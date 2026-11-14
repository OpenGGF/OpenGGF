package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.GameLoop;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.tools.HeadlessGameBoot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Native check for the Quartz Quadrant backgrounds: for each of R51A-R53D the production renderer
 * against the two BizHawk screenshots of the stage's rehost fixture (early: nothing pressed; end: the last
 * trace row). The fixture trace carries no background-camera globals, only the foreground camera and the
 * player (physics.csv), so the camera path is the only thing taken from the native run: before every
 * engine step the frozen camera is set to the native camera of that level frame, the same way the Palmtree
 * Panic picture test does it, and the scroll handler and palette cycle have to reproduce the rest.
 *
 * <p>One boot renders all ten stages ({@code TestQqzBackgroundPictures}): a second headless GL boot after a render in
 * the same JVM fails (GL error 0x502 in the native sprite pass), so later stages are loaded into the first boot.
 *
 * <p>The compared area is every pixel that is not under the HUD, Sonic, or a placement-table object (the
 * objects are unhosted). The native video shows the buffers the previous level frame wrote, while the
 * trace counter is read after that frame's logic, so native level frame N is compared with the engine
 * after level frame N-1 (see TestPpzR11aBackgroundPicture).
 */
final class QqzPictureSupport {
    private QqzPictureSupport() { }

    /** One physics.csv row: the level frame and the native camera and player at it. */
    record Row(int levelFrame, int cameraX, int cameraY, int playerX, int playerY) { }

    /** One screenshot: its file, the level frame it shows. */
    record Shot(String kind, Path file, int levelFrame, int traceRow) { }

    /** A native object slot near the player at one trace row (aux_state.jsonl {@code object_near}). */
    record NativeObject(int type, int x, int y) { }

