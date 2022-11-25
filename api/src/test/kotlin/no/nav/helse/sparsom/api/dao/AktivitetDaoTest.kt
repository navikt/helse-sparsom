package no.nav.helse.sparsom.api.dao

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Duration

internal class AktivitetDaoTest {
    private companion object {
        private val postgres = PostgreSQLContainer<Nothing>("postgres:14").apply {
            withReuse(true)
            withLabel("app-navn", "sparsom-api")
            start()
        }

        private val config = HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 2
            initializationFailTimeout = Duration.ofSeconds(30).toMillis()
        }
        private val dataSource = HikariDataSource(config)

        private val flyway = Flyway
            .configure()
            .dataSource(dataSource)
            .cleanDisabled(false)
            .load()
    }

    @BeforeEach
    fun setup() {
        flyway.clean()
        flyway.migrate()
    }

    @Test
    fun `ingen aktiviteter`() {
        val dao = AktivitetDao(dataSource)
        assertEquals(0, dao.hentAktiviteterFor("ident").size)
    }
}
