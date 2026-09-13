# 3D Geological World POC — 0.10.0

## What 0.10 adds

0.9 proved the immediate CT/global-shell architecture on mobile. 0.10 keeps that architecture and adds two inspection tools plus the first typed procedural geology.

- **PAN** switches one-finger drag from orbit rotation to screen-space camera movement. Pinch zoom continues to work in both modes.
- **SEE ORE** is an explicit inspection/debug view that renders all ore bodies inside the currently generated geology through the rock.
- The old generic single ore body is replaced by deterministic **Gold, Silver and Copper** deposits.

## Canonical ownership

`domain/` owns the real mine and geology: tunnel path, generated extent, seeded ore bodies, ore types, body shapes and discovery state.

`ui/render/` owns transient inspection state: rotate/pan selection, zoom, CT clipping and SEE ORE presentation. SEE ORE never modifies discovery state.

## Seeded typed ore geology

The POC uses a fixed seed so the same mine always produces the same geology during testing.

Each ore deposit is a continuous 3D body made from a sequence of control nodes. Every node contains an X/Y/Z position and a radius. Consecutive nodes form a varying-width tube, so ore is not represented as visible cubic blocks.

The first generator creates six independent deposits:

- two **Gold** bodies — thinner, shorter and more irregular;
- two **Silver** bodies — medium width and continuity;
- two **Copper** bodies — broader, longer and less erratic.

Bodies may extend beyond the initial world bounds. They are generated from the seed rather than spawned when the player reaches them, so expanding the geological world reveals more of an already-defined deposit.

## Discovery

Normal play keeps undiscovered ore hidden. Each new excavation segment is tested against undiscovered typed ore bodies. Intersecting a body marks that connected deposit as discovered and it remains known afterwards.

SEE ORE bypasses that visibility rule only for inspection. It renders the generated Gold/Silver/Copper bodies without changing the domain discovery set.

## Ore presentation

SEE ORE uses lightweight tube geometry rather than feeding ore through the expensive tunnel scalar-field mesher.

- Gold is rendered gold/yellow.
- Silver is rendered pale silver/blue-grey.
- Copper is rendered copper/orange.
- Only parts overlapping the currently generated mine bounds are sent to the renderer.
- In SEE ORE mode the bodies are drawn as an x-ray overlay through rock so their full 3D relationship can be inspected while rotating, panning, zooming and slicing.

## Touch camera controls

The VIEW panel now exposes the complete touchscreen camera set:

- default one-finger drag: **rotate/orbit**;
- PAN selected: one-finger drag **moves the camera target in screen-space left/right/up/down**;
- pinch: **zoom** in either mode;
- RESET clears zoom, rotation and pan offsets.

PAN is implemented in camera right/up coordinates, so dragging remains intuitive after rotating the mine rather than being tied to fixed world X/Y axes.

## Existing 0.9 architecture retained

- one coherent constant-cost geological shell;
- grass as a separate surface mesh;
- detailed marching-tetrahedra geometry only around excavation;
- immediate analytic X/Y/Z CT faces with no asynchronous cap queue;
- chunk work scales with actual workings rather than untouched world volume;
- ROCK OFF and Digger POV remain available.

## What to test on device

1. Rotate the mine, enable PAN, and swipe in every direction. The visible mine should translate relative to the screen without changing its orientation.
2. Pinch zoom while PAN is selected and confirm zoom behaves exactly as before.
3. Toggle PAN off and confirm one-finger drag returns to rotation.
4. Enable SEE ORE and inspect the six seeded deposits from multiple angles and X/Y/Z slices.
5. Verify Gold looks thinner/more irregular, Silver intermediate, and Copper visibly broader.
6. Disable SEE ORE and confirm undiscovered deposits disappear again.
7. Dig into the starter Gold body and confirm only genuinely discovered geology remains visible in normal mode.
8. Expand the world and confirm additional portions of pre-seeded deposits become visible rather than changing shape or relocating.
9. Confirm FPS and excavation queues remain comparable to 0.9.

## Still outside this POC

- grades and ore quality;
- processing/refining and economics;
- geological faults, branching/mineralisation families and realistic deposit genesis;
- exploration confidence/uncertainty;
- broken-rock haulage and disposal;
- shaft hoisting;
- workers, power and ventilation;
- production stopes;
- save/load and sparse disk-backed world streaming.
