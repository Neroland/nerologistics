# NeroLogistics Wiki

Player- and contributor-facing documentation for **NeroLogistics**, part of the **Neroland**
sci-fi Minecraft mod ecosystem. Built on **Neroland Core**.

> **Status:** beta (version `0.6.0-beta.1`), built on Neroland Core across the nine cross-loader
> cells. NeroLogistics is the ecosystem's automation brain — it moves items, fluids, energy and
> cargo within a base, across a base wirelessly, by drone, and between dimensions.

## Contents

- [Network Controller](Controller.md) — the optional single brain that manages a network and scales
  its throughput with network modules.
- [Universal Duct & Native Storage](Universal-Duct-and-Storage.md) — one duct for items + fluids, and
  the native item storage block the network indexes.
- [Storage Network](Storage-Network.md) — portable storage cells, the six-bay Drive Bay, and the
  per-network storage index that aggregates cells, chests and tanks.
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
- [Cross-Dimension Shipping](Cross-Dimension-Shipping.md) — the rocket cargo port: standalone routes or
  real Nerospace cargo flights, launch schedules, and the express/bulk shipping classes.
- [Logistics Programming](Logistics-Programming.md) — the logistics processor's rule-based supply
  policies (keep stocked, export excess, auto-ship), rocket-port conditions and redstone alarms.
- [Companion Link Module](Link-Module.md) — the read-only storage/network/shipment/drone view a
  NeroLink companion client sees, the two safe actions, and why it is scoped by proximity.
- [Dashboard & Privacy](Dashboard-and-Privacy.md) — the logistics dashboard, the shipping report and
  the POPIA/GDPR data posture (attribution opt-out, the port dispatcher).
- [Configuration](Configuration.md) — every config key, with defaults.

NeroLogistics also ships a **quest pack** (chapter "Supply Lines", 15 quests) that appears in the quest book
when a compatible quest mod is installed and is inert otherwise; its rewards and the data they store are
described under [Dashboard & Privacy](Dashboard-and-Privacy.md).

Add one page per block, item, or system as it is built, and link it here. Keep this page as the index.

## How it fits together

Everything is built on **Neroland Core**: conduits and cables discover neighbours through Core's
energy/fluid surfaces and vanilla inventories, machine faces speak Core's **side-config** vocabulary,
config flows through Core's **config manager**, any player data routes through Core's
**data-erasure** hook, and companion apps read it through Core's **link API**. Core's **gates** are used
only as records a quest pack can read (such as the first accepted rocket launch) — nothing in
NeroLogistics is locked behind a gate; it is standalone-first and paced by its own recipes and
materials. NeroLogistics never hard-depends on Nerospace, Nerotech, Create or AE2 — it interoperates
through Core capabilities, tags and seams, so each of those is optional.

## See also

- [Build & contributor context](https://github.com/Neroland/nerologistics/blob/main/AGENTS.md)
- [Source repository](https://github.com/Neroland/nerologistics) ·
  [Issues](https://github.com/Neroland/nerologistics/issues)
