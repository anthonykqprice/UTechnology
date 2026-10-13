package dev.utechnology.spike.test;

import dev.utechnology.spike.UTSpike;
import dev.utechnology.spike.research.*;
import dev.utechnology.spike.test.SpikeTestUtil.Built;

import com.gregtechceu.gtceu.common.machine.owner.FTBOwner;

import dev.ftb.mods.ftbteams.data.PartyTeam;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static dev.utechnology.spike.test.SpikeTestUtil.*;

/** Check 2 (cached per-team bonus) and Check 3 (owner/team edge cases). */
@GameTestHolder(UTSpike.MOD_ID)
@PrefixGameTestTemplate(false)
public class ResearchTests {

    static final int TIMEOUT = 600;
    static final String NODE = ResearchNodes.TEST_DURATION;

    // four 1-segment machines fit in the "wide" template
    static final BlockPos[] SLOTS = { new BlockPos(2, 2, 1), new BlockPos(7, 2, 1), new BlockPos(12, 2, 1),
            new BlockPos(2, 2, 8) };

    private static void grant(GameTestHelper h, UUID team) {
        ResearchService.grant(h.getLevel().getServer(), team, NODE);
    }

    private static void revoke(GameTestHelper h, UUID team) {
        ResearchService.revoke(h.getLevel().getServer(), team, NODE);
    }

    private static void assertDuration(Built b, int expected) {
        assertEq(expected, runningRecipe(b).duration, "duration");
    }

    /** One grant reaches every machine of the team; another team's machine is unaffected. */
    @GameTest(template = "wide", timeoutTicks = TIMEOUT)
    public static void bonusAppliesToWholeTeamOnly(GameTestHelper h) {
        FakePlayer a = login(h, "a"), a2 = login(h, "a2"), b = login(h, "b");
        PartyTeam p = party(a, "P");
        join(p, a, a2);
        Built m1 = build(h, SLOTS[0], 1, a.getUUID());
        Built m2 = build(h, SLOTS[1], 1, a2.getUUID());
        Built m3 = build(h, SLOTS[2], 1, b.getUUID());
        h.startSequence()
                .thenWaitUntil(() -> { assertFormed(m1, 1); assertFormed(m2, 1); assertFormed(m3, 1); })
                .thenExecute(() -> {
                    assertEq(p.getId(), TeamResolver.teamOf(m1.controller()), "m1 team");
                    assertEq(p.getId(), TeamResolver.teamOf(m2.controller()), "m2 team");
                    grant(h, p.getId()); // machines are not touched
                    feed(m1, 1); feed(m2, 1); feed(m3, 1);
                })
                .thenWaitUntil(() -> { assertDuration(m1, 80); assertDuration(m2, 80); assertDuration(m3, 100); })
                .thenSucceed();
    }

    /** Cache recomputes only on real changes, only for that team, and grant/revoke reach the next recipe start. */
    @GameTest(template = "wide", timeoutTicks = TIMEOUT)
    public static void cacheUpdatesOnGrantAndRevoke(GameTestHelper h) {
        FakePlayer a = login(h, "c");
        UUID team = TeamResolver.teamOfPlayer(a.getUUID());
        Built m = build(h, SLOTS[0], 1, a.getUUID());
        var server = h.getLevel().getServer();
        h.startSequence()
                .thenExecute(() -> {
                    assertEq(TeamBonus.NONE, ResearchCache.get(team), "before");
                    long r0 = ResearchCache.RECOMPUTES.get();
                    assertEq(true, ResearchService.grant(server, team, NODE), "grant changed");
                    assertEq(false, ResearchService.grant(server, team, NODE), "second grant changed");
                    assertEq(r0 + 1, ResearchCache.RECOMPUTES.get(), "recomputes after grant x2");
                    assertEq(0.8, ResearchCache.get(team).durationMultiplier(), "after grant");
                    assertEq(true, ResearchSavedData.get(server).isDirty(), "save data dirty");
                })
                .thenWaitUntil(() -> assertFormed(m, 1))
                .thenExecute(() -> feed(m, 3)) // three consecutive runs
                .thenWaitUntil(() -> assertDuration(m, 80))
                .thenExecute(() -> revoke(h, team)) // while running: must apply to the next run
                .thenExecute(() -> assertEq(TeamBonus.NONE, ResearchCache.get(team), "after revoke"))
                .thenWaitUntil(() -> assertEq(1, diamonds(m), "first run done"))
                .thenWaitUntil(() -> assertDuration(m, 100))
                .thenSucceed();
    }

    /** NBT round trip with schema version; a newer schema is refused rather than silently dropped. */
    @GameTest(template = "empty")
    public static void saveDataRoundTrip(GameTestHelper h) {
        UUID t1 = UUID.randomUUID(), t2 = UUID.randomUUID();
        ResearchSavedData d = new ResearchSavedData();
        var server = h.getLevel().getServer();
        // use the live instance's package-private API via the service on a throwaway team
        ResearchService.grant(server, t1, NODE);
        CompoundTag tag = ResearchSavedData.get(server).save(new CompoundTag());
        assertEq(ResearchSavedData.SCHEMA_VERSION, tag.getInt("schema"), "schema written");
        ResearchSavedData loaded = ResearchSavedData.load(tag);
        assertEq(java.util.Set.of(NODE), loaded.nodes(t1), "t1 nodes after reload");
        assertEq(java.util.Set.of(), loaded.nodes(t2), "t2 nodes after reload");
        ResearchService.revoke(server, t1, NODE);
        CompoundTag future = tag.copy();
        future.putInt("schema", ResearchSavedData.SCHEMA_VERSION + 1);
        try {
            ResearchSavedData.load(future);
            throw new GameTestAssertException("newer schema accepted");
        } catch (IllegalStateException expected) {}
        assertEq(0, d.all().size(), "fresh data empty");
        h.succeed();
    }

