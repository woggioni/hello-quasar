import java.net.URI


plugins {
    id("org.jetbrains.kotlin.jvm") apply false
    application
}

allprojects {
    val artifactory_contextUrl = "https://software.r3.com/artifactory"
    apply(plugin = "org.jetbrains.kotlin.jvm")
    repositories {
        mavenLocal()
        maven {
            url = URI("${artifactory_contextUrl}/corda-dependencies")
//            content {
//                includeGroupByRegex 'net\\.corda(\\..*)?'
//                includeGroupByRegex 'com\\.r3(\\..*)?'
//                includeGroup 'co.paralleluniverse'
//                includeGroup 'org.crashub'
//                includeGroup 'com.github.bft-smart'
//            }
        }
        mavenCentral()
        jcenter()
    }
    fun property(name: String) = project.property(name).toString()
    fun DependencyHandlerScope.dep(conf : String, group:String, name:String, version:String) {
        add(conf, mapOf("group" to group, "name" to name, "version" to version))
    }

    dependencies {
        dep("testRuntimeOnly", "org.junit.jupiter", "junit-jupiter-engine", property("junit.version"))
        dep("testImplementation","org.junit.jupiter", "junit-jupiter-api", property("junit.version"))
        dep("testImplementation","org.junit.jupiter", "junit-jupiter-params", property("junit.version"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.languageVersion = "1.4"
        kotlinOptions.apiVersion = "1.4"
        kotlinOptions.jvmTarget = "11"
    }
}

val quasarNotation = "co.paralleluniverse:quasar-core:0.8.0"
fun property(name: String) = project.property(name).toString()

dependencies {
//    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(quasarNotation)

    implementation("com.fasterxml.jackson.core", "jackson-databind", property("jackson.version"))

//    implementation("net.corda", "splitcorda-contract", property("splitcorda.version"))
//    implementation("net.corda", "splitcorda-workflows", property("splitcorda.version"))

//    implementation("net.corda", "corda-node", property("corda.version")) {
//        exclude(group = "co.paralleluniverse", module = "quasar-core")
//        exclude(group = "org.crashub", module = "crash.connectors.ssh")
//        exclude(group = "org.crashub", module = "crash.shell")
//        exclude(group = "net.corda.djvm", module = "corda-djvm")
//        exclude(group = "com.github.bft-smart", module = "library")
//    }

//    implementation("net.corda", "corda-node-api", "4.6-SNAPSHOT") {
//        exclude(group = "co.paralleluniverse", module = "quasar-core")
//    }

    testImplementation("org.jetbrains.kotlin:kotlin-test")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

//    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", property("junit.version"))
//    testImplementation("org.junit.jupiter", "junit-jupiter-api", property("junit.version"))
//    testImplementation("org.junit.jupiter", "junit-jupiter-params", property("junit.version"))
}

application {
//    mainClassName = "net.corda.quasar.hello.AppKt"
//    mainClassName = "net.corda.quasar.hello.FlowNecromancer"
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


let {
    val addQuasarAgentAction = closureOf<JavaForkOptions> {
        jvmArgs = (jvmArgs ?: ArrayList<String>()).apply {
//            add("-javaagent:${project.configurations[QUASAR].singleFile}")
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

//application {
//    mainClass = "net.corda.quasar.hello.App"
//}

configure<JavaApplication>() {
    mainClassName = "net.corda.quasar.hello.App"
    applicationDefaultJvmArgs += listOf("-javaagent:${project.configurations[QUASAR].singleFile}")
}

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    kotlinOptions.jvmTarget = "11"
//}
