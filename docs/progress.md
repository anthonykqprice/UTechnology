# Prototype progress

Plan: `docs/prototype-plan.md`. This file says where the work is and why things were decided, in plain
language. If a session restarts, resume from "Current state".

## Current state
- Phase 1 (repository and pack skeleton): built; under review.
- Branch: `claude/gifted-hawking-pmc94i` (one branch for all five phases, one PR at the end).

## How to run things (from the repository root)
| What | Command |
|---|---|
| Simulator tests | `node design/sim/test.js` |
| Design checks (stale generated files, validator) | `tools/check.sh` |
| Addon build + GameTests | `cd addon && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --max-workers=1 build runGameTestServer` |
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
