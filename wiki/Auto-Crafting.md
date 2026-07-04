# Auto-Crafting

The **Auto-Crafter** crafts items on its own by pulling ingredients from the network it is attached to.
Each Auto-Crafter holds one **pattern** (a recipe) and keeps producing it from network stock.

## How it works

1. Place an Auto-Crafter touching a [duct](Universal-Duct-and-Storage.md) line and power it with an
   [Energy Cable](Conduits.md).
2. **Right-click** to open it. The left 3×3 grid is the **pattern inputs**; the slot to the right is the
   **pattern output**.
3. **Set the pattern** by clicking a ghost slot with an item on your cursor — it stamps a count-1 copy
   (your item is **not** consumed). Click a ghost slot with an empty cursor to clear it. Fill the inputs
   with the ingredients and the output slot with what they make.
4. On each interval, if the ingredients are present in the network's storage and the crafter is powered,
   it consumes one of each input and places the output in its **output buffer** (the bottom row), which
   ducts and hoppers then distribute.

Because the output is stored in the pattern, the Auto-Crafter can produce **any** recipe — vanilla or
modded — without a recipe lookup.

## Throughput

The crafter runs `autoCraftsPerInterval` crafts per pass, **multiplied by the managing
[Network Controller](Controller.md)'s capacity** — so adding network modules speeds up crafting as well
as transport. Each craft costs energy (`autoCraftEnergyPerCraft`) and the pass is config-capped.

## Configuration

| Key | Default | Meaning |
| --- | --- | --- |
| `enableAutoCrafting` | `true` | Master toggle for the auto-crafter. |
| `craftIntervalTicks` | `20` | Ticks between crafting passes. |
| `autoCraftEnergyPerCraft` | `200` | NE charged per item crafted. |
| `autoCraftsPerInterval` | `4` | Base crafts per pass (scaled by the controller's capacity). |

## Roadmap (Stage 9 follow-ups)

- **Tabbed terminal** — a single Items / Fluids / Gas / **Crafting** / Drone Ports terminal that lets you
  *request* a missing item and have the network auto-craft it on demand (the Crafting and Drone Ports
  tabs land with their backing systems — native fluid/gas storage and the redesigned drone ports).
- **Recursive planning** — crafting intermediate ingredients automatically (multi-step recipe trees).
- **Pattern items + multiple hosts**, and **delegation** to AE2 / Nerotech auto-crafters when present.

## See also

- [Network Controller](Controller.md) · [Universal Duct & Native Storage](Universal-Duct-and-Storage.md)
  · [Terminals](Terminals.md)
