# Prototype production plan

Goal: a playable test world where a player starts at LV with materials handed out and builds
the mechanical component chain up to HV, using the new single-block machines, the four
upgradeable multiblocks and their frames, segments, modules and retrofit kits.

Scope is set by the design files, which are the source of truth:
- `design/slice1-mechanical.yaml`: items, machines, recipes, decisions
- `design/multiblocks.yaml`: framework (structure, hatches, frames, segments, modules, retrofit),
  the four multiblocks, their build recipes, and the overlap decision on stock Large multiblocks
- `design/sim/`: the simulator; its math is the reference for the addon's bonus math
- `docs/spike-1.md`: verified GTM 7.5.3 API usage and gotchas (also summarised in CLAUDE.md)

Out of scope for the prototype: quests, skyblock, ore processing, steam age, real textures
(placeholders are fine), and research (superseded).

All five phases run in one go, in order, on one branch. Don't wait for approval between
phases. A phase is finished when its "done when" passes in CI and an independent review agent
has checked it (CLAUDE.md, Workflow); then commit and move straight to the next phase.

Keep `docs/progress.md` up to date as you go: which phase you're in, what's done, decisions made
and why, in plain language. If the session ends or restarts, resume from it.

Stop and ask me only for: a network host that is blocked, a gameplay or design question the
design files don't answer, or something irreversible. Everything else, decide and record it in
`docs/progress.md`.

When all phases are done, open one pull request. Its description summarises what was built, in
plain language, and points me to `docs/prototype-test.md`.

## Phase 1: repository and pack skeleton
- Repository layout: `pack/` (packwiz), `addon/` (Java addon, fresh project; the spike code is
  reference only), `tools/` (generators and validator), `design/`, `docs/`.
- packwiz pack for Forge 1.20.1 with GTM 7.5.3 and its required dependencies, KubeJS and GTM's
  KubeJS integration, AE2, one recipe viewer (JEI or EMI), Jade, and a small set of performance
  mods. Follow CLAUDE.md's rules for adding mods; list every mod with version, source and reason
  in `docs/mods.md`.
- Addon project from the official GTM addon template, building against GTM 7.5.3, with the
  GameTest setup from the spike.
- CI: GitHub Actions that builds the addon, runs GameTests, runs the simulator tests
  (`node design/sim/test.js`) and the validator.
- Done when: the addon builds, an empty GameTest run passes in CI, and the pack exports.

## Phase 2: data pipeline and validator
- `tools/` generator that reads the design YAML files and writes:
  - KubeJS server scripts: changed recipes (MV and HV motor), new recipes (stator coils,
    bearings), builds (casings if not stock, cores, controllers, retrofit kits, modules), and
    removal and hiding of `large_wiremill`, `large_assembler` and `large_cutter`.
  - Data pack JSON the addon reads: frames, segment caps, module definitions, multiblock specs.
  - Test cases for the addon's bonus math, computed with the simulator core so the two can't drift.
- Validator: every item referenced exists or is supplied, stock recipes still match GTM 7.5.3,
  recipe conflicts are listed (and must be solvable by circuits or distinct buses), recipe
  input counts fit the buses at their tier. Fails CI on errors.
- Generated files are committed and marked "generated, do not edit".
- Done when: regenerating from unchanged YAML produces no diff, and the validator passes.

## Phase 3: addon content without multiblocks
- Items: stator coils, precision bearing, the four core blocks, retrofit kits, the three modules.
- Single-block machines: coil winder (LV, MV, HV) and bearing press (MV, HV) with their recipe
  types, defined in Java through GTM's machine and recipe type APIs.
- Their recipes come from Phase 2's generated KubeJS, not Java.
- Placeholder textures and lang entries.
- GameTests: each machine runs one of its recipes at the right voltage and duration.

## Phase 4: the upgradeable multiblock framework and the four multiblocks
- One base class for everything in `multiblocks.yaml`'s framework: the structure (front aisle,
  repeatable segments with a core block, end aisle with frame-box corners), hatch limits,
  frame detection (all casings and frame boxes must match), voltage capped at the frame,
  counted segments = min(built, cap), modules in controller slots, specialization lock,
  retrofit by right-clicking with kits, and the recipe filters (component / not component).
- Frames, caps and module effects are read from Phase 2's data pack JSON, never hard-coded.
- Bonuses are computed when the structure forms or a module changes, never per recipe.
- The four multiblocks as thin subclasses or definitions on top of the base.
- GameTests: every frame and length from 1 to the largest cap; over-cap structures form and run
  at the cap; limited hatches in the end aisle at every length (the spike's GTM bug); module
  slot limits; retrofit up and down returns the old casings; bonus math matches the generated
  test cases; structure check time measured at 16 and 32 segments and reported.

## Phase 5: prototype world and manual test pass
- A test-world setup: on first join, the player gets an LV starter kit (ingots, rubber, circuits
  and the other supplied items in `slice1-mechanical.yaml`), plus a creative power option for
  quick tests and a real LV-HV power setup for realistic ones. A one-time server script is fine
  here; it is not a hot path.
- `docs/prototype-test.md`: a manual checklist covering building each multiblock, upgrading
  frames with kits, adding segments, modules, AE2 feeding in non-blocking mode with distinct
  buses, and producing the example goal from the simulator (4 each of HV robot arms, conveyors
  and pistons), noting how the in-game time compares with the simulator's estimate.
- Done when: I can play through the checklist on my own computer.

## After the prototype
Balance pass with the simulator and in-game notes, then decide what milestone 1 needs next
(rubber chain, power, steam age and skyblock start, quests).
