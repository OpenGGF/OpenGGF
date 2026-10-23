package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.SpriteSlotEmission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.level.objects.NativeSpriteSubmissionAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Read-only late SST resolution through a real manager; no drawing or object dispatch. */
class TestNativeSpriteResolvedAdmissions {
    private GraphicsManager graphics;

    @BeforeEach void setUp() {
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
    }

    @AfterEach void tearDown() { graphics.resetState(); }

    @Test void rawOrderBucketsDuplicatesAndOrphansArePreservedForOrdinaryAndNativeOwners() {
        var manager = manager();
        var ordinary = add(manager, 70, 1, false);
        var nativeOwner = add(manager, 40, 2, true);
        submit(manager, 70, 7);
        submit(manager, nativeOwner, 2);
        submit(manager, 70, 2);
        submit(manager, 127, 7);
        submit(manager, 40, 2);
        ordinary.bucket = 0;
        nativeOwner.bucket = 6;
        var visitor = new RecordingVisitor();

        visitResolvedAdmissions(manager, null, visitor);

        assertEquals(List.of(
                new Seen(0, new Admission(70, 7), ordinary),
                new Seen(1, new Admission(40, 2), nativeOwner),
                new Seen(2, new Admission(70, 2), ordinary),
                new Seen(3, new Admission(127, 7), null),
                new Seen(4, new Admission(40, 2), nativeOwner)), visitor.seen);
    }

    @Test void lateReplacementAndClearedIdZeroOwnersBlockExternalFallback() {
        var manager = manager();
        var retired = add(manager, 40, 1, true);
        submit(manager, retired, 3);
        submit(manager, 40, 1);
        manager.removeDynamicObject(retired);
        var replacement = add(manager, 40, 2, false);
        replacement.liveId = 0;
        var visitor = new RecordingVisitor();

        visitResolvedAdmissions(manager, slot -> {
            fail("A real ID-zero owner must block external lookup");
            return null;
        }, visitor);

        assertEquals(List.of(new Seen(0, new Admission(40, 3), replacement),
                new Seen(1, new Admission(40, 1), replacement)), visitor.seen);
        assertEquals(0, replacement.getLiveObjectId());
        assertNotSame(retired, visitor.seen.getFirst().owner());
    }

    @Test void explicitTypedExternalResolutionNeverUsesTheLegacyDrawResolver() {
        var manager = manager();
        submit(manager, 60, 2);
        submit(manager, 61, 4);
        submit(manager, 60, 1);
        bindExternalResolver(manager, slot -> {
            fail("Resolved-owner visitation must not consult the draw resolver");
            return new SpriteSlotEmission(slot, 0, 0, () -> fail("Must not draw"));
        });
        var source = new ExternalSource(60, 17);
        var visitedSlots = new ArrayList<Integer>();
        var visitor = new RecordingVisitor();

        visitResolvedAdmissions(manager, slot -> {
            visitedSlots.add(slot);
            return slot == 60 ? source : null;
        }, visitor);

        assertEquals(List.of(60, 61, 60), visitedSlots);
        assertEquals(List.of(new Seen(0, new Admission(60, 2), source),
                new Seen(1, new Admission(61, 4), null),
                new Seen(2, new Admission(60, 1), source)), visitor.seen);
        var mismatched = new RecordingVisitor();
        assertThrows(IllegalArgumentException.class, () ->
                visitResolvedAdmissions(manager, slot -> new ExternalSource(62, 0), mismatched));
        assertTrue(mismatched.seen.isEmpty());
    }

    @Test void repeatedVisitsPreserveWorldStateAdmissionCapacityAndPassPresence() {
        var manager = manager();
        var actor = add(manager, 40, 1, true);
        manager.update(0, null, List.of(), 9, false);
        for (int call = 0; call < 63; call++) assertTrue(submit(manager, actor, 3));
        var registry = registry(manager);
        var before = registry.capture();
        int objectTicks = actor.ticks;
        assertTrue(nativePassPresent(manager));

        for (int repeat = 0; repeat < 3; repeat++) {
            var visitor = new RecordingVisitor();
            visitResolvedAdmissions(manager, null, visitor);
            assertEquals(63, visitor.seen.size());
            assertSame(actor, visitor.seen.getFirst().owner());
        }

        assertEquals(objectTicks, actor.ticks);
        assertEquals(0, actor.draws);
        assertTrue(nativePassPresent(manager));
        assertFalse(submit(manager, 60, 3), "Visiting must not replenish or consume admission capacity");
        var after = registry.capture();
        for (String key : List.of("object-manager", REWIND_KEY)) {
            assertTrue(RewindSnapshotDiff.diffKey(key, before.get(key), after.get(key)).isEmpty(), key);
        }
    }

