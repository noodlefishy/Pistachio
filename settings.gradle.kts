plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "Operating-System"
include("hardware")
include("hardware")
include("kernal")
include("compiler")
include("cli")
include("terminal")
include("kernel")