package com.openggf.tools;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.MenuPathBrowser;
import com.openggf.game.MenuRomLocator;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.TexturedQuadRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Purpose: capture populated, paged, empty and folder-only path browsers, the Settings
 * browser entry and a rejected ROM selection using production menu renderers and scripted
 * InputHandler events; never desktop input or audio. Inputs: output directory, optional
 * logical width (320), optional scale (3). Requires display-free surfaceless EGL.
 * Origin: 2026-10-27 menu design review, round 3 ROM/path browser polish.
 */
public final class MenuPathBrowserCaptureTool {
    private MenuPathBrowserCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("<output-dir> [width=320] [scale=3]");
        if (!SurfacelessEglContext.requested()) throw new IllegalArgumentException("Select surfaceless-egl; desktop capture is unsupported");
        Path out = Path.of(args[0]);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 320;
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        Files.createDirectories(out);
        Path temp = Files.createTempDirectory("path-browser-review");
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize null-platform GLFW");
        try (var egl = SurfacelessEglContext.create(width * scale, 224 * scale, true)) {
            GL.createCapabilities();
            var renderer = new TexturedQuadRenderer();
            var font = new MenuPixelFont();
            try {
                renderer.init();
                renderer.setProjectionMatrix(new Matrix4f().ortho2D(0, width, 0, 224).get(new float[16]));
                font.init("pixel-font.png", renderer);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                var config = SonicConfigurationService.createStandalone(temp.resolve("config"));
                var input = new InputHandler(InputBindingFactory.supplier(config), List::of);
                Path library = Files.createDirectories(temp.resolve("Library/Genesis/Original cartridges"));
                Files.createDirectory(library.resolve("Backups"));
                Files.createDirectory(library.resolve("Translations"));
                for (int i = 0; i < 13; i++) Files.writeString(library.resolve("Cartridge %02d.gen".formatted(i + 1)), "Presentation fixture, not a ROM");
                Files.writeString(library.resolve("Read me.txt"), "All filenames remain selectable");
                var browser = new MenuPathBrowser(library.toString(), true, "LOCATE SONIC 2", "Choose a ROM file (any filename)");
                try {
                    settle(browser, input);
                    save(out, "p01-populated", width, scale, () -> browser.render(font, width));
                    press(browser::update, input, GLFW_KEY_DOWN);
                    press(browser::update, input, GLFW_KEY_DOWN);
                    press(browser::update, input, GLFW_KEY_DOWN);
                    save(out, "p02-rom-candidate", width, scale, () -> browser.render(font, width));
                    browser.pointer(30, 40, false, false, false, -9);
                    tick(browser::update, input);
                    save(out, "p03-scrolled", width, scale, () -> browser.render(font, width));
                    press(browser::update, input, GLFW_KEY_RIGHT);
                    save(out, "p04-places-focus", width, scale, () -> browser.render(font, width));
                } finally { browser.cancel(); }
                Path empty = Files.createDirectory(library.resolve("Empty folder"));
                var emptyBrowser = new MenuPathBrowser(empty.toString(), true, "LOCATE SONIC 2", "Choose a ROM file (any filename)");
                try {
                    settle(emptyBrowser, input);
                    save(out, "p05-empty", width, scale, () -> emptyBrowser.render(font, width));
                } finally { emptyBrowser.cancel(); }
                var folderBrowser = MenuPathBrowser.folder(empty.toString(), "BROWSE PATH", "Choose a folder");
                try {
                    settle(folderBrowser, input);
                    save(out, "p06-folder-only", width, scale, () -> folderBrowser.render(font, width));
                } finally { folderBrowser.cancel(); }
                Path invalid = Files.createDirectory(temp.resolve("Unreadable ROM image"));
                Files.writeString(invalid.resolve("cartridge.backup"), "Not a ROM");
                config.setConfigValue(SonicConfiguration.SONIC_2_ROM, invalid.toString());
                var locator = new MenuRomLocator(MasterTitleScreen.GameEntry.SONIC_2, config);
                try {
                    for (int i = 0; i < 50; i++) { tick(locator::update, input); Thread.sleep(5); }
                    locator.pointer(30, 91, true, true, false, 0);
                    tick(locator::update, input);
                    for (int i = 0; i < 50; i++) { tick(locator::update, input); Thread.sleep(5); }
                    save(out, "p07-invalid-rom", width, scale, () -> locator.render(font, width));
                } finally { locator.cancel(); }
                var hub = new MasterTitleScreen(config);
                try {
                    hub.initialize();
                    for (int i = 0; i < 20; i++) tick(hub::update, input);
                    for (int key : List.of(GLFW_KEY_UP, GLFW_KEY_RIGHT, GLFW_KEY_ENTER, GLFW_KEY_UP,
                            GLFW_KEY_RIGHT, GLFW_KEY_RIGHT, GLFW_KEY_RIGHT, GLFW_KEY_RIGHT,
                            GLFW_KEY_DOWN, GLFW_KEY_ENTER, GLFW_KEY_UP)) press(hub::update, input, key);
                    for (int i = 0; i < 50; i++) { tick(hub::update, input); Thread.sleep(5); }
                    hub.setViewportWidth(width);
                    hub.setProjectionMatrix(new Matrix4f().ortho2D(0, width, 0, 224).get(new float[16]));
                    save(out, "p08-settings-browser", width, scale, hub::draw);
                } finally { hub.cleanup(); }
            } finally {
                font.cleanup();
                renderer.cleanup();
            }
        } finally {
            glfwTerminate();
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void settle(MenuPathBrowser browser, InputHandler input) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (browser.loading()) {
            tick(browser::update, input);
            if (System.nanoTime() > deadline) throw new IllegalStateException("Folder read did not finish");
            Thread.sleep(5);
        }
    }

    private static void tick(Consumer<InputHandler> update, InputHandler input) {
        input.refreshLogicalSnapshot();
        update.accept(input);
        input.update();
    }

    private static void press(Consumer<InputHandler> update, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        tick(update, input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        tick(update, input);
        for (int i = 0; i < 4; i++) tick(update, input);
    }

    private static void save(Path out, String name, int width, int scale, Runnable draw) throws Exception {
        glViewport(0, 0, width * scale, 224 * scale);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        draw.run();
        glFinish();
        ScreenshotCapture.captureAndSavePNG(width * scale, 224 * scale, out.resolve(name + ".png"));
        System.out.println("wrote " + name);
    }
}
