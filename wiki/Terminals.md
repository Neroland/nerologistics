# Terminals

> **Redesign note:** the Storage Request Terminal now also recognises the
> **[Universal Duct](Universal-Duct-and-Storage.md)** (not just the legacy item duct), so it aggregates
> native **[Item Storage](Universal-Duct-and-Storage.md)** alongside any other inventories on the
> network. **[Auto-Crafting](Auto-Crafting.md)** adds request-and-build on top of storage. A single
> tabbed terminal (Items / Fluids / Gas / Crafting / Drone Ports) is the planned successor — see the
> redesign docs.

Three blocks extend logistics beyond a single conduit line. All three hold an internal buffer that
hoppers, ducts, Create and other mods can fill and drain (they expose the standard item capability on
all three loaders — Fabric, NeoForge and Forge — and accept cable power the same way).

## Wireless Cargo Terminal

A buffered **virtual endpoint** keyed to a **channel** (0–15). Terminals on the same channel within
range form a wireless link with no physical conduit: items pushed into one terminal's buffer are moved
to another in-range terminal's buffer, on an interval.

- Right-click to cycle the channel.
- Each transfer charges the **sending** terminal's energy buffer (power it with cables); if it has no
  energy, it does not send.
- Range, per-item energy cost, and the transfer interval are configurable.
- Works out of the box — no progression unlock required.

Use a pair to bridge two duct networks across a base without running cable between them.

## Storage Request Terminal

A GUI block that **aggregates the item network it is attached to**. On an interval it restocks its
output buffer with items pulled from the inventories on the adjacent item duct's network, matching a
**request filter** (default: everything). Open it and take what you need; it restocks from the network.

- When AE2 is present, its interface is just another inventory on the network and is read through the
  same path — no AE2 dependency, no duplication.
- Works out of the box — no progression unlock required.

## Train Cargo Interface

A passive buffered inventory that bridges a NeroLogistics item network to **Create** train load/unload.
NeroLogistics ducts treat it as an ordinary inventory endpoint; Create reaches the same buffer through
the standard item capability — so trains carry network cargo with **no hard Create dependency**. For
native same-dimension bulk hauling with no other mods, see the **[Train Station](Trains.md)**; this
interface is the optional Create bridge.

## See also

- [Conduits](Conduits.md) · [Universal Duct & Native Storage](Universal-Duct-and-Storage.md) ·
  [Auto-Crafting](Auto-Crafting.md) · [Trains](Trains.md) ·
  [Cross-Dimension Shipping](Cross-Dimension-Shipping.md) · [Configuration](Configuration.md)
