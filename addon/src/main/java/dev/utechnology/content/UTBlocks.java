package dev.utechnology.content;

import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static dev.utechnology.UTechnology.REGISTRATE;

/** The four multiblock core blocks: one per segment of each machine type. Textures are GTM casing placeholders. */
public final class UTBlocks {

    static {
        REGISTRATE.creativeModeTab(() -> UTCreativeTab.TAB);
    }

    private static BlockEntry<Block> core(String id, String name, String texture) {
        return REGISTRATE.block(id, Block::new)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.strength(5f, 6f).requiresCorrectToolForDrops())
                .lang(name)
                .tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry(),
                        prov.models().cubeAll(ctx.getName(), new ResourceLocation(texture))))
                .simpleItem()
                .register();
    }

    public static final BlockEntry<Block> ASSEMBLY_ARM_HOUSING = core("assembly_arm_housing", "Assembly Arm Housing",
            "gtceu:block/casings/gearbox/machine_casing_gearbox_steel");
    public static final BlockEntry<Block> ASSEMBLY_BENCH_UNIT = core("assembly_bench_unit", "Assembly Bench Unit",
            "gtceu:block/casings/gearbox/machine_casing_gearbox_bronze");
    public static final BlockEntry<Block> DRAWING_DIE_UNIT = core("drawing_die_unit", "Drawing Die Unit",
            "gtceu:block/casings/gearbox/machine_casing_gearbox_stainless_steel");
    public static final BlockEntry<Block> SPINDLE_UNIT = core("spindle_unit", "Spindle Unit",
            "gtceu:block/casings/gearbox/machine_casing_gearbox_titanium");

    public static void init() {}

    private UTBlocks() {}
}
