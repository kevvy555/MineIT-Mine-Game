# MineIT Mine Game

Native Android mining-game prototype in the MineIT universe.

## Current prototype — 0.4.0

The mine is a genuine **3D geological volume** with a lightweight native OpenGL renderer and renderer-independent domain rules.

The current POC is focused on making the underground world feel like solid geology rather than a tunnel floating inside an empty box:

- the mine exists as true X/Y/Z data;
- rock is generated from a continuous solid/air field and meshed into smooth triangles;
- the player can freely rotate the mine and pinch to zoom;
- X, Y and Z slices now generate a **solid geological cut face**, so slicing reads like a CT scan rather than a hollow clipped shell;
- tunnel voids appear as holes through that solid cut face;
- the surface is a grass-covered geological boundary;
- the mining machine starts on the surface and remains visible in the real 3D world;
- digging is continuous with **START DIGGING / STOP** rather than fixed 8 m jumps;
- horizontal steering is expressed as **LEFT / RIGHT**;
- the vertical angle control is a real vertical **UP / DOWN** slider;
- steering while digging creates curved 3D excavation;
- a connected curved ore vein remains hidden until excavation first intersects it;
- once discovered, that connected vein is shown wherever the X/Y/Z CT slice crosses it;
- the geological volume expands in 24 m chunks as excavation approaches an edge;
- excavated cubic metres and resulting waste-rock tonnes are tracked.

The renderer remains intentionally lightweight: Jetpack Compose owns the mobile UI and an embedded OpenGL ES renderer draws the 3D mine. The geological world, excavation, discovery and material accounting remain renderer-independent domain data.

## Controls

- Drag the 3D view to rotate it.
- Pinch to zoom.
- Select X, Y or Z and move the slice slider to move the geological cut plane.
- **OTHER SIDE** reverses which side of the cut is removed.
- **SLICE ON / FULL** switches between a CT-style cutaway and the complete geological volume.
- Use **STEER — LEFT / RIGHT** while digging to curve the tunnel horizontally.
- Use the vertical **ANGLE — UP / DOWN** slider to climb or descend.
- Press **START DIGGING** to move continuously and **STOP** to halt excavation.
- **RESET VIEW** resets the camera; **RESET MINE** restores the untouched surface start.

Purple mineralisation is hidden until first physical contact. After discovery, the connected vein becomes available for geological inspection through the slice views.

## Architecture

- `domain/` — renderer-independent geology, chunks, ore body, tunnel geometry, continuous excavation, discovery and material accounting.
- `ui/` — Compose controls, continuous-dig loop and Android lifecycle/view state.
- `ui/render/` — OpenGL ES camera, clipping, CT cut-cap generation, machine mesh and generated geological triangle mesh.

See `docs/THREE_D_WORLD_POC.md` for the current design direction.
