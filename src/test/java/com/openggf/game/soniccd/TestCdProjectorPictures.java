package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.objects.projector.CdProjector;
import com.openggf.game.soniccd.objects.projector.CdProjectorHologram;
import com.openggf.game.soniccd.objects.projector.ProjectorChild;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Native pictures of Metal Sonic's projector: the production renderer against BizHawk frames of the authored movie that
 * rolls into the Past act 1 projector ({@code native/rehost-fixtures-warp/R11B-destroy-out/R11B}; the post-frame shots in
 * {@code R11B-shots}, taken with the movie's own schedule through the probe, user-local; the test skips without them). The
 * engine replays the movie row by row ({@link CdFixtureReplay}'s phases and V-INT admission) on a GL boot and renders where a
 * shot's level frame is complete: a picture shows the state after the level frame before the one its row counts (the V-INT
 * that starts the video frame DMAs what the previous iteration wrote, Level/Main.asm:829-831), so a shot whose probe frame
 * reads {@code levelFrames} N is compared with the engine after level frame N-1. A row counts only while the engine's player
 * and camera equal the trace; lag-frame shots are not taken.
 *
 * <p>Compared: the pixels where the gameplay render differs from the tiles-only render (a sprite is drawn there) inside a
 * box around each live projector part (body and holograms) and each explosion, minus the HUD, the player's box and a
 * neighbourhood of every hologram animal (animals draw nothing yet, the animal owners' art), at 3 bits per channel against
 * the full engine frame. (Every pixel inside the boxes is reported too: it is about 94% equal; the rest is other owners' art, the
 * hologram animals and level objects that draw nothing yet, and it is as large in the frames after the projector is gone.) A negative control compares the same pixels against the engine five level frames later: the
 * holograms flicker and the explosions move on, so it must fall well below the real figure.
 */
@EnabledOnOs(OS.LINUX)
class TestCdProjectorPictures {
    private static final int BOX = 0x30;
    private static final int CONTROL_SHIFT = 5;

    private record Shot(int probeFrame, int levelFrames, Path file) { }

    private static final class Tally {
        int same, total, controlSame, controlTotal, shots, allSame, allTotal;
        final Map<String, int[]> kinds = new LinkedHashMap<>();
    }

    @Test
    void projectorAndExplosionSpritesMatchTheNativeFrames() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Path frames = CdTestMedia.nativeFile("rehost-fixtures-warp/R11B-shots/frames.csv");
        Path physics = CdTestMedia.nativeFile("rehost-fixtures-warp/R11B-destroy-out/R11B/trace/physics.csv");
        List<Shot> shots = shots(frames);
        org.junit.jupiter.api.Assumptions.assumeTrue(!shots.isEmpty(), "No native shots");
        Path traceDir = physics.getParent();
        Path movie = traceDir.resolveSibling("movie.bk2");
        Tally tally = new Tally();
        List<String> report = new ArrayList<>();
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             CdFreshStageBoots boots = new CdFreshStageBoots(320, 224)) {
            boots.boot(disc, CdStagePayload.R11B);
            com.openggf.game.session.SessionManager.requireCurrentGameModule()
                    .getGameService(CdLevelVIntCounter.class)
                    .seedNextStageForReplay(CdFixtureReplay.stageEntryBase(TraceData.load(traceDir)));
            CdFixtureReplay.loadStage(CdStagePayload.R11B);
            play(shots, traceDir, movie, tally, report);
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
        StringBuilder kinds = new StringBuilder();
        for (var e : tally.kinds.entrySet()) kinds.append(String.format(" %s %d/%d;", e.getKey(), e.getValue()[0], e.getValue()[1]));
        String text = String.format("projector: %d/%d sprite pixels (%d/%d of every pixel in the boxes) over %d shots (%s); control (+%d level frames): %d/%d",
                tally.same, tally.total, tally.allSame, tally.allTotal, tally.shots, kinds.toString().trim(), CONTROL_SHIFT, tally.controlSame, tally.controlTotal);
        Path out = Path.of("target", "soniccd-projector");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text + "\n");
        System.out.println("PROJECTOR pictures:\n" + String.join("\n", report) + "\n" + text);
        assertTrue(tally.shots >= 20, "too few shots with a projector part on screen: " + text);
        assertTrue(tally.total >= 1500, "too few sprite pixels compared: " + text);
        assertTrue(tally.same * 100L >= tally.total * 95L, "below 95%: " + text);
        assertTrue(tally.controlSame * 100L <= tally.controlTotal * 90L, "negative control did not fail: " + text);
    }

    private static List<Shot> shots(Path frames) throws Exception {
        List<String> lines = Files.readAllLines(frames);
        List<String> header = List.of(lines.getFirst().split(","));
        int frame = header.indexOf("frame"), levelFrames = header.indexOf("levelFrames"), lag = header.indexOf("lag");
        List<Shot> shots = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",", -1);
            int f = Integer.parseInt(c[frame]);
            if (!c[lag].equals("0")) continue;
            Path png = frames.resolveSibling(String.format("postframe-%05d.png", f));
            if (Files.isRegularFile(png)) shots.add(new Shot(f, Integer.parseInt(c[levelFrames]), png));
        }
        return shots;
    }

    private static void play(List<Shot> shots, Path traceDir, Path movieFile, Tally tally, List<String> report) throws Exception {
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
            var done = trace.getFrame(CdFixtureReplay.completedRow(trace, i));
            if ((sonic.getCentreX() & 0xFFFF) != (done.x() & 0xFFFF) || (sonic.getCentreY() & 0xFFFF) != (done.y() & 0xFFFF)
                    || (GameServices.camera().getX() & 0xFFFF) != done.cameraX()
                    || (GameServices.camera().getY() & 0xFFFF) != done.cameraY()) {
                report.add("engine left the native route at row " + i + "; later shots not compared");
                return;
            }
            for (Shot shot : atRow.getOrDefault(i, List.of())) {
                Frame frame = capture(sonic);
                if (frame.boxes.isEmpty()) {
                    report.add(String.format("probe frame %d (row %d): no projector part on screen; not compared", shot.probeFrame(), i));
                    continue;
                }
                held.put(shot.probeFrame(), frame);
                Path dir = Path.of("target", "soniccd-projector");
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

    /** One engine frame and where its projector parts, explosions, animals and the player stand (screen coordinates). */
    private record Frame(RgbaImage full, RgbaImage tiles, int playerX, int playerY, List<int[]> boxes, List<String> kinds,
                         List<int[]> animals) { }

    private static Frame capture(AbstractPlayableSprite sonic) {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        List<int[]> animals = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed()) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (o instanceof ProjectorChild) animals.add(new int[] {sx, sy});
            String kind = o instanceof CdProjector || o instanceof CdProjectorHologram ? "projector"
                    : o instanceof CdExplosion ? "explosion" : null;
            if (kind != null) {
                boxes.add(new int[] {sx, sy});
                kinds.add(kind);
            }
        }
        RgbaImage tiles = render(LevelManager.LevelRenderOptions.tilesOnly());
        RgbaImage full = render(LevelManager.LevelRenderOptions.gameplay());
        return new Frame(full, tiles, sonic.getCentreX() - cx, sonic.getCentreY() - cy, boxes, kinds, animals);
    }

    private static void compare(Shot shot, int row, Frame frame, RgbaImage engine, RgbaImage reference, Tally tally,
                                List<String> report, boolean control) {
        int same = 0, total = 0, allSame = 0, allTotal = 0;
        boolean[][] taken = new boolean[224][320];
        Map<String, int[]> per = new LinkedHashMap<>();
        for (int b = 0; b < frame.boxes.size(); b++) {
            int[] star = frame.boxes.get(b);
            int[] kind = per.computeIfAbsent(frame.kinds.get(b), k -> new int[2]);
            for (int y = Math.max(0, star[1] - BOX); y < Math.min(224, star[1] + BOX); y++) {
                for (int x = Math.max(0, star[0] - BOX); x < Math.min(320, star[0] + BOX); x++) {
                    if (taken[y][x]) continue;
                    if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;                      // HUD
                    if (Math.abs(x - frame.playerX) < 24 && y > frame.playerY - 32 && y < frame.playerY + 32) continue;
                    boolean nearAnimal = false;
                    for (int[] a : frame.animals) nearAnimal |= Math.abs(x - a[0]) < 16 && Math.abs(y - a[1]) < 16;
                    if (nearAnimal) continue;
                    taken[y][x] = true;
                    boolean equal = TestMmzObjectsPictures.level(reference.argb(x, y)) == TestMmzObjectsPictures.level(engine.argb(x, y));
                    allTotal++;
                    if (equal) allSame++;
                    if (frame.full.argb(x, y) == frame.tiles.argb(x, y)) continue;                  // no sprite drawn here
                    total++;
                    kind[1]++;
                    if (equal) { same++; kind[0]++; }
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
        tally.allSame += allSame;
        tally.allTotal += allTotal;
        for (var e : per.entrySet()) {
            int[] t = tally.kinds.computeIfAbsent(e.getKey(), k -> new int[2]);
            t[0] += e.getValue()[0];
            t[1] += e.getValue()[1];
        }
        report.add(String.format("probe frame %d (row %d): %d/%d sprite pixels, %d/%d of every pixel in the boxes (%s)", shot.probeFrame(), row,
                same, total, allSame, allTotal, frame.kinds));
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
