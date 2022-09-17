alter table aktivitet
    add constraint aktivitet_melding_id_fkey foreign key (melding_id) references melding(id) on delete cascade,
    add constraint aktivitet_personident_id_fkey foreign key (personident_id) references personident(id) on delete cascade;

alter table aktivitet_kontekst
    add constraint aktivitet_kontekst_aktivitet_id_fkey foreign key (aktivitet_id) references aktivitet(id) on delete cascade ,
    add constraint aktivitet_kontekst_kontekst_navn_id_fkey foreign key(kontekst_navn_id) references kontekst_navn(id) on delete cascade,
    add constraint aktivitet_kontekst_kontekst_type_id_fkey foreign key(kontekst_type_id) references kontekst_type(id) on delete cascade ,
    add constraint aktivitet_kontekst_kontekst_verdi_id_fkey foreign key(kontekst_verdi_id) references kontekst_verdi(id) on delete cascade;

create index if not exists idx_aktivitet_level on aktivitet(level);