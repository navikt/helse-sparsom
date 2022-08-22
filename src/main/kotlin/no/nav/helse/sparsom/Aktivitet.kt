package no.nav.helse.sparsom

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.helse.sparsom.Kontekst.Companion.lagre
import no.nav.helse.sparsom.db.AktivitetRepository
import java.time.LocalDateTime

class Aktivitet(
    private val nivå: Nivå,
    private val melding: String,
    private val tidsstempel: LocalDateTime,
    private val kontekster: List<Kontekst>
) {
    internal fun lagre(aktivitetRepository: AktivitetRepository, hendelseId: Long) {
        kontekster.lagre(nivå, melding, tidsstempel, hendelseId, aktivitetRepository)
    }
}

class Kontekst(
    @JsonAlias("konteksttype")
    private val type: String,
    @JsonAlias("kontekstmap")
    private val detaljer: Map<String, String>,
) {
    internal companion object {
        fun List<Kontekst>.lagre(
            nivå: Nivå,
            melding: String,
            tidsstempel: LocalDateTime,
            hendelseId: Long,
            aktivitetRepository: AktivitetRepository
        ) {
            val kontekster = flatMap { kontekst ->
                kontekst.detaljer.map { Triple(kontekst.type, it.key, it.value) }
            }
            aktivitetRepository.lagre(nivå, melding, tidsstempel, hendelseId, kontekster)
        }
    }
}

enum class Nivå(private val dbName: String) {
    Info("INFO");

    override fun toString() = dbName
}