package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Real-manager controls for the named Obj7A safe-admission exception, not ROM corruption parity. */
class TestS2SidewaysPformAdmissionQualification {
    private static final int X = 0x220;
    private static final int Y = 0x140;

    @BeforeEach void setup() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void teardown() { GraphicsManager.getInstance().resetState(); }

    @ParameterizedTest(name = "parent={0}, holes={1}, subtype={2}")
    @CsvSource({"40, full, 6", "40, tail, 6", "127, full, 6", "40, earlier, 6",
            "40, one, 6", "40, two, 6", "40, full, 12", "40, one, 12", "40, two, 12"})
    void safeAdmissionAndFreshReplay(int parentSlot, String shape, int subtype) {
        Set<Integer> holes = switch (shape) {
            case "earlier" -> Set.of(16);
            case "one" -> Set.of(49);
            case "two" -> Set.of(47, 80);
            default -> Set.of();
        };
        Fixture f = fixture(parentSlot, holes, !shape.equals("tail"), subtype);
        Map<ObjectRefId, Integer> before = identities(f.manager);
        f.manager.update(0, null, null, 1);
        RecordingManager m = f.manager;
        SidewaysPformObjectInstance child = m.child;
        assertEquals(1, m.attempts);
        assertEquals(parentSlot, m.anchor);
        assertEquals(subtype == 12 ? 1 : 0, value(f.parent, "direction"));
        int initialParentX = X + (subtype == 12 ? -0x80 : -0xB0);
        assertEquals(initialParentX + (subtype == 12 ? -1 : 1), f.parent.getX());
        assertEquals(X - (subtype == 12 ? 0xE8 : 0xA8), value(f.parent, "minX"));
        assertEquals(X + (subtype == 12 ? 0xE8 : 0xA8), value(f.parent, "maxX"));
        assertEquals(parentSlot, f.parent.getSlotIndex());
        if (!shape.equals("one") && !shape.equals("two")) {
            assertNull(child, "exhaustion must not construct/submit a child");
            assertNull(link(f.parent));
            assertEquals(before, identities(m));
            RewindRegistry r = registry(m);
            CompositeSnapshot snapshot = r.capture();
            ObjectRefId parentId = id(m, f.parent);
            m.update(0, null, null, 2);
            int nextX = f.parent.getX();
            // Destructively recreate every identity; init must not run a second time.
            r.restore(snapshot);
            SidewaysPformObjectInstance restored = platform(m, parentId);
            assertNotSame(f.parent, restored);
            assertNull(link(restored));
            assertEquals(before, identities(m));
            m.update(0, null, null, 2);
            assertEquals(nextX, restored.getX());
            ObjectInstance occupant = m.getActiveObjects().stream()
                    .filter(PressureOccupant.class::isInstance).findFirst().orElseThrow();
            m.removeDynamicObject(occupant);
            m.update(0, null, null, 3);
            assertEquals(1, m.attempts, "freeing a genuine occupant does not retry initialization");
            assertNull(link(restored));
            assertDoesNotThrow(r::capture);
            return;
        }
        assertNotNull(child);
        assertTrue(m.constructorLinksWereAbsent, "neither link may be published before admission");
        assertEquals(X + (subtype == 12 ? 0x80 : 0x40), m.constructorX);
        assertEquals(Y, child.getY());
        assertEquals(4, child.getPriorityBucket());
        assertEquals(0, value(child, "direction"));
        assertSame(child, link(f.parent));
        assertSame(f.parent, link(child));
        int expectedSlot = shape.equals("one") ? 49 : 47;
        assertEquals(expectedSlot, child.getSlotIndex());
        assertFalse(child.isDestroyed());
        assertNotNull(id(m, child));
        assertEquals(m.constructorX + 1, child.getX(), "higher slot moves in the same pass");
        assertEquals(before.size() + 1, identities(m).size());
        ObjectRefId parentId = id(m, f.parent);
        ObjectRefId childId = id(m, child);
        RewindRegistry r = registry(m);
        CompositeSnapshot snapshot = r.capture();
        Map<ObjectRefId, Integer> captured = identities(m);
        m.update(0, null, null, 2);
        int nextParentX = f.parent.getX();
        int nextChildX = child.getX();
        m.removeDynamicObject(child);
        r.restore(snapshot);
        assertEquals(captured, identities(m), "every pressure occupant must reconstruct at its exact SST");
        SidewaysPformObjectInstance restoredParent = platform(m, parentId);
        SidewaysPformObjectInstance restoredChild = platform(m, childId);
        assertNotSame(f.parent, restoredParent);
        assertNotSame(child, restoredChild);
        assertSame(restoredChild, link(restoredParent));
        assertSame(restoredParent, link(restoredChild));
        // The discarded graph cannot reach the replacement identities after restore.
        f.parent.setDestroyedByOffscreen();
        child.setDestroyed(true);
        assertFalse(restoredParent.isDestroyed());
        assertFalse(restoredChild.isDestroyed());
        assertSame(restoredChild, link(restoredParent));
        m.update(0, null, null, 2);
        assertEquals(nextParentX, restoredParent.getX());
        assertEquals(nextChildX, restoredChild.getX());
        assertEquals(1, m.attempts, "restored initialized parent does not retry admission");
        assertDoesNotThrow(() -> r.capture());
    }

