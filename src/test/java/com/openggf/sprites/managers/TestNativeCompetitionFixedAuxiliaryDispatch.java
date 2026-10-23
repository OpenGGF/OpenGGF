package com.openggf.sprites.managers;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameModule;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionPolicy;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class)
class TestNativeCompetitionFixedAuxiliaryDispatch {
    @AfterEach
    void closeWorld() {
        TestEnvironment.resetAll();
    }

    @Test
    void initialFixedSlotsSelectHumanPlayerTwoWithoutAdvancingPlayerOneDust() {
        var f = fixture();
        assertFalse(f.tails.isCpuControlled());
        assertNull(f.tails.getCpuController());
        assertSame(f.tails, f.session.player(1));
        f.tails.setAnimationId(5);
        f.tails.setSpindash(true);
        f.sonic.setSpindash(true);
        var p1Dust = f.sonic.getSpindashDustController().captureRewindState();

        f.sprites.processInitialTailsFixedSlot();
        f.sprites.processInitialDustFixedSlot(1);

        assertIdleTail(f.tail, 7);
        assertEquals(new SpindashDustController.RewindState(1, 1, 0x0A, true),
                f.dust.captureRewindState());
        assertEquals(p1Dust, f.sonic.getSpindashDustController().captureRewindState());
    }

    @Test
    void canonicalWorldPassReadsLateParentAnimationAndTicksHumanTailOnlyOnce() {
        var f = fixture();
        f.tails.setAnimationId(0);
        f.sprites.processInitialTailsFixedSlot();
        assertEquals(0, f.tail.captureRewindState().lastParentAnim());

        canonicalTick(f, () -> {
            assertEquals(0, f.tail.captureRewindState().lastParentAnim(),
                    "the tail must wait for its own slot after dynamic objects");
            f.tails.setAnimationId(5);
        });

        assertIdleTail(f.tail, 7);
        verify(f.level, times(1)).updateObjectPositionsPostPhysicsWithoutTouches(any(), eq(false));
    }

    @Test
    void canonicalWorldPassAlreadyIncludesHumanPlayerTwoFixedSkidDust() {
        var f = fixture();
        f.tails.setAnimationProfile(new ScriptedVelocityAnimationProfile().setSkidAnimId(0x0D));
        f.tails.setAnimationId(0x0D);
        f.tails.setSkidDustTimer(3);
        f.sprites.processInitialDustFixedSlot(1);
        assertEquals(2, f.tails.getSkidDustTimer());
        var tail = spy(f.tail);
        f.tails.setTailsTailsController(tail);
        doAnswer(call -> {
            assertEquals(2, f.tails.getSkidDustTimer(), "the native tail slot precedes dust");
            return call.callRealMethod();
        }).when(tail).update();

        canonicalTick(f, () -> assertEquals(2, f.tails.getSkidDustTimer()));

        assertEquals(1, f.tails.getSkidDustTimer(),
                "the all-playable late dust pass must tick human P2 once");
        assertTrue(f.tails.isFixedSkidDustActive());
        assertEquals(0, f.sonic.getSkidDustTimer());
    }

    @Test
    void terminatedObjectWalkSkipsBothHumanAuxiliaries() {
        var f = fixture();
        f.tails.setAnimationProfile(new ScriptedVelocityAnimationProfile().setSkidAnimId(0x0D));
        f.tails.setAnimationId(0x0D);
        f.tails.setSkidDustTimer(3);
        var tail = f.tail.captureRewindState();
        when(f.objects.objectLoopReachedFixedInLevelSlots()).thenReturn(false);

        canonicalTick(f, () -> { });

        assertEquals(tail, f.tail.captureRewindState());
        assertEquals(3, f.tails.getSkidDustTimer());
        assertFalse(f.tails.isFixedSkidDustActive());
    }

    @Test
    void actualSpriteSnapshotRestoresBothAuxiliariesAndReplaysTheLatePass() {
        var f = fixture();
        f.tails.setAnimationId(5);
        f.tails.setSpindash(true);
        var adapter = f.sprites.rewindSnapshottable();
        var beforeInitial = adapter.capture();
        var untouchedTail = f.tail.captureRewindState();
        var untouchedDust = f.dust.captureRewindState();
        f.sprites.processInitialTailsFixedSlot();
        f.sprites.processInitialDustFixedSlot(1);
        var initialTail = f.tail.captureRewindState();
        var initialDust = f.dust.captureRewindState();
        assertNotEquals(untouchedTail, initialTail);
        assertNotEquals(untouchedDust, initialDust);

        adapter.restore(beforeInitial);
        assertEquals(untouchedTail, f.tail.captureRewindState());
        assertEquals(untouchedDust, f.dust.captureRewindState());
        f.sprites.processInitialTailsFixedSlot();
        f.sprites.processInitialDustFixedSlot(1);
        assertEquals(initialTail, f.tail.captureRewindState());
        assertEquals(initialDust, f.dust.captureRewindState());
        var snapshot = adapter.capture();

        canonicalTick(f, () -> { });
        var forwardTail = f.tail.captureRewindState();
        assertNotEquals(initialTail, forwardTail);

        adapter.restore(snapshot);
        assertSame(f.tails, f.session.player(1));
        assertFalse(f.tails.isCpuControlled());
        assertEquals(initialTail, f.tail.captureRewindState());
        assertEquals(initialDust, f.dust.captureRewindState());
        canonicalTick(f, () -> { });
        assertEquals(forwardTail, f.tail.captureRewindState());
    }

