package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.objects.badniks.Sonic1BombBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BombFuseInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CaterkillerBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CaterkillerBodyInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1OrbinautBadnikInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.TrigLookupTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS1BadnikChildGraphRewind {

    private static final String ORB_SPIKE_CLASS =
            "com.openggf.game.sonic1.objects.badniks.Sonic1OrbinautBadnikInstance"
                    + "$OrbSpikeObjectInstance";
    private static final String BOMB_SHRAPNEL_CLASS =
            "com.openggf.game.sonic1.objects.badniks.Sonic1BombShrapnelInstance";

    private static final ObjectSpawn BOMB_SPAWN =
            new ObjectSpawn(0x0100, 0x0120, Sonic1ObjectIds.BOMB, 0, 0, false, 10);
    private static final ObjectSpawn CATERKILLER_SPAWN =
            new ObjectSpawn(0x0180, 0x0140, Sonic1ObjectIds.CATERKILLER, 0, 0, false, 20);
    private static final ObjectSpawn ORBINAUT_SPAWN =
            new ObjectSpawn(0x0200, 0x0160, Sonic1ObjectIds.ORBINAUT, 0, 0, false, 30);

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void twoRoutineOrbinautFamiliesKeepTheirCapturedParentsWhenAnotherOrbinautIsCloser() {
        // Orb_CircleSpikeball uses CalcSine >> 4, so angle zero has a sixteen-pixel radius.
        int orbitRadius = TrigLookupTable.cosHex(0) >> 4;
        ObjectSpawn leftSpawn = new ObjectSpawn(0x80, 0x70, Sonic1ObjectIds.ORBINAUT, 0, 0, false, 0x70, 31);
        ObjectSpawn rightSpawn = new ObjectSpawn(
                leftSpawn.x() + orbitRadius, leftSpawn.y(), Sonic1ObjectIds.ORBINAUT,
                0, 0, false, leftSpawn.y(), leftSpawn.layoutIndex() + 1);
        ObjectManager manager = Harness.create(List.of(leftSpawn, rightSpawn)).objectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        // Both parents create their satellites from updateMovement via owned spawnFreeChild.
        // A higher-slot child may execute in that pass; the second pass also covers lower slots.
        manager.update(0, null, List.of(), 0);
        manager.update(0, null, List.of(), 1);

        List<Sonic1OrbinautBadnikInstance> parents = liveObjects(manager, Sonic1OrbinautBadnikInstance.class);
        assertEquals(2, parents.size(), "fixture must have two normally placed Orbinauts");
        Sonic1OrbinautBadnikInstance left = parents.stream()
                .filter(parent -> parent.getX() == leftSpawn.x()).findFirst().orElseThrow();
        Sonic1OrbinautBadnikInstance right = parents.stream()
                .filter(parent -> parent.getX() == rightSpawn.x()).findFirst().orElseThrow();
        ObjectInstance rightmostLeftSpike = readObjectList(left, "spikes").getFirst();
        assertOrbitPositionUsesPreviousAngle(rightmostLeftSpike, left);
        assertEquals(8, liveObjectsByClassName(manager, ORB_SPIKE_CLASS).size());

        Map<ObjectRefId, ObjectRefId> expectedParents = new LinkedHashMap<>();
        for (Sonic1OrbinautBadnikInstance parent : List.of(left, right)) {
            assertEquals(4, readObjectList(parent, "spikes").size());
            for (ObjectInstance spike : readObjectList(parent, "spikes")) {
                assertSame(parent, readObject(spike, "parent"), "normal routine birth must use its real parent");
                assertFalse(readBoolean(spike, "launched"), "null player must leave this fixture orbiting");
                expectedParents.put(objectId(manager, spike), objectId(manager, parent));
            }
        }
        var adapter = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshot = adapter.capture();
        assertCapturedOrbinautFamilies(snapshot, expectedParents);
        var capturedRightmostLeftSpike = snapshot.dynamicObjects().stream()
                .filter(entry -> objectId(manager, rightmostLeftSpike).equals(entry.objectId()))
                .findFirst().orElseThrow();
        assertEquals(rightmostLeftSpike.getX(), capturedRightmostLeftSpike.spawn().x(),
                "OrbSpike.getSpawn must capture the evaluated orbit x, not its constructor center");
        assertEquals(rightmostLeftSpike.getY(), capturedRightmostLeftSpike.spawn().y(),
                "OrbSpike.getSpawn must capture the evaluated orbit y, not its constructor center");
        assertTrue(distanceSquared(right, capturedRightmostLeftSpike.spawn())
                        < distanceSquared(left, capturedRightmostLeftSpike.spawn()),
                "the snapshot spawn passed to the hook must be strictly closer to the other Orbinaut");

        for (int restore = 0; restore < 2; restore++) {
            Map<ObjectRefId, ObjectInstance> previous = new LinkedHashMap<>();
            for (var family : expectedParents.entrySet()) {
                previous.put(family.getKey(), manager.objectQuery().resolve(family.getKey()).orElseThrow());
                previous.put(family.getValue(), manager.objectQuery().resolve(family.getValue()).orElseThrow());
            }
            adapter.restore(snapshot);
            assertEquals(8, liveObjectsByClassName(manager, ORB_SPIKE_CLASS).size(),
                    "fresh restore must retain the eight captured satellite ids");
            for (var family : expectedParents.entrySet()) {
                ObjectInstance spike = manager.objectQuery().resolve(family.getKey()).orElseThrow();
                Sonic1OrbinautBadnikInstance parent = (Sonic1OrbinautBadnikInstance)
                        manager.objectQuery().resolve(family.getValue()).orElseThrow();
                assertNotSame(previous.get(family.getKey()), spike,
                        "the satellite must be freshly recreated, not retained from the source graph");
                assertNotSame(previous.get(family.getValue()), parent,
                        "the Orbinaut parent must be freshly reconstructed");
                assertSame(parent, readObject(spike, "parent"),
                        "satellite must use its captured family parent even when another Orbinaut is closer");
                assertEquals(1, identityFrequency(readObjectList(parent, "spikes"), spike),
                        "each restored satellite id must occur once in its own parent's list");
                assertOrbitPositionUsesPreviousAngle(spike, parent);
            }
            for (ObjectRefId parentId : expectedParents.values().stream().distinct().toList()) {
                Sonic1OrbinautBadnikInstance parent = (Sonic1OrbinautBadnikInstance)
                        manager.objectQuery().resolve(parentId).orElseThrow();
                List<ObjectInstance> spikes = readObjectList(parent, "spikes");
                assertEquals(4, spikes.size(), "each restored parent must retain only its four satellites");
                for (ObjectInstance spike : spikes) {
                    assertEquals(parentId, expectedParents.get(objectId(manager, spike)),
                            "a restored parent list must not borrow another family's captured satellite");
                }
            }
            if (restore == 0) {
                manager.update(0, null, List.of(), 2);
                snapshot = adapter.capture();
                assertCapturedOrbinautFamilies(snapshot, expectedParents);
            }
        }
    }


    @Test
    void orbinautKnownAbsentParentDropsSpikeInsteadOfBorrowingNearestFamily() {
        OrbinautFamilyBoundary boundary = orbinautFamilyBoundary();
        ObjectRefId ownerId = boundary.entry().constructionFamily().parentId();
        ObjectRefId absentId = new ObjectRefId(ownerId.slotIndex(), ownerId.generation() + 1,
                ownerId.spawnId(), ownerId.dynamicId(), ownerId.kind());
        assertTrue(boundary.manager().objectQuery().resolve(absentId).isEmpty());
        assertOrbinautBoundaryDropped(boundary,
                new ObjectManagerSnapshot.ConstructionFamily(true, absentId), "absent parent");
    }

    @Test
    void orbinautKnownWrongTypeParentDropsSpikeInsteadOfBorrowingNearestFamily() {
        OrbinautFamilyBoundary boundary = orbinautFamilyBoundary();
        ObjectInstance wrongType = boundary.manager().objectQuery()
                .resolve(boundary.wrongTypeId()).orElseThrow();
        assertEquals(ORB_SPIKE_CLASS, wrongType.getClass().getName(),
                "wrong-type parent identity must resolve to a real managed spike");
        assertOrbinautBoundaryDropped(boundary,
                new ObjectManagerSnapshot.ConstructionFamily(true, boundary.wrongTypeId()), "wrong-type parent");
    }

    @Test
    void orbinautKnownExplicitNoParentDropsSpikeInsteadOfBorrowingNearestFamily() {
        assertOrbinautBoundaryDropped(orbinautFamilyBoundary(),
                new ObjectManagerSnapshot.ConstructionFamily(true, null), "explicit no-parent");
    }

    @Test
    void orbinautLegacyFamilyAbsenceKeepsNearestParentCompatibility() {
        assertOrbinautBoundaryUsesNearest(orbinautFamilyBoundary(), null);
    }

    @Test
    void orbinautUnknownFamilyKeepsNearestParentCompatibility() {
        assertOrbinautBoundaryUsesNearest(orbinautFamilyBoundary(),
                new ObjectManagerSnapshot.ConstructionFamily(false, null));
    }

    private static void assertOrbinautBoundaryDropped(OrbinautFamilyBoundary boundary,
            ObjectManagerSnapshot.ConstructionFamily family, String condition) {
        // This one metadata edit is an explicit reconstruction boundary input.
        // The source children and their original known families came from ordinary updates.
        boundary.manager().rewindSnapshottable().restore(orbinautSnapshotWithFamily(boundary, family));
        assertTrue(boundary.manager().objectQuery().resolve(boundary.entry().objectId()).isEmpty(),
                "known Orbinaut family must drop rather than borrow nearest for " + condition);
        assertEquals(7, liveObjectsByClassName(boundary.manager(), ORB_SPIKE_CLASS).size(),
                "dropping one invalid family must preserve the other seven captured spikes");
        assertFreshBoundaryParents(boundary);
        for (var parent : liveObjects(boundary.manager(), Sonic1OrbinautBadnikInstance.class)) {
            for (ObjectInstance spike : readObjectList(parent, "spikes")) {
                assertNotEquals(boundary.entry().objectId(), objectId(boundary.manager(), spike),
                        "no parent inventory may retain the deliberately dropped captured spike");
            }
        }
    }

    private static void assertOrbinautBoundaryUsesNearest(OrbinautFamilyBoundary boundary,
            ObjectManagerSnapshot.ConstructionFamily family) {
        boundary.manager().rewindSnapshottable().restore(orbinautSnapshotWithFamily(boundary, family));
        ObjectInstance restored = boundary.manager().objectQuery().resolve(boundary.entry().objectId()).orElseThrow();
        ObjectInstance nearest = boundary.manager().objectQuery().resolve(boundary.nearestId()).orElseThrow();
        assertNotSame(boundary.sourceSpike(), restored,
                "legacy/unknown control must execute fresh spike reconstruction");
        assertSame(nearest, readObject(restored, "parent"),
                "legacy/unknown Orbinaut family must retain nearest-parent compatibility");
        assertEquals(1, identityFrequency(readObjectList(nearest, "spikes"), restored));
        assertEquals(8, liveObjectsByClassName(boundary.manager(), ORB_SPIKE_CLASS).size());
        assertFreshBoundaryParents(boundary);
    }

    private static void assertFreshBoundaryParents(OrbinautFamilyBoundary boundary) {
        assertNotSame(boundary.sourceOwner(), boundary.manager().objectQuery()
                .resolve(boundary.ownerId()).orElseThrow(), "source owner must be freshly reconstructed");
        assertNotSame(boundary.sourceNearest(), boundary.manager().objectQuery()
                .resolve(boundary.nearestId()).orElseThrow(), "nearest unrelated parent must remain live and fresh");
    }

    private static OrbinautFamilyBoundary orbinautFamilyBoundary() {
        int orbitRadius = TrigLookupTable.cosHex(0) >> 4;
        ObjectSpawn leftSpawn = new ObjectSpawn(0x80, 0x70, Sonic1ObjectIds.ORBINAUT, 0, 0, false, 0x70, 31);
        ObjectSpawn rightSpawn = new ObjectSpawn(leftSpawn.x() + orbitRadius, leftSpawn.y(),
                Sonic1ObjectIds.ORBINAUT, 0, 0, false, leftSpawn.y(), leftSpawn.layoutIndex() + 1);
        ObjectManager manager = Harness.create(List.of(leftSpawn, rightSpawn)).objectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        manager.update(0, null, List.of(), 0);
        manager.update(0, null, List.of(), 1);
        Sonic1OrbinautBadnikInstance left = liveObjects(manager, Sonic1OrbinautBadnikInstance.class).stream()
                .filter(parent -> parent.getX() == leftSpawn.x()).findFirst().orElseThrow();
        Sonic1OrbinautBadnikInstance right = liveObjects(manager, Sonic1OrbinautBadnikInstance.class).stream()
                .filter(parent -> parent.getX() == rightSpawn.x()).findFirst().orElseThrow();
        ObjectInstance target = readObjectList(left, "spikes").getFirst();
        ObjectInstance wrongType = readObjectList(right, "spikes").getFirst();
        assertOrbitPositionUsesPreviousAngle(target, left);
        Map<ObjectRefId, ObjectRefId> expected = new LinkedHashMap<>();
        for (var parent : List.of(left, right)) {
            assertEquals(4, readObjectList(parent, "spikes").size());
            for (ObjectInstance spike : readObjectList(parent, "spikes")) {
                assertSame(parent, readObject(spike, "parent"), "source must use ordinary owned birth");
                assertFalse(readBoolean(spike, "launched"));
                expected.put(objectId(manager, spike), objectId(manager, parent));
            }
        }
        ObjectManagerSnapshot snapshot = manager.rewindSnapshottable().capture();
        assertCapturedOrbinautFamilies(snapshot, expected);
        var entry = snapshot.dynamicObjects().stream()
                .filter(candidate -> objectId(manager, target).equals(candidate.objectId())).findFirst().orElseThrow();
        assertEquals(target.getX(), entry.spawn().x());
        assertEquals(target.getY(), entry.spawn().y());
        assertTrue(distanceSquared(right, entry.spawn()) < distanceSquared(left, entry.spawn()),
                "legacy fallback must select the other parent using the actual captured orbit spawn");
        return new OrbinautFamilyBoundary(manager, snapshot, entry, objectId(manager, left),
                objectId(manager, right), objectId(manager, wrongType), target, left, right);
    }

    private record OrbinautFamilyBoundary(ObjectManager manager, ObjectManagerSnapshot snapshot,
            ObjectManagerSnapshot.DynamicObjectEntry entry, ObjectRefId ownerId, ObjectRefId nearestId,
            ObjectRefId wrongTypeId, ObjectInstance sourceSpike, ObjectInstance sourceOwner,
            ObjectInstance sourceNearest) { }

    private static ObjectManagerSnapshot orbinautSnapshotWithFamily(OrbinautFamilyBoundary boundary,
            ObjectManagerSnapshot.ConstructionFamily family) {
        ObjectManagerSnapshot s = boundary.snapshot();
        var entries = s.dynamicObjects().stream().map(entry -> {
            if (!boundary.entry().objectId().equals(entry.objectId())) return entry;
            return new ObjectManagerSnapshot.DynamicObjectEntry(entry.className(), entry.spawn(), entry.slotIndex(),
                    entry.state(), entry.playerOwner(), entry.objectId(), entry.ownerModId(),
                    entry.rewindableAuxiliary(), entry.playerOwnerId(), family);
        }).toList();
        return new ObjectManagerSnapshot(s.usedSlotsBits(), s.slots(), s.frameCounter(), s.vblaCounter(),
                s.currentExecSlot(), s.peakSlotCount(), s.bucketsDirty(), s.childSpawns(), entries,
                s.placement(), s.solidContactRiding(), s.solidContactState(), s.planeSwitchers(),
                s.touchResponseOverlap(), s.dynamicObjectIdCounter(), s.collisionResponseState(),
                s.sineCosineInputFraction());
    }

    private static void assertCapturedOrbinautFamilies(
            ObjectManagerSnapshot snapshot, Map<ObjectRefId, ObjectRefId> expectedParents) {
        for (var family : expectedParents.entrySet()) {
            var child = snapshot.dynamicObjects().stream()
                    .filter(entry -> family.getKey().equals(entry.objectId())).findFirst().orElseThrow();
            assertEquals(ORB_SPIKE_CLASS, child.className());
            assertNotNull(child.constructionFamily(), "owned routine satellite birth must record family metadata");
            assertTrue(child.constructionFamily().known());
            assertEquals(family.getValue(), child.constructionFamily().parentId(),
                    "captured satellite family id must already name its normal source owner");
        }
    }

    private static void assertOrbitPositionUsesPreviousAngle(
            ObjectInstance spike, Sonic1OrbinautBadnikInstance parent) {
        // update() computes x/y from the old angle, THEN increments angle by the parent's step.
        int positionAngle = (readInt(spike, "angle") - readInt(parent, "angleStep")) & 0xFF;
        assertEquals(parent.getX() + (TrigLookupTable.cosHex(positionAngle) >> 4), spike.getX(),
                "orbit x must come from the angle used before its update increment");
        assertEquals(parent.getY() + (TrigLookupTable.sinHex(positionAngle) >> 4), spike.getY(),
                "orbit y must come from the angle used before its update increment");
    }

    private static long distanceSquared(ObjectInstance parent, ObjectSpawn capturedSpawn) {
        long dx = parent.getX() - capturedSpawn.x();
        long dy = parent.getY() - capturedSpawn.y();
        return dx * dx + dy * dy;
    }

    @Test
    void s1BadnikChildGraphRestoresParentsCollectionsAndScalars() throws Exception {
        Harness harness = Harness.createWithParents();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);

        BadnikChildGraph before = BadnikChildGraph.spawnRepresentativeFamily(objectManager);
        Map<Class<?>, Integer> beforeCounts = before.counts();
        Map<String, ObjectRefId> beforeIds = before.ids(objectManager);
        Map<String, Object> beforeScalars = before.scalarSnapshot();

        RewindRegistry registry = new RewindRegistry();
        registry.register(objectManager.rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();

        before.removeDynamicChildren(objectManager);
        BadnikChildGraph replacement = BadnikChildGraph.spawnReplacementFamily(objectManager);
        assertNotEquals(beforeIds.get("fuse"), objectId(objectManager, replacement.fuse()),
                "post-snapshot replacement fuse must not alias the captured fuse identity");

        registry.restore(snapshot);

        BadnikChildGraph restored = BadnikChildGraph.fromLiveObjects(objectManager);
        assertEquals(beforeCounts, restored.counts(),
                "restore must not drop or duplicate S1 badnik child graph objects");
        assertEquals(beforeIds, restored.ids(objectManager),
                "restore must preserve captured S1 badnik child graph identities");
        assertEquals(beforeScalars, restored.scalarSnapshot(),
                "compact restore must round-trip meaningful S1 badnik child scalar state");

        assertRestoredObjectsAreFresh(before, restored);
        assertRestoredChildrenPointAtLiveParents(before, restored, replacement);
        assertCaterkillerParentListAndChain(restored);
        assertOrbinautSpikeList(restored, replacement);

        assertCaterkillerBodiesReadImmediatePredecessorState(restored);
        assertBombFuseCanExpireThroughRestoredParent(objectManager, restored.fuse());
        assertOrbinautUnloadManagesRestoredSpikes(objectManager, restored.orbinaut());
    }

    @Test
    void genericRecreateDropsS1BadnikChildrenWhenRequiredLiveParentIsMissing() throws Exception {
        Harness harness = Harness.createWithoutParents();
        ObjectManager objectManager = harness.objectManager();

        assertNull(genericRecreate(objectManager, Sonic1BombFuseInstance.class.getName(),
                        new ObjectSpawn(0x0100, 0x0120, Sonic1ObjectIds.BOMB, 4, 0, false, 0)),
                "Bomb fuse generic recreate must drop when no live Bomb parent exists");
        assertNull(genericRecreate(objectManager, Sonic1CaterkillerBodyInstance.class.getName(),
                        new ObjectSpawn(0x018C, 0x0140, Sonic1ObjectIds.CATERKILLER, 0, 0, false, 0)),
                "Caterkiller body generic recreate must drop when no live Caterkiller head exists");
        assertNull(genericRecreate(objectManager, ORB_SPIKE_CLASS,
                        new ObjectSpawn(0x0210, 0x0160, Sonic1ObjectIds.ORBINAUT, 0, 0, false, 0)),
                "Orbinaut spike generic recreate must drop when no live Orbinaut parent exists");
    }

    @Test
    void s1BadnikChildrenUseRewindRecreatableWithoutExplicitS1DynamicCodecs() throws Exception {
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic1BombFuseInstance.class));
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic1CaterkillerBodyInstance.class));
        assertTrue(RewindRecreatable.class.isAssignableFrom(Class.forName(ORB_SPIKE_CLASS)));

    }

    @Test
    void s1BadnikGraphParentsUseRewindRecreatableWithoutExplicitS1DynamicCodecs() {
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic1CaterkillerBadnikInstance.class));
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic1OrbinautBadnikInstance.class));

    }

    private static ObjectInstance genericRecreate(
            ObjectManager objectManager,
            String className,
            ObjectSpawn spawn) {
        return ObjectRewindDynamicCodecs.genericRecreate(
                new ObjectManagerSnapshot.DynamicObjectEntry(className, spawn, 0, emptyState()),
                new DynamicObjectRecreateContext(objectManager));
    }

    private static PerObjectRewindSnapshot emptyState() {
        return new PerObjectRewindSnapshot(
                false, false, false, 0, 0, 0, 0, false, 0,
                false, false, 0, -1, null, null, null);
    }

    private static void assertRestoredObjectsAreFresh(BadnikChildGraph before, BadnikChildGraph restored) {
        assertNotSame(before.bomb(), restored.bomb(),
                "restore must recreate the active Bomb parent when in-place restore is disabled");
        assertNotSame(before.caterkiller(), restored.caterkiller(),
                "restore must recreate the active Caterkiller parent when in-place restore is disabled");
        assertNotSame(before.orbinaut(), restored.orbinaut(),
                "restore must recreate the active Orbinaut parent when in-place restore is disabled");
        assertNotSame(before.fuse(), restored.fuse(), "restore must recreate removed Bomb fuse");
        for (int i = 0; i < before.bodies().size(); i++) {
            assertNotSame(before.bodies().get(i), restored.bodies().get(i),
                    "restore must recreate removed Caterkiller body " + i);
        }
        for (int i = 0; i < before.spikes().size(); i++) {
            assertNotSame(before.spikes().get(i), restored.spikes().get(i),
                    "restore must recreate removed Orbinaut spike " + i);
        }
    }

    private static void assertRestoredChildrenPointAtLiveParents(
            BadnikChildGraph before,
            BadnikChildGraph restored,
            BadnikChildGraph replacement) {
        assertSame(restored.bomb(), readObject(restored.fuse(), "parent"),
                "restored fuse must point at restored live Bomb parent");
        assertNotSame(before.bomb(), readObject(restored.fuse(), "parent"),
                "restored fuse must not retain stale pre-restore Bomb parent");
        assertNotSame(replacement.bomb(), readObject(restored.fuse(), "parent"),
                "restored fuse must not point at divergent replacement Bomb parent");

        for (Sonic1CaterkillerBodyInstance body : restored.bodies()) {
            assertSame(restored.caterkiller(), readObject(body, "head"),
                    "restored Caterkiller body must point at restored head");
            assertNotSame(before.caterkiller(), readObject(body, "head"),
                    "restored Caterkiller body must not retain stale pre-restore head");
        }

        for (ObjectInstance spike : restored.spikes()) {
            assertSame(restored.orbinaut(), readObject(spike, "parent"),
                    "restored Orbinaut spike must point at restored parent");
            assertNotSame(before.orbinaut(), readObject(spike, "parent"),
                    "restored Orbinaut spike must not retain stale pre-restore parent");
            assertNotSame(replacement.orbinaut(), readObject(spike, "parent"),
                    "restored Orbinaut spike must not point at divergent replacement parent");
        }
    }

    private static void assertCaterkillerParentListAndChain(BadnikChildGraph restored) {
        List<?> parentBodies = readList(restored.caterkiller(), "bodySegments");
        assertEquals(restored.bodies().size(), parentBodies.size(),
                "restored Caterkiller head bodySegments must contain only restored bodies");
        for (Sonic1CaterkillerBodyInstance body : restored.bodies()) {
            assertEquals(1, identityFrequency(parentBodies, body),
                    "restored Caterkiller body must be present exactly once in head bodySegments");
        }
        assertSame(restored.caterkiller(), readObject(restored.bodies().get(0), "parentState"),
                "first body segment must follow the head");
        assertSame(restored.bodies().get(0), readObject(restored.bodies().get(1), "parentState"),
                "second body segment must follow the first restored body");
        assertSame(restored.bodies().get(1), readObject(restored.bodies().get(2), "parentState"),
                "third body segment must follow the second restored body");
    }

    private static void assertOrbinautSpikeList(BadnikChildGraph restored, BadnikChildGraph replacement) {
        List<?> parentSpikes = readList(restored.orbinaut(), "spikes");
        assertEquals(restored.spikes().size(), parentSpikes.size(),
                "restored Orbinaut parent spikes list must contain restored spikes only");
        for (ObjectInstance spike : restored.spikes()) {
            assertEquals(1, identityFrequency(parentSpikes, spike),
                    "restored Orbinaut spike must be present exactly once in parent spikes list");
        }
        for (ObjectInstance spike : replacement.spikes()) {
            assertEquals(0, identityFrequency(parentSpikes, spike),
                    "restored Orbinaut spikes list must not retain replacement spikes");
        }
    }

    private static void assertCaterkillerBodiesReadImmediatePredecessorState(BadnikChildGraph restored) {
        Sonic1CaterkillerBodyInstance body0 = restored.bodies().get(0);
        Sonic1CaterkillerBodyInstance body1 = restored.bodies().get(1);
        Sonic1CaterkillerBodyInstance body2 = restored.bodies().get(2);

        writeInt(restored.caterkiller(), "secondaryState", 0);
        writeInt(body0, "secondaryState", 1);
        writeInt(body0, "inertia", 0);
        writeInt(body0, "xVelocity", 0x100);
        writeInt(body0, "animControl", 0x34);
        body0.writeRingBuffer(readInt(body1, "ringBufferIndex"), 3);
        int body1X = body1.getX();
        int body1Y = body1.getY();

        body1.update(100, null);

        assertEquals(body1X + 1, body1.getX(),
                "body1 must follow body0 velocity, not the head");
        assertEquals(body1Y + 3, body1.getY(),
                "body1 must consume body0 ring-buffer delta, not the head");
        assertEquals(1, readInt(body1, "secondaryState"),
                "body1 must copy secondary state from body0");

        writeInt(body1, "secondaryState", 1);
        writeInt(body1, "inertia", 0);
        writeInt(body1, "xVelocity", 0x100);
        body1.writeRingBuffer(readInt(body2, "ringBufferIndex"), 4);
        int body2X = body2.getX();
        int body2Y = body2.getY();

        body2.update(101, null);

        assertEquals(body2X + 1, body2.getX(),
                "body2 must follow body1 velocity");
        assertEquals(body2Y + 4, body2.getY(),
                "body2 must consume body1 ring-buffer delta");
    }

    private static void assertBombFuseCanExpireThroughRestoredParent(
            ObjectManager objectManager,
            Sonic1BombFuseInstance fuse) {
        int shrapnelBefore = liveObjectsByClassName(objectManager, BOMB_SHRAPNEL_CLASS).size();
        writeInt(fuse, "timer", 0);

        fuse.update(200, null);

        assertTrue(fuse.isDestroyed(), "expired restored fuse must destroy itself");
        assertEquals(shrapnelBefore + 4, liveObjectsByClassName(objectManager, BOMB_SHRAPNEL_CLASS).size(),
                "expired restored fuse must spawn shrapnel through its restored Bomb parent");
    }

    private static void assertOrbinautUnloadManagesRestoredSpikes(
            ObjectManager objectManager,
            Sonic1OrbinautBadnikInstance orbinaut) {
        assertEquals(4, liveObjectsByClassName(objectManager, ORB_SPIKE_CLASS).size(),
                "restored Orbinaut spikes must be live before unload");

        orbinaut.onUnload();

        assertEquals(0, liveObjectsByClassName(objectManager, ORB_SPIKE_CLASS).size(),
                "Orbinaut unload must remove restored spikes from ObjectManager");
        assertEquals(0, readList(orbinaut, "spikes").size(),
                "Orbinaut unload must clear restored parent spike list");
        assertEquals(0, readInt(orbinaut, "activeSpikes"),
                "Orbinaut unload must clear activeSpikes after removing restored spikes");
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager capture identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> matches = liveObjects(objectManager, type);
        assertEquals(1, matches.size(), "expected exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static ObjectInstance onlyByClassName(ObjectManager objectManager, String className) {
        List<ObjectInstance> matches = liveObjectsByClassName(objectManager, className);
        assertEquals(1, matches.size(), "expected exactly one live " + className);
        return matches.getFirst();
    }

    private static <T extends ObjectInstance> List<T> liveObjects(ObjectManager objectManager, Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .toList();
    }

    private static List<ObjectInstance> liveObjectsByClassName(ObjectManager objectManager, String className) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals(className) && !object.isDestroyed())
                .toList();
    }

    private static int identityFrequency(List<?> values, Object expected) {
        int count = 0;
        for (Object value : values) {
            if (value == expected) {
                count++;
            }
        }
        return count;
    }

    private static Object readObject(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ObjectInstance> readObjectList(Object target, String fieldName) {
        return (List<ObjectInstance>) readObject(target, fieldName);
    }

    private static List<?> readList(Object target, String fieldName) {
        return (List<?>) readObject(target, fieldName);
    }

    private static int readInt(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static boolean readBoolean(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read " + fieldName + " from " + target.getClass(), e);
        }
    }

    private static void writeInt(Object target, String fieldName, int value) {
        try {
            findField(target.getClass(), fieldName).setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void writeBoolean(Object target, String fieldName, boolean value) {
        try {
            findField(target.getClass(), fieldName).setBoolean(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to write " + fieldName + " on " + target.getClass(), e);
        }
    }

    private static void invokePrivate(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to invoke " + methodName + " on " + target.getClass(), e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Walk superclass chain.
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private record BadnikChildGraph(
            Sonic1BombBadnikInstance bomb,
            Sonic1CaterkillerBadnikInstance caterkiller,
            Sonic1OrbinautBadnikInstance orbinaut,
            Sonic1BombFuseInstance fuse,
            List<Sonic1CaterkillerBodyInstance> bodies,
            List<ObjectInstance> spikes) {

        static BadnikChildGraph spawnRepresentativeFamily(ObjectManager objectManager) throws Exception {
            Sonic1BombBadnikInstance bomb = only(objectManager, Sonic1BombBadnikInstance.class);
            Sonic1CaterkillerBadnikInstance caterkiller =
                    only(objectManager, Sonic1CaterkillerBadnikInstance.class);
            Sonic1OrbinautBadnikInstance orbinaut = only(objectManager, Sonic1OrbinautBadnikInstance.class);

            Sonic1BombFuseInstance fuse = objectManager.createDynamicObject(
                    () -> new Sonic1BombFuseInstance(
                            bomb.getX(), bomb.getY(), true, false, 0x33, -0x10, bomb));
            List<Sonic1CaterkillerBodyInstance> bodies = spawnCaterkillerBodies(objectManager, caterkiller);
            invokePrivate(orbinaut, "spawnSatellites");
            List<ObjectInstance> spikes = liveObjectsByClassName(objectManager, ORB_SPIKE_CLASS);

            BadnikChildGraph graph = new BadnikChildGraph(
                    bomb, caterkiller, orbinaut, fuse, List.copyOf(bodies), List.copyOf(spikes));
            seedDistinctState(graph);
            return graph;
        }

        static BadnikChildGraph spawnReplacementFamily(ObjectManager objectManager) throws Exception {
            return spawnRepresentativeFamily(objectManager);
        }

        static BadnikChildGraph fromLiveObjects(ObjectManager objectManager) {
            Sonic1CaterkillerBadnikInstance caterkiller =
                    only(objectManager, Sonic1CaterkillerBadnikInstance.class);
            Sonic1OrbinautBadnikInstance orbinaut = only(objectManager, Sonic1OrbinautBadnikInstance.class);
            List<Sonic1CaterkillerBodyInstance> bodies = readObjectList(caterkiller, "bodySegments").stream()
                    .map(Sonic1CaterkillerBodyInstance.class::cast)
                    .toList();
            List<ObjectInstance> spikes = readObjectList(orbinaut, "spikes");
            assertEquals(3, bodies.size(), "expected three parent-owned Caterkiller bodies");
            assertEquals(4, spikes.size(), "expected four parent-owned Orbinaut spikes");
            return new BadnikChildGraph(
                    only(objectManager, Sonic1BombBadnikInstance.class),
                    caterkiller,
                    orbinaut,
                    only(objectManager, Sonic1BombFuseInstance.class),
                    List.copyOf(bodies),
                    List.copyOf(spikes));
        }

        Map<Class<?>, Integer> counts() {
            Map<Class<?>, Integer> counts = new LinkedHashMap<>();
            counts.put(Sonic1BombBadnikInstance.class, 1);
            counts.put(Sonic1CaterkillerBadnikInstance.class, 1);
            counts.put(Sonic1OrbinautBadnikInstance.class, 1);
            counts.put(Sonic1BombFuseInstance.class, 1);
            counts.put(Sonic1CaterkillerBodyInstance.class, bodies.size());
            counts.put(spikes.getFirst().getClass(), spikes.size());
            return counts;
        }

        Map<String, ObjectRefId> ids(ObjectManager objectManager) {
            Map<String, ObjectRefId> ids = new LinkedHashMap<>();
            ids.put("bomb", objectId(objectManager, bomb));
            ids.put("caterkiller", objectId(objectManager, caterkiller));
            ids.put("orbinaut", objectId(objectManager, orbinaut));
            ids.put("fuse", objectId(objectManager, fuse));
            for (int i = 0; i < bodies.size(); i++) {
                ids.put("body" + i, objectId(objectManager, bodies.get(i)));
            }
            for (int i = 0; i < spikes.size(); i++) {
                ids.put("spike" + i, objectId(objectManager, spikes.get(i)));
            }
            return ids;
        }

        Map<String, Object> scalarSnapshot() {
            Map<String, Object> scalars = new LinkedHashMap<>();
            scalars.put("fuse.currentX", readInt(fuse, "currentX"));
            scalars.put("fuse.currentY", readInt(fuse, "currentY"));
            scalars.put("fuse.yVelocity", readInt(fuse, "yVelocity"));
            scalars.put("fuse.timer", readInt(fuse, "timer"));
            scalars.put("fuse.animTickCounter", readInt(fuse, "animTickCounter"));
            scalars.put("fuse.facingLeft", readBoolean(fuse, "facingLeft"));
            scalars.put("fuse.ceilingBomb", readBoolean(fuse, "ceilingBomb"));
            for (int i = 0; i < bodies.size(); i++) {
                Sonic1CaterkillerBodyInstance body = bodies.get(i);
                String prefix = "body" + i + ".";
                scalars.put(prefix + "currentX", readInt(body, "currentX"));
                scalars.put(prefix + "currentY", readInt(body, "currentY"));
                scalars.put(prefix + "xVelocity", readInt(body, "xVelocity"));
                scalars.put(prefix + "yVelocity", readInt(body, "yVelocity"));
                scalars.put(prefix + "inertia", readInt(body, "inertia"));
                scalars.put(prefix + "ringBufferIndex", readInt(body, "ringBufferIndex"));
                scalars.put(prefix + "isAnimatedSegment", readBoolean(body, "isAnimatedSegment"));
                scalars.put(prefix + "animAngle", readInt(body, "animAngle"));
                scalars.put(prefix + "animControl", readInt(body, "animControl"));
                scalars.put(prefix + "secondaryState", readInt(body, "secondaryState"));
            }
            scalars.put("orb.activeSpikes", readInt(orbinaut, "activeSpikes"));
            scalars.put("orb.initialized", readBoolean(orbinaut, "initialized"));
            for (int i = 0; i < spikes.size(); i++) {
                ObjectInstance spike = spikes.get(i);
                String prefix = "spike" + i + ".";
                scalars.put(prefix + "x", readInt(spike, "x"));
                scalars.put(prefix + "y", readInt(spike, "y"));
                scalars.put(prefix + "angle", readInt(spike, "angle"));
                scalars.put(prefix + "launched", readBoolean(spike, "launched"));
                scalars.put(prefix + "xVelocity", readInt(spike, "xVelocity"));
            }
            return scalars;
        }

        void removeDynamicChildren(ObjectManager objectManager) {
            objectManager.removeDynamicObject(fuse);
            for (Sonic1CaterkillerBodyInstance body : bodies) {
                objectManager.removeDynamicObject(body);
            }
            for (ObjectInstance spike : spikes) {
                objectManager.removeDynamicObject(spike);
            }
        }

        private static List<Sonic1CaterkillerBodyInstance> spawnCaterkillerBodies(
                ObjectManager objectManager,
                Sonic1CaterkillerBadnikInstance head) throws Exception {
            List<Sonic1CaterkillerBodyInstance> bodies = new ArrayList<>();
            Object parentState = head;
            boolean[] animated = {false, true, false};
            for (int i = 0; i < 3; i++) {
                int index = i;
                Object immediateParent = parentState;
                Sonic1CaterkillerBodyInstance body = objectManager.createDynamicObject(
                        () -> newCaterkillerBody(
                                head,
                                immediateParent,
                                head.getX() + 0x0C * (index + 1),
                                head.getY(),
                                animated[index],
                                index,
                                4 * (index + 1)));
                bodies.add(body);
                parentState = body;
            }
            readObjectList(head, "bodySegments").clear();
            readObjectList(head, "bodySegments").addAll(bodies);
            return bodies;
        }

        private static Sonic1CaterkillerBodyInstance newCaterkillerBody(
                Sonic1CaterkillerBadnikInstance head,
                Object parentState,
                int x,
                int y,
                boolean animated,
                int segmentIndex,
                int ringBufferStart) {
            try {
                Class<?> parentStateType = Class.forName(
                        "com.openggf.game.sonic1.objects.badniks.CaterkillerParentState");
                Constructor<Sonic1CaterkillerBodyInstance> constructor =
                        Sonic1CaterkillerBodyInstance.class.getDeclaredConstructor(
                                Sonic1CaterkillerBadnikInstance.class,
                                parentStateType,
                                int.class,
                                int.class,
                                boolean.class,
                                boolean.class,
                                int.class,
                                int.class);
                constructor.setAccessible(true);
                return constructor.newInstance(
                        head, parentState, x, y, true, animated, segmentIndex, ringBufferStart);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unable to construct Caterkiller body", e);
            }
        }

        private static void seedDistinctState(BadnikChildGraph graph) {
            writeInt(graph.fuse(), "currentX", BOMB_SPAWN.x() + 5);
            writeInt(graph.fuse(), "currentY", BOMB_SPAWN.y() - 7);
            writeInt(graph.fuse(), "yVelocity", -0x20);
            writeInt(graph.fuse(), "timer", 12);
            writeInt(graph.fuse(), "animTickCounter", 5);
            writeBoolean(graph.fuse(), "facingLeft", true);
            writeBoolean(graph.fuse(), "ceilingBomb", true);

            for (int i = 0; i < graph.bodies().size(); i++) {
                Sonic1CaterkillerBodyInstance body = graph.bodies().get(i);
                writeInt(body, "currentX", CATERKILLER_SPAWN.x() + 0x20 + i * 0x10);
                writeInt(body, "currentY", CATERKILLER_SPAWN.y() + i);
                writeInt(body, "xVelocity", 0x80 + i);
                writeInt(body, "yVelocity", -0x40 - i);
                writeInt(body, "inertia", 0x10 + i);
                writeInt(body, "ringBufferIndex", i);
                writeBoolean(body, "isAnimatedSegment", i == 1);
                writeInt(body, "animAngle", 0x20 + i);
                writeInt(body, "animControl", 0x80 | i);
                writeInt(body, "secondaryState", i == 0 ? 1 : 0);
                body.writeRingBuffer(i, 2 + i);
            }

            writeInt(graph.orbinaut(), "activeSpikes", 2);
            writeBoolean(graph.orbinaut(), "initialized", true);
            for (int i = 0; i < graph.spikes().size(); i++) {
                ObjectInstance spike = graph.spikes().get(i);
                writeInt(spike, "x", ORBINAUT_SPAWN.x() + i * 3);
                writeInt(spike, "y", ORBINAUT_SPAWN.y() - i * 2);
                writeInt(spike, "angle", 0x20 * i);
                writeBoolean(spike, "launched", i >= 2);
                writeInt(spike, "xVelocity", i >= 2 ? 0x200 : 0);
            }
        }
    }

    private record Harness(ObjectManager objectManager) {
        static Harness createWithParents() {
            return create(List.of(BOMB_SPAWN, CATERKILLER_SPAWN, ORBINAUT_SPAWN));
        }

        static Harness createWithoutParents() {
            return create(List.of());
        }

        private static Harness create(List<ObjectSpawn> spawns) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCameraAtOrigin();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
            };
            ObjectManager objectManager = new ObjectManager(
                    spawns,
                    new Sonic1ObjectRegistry(),
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(0);
            return new Harness(objectManager);
        }
    }

    private static Camera mockCameraAtOrigin() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
