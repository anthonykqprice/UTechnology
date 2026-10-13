package dev.utechnology.spike.test;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import dev.utechnology.spike.machine.SegmentAssemblerMachine;
import dev.utechnology.spike.machine.SpikeMachines;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class SpikeTestUtil {

    /** Where hatches go. FRONT: all in the controller aisle. END_ENERGY: energy hatch in the end aisle. */
    enum Hatches { FRONT, END_ENERGY }

    record Built(SegmentAssemblerMachine controller, ItemBusPartMachine input, ItemBusPartMachine output,
                 EnergyHatchPartMachine energy, BlockPos controllerRel, int segments) {

        BlockPos segmentCenter(int segment) { // 1-based
            return controllerRel.south(segment);
        }

        BlockPos endCenter() {
            return controllerRel.south(segments + 1);
        }
    }

    static Built build(GameTestHelper h, BlockPos ctrl, int segments, @Nullable UUID owner) {
        return build(h, ctrl, segments, owner, Hatches.FRONT);
    }

    static Built build(GameTestHelper h, BlockPos ctrl, int segments, @Nullable UUID owner, Hatches hatches) {
        BlockState casing = GTBlocks.CASING_STEEL_SOLID.getDefaultState();
        BlockState grate = GTBlocks.CASING_GRATE.getDefaultState();
        BlockPos energyPos = hatches == Hatches.FRONT ? ctrl.offset(-1, -1, 0) :
                ctrl.offset(-1, -1, segments + 1);
        BlockPos inPos = ctrl.offset(0, -1, 0);
        BlockPos outPos = ctrl.offset(1, -1, 0);
        for (int k = 0; k <= segments + 1; k++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = ctrl.offset(dx, dy, k);
                    if (p.equals(ctrl)) continue;
                    boolean centre = dx == 0 && dy == 0;
                    BlockState s;
                    if (p.equals(energyPos)) s = GTMachines.ENERGY_INPUT_HATCH[GTValues.HV].getBlock().defaultBlockState();
                    else if (p.equals(inPos)) s = GTMachines.ITEM_IMPORT_BUS[GTValues.LV].getBlock().defaultBlockState();
                    else if (p.equals(outPos)) s = GTMachines.ITEM_EXPORT_BUS[GTValues.LV].getBlock().defaultBlockState();
                    else if (centre && k >= 1 && k <= segments) s = grate;
                    else s = casing;
                    h.setBlock(p, s);
                }
            }
        }
        h.setBlock(ctrl, SpikeMachines.SEGMENT_ASSEMBLER.getBlock().defaultBlockState());
        var controller = (SegmentAssemblerMachine) machine(h, ctrl);
        controller.setOwnerUUID(owner);
        if (controller.getFrontFacing() != Direction.NORTH) controller.setFrontFacing(Direction.NORTH);
        var energy = (EnergyHatchPartMachine) machine(h, energyPos);
        // Keep the hatch full for the whole test.
        h.onEachTick(() -> energy.energyContainer.setEnergyStored(energy.energyContainer.getEnergyCapacity()));
        return new Built(controller, (ItemBusPartMachine) machine(h, inPos), (ItemBusPartMachine) machine(h, outPos),
                energy, ctrl, segments);
    }

    static MetaMachine machine(GameTestHelper h, BlockPos rel) {
        MetaMachine m = MetaMachine.getMachine(h.getLevel(), h.absolutePos(rel));
        if (m == null) throw new GameTestAssertException("no machine at " + rel);
        return m;
    }

    static void feed(Built b, int sets) {
        b.input.getInventory().storage.setStackInSlot(0, new ItemStack(Items.DIRT, sets));
        b.input.getInventory().storage.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, sets));
    }

    static int diamonds(Built b) {
        int n = 0;
        var s = b.output.getInventory().storage;
        for (int i = 0; i < s.getSlots(); i++) if (s.getStackInSlot(i).is(Items.DIAMOND)) n += s.getStackInSlot(i).getCount();
        return n;
    }

    static void assertFormed(Built b, int segments) {
        if (!b.controller.isFormed()) throw new GameTestAssertException("not formed");
        if (b.controller.getSegments() != segments)
            throw new GameTestAssertException("segments " + b.controller.getSegments() + " != " + segments);
    }

    static void assertNotFormed(Built b) {
        if (b.controller.isFormed()) throw new GameTestAssertException("still formed");
    }

    static GTRecipe runningRecipe(Built b) {
        var r = b.controller.getRecipeLogic().getLastRecipe();
        if (r == null || !b.controller.getRecipeLogic().isWorking())
            throw new GameTestAssertException("no recipe running");
        return r;
    }

    static void assertEq(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) throw new GameTestAssertException(what + ": expected " + expected + " got " + actual);
    }

    static void air(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, Blocks.AIR);
    }

    // ---- FTB Teams fake players ----

    private static final AtomicInteger NAMES = new AtomicInteger();

    /** A fake player that FTB Teams treats as logged in (it gets a personal team). */
    static FakePlayer login(GameTestHelper h, String prefix) {
        String name = (prefix + NAMES.incrementAndGet());
        if (name.length() > 16) name = name.substring(0, 16);
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        TeamManagerImpl.INSTANCE.playerLoggedIn(p, p.getUUID(), name);
        return p;
    }

    static void logout(FakePlayer p) {
        TeamManagerImpl.INSTANCE.playerLoggedOut(p);
    }

    static PartyTeam party(FakePlayer owner, String name) {
        try {
            return TeamManagerImpl.INSTANCE.createParty(owner, name + NAMES.incrementAndGet());
        } catch (Exception e) {
            throw new GameTestAssertException("createParty failed: " + e);
        }
    }

    static void join(PartyTeam party, FakePlayer owner, FakePlayer joiner) {
        try {
            party.invite(owner, List.of(joiner.getGameProfile()));
            party.join(joiner);
        } catch (Exception e) {
            throw new GameTestAssertException("join failed: " + e);
        }
    }

    static void leave(PartyTeam party, FakePlayer p) {
        try {
            party.leave(p.getUUID());
        } catch (Exception e) {
            throw new GameTestAssertException("leave failed: " + e);
        }
    }

    private SpikeTestUtil() {}
}
