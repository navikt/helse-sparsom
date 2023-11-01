package no.nav.helse.sparsom.job

class Varselkodemapper() {
    private val kompensering = mapOf(
        Varselkode.RV_UT_23 to listOf("Det er beregnet et negativt beløp"),
        Varselkode.RV_RE_1 to listOf("Fant ikke refusjonsgrad for perioden"),
        Varselkode.RV_IM_4 to listOf("Mottatt flere inntektsmeldinger - den første inntektsmeldingen som ble mottatt er lagt til grunn."),
        Varselkode.RV_IM_25 to listOf("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden på grunn av: FerieEllerAvspasering"),
        Varselkode.RV_IM_8 to listOf("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden på grunn av:"),
        Varselkode.RV_IM_9 to listOf("ArbeidsforholdsID er fylt ut i inntektsmeldingen. Kontroller om brukeren har flere arbeidsforhold i samme virksomhet. Flere arbeidsforhold støttes ikke av systemet foreløpig."),
        Varselkode.RV_IM_16 to listOf("Inntektsmelding inneholder ikke arbeidsgiverperiode."),
        Varselkode.RV_IV_7 to listOf("Det er gjenbrukt inntektsopplysninger"),
        Varselkode.RV_IT_1 to listOf("Det er utbetalt en periode i Infotrygd etter perioden du skal"),
        Varselkode.RV_IT_3 to listOf("Utbetaling i Infotrygd "),
        Varselkode.RV_IT_5 to listOf("Mangler inntekt for første utbetalingsdag"),
        Varselkode.RV_IT_18 to listOf("Avslutter perioden fordi tilstøtende gikk til Infotrygd"),
        Varselkode.RV_IT_19 to listOf("Dagsatsen har endret seg minst én gang i en historisk, sammenhengende periode i Infotrygd."),
        Varselkode.RV_IT_21 to listOf("Det er lagt inn flere inntekter i Infotrygd med samme fom-dato. Kontroller sykepengegrunnlaget."),
        Varselkode.RV_IT_22 to listOf("Det er lagt inn statslønn i Infotrygd"),
        Varselkode.RV_IT_31 to listOf("Perioden forlenger en behandling i Infotrygd, og har historikk i ny løsning også: Undersøk at antall dager igjen er beregnet riktig."),
        Varselkode.RV_IT_37 to listOf("Det er en utbetaling i Infotrygd nærmere enn 20 dager"),
        Varselkode.RV_SØ_1 to listOf("Søknaden inneholder permittering - se om det har innvirkning på saken før du utbetaler"),
        Varselkode.RV_SØ_2 to listOf("Minst én dag er avslått på grunn av foreldelse", "Minst én dag er avslått fordi søknaden er fremsatt for sent"),
        Varselkode.RV_SØ_4 to listOf("Utdanning oppgitt i perioden i søknaden", "Søknaden inneholder en Utdanningsperiode"),
        Varselkode.RV_SØ_8 to listOf("Utenlandsopphold oppgitt i perioden i søknaden"),
        Varselkode.RV_SØ_10 to listOf("Den sykmeldte har oppgitt å ha andre arbeidsforhold med sykmelding i søknaden.", "Den sykmeldte har fått et nytt arbeidsforhold."),
        Varselkode.RV_SØ_13 to listOf("\"Overlappende søknad starter før, eller slutter etter, opprinnelig periode\"", "Den sykmeldte har fått et nytt arbeidsforhold."),
        Varselkode.RV_SØ_18 to listOf("Søknaden inneholder andre inntektskilder", "Søknad inneholder andre inntektskilder"),
        Varselkode.RV_SØ_20 to listOf("\"Søknad overlapper med, eller er før, en forkastet vedtaksperiode\"", "Søknad overlapper med forkastet vedtaksperiode"),
        Varselkode.RV_SØ_24 to listOf("Mottatt flere søknader - den første søknaden som ble mottatt er lagt til grunn."),
        Varselkode.RV_SØ_25 to listOf("Permisjon oppgitt i perioden i søknaden."),
        Varselkode.RV_SV_1 to listOf("Inntekt under krav til minste sykepengegrunnlag"),
        Varselkode.RV_SV_4 to listOf("Brukeren har flere inntekter de siste tre måneder"),
        Varselkode.RV_SY_3 to listOf("Mottatt flere sykmeldinger - den første sykmeldingen som ble mottatt er lagt til grunn."),
        Varselkode.RV_VV_2 to listOf("Flere arbeidsgivere og ulikt starttidspunkt for sykefraværet"),
        Varselkode.RV_VV_4 to listOf("Minst én dag uten utbetaling på grunn av sykdomsgrad under 20"),
        Varselkode.RV_VV_15 to listOf("Maks antall sykepengedager er nådd i perioden."),
        Varselkode.RV_VV_16 to listOf("Søknaden inneholder egenmeldingsdager som ikke er oppgitt i inntektsmeldingen"),
        Varselkode.RV_MV_1 to listOf("Lovvalg og medlemskap må vurderes manuelt", "Vi vet ikke om bruker er medlem av Folketrygden"),
        Varselkode.RV_AY_3 to listOf("Bruker har mottatt AAP innenfor 6 måneder"),
        Varselkode.RV_AY_4 to listOf("Bruker har mottatt dagpenger innenfor 4 uker"),
        Varselkode.RV_AY_5 to listOf("Har overlappende foreldrepengeperioder med syketilfelle"),
        Varselkode.RV_AY_6 to listOf("Har overlappende pleiepengeytelse med syketilfelle"),
        Varselkode.RV_AY_7 to listOf("Har overlappende omsorgspengerytelse med syketilfelle"),
        Varselkode.RV_AY_8 to listOf("Har overlappende opplæringspengerytelse med syketilfelle"),
        Varselkode.RV_AY_9 to listOf("Har overlappende institusjonsopphold med syketilfelle"),
        Varselkode.RV_SI_3 to listOf("Simuleringen mot oppdragssystemet gir et negativt beløp"),
        Varselkode.RV_SI_7 to listOf("Simulering kom frem til et annet totalbeløp"),
        Varselkode.RV_UT_12 to listOf("Forventet ikke simulering på utbetaling"),
        Varselkode.RV_UT_19 to listOf("Utbetaling markert som ikke godkjent av saksbehandler"),
    )

