# Buffers

A **Buffer** holds a set amount of items on the network. Each buffer runs in one of two modes, toggled
per block.

## Modes

- **Passive cache** *(default)* — the buffer just holds whatever is in it as a throughput/overflow
  reservoir. Ducts move items in and out; the buffer itself does nothing. Good for smoothing bursty
  production or staging materials.
- **Keep-stocked leveling** — the buffer maintains a **target** item at a **target quantity**, pulling
  top-ups from the network's storage when it runs short and releasing the overflow back when it has too
  much. Good for guaranteeing a machine input never starves.

**Sneak-right-click** the block to toggle between the two modes (it tells you the new mode in chat).

## Setting the target (keep-stocked)

Right-click the buffer to open it. Click the **ghost target slot** (top-left) with a stack on your
cursor: the buffer remembers that **item**, and the stack's **count** becomes the level to hold (for
example, hold 32 iron and click to "keep 32 iron"). Your held item is not consumed. Click the ghost slot
with an empty hand to clear the target.

The buffer pulls and releases on an interval, drawing from the inventories on the attached item network —
so a keep-stocked buffer set to an [Auto-Crafter](Auto-Crafting.md)'s output will keep that item topped
up as long as the crafter (or your storage) can supply it.

Buffers need **no power** and are cheap to craft.

## Configuration

| Key | Default | Meaning |
| --- | --- | --- |
| `enableBuffers` | `true` | Master toggle for keep-stocked leveling (passive buffers always hold). |
| `bufferIntervalTicks` | `20` | Ticks between a keep-stocked buffer's top-up/overflow passes. |

## See also

- [Universal Duct & Native Storage](Universal-Duct-and-Storage.md) · [Auto-Crafting](Auto-Crafting.md) ·
  [Network Controller](Controller.md)
