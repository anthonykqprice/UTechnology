package dev.utechnology.spike.research;

import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;

import dev.utechnology.spike.machine.SegmentAssemblerMachine;

import java.util.UUID;

public final class SpikeModifiers {

    /** Same shape as GTRecipeModifiers.hatchParallel, but the limit is the segment count. */
    public static final RecipeModifier SEGMENT_PARALLEL = (machine, recipe) -> {
        if (!(machine instanceof SegmentAssemblerMachine m) || !m.isFormed()) return ModifierFunction.IDENTITY;
        int limit = m.getSegments();
        if (limit <= 1) return ModifierFunction.IDENTITY;
        int parallels = ParallelLogic.getParallelAmount(machine, recipe, limit);
        if (parallels == 0) return ModifierFunction.NULL;
        if (parallels == 1) return ModifierFunction.IDENTITY;
        return ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .eutMultiplier(parallels)
                .parallels(parallels)
                .build();
    };

    /** Reads the cached team bonus. Never touches save data or the research tree. */
    public static final RecipeModifier TEAM_RESEARCH = (machine, recipe) -> {
        UUID team = TeamResolver.teamOf(machine);
        TeamBonus bonus = ResearchCache.get(team);
        if (bonus.durationMultiplier() == 1.0) return ModifierFunction.IDENTITY;
        return ModifierFunction.builder().durationMultiplier(bonus.durationMultiplier()).build();
    };

    private SpikeModifiers() {}
}
