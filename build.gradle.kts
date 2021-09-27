plugins {
    kotlin("jvm") version "1.5.31"
    application
}

group = "moe.nea89"
version = "0.0.0"

repositories {
    mavenCentral()
}

application {
    mainClass.set("Main")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
}
