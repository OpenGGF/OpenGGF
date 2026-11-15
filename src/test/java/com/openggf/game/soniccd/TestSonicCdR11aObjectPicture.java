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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The common objects in the native Palmtree Panic Act 1 Present frame
 * {@code player-reference-r1/final/observed-1/postframe-02590.png} (levelFrames 188, camera
 * $0,$70A): the HUD's score, time label, rings row and lives, and the two rings at $F0,$720 and
 * $110,$720, compared region by region at 3 bits per channel. Two regions are reported, not
 * asserted, because both depend on the native lag frames (levelFrames 2 and 130-146, while
 * PLC 2 decodes) that the engine does not model: the time digits (the timer starts when the
 * title card's WaitPLC clears {@code ctrlLocked}, Title Card/Main.asm:181-186) and the RINGS
 * label's flash phase (an {@code eori} whenever levelVIntCounter's low nibble is 0 during
 * RunObjects, HUD and Points/Main.asm:126-131; native toggled 12 times by levelFrames 188).
 */
@EnabledOnOs(OS.LINUX)
class TestSonicCdR11aObjectPicture {
    /**
     * The native frame is levelFrames 188's picture, which shows the state after level frame 187 (the V-INT that starts
     * the video frame DMAs what the previous iteration wrote, Level/Main.asm:829-831). The loaded stage is level frame 1
     * ({@code CdFirstMainLoopIteration}), so the engine steps to 187.
     */
    private static final int ENGINE_LEVEL_FRAME = 188 - 1;

    /** {x0, y0, x1, y1} screen boxes. */
    private static final Map<String, int[]> ASSERTED = new LinkedHashMap<>();
    static {
        ASSERTED.put("SCORE label and digits", new int[] {16, 8, 120, 24});
        ASSERTED.put("TIME label and separators", new int[] {16, 24, 56, 40});
        ASSERTED.put("ring digits", new int[] {64, 40, 96, 56});
        ASSERTED.put("lives icon and count", new int[] {16, 200, 56, 224});
        ASSERTED.put("rings at $F0 and $110", new int[] {232, 14, 288, 30});
    }
    private static final int[] TIME_DIGITS = {56, 24, 104, 40};
    private static final int[] RINGS_LABEL = {16, 40, 64, 56};

    @Test
    void hudAndRingsMatchTheNativeFrame() throws Exception {
        Path cue = CdTestMedia.usaCue();
        Path nativeFrame = CdTestMedia.nativeFile("player-reference-r1/final/observed-1/postframe-02590.png");
        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(),
                    SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A), SonicCdZoneRegistry.actIndex(CdStagePayload.R11A));
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            for (int frame = 1; frame < ENGINE_LEVEL_FRAME; frame++) loop.step();

            RgbaImage engine = render();
            RgbaImage reference = ScreenshotCapture.loadPNG(nativeFrame);
            Path out = Path.of("target", "soniccd-obj0");
            Files.createDirectories(out);
            ScreenshotCapture.savePNG(engine, out.resolve("engine-r11a-levelframes-188.png"));
            ScreenshotCapture.savePNG(ScreenshotCapture.createDiffImage(reference, engine, 0), out.resolve("diff.png"));

            List<String> report = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (var region : ASSERTED.entrySet()) {
                int[] r = region.getValue();
                int[] result = compare(reference, engine, r);
                String line = String.format("%s %d/%d", region.getKey(), result[0], result[1]);
                report.add(line);
                if (result[0] * 100L < result[1] * 99L) failures.add(line);
            }
            int[] time = compare(reference, engine, TIME_DIGITS);
            report.add(String.format("time digits (reported) %d/%d", time[0], time[1]));
            int[] label = compare(reference, engine, RINGS_LABEL);
            report.add(String.format("RINGS label flash (reported) %d/%d", label[0], label[1]));
            String text = String.join("; ", report);
            System.out.println("OBJ0 picture: " + text);
            Files.writeString(out.resolve("report.txt"), text + System.lineSeparator());
            assertTrue(failures.isEmpty(), "regions below 99%: " + failures + " (all: " + text + ")");
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }

    private static RgbaImage render() {
        GraphicsManager graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        var level = GameServices.level();
        level.setClearColor();
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        level.drawWithSpritePriority(GameServices.sprites(), true);
        graphics.flush();
        if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
        org.lwjgl.opengl.GL11.glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static int[] compare(RgbaImage reference, RgbaImage engine, int[] box) {
        int same = 0, total = 0;
        for (int y = box[1]; y < box[3]; y++) {
            for (int x = box[0]; x < box[2]; x++) {
                total++;
                if (level(reference.argb(x, y)) == level(engine.argb(x, y))) same++;
            }
        }
        return new int[] {same, total};
    }

    private static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }
}
