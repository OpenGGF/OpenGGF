package com.openggf.tools;

import com.openggf.Engine;
import com.openggf.InputBindingFactory;
import com.openggf.audio.AudioManager;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.NullAudioBackend;
import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.session.EngineContext;
import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;
import com.openggf.mods.BundledModManifest;
import com.openggf.mods.code.OwnedSceneFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Display-free capture driver for the ordinary Engine startup and Time Attack UI.
 * Inputs: an output/profile directory and an existing ROM directory. Run with an
 * isolated working directory: the mod's identity and ghost paths are relative to it.
 * Origin: 2026-10-21 Time Attack relay-capacity video task.
 *
 * <p>The public configured-session seam deliberately omits platform initialization.
 * Reflection here reuses its three remaining production presentation/boot operations;
 * no gameplay state or scene state is injected. A frame is Engine.display(), including
 * hosted-run callbacks and HUD, with no native window, swap, or desktop event polling.
 */
public final class TimeAttackVideoCaptureTool implements AutoCloseable {
    private final int width;
    private final int height;
    private final Path output;
    private final HeadlessGameBoot glBoot;
    private final Engine engine;
    private final InputHandler input;
    private final AudioManager audio;
    private final HeadlessSmpsAudioBackend offlineBackend;
    private final GlReadPixelsGrabber grabber;
    private final Method display;
    private long frames;
    private Consumer<TimeAttackVideoCaptureTool> beforeFrame = ignored -> { };
    private Consumer<TimeAttackVideoCaptureTool> afterFrame = ignored -> { };
    private boolean closed;

    public TimeAttackVideoCaptureTool(Path output, Path romRoot) throws Exception {
        this(output, romRoot, 960, 672);
    }

    public TimeAttackVideoCaptureTool(Path output, Path romRoot, int width, int height) throws Exception {
        this(output, romRoot, width, height, 320);
    }

