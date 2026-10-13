# GregTech Progression Modpack

## What this pack is
An expert-style tech progression pack built around GregTech CEu Modern (GTM) voltage tiers.
Inspirations: Monifactory / Nomi-CEu, GregTech: New Horizons, Star Technology.
Target difficulty and length: comparable to Star Technology. That means a long, demanding
progression that is still fair, with heavy recipe integration, guided by a quest book.
It is not GTNH-level grind.

## Fixed decisions (do not change without asking me)
- Minecraft: 1.20.1
- Loader: Forge (1.20.1 is where GTM and most GT-focused addons/packs live)
- Core mod: GregTech CEu Modern, latest 1.20.1 release (look it up; don't guess version numbers)
- Recipes and data: KubeJS (+ GTM's KubeJS integration) for recipe changes and custom materials.
  Generate these from the design data files in design/ wherever possible.
- Machines, upgrades and recipe bonuses: a Java addon for GregTech Modern (Forge 1.20.1, Java 17).
  Anything that runs during play goes here, not in KubeJS.
- Quests: FTB Quests, for guidance only. Progression systems (upgrades, unlocks) are never built on quests.
- Pack management: packwiz. Every mod is added through packwiz, never by dropping jars in manually
- Start type: skyblock, like Star Technology  <-- change this line if I want a normal overworld start

## Progression design
- GregTech voltage tiers are the backbone: Steam -> ULV -> LV -> MV -> HV -> EV -> IV -> LuV -> ZPM -> UV -> UHV
- Every non-GT mod must be gated to a GT tier through KubeJS recipe changes.
  Keep a table of which mod/feature unlocks at which tier in docs/progression.md.
- Build in milestones. Milestone 1 = playable from start through HV with quests.
  Don't start the next milestone until the current one is tested in-game.
- Hard mode GT configs on. Don't make things tedious for no reason. Every tier should
  introduce a new mechanic or processing chain, not just bigger numbers.
- Design files live in design/. The slice recipe files (e.g. design/slice1-mechanical.yaml)
  are the source of truth for recipes; the simulator and generated KubeJS both read them.
  Decisions and their reasons are recorded in each file's decisions section.
- Machines are upgraded individually, and upgrades are optional. A multiblock's frame (casing
  material) sets its max voltage, segment cap and module slots. Players upgrade a frame with
  retrofit kits (right-click the controller; one kit per 8 casings; old casings are returned), add
  segments by building them, and install a few modules with real tradeoffs. A basic machine runs
  every recipe its voltage allows, so unupgraded machines stay useful for passive and low-volume
  work. Upgrade effort is the intended soft limit on machine count. There is no global research
  tree (design/research-v1.yaml is superseded and kept for reference only).
- AE2 is the logistics tool for the whole pack. Recipe conflicts (recipes in one machine type that
  accept the same inputs) are allowed, as in stock GregTech; working around them is part of the
  game. But every conflict must be solvable with GregTech's standard tools (programmed circuits,
  distinct input buses), so a player never needs blocking mode with multiplied patterns. Then a
  pattern provider in non-blocking mode feeding a distinct bus fills the machine's parallels, and
  upgrading a machine's parallels never requires re-encoding patterns.
  Conflicts in our own recipes are deliberate: the validator lists every conflict so each one is
  a choice. Keep each recipe's number of distinct item inputs within what the input buses at that
  tier can hold.
- Factories should grow physically: frames raise segment caps, and players build out to use them.
  One multiblock per core machine type; no recipe-type switching.

## Engineering priorities
- Stability and performance come first. Writing more code is fine; fragile or slow code is not.
- No per-tick or per-recipe logic in KubeJS. Hot paths live in the Java addon.
- A machine's upgrades (frame, segment count, modules) live on the machine and are persisted with
  it. Compute its bonuses when the structure forms or a module changes, never per recipe.
- Frames, segment caps, modules and their effects are data (data pack JSON generated from
  design/), so rebalancing never needs a recompile. Item and block IDs never change once released.
- The addon's bonus math must match the simulator. Cover it with automated tests generated from
  the design data.
- Performance-test large multiblocks (structure checks) before raising segment caps.

## Lessons from spike 1 (docs/spike-1.md)
- Build on GTM 7.5.3 (the newest 1.20.1 release). The 1.20.1 branch head is the unreleased 8.0, which
  moves the pattern API to com.gregtechceu.gtceu.api.multiblock.pattern. Keep all pattern and
  segment-count code in one base class so the 8.0 migration touches one place.
- Segments use FactoryBlockPattern.aisleRepeatable and BlockPattern.getFormedRepetitionCount. The
  count is shared by every controller of a type: read it inside synchronized (pattern) and copy it
  per controller. Persist the copied count.
- GTM bug: a limited hatch (setMaxGlobalLimited) in the aisle right after a repeatable aisle is
  counted several times, so the structure forms only at some lengths. Never put limited hatches
  there, or make that aisle's first checked block differ from the segment's.
- Upgradeable multiblocks set alwaysTryModifyRecipe(true), so a module or segment change applies on
  the next recipe, not only after the machine idles. Modifier order: parallel, then modules, then
  overclock.
- If team features ever return: resolve a machine's team with FTBTeamsAPI
  TeamManager.getTeamForPlayerID(ownerUUID) (the effective team); FTBOwner.getUUID()/getTeam()
  return the personal team. Production code uses only the FTBTeamsAPI.

## Rules for adding mods
- Prefer Modrinth. Use CurseForge only if a mod isn't on Modrinth, and tell me when you do.
- Before adding a mod: confirm it has a 1.20.1 Forge build, list its required dependencies,
  and check for overlap with mods already in the pack.
- Never download jars from anywhere except Modrinth/CurseForge.
- Mark client-only mods as client side in packwiz so a server export works.
- Include performance mods from the start (GT packs are heavy).

## Reference material
- Monifactory and Star Technology have their source on GitHub. Read their KubeJS scripts
  and quest files to learn patterns. Check the license before copying anything verbatim,
  and credit them if we adapt something.
- GTM wiki/docs for the KubeJS API, and the GTM source (GregTechCEu/GregTech-Modern, branch 1.20.1)
  for the Java API. Check them instead of inventing method names.

## Workflow
- I don't review code. Make engineering decisions yourself, and explain them in plain language in
  docs/ so I can follow what was built and why.
- Ask me only about gameplay or design questions the design files don't answer, or before anything
  irreversible. Don't wait for approval on engineering plans, mod choices within the rules above,
  or code structure.
- Because nobody reviews the code, verification is automated: tests and GameTests for every
  feature, CI that must pass, the validator, and an independent review of each finished piece of
  work by a separate agent that hasn't seen it being written. Fix what the review finds.
- Commit to git after each working change, with a clear message.
- I'll test in-game (Prism Launcher) and paste crash reports or latest.log. Diagnose from the
  log; don't guess.
- Keep docs/changelog.md updated.
