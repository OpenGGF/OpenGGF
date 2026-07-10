package com.openggf.game.timeattack;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestAttemptReplayHarness {

    @Test
    void replayIsDeterministicAndDoctoredInputsDiverge() {
        Path rom = s3kRom();
        String fingerprint = AttemptReplayHarness.fingerprintForRom(rom);
        AttemptInputRecording original = recording(fingerprint, false);
        AttemptInputRecording doctored = recording(fingerprint, true);

        AttemptReplayHarness.Result first = AttemptReplayHarness.replay(original, rom);
        AttemptReplayHarness.Result second = AttemptReplayHarness.replay(original, rom);
        AttemptReplayHarness.Result altered = AttemptReplayHarness.replay(doctored, rom);
        assertNull(first.failureReason());
        assertEquals(first, second);
        assertEquals(original.frameCount(), first.framesSimulated());
        assertNotEquals(first.ghostStreamHashHex(), altered.ghostStreamHashHex());
    }

    @Test
    void fingerprintMismatchRefusesToSimulate() {
        Path rom = s3kRom();
        AttemptInputRecording recording = recording("0.0:00000000", false);
        AttemptReplayHarness.Result result = AttemptReplayHarness.replay(recording, rom);
        assertEquals("fingerprint mismatch", result.failureReason());
        assertEquals(0, result.framesSimulated());
    }

    private static AttemptInputRecording recording(String fingerprint, boolean doctored) {
        AttemptInputRecording recording = new AttemptInputRecording(
                new AttemptStartDescriptor("s3k", 1, 0, "sonic", fingerprint));
        for (int frame = 0; frame < 600; frame++) {
            int mask = frame < 300 ? com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT : 0;
            if (doctored && frame >= 200 && frame < 260) {
                mask = com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT;
            }
            if (frame == 120 || frame == 400) {
                mask |= com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_JUMP;
            }
            recording.appendFrame(mask, false);
        }
        return recording;
    }

    private static Path s3kRom() {
        String configured = System.getProperty("s3k.rom.path", "s3k.gen");
        Path path = Path.of(configured);
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "S3K ROM unavailable; skipping replay harness");
        return path;
    }
}
