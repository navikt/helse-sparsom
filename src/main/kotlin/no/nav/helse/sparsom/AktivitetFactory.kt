package no.nav.helse.sparsom

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.sparsom.db.AktivitetRepository

internal class AktivitetFactory(private val aktivitetRepository: AktivitetRepository) {
    internal fun aktiviteter(aktiviteter: Iterable<JsonNode>, hendelseId: Long) {
        aktiviteter
            .map { objectMapper.treeToValue(it, Aktivitet::class.java) }
            .forEach { it.lagre(aktivitetRepository, hendelseId) }
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}