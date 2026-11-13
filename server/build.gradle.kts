plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

val ktor = "3.6.1"

kotlin { jvmToolchain(17) }

application {
    mainClass.set("medic.ServerKt")
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-cors:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-server-call-logging:$ktor")
    implementation("io.ktor:ktor-server-default-headers:$ktor")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor")
    testImplementation(kotlin("test"))
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir.resolve("server")
}
