# Logistics Programming

The **Logistics Processor** adds rule-based supply policies — *if stock is below/above X, then act* —
so keep-stocked, overflow-export and auto-shipping behaviour can be expressed without external
automation mods.

## The block

Place the processor against any **item duct** (or universal duct) and it joins that network, exactly
like the buffer and auto-crafter. Right-click opens the rule GUI. The processor draws NE from energy
cables and charges a small amount per executed action (`logisticsEnergyPerAction`, default 100).

## Rules

A processor holds up to **8 rules**. Each rule has:

- **Item** — a ghost slot; click with a carried stack to stamp it (exact item + components, nothing
  is consumed), click with an empty cursor to clear, or shift-click a stack from your inventory to
  stamp the first free rule.
- **Comparator** — `<` (below) or `>` (above) the threshold.
- **Threshold** — 1 to 1,000,000. The −/+ buttons step by 1; hold **Shift** for ×10, **Ctrl** for
  ×100, **Shift+Ctrl** for ×1000.
- **Action** — what to do when the comparison holds (cycles between the three below).
- **Enabled** toggle and a **status dot** (hover it for the reason: idle, acted, no network, no
  adjacent inventory, no rocket port, no fuel, no energy, blocked).

## Actions

- **Stock** (*keep adjacent stocked*): counts the item in the processor's **adjacent inventory**
  (its first non-duct neighbour — a chest, barrel, machine…). When the count is below the
  threshold, the deficit is pulled from the [network storage index](Storage-Network.md) into that
  inventory. Think of it as a keep-stocked [buffer](Buffers.md) that fills *someone else's*
  inventory from digital storage.
- **Export** (*export excess*): counts the item across the **whole network index**. When it is
  above the threshold, the excess is pushed from network storage into the adjacent inventory —
  feed a trash barrel, an outbound chest, a furnace bank.
- **Ship** (*ship excess*): counts the item across the network index. When it is above the
  threshold, the excess is extracted and loaded into the **nearest rocket cargo port on the same
  network**, which launches it along its own configured destination, channel and
  [shipping class](Cross-Dimension-Shipping.md). With no port or no buffered rocket fuel the rule
  simply idles with a status — it never errors.

Every rule moves at most `logisticsActionCapPerCycle` items (default 64) per evaluation, and
evaluation runs on a staggered interval (`logisticsRuleIntervalTicks`, default 40 ticks) — never
per-tick, so hundreds of processors stay cheap. Anything a full destination refuses is returned to
network storage; items are never voided.

## Configuration

- **`enableLogisticsProcessor`** (default `true`) — master toggle; off = the block stays placeable
  but idles and refuses to open its GUI.
- **`logisticsRuleIntervalTicks`** (default `40`) — ticks between rule-evaluation passes.
- **`logisticsActionCapPerCycle`** (default `64`) — max items one rule moves per pass.
- **`logisticsEnergyPerAction`** (default `100`) — NE charged per executed action (`0` = free).

## Privacy

Rules, thresholds and statuses are block-scoped only — the processor stores no player data
(POPIA/GDPR posture unchanged).

## See also

- [Storage Network](Storage-Network.md) · [Buffers](Buffers.md) ·
  [Cross-Dimension Shipping](Cross-Dimension-Shipping.md) · [Configuration](Configuration.md)
