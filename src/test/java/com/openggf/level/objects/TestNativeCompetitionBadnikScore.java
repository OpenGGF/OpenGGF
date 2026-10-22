package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionPolicy;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.objects.badniks.CoconutsBadnikInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real badnik destruction routes the existing actor chain award to its owning progress bank. */
class TestNativeCompetitionBadnikScore {
    @BeforeEach void setup() { TestEnvironment.resetAll(); }
    @AfterEach void cleanup() { SessionManager.clear(); }

    @Test
    void eachNativeHumanKeepsItsOwnScoreAndExistingBadnikChain() {
        var fixture = new Fixture(true);
        var nativeSession = fixture.roster;
        assertSame(nativeSession, NativeCompetitionRoster.from(fixture.world));
        fixture.kill(nativeSession.player(1));
        assertScores(fixture, 0, 100, 0);
        fixture.kill(nativeSession.player(0));
        assertScores(fixture, 100, 100, 0);
        fixture.kill(nativeSession.player(1));
        assertScores(fixture, 100, 300, 0);
        fixture.kill(nativeSession.player(0));
        assertScores(fixture, 300, 300, 0);
        assertFalse(nativeSession.player(0).isCpuControlled());
        assertFalse(nativeSession.player(1).isCpuControlled());
    }

    @Test
    void aNonmemberHumanDoesNotAcquireANativeProgressBankByCharacterName() {
        var fixture = new Fixture(true);
        var outsider = new Tails("tails", (short) 160, (short) 256);
        assertEquals(-1, fixture.roster.slotOf(outsider));
        fixture.kill(outsider);
        assertScores(fixture, 0, 0, 100);
        fixture.kill(outsider);
        assertScores(fixture, 0, 0, 300);
    }

    @Test
    void ordinaryCpuTailsStillAwardsTheInjectedCampaignBank() {
        var fixture = new Fixture(false);
        assertNull(NativeCompetitionRoster.from(fixture.world));
        var ordinaryTails = new Tails("tails", (short) 160, (short) 256);
        ordinaryTails.setCpuControlled(true);
        fixture.kill(ordinaryTails);
        assertEquals(100, fixture.fallback.getScore());
        fixture.kill(ordinaryTails);
        assertEquals(300, fixture.fallback.getScore());
    }

    private static void assertScores(Fixture fixture, int first, int second, int fallback) {
        assertEquals(first, fixture.roster.gameState(0).getScore(), "native P1 score");
        assertEquals(second, fixture.roster.gameState(1).getScore(), "native P2 score");
        assertEquals(fallback, fixture.fallback.getScore(), "ordinary fallback score");
    }

    private static final class Fixture {
        final Sonic2CompetitionSession roster = new Sonic2CompetitionSession();
        final GameStateManager fallback = new GameStateManager();
        final WorldSession world;
        final ObjectManager objects;
        private int nextSpawn;

        Fixture(boolean nativeCompetition) {
            roster.bindPlayers(new Sonic("sonic", (short) 128, (short) 256),
                    new Tails("tails", (short) 160, (short) 256));
            roster.bindCameras(new Camera(), new Camera());
            roster.updateMenu(LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),
                    PlayerInputState.of(0, 0, 0, 0, true, true)), true);
            var base = new Sonic2GameModule();
            var policy = new Sonic2CompetitionPolicy(roster);
            var module = new DelegatingGameModule(base, "test:native-badnik-score") {
                @Override public <T> T getGameService(Class<T> type) {
                    return nativeCompetition && type == WorldSessionPolicyProvider.class
                            ? type.cast((WorldSessionPolicyProvider) ignored -> policy) : super.getGameService(type);
                }
            };
            world = new WorldSession(module);
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return objects; }
                @Override public WorldSession worldSession() { return world; }
                @Override public GameStateManager gameState() { return fallback; }
                @Override public Camera camera() { return roster.camera(0); }
                @Override public List<PlayableEntity> sidekicks() { return List.of(roster.player(1)); }
            };
            objects = new ObjectManager(List.of(), REGISTRY, -1, null, null, null, roster.camera(0), services);
        }

        void kill(PlayableEntity player) {
            var spawn = new ObjectSpawn(160 + nextSpawn++ * 32, 256, 0x9D, 0x1E, 0, false, 256);
            var body = objects.createDynamicObject(() -> new CoconutsBadnikInstance(spawn));
            assertNotNull(body);
            int slot = body.getSlotIndex();
            assertTrue(slot >= ObjectSlotLayout.SONIC_2.firstDynamicSlot());
            var touch = new TouchResponseResult(9, 16, 16, TouchCategory.ENEMY);
            // Invoke the real accepted-attack/destruction boundary, without
            // seeding score, chain counters, actor physics or comparison data.
            body.onPlayerAttack(player, touch);
            body.onPlayerAttack(player, touch); // A retired body cannot award twice.
            assertTrue(body.isDestroyed());
            assertTrue(objects.getActiveObjects().stream().anyMatch(object ->
                    object instanceof ExplosionObjectInstance explosion && explosion.getSlotIndex() == slot),
                    "actual badnik destruction must transfer its SST slot to the real explosion");
        }
    }

    private static final ObjectRegistry REGISTRY = new ObjectRegistry() {
        @Override public ObjectInstance create(ObjectSpawn spawn) { throw new AssertionError("dynamic-only fixture"); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "Coconuts"; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_2; }
    };
}
