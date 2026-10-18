# NeroLogistics

> Part of the **Neroland** sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** in development — version `0.6.0-beta.1`. Ships the Network Controller, Universal Duct, energy cables, item storage (incl. 54-slot storage block), the digital storage network, auto-crafting, buffers, the logistics processor, drone ports, logistics trains, rocket cargo ports — real Nerospace cargo flights when Nerospace 1.3.0+ is installed, standalone stub routes otherwise — and chat-report dashboards. Key follow-ups: Forge capability wiring, the live dashboard GUI, and the full 5-tab terminal.

**Nerospace (optional):** built against Nerospace `1.3.0` (`compileOnly`). Until that tag publishes to GitHub Packages, run `./gradlew publishToMavenLocal` in `../nerospace` before building. See [docs/NEROSPACE-ROUTE-INTEGRATION.md](docs/NEROSPACE-ROUTE-INTEGRATION.md).

**NeroQuests (optional):** the jar carries a quest pack — chapter "Supply Lines", 15 quests — that shows up in the quest book when NeroQuests is installed and is inert data otherwise (no dependency). Quest rewards can unlock a cosmetic dashboard badge and a saved cell-preset slot; see [`PRIVACY.md`](PRIVACY.md) for what that stores.

**NeroLink (optional):** NeroLogistics registers a link module with Neroland Core's link API, so a NeroLink companion app can show the storage, conduit networks, rocket shipments and drone ports around the player — scoped to their own dimension within 128 blocks while they are online, never a server-wide roster — plus two safe actions (set a nearby cargo port's shipping class, acknowledge your own alert). Off with `enableLinkModule=false`; see [wiki/Link-Module.md](wiki/Link-Module.md).

## Build targets

- **Minecraft:** 26.1.2, 26.2 and 26.3
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "9 cells")
- **Java:** 25
- Mod id: `nerologistics` · package `za.co.neroland.nerologistics`

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

## Building

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # all nine
```

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.

## Documentation

- [Wiki](https://github.com/Neroland/nerologistics/wiki) — player- and contributor-facing docs
  (source lives in [`wiki/`](wiki/); edit there, never the `.wiki` repo directly).
- [`CHANGELOG.md`](CHANGELOG.md) — release history.
- [`PRIVACY.md`](PRIVACY.md) — data posture and crash reporting.

## Privacy

NeroLogistics sends anonymous crash reports (stack trace + mod/game versions only — never IPs,
usernames, UUIDs, or world data) via Sentry on EU servers. It is **on by default** and **opt-out**:
set `telemetryEnabled=false` in `config/nerologistics.properties` (client-local, takes effect on next
launch). Per-player throughput attribution is off by default; quest-unlocked perks are the only other
per-player record (UUID-keyed, erased via Core). Full disclosure: [`PRIVACY.md`](PRIVACY.md).
