package no.nav.helse.sparsom

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.helse.sparsom.Kontekst.Companion.hash
import no.nav.helse.sparsom.db.AktivitetRepository
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import java.sql.PreparedStatement
import java.sql.Types
import java.time.LocalDateTime
import java.util.*
import kotlin.system.measureTimeMillis

internal class Aktivitet(
    private val id: UUID,
    private val nivå: Nivå,
    private val melding: String,
    private val tidsstempel: LocalDateTime,
    private val kontekster: List<Kontekst>
) {
    private var bleLagret: Boolean = false
    private var meldingId: Long = -1
    private var aktivitetId: Long = -1

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

    fun lagreAktivitet(statement: PreparedStatement, personidentId: Long, hendelseId: Long?) {
        check(meldingId != -1L) { "har ikke meldingid satt" }
        statement.setLong(1, meldingId)
        statement.setLong(2, personidentId)
        if (hendelseId != null) statement.setLong(3, hendelseId)
        else statement.setNull(3, Types.BIGINT)
        statement.setString(4, nivå.toString())
        statement.setString(5, tidsstempel.toString())
        statement.setString(6, id.toString())
        statement.addBatch()
    }

    fun bleLagret(verdi: Boolean) {
        bleLagret = verdi
    }

    fun meldingId(id: Long) {
        meldingId = id
    }

    fun kobleAktivitetOgKontekst(statement: PreparedStatement) {
        kontekster.forEach {
            it.kobleAktivitetOgKontekst(statement, aktivitetId)
        }
    }

    fun kontekstTypeId(id: Long) =
        kontekster.any { it.typeId(id) }

    fun kontekstNavnId(id: Long) =
        kontekster.any { it.navnId(id) }

    fun kontekstVerdiId(id: Long) =
        kontekster.any { it.verdiId(id) }

    fun aktivitetId(id: Long) {
        aktivitetId = id
    }

    internal companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        internal fun List<Aktivitet>.lagre(aktivitetRepository: AktivitetRepository, personident: String, hendelseId: Long?) {
            aktivitetRepository.lagre(this, personident, hendelseId)
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
    private val detaljerliste = detaljer.toList()
    private var id: Long? = null
    private var lagret: Boolean = false
    private var lagretKontekstNavn: Boolean = false
    private var lagretKontekstVerdi: Boolean = false
    private val detaljerId = mutableMapOf<Int, Pair<Long, Long?>>()

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
        if (lagret) return
        lagret = true
        statement.setString(1, type)
        statement.addBatch()
    }

    fun lagreKontekstNavn(statement: PreparedStatement) {
        if (lagretKontekstNavn) return
        lagretKontekstNavn = true
        detaljerliste.forEach { (navn, verdi) ->
            statement.setString(1, navn)
            statement.addBatch()
        }
    }
    fun lagreKontekstVerdi(statement: PreparedStatement) {
        if (lagretKontekstVerdi) return
        lagretKontekstVerdi = true
        detaljerliste.forEach { (navn, verdi) ->
            statement.setString(1, verdi)
            statement.addBatch()
        }
    }

    fun kobleAktivitetOgKontekst(statement: PreparedStatement, aktivitetId: Long) {
        statement.setLong(1, aktivitetId)
        statement.setLong(2, requireNotNull(this.id) { "har ikke kontekst id" })
        detaljerId.values.forEach { (navn, verdi) ->
            statement.setLong(3, navn)
            statement.setLong(4, checkNotNull(verdi) { "har ikke id til verdi" })
            statement.addBatch()
        }
    }

    fun typeId(id: Long): Boolean {
        if (this.id != null) return false
        this.id = id
        return true
    }

    fun navnId(id: Long): Boolean {
        detaljerliste.forEachIndexed { index, _ ->
            if (index !in detaljerId) {
                detaljerId[index] = id to null
                return true
            }
        }
        return false
    }

    fun verdiId(id: Long): Boolean {
        detaljerliste.forEachIndexed { index, _ ->
            val value = detaljerId.getValue(index)
            if (value.second == null) {
                detaljerId.replace(index, value.first to id)
                return true
            }
        }
        return false
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