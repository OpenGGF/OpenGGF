package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sonic2SpecialStageStreamedObjectCadenceTest {

    @Test
    void streamedRingAndBombRunTheirRomInitFallthroughBeforeTheNextNormalPass() throws Exception {
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();
        Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager objectManager =
                new Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager(null);
        set(objectManager, "objectLocationData", new byte[] {
                0x05, 0x40,       // ring: distance 5, angle $40
                0x45, 0x60,       // bomb: distance 5, angle $60
                (byte) 0xFF
        });
        set(objectManager, "currentPosition", 0);

        Sonic2TrackAnimator trackAnimator = new Sonic2TrackAnimator(null);
        trackAnimator.initializeWithMockLayout();
        set(trackAnimator, "currentSegmentType", 4); // length 11: depth = 5*4 + 11*4 = $40

        set(manager, "objectManager", objectManager);
        set(manager, "trackAnimator", trackAnimator);
        set(manager, "perspectiveData", alwaysVisiblePerspective());
        set(manager, "playerBootstrapPhase", Sonic2SpecialStageManager.PlayerBootstrapPhase.INITIALIZED);
        set(manager, "drawingIndex", 4);
        set(manager, "lastDrawingIndex", 3);

        invoke(manager, "streamSpecialStageObjects");

        List<Sonic2SpecialStageObject> objects = objectManager.getActiveObjects();
        assertEquals(2, objects.size());
        assertEquals(0x003F3334L, depthFixed(objects.get(0)),
                "Obj60 init must fall through to the drawing-index-4 $CCCC depth step");
        assertEquals(0x003F3334L, depthFixed(objects.get(1)),
                "Obj61 shares the same init-fallthrough depth path");
        assertEquals(Sonic2SpecialStageObject.State.ACTIVE, objects.get(0).getState());
        assertEquals(0, objects.get(0).getAnimIndex());
        assertTrue(objects.get(0).isOnScreen(), "the init tick must also publish projection state");

        set(manager, "drawingIndex", 0);
        invoke(manager, "executeActiveSpecialStageObjects");

        assertEquals(0x003E6667L, depthFixed(objects.get(0)),
                "the following active pass must contribute exactly one normal $CCCD step");
        assertEquals(0x003E6667L, depthFixed(objects.get(1)));
    }

    @Test
    void firstPlayerToCollectSharedRingConsumesCollisionForBothPlayers() throws Exception {
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();
        Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager objectManager =
                new Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager(null);
        Sonic2SpecialStageRing ring = new Sonic2SpecialStageRing();
        ring.initialize(4, 0x40);
        set(ring, "animIndex", 8);
        objectManager.getActiveObjects().add(ring);

        Sonic2SpecialStagePlayer sonic = initializedPlayer(
                Sonic2SpecialStagePlayer.PlayerType.SONIC, true);
        Sonic2SpecialStagePlayer tails = initializedPlayer(
                Sonic2SpecialStagePlayer.PlayerType.TAILS, false);

        set(manager, "objectManager", objectManager);
        set(manager, "players", new ArrayList<>(List.of(sonic, tails)));

        invoke(manager, "checkObjectCollisions");

        assertEquals(1, objectManager.getRingsCollected(),
                "Obj61_TestCollision clears collision on the first successful player");
        assertEquals(Sonic2SpecialStageObject.State.COLLECTED, ring.getState());
    }

    @Test
    void bombTestsTailsFirstWhenSonicsUnsignedDepthIsNotLess() throws Exception {
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();
        Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager objectManager =
                new Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager(null);
        Sonic2SpecialStageBomb bomb = new Sonic2SpecialStageBomb();
        bomb.initialize(4, 0x40);
        set(bomb, "animIndex", 8);
        objectManager.getActiveObjects().add(bomb);

        Sonic2SpecialStagePlayer sonic = initializedPlayer(
                Sonic2SpecialStagePlayer.PlayerType.SONIC, true);
        Sonic2SpecialStagePlayer tails = initializedPlayer(
                Sonic2SpecialStagePlayer.PlayerType.TAILS, false);
        set(sonic, "ssZPos", 0x80);
        set(tails, "ssZPos", 0x80);
        set(sonic, "collisionProperty", 0);
        set(tails, "collisionProperty", 0);

        set(manager, "objectManager", objectManager);
        set(manager, "players", new ArrayList<>(List.of(sonic, tails)));

        invoke(manager, "checkObjectCollisions");
        sonic.update(0, 0);
        tails.update(0, 0);

        assertFalse(sonic.isHurt(), "ROM must not test Sonic first when Sonic Z >= Tails Z");
        assertTrue(tails.isHurt(), "the first successful Tails collision must consume the bomb");
    }

    private static Sonic2SpecialStagePlayer initializedPlayer(
            Sonic2SpecialStagePlayer.PlayerType type,
            boolean main) {
        Sonic2SpecialStagePlayer player = new Sonic2SpecialStagePlayer(type, main);
        player.initializeScalarStateFromRomObjectRoutine();
        return player;
    }

    private static Sonic2PerspectiveData alwaysVisiblePerspective() {
        return new Sonic2PerspectiveData() {
            @Override
            public PerspectiveEntry getEntry(int trackFrame, int depth) {
                return new PerspectiveEntry(0x7F, 0x58, 8, 8, 0, 0);
            }
        };
    }

    private static long depthFixed(Sonic2SpecialStageObject object) {
        return object.captureBaseRewindSnapshot().depthFixed();
    }

    private static void invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
