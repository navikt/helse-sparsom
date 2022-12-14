package no.nav.helse.sparsom

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.helse.sparsom.db.AktivitetRepository
import org.slf4j.LoggerFactory
import java.sql.PreparedStatement
import java.sql.Types
import java.time.LocalDateTime
import java.util.*

class Aktivitet(
    private val id: UUID,
    private val nivå: Nivå,
    private val melding: Melding,
    private val tidsstempel: LocalDateTime,
    private val kontekster: List<Kontekst>
) {
    private var aktivitetId: Long = -1


    fun lagreAktivitet(statement: PreparedStatement, index: Int, personidentId: Long, hendelseId: Long?) {
        statement.setLong(index + 0, requireNotNull(melding.id) { "har ikke meldingId" })
        statement.setLong(index + 1, personidentId)
        if (hendelseId != null) statement.setLong(index + 2, hendelseId)
        else statement.setNull(index + 2, Types.BIGINT)
        statement.setString(index + 3, nivå.toString())
        statement.setString(index + 4, tidsstempel.toString())
        statement.setString(index + 5, id.toString())
    }


    fun kobleAktivitetOgKontekst() = kontekster.flatMap {
        it.kobleAktivitetOgKontekst(aktivitetId)
    }

    fun aktivitetId(uuid: String, id: Long): Boolean {
        if (this.id.toString() != uuid) return false
        if (this.aktivitetId != -1L) return false
        aktivitetId = id
        return true
    }

    companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        fun List<Aktivitet>.lagre(aktivitetRepository: AktivitetRepository, personident: String, hendelseId: Long?) {
            aktivitetRepository.lagre(this, emptyList(), emptyList(),  emptyList(), emptyList(), personident, hendelseId)
        }
    }
}

class Melding(private val melding: String) {
    internal var id: Long? = null
        private set

    fun lagreMelding(statement: PreparedStatement, index: Int) {
        statement.setString(index, melding)
    }

    fun meldingId(tekst: String, id: Long): Boolean {
        if (this.melding != tekst) return false
        if (this.id != null) return false
        this.id = id
        return true
    }

    override fun equals(other: Any?) = other is Melding && other.melding == this.melding
    override fun hashCode() = melding.hashCode()
}

class KontekstType(private val type: String) {
    internal var id: Long? = null
        private set

    fun lagreKontekstType(statement: PreparedStatement, index: Int) {
        statement.setString(index, type)
    }

    fun typeId(type: String, id: Long): Boolean {
        if (type != this.type) return false
        if (this.id != null) return false
        this.id = id
        return true
    }

    override fun equals(other: Any?) = other is KontekstType && other.type == this.type
    override fun hashCode() = type.hashCode()
}
class KontekstNavn(private val navn: String) {
    internal var id: Long? = null
        private set

    fun lagreKontekstNavn(statement: PreparedStatement, index: Int) {
        statement.setString(index, navn)
    }

    fun navnId(navn: String, id: Long): Boolean {
        if (navn != this.navn) return false
        if (this.id != null) return false
        this.id = id
        return true
    }

    override fun equals(other: Any?) = other is KontekstNavn && other.navn == this.navn
    override fun hashCode() = navn.hashCode()
}

class KontekstVerdi(private val verdi: String) {
    internal var id: Long? = null
        private set

    fun lagreKontekstVerdi(statement: PreparedStatement, index: Int) {
        statement.setString(index, verdi)
    }

    fun verdiId(verdi: String, id: Long): Boolean {
        if (verdi != this.verdi) return false
        if (this.id != null) return false
        this.id = id
        return true
    }

    override fun equals(other: Any?) = other is KontekstVerdi && other.verdi == this.verdi
    override fun hashCode() = verdi.hashCode()
}

class Kontekst(
    @JsonAlias("konteksttype")
    private val type: KontekstType,
    @JsonAlias("kontekstmap")
    private val detaljer: Map<KontekstNavn, KontekstVerdi>,
) {

    fun kobleAktivitetOgKontekst(aktivitetId: Long) =
        detaljer.map { (navn, verdi) ->
            arrayOf(aktivitetId, requireNotNull(type.id) { "mangler typeId" }, requireNotNull(navn.id) { "har ikke navnId" }, requireNotNull(verdi.id) { "har ikke verdiId" })
        }
}

enum class Nivå {
    INFO,
    BEHOV,
    VARSEL,
    FUNKSJONELL_FEIL,
    LOGISK_FEIL;
}