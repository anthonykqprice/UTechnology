package dev.utechnology.upgrade;

import dev.utechnology.UTechnology;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Frames, modules and multiblock specs from the data pack (data/utechnology/upgrade_{frames,modules,multiblocks},
 * generated from design/ by tools/generate.py and shipped in the pack's kubejs/data). Reloaded with /reload, so
 * rebalancing needs no recompile. Readers get an immutable snapshot; its version changes on every reload so
 * machines know to recompute their cached bonuses.
 */
public final class UpgradeData {

    public record Frame(BonusCalculator.Frame stats, Block casing, Block frameBox, Item kit) {
        public String key() {
            return stats.key();
        }
    }

    public record Module(BonusCalculator.Module stats, Item item, String name, String effect) {
        public String key() {
            return stats.key();
        }
    }

    /** Hatch limits as {min, max} by design name (maintenance, energy_input, item_input_bus, ...). */
    public record Spec(String key, String filter, Set<Item> componentItems, int maxSegmentsBuilt,
                       Map<String, int[]> hatchLimits, int casingsPerKit) {}

    public record Snapshot(int version, Map<String, Frame> frames, Map<String, Module> modules, Map<String, Spec> specs) {

        public @Nullable Frame frameByCasing(Block b) {
            for (Frame f : frames.values()) if (f.casing() == b) return f;
            return null;
        }

        public @Nullable Frame frameByBox(Block b) {
            for (Frame f : frames.values()) if (f.frameBox() == b) return f;
            return null;
        }

        public @Nullable Frame frameByKit(Item i) {
            for (Frame f : frames.values()) if (f.kit() == i) return f;
            return null;
        }

        public @Nullable Module moduleByItem(Item i) {
            for (Module m : modules.values()) if (m.item() == i) return m;
            return null;
        }
    }

    private static volatile Snapshot current = new Snapshot(0, Map.of(), Map.of(), Map.of());

    public static Snapshot get() {
        return current;
    }

    static void set(Snapshot s) {
        current = s;
    }

    private static Item item(String id) {
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (i == null) throw new IllegalArgumentException("unknown item " + id);
        return i;
    }

    private static Block block(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (b == null) throw new IllegalArgumentException("unknown block " + id);
        return b;
    }

    /** Parses the three folders; a bad entry is logged and skipped so one mistake doesn't take everything down. */
    static Snapshot parse(int version, Map<ResourceLocation, JsonElement> frames, Map<ResourceLocation, JsonElement> modules,
                          Map<ResourceLocation, JsonElement> specs) {
        Map<String, Frame> f = new TreeMap<>();
        frames.forEach((id, json) -> {
            try {
                JsonObject j = json.getAsJsonObject();
                f.put(id.getPath(), new Frame(UpgradeJson.frame(id.getPath(), j), block(j.get("casing").getAsString()),
                        block(j.get("frame_box").getAsString()), item(j.get("retrofit_kit").getAsString())));
            } catch (Exception e) {
                UTechnology.LOGGER.error("Bad frame {}: {}", id, e.toString());
            }
        });
        // frames in tier order, so lists and previews read low to high
        Map<String, Frame> sorted = new LinkedHashMap<>();
        f.values().stream().sorted(Comparator.comparingInt(x -> x.stats().tier())).forEach(x -> sorted.put(x.key(), x));
        Map<String, Module> m = new TreeMap<>();
        modules.forEach((id, json) -> {
            try {
                JsonObject j = json.getAsJsonObject();
                m.put(id.getPath(), new Module(UpgradeJson.module(id.getPath(), j), item(j.get("item").getAsString()),
                        j.get("name").getAsString(), j.get("effect").getAsString()));
            } catch (Exception e) {
                UTechnology.LOGGER.error("Bad module {}: {}", id, e.toString());
            }
        });
        Map<String, Spec> s = new TreeMap<>();
        specs.forEach((id, json) -> {
            try {
                JsonObject j = json.getAsJsonObject();
                Set<Item> comp = new HashSet<>();
                j.getAsJsonArray("component_items").forEach(x -> comp.add(item(x.getAsString())));
                Map<String, int[]> limits = new LinkedHashMap<>();
                j.getAsJsonObject("hatch_limits").entrySet().forEach(e -> limits.put(e.getKey(),
                        new int[] { e.getValue().getAsJsonObject().get("min").getAsInt(), e.getValue().getAsJsonObject().get("max").getAsInt() }));
                s.put(id.getPath(), new Spec(id.getPath(), j.get("filter").getAsString(), Set.copyOf(comp),
                        j.get("max_segments_built").getAsInt(), limits, j.get("casings_per_kit").getAsInt()));
            } catch (Exception e) {
                UTechnology.LOGGER.error("Bad multiblock spec {}: {}", id, e.toString());
            }
        });
        return new Snapshot(version, Collections.unmodifiableMap(sorted), Collections.unmodifiableMap(m), Collections.unmodifiableMap(s));
    }

    /** Server data reload listener for the three folders. */
    public static final class Listener extends SimplePreparableReloadListener<Map<String, Map<ResourceLocation, JsonElement>>> {

        private static final Gson GSON = new Gson();
        private static final String[] DIRS = { "upgrade_frames", "upgrade_modules", "upgrade_multiblocks" };

        @Override
        protected Map<String, Map<ResourceLocation, JsonElement>> prepare(ResourceManager rm, ProfilerFiller profiler) {
            Map<String, Map<ResourceLocation, JsonElement>> out = new HashMap<>();
            for (String dir : DIRS) {
                Map<ResourceLocation, JsonElement> map = new HashMap<>();
                SimpleJsonResourceReloadListener.scanDirectory(rm, dir, GSON, map);
                map.keySet().removeIf(id -> !id.getNamespace().equals(UTechnology.MOD_ID));
                out.put(dir, map);
            }
            return out;
        }

        @Override
        protected void apply(Map<String, Map<ResourceLocation, JsonElement>> data, ResourceManager rm, ProfilerFiller profiler) {
            Snapshot s = parse(current.version() + 1, data.get(DIRS[0]), data.get(DIRS[1]), data.get(DIRS[2]));
            set(s);
            UTechnology.LOGGER.info("Upgrade data loaded: {} frames, {} modules, {} multiblocks", s.frames().size(),
                    s.modules().size(), s.specs().size());
        }
    }

    private UpgradeData() {}
}
