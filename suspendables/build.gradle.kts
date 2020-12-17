plugins {
    id("kotlin")
    application
}


val quasarNotation = mapOf("group" to "co.paralleluniverse", "name" to "quasar-core", "version" to "0.8.4_r3")

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compileOnly(quasarNotation)
}

val QUASAR = "quasar"
val quasar = project.configurations.create(QUASAR)
quasar.withDependencies {
    val quasarDependency = project.dependencies.create(
        quasarNotation,
        closureOf<ModuleDependency> {
            isTransitive = false
        })
    add(quasarDependency)
}

application {
    mainClass.set("net.woggioni.hello.quasar.Main")
}

let {
    val addQuasarAgentAction = closureOf<JavaForkOptions> {
        jvmArgs = (jvmArgs ?: ArrayList<String>()).apply {
            add("-javaagent:${project.configurations[QUASAR].singleFile}=d")
//            add("-javaagent:/home/r3/code/quasar/quasar-core/build/libs/quasar-core-0.8.0.jar")
            add("-Dco.paralleluniverse.fibers.verifyInstrumentation")
//            add("-Djdk.attach.allowAttachSelf=true")
        }
    }

    sequenceOf<kotlin.reflect.KClass<out Task>>(JavaExec::class, Test::class).forEach {
        project.tasks.withType(it, Action<Task> {
            doFirst(addQuasarAgentAction)
        })
    }
}