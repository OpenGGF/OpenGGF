package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.audio.AudioManager;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.FbzCloudInstance;
import com.openggf.game.sonic3k.objects.Fbz2SubbossInstance;
import com.openggf.game.sonic3k.objects.FbzEndBossEventControlInstance;
import com.openggf.game.sonic3k.objects.FbzEndBossGraphMember;
import com.openggf.game.sonic3k.objects.FbzEndBossInstance;
import com.openggf.game.sonic3k.objects.FbzEndEggCapsuleButtonInstance;
import com.openggf.game.sonic3k.objects.FbzEndEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.FbzExitDoorInstance;
import com.openggf.game.sonic3k.objects.FbzMinibossInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.scroll.SwScrlFbz;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Task-18 whole-graph rewind coverage for FBZ's terminal encounter. */
class TestFbzBossGraphRewind {
    private static final int CAMERA_X = 0x2D00;
    private static final int CAMERA_Y = 0x0600;

    @BeforeEach
    void initializeHeadlessObjectRuntime() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(
                CAMERA_X, CAMERA_Y, CAMERA_X + 0x4000, CAMERA_Y + 0x1000, 0);
    }

    @AfterEach
    void resetHeadlessObjectRuntime() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void bossCloudExitAndCapsuleGraphsRoundTripAndReplayDeterministically() {
        Harness harness = Harness.create();
        ObjectManager manager = harness.manager();

        harness.events().setUpAct2BossEvent(harness.events().bossEventSetupEffects(
                manager, () -> { }, () -> { }, () -> { }), false);
        harness.events().setBossBackgroundState(0x10, 0, 0);

        FbzEndBossInstance boss = manager.createDynamicObject(() -> new FbzEndBossInstance(
                new ObjectSpawn(0x31C0, 0x690, Sonic3kObjectIds.FBZ_END_BOSS,
                        0, 0, false, 0)));
        manager.createDynamicObject(() -> new FbzEndEggCapsuleInstance(0x307C, 0x660));
        FbzExitDoorInstance exit = manager.createDynamicObject(() -> new FbzExitDoorInstance(
                new ObjectSpawn(0x3100, 0x660, Sonic3kObjectIds.FBZ_EXIT_DOOR,
                        0, 0, false, 0)));
        exit.onTouchResponse(harness.player(),
                new TouchResponseResult(0, 8, 0x20, TouchCategory.SPECIAL), 0);

        for (int frame = 0; frame <= 126; frame++) harness.step(frame);

        assertEquals(10, live(manager, FbzCloudInstance.class).size(),
                "the production boss setup must allocate all ten cloud address slots");
        assertTrue(live(manager, FbzCloudInstance.class).stream()
                        .anyMatch(cloud -> cloud.getX() != 0 || cloud.getY() != 0),
                "the real cloud deform source must publish live world positions");
        assertEquals(1, live(manager, FbzEndBossEventControlInstance.class).size(),
                "the production boss setup must retain its event controller");
        assertNotNull(boss.ship(), "the real pre-music routine must publish the boss ship");
        assertEquals(2, boss.arms().size(), "the real graph must contain both arms");
        assertEquals(8, boss.chainLinks().size(), "the real graph must contain both four-link chains");
        assertEquals(16, live(manager, FbzEndBossGraphMember.class).size(),
                "the steady combat graph must contain exactly sixteen SST members");
        assertEquals(1, live(manager, FbzEndEggCapsuleButtonInstance.class).size(),
                "the capsule must publish its real after-current button child");
        assertTrue(exit.isFlying(), "the exit door must retain its SPECIAL-hit flight state");
        assertTrue(harness.events().getCloudRewindIds().stream().allMatch(id -> id != null),
                "the runtime owner must publish every live cloud identity");

        RewindRegistry rewind = harness.rewind();
        CompositeSnapshot checkpoint = rewind.capture();
        harness.step(127);
        CompositeSnapshot uninterrupted = rewind.capture();

        for (ObjectInstance object : new ArrayList<>(manager.getActiveObjects())) {
            manager.removeDynamicObject(object);
        }
        harness.events().setBossBackgroundState(8, 0x12000, -0x8000);
        harness.levelEvents().setBossActive(false);
        rewind.restore(checkpoint);

        assertSnapshotEquals(checkpoint, rewind.capture(), "terminal graph restore");
        assertRestoredGraphLinks(harness);

        harness.step(127);
        assertSnapshotEquals(uninterrupted, rewind.capture(), "terminal graph forward replay");
        assertRestoredGraphLinks(harness);
    }

    @Test
    void act1MinibossFullNativeGraphRoundTripsAndReplaysDeterministically() {
        FamilyHarness harness = FamilyHarness.create(0, 0x2E20, 0x540, 0x2F00, 0x5E0);
        ObjectManager manager = harness.manager();
        manager.createDynamicObject(() -> new FbzMinibossInstance(new ObjectSpawn(
                0x2F00, 0x5E0, Sonic3kObjectIds.FBZ_MINIBOSS, 0, 0, true, 3)));
        harness.step(0);

        ObjectInstance plunger = manager.getActiveObjects().stream()
                .filter(object -> "FBZMinibossPlunger".equals(object.getName()))
                .findFirst().orElseThrow();
        SolidObjectListener contactOwner = (SolidObjectListener) plunger;
        SolidContact standing = new SolidContact(true, false, false, true, false);
        for (int frame = 1; frame < 170; frame++) {
            contactOwner.onSolidContact(harness.player(), standing, frame);
            harness.step(frame);
        }
        assertEquals(FbzMinibossInstance.fullPersistentGraphSlots(),
                familyCount(manager, "FBZMiniboss"),
                "the production plunger contact and arm timers must publish the full native graph");

        CompositeSnapshot checkpoint = harness.rewind().capture();
        contactOwner.onSolidContact(harness.player(), standing, 170);
        harness.step(170);
        CompositeSnapshot uninterrupted = harness.rewind().capture();

        new ArrayList<>(manager.getActiveObjects()).forEach(manager::removeDynamicObject);
        harness.rewind().restore(checkpoint);
        assertSnapshotEquals(checkpoint, harness.rewind().capture(),
                "FBZ1 miniboss graph restore", "object-manager", "level-event");
        assertEquals(FbzMinibossInstance.fullPersistentGraphSlots(),
                familyCount(manager, "FBZMiniboss"));

        ObjectInstance restoredPlunger = manager.getActiveObjects().stream()
                .filter(object -> "FBZMinibossPlunger".equals(object.getName()))
                .findFirst().orElseThrow();
        ((SolidObjectListener) restoredPlunger).onSolidContact(harness.player(), standing, 170);
        harness.step(170);
        assertSnapshotEquals(uninterrupted, harness.rewind().capture(),
                "FBZ1 miniboss graph forward replay", "object-manager", "level-event");
    }

    @Test
    void act2LaserSubbossGraphRoundTripsAndReplaysDeterministically() {
        FamilyHarness harness = FamilyHarness.create(1, 0x2A00, 0x560, 0x2B40, 0x5F0);
        ObjectManager manager = harness.manager();
        manager.createDynamicObject(() -> new Fbz2SubbossInstance(new ObjectSpawn(
                0x2B40, 0x5F0, Sonic3kObjectIds.FBZ2_SUBBOSS, 0, 0, true, 417)));
        for (int frame = 0; frame <= 125; frame++) harness.step(frame);

        assertEquals(1, live(manager, Fbz2SubbossInstance.class).size());
        assertEquals(1, exactNameCount(manager, "FBZ2SubbossLaser"),
                "the natural drop and pre-laser timers must publish the live beam child");
        assertTrue(familyCount(manager, "FBZ2Subboss") >= 10,
                "the laser subboss must retain its root, machine, pilot, mask, corners, sides, and beam");

        CompositeSnapshot checkpoint = harness.rewind().capture();
        harness.step(126);
        CompositeSnapshot uninterrupted = harness.rewind().capture();

        new ArrayList<>(manager.getActiveObjects()).forEach(manager::removeDynamicObject);
        harness.rewind().restore(checkpoint);
        assertSnapshotEquals(checkpoint, harness.rewind().capture(),
                "FBZ2 laser subboss graph restore", "object-manager", "level-event");
        assertEquals(1, exactNameCount(manager, "FBZ2SubbossLaser"));

        harness.step(126);
        assertSnapshotEquals(uninterrupted, harness.rewind().capture(),
                "FBZ2 laser subboss graph forward replay", "object-manager", "level-event");
    }

    private static void assertRestoredGraphLinks(Harness harness) {
        ObjectManager manager = harness.manager();
        FbzEndBossInstance boss = live(manager, FbzEndBossInstance.class).getFirst();
        assertNotNull(boss.ship());
        assertSame(boss, boss.ship().boss(), "the restored ship must target the restored root");
        assertTrue(boss.chainLinks().stream().allMatch(link -> link.boss() == boss),
                "every restored chain link must target the restored root");

        var identities = manager.captureIdentityContext().requireIdentityTable();
        for (var cloudId : harness.events().getCloudRewindIds()) {
            assertTrue(identities.resolve(cloudId) instanceof FbzCloudInstance,
                    "each event-owned cloud id must resolve to a restored live cloud");
        }
        assertEquals(1, live(manager, FbzEndEggCapsuleButtonInstance.class).size(),
                "the restored capsule button must remain in the restored parent graph");
        assertFalse(live(manager, FbzEndEggCapsuleButtonInstance.class).getFirst().isDestroyed());
    }

    private static void assertSnapshotEquals(CompositeSnapshot expected, CompositeSnapshot actual,
                                             String boundary) {
        assertSnapshotEquals(expected, actual, boundary,
                "object-manager", "level-event", "zone-runtime");
    }

    private static void assertSnapshotEquals(CompositeSnapshot expected, CompositeSnapshot actual,
                                             String boundary, String... keys) {
        for (String key : keys) {
            List<String> diffs = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diffs.isEmpty(), () -> boundary + " changed " + key + ": " + diffs);
        }
    }

    private static <T> List<T> live(ObjectManager manager, Class<T> type) {
        return manager.getActiveObjects().stream()
                .filter(object -> type.isInstance(object) && !object.isDestroyed())
                .map(type::cast)
                .toList();
    }

    private static long familyCount(ObjectManager manager, String namePrefix) {
        return manager.getActiveObjects().stream()
                .filter(object -> !object.isDestroyed() && object.getName().startsWith(namePrefix))
                .count();
    }

    private static long exactNameCount(ObjectManager manager, String name) {
        return manager.getActiveObjects().stream()
                .filter(object -> !object.isDestroyed() && name.equals(object.getName()))
                .count();
    }

    private record FamilyHarness(ObjectManager manager, PlayableEntity player,
                                 Sonic3kLevelEventManager levelEvents,
                                 RewindRegistry rewind, int cameraX) {
        static FamilyHarness create(int act, int cameraX, int cameraY, int playerX, int playerY) {
            PlayableEntity player = mock(PlayableEntity.class);
            when(player.getCentreX()).thenReturn((short) playerX);
            when(player.getCentreY()).thenReturn((short) playerY);
            when(player.isDebugMode()).thenReturn(false);
            Camera camera = new Camera() {
                @Override public short getX() { return (short) cameraX; }
                @Override public short getY() { return (short) cameraY; }
                @Override public short getMinY() { return (short) cameraY; }
                @Override public short getMaxY() { return (short) 0x540; }
                @Override public short getMaxYTarget() { return (short) 0x540; }
                @Override public short getMinX() { return (short) cameraX; }
                @Override public short getMaxX() { return (short) (cameraX + 0xA0); }
                @Override public short getWidth() { return 320; }
                @Override public short getHeight() { return 224; }
                @Override public boolean isVerticalWrapEnabled() { return false; }
            };
            Sonic3kLevelEventManager levelEvents = new Sonic3kLevelEventManager();
            levelEvents.initLevel(Sonic3kZoneIds.ZONE_FBZ, act);
            ObjectManager[] holder = new ObjectManager[1];
            AudioManager audio = mock(AudioManager.class);
            GameStateManager gameState = new GameStateManager();
            ObjectServices services = new StubObjectServices() {
                private final ObjectPlayerQuery players = new ObjectPlayerQuery(() -> player, List::of);
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public ObjectPlayerQuery playerQuery() { return players; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public AudioManager audioManager() { return audio; }
                @Override public LevelEventProvider levelEventProvider() { return levelEvents; }
                @Override public GameStateManager gameState() { return gameState; }
                @Override public int currentAct() { return act; }
            };
            ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                    null, null, GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.reset(cameraX);
            RewindRegistry rewind = new RewindRegistry();
            rewind.register(manager.rewindSnapshottable());
            rewind.register(levelEvents);
            return new FamilyHarness(manager, player, levelEvents, rewind, cameraX);
        }

        void step(int frame) {
            manager.update(cameraX, player, List.of(), frame, false);
        }
    }

    private record Harness(ObjectManager manager, PlayableEntity player,
                           Sonic3kLevelEventManager levelEvents, Sonic3kFBZEvents events,
                           FbzZoneRuntimeState runtime, SwScrlFbz scroll,
                           RewindRegistry rewind) {
        static Harness create() {
            PlayableEntity player = mock(PlayableEntity.class);
            when(player.getCentreX()).thenReturn((short) 0x2D40);
            when(player.getCentreY()).thenReturn((short) 0x690);
            when(player.isDebugMode()).thenReturn(false);

            Camera camera = new Camera() {
                @Override public short getX() { return (short) CAMERA_X; }
                @Override public short getY() { return (short) CAMERA_Y; }
                @Override public short getMinY() { return 0x3C; }
                @Override public short getMaxY() { return 0x700; }
                @Override public short getMinX() { return (short) CAMERA_X; }
                @Override public short getMaxX() { return 0x32B8; }
                @Override public short getWidth() { return 0x4000; }
                @Override public short getHeight() { return 0x1000; }
                @Override public boolean isVerticalWrapEnabled() { return false; }
            };

            Sonic3kLevelEventManager levelEvents = new Sonic3kLevelEventManager();
            levelEvents.initLevel(Sonic3kZoneIds.ZONE_FBZ, 1);
            Sonic3kFBZEvents events = levelEvents.getFbzEvents();
            FbzZoneRuntimeState runtime = new FbzZoneRuntimeState(
                    1, PlayerCharacter.SONIC_ALONE, events);
            SwScrlFbz scroll = new SwScrlFbz(() -> runtime);
            ParallaxManager parallax = new ParallaxManager() {
                @Override public com.openggf.level.scroll.ZoneScrollHandler getHandler(int zoneId) {
                    return zoneId == Sonic3kZoneIds.ZONE_FBZ ? scroll : null;
                }
            };

            ObjectManager[] holder = new ObjectManager[1];
            GameStateManager gameState = new GameStateManager();
            AudioManager audio = mock(AudioManager.class);
            ObjectServices services = new StubObjectServices() {
                private final ObjectPlayerQuery players = new ObjectPlayerQuery(() -> player, List::of);
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public ObjectPlayerQuery playerQuery() { return players; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public ParallaxManager parallaxManager() { return parallax; }
                @Override public int featureZoneId() { return Sonic3kZoneIds.ZONE_FBZ; }
                @Override public int currentAct() { return 1; }
                @Override public LevelEventProvider levelEventProvider() { return levelEvents; }
                @Override public GameStateManager gameState() { return gameState; }
                @Override public AudioManager audioManager() { return audio; }
            };
            services.zoneRuntimeRegistry().install(runtime);

            ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                    null, null, GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.reset(CAMERA_X);
            RewindRegistry rewind = new RewindRegistry();
            rewind.register(manager.rewindSnapshottable());
            rewind.register(levelEvents);
            rewind.register(services.zoneRuntimeRegistry());
            return new Harness(manager, player, levelEvents, events, runtime, scroll, rewind);
        }

        void step(int frame) {
            scroll.update(new int[224], CAMERA_X, CAMERA_Y, frame, 1);
            manager.update(CAMERA_X, player, List.of(), frame);
        }
    }
}
