# Prototype progress

Plan: `docs/prototype-plan.md`. This file says where the work is and why things were decided, in plain
language. If a session restarts, resume from "Current state".

## Current state
- Phase 1 (repository and pack skeleton): done. Reviewed; fixes applied (JEI pinned to its newest
  release, CI actions updated and the runner pinned, sturdier scripts, changelog). CI green.
- Phase 2 (data pipeline and validator): done. Reviewed; the review's findings are fixed (see "Phase 2
  review" below). Committed on its own so CI checks it.
- Phase 3 (items and single-block machines): done. Reviewed; fixes applied.
- Phase 4 (upgradeable multiblocks): done. Reviewed; fixes applied (194 GameTests, JUnit green).
- Phase 5 (test world, manual test list): built; full pack boots with kits checked; reviewed and fixed.
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
- **JEI rather than EMI** (superseded: EMI replaced JEI after play-testing, see the end of this file): both GTM and AE2 have mature JEI integrations, including AE2 pattern
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
  `pack/kubejs/client_scripts/generated/hide.js (now `pack/kubejs/assets/emi/`: EMI's hidden-items and recipe-filter files)`, the addon's data in
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
  GTM's tool for them is the **colour group**: a dyed input bus (spray can) forms its colour's group, and
  every *undyed* hatch joins every colour group (corrected in phase 4 after reading GTM's
  `RecipeHelper.addToRecipeHandlerMap`). So fluid recipes go on dyed buses and share the machine's undyed
  fluid hatches. The validator works out, for each multiblock, how its recipes split into input groups that
  AE2 can feed without wrong crafts (no recipe's items all also belong to other recipes in its group) and
  checks that against the limits (one bus per group, 6 buses; the machine's fluids must fit its 2 fluid
  hatches). Every machine fits with HV buses. With the small buses a player starts with, one Component
  Assembler or Assembly Hall can't hold every recipe at once; that's a warning, and the answer in play is
  several machines or bigger buses. The grouping is in `docs/validation-report.md`.
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

### Phase 4: what was built
- **One base class**, `UpgradeableMultiblockMachine`, holds everything from the design's framework: the
  structure (front aisle, 1 to 32 segments with the machine's core block, end aisle with frame-box corners),
  hatch limits, frame detection (every casing and frame box must be the same frame), voltage capped at the
  frame, counted segments = min(built, cap), modules in controller slots, the specialization lock, retrofit
  with kits, and the recipe filters. The four machines are four short definitions in `UTMultiblocks`.
- **Data, not code**: frames, caps, slots, module effects, hatch limits and filters are read from the pack's
  generated data (`UpgradeData`, reloaded by `/reload`). Only a machine's recipe type and core block are
  fixed in Java (they must exist when the game registers machines); a GameTest checks they agree with the
  design.
- **Bonuses are computed when the structure forms, a module changes or the data reloads**, then cached
  (`BonusCalculator`, plain Java). Per recipe, the machine only checks the filter and lock and lets GTM count
  how many parallels the inputs allow.
- **Free parallels** (Anthony's answer): the parallel step scales items and fluids but not EU/t, and GTM's
  input-only parallel count is used (`getParallelAmountWithoutEU`). Then module effects, then GTM's normal
  overclocking. Order: segments, modules, overclocking, as designed.
- **Batch bus upkeep**: lubricant is added to the recipe as an input, 10 mB per parallel.
- **Modules**: three slots in the controller screen (a "Modules" side tab). They can also be installed by
  right-clicking the controller with a module (this checks the frame's slot count, unlock tier and one of
  each) and removed by sneak-right-clicking with an empty hand. A module the frame can't use (for example
  after a downgrade) stays in its slot but is shown as "not working" and has no effect, as in the simulator.
- **Retrofit**: right-click the formed controller holding kits of another frame. It needs one kit per 8
  casings and frame boxes (rounded up), converts them all at once, and gives the old ones back. Works up and
  down. In creative mode no kits are used up.
- **Spike lessons applied**: the segment count is read under a lock on GTM's shared pattern and saved with
  the machine; the end aisle's corners are frame boxes, so GTM's limited-hatch bug can't trigger (tested at
  every length 1 to 32); `alwaysTryModifyRecipe` is on.

### Phase 4: what the GameTests found, and what changed
- **The simulator didn't match GTM's overclocking** in two places: GTM gives ULV recipes (like wire, 7 EU/t)
  one overclock fewer, and it truncates EU/t and ticks to whole numbers after each modifier (so the efficiency
  coil turns 16 EU/t into 9, not 9.6). The simulator now computes recipe runs exactly as GTM does, and the
  in-game tests compare exact ticks and EU/t. This changes some simulator estimates slightly (ULV recipes in
  LV machines are now slower in the estimate, as they are in game).
- **Batch bus and AE2** (a gameplay consequence worth knowing): with a batch bus, every recipe also takes
  lubricant from a fluid hatch, and in GTM a distinct bus never sees fluid hatches. So a batch-bus machine
  must be fed through dyed (colour group) buses, which all see an undyed lubricant hatch; distinct buses
  stop working while the batch bus is installed. A machine with rubber recipes then uses both fluid
  hatches (rubber and lubricant). The validator checks that the fluids fit.

### Phase 4: numbers
- Structure check time (one full check, the work GTM does on every block change in a formed structure),
  measured in the GameTest server while ~186 other tests ran: **16 segments: about 63 µs; 32 segments:
  about 104 µs** (medians; spike: 37.5 µs at 8). Even at 32 segments that is 0.2% of a 50 ms tick, and checks
  only run when blocks in the structure change.
- GameTests: 187 (every frame at lengths 1-16, 17 and 32; hatches in the end aisle at every length 1-32;
  mixed frames; breaking and rebuilding; module slot rules; retrofit up and down; recipe filters; the lock;
  saving; registration vs design; and the simulator's 75 recipe cases run in game).
- JUnit: the bonus calculator matches all 1,632 simulator cases and the 75 recipe cases.

### Phase 4 review (independent agent) and what changed
- **Voltage gate (blocker).** GTM's own voltage check divides a recipe's EU/t by its parallel count. With free
  parallels that let, say, a 4-segment steel machine run a 120 EU/t MV recipe. Our modifier now checks the
  recipe's tier against the frame-capped machine tier before anything else. GameTest added.
- **Modules dropped on breaking (blocker).** Breaking the controller now drops its modules. GameTest added.
- **Lubricant and parallels.** The batch bus's lubricant is now counted when GTM works out how many
  parallels fit, so too little lubricant runs fewer parallels instead of nothing. GameTest added.
- **Changes while idle.** GTM reruns its last recipe without re-running modifiers unless told the recipe
  changed; module changes and data reloads now tell it. GameTest added.
- **Segment count no longer read from GTM's shared pattern.** It's counted from this controller's own
  match result (one core per segment). The spike's "read the shared count under a lock" approach wasn't
  safe against the client's multiblock preview, which reads the same pattern without the lock. The CLAUDE.md
  "lessons from spike 1" line about this is now out of date: the count comes from the core blocks.
- **Recipe filter** moved into GTM's recipe search (filtered recipes are never candidates), cached per recipe.
- **Retrofit**: refused while the machine is running a recipe (the conversion briefly unforms it, which would
  lose the recipe), refused right after loading until the structure has been re-checked, and one frame at a
  time (the design says "kits of the next frame"; downgrades one step down). Kits in the off-hand count.
- **Smaller fixes**: module slots hold one module each; frame names are translated in messages; a lock in a
  slot the frame can't use is cleared; the client (which doesn't receive the server's upgrade data) still
  recognises modules and kits; the simulator and Java never go below 1 tick (as GTM).
- **Tests added**: over-cap machines run at the cap (17 segments, stainless: 16 parallels); mixed casings and
  mixed frame boxes, each with a positive control; saved upgrades loaded back; preview fallback limits match
  the data.
- **Known limits, not fixed**: a dedicated server's clients don't get the upgrade data, so the JEI multiblock
  preview uses fallback hatch limits and shows only steel blocks (single-player is unaffected). `/reload`
  applies new data to the next recipe but doesn't re-check formed structures until a block changes.

### Phase 5: the test world
- **Kits, not a custom world**: `design/prototype.yaml` defines the test world. The generator asks the simulator
  what the goal (4 each of HV robot arms, conveyors and pistons) and the starter factory cost in supplied
  materials, adds 50%, and writes `pack/kubejs/server_scripts/generated/starter.js`. Every player gets the kits
  once on first join as labelled shulker boxes (11 boxes): materials, machines and hatches, AE2, creative
  power, fluids, and real LV-HV power. `/utechkit` gives them again. This is a one-time script, not a hot path.
- **Real power** is real generators (LV, MV, HV combustion) and cables, with diesel buckets sized by the
  simulator from the goal's energy (480 EU per mB). **Rubber** (about 155 buckets' worth) comes from creative
  tanks, because the rubber chain is out of scope; lubricant comes as real buckets.
- **Maintenance hatches in the kit are auto-maintenance**, so the test is about our machines, not GT
  maintenance.
- **`docs/prototype-estimate.md`** (generated): the simulator's time for the goal, single-block machines only
  (about 33 minutes) and with the multiblocks (about 17 minutes), with the kit's generators.
- **`docs/prototype-test.md`**: the manual checklist.
- **The full pack boots on a dedicated server** in CI (`tools/pack-server-smoke.sh`): the exported .mrpack with
  every server-side mod (AE2, JEI, Jade and the rest load together), configs, scripts and the addon. It runs
  `/utechkit check`, which builds every kit box and checks every item exists, and fails on KubeJS errors.
  This found a real bug before anyone played: KubeJS's script engine rejects `const` inside loops on the
  second pass, so kits with more than one box would have failed to give.
  What the test can't cover: the first-join gift itself (a dedicated server in CI has no player joining), so that
  is in the manual checklist. The first-join flag is kept in the server's saved data per player, not on the
  player, because Forge doesn't carry a player's own data over after death.
- **Phase 5 review fixes:** kits now cover the checklist's whole retrofit path (steel to aluminium to stainless
  and back to aluminium, with an 8-segment Component Assembler); diesel covers the goal in either reference
  factory; the checklist says to use Survival for retrofits (Creative skips kit use), that build times overclock,
  that retrofit messages show above the hotbar, and how products get back into AE2; the retrofit message reads
  "Used N kits: the frame is now Aluminium"; the pack-server CI job has a 30-minute limit and the smoke test
  can't hang on shutdown.
- **`docs/progression.md`**: the mod/tier table CLAUDE.md asks for. AE2 isn't gated to LV by recipes yet
  (the prototype hands it out); that's milestone 1 work.

### After the PR: play-testing fixes
- **No tooltips on GregTech recipe pages in JEI** (reported from play-testing). Cause: JEI 15.56 changed internals that
  GregTech 7.5.3 and LDLib (its UI library) patch; GregTech is built against JEI 15.20. Reproduced in a real game
  client on the build VM (`./gradlew runClient` on a virtual display): with 15.56 GregTech's fluid-tooltip patch fails;
  with 15.20.0.134 the bender page shows item tooltips. JEI is pinned to 15.20.0.134 (docs/mods.md), and a test keeps
  the addon's dev mods equal to the pack's.
- **EMI instead of JEI** (suggested from play-testing). GregTech Modern ships a native EMI plugin and AE2 15.4 an EMI
  module with pattern encoding, and JEI's working versions for GregTech 7.5.3 are betas. EMI 1.1.24 checked in the dev
  client: GregTech's and LDLib's EMI patches apply, GregTech recipe pages show tooltips, the Coil Winder page shows
  our recipe, and the disabled multiblocks are hidden (EMI index file generated from `design/ids.yaml`).
