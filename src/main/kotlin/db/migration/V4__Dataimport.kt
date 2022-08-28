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
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess
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
                val aktivitetslogg = mutableListOf<JsonNode>()
                measureTimeMillis {
                    var elapsed = 0L
                    logg.info("henter $BATCH_SIZE fra $offset")
                    context.connection.prepareStatement("SELECT fnr, data FROM aktivitetslogg ORDER BY fnr LIMIT $BATCH_SIZE OFFSET $offset").use { statement ->
                        statement.executeQuery().use { rs ->
                            while (rs.next()) {
                                elapsed += measureTimeMillis {
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

                                    aktivitetslogg.addAll(normalizeJson(objectMapper.readTree(rs.getString(2))).onEach {
                                        (it as ObjectNode).put("personidentId", personidentId)
                                    })
                                }.also {
                                    val snitt = 1000.0 / it
                                    logg.info(
                                        "[${counter.toString().padEnd(7)}] [$elapsed ms] | brukte $it ms på å importere hele aktivitetsloggen | snitt $snitt personer i sekundet"
                                    )
                                }
                            }
                        }
                        val stdParams = 0.until(AKTIVITETSLOGG_CHUNK_SIZE).joinToString {
                            "(?, CAST(? as LEVEL), ?, CAST(? as timestamptz), ?)"
                        }
                        context.connection.prepareStatement(INSERT_AKTIVITET + stdParams, Statement.RETURN_GENERATED_KEYS).use { aktivitetBatchStatement ->
                            val kontekstStdParams = 0.until(KONTEKST_CHUNK_SIZE).joinToString { "(?, ?, ?, ?)" }
                            context.connection.prepareStatement(INSERT_KONTEKST + kontekstStdParams).use { kontekstBatchStatement ->
                                lagreAktiviteter(context.connection, aktivitetBatchStatement, aktivitetslogg)
                                lagreKontekster(context.connection, kontekstBatchStatement, aktivitetslogg)

                                aktivitetBatchStatement.clearBatch()
                                kontekstBatchStatement.clearBatch()
                            }
                        }
                    }
                }.also {
                    logg.info("batch [${pageCount.toString().padEnd(4)}] ferdig på $it ms | $rows personer hentet | snitt ${it/rows.toDouble()} ms per person")
                    exitProcess(1)
                }
            } while (rows > 0)
        }
    }

    private fun lagreAktiviteter(connection: Connection, aktivitetBatchStatement: PreparedStatement, aktivitetslogg: List<JsonNode>) {
        aktivitetslogg.chunked(AKTIVITETSLOGG_CHUNK_SIZE).let {
            val chunkCount = it.size

            var paramIndex = 1
            it.forEachIndexed { index, aktivitetsloggChunk ->
                if ((index + 1) == chunkCount) return@forEachIndexed
                aktivitetsloggChunk.forEach {
                    addAktivitet(aktivitetBatchStatement, paramIndex, it.path("personidentId").longValue(), it)
                    paramIndex += 5
                }
                aktivitetBatchStatement.addBatch()
                paramIndex = 1
            }
            val sisteChunk = it.lastOrNull() ?: emptyList()

            val tid1 = measureTimeMillis {
                aktivitetBatchStatement.executeLargeBatch()
            }
            check(oppdaterPrimaryKey(aktivitetslogg, aktivitetBatchStatement) == (aktivitetslogg.size - sisteChunk.size))

            var tid2 = 0L
            if (sisteChunk.isNotEmpty()) {
                val aktivitetParams = sisteChunk.joinToString {
                    "(?, CAST(? as LEVEL), ?, CAST(? as timestamptz), ?)"
                }
                paramIndex = 1
                connection.prepareStatement(INSERT_AKTIVITET + aktivitetParams, Statement.RETURN_GENERATED_KEYS).use { aktivitetStatement ->
                    sisteChunk.forEach {
                        addAktivitet(aktivitetStatement, paramIndex, it.path("personidentId").longValue(), it)
                        paramIndex += 5
                    }

                    tid2 = measureTimeMillis {
                        aktivitetStatement.execute()
                    }
                    check(oppdaterPrimaryKey(sisteChunk, aktivitetStatement) == sisteChunk.size)
                }
            }
            logg.info("tok ${tid1+tid2} ms å inserte ${aktivitetslogg.size} aktiviteter")
        }
    }
    private fun addAktivitet(statement: PreparedStatement, paramIndex: Int, personidentId: Long, aktivitet: JsonNode) {
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
        statement.setLong(paramIndex, personidentId)
        statement.setString(paramIndex + 1, nivå)
        statement.setString(paramIndex + 2, melding)
        statement.setString(paramIndex + 3, tidsstempel)
        statement.setString(paramIndex + 4, hash)
    }

    private fun oppdaterPrimaryKey(noder: List<JsonNode>, statement: PreparedStatement): Int {
        var index = 0
        statement.generatedKeys.use { rs ->
            while (rs.next()) {
                (noder[index] as ObjectNode).put("aktivitetId", rs.getLong(1))
                index += 1
            }
        }
        return index
    }

    private fun lagreKontekster(connection: Connection, kontekstBatchStatement: PreparedStatement, aktivitetslogg: List<JsonNode>) {
        val kontekster = organiserKontekster(aktivitetslogg)
        measureTimeMillis {
            val chunks = kontekster.chunked(KONTEKST_CHUNK_SIZE)

            var paramIndex = 1
            chunks.forEachIndexed { index, chunk ->
                if ((index + 1) == chunks.size) return@forEachIndexed
                chunk.forEach { (aktivitetId, kontekst) ->
                    addKontekst(kontekstBatchStatement, paramIndex, aktivitetId, kontekst)
                    paramIndex += 4
                }
                kontekstBatchStatement.addBatch()
                paramIndex = 1
            }
            val sisteChunk = chunks.lastOrNull() ?: emptyList()
            kontekstBatchStatement.executeLargeBatch()
            if (sisteChunk.isNotEmpty()) {
                val kontekstparams = sisteChunk.joinToString { "(?, ?, ?, ?)" }
                connection.prepareStatement(INSERT_KONTEKST + kontekstparams).use { kontekstStatement ->
                    paramIndex = 1
                    sisteChunk.forEach { (aktivitetId, kontekst) ->
                        addKontekst(kontekstStatement, paramIndex, aktivitetId, kontekst)
                        paramIndex += 4
                    }

                    kontekstStatement.execute()
                }
            }
        }.also {
            logg.info("tok $it ms å inserte ${kontekster.size} kontekster")
        }
    }

    private fun organiserKontekster(aktivitetslogg: List<JsonNode>) =
        aktivitetslogg.flatMap { aktivitet ->
            val id = aktivitet.path("aktivitetId").longValue()
            aktivitet.path("kontekster").flatMap { kontekst ->
                val type = kontekst.path("konteksttype").asText()
                val detaljer = (kontekst.path("kontekstmap") as ObjectNode)
                detaljer.fields().asSequence().map { (navn, verdi) ->
                    id to Triple(type, navn, verdi.asText())
                }.toList()
            }
        }

    private fun addKontekst(statement: PreparedStatement, paramIndex: Int, aktivitetId: Long, kontekst: Triple<String, String, String>) {
        statement.setLong(paramIndex, aktivitetId)
        statement.setString(paramIndex + 1, kontekst.first)
        statement.setString(paramIndex + 2, kontekst.second)
        statement.setString(paramIndex + 3, kontekst.third)
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

        private const val AKTIVITETSLOGG_CHUNK_SIZE = 5000
        private const val KONTEKST_CHUNK_SIZE = 5000
        private const val BATCH_SIZE = 100
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