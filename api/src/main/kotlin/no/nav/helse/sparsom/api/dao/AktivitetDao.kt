package no.nav.helse.sparsom.api.dao

import kotliquery.Row
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

    fun hentAktiviteterFor(kontekstNavn: String, kontekstVerdi: String) = sessionOf(dataSource).use { session ->
        session.run(queryOf(AKTIVITETER_FOR_KONTEKST, kontekstVerdi, kontekstNavn).map(::mapRow).asList)
    }

    fun hentAktiviteterFor(ident: String) = sessionOf(dataSource).use { session ->
        session.run(queryOf(AKTIVITETER_FOR_IDENT, ident).map(::mapRow).asList)
    }

    private fun mapRow(row: Row) =
        mapOf(
            "tidsstempel" to row.string("tidsstempel"),
            "tekst" to row.string("tekst"),
            "kontekster" to row.string("kontekster")
                .split(ROW_SEPARATOR)
                .map {
                    val verdier = it.split(VALUE_SEPARATOR)
                    Triple(verdier[0], verdier[1], verdier[2])
                }
                .groupBy(Triple<String, *, *>::first)
                .mapValues { verdier ->
                    verdier.value.associate { kontekstverdi ->
                        kontekstverdi.second to kontekstverdi.third
                    }
                }
        )

    private companion object {
        private const val VALUE_SEPARATOR = ','
        private const val ROW_SEPARATOR = ';'

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

        @Language("PostgreSQL")
        private val AKTIVITETER_FOR_KONTEKST = """
            with aktiviteter as materialized (
                select ak.aktivitet_id
                from aktivitet_kontekst ak
                inner join kontekst_verdi kv on ak.kontekst_verdi_id = kv.id
                inner join kontekst_navn k on ak.kontekst_navn_id = k.id
                where kv.verdi = ? and k.navn=?
            )
            select a.id, a.level, a.tidsstempel, m.tekst, string_agg(concat_ws('$VALUE_SEPARATOR', kt.type, kn.navn, kv.verdi), '$ROW_SEPARATOR') as kontekster 
            from aktivitet a
            inner join personident p on p.id = a.personident_id
            inner join melding m on a.melding_id = m.id
            inner join aktivitet_kontekst ak on a.id = ak.aktivitet_id
            inner join kontekst_type kt on ak.kontekst_type_id = kt.id
            inner join kontekst_navn kn on kn.id = ak.kontekst_navn_id
            inner join kontekst_verdi kv on kv.id = ak.kontekst_verdi_id
            where a.id in (SELECT aktivitet_id FROM aktiviteter)
            group by a.id, a.tidsstempel, m.tekst
            order by tidsstempel;
        """

        @Language("PostgreSQL")
        private val AKTIVITETER_FOR_IDENT = """ 
            select a.id, a.level, a.tidsstempel, m.tekst, string_agg(concat_ws('$VALUE_SEPARATOR', kt.type, kn.navn, kv.verdi), '$ROW_SEPARATOR') as kontekster 
            from aktivitet a
            inner join personident p on p.id = a.personident_id
            inner join melding m on a.melding_id = m.id
            inner join aktivitet_kontekst ak on a.id = ak.aktivitet_id
            inner join kontekst_type kt on ak.kontekst_type_id = kt.id
            inner join kontekst_navn kn on kn.id = ak.kontekst_navn_id
            inner join kontekst_verdi kv on kv.id = ak.kontekst_verdi_id
            where p.ident=?
            group by a.id, a.tidsstempel, m.tekst
            order by tidsstempel;
        """
    }
}
