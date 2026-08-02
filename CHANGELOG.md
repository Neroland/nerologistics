# Changelog

All notable changes to **NeroLogistics** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Nothing yet._

## [0.1.0-alpha.1] - 2026-08-03

First release with a native digital storage network, rule-based logistics programming and
shipping quality-of-service lanes.

### Added

#### Storage network

- **Storage cells** — four item tiers (1k / 8k / 64k / 512k total items) and four fluid tiers
  (16 / 128 / 1024 / 8192 buckets). Pure count-based capacity, no byte/type math.
- Cell contents live **on the cell item**, so cells stay portable between bays.
- Per-cell **9-slot partition filter** and **signed priority**, edited via a sneak-use config menu.
- **Drive Bay** — six-slot block with comparator fill output. Digital-only: never exposed as a
  vanilla container, so ducts and hoppers cannot vacuum cells out of it.
- **Storage index** — lazily built and hard-capped per network, aggregating drive bays,
  read-through vanilla containers and Core fluid storages with priority-honouring insert/extract
  routing.

#### Terminals

- **Storage Terminal** — duct-attached block opening a live, scrollable window onto the whole
  index: abbreviated counts (`1.2k`), instant client-side search by name or mod id, count/name/mod
  sorting, click / right-click / shift-click extraction, carried-stack and shift-click insertion,
  and a fluids tab with bucket fill/drain. All transfers are server-validated against the exact
  item, never client slot indices. Re-syncs are change-driven and throttled to
  `terminalResyncTicks`.
- **Wireless Terminal** — sneak-use a Network Controller to bind, then open the same screen
  anywhere within `wirelessTerminalRange` of that controller (default 64, same dimension, `-1` =
  unlimited). Clear feedback when unbound, out of range or networkless.

#### Logistics programming

- **Logistics Processor** — duct-attached block holding up to 8 rule-based supply policies. Each
  rule pairs a ghost item (exact item + components), a BELOW/ABOVE comparator and a 1–1,000,000
  threshold with one of three actions:
  - keep the adjacent inventory **stocked** from the storage index,
  - **export** network excess into the adjacent inventory,
  - **ship** network excess via the nearest rocket cargo port on the same network.
- Threshold steppers support shift ×10, ctrl ×100 and shift+ctrl ×1000 increments.
- Evaluation is server-side on a staggered interval (never per-tick), with per-rule status dots
  (idle / acted / no network / no target / no port / no fuel / no energy / blocked), an NE cost per
  executed action, and rules persisted in block NBT.

#### Shipping QoS

- Every rocket cargo port now carries a **shipping class**, cycled by right-clicking with the
  Configurator:
  - **STANDARD** — unchanged.
  - **EXPRESS** — transit ×0.25 (min 20 ticks), fuel ×3.
  - **BULK** — transit ×2, fuel ×0.5 rounded up (min 1).
- Applied where the route's transit and fuel are priced, so Nerospace's per-route costs scale too.
- Manifests and pre-QoS worlds load unchanged (missing class = STANDARD).

#### Interop & tooling

- **Energized Power interop** — live Forge-Energy interop through Neroland Core's shared energy
  tags. Energized Power already targets MC 26.1+, so this integration is active rather than
  dormant.
- **Wiki sync workflow** — the in-repo `wiki/` folder now publishes automatically to the GitHub
  wiki.

#### New config keys

`enableStorageNetwork`, per-tier cell capacities, `storageIndexRefreshTicks`,
`enableStorageTerminal`, `terminalResyncTicks`, `wirelessTerminalRange`,
`enableLogisticsProcessor`, `logisticsRuleIntervalTicks`, `logisticsActionCapPerCycle`,
`logisticsEnergyPerAction`, `enableShippingQos`, `expressTransitFactor`, `expressFuelFactor`,
`bulkTransitFactor`, `bulkFuelFactor`.

### Changed

- `/nerologistics gallery` now teaches usage — every showcased block and item carries a one-line
  usage hint, the new storage blocks and items are included, and two new live demos show the
  digital storage network (Drive Bay with a preloaded cell + Storage Terminal) and the Logistics
  Processor with its adjacent target chest.
