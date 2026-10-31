package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.MasterTitleEntry;
import com.openggf.game.MasterTitleScreen;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Offscreen captures of the master title hub, Game Setup and Settings for visual review.
 *
 * <p>Purpose: render the pre-game menus in a hidden GLFW window with scripted
 * {@link InputHandler} key events (never desktop input) and write one PNG per scene.
 * Inputs: {@code <output-dir> [viewport-width=320] [scale=3] [paused-frame.png]}; the working
 * directory's ROM images supply the title logos exactly as at startup. A paused-frame PNG (any
 * gameplay capture) adds the suspended-game scenes: its card first on the shelf, the End game
 * prompt, Quit with a paused game, and the freeze-to-card zoom. Restart scenes show an unchanged restart-only setting and a
 * saved frame-rate change waiting for a restart (hub notice and Settings summary). Add your games scenes
 * run the real picker and discovery over the working directory read-only (it must hold the canonical S2
 * REV01 file) and write only to each scene's scratch configuration; the scan-limit scene scans a scratch tree
 * nine folders deep with one unreadable folder, so the real walker reports both and finds no game (no ROM is
 * copied or linked into it). On macOS run with {@code -XstartOnFirstThread}.
 * Originating task: the 2026-10 master title redesign (branch
 * {@code feature/ai-master-title-redesign}); suspended scenes from the hold-Escape suspend task.
 */
public final class MasterTitleCaptureTool {

    private record Scene(String name, List<Integer> keys, int settleFrames) { }

    private MasterTitleCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: MasterTitleCaptureTool <output-dir> [viewport-width] [scale] [paused-frame.png]");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 320;
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        RgbaImage paused = args.length > 3 ? ScreenshotCapture.loadPNG(Path.of(args[3])) : null;
        Files.createDirectories(out);

