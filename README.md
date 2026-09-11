# MineIT Mine Game

Native Android mining-game prototype in the MineIT universe.

## Current prototype — 0.6.0

The mine is a genuine **3D geological volume** with renderer-independent gameplay rules and a lightweight native OpenGL renderer.

0.6 builds on the solid CT-scan concept with a second performance pass and a simpler mobile control layout:

- X/Y/Z slices expose solid geological cut faces with real tunnel holes;
- the directional hollow-cap bug is fixed by retaining the last valid CT cap until the replacement is ready;
- geological chunk and CT-cap mesh generation now runs on background CPU workers rather than the OpenGL render thread;
- the active face is remeshed by excavation distance instead of every 100 ms simulation tick;
- untouched interior chunks are skipped entirely until excavation reaches them;
- touched chunks still receive a finer refinement pass after digging stops;
- cached GPU buffers remain chunk-local;
- diagnostics now report real FPS, frame time, CPU mesh/cap build time, upload time, triangle count, chunk count, queue depth and worker state;
- pinch zoom can move much closer to the geology and mining machine;
- **FOLLOW DIGGER** keeps the camera centred on the machine as the mine grows;
- the orange x-ray machine remains visible through solid rock and CT clipping;
- controls are split into **CONTROL / VIEW / OTHER** panels selected from three persistent buttons at the bottom;
- stopped steering still visibly previews the machine heading;
- fixed horizontal turns provide **L 90, L 45, R 45 and R 90**;
- vertical absolute presets remain **UP 90, UP 45, LEVEL, DOWN 45 and DOWN 90**.

## Controls

### CONTROL

- Use **STEER — LEFT / RIGHT** for a visible direction preview while stopped and gradual turning while digging.
- When stopped, use **L 90 / L 45 / R 45 / R 90** for exact relative horizontal turns.
- Use the vertical **ANGLE — UP / DOWN** slider for arbitrary slope.
- Use the absolute slope presets for exact shaft/drive angles.
- **DOWN 90** creates a vertical shaft; **LEVEL** changes to a horizontal drive.
- Press **START DIGGING / STOP** for continuous excavation.

### VIEW

- Drag to orbit the 3D mine and pinch to zoom.
- Select **X / Y / Z** and move the slice slider to inspect the geology like a CT scan.
- **OTHER SIDE** reverses which side of the cut remains visible.
- **SLICE ON / FULL** switches between cutaway and the complete geological volume.
- **FOLLOW DIGGER** centres the camera on the machine as it moves.
- **RESET VIEW** restores the default orbit/zoom.

### OTHER

- Toggle the on-screen renderer diagnostics.
- **RESET MINE** restores the untouched surface start.

Purple mineralisation remains hidden until first physical contact. After discovery, the connected vein becomes visible where CT slices intersect it.

## Architecture

- `domain/` — renderer-independent geology, chunks, ore body, tunnel geometry, excavation, discovery and material accounting.
- `ui/` — Compose controls, continuous-dig loop and Android lifecycle/view state.
- `ui/render/` — chunk planning/spatial indexing, asynchronous mesh workers, OpenGL ES camera/VBO cache, clipping, CT caps and x-ray machine rendering.

See `docs/THREE_D_WORLD_POC.md` for the current design direction.
