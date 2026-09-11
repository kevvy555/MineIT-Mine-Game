# MineIT Mine Game — Current Game Direction

## Core idea

MineIT Mine Game is a single-mine management and development game set in the MineIT / Koplin universe.

The mine is no longer conceived as a side-on 2D world or a stack of independent 2D levels. The canonical mine model is now a **continuous 3D geological volume**.

The player gradually reveals and reshapes that volume by physically excavating shafts, drifts, ramps and later stopes. The mine itself becomes the game board.

## 3D geology as the source of truth

The underground world uses real X/Y/Z coordinates. Rock, ore bodies and excavation all occupy continuous 3D space.

The important consequences are:

- ore veins can dip, curve, branch, narrow and widen through all three axes;
- shafts and tunnels can be driven in arbitrary directions instead of snapping to square tiles;
- tunnel diameter and shape can vary by machine or excavation method;
- mined-out space is actual empty volume;
- the amount and composition of removed material can be derived from the same geometry that the player sees;
- mine infrastructure can later use the same world coordinates for haulage, ventilation, workers and machinery.

The visible rock volume expands as the player develops beyond its current limits. Internally the world is chunked so the game does not need to hold an unlimited mine in one monolithic mesh.

## Discovery

Intact rock hides its geology. Rotating or slicing the mine must not magically reveal unknown ore.

Ore becomes known through actions that genuinely expose or investigate it, for example:

- a tunnel wall intersects an ore body;
- shaft sinking exposes mineralisation;
- exploration drilling samples material ahead of the workings;
- later survey technology may provide uncertain/inferred geological information.

Confirmed exposed ore and inferred continuation should remain visually distinct.

The player should be able to rotate the geological volume freely and move cutting planes through X, Y and Z to inspect known mine geometry from any useful angle.

## Excavation and material conservation

Rock does not disappear when mined.

Every excavation removes a real volume. That volume produces material based on rock density and composition. Early gameplay can treat ordinary material as waste rock; later the same event can split removed material into ore, waste and other substances.

This creates the eventual logistics chain:

1. excavation creates broken material;
2. material accumulates at the face;
3. loaders, conveyors or haulage move it through the mine;
4. shafts/ramps move material toward the surface;
5. ore and waste are separated into their appropriate surface flows.

The geometry and the material accounting should never become separate contradictory systems.

## Rendering direction

The mine simulation must not depend on one renderer.

The domain owns:

- chunk extents;
- geological fields;
- ore bodies;
- excavation paths/volumes;
- known/unknown geology;
- removed material quantities;
- mine connectivity when that system is introduced.

The renderer consumes that data and builds disposable visual meshes.

The 0.3 POC deliberately uses a lightweight custom OpenGL ES renderer rather than adopting a full game engine immediately. This tests whether MineIT can keep its native Compose management UI while using a purpose-built 3D mine renderer.

If a different renderer is adopted later, the mine model should not need to be rewritten.

## Interaction model

The intended inspection interaction is:

- one-finger drag rotates the mine freely;
- pinch zooms in/out;
- X/Y/Z cutting planes move through the rock volume;
- cutting direction can be reversed;
- the full uncut geological volume can be restored instantly;
- future tools may add arbitrary-angle section planes and focus/isolate controls.

Excavation is separate from camera manipulation. The player chooses an excavation heading/dip and advances a mining face through actual rock.

## Long-term mining gameplay

The current 3D foundation should eventually support multiple real mining methods rather than forcing every deposit into one pattern.

Examples include:

- shaft sinking and horizontal development;
- narrow-vein mining;
- cut-and-fill;
- long-hole/open stoping;
- room-and-pillar where geology suits it;
- large-scale late-game methods such as block caving.

The correct method should depend on ore-body geometry, grade, depth, ground conditions and economics.

## Immediate POC goal

Version 0.3 is not trying to be the finished game. It is proving the underlying world concept.

Success means we can:

1. represent an expandable 3D rock volume;
2. rotate it from any useful angle;
3. slice it in X/Y/Z;
4. carve smooth arbitrary tunnels rather than square blocks;
5. embed a curved 3D ore body;
6. reveal ore only where excavation exposes it;
7. extend a tunnel through all three axes;
8. expand the geological world as excavation reaches an edge;
9. derive excavated volume and waste-rock tonnage from the mine geometry.

Once this works convincingly, the next major layer is physical material handling: broken rock at the face, haulage and surface disposal.
