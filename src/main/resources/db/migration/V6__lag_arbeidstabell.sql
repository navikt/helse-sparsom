create table arbeidstabell(
    id serial primary key,
    start_offset int not null,
    end_offset int not null,
    startet timestamptz default null,
    ferdig timestamptz default null
);

-- lager en tabell bestående av ceil(1620709158/1000000) = 1621 rader
-- hvor hver rad har en start_offset-verdi som øker med 1 million
-- tanken er at instansene som skal plukke fra tabellen henter ut en offset hvor startet IS NULL
-- og så utfører de en spørring ala SELECT ... FROM aktivitet_denormalisert OFFSET <start-offset> LIMIT 1000000.
-- alternativt kan man også lage spørringer som sjekker WHERE id BETWEEN <start-offset> AND <end-offset>
insert into arbeidstabell(start_offset, end_offset)
select start_offset,start_offset+999999 as end_offset from generate_series(0,1620709158,1000000) as start_offset;