package no.nav.helse.sparsom.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sparsom.Nivå
import org.apache.commons.codec.digest.DigestUtils
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import javax.sql.DataSource

internal class AktivitetDao(private val dataSource: () -> DataSource): AktivitetRepository {
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
                val aktivitetId = tx.aktivitet(nivå, melding, tidsstempel, hash) ?: return@transaction
                val kontekstIder = tx.kontekster(kontekster)
                tx.koble(aktivitetId, kontekstIder, hendelseId)
            }
        }
    }

    private fun hash(nivå: Nivå, melding: String, tidsstempel: LocalDateTime, kontekster: List<Triple<String, String, String>>): String {
        val toDigest = "$nivå$melding$tidsstempel${kontekster.joinToString("") { "${it.first}${it.second}${it.third}" }}"
        return DigestUtils.sha3_256Hex(toDigest)
    }

    private fun TransactionalSession.aktivitet(nivå: Nivå, melding: String, tidsstempel: LocalDateTime, hash: String): Long? {
        @Language("PostgreSQL")
        val query = "INSERT INTO aktivitet (level, melding, tidsstempel, hash) VALUES (CAST(? as LEVEL), ?, ?, ?) ON CONFLICT (hash) DO NOTHING"
        return run(queryOf(query, nivå.toString(), melding, tidsstempel, hash).asUpdateAndReturnGeneratedKey)
    }

    private fun TransactionalSession.kontekster(kontekster: List<Triple<String, String, String>>): List<Long> {
        return kontekster.map { (type, identifikatornavn, identifikator) ->
            lagreKontekst(type, identifikatornavn, identifikator) ?: finnKontekst(type, identifikatornavn, identifikator)
        }
    }

    private fun TransactionalSession.lagreKontekst(type: String, identifikatornavn: String, identifikator: String): Long? {
        @Language("PostgreSQL")
        val query = "INSERT INTO kontekst (type, identifikatornavn, identifikator) VALUES (?, ?, ?) ON CONFLICT(type, identifikatornavn, identifikator) DO NOTHING"
        return run(queryOf(query, type, identifikatornavn, identifikator).asUpdateAndReturnGeneratedKey)
    }

    private fun TransactionalSession.finnKontekst(type: String, identifikatornavn: String, identifikator: String): Long {
        @Language("PostgreSQL")
        val query = "SELECT id FROM kontekst WHERE type = ? AND identifikatornavn = ? AND identifikator = ?"
        return requireNotNull(run(queryOf(query, type, identifikatornavn, identifikator).map { it.long(1) }.asSingle))
    }

    private fun TransactionalSession.koble(aktivitetId: Long, kontekstIder: List<Long>, hendelseId: Long) {
        kontekstIder.forEach {kontekstId ->
            @Language("PostgreSQL")
            val query = "INSERT INTO aktivitet_kontekst (aktivitet_ref, kontekst_ref, hendelse_ref) VALUES (?, ?, ?)"
            run(queryOf(query, aktivitetId, kontekstId, hendelseId).asExecute)
        }
    }
}