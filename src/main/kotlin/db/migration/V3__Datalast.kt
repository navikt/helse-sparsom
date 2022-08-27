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

internal class V3__Datalast : BaseJavaMigration() {

    private val env = System.getenv()
    private val config by lazy {
        HikariConfig().apply {
            jdbcUrl = String.format(
                "jdbc:postgresql:///%s?%s&%s",
                env["DATABASE_SPARSOM_DATABASE"],
                "cloudSqlInstance=${env["GCP_TEAM_PROJECT_ID"]}:${env["DATABASE_SPARSOM_REGION"]}:${env["DATABASE_SPARSOM_INSTANCE"]}",
                "socketFactory=com.google.cloud.sql.postgres.SocketFactory"
            )
            username = env["DATABASE_SPARSOM_USERNAME"]
            password = env["DATABASE_SPARSOM_PASSWORD"]
            initializationFailTimeout = Duration.ofMinutes(1).toMillis()
            connectionTimeout = Duration.ofMinutes(1).toMillis()
        }
    }
    private val spleisDataSource by lazy { HikariDataSource(config) }

    override fun migrate(context: Context) {
        if (config.username.isNullOrBlank()) return log.info("Kjører _IKKE_ migrering fordi jdbc ikke er satt. Mangler konfig?")
        log.info("Kjører i gang migrering")
        spleisDataSource.use { ds ->
            log.info("Henter personer")
            ds.connection.autoCommit = false
            val personer = hentPersoner(ds.connection)
            var gjenstående = personer.size

            var tidBrukt = 0L
            var count = 0
            var insertBatchCount = 0
            context.connection.prepareStatement(INSERT).use { insertStatement ->
                ds.connection.prepareStatement(SELECT_JSON).use { fetchStatement ->
                    personer.values.chunked(BATCH_SIZE).forEach { ider ->
                        tidBrukt += migrerPersoner(insertStatement, fetchStatement, ider)
                        count += ider.size
                        insertBatchCount += ider.size
                        gjenstående -= ider.size
                        if (insertBatchCount >= INSERT_BATCH_SIZE) {
                            log.info("Utfører batch insert for $insertBatchCount personer")
                            insertBatchCount = 0
                            measureTimeMillis {
                                insertStatement.executeLargeBatch()
                                insertStatement.clearBatch()
                            }.also {
                                log.info("batch insert tok $it ms")
                            }
                        }
                        if (count % BATCH_SIZE == 0) {
                            count = 0
                            val snitt = tidBrukt / BATCH_SIZE.toDouble()
                            val gjenståendeTid = Duration.ofMillis((gjenstående * snitt).toLong())
                            log.info("[${insertBatchCount.toString().padStart(4, '0')} / ${INSERT_BATCH_SIZE}] brukt $tidBrukt ms på å hente $BATCH_SIZE personer, snitt $snitt ms per person. gjenstående $gjenstående personer, ca ${gjenståendeTid.toDaysPart()} dager ${gjenståendeTid.toHoursPart()} timer ${gjenståendeTid.toSecondsPart()} sekunder gjenstående")
                        }
                    }
                    if (insertBatchCount > 0) {
                        log.info("Utfører batch insert for $insertBatchCount personer")
                        insertBatchCount = 0
                        measureTimeMillis {
                            insertStatement.executeLargeBatch()
                            insertStatement.clearBatch()
                        }.also {
                            log.info("batch insert tok $it ms")
                        }
                    }
                }
            }
        }
    }

    private fun migrerPersoner(insertStatement: PreparedStatement, fetchStatement: PreparedStatement, ider: List<Long>): Long {
        ider.forEachIndexed { index, id -> fetchStatement.setLong(index + 1, id) }
        (ider.size until BATCH_SIZE).forEach { index ->
            fetchStatement.setLong(index + 1, 0)
        }
        return measureTimeMillis {
            val rs = fetchStatement.executeQuery()
            fetchStatement.clearParameters()
            while (rs.next()) {
                insertStatement.setLong(1, rs.getLong("fnr"))
                insertStatement.setString(2, objectMapper.readTree(rs.getString("data")).path("aktivitetslogg").toString())
                insertStatement.addBatch()
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
        private const val INSERT_BATCH_SIZE = 5000
        private const val BATCH_SIZE = 1000
        private val log = LoggerFactory.getLogger(V3__Datalast::class.java)
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
            select fnr,data from person where id IN (${0.until(BATCH_SIZE).joinToString { "?" }});
        """
        @Language("PostgreSQL")
        private val INSERT = """
            INSERT INTO aktivitetslogg (fnr, data) VALUES (?, ?);
        """
    }
}