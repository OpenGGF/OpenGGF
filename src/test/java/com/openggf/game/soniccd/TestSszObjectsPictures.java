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
 * Native pictures of the Stardust Speedway objects: the production renderer against the two screenshots each stage
 * fixture carries (the early one, 30 frames after the title card releases, and the one at the last trace row;
 * {@code manifest.json}'s {@code screenshots}). The engine plays the trace's input (comparison only: nothing read from
 * {@code physics.csv} reaches the engine except the BK2 buttons, as in {@link TestSszObjectsAppearances}) up to each
 * shot's level frame and is compared only while its player and camera still equal the trace. Native probe frame
 * {@code bk2_frame_offset + row + 1} is the picture after level frame N, which is the engine after N-1 steps from its
 * loaded state.
 *
 * <p>What is compared: the pixels this lane's objects draw. The engine frame is rendered twice more, tiles only and
 * object sprites over those tiles; where the two differ an object sprite is drawn. Those pixels inside a box around
 * each of this lane's on-screen objects, minus the player's box and the HUD, are compared with the native frame at
 * 3 bits per channel against the full engine frame. Objects that are on screen in no shot the engine reaches are
 * reported as unverified, not passed.
 */
@EnabledOnOs(OS.LINUX)
class TestSszObjectsPictures {
    private static final CdStagePayload[] PAYLOADS = {CdStagePayload.R71A, CdStagePayload.R71B, CdStagePayload.R71C,
            CdStagePayload.R71D, CdStagePayload.R72A, CdStagePayload.R72B, CdStagePayload.R72C, CdStagePayload.R72D,
            CdStagePayload.R73C, CdStagePayload.R73D};
    private static final int BOX = 0x28;
    /** Kinds reported but not asserted: an open finding, not a pass. None: the lamp finding was a frame-pairing error (see playStage). */
    private static final java.util.Set<String> OPEN_FINDINGS = java.util.Set.of();

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    private record Shot(int levelFrames, Path file) { }

    /** Pixels compared and equal, per kind of object. */
    private static final class Tally {
        int same;
        int total;
        int brokenSame;
        final List<String> shots = new ArrayList<>();
    }

    @Test
    void objectSpritesMatchTheNativeFrames() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Map<CdStagePayload, List<Shot>> shots = new LinkedHashMap<>();
        Map<CdStagePayload, Map<Integer, int[]>> traces = new LinkedHashMap<>();
        for (CdStagePayload payload : PAYLOADS) {
            Path manifest = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json");
            List<Shot> list = new ArrayList<>();
            JsonNode screenshots = new ObjectMapper().readTree(manifest.toFile()).get("screenshots");
            for (String which : new String[] {"early", "end"}) {
                JsonNode shot = screenshots.get(which);
                list.add(new Shot(shot.get("level_frames").asInt(), manifest.resolveSibling(shot.get("file").asText())));
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
            text.append(String.format("%s %d/%d over %d shots (%s)%n", entry.getKey(), t.same, t.total, t.shots.size(),
                    String.join(", ", t.shots)));
            boolean asserted = !entry.getKey().endsWith("(uncertified tiles)") && !OPEN_FINDINGS.contains(entry.getKey());
            if (asserted && t.total >= 150 && t.same * 100L < t.total * 95L) failures.add(entry.getKey() + " " + t.same + "/" + t.total);
            // The comparison can fail: the same pixels compared 7 pixels off must not pass (negative control).
            if (asserted && t.total >= 150) {
                text.append(String.format("  negative control (7 px off) %d/%d%n", t.brokenSame, t.total));
                if (t.brokenSame * 100L >= t.total * 95L) failures.add(entry.getKey() + " negative control passes: " + t.brokenSame + "/" + t.total);
            }
        }
        Path out = Path.of("target", "soniccd-sszobj");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("SSZOBJ pictures:\n" + String.join("\n", report) + "\n" + text);
        if (kinds.isEmpty()) System.out.println("SSZOBJ pictures: no object of this lane was on screen in a shot the engine reached; all unverified");
        assertTrue(failures.isEmpty(), "kinds below 95%: " + failures + "\n" + text);
    }

    private static void playStage(CdStagePayload payload, List<Shot> shots, Map<Integer, int[]> rows,
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
            // The fixture's screenshots are the frame before their named probe frame (noted in
            // rehost-fixtures-wwz/<payload>/mid.json), so the one labelled levelFrames N shows what
            // iteration N-1 drew; the engine's
            // level frame counter is levelFrames itself (1 after the load). Compare one level frame
            // earlier, as TestTtzObjectPictures and CczNativePictureCheck do: every searchlight
            // phase and the lamp then match with no tile shift.
            if (levelFrame == shots.get(next).levelFrames() - 1) {
                compare(payload, shots.get(next), sonic, kinds, report);
                next++;
            }
        }
    }

    private static void compare(CdStagePayload payload, Shot shot, AbstractPlayableSprite sonic,
                                Map<String, Tally> kinds, List<String> report) throws Exception {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !o.getClass().getName().contains(".objects.ssz.")) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx < -BOX || sx > 320 + BOX || sy < -BOX || sy > 224 + BOX) continue;
            boxes.add(new int[] {sx - BOX, sy - BOX, sx + BOX, sy + BOX});
            names.add(o.getClass().getSimpleName());
        }
        if (boxes.isEmpty()) return;

        RgbaImage tiles = render(LevelManager.LevelRenderOptions.tilesOnly());
        RgbaImage full = render(LevelManager.LevelRenderOptions.gameplay());
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        Path out = Path.of("target", "soniccd-sszobj");
        Files.createDirectories(out);
        ScreenshotCapture.savePNG(full, out.resolve("engine-" + payload + "-lf" + shot.levelFrames() + ".png"));

        // The picture shows the previous level frame (see playStage), so no shift is applied: the best tile shift is only
        // measured, and reported, as the check that the frame pairing is right (0 when it is).
        int[] measured = tileShift(tiles, reference);
        int[] shift = {0, 0};
        double agreement = tileAgreement(tiles, reference, shift);
        boolean certified = agreement >= 0.90;
        report.add(String.format("%s lf %d (engine lf %d) best tile shift (%d,%d), tile layer agreement at no shift %.1f%%%s",
                payload, shot.levelFrames(), shot.levelFrames() - 1, measured[0], measured[1], agreement * 100,
                certified ? "" : " (below 90%: the foreground differs, the presentation lane's known late-shot finding;"
                        + " object boxes are reported, not asserted)"));
        int psx = (sonic.getCentreX() - cx), psy = (sonic.getCentreY() - cy);
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int same = 0, total = 0, brokenSame = 0;
            for (int y = Math.max(0, b[1]); y < Math.min(224, b[3]); y++) {
                for (int x = Math.max(0, b[0]); x < Math.min(320, b[2]); x++) {
                    if (tiles.argb(x, y) == full.argb(x, y)) continue;                      // no object sprite drawn here
                    if (Math.abs(x - psx) < 24 && y > psy - 32 && y < psy + 32) continue;  // the player is on top
                    if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;            // HUD
                    total++;
                    int rx = x + shift[0], ry = y + shift[1];
                    if (rx >= 0 && rx < 320 && ry >= 0 && ry < 224 && level(reference.argb(rx, ry)) == level(full.argb(x, y))) same++;
                    int bx = rx + 7;                                   // negative control: the same compare 7 pixels off
                    if (bx >= 0 && bx < 320 && ry >= 0 && ry < 224 && level(reference.argb(bx, ry)) == level(full.argb(x, y))) brokenSame++;
                }
            }
            if (total == 0) continue;
            Tally t = kinds.computeIfAbsent(names.get(i) + (certified ? "" : " (uncertified tiles)"), k -> new Tally());
            t.same += same;
            t.total += total;
            t.brokenSame += brokenSame;
            String label = payload + "@" + shot.levelFrames();
            if (!t.shots.contains(label)) t.shots.add(label);
            report.add(String.format("%s lf %d %s box(%d,%d): %d/%d", payload, shot.levelFrames(), names.get(i),
                    (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, same, total));
        }
    }

    /** The (dx, dy) in [-3, 3] with the most equal pixels between the engine tile layer and the native frame; (0,0) wins ties. */
    private static int[] tileShift(RgbaImage tiles, RgbaImage reference) {
        int[] best = {0, 0};
        long bestSame = -1;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                long same = 0;
                for (int y = 8; y < 216; y += 2) {
                    for (int x = 8; x < 312; x += 2) {
                        if (level(tiles.argb(x, y)) == level(reference.argb(x + dx, y + dy))) same++;
                    }
                }
                boolean origin = dx == 0 && dy == 0;
                if (same > bestSame || (origin && same == bestSame)) {
                    bestSame = same;
                    best = new int[] {dx, dy};
                }
            }
        }
        return best;
    }

    /** The fraction of equal pixels between the engine tile layer and the native frame at {@code shift}, sampled every second pixel. */
    private static double tileAgreement(RgbaImage tiles, RgbaImage reference, int[] shift) {
        long same = 0, total = 0;
        for (int y = 8; y < 216; y += 2) {
            for (int x = 8; x < 312; x += 2) {
                total++;
                if (level(tiles.argb(x, y)) == level(reference.argb(x + shift[0], y + shift[1]))) same++;
            }
        }
        return (double) same / total;
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
