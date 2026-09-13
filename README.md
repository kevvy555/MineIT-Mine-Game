# MineIT Mine Game

Native Android mining-game prototype in the MineIT universe.

## Current prototype — 0.13.0

The mine is a genuine **3D geological volume** with renderer-independent gameplay rules and a lightweight native OpenGL renderer.

0.13 changes ore from radius-depleted tube geometry to true subtractive solid geometry. Seeded deposits remain immutable; remaining ore is derived from the original deposit minus the actual swept excavation volume, so mining leaves cutter-shaped holes rather than making a vein uniformly thinner.

- the cutter partitions each newly excavated volume into **ore or waste rock exactly once**;
- `excavated volume = waste-rock volume + mined-ore volume`;
- ore volume is accumulated separately for **Gold, Silver and Copper**;
- waste tonnage is calculated only from waste-rock volume, so mined ore is not double-counted as waste;
- samples already inside an existing tunnel are ignored, so crossing an old working cannot produce material a second time;
- original seeded deposits remain immutable; a signed remaining-material field subtracts the canonical tunnel volume and supports holes, notches and disconnected remnants;
- normal discovered-vein rendering, ROCK OFF and SEE ORE CT sections all use the same remaining ore geometry;
- tri-planar X/Y/Z CT inspection remains persistent and simultaneous, with ore clipped to generated geology;
- seeded Gold/Silver/Copper geology, pan/orbit/zoom, follow, DIGGER POV, speed control and fixed heading/angle controls remain available;
- the mobile renderer retains the global-shell + tunnel-chunk architecture that has held approximately 90 FPS on a Pixel 7 in POC testing.

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
- **ROCK OFF** shows the excavation/ore inspection view while retaining grass as the surface reference.
- **TUNNEL ON/OFF** controls only the direct tunnel overview skin in ROCK OFF, allowing depleted ore to be inspected without changing excavation state or accounting.
- In **ORBIT / CT**, drag to orbit and pinch to zoom; switch **PAN** on to move the camera instead.
- X, Y and Z each keep an independent CT position and cut-side setting and can be enabled simultaneously for tri-planar inspection.
- **SEE ORE** reveals all remaining seeded deposits only where active CT planes intersect them.
- **FOLLOW** centres the orbit camera on the digger and moves all three slice positions with it.
- **DIGGER POV** looks straight out from just behind the cutter along the current heading and slope.

### OTHER

- Toggle the on-screen renderer diagnostics.
- The material readout shows cumulative mined Gold, Silver and Copper volumes.
- **RESET MINE** restores the untouched surface start and original seeded ore bodies.

In normal play ore remains hidden until first physical contact. Once a deposit is discovered, its connected **remaining** body can be inspected; material already cut by the machine is no longer present.

## Architecture

- `domain/` — renderer-independent geology, chunks, typed ore bodies, tunnel geometry, excavation, material partitioning, ore depletion, speed and discovery.
- `ui/` — Compose controls, continuous-dig loop and Android lifecycle/transient view state.
- `ui/render/` — chunk planning/spatial indexing, asynchronous mesh workers, OpenGL ES cameras/VBO cache, global shell, tri-planar clipping/CT faces, ore presentation, direct tunnel overview and x-ray machine rendering.

See `docs/THREE_D_WORLD_POC.md` for the current design direction.
