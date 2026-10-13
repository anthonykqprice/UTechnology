package dev.utechnology.spike.test;

import dev.utechnology.spike.UTSpike;
import dev.utechnology.spike.machine.SegmentAssemblerMachine;
import dev.utechnology.spike.test.SpikeTestUtil.Built;
import dev.utechnology.spike.test.SpikeTestUtil.Hatches;

import com.gregtechceu.gtceu.common.data.GTBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static dev.utechnology.spike.test.SpikeTestUtil.*;

/** Check 1: variable-segment multiblock. */
@GameTestHolder(UTSpike.MOD_ID)
@PrefixGameTestTemplate(false)
public class SegmentTests {

    static final BlockPos CTRL = new BlockPos(3, 2, 1);
    static final int TIMEOUT = 400;

    private static void formsWith(GameTestHelper h, int n) {
        Built b = build(h, CTRL, n, null);
        h.succeedWhen(() -> assertFormed(b, n));
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void forms1(GameTestHelper h) { formsWith(h, 1); }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void forms4(GameTestHelper h) { formsWith(h, 4); }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void forms8(GameTestHelper h) { formsWith(h, 8); }

    /** Zero segments (front directly against end) must not form. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void zeroSegmentsDoesNotForm(GameTestHelper h) {
        Built b = build(h, CTRL, 1, null);
        // turn the only segment into an end aisle: front + end + (old end, ignored)
        h.setBlock(b.segmentCenter(1), GTBlocks.CASING_STEEL_SOLID.getDefaultState());
        h.runAfterDelay(100, () -> { assertNotFormed(b); h.succeed(); });
    }

    /** Breaking a block inside a segment unforms; restoring it re-forms with the same count. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void breakSegmentUnformsAndReforms(GameTestHelper h) {
        Built b = build(h, CTRL, 4, null);
        BlockPos broken = b.segmentCenter(2).east();
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(b, 4))
                .thenExecute(() -> air(h, broken))
                .thenWaitUntil(() -> assertNotFormed(b))
                .thenExecute(() -> assertEq(0, b.controller().getSegments(), "segments when unformed"))
                .thenExecute(() -> h.setBlock(broken, GTBlocks.CASING_STEEL_SOLID.getDefaultState()))
                .thenWaitUntil(() -> assertFormed(b, 4))
                .thenSucceed();
    }

    /** Turning the last segment into an end aisle shrinks 4 -> 3 without unforming. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void shrinkUpdatesSegments(GameTestHelper h) {
        Built b = build(h, CTRL, 4, null);
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(b, 4))
                .thenExecute(() -> h.setBlock(b.segmentCenter(4), GTBlocks.CASING_STEEL_SOLID.getDefaultState()))
                .thenWaitUntil(() -> assertFormed(b, 3))
                .thenSucceed();
    }

    /** Extending 4 -> 5: end aisle becomes a segment (unforms), new end aisle added (re-forms with 5). */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void growUpdatesSegments(GameTestHelper h) {
        Built b = build(h, CTRL, 4, null);
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(b, 4))
                .thenExecute(() -> {
                    h.setBlock(b.endCenter(), GTBlocks.CASING_GRATE.getDefaultState());
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
                        h.setBlock(b.endCenter().offset(dx, dy, 1), GTBlocks.CASING_STEEL_SOLID.getDefaultState());
                })
                .thenWaitUntil(() -> assertFormed(b, 5))
                .thenSucceed();
    }

    /** 9 segments exceeds the max of 8: the 9th grate is where the end aisle should be, so it must not form. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nineSegmentsDoesNotForm(GameTestHelper h) {
        Built b = build(h, new BlockPos(3, 2, 1), 8, null);
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(b, 8))
                .thenExecute(() -> {
                    h.setBlock(b.endCenter(), GTBlocks.CASING_GRATE.getDefaultState());
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
                        h.setBlock(b.endCenter().offset(dx, dy, 1), GTBlocks.CASING_STEEL_SOLID.getDefaultState());
                })
                .thenIdle(100)
                .thenExecute(() -> assertNotFormed(b))
                .thenSucceed();
    }

    /*
     * Observation: an energy hatch (limited to max 2) in the END aisle. GTM's matcher retries the end aisle as a
     * middle segment (max - built) times and each failed attempt counts the hatch again, so 1..5 segments exceed the
     * limit; 6 segments = 2 extra counts... these optional tests pin down where it starts working.
     */
    private static void endHatch(GameTestHelper h, int n) {
        Built b = build(h, CTRL, n, null, Hatches.END_ENERGY);
        h.succeedWhen(() -> assertFormed(b, n));
    }

