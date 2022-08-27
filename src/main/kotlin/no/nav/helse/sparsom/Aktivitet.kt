package no.nav.helse.sparsom

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.helse.sparsom.Kontekst.Companion.hash
import no.nav.helse.sparsom.db.AktivitetRepository
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import java.sql.PreparedStatement
import java.sql.Types
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

internal class Aktivitet(
    private val nivå: Nivå,
    private val melding: String,
    private val tidsstempel: LocalDateTime,
    private val kontekster: List<Kontekst>
) {
    private val hash: String = DigestUtils.sha3_256Hex("$nivå$melding$tidsstempel${kontekster.hash()}")
    private var bleLagret: Boolean = false

    fun lagreMelding(statement: PreparedStatement) {
        statement.setString(1, melding)
        statement.addBatch()
    }

    fun lagreKontekstType(statement: PreparedStatement) {
        kontekster.forEach { it.lagreKontekstType(statement) }
    }
    fun lagreKontekstNavn(statement: PreparedStatement) {
        kontekster.forEach { it.lagreKontekstNavn(statement) }
    }
    fun lagreKontekstVerdi(statement: PreparedStatement) {
        kontekster.forEach { it.lagreKontekstVerdi(statement) }
    }

    fun lagreAktivitet(statement: PreparedStatement, personident: String, hendelseId: Long?) {
        statement.setString(1, melding)
        statement.setString(2, personident)
        if (hendelseId != null) statement.setLong(3, hendelseId)
        else statement.setNull(3, Types.BIGINT)
        statement.setString(4, nivå.toString())
        statement.setString(5, tidsstempel.toString())
        statement.setString(6, hash)
        statement.addBatch()
    }

    fun bleLagret(verdi: Boolean) {
        bleLagret = verdi
    }

    fun kobleAktivitetOgKontekst(statement: PreparedStatement) {
        kontekster.forEach {
            it.kobleAktivitetOgKontekst(statement, hash)
        }
    }

    internal companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        internal fun List<Aktivitet>.lagre(aktivitetRepository: AktivitetRepository, personident: String, hendelseId: Long?) {
            val tidBrukt = measureTimeMillis {
                aktivitetRepository.lagre(this, personident, hendelseId)
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
        private val hash: String,
        private val kontekster: List<Kontekst.KontekstDTO>
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

    fun lagreKontekstType(typeStatement: PreparedStatement, navnStatement: PreparedStatement, verdiStatement: PreparedStatement) {
        typeStatement.setString(1, type)
        typeStatement.addBatch()
        detaljer.forEach { (navn, verdi) ->
            navnStatement.setString(1, navn)
            navnStatement.addBatch()
            verdiStatement.setString(1, verdi)
            verdiStatement.addBatch()
        }
    }
    fun lagreKontekstType(statement: PreparedStatement) {
        statement.setString(1, type)
        statement.addBatch()
    }
    fun lagreKontekstNavn(statement: PreparedStatement) {
        detaljer.forEach { (navn, verdi) ->
            statement.setString(1, navn)
            statement.addBatch()
        }
    }
    fun lagreKontekstVerdi(statement: PreparedStatement) {
        detaljer.forEach { (navn, verdi) ->
            statement.setString(1, verdi)
            statement.addBatch()
        }
    }

    fun kobleAktivitetOgKontekst(statement: PreparedStatement, hash: String) {
        statement.setString(1, hash)
        statement.setString(2, type)
        detaljer.forEach { (navn, verdi) ->
            statement.setString(3, navn)
            statement.setString(4, verdi)
            statement.addBatch()
        }
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