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
        aktivitetDao = AktivitetDao(dataSource)
        hendelseDao = HendelseDao { dataSource }
    }

    @Test
    fun lagre() {
        val fødselsnummer = "12345678910"
        val hendelseId = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        aktivitetDao.lagre(
            aktiviteter = listOf(
                Aktivitet(UUID.randomUUID(), INFO, "en melding", LocalDateTime.now(), listOf(Kontekst("Person", mapOf("fødselsnummer" to fødselsnummer))))
            ),
            personident = fødselsnummer,
            hendelseId = hendelseId
        )
        assertAntallRader(1, 1, 1)
    }

    @Test
    fun `lagre med flere kontekster`() {
        val fødselsnummer = "12345678910"
        val hendelseId = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        aktivitetDao.lagre(
            listOf(
                Aktivitet(UUID.randomUUID(), INFO, "en melding", LocalDateTime.now(), listOf(
                    Kontekst("Person", mapOf(
                        "fødselsnummer" to fødselsnummer
                    )),
                    Kontekst("Arbeidsgiver", mapOf(
                        "organisasjonsnummer" to "987654321"
                    ))
                ))
            ),
            fødselsnummer,
            hendelseId
        )
        assertAntallRader(1, 2, 2)
    }

    @Test
    fun `ulike hendelser med samme kontekst`() {
        val fødselsnummer = "12345678910"
        val hendelseId1 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        aktivitetDao.lagre(
            listOf(
                Aktivitet(UUID.randomUUID(), INFO, "en melding", LocalDateTime.now(), listOf(
                    Kontekst("Person", mapOf(
                        "fødselsnummer" to fødselsnummer
                    ))
                ))
            ),
            fødselsnummer,
            hendelseId1
        )
        aktivitetDao.lagre(
            listOf(Aktivitet(UUID.randomUUID(), INFO, "en annen melding", LocalDateTime.now(), listOf(
                Kontekst("Person", mapOf(
                    "fødselsnummer" to fødselsnummer
                )),
                Kontekst("Person", mapOf(
                    "fødselsnummer" to fødselsnummer
                ))
            ))),
            fødselsnummer,
            hendelseId2
        )
        assertAntallRader(2, 1, 2)
    }

    @Test
    fun `varsel finnes fra før av`() {
        val fødselsnummer = "12345678910"
        val hendelseId1 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val tidsstempel = LocalDateTime.now()
        val id = UUID.randomUUID()
        val aktivitet1 = Aktivitet(id, INFO, "melding", tidsstempel, listOf(Kontekst("Person", mapOf("fødselsnummer" to fødselsnummer))))
        val aktivitet1Kopi = Aktivitet(id, INFO, "melding", tidsstempel, listOf(Kontekst("Person", mapOf("fødselsnummer" to fødselsnummer))))
        aktivitetDao.lagre(
            listOf(aktivitet1),
            fødselsnummer,
            hendelseId1
        )
        aktivitetDao.lagre(
            listOf(
                aktivitet1Kopi,
                Aktivitet(UUID.randomUUID(), INFO, "annen melding", LocalDateTime.now(), listOf(
                    Kontekst("Person", mapOf(
                        "fødselsnummer" to "01987654321"
                    )
                ))),
            ),
            fødselsnummer,
            hendelseId2
        )
        assertAntallRader(2, 2, 2)
    }

    @Test
    fun duplikathåndtering() {
        val fødselsnummer = "12345678910"
        val hendelseId1 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val tidsstempel = LocalDateTime.now()
        val id = UUID.randomUUID()
        aktivitetDao.lagre(
            listOf(Aktivitet(id, INFO, "melding", tidsstempel, listOf(
                Kontekst("Person", mapOf(
                    "fødselsnummer" to fødselsnummer
                ))
            ))),
            fødselsnummer,
            hendelseId1
        )
        aktivitetDao.lagre(
            listOf(Aktivitet(id, INFO, "melding", tidsstempel, listOf(
                Kontekst("Person", mapOf(
                    "fødselsnummer" to fødselsnummer
                ))
            ))),
            fødselsnummer,
            hendelseId2
        )
        assertAntallRader(1, 1, 1)
    }

    @Test
    fun `duplikathåndtering med flere kontekster`() {
        val fødselsnummer = "12345678910"
        val hendelseId = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val hendelseId2 = hendelseDao.lagre(fødselsnummer, UUID.randomUUID(), "{}", LocalDateTime.now())
        val tidsstempel = LocalDateTime.now()
        val vedtaksperiodeid = UUID.randomUUID()
        val id = UUID.randomUUID()
        aktivitetDao.lagre(
            listOf(Aktivitet(id, INFO, "en melding", tidsstempel, listOf(
                Kontekst("Person", mapOf(
                    "fødselsnummer" to fødselsnummer
                )),
                Kontekst("Vedtaksperiode", mapOf(
                    "vedtaksperiodeId" to "$vedtaksperiodeid"
                ))
            ))),
            fødselsnummer,
            hendelseId
        )
        aktivitetDao.lagre(
            listOf(Aktivitet(id, INFO, "en melding", tidsstempel, listOf(
                Kontekst("Person", mapOf(
                    "fødselsnummer" to fødselsnummer
                )),
                Kontekst("Vedtaksperiode", mapOf(
                    "vedtaksperiodeId" to "$vedtaksperiodeid"
                ))
            ))),
            fødselsnummer,
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
        requireNotNull(it.run(queryOf("SELECT COUNT(1) FROM kontekst_verdi").map { it.int(1) }.asSingle))
    }

    private fun antallKoblinger() = sessionOf((dataSource)).use {
        requireNotNull(it.run(queryOf("SELECT COUNT(1) FROM aktivitet_kontekst").map { it.int(1) }.asSingle))
    }
}