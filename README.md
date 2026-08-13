# Just Compatible

Server-side compatibility diagnostics and safe migration framework for Minecraft 1.21.1 NeoForge.

Community, support and bug reports: [join the yofred.dev Discord](https://discord.gg/cCHeBPKkD).

Official binary downloads are distributed exclusively through CurseForge and Modrinth. GitHub is used for source code, issue tracking, tags, and changelogs only.

## Simple use

Install it on the server with Just Core. Runtime fixes for Vinery, Starcatcher and Bountiful Baubles activate automatically when those mods are present.

Optional integrations deliberately declare no mod version range. Reference JARs are compile-time fixtures only. At runtime, each adapter checks the API shape it needs; an incompatible redesign disables that adapter instead of preventing the server from starting.

`/justcompatible scan` shows active integrations and performs a Waystones dry run. `/justcompatible repair` applies only unambiguous Waystones migrations after writing a backup.

## Safety rules

- No dimension name is hardcoded.
- Runtime adapters do not write chunks, inventories, or `level.dat`.
- Waystones is the only persistent migration. It requires UUID + position evidence, backs up first, and rolls the whole in-memory batch back if rebuilding its index fails.
- Unknown and ambiguous records are reported and left untouched.

## Integrations

- **Waystones:** safe dimension migration while preserving UUIDs and discoveries.
- **Vinery:** reads the greatest active server-dimension clock, preventing wine age from going backwards without rewriting bottles.
- **Starcatcher:** recognizes Overworld-/Nether-/End-like dimensions from their vanilla dimension effects rather than configured world names.
- **Bountiful Baubles:** after login or dimension travel, repairs stale attributes plus migrated absolute clocks on Endless Pearl, Dark Egg, Mad Aura, and Mind's Eye in the player's inventory, Ender Chest, Curios, or session state. Since 0.3.2, an affected item is also repaired immediately on use, covering items removed from backpacks after login without polling inventories. It never scans worlds or loads chunks.
- **Vanilla Vaults:** detects absolute Vault timers inherited from a world with a later game clock. `/justcompatible vaults scan` is read-only; `/justcompatible vaults repair` backs up and resets only impossible timers while preserving keys, loot tables, states, and rewarded players. It never scans or loads chunks and does not match Create Item Vaults.

## Advanced Waystones migration

`/justcompatible waystones scan` performs a dry run. It detects moved waystones by matching the stored UUID and block position against active dimensions. No dimension identifier is hardcoded.

`/justcompatible waystones repair` writes a JSON backup, updates only unambiguous matches, rebuilds Waystones indexes, and synchronizes online players. Player discoveries remain valid because waystone UUIDs are preserved.

Optional mappings can be configured as `old_namespace:world=new_namespace:world`, but they are accepted only when the destination contains a matching waystone UUID at the same position.
