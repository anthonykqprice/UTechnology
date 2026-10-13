package dev.utechnology.upgrade;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The upgradeable multiblocks' recipe modifier, run before GTM's overclocking (design modifier_order: segments,
 * then modules, then overclocking). It reads the machine's cached bonus (computed when the structure formed or a
 * module changed), so per recipe it only does the recipe filter, the lock check and GTM's input-limited parallel
 * count.
 */
public final class UpgradeModifier implements RecipeModifier {

    public static final UpgradeModifier INSTANCE = new UpgradeModifier();

    @Override
    public ModifierFunction getModifier(com.gregtechceu.gtceu.api.machine.MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof UpgradeableMultiblockMachine m) || !m.isFormed()) return ModifierFunction.NULL;
        if (!m.accepts(recipe))
            return ModifierFunction.cancel(Component.translatable("utechnology.recipe.filtered"));
        var b = m.bonus();
        if (b == null) return ModifierFunction.cancel(Component.translatable("utechnology.recipe.no_data"));
        if (b.locked() && m.getLockedRecipe() != null && !m.getLockedRecipe().equals(recipe.getId()))
            return ModifierFunction.cancel(Component.translatable("utechnology.recipe.locked"));

        // Parallels: limited by the inputs present and output space, never by power (decision free-parallels):
        // the EU/t is not multiplied, so all parallels share one recipe's draw.
        int limit = b.parallelLimit();
        int p = limit <= 1 ? 1 : ParallelLogic.getParallelAmountWithoutEU(machine, recipe, limit);
        if (p <= 0) return ModifierFunction.NULL;

        var fb = ModifierFunction.builder();
        if (p > 1) fb.modifyAllContents(ContentModifier.multiplier(p)).parallels(p);
        double duration = b.duration() * b.lockDuration();
        if (duration != 1) fb.durationMultiplier(duration);
        if (b.energy() != 1) fb.eutMultiplier(b.energy());
        ModifierFunction f = fb.build();
        if (!b.upkeepPerParallel().isEmpty()) f = f.andThen(upkeep(b.upkeepPerParallel(), p));
        return f;
    }

    /** Adds module upkeep fluids (e.g. the batch bus's lubricant) as recipe inputs, per parallel. */
    static ModifierFunction upkeep(List<BonusCalculator.Upkeep> upkeep, int parallels) {
        return recipe -> {
            var inputs = new HashMap<>(recipe.inputs);
            List<Content> fluids = new ArrayList<>(inputs.getOrDefault(FluidRecipeCapability.CAP, List.of()));
            for (var u : upkeep) {
                var fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(u.fluid()));
                if (fluid == null) return null;
                int max = ChanceLogic.getMaxChancedValue();
                fluids.add(new Content(FluidIngredient.of(fluid, u.amount() * parallels), max, max, 0));
            }
            inputs.put(FluidRecipeCapability.CAP, fluids);
            var copy = new GTRecipe(recipe.recipeType, recipe.id, inputs, new HashMap<>(recipe.outputs),
                    new HashMap<>(recipe.tickInputs), new HashMap<>(recipe.tickOutputs),
                    new HashMap<>(recipe.inputChanceLogics), new HashMap<>(recipe.outputChanceLogics),
                    new HashMap<>(recipe.tickInputChanceLogics), new HashMap<>(recipe.tickOutputChanceLogics),
                    new ArrayList<>(recipe.conditions), new ArrayList<>(recipe.ingredientActions), recipe.data,
                    recipe.duration, recipe.recipeCategory, recipe.groupColor);
            copy.parallels = recipe.parallels;
            copy.subtickParallels = recipe.subtickParallels;
            copy.ocLevel = recipe.ocLevel;
            copy.batchParallels = recipe.batchParallels;
            return copy;
        };
    }

    private UpgradeModifier() {}
}
