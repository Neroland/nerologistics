# Drones

> **Redesign note:** the **[Drone Port](Drone-Ports.md)** is the current drone block — standalone,
> RF-powered, with import/export modes, drones-as-lanes, name/Auto routing, and the unrendered
> **Hyperspeed** upgrade. The **Drone Hub** below is the legacy Stage-3 block, kept for migration. New
> setups should use Drone Ports.

Drones carry items point-to-point without conduits or wireless links.

## Drone Hub (legacy)

The hub stocks an item buffer (fill it with ducts/hoppers) and dispatches **delivery drones** to
**wireless cargo terminals on its channel** within range. Right-click to cycle the hub's channel.

- It holds a **hard-capped pool** of live drones (`dronesPerHub`); it never dispatches beyond the cap.
- Each dispatch charges the hub's energy buffer (`droneEnergyPerDelivery`); power it with cables.
- Targets come from the cheap wireless-channel membership list — no world scan.
- Toggle the whole system with the `enableDrones` config (no progression unlock required).

## Delivery Drone

A lightweight non-living entity. It flies in a straight line from its hub to the target, deposits its
cargo into the destination inventory (dropping any overflow), then despawns. A failsafe age bounds its
lifetime so a stranded drone never lingers.

> **Note:** the delivery drone's detailed animated model is part of the deferred visual/art pass (see
> [MODELS.md](../../neroland-mc-ecosystem/nerologistics/MODELS.md) in the umbrella docs). Drone-port
> **Hyperspeed** transfers deliberately spawn **no** drone entity at all, for performance.

## See also

- [Drone Ports](Drone-Ports.md) · [Terminals](Terminals.md) · [Configuration](Configuration.md)
