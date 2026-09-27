# Nerospace route integration

How NeroLogistics binds Nerospace's cargo route API (`za.co.neroland.nerospace.api.route`, Nerospace
1.3.0+). Player-facing behaviour is in [the shipping wiki page](../wiki/Cross-Dimension-Shipping.md).
The Nerospace side is documented in Nerospace's `docs/CARGO-ROCKETS.md` and
`docs/NEROLOGISTICS-HANDOVER.md`.

## Binding

- **Build:** `compileOnly "za.co.neroland.nerospace:nerospace-<loader>-<mc>:${nerospace_version}"` on
  every Stonecutter node, resolved from `mavenLocal()` or Nerospace's GitHub Packages feed (restricted
  to the `za.co.neroland.nerospace` group). `-PwithNerospace` adds the jar to dev runs. Until the 1.3.0
  tag publishes, run `./gradlew publishToMavenLocal` in `../nerospace` first.
- **Manifests:** Nerospace stays optional, floored at the compiled version — `[1.3.0,2.0)` on NeoForge
  and Forge, `suggests ">=1.3.0 <2.0.0"` on Fabric.
- **Class loading:** only `common/.../compat/nerospace/` imports Nerospace. `compat/NerospaceCompat`
  checks `Services.PLATFORM.isModLoaded("nerospace")` and only then calls
  `NerospaceRouteProvider.install()` (a static call, so nothing Nerospace-typed resolves earlier). A
  Nerospace without the route API surfaces as a `LinkageError`, logged once, and the stub stays.
- **Selection:** `RouteProviders.get()` returns the Nerospace provider only while `nerospaceRouting` is
  `true`; otherwise the stub. The Nerospace provider keeps draining its events either way.

## Mapping, as implemented

| NeroLogistics seam | Nerospace call |
| --- | --- |
| `RouteProvider.destinations` | `RouteApi.padsVisibleTo(server, dispatcher)`, minus the origin pad, stations sorted last; no dispatcher → public pads only |
| `RouteProvider.resolve` (saved `pad:<id>`) | `RouteApi.pad(server, id)` filtered by `accessibleBy(dispatcher)` (or `isPublic()` with no dispatcher) |
| `RouteProvider.originProblem` / origin pad | `padsVisibleTo(dispatcher)` filtered to a non-station pad in the port's dimension touching the port (26-neighbourhood) that the dispatcher owns or may use; the id is cached per port and re-checked with `RouteApi.pad` before use |
| `RouteProvider.launch` | `RouteApi.quote(...)` (empty → `ROUTE_CLOSED`), then `RouteApi.requestFlight(server, dispatcher, new FlightRequest(origin, dest, items, returnEmpty))`; items leave the port only on `accepted()` |
| `ShipDenial` | `FlightRequestResult.Denial` by name; unknown future constants → `OTHER` (a unit test fails the build if the mirror falls behind) |
| Departure / arrival / hold / drop | one `RouteEvents.Listener`, queued (bounded) and drained on NeroLogistics' server tick; only flights in `RocketFlightsState` count |
| In-transit list | `RouteApi.flight(server, id)` for each tracked flight launched within 128 blocks |
| Restart safety | `RocketFlightsState` (flight id, origin port, destination pad id, ticks) plus a reconciliation against `RouteApi.flight` every 200 ticks |
| `ShipmentState` | stub rocket cargo and train hauls only |

Travel time and fuel are Nerospace's. The port pays energy only; it never burns
`nerologistics:rocket_fuel` items on this path (`RouteProvider.chargesOwnFuel() == false`).

## Who the port ships as (POPIA / GDPR)

Every `RouteApi` call needs a player UUID. NeroLogistics stores a separate **dispatcher** UUID on the
port. This is separate from the opt-in analytics `owner`:

- set when a player picks a Nerospace destination on the port (the clicking player replaces any previous
  dispatcher);
- stored regardless of `perPlayerThroughputAttribution`, because it is needed for the function to work
  (lawful basis: performing the shipping that player set up); UUID only;
- never logged, never sent to telemetry, never shown except as "you" / "someone else" / "nobody";
- kept for the life of the block (breaking the port deletes it);
- erased by Core's `PlayerDataErasure`: loaded ports at once, unloaded ports through the
  `ErasedOwnersState` tombstones on their next load. An erased dispatcher leaves the port listing
  public pads only and unable to launch.

The alternative was an in-memory, session-only requester with public pads only. It was rejected because
it would stop every port after a restart until someone clicked it, and would rule out private pads.

Pad **ids** are cached, never another player's pad positions. The crate-recovery hint shows a pad's
position only to a viewer who may ship to that pad anyway.

## For pack authors

- Rocket cargo needs a Cargo Pad (inside a 3×3 launch pad) with a docked, fuelled Cargo Rocket, and a
  Rocket Cargo Port touching that Cargo Pad block.
- `nerospaceRouting = false` keeps the standalone stub routes with Nerospace installed.
- `shipReturnEmpty` sets new ports' return-empty default; `shipDenialBackoffMaxTicks` caps the retry wait
  after a refusal.
- The shipping lanes trade energy and launch priority under Nerospace routing (`expressFuelFactor` /
  `bulkFuelFactor` scale energy there); the transit factors do not apply.

## Asks for Nerospace

- `FlightHandle` has no "held since" tick. NeroLogistics stamps the hold time itself when it learns of
  the hold (the event, or the 200-tick reconciliation for a flight that started holding while
  NeroLogistics was not listening), so the hold time shown can run short by up to 10 seconds.
- There is no pad-level "can this requester launch from here" query. A dispatcher can see a public pad
  they may not launch from; NeroLogistics finds out only from a `NOT_PERMITTED` denial.
