package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.soniccd.level.CdLevel;

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
 * Native check for the Palmtree Panic Act 1 Present background: the production renderer at level
 * frame 188 against the native BizHawk frame {@code postframe-02590.png} (the same frame as
 * {@code TestSonicCdR11aFirstFramePicture}). The compared area is every pixel above the ground
 * band that is not under the HUD or an unhosted object, which is where the cloud, mountain, bush
 * and water deformation lives.
 */
@EnabledOnOs(OS.LINUX)
class TestPpzR11aBackgroundPicture {
    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** One native level frame's camera, from the BizHawk globals in player.jsonl. */
    private record NativeCamera(int levelFrames, int cameraX, int cameraY) { }

    private static List<NativeCamera> nativeCameras() throws Exception {
        Path file = CdTestMedia.nativeFile("player-reference-r1/final/observed-1/player.jsonl");
        ObjectMapper mapper = new ObjectMapper();
        List<NativeCamera> out = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            for (String line; (line = reader.readLine()) != null; ) {
                JsonNode globals = mapper.readTree(line).get("globals");
                if (globals == null || globals.get("levelFrames").asInt() < 1) continue;
                out.add(new NativeCamera(globals.get("levelFrames").asInt(), globals.get("cameraX").asInt() >> 16,
                        globals.get("cameraY").asInt() >> 16));
            }
        }
        return out;
    }

    /** The framebuffer plus the camera and placement table read before the boot closes. */
    private record Rendered(RgbaImage image, int cameraX, int cameraY, List<CdLevel.CdPlacement> placements) { }

    /**
     * Boots R11A once and captures the production renderer after each of {@code targets} level frames
     * (ascending). The camera is frozen and set to the native camera before every step, so the scroll
     * handler sees the native camera path without the player lane's movement; nothing else is taken
     * from the native run. (A headless GL boot cannot be repeated inside one JVM, hence one boot.)
     */
    private static List<Rendered> renderEngineFrames(int... targets) throws Exception {
        Path cue = CdTestMedia.usaCue();
        List<Rendered> out = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(),
                    SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A), SonicCdZoneRegistry.actIndex(CdStagePayload.R11A));
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            GameServices.camera().setFrozen(true);
            int previous = 1;
            int next = 0;
            for (NativeCamera camera : nativeCameras()) {
                if (camera.levelFrames() == previous) continue;
                if (camera.levelFrames() > targets[targets.length - 1]) break;
                previous = camera.levelFrames();
                GameServices.camera().setX((short) camera.cameraX());
                GameServices.camera().setY((short) camera.cameraY());
                loop.step();
                if (previous == targets[next]) {
                    out.add(capture());
                    next++;
                }
            }
            assertEquals(targets.length, out.size(), "native level frames reached");
        }
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

    /**
     * Native level frames 188, 408 and 778 (camera X 0, $8A and $2D8) against the production renderer.
     * Frame 188 is the earliest native picture but its camera has not moved; 408 and 778 deform the
     * clouds, mountains, bushes and water against a moving camera. HUD, Sonic, and every other unhosted
     * object are masked (Sonic sits at screen (160,96) while the camera follows him; other objects by
     * the level's placement table), leaving background and terrain.
     *
     * <p>The native video frame shows the scroll and palette buffers the previous level frame wrote: the
     * VInt that starts the frame uploads them before this frame's logic runs, while the globals in
     * player.jsonl are read after that logic. So native level frame N is compared with the engine after
     * level frame N-1.
     */
    @Test
    void backgroundMatchesTheNativeFramesWhileTheCameraMoves() throws Exception {
        int[][] frames = {{188, 2590}, {408, 2810}, {778, 3180}};
        Path[] natives = new Path[frames.length];
        for (int i = 0; i < frames.length; i++) {
            natives[i] = CdTestMedia.nativeFile(
                    String.format("player-reference-r1/final/observed-1/postframe-%05d.png", frames[i][1]));
        }
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try {
            List<Rendered> rendered = renderEngineFrames(frames[0][0] - 1, frames[1][0] - 1, frames[2][0] - 1);
            StringBuilder summary = new StringBuilder();
            Path out = Path.of("target", "soniccd-ppz-background");
            Files.createDirectories(out);
            for (int i = 0; i < frames.length; i++) {
                RgbaImage engine = rendered.get(i).image();
                RgbaImage reference = ScreenshotCapture.loadPNG(natives[i]);
                int camX = rendered.get(i).cameraX();
                int camY = rendered.get(i).cameraY();
                boolean[][] masked = new boolean[320][224];
                maskBox(masked, 160 - 28, 96 - 36, 160 + 28, 96 + 36);
                for (CdLevel.CdPlacement placement : rendered.get(i).placements()) {
                    int sx = placement.x() - camX;
                    int sy = (placement.yWord() & 0xFFF) - camY;
                    maskBox(masked, sx - 32, sy - 32, sx + 32, sy + 32);
                }
                int same = 0, total = 0;
                int minX = 999, minY = 999, maxX = -1, maxY = -1;
                for (int y = 0; y < 224; y++) {
                    for (int x = 0; x < 320; x++) {
                        if (masked[x][y] || (x < 120 && y < 56) || (x < 56 && y >= 192) || (x >= 224 && y < 40)) continue;
                        total++;
                        if (level(reference.argb(x, y)) == level(engine.argb(x, y))) same++;
                        else {
                            minX = Math.min(minX, x); minY = Math.min(minY, y);
                            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                        }
                    }
                }
                ScreenshotCapture.savePNG(engine, out.resolve("engine-r11a-levelframes-" + (frames[i][0] - 1) + ".png"));
                String line = String.format("native level frame %d = engine level frame %d (camera %X,%X): %d/%d unmasked pixels match%s",
                        frames[i][0], frames[i][0] - 1, camX, camY, same, total,
                        same == total ? "" : String.format("; differing pixels lie in x %d-%d, y %d-%d", minX, maxX, minY, maxY));
                summary.append(line).append(System.lineSeparator());
                System.out.println("PPZ background: " + line);
                assertTrue(total > 20000, "too little left after masking: " + line);
                assertTrue(same * 1000L >= total * 995L, line);
            }
            Files.writeString(out.resolve("report.txt"), summary.toString());
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }

    private static void maskBox(boolean[][] masked, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y < Math.min(224, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(320, x1); x++) masked[x][y] = true;
        }
    }
}
