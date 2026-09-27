package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.objects.MhzTwistedVineObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native standing bits survive vine recreation and replacement of both player slots. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestMhzTwistedVineRiderRewind {
    private final TestablePlayableSprite[] players = new TestablePlayableSprite[2];

    @BeforeEach void setup() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x1000, 0);
    }

    @AfterEach void cleanup() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @CsvSource({"false,320", "true,320", "false,800", "true,800"})
    void bothNativeRidersContinueTheArcAfterRecreation(boolean upper, int width) {
        players[0] = player("sonic", 0x1FC8, upper ? 0x0618 : 0x05D8, 0x600);
        players[1] = player("tails", 0x2038, upper ? 0x05D8 : 0x0618, -0x600);
        var camera = new Camera() {
            @Override public short getWidth() { return (short) width; }
            @Override public AbstractPlayableSprite getFocusedSprite() { return players[0]; }
        };
        var holder = new ObjectManager[1];
        var services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public List<PlayableEntity> sidekicks() { return List.of(players[1]); }
            @Override public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
                return new com.openggf.level.objects.ObjectPlayerQuery(
                        () -> players[0], () -> List.of(players[1]));
            }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        };
        var manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var source = manager.createDynamicObject(() -> new MhzTwistedVineObjectInstance(
                new ObjectSpawn(0x2000, 0x0600, 3, 0, upper ? 1 : 0, false, 0)));
        source.update(0, players[0]);
        assertTrue(players[0].isOnObject());
        assertTrue(players[1].isOnObject());
        var registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        var saved = registry.capture();
        var p1State = players[0].captureRewindState();
        var p2State = players[1].captureRewindState();
        var oldP1 = players[0];
        var oldP2 = players[1];
        oldP1.setCentreXPreserveSubpixel((short) 0x1FE0);
        oldP2.setCentreXPreserveSubpixel((short) 0x2020);
        source.update(1, oldP1);
        int p1Y = oldP1.getCentreY(), p2Y = oldP2.getCentreY();
        int p1Flip = oldP1.getFlipAngle(), p2Flip = oldP2.getFlipAngle();

        for (int cycle = 0; cycle < 2; cycle++) {
            players[0] = player("sonic", 0, 0, 0);
            players[1] = player("tails", 0, 0, 0);
            players[0].restoreRewindState(p1State);
            players[1].restoreRewindState(p2State);
            registry.restore(saved);
            var restored = manager.activeObjectsOfType(MhzTwistedVineObjectInstance.class).getFirst();
            assertNotSame(source, restored);
            players[0].setCentreXPreserveSubpixel((short) 0x1FE0);
            players[1].setCentreXPreserveSubpixel((short) 0x2020);
            restored.update(1, players[0]);
            assertEquals(p1Y, players[0].getCentreY(), "P1 curve continuation");
            assertEquals(p2Y, players[1].getCentreY(), "P2 curve continuation");
            assertEquals(p1Flip, players[0].getFlipAngle());
            assertEquals(p2Flip, players[1].getFlipAngle());
            assertSame(restored, players[0].getLatchedSolidObjectInstance());
            assertSame(restored, players[1].getLatchedSolidObjectInstance());
            players[0].setAir(true);
            players[1].setCentreXPreserveSubpixel((short) 0x2010);
            restored.update(2, players[0]);
            assertFalse(players[0].isOnObject(), "P1 release stays independent of P2");
            assertTrue(players[1].isOnObject());
            assertNotEquals(p2Y, players[1].getCentreY());
            assertEquals(p1Y, oldP1.getCentreY(), "old P1 identity is not retained");
            assertEquals(p2Y, oldP2.getCentreY(), "old P2 identity is not retained");
        }
    }

    private static TestablePlayableSprite player(String code, int x, int y, int speed) {
        var player = new TestablePlayableSprite(code, (short) 0, (short) 0);
        player.setCentreXPreserveSubpixel((short) x);
        player.setCentreYPreserveSubpixel((short) y);
        player.setAir(false);
        player.setXSpeed((short) speed);
        player.setGSpeed((short) speed);
        return player;
    }
}
