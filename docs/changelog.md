# Changelog

## Unreleased

### 2026-10-13: EMI replaces JEI
- The recipe viewer is now EMI 1.1.24 (pinned). GregTech and AE2 both support it natively, including AE2 pattern
  encoding from EMI recipes. Checked in a real client: GregTech recipe pages show tooltips, our Coil Winder page
  works, and the disabled Large Wire Factory, Large Assembling Factory and Large Cutter are hidden.
- The disabled multiblocks are hidden with EMI's own index file (`pack/kubejs/assets/emi/index/stacks/utechnology.json`,
  generated) instead of a KubeJS JEI script; their multiblock preview pages are filtered out too
  (`assets/emi/recipe/filters/`).

### 2026-10-13: JEI tooltips on GregTech recipe pages
- Fixed: hovering items on GregTech machine recipe pages in JEI showed no tooltip. JEI 15.56 changed internals that
  GregTech 7.5.3 and its UI library patch. JEI is now pinned to 15.20.0.134, the newest build GregTech works with.
- The addon has a dev game client (`./gradlew runClient`, with JEI) for checking UI issues; a test keeps the addon's
  dev mods and the pack's mods the same versions.

### 2026-10-13: Prototype phase 5 (test world)
- `design/prototype.yaml`: the test world; starter kits sized by the simulator, given on first join and with
  `/utechkit`; `/utechkit check`.
- `tools/pack-server-smoke.sh` and a CI job: the exported pack boots on a dedicated server with all
  server-side mods and the kits check out.
- Docs: `prototype-test.md` (manual checklist), `prototype-estimate.md` (generated), `progression.md`.
- Review fixes: kits sized for the full retrofit path and an 8-segment Component Assembler; diesel for either
  reference factory; first-join flag survives death; clearer retrofit message; CI timeout on the pack server job.

### 2026-10-13: Prototype phase 4 (upgradeable multiblocks)
- Component Assembler, Assembly Hall, Wire Drawing Line and Turning Line on one base class: frames,
  1-32 segments (counted up to the frame's cap), modules in controller slots, specialization lock, retrofit
  kits (up and down), recipe filters, voltage capped at the frame, free parallels.
- Frames, modules and machine specs read from the generated data pack; bonuses cached per machine.
- Simulator: recipe runs now computed exactly as GTM does (ULV overclock rule, whole ticks and EU/t).
- Validator: batch-bus feeding limits reported.
- 187 GameTests, JUnit bonus tests; structure check about 63 µs at 16 segments, 104 µs at 32.

### 2026-10-13: Prototype phase 3 (items and single-block machines)
- Addon: stator coils, precision bearing, four core blocks, retrofit kits, modules; coil winder (LV-HV) and
  bearing press (MV-HV) with their recipe types. Recipes come from the generated KubeJS script.
- Placeholder textures borrowed from GTM and vanilla; models and lang from data generation
  (`tools/run-datagen.sh`).
- GameTests: each machine runs its recipe at the right voltage and duration (measured in ticks); every item,
  block and recipe type has a name and model.
- Design: parallels draw no extra power (decision free-parallels); simulator updated.

### 2026-10-13: Prototype phase 2 (data pipeline and validator)
- `design/ids.yaml`: design names mapped to game IDs.
- `tools/generate.py`: writes the pack's KubeJS recipe scripts (changed MV/HV motors, stator coils,
  bearings, all builds; removes the replaced stock recipes and the disabled Large multiblocks), the
  data pack JSON the addon reads (frames, modules, multiblocks), bonus-math test cases computed by the
  simulator core, and GameTest expectations.
- `tools/validate.py`: checks items, IDs, recipe conflicts and bus sizes; writes
  `docs/validation-report.md`. GameTests check the live game: IDs exist, stock recipes still match
  GTM 7.5.3, replaced recipes are gone, our recipes loaded as designed and don't conflict.
- `pack/config/gtceu.yaml`: GTM's hard recipe options on (except wood and dye nerfs).
- Validator models AE2 feeding with distinct buses and colour groups (GTM 7.5.3: distinct buses don't see
  fluid hatches) against the hatch limits, and lists each machine's input groups.
- Design: one of each module per machine; batch bus lubricant per parallel; hard-mode scope recorded;
  conveyors' stock circuit 1 recorded; build recipes are assembler recipes one tier below
  (decision build-recipes); retrofit kits use circuit 7 (kit-circuit); MV/HV coil winder and HV
  bearing press builds added.

### 2026-10-13: Prototype phase 1 (repository and pack skeleton)
- `pack/`: packwiz pack, Forge 1.20.1 (47.4.27), GTM 7.5.3, KubeJS, AE2, JEI, Jade and performance mods
  (`docs/mods.md`).
- `addon/`: new GTM addon project (`utechnology`) with a GameTest server that loads KubeJS and the pack's
  scripts.
- CI: simulator tests, design checks, addon build and GameTests, pack export with the addon jar.
- Design: efficiency coil saves 25% energy per craft; fluid hatches on every multiblock; steel retrofit kit.
- `spike/` removed (kept in git history).

### 2026-10-13: Design files and prototype plan
- `CLAUDE.md`: per-machine upgrades (frames, segments, modules, retrofit kits) replace the research
  tree; AE2 and recipe-conflict rules; lessons from spike 1; workflow without code review.
- `design/slice1-mechanical.yaml` (moved from the repo root): the mechanical component slice from
  ingots to HV, with stock recipes from GTM 7.5.3's generators, and all decisions so far.
- `design/multiblocks.yaml`: the upgradeable multiblock framework and the four prototype multiblocks.
- `design/research-v1.yaml`: superseded research design, kept for reference.
- `design/sim/`: the factory simulator (core, tests, build script, page template; `data.json` and
  `index.html` are generated by `build.py`).
- `docs/prototype-plan.md`: the five-phase plan for building the prototype.

### 2026-10-13: Spike 1 (branch `spike-1`, throwaway)
- `spike/`: Forge 1.20.1 addon from the official GregTech addon template, pinned to GTM 7.5.3 + FTB Teams 2001.3.2.
- Proved: a variable-segment multiblock (segments = parallels), a cached per-team research bonus applied by a
  RecipeModifier, and the owner/team edge cases. 21 required + 4 optional GameTests plus a dedicated-server save/reload run.
- Findings and the manual test list: `docs/spike-1.md`.
