val cloudSqlVersion = "1.7.2"

val mainClass = "no.nav.helse.sparsom.job.AppKt"

dependencies {
    implementation(project(":dao"))
    implementation("com.google.cloud.sql:postgres-socket-factory:$cloudSqlVersion")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("app")

    manifest {
        attributes["Main-Class"] = mainClass
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }

    doLast {
        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists())
                it.copyTo(file)
        }
    }
}
