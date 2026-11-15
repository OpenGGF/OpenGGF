package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.NullAudioBackend;
import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

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
 * Native check for the Palmtree Panic background (scroll deformation, background camera and palette
 * cycling) in every period of acts 1 to 3, against the user-local rehost fixtures
 * {@code native/rehost-fixtures/<payload>/}: the early screenshot (nothing pressed) and the end
 * screenshot (after about 800 level frames of the fixed Right-and-jump route).
 *
 * <p>The production renderer is driven with the camera of the fixture's v5 trace
 * ({@code physics.csv} camera_x/camera_y) frame by frame, the camera frozen so nothing but the
 * camera path comes from the native run. The fixtures carry no {@code cameraBg*} globals, so the
 * background camera is checked through the picture only. HUD, Sonic (placed from the trace's player
 * position) and every placement box are masked, leaving background and terrain.
 *
 * <p>Alignment (calibrated on R11A, whose scroll was certified against an older reference, and
 * applied unchanged to every other stage): the video frame of level frame N shows the buffers level
 * frame N-1 wrote, because the V-blank that starts the frame uploads them before this frame's logic
 * runs, while the trace row is sampled after that logic. The engine is therefore captured after level
 * frame N-1. The loaded stage is level frame 1 with that iteration's scroll and palette cycle already run
 * ({@code CdFirstMainLoopIteration}), so the first step is level frame 2. The title card's {@code scrollLock}
 * reaches the scroll handler from the hosted title card ({@code CdLevelScrollGate}); nothing but the camera
 * path and the player position below comes from the native run.
 */
@EnabledOnOs(OS.LINUX)
class TestPpzStageFixturePictures {
    /** Fraction of unmasked pixels that must agree, in thousandths. */
    private static final int REQUIRED_PER_MILLE = 995;

    private record Row(int index, int levelFrame, int cameraX, int cameraY, int playerX, int playerY) { }

    private record Shot(String name, int levelFrame, Path file) { }

    private record Rendered(RgbaImage image, int cameraX, int cameraY, int playerX, int playerY,
                            List<CdLevel.CdPlacement> placements, List<int[]> nativeObjects) { }

    private record Score(int same, int total, String where) {
        boolean passes() { return total > 20000 && same * 1000L >= total * (long) REQUIRED_PER_MILLE; }
        @Override public String toString() { return same + "/" + total + where; }
    }

