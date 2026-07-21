package com.openggf.tools.fbzvisual;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.level.LevelManager;
import com.openggf.tools.HeadlessGameBoot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Real hidden-GL FBZ capture session using the production gameplay renderer.
 */
public final class HiddenGlCaptureSession implements AutoCloseable {

    private static final int FBZ_ZONE = 4;

    private final FbzVisualCaptureMode mode;
    private final SonicConfigurationService configuration;
    private final HeadlessGameBoot boot;
    private final Map<String, Object> effectiveConfiguration;
    private GameLoop loop;
    private boolean closed;

    public HiddenGlCaptureSession(FbzVisualCaptureMode mode) {
        this(mode, EngineContext.fromLegacySingletonsForBootstrap());
    }

    HiddenGlCaptureSession(FbzVisualCaptureMode mode, EngineContext engineContext) {
        this(mode, engineContext, HeadlessGameBoot::new);
    }

    HiddenGlCaptureSession(FbzVisualCaptureMode mode, EngineContext engineContext,
                           HeadlessBootFactory bootFactory) {
        this.mode = Objects.requireNonNull(mode, "mode");
        engineContext = Objects.requireNonNull(engineContext, "engineContext");
        bootFactory = Objects.requireNonNull(bootFactory, "bootFactory");
        configuration = engineContext.configuration();
        Map<String, Object> configured;
        HeadlessGameBoot createdBoot;
        try {
            configured = configure(configuration, mode);
            createdBoot = Objects.requireNonNull(bootFactory.create(
                    mode.framebufferWidth(), mode.framebufferHeight(), engineContext), "boot");
        } catch (RuntimeException | Error initializationFailure) {
            configuration.clearSessionOverrides();
            throw initializationFailure;
        }
        effectiveConfiguration = configured;
        boot = createdBoot;
    }

    public void boot(Path rom, int zeroBasedAct) throws IOException {
        boot(rom, zeroBasedAct, 0L);
    }

    public void boot(Path rom, int zeroBasedAct, long rngSeed) throws IOException {
        if (loop != null) {
            throw new IllegalStateException("FBZ hidden-GL session is already booted");
        }
        if (zeroBasedAct < 0 || zeroBasedAct > 1) {
            throw new IllegalArgumentException("FBZ act index must be 0 or 1: " + zeroBasedAct);
        }
        var worldBeforeBoot = SessionManager.getCurrentWorldSession();
        try {
            loop = boot.boot(Objects.requireNonNull(rom, "rom"), FBZ_ZONE, zeroBasedAct, rngSeed);
        } catch (IOException | RuntimeException | Error bootFailure) {
            if (SessionManager.getCurrentWorldSession() != worldBeforeBoot) {
                SessionManager.closeGameplaySession();
            }
            throw bootFailure;
        }
        // Visual recipes begin at the first gameplay VBlank after native level
        // setup. The live load path leaves a title-card request queued; consuming
        // it here is the headless equivalent of waiting for that card to finish,
        // without spending hundreds of non-gameplay frames in the evidence tool.
        GameServices.level().consumeTitleCardRequest();
        if (loop.getCurrentGameMode() != GameMode.LEVEL) {
            throw new IllegalStateException("FBZ capture did not boot into LEVEL gameplay mode: "
                    + loop.getCurrentGameMode());
        }
        assertFramebufferConfiguration();
    }

    public void stepFrames(int count) {
        requireBooted();
        if (count < 0) {
            throw new IllegalArgumentException("Negative FBZ capture frame count: " + count);
        }
        GraphicsManager graphics = GameServices.graphics();
        UiRenderPipeline ui = graphics.getUiRenderPipeline();
        for (int i = 0; i < count; i++) {
            // Seamless fixture loads can queue another card. Evidence recipes
            // always target the ensuing gameplay frame, never TITLE_CARD mode.
            GameServices.level().consumeTitleCardRequest();
            GameMode beforeMode = loop.getCurrentGameMode();
            int beforeFrame = GameServices.level().getFrameCounter();
            if (ui != null) {
                ui.updateFade();
            }
            loop.step();
            verifyGameplayFrameAdvance(beforeMode, beforeFrame,
                    loop.getCurrentGameMode(), GameServices.level().getFrameCounter());
        }
    }

