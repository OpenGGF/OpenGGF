package com.openggf.game.soniccd.special;

import com.openggf.game.soniccd.CdDiscDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whole-screen native pixel check: the session's picture (both planes, sprites, palette) is
 * composed as the display layers it (backdrop, then low B, A, sprites, then high B, A, sprites;
 * sprites in list order, at most 16 per line in H32) and every pixel is compared with the native
 * screenshot of the same frame, for every screenshot of the stage loop. The backdrop is palette
 * entry $20 ({@code StageVDPRegs} register 7, scddisasm/src/Special Stage/Main.asm:3503). One named mask: HUD cells the
 * main loop rewrites during the compared frame ({@code DrawRingCount}, {@code DrawUFOCount},
 * {@code DrawTimer} write plane A while the frame is on screen, so native shows a raster-dependent
 * mix of old and new rows there, e.g. SS1 frame 2245 lines 15-17 of the UFO digit).
 */
class TestCdSpecialStageScreenshotNative {
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
    void everyScreenshotOfTheLoopEqualsNative(CdSpecialNative.Case c) throws Exception {
        Path run = CdSpecialNative.run(c.run());
        Map<Integer, Map<String, String>> frames = CdSpecialNative.frames(run);
        List<Integer> updates = CdSpecialNative.updates(frames, c.fromFrame());
        CdSpecialStageSession session = new CdSpecialStageSession(assets, c.entry());
        int first = CdSpecialStageSession.FADE_IN_FRAMES + 1;
        List<String> failures = new ArrayList<>();
        int compared = 0;
        for (int k = 1; !session.ended() && updates.get(1) + k - first <= updates.getLast() + 2; k++) {
            int nativeFrame = updates.get(1) + k - first;
            int[] hudBefore = java.util.Arrays.copyOfRange(session.screen().planeA, 64, 64 * 3);
            session.frame(c.demo() ? 0 : CdSpecialNative.pad(run, nativeFrame));
            Path shot = run.resolve(String.format("postframe-%05d.ppm", nativeFrame));
            if (nativeFrame < updates.get(1) || session.ended() || !Files.isRegularFile(shot)) continue;
            int[] ours = compose(session.screen());
            int[] theirs = ppmLevels(shot);
            int bad = 0, minX = 999, minY = 999, maxX = -1, maxY = -1;
            int[] hudAfter = java.util.Arrays.copyOfRange(session.screen().planeA, 64, 64 * 3);
            for (int i = 0; i < ours.length; i++) {
                if (ours[i] == theirs[i]) continue;
                int row = i / 256 / 8 - 1, col = i % 256 / 8;
                if (row >= 0 && row < 2 && hudBefore[row * 64 + col] != hudAfter[row * 64 + col]) continue;
                bad++;
                minX = Math.min(minX, i % 256); maxX = Math.max(maxX, i % 256);
                minY = Math.min(minY, i / 256); maxY = Math.max(maxY, i / 256);
            }
            if (bad > 0) failures.add("frame " + nativeFrame + ": " + bad + " px in x " + minX + "-" + maxX + ", y " + minY + "-" + maxY);
            compared++;
        }
        assertTrue(compared >= 30, "screenshots compared " + compared);
        assertEquals(List.of(), failures);
    }

    static int level(int cramWord) {
        return ((cramWord >> 1) & 7) << 6 | ((cramWord >> 5) & 7) << 3 | ((cramWord >> 9) & 7);
    }

    /** The displayed picture as palette levels, layered by priority. */
    static int[] compose(CdSpecialStageScreen screen) {
        int[] out = new int[256 * 224];
        if (!screen.display) {
            return out;
        }
        int[] spriteColor = new int[256];
        boolean[] spriteHigh = new boolean[256];
        for (int y = 0; y < 224; y++) {
            java.util.Arrays.fill(spriteColor, -1);
            int perLine = 0;
            for (CdSpecialSprites.Entry e : screen.spriteTable) {
                int sy = (e.y() & 0x3FF) - 128, sx = (e.x() & 0x1FF) - 128;
                int w = ((e.size() >> 2) & 3) + 1, h = (e.size() & 3) + 1;
                if (y < sy || y >= sy + h * 8) continue;
                if (++perLine > 16) break;
                boolean hf = (e.tile() & 0x800) != 0, vf = (e.tile() & 0x1000) != 0;
                int py = y - sy;
                if (vf) py = h * 8 - 1 - py;
                for (int px = 0; px < w * 8; px++) {
                    int x = sx + px;
                    if (x < 0 || x >= 256 || spriteColor[x] >= 0) continue;
                    int qx = hf ? w * 8 - 1 - px : px;
                    int tile = ((e.tile() & 0x7FF) + (qx >> 3) * h + (py >> 3)) & 0x7FF;
                    int c = pixel(screen.tiles, tile, qx & 7, py & 7);
                    if (c == 0) continue;
                    spriteColor[x] = ((e.tile() >> 13) & 3) * 16 + c;
                    spriteHigh[x] = (e.tile() & 0x8000) != 0;
                }
            }
            for (int x = 0; x < 256; x++) {
                int a = plane(screen, screen.planeA, screen.scrollA[y], x, y);
                int b = plane(screen, screen.planeB, screen.scrollB[y], x, y);
                int s = spriteColor[x];
                int color;
                if (s >= 0 && spriteHigh[x]) color = s;
                else if (a >= 0 && (a & 0x100) != 0) color = a & 0xFF;
                else if (b >= 0 && (b & 0x100) != 0) color = b & 0xFF;
                else if (s >= 0) color = s;
                else if (a >= 0) color = a & 0xFF;
                else if (b >= 0) color = b & 0xFF;
                else color = 0x20;
                out[y * 256 + x] = level(screen.cram[color]);
            }
        }
        return out;
    }

    /** A plane pixel: colour index with bit 8 for priority, or -1 when transparent. */
    private static int plane(CdSpecialStageScreen screen, int[] map, int scroll, int x, int y) {
        int px = (x - scroll) & 511;
        int word = map[(y >> 3) * 64 + (px >> 3)];
        int tx = px & 7, ty = y & 7;
        if ((word & 0x800) != 0) tx = 7 - tx;
        if ((word & 0x1000) != 0) ty = 7 - ty;
        int c = pixel(screen.tiles, word & 0x7FF, tx, ty);
        if (c == 0) return -1;
        return ((word >> 13) & 3) * 16 + c | ((word & 0x8000) != 0 ? 0x100 : 0);
    }

    private static int pixel(byte[] tiles, int tile, int x, int y) {
        int b = tiles[tile * 32 + y * 4 + (x >> 1)] & 0xFF;
        return (x & 1) == 0 ? b >> 4 : b & 15;
    }

    static int[] ppmLevels(Path file) throws Exception {
        byte[] data = Files.readAllBytes(file);
        int at = 2, fields = 0;
        int[] header = new int[3];
        while (fields < 3) {
            while (Character.isWhitespace(data[at])) at++;
            int value = 0;
            while (!Character.isWhitespace(data[at])) value = value * 10 + (data[at++] - '0');
            header[fields++] = value;
        }
        at++;
        int[] out = new int[header[0] * header[1]];
        for (int i = 0; i < out.length; i++, at += 3) {
            out[i] = Math.round((data[at] & 0xFF) / 34f) << 6 | Math.round((data[at + 1] & 0xFF) / 34f) << 3
                    | Math.round((data[at + 2] & 0xFF) / 34f);
        }
        return out;
    }
}
