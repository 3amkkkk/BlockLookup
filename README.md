# BlockLookup

BlockLookup is een snelle logging plugin voor Paper/Spigot servers waarmee je blokplaatsingen, blokbreukjes en chatberichten kunt terugvinden en (deels) terugdraaien. Ontworpen voor grote servers: writes gaan async in batches naar SQLite en rollback/restore wordt tick-based toegepast om lag spikes te beperken.

## Features

- Logt block place en block break inclusief world, coordinaten en blockdata
- Logt chatberichten (async)
- Lookup commando voor recente block events rond je positie
- Chat log viewer met filters
- Rollback en restore met radius en tijdvenster
- SQLite opslag met WAL mode en indexen voor snelle queries
- Kleurrijke output met multi-color gradient prefix

## Installatie

1. Build de jar.
2. Zet de jar in `plugins/`.
3. Start de server een keer om `config.yml` te genereren.
4. Pas `config.yml` aan indien nodig en herstart.

De database wordt opgeslagen in de plugin folder als `blocklookup.db` (configurable).

## Commands

Alle commands hebben ook alias `/bl`.

- `/bl lookup [player|*] [duration] [radius] [limit]`
- `/bl chat [player|*] [duration] [limit]`
- `/bl rollback <player|*> <duration> [radius] [limit]`
- `/bl restore <player|*> <duration> [radius] [limit]`

Duration formats:

- `30m` minuten
- `2h` uren
- `1d` dagen
- `45` wordt gezien als minuten

Voorbeelden:

- `/bl lookup * 30m 15 30`
- `/bl lookup Notch 2h 20 50`
- `/bl rollback * 15m 15 500`
- `/bl restore Griefer 1h 30 2000`

## Permissions

- `blocklookup.lookup`
- `blocklookup.chat`
- `blocklookup.rollback`
- `blocklookup.restore`

Defaults staan op `op` in `plugin.yml`.

## Config

Zie [`src/main/resources/config.yml`](C:/Users/jeanp/dev/BlockLookup/src/main/resources/config.yml) voor alle instellingen.

Belangrijkste opties:

- `database.writeBatchSize` bepaalt hoeveel events per commit worden weggeschreven
- `database.flushIntervalTicks` bepaalt hoe vaak de writer minimaal probeert te flushen
- `rollback.applyPerTick` bepaalt hoeveel acties per tick worden toegepast
- `rollback.loadChunks` bepaalt of chunks automatisch geladen mogen worden tijdens rollback/restore

## Opslagmodel

SQLite tabellen:

- `block_events` voor block place/break
- `chat_events` voor chat

Block events slaan zowel `before` als `after` op als `material` en `blockdata` string, zodat rollback/restore exact kan matchen op de huidige block state om onverwachte overschrijvingen te beperken.

## Performance notes

- Logging gebeurt op MONITOR priority en alleen als events niet gecanceld zijn
- Database writes zijn async en batch-based
- Rollback/restore draait op de main thread (block updates) maar wordt per tick gedoseerd
- Als de write queue vol raakt, worden entries gedropt en wordt dit rate-limited gelogd in de console

## Build

Deze plugin is opgezet voor Java 17 en Maven.


De output jar staat daarna in `target/`.

