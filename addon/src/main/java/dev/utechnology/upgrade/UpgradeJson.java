package dev.utechnology.upgrade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Reads the generated data pack JSON (tools/generate.py) into the calculator's records. No Minecraft classes. */
public final class UpgradeJson {

    public static BonusCalculator.Frame frame(String key, JsonObject j) {
        return new BonusCalculator.Frame(key, BonusCalculator.tier(j.get("tier").getAsString()),
                BonusCalculator.tier(j.get("max_voltage").getAsString()), j.get("segment_cap").getAsInt(),
                j.get("module_slots").getAsInt());
    }

    public static BonusCalculator.Module module(String key, JsonObject j) {
        List<BonusCalculator.Upkeep> upkeep = new ArrayList<>();
        for (JsonElement e : j.getAsJsonArray("upkeep_per_parallel")) {
            JsonObject u = e.getAsJsonObject();
            upkeep.add(new BonusCalculator.Upkeep(u.get("fluid").getAsString(), u.get("amount").getAsInt()));
        }
        return new BonusCalculator.Module(key, BonusCalculator.tier(j.get("unlocked_at").getAsString()),
                j.get("lock").getAsBoolean(), j.get("lock_duration").getAsDouble(), j.get("duration").getAsDouble(),
                j.get("energy").getAsDouble(), j.get("parallels").getAsDouble(), List.copyOf(upkeep));
    }

    private UpgradeJson() {}
}
