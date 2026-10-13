# Mods

Every mod comes from **Modrinth** through packwiz (`pack/mods/*.pw.toml`). None come from CurseForge.
Versions were checked for a Forge 1.20.1 build when they were added (2026-10-13). "Side" is what
packwiz records: client-only mods are left out of a server export.

| Mod | Version (file) | Side | Why it's here | Required deps |
|---|---|---|---|---|
| GregTech CEu Modern | `gtceu-1.20.1-7.5.3.jar` | both | The core of the pack. 7.5.3 is the newest 1.20.1 release (see docs/spike-1.md). | LDLib, Configuration (both embedded in this jar) |
| KubeJS | `kubejs-forge-2001.6.5-build.26.jar` | both | Recipe changes generated from design/. GTM's KubeJS integration is built into GTM and activates when KubeJS is present. | Rhino, Architectury API |
| Rhino | `rhino-forge-2001.2.3-build.10.jar` | both | KubeJS's script engine. | — |
| Architectury API | `architectury-9.2.14-forge.jar` | both | Required by KubeJS. Same version GTM 7.5.3 builds against. | — |
| Applied Energistics 2 | `appliedenergistics2-forge-15.4.11.jar` | both | The pack's logistics and autocrafting tool, from LV (CLAUDE.md). | GuideME |
| GuideME | `guideme-20.1.15.jar` | both | AE2's in-game guide book library. | — |
| Just Enough Items (JEI) | `jei-1.20.1-forge-15.62.0.219.jar` | both | The one recipe viewer. Chosen over EMI because GTM and AE2 both ship mature JEI integrations, including AE2's pattern encoding from JEI. | MezzConfig |
| MezzConfig | `mezz_config-1.20.1-forge-0.6.8.jar` | both | JEI's config library. | — |
| Jade | `Jade-1.20.1-Forge-11.13.3.jar` | both | Shows machine status, recipe progress and parallels when looking at a block. GTM has built-in Jade support. | — |
| ModernFix | `modernfix-forge-5.27.88+mc1.20.1.jar` | both | Performance: faster launch, less memory. Standard in GT packs. | — |
| FerriteCore | `ferritecore-6.0.1-forge.jar` | both | Performance: memory use of block states and models. | — |
| Embeddium | `embeddium-0.3.31+mc1.20.1.jar` | client | Performance: chunk rendering (Sodium port for Forge). | — |
| Entity Culling | `entityculling-forge-1.11.3-mc1.20.1.jar` | client | Performance: skips rendering hidden entities and block entities (GT machines are block entities). | — |
| spark | `spark-1.10.53-forge.jar` | both | Profiler, for checking tick time of large multiblocks. | — |

## Overlap checks
- One recipe viewer (JEI; no EMI or REI), one block-info overlay (Jade; no The One Probe).
- One renderer optimisation (Embeddium; no Rubidium or Sodium forks).
- ModernFix and FerriteCore are routinely used together; ModernFix detects FerriteCore and disables
  its own overlapping patches.

## Considered and left out
- **ImmediatelyFast**: its Modrinth versions are labelled inconsistently (packwiz picked a build named
  for 1.20.2), and it is a client-only renderer that can't be tested in CI. Revisit with a client test.
- **FTB Teams / FTB Quests**: research was superseded by per-machine upgrades, and quests are out of
  scope for the prototype.

## The addon
`utechnology` (this repository's `addon/`) isn't on Modrinth. The pack export script adds the built jar
to the `.mrpack` (see docs/progress.md).
