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

## Look

Every conduit (the Universal Duct included) renders as a slim tinted **glass tube** with a glowing, animated
core line and thin dark edge lines, in the style of Nerospace's Universal Pipe. Connected conduits
form one seamless pipe: the edge lines run along its length and only close off where the pipe turns or
ends, with no joints or inner walls. The colour tells the medium apart: **amber** item duct, **blue** fluid duct, **red** energy
cable, **teal** universal duct. An arm reaches out only where the conduit actually connects: to a
conduit sharing its medium, or to something it can move that medium to or from (an inventory, a drive
bay, a tank, an energy storage, a network controller). A face set to **Disabled** with the
Configurator hides its arm.

The look is cosmetic only. Networks, endpoints and throughput are exactly as before, and the hitbox
follows the tube, so you can walk beside a line instead of on it.

The front textures on logistics devices face the player when placed. Existing devices keep the
orientation they had before the update.

## Networks

Conduits of the **same medium** that touch each other form a single **network**. Networks are tracked
per dimension and rebuilt **incrementally**: placing a conduit merges the adjacent same-medium
networks, breaking one splits the survivors back into separate networks. The world is never re-scanned
to find connections.

Networks work out of the box — no progression unlock or configuration required, whether you play
NeroLogistics standalone or alongside the other Nero mods.

## Per-face modes

Right-click with an empty hand or sneak-use a NeroLogistics, Nerospace or NeroTech Configurator to
open the face panel. Right-click a face while holding a Configurator to cycle it directly.

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

Item and Universal Ducts have an item whitelist. Fluid and Universal Ducts have one fluid sample
slot per face. Use a filled bucket as the sample, then click the slot with an empty cursor to
clear it. A face without a fluid sample passes all fluids.

## Throughput & limits

Each network moves up to a configurable budget **per network per tick** (separate budgets for items,
fluids and energy), and transport runs at most **once per network per tick**. A network that would
grow past `maxNodesPerNetwork` refuses to connect the new conduit (it stays isolated) rather than
costing tick time. See [Configuration](Configuration.md).

Right-click an Energy Cable with an empty hand to see the network's recent NE/t. Sneak-use a
Configurator on a powered logistics device to see stored energy and actual energy spent over
the last 20 ticks. Some devices only draw power when they complete a job.

## What it connects to

Item ducts move items via the vanilla `Container` / `WorldlyContainer` contract, which covers vanilla
blocks (chests, barrels, furnaces) and every Nero machine. Energy uses Core's energy lookup; fluids
use Core's fluid lookup. A duct or cable can also move its supported resources to or from an adjacent Nerospace Universal
Pipe. To send from a pipe to NeroLogistics, set the pipe side to **Out** and the duct face to **In**.
Reverse the two modes to send the other way. The pipe buffer hands off the resource between
networks.

## See also

- [Universal Duct & Native Storage](Universal-Duct-and-Storage.md) · [Network Controller](Controller.md)
  · [Terminals](Terminals.md) · [Dashboard & Privacy](Dashboard-and-Privacy.md) ·
  [Configuration](Configuration.md)
