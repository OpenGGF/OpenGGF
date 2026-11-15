package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.NullAudioBackend;
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
import com.openggf.tools.HeadlessGameBoot;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Native pictures of the Wacky Workbench objects: the production renderer against BizHawk frames of the stage
 * fixtures' own movies, taken at chosen level frames with {@code rehost-fixtures/_tools/probe.sh} into
 * {@code native/rehost-fixtures-wwzobj/<payload>/} (user-local, not committed; {@code shots.json} lists them; the test
 * skips without them). One headless GL boot loads R61A, R62A, R62B, R62C and R62D in turn.
 *
 * <p>The engine plays the trace's input (comparison only: nothing read from {@code physics.csv} reaches the engine
 * except the BK2 buttons, as in {@link TestWwzObjectsAppearances}) up to each shot's level frame and is compared only
 * while its player and camera still equal the trace. Native probe frame {@code bk2_frame_offset + row + 1} is the
 * picture of the engine state that matches the trace row of that level frame.
 *
 * <p>What is compared: the pixels this lane's objects draw. The engine frame is rendered twice more, tiles only and
 * object sprites over those tiles; where the two differ an object sprite is drawn. Those pixels inside a box around
 * each of this lane's on-screen objects, minus the player's box and the HUD, are compared with the native frame at
 * 3 bits per channel against the full engine frame, so the foreground's priority tiles are included but the
 * background, which belongs to other lanes, is not.
 */
