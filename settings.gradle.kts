plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "Pistachio"
include("hardware")
include("compiler")
include("terminal")