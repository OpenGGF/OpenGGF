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
 * Native pictures of the Metallic Madness objects: the production renderer against the BizHawk frames the zone's
 * presentation lane took of the stage fixtures' own movies ({@code native/rehost-fixtures-mmz/<payload>/mid.json}:
 * three camera-X shots, the farthest camera Y and the last row; user-local, not committed; the test skips without
 * them). One headless GL boot loads the ten stages in turn.
 *
 * <p>The engine plays the trace's input (comparison only: nothing read from {@code physics.csv} reaches the engine
 * except the BK2 buttons, as in {@link TestMmzObjectsAppearances}) up to each shot's level frame and is compared only
 * while its player and camera still equal the trace. Each shot is its trace row's successor's picture, the state after
 * lf(row+1)-1 ({@link #pictureLevelFrame}); the engine stands at level frame N after N-1 steps from its loaded state.
 *
 * <p>What is compared: the pixels this lane's objects draw. The engine frame is rendered twice more, tiles only and
 * object sprites over those tiles; where the two differ an object sprite is drawn. Those pixels inside a box around
 * each of this lane's on-screen objects, minus the player's box, the HUD and a freed animal's neighbourhood, are compared with the native frame at
 * 3 bits per channel against the full engine frame, so the foreground's priority tiles are included but the
 * background, which belongs to other lanes, is not.
 */
@EnabledOnOs(OS.LINUX)
class TestMmzObjectsPictures {
    private static final CdStagePayload[] PAYLOADS = {CdStagePayload.R81A, CdStagePayload.R81B, CdStagePayload.R81C,
            CdStagePayload.R81D, CdStagePayload.R82A, CdStagePayload.R82B, CdStagePayload.R82C, CdStagePayload.R82D,
            CdStagePayload.R83C, CdStagePayload.R83D};
    private static final int BOX = 0x18;

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** A native shot and the engine level frame it shows ({@link #pictureLevelFrame}). */
    private record Shot(int levelFrames, Path file) { }

    /**
     * The level frame whose state a shot shows: a picture shows the state after the level frame before the one its row
     * counts (the V-INT that starts the video frame DMAs what the previous iteration wrote, Level/Main.asm:829-831), and
     * every shot here is its trace row's successor's picture ({@code probe.sh} at {@code bk2_frame_offset + row + 1} with
     * the schedule fed one frame ahead, or at {@code + row + 2} with true inputs in {@code rehost-fixtures-mmz2}, whose
     * {@code level_frames} were read from the probe's {@code levelFrames} column): lf(row+1)-1. Every shot's
     * {@code level_frames} equals it; the rule, not the field, pairs.
     */
    private static int pictureLevelFrame(List<Integer> counters, JsonNode shot) {
        int row = shot.get("trace_row").asInt();
        return row + 1 < counters.size() ? counters.get(row + 1) - 1 : counters.get(row);
    }

    /** {@code gameplay_frame_counter} of every trace row. */
    private static List<Integer> counters(Path csv) throws Exception {
        List<String> lines = Files.readAllLines(csv);
        int counter = List.of(lines.getFirst().split(",")).indexOf("gameplay_frame_counter");
        List<Integer> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) out.add(Integer.parseInt(line.split(",")[counter], 16));
        return out;
    }

    /** One native movie: the stage it plays, its label, its picture shots and its trace rows. */
    private record Source(CdStagePayload payload, String label, List<Shot> shots, Map<Integer, int[]> trace) { }

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
        for (CdStagePayload payload : PAYLOADS) {
            List<Integer> counters = counters(CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/physics.csv"));
            List<Shot> list = new ArrayList<>();
            // The presentation lane's shots (mid.json) and this lane's extra shots where objects are on screen
            // (objects/shots.json, taken with probe.sh from the same movies).
            Path mid = CdTestMedia.nativeFile("rehost-fixtures-mmz/" + payload + "/mid.json");
            for (String name : new String[] {"mid.json", "objects/shots.json"}) {
                Path index = mid.resolveSibling(name);
                if (!Files.exists(index)) continue;
                for (JsonNode shot : new ObjectMapper().readTree(index.toFile()).get("shots")) {
                    list.add(new Shot(pictureLevelFrame(counters, shot), index.resolveSibling(shot.get("file").asText())));
                }
            }
            // The second pass's shots of the fixtures' own movies (rehost-fixtures-mmz2/<payload>/shots.json): frames where
            // the spike crusher and the other second-pass objects are on screen.
            Path second = mid.getParent().getParent().resolveSibling("rehost-fixtures-mmz2").resolve(payload.name())
                    .resolve("shots.json");
            if (Files.exists(second)) {
                for (JsonNode shot : new ObjectMapper().readTree(second.toFile()).get("shots")) {
                    list.add(new Shot(pictureLevelFrame(counters, shot), second.resolveSibling(shot.get("file").asText())));
                }
            }
            org.junit.jupiter.api.Assumptions.assumeTrue(!list.isEmpty(), "No native shots for " + payload);
            // One shot per level frame: the same frame from two sets is the same picture (playStage steps one shot per frame).
            Map<Integer, Shot> byFrame = new java.util.TreeMap<>();
            for (Shot shot : list) byFrame.putIfAbsent(shot.levelFrames(), shot);
            list = new ArrayList<>(byFrame.values());
            sources.add(new Source(payload, payload.name(), list, firstRowPerLevelFrame(
                    CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/physics.csv"))));
        }
        // The movies this lane authored to reach objects the ten-second fixtures do not
        // (rehost-fixtures-mmz2/m-<name>/<payload>/{trace,shots.json}); present only where the user-local fixtures are.
        Path authoredRoot = CdTestMedia.nativeFile("rehost-fixtures-mmz/R81A/mid.json").getParent().getParent()
                .resolveSibling("rehost-fixtures-mmz2");
        try (var movies = Files.isDirectory(authoredRoot) ? Files.list(authoredRoot) : java.util.stream.Stream.<Path>empty()) {
            for (Path movie : movies.filter(m -> m.getFileName().toString().startsWith("m-")).sorted().toList()) {
                try (var stages = Files.list(movie)) {
                    for (Path dir : stages.sorted().toList()) {
                        Path index = dir.resolve("shots.json");
                        Path physics = dir.resolve("trace/physics.csv");
                        if (!Files.exists(index) || !Files.exists(physics)) continue;
                        List<Integer> counters = counters(physics);
                        List<Shot> list = new ArrayList<>();
                        for (JsonNode shot : new ObjectMapper().readTree(index.toFile()).get("shots")) {
                            list.add(new Shot(pictureLevelFrame(counters, shot), dir.resolve(shot.get("file").asText())));
                        }
                        list.sort(java.util.Comparator.comparingInt(Shot::levelFrames));
                        sources.add(new Source(CdStagePayload.valueOf(dir.getFileName().toString()),
                                movie.getFileName() + "/" + dir.getFileName(), list, firstRowPerLevelFrame(physics)));
                    }
                }
            }
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
                playStage(source.label(), source.shots(), source.trace(), kinds, report);
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
        Path out = Path.of("target", "soniccd-mmzobj");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("MMZOBJ pictures:\n" + String.join("\n", report) + "\n" + text);
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
                compare(payload, shots.get(next), sonic, kinds, report);
                next++;
            }
        }
    }

    private static void compare(String payload, Shot shot, AbstractPlayableSprite sonic,
                                Map<String, Tally> kinds, List<String> report) throws Exception {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<ObjectInstance> drawn = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !o.getClass().getName().contains(".objects.mmz.")) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            int half = o instanceof com.openggf.game.soniccd.objects.common.CdObject co
                    ? Math.max(BOX, Math.max(co.getOnScreenHalfWidth(), co.getOnScreenHalfHeight()) + 8) : BOX;
            if (sx < -half || sx > 320 + half || sy < -half || sy > 224 + half) continue;
            boxes.add(new int[] {sx - half, sy - half, sx + half, sy + half});
            names.add(o.getClass().getSimpleName());
            drawn.add(o);
        }
        if (boxes.isEmpty()) return;

        RgbaImage tiles = render(LevelManager.LevelRenderOptions.tilesOnly());
        RgbaImage objectsOnly = render(new LevelManager.LevelRenderOptions(false, true, false, false, false, false, false));
        RgbaImage full = render(LevelManager.LevelRenderOptions.gameplay());
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        Path out = Path.of("target", "soniccd-mmzobj");
        Files.createDirectories(out);
        ScreenshotCapture.savePNG(full, out.resolve("engine-" + payload.replace('/', '-') + "-lf" + shot.levelFrames() + ".png"));

        int psx = (sonic.getCentreX() - cx), psy = (sonic.getCentreY() - cy);
        // A freed animal (common object $32) is in the engine's object list but is not drawn there (another lane's art),
        // while the native frame shows it: its $10 pixels around are not this lane's to compare.
        // Rings are compared: Metallic Madness rings draw at priority 0, in front of the scarab's beetle (bucket 1)
        // (Ring/Main.asm:158-166).
        List<int[]> animals = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed()) continue;
            if (o instanceof com.openggf.game.soniccd.objects.common.CdAnimal) {
                animals.add(new int[] {o.getX() - cx, o.getY() - cy, 0x10});
            }
        }
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int same = 0, total = 0;
            for (int y = Math.max(0, b[1]); y < Math.min(224, b[3]); y++) {
                for (int x = Math.max(0, b[0]); x < Math.min(320, b[2]); x++) {
                    if (tiles.argb(x, y) == objectsOnly.argb(x, y)) continue;           // no object sprite here
                    if (Math.abs(x - psx) < 24 && y > psy - 32 && y < psy + 32) continue;  // the player is on top
                    if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;            // HUD
                    final int fx = x, fy = y;
                    if (animals.stream().anyMatch(a -> Math.abs(fx - a[0]) < a[2] && Math.abs(fy - a[1]) < a[2])) continue;
                    total++;
                    if (level(reference.argb(x, y)) == level(full.argb(x, y))) same++;
                }
            }
            if (total == 0) continue;
            if (same * 100L < total * 99L) {
                // What else is drawn inside the box (other lanes' objects, priority bucket): the diagnosis of a mismatch.
                StringBuilder others = new StringBuilder();
                for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                    if (o.isDestroyed() || o.getClass().getName().contains(".objects.mmz.")) continue;
                    int sx = o.getX() - cx, sy = o.getY() - cy;
                    if (sx < b[0] - 16 || sx > b[2] + 16 || sy < b[1] - 16 || sy > b[3] + 16) continue;
                    others.append(String.format(" %s(id %02X)@(%d,%d)/prio %d slot %d", o.getClass().getSimpleName(),
                            o.getSpawn().objectId(), sx, sy, o.getPriorityBucket(),
                            o instanceof com.openggf.level.objects.AbstractObjectInstance a ? a.getSlotIndex() : -1));
                }
                ObjectInstance self = drawn.get(i);
                report.add(String.format("%s lf %d %s (prio %d slot %d) box(%d,%d) below 99%%: %d/%d; other objects in the box:%s",
                        payload, shot.levelFrames(), names.get(i), self.getPriorityBucket(),
                        self instanceof com.openggf.level.objects.AbstractObjectInstance a ? a.getSlotIndex() : -1,
                        (b[0] + b[2]) / 2, (b[1] + b[3]) / 2, same, total, others));
            }
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
