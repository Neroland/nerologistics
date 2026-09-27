# Changelog

All notable changes to **NeroLogistics** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0-beta.1] - 2026-09-27

Rocket cargo as real **Nerospace** cargo flights (Nerospace 1.3.0+, optional), shipping schedules,
port conditions for the Logistics Processor, a player attribution opt-out, and the first unit tests.

### Phase 1 — Nerospace route API (rocket cargo as real Nerospace flights)

Requires **Nerospace 1.3.0+** for rocket routing; without it (or with `nerospaceRouting=false`) the
standalone stub routes behave exactly as before.

#### Added

- **Real cargo flights.** With Nerospace 1.3.0+ a Rocket Cargo Port launches through Nerospace's
  semver-stable route API (`za.co.neroland.nerospace.api.route`): the port must touch a Cargo Pad with a
  docked, fuelled Cargo Rocket, destinations are the **Cargo Pads and stations** the player may ship to
  (stations prefixed "Station:"), and Nerospace flies, persists, holds and — after its timeout — crates
  the cargo. The port supplies the manifest and pays energy only; it no longer consumes
  `nerologistics:rocket_fuel` items on this path (no double fuel charge).
- **Dispatcher.** Under Nerospace routing a port stores the UUID of the player who last picked a
  destination on it, because Nerospace launches on behalf of a player. Functionally necessary (stored
  regardless of the attribution toggle), UUID only, never logged, shown only as "you" / "someone else" /
  "nobody", deleted with the block and erased through Core's `PlayerDataErasure` (tombstones for
  unloaded ports). Documented in `PRIVACY.md` and the wiki.
- **Port status.** Every destination click (and sneak-use with the Configurator) prints the destination,
  lane, origin pad, dispatcher (relative to you), return-empty setting and the **last refusal** with its
  retry countdown. Refused launches keep the cargo and back off exponentially, capped by
  `shipDenialBackoffMaxTicks`; interacting with the port resets the wait.
- **Return empty** per port (sneak-right-click under Nerospace routing, where channels do not apply),
  defaulting to the new `shipReturnEmpty` config.
- **Shipping report** in the Logistics Dashboard and the new **`/nerologistics shipping`** command:
  in-transit shipments with state and ETA, nearby ports that are not launching and why, held / crated
  counts, and a crate-recovery hint for dropped flights. Proximity-scoped (own dimension, 128 blocks).
- Config: `nerospaceRouting` (default `true`), `shipDenialBackoffMaxTicks` (default `2400`),
  `shipReturnEmpty` (default `false`).

#### Changed

- Shipping lanes under Nerospace routing trade **energy and priority** instead of fuel and time
  (Nerospace owns both): Express launches first when ports share a pad and pays `expressFuelFactor`%
  energy, Bulk waits for a full buffer and pays `bulkFuelFactor`%. Standalone lanes are unchanged.
- The `RouteProvider` seam now thinks in destinations with a stable identity (`pad:<id>` or
  `dim:<id>`) and owns the launch itself; the reflective binding against the dimension-level
  `NerospaceRoutes` facade is gone. Nerospace is a `compileOnly` dependency behind the existing
  `isModLoaded` guard, so no Nerospace class loads without Nerospace; a Nerospace older than the route
  API logs one warning and keeps the stub.
- Loader manifests floor the optional Nerospace dependency at the compiled version (`[1.3.0,2.0)` on
  NeoForge/Forge; `suggests >=1.3.0 <2.0.0` on Fabric).
- The Logistics Processor's *Ship above* rule no longer reports "no fuel" under Nerospace routing (the
  port holds no fuel there).

#### Migration

- Ports saved their destination as `DestIndex`, an index into a live list. They now save `Dest`
  (`pad:<id>` / `dim:<namespace:path>`). An old port resolves its index once against the standalone
  dimension list on first use and rewrites itself; nothing else changes. Under Nerospace routing an old
  dimension destination names no pad, so the port asks for a destination once.

#### Build

- `nerospace_version=1.3.0` in `gradle.properties`; `compileOnly` per loader node, resolved from
  `mavenLocal()` or Nerospace's GitHub Packages feed. **Until the Nerospace 1.3.0 tag publishes, run
  `./gradlew publishToMavenLocal` in `../nerospace` first** — CI resolves it only once that package
  exists. `-PwithNerospace` loads Nerospace in dev runs (default runs stay without it).

### Phase 2 — Shipping features

#### Added

- **In-transit liveness** (chat report, list form): the dashboard and `/nerologistics shipping` list each
  shipment launched nearby with its state — in flight (with ETA), waiting for the destination to load,
  holding (with how long), unloading — and flag crated cargo. No GUI yet; the chat report comes first.
- **Launch schedules** per port, cycled with the Configurator's sneak-use: **every interval** (the
  default and the old behaviour), **when full** (every slot holds a stack) and **manual** (one launch
  per redstone pulse, no interval work at all). Ports saved before this load as *every interval*.
