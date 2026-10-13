package dev.utechnology.spike.research;

import dev.utechnology.spike.UTSpike;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** The only writer of research state. Keeps save data and the cache in step. */
public final class ResearchService {

    public static void onServerStarted(MinecraftServer server) {
        ResearchCache.clear();
        ResearchSavedData data = ResearchSavedData.get(server);
        data.all().forEach(ResearchCache::recompute);
        UTSpike.LOGGER.info("Research cache built: {} teams with bonuses (schema {})", ResearchCache.size(),
                data.getLoadedSchema());
    }

    public static void onServerStopped() {
        ResearchCache.clear();
    }

    public static boolean grant(MinecraftServer server, UUID team, String node) {
        ResearchSavedData data = ResearchSavedData.get(server);
        if (!data.grant(team, node)) return false;
        ResearchCache.recompute(team, data.nodes(team));
        return true;
    }

    public static boolean revoke(MinecraftServer server, UUID team, String node) {
        ResearchSavedData data = ResearchSavedData.get(server);
        if (!data.revoke(team, node)) return false;
        ResearchCache.recompute(team, data.nodes(team));
        return true;
    }

    private ResearchService() {}
}
