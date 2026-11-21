package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.trace.TraceReplayFrameClosureDriver;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Native pictures of the shared objects (owner {@code shared}): the production renderer against BizHawk frames of
 * the stage fixtures' own movies, taken every 10 or 20 level frames with {@code rehost-fixtures/_tools/probe.sh}
 * into {@code native/rehost-fixtures-shared/<payload>/} (user-local, not committed; {@code shots.json} lists them;
 * the test skips without them). Stages: R42C (Tidal Tempest block), R51C (Quartz Quadrant falling block and
 * spikes), R53D (collapse floor), R61B (switch, door, platform), R72A (Stardust Speedway block), R82D (crusher).
 * The method is {@link TestPpzObjectsAPictures}'s: the engine plays the trace's BK2 input (comparison only) and is
 * compared only while its player and camera equal the trace; native probe frame {@code bk2_frame_offset + row + 1}
 * is the picture after level frame N. Compared are the pixels this owner's objects draw, inside a box around
 * each on-screen shared object, minus the player's box and the HUD, at 3 bits per channel.
 */
@EnabledOnOs(OS.LINUX)
class TestSonicCdSharedObjectPictures {
    private static final CdStagePayload[] PAYLOADS = {CdStagePayload.R42C, CdStagePayload.R51C, CdStagePayload.R53D,
            CdStagePayload.R61B, CdStagePayload.R72A, CdStagePayload.R82D};
    /**
     * Stages whose full-act native route ({@code rehost-long}) also has pictures, in {@code rehost-fixtures-routesb3/long-<payload>}.
     * Each stage boots in a fresh session ({@link CdFreshStageBoots}), so the order does not matter.
     */
    private static final CdStagePayload[] LONG_ROUTES = {CdStagePayload.R51A, CdStagePayload.R51C, CdStagePayload.R52A,
            CdStagePayload.R61B, CdStagePayload.R62A, CdStagePayload.R71A, CdStagePayload.R81A, CdStagePayload.R82A};

    /** A stage's trace and its pictures: the ten-second fixture or the full-act route. */
    private record Source(CdStagePayload payload, boolean longRoute) {
        String label() { return (longRoute ? "long-" : "") + payload; }
        String physics() { return (longRoute ? "rehost-long/" : "rehost-fixtures/") + payload + "/trace/physics.csv"; }
    }

