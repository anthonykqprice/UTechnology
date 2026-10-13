package dev.utechnology.spike.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import dev.utechnology.spike.research.SpikeModifiers;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.CASING_GRATE;
import static com.gregtechceu.gtceu.common.data.GTBlocks.CASING_STEEL_SOLID;
import static dev.utechnology.spike.UTSpike.REGISTRATE;

public class SpikeMachines {

    public static final int MIN_SEGMENTS = 1;
    public static final int MAX_SEGMENTS = 8;

    /*
     * 3x3 cross-section, aisles run from the controller towards the back.
     * front: XXX / XSX / XXX (controller in the centre)
     * middle: XXX / XGX / XXX repeated 1..8 times (G = grate casing marks a segment)
     * end: XXX / XXX / XXX
     * X = steel casing or a hatch (input bus, output bus, energy hatch).
     */
    public static final MultiblockMachineDefinition SEGMENT_ASSEMBLER = REGISTRATE
            .multiblock("spike_segment_assembler", SegmentAssemblerMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.ASSEMBLER_RECIPES)
            // Re-run modifiers on every consecutive recipe; otherwise GTM reuses the last modified recipe and a
            // research change only applies after the machine goes idle.
            .alwaysTryModifyRecipe(true)
            // No overclocking in the spike so durations stay predictable in tests.
            .recipeModifiers(SpikeModifiers.SEGMENT_PARALLEL, SpikeModifiers.TEAM_RESEARCH)
            .appearanceBlock(CASING_STEEL_SOLID)
            .pattern(definition -> FactoryBlockPattern
                    .start(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.BACK)
                    .aisle("XXX", "XSX", "XXX")
                    .aisleRepeatable(MIN_SEGMENTS, MAX_SEGMENTS, "XXX", "XGX", "XXX")
                    .aisle("XXX", "XXX", "XXX")
                    .where('S', controller(blocks(definition.getBlock())))
                    .where('G', blocks(CASING_GRATE.get()))
                    .where('X', blocks(CASING_STEEL_SOLID.get())
                            .or(abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(2))
                            .or(abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(2))
                            .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2)))
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTCEu.id("block/machines/assembler"))
            .register();

    public static void init() {}
}
