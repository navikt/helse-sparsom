package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotliquery.param
import no.nav.helse.sparsom.AktivitetFactory.Companion.objectMapper
import no.nav.helse.sparsom.db.AktivitetDao
import org.apache.commons.codec.digest.DigestUtils
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.sql.Statement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

internal class V4__Dataimport : BaseJavaMigration() {

    override fun migrate(context: Context) {
        context.connection.createStatement().use {
            it.execute(datalasttabeller)
        }

        var counter = 0
        var pageCount = 0
        var rows: Int
        context.connection.prepareStatement(PERSON_INSERT, Statement.RETURN_GENERATED_KEYS).use { personStatement ->
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
                                    var personidentId: Long = 0L
                                    personStatement.setString(1, ident)
                                    personStatement.execute()
                                    personStatement.generatedKeys.use { rs ->
                                        rs.next()
                                        personidentId = rs.getLong(1)
                                    }
                                    check(personidentId != 0L)

                                    val aktivitetslogg = normalizeJson(objectMapper.readTree(rs.getString(2)))
                                    aktivitetslogg.chunked(13_000).forEach { aktivitetsloggChunk ->
                                        var paramIndex = 1
                                        val aktivitetParams = aktivitetsloggChunk.joinToString {
                                            "(?, CAST(? as LEVEL), ?, CAST(? as timestamptz), ?)"
                                        }
                                        context.connection.prepareStatement(INSERT_AKTIVITET + aktivitetParams, Statement.RETURN_GENERATED_KEYS)
                                            .use { aktivitetStatement ->
                                                aktivitetsloggChunk.forEach { aktivitet ->
                                                    val nivå = aktivitet.path("nivå").asText()
                                                    val melding = aktivitet.path("melding").asText()
                                                    val tidsstempel = aktivitet.path("tidsstempel").asText()
                                                    val kontekster = aktivitet.path("kontekster").flatMap { kontekst ->
                                                        val type = kontekst.path("konteksttype").asText()
                                                        val detaljer = (kontekst.path("kontekstmap") as ObjectNode)
                                                        detaljer.fields().asSequence().map { (navn, verdi) ->
                                                            "$type$navn${verdi.asText()}"
                                                        }.toList()
                                                    }
                                                    val hash: String = DigestUtils.sha3_256Hex("$nivå$melding$tidsstempel${kontekster.joinToString("")}")
                                                    aktivitetStatement.setLong(paramIndex, personidentId)
                                                    paramIndex += 1
                                                    aktivitetStatement.setString(paramIndex, nivå)
                                                    paramIndex += 1
                                                    aktivitetStatement.setString(paramIndex, melding)
                                                    paramIndex += 1
                                                    aktivitetStatement.setString(paramIndex, tidsstempel)
                                                    paramIndex += 1
                                                    aktivitetStatement.setString(paramIndex, hash)
                                                    paramIndex += 1
                                                }

                                                measureTimeMillis {
                                                    aktivitetStatement.execute()
                                                }.also {
                                                    logg.info("tok $it ms å inserte aktiviteter")
                                                }
                                                aktivitetStatement.generatedKeys.use { rs ->
                                                    var index = 0
                                                    while (rs.next()) {
                                                        (aktivitetsloggChunk[index] as ObjectNode).put("aktivitetId", rs.getLong(1))
                                                        index += 1
                                                    }
                                                    check(index == aktivitetsloggChunk.size)
                                                }
                                            }
                                    }

                                    measureTimeMillis {
                                        aktivitetslogg.flatMap { aktivitet ->
                                            val id = aktivitet.path("aktivitetId").longValue()
                                            aktivitet.path("kontekster").flatMap { kontekst ->
                                                val type = kontekst.path("konteksttype").asText()
                                                val detaljer = (kontekst.path("kontekstmap") as ObjectNode)
                                                detaljer.fields().asSequence().map { (navn, verdi) ->
                                                    id to Triple(type, navn, verdi.asText())
                                                }.toList()
                                            }
                                        }.chunked(16250).forEach { chunk ->
                                            val kontekstparams = chunk.joinToString { "(?, ?, ?, ?)" }
                                            context.connection.prepareStatement(INSERT_KONTEKST + kontekstparams).use { kontekstStatement ->
                                                var paramIndex = 1
                                                chunk.forEach { (aktivitetId, resten) ->
                                                    kontekstStatement.setLong(paramIndex, aktivitetId)
                                                    paramIndex += 1
                                                    kontekstStatement.setString(paramIndex, resten.first)
                                                    paramIndex += 1
                                                    kontekstStatement.setString(paramIndex, resten.second)
                                                    paramIndex += 1
                                                    kontekstStatement.setString(paramIndex, resten.third)
                                                    paramIndex += 1
                                                }

                                                kontekstStatement.execute()
                                            }
                                        }
                                    }.also {
                                        logg.info("tok $it ms å inserte kontekster")
                                    }
                                }.also {
                                    val snitt = 1000.0 / it
                                    logg.info(
                                        "[${
                                            counter.toString().padEnd(7)
                                        }] brukte $it ms på å importere hele aktivitetsloggen | snitt $snitt personer i sekundet"
                                    )
                                }
                            }
                        }
                    }
                }.also {
                    logg.info("batch [${pageCount.toString().padEnd(4)}] ferdig på $it ms | $rows personer hentet | snitt ${it/rows.toDouble()} ms per person")
                }
            } while (rows > 0)
        }
    }

    private companion object {
        @Language("PostgreSQL")
        private val PERSON_INSERT = """
            INSERT INTO personident(ident) VALUES(?) 
            ON CONFLICT(ident) 
            DO UPDATE SET ident=EXCLUDED.ident 
            RETURNING id;
        """

        @Language("PostgreSQL")
        private val INSERT_AKTIVITET = """ 
            INSERT INTO aktivitet_denormalisert(personident_id, level, melding, tidsstempel, hash)
            VALUES
"""

        @Language("PostgreSQL")
        private val INSERT_KONTEKST = """ 
            INSERT INTO aktivitet_kontekst_denormalisert(aktivitet_id, kontekst_type, kontekst_navn, kontekst_verdi)
            VALUES 
"""

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

        @Language("PostgreSQL")
        private val datalasttabeller = """
CREATE TABLE aktivitet_denormalisert(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    personident_id BIGINT NOT NULL,
    hendelse_id BIGINT,
    level LEVEL NOT NULL,
    melding text not null,
    tidsstempel timestamptz NOT NULL,
    hash char(64) NOT NULL
);

CREATE TABLE aktivitet_kontekst_denormalisert (
    aktivitet_id BIGINT NOT NULL,
    kontekst_type varchar NOT NULL,
    kontekst_navn varchar NOT NULL,
    kontekst_verdi varchar not null
);
"""
    }
}