- **Port conditions on Logistics Processor rules** (right-click a rule's action): *needs fuel*, *in
  transit*, *stalled*. A conditioned rule only acts while its condition holds; a conditioned rule with
  no item is an **alarm** that makes the processor emit a full redstone signal. Rules saved before this
  load with no condition.
- **`/nerologistics privacy optout|optin`**: any player can opt out of per-player attribution for
  themselves. Opted-out shipments count only in the anonymous totals; the player's attribution record
  and loaded ports' owner UUID are dropped at once (unloaded ports on next load). UUID-only SavedData,
  cleared by Core's `PlayerDataErasure` with everything else.
- **Crate-recovery hint**: when Nerospace crates a flight's cargo, the report names the destination pad —
  and its position when the viewer may ship to that pad anyway. Position only, no player data.
- **`/nerologistics gallery`** gains a live **Rocket shipping** row: a fed, powered Rocket Cargo Port with
  no destination yet (so it reports why it is not launching), a Logistics Processor on the same duct with a
  *port stalled* alarm rule driving a redstone lamp, and a Logistics Dashboard for the shipping report. The
  showcase hints for the port, processor, dashboard and Configurator describe the new controls, and the
  build message points at `/nerologistics shipping` and `/nerologistics privacy`.

### Conduit look

#### Changed

- **Conduits render like Nerospace's Universal Pipe**: a seamless translucent tube with a glowing,
  animated core line and thin dark edge lines — arms are open-ended, a core wall is only drawn on
  unconnected sides, and a core edge line only where both of its faces are walls, so a line reads as
  one continuous pipe with edges along its length and no joints or inner panes — tinted by medium (amber item duct, blue fluid duct, red
  energy cable, teal universal duct). A multipart model driven by six connection block-state properties
  shows an arm only towards a same-medium conduit or something the conduit can serve (inventory, drive
  bay, fluid or energy storage, network controller), and hides it on a **Disabled** face.
- Cosmetic only: transport never reads the connection properties, so networks, endpoints and
  throughput are unchanged. Connections are computed server-side on placement, neighbour change and
  face-mode change, plus a staggered 100-tick safety refresh; clients get ordinary block updates (no
  new packets, no block-entity renderer).
- The conduits no longer occlude neighbours, and their hitbox follows the tube (8×8 core plus arms)
  instead of a full cube. Existing worlds pick up their arms on the first tick after loading.

### Phase 3 — Optimisation and hygiene

No profiling session was run for this change (it needs an in-game world with ~50 ports); the changes
below are the cheap, pre-identified ones only.

#### Changed

- `ShipmentManager`'s port directory uses insertion-ordered sets instead of lists (O(1) register /
  unregister instead of O(n) `contains`/`remove`); `findPort` keeps first-registered order.
- A rocket cargo port's launch attempt scans its 10-slot buffer once (cargo, fullness and the fuel stack
  together) instead of up to three times. Stub fuel is paid from the largest fuel stack.
- The per-tick shipment driver reads the `ShipmentState` instance cached for the running server and
  runs the full guarded accessor (which also refreshes its recovery backup) once a second; the cache is
  dropped on server stop.

#### Removed

- The reflective `NerospaceRoutes` binding, its `MB_PER_FUEL_ITEM` item-fuel conversion and the unused
  `RouteProvider` overloads.

#### Tests

- First unit tests (`common/src/test/java`, JUnit 5, run on the NeoForge nodes like Nerospace's):
  destination keys and the `DestIndex` migration, retry backoff, lane maths, and contract tests that
  fail the build if Nerospace adds a denial, flight state or schedule mode the NeroLogistics mirrors do
  not know.

## [0.3.0-alpha.1] - 2026-09-24

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** Every NeroLogistics recipe uses a vanilla recipe type, which EMI shows on its own, so
  nothing needed a plugin. The build now compiles against the community EMI Unofficial Port (Unstable),
  the only EMI build for Minecraft 26.x, and dev clients load it with `-PwithEmi` (default runs stay
  JEI-only). EMI stays optional.

## [0.2.0-alpha.1] - 2026-09-20

Minecraft **26.3** support, plus the changes previously listed under *Unreleased*.

_Nothing yet._

### Minecraft 26.3

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.
- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- Requires **Neroland Core 1.13.0** (was `1.9.0`) — the first Core release with a 26.3
  build. The loader range still derives from the pin (`[${nerolandcore_version},2.0)`).
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- Block classes build their codecs through Core's `BlockCodecs` (26.3 removed block-type codecs); `codec()` is kept without `@Override` so one source compiles on every version.
- 26.3 API differences are handled with Stonecutter blocks: `PoseStack#rotate` (was `mulPose`), the new `Prediction` argument on `drop` / `placeItemBackInInventory`, `setPermanentlyInvulnerable`, and similar renames.
- Fixed: the Configurator recipe used the invalid category `tools`, so it failed to load on every version. It is now `equipment`.
- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.
- NeoForge metadata: the deprecated `logoFile` property is replaced by `iconFile` on 26.2+ (the logo is a square 256x256 PNG) while 26.1.2, whose FML only understands the old key, still gets `logoFile` — the key is chosen per cell when the manifest is expanded. This clears NeoForge 26.2+'s dev-only "uses the deprecated `logoFile` property" warning screen. The Forge manifest is unchanged: `logoFile` is still the only key Forge supports.

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

[Unreleased]: https://github.com/Neroland/nerologistics/compare/v0.3.0-alpha.1...HEAD
[0.3.0-alpha.1]: https://github.com/Neroland/nerologistics/compare/v0.2.0-alpha.1...v0.3.0-alpha.1
[0.2.0-alpha.1]: https://github.com/Neroland/nerologistics/releases/tag/v0.2.0-alpha.1
[0.1.0-alpha.1]: https://github.com/Neroland/nerologistics/compare/v0.0.1-alpha.2...v0.1.0-alpha.1
[0.0.1-alpha.2]: https://github.com/Neroland/nerologistics/compare/v0.0.1-alpha.1...v0.0.1-alpha.2
[0.0.1-alpha.1]: https://github.com/Neroland/nerologistics/releases/tag/v0.0.1-alpha.1
