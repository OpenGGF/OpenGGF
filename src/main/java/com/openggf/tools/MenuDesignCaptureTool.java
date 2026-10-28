package com.openggf.tools;

import com.openggf.ContentImportScreen;
import com.openggf.ContentImportScreenRenderer;
import com.openggf.ContentImportService;
import com.openggf.ModManagerScreenHost;
import com.openggf.ModSubsystem;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.LegalDisclaimerScreen;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.MenuTextEditor;
import com.openggf.game.NativeModNoticeScreen;
import com.openggf.game.recording.UserRecordingEntry;
import com.openggf.game.playback.PlaybackEntry;
import com.openggf.game.playback.PlaybackLibrary;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.graphics.ScreenshotCapture;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Offscreen captures of every engine-owned menu family outside the hub pages that
 * {@link MasterTitleCaptureTool} already covers, for design review.
 *
 * <p>Purpose: one PNG per scene for the Mod manager, recordings, trace picker, details,
 * text/path editors, advanced settings, startup disclaimer, native-mod notice and content
 * import, plus a text field mixing narrow and wide letters with its caret and keypad, and the
 * integer and decimal number keypads, driven by
 * scripted {@link InputHandler} key events (never desktop input).
 * Inputs: {@code <output-dir> [viewport-width=320] [scale=3] [mods-dir]}. Run from a directory
 * whose ROM images supply the hub logos and whose trace catalog fills the picker. The optional
 * mods directory is copied to a temporary root first, so the manager scans copies and never writes
 * the caller's mod state. Linux runs display-free with {@code -Dopenggf.headless.gl=surfaceless-egl}.
 * Audio is never opened; every temporary configuration, import and mod root is deleted on exit.
 * Originating task: 2026-10-27 menu design review
 * ({@code feature/ai-menu-design-review}).
 */
public final class MenuDesignCaptureTool {

    private record Scene(String name, Consumer<Context> setup, List<Integer> keys, int settleFrames) { }

    /** Per-scene configuration hooks applied before the hub initializes. */
    private static final class Context {
        final SonicConfigurationService config;
        final Path temp;
        MasterTitleScreen hub;
        Context(SonicConfigurationService config, Path temp) { this.config = config; this.temp = temp; }
    }

    private MenuDesignCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: MenuDesignCaptureTool <output-dir> [viewport-width] [scale] [mods-dir]");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 320;
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        Path modsSource = args.length > 3 ? Path.of(args[3]) : null;
        Files.createDirectories(out);
        Path modsCopy = modsSource == null ? null : copyMods(modsSource);

        int up = GLFW_KEY_UP, down = GLFW_KEY_DOWN, right = GLFW_KEY_RIGHT, enter = GLFW_KEY_ENTER;
        int f1 = GLFW_KEY_F1;
        Consumer<Context> none = c -> { };
        Consumer<Context> recordings = c -> c.hub.setPlaybackLibrarySourceForTest(() -> new PlaybackLibrary(
                sampleRecordings("s2").stream().<PlaybackEntry>map(entry -> new PlaybackEntry.Recording("s2", entry)).toList(),
                List.of()));
        Consumer<Context> traces = c -> c.config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        Consumer<Context> mods = c -> {
            if (modsCopy == null) return;
            installMods(modsCopy);
            c.hub.setModManagerScreenFactory(font -> ModSubsystem.current().createManager(font));
        };
        List<Integer> settings = List.of(up, right, enter);
        List<Integer> advanced = List.of(up, right, right, enter, down, enter);
        List<Scene> hubScenes = new ArrayList<>(List.of(
                new Scene("m01-settings-details", none, concat(settings, f1), 20),
                new Scene("m02-settings-files", none, concat(settings, up, right, right, right, right, down), 20),
                new Scene("m03-settings-files-edit", none,
                        concat(settings, up, right, right, right, right, down, enter), 30),
                new Scene("m04-settings-capture", none, concat(settings, up, right, right, right, right, right, down), 20),
                new Scene("m05-advanced-settings", none, advanced, 20),
                new Scene("m06-advanced-settings-scrolled", none, concat(advanced, repeat(down, 14)), 20),
                // Captioned groups: the tape coast's settings indented under it (off, so dimmed),
                // the middle of the list and its last row.
                new Scene("m06a-advanced-tape-coast", none, concat(advanced, repeat(down, 10)), 20),
                new Scene("m06b-advanced-settings-middle", none, concat(advanced, repeat(down, 40)), 20),
                new Scene("m06c-advanced-settings-end", none, concat(advanced, repeat(down, 68)), 20),
                new Scene("m06d-advanced-details", none, concat(advanced, repeat(down, 10), f1), 20),
                new Scene("m07-advanced-text-edit", none, concat(advanced, repeat(down, 7), enter), 20),
                new Scene("m08-recordings", recordings, List.of(down, down, right, right, enter), 20),
                new Scene("m09-recordings-row", recordings, List.of(down, down, right, right, enter, down), 20),
                new Scene("m10-trace-picker", traces, List.of(), 120),
                new Scene("m11-trace-picker-row", traces, List.of(), 120)));
        if (modsCopy != null) {
            hubScenes.add(new Scene("m12-mods", mods, List.of(up, enter), 30));
            hubScenes.add(new Scene("m13-mods-row", mods, List.of(up, enter, down, down), 30));
            hubScenes.add(new Scene("m14-mods-detail", mods, List.of(up, enter, down, down, enter), 30));
        }