    /**
     * {@code logicalWidth} is the engine's logical screen width (320, or a widescreen width such as
     * 400); the window is {@code width} x {@code height} pixels and should be an integer multiple.
     */
    public TimeAttackVideoCaptureTool(Path output, Path romRoot, int width, int height, int logicalWidth)
            throws Exception {
        this.output = output.toAbsolutePath().normalize();
        this.width = width;
        this.height = height;
        Files.createDirectories(this.output);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        System.setProperty("openggf.saveRoot", this.output.resolve("profile/saves").toString());
        // The capture is a headless composition root, like HeadlessGameBoot: take the
        // process owners from the bootstrap bridge and replace only the configuration.
        EngineContext process = EngineContext.fromLegacySingletonsForBootstrap();
        // Retire any inherited device owner before boot or external content can play.
        audio = process.audio();
        audio.setBackend(new NullAudioBackend());
        SonicConfigurationService config = SonicConfigurationService.createStandalone(this.output.resolve("profile"));
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, width);
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, height);
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, logicalWidth);
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        config.setSessionOverride(SonicConfiguration.ROMS_DIRECTORY, romRoot.toAbsolutePath().normalize().toString());
        config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP, true);
        config.setSessionOverride(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP, true);
        config.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, false);
        EngineContext context = new EngineContext(config, process.graphics(), audio,
                process.roms(), process.profiler(), process.debugOverlay(),
                process.playbackDebug(), process.romDetection(), process.crossGameFeatures());
        try {
            glBoot = new HeadlessGameBoot(width, height, context);
            engine = new Engine(context);
            input = new InputHandler(InputBindingFactory.supplier(config));
            offlineBackend = new HeadlessSmpsAudioBackend(config, context.profiler(), audio, audio);
            grabber = new GlReadPixelsGrabber(width, height);
            display = method("display");
            invoke(method("initializeExternalContentAtBoot"));
            invoke(method("initializePresentationGraphics"));
            // HeadlessGameBoot supplied a framebuffer-sized standalone projection before
            // Engine existed. Engine now owns logical projection (including mode changes);
            // leaving that local override installed shrinks hosted sprites and menus at 3x.
            context.graphics().setProjectionMatrixBuffer(null);
            invoke(method("reshape", int.class, int.class), width, height);
            engine.initializeConfiguredHeadlessSession(input, offlineBackend);
            requireOfflineAudioOwner();
        } catch (Throwable failure) {
            try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public Engine engine() { return engine; }
    public InputHandler input() { return input; }
    public long frameNumber() { return frames; }
    public int width() { return width; }
    public int height() { return height; }
    public void beforeFrame(Consumer<TimeAttackVideoCaptureTool> hook) { beforeFrame = hook; }
    public void afterFrame(Consumer<TimeAttackVideoCaptureTool> hook) { afterFrame = hook; }

    public void frame() throws Exception {
        if (closed) throw new IllegalStateException("capture closed");
        requireOfflineAudioOwner();
        beforeFrame.accept(this);
        requireOfflineAudioOwner();
        if (!(Boolean) invoke(display)) throw new IllegalStateException("Engine aborted its display frame");
        requireOfflineAudioOwner();
        frames++;
        afterFrame.accept(this);
        requireOfflineAudioOwner();
    }

    /** Exact retained no-device owner, including across scene-launched level loads. */
    private void requireOfflineAudioOwner() {
        if (!audio.hasInstalledBackend(offlineBackend)) {
            throw new IllegalStateException("Capture's supplied offline audio backend was replaced");
        }
    }

    public void frames(int count) throws Exception {
        for (int i = 0; i < count; i++) frame();
    }

    /** Use real physical keyboard events so both menu layers and gameplay see them. */
    public void key(int key, boolean held) { input.handleKeyEvent(key, held ? GLFW_PRESS : GLFW_RELEASE); }

    public void tap(int key) throws Exception {
        key(key, true);
        frame();
        key(key, false);
        frame();
    }

    /** Returns reusable RGBA bytes in GL bottom-up order, ready for ffmpeg vflip. */
    public byte[] pixels() { return grabber.grab(); }

    public void png(String name) throws IOException {
        capture(output.resolve(name));
    }

    public void capture(Path destination) throws IOException {
        byte[] rgba = pixels();
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = ((height - 1 - y) * width + x) * 4;
                argb[y * width + x] = ((rgba[index + 3] & 255) << 24) | ((rgba[index] & 255) << 16)
                        | ((rgba[index + 1] & 255) << 8) | (rgba[index + 2] & 255);
            }
        }
        PngCodec.write(destination, new PixelImage(width, height, argb));
    }

    /**
     * Ordinary fresh-start route; every transition is driven by the actual input handler. Time
     * Attack is a bundled mod: it reaches Extras only when the engine's classpath carries the
     * bundled-mod manifest that {@code package} writes, so a build without it fails here instead of
     * navigating the title into some other page.
     */
    public void openTimeAttack() throws Exception {
        requireBundledTimeAttack();
        frames(40);
        png("01-legal.png");
        frames(310);
        tap(GLFW_KEY_ENTER);
        for (int i = 0; i < 180 && engine.getCurrentGameMode() != GameMode.MASTER_TITLE_SCREEN; i++) frame();
        if (engine.getCurrentGameMode() != GameMode.MASTER_TITLE_SCREEN)
            throw new IllegalStateException("startup did not reach master title: " + engine.getCurrentGameMode());
        frames(80);
        png("02-master-title.png");
        tap(GLFW_KEY_UP);
        tap(GLFW_KEY_RIGHT);
        tap(GLFW_KEY_RIGHT);
        tap(GLFW_KEY_ENTER);
        frames(30);
        png("03-extras.png");
        tap(GLFW_KEY_ENTER);
        frames(80);
        png("04-time-attack.png");
        if (engine.getCurrentGameMode() != GameMode.MOD_SCENE)
            throw new IllegalStateException("Extras did not open Time Attack: " + engine.getCurrentGameMode());
        Object scene = openScene();
        if (scene == null || !TIME_ATTACK_SCENE.equals(scene.getClass().getName()))
            throw new IllegalStateException("Extras opened " + (scene == null ? "no scene" : scene.getClass().getName())
                    + ", not Time Attack");
    }

    private static final String TIME_ATTACK_SCENE = "openggf.timeattack.TimeAttackScene";

    /** The creator's open scene, read from the game loop's scene host (capture inspection only). */
    public Object openScene() throws ReflectiveOperationException {
        java.lang.reflect.Field loopField = Engine.class.getDeclaredField("gameLoop");
        loopField.setAccessible(true);
        Object loop = loopField.get(engine);
        java.lang.reflect.Field hostField = loop.getClass().getDeclaredField("modSceneHost");
        hostField.setAccessible(true);
        Object host = hostField.get(loop);
        java.lang.reflect.Field sceneField = host.getClass().getDeclaredField("scene");
        sceneField.setAccessible(true);
        Object scene = sceneField.get(host);
        return scene instanceof com.openggf.mods.scene.ModScene owned ? OwnedSceneFactory.unwrap(owned) : scene;
    }

    private static void requireBundledTimeAttack() throws IOException {
        try (var manifest = Engine.class.getClassLoader().getResourceAsStream(BundledModManifest.RESOURCE)) {
            if (manifest == null || BundledModManifest.parse(manifest.readAllBytes()).mods().stream()
                    .noneMatch(entry -> entry.id().equals("time-attack")))
                throw new IllegalStateException("The engine classpath has no bundled Time Attack ("
                        + BundledModManifest.RESOURCE + "); run `mvn -DskipTests package` so target/classes"
                        + " carries the manifest and target/bundled the mod jar");
        }
    }

    private static Method method(String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = Engine.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private Object invoke(Method method, Object... arguments) throws Exception {
        try { return method.invoke(engine, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw new RuntimeException(failure.getCause());
        }
    }

    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        try {
            if (engine != null) invoke(method("cleanup"));
        } finally {
            try {
                if (engine != null) engine.closeConfiguredHeadlessSession();
            } finally {
                try {
                    if (grabber != null) grabber.close();
                } finally {
                    Engine.clearGlobalInstance();
                    if (glBoot != null) glBoot.close();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: TimeAttackVideoCaptureTool OUTPUT_DIR ROM_DIR");
        try (TimeAttackVideoCaptureTool capture = new TimeAttackVideoCaptureTool(Path.of(args[0]), Path.of(args[1]), 960, 672)) {
            capture.frame();
            capture.png("00-first-frame.png");
            capture.openTimeAttack();
            System.out.println("Captured real startup to " + capture.output + " in " + capture.frames + " frames");
        }
    }
}
