# Terminals

Three blocks extend logistics beyond a single conduit line. All three hold an internal buffer that
hoppers, ducts, Create and other mods can fill and drain (they expose the standard item capability on
Fabric and NeoForge).

## Wireless Cargo Terminal

A buffered **virtual endpoint** keyed to a **channel** (0–15). Terminals on the same channel within
range form a wireless link with no physical conduit: items pushed into one terminal's buffer are moved
to another in-range terminal's buffer, on an interval.

- Right-click to cycle the channel.
- Each transfer charges the **sending** terminal's energy buffer (power it with cables); if it has no
  energy, it does not send.
- Range, per-item energy cost, and the transfer interval are configurable.
- Gated behind `industrial_power`.

Use a pair to bridge two duct networks across a base without running cable between them.

## Storage Request Terminal

A GUI block that **aggregates the item network it is attached to**. On an interval it restocks its
output buffer with items pulled from the inventories on the adjacent item duct's network, matching a
**request filter** (default: everything). Open it and take what you need; it restocks from the network.

- When AE2 is present, its interface is just another inventory on the network and is read through the
  same path — no AE2 dependency, no duplication.
- Gated behind `industrial_power`.

## Train Cargo Interface

A passive buffered inventory that bridges a NeroLogistics item network to **Create** train load/unload.
NeroLogistics ducts treat it as an ordinary inventory endpoint; Create reaches the same buffer through
the standard item capability — so trains carry network cargo with **no hard Create dependency**. Best
for same-dimension long-haul before you unlock rockets.

## See also

- [Conduits](Conduits.md) · [Cross-Dimension Shipping](Cross-Dimension-Shipping.md) ·
  [Configuration](Configuration.md)