    private static final int BOX = 0x28;
    /**
     * Shots whose shared-object boxes hold another owner's content or a state this owner cannot reach,
     * so they say nothing about these sprites: R42C lf 320, Tidal Tempest's waterfall tiles animate over the
     * block (zone presentation); R72A lf 340, Stardust Speedway's searchlight ($38, a zone object, not
     * hosted) sweeps its beam over the block; R61B lf 420 on, the player holds the switch and the native door
     * rises ({@code switch_flags} bit 7, objects/r6/door.asm:52-58), which needs the switch-flag game service.
     *
     * <p>Quartz Quadrant's moving platform ($30) is compared in every shot. Its two shared shots (R51C lf 360 and 380)
     * drew it one pixel above the position the trace's RAM holds (platform Y $1A2 at row 382, the shot showed $1A1) with
     * every other pixel of the picture equal to a fresh run of the same movie (434 pixels differ, all in the platform's
     * box; the cause of the shared run's different sink is not established). In a fresh run the native pictures pair
     * exactly: probe frame P shows the sprite table and vertical scroll that were in VRAM at P, which are the buffer
     * and camera of the level frame before the RAM sampled at P (the platform's sprite top equals its sprite-table Y on
     * every frame 382-386), so the picture after level frame lf is probe frame {@code bk2_frame_offset + row(lf) + 2}.
     * {@code rehost-fixtures-routesb3/R51C} holds such pictures and replaces the two shots (see {@code _tools/shots.py}).
     */
    private static final java.util.Set<String> EXCLUDED = java.util.Set.of("R42C@320", "R72A@340", "R61B@420",
            "R61B@430", "R61B@440");

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** A native shot and the engine level frame it shows ({@link #pictureLevelFrame}). */
    private record Shot(int levelFrames, Path file, int traceRow) { }

    /**
     * The level frame whose state a {@code probe.sh} shot shows. A picture shows the state after the level frame before
     * the one its row counts (the V-INT that starts the video frame DMAs what the previous iteration wrote,
     * Level/Main.asm:829-831), and probe frame {@code bk2_frame_offset + row + 1} is row+1's picture: lf(row+1)-1. That
     * is lf(row) unless row+1 is a lag row.
     */
    private static int pictureLevelFrame(List<Integer> counters, int traceRow) {
        return traceRow + 1 < counters.size() ? counters.get(traceRow + 1) - 1 : counters.get(traceRow);
    }

    /** A {@code shots.json} whose {@code level_frames} is the level frame the picture shows (routesb3/_tools/shots.py). */
    private static void addAlignedShots(Map<Integer, Shot> into, Path index) throws Exception {
        for (JsonNode shot : new ObjectMapper().readTree(index.toFile()).get("shots")) {
            Shot s = new Shot(shot.get("level_frames").asInt(), index.resolveSibling(shot.get("file").asText()),
                    shot.get("trace_row").asInt());
            into.put(s.levelFrames(), s);
        }
    }

    private static void addShots(Map<Integer, Shot> into, Path index, List<Integer> counters) throws Exception {
        for (JsonNode shot : new ObjectMapper().readTree(index.toFile()).get("shots")) {
            Shot s = new Shot(pictureLevelFrame(counters, shot.get("trace_row").asInt()),
                    index.resolveSibling(shot.get("file").asText()), shot.get("trace_row").asInt());
            into.put(s.levelFrames(), s);
        }
    }

    /** {@code gameplay_frame_counter} of every trace row. */
    private static List<Integer> counters(Path csv) throws Exception {
        List<String> lines = Files.readAllLines(csv);
        int counter = List.of(lines.getFirst().split(",")).indexOf("gameplay_frame_counter");
        List<Integer> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) out.add(Integer.parseInt(line.split(",")[counter], 16));
        return out;
    }

    /** Pixels compared and equal, per kind of object. */
    private static final class Tally {
        int same;
        int total;
        final List<String> shots = new ArrayList<>();
    }

    @Test
    void objectSpritesMatchTheNativeFrames() throws Exception {
        Path cue = CdTestMedia.usaCue();
        List<Source> sources = new ArrayList<>();
        Map<String, List<Shot>> shots = new LinkedHashMap<>();
        Map<String, Map<Integer, int[]>> traces = new LinkedHashMap<>();
        for (CdStagePayload payload : PAYLOADS) sources.add(new Source(payload, false));
        for (CdStagePayload payload : LONG_ROUTES) sources.add(new Source(payload, true));
        // -Dsoniccd.pictures.sources=long-R62A,R51C limits the run to those labels (diagnosis; every source by default).
        String only = System.getProperty("soniccd.pictures.sources", "");
        if (!only.isBlank()) {
            java.util.Set<String> labels = java.util.Set.of(only.split(","));
            sources.removeIf(source -> !labels.contains(source.label()));
        }
        for (Source source : new ArrayList<>(sources)) {
            Map<Integer, Shot> byLevelFrame = new java.util.TreeMap<>();
            Path physics = CdTestMedia.optionalNativeFile(source.physics());
            if (physics == null) { sources.remove(source); continue; }
            List<Integer> counters = counters(physics);
            if (source.longRoute()) {
                Path aligned = CdTestMedia.optionalNativeFile("rehost-fixtures-routesb3/long-" + source.payload() + "/shots.json");
                if (aligned == null) { sources.remove(source); continue; }
                addAlignedShots(byLevelFrame, aligned);
            } else {
                Path index = CdTestMedia.nativeFile("rehost-fixtures-shared/" + source.payload() + "/shots.json");
                addShots(byLevelFrame, index, counters);
                // Pictures aligned with the trace by routesb3/_tools/shots.py; they replace a shared shot of the same level frame.
                Path aligned = CdTestMedia.optionalNativeFile("rehost-fixtures-routesb3/" + source.payload() + "/shots.json");
                if (aligned != null) addAlignedShots(byLevelFrame, aligned);
            }
            shots.put(source.label(), new ArrayList<>(byLevelFrame.values()));
            traces.put(source.label(), firstRowPerLevelFrame(physics));
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(!sources.isEmpty(), "No native pictures");

        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        // The replay boots Sonic alone (CdFixtureReplay.boot); a personal config.yaml must not add a sidekick here.
        String oldMain = GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        String oldSidekick = GameServices.configuration().getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        GameServices.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        Map<String, Tally> kinds = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             CdFreshStageBoots boots = new CdFreshStageBoots(320, 224)) {
            for (Source source : sources) {
                CdStagePayload payload = source.payload();
                boots.boot(disc, payload);
                if (source.longRoute()) {
                    Path dir = CdTestMedia.nativeFile("rehost-long/" + payload + "/trace/metadata.json").getParent();
                    playLongStage(source.label(), dir, dir.resolveSibling("movie.bk2"), shots.get(source.label()), kinds, report);
                } else {
                    playStage(source.label(), shots.get(source.label()), traces.get(source.label()), kinds, report);
                }
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            GameServices.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, oldMain);
            GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, oldSidekick);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }

        StringBuilder text = new StringBuilder();
        List<String> failures = new ArrayList<>();
        for (var entry : kinds.entrySet()) {
            Tally t = entry.getValue();
            text.append(String.format("%s %d/%d over %d shots (%s)%n", entry.getKey(), t.same, t.total, t.shots.size(),
                    String.join(", ", t.shots)));
            if (t.total >= 150 && t.same * 100L < t.total * 95L) failures.add(entry.getKey() + " " + t.same + "/" + t.total);
        }
        Path out = Path.of("target", "soniccd-shared-objects");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("Shared object pictures:\n" + String.join("\n", report) + "\n" + text);
        assertTrue(!kinds.isEmpty(), "no object of this lane was on screen in any shot");
        assertTrue(failures.isEmpty(), "kinds below 95%: " + failures + "\n" + text);
    }