    @GameTest(template = "empty", timeoutTicks = 200, required = false)
    public static void endHatch1(GameTestHelper h) { endHatch(h, 1); }

    @GameTest(template = "empty", timeoutTicks = 200, required = false)
    public static void endHatch6(GameTestHelper h) { endHatch(h, 6); }

    @GameTest(template = "empty", timeoutTicks = 200, required = false)
    public static void endHatch7(GameTestHelper h) { endHatch(h, 7); }

    @GameTest(template = "empty", timeoutTicks = 200, required = false)
    public static void endHatch8(GameTestHelper h) { endHatch(h, 8); }

    private static void parallelRun(GameTestHelper h, int n) {
        Built b = build(h, CTRL, n, null);
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(b, n))
                .thenExecute(() -> feed(b, 16))
                .thenWaitUntil(() -> runningRecipe(b))
                .thenExecute(() -> {
                    var r = runningRecipe(b);
                    assertEq(n, r.parallels, "parallels");
                    assertEq(100, r.duration, "duration");
                })
                .thenWaitUntil(() -> assertEq(n, diamonds(b), "diamonds after one run"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void parallel4(GameTestHelper h) { parallelRun(h, 4); }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void parallel8(GameTestHelper h) { parallelRun(h, 8); }

    /** Inputs for only 3 sets on an 8-segment machine: parallels are limited by inputs. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void parallel8LimitedByInputs(GameTestHelper h) {
        Built b = build(h, CTRL, 8, null);
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(b, 8))
                .thenExecute(() -> feed(b, 3))
                .thenWaitUntil(() -> assertEq(3, runningRecipe(b).parallels, "parallels"))
                .thenSucceed();
    }

    private static double timeUs(Built b, int iterations) {
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) b.controller().checkPatternWithLock();
        return (System.nanoTime() - t0) / 1000.0 / iterations;
    }

    /** Interleaved rounds after a long warm-up; reports min and median of per-round means. */
    private static String time(Built b, java.util.List<Double> rounds) {
        var sorted = rounds.stream().sorted().toList();
        return String.format("min %.1f us, median %.1f us", sorted.get(0), sorted.get(sorted.size() / 2));
    }

    /** Measures one full structure check (what GTM runs on every block change inside the structure). */
    @GameTest(template = "wide", timeoutTicks = TIMEOUT)
    public static void structureCheckTiming(GameTestHelper h) {
        Built b8 = build(h, new BlockPos(2, 2, 1), 8, null);
        Built b1 = build(h, new BlockPos(7, 2, 1), 1, null);
        Built b4 = build(h, new BlockPos(12, 2, 1), 4, null);
        h.startSequence()
                .thenWaitUntil(() -> { assertFormed(b8, 8); assertFormed(b1, 1); assertFormed(b4, 4); })
                .thenExecute(() -> {
                    Built[] all = { b1, b4, b8 };
                    for (Built b : all) timeUs(b, 3000); // warm-up
                    java.util.List<java.util.List<Double>> r = java.util.List.of(new java.util.ArrayList<>(),
                            new java.util.ArrayList<>(), new java.util.ArrayList<>());
                    for (int round = 0; round < 15; round++)
                        for (int i = 0; i < 3; i++) r.get(i).add(timeUs(all[i], 500));
                    UTSpike.LOGGER.info("SPIKE-TIMING formed check: 1 seg {} | 4 seg {} | 8 seg {}",
                            time(b1, r.get(0)), time(b4, r.get(1)), time(b8, r.get(2)));
                    air(h, b8.endCenter().east());
                })
                .thenWaitUntil(() -> assertNotFormed(b8))
                .thenExecute(() -> {
                    java.util.List<Double> r = new java.util.ArrayList<>();
                    for (int round = 0; round < 15; round++) r.add(timeUs(b8, 500));
                    UTSpike.LOGGER.info("SPIKE-TIMING failing check, 8 seg broken at end: {}", time(b8, r));
                })
                .thenExecute(() -> assertEq(true, SegmentAssemblerMachine.checkCount > 0, "checks ran"))
                .thenSucceed();
    }
}
