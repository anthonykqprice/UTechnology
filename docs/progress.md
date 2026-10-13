# Prototype progress

Plan: `docs/prototype-plan.md`. This file says where the work is and why things were decided, in plain
language. If a session restarts, resume from "Current state".

## Current state
- Phase 1 (repository and pack skeleton): done. Reviewed; fixes applied (JEI pinned to its newest
  release, CI actions updated and the runner pinned, sturdier scripts, changelog). CI green.
- Phase 2 (data pipeline and validator): done. Reviewed; the review's findings are fixed (see "Phase 2
  review" below). Committed on its own so CI checks it.
- Phase 3 (items and single-block machines): done. Reviewed; fixes applied.
- Phase 4 (upgradeable multiblocks): in progress.
- Branch: `claude/gifted-hawking-pmc94i` (one branch for all five phases, one PR at the end).

## How to run things (from the repository root)
| What | Command |
|---|---|
| Simulator tests | `node design/sim/test.js` |
| Design checks (stale generated files, validator) | `tools/check.sh` |
| Addon build + GameTests | `cd addon && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --max-workers=1 build runGameTestServer` |
| Regenerate everything from design/ | `python3 tools/generate.py` then `python3 tools/validate.py` |
| Regenerate the addon's models, blockstates and lang | `tools/run-datagen.sh` (set `GRADLE_ARGS=--max-workers=1` in this VM) |
| Pack export (with addon jar) | `tools/export-pack.sh addon/build/libs/utechnology-0.1.0.jar UTechnology.mrpack` |

In this cloud VM, Gradle must use Java 17 (the default `java` is 21) and `--max-workers=1` (Maven Central
rate-limits parallel downloads through the proxy). CI on GitHub doesn't need either.

## Decisions

### Before phase 1 (gameplay answers from Anthony, 2026-10-13)
Recorded in `design/multiblocks.yaml` → `decisions`:
- **Efficiency coil** saves 25% energy per craft: EU/t ×0.6 and duration ×1.25. The simulator's data
  (`energy: 0.6`) and test were updated; the simulator code didn't need to change.
- **Fluid hatches on every multiblock** (0 to 2), so the batch bus can get lubricant on any machine.
- **Steel retrofit kit** added (24 steel plates + 4 steel frames), so machines can be downgraded to steel.

### Phase 1
- **Layout**: `pack/` (packwiz), `addon/` (Java), `tools/` (scripts), `design/`, `docs/`. The spike's
  folder was removed once the new addon no longer needed it; it stays in git history. Two small
  scripts were carried over: `tools/make_empty_structure.py` (GameTest structure templates) and
  `tools/server_session.sh` (drives a dedicated server's console).
- **Forge 47.4.27** (newest 1.20.1 Forge) for both the pack and the addon.
- **JEI rather than EMI**: both GTM and AE2 have mature JEI integrations, including AE2 pattern
  encoding. Full list and reasons: `docs/mods.md`.
- **Mod id `utechnology`**, Java package `dev.utechnology`. Item and block IDs will live under this mod id
  and must never change once released.
- **KubeJS in GameTests**: the addon's GameTest server loads KubeJS, Rhino and Architectury from
  Modrinth's Maven (the exact files the pack uses), and the pack's `kubejs/` and `config/` folders are
  copied into the test run folder first. So GameTests run the recipes exactly as the pack ships them.
- **The addon in the pack**: the addon isn't on Modrinth, so `tools/export-pack.sh` exports the pack with
  packwiz and adds the built jar under `overrides/mods/`. This is the one jar not managed by a packwiz
  metafile; it's built from this repository.
- **CI** (`.github/workflows/ci.yml`): three jobs. *design* runs the simulator tests and
  `tools/check.sh`. *addon* builds the addon and runs all GameTests; a failed required GameTest makes
  the run exit non-zero. *pack* checks the packwiz index is current and exports the `.mrpack` with
  the addon jar. The spike folder isn't built or checked (it's deleted).
- **Network**: packwiz needed `files.minecraftforge.net` (Forge's version list); Anthony allowed it.

### Phase 2
- **Questions answered by Anthony**: build items (machines, cores, controllers, kits, modules) are
  assembler recipes one voltage tier below the item's tier, 10 s each (`build_rule` in
  `design/multiblocks.yaml`).
