package no.nav.helse.sparsom

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.bulk
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.medMdc
import no.nav.sykepenger.libs.logging.navngittLogger
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.system.measureTimeMillis

internal class AktivitetRiver(
    rapidsConnection: RapidsConnection,
    private val openSearchClient: SearchClient,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "aktivitetslogg_ny_aktivitet") }
                validate {
                    it.requireKey("fødselsnummer", "@id", "@opprettet")
                    it.requireArray("aktiviteter") {
                        require("id") { UUID.fromString(it.asText()) }
                        requireKey("nivå", "melding")
                        require("tidsstempel", JsonNode::asLocalDateTime)
                        requireArray("kontekster") {
                            requireKey("konteksttype", "kontekstmap")
                        }
                    }
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val hendelseId = UUID.fromString(packet["@id"].asText())
        medMdc(MdcKey.MELDING_ID to hendelseId.toString()) {
            val tidBrukt =
                measureTimeMillis {
                    runBlocking {
                        try {
                            openSearchClient.bulk(failOnFirstError = true, refresh = Refresh.False) {
                                tilOpenSearchAktiviteter(packet)
                                    .map {
                                        index(
                                            id = it.id,
                                            source =
                                                objectMapper.writeValueAsString(it).also { json ->
                                                    logger.info("Skriver dokument til opensearch", "dokument" to json)
                                                },
                                            index = opensearchIndexnavn,
                                        )
                                    }
                            }
                        } catch (err: Exception) {
                            logger.error("Lagring til opensearch feilet", err)
                            throw err
                        }
                    }
                }
            logger.info("Lagret aktiviteter fra hendelse", "tidBruktMs" to tidBrukt.toString())
        }
    }

    private fun tilOpenSearchAktiviteter(packet: JsonMessage): List<OpenSearchAktivitet> =
        packet["aktiviteter"]
            .map { aktivitet ->
                val kontekster =
                    aktivitet.path("kontekster").map { kontekst ->
                        val konteksttype = kontekst.path("konteksttype").asText()
                        val detaljer =
                            kontekst
                                .path("kontekstmap")
                                .fields()
                                .asSequence()
                                .associate { (key, value) -> key to value.asText() }
                        konteksttype to detaljer
                    }
                OpenSearchAktivitet(
                    id = aktivitet.path("id").asText(),
                    fødselsnummer = packet["fødselsnummer"].asText(),
                    nivå = aktivitet.path("nivå").asText(),
                    melding = aktivitet.path("melding").asText(),
                    tidsstempel = LocalDateTime.parse(aktivitet.path("tidsstempel").asText()).atZone(ZoneId.systemDefault()),
                    kontekster =
                        kontekster.map { (konteksttype, detaljer) ->
                            detaljer + mapOf("konteksttype" to konteksttype)
                        },
                    kontekstverdier =
                        kontekster.fold(emptyMap()) { resultat, (_, detaljer) ->
                            resultat + detaljer
                        },
                    varselkode = aktivitet.path("varselkode").takeIf(JsonNode::isTextual)?.asText(),
                )
            }

    private companion object {
        private val objectMapper =
            jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        private val logger = navngittLogger("no.nav.helse.sparsom.AktivitetRiver")
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
    val varselkode: String?,
) {
    @JsonAnyGetter
    val detaljer =
        kontekstverdier.toMutableMap().apply {
            remove("id")
            remove("fødselsnummer")
            remove("nivå")
            remove("melding")
            remove("tidsstempel")
            remove("kontekster")
        }
}
