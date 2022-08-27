package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.sparsom.AktivitetFactory
import no.nav.helse.sparsom.AktivitetFactory.Companion.objectMapper
import no.nav.helse.sparsom.db.AktivitetDao
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

internal class V4__Dataimport : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val dao = AktivitetDao({ context.connection }, false)
        val factory = AktivitetFactory(dao)
        context.connection.prepareStatement("SELECT fnr, data FROM aktivitetslogg").use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    measureTimeMillis {
                        val ident = rs.getLong(1).toString().padStart(11, '0')
                        val aktivitetslogg = normalizeJson(objectMapper.readTree(rs.getString(1)))
                        factory.aktiviteter(aktivitetslogg, ident, null)
                    }
                }
            }
        }
    }

    private companion object {
        private val tidsstempelformat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private fun normalizeJson(original: JsonNode): List<JsonNode> {
            val kontekster = original.path("kontekster").map {
                (it as ObjectNode)
                it.put("konteksttype", it.path("kontekstType").asText())
                it.set<JsonNode>("kontekstmap", it.path("kontekstMap").deepCopy())
                it.remove("kontekstType")
                it.remove("kontekstMap")
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