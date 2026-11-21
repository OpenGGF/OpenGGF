package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.BufferedReader;
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
 * Native check for the Collision Chaos background of all ten stage programs: the production renderer at
 * the level frames of each stage's two native screenshots (idle start, and the end of the 10-second
 * Right-held run) against the BizHawk frames in {@code native/rehost-fixtures/<payload>/}.
 *
 * <p>The v5 trace carries the foreground camera only, so the camera is frozen and set to the trace's
 * {@code camera_x/camera_y} before every level frame; the scroll handler then sees the native camera path
 * and has to produce the native background by itself. The camera advances only on level frames (the trace
 * repeats {@code gameplay_frame_counter} on lag rows). HUD, the player and every object the engine does not
 * host yet (the level's placement table) are masked, leaving terrain and background.
 *
 * <p>The native video shows the scroll and palette buffers the previous level frame wrote: the VInt that
 * starts the frame uploads them before this frame's logic runs, while the trace row is sampled after that
 * logic. So native level frame N is compared with the engine after level frame N-1 (same rule as
 * {@code TestPpzR11aBackgroundPicture}).
 */
final class CczNativePictureCheck {
    private CczNativePictureCheck() { }

    static final CdStagePayload[] STAGES = {
            CdStagePayload.R31A, CdStagePayload.R31B, CdStagePayload.R31C, CdStagePayload.R31D,
            CdStagePayload.R32A, CdStagePayload.R32B, CdStagePayload.R32C, CdStagePayload.R32D,
            CdStagePayload.R33C, CdStagePayload.R33D};

    /** One trace row: level frame counter, foreground camera, player centre. */
    record Row(int levelFrame, int cameraX, int cameraY, int playerX, int playerY, int playerRoutine, int input) { }

    /** One native screenshot: its trace row, the level frame the row reports and the PNG. */
    record Shot(String name, int traceRow, int levelFrames, Path png) { }

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    static List<Row> rows(CdStagePayload payload) throws Exception {
        Path file = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/physics.csv");
        List<Row> out = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String[] header = reader.readLine().split(",");
            int frame = indexOf(header, "gameplay_frame_counter");
            int cx = indexOf(header, "camera_x");
            int cy = indexOf(header, "camera_y");
            int px = indexOf(header, "player_x");
            int py = indexOf(header, "player_y");
            int routine = indexOf(header, "player_routine");
            int input = indexOf(header, "input");
            for (String line; (line = reader.readLine()) != null; ) {
                String[] c = line.split(",");
                out.add(new Row(Integer.parseInt(c[frame], 16), Integer.parseInt(c[cx], 16),
                        Integer.parseInt(c[cy], 16), Integer.parseInt(c[px], 16), Integer.parseInt(c[py], 16),
                        Integer.parseInt(c[routine], 16), Integer.parseInt(c[input], 16)));
            }
        }
        return out;
    }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) if (header[i].equals(name)) return i;
        throw new IllegalStateException("physics.csv has no " + name);
    }

    static List<Shot> shots(CdStagePayload payload) throws Exception {
        Path manifest = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json");
        JsonNode screenshots = new ObjectMapper().readTree(manifest.toFile()).get("screenshots");
        List<Shot> out = new ArrayList<>();
        for (String name : List.of("early", "end")) {
            JsonNode shot = screenshots.get(name);
            out.add(new Shot(name, shot.get("trace_row").asInt(), shot.get("level_frames").asInt(),
                    manifest.resolveSibling(shot.get("file").asText())));
        }
        return out;
    }

    /** The framebuffer, the camera it was drawn with and the placement table, read before the boot closes. */
    record Rendered(RgbaImage image, int cameraX, int cameraY, List<CdLevel.CdPlacement> placements) { }

    /**
     * Boots the stage once and captures the production renderer after each of {@code targets} level frames
     * (ascending). The camera is frozen and set to the trace camera of the row that first reports each level
     * frame; nothing else is taken from the native run.
     */
    static List<Rendered> renderEngineFrames(HeadlessGameBoot boot, CdDiscDataSource disc, CdStagePayload payload,
                                             List<Row> rows, int... targets) throws Exception {
        List<Rendered> out = new ArrayList<>();
        // The title card's scrollLock and the dead player's skipped LevelScroll reach the scroll handler from the
        // engine (CdLevelScrollGate). The engine's player plays the fixture's BK2 buttons (comparison only, as the
        // object picture tests do), so it dies where the native one does while it follows the route; the camera is
        // frozen and set to the trace's path, so the background sees the native camera even where it does not.
        boot.boot(disc, new SonicCdGameModule(),
                SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
        GameServices.camera().setFrozen(true);
        var runner = new com.openggf.tests.HeadlessTestRunner(GameServices.camera().getFocusedSprite());
        // The loaded stage is level frame 1, that iteration's scroll, palette cycle and tile animation included
        // (CdFirstMainLoopIteration): stepping starts at level frame 2.
        int previous = 1;
        int next = 0;
        for (Row row : rows) {
            if (row.levelFrame() == previous) continue;           // lag: the level did not advance
            if (row.levelFrame() > targets[targets.length - 1]) break;
            previous = row.levelFrame();
            GameServices.camera().setX((short) row.cameraX());
            GameServices.camera().setY((short) row.cameraY());
            int input = row.input();
            runner.stepFrame((input & 1) != 0, (input & 2) != 0, (input & 4) != 0, (input & 8) != 0, (input & 0x70) != 0);
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
        return new Rendered(ScreenshotCapture.captureFramebuffer(320, 224),
                GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                ((CdLevel) GameServices.level().getCurrentLevel()).placements());
    }

    /** Result of one picture comparison; {@code ignored[x][y]} marks the pixels that were masked out. */
    record Comparison(int same, int total, int minX, int minY, int maxX, int maxY, boolean[][] ignored) {
        boolean passes() { return total > 20000 && same == total; }
    }

    /** Half-width of the box masked around each placement the engine does not host yet. */
    static final int OBJECT_MARGIN = 32;

    /**
     * Sprites of objects the engine does not host that are not at their placement: they were spawned or moved
     * during the run, so the placement boxes cannot cover them. Screen boxes {x0, y0, x1, y1} (x1 and y1
     * exclusive) read off the native end screenshots of the first report's mismatch clusters, each checked against
     * the native picture to be a sprite: R31A Amy Rose and her hearts, the left-edge machine parts and a spark;
     * R31B two rings at the left edge; R32D a floating platform and a ring; R33C and R33D the act 3 boss machine and
     * a ring. Only end shots have
     * any, and the share of the frame they cover is printed with the result.
     */
    private static final java.util.Map<CdStagePayload, int[][]> UNHOSTED_AT_END = java.util.Map.of(
            CdStagePayload.R31A, new int[][] {{0, 54, 24, 86}, {0, 102, 24, 118}, {24, 54, 94, 124}, {118, 8, 132, 17}},
            CdStagePayload.R31B, new int[][] {{12, 84, 30, 128}},
            // The platform is hosted now and the engine draws it; its right edge column (x 158, y 171-173)
            // differs from native at this frame, which is the object lane's to explain, not the background's.
            CdStagePayload.R32D, new int[][] {{90, 146, 160, 182}, {206, 208, 224, 222}},
            CdStagePayload.R33C, new int[][] {{24, 112, 174, 214}, {8, 54, 20, 74}},
            CdStagePayload.R33D, new int[][] {{24, 112, 174, 214}, {8, 54, 20, 74}});

    static Comparison compare(RgbaImage engine, RgbaImage reference, Rendered rendered, Row row) {
        return compare(engine, reference, rendered, row, OBJECT_MARGIN);
    }

    static Comparison compare(RgbaImage engine, RgbaImage reference, Rendered rendered, Row row, int margin) {
        return compare(engine, reference, rendered, row, margin, new int[0][]);
    }

    static Comparison compare(RgbaImage engine, RgbaImage reference, Rendered rendered, Row row, int margin,
                              int[][] extraBoxes) {
        boolean[][] masked = new boolean[320][224];
        for (int[] box : extraBoxes) maskBox(masked, box[0], box[1], box[2], box[3]);
        // The player: x_pos/y_pos are the centre (Sonic's sprite is about 40 px tall).
        maskBox(masked, row.playerX() - rendered.cameraX() - 36, row.playerY() - rendered.cameraY() - 44,
                row.playerX() - rendered.cameraX() + 36, row.playerY() - rendered.cameraY() + 44);
        for (CdLevel.CdPlacement placement : rendered.placements()) {
            int sx = placement.x() - rendered.cameraX();
            int sy = (placement.yWord() & 0xFFF) - rendered.cameraY();
            maskBox(masked, sx - margin, sy - margin, sx + margin, sy + margin);
        }
        int same = 0, total = 0;
        int minX = 999, minY = 999, maxX = -1, maxY = -1;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (x < 120 && y < 56 || x < 56 && y >= 192 || x >= 224 && y < 40) masked[x][y] = true;   // HUD
                if (masked[x][y]) continue;
                total++;
                if (level(reference.argb(x, y)) == level(engine.argb(x, y))) same++;
                else {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
        }
        return new Comparison(same, total, minX, minY, maxX, maxY, masked);
    }

    /**
     * For each screen line, the horizontal shift (-16..16) that best aligns the engine line with the native
     * line, as {@code y:shift(matching/compared)} for every line whose best shift is not zero. A diagnostic
     * for a failing stage: it tells per-line scroll errors from missing art.
     */
    static String lineShifts(RgbaImage engine, RgbaImage reference) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < 224; y++) {
            int bestShift = 0, bestScore = -1, zeroScore = 0;
            for (int shift = -16; shift <= 16; shift++) {
                int score = 0;
                for (int x = 120; x < 224; x++) {                       // clear of the HUD columns
                    int ex = x + shift;
                    if (ex < 0 || ex >= 320) continue;
                    if (level(engine.argb(ex, y)) == level(reference.argb(x, y))) score++;
                }
                if (shift == 0) zeroScore = score;
                if (score > bestScore) { bestScore = score; bestShift = shift; }
            }
            if (bestShift != 0 && bestScore > zeroScore + 8) out.append(y).append(':').append(bestShift)
                    .append('(').append(zeroScore).append("->").append(bestScore).append(") ");
        }
        return out.toString();
    }

    /** Connected groups of unmasked differing pixels (8-neighbour, 3 px gap): bbox and size, largest first. */
    static String clusters(RgbaImage engine, RgbaImage reference, boolean[][] ignored, Rendered rendered) {
        boolean[][] bad = new boolean[320][224];
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                bad[x][y] = !ignored[x][y] && level(engine.argb(x, y)) != level(reference.argb(x, y));
            }
        }
        boolean[][] seen = new boolean[320][224];
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (!bad[x][y] || seen[x][y]) continue;
                int[] box = {x, y, x, y, 0};
                java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
                queue.add(new int[] {x, y});
                seen[x][y] = true;
                while (!queue.isEmpty()) {
                    int[] p = queue.poll();
                    box[0] = Math.min(box[0], p[0]); box[1] = Math.min(box[1], p[1]);
                    box[2] = Math.max(box[2], p[0]); box[3] = Math.max(box[3], p[1]);
                    box[4]++;
                    for (int dy = -3; dy <= 3; dy++) {
                        for (int dx = -3; dx <= 3; dx++) {
                            int nx = p[0] + dx, ny = p[1] + dy;
                            if (nx >= 0 && nx < 320 && ny >= 0 && ny < 224 && bad[nx][ny] && !seen[nx][ny]) {
                                seen[nx][ny] = true;
                                queue.add(new int[] {nx, ny});
                            }
                        }
                    }
                }
                out.add(box);
            }
        }
        out.sort((a, b) -> b[4] - a[4]);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(8, out.size()); i++) {
            int[] b = out.get(i);
            text.append(String.format("[x%d-%d y%d-%d n=%d] ", b[0], b[2], b[1], b[3], b[4]));
        }
        StringBuilder boxes = new StringBuilder(" placements(screen):");
        for (CdLevel.CdPlacement placement : rendered.placements()) {
            int sx = placement.x() - rendered.cameraX();
            int sy = (placement.yWord() & 0xFFF) - rendered.cameraY();
            if (sx > -64 && sx < 384 && sy > -64 && sy < 288) boxes.append(String.format(" (%d,%d)", sx, sy));
        }
        return text.append(boxes).toString();
    }

    /** Engine | native | difference (masked pixels grey), side by side, for whoever reads a failure. */
    static RgbaImage panel(RgbaImage engine, RgbaImage reference, boolean[][] ignored) {
        RgbaImage out = new RgbaImage(960, 224, new int[960 * 224]);
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                out.setArgb(x, y, engine.argb(x, y));
                out.setArgb(320 + x, y, reference.argb(x, y));
                out.setArgb(640 + x, y, ignored[x][y] ? 0xFF303030
                        : level(engine.argb(x, y)) == level(reference.argb(x, y)) ? 0xFF000000 : 0xFFFF00FF);
            }
        }
        return out;
    }

    private static void maskBox(boolean[][] masked, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(224, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(320, x1); x++) masked[x][y] = true;
        }
    }

    /** Engine level frames around the early shot's N-1 that the alignment sweep renders (diagnostic only). */
    private static final int SWEEP_BEFORE = 4;
    private static final int SWEEP_AFTER = 4;

    /** Per stage: matching pixels of the early shot against engine frames N-5, N-1 and N+3 (offsets -4, 0, +4). */
    private static final java.util.Map<CdStagePayload, int[]> SWEEP_AT_ZERO = new java.util.EnumMap<>(CdStagePayload.class);

    /**
     * Native shot of {@code stage} at level frame N against the engine after level frame N-1. The early shot is
     * taken while the camera is still (Sonic idles), so the engine is also rendered a few level frames either
     * side and every offset is printed: this shows whether a mismatch is a scroll-state error or a frame
     * alignment error. Only offset 0 is asserted.
     */
    private static String checkStage(HeadlessGameBoot boot, CdDiscDataSource disc, CdStagePayload payload, Path out)
            throws Exception {
        List<Row> rows = rows(payload);
        List<Shot> shots = shots(payload);
        Shot early = shots.get(0);
        Shot end = shots.get(1);
        int sweepCount = SWEEP_BEFORE + 1 + SWEEP_AFTER;
        int[] targets = new int[sweepCount + 1];
        for (int k = 0; k < sweepCount; k++) targets[k] = early.levelFrames() - 1 - SWEEP_BEFORE + k;
        targets[sweepCount] = end.levelFrames() - 1;
        List<Rendered> rendered = renderEngineFrames(boot, disc, payload, rows, targets);
        StringBuilder summary = new StringBuilder();
        StringBuilder failed = new StringBuilder();
        StringBuilder sweep = new StringBuilder();
        int[] sweepMatch = new int[sweepCount];
        for (int k = 0; k < sweepCount; k++) {
            RgbaImage reference = ScreenshotCapture.loadPNG(early.png());
            Row row = rows.get(early.traceRow());
            Comparison c = compare(rendered.get(k).image(), reference, rendered.get(k), row);
            sweep.append(String.format(" %+d:%d", k - SWEEP_BEFORE, c.same()));
            sweepMatch[k] = c.same();
        }
        System.out.println("CCZ sweep " + payload + " early (engine level frame N-1+offset: matching pixels):" + sweep);
        SWEEP_AT_ZERO.put(payload, new int[] {sweepMatch[0], sweepMatch[SWEEP_BEFORE], sweepMatch[sweepCount - 1]});
        for (int i = 0; i < 2; i++) {
            Shot shot = i == 0 ? early : end;
            Rendered engineFrame = rendered.get(i == 0 ? SWEEP_BEFORE : sweepCount);
            RgbaImage engine = engineFrame.image();
            RgbaImage reference = ScreenshotCapture.loadPNG(shot.png());
            Row row = rows.get(shot.traceRow());
            assertEquals(shot.levelFrames(), row.levelFrame(), payload + " " + shot.name() + ": trace row level frame");
            Comparison result = compare(engine, reference, engineFrame, row, OBJECT_MARGIN,
                    i == 1 ? UNHOSTED_AT_END.getOrDefault(payload, new int[0][]) : new int[0][]);
            ScreenshotCapture.savePNG(panel(engine, reference, result.ignored()), out.resolve(payload + "-" + shot.name() + ".png"));
            int ignored = 0;
            for (boolean[] column : result.ignored()) for (boolean b : column) if (b) ignored++;
            String line = String.format("%s %s: native level frame %d = engine level frame %d (camera %X,%X): %d/%d unmasked pixels match (%d%% of the frame masked)%s",
                    payload, shot.name(), shot.levelFrames(), shot.levelFrames() - 1, engineFrame.cameraX(),
                    engineFrame.cameraY(), result.same(), result.total(), ignored * 100 / (320 * 224),
                    result.same() == result.total() ? "" : String.format("; differing pixels lie in x %d-%d, y %d-%d",
                            result.minX(), result.maxX(), result.minY(), result.maxY()));
            summary.append(line).append(System.lineSeparator());
            System.out.println("CCZ background: " + line);
            if (!result.passes()) System.out.println("CCZ clusters " + payload + " " + shot.name() + ": "
                    + clusters(engine, reference, result.ignored(), engineFrame));
            if (!result.passes()) System.out.println("CCZ line shifts " + payload + " " + shot.name() + ": "
                    + lineShifts(engine, reference));
            if (!result.passes()) failed.append(line).append(System.lineSeparator());
        }
        if (failed.length() > 0) throw new AssertionError(failed.toString());
        return summary.toString();
    }

    /**
     * Checks both native screenshots of every stage on one headless boot: a second GL boot in the same JVM
     * fails with GL error 0x502 in the native sprite stencil save, so the GL context is created once and each
     * stage opens its own gameplay session on it (the way HeadlessGameBoot.reboot does).
     */
    static void runAll(CdStagePayload... payloads) throws Exception {
        for (CdStagePayload payload : payloads) {
            CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json");   // skip when absent
        }
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            Path out = Path.of("target", "soniccd-ccz-background");
            Files.createDirectories(out);
            StringBuilder summary = new StringBuilder();
            AssertionError first = null;
            for (int i = 0; i < payloads.length; i++) {
                if (i > 0) com.openggf.game.session.SessionManager.closeGameplaySession();
                try {
                    summary.append(checkStage(boot, disc, payloads[i], out));
                } catch (AssertionError e) {
                    summary.append(payloads[i]).append(" FAILED: ").append(e.getMessage()).append(System.lineSeparator());
                    if (first == null) first = e;
                }
            }
            Files.writeString(out.resolve("report.txt"), summary.toString());
            if (first != null) throw new AssertionError(summary.toString(), first);
            // The comparison must be able to fail: an engine four level frames early or late has to be strictly
            // worse in every stage (one frame is not enough, an idle picture can stay equal for a frame or two).
            for (var entry : SWEEP_AT_ZERO.entrySet()) {
                int[] m = entry.getValue();
                assertTrue(m[1] > m[0] && m[1] > m[2], entry.getKey() + ": four frames of misalignment not detected ("
                        + m[0] + ", " + m[1] + ", " + m[2] + " matching pixels at -4, 0, +4)");
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }
}
