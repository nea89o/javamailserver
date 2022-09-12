plugins {
    kotlin("jvm") version "1.7.10"
    id("com.bnorm.power.kotlin-power-assert") version "0.12.0"
    application
}

group = "moe.nea89"
version = "0.0.1"

repositories {
    mavenCentral()
}

application {
    mainClass.set("Main")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm")
    testImplementation(platform("io.kotest:kotest-bom:5.4.2"))
    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("io.kotest:kotest-property")
    testImplementation("io.kotest:kotest-framework-datatest")
}

kotlin {

}

tasks.withType<Test>() {
    useJUnitPlatform()
}
