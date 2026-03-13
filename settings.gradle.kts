rootProject.name = "wire-cli"

// Include Kalium SDK as a composite build, mirroring wiretui.
// Default location is ./vendor/kalium (git submodule).
// Override via KALIUM_DIR env var or -Pkalium.dir=... Gradle property.
val kaliumDir =
    providers.gradleProperty("kalium.dir")
        .orElse(providers.environmentVariable("KALIUM_DIR"))
        .getOrElse("vendor/kalium")
val kaliumDirFile = file(kaliumDir)

require(kaliumDirFile.exists()) {
    "Kalium directory '$kaliumDir' not found. Set -Pkalium.dir=/path/to/kalium (or KALIUM_DIR)."
}

includeBuild(kaliumDirFile) {
    dependencySubstitution {
        substitute(module("com.wire:logic")).using(project(":logic"))
    }
}
