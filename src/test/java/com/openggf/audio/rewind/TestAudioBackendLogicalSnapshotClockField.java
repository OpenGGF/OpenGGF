package com.openggf.audio.rewind;

import com.openggf.audio.runtime.AudioFrameClock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestAudioBackendLogicalSnapshotClockField {

    @Test
    void compatibilityConstructorsLeaveClockSnapshotNull() {
        AudioBackendLogicalSnapshot oldShape = new AudioBackendLogicalSnapshot(
                null, false, false, false, 1, List.of());
        assertNull(oldShape.clockSnapshot());

        AudioBackendLogicalSnapshot drivers = new AudioBackendLogicalSnapshot(
                null, false, false, false, 1, List.of(), null, null);
        assertNull(drivers.clockSnapshot());

        assertNull(AudioBackendLogicalSnapshot.empty().clockSnapshot());
    }

    @Test
    void canonicalConstructorRetainsClockSnapshot() {
        AudioFrameClock.Snapshot clock = new AudioFrameClock.Snapshot(48000, 60, 480000L, 13);
        AudioBackendLogicalSnapshot snapshot = new AudioBackendLogicalSnapshot(
                null, false, false, false, 1, List.of(), null, null, clock);

        assertNotNull(snapshot.clockSnapshot());
        assertEquals(480000L, snapshot.clockSnapshot().totalSamplesProduced());
        assertEquals(13, snapshot.clockSnapshot().remainder());
    }
}
