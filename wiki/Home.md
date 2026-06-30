# NeroLogistics Wiki

Player- and contributor-facing documentation for **NeroLogistics**, part of the
[Neroland ecosystem](../../neroland-mc-ecosystem/README.md). Built on **Neroland Core**.

> **Status:** alpha (version `0.0.1-alpha.1`), built on Neroland Core across the six cross-loader
> cells. NeroLogistics is the ecosystem's automation brain — it moves items, fluids, energy and
> cargo within a base, across a base wirelessly, by drone, and between dimensions. Keep this wiki
> updated alongside code changes (see [`../AGENTS.md`](../AGENTS.md) / [`../CLAUDE.md`](../CLAUDE.md)).

## Contents

- [Network Controller](Controller.md) — the optional single brain that manages a network and scales
  its throughput with network modules.
- [Universal Duct & Native Storage](Universal-Duct-and-Storage.md) — one duct for items + fluids, and
  the native item storage block the network indexes.
- [Auto-Crafting](Auto-Crafting.md) — the auto-crafter that builds items from network stock using a
  pattern.
- [Buffers](Buffers.md) — keep-stocked and passive-cache reservoir blocks.
- [Drone Ports](Drone-Ports.md) — standalone RF ports that move items point-to-point and between
  networks; drones-as-lanes and the unrendered Hyperspeed upgrade.
- [Trains](Trains.md) — native cheap bulk hauling between train stations on a named line.
- [Conduits](Conduits.md) — legacy item ducts, fluid ducts and energy cables; per-face modes, filters,
  and how networks form.
- [Terminals](Terminals.md) — wireless cargo terminals, the storage request terminal, and the Create
  train cargo interface.
- [Drones](Drones.md) — the drone hub and delivery drones (capped pool, channels, energy cost).
- [Cross-Dimension Shipping](Cross-Dimension-Shipping.md) — the rocket cargo port, routes, rocket fuel,
  and the orbit gate.
- [Dashboard & Privacy](Dashboard-and-Privacy.md) — the logistics dashboard and the POPIA/GDPR data
  posture.
- [Configuration](Configuration.md) — every config key, with defaults.

Add one page per block, item, or system as it is built, and link it here. Keep this page as the index.

## How it fits together

Everything is built on **Neroland Core**: conduits and cables discover neighbours through Core's
energy/fluid surfaces and vanilla inventories, machine faces speak Core's **side-config** vocabulary,
progression is gated by Core's **gates**, config flows through Core's **config manager**, and any
player data routes through Core's **data-erasure** hook. NeroLogistics never hard-depends on Nerospace,
Nerotech, Create or AE2 — it interoperates through Core capabilities, tags and seams, so each of those
is optional.

## See also

- [Build & contributor context](../AGENTS.md)
- [Ecosystem overview](../../neroland-mc-ecosystem/README.md)
- [This mod's planning docs](../../neroland-mc-ecosystem/nerologistics/)
