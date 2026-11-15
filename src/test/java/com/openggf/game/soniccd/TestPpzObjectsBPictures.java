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
 * Native pictures of the Palmtree Panic objects B (collapsing platform and pieces, springboard, breakable wall,
 * spinning disc, Sonic hole): the production renderer against BizHawk frames of the stage fixtures' own movies, taken
 * with {@code rehost-fixtures/_tools/probe.sh} into {@code native/rehost-fixtures-ppz/<payload>/} (this lane's) and
 * the earlier lane's {@code native/rehost-fixtures-ppzobja/<payload>/} (user-local, not committed; each directory's
 * {@code shots.json} lists them; the test skips without them). One headless GL boot loads each stage in turn.
 *
 * <p>The engine plays the trace's input (comparison only: nothing read from {@code physics.csv} reaches the engine
 * except the BK2 buttons) up to each shot's level frame and is compared only while its player and camera still equal
 * the trace. Native probe frame {@code bk2_frame_offset + row + 1} is the picture after level frame N.
 *
 * <p>What is compared: the pixels this lane's objects draw. The engine frame is rendered twice more, tiles only and
 * object sprites over those tiles; where the two differ an object sprite is drawn. Those pixels inside a box around
 * each of this lane's on-screen objects, minus the player's box and the HUD, are compared with the native frame at
 * 3 bits per channel against the full engine frame.
 */
@EnabledOnOs(OS.LINUX)
class TestPpzObjectsBPictures {
    private static final CdStagePayload[] PAYLOADS = {CdStagePayload.R11A, CdStagePayload.R11B, CdStagePayload.R11C,
            CdStagePayload.R11D, CdStagePayload.R12A, CdStagePayload.R12B, CdStagePayload.R12C, CdStagePayload.R12D};
    /** The stages this lane recorded longer movies of ({@code native/rehost-fixtures-ppz/ext/<payload>}). */
    private static final CdStagePayload[] EXTENDED = {CdStagePayload.R12A, CdStagePayload.R12B, CdStagePayload.R12C,
            CdStagePayload.R12D};
    /**
     * This lane's objects, and the earlier lane's gaps it closes with its own native shots: the Tamabboh missile, the ramp
     * (its booster pad after the player's launch), the plant, the scenery and the log shadow.
     */
    private static final java.util.Set<String> KINDS = java.util.Set.of("ObjCollapsePlatform", "ObjSpringBoard",
            "ObjBreakableWall", "ObjSpinningDisc", "ObjSonicHole", "ObjTamabbohMissile", "Obj3DRamp", "Obj3DPlant",
            "ObjScenery", "ObjLogShadow");

    /**
     * The last shot each stage's engine replay must still compare (it must follow the native player and camera through
     * that level frame): the route stays equal to native only while the objects it meets behave natively, so a wrong
     * object ends it early. Measured on the first green run; a shorter reach fails the test.
     */
    private static final Map<String, Integer> MIN_REACH = Map.of("R11A", 730, "R11B", 650, "R12A", 730,
            "R12B", 810, "R12C", 790, "R12D", 520);

    /** A native fixture (movie and trace) and the directories whose {@code shots.json} hold its pictures. */
    private record Source(CdStagePayload payload, String fixture, List<String> shotDirectories) { }

    private static List<Source> sources() {
        List<Source> out = new ArrayList<>();
        for (CdStagePayload payload : PAYLOADS) {
            out.add(new Source(payload, "rehost-fixtures/" + payload,
                    List.of("rehost-fixtures-ppz/" + payload, "rehost-fixtures-ppzobja/" + payload)));
        }
        for (CdStagePayload payload : EXTENDED) {
            out.add(new Source(payload, "rehost-fixtures-ppz/ext/" + payload,
                    List.of("rehost-fixtures-ppz/ext/" + payload)));
        }
        return out;
    }
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
        List<Source> sources = new ArrayList<>();
        Map<Source, List<Shot>> shots = new LinkedHashMap<>();
        Map<Source, Map<Integer, int[]>> traces = new LinkedHashMap<>();
        for (Source source : sources()) {
            List<Shot> list = new ArrayList<>();
            for (String directory : source.shotDirectories()) {
                Path index = optionalNativeFile(directory + "/shots.json");
                if (index == null) continue;
                for (JsonNode shot : new ObjectMapper().readTree(index.toFile()).get("shots")) {
                    list.add(new Shot(shot.get("level_frames").asInt(), index.resolveSibling(shot.get("file").asText())));
                }
            }
            Path physics = optionalNativeFile(source.fixture() + "/trace/physics.csv");
            if (list.isEmpty() || physics == null) continue;
            list.sort(java.util.Comparator.comparingInt(Shot::levelFrames));
            // One picture per level frame: the stages' two shot directories overlap, and the replay compares a level frame once.
            for (int i = list.size() - 1; i > 0; i--) {
                if (list.get(i).levelFrames() == list.get(i - 1).levelFrames()) list.remove(i);
            }
            sources.add(source);
            shots.put(source, list);
            traces.put(source, firstRowPerLevelFrame(physics));
        }

