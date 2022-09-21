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

class AktivitetDao(private val connectionFactory: () -> Connection, private val closeAfterUse: Boolean) : AktivitetRepository {
    constructor(dataSource: DataSource): this({ dataSource.connection }, true)

    private companion object {
        private const val ROWS_PER_INSERT_STATEMENT = 10000

        private val logg = LoggerFactory.getLogger(AktivitetDao::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        @Language("PostgreSQL")
        private val PERSON_INSERT = """
            with verdier as (
                select ident from (values %s) v(ident)
            ), ins as (
                insert into personident(ident)
                select ident from verdier
                on conflict do nothing
                returning id,ident
            )
            select id,ident from ins
            union all
            select p.id,v.ident from verdier v
            join personident p on p.ident = v.ident
            
        """

        @Language("PostgreSQL")
        private val MELDING_INSERT = """
            with verdier as (
                select tekst from (values %s) v(tekst)
            ), ins as (
                insert into melding(tekst)
                select tekst from verdier
                on conflict do nothing
                returning id,tekst
            )
            select id,tekst from ins
            union all
            select m.id,v.tekst from verdier v
            join melding m on m.tekst = v.tekst
        """

        @Language("PostgreSQL")
        private val KONTEKST_TYPE_INSERT = """
            with verdier as (
                select type from (values %s) v(type)
            ), ins as (
                insert into kontekst_type(type)
                select type from verdier
                on conflict do nothing
                returning id,type
            )
            select id,type from ins
            union all
            select kt.id,v.type from verdier v
            join kontekst_type kt on kt.type = v.type
        """

        @Language("PostgreSQL")
        private val KONTEKST_NAVN_INSERT = """
            with verdier as (
                select navn from (values %s) v(navn)
            ), ins as (
                insert into kontekst_navn(navn)
                select navn from verdier
                on conflict do nothing
                returning id,navn
            )
            select id,navn from ins
            union all
            select kn.id,v.navn from verdier v
            join kontekst_navn kn on kn.navn = v.navn
        """
        @Language("PostgreSQL")
        private val KONTEKST_VERDI_INSERT = """
            with verdier as (
                select verdi from (values %s) v(verdi)
            ), ins as (
                insert into kontekst_verdi(verdi) 
                select verdi from verdier
                on conflict do nothing
                returning id,verdi
            )
            select id,verdi from ins
            union all
            select kv.id,v.verdi from verdier v
            join kontekst_verdi kv on kv.verdi = v.verdi
        """
        @Language("PostgreSQL")
        private val AKTIVITET_INSERT = """
            with verdier as (
                select melding_id, personident_id, hendelse_id, level, tidsstempel, aktivitet_uuid from (values %s) v(melding_id, personident_id, hendelse_id, level, tidsstempel, aktivitet_uuid)
            ), ins as (
                insert into aktivitet(melding_id, personident_id, hendelse_id, level, tidsstempel, aktivitet_uuid)
                select melding_id, personident_id, hendelse_id, level, tidsstempel, aktivitet_uuid from verdier
                on conflict do nothing
                returning id,aktivitet_uuid
            )
            select id,aktivitet_uuid from ins
            union all
            select a.id,v.aktivitet_uuid from verdier v
            join aktivitet a on a.aktivitet_uuid = v.aktivitet_uuid
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
                connection.prepareStatement(sql).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreKontekstType(statement, index)
                        index += 1
                    }
                    retryDeadlock(statement)
                    statement.resultSet.use { rs ->
                        index = 0
                        while (rs.next()) {
                            val id = rs.getLong(1)
                            val type = rs.getString(2)
                            chunk.single { it.typeId(type, id) }
                            index += 1
                        }
                        check(index == chunk.size) { "lagret ulikt antall" }
                    }
                }
            }

            kontekstNavn.chunked(ROWS_PER_INSERT_STATEMENT).forEach { chunk ->
                val sql = String.format(KONTEKST_NAVN_INSERT, chunk.indices.joinToString { "(?)" })
                connection.prepareStatement(sql).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreKontekstNavn(statement, index)
                        index += 1
                    }
                    retryDeadlock(statement)
                    statement.resultSet.use { rs ->
                        index = 0
                        while (rs.next()) {
                            val id = rs.getLong(1)
                            val navn = rs.getString(2)
                            chunk.single { it.navnId(navn, id) }
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
                connection.prepareStatement(sql).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreKontekstVerdi(statement, index)
                        index += 1
                    }
                    retryDeadlock(statement)
                    statement.resultSet.use { rs ->
                        index = 0
                        while (rs.next()) {
                            val id = rs.getLong(1)
                            val verdi = rs.getString(2)
                            chunk.single { it.verdiId(verdi, id) }
                            index += 1
                        }
                        check(index == chunk.size) {
                            "lagret ulikt antall"
                        }
                    }
                }
            }

            var personidentId: Long = 0L
            connection.prepareStatement(String.format(PERSON_INSERT, "(?)")).use { statement ->
                statement.setString(1, personident)
                retryDeadlock(statement)
                statement.resultSet.use { rs ->
                    rs.next()
                    check(personident == rs.getString(2)) { "en annen person" }
                    personidentId = rs.getLong(1)
                }
                check(personidentId != 0L) { "har ikke personidentId" }
            }

            meldinger.chunked(ROWS_PER_INSERT_STATEMENT).forEach { chunk ->
                val sql = String.format(MELDING_INSERT, chunk.indices.joinToString { "(?)" })
                connection.prepareStatement(sql).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreMelding(statement, index)
                        index += 1
                    }
                    retryDeadlock(statement)
                    statement.resultSet.use { rs ->
                        index = 0
                        while (rs.next()) {
                            val id = rs.getLong(1)
                            val tekst = rs.getString(2)
                            chunk.single { it.meldingId(tekst, id) }
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
                connection.prepareStatement(sql).use { statement ->
                    var index = 1
                    chunk.forEach {
                        it.lagreAktivitet(statement, index, personidentId, hendelseId)
                        index += 6
                    }
                    retryDeadlock(statement)
                    logg.info("${chunk.size} aktiviteter ble lagret")
                    statement.resultSet.use { rs ->
                        index = 0
                        while (rs.next()) {
                            chunk.single { it.aktivitetId(rs.getString(2), rs.getLong(1)) }
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