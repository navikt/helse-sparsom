package no.nav.helse.sparsom

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sparsom.db.HendelseRepository
import java.time.LocalDateTime
import java.util.*

internal class AktivitetRiver(
    rapidsConnection: RapidsConnection,
    private val hendelseRepository: HendelseRepository,
    private val aktivitetFactory: AktivitetFactory
): River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "aktivitetslogg_ny_aktivitet")
                it.requireKey("fødselsnummer", "@id", "@opprettet")
                it.requireArray("aktiviteter") {
                    requireAny("nivå", Nivå.values().map { it.toString() })
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
        val id = hendelseRepository.lagre(fødselsnummer, hendelseId, packet.toJson(), tidsstempel)
        val aktiviteter = packet["aktiviteter"].takeUnless(JsonNode::isMissingOrNull) ?: emptyList()
        aktivitetFactory.aktiviteter(aktiviteter, id)
    }
}