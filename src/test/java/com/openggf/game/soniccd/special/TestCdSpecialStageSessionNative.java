package com.openggf.game.soniccd.special;

import com.openggf.game.soniccd.CdDiscDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native check of the frame loop: the session, driven only by frames (the demo supplies the
 * input as natively), runs its updates on the native frames and shows the native floor. In this
 * harness the screenshot of frame F shows the state its own V-blank set: it matches the sprite
 * table dumped after frame F exactly and differs from the one dumped after F - 1. So the
 * screenshot of native frame F is paired with what the session shows after the matching frame.
 * Compared pixels are the floor rows 128-223 where our floor is opaque and no native sprite
 * covers the pixel (sprite boxes from the native sprite table at VRAM $D400,
 * {@code StageVDPRegs}, scddisasm/src/Special Stage/Main.asm:3501), coloured with the native palette line 1 of that frame.
 */
class TestCdSpecialStageSessionNative {
    private static CdSpecialStageAssets assets;

    @BeforeAll
    static void loadDisc() throws Exception {
        try (CdDiscDataSource disc = CdSpecialNative.disc()) {
            assets = CdSpecialStageAssets.load(disc);
        }
    }

    static java.util.stream.Stream<CdSpecialNative.Case> cases() { return CdSpecialNative.cases(); }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void updatesRunOnNativeFramesAndTheScreenShowsTheNativeFloor(CdSpecialNative.Case c) throws Exception {
        Path run = CdSpecialNative.run(c.run());
        Map<Integer, Map<String, String>> frames = CdSpecialNative.frames(run);
        List<Integer> nativeUpdates = CdSpecialNative.updates(frames, c.fromFrame());
        List<Integer> shots = new ArrayList<>(CdSpecialNative.dumpFrames(run, "cram"));
        shots.removeIf(f -> f < nativeUpdates.get(1) || f > nativeUpdates.getLast() + 2 || !Files.isRegularFile(run.resolve(String.format("postframe-%05d.ppm", f))));
        assertTrue(shots.size() >= 15, "comparable screenshots " + shots);

        CdSpecialStageSession session = new CdSpecialStageSession(assets, c.entry());
        // Session frame k (1-based) is native frame updates[1] + k - (fade + 1): the fade, then update 1.
        List<Integer> updateFrames = new ArrayList<>();
        Map<Integer, byte[]> shown = new HashMap<>();
        int sessionFrame = 0;
        int seen = session.stage().updates();
        int lastNative = nativeUpdates.getLast() + 2;
        while (!session.ended() && sessionFrame < 100_000
                && (c.ends() || nativeUpdates.get(1) + sessionFrame - CdSpecialStageSession.FADE_IN_FRAMES - 1 < lastNative)) {
            int nativeFrameNext = nativeUpdates.get(1) + (sessionFrame + 1) - (CdSpecialStageSession.FADE_IN_FRAMES + 1);
            session.frame(c.demo() ? 0 : CdSpecialNative.pad(run, nativeFrameNext));
            sessionFrame++;
            if (session.stage().updates() != seen) {
                seen = session.stage().updates();
                updateFrames.add(sessionFrame);
            }
            if (!updateFrames.isEmpty()) {
                // Update 1 runs at the end of the fade; its pass's first V-blank is the next frame.
                int nativeFrame = nativeUpdates.get(1) + (sessionFrame - updateFrames.getFirst() - 1);
                if (shots.contains(nativeFrame)) shown.put(nativeFrame, session.displayedFloor().clone());
            }
        }
        if (c.ends()) assertTrue(session.ended(), "the session ends");
        assertTrue(updateFrames.size() >= nativeUpdates.size() - 1, "loop updates");
        for (int k = 1; k < nativeUpdates.size(); k++) {
            assertEquals(nativeUpdates.get(k) - nativeUpdates.get(1), updateFrames.get(k - 1) - updateFrames.getFirst(),
                    "update " + k + " frame offset");
        }

        int comparedTotal = 0;
        for (int f : shots) {
            byte[] floor = shown.get(f);
            assertTrue(floor != null, "no session frame for native frame " + f);
            int[][] screen = ppm(run.resolve(String.format("postframe-%05d.ppm", f)));
            byte[] cram = CdSpecialNative.dump(run, "cram", f);
            boolean[][] sprite = spriteMask(CdSpecialNative.dump(run, "vram", f));
            int compared = 0;
            for (int y = 0; y < CdStampFloor.IMAGE_HEIGHT; y++) {
                for (int x = 0; x < CdStampFloor.IMAGE_WIDTH; x++) {
                    int index = floor[y * CdStampFloor.IMAGE_WIDTH + x];
                    if (index == 0 || sprite[128 + y][x]) continue;
                    int word = CdSpecialNative.word(cram, 2 * (16 + index));
                    int expected = ((word >> 1) & 7) << 6 | ((word >> 5) & 7) << 3 | ((word >> 9) & 7);
                    assertEquals(expected, level(screen[128 + y][x]), "native frame " + f + " pixel " + x + "," + (128 + y));
                    compared++;
                }
            }
            comparedTotal += compared;
        }
        assertTrue(comparedTotal > 100_000, "compared " + comparedTotal);
    }

    private static int level(int rgb) {
        return Math.round(((rgb >> 16) & 0xFF) / 34f) << 6 | Math.round(((rgb >> 8) & 0xFF) / 34f) << 3
                | Math.round((rgb & 0xFF) / 34f);
    }

    private static int[][] ppm(Path file) throws Exception {
        byte[] data = Files.readAllBytes(file);
        int at = 0, fields = 0;
        int[] header = new int[3];
        at = 2;
        while (fields < 3) {
            while (Character.isWhitespace(data[at])) at++;
            int value = 0;
            while (!Character.isWhitespace(data[at])) value = value * 10 + (data[at++] - '0');
            header[fields++] = value;
        }
        at++;
        int width = header[0], height = header[1];
        int[][] pixels = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++, at += 3) {
                pixels[y][x] = ((data[at] & 0xFF) << 16) | ((data[at + 1] & 0xFF) << 8) | (data[at + 2] & 0xFF);
            }
        }
        return pixels;
    }

    /** Boxes of the native sprite list (link order from entry 0) on the 256 x 224 screen. */
    private static boolean[][] spriteMask(byte[] vram) {
        boolean[][] mask = new boolean[224][256];
        int entry = 0;
        for (int n = 0; n < 80; n++) {
            int at = 0xD400 + entry * 8;
            int y = (CdSpecialNative.word(vram, at) & 0x3FF) - 128;
            int size = vram[at + 2] & 0xFF;
            int link = vram[at + 3] & 0x7F;
            int x = (CdSpecialNative.word(vram, at + 6) & 0x1FF) - 128;
            int w = (((size >> 2) & 3) + 1) * 8, h = ((size & 3) + 1) * 8;
            for (int yy = Math.max(0, y); yy < Math.min(224, y + h); yy++) {
                for (int xx = Math.max(0, x); xx < Math.min(256, x + w); xx++) mask[yy][xx] = true;
            }
            if (link == 0) break;
            entry = link;
        }
        return mask;
    }
}
