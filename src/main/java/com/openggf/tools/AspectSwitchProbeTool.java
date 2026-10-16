package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.graphics.ScreenshotCapture;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders a level after its aspect changes mid-level, for comparison with the same level started
 * at that aspect.
 *
 * <p>Purpose: a game suspended to the master title can resume at a new aspect (see
 * {@code LiveSettings}). The level was loaded with the old width cached in the camera, parallax,
 * background and tilemap renderers; this checks the resumed picture against a native start.
 * Inputs: {@code <game> <zone> <act> <out-dir> <native|switch|stale> [frames-before=60] [frames-after=60]}.
 * {@code switch} loads the level at 4:3, runs, then applies 16:9 through the same refresh hooks
 * {@code Engine.applyResolvedDisplayDimensions} uses; {@code native} starts at 16:9; {@code stale}
 * refreshes only the camera, as before the level/background hooks existed (a negative control).
 * All hold right for the same frames and write {@code <mode>.png}; compare a mode with native. Run each mode in its own JVM, with
 * {@code -XstartOnFirstThread} on macOS. Originating task: in-flight settings for suspended
 * games, 2026-10-15.
 */
public final class AspectSwitchProbeTool {
    private static final int WIDE = 400;

    private AspectSwitchProbeTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: AspectSwitchProbeTool <game> <zone> <act> <out-dir> <native|switch> [before] [after]");
            System.exit(2);
        }
        String game = args[0];
        int act = Integer.parseInt(args[2]) - 1; // one-based on the command line, as GameplayCaptureTool
        Path out = Path.of(args[3]);
        boolean switching = !"native".equals(args[4]);
        boolean stale = "stale".equals(args[4]);
        int before = args.length > 5 ? Integer.parseInt(args[5]) : 60;
        int after = args.length > 6 ? Integer.parseInt(args[6]) : 60;
        Files.createDirectories(out);

        var settings = new GameplayCaptureSession.Settings(WIDE, "sonic", "", "off", null, null, null);
        try (GameplayCaptureSession session = new GameplayCaptureSession(settings)) {
            var config = GameServices.configuration();
            if (switching) {
                config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
                config.resolveDisplayAspect();
            }
            Path rom = TraceToolRomLocations.resolve(game, config, Path.of(""));
            session.boot(rom, GameplayCaptureTool.ZoneIds.resolve(game, args[1]), act, settings);
            for (int frame = 0; frame < before; frame++) session.step(right(frame));
            if (switching) {
                config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
                config.resolveDisplayAspect();
                GameServices.camera().refreshViewportDimensions(config);
                if (!stale) {
                    GameServices.graphics().applyResolvedDisplayWidth(WIDE); // the tilemap renderer
                    com.openggf.level.LevelDisplayWidth.refresh(GameServices.level()); // level, parallax, background
                }
            }
            for (int frame = before; frame < before + after; frame++) session.step(right(frame));
            ScreenshotCapture.savePNG(session.render(), out.resolve(args[4] + ".png"));
            System.out.println("wrote " + out.resolve(args[4] + ".png") + " camera x="
                    + GameServices.camera().getX() + " width=" + GameServices.camera().getWidth());
        }
    }

    private static Bk2FrameInput right(int frame) {
        return new Bk2FrameInput(frame, com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT, 0, false, 0, 0, false,
                "|..|...R....|........|");
    }
}
