package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.objects.badniks.TechnoSqueekAttachmentObjectInstance;
import com.openggf.game.sonic3k.objects.badniks.TechnoSqueekBadnikInstance;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Task-18 integration coverage for mutable FBZ traversal object state. */
class TestFbzObjectRewind {
    @BeforeEach
    void initializeHeadlessObjectRuntime() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x1000, 0);
    }

    @AfterEach
    void resetHeadlessObjectRuntime() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void eventsPolarityCarriersHazardsAndBadniksRoundTripAndReplayDeterministically() {
        Harness harness = Harness.create();
        ObjectManager manager = harness.manager();

        FbzExitDoorInstance door = manager.createDynamicObject(() -> new FbzExitDoorInstance(
                spawn(0x1080, 0x700, Sonic3kObjectIds.FBZ_EXIT_DOOR, 0)));
        FbzMagneticSpikeBallObjectInstance field = manager.createDynamicObject(
                () -> new FbzMagneticSpikeBallObjectInstance(
                        spawn(0x10C0, 0x700, Sonic3kObjectIds.FBZ_MAGNETIC_SPIKE_BALL, 0xFE)));
        FbzMineObjectInstance mine = manager.createDynamicObject(() -> new FbzMineObjectInstance(
                spawn(0x1100, 0x700, Sonic3kObjectIds.FBZ_MINE, 0)));
        manager.createDynamicObject(() -> new FbzSnakePlatformObjectInstance(
                spawn(0x1140, 0x700, Sonic3kObjectIds.FBZ_SNAKE_PLATFORM, 0)));
        manager.createDynamicObject(() -> new TechnoSqueekBadnikInstance(
                spawn(0x1120, 0x700, Sonic3kObjectIds.TECHNOSQUEEK, 0)));

        door.onTouchResponse(harness.player(),
                new TouchResponseResult(0, 8, 0x20, TouchCategory.SPECIAL), 0);
        manager.update(0x0F00, harness.player(), List.of(), 0);
        manager.update(0x0F00, harness.player(), List.of(), 1);

        assertTrue(door.isFlying(), "the real SPECIAL callback must put the exit door into flight");
        assertEquals(2, field.mappingFrame(), "active FBZ polarity must advance the magnetic field");
        assertFalse(mine.isArmed(), "the mine must be in its captured blink countdown, not skipped ahead");
        assertEquals(1, mine.mappingFrame(), "the nearby player must put the mine in its blinking hazard state");
        assertEquals(4, live(manager, FbzSnakePlatformObjectInstance.class).size(),
                "the carrier must publish its complete native segment family");
        assertEquals(1, live(manager, TechnoSqueekAttachmentObjectInstance.class).size(),
                "the badnik must publish its native attachment child");

        RewindRegistry rewind = harness.rewind();
        CompositeSnapshot checkpoint = rewind.capture();

        manager.update(0x0F00, harness.player(), List.of(), 2);
        harness.runtime().advanceMagneticPhase(64);
        CompositeSnapshot uninterrupted = rewind.capture();

        for (ObjectInstance object : new ArrayList<>(manager.getActiveObjects())) {
            manager.removeDynamicObject(object);
        }
        harness.runtime().advanceMagneticPhase(128);
        rewind.restore(checkpoint);
        assertSnapshotEquals(checkpoint, rewind.capture(), "capture -> restore -> capture");

        manager.update(0x0F00, harness.player(), List.of(), 2);
        harness.runtime().advanceMagneticPhase(64);
        assertSnapshotEquals(uninterrupted, rewind.capture(), "one-frame forward replay");
    }

    @Test
    void pendingActTransitionRoundTripsAcrossLevelEventAndZoneRuntimeOwners() {
        Sonic3kLevelEventManager levelEvents = new Sonic3kLevelEventManager();
        levelEvents.initLevel(Sonic3kZoneIds.ZONE_FBZ, 0);
        Sonic3kFBZEvents events = levelEvents.getFbzEvents();
        events.setEventsFg5(true);
        ZoneRuntimeRegistry zoneRuntime = new ZoneRuntimeRegistry();
        zoneRuntime.install(new FbzZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE, events));

        RewindRegistry rewind = new RewindRegistry();
        rewind.register(levelEvents);
        rewind.register(zoneRuntime);
        CompositeSnapshot pending = rewind.capture();

        events.updateAct1BackgroundEvent(0x2E20, 0x540, false);
        CompositeSnapshot consumed = rewind.capture();
        assertFalse(events.isEventsFg5(),
                "FBZ1BGE_Normal must consume the real results transition flag");

        rewind.restore(pending);
        assertSnapshotEquals(pending, rewind.capture(), "pending transition restore",
                "level-event", "zone-runtime");
        events.updateAct1BackgroundEvent(0x2E20, 0x540, false);
        assertSnapshotEquals(consumed, rewind.capture(), "transition forward replay",
                "level-event", "zone-runtime");
    }

    private static ObjectSpawn spawn(int x, int y, int id, int subtype) {
        return new ObjectSpawn(x, y, id, subtype, 0, false, 0);
    }

    private static <T extends ObjectInstance> List<T> live(ObjectManager manager, Class<T> type) {
        return manager.getActiveObjects().stream()
                .filter(object -> type.isInstance(object) && !object.isDestroyed())
                .map(type::cast)
                .toList();
    }

    private static void assertSnapshotEquals(CompositeSnapshot expected, CompositeSnapshot actual,
                                             String boundary) {
        assertSnapshotEquals(expected, actual, boundary, "object-manager", "zone-runtime");
    }

    private static void assertSnapshotEquals(CompositeSnapshot expected, CompositeSnapshot actual,
                                             String boundary, String... keys) {
        for (String key : keys) {
            List<String> diffs = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diffs.isEmpty(), () -> boundary + " changed " + key + ": " + diffs);
        }
    }

    private record Harness(ObjectManager manager, PlayableEntity player,
                           FbzZoneRuntimeState runtime, RewindRegistry rewind) {
        static Harness create() {
            PlayableEntity player = mock(PlayableEntity.class);
            when(player.getCentreX()).thenReturn((short) 0x1100);
            when(player.getCentreY()).thenReturn((short) 0x700);
            when(player.isDebugMode()).thenReturn(false);

            Camera camera = new Camera() {
                @Override public short getX() { return 0x0F00; }
                @Override public short getY() { return 0x0600; }
                @Override public short getWidth() { return 0x4000; }
                @Override public short getHeight() { return 0x1000; }
                @Override public boolean isVerticalWrapEnabled() { return false; }
            };
            ObjectManager[] holder = new ObjectManager[1];
            StubObjectServices services = new StubObjectServices() {
                private final ObjectPlayerQuery players = new ObjectPlayerQuery(() -> player, List::of);
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public ObjectPlayerQuery playerQuery() { return players; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            };

            Sonic3kFBZEvents events = new Sonic3kFBZEvents();
            events.init(1);
            events.setMagneticState(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 0);
            FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE, events);
            services.zoneRuntimeRegistry().install(runtime);

            ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                    null, null, GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.reset(camera.getX());
            RewindRegistry rewind = new RewindRegistry();
            rewind.register(manager.rewindSnapshottable());
            rewind.register(services.zoneRuntimeRegistry());
            return new Harness(manager, player, runtime, rewind);
        }
    }
}
