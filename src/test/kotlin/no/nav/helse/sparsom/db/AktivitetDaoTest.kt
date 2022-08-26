package no.nav.helse.sparsom.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sparsom.Aktivitet
import no.nav.helse.sparsom.Kontekst
import no.nav.helse.sparsom.Nivå.INFO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.time.LocalDateTime
import java.util.*

@TestInstance(PER_CLASS)
internal class AktivitetDaoTest: AbstractDatabaseTest() {

    private lateinit var aktivitetDao: AktivitetDao
    private lateinit var hendelseDao: HendelseDao

    @BeforeAll
    fun beforeAll() {
        aktivitetDao = AktivitetDao { dataSource }
        hendelseDao = HendelseDao { dataSource }
    }

    @Test
    fun lagre() {
        val hendelseId = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        aktivitetDao.lagre(
            listOf(Aktivitet.AktivitetDTO(INFO, "en melding", LocalDateTime.now(), "hash")),
            listOf(Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash", hendelseId)),
            hendelseId
        )
        assertAntallRader(1, 1, 1)
    }

    @Test
    fun `lagre med flere kontekster`() {
        val hendelseId = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        aktivitetDao.lagre(
            listOf(Aktivitet.AktivitetDTO(INFO, "en melding", LocalDateTime.now(), "hash")),
            listOf(
                Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash", hendelseId),
                Kontekst.KontekstDTO("Arbeidsgiver", "organisasjonsnummer", "987654321", "hash", hendelseId)
            ),
            hendelseId
        )
        assertAntallRader(1, 2, 2)
    }

    @Test
    fun `ulike hendelser med samme kontekst`() {
        val hendelseId1 = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        aktivitetDao.lagre(
            listOf(Aktivitet.AktivitetDTO(INFO, "en melding", LocalDateTime.now(), "hash")),
            listOf(Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash", hendelseId1)),
            hendelseId1
        )
        aktivitetDao.lagre(
            listOf(Aktivitet.AktivitetDTO(INFO, "en annen melding", LocalDateTime.now(), "hash2")),
            listOf(
                Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash2", hendelseId2),
                Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash2", hendelseId2)
            ),
            hendelseId2
        )
        assertAntallRader(2, 1, 2)
    }

    @Test
    fun `varsel finnes fra før av`() {
        val hendelseId1 = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        val tidsstempel = LocalDateTime.now()
        aktivitetDao.lagre(
            listOf(Aktivitet.AktivitetDTO(INFO, "melding", tidsstempel, "hash")),
            listOf(Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash", hendelseId1)),
            hendelseId1
        )
        aktivitetDao.lagre(
            listOf(
                Aktivitet.AktivitetDTO(INFO, "melding", tidsstempel, "hash"),
                Aktivitet.AktivitetDTO(INFO, "annen melding", LocalDateTime.now(), "hash2"),
            ),
            listOf(
                Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash", hendelseId2),
                Kontekst.KontekstDTO("Person", "fødselsnummer", "01987654321", "hash2", hendelseId2)
            ),
            hendelseId2
        )
        assertAntallRader(2, 2, 2)
    }

    @Test
    fun duplikathåndtering() {
        val hendelseId1 = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        val tidsstempel = LocalDateTime.now()
        aktivitetDao.lagre(
            listOf(Aktivitet.AktivitetDTO(INFO, "melding", tidsstempel, "hash")),
            listOf(Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash", hendelseId1)),
            hendelseId1
        )
        aktivitetDao.lagre(
            listOf(Aktivitet.AktivitetDTO(INFO, "melding", tidsstempel, "hash")),
            listOf(Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash", hendelseId2)),
            hendelseId2
        )
        assertAntallRader(1, 1, 1)
    }

    @Test
    fun `duplikathåndtering med flere kontekster`() {
        val hendelseId = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre("12345678910", UUID.randomUUID(), "{}", LocalDateTime.now())
        val tidsstempel = LocalDateTime.now()
        val vedtaksperiodeid = UUID.randomUUID()
        aktivitetDao.lagre(
            listOf(Aktivitet.AktivitetDTO(INFO, "en melding", tidsstempel, "hash")),
            listOf(
                Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash", hendelseId),
                Kontekst.KontekstDTO("Vedtaksperiode", "vedtaksperiodeId", "$vedtaksperiodeid", "hash", hendelseId)
            ),
            hendelseId
        )
        aktivitetDao.lagre(
            listOf(Aktivitet.AktivitetDTO(INFO, "en melding", tidsstempel, "hash")),
            listOf(
                Kontekst.KontekstDTO("Person", "fødselsnummer", "12345678910", "hash", hendelseId),
                Kontekst.KontekstDTO("Vedtaksperiode", "vedtaksperiodeId", "$vedtaksperiodeid", "hash", hendelseId)
            ),
            hendelseId2
        )
        assertAntallRader(
            forventetAntallAktiviteter = 1,
            forventetAntallKontekster = 2,
            forventetAntallKoblinger = 2
        )
    }

    private fun assertAntallRader(forventetAntallAktiviteter: Int, forventetAntallKontekster: Int, forventetAntallKoblinger: Int) {
        val faktiskAntallAktiviteter = antallAktiviteter()
        val faktiskAntallKontekster = antallKontekster()
        val faktiskAntallKoblinger = antallKoblinger()
        assertEquals(forventetAntallAktiviteter, faktiskAntallAktiviteter) { "forventet antall aktiviteter: $forventetAntallAktiviteter, faktisk: $faktiskAntallAktiviteter" }
        assertEquals(forventetAntallKontekster, faktiskAntallKontekster) { "forventet antall kontekster: $forventetAntallKontekster, faktisk: $faktiskAntallKontekster" }
        assertEquals(forventetAntallKoblinger, faktiskAntallKoblinger) { "forventet antall koblinger: $forventetAntallKoblinger, faktisk: $faktiskAntallKoblinger" }
    }

    private fun antallAktiviteter() = sessionOf((dataSource)).use {
        requireNotNull(it.run(queryOf("SELECT COUNT(1) FROM aktivitet").map { it.int(1) }.asSingle))
    }

    private fun antallKontekster() = sessionOf((dataSource)).use {
        requireNotNull(it.run(queryOf("SELECT COUNT(1) FROM kontekst").map { it.int(1) }.asSingle))
    }

    private fun antallKoblinger() = sessionOf((dataSource)).use {
        requireNotNull(it.run(queryOf("SELECT COUNT(1) FROM aktivitet_kontekst").map { it.int(1) }.asSingle))
    }
}