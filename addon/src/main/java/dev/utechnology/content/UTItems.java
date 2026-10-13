package dev.utechnology.content;

import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import static dev.utechnology.UTechnology.REGISTRATE;

/** Parts, retrofit kits and modules. Textures are placeholders borrowed from GTM and vanilla. */
public final class UTItems {

    static {
        REGISTRATE.creativeModeTab(() -> UTCreativeTab.TAB);
    }

    private static ItemEntry<Item> item(String id, String name, String texture, int stack) {
        return REGISTRATE.item(id, Item::new)
                .lang(name)
                .properties(p -> p.stacksTo(stack))
                .model((ctx, prov) -> prov.generated(ctx::getEntry, new ResourceLocation(texture)))
                .register();
    }

    // parts (slice1-mechanical.yaml)
    public static final ItemEntry<Item> MV_STATOR_COIL = item("mv_stator_coil", "MV Stator Coil", "gtceu:item/mv_voltage_coil", 64);
    public static final ItemEntry<Item> HV_STATOR_COIL = item("hv_stator_coil", "HV Stator Coil", "gtceu:item/hv_voltage_coil", 64);
    public static final ItemEntry<Item> PRECISION_BEARING = item("precision_bearing", "Precision Bearing", "minecraft:item/iron_nugget", 64);

    // retrofit kits (multiblocks.yaml); right-click a controller with them (phase 4)
    public static final ItemEntry<Item> STEEL_RETROFIT_KIT = item("steel_retrofit_kit", "Steel Retrofit Kit", "minecraft:item/iron_ingot", 64);
    public static final ItemEntry<Item> ALUMINIUM_RETROFIT_KIT = item("aluminium_retrofit_kit", "Aluminium Retrofit Kit", "minecraft:item/paper", 64);
    public static final ItemEntry<Item> STAINLESS_STEEL_RETROFIT_KIT = item("stainless_steel_retrofit_kit", "Stainless Steel Retrofit Kit", "minecraft:item/netherite_scrap", 64);

    // modules (multiblocks.yaml); installed in a controller's module slots (phase 4)
    public static final ItemEntry<Item> SPECIALIZATION_LOCK = item("specialization_lock", "Specialization Lock", "gtceu:item/lv_sensor", 16);
    public static final ItemEntry<Item> EFFICIENCY_COIL = item("efficiency_coil", "Efficiency Coil", "gtceu:item/mv_field_generator", 16);
    public static final ItemEntry<Item> BATCH_BUS = item("batch_bus", "Batch Bus", "gtceu:item/hv_conveyor_module", 16);

    public static boolean isModule(Item item) {
        return item == SPECIALIZATION_LOCK.get() || item == EFFICIENCY_COIL.get() || item == BATCH_BUS.get();
    }

    public static boolean isKit(Item item) {
        return item == STEEL_RETROFIT_KIT.get() || item == ALUMINIUM_RETROFIT_KIT.get() || item == STAINLESS_STEEL_RETROFIT_KIT.get();
    }

    public static void init() {}

    private UTItems() {}
}
