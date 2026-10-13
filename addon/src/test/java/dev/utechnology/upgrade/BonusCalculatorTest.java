package dev.utechnology.upgrade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The addon's bonus math matches the simulator: every case in bonus_cases.json was computed by
 * design/sim/sim-core.js (tools/sim-cases.js), with frames and modules read from the pack's generated data.
 */
class BonusCalculatorTest {

    static final Path DATA = Path.of("../pack/kubejs/data/utechnology");

    static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    static Map<String, BonusCalculator.Frame> frames() throws IOException {
        Map<String, BonusCalculator.Frame> out = new HashMap<>();
        try (var files = Files.list(DATA.resolve("upgrade_frames"))) {
            for (Path p : files.toList()) {
                String key = p.getFileName().toString().replace(".json", "");
                out.put(key, UpgradeJson.frame(key, read(p)));
            }
        }
        return out;
    }

    static Map<String, BonusCalculator.Module> modules() throws IOException {
        Map<String, BonusCalculator.Module> out = new HashMap<>();
        try (var files = Files.list(DATA.resolve("upgrade_modules"))) {
            for (Path p : files.toList()) {
                String key = p.getFileName().toString().replace(".json", "");
                out.put(key, UpgradeJson.module(key, read(p)));
            }
        }
        return out;
    }

    static JsonObject cases() throws IOException {
        try (var in = BonusCalculatorTest.class.getResourceAsStream("/bonus_cases.json")) {
            return JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes())).getAsJsonObject();
        }
    }

    static List<BonusCalculator.Module> mods(JsonArray keys, Map<String, BonusCalculator.Module> all) {
        List<BonusCalculator.Module> out = new ArrayList<>();
        keys.forEach(k -> out.add(all.get(k.getAsString())));
        return out;
    }

    @Test
    void everyGroupCaseMatchesTheSimulator() throws IOException {
        var frames = frames();
        var modules = modules();
        JsonArray cases = cases().getAsJsonArray("cases");
        assertTrue(cases.size() > 1000, "too few cases");
        for (JsonElement el : cases) {
            JsonObject c = el.getAsJsonObject(), e = c.getAsJsonObject("expect");
            var b = BonusCalculator.compute(frames.get(c.get("frame").getAsString()), c.get("built").getAsInt(),
                    mods(c.getAsJsonArray("modules"), modules));
            String at = c.toString();
            assertEquals(e.get("tier").getAsInt(), b.tier(), at);
            assertEquals(e.get("counted").getAsInt(), b.counted(), at);
            assertEquals(e.get("parallels").getAsDouble(), b.parallels(), 1e-9, at);
            assertEquals(e.get("duration").getAsDouble(), b.duration(), 1e-9, at);
            assertEquals(e.get("energy").getAsDouble(), b.energy(), 1e-9, at);
            assertEquals(e.get("lockDuration").getAsDouble(), b.lockDuration(), 1e-9, at);
            assertEquals(e.get("locked").getAsBoolean(), b.locked(), at);
            List<String> installed = new ArrayList<>();
            e.getAsJsonArray("installed").forEach(x -> installed.add(x.getAsString()));
            assertEquals(installed, b.installed(), at);
            Map<String, Integer> upkeep = new HashMap<>();
            e.getAsJsonArray("upkeepPerParallel").forEach(x -> upkeep.put(x.getAsJsonObject().get("fluid").getAsString(),
                    x.getAsJsonObject().get("amount").getAsInt()));
            Map<String, Integer> mine = new HashMap<>();
            b.upkeepPerParallel().forEach(u -> mine.put(u.fluid(), u.amount()));
            assertEquals(upkeep, mine, at);
        }
    }

    @Test
    void everyRecipeCaseMatchesTheSimulator() throws IOException {
        var frames = frames();
        var modules = modules();
        JsonArray cases = cases().getAsJsonArray("recipe_cases");
        assertFalse(cases.isEmpty());
        for (JsonElement el : cases) {
            JsonObject c = el.getAsJsonObject();
            var b = BonusCalculator.compute(frames.get(c.get("frame").getAsString()), c.get("segments").getAsInt(),
                    mods(c.getAsJsonArray("modules"), modules));
            double[] r = BonusCalculator.recipe(b, c.get("recipe_eut").getAsLong(), c.get("recipe_ticks").getAsInt(), true);
            String at = c.toString();
            if (c.get("expect").isJsonNull()) {
                assertNull(r, at);
                continue;
            }
            JsonObject e = c.getAsJsonObject("expect");
            assertNotNull(r, at);
            assertEquals(e.get("ticks").getAsDouble(), r[0], 1e-9, at);
            assertEquals(e.get("eut").getAsDouble(), r[1], 1e-9, at);
            assertEquals(e.get("overclocks").getAsInt(), (int) r[2], at);
            assertEquals(e.get("parallels").getAsDouble(), b.parallels(), 1e-9, at);
        }
    }
}