    @Test
    void admittedPairRetiresAndReleasesRememberFlagAndSlots() {
        MovingCamera camera = new MovingCamera();
        ObjectSpawn spawn = new ObjectSpawn(X, Y, Sonic2ObjectIds.SIDEWAYS_PFORM, 6, 0, true, 0, 121);
        RecordingManager m = manager(List.of(spawn), camera);
        m.update(0, null, null, 1);
        SidewaysPformObjectInstance child = m.child;
        SidewaysPformObjectInstance parent = link(child);
        int parentSlot = parent.getSlotIndex();
        int childSlot = child.getSlotIndex();
        ObjectRefId parentId = id(m, parent);
        ObjectRefId childId = id(m, child);
        assertNotNull(parentId);
        assertNotNull(childId);
        assertFalse(m.isRemembered(spawn));
        assertDoesNotThrow(() -> registry(m).capture());
        camera.x = 0x2000;
        // RunObjects reads the previous ObjectsManager post-camera latch.
        m.postCameraPlacementUpdate(camera.x);
        m.update(camera.x, null, null, 2);
        // Placed unload removes the parent without mutating a retained Java
        // reference's destroyed flag. Check the live graph and reclaimed SSTs.
        assertTrue(child.isDestroyed());
        assertFalse(m.isRemembered(spawn), "offscreen retirement releases the placement latch");
        assertFalse(m.getActiveObjects().contains(parent));
        assertFalse(m.getActiveObjects().contains(child));
        PressureOccupant first = m.createDynamicObject(() -> new PressureOccupant(occupantSpawn(1)));
        PressureOccupant second = m.createDynamicObject(() -> new PressureOccupant(occupantSpawn(2)));
        assertEquals(parentSlot, first.getSlotIndex());
        assertEquals(childSlot, second.getSlotIndex());
        ObjectRefId firstId = id(m, first);
        ObjectRefId secondId = id(m, second);
        assertNotNull(firstId);
        assertNotNull(secondId);
        camera.x = 0;
        m.postCameraPlacementUpdate(camera.x);
        m.update(0, null, null, 3);
        assertEquals(2, m.attempts, "camera return attempts a second child admission");
        SidewaysPformObjectInstance returnedChild = m.child;
        assertNotNull(returnedChild);
        SidewaysPformObjectInstance returnedParent = link(returnedChild);
        assertNotNull(returnedParent);
        assertNotSame(child, returnedChild);
        assertNotSame(parent, returnedParent);
        assertFalse(returnedParent.isDestroyed());
        assertFalse(returnedChild.isDestroyed());
        assertSame(returnedChild, link(returnedParent));
        assertSame(returnedParent, link(returnedChild));
        assertTrue(m.getActiveObjects().contains(returnedParent));
        assertTrue(m.getActiveObjects().contains(returnedChild));
        assertEquals(childSlot + 1, returnedParent.getSlotIndex());
        assertEquals(childSlot + 2, returnedChild.getSlotIndex());
        ObjectRefId returnedParentId = id(m, returnedParent);
        ObjectRefId returnedChildId = id(m, returnedChild);
        assertNotNull(returnedParentId);
        assertNotNull(returnedChildId);
        assertNotEquals(returnedParentId, returnedChildId);
        assertFalse(Set.of(parentId, childId).contains(returnedParentId));
        assertFalse(Set.of(parentId, childId).contains(returnedChildId));
        var table = m.captureIdentityContext().requireIdentityTable();
        assertSame(returnedParent, table.resolveObject(returnedParentId, true));
        assertSame(returnedChild, table.resolveObject(returnedChildId, true));
        assertNull(table.idFor(parent));
        assertNull(table.idFor(child));
        assertTrue(m.getActiveObjects().contains(first));
        assertTrue(m.getActiveObjects().contains(second));
        assertFalse(first.isDestroyed());
        assertFalse(second.isDestroyed());
        assertEquals(parentSlot, first.getSlotIndex());
        assertEquals(childSlot, second.getSlotIndex());
        assertEquals(firstId, table.idFor(first));
        assertEquals(secondId, table.idFor(second));
        assertEquals(4, m.getActiveObjects().size(), "returned pair must coexist with both real reused-slot occupants");
        assertDoesNotThrow(() -> registry(m).capture());
    }

