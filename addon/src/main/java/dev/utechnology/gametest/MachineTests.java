package dev.utechnology.gametest;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import dev.utechnology.UTechnology;
import dev.utechnology.content.UTItems;
import dev.utechnology.content.UTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Each single-block machine runs one of its recipes (loaded from the pack's generated KubeJS script) at the
 * right voltage and duration, overclocking like GTM's own machines (2x speed for 4x EU/t per tier above).
 */
@GameTestHolder(UTechnology.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineTests {

    static final BlockPos POS = new BlockPos(2, 1, 2);

    static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        if (item == net.minecraft.world.item.Items.AIR) throw new GameTestAssertException("no item " + id);
        return item;
    }

    static SimpleTieredMachine place(GameTestHelper h, MachineDefinition def) {
        h.setBlock(POS, def.getBlock().defaultBlockState());
        var m = (SimpleTieredMachine) MetaMachine.getMachine(h.getLevel(), h.absolutePos(POS));
        if (m == null) throw new GameTestAssertException("machine not placed");
        h.onEachTick(() -> m.energyContainer.setEnergyStored(m.energyContainer.getEnergyCapacity()));
        return m;
    }

    static void give(SimpleTieredMachine m, int slot, String id, int count) {
        m.importItems.storage.setStackInSlot(slot, new ItemStack(item(id), count));
    }

    static int count(SimpleTieredMachine m, Item item) {
        int n = 0;
        for (int i = 0; i < m.exportItems.storage.getSlots(); i++) {
            ItemStack s = m.exportItems.storage.getStackInSlot(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /**
     * Runs a recipe and checks the running recipe's duration, EU/t and amperage, and that the output arrives
     * that many ticks after the machine started (measured, not just read from the recipe).
     */
    static void runs(GameTestHelper h, SimpleTieredMachine m, String recipeId, int ticks, long eut, Item out, int outCount) {
        long[] started = { -1 };
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (!m.recipeLogic.isWorking()) throw new GameTestAssertException("not working");
                    started[0] = h.getTick();
                })
                .thenExecute(() -> {
                    var r = m.recipeLogic.getLastRecipe();
                    if (!r.getId().toString().equals(recipeId)) throw new GameTestAssertException("running " + r.getId() + ", expected " + recipeId);
                    if (r.duration != ticks) throw new GameTestAssertException("duration " + r.duration + " != " + ticks);
                    var e = RecipeHelper.getRealEUt(r);
                    if (e.voltage() != eut) throw new GameTestAssertException("EU/t " + e.voltage() + " != " + eut);
                    if (e.amperage() != 1) throw new GameTestAssertException("amperage " + e.amperage() + " != 1");
                })
                .thenWaitUntil(() -> {
                    if (count(m, out) != outCount) throw new GameTestAssertException("output " + count(m, out) + " != " + outCount);
                })
                .thenExecute(() -> {
                    long took = h.getTick() - started[0];
                    if (Math.abs(took - ticks) > 2) throw new GameTestAssertException("took " + took + " ticks, expected " + ticks);
                })
                .thenSucceed();
    }

    static void statorCoilMv(SimpleTieredMachine m) {
        give(m, 0, "gtceu:magnetic_steel_rod", 1);
        give(m, 1, "gtceu:cupronickel_double_wire", 4);
    }

    static void statorCoilHv(SimpleTieredMachine m) {
        give(m, 0, "gtceu:magnetic_steel_rod", 1);
        give(m, 1, "gtceu:electrum_double_wire", 4);
    }

    static void bearing(SimpleTieredMachine m) {
        give(m, 0, "gtceu:stainless_steel_ring", 2);
        give(m, 1, "gtceu:stainless_steel_rod", 1);
        m.importFluids.fillInternal(new FluidStack(ForgeRegistries.FLUIDS.getValue(new ResourceLocation("gtceu:lubricant")), 50),
                IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void lvCoilWinderWindsMvStatorCoil(GameTestHelper h) {
        var m = place(h, UTMachines.COIL_WINDER[GTValues.LV]);
        statorCoilMv(m);
        runs(h, m, "utechnology:coil_winder/stator_coil_mv", 200, 30, UTItems.MV_STATOR_COIL.get(), 1);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lvCoilWinderCannotWindHvStatorCoil(GameTestHelper h) {
        var m = place(h, UTMachines.COIL_WINDER[GTValues.LV]);
        statorCoilHv(m);
        h.runAfterDelay(60, () -> {
            if (m.recipeLogic.isWorking() || m.recipeLogic.getLastRecipe() != null)
                throw new GameTestAssertException("an LV machine ran an MV recipe");
            if (m.importItems.storage.getStackInSlot(1).getCount() != 4) throw new GameTestAssertException("inputs were used");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void mvCoilWinderWindsHvStatorCoil(GameTestHelper h) {
        var m = place(h, UTMachines.COIL_WINDER[GTValues.MV]);
        statorCoilHv(m);
        runs(h, m, "utechnology:coil_winder/stator_coil_hv", 200, 120, UTItems.HV_STATOR_COIL.get(), 1);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void hvCoilWinderOverclocks(GameTestHelper h) {
        var m = place(h, UTMachines.COIL_WINDER[GTValues.HV]);
        statorCoilHv(m);
        runs(h, m, "utechnology:coil_winder/stator_coil_hv", 100, 480, UTItems.HV_STATOR_COIL.get(), 1);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void mvBearingPressPressesBearings(GameTestHelper h) {
        var m = place(h, UTMachines.BEARING_PRESS[GTValues.MV]);
        bearing(m);
        runs(h, m, "utechnology:bearing_press/bearing_hv", 160, 120, UTItems.PRECISION_BEARING.get(), 2);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void hvBearingPressOverclocks(GameTestHelper h) {
        var m = place(h, UTMachines.BEARING_PRESS[GTValues.HV]);
        bearing(m);
        runs(h, m, "utechnology:bearing_press/bearing_hv", 80, 480, UTItems.PRECISION_BEARING.get(), 2);
    }
}
