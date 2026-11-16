package com.openggf.tools;

import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestSessionOutputPaths;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code --pictures-per-frame} with and without {@code --interpolation}: the same run filmed at four
 * pictures per logic frame repeats its exact picture when interpolation is off and draws four
 * different production-blended pictures when it is on, with identical gameplay either way.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayCaptureInterpolation {
    private static final int PICTURES = 4;
    private static final int FIRST = 110;
    private static final int FRAMES = 120;

    @Test
    void interpolatedPicturesBlendThroughTheProductionPathAndLeaveGameplayExact() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        Path root = TestSessionOutputPaths.diagnostics("gameplay-capture-interpolation");
        Files.createDirectories(root);
        Path input = root.resolve("input.txt");
        InputLogAuthorTool.author(FRAMES + " R", input, "s3k");

        GameplayCaptureTool.Report exact;
        try {
            exact = capture(rom, input, root.resolve("exact"), false);
        } catch (IllegalStateException | UnsatisfiedLinkError glUnavailable) {
            assumeTrue(false, "offscreen GL unavailable: " + glUnavailable.getMessage());
            return;
        }
        GameplayCaptureTool.Report blended = capture(rom, input, root.resolve("blended"), true);

        int captured = FRAMES - FIRST;
        assertEquals(captured * PICTURES, exact.pngCount());
        assertEquals(captured * PICTURES, blended.pngCount());
        List<String> state = Files.readAllLines(exact.stateCsv(), StandardCharsets.UTF_8);
        assertEquals(state, Files.readAllLines(blended.stateCsv(), StandardCharsets.UTF_8),
                "interpolation is render-only: both captures step the same gameplay");

        List<String> exactPictures = Files.readAllLines(exact.outDir().resolve("pictures.csv"), StandardCharsets.UTF_8);
        List<String> blendedPictures = Files.readAllLines(blended.outDir().resolve("pictures.csv"), StandardCharsets.UTF_8);
        assertEquals("picture,frame,phase,alpha,outcome,file", exactPictures.get(0));
        assertEquals(captured * PICTURES + 1, exactPictures.size());
        assertEquals(exactPictures.size(), blendedPictures.size());

        int cameraColumn = List.of(state.get(0).split(",")).indexOf("cam_x");
        int checked = 0;
        for (int frame = FIRST; frame < FRAMES; frame++) {
            byte[][] repeated = pictures(exact.outDir(), frame);
            for (int phase = 0; phase < PICTURES; phase++) {
                String[] row = exactPictures.get(1 + (frame - FIRST) * PICTURES + phase).split(",", -1);
                assertEquals(List.of(Integer.toString(frame), Integer.toString(phase), "", "EXACT"),
                        List.of(row).subList(1, 5));
                assertArrayEquals(repeated[0], repeated[phase], "exact picture repeats at frame " + frame);
            }
            int cameraStep = Integer.parseInt(state.get(frame + 1).split(",")[cameraColumn])
                    - Integer.parseInt(state.get(frame).split(",")[cameraColumn]);
            if (cameraStep < PICTURES) continue;
            // The camera moved at least one pixel per picture, so every phase is a different picture.
            byte[][] phased = pictures(blended.outDir(), frame);
            Set<Integer> distinct = new HashSet<>();
            for (int phase = 0; phase < PICTURES; phase++) {
                String[] row = blendedPictures.get(1 + (frame - FIRST) * PICTURES + phase).split(",", -1);
                assertEquals(Double.toString((double) phase / PICTURES), row[3]);
                assertEquals("BLENDED", row[4], "production outcome at frame " + frame + " phase " + phase);
                distinct.add(Arrays.hashCode(phased[phase]));
                assertFalse(Arrays.equals(repeated[0], phased[phase]),
                        "a blended picture trails the exact one at frame " + frame + " phase " + phase);
            }
            assertEquals(PICTURES, distinct.size(), "four different pictures at frame " + frame);
            checked++;
        }
        assertTrue(checked > 0, "the run must reach a camera step of " + PICTURES + " pixels or more");
    }

    private static GameplayCaptureTool.Report capture(Path rom, Path input, Path outDir, boolean interpolation)
            throws Exception {
        List<String> flags = new java.util.ArrayList<>(List.of(
                "--game", "s3k", "--rom", rom.toString(), "--zone", "aiz", "--act", "1", "--sidekick", "none",
                "--input", input.toString(), "--capture-from", Integer.toString(FIRST),
                "--pictures-per-frame", Integer.toString(PICTURES), "--no-video", "--out-dir", outDir.toString()));
        if (interpolation) flags.add("--interpolation");
        return GameplayCaptureTool.run(GameplayCaptureTool.Arguments.parse(flags.toArray(String[]::new)));
    }

    private static byte[][] pictures(Path outDir, int frame) throws Exception {
        byte[][] files = new byte[PICTURES][];
        for (int phase = 0; phase < PICTURES; phase++) {
            files[phase] = Files.readAllBytes(outDir.resolve("frames")
                    .resolve(String.format(java.util.Locale.ROOT, "%05d-%02d.png", frame, phase)));
        }
        return files;
    }
}
