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
 * Native pictures of the Collision Chaos objects: the production renderer against BizHawk frames of the stage
 * fixtures' own movies, retaken on the trace run into {@code native/rehost-fixtures-ccz2/<payload>/} (user-local, not
 * committed; {@code shots.json} lists them; the test skips without them) by
 * {@code rehost-bossfix/_tools/retake_ccz_shots.py}: every ten level frames from 300 for R31A-R32D. The earlier set
 * ({@code rehost-fixtures-ccz}) came from a probe run that pressed every input one frame early and left the trace
 * route after the bumpers; the retaken run applies the fixture's inputs on their own frames, and at every shot's probe
 * frame its player X/Y, camera X/Y and level frame counter equal the shot's trace row. The engine plays the trace's
 * input (comparison only: nothing read from {@code physics.csv} reaches the engine except the BK2 buttons, as in
 * {@link TestCczObjectsAppearances}) up to each shot's level frame and is compared only while its player and camera
 * still equal the trace. Pairing, the one rule every Sonic CD picture test uses: a picture shows the state after the
 * level frame before the one its row counts (the V-INT that starts the video frame DMAs what the previous iteration
 * wrote, Level/Main.asm:829-831). A shot at probe frame {@code bk2_frame_offset + trace_row + 1} of this run is the
 * row's own picture (the boss probes' convention, {@code TestPpzBossPictures}): engine level frame lf(row)-1. No shot is
 * chosen by agreement or dropped; the terrain agreement is printed as a diagnostic only.
 *
 * <p>What is compared: the pixels this lane's objects draw. The engine frame is rendered twice more, tiles only and object
 * sprites over those tiles; where the two differ an object sprite is drawn. Those pixels inside a box around each of this
 * lane's on-screen objects, minus the player's box and the HUD, are compared with the native frame at 3 bits per channel
 * against the full engine frame.
 */
