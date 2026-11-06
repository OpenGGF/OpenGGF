package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.kis2.Kis2Physics;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.physics.*;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.*;
import com.openggf.tests.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Obj48 routine-four admission and live-velocity movement, not a buried-ring route. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS2HurtLauncher {
    private static final int X = 0x1000;
    private static final int Y = 0x1000;

    private static final class LockOnKnuckles extends Knuckles {
        LockOnKnuckles() {
            super("knuckles", (short) 0, (short) 0);
            setGameRulesForTest(Kis2Rules.RULES);
            applyExternalPhysicsProfile(Kis2Physics.KNUCKLES);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void hurtAndNormalCaptureRunThroughNativeMovementAndExit(boolean lockOn, boolean hurt) throws Exception {
        var w = world(lockOn);
        var p = w.player();
        var first = ball(w, X, Y, 0);
        var preHurt = w.rewind().capture();
        capture(first, p, hurt);
        var captured = w.rewind().capture();
        advanceToLaunch(w, first);
        assertEquals(hurt, p.isHurt(), "capture and opening do not write routine 2");
        assertEquals(0x1000, p.getXSpeed());
        assertEquals(0, p.getYSpeed());
        int x = p.getCentreX(), yFraction = p.getYSubpixelRaw();
        tick(w, first, 50);
        assertEquals(x + (hurt ? 32 : 16), p.getCentreX(),
                "hurt ObjectMove and Obj48_MoveCharacter both consume the live x velocity");
        assertEquals(hurt ? 0x30 : 0, p.getYSpeed());
        assertEquals((yFraction + (hurt ? 0x3000 : 0)) & 0xFFFF, p.getYSubpixelRaw(),
                "Obj48's later slot also consumes hurt gravity's live y velocity");
        var moved = w.rewind().capture();

        w.rewind().restore(captured);
        first = onlyBall(w);
        assertSame(first, p.getLatchedSolidObjectInstance(), "restore must relink native interact owner");
        advanceToLaunch(w, first);
        tick(w, first, 50);
        assertSnapshotEquals(moved, w.rewind().capture());

        // A second real launcher is placed at the reached exit, not a synthetic callback.
        var exit = ball(w, p.getCentreX(), p.getCentreY(), 0x80);
        exit.update(51, p);
        assertSame(exit, p.getLatchedSolidObjectInstance());
        assertEquals(0, state(first, p), "native interact clears the previous ball's participant routine");
        assertEquals(2, state(exit, p));
        assertEquals(hurt, p.isHurt());
        var transferred = w.rewind().capture();
        advanceToLaunch(w, exit);
        assertFalse(p.isObjectControlled());
        assertFalse(p.isOnObject());
        assertFalse(p.isHurt(), "negative subtype explicitly writes player routine 2 at exit");
        assertEquals(6, state(exit, p));
        assertEquals(0x1000, p.getXSpeed());
        var released = w.rewind().capture();
        for (int i = 0; i < 8; i++) exit.update(100 + i, p);
        assertEquals(0, state(exit, p));
        var cooled = w.rewind().capture();
        w.rewind().restore(released);
        exit = w.objects().getActiveObjects().stream().filter(LauncherBallObjectInstance.class::isInstance)
                .map(LauncherBallObjectInstance.class::cast).filter(o -> o.getSpawn().subtype() == 0x80)
                .findFirst().orElseThrow();
        assertEquals(6, state(exit, p));
        for (int i = 0; i < 8; i++) exit.update(100 + i, p);
        assertSnapshotEquals(cooled, w.rewind().capture());

        w.rewind().restore(transferred);
        exit = w.objects().getActiveObjects().stream().filter(LauncherBallObjectInstance.class::isInstance)
                .map(LauncherBallObjectInstance.class::cast).filter(o -> o.getSpawn().subtype() == 0x80)
                .findFirst().orElseThrow();
        assertSame(exit, p.getLatchedSolidObjectInstance());
        advanceToLaunch(w, exit);
        assertSnapshotEquals(released, w.rewind().capture());

        w.rewind().restore(preHurt);
        assertFalse(p.isHurt());
        assertFalse(p.isObjectControlled());
        first = onlyBall(w);
        capture(first, p, hurt);
        assertSnapshotEquals(captured, w.rewind().capture());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hurtMovementOffscreenAndUnloadReleaseOnlyTheirOwnPlayer(boolean lockOn) throws Exception {
        var w = world(lockOn);
        var p = w.player();
        var first = ball(w, X, Y, 0);
        capture(first, p, true);
        advanceToLaunch(w, first);
        GameServices.camera().setX((short) 0);
        GameServices.camera().setY((short) 0);
        first.update(50, p);
        assertFalse(p.isObjectControlled());
        assertTrue(p.isHurt(), "offscreen release does not write player routine 2");
        assertEquals(0, state(first, p));

        var second = ball(w, p.getCentreX(), p.getCentreY(), 0);
        second.update(51, p);
        assertTrue(p.isObjectControlled());
        w.objects().removeDynamicObject(first);
        assertTrue(p.isObjectControlled(), "old owner retirement cannot release the new owner");
        w.objects().removeDynamicObject(second);
        assertFalse(p.isObjectControlled());
        assertTrue(p.isHurt());
        assertEquals(0, state(second, p));
        GameServices.sprites().removeSprite(p.getCode());
        assertTrue(w.objects().getActiveObjects().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"false,dead", "false,debug", "true,dead", "true,debug"})
    void deadAndDebugAdmissionRemainRejected(boolean lockOn, String mode) {
        var w = world(lockOn);
        var p = w.player();
        if (mode.equals("dead")) p.setDead(true); else p.setDebugMode(true);
        ball(w, X, Y, 0).update(0, p);
        assertFalse(p.isObjectControlled());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeOffscreenReleaseDoesNotRequireInteractToStillPointAtThisBall(boolean lockOn) throws Exception {
        var w = world(lockOn);
        var p = w.player();
        var first = ball(w, X, Y, 0);
        capture(first, p, true);
        advanceToLaunch(w, first);
        // Declared released-on_object inlet; the terrain route to this state
        // is outside this checkpoint. This controls the native previous-ball
        // branch, not a claim that a speed-stacking route was executed.
        p.setOnObject(false);
        var second = ball(w, p.getCentreX(), p.getCentreY(), 0);
        second.update(50, p);
        assertEquals(4, state(first, p), "with on_object clear, native admission does not clear old ball state");
        assertEquals(2, state(second, p));
        assertSame(second, p.getLatchedSolidObjectInstance());
        GameServices.camera().setX((short) 0); GameServices.camera().setY((short) 0);
        first.update(51, p);
        assertEquals(0, state(first, p));
        assertEquals(2, state(second, p), "offscreen release does not write another object's routine");
        assertFalse(p.isObjectControlled(), "loc_25474 still clears this player's control byte");
        assertTrue(p.isHurt());
    }

    private record World(AbstractPlayableSprite player, PlayableSpriteMovement movement,
                         ObjectManager objects, RewindRegistry rewind) { }

    private static World world(boolean lockOn) {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(mock(Level.class)); // authored empty air
        GroundSensor.setLevelManager(level);
        var terrain = new TerrainCollisionManager();
        var collision = new CollisionSystem(terrain);
        var mode = TestEnvironment.activeGameplayMode();
        mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(), terrain, collision,
                mode.getSpriteManager(), level);
        AbstractPlayableSprite p = lockOn ? new LockOnKnuckles() : new Sonic("sonic", (short) 0, (short) 0);
        p.setCentreX((short) X); p.setCentreY((short) Y);
        GameServices.sprites().addSprite(p);
        var camera = GameServices.camera();
        camera.setFocusedSprite(p);
        camera.setMaxX((short) 0x7000); camera.setMaxY((short) 0x7000);
        var services = new TestObjectServices().withCamera(camera).withSpriteManager(GameServices.sprites());
        var objects = new ObjectManager(List.of(), null, 0, null, null, null, camera, services);
        services.withDirectObjectManager(objects);
        when(level.getObjectManager()).thenReturn(objects);
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = new RewindRegistry();
        registry.register(GameServices.sprites().rewindSnapshottable());
        registry.register(objects.rewindSnapshottable());
        registry.registerPostRestoreCallback("native-interact",
                () -> GameServices.sprites().refreshLatchedSolidObjectsAfterRewindRestore(objects));
        return new World(p, new PlayableSpriteMovement(p, collision, GameServices.gameState()), objects, registry);
    }

    private static LauncherBallObjectInstance ball(World w, int x, int y, int subtype) {
        return w.objects().createDynamicObject(() -> new LauncherBallObjectInstance(
                new ObjectSpawn(x, y, 0x48, subtype, 0, false, 0), "LauncherBall"));
    }

    private static void capture(LauncherBallObjectInstance ball, AbstractPlayableSprite p, boolean hurt) {
        if (hurt) assertTrue(p.applyHurtOrDeath(p.getCentreX() + 1, true, true));
        assertEquals(hurt, p.isHurt());
        ball.update(0, p);
        assertTrue(p.isObjectControlled());
        assertEquals(0x1000, p.getGSpeed());
        assertEquals(0, p.getXSpeed()); assertEquals(0, p.getYSpeed());
    }

    private static void advanceToLaunch(World w, LauncherBallObjectInstance ball) throws Exception {
        int updates = 0;
        while (state(ball, w.player()) == 2 && updates < 60) tick(w, ball, ++updates);
        assertEquals(49, updates, "zero-duration initial frame opens seven steps eight passes apart");
    }

    private static void tick(World w, LauncherBallObjectInstance ball, int frame) {
        w.movement().handleMovement(false, false, false, false, false, false, false, false);
        GameServices.camera().setX((short) (w.player().getCentreX() - 100));
        GameServices.camera().setY((short) (w.player().getCentreY() - 100));
        ball.update(frame, w.player());
    }

    private static LauncherBallObjectInstance onlyBall(World w) {
        return (LauncherBallObjectInstance) w.objects().getActiveObjects().iterator().next();
    }

    private static int state(LauncherBallObjectInstance ball, AbstractPlayableSprite p) throws Exception {
        Field f = LauncherBallObjectInstance.class.getDeclaredField("playerStates");
        f.setAccessible(true);
        Object value = ((Map<?, ?>) f.get(ball)).get(p);
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static void assertSnapshotEquals(CompositeSnapshot expected, CompositeSnapshot actual) {
        for (var entry : expected.entries().entrySet()) {
            assertEquals(List.of(), RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                    actual.entries().get(entry.getKey())), entry.getKey());
        }
    }
}
