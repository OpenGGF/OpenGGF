package com.openggf.game.rewind;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindSnapshotDiffDynamicIdentity {
    @Test
    void distinctCapturedVisualsSharingFixedSlotCanRestoreInReverseOrder() {
        var insta = entry("InstaShield", 100, 1, false);
        var bubble = entry("BubbleShield", 100, 450, false);
        assertEquivalent(snapshot(insta, bubble), snapshot(bubble, insta));
    }

    @Test
    void sameSlotMemberCannotBeDroppedOrDuplicated() {
        var insta = entry("InstaShield", 100, 1, false);
        var bubble = entry("BubbleShield", 100, 450, false);
        assertDifferent(snapshot(insta, bubble), snapshot(bubble), "missing in B");
        assertDifferent(snapshot(insta, bubble), snapshot(insta, bubble, bubble), "duplicate object identity");
        assertDifferent(snapshot(insta, insta), snapshot(insta, insta), "duplicate object identity");
    }

    @Test
    void stableIdentityDoesNotHideChangedSlotClassOrState() {
        var insta = entry("InstaShield", 100, 1, false);
        var bubble = entry("BubbleShield", 100, 450, false);
        var before = snapshot(insta, bubble);
        assertDifferent(before, snapshot(bubble, entry("InstaShield", 101, 1, false)), "slotIndex");
        assertDifferent(before, snapshot(bubble, entry("OtherShield", 100, 1, false)), "className");
        assertDifferent(before, snapshot(bubble, entry("InstaShield", 100, 1, true)), "destroyed");
        assertDifferent(before, snapshot(bubble, entry("InstaShield", 100, 2, false)), "missing in B");
    }

    @Test
    void legacyEntriesRetainUniqueSlotFallbackWithoutCollapsingDuplicates() {
        var first = new DynamicObjectEntry("Legacy", null, 50, null);
        var second = new DynamicObjectEntry("Legacy", null, 60, null);
        assertEquivalent(snapshot(first, second), snapshot(second, first));
        assertDifferent(snapshot(first, second), snapshot(first, first), "duplicate object identity");
    }

    private static DynamicObjectEntry entry(String className, int slot, int id, boolean destroyed) {
        var state = new PerObjectRewindSnapshot(destroyed, false, false, 0, 0,
                0, 0, false, -1, false, true, slot, -1, null, null, null);
        // Identity is minted at creation; a later slot move does not change it.
        return new DynamicObjectEntry(className, null, slot, state, null,
                ObjectRefId.dynamic(100, 0, id));
    }

    private static ObjectManagerSnapshot snapshot(DynamicObjectEntry... entries) {
        return new ObjectManagerSnapshot(new long[0], List.of(), 0, 0, 0, 0,
                false, List.of(), List.of(entries), null);
    }

    private static void assertEquivalent(ObjectManagerSnapshot a, ObjectManagerSnapshot b) {
        assertTrue(RewindSnapshotDiff.keyEquals("object-manager", a, b));
        assertTrue(RewindSnapshotDiff.diffKey("object-manager", a, b).isEmpty());
    }

    private static void assertDifferent(ObjectManagerSnapshot a, ObjectManagerSnapshot b, String field) {
        assertFalse(RewindSnapshotDiff.keyEquals("object-manager", a, b));
        var differences = RewindSnapshotDiff.diffKey("object-manager", a, b);
        assertTrue(differences.stream().anyMatch(diff -> diff.contains(field)), differences.toString());
    }
}
