package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.common.CdTimeStars;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.trace.TraceData;
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
 * Native pictures of the time-warp stars ({@link CdTimeStars}): the production renderer against BizHawk frames of the
 * recorded Palmtree Panic Present-to-Past warp ({@code native/rehost-timetravel/present-to-past}, a New Game route
 * whose Sonic touches the past post and warps). The shots ({@code native/rehost-fixtures-warp/present-to-past},
 * user-local; the test skips without them) are the probe's post-frame screenshots of that same movie, taken with the
 * unmodified schedule one probe frame per BK2 row at the frames listed in {@code frames.csv}; the probe's
 * {@code frames.csv} equals {@code probe-frames.csv} of the recorded movie on its first 4,180 rows.
 *
 * <p>The engine plays the trace's inputs (comparison only: nothing but the BK2 buttons reaches the engine, as in
 * {@link TestPpzBossPictures}) one level frame at a time and a shot is compared only where the engine's player and
 * camera still equal the trace. A picture shows the state after the level frame before the one its row counts
 * (the V-INT that starts the video frame DMAs what the previous iteration wrote, Level/Main.asm:829-831), so a shot
 * whose probe frame reads {@code levelFrames} N is compared with the engine after level frame N-1. Only non-lag probe
 * frames are shot.
 *
 * <p>Compared: the pixels where the gameplay render differs from the tiles-only render (a sprite is drawn there)
 * inside a box around each live star, minus the HUD, at 3 bits per channel against the full engine frame; and the
 * same without the player's box. A negative control compares the same pixels with the engine five level frames later:
 * the trail has moved on, so the comparison must fall well below the real one.
 */
@EnabledOnOs(OS.LINUX)
class TestCdTimeStarsPictures {
    /** Half-width of the box around a star: its widest mapping piece reaches 32 pixels from the centre (MapSpr_Powerup). */
    private static final int BOX = 0x28;
    private static final int CONTROL_SHIFT = 5;

    private record Shot(int probeFrame, int levelFrames, Path file) { }

    private static final class Tally {
        int same;
        int total;
        int sameOutsidePlayer;
        int totalOutsidePlayer;
        int controlSame;
        int controlTotal;
        int shots;
        int starsSeen;
        /** Engine: the first row each reserved slot holds a time star, and that star's animation. */
        final Map<Integer, int[]> engineAppearance = new java.util.TreeMap<>();
    }

    /** A native movie with its probe shots: {@code frames.csv} beside the PNGs, the trace directory (movie beside it). */
    private record Source(String label, String framesFile, String physicsFile) { }

    private static final List<Source> SOURCES = List.of(
            // The recorded Present-to-Past warp (hurt before the post; a New Game route).
            new Source("present-to-past", "rehost-fixtures-warp/present-to-past/frames.csv",
                    "rehost-timetravel/present-to-past/trace/physics.csv"),
            // The authored warp take's movie (scd-30-warp): Sonic runs through the Past post at ground speed $600.
            new Source("R11A-warp", "rehost-fixtures-warp/R11A-shots/frames.csv",
                    "rehost-fixtures-warp/R11A/trace/physics.csv"));

