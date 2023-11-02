package no.nav.helse.sparsom.job

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.bulk
import com.jillesvangurp.ktsearch.bulkSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sparsom.*
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource
import kotlin.system.measureTimeMillis
import kotlin.time.measureTime

internal class ImporterOpenSearch(private val dispatcher: Dispatcher) {

    private val varselmapper = Varselkodemapper()

    fun migrate(openSearchClient: SearchClient, dataSource: DataSource) {
        utførMigrering(openSearchClient, Dao(dataSource))
    }

    private fun utførMigrering(openSearchClient: SearchClient, dao: Dao) {
        var arbeid: Work? = dispatcher.hentArbeid() ?: return
        while (arbeid != null) {
            /* utfør arbeid */
            utførArbeid(openSearchClient, dao, arbeid)
            log.info("committer ferdig utført arbeid")
            arbeid = dispatcher.hentArbeid()
        }
    }

    private fun utførArbeid(openSearchClient: SearchClient, dao: Dao, work: Work) {
        work.begin()
        try {
            migrerAktivitetslogg(openSearchClient, dao, work.detaljer().first())
            work.done()
        } catch (err: Exception) {
            log.error("fikk feil fra opensearch: {}. Markerer ikke arbeidet som ferdig, men går videre", err.message, err)
        }
    }

    private fun migrerAktivitetslogg(openSearchClient: SearchClient, dao: Dao, ident: Long) {
        val t1 = System.currentTimeMillis()
        val aktiveter = dao.hentAktiviteterFor(ident.toString().padStart(11, '0'))
            .map { aktivitet ->
                if (aktivitet.nivå in setOf("VARSEL", "FUNKSJONELL_FEIL")) varselmapper.map(aktivitet.melding)?.let { aktivitet.copy(varselkode = it.name) } ?: aktivitet
                else aktivitet
            }
        val t2 = System.currentTimeMillis()
        log.info("brukte ${t2 - t1} ms på å hente aktivietslogg fra psql")

        measureTimeMillis {
            val maxRetries = 10
            var retries = 0
            runBlocking {
                var ok = false
                do {
                    try {
                        openSearchClient.bulk(bulkSize = 500, failOnFirstError = true) {
                            aktiveter.map {
                                index(
                                    id = it.id,
                                    source = objectMapper.writeValueAsString(it),
                                    index = "aktivitetslogg"
                                )
                            }
                        }
                        ok = true
                    } catch (err: Exception) {
                        if (retries == maxRetries) throw err
                        log.info("fikk en feil fra opensearch, men forsøker igjen om 1 sekund")
                        delay(1000)
                        retries += 1
                    }
                } while (!ok)
            }
        }.also {
            log.info("brukte $it ms på å sende til opensearch")
        }
    }

    @JsonIgnoreProperties("kontekstverdier", "detaljer")
    data class OpenSearchAktivitet(
        val id: String,
        val fødselsnummer: String,
        val nivå: String,
        val melding: String,
        val tidsstempel: ZonedDateTime,
        val kontekster: List<Map<String, String>>,
        val kontekstverdier: Map<String, String>,
        val varselkode: String?
    ) {
        @JsonAnyGetter
        val detaljer = kontekstverdier.toMutableMap().apply {
            remove("id")
            remove("fødselsnummer")
            remove("nivå")
            remove("melding")
            remove("tidsstempel")
            remove("kontekster")
            remove("varselkode")
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ImporterOpenSearch::class.java)
        private val objectMapper = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())
    }

    private class Dao(private val dataSource: DataSource) {
        fun hentAktiviteterFor(ident: String) = sessionOf(dataSource).use { session ->
            session.run(queryOf(AKTIVITETER_FOR_IDENT, mapOf("ident" to ident)).map { mapRow(it, ident) }.asList)
        }

        private fun mapRow(row: Row, ident: String): OpenSearchAktivitet {
            val kontekster = row.string("kontekster")
                .split(ROW_SEPARATOR)
                .map {
                    val verdier = it.split(VALUE_SEPARATOR)
                    Triple(verdier[0], verdier[1], verdier[2])
                }
                .groupBy(Triple<String, *, *>::first)
                .map { (konteksttype, detaljer) ->
                    konteksttype to detaljer.associate { kontekstverdi ->
                        kontekstverdi.second to kontekstverdi.third
                    }
                }
            return OpenSearchAktivitet(
                id = row.string("aktivitet_uuid"),
                fødselsnummer = ident,
                tidsstempel = row.localDateTime("tidsstempel").atZone(ZoneId.systemDefault()),
                nivå = row.string("level"),
                melding = row.string("tekst"),
                kontekster = kontekster.map { (konteksttype, detaljer) ->
                    detaljer + mapOf("konteksttype" to konteksttype)
                },
                kontekstverdier = kontekster.fold(emptyMap()) { resultat, (_, detaljer) ->
                    resultat + detaljer
                },
                varselkode = null
            )
        }
        private companion object {
            private const val VALUE_SEPARATOR = ','
            private const val ROW_SEPARATOR = ';'

            @Language("PostgreSQL")
            private val AKTIVITETER_FOR_IDENT = """
            with aktiviteter as materialized (
                select ak.aktivitet_id
                from aktivitet_kontekst ak
                inner join kontekst_verdi kv on ak.kontekst_verdi_id = kv.id
                inner join kontekst_navn k on ak.kontekst_navn_id = k.id
                where kv.verdi = :ident and k.navn='aktørId'
            )
            (
                select a.id, a.level, a.aktivitet_uuid, a.tidsstempel, m.tekst, string_agg(concat_ws('$VALUE_SEPARATOR', kt.type, kn.navn, kv.verdi), '$ROW_SEPARATOR') as kontekster 
                from aktivitet a
                inner join melding m on a.melding_id = m.id
                inner join aktivitet_kontekst ak on a.id = ak.aktivitet_id
                inner join kontekst_type kt on kt.id = ak.kontekst_type_id
                inner join kontekst_navn kn on kn.id = ak.kontekst_navn_id
                inner join kontekst_verdi kv on kv.id = ak.kontekst_verdi_id
                where a.id in (SELECT aktivitet_id FROM aktiviteter)
                group by a.id, a.tidsstempel, m.tekst
            )
            union
            (
                select a.id, a.level, a.aktivitet_uuid, a.tidsstempel, m.tekst, string_agg(concat_ws('$VALUE_SEPARATOR', kt.type, kn.navn, kv.verdi), '$ROW_SEPARATOR') as kontekster 
                from aktivitet a
                inner join melding m on a.melding_id = m.id
                inner join personident p on p.id = a.personident_id
                inner join aktivitet_kontekst ak on a.id = ak.aktivitet_id
                inner join kontekst_type kt on kt.id = ak.kontekst_type_id
                inner join kontekst_navn kn on kn.id = ak.kontekst_navn_id
                inner join kontekst_verdi kv on kv.id = ak.kontekst_verdi_id
                where p.ident = :ident
                group by a.id, a.tidsstempel, m.tekst
            )
            order by tidsstempel, id; 
        """
        }
    }
}