package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Native check for the Wacky Workbench presentation of all ten stages: the production renderer (scroll handler,
 * palette cycle through the shared palette owner, and the animated-tile manager stepped once per level frame as the
 * engine would) against BizHawk screenshots of the stage run: the fixture's early shot (idle, nothing pressed, about
 * 180 level frames in) and four more taken from the same movie while the camera moves right: at camera X about
 * $80, $100 and $180 ({@code mid1}-{@code mid3}) and at the last trace row ({@code late}). The extra shots are made
 * with {@code rehost-fixtures/_tools/probe.sh} and live in {@code native/rehost-fixtures-wwz/<payload>/} (see its
 * {@code mid.json}); the test skips when they or the fixtures are absent.
 *
 * <p>The test projects the recorded camera path to isolate the presentation from player movement. R61A and R61C
 * replay the controller movie so their platforms and animals see the previous camera during the object pass.
 * The probe run's level counter pairs its picture with the preceding completed iteration. Some probe runs
 * advance a different number of iterations than their separately recorded physics traces.
 *
 * <p>The HUD, Sonic in both pictures, and the areas around layout placements and ring lines are masked.
 * The trace carries no background-camera globals, so the test checks the background camera through the picture.
 */
@EnabledOnOs(OS.LINUX)
class TestWwzNativePictures {
    private static final CdStagePayload[] PAYLOADS = {
            CdStagePayload.R61A, CdStagePayload.R61B, CdStagePayload.R61C, CdStagePayload.R61D,
            CdStagePayload.R62A, CdStagePayload.R62B, CdStagePayload.R62C, CdStagePayload.R62D,
            CdStagePayload.R63C, CdStagePayload.R63D};
    private static final int PLACEMENT_RADIUS = 32;
    private static final int RING_RADIUS = 16;

    private static final Set<CdStagePayload> MOVIE_PAYLOADS = Set.of(CdStagePayload.R61A, CdStagePayload.R61C);

    private record ProbeFrame(CdStagePayload payload, int frame) { }

    // Probe counters measured by rerunning the stage schedules and checking the resulting PNGs against mid.json.
    private static final Map<ProbeFrame, Integer> CAPTURE_COUNTERS = Map.of(
            new ProbeFrame(CdStagePayload.R62B, 2822), 307,
            new ProbeFrame(CdStagePayload.R62B, 2873), 358,
            new ProbeFrame(CdStagePayload.R62B, 2884), 369,
            new ProbeFrame(CdStagePayload.R62B, 3331), 813,
            new ProbeFrame(CdStagePayload.R63D, 2771), 306,
            new ProbeFrame(CdStagePayload.R63D, 2792), 327,
            new ProbeFrame(CdStagePayload.R63D, 2814), 349,
            new ProbeFrame(CdStagePayload.R63D, 3276), 811);

    private record CameraClock(int firstTraceFrame, int captureFrameOffset) { }

    // Match the physics trace's camera samples to the probe run's level clock. Both changes in clock
    // alignment occur while the camera is stationary. R62B trace level 725 is probe level 722.
    private static final Map<CdStagePayload, List<CameraClock>> PROBE_CAMERA_CLOCKS = Map.of(
            CdStagePayload.R62B, List.of(new CameraClock(1, -1), new CameraClock(725, -3)),
            CdStagePayload.R63D, List.of(new CameraClock(1, 1)));

    // ObjRing_Deltas (Level/_Objects/Ring/Main.asm:17-32): x and y step of a ring line by subtype high nibble.
    private static final int[] RING_DELTAS = {
            0x10, 0, 0x18, 0, 0x20, 0, 0, 0x10, 0, 0x18, 0, 0x20, 0x10, 0x10, 0x18, 0x18,
            0x20, 0x20, -0x10, 0x10, -0x18, 0x18, -0x20, 0x20, 0x10, 8, 0x18, 0x10, -0x10, 8, -0x18, 0x10};
    private static final int RING_OBJECT_ID = 0x10;          // Object Index: 10 - Ring

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** One trace row. */
    private record Sample(int levelFrames, int cameraX, int cameraY, int playerX, int playerY) { }