    @Test
    void noManagerBootstrapInitializesOnlyTheParent() {
        StubObjectServices noManager = new StubObjectServices();
        SidewaysPformObjectInstance parent = ObjectConstructionContext.construct(noManager,
                () -> new SidewaysPformObjectInstance(parentSpawn(6), "SidewaysPform"));
        parent.setServices(noManager);
        parent.update(1, null);
        SidewaysPformObjectInstance child = link(parent);
        assertNull(child, "an unmanaged parent must not fabricate an unregistered companion");
        assertEquals(X - 0xB0 + 1, parent.getX());
        parent.update(2, null);
        assertNull(link(parent));
        assertEquals(X - 0xB0 + 2, parent.getX());
    }

    @ParameterizedTest(name = "retireParent={0}, mode={1}")
    @CsvSource({"true,raw", "false,raw", "true,marked", "false,marked",
            "true,offscreen", "false,offscreen", "true,stageplay", "false,stageplay"})
    void eitherMemberRetirementDetachesBeforeSlotReuse(boolean retireParent, String mode) {
        Fixture f = fixture(40, Set.of(49), true, 6);
        RecordingManager m = f.manager;
        m.update(0, null, null, 1);
        SidewaysPformObjectInstance parent = f.parent, child = m.child;
        SidewaysPformObjectInstance retired = retireParent ? parent : child;
        switch (mode) {
            case "raw" -> m.removeDynamicObject(retired);
            case "marked" -> retired.setDestroyed(true);
            case "offscreen" -> retired.setDestroyedByOffscreen();
            case "stageplay" -> ObjectDirectionAccess.forManager(m).removeDynamic(retired);
            default -> fail(mode);
        }
        assertNull(link(parent));
        assertNull(link(child));
        // Repeated retirement is idempotent even before either slot is swept.
        if (mode.equals("stageplay")) ObjectDirectionAccess.forManager(m).removeDynamic(retired);
        else if (mode.equals("raw")) m.removeDynamicObject(retired);
        else if (mode.equals("offscreen")) retired.setDestroyedByOffscreen();
        else retired.setDestroyed(true);
        assertNull(link(parent));
        assertNull(link(child));
        if (retireParent) assertTrue(child.isDestroyed(), "parent retirement retires its companion");
        else assertFalse(parent.isDestroyed(), "child retirement preserves its initialized parent");
        assertDoesNotThrow(() -> registry(m).capture(), "marked-but-unswept state must already close");
        m.update(0, null, null, 2);
        assertFalse(m.getActiveObjects().contains(child));
        assertEquals(!retireParent, m.getActiveObjects().contains(parent));
        PressureOccupant replacement = m.createDynamicObject(() -> new PressureOccupant(occupantSpawn(9)));
        assertNotNull(replacement);
        assertEquals(retireParent ? 40 : 49, replacement.getSlotIndex());
        ObjectRefId replacementId = id(m, replacement);
        // Stale references can mark only old instances, never the new slot occupant.
        parent.setDestroyedByOffscreen();
        child.setDestroyed(true);
        m.update(0, null, null, 3);
        assertTrue(m.getActiveObjects().contains(replacement));
        assertFalse(replacement.isDestroyed());
        assertEquals(replacementId, id(m, replacement));
        assertEquals(1, m.attempts);
        assertDoesNotThrow(() -> registry(m).capture());
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void stageplayRejectedChildReleasesReservationWithoutPublishingLinks(boolean replaceRejectedSlot) {
        RecordingManager m = manager(List.of(), new MovingCamera());
        m.rejectChildWithStageplay = true;
        m.replaceRejectedSlot = replaceRejectedSlot;
        SidewaysPformObjectInstance parent = m.createDynamicObjectAtSlot(
                () -> new SidewaysPformObjectInstance(parentSpawn(6), "SidewaysPform"), 40);
        m.update(0, null, null, 1);
        assertEquals(1, m.attempts);
        assertNotNull(m.child, "capacity existed, so Stageplay must see the unlinked candidate");
        assertTrue(m.constructorLinksWereAbsent);
        assertNull(link(parent));
        assertNull(link(m.child));
        assertNull(id(m, m.child));
        assertEquals(-1, m.child.getSlotIndex());
        assertFalse(m.child.isDestroyed(), "Stageplay rejection is not an exhaustion marker");
        assertEquals(0, m.cleanupReleases, "Stageplay already released once; local cleanup must not repeat it");
        assertDoesNotThrow(() -> registry(m).capture());
        parent.update(2, null);
        assertEquals(1, m.attempts, "policy rejection does not retry");
        // A replacement created inside rejection must retain the slot it now owns.
        if (replaceRejectedSlot) {
            assertNotNull(m.replacement);
            assertEquals(41, ((AbstractObjectInstance) m.replacement).getSlotIndex());
            assertNotNull(id(m, m.replacement));
        }
        int free = m.allocateSlotAfter(40);
        assertEquals(replaceRejectedSlot ? 42 : 41, free);
        m.releaseDynamicSlot(free);
    }

    @Test
    void bulkResetOldLinksCannotRetireReplacementIncarnations() {
        RecordingManager m = manager(List.of(), new MovingCamera());
        SidewaysPformObjectInstance parent = m.createDynamicObjectAtSlot(
                () -> new SidewaysPformObjectInstance(parentSpawn(6), "SidewaysPform"), 40);
        m.update(0, null, null, 1);
        SidewaysPformObjectInstance oldChild = m.child;
        m.reset(0);
        PressureOccupant replacement = m.createDynamicObjectAtSlot(
                () -> new PressureOccupant(occupantSpawn(9)), 40);
        PressureOccupant replacementChild = m.createDynamicObjectAtSlot(
                () -> new PressureOccupant(occupantSpawn(10)), 41);
        parent.setDestroyedByOffscreen();
        oldChild.setDestroyed(true);
        m.update(0, null, null, 2);
        assertEquals(List.of(replacement, replacementChild), List.copyOf(m.getActiveObjects()));
        assertFalse(replacement.isDestroyed());
        assertFalse(replacementChild.isDestroyed());
        assertDoesNotThrow(() -> registry(m).capture());
    }

    @ParameterizedTest
    @CsvSource({"before", "after"})
    void admissionExceptionReleasesOnlyItsOwnReservation(String phase) {
        RecordingManager m = manager(List.of(), new MovingCamera());
        SidewaysPformObjectInstance parent = m.createDynamicObjectAtSlot(
                () -> new SidewaysPformObjectInstance(parentSpawn(6), "SidewaysPform"), 40);
        m.admissionFailure = phase;
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> parent.update(1, null));
        assertEquals("admission-" + phase, error.getMessage());
        assertNull(link(parent));
        assertNull(link(m.child));
        assertNull(id(m, m.child));
        assertEquals(-1, m.child.getSlotIndex());
        assertEquals(phase.equals("before") ? 1 : 0, m.cleanupReleases);
        assertEquals(41, m.allocateSlotAfter(40));
        m.releaseDynamicSlot(41);
        parent.update(2, null);
        assertEquals(2, m.attempts, "only the explicit allocator probe added an attempt");
        assertDoesNotThrow(() -> registry(m).capture());
    }

