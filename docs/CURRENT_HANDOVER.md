# Current Handover — MineIT Mine Game

Date: 2026-09-13
Target release: 0.14.0
Branch: `feature/deposit-geometry-stage2`

## Current state

The native Android MineIT Mine Game POC now has a stable 3D subtractive mining foundation plus the Stage 2 realistic deposit model:

- OpenGL ES renderer behind Jetpack Compose.
- Expandable 3D geological world with rotate, pan and pinch zoom.
- Persistent multi-axis X/Y/Z CT cutting; all three planes can be enabled together.
- SEE ORE reveals ore only on CT cut faces, not as an x-ray overlay.
- ROCK OFF and TUNNEL OFF allow inspection of discovered/depleted ore bodies.
- Seeded Gold, Silver and Copper deposits no longer use control-node/radius tubes.
- Four immutable deposit archetypes are implemented: tabular vein/lode, lens/massive, layered/stratiform and disseminated/stockwork.
- Every deposit exposes one signed domain material field plus conservative whole-body and local planning bounds.
- Excavation remains true subtractive geometry: original deposits are immutable and remaining ore is derived from `original deposit - excavated tunnel volume`.
- Ore and waste accounting partitions every newly removed volume exactly once; revisiting old workings cannot create material again.
- Remaining ore is chunked and meshed asynchronously. Live digging uses a coarser ore mesh and stopped/idle rendering uses the 0.6m refined mesh.
- Mining ticks remain off the Android UI thread on the fixed 10 Hz cadence introduced in 0.13.3.
- Exportable CSV diagnostics remain available from the app.

## Stage 2 implementation

`docs/DEPOSIT_GEOMETRY_REDESIGN.md` is the durable design contract.

Stage 2 replaces `ore = tube` with a shape-agnostic domain contract:

- `OreBody` owns an immutable `OreDepositGeometry`.
- `margin(point)` is canonical original-ore truth: positive inside ore, zero at the surface and negative outside.
- `bounds` provides conservative mining broad-phase culling.
- `planningBounds()` provides conservative local regions for 12m render-chunk scheduling.
- Mining, discovery, remaining-material queries, CT and rendering consume this common contract instead of branching on archetype.

The default six bodies are:

- `gold-1` — tabular vein/lode, kept near the starter route for easy verification.
- `gold-2` — tabular vein/lode.
- `silver-1` — tabular vein/lode.
- `silver-2` — lens/massive body.
- `copper-1` — disseminated/stockwork volume.
- `copper-2` — layered/stratiform body.

The geometries are deterministic from `MineWorldContent.ORE_SEED`. Strike/dip basis vectors, conservative bounds and local planning regions are precomputed once per immutable body, so per-sample mining queries remain constant-cost and do not traverse deposit topology.

For irregular lens/stockwork bodies, conservative bound padding is scaled so low-frequency boundary variation cannot extend beyond the broad-phase AABB and be falsely culled.

## Stage 2 regression coverage

The tests now prove:

- the same seed produces structurally equal geology;
- the default seed contains two bodies of each commodity and all four archetypes;
- archetype fields classify representative inside/outside points correctly;
- generated bodies stay below the 5m minimum ore depth;
- the starter route still discovers/mines `gold-1`;
- every newly excavated sample remains exactly one of ore or waste;
- repeated excavation produces no material twice;
- all four archetypes inherit the same cutter subtraction and retain adjacent ore;
- distant deposits are broad-phase culled before per-sample field evaluation;
- long/diagonal deposits use local planning bounds rather than queuing empty corners of one enclosing AABB;
- CT and remaining-ore rendering still expose real cutter-sized voids.

A completed Stage 2 CI run has already passed unit tests, APK assembly, signer verification and artifact upload. The final 0.14.0 branch head must also pass the same full CI before promotion to `main`.

## 0.13.3 performance foundation retained

The Stage 2 implementation deliberately preserves the previous performance work:

- mining calculation runs on `Dispatchers.Default`, not the Android UI thread;
- tick scheduling is anchored to a fixed 10 Hz cadence;
- stale background ticks cannot overwrite newer steering/speed input;
- mining first culls ore bodies by conservative bounds;
- historical tunnel candidates use expanded AABBs before exact cylinder checks;
- diagnostics distinguish tunnel bounds checks from exact checks and expose nearby ore-body counts;
- ore rendering stays partitioned into aligned 12m chunks with asynchronous ore work;
- Stage 2 local planning bounds replace tube-segment planning without reverting to whole-deposit enclosing boxes.

## Next device test

After installing 0.14.0, validate both geology appearance and the retained performance behaviour:

1. Start a fresh mine with the default seed.
2. Use SEE ORE with X/Y/Z CT planes to inspect the generated bodies before mining. Confirm the vein, lens, layered and disseminated bodies are visibly distinct rather than tube-like.
3. Follow the normal starter route and confirm `gold-1` is still encountered naturally.
4. Dig through the Gold vein at about 0.3x speed and confirm the cutter leaves an actual tunnel-shaped hole through the sheet rather than thinning the entire body.
5. Use ROCK OFF after discovery to inspect the remaining body from several angles.
6. Stop and wait for coarse/fine ore workers to settle.
7. Export a diagnostic CSV.
8. Compare `tick_ms`, `material_ms`, `nearby_ore_bodies`, `ore_field_evals`, ore queue size and ore mesh latency against the 0.13.3 performance test. Stage 2 should not materially regress the 0.13.3 behaviour.

## What to do next

Once 0.14.0 is visually/device validated, Stage 3 of `docs/DEPOSIT_GEOMETRY_REDESIGN.md` is next:

- add lightweight deterministic host-rock regions/layers;
- give host units identity, colour and metadata;
- allow deposit generation to reference layers/contacts without coupling host rock and ore into one model;
- avoid a large hardness/economy simulation until gameplay requires it.

If device diagnostics show material ticks again regularly above roughly 15–20ms, investigate domain-side spatial indexing of excavation history rather than weakening the new deposit fields. If the ore refinement queue is again large, prioritise progressive/low-priority fine refinement rather than changing canonical geology.

## Repository rules

Always read root `AGENTS.md` in full before changing code. In particular:

- `domain/` owns canonical gameplay state/rules.
- `ui/` and `ui/render/` own presentation, scheduling and disposable mesh/cache state.
- Do not introduce duplicate/new/old/temp/compatibility production implementations.
- Gameplay/domain changes require regression tests.
- Full Android CI must pass before considering a significant change complete or updating `main`.
