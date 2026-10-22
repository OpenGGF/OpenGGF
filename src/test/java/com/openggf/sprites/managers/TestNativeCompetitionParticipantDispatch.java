package com.openggf.sprites.managers;

import com.openggf.camera.Camera;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelState;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.competition.NativeCompetitionWorld;
import com.openggf.game.patch.DelegatingGameModule;
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
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Sensor;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.sprites.playable.AbstractPlayableSprite.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeCompetitionParticipantDispatch {
    @AfterEach void closeWorld() { SessionManager.clear(); }

    @Test void independentHumansUseNativeOrderAndBothControllerEdges() {
        var fixture = fixture();
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(INPUT_RIGHT, INPUT_RIGHT, 0, 0, false, false),
                PlayerInputState.of(INPUT_LEFT, INPUT_LEFT, InputActionMasks.ACTION_B,
                        InputActionMasks.ACTION_B, false, false)));
        fixture.sprites.publishHeldInputForLevelEvents(input);
        assertTrue(fixture.p1.isRightPressed());
        assertFalse(fixture.p1.isLeftPressed());
        assertTrue(fixture.p2.isLeftPressed());
        assertFalse(fixture.p2.isRightPressed());
        fixture.sprites.update(input);
        assertEquals(List.of("z-p1", "a-p2"), fixture.order);
        assertTrue(fixture.p1.right);
        assertFalse(fixture.p1.left);
        assertTrue(fixture.p2.left);
        assertFalse(fixture.p2.right);
        assertFalse(fixture.p1.jump);
        assertTrue(fixture.p2.jump);
        assertSame(fixture.p1, fixture.sprites.getMainPlayable());
        assertEquals(List.of(fixture.p2), fixture.sprites.getSidekicks());
        assertFalse(fixture.p2.isCpuControlled());
        assertNull(fixture.p2.getCpuController());
    }

    @Test void initialAssemblyPublishesControllerTwoWithoutMovingEitherActor() {
        var fixture = fixture();
        fixture.sprites.processInitialPlayableSlots(new ProcessSpritesEpoch(0, 1, false),
                new InitialPlayableInput(INPUT_DOWN, INPUT_DOWN, 0,
                        INPUT_UP | INPUT_JUMP, INPUT_JUMP, InputActionMasks.ACTION_C, true));
        assertTrue(fixture.p1.isDownPressed());
        assertFalse(fixture.p1.isUpPressed());
        assertTrue(fixture.p2.isUpPressed());
        assertTrue(fixture.p2.isJumpPressed());
        assertTrue(fixture.order.isEmpty());
    }

    @Test void factoryAndRestoreKeepSessionBanksAndRelinkRestoredRoster() {
        var fixture = fixture();
        var context = SessionManager.getCurrentGameplayMode();
        assertSame(fixture.roster.camera(0), context.getCamera());
        assertSame(fixture.roster.gameState(0), context.getGameStateManager());
        int previousBinds = fixture.roster.bindCount;
        var snapshot = fixture.sprites.rewindSnapshottable().capture();
        var replacement = new Actor("a-p2", fixture.order);
        replacement.setCentreX((short) 456);
        fixture.sprites.addSprite(replacement);
        fixture.sprites.rewindSnapshottable().restore(snapshot);
        assertSame(replacement, fixture.roster.player(1));
        assertSame(replacement, fixture.sprites.getSidekicks().getFirst());
        assertEquals(0, replacement.getCentreX());
        assertEquals(previousBinds + 1, fixture.roster.bindCount);
    }

    @Test void fullRegistryRestoresBothCamerasAndRebindsRecreatedActorsWithoutMovingTheViews() {
        var fixture = fixture();
        var context = SessionManager.getCurrentGameplayMode();
        fixture.roster.camera(0).setFocusedSprite(fixture.p1);
        fixture.roster.camera(1).setFocusedSprite(fixture.p2);
        fixture.roster.camera(0).setX((short) 111);
        fixture.roster.camera(1).setX((short) 222);
        fixture.roster.camera(1).setY((short) 333);
        var snapshot = context.getRewindRegistry().capture();
        var replacement = new Actor("a-p2", fixture.order);
        fixture.sprites.addSprite(replacement);
        fixture.roster.camera(0).setX((short) 777);
        fixture.roster.camera(1).setX((short) 888);
        fixture.roster.camera(1).setY((short) 999);
        context.getRewindRegistry().restore(snapshot);
        assertEquals(111, fixture.roster.camera(0).getX());
        assertEquals(222, fixture.roster.camera(1).getX());
        assertEquals(333, fixture.roster.camera(1).getY());
        assertSame(fixture.p1, fixture.roster.camera(0).getFocusedSprite());
        assertSame(replacement, fixture.roster.camera(1).getFocusedSprite());
        assertSame(replacement, fixture.roster.player(1));
    }

    @Test void canonicalLevelUpdateAdvancesBothBanksOnceAndKeepsTheirPauseIndependent() {
        var fixture = fixture();
        fixture.level.resetLevelGamestate(fixture.roster.levelState(0));
        fixture.level.update();
        verify(fixture.roster.levelState(0)).update();
        verify(fixture.roster.levelState(1)).update();
    }

    @Test void invalidHumanRosterCannotRetireTheActiveWorld() {
        var fixture = fixture();
        var current = SessionManager.getCurrentGameplayMode();
        fixture.p2.setCpuControlled(true);
        assertThrows(IllegalArgumentException.class, () -> NativeCompetitionWorld.open(
                current.getWorldSession().rootGameModule(), current.getWorldSession().getGameModule(),
                fixture.roster, fixture.roster, EngineServices.current(), ignored -> fail("must not publish")));
        assertSame(current, SessionManager.getCurrentGameplayMode());
        assertSame(fixture.p1, fixture.sprites.getMainPlayable());
    }

    private static Fixture fixture() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var order = new ArrayList<String>();
        var p1 = new Actor("z-p1", order);
        var p2 = new Actor("a-p2", order);
        var roster = new Roster(p1, p2);
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:native-humans") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) world -> roster) : super.getGameService(type);
            }
        };
        var gameplay = SessionManager.openGameplaySession(module);
        GameplaySessionFactory.attachManagers(gameplay, EngineServices.current());
        var sprites = gameplay.getSpriteManager();
        var liveLevel = gameplay.getLevelManager();
        var level = mock(LevelManager.class);
        when(level.getWorldSession()).thenReturn(gameplay.getWorldSession());
        var terrain = mock(TerrainCollisionManager.class);
        gameplay.attachLevelManagers(new WaterSystem(), new ParallaxManager(),
                terrain, new CollisionSystem(terrain), sprites, level);
        // Reverse insertion and character-code order must not choose the native slots.
        sprites.addSprite(p2);
        sprites.addSprite(p1);
        return new Fixture(sprites, roster, p1, p2, order, liveLevel);
    }

    private record Fixture(SpriteManager sprites, Roster roster, Actor p1, Actor p2, ArrayList<String> order,
                           LevelManager level) { }

    private static final class Roster implements NativeCompetitionRoster, WorldSessionPolicyState {
        private final AbstractPlayableSprite[] players;
        private final Camera[] cameras = {new Camera(), new Camera()};
        private final GameStateManager[] games = {new GameStateManager(), new GameStateManager()};
        private final LevelState[] levels = {mock(LevelState.class), mock(LevelState.class)};
        private int bindCount;
        private Roster(Actor p1, Actor p2) { players = new AbstractPlayableSprite[]{p1, p2}; }
        @Override public AbstractPlayableSprite player(int slot) { return players[slot]; }
        @Override public LevelState levelState(int slot) { return levels[slot]; }
        @Override public GameStateManager gameState(int slot) { return games[slot]; }
        @Override public Camera camera(int slot) { return cameras[slot]; }
        @Override public <T> T getService(Class<T> type) { return type.isInstance(this) ? type.cast(this) : null; }
        @Override public RewindSnapshottable<?> rewindAdapter() {
            return new RewindSnapshottable<Integer>() {
                @Override public String key() { return "test-native-roster"; }
                @Override public Integer capture() { return 0; }
                @Override public void restore(Integer state) { }
            };
        }
        @Override public void bindRoster(SpriteManager sprites) {
            bindCount++;
            players[0] = (AbstractPlayableSprite) sprites.getSprite("z-p1");
            players[1] = (AbstractPlayableSprite) sprites.getSprite("a-p2");
        }
        @Override public void beforeAssembly(LevelLoadCause cause) { }
        @Override public void failedAssembly(LevelLoadCause cause) { }
        @Override public void closeScreens() { }
        @Override public void retire() { }
    }

    private static final class Actor extends AbstractPlayableSprite implements CustomPlayablePhysics {
        private final List<String> order;
        private boolean left, right, jump;
        private Actor(String code, List<String> order) { super(code, (short) 0, (short) 0); this.order = order; }
        @Override public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                boolean jump, boolean test, boolean speedUp, boolean slowDown, LevelManager level, int frame) {
            this.left = left; this.right = right; this.jump = jump; order.add(getCode());
        }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() {
            groundSensors = new Sensor[0]; ceilingSensors = new Sensor[0]; pushSensors = new Sensor[0];
        }
        @Override public void draw() { }
    }
}