    @ParameterizedTest
    @CsvSource({"-1", "40"})
    void unmanagedParentWithAManagerHasNoAllocationAnchor(int unownedSlot) {
        RecordingManager m = manager(List.of(), new MovingCamera());
        SidewaysPformObjectInstance parent = ObjectConstructionContext.with(m.getObjectServices(), unownedSlot,
                () -> new SidewaysPformObjectInstance(parentSpawn(6), "SidewaysPform"));
        parent.setServices(m.getObjectServices());
        parent.update(1, null);
        assertNull(link(parent));
        assertEquals(0, m.attempts);
        assertTrue(m.getActiveObjects().isEmpty());
    }

    @Test
    void exhaustedParentNeverChangesTailsOrItsNativeAnimationBoundary() {
        Fixture f = fixture(127, Set.of(), true, 12);
        var tails = new TestablePlayableSprite("tails", (short) 0x1234, (short) 0x567);
        tails.setAnimationId(7);
        var tail = new TailsTailsController(tails, null, false);
        for (int i = 0; i < 21; i++) tail.update();
        var state = tail.captureRewindState();
        int tailsX = tails.getCentreX(), tailsY = tails.getCentreY();
        f.parent.update(1, tails);
        assertNull(link(f.parent));
        assertEquals(tailsX, tails.getCentreX());
        assertEquals(tailsY, tails.getCentreY());
        assertEquals(7, tails.getAnimationId());
        assertEquals(state, tail.captureRewindState(), "prev_anim/FD return remains untouched");
        assertDoesNotThrow(() -> registry(f.manager).capture());
        f.parent.setDestroyedByOffscreen();
        assertEquals(state, tail.captureRewindState());
        assertEquals(tailsX, tails.getCentreX());
        assertEquals(tailsY, tails.getCentreY());
    }