    // ---------------- Check 3: owner / team edge cases ----------------

    /** GTM's FTBOwner reports the personal team, not the party the owner is in. */
    @GameTest(template = "empty")
    public static void ftbOwnerUuidIsPersonalTeam(GameTestHelper h) {
        FakePlayer a = login(h, "o");
        PartyTeam p = party(a, "O");
        Built m = build(h, new BlockPos(3, 2, 1), 1, a.getUUID());
        var owner = (FTBOwner) m.controller().getOwner();
        UUID gtm = owner.getUUID();
        UUID ours = TeamResolver.teamOf(m.controller());
        UTSpike.LOGGER.info("SPIKE-OWNER FTBOwner.getUUID()={} personalTeam={} party={} resolver={}", gtm,
                owner.getTeam() == null ? null : owner.getTeam().getId(), p.getId(), ours);
        assertEq(p.getId(), ours, "resolver gives party");
        assertEq(false, p.getId().equals(gtm), "FTBOwner.getUUID() is not the party");
        h.succeed();
    }

    /** Owner offline: FTB keeps team membership for offline players, bonus still applies. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void ownerOffline(GameTestHelper h) {
        FakePlayer a = login(h, "off");
        PartyTeam p = party(a, "Off");
        grant(h, p.getId());
        Built m = build(h, new BlockPos(3, 2, 1), 1, a.getUUID());
        h.startSequence()
                .thenExecute(() -> logout(a))
                .thenWaitUntil(() -> assertFormed(m, 1))
                .thenExecute(() -> {
                    assertEq(p.getId(), TeamResolver.teamOf(m.controller()), "team while offline");
                    feed(m, 1);
                })
                .thenWaitUntil(() -> assertDuration(m, 80))
                .thenSucceed();
    }

    /** Owner leaves a researched party and joins another: the machine follows the player. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void ownerChangesTeam(GameTestHelper h) {
        FakePlayer a = login(h, "lv"), keeper = login(h, "kp"), b = login(h, "nb");
        PartyTeam p1 = party(keeper, "Old");
        join(p1, keeper, a);
        PartyTeam p2 = party(b, "New");
        grant(h, p1.getId());
        Built m = build(h, new BlockPos(3, 2, 1), 1, a.getUUID());
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(m, 1))
                .thenExecute(() -> {
                    assertEq(p1.getId(), TeamResolver.teamOf(m.controller()), "team before");
                    leave(p1, a);
                    UTSpike.LOGGER.info("SPIKE-OWNER after leave: team={} (personal={})",
                            TeamResolver.teamOf(m.controller()), a.getUUID());
                    join(p2, b, a);
                    assertEq(p2.getId(), TeamResolver.teamOf(m.controller()), "team after join");
                    feed(m, 1);
                })
                .thenWaitUntil(() -> assertDuration(m, 100))
                .thenSucceed();
    }

    /** Machine placed while the owner was solo; the owner then creates a party. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void placedBeforeParty(GameTestHelper h) {
        FakePlayer a = login(h, "pre");
        UUID personal = TeamResolver.teamOfPlayer(a.getUUID());
        grant(h, personal); // research done while solo
        Built m = build(h, new BlockPos(3, 2, 1), 1, a.getUUID());
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(m, 1))
                .thenExecute(() -> {
                    PartyTeam p = party(a, "Late");
                    assertEq(p.getId(), TeamResolver.teamOf(m.controller()), "machine now resolves to party");
                    UTSpike.LOGGER.info("SPIKE-OWNER solo research on personal team {} does not follow into party {}",
                            personal, p.getId());
                    feed(m, 1);
                })
                .thenWaitUntil(() -> assertDuration(m, 100)) // personal research is not the party's
                .thenSucceed();
    }

    /** No owner (world edit / structure placement): no team, no bonus, recipes still run. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void noOwner(GameTestHelper h) {
        Built m = build(h, new BlockPos(3, 2, 1), 1, null);
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(m, 1))
                .thenExecute(() -> {
                    assertEq(true, m.controller().getOwner() == null, "owner null");
                    assertEq(true, TeamResolver.teamOf(m.controller()) == null, "team null");
                    feed(m, 1);
                })
                .thenWaitUntil(() -> assertDuration(m, 100))
                .thenSucceed();
    }

    /** Placed by automation: GTM records the fake player's UUID as owner; it has no FTB team. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void placedByFakePlayer(GameTestHelper h) {
        Built m = build(h, new BlockPos(3, 2, 1), 1, null);
        var fake = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        BlockPos abs = h.absolutePos(new BlockPos(3, 2, 1));
        var state = h.getLevel().getBlockState(abs);
        state.getBlock().setPlacedBy(h.getLevel(), abs, state, fake, net.minecraft.world.item.ItemStack.EMPTY);
        h.startSequence()
                .thenWaitUntil(() -> assertFormed(m, 1))
                .thenExecute(() -> {
                    assertEq(fake.getUUID(), m.controller().getOwnerUUID(), "owner is fake player");
                    UTSpike.LOGGER.info("SPIKE-OWNER fake-player-placed machine: owner={} team={}",
                            m.controller().getOwnerUUID(), TeamResolver.teamOf(m.controller()));
                    assertEq(true, TeamResolver.teamOf(m.controller()) == null, "no team for fake player");
                    feed(m, 1);
                })
                .thenWaitUntil(() -> assertDuration(m, 100))
                .thenSucceed();
    }
}
