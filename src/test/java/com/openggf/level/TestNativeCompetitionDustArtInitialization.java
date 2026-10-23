package com.openggf.level;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic2.Sonic2;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionPolicy;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestNativeCompetitionDustArtInitialization {
    @AfterEach
    void closeWorld() {
        RenderContext.reset();
        TestEnvironment.resetAll();
    }

    @Test
    void productionInitializerAssignsHumanPlayerTwoItsNativeDustSlotAndReplaysAuxiliaries() {
        var f = fixture(true, false);
        var dust = f.tails.getSpindashDustController();
        var tail = f.tails.getTailsTailsController();
        assertFalse(f.tails.isCpuControlled());
        assertNull(f.tails.getCpuController());
        // Sonic_Dust / Tails_Dust (s2.constants.asm:1158-1161),
        // selected by Obj08_Init's own record address (s2.asm:42725-42737).
        assertEquals(132, f.main.getSpindashDustController().fixedSlotIndex());
        assertEquals(133, dust.fixedSlotIndex());
        f.tails.setAnimationId(0x0D);
        f.tails.setSkidDustTimer(3);
        f.tails.setSpindash(true);
        var adapter = f.sprites.rewindSnapshottable();
        var beforeInitial = adapter.capture();
        var untouchedDust = dust.captureRewindState();

        f.sprites.processInitialTailsFixedSlot();
        f.sprites.processInitialDustFixedSlot(1);
        var initialTail = tail.captureRewindState();
        var initialDust = dust.captureRewindState();
        assertEquals(2, f.tails.getSkidDustTimer());
        assertTrue(initialDust.activeLastTick());
        assertNotEquals(untouchedDust, initialDust);

        adapter.restore(beforeInitial);
        assertEquals(untouchedDust, dust.captureRewindState());
        assertEquals(3, f.tails.getSkidDustTimer());
        f.sprites.processInitialTailsFixedSlot();
        f.sprites.processInitialDustFixedSlot(1);
        assertEquals(initialTail, tail.captureRewindState());
        assertEquals(initialDust, dust.captureRewindState());
        var afterInitial = adapter.capture();

        canonicalTick(f);
        var forwardTail = tail.captureRewindState();
        assertEquals(initialTail.frameTick() - 1, forwardTail.frameTick());
        assertEquals(1, f.tails.getSkidDustTimer());
        adapter.restore(afterInitial);
        assertSame(f.tails, f.session.player(1));
        assertEquals(133, dust.fixedSlotIndex());
        assertEquals(initialTail, tail.captureRewindState());
        assertEquals(initialDust, dust.captureRewindState());
        canonicalTick(f);
        assertEquals(forwardTail, tail.captureRewindState());
        assertEquals(1, f.tails.getSkidDustTimer());
    }

    @Test
    void foreignActorWithTheSameCodeCannotAcquireNativeFixedDustOwnership() {
        var f = fixture(true, false);
        var replacement = new Sonic("sonic", (short) 0, (short) 0);
        assertNotSame(replacement, f.session.player(0));
        f.sprites.addSprite(replacement);

        f.initializer.initialize();

        assertNotNull(replacement.getSpindashDustController());
        assertEquals(-1, replacement.getSpindashDustController().fixedSlotIndex());
        assertEquals(133, f.tails.getSpindashDustController().fixedSlotIndex());
    }

    @Test
    void initializerConsultsItsInjectedWorldInsteadOfTheAmbientNativeRoster() {
        var f = fixture(true, false);
        assertSame(f.tails, f.session.player(1));
        // Keep the active native world and its same actor identities in place,
        // while this detached initializer explicitly belongs to an ordinary world.
        var ordinary = new WorldSession(new Sonic2GameModule());
        when(f.level.getWorldSession()).thenReturn(ordinary);

        f.initializer.initialize();

        assertEquals(132, f.tails.getSpindashDustController().fixedSlotIndex(),
                "an ambient native roster must not override the initializer's injected world");
    }

    @Test
    void ordinaryCpuPairKeepsOnlyTheFirstFollowerInTheSecondaryFixedDustSlot() {
        var f = fixture(false, false);
        var later = new Tails("tails", (short) 0, (short) 0);
        later.setCpuControlled(true);
        f.sprites.addSprite(later, "tails");

        f.initializer.initialize();

        assertEquals(132, f.main.getSpindashDustController().fixedSlotIndex());
        assertEquals(133, f.tails.getSpindashDustController().fixedSlotIndex());
        assertNotNull(later.getSpindashDustController());
        assertEquals(-1, later.getSpindashDustController().fixedSlotIndex());
    }

    @Test
    void ordinarySoloTailsKeepsThePrimaryFixedDustSlot() {
        var f = fixture(false, true);

        assertInstanceOf(Tails.class, f.main);
        assertFalse(f.main.isCpuControlled());
        assertEquals(132, f.main.getSpindashDustController().fixedSlotIndex());
        f.main.setSpindash(true);
        f.sprites.processInitialDustFixedSlot(0);
        assertTrue(f.main.getSpindashDustController().captureRewindState().activeLastTick());
    }

    private static Fixture fixture(boolean nativePlayers, boolean soloTails) {
        var rom = TestEnvironment.currentRom();
        var sonic = new Sonic("sonic", (short) 0, (short) 0);
        var tails = new Tails(soloTails ? "tails" : "tails_p2", (short) 0, (short) 0);
        tails.setCpuControlled(!nativePlayers && !soloTails);
        AbstractPlayableSprite main = soloTails ? tails : sonic;
        var session = new Sonic2CompetitionSession();
        GameModule module = new Sonic2GameModule();
        if (nativePlayers) {
            session.bindPlayers(sonic, tails);
            session.bindCameras(new Camera(), new Camera());
            session.updateMenu(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
            var policy = new Sonic2CompetitionPolicy(session);
            module = new DelegatingGameModule(module, "test:native-dust-art") {
                @Override public <T> T getGameService(Class<T> type) {
                    return type == WorldSessionPolicyProvider.class
                            ? type.cast((WorldSessionPolicyProvider) world -> policy) : super.getGameService(type);
                }
            };
        }
        var gameplay = SessionManager.openGameplaySession(module);
        GameplaySessionFactory.attachManagers(gameplay, EngineServices.current());
        var sprites = gameplay.getSpriteManager();
        if (!soloTails) sprites.addSprite(tails, "tails");
        sprites.addSprite(main);
        var level = mock(LevelManager.class);
        when(level.resolveMainCharacterCode()).thenReturn(main.getCode());
        level.game = new Sonic2(rom);
        level.gameModule = module;
        when(level.activeGameModule()).thenReturn(module);
        when(level.playableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        when(level.getWorldSession()).thenReturn(gameplay.getWorldSession());
        var objects = mock(ObjectManager.class);
        when(level.getObjectManager()).thenReturn(objects);
        when(level.objectsExecuteAfterPlayerPhysics()).thenReturn(true);
        when(objects.objectLoopReachedFixedInLevelSlots()).thenReturn(true);
        var terrain = mock(TerrainCollisionManager.class);
        gameplay.attachLevelManagers(new WaterSystem(), new ParallaxManager(), terrain,
                new CollisionSystem(terrain), sprites, level);
        var config = mock(SonicConfigurationService.class);
        when(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn(main.getCode());
        var initializer = new LevelPlayableArtInitializer(level, sprites,
                mock(GraphicsManager.class), config, mock(CrossGameFeatureProvider.class));
        initializer.initialize();
        assertNotNull(main.getSpindashDustController());
        if (!soloTails) assertNotNull(tails.getSpindashDustController());
        return new Fixture(initializer, level, sprites, main, tails, session);
    }

    private static void canonicalTick(Fixture f) {
        doAnswer(call -> {
            assertEquals(2, f.tails.getSkidDustTimer());
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

    private record Fixture(LevelPlayableArtInitializer initializer, LevelManager level,
                           SpriteManager sprites, AbstractPlayableSprite main, Tails tails,
                           Sonic2CompetitionSession session) { }
}
