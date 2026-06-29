# Configuration

NeroLogistics config is managed by Neroland Core's config manager (schema id `nerologistics`). Every
value below is **server-authoritative** — the server's values win and sync to clients.

## Local transport (conduits)

- **`itemTransferPerTick`** (default `64`) — max items moved per item network per tick.
- **`fluidTransferPerTick`** (default `1000`) — max mB moved per fluid network per tick.
- **`energyTransferPerTick`** (default `2560`) — max NE moved per energy network per tick.
- **`maxNodesPerNetwork`** (default `2000`) — max conduits in one network; a conduit past the cap stays
  isolated rather than costing tick time.

## Wireless

- **`enableWireless`** (default `true`) — master toggle for wireless cargo terminals.
- **`wirelessRange`** (default `64`) — max block distance between two terminals on a channel.
- **`wirelessEnergyPerItem`** (default `16`) — NE charged per item moved over a channel.
- **`wirelessIntervalTicks`** (default `10`) — ticks between wireless transport passes (batching window).

## Drones

- **`enableDrones`** (default `true`) — master toggle for drone hubs and delivery drones.
- **`droneRange`** (default `48`) — max block distance a drone travels from its hub.
- **`dronesPerHub`** (default `4`) — hard cap on live drones per hub.
- **`droneEnergyPerDelivery`** (default `512`) — NE charged per delivery dispatched.

## Cross-dimension shipping

- **`enableCrossDimension`** (default `true`) — master toggle for rocket cargo ports.
- **`shipTransitTicks`** (default `1200`) — transit time for a shipment.
- **`shipEnergyPerStack`** (default `10000`) — NE charged per stack shipped.
- **`shipFuelPerLaunch`** (default `1`) — rocket-fuel-tagged items consumed per launch.
- **`shipIntervalTicks`** (default `100`) — ticks between a port's auto-ship attempts.
- **`maxPendingShipments`** (default `1024`) — hard cap on in-transit shipments; ports stop launching at
  the cap.

## Dashboards and privacy (POPIA/GDPR)

- **`perPlayerThroughputAttribution`** (default `false`) — opt-in: attribute cargo-port shipments to the
  placing player (UUID only). Off means no player data is stored.
- **`attributionRetentionDays`** (default `30`) — days to retain per-player attribution before
  auto-prune (`0` = keep until erased).

See [Dashboard & Privacy](Dashboard-and-Privacy.md) for the data posture.

## See also

- [Conduits](Conduits.md) · [Terminals](Terminals.md) · [Drones](Drones.md) ·
  [Cross-Dimension Shipping](Cross-Dimension-Shipping.md)
