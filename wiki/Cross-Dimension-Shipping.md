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

Manifests are **durably persisted** in world saved-data (payload, source/destination dimension and
position, departure/arrival ticks — never any player identity), so a shipment in flight **survives a
server restart** and its transit timer resumes where it left off. The `maxPendingShipments` cap bounds
the store.

## Destinations (the route seam)

Destinations come from a **`RouteProvider`**. The built-in stub treats every loaded dimension as a
destination, so shipping works standalone (e.g. Overworld ↔ Nether). When **Nerospace** is installed,
the compat hook binds its stable `nerospace.api` route catalog (reflectively — NeroLogistics never
imports Nerospace, so this stays inert, not broken, without it) and swaps it in via
`RouteProviders.set(...)`. With Nerospace active:

- **Destinations** become the planet/station catalog: Home (Overworld), the orbital station,
  Greenxertz, Cindara and Glacira — and a port only launches between catalog endpoints on an **open**
  route (never to its own dimension).
- **Transit time** comes from the route (1 200 ticks per dimension-separation step) instead of the
  flat `shipTransitTicks` config.
- **Fuel per launch** is priced per route from Nerospace's fuel cost (1 000 mB ≈ 1 rocket-fuel-tagged
  item, rounded up, never below `shipFuelPerLaunch`); setting `shipFuelPerLaunch` to `0` keeps
  launches fuel-free either way. Fuel is still matched **by tag** (`nerologistics:rocket_fuel`).

## Known limitations

- Per-lane throughput/priority and recurring schedules beyond the interval are planned.

## See also

- [Terminals](Terminals.md) · [Configuration](Configuration.md)
