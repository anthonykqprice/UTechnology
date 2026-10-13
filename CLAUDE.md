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
- Machines, research and recipe bonuses: a Java addon for GregTech Modern (Forge 1.20.1, Java 17).
  Anything that runs during play goes here, not in KubeJS.
- Quests: FTB Quests, for guidance only. Progression systems (research, unlocks) are never built on quests.
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
- Upgrades are global research, not per-machine modules. Research multiblocks consume items,
  power and time through ordinary GregTech recipes. Unlocks apply to all of a team's machines.
  Research is team-based through FTB Teams (a solo player is a team of one); machines find their
  team through their owner.
  No mutually exclusive branches: cost and time are the limits. Branches end in repeatable nodes.
- AE2 is the logistics tool for the whole pack. Design every recipe for autocrafting: within one
  machine type, no two recipes may accept the same set of inputs, and avoid non-consumed inputs
  (programmed circuits, catalysts) where possible. Research prerequisites live in the research
  tree, never as items a machine must hold.
- Factories should grow physically: research raises caps (e.g. multiblock segments), players
  build out to use them. One multiblock per core machine type; no recipe-type switching.

## Engineering priorities
- Stability and performance come first. Writing more code is fine; fragile or slow code is not.
- No per-tick or per-recipe logic in KubeJS. Hot paths live in the Java addon.
- Cache research bonuses per team and recompute only when something unlocks. Machines read the
  cached value when a recipe starts.
- Research progress lives in versioned world save data. Node and item IDs never change once released.
- The research tree, costs and effects are data (data pack JSON generated from design/), so
  rebalancing never needs a recompile.
- The addon's bonus math must match the simulator. Cover it with automated tests generated from
  the design data.
- Performance-test large multiblocks (structure checks) before raising segment caps.

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
- Before writing a research tree screen from scratch, check whether an existing 1.20.1 Forge mod
  already provides one we could build on.

## Workflow
- Propose a plan and wait for my approval before large changes (new mods, recipe overhauls).
- Commit to git after each working change, with a clear message.
- I'll test in-game (Prism Launcher) and paste crash reports or latest.log. Diagnose from the
  log; don't guess.
- Keep docs/changelog.md updated.
