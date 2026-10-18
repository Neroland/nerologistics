# Companion Link Module

NeroLogistics can publish a view of your logistics network to a **NeroLink companion
client** — the phone app that talks to your server. This page describes exactly what it shows, what
it lets you change, and why it is scoped the way it is.

The module is plumbing, not content: nothing in the mod behaves differently whether it is on or off.
With it off, or with no NeroLink bridge installed on the server, a companion client simply lists
NeroLogistics as absent.

> **Requires:** Neroland Core (already a hard dependency) and the separate **NeroLink** bridge mod on
> the server. NeroLogistics itself ships no server, no networking and no account of any kind — it
> registers what it can show with Core, and the bridge serves it.

## Turning it off

```properties
# config/nerologistics.properties
enableLinkModule=false
```

Server-authoritative: the server's value wins. With it `false` the module is never registered, no
events are published and every action is refused.

## What you can see

Everything is scoped to **where you are standing**, in **your own dimension**, within **128 blocks**
of you, and only **while you are online**. See [Why proximity, not ownership](#why-proximity-not-ownership)
below. Every section reports `player_online`, `scan_radius` and your current `dimension` so the app
can explain an empty list instead of looking broken.

### `storage`

What the storage network around you is holding.

- Aggregated **item** lines (registry id, display name, total count) and **fluid** lines (id, name,
  total mB) — the same totals a Storage Terminal shows, read from the same cached index.
- Every **drive bay** in range: position, medium, priority, whether it is active, and its average
  fill; then per installed **cell**: kind (item/fluid), tier, capacity, amount stored, distinct
  types, priority, and whether it is partitioned.
- Totals: `item_types`, `item_total`, `fluid_types`, `fluid_total_mb`.

Optional query parameters: `q` (case-insensitive substring on an id or a name) and `limit`
(1–1000, default 250). The response says `items_truncated` / `fluids_truncated` when there was more.

### `network`

Conduit-network health.

- One row per network in range: medium, conduit count, endpoint count, whether it is at the node cap,
  the managing controller's throughput multiplier, and whether two controllers are in conflict.
- When the controller itself is in range: its position, status (`IDLE` / `ACTIVE` / `CONFLICT`),
  attached module count and charge.
- The dimension's aggregate **throughput** counters (items, fluid mB, energy, drones dispatched,
  shipments launched and delivered) and the configured per-tick budgets.

There are exactly three media — **item, fluid and energy**. NeroLogistics has **no gas medium**, and
the `media` field says so explicitly rather than leaving a client to guess.

### `shipments`

Rocket cargo, on whichever route is active — the standalone stub routes, or real Nerospace cargo
flights when Nerospace routing is on. It is the same proximity-scoped view the Logistics Dashboard and
`/nerologistics shipping` print.

- **In transit**: every shipment launched from a port near you — its id, origin and destination as
  the game labels them (a dimension name on the stub route; a pad or station name under Nerospace),
  its state (`IN_FLIGHT`, `AWAITING_CHUNK`, `HOLDING`, `UNLOADING`, …), an ETA in ticks and seconds,
  and how long it has been held when it is holding.
- **Ports**: rocket cargo ports in range — position, channel, shipping class (configured *and*
  effective), launch schedule, the last launch refusal if there is one, whether fuel is buffered
  (stub route only; under Nerospace the rocket carries the fuel), charge, and buffer usage.
- The route and its queue: `routing` (which route provider is active), `pending`,
  `pending_capacity`, `queue_full`.

Two deliberate omissions. **No far-side position is ever reported** — only the labels you would see
in game. And the **shipping class lives on the port, not the shipment**, which is why it appears on
`ports` rows. A port's dispatcher and opt-in owner are never read out; a port row only says whether
*you* are its opt-in owner (`is_owner`).

### `drones`

The drone fleet around you — the section the companion app's drones screen is built on.

- Every **drone port** in range: position, name, routing (`auto` when unnamed, `named` otherwise),
  mode (`IMPORT` / `EXPORT`), drones installed, drones in flight, free lanes, whether a Hyperspeed
  Card is fitted, charge, and buffer usage.
- Every **delivery drone** currently in the air near you: its entity id, where it is, and which port
  it launched from.
- The relevant balance keys: port range, max drones per port, capacity per drone, energy per dispatch.

## What you can do

Two actions, and deliberately only two.

### `set_shipping_class`

Retunes a rocket cargo port's quality-of-service lane. Parameters: `x`, `y`, `z` (the port) and an
optional `class` (`STANDARD`, `EXPRESS` or `BULK`); omit `class` and the port cycles.

