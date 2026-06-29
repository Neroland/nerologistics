# Dashboard & Privacy

## Logistics Dashboard

Right-click the **Logistics Dashboard** block to read a summary in chat:

- the adjacent network's **node and endpoint counts**;
- this dimension's aggregate **throughput** (items, fluid mB, energy NE moved);
- **shipping** — launched, delivered, and in-transit counts;
- **drones dispatched**.

All of these are **aggregate world figures keyed by dimension** — never by player.

## Privacy (POPIA / GDPR)

NeroLogistics is built to store **no personal data by default**:

- Networks, terminals, ports, drones and dashboards are keyed by **block position / dimension**, never
  by player identity.
- **Per-player attribution is opt-in and off by default** (`perPlayerThroughputAttribution`). With it
  off, nothing about a player is stored.
- When turned on, it records only the placing player's **UUID** (never a name) against their cargo
  port's shipments. That record is **retention-pruned** daily (`attributionRetentionDays`) and is
  purged through Core's shared **data-erasure** hook — both on an explicit erase request and by Core's
  inactivity sweep. The opt-in flag doubles as the server toggle to disable personal-data logging.

This mirrors the ecosystem-wide pattern: any mod that stores player data routes erasure through Core
so a single request clears a player everywhere.

## See also

- [Configuration](Configuration.md) · [Cross-Dimension Shipping](Cross-Dimension-Shipping.md)
