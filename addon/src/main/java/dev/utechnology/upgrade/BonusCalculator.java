package dev.utechnology.upgrade;

import java.util.ArrayList;
import java.util.List;

/**
 * A multiblock's bonuses from its frame, length and modules. Plain Java (no Minecraft classes) so it can be
 * unit-tested against the simulator: this mirrors resolveGroup in design/sim/sim-core.js, and
 * BonusCalculatorTest checks it against every case tools/generate.py computes with the simulator's own code.
 * Called when a structure forms or its modules change, never per recipe.
 */
public final class BonusCalculator {

    /** Voltage tiers by name, GTM order (ULV = 0). */
    public static final List<String> TIERS = List.of("ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV");

    public static int tier(String name) {
        int t = TIERS.indexOf(name);
        if (t < 0) throw new IllegalArgumentException("unknown tier " + name);
        return t;
    }

    public record Frame(String key, int tier, int maxVoltageTier, int segmentCap, int moduleSlots) {}

    public record Upkeep(String fluid, int amount) {}

    public record Module(String key, int unlockedTier, boolean lock, double lockDuration, double duration,
                         double energy, double parallels, List<Upkeep> upkeepPerParallel) {}

    /**
     * @param tier             the machine's voltage tier (capped at the frame's)
     * @param counted          segments that count: min(built, frame cap)
     * @param parallels        counted segments times module multipliers
     * @param duration         recipe duration multiplier from modules (the lock's is separate)
     * @param energy           EU/t multiplier from modules
     * @param lockDuration     duration multiplier for the locked recipe (1 without a lock)
     * @param installed        modules that take effect, in slot order
     * @param upkeepPerParallel fluids consumed per parallel each run
     * @param ignored          modules present but not in effect, with the reason
     */
    public record Bonus(int tier, int built, int counted, double parallels, double duration, double energy,
                        double lockDuration, boolean locked, List<String> installed, List<Upkeep> upkeepPerParallel,
                        List<String> ignored) {

        public int parallelLimit() {
            return Math.max(1, (int) Math.round(parallels));
        }
    }

    /**
     * @param modules modules in slot order; null entries are empty slots
     */
    public static Bonus compute(Frame frame, int built, List<Module> modules) {
        int counted = Math.min(built, frame.segmentCap());
        double parallels = counted, duration = 1, energy = 1, lockDuration = 1;
        List<String> used = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        List<Upkeep> upkeep = new ArrayList<>();
        for (Module m : modules) {
            if (m == null) continue;
            if (m.unlockedTier() > frame.tier()) {
                ignored.add(m.key() + ": needs at least a " + TIERS.get(m.unlockedTier()) + " frame");
                continue;
            }
            if (used.contains(m.key())) {
                ignored.add(m.key() + ": already installed");
                continue;
            }
            if (used.size() >= frame.moduleSlots()) {
                ignored.add(m.key() + ": the frame has " + frame.moduleSlots() + " module slot(s)");
                continue;
            }
            used.add(m.key());
            if (m.lock()) {
                lockDuration = m.lockDuration();
                continue;
            }
            duration *= m.duration();
            energy *= m.energy();
            parallels *= m.parallels();
            for (Upkeep u : m.upkeepPerParallel()) addUpkeep(upkeep, u);
        }
        boolean locked = used.stream().anyMatch(k -> modules.stream().anyMatch(m -> m != null && m.key().equals(k) && m.lock()));
        return new Bonus(frame.maxVoltageTier(), built, counted, parallels, duration, energy, locked ? lockDuration : 1,
                locked, List.copyOf(used), List.copyOf(upkeep), List.copyOf(ignored));
    }

    private static void addUpkeep(List<Upkeep> list, Upkeep u) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).fluid().equals(u.fluid())) {
                list.set(i, new Upkeep(u.fluid(), list.get(i).amount() + u.amount()));
                return;
            }
        }
        list.add(u);
    }

    /** Lowest tier whose voltage covers this EU/t (as the simulator's recipeTier). */
    public static int recipeTier(double eut) {
        long[] v = { 8, 32, 128, 512, 2048, 8192, 32768, 131072, 524288, 2097152 };
        int t = 0;
        while (t < v.length - 1 && v[t] < eut) t++;
        return t;
    }

    /**
     * One recipe run in the machine, as the simulator's capability() and GTM 7.5.3 compute it: module multipliers
     * first, each truncated to whole ticks and EU/t; then overclocking (2x speed for 4x EU/t per tier) judged on
     * the modified EU/t, one fewer for ULV recipes, never below 1 tick. Parallels draw no extra power.
     *
     * @return {ticks, eut, overclocks}, or null if the machine can't run it
     */
    public static double[] recipe(Bonus b, long recipeEut, int recipeTicks, boolean isLockedRecipe) {
        if (b.tier() < recipeTier(recipeEut)) return null;
        if (b.locked() && !isLockedRecipe) return null;
        double dmul = b.duration() * (b.locked() ? b.lockDuration() : 1);
        long ticksMod = dmul == 1 ? recipeTicks : (long) (recipeTicks * dmul);
        long eutMod = b.energy() == 1 ? recipeEut : (long) (recipeEut * b.energy());
        int rt = recipeTier(eutMod);
        int n = Math.max(0, b.tier() - rt - (rt == 0 ? 1 : 0));
        while (n > 0 && ticksMod / Math.pow(2, n) < 1) n--;
        return new double[] { Math.floor(ticksMod / Math.pow(2, n)), eutMod * Math.pow(4, n), n };
    }

    private BonusCalculator() {}
}
