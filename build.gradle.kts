plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "wirecli"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
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

kotlin {
    jvmToolchain(17)
}
