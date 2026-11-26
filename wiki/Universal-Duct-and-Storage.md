# Universal Duct & Native Storage

Stage 8 introduces the **Universal Duct** — one duct that carries **items and fluids** on a single
network — and **native storage** that the network indexes.

## Universal Duct

The universal duct is the new default conduit. A single line carries **both items and fluids**
(energy stays on [Energy Cables](Conduits.md)); each resource is routed to the matching neighbour —
items to inventories, fluids to fluid storages. The crafting recipe makes eight ducts. Use copper in the corners, glass in the top and bottom
middle slots, iron on either side of the centre, and redstone in the centre.

- **One line, two media.** Internally the duct joins both the item and fluid networks at the same
  positions, so you no longer run parallel item and fluid lines.
- **Faces.** Right-click with an empty hand or sneak-use a Configurator to open all six face controls.
  Click any face to cycle Off, In, Out and Both. The mode applies to both items and fluids.
- **Item filter.** Click a filter slot with a sample item, or shift-click a sample in your inventory.
  An empty item filter lets all items through.
- **Fluid filter.** Click a face's slot with a filled bucket to allow that fluid through the face.
  Click with an empty cursor to clear the rule. The filter keeps a sample and returns the bucket
  and its contents to you.
- **Interop.** A universal duct placed against a legacy **Item Duct** merges with its item network, and
  against a **Fluid Duct** merges with its fluid network — so old and new lines mix freely.

## Item Storage

The **Item Storage** block is a native network warehouse: a 54-slot store that the network reads and
writes.

- **Right-click** to open its double-chest GUI.
- **On the network.** Universal ducts route items into and out of it, and the
  [Storage Request Terminal](Terminals.md) aggregates its contents into the searchable network index —
  no extra setup, because NeroLogistics discovers inventories through the vanilla container contract.
- **Interop.** Hoppers, and the standard item capability (on all three loaders — Fabric, NeoForge and
  Forge), expose it to other mods; when
  **AE2** is present its interface is just another inventory on the network, read the same way — no hard
  AE2 dependency, no duplication.

## Roadmap (Stage 8 follow-ups)

These are designed and scheduled but not in this build:

- **Gas** as a third universal-duct medium (pending a confirmed gas capability / Mekanism interop).
- **Native fluid tanks and gas containers** as first-class storage endpoints.
- **Typed storage cells** (item / fluid / gas) with **capacity tiers** and **partition / priority**,
  held in storage drive/host blocks.
- A **controller-unified network index** (the controller aggregating all storage into one index) and
  deeper **AE2** crafting delegation.

## See also

- [Network Controller](Controller.md) — manages the network and scales its throughput.
- [Conduits](Conduits.md) — legacy item/fluid ducts and energy cables.
- [Terminals](Terminals.md) — the storage request terminal that indexes native storage.
