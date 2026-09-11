# MineIT Mine Game

Native Android mining-game prototype in the MineIT universe.

## Current prototype — 0.5.0

The mine is a genuine **3D geological volume** with a lightweight native OpenGL renderer and renderer-independent domain rules.

0.5 is the first performance-oriented pass on the solid CT concept. It keeps the 0.4 interaction model but changes how geometry is generated and drawn so the approach can scale on a phone:

- the mine remains true X/Y/Z data;
- X/Y/Z CT slices still create solid geological cut faces with real tunnel holes;
- the grass surface, continuous digging and connected-vein discovery remain intact;
- geological rendering is now cached **per 24 m chunk** instead of rebuilding the entire visible world whenever the cutter advances;
- each chunk has a spatial list of only the tunnel segments capable of affecting it;
- active excavation uses a coarser local mesh, then touched chunks receive a finer refinement pass after digging stops;
- OpenGL geometry is uploaded into cached GPU vertex buffers rather than retained as one large client-side buffer;
- the CT cap is only rebuilt when the slice changes, the world extent changes, ore discovery changes, or new excavation reaches the active slice plane;
- ore exposure checks are incremental: a new excavation segment is checked rather than rescanning the full historic tunnel;
- an on-screen performance panel reports FPS, frame time, chunk-mesh build time, CT-cap build time, triangle count, cached chunks and GPU-buffer upload time;
- the mining machine is deliberately asymmetric with a bright forward spine, making heading and slope readable;
- the machine has an always-visible low-alpha **x-ray pass**, so its real 3D position remains visible even behind rock or on the removed side of a CT slice;
- while stopped, LEFT/RIGHT steering previews the direction of the whole machine; pressing START commits that heading and recentres steering;
- while digging, LEFT/RIGHT returns to continuous turn-rate steering;
- vertical angle now supports the full -90° to +90° range;
- absolute presets provide **UP 90, UP 45, LEVEL, DOWN 45 and DOWN 90** for shafts and level drives.

## Controls

- Drag the 3D view to rotate it.
- Pinch to zoom.
- Select X, Y or Z and move the slice slider to move the geological cut plane.
- **OTHER SIDE** reverses which side of the cut is removed.
- **SLICE ON / FULL** switches between a CT-style cutaway and the complete geological volume.
- While stopped, use **STEER — LEFT / RIGHT** to preview the machine heading. **START DIGGING** commits that heading.
- While digging, LEFT/RIGHT curves the tunnel.
- Use the vertical **ANGLE — UP / DOWN** slider for arbitrary slope, or an absolute angle preset.
- **DOWN 90** produces a vertical shaft; **LEVEL** changes to a horizontal drive.
- The translucent orange machine is intentionally visible through rock and clipping as an x-ray locator.
- **RESET VIEW** resets the camera; **RESET MINE** restores the untouched surface start.

Purple mineralisation is hidden until first physical contact. After discovery, the connected vein becomes available for geological inspection through the slice views.

## Architecture

- `domain/` — renderer-independent geology, chunks, ore body, tunnel geometry, continuous excavation, discovery and material accounting.
- `ui/` — Compose controls, continuous-dig loop and Android lifecycle/view state.
- `ui/render/` — chunk planning/spatial indexing, OpenGL ES camera, VBO cache, clipping, CT cap generation, x-ray machine and generated geological triangle meshes.

See `docs/THREE_D_WORLD_POC.md` for the current design direction.