    @Test
    void timeStarsMatchTheNativeFrames() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Tally tally = new Tally();
        List<String> report = new ArrayList<>();
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             CdFreshStageBoots boots = new CdFreshStageBoots(320, 224)) {
            for (Source source : SOURCES) {
                Path frames = CdTestMedia.nativeFile(source.framesFile());
                Path trace = CdTestMedia.nativeFile(source.physicsFile());
                List<Shot> shots = shots(frames);
                org.junit.jupiter.api.Assumptions.assumeTrue(!shots.isEmpty(), "No native shots");
                Path traceDir = trace.getParent();
                Path movie = trace.resolveSibling("../movie.bk2").normalize();
                boots.boot(disc, CdStagePayload.R11A);
                // CdFixtureReplay's load: the stage is entered with the V-INT base of the trace's row 0 (contract section 4).
                com.openggf.game.session.SessionManager.requireCurrentGameModule()
                        .getGameService(CdLevelVIntCounter.class)
                        .seedNextStageForReplay(CdFixtureReplay.stageEntryBase(TraceData.load(traceDir)));
                CdFixtureReplay.loadStage(CdStagePayload.R11A);
                tally.engineAppearance.clear();
                report.add("== " + source.label());
                play(shots, traceDir, movie, tally, report);
                assertAppearance(source, trace, tally);
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
        String text = String.format("stars: %d/%d all sprite pixels, %d/%d outside the player's box, over %d shots "
                        + "(%d star boxes); control (+%d level frames): %d/%d",
                tally.same, tally.total, tally.sameOutsidePlayer, tally.totalOutsidePlayer, tally.shots,
                tally.starsSeen, CONTROL_SHIFT, tally.controlSame, tally.controlTotal);
        Path out = Path.of("target", "soniccd-timestars");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text + "\n");
        System.out.println("TIMESTARS pictures:\n" + String.join("\n", report) + "\n" + text);
        assertTrue(tally.shots >= 20, "too few shots with stars on screen: " + text);
        assertTrue(tally.totalOutsidePlayer >= 150, "too few star pixels outside the player: " + text);
        assertTrue(tally.same * 100L >= tally.total * 95L, "below 95%: " + text);
        assertTrue(tally.sameOutsidePlayer * 100L >= tally.totalOutsidePlayer * 95L, "below 95% outside the player: " + text);
        // The comparison can fail: against the trail five frames later it must fall well below the real one.
        assertTrue(tally.controlSame * 100L <= tally.controlTotal * 85L, "negative control did not fail: " + text);
    }

    /**
     * The aux appearance rows: native's object_appeared events of object $03 (nothing else owns that ID in these routes):
     * slots 12 to 15 on one row; the engine's stars must first hold those slots on the same row, with animations 5 to 8
     * in slot order (ObjSonic_MakeTimeWarpStars, Sonic/Main.asm:118-125).
     */
    private static void assertAppearance(Source source, Path physics, Tally tally) throws Exception {
        Map<Integer, Integer> nativeAppearance = new java.util.TreeMap<>();
        for (String line : Files.readAllLines(physics.resolveSibling("aux_state.jsonl"))) {
            if (!line.contains("\"object_appeared\"") || !line.contains("\"object_type\":\"0x03\"")) continue;
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(line);
            nativeAppearance.putIfAbsent(node.get("slot").asInt(), node.get("frame").asInt());
        }
        assertEquals(java.util.Set.of(12, 13, 14, 15), nativeAppearance.keySet(), source.label() + ": native star slots");
        for (var entry : nativeAppearance.entrySet()) {
            int[] engine = tally.engineAppearance.get(entry.getKey());
            org.junit.jupiter.api.Assertions.assertNotNull(engine, source.label() + ": engine star in slot " + entry.getKey());
            assertEquals(entry.getValue(), engine[0], source.label() + ": first row of slot " + entry.getKey());
            assertEquals(5 + (entry.getKey() - 12), engine[1], source.label() + ": animation of slot " + entry.getKey());
        }
    }

    private static List<Shot> shots(Path frames) throws Exception {
        List<String> lines = Files.readAllLines(frames);
        List<String> header = List.of(lines.getFirst().split(","));
        int frame = header.indexOf("frame"), levelFrames = header.indexOf("levelFrames"), lag = header.indexOf("lag");
        List<Shot> shots = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",", -1);
            int f = Integer.parseInt(c[frame]);
            if (!c[lag].equals("0")) continue;          // a lag frame's picture is not paired by the level-frame rule
            Path png = frames.resolveSibling(String.format("postframe-%05d.png", f));
            if (Files.isRegularFile(png)) shots.add(new Shot(f, Integer.parseInt(c[levelFrames]), png));
        }
        return shots;
    }

    /**
     * Replays the movie row by row as {@link CdFixtureReplay} does (phases, V-INT service rows, the BK2 buttons) on a
     * GL boot, and renders where a shot's level frame is complete: the last row whose {@code gameplay_frame_counter}
     * is the shot's {@code levelFrames} - 1. A row stays on the route only while its player and camera equal the trace.
     */
    private static void play(List<Shot> shots, Path traceDir, Path movieFile, Tally tally, List<String> report)
            throws Exception {
        TraceData trace = TraceData.load(traceDir);
        Map<Integer, Integer> lastRowOfCounter = new LinkedHashMap<>();
        for (int i = 0; i < trace.frameCount(); i++) lastRowOfCounter.put(trace.getFrame(i).gameplayFrameCounter(), i);
        Map<Integer, List<Shot>> atRow = new LinkedHashMap<>();
        Map<Integer, List<Shot>> controlAtRow = new LinkedHashMap<>();
        int lastRow = 0;
        for (Shot shot : shots) {
            Integer row = lastRowOfCounter.get(shot.levelFrames() - 1);
            Integer later = lastRowOfCounter.get(shot.levelFrames() - 1 + CONTROL_SHIFT);
            if (row == null || later == null) continue;
            atRow.computeIfAbsent(row, k -> new ArrayList<>()).add(shot);
            controlAtRow.computeIfAbsent(later, k -> new ArrayList<>()).add(shot);
            lastRow = Math.max(lastRow, later);
        }
        AbstractPlayableSprite sonic = (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        runner.setBk2Movie(new com.openggf.debug.playback.Bk2MovieLoader().load(movieFile), trace.metadata().bk2FrameOffset());
        var vint = com.openggf.trace.replay.TraceVIntServiceCoordinator.open(traceDir, trace,
                com.openggf.game.session.SessionManager.requireCurrentGameModule());
        Map<Integer, Frame> held = new LinkedHashMap<>();
        for (int i = 0; i <= Math.min(lastRow, trace.frameCount() - 1); i++) {
            var expected = trace.getFrame(i);
            var prior = i > 0 ? trace.getFrame(i - 1) : null;
            if (i == 0) {
                runner.consumeRecordingFrameInputOnly();
            } else {
                var phase = com.openggf.trace.TraceReplayBootstrap.phaseForReplay(trace, prior, expected);
                if (!vint.vBlankServiced(i)) runner.consumeRecordingFrameInputOnly();
                else com.openggf.tests.trace.TraceReplayFrameClosureDriver.driveGeneral(phase,
                        runner::stepFrameFromRecording, runner::skipFrameFromRecording, () -> { });
            }
            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                if (!o.isDestroyed() && o instanceof CdTimeStars star
                        && o instanceof com.openggf.level.objects.AbstractObjectInstance a) {
                    tally.engineAppearance.putIfAbsent(a.getSlotIndex(), new int[] {i, star.animation()});
                }
            }
            var done = trace.getFrame(CdFixtureReplay.completedRow(trace, i));
            if ((sonic.getCentreX() & 0xFFFF) != (done.x() & 0xFFFF) || (sonic.getCentreY() & 0xFFFF) != (done.y() & 0xFFFF)
                    || (GameServices.camera().getX() & 0xFFFF) != done.cameraX()
                    || (GameServices.camera().getY() & 0xFFFF) != done.cameraY()) {
                report.add(String.format("engine left the native route at row %d: player %04X,%04X camera %04X,%04X native"
                                + " %04X,%04X camera %04X,%04X; later shots not compared", i,
                        sonic.getCentreX() & 0xFFFF, sonic.getCentreY() & 0xFFFF, GameServices.camera().getX() & 0xFFFF,
                        GameServices.camera().getY() & 0xFFFF, (done.x() & 0xFFFF), (done.y() & 0xFFFF), done.cameraX(),
                        done.cameraY()));
                return;
            }
            for (Shot shot : atRow.getOrDefault(i, List.of())) {
                Frame frame = capture(sonic);
                if (frame.stars.isEmpty()) {
                    report.add(String.format("probe frame %d (row %d): no star alive; not compared", shot.probeFrame(), i));
                    continue;
                }
                held.put(shot.probeFrame(), frame);
                Path dir = Path.of("target", "soniccd-timestars");
                Files.createDirectories(dir);
                ScreenshotCapture.savePNG(frame.full, dir.resolve("engine-" + shot.probeFrame() + ".png"));
                compare(shot, i, frame, frame.full, ScreenshotCapture.loadPNG(shot.file()), tally, report, false);
                tally.shots++;
            }
            for (Shot shot : controlAtRow.getOrDefault(i, List.of())) {
                Frame early = held.remove(shot.probeFrame());
                if (early == null) continue;
                compare(shot, i, early, capture(sonic).full, ScreenshotCapture.loadPNG(shot.file()), tally, report, true);
            }
        }
    }

    /** One engine frame: the gameplay render, the tiles-only render and where the player and the stars stand. */
    private record Frame(RgbaImage full, RgbaImage tiles, int playerX, int playerY, List<int[]> stars) { }

    private static Frame capture(AbstractPlayableSprite sonic) {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> stars = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !(o instanceof CdTimeStars)) continue;
            stars.add(new int[] {o.getX() - cx, o.getY() - cy});
        }
        RgbaImage tiles = render(LevelManager.LevelRenderOptions.tilesOnly());
        RgbaImage full = render(LevelManager.LevelRenderOptions.gameplay());
        return new Frame(full, tiles, sonic.getCentreX() - cx, sonic.getCentreY() - cy, stars);
    }

    private static void compare(Shot shot, int levelFrame, Frame frame, RgbaImage engine, RgbaImage reference,
                                Tally tally, List<String> report, boolean control) throws Exception {
        int same = 0, total = 0, sameOut = 0, totalOut = 0;
        boolean[][] taken = new boolean[224][320];
        for (int[] star : frame.stars) {
            for (int y = Math.max(0, star[1] - BOX); y < Math.min(224, star[1] + BOX); y++) {
                for (int x = Math.max(0, star[0] - BOX); x < Math.min(320, star[0] + BOX); x++) {
                    if (taken[y][x] || frame.full.argb(x, y) == frame.tiles.argb(x, y)) continue;   // no sprite here
                    if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;                       // HUD
                    taken[y][x] = true;
                    boolean equal = TestMmzObjectsPictures.level(reference.argb(x, y))
                            == TestMmzObjectsPictures.level(engine.argb(x, y));
                    total++;
                    if (equal) same++;
                    if (!(Math.abs(x - frame.playerX) < 24 && y > frame.playerY - 32 && y < frame.playerY + 32)) {
                        totalOut++;
                        if (equal) sameOut++;
                    }
                }
            }
        }
        if (control) {
            tally.controlSame += same;
            tally.controlTotal += total;
            return;
        }
        tally.same += same;
        tally.total += total;
        tally.sameOutsidePlayer += sameOut;
        tally.totalOutsidePlayer += totalOut;
        tally.starsSeen += frame.stars.size();
        report.add(String.format("probe frame %d (row %d): %d stars, %d/%d sprite pixels, %d/%d outside the player",
                shot.probeFrame(), levelFrame, frame.stars.size(), same, total, sameOut, totalOut));
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
}
