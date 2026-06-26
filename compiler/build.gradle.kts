plugins {
    kotlin("jvm")
}

group = "io.cuttlefish"
version = "1.0-c"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":hardware"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}