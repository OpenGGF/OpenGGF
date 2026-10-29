package com.openggf.tools;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.graphics.shaderlib.DisplayShaderLibrary;
import com.openggf.graphics.shaderlib.DisplayShaderPickerController;
import com.openggf.DisplayShaderPickerRenderer;
import com.openggf.graphics.shaderlib.DisplayShaderPresetRef;
import com.openggf.graphics.shaderlib.DisplayShaderSelectionModel;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.scene.host.SceneSurface;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Purpose: capture shader-picker layout states through its production renderer over S2 EHZ1.
 * Inputs: {@code <S2-ROM> <new-output-dir> [width=320] [scale=3]}.
 * Origin: 2026-10-27 menu design review round 4.
 *
 * <p>Creates a disposable shader library under this worktree's target directory. The scanned
 * files are presentation samples; no shader is activated, downloaded or persisted. Captures
 * declare an applied reference to exercise the active marker. GameplayCaptureSession owns the
 * production world, EGL and NullAudioBackend; renderer resources close before its context.
 */
public final class DisplayShaderPickerDesignCaptureTool {
    private DisplayShaderPickerDesignCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 4)
            throw new IllegalArgumentException("usage: DisplayShaderPickerDesignCaptureTool <S2-ROM> <new-output-dir> [width] [scale]");
        if (!SurfacelessEglContext.requested() || System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null)
            throw new IllegalStateException("Requires display-free surfaceless EGL; unset DISPLAY and WAYLAND_DISPLAY");
        Path rom = Path.of(args[0]).toAbsolutePath(), out = Path.of(args[1]).toAbsolutePath();
        int width = args.length > 2 ? Integer.parseInt(args[2]) : 320;
        int scale = args.length > 3 ? Integer.parseInt(args[3]) : 3;
        if ((width != 320 && width != 400) || scale < 1 || scale > 8)
            throw new IllegalArgumentException("width must be 320 or 400; scale must be 1..8");
        if (!Files.isRegularFile(rom) || Files.exists(out))
            throw new IllegalArgumentException("Supply an existing ROM and a new output directory");
        Files.createDirectories(Path.of("target"));
        Path library = Files.createTempDirectory(Path.of("target"), "shader-picker-design-").toAbsolutePath();
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings);
             var renderer = new DisplayShaderPickerRenderer()) {
            session.boot(rom, 0, 0, settings);
            for (int i = 0; i < 90; i++) session.step(null);
            requireSilent(session);
            if (session.player().getDead()) throw new IllegalStateException("Capture player died during setup");
            Files.createDirectories(out);
            Files.writeString(out.resolve("state.csv"), GameplayCaptureSession.stateHeader() + "\n" + session.stateLine(89, null) + "\n");
            for (int i = 1; i <= 24; i++) sample(library, "crt/scanlines-%02d.glsl".formatted(i));
            sample(library, "lcd/handheld.glsl");
            String deep = "libretro-glsl/handheld/portable-displays/high-resolution/lcd-grid.glsl";
            sample(library, deep);
            var model = new DisplayShaderSelectionModel(DisplayShaderLibrary.scan(library));
            var picker = open(model, DisplayShaderPresetRef.OFF);
            var shots = new Shots(session, renderer, out, width, scale);
            shots.save("01-root-first-draw", picker, DisplayShaderPresetRef.OFF, null, false);
            shots.save("01b-root-settled", picker, DisplayShaderPresetRef.OFF, null, false);
            press(picker, GLFW_KEY_DOWN, DisplayShaderPresetRef.OFF);
            shots.save("02-folder-focus", picker, DisplayShaderPresetRef.OFF, null, false);
            press(picker, GLFW_KEY_ENTER, DisplayShaderPresetRef.OFF);
            shots.save("03-parent-focus", picker, DisplayShaderPresetRef.OFF, null, false);
            var active = ref(library, "crt/scanlines-18.glsl");
            picker = open(model, active);
            shots.save("04-long-list-active", picker, active, null, false);
            press(picker, GLFW_KEY_DOWN, active);
            shots.save("05-focus-beside-active", picker, active, null, false);
            var nested = ref(library, deep);
            picker = open(model, nested);
            shots.save("06-nested-breadcrumb", picker, nested, null, false);
            Path empty = Files.createDirectory(library.resolve("empty"));
            var emptyPicker = open(new DisplayShaderSelectionModel(DisplayShaderLibrary.scan(empty)), DisplayShaderPresetRef.OFF);
            shots.save("07-empty-library", emptyPicker, DisplayShaderPresetRef.OFF, null, false);
            picker = open(model, DisplayShaderPresetRef.OFF);
            for (int i = 0; i < 3; i++) press(picker, GLFW_KEY_Z, DisplayShaderPresetRef.OFF);
            shots.save("08-no-filter-matches", picker, DisplayShaderPresetRef.OFF, null, false);
            shots.save("09-downloading", picker, DisplayShaderPresetRef.OFF, "Downloading libretro GLSL pack...", true);
            requireSilent(session);
            System.out.println("Captured shader states " + width + "x224 at scale " + scale + "; audio=NullAudioBackend; out=" + out);
        } finally {
            // Only remove the disposable files this invocation created under target/.
            try (var paths = Files.walk(library)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void requireSilent(GameplayCaptureSession session) {
        if (!session.usesNullAudioBackend())
            throw new IllegalStateException("Capture must retain NullAudioBackend");
    }

    private static void sample(Path library, String path) throws Exception {
        Path file = library.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "// Draw-only menu design sample. Never activated.\n");
    }

    private static DisplayShaderPresetRef ref(Path library, String path) {
        return new DisplayShaderPresetRef(DisplayShaderPresetRef.Kind.GLSL, path, library.resolve(path));
    }

    private static DisplayShaderPickerController open(DisplayShaderSelectionModel model, DisplayShaderPresetRef active) {
        var picker = new DisplayShaderPickerController(model, GLFW_KEY_F8);
        press(picker, GLFW_KEY_F8, active);
        return picker;
    }

    private static void press(DisplayShaderPickerController picker, int key, DisplayShaderPresetRef active) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        picker.update(input, active);
    }

    private record Shots(GameplayCaptureSession session, DisplayShaderPickerRenderer renderer,
                         Path output, int width, int scale) {
        void save(String name, DisplayShaderPickerController picker, DisplayShaderPresetRef active,
                  String status, boolean downloading) throws Exception {
            session.renderFrame();
            GameServices.graphics().resetForFixedFunction();
            renderer.render(picker, active, "shaders", status, downloading, SceneSurface.projection(width, 224), width);
            GameServices.graphics().flushScreenSpace();
            glFinish();
            RgbaImage source = ScreenshotCapture.captureFramebuffer(width, 224);
            RgbaImage scaled = new RgbaImage(width * scale, 224 * scale, new int[width * 224 * scale * scale]);
            for (int y = 0; y < scaled.height(); y++) for (int x = 0; x < scaled.width(); x++)
                scaled.setArgb(x, y, source.argb(x / scale, y / scale));
            ScreenshotCapture.savePNG(scaled, output.resolve(name + ".png"));
            System.out.println("wrote " + name);
        }
    }
}
