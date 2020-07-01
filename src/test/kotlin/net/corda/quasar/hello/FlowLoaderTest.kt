package net.corda.quasar.hello

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.internal.toPath
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.nodeapi.internal.rpc.client.AMQPClientSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.nodeapi.internal.serialization.kryo.KryoCheckpointSerializer
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory
import org.apache.activemq.artemis.core.cluster.DiscoveryEntry
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.streams.asSequence

interface Foo {
    fun foo()
}

class Bar
class FooImpl : Foo {
    override fun foo() {
        Bar()
    }
}

class ArchiveClassLoaderTest {
    @Test
    fun test() {
        val p = Paths.get("/home/r3/checkpoints_debug.zip")
        val flowLoader = FlowLoader(p)
        val cls = flowLoader.classLoader.loadClass(DiscoveryEntry::class.java.name)
        val cls2 = flowLoader.classLoader.loadClass("net.corda.splitcorda.flows.SuspendedFlow")
        val cls3 = flowLoader.classLoader.loadClass("co.paralleluniverse.fibers.SuspendExecution")
        println(cls3)
//        val fiberFile = flowLoader.fibers().first()
//        flowLoader.fiber(fiberFile)
    }

    @Test
    fun test3() {
        val destination = Paths.get("/tmp/ciao")
        Files.createDirectories(destination)
        val filesystem = FileSystems.newFileSystem(Paths.get("/home/r3/checkpoints_debug.zip"), null)
        (Files.list(filesystem.getPath("lib")).asSequence() +
                        Files.list(filesystem.getPath("drivers")).asSequence() +
                        Files.list(filesystem.getPath("cordapps")).asSequence()
                ).filter {
                    it.fileName.toString().endsWith(".jar")
                }.map {
                    it.toUri().toURL()
                }.forEach { url ->
                    url.openStream().use { inputStream ->
                        Files.newOutputStream(destination.resolve(UUID.randomUUID().toString() + ".jar")).use { outputStream ->
                            val buffer = ByteArray(0x10000)
                            while(true) {
                                val read = inputStream.read(buffer)
                                if(read < 0) break
                                outputStream.write(buffer, 0, read)
                            }
                        }
                    }
                }
    }

    @Test
    fun test2() {
        val cl = BookkeepingClassLoader(javaClass.classLoader)
        Thread.currentThread().contextClassLoader = cl
        val className = FooImpl::class.java.name
        val cls = Class.forName(className, true, cl)
        val foo = cls.constructors.single().newInstance()
        cls.getMethod("foo").invoke(foo)
        println(cl.classes)
        cl.reset()
        val foo2 = cls.constructors.single().newInstance()
        cls.getMethod("foo").invoke(foo2)
        println(cl.classes)
    }
}