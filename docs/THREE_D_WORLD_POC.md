# 3D Geological World POC — 0.4.0

## Purpose

This POC advances the continuous 3D mine into the interaction model intended for the game: a solid geological volume that can be rotated, sliced and excavated while preserving discovery.

The main design goal for 0.4 is to make the mine read as **solid rock**. The player should feel as though they are cutting through geology like a CT scan, not looking at a tunnel floating inside a transparent box.

## Architecture

### Domain

`domain/` owns the actual mine:

- X/Y/Z coordinates;
- chunk extent and world bounds;
- tunnel geometry;
- ore-body geometry;
- continuous excavation state;
- horizontal steering;
- vertical excavation angle;
- automatic extent expansion;
- exposed-ore detection and connected-body discovery;
- excavated volume;
- waste-rock tonnage.

No Android/OpenGL types are permitted in this layer.

### Mesh generation

The visual rock is generated from a scalar solid/air field. A point is solid only when it is inside the geological bounds and outside excavated tunnel volume.

The field is sampled on a grid and polygonised with marching tetrahedra. The grid remains an implementation detail; visible tunnels are smooth triangle surfaces rather than block voxels.

### Solid CT slice caps

OpenGL clipping alone creates a hollow-shell appearance because removed triangles leave an open volume. 0.4 adds a generated **cut cap** at the active X, Y or Z plane.

The cap samples the same authoritative solid/air field:

- solid cells draw rock;
- excavated tunnel cells remain open holes;
- the surface edge can draw grass;
- after discovery, cells inside the connected ore body draw mineralisation.

This gives the sliced block a physically solid cross-section.

### Ore discovery

The ore body is a continuous tube-like field interpolated through 3D control nodes with varying radius.

Before first contact, the vein is hidden and cannot be revealed merely by moving a slice plane. Once any excavation intersects the connected ore body, that body becomes discovered. From then on, geological slice caps show the ore wherever the current X/Y/Z plane intersects the known connected vein.

This makes first contact a meaningful discovery event while allowing the player to inspect the deposit and plan how to mine it.

### Surface and mining machine

The initial mine is untouched solid rock with a grass-covered surface at Z=0. A simple 3D mining machine begins on that surface, aligned with the starting heading and down-angle.

The machine is renderer geometry only. Its authoritative position and orientation come from domain tunnel/heading state.

### Continuous digging

The fixed `DIG +8m` command has been removed.

The player now starts and stops excavation. While digging, the domain advances the machine on timed simulation ticks:

- **Steer LEFT/RIGHT** controls turn rate around the horizontal plane;
- **Angle UP/DOWN** controls vertical excavation angle;
- the angle UI is intentionally vertical to match the physical meaning;
- the machine cannot start level/upward from the surface;
- upward excavation from underground stops when it exits back through the surface;
- each movement segment expands the tunnel geometry and material accounting.

The X/Y/Z inspection slices remain independent of digging and can be moved while excavation continues.

## Renderer

Compose remains responsible for the Android UI. `GLSurfaceView` provides the 3D viewport.

OpenGL ES provides generic graphics work only:

- perspective camera;
- orbit rotation and pinch zoom;
- depth testing;
- triangle rendering;
- X/Y/Z clipping;
- simple lighting.

Generated meshes currently include:

- geological/tunnel surface mesh;
- CT cut-cap mesh;
- simple mining-machine mesh.

The renderer has no authority over gameplay rules.

## World expansion

The geological volume grows in 24 m chunks when excavation approaches an X, Y or Z boundary. The current model keeps a contiguous extent; this can later become sparse streamed chunks without changing the player-facing concept.

## Material accounting

Each new tunnel segment adds its swept cylindrical volume to the excavated total. Waste rock currently uses a provisional density of 2.7 tonnes/m³.

Overlap is still approximate. A later occupancy/material field will make removed-volume accounting authoritative and prevent double-counting previously excavated void.

## Deliberate non-goals

0.4 still does not include:

- broken-rock piles or removal logistics;
- loaders/trucks/conveyors;
- shaft hoisting;
- workers;
- power;
- ventilation;
- multiple rock/mineral types;
- survey uncertainty;
- arbitrary machine/tunnel profiles;
- stopes;
- save/load;
- sparse chunk streaming;
- advanced lighting/textures.

## What to evaluate

1. Does the generated cut face make the volume finally feel like solid rock?
2. Does rotating while changing X/Y/Z slices feel like inspecting a geological CT scan?
3. Is starting the machine at the grass surface spatially understandable?
4. Are **LEFT/RIGHT** steering and the vertical **UP/DOWN** angle control intuitive without mining terminology?
5. Does continuous start/stop digging feel better than discrete excavation steps?
6. Is revealing the connected vein after first contact a satisfying and useful discovery mechanic?
7. Does the custom OpenGL/mesh approach still perform well enough on the target phone as continuous excavation updates the geometry?
