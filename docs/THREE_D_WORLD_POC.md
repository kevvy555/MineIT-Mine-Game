# 3D Geological World POC — 0.7.0

## Purpose

0.7 follows a successful real-device proof: the 0.6 custom OpenGL/async-mesh architecture held **90 FPS continuously on a Pixel 7**. The architectural question is therefore considered answered positively enough to move from basic viability toward visual fidelity and interaction quality.

This pass targets five issues from device testing:

1. excavation could visually stop updating while the digger continued, then catch up after STOP;
2. tunnel walls were still visibly coarse;
3. FOLLOW DIGGER did not move the CT slices with the machine;
4. there was no first-person view from the mining tool;
5. manual CT slicing could lag behind finger movement and only catch up after the slider stopped.

## Canonical ownership

`domain/` remains authoritative for geology, tunnel geometry, ore discovery, machine heading/slope, continuous excavation and material accounting.

Camera modes, CT slice positions, follow behaviour, mesh scheduling and GPU presentation remain UI/render concerns. The renderer is never the source of truth for where the machine or tunnel actually exists.

## Progressive asynchronous geometry

0.6 correctly rejected stale asynchronous mesh results, but the policy was too strict for interactive presentation. If the digger or CT slider kept moving, a completed build could already have a newer revision queued and was therefore discarded. On a busy worker this could repeat continuously, leaving the visible geometry frozen until input stopped.

0.7 changes the presentation rule:

- a completed chunk mesh is accepted if it is newer than the revision currently shown for that chunk;
- a later queued revision remains pending and replaces it when ready;
- the same progressive rule is used for CT caps;
- cap results from a different axis/side are still rejected so an old X/Y/Z section cannot replace the currently selected orientation.

This allows useful intermediate geometry to reach the screen instead of being thrown away while maintaining monotonic visual progress.

## Increased excavation quality

The live geological sampling step is reduced from 2.0 m to approximately **1.6 m**. Chunks touched during an excavation run receive a **1.0 m** refinement pass after the machine stops.

This is intentionally a modest quality increase. The aim is to measure whether the higher triangle/CPU cost remains comfortable before considering much finer walls or a different meshing algorithm.

## Follow camera + follow slices

VIEW now keeps separate slice fractions for X, Y and Z rather than sharing one slider value across all axes.

When **FOLLOW + SLICES** is enabled, all three fractions are continuously derived from the authoritative digger position. The orbit camera still targets the machine as before, but changing from X to Y or Z now opens the corresponding section at the same physical location rather than returning to an unrelated old cut plane.

Follow slice planning is pure/tested render-state logic and does not modify the mine itself.

## DIGGER POV

A new camera mode places the eye just behind the cutter and points along the current machine heading and vertical slope.

The POV uses the machine's own up vector rather than global up, which keeps the camera stable even for vertical shafts where a normal world-up look-at matrix would become degenerate.

DIGGER POV deliberately:

- hides the third-person machine mesh;
- ignores CT clipping and cut caps;
- locks viewing direction to the machine rather than allowing orbit drag;
- leaves all CT/follow state intact in the background so returning to ORBIT / CT immediately restores the geological inspection view.

## What to evaluate

1. Does the visible tunnel now continue advancing while continuous digging remains active, rather than freezing until STOP?
2. Is the 1.6 m live / 1.0 m refined geometry noticeably smoother without compromising the established 90 FPS rendering result?
3. Does FOLLOW + SLICES keep X/Y/Z sections usefully centred around the digger during shafts, level drives and turns?
4. Does DIGGER POV feel spatially correct at level, 45° and vertical headings?
5. Do CT caps move progressively enough during fast manual slider movement, and how large is the remaining CPU cap-build latency?
6. With the new quality, do mesh queue depth/build times remain bounded during long excavation runs?

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
