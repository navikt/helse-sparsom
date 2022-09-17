package no.nav.helse.sparsom.api.dao

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

internal class AktivitetDao(private val dataSource: DataSource) {
    fun hentVarsler() = sessionOf(dataSource).use { session ->
        session.run(queryOf(VARSLER).map { row ->
            mapOf(
                "antall" to row.int("antall"),
                "tekst" to row.string("tekst")
            )
        }.asList)
    }

    private companion object {
        @Language("PostgreSQL")
        private val VARSLER = """ 
             with
                filtrert as (
                    select count(1) as antall, melding_id
                    from aktivitet
                    where level='VARSEL'::level
                    group by melding_id
                )
            select antall, tekst from filtrert
            join melding on id = melding_id
            order by antall desc
;
        """
    }
}