- **IDs live in `design/ids.yaml`**: one table from design names (`motor_lv`) to game IDs
  (`gtceu:lv_electric_motor`). Our own IDs: parts `utechnology:mv_stator_coil`, `hv_stator_coil`,
  `precision_bearing`; cores, kits and modules by name; controllers named after the machine.
- **What's generated** (all marked "do not edit"): `pack/kubejs/server_scripts/generated/recipes.js`,
  `pack/kubejs/client_scripts/generated/hide.js`, the addon's data in
  `pack/kubejs/data/utechnology/upgrade_{frames,modules,multiblocks}/`, the bonus test cases
  (`addon/src/test/resources/bonus_cases.json`, 1,287 cases computed by the simulator's own code) and the
  GameTest expectations. The addon's data lives in the pack (KubeJS loads `kubejs/data` as a data pack),
  so rebalancing is "edit design, regenerate", with no recompile.
- **Validator, two halves.** `tools/validate.py` checks the design on its own (items, IDs, conflicts, bus
  sizes) and writes `docs/validation-report.md`. Whether stock recipes still match GTM 7.5.3 can only be
  answered by the running game, so GameTests (`DesignTests`) check that: every ID exists, stock recipes
  match, replaced recipes are gone, our recipes loaded as designed, and none of ours conflicts with any
  other recipe of the same machine type in the whole game (not just the design).
- **Conflict rule.** A conflict is two recipes of one machine type where every input of one (programmed
  circuit included) is also an input of the other: a bus loaded for the larger can run the smaller. Only a
  different circuit fixes that, so it's an error. The simulator's checks use the same rule.
- **What the GameTests found and fixed**: GTM's stock conveyor recipes use programmed circuit 1 (the
  design didn't record it), and GTM's casing recipes use circuit 6. Retrofit kits take the same plates and
  frame boxes as casings, so they now use **circuit 7** (decision `kit-circuit`); otherwise a bus loaded for
  casings could make kits.
- **Recipe IDs**: GTM's KubeJS integration puts the recipe type in front, so ours are
  `utechnology:<type>/<name>`, e.g. `utechnology:assembler/motor_mv`, `utechnology:assembler/build/core_turning_line`.
- **Hard mode**: GTM's hard recipe options are on in `pack/config/gtceu.yaml` (harder circuits, controllers,
  redstone, tools, bricks, misc). Wood and dye nerfs stay off: on a skyblock start they only add tedium.
- **MV/HV coil winders and the HV bearing press** follow the LV/MV builds (decision
  `higher-tier-machine-builds` in the slice file); the design only had the first tier of each.
- **Gate for later phases**: our own items, machines and recipe types don't exist until phases 3 and 4.
  `DesignTests.NOT_YET_BUILT` lists what is skipped; each phase narrows it and it's empty at the end.

