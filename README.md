# Oushii

An explosion engine for Minecraft that replaces vanilla raycasting with direct chunk iteration and simplified TNT physics. It handles large TNT chain explosions without stalling the server while preserving realistic crater shapes.

---

## Why Oushii?

In vanilla Minecraft, large TNT detonations overload the server because the default raycasting algorithm evaluates block positions individually. Even with performance mods like Lithium, chain explosions cause significant TPS drops.

Oushii iterates directly over loaded chunk sections in memory, skips collision checks for TNT moving through air, and applies a deterministic 3D hash to shape craters.
During a 27,000 block TNT explosion, server performance stays around 19.35 TPS compared to 5.93 TPS on vanilla.

---

## Compatibility

Oushii is compatible with Lithium, and should also be with other optimization mods and custom TNT mods.

---

## Performance Comparison (with Spark)

Tested on a superflat world with no mobs or pre-spawned item entities.
Profiled on a detonation of a 30×30×30 TNT cube (27,000 blocks).

| Metric (Spark) | Vanilla | Lithium | **Oushii** | **Lithium + Oushii** | Best vs Vanilla |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **`PrimedTnt.tick` Execution** | `150,808 ms` | `39,940 ms` | `3,572 ms` | **`2,996 ms`** | **50.3× faster** |
| **Server TPS (1m window)** | `5.93` | `12.80` | `19.30` | **`19.35`** | **+226%** |
| **Median MSPT** | `4.33 ms` | `3.64 ms` | `3.74 ms` | **`2.38 ms`** | **+45%** |
| **MSPT (95th percentile)** | `164 ms` | `82.6 ms` | `88.8 ms` | **`33.8 ms`** | **+79%** |
| **RAM Footprint** | `739.9 MB` | `939.8 MB` | `757.8 MB` | **`485.1 MB`** | **-34%** |