This is the one genuinely safe write the mod has. It moves no items, cancels nothing in flight, and
the same tap reverses it. On the stub route it re-prices the *next* launch's fuel and re-times its
transit; under Nerospace routing (where Nerospace owns travel time and fuel) it changes the port's
launch energy and whether Bulk waits for a full load — see
[Cross-Dimension Shipping](Cross-Dimension-Shipping.md).
It requires you to be online and within reach of the port; a port that is out of reach, unloaded, or
simply not there all answer identically, so the action cannot be used to hunt for other people's
cargo ports. On servers that enabled per-player attribution, a port that records a different owner is
refused.

With `enableShippingQos=false` the action is refused, exactly as the in-game Configurator refuses to
cycle the class.

### `acknowledge_alert`

Marks one of *your own* NeroLogistics alerts read in Core's per-player alert store. Works while you
are offline, which is rather the point of an alert.

### What is deliberately absent

No action extracts from the storage network, pushes items into a buffer, runs the auto-crafter, fires
a cargo port early or retargets a drone port. All of those move quantities of things in a world you
are not standing in. **Cancelling a shipment in flight is absent for the same reason**: the manifest
holds the only copy of its payload, so "cancel" would have to either destroy it or materialise it
somewhere, and both are item movement dressed up as a toggle.

## Live events

| Topic | Scope | Payload |
|-------|-------|---------|
| `shipments` | your own port | position, destination dimension, shipping class, stacks, items, ETA when quoted |
| `shipment_state` | broadcast | departed/arrived, origin + destination dimension, stacks, items |
| `network_fault` | broadcast | the active route's in-transit count hit its cap: pending count and capacity |

`arrived` is published by the stub route's delivery. Under Nerospace routing the cargo travels as a
Nerospace flight, so its arrival is Nerospace's own event and NeroLogistics does not echo it.

Broadcasts carry **dimension ids and counts and nothing else** — no positions, no names, no payload
contents — because they reach every connected session.

One alert is raised: **the shipment queue is full and your port has stopped launching** (rate-limited
to once per port per five minutes). Because it has to be addressed to somebody, it only exists on
servers that turned per-player attribution on — see below.

## Why proximity, not ownership

NeroLogistics records **no owner** for a conduit network, a drive bay, a storage cell, a drone port,
a delivery drone or a cargo manifest. That is not an oversight; it is the mod's data posture. Those
things are world state, and keeping them free of player identity is why NeroLogistics stores no
personal data by default (see [Dashboard & Privacy](Dashboard-and-Privacy.md)).

That leaves two honest options for a companion module, and one dishonest one:

- **Invent an owner** — fabricate an ownership model that the mod does not have and the game does not
  enforce. Rejected.
- **Answer with the server-wide registries** — hand a phone every drive bay, port and network on the
  server, which is a map of every base on it. Firmly rejected.
- **Answer what you could already see by standing where you are.** That is what this module does.

The single exception is a rocket cargo port, which stores the placing player's UUID **only** when a
server opts into `perPlayerThroughputAttribution` (off by default). That UUID is used for two things
and nothing else: routing your own shipment events and alerts to you, and refusing the shipping-class
action on somebody else's port. It is never read out into a snapshot — the module asks the port
"is this player the owner?" rather than "who owns you?" — and Core's data-erasure hook scrubs it on
request, along with everything else the ecosystem holds for you.

Under Nerospace routing a port also records a **dispatcher** — the player Nerospace launches on behalf
of. That UUID exists to perform the shipping that player configured, so the link module does not use
it for anything: not for scoping, not for events, not for the action gate.

The trade-off is that the module has nothing to say while you are offline. That is the honest result,
and the `player_online` flag says so plainly rather than presenting an empty base.

Operator status is deliberately **not** honoured. An operator's powers belong to a live command
source, not to a player id arriving over a bridge; honouring them here would turn "I am an admin"
into "my phone can read every base on the server".

## See also

- [Dashboard & Privacy](Dashboard-and-Privacy.md) — the full POPIA/GDPR posture, including the
  opt-in attribution switch this page refers to.
- [Cross-Dimension Shipping](Cross-Dimension-Shipping.md) — cargo ports, routes and shipping classes.
- [Drone Ports](Drone-Ports.md) — what the `drones` section is describing.
- [Storage Network](Storage-Network.md) — cells, drive bays and the index behind the `storage` section.
- [Configuration](Configuration.md) — every config key, including `enableLinkModule`.
