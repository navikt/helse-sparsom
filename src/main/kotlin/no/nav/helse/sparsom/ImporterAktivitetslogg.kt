package no.nav.helse.sparsom

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.sparsom.db.AktivitetDao
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class ImporterAktivitetslogg(private val dispatcher: Dispatcher) {

    fun migrate(connection: Connection) = connection.use {
        connection.autoCommit = false
        try {
            utførMigrering(AktivitetDao({ connection }, false), connection)
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }

    private fun utførMigrering(dao: AktivitetDao, connection: Connection) {
        connection.prepareStatement("SELECT fnr, data FROM aktivitetslogg WHERE fnr=? LIMIT 1;").use { fetchAktivitetsloggStatement ->
            var arbeid: Work? = dispatcher.hentArbeid() ?: return@use
            while (arbeid != null) {
                /* utfør arbeid */
                utførArbeid(dao, fetchAktivitetsloggStatement, arbeid)
                log.info("committer ferdig utført arbeid")
                connection.commit()
                arbeid = dispatcher.hentArbeid()
            }
        }
    }

    private fun utførArbeid(dao: AktivitetDao, fetchStatement: PreparedStatement, work: Work) {
        work.begin()
        migrerAktivitetslogg(dao, fetchStatement, work.detaljer().first())
        work.done()
    }

    private fun migrerAktivitetslogg(dao: AktivitetDao, fetchStatement: PreparedStatement, ident: Long) {
        fetchStatement.setLong(1, ident)
        fetchStatement.executeQuery().use { rs ->
            fetchStatement.clearParameters()
            while (rs.next()) {
                val aktiviteter = normalizeJson(objectMapper.readTree(rs.getString("data")))
                dao.lagre(aktiviteter, ident.toString().padStart(11, '0'), null)
            }
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ImporterAktivitetslogg::class.java)
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())

        private val tidsstempelformat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private fun normalizeJson(original: JsonNode): List<Aktivitet> {
            val kontekster = original.path("kontekster").map {
                Kontekst(it.path("kontekstType").asText(), objectMapper.convertValue(it.path("kontekstMap")))
            }
            return original.path("aktiviteter").mapNotNull { aktivitet ->
                tilNivå(aktivitet.path("alvorlighetsgrad").asText())?.let { nivå ->
                    val aktivitetKontekster = aktivitet.path("kontekster")
                        .map { it.intValue() }
                        .map { kontekster[it] }
                    Aktivitet(
                        nivå = nivå,
                        melding = aktivitet.path("melding").asText(),
                        tidsstempel = LocalDateTime.parse(aktivitet.path("tidsstempel").asText(), tidsstempelformat),
                        kontekster = aktivitetKontekster
                    )
                }
            }
        }

        private fun tilNivå(value: String) = when (value) {
            "INFO" -> Nivå.INFO
            "WARNING" -> Nivå.VARSEL
            "ERROR" -> Nivå.FUNKSJONELL_FEIL
            "SEVERE" -> Nivå.LOGISK_FEIL
            else -> null
        }
    }
}