package no.nav.helse.sparsom.api.dao

import com.fasterxml.jackson.databind.node.ObjectNode
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.scroll
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.nested
import com.jillesvangurp.searchdsls.querydsl.term
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import no.nav.helse.sparsom.api.objectMapper
import java.time.LocalDateTime
import java.time.ZonedDateTime

internal class AktivitetDao(private val client: SearchClient) {
    fun hentAktiviteterFor(ident: String): List<AktivitetDto> {
        return runBlocking {
            val response = client.search(aktivitetsloggIndexName, scroll = "2m") {
                query = term("fødselsnummer", ident)
                query = bool {
                    should(
                        term("fødselsnummer", ident),
                        nested {
                            path = "kontekster"
                            query = term("kontekster.aktørId", ident)
                        }
                    )
                }
            }
            client.scroll(response).map {
                it.mapTilAktivitetDto()
            }.toList()
        }
    }

    private fun SearchResponse.Hit.mapTilAktivitetDto() =
        objectMapper.readTree(source.toString()).let { row ->
            AktivitetDto(
                id = row.path("id").asLong(),
                tidsstempel = ZonedDateTime.parse(row.path("tidsstempel").asText()).toLocalDateTime(),
                nivå = NivåDto.valueOf(row.path("nivå").asText()),
                tekst = row.path("melding").asText(),
                kontekster = row.path("kontekster").associate { kontekst ->
                    kontekst as ObjectNode
                    val konteksttype = kontekst.remove("konteksttype").asText()
                    val detaljer = kontekst.fields().asSequence().associate { (k, v) ->
                        k to v.asText()
                    }
                    konteksttype to detaljer
                }
            )
        }

    private companion object {
        private const val aktivitetsloggIndexName = "aktivitetslogg"
    }
}

data class AktivitetDto(
    val id: Long,
    val tidsstempel: LocalDateTime,
    val nivå: NivåDto,
    val tekst: String,
    val kontekster: Map<String, Map<String, String>>
)

enum class NivåDto {
    INFO,
    BEHOV,
    VARSEL,
    FUNKSJONELL_FEIL,
    LOGISK_FEIL;
}