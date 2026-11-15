package com.openggf.game.sonic3k.ending;

import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.internal.NativeArenaCameraFraming;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.RgbaImage;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The two endings in a picture wider than the cartridge's: the same native window in the
 * middle, the scene continued beside it, the same script on every frame, and nothing that
 * waits or turns outside the native screen showing in the margins.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingWidescreen {
    private static final int NATIVE = 320, HEIGHT = 224;

    /** Pictures of one run, by frame: tile planes only, and complete with sprites. */
    private record Run(int width, Map<Integer, RgbaImage> planes, Map<Integer, RgbaImage> complete,
                       List<String> script, Map<String, Integer> marks) {
        int inset() { return (width - NATIVE) / 2; }
    }

    // Sonic's good ending: the internal level $0D01 with seven Chaos Emeralds, neutral input.
    private static final int PARKED = 200, CLOSE_SHOT = 1300, ISLAND = 2400, RISING = 3300, SONIC_FRAMES = 3400;
    private static final int PAN_PASSES = 6;

    private static Run sonic(int width) throws Exception {
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null, null, null,
                "1111111", false, false, null, null);
        var run = new Run(width, new HashMap<>(), new HashMap<>(), new ArrayList<>(), new HashMap<>());
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 13, 1, settings);
            for (int frame = 0; frame < SONIC_FRAMES; frame++) {
                session.step(null);
                var zone = (EndingZoneRuntimeState) GameServices.zoneRuntimeState();
                var ram = zone.screen();
                run.script().add(session.stateLine(frame, null) + "," + ram.foregroundRoutine + ","
                        + ram.backgroundRoutine + "," + ram.scrollXFixed + "," + ram.scrollYFixed + ","
                        + GameServices.level().getObjectManager().getActiveObjects().size());
                // loc_5AB36 parks Plane A at $140; six passes on, the pan has drawn no column
                // inside the native window yet.
                if (ram.foregroundRoutine == 4) run.marks().putIfAbsent("pan", frame + PAN_PASSES);
                if (frame == PARKED || frame == CLOSE_SHOT || frame == ISLAND || frame == RISING
                        || Integer.valueOf(frame).equals(run.marks().get("pan"))) {
                    run.planes().put(frame, session.render(false));
                    run.complete().put(frame, session.render());
                } else if (frame % 16 == 0) {
                    session.renderFrame();
                }
                if (frame == ISLAND && width > NATIVE) assertSceneFillsThePicture(zone);
            }
        }
        return run;
    }

    private static void assertSceneFillsThePicture(EndingZoneRuntimeState zone) {
        var framing = (NativeArenaCameraFraming) GameServices.level().getZoneFeatureProvider();
        var camera = GameServices.camera();
        assertTrue(framing.ownsWidescreenMargins(), "the rescue fills the picture");
        assertTrue(framing.presentationNativeHorizontalOrigin().isEmpty(), "no native-window mask");
        // Its closing art on black is 320 pixels with nothing beside it: back between the bars.
        zone.ending().setFlag(3);
        try {
            assertFalse(framing.ownsWidescreenMargins());
            assertEquals(camera.getXWithShake() + EndingWideStage.margin(camera),
                    framing.presentationNativeHorizontalOrigin().orElseThrow());
        } finally {
            zone.ending().clearFlag(3);
        }
    }

    @Test void sonicsEndingFillsAWiderPictureAroundTheSameNativeWindow() throws Exception {
        Run cartridge = sonic(NATIVE), wide = sonic(400), widest = sonic(800);
        int pan = cartridge.marks().get("pan");
        for (Run run : List.of(wide, widest)) {
            assertEquals(cartridge.script(), run.script(), "the script runs the same at " + run.width());
            for (int frame : List.of(PARKED, CLOSE_SHOT, ISLAND, RISING, pan))
                assertNativeWindow(cartridge.planes().get(frame), run.planes().get(frame), run.inset(),
                        "tile planes, frame " + frame + " at " + run.width());
            // Nothing but the island's own sprites is on screen here: the whole picture agrees.
            assertNativeWindow(cartridge.complete().get(ISLAND), run.complete().get(ISLAND), run.inset(),
                    "complete picture, frame " + ISLAND + " at " + run.width());

            // The Tornado waits 64 pixels left of the cartridge's screen: left of this one too.
            assertMarginsHoldNoSprite(run, PARKED);

            // The island comes in from the real edge: it is in the margin before the native window.
            Set<Integer> island = islandColours(cartridge.planes().get(ISLAND));
            assertFalse(island.isEmpty(), "the island has colours of its own");
            var panning = run.planes().get(pan);
            assertEquals(0, count(panning, island, run.inset(), run.inset() + NATIVE),
                    "the island has not reached the native window on frame " + pan);
            assertTrue(count(panning, island, run.inset() + NATIVE, run.width()) >= 8,
                    "the island is already in the right margin at " + run.width());
            assertEquals(0, count(panning, island, 0, run.inset()), "and nowhere on the left");
        }
        // Both widths show the same thing beside the native window.
        for (int frame : List.of(PARKED, CLOSE_SHOT, ISLAND, RISING, pan))
            assertNativeWindow(wide.planes().get(frame), widest.planes().get(frame), 200, 400,
                    "400 against the middle of 800, frame " + frame);
        // The sea is Plane B: one 512-pixel image scrolled line by line, repeated as on the VDP.
        var sea = widest.planes().get(ISLAND);
        for (int y = 192; y < HEIGHT; y++) for (int x = 0; x < 800 - 512; x++)
            assertEquals(sea.argb(x, y), sea.argb(x + 512, y), "sea repeats at " + x + "," + y);
    }

    // Knuckles' best ending: Sky Sanctuary act 2 from the engine's own final-fight movie.
    private static final int THUMBS_UP = 9150, FLOWN_OFF = 9300, CREDITS = 12000, KNUCKLES_FRAMES = 12001;

    private static Run knuckles(int width) throws Exception {
        Bk2Movie movie = new Bk2MovieLoader().loadMovieOrInputLog(
                Path.of("src/test/resources/routes/s3k/ssz2-final-defeat.bk2"));
        var settings = new GameplayCaptureSession.Settings(width, "knuckles", "", "off", null, null, null,
                "3333333", false, false, 410766, null);
        var run = new Run(width, new HashMap<>(), new HashMap<>(), new ArrayList<>(), new HashMap<>());
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 1, settings);
            for (int frame = 0; frame < KNUCKLES_FRAMES; frame++) {
                var input = frame < movie.getFrameCount() ? movie.getFrame(frame) : null;
                session.step(input);
                var ssz = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
                // Everything but the camera's X, which a wider picture centres on the arena.
                String[] state = session.stateLine(frame, null).split(",", -1);
                state[13] = "";
                run.script().add(String.join(",", state) + "," + ssz.foregroundRoutine());
                if (frame == THUMBS_UP || frame == FLOWN_OFF || frame == CREDITS) {
                    run.planes().put(frame, session.render(false));
                    run.complete().put(frame, session.render());
                } else if (frame % 32 == 0) {
                    session.renderFrame();
                }
                if (frame == THUMBS_UP && width > NATIVE) {
                    var framing = (NativeArenaCameraFraming) GameServices.level().getZoneFeatureProvider();
                    var camera = GameServices.camera();
                    assertEquals(camera.getMinX(), camera.getMaxX(), "the arena's native bounds are untouched");
                    assertTrue(framing.ownsWidescreenMargins(), "the flight fills the picture all the same");
                }
            }
        }
        return run;
    }

    @Test void knucklesEndingFillsAWiderPictureAroundTheSameIsland() throws Exception {
        Run cartridge = knuckles(NATIVE), wide = knuckles(400), widest = knuckles(800);
        var nativeFlight = cartridge.planes().get(THUMBS_UP);
        Set<Integer> island = islandColours(nativeFlight);
        int[] nativeSpan = span(nativeFlight, island, 0, NATIVE);
        assertTrue(nativeSpan[1] - nativeSpan[0] > 150, "the island is found in the cartridge's picture");
        for (Run run : List.of(wide, widest)) {
            assertEquals(cartridge.script(), run.script(), "the script runs the same at " + run.width());
            // The whole island, where the cartridge puts it in the native window.
            int[] span = span(run.planes().get(THUMBS_UP), island, 0, run.width());
            assertEquals(nativeSpan[0] + run.inset(), span[0], "island's left end at " + run.width());
            assertEquals(nativeSpan[1] + run.inset(), span[1], "island's right end at " + run.width());
            // The credits scene: island plane, text plane and backdrop agree pixel for pixel.
            assertNativeWindow(cartridge.planes().get(CREDITS), run.planes().get(CREDITS), run.inset(),
                    "credits tile planes at " + run.width());
        }
        // The Tornado has flown out to the right and not yet turned: beyond this picture's edge too.
        assertMarginsHoldNoSprite(widest, FLOWN_OFF);
    }

    private static void assertNativeWindow(RgbaImage cartridge, RgbaImage wide, int inset, String what) {
        assertNativeWindow(cartridge, wide, inset, NATIVE, what);
    }

    /** {@code wide}'s columns from {@code inset} are {@code narrow}, pixel for pixel. */
    private static void assertNativeWindow(RgbaImage narrow, RgbaImage wide, int inset, int columns, String what) {
        assertNotNull(narrow, what);
        assertNotNull(wide, what);
        int different = 0;
        String first = null;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < columns; x++)
            if (narrow.argb(x, y) != wide.argb(x + inset, y)) {
                if (first == null) first = x + "," + y;
                different++;
            }
        assertEquals(0, different, what + ": first difference at " + first);
    }

    private static void assertMarginsHoldNoSprite(Run run, int frame) {
        var planes = run.planes().get(frame);
        var complete = run.complete().get(frame);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < run.width(); x++) {
            if (x >= run.inset() && x < run.inset() + NATIVE) continue;
            assertEquals(planes.argb(x, y), complete.argb(x, y),
                    "a sprite shows in the margin at " + x + "," + y + ", frame " + frame + " at " + run.width());
        }
    }

    /**
     * Colours of the island's rock and grass: those in the middle of the native picture that
     * the sky, sea and cloud of its outer columns never use.
     */
    private static Set<Integer> islandColours(RgbaImage picture) {
        Set<Integer> elsewhere = new HashSet<>(), island = new HashSet<>();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < 30; x++) elsewhere.add(picture.argb(x, y));
            for (int x = 296; x < NATIVE; x++) elsewhere.add(picture.argb(x, y));
        }
        for (int y = 60; y < 165; y++) for (int x = 100; x < 220; x++)
            if (!elsewhere.contains(picture.argb(x, y))) island.add(picture.argb(x, y));
        return island;
    }

    private static int count(RgbaImage picture, Set<Integer> colours, int from, int to) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = from; x < to; x++)
            if (colours.contains(picture.argb(x, y))) count++;
        return count;
    }

    /** First and last column holding one of the colours. */
    private static int[] span(RgbaImage picture, Set<Integer> colours, int from, int to) {
        int first = Integer.MAX_VALUE, last = Integer.MIN_VALUE;
        for (int y = 0; y < HEIGHT; y++) for (int x = from; x < to; x++)
            if (colours.contains(picture.argb(x, y))) {
                first = Math.min(first, x);
                last = Math.max(last, x);
            }
        return new int[] {first, last};
    }
}
