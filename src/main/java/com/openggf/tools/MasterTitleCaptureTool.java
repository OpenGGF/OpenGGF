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
 * saved frame-rate change waiting for a restart (hub notice and Settings summary). On macOS run with {@code -XstartOnFirstThread}.
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
                new Scene("08-setup-edited", List.of(down, down, right, enter, down, down, down, right), 20),
                new Scene("09-setup-prompt", List.of(down, down, right, enter, down, down, down, right, esc), 20),
                new Scene("10-hub-overridden", List.of(down, down, right, enter, down, down, down, right, esc, enter), 30),
                new Scene("11-settings", List.of(up, right, enter), 20),
                new Scene("12-settings-tabs", List.of(up, right, enter, up), 20),
                new Scene("13-settings-gameplay", List.of(up, right, enter, up, right, right, right, down), 20),
                new Scene("14-settings-controls", List.of(up, right, enter, up, right, right, down, down, down, down,
                        down, down, down, down, down, down), 20),
                new Scene("15-advanced", List.of(up, right, right, enter), 20),
                new Scene("16-help", List.of(up, right, right, enter, down, down, enter), 20),
                new Scene("17-quit", List.of(esc), 20),
                new Scene("18-confirm", List.of(enter), 3),
                new Scene("38-hub-crowded", List.of(), 40),
                new Scene("39-hub-missing-rom", List.of(), 40),
                new Scene("40-locate-rom", List.of(enter), 40),
                new Scene("41-settings-save", List.of(up, right, enter, right, up, up), 20),
                new Scene("42-setup-save", List.of(down, right, enter, right, up), 20));
        List<Scene> pausedScenes = List.of(
                new Scene("19-paused-card", List.of(), 40),
                new Scene("20-paused-end-button", List.of(down, right), 40),
                new Scene("21-paused-start-other", List.of(right, enter), 20),
                new Scene("22-paused-quit", List.of(esc), 20),
                new Scene("23-paused-resume-flash", List.of(enter), 3),
                new Scene("24-paused-setup-button", List.of(down, right), 40),
                new Scene("25-paused-setup-rewind", List.of(down, right, enter, down, down, down, down), 20),
                new Scene("26-paused-setup-character", List.of(down, right, enter), 20),
                new Scene("27-paused-settings", List.of(up, right, enter), 20),
                new Scene("28-paused-settings-row", List.of(up, right, enter, down), 20),
                new Scene("29-settings-restart-row", advancedSettingKeys(SonicConfiguration.FPS), 20),
                new Scene("30-hub-restart-pending", List.of(), 40),
                new Scene("31-settings-restart-pending", List.of(up, right, enter, up), 20),
                new Scene("32-paused-zoom-start", List.of(), 0),
                new Scene("33-paused-zoom-middle", List.of(), 16),
                new Scene("34-paused-zoom-end", List.of(), 32),
                new Scene("35-paused-resume-zoom-start", List.of(), 0),
                new Scene("36-paused-resume-zoom-middle", List.of(), 16),
                new Scene("37-paused-resume-zoom-end", List.of(), 32));

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        long window = NULL;
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
            window = glfwCreateWindow(fbw, fbh, "Master title capture", NULL, NULL);
            if (window == NULL) throw new IllegalStateException("Failed to create hidden window");
            glfwMakeContextCurrent(window);
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
                if (scene.name().contains("crowded")) {
                    var store = new com.openggf.game.launch.LaunchProfileStore(config);
                    store.save(MasterTitleScreen.GameEntry.SONIC_2, new com.openggf.game.launch.LaunchProfile(
                            com.openggf.game.launch.LaunchToggle.ON, "s3k",
                            com.openggf.game.launch.LaunchToggle.ON, "NATIVE_4_3", "sonic", "tails"));
                }
                MasterTitleScreen screen = new MasterTitleScreen(config);
                screen.initialize();
                if (scene.name().contains("crowded")) screen.setTitleEntries(List.of("Extra mode"), (games, index) -> { });
                screen.setEnabledModsByGame(Map.of(MasterTitleScreen.GameEntry.SONIC_2,
                        List.of("Widescreen+", "HUD Tweaks")));
                if (paused != null && scene.name().contains("paused")) {
                    screen.setSuspendedGame(new MasterTitleEntry.Suspended(
                                    new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2), "Emerald Hill, Act 1", true),
                            paused.width(), paused.height(), rgba(paused));
                }
                InputHandler input = new InputHandler();
                for (int i = 0; i < 20; i++) screen.update(input);
                for (int key : scene.keys()) press(screen, input, key);
                if (scene.name().contains("paused-zoom")) screen.animateSuspendedGame();
                if (scene.name().contains("paused-resume-zoom")) {
                    input.handleKeyEvent(enter, GLFW_PRESS);
                    screen.update(input);
                    input.handleKeyEvent(enter, GLFW_RELEASE);
                    input.update();
                }
                for (int i = 0; i < scene.settleFrames(); i++) {
                    // The file picker reads asynchronously; allow real time for its folder listing.
                    if (scene.name().contains("locate-rom")) Thread.sleep(10);
                    screen.update(input);
                }
                int[] fb = new int[2];
                int[] fbH = new int[1];
                glfwGetFramebufferSize(window, fb, fbH);
                glViewport(0, 0, fb[0], fbH[0]);
                glClearColor(0, 0, 0, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                screen.setViewportWidth(width);
                screen.setProjectionMatrix(projection);
                screen.draw();
                glFinish();
                ScreenshotCapture.captureAndSavePNG(fb[0], fbH[0], out.resolve(scene.name() + ".png"));
                screen.cleanup();
                System.out.println("wrote " + scene.name());
            }
        } finally {
            if (window != NULL) glfwDestroyWindow(window);
            glfwTerminate();
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
