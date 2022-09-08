package no.nav.helse.sparsom

import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDateTime
import kotlin.math.ceil

internal class ImporterMeldinger {

    fun migrate(connection: Connection) = connection.use {
        it.autoCommit = false
        try {
            utførMigrering(it)
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }

    private fun utførMigrering(connection: Connection) {
        connection.prepareStatement(SQL).use { migration ->
            connection.prepareStatement("UPDATE arbeidstabell SET ferdig=CAST(? as timestamptz) WHERE id=?;").use use2@{ updateLock ->
                var arbeid: Triple<Int, Int, Int>? = hentArbeid(connection) ?: return@use2
                while (arbeid != null) {
                    /* utfør arbeid */
                    val (id, startOffset, endOffset) = arbeid
                    utførArbeid(migration, updateLock, id, startOffset, endOffset)
                    log.info("committer ferdig utført arbeid")
                    connection.commit()
                    arbeid = hentArbeid(connection)
                }
            }
        }
    }

    private fun utførArbeid(migration: PreparedStatement, updateLock: PreparedStatement, id: Int, startOffset: Int, endOffset: Int) {

        log.info("blokk id={}, startOffset={}, endOffset={} starter", id, startOffset, endOffset)
        migration.setInt(1, startOffset)
        migration.setInt(2, endOffset)
        migration.execute()

        /*
        val batches = ceil((endOffset - startOffset) / BATCH_SIZE.toDouble()).toInt()
        log.info("bryter blokk id={}, startOffset={}, endOffset={} ned i {} batches", id, startOffset, endOffset, batches)
        var start = startOffset
        0.until(batches).forEach { batchIndex ->
            val end = (start + BATCH_SIZE - 1).coerceAtMost(endOffset)
            migration.setInt(1, start)
            migration.setInt(2, end)
            migration.addBatch()
            log.info("batch #{} start={}, end={}", batchIndex + 1, start, end)
            start = end + 1
        }
        log.info("utfører batch")
        migration.executeLargeBatch()
        migration.clearBatch()*/
        log.info("blokk id={}, startOffset={}, endOffset={} ferdig, oppdaterer ferdigtidspunkt for arbeidet", id, startOffset, endOffset)
        updateLock.setString(1, LocalDateTime.now().toString())
        updateLock.setInt(2, id)
        updateLock.execute()
    }

    private fun hentArbeid(connection: Connection): Triple<Int, Int, Int>? {
        log.info("henter arbeid")
        var offset: Triple<Int, Int, Int>? = null

        connection.prepareStatement("SELECT * FROM arbeidstabell WHERE startet IS NULL LIMIT 1 FOR UPDATE SKIP LOCKED;").use { stmt ->
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val id = rs.getInt("id")
                    offset = Triple(id, rs.getInt("start_offset"), rs.getInt("end_offset"))
                    connection.prepareStatement("UPDATE arbeidstabell SET startet=CAST(? as timestamptz) WHERE id=? AND startet IS NULL;").use { updateStmt ->
                        updateStmt.setString(1, LocalDateTime.now().toString())
                        updateStmt.setInt(2, id)
                        check(1 == updateStmt.executeUpdate()) { "prøvde å oppdatere en arbeidsrad som noen andre har endret på!" }
                    }
                }
            }
        }
        connection.commit()
        log.info("fant arbeid={}", offset)
        return offset
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ImporterMeldinger::class.java)

        private const val BATCH_SIZE = 50_000

        @Language("PostgreSQL")
        private val SQL = """
insert into aktivitet_kontekst(aktivitet_id, kontekst_type_id, kontekst_navn_id, kontekst_verdi_id)
select a.id, kt.id, kn.id, kv.id from aktivitet a
inner join aktivitet_kontekst_denormalisert akd on a.denormalisert_id = akd.aktivitet_id
inner join kontekst_type kt on akd.kontekst_type = kt.type
inner join kontekst_navn kn on akd.kontekst_navn=kn.navn
inner join kontekst_verdi kv on akd.kontekst_verdi=kv.verdi
where a.denormalisert_id is not null and (a.denormalisert_id between ? and ?)
on conflict do nothing
;
"""
    }
}