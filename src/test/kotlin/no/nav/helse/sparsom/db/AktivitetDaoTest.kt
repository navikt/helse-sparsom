package no.nav.helse.sparsom.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sparsom.*
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
        aktivitetDao = AktivitetDao(dataSource)
        hendelseDao = HendelseDao { dataSource }
    }

    @Test
    fun lagre() {
        val fødselsnummer = "12345678910"
        val hendelseId = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val melding = Melding("en melding")
        val kontekstType = KontekstType("Person")
        val kontekstNavn = KontekstNavn("fødselsnummer")
        val kontekstVerdi = KontekstVerdi(fødselsnummer)
        val aktivitetKontekst = Kontekst(kontekstType, mapOf(kontekstNavn to kontekstVerdi))
        val aktivitet = Aktivitet(
            UUID.randomUUID(), INFO, melding, LocalDateTime.now(), listOf(aktivitetKontekst)
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet),
            meldinger = listOf(melding),
            konteksttyper = listOf(kontekstType),
            kontekstNavn = listOf(kontekstNavn),
            kontekstVerdi = listOf(kontekstVerdi),
            personident = fødselsnummer,
            hendelseId = hendelseId
        )
        assertAntallRader(1, 1, 1)
    }


    @Test
    fun `lagre med flere kontekster`() {
        val fødselsnummer = "12345678910"
        val hendelseId = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val melding = Melding("en melding")
        val person = KontekstType("Person")
        val arbeidsgiver = KontekstType("Arbeidsgiver")
        val fnr = KontekstNavn("fødselsnummer")
        val orgnr = KontekstNavn("organisasjonsnummer")
        val fnrverdi = KontekstVerdi(fødselsnummer)
        val orgnrverdi = KontekstVerdi("987654321")
        val aktivitetKontekst1 = Kontekst(person, mapOf(fnr to fnrverdi))
        val aktivitetKontekst2 = Kontekst(arbeidsgiver, mapOf(orgnr to orgnrverdi))
        val aktivitet = Aktivitet(
            UUID.randomUUID(), INFO, melding, LocalDateTime.now(), listOf(aktivitetKontekst1, aktivitetKontekst2)
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet),
            meldinger = listOf(melding),
            konteksttyper = listOf(person, arbeidsgiver),
            kontekstNavn = listOf(fnr, orgnr),
            kontekstVerdi = listOf(fnrverdi, orgnrverdi),
            personident = fødselsnummer,
            hendelseId = hendelseId
        )
        assertAntallRader(1, 2, 2)
    }


    @Test
    fun `ulike hendelser med samme kontekst`() {
        val fødselsnummer = "12345678910"
        val hendelseId1 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())

        val melding = Melding("en melding")
        val person = KontekstType("Person")
        val fnr = KontekstNavn("fødselsnummer")
        val fnrverdi = KontekstVerdi(fødselsnummer)
        val aktivitetKontekst1 = Kontekst(person, mapOf(fnr to fnrverdi))
        val aktivitet1 = Aktivitet(
            UUID.randomUUID(), INFO, melding, LocalDateTime.now(), listOf(aktivitetKontekst1)
        )

        val melding2 = Melding("en melding")
        val person2 = KontekstType("Person")
        val fnr2 = KontekstNavn("fødselsnummer")
        val fnrverdi2 = KontekstVerdi(fødselsnummer)
        val aktivitetKontekst2 = Kontekst(person2, mapOf(fnr2 to fnrverdi2))
        val aktivitetKontekst3 = Kontekst(person2, mapOf(fnr2 to fnrverdi2))
        val aktivitet2 = Aktivitet(
            UUID.randomUUID(), INFO, melding2, LocalDateTime.now(), listOf(aktivitetKontekst2, aktivitetKontekst3)
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet1),
            meldinger = listOf(melding),
            konteksttyper = listOf(person),
            kontekstNavn = listOf(fnr),
            kontekstVerdi = listOf(fnrverdi),
            personident = fødselsnummer,
            hendelseId = hendelseId1
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet2),
            meldinger = listOf(melding2),
            konteksttyper = listOf(person2),
            kontekstNavn = listOf(fnr2),
            kontekstVerdi = listOf(fnrverdi2),
            personident = fødselsnummer,
            hendelseId = hendelseId2
        )
        assertAntallRader(2, 1, 2)
    }


    @Test
    fun `varsel finnes fra før av`() {
        val fødselsnummer = "12345678910"
        val hendelseId1 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())

        val melding = Melding("en melding")
        val person = KontekstType("Person")
        val fnr = KontekstNavn("fødselsnummer")
        val fnrverdi = KontekstVerdi(fødselsnummer)
        val aktivitetKontekst1 = Kontekst(person, mapOf(fnr to fnrverdi))
        val aktivitet1 = Aktivitet(
            UUID.randomUUID(), INFO, melding, LocalDateTime.now(), listOf(aktivitetKontekst1)
        )

        val melding2 = Melding("en melding")
        val person2 = KontekstType("Person")
        val fnr2 = KontekstNavn("fødselsnummer")
        val fnrverdi2 = KontekstVerdi("annet fnr")
        val aktivitetKontekst2 = Kontekst(person2, mapOf(fnr2 to fnrverdi2))
        val aktivitet2 = Aktivitet(
            UUID.randomUUID(), INFO, melding2, LocalDateTime.now(), listOf(aktivitetKontekst2)
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet1),
            meldinger = listOf(melding),
            konteksttyper = listOf(person),
            kontekstNavn = listOf(fnr),
            kontekstVerdi = listOf(fnrverdi),
            personident = fødselsnummer,
            hendelseId = hendelseId1
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet2),
            meldinger = listOf(melding2),
            konteksttyper = listOf(person2),
            kontekstNavn = listOf(fnr2),
            kontekstVerdi = listOf(fnrverdi2),
            personident = fødselsnummer,
            hendelseId = hendelseId2
        )
        assertAntallRader(2, 2, 2)
    }


    @Test
    fun duplikathåndtering() {
        val fødselsnummer = "12345678910"
        val hendelseId1 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())

        val id = UUID.randomUUID()

        val melding = Melding("en melding")
        val person = KontekstType("Person")
        val fnr = KontekstNavn("fødselsnummer")
        val fnrverdi = KontekstVerdi(fødselsnummer)
        val aktivitetKontekst1 = Kontekst(person, mapOf(fnr to fnrverdi))
        val aktivitet1 = Aktivitet(
            id, INFO, melding, LocalDateTime.now(), listOf(aktivitetKontekst1)
        )

        val melding2 = Melding("en melding")
        val person2 = KontekstType("Person")
        val fnr2 = KontekstNavn("fødselsnummer")
        val fnrverdi2 = KontekstVerdi(fødselsnummer)
        val aktivitetKontekst2 = Kontekst(person2, mapOf(fnr2 to fnrverdi2))
        val aktivitet2 = Aktivitet(
            id, INFO, melding2, LocalDateTime.now(), listOf(aktivitetKontekst2)
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet1),
            meldinger = listOf(melding),
            konteksttyper = listOf(person),
            kontekstNavn = listOf(fnr),
            kontekstVerdi = listOf(fnrverdi),
            personident = fødselsnummer,
            hendelseId = hendelseId1
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet2),
            meldinger = listOf(melding2),
            konteksttyper = listOf(person2),
            kontekstNavn = listOf(fnr2),
            kontekstVerdi = listOf(fnrverdi2),
            personident = fødselsnummer,
            hendelseId = hendelseId2
        )
        assertAntallRader(1, 1, 1)
    }

    @Test
    fun `duplikathåndtering med flere kontekster`() {
        val fødselsnummer = "12345678910"
        val hendelseId1 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())

        val id = UUID.randomUUID()

        val melding = Melding("en melding")
        val person = KontekstType("Person")
        val fnr = KontekstNavn("fødselsnummer")
        val fnrverdi = KontekstVerdi(fødselsnummer)
        val aktivitetKontekst1 = Kontekst(person, mapOf(fnr to fnrverdi))
        val aktivitet1 = Aktivitet(
            id, INFO, melding, LocalDateTime.now(), listOf(aktivitetKontekst1)
        )

        val melding2 = Melding("en melding")
        val person2 = KontekstType("Person")
        val fnr2 = KontekstNavn("fødselsnummer")
        val fnrverdi2 = KontekstVerdi("annet fnr")
        val aktivitetKontekst2 = Kontekst(person2, mapOf(fnr2 to fnrverdi2))
        val aktivitet2 = Aktivitet(
            id, INFO, melding2, LocalDateTime.now(), listOf(aktivitetKontekst2)
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet1),
            meldinger = listOf(melding),
            konteksttyper = listOf(person),
            kontekstNavn = listOf(fnr),
            kontekstVerdi = listOf(fnrverdi),
            personident = fødselsnummer,
            hendelseId = hendelseId1
        )
        aktivitetDao.lagre(
            aktiviteter = listOf(aktivitet2),
            meldinger = listOf(melding2),
            konteksttyper = listOf(person2),
            kontekstNavn = listOf(fnr2),
            kontekstVerdi = listOf(fnrverdi2),
            personident = fødselsnummer,
            hendelseId = hendelseId2
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
        requireNotNull(it.run(queryOf("SELECT COUNT(1) FROM kontekst_verdi").map { it.int(1) }.asSingle))
    }

    private fun antallKoblinger() = sessionOf((dataSource)).use {
        requireNotNull(it.run(queryOf("SELECT COUNT(1) FROM aktivitet_kontekst").map { it.int(1) }.asSingle))
    }
}