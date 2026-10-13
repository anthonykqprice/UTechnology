package dev.utechnology.content;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import dev.utechnology.UTechnology;

import java.util.Locale;

import static dev.utechnology.UTechnology.REGISTRATE;

/** Single-block machines: coil winder (LV-HV) and bearing press (MV-HV). Built like GTM's own simple machines. */
public final class UTMachines {

    public static MachineDefinition[] COIL_WINDER;
    public static MachineDefinition[] BEARING_PRESS;

    /**
     * GTMachineUtils.registerSimpleMachines with our registrate, a borrowed GTM overlay texture (placeholder),
     * and the same overclocking (OC_NON_PERFECT: each tier above the recipe gives 2x speed for 4x EU/t).
     */
    private static MachineDefinition[] simple(String name, GTRecipeType type, String overlay, int... tiers) {
        MachineDefinition[] defs = new MachineDefinition[GTValues.TIER_COUNT];
        for (int tier : tiers) {
            defs[tier] = REGISTRATE.machine(GTValues.VN[tier].toLowerCase(Locale.ROOT) + "_" + name,
                    holder -> new SimpleTieredMachine(holder, tier, GTMachineUtils.defaultTankSizeFunction))
                    .tier(tier)
                    .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                    .langValue("%s %s %s".formatted(GTValues.VLVH[tier], FormattingUtil.toEnglishName(name), GTValues.VLVT[tier]))
                    .editableUI(SimpleTieredMachine.EDITABLE_UI_CREATOR.apply(UTechnology.id(name), type))
                    .rotationState(RotationState.NON_Y_AXIS)
                    .recipeType(type)
                    .workableTieredHullModel(GTCEu.id("block/machines/" + overlay))
                    .tooltips(GTMachineUtils.workableTiered(tier, GTValues.V[tier], GTValues.V[tier] * 64, type,
                            GTMachineUtils.defaultTankSizeFunction.applyAsInt(tier), true))
                    .register();
        }
        return defs;
    }

    public static void init() {
        COIL_WINDER = simple("coil_winder", UTRecipeTypes.COIL_WINDER, "wiremill", GTValues.LV, GTValues.MV, GTValues.HV);
        BEARING_PRESS = simple("bearing_press", UTRecipeTypes.BEARING_PRESS, "compressor", GTValues.MV, GTValues.HV);
    }

    private UTMachines() {}
}
