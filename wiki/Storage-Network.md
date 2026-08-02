# Storage Network

The **storage network** turns a conduit network into digital bulk storage: portable **storage
cells** slotted into **drive bays**, aggregated together with your ordinary chests and warehouses
by a per-network **storage index**. It is deliberately *not* an AE2 clone — capacity is a plain
item/bucket count with **no byte or type math**, and there are no channels.

## Storage Cells

Cells are items; their contents live **on the cell itself**, so a cell pulled from one drive bay
and slotted into another carries its inventory with it.

| Cell                                  | Holds                                        |
|---------------------------------------|----------------------------------------------|
| 1k / 8k / 64k / 512k Item Cell        | 1 000 / 8 000 / 64 000 / 512 000 items total |
| 16B / 128B / 1024B / 8192B Fluid Cell | 16 / 128 / 1 024 / 8 192 buckets total       |

- **Count-based capacity.** A cell holds any mix of types up to its total — a 1k cell can hold
  1 000 of one item or 10 each of 100 different items. Item lines are kept exact (a renamed or
  enchanted variant is its own line).
- **Partition.** Sneak-use a held cell to open its configuration: nine ghost slots. Stamp items
  into them (nothing is consumed) and the cell only accepts those items. For **fluid cells**,
  stamp **buckets** — the partition matches the bucket's fluid. All slots empty = accepts
  everything. Item partition matching is exact (item + components).
- **Priority.** The same screen sets a signed priority (default 0, −9999…9999). Higher-priority
  cells fill first — partition an ore cell at priority 10 and your ores sort themselves before
  anything falls through to general storage.
- **Tooltip.** Every cell shows its fill, type count, priority and whether it is partitioned.
- Cells never stack, and crafting a higher tier uses three of the lower tier (contents are kept on
  the ingredient cells' stacks — empty them first to be safe).

## Drive Bay

The **Drive Bay** block holds **six cells** and joins the item and fluid networks of any adjacent
duct (universal ducts join it to both at once).

- **Right-click** opens the bay GUI; drop cells in or out freely.
- The drive bay is **not** a vanilla inventory — ducts and hoppers can never pull the cell items
  out of it, because cells are digital media, not cargo. The network sees only the cells'
  *contents*.
- Insertion honours cell settings network-wide: the highest-priority accepting cell fills first;
  partitioned cells that match take the resource before unpartitioned ones.
- **Comparator** output reads the average cell fill (0–15).

## The storage index

Every conduit network keeps a lazily-built **storage index** — the aggregated view of everything
storable on it:

1. **Drive bays** (native digital storage, priority-ordered),
2. **read-through inventories** — [Item Storage](Universal-Duct-and-Storage.md) warehouses,
   chests, barrels and any other vanilla-compatible container on the network,
3. **fluid storages** exposed through Neroland Core's fluid surface.

Items inserted into the network go to the best cell first and spill over into read-through
inventories; extraction drains cells first. The index never scans per tick — it refreshes only
when something actually changed (drives report changes precisely; plain chests are re-read on a
short cooldown, default 20 ticks) and is hard-capped so megabases stay smooth. The **Storage
Terminal** below is the player-facing window onto this index.

## Storage Terminal

Place the **Storage Terminal** against any duct and right-click it to browse everything the
index aggregates — cells, chests, warehouses — in one scrollable 9×4 grid.

- **Counts** are shown abbreviated (`1.2k`, `3.4M`); hover an entry for the exact total and the
  owning mod.
- **Search** (top right) filters by item name or mod id; the **Sort** button cycles
  count ↓ / name / mod. Both are instant and purely client-side.
- **Take things out:** click an entry with an empty cursor to pull up to a stack onto the
  cursor; **right-click** for half a stack; **shift-click** to send a stack straight to your
  inventory.
- **Put things in:** click the grid with a carried stack to push it into the network
  (right-click pushes a single item); **shift-click** any stack in your inventory to push it in
  too. Insertion follows the usual routing — best cell first, then chests.
- **Fluids tab** (appears when a fluid network is reachable, e.g. through a universal duct):
  lists every stored fluid with its mB total. Click a fluid with an **empty bucket** on the
  cursor to fill it (exactly 1 000 mB); click with a **full bucket** to drain it into the
  network. Fluids with no bucket item are display-only.
- Contents refresh live while the screen is open (default at most every 10 ticks, and only when
  something actually changed — an idle terminal costs nothing).

The terminal itself is passive: no power, no buffer. Cutting the duct while the screen is open
simply drops it to "no network".

## Wireless Terminal

The **Wireless Terminal** item is the same screen, portable:

1. **Sneak-use it on a Network Controller** to bind it (the binding survives logout and shows in
   the tooltip).
2. **Use it anywhere** within `wirelessTerminalRange` blocks of that controller (default 64,
   same dimension; `-1` removes the limit) to open the terminal against the networks on the
   controller's conduits.

If the controller is gone, out of range, in another dimension or has no network, the terminal
tells you instead of opening a dead screen — and an open session re-checks the whole chain, so
walking out of range closes it just like walking away from a chest. Crafted from a Storage
Terminal, a Hyperspeed Card and an ender pearl.

## Configuration

- **`enableStorageNetwork`** (default `true`) — master toggle; off makes drive bays inert and the
  index empty.
- **`itemCellCapacity1k/8k/64k/512k`** — total items per item-cell tier.
- **`fluidCellCapacity16b/128b/1024b/8192b`** — total buckets per fluid-cell tier.
- **`storageIndexRefreshTicks`** (default `20`) — minimum ticks between read-through container
  re-reads.
- **`enableStorageTerminal`** (default `true`) — master toggle for the storage/wireless terminal
  GUIs; off leaves the blocks/items in place but they refuse to open.
- **`terminalResyncTicks`** (default `10`) — minimum ticks between content re-syncs to an open
  terminal (only sent when contents changed).
- **`wirelessTerminalRange`** (default `64`) — wireless terminal working radius from its bound
  controller, same dimension; `-1` = unlimited.

Drive bays always have exactly six slots. See [Configuration](Configuration.md) for every key.

## See also

- [Universal Duct & Native Storage](Universal-Duct-and-Storage.md) ·
  [Terminals](Terminals.md) · [Conduits](Conduits.md)