        int up = GLFW_KEY_UP, down = GLFW_KEY_DOWN, left = GLFW_KEY_LEFT, right = GLFW_KEY_RIGHT;
        int enter = GLFW_KEY_ENTER, esc = GLFW_KEY_ESCAPE;
        List<Scene> scenes = List.of(
                new Scene("01-hub", List.of(), 40),
                new Scene("02-hub-sliding", List.of(right), 2),
                new Scene("03-hub-next-game", List.of(right), 40),
                new Scene("04-hub-chip", List.of(down, right), 40),
                new Scene("05-hub-setup-button", List.of(down, down, right), 40),
                new Scene("06-hub-tabs", List.of(up, right), 40),
                new Scene("07-setup", List.of(down, down, right, enter), 20),
                new Scene("08-setup-edited", List.of(down, down, right, enter, down, down, down, down, right), 20),
                new Scene("09-setup-prompt", List.of(down, down, right, enter, down, down, down, down, right, esc), 20),
                new Scene("10-hub-overridden", List.of(down, down, right, enter, down, down, down, down, right, esc, enter), 30),
                new Scene("11-settings", List.of(up, right, enter), 20),
                new Scene("12-settings-tabs", List.of(up, right, enter, up), 20),
                new Scene("13-settings-gameplay", List.of(up, right, enter, up, right, right, right, down), 20),
                new Scene("14-settings-controls", List.of(up, right, enter, up, right, right, down, down, down, down,
                        down, down, down, down, down, down), 20),
                new Scene("15-advanced", List.of(up, right, right, enter), 20),
                new Scene("16-help", List.of(GLFW_KEY_F1), 20),
                new Scene("17-quit", List.of(esc), 20),
                new Scene("18-confirm", List.of(enter), 3),
                new Scene("38-hub-crowded", List.of(), 40),
                new Scene("39-hub-missing-rom", List.of(), 40),
                new Scene("40-locate-rom", List.of(enter), 40),
                new Scene("41-settings-save", List.of(up, right, enter, right, up, up), 20),
                new Scene("42-setup-save", List.of(down, right, enter, down, right, up), 20),
                new Scene("43-hub-idle", List.of(), 560),
                new Scene("44-settings-sound", List.of(up, right, enter, up, right, down), 20),
                new Scene("45-controls-group", List.of(up, right, enter, up, right, right, down), 20),
                new Scene("46-controller-help", List.of(GLFW_KEY_F1), 20),
                new Scene("47-controller-settings", List.of(up, right, enter), 20),
                new Scene("49-add-games", List.of(), 20),
                new Scene("50-hub-music-volume", List.of(up, right, enter, up, right, down, down, down), 20),
                // Gameplay tab: last row, then Reset tab, then its confirmation.
                new Scene("51-settings-reset-prompt", List.of(up, right, enter, up, right, right, right,
                        down, down, down, down, down, down, down, down, enter), 20),
                // Display tab: change the window mode and save it; the preview asks to keep it.
                new Scene("52-window-mode-confirm", List.of(up, right, enter, down, right, esc, enter), 20),
                // Add your games with real, read-only discovery of the working directory's ROM files:
                // the pending frame is the update that starts the task, so it never races the reader.
                new Scene("53-add-games-checking", List.of(enter), 0),
                new Scene("54-add-games-found-one", List.of(enter), 20),
                new Scene("55-add-games-scanning", List.of(down, enter), 0),
                new Scene("56-add-games-found-scan", List.of(down, enter), 20),
                // The scan's longest outcome: stopped at the depth limit, unreadable files skipped, nothing found.
                new Scene("68-add-games-scan-limit", List.of(down, enter), 20),
                // Advanced with every host tool bound (editor projects and content import).
                new Scene("57-advanced-all-tools", List.of(up, right, right, enter), 20),
                // The 3&K donor's save-screen team on Start, and a setup too wide for the chip row
                // (named in full on the card and on Start).
                new Scene("58-hub-crowded-start", List.of(down), 40),
                new Scene("59-hub-overflow", List.of(), 40),
                new Scene("60-hub-overflow-start", List.of(down), 40),
                new Scene("61-setup-borrow-s3k", List.of(down, down, right, enter, right, right), 20),
                // A missing ROM found by Game setup or Playback opens the same locator Start opens;
                // Back returns to the card with the same button focused.
                new Scene("62-missing-rom-locate-rom-from-setup", List.of(down, right, enter), 40),
                new Scene("63-missing-rom-locate-rom-from-playback", List.of(down, right, right, enter), 40),
                new Scene("64-missing-rom-locate-rom-back-to-setup", List.of(down, right, enter, esc), 40),
                // A failed ROM load (the engine's showRomLoadError): ROM NOT FOUND offers Locate ROM first.
                new Scene("65-missing-rom-not-found-page", List.of(), 20),
                new Scene("66-missing-rom-not-found-locate-rom", List.of(enter), 40),
                // A standalone card has no Game setup: the honest ACTION UNAVAILABLE page, no locator.
                new Scene("67-standalone-action-unavailable", List.of(GLFW_KEY_TAB), 20),
                // Frame sequences (0 starts recording): the hub slide, Settings > Display moving
                // down nine rows past the list edge, and a Controls rebind (listen, key, updated row).
                new Scene("seq-hub-slide", List.of(0, right), 14),
                new Scene("seq-display-down", List.of(up, right, enter, 0, down, down, down, down, down, down, down, down, down), 4),
                new Scene("seq-controls-rebind", List.of(up, right, enter, up, right, right, down, 0, enter, enter, GLFW_KEY_K), 4));
        List<Scene> pausedScenes = List.of(
                new Scene("19-paused-card", List.of(), 40),
                new Scene("20-paused-end-button", List.of(down, right), 40),
                new Scene("20b-paused-resume-button", List.of(down), 40),
                new Scene("20c-paused-settings-tab", List.of(up, right), 40),
                new Scene("21-paused-start-other", List.of(right, enter), 20),
                new Scene("22-paused-quit", List.of(esc), 20),
                new Scene("23-paused-resume-flash", List.of(enter), 3),
                new Scene("24-paused-setup-button", List.of(down, right), 40),
                new Scene("25-paused-setup-rewind", List.of(down, right, enter, down, down, down, down), 20),
                new Scene("26-paused-setup-character", List.of(down, right, enter, down), 20),
                new Scene("27-paused-settings", List.of(up, right, enter), 20),
                new Scene("28-paused-settings-row", List.of(up, right, enter, down), 20),
                new Scene("29-settings-restart-row", advancedSettingKeys(SonicConfiguration.DISPLAY_PACING), 20),
                new Scene("30-hub-restart-pending", List.of(), 40),
                new Scene("31-settings-restart-pending", List.of(up, right, enter, up), 20),
                new Scene("32-paused-zoom-start", List.of(), 0),
                new Scene("33-paused-zoom-middle", List.of(), 16),
                new Scene("34-paused-zoom-end", List.of(), 32),
                new Scene("35-paused-resume-zoom-start", List.of(), 0),
                new Scene("36-paused-resume-zoom-middle", List.of(), 16),
                new Scene("37-paused-resume-zoom-end", List.of(), 32));

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
            int fbw = width * scale;
            int fbh = 224 * scale;
            if (surfaceless) {
                egl = SurfacelessEglContext.create(fbw, fbh, true);
            } else {
                window = glfwCreateWindow(fbw, fbh, "Master title capture", NULL, NULL);
                if (window == NULL) throw new IllegalStateException("Failed to create hidden window");
                glfwMakeContextCurrent(window);
            }
            GL.createCapabilities();
            float[] projection = new org.joml.Matrix4f().ortho2D(0, width, 0, 224).get(new float[16]);
            List<Scene> all = new java.util.ArrayList<>(scenes);
            if (paused != null) all.addAll(pausedScenes);
            else all.addAll(pausedScenes.subList(10, 13));
            for (Scene scene : all) {
                Path configDir = Files.createTempDirectory("master-title-capture");
                SonicConfigurationService config = SonicConfigurationService.createStandalone(configDir);
                config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
                if (scene.name().contains("restart-pending")) {
                    // A frame-rate change saved in Settings since start-up, which waits for a restart.
                    com.openggf.configuration.PendingRestart.captureAtStart(config);
                    var draft = new com.openggf.configuration.EngineSettingsDraft(config);
                    draft.set(SonicConfiguration.FPS, "75");
                    draft.apply();
                } else {
                    com.openggf.configuration.PendingRestart.captureAtStart(config);
                }
                if (scene.name().contains("missing-rom") || scene.name().contains("locate-rom"))
                    config.setConfigValue(SonicConfiguration.SONIC_2_ROM, configDir.resolve("not-present.gen").toString());
                if (scene.name().contains("add-games")) {
                    for (var game : MasterTitleScreen.GameEntry.values())
                        config.setConfigValue(game.romConfigKey, configDir.resolve(game.gameId + "-missing.gen").toString());
                    // The pickers open in the working directory; discovery only reads it, and the
                    // found paths are saved to this scene's scratch configuration.
                    config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, ROM_FOLDER.toString());
                    if (scene.name().contains("scan-limit"))
                        config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, scanLimitTree(configDir).toString());
                }
                if (scene.name().contains("overflow")) {
                    var store = new com.openggf.game.launch.LaunchProfileStore(config);
                    store.save(MasterTitleScreen.GameEntry.SONIC_2, new com.openggf.game.launch.LaunchProfile(
                            com.openggf.game.launch.LaunchToggle.OFF, "s1",
                            com.openggf.game.launch.LaunchToggle.ON, "WIDE_16_10", "sonic", "tails"));
                }
                if (scene.name().contains("crowded")) {
                    var store = new com.openggf.game.launch.LaunchProfileStore(config);
                    store.save(MasterTitleScreen.GameEntry.SONIC_2, new com.openggf.game.launch.LaunchProfile(
                            com.openggf.game.launch.LaunchToggle.ON, "s3k",
                            com.openggf.game.launch.LaunchToggle.ON, "NATIVE_4_3", "sonic", "tails"));
                }
                MasterTitleScreen screen;
                if (scene.name().contains("standalone")) {
                    var entries = new java.util.ArrayList<MasterTitleEntry>();
                    for (var game : MasterTitleScreen.GameEntry.values()) entries.add(new MasterTitleEntry.Stock(game));
                    entries.add(new MasterTitleEntry.Standalone("sample", "Sample game", false));
                    screen = new MasterTitleScreen(config, new com.openggf.game.launch.LaunchProfileStore(config), entries);
                } else screen = new MasterTitleScreen(config);
                screen.initialize();
                if (scene.name().contains("crowded")) screen.setTitleEntries(List.of("Extra mode"), (games, index) -> { });
                if (scene.name().contains("all-tools")) {
                    // Inert handlers: the rows exist as in the engine, and nothing is opened.
                    screen.setEditorProjectsOpenHandler(() -> { });
                    com.openggf.game.TitleContentImportAccess.bind(screen, () -> { });
                }
                screen.setEnabledModsByGame(Map.of(MasterTitleScreen.GameEntry.SONIC_2, scene.name().contains("overflow")
                        ? java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(i -> "Mod " + i).toList()
                        : List.of("Widescreen+", "HUD Tweaks")));
                if (paused != null && scene.name().contains("paused")) {
                    screen.setSuspendedGame(new MasterTitleEntry.Suspended(
                                    new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2), "Emerald Hill, Act 1", true),
                            paused.width(), paused.height(), rgba(paused));
                }
                var pad = new java.util.concurrent.atomic.AtomicReference<List<com.openggf.control.GamepadStateSource.DeviceState>>(List.of());
                InputHandler input = new InputHandler(com.openggf.InputBindingFactory.supplier(config), pad::get);
                for (int i = 0; i < 20; i++) screen.update(input);
                if (scene.name().contains("standalone")) screen.setSelectedIndexForTest(3);
                if (scene.name().contains("not-found")) screen.showRomLoadError("s2");
                final long seqWindow = window;
                Sequence shoot = name -> {
                    int[] sfb = {fbw};
                    int[] sfbH = {fbh};
                    if (!surfaceless) glfwGetFramebufferSize(seqWindow, sfb, sfbH);
                    glViewport(0, 0, sfb[0], sfbH[0]);
                    glClearColor(0, 0, 0, 1);
                    glClear(GL_COLOR_BUFFER_BIT);
                    screen.setViewportWidth(width);
                    screen.setProjectionMatrix(projection);
                    screen.draw();
                    glFinish();
                    ScreenshotCapture.captureAndSavePNG(sfb[0], sfbH[0], out.resolve(name + ".png"));
                };
                if (scene.name().contains("add-games-")) addGames(screen, input, scene.name());
                else if (scene.name().startsWith("seq-")) {
                    // A real frame sequence: after each scripted press, every presentation frame of
                    // the settle window is drawn and saved (focus movement, scrolling, slides).
                    sequence(screen, input, scene, shoot);
                    screen.cleanup();
                    deleteTree(configDir);
                    System.out.println("wrote " + scene.name());
                    continue;
                }
                else for (int key : scene.keys()) press(screen, input, key);
                if (scene.name().contains("controller")) {
                    config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
                    boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
                    pad.set(List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox capture pad", buttons, 0, 0)));
                    input.refreshLogicalSnapshot();
                    input.update();
                    buttons[GLFW_GAMEPAD_BUTTON_DPAD_RIGHT] = true;
                    pad.set(List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox capture pad", buttons, 0, 0)));
                    input.refreshLogicalSnapshot();
                    screen.update(input);
                    input.update();
                    pad.set(List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox capture pad", new boolean[buttons.length], 0, 0)));
                    input.refreshLogicalSnapshot();
                    input.update();
                }
                if (scene.name().contains("paused-zoom")) screen.animateSuspendedGame();
                if (scene.name().contains("paused-resume-zoom")) {
                    input.handleKeyEvent(enter, GLFW_PRESS);
                    screen.update(input);
                    input.handleKeyEvent(enter, GLFW_RELEASE);
                    input.update();
                }
                for (int i = 0; i < scene.settleFrames(); i++) {
                    // The file picker reads asynchronously; allow real time for its folder listing.
                    if (scene.name().contains("locate-rom") || scene.name().contains("hub-idle")) Thread.sleep(10);
                    screen.update(input);
                }
                int[] fb = {fbw};
                int[] fbH = {fbh};
                if (!surfaceless) glfwGetFramebufferSize(window, fb, fbH);
                glViewport(0, 0, fb[0], fbH[0]);
                glClearColor(0, 0, 0, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                screen.setViewportWidth(width);
                screen.setProjectionMatrix(projection);
                screen.draw();
                glFinish();
                ScreenshotCapture.captureAndSavePNG(fb[0], fbH[0], out.resolve(scene.name() + ".png"));
                screen.cleanup();
                deleteTree(configDir);
                System.out.println("wrote " + scene.name());
            }
        } finally {
            if (egl != null) egl.close();
            if (window != NULL) glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private static final Path ROM_FOLDER = Path.of("").toAbsolutePath().normalize();
    private static final String S2_ROM = "Sonic The Hedgehog 2 (W) (REV01) [!].gen";

    /**
     * Locate (one canonical S2 file) or scan (the working directory) through the real picker and
     * discovery task, then checks the page state each picture claims to show.
     */
    private static void addGames(MasterTitleScreen screen, InputHandler input, String scene) throws Exception {
        boolean scan = scene.contains("scan");
        if (scan) press(screen, input, GLFW_KEY_DOWN);
        press(screen, input, GLFW_KEY_ENTER);
        settle(screen, input, () -> onboardingField(screen, "browser") instanceof com.openggf.game.MenuPathBrowser browser
                && !browser.loading());
        if (!scan) {
            // Rows: the parent folder, then folders, likely ROMs and other files by name, as the picker sorts them.
            List<Path> entries;
            try (var list = Files.list(ROM_FOLDER)) {
                entries = list.sorted(java.util.Comparator.comparing((Path p) -> Files.isDirectory(p)).reversed()
                        .thenComparing(p -> likelyRom(p) ? 0 : 1)
                        .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).toList();
            }
            int row = 1 + entries.indexOf(ROM_FOLDER.resolve(S2_ROM));
            if (row < 1) throw new IllegalStateException("Working directory lacks " + S2_ROM);
            for (int i = 0; i < row; i++) press(screen, input, GLFW_KEY_DOWN);
        }
        // One update with Enter: the picker returns its choice and the page starts discovery.
        input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS);
        screen.update(input);
        input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_RELEASE);
        input.update();
        if (onboardingField(screen, "discovery") == null)
            throw new IllegalStateException(scene + ": discovery did not start");
        if (scene.contains("found")) {
            settle(screen, input, () -> onboardingField(screen, "discovery") == null);
            if (((java.util.Map<?, ?>) onboardingField(screen, "found")).isEmpty())
                throw new IllegalStateException(scene + ": discovery found no games in " + ROM_FOLDER);
            System.out.println(scene + ": found " + onboardingField(screen, "found"));
        }
        if (scene.contains("scan-limit")) {
            settle(screen, input, () -> onboardingField(screen, "discovery") == null);
            Object notice = onboardingField(screen, "scanNotice");
            if (!"Stopped early, skipped unreadable files; scan their folder".equals(notice))
                throw new IllegalStateException(scene + ": the walker reported " + notice + ", " + onboardingField(screen, "message"));
            Path locked = Path.of(String.valueOf(scanLimitRoot.get())).resolve("locked");
            Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            System.out.println(scene + ": " + onboardingField(screen, "message") + " / " + notice);
        }
    }

    private static final java.util.concurrent.atomic.AtomicReference<Path> scanLimitRoot = new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * A scratch folder the scan cannot finish: a chain nine folders deep (the walker stops at eight)
     * and a folder it may not read. It holds no game, so the page reports both and finds none.
     */
    private static Path scanLimitTree(Path configDir) throws java.io.IOException {
        Path root = Files.createDirectories(configDir.resolve("scan-limit"));
        Path deep = root;
        for (int i = 0; i < 9; i++) deep = Files.createDirectories(deep.resolve("folder-" + i));
        Files.writeString(deep.resolve("notes.txt"), "not a game");
        Path locked = Files.createDirectories(root.resolve("locked"));
        Files.writeString(locked.resolve("hidden.txt"), "not readable");
        Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("---------"));
        scanLimitRoot.set(root);
        return root;
    }

    private static boolean likelyRom(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".gen") || name.endsWith(".bin") || name.endsWith(".md") || name.endsWith(".smd");
    }

    private interface Check { boolean done() throws Exception; }

    /** Updates in real time until a background read finishes; fails rather than capturing a wrong state. */
    private static void settle(MasterTitleScreen screen, InputHandler input, Check done) throws Exception {
        for (int i = 0; i < 1000; i++) {
            if (done.done()) return;
            Thread.sleep(10);
            screen.update(input);
            input.update();
        }
        throw new IllegalStateException("Background read did not finish within 10 s");
    }

    private static Object onboardingField(MasterTitleScreen screen, String name) throws ReflectiveOperationException {
        var page = MasterTitleScreen.class.getDeclaredField("onboarding");
        page.setAccessible(true);
        Object onboarding = page.get(screen);
        if (onboarding == null) throw new IllegalStateException("Add your games is not open");
        var field = onboarding.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(onboarding);
    }

    /** Each scene's standalone configuration is scratch; remove it once the PNG is written. */
    private static void deleteTree(Path root) {
        try (var walk = Files.walk(root)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (java.io.IOException ignored) {
            // Best effort: the capture never depends on removing its scratch configuration.
        }
    }

    private static List<Integer> advancedSettingKeys(SonicConfiguration key) {
        var keys = new java.util.ArrayList<>(List.of(GLFW_KEY_UP, GLFW_KEY_RIGHT, GLFW_KEY_RIGHT,
                GLFW_KEY_ENTER, GLFW_KEY_DOWN, GLFW_KEY_ENTER));
        var settings = com.openggf.configuration.ConfigCatalog.emitOrder().stream()
                .filter(k -> com.openggf.configuration.EngineSettingsDraft.category(k)
                        == com.openggf.configuration.EngineSettingsDraft.Category.ADVANCED).toList();
        for (int i = 0; i < settings.indexOf(key); i++) keys.add(GLFW_KEY_DOWN);
        return keys;
    }

    private static byte[] rgba(RgbaImage image) {
        byte[] bytes = new byte[image.width() * image.height() * 4];
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                int argb = image.argb(x, y);
                int i = (y * image.width() + x) * 4;
                bytes[i] = (byte) (argb >>> 16);
                bytes[i + 1] = (byte) (argb >>> 8);
                bytes[i + 2] = (byte) argb;
                bytes[i + 3] = (byte) 0xFF;
            }
        }
        return bytes;
    }

    @FunctionalInterface private interface Sequence { void shoot(String name) throws Exception; }

    /**
     * Keys in a "seq-" scene: the setup keys before {@code 0} are pressed normally; after the marker
     * each key is pressed and released in one frame, then {@code settleFrames} frames are drawn,
     * one picture per frame ({@code <name>-kNN-fNN}). {@code -1} holds the previous key for a frame.
     */
    private static void sequence(MasterTitleScreen screen, InputHandler input, Scene scene, Sequence shoot) throws Exception {
        boolean recording = false;
        int step = 0;
        for (int key : scene.keys()) {
            if (key == 0) { recording = true; for (int i = 0; i < 20; i++) screen.update(input); shoot.shoot(scene.name() + "-k00-f00"); continue; }
            if (!recording) { press(screen, input, key); continue; }
            step++;
            input.handleKeyEvent(key, GLFW_PRESS);
            screen.update(input);
            input.handleKeyEvent(key, GLFW_RELEASE);
            input.update();
            shoot.shoot(String.format("%s-k%02d-f00", scene.name(), step));
            for (int f = 1; f <= scene.settleFrames(); f++) {
                screen.update(input);
                input.update();
                shoot.shoot(String.format("%s-k%02d-f%02d", scene.name(), step, f));
            }
        }
    }

    private static void press(MasterTitleScreen screen, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        screen.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        screen.update(input);
        for (int i = 0; i < 4; i++) {
            input.update();
            screen.update(input);
        }
    }
}
