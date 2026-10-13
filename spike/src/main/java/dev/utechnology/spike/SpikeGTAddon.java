package dev.utechnology.spike;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

@GTAddon
public class SpikeGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return UTSpike.REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return UTSpike.MOD_ID;
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        // Test-only assembler recipe with inputs no stock recipe uses: 1 dirt + 1 cobblestone -> 1 diamond.
        // LV (30 EU/t), 100 ticks, so 8 parallels need 240 EU/t.
        GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(UTSpike.id("spike_test"))
                .inputItems(Items.DIRT)
                .inputItems(Items.COBBLESTONE)
                .outputItems(Items.DIAMOND)
                .EUt(30)
                .duration(100)
                .save(provider);
    }
}
