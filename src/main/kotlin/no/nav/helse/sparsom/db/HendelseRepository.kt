package no.nav.helse.sparsom.db

import java.time.LocalDateTime
import java.util.*

internal interface HendelseRepository {
    fun lagre(fødselsnummer: String, hendelseId: UUID, json: String, tidsstempel: LocalDateTime): Long
}