package dev.utechnology.upgrade;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import dev.utechnology.content.UTBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static dev.utechnology.UTechnology.REGISTRATE;

/**
 * The prototype's four upgradeable multiblocks: thin definitions on top of {@link UpgradeableMultiblockMachine}.
 * Their recipe type and core block are fixed at registration; everything that can be rebalanced (frames, caps,
 * slots, modules, hatch limits, recipe filter) comes from the data pack. A GameTest checks these agree with it.
 */
public final class UTMultiblocks {

    public static final Map<String, MultiblockMachineDefinition> ALL = new LinkedHashMap<>();

    public static MultiblockMachineDefinition COMPONENT_ASSEMBLER, ASSEMBLY_HALL, WIRE_DRAWING_LINE, TURNING_LINE;

    private static MultiblockMachineDefinition register(String key, String name, GTRecipeType type,
                                                       Supplier<Block> core, String overlay) {
        var def = REGISTRATE.multiblock(key, holder -> new UpgradeableMultiblockMachine(holder, key))
                .langValue(name)
                .tooltips(Component.translatable("utechnology.machine.tooltip.upgradeable"))
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(type)
                // segments (parallels), then modules, then overclocking; free parallels (see UpgradeModifier)
                .recipeModifiers(UpgradeModifier.INSTANCE, GTRecipeModifiers.OC_NON_PERFECT)
                // re-run modifiers for every recipe, so a module or segment change applies on the next one
                .alwaysTryModifyRecipe(true)
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .pattern(d -> UpgradePattern.build(d, key, core.get()))
                .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTCEu.id("block/machines/" + overlay))
                .register();
        def.setPatternFactory(UpgradePattern.factory(def, key, core));
        ALL.put(key, def);
        return def;
    }

    public static void init() {
        COMPONENT_ASSEMBLER = register("component_assembler", "Component Assembler", GTRecipeTypes.ASSEMBLER_RECIPES,
                UTBlocks.ASSEMBLY_ARM_HOUSING::get, "assembler");
        ASSEMBLY_HALL = register("assembly_hall", "Assembly Hall", GTRecipeTypes.ASSEMBLER_RECIPES,
                UTBlocks.ASSEMBLY_BENCH_UNIT::get, "assembler");
        WIRE_DRAWING_LINE = register("wire_drawing_line", "Wire Drawing Line", GTRecipeTypes.WIREMILL_RECIPES,
                UTBlocks.DRAWING_DIE_UNIT::get, "wiremill");
        TURNING_LINE = register("turning_line", "Turning Line", GTRecipeTypes.LATHE_RECIPES,
                UTBlocks.SPINDLE_UNIT::get, "lathe");
    }

    private UTMultiblocks() {}
}
