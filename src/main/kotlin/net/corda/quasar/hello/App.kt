package net.corda.quasar.hello

import co.paralleluniverse.fibers.DefaultFiberScheduler
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Fiber.parkAndSerialize
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.io.serialization.ByteArraySerializer
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration


enum class Action {
    serialize, deserialize;
}

data class Person(
    val name: String,
    val surname: String,
    val age: Int
) : Serializable


class MyFiber : Fiber<Unit>() {
    @Suspendable
    fun sleep(duration: Duration = Duration.ofMillis(0L)) {
        Fiber.sleep(duration.toMillis())
    }

    @Suspendable
    override fun run() {
        val p = Person("Walter", "Oggioni", 32)
        println("Fiber started")
//        park()
        foo(y = 5.0)
        println("Fiber resumed")
    }

    private fun foo(n: Int = 0, s: String = "") {
        sleep()
    }

    @Suspendable
    fun foo(n: Int = 0, y: Double = 0.0) {
        sleep()
    }
}

object App {
    val om = ObjectMapper()

    val fiberFile = Paths.get("/tmp/fiber.bin")

    val serializer = Fiber.getFiberSerializer(false) as KryoSerializer

//    val serializer = JDKSerializer()

    fun newFiber(): Fiber<String> {
        val SERIALIZER_BLOCKER = Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER")
            .apply { isAccessible = true }
            .get(null)
        return object : Fiber<String>() {
            @Suspendable
            override fun run(): String {
                val p = Person("Walter", "Oggioni", 32)
                println("Fiber started")
//                park()
                parkAndSerialize { f: Fiber<*>, _: ByteArraySerializer ->
                    Files.newOutputStream(fiberFile).use {
                        serializer.write(it, f)
                    }
//                    unpark(SERIALIZER_BLOCKER)
                }
                println("Fiber resumed")
                return om.writeValueAsString(p)
            }
        }
    }


    fun run(action: Action) {
        val fiber = when (action) {
            Action.serialize -> newFiber().start()
            Action.deserialize -> {
                Files.newInputStream(fiberFile).use {
                    serializer.read(it) as Fiber<String>
                }
            }
        }
        Thread.sleep(1000)
        Fiber.unparkDeserialized(fiber, DefaultFiberScheduler.getInstance())
//    fiber.get()
//    while(!fiber.isInterrupted) Thread.sleep(0L)
//    Thread.sleep(1000)
//    println("Resuming fiber")
//    fiber.unpark()
        println(fiber.get())
    }

//    fun foo(cl : ClassLoader) : Iterator<Class<*>> {
//        var classLoaderClass: Class<in ClassLoader> = cl::class.java
//        while (classLoaderClass != ClassLoader::class.java) {
//            classLoaderClass = classLoaderClass.superclass
//        }
//        val ClassLoader_classes_field = classLoaderClass
//                .getDeclaredField("classes")
//        ClassLoader_classes_field.isAccessible = true
//        val classes = ClassLoader_classes_field[CL] as Vector<Class<*>>
//        return classes.iterator()
//    }

    @JvmStatic
    fun main(args: Array<String>) {
//        val runtimeMxBean = ManagementFactory.getRuntimeMXBean()
//        val arguments = runtimeMxBean.inputArguments
//        println(arguments)

//        run(Action.serialize)
//        run(Action.deserialize)

        val fiber = MyFiber()

        fiber.start()

        fiber.get()
    }
}

