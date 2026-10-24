package com.openggf.game.rewind;

import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic1.objects.Sonic1RingInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Nullable recipes accepted by the public constructor remain valid without a manager/table. */
class TestS1RingNullRecipeRewind {
    @ParameterizedTest(name = "null parent={0}, explicit none={1}, recreated={2}")
    @CsvSource({
            "true, false, false", "true, false, true",
            "true, true, false", "true, true, true",
            "false, false, false", "false, false, true",
            "false, true, false", "false, true, true"
    })
    void nullableRecipeSurvivesBothPublicNoTableForms(boolean nullParent, boolean explicitNone,
                                                     boolean recreated) {
        ObjectSpawn spawn = new ObjectSpawn(0x100, 0x200, 0x25, 3, 0, false, 0);
        RingSpawn parentPlacement = new RingSpawn(0x100, 0x200, 10);
        RingSpawn childPlacement = new RingSpawn(0x120, 0x200, 11);
        List<RingSpawn> supplied = nullParent ? Collections.singletonList(null)
                : new ArrayList<>(Arrays.asList(parentPlacement, null, childPlacement, null));
        Sonic1RingInstance original = new Sonic1RingInstance(spawn, supplied);
        original.setServices(new StubObjectServices());
        assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(Sonic1RingInstance.class));
        assertTrue(GenericRewindEligibility.usesCompactDefaultSubclassCapture(Sonic1RingInstance.class));

        if (!nullParent) {
            childPlacement = new RingSpawn(0x130, 0x210, 12);
            supplied.set(2, childPlacement);
            assertEquals(supplied.subList(1, supplied.size()), placements(original, "childRingSpawns"),
                    "the constructor must retain the caller's child view before capture");
        }
        List<RingSpawn> capturedValues = new ArrayList<>(supplied);
        var snapshot = explicitNone ? original.captureRewindState(RewindCaptureContext.none())
                : original.captureRewindState();
        assertNotNull(snapshot.compactGenericState());
        if (!nullParent) {
            supplied.set(1, childPlacement);
            supplied.set(3, childPlacement);
            assertEquals(supplied.subList(1, supplied.size()), placements(original, "childRingSpawns"),
                    "capture must not detach the still-live caller view");
        }
        original.update(1, null);
        Sonic1RingInstance restored = recreated
                ? (Sonic1RingInstance) original.recreateForRewind(
                        new RewindRecreateContext(spawn, snapshot, new StubObjectServices()))
                : original;
        restored.setServices(new StubObjectServices());
        if (recreated) assertNotSame(original, restored);
        if (explicitNone) restored.restoreRewindState(snapshot, RewindCaptureContext.none());
        else restored.restoreRewindState(snapshot);

        assertEquals(Collections.singletonList(capturedValues.getFirst()), placements(restored, "ringSpawn"));
        assertEquals(capturedValues.subList(1, capturedValues.size()), placements(restored, "childRingSpawns"));
        assertEquals(0, restored.getCollisionFlags(), "restore the captured INIT boundary");
        assertThrows(UnsupportedOperationException.class,
                () -> placements(restored, "ringSpawn").clear(), "restored placement view is immutable");
        if (!nullParent) {
            supplied.set(2, new RingSpawn(99, 100, 20));
            assertEquals(capturedValues.subList(1, capturedValues.size()), placements(restored, "childRingSpawns"),
                    "later caller edits cannot mutate the restored recipe or captured snapshot");
        }
        assertDoesNotThrow(() -> restored.update(1, null));
        assertEquals(Sonic1RingInstance.RING_COLLISION_FLAGS, restored.getCollisionFlags());
        if (explicitNone) restored.restoreRewindState(snapshot, RewindCaptureContext.none());
        else restored.restoreRewindState(snapshot);
        assertEquals(capturedValues.subList(1, capturedValues.size()), placements(restored, "childRingSpawns"),
                "a second restoration reuses the same immutable capture");
    }

    private static List<?> placements(Sonic1RingInstance ring, String name) {
        try {
            Field field = Sonic1RingInstance.class.getDeclaredField(name);
            field.setAccessible(true);
            return (List<?>) field.get(ring);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
