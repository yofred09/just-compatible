# Just Compatible

Server-side compatibility, migration and preventive diagnostics for Minecraft 1.21.1 NeoForge.

Community, support and bug reports: [join the yofred.dev Discord](https://discord.gg/cCHeBPKkD).

Official binary downloads are distributed exclusively through CurseForge and Modrinth. GitHub is used for source code, issue tracking, tags, and changelogs only.

## Simple use

Install it on the server with Just Core. Runtime fixes for Vinery, Starcatcher and Bountiful Baubles activate automatically when those mods are present.

Optional integrations deliberately declare no mod version range. Reference JARs are compile-time fixtures only. At runtime, each adapter checks the API shape it needs; an incompatible redesign disables that adapter instead of preventing the server from starting.

`/justcompatible scan` shows active integrations and performs a Waystones dry run. `/justcompatible repair` applies only unambiguous Waystones migrations after writing a backup.

## Three pillars

- **World recovery:** safely reconciles data after servers change dimensions or world clocks.
- **Mod compatibility:** targeted adapters for Waystones, Vinery, Starcatcher, Bountiful Baubles and vanilla Vaults.
- **Server health:** the early Mod Doctor reports unsafe client-only JARs and conservative duplicate candidates before normal mod discovery.

## Safety rules

- No dimension name is hardcoded.
- Runtime adapters do not write chunks, inventories, or `level.dat`.
- Waystones is the only persistent migration. It requires UUID + position evidence, backs up first, and rolls the whole in-memory batch back if rebuilding its index fails.
- Unknown and ambiguous records are reported and left untouched.
- The Mod Doctor never deletes JARs. Automatic quarantine is disabled by default and every move records original path, destination, SHA-256, version, reason and timestamp.
- Just Compatible, Just Core, Minecraft, NeoForge and language-provider identifiers are protected from quarantine.

## Integrations

- **Server Mod Doctor:** runs before normal NeoForge mod discovery on dedicated servers and detects explicit client-only JARs plus conservative duplicate candidates. It writes `config/justcompatible-mod-doctor-report.txt`. Automatic quarantine is opt-in during the 0.4.x validation period through `config/justcompatible-mod-doctor.properties`; files are moved reversibly and never deleted.

- **Waystones:** safe dimension migration while preserving UUIDs and discoveries.
- **Vinery:** reads the greatest active server-dimension clock, preventing wine age from going backwards without rewriting bottles.
- **Starcatcher:** recognizes Overworld-/Nether-/End-like dimensions from their vanilla dimension effects rather than configured world names.
- **Bountiful Baubles:** after login or dimension travel, repairs stale attributes plus migrated absolute clocks on Endless Pearl, Dark Egg, Mad Aura, and Mind's Eye in the player's inventory, Ender Chest, Curios, or session state. Since 0.3.2, an affected item is also repaired immediately on use, covering items removed from backpacks after login without polling inventories. Version 0.4.2 adds a run-once compatibility bridge for every equipped Bountiful bauble, restoring missing continuous behavior such as Turtle Shell effects without double-running dangerous effects such as Mad Aura. It never scans worlds or loads chunks.
- **Vanilla Vaults:** detects absolute Vault timers inherited from a world with a later game clock. `/justcompatible vaults scan` is read-only; `/justcompatible vaults repair` backs up and resets only impossible timers while preserving keys, loot tables, states, and rewarded players. It never scans or loads chunks and does not match Create Item Vaults.

## Advanced Waystones migration

`/justcompatible waystones scan` performs a dry run. It detects moved waystones by matching the stored UUID and block position against active dimensions. No dimension identifier is hardcoded.

`/justcompatible waystones repair` writes a JSON backup, updates only unambiguous matches, rebuilds Waystones indexes, and synchronizes online players. Player discoveries remain valid because waystone UUIDs are preserved.

Optional mappings can be configured as `old_namespace:world=new_namespace:world`, but they are accepted only when the destination contains a matching waystone UUID at the same position.

## Commands

- `/justcompatible info` — integration status.
- `/justcompatible scan` — global read-only scan.
- `/justcompatible doctor status` — summary of the latest startup scan.
- `/justcompatible doctor report` — displays the latest report.
- `/justcompatible doctor restore` — restores every available quarantined JAR and requests a restart.
- `/justcompatible waystones scan|repair` — explicit Waystones migration.
- `/justcompatible vaults scan|repair` — loaded-chunk-only Vault timer migration.

## Mod Doctor files

- `config/justcompatible-mod-doctor.properties` — early bootstrap settings.
- `config/justcompatible-mod-doctor-report.txt` — human-readable latest scan.
- `config/justcompatible-mod-doctor-manifest.jsonl` — append-only reversible quarantine history.
- `mods/justcompatible-quarantine/` — quarantined files; never deleted.

Set `automaticSafeFixes=true` only after reviewing a report. Changes apply on the next restart.
