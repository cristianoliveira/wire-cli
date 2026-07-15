import org.gradle.jvm.application.tasks.CreateStartScripts
import java.time.Duration

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    application
}

group = "wirecli"
version = "0.1.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("com.wire:logic")
    implementation("com.wire:backup")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.squareup.okio:okio:3.9.0")

    // Logging infrastructure: SLF4J + Logback + kotlin-logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    testImplementation(kotlin("test"))

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

application {
    mainClass.set("wirecli.MainKt")
}

// Optional debug props for binary-search runs
val batsFilter = providers.gradleProperty("batsFilter").orNull
val batsPath = providers.gradleProperty("batsPath").orNull ?: "test/bats"

val batsTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs bash integration tests with Bats"
    dependsOn(tasks.named("installDist"))

    // Run bats directly via bash -lc to avoid Gradle Exec process handling issues
    // that cause stalls after test 14 in full suite runs
    val cmd = mutableListOf("bats", "--print-output-on-failure")
    if (!batsFilter.isNullOrBlank()) {
        cmd += listOf("--filter", batsFilter)
    }
    cmd += batsPath

    commandLine("bash", "-lc", cmd.joinToString(" ") { it.replace("\"", "\\\"").let { "\"$it\"" } })
    environment("WIRE_BIN", layout.buildDirectory.file("install/${project.name}/bin/${project.name}").get().asFile.absolutePath)
    timeout.set(Duration.ofMinutes(20))
}

tasks.named("check") {
    dependsOn("ktlintCheck")
    dependsOn("detekt")
    dependsOn(batsTest)
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = true
    config.setFrom(files("detekt.yml"))
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        val unixScriptFile = unixScript
        val patched =
            unixScriptFile.readText().replace(
                Regex("^CLASSPATH=(.*)$", RegexOption.MULTILINE),
                "CLASSPATH=\"$1\"",
            )
        unixScriptFile.writeText(patched)
    }
}

ktlint {
    version.set("1.2.1")
    baseline.set(file("config/ktlint/baseline.xml"))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/detekt.yml"))
    ignoreFailures = true
}

kotlin {
    jvmToolchain(21)
}