    @Test
    void admittedPairRetainsExactChildEdgeCollisionToggle() throws Exception {
        Fixture f = fixture(40, Set.of(49), true, 6);
        f.manager.update(0, null, null, 1);
        SidewaysPformObjectInstance child = f.manager.child;
        field("x").setInt(child, f.parent.getX() + 48 - 1);
        assertEquals(0, value(child, "direction"));
        child.update(2, null);
        assertEquals(1, value(child, "direction"));
        assertEquals(1, value(f.parent, "direction"));
        assertEquals(24, child.getSolidParams().halfWidth());
        assertTrue(child.isTopSolidOnly());
    }

    @ParameterizedTest
    @CsvSource({"probe", "restore"})
    void reconstructionAndProbeUpdatesDoNotPerformLiveAdmission(String scope) {
        RecordingManager m = manager(List.of(), new MovingCamera());
        SidewaysPformObjectInstance parent = m.createDynamicObjectAtSlot(
                () -> new SidewaysPformObjectInstance(parentSpawn(6), "SidewaysPform"), 40);
        java.util.function.Supplier<Void> initialize = () -> { parent.update(1, null); return null; };
        if (scope.equals("probe")) ObjectConstructionContext.withProbeConstruction(initialize);
        else ObjectConstructionContext.withRewindActiveRestore(initialize);
        assertNull(link(parent));
        assertEquals(0, m.attempts);
        m.update(0, null, null, 2);
        assertEquals(0, m.attempts, "initialized reconstruction receivers do not retry");
        assertDoesNotThrow(() -> registry(m).capture());
    }