    private static Path fixture(CdStagePayload payload, String name) {
        return CdTestMedia.nativeFile("rehost-fixtures/" + payload.name() + "/" + name);
    }

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    private static List<Row> rows(CdStagePayload payload) throws Exception {
        List<String> lines = Files.readAllLines(fixture(payload, "trace/physics.csv"));
        String[] header = lines.get(0).split(",");
        int cx = indexOf(header, "camera_x"), cy = indexOf(header, "camera_y"),
                gf = indexOf(header, "gameplay_frame_counter"), px = indexOf(header, "player_x"),
                py = indexOf(header, "player_y");
        List<Row> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            out.add(new Row(hex(c[0]), hex(c[gf]), hex(c[cx]), hex(c[cy]), hex(c[px]), hex(c[py])));
        }
        return out;
    }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) if (header[i].equals(name)) return i;
        throw new IllegalStateException("physics.csv has no column " + name);
    }

    private static int hex(String value) {
        return Integer.parseInt(value, 16);
    }

    /**
     * Where the native objects were at each trace row: the v5 aux {@code object_near} events (slot, type,
     * centre x/y). Used only to mask the picture where an unhosted native object, or an effect it
     * spawned, is drawn; nothing is fed to the engine from it.
     */
    private static java.util.Map<Integer, List<int[]>> nativeObjects(CdStagePayload payload) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        java.util.Map<Integer, List<int[]>> out = new java.util.HashMap<>();
        for (String line : Files.readAllLines(fixture(payload, "trace/aux_state.jsonl"))) {
            if (!line.contains("\"object_near\"")) continue;
            JsonNode node = mapper.readTree(line);
            out.computeIfAbsent(node.get("frame").asInt(), k -> new ArrayList<>()).add(new int[] {
                    Integer.decode(node.get("x").asText()), Integer.decode(node.get("y").asText())});
        }
        return out;
    }

    private static List<Shot> shots(CdStagePayload payload) throws Exception {
        JsonNode screenshots = new ObjectMapper().readTree(fixture(payload, "manifest.json").toFile()).get("screenshots");
        List<Shot> out = new ArrayList<>();
        for (String name : List.of("early", "end")) {
            JsonNode node = screenshots.get(name);
            out.add(new Shot(name, node.get("level_frames").asInt(), fixture(payload, node.get("file").asText())));
        }
        return out;
    }

    /**
     * Captures the production renderer after level frame {@code target - 1} for each shot, on a stage
     * that is already loaded. Level frame 1 is the load frame, so stepping starts at level frame 2
     * (the same convention as {@code TestPpzR11aBackgroundPicture}).
     */
    private static List<Rendered> render(GameLoop loop, CdStagePayload payload, List<Row> rows,
                                         List<Shot> shots, java.util.Map<Integer, List<int[]>> objects) {
        List<Rendered> out = new ArrayList<>();
        GameServices.camera().setFrozen(true);
        int previous = 1;
        int next = 0;
        for (Row row : rows) {
            if (row.levelFrame() == previous) continue;           // lag: the level did not advance
            if (row.levelFrame() > shots.get(shots.size() - 1).levelFrame()) break;
            previous = row.levelFrame();
            GameServices.camera().setX((short) row.cameraX());
            GameServices.camera().setY((short) row.cameraY());
            // Scroll isolation only: Present act 1 gates its background Y on the player's X
            // (Scroll (Act 1 Present).asm:136,241), so there the sprite is placed where the trace has it. No other
            // stage's scroll reads the player, and a sprite moved along the native path without its own motion can
            // be killed where the native one is not, which would stop LevelScroll (CdLevelScrollGate).
            if (payload.stage().actNumber() == 1 && payload.stage().period() == CdPeriod.PRESENT) {
                var sprite = GameServices.camera().getFocusedSprite();
                sprite.setCentreX((short) row.playerX());
                sprite.setCentreY((short) row.playerY());
            }
            loop.step();
            if (previous == shots.get(next).levelFrame() - 1) {
                out.add(capture(row, objects.getOrDefault(row.index(), List.of())));
                if (++next == shots.size()) break;
            }
        }
        assertEquals(shots.size(), out.size(), payload + ": native level frames reached");
        return out;
    }

    private static Rendered capture(Row row, List<int[]> nativeObjects) {
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
                row.playerX(), row.playerY(), ((CdLevel) GameServices.level().getCurrentLevel()).placements(), nativeObjects);
    }

    private static boolean[][] mask(Rendered r) {
        boolean[][] masked = new boolean[320][224];
        int sonicX = r.playerX() - r.cameraX();
        int sonicY = r.playerY() - r.cameraY();
        maskBox(masked, sonicX - 28, sonicY - 36, sonicX + 28, sonicY + 36);
        for (CdLevel.CdPlacement placement : r.placements()) {
            int sx = placement.x() - r.cameraX();
            int sy = (placement.yWord() & 0xFFF) - r.cameraY();
            maskBox(masked, sx - 32, sy - 32, sx + 32, sy + 32);
        }
        for (int[] object : r.nativeObjects()) {
            int sx = object[0] - r.cameraX();
            int sy = object[1] - r.cameraY();
            maskBox(masked, sx - 40, sy - 40, sx + 40, sy + 40);
        }
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if ((x < 120 && y < 56) || (x < 96 && y >= 192) || (x >= 224 && y < 40)) masked[x][y] = true;
            }
        }
        return masked;
    }

    private static void maskBox(boolean[][] masked, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(224, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(320, x1); x++) masked[x][y] = true;
        }
    }

    private static Score compare(RgbaImage engine, RgbaImage reference, boolean[][] masked) {
        int same = 0, total = 0, minX = 999, minY = 999, maxX = -1, maxY = -1;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (masked[x][y]) continue;
                total++;
                if (level(reference.argb(x, y)) == level(engine.argb(x, y))) {
                    same++;
                } else {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
        }
        String where = same == total ? "" : String.format("; differing pixels lie in x %d-%d, y %d-%d", minX, maxX, minY, maxY);
        return new Score(same, total, where);
    }

    /**
     * Shots that do not reach {@link #REQUIRED_PER_MILLE}. They are reported, not asserted, and the test fails
     * if one starts to pass so it gets promoted. Evidence per shot is in {@code PPZ23-report.md}.
     */
    private static final java.util.Map<String, String> UNCERTIFIED = java.util.Map.of(
            "R11A end", "Present past the first 3D ramp (X >= $800): the ramp control bit is not exposed, so the "
                    + "cloud and mountain offsets (Scroll (Act 1 Present).asm:203-229) are missing",
            "R11D end", "band rows at far camera X need a Plane B window per band (shared renderer); static cloud "
                    + "rows forbid following the layers with the single window",
            "R12D end", "same renderer limit as R11D end",
            "R12B end", "the bush band (screen rows 49-108) is 32 px left of native; native's H-scroll table at the "
                    + "shot (probe frame 3158) is plane B -258 on lines 0-44 and -388 from line 45 with VSRAM B 147, "
                    + "the engine's equal on the paired frame, so the offset is plane B content (the band's tiles), "
                    + "not a scroll input: a Plane B window cause, as R11D/R12D");

    /**
     * Stages in the order they are loaded into the one boot (a second headless GL boot in a JVM fails
     * to capture with GL 0x502, so every stage is loaded into the first boot). R11A comes first: it is
     * the calibration stage whose scroll was certified against an older reference.
     */
    private static final CdStagePayload[] STAGES = {
            CdStagePayload.R11A, CdStagePayload.R11B, CdStagePayload.R11C, CdStagePayload.R11D,
            CdStagePayload.R12A, CdStagePayload.R12B, CdStagePayload.R12C, CdStagePayload.R12D,
            CdStagePayload.R13C, CdStagePayload.R13D};

    /** A different stage whose native picture the engine must NOT match (negative control). */
    private static CdStagePayload control(int index) {
        return STAGES[(index + 4) % STAGES.length];
    }

    @Test
    void backgroundMatchesTheNativeScreenshotsOfEveryStage() throws Exception {
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        Path out = Path.of("target", "soniccd-ppz-fixtures");
        Files.createDirectories(out);
        StringBuilder failures = new StringBuilder();
        StringBuilder summary = new StringBuilder();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            GameLoop loop = null;
            for (int s = 0; s < STAGES.length; s++) {
                CdStagePayload payload = STAGES[s];
                List<Row> rows = rows(payload);
                List<Shot> shots = shots(payload);
                List<Shot> otherShots = shots(control(s));
                if (loop == null) {
                    loop = boot.boot(disc, new SonicCdGameModule(),
                            SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
                    assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
                } else {
                    GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload),
                            SonicCdZoneRegistry.actIndex(payload));
                }
                assertEquals(payload, ((CdLevel) GameServices.level().getCurrentLevel()).payload(), "loaded stage");
                List<Rendered> rendered = render(loop, payload, rows, shots, nativeObjects(payload));
                for (int i = 0; i < shots.size(); i++) {
                    Shot shot = shots.get(i);
                    Rendered engine = rendered.get(i);
                    boolean[][] masked = mask(engine);
                    Score score = compare(engine.image(), ScreenshotCapture.loadPNG(shot.file()), masked);
                    ScreenshotCapture.savePNG(engine.image(), out.resolve("engine-" + payload + "-" + shot.name() + ".png"));
                    String key = payload + " " + shot.name();
                    boolean uncertified = UNCERTIFIED.containsKey(key);
                    String line = String.format("%s: native level frame %d = engine level frame %d (camera %X,%X): %s%s",
                            key, shot.levelFrame(), shot.levelFrame() - 1, engine.cameraX(), engine.cameraY(), score,
                            uncertified ? " [UNCERTIFIED: " + UNCERTIFIED.get(key) + "]" : "");
                    System.out.println("PPZ fixture: " + line);
                    summary.append(line).append(System.lineSeparator());
                    if (uncertified) {
                        if (score.passes()) failures.append("now passes, move it out of UNCERTIFIED: ").append(line)
                                .append(System.lineSeparator());
                    } else if (!score.passes()) {
                        failures.append(line).append(System.lineSeparator());
                    }

                    // Negative control: the same engine picture against another stage's native picture
                    // must fail, or the comparison could not tell stages apart.
                    Score wrong = compare(engine.image(), ScreenshotCapture.loadPNG(otherShots.get(i).file()), masked);
                    String control = "control " + payload + " vs " + control(s) + " " + shot.name() + ": " + wrong;
                    System.out.println("PPZ " + control);
                    summary.append(control).append(System.lineSeparator());
                    if (wrong.passes()) failures.append("the comparison accepted another stage's picture: ")
                            .append(control).append(System.lineSeparator());
                }
            }
        } finally {
            Files.writeString(out.resolve("report.txt"), summary.toString());
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
        assertEquals("", failures.toString(), "native picture mismatches");
    }
}
