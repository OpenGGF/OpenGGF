package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.MasterTitleScreen;
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
 * Inputs: {@code <output-dir> [viewport-width=320] [scale=3]}; the working directory's
 * ROM images supply the title logos exactly as at startup. On macOS run with
 * {@code -XstartOnFirstThread}. Originating task: the 2026-10 master title redesign
 * (branch {@code feature/ai-master-title-redesign}).
 */
public final class MasterTitleCaptureTool {

    private record Scene(String name, List<Integer> keys, int settleFrames) { }

    private MasterTitleCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: MasterTitleCaptureTool <output-dir> [viewport-width] [scale]");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 320;
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : 3;
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
                new Scene("18-confirm", List.of(enter), 3));

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
            for (Scene scene : scenes) {
                Path configDir = Files.createTempDirectory("master-title-capture");
                SonicConfigurationService config = SonicConfigurationService.createStandalone(configDir);
                config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
                MasterTitleScreen screen = new MasterTitleScreen(config);
                screen.initialize();
                screen.setEnabledModsByGame(Map.of(MasterTitleScreen.GameEntry.SONIC_2,
                        List.of("Widescreen+", "HUD Tweaks")));
                InputHandler input = new InputHandler();
                for (int i = 0; i < 20; i++) screen.update(input);
                for (int key : scene.keys()) press(screen, input, key);
                for (int i = 0; i < scene.settleFrames(); i++) screen.update(input);
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
