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
import static org.mockito.Mockito.*;

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
        harness.tick(15);
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

    @Test void managerDispatchKeepsNativeSixteenTickCuePendingWaveReplayAndFatalCancellation() throws Exception {
        var harness = new Harness(policy, 3);
        startWarningAtNativePeak(harness);
        for (int i=0;i<7;i++) {
            harness.assertWarningPresentation(3);
            harness.managerTick(1);
        }
        var saved = harness.objects.rewindSnapshottable().capture();
        for (int i=7;i<16;i++) {
            harness.assertWarningPresentation(3);
            assertTrue(harness.flames().isEmpty()); harness.managerTick(1);
        }
        assertEquals(4, harness.flames().size()); var expected = harness.objects.rewindSnapshottable().capture();
        harness.objects.rewindSnapshottable().restore(saved); harness.tick=8;
        harness.boss = harness.objects.activeObjectsOfType(AizMinibossInstance.class).getFirst(); harness.managerTick(9);
        assertEquals(expected.dynamicObjects(), harness.objects.rewindSnapshottable().capture().dynamicObjects());
        harness.objects.rewindSnapshottable().restore(saved); harness.tick=8;
        harness.boss = harness.objects.activeObjectsOfType(AizMinibossInstance.class).getFirst();
        harness.boss.getState().hitCount=1; harness.boss.onPlayerAttack(null,null); harness.managerTick(20);
        assertTrue(harness.flames().isEmpty()); assertTrue(harness.boss.getState().defeated);
        assertEquals(0, liveFlares(harness));
    }

    @Test void longerAdmittedWarningHoldsNativeFinalPoseAndRestoresTheRemainingTail() throws Exception {
        var harness = new Harness(hits -> 32, 3); startWarningAtNativePeak(harness);
        for (int i=0;i<20;i++) { harness.assertWarningPresentation(3); harness.managerTick(1); }
        var saved = harness.objects.rewindSnapshottable().capture(); int savedTick = harness.tick;
        for (int i=0;i<11;i++) { harness.assertWarningPresentation(3); harness.managerTick(1); }
        assertTrue(harness.flames().isEmpty()); harness.assertWarningPresentation(3);
        harness.managerTick(1); assertEquals(4, harness.flames().size()); assertEquals(0, liveFlares(harness));
        var expected = harness.objects.rewindSnapshottable().capture();
        harness.objects.rewindSnapshottable().restore(saved); harness.tick=savedTick;
        harness.boss = harness.objects.activeObjectsOfType(AizMinibossInstance.class).getFirst(); harness.managerTick(12);
        assertEquals(expected.dynamicObjects(), harness.objects.rewindSnapshottable().capture().dynamicObjects());
    }

    @Test void ordinaryStockFlareRetainsInitAndF4BeforeItsNextPhysicalClear() throws Exception {
        var harness = new Harness(null, 3);
        var initialize = AizMinibossInstance.class.getDeclaredMethod("onInitialDelayComplete"); initialize.setAccessible(true);
        initialize.invoke(harness.boss);
        var barrel = harness.objects.activeObjectsOfType(com.openggf.game.sonic3k.objects.AizMinibossFlameBarrelChild.class).getFirst();
        var flare = harness.objects.createDynamicObject(() -> new com.openggf.game.sonic3k.objects.AizMinibossBarrelShotFlareChild(barrel));
        // Native Init,16 animation updates, then F4's retained outer draw.
        for (int i=0;i<18;i++) { flare.update(i,null); assertFalse(flare.isDestroyed()); }
        flare.update(18,null); assertTrue(flare.isDestroyed());
    }

    @Test void disabledPolicyKeepsTheOrdinaryManagerGraphAndTiming() throws Exception {
        var stock = new Harness(null, 3);
        var disabled = new Harness(hits -> -1, 3);
        startWarningAtNativePeak(stock); startWarningAtNativePeak(disabled);
        for (int i=0;i<40;i++) {
            assertEquals(stock.objects.rewindSnapshottable().capture().dynamicObjects(),
                    disabled.objects.rewindSnapshottable().capture().dynamicObjects());
            stock.managerTick(1); disabled.managerTick(1);
        }
    }

    @Test void childFirstAndParentFirstBarrelSlotsKeepEveryWarningPresentation() throws Exception {
        for (boolean childFirst : List.of(false, true)) {
            var harness = new Harness(policy, 3);
            var initialize = AizMinibossInstance.class.getDeclaredMethod("onInitialDelayComplete");
            initialize.setAccessible(true); initialize.invoke(harness.boss);
            if (childFirst) {
                harness.objects.removeDynamicObject(harness.boss);
                harness.objects.addDynamicObjectAtSlot(harness.boss, 70);
            }
            startWarningAtNativePeak(harness);
            for (int i=0;i<16;i++) {
                harness.assertWarningPresentation(3); harness.managerTick(1);
            }
            assertEquals(4, harness.flames().size());
        }
    }

    @Test void exhaustedSlotsBoundWarningAndWaveAllocationWithoutLateRetries() throws Exception {
        var harness = new Harness(policy, 3);
        var initialize = AizMinibossInstance.class.getDeclaredMethod("onInitialDelayComplete");
        initialize.setAccessible(true); initialize.invoke(harness.boss);
        var fillers = new java.util.ArrayList<com.openggf.level.objects.AbstractObjectInstance>();
        while (harness.objects.hasFreeDynamicSlot()) {
            fillers.add(harness.objects.createDynamicObject(() -> new com.openggf.level.objects.AbstractObjectInstance(
                    new ObjectSpawn(0x1100, 0x300, 0x7f, 0, 0, false, 0), "WarningSlotFiller") {
                @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { }
                @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
            }));
        }
        startWarningAtNativePeak(harness); harness.managerTick(16);
        assertEquals(0, liveFlares(harness)); assertTrue(harness.flames().isEmpty());
        fillers.forEach(harness.objects::removeDynamicObject);
        harness.managerTick(10); assertTrue(harness.flames().isEmpty(), "failed slots are not retried later");
    }

    private static long liveFlares(Harness harness) {
        return harness.objects.activeObjectsOfType(com.openggf.game.sonic3k.objects.AizMinibossBarrelShotFlareChild.class)
                .stream().filter(o -> !o.isDestroyed()).count();
    }

    private static void startWarningAtNativePeak(Harness harness) throws Exception {
        var initialize = AizMinibossInstance.class.getDeclaredMethod("onInitialDelayComplete"); initialize.setAccessible(true);
        if (harness.objects.activeObjectsOfType(com.openggf.game.sonic3k.objects.AizMinibossFlameBarrelChild.class).isEmpty())
            initialize.invoke(harness.boss);
        var swing = AizMinibossInstance.class.getDeclaredField("swingMotion"); swing.setAccessible(true);
        var motion = (com.openggf.game.sonic3k.objects.AizMinibossSwingMotion)swing.get(harness.boss);
        motion.setup1(harness.boss.getState()); motion.setCycleCounter(0);
        harness.boss.getState().routine=10; harness.boss.getState().yVel=-0xb0;
        // The native negative peak schedules the warning DURING Process_Sprites,
        // so its later-slot child executes in that same creation pass.
        harness.managerTick(1);
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
        harness.tick(8);
        assertTrue(harness.flames().isEmpty());
        harness.tick(1);
        List<String> expectedWave = harness.flamePositions();
        assertEquals(4, expectedWave.size());

        registry.restore(snapshot);
        harness.boss = harness.objects.activeObjectsOfType(AizMinibossInstance.class).getFirst();
        assertTrue(harness.flames().isEmpty(), "restore drops the later wave and recreates its pending owner");
        harness.tick(8);
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
        // A follow-up requires the real native three-barrel warning graph;
        // calling only the boundary on a never-initialized root is invisible.
        Method initialize = AizMinibossInstance.class.getDeclaredMethod("onInitialDelayComplete");
        initialize.setAccessible(true);
        initialize.invoke(boss);
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
        final com.openggf.level.render.PatternSpriteRenderer renderer = mock(com.openggf.level.render.PatternSpriteRenderer.class);
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
            when(renderer.isReady()).thenReturn(true);
            var renderManager = mock(com.openggf.level.objects.ObjectRenderManager.class);
            when(renderManager.getRenderer(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.AIZ_MINIBOSS)).thenReturn(renderer);
            var rom = mock(com.openggf.data.RomByteReader.class);
            // Independent byte_6913F fixture; production reads through its ROM service.
            when(rom.slice(0x6913F, 13)).thenReturn(new byte[]{7,1,7,1,8,1,9,3,10,3,11,3,(byte)0xF4});
            ObjectManager[] holder = new ObjectManager[1];
            camera.setX((short)0x1100); camera.setY((short)0x290);
            StubObjectServices services = new StubObjectServices() {
                @Override public com.openggf.level.objects.ObjectRenderManager renderManager() { return renderManager; }
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public GameStateManager gameState() { return gameState; }
                @Override public com.openggf.data.RomByteReader romReader() { return rom; }
                @Override public Camera camera() { return camera; }
                @Override public SonicConfigurationService configuration() { return config; }
                @Override public GameModule gameModule() { return module; }
            };
            objects = new ObjectManager(List.of(), module.createObjectRegistry(), 0, null, null, null, camera, services);
            holder[0] = objects;
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

        void assertWarningPresentation(int expected) {
            clearInvocations(renderer);
            var flares = objects.activeObjectsOfType(com.openggf.game.sonic3k.objects.AizMinibossBarrelShotFlareChild.class)
                    .stream().filter(o -> !o.isDestroyed()).toList();
            assertEquals(expected, flares.size());
            for (var flare : flares) {
                flare.appendRenderCommands(new java.util.ArrayList<>());
                verify(renderer).drawFrameIndex(intThat(frame -> frame >= 7 && frame <= 11),
                        eq(flare.getX()), eq(flare.getY()), eq(false), eq(false), eq(0));
            }
            verify(renderer, times(expected)).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt());
        }

        void managerTick(int count) {
            for (int i=0;i<count;i++) objects.update(0x1100, null, List.of(), ++tick, false);
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
