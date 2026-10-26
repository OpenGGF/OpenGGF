package com.openggf.sprites.managers;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameId;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelState;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.resources.DynamicArtDecisionOwner;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Sensor;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class)
class TestNativeCompetitionTitlePlayablePass {
    @AfterEach void closeWorld() { TestEnvironment.resetAll(); }

    @Test void titlePassRunsNormalMovementHistoryAnimationAndArtInNativeOrderWithoutClockOrInputAdvance() {
        var f = fixture();
        var input = new InputHandler();
        var held = LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(INPUT_RIGHT, INPUT_RIGHT, 0, 0, false, false),
                PlayerInputState.of(INPUT_RIGHT, INPUT_RIGHT, 0, 0, false, false));
        input.setLogicalOverride(held);
        f.sprites.publishHeldInputForLevelEvents(input);
        when(f.objects.getFrameCounter()).thenReturn(17);
        when(f.objects.getVblaCounter()).thenReturn(0x1234);
        var art = mock(DynamicArtLifecycleService.class);
        when(art.isRunActive()).thenReturn(true);
        installArtOwner(f.p1, art);
        installArtOwner(f.p2, art);
        int p1History = f.p1.getHistorySlotIndex(0);
        int p2History = f.p2.getHistorySlotIndex(0);
        short p1X = f.p1.getCentreX();
        short p2X = f.p2.getCentreX();

        SpriteManagerInternalAccess.runNativeCompetitionTitlePlayablePass(f.sprites);
        assertEquals(List.of("z-p1", "a-p2"), f.order);
        assertEquals((short) (p1X + 1), f.p1.getCentreX());
        assertEquals((short) (p2X + 1), f.p2.getCentreX());
        assertEquals((p1History + 1) & 63, f.p1.getHistorySlotIndex(0));
        assertEquals((p2History + 1) & 63, f.p2.getHistorySlotIndex(0));
        assertEquals(f.p1.getCentreX(), f.p1.getXHistory()[f.p1.getHistorySlotIndex(0)]);
        assertEquals(f.p2.getCentreX(), f.p2.getXHistory()[f.p2.getHistorySlotIndex(0)]);
        assertEquals(7, f.p1.getMappingFrame());
        assertEquals(7, f.p2.getMappingFrame());