        GLFWErrorCallback.createPrint(System.err).set();
        boolean surfaceless = SurfacelessEglContext.requested();
        if (surfaceless) glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
        try {
            if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        } finally {
            if (surfaceless) glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
        }
        long window = NULL;
        SurfacelessEglContext egl = null;
        int fbw = width * scale;
        int fbh = 224 * scale;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            if (surfaceless) {
                egl = SurfacelessEglContext.create(fbw, fbh, true);
            } else {
                window = glfwCreateWindow(fbw, fbh, "Menu design capture", NULL, NULL);
                if (window == NULL) throw new IllegalStateException("Failed to create hidden window");
                glfwMakeContextCurrent(window);
            }
            GL.createCapabilities();
            float[] projection = new org.joml.Matrix4f().ortho2D(0, width, 0, 224).get(new float[16]);

            for (Scene scene : hubScenes) {
                Path temp = owned(Files.createTempDirectory("menu-design-capture"));
                SonicConfigurationService config = SonicConfigurationService.createStandalone(temp);
                config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
                com.openggf.configuration.PendingRestart.captureAtStart(config);
                Context context = new Context(config, temp);
                MasterTitleScreen screen = new MasterTitleScreen(config);
                context.hub = screen;
                scene.setup().accept(context);
                screen.initialize();
                screen.setEnabledModsByGame(Map.of(MasterTitleScreen.GameEntry.SONIC_2,
                        List.of("Widescreen+", "HUD Tweaks")));
                InputHandler input = new InputHandler(com.openggf.InputBindingFactory.supplier(config), List::of);
                for (int i = 0; i < 20; i++) screen.update(input);
                for (int key : scene.keys()) press(screen::update, input, key);
                for (int i = 0; i < scene.settleFrames(); i++) {
                    // Catalog loads and mod scans run off-thread; allow real time for them.
                    Thread.sleep(10);
                    screen.update(input);
                }
                if (scene.name().equals("m11-trace-picker-row")) {
                    for (int i = 0; i < 4; i++) press(screen::update, input, down);
                }
                frame(fbw, fbh, window, surfaceless);
                screen.setViewportWidth(width);
                screen.setProjectionMatrix(projection);
                screen.draw();
                save(fbw, fbh, out.resolve(scene.name() + ".png"), scene.name());
                screen.cleanup();
            }

            // Startup notices, drawn without their fade overlay.
            FadeManager fades = new FadeManager();
            LegalDisclaimerScreen legal = new LegalDisclaimerScreen(fades);
            legal.initialize();
            legal.setViewportWidth(width);
            legal.setProjectionMatrix(projection);
            InputHandler input = new InputHandler();
            // Run the fade-in and the prompt's own fade so the dismiss prompt is fully shown.
            for (int i = 0; i < 420; i++) { fades.update(); legal.update(input); input.update(); }
            frame(fbw, fbh, window, surfaceless);
            legal.draw();
            save(fbw, fbh, out.resolve("m20-legal-disclaimer.png"), "m20-legal-disclaimer");

            NativeModNoticeScreen notice = new NativeModNoticeScreen(fades, com.openggf.mods.NativeUnsupportedMods.noticeLines(
                    List.of("Sonic Echoes", "Starfall Frontier", "Infinite Sonic"), Integer.MAX_VALUE));
            notice.initialize();
            notice.setViewportWidth(width);
            notice.setProjectionMatrix(projection);
            for (int i = 0; i < 4; i++) { notice.update(input); input.update(); }
            frame(fbw, fbh, window, surfaceless);
            notice.draw();
            save(fbw, fbh, out.resolve("m21-native-mod-notice.png"), "m21-native-mod-notice");

