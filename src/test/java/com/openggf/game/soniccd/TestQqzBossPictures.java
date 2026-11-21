package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Native pictures of the Quartz Quadrant boss (top, booth, back, pipes, Eggman, dropper, bombs, sparks, explosions,
 * spike wall) and its capsule: the production renderer against the BizHawk frames recorded with the boss movies
 * ({@code native/rehost-boss-qqz/<movie>/screenshot-rowNNNN.png}, taken by the boss
 * probe at the trace rows the manifest lists; user-local, the test skips without them). As in
 * {@link TestSonicCdSharedObjectPictures}, the engine plays the trace's input one level frame at a time and a shot
 * is compared only on a level frame where the engine's player and camera equal the trace; compared are the pixels
 * where the gameplay render differs from the tiles-only render (a sprite is drawn there) inside a box around each
 * on-screen boss part or capsule (boss explosions included), minus the player's box, other owners' objects (lost
 * rings) and the HUD, at 3 bits per channel. Background planes are another owner's and are not compared.
 */
@EnabledOnOs(OS.LINUX)
class TestQqzBossPictures {
    private static final String[][] MOVIES = {{"R53C-fight", "R53C"}, {"R53D-fight", "R53D"}, {"R53C-death", "R53C"}};
    private static final int BOX = 0x30;

    private record Shot(int row, int levelFrames, Path file) { }

    private static final class Tally {
        int same;
        int total;
        final List<String> shots = new ArrayList<>();
    }

    @Test
    void bossAndCapsuleSpritesMatchTheNativeFrames() throws Exception {
        Path cue = CdTestMedia.usaCue();
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        Map<String, Tally> kinds = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             CdFreshStageBoots boots = new CdFreshStageBoots(320, 224)) {
            for (String[] movie : MOVIES) {
                Path root = CdTestMedia.nativeFile("rehost-boss-qqz/" + movie[0] + "/manifest.json").getParent();
                CdStagePayload payload = CdStagePayload.valueOf(movie[1]);
                Map<Integer, int[]> rows = new LinkedHashMap<>();
                Map<Integer, Integer> levelFrameOfRow = new LinkedHashMap<>();
                readTrace(root.resolve("trace/physics.csv"), rows, levelFrameOfRow);
                List<Shot> shots = new ArrayList<>();
                for (JsonNode shot : new ObjectMapper().readTree(root.resolve("manifest.json").toFile()).get("screenshots")) {
                    int row = shot.get("trace_row").asInt();
                    // The picture shown during a frame is the sprite table and scroll that V-INT copied at its start:
                    // the state after the previous level frame (the PPZ boss lane's finding). The engine is compared
                    // after level frame lf(row) - 1.
                    shots.add(new Shot(row, levelFrameOfRow.get(row) - 1, root.resolve(shot.get("file").asText())));
                }
                boots.boot(disc, payload);
                play(movie[0], shots, rows, kinds, report);
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
            if (t.same * 100L < t.total * 95L) failures.add(entry.getKey() + " " + t.same + "/" + t.total);
        }
        Path out = Path.of("target", "soniccd-boss-qqz");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("QQZBOSS pictures:\n" + String.join("\n", report) + "\n" + text);
        assertTrue(kinds.containsKey("QqzBossBooth") && kinds.containsKey("QqzBossEggman") && kinds.containsKey("CdCapsule"),
                "the booth, Eggman and the capsule must be compared: " + kinds.keySet());
        assertTrue(failures.isEmpty(), "kinds below 95%: " + failures + "\n" + text);
    }

    private static void play(String movie, List<Shot> shots, Map<Integer, int[]> rows, Map<String, Tally> kinds,
                             List<String> report) throws Exception {
        AbstractPlayableSprite sonic = GameServices.camera().getFocusedSprite();
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        int last = shots.stream().mapToInt(Shot::levelFrames).max().orElse(0);
        for (int levelFrame = 1; levelFrame <= last; levelFrame++) {
            int[] row = rows.get(levelFrame);
            if (row == null) break;
            if (levelFrame > 1) {
                int input = row[5];
                runner.stepFrame((input & 1) != 0, (input & 2) != 0, (input & 4) != 0, (input & 8) != 0,
                        (input & 0x70) != 0);
            }
            for (Shot shot : shots) {
                if (shot.levelFrames() != levelFrame) continue;
                boolean onRoute = (sonic.getCentreX() & 0xFFFF) == row[3] && (sonic.getCentreY() & 0xFFFF) == row[4]
                        && (GameServices.camera().getX() & 0xFFFF) == row[1]
                        && (GameServices.camera().getY() & 0xFFFF) == row[2];
                if (!onRoute) {
                    report.add(movie + " row " + shot.row() + ": engine player/camera off the native route; not compared");
                    continue;
                }
                compare(movie, shot, sonic, kinds, report);
            }
        }
    }

