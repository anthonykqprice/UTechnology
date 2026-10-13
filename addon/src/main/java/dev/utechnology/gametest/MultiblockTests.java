package dev.utechnology.gametest;

import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import dev.utechnology.UTechnology;
import dev.utechnology.gametest.Rig.Hatches;
import dev.utechnology.upgrade.UTMultiblocks;
import dev.utechnology.upgrade.UpgradeData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

import java.util.*;
import java.util.function.Consumer;

/** Phase 4: the upgradeable multiblock framework and the four multiblocks. */
@GameTestHolder(UTechnology.MOD_ID)
@PrefixGameTestTemplate(false)
public class MultiblockTests {

    static final BlockPos CTRL = new BlockPos(2, 2, 1);
    static final String LONG = "utechnology:long";
    static final String[] FRAMES = { "steel", "aluminium", "stainless_steel" };

    static TestFunction test(String name, int ticks, Consumer<GameTestHelper> body) {
        return new TestFunction("utechnology_multiblocks", "utechnology." + name, LONG, Rotation.NONE, ticks, 0, true, body);
    }

    static int cap(String frame) {
        return Rig.frame(frame).stats().segmentCap();
    }

    // ---------------------------------------------------------------- structure

    /** Every frame at every length from 1 to the largest cap, plus over-cap lengths (they form and count the cap). */
    @GameTestGenerator
    public static Collection<TestFunction> everyFrameAndLength() {
        List<TestFunction> out = new ArrayList<>();
        for (String f : FRAMES) {
            List<Integer> lengths = new ArrayList<>();
            for (int n = 1; n <= 16; n++) lengths.add(n);
            lengths.add(17);
            lengths.add(32);
            for (int n : lengths) {
                out.add(test("forms_" + f + "_" + n, 400, h -> {
                    var r = Rig.build(h, CTRL, "turning_line", f, n, Hatches.FRONT);
                    h.succeedWhen(() -> {
                        r.assertFormed(f, n, Math.min(n, cap(f)));
                        long v = r.controller.getMaxVoltage();
                        long want = com.gregtechceu.gtceu.api.GTValues.V[Rig.frame(f).stats().maxVoltageTier()];
                        if (v != want) throw new GameTestAssertException("voltage " + v + " != frame's " + want + " (HV hatch)");
                    });
                }));
            }
        }
        return out;
    }

    /** Limited hatches in the end aisle at every length 1..32 (the spike's GTM bug must not bite). */
    @GameTestGenerator
    public static Collection<TestFunction> hatchesInEndAisleEveryLength() {
        List<TestFunction> out = new ArrayList<>();
        for (int n = 1; n <= 32; n++) {
            int len = n;
            out.add(test("end_hatches_" + len, 400, h -> {
                var r = Rig.build(h, CTRL, "wire_drawing_line", "stainless_steel", len, Hatches.END);
                h.succeedWhen(() -> r.assertFormed("stainless_steel", len, Math.min(len, cap("stainless_steel"))));
            }));
        }
        return out;
    }

