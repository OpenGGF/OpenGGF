package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.FbzEndBossInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Seeded local EXIT_READY boundary, not a boss/capsule route-completion claim.
 * Real boss update requests StartNewLevel #$0800; GameLoop consumes/fades/loads.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzSandopolisTimelineHeadless {
    @Test
    void productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice() throws Exception {
        var config = SonicConfigurationService.getInstance();
        boolean oldRewind = config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        GameLoop loop = null;
        try {
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1).build();
            loop = new GameLoop(new InputHandler());
            loop.setGameplayMode(fixture.gameplayMode());
            loop.setGameMode(GameMode.LEVEL);
            var live = (LiveRewindManager) field(loop, "liveRewindManager");
            var boundaries = new ArrayList<RewindBoundary>();
            var levelLoadFrames = new ArrayList<Integer>();
            fixture.gameplayMode().setRewindBoundaryReporter(boundary -> {
                boundaries.add(boundary);
                live.markBoundary(boundary);
                if (boundary == RewindBoundary.LEVEL_LOAD)
                    levelLoadFrames.add(fixture.gameplayMode().getRewindController().currentFrame());
            });
            for (int i=0; i<12; i++) loop.step();
            var outgoing = fixture.gameplayMode().getRewindController();
            assertNotNull(outgoing);
            assertTrue(outgoing.currentFrame()>0, "source timeline must contain FBZ history");
            var boss = ObjectConstructionContext.construct(TestEnvironment.objectServices(),
                    () -> new FbzEndBossInstance(new ObjectSpawn(0x307C,0x648,FbzEndBossInstance.OBJECT_ID,0,0,false,0)));
            setField(boss, "phaseOrdinal", FbzEndBossInstance.Phase.EXIT_READY.ordinal());
            setField(boss, "nativeStarted", true);
            GameServices.level().getObjectManager().addDynamicObject(boss);
            GameServices.camera().setFrozen(true);
            GameServices.camera().setY((short)0x720);
            loop.step(); // Production object pass publishes the zone/act request.
            assertTrue(boss.isDestroyed());
            assertEquals(Sonic3kZoneIds.ZONE_SOZ, GameServices.level().getRequestedZone());
            assertEquals(0, GameServices.level().getRequestedAct());
            for (int i=0; i<300 && (GameServices.level().getCurrentZone()!=Sonic3kZoneIds.ZONE_SOZ
                    || loop.getCurrentGameMode()!=GameMode.LEVEL
                    || fixture.gameplayMode().getFadeManager().isActive()); i++) loop.step();
            assertEquals(Sonic3kZoneIds.ZONE_SOZ, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
            assertTrue(boundaries.contains(RewindBoundary.LEVEL_LOAD));
            assertFalse(boundaries.contains(RewindBoundary.SEAMLESS_LEVEL_TRANSITION));
            assertEquals(java.util.List.of(0), levelLoadFrames, "fresh StartNewLevel resets frame-zero timeline");
            for (int i=0; i<8; i++) loop.step();
            var destination = fixture.gameplayMode().getRewindController();
            assertNotNull(destination);
            destination.seekTo(0);
            assertEquals(0,destination.currentFrame());
            assertEquals(Sonic3kZoneIds.ZONE_SOZ,GameServices.level().getCurrentZone(),
                    "seeking the destination floor must never resurrect outgoing FBZ history");
            assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(FbzEndBossInstance.class).isEmpty());
            var registry = fixture.gameplayMode().getRewindRegistry();
            CompositeSnapshot before = registry.capture();
            assertTrue(before.containsKey("object-manager"));
            assertTrue(before.containsKey("zone-runtime"));
            fixture.stepIdleFrames(8);
            CompositeSnapshot expected = registry.capture();
            for (int cycle=0; cycle<2; cycle++) {
                registry.restore(before);
                assertSnapshotsEqual(before,registry.capture(),"SOZ restore cycle "+cycle);
                fixture.stepIdleFrames(8);
                assertSnapshotsEqual(expected,registry.capture(),"SOZ forward replay cycle "+cycle);
                assertEquals(Sonic3kZoneIds.ZONE_SOZ,GameServices.level().getCurrentZone());
                assertFalse(fixture.sprite().getDead());
            }
        } finally {
            if (loop != null) loop.closePresence();
            config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED,oldRewind);
        }
    }
    private static Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static void setField(Object object,String name,Object value) throws Exception {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private static void assertSnapshotsEqual(CompositeSnapshot expected, CompositeSnapshot actual,
                                             String boundary) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), boundary);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, canonicalCollections(expected.get(key)),
                    canonicalCollections(actual.get(key)));
            assertTrue(differences.isEmpty(), () -> boundary + " " + key + ": " + differences);
        }
    }

    /**
     * Compare collection contents and all gameplay fields. Mirror only the
     * documented nonsemantic exclusions in RewindSnapshotDiff: level CoW epoch,
     * object-manager render-bucket dirtiness and peak-slot telemetry. Keep newer
     * gameplay fields (HUD/timers, collision/contact state, dynamic IDs) that the
     * shared comparator's older explicit field list does not yet enumerate.
     */
    private static Object canonicalCollections(Object value) {
        if (value == null) return null;
        if (value instanceof java.util.List<?> list) {
            if (!list.isEmpty() && list.getFirst()
                    instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry) {
                var bySlot = new java.util.TreeMap<Integer, Object>();
                for (Object item : list) {
                    var entry = (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry) item;
                    assertNull(bySlot.put(entry.slotIndex(), canonicalCollections(entry)),
                            "duplicate captured slot identity");
                }
                return bySlot;
            }
            var result = new java.util.ArrayList<Object>(list.size());
            for (Object item : list) result.add(canonicalCollections(item));
            return result;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            var result = new java.util.LinkedHashMap<Object, Object>();
            map.forEach((key, item) -> result.put(canonicalCollections(key), canonicalCollections(item)));
            return result;
        }
        if (value instanceof java.util.Set<?> set) {
            var result = new java.util.LinkedHashSet<Object>();
            for (Object item : set) result.add(canonicalCollections(item));
            return result;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            var result = new java.util.ArrayList<Object>(length);
            for (int i = 0; i < length; i++) result.add(
                    canonicalCollections(java.lang.reflect.Array.get(value, i)));
            return result;
        }
        if (type.isRecord()) {
            try {
                var result = new java.util.LinkedHashMap<String, Object>();
                result.put("recordType", type.getName());
                for (var component : type.getRecordComponents()) {
                    if (value instanceof com.openggf.game.rewind.snapshot.LevelSnapshot
                            && component.getName().equals("epochAtCapture")) continue;
                    if (value instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot
                            && (component.getName().equals("bucketsDirty")
                                || component.getName().equals("peakSlotCount"))) continue;
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    result.put(component.getName(), canonicalCollections(accessor.invoke(value)));
                }
                return result;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot compare snapshot record " + type.getName(), failure);
            }
        }
        return value;
    }

}
