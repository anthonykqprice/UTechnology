package dev.utechnology.upgrade;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;

/**
 * The shared structure (design/multiblocks.yaml framework.structure), rows bottom to top, aisles from the
 * controller backwards:
 * <pre>
 * front:   HHH / HXH / HHH      X controller, H frame casing or hatch
 * segment: CCC / CKC / CCC      repeats 1..max_segments_built; C frame casing, K this machine's core
 * end:     FHF / HHH / FHF      F frame box
 * </pre>
 * The end aisle's first checked block is a frame box, never a casing, so GTM's matcher stops its failed segment
 * attempts there before reaching any hatch (the spike's double-counting bug). Hatch limits come from the data.
 */
public final class UpgradePattern {

    private static final Map<String, PartAbility> ABILITIES = Map.of(
            "maintenance", PartAbility.MAINTENANCE,
            "energy_input", PartAbility.INPUT_ENERGY,
            "item_input_bus", PartAbility.IMPORT_ITEMS,
            "item_output_bus", PartAbility.EXPORT_ITEMS,
            "fluid_input_hatch", PartAbility.IMPORT_FLUIDS,
            "fluid_output_hatch", PartAbility.EXPORT_FLUIDS);

    /** Hatch limits when no data is loaded yet (a client building a preview); the data's values are the real ones. */
    static final Map<String, int[]> PREVIEW_LIMITS = Map.of(
            "maintenance", new int[] { 1, 1 }, "energy_input", new int[] { 1, 2 }, "item_input_bus", new int[] { 1, 6 },
            "item_output_bus", new int[] { 1, 2 }, "fluid_input_hatch", new int[] { 0, 2 }, "fluid_output_hatch", new int[] { 0, 1 });

    static BlockPattern build(MultiblockMachineDefinition def, String specKey, Block core) {
        var spec = UpgradeData.get().specs().get(specKey);
        Map<String, int[]> limits = spec == null ? PREVIEW_LIMITS : spec.hatchLimits();
        int maxBuilt = spec == null ? 32 : spec.maxSegmentsBuilt();
        TraceabilityPredicate hatch = FramePredicates.casing();
        for (var e : limits.entrySet()) {
            PartAbility ability = ABILITIES.get(e.getKey());
            if (ability == null) throw new IllegalArgumentException("unknown hatch kind " + e.getKey());
            int min = e.getValue()[0], max = e.getValue()[1];
            if (max <= 0) continue;
            var p = abilities(ability).setMaxGlobalLimited(max);
            if (min > 0) p.setMinGlobalLimited(min);
            hatch = hatch.or(p);
        }
        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.BACK)
                .aisle("HHH", "HXH", "HHH")
                .aisleRepeatable(1, maxBuilt, "CCC", "CKC", "CCC")
                .aisle("FHF", "HHH", "FHF")
                .where('X', controller(blocks(def.getBlock())))
                .where('K', FramePredicates.core(core))
                .where('C', FramePredicates.casing())
                .where('F', FramePredicates.frameBox())
                .where('H', hatch)
                .build();
    }

    /** A pattern factory that rebuilds the pattern when the upgrade data reloads (hatch limits may change). */
    static Supplier<BlockPattern> factory(MultiblockMachineDefinition def, String specKey, Supplier<Block> core) {
        return new Supplier<>() {

            private BlockPattern pattern;
            private int version = -1;

            @Override
            public synchronized BlockPattern get() {
                int v = UpgradeData.get().version();
                if (pattern == null || version != v) {
                    pattern = build(def, specKey, core.get());
                    version = v;
                }
                return pattern;
            }
        };
    }

    private UpgradePattern() {}
}
