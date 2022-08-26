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

internal class AktivitetDao(private val dataSource: () -> DataSource): AktivitetRepository {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }
    override fun lagre(
        nivå: Nivå,
        melding: String,
        tidsstempel: LocalDateTime,
        hendelseId: Long,
        kontekster: List<Triple<String, String, String>>
    ) {
        sessionOf(dataSource(), returnGeneratedKey = true).use { session ->
            session.transaction { tx ->
                val hash = hash(nivå, melding, tidsstempel, kontekster)
                val aktivitetId = tx.aktivitet(hendelseId, nivå, melding, tidsstempel, hash) ?: kotlin.run {
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
                tx.kontekster(kontekster)
                tx.koble(aktivitetId, kontekster)
            }
        }
    }

    private fun hash(nivå: Nivå, melding: String, tidsstempel: LocalDateTime, kontekster: List<Triple<String, String, String>>): String {
        val toDigest = "$nivå$melding$tidsstempel${kontekster.joinToString("") { "${it.first}${it.second}${it.third}" }}"
        return DigestUtils.sha3_256Hex(toDigest)
    }

    private fun TransactionalSession.aktivitet(hendelseId: Long, nivå: Nivå, melding: String, tidsstempel: LocalDateTime, hash: String): Long? {
        @Language("PostgreSQL")
        val query = "INSERT INTO aktivitet (hendelse_id, level, melding, tidsstempel, hash) VALUES (?, CAST(? as LEVEL), ?, ?, ?) ON CONFLICT (hash) DO NOTHING"
        return run(queryOf(query, hendelseId, nivå.toString(), melding, tidsstempel, hash).asUpdateAndReturnGeneratedKey)
    }

    private fun TransactionalSession.kontekster(kontekster: List<Triple<String, String, String>>) {
        @Language("PostgreSQL")
        val query = "INSERT INTO kontekst (type, identifikatornavn, identifikator) VALUES ${kontekster.joinToString { "(?, ?, ?)" }} ON CONFLICT(type, identifikatornavn, identifikator) DO NOTHING"
        run(queryOf(query, *kontekster.flatMap { it.toList() }.toTypedArray()).asExecute)
    }

    private fun TransactionalSession.koble(aktivitetId: Long, kontekster: List<Triple<String, String, String>>) {
        @Language("PostgreSQL")
        val query = """
                INSERT INTO aktivitet_kontekst (aktivitet_id, kontekst_id)
                SELECT ?, id FROM kontekst WHERE ${kontekster.joinToString(separator = " OR ") { "(type=? AND identifikatornavn=? AND identifikator=?)" }}
        """

        run(queryOf(query, *(listOf(aktivitetId) + kontekster.flatMap { it.toList() }).toTypedArray()).asExecute)
    }
}