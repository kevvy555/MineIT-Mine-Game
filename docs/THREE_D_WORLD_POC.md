# 3D Geological World POC — 0.6.0

## Purpose

0.6 keeps the solid geological/CT interaction and focuses on two things revealed by real Pixel 7 testing:

1. **rendering must stay responsive while excavation geometry is regenerated**;
2. the mobile controls need to stay understandable as more view/mining tools are added.

The Pixel-class phone remains a deliberate performance target. The goal is not to excuse slow behaviour as old hardware; the custom 3D approach must prove it can scale before tunnel quality is increased significantly.

## Canonical ownership

`domain/` remains authoritative for:

- X/Y/Z mine coordinates and world extent;
- tunnel geometry;
- ore-body geometry/discovery;
- heading and vertical angle;
- continuous excavation;
- steering and fixed relative heading turns;
- removed volume and waste tonnage.

Camera following, CT clipping, diagnostics, chunk caches and background mesh jobs are renderer/UI concerns only.

## Control model

The bottom interaction area is split into three mutually exclusive panels selected by persistent **CONTROL / VIEW / OTHER** buttons.

### CONTROL

- stopped LEFT/RIGHT steering visibly previews the machine direction;
- moving LEFT/RIGHT remains a turn-rate input;
- fixed relative turns are available at **45° and 90° left/right** while stopped;
- the vertical UP/DOWN slider remains available for arbitrary slope;
- absolute slope presets remain UP 90, UP 45, LEVEL, DOWN 45 and DOWN 90;
- START/STOP controls continuous excavation.

### VIEW

- orbit drag and pinch zoom;
- X/Y/Z CT slice controls;
- OTHER SIDE and FULL/SLICE;
- **FOLLOW DIGGER**, which targets the camera at the machine while preserving orbit and zoom;
- reset view.

The zoom range is deliberately much wider than 0.5 so the player can inspect the cutter/tunnel wall closely.

### OTHER

- diagnostics toggle;
- reset mine;
- compact live renderer status.

## Second performance architecture pass

### Background CPU meshing

0.5 made geology chunk-local, but scalar-field sampling and polygonisation still ran synchronously inside `onDrawFrame`. A single expensive chunk could therefore freeze camera motion even though the total amount of work was much smaller.

0.6 moves **chunk mesh generation** to a dedicated background worker. Completed CPU meshes are returned to the OpenGL thread, where only the VBO upload/replacement occurs.

The CT cap has a separate background worker so moving a slice is not forced to wait behind a geological chunk build.

Build requests include pipeline/revision IDs. If excavation changes a chunk or the slice moves while an older request is still running, the stale result is discarded rather than overwriting newer geometry.

### Distance-throttled active excavation

The domain machine still advances at 100 ms ticks, but geology is no longer remeshed every tick.

New tunnel segments are immediately indexed spatially, while affected chunks accumulate in an active dirty set. An active remesh is scheduled after the cutter has advanced roughly **1.2 m** (or when world extent changes). This separates smooth machine motion from the much more expensive geological surface update.

When digging stops, every chunk touched during that run receives the existing finer refinement pass.

### Skip solid interior chunks

A completely solid chunk inside the geological extent cannot contribute visible triangles. Such chunks are no longer sampled/meshed until excavation reaches them. Outer world faces and excavated interior chunks still generate normal meshes.

This matters increasingly as the mine expands in X/Y/Z because cost now follows visible surface plus excavated workings rather than total volume.

## CT cap bug fix

Real-device testing exposed a directional hollow-box effect when moving a Z slice deeper from the grass surface.

The cause was timing rather than geology: the OpenGL clip plane moved immediately, but a replacement solid cap took longer to generate. The previous shallower cap was clipped away by the new plane, temporarily exposing the hollow shell. Moving back upward appeared correct because the previous deeper cap remained on the kept side.

0.6 keeps the last valid cap visible without applying the current clipping plane to that cap while the next asynchronous cap is generated. The replacement then swaps in atomically on the GL thread.

## Diagnostics

The 0.5 diagnostics listener could be detached during Compose recomposition because the lifecycle effect disposed the newly created view rather than the view captured by that effect.

0.6 binds/unbinds the specific `MineSurfaceView` instance and explicitly resumes it when attached to an already-resumed lifecycle. The overlay now reports:

- FPS/frame time;
- latest background chunk build time;
- latest background CT-cap build time;
- GPU upload time;
- triangle count;
- cached chunk count;
- pending chunk queue length;
- mesh/cap worker BUSY/IDLE state.

## Follow digger

FOLLOW DIGGER changes the camera target from the full geological extent centre to the current tunnel face/machine position. It uses a fixed local framing span so the machine does not become progressively smaller simply because the mine world has expanded hundreds of metres away.

The x-ray machine remains independent of follow mode and continues to render through rock and CT slices.

## What to evaluate

1. Does orbit/zoom remain smooth while the mesh worker reports BUSY?
2. Does continuous digging feel smoother even if the actual tunnel wall catches up in ~1 m steps?
3. Do diagnostics now show realistic FPS/build/queue values on device?
4. Does Z slicing stay visually solid in both drag directions?
5. Is the larger zoom range enough to inspect the cutter and wall closely?
6. Is FOLLOW DIGGER useful without becoming disorienting?
7. Are fixed 45°/90° horizontal turns useful for deliberate mine layouts?
8. Does the three-panel control layout leave enough viewport space while keeping all important actions obvious?

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
