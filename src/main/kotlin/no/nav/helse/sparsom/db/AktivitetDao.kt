package no.nav.helse.sparsom.db

import no.nav.helse.sparsom.Aktivitet
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Statement.RETURN_GENERATED_KEYS
import javax.sql.DataSource

internal class AktivitetDao(private val connectionFactory: () -> Connection, private val closeAfterUse: Boolean) : AktivitetRepository {
    constructor(dataSource: DataSource): this({ dataSource.connection }, true)

    private companion object {
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
            INSERT INTO melding(tekst) VALUES(?) 
            ON CONFLICT(tekst)
            DO UPDATE SET tekst=EXCLUDED.tekst 
            RETURNING id;
        """

        @Language("PostgreSQL")
        private val KONTEKST_TYPE_INSERT = """
            INSERT INTO kontekst_type(type) VALUES(?) 
            ON CONFLICT (type) 
            DO UPDATE SET type=EXCLUDED.type
            RETURNING id;
        """

        @Language("PostgreSQL")
        private val KONTEKST_NAVN_INSERT = """
            INSERT INTO kontekst_navn(navn) VALUES(?) 
            ON CONFLICT (navn) 
            DO UPDATE SET navn=EXCLUDED.navn
            RETURNING id;
        """
        @Language("PostgreSQL")
        private val KONTEKST_VERDI_INSERT = """
            INSERT INTO kontekst_verdi(verdi) VALUES(?) 
            ON CONFLICT (verdi) 
            DO UPDATE SET verdi=EXCLUDED.verdi
            RETURNING id;
        """
        @Language("PostgreSQL")
        private val AKTIVITET_INSERT = """
            INSERT INTO aktivitet(melding_id, personident_id, hendelse_id, level, tidsstempel, hash) 
            VALUES(?, ?, ?, CAST(? AS LEVEL), CAST(? AS timestamptz), ?) 
            ON CONFLICT (hash) 
            DO UPDATE SET level=EXCLUDED.level
            RETURNING id;
        """
        @Language("PostgreSQL")
        private val AKTIVITET_KONTEKST_INSERT = """
            INSERT INTO aktivitet_kontekst(aktivitet_id, kontekst_type_id, kontekst_navn_id, kontekst_verdi_id) 
            VALUES(?, ?, ?, ?) 
            ON CONFLICT DO NOTHING;
        """

    }

    private fun makeConnection(block: (Connection) -> Unit) {
        if (!closeAfterUse) return block(connectionFactory())
        return connectionFactory().use(block)
    }

    override fun lagre(aktiviteter: List<Aktivitet>, personident: String, hendelseId: Long?) {
        makeConnection { connection ->
            var personidentId: Long = 0L
            connection.prepareStatement(PERSON_INSERT, RETURN_GENERATED_KEYS).use { statement ->
                statement.setString(1, personident)
                statement.execute()
                statement.generatedKeys.use { rs ->
                    rs.next()
                    personidentId = rs.getLong(1)
                }
                check(personidentId != 0L) { "har ikke personidentId" }
            }
            connection.prepareStatement(MELDING_INSERT, RETURN_GENERATED_KEYS).use { statement ->
                aktiviteter.forEach { it.lagreMelding(statement) }
                statement.executeLargeBatch()
                statement.generatedKeys.use { rs ->
                    var index = 0
                    while (rs.next()) {
                        val id = rs.getLong(1)
                        aktiviteter[index].meldingId(id)
                        index += 1
                    }
                    check(index == aktiviteter.size) {
                        "forventet å få id for alle meldinger, uavhengig om det er duplikater"
                    }
                }
            }
            connection.prepareStatement(KONTEKST_TYPE_INSERT, RETURN_GENERATED_KEYS).use { statement ->
                aktiviteter.forEach { it.lagreKontekstType(statement) }
                statement.executeLargeBatch()
                statement.generatedKeys.use { rs ->
                    while (rs.next()) {
                        val id = rs.getLong(1)
                        check(aktiviteter.any { it.kontekstTypeId(id) }) {
                            "forventet at noen trengte id til konteksttype"
                        }
                    }
                }
            }
            connection.prepareStatement(KONTEKST_NAVN_INSERT, RETURN_GENERATED_KEYS).use { statement ->
                aktiviteter.forEach { it.lagreKontekstNavn(statement) }
                statement.executeLargeBatch()
                statement.generatedKeys.use { rs ->
                    while (rs.next()) {
                        val id = rs.getLong(1)
                        check(aktiviteter.any { it.kontekstNavnId(id) }) {
                            "forventet at noen trengte id til kontekstnavn"
                        }
                    }
                }
            }
            connection.prepareStatement(KONTEKST_VERDI_INSERT, RETURN_GENERATED_KEYS).use { statement ->
                aktiviteter.forEach { it.lagreKontekstVerdi(statement) }
                statement.executeLargeBatch()
                statement.generatedKeys.use { rs ->
                    while (rs.next()) {
                        val id = rs.getLong(1)
                        check(aktiviteter.any { it.kontekstVerdiId(id) }) {
                            "forventet at noen trengte id til kontekstverdi"
                        }
                    }
                }
            }
            connection.prepareStatement(AKTIVITET_INSERT, RETURN_GENERATED_KEYS).use { statement ->
                aktiviteter.forEach { it.lagreAktivitet(statement, personidentId, hendelseId) }
                statement.executeLargeBatch().onEachIndexed { index, affectedRows ->
                    aktiviteter[index].bleLagret(affectedRows == 1L)
                }.sum().also {
                    logg.info("$it aktiviteter ble lagret")
                }
                statement.generatedKeys.use { rs ->
                    var index = 0
                    while (rs.next()) {
                        aktiviteter[index].aktivitetId(rs.getLong(1))
                        index += 1
                    }
                    check(index == aktiviteter.size) {
                        "forventet å få id for alle meldinger, uavhengig om det er duplikater"
                    }
                }
            }
            connection.prepareStatement(AKTIVITET_KONTEKST_INSERT).use { statement ->
                aktiviteter.forEach { it.kobleAktivitetOgKontekst(statement) }
                statement.executeLargeBatch()
            }
        }
    }
}