    static void verifyGameplayFrameAdvance(GameMode beforeMode, int beforeFrame,
                                           GameMode afterMode, int afterFrame) {
        if (beforeMode != GameMode.LEVEL || afterMode != GameMode.LEVEL) {
            throw new IllegalStateException("FBZ visual capture must step LEVEL gameplay: before="
                    + beforeMode + ", after=" + afterMode);
        }
        if (afterFrame != beforeFrame + 1) {
            throw new IllegalStateException("FBZ visual capture gameplay step must advance "
                    + "level_frame_counter by exactly one: before=" + beforeFrame
                    + ", after=" + afterFrame);
        }
    }

    public CapturedImages renderAndCapture() {
        requireBooted();
        FbzVisualVisibilityVerifier.verifyState(captureState().values());
        GraphicsManager graphics = GameServices.graphics();
        LevelManager level = GameServices.level();
        graphics.runPendingRenderThreadTasks();

        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithSpritePriority(GameServices.sprites());
        graphics.flush();

        UiRenderPipeline ui = graphics.getUiRenderPipeline();
        if (ui != null) {
            ui.renderFadePass();
        }
        glFinish();

        RgbaImage full = ScreenshotCapture.captureFramebuffer(
                mode.framebufferWidth(), mode.framebufferHeight());
        RgbaImage crop = mode.nativeMode()
                ? full.copy()
                : ScreenshotCapture.captureFramebufferRegion(
                        mode.nativeCropX(), mode.nativeCropY(),
                        mode.nativeCropWidth(), mode.nativeCropHeight());
        FbzVisualVisibilityVerifier.verifyGameplayPixels(crop);
        return new CapturedImages(full, crop);
    }

    FbzVisualStateProbe.Snapshot captureState() {
        return decorateSnapshot(FbzVisualStateProbe.captureRuntime());
    }

    FbzVisualStateProbe.Snapshot decorateSnapshot(FbzVisualStateProbe.Snapshot snapshot) {
        requireBooted();
        Map<String, Object> values = new LinkedHashMap<>(snapshot.values());
        values.put("game_mode", loop.getCurrentGameMode().name());
        values.put("gameplay_context_active", SessionManager.getCurrentGameplayMode() != null);
        values.put("overlays_disabled", overlaysDisabled());
        return new FbzVisualStateProbe.Snapshot(values);
    }

    private boolean overlaysDisabled() {
        return Boolean.FALSE.equals(effectiveConfiguration.get(SonicConfiguration.DEBUG_VIEW_ENABLED.name()))
                && Boolean.FALSE.equals(effectiveConfiguration.get(SonicConfiguration.DEBUG_COLLISION_VIEW_ENABLED.name()))
                && Boolean.FALSE.equals(effectiveConfiguration.get(SonicConfiguration.EDITOR_ENABLED.name()))
                && Boolean.FALSE.equals(effectiveConfiguration.get(SonicConfiguration.TEST_MODE_ENABLED.name()))
                && Boolean.FALSE.equals(effectiveConfiguration.get(SonicConfiguration.LIVE_REWIND_ENABLED.name()));
    }

    public Map<String, Object> effectiveConfiguration() {
        return effectiveConfiguration;
    }

