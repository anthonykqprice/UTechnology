package dev.utechnology.content;

import dev.utechnology.UTechnology;

import com.tterrag.registrate.util.entry.RegistryEntry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

import static dev.utechnology.UTechnology.REGISTRATE;

public final class UTCreativeTab {

    public static final RegistryEntry<CreativeModeTab> TAB = REGISTRATE.defaultCreativeTab(UTechnology.MOD_ID,
            b -> b.title(Component.literal("UTechnology")).icon(() -> UTItems.MV_STATOR_COIL.asStack()).build())
            .register();

    public static void init() {}

    private UTCreativeTab() {}
}