@EnabledOnOs(OS.LINUX)
class TestWwzObjectsPictures {
    private static final CdStagePayload[] PAYLOADS = {CdStagePayload.R61A, CdStagePayload.R62A, CdStagePayload.R62B,
            CdStagePayload.R62C, CdStagePayload.R62D};
    private static final int BOX = 0x28;

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** A native shot and the engine level frame it shows ({@link #pictureLevelFrame}). */
    private record Shot(int levelFrames, Path file) { }

    /**
     * The level frame whose state a {@code probe.sh} shot shows. A picture shows the state after the level frame before
     * the one its row counts (the V-INT that starts the video frame DMAs what the previous iteration wrote,
     * Level/Main.asm:829-831), and probe frame {@code bk2_frame_offset + row + 1} is row+1's picture: lf(row+1)-1. That
     * is lf(row) unless row+1 is a lag row.
     */
    private static int pictureLevelFrame(List<Integer> counters, int traceRow) {
        return traceRow + 1 < counters.size() ? counters.get(traceRow + 1) - 1 : counters.get(traceRow);
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
        /** The same boxes against a native frame at least 150 level frames away: the negative control. */
        int controlSame;
        int controlTotal;
        final List<String> shots = new ArrayList<>();
    }

    @Test
    void objectSpritesMatchTheNativeFrames() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Map<CdStagePayload, List<Shot>> shots = new LinkedHashMap<>();
        Map<CdStagePayload, Map<Integer, int[]>> traces = new LinkedHashMap<>();
        for (CdStagePayload payload : PAYLOADS) {
            Path index = CdTestMedia.nativeFile("rehost-fixtures-wwzobj/" + payload + "/shots.json");
            List<Integer> counters = counters(CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/physics.csv"));
            List<Shot> list = new ArrayList<>();
            for (JsonNode shot : new ObjectMapper().readTree(index.toFile()).get("shots")) {
                list.add(new Shot(pictureLevelFrame(counters, shot.get("trace_row").asInt()),
                        index.resolveSibling(shot.get("file").asText())));
            }
            shots.put(payload, list);
            traces.put(payload, firstRowPerLevelFrame(
                    CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/physics.csv")));
        }

        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        Map<String, Tally> kinds = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            boolean first = true;
            for (CdStagePayload payload : PAYLOADS) {
                if (first) {
                    boot.boot(disc, new SonicCdGameModule(), SonicCdZoneRegistry.zoneIndex(payload),
                            SonicCdZoneRegistry.actIndex(payload));
                    assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
                    first = false;
                } else {
                    // The headless GL context boots once per JVM; later stages load through the same boot.
                    GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload),
                            SonicCdZoneRegistry.actIndex(payload));
                }
                playStage(payload, shots.get(payload), traces.get(payload), kinds, report);
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }

        StringBuilder text = new StringBuilder();
        List<String> failures = new ArrayList<>();
        for (var entry : kinds.entrySet()) {
            Tally t = entry.getValue();
            text.append(String.format("%s %d/%d over %d shots (negative control %d/%d)%n", entry.getKey(), t.same, t.total,
                    t.shots.size(), t.controlSame, t.controlTotal));
            if (t.total >= 150 && t.same * 100L < t.total * 95L) failures.add(entry.getKey() + " " + t.same + "/" + t.total);
            // The negative control: against a frame 150 or more level frames away the same pixels must not agree.
            if (t.controlTotal >= 150 && t.controlSame * 100L >= t.controlTotal * 90L) {
                failures.add(entry.getKey() + " control " + t.controlSame + "/" + t.controlTotal);
            }
        }
        Path out = Path.of("target", "soniccd-wwzobj");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("WWZOBJ pictures:\n" + String.join("\n", report) + "\n" + text);
        assertTrue(!kinds.isEmpty(), "no object of this lane was on screen in any shot");
        assertTrue(failures.isEmpty(), "kinds below 95%: " + failures + "\n" + text);
    }

    private static void playStage(CdStagePayload payload, List<Shot> shots, Map<Integer, int[]> rows,
                                  Map<String, Tally> kinds, List<String> report) throws Exception {
        AbstractPlayableSprite sonic = GameServices.camera().getFocusedSprite();
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        int next = 0;
        int previousCameraY = Integer.MIN_VALUE;
        Map<ObjectInstance, int[]> last = new java.util.IdentityHashMap<>();
        Map<ObjectInstance, int[]> now = new java.util.IdentityHashMap<>();
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
            int cameraY = GameServices.camera().getY() & 0xFFFF;
            last = now;
            now = new java.util.IdentityHashMap<>();
            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                now.put(o, new int[] {o.getX(), o.getY()});
            }
            if (levelFrame == shots.get(next).levelFrames()) {
                // A camera falling 12 or more pixels a frame (the stage's long drops) is not aligned with the native video
                // frame: those shots are listed, not compared.
                int dy = Math.abs((short) (cameraY - previousCameraY));
                if (previousCameraY != Integer.MIN_VALUE && dy >= 12) {
                    report.add(String.format("%s lf %d: camera moved %d vertically in the last frame; not compared",
                            payload, levelFrame, dy));
                } else {
                    compare(payload, shots.get(next), controlShot(shots, shots.get(next)), sonic, kinds, report, last, now);
                }
                next++;
            }
            previousCameraY = cameraY;

        }
    }

    /** The shot furthest in level frames from {@code shot}, at least 150 away, or {@code null}. */
    private static Shot controlShot(List<Shot> shots, Shot shot) {
        Shot far = null;
        for (Shot other : shots) {
            int distance = Math.abs(other.levelFrames() - shot.levelFrames());
            if (distance >= 150 && (far == null || distance > Math.abs(far.levelFrames() - shot.levelFrames()))) far = other;
        }
        return far;
    }