        org.junit.jupiter.api.Assumptions.assumeTrue(!sources.isEmpty(),
                "no native shots under native/rehost-fixtures-ppz or rehost-fixtures-ppzobja");
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        Map<String, Tally> kinds = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();
        List<String> shortReach = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            boolean first = true;
            for (Source source : sources) {
                CdStagePayload payload = source.payload();
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
                int reach = playStage(source.fixture().contains("/ext/") ? payload + " (extended)" : payload.toString(),
                        shots.get(source), traces.get(source), kinds, report);
                if (!source.fixture().contains("/ext/")) {
                    int required = MIN_REACH.getOrDefault(payload.toString(), 0);
                    int lastShot = shots.get(source).stream().mapToInt(Shot::levelFrames).filter(f -> f <= required).max().orElse(0);
                    if (reach < lastShot) shortReach.add(payload + " reached level frame " + reach + ", needed " + lastShot);
                }
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
        Path out = Path.of("target", "soniccd-ppz");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("PPZOBJB pictures:\n" + String.join("\n", report) + "\n" + text);
        assertTrue(!kinds.isEmpty(), "no object of this lane was on screen in any shot (capture shots into native/rehost-fixtures-ppz first)");
        assertTrue(failures.isEmpty(), "kinds below 95%: " + failures + "\n" + text);
        assertTrue(shortReach.isEmpty(), "the engine left the native route before the last shot: " + shortReach);
    }

    /** Like {@code CdTestMedia.nativeFile} but {@code null} instead of skipping when the file is absent. */
    private static Path optionalNativeFile(String relative) {
        String configured = System.getProperty("soniccd.native.root");
        Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("soniccd.media.root", "."), "..", "native");
        Path file = root.resolve(relative).normalize();
        return Files.isRegularFile(file) ? file : null;
    }

    /** Returns the level frame of the last shot compared (0 when none). */
    private static int playStage(String payload, List<Shot> shots, Map<Integer, int[]> rows,
                                 Map<String, Tally> kinds, List<String> report) throws Exception {
        int lastCompared = 0;
        AbstractPlayableSprite sonic = GameServices.camera().getFocusedSprite();
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        int next = 0;
        for (int levelFrame = 1; next < shots.size(); levelFrame++) {
            int[] row = rows.get(levelFrame);
            if (row == null) break;
            if (levelFrame > 1) {
                int input = row[5];
                try {
                    runner.stepFrame((input & 1) != 0, (input & 2) != 0, (input & 4) != 0, (input & 8) != 0,
                            (input & 0x70) != 0);
                } catch (IllegalStateException e) {
                    report.add(payload + ": an engine object failed at level frame " + levelFrame + " (" + e.getMessage()
                            + "); later shots not compared");
                    return lastCompared;
                }
            }
            if ((sonic.getCentreX() & 0xFFFF) != row[3] || (sonic.getCentreY() & 0xFFFF) != row[4]
                    || (GameServices.camera().getX() & 0xFFFF) != row[1]
                    || (GameServices.camera().getY() & 0xFFFF) != row[2]) {
                report.add(payload + ": engine left the native route at level frame " + levelFrame
                        + "; later shots not compared");
                return lastCompared;
            }
            if (levelFrame == shots.get(next).levelFrames()) {
                compare(payload, shots.get(next), sonic, kinds, report);
                lastCompared = levelFrame;
                next++;
            }
        }
        return lastCompared;
    }

    private static void compare(String payload, Shot shot, AbstractPlayableSprite sonic,
                                Map<String, Tally> kinds, List<String> report) throws Exception {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !KINDS.contains(o.getClass().getSimpleName())) continue;
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
        Path out = Path.of("target", "soniccd-ppz");
        Files.createDirectories(out);
        ScreenshotCapture.savePNG(full, out.resolve("engine-" + payload.replace(" (extended)", "-ext") + "-lf" + shot.levelFrames() + ".png"));

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

    /**
     * row, camera_x, camera_y, player_x, player_y, input per level frame: the row and input of the first trace row with
     * that gameplay frame counter, the positions of the last (a lag row repeats the counter and shows the previous
     * frame's camera).
     */
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
            int[] row = {Integer.parseInt(c[frame], 16), Integer.parseInt(c[cx], 16), Integer.parseInt(c[cy], 16),
                    Integer.parseInt(c[px], 16), Integer.parseInt(c[py], 16), Integer.parseInt(c[input], 16)};
            result.merge(Integer.parseInt(c[counter], 16), row, (first, later) -> {
                first[1] = later[1];
                first[2] = later[2];
                first[3] = later[3];
                first[4] = later[4];
                return first;
            });
        }
        return result;
    }
}