    @Test
    void activeNativeRosterDoesNotDispatchAnUnrelatedCpuTail() {
        var f = fixture();
        var extra = new Tails("extra-cpu", (short) 0, (short) 0);
        extra.setCpuControlled(true);
        extra.setAnimationId(5);
        var extraTail = new TailsTailsController(extra, null);
        extra.setTailsTailsController(extraTail);
        var untouched = extraTail.captureRewindState();
        f.sprites.addSprite(extra, "tails");
        f.tails.setAnimationId(5);

        f.sprites.processInitialTailsFixedSlot();
        f.sprites.advanceTailsTailsAfterObjectExecution();

        assertIdleTail(f.tail, 6);
        assertEquals(untouched, extraTail.captureRewindState());
    }

    @Test
    void ordinarySuppressedCpuPairRetainsRegisteredFixedSlotDispatch() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var sprites = new SpriteManager() {
            @Override protected boolean resolveCpuSidekickSuppressed() { return true; }
        };
        var sonic = new Sonic("sonic", (short) 0, (short) 0);
        var tails = new Tails("tails", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        tails.setAnimationId(5);
        tails.setSpindash(true);
        var tail = new TailsTailsController(tails, null);
        var dust = new SpindashDustController(tails, null, GameRules.SONIC_2.powerUp().fixedDustSlotIndex(true));
        tails.setTailsTailsController(tail);
        tails.setSpindashDustController(dust);
        sprites.addSprite(sonic);
        sprites.addSprite(tails, "tails");
        assertEquals(List.of(), sprites.getSidekicks());

        sprites.processInitialTailsFixedSlot();
        sprites.processInitialDustFixedSlot(1);
        sprites.advanceTailsTailsAfterObjectExecution();

        assertIdleTail(tail, 6);
        assertTrue(dust.captureRewindState().activeLastTick());
    }

    private static void assertIdleTail(TailsTailsController tail, int remaining) {
        var state = tail.captureRewindState();
        assertEquals(5, state.lastParentAnim());
        assertEquals(1, state.currentAnim());
        assertEquals(0x09, state.mappingFrame());
        assertEquals(1, state.frameIndex());
        assertEquals(remaining, state.frameTick());
    }

    private static Fixture fixture() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var sonic = new Sonic("sonic", (short) 0, (short) 0);
        var tails = new Tails("tails", (short) 0, (short) 0);
        var session = new Sonic2CompetitionSession();
        session.bindPlayers(sonic, tails);
        session.bindCameras(new Camera(), new Camera());
        session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
        var policy = new Sonic2CompetitionPolicy(session);
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:native-fixed-aux") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) world -> policy) : super.getGameService(type);
            }
        };
        var gameplay = SessionManager.openGameplaySession(module);
        GameplaySessionFactory.attachManagers(gameplay, EngineServices.current());
        var sprites = gameplay.getSpriteManager();
        var level = mock(LevelManager.class);
        var objects = mock(ObjectManager.class);
        when(level.getWorldSession()).thenReturn(gameplay.getWorldSession());
        when(level.getObjectManager()).thenReturn(objects);
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
        when(objects.objectLoopReachedFixedInLevelSlots()).thenReturn(true);
        var terrain = mock(TerrainCollisionManager.class);
        gameplay.attachLevelManagers(new WaterSystem(), new ParallaxManager(), terrain,
                new CollisionSystem(terrain), sprites, level);
        var services = new DefaultObjectServices(gameplay, EngineServices.current());
        when(objects.getObjectServices()).thenReturn(services);
        assertSame(gameplay.getWorldSession(), services.worldSession());
        assertSame(level, services.levelManager());
        assertSame(objects, services.objectManager());
        assertSame(gameplay.runtimeArtCoordinator(), services.runtimeArtCoordinator());
        assertSame(RuntimeArtCoordinator.NONE, services.runtimeArtCoordinator(),
                "ordinary module dispatch does not qualify physical tail DPLC loading");
        // Reversed insertion does not grant a human or CPU actor the native P2 slot.
        sprites.addSprite(tails);
        sprites.addSprite(sonic);
        var tail = new TailsTailsController(tails, null);
        var dust = new SpindashDustController(tails, null, GameRules.SONIC_2.powerUp().fixedDustSlotIndex(true));
        tails.setTailsTailsController(tail);
        tails.setSpindashDustController(dust);
        sonic.setSpindashDustController(new SpindashDustController(sonic, null,
                GameRules.SONIC_2.powerUp().fixedDustSlotIndex(false)));
        return new Fixture(sprites, session, sonic, tails, tail, dust, level, objects);
    }

    private static void canonicalTick(Fixture f, Runnable dynamicObjects) {
        // Real actors/controllers and session injection; only the surrounding
        // world is stubbed so this test requires no ROM or rendered level.
        doAnswer(call -> {
            dynamicObjects.run();
            call.getArgument(0, Runnable.class).run();
            return null;
        }).when(f.level).updateObjectPositionsPostPhysicsWithoutTouches(any(), eq(false));
        var module = mock(GameModule.class);
        when(module.getRules()).thenReturn(GameRules.SONIC_2);
        var gameplay = SessionManager.getCurrentGameplayMode();
        var context = new LevelFrameContext(module, GameRules.SONIC_2, null, null,
                f.sprites, gameplay.getGameStateManager(), gameplay.getTimerManager(),
                null, null, gameplay.hardwareTiming(), null, null, f.level);
        LevelFrameStep.execute(context, mock(PlcLifecycleFrame.class), PlcLifecyclePhase.ORDINARY_LEVEL,
                f.level, mock(Camera.class),
                () -> f.sprites.setFrameCounter(f.sprites.getFrameCounter() + 1),
                LevelFrameStep.DIRECT_WRAPPER);
    }

    private record Fixture(SpriteManager sprites, Sonic2CompetitionSession session,
                           Sonic sonic, Tails tails, TailsTailsController tail,
                           SpindashDustController dust, LevelManager level, ObjectManager objects) { }
}
