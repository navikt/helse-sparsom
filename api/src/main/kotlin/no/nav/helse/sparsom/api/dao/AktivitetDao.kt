package no.nav.helse.sparsom.api.dao

import com.fasterxml.jackson.databind.node.ObjectNode
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.scroll
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.nested
import com.jillesvangurp.searchdsls.querydsl.term
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helse.sparsom.api.objectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZonedDateTime

private val logger = LoggerFactory.getLogger("no.nav.helse.sparsom.App")

internal class AktivitetDao(private val client: SearchClient) {
    fun hentAktiviteterFor(ident: String): List<AktivitetDto> {
        return runBlocking {
            logger.debug("henter aktiviteter fra opensearch")
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
            logger.debug("mapper aktiviteter fra opensearch")
            client.scroll(response).concurrentMap {
                it.mapTilAktivitetDto()
            }.toList().also {
                logger.debug("aktiviteter mappet til liste")
            }
        }
    }

    // https://stackoverflow.com/a/76510232/218423
    private inline fun <T, R> Flow<T>.concurrentMap(crossinline transform: suspend (T) -> R): Flow<R> = channelFlow {
        collect { item ->
            launch { send(transform(item)) }
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
