package com.openggf.game.soniccd.special;

import com.openggf.game.soniccd.CdDiscDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native check of the special stage floor against the retail game running Special Stage 1
 * from the stage-select menu (run {@code ss1-demo}; route and frames in its README). The
 * dumps are the sub-CPU's Word RAM and PRG RAM after the stated frames; the Word RAM addresses
 * used to read them (stamps $200, trace table $1C800, image $1D000, map $20000;
 * Special Stage/_Common.inc:11-13, Sub.asm:486-493) locate the reference only.
 *
 * <p>An update is a frame where the sub-CPU's timer frame counter changed (it changes on every
 * update, {@code UpdateTimer}, scddisasm/src/Special Stage/Sub.asm:1724-1727). The graphics operation finishes before the
 * sub-CPU hands Word RAM back ({@code .WaitGfx}, Sub.asm:305-309), which can fall after the
 * timer step's frame (it does for the first loop update), so the image is compared on the frame
 * after the update; one frame later the next operation is already rewriting it.
 */
class TestCdSpecialStageFloorNative {
    private static final int TRACE = 0x1C800;
    private static final int IMAGE = 0x1D000;
    private static final int MAP = 0x20000;
    private static final int STAMPS = 0x200;
    private static final int GFX = 0x3E400;

    private static CdSpecialStageAssets assets;
    private static Path run;
    private static Map<Integer, Map<String, String>> frames;
    private static List<Integer> updates;
    private static int stage;

    @BeforeAll
    static void loadDisc() throws Exception {
        try (CdDiscDataSource disc = CdSpecialNative.disc()) {
            assets = CdSpecialStageAssets.load(disc);
        }
    }

    static java.util.stream.Stream<CdSpecialNative.Case> cases() { return CdSpecialNative.cases(); }

    private static void use(CdSpecialNative.Case c) throws Exception {
        run = CdSpecialNative.run(c.run());
        frames = CdSpecialNative.frames(run);
        updates = CdSpecialNative.updates(frames, c.fromFrame());
        stage = c.stage();
    }

    private static int lastUpdate(int frame) {
        int last = -1;
        for (int u : updates) if (u <= frame) last = u;
        return last;
    }

    private static List<Integer> settledFrames() throws Exception {
        List<Integer> settled = new ArrayList<>();
        for (int f : CdSpecialNative.dumpFrames(run, "wordram")) {
            if (f <= updates.getFirst()) continue;
            int u = lastUpdate(f);
            if (u > 0 && f - u == 1) settled.add(f);
        }
        return settled;
    }

    private static CdStampTrace nativeTrace(byte[] wordRam) {
        CdStampTrace trace = new CdStampTrace();
        for (int line = 0; line < CdStampFloor.IMAGE_HEIGHT; line++) {
            int at = TRACE + line * 8;
            trace.set(line, CdSpecialNative.word(wordRam, at), CdSpecialNative.word(wordRam, at + 2),
                    CdSpecialNative.word(wordRam, at + 4), CdSpecialNative.word(wordRam, at + 6));
        }
        return trace;
    }

    /** The native image buffer (8 x 8 cells down each 12-cell column) as row-major pixels. */
    private static byte[] nativeImage(byte[] wordRam) {
        byte[] pixels = new byte[CdStampFloor.IMAGE_WIDTH * CdStampFloor.IMAGE_HEIGHT];
        for (int y = 0; y < CdStampFloor.IMAGE_HEIGHT; y++) {
            for (int x = 0; x < CdStampFloor.IMAGE_WIDTH; x++) {
                int cell = (x >> 3) * 12 + (y >> 3);
                int b = wordRam[IMAGE + cell * 32 + (y & 7) * 4 + (x & 7) / 2] & 0xFF;
                pixels[y * CdStampFloor.IMAGE_WIDTH + x] = (byte) ((x & 1) == 0 ? b >> 4 : b & 15);
            }
        }
        return pixels;
    }

