import java.nio.file.Files.createSymbolicLink

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
    implementation(project(":compiler"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    testImplementation(kotlin("test"))
}
application {
    applicationDefaultJvmArgs = listOf()
    applicationName = "lx"
    mainClass.set("io.cuttlefish.MainKt") // Tells gradle where your main() function is
}


val createSymlink= tasks.register("createSymlink") {
    description = "Creates a symlink from the project root to the installDist executable"
    group = "distribution"

    // We depend on installDist because that's what creates the bin/lx file
    dependsOn(tasks.named("installDist"))

    doLast {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val executableName = if (isWindows) "lx.bat" else "lx"

        val targetFile = layout.buildDirectory.file("install/lx/bin/$executableName").get().asFile
        val linkFile = rootProject.file(executableName)

        if (linkFile.exists()) linkFile.delete()

        try {
            createSymbolicLink(linkFile.toPath(), targetFile.toPath())
            logger.lifecycle("Symlink created: ${linkFile.absolutePath} -> ${targetFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to create symlink: ${e.message}")
        }
    }
}

tasks.named("installDist") {
    finalizedBy(createSymlink)
}


tasks.test {
    useJUnitPlatform()
}