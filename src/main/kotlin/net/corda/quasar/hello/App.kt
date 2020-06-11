package net.corda.quasar.hello

import co.paralleluniverse.common.util.SystemProperties
import co.paralleluniverse.fibers.DefaultFiberScheduler
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Fiber.parkAndSerialize
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.io.serialization.ByteArraySerializer
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.nodeapi.internal.rpc.client.AMQPClientSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.*
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory
import java.io.Serializable
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Paths

enum class Action {
    serialize, deserialize;
}

data class Person(
    val name: String,
    val surname: String,
    val age: Int
) : Serializable

object App {
    val fiberFile = Paths.get("/tmp/fiber.bin")

    val serializer = (Fiber.getFiberSerializer(false) as KryoSerializer).apply {
        val serializer = Fiber.getFiberSerializer(false) as KryoSerializer
        val classResolver = CordaClassResolver(KRYO_CHECKPOINT_CONTEXT).apply { setKryo(serializer.kryo) }
        // TODO The ClassResolver can only be set in the Kryo constructor and Quasar doesn't provide us with a way of doing that
        val field = Kryo::class.java.getDeclaredField("classResolver").apply { isAccessible = true }
        serializer.kryo.apply {
            field.set(this, classResolver)
            // don't allow overriding the public key serializer for checkpointing
            DefaultKryoCustomizer.customize(this)
    //        addDefaultSerializer(AutoCloseable::class.java, AutoCloseableSerialisationDetector)
            register(ClosureSerializer.Closure::class.java, CordaClosureSerializer)
            classLoader = javaClass.classLoader
        }
    }

//    val serializer = JDKSerializer()

    fun newFiber(): Fiber<String> {
        val SERIALIZER_BLOCKER = Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER")
                .apply { isAccessible = true }
                .get(null)
        val om = ObjectMapper()
        return object : Fiber<String>() {
            @Suspendable
            override fun run(): String {
                val p = Person("Walter", "Oggioni", 32)
                println("Fiber started")
//            park()
                parkAndSerialize { f: Fiber<*>, _: ByteArraySerializer ->
                    Files.newOutputStream(fiberFile).use {
//                        val bytes = this.checkpointSerialize()
//                        it.write(bytes.bytes)
                    serializer.write(it, f)
                    }
                    unpark(SERIALIZER_BLOCKER)
                }
                println("Fiber resumed")
                return om.writeValueAsString(p)
            }
        }
    }


    fun run(action: Action) {
        val runtimeMxBean = ManagementFactory.getRuntimeMXBean()
        val arguments = runtimeMxBean.inputArguments
        println(arguments)
        val fiber = when (action) {
            Action.serialize -> newFiber().start()
            Action.deserialize -> {
                Files.newInputStream(fiberFile).use {
//                    it.readBytes().checkpointDeserialize<Fiber<String>>()
                serializer.read(it) as Fiber<String>
                }
            }
        }
        Thread.sleep(1000)
        Fiber.unparkDeserialized(fiber, DefaultFiberScheduler.getInstance())
        fiber.unpark()
//    fiber.get()
//    while(!fiber.isInterrupted) Thread.sleep(0L)
//    Thread.sleep(1000)
//    println("Resuming fiber")
//    fiber.unpark()
        println(fiber.get())
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val verify = SystemProperties.isEmptyOrTrue("co.paralleluniverse.fibers.verifyInstrumentation")
        val classloader = javaClass.classLoader
        nodeSerializationEnv = SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
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
                p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classLoader = classloader),
                rpcServerContext = AMQP_RPC_SERVER_CONTEXT.withClassLoader(classloader),
                rpcClientContext = AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classloader), //even Shell embeded in the node connects via RPC to the node
                storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classloader),

                checkpointSerializer = KryoCheckpointSerializer,
                checkpointContext = KRYO_CHECKPOINT_CONTEXT.withClassLoader(classloader)
        )
        run(Action.serialize)
        run(Action.deserialize)
    }

    @JvmStatic
    fun main2(args : Array<String>) {
        val KRYO_CHECKPOINT_CONTEXT = CheckpointSerializationContextImpl(
                SerializationDefaults.javaClass.classLoader,
                QuasarWhitelist,
                emptyMap(),
                true,
                null,
                AlwaysAcceptEncodingWhitelist
        )
        val classloader = javaClass.classLoader
        nodeSerializationEnv = SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
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
                p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classLoader = classloader),
                rpcServerContext = AMQP_RPC_SERVER_CONTEXT.withClassLoader(classloader),
                rpcClientContext = AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classloader), //even Shell embeded in the node connects via RPC to the node
                storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classloader),

                checkpointSerializer = KryoCheckpointSerializer,
                checkpointContext = KRYO_CHECKPOINT_CONTEXT.withClassLoader(classloader)
        )
        val flowStateMachine = Paths.get("/tmp/flows/3fa4d023-8a3e-4a2f-9b38-d5925ae8c68b.flow")
                .let {Files.newInputStream(it)}
                .use { it.readBytes().checkpointDeserialize<FlowStateMachineImpl<Unit>>(KRYO_CHECKPOINT_CONTEXT) }
        val stack = flowStateMachine.logic.flowStackSnapshot()
    }
}