    private static void compare(String movie, Shot shot, AbstractPlayableSprite sonic, Map<String, Tally> kinds,
                                List<String> report) throws Exception {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !(o instanceof com.openggf.game.soniccd.objects.boss.CdBossPart)) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx < -BOX || sx > 320 + BOX || sy < -BOX || sy > 224 + BOX) continue;
            boxes.add(new int[] {sx - BOX, sy - BOX, sx + BOX, sy + BOX});
            names.add(o.getClass().getSimpleName());
        }
        if (boxes.isEmpty()) return;
        RgbaImage tiles = render(LevelManager.LevelRenderOptions.tilesOnly());
        RgbaImage full = render(LevelManager.LevelRenderOptions.gameplay());
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        Path out = Path.of("target", "soniccd-boss-qqz");
        Files.createDirectories(out);
        ScreenshotCapture.savePNG(full, out.resolve("engine-" + movie + "-row" + shot.row() + ".png"));
        ScreenshotCapture.savePNG(tiles, out.resolve("tiles-" + movie + "-row" + shot.row() + ".png"));
        int psx = sonic.getCentreX() - cx, psy = sonic.getCentreY() - cy;
        // Other owners' sprites over the boss: lost rings (the shared ring owner) and the player.
        List<int[]> others = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || o instanceof com.openggf.game.soniccd.objects.boss.CdBossPart
                    || o instanceof com.openggf.game.soniccd.objects.common.CdExplosion) continue;
            others.add(new int[] {o.getX() - cx, o.getY() - cy});
        }
        // A pixel belongs to the first box (in object order) that holds it, so overlapping parts count once.
        boolean[][] taken = new boolean[224][320];
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int same = 0, total = 0;
            for (int y = Math.max(0, b[1]); y < Math.min(224, b[3]); y++) {
                for (int x = Math.max(0, b[0]); x < Math.min(320, b[2]); x++) {
                    if (taken[y][x] || full.argb(x, y) == tiles.argb(x, y)) continue;   // no sprite drawn over the tiles
                    boolean other = false;
                    for (int[] o : others) other |= Math.abs(x - o[0]) < 16 && Math.abs(y - o[1]) < 16;
                    if (other) continue;
                    if (Math.abs(x - psx) < 24 && y > psy - 32 && y < psy + 32) continue;
                    if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;
                    taken[y][x] = true;
                    total++;
                    if (TestSonicCdSharedObjectPictures.level(reference.argb(x, y))
                            == TestSonicCdSharedObjectPictures.level(full.argb(x, y))) same++;
                }
            }
            if (total == 0) continue;
            Tally t = kinds.computeIfAbsent(names.get(i), k -> new Tally());
            t.same += same;
            t.total += total;
            String label = movie + "@" + shot.row();
            if (!t.shots.contains(label)) t.shots.add(label);
            report.add(String.format("%s row %d %s box(%d,%d): %d/%d", movie, shot.row(), names.get(i),
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

    /** Per level frame: row, camera_x, camera_y, player_x, player_y, input of its first row; and each row's level frame. */
    private static void readTrace(Path csv, Map<Integer, int[]> rows, Map<Integer, Integer> levelFrameOfRow) throws Exception {
        List<String> lines = Files.readAllLines(csv);
        List<String> header = List.of(lines.getFirst().split(","));
        int frame = header.indexOf("frame"), counter = header.indexOf("gameplay_frame_counter");
        int cx = header.indexOf("camera_x"), cy = header.indexOf("camera_y");
        int px = header.indexOf("player_x"), py = header.indexOf("player_y");
        int input = header.indexOf("input");
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            int lf = Integer.parseInt(c[counter], 16);
            levelFrameOfRow.put(Integer.parseInt(c[frame], 16), lf);
            rows.putIfAbsent(lf, new int[] {Integer.parseInt(c[frame], 16), Integer.parseInt(c[cx], 16),
                    Integer.parseInt(c[cy], 16), Integer.parseInt(c[px], 16), Integer.parseInt(c[py], 16),
                    Integer.parseInt(c[input], 16)});
        }
    }
}
