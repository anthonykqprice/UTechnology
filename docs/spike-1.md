# Spike 1: GTM Java API checks

Throwaway code in `spike/`, on branch `spike-1`. Goal: prove that three things work in GregTech Modern's
Java API, and record what we learned. Nothing here is production code.

## Setup

| | |
|---|---|
| Template | [GregTechCEu/GregTech-Addon-Template](https://github.com/GregTechCEu/GregTech-Addon-Template) (official, LGPLv3, by screret / JuiceyBeans). Copied, then trimmed: no JEI/EMI/Jade, no Spotless, no mixins. Credit kept in `build.gradle`. |
| Build | ModDevGradle Legacy Forge 2.0.91, Gradle 8.10, Forge 1.20.1-47.4.0, Parchment 2023.09.03, Java 17 |
| GTM | **7.5.3** (tag `v7.5.3-1.20.1`, commit `91a79b8`), the newest 1.20.1 release tag. `latest-1.20.1` is a moving tag on the branch head, not a release. |
| GTM required deps | LDLib 1.0.40.b, Configuration 2.2.0, Registrate MC1.20-1.3.11 |
| FTB Teams | 2001.3.2, plus its required deps FTB Library 2001.2.13 and Architectury 9.2.14 |

Commands (from `spike/`, with `JAVA_HOME` set to a JDK 17):

```
./gradlew --max-workers=1 runGameTestServer      # 25 required + 4 optional GameTests
tools/server_session.sh tools/reload_run1.txt run1.log   # dedicated server, scripted console
tools/server_session.sh tools/reload_run2.txt run2.log   # restart and check
```

Result of the last run: **all 25 required GameTests pass.** 2 of the 4 optional (observation) tests fail, as
expected; see Check 1. The save/reload run on a dedicated server passes.

Environment notes:
- The VM's default `java` is 21. Gradle must run with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.
- Maven Central returned HTTP 429 through the proxy when Gradle downloaded in parallel. `--max-workers=1` avoids it.
- The test server logs a Forge update-check 403 and a Mojang auth-host failure. Both are harmless.
- The GameTest structure templates (`empty`, `wide`) are generated air boxes. `tools/make_empty_structure.py` writes them.

---

## Check 1: variable-segment multiblock

**What worked.** `spike_segment_assembler` runs the assembler recipe type. It has a front aisle with the
controller, a middle aisle that repeats 1 to 8 times (marked by a grate casing in its centre), and an end aisle.
After forming, the controller reads how many middle aisles were matched. That number becomes the parallel limit,
applied through GTM's own `ParallelLogic.getParallelAmount`, in the same way GTM's parallel hatch modifier does it.

| GameTest | Result |
|---|---|
| `forms1`, `forms4`, `forms8` | form, with `getSegments()` = 1 / 4 / 8 |
| `zero_segments_does_not_form`, `nine_segments_does_not_form` | don't form (below min / above max) |
| `break_segment_unforms_and_reforms` | breaking a casing in segment 2 unforms (segments → 0). Restoring it re-forms with 4. |
| `shrink_updates_segments` | turning segment 4 into an end aisle re-forms immediately with 3, without unforming |
| `grow_updates_segments` | 4 → 5 segments: unforms, then re-forms with 5 once the new end aisle is placed |
| `parallel4`, `parallel8` | running recipe has `parallels` = 4 / 8; output = 4 / 8 diamonds after one 100-tick run |
| `parallel8_limited_by_inputs` | 8 segments, inputs for 3 → 3 parallels (GTM's input limiting works) |

**Measured structure check time** (`structure_check_timing`; mean per full `checkPattern` call, 15 interleaved
rounds of 500 checks after warm-up; 4-vCPU Xeon @ 2.1 GHz, GameTest server under load):

| Structure | Median | Min |
|---|---|---|
| 1 segment (formed) | 23.8 µs | 23.3 µs |
| 4 segments (formed) | 31.3 µs | 29.5 µs |
| **8 segments (formed)** | **37.5 µs** | **35.6 µs** |
| 8 segments, broken end aisle (failing) | 92.2 µs | 69.7 µs |

GTM runs one full check on the main thread for every block change inside a formed structure. While the
structure is unformed, it also re-checks on its async thread about once per second. At 8 segments, a check costs
under 0.1% of a 50 ms tick. The failing case costs more and varies more, probably because the async thread is
re-checking the same unformed structure and contending for the shared pattern lock.

**GTM classes used:** `FactoryBlockPattern.aisleRepeatable(min, max, ...)`, `BlockPattern.getFormedRepetitionCount()`,
`WorkableElectricMultiblockMachine` (`checkPattern`, `onStructureFormed`, `onStructureInvalid`),
`Predicates.controller/blocks/abilities`, `PartAbility`, `ParallelLogic.getParallelAmount`,
`ModifierFunction.builder()`, `ContentModifier.multiplier`, `RecipeModifier`, `MultiblockMachineBuilder.alwaysTryModifyRecipe`,
`GTRegistrate`, `IGTAddon` / `@GTAddon`.

**Gotchas**

1. **7.5.3 has no slice system.** `PatternSlice`, `BasicSliceStrategy` and `actualRepeats` exist only on the
   1.20.1 branch head, which is the unreleased **8.0.0**. There the pattern API has moved to
   `com.gregtechceu.gtceu.api.multiblock.pattern`, so moving to 8.0 means a package migration. In 7.5.3, repeats come from
   `aisleRepeatable(min, max, …)` or `.aisle(...).setRepeatable(min, max)`. The matched count is stored in
   `BlockPattern.formedRepetitionCount[aisleIndex]`.
2. **The repeat count is shared mutable state.** GTM memoizes one `BlockPattern` per machine *definition*, so
   every controller of that type shares `formedRepetitionCount`. Checks run on both the server thread and GTM's
   async structure thread (`MultiblockWorldSavedData`), and each check holds only its own controller's lock. The
   spike overrides `checkPattern()` to `synchronized (pattern)` and copies the count before releasing the lock
   (`SegmentAssemblerMachine`). Without that, two controllers of the same type could read each other's counts.
3. **The matcher double-counts limited hatches (GTM bug).** After the last real segment, GTM retries the next
   aisle as a segment `max − built` times. Each failed attempt runs the predicates it reached before failing, so a
   hatch with `setMaxGlobalLimited(n)` sitting there is counted again each time. Optional tests: an energy hatch
   (max 2) in the end aisle forms with 7 or 8 segments but **not with 1 or 6**. A player would see a structure that
   forms only at some lengths. Avoid it in our patterns: put limited hatches only in aisles that aren't directly
   after a repeatable aisle, or make the following aisle differ from the segment in its first checked block (the
   bottom row, first character), so retries fail before they reach any hatch. Worth reporting upstream.
4. **Don't forget `alwaysTryModifyRecipe(true)`.** Without it, `RecipeLogic.onRecipeFinish` reuses the
   already-modified recipe for consecutive runs. A research change (or segment change) would then only apply
   after the machine goes idle. With it, the modifiers run at every recipe start; `cache_updates_on_grant_and_revoke`
   checks this mid-run.
5. **Persist the segment count.** GTM persists `isFormed`, and a freshly loaded controller re-checks within
   about a second (its `MultiblockState` starts with `UNINIT_ERROR`). The spike stores `segments` with `@Persisted`
   anyway, so a still-formed machine never reports 0 segments in that window.
6. Patterns are written front-first with `FactoryBlockPattern.start(LEFT, UP, BACK)`. GTM's default `start()`
   is `(LEFT, UP, FRONT)`, where the controller aisle comes last.
7. The spike leaves overclocking out (`recipeModifiers(SEGMENT_PARALLEL, TEAM_RESEARCH)`) to keep durations
   predictable. The real machine should order them parallel → research → OC. Check that the research
   duration multiplier and OC combine the way the simulator expects.

**Recommendation.** Use it. On 7.5.3, `aisleRepeatable` plus a per-controller copy of the count under a lock on
the pattern is a sound way to build "segments = parallels". The cost is tiny at 8 segments, so segment caps can go
well above 8 before structure checks matter. Measure again at each new cap, as CLAUDE.md requires. Wrap the
count-reading in one base class, design patterns around gotcha 3, and plan for the 8.0 package move (likely
before release, since 8.0 replaces this matcher).

---

## Check 2: cached per-team recipe bonus

**What worked.**
- `ResearchSavedData` (overworld `data/utspike_research.dat`) stores `schema: 1` and `teams: [{team: UUID, nodes: [...]}]`.
  A file with a newer schema is refused with an error rather than loaded and lost on the next save. The place for
  a 0 → 1 migration is marked.
- `ResearchCache` is a `ConcurrentHashMap<teamId, TeamBonus>`, where `TeamBonus` is an immutable record. It is
  built once at `ServerStartedEvent` from save data and cleared at `ServerStoppedEvent`. After that it changes
  only when `ResearchService.grant/revoke` actually changes a team, and then only that team's entry is
  recomputed. It is never rebuilt wholesale and never recomputed per recipe.
- `SpikeModifiers.TEAM_RESEARCH` is a `RecipeModifier`. At recipe start it does
  machine → `getOwner()` → owner UUID → FTB team ID → cache lookup, and returns
  `durationMultiplier(0.8)` or `IDENTITY`. It never touches save data or iterates research.
- Command: `/utspike research grant|revoke <team>` and `/utspike research list` (op level 2). `<team>` can be an FTB
  team display name, a short name (`name#xxxxxxxx`), a team UUID, or an online player's name. A debug command,
  `/utspike machine <pos>`, prints formed state, segments, owner, team, cached bonus and the running recipe.

| Test | Result |
|---|---|
| `bonus_applies_to_whole_team_only` | one grant to party P → machines owned by two different P members both run at 80 ticks, without being touched; a machine owned by a non-member runs at 100 |
| `cache_updates_on_grant_and_revoke` | grant → cache ×0.8, save data dirty; a second grant → no change and **no recompute** (counter +1 total); revoke *while the machine is running* → the next consecutive recipe runs at 100 |
| `save_data_round_trip` | NBT round trip keeps nodes per team; schema written; newer schema refused |
| Dedicated server, 2 boots | boot 1: `ftbteams server create spiketeam`, grant, `save-all`, stop. Boot 2 logs `Research cache built: 1 teams with bonuses (schema 1)`; `list` shows the node with cached ×0.8 under the same team UUID; grant again → "no change"; revoke → cache ×1.0 |

**Classes used:** GTM `RecipeModifier`, `ModifierFunction.builder().durationMultiplier`,
`MetaMachine.getOwner()/getOwnerUUID()`, `FTBOwner`. FTB Teams `FTBTeamsAPI.api().getManager()`,
`TeamManager.getTeamForPlayerID / getTeamByName / getTeamByID / getTeams`, `Team.getId()`. Minecraft `SavedData`,
`DimensionDataStorage.computeIfAbsent`.

**Gotchas**

1. **`FTBOwner.getUUID()` / `getTeam()` return the owner's personal team, not their party.** They call
   `getPlayerTeamForPlayerID`. `ftb_owner_uuid_is_personal_team` logs `FTBOwner.getUUID()` = personal team = the player's
   UUID, while the party has a different ID. For team research you must use
   `TeamManager.getTeamForPlayerID(ownerUUID)`, which returns the *effective* team. `TeamResolver` does exactly this,
   and both calls are hash lookups.
2. Look teams up by display name with care: `getTeamByName` wants the short name (`spiketeam#8f9e507d`).
3. Save data lives on the overworld's `DimensionDataStorage`. It is written on autosave, `save-all` and shutdown,
   like any `SavedData`; the cache itself is never saved.
4. FTB Teams loads its data before `ServerStartedEvent` ("loaded team data … 1 teams total" comes before
   our "Research cache built"). The cache doesn't depend on FTB anyway: it is keyed by team UUID, and FTB keeps
   team UUIDs across restarts.

**Recommendation.** Keep this shape: a single writer service that updates save data and the cache together, an
immutable bonus record per team, and a modifier that does two hash lookups. For production: generate the node
list and effects from `design/` as data pack JSON, rebuild the whole cache on `/reload`, and add a test, generated
from design data, that the bonus math matches the simulator. Delete a team's entry on FTB `TeamEvent.DELETED`, and
decide what happens to research when a party disbands (see Check 3).

---

## Check 3: owner and team edge cases

How a GTM machine knows its owner: `MetaMachine` persists **only the placing player's UUID** (`ownerUUID`, set in
`MetaMachineBlock.setPlacedBy`). `getOwner()` wraps it in an `FTBOwner` (cached forever in a static map; it holds only
the UUID). The team is resolved **live** on every lookup. So the machine follows the *player*, not the team they were
in when they placed it.

| Case | What happens (tested headlessly unless noted) | Suggested fallback / decision |
|---|---|---|
| Owner offline | `owner_offline`: after the owner logs out, FTB still maps them to their party; the bonus still applies (80 ticks). | None needed. This is the behaviour we want for automation running while players are away. |
| Owner leaves one team, joins another | `owner_changes_team`: after leaving, the machine resolves to the owner's personal team (= their UUID). After joining P2 it resolves to P2 and loses P1's bonus. The machine moves teams with its owner. | Decide which we want. (a) **Follow the player** (current behaviour, simplest, matches GTM's own ownership checks). (b) **Stay with the team**: record the team ID in the machine when placed or when its owner joins a party, and re-stamp it only on explicit action. Recommend (a), plus a way for a team to claim or transfer machines, because stamping goes stale when parties disband. |
| Machine placed before its owner joined a party | `placed_before_party`: after the owner creates a party, the machine resolves to the party at once, with no machine update. Research the owner did *solo* (on their personal team) does **not** carry into the party: the machine runs at 100 ticks. | Decide what joining a party does with solo research: lose it, keep it on the personal team (it returns if they leave), or merge it into the party. Recommend: keep it on the personal team, and give the party the union of members' research only if we want it to be friendly. That is a design choice; the code supports any of them. |
| No owner (world edit, structure template, `/setblock`) | `no_owner`: `getOwner()` is null → no team → no bonus; recipes still run normally. Dedicated server: a `/setblock`-placed controller has `owner=null`. Note that GTM's `MetaMachineBlock.use` makes **the first player who right-clicks an unowned machine its owner**, whoever they are. | Treat as "no research". Keep GTM's first-click claim (it is the natural fix for world-edited bases). Mention it in the quest book, and consider restricting it to players who can edit the chunk. |
| Placed by automation (fake player) | `placed_by_fake_player`: GTM records the **fake player's UUID** as owner (here `[Minecraft]` 41c82c87…). It has no FTB team → no bonus. | For machines placed by a fake player (Create deployer, AE2 annihilation-style placers), fall back to the owner of the block entity that placed it, if the placer exposes one. Otherwise treat the machine as unowned so the first right-click claims it. This needs a small mixin or event hook in `setPlacedBy`. Check which placers the pack has before building it. |
| World reloaded | Dedicated server: research save data and team UUIDs survive the restart, the cache is rebuilt at startup, and the multiblock is formed with 4 segments one second after boot. Team resolution after a reload is a pure function of things that are persisted (GTM's `ownerUUID`, FTB's team files). | None needed. The *client-side* view (Jade/tooltips showing a bonus) is untested: see the manual list. |

**Classes used:** GTM `MetaMachine`, `MachineOwner`, `FTBOwner`, `MetaMachineBlock`. FTB Teams `TeamManagerImpl`
(`playerLoggedIn/playerLoggedOut/createParty`, test only), `PartyTeam` (`invite/join/leave`), `PlayerTeam`.

**Gotchas**
- The tests drive FTB Teams through its implementation class `TeamManagerImpl`, with Forge `FakePlayer`s,
  because 1.20.1's GameTest has no mock `ServerPlayer` helper. That is fine for tests, but production code must use
  only `FTBTeamsAPI`.
- Only the **controller's** owner matters. Hatches may be owned by different players, or by nobody.
- Every lookup goes through `FTBTeamsAPI.api().isManagerLoaded()`. On the client it is false; the client
  manager is a different code path (`FTBOwner` handles it, `TeamResolver` returns null).

**Recommendation.** Resolve owner → effective team live at recipe start; it costs two map lookups. Don't copy
GTM's `FTBOwner.getUUID()`. The open questions are design calls rather than technical risks: whether machines
follow players or teams, and what happens to solo research when a player joins a party. Decide them before writing
the research data format.

---

## Manual test list (Prism Launcher, single player, then a LAN or dedicated server with two accounts)

Build the jar with `./gradlew build` in `spike/` (output in `build/libs/`). Install it together with GTM 7.5.3, LDLib,
Configuration, FTB Teams 2001.3.2, FTB Library and Architectury, plus AE2 and Jade for the tests below. These
are test-only installs; they don't go into the pack. Use a creative world.

1. **Rendering and placement.** Place `spike_segment_assembler`, then build 1, 4 and 8 segments using the GTM
   multiblock preview (JEI/EMI). Check that the preview shows the repeatable aisle and that the controller renders
   formed and unformed. Note any missing-model or missing-lang spam.
2. **AE2 at 8 segments.** Build 8 segments with an HV energy hatch on a creative energy source, an input bus and an
   output bus. Attach an AE2 pattern provider to the input bus (with a pattern for the test recipe: 1 dirt + 1
   cobblestone → 1 diamond, assembler) and an ME interface or import bus on the output bus. Request 64 diamonds.
   Expected: each recipe run shows **8 parallels** (Jade or the controller UI), 8 diamonds per 100-tick run, and
   AE2 keeps all 8 parallels busy from the first run. Repeat with "blocking mode" on and off. Watch for the
   provider pushing only one set at a time, which would leave the machine at 1 parallel.
3. **Research in the client.** Run `/utspike research grant <your name>` and start a recipe: the controller
   UI or Jade should show 80 ticks. Revoke it; the next run shows 100.
4. **Two players.** Player A creates a party and places a machine, then player B joins. Grant to the party, and
   check B's machine (placed after joining) and A's machine both get 80. B leaves: B's machines go back to 100.
5. **Right-click claim.** Use `/setblock` to place a controller, then have player B right-click it first.
   Use GTM's portable scanner to confirm B is now the owner.
6. **Reload in single player.** Quit to title and reopen the world: segments, formed state and the research bonus
   should all survive. Repeat once with the machine running mid-recipe.
7. **Break while running.** While running at 8 parallels, break a segment casing: the machine should stop
   cleanly with no item loss, and resume after the block is replaced.
