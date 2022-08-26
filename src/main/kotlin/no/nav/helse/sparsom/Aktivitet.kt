package no.nav.helse.sparsom

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.helse.sparsom.Kontekst.Companion.hash
import no.nav.helse.sparsom.db.AktivitetRepository
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

internal class Aktivitet(
    private val nivå: Nivå,
    private val melding: String,
    private val tidsstempel: LocalDateTime,
    private val kontekster: List<Kontekst>
) {
    private val hash: String = DigestUtils.sha3_256Hex("$nivå$melding$tidsstempel${kontekster.hash()}")

    internal companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        internal fun List<Aktivitet>.lagre(aktivitetRepository: AktivitetRepository, hendelseId: Long) {
            val aktivitetDtoer = map {
                AktivitetDTO(it.nivå, it.melding, it.tidsstempel, it.hash)
            }
            val kontekster = flatMap { it.kontekster.toDto(hendelseId, it.hash) }
            val tidBrukt = measureTimeMillis {
                aktivitetRepository.lagre(aktivitetDtoer, kontekster, hendelseId)
            }
            sikkerlogg.info("Tid brukt på insert av alt innhold i meldingen: $tidBrukt")
        }

        private fun List<Kontekst>.toDto(hendelseId: Long, hash: String) = flatMap { kontekst ->
            kontekst.toDto(hendelseId, hash)
        }
    }

    internal class AktivitetDTO(
        private val nivå: Nivå,
        private val melding: String,
        private val tidsstempel: LocalDateTime,
        private val hash: String
    ) {
        private fun stringify(hendelseId: Long): List<String> {
            return listOf(hendelseId.toString(), nivå.toString(), melding, tidsstempel.toString(), hash)
        }
        internal companion object {
            fun List<AktivitetDTO>.stringify(hendelseId: Long) = flatMap { it.stringify(hendelseId) }
        }
    }
}

internal class Kontekst(
    @JsonAlias("konteksttype")
    private val type: String,
    @JsonAlias("kontekstmap")
    private val detaljer: Map<String, String>,
) {
    internal fun toDto(hendelseId: Long, hash: String): List<KontekstDTO> {
        return detaljer.map { KontekstDTO(type, it.key, it.value, hash, hendelseId) }
    }
    internal companion object {
        internal fun List<Kontekst>.hash(): String {
            val kontekster = flatMap { kontekst ->
                kontekst.detaljer.map { Triple(kontekst.type, it.key, it.value) }
            }
            return kontekster.joinToString("") { "${it.first}${it.second}${it.third}" }
        }
    }

    internal data class KontekstDTO(
        private val type: String,
        private val identifikatornavn: String,
        private val identifikator: String,
        private val hash: String,
        private val hendelseId: Long
    ) {
        private fun stringify(): List<String> {
            return listOf(type, identifikatornavn, identifikator)
        }

        internal companion object {
            fun List<KontekstDTO>.filtrerHarHash(hasher: List<String>) = filter { kontekst -> kontekst.hash in hasher.map { it.trimEnd() } }
            fun Set<KontekstDTO>.stringify() = flatMap { it.stringify() }
            fun Set<KontekstDTO>.stringifyForKobling() = flatMap { listOf(it.hash) + it.stringify() }
        }
    }
}
enum class Nivå {
    INFO,
    VARSEL,
    FUNKSJONELL_FEIL,
    LOGISK_FEIL;
}