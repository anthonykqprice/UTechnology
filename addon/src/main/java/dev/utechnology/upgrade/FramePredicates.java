package dev.utechnology.upgrade;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Pattern predicates for frame casings and frame boxes. They accept the blocks of any frame in the current
 * upgrade data and record, in the pattern's match context, which frame each block belongs to and where it is.
 * The machine then requires all of them to be the same frame, and uses the positions for retrofitting.
 */
public final class FramePredicates {

    public static final String FRAMES = "utechnology:frames";
    public static final String CASINGS = "utechnology:casing_positions";
    public static final String BOXES = "utechnology:frame_box_positions";
    public static final String CORES = "utechnology:core_positions";

    private static TraceabilityPredicate of(Function<UpgradeData.Frame, Block> part, String positions, Block fallback) {
        return new TraceabilityPredicate((MultiblockState state) -> {
            Block b = state.getBlockState().getBlock();
            for (UpgradeData.Frame f : UpgradeData.get().frames().values()) {
                if (part.apply(f) == b) {
                    var ctx = state.getMatchContext();
                    ctx.<Set<String>>getOrCreate(FRAMES, HashSet::new).add(f.key());
                    ctx.<LongOpenHashSet>getOrCreate(positions, LongOpenHashSet::new).add(state.getPos().asLong());
                    return true;
                }
            }
            return false;
        }, () -> {
            var frames = UpgradeData.get().frames().values();
            if (frames.isEmpty()) return new BlockInfo[] { BlockInfo.fromBlockState(fallback.defaultBlockState()) };
            return frames.stream().map(f -> BlockInfo.fromBlockState(part.apply(f).defaultBlockState())).toArray(BlockInfo[]::new);
        });
    }

    /** A frame casing (e.g. gtceu:solid_machine_casing for steel). */
    public static TraceabilityPredicate casing() {
        return of(UpgradeData.Frame::casing, CASINGS, GTBlocks.CASING_STEEL_SOLID.get());
    }

    /** A frame box of the frame material (e.g. gtceu:steel_frame). */
    public static TraceabilityPredicate frameBox() {
        Block steelBox = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("gtceu", "steel_frame"));
        return of(UpgradeData.Frame::frameBox, BOXES, steelBox);
    }

    /** The machine's core block; its positions give the segment count (one core per segment). */
    public static TraceabilityPredicate core(Block core) {
        return new TraceabilityPredicate((MultiblockState state) -> {
            if (state.getBlockState().getBlock() != core) return false;
            state.getMatchContext().<LongOpenHashSet>getOrCreate(CORES, LongOpenHashSet::new).add(state.getPos().asLong());
            return true;
        }, () -> new BlockInfo[] { BlockInfo.fromBlockState(core.defaultBlockState()) });
    }

    private FramePredicates() {}
}
