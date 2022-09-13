package no.nav.helse.sparsom.db

import no.nav.helse.sparsom.*
import no.nav.helse.sparsom.Aktivitet
import no.nav.helse.sparsom.Kontekst
import no.nav.helse.sparsom.KontekstNavn
import no.nav.helse.sparsom.KontekstVerdi
import no.nav.helse.sparsom.Melding
import org.intellij.lang.annotations.Language
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement.RETURN_GENERATED_KEYS
import javax.sql.DataSource

internal class AktivitetDao(private val connectionFactory: () -> Connection, private val closeAfterUse: Boolean) : AktivitetRepository {
    constructor(dataSource: DataSource): this({ dataSource.connection }, true)

    private companion object {
        private const val ROWS_PER_INSERT_STATEMENT = 10000

        private val logg = LoggerFactory.getLogger(AktivitetDao::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        @Language("PostgreSQL")
        private val PERSON_INSERT = """
            INSERT INTO personident(ident) VALUES(?) 
            ON CONFLICT(ident) 
            DO UPDATE SET ident=EXCLUDED.ident 
            RETURNING id;
        """

        @Language("PostgreSQL")
        private val MELDING_INSERT = """
            INSERT INTO melding(tekst) VALUES %s 
            ON CONFLICT(tekst)
            DO UPDATE SET tekst=EXCLUDED.tekst 
            RETURNING id;
        """

        @Language("PostgreSQL")
        private val KONTEKST_TYPE_INSERT = """
            INSERT INTO kontekst_type(type) VALUES %s
            ON CONFLICT (type) 
            DO UPDATE SET type=EXCLUDED.type
            RETURNING id;
        """

        @Language("PostgreSQL")
        private val KONTEKST_NAVN_INSERT = """
            INSERT INTO kontekst_navn(navn) VALUES %s
            ON CONFLICT (navn) 
            DO UPDATE SET navn=EXCLUDED.navn
            RETURNING id;
        """
        @Language("PostgreSQL")
        private val KONTEKST_VERDI_INSERT = """
            INSERT INTO kontekst_verdi(verdi) VALUES %s
            ON CONFLICT (verdi) 
            DO UPDATE SET verdi=EXCLUDED.verdi
            RETURNING id;
        """
        @Language("PostgreSQL")
        private val AKTIVITET_INSERT = """
            INSERT INTO aktivitet(melding_id, personident_id, hendelse_id, level, tidsstempel, aktivitet_uuid) 
            VALUES %s
            ON CONFLICT (aktivitet_uuid) 
            DO UPDATE SET level=EXCLUDED.level
            RETURNING id;
        """
        @Language("PostgreSQL")
        private val AKTIVITET_KONTEKST_INSERT = """
            INSERT INTO aktivitet_kontekst(aktivitet_id, kontekst_type_id, kontekst_navn_id, kontekst_verdi_id) 
            VALUES %s
            ON CONFLICT DO NOTHING;
        """

    }

    private fun makeConnection(block: (Connection) -> Unit) {
        if (!closeAfterUse) return block(connectionFactory())
        return connectionFactory().use(block)
    }

    override fun lagre(aktiviteter: List<Aktivitet>, meldinger: Collection<Melding>, konteksttyper: Collection<KontekstType>, kontekstNavn: Collection<KontekstNavn>, kontekstVerdi: Collection<KontekstVerdi>, personident: String, hendelseId: Long?) {
        makeConnection { connection ->
            konteksttyper.chunked(ROWS_PER_INSERT_STATEMENT).forEach { chunk ->
                val sql = String.format(KONTEKST_TYPE_INSERT, chunk.indices.joinToString { "(?)" })
                connection.prepareStatement(sql, RETURN_GENERATED_KEYS).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreKontekstType(statement, index)
                        index += 1
                    }
                    retryDeadlock(statement)
                    statement.generatedKeys.use { rs ->
                        index = 0
                        while (rs.next()) {
                            val id = rs.getLong(1)
                            chunk[index].typeId(id)
                            index += 1
                        }
                        check(index == chunk.size) { "lagret ulikt antall" }
                    }
                }
            }

            kontekstNavn.chunked(ROWS_PER_INSERT_STATEMENT).forEach { chunk ->
                val sql = String.format(KONTEKST_NAVN_INSERT, chunk.indices.joinToString { "(?)" })
                connection.prepareStatement(sql, RETURN_GENERATED_KEYS).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreKontekstNavn(statement, index)
                        index += 1
                    }
                    retryDeadlock(statement)
                    statement.generatedKeys.use { rs ->
                        index = 0
                        while (rs.next()) {
                            val id = rs.getLong(1)
                            chunk[index].navnId(id)
                            index += 1
                        }
                        check(index == chunk.size) {
                            "lagret ulikt antall"
                        }
                    }
                }
            }

            kontekstVerdi.chunked(ROWS_PER_INSERT_STATEMENT).forEach { chunk ->
                val sql = String.format(KONTEKST_VERDI_INSERT, chunk.indices.joinToString { "(?)" })
                connection.prepareStatement(sql, RETURN_GENERATED_KEYS).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreKontekstVerdi(statement, index)
                        index += 1
                    }
                    retryDeadlock(statement)
                    statement.generatedKeys.use { rs ->
                        index = 0
                        while (rs.next()) {
                            val id = rs.getLong(1)
                            chunk[index].verdiId(id)
                            index += 1
                        }
                        check(index == chunk.size) {
                            "lagret ulikt antall"
                        }
                    }
                }
            }

            var personidentId: Long = 0L
            connection.prepareStatement(PERSON_INSERT, RETURN_GENERATED_KEYS).use { statement ->
                statement.setString(1, personident)
                retryDeadlock(statement)
                statement.generatedKeys.use { rs ->
                    rs.next()
                    personidentId = rs.getLong(1)
                }
                check(personidentId != 0L) { "har ikke personidentId" }
            }

            meldinger.chunked(ROWS_PER_INSERT_STATEMENT).forEach { chunk ->
                val sql = String.format(MELDING_INSERT, chunk.indices.joinToString { "(?)" })
                connection.prepareStatement(sql, RETURN_GENERATED_KEYS).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreMelding(statement, index)
                        index += 1
                    }
                    retryDeadlock(statement)
                    statement.generatedKeys.use { rs ->
                        index = 0
                        while (rs.next()) {
                            val id = rs.getLong(1)
                            chunk[index].meldingId(id)
                            index += 1
                        }
                        check(index == chunk.size) {
                            "forventet å få id for alle meldinger, uavhengig om det er duplikater"
                        }
                    }
                }
            }
            aktiviteter.chunked(ROWS_PER_INSERT_STATEMENT).forEach { chunk ->
                val sql = String.format(AKTIVITET_INSERT, chunk.indices.joinToString { "(?, ?, ?, CAST(? AS LEVEL), CAST(? AS timestamptz), CAST(? AS uuid))" })
                connection.prepareStatement(sql, RETURN_GENERATED_KEYS).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreAktivitet(statement, index, personidentId, hendelseId)
                        index += 6
                    }
                    retryDeadlock(statement)
                    logg.info("${chunk.size} aktiviteter ble lagret")
                    statement.generatedKeys.use { rs ->
                        index = 0
                        while (rs.next()) {
                            chunk[index].aktivitetId(rs.getLong(1))
                            index += 1
                        }
                        check(index == chunk.size) {
                            "forventet å få id for alle meldinger, uavhengig om det er duplikater"
                        }
                    }
                }
                chunk
                    .flatMap { it.kobleAktivitetOgKontekst() }
                    .chunked(ROWS_PER_INSERT_STATEMENT).forEach { aktivitetKontekster ->
                        val sql2 = String.format(AKTIVITET_KONTEKST_INSERT, aktivitetKontekster.indices.joinToString { "(?, ?, ?, ?)" })
                        connection.prepareStatement(sql2).use { statement ->
                            var index = 1
                            aktivitetKontekster.forEach { rad ->
                                statement.setLong(index + 0, rad[0])
                                statement.setLong(index + 1, rad[1])
                                statement.setLong(index + 2, rad[2])
                                statement.setLong(index + 3, rad[3])
                                index += 4
                            }
                            retryDeadlock(statement)
                        }
                    }
            }
        }
    }

    private fun retryDeadlock(statement: PreparedStatement, retryCount: Int = 0) {
        try {
            statement.execute()
        } catch (err: PSQLException) {
            if (retryCount < 30 && err.message?.contains("deadlock detected") == true) {
                logg.info("forsøker på nytt pga. deadlock")
                return retryDeadlock(statement, retryCount + 1)
            }
            throw err
        }
    }
}