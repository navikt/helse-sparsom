package no.nav.helse.sparsom.db

import no.nav.helse.sparsom.*
import no.nav.helse.sparsom.Aktivitet
import no.nav.helse.sparsom.Kontekst
import no.nav.helse.sparsom.KontekstNavn
import no.nav.helse.sparsom.KontekstVerdi
import no.nav.helse.sparsom.Melding

interface AktivitetRepository {
    fun lagre(
        aktiviteter: List<Aktivitet>,
        meldinger: Collection<Melding>,
        konteksttyper: Collection<KontekstType>,
        kontekstNavn: Collection<KontekstNavn>,
        kontekstVerdi: Collection<KontekstVerdi>,
        personident: String,
        hendelseId: Long?
    )
}