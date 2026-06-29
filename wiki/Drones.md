# Drones

Drones carry items point-to-point without conduits or wireless links.

## Drone Hub

The hub stocks an item buffer (fill it with ducts/hoppers) and dispatches **delivery drones** to
**wireless cargo terminals on its channel** within range. Right-click to cycle the hub's channel.

- It holds a **hard-capped pool** of live drones (`dronesPerHub`); it never dispatches beyond the cap.
- Each dispatch charges the hub's energy buffer (`droneEnergyPerDelivery`); power it with cables.
- Targets come from the cheap wireless-channel membership list — no world scan.
- Gated behind `industrial_power`, and by the `enableDrones` master toggle.

## Delivery Drone

A lightweight non-living entity. It flies in a straight line from its hub to the target, deposits its
cargo into the destination inventory (dropping any overflow), then despawns. A failsafe age bounds its
lifetime so a stranded drone never lingers.

> **Note:** the drone currently has a **placeholder renderer** — it works but is invisible. A proper
> model and texture are a planned visual follow-up.

## See also

- [Terminals](Terminals.md) · [Configuration](Configuration.md)
