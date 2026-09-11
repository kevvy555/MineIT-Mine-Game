# MineIT Mine Game

Native Android mining-game prototype in the MineIT universe.

## Current prototype — 0.7.0

The mine is a genuine **3D geological volume** with renderer-independent gameplay rules and a lightweight native OpenGL renderer.

0.7 builds on the proven 90 FPS Pixel 7 result and focuses on visual fidelity and eliminating asynchronous catch-up lag:

- chunk and CT-cap meshes still build away from the OpenGL render thread;
- completed geometry is now shown **progressively** even when a newer revision is already queued, so excavation and CT slices no longer appear frozen until movement stops;
- active tunnel sampling increases from the previous coarse setting to roughly **1.6 m**, with a **1.0 m** stopped/refined pass for smoother walls;
- follow mode now tracks not only the camera but also independent **X, Y and Z slice positions** around the digger;
- switching X/Y/Z while follow is enabled therefore stays centred on the machine;
- a new **DIGGER POV** camera sits just behind the cutter and looks directly along the machine heading/slope;
- DIGGER POV deliberately ignores CT clipping so it represents the physical view from the machine;
- returning to **ORBIT / CT** restores normal free rotation, slicing and the previously followed slice positions;
- the existing x-ray machine, fixed 45°/90° horizontal turns and absolute vertical angle presets remain intact.

## Controls

### CONTROL

- Use **STEER — LEFT / RIGHT** for a visible direction preview while stopped and gradual turning while digging.
- When stopped, use **L 90 / L 45 / R 45 / R 90** for exact relative horizontal turns.
- Use the vertical **ANGLE — UP / DOWN** slider for arbitrary slope.
- Use **UP 90 / UP 45 / LEVEL / DOWN 45 / DOWN 90** for exact vertical angles.
- Press **START DIGGING / STOP** for continuous excavation.

### VIEW

- In **ORBIT / CT**, drag to orbit and pinch to zoom.
- Select **X / Y / Z** and move the slice slider to inspect the geology like a CT scan.
- **OTHER SIDE** reverses which side of the cut remains visible.
- **SLICE ON / FULL** switches between cutaway and the complete geological volume.
- **FOLLOW + SLICES** centres the orbit camera on the digger and moves all three slice positions with it.
- **DIGGER POV** looks straight out from just behind the cutter along the current heading and slope.
- **RESET VIEW** restores the default orbit/zoom.

### OTHER

- Toggle the on-screen renderer diagnostics.
- **RESET MINE** restores the untouched surface start.

Purple mineralisation remains hidden until first physical contact. After discovery, the connected vein becomes visible where CT slices intersect it.

## Architecture

- `domain/` — renderer-independent geology, chunks, ore body, tunnel geometry, excavation, discovery and material accounting.
- `ui/` — Compose controls, continuous-dig loop and Android lifecycle/view state.
- `ui/render/` — chunk planning/spatial indexing, progressive asynchronous mesh workers, OpenGL ES cameras/VBO cache, clipping, CT caps, follow planning and x-ray machine rendering.

See `docs/THREE_D_WORLD_POC.md` for the current design direction.
