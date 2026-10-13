package dev.utechnology;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import dev.utechnology.content.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(UTechnology.MOD_ID)
@SuppressWarnings("removal")
public class UTechnology {

    public static final String MOD_ID = "utechnology";
    public static final Logger LOGGER = LogManager.getLogger("utechnology");
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public UTechnology() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        UTCreativeTab.init();
        UTItems.init();
        UTBlocks.init();
        modBus.addGenericListener(GTRecipeType.class, this::registerRecipeTypes);
        modBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        REGISTRATE.registerRegistrate();
    }

    private void registerRecipeTypes(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        UTRecipeTypes.init();
    }

    private void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        UTMachines.init();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
