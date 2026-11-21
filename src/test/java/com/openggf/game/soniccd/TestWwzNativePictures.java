package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.trace.TraceReplayFrameClosureDriver;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceVIntServiceCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * {@code mid.json} and the matching post-frame observations in {@code probe-frames.csv}).
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

    private record Position(int playerX, int playerY, int cameraX, int cameraY) {
        static Position fromTrace(TraceFrame frame) {
            return new Position(frame.x(), frame.y(), frame.cameraX(), frame.cameraY());
        }
    }

    private record ProbeFrame(int frame, int levelFrames, Position position) { }

    private static List<ProbeFrame> probeFrames(Path file) throws Exception {
        List<String> lines = Files.readAllLines(file);
        assertEquals("frame,levelFrames,p_x,p_y,camera_x,camera_y", lines.getFirst(), file.toString());
        List<ProbeFrame> frames = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",");
            frames.add(new ProbeFrame(Integer.parseInt(cells[0]), Integer.parseInt(cells[1]),
                    new Position(Integer.parseInt(cells[2]), Integer.parseInt(cells[3]),
                            Integer.parseInt(cells[4]), Integer.parseInt(cells[5]))));
        }
        return frames;
    }

    private record Shot(String name, Path file, int videoLevelFrames) { }

    private static List<Shot> shots(CdStagePayload payload, TraceData trace, List<ProbeFrame> probeFrames)
            throws Exception {
        var mapper = new ObjectMapper();
        Path manifest = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json");
        JsonNode early = mapper.readTree(manifest.toFile()).required("screenshots").required("early");
        List<Shot> out = new ArrayList<>();
        out.add(new Shot("early", manifest.resolveSibling(early.required("file").asText()),
                manifestLevelFrames(early, trace)));
        Map<Integer, ProbeFrame> byFrame = new HashMap<>();
        for (ProbeFrame frame : probeFrames) byFrame.put(frame.frame(), frame);
        Path mid = CdTestMedia.nativeFile("rehost-fixtures-wwz/" + payload + "/mid.json");
        for (JsonNode shot : mapper.readTree(mid.toFile()).required("shots")) {
            manifestLevelFrames(shot, trace);
            String name = shot.required("name").asText();
            ProbeFrame capture = byFrame.get(shot.required("probe_frame").asInt());
            assertNotNull(capture, payload + " " + name + ": probe frame");
            out.add(new Shot(name, mid.resolveSibling(shot.required("file").asText()), capture.levelFrames()));
        }
        return out;
    }

    private static int manifestLevelFrames(JsonNode shot, TraceData trace) {
        int row = shot.required("trace_row").asInt();
        int levelFrames = trace.getFrame(row).gameplayFrameCounter();
        assertEquals(shot.required("level_frames").asInt(), levelFrames, "trace row " + row);
        return levelFrames;
    }

    /** The framebuffer plus what the masks need, read before the stage is replaced. */
    private record Rendered(RgbaImage image, int cameraX, int cameraY, int playerX, int playerY,
                            int enginePlayerX, int enginePlayerY, List<CdLevel.CdPlacement> placements) { }

    private static List<Rendered> render(com.openggf.GameLoop loop, CdStagePayload payload, boolean firstBoot,
                                         TraceData trace, List<Position> cameraPath, int[] targets) throws Exception {
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
        if (MOVIE_PAYLOADS.contains(payload)) {
            return replayMovie(payload, trace, cameraPath, targets);
        }

        Rendered[] out = new Rendered[targets.length];
        GameServices.camera().setFrozen(true);
        int captured = 0;
        for (int frame = 1; frame <= cameraPath.size(); frame++) {
            Position position = cameraPath.get(frame - 1);
            GameServices.camera().setX((short) position.cameraX());
            GameServices.camera().setY((short) position.cameraY());
            // CdFirstMainLoopIteration already ran scroll, palette and tile animation for frame 1.
            if (frame > 1) loop.step();
            captured += captureDue(frame, position, targets, out);
        }
        assertEquals(targets.length, captured, payload + ": shots captured");
        return List.of(out);
    }

    private static List<Rendered> replayMovie(CdStagePayload payload, TraceData trace, List<Position> cameraPath,
                                               int[] targets) throws Exception {
        GameServices.camera().setFrozen(false);
        Path movie = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/movie.bk2");
        var sonic = GameServices.camera().getFocusedSprite();
        var runner = new HeadlessTestRunner(sonic);
        runner.setBk2Movie(new Bk2MovieLoader().load(movie), trace.metadata().bk2FrameOffset());
        var vint = TraceVIntServiceCoordinator.open(trace.sourceDirectory().orElseThrow(), trace,
                SessionManager.requireCurrentGameModule());
        runner.consumeRecordingFrameInputOnly();

        // SpawnObjects_Main (Level/Object Spawner.asm:107-115) reads the previous camera because
        // the object pass precedes scroll, Level_MainLoop (Level/Main.asm:397-418).
        Rendered[] out = new Rendered[targets.length];
        int captured = 0;
        for (int row = 1; row < trace.frameCount() && captured < targets.length; row++) {
            TraceFrame expected = trace.getFrame(row);
            if (!vint.vBlankServiced(row)) {
                runner.consumeRecordingFrameInputOnly();
            } else {
                var phase = TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(row - 1), expected);
                TraceReplayFrameClosureDriver.driveGeneral(phase, runner::stepFrameFromRecording,
                        runner::skipFrameFromRecording, () -> { });
            }
            int frame = expected.gameplayFrameCounter();
            for (int i = 0; i < targets.length; i++) {
                if (targets[i] == frame && out[i] == null) {
                    String context = payload + " trace row " + row;
                    assertEquals(frame, GameServices.level().getFrameCounter(), context + ": level frame");
                    assertEquals(expected.x(), sonic.getCentreX(), context + ": Sonic X");
                    out[i] = capture(cameraPath.get(frame - 1));
                    captured++;
                }
            }
        }
        assertEquals(targets.length, captured, payload + ": movie shots captured");
        return List.of(out);
    }

    private static List<Position> alignCameraClock(TraceData trace, List<ProbeFrame> probeFrames, int lastFrame) {
        Map<Integer, TraceFrame> traceByLevel = new HashMap<>();
        for (int row = 0; row < trace.frameCount(); row++) {
            TraceFrame frame = trace.getFrame(row);
            traceByLevel.putIfAbsent(frame.gameplayFrameCounter(), frame);
        }
        Map<Integer, ProbeFrame> probeByLevel = new HashMap<>();
        for (ProbeFrame frame : probeFrames) probeByLevel.putIfAbsent(frame.levelFrames(), frame);
        ProbeFrame first = probeByLevel.get(1);
        assertNotNull(first, "probe level entry");

        // The first change in position distinguishes the clock alignment from the stationary entry frames.
        ProbeFrame anchor = probeFrames.stream()
                .filter(frame -> frame.levelFrames() > 1 && !frame.position().equals(first.position()))
                .findFirst().orElseThrow();
        TraceFrame matched = matchingTraceFrame(trace, anchor);
        assertNotNull(matched, "trace position for probe frame " + anchor.frame());
        int offset = matched.gameplayFrameCounter() - anchor.levelFrames();

        List<Position> aligned = new ArrayList<>();
        for (int frame = 1; frame <= lastFrame; frame++) {
            ProbeFrame probe = probeByLevel.get(frame);
            assertNotNull(probe, "probe level frame " + frame);
            TraceFrame sample = traceByLevel.get(Math.max(trace.getFrame(0).gameplayFrameCounter(), frame + offset));
            // Keep the established offset through repeated positions and movements unique to one recording.
            // A new position match can establish a different offset after the clocks diverge.
            if (sample == null || !probe.position().equals(Position.fromTrace(sample))) {
                matched = matchingTraceFrame(trace, probe);
                if (matched != null) {
                    offset = matched.gameplayFrameCounter() - frame;
                    sample = matched;
                }
            }
            assertNotNull(sample, "trace camera for probe level frame " + frame);
            aligned.add(Position.fromTrace(sample));
        }
        return aligned;
    }

    private static TraceFrame matchingTraceFrame(TraceData trace, ProbeFrame probe) {
        int nominalRow = probe.frame() - trace.metadata().bk2FrameOffset();
        TraceFrame nearest = null;
        int distance = Integer.MAX_VALUE;
        for (int row = 0; row < trace.frameCount(); row++) {
            TraceFrame frame = trace.getFrame(row);
            int rowDistance = Math.abs(row - nominalRow);
            if (rowDistance < distance && probe.position().equals(Position.fromTrace(frame))) {
                nearest = frame;
                distance = rowDistance;
            }
        }
        return nearest;
    }

    private static int captureDue(int levelFrame, Position position, int[] targets, Rendered[] out) {
        int n = 0;
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == levelFrame && out[i] == null) {
                out[i] = capture(position);
                n++;
            }
        }
        return n;
    }

    /**
     * A picture shows the preceding completed iteration. VInt_Level (Level/Main.asm:821-837) uploads that
     * iteration's palette, scroll and sprite table, then runs its tile animation. The early shot gets the
     * video frame's counter from its trace row. Each supplemental shot gets it from the screenshot probe.
     */
    private static int engineFrame(Shot shot) {
        return shot.videoLevelFrames() - 1;
    }

    private static Rendered capture(Position position) {
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
                position.playerX(), position.playerY(),
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
        Path traceDir = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/metadata.json").getParent();
        TraceData trace = TraceData.load(traceDir);
        List<ProbeFrame> probe = probeFrames(CdTestMedia.nativeFile(
                "rehost-fixtures-wwz/" + payload + "/probe-frames.csv"));
        List<Shot> shots = shots(payload, trace, probe);
        int[] targets = shots.stream().mapToInt(TestWwzNativePictures::engineFrame).toArray();
        int lastFrame = java.util.Arrays.stream(targets).max().orElseThrow();
        List<Position> cameraPath = alignCameraClock(trace, probe, lastFrame);
        List<Rendered> rendered = render(loop, payload, firstBoot, trace, cameraPath, targets);
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
                    key, engineFrame(shot),
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
