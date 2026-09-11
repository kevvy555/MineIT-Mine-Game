# 3D Geological World POC — 0.5.0

## Purpose

0.5 keeps the solid geological/CT interaction proven by 0.4 and focuses on the next critical question: **can the custom 3D approach scale on ordinary mobile hardware while tunnel quality later increases?**

The Pixel-class phone is treated as a useful target rather than assuming performance problems are simply old hardware.

## Domain ownership

`domain/` remains the canonical owner of:

- X/Y/Z mine coordinates and world extent;
- tunnel geometry;
- ore-body geometry and discovery;
- machine heading and vertical angle;
- continuous excavation rules;
- horizontal steering;
- excavated volume and waste tonnage.

Renderer optimisation must not become gameplay truth.

### Steering preview

When stopped, the LEFT/RIGHT slider previews up to 90° either side of the committed heading. The machine mesh visibly rotates to this preview direction. Starting excavation commits the preview heading and recentres steering.

Once moving, steering again represents turn rate so the tunnel curves rather than snapping between headings.

### Absolute vertical angle

The domain now supports the complete -90° to +90° range:

- -90° = vertically up;
- -45° = 45° up;
- 0° = level;
- +45° = 45° down;
- +90° = vertically down.

The UI keeps the vertical slider and adds absolute presets. This makes a shaft-to-level sequence explicit: **DOWN 90**, then **LEVEL**.

## Performance architecture

### Chunk-local rock meshes

The 24 m geological chunks that previously existed mainly as world-expansion bookkeeping are now actual render cache units.

The renderer no longer regenerates one mesh for the complete geological extent whenever the tunnel changes. Each new tunnel segment calculates the small set of chunks its cutter radius can affect and only those chunks are marked dirty.

When world expansion moves an exterior boundary, only newly created chunks and old boundary-face chunks are invalidated.

### Tunnel spatial index

Each render chunk keeps a set of tunnel segments that can physically influence that chunk. Scalar-field sampling therefore tests a point against local tunnel geometry rather than every segment ever excavated.

This is renderer acceleration data only; the authoritative tunnel remains the domain polyline.

### Active and refined quality

During continuous digging, dirty chunks use a 2 m scalar-field sampling step to keep cutter updates responsive.

When digging stops, chunks touched by that excavation run a 1.2 m refinement pass. This deliberately separates **interactive excavation cost** from **finished tunnel quality**, allowing later testing of still finer walls without multiplying the whole-world workload.

### GPU buffers

Each cached chunk is uploaded to an OpenGL vertex buffer object (VBO). Meshes remain on the GPU until that specific chunk is invalidated.

The CT cap and machine are separate VBOs. Shader attribute/uniform locations are cached rather than looked up every frame.

### CT cap invalidation

The solid CT cap is expensive enough to treat independently. It rebuilds when:

- the player changes axis, position, side or enables/disables slicing;
- geological extent changes;
- ore discovery changes the material shown on a cut;
- a newly excavated segment reaches the current cut plane.

Excavation elsewhere no longer forces the current CT section to regenerate.

### Incremental ore contact

Previously, every excavation tick rescanned the complete historic tunnel to calculate ore exposure. 0.5 tests only the newly added tunnel segment and merges any newly exposed ore segment IDs into existing discovery state.

The full scan remains available as a regression reference and is tested against the incremental result.

## Performance HUD

The 3D viewport displays a compact diagnostic overlay containing:

- FPS;
- effective frame time;
- most recent chunk mesh-build time;
- number of chunks rebuilt in that sample;
- CT-cap build time;
- triangle count;
- cached chunk count;
- GPU-buffer upload time.

`GLSurfaceView` deliberately runs continuously in this POC so FPS can be measured on real devices. A later production pass can return to demand-driven rendering when static to reduce battery use.

## Digger readability and x-ray locator

The old machine was too visually symmetrical to communicate direction. 0.5 gives it:

- a wide bright cutter at the front;
- an elongated orange body;
- a bright centre spine pointing at the face;
- a dark rear block.

The same mesh is rendered a second time at low alpha with depth testing and CT clipping disabled. This creates an always-visible x-ray silhouette without revealing hidden tunnels or ore.

## Continuous digging cadence

The simulation cadence moves from 250 ms to 100 ms. Chunk-local invalidation is intended to make the more responsive machine movement affordable while exposing actual device performance through the HUD.

## What to evaluate

1. Does FPS remain comfortable while continuously digging on a Pixel 7-class device?
2. How large are the reported **mesh**, **cap**, and **upload** times during a straight shaft and during curved excavation?
3. Does stopping cause a tolerable refinement cost, and is the resulting tunnel noticeably smoother?
4. Does world expansion create an obvious hitch or remain local enough?
5. Is the asymmetric/x-ray machine easy to locate and is its direction unmistakable?
6. Does stopped steering preview make choosing a heading intuitive?
7. Are **DOWN 90 → LEVEL** and the other absolute presets useful enough to keep?
8. After this pass, is finer tunnel sampling realistic on the target phone, or should meshing move off the GL thread before quality is increased further?

## Still deliberately excluded

- broken-rock piles and haulage;
- shaft hoisting;
- workers;
- power and ventilation;
- multiple geology types;
- exploration uncertainty;
- arbitrary tunnel profiles and stopes;
- save/load;
- sparse disk-backed chunk streaming;
- production lighting/textures.
