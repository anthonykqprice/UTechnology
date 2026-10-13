package dev.utechnology.spike;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import dev.utechnology.spike.machine.SpikeMachines;
import dev.utechnology.spike.research.ResearchCommands;
import dev.utechnology.spike.research.ResearchService;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(UTSpike.MOD_ID)
@SuppressWarnings("removal")
public class UTSpike {

    public static final String MOD_ID = "utspike";
    public static final Logger LOGGER = LogManager.getLogger("utspike");
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public UTSpike() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        REGISTRATE.registerRegistrate();

        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ResearchCommands.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> ResearchService.onServerStarted(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ResearchService.onServerStopped());
    }

    private void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        SpikeMachines.init();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