        SpriteManagerInternalAccess.runNativeCompetitionTitlePlayablePass(f.sprites);
        assertEquals(List.of("z-p1", "a-p2", "z-p1", "a-p2"), f.order);
        assertEquals((p1History + 2) & 63, f.p1.getHistorySlotIndex(0));
        assertEquals((p2History + 2) & 63, f.p2.getHistorySlotIndex(0));
        assertEquals(8, f.p1.getMappingFrame());
        assertEquals(8, f.p2.getMappingFrame());
        assertEquals(List.of(0, 0), f.p1.epochs);
        assertEquals(List.of(0, 0), f.p2.epochs);
        assertEquals(0, f.p1.getInputHistory(0));
        assertEquals(0, f.p2.getInputHistory(0));
        assertFalse(f.p1.isRightPressed());
        assertFalse(f.p2.isRightPressed());
        assertEquals(0, f.sprites.getFrameCounter());
        assertEquals(0, f.level.getFrameCounter());
        assertEquals(17, f.objects.getFrameCounter());
        assertEquals(0x1234, f.objects.getVblaCounter());
        assertSame(held, input.logical());
        assertSame(held, SpriteManagerInternalAccess.publishedInput(f.sprites));
        for (var actor : List.of(f.p1, f.p2)) {
            for (int frame : List.of(7, 8)) {
                verify(art).observePlayerDplc(GameId.S2, actor.getCode(), frame, null,
                        DynamicArtLifecycleService.DecisionKind.NORMAL_OBJECT, null);
            }
        }
        verify(f.p1.getSpindashDustController(), never()).update();
        verify(f.p2.getSpindashDustController(), never()).update();
        verify(f.level, never()).update();
        verify(f.objects, never()).advanceVblaCounter();
    }

    @Test void failedPlayerSlotClearsScopeAndLeavesOrdinaryDustAndCounterBehaviorIntact() {
        var f = fixture();
        var failure = new IllegalStateException("movement failed");
        f.p1.beforeMovement = () -> { throw failure; };

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> SpriteManagerInternalAccess.runNativeCompetitionTitlePlayablePass(f.sprites)));
        assertFalse(NativeCompetitionTitlePlayableScope.suppressFixedDustFor(f.p1));
        assertFalse(NativeCompetitionTitlePlayableScope.suppressFixedDustFor(f.p2));
        assertEquals(0, f.sprites.getFrameCounter());
        assertTrue(f.order.isEmpty());

        f.p1.beforeMovement = () -> { };
        f.sprites.updateWithoutInput();
        assertEquals(1, f.sprites.getFrameCounter());
        assertEquals(List.of("z-p1", "a-p2"), f.order);
        verify(f.p1.getSpindashDustController()).update();
        verify(f.p2.getSpindashDustController()).update();
    }

    @Test void titlePassRejectsCpuAliasedMissingAndNonzeroEpochActorsBeforeDispatch() {
        var f = fixture();
        f.p2.setCpuControlled(true);
        assertThrows(IllegalStateException.class, () -> f.sprites.runNativeCompetitionTitlePlayablePass());
        f.p2.setCpuControlled(false);
        f.p2.setCpuController(mock(SidekickCpuController.class));
        assertThrows(IllegalStateException.class, () -> f.sprites.runNativeCompetitionTitlePlayablePass());
        f.p2.setCpuController(null);
        f.roster.players[1] = f.p1;
        assertThrows(IllegalStateException.class, () -> f.sprites.runNativeCompetitionTitlePlayablePass());
        f.roster.players[1] = null;
        assertThrows(IllegalStateException.class, () -> f.sprites.runNativeCompetitionTitlePlayablePass());
        f.roster.players[1] = f.p2;
        f.sprites.setFrameCounter(1);
        assertThrows(IllegalStateException.class, () -> f.sprites.runNativeCompetitionTitlePlayablePass());
        f.sprites.setFrameCounter(0);
        when(f.level.getFrameCounter()).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> f.sprites.runNativeCompetitionTitlePlayablePass());
        when(f.level.getFrameCounter()).thenReturn(0);
        when(f.objects.hasActiveInitialProcessSpritesDispatch()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> f.sprites.runNativeCompetitionTitlePlayablePass());
        when(f.objects.hasActiveInitialProcessSpritesDispatch()).thenReturn(false);
        f.sprites.removeSprite(f.p2.getCode());
        assertThrows(IllegalStateException.class, () -> f.sprites.runNativeCompetitionTitlePlayablePass());
        assertTrue(f.order.isEmpty());
        assertFalse(NativeCompetitionTitlePlayableScope.suppressFixedDustFor(f.p1));
    }

    @Test void scopeExcludesOnlyExactSuppliedActorsFixedDust() {
        var f = fixture();
        var unrelated = new Actor(f.p1.getCode(), new ArrayList<>());
        var unrelatedDust = spy(new SpindashDustController(unrelated, null, 129));
        unrelated.setSpindashDustController(unrelatedDust);
        var extensionDust = spy(new SpindashDustController(f.p2, null, -1));
        f.p2.setSpindashDustController(extensionDust);

        try (var ignored = NativeCompetitionTitlePlayableScope.enter(f.p1, f.p2)) {
            f.p1.getAnimationManager().update(0);
            f.p2.getAnimationManager().update(0);
            unrelated.getAnimationManager().update(0);
            verify(f.p1.getSpindashDustController(), never()).update();
            verify(extensionDust).update();
            verify(unrelatedDust).update();
        }
        f.p1.getAnimationManager().update(0);
        verify(f.p1.getSpindashDustController()).update();
    }

    @Test void scopeRejectsNestedEntryAndCrossThreadCloseWithoutClearingTheOwner() throws InterruptedException {
        var f = fixture();
        var scope = NativeCompetitionTitlePlayableScope.enter(f.p1, f.p2);
        try {
            assertThrows(IllegalStateException.class,
                    () -> NativeCompetitionTitlePlayableScope.enter(f.p1, f.p2));
            var foreignFailure = new AtomicReference<Throwable>();
            var foreignSeesScope = new AtomicReference<Boolean>();
            Thread foreign = Thread.ofPlatform().unstarted(() -> {
                foreignSeesScope.set(NativeCompetitionTitlePlayableScope.suppressFixedDustFor(f.p1));
                try { scope.close(); }
                catch (Throwable failure) { foreignFailure.set(failure); }
            });
            foreign.start();
            foreign.join();
            assertEquals(Boolean.FALSE, foreignSeesScope.get());
            assertInstanceOf(IllegalStateException.class, foreignFailure.get());
            assertTrue(NativeCompetitionTitlePlayableScope.suppressFixedDustFor(f.p1));
        } finally {
            scope.close();
        }
        assertFalse(NativeCompetitionTitlePlayableScope.suppressFixedDustFor(f.p1));
        assertThrows(IllegalStateException.class, scope::close);
    }

    private static void installArtOwner(Actor actor, DynamicArtLifecycleService lifecycle) {
        actor.getAnimationManager().setDynamicArtDecisionOwner(new DynamicArtDecisionOwner(
                lifecycle, GameId.S2, actor.getCode(), mock(PlayerSpriteRenderer.class)));
    }

    private static Fixture fixture() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var order = new ArrayList<String>();
        var p1 = new Actor("z-p1", order);
        var p2 = new Actor("a-p2", order);
        var roster = new Roster(p1, p2);
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:native-title-playables") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) ignored -> roster) : super.getGameService(type);
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
        var terrain = mock(TerrainCollisionManager.class);
        gameplay.attachLevelManagers(new WaterSystem(), new ParallaxManager(), terrain,
                new CollisionSystem(terrain), sprites, level);
        // Reverse insertion and lexical ordering must not choose the SST order.
        sprites.addSprite(p2);
        sprites.addSprite(p1);
        p1.setSpindashDustController(spy(new SpindashDustController(p1, null, 129)));
        p2.setSpindashDustController(spy(new SpindashDustController(p2, null, 130)));
        return new Fixture(sprites, roster, p1, p2, level, objects, order);
    }

    private record Fixture(SpriteManager sprites, Roster roster, Actor p1, Actor p2,
                           LevelManager level, ObjectManager objects, List<String> order) { }

    private static final class Roster implements NativeCompetitionRoster, WorldSessionPolicyState {
        private final AbstractPlayableSprite[] players;
        private final Camera[] cameras = {new Camera(), new Camera()};
        private final GameStateManager[] games = {new GameStateManager(), new GameStateManager()};
        private final LevelState[] levels = {new LevelGamestate(), new LevelGamestate()};
        private Roster(Actor p1, Actor p2) { players = new AbstractPlayableSprite[]{p1, p2}; }
        @Override public AbstractPlayableSprite player(int slot) { return players[slot]; }
        @Override public LevelState levelState(int slot) { return levels[slot]; }
        @Override public GameStateManager gameState(int slot) { return games[slot]; }
        @Override public Camera camera(int slot) { return cameras[slot]; }
        @Override public <T> T getService(Class<T> type) { return type.isInstance(this) ? type.cast(this) : null; }
        @Override public RewindSnapshottable<?> rewindAdapter() {
            return new RewindSnapshottable<Integer>() {
                @Override public String key() { return "test-native-title-roster"; }
                @Override public Integer capture() { return 0; }
                @Override public void restore(Integer state) { }
            };
        }
        @Override public void bindRoster(SpriteManager sprites) { }
        @Override public void beforeAssembly(LevelLoadCause cause) { }
        @Override public void failedAssembly(LevelLoadCause cause) { }
        @Override public void closeScreens() { }
        @Override public void retire() { }
    }

    private static final class Actor extends AbstractPlayableSprite implements CustomPlayablePhysics {
        private final List<String> order;
        private final List<Integer> epochs = new ArrayList<>();
        private Runnable beforeMovement = () -> { };
        private Actor(String code, List<String> order) {
            super(code, (short) 0x100, (short) 0x100);
            this.order = order;
            var animations = new SpriteAnimationSet();
            animations.addScript(0, new SpriteAnimationScript(0, List.of(7, 8), SpriteAnimationEndAction.LOOP, 0));
            setAnimationSet(animations);
        }
        @Override public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                boolean jump, boolean test, boolean speedUp, boolean slowDown, LevelManager level, int frame) {
            beforeMovement.run();
            assertFalse(up || down || left || right || jump || test || speedUp || slowDown);
            order.add(getCode());
            epochs.add(frame);
            NativePositionOps.addXPosPreserveSubpixel(this, 1);
        }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() {
            groundSensors = new Sensor[0]; ceilingSensors = new Sensor[0]; pushSensors = new Sensor[0];
        }
        @Override public void draw() { }
    }
}
