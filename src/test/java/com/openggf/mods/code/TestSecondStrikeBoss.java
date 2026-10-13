package com.openggf.mods.code;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameStateManager;
import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.sonic3k.objects.AizMinibossFlameChild;
import com.openggf.game.sonic3k.objects.AizMinibossInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the native boss owner around its semantic breath boundary and rewind. */
class TestSecondStrikeBoss {
    @TempDir
    static Path content;
    private static URLClassLoader loader;
    private static AizMinibossAttackPolicy policy;

    @BeforeAll
    static void compileContent() throws Exception {
        loader = TestSecondStrikeStages.compilePolicies(content);
        policy = (AizMinibossAttackPolicy) loader.loadClass("secondstrike.boss.SecondStrikeBosses")
                .getConstructor().newInstance();
    }

    @AfterAll
    static void closeLoader() throws Exception {
        if (loader != null) loader.close();
    }

    @Test
    void damagedPhaseEmitsOneDelayedNativeWaveWithoutChangingHitEligibility() throws Exception {
        Harness harness = new Harness(policy, 3);
        int nativeCollision = harness.boss.getCollisionFlags();
        breathComplete(harness.boss);
        harness.tick(19);
        assertEquals(0, harness.flames().size(), "the second wave leaves a readable delay");
        harness.tick(1);
        assertEquals(4, harness.flames().size(), "the follow-up uses the native four-flame wave");
        assertEquals(nativeCollision, harness.boss.getCollisionFlags());
        assertEquals(3, harness.boss.getState().hitCount);
        harness.tick(20);
        assertEquals(4, harness.flames().size(), "one breath completion cannot repeat indefinitely");
        assertFalse(harness.boss.getState().defeated);
        assertFalse(harness.gameState.isEndOfLevelFlag());
    }

    @Test
    void healthyAndDisabledBossesKeepTheNativeAttackBoundary() throws Exception {
        for (Harness harness : List.of(new Harness(policy, 6), new Harness(null, 3))) {
            breathComplete(harness.boss);
            harness.tick(40);
            assertTrue(harness.flames().isEmpty());
            assertFalse(harness.boss.getState().defeated);
        }
    }

    @Test
    void horizontalArcReturnCannotStartAnotherFollowupWave() throws Exception {
        Harness harness = new Harness(policy, 3);
        harness.boss.getState().routine = 12; // native horizontal-arc return hold
        invokeBreathBoundary(harness.boss);
        harness.tick(40);
        assertTrue(harness.flames().isEmpty());
    }

    @Test
    void pendingWaveRestoresAndReplaysFromTheSameRemainingDelay() throws Exception {
        Harness harness = new Harness(policy, 3);
        breathComplete(harness.boss);
        harness.tick(7);
        RewindRegistry registry = new RewindRegistry();
        registry.register(harness.objects.rewindSnapshottable());
        var snapshot = registry.capture();
        harness.tick(12);
        assertTrue(harness.flames().isEmpty());
        harness.tick(1);
        List<String> expectedWave = harness.flamePositions();
        assertEquals(4, expectedWave.size());

        registry.restore(snapshot);
        harness.boss = harness.objects.activeObjectsOfType(AizMinibossInstance.class).getFirst();
        assertTrue(harness.flames().isEmpty(), "restore drops the later wave and recreates its pending owner");
        harness.tick(12);
        assertTrue(harness.flames().isEmpty());
        harness.tick(1);
        assertEquals(expectedWave, harness.flamePositions());
    }

    @Test
    void killingHitCancelsPendingWaveAndKeepsNativeDefeat() throws Exception {
        Harness harness = new Harness(policy, 1);
        breathComplete(harness.boss);
        harness.tick(7);
        harness.boss.onPlayerAttack(null, null);
        assertEquals(0, harness.boss.getState().hitCount);
        assertEquals(0, harness.boss.getCollisionFlags());
        harness.tick(20);
        assertTrue(harness.flames().isEmpty(), "no attack may appear during the native fatal-hit delay");
        assertTrue(harness.boss.getState().defeated);
        assertFalse(harness.gameState.isEndOfLevelFlag(), "native defeat still owns signpost/result handoff");
    }

    @Test
    void freshBossAfterDeathHasNoPendingAttackFromItsPredecessor() throws Exception {
        Harness harness = new Harness(policy, 3);
        breathComplete(harness.boss);
        harness.tick(7);
        harness.objects.removeDynamicObject(harness.boss);
        harness.boss = harness.newBoss();
        harness.boss.getState().routine = 4; // native wait, without starting another breath
        harness.tick(40);
        assertTrue(harness.flames().isEmpty());
        assertEquals(6, harness.boss.getState().hitCount);
    }

    private static void breathComplete(AizMinibossInstance boss) throws Exception {
        boss.getState().routine = 10; // native breath half-cycle expiry
        invokeBreathBoundary(boss);
    }

    private static void invokeBreathBoundary(AizMinibossInstance boss) throws Exception {
        // Short owner-level regression: production reaches this same boundary by
        // its native swing half-cycle counter. No advertised gameplay skips it.
        Method boundary = AizMinibossInstance.class.getDeclaredMethod("onBreathCycleComplete");
        boundary.setAccessible(true);
        boundary.invoke(boss);
    }

    private static final class Harness {
        final GameStateManager gameState = new GameStateManager();
        final ObjectManager objects;
        AizMinibossInstance boss;
        int tick;

        Harness(AizMinibossAttackPolicy attackPolicy, int remainingHits) {
            SonicConfigurationService config = SonicConfigurationService.createStandalone();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            Camera camera = new Camera(config);
            GameModule module = new DelegatingGameModule(new Sonic3kGameModule(), "test:second-strike") {
                @Override public <T> T getGameService(Class<T> contract) {
                    return contract == AizMinibossAttackPolicy.class ? contract.cast(attackPolicy) : null;
                }
            };
            StubObjectServices services = new StubObjectServices() {
                @Override public GameStateManager gameState() { return gameState; }
                @Override public Camera camera() { return camera; }
                @Override public SonicConfigurationService configuration() { return config; }
                @Override public GameModule gameModule() { return module; }
            }.withIsolatedObjectManager();
            objects = services.objectManager();
            boss = newBoss();
            boss.getState().hitCount = remainingHits;
        }

        AizMinibossInstance newBoss() {
            return objects.createDynamicObject(() -> new AizMinibossInstance(new ObjectSpawn(
                    0x11F0, 0x330, Sonic3kObjectIds.AIZ_MINIBOSS, 0, 0, false, 0)));
        }

        void tick(int count) {
            for (int i = 0; i < count; i++) boss.update(++tick, null);
        }

        List<AizMinibossFlameChild> flames() {
            return objects.activeObjectsOfType(AizMinibossFlameChild.class).stream()
                    .filter(flame -> !flame.isDestroyed()).toList();
        }

        List<String> flamePositions() {
            return flames().stream().map(flame -> flame.getX() + "," + flame.getY()).toList();
        }
    }
}
