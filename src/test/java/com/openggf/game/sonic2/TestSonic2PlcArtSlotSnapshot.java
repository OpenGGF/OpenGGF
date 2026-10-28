package com.openggf.game.sonic2;

import com.openggf.game.rewind.snapshot.PlcProgressSnapshot;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot.ArtSlotIdentity;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot.ArtSlotRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic2PlcArtSlotSnapshot {
    private static final ArtSlotIdentity IDENTITY =
            new ArtSlotIdentity("8bca5dcef1af3e00098666fd892dc1c2a76333f9", 14);

    @Test
    void recipeImageDefensivelyOwnsItsOrderedValuesAndEmptyImageKeepsProvenance() {
        var ranges = new ArrayList<>(List.of(new ArtSlotRange(0x580, 0x84890, 0, 20)));
        var snapshot = new PlcProgressSnapshot(7, IDENTITY, ranges);
        ranges.clear();
        assertEquals(1, snapshot.artSlotRanges().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.artSlotRanges().clear());
        assertEquals("8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9", snapshot.artSlotIdentity().romSha1());
        assertEquals(IDENTITY, new PlcProgressSnapshot(1, IDENTITY, List.of()).artSlotIdentity());
        assertNotEquals(new PlcProgressSnapshot(1), new PlcProgressSnapshot(1, IDENTITY, List.of()));
    }

    @Test
    void imageRejectsAbsentProvenanceUnorderedOverlappingOrNullEntries() {
        var first = new ArtSlotRange(0x580, 0x84890, 0, 20);
        var after = new ArtSlotRange(0x594, 0x84890, 20, 20);
        assertThrows(IllegalArgumentException.class, () -> new PlcProgressSnapshot(1, null, List.of(first)));
        assertThrows(IllegalArgumentException.class, () -> new PlcProgressSnapshot(1, IDENTITY, List.of(after, first)));
        assertThrows(IllegalArgumentException.class, () -> new PlcProgressSnapshot(1, IDENTITY,
                List.of(first, new ArtSlotRange(0x590, 0x84890, 16, 8))));
        assertThrows(NullPointerException.class, () -> new PlcProgressSnapshot(1, IDENTITY,
                java.util.Arrays.asList(first, null)));
        assertEquals(List.of(first, after), new PlcProgressSnapshot(1, IDENTITY, List.of(first, after)).artSlotRanges());
    }

    @Test
    void recipeAndIdentityBoundsRejectOverflowAndMalformedInputs() {
        assertDoesNotThrow(() -> new ArtSlotRange(0x7FF, 0xFFFFFE, 0x7FFE, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotRange(-1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotRange(0x800, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotRange(0x7FF, 0, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotRange(0, -1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotRange(0, 0xFFFFFF, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotRange(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotRange(0, 0, 0x7FFF, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotRange(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotRange(1, 0, 0, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotIdentity("bad", 14));
        assertThrows(IllegalArgumentException.class, () -> new ArtSlotIdentity(IDENTITY.romSha1(), -2));
    }

    @Test
    void everyLegacyConstructorRetainsNoImageAndTheOldFullSignatureIsPreserved() {
        List<PlcProgressSnapshot> legacy = List.of(
                new PlcProgressSnapshot(1),
                new PlcProgressSnapshot(1, 2),
                new PlcProgressSnapshot(1, List.of("a")),
                new PlcProgressSnapshot(1, 2, List.of("a")),
                new PlcProgressSnapshot(1, 2, List.of(), List.of(), false),
                new PlcProgressSnapshot(1, 2, List.of("a"), List.of(), List.of(), false),
                new PlcProgressSnapshot(1, 2, List.of("a"), List.of(), List.of(), true,
                        3, 4, 5, 6, 7, null, true, false, 8));
        for (var snapshot : legacy) {
            assertNull(snapshot.artSlotIdentity());
            assertTrue(snapshot.artSlotRanges().isEmpty());
        }
        var full = legacy.getLast();
        assertEquals(2, full.runtimeState());
        assertEquals(List.of("a"), full.publishedLevelArtKeys());
        assertTrue(full.kosSubmissionArmed());
        assertEquals(3, full.titleCardTeardownTicks());
        assertEquals(4, full.runtimeArtAdmissionGeneration());
        assertEquals(5, full.runtimeArtAdmissionNextLeaseId());
        assertEquals(6, full.runtimeArtAdmissionLeaseId());
        assertEquals(7, full.runtimeArtAdmissionBatchFingerprint());
        assertTrue(full.runtimeArtAdmissionBound());
        assertFalse(full.runtimeArtAdmissionConsumed());
        assertEquals(8, full.titleCardTeardownLeaseId());
    }
}
