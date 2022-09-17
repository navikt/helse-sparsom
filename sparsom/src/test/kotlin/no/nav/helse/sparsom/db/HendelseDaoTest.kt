package no.nav.helse.sparsom.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class HendelseDaoTest: AbstractDatabaseTest() {
    private val dao = HendelseDao { dataSource }

    @Test
    fun lagre() {
        val id = dao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        assertEquals(1L, id)
    }

    @Test
    fun `lagre flere`() {
        val id1 = dao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        val id2 = dao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        assertEquals(1L, id1)
        assertEquals(2L, id2)
    }

    @Test
    fun `lagre duplikat`() {
        val hendelseId = UUID.randomUUID()
        val id = dao.lagre("12345678910", hendelseId, "{}", LocalDateTime.now())
        val duplikat = dao.lagre("12345678910", hendelseId, "{}", LocalDateTime.now())
        assertEquals(1L, id)
        assertEquals(id, duplikat)
    }
}