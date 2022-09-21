package no.nav.helse.sparsom.job

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import kotlin.system.exitProcess

internal val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())

private val log = LoggerFactory.getLogger("no.nav.helse.sparsom.job.App")
private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val env = System.getenv()
    val databaseInstance = requireNotNull(env["DATABASE_INSTANCE"]) { "database instance must be set" }
    val databaseRegion: String = requireNotNull(env["DATABASE_REGION"]) { "DATABASE_REGION må settes" }
    val gcpProjectId: String = requireNotNull(env["GCP_TEAM_PROJECT_ID"]) { "GCP_TEAM_PROJECT_ID må settes" }
    val databaseName: String = requireNotNull(env["DATABASE_DATABASE"]) { "databasenavn må settes" }
    val dbConfig = HikariConfig().apply {
        jdbcUrl = String.format(
            "jdbc:postgresql:///%s?%s&%s",
            databaseName,
            "cloudSqlInstance=$gcpProjectId:$databaseRegion:$databaseInstance",
            "socketFactory=com.google.cloud.sql.postgres.SocketFactory"
        )
        username = requireNotNull(env["DATABASE_USERNAME"]) { "brukernavn må settes" }
        password = requireNotNull(env["DATABASE_PASSWORD"]) { "passord må settes" }
        initializationFailTimeout = Duration.ofMinutes(1).toMillis()
        connectionTimeout = Duration.ofMinutes(1).toMillis()
        maximumPoolSize = 1
    }

    HikariDataSource(dbConfig).connection.use {
        runApplication(it)
    }
}

private fun runApplication(connection: Connection) {
    ImporterAktivitetslogg(Dispatcher("arbeidstabell_step2", connection))
        .migrate(connection)
}

