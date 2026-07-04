# Drone Ports

**Drone Ports** move items point-to-point and **between separate networks** using drones. They are
standalone and **RF-powered** — they work with no network at all — and they are also the sanctioned way
to bridge two [controller](Controller.md) networks.

## How it works

1. Craft and place two ports. **Sneak-right-click** a port to toggle it between **export** and **import**
   (it tells you the mode in chat).
2. Craft **Drones** and place them in the port's **drone slots** (right-click to open the GUI). Each
   installed drone is a **lane** — the more drones, the more the port carries (throughput = drones ×
   per-drone capacity per window).
3. Feed items into an **export** port's cargo buffer (hopper, duct, or by hand). It dispatches drones to
   an **import** port; the drone flies there and drops the cargo into the import port's buffer, which you
   then pull out with a duct/hopper.

## Routing — name or Auto

- **Auto** *(default)*: an unnamed export port ships to the **nearest import port** in range.
- **Named**: rename the Drone Port item in an **anvil** before placing it (for example "Base A"). A named
  export port ships only to import ports with the **same name** — so naming pairs specific ports.

## Hyperspeed upgrade

Put a **Hyperspeed Card** in an upgrade slot and that port's transfers become **near-instant** — and,
crucially, **no drone entity is spawned or rendered**. This is the performance path: a base full of
hyperspeed ports moves goods with zero drone-render cost. Without the card, drones are visible entities
flying at a watchable speed, capped per port by the number of installed drones.

## Bridging networks

Because a port's cargo buffer is a standard inventory, a duct on network A pushes items into an export
port and a duct on network B pulls them from the matching import port. This is how you run **multiple
separate controllers** and still move goods between them.

## Configuration

| Key | Default | Meaning |
| --- | --- | --- |
| `enableDrones` | `true` | Master toggle for drones (shared with the legacy drone hub). |
| `maxDronesPerPort` | `8` | Hard cap on drones (lanes) a port counts. |
| `dronePortRange` | `256` | Max distance an export port ships to an import port. |
| `dronePerDroneCapacity` | `16` | Items each drone carries per dispatch. |
| `dronePortEnergyPerStack` | `256` | NE charged per dispatch. |

## Roadmap (Stage 11 follow-ups)

- **In-GUI** name and destination editing, and an explicit destination picker (currently: anvil-name +
  Auto/nearest).
- **Fluid / liquid / gas / energy** upgrade cards so ports ship those media too.
- Core **SPEED / RANGE / CAPACITY / EFFICIENCY** upgrade tuning.
- The terminal's **Drone Ports tab** (lands with the tabbed terminal) for central naming and monitoring.

## See also

- [Network Controller](Controller.md) · [Universal Duct & Native Storage](Universal-Duct-and-Storage.md)
  · [Drones](Drones.md) (the legacy hub)