    @Test
    void exhaustedPlacedParentReleasesRememberAndRetriesOnlyAsAFreshIncarnation() {
        MovingCamera camera = new MovingCamera();
        ObjectSpawn spawn = new ObjectSpawn(X, Y, Sonic2ObjectIds.SIDEWAYS_PFORM, 6, 0, true, 0, 121);
        RecordingManager m = manager(List.of(spawn), camera);
        SidewaysPformObjectInstance parent = (SidewaysPformObjectInstance) m.getActiveObjects().iterator().next();
        for (int slot = 17; slot < 128; slot++) {
            final int occupied = slot;
            m.createDynamicObjectAtSlot(() -> new PressureOccupant(occupantSpawn(occupied)), slot);
        }
        m.update(0, null, null, 1);
        assertNull(link(parent));
        assertEquals(1, m.attempts);
        assertDoesNotThrow(() -> registry(m).capture());
        camera.x = 0x2000;
        m.postCameraPlacementUpdate(camera.x);
        m.update(camera.x, null, null, 2);
        assertFalse(m.getActiveObjects().contains(parent));
        assertFalse(m.isRemembered(spawn));
        PressureOccupant replacement = m.createDynamicObject(() -> new PressureOccupant(occupantSpawn(9)));
        assertEquals(16, replacement.getSlotIndex(), "exhausted parent releases its actual slot");
        ObjectInstance later = m.getActiveObjects().stream()
                .filter(o -> ((AbstractObjectInstance) o).getSlotIndex() == 40).findFirst().orElseThrow();
        m.removeDynamicObject(later);
        m.removeDynamicObject(replacement);
        camera.x = 0;
        m.postCameraPlacementUpdate(0);
        m.update(0, null, null, 3);
        SidewaysPformObjectInstance fresh = link(m.child);
        assertEquals(2, m.attempts);
        assertNotSame(parent, fresh);
        assertSame(m.child, link(fresh));
        assertEquals(16, fresh.getSlotIndex());
        assertEquals(40, m.child.getSlotIndex());
        assertDoesNotThrow(() -> registry(m).capture());
    }

