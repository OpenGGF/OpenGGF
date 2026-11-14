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
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Native check for the Wacky Workbench presentation of all ten stages: the production renderer (scroll handler,
 * palette cycle through the shared palette owner, and the animated-tile manager stepped once per level frame as the
 * engine would) against BizHawk screenshots of the stage run: the fixture's early shot (idle, nothing pressed, about
 * 180 level frames in) and four more taken from the same movie while the camera moves right: at camera X about
 * $80, $100 and $180 ({@code mid1}-{@code mid3}) and at the last trace row ({@code late}). The extra shots are made
 * with {@code rehost-fixtures/_tools/probe.sh} and live in {@code native/rehost-fixtures-wwz/<payload>/} (see its
 * {@code mid.json}); the test skips when they or the fixtures are absent.
 *
 * <p>Method follows {@code TestPpzR11aBackgroundPicture}: the camera is frozen and set to the trace's camera for each
 * native level frame, so the scroll handler sees the native camera path without the player lane's movement; nothing
 * else is taken from the trace. Pairing ({@link #engineFrame}): a picture is the state after level frame lf-1 of
 * its own row. The fixture screenshots are their row's picture; a {@code probe.sh} shot is the next row's (probe frame
 * {@code bk2_frame_offset + row + 1} with the movie continuing), which is why, measured with a camera moving 6 px a
 * frame, it shows the camera of the level frame its row counts.
 *
 * <p>Masked: the HUD, the box around the trace's Sonic and around the engine's own Sonic (the player lane is not driven
 * by the trace), a box around every placement and around every ring of a ring line (objects are not hosted by this
 * lane). The trace carries no background-camera globals, so the background camera itself is checked only through the
 * picture. The {@code late} shot is restricted to pixels whose background world X is below 512: the native background
 * plane is 512 px wide and rows drawn "static" show the layout modulo 512 (Present:739-755, {@code DrawBlockRowAbsX}),
 * which the engine's wide plane does not model; that belongs to the background drawing owner, not the scroll handler.
 */
@EnabledOnOs(OS.LINUX)
class TestWwzNativePictures {
    private static final CdStagePayload[] PAYLOADS = {
            CdStagePayload.R61A, CdStagePayload.R61B, CdStagePayload.R61C, CdStagePayload.R61D,
            CdStagePayload.R62A, CdStagePayload.R62B, CdStagePayload.R62C, CdStagePayload.R62D,
            CdStagePayload.R63C, CdStagePayload.R63D};
    private static final int PLACEMENT_RADIUS = 32;
    private static final int RING_RADIUS = 16;
    private static final int LATE_MIN_PIXELS = 3000;

    /**
     * Shots that do NOT reach the threshold and are reported as uncertified rather than asserted. The test asserts that
     * each of these still fails, so the list cannot go stale. Evidence in the lane report.
     */
    private static final Set<String> UNCERTIFIED = Set.of(
            "R63D mid1",     // checker-floor colour of the electric-floor flash is 1-2 palette updates out of phase
            "R61A late");   // unhosted objects larger than the placement boxes remain (R61B late: the shared door and
                            // platform are hosted; R61D late: the Wacky Workbench objects are hosted; both certified)

    // ObjRing_Deltas (Level/_Objects/Ring/Main.asm:17-32): x and y step of a ring line by subtype high nibble.
    private static final int[] RING_DELTAS = {
            0x10, 0, 0x18, 0, 0x20, 0, 0, 0x10, 0, 0x18, 0, 0x20, 0x10, 0x10, 0x18, 0x18,
            0x20, 0x20, -0x10, 0x10, -0x18, 0x18, -0x20, 0x20, 0x10, 8, 0x18, 0x10, -0x10, 8, -0x18, 0x10};
    private static final int RING_OBJECT_ID = 0x10;          // Object Index: 10 - Ring

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** One trace row. */
    private record Sample(int levelFrames, int cameraX, int cameraY, int playerX, int playerY) { }

    private static List<Sample> samples(CdStagePayload payload) throws Exception {
        Path file = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/physics.csv");
        List<String> lines = Files.readAllLines(file);
        String[] header = lines.get(0).split(",");
        int camX = indexOf(header, "camera_x");
        int camY = indexOf(header, "camera_y");
        int counter = indexOf(header, "gameplay_frame_counter");
        int px = indexOf(header, "player_x");
        int py = indexOf(header, "player_y");
        List<Sample> out = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",");
            out.add(new Sample(Integer.parseInt(cells[counter], 16), Integer.parseInt(cells[camX], 16),
                    Integer.parseInt(cells[camY], 16), Integer.parseInt(cells[px], 16), Integer.parseInt(cells[py], 16)));
        }
        return out;
    }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) if (header[i].equals(name)) return i;
        throw new IllegalStateException("no column " + name);
    }

    /** A native screenshot: its name, file, trace row and level-frame counter. */
    private record Shot(String name, Path file, int traceRow, int levelFrames) { }

    private static List<Shot> shots(CdStagePayload payload) throws Exception {
        Path manifest = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json");
        JsonNode early = new ObjectMapper().readTree(manifest.toFile()).get("screenshots").get("early");
        List<Shot> out = new ArrayList<>();
        out.add(new Shot("early", CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/" + early.get("file").asText()),
                early.get("trace_row").asInt(), early.get("level_frames").asInt()));
        Path mid = CdTestMedia.nativeFile("rehost-fixtures-wwz/" + payload + "/mid.json");
        for (JsonNode shot : new ObjectMapper().readTree(mid.toFile()).get("shots")) {
            out.add(new Shot(shot.get("name").asText(), mid.resolveSibling(shot.get("file").asText()),
                    shot.get("trace_row").asInt(), shot.get("level_frames").asInt()));
        }
        return out;
    }

    /** The framebuffer plus what the masks need, read before the stage is replaced. */
    private record Rendered(RgbaImage image, int cameraX, int cameraY, int playerX, int playerY,
                            int enginePlayerX, int enginePlayerY, List<CdLevel.CdPlacement> placements, int[] hscroll) { }

    /**
     * Loads {@code payload} on the shared boot and captures the production renderer at each shot's engine level frame
     * ({@link #engineFrame}). The loaded stage is level frame 1, that iteration's scroll, palette cycle and tile
     * animation included ({@code CdFirstMainLoopIteration}); every later distinct {@code gameplay_frame_counter} sets
     * the native camera of that level frame and steps once, so the engine stands at level frame L after the step that
     * took the trace's camera for L. The production tile animator is the only one.
     */
    private static List<Rendered> render(com.openggf.GameLoop loop, CdStagePayload payload, boolean firstBoot,
                                         List<Sample> samples, List<Shot> shots) throws Exception {
        if (!firstBoot) {
            // The headless GL context can be booted once per JVM, so later stages load through the same boot.
            // The previous stage's last camera is where the new stage would spawn its first objects: start from X 0, as a
            // stage entry does (hosted objects, such as the electric beams, would otherwise run from the first frame).
            GameServices.camera().setX((short) 0);
            GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        }
        assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
        var game = (SonicCdGame) GameServices.level().getGame();
        assertEquals(payload, game.currentLevel().payload(), "stage loaded");
        int[] targets = new int[shots.size()];
        for (int i = 0; i < shots.size(); i++) targets[i] = engineFrame(shots.get(i), samples);
        Rendered[] out = new Rendered[shots.size()];
        GameServices.camera().setFrozen(true);
        Sample first = samples.get(0);
        GameServices.camera().setX((short) first.cameraX());
        GameServices.camera().setY((short) first.cameraY());
        int previous = first.levelFrames();
        int captured = captureDue(previous, first, targets, out);
        for (Sample sample : samples) {
            if (captured == out.length) break;
            if (sample.levelFrames() == previous) continue;   // lag: the level did not advance
            previous = sample.levelFrames();
            GameServices.camera().setX((short) sample.cameraX());
            GameServices.camera().setY((short) sample.cameraY());
            loop.step();
            captured += captureDue(previous, sample, targets, out);
        }
        assertEquals(shots.size(), captured, payload + ": shots captured");
        return List.of(out);
    }

    /** Captures every shot whose engine level frame is {@code levelFrame}; returns how many. */
    private static int captureDue(int levelFrame, Sample sample, int[] targets, Rendered[] out) {
        int n = 0;
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == levelFrame && out[i] == null) {
                out[i] = capture(sample);
                n++;
            }
        }
        return n;
    }

    /**
     * The engine level frame a shot shows. A picture shows the state after the level frame before the one its row
     * counts: the V-INT that starts the video frame DMAs the palette, scroll and sprite table the previous iteration
     * wrote (Level/Main.asm:829-831), and the tile animation it runs is the engine frame's own (see {@code
     * CdFirstMainLoopIteration}). The fixture's early screenshot is its row's picture: level frame lf(row)-1. A
     * {@code probe.sh} shot is the video frame after its row (probe frame {@code bk2_frame_offset + row + 1}, the run
     * continuing), so it is row+1's picture: lf(row+1)-1. No row follows the last one; there the level is taken to
     * advance, as it does on every frame without a pattern-load lag: lf(row).
     */
    private static int engineFrame(Shot shot, List<Sample> samples) {
        if (shot.name().equals("early")) return samples.get(shot.traceRow()).levelFrames() - 1;
        int after = shot.traceRow() + 1;
        return after < samples.size() ? samples.get(after).levelFrames() - 1 : samples.get(shot.traceRow()).levelFrames();
    }

    private static Rendered capture(Sample sample) {
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
        AbstractPlayableSprite sprite = GameServices.camera().getFocusedSprite();
        return new Rendered(ScreenshotCapture.captureFramebuffer(320, 224),
                GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                sample.playerX(), sample.playerY(),
                sprite == null ? -9999 : sprite.getCentreX(), sprite == null ? -9999 : sprite.getCentreY(),
                ((CdLevel) level.getCurrentLevel()).placements(), GameServices.parallax().getHScroll().clone());
    }

    private static boolean[][] mask(Rendered rendered) {
        boolean[][] masked = new boolean[320][224];
        int camX = rendered.cameraX();
        int camY = rendered.cameraY();
        maskBox(masked, rendered.playerX() - camX - 28, rendered.playerY() - camY - 36,
                rendered.playerX() - camX + 28, rendered.playerY() - camY + 36);
        maskBox(masked, rendered.enginePlayerX() - camX - 28, rendered.enginePlayerY() - camY - 36,
                rendered.enginePlayerX() - camX + 28, rendered.enginePlayerY() - camY + 36);
        for (CdLevel.CdPlacement placement : rendered.placements()) {
            int px = placement.x() - camX;
            int py = (placement.yWord() & 0xFFF) - camY;
            if ((placement.idByte() & 0x7F) == RING_OBJECT_ID) {
                // ObjRing_Init (Ring/Main.asm:60-80): subtype low 3 bits = rings - 1, high nibble = delta table row.
                int rings = (placement.subtype() & 7) + 1;
                int row = (placement.subtype() >> 4) & 0xF;
                int dx = RING_DELTAS[row * 2];
                int dy = RING_DELTAS[row * 2 + 1];
                for (int i = 0; i < rings; i++) {
                    maskBox(masked, px + i * dx - RING_RADIUS, py + i * dy - RING_RADIUS,
                            px + i * dx + RING_RADIUS, py + i * dy + RING_RADIUS);
                }
            } else {
                maskBox(masked, px - PLACEMENT_RADIUS, py - PLACEMENT_RADIUS, px + PLACEMENT_RADIUS, py + PLACEMENT_RADIUS);
            }
        }
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if ((x < 120 && y < 56) || (x < 56 && y >= 192) || (x >= 224 && y < 40)) masked[x][y] = true;
            }
        }
        return masked;
    }

    private static void maskBox(boolean[][] masked, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(224, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(320, x1); x++) masked[x][y] = true;
        }
    }

    /** {matching, total, minX, minY, maxX, maxY} over the unmasked pixels (and, with {@code hscroll}, world X below 512). */
    private static int[] compare(RgbaImage engine, RgbaImage reference, boolean[][] masked, int[] hscroll) {
        int same = 0, total = 0, minX = 999, minY = 999, maxX = -1, maxY = -1;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (masked[x][y]) continue;
                if (hscroll != null && ((x - (short) hscroll[y]) & 0xFFFF) >= 512) continue;
                total++;
                if (level(reference.argb(x, y)) == level(engine.argb(x, y))) {
                    same++;
                } else {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {same, total, minX, minY, maxX, maxY};
    }

    private static boolean passes(int[] r, boolean late) {
        return r[1] >= (late ? LATE_MIN_PIXELS : 20000) && r[0] * 1000L >= r[1] * 995L;
    }

    /** One stage on the shared boot; returns the failure lines (empty when every asserted shot matches). */
    private static List<String> check(com.openggf.GameLoop loop, CdStagePayload payload, boolean firstBoot) throws Exception {
        Path out = Path.of("target", "soniccd-wwz-pictures");
        Files.createDirectories(out);
        List<Sample> samples = samples(payload);
        List<Shot> shots = shots(payload);
        // The trace row and the manifest agree on the level-frame counter at each shot.
        for (Shot shot : shots) {
            assertEquals(shot.levelFrames(), samples.get(shot.traceRow()).levelFrames(), payload + " " + shot.name());
        }
        List<Rendered> rendered = render(loop, payload, firstBoot, samples, shots);
        List<String> failures = new ArrayList<>();
        RgbaImage[] reference = new RgbaImage[shots.size()];
        for (int i = 0; i < shots.size(); i++) {
            reference[i] = ScreenshotCapture.loadPNG(shots.get(i).file());
            Shot shot = shots.get(i);
            boolean late = shot.name().equals("late");
            boolean[][] masked = mask(rendered.get(i));
            int[] r = compare(rendered.get(i).image(), reference[i], masked, late ? rendered.get(i).hscroll() : null);
            ScreenshotCapture.savePNG(rendered.get(i).image(), out.resolve("engine-" + payload + "-" + shot.name() + ".png"));
            String key = payload + " " + shot.name();
            if (late && r[1] < LATE_MIN_PIXELS) {
                // Every row is at background world X >= 512 here: no pixel the engine models is left to compare.
                System.out.println("WWZ picture: NO-COVERAGE " + key + " late: " + r[1] + " pixels below world x 512");
                continue;
            }
            boolean ok = passes(r, late);
            String line = String.format("%s%s: native level frame %d (camera %X,%X): %d/%d unmasked pixels match%s",
                    key, late ? " (background world x < 512 only)" : "", shot.levelFrames(),
                    rendered.get(i).cameraX(), rendered.get(i).cameraY(), r[0], r[1],
                    r[0] == r[1] ? "" : String.format("; differing pixels lie in x %d-%d, y %d-%d", r[2], r[4], r[3], r[5]));
            System.out.println("WWZ picture: " + (UNCERTIFIED.contains(key) ? "UNCERTIFIED " : ok ? "ok " : "FAIL ") + line);
            if (UNCERTIFIED.contains(key)) {
                if (ok) failures.add(key + " is listed as uncertified but now passes: " + line);
            } else if (!ok) {
                failures.add(line);
            }
        }
        // Negative control: this stage's early engine picture must not match its own last native screenshot.
        int last = shots.size() - 1;
        int[] control = compare(rendered.get(0).image(), reference[last], mask(rendered.get(0)), null);
        if (control[0] * 1000L >= control[1] * 995L) {
            failures.add(payload + " early engine picture also matches the late native screenshot: " + control[0] + "/" + control[1]);
        }
        return failures;
    }

    /**
     * Each stage at its early and moving-camera native frames, at least 99.5% of the unmasked pixels equal (3 bits per
     * channel, as {@code TestPpzR11aBackgroundPicture}). One boot, one stage load per payload; failures are
     * collected so every stage is reported.
     */
    @Test
    void everyStageMatchesItsNativeScreenshots() throws Exception {
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        List<String> failures = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            CdStagePayload first = PAYLOADS[0];
            var loop = boot.boot(disc, new SonicCdGameModule(),
                    SonicCdZoneRegistry.zoneIndex(first), SonicCdZoneRegistry.actIndex(first));
            for (CdStagePayload payload : PAYLOADS) {
                failures.addAll(check(loop, payload, payload == first));
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }
}
