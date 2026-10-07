package com.openggf.tools.challenge;

import static com.openggf.tools.challenge.ChallengePresentation.HEIGHT;
import static com.openggf.tools.challenge.ChallengePresentation.WIDTH;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;

import com.openggf.audio.GameSound;
import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.GlfwGamepadStateSource;
import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.tools.challenge.ChallengePresentation.Scene;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/** Polished JVM-first process-host prototype: three production games, one held pad. */
public final class ThreeOpeningsTool {
    private final List<ChallengeHost.Member> members;
    private final ChallengeInputProgram program;
    private final ChallengeCapture capture;
    private final ExecutorService runner =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "challenge-host-admission"));
    private final GamepadStateSource pads = new GlfwGamepadStateSource();
    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private long window, generation, nextTick, sceneAt = System.nanoTime();
    private Scene scene = Scene.TITLE;
    private ChallengeHost host;
    private CompletableFuture<List<ChallengeProtocol.Frame>> pending;
    private List<ChallengeProtocol.Frame> frames = List.of();
    private int submittedHeld, focus, oldFocus, fadeTicks;
    private boolean padAcceptBefore, padPauseBefore, padFocusBefore, padRestartBefore, hadPad, focused = true;
    private String fault = "", audioFault;
    private OpenAlPcmSink sink;
    private ChallengeMenuAudio menuAudio;
    private int countdownCue;
    private ChallengePresentation presentation;
    private final Set<String> captured = new HashSet<>();
    private long stepStarted;
    private double lastStepMs;
    private final long[] stepTimes = new long[36000];
    private int stepCount;

    private ThreeOpeningsTool(List<ChallengeHost.Member> members, ChallengeInputProgram program,
            Path captureDirectory) throws IOException {
        this.members = List.copyOf(members);
        this.program = program;
        capture = captureDirectory == null ? null : new ChallengeCapture(captureDirectory);
    }
    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        if (options.containsKey("help")) {
            System.out.println("Three Openings — one pad, three worlds\njava -cp <OpenGGF jar> "
                    + "com.openggf.tools.challenge.ThreeOpeningsTool --s1 <rom> --s2 <rom> --s3k "
                    + "<rom>\nOptional: --program <common.pad> --capture-dir <outside-repo "
                    + "directory>\nArrows move; Z=A X=B C=C; Enter=game Start; P=host pause; "
                    + "R=restart; Tab=audio focus; Esc=title/exit.");
            return;
        }
        List<ChallengeHost.Member> members = new ArrayList<>();
        for (String game : List.of("s1", "s2", "s3k"))
            members.add(
                    new ChallengeHost.Member(game, game, Path.of(options.getOrDefault(game, game + ".gen"))));
        ChallengeInputProgram program = options.containsKey("program")
                ? ChallengeInputProgram.read(Path.of(options.get("program")))
                : null;
        var tool = new ThreeOpeningsTool(members, program,
                options.containsKey("capture-dir") ? Path.of(options.get("capture-dir")) : null);
        tool.run();
    }
    private static Map<String, String> options(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (key.equals("--help")) {
                values.put("help", "");
                continue;
            }
            if (!Set.of("--s1", "--s2", "--s3k", "--program", "--capture-dir").contains(key)
                    || i + 1 >= args.length || values.containsKey(key.substring(2)))
                throw new IllegalArgumentException("Unknown, duplicate or incomplete option: " + key);
            values.put(key.substring(2), args[++i]);
        }
        return values;
    }
    private void run() throws Exception {
        Throwable terminalFailure = null;
        try {
            if (!glfwInit())
                throw new IllegalStateException("The desktop display could not be opened");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            window = glfwCreateWindow(WIDTH, HEIGHT, "Three Openings | OpenGGF", 0, 0);
            if (window == 0)
                throw new IllegalStateException("The game window could not be created");
            glfwMakeContextCurrent(window);
            glfwSwapInterval(1);
            GL.createCapabilities();
            glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
                if (key >= 0 && key < keys.length)
                    keys[key] = action != GLFW_RELEASE;
            });
            glfwSetWindowFocusCallback(window, (w, value) -> {
                focused = value;
                if (!value) {
                    Arrays.fill(keys, false);
                    if (scene == Scene.PLAY)
                        pause();
                }
            });
            presentation = new ChallengePresentation(window);
            try {
                initializeSound();
                cue(GameSound.CHECKPOINT);
            } catch (IOException failure) {
                System.err.println("Title sound unavailable: " + failure.getMessage());
            }
            boolean[] previous = new boolean[keys.length];
            while (!glfwWindowShouldClose(window)) {
                glfwPollEvents();
                update(previous);
                if (capture != null)
                    capture.presentation(scene.name(), generation, host == null ? 0 : host.tick(),
                            focus, pending != null, focused);
                presentation.draw(new ChallengePresentation.View(
                        scene, elapsed(), focus, fault, lastStepMs, host == null ? 0 : host.tick()));
                if (capture != null)
                    capturePresentation();
                glfwSwapBuffers(window);
                System.arraycopy(keys, 0, previous, 0, keys.length);
                if (program != null && scene == Scene.TITLE && elapsed() > 0.8)
                    launch();
                if (program != null && scene == Scene.READY && elapsed() > 0.7)
                    countdown();
                if (program != null && scene == Scene.FAULT)
                    throw new IOException(fault);
            }
        } catch (Throwable failure) {
            terminalFailure = failure;
        } finally {
            terminalFailure = ChallengeCleanup.closeAll(terminalFailure, this::closeRun,
                    ()
                            -> {
                        runner.shutdownNow();
                        if (!runner.awaitTermination(5, TimeUnit.SECONDS))
                            throw new IOException("Host admission cleanup pending");
                    },
                    this::disposeSound,
                    ()
                            -> {
                        if (capture != null)
                            capture.close();
                    },
                    ()
                            -> {
                        if (presentation != null)
                            presentation.close();
                    },
                    ()
                            -> {
                        if (window != 0)
                            glfwFreeCallbacks(window);
                    },
                    ()
                            -> {
                        if (window != 0)
                            glfwDestroyWindow(window);
                    },
                    () -> glfwTerminate());
            if (stepCount > 0) {
                long[] times = Arrays.copyOf(stepTimes, stepCount);
                Arrays.sort(times);
                System.out.printf(Locale.ROOT, "Challenge steps=%d p50=%.2fms p95=%.2fms p99=%.2fms%n",
                        stepCount, times[stepCount / 2] / 1e6,
                        times[Math.min(stepCount - 1, (int) (stepCount * .95))] / 1e6,
                        times[Math.min(stepCount - 1, (int) (stepCount * .99))] / 1e6);
            }
        }
        ChallengeCleanup.rethrow(terminalFailure);
    }
    private void update(boolean[] previous) throws IOException {
        var connected = pads.pollDevices()
                                .stream()
                                .filter(GamepadStateSource.DeviceState::connected)
                                .findFirst()
                                .orElse(null);
        boolean accept = focused && connected != null && connected.buttonDown(GLFW_GAMEPAD_BUTTON_A);
        boolean padPause = focused && connected != null && connected.buttonDown(GLFW_GAMEPAD_BUTTON_BACK);
        boolean padFocus =
                focused && connected != null && connected.buttonDown(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER);
        boolean padRestart =
                focused && connected != null && connected.buttonDown(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER);
        if (hadPad && connected == null && scene == Scene.PLAY)
            pause();
        hadPad = connected != null;
        if (edge(GLFW_KEY_ESCAPE, previous)) {
            if (scene == Scene.TITLE)
                glfwSetWindowShouldClose(window, true);
            else {
                closeRun();
                transition(Scene.TITLE);
            }
        }
        if ((edge(GLFW_KEY_ENTER, previous) || (accept && !padAcceptBefore))
                && (scene == Scene.TITLE || scene == Scene.FAULT))
            launch();
        else if ((edge(GLFW_KEY_ENTER, previous) || (accept && !padAcceptBefore)) && scene == Scene.READY)
            countdown();
        if (edge(GLFW_KEY_P, previous) || (padPause && !padPauseBefore)) {
            if (scene == Scene.PLAY)
                pause();
            else if (scene == Scene.PAUSE) {
                if (sink != null)
                    sink.resume();
                transition(Scene.PLAY);
                nextTick = System.nanoTime();
            }
        }
        if ((edge(GLFW_KEY_R, previous) || (padRestart && !padRestartBefore))
                && (scene == Scene.PLAY || scene == Scene.PAUSE || scene == Scene.FAULT))
            launch();
        if (edge(GLFW_KEY_TAB, previous) || (padFocus && !padFocusBefore))
            changeFocus((focus + 1) % 3);
        for (int i = 0; i < 3; i++)
            if (edge(GLFW_KEY_1 + i, previous))
                changeFocus(i);
        padAcceptBefore = accept;
        padPauseBefore = padPause;
        padFocusBefore = padFocus;
        padRestartBefore = padRestart;
        if (pending != null && pending.isDone()) {
            try {
                frames = pending.join();
                pending = null;
                presentation.upload(frames);
                if (scene == Scene.LOADING) {
                    cue(GameSound.CHECKPOINT);
                    transition(Scene.READY);
                } else if (scene == Scene.COUNTDOWN) {
                    if (sink != null) {
                        sink.onReverseBoundary();
                        sink.resume();
                    }
                    cue(GameSound.SPRING);
                    transition(Scene.PLAY);
                    nextTick = System.nanoTime();
                    if (!focused && program == null)
                        pause();
                } else {
                    lastStepMs = (System.nanoTime() - stepStarted) / 1e6;
                    if (stepCount < stepTimes.length)
                        stepTimes[stepCount++] = System.nanoTime() - stepStarted;
                    short[] pcm = focusedPcm();
                    if (menuAudio != null)
                        pcm = menuAudio.mix(pcm);
                    if (capture != null)
                        capture.frame(frames, submittedHeld, pcm);
                    if (scene == Scene.PLAY && sink != null) {
                        sink.acceptStereoPcm(pcm);
                        sink.updateDevice();
                    }
                    if (program != null && host.tick() >= program.length()) {
                        glfwSetWindowShouldClose(window, true);
                    }
                }
            } catch (CompletionException failure) {
                pending = null;
                fail(failure.getCause());
            }
        }
        if (audioFault != null) {
            String message = audioFault;
            audioFault = null;
            disposeSound();
            fail(new IOException(message));
        }
        if (scene == Scene.COUNTDOWN) {
            int count = (int) elapsed();
            if (count > countdownCue && count < 3) {
                countdownCue = count;
                cue(GameSound.RING);
            }
        }
        if (menuAudio != null)
            menuAudio.update(scene != Scene.PLAY);
        if (sink != null)
            sink.updateDevice();
        if (scene == Scene.COUNTDOWN && elapsed() >= 3 && pending == null) {
            ChallengeHost run = host;
            pending = async(run::start);
        }
        if (scene == Scene.PLAY && pending == null && System.nanoTime() >= nextTick) {
            int held = program == null ? ChallengeHostInput.held(connected, keys)
                                       : program.heldAt((int) host.tick());
            if (!focused && program == null)
                held = 0;
            submittedHeld = held;
            stepStarted = System.nanoTime();
            ChallengeHost run = host;
            final int sample = held;
            pending = async(() -> run.step(sample));
            // Keep a stable deadline instead of accumulating a fresh UI-frame
            // delay after every submission. A slow tuple never triggers catch-up.
            nextTick = Math.max(nextTick + 16_666_667, System.nanoTime());
        }
        if (sink != null && scene == Scene.PLAY)
            sink.updateDevice();
    }
    private void launch() {
        closeRun();
        fault = "";
        frames = List.of();
        generation++;
        presentation.reset(generation);
        try {
            initializeSound();
            sink.onReverseBoundary();
            sink.resume();
            cue(GameSound.CHECKPOINT);
            host = new ChallengeHost(members, generation);
            transition(Scene.LOADING);
            ChallengeHost run = host;
            pending = async(run::prepare);
        } catch (Throwable failure) {
            fail(failure);
        }
    }
    private void countdown() {
        countdownCue = 0;
        cue(GameSound.RING);
        transition(Scene.COUNTDOWN);
    }
    private void pause() {
        if (sink != null) {
            sink.onReverseBoundary();
            sink.resume();
        }
        cue(GameSound.JUMP);
        transition(Scene.PAUSE);
    }
    private void changeFocus(int selected) {
        if (selected == focus)
            return;
        oldFocus = focus;
        focus = selected;
        fadeTicks = 4;
        cue(GameSound.RING);
    }
    private short[] focusedPcm() {
        short[] next = frames.get(focus).pcm();
        if (fadeTicks == 0)
            return next;
        short[] old = frames.get(oldFocus).pcm();
        short[] mixed = new short[next.length];
        double gain = (5 - fadeTicks) / 4.0;
        for (int i = 0; i < mixed.length; i++)
            mixed[i] = (short) Math.round(next[i] * gain + (i < old.length ? old[i] : 0) * (1 - gain));
        fadeTicks--;
        return mixed;
    }
    private <T> CompletableFuture<T> async(Callable<T> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, runner);
    }
    private void initializeSound() throws IOException {
        if (sink == null) {
            try {
                sink = OpenAlPcmSink.openDefault(e
                        -> audioFault = "Sound output stopped. Check your audio device and retry.",
                        System.err::println);
            } catch (RuntimeException failure) {
                throw new IOException("Sound output could not open. Check your audio device and retry.", failure);
            }
            if (sink.sampleRate() != ChallengeProtocol.RATE) {
                sink.close();
                sink = null;
                throw new IOException("This prototype needs a 48 kHz sound device.");
            }
        }
        if (menuAudio == null)
            menuAudio = new ChallengeMenuAudio(members.getFirst().rom(), sink, capture);
    }
    private void disposeSound() throws IOException {
        ChallengeMenuAudio oldMenu = menuAudio;
        menuAudio = null;
        OpenAlPcmSink oldSink = sink;
        sink = null;
        try {
            if (oldMenu != null)
                oldMenu.close();
        } finally {
            if (oldSink != null)
                oldSink.close();
        }
    }
    private void cue(GameSound sound) {
        if (menuAudio != null)
            menuAudio.cue(sound);
    }
    private void fail(Throwable failure) {
        failure.printStackTrace(System.err);
        fault = message(failure);
        closeRun();
        transition(Scene.FAULT);
        cue(GameSound.ERROR);
    }
    private String message(Throwable failure) {
        String text = failure.getMessage();
        if (text == null)
            return "A game stopped. Restart all three to try again.";
        if (text.startsWith("Missing or invalid"))
            return "A ROM is missing. Check the launch paths, then retry.";
        if (text.startsWith("Wrong revision"))
            return "A ROM revision does not match. Use the versions in the guide.";
        if (text.startsWith("Sound output") || text.contains("48 kHz"))
            return text;
        return "A game could not continue. Restart all three to try again.";
    }
    private void closeRun() {
        ChallengeHost old = host;
        host = null;
        CompletableFuture<?> oldPending = pending;
        pending = null;
        Throwable failure = ChallengeCleanup.closeAll(null,
                ()
                        -> {
                    if (old != null)
                        old.close();
                },
                ()
                        -> {
                    if (oldPending != null)
                        oldPending.cancel(true);
                },
                () -> {
                    if (sink != null) {
                        sink.onReverseBoundary();
                        sink.resume();
                    }
                });
        if (failure != null)
            throw new IllegalStateException("Challenge teardown incomplete", failure);
    }
    private void transition(Scene next) {
        scene = next;
        sceneAt = System.nanoTime();
        if (capture != null)
            try {
                capture.presentation(scene.name(), generation, host == null ? 0 : host.tick(),
                        focus, pending != null, focused);
            } catch (IOException failure) {
                throw new IllegalStateException("Transition capture failed", failure);
            }
    }
    private double elapsed() {
        return (System.nanoTime() - sceneAt) / 1e9;
    }
    private boolean edge(int key, boolean[] previous) {
        return keys[key] && !previous[key];
    }
    private void capturePresentation() throws IOException {
        String name = switch (scene) {
            case TITLE -> elapsed() > .3 ? "title" : null;
            case LOADING -> elapsed() > .25 ? "loading" : null;
            case READY -> elapsed() > .3 ? "ready" : null;
            case COUNTDOWN -> elapsed() > .3 ? "countdown" : null;
            case PLAY ->
                host != null && program != null && host.tick() >= program.length() ? "play-final"
                        : host != null&& host.tick() == 180           ? "play-180"
                        : host != null&& host.tick() == 600 ? "play-600"
                        : host != null&& host.tick() == 1536 ? "play-1536"
                                                            : null;
            case PAUSE -> elapsed() > .3 ? "pause" : null;
            case FAULT -> elapsed() > .3 ? "fault" : null;
        };
        if (name != null && captured.add(name))
            capture.screenshot(name, presentation.framebufferWidth(), presentation.framebufferHeight());
    }
}
