package net.corda.quasar.hello

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.nodeapi.internal.rpc.client.AMQPClientSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.nodeapi.internal.serialization.kryo.KryoCheckpointSerializer
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory
import java.io.File
import java.lang.ClassLoader.getSystemClassLoader
import java.lang.management.ManagementFactory
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import com.sun.tools.attach.VirtualMachine
import kotlin.streams.asSequence


class FlowLoader(archivePath: Path, parent: ClassLoader? = getSystemClassLoader()) : AutoCloseable {
    private val temporaryDir: Path

    val classLoader: ClassLoader

    fun startQuasarAgent() {
        val nameOfRunningVM = ManagementFactory.getRuntimeMXBean().name
        val pid = nameOfRunningVM.substring(0, nameOfRunningVM.indexOf('@'))
        val vm = VirtualMachine.attach(pid)
//        vm.loadAgent(jarFilePath, "")
        vm.detach()
    }

    private fun extract(archivePath: Path, destination: Path) {
        val buffer = ByteArray(0x10000)
        ZipInputStream(Files.newInputStream(archivePath)).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val newFile: Path = destination.resolve(zipEntry.name)
                Files.createDirectories(newFile.parent)
                Files.newOutputStream(newFile).use { outputStream ->
                    while (true) {
                        val read = zis.read(buffer)
                        if (read < 0) break
                        outputStream.write(buffer, 0, read)
                    }
                }
                zipEntry = zis.nextEntry
            }
            zis.closeEntry()
        }
    }

    init {
        temporaryDir = Files.createTempDirectory("flowLoader")
        extract(archivePath, temporaryDir)
        val jars = (
                Files.list(temporaryDir.resolve("lib")).asSequence() +
                        Files.list(temporaryDir.resolve("drivers")).asSequence() +
                        Files.list(temporaryDir.resolve("cordapps")).asSequence()
                ).filter {
                    it.fileName.toString().endsWith(".jar")
                }.map {
                    FileSystems.newFileSystem(it, null)
                }.toList()
        classLoader = object : ClassLoader(parent) {

            override fun loadClass(name: String, resolve: Boolean): Class<*>? {
                synchronized(getClassLoadingLock(name)) {
                    var c = findLoadedClass(name)
                    if (c == null) {
                        if(name.startsWith("co.paralleluniverse")) {
                            //We have to use the same version of quasar the java agent is running
                            // (it is not possible to dynamically start the agent after reading)
                            c = Class.forName(name)
                        } else {
                            c = this.findClass(name)
                            if (c == null) {
                                c = super.loadClass(name, resolve)
                            } else if (resolve) {
                                resolveClass(c)
                            }
                        }
                    } else if (resolve) {
                        resolveClass(c)
                    }
                    return c
                }
            }

            override fun findClass(name: String): Class<*>? {
                val resourceName = "/" + name.replace('.', '/') + ".class"
                return findResource(resourceName)?.let {
                    it.openStream().use { inputStream ->
                        val bytes = inputStream.readAllBytes()
                        defineClass(name, bytes, 0, bytes.size)
                    }
                }
            }

            override fun findResource(name: String): URL? {
                return jars.asSequence().map {
                    it.getPath(name)
                }.firstOrNull {
                    Files.exists(it)
                }?.toUri()?.toURL()
            }

            override fun getResource(name: String): URL? {
                return findResource(name) ?: super.getResource(name)
            }
        }

//        checkpointSerializationContext = CheckpointSerializationContextImpl(
//                classLoader,
//                QuasarWhitelist,
//                emptyMap(),
//                true,
//                null)

        nodeSerializationEnv = SerializationEnvironment.with(
                SerializationFactoryImpl().apply
                {
                    registerScheme(AMQPServerSerializationScheme(
                            emptyList(),
                            Caffeine.newBuilder()
                                    .maximumSize(128)
                                    .build<SerializationFactoryCacheKey, SerializerFactory>().asMap()))
                    registerScheme(AMQPClientSerializationScheme(
                            emptyList(),
                            Caffeine.newBuilder()
                                    .maximumSize(128)
                                    .build<SerializationFactoryCacheKey, SerializerFactory>()
                                    .asMap()))
                },
                p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classLoader = classLoader),
                rpcServerContext = AMQP_RPC_SERVER_CONTEXT.withClassLoader(classLoader),
                rpcClientContext = AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classLoader), //even Shell embeded in the node connects via RPC to the node
                storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classLoader),
                checkpointSerializer = KryoCheckpointSerializer,
                checkpointContext = KRYO_CHECKPOINT_CONTEXT.withClassLoader(classLoader)
        )

    }

    fun fibers(): Sequence<String> = Files.list(temporaryDir.resolve("fibers")).asSequence()
            .map { it.fileName.toString() }
            .filter { it.endsWith(".fiber") }

    fun fiber(name: String): Any = Files.newInputStream(temporaryDir.resolve("fibers/$name")).use {
        it.readAllBytes().checkpointDeserialize()
    }

    private fun deletePath(path: Path?) {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map { obj: Path -> obj.toFile() }
                .forEach { obj: File -> obj.delete() }
    }

    override fun close() {
        deletePath(temporaryDir)
    }
}