- Gallery labels are now `text_display` holograms (one two-line display per exhibit) instead of
  armor-stand name tags. Name tags rendered every label as a full LivingEntity with a two-pass text
  draw each frame regardless of distance; with usage hints doubling the count to ~60 that dropped
  clients to ~12 FPS (render-thread CPU-bound, GPU idle). `gallery clear` removes both the new
  displays and legacy armor-stand labels.
- **CurseForge upload** split into per-file direct uploads with client/server environment metadata,
  serialized with retry on transient 5xx responses.
- Bumped the Neroland Core dependency to **1.9.0**.

### Fixed

- **Core dependency version range** now floors at the compiled Core version instead of a broad
  `[1.0,2.0)`, preventing loads against a too-old Core.
- Audit remediation across the network core:
  - `SavedData` reads routed through a recovery guard — corrupt saves no longer crash the server.
  - Server-stop registry clearing — no state leaks between world loads.
  - Controller cache invalidation on network topology changes.
  - Filter persistence across save/reload.
  - Transfer anti-ping-pong — items no longer oscillate between equivalent endpoints.
  - Endpoint-cache reuse plus interval staggering — no synchronized full rescans.
  - Drone counter bookkeeping instead of per-dispatch AABB entity scans.
  - POPIA/GDPR owner-UUID erasure coverage — all owner records honour Core's
    `data.PlayerDataErasure` hook.
  - All menu-open sites routed through the `MenuOpener` guard (Paper-hybrid safety).
  - Chunk force-load correctness for cross-dimension arrivals — momentary loads only, always
    released.

## [0.0.1-alpha.2] - 2026-07-04

The controller-centric redesign, **Stages 7–13**, built on the 0.0.1-alpha.1 foundation.

### Added

- **Network Controller** — the optional single network brain, with module-driven capacity.
  Networks still form without one; attaching a controller manages automation and throughput.
- **Universal Duct** — one content-routed duct for items and fluids with per-face modes and
  filters, replacing the earlier item-duct / fluid-duct split.
- **Typed storage** and a controller-owned unified index spanning items and fluids.
- **Terminal redesign** and **native auto-crafting** from network stock using patterns.
- **Buffer blocks** — keep-stocked leveling and passive fixed-cache modes.
- **Drone-port redesign** — standalone RF-powered point-to-point transport, drones-as-lanes,
  network bridging, and the unrendered Hyperspeed upgrade.
- **Logistics trains** — native cheap bulk hauling between named stations.
- **Animated 3D models** for the redesigned blocks.
- **Configurator** item for in-world block configuration.
- **Nerospace compatibility** layer.
- Expanded `/nerologistics gallery` showcase with item displays.

### Changed

- Modrinth release metadata now sets the client/server environment on published versions.
- Bumped loader and API versions within the Minecraft line.

## [0.0.1-alpha.1] - 2026-06-30

Initial release — **Stages 1–6** of the original flat, ownership-scoped logistics network.

### Added

- Multiloader scaffold and CI automation: NeoForge, Forge and Fabric on MC 26.1.2 and 26.2.
- **Network model and local transport** — item and fluid ducts with per-face modes and filters.
- **Energy cables** on Neroland Core's shared power framework.
- **Item storage**, including the 54-slot warehouse storage block the network indexes.
- **Base-scale automation** — drone hub and the Create train interface.
- **Rocket cargo routes** for cross-dimension shipping (stub provider pending the Nerospace API).
- **Chat-report dashboards** and configuration via Core's shared config system.
- POPIA/GDPR compliance surface and opt-out Sentry crash reporting.
- Command palette, mod logo and store descriptions.
- In-repo wiki scaffold.

[Unreleased]: https://github.com/Neroland/nerologistics/compare/v0.1.0-alpha.1...HEAD
[0.1.0-alpha.1]: https://github.com/Neroland/nerologistics/compare/v0.0.1-alpha.2...v0.1.0-alpha.1
[0.0.1-alpha.2]: https://github.com/Neroland/nerologistics/compare/v0.0.1-alpha.1...v0.0.1-alpha.2
[0.0.1-alpha.1]: https://github.com/Neroland/nerologistics/releases/tag/v0.0.1-alpha.1
