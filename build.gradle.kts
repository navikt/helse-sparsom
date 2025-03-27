plugins {
    kotlin("jvm") version "2.1.20"
}

val ktorVersion = "3.0.1"
val opensearchClientVersion = "2.3.0"
val jacksonVersion = "2.18.1"
val junitJupiterVersion = "5.11.3"
val logbackClassicVersion = "1.5.12"
val logbackEncoderVersion = "8.0"

allprojects {
    group = "no.nav.helse"
    version = properties["version"] ?: "local-build"

    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        val githubPassword: String? by project
        mavenCentral()
        /* ihht. https://github.com/navikt/utvikling/blob/main/docs/teknisk/Konsumere%20biblioteker%20fra%20Github%20Package%20Registry.md
            så plasseres github-maven-repo (med autentisering) før nav-mirror slik at github actions kan anvende førstnevnte.
            Det er fordi nav-mirroret kjører i Google Cloud og da ville man ellers fått unødvendige utgifter til datatrafikk mellom Google Cloud og GitHub
         */
        maven {
            url = uri("https://maven.pkg.github.com/navikt/maven-release")
            credentials {
                username = "x-access-token"
                password = githubPassword
            }
        }
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        maven("https://maven.tryformation.com/releases") {
            content {
                includeGroup("com.jillesvangurp")
            }
        }
    }

    dependencies {
        constraints {
            api("io.ktor:ktor-server-netty:$ktorVersion")
            api("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
            api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
            api("io.ktor:ktor-serialization-jackson:$ktorVersion")
            api("io.ktor:ktor-server-call-id:$ktorVersion")
            api("io.ktor:ktor-server-auth-jwt:$ktorVersion")
        }
        implementation("com.jillesvangurp:search-client:$opensearchClientVersion") {
            /* clamper search-client til å bruke samme ktorversjon som api-modulen */
            constraints {
                api("io.ktor:ktor-client-cio:$ktorVersion")
                api("io.ktor:ktor-client-java:$ktorVersion")
                api("io.ktor:ktor-client-core:$ktorVersion")
                api("io.ktor:ktor-client-auth:$ktorVersion")
                api("io.ktor:ktor-client-logging:$ktorVersion")
                api("io.ktor:ktor-client-serialization:$ktorVersion")
                api("io.ktor:ktor-serialization-kotlinx:$ktorVersion")
                api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                api("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                api("io.ktor:ktor-client-darwin:$ktorVersion")
            }
        }
        implementation("ch.qos.logback:logback-classic:$logbackClassicVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion") {
            exclude("com.fasterxml.jackson.core")
            exclude("com.fasterxml.jackson.dataformat")
        }
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion") {
            exclude("org.jetbrains.kotlin:kotlin-reflect")
        }
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

        testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of("21"))
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
}
