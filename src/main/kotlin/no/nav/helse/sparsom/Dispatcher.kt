package no.nav.helse.sparsom

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDateTime
import java.time.LocalTime

internal class Dispatcher(
    private val table: String,
    private val connection: Connection
) {
    private companion object {
        private val log = LoggerFactory.getLogger(Dispatcher::class.java)
    }

    private val updateLock: PreparedStatement = connection.prepareStatement("UPDATE $table SET ferdig=CAST(? as timestamptz) WHERE id=?;")

    fun hentArbeid(): Work? {
        val nå = LocalTime.now()
        val etterArbeidstid = LocalTime.of(18, 0, 0)
        val førArbeidstid = LocalTime.of(5, 0, 0)
        if (nå in førArbeidstid..etterArbeidstid) {
            log.info("stopper arbeidet ettersom det snart er morgen, og vil ikke risikere å klogge til prod")
            return null
        }
        log.info("henter arbeid")
        var work: Work? = null

        val before = connection.autoCommit
        connection.autoCommit = false
        connection.prepareStatement("SELECT id,ident FROM $table WHERE startet IS NULL LIMIT 1 FOR UPDATE SKIP LOCKED;").use { stmt ->
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val id = rs.getInt("id")
                    work = Work(id, rs.getString("ident"), updateLock)
                    connection.prepareStatement("UPDATE $table SET startet=CAST(? as timestamptz) WHERE id=? AND startet IS NULL;").use { updateStmt ->
                        updateStmt.setString(1, LocalDateTime.now().toString())
                        updateStmt.setInt(2, id)
                        check(1 == updateStmt.executeUpdate()) { "prøvde å oppdatere en arbeidsrad som noen andre har endret på!" }
                    }
                }
            }
        }
        // må committe for å frigjøre låsen
        connection.commit()
        connection.autoCommit = before
        log.info("fant arbeid={}", work)
        return work
    }

}

internal class Work(private val id: Int, private val ident: String, private val updateLock: PreparedStatement) {

    private companion object {
        private val log = LoggerFactory.getLogger(Dispatcher::class.java)
    }

    internal fun detaljer() = listOf(ident.toLong())

    internal fun begin() {
        log.info("blokk id={} starter", id)
    }

    internal fun done() {
        log.info("blokk id={} ferdig, oppdaterer ferdigtidspunkt for arbeidet", id)
        updateLock.setString(1, LocalDateTime.now().toString())
        updateLock.setInt(2, id)
        updateLock.execute()
    }
}