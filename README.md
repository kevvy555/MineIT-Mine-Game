# MineIT Mine Game

Native Android mining-game prototype in the MineIT universe.

## Current prototype — 0.8.0

The mine is a genuine **3D geological volume** with renderer-independent gameplay rules and a lightweight native OpenGL renderer.

0.8 responds to real Pixel 7 diagnostics: rendering stayed at 90 FPS, but CPU geology generation could fall several seconds behind the digger as the world expanded. The focus is therefore **geometry throughput**, plus two new player controls.

- untouched outer rock chunks now use a very cheap planar shell instead of running marching tetrahedra over solid material;
- detailed scalar-field meshing is reserved for chunks actually affected by excavation;
- redundant solid-field samples previously made for every generated triangle have been removed;
- dense 100 ms simulation tunnel segments are compacted for meshing while preserving endpoints and sharp bends;
- two bounded low-priority chunk workers can process the active face in parallel without creating an unbounded CPU pool;
- active excavation is prioritised over stopped high-detail refinement work;
- CT-cap sampling is lighter and repeated slice requests are throttled by physical movement distance;
- live/refined tunnel quality remains approximately **1.6 m / 1.0 m**;
- a new **SPEED** control changes the real domain excavation rate from **0.25× to 2.0×**;
- **ROCK OFF** hides geological rock and CT caps but keeps the grass surface and renders the complete excavated tunnel directly, independent of the detailed rock-mesh backlog;
- follow camera/slices, DIGGER POV, x-ray machine, fixed horizontal turns and absolute vertical angle presets remain available.

## Controls

### CONTROL

- Use **STEER — LEFT / RIGHT** for a visible direction preview while stopped and gradual turning while digging.
- When stopped, use **L 90 / L 45 / R 45 / R 90** for exact relative horizontal turns.
- Use the vertical **ANGLE — UP / DOWN** slider for arbitrary slope.
- Use **UP 90 / UP 45 / LEVEL / DOWN 45 / DOWN 90** for exact vertical angles.
- Use **SPEED** for 0.25×–2.0× excavation speed.
- Press **START DIGGING / STOP** for continuous excavation.

### VIEW

- **ROCK ON** shows the solid geological model and CT tools.
- **ROCK OFF** shows the excavated tunnel network while retaining grass as the surface reference.
- In **ORBIT / CT**, drag to orbit and pinch to zoom.
- Select **X / Y / Z** and move the slice slider to inspect geology like a CT scan.
- **OTHER SIDE** reverses which side of the cut remains visible.
- **SLICE ON / FULL** switches between cutaway and the complete geological volume.
- **FOLLOW** centres the orbit camera on the digger and moves all three slice positions with it.
- **DIGGER POV** looks straight out from just behind the cutter along the current heading and slope.

### OTHER

- Toggle the on-screen renderer diagnostics.
- **RESET MINE** restores the untouched surface start.

Purple mineralisation remains hidden until first physical contact. After discovery, the connected vein becomes visible where CT slices intersect it.

## Architecture

- `domain/` — renderer-independent geology, chunks, ore body, tunnel geometry, excavation speed, discovery and material accounting.
- `ui/` — Compose controls, continuous-dig loop and Android lifecycle/view state.
- `ui/render/` — chunk planning/spatial indexing, tunnel-segment compaction, bounded asynchronous mesh workers, OpenGL ES cameras/VBO cache, clipping, CT caps, direct tunnel overview and x-ray machine rendering.

See `docs/THREE_D_WORLD_POC.md` for the current design direction.
