package no.nav.helse.sparsom.job

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement

internal class HentAktivitetslogg(private val dispatcher: Dispatcher) {

    fun migrate(connection: Connection, spleisConnection: Connection) {
        connection.autoCommit = false
        spleisConnection.autoCommit = false
        try {
            utførMigrering(connection, spleisConnection)
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }

    private fun utførMigrering(connection: Connection, spleisConnection: Connection) {
        connection.prepareStatement(INSERT).use { insertStatement ->
            spleisConnection.prepareStatement(SELECT_PERSON).use use2@ { fetchPersonStatement ->
                var arbeid: Work? = dispatcher.hentArbeid() ?: return@use2
                while (arbeid != null) {
                    /* utfør arbeid */
                    utførArbeid(insertStatement, fetchPersonStatement, arbeid)
                    log.info("committer ferdig utført arbeid")
                    connection.commit()
                    arbeid = dispatcher.hentArbeid()
                }
            }
        }
    }

    private fun utførArbeid(insertStatement: PreparedStatement, fetchStatement: PreparedStatement, work: Work) {
        work.begin()
        migrerPersoner(insertStatement, fetchStatement, work.detaljer().first())
        work.done()
    }

    private fun migrerPersoner(insertStatement: PreparedStatement, fetchStatement: PreparedStatement, ident: Long) {
        fetchStatement.setLong(1, ident)
        fetchStatement.executeQuery().use { rs ->
            fetchStatement.clearParameters()
            while (rs.next()) {
                // v175 introduserte "id" på aktiviteter
                if (rs.getInt("skjema_versjon") >= 175) {
                    insertStatement.setLong(1, rs.getLong("fnr"))
                    insertStatement.setString(2, objectMapper.readTree(rs.getString("data")).path("aktivitetslogg").toString())
                    insertStatement.addBatch()
                }
            }
        }
        insertStatement.executeBatch()
        insertStatement.clearBatch()
    }

    private companion object {
        private val log = LoggerFactory.getLogger(HentAktivitetslogg::class.java)
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())

        // finner fnr og siste/nyeste id til alle personjsons i spleis
        @Language("PostgreSQL")
        private val SELECT_PERSON = """
            select p1.skjema_versjon, p1.fnr, p1.id, p1.data from unike_person up
            inner join person p1 on up.fnr = p1.fnr
            left join person p2 on up.fnr = p2.fnr and p2.id > p1.id
            where p2.id is null and up.fnr = ?;
        """

        @Language("PostgreSQL")
        private val INSERT = """
            INSERT INTO aktivitetslogg (fnr, data) VALUES (?, ?);
        """
    }
}