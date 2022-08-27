package no.nav.helse.sparsom.db

import no.nav.helse.sparsom.Aktivitet
import no.nav.helse.sparsom.Kontekst

internal interface AktivitetRepository {
    fun lagre(
        aktiviteter: List<Aktivitet>,
        personident: String,
        hendelseId: Long?
    )
}