package dev.utechnology.spike.research;

import dev.utechnology.spike.UTSpike;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Per-team research state, stored in data/utspike_research.dat of the overworld. */
public class ResearchSavedData extends SavedData {

    public static final String NAME = "utspike_research";
    public static final int SCHEMA_VERSION = 1;

    private final Map<UUID, Set<String>> unlocked = new HashMap<>();
    /** Schema version the data was read from (for tests/migration logging). */
    private int loadedSchema = SCHEMA_VERSION;

    public static ResearchSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ResearchSavedData::load, ResearchSavedData::new,
                NAME);
    }

    public static ResearchSavedData load(CompoundTag tag) {
        ResearchSavedData data = new ResearchSavedData();
        int schema = tag.contains("schema") ? tag.getInt("schema") : 0;
        data.loadedSchema = schema;
        if (schema > SCHEMA_VERSION) {
            // Written by a newer addon. Refuse to guess; keep the data untouched in memory and don't save over it.
            UTSpike.LOGGER.error("Research data schema {} is newer than supported {}", schema, SCHEMA_VERSION);
            throw new IllegalStateException("utspike research data schema " + schema + " is newer than supported");
        }
        // schema 0 -> 1 migrations would go here.
        ListTag teams = tag.getList("teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teams.size(); i++) {
            CompoundTag t = teams.getCompound(i);
            Set<String> nodes = new HashSet<>();
            ListTag list = t.getList("nodes", Tag.TAG_STRING);
            for (int j = 0; j < list.size(); j++) nodes.add(list.getString(j));
            if (!nodes.isEmpty()) data.unlocked.put(t.getUUID("team"), nodes);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", SCHEMA_VERSION);
        ListTag teams = new ListTag();
        unlocked.forEach((team, nodes) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("team", team);
            ListTag list = new ListTag();
            nodes.stream().sorted().forEach(n -> list.add(StringTag.valueOf(n)));
            t.put("nodes", list);
            teams.add(t);
        });
        tag.put("teams", teams);
        return tag;
    }

    public Set<String> nodes(UUID team) {
        return Collections.unmodifiableSet(unlocked.getOrDefault(team, Set.of()));
    }

    public Map<UUID, Set<String>> all() {
        return Collections.unmodifiableMap(unlocked);
    }

    public int getLoadedSchema() {
        return loadedSchema;
    }

    boolean grant(UUID team, String node) {
        boolean changed = unlocked.computeIfAbsent(team, t -> new HashSet<>()).add(node);
        if (changed) setDirty();
        return changed;
    }

    boolean revoke(UUID team, String node) {
        Set<String> nodes = unlocked.get(team);
        if (nodes == null || !nodes.remove(node)) return false;
        if (nodes.isEmpty()) unlocked.remove(team);
        setDirty();
        return true;
    }
}
