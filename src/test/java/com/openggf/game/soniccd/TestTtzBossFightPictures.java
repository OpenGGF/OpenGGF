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
import com.openggf.sprites.NativePositionOps;
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
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Native pictures of the Tidal Tempest boss in its arena: Eggman, his Egg Mobile, the bubbles and the missiles,
 * rendered by the production renderer and compared with the BizHawk frames of the idle movies
 * ({@code native/rehost-boss-ttz/R43?-idle/screenshot-rowNNNN.png}; comparison only). The engine's player cannot
 * follow the native route under water, so the boss runs on its own timeline as in {@link TestTtzBossFightTimeline}:
 * the stage is booted, the player set down in the arena (a test setup) and both runs are aligned on Eggman's routine 6.
 * A native picture shows the state after level frame lf - 1 (V-INT copies the sprite table at the frame's start);
 * the engine renders after the same number of level frames from routine 6. Compared are the pixels where the gameplay
 * render differs from the tiles-only render, inside a box around each boss part whose engine X, Y, routine and mapping frame equal
 * native RAM at that frame (a bubble rising or orbiting on its random sway, or a part off by its slot order, is left
 * out), minus both runs' players, the parts only one run has in that state, and the HUD, at 3 bits per channel; the camera must equal native's.
 * The mask is the object-sprite render (no player, rings or HUD), and a 16-pixel square around every engine object that
 * is not a boss part (air bubbles, points, rings) is left out: those sprites are not the boss's.
 */
@EnabledOnOs(OS.LINUX)
class TestTtzBossFightPictures {
    private static final String[][] MOVIES = {{"R43C-idle", "R43C"}, {"R43D-idle", "R43D"}};
    private static final int BOX = 0x30;

    private static final class Tally {
        int same;
        int total;
        final List<String> shots = new ArrayList<>();
    }

    /** One native shot: the level frames from routine 6, the camera, both players, the parts' states (ID x y rt). */
    private record Shot(int row, int k, int camX, int camY, int playerX, int playerY, List<String> parts, Path file) { }

    @Test
    void bossPartsMatchTheNativeFrames() throws Exception {
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
                Path root = CdTestMedia.nativeFile(TtzBossNativeCheck.ROOT + "/" + movie[0] + "/manifest.json").getParent();
                CdStagePayload payload = CdStagePayload.valueOf(movie[1]);
                List<Shot> shots = shots(root);
                boots.boot(disc, payload);
                play(movie[0], shots, kinds, report);
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
            if (!entry.getKey().endsWith(" overlap") && t.same * 100L < t.total * 95L) failures.add(entry.getKey() + " " + t.same + "/" + t.total);
        }
        Path out = Path.of("target", "soniccd-boss-ttz");
        Files.createDirectories(out);
        Files.writeString(out.resolve("fight-report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("TTZBOSS fight pictures:\n" + String.join("\n", report) + "\n" + text);
        assertTrue(kinds.containsKey("TtzBossEggman") && kinds.containsKey("TtzBossBubble")
                        && kinds.containsKey("TtzBossMissile"),
                "Eggman, the bubbles and the missiles must all be compared: " + kinds.keySet());
        assertTrue(failures.isEmpty(), "kinds below 95%: " + failures + "\n" + text);
    }

    /** The manifest's shots with the native state they show. */
    private static List<Shot> shots(Path root) throws Exception {
        Map<Integer, String[]> globals = new HashMap<>();
        List<String> lines = Files.readAllLines(root.resolve("probe/globals.csv"));
        for (String line : lines.subList(1, lines.size())) {
            String[] g = line.split(",");
            globals.put(Integer.parseInt(g[0]), g);
        }
        Map<Integer, List<String[]>> objects = new HashMap<>();
        lines = Files.readAllLines(root.resolve("probe/objects.csv"));
        int start = -1;
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            int frame = Integer.parseInt(c[0]);
            objects.computeIfAbsent(frame, k -> new ArrayList<>()).add(c);
            if (start < 0 && "4C".equals(c[2]) && "6".equals(c[4])) start = frame;
        }
        int lf0 = Integer.parseInt(globals.get(start)[2]);
        List<Shot> out = new ArrayList<>();
        for (JsonNode shot : new ObjectMapper().readTree(root.resolve("manifest.json").toFile()).get("screenshots")) {
            int frame = shot.get("probe_frame").asInt();
            int shown = Integer.parseInt(globals.get(frame)[2]) - 1;    // the state after level frame lf - 1
            int g = frame;
            while (g > 0 && globals.containsKey(g) && Integer.parseInt(globals.get(g)[2]) > shown) g--;
            if (frame < start || shown < lf0) continue;
            List<String> parts = new ArrayList<>();
            for (String[] c : objects.getOrDefault(g, List.of())) {
                parts.add(String.format("%s %04X %04X %02X %02X", c[2], Integer.parseInt(c[6]), Integer.parseInt(c[7]),
                        Integer.parseInt(c[4]), Integer.parseInt(c[10])));
            }
            String[] at = globals.get(g);
            out.add(new Shot(shot.get("trace_row").asInt(), shown - lf0, Integer.parseInt(at[11]), Integer.parseInt(at[12]),
                    Integer.parseInt(at[3]), Integer.parseInt(at[4]), parts, root.resolve(shot.get("file").asText())));
        }
        return out;
    }

    private static void play(String movie, List<Shot> shots, Map<String, Tally> kinds, List<String> report) throws Exception {
        AbstractPlayableSprite sonic = GameServices.camera().getFocusedSprite();
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        for (int i = 0; i < 240; i++) runner.stepFrame(false, false, false, false, false);   // the title card
        sonic.setRingCount(50);
        NativePositionOps.writeXPosResetSubpixel(sonic, 0x9F0);
        NativePositionOps.writeYPosResetSubpixel(sonic, 0x5AC);
        GameServices.camera().setX((short) 0x950);
        GameServices.camera().setY((short) 0x4F0);
        // The bottom boundary the chase leaves: R43Events holds $4F0 once the chase Eggman sets boss_flags.
        GameServices.camera().setMaxY((short) 0x4F0);
        for (int i = 0; i < 30; i++) runner.stepFrame(false, false, false, false, false);
        NativePositionOps.writeXPosResetSubpixel(sonic, 0xAAF);
        NativePositionOps.writeYPosResetSubpixel(sonic, 0x5AC);
        int k = -1;
        int last = shots.stream().mapToInt(Shot::k).max().orElse(0);
        for (int i = 0; i < 4000 && k < last; i++) {
            runner.stepFrame(false, false, false, false, false);
            if (k < 0 && eggmanRoutine() == 6) k = 0;
            else if (k >= 0) k++;
            for (Shot shot : shots) if (shot.k() == k) compare(movie, shot, sonic, kinds, report);
        }
    }

    private static int eggmanRoutine() {
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o instanceof com.openggf.game.soniccd.objects.boss.ttz.TtzBossEggman e && !e.isDestroyed()) return e.routine();
        }
        return -1;
    }

    private static void compare(String movie, Shot shot, AbstractPlayableSprite sonic, Map<String, Tally> kinds,
                                List<String> report) throws Exception {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        if ((cx & 0xFFFF) != shot.camX() || (cy & 0xFFFF) != shot.camY()) {
            report.add(String.format("%s row %d: camera %04X,%04X off native's %04X,%04X; not compared", movie, shot.row(),
                    cx & 0xFFFF, cy & 0xFFFF, shot.camX(), shot.camY()));
            return;
        }
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        // Parts in only one run's state (a bubble on its random sway, a part a frame apart by slot order) are masked
        // where either run draws them: {screen x, screen y, half size}.
        List<int[]> unmatched = new ArrayList<>();
        List<String> matchedStates = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !(o instanceof com.openggf.game.soniccd.objects.boss.CdBossPart part)) continue;
            String state = String.format("%02X %04X %04X %02X %02X", part.getSpawn().objectId(), part.getX() & 0xFFFF,
                    part.getY() & 0xFFFF, part.routine(), part.mapFrame());
            if (!shot.parts().contains(state)) {                        // not in native's state at this frame
                unmatched.add(new int[] {o.getX() - cx, o.getY() - cy, part.getSpawn().objectId() == 0x4C ? 32 : 16});
                continue;
            }
            matchedStates.add(state);
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx < -BOX || sx > 320 + BOX || sy < -BOX || sy > 224 + BOX) continue;
            boxes.add(new int[] {sx - BOX, sy - BOX, sx + BOX, sy + BOX});
            names.add(o.getClass().getSimpleName());
        }
        for (String state : shot.parts()) {
            if (matchedStates.contains(state)) continue;
            String[] v = state.split(" ");
            unmatched.add(new int[] {Integer.parseInt(v[1], 16) - cx, Integer.parseInt(v[2], 16) - cy,
                    "4C".equals(v[0]) ? 32 : 16});
        }
        if (boxes.isEmpty()) return;
        // Sprites that are not boss parts (the zone's and the player's air bubbles, rings, monitors, points) are not this
        // test's: where the engine draws one, a square of 16 pixels around it is left out, as for an unmatched part.
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || o instanceof com.openggf.game.soniccd.objects.boss.CdBossPart) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx < -16 || sx > 336 || sy < -16 || sy > 240) continue;
            unmatched.add(new int[] {sx, sy, 16});
        }
        RgbaImage tiles = render(LevelManager.LevelRenderOptions.tilesOnly());
        RgbaImage full = render(LevelManager.LevelRenderOptions.gameplay());
        // Object sprites only (no player, rings, HUD or water surface): where it differs from the tiles, an object draws.
        RgbaImage objects = render(new LevelManager.LevelRenderOptions(false, true, false, false, false, false, false));
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        Path out = Path.of("target", "soniccd-boss-ttz");
        Files.createDirectories(out);
        ScreenshotCapture.savePNG(full, out.resolve("engine-" + movie + "-row" + shot.row() + ".png"));
        int[][] players = {{sonic.getCentreX() - cx, sonic.getCentreY() - cy}, {shot.playerX() - cx, shot.playerY() - cy}};
        // Where two parts' sprites overlap, which one is on top follows their slots (the draw queue's order), and the
        // free slots are the allocator's: those pixels are tallied apart ("<kind> overlap") and not asserted.
        int[][] cover = new int[224][320];
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !(o instanceof com.openggf.game.soniccd.objects.boss.CdBossPart part)) continue;
            int id = part.getSpawn().objectId();
            int half = id == 0x4E ? 8 : id == 0x4F ? 16 : 32;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            for (int y = Math.max(0, sy - half); y < Math.min(224, sy + half); y++) {
                for (int x = Math.max(0, sx - half); x < Math.min(320, sx + half); x++) cover[y][x]++;
            }
        }
        boolean[][] taken = new boolean[224][320];
        // Diagnostic picture (target/soniccd-boss-ttz/diff-*.png): the native frame, with every compared pixel marked
        // green where equal and red where it differs (overlap pixels yellow/magenta).
        RgbaImage diff = new RgbaImage(320, 224, new int[320 * 224]);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) diff.setArgb(x, y, (reference.argb(x, y) >> 2) & 0xFF3F3F3F | 0xFF000000);
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int same = 0, total = 0, overlapSame = 0, overlapTotal = 0;
            for (int y = Math.max(0, b[1]); y < Math.min(224, b[3]); y++) {
                for (int x = Math.max(0, b[0]); x < Math.min(320, b[2]); x++) {
                    if (taken[y][x] || objects.argb(x, y) == tiles.argb(x, y)) continue;
                    boolean player = false;
                    for (int[] p : players) player |= Math.abs(x - p[0]) < 24 && y > p[1] - 32 && y < p[1] + 32;
                    for (int[] u : unmatched) player |= Math.abs(x - u[0]) < u[2] && Math.abs(y - u[1]) < u[2];
                    if (player || (x < 128 && y < 56) || (x < 64 && y >= 200)) continue;
                    taken[y][x] = true;
                    boolean equal = TestSonicCdSharedObjectPictures.level(reference.argb(x, y))
                            == TestSonicCdSharedObjectPictures.level(full.argb(x, y));
                    if (cover[y][x] > 1) {
                        overlapTotal++;
                        if (equal) overlapSame++;
                        diff.setArgb(x, y, equal ? 0xFFFFFF00 : 0xFFFF00FF);
                        continue;
                    }
                    total++;
                    if (equal) same++;
                    diff.setArgb(x, y, equal ? 0xFF00C000 : 0xFFFF0000);
                }
            }
            if (overlapTotal > 0) {
                Tally o = kinds.computeIfAbsent(names.get(i) + " overlap", n -> new Tally());
                o.same += overlapSame;
                o.total += overlapTotal;
            }
            if (total == 0) continue;
            Tally t = kinds.computeIfAbsent(names.get(i), n -> new Tally());
            t.same += same;
            t.total += total;
            String label = movie + "@" + shot.row();
            if (!t.shots.contains(label)) t.shots.add(label);
            report.add(String.format("%s row %d %s box(%d,%d): %d/%d", movie, shot.row(), names.get(i),
                    (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, same, total));
        }
        ScreenshotCapture.savePNG(diff, out.resolve("diff-" + movie + "-row" + shot.row() + ".png"));
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
