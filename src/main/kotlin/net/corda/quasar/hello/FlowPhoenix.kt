package net.corda.quasar.hello

import java.lang.management.ManagementFactory
import java.nio.file.Paths

class FlowNecromancer {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val runtimeMxBean = ManagementFactory.getRuntimeMXBean()
            val arguments = runtimeMxBean.inputArguments
            println(arguments)

            val p = Paths.get("/home/r3/checkpoints_debug.zip")
            val flowLoader = FlowLoader(p, null)
            val clasFile = flowLoader.classLoader.getResource("net/corda/node/services/statemachine/TransientReference.class")
            val fiberFile = flowLoader.fibers().first()
            val fib = flowLoader.fiber(fiberFile)
            val cls = fib.javaClass
            println(fib.javaClass)
        }
    }
}