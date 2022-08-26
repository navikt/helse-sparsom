package db.migration

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.Duration
import kotlin.system.measureTimeMillis

internal class V2__Datalast : BaseJavaMigration() {

    private val env = System.getenv()
    private val config by lazy {
        HikariConfig().apply {
            jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", env["DATABASE_SPARSOM_HOST"], env["DATABASE_SPARSOM_PORT"], env["DATABASE_SPARSOM_DATABASE"])
            username = env["DATABASE_SPARSOM_USERNAME"]
            password = env["DATABASE_SPARSOM_PASSWORD"]
            initializationFailTimeout = Duration.ofMinutes(1).toMillis()
            connectionTimeout = Duration.ofMinutes(1).toMillis()
            maximumPoolSize = 1
        }
    }
    private val spleisDataSource by lazy { HikariDataSource(config) }

    override fun migrate(context: Context) {
        if (config.username.isNullOrBlank()) return log.info("Kjører _IKKE_ migrering fordi jdbc ikke er satt. Mangler konfig?")
        log.info("Kjører i gang migrering")
        spleisDataSource.use { ds ->
            ds.connection.autoCommit = false
            val personer = hentPersoner(ds.connection)
            var gjenstående = personer.size

            ds.connection.prepareStatement(SELECT_JSON).use { fetchStatement ->
                var tidBrukt = 0L
                var count = 0
                personer.forEach { (fnr, id) ->
                    tidBrukt += migrerPerson(fetchStatement, fnr, id)
                    count += 1
                    gjenstående -= 1
                    if (count % BATCH_SIZE == 0) {
                        count = 0
                        val snitt = tidBrukt / BATCH_SIZE.toDouble()
                        val gjenståendeTid = Duration.ofMillis((gjenstående * snitt).toLong())
                        log.info("brukt $tidBrukt ms på å hente 100 personer, snitt $snitt ms per person. gjenstående $gjenstående personer, ca ${gjenståendeTid.toDaysPart()} dager ${gjenståendeTid.toHoursPart()} timer ${gjenståendeTid.toSecondsPart()} sekunder gjenstående")
                    }
                }
            }
        }
    }

    private fun migrerPerson(fetchStatement: PreparedStatement, fnr: Long, id: Long): Long {
        fetchStatement.setLong(1, id)
        return measureTimeMillis {
            val rs = fetchStatement.executeQuery()
            fetchStatement.clearParameters()
            if (!rs.next()) {
                log.warn("Fant ikke json for rad id=$id")
            } else {
                val data = objectMapper.readTree(rs.getString("data"))
            }
        }
    }

    private fun hentPersoner(connection: Connection): Map<Long, Long> {
        val personer = mutableMapOf<Long, Long>()
        measureTimeMillis {
            connection.createStatement().use { selectStatement ->
                val rs = selectStatement.executeQuery(SELECT_PERSON)
                while (rs.next()) {
                    personer[rs.getLong("fnr")] = rs.getLong("id")
                }
            }
        }.also {
            log.info("tid brukt for å hente alle personer: $it ms")
        }
        return personer
    }

    private companion object {
        private const val BATCH_SIZE = 100
        private val log = LoggerFactory.getLogger(V2__Datalast::class.java)
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())

        // finner fnr og siste/nyeste id til alle personjsons i spleis
        @Language("PostgreSQL")
        private val SELECT_PERSON = """
            select p1.fnr, p1.id from unike_person up
            inner join person p1 on up.fnr = p1.fnr
            left join person p2 on up.fnr = p2.fnr and p2.id > p1.id
            where p2.id is null;
        """
        @Language("PostgreSQL")
        private val SELECT_JSON = """
            select data from person where id=?;
        """
    }
}