# Changelog

All notable changes to **NeroLogistics** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Native storage network (backend)**: typed **storage cells** — four item tiers (1k/8k/64k/512k
  total items) and four fluid tiers (16/128/1024/8192 buckets), pure count-based capacity with no
  byte/type math — whose contents live on the cell item itself (portable between bays); a 9-slot
  **partition** and signed **priority** per cell, edited via a sneak-use config menu; the six-slot
  **Drive Bay** block (digital — never exposed as a vanilla container, so ducts/hoppers cannot
  vacuum cells) with comparator fill output; and the lazily-built, hard-capped per-network
  **storage index** aggregating drive bays, read-through vanilla containers and Core fluid
  storages, with priority-honouring insert/extract routing. Config: `enableStorageNetwork`,
  per-tier cell capacities, `storageIndexRefreshTicks`.
- **Storage Terminal**: a duct-attached block opening a live, scrollable window onto the whole
  storage index — abbreviated counts (`1.2k`), instant client-side search (name or mod id) and
  count/name/mod sorting, click/right-click/shift-click extraction and carried-stack or
  shift-click insertion (all server-validated against the exact item, never client indices), a
  fluids tab with bucket fill/drain, and change-driven re-syncs while open (at most every
  `terminalResyncTicks`, default 10).
- **Wireless Terminal** item: sneak-use a Network Controller to bind it, then use it anywhere
  within `wirelessTerminalRange` blocks of that controller (default 64, same dimension, `-1` =
  unlimited) to open the same storage screen remotely; clear feedback when unbound, out of range
  or networkless. Config: `enableStorageTerminal`, `terminalResyncTicks`,
  `wirelessTerminalRange`.
- **Logistics programming**: the **Logistics Processor** — a duct-attached block holding up to 8
  rule-based supply policies. Each rule pairs a ghost item (exact item + components), a
  BELOW/ABOVE comparator and a 1–1,000,000 threshold (−/+ buttons with shift ×10 / ctrl ×100 /
  shift+ctrl ×1000 steps) with an action: **keep the adjacent inventory stocked** from the network
  storage index, **export network excess** into the adjacent inventory, or **ship network excess**
  via the nearest rocket cargo port on the same network. Server-side evaluation on a staggered
  interval (never per-tick), per-rule status dots (idle / acted / no network / no target / no
  port / no fuel / no energy / blocked), NE cost per executed action, rules persisted in block
  NBT. Config: `enableLogisticsProcessor`, `logisticsRuleIntervalTicks`,
  `logisticsActionCapPerCycle`, `logisticsEnergyPerAction`.
- **Shipping QoS lanes**: every rocket cargo port now has a **shipping class** — STANDARD /
  EXPRESS (transit ×0.25, min 20 ticks, fuel ×3) / BULK (transit ×2, fuel ×0.5 rounded up, min
  1) — cycled by right-clicking the port with the Configurator, applied where the route's transit
  and fuel are priced (so Nerospace's per-route costs scale too). Manifests and pre-QoS worlds
  load unchanged (missing class = STANDARD). Config: `enableShippingQos` (off = all ports ship
  STANDARD), `expressTransitFactor`, `expressFuelFactor`, `bulkTransitFactor`, `bulkFuelFactor`.

### Fixed

- Audit remediation across the network core:
  - `SavedData` reads routed through a recovery guard (corrupt saves no longer crash the server).
  - Server-stop registry clearing (no state leaking between world loads).
  - Controller cache invalidation on network topology changes.
  - Filter persistence across save/reload.
  - Transfer anti-ping-pong (items no longer oscillate between equivalent endpoints).
  - Endpoint-cache reuse plus interval staggering (no synchronized full rescans).
  - Drone counter bookkeeping instead of per-dispatch AABB entity scans.
  - POPIA/GDPR owner-UUID erasure coverage — all owner records honour Core's
    `data.PlayerDataErasure` hook.
  - All menu-open sites routed through the `MenuOpener` guard (Paper-hybrid safety).
  - Chunk force-load correctness for cross-dimension arrivals (momentary loads only, always
    released).

## [0.0.1-alpha.2] - 2026-08-02

### Added

- **Energized Power interop** — live Forge-Energy interop through Neroland Core's shared energy
  tags (Energized Power already targets MC 26.1+, so this integration is active rather than
  dormant).
- **Wiki sync workflow** — the in-repo `wiki/` folder now publishes automatically to the GitHub
  wiki.

### Changed

- **CurseForge upload split** into per-file direct uploads with client/server environment
  metadata (serialized, with retry on transient 5xx responses).
- Bumped the Neroland Core dependency to the current release.

### Fixed

- **Core dependency version range** now floors at the compiled Core version instead of a broad
  `[1.0,2.0)`, preventing loads against a too-old Core.

## [0.0.1-alpha.1] - 2026-07-04

### Added

- Initial release: build **Stages 1–13** of the controller-centric logistics network.
- **Network Controller** — the optional single network brain, with module-driven capacity.
- **Universal Duct** — one duct for items and fluids, with per-face modes and filters.
- **Energy cables** on Neroland Core's shared power framework.
- **Item storage**, including the 54-slot warehouse storage block the network indexes.
- **Auto-crafting** from network stock using patterns.
- **Buffer blocks** — keep-stocked leveling and passive fixed-cache modes.
- **Drone ports** and legacy drone hub — standalone RF-powered point-to-point item transport,
  drones-as-lanes, and the unrendered Hyperspeed upgrade.
- **Logistics trains** — native cheap bulk hauling between named stations.
- **Rocket cargo routes** for cross-dimension shipping (stub provider pending the Nerospace API).
- **Chat-report dashboards** and configuration via Core's shared config system.
- Cross-loader build for the six cells: NeoForge, Forge and Fabric on MC 26.1.2 and 26.2.
