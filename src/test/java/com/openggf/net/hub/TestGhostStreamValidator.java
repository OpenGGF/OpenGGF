package com.openggf.net.hub;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostStreamValidator {
    private static final TrackValidationProfile PROFILE =
            new TrackValidationProfile(0x2A00, 0x0800, 16, 60);

    private long now = 100_000;
    private final List<String> violations = new ArrayList<>();

    private GhostStreamValidator validator(TrackValidationProfile profile) {
        return new GhostStreamValidator(profile, () -> now,
                (kind, detail) -> violations.add(kind));
    }

    private static byte[] frames(int startX, int step, int count) {
        byte[] data = new byte[count * GhostFrameCodec.BYTES];
        for (int i = 0; i < count; i++) {
            GhostFrameCodec.encode(new GhostFrame(startX + i * step, 0x0100, 1,
                    false, false, false, 2, false), data, i * GhostFrameCodec.BYTES);
        }
        return data;
    }

    private static GhostPackets.FramesBatch batch(int attemptId, int startIndex,
                                                   byte[] frameData) {
        return GhostPackets.decodeFrames(
                GhostPackets.encodeFrames(attemptId, startIndex, frameData));
    }

    private static String hash(byte[]... chunks) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (byte[] chunk : chunks) digest.update(chunk);
        return HexFormat.of().formatHex(digest.digest());
    }

    @Test
    void acceptsContiguousLegitimateStream() {
        GhostStreamValidator v = validator(PROFILE);
        v.onAttemptStart(1);
        for (int i = 0; i < 20; i++) {
            now += 50;
            assertEquals(GhostStreamValidator.Verdict.ACCEPT,
                    v.onBatch(batch(1, i * 3, frames(100 + i * 6, 2, 3))));
        }
        assertEquals(0, v.violationCount());
        assertFalse(v.isAttemptFlagged());
    }

    @Test
    void dropsStaleAttemptSilentlyAndResetsOnNewAttempt() {
        GhostStreamValidator v = validator(PROFILE);
        v.onAttemptStart(2);
        assertEquals(GhostStreamValidator.Verdict.ACCEPT,
                v.onBatch(batch(2, 0, frames(100, 2, 3))));
        assertEquals(GhostStreamValidator.Verdict.DROP,
                v.onBatch(batch(1, 0, frames(100, 2, 3))));
        assertTrue(violations.isEmpty());
        v.onAttemptStart(3);
        assertEquals(GhostStreamValidator.Verdict.ACCEPT,
                v.onBatch(batch(3, 0, frames(500, 2, 3))));
    }

    @Test
    void newAttemptMustStartAtFrameZero() {
        GhostStreamValidator v = validator(PROFILE);
        v.onAttemptStart(1);
        assertEquals(GhostStreamValidator.Verdict.DROP,
                v.onBatch(batch(1, 30, frames(100, 2, 3))));
        assertEquals(List.of("frame-gap"), violations);
    }

    @Test
    void frameGapViolates() {
        GhostStreamValidator v = validator(PROFILE);
        v.onAttemptStart(1);
        v.onBatch(batch(1, 0, frames(100, 2, 3)));
        assertEquals(GhostStreamValidator.Verdict.DROP,
                v.onBatch(batch(1, 9, frames(112, 2, 3))));
        assertEquals(List.of("frame-gap"), violations);
    }

    @Test
    void teleportViolatesSpeedCapAcrossBatchBoundary() {
        GhostStreamValidator v = validator(PROFILE);
        v.onAttemptStart(1);
        v.onBatch(batch(1, 0, frames(100, 2, 3)));
        assertEquals(GhostStreamValidator.Verdict.DROP,
                v.onBatch(batch(1, 3, frames(604, 2, 3))));
        assertEquals(List.of("speed"), violations);
    }

    @Test
    void outOfBoundsViolatesOnlyWithProfile() {
        GhostStreamValidator withProfile = validator(PROFILE);
        withProfile.onAttemptStart(1);
        assertEquals(GhostStreamValidator.Verdict.ACCEPT,
                withProfile.onBatch(batch(1, 0, frames(0x2A30, 2, 3))));
        assertEquals(GhostStreamValidator.Verdict.DROP,
                withProfile.onBatch(batch(1, 3, frames(0x2A3E, 2, 3))));
        assertEquals(List.of("bounds"), violations);

        violations.clear();
        GhostStreamValidator degraded = validator(null);
        degraded.onAttemptStart(1);
        assertEquals(GhostStreamValidator.Verdict.ACCEPT,
                degraded.onBatch(batch(1, 0, frames(0x2A3E, 2, 3))));
        assertTrue(violations.isEmpty());
    }

    @Test
    void pacingFlagsSlowMotionButStillRelays() {
        GhostStreamValidator v = validator(PROFILE);
        v.onAttemptStart(1);
        v.onBatch(batch(1, 0, frames(100, 2, 3)));
        now += 10_000;
        assertEquals(GhostStreamValidator.Verdict.ACCEPT_FLAGGED,
                v.onBatch(batch(1, 3, frames(106, 2, 3))));
        assertTrue(v.isAttemptFlagged());
        assertEquals(List.of("pacing"), violations);
        v.onAttemptStart(2);
        v.onBatch(batch(2, 0, frames(100, 2, 3)));
        assertFalse(v.isAttemptFlagged());
    }

    @Test
    void rateCapViolatesOnSustainedOverSixtyFps() {
        GhostStreamValidator v = validator(PROFILE);
        v.onAttemptStart(1);
        int index = 0;
        boolean rateTripped = false;
        for (int i = 0; i < 200 && !rateTripped; i++) {
            GhostStreamValidator.Verdict verdict =
                    v.onBatch(batch(1, index, frames(100 + index * 2, 2, 3)));
            if (verdict != GhostStreamValidator.Verdict.ACCEPT) {
                rateTripped = true;
                assertTrue(violations.contains("rate-cap"));
            } else {
                index += 3;
            }
        }
        assertTrue(rateTripped);
    }

    @Test
    void updateProfileTightensChecksWithoutResettingStreamState() {
        GhostStreamValidator v = validator(null);
        v.onAttemptStart(1);
        v.onBatch(batch(1, 0, frames(0x29F0, 2, 3)));
        v.updateProfile(PROFILE);
        assertEquals(GhostStreamValidator.Verdict.ACCEPT,
                v.onBatch(batch(1, 3, frames(0x29F6, 2, 3))));
        byte[] outOfLevel = new byte[GhostFrameCodec.BYTES];
        GhostFrameCodec.encode(new GhostFrame(0x2A00 + 65, 0x0100, 1,
                false, false, false, 2, false), outOfLevel, 0);
        assertEquals(GhostStreamValidator.Verdict.DROP,
                v.onBatch(batch(1, 6, outOfLevel)));
        assertFalse(violations.isEmpty());
    }

    @Test
    void tenViolationsKick() {
        GhostStreamValidator v = validator(PROFILE);
        v.onAttemptStart(1);
        v.onBatch(batch(1, 0, frames(100, 2, 3)));
        GhostStreamValidator.Verdict last = GhostStreamValidator.Verdict.ACCEPT;
        for (int i = 0; i < 10; i++) {
            last = v.onBatch(batch(1, 999, frames(100, 2, 3)));
        }
        assertEquals(GhostStreamValidator.Verdict.KICK, last);
        assertEquals(10, v.violationCount());
    }

    @Test
    void finishRequiresServerObservedStartContiguousFramesAndMatchingHash() throws Exception {
        GhostStreamValidator v = validator(PROFILE);
        byte[] first = frames(100, 2, 2);
        byte[] last = frames(104, 2, 1);
        String hash = hash(first, last);
        assertFalse(v.hasFinishEvidence(1, 2, hash));

        v.onAttemptStart(1);
        assertFalse(v.hasFinishEvidence(1, 2, hash));
        v.onBatch(batch(1, 0, first));
        assertFalse(v.hasFinishEvidence(1, 2, hash));
        v.onBatch(batch(1, 2, last));
        assertFalse(v.hasFinishEvidence(1, 2, "00".repeat(32)));

        GhostStreamValidator matching = validator(PROFILE);
        matching.onAttemptStart(1);
        matching.onBatch(batch(1, 0, first));
        matching.onBatch(batch(1, 2, last));
        assertTrue(matching.hasFinishEvidence(1, 2, hash));
    }

    @Test
    void pacingClockStartsAtAttemptStartNotFirstBatch() throws Exception {
        GhostStreamValidator v = validator(PROFILE);
        v.onAttemptStart(1);
        now += 10_000;
        assertEquals(GhostStreamValidator.Verdict.ACCEPT_FLAGGED,
                v.onBatch(batch(1, 0, frames(100, 2, 3))));
        assertFalse(v.hasFinishEvidence(1, 2, hash(frames(100, 2, 3))));
    }
}
