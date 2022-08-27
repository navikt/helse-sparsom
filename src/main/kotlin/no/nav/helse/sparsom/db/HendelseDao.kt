package no.nav.helse.sparsom.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class HendelseDao(private val dataSource: () -> DataSource): HendelseRepository {
    override fun lagre(fødselsnummer: String, hendelseId: UUID, json: String, tidsstempel: LocalDateTime): Long {
        return sessionOf(dataSource(), returnGeneratedKey = true).use { session ->
            session.transaction { tx ->
                tx.lagrePerson(fødselsnummer)
                tx.lagreHendelse(fødselsnummer, hendelseId, json, tidsstempel) ?: tx.finnHendelse(hendelseId)
            }
        }
    }

    private fun TransactionalSession.lagrePerson(fødselsnummer: String): Long? {
        @Language("PostgreSQL")
        val query = "INSERT INTO personident(ident) VALUES(?) ON CONFLICT(ident) DO NOTHING"
        return run(queryOf(query, fødselsnummer).asUpdateAndReturnGeneratedKey)
    }

    private fun TransactionalSession.lagreHendelse(fødselsnummer: String, hendelseId: UUID, json: String, tidsstempel: LocalDateTime): Long? {
        @Language("PostgreSQL")
        val query = """
            INSERT INTO hendelse(hendelse_id, personident_id, hendelse, tidsstempel) 
            VALUES (?, (SELECT id FROM personident WHERE ident = ? LIMIT 1), CAST(? as json), ?) 
            ON CONFLICT(hendelse_id) DO NOTHING
        """
        return run(queryOf(query, hendelseId, fødselsnummer, json, tidsstempel).asUpdateAndReturnGeneratedKey)
    }

    private fun TransactionalSession.finnHendelse(hendelseId: UUID): Long {
        @Language("PostgreSQL")
        val query = "SELECT id FROM hendelse WHERE hendelse_id = ? LIMIT 1;"
        return requireNotNull(run(queryOf(query, hendelseId).map { it.long(1) }.asSingle))
    }
}