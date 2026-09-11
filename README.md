# MineIT Mine Game

Native Android mining-game prototype in the MineIT universe.

## Current prototype — 0.3.0

The game has moved from the 2D/2.5D Mine Prism experiment to a genuine **3D geological volume**.

The current POC is deliberately testing the technical/gameplay foundation rather than a full economy:

- the mine exists as true X/Y/Z data;
- a continuous rock volume is converted into smooth triangle geometry;
- the player can freely rotate the mine and pinch to zoom;
- X, Y and Z cutting planes can be moved through the rock to inspect the interior;
- the cut direction can be flipped or disabled to show the full volume;
- the starting shaft and every new excavation are represented as real 3D tunnel volumes rather than square tiles;
- tunnel diameter is continuous and tunnel turns form rounded joins;
- a curved 3D ore body exists inside the geology;
- ore is only coloured where an excavated tunnel wall actually exposes it;
- the player can steer excavation with azimuth and dip and dig another 8 m segment;
- the geological volume expands in 24 m chunks as excavation approaches an edge;
- excavated cubic metres and resulting waste-rock tonnes are tracked.

The renderer is intentionally lightweight: Jetpack Compose owns the mobile UI and an embedded OpenGL ES renderer draws the 3D mine. The geological world and excavation rules remain renderer-independent domain data.

## Controls

- Drag the 3D view to rotate it.
- Pinch to zoom.
- Select X, Y or Z and move the slice slider to cut through the geological volume.
- Use **CUT + / CUT -** to choose which side of the plane is removed.
- Use **CUT ON / FULL** to switch between a sliced view and the complete rock volume.
- Set tunnel **Azimuth** and **Dip**, then press **DIG +8m**.
- **VIEW** resets the camera; **RESET** resets the mine.

Purple material is ore that the current excavation has physically exposed. Intact hidden ore is not revealed by the inspection view.

## Architecture

- `domain/` — renderer-independent geology, chunks, ore body, tunnel geometry, excavation and material accounting.
- `ui/` — Compose controls and Android lifecycle/view state.
- `ui/render/` — OpenGL ES camera, clipping and generated triangle mesh.

See `docs/THREE_D_WORLD_POC.md` for the current design direction.