    private static void compare(CdStagePayload payload, Shot shot, Shot control, AbstractPlayableSprite sonic,
                                Map<String, Tally> kinds, List<String> report, Map<ObjectInstance, int[]> last,
                                Map<ObjectInstance, int[]> now) throws Exception {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !o.getClass().getName().contains(".objects.wwz.")) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx < -BOX || sx > 320 + BOX || sy < -BOX || sy > 224 + BOX) continue;
            // The native video of a fast-moving object is a frame off its state (the near events agree with the engine
            // there, see TestWwzObjectsAppearances): only objects that moved less than four pixels in the last level frame
            // are compared by picture.
            int[] before = last.get(o);
            if (before != null && (Math.abs(o.getX() - before[0]) >= 4 || Math.abs(o.getY() - before[1]) >= 4)) {
                report.add(String.format("%s lf %d %s: moved %d,%d in the last frame; not compared", payload,
                        shot.levelFrames(), o.getClass().getSimpleName(), o.getX() - before[0], o.getY() - before[1]));
                continue;
            }
            boxes.add(new int[] {sx - BOX, sy - BOX, sx + BOX, sy + BOX});
            names.add(o.getClass().getSimpleName());
        }
        if (boxes.isEmpty()) return;
        // Other lanes' objects (ring sparkles, springs, badniks) are masked by a box of their own.
        List<int[]> others = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || o.getClass().getName().contains(".objects.wwz.")) continue;
            others.add(new int[] {o.getX() - cx - 20, o.getY() - cy - 20, o.getX() - cx + 20, o.getY() - cy + 20});
        }

        RgbaImage tiles = render(LevelManager.LevelRenderOptions.tilesOnly());
        RgbaImage objectsOnly = render(new LevelManager.LevelRenderOptions(false, true, false, false, false, false, false));
        RgbaImage full = render(LevelManager.LevelRenderOptions.gameplay());
        sonic.setHidden(true);
        RgbaImage withoutPlayer = render(LevelManager.LevelRenderOptions.gameplay());
        sonic.setHidden(false);
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        RgbaImage other = control == null ? null : ScreenshotCapture.loadPNG(control.file());
        Path out = Path.of("target", "soniccd-wwzobj");
        Files.createDirectories(out);

        int psx = (sonic.getCentreX() - cx), psy = (sonic.getCentreY() - cy);
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int same = 0, total = 0, controlSame = 0, controlTotal = 0;
            for (int y = Math.max(0, b[1]); y < Math.min(224, b[3]); y++) {
                for (int x = Math.max(0, b[0]); x < Math.min(320, b[2]); x++) {
                    if (tiles.argb(x, y) == objectsOnly.argb(x, y)) continue;           // no object sprite here
                    if (Math.abs(x - psx) < 36 && y > psy - 32 && y < psy + 32) continue;  // the player is on top
                    if (nearPlayerPixel(full, withoutPlayer, x, y)) continue;               // the player's own sprite, grown 4 px
                    boolean foreign = false;
                    for (int[] m : others) foreign |= x >= m[0] && x < m[2] && y >= m[1] && y < m[3];
                    if (foreign) continue;
                    if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;            // HUD
                    total++;
                    if (level(reference.argb(x, y)) == level(full.argb(x, y))) same++;
                    if (other != null) {
                        controlTotal++;
                        if (level(other.argb(x, y)) == level(full.argb(x, y))) controlSame++;
                    }
                }
            }
            if (total == 0) continue;
            Tally t = kinds.computeIfAbsent(names.get(i), k -> new Tally());
            t.same += same;
            t.total += total;
            t.controlSame += controlSame;
            t.controlTotal += controlTotal;
            String label = payload + "@" + shot.levelFrames();
            if (!t.shots.contains(label)) t.shots.add(label);
            report.add(String.format("%s lf %d %s box(%d,%d): %d/%d", payload, shot.levelFrames(), names.get(i),
                    (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, same, total));
            if (same * 100L < total * 99L) {
                // Keep the engine frame and the player-less frame of a shot that is not exact, to look at.
                ScreenshotCapture.savePNG(full, out.resolve("engine-" + payload + "-lf" + shot.levelFrames() + ".png"));
            }
        }
    }

    /** True where the engine's frame differs from the frame without the player within 4 pixels: the player's silhouette. */
    private static boolean nearPlayerPixel(RgbaImage full, RgbaImage withoutPlayer, int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int px = x + dx, py = y + dy;
                if (px < 0 || py < 0 || px >= 320 || py >= 224) continue;
                if (full.argb(px, py) != withoutPlayer.argb(px, py)) return true;
            }
        }
        return false;
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
