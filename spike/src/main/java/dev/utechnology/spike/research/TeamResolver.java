package dev.utechnology.spike.research;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.owner.FTBOwner;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Machine -> research team. */
public final class TeamResolver {

    /**
     * The team whose research applies to this machine, or null if none.
     * <p>
     * GTM's FTBOwner.getUUID()/getTeam() return the owner's personal (player) team even when the owner is in a
     * party, so we resolve the effective team ourselves with getTeamForPlayerID. Both are O(1) map lookups.
     */
    public static @Nullable UUID teamOf(MetaMachine machine) {
        MachineOwner owner = machine.getOwner();
        if (!(owner instanceof FTBOwner ftbOwner)) return null;
        return teamOfPlayer(ftbOwner.getPlayerUUID());
    }

    public static @Nullable UUID teamOfPlayer(UUID playerId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return null;
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerId).map(Team::getId).orElse(null);
    }

    private TeamResolver() {}
}
