import java.nio.file.Files

plugins {
    kotlin("jvm")
    application
}

group = "io.cuttlefish"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("test"))
    implementation(project(":hardware"))
    implementation(project(":compiler"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

}

application {
    applicationName = "lx"
    mainClass.set("io.cuttlefish.MainKt")
}

val createSymlink = tasks.register("createSymlink") {
    description = "Creates a symlink from the project root to the installDist executable"
    group = "distribution"

    dependsOn(tasks.named("installDist"))

    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val executableName = if (isWindows) "lx.bat" else "lx"

    val targetFileProvider = layout.buildDirectory.file("install/lx/bin/$executableName")
    val linkFileProvider = rootProject.layout.projectDirectory.file(executableName)

    // Inputs/Outputs for Gradle up-to-date checking
    inputs.file(targetFileProvider)
    outputs.file(linkFileProvider)

    doLast {
        val target = targetFileProvider.get().asFile.toPath()
        val link = linkFileProvider.asFile.toPath()

        if (Files.exists(link)) Files.delete(link)

        try {
            Files.createSymbolicLink(link, target)
            println("Symlink created: $link -> $target")
        } catch (e: Exception) {
            try {
                Files.copy(target, link)
                println("Symlink failed; copied executable instead: $link")
            } catch (copyEx: Exception) {
                throw RuntimeException("Could not create link or copy file: ${e.message}")
            }
        }
    }
}!!

tasks.named("installDist") {
    finalizedBy(createSymlink)
}