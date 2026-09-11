# 3D Geological World POC — 0.8.0

## Purpose

0.8 follows a useful real-device result from 0.7: the OpenGL renderer remained at **90 FPS on a Pixel 7**, but CPU geometry production did not keep up with excavation. At roughly 165 m depth diagnostics showed examples around **3.3 s per chunk**, **7.4 s per CT cap** and a queue of **155** pending chunk rebuilds.

That means the remaining problem is not GPU frame rendering. It is the amount of CPU meshing work generated as the geological world expands.

This pass therefore targets:

1. keeping the visible excavation much closer to the authoritative digger position;
2. preventing untouched world-shell growth from creating an ever-growing expensive queue;
3. reducing repeated work caused by dense 100 ms simulation tunnel segments;
4. adding a player-controlled excavation speed;
5. adding a tunnel-only inspection view which hides rock but retains the grass surface.

## Canonical ownership

`domain/` remains authoritative for geology, tunnel geometry, ore discovery, machine heading/slope, **excavation speed**, continuous excavation and material accounting.

Camera modes, CT slicing, rock visibility, follow behaviour, mesh scheduling and GPU presentation remain UI/render concerns. ROCK OFF changes presentation only; it does not alter geology or excavation state.

## World-shell fast path

The previous renderer used marching tetrahedra for any chunk on an outer world boundary, even when the entire chunk contained untouched solid rock. As depth and X/Y extent grew, this created many expensive jobs that added almost no visual information.

0.8 keeps the same chunk/world model but changes rendering cost:

- chunks containing excavation still use the scalar-field polygoniser;
- untouched outer chunks are represented directly by flat boundary quads;
- completely internal untouched chunks remain unmeshed;
- old shell faces are removed/replaced normally when the extent expands.

The important scaling property is that expensive work should now track **excavated workings**, not the volume or surface area of the entire enclosing geological box.

## Tunnel-segment compaction

The simulation deliberately records a point every 100 ms for responsive steering and accurate gameplay state. That detail level is excessive for a 1.0–1.6 m render grid.

A render-only `TunnelSegmentReducer` now merges short contiguous, nearly-collinear segments before scalar-field sampling. It preserves sharp bends and exact endpoints. Domain tunnel data remains unchanged.

This reduces the number of distance-to-segment calculations made by every scalar-field sample without reducing simulation precision.

## Removing redundant per-triangle field samples

0.7 sampled the full solid field twice for every emitted triangle to decide its winding direction. OpenGL culling is disabled and the simple lighting shader uses the absolute normal/light dot product, so changing the sign of the normal did not affect visible output.

0.8 removes those two expensive scalar-field queries and uses the geometric triangle normal directly.

## Bounded parallel meshing and priority

Chunk meshing now uses **two low-priority background workers** rather than one. This is deliberately bounded: the goal is to improve throughput without creating an unbounded CPU/battery load.

While digging:

- the current face is prioritised by distance;
- a new active coarse rebuild replaces pending stopped/refined work for the same chunk;
- fine stopped refinement receives a large scheduling penalty until active excavation work is clear;
- completed intermediate revisions continue to be shown progressively.

Live/refined grid spacing remains approximately **1.6 m / 1.0 m**.

## CT cap throughput

The solid CT cap remains asynchronous and independent of rock chunk workers. 0.8 also reduces cap pressure:

- cap cells use a lighter sampling step;
- movement smaller than roughly 1 m does not enqueue another expensive rebuild;
- only tunnel segments capable of intersecting the selected slice are supplied to the cap builder;
- those segments are compacted before sampling.

The previous cap stays visible until a newer result is ready, preserving the solid CT appearance.

## Excavation speed

The CONTROL panel adds a **SPEED** slider from **0.25× to 2.0×**.

This is domain state and changes actual distance excavated per simulation tick. It therefore changes tunnel length, removed volume, waste tonnage, ore contact and world expansion consistently rather than only speeding up an animation.

## ROCK OFF — tunnel-only inspection

VIEW adds **ROCK ON / ROCK OFF**.

ROCK OFF:

- hides the geological chunk meshes and CT cap;
- retains a lightweight grass surface as the surface reference;
- renders the complete excavated tunnel directly as a smooth low-cost tube generated from the authoritative tunnel path;
- keeps ore colouring on relevant tunnel sections after discovery;
- keeps the third-person/x-ray digger and normal camera controls.

This view is intentionally independent of detailed geological remeshing, so the player can inspect the full mine immediately even if rock refinement is still underway.

## What to evaluate

1. During a long continuous shaft/drive, does queue depth remain small instead of growing into the tens/hundreds?
2. How do `mesh ms` values compare with the 0.7 examples of ~700–3200 ms?
3. How do `cap ms` values compare with the observed ~7.4 s deep-world cap build?
4. Does the excavated rock surface stay materially closer to the digger while follow mode is active?
5. At 2.0× speed, can the geometry pipeline still remain bounded?
6. Is ROCK OFF useful for understanding the complete tunnel network, and does the retained grass surface give enough orientation?
7. Does the speed control feel useful from 0.25× through 2.0×?

## Still deliberately excluded

- broken-rock removal logistics;
- shaft hoisting;
- workers;
- power and ventilation;
- multiple geology types;
- exploration uncertainty;
- arbitrary tunnel profiles and stopes;
- save/load;
- sparse disk-backed chunk streaming;
- production lighting/textures.
