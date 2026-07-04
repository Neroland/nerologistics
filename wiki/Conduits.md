# Conduits

> **Redesign note:** the **[Universal Duct](Universal-Duct-and-Storage.md)** is now the default conduit —
> one cheap duct that carries **items and fluids** on a single line. The separate item/fluid ducts below
> are kept for migration and still interoperate (a universal duct merges with either). **Energy** still
> travels on its own **Energy Cable**. For the managed network on top of conduits, see the
> **[Network Controller](Controller.md)**.

Conduits are the local transport backbone. The legacy line-up is three block types, one per medium.

- **Item Duct** — moves items between adjacent inventories. *(Superseded by the Universal Duct.)*
- **Fluid Duct** — moves fluids between adjacent Nero fluid tanks/machines. *(Superseded by the Universal
  Duct.)*
- **Energy Cable** — moves Nero energy (NE) between adjacent machines and batteries. *(Still the way to
  move energy — the universal duct does not carry power.)*

All three are crafted cheaply (a row of iron around glass / copper / redstone, six at a time) and
mined with a pickaxe.

## Networks

Conduits of the **same medium** that touch each other form a single **network**. Networks are tracked
per dimension and rebuilt **incrementally**: placing a conduit merges the adjacent same-medium
networks, breaking one splits the survivors back into separate networks. The world is never re-scanned
to find connections.

Networks work out of the box — no progression unlock or configuration required, whether you play
NeroLogistics standalone or alongside the other Nero mods.

## Per-face modes

Every conduit face carries a Core **side-config** mode for its medium's channel, exactly like a
machine face:

- **Input** — pull the resource *from* the neighbour into the network.
- **Output** — push the resource *to* the neighbour.
- **IO** — both (the default on a freshly placed conduit, so a line balances adjacent inventories out
  of the box).
- **Disabled** — ignore that face.

Conduit-to-conduit connectivity is independent of these modes — the modes only govern how a face
interacts with the **external** (non-conduit) block on it.

## Filters

Item and fluid ducts carry a **per-face filter**:

- **Item filter** — match by exact item, item tag (e.g. `c:ingots`), mod id, or full
  data-component (NBT) match. Whitelist or blacklist.
- **Fluid filter** — match by fluid, fluid tag, or mod id. Whitelist or blacklist.

A face with no filter passes everything; a whitelist with no rules passes nothing.

## Throughput & limits

Each network moves up to a configurable budget **per network per tick** (separate budgets for items,
fluids and energy), and transport runs at most **once per network per tick**. A network that would
grow past `maxNodesPerNetwork` refuses to connect the new conduit (it stays isolated) rather than
costing tick time. See [Configuration](Configuration.md).

## What it connects to

Item ducts move items via the vanilla `Container` / `WorldlyContainer` contract, which covers vanilla
blocks (chests, barrels, furnaces) and every Nero machine. Energy uses Core's energy lookup; fluids
use Core's fluid lookup. Loader-native transfer-API and AE2 discovery are a planned enhancement.

## See also

- [Universal Duct & Native Storage](Universal-Duct-and-Storage.md) · [Network Controller](Controller.md)
  · [Terminals](Terminals.md) · [Dashboard & Privacy](Dashboard-and-Privacy.md) ·
  [Configuration](Configuration.md)
