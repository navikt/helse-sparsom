package no.nav.helse.sparsom.opprydding

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.*
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.time.Duration
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("no.nav.helse.sparsom.opprydding.App")
private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val env = System.getenv()

    val app = RapidApplication.create(env).apply {
        val dp = DataSourceProvider(hikariConfig(env))
        Opprydding(this, dp::datasource)
    }

    app.start()
}

private fun hikariConfig(env: Map<String, String>): HikariConfig {
    val databaseInstance = requireNotNull(env["DATABASE_INSTANCE"]) { "database instance must be set" }
    val databaseRegion: String = requireNotNull(env["DATABASE_REGION"]) { "DATABASE_REGION må settes" }
    val gcpProjectId: String = requireNotNull(env["GCP_TEAM_PROJECT_ID"]) { "GCP_TEAM_PROJECT_ID må settes" }
    val databaseName: String = requireNotNull(env["DATABASE_SPARSOM_OPPRYDDING_DATABASE"]) { "databasenavn må settes" }
    return HikariConfig().apply {
        jdbcUrl = String.format(
            "jdbc:postgresql:///%s?%s&%s",
            databaseName,
            "cloudSqlInstance=$gcpProjectId:$databaseRegion:$databaseInstance",
            "socketFactory=com.google.cloud.sql.postgres.SocketFactory"
        )
        username = requireNotNull(env["DATABASE_SPARSOM_OPPRYDDING_USERNAME"]) { "brukernavn må settes" }
        password = requireNotNull(env["DATABASE_SPARSOM_OPPRYDDING_PASSWORD"]) { "passord må settes" }
        initializationFailTimeout = Duration.ofMinutes(1).toMillis()
        connectionTimeout = Duration.ofMinutes(1).toMillis()
        maximumPoolSize = 1
    }
}

private class DataSourceProvider(private val config: HikariConfig) {
    private val hikaridatasource by lazy {
        HikariDataSource(config)
    }

    fun datasource() = hikaridatasource
}

private class Opprydding(rapidsConnection: RapidsConnection, private val dataSourceProvider: () -> DataSource) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "slett_person")
                it.requireKey("@id", "fødselsnummer")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val ident = packet["fødselsnummer"].asText()
        sessionOf(dataSourceProvider()).use { session ->
            val identId = session.run(queryOf(FINN_IDENT_ID_STMT, ident).map { it.long("id") }.asList).singleOrNullOrThrow() ?: return@use
            log.info("sletter testdata for testperson $ident")
            sikkerLogg.info("sletter testdata for testperson $ident")
            session.run(queryOf(SLETT_AKTIVITET_STMT, identId).asExecute)
            session.run(queryOf(SLETT_HENDELSE_STMT, identId).asExecute)
            session.run(queryOf(SLETT_IDENT_STMT, identId).asExecute)
        }
    }

    private companion object {
        @Language("PostgreSQL")
        private const val FINN_IDENT_ID_STMT = """select id from personident where ident=?;"""
        @Language("PostgreSQL")
        private const val SLETT_IDENT_STMT = """delete from personident where id = ?;"""
        @Language("PostgreSQL")
        private const val SLETT_HENDELSE_STMT = """delete from hendelse where personident_id = ?;"""
        @Language("PostgreSQL")
        private const val SLETT_AKTIVITET_STMT = """delete from aktivitet where personident_id = ?;"""

        private fun <R> Collection<R>.singleOrNullOrThrow() =
            if (size < 2) this.firstOrNull()
            else throw IllegalStateException("Listen inneholder mer enn ett element!")
    }
}
