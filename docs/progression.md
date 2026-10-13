# Progression: what unlocks at which tier

CLAUDE.md: every non-GregTech mod is gated to a GT tier through KubeJS recipe changes. This table records the
intended tier and whether the gate exists yet.

| Mod or feature | Tier | Gated yet? | Notes |
|---|---|---|---|
| Applied Energistics 2 (autocrafting) | LV | **No** | Decision `ae2-logistics` (slice1-mechanical.yaml): AE2 is in from LV. The prototype hands out AE2 parts in a kit; recipe gating to LV components comes with milestone 1. |
| EMI, Jade | — | not gated | Information mods. |
| Performance mods (ModernFix, FerriteCore, Embeddium, Entity Culling, spark) | — | not gated | No content. |
| Coil Winder | LV | yes (needs LV motors) | This pack's machine. |
| Bearing Press | MV | yes (needs MV motor and pistons) | This pack's machine. |
| Upgradeable multiblocks (steel frame) | LV | yes (needs LV components) | Frames: aluminium = MV, stainless steel = HV (retrofit kits from those materials). |
| Modules | LV / MV / HV | yes | Specialization lock LV, efficiency coil MV, batch bus HV (each also needs a frame of that tier). |
| GTM Large Wire Factory, Large Assembling Factory, Large Cutter | — | removed | Decision `disable-overlapping-large-multiblocks`. |
