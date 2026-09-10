# Mine Prism POC

## Status

**Current playable direction — POC 0.2.0.**

The original side-on steerable-digger prototype is superseded. Git history preserves it, but it is no longer production code.

## Problem being tested

A real underground mine is three-dimensional. Ore bodies can move horizontally, dip vertically, branch and pass through many working horizons. A full free-camera 3D game is realistic, but on a phone it risks making the player fight occlusion and camera controls instead of making mining decisions.

The Mine Prism attempts a different answer:

> **Keep the mine genuinely 3D underneath, but let the player manipulate it through a geological prism that can be peeled, exploded and unfolded into readable 2D working views.**

## POC interaction

### Geological prism

The default view is an isometric block of ground. The prism contains:

- a surface reference plane;
- a vertical main shaft;
- three working levels;
- a continuous ore body whose `x`, `y` and depth position changes as it descends.

This is not three unrelated floor plans. The level views are slices through the same 3D mine model.

### Peel by depth

A depth slider removes the conceptual rock above the selected depth.

As the player moves deeper:

1. more of the shaft is exposed;
2. the 3D ore body is progressively revealed;
3. working levels appear when the slice reaches their elevation.

The intended future use is surveying and geological understanding as well as navigation.

### Exploded levels

**Explode Levels** separates visible working horizons vertically for readability. It is an engineering-diagram presentation of the same mine rather than a different world state.

The long-term version should make vertical connections — shaft, raises, ore passes, ramps and ventilation — especially easy to understand in this mode.

### Unfold a level

Tapping a revealed level opens it as a top-down operational view.

The POC shows:

- shaft station;
- developed drifts/crosscuts;
- two simple face markers;
- the horizontal trace where the 3D ore body intersects that level.

The intended mature interaction is that detailed mining, haulage, support, ventilation and crew decisions happen in this readable working view, while strategic depth/geology decisions happen in the Prism.

### Navigation

- drag to pan;
- pinch to zoom;
- scrub the depth slider;
- tap a revealed level;
- return to the Prism;
- explode/collapse the mine.

## Underlying model

The POC deliberately stores mine geometry in three axes:

- `x`
- `y`
- `depthMetres`

That is an architectural decision, not merely a visual effect. Future veins, shafts, ramps, stopes and drill results can therefore occupy real 3D positions even if most gameplay remains on readable projections.

## What this POC is not

Do not judge this build as a finished mining game. It does not yet simulate:

- excavation;
- waste rock;
- ore extraction;
- mining methods;
- machinery;
- workers;
- lifts/skips;
- haulage;
- power;
- ventilation;
- ground support;
- contracts;
- economics;
- persistence.

Those should only be layered on after the Prism interaction proves understandable and enjoyable.

## Questions the POC should answer

1. Does the prism make mine depth and level relationships understandable on a phone?
2. Does peeling through depth feel natural?
3. Does the continuous ore body communicate that geology is genuinely 3D?
4. Is exploding levels useful rather than gimmicky?
5. Does tapping a level and switching to top-down feel like one coherent mine rather than two unrelated screens?
6. Can this become the signature interface for MineIT Mine Game?

## Likely next step if the POC works

The first real gameplay system should be **shaft development and material removal**.

The player would extend the shaft toward the first working depth, and every excavated volume would create waste rock that must be removed rather than disappearing. Reaching the first level would then unlock horizontal development from the shaft station.

The important difference from the old prototype is that excavation would now modify the same 3D mine model used by the Prism, rather than being a purely side-on drawing mechanic.
