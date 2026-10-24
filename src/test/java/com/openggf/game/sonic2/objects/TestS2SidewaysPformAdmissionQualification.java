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

/**
 * Bounded qualification of the unrepaired Obj7A admission/capture gap.
 *
 * <p>The exhausted-tail cases deliberately characterize the current rejected,
 * unregistered Java child and the resulting strict capture failure. Passing
 * those assertions does not establish a repair or native failure parity.
 * The pinned native branch instead aliases the fixed Tails_Tails SST; see
 * docs/architecture/audits/2026-10-24-rewind-disposition-review.md,
 * "S2 Sideways Platform allocation-failure qualification". A future native
 * fixed-slot repair must revise these known-gap assertions with its evidence.
 * All admission and identity decisions here use the real ObjectManager.
 */
class TestS2SidewaysPformAdmissionQualification {
    private static final int X = 0x220;
    private static final int Y = 0x140;

    @BeforeEach void setup() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void teardown() { GraphicsManager.getInstance().resetState(); }

    @ParameterizedTest(name = "parent={0}, holes={1}, subtype={2}")
    @CsvSource({"40, full, 6", "40, tail, 6", "127, full, 6", "40, earlier, 6",
            "40, one, 6", "40, two, 6", "40, full, 12", "40, one, 12", "40, two, 12"})
    void characterizeRealAdmissionAndFreshReplay(int parentSlot, String shape, int subtype) {
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
        assertNotNull(child);
        assertEquals(1, m.attempts);
        assertEquals(parentSlot, m.anchor);
        assertTrue(m.constructorLinksWereReciprocal);
        assertEquals(X + (subtype == 12 ? 0x80 : 0x40), m.constructorX);
        assertEquals(Y, child.getY());
        assertEquals(4, child.getPriorityBucket());
        assertEquals(0, value(child, "direction"));
        assertEquals(subtype == 12 ? 1 : 0, value(f.parent, "direction"));
        int initialParentX = X + (subtype == 12 ? -0x80 : -0xB0);
        assertEquals(initialParentX + (subtype == 12 ? -1 : 1), f.parent.getX());
        assertSame(child, link(f.parent));
        assertSame(f.parent, link(child));
        assertEquals(parentSlot, f.parent.getSlotIndex());
        if (!shape.equals("one") && !shape.equals("two")) {
            assertTrue(child.isDestroyed());
            assertEquals(-1, child.getSlotIndex());
            assertNull(id(m, child));
            assertFalse(m.getActiveObjects().contains(child));
            assertEquals(before, identities(m));
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> registry(m).capture());
            assertTrue(failure.getMessage().contains("no registered id for object reference"),
                    failure.getMessage());
            // Free a genuine occupant. Initialization must not retry next pass.
            ObjectInstance occupant = m.getActiveObjects().stream()
                    .filter(PressureOccupant.class::isInstance).findFirst().orElseThrow();
            m.removeDynamicObject(occupant);
            m.update(0, null, null, 2);
            assertEquals(1, m.attempts);
            assertSame(child, link(f.parent));
            assertNull(id(m, child));
            return;
        }
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
        camera.x = 0;
        m.postCameraPlacementUpdate(camera.x);
        m.update(0, null, null, 3);
        assertEquals(2, m.attempts, "camera return admits the placed pair again");
    }

    @Test
    void noManagerBootstrapRetainsExistingUnmanagedPair() {
        StubObjectServices noManager = new StubObjectServices();
        SidewaysPformObjectInstance parent = ObjectConstructionContext.construct(noManager,
                () -> new SidewaysPformObjectInstance(parentSpawn(6), "SidewaysPform"));
        parent.setServices(noManager);
        parent.update(1, null);
        SidewaysPformObjectInstance child = link(parent);
        assertNotNull(child);
        assertSame(parent, link(child));
        assertFalse(child.isDestroyed());
        assertEquals(-1, child.getSlotIndex());
        assertEquals(X + 0x40, child.getX());
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
        boolean constructorLinksWereReciprocal;
        RecordingManager(List<ObjectSpawn> spawns, ObjectServices services, Camera camera) {
            super(spawns, new Sonic2ObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
        }
        @Override public void addDynamicObjectAfterSlot(ObjectInstance object, int parentSlot) {
            child = (SidewaysPformObjectInstance) object;
            attempts++;
            anchor = parentSlot;
            constructorX = child.getX();
            constructorLinksWereReciprocal = link(link(child)) == child;
            super.addDynamicObjectAfterSlot(object, parentSlot);
        }
    }

    private static final class PressureOccupant extends AbstractObjectInstance implements RewindRecreatable {
        PressureOccupant(ObjectSpawn spawn) { super(spawn, "PressureOccupant"); }
        @Override public PressureOccupant recreateForRewind(RewindRecreateContext ctx) {
            return new PressureOccupant(ctx.spawn());
        }
        @Override public void update(int count, PlayableEntity player) {}
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
