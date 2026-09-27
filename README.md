# NeroLogistics

> Part of the **Neroland** sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** in development — version `0.4.0-alpha.1`. Ships the Network Controller, Universal Duct, energy cables, item storage (incl. 54-slot storage block), the digital storage network, auto-crafting, buffers, the logistics processor, drone ports, logistics trains, rocket cargo ports — real Nerospace cargo flights when Nerospace 1.3.0+ is installed, standalone stub routes otherwise — and chat-report dashboards. Key follow-ups: Forge capability wiring, the live dashboard GUI, and the full 5-tab terminal.

**Nerospace (optional):** built against Nerospace `1.3.0` (`compileOnly`). Until that tag publishes to GitHub Packages, run `./gradlew publishToMavenLocal` in `../nerospace` before building. See [docs/NEROSPACE-ROUTE-INTEGRATION.md](docs/NEROSPACE-ROUTE-INTEGRATION.md).

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
