package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.sparsom.AktivitetFactory
import no.nav.helse.sparsom.AktivitetFactory.Companion.objectMapper
import no.nav.helse.sparsom.db.AktivitetDao
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.log
import kotlin.system.measureTimeMillis

internal class V4__Dataimport : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val dao = AktivitetDao({ context.connection }, false)
        val factory = AktivitetFactory(dao)

        var counter = 0
        var pageCount = 0
        var rows: Int
        do {
            val offset = pageCount * BATCH_SIZE
            rows = 0
            pageCount += 1
            measureTimeMillis {
                logg.info("henter $BATCH_SIZE fra $offset")
                context.connection.prepareStatement("SELECT fnr, data FROM aktivitetslogg ORDER BY fnr LIMIT $BATCH_SIZE OFFSET $offset").use { statement ->
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            measureTimeMillis {
                                rows += 1
                                counter += 1
                                val ident = rs.getLong(1).toString().padStart(11, '0')
                                val aktivitetslogg = normalizeJson(objectMapper.readTree(rs.getString(2)))
                                factory.aktiviteter(aktivitetslogg, ident, null)
                            }.also {
                                val snitt = 1000.0 / it
                                logg.info("[${counter.toString().padEnd(7)}] brukte $it ms på å importere hele aktivitetsloggen | snitt $snitt personer i sekundet")
                            }
                        }
                    }
                }
            }.also {
                logg.info("batch [${pageCount.toString().padEnd(4)}] ferdig på $it ms | $rows personer hentet | snitt ${it/rows.toDouble()} ms per person")
            }
        } while (rows > 0)
    }

    private companion object {
        private const val BATCH_SIZE = 500
        private val logg = LoggerFactory.getLogger(AktivitetDao::class.java)
        private val tidsstempelformat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private fun normalizeJson(original: JsonNode): List<JsonNode> {
            val kontekster = original.path("kontekster").map {
                (it as ObjectNode)
                it.put("konteksttype", it.path("kontekstType").asText())
                it.set<JsonNode>("kontekstmap", it.path("kontekstMap").deepCopy())
                it.remove("kontekstType")
                it.remove("kontekstMap")
                it
            }
            return original.path("aktiviteter").mapNotNull { aktivitet ->
                (aktivitet as ObjectNode)
                tilNivå(aktivitet.path("alvorlighetsgrad").asText())?.let { nivå ->
                    aktivitet.put("nivå", nivå)
                    aktivitet.put("tidsstempel", LocalDateTime.parse(aktivitet.path("tidsstempel").asText(), tidsstempelformat).toString())
                    val aktivitetKontekster = aktivitet.path("kontekster")
                        .map { it.intValue() }
                        .map { kontekster[it] }
                    aktivitet.replace("kontekster", objectMapper.createArrayNode().addAll(aktivitetKontekster))
                    aktivitet.remove("alvorlighetsgrad")
                    aktivitet.remove("detaljer")
                    aktivitet
                }
            }
        }

        private fun tilNivå(value: String) = when (value) {
            "INFO" -> "INFO"
            "WARNING" -> "VARSEL"
            "ERROR" -> "FUNKSJONELL_FEIL"
            "SEVERE" -> "LOGISK_FEIL"
            else -> null
        }
    }
}