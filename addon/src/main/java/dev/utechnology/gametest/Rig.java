package dev.utechnology.gametest;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import dev.utechnology.upgrade.UTMultiblocks;
import dev.utechnology.upgrade.UpgradeData;
import dev.utechnology.upgrade.UpgradeableMultiblockMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds an upgradeable multiblock in a GameTest: controller facing north at {@code ctrl}, segments going south.
 * Hatches: auto-maintenance (so maintenance never slows tests), an HV energy hatch (the frame caps the voltage),
 * an HV input bus (16 slots), an HV output bus and an IV fluid input hatch.
 */
final class Rig {

    /** Where the hatches go: all in the front aisle, or all in the end aisle (the spike's GTM bug). */
    enum Hatches { FRONT, END }

    final UpgradeableMultiblockMachine controller;
    final ItemBusPartMachine input, output;
    final FluidHatchPartMachine fluid;
    final FluidHatchPartMachine lubricant; // null with Hatches.END
    final EnergyHatchPartMachine energy;
    final BlockPos ctrl;
    final int segments;

    private Rig(UpgradeableMultiblockMachine c, ItemBusPartMachine in, ItemBusPartMachine out, FluidHatchPartMachine fl,
                FluidHatchPartMachine lub, EnergyHatchPartMachine en, BlockPos ctrl, int segments) {
        this.lubricant = lub;
        this.controller = c;
        this.input = in;
        this.output = out;
        this.fluid = fl;
        this.energy = en;
        this.ctrl = ctrl;
        this.segments = segments;
    }

    static UpgradeData.Frame frame(String key) {
        var f = UpgradeData.get().frames().get(key);
        if (f == null) throw new GameTestAssertException("no frame " + key + " in the upgrade data (did the pack's kubejs/data load?)");
        return f;
    }