    /** Pure configuration contract used by preboot hashing before GL/engine initialization. */
    public static Map<String, Object> configurationContract(FbzVisualCaptureMode mode) {
        Objects.requireNonNull(mode, "mode");
        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put(SonicConfiguration.DISPLAY_ASPECT.name(), aspectFor(mode.framebufferWidth()));
        resolved.put(SonicConfiguration.SCREEN_WIDTH_PIXELS.name(), mode.framebufferWidth());
        resolved.put(SonicConfiguration.SCREEN_HEIGHT_PIXELS.name(), mode.framebufferHeight());
        resolved.put(SonicConfiguration.DEBUG_VIEW_ENABLED.name(), false);
        resolved.put(SonicConfiguration.DEBUG_COLLISION_VIEW_ENABLED.name(), false);
        resolved.put(SonicConfiguration.EDITOR_ENABLED.name(), false);
        resolved.put(SonicConfiguration.TEST_MODE_ENABLED.name(), false);
        resolved.put(SonicConfiguration.LIVE_REWIND_ENABLED.name(), false);
        resolved.put(SonicConfiguration.DISPLAY_SHADER_SELECTION.name(), "OFF");
        resolved.put(SonicConfiguration.DISPLAY_COLOR_PROFILE.name(), "RAW_RGB");
        resolved.put(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED.name(), false);
        resolved.put(SonicConfiguration.LAUNCH_S3K_CROSS_GAME_SOURCE.name(), "off");
        resolved.put(SonicConfiguration.MAIN_CHARACTER_CODE.name(), "sonic");
        resolved.put(SonicConfiguration.SIDEKICK_CHARACTER_CODE.name(), "tails");
        resolved.put("mode_key", mode.key());
        return Map.copyOf(resolved);
    }

    private void assertFramebufferConfiguration() {
        SonicConfigurationService config = GameServices.configuration();
        int width = config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        int height = config.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
        if (width != mode.framebufferWidth() || height != mode.framebufferHeight()) {
            throw new IllegalStateException("FBZ effective framebuffer mismatch: expected "
                    + mode.framebufferWidth() + "x" + mode.framebufferHeight()
                    + ", got " + width + "x" + height);
        }
    }

    private static Map<String, Object> configure(SonicConfigurationService config,
                                                  FbzVisualCaptureMode mode) {
        config.resetToDefaults();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspectFor(mode.framebufferWidth()));
        config.setSessionOverride(SonicConfiguration.DISPLAY_WINDOW_AUTOSIZE, false);
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, mode.framebufferWidth());
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, mode.framebufferHeight());
        config.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DEBUG_COLLISION_VIEW_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.EDITOR_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_SHADER_SELECTION, "OFF");
        config.setSessionOverride(SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LAUNCH_S3K_CROSS_GAME_SOURCE, "off");
        config.setSessionOverride(SonicConfiguration.LAUNCH_S3K_REWIND, false);
        config.setSessionOverride(SonicConfiguration.LAUNCH_S3K_DEBUG_TOOLS, false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.resolveDisplayAspect();

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> expected : configurationContract(mode).entrySet()) {
            if ("mode_key".equals(expected.getKey())) {
                resolved.put(expected.getKey(), mode.key());
                continue;
            }
            SonicConfiguration key = SonicConfiguration.valueOf(expected.getKey());
            Object actual = config.getConfigValue(key);
            if (!Objects.equals(expected.getValue(), actual)) {
                throw new IllegalStateException("FBZ effective configuration mismatch for " + key
                        + ": expected " + expected.getValue() + ", got " + actual);
            }
            resolved.put(expected.getKey(), actual);
        }
        return Map.copyOf(resolved);
    }

    private static String aspectFor(int width) {
        return switch (width) {
            case 320 -> "NATIVE_4_3";
            case 352 -> "WIDE_16_10";
            case 400 -> "WIDE_16_9";
            case 528 -> "ULTRA_21_9";
            case 800 -> "SUPER_32_9";
            default -> throw new IllegalArgumentException("Unsupported FBZ framebuffer width: " + width);
        };
    }

    private void requireBooted() {
        if (loop == null || closed) {
            throw new IllegalStateException("FBZ hidden-GL session is not active");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (loop != null) {
                SessionManager.closeGameplaySession();
                loop = null;
            }
        } finally {
            boot.close();
            configuration.clearSessionOverrides();
        }
    }

    public record CapturedImages(RgbaImage full, RgbaImage nativeCrop) {
        public CapturedImages {
            Objects.requireNonNull(full, "full");
            Objects.requireNonNull(nativeCrop, "nativeCrop");
        }
    }

    @FunctionalInterface
    interface HeadlessBootFactory {
        HeadlessGameBoot create(int width, int height, EngineContext engineContext);
    }
}
