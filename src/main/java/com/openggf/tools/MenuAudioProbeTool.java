package com.openggf.tools;

import com.openggf.audio.AudioManager;
import com.openggf.audio.LWJGLAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.game.session.EngineServices;
import org.lwjgl.opengl.GL;

import java.nio.file.Files;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Measures menu draw cost and real speaker starvation during sequential cue playback.
 * Inputs: [width=320] [hub|settings|launch]; user ROMs provide the hub previews.
 * Uses an owned hidden GLFW window and synthetic InputHandler events, never desktop input.
 * Run with -XstartOnFirstThread on macOS. Reports warm-up separately from 240 paced frames;
 * this is a device/render diagnostic, not an Engine.loop timing or subjective listening test.
 * Origin: menu audio breakup follow-up to 2596fbc40, 2026-10-12.
 */
public final class MenuAudioProbeTool {
    private static final int WARMUP_FRAMES = 120;
    private static final int MEASURED_FRAMES = 240;
    private static final long FRAME_NANOS = 1_000_000_000L / 60;

    private MenuAudioProbeTool() { }

    public static void main(String[] args) throws Exception {
        int width = args.length > 0 ? Integer.parseInt(args[0]) : 320;
        String scene = args.length > 1 ? args[1] : "hub";
        if (width < 320 || width > 528 || !(scene.equals("hub") || scene.equals("settings") || scene.equals("launch")))
            throw new IllegalArgumentException("usage: MenuAudioProbeTool [width=320, up to 528] [hub|settings|launch]");
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        long window = 0;
        AudioManager audio = EngineServices.current().audio();
        MasterTitleScreen screen = null;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            window = glfwCreateWindow(width * 3, 224 * 3, "Menu audio probe", 0, 0);
            if (window == 0) throw new IllegalStateException("Unable to create owned hidden window");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glfwSwapInterval(0);
            var configDirectory = Files.createTempDirectory("menu-audio-probe");
            configDirectory.toFile().deleteOnExit();
            var config = SonicConfigurationService.createStandalone(configDirectory);
            config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
            audio.resetState();
            audio.setBackend(new LWJGLAudioBackend(config));
            audio.ensurePresentationSink();
            if (audio.consumedPresentationStereoFrames() < 0)
                throw new IllegalStateException("No physical speaker cursor; cannot measure underruns");
            screen = new MasterTitleScreen(config, new LaunchProfileStore(config),
                    cue -> audio.playSfx(cue.sfxName()));
            screen.initialize();
            screen.setViewportWidth(width);
            screen.setProjectionMatrix(new org.joml.Matrix4f().ortho2D(0, width, 0, 224).get(new float[16]));
            InputHandler input = new InputHandler();
            int[] framebufferWidth = new int[1], framebufferHeight = new int[1];
            glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
            glViewport(0, 0, framebufferWidth[0], framebufferHeight[0]);
            try (var capture = audio.beginLiveCaptureAudio(60)) {
                short[] pcm = new short[capture.maxStereoFramesPerPacket() * 2];
                long updateNanos = 0, audioNanos = 0, drawNanos = 0, finishNanos = 0;
                long warmupUnderruns = 0, deadline = System.nanoTime();
                int slowFrames = 0, peak = 0;
                long flashNanos = 0, flashMaxNanos = 0;
                for (int frame = 0; frame < WARMUP_FRAMES + MEASURED_FRAMES; frame++) {
                    if (frame == WARMUP_FRAMES) {
                        // Validate the scripted destination instead of trusting the scenario label.
                        var settingsField = MasterTitleScreen.class.getDeclaredField("settingsScreen");
                        settingsField.setAccessible(true);
                        if ((settingsField.get(screen) != null) != scene.equals("settings"))
                            throw new IllegalStateException("Script did not reach requested menu: " + scene);
                    }
                    long start = System.nanoTime();
                    if (scene.equals("settings")) {
                        keyAtFrame(input, frame, 40, GLFW_KEY_UP);
                        keyAtFrame(input, frame, 46, GLFW_KEY_RIGHT);
                        keyAtFrame(input, frame, 52, GLFW_KEY_ENTER);
                    }
                    if (scene.equals("launch")) keyAtFrame(input, frame, WARMUP_FRAMES, GLFW_KEY_ENTER);
                    if (frame >= WARMUP_FRAMES && !scene.equals("launch")) {
                        int first = scene.equals("settings") ? GLFW_KEY_DOWN : GLFW_KEY_RIGHT;
                        int second = scene.equals("settings") ? GLFW_KEY_UP : GLFW_KEY_LEFT;
                        if (frame % 6 == 0) input.handleKeyEvent((frame / 6) % 2 == 0 ? first : second, GLFW_PRESS);
                        if (frame % 6 == 1) {
                            input.handleKeyEvent(first, GLFW_RELEASE);
                            input.handleKeyEvent(second, GLFW_RELEASE);
                        }
                    }
                    screen.update(input);
                    if (scene.equals("launch") && frame == WARMUP_FRAMES && !screen.isGameSelected())
                        throw new IllegalStateException("Launch confirmation did not start; check the selected ROM");
                    input.update();
                    long updated = System.nanoTime();
                    audio.presentFrame(PresentationMode.FORWARD);
                    audio.update();
                    int frames = capture.drainPresentationFrame(pcm);
                    for (int i = 0; i < frames * 2; i++) peak = Math.max(peak, Math.abs(pcm[i]));
                    long synthesized = System.nanoTime();
                    glClear(GL_COLOR_BUFFER_BIT);
                    screen.draw();
                    long submitted = System.nanoTime();
                    glFinish();
                    glfwSwapBuffers(window);
                    glfwPollEvents();
                    long finished = System.nanoTime();
                    if (frame >= WARMUP_FRAMES) {
                        updateNanos += updated - start;
                        audioNanos += synthesized - updated;
                        drawNanos += submitted - synthesized;
                        finishNanos += finished - submitted;
                        if (finished - start > FRAME_NANOS) slowFrames++;
                        if (scene.equals("launch") && frame < WARMUP_FRAMES + 14) {
                            flashNanos += finished - start;
                            flashMaxNanos = Math.max(flashMaxNanos, finished - start);
                            System.out.printf(Locale.ROOT,
                                    "flashFrame=%d updateMs=%.3f audioMs=%.3f drawMs=%.3f finishMs=%.3f%n",
                                    frame - WARMUP_FRAMES, (updated - start) / 1_000_000.0,
                                    (synthesized - updated) / 1_000_000.0, (submitted - synthesized) / 1_000_000.0,
                                    (finished - submitted) / 1_000_000.0);
                        }
                    } else if (frame == WARMUP_FRAMES - 1) {
                        warmupUnderruns = audio.presentationUnderrunCount();
                    }
                    deadline += FRAME_NANOS;
                    long waitNanos = deadline - System.nanoTime();
                    if (waitNanos > 0) Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
                }
                if (peak == 0) throw new IllegalStateException("No cue PCM; invalid playback measurement");
                double denominator = MEASURED_FRAMES * 1_000_000.0;
                System.out.printf(Locale.ROOT,
                        "scene=%s width=%d frames=%d updateMs=%.3f audioMs=%.3f drawMs=%.3f finishMs=%.3f slowFrames=%d warmupUnderruns=%d underruns=%d peak=%d consumedFrames=%d%n",
                        scene, width, MEASURED_FRAMES, updateNanos / denominator, audioNanos / denominator,
                        drawNanos / denominator, finishNanos / denominator, slowFrames, warmupUnderruns,
                        audio.presentationUnderrunCount() - warmupUnderruns, peak, audio.consumedPresentationStereoFrames());
                if (scene.equals("launch")) System.out.printf(Locale.ROOT,
                        "flashFrames=14 meanMs=%.3f maxMs=%.3f (menu confirmation only; no game bootstrap)%n",
                        flashNanos / 14_000_000.0, flashMaxNanos / 1_000_000.0);
            }
        } finally {
            if (screen != null) screen.cleanup();
            audio.resetState();
            if (window != 0) glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private static void keyAtFrame(InputHandler input, int frame, int pressFrame, int key) {
        if (frame == pressFrame) input.handleKeyEvent(key, GLFW_PRESS);
        else if (frame == pressFrame + 1) input.handleKeyEvent(key, GLFW_RELEASE);
    }
}