    /** A casing of another frame stops it forming; putting the right one back forms it (positive control). */
    @GameTest(template = "long", timeoutTicks = 400)
    public static void mixedCasingsDontForm(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "turning_line", "steel", 3, Hatches.FRONT);
        BlockPos p = r.segmentCore(2).east();
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("steel", 3, 3))
                .thenExecute(() -> h.setBlock(p, Rig.frame("aluminium").casing()))
                .thenWaitUntil(() -> { if (r.controller.isFormed()) throw new GameTestAssertException("formed with mixed casings"); })
                .thenExecute(() -> {
                    var err = r.controller.getMultiblockState().error;
                    if (err == null || !(err.getErrorInfo().getString().contains("same frame") || err.getErrorInfo().getString().contains("mixed_frames")))
                        throw new GameTestAssertException("expected the mixed-frames error, got " + (err == null ? null : err.getErrorInfo().getString()));
                    h.setBlock(p, Rig.frame("steel").casing());
                })
                .thenWaitUntil(() -> r.assertFormed("steel", 3, 3))
                .thenSucceed();
    }

    @GameTest(template = "long", timeoutTicks = 400)
    public static void mixedFrameBoxesDontForm(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "turning_line", "steel", 2, Hatches.FRONT);
        BlockPos corner = r.endCenter().offset(1, 1, 0);
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("steel", 2, 2))
                .thenExecute(() -> h.setBlock(corner, Rig.frame("aluminium").frameBox()))
                .thenWaitUntil(() -> { if (r.controller.isFormed()) throw new GameTestAssertException("formed with mixed frame boxes"); })
                .thenExecute(() -> h.setBlock(corner, Rig.frame("steel").frameBox()))
                .thenWaitUntil(() -> r.assertFormed("steel", 2, 2))
                .thenSucceed();
    }

    @GameTest(template = "long", timeoutTicks = 400)
    public static void breakingASegmentUnformsAndRebuildingReforms(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "assembly_hall", "aluminium", 5, Hatches.FRONT);
        BlockPos broken = r.segmentCore(3).above();
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("aluminium", 5, 5))
                .thenExecute(() -> h.setBlock(broken, net.minecraft.world.level.block.Blocks.AIR))
                .thenWaitUntil(() -> { if (r.controller.isFormed()) throw new GameTestAssertException("still formed"); })
                .thenExecute(() -> h.setBlock(broken, Rig.frame("aluminium").casing()))
                .thenWaitUntil(() -> r.assertFormed("aluminium", 5, 5))
                .thenSucceed();
    }

    /** The four machines registered here agree with the data pack's multiblock specs. */
    @GameTest(template = "empty")
    public static void registrationMatchesData(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        var exp = DesignTests.expectations().getAsJsonObject("multiblocks");
        if (!exp.keySet().equals(UTMultiblocks.ALL.keySet())) problems.add("machines " + UTMultiblocks.ALL.keySet() + " != " + exp.keySet());
        if (!UpgradeData.get().specs().keySet().equals(UTMultiblocks.ALL.keySet()))
            problems.add("data specs " + UpgradeData.get().specs().keySet() + " != " + UTMultiblocks.ALL.keySet());
        UTMultiblocks.ALL.forEach((k, def) -> {
            var e = exp.getAsJsonObject(k);
            if (e == null) return;
            if (!def.getRecipeTypes()[0].registryName.toString().equals(e.get("recipe_type").getAsString()))
                problems.add(k + " runs " + def.getRecipeTypes()[0].registryName + ", design says " + e.get("recipe_type").getAsString());
            if (!def.getId().toString().equals(e.get("controller").getAsString())) problems.add(k + " id " + def.getId());
        });
        DesignTests.report(h, "registration", problems);
    }

    // ---------------------------------------------------------------- modules

    static void installed(Rig r, String... keys) {
        var got = r.controller.bonus().installed();
        if (!got.equals(List.of(keys))) throw new GameTestAssertException("installed " + got + " != " + List.of(keys));
    }

    static ItemStack module(String key) {
        return new ItemStack(UpgradeData.get().modules().get(key).item());
    }

    /** Slot limits per frame, unlock tiers and one-of-each, through the right-click install path. */
    @GameTest(template = "long", timeoutTicks = 400)
    public static void moduleSlotLimits(GameTestHelper h) {
        var steel = Rig.build(h, CTRL, "turning_line", "steel", 2, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> steel.assertFormed("steel", 2, 2))
                .thenExecute(() -> {
                    steel.controller.installModule(module("efficiency_coil"), null); // MV module on an LV frame
                    installed(steel);
                    steel.controller.installModule(module("specialization_lock"), null);
                    installed(steel, "specialization_lock");
                    steel.controller.installModule(module("specialization_lock"), null); // full (1 slot) and duplicate
                    installed(steel, "specialization_lock");
                    if (!steel.controller.removeLastModule().is(module("specialization_lock").getItem()))
                        throw new GameTestAssertException("removing didn't return the lock");
                    installed(steel);
                })
                .thenSucceed();
    }

    @GameTest(template = "long", timeoutTicks = 400)
    public static void moduleSlotsByFrame(GameTestHelper h) {
        var al = Rig.build(h, CTRL, "turning_line", "aluminium", 2, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> al.assertFormed("aluminium", 2, 2))
                .thenExecute(() -> {
                    al.controller.installModule(module("batch_bus"), null); // HV module, MV frame
                    al.controller.installModule(module("efficiency_coil"), null);
                    al.controller.installModule(module("efficiency_coil"), null); // one of each
                    al.controller.installModule(module("specialization_lock"), null);
                    installed(al, "efficiency_coil", "specialization_lock");
                    // a module put straight into the third slot (as the UI could) is ignored: 2 slots
                    al.controller.getModules().setStackInSlot(2, module("batch_bus"));
                    installed(al, "efficiency_coil", "specialization_lock");
                    if (al.controller.bonus().ignored().isEmpty()) throw new GameTestAssertException("no ignored module reported");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- retrofit

    static FakePlayer player(GameTestHelper h, String name) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    static int count(FakePlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    static void rightClick(GameTestHelper h, FakePlayer p, Rig r, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(r.ctrl);
        r.controller.onUse(h.getLevel().getBlockState(abs), h.getLevel(), abs, p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), net.minecraft.core.Direction.NORTH, abs, false));
    }

    /** Steel -> aluminium -> steel with kits: one kit per 8 casings/frame boxes, old blocks returned. */
    @GameTest(template = "long", timeoutTicks = 600)
    public static void retrofitUpAndDown(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "assembly_hall", "steel", 3, Hatches.FRONT);
        var steel = Rig.frame("steel");
        var al = Rig.frame("aluminium");
        FakePlayer p = player(h, "utech_retrofit");
        p.getInventory().clearContent();
        int[] kits = { 0 };
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("steel", 3, 3))
                .thenExecute(() -> {
                    // front 8 - 6 hatches = 2 casings, 3 x 8 segment casings, end 5 casings + 4 frame boxes = 35 -> 5 kits
                    kits[0] = r.controller.kitsNeeded();
                    if (kits[0] != 5) throw new GameTestAssertException("kits needed " + kits[0] + " != 5");
                    p.getInventory().add(new ItemStack(al.kit(), kits[0] - 1));
                    rightClick(h, p, r, p.getInventory().items.get(0)); // one short: refused
                    if (count(p, al.kit()) != kits[0] - 1) throw new GameTestAssertException("kits used without enough");
                    p.getInventory().clearContent();
                    p.getInventory().add(new ItemStack(al.kit(), kits[0] + 2));
                    rightClick(h, p, r, p.getInventory().items.get(0));
                })
                .thenWaitUntil(() -> r.assertFormed("aluminium", 3, 3))
                .thenExecute(() -> {
                    if (count(p, al.kit()) != 2) throw new GameTestAssertException("kits left " + count(p, al.kit()) + " != 2");
                    if (count(p, steel.casing().asItem()) != 31) throw new GameTestAssertException("steel casings back " + count(p, steel.casing().asItem()));
                    if (count(p, steel.frameBox().asItem()) != 4) throw new GameTestAssertException("steel frame boxes back " + count(p, steel.frameBox().asItem()));
                    p.getInventory().clearContent();
                    p.getInventory().add(new ItemStack(steel.kit(), 5));
                    rightClick(h, p, r, p.getInventory().items.get(0));
                })
                .thenWaitUntil(() -> r.assertFormed("steel", 3, 3))
                .thenExecute(() -> {
                    if (count(p, steel.kit()) != 0) throw new GameTestAssertException("steel kits left");
                    if (count(p, al.casing().asItem()) != 31) throw new GameTestAssertException("aluminium casings back " + count(p, al.casing().asItem()));
                    if (count(p, al.frameBox().asItem()) != 4) throw new GameTestAssertException("aluminium frame boxes back");
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- recipes

    static JsonObject designRecipe(String designId) {
        var e = DesignTests.expectations();
        for (var el : e.getAsJsonArray("stock_recipes"))
            if (el.getAsJsonObject().get("design_id").getAsString().equals(designId)) return el.getAsJsonObject();
        for (var el : e.getAsJsonArray("our_recipes"))
            if (el.getAsJsonObject().get("design_id").getAsString().equals(designId)) return el.getAsJsonObject();
        throw new GameTestAssertException("no design recipe " + designId);
    }

    static String machineFor(String type, boolean component) {
        return switch (type) {
            case "gtceu:wiremill" -> "wire_drawing_line";
            case "gtceu:lathe" -> "turning_line";
            default -> component ? "component_assembler" : "assembly_hall";
        };
    }

    /** Loads inputs for n runs of a design recipe (plus upkeep fluids for the bonus) into the rig. */
    static void feed(Rig r, JsonObject recipe, int n, int lubricantPerRun) {
        recipe.getAsJsonObject("items_in").entrySet().forEach(e -> r.give(e.getKey(), e.getValue().getAsInt() * n));
        recipe.getAsJsonObject("fluids_in").entrySet().forEach(e -> r.fluid(e.getKey(), e.getValue().getAsInt() * n));
        if (recipe.has("circuit")) r.circuit(recipe.get("circuit").getAsInt());
        if (lubricantPerRun > 0) r.fluid("gtceu:lubricant", lubricantPerRun * n);
    }

    /**
     * The simulator's per-recipe cases (frames x modules x recipes, 3 segments) run in game: parallels, duration
     * and EU/t of the running recipe match exactly. Parallels draw no extra power, so EU/t is one recipe's.
     */
    @GameTestGenerator
    public static Collection<TestFunction> simulatorRecipeCases() {
        List<TestFunction> out = new ArrayList<>();
        int i = 0;
        for (var el : DesignTests.expectations().getAsJsonArray("recipe_cases")) {
            JsonObject c = el.getAsJsonObject();
            String frame = c.get("frame").getAsString(), id = c.get("recipe").getAsString();
            List<String> mods = new ArrayList<>();
            c.getAsJsonArray("modules").forEach(m -> mods.add(m.getAsString()));
            String name = "recipe_" + (i++) + "_" + frame + "_" + id + (mods.isEmpty() ? "" : "_" + String.join("_", mods));
            out.add(test(name, 1200, h -> {
                JsonObject recipe = designRecipe(id);
                String type = recipe.get("type").getAsString();
                boolean component = DesignTests.expectations().getAsJsonObject("replaced_outputs") != null &&
                        isComponent(recipe);
                var r = Rig.build(h, CTRL, machineFor(type, component), frame, c.get("segments").getAsInt(), Hatches.FRONT);
                h.startSequence()
                        .thenWaitUntil(() -> { if (!r.controller.isFormed()) throw new GameTestAssertException("not formed"); })
                        .thenExecute(() -> {
                            for (int s = 0; s < mods.size(); s++) r.controller.getModules().setStackInSlot(s, module(mods.get(s)));
                            var b = r.controller.bonus();
                            int lub = b.upkeepPerParallel().stream().mapToInt(u -> u.amount()).sum();
                            feed(r, recipe, Math.max(1, b.parallelLimit()), lub);
                        })
                        .thenExecute(() -> {})
                        .thenWaitUntil(() -> {
                            if (c.get("expect").isJsonNull()) return; // checked below
                            if (!r.controller.getRecipeLogic().isWorking()) throw new GameTestAssertException("not working");
                        })
                        .thenIdle(c.get("expect").isJsonNull() ? 60 : 0)
                        .thenExecute(() -> {
                            var logic = r.controller.getRecipeLogic();
                            if (c.get("expect").isJsonNull()) {
                                if (logic.isWorking() || logic.getLastRecipe() != null)
                                    throw new GameTestAssertException("ran a recipe the simulator says it can't");
                                if (r.input.getInventory().storage.getStackInSlot(0).isEmpty())
                                    throw new GameTestAssertException("inputs were used");
                                return;
                            }
                            JsonObject e = c.getAsJsonObject("expect");
                            var run = logic.getLastRecipe();
                            int want = (int) Math.round(e.get("parallels").getAsDouble());
                            if (run.parallels != want) throw new GameTestAssertException("parallels " + run.parallels + " != " + want);
                            double ticks = e.get("ticks").getAsDouble();
                            if (run.duration != ticks) throw new GameTestAssertException("ticks " + run.duration + " != " + ticks);
                            double eut = e.get("eut").getAsDouble();
                            long got = RecipeHelper.getRealEUt(run).voltage();
                            if (got != eut) throw new GameTestAssertException("EU/t " + got + " != " + eut + " (parallels must not add power)");
                        })
                        .thenSucceed();
            }));
        }
        return out;
    }

    static boolean isComponent(JsonObject recipe) {
        // the Component Assembler's filter: the output is a component item (from the data pack spec)
        var spec = UpgradeData.get().specs().get("component_assembler");
        for (String out : recipe.getAsJsonObject("items_out").keySet())
            if (spec != null && spec.componentItems().contains(BuiltInRegistries.ITEM.get(new ResourceLocation(out)))) return true;
        return false;
    }

    /** The recipe filters: the Assembly Hall won't run a component recipe; the Component Assembler won't run cables. */
    @GameTest(template = "long", timeoutTicks = 300)
    public static void assemblyHallRefusesComponents(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "assembly_hall", "aluminium", 2, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("aluminium", 2, 2))
                .thenExecute(() -> feed(r, designRecipe("motor_lv"), 2, 0))
                .thenIdle(80)
                .thenExecute(() -> { if (r.controller.getRecipeLogic().isWorking()) throw new GameTestAssertException("ran a component"); })
                .thenSucceed();
    }

    @GameTest(template = "long", timeoutTicks = 300)
    public static void componentAssemblerRefusesCables(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "component_assembler", "aluminium", 2, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("aluminium", 2, 2))
                .thenExecute(() -> feed(r, designRecipe("cable_tin_single"), 2, 0))
                .thenIdle(80)
                .thenExecute(() -> { if (r.controller.getRecipeLogic().isWorking()) throw new GameTestAssertException("ran a cable"); })
                .thenSucceed();
    }

    /** The specialization lock locks to the first recipe and then refuses others. */
    @GameTest(template = "long", timeoutTicks = 800)
    public static void specializationLock(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "turning_line", "steel", 2, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("steel", 2, 2))
                .thenExecute(() -> {
                    r.controller.getModules().setStackInSlot(0, module("specialization_lock"));
                    feed(r, designRecipe("rod_steel"), 1, 0);
                })
                .thenWaitUntil(() -> {
                    if (r.controller.getLockedRecipe() == null) throw new GameTestAssertException("not locked yet");
                })
                .thenWaitUntil(() -> { if (r.controller.getRecipeLogic().isWorking()) throw new GameTestAssertException("still working"); })
                .thenExecute(() -> feed(r, designRecipe("rod_aluminium"), 1, 0))
                .thenIdle(80)
                .thenExecute(() -> {
                    if (r.controller.getRecipeLogic().isWorking()) throw new GameTestAssertException("ran another recipe while locked");
                    r.controller.removeLastModule();
                    if (r.controller.getLockedRecipe() != null) throw new GameTestAssertException("lock kept after removing the module");
                })
                .thenWaitUntil(() -> { if (!r.controller.getRecipeLogic().isWorking()) throw new GameTestAssertException("didn't run after unlocking"); })
                .thenSucceed();
    }

    /** Frame, segments, modules and the lock are saved with the machine and loaded back. */
    @GameTest(template = "long", timeoutTicks = 600)
    public static void upgradesArePersisted(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "turning_line", "steel", 2, Hatches.FRONT);
        net.minecraft.nbt.CompoundTag[] saved = { null };
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("steel", 2, 2))
                .thenExecute(() -> {
                    r.controller.getModules().setStackInSlot(0, module("specialization_lock"));
                    feed(r, designRecipe("rod_steel"), 1, 0);
                })
                .thenWaitUntil(() -> { if (r.controller.getLockedRecipe() == null) throw new GameTestAssertException("not locked"); })
                .thenExecute(() -> {
                    var be = h.getLevel().getBlockEntity(h.absolutePos(CTRL));
                    saved[0] = be.saveWithoutMetadata();
                    // change everything, then load the saved state back into the machine
                    String locked = r.controller.getLockedRecipe().toString();
                    r.controller.removeLastModule();
                    if (r.controller.getLockedRecipe() != null) throw new GameTestAssertException("lock not cleared");
                    be.load(saved[0]);
                    if (!r.controller.getModules().getStackInSlot(0).is(module("specialization_lock").getItem()))
                        throw new GameTestAssertException("module not restored");
                    if (r.controller.getLockedRecipe() == null || !r.controller.getLockedRecipe().toString().equals(locked))
                        throw new GameTestAssertException("lock not restored");
                    if (!r.controller.getFrameKey().equals("steel")) throw new GameTestAssertException("frame not restored");
                    String nbt = saved[0].toString();
                    if (!nbt.contains("builtSegments:2")) throw new GameTestAssertException("segments not saved: " + nbt);
                })
                .thenSucceed();
    }

    /** Breaking the controller drops its modules. */
    @GameTest(template = "long", timeoutTicks = 400)
    public static void modulesDropWhenTheControllerBreaks(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "turning_line", "aluminium", 2, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("aluminium", 2, 2))
                .thenExecute(() -> {
                    r.controller.getModules().setStackInSlot(0, module("efficiency_coil"));
                    h.destroyBlock(CTRL);
                })
                .thenExecute(() -> h.assertItemEntityPresent(module("efficiency_coil").getItem(), CTRL, 3))
                .thenSucceed();
    }

    /** Free parallels must not let a frame run recipes above its tier (GTM's check divides EU/t by parallels). */
    @GameTest(template = "long", timeoutTicks = 400)
    public static void steelFrameRefusesMvRecipeAtAnyLength(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "component_assembler", "steel", 4, Hatches.FRONT);
        JsonObject motorHv = designRecipe("motor_hv");
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("steel", 4, 4))
                .thenExecute(() -> feed(r, motorHv, 4, 0))
                .thenIdle(100)
                .thenExecute(() -> {
                    if (r.controller.getRecipeLogic().isWorking() || r.outputCount(BuiltInRegistries.ITEM.get(new ResourceLocation("gtceu:hv_electric_motor"))) > 0)
                        throw new GameTestAssertException("a steel (LV) frame ran the 120 EU/t HV motor recipe");
                })
                .thenSucceed();
    }

    /** An over-cap machine runs at its frame's cap: 17 segments on stainless steel = 16 parallels. */
    @GameTest(template = "long", timeoutTicks = 600)
    public static void overCapRunsAtTheCap(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "component_assembler", "stainless_steel", 17, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("stainless_steel", 17, 16))
                .thenExecute(() -> feed(r, designRecipe("motor_lv"), 20, 0))
                .thenWaitUntil(() -> { if (!r.controller.getRecipeLogic().isWorking()) throw new GameTestAssertException("not working"); })
                .thenExecute(() -> {
                    int p = r.controller.getRecipeLogic().getLastRecipe().parallels;
                    if (p != 16) throw new GameTestAssertException("parallels " + p + " != 16 (the cap)");
                })
                .thenSucceed();
    }

    /** The batch bus's lubricant limits parallels: lubricant for 3 of 6 possible parallels runs 3. */
    @GameTest(template = "long", timeoutTicks = 600)
    public static void lubricantLimitsParallels(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "turning_line", "stainless_steel", 3, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("stainless_steel", 3, 3))
                .thenExecute(() -> {
                    r.controller.getModules().setStackInSlot(0, module("batch_bus"));
                    feed(r, designRecipe("rod_steel"), 6, 0);
                    r.fluid("gtceu:lubricant", 30);
                })
                .thenWaitUntil(() -> { if (!r.controller.getRecipeLogic().isWorking()) throw new GameTestAssertException("not working"); })
                .thenExecute(() -> {
                    int p = r.controller.getRecipeLogic().getLastRecipe().parallels;
                    if (p != 3) throw new GameTestAssertException("parallels " + p + " != 3 (lubricant for 3)");
                })
                .thenSucceed();
    }

    /** A module installed while the machine is idle applies to its next recipe. */
    @GameTest(template = "long", timeoutTicks = 800)
    public static void moduleChangeAppliesWhenIdle(GameTestHelper h) {
        var r = Rig.build(h, CTRL, "turning_line", "aluminium", 1, Hatches.FRONT);
        JsonObject rod = designRecipe("rod_steel");
        h.startSequence()
                .thenWaitUntil(() -> r.assertFormed("aluminium", 1, 1))
                .thenExecute(() -> feed(r, rod, 1, 0))
                .thenWaitUntil(() -> { if (r.outputCount(BuiltInRegistries.ITEM.get(new ResourceLocation("gtceu:steel_rod"))) < 1) throw new GameTestAssertException("first rod"); })
                .thenIdle(10)
                .thenExecute(() -> {
                    r.controller.installModule(module("efficiency_coil"), null);
                    feed(r, rod, 1, 0);
                })
                .thenWaitUntil(() -> { if (!r.controller.getRecipeLogic().isWorking()) throw new GameTestAssertException("not working"); })
                .thenExecute(() -> {
                    // 112 ticks x 1.25 = 140, one overclock on MV: 70 ticks at 36 EU/t (16 x 0.6 = 9, x4)
                    var run = r.controller.getRecipeLogic().getLastRecipe();
                    if (run.duration != 70 || RecipeHelper.getRealEUt(run).voltage() != 36)
                        throw new GameTestAssertException("coil not applied: " + run.duration + " ticks, " + RecipeHelper.getRealEUt(run).voltage() + " EU/t");
                })
                .thenSucceed();
    }

    /** The preview's fallback hatch limits (used before data loads) equal the design's. */
    @GameTest(template = "empty")
    public static void previewLimitsMatchData(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        UpgradeData.get().specs().forEach((k, spec) -> spec.hatchLimits().forEach((kind, mm) -> {
            int[] p = dev.utechnology.upgrade.UpgradePatternAccess.previewLimits().get(kind);
            if (p == null || p[0] != mm[0] || p[1] != mm[1]) problems.add(k + " " + kind);
        }));
        DesignTests.report(h, "preview limits", problems);
    }

    // ---------------------------------------------------------------- performance

    static double timeUs(Rig r, int iterations) {
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) r.controller.checkPatternWithLock();
        return (System.nanoTime() - t0) / 1000.0 / iterations;
    }

    /** Structure check time at 16 and 32 segments (CLAUDE.md: measure before raising caps). Logged as SPIKE-TIMING. */
    @GameTest(template = "long", timeoutTicks = 600)
    public static void structureCheckTiming(GameTestHelper h) {
        var r32 = Rig.build(h, CTRL, "component_assembler", "stainless_steel", 32, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> r32.assertFormed("stainless_steel", 32, 16))
                .thenExecute(() -> {
                    timeUs(r32, 2000); // warm-up
                    List<Double> rounds = new ArrayList<>();
                    for (int i = 0; i < 15; i++) rounds.add(timeUs(r32, 300));
                    Collections.sort(rounds);
                    UTechnology.LOGGER.info("TIMING structure check, 32 segments: median {} us, min {} us",
                            String.format("%.1f", rounds.get(7)), String.format("%.1f", rounds.get(0)));
                })
                .thenSucceed();
    }

    @GameTest(template = "long", timeoutTicks = 600)
    public static void structureCheckTiming16(GameTestHelper h) {
        var r16 = Rig.build(h, CTRL, "component_assembler", "stainless_steel", 16, Hatches.FRONT);
        h.startSequence()
                .thenWaitUntil(() -> r16.assertFormed("stainless_steel", 16, 16))
                .thenExecute(() -> {
                    timeUs(r16, 2000);
                    List<Double> rounds = new ArrayList<>();
                    for (int i = 0; i < 15; i++) rounds.add(timeUs(r16, 300));
                    Collections.sort(rounds);
                    UTechnology.LOGGER.info("TIMING structure check, 16 segments: median {} us, min {} us",
                            String.format("%.1f", rounds.get(7)), String.format("%.1f", rounds.get(0)));
                })
                .thenSucceed();
    }
}