### Phase 2 review (independent agent) and what changed
- **Fluids and distinct buses (the review's blocker).** In GTM 7.5.3 a distinct input bus never sees a
  fluid hatch, so recipes with fluids (conveyors, cables, the bearing) can't be fed through distinct buses.
  GTM's tool for them is the **colour group**: dye an input bus and a fluid hatch the same colour. The
  validator now works out, for each multiblock, how its recipes split into input groups that AE2 can feed
  without wrong crafts (no recipe's inputs all also belong to other recipes in its group), and checks
  that against the hatch limits (6 input buses, 2 fluid hatches). Every machine fits with HV buses. With
  the small buses a player starts with, one Component Assembler or Assembly Hall can't hold every recipe
  at once (10 and 13 groups); that's a warning, and the answer in play is several machines or bigger
  buses. The grouping is listed in `docs/validation-report.md`, and the AE2 decision in the slice file
  has a note about colour groups.
- **Answers from Anthony**: a machine holds at most **one of each module** (the simulator now refuses
  duplicates); the batch bus's lubricant is charged **per parallel**; hard mode stays as configured (wood,
  dye and vanilla-block nerfs off; decision `hard-mode-scope`).
- **Honesty about the gate**: in phase 2 none of our own recipes could load (they all use phase 3 items), so
  the two GameTests for our recipes checked nothing yet. They become real in phase 3 (now) and phase 4.
- Other fixes: a GameTest that nothing but our recipe makes a replaced item (catches a wrong removal ID);
  the disabled multiblocks' IDs are checked; validator checks fluid/item mix-ups, that every changed recipe
  names what it replaces, that component recipes and component items agree, and that frame IDs agree
  between the two design files; bonus cases now cover overfilled slots, duplicate modules, and per-recipe
  timing and EU/t (overclocking after modules), one case per line; module data has names, effect text,
  `lock_duration` and `upkeep_per_parallel` with the fluid ID; kit size comes from the design; stray
  generated scripts are detected.
- Not changed: GameTest expectations ship inside the addon jar (small, harmless); disabled multiblocks are
  hidden from JEI but still show in the creative tab.

### Phase 3
- **Content**: parts (`mv_stator_coil`, `hv_stator_coil`, `precision_bearing`), the four core blocks, three
  retrofit kits and three modules (plain items for now; phase 4 gives kits and modules their behaviour),
  a creative tab, and two machine types: the coil winder (LV, MV, HV) and the bearing press (MV, HV).
- **How the machines are built**: the same way GTM builds its own simple machines (`SimpleTieredMachine`,
  GTM's machine UI, normal overclocking: each tier above the recipe gives 2x speed for 4x EU/t). Their two
  recipe types are registered in GTM's own registration event, with the same steps GTM uses.
- **Recipes come from the pack**, as planned: the machines have no recipes in Java; the generated KubeJS
  script adds them. The GameTests load that script, so they test what the pack ships.
- **Placeholder art**: items borrow GTM and vanilla textures (stator coils use GTM's voltage coils), core
  blocks use GTM gearbox casing textures, and machines use GTM's wiremill and compressor overlays.
- **Models, blockstates, names and the core blocks' loot tables** are written by data generation (Registrate)
  into
  `addon/src/generated/resources` and committed. The generator finishes in a second but something in the
  mod stack keeps Java running afterwards, so `tools/run-datagen.sh` stops it once the files are written.
- **GameTests** (`MachineTests`): each machine runs a recipe at its tier with the right duration and EU/t
  (LV winder: MV stator coil, 200 ticks at 30 EU/t; MV winder: HV stator coil, 200 at 120; HV winder
  overclocks it to 100 at 480; MV press: bearings, 160 at 120; HV press overclocks to 80 at 480), and an
  LV winder refuses the MV-voltage recipe. `DesignTests` now checks all our IDs and recipes except the four
  multiblock controllers (phase 4).

### Phase 3 review (independent agent) and what changed
- JEI category titles: the recipe types now have names ("Coil Winder", "Bearing Press").
- The creative tab was empty (GTM's tab helper doesn't fill it on its own); it now lists all our items,
  blocks and machines, and its title is a translatable name.
- Machine GameTests now measure the real number of ticks from start to output, check amperage, and the
  "LV can't run an MV recipe" test also checks nothing was consumed.
- New `AssetTests` GameTest: every item, block and recipe type of ours has a name and model in the jar. This
  is how CI notices a forgotten `tools/run-datagen.sh` (the test server never loads client assets).
- `tools/run-datagen.sh`: clears the output first (so removed content leaves nothing behind), fails fast
  on errors, stops its Java processes even if interrupted.
- **Stack sizes** (my choice, not in the design): parts and kits stack to 64, modules to 16.
- **Core blocks** are pickaxe blocks with no tool tier for now; revisit with the structure work.

### Phase 4 decision from Anthony: free parallels
GTM's own rule limits parallels to voltage ÷ recipe EU/t, which with frames capping voltage would make
segments nearly useless at the frame's tier. Anthony's answer: **parallels don't consume additional power**.
A machine runs all its parallels at one recipe's EU/t; overclocking is judged on one recipe against the
frame voltage; only inputs and output space limit parallels. Recorded as decision `free-parallels` in
`design/multiblocks.yaml`; the simulator now counts energy the same way (energy per craft ÷ parallels).
