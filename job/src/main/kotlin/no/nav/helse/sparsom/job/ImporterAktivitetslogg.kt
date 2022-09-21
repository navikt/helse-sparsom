package no.nav.helse.sparsom.job

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.sparsom.*
import no.nav.helse.sparsom.db.AktivitetDao
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

internal class ImporterAktivitetslogg(private val dispatcher: Dispatcher) {

    fun migrate(connection: Connection) = connection.use {
        connection.autoCommit = true
        try {
            utførMigrering(AktivitetDao({ connection }, false), connection)
        } catch (e: Exception) {
            if (!connection.autoCommit) connection.rollback()
            throw e
        }
    }

    private fun utførMigrering(dao: AktivitetDao, connection: Connection) {
        connection.prepareStatement("SELECT fnr, data FROM aktivitetslogg WHERE fnr=? LIMIT 1;").use { fetchAktivitetsloggStatement ->
            var arbeid: Work? = dispatcher.hentArbeid() ?: return@use
            while (arbeid != null) {
                /* utfør arbeid */
                check(connection.autoCommit)
                utførArbeid(dao, fetchAktivitetsloggStatement, arbeid)
                log.info("committer ferdig utført arbeid")
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
                val typer = mutableMapOf<String, KontekstType>()
                val navn = mutableMapOf<String, KontekstNavn>()
                val verdier = mutableMapOf<String, KontekstVerdi>()
                val meldinger = mutableMapOf<String, Melding>()

                val original = objectMapper.readTree(rs.getString("data"))
                val kontekster = original.path("kontekster").map {
                    val kontekstverdier = mutableMapOf<KontekstNavn, KontekstVerdi>()

                    it.path("kontekstMap").fields().forEach { (kontekstNavn, kontekstVerdi) ->
                        val kn = navn.getOrPut(kontekstNavn) { KontekstNavn(kontekstNavn) }
                        val kv = verdier.getOrPut(kontekstVerdi.asText()) { KontekstVerdi(kontekstVerdi.asText()) }
                        kontekstverdier[kn] = kv
                    }
                    val type = it.path("kontekstType").asText()
                    Kontekst(typer.getOrPut(type) { KontekstType(type) }, kontekstverdier)
                }
                val aktiviteter = normalizeJson(original, kontekster, meldinger)
                dao.lagre(aktiviteter, meldinger.values, typer.values, navn.values, verdier.values, ident.toString().padStart(11, '0'), null)
            }
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ImporterAktivitetslogg::class.java)
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())

        private val tidsstempelformat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private fun normalizeJson(original: JsonNode, kontekster: List<Kontekst>, meldinger: MutableMap<String, Melding>): List<Aktivitet> {
            return original.path("aktiviteter").mapNotNull { aktivitet ->
                tilNivå(aktivitet.path("alvorlighetsgrad").asText())?.let { nivå ->
                    val aktivitetKontekster = aktivitet.path("kontekster")
                        .map { it.intValue() }
                        .map { kontekster[it] }
                    Aktivitet(
                        id = UUID.fromString(aktivitet.path("id").asText()),
                        nivå = nivå,
                        melding = meldinger.getOrPut(aktivitet.path("melding").asText()) { Melding(aktivitet.path("melding").asText()) },
                        tidsstempel = LocalDateTime.parse(aktivitet.path("tidsstempel").asText(), tidsstempelformat),
                        kontekster = aktivitetKontekster
                    )
                }
            }
        }

        private fun tilNivå(value: String) = when (value) {
            "INFO" -> Nivå.INFO
            "WARN" -> Nivå.VARSEL
            "BEHOV" -> Nivå.BEHOV
            "ERROR" -> Nivå.FUNKSJONELL_FEIL
            "SEVERE" -> Nivå.LOGISK_FEIL
            else -> null
        }
    }
}