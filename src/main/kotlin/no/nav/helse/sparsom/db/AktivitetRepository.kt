package no.nav.helse.sparsom.db

import no.nav.helse.sparsom.Nivå
import java.time.LocalDateTime

interface AktivitetRepository {
    fun lagre(
        nivå: Nivå,
        melding: String,
        tidsstempel: LocalDateTime,
        hendelseId: Long,
        kontekster: List<Triple<String, String, String>>
    )
}