    @Test void destructiveRestoreRetainsAdmissionsButResolvesFreshCurrentOwnersAndExternalSources() {
        var manager = manager();
        var retired = add(manager, 40, 1, true);
        submit(manager, retired, 7);
        submit(manager, 60, 1);
        submit(manager, 40, 2);
        submit(manager, 127, 0);
        var registry = registry(manager);
        var saved = registry.capture();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        manager.reset(0);
        add(manager, 40, 9, false);

        registry.restore(saved);
        var restored = NativeSstSlotAccess.currentOwner(manager, 40);
        var external = new ExternalSource(60, 99);
        var visitor = new RecordingVisitor();
        visitResolvedAdmissions(manager, slot -> slot == 60 ? external : null, visitor);

        assertNotNull(restored);
        assertNotSame(retired, restored);
        assertInstanceOf(NativeProbe.class, restored);
        assertEquals(List.of(new Seen(0, new Admission(40, 7), restored),
                new Seen(1, new Admission(60, 1), external),
                new Seen(2, new Admission(40, 2), restored),
                new Seen(3, new Admission(127, 0), null)), visitor.seen);
        assertEquals(0, ((Probe) restored).draws);
    }

    @Test void nullManagerIsEmptyAndNullVisitorIsAlwaysRejected() {
        var visitor = new RecordingVisitor();
        visitResolvedAdmissions(null, slot -> { fail("No manager has no slots to resolve"); return null; }, visitor);
        assertTrue(visitor.seen.isEmpty());
        assertThrows(NullPointerException.class, () -> visitResolvedAdmissions(null, null, null));
        assertThrows(NullPointerException.class, () -> visitResolvedAdmissions(manager(), null, null));
    }

    private ObjectManager manager() {
        Camera camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };
        holder[0] = new ObjectManager(List.of(), null, 0, null, null, graphics, camera, services);
        return holder[0];
    }

    private static Probe add(ObjectManager manager, int slot, int label, boolean nativeOwner) {
        var spawn = new ObjectSpawn(80, 80, 1, label, 0, false, 0);
        Probe actor = nativeOwner ? new NativeProbe(spawn) : new Probe(spawn);
        manager.addDynamicObjectAtSlot(actor, slot);
        return actor;
    }

    private static RewindRegistry registry(ObjectManager manager) {
        var registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        registerRewind(manager, registry);
        return registry;
    }

    private record ExternalSource(int slot, int value) implements ExternalSlotSource { }
    private record Seen(int ordinal, Admission admission, Object owner) { }

    private static final class RecordingVisitor implements ResolvedOwnerVisitor {
        final List<Seen> seen = new ArrayList<>();
        @Override public void object(int ordinal, Admission admission, AbstractObjectInstance owner) {
            seen.add(new Seen(ordinal, admission, owner));
        }
        @Override public void external(int ordinal, Admission admission, ExternalSlotSource source) {
            seen.add(new Seen(ordinal, admission, source));
        }
        @Override public void orphan(int ordinal, Admission admission) { seen.add(new Seen(ordinal, admission, null)); }
    }

    public static class Probe extends AbstractObjectInstance implements SpawnRewindRecreatable {
        int bucket = 3, liveId = 1, ticks, draws;
        public Probe(ObjectSpawn spawn) { super(spawn, "resolved-admission-probe"); }
        @Override public int getPriorityBucket() { return bucket; }
        @Override public int getLiveObjectId() { return liveId; }
        @Override public boolean isPersistent() { return true; }
        @Override public void update(int vIntRunCount, PlayableEntity player) { ticks++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { draws++; }
    }

    public static final class NativeProbe extends Probe implements NativeSpriteSubmissionSource {
        public NativeProbe(ObjectSpawn spawn) { super(spawn); }
    }
}
