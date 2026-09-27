# Cross-Dimension Shipping

The **Rocket Cargo Port** ships cargo between dimensions. How a shipment travels depends on whether
**Nerospace** is installed:

| | Standalone (stub routes) | With Nerospace 1.3.0+ |
| --- | --- | --- |
| Destination | a whole **dimension** | a specific **Cargo Pad** or **station** |
| Vehicle | none (simulated) | a real **Cargo Rocket** docked on a Cargo Pad |
| Fuel | `nerologistics:rocket_fuel` items in the port | the rocket's own fuel (Nerospace) |
| Arrives in | a same-channel port in that dimension | the destination Cargo Pad's inventory |
| Unavailable destination | payload dropped from tracking | Nerospace **holds** the flight, then crates it |

A server can keep the standalone behaviour even with Nerospace installed: set
**`nerospaceRouting = false`** (see [Configuration](Configuration.md)).

## Controls

| Action | Standalone | With Nerospace |
| --- | --- | --- |
| Right-click (empty hand) | cycle destination, then show status | cycle destination (you become the port's **dispatcher**), then show status |
| Sneak-right-click (empty hand) | cycle the route **channel** | toggle **return empty** |
| Right-click with the Configurator | cycle the **shipping class** | cycle the **shipping class** |
| Sneak-right-click with the Configurator | cycle the **launch schedule**, then show status | cycle the **launch schedule**, then show status |
| Redstone pulse into the port | launch now (manual schedule only) | launch now (manual schedule only) |

The status line says where the port is shipping, which lane it uses, and — when it is not launching —
**why**, plus when it will try again.

## Every launch

A port holds a 10-slot cargo buffer and an energy buffer. When it launches depends on its **launch
schedule**:

| Schedule | Launches |
| -------- | -------- |
| **Every interval** (default) | every `shipIntervalTicks` when there is cargo |
| **When full** | only once every buffer slot holds a stack |
| **Manual** | only when a **redstone pulse** reaches the port (one launch per pulse) — no interval work at all |

The schedule mirrors the one on Nerospace's own Cargo Pad, but it belongs to the port: the pad's own
schedule is for cargo loaded into the pad, the port's for cargo in the port.

Every launch:

- A launch is charged **energy per stack** (`shipEnergyPerStack`) — deliberately expensive, so shipping
  complements crewed rocket travel rather than replacing it.
- If the launch is **refused** (no destination, no rocket, not enough fuel, …) the cargo stays in the
  port, the reason is shown in its status, and the port **backs off**: each consecutive refusal doubles
  the wait, up to `shipDenialBackoffMaxTicks`. Right-clicking the port resets the wait. Shortages that
  fix themselves (energy, the server-wide in-transit cap) are retried every interval instead.

## Standalone: stub routes

Without Nerospace every loaded dimension is a destination, so shipping works on its own (for example
Overworld ↔ Nether):

- The port burns **rocket fuel by tag** (`nerologistics:rocket_fuel` — blaze powder and rods by
  default; any mod can add to the tag), `shipFuelPerLaunch` items per launch.
- The shipment becomes a **manifest** held for `shipTransitTicks`. On arrival the destination chunk is
  force-loaded **only momentarily** to deposit the cargo into a same-channel port there (overflow is
  dropped), then released — two dimensions are never kept loaded for the transit.
- Manifests are **durably persisted** (payload, dimensions, positions, ticks — never player identity),
  so a shipment in flight survives a restart. `maxPendingShipments` bounds the store.

## With Nerospace: real cargo flights

With Nerospace 1.3.0 or newer, a launch is a **real Nerospace cargo flight** requested through
Nerospace's route API:

1. Build a **Cargo Pad** into a 3×3 launch pad and dock a fuelled **Cargo Rocket** on it (see the
   Nerospace wiki's Freight chapter).
2. Place the Rocket Cargo Port **touching the Cargo Pad block** — beside it, diagonal to it, or directly
   under it. Its status names the pad it found, or says "place this port next to a Cargo Pad".
3. Right-click the port to pick a destination. The list holds the Cargo Pads and stations **you** may
   ship to — your own, pads shared with you, public pads and stations you manage. Stations show as
   "Station: …". Right-clicking makes you the port's **dispatcher**: the port launches on your behalf.
4. Feed the port cargo (ducts, hoppers or a [Logistics Processor](Logistics-Programming.md) *Ship
   above* rule). Each interval the port asks Nerospace for a flight; when Nerospace accepts, the
   cargo leaves the port.

What changes compared to standalone:

- **Fuel is the rocket's.** The port does **not** consume rocket-fuel items — the docked rocket pays,
  exactly as for a manual launch. Travel time and fuel come from Nerospace's route quote.
- **Refusals are Nerospace's** and show verbatim in the port status: no rocket docked, the pad is
  smaller than 3×3, not enough fuel, the dispatcher may not use a pad, too many flights, …
- **Nothing is lost.** Nerospace persists the flight across restarts; an unavailable destination makes
  it wait (unloaded) or **hold** (occupied / missing pad) and, after Nerospace's hold timeout, drops the
  cargo as a **Cargo Crate** at the destination. The dashboard names the pad and — if you may use that
  pad — where the crate is.
- **Return empty.** Off (the default, `shipReturnEmpty`): the rocket stays at the destination, so a
  receiving base gains a rocket to send back. On: the rocket flies home empty after unloading, and the
  return leg's fuel is charged at launch.
- **Channels do not apply** — the destination is a pad, so the sneak-click toggles return-empty instead.
- **Who the port ships as.** A port with no dispatcher (never configured, or its dispatcher asked for
  their data to be erased) lists public pads only and does not launch until someone right-clicks it.
  See [Dashboard & Privacy](Dashboard-and-Privacy.md).

## Shipping classes (QoS lanes)

Each port has a **shipping class**, cycled with the Configurator.

**Standalone**, the class trades transit time against fuel items:

| Class | Transit time | Fuel per launch |
| ----- | ------------ | --------------- |
| **Standard** | base | base |
| **Express** | ×0.25 (`expressTransitFactor`, min 20 ticks) | ×3 (`expressFuelFactor`) |
| **Bulk** | ×2 (`bulkTransitFactor`) | ×0.5 (`bulkFuelFactor`, rounded up, min 1) |

A fuel-free setup (`shipFuelPerLaunch = 0`) stays free in every class.

**With Nerospace**, flight time and fuel belong to Nerospace and cannot be bought faster, so the class
becomes an **energy and priority** trade-off instead:

| Class | Priority | Energy per launch | Launches when |
| ----- | -------- | ----------------- | ------------- |
| **Express** | first | ×3 (`expressFuelFactor`) | there is cargo |
| **Standard** | second | base | there is cargo |
| **Bulk** | last | ×0.5 (`bulkFuelFactor`) | the buffer is **full** |

Priority matters when several ports share one pad: one rocket means one launch, and the Express port
asks first. With a single port per pad the lanes differ only in energy and Bulk's full-buffer wait —
honestly a thin difference; the lanes mostly matter for standalone shipping.

The class is saved on the port. With **`enableShippingQos = false`** every port ships Standard
regardless of its configured class (the setting is kept for when the toggle returns).

## Upgrading worlds from before 0.4

Ports used to save their destination as a position in a list (`DestIndex`), which pointed somewhere else
whenever the list changed. They now save the destination itself. Old ports are converted the first time
they are used: the old index is read against the standalone dimension list and replaced. With Nerospace
routing on, an old dimension destination does not name a pad, so the port asks you to pick a
destination once.

## See also

- [Dashboard & Privacy](Dashboard-and-Privacy.md) · [Logistics Programming](Logistics-Programming.md) ·
  [Terminals](Terminals.md) · [Configuration](Configuration.md)
