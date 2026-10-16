package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.difficulty.ClamerRecipe;
import com.openggf.game.sonic3k.difficulty.IczStalagtiteRecipe;
import com.openggf.game.sonic3k.difficulty.LbzAlarmRecipe;
import com.openggf.game.sonic3k.objects.badniks.Flybot767BadnikInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Capture/restore/forward-replay of the C1-east creator presets on a real
 * {@link ObjectManager}: a recreated owner must come back with its preset and
 * replay identically; STOCK stays the recreated default.
 */
class TestSecondStrikeEastRecipeRewind {
    private ObjectManager manager;
    private TestablePlayableSprite player;
    private Camera camera;

    @BeforeEach void setup() {
        GraphicsManager.getInstance().initHeadless();
        player = new TestablePlayableSprite("sonic", (short) 0x0140, (short) 0x0100);
        camera = new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 1024; }
            @Override public short getHeight() { return 768; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public Camera camera() { return camera; }
            @Override public GameStateManager gameState() { return new GameStateManager(); }
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> player, List::of);
            }
        };
        manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 768, 0);
    }

    @AfterEach void cleanup() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    private ObjectRefId id(ObjectInstance object) {
        return manager.captureIdentityContext().requireIdentityTable().idFor(object);
    }

    @SuppressWarnings("unchecked")
    private <T> T byId(ObjectRefId id, Class<T> type) {
        return manager.getActiveObjects().stream().filter(type::isInstance)
                .filter(o -> id.equals(id(o))).map(o -> (T) o).findFirst().orElseThrow();
    }

    private static Object field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        throw new AssertionError("No field " + name);
    }

    @Test void staggeredClamerRecreatesWithItsPresetAndReplaysTheHeldTell() {
        var clamer = new ClamerObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.CLAMER, 0, 1, false, 0x0100),
                ClamerRecipe.STAGGERED_CADENCE);
        manager.addDynamicObject(clamer);
        ObjectRefId id = id(clamer);
        int tick = 0;
        // Release the offscreen gate, initialize, commit, then reach the held tell frame.
        while (clamer.testAutoCloseAnimTimer() <= 0x20 && tick < 400) clamer.update(tick++, player);
        assertTrue(clamer.testAutoCloseAnimTimer() > 0x20, "reached the extended tell hold");
        var captured = manager.rewindSnapshottable().capture();
        int routine = clamer.testRoutine(), timer = clamer.testAutoCloseAnimTimer();
        for (int i = 0; i < 12; i++) clamer.update(tick + i, player);
        int expectedTimer = clamer.testAutoCloseAnimTimer();
        manager.removeDynamicObject(clamer);
        manager.rewindSnapshottable().restore(captured);
        var restored = byId(id, ClamerObjectInstance.class);
        assertNotSame(clamer, restored);
        assertSame(ClamerRecipe.STAGGERED_CADENCE, field(restored, "recipe"));
        assertEquals(routine, restored.testRoutine());
        assertEquals(timer, restored.testAutoCloseAnimTimer());
        for (int i = 0; i < 12; i++) restored.update(tick + i, player);
        assertEquals(expectedTimer, restored.testAutoCloseAnimTimer(), "forward replay matches");
    }

    @Test void warnedStalactiteRecreatesWithItsPresetAndReplaysTheLongShake() {
        var stalactite = new IczStalagtiteObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.ICZ_STALAGTITE, 0, 0, false, 0x0100),
                IczStalagtiteRecipe.WARNED);
        manager.addDynamicObject(stalactite);
        ObjectRefId id = id(stalactite);
        player.setCentreX((short) 0x0700); // far from every trigger while the owner initializes
        stalactite.update(0, player);
        stalactite.refreshPostCameraRenderState();
        for (int i = 1; i <= 3; i++) stalactite.update(i, player);
        player.setCentreX((short) (0x0100 + 0x90)); // inside $A0, outside stock $70
        stalactite.update(4, player);
        assertEquals("SHAKING", stalactite.getPhaseNameForTesting());
        for (int i = 0; i < 10; i++) stalactite.update(5 + i, player);
        var captured = manager.rewindSnapshottable().capture();
        int remaining = 0;
        while ("SHAKING".equals(stalactite.getPhaseNameForTesting())) { stalactite.update(100 + remaining, player); remaining++; }
        assertEquals(38, remaining, "48-update shake, 10 already elapsed");
        manager.removeDynamicObject(stalactite);
        manager.rewindSnapshottable().restore(captured);
        var restored = byId(id, IczStalagtiteObjectInstance.class);
        assertSame(IczStalagtiteRecipe.WARNED, field(restored, "recipe"));
        assertEquals("SHAKING", restored.getPhaseNameForTesting());
        int replay = 0;
        while ("SHAKING".equals(restored.getPhaseNameForTesting())) { restored.update(100 + replay, player); replay++; }
        assertEquals(remaining, replay, "forward replay matches");
    }

    @Test void boundedAlarmKeepsItsSpentStateAndOneEscortAcrossRestore() {
        var alarm = new LbzAlarmObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.LBZ_ALARM, 0, 0, false, 0x0100),
                LbzAlarmRecipe.BOUNDED);
        manager.addDynamicObject(alarm);
        ObjectRefId id = id(alarm);
        alarm.onTouchResponse(player, new TouchResponseResult(0x17, 0x10, 0x10, TouchCategory.SPECIAL, 0), 0);
        alarm.update(0, player);
        long escorts = manager.getActiveObjects().stream()
                .filter(o -> o instanceof Flybot767BadnikInstance f && f.isBoundedEscort()).count();
        assertEquals(1, escorts);
        var captured = manager.rewindSnapshottable().capture();
        manager.removeDynamicObject(alarm);
        manager.rewindSnapshottable().restore(captured);
        var restored = byId(id, LbzAlarmObjectInstance.class);
        assertSame(LbzAlarmRecipe.BOUNDED, field(restored, "recipe"));
        assertEquals(Boolean.TRUE, field(restored, "summoned"));
        assertEquals(1, manager.getActiveObjects().stream()
                .filter(o -> o instanceof Flybot767BadnikInstance f && f.isBoundedEscort()).count(),
                "restore adopts the captured escort without duplicating it");
        for (int i = 1; i < 300; i++) restored.update(i, player);
        assertEquals(0, restored.getCollisionFlags(), "a summoned alarm never re-arms after restore");
    }

    @Test void stockPresetsRemainTheRecreatedDefaults() {
        var clamer = new ClamerObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.CLAMER, 0, 1, false, 0x0100));
        var alarm = new LbzAlarmObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.LBZ_ALARM, 0, 0, false, 0x0100));
        assertSame(ClamerRecipe.STOCK, field(clamer, "recipe"));
        assertSame(LbzAlarmRecipe.STOCK, field(alarm, "recipe"));
    }
}
