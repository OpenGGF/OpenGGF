package openggf.racing.server.verifier;

import openggf.timeattack.AttemptStartDescriptor;
import openggf.timeattack.AttemptInputRecording;
import openggf.timeattack.TimeAttackLaunchRequest;

import openggf.racing.server.verifier.AttemptReplayHarness;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestAttemptReplayHarness {

    @Test
    void replayIsDeterministicAndDoctoredInputsDiverge() throws Exception {
        Path rom = s3kRom();
        String fingerprint = AttemptReplayHarness.fingerprintForRom(rom);
        AttemptInputRecording original = recording(fingerprint, false);
        AttemptInputRecording doctored = recording(fingerprint, true);

        AttemptReplayHarness.Result first = AttemptReplayHarness.replay(original, rom);
        var observed = new ArrayList<GhostFrame>();
        AttemptReplayHarness.Result second = AttemptReplayHarness.replay(original, rom, observed::add);
        AttemptReplayHarness.Result altered = AttemptReplayHarness.replay(doctored, rom);
        assertNull(first.failureReason());
        assertEquals(first, second);
        assertEquals(original.frameCount(), first.framesSimulated());
        assertEquals(first.framesSimulated(), observed.size());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encoded = new byte[GhostFrameCodec.BYTES];
        for (GhostFrame frame : observed) {
            GhostFrameCodec.encode(frame, encoded, 0);
            digest.update(encoded);
        }
        assertEquals(first.ghostStreamHashHex(), HexFormat.of().formatHex(digest.digest()));
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