    @ParameterizedTest
    @CsvSource({"removed", "marked", "reidentity", "replaced"})
    void publicationRequiresTheSameLiveParentIdentityAfterAdmission(String action) {
        RecordingManager m = manager(List.of(), new MovingCamera());
        SidewaysPformObjectInstance parent = m.createDynamicObjectAtSlot(
                () -> new SidewaysPformObjectInstance(parentSpawn(6), "SidewaysPform"), 40);
        ObjectRefId parentId = id(m, parent);
        m.parentAdmissionAction = action;
        parent.update(1, null);
        assertEquals(1, m.attempts);
        assertNotNull(m.child, "this control admits a real candidate before the parent changes");
        assertNull(link(parent));
        assertNull(link(m.child));
        assertNull(id(m, m.child), "publication rejection removes only that admitted child");
        assertEquals(-1, m.child.getSlotIndex());
        if (action.equals("marked")) {
            assertEquals(parentId, id(m, parent), "destroyed-but-unswept still has its registered identity");
            assertTrue(parent.isDestroyed());
        }
        if (action.equals("reidentity")) {
            assertNotNull(id(m, parent));
            assertNotEquals(parentId, id(m, parent), "present identity alone is insufficient");
            assertFalse(parent.isDestroyed());
        }
        assertDoesNotThrow(() -> registry(m).capture(), "capture before sweep is already closed");
        assertEquals(41, m.allocateSlotAfter(40));
        m.releaseDynamicSlot(41);
        if (action.equals("replaced")) {
            ObjectRefId replacementId = id(m, m.replacement);
            assertNotNull(replacementId);
            assertEquals(40, ((AbstractObjectInstance) m.replacement).getSlotIndex());
            parent.setDestroyedByOffscreen();
            m.child.setDestroyed(true);
            m.update(0, null, null, 2);
            assertEquals(replacementId, id(m, m.replacement));
            assertFalse(m.replacement.isDestroyed());
        } else m.update(0, null, null, 2);
        assertDoesNotThrow(() -> registry(m).capture());
    }

    private static Fixture fixture(int parentSlot, Set<Integer> holes, boolean fillEarlier, int subtype) {
        RecordingManager m = manager(List.of(), new MovingCamera());
        SidewaysPformObjectInstance parent = m.createDynamicObjectAtSlot(
                () -> new SidewaysPformObjectInstance(parentSpawn(subtype), "SidewaysPform"), parentSlot);
        assertNotNull(parent);
        assertNotNull(id(m, parent));
        for (int slot = fillEarlier ? 16 : parentSlot + 1; slot < 128; slot++) {
            if (slot != parentSlot && !holes.contains(slot)) {
                final int index = slot;
                assertNotNull(m.createDynamicObjectAtSlot(
                        () -> new PressureOccupant(occupantSpawn(index)), slot));
            }
        }
        m.setRewindInPlaceRestoreEnabledForTest(false);
        assertDoesNotThrow(() -> registry(m).capture(), "pressure fixture itself must be capturable");
        return new Fixture(m, parent);
    }

