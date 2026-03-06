plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
}

application {
    mainClass.set("com.example.wirecli.MainKt")
}

kotlin {
    jvmToolchain(17)
}