    private static void playStage(String payload, List<Shot> shots, Map<Integer, int[]> rows,
                                  Map<String, Tally> kinds, List<String> report) throws Exception {
        AbstractPlayableSprite sonic = GameServices.camera().getFocusedSprite();
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        int next = 0;
        for (int levelFrame = 1; next < shots.size(); levelFrame++) {
            int[] row = rows.get(levelFrame);
            if (row == null) break;
            if (levelFrame > 1) {
                int input = row[5];
                runner.stepFrame((input & 1) != 0, (input & 2) != 0, (input & 4) != 0, (input & 8) != 0,
                        (input & 0x70) != 0);
            }
            if ((sonic.getCentreX() & 0xFFFF) != row[3] || (sonic.getCentreY() & 0xFFFF) != row[4]
                    || (GameServices.camera().getX() & 0xFFFF) != row[1]
                    || (GameServices.camera().getY() & 0xFFFF) != row[2]) {
                report.add(payload + ": engine left the native route at level frame " + levelFrame
                        + "; later shots not compared");
                return;
            }
            if (levelFrame == shots.get(next).levelFrames()) {
                if (!EXCLUDED.contains(payload + "@" + levelFrame)) compare(payload, shots.get(next), sonic, kinds, report);
                next++;
            }
        }
    }

    /**
     * A full-act route, stepped as {@link CdFixtureReplay} does: the BK2 movie drives every row, a V-blank-only row skips
     * the level frame, and the engine is compared with the row's completed level frame before a picture is taken.
     */
    private static void playLongStage(String label, Path traceDir, Path movie, List<Shot> shots, Map<String, Tally> kinds,
                                      List<String> report) throws Exception {
        TraceData trace = TraceData.load(traceDir);
        AbstractPlayableSprite sonic = GameServices.camera().getFocusedSprite();
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        runner.setBk2Movie(new Bk2MovieLoader().load(movie), trace.metadata().bk2FrameOffset());
        int next = 0;
        int last = shots.getLast().traceRow();
        for (int i = 0; i <= last && next < shots.size(); i++) {
            TraceFrame expected = trace.getFrame(i);
            TraceFrame prior = i > 0 ? trace.getFrame(i - 1) : null;
            TraceExecutionPhase phase;
            if (i == 0) {
                phase = TraceExecutionPhase.FULL_LEVEL_FRAME;
                runner.consumeRecordingFrameInputOnly();
            } else {
                phase = TraceReplayBootstrap.phaseForReplay(trace, prior, expected);
                TraceReplayFrameClosureDriver.driveGeneral(phase, runner::stepFrameFromRecording,
                        runner::skipFrameFromRecording, () -> { });
            }
            if (i != shots.get(next).traceRow()) continue;
            TraceFrame native_ = CdFixtureReplay.completedLevelFrame(trace, i, prior, expected, phase);
            if ((sonic.getCentreX() & 0xFFFF) != (native_.x() & 0xFFFF) || (sonic.getCentreY() & 0xFFFF) != (native_.y() & 0xFFFF)
                    || (GameServices.camera().getX() & 0xFFFF) != (native_.cameraX() & 0xFFFF)
                    || (GameServices.camera().getY() & 0xFFFF) != (native_.cameraY() & 0xFFFF)) {
                report.add(String.format("%s: engine left the native route at row %d (engine %04X,%04X cam %04X,%04X; native %04X,%04X cam %04X,%04X);"
                                + " later shots not compared", label, i, sonic.getCentreX() & 0xFFFF, sonic.getCentreY() & 0xFFFF,
                        GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF, native_.x() & 0xFFFF,
                        native_.y() & 0xFFFF, native_.cameraX() & 0xFFFF, native_.cameraY() & 0xFFFF));
                return;
            }
            compare(label, shots.get(next), sonic, kinds, report);
            next++;
        }
    }

