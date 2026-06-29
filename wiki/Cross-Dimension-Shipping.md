# Cross-Dimension Shipping

The **Rocket Cargo Port** ships cargo between dimensions.

## How it works

A port holds a cargo buffer and an energy buffer, and it consumes **rocket fuel by tag**
(`nerologistics:rocket_fuel` — blaze powder/rods by default; any mod, including Nerospace, can add to
the tag). On an interval it launches the non-fuel cargo in its buffer to a **same-channel port in the
selected destination dimension**:

- Right-click cycles the **destination**; sneak-right-click cycles the **channel**.
- A launch is charged energy per stack plus fuel per launch.
- Shipping is deliberately **slow and energy-hungry** so it complements crewed rocket travel rather
  than replacing it.

## Transit & arrival

A launched shipment becomes a **manifest** held in transit for a configurable number of ticks. On
arrival the destination chunk is force-loaded **only momentarily** to deposit the cargo into the
destination port (overflow is dropped), then released — so **two dimensions are never kept loaded** for
the transit duration.

## Destinations (the route seam)

Destinations come from a **`RouteProvider`**. The built-in stub treats every loaded dimension as a
destination, so shipping works standalone (e.g. Overworld ↔ Nether). When Nerospace is present, a
compat hook swaps in a planet/station-backed provider via `RouteProviders.set(...)` — NeroLogistics
never imports Nerospace, so this stays inert (not broken) without it.

## Known limitations

- The in-transit queue is **not yet persisted** — a shipment in flight is lost on a server restart
  (durable saved-data is a planned follow-up).
- Per-lane throughput/priority and recurring schedules beyond the interval are planned.

## See also

- [Terminals](Terminals.md) · [Configuration](Configuration.md)
