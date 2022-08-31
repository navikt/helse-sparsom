package no.nav.helse.sparsom

import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.sql.Connection

internal class FikseForeignKey {

    fun migrate(connection: Connection) = connection.use {
        it.autoCommit = false
        utførMigrering(it)
    }

    private fun utførMigrering(connection: Connection) {
        var arbeid = hentArbeid(connection)
        while (arbeid != null) {
            /* utfør arbeid */
            val (id, startOffset, endOffset) = arbeid
            connection.prepareStatement(SQL).use { stmt ->
                stmt.setInt(1, startOffset)
                stmt.setInt(2, endOffset)
                stmt.execute()
            }
            log.info("blok {} ferdig, oppdaterer ferdigtidspunkt for arbeidet", arbeid)
            connection.prepareStatement("UPDATE arbeidstabell SET ferdig=now() WHERE id=?;").use { stmt ->
                stmt.setInt(1, id)
                stmt.execute()
            }
            connection.commit()
            arbeid = hentArbeid(connection)
        }
    }

    private fun hentArbeid(connection: Connection): Triple<Int, Int, Int>? {
        log.info("henter arbeid")
        var offset: Triple<Int, Int, Int>? = null
        connection.createStatement().use { it.execute("LOCK arbeidstabell IN ACCESS EXCLUSIVE;") }
        connection.prepareStatement("SELECT * FROM arbeidstabell WHERE startet IS NULL LIMIT 1;").use { stmt ->
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val id = rs.getInt("id")
                    offset = Triple(id, rs.getInt("start_offset"), rs.getInt("end_offset"))
                    connection.prepareStatement("UPDATE arbeidstabell SET startet=now() WHERE id=?").use { updateStmt ->
                        updateStmt.setInt(1, id)
                        updateStmt.execute()
                    }
                }
            }
        }
        connection.commit()
        log.info("fant arbeid={}", offset)
        return offset
    }

    private companion object {
        private val log = LoggerFactory.getLogger(FikseForeignKey::class.java)

        @Language("PostgreSQL")
        private val SQL = """
update aktivitet_denormalisert as a
set melding_id = m.id
from melding as m
where (a.id BETWEEN ? AND ?) AND a.melding_id IS NULL AND m.tekst=a.melding;
"""
    }
}