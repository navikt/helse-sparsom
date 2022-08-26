package no.nav.helse.sparsom.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.sparsom.Aktivitet
import no.nav.helse.sparsom.Aktivitet.AktivitetDTO.Companion.stringify
import no.nav.helse.sparsom.Kontekst
import no.nav.helse.sparsom.Kontekst.KontekstDTO.Companion.filtrerHarHash
import no.nav.helse.sparsom.Kontekst.KontekstDTO.Companion.stringify
import no.nav.helse.sparsom.Kontekst.KontekstDTO.Companion.stringifyForKobling
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import javax.sql.DataSource
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

internal class AktivitetDao(private val dataSource: () -> DataSource): AktivitetRepository {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    @OptIn(ExperimentalTime::class)
    override fun lagre(aktiviteter: List<Aktivitet.AktivitetDTO>, kontekster: List<Kontekst.KontekstDTO>, hendelseId: Long) {
        sessionOf(dataSource()).use { session ->
            session.transaction { tx ->
                val (lagredeHasher, tidBruktLagreAktiviteter) = measureTimedValue {
                    tx.lagreAktiviteter(aktiviteter)
                }
                sikkerlogg.info("Tid brukt på insert av ${lagredeHasher.size} aktiviteter: ${tidBruktLagreAktiviteter.inWholeMilliseconds}")
                val konteksterSomSkalLagres = kontekster.filtrerHarHash(lagredeHasher).toSet()
                if (konteksterSomSkalLagres.isEmpty()) return@transaction
                val tidBruktLagreKontekster = measureTimeMillis {
                    tx.lagreKontekster(konteksterSomSkalLagres)
                }
                val (antallKoblingerLagret, tidBruktLagreKoblinger) = measureTimedValue {
                    tx.lagreKoblinger(konteksterSomSkalLagres)
                }
                sikkerlogg.info("Tid brukt på insert av ${konteksterSomSkalLagres.size} kontekster: $tidBruktLagreKontekster")
                sikkerlogg.info("Tid brukt på insert av $antallKoblingerLagret koblinger: $tidBruktLagreKoblinger")
            }
        }
    }

    private fun TransactionalSession.lagreAktiviteter(aktiviteter: List<Aktivitet.AktivitetDTO>): List<String> {
        @Language("PostgreSQL")
        val query = "INSERT INTO aktivitet(level, melding, tidsstempel, hash) VALUES ${aktiviteter.joinToString { "(CAST(? as LEVEL), ?, CAST(? as timestamptz), ?)" }} ON CONFLICT (hash) DO NOTHING RETURNING(hash)"
        return run(queryOf(query, *aktiviteter.stringify().toTypedArray()).map { it.string(1) }.asList)
    }

    private fun TransactionalSession.lagreKontekster(kontekster: Set<Kontekst.KontekstDTO>) {
        @Language("PostgreSQL")
        val query = "INSERT INTO kontekst (type, identifikatornavn, identifikator) VALUES ${kontekster.joinToString { "(?, ?, ?)" }} ON CONFLICT(type, identifikatornavn, identifikator) DO NOTHING"
        run(queryOf(query, *kontekster.stringify().toTypedArray()).asUpdate)
    }

    private fun TransactionalSession.lagreKoblinger(kontekster: Set<Kontekst.KontekstDTO>): Int {
        @Language("PostgreSQL")
        val query = "INSERT INTO aktivitet_kontekst (aktivitet_ref, kontekst_ref, hendelse_ref) VALUES ${kontekster.joinToString { "((SELECT id FROM aktivitet WHERE hash=?), (SELECT id FROM kontekst WHERE type=? AND identifikatornavn=? AND identifikator=?), ?)" }}"
        return run(queryOf(query, *kontekster.stringifyForKobling().toTypedArray()).asUpdate)
    }

}