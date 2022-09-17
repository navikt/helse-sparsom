insert into aktivitet_kontekst(aktivitet_id, kontekst_type_id, kontekst_navn_id, kontekst_verdi_id)
select a.id, kt.id, kn.id, kv.id from aktivitet a
inner join aktivitet_kontekst_denormalisert akd on a.denormalisert_id = akd.aktivitet_id
inner join kontekst_type kt on akd.kontekst_type = kt.type
inner join kontekst_navn kn on akd.kontekst_navn=kn.navn
inner join kontekst_verdi kv on akd.kontekst_verdi=kv.verdi
where a.denormalisert_id is not null and a.denormalisert_id between 1000000 and 499999999
on conflict do nothing
;