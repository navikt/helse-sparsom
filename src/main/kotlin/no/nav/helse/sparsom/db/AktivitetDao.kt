package no.nav.helse.sparsom.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.sparsom.Nivå
import org.apache.commons.codec.digest.DigestUtils
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import javax.sql.DataSource
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

internal class AktivitetDao(private val dataSource: () -> DataSource): AktivitetRepository {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(AktivitetDao::class.java)
    }
    @OptIn(ExperimentalTime::class)
    override fun lagre(
        nivå: Nivå,
        melding: String,
        tidsstempel: LocalDateTime,
        hendelseId: Long,
        kontekster: List<Triple<String, String, String>>
    ) {
        sessionOf(dataSource(), returnGeneratedKey = true).use { session ->
            session.transaction { tx ->
                val (hash, tidBruktHashing) = measureTimedValue {
                    hash(nivå, melding, tidsstempel, kontekster)
                }
                logg.info("Det tok ${tidBruktHashing.inWholeMilliseconds}ms å hashe")
                val aktivitetId = tx.aktivitet(nivå, melding, tidsstempel, hash) ?: kotlin.run {
                    sikkerlogg.info(
                        "Oppdaget kollisjon med {} for melding {}, {}, {} og {}. {}",
                        keyValue("hash", hash),
                        keyValue("nivå", nivå),
                        keyValue("melding", melding),
                        keyValue("tidsstempel", tidsstempel),
                        keyValue("hendelseId", hendelseId),
                        keyValue("Kontekster", kontekster)
                    )
                    return@transaction
                }
                val tidBruktKontekster = measureTimeMillis { tx.kontekster(aktivitetId, kontekster) }
                val tidBruktKobling = measureTimeMillis {
                    tx.koble(aktivitetId, kontekster, hendelseId)
                }
                logg.info("Det tok ${tidBruktKontekster}ms å inserte kontekster for {}", keyValue("aktivitetId", aktivitetId))
                logg.info("Det tok ${tidBruktKobling}ms å inserte koblinger for {}", keyValue("aktivitetId", aktivitetId))
            }
        }
    }

    private fun hash(nivå: Nivå, melding: String, tidsstempel: LocalDateTime, kontekster: List<Triple<String, String, String>>): String {
        val toDigest = "$nivå$melding$tidsstempel${kontekster.joinToString("") { "${it.first}${it.second}${it.third}" }}"
        return DigestUtils.sha3_256Hex(toDigest)
    }

    @OptIn(ExperimentalTime::class)
    private fun TransactionalSession.aktivitet(nivå: Nivå, melding: String, tidsstempel: LocalDateTime, hash: String): Long? {
        val (id, tidBrukt) = measureTimedValue {
            @Language("PostgreSQL")
            val query = "INSERT INTO aktivitet (level, melding, tidsstempel, hash) VALUES (CAST(? as LEVEL), ?, ?, ?) ON CONFLICT (hash) DO NOTHING"
            run(queryOf(query, nivå.toString(), melding, tidsstempel, hash).asUpdateAndReturnGeneratedKey)
        }
        logg.info("Det tok ${tidBrukt.inWholeMilliseconds}ms å inserte aktivitet for {}", keyValue("aktivitetId", id))
        return id
    }

    private fun TransactionalSession.kontekster(aktivitetId: Long, kontekster: List<Triple<String, String, String>>) {
        if (kontekster.isEmpty()) {
            sikkerlogg.warn("kontekster for hendelse med {} er tom", keyValue("aktivitetId", aktivitetId))
            return
        }
        @Language("PostgreSQL")
        val query = "INSERT INTO kontekst (type, identifikatornavn, identifikator) VALUES ${kontekster.joinToString { "(?, ?, ?)" }} ON CONFLICT(type, identifikatornavn, identifikator) DO NOTHING"
        run(queryOf(query, *kontekster.flatMap { it.toList() }.toTypedArray()).asExecute)
    }

    private fun TransactionalSession.koble(aktivitetId: Long, kontekster: List<Triple<String, String, String>>, hendelseId: Long) {
        if (kontekster.isEmpty()) {
            sikkerlogg.warn("kontekster for hendelse med {} er tom", keyValue("aktivitetId", aktivitetId))
            return
        }
        @Language("PostgreSQL")
        val query = "INSERT INTO aktivitet_kontekst (aktivitet_ref, kontekst_ref, hendelse_ref) VALUES ${kontekster.joinToString { "('$aktivitetId', (SELECT id FROM kontekst WHERE type=? AND identifikatornavn=? AND identifikator=?), '$hendelseId')" }}"
        run(queryOf(query, *kontekster.flatMap { it.toList() }.toTypedArray()).asExecute)
    }
}