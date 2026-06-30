# Network Controller

The **Network Controller** is the single brain of a NeroLogistics network. It is **optional** —
ducts, cables, terminals, drone ports and storage all work on their own with zero config — but
attaching one controller to a conduit line **manages** that network and scales its throughput.

## What it does

- **One controller per network.** A network may have exactly one controller. If two controllers end
  up touching the same conduit network, neither manages it: the network falls back to base throughput
  and both controllers report a **conflict**. To run several networks, give each its own controller
  and move goods between them with **drones** (export from one network's drone port, import on
  another's).
- **Governs throughput.** While powered, the controller multiplies its network's per-tick transfer
  budget for items, fluids and energy. An **unpowered** controller still *manages* the network, but at
  the base (unboosted) rate.
- **Modular capacity — expands as you build.** Place **Network Modules** next to the controller (they
  chain — any module connected to the cluster counts). Each module adds throughput. The bonus per
  module, the module cap and the maximum multiplier are all server-config values.

## Using it

1. Craft a **Network Controller** (expensive — it does the heavy lifting) and place it touching a
   duct/cable line.
2. Power it with an **Energy Cable** (it draws a small upkeep each tick to stay boosted).
3. Build a **Network Module** cluster against the controller to raise throughput.
4. **Right-click** the controller to read its status: `active`, `idle` (not on a network) or
   `conflict`, along with the module count and the current throughput percentage.

## Standalone behaviour

Without a controller, conduits still move resources at the configured base budget — NeroLogistics
never *requires* a controller. The controller is the upgrade that unifies and accelerates a network,
not a gate in front of basic transport.

## Configuration

| Key | Default | Meaning |
| --- | --- | --- |
| `enableController` | `true` | Master toggle for the controller's managed throughput boost. |
| `controllerUpkeepPerTick` | `8` | NE/tick to keep a controller powered (below this it manages at base speed). |
| `controllerModuleBonusPercent` | `25` | Throughput bonus (%) each connected module adds to a powered controller. |
| `controllerMaxModules` | `16` | Max modules a controller counts (bounds the flood-fill). |
| `controllerMaxPercent` | `500` | Cap on the managed throughput multiplier (100 = no boost). |

See [Configuration](Configuration.md) for the full list.

## See also

- [Conduits](Conduits.md) — how networks form and how faces route.
- [Drones](Drones.md) — bridging goods between separate controllers/networks.
