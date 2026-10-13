package dev.utechnology.spike.research;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import dev.utechnology.spike.machine.SegmentAssemblerMachine;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * /utspike research grant|revoke <team> [node]
 * /utspike research list
 * <team> is an FTB team name (as in /ftbteams), a team UUID, or a player name (their current team).
 */
public final class ResearchCommands {

    private static final SimpleCommandExceptionType NO_TEAM = new SimpleCommandExceptionType(
            Component.literal("Unknown team"));

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("utspike").requires(s -> s.hasPermission(2))
                .then(Commands.literal("research")
                        .then(Commands.literal("grant").then(Commands.argument("team", StringArgumentType.string())
                                .executes(c -> change(c, true))))
                        .then(Commands.literal("revoke").then(Commands.argument("team", StringArgumentType.string())
                                .executes(c -> change(c, false))))
                        .then(Commands.literal("list").executes(ResearchCommands::list)))
                .then(Commands.literal("machine").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ResearchCommands::machine))));
    }

    private static int change(CommandContext<CommandSourceStack> c, boolean grant) throws CommandSyntaxException {
        Team team = resolve(c.getSource(), StringArgumentType.getString(c, "team"));
        String node = ResearchNodes.TEST_DURATION;
        var server = c.getSource().getServer();
        boolean changed = grant ? ResearchService.grant(server, team.getId(), node) :
                ResearchService.revoke(server, team.getId(), node);
        c.getSource().sendSuccess(() -> Component.literal((grant ? "grant " : "revoke ") + node + " for " +
                team.getShortName() + " (" + team.getId() + "): " + (changed ? "changed" : "no change") +
                ", cached duration x" + ResearchCache.get(team.getId()).durationMultiplier()), true);
        return changed ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        var data = ResearchSavedData.get(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("research teams=" + data.all().size() + " schema=" +
                data.getLoadedSchema()), false);
        data.all().forEach((team, nodes) -> c.getSource().sendSuccess(() -> Component.literal(
                "team " + team + " nodes=" + nodes + " cached duration x" +
                        ResearchCache.get(team).durationMultiplier()),
                false));
        return data.all().size();
    }

    /** Debug: formed state, segments, owner, team and cached bonus of the machine at pos. */
    private static int machine(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        var m = MetaMachine.getMachine(c.getSource().getLevel(), pos);
        String msg;
        if (m instanceof SegmentAssemblerMachine s) {
            UUID team = TeamResolver.teamOf(s);
            var r = s.getRecipeLogic().getLastRecipe();
            msg = "formed=" + s.isFormed() + " segments=" + s.getSegments() + " owner=" + s.getOwnerUUID() +
                    " team=" + team + " cachedDuration=x" + ResearchCache.get(team).durationMultiplier() +
                    " working=" + s.getRecipeLogic().isWorking() +
                    (r == null ? "" : " recipe(parallels=" + r.parallels + ", duration=" + r.duration + ")");
        } else {
            msg = "no spike machine at " + pos;
        }
        String out = msg;
        c.getSource().sendSuccess(() -> Component.literal("SPIKE-MACHINE " + out), false);
        return 1;
    }

    private static Team resolve(CommandSourceStack source, String arg) throws CommandSyntaxException {
        var manager = FTBTeamsAPI.api().getManager();
        Optional<Team> team = manager.getTeamByName(arg);
        if (team.isEmpty()) {
            try {
                team = manager.getTeamByID(UUID.fromString(arg));
            } catch (IllegalArgumentException ignored) {}
        }
        if (team.isEmpty()) { // display name, e.g. "spiketeam" (getTeamByName wants the short name "spiketeam#xxxx")
            team = manager.getTeams().stream().filter(t -> t.getName().getString().equalsIgnoreCase(arg)).findFirst()
                    .map(t -> (Team) t);
        }
        if (team.isEmpty()) {
            var player = source.getServer().getPlayerList().getPlayerByName(arg);
            if (player != null) team = manager.getTeamForPlayer(player);
        }
        return team.orElseThrow(NO_TEAM::create);
    }

    private ResearchCommands() {}
}
