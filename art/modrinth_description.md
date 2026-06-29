# NeroLogistics

**Move it anywhere — ducts, wireless links, delivery drones and inter-dimensional rockets for the Neroland universe.**

NeroLogistics is the **transport & automation** mod of the Neroland ecosystem — the brain that moves items, fluids, energy and cargo around your base, across it without wires, by drone, and between dimensions. Wire up duct networks, link bases on a wireless channel, stock terminals from across a network, and ship cargo to the Nether (or another planet) by rocket.

Built on **Neroland Core**, so its energy, side-config, progression gates and data handling are shared with the rest of the lineup. *(Early alpha — actively developed.)*

---

## What you can build

1. **Conduits.** **Item Ducts**, **Fluid Ducts** and **Energy Cables** form per-dimension networks that rebuild incrementally as you place and break them. Each face speaks Core's **side-config** vocabulary (input / output / IO / disabled), and ducts carry **per-face filters** — by item, tag, mod, or full data-component (NBT).
2. **Wireless cargo.** **Wireless Cargo Terminals** on the same channel link without any cable, moving items between bases for an energy cost.
3. **Storage request terminal.** Aggregates everything on the attached network into one GUI and keeps a buffer stocked to your filter.
4. **Drones.** A **Drone Hub** dispatches a hard-capped pool of **delivery drones** to terminals on its channel — point-to-point delivery with no infrastructure between.
5. **Create trains.** A **Train Cargo Interface** presents your network to Create train load/unload — no hard Create dependency.
6. **Cross-dimension shipping.** **Rocket Cargo Ports** launch cargo to a same-channel port in another dimension: slow, fuel-and-energy-hungry, and gated behind Core's **orbit** progression. Shipments never keep two dimensions chunk-loaded.
7. **Dashboard.** A **Logistics Dashboard** reports network size, throughput, shipping queues and drone status at a glance.

## Built for big bases

- ⚡ **Performance-first** — cached endpoints, incremental network rebuilds, per-tick transfer budgets, and hard caps on network size, drone pools and the shipping queue. Large networks degrade to higher latency, **not lower TPS**.
- 🎛️ **Tune or disable anything** — per-medium throughput, ranges, energy costs, and master toggles for wireless / drones / cross-dimension shipping, all server-authoritative.

## Privacy-first (POPIA / GDPR)

NeroLogistics stores **no personal data by default** — everything is keyed by block and dimension, never by player. Optional per-player throughput attribution is **off by default**, UUID-only when enabled, retention-pruned, and erasable through Core's shared data-erasure hook. Anonymous crash reporting is opt-out and carries only version strings — never names, UUIDs, IPs or world data. Full disclosure in `PRIVACY.md`.

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — one energy type, one side-config system, one progression arc, shared `c:` tags. NeroLogistics ships in its own creative tab.
- 🔌 **Interoperates, never hard-depends** — it discovers vanilla inventories, Nero machines, and (where present) Create and AE2 through Core capabilities and tags. Nerospace and Nerotech are optional: with them absent, NeroLogistics plays **fully standalone** (cross-dimension shipping even works between vanilla dimensions).
