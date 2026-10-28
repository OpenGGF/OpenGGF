package com.openggf.tools;

import com.openggf.ModManagerScreenHost;
import com.openggf.game.MenuChrome;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.mods.*;
import com.openggf.mods.ui.ModManagerScreen;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.lwjgl.opengl.GL11.*;

/**
 * Purpose: deterministic Mod manager reading-page design captures, including dependency
 * confirmations and long diagnostic pages absent from the installed-mod capture set.
 * Inputs: {@code <output-dir> [width=320] [scale=3]}. Synthetic descriptors never load
 * code or user jars. All editable state stays in a temporary directory; no audio or
 * desktop connection is opened. Requires the surfaceless EGL property.
 * Origin: 2026-10-27 menu-design review, round 3 Mod manager (base 191701bfeb).
 */
public final class ModManagerDesignCaptureTool {
    private ModManagerDesignCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("output-dir [width=320] [scale=3]");
        if (!SurfacelessEglContext.requested()) throw new IllegalStateException("Use surfaceless-egl");
        Path out = Path.of(args[0]);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 320;
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        Files.createDirectories(out);
        Path temp = Files.createTempDirectory("mod-manager-design-");
        try (var egl = SurfacelessEglContext.create(width * scale, 224 * scale, true)) {
            GL.createCapabilities();
            TexturedQuadRenderer renderer = new TexturedQuadRenderer();
            MenuPixelFont font = new MenuPixelFont();
            try {
                renderer.init();
                font.init("pixel-font.png", renderer);
                ModDescriptor core = descriptor("art-library", "Shared art library", false, List.of(), List.of());
                ModDescriptor code = descriptor("moonlight", "Moonlight adventures", true,
                        List.of(new ModDependency("art-library", VersionRange.parse(">=1.0.0 <2.0.0"))),
                        List.of(new ModFinding(ModFindingSeverity.WARNING, "ART_OVERLAP",
                                "Another mod replaces the same art. Review the load order before playing.", null)));
                ModDescriptor extra = descriptor("night-music", "Night music", false, List.of(), List.of());
                List<ModDescriptor> mods = List.of(core, code, extra);
                var state = new ModState(1, List.of(new ModState.Entry("art-library", false, 0),
                        new ModState.Entry("moonlight", false, 1), new ModState.Entry("night-music", false, 2)));
                var catalog = new EffectiveCatalogBuilder().build(mods, state);
                var findings = new ModRuntimeFindingStore();
                findings.replaceOwner("retired-mod", List.of(new ModFinding(ModFindingSeverity.ERROR,
                        "AUDIO_LOAD_FAILED", "Music could not be loaded. The original music remains available.", null)));
                var editor = new PendingModStateEditor(state, catalog.scanned(), new ModStateStore(temp));
                var screen = new ModManagerScreen(catalog, editor, findings, ModManagerScreenHost.textSink(font));
                screen.select(1);
                save(screen, renderer, font, width, scale, out, "d01-row");
                press(screen, 'r'); press(screen, 'a');
                save(screen, renderer, font, width, scale, out, "d02-details");
                for (int i = 0; i < 6; i++) press(screen, 'd');
                save(screen, renderer, font, width, scale, out, "d03-details-findings");
                press(screen, 'b'); press(screen, 'r'); press(screen, 'r'); press(screen, 'a');
                save(screen, renderer, font, width, scale, out, "d04-notices");
                press(screen, 'b'); press(screen, 'l'); press(screen, 'a');
                save(screen, renderer, font, width, scale, out, "d05-order");
                press(screen, 'b'); screen.select(1);
                // The trust flow as a sequence: the NEEDS TRUST row, its TRUST AND ENABLE page, the row after confirming.
                save(screen, renderer, font, width, scale, out, "seq-trust-k01-row");
                press(screen, 'a');
                save(screen, renderer, font, width, scale, out, "d06-trust");
                press(screen, 'a');
                save(screen, renderer, font, width, scale, out, "seq-trust-k03-confirmed");
                screen.select(0); press(screen, 'a');
                save(screen, renderer, font, width, scale, out, "d07-disable-cascade");
                press(screen, 'b');
                // A data-only dependency cascade exercises enable without any trust prompt.
                var dependent = descriptor("night-music", "Night music", false,
                        List.of(new ModDependency("art-library", VersionRange.parse(">=1.0.0 <2.0.0"))), List.of());
                var dataCatalog = new EffectiveCatalogBuilder().build(List.of(core, dependent), state);
                var dataEditor = new PendingModStateEditor(state, dataCatalog.scanned(), new ModStateStore(temp));
                screen = new ModManagerScreen(dataCatalog, dataEditor, new ModRuntimeFindingStore(),
                        ModManagerScreenHost.textSink(font));
                screen.select(1); press(screen, 'a');
                save(screen, renderer, font, width, scale, out, "d08-enable-cascade");
            } finally {
                font.cleanup();
                renderer.cleanup();
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static ModDescriptor descriptor(String id, String name, boolean code,
                                             List<ModDependency> dependencies, List<ModFinding> findings) {
        var manifest = new ModManifest(1, id, name, SemanticVersion.parse("1.2.0"), List.of("Example creator"),
                "A new adventure with custom art and music.", VersionRange.parse(">=0.7.0 <0.8.0"),
                ModType.PATCH, "s2", code ? "example.Moonlight" : null, dependencies, Map.of(), Map.of(),
                null, OptionalInt.empty());
        return new ModDescriptor(Path.of(id + ".jar"), manifest, "a".repeat(64), code, findings);
    }

    private static void press(ModManagerScreen screen, char key) {
        screen.update(new ModManagerScreen.MenuInput() {
            @Override public boolean menuAccept() { return key == 'a'; }
            @Override public boolean menuBack() { return key == 'b'; }
            @Override public boolean menuRight() { return key == 'r'; }
            @Override public boolean menuLeft() { return key == 'l'; }
            @Override public boolean menuDown() { return key == 'd'; }
        });
        screen.update(ModManagerScreen.MenuInput.NEUTRAL);
    }

    private static void save(ModManagerScreen screen, TexturedQuadRenderer renderer, MenuPixelFont font,
                             int width, int scale, Path out, String name) throws Exception {
        glViewport(0, 0, width * scale, 224 * scale);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        renderer.setProjectionMatrix(new Matrix4f().ortho2D(0, width, 0, 224).get(new float[16]));
        int margin = Math.max(0, (width - 320) / 2);
        if (margin > 0) MenuChrome.widePageFrame(font, width, margin);
        renderer.setProjectionMatrix(new Matrix4f().ortho2D(-margin, width - margin, 0, 224).get(new float[16]));
        screen.render();
        glFinish();
        ScreenshotCapture.captureAndSavePNG(width * scale, 224 * scale, out.resolve(name + ".png"));
        System.out.println("wrote " + name);
    }
}
