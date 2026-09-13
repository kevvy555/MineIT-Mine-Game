# Deposit Geometry Redesign

Status: Stage 1 in progress
Branch: `feature/deposit-geometry-redesign`

## Why this exists

The current ore prototype generates deposits as variable-radius tube paths. Mining then approximates depletion by shrinking the radius of touched stations. That is sufficient for discovery/accounting experiments but is not suitable for MineIT's long-term mining mechanic: a cutter must remove its actual swept 3D volume from a deposit, leaving persistent holes, notches, split remnants and repeated-pass geometry.

The redesign separates four concepts:

1. **Original geology** — immutable seeded deposit definitions.
2. **Excavation** — canonical removed 3D volume produced by mining tools.
3. **Remaining material** — original deposit minus excavation.
4. **Presentation** — disposable meshes/CT sections generated from the remaining-material field.

The domain remains the source of truth. Render meshes are always rebuildable from domain state.

## Stage 1 — True subtractive deposit geometry

Goal: make excavation physically subtract from ore without mutating the original deposit definition.

- Keep seeded deposit definitions immutable for the lifetime of a mine.
- Remove radius-shrink depletion as canonical state.
- Introduce one domain query for remaining ore at any point: `originalDeposit(point) - excavation(point)`.
- Reuse the existing swept tunnel volume as the first excavation primitive.
- Preserve the accounting invariant: every newly removed sample is exactly one of waste rock or one ore deposit, never both.
- Revisiting already excavated space must produce zero new material.
- Render discovered ore from the remaining-material scalar field so a cutter leaves a true cutter-shaped hole.
- Build CT ore from the same remaining-material field so CT, ROCK OFF and normal discovered-ore views agree.
- Remesh only when excavation changes ore, not merely because the camera/view changes.
- Add domain/render regressions proving a central cutter pass creates an empty cylindrical channel while ore remains on either side.

Exit criteria: the current screenshot case shows a literal tunnel-sized bite through the deposit rather than a thinner vein.

## Stage 2 — Realistic deposit archetypes

Goal: replace `ore = tube` with a small set of useful geological forms while retaining seeded reproducibility.

Initial archetypes:

- **Tabular vein / lode** — thin sheet-like body with strike/dip, pinch/swell and controlled waviness. Good initial Gold/Silver form.
- **Lens / massive body** — irregular flattened body that thickens centrally and tapers at the margins.
- **Layered / stratiform body** — laterally extensive mineralised sheet/band, suitable for layered-intrusion style mineralisation.
- **Disseminated / stockwork volume** — broad irregular mineralised zone, a strong initial Copper form and future home for grade variation.

Deposit definitions should expose a common scalar/material query rather than requiring the renderer or mining controller to know their shape.

The initial Gold/Silver/Copper mapping can be gameplay-oriented rather than claiming every real-world occurrence follows one form.

Exit criteria: a seed can generate visibly distinct vein, lens, layered and disseminated bodies, all of which can be cut by the same Stage 1 excavation system.

## Stage 3 — Lightweight host-rock geology

Goal: give deposits geological context without building a full geology simulator prematurely.

- Add a small number of host-rock regions/layers to the underground world.
- Start with deterministic layer/region identity, colour and metadata.
- Do not initially add a large hardness/economy simulation unless required by gameplay.
- Deposit generators may constrain themselves to contacts, layers or host regions.
- Keep host geology and deposits separate: host rock explains where a deposit occurs; it is not itself the ore deposit.

Possible first simple sequence: sedimentary layers over basement, with thickness/attitude varied by seed.

Exit criteria: CT/world views can show a believable layered host volume and deposit generators can reference it.

## Stage 4 — Intrusions, relationships and grade

Goal: move from independent shapes to a small believable geological system.

- Add intrusive host bodies, with **layered intrusion** as the first target.
- Add laccolith-style intrusive bodies as host geology where useful; a laccolith is not treated as an ore deposit by itself.
- Allow deposits/mineralised zones to be generated relative to contacts/intrusions.
- Introduce grade/composition fields so a deposit can contain spatially varying metal concentration rather than binary ore/no-ore only.
- Keep UI simple initially: grade can exist in domain data before detailed processing/economics are exposed.

Exit criteria: a generated mine can contain host layers/intrusions and mineralisation whose shape/grade is related to that geology.

## Architecture rules

- `domain/` owns original deposits, host geology, excavation/material classification and remaining-material queries.
- `ui/render/` owns scalar-field sampling, meshing, CT presentation and caches only.
- Do not store renderer meshes as gameplay state.
- Do not mutate an original deposit to represent mining; excavation is a separate subtraction input.
- New mining tools should add/compose excavation primitives rather than special-case every deposit type.
- Prefer bounded/local remeshing and deterministic seeds for mobile performance and reproducibility.

## Deferred deliberately

Not part of Stage 1: processing/refining, commodity pricing, detailed rock mechanics, faults/folding, explosives, support systems, ore dilution, recovery percentage, haulage, or a full grade/economy UI. These can use the new geometry later without blocking the physical mining model now.
