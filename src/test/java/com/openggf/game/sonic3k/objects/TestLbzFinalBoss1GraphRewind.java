package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.audio.AudioManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native allocation boundary, exact graph links and retirement without diagnostic owner history. */
class TestLbzFinalBoss1GraphRewind {
    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0x4300, 0x0500, 0x4600, 0x0900, 0);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void allocatedGraphRestoresBeforeDeferredNestedTurretInitialization() throws Exception {
        Harness h = new Harness();
        var boss = h.allocateBoss();
        assertEquals(7, h.manager.getActiveObjects().size() - 1);
        assertTrue(h.manager.activeObjectsOfType(LbzFinalBoss1Instance.LaserHeadChild.class).isEmpty());
        assertTrue(h.manager.activeObjectsOfType(LbzFinalBoss1Instance.GunPodChild.class).isEmpty());
        List<Link> before = h.links();
        CompositeSnapshot snapshot = h.registry.capture();
        List<CompositeSnapshot> expected = h.advance(1, 12);
        assertEquals(4, h.manager.activeObjectsOfType(LbzFinalBoss1Instance.LaserHeadChild.class).size());
        assertEquals(2, h.manager.activeObjectsOfType(LbzFinalBoss1Instance.GunPodChild.class).size());
        h.assertCompleteTurretTables();

        h.destructiveRestore(snapshot, boss);

        assertEquals(before, h.links(), "no nested slots may appear during reconstruction");
        h.assertReplay(expected, 1);
        h.assertCompleteTurretTables();
    }

    @Test
    void progressedNestedGraphRestoresExactOwnersEvenWithOverlappingBosses() throws Exception {
        Harness h = new Harness();
        var first = h.allocateBoss();
        var second = h.allocateBoss();
        h.advance(1, 4);
        List<Link> before = h.links();
        h.assertCompleteTurretTables();
        CompositeSnapshot snapshot = h.registry.capture();
        List<CompositeSnapshot> expected = h.advance(5, 12);

        h.destructiveRestore(snapshot, first);

        assertNotSame(second, h.byId(before.stream().filter(link -> link.type().equals("LbzFinalBoss1Instance"))
                .skip(1).findFirst().orElseThrow().id()));
        assertEquals(before, h.links());
        h.assertCompleteTurretTables();
        h.assertReplay(expected, 5);
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void detachRetirementReplaysAndPostRetirementRestoreDoesNotResurrectHistory() throws Exception {
        Harness h = new Harness();
        var boss = h.allocateBoss();
        h.advance(1, 1);
        var top = h.manager.activeObjectsOfType(LbzFinalBoss1Instance.TurretSegmentChild.class).stream()
                .min(Comparator.comparingInt(LbzFinalBoss1Instance.TurretSegmentChild::getDyForTest))
                .orElseThrow();
        ObjectRefId bossId = h.id(boss);
        List<ObjectRefId> retiredIds = new ArrayList<>();
        retiredIds.add(h.id(top));
        for (var head : h.manager.activeObjectsOfType(LbzFinalBoss1Instance.LaserHeadChild.class)) {
            if (field(head, "segment") == top) retiredIds.add(h.id(head));
        }
        assertEquals(3, retiredIds.size());
        CompositeSnapshot beforeDetach = h.registry.capture();
        h.hitTop(boss);
        List<CompositeSnapshot> expected = h.advance(2, 24);
        h.assertAbsent(retiredIds);
        h.destructiveRestore(beforeDetach, boss);
        h.hitTop((LbzFinalBoss1Instance) h.byId(bossId));
        h.assertReplay(expected, 2);
        h.assertAbsent(retiredIds);
        h.assertTurretLinks();

        CompositeSnapshot afterRetirement = assertDoesNotThrow(h.registry::capture);
        List<Link> remaining = h.links();
        List<CompositeSnapshot> later = h.advance(26, 12);
        h.destructiveRestore(afterRetirement, h.byId(bossId));
        assertEquals(remaining, h.links());
        h.assertAbsent(retiredIds);
        h.assertReplay(later, 26);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void nestedAllocationFailurePrefixesStayMissingAfterRestore(int freeSlots) throws Exception {
        Harness h = new Harness();
        var boss = h.allocateBoss();
        // A bare reserveAllButNFreeSlots bit is not a rewindable occupant.
        // Real inert objects preserve both capacity and native slot order on restore.
        int fillers = h.objects.objectSlotLayout().dynamicSlotCount()
                - h.manager.getActiveObjects().size() - freeSlots;
        for (int index = 0; index < fillers; index++) {
            var filler = h.manager.createDynamicObject(() -> new PlaceholderObjectInstance(
                    new ObjectSpawn(0x4480, 0x0680, 0, 0, 0, false, 0), "slot pressure"));
            assertFalse(filler.isDestroyed());
        }
        CompositeSnapshot beforeNested = h.registry.capture();
        List<CompositeSnapshot> expected = h.advance(1, 4);
        assertEquals(Math.min(4, freeSlots),
                h.manager.activeObjectsOfType(LbzFinalBoss1Instance.LaserHeadChild.class).size());
        assertEquals(Math.max(0, freeSlots - 4),
                h.manager.activeObjectsOfType(LbzFinalBoss1Instance.GunPodChild.class).size());
        List<Link> progressed = h.links();
        CompositeSnapshot afterNested = h.registry.capture();
        h.destructiveRestore(beforeNested, boss);
        h.assertReplay(expected, 1);
        assertEquals(progressed, h.links());
        h.destructiveRestore(afterNested, h.manager.activeObjectsOfType(LbzFinalBoss1Instance.class).getFirst());
        assertEquals(progressed, h.links());
        List<CompositeSnapshot> later = h.advance(5, 4);
        assertEquals(progressed.stream().map(Link::id).toList(), h.links().stream().map(Link::id).toList(),
                "failed suffix allocations must not retry on a later segment tick");
        h.destructiveRestore(afterNested, h.manager.activeObjectsOfType(LbzFinalBoss1Instance.class).getFirst());
        h.assertReplay(later, 5);
    }

    private record Link(String type, ObjectRefId id, int slot, ObjectRefId boss, ObjectRefId segment) { }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static final class Harness {
        final Camera camera = new Camera();
        final RewindRegistry registry = new RewindRegistry();
        final AudioManager audio = org.mockito.Mockito.mock(AudioManager.class);
        final StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public Camera camera() { return camera; }
            @Override public AudioManager audioManager() { return audio; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        };
        final Sonic3kObjectRegistry objects = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return Sonic3kZoneIds.ZONE_LBZ; }
            @Override protected com.openggf.level.Level currentLevel() { return null; }
        };
        final ObjectManager manager = new ObjectManager(List.of(), objects, 0,
                null, null, GraphicsManager.getInstance(), camera, services);

        Harness() {
            camera.setX((short) 0x4400);
            camera.setY((short) 0x0600);
            services.zoneRuntimeRegistry().install(new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_AND_TAILS));
            manager.reset(camera.getX());
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            registry.register(manager.rewindSnapshottable());
            registry.register(services.rng());
            registry.register(camera);
        }

        LbzFinalBoss1Instance allocateBoss() {
            var boss = (LbzFinalBoss1Instance) manager.createDynamicObject(() ->
                    objects.create(new ObjectSpawn(0x44A0, 0x0780, 0xCA, 0, 0, false, 0)));
            boss.initializeOnAllocationBeforeParentRelease();
            // Both seeded head steps advance away from a firing notch. Keep this
            // graph check independent of the separately owned muzzle/head lifetime.
            boss.setRenderXFlipForTest(true);
            return boss;
        }

        void hitTop(LbzFinalBoss1Instance boss) {
            boss.forceHitCountForTest(6);
            boss.onPlayerAttack(null, null);
        }

        List<CompositeSnapshot> advance(int firstFrame, int count) {
            List<CompositeSnapshot> result = new ArrayList<>();
            for (int frame = firstFrame; frame < firstFrame + count; frame++) {
                manager.update(camera.getX(), null, List.of(), frame, false);
                result.add(registry.capture());
            }
            return result;
        }

        void assertReplay(List<CompositeSnapshot> expected, int firstFrame) {
            List<CompositeSnapshot> actual = advance(firstFrame, expected.size());
            for (int index = 0; index < expected.size(); index++) {
                for (String key : expected.get(index).entries().keySet()) {
                    assertEquals(List.of(), RewindSnapshotDiff.diffKey(key,
                            expected.get(index).get(key), actual.get(index).get(key)),
                            "frame " + (firstFrame + index) + " " + key);
                }
            }
        }

        void destructiveRestore(CompositeSnapshot snapshot, ObjectInstance oldBoss) {
            for (ObjectInstance object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
            var divergent = manager.createDynamicObject(() -> new LbzFinalBoss1Instance(
                    new ObjectSpawn(0x44B0, 0x0790, 0xCA, 0, 0, false, 0)));
            divergent.forceHitCountForTest(1);
            registry.restore(snapshot);
            assertFalse(manager.getActiveObjects().contains(divergent));
            assertFalse(manager.getActiveObjects().contains(oldBoss), "restore must use new shells");
        }

        List<Link> links() throws Exception {
            List<Link> result = new ArrayList<>();
            for (ObjectInstance object : manager.getActiveObjects()) {
                result.add(new Link(object.getClass().getSimpleName(), id(object),
                        ((AbstractObjectInstance) object).getSlotIndex(),
                        ref(field(object, "boss")), ref(field(object, "segment"))));
            }
            return result.stream().sorted(Comparator.comparingInt(Link::slot)).toList();
        }

        void assertCompleteTurretTables() throws Exception {
            assertTurretLinks();
            for (var boss : manager.activeObjectsOfType(LbzFinalBoss1Instance.class)) {
                var segments = new ArrayList<LbzFinalBoss1Instance.TurretSegmentChild>();
                for (var segment : manager.activeObjectsOfType(LbzFinalBoss1Instance.TurretSegmentChild.class)) {
                    if (field(segment, "boss") == boss) segments.add(segment);
                }
                segments.sort(Comparator.comparingInt(LbzFinalBoss1Instance.TurretSegmentChild::getDyForTest));
                assertEquals(List.of(8, 0x30, 0x5C), segments.stream()
                        .map(LbzFinalBoss1Instance.TurretSegmentChild::getDyForTest).toList());
                for (int index = 0; index < segments.size(); index++) {
                    var segment = segments.get(index);
                    assertTrue(segment.getSlotIndex() > boss.getSlotIndex());
                    int heads = 0;
                    int guns = 0;
                    for (var child : manager.activeObjectsOfType(LbzFinalBoss1Instance.BossChild.class)) {
                        if (field(child, "segment") != segment) continue;
                        assertSame(boss, field(child, "boss"));
                        assertTrue(child.getSlotIndex() > segment.getSlotIndex(),
                                "nested allocation uses the segment's native FindNextFreeObj owner");
                        if (child instanceof LbzFinalBoss1Instance.LaserHeadChild) heads++;
                        if (child instanceof LbzFinalBoss1Instance.GunPodChild) guns++;
                    }
                    assertEquals(index < 2 ? 2 : 0, heads, "exact top/middle head pairs");
                    assertEquals(index == 2 ? 2 : 0, guns, "exact bottom gun pair");
                }
            }
        }

        void assertTurretLinks() throws Exception {
            for (var child : manager.activeObjectsOfType(LbzFinalBoss1Instance.BossChild.class)) {
                Object owner = field(child, "boss");
                assertTrue(manager.getActiveObjects().contains(owner), "boss link must be in the restored graph");
                Object segment = field(child, "segment");
                if (segment != null) {
                    assertTrue(manager.getActiveObjects().contains(segment));
                    assertSame(owner, field(segment, "boss"), "nested child and segment must use the exact same boss");
                }
            }
        }

        void assertAbsent(List<ObjectRefId> ids) {
            assertTrue(manager.getActiveObjects().stream().noneMatch(object -> ids.contains(id(object))),
                    "retired segment and its exact heads must stay absent");
        }

        ObjectInstance byId(ObjectRefId id) {
            return manager.getActiveObjects().stream().filter(object -> id(object).equals(id)).findFirst().orElseThrow();
        }

        ObjectRefId ref(Object object) { return object == null ? null : id((ObjectInstance) object); }
        ObjectRefId id(ObjectInstance object) {
            var id = manager.captureIdentityContext().requireIdentityTable().idFor(object);
            assertNotNull(id, "every asserted graph link must name a registered identity");
            return id;
        }
    }
}
