import java.net.URI

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.2.71"
    application
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven {
        url = URI("https://software.r3.com/artifactory/corda-releases")
    }
    maven {
        url = URI("https://software.r3.com/artifactory/corda-dependencies")
    }
}

val quasarNotation = "co.paralleluniverse:quasar-core:0.7.12_r3:jdk8"

dependencies {
//    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.jetbrains.kotlin:kotlin-test")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    implementation(quasarNotation)

    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.0")
    implementation("net.corda:splitcorda-contract:0.0.1")
    implementation("net.corda:splitcorda-workflows:0.0.1")

    implementation("net.corda:corda-node:4.6-SNAPSHOT") {
        exclude(group = "co.paralleluniverse", module = "quasar-core")
        exclude(group = "org.crashub", module = "crash.connectors.ssh")
        exclude(group = "org.crashub", module = "crash.shell")
        exclude(group = "net.corda.djvm", module = "corda-djvm")
        exclude(group = "com.github.bft-smart", module = "library")
    }
    implementation("net.corda:corda-node-api:4.6-SNAPSHOT") {
        exclude(group = "co.paralleluniverse", module = "quasar-core")
    }
}

application {
    mainClassName = "net.corda.quasar.hello.AppKt"
}

val QUASAR = "quasar"
val quasar = project.configurations.create(QUASAR)
quasar.withDependencies {
    val quasarDependency = project.dependencies.create(
            quasarNotation, closureOf<ModuleDependency> {
        isTransitive = false
    })
    add(quasarDependency)
}


let {
    val addQuasarAgentAction = closureOf<JavaForkOptions> {
        jvmArgs = (jvmArgs ?: ArrayList<String>()).apply {
            add("-javaagent:${project.configurations[QUASAR].singleFile}")
            add("-Dco.paralleluniverse.fibers.verifyInstrumentation")
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
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}