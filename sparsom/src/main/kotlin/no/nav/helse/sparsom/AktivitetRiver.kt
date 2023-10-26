package no.nav.helse.sparsom

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.bulk
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sparsom.db.AktivitetDao
import no.nav.helse.sparsom.db.HendelseRepository
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import kotlin.system.measureTimeMillis

internal class AktivitetRiver(
    rapidsConnection: RapidsConnection,
    private val hendelseRepository: HendelseRepository,
    private val aktivitetDao: AktivitetDao,
    private val openSearchClient: SearchClient? = null
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
        val fødselsnummer = packet["fødselsnummer"].asText()
        val hendelseId = UUID.fromString(packet["@id"].asText())
        val tidsstempel = LocalDateTime.parse(packet["@opprettet"].asText())
        val tidBrukt = measureTimeMillis {
            val id = hendelseRepository.lagre(fødselsnummer, hendelseId, packet.toJson(), tidsstempel)

            val typer = mutableMapOf<String, KontekstType>()
            val navn = mutableMapOf<String, KontekstNavn>()
            val verdier = mutableMapOf<String, KontekstVerdi>()
            val meldinger = mutableMapOf<String, Melding>()

            val aktiviteter = packet["aktiviteter"]
                .filter { aktivitet ->
                    aktivitet.path("nivå").asText() in Nivå.values().map(Enum<*>::name)
                }
                .mapNotNull { aktivitet ->
                    val kontekster = aktivitet.path("kontekster").map {
                        val kontekstverdier = mutableMapOf<KontekstNavn, KontekstVerdi>()

                        it.path("kontekstmap").fields().forEach { (kontekstNavn, kontekstVerdi) ->
                            val kn = navn.getOrPut(kontekstNavn) { KontekstNavn(kontekstNavn) }
                            val kv = verdier.getOrPut(kontekstVerdi.asText()) { KontekstVerdi(kontekstVerdi.asText()) }
                            kontekstverdier[kn] = kv
                        }
                        val type = it.path("konteksttype").asText()
                        Kontekst(typer.getOrPut(type) { KontekstType(type) }, kontekstverdier)
                    }

                    Aktivitet(
                        id = UUID.fromString(aktivitet.path("id").asText()),
                        nivå = Nivå.valueOf(aktivitet.path("nivå").asText()),
                        melding = meldinger.getOrPut(aktivitet.path("melding").asText()) { Melding(aktivitet.path("melding").asText()) },
                        tidsstempel = LocalDateTime.parse(aktivitet.path("tidsstempel").asText()),
                        kontekster = kontekster
                    )
                }
            aktivitetDao.lagre(aktiviteter, meldinger.values, typer.values, navn.values, verdier.values, fødselsnummer, id)
            runBlocking {
                openSearchClient?.bulk {
                    packet["aktiviteter"]
                        .map { aktivitet ->
                            OpenSearchAktivitet(
                                id = aktivitet.path("id").asText(),
                                fødselsnummer = aktivitet.path("fødselsnummer").asText(),
                                nivå = aktivitet.path("nivå").asText(),
                                melding = aktivitet.path("melding").asText(),
                                tidsstempel = LocalDateTime.parse(aktivitet.path("tidsstempel").asText()),
                                kontekster = aktivitet.path("kontekster").map { kontekst ->
                                    val konteksttype = kontekst.path("konteksttype").asText()
                                    val detaljer = kontekst.path("kontekstmap")
                                        .fields()
                                        .asSequence()
                                        .associate { (key, value) -> key to value.asText() }
                                    detaljer + mapOf("konteksttype" to konteksttype)
                                }
                            )
                        }
                        .map {
                            index(
                                id = it.id,
                                source = objectMapper.writeValueAsString(it),
                                index = opensearchIndexnavn
                            )
                        }
                    }
                }
        }
        logger.info("lagrer aktiviteter fra hendelse {}. Tid brukt: ${tidBrukt}ms", keyValue("meldingsreferanseId", hendelseId))
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
        private val logger = LoggerFactory.getLogger(AktivitetRiver::class.java)
    }
}

data class OpenSearchAktivitet(
    val id: String,
    val fødselsnummer: String,
    val nivå: String,
    val melding: String,
    val tidsstempel: LocalDateTime,
    val kontekster: List<Map<String, String>>
)