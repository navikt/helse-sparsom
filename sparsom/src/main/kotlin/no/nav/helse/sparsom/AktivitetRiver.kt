package no.nav.helse.sparsom

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.bulk
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.system.measureTimeMillis

internal class AktivitetRiver(
    rapidsConnection: RapidsConnection,
    private val openSearchClient: SearchClient
): River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "aktivitetslogg_ny_aktivitet")
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
    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelseId = UUID.fromString(packet["@id"].asText())
        withMDC(mapOf(
            "hendelseId" to hendelseId.toString()
        )) {
            val tidBrukt = measureTimeMillis {
                runBlocking {
                    try {
                        openSearchClient.bulk(failOnFirstError = true, refresh = Refresh.False) {
                            tilOpenSearchAktiviteter(packet)
                                .map {
                                    index(
                                        id = it.id,
                                        source = objectMapper.writeValueAsString(it).also { json ->
                                            sikkerlogg.info("skriver dokument til opensearch:\n$json")
                                        },
                                        index = opensearchIndexnavn
                                    )
                                }
                        }
                    } catch (err: Exception) {
                        sikkerlogg.error("kan ikke lagre til opensearch: {}", err.message, err)
                        logger.error("kan ikke lagre til opensearch: {}", err.message, err)
                        throw err
                    }
                }
            }
            logger.info("lagrer aktiviteter fra hendelse {}. Tid brukt: ${tidBrukt}ms", keyValue("meldingsreferanseId", hendelseId))
        }
    }

    private fun tilOpenSearchAktiviteter(packet: JsonMessage): List<OpenSearchAktivitet> {
        return packet["aktiviteter"]
            .map { aktivitet ->
                val kontekster = aktivitet.path("kontekster").map { kontekst ->
                    val konteksttype = kontekst.path("konteksttype").asText()
                    val detaljer = kontekst.path("kontekstmap")
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
                    kontekster = kontekster.map { (konteksttype, detaljer) ->
                        detaljer + mapOf("konteksttype" to konteksttype)
                    },
                    kontekstverdier = kontekster.fold(emptyMap()) { resultat, (_, detaljer) ->
                        resultat + detaljer
                    },
                    varselkode = aktivitet.path("varselkode").takeIf(JsonNode::isTextual)?.asText()
                )
            }
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        private val logger = LoggerFactory.getLogger(AktivitetRiver::class.java)
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
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
    }
}