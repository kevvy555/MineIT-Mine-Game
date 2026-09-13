# Current Handover — MineIT Mine Game

Date: 2026-09-13
Target release: 0.13.3

## Current state

The native Android MineIT Mine Game POC now has a stable 3D geology/mining foundation:

- OpenGL ES renderer behind Jetpack Compose.
- Expandable 3D geological world with rotate, pan and pinch zoom.
- Persistent multi-axis X/Y/Z CT cutting; all three planes can be enabled together.
- SEE ORE reveals ore only on CT cut faces, not as an x-ray overlay.
- ROCK OFF and TUNNEL OFF allow inspection of discovered/depleted ore bodies.
- Seeded Gold, Silver and Copper deposits currently use the original control-node/radius tube representation.
- Excavation is true subtractive geometry: original deposits are immutable and remaining ore is derived from `original deposit - excavated tunnel volume`.
- Ore and waste accounting partitions every newly removed volume exactly once; revisiting old workings cannot create material again.
- Remaining ore is chunked and meshed asynchronously. Live digging uses a coarser ore mesh and stopped/idle rendering uses the 0.6m refined mesh.
- Exportable CSV diagnostics are available from the app. The logger records 1-second samples plus tick/mesh spike events.

## Deposit redesign plan

`docs/DEPOSIT_GEOMETRY_REDESIGN.md` is the long-term plan.

1. True subtractive geometry — COMPLETE.
2. Realistic deposit generators — NEXT after performance is acceptable:
   - tabular/lode vein,
   - lens/massive body,
   - layered/stratiform body,
   - disseminated/stockwork volume.
3. Lightweight host-rock layers.
4. Layered intrusions, laccolith-style host bodies, geological relationships and grade/composition.

The key architectural decision is to keep deposit generation separate from excavation. Future deposit types should expose a signed/implicit material field and automatically inherit the same subtraction/mining behaviour.

## 0.13.2 diagnostic findings

A user test log (`mineit-diagnostics-20260913-141327.csv`) isolated two bottlenecks:

- Rendering stayed near 89–90 fps / ~11.1ms while the user perceived digging hitches.
- The synchronous mining tick reached ~32ms, with ~30ms in material classification.
- Typical ticks evaluated ~177 candidate samples, up to 6 ore bodies per sample, and many historical tunnel segments.
- Exact ore mesh generation was no longer the immediate UI blocker, but stopping after an ore cut could create a large fine-mesh backlog (around 75 ore chunks in the captured run).
- Fine 0.6m ore chunks could take roughly 0.8–0.95s each to polygonise on the ore worker.

Conclusion: keep the subtractive geometry architecture; optimise scheduling and spatial/query breadth rather than redesigning the ore model again.

## 0.13.3 performance pass

This release targets the findings above without changing gameplay truth:

- Mining ticks are moved off the Android main/UI thread onto `Dispatchers.Default`.
- Tick scheduling is anchored to a fixed 10 Hz cadence rather than `100ms delay + calculation time`.
- A completed background tick is only published if its input state is still current, so steering/speed/view-related user input cannot be overwritten by a stale result.
- Ore-field classification first culls distant ore bodies using conservative deposit bounds.
- `oreMargin` no longer allocates `zipWithNext()` lists for every sample.
- Historical tunnel candidates get precomputed expanded AABBs; cheap bounds checks reject most segments before the expensive finite-cylinder distance test.
- Diagnostics now distinguish tunnel bounds checks from exact cylinder checks and report the number of nearby ore bodies.
- Ore render chunk planning follows each local ore segment envelope instead of the whole deposit's enclosing AABB. This reduces empty chunks queued for discovery/live/final ore meshing, especially for long or curved deposits.

## Next device test

Use the current 0.13.3 APK and repeat the same diagnostic scenario:

1. Start a fresh mine and approach a reasonably large ore body.
2. Dig through it at 0.3x speed with diagnostics visible.
3. Continue for several seconds after entering and leaving the ore.
4. Stop and wait until ore/rock workers settle.
5. Export the CSV diagnostics and upload it to the next chat.
6. If possible, note whether controls/machine movement still visibly hitch even when FPS remains high.

Compare against 0.13.2 specifically for:

- `tick_ms` and `material_ms` peaks,
- `nearby_ore_bodies` and `ore_field_evals`,
- `tunnel_bounds_checks` versus `exact_tunnel_checks`,
- ore queue size after discovery and after Stop,
- live ore mesh latency and fine ore mesh backlog.

## What to do next

If 0.13.3 materially reduces tick spikes and ore queue size, proceed to Stage 2 of the deposit redesign rather than further micro-optimising the current tube deposits.

If material ticks are still regularly above ~15–20ms, the next likely optimisation is a persistent domain-side spatial index/query structure for excavation history rather than scanning the broad candidate list every tick.

If the ore queue is still large, add a low-priority refinement backlog so coarse live chunks remain usable and fine 0.6m refinement is progressively scheduled instead of creating a large immediate stop-time queue.

## Repository rules

Always read root `AGENTS.md` in full before changing code. In particular:

- `domain/` owns canonical gameplay state/rules.
- `ui/` and `ui/render/` own presentation, scheduling and disposable mesh/cache state.
- Do not introduce duplicate/new/old/temp production implementations.
- Gameplay/domain changes require regression tests.
- Full Android CI must pass before considering a significant change complete.
