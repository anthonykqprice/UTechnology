package dev.utechnology.gametest;

import dev.utechnology.UTechnology;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The addon, GTM and KubeJS load together on a server. */
@GameTestHolder(UTechnology.MOD_ID)
@PrefixGameTestTemplate(false)
public class SmokeTests {

    @GameTest(template = "empty")
    public static void modsLoaded(GameTestHelper h) {
        for (String mod : new String[] { UTechnology.MOD_ID, "gtceu", "kubejs" })
            if (!ModList.get().isLoaded(mod)) h.fail(mod + " not loaded");
        h.succeed();
    }
}
