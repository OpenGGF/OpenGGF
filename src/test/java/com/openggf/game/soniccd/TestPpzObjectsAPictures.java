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
 * Native pictures of the Palmtree Panic objects A: the production renderer against BizHawk frames of the stage
 * fixtures' own movies, taken every ten level frames from frame 300 with
 * {@code rehost-fixtures/_tools/probe.sh} into {@code native/rehost-fixtures-ppzobja/<payload>/} (user-local, not
 * committed; {@code shots.json} lists them; the test skips without them). One headless GL boot loads R11A, R11B, R11D,
 * R12A, R12B and R12D in turn.
 *
 * <p>The engine plays the trace's input (comparison only: nothing read from {@code physics.csv} reaches the engine
 * except the BK2 buttons, as in {@link TestPpzObjectsAAppearances}) up to each shot's level frame and is compared
 * only while its player and camera still equal the trace. Native probe frame {@code bk2_frame_offset + row + 1}
 * is the picture after level frame N, which is the engine after N-1 steps from its loaded state (the WWZ lane's
 * measurement of the same convention).
 *
 * <p>What is compared: the pixels this lane's objects draw. The engine frame is rendered twice more, tiles only and
 * object sprites over those tiles; where the two differ an object sprite is drawn. Those pixels inside a box around
 * each of this lane's on-screen objects, minus the player's box and the HUD, are compared with the native frame at
 * 3 bits per channel against the full engine frame, so the foreground's priority tiles are included but the
 * background, which belongs to other lanes, is not.
 */
