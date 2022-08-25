package no.nav.helse.sparsom

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sparsom.db.HendelseRepository
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import kotlin.system.measureTimeMillis

internal class AktivitetRiver(
    rapidsConnection: RapidsConnection,
    private val hendelseRepository: HendelseRepository,
    private val aktivitetFactory: AktivitetFactory
): River.PacketListener {
    private companion object {
        private val logger = LoggerFactory.getLogger(AktivitetRiver::class.java)
    }
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "aktivitetslogg_ny_aktivitet")
                it.requireKey("fødselsnummer", "@id", "@opprettet")
                it.requireArray("aktiviteter") {
                    requireAny("nivå", Nivå.values().map { nivå -> nivå.toString() })
                    requireKey("melding")
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
            val aktiviteter = packet["aktiviteter"].takeUnless(JsonNode::isMissingOrNull) ?: emptyList()
            aktivitetFactory.aktiviteter(aktiviteter, id)
        }
        logger.info("lagrer aktiviteter fra hendelse {}. Tid brukt: ${tidBrukt}ms", keyValue("meldingsreferanseId", hendelseId))
    }
}