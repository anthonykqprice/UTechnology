package dev.utechnology.spike.research;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * team ID -> computed bonus. Written only on server start and when a team's research changes; read on recipe start.
 */
public final class ResearchCache {

    private static final Map<UUID, TeamBonus> CACHE = new ConcurrentHashMap<>();
    /** Number of per-team recomputations, for tests. */
    public static final AtomicLong RECOMPUTES = new AtomicLong();

    public static TeamBonus get(@Nullable UUID team) {
        if (team == null) return TeamBonus.NONE;
        return CACHE.getOrDefault(team, TeamBonus.NONE);
    }

    static void recompute(UUID team, Set<String> nodes) {
        RECOMPUTES.incrementAndGet();
        TeamBonus bonus = ResearchNodes.compute(nodes);
        if (bonus.equals(TeamBonus.NONE)) CACHE.remove(team);
        else CACHE.put(team, bonus);
    }

    static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    private ResearchCache() {}
}