    /** A floor carrying the native map of a dump, so a render check uses native inputs only. */
    private static CdStampFloor floorWithMap(byte[] wordRam) throws Exception {
        CdStampFloor floor = assets.floor(stage);
        short[] map = floor.mapView();
        for (int i = 0; i < map.length; i++) map[i] = (short) CdSpecialNative.word(wordRam, MAP + 2 * i);
        return floor;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void stampArtFromTheDiscEqualsNativeWordRam(CdSpecialNative.Case c) throws Exception {
        use(c);
        byte[] wordRam = CdSpecialNative.dump(run, "wordram", settledFrames().getFirst());
        byte[] stamps = assets.kosinski(stage == 2 ? "Stamps_SS3_1" : "Stamps_SS" + (stage + 1));
        assertArrayEquals(stamps, Arrays.copyOfRange(wordRam, STAMPS, STAMPS + stamps.length));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void stampMapAnimatesAsNativeOnEverySettledFrame(CdSpecialNative.Case c) throws Exception {
        use(c);
        List<Integer> settled = settledFrames();
        assertTrue(settled.size() >= 15, "settled frames " + settled);
        for (int f : settled) {
            CdStampFloor floor = assets.floor(stage);
            int count = (int) updates.stream().filter(u -> u <= f).count();
            for (int i = 0; i < count; i++) floor.animate();
            byte[] wordRam = CdSpecialNative.dump(run, "wordram", f);
            short[] map = floor.mapView();
            for (int i = 0; i < map.length; i++) {
                assertEquals(CdSpecialNative.word(wordRam, MAP + 2 * i), map[i] & 0xFFFF, "frame " + f + " cell " + i);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void floorRenderedFromTheNativeTraceEqualsTheNativeImage(CdSpecialNative.Case c) throws Exception {
        use(c);
        int checked = 0;
        for (int f : settledFrames()) {
            byte[] wordRam = CdSpecialNative.dump(run, "wordram", f);
            byte[] image = new byte[CdStampFloor.IMAGE_WIDTH * CdStampFloor.IMAGE_HEIGHT];
            floorWithMap(wordRam).render(nativeTrace(wordRam), image);
            assertArrayEquals(nativeImage(wordRam), image, "floor pixels at frame " + f);
            checked++;
        }
        assertTrue(checked >= 15, "checked " + checked);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void traceFromThePreviousUpdatesCameraEqualsTheNativeTrace(CdSpecialNative.Case c) throws Exception {
        use(c);
        short[] sine = assets.sineTable();
        int moving = 0;
        for (int f : settledFrames()) {
            int u = lastUpdate(f);
            Map<String, String> before = frames.get(u - 1);
            CdSpecialStageView view = new CdSpecialStageView();
            view.setCamera(CdSpecialNative.hex(before, "cam_x"), CdSpecialNative.hex(before, "cam_y"),
                    CdSpecialNative.hex(before, "cam_z"), CdSpecialNative.hex(before, "pitch"), CdSpecialNative.hex(before, "yaw"));
            view.takeSines(sine);
            byte[] prg = CdSpecialNative.dump(run, "prgram", f);
            // GetGfxSines ran at the start of update u from the same pitch and yaw.
            assertEquals(CdSpecialNative.word(prg, GFX + 8), view.pitchSin() & 0xFFFF, "sin(pitch) at " + f);
            assertEquals(CdSpecialNative.word(prg, GFX + 14), view.yawSin() & 0xFFFF, "sin(yaw) at " + f);
            assertEquals(CdSpecialNative.word(prg, GFX + 16), view.yawCos() & 0xFFFF, "cos(yaw) at " + f);
            CdStampTrace trace = new CdStampTrace();
            trace.generate(view);
            CdStampTrace expected = nativeTrace(CdSpecialNative.dump(run, "wordram", f));
            for (int line = 0; line < CdStampFloor.IMAGE_HEIGHT; line++) {
                String at = "frame " + f + " line " + line;
                assertEquals(expected.x(line), trace.x(line), at);
                assertEquals(expected.y(line), trace.y(line), at);
                assertEquals(expected.dx(line), trace.dx(line), at);
                assertEquals(expected.dy(line), trace.dy(line), at);
            }
            Map<String, String> now = frames.get(u), was = frames.get(u - 1);
            if (!now.get("yaw").equals(was.get("yaw")) || !now.get("cam_x").equals(was.get("cam_x"))
                    || !now.get("cam_y").equals(was.get("cam_y"))) moving++;
        }
        assertTrue(moving >= 3, "frames with a moving or rotating camera checked " + moving);
    }

    /** Negative control: the same comparison rejects a camera one pixel off. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void aCameraOnePixelOffIsRejected(CdSpecialNative.Case c) throws Exception {
        use(c);
        int f = settledFrames().getLast();
        Map<String, String> before = frames.get(lastUpdate(f) - 1);
        CdSpecialStageView view = new CdSpecialStageView();
        view.setCamera(CdSpecialNative.hex(before, "cam_x") + 1, CdSpecialNative.hex(before, "cam_y"),
                CdSpecialNative.hex(before, "cam_z"), CdSpecialNative.hex(before, "pitch"), CdSpecialNative.hex(before, "yaw"));
        view.takeSines(assets.sineTable());
        CdStampTrace trace = new CdStampTrace();
        trace.generate(view);
        byte[] wordRam = CdSpecialNative.dump(run, "wordram", f);
        byte[] image = new byte[CdStampFloor.IMAGE_WIDTH * CdStampFloor.IMAGE_HEIGHT];
        floorWithMap(wordRam).render(trace, image);
        assertFalse(Arrays.equals(nativeImage(wordRam), image), "an offset camera must not reproduce the native floor");
    }
}