    static List<Row> rows(String stage) throws Exception {
        Path csv = CdTestMedia.nativeFile("rehost-fixtures/" + stage + "/trace/physics.csv");
        List<String> lines = Files.readAllLines(csv);
        List<String> header = List.of(lines.get(0).split(","));
        int gfc = header.indexOf("gameplay_frame_counter");
        int cx = header.indexOf("camera_x");
        int cy = header.indexOf("camera_y");
        int px = header.indexOf("player_x");
        int py = header.indexOf("player_y");
        List<Row> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            out.add(new Row(Integer.parseInt(c[gfc], 16), Integer.parseInt(c[cx], 16), Integer.parseInt(c[cy], 16),
                    Integer.parseInt(c[px], 16), Integer.parseInt(c[py], 16)));
        }
        return out;
    }

    static List<Shot> shots(String stage) throws Exception {
        Path manifest = CdTestMedia.nativeFile("rehost-fixtures/" + stage + "/manifest.json");
        JsonNode screenshots = new ObjectMapper().readTree(Files.readString(manifest)).get("screenshots");
        List<Shot> out = new ArrayList<>();
        for (String kind : new String[] {"early", "end"}) {
            JsonNode node = screenshots.get(kind);
            out.add(new Shot(kind, manifest.resolveSibling(node.get("file").asText()), node.get("level_frames").asInt(),
                    node.get("trace_row").asInt()));
        }
        return out;
    }

    /** The native object slots listed by {@code object_near} at {@code traceRow} (the aux trace's frame number). */
    static List<NativeObject> nativeObjects(String stage, int traceRow) throws Exception {
        Path aux = CdTestMedia.nativeFile("rehost-fixtures/" + stage + "/trace/aux_state.jsonl");
        ObjectMapper mapper = new ObjectMapper();
        List<NativeObject> out = new ArrayList<>();
        for (String line : Files.readAllLines(aux)) {
            if (!line.contains("\"object_near\"")) continue;
            JsonNode node = mapper.readTree(line);
            if (node.get("frame").asInt() != traceRow) continue;
            out.add(new NativeObject(Integer.decode(node.get("type").asText()),
                    Integer.decode(node.get("x").asText()), Integer.decode(node.get("y").asText())));
        }
        return out;
    }

    /** The framebuffer plus what the masks need, read before the boot closes. */
    record Rendered(RgbaImage image, int cameraX, int cameraY, int playerX, int playerY,
                    List<CdLevel.CdPlacement> placements, List<int[]> rings) { }

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /**
     * Boots {@code payload}, steps once per distinct native level frame with the frozen camera set to the
     * native one, and captures after each of {@code targets} (ascending level frames).
     */
    static List<Rendered> renderEngineFrames(GameLoop loop, CdStagePayload payload, List<Row> rows,
                                             boolean load, int... targets) throws Exception {
        List<Rendered> out = new ArrayList<>();
        if (load) {
            GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        }
        GameServices.camera().setFrozen(true);
        // The loaded stage is level frame 1, that iteration's scroll and palette cycle included
        // (CdFirstMainLoopIteration): stepping starts at level frame 2.
        int previous = 1;
        int next = 0;
        for (Row row : rows) {
            if (row.levelFrame() == previous) continue;     // lag frame: the level did not advance
            if (row.levelFrame() > targets[targets.length - 1]) break;
            previous = row.levelFrame();
            GameServices.camera().setX((short) row.cameraX());
            GameServices.camera().setY((short) row.cameraY());
            loop.step();
            if (previous == targets[next]) {
                out.add(capture());
                next++;
            }
        }
        assertEquals(targets.length, out.size(), payload + ": native level frames reached");
        return out;
    }

    private static Rendered capture() {
        GraphicsManager graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        var level = GameServices.level();
        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithSpritePriority(GameServices.sprites(), true);
        graphics.flush();
        if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
        glFinish();
        var sprite = GameServices.camera().getFocusedSprite();   // the engine's own Sonic stands where the player lane put him
        return new Rendered(ScreenshotCapture.captureFramebuffer(320, 224),
                GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                sprite == null ? -1000 : sprite.getCentreX(), sprite == null ? -1000 : sprite.getCentreY(),
                ((CdLevel) GameServices.level().getCurrentLevel()).placements(),
                GameServices.level().getCurrentLevel().getRings().stream()
                        .map(r -> new int[] {r.x(), r.y()}).toList());
    }

    private static void maskBox(boolean[][] masked, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(224, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(320, x1); x++) masked[x][y] = true;
        }
    }

    /** Result of one comparison. */
    record Result(String stage, String kind, int levelFrame, int same, int total, String where) {
        boolean passes() { return same == total; }
        @Override public String toString() {
            return String.format("%s %s native level frame %d = engine level frame %d: %d/%d unmasked pixels match%s",
                    stage, kind, levelFrame, levelFrame - 1, same, total, same == total ? "" : "; " + where);
        }
    }

    static Result compare(String stage, Shot shot, Rendered engineFrame, List<Row> rows) throws Exception {
        List<NativeObject> natives = nativeObjects(stage, shot.traceRow());
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        int camX = engineFrame.cameraX();
        int camY = engineFrame.cameraY();
        boolean[][] masked = new boolean[320][224];
        for (Row row : rows) {                                  // Sonic, in the frame shown and the one before
            if (row.levelFrame() == shot.levelFrame() || row.levelFrame() == shot.levelFrame() - 1) {
                int sx = row.playerX() - row.cameraX();
                int sy = row.playerY() - row.cameraY();
                maskBox(masked, sx - 32, sy - 40, sx + 32, sy + 40);
            }
        }
        maskBox(masked, engineFrame.playerX() - camX - 32, engineFrame.playerY() - camY - 40,
                engineFrame.playerX() - camX + 32, engineFrame.playerY() - camY + 40);   // the engine's Sonic
        for (NativeObject object : natives) {                  // native objects (rings, badniks, springs ...) the engine does not host
            // A ring object (type $10 here) is a row of up to 6 rings spaced up to $20 apart (ObjRing_Deltas, Ring/Main.asm:17-32);
            // the later rings are not listed as objects of their own, so the box runs 160 px to the right.
            int reach = object.type() == 0x10 ? 200 : 40;
            maskBox(masked, object.x() - camX - 40, object.y() - camY - 40, object.x() - camX + reach, object.y() - camY + 40);
        }
        for (CdLevel.CdPlacement placement : engineFrame.placements()) {   // the engine's own placeholder boxes
            int sx = placement.x() - camX;
            int sy = (placement.yWord() & 0xFFF) - camY;
            maskBox(masked, sx - 32, sy - 32, sx + 32, sy + 32);
        }
        // Every ring of the stage: rings are hosted objects now, and the engine's player is not on the
        // native path, so rings native has collected (or whose spin frame differs) are not this check's.
        for (int[] ring : engineFrame.rings()) {
            maskBox(masked, ring[0] - camX - 12, ring[1] - camY - 12, ring[0] - camX + 12, ring[1] - camY + 12);
        }
        int same = 0, total = 0;
        int minX = 999, minY = 999, maxX = -1, maxY = -1;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (masked[x][y] || (x < 120 && y < 56) || (x < 90 && y >= 192) || (x >= 224 && y < 40)) continue;
                total++;
                if (level(reference.argb(x, y)) == level(engineFrame.image().argb(x, y))) same++;
                else {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
        }
        Path out = Path.of("target", "soniccd-qqz-background");
        Files.createDirectories(out);
        ScreenshotCapture.savePNG(engineFrame.image(),
                out.resolve("engine-" + stage.toLowerCase() + "-" + shot.kind() + ".png"));
        String where = String.format("differing pixels lie in x %d-%d, y %d-%d (camera %X,%X)", minX, maxX, minY, maxY, camX, camY);
        return new Result(stage, shot.kind(), shot.levelFrame(), same, total, where);
    }

    /**
     * Boots the first stage once and renders both screenshot frames of every stage in {@code stages}, loading each
     * further stage into the same boot (one headless GL boot per JVM; see the class comment).
     */
    static List<Result> run(String... stages) throws Exception {
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        List<Result> results = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(),
                    SonicCdZoneRegistry.zoneIndex(CdStagePayload.valueOf(stages[0])),
                    SonicCdZoneRegistry.actIndex(CdStagePayload.valueOf(stages[0])));
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            StringBuilder summary = new StringBuilder();
            for (int n = 0; n < stages.length; n++) {
                String stage = stages[n];
                List<Row> rows = rows(stage);
                List<Shot> shots = shots(stage);
                List<Rendered> rendered = renderEngineFrames(loop, CdStagePayload.valueOf(stage), rows, n > 0,
                        shots.get(0).levelFrame() - 1, shots.get(1).levelFrame() - 1);
                for (int i = 0; i < shots.size(); i++) {
                    Result result = compare(stage, shots.get(i), rendered.get(i), rows);
                    results.add(result);
                    summary.append(result).append(System.lineSeparator());
                    System.out.println("QQZ background: " + result);
                }
            }
            Path out = Path.of("target", "soniccd-qqz-background");
            Files.createDirectories(out);
            Files.writeString(out.resolve("report.txt"), summary.toString());
            return results;
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }

    /** Asserts that every unmasked pixel of both comparisons of one stage matches (3 bits per channel). */
    static void assertMatches(List<Result> results) {
        for (Result result : results) {
            assertTrue(result.total() > 20000, "too little left after masking: " + result);
            assertTrue(result.passes(), result.toString());
        }
    }
}
