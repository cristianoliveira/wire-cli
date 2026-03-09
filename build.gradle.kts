import org.gradle.jvm.application.tasks.CreateStartScripts

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
}

application {
    mainClass.set("wirecli.MainKt")
}

val batsTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs bash integration tests with Bats"
    dependsOn(tasks.named("installDist"))
    commandLine("bash", "${project.rootDir}/test/bats/run.sh")
    environment(
        "WIRE_BIN",
        layout.buildDirectory.file("install/${project.name}/bin/${project.name}").get().asFile.absolutePath
    )
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
