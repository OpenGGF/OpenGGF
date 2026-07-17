package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.graphics.RgbaImage;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzVisualCadenceContract {

    private static final String V0 = "00".repeat(32);
    private static final String V1 = "11".repeat(32);
    private static final String C0 = "22".repeat(32);
    private static final String C1 = "33".repeat(32);

    @TempDir
    Path tempDir;

    @Test
    void requiresControlsNaturalExpiryHashesAndReviewedVisibleChange() {
        List<FbzVisualCadenceVerifier.FrameEvidence> complete = List.of(
                frame(0, "zero-step", 1, 0, 1, 0, V0, C0, false),
                frame(1, "one-step", 1, 0, 0, 0, V0, C0, false),
                frame(2, "one-step", 0, 0, 7, 1, V1, C1, true),
                frame(3, "one-step", 7, 1, 6, 1, V1, C1, false),
                frame(4, "one-step", 6, 1, 5, 1, V1, C1, false));
        assertDoesNotThrow(() -> FbzVisualCadenceVerifier.verify(complete));

        assertThrows(IllegalStateException.class,
                () -> FbzVisualCadenceVerifier.verify(complete.subList(0, 4)));
        assertThrows(IllegalStateException.class,
                () -> FbzVisualCadenceVerifier.verify(complete.stream()
                        .map(frame -> new FbzVisualCadenceVerifier.FrameEvidence(
                                frame.index(), frame.control(), frame.timerBefore(), frame.frameBefore(),
                                frame.timerAfter(), frame.frameAfter(), frame.vramSha256(), C0,
                                frame.overlayFree(), frame.reviewedVisibleRegionChanged()))
                        .toList()));

        assertThrows(IllegalStateException.class,
                () -> FbzVisualCadenceVerifier.verify(complete.stream()
                        .map(frame -> new FbzVisualCadenceVerifier.FrameEvidence(
                                frame.index(), frame.control(), frame.timerBefore(), frame.frameBefore(),
                                frame.timerAfter(), frame.frameAfter(), "not-a-sha256", frame.cropSha256(),
                                frame.overlayFree(), frame.reviewedVisibleRegionChanged()))
                        .toList()));
    }

    @Test
    void hashesPackedGenesisDestinationPatternsAndDetectsOnlyReviewedRegionChanges() {
        Pattern first = new Pattern();
        Pattern second = new Pattern();
        first.setPixel(0, 0, (byte) 0xA);
        first.setPixel(1, 0, (byte) 0x5);
        second.setPixel(7, 7, (byte) 0xF);

        String digest = FbzVisualCadenceCapture.sha256Patterns(List.of(first, second));
        assertEquals(64, digest.length());
        assertEquals(digest, FbzVisualCadenceCapture.sha256Patterns(List.of(first, second)));

        RgbaImage before = new RgbaImage(4, 4, new int[16]);
        RgbaImage outside = before.copy();
        outside.setArgb(3, 3, 0xFFFFFFFF);
        RgbaImage inside = outside.copy();
        inside.setArgb(1, 1, 0xFFFFFFFF);
        FbzVisualEvidenceAmendment.VisibleRegion region =
                new FbzVisualEvidenceAmendment.VisibleRegion(1, 1, 1, 1);
        assertFalse(FbzVisualCadenceCapture.reviewedRegionChanged(before, outside, region));
        assertTrue(FbzVisualCadenceCapture.reviewedRegionChanged(outside, inside, region));
    }

    @Test
    void publishesVersionedCadenceFramesAndValidatorCompatibleReceiptsAsOneBatch() throws Exception {
        Path modeRoot = tempDir.resolve("native-pre-compat");
        FbzVisualCadenceCapture.FramePublication zero = publication(modeRoot, 0, "zero-step", C0);
        FbzVisualCadenceCapture.FramePublication one = publication(modeRoot, 1, "one-step", C1);

        new FbzVisualCapturePublisher(new ObjectMapper()).publishCadenceSeries(List.of(
                zero, one, publication(modeRoot, 2, "one-step", C1),
                publication(modeRoot, 3, "one-step", C1),
                publication(modeRoot, 4, "one-step", C1)));

        assertTrue(Files.exists(zero.png()));
        assertTrue(Files.exists(one.png()));
        Map<?, ?> receipt = new ObjectMapper().readValue(one.receipt().toFile(), Map.class);
        assertEquals("one-step", receipt.get("control"));
        assertEquals(C1, receipt.get("crop_sha256"));
        assertEquals(Boolean.TRUE, receipt.get("overlay_free"));
        assertTrue(one.png().toString().contains("time-series" + java.io.File.separator + "engine"));
        assertTrue(one.png().getFileName().toString().startsWith("aniplc-cadence-200-v2-one-step-01"));
    }

    private static FbzVisualCadenceCapture.FramePublication publication(
            Path modeRoot, int index, String control, String cropHash) {
        FbzVisualCadenceCapture.FramePaths paths = FbzVisualCadenceCapture.paths(
                modeRoot, "aniplc-cadence-200-v2", index, control);
        Map<String, Object> receipt = Map.of(
                "control", control,
                "overlay_free", true,
                "vram_sha256", V0,
                "crop_sha256", cropHash);
        return new FbzVisualCadenceCapture.FramePublication(paths.png(), paths.receipt(),
                new byte[]{(byte) index, 7}, receipt);
    }

    private static FbzVisualCadenceVerifier.FrameEvidence frame(
            int index, String control, int timerBefore, int frameBefore,
            int timerAfter, int frameAfter, String vram, String crop, boolean reviewed) {
        return new FbzVisualCadenceVerifier.FrameEvidence(index, control,
                timerBefore, frameBefore, timerAfter, frameAfter,
                vram, crop, true, reviewed);
    }
}