@EnabledOnOs(OS.LINUX)
class TestPpzObjectsAPictures {
    private static final CdStagePayload[] PAYLOADS = {CdStagePayload.R11A, CdStagePayload.R11B, CdStagePayload.R11D,
            CdStagePayload.R12A, CdStagePayload.R12B, CdStagePayload.R12D};
    private static final int BOX = 0x28;

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
        final List<String> shots = new ArrayList<>();
    }

    @Test
    void objectSpritesMatchTheNativeFrames() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Map<CdStagePayload, List<Shot>> shots = new LinkedHashMap<>();
        Map<CdStagePayload, Map<Integer, int[]>> traces = new LinkedHashMap<>();
        for (CdStagePayload payload : PAYLOADS) {
            Path index = CdTestMedia.nativeFile("rehost-fixtures-ppzobja/" + payload + "/shots.json");
            List<Shot> list = new ArrayList<>();
            for (JsonNode shot : new ObjectMapper().readTree(index.toFile()).get("shots")) {
                list.add(new Shot(shot.get("level_frames").asInt(), index.resolveSibling(shot.get("file").asText())));
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
        HudTimeChecks hudTime = new HudTimeChecks();
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
                playStage(payload, shots.get(payload), traces.get(payload), kinds, report, hudTime);
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
            if (t.total >= 150 && t.same * 100L < t.total * 95L) failures.add(entry.getKey() + " " + t.same + "/" + t.total);
        }
        Path out = Path.of("target", "soniccd-ppzobja");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("PPZOBJA pictures:\n" + String.join("\n", report) + "\n" + text);
        hudTime.assertComplete();
        assertTrue(!kinds.isEmpty(), "no object of this lane was on screen in any shot");
        assertTrue(failures.isEmpty(), "kinds below 95%: " + failures + "\n" + text);
    }

    private static void playStage(CdStagePayload payload, List<Shot> shots, Map<Integer, int[]> rows,
                                  Map<String, Tally> kinds, List<String> report, HudTimeChecks hudTime) throws Exception {
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
                compare(payload, shots.get(next), sonic, kinds, report, hudTime);
                next++;
            }
        }
    }

    private static void compare(CdStagePayload payload, Shot shot, AbstractPlayableSprite sonic,
                                Map<String, Tally> kinds, List<String> report, HudTimeChecks hudTime) throws Exception {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !o.getClass().getName().contains(".objects.ppz.")) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx < -BOX || sx > 320 + BOX || sy < -BOX || sy > 224 + BOX) continue;
            boxes.add(new int[] {sx - BOX, sy - BOX, sx + BOX, sy + BOX});
            names.add(o.getClass().getSimpleName());
        }
        HudTimeShot timeShot = hudTime.pending.stream()
                .filter(candidate -> candidate.payload() == payload && candidate.levelFrame() == shot.levelFrames())
                .findFirst().orElse(null);
        if (boxes.isEmpty() && timeShot == null) return;

        RgbaImage tiles = boxes.isEmpty() ? null : render(LevelManager.LevelRenderOptions.tilesOnly());
        RgbaImage objectsOnly = boxes.isEmpty() ? null
                : render(new LevelManager.LevelRenderOptions(false, true, false, false, false, false, false));
        RgbaImage full = render(LevelManager.LevelRenderOptions.gameplay());
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        if (timeShot != null) hudTime.compare(timeShot, reference, full);
        Path out = Path.of("target", "soniccd-ppzobja");
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
            report.add(String.format("%s lf %d %s box(%d,%d): %d/%d", payload, shot.levelFrames(), names.get(i),
                    (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, same, total));
        }
    }

    private record HudTimeShot(CdStagePayload payload, int levelFrame, String residentDigits,
                               String previousDigits, int lastDigitRefreshY) { }

    // UpdateHUD (Level/_Objects/HUD and Points/Main.asm:252-305) runs in all of these
    // V-INTs. The VRAM bytes arrive after scanout for part or all of the changed glyphs.
    // The Sonic CD hardware timings entry records the per-digit write scanlines.
    private static final List<HudTimeShot> LATE_HUD_TIME_WRITES = List.of(
            new HudTimeShot(CdStagePayload.R11A, 330, "00306", "00305", 33),
            new HudTimeShot(CdStagePayload.R11A, 370, "00373", "00371", 40),
            new HudTimeShot(CdStagePayload.R11A, 440, "00490", "00488", 40),
            new HudTimeShot(CdStagePayload.R12A, 320, "00283", "00281", 40),
            new HudTimeShot(CdStagePayload.R12A, 370, "00366", "00365", 40),
            new HudTimeShot(CdStagePayload.R12A, 430, "00466", "00465", 40));

    private static final class HudTimeChecks {
        private static final int[] DIGIT_X = {56, 72, 80, 96, 104};

        private final List<HudTimeShot> pending = new ArrayList<>(LATE_HUD_TIME_WRITES);
        private final List<String> differences = new ArrayList<>();

        HudTimeChecks() {
            pending.add(new HudTimeShot(CdStagePayload.R11A, 320, "00290", "00290", 24));
            pending.add(new HudTimeShot(CdStagePayload.R12A, 330, "00300", "00300", 24));
        }

        void compare(HudTimeShot shot, RgbaImage reference, RgbaImage engine) {
            var art = (com.openggf.game.soniccd.objects.art.CdObjectArtProvider)
                    GameServices.module().getObjectArtProvider();
            int digits = art.entry().hudNumbersArt();
            for (int column = 0; column < DIGIT_X.length; column++) {
                byte[] resident = art.image().slice(digits + 64 * (shot.residentDigits().charAt(column) - '0'), 64);
                byte[] previous = art.image().slice(digits + 64 * (shot.previousDigits().charAt(column) - '0'), 64);
                int engineErrors = 0, nativeErrors = 0;
                for (int row = 0; row < 16; row++) {
                    int y = 24 + row;
                    byte[] scanned = column == 4 && y >= shot.lastDigitRefreshY() ? resident : previous;
                    for (int x = 0; x < 8; x++) {
                        if (!matchesGlyphPixel(engine, DIGIT_X[column], y, x, row, resident)) engineErrors++;
                        if (!matchesGlyphPixel(reference, DIGIT_X[column], y, x, row, scanned)) nativeErrors++;
                    }
                }
                if (engineErrors != 0 || nativeErrors != 0) {
                    differences.add("%s lf %d TIME digit x=%d: %d engine and %d native opaque pixel mismatches"
                            .formatted(shot.payload(), shot.levelFrame(), DIGIT_X[column], engineErrors, nativeErrors));
                }
            }
            pending.remove(shot);
        }

        private static boolean matchesGlyphPixel(RgbaImage frame, int left, int y, int x, int row, byte[] glyph) {
            int index = (glyph[row * 4 + x / 2] >>> ((1 - x % 2) * 4)) & 0xF;
            // Transparent glyph pixels show the stage behind the HUD.
            if (index == 0) return true;
            return level(frame.argb(left + x, y)) == (index == 1 ? 0 : 0x1FF);
        }

        void assertComplete() {
            assertTrue(pending.isEmpty(), () -> "Missing native TIME shots: " + pending);
            assertTrue(differences.isEmpty(), () -> String.join("\n", differences));
            System.out.println("PPZ TIME: 8 pictures match the measured scanout and published digit art");
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
