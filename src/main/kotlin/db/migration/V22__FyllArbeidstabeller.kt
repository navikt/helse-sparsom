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

internal class V22__FyllArbeidstabeller : BaseJavaMigration() {

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
        context.connection.prepareStatement("INSERT INTO arbeidstabell_step1(ident) VALUES(?);").use { arbeidstabell_step1 ->
            spleisDataSource.use { ds ->
                log.info("Henter personer")
                ds.connection.use { spleisConnection ->
                    spleisConnection.autoCommit = false
                    spleisConnection.createStatement().use { stmt ->
                        stmt.executeQuery("SELECT fnr FROM unike_person").use { rs ->
                            while (rs.next()) {
                                arbeidstabell_step1.setString(1, rs.getString("fnr"))
                                arbeidstabell_step1.addBatch()
                            }
                        }
                    }
                }
            }
            arbeidstabell_step1.executeBatch()
            context.connection.createStatement().use { stmt ->
                stmt.execute("INSERT INTO arbeidstabell_step2(ident) SELECT ident FROM arbeidstabell_step1;")
            }
        }
    }

    private companion object {
        private const val INSERT_BATCH_SIZE = 5000
        private const val BATCH_SIZE = 1000
        private val log = LoggerFactory.getLogger(V22__FyllArbeidstabeller::class.java)
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