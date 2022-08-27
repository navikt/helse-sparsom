package no.nav.helse.sparsom.db

import no.nav.helse.sparsom.Aktivitet
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.sql.Connection
import javax.sql.DataSource
import kotlin.math.log
import kotlin.system.measureTimeMillis

internal class AktivitetDao(private val connectionFactory: () -> Connection, private val closeAfterUse: Boolean) : AktivitetRepository {
    constructor(dataSource: DataSource): this({ dataSource.connection }, true)

    private companion object {
        private val logg = LoggerFactory.getLogger(AktivitetDao::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        @Language("PostgreSQL")
        private val PERSON_INSERT = """
            INSERT INTO person(ident) VALUES(?) 
            ON CONFLICT(ident) DO NOTHING;
        """
        @Language("PostgreSQL")
        private val HENDELSE_INSERT = """
            INSERT INTO hendelse(hendelse_id, personident_id, hendelse, tidsstempel) 
            VALUES (?, SELECT id FROM personident WHERE ident=? LIMIT 1, ?, ?) 
            ON CONFLICT (hendelse_id) DO NOTHING;
        """

        @Language("PostgreSQL")
        private val MELDING_INSERT = """
            INSERT INTO melding(tekst) VALUES(?) 
            ON CONFLICT (tekst) DO NOTHING;
        """

        @Language("PostgreSQL")
        private val KONTEKST_TYPE_INSERT = """
            INSERT INTO kontekst_type(type) VALUES(?) 
            ON CONFLICT (type) DO NOTHING;
        """

        @Language("PostgreSQL")
        private val KONTEKST_NAVN_INSERT = """
            INSERT INTO kontekst_navn(navn) VALUES(?) 
            ON CONFLICT (navn) DO NOTHING;
        """
        @Language("PostgreSQL")
        private val KONTEKST_VERDI_INSERT = """
            INSERT INTO kontekst_verdi(verdi) VALUES(?) 
            ON CONFLICT (verdi) DO NOTHING;
        """
        @Language("PostgreSQL")
        private val AKTIVITET_INSERT = """
            INSERT INTO aktivitet(melding_id, personident_id, hendelse_id, level, tidsstempel, hash) 
            VALUES(
                (SELECT id FROM melding WHERE tekst=? LIMIT 1),
                (SELECT id FROM personident WHERE ident=? LIMIT 1), 
                ?, 
                CAST(? AS LEVEL), 
                CAST(? AS timestamptz), 
                ?
            ) 
            ON CONFLICT (hash) DO NOTHING;
        """
        @Language("PostgreSQL")
        private val AKTIVITET_KONTEKST_INSERT = """
            INSERT INTO aktivitet_kontekst(aktivitet_id, kontekst_type_id, kontekst_navn_id, kontekst_verdi_id) 
            VALUES(
                (SELECT id FROM aktivitet WHERE hash=? LIMIT 1), 
                (SELECT id FROM kontekst_type WHERE type=? LIMIT 1), 
                (SELECT id FROM kontekst_navn WHERE navn=? LIMIT 1),
                (SELECT id FROM kontekst_verdi WHERE verdi=? LIMIT 1)
            ) 
            ON CONFLICT DO NOTHING;
        """

    }

    private fun makeConnection(block: (Connection) -> Unit) {
        if (!closeAfterUse) return block(connectionFactory())
        return connectionFactory().use(block)
    }

    override fun lagre(aktiviteter: List<Aktivitet>, personident: String, hendelseId: Long?) {
        makeConnection { connection ->
            connection.prepareStatement(MELDING_INSERT).use { statement ->
                aktiviteter.forEach { it.lagreMelding(statement) }
                statement.executeLargeBatch()
            }
            connection.prepareStatement(KONTEKST_TYPE_INSERT).use { statement ->
                aktiviteter.forEach { it.lagreKontekstType(statement) }
                statement.executeLargeBatch()
            }
            connection.prepareStatement(KONTEKST_NAVN_INSERT).use { statement ->
                aktiviteter.forEach { it.lagreKontekstNavn(statement) }
                statement.executeLargeBatch()
            }
            connection.prepareStatement(KONTEKST_VERDI_INSERT).use { statement ->
                aktiviteter.forEach { it.lagreKontekstVerdi(statement) }
                statement.executeLargeBatch()
            }
            connection.prepareStatement(AKTIVITET_INSERT).use { statement ->
                aktiviteter.forEach { it.lagreAktivitet(statement, personident, hendelseId) }
                statement.executeLargeBatch().onEachIndexed { index, affectedRows ->
                    aktiviteter[index].bleLagret(affectedRows == 1L)
                }.sum().also {
                    logg.info("$it aktiviteter ble lagret")
                }
            }
            connection.prepareStatement(AKTIVITET_KONTEKST_INSERT).use { statement ->
                aktiviteter.forEach { it.kobleAktivitetOgKontekst(statement) }
                statement.executeLargeBatch()
            }
        }
    }
}