    fun map(tekst: String): Varselkode? {
        return Varselkode.entries.firstOrNull { it.varseltekst == tekst || it.funksjonellFeilTekst == tekst } ?: finnVarselkodeMedKompensering(tekst)
    }

    private fun finnVarselkodeMedKompensering(tekst: String) =
        kompensering.entries.firstOrNull {
            it.value.any { t -> tekst.startsWith(t) || tekst.contains(t) }
        }?.key
}

enum class Varselkode(
    val varseltekst: String,
    val funksjonellFeilTekst: String = varseltekst
) {

    RV_SY_1("Korrigert sykmelding er lagt til grunn - kontroller dagene i sykmeldingsperioden"),
    RV_SY_2("Mottatt en sykmelding som er skrevet tidligere enn den som er lagt til grunn, vurder sykmeldingene og gjør eventuelle justeringer"),
    RV_SY_3("Mottatt flere sykmeldinger for perioden - den første sykmeldingen som ble mottatt er lagt til grunn. Utbetal kun hvis det blir korrekt"),

    // SØ: Søknad
    RV_SØ_1("Søknaden inneholder permittering. Vurder om permittering har konsekvens for rett til sykepenger"),
    RV_SØ_2("Minst én dag er avslått på grunn av foreldelse. Vurder å sende vedtaksbrev fra Infotrygd"),
    RV_SØ_3("Sykmeldingen er tilbakedatert, vurder fra og med dato for utbetaling."),
    RV_SØ_4("Utdanning oppgitt i perioden i søknaden."),
    RV_SØ_5("Søknaden inneholder Permisjonsdager utenfor sykdomsvindu"),
    RV_SØ_6("Søknaden inneholder egenmeldingsdager etter sykmeldingsperioden"),
    RV_SØ_7("Søknaden inneholder Arbeidsdager utenfor sykdomsvindu"),
    RV_SØ_8("Utenlandsopphold oppgitt i perioden i søknaden."),
    RV_SØ_9("Det er oppgitt annen inntektskilde i søknaden. Vurder inntekt."),
    RV_SØ_10("Den sykmeldte har fått et nytt inntektsforhold."),
    RV_SØ_11("Mottatt søknad out of order"),
    RV_SØ_12("Mottatt overlappende søknad"),
    RV_SØ_13("Overlappende søknad starter før, eller slutter etter, opprinnelig periode"),
    RV_SØ_14("Mottatt flere søknader for annen periode enn siste skjæringstidspunkt"),
    RV_SØ_15("Mottatt flere søknader for perioden - siste søknad inneholder arbeidsdag"),
    RV_SØ_16("Mottatt flere søknader for perioden - det støttes ikke før replay av hendelser er på plass"),
    RV_SØ_17("Søker er ikke gammel nok på søknadstidspunktet til å søke sykepenger uten fullmakt fra verge"),
    RV_SØ_18("Søknaden inneholder andre inntektskilder enn ANDRE_ARBEIDSFORHOLD"),
    RV_SØ_19("Søknad forlenger en forkastet periode"),
    RV_SØ_20("Søknad overlapper med, eller er før, en forkastet vedtaksperiode"),
    RV_SØ_21("Bruker har oppgitt at de har jobbet mindre enn sykmelding tilsier"),
    RV_SØ_22("Søknaden inneholder en Papirsykmeldingsperiode"),
    RV_SØ_23("Korrigert søknad er mottatt med nye opplysninger - kontroller dagene i sykmeldingsperioden"),
    RV_SØ_24("Mottatt flere søknade for perioden - den første søknaden som ble mottatt er lagt til grunn. Utbetal kun hvis det blir korrekt"),
    RV_SØ_25("Permisjon oppgitt i perioden i søknaden. Vurder rett til sykepenger og korriger sykmeldingsperioden"),
    RV_SØ_26("Søknaden inneholder Feriedager utenfor perioden søknaden gjelder for"),
    RV_SØ_27("Mottatt søknad out of order innenfor 18 dager til neste"),
    RV_SØ_28("Søknad har mindre enn 20 dagers gap til en forkastet periode"),
    RV_SØ_29("Søknaden er opprettet fra en utenlandsk sykmelding"),
    RV_SØ_30("Søknaden er markert med flagget sendTilGosys"),
    RV_SØ_31("Søknad er før en forkastet vedtaksperiode hos samme arbeidsgiver"),
    RV_SØ_32("Søknad er før en forkastet vedtaksperiode hos annen arbeidsgiver"),
    RV_SØ_33("Søknad overlapper med en forkastet periode hos samme arbeidsgiver"),
    RV_SØ_34("Søknad overlapper med en forkastet periode hos annen arbeidsgiver"),
    RV_SØ_35("Søknad overlapper delvis med en forkastet periode hos samme arbeidsgiver"),
    RV_SØ_36("Søknad overlapper delvis med en forkastet periode hos annen arbeidsgiver"),
    RV_SØ_37("Søknad forlenger en forkastet periode hos samme arbeidsgiver"),
    RV_SØ_38("Søknad forlenger en forkastet periode hos annen arbeidsgiver"),

    RV_SØ_39("Støtter ikke søknadstypen"),
    RV_SØ_40("Støtter ikke søknadstypen for forlengelser vilkårsprøvd i Infotrygd"),
    RV_SØ_41("Støtter kun søknadstypen hvor den aktuelle arbeidsgiveren er den eneste i sykepengegrunnlaget"),

    // Arbeidsledig søknader
    RV_SØ_42("Støtter ikke førstegangsbehandlinger for arbeidsledigsøknader"),
    RV_SØ_43("Arbeidsledigsøknad er lagt til grunn, undersøk refusjonsopplysningene før du utbetaler"),

    RV_SØ_44("I søknaden er det oppgitt at den sykmeldte har et arbeidsforhold som ikke er registrert i AA-registeret."),

    // OO: Out-of-order
    RV_OO_1("Det er behandlet en søknad i Speil for en senere periode enn denne."),
    RV_OO_2("Saken må revurderes fordi det har blitt behandlet en tidligere periode som kan ha betydning."),

    // IM: Inntektsmelding
    RV_IM_1("Vi har mottatt en inntektsmelding i en løpende sykmeldingsperiode med oppgitt første/bestemmende fraværsdag som er ulik tidligere fastsatt skjæringstidspunkt."),
    RV_IM_2("Første fraværsdag i inntektsmeldingen er ulik skjæringstidspunktet. Kontrollér at inntektsmeldingen er knyttet til riktig periode."),
    RV_IM_3("Inntektsmeldingen og vedtaksløsningen er uenige om beregningen av arbeidsgiverperioden. Undersøk hva som er riktig arbeidsgiverperiode."),
    RV_IM_4("Det er mottatt flere inntektsmeldinger på samme skjæringstidspunkt. Undersøk at arbeidsgiverperioden, sykepengegrunnlaget og refusjonsopplysningene er riktige"),
    RV_IM_5("Sykmeldte har oppgitt ferie første dag i arbeidsgiverperioden."),
    RV_IM_6("Inntektsmelding inneholder ikke beregnet inntekt"),
    RV_IM_7("Brukeren har opphold i naturalytelser"),
    RV_IM_8("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden"),
    RV_IM_9("ArbeidsforholdsID fra inntektsmeldingen er utfylt"),
    RV_IM_10("Første fraværsdag i inntektsmeldingen er forskjellig fra foregående tilstøtende periode"),
    RV_IM_11("Første fraværsdag i inntektsmeldingen er ulik første fraværsdag i sykdomsperioden"),
    RV_IM_12("Første fraværsdag i inntektsmeldingen er utenfor søknadsperioden. Kontroller at inntektsmeldingen er knyttet til riktig periode"),
    RV_IM_13("Første fraværsdag i inntektsmeldingen er utenfor sykmeldingsperioden"),
    RV_IM_14("Første fraværsdag oppgitt i inntektsmeldingen er ulik den systemet har beregnet. Utbetal kun hvis dagsatsen er korrekt"),
    RV_IM_15("Første fraværsdag oppgitt i inntektsmeldingen er ulik den systemet har beregnet. Vurder hvilken inntektsmelding som skal legges til grunn, og utbetal kun hvis dagsatsen er korrekt i forhold til denne."),
    RV_IM_16("Inntektsmelding inneholder ikke arbeidsgiverperiode"),
    RV_IM_17("Inntektsmelding inneholder ikke arbeidsgiverperiode. Vurder om  arbeidsgiverperioden beregnes riktig"),
    RV_IM_18("Inntektsmeldingen mangler arbeidsgiverperiode. Vurder om vilkårene for sykepenger er oppfylt, og om det skal være arbeidsgiverperiode"),
    RV_IM_19("Mottatt flere inntektsmeldinger - den andre inntektsmeldingen som ble mottatt er lagt til grunn. Utbetal kun hvis det blir korrekt."),
    RV_IM_20("Finner ikke informasjon om refusjon i inntektsmelding og personen har brukerutbetaling"),
    RV_IM_21("Inntektsmeldingen mangler ikke arbeidsgiverperiode. Vurder om vilkårene for sykepenger er oppfylt, og om det skal være arbeidsgiverperiode"),
    RV_IM_22("Det er mottatt flere inntektsmeldinger på kort tid for samme arbeidsgiver"),
    RV_IM_23("Arbeidsgiver har oppgitt hullete arbeidsgiverperiode og begrunnelse for redusert utbetaling i arbeidsgiverperiode"),
    RV_IM_24("Det har kommet ny inntektsmelding, vurder om arbeidsgiverperiode er riktig"),
    RV_IM_25("Arbeidsgiver har redusert utbetaling av arbeidsgiverperioden grunnet ferie eller avspasering"),

    // ST: Sykdomstidslinje
    RV_ST_1("Sykdomstidslinjen inneholder ustøttet dag."),

    // RE: Refusjon
    RV_RE_1("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler."),
    RV_RE_2("Mangler refusjonsopplysninger"),

    // IT: Infotrygd
    RV_IT_1("Det er utbetalt en periode i Infotrygd etter perioden du skal behandle nå. Undersøk at antall forbrukte dager og grunnlag i Infotrygd er riktig", funksjonellFeilTekst = "Det er utbetalt en nyere periode i Infotrygd"),
    RV_IT_2("Perioden er lagt inn i Infotrygd, men ikke utbetalt. Fjern fra Infotrygd hvis det utbetales via speil."),
    RV_IT_3("Utbetaling i Infotrygd overlapper med vedtaksperioden"), // funksjonellFeil
    RV_IT_4("Det er registrert utbetaling på nødnummer"), // funksjonellFeil
    RV_IT_5("Mangler inntekt for første utbetalingsdag i en av infotrygdperiodene"), // funksjonellFeil
    RV_IT_6("Det er en ugyldig utbetalingsperiode i Infotrygd (mangler fom- eller tomdato)"),
    RV_IT_7("Det er en ugyldig utbetalingsperiode i Infotrygd (fom er nyere enn tom)"),
    RV_IT_8("Det er en ugyldig utbetalingsperiode i Infotrygd (utbetalingsgrad mangler)"),
    RV_IT_9("Det er en ugyldig utbetalingsperiode i Infotrygd (utbetalingsgrad er mindre eller lik 0)"),
    RV_IT_10("Det er en ugyldig utbetalingsperiode i Infotrygd"),
    RV_IT_11("Det er registrert bruk av på nødnummer"),
    RV_IT_12("Organisasjonsnummer for inntektsopplysning fra Infotrygd mangler"),
    RV_IT_13("Støtter ikke overgang fra infotrygd for flere arbeidsgivere"),
    RV_IT_14("Forlenger en Infotrygdperiode på tvers av arbeidsgivere"),
    RV_IT_15("Personen er ikke registrert som normal arbeidstaker i Infotrygd"),
    RV_IT_16("Støtter ikke saker med vilkårsgrunnlag i Infotrygd"),
    RV_IT_17("Forespurt overstyring av inntekt hvor skjæringstidspunktet ligger i infotrygd"),
    RV_IT_18("Avslutter perioden fordi tilstøtende, eller nyere periode, gikk til Infotrygd"),
    RV_IT_19("Dagsatsen har endret seg minst én gang i en historisk, sammenhengende periode i Infotrygd."),
    RV_IT_20("Det er en utbetalingsperiode som er lagt inn i Infotrygd uten at inntektsopplysninger er registrert."),
    RV_IT_21("Det er lagt inn flere inntekter i Infotrygd med samme fom-dato, den seneste er lagt til grunn. Kontroller sykepengegrunnlaget."),
    RV_IT_22("Det er lagt inn statslønn i Infotrygd, undersøk at utbetalingen blir riktig."),
    RV_IT_23("Direkte overgang fra Infotrygd. Dagsatsen har endret seg minst én gang i Infotrygd. Kontroller at sykepengegrunnlaget er riktig."),
    RV_IT_24("Forkaster perioden fordi vi har oppdaget utbetalinger i Infotrygd"),
    RV_IT_25("Har utbetalt periode i Infotrygd nærmere enn 18 dager fra første dag"),
    RV_IT_26("Infotrygd inneholder utbetalinger med varierende dagsats for en sammenhengende periode"),
    RV_IT_27("Opplysninger fra Infotrygd har endret seg etter at vedtaket ble fattet. Undersøk om det er overlapp med periode fra Infotrygd."),
    RV_IT_28("Perioden er en direkte overgang fra periode i Infotrygd"),
    RV_IT_29("Perioden er lagt inn i Infotrygd - men mangler inntektsopplysninger. Fjern perioden fra SP UB hvis du utbetaler via speil."),
    RV_IT_30("Perioden er lagt inn i Infotrygd, men ikke utbetalt. Fjern fra Infotrygd hvis det utbetales via speil."),
    RV_IT_31("Perioden forlenger en behandling i Infotrygd, og har historikk fra ny løsning: Undersøk at antall dager igjen er beregnet riktig."),
    RV_IT_32("Perioden forlenger en behandling i ny løsning, og har historikk i Infotrygd også: Undersøk at antall dager igjen er beregnet riktig."),
    RV_IT_33("Skjæringstidspunktet har endret seg som følge av historikk fra Infotrygd"),
    RV_IT_34("Avslutter perioden fordi tidligere periode gikk til Infotrygd"),
    RV_IT_35("Det er en utbetalingsperiode i Infotrygd som mangler fom- eller tomdato"),
    RV_IT_36("Periode som før var uten utbetalingsdager har nå utbetalingsdager etter endringer fra Infotrygd"),
    RV_IT_37("Det er en utbetaling i Infotrygd nærmere enn 18 dager"),
    RV_IT_38("En utbetaling i Infotrygd har medført at perioden nå vil utbetales"),

    // VV: Vilkårsvurdering
    RV_VV_1("Arbeidsgiver er ikke registrert i Aa-registeret."),
    RV_VV_2("Flere arbeidsgivere, ulikt starttidspunkt for sykefraværet eller ikke fravær fra alle arbeidsforhold"),
    RV_VV_3("Første utbetalingsdag er i Infotrygd og mellom 1. og 16. mai. Kontroller at riktig grunnbeløp er brukt."),
    RV_VV_4("Minst én dag uten utbetaling på grunn av sykdomsgrad under 20 %. Vurder å sende vedtaksbrev fra Infotrygd"),
    RV_VV_5("Bruker mangler nødvendig inntekt ved validering av Vilkårsgrunnlag"),
    RV_VV_8("Den sykmeldte har skiftet arbeidsgiver, og det er beregnet at den nye arbeidsgiveren mottar refusjon lik forrige. Kontroller at dagsatsen blir riktig."),
    RV_VV_9("Bruker er fortsatt syk 26 uker etter maksdato"),
    RV_VV_10("Fant ikke vilkårsgrunnlag. Kan ikke vilkårsprøve på nytt etter ny informasjon fra saksbehandler."),
    RV_VV_11("Vilkårsgrunnlaget ligger i infotrygd. Det er ikke støttet i revurdering eller overstyring."),
    RV_VV_12("Kan ikke overstyre inntekt uten at det foreligger et vilkårsgrunnlag"),
    RV_VV_13("26 uker siden forrige utbetaling av sykepenger, vurder om vilkårene for sykepenger er oppfylt"),
    RV_VV_14("Denne personen har en utbetaling for samme periode for en annen arbeidsgiver. Kontroller at beregningene for begge arbeidsgiverne er korrekte."),
    RV_VV_15("Maks antall sykepengedager er nådd i perioden"),
    RV_VV_16("Søknaden inneholder egenmeldingsdager som ikke er oppgitt i inntektsmeldingen. Vurder om arbeidsgiverperioden beregnes riktig"),

    // VV: Opptjeningsvurdering
    RV_OV_1("Perioden er avslått på grunn av manglende opptjening"),
    RV_OV_2("Opptjeningsvurdering må gjøres manuelt fordi opplysningene fra AA-registeret er ufullstendige"),

    // MV: Medlemskapsvurdering
    RV_MV_1("Vurder lovvalg og medlemskap"),
    RV_MV_2("Perioden er avslått på grunn av at den sykmeldte ikke er medlem av Folketrygden"),
    RV_MV_3("Arbeid utenfor Norge oppgitt i søknaden"),

    // IV: Inntektsvurdering
    RV_IV_1("Bruker har flere inntektskilder de siste tre månedene enn arbeidsforhold som er oppdaget i Aa-registeret."),
    RV_IV_2("Har mer enn 25 % avvik. Dette støttes foreløpig ikke i Speil. Du må derfor annullere periodene.", funksjonellFeilTekst = "Har mer enn 25 % avvik"),
    RV_IV_3("Fant frilanserinntekt på en arbeidsgiver de siste 3 månedene"),
    RV_IV_4("Finnes inntekter fra flere virksomheter siste tre måneder"),
    RV_IV_5("Har inntekt på flere arbeidsgivere med forskjellig fom dato"),
    RV_IV_6("Inntekter fra mer enn én arbeidsgiver i A-ordningen siste tre måneder. Kontroller om brukeren har flere arbeidsforhold på sykmeldingstidspunktet. Flere arbeidsforhold støttes ikke av systemet."),
    RV_IV_7("Det er gjenbrukt inntektsopplysninger "),

    // SV: Sykepengegrunnlagsvurdering
    RV_SV_1("Perioden er avslått på grunn av at inntekt er under krav til minste sykepengegrunnlag"),
    RV_SV_2("Minst en arbeidsgiver inngår ikke i sykepengegrunnlaget"),
    RV_SV_3("Mangler inntekt for sykepengegrunnlag som følge av at skjæringstidspunktet har endret seg"),
    RV_SV_4("Brukeren har flere inntekter de siste tre måneder. Kontroller om brukeren har flere arbeidsforhold eller andre ytelser på sykmeldingstidspunktet som påvirker utbetalingen."),

    // AY: Andre ytelser
    RV_AY_3("Bruker har mottatt AAP innenfor 6 måneder før skjæringstidspunktet. Kontroller at brukeren har rett til sykepenger"),
    RV_AY_4("Bruker har mottatt dagpenger innenfor 4 uker før skjæringstidspunktet. Kontroller om bruker er dagpengemottaker. Kombinerte ytelser støttes foreløpig ikke av systemet"),
    RV_AY_5("Det er utbetalt foreldrepenger i samme periode."),
    RV_AY_6("Det er utbetalt pleiepenger i samme periode."),
    RV_AY_7("Det er utbetalt omsorgspenger i samme periode."),
    RV_AY_8("Det er utbetalt opplæringspenger i samme periode."),
    RV_AY_9("Det er institusjonsopphold i perioden. Vurder retten til sykepenger."),
    RV_AY_10("Behandling av Ytelser feilet, årsak ukjent"),
    RV_AY_11("Det er utbetalt svangerskapspenger i samme periode."),

    // SI: Simulering
    RV_SI_1("Feil under simulering"),
    RV_SI_2("Simulering av revurdert utbetaling feilet. Utbetalingen må annulleres"),
    RV_SI_3("Det er simulert et negativt beløp."),
    RV_SI_4("Ingenting ble simulert"),
    RV_SI_5("Simulering har endret dagsats eller antall på én eller flere utbetalingslinjer"),
    RV_SI_6("Simulering inneholder ikke alle periodene som skal betales"),
    RV_SI_7("Simulering kom frem til et annet totalbeløp. Kontroller beløpet til utbetaling"),

    // UT: Utbetaling
    RV_UT_1("Utbetaling av revurdert periode ble avvist av saksbehandler. Utbetalingen må annulleres"),
    RV_UT_2("Utbetalingen ble gjennomført, men med advarsel"),
    RV_UT_3("Feil ved utbetalingstidslinjebygging"),
    RV_UT_4("Finner ingen utbetaling å annullere"),
    RV_UT_5("Utbetaling ble ikke gjennomført"),
    RV_UT_6("Forventet ikke å opprette utbetaling"),
    RV_UT_7("Forventet ikke godkjenning på utbetaling"),
    RV_UT_8("Forventet ikke å etterutbetale på utbetaling"),
    RV_UT_9("Forventet ikke å annullere på utbetaling"),
    RV_UT_10("Forventet ikke overførtkvittering på utbetaling"),
    RV_UT_11("Forventet ikke kvittering på utbetaling"),
    RV_UT_12("Forventet ikke simulering på utbetaling"),
    RV_UT_13("Forventet ikke å lage godkjenning på utbetaling"),
    RV_UT_14("Gir opp å prøve utbetaling på nytt"),
    RV_UT_15("Kan ikke annullere: hendelsen er ikke relevant"),
    RV_UT_16("Feil ved kalkulering av utbetalingstidslinjer"),
    RV_UT_17("Utbetaling feilet"),
    RV_UT_18("Utbetaling markert som ikke godkjent automatisk"),
    RV_UT_19("Utbetaling markert som ikke godkjent av saksbehandler"),
    RV_UT_20("Utbetaling fra og med dato er endret. Kontroller simuleringen"),
    RV_UT_21("Utbetaling opphører tidligere utbetaling. Kontroller simuleringen"),
    RV_UT_22("Annullering ble ikke gjennomført"),
    RV_UT_23("Den nye beregningen vil trekke penger i sykefraværstilfellet"),
    RV_UT_24("Utbetalingen er avvist, men perioden kan ikke forkastes. Overstyr perioden, eller annuller sykefraværstilfellet om nødvendig"),

    // OS: Oppdragsystemet
    RV_OS_1("Utbetalingen forlenger et tidligere oppdrag som opphørte alle utbetalte dager. Sjekk simuleringen."),
    RV_OS_2("Utbetalingens fra og med-dato er endret. Kontroller simuleringen"),
    RV_OS_3("Endrer tidligere oppdrag. Kontroller simuleringen."),

    // RV: Revurdering
    RV_RV_1("Denne perioden var tidligere regnet som innenfor arbeidsgiverperioden"),
    RV_RV_2("Forkaster avvist revurdering ettersom vedtaksperioden ikke har tidligere utbetalte utbetalinger."),
    RV_RV_3("Forespurt revurdering av inntekt hvor personen har flere arbeidsgivere (inkl. ghosts)"),
    RV_RV_4("Revurdering er igangsatt og må fullføres"),
    RV_RV_5("Validering av ytelser ved revurdering feilet. Utbetalingen må annulleres"),
    RV_RV_6("Det er oppgitt ny informasjon om ferie i søknaden som det ikke har blitt opplyst om tidligere. Tidligere periode må revurderes."),

    // VT: Vedtaksperiodetilstand
    RV_VT_1("Gir opp fordi tilstanden er nådd makstid"),
    RV_VT_2("Forventet ikke vilkårsgrunnlag"),
    RV_VT_3("Forventet ikke utbetalingsgodkjenning"),
    RV_VT_4("Forventet ikke simulering"),
    RV_VT_5("Forventet ikke utbetaling"),
    RV_VT_6("Forventet ikke overstyring fra saksbehandler"),
    RV_VT_7("Forventet ikke ytelsehistorikk"),

    RV_AG_1("Finner ikke arbeidsgiver"),

    //AN: Annet
    RV_AN_1("Avslutter perioden på grunn av tilbakestilling"),
    RV_AN_2("Feil i vilkårsgrunnlag i AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD"),
    RV_AN_3("Feil i vilkårsgrunnlag i AVVENTER_VILKÅRSPRØVING_GAP"),
    RV_AN_4("Personen har blitt tilbakestilt og kan derfor ha avvik i historikken fra infotrygd."),
    RV_AN_5("Personen har blitt behandlet på en tidligere ident"),

    // YS: Yrkesskade
    RV_YS_1("Yrkesskade oppgitt i søknaden")
    ;
}