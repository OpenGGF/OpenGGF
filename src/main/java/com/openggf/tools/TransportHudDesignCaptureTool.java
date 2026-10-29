package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.game.recording.UserRecordingHud;
import com.openggf.game.recording.UserRecordingHudState;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.graphics.PixelFontVariant;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.scene.host.SceneSurface;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Purpose: verify transport HUD clearance and presentation edge cases over any of the three games.
 * Inputs: {@code <ROM> <new-output-dir> [width=320|400]}; writes 3x PNGs and the gameplay state CSV.
 * Origin: 2026-10-27 menu design review round 4.
 * Declared display snapshots are comparison pictures, not recording/rewind/parity evidence.
 * Requires display-free EGL and checks the boot-owned no-device audio backend.
 */
public final class TransportHudDesignCaptureTool {
    private TransportHudDesignCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("usage: TransportHudDesignCaptureTool <ROM> <new-output-dir> [320|400]");
        }
        if (!SurfacelessEglContext.requested() || System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null) {
            throw new IllegalStateException("Requires surfaceless EGL with DISPLAY and WAYLAND_DISPLAY unset");
        }
        Path rom = Path.of(args[0]).toAbsolutePath(), out = Path.of(args[1]).toAbsolutePath();
        int width = args.length == 3 ? Integer.parseInt(args[2]) : 320;
        if (!Files.isRegularFile(rom) || Files.exists(out) || (width != 320 && width != 400)) {
            throw new IllegalArgumentException("Supply an existing ROM, new output directory and width 320 or 400");
        }
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 0, 0, settings);
            requireNoDeviceAudio(session);
            for (int frame = 0; frame < 90; frame++) session.step(null);
            if (session.player().getDead()) throw new IllegalStateException("Player died during capture setup");
            Files.createDirectories(out);
            Files.writeString(out.resolve("state.csv"), GameplayCaptureSession.stateHeader() + "\n"
                    + session.stateLine(89, null) + "\n");
            var text = PixelFontTextRenderer.compactMenu();
            text.setProjectionMatrix(SceneSurface.projection(width, 224));
            // Reach the existing package-private drawing owner without adding a runtime API.
            var type = Class.forName("com.openggf.game.rewind.LiveRewindHudOverlay");
            var constructor = type.getDeclaredConstructor(Supplier.class);
            constructor.setAccessible(true);
            var render = type.getDeclaredMethod("render", PixelFontTextRenderer.class, GraphicsManager.class, int.class);
            render.setAccessible(true);
            Object rewind = constructor.newInstance((Supplier<String>) () -> "REWIND 1248");
            try {
                save(session, out, width, "00-gameplay", () -> { });
                save(session, out, width, "04-live-rewind", () -> render.invoke(rewind, text, GameServices.graphics(), width));
                save(session, out, width, "13-recording-hold", () -> UserRecordingHud.render(text, GameServices.graphics(),
                        UserRecordingHud.holdPromptState(30, 60)));
                var active = new UserRecordingHudState(true, "REC frame 1248", "ehz1-review.bk2", 1248, false, false);
                save(session, out, width, "14-recording-active", () -> UserRecordingHud.render(text, GameServices.graphics(), active));
                save(session, out, width, "15-recording-playback", () -> UserRecordingHud.render(text, GameServices.graphics(),
                        new UserRecordingHudState(true, "PLAYBACK 1248/3600", "VERIFY clean", 1248, false, false)));
                save(session, out, width, "22-simultaneous-transports", () -> {
                    render.invoke(rewind, text, GameServices.graphics(), width);
                    UserRecordingHud.render(text, GameServices.graphics(), active);
                });
                save(session, out, width, "23-playback-warning", () -> UserRecordingHud.render(text, GameServices.graphics(),
                        new UserRecordingHudState(true, "PLAYBACK FF 1248/3600", "VERIFY missing-sidecar", 1248, true, false)));
                save(session, out, width, "24-recording-error", () -> UserRecordingHud.render(text, GameServices.graphics(),
                        new UserRecordingHudState(true, "REC ERROR", "IO_ERROR: disk full", 1248, false, true)));
                save(session, out, width, "25-long-playback-counters", () -> UserRecordingHud.render(text, GameServices.graphics(),
                        new UserRecordingHudState(true, "PLAYBACK FF 2147483646/2147483647", "VERIFY truncated-sidecar",
                                Integer.MAX_VALUE - 1, true, false)));
                requireNoDeviceAudio(session);
                System.out.println("Captured " + width + "x224 at scale 3; audio=NullAudioBackend; out=" + out);
            } finally {
                text.cleanup();
            }
        }
    }

    private static void requireNoDeviceAudio(GameplayCaptureSession session) {
        if (!session.usesNullAudioBackend()) {
            throw new IllegalStateException("Capture requires the boot-owned NullAudioBackend");
        }
    }

    @FunctionalInterface
    private interface Draw { void run() throws Exception; }

    private static void save(GameplayCaptureSession session, Path out, int width, String name, Draw draw) throws Exception {
        session.renderFrame();
        GameServices.graphics().resetForFixedFunction();
        draw.run();
        GameServices.graphics().flushScreenSpace();
        glFinish();
        var source = ScreenshotCapture.captureFramebuffer(width, 224);
        var scaled = new RgbaImage(width * 3, 224 * 3, new int[width * 224 * 9]);
        for (int y = 0; y < scaled.height(); y++) {
            for (int x = 0; x < scaled.width(); x++) scaled.setArgb(x, y, source.argb(x / 3, y / 3));
        }
        ScreenshotCapture.savePNG(scaled, out.resolve(name + ".png"));
    }
}
