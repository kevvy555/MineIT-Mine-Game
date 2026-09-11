# 3D Geological World POC — 0.3.0

## Purpose

This POC replaces the Mine Prism experiment with the first implementation of the intended long-term mine representation: a continuous, expandable 3D geological volume.

The goal is to validate both the player interaction and the technical split between a renderer-independent mine simulation and a lightweight native 3D renderer.

## Architecture

### Domain

`domain/` owns the actual mine:

- X/Y/Z coordinates;
- chunk extent and world bounds;
- tunnel geometry;
- ore-body geometry;
- excavation direction;
- automatic extent expansion;
- exposed-ore detection;
- excavated volume;
- waste-rock tonnage.

No Android/OpenGL types are permitted in this layer.

### Mesh generation

The visual rock is generated from a scalar solid/air field.

A point is solid only when it is:

- inside the current geological bounds; and
- outside every excavated tunnel volume.

The POC samples this field on a fine grid and polygonises each cube through marching tetrahedra. This produces smooth triangle surfaces around both the exterior geological block and the excavated tunnel walls without showing square voxel blocks.

The grid is an implementation detail, not the visible gameplay geometry.

### Ore

The ore body is a continuous tube-like field interpolated through 3D control nodes with varying radius.

Ore is deliberately **not rendered as a transparent hidden object inside intact rock**. Instead, generated tunnel-wall triangles are classified by the underlying ore field. Only excavated surfaces that intersect mineralisation are coloured as exposed ore.

This preserves discovery: slicing the rock cannot be used to reveal ore that mining has not exposed.

### Renderer

Compose remains responsible for the normal Android UI. A `GLSurfaceView` is embedded for the 3D viewport.

OpenGL ES currently provides only the generic graphics work:

- perspective camera;
- depth testing;
- triangle rendering;
- simple lighting;
- X/Y/Z clipping planes.

The renderer has no authority over mine rules.

## Current interaction

- drag: orbit the 3D mine;
- pinch: zoom;
- X/Y/Z buttons: choose geological cutting axis;
- slice slider: move the cutting plane;
- CUT + / CUT -: reverse which side is removed;
- CUT ON / FULL: enable/disable slicing;
- Azimuth: excavation compass direction;
- Dip: excavation up/down angle;
- DIG +8m: add a new arbitrary 3D tunnel segment;
- VIEW: reset camera;
- RESET: restore the starting shaft.

## World expansion

The geological volume is divided conceptually into 24 m chunks. The current POC maintains a contiguous chunk extent.

When the active tunnel approaches an X, Y or Z edge, another chunk is added in that direction before excavation reaches the boundary. The visible rock volume therefore grows with the mine rather than being a permanently fixed cube.

A future implementation can replace the simple contiguous extent with sparse streamed chunks without changing the game-facing model.

## Material accounting

For the POC, each new tunnel segment adds its swept cylindrical volume to the excavated total.

Waste rock is calculated using a provisional bulk rock density of 2.7 tonnes/m³.

This is intentionally approximate where excavation volumes overlap. A later occupancy/material field will make removed-volume accounting authoritative and prevent previously mined void from being counted twice.

## Deliberate non-goals

0.3 does not yet include:

- broken-rock piles;
- loaders/trucks/conveyors;
- shaft hoisting;
- workers;
- power;
- ventilation;
- detailed geological rock types;
- drilling/survey uncertainty;
- arbitrary tunnel cross-sections;
- stopes;
- saving/loading;
- sparse chunk streaming;
- advanced lighting/textures.

## What to evaluate

The important questions for this build are:

1. Does freely rotating and slicing the mine make the 3D geology understandable on a phone?
2. Do smooth tunnels feel substantially better than visible voxel/block excavation?
3. Does discovering ore on physical tunnel walls make intuitive sense?
4. Does steering with azimuth/dip make it believable that excavation can go anywhere in X/Y/Z?
5. Does automatic geological-volume expansion feel like the mine is growing rather than moving inside a fixed level?
6. Is the custom OpenGL approach lightweight enough to continue, or would a renderer such as Filament/Godot provide enough benefit to justify adoption?
