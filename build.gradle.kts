import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
val junitVersion = "5.9.0"
val flywayCoreVersion = "9.0.4"
val kotliqueryVersion = "1.8.0"
val postgresqlVersion = "42.4.0"
val hikariCPVersion = "5.0.1"
val testcontainersPostgresqlVersion = "1.17.3"
val mockkVersion = "1.12.5"
val rapidsAndRiversVersion = "2022072721371658950659.c1e8f7bf35c6"

plugins {
    kotlin("jvm") version "1.7.10"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")

    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}