    private static RecordingManager manager(List<ObjectSpawn> spawns, MovingCamera camera) {
        RecordingManager[] holder = new RecordingManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        };
        RecordingManager m = new RecordingManager(spawns, services, camera);
        holder[0] = m;
        m.reset(0);
        return m;
    }

    private record Fixture(RecordingManager manager, SidewaysPformObjectInstance parent) {}

    private static final class RecordingManager extends ObjectManager {
        SidewaysPformObjectInstance child;
        int attempts;
        int anchor;
        int constructorX;
        boolean constructorLinksWereAbsent;
        boolean rejectChildWithStageplay;
        boolean replaceRejectedSlot;
        boolean installingReplacement;
        String admissionFailure;
        String parentAdmissionAction;
        ObjectInstance replacement;
        int cleanupReleases;
        RecordingManager(List<ObjectSpawn> spawns, ObjectServices services, Camera camera) {
            super(spawns, new Sonic2ObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
        }
        @Override public void addDynamicObject(ObjectInstance object) {
            if (installingReplacement)
                ((AbstractObjectInstance) object).setSlotIndex(41);
            super.addDynamicObject(object);
        }
        @Override public void releaseDynamicSlot(int slot) {
            cleanupReleases++;
            super.releaseDynamicSlot(slot);
        }
        @Override public int allocateSlotAfter(int parentSlot) {
            attempts++;
            anchor = parentSlot;
            return super.allocateSlotAfter(parentSlot);
        }
        @Override public void addDynamicObjectAfterSlot(ObjectInstance object, int parentSlot) {
            child = (SidewaysPformObjectInstance) object;
            constructorX = child.getX();
            constructorLinksWereAbsent = link(child) == null && getActiveObjects().stream()
                    .filter(SidewaysPformObjectInstance.class::isInstance)
                    .allMatch(parent -> link(parent) == null);
            if ("before".equals(admissionFailure)) throw new IllegalStateException("admission-before");
            if (rejectChildWithStageplay) ObjectDirectionAccess.forManager(this).clearObjects();
            super.addDynamicObjectAfterSlot(object, parentSlot);
            if (parentAdmissionAction != null) {
                SidewaysPformObjectInstance parent = (SidewaysPformObjectInstance) getActiveObjects().stream()
                        .filter(o -> o instanceof SidewaysPformObjectInstance && o != child)
                        .findFirst().orElseThrow();
                if (parentAdmissionAction.equals("marked")) parent.setDestroyed(true);
                else {
                    removeDynamicObject(parent);
                    if (parentAdmissionAction.equals("reidentity")) addDynamicObjectAtSlot(parent, parentSlot);
                    if (parentAdmissionAction.equals("replaced")) replacement = createDynamicObjectAtSlot(
                            () -> new PressureOccupant(occupantSpawn(9)), parentSlot);
                }
            }
            if (replaceRejectedSlot) {
                installingReplacement = true;
                try {
                    replacement = ObjectDirectionAccess.createNative(this,
                            new ObjectSpawn(X, Y, Sonic2ObjectIds.SIDEWAYS_PFORM, 0, 0, false, 0));
                } finally {
                    installingReplacement = false;
                }
            }
            if ("after".equals(admissionFailure)) throw new IllegalStateException("admission-after");
        }
    }

    private static final class PressureOccupant extends AbstractObjectInstance implements RewindRecreatable {
        PressureOccupant(ObjectSpawn spawn) { super(spawn, "PressureOccupant"); }
        @Override public PressureOccupant recreateForRewind(RewindRecreateContext ctx) {
            return new PressureOccupant(ctx.spawn());
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {}
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {}
    }

    private static final class MovingCamera extends Camera {
        int x;
        @Override public short getX() { return (short) x; }
        @Override public short getY() { return 0; }
        @Override public short getWidth() { return 320; }
        @Override public short getHeight() { return 224; }
        @Override public boolean isVerticalWrapEnabled() { return false; }
    }

    private static ObjectSpawn parentSpawn(int subtype) {
        return new ObjectSpawn(X, Y, Sonic2ObjectIds.SIDEWAYS_PFORM, subtype, 0, false, 0, 121);
    }
    private static ObjectSpawn occupantSpawn(int slot) { return new ObjectSpawn(X, Y, 1, slot, 0, false, 0); }
    private static ObjectRefId id(ObjectManager m, ObjectInstance o) {
        return m.captureIdentityContext().requireIdentityTable().idFor(o);
    }
    private static Map<ObjectRefId, Integer> identities(ObjectManager m) {
        Map<ObjectRefId, Integer> ids = new HashMap<>();
        for (ObjectInstance o : m.getActiveObjects()) {
            assertNotNull(id(m, o));
            ids.put(id(m, o), ((AbstractObjectInstance) o).getSlotIndex());
        }
        return ids;
    }
    private static SidewaysPformObjectInstance platform(ObjectManager m, ObjectRefId id) {
        return (SidewaysPformObjectInstance) m.getActiveObjects().stream()
                .filter(o -> id.equals(id(m, o))).findFirst().orElseThrow();
    }
    private static RewindRegistry registry(ObjectManager m) {
        RewindRegistry r = new RewindRegistry();
        r.register(m.rewindSnapshottable());
        return r;
    }
    private static SidewaysPformObjectInstance link(Object target) {
        try { return (SidewaysPformObjectInstance) field("linkedPlatform").get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static int value(Object target, String name) {
        try { return field(name).getInt(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Field field(String name) throws NoSuchFieldException {
        Field f = SidewaysPformObjectInstance.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
