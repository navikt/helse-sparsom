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
    val databaseHost: String = requireNotNull(env["DATABASE_HOST"]) { "host må settes" }
    val databasePort: String = requireNotNull(env["DATABASE_PORT"]) { "port må settes" }
    val databaseName: String = requireNotNull(env["DATABASE_DATABASE"]) { "databasenavn må settes" }
    val dbConfig = HikariConfig().apply {
        jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", databaseHost, databasePort, databaseName)
        username = requireNotNull(env["DATABASE_USERNAME"]) { "brukernavn må settes" }
        password = requireNotNull(env["DATABASE_PASSWORD"]) { "passord må settes" }
        initializationFailTimeout = Duration.ofMinutes(1).toMillis()
        connectionTimeout = Duration.ofMinutes(1).toMillis()
        maximumPoolSize = 2
    }

    HikariDataSource(dbConfig).connection.use {
        runApplication(it)
    }
}

private fun runApplication(connection: Connection) {
    ImporterAktivitetslogg(Dispatcher("arbeidstabell_step2", connection))
        .migrate(connection)
}

