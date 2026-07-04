# NeroLogistics

> Part of the [Neroland](../neroland-mc-ecosystem) sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** in development — version `0.0.1-alpha.1`, build Stages 1–13 implemented. Ships the Network Controller, Universal Duct, energy cables, item storage (incl. 54-slot storage block), auto-crafting, buffers, drone ports, logistics trains, rocket cargo routes (stub provider pending the Nerospace API), and chat-report dashboards. Key follow-ups: Forge capability wiring, in-transit shipment persistence, and the full 5-tab terminal.

## Build targets

- **Minecraft:** 26.1.2 and 26.2
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "6 cells")
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
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build \
          :forge:26.1.2:build :forge:26.2:build \
          :fabric:26.1.2:build :fabric:26.2:build   # all six
```

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.

## Planning docs

Design, feature and dependency docs for this mod live in the umbrella repo under
[`../neroland-mc-ecosystem/nerologistics`](../neroland-mc-ecosystem/nerologistics).
