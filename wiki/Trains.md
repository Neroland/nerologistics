# Trains

The **Train Station** is the native, **cheap, early-game bulk hauler** — it moves large batches of items
between stations over long distances, with no other mods required and **no power**.

## How it works

1. Craft **Train Stations** (cheap: rails + chest + iron) and place them where you want to send and
   receive bulk cargo.
2. **Sneak-right-click** a station to toggle it between **load (send)** and **unload (receive)**.
3. Put stations on the same **line** by renaming the station item in an **anvil** before placing it (for
   example "Ore Line"). A **load** station hauls to **unload** stations with the same line name; with no
   name it ships to the nearest unload station (Auto).
4. Fill a load station's 54-slot bulk buffer (hopper, duct, or by hand). On its interval it packs the
   buffer and hauls it to the destination station; after a **travel time** (longer for longer distances)
   the cargo arrives in the unload station's buffer, where a duct/hopper drains it.

Right-click a station to open its bulk chest GUI.

## When to use trains

Trains are **slow but big and free** — they trade latency for cheap, high-volume overland transport.
Use ducts for instant local moves, drones for point-to-point and cross-network hops, and trains for
hauling lots of material across a base or between bases before you have rockets.

## Create trains

If you run **Create**, the [Train Cargo Interface](Terminals.md) still lets a NeroLogistics network load
and unload Create trains — native stations are the default, Create trains are a bonus.

## Configuration

| Key | Default | Meaning |
| --- | --- | --- |
| `enableTrains` | `true` | Master toggle for native train stations. |
| `trainStationIntervalTicks` | `100` | Ticks between a load station's haul attempts. |
| `trainTicksPerBlock` | `2` | Transit ticks added per block of distance (more = more travel feel). |
| `trainMinTransitTicks` | `40` | Minimum transit time for a haul. |
| `trainMaxRange` | `2048` | Max distance a load station ships to an unload station. |
| `trainBulkPerTrip` | `27` | Max buffer slots hauled per trip. |

## Roadmap (Stage 12 visual follow-up)

The train **gameplay** is in; these visuals are a dedicated art pass:

- **Physical rail blocks** to lay between stations, and a **visible train entity** riding them (currently
  the haul is an abstracted travel timer — cargo arrives after the transit time).
- The **sophisticated animated 3D models** from `MODELS.md` for every NeroLogistics block (connection-
  aware ducts, rotating controller core, drone-port bays + flying drones, station loading arms, etc.),
  with custom block-entity renderers and an animation-quality config.

> Note: like cross-dimension shipping, an in-flight haul is not yet persisted across a server restart
> (durable saved-data is a shared follow-up).

## See also

- [Universal Duct & Native Storage](Universal-Duct-and-Storage.md) · [Drone Ports](Drone-Ports.md) ·
  [Cross-Dimension Shipping](Cross-Dimension-Shipping.md)
