alter table aktivitet
    drop constraint aktivitet_melding_id_fkey,
    drop constraint aktivitet_personident_id_fkey;

alter table aktivitet_kontekst
    drop constraint aktivitet_kontekst_aktivitet_id_fkey,
    drop constraint aktivitet_kontekst_kontekst_navn_id_fkey,
    drop constraint aktivitet_kontekst_kontekst_type_id_fkey,
    drop constraint aktivitet_kontekst_kontekst_verdi_id_fkey;