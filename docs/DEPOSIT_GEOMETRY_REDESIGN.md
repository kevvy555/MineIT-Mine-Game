# Deposit Geometry Redesign

Status: Stages 1 and 2 complete; Stage 3 next
Branch: `feature/deposit-geometry-stage2`

## Why this exists

The original ore prototype generated deposits as variable-radius tube paths. Mining then approximated depletion by shrinking the radius of touched stations. That was sufficient for discovery/accounting experiments but was not suitable for MineIT's long-term mining mechanic: a cutter must remove its actual swept 3D volume from a deposit, leaving persistent holes, notches, split remnants and repeated-pass geometry.

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

Stage 1 refinement (0.13.1): remaining-ore presentation is partitioned into aligned 12m render chunks, rebuilt asynchronously on a dedicated ore worker. Cutter passes invalidate only intersected ore chunks; CT continues to use the same canonical remaining-material field. Diagnostics now report rock and ore build costs separately.

## Stage 2 — Realistic deposit archetypes

Goal: replace `ore = tube` with a small set of useful geological forms while retaining seeded reproducibility.

Initial archetypes:

- **Tabular vein / lode** — thin sheet-like body with strike/dip, pinch/swell and controlled waviness. Good initial Gold/Silver form.
- **Lens / massive body** — irregular flattened body that thickens centrally and tapers at the margins.
- **Layered / stratiform body** — laterally extensive mineralised sheet/band, suitable for layered-intrusion style mineralisation.
- **Disseminated / stockwork volume** — broad irregular mineralised zone, a strong initial Copper form and future home for grade variation.

Deposit definitions expose a common scalar/material query rather than requiring the renderer or mining controller to know their shape.

The initial Gold/Silver/Copper mapping is gameplay-oriented rather than claiming every real-world occurrence follows one form.

### Stage 2 implementation contract

The shape abstraction is part of the domain model, not the renderer:

- Each immutable `OreBody` owns one deposit geometry implementation.
- Every deposit geometry exposes the same three pieces of information:
  - a signed material field `margin(point)` where positive means ore, zero is the original ore surface and negative means outside the body;
  - one conservative whole-body AABB for mining broad-phase culling;
  - one or more conservative local planning AABBs so the renderer can keep 12m ore-chunk scheduling local without knowing the archetype.
- Mining classification, discovery, CT sampling, ROCK OFF rendering and remaining-ore meshing call the common field. They do not switch on the archetype.
- The archetype-specific code is limited to immutable geometry definitions and seeded generation.
- Remaining ore stays `min(originalDepositMargin, excavationMargin)`, so every Stage 2 body automatically inherits Stage 1 subtraction.

Initial seeded mapping for the six POC bodies:

- `gold-1`: tabular vein/lode and intentionally positioned to remain easy to encounter from the default starter heading.
- `gold-2`: tabular vein/lode.
- `silver-1`: tabular vein/lode.
- `silver-2`: lens/massive body.
- `copper-1`: disseminated/stockwork volume.
- `copper-2`: layered/stratiform body.

This mapping is deliberately gameplay-first. Later host geology and grade can make commodity/deposit associations more geologically constrained.

Initial parameter envelopes:

- **Gold vein**: roughly 50–85m strike length, 12–22m in-plane width, 1.8–3.6m total thickness, noticeable pinch/swell and sub-metre to ~1m waviness.
- **Silver vein**: roughly 55–95m strike length, 18–30m in-plane width, 2.8–5.2m total thickness, gentler pinch/swell than Gold.
- **Silver lens**: roughly 28–48m long, 18–32m wide and 8–16m thick, with bounded low-frequency surface irregularity.
- **Copper disseminated zone**: roughly 45–75m long, 35–60m wide and 20–36m thick, with low-frequency three-dimensional boundary irregularity.
- **Copper layered body**: roughly 75–130m long, 55–100m wide and 6–12m thick, with broad low-amplitude undulation rather than vein-style pinch/swell.

Generation rules:

- The same seed reproduces the same Stage 2 geology within a given implementation/version.
- Stage 2 does not preserve the exact old tube geometry for the existing seed; the old representation is deliberately replaced.
- Bodies remain below the surface with a conservative minimum ore depth.
- Strike/dip, basis vectors, conservative bounds and local planning regions are generated/precomputed once per immutable deposit.
- Per-sample material queries perform constant-cost arithmetic and do not generate random values or traverse deposit topology.
- Conservative bounds must never cull real ore. False-positive broad-phase overlap is acceptable; false negatives are not.
- Local planning bounds may overlap and may be conservative, but avoid turning a long vein/layer into one large enclosing-box mesh queue.
- Disseminated/stockwork is a continuous mineralised volume in Stage 2. Sparse grade distribution belongs to Stage 4 rather than introducing holes that would falsely imply barren rock at this stage.

Required regression coverage:

- Same seed produces structurally equal geology.
- The default seed contains all four archetypes and two bodies of each ore commodity.
- Representative inside/outside samples prove each archetype's field and conservative bounds.
- Generated bodies obey the minimum ore depth.
- The default starter path still discovers `gold-1`.
- Mining accounting still partitions every newly removed sample exactly once.
- A cutter pass through each archetype produces a true subtractive void while leaving adjacent ore intact.
- Broad-phase mining culling still excludes distant bodies before per-sample field evaluation.
- Ore chunk planning remains local for long veins/layers and does not queue the empty corners of one whole-body AABB.

Performance acceptance for Stage 2:

- A material-field query is constant-cost with no topology traversal proportional to deposit length.
- Mining continues to broad-phase by conservative body bounds before evaluating the field.
- Render planning uses generic local planning bounds rather than archetype knowledge.
- Stage 2 does not reintroduce the large ore-mesh queues or main-thread material-classification work removed in 0.13.3.

### Stage 2 completion — 0.14.0

Stage 2 is implemented on `feature/deposit-geometry-stage2`:

- the tube/node representation has been removed from canonical ore state;
- all four archetypes implement the shared `OreDepositGeometry` field/bounds contract;
- the six seeded POC bodies use the mapping above;
- strike/dip basis, whole-body bounds and local planning bounds are precomputed per immutable geometry;
- irregular lens/stockwork bodies use conservative padding so boundary noise cannot escape broad-phase bounds;
- mining classification and discovery are shape-agnostic;
- CT, discovered-ore meshes, ROCK OFF and tunnel-wall ore colouring read the same domain material field;
- ore chunk planning consumes generic local planning bounds rather than tube segments;
- regression coverage proves deterministic generation, all four archetypes, minimum depth, starter discovery, accounting, broad-phase culling, local chunking and cutter subtraction across every archetype.

Exit criteria: satisfied once the final 0.14.0 Android CI run is green. Stage 3 is the next planned geology step.

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

Not part of Stage 1 or Stage 2: processing/refining, commodity pricing, detailed rock mechanics, faults/folding, explosives, support systems, ore dilution, recovery percentage, haulage, sparse grade distribution, or a full grade/economy UI. These can use the new geometry later without blocking the physical mining model now.
