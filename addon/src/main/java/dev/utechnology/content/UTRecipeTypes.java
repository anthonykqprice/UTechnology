package dev.utechnology.content;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;

import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;

import dev.utechnology.UTechnology;

import static dev.utechnology.UTechnology.REGISTRATE;

import net.minecraft.core.registries.BuiltInRegistries;

/** Recipe types of this pack's single-block machines. Their recipes come from the pack's KubeJS scripts. */
public final class UTRecipeTypes {

    public static GTRecipeType COIL_WINDER;
    public static GTRecipeType BEARING_PRESS;

    /** Same steps as GTRecipeTypes.register, in our namespace. Called from GTM's recipe type registration event. */
    private static GTRecipeType register(String name) {
        var type = new GTRecipeType(UTechnology.id(name), GTRecipeTypes.ELECTRIC);
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, type.registryName, type);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, type.registryName, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(type.registryName, type);
        return type;
    }

    static {
        // Recipe viewer category titles and machine UI text use the type's registry name as a lang key
        REGISTRATE.addRawLang("utechnology.coil_winder", "Coil Winder");
        REGISTRATE.addRawLang("utechnology.bearing_press", "Bearing Press");
    }

    public static void init() {
        // winds stator coils: magnetic rod + fine wire
        COIL_WINDER = register("coil_winder").setMaxIOSize(2, 1, 0, 0).setEUIO(IO.IN)
                .setProgressBar(GuiTextures.PROGRESS_BAR_WIREMILL, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSound(GTSoundEntries.MOTOR);
        // presses precision bearings: rings + rod, with lubricant
        BEARING_PRESS = register("bearing_press").setMaxIOSize(2, 1, 1, 0).setEUIO(IO.IN)
                .setProgressBar(GuiTextures.PROGRESS_BAR_COMPRESS, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSound(GTSoundEntries.COMPRESSOR);
    }

    private UTRecipeTypes() {}
}
