
plugins {
    kotlin("jvm")
    application
}

group = "io.cuttlefish"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":hardware"))
    implementation(project(":kernel"))
    implementation(project(":compiler"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    testImplementation(kotlin("test"))
}
application {
    mainClass.set("io.cuttlefish.MainKt") // Tells gradle where your main() function is
}

tasks.test {
    useJUnitPlatform()
}