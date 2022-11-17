plugins {
    kotlin("jvm") version "1.7.0"
}

val kotliqueryVersion = "1.9.0"
val hikariVersion = "5.0.1"
val jacksonVersion = "2.14.0"
val junitJupiterVersion = "5.9.0"
val postgresqlVersion = "42.5.0"
val logbackClassicVersion = "1.4.4"
val logstashVersion = "7.2"
val jvmTargetVersion = "17"

allprojects {
    group = "no.nav.helse"
    version = properties["version"] ?: "local-build"

    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        implementation("org.postgresql:postgresql:$postgresqlVersion")
        implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
        implementation("ch.qos.logback:logback-classic:$logbackClassicVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion") {
            exclude("com.fasterxml.jackson.core")
            exclude("com.fasterxml.jackson.dataformat")
        }
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion") {
            exclude("org.jetbrains.kotlin:kotlin-reflect")
        }
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

        implementation("com.zaxxer:HikariCP:$hikariVersion")

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    }

    tasks {
        compileKotlin {
            kotlinOptions.jvmTarget = jvmTargetVersion
        }

        compileTestKotlin {
            kotlinOptions.jvmTarget = jvmTargetVersion
        }

        withType<Wrapper> {
            gradleVersion = "7.4.2"
        }

    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks {
        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("skipped", "failed")
            }
        }
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    }
}
