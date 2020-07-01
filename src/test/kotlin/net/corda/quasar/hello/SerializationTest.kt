package net.corda.quasar.hello

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.nodeapi.internal.rpc.client.AMQPClientSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.KryoCheckpointSerializer
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class SerializationTest {
    @Test
    fun test() {

        val KRYO_CHECKPOINT_CONTEXT = CheckpointSerializationContextImpl(
                SerializationDefaults.javaClass.classLoader,
                QuasarWhitelist,
                emptyMap(),
                true,
                null,
                AlwaysAcceptEncodingWhitelist
        )
        val classloader = javaClass.classLoader
        val nodeSerializationEnv = SerializationEnvironment.with(
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
                .let { Files.newInputStream(it) }
                .use { it.readBytes().checkpointDeserialize<FlowStateMachineImpl<Unit>>(KRYO_CHECKPOINT_CONTEXT) }
        val stack = flowStateMachine.logic.flowStackSnapshot()
    }
}