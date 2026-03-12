import org.gradle.jvm.application.tasks.CreateStartScripts
import java.time.Duration
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

plugins {
    kotlin("jvm") version "2.3.0"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
    testImplementation(kotlin("test"))
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
    dependsOn(batsTest)
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        val unixScriptFile = unixScript
        val patched = unixScriptFile.readText().replace(
            Regex("^CLASSPATH=(.*)$", RegexOption.MULTILINE),
            "CLASSPATH=\"$1\""
        )
        unixScriptFile.writeText(patched)
    }
}

kotlin {
    jvmToolchain(17)
}