    private static List<Sample> samples(CdStagePayload payload) throws Exception {
        Path file = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/physics.csv");
        List<String> lines = Files.readAllLines(file);
        String[] header = lines.get(0).split(",");
        int camX = indexOf(header, "camera_x");
        int camY = indexOf(header, "camera_y");
        int counter = indexOf(header, "gameplay_frame_counter");
        int px = indexOf(header, "player_x");
        int py = indexOf(header, "player_y");
        List<Sample> out = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",");
            out.add(new Sample(Integer.parseInt(cells[counter], 16), Integer.parseInt(cells[camX], 16),
                    Integer.parseInt(cells[camY], 16), Integer.parseInt(cells[px], 16), Integer.parseInt(cells[py], 16)));
        }
        return out;
    }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) if (header[i].equals(name)) return i;
        throw new IllegalStateException("no column " + name);
    }

    /** A native screenshot: its name, file, trace row and level-frame counter. */
    private record Shot(String name, Path file, int traceRow, int levelFrames, Integer captureCounter) { }

    private static List<Shot> shots(CdStagePayload payload) throws Exception {
        Path manifest = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json");
        JsonNode early = new ObjectMapper().readTree(manifest.toFile()).get("screenshots").get("early");
        List<Shot> out = new ArrayList<>();
        out.add(new Shot("early", CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/" + early.get("file").asText()),
                early.get("trace_row").asInt(), early.get("level_frames").asInt(), null));
        Path mid = CdTestMedia.nativeFile("rehost-fixtures-wwz/" + payload + "/mid.json");
        for (JsonNode shot : new ObjectMapper().readTree(mid.toFile()).get("shots")) {
            out.add(new Shot(shot.get("name").asText(), mid.resolveSibling(shot.get("file").asText()),
                    shot.get("trace_row").asInt(), shot.get("level_frames").asInt(),
                    CAPTURE_COUNTERS.get(new ProbeFrame(payload, shot.path("probe_frame").asInt()))));
        }
        return out;
    }

    /** The framebuffer plus what the masks need, read before the stage is replaced. */
    private record Rendered(RgbaImage image, int cameraX, int cameraY, int playerX, int playerY,
                            int enginePlayerX, int enginePlayerY, List<CdLevel.CdPlacement> placements) { }

    /**
     * Loads {@code payload} on the shared boot and captures the production renderer at each shot's engine level frame
     * ({@link #engineFrame}). The loaded stage is level frame 1, that iteration's scroll, palette cycle and tile
     * animation included ({@code CdFirstMainLoopIteration}); every later distinct {@code gameplay_frame_counter} sets
     * the native camera of that level frame and steps once, so the engine stands at level frame L after the step that
     * took the trace's camera for L. The production tile animator advances with the level clock.
     */
    private static List<Rendered> render(com.openggf.GameLoop loop, CdStagePayload payload, boolean firstBoot,
                                         List<Sample> samples, List<Shot> shots) throws Exception {
        if (!firstBoot) {
            // The headless GL context can be booted once per JVM, so later stages load through the same boot.
            // The previous stage's last camera is where the new stage would spawn its first objects: start from X 0, as a
            // stage entry does (hosted objects, such as the electric beams, would otherwise run from the first frame).
            GameServices.camera().setX((short) 0);
            GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        }
        assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
        var game = (SonicCdGame) GameServices.level().getGame();
        assertEquals(payload, game.currentLevel().payload(), "stage loaded");
        int[] targets = new int[shots.size()];
        for (int i = 0; i < shots.size(); i++) targets[i] = engineFrame(shots.get(i), samples);
        if (MOVIE_PAYLOADS.contains(payload)) {
            return replayMovie(payload, samples, targets);
        }
        samples = alignCameraClock(payload, samples);
        Rendered[] out = new Rendered[shots.size()];
        GameServices.camera().setFrozen(true);
        Sample first = samples.get(0);
        GameServices.camera().setX((short) first.cameraX());
        GameServices.camera().setY((short) first.cameraY());
        int previous = first.levelFrames();
        int captured = captureDue(previous, first, targets, out);
        for (Sample sample : samples) {
            if (captured == out.length) break;
            if (sample.levelFrames() == previous) continue;   // lag: the level did not advance
            assertEquals(previous + 1, sample.levelFrames(), payload + ": camera path level frame");
            previous = sample.levelFrames();
            GameServices.camera().setX((short) sample.cameraX());
            GameServices.camera().setY((short) sample.cameraY());
            loop.step();
            captured += captureDue(previous, sample, targets, out);
        }
        assertEquals(shots.size(), captured, payload + ": shots captured");
        return List.of(out);
    }

    private static List<Rendered> replayMovie(CdStagePayload payload, List<Sample> samples, int[] targets)
            throws Exception {
        GameServices.camera().setFrozen(false);
        String fixture = "rehost-fixtures/" + payload + "/";
        Path traceDir = CdTestMedia.nativeFile(fixture + "trace/metadata.json").getParent();
        Path movie = CdTestMedia.nativeFile(fixture + "movie.bk2");
        var runner = new HeadlessTestRunner(GameServices.camera().getFocusedSprite());
        runner.setBk2Movie(new Bk2MovieLoader().load(movie), TraceData.load(traceDir).metadata().bk2FrameOffset());
        runner.consumeRecordingFrameInputOnly();

        // SpawnObjects_Main (Level/Object Spawner.asm:107-115) reads the previous camera because
        // the object pass precedes scroll, Level_MainLoop (Level/Main.asm:397-418).
        Rendered[] out = new Rendered[targets.length];
        Sample first = samples.getFirst();
        int captured = captureDue(first.levelFrames(), first, targets, out);
        for (int row = 1; row < samples.size() && captured < targets.length; row++) {
            Sample sample = samples.get(row);
            if (sample.levelFrames() == samples.get(row - 1).levelFrames()) {
                runner.skipFrameFromRecording();
            } else {
                runner.stepFrameFromRecording();
                captured += captureDue(sample.levelFrames(), sample, targets, out);
            }
        }
        assertEquals(targets.length, captured, payload + ": movie shots captured");
        return List.of(out);
    }

    private static List<Sample> alignCameraClock(CdStagePayload payload, List<Sample> samples) {
        List<CameraClock> clock = PROBE_CAMERA_CLOCKS.get(payload);
        if (clock == null) return samples;
        var aligned = new TreeMap<Integer, Sample>();
        Sample first = samples.getFirst();
        aligned.put(first.levelFrames(), first);
        // Keep the loaded iteration when its camera sample precedes the first aligned sample.
        int previous = -1;
        int segment = 0;
        for (Sample sample : samples) {
            if (sample.levelFrames() == previous) continue;
            previous = sample.levelFrames();
            if (segment + 1 < clock.size() && previous >= clock.get(segment + 1).firstTraceFrame()) segment++;
            int frame = previous + clock.get(segment).captureFrameOffset();
            if (frame > 0) {
                aligned.put(frame, new Sample(frame, sample.cameraX(), sample.cameraY(), sample.playerX(), sample.playerY()));
            }
        }
        return List.copyOf(aligned.values());
    }

    /** Captures every shot whose engine level frame is {@code levelFrame}; returns how many. */
    private static int captureDue(int levelFrame, Sample sample, int[] targets, Rendered[] out) {
        int n = 0;
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == levelFrame && out[i] == null) {
                out[i] = capture(sample);
                n++;
            }
        }
        return n;
    }

    /**
     * The engine level frame a shot shows. A picture shows the state after the level frame before the one its row
     * counts: the V-INT that starts the video frame DMAs the palette, scroll and sprite table the previous iteration
     * wrote, VInt_Level (Level/Main.asm:821-837), and the tile animation it runs is the engine frame's own (see {@code
     * CdFirstMainLoopIteration}). The fixture's early screenshot is its row's picture: level frame lf(row)-1. A
     * {@code probe.sh} shot is the video frame after its row (probe frame {@code bk2_frame_offset + row + 1}, the run
     * continuing), so it is row+1's picture: lf(row+1)-1. No row follows the last one; there the level is taken to
     * advance, as it does on every frame without a pattern-load lag: lf(row).
     */
    private static int engineFrame(Shot shot, List<Sample> samples) {
        if (shot.captureCounter() != null) return shot.captureCounter() - 1;
        if (shot.name().equals("early")) return samples.get(shot.traceRow()).levelFrames() - 1;
        int after = shot.traceRow() + 1;
        return after < samples.size() ? samples.get(after).levelFrames() - 1 : samples.get(shot.traceRow()).levelFrames();
    }

    private static Rendered capture(Sample sample) {
        GraphicsManager graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        var level = GameServices.level();
        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithSpritePriority(GameServices.sprites(), true);
        graphics.flush();
        if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
        glFinish();
        AbstractPlayableSprite sprite = GameServices.camera().getFocusedSprite();
        return new Rendered(ScreenshotCapture.captureFramebuffer(320, 224),
                GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                sample.playerX(), sample.playerY(),
                sprite == null ? -9999 : sprite.getCentreX(), sprite == null ? -9999 : sprite.getCentreY(),
                ((CdLevel) level.getCurrentLevel()).placements());
    }

    private static boolean[][] mask(Rendered rendered) {
        boolean[][] masked = new boolean[320][224];
        int camX = rendered.cameraX();
        int camY = rendered.cameraY();
        maskBox(masked, rendered.playerX() - camX - 28, rendered.playerY() - camY - 36,
                rendered.playerX() - camX + 28, rendered.playerY() - camY + 36);
        maskBox(masked, rendered.enginePlayerX() - camX - 28, rendered.enginePlayerY() - camY - 36,
                rendered.enginePlayerX() - camX + 28, rendered.enginePlayerY() - camY + 36);
        for (CdLevel.CdPlacement placement : rendered.placements()) {
            int px = placement.x() - camX;
            int py = (placement.yWord() & 0xFFF) - camY;
            if ((placement.idByte() & 0x7F) == RING_OBJECT_ID) {
                // ObjRing_Init (Ring/Main.asm:60-80): subtype low 3 bits = rings - 1, high nibble = delta table row.
                int rings = (placement.subtype() & 7) + 1;
                int row = (placement.subtype() >> 4) & 0xF;
                int dx = RING_DELTAS[row * 2];
                int dy = RING_DELTAS[row * 2 + 1];
                for (int i = 0; i < rings; i++) {
                    maskBox(masked, px + i * dx - RING_RADIUS, py + i * dy - RING_RADIUS,
                            px + i * dx + RING_RADIUS, py + i * dy + RING_RADIUS);
                }
            } else {
                maskBox(masked, px - PLACEMENT_RADIUS, py - PLACEMENT_RADIUS, px + PLACEMENT_RADIUS, py + PLACEMENT_RADIUS);
            }
        }
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if ((x < 120 && y < 56) || (x < 56 && y >= 192) || (x >= 224 && y < 40)) masked[x][y] = true;
            }
        }
        return masked;
    }

    private static void maskBox(boolean[][] masked, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(224, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(320, x1); x++) masked[x][y] = true;
        }
    }

    /** {matching, total, minX, minY, maxX, maxY} over the unmasked pixels. */
    private static int[] compare(RgbaImage engine, RgbaImage reference, boolean[][] masked) {
        int same = 0, total = 0, minX = 999, minY = 999, maxX = -1, maxY = -1;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (masked[x][y]) continue;
                total++;
                if (level(reference.argb(x, y)) == level(engine.argb(x, y))) {
                    same++;
                } else {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {same, total, minX, minY, maxX, maxY};
    }

    /** One stage on the shared boot; returns the failure lines (empty when every asserted shot matches). */
    private static List<String> check(com.openggf.GameLoop loop, CdStagePayload payload, boolean firstBoot) throws Exception {
        Path out = Path.of("target", "soniccd-wwz-pictures");
        Files.createDirectories(out);
        List<Sample> samples = samples(payload);
        List<Shot> shots = shots(payload);
        // The trace and mid.json agree on the source camera row, even when the probe's level counter differs.
        for (Shot shot : shots) {
            assertEquals(shot.levelFrames(), samples.get(shot.traceRow()).levelFrames(), payload + " " + shot.name());
        }
        List<Rendered> rendered = render(loop, payload, firstBoot, samples, shots);
        List<String> failures = new ArrayList<>();
        RgbaImage[] reference = new RgbaImage[shots.size()];
        for (int i = 0; i < shots.size(); i++) {
            reference[i] = ScreenshotCapture.loadPNG(shots.get(i).file());
            Shot shot = shots.get(i);
            int[] r = compare(rendered.get(i).image(), reference[i], mask(rendered.get(i)));
            ScreenshotCapture.savePNG(rendered.get(i).image(), out.resolve("engine-" + payload + "-" + shot.name() + ".png"));
            String key = payload + " " + shot.name();
            boolean ok = r[1] >= 20000 && r[0] == r[1];
            String line = String.format("%s: picture of level frame %d (camera %X,%X): %d/%d unmasked pixels match%s",
                    key, engineFrame(shot, samples),
                    rendered.get(i).cameraX(), rendered.get(i).cameraY(), r[0], r[1],
                    r[0] == r[1] ? "" : String.format("; differing pixels lie in x %d-%d, y %d-%d", r[2], r[4], r[3], r[5]));
            System.out.println("WWZ picture: " + (ok ? "ok " : "FAIL ") + line);
            if (!ok) {
                failures.add(line + " (expected exact match over at least 20000 pixels)");
            }
        }
        // Negative control: this stage's early engine picture must not match its own last native screenshot.
        int last = shots.size() - 1;
        int[] control = compare(rendered.get(0).image(), reference[last], mask(rendered.get(0)));
        if (control[0] * 1000L >= control[1] * 995L) {
            failures.add(payload + " early engine picture also matches the late native screenshot: " + control[0] + "/" + control[1]);
        }
        return failures;
    }

    /**
     * Every unmasked pixel must match at three bits per channel. Use one boot and one load per stage.
     * Collect failures so every stage is reported.
     */
    @Test
    void everyStageMatchesItsNativeScreenshots() throws Exception {
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        List<String> failures = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            CdStagePayload first = PAYLOADS[0];
            var loop = boot.boot(disc, new SonicCdGameModule(),
                    SonicCdZoneRegistry.zoneIndex(first), SonicCdZoneRegistry.actIndex(first));
            for (CdStagePayload payload : PAYLOADS) {
                failures.addAll(check(loop, payload, payload == first));
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }
}
