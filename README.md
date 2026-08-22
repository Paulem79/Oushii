# Oushii

An explosion engine for Minecraft that replaces vanilla raycasting with a blast field solved directly on the block grid. It handles large TNT chain explosions without stalling the server, and keeps craters shaped the way vanilla shapes them.

---

## Why Oushii?

In vanilla Minecraft, large TNT detonations overload the server because the blast is sampled with 1352 rays that each re-walk the blocks their neighbours already walked, one `getBlockState` at a time. Even with performance mods like Lithium, chain explosions cause significant TPS drops.

Oushii solves the same energy budget on the block grid instead of along rays. Every block is visited at most once, straight out of the chunk sections in memory: it takes the energy left by the neighbours one step closer to the centre, pays for its own blast resistance, and passes on what is left. Blocks the blast cannot get through cast a shadow behind them, exactly like a ray that ran out.

That keeps what makes a vanilla crater recognisable — shallow in stone, deep in dirt, stopped by obsidian and by water — while the blast that never reaches a block costs nothing to skip. Explosions going off in the same tick close to each other are merged into one, and TNT flying through empty chunk sections skips collision checks entirely.

---

## Compatibility

Oushii is compatible with Lithium, and should also be with other optimization mods and custom TNT mods.

Note: This mod is not compatible with TNT Breaks Bedrock, at least for now.

---

## Performance Comparison (with Spark)

Tested on a 26.2 superflat world with no mobs or pre-spawned item entities.
Profiled on a detonation of a 30×30×30 TNT cube (27,000 blocks).

> Measured on Oushii 1.0.1, before the blast field became resistance aware. The engine now
> destroys what vanilla would destroy rather than a fixed sphere, so these figures need to be
> taken again.

| Metric (Spark) | Vanilla | Lithium | **Oushii** | **Lithium + Oushii** | Best vs Vanilla |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **`PrimedTnt.tick` Execution** | `150,808 ms` | `39,940 ms` | `3,572 ms` | **`2,996 ms`** | **50.3× faster** |
| **Server TPS (1m window)** | `5.93` | `12.80` | `19.30` | **`19.35`** | **+226%** |
| **Median MSPT** | `4.33 ms` | `3.64 ms` | `3.74 ms` | **`2.38 ms`** | **+45%** |
| **MSPT (95th percentile)** | `164 ms` | `82.6 ms` | `88.8 ms` | **`33.8 ms`** | **+79%** |
| **RAM Footprint** | `739.9 MB` | `939.8 MB` | `757.8 MB` | **`485.1 MB`** | **-34%** |