    static Block coreOf(String machine) {
        var def = UTMultiblocks.ALL.get(machine);
        var exp = DesignTests.expectations().getAsJsonObject("multiblocks").getAsJsonObject(machine);
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(exp.get("core").getAsString()));
    }

    static Rig build(GameTestHelper h, BlockPos ctrl, String machine, String frameKey, int segments, Hatches hatches) {
        var f = frame(frameKey);
        BlockState casing = f.casing().defaultBlockState(), box = f.frameBox().defaultBlockState();
        BlockState core = coreOf(machine).defaultBlockState();
        int end = segments + 1;
        // hatch positions, as offsets (dx, dy, aisle)
        Map<BlockPos, BlockState> hatch = new HashMap<>();
        int a = hatches == Hatches.FRONT ? 0 : end;
        if (hatches == Hatches.FRONT) {
            hatch.put(new BlockPos(-1, -1, a), GTMachines.ENERGY_INPUT_HATCH[GTValues.HV].getBlock().defaultBlockState());
            hatch.put(new BlockPos(0, -1, a), GTMachines.ITEM_IMPORT_BUS[GTValues.HV].getBlock().defaultBlockState());
            hatch.put(new BlockPos(1, -1, a), GTMachines.ITEM_EXPORT_BUS[GTValues.HV].getBlock().defaultBlockState());
            hatch.put(new BlockPos(-1, 0, a), GTMachines.AUTO_MAINTENANCE_HATCH.getBlock().defaultBlockState());
            hatch.put(new BlockPos(1, 0, a), GTMachines.FLUID_IMPORT_HATCH[GTValues.IV].getBlock().defaultBlockState());
            // a second fluid hatch, for lubricant (batch bus) next to a recipe's own fluid
            hatch.put(new BlockPos(0, 1, a), GTMachines.FLUID_IMPORT_HATCH[GTValues.EV].getBlock().defaultBlockState());
        } else { // the end aisle's H positions: (0,-1) (-1,0) (0,0) (1,0) (0,1)
            hatch.put(new BlockPos(0, -1, a), GTMachines.ENERGY_INPUT_HATCH[GTValues.HV].getBlock().defaultBlockState());
            hatch.put(new BlockPos(-1, 0, a), GTMachines.ITEM_IMPORT_BUS[GTValues.HV].getBlock().defaultBlockState());
            hatch.put(new BlockPos(0, 0, a), GTMachines.ITEM_EXPORT_BUS[GTValues.HV].getBlock().defaultBlockState());
            hatch.put(new BlockPos(1, 0, a), GTMachines.AUTO_MAINTENANCE_HATCH.getBlock().defaultBlockState());
            hatch.put(new BlockPos(0, 1, a), GTMachines.FLUID_IMPORT_HATCH[GTValues.IV].getBlock().defaultBlockState());
        }
        for (int k = 0; k <= end; k++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (k == 0 && dx == 0 && dy == 0) continue; // controller
                    BlockState s;
                    var key = new BlockPos(dx, dy, k);
                    if (hatch.containsKey(key)) s = hatch.get(key);
                    else if (k > 0 && k < end && dx == 0 && dy == 0) s = core;
                    else if (k == end && dx != 0 && dy != 0) s = box;
                    else s = casing;
                    h.setBlock(ctrl.offset(dx, dy, k), s);
                }
            }
        }
        h.setBlock(ctrl, UTMultiblocks.ALL.get(machine).getBlock().defaultBlockState());
        var c = (UpgradeableMultiblockMachine) machine(h, ctrl);
        if (c.getFrontFacing() != Direction.NORTH) c.setFrontFacing(Direction.NORTH);
        BlockPos hb = ctrl;
        var en = (EnergyHatchPartMachine) machine(h, at(ctrl, hatch, GTMachines.ENERGY_INPUT_HATCH[GTValues.HV].getBlock()));
        h.onEachTick(() -> en.energyContainer.setEnergyStored(en.energyContainer.getEnergyCapacity()));
        return new Rig(c, (ItemBusPartMachine) machine(h, at(ctrl, hatch, GTMachines.ITEM_IMPORT_BUS[GTValues.HV].getBlock())),
                (ItemBusPartMachine) machine(h, at(ctrl, hatch, GTMachines.ITEM_EXPORT_BUS[GTValues.HV].getBlock())),
                (FluidHatchPartMachine) machine(h, at(ctrl, hatch, GTMachines.FLUID_IMPORT_HATCH[GTValues.IV].getBlock())),
                hatches == Hatches.FRONT ? (FluidHatchPartMachine) machine(h, at(ctrl, hatch, GTMachines.FLUID_IMPORT_HATCH[GTValues.EV].getBlock())) : null,
                en, ctrl, segments);
    }

    private static BlockPos at(BlockPos ctrl, Map<BlockPos, BlockState> hatch, Block b) {
        for (var e : hatch.entrySet()) if (e.getValue().getBlock() == b) return ctrl.offset(e.getKey());
        throw new IllegalStateException("no hatch " + b);
    }

    static MetaMachine machine(GameTestHelper h, BlockPos rel) {
        MetaMachine m = MetaMachine.getMachine(h.getLevel(), h.absolutePos(rel));
        if (m == null) throw new GameTestAssertException("no machine at " + rel);
        return m;
    }

    BlockPos segmentCore(int i) { // 1-based
        return ctrl.south(i);
    }

    BlockPos endCenter() {
        return ctrl.south(segments + 1);
    }

    void assertFormed(String frameKey, int built, int counted) {
        var c = controller;
        if (!c.isFormed()) throw new GameTestAssertException("not formed");
        if (!c.getFrameKey().equals(frameKey)) throw new GameTestAssertException("frame " + c.getFrameKey() + " != " + frameKey);
        var b = c.bonus();
        if (b == null) throw new GameTestAssertException("no bonus");
        if (b.built() != built) throw new GameTestAssertException("built " + b.built() + " != " + built);
        if (b.counted() != counted) throw new GameTestAssertException("counted " + b.counted() + " != " + counted);
    }

    /** Puts count of an item (or tag's first item) into the input bus, in stacks of 64. */
    void give(String idOrTag, int count) {
        Item item = RecipeView.accepts(idOrTag).stream().findFirst()
                .map(id -> BuiltInRegistries.ITEM.get(new ResourceLocation(id))).orElse(Items.AIR);
        if (item == Items.AIR) throw new GameTestAssertException("no item for " + idOrTag);
        var s = input.getInventory().storage;
        while (count > 0) {
            int slot = -1;
            for (int i = 0; i < s.getSlots(); i++) if (s.getStackInSlot(i).isEmpty()) { slot = i; break; }
            if (slot < 0) throw new GameTestAssertException("input bus full");
            int n = Math.min(64, count);
            s.setStackInSlot(slot, new ItemStack(item, n));
            count -= n;
        }
    }

    void circuit(int config) {
        input.getCircuitInventory().storage.setStackInSlot(0, IntCircuitBehaviour.stack(config));
    }

    void fluid(String id, int amount) {
        var hatch = id.equals("gtceu:lubricant") && lubricant != null ? lubricant : fluid;
        hatch.tank.fillInternal(new FluidStack(ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id)), amount),
                IFluidHandler.FluidAction.EXECUTE);
    }

    int outputCount(Item item) {
        int n = 0;
        var s = output.getInventory().storage;
        for (int i = 0; i < s.getSlots(); i++) if (s.getStackInSlot(i).is(item)) n += s.getStackInSlot(i).getCount();
        return n;
    }
}
