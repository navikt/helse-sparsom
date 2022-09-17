drop index if exists idx_aktivitet_kontekst_kontekst_verdi_id;
drop index if exists idx_aktivitet_kontekst_navn_id;
drop index if exists idx_aktivitet_kontekst_aktivitet_id;

create index if not exists idx_aktivitet_kontekst_multi on aktivitet_kontekst(kontekst_navn_id,kontekst_verdi_id);