    private static void compare(String payload, Shot shot, AbstractPlayableSprite sonic,
                                Map<String, Tally> kinds, List<String> report) throws Exception {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !o.getClass().getName().contains(".objects.shared.")) continue;
            // The roll tunnel draws only under the debug cheat (Roll Tunnel/Main.asm:12-14): no sprite of its own.
            if (o instanceof com.openggf.game.soniccd.objects.shared.ObjRollTunnel) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx < -BOX || sx > 320 + BOX || sy < -BOX || sy > 224 + BOX) continue;
            boxes.add(new int[] {sx - BOX, sy - BOX, sx + BOX, sy + BOX});
            names.add(o.getClass().getSimpleName());
        }
        if (boxes.isEmpty()) return;

        RgbaImage tiles = render(LevelManager.LevelRenderOptions.tilesOnly());
        RgbaImage objectsOnly = render(new LevelManager.LevelRenderOptions(false, true, false, false, false, false, false));
        RgbaImage full = render(LevelManager.LevelRenderOptions.gameplay());
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        Path out = Path.of("target", "soniccd-shared-objects");
        Files.createDirectories(out);
        ScreenshotCapture.savePNG(full, out.resolve("engine-" + payload + "-lf" + shot.levelFrames() + ".png"));

        int psx = (sonic.getCentreX() - cx), psy = (sonic.getCentreY() - cy);
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int same = 0, total = 0;
            for (int y = Math.max(0, b[1]); y < Math.min(224, b[3]); y++) {
                for (int x = Math.max(0, b[0]); x < Math.min(320, b[2]); x++) {
                    if (tiles.argb(x, y) == objectsOnly.argb(x, y)) continue;           // no object sprite here
                    if (Math.abs(x - psx) < 24 && y > psy - 32 && y < psy + 32) continue;  // the player is on top
                    if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;            // HUD
                    total++;
                    if (level(reference.argb(x, y)) == level(full.argb(x, y))) same++;
                }
            }
            if (total == 0) continue;
            Tally t = kinds.computeIfAbsent(names.get(i), k -> new Tally());
            t.same += same;
            t.total += total;
            String label = payload + "@" + shot.levelFrames();
            if (!t.shots.contains(label)) t.shots.add(label);
            if (same * 100L < total * 95L) {
                // A box below 95%: the engine frame and the native one, for looking at (target/soniccd-shared-objects).
                ScreenshotCapture.savePNG(full, out.resolve("miss-" + payload + "-lf" + shot.levelFrames() + "-engine.png"));
                ScreenshotCapture.savePNG(reference, out.resolve("miss-" + payload + "-lf" + shot.levelFrames() + "-native.png"));
            }
            report.add(String.format("%s lf %d %s box(%d,%d): %d/%d", payload, shot.levelFrames(), names.get(i),
                    (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, same, total));
        }
    }

    private static RgbaImage render(LevelManager.LevelRenderOptions options) {
        GraphicsManager graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        var level = GameServices.level();
        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithRenderOptions(GameServices.sprites(), options);
        graphics.flush();
        if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    /** row, camera_x, camera_y, player_x, player_y, input of the first row per levelFrames. */
    private static Map<Integer, int[]> firstRowPerLevelFrame(Path csv) throws Exception {
        List<String> lines = Files.readAllLines(csv);
        List<String> header = List.of(lines.getFirst().split(","));
        int frame = header.indexOf("frame"), counter = header.indexOf("gameplay_frame_counter");
        int cx = header.indexOf("camera_x"), cy = header.indexOf("camera_y");
        int px = header.indexOf("player_x"), py = header.indexOf("player_y");
        int input = header.indexOf("input");
        Map<Integer, int[]> result = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            result.putIfAbsent(Integer.parseInt(c[counter], 16), new int[] {Integer.parseInt(c[frame], 16),
                    Integer.parseInt(c[cx], 16), Integer.parseInt(c[cy], 16), Integer.parseInt(c[px], 16),
                    Integer.parseInt(c[py], 16), Integer.parseInt(c[input], 16)});
        }
        return result;
    }
}