            // Content import: the empty drop page and an inspected candidate.
            Path installRoot = owned(Files.createTempDirectory("menu-design-import"));
            ContentImportScreen importScreen = new ContentImportScreen(new ContentImportService(installRoot),
                    inertImportHost());
            ContentImportScreenRenderer importRenderer = new ContentImportScreenRenderer();
            importScreen.open();
            for (int i = 0; i < 4; i++) { importScreen.update(input); input.update(); }
            frame(fbw, fbh, window, surfaceless);
            importRenderer.render(importScreen, projection, width);
            save(fbw, fbh, out.resolve("m22-import-drop.png"), "m22-import-drop");
            if (modsCopy != null) {
                try (var jars = Files.list(modsCopy)) {
                    Path jar = jars.filter(p -> p.toString().endsWith(".jar")).sorted().findFirst().orElse(null);
                    if (jar != null) {
                        importScreen.drop(List.of(jar));
                        for (int i = 0; i < 200 && importScreen.busy(); i++) {
                            Thread.sleep(10);
                            importScreen.update(input);
                        }
                        for (int i = 0; i < 4; i++) { importScreen.update(input); input.update(); }
                        frame(fbw, fbh, window, surfaceless);
                        importRenderer.render(importScreen, projection, width);
                        save(fbw, fbh, out.resolve("m23-import-candidate.png"), "m23-import-candidate");
                    }
                }
            }
            importScreen.close();

