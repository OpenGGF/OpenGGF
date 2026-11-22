package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.LevelManager.LevelRenderOptions;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

@EnabledOnOs(OS.LINUX)
class TestWwzBossHoldPictures {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 224;

    private record Hold(int first, int last, String artCounters) {
        boolean includes(int row) { return first <= row && row <= last; }
    }

    @Test
    void goodFutureRetainsTheNativePictureDuringBothHolds() throws Exception {
        compare(CdStagePayload.R63C, new Hold(5475, 5495, "020000:010200"),
                new Hold(5555, 5575, "020000:030000"));
    }

    @Test
    void badFutureRetainsTheNativePictureDuringBothHolds() throws Exception {
        compare(CdStagePayload.R63D, new Hold(5726, 5746, "020103:04020d"),
                new Hold(5806, 5826, "010101:010006"));
    }

    private static void compare(CdStagePayload payload, Hold... holds) throws Exception {
        String base = "rehost-boss-wwz/" + payload;
        Path traceDir = CdTestMedia.nativeFile(base + "-defeat/trace/metadata.json").getParent();
        Path movie = CdTestMedia.nativeFile(base + "-defeat/movie.bk2");
        var pictures = new TreeMap<Integer, Path>();
        for (Hold hold : holds) {
            // Include the regular V-INT at entry and the first regular V-INT after the wait.
            for (int row = hold.first() - 2; row <= hold.last() + 1; row++) {
                pictures.put(row, CdTestMedia.nativeFile(base + "-fades/pictures/row-" + row + ".png"));
            }
        }
        TraceData trace = TraceData.load(traceDir);
        var config = GameServices.configuration();
        boolean oldAudio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        boolean oldSpriteLimit = config.getBoolean(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT);
        boolean oldHud = config.getBoolean(SonicConfiguration.TRACE_SHOW_GAME_HUD);
        String oldProfile = config.getString(SonicConfiguration.DISPLAY_COLOR_PROFILE);
        String oldExpansion = config.getString(SonicConfiguration.DISPLAY_COLOR_EXPANSION);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
        config.setSessionOverride(SonicConfiguration.TRACE_SHOW_GAME_HUD, true);
        config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
        config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, "FULL");
        var differences = new ArrayList<String>();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boots = new CdFreshStageBoots(WIDTH, HEIGHT)) {
            var module = new SonicCdGameModule();
            module.getGameService(CdLevelVIntCounter.class).seedNextStageForReplay(CdFixtureReplay.stageEntryBase(trace));
            boots.boot(disc, module, payload);
            int first = pictures.firstKey();
            int last = pictures.lastKey();
            CdFixtureReplay.playLoaded(new CdFixtureReplay.Fixture(payload, traceDir, movie), first - 1,
                    false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic)
                                throws Exception {
                            var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                            CompositeSnapshot snapshot = null;
                            for (int row = first; row <= last; row++) {
                                // Native row R shows the picture prepared through R-1. Held rows share
                                // a level counter, so count physical V-INTs in the palette wait.
                                assertEquals(trace.getFrame(row - 1).gameplayFrameCounter(),
                                        GameServices.level().getFrameCounter(), payload + " before row " + row);
                                if (pictures.containsKey(row)) {
                                    comparePicture(payload, row, pictures.get(row), differences);
                                }
                                for (Hold hold : holds) {
                                    if (hold.includes(row)) {
                                        assertEquals(hold.artCounters(), artCounters(), payload + " animated tiles at " + row);
                                    }
                                }
                                if (row == holds[0].first()) snapshot = rewind.capture();
                                runner.stepFrameFromRecording();
                            }
                            rewind.restore(snapshot);
                            comparePicture(payload, holds[0].first(), pictures.get(holds[0].first()), differences);
                        }
                    });
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            config.setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, oldSpriteLimit);
            config.setSessionOverride(SonicConfiguration.TRACE_SHOW_GAME_HUD, oldHud);
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, oldProfile);
            config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_EXPANSION, oldExpansion);
        }
        assertTrue(differences.isEmpty(), () -> String.join("\n", differences));
        System.out.printf("%s: %d unmasked pictures match (%d pixels)%n",
                payload, pictures.size(), pictures.size() * WIDTH * HEIGHT);
    }

    // The white palette can conceal a premature animation update. Compare the bytes from
    // aniArtFrames and aniArtTimers (Level/_Definitions.inc:222-223) as well as the pixels.
    private static String artCounters() throws ReflectiveOperationException {
        var animation = GameServices.level().getAnimatedPatternManager();
        var zoneTiles = animation.getClass().getDeclaredField("zoneTiles");
        zoneTiles.setAccessible(true);
        Object tiles = zoneTiles.get(animation);
        var frames = tiles.getClass().getDeclaredField("frames");
        var timers = tiles.getClass().getDeclaredField("timers");
        frames.setAccessible(true);
        timers.setAccessible(true);
        var hex = HexFormat.of();
        return hex.formatHex((byte[]) frames.get(tiles)) + ":" + hex.formatHex((byte[]) timers.get(tiles));
    }

    private static void comparePicture(CdStagePayload payload, int row, Path file,
                                       List<String> differences) throws Exception {
        RgbaImage nativeFrame = ScreenshotCapture.loadPNG(file);
        RgbaImage engine = draw();
        int mismatches = 0;
        int hudMismatches = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (TestWwzNativePictures.level(nativeFrame.argb(x, y))
                        == TestWwzNativePictures.level(engine.argb(x, y))) continue;
                mismatches++;
                if ((x < 128 && y < 56) || (x < 64 && y >= 200)) hudMismatches++;
            }
        }
        if (mismatches != 0) {
            Path output = Path.of("target", "wwz-hold-pictures", payload.name());
            Files.createDirectories(output);
            ScreenshotCapture.savePNG(engine, output.resolve("row-" + row + ".png"));
            differences.add("%s row %d: %d/%d mismatched pixels, %d in the HUD regions"
                    .formatted(payload, row, mismatches, WIDTH * HEIGHT, hudMismatches));
        }
    }

    private static RgbaImage draw() {
        var level = GameServices.level();
        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithRenderOptions(GameServices.sprites(), LevelRenderOptions.gameplay());
        GameServices.graphics().flush();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(WIDTH, HEIGHT);
    }
}
