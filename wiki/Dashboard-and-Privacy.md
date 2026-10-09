# Dashboard & Privacy

## Logistics Dashboard

Right-click the **Logistics Dashboard** block to read a summary in chat:

- the adjacent network's **node and endpoint counts**;
- this dimension's aggregate **throughput** (items, fluid mB, energy NE moved);
- **shipping** — launched, delivered and in-transit counts, plus, with Nerospace, how many flights were
  **held** at their destination and how many were **crated**;
- **drones dispatched**;
- the **shipping report** for everything within 128 blocks of the dashboard (below).

The same shipping report is available anywhere with **`/nerologistics shipping`**, centred on you.

### Shipping report

- **In transit** — each shipment launched from a nearby port: flight number, origin → destination,
  state (in flight, waiting for the destination to load, holding, unloading) and ETA, or how long it
  has been holding.
- **Ports** — how many rocket cargo ports are nearby, and every one that is **not launching**, with
  the reason (no rocket, not enough fuel, no destination, …).
- **Crated cargo** — with Nerospace, a flight that could not be delivered in time ends as a Cargo Crate
  at the destination pad. The report names the pad, and shows where it is only if you are allowed to
  ship to that pad anyway.

The report is **proximity-scoped** (your dimension, 128 blocks), never a server-wide list, so it cannot
be used to map other players' bases.

All dashboard figures are **aggregate world figures keyed by dimension or block** — never by player.
They are fully useful with per-player attribution off.

## Privacy (POPIA / GDPR)

NeroLogistics is built to store as little personal data as possible:

- Networks, terminals, drones, dashboards and in-transit shipments are keyed by **block position /
  dimension**, never by player identity.
- **Per-player attribution is opt-in and off by default** (`perPlayerThroughputAttribution`). With it
  off, no analytics data about a player is stored.
- When turned on, it records only the placing player's **UUID** (never a name) against their cargo
  port's shipments. That record is **retention-pruned** daily (`attributionRetentionDays`) and is
  purged through Core's shared **data-erasure** hook — both on an explicit erase request and by Core's
  inactivity sweep. The opt-in flag doubles as the server toggle to disable personal-data logging.
- **Any player can opt out** of attribution for themselves: **`/nerologistics privacy optout`** (and
  `optin` to undo, or no argument to see your setting). While opted out, your shipments count only in
  the anonymous per-dimension totals; your existing attribution record and the owner UUID on your
  loaded ports are dropped at once, and unloaded ports drop it when they next load. The opt-out list is
  UUIDs only; a data-erasure request removes you from it along with everything else, so run the command
  again afterwards if you want to stay opted out.

### The port's dispatcher (Nerospace routing only)

Nerospace only launches a cargo flight **on behalf of a player**, so with Nerospace routing a rocket
cargo port stores one UUID that is needed for the port to work at all — its **dispatcher**:

| | |
| --- | --- |
| What | the UUID of the player who last picked a Nerospace destination on the port — never a name |
| Why | Nerospace needs someone to launch as (performing the shipping that player set up) |
| Where | on the port block only; never logged, never in telemetry |
| Shown as | "you", "someone else" or "nobody" — never the player's identity |
| Kept for | the life of the port; breaking it deletes the UUID, and the next player to configure the port replaces it |
| Erased by | Core's shared data-erasure hook — loaded ports at once, unloaded ports when they next load |

This is separate from attribution: it is stored whatever `perPlayerThroughputAttribution` says, because
it is functionally necessary rather than analytics. A port whose dispatcher was erased lists public
pads only and does not launch until someone right-clicks it.

This mirrors the ecosystem-wide pattern: any mod that stores player data routes erasure through Core
so a single request clears a player everywhere.

### Crash reporting

NeroLogistics sends anonymous crash reports to the developers via Sentry (EU servers): the stack trace
of an error in NeroLogistics' own code plus mod, loader, Minecraft, OS and Java versions. No names,
UUIDs, IP addresses, chat, coordinates or world data are sent, file paths are scrubbed of your account
name, and reports are capped at 10 per session. It is **on by default** and **opt-out**: set
`telemetryEnabled=false` in `config/nerologistics.properties` (client-local, takes effect on next
launch). Full disclosure in `PRIVACY.md` in the source repository.

## See also

- [Configuration](Configuration.md) · [Cross-Dimension Shipping](Cross-Dimension-Shipping.md)