@EnabledOnOs(OS.LINUX)
class TestCczObjectsPictures {
    private static final CdStagePayload[] PAYLOADS = {CdStagePayload.R31A, CdStagePayload.R31B, CdStagePayload.R31C,
            CdStagePayload.R31D, CdStagePayload.R32A, CdStagePayload.R32B, CdStagePayload.R32C, CdStagePayload.R32D};
    private static final int BOX = 0x30;

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** A native shot: the engine level frame it shows ({@link #pictureLevelFrame}), its file and the row's own lf. */
    private record Shot(int levelFrames, Path file, int rowLevelFrames) { }

    /** Probe frame {@code offset + row + 1} of the trace run is the row's own picture: the state after lf(row)-1. */
    private static int pictureLevelFrame(List<Integer> counters, int traceRow) {
        return counters.get(traceRow) - 1;
    }

    /** {@code gameplay_frame_counter} of every trace row. */
    private static List<Integer> counters(Path csv) throws Exception {
        List<String> lines = Files.readAllLines(csv);
        int counter = List.of(lines.getFirst().split(",")).indexOf("gameplay_frame_counter");
        List<Integer> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) out.add(Integer.parseInt(line.split(",")[counter], 16));
        return out;
    }

    /**
     * Shots (stage@row level frame) whose native frame does not show the trace's state, so no level frame pairs them.
     * They are compared and reported apart from the kinds; each must still fail (below 95% of its object pixels), or the
     * test fails so it gets promoted. None: every retaken shot is on its trace row (class comment).
     */
    private static final Map<String, String> UNCERTIFIED = Map.of();

    /** Object pixels compared and equal per uncertified shot. */
    private static final Map<String, int[]> UNCERTIFIED_SCORES = new LinkedHashMap<>();

    /** Pixels compared and equal, per kind of object. */
    private static final class Tally {
        int same;
        int total;
        final List<String> shots = new ArrayList<>();
    }

    @Test
    void objectSpritesMatchTheNativeFrames() throws Exception {
        UNCERTIFIED_SCORES.clear();
        Path cue = CdTestMedia.usaCue();
        Map<CdStagePayload, List<Shot>> shots = new LinkedHashMap<>();
        Map<CdStagePayload, Map<Integer, int[]>> traces = new LinkedHashMap<>();
        for (CdStagePayload payload : PAYLOADS) {
            Path index = CdTestMedia.nativeFile("rehost-fixtures-ccz2/" + payload + "/shots.json");
            List<Integer> counters = counters(CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/physics.csv"));
            List<Shot> list = new ArrayList<>();
            for (JsonNode shot : new ObjectMapper().readTree(index.toFile()).get("shots")) {
                list.add(new Shot(pictureLevelFrame(counters, shot.get("trace_row").asInt()),
                        index.resolveSibling(shot.get("file").asText()), shot.get("level_frames").asInt()));
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
        for (var entry : UNCERTIFIED_SCORES.entrySet()) {
            int[] score = entry.getValue();
            boolean passes = score[1] > 0 && score[0] * 100L >= score[1] * 95L;
            text.append(String.format("UNCERTIFIED %s %d/%d: %s%n", entry.getKey(), score[0], score[1],
                    UNCERTIFIED.get(entry.getKey())));
            if (passes) failures.add(entry.getKey() + " is listed as uncertified but now passes: " + score[0] + "/" + score[1]);
        }
        for (var entry : kinds.entrySet()) {
            Tally t = entry.getValue();
            text.append(String.format("%s %d/%d over %d shots (%s)%n", entry.getKey(), t.same, t.total, t.shots.size(),
                    String.join(", ", t.shots)));
            if (t.total >= 150 && t.same * 100L < t.total * 95L) failures.add(entry.getKey() + " " + t.same + "/" + t.total);
        }
        Path out = Path.of("target", "soniccd-cczobj");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("CCZOBJ pictures:\n" + String.join("\n", report) + "\n" + text);
        assertTrue(!kinds.isEmpty(), "no object of this lane was on screen in any shot");
        assertTrue(failures.isEmpty(), "kinds below 95%: " + failures + "\n" + text);
    }

    /** The engine's three renders of one level frame and the boxes of this lane's on-screen objects. */
    private record Snap(RgbaImage tiles, RgbaImage objectsOnly, RgbaImage full, List<int[]> boxes, List<String> names,
                        int psx, int psy) { }

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
            while (next < shots.size() && levelFrame == shots.get(next).levelFrames()) {
                compare(payload, shots.get(next), capture(sonic), kinds, report);
                next++;
            }
        }
    }

    private static Snap capture(AbstractPlayableSprite sonic) {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !o.getClass().getName().contains(".objects.ccz.")) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx < -BOX || sx > 320 + BOX || sy < -BOX || sy > 224 + BOX) continue;
            boxes.add(new int[] {sx - BOX, sy - BOX, sx + BOX, sy + BOX});
            names.add(o.getClass().getSimpleName());
        }
        return new Snap(render(LevelManager.LevelRenderOptions.tilesOnly()),
                render(new LevelManager.LevelRenderOptions(false, true, false, false, false, false, false)),
                render(LevelManager.LevelRenderOptions.gameplay()), boxes, names,
                sonic.getCentreX() - cx, sonic.getCentreY() - cy);
    }

    /** Terrain agreement with the native frame: pixels no sprite covers, outside the HUD, the player and the lane's boxes. */
    private static double terrainAgreement(Snap snap, RgbaImage reference) {
        int same = 0, total = 0;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (snap.tiles().argb(x, y) != snap.objectsOnly().argb(x, y)) continue;      // a sprite covers it
                if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;                  // HUD
                if (Math.abs(x - snap.psx()) < 24 && y > snap.psy() - 32 && y < snap.psy() + 32) continue;
                boolean inBox = false;
                for (int[] b : snap.boxes()) inBox |= x >= b[0] && x < b[2] && y >= b[1] && y < b[3];
                if (inBox) continue;
                total++;
                if (level(reference.argb(x, y)) == level(snap.full().argb(x, y))) same++;
            }
        }
        return total == 0 ? 0 : (double) same / total;
    }

    private static void compare(CdStagePayload payload, Shot shot, Snap snap, Map<String, Tally> kinds,
                                List<String> report) throws Exception {
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        if (snap.boxes().isEmpty()) return;
        double terrain = terrainAgreement(snap, reference);
        Path out = Path.of("target", "soniccd-cczobj");
        Files.createDirectories(out);
        ScreenshotCapture.savePNG(snap.full(), out.resolve("engine-" + payload + "-lf" + shot.levelFrames() + ".png"));
        RgbaImage tiles = snap.tiles(), objectsOnly = snap.objectsOnly(), full = snap.full();
        int psx = snap.psx(), psy = snap.psy();
        List<int[]> boxes = snap.boxes();
        List<String> names = snap.names();
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
            String shotKey = payload + "@" + shot.rowLevelFrames();
            if (UNCERTIFIED.containsKey(shotKey)) {
                int[] score = UNCERTIFIED_SCORES.computeIfAbsent(shotKey, kk -> new int[2]);
                score[0] += same;
                score[1] += total;
                report.add(String.format("%s row lf %d = engine lf %d (terrain %.2f) %s box(%d,%d): %d/%d [UNCERTIFIED]",
                        payload, shot.rowLevelFrames(), shot.levelFrames(), terrain, names.get(i), (b[0] + b[2]) / 2,
                        (b[1] + b[3]) / 2, same, total));
                continue;
            }
            Tally t = kinds.computeIfAbsent(names.get(i), kk -> new Tally());
            t.same += same;
            t.total += total;
            String label = payload + "@" + shot.rowLevelFrames();
            if (!t.shots.contains(label)) t.shots.add(label);
            report.add(String.format("%s row lf %d = engine lf %d (terrain %.2f) %s box(%d,%d): %d/%d", payload,
                    shot.rowLevelFrames(), shot.levelFrames(), terrain, names.get(i), (b[0] + b[2]) / 2, (b[1] + b[3]) / 2,
                    same, total));
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
