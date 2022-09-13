package no.nav.helse.sparsom

import com.fasterxml.jackson.databind.JsonNode
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
    private val aktivitetDao: AktivitetDao
): River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "aktivitetslogg_ny_aktivitet")
                it.requireKey("fødselsnummer", "@id", "@opprettet")
                it.requireArray("aktiviteter") {
                    require("id") { UUID.fromString(it.asText()) }
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

            val typer = mutableMapOf<String, KontekstType>()
            val navn = mutableMapOf<String, KontekstNavn>()
            val verdier = mutableMapOf<String, KontekstVerdi>()
            val meldinger = mutableMapOf<String, Melding>()

            val aktiviteter = packet["aktiviteter"].mapNotNull { aktivitet ->
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

                tilNivå(aktivitet.path("alvorlighetsgrad").asText())?.let { nivå ->
                    val aktivitetKontekster = aktivitet.path("kontekster")
                        .map { it.intValue() }
                        .map { kontekster[it] }
                    Aktivitet(
                        id = UUID.fromString(aktivitet.path("id").asText()),
                        nivå = nivå,
                        melding = meldinger.getOrPut(aktivitet.path("melding").asText()) { Melding(aktivitet.path("melding").asText()) },
                        tidsstempel = LocalDateTime.parse(aktivitet.path("tidsstempel").asText()),
                        kontekster = aktivitetKontekster
                    )
                }
            }
            aktivitetDao.lagre(aktiviteter, meldinger.values, typer.values, navn.values, verdier.values, fødselsnummer, id)
        }
        logger.info("lagrer aktiviteter fra hendelse {}. Tid brukt: ${tidBrukt}ms", keyValue("meldingsreferanseId", hendelseId))
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(AktivitetRiver::class.java)
        private fun tilNivå(value: String) = when (value) {
            "INFO" -> Nivå.INFO
            "WARNING" -> Nivå.VARSEL
            "ERROR" -> Nivå.FUNKSJONELL_FEIL
            "SEVERE" -> Nivå.LOGISK_FEIL
            else -> null
        }
    }
}