# Sparsom
![Bygg og deploy app](https://github.com/navikt/helse-sparsom/workflows/Sparsom/badge.svg)

## Beskrivelse

Tar inn søknader og inntektsmeldinger for en person og foreslår utbetalinger.

## Nyttige spørringer

### Alle unike varsler

```postgresql
select count(1) as antall, tekst
from aktivitet
inner join melding m on m.id = aktivitet.melding_id
where level='VARSEL'::level
group by m.id
;
```
#### Alternativ spørring

```postgresql
    with aktiviteter as (select distinct on (melding_id) melding_id
                         from aktivitet
                         where level = 'VARSEL'::level
     )
select * from melding where id in (select melding_id from aktiviteter)
;
```

### Alle unike funksjonelle feil

```postgresql
select count(1) as antall, tekst
from aktivitet
         inner join melding m on m.id = aktivitet.melding_id
where level='FUNKSJONELL_FEIL'::level
group by m.id
;
```

#### Alternativ spørring

```postgresql
    with aktiviteter as (select distinct on (melding_id) melding_id
                         from aktivitet
                         where level = 'FUNKSJONELL_FEIL'::level
     )
select * from melding where id in (select melding_id from aktiviteter)
;
```

### Alle aktiviteter på en ident (fnr)

Om spørringen skal brukes i utgangspunkt i en applikasjon, kan det være greit å ta bort `string_agg(concat_ws...`-delen: den er her for å bedre
lesbarheten om spørringen kjøres i et console.
````postgresql
select a.id, a.level, a.tidsstempel, m.tekst, string_agg(concat_ws(',', kt.type, kn.navn, kv.verdi), ';') from aktivitet a
inner join personident p on p.id = a.personident_id
inner join melding m on a.melding_id = m.id
inner join aktivitet_kontekst ak on a.id = ak.aktivitet_id
inner join kontekst_type kt on ak.kontekst_type_id = kt.id
inner join kontekst_navn kn on kn.id = ak.kontekst_navn_id
inner join kontekst_verdi kv on kv.id = ak.kontekst_verdi_id
where p.ident='<FNR>'
group by a.id, a.tidsstempel, m.tekst
order by tidsstempel
;
````

### Alle med en kontekstnavn og kontekstverdi

`kontekstnavn` avhenger av hva Spleis putter på aktivitetene, men i skrivende stund er følgende kontekstnavn tilgjengelig å søke på:

```
vedtaksperiodeId
aktørId
meldingsreferanseId
vilkårsgrunnlagId
utbetalingId
skjæringstidspunkt
vilkårsgrunnlagtype
fagsystemId
tilstand
organisasjonsnummer
fødselsnummer
```

`<verdi>` i spørringen vil være det du vil søke på: et orgnr, en aktørId, osv.

```postgresql
    with aktiviteter as materialized (
        select a.id
         from aktivitet_kontekst ak
                  inner join kontekst_verdi kv on ak.kontekst_verdi_id = kv.id
                  inner join kontekst_navn k on ak.kontekst_navn_id = k.id
                  inner join aktivitet a on a.id = ak.aktivitet_id
         where kv.verdi = '<VERDI>' and k.navn = '<kontekst navn>' -- and level='VARSEL'::level
    )
select a.id, p.ident, a.tidsstempel, a.level, m.tekst from aktivitet a
 inner join personident p on p.id = a.personident_id
 inner join melding m on m.id = a.melding_id
where a.id in (select id from aktiviteter)
;
```

I spørringen over er `level='VARSEL'::level` kommentert ut for å hvordan man kan strupe spørringen til å søke etter et konkret aktivitetsnivå.

### Finne aktiviteter med en bestemt meldingstekst:

**NB** det er ikke lagt inn indekser som vil hjelpe med spørringen som `tekst LIKE ...`. En `EXPLAIN` på en slik spørring vil vise at `cost` blir skyhøy.

```postgresql
    select * from melding
                      inner join aktivitet a on melding.id = a.melding_id
    where tekst = 'Søknaden inneholder Arbeidsdager utenfor sykdomsvindu'
    order by tidsstempel desc
;
```

## Oppgradering av gradle wrapper
Finn nyeste versjon av gradle her: https://gradle.org/releases/

```./gradlew wrapper --gradle-version $gradleVersjon```

Husk å oppdater gradle versjon i build.gradle.kts filen
```gradleVersion = "$gradleVersjon"```

## Protip for å kjøre tester raskere
Finn filen .testcontainers.properties, ligger ofte på hjemmeområdet ditt eks:

```~/.testcontainers.properties```

legg til denne verdien

```testcontainers.reuse.enable=true```

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen [#team-bømlo-værsågod](https://nav-it.slack.com/archives/C019637N90X).
