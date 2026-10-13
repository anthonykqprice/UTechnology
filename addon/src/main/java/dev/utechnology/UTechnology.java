package dev.utechnology;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(UTechnology.MOD_ID)
public class UTechnology {

    public static final String MOD_ID = "utechnology";
    public static final Logger LOGGER = LogManager.getLogger("utechnology");
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public UTechnology() {
        REGISTRATE.registerRegistrate();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
