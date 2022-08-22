package no.nav.helse.sparsom

import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helse.sparsom.db.HendelseRepository
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AktivitetRiverTest {
    private val testRapid = TestRapid()
    private lateinit var aktivitetFactory: AktivitetFactory
    private lateinit var hendelseRepository: HendelseRepository

    @BeforeEach
    fun beforeEach() {
        testRapid.reset()
        aktivitetFactory = mockk(relaxed = true)
        hendelseRepository = mockk(relaxed = true)
        AktivitetRiver(testRapid, hendelseRepository, aktivitetFactory)
    }

    @Test
    fun river() {
        val melding = AktivitetRiverTest::class.java.classLoader.getResource("testmelding.json").readText()
        testRapid.sendTestMessage(melding)
        verify(exactly = 1) { aktivitetFactory.aktiviteter(any(), any()) }
    }

    @Test
    fun `mangler fødselsnummer`() {
        testRapid.sendTestMessage(meldingUtenFnr())
        verify(exactly = 0) { aktivitetFactory.aktiviteter(any(), any()) }
    }

    @Test
    fun `mangler aktiviteter`() {
        testRapid.sendTestMessage(meldingUtenAktiviteter())
        verify(exactly = 0) { aktivitetFactory.aktiviteter(any(), any()) }
    }

    @Test
    fun `aktivitet har dårlig timestamp`() {
        testRapid.sendTestMessage(meldingMedDårligTimestamp())
        verify(exactly = 0) { aktivitetFactory.aktiviteter(any(), any()) }
    }

    @Language("JSON")
    private fun meldingUtenFnr() = """{
      "@event_name": "aktivitetslogg_ny_aktivitet",
      "aktiviteter": [],
      "@id": "df5758d0-6efc-4a3a-a7f0-e8b6b1573c32",
      "@opprettet": "2022-08-22T13:31:19.110451756"
    }"""

    @Language("JSON")
    private fun meldingUtenAktiviteter() = """{
      "@event_name": "aktivitetslogg_ny_aktivitet",
      "fødselsnummer": "12345678910",
      "@id": "0c5129bb-a19c-4b45-be4b-0611d1bc4320",
      "@opprettet": "2022-08-22T13:31:19.110451756"
    }"""

    @Language("JSON")
    private fun meldingMedDårligTimestamp() = """{
      "@event_name": "aktivitetslogg_ny_aktivitet",
      "aktiviteter": [
        {
          "nivå": "Info",
          "melding": "Behandler historiske utbetalinger og inntekter",
          "tidsstempel": "2022-08-22 16:17:40.559",
          "kontekster": [
            {
              "konteksttype": "Person",
              "kontekstmap": {
                "fødselsnummer": "12345678910",
                "aktørId": "1234567891011"
              }
            }
          ]
        }
      ],
      "@id": "df5758d0-6efc-4a3a-a7f0-e8b6b1573c32",
      "@opprettet": "2022-08-22T13:31:19.110451756"
    }"""
}