            // Proportional text field: narrow and wide runs with the caret inside the W run
            // (13 presses keep the blink phase visible), then the keypad below the same value.
            PixelFontTextRenderer editorText = PixelFontTextRenderer.menu();
            editorText.setProjectionMatrix(projection);
            PixelFont editorFont = editorText.font();
            MenuTextEditor editor = new MenuTextEditor("Profile name", "iiiiiiiiiiii WWWWWWWWWWWW iiii", 64);
            InputHandler editorInput = new InputHandler();
            for (int i = 0; i < 2; i++) { editor.update(editorInput); editorInput.update(); }
            press(editor::update, editorInput, GLFW_KEY_HOME);
            for (int i = 0; i < 15; i++) press(editor::update, editorInput, GLFW_KEY_RIGHT);
            for (String scene : List.of("m24-text-editor-caret", "m25-text-editor-keypad")) {
                if (scene.equals("m25-text-editor-keypad")) press(editor::update, editorInput, GLFW_KEY_DOWN);
                frame(fbw, fbh, window, surfaceless);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                editorFont.beginMegaBatch();
                editor.render(editorFont, width);
                editorFont.endMegaBatch();
                save(fbw, fbh, out.resolve(scene + ".png"), scene);
            }
            // The numeric keypad: an integer setting and a decimal one, each with the keypad focused,
            // and the integer pad's cursor keys (every cell is a key; cell 12 is cursor to start).
            for (var numeric : List.of(
                    new Object[] {"m26-numeric-keypad", new MenuTextEditor("Frame rate", "60", 3, "60", MenuTextEditor.Mode.INTEGER), 1},
                    new Object[] {"m26b-numeric-keypad-cursor-keys", new MenuTextEditor("Frame rate", "60", 3, "60", MenuTextEditor.Mode.INTEGER), 12},
                    new Object[] {"m26c-numeric-keypad-cursor-right", new MenuTextEditor("Frame rate", "60", 3, "60", MenuTextEditor.Mode.INTEGER), 16},
                    new Object[] {"m27-decimal-keypad", new MenuTextEditor("Music volume", "0.8", 4, "1.0", MenuTextEditor.Mode.DECIMAL), 1},
                    new Object[] {"m27b-decimal-keypad-exponent", new MenuTextEditor("Music volume", "0.8", 4, "1.0", MenuTextEditor.Mode.DECIMAL), 13})) {
                MenuTextEditor keypad = (MenuTextEditor) numeric[1];
                InputHandler keypadInput = new InputHandler();
                for (int i = 0; i < 2; i++) { keypad.update(keypadInput); keypadInput.update(); }
                press(keypad::update, keypadInput, GLFW_KEY_DOWN);
                for (int i = 0; i < (int) numeric[2]; i++) press(keypad::update, keypadInput, GLFW_KEY_RIGHT);
                frame(fbw, fbh, window, surfaceless);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                editorFont.beginMegaBatch();
                keypad.render(editorFont, width);
                editorFont.endMegaBatch();
                save(fbw, fbh, out.resolve(numeric[0] + ".png"), (String) numeric[0]);
            }
            editorText.cleanup();
        } finally {
            if (egl != null) egl.close();
            if (window != NULL) glfwDestroyWindow(window);
            glfwTerminate();
            deleteOwned();
        }
        System.exit(0);
    }

    private static final List<Path> OWNED = new ArrayList<>();

    /** Temporary roots this run created; all are removed when the capture finishes. */
    private static Path owned(Path dir) {
        OWNED.add(dir);
        return dir;
    }

    private static void deleteOwned() {
        for (Path root : OWNED) {
            try (var walk = Files.walk(root)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            } catch (java.io.IOException ignored) {
                // Best effort: a capture result never depends on removing its scratch roots.
            }
        }
    }

    /**
     * The import scenes never confirm anything, so the host's callbacks are inert. A proxy keeps
     * this production tool free of the editor's project type (TestArchUnitRules core-runtime ratchet).
     */
    private static ContentImportScreen.Host inertImportHost() {
        return (ContentImportScreen.Host) java.lang.reflect.Proxy.newProxyInstance(
                ContentImportScreen.Host.class.getClassLoader(), new Class<?>[] {ContentImportScreen.Host.class},
                (proxy, method, args) -> {
                    if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "inert import host";
                        default -> null;
                    };
                });
    }

    private static void frame(int fbw, int fbh, long window, boolean surfaceless) {
        int[] w = {fbw};
        int[] h = {fbh};
        if (!surfaceless) glfwGetFramebufferSize(window, w, h);
        glViewport(0, 0, w[0], h[0]);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private static void save(int fbw, int fbh, Path file, String name) throws Exception {
        glFinish();
        ScreenshotCapture.captureAndSavePNG(fbw, fbh, file);
        System.out.println("wrote " + name);
    }

    private static Path copyMods(Path source) throws Exception {
        Path copy = owned(Files.createTempDirectory("menu-design-mods"));
        try (var files = Files.list(source)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".jar")).toList()) {
                Files.copy(p, copy.resolve(p.getFileName()));
            }
        }
        return copy;
    }

    private static void installMods(Path root) {
        var policy = new com.openggf.ExternalContentPolicy(com.openggf.ExternalContentMode.NORMAL);
        ModSubsystem.SessionAudioBoundary silent = new ModSubsystem.SessionAudioBoundary() {
            @Override public void install(com.openggf.audio.StreamedMusicPort port) { }
            @Override public void clear() { }
        };
        ModSubsystem.installAtBoot(policy, ModSubsystem.normalBootLoader(() -> root,
                com.openggf.io.ModInputLimits.production(), com.openggf.StockMusicDomains::containsSupported,
                silent));
    }

    private static List<UserRecordingEntry> sampleRecordings(String gameId) {
        List<UserRecordingEntry> entries = new ArrayList<>();
        String[][] rows = {{"Emerald Hill speedrun", "5400"}, {"Chemical Plant zip", "2310"},
                {"Aquatic Ruin no rings", "11820"}};
        for (int i = 0; i < rows.length; i++) {
            int frames = Integer.parseInt(rows[i][1]);
            java.time.Instant at = java.time.Instant.parse("2026-10-2" + (i + 2) + "T14:30:22Z");
            entries.add(new UserRecordingEntry(Path.of(gameId + "-" + i + ".bk2"), rows[i][0],
                    new com.openggf.game.recording.UserRecordingManifest(
                            com.openggf.game.recording.UserRecordingManifest.CURRENT_SCHEMA_VERSION, rows[i][0],
                            new com.openggf.version.BuildIdentity("0.7.prerelease", "abcdef123", false),
                            new com.openggf.game.recording.RecordingLaunchContext(gameId, i, 0, "sonic",
                                    List.of("tails"), false, "current-act-fresh-start"),
                            com.openggf.game.recording.UserRecordingSidecarMetadata.everyFrame(),
                            new com.openggf.game.recording.RecordingDeterminismMetadata(0, null), "A", frames,
                            com.openggf.game.recording.UserRecordingStopReason.USER_STOPPED, at),
                    frames, at, com.openggf.game.recording.RecordingVersionWarning.NONE, null));
        }
        return entries;
    }

    private static List<Integer> concat(List<Integer> head, Object... tail) {
        List<Integer> keys = new ArrayList<>(head);
        for (Object t : tail) {
            if (t instanceof Integer k) keys.add(k);
            else for (Object k : (List<?>) t) keys.add((Integer) k);
        }
        return keys;
    }

    private static List<Integer> repeat(int key, int count) {
        return java.util.Collections.nCopies(count, key);
    }

    private static void press(Consumer<InputHandler> update, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        update.accept(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        update.accept(input);
        for (int i = 0; i < 4; i++) {
            input.update();
            update.accept(input);
        }
    }
}
