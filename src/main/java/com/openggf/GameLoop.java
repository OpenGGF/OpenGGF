package com.openggf;

import com.openggf.game.GameOverExit;
import com.openggf.game.ContinueScreenProvider;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.DebugColor;
import com.openggf.editor.EditorInputHandler;
import com.openggf.game.*;

import com.openggf.control.InputHandler;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.audio.AudioManager;
import com.openggf.audio.presentation.OuterFramePresentation;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugObjectArtViewer;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.mode.BootScreenModeController;
import com.openggf.game.launch.MasterTitleLaunchCoordinator;
import com.openggf.game.launch.MasterTitleExitCoordinator;
import com.openggf.game.mode.MenuScreenModeController;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.startup.DataSelectPresentationResolution;
import com.openggf.game.startup.StartupRouteResolver;
import com.openggf.game.startup.TitleActionRoute;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.level.BigRingReturnState;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import static org.lwjgl.glfw.GLFW.*;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.timer.TimerManager;
import com.openggf.graphics.FadeManager;

import com.openggf.game.DemoLamppostState;
import com.openggf.level.WaterSystem;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.debug.playback.PlaybackInputBridge;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.data.RomManager;
import com.openggf.data.Rom;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SessionSaveRequests;
import com.openggf.game.SpecialStageReturnSpawn;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.ScheduledPlaybackInputOps;
import com.openggf.game.session.SessionManager;
import com.openggf.integration.presence.PresenceFormatter;
import com.openggf.integration.presence.PresenceManager;
import com.openggf.integration.presence.RuntimePresenceSnapshotProvider;
import com.openggf.integration.presence.discord.DiscordIpcPresenceClient;
import com.openggf.integration.presence.discord.DiscordIpcTransports;
import com.openggf.game.recording.RecordingLaunchContext;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.recording.UserRecordingHudState;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.recording.UserRecordingSessionLauncher;
import com.openggf.game.recording.UserRecordingStopReason;
import com.openggf.game.recording.UserRecordingPlaybackState;
import com.openggf.game.recording.UserRecordingVerificationResult;
import com.openggf.game.recording.menu.UserRecordingMenu;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.game.session.GameplayRunRouting;
import com.openggf.testmode.TraceCameraFocusController;
import com.openggf.trace.replay.TraceSuppressedRowClosure;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Standalone game loop that can run independently of the rendering system.
 * This enables headless testing of game logic without requiring OpenGL context.
 *
 * <p>
 * The GameLoop manages:
 * <ul>
 * <li>Audio updates</li>
 * <li>Timer updates</li>
 * <li>Input processing</li>
 * <li>Game mode transitions (level ↔ special stage)</li>
 * <li>Sprite collision and movement</li>
 * <li>Camera updates</li>
 * <li>Level updates</li>
 * </ul>
 *
 * <p>
 * For headless testing, create a GameLoop with a mock InputHandler
 * and call {@link #step()} to advance one frame.
 */
public class GameLoop {
    static final int STATUS_FIRE_SHIELD_BIT = GameLoopBonusStageSession.STATUS_FIRE_SHIELD_BIT;
    static final int STATUS_LIGHTNING_SHIELD_BIT = GameLoopBonusStageSession.STATUS_LIGHTNING_SHIELD_BIT;
    static final int STATUS_BUBBLE_SHIELD_BIT = GameLoopBonusStageSession.STATUS_BUBBLE_SHIELD_BIT;
    static final int SAVED_SHIELD_MASK = GameLoopBonusStageSession.SAVED_SHIELD_MASK;
    private static final int USER_RECORDING_FAST_FORWARD_EXTRA_STEPS_PER_FRAME = 8;

    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());
    private final EngineContext engineServices;
    final SonicConfigurationService configService;
    private final AudioManager audioManager;
    private boolean gameplayAudioRateOwned;
    private final GameLoopSpecialStageInput specialStagePacingInput = new GameLoopSpecialStageInput();
    private final OuterFramePresentation outerFramePresentation;
    private final RomManager romManager;
    private final DebugOverlayManager debugOverlayManager;
    SpriteManager spriteManager;
    Camera camera;
    private TimerManager timerManager;
    LevelManager levelManager;
    GameStateManager gameState;
    FadeManager fadeManager;
    private WaterSystem waterSystem;
    private final PerformanceProfiler profiler;
    private final PlaybackDebugManager playbackDebugManager;
    private final PlaybackInputBridge playbackInputBridge = new PlaybackInputBridge();
    private final GameLoopSessionReset sessionReset;
    private final ModuleResolutionService moduleResolutionService;
    private final LiveRewindManager liveRewindManager;
    private final StartupRouteResolver startupRouteResolver = new StartupRouteResolver();
    private final BootScreenModeController bootScreenModeController = new BootScreenModeController();
    private final GameLoopDebugShortcuts debugShortcuts;
    private final GameLoopContinueCoordinator continueScreen = new GameLoopContinueCoordinator(this);

    private final MenuScreenModeController menuScreenModeController = new MenuScreenModeController();
    /** The open mod scene, if any; {@link ModSceneLauncher} opens, draws and leaves it. */
    final ModSceneActBridge modSceneActBridge;
    final com.openggf.mods.scene.host.ModSceneHost modSceneHost = new com.openggf.mods.scene.host.ModSceneHost();
    final ModSceneNavigation modSceneNavigation = new ModSceneNavigation(this);
    private final PresenceManager presenceManager;
    private final EscapeToMasterTitleController escapeToMasterTitleController;
    final GameLoopSuspension suspension = new GameLoopSuspension(this);
    private MasterTitleLaunchCoordinator masterTitleLaunchCoordinator;
    private final MasterTitleExitCoordinator masterTitleExitCoordinator;

    // The active session-owned gameplay mode. Cached fields above are sourced from this context.
    private GameplayModeContext gameplayMode;

    /** Comparison-only pacing for special-stage object passes observed per V-blank. */
    interface SpecialStageObservationPacing {
        int passCount();

        void applyPassInput(int index, SpecialStageProvider provider);

        /**
         * Executes pass {@code index}.
         *
         * <p>{@code SpecialStage_MainLoop} is two loops, not one
         * (docs/s2disasm/s2.asm:6674-6721), and one observation can own a pass
         * from each: the pre-start loop copies {@code Ctrl_1}/{@code Ctrl_2}
         * <em>before</em> its {@code WaitForVint} (s2.asm:6675-6676), so its
         * terminal pass owns no post-V-int controller sample for the recurring
         * loop's binding path to consume. A driver that can tell the two apart
         * overrides this to publish such a pass through the startup boundary
         * instead, exactly as {@code S2SpecialStageReplayHarness.stepPasses}
         * already does. The default treats every pass as a recurring-loop pass.
         */
        default void runPass(int index, SpecialStageProvider provider) {
            applyPassInput(index, provider);
            provider.update();
        }

        /**
         * Runs after pass {@code index}'s object scan, for a pass the ROM
         * finished BEFORE this observation's V-int.
         *
         * <p>{@code SS_MainLoop} waits for the V-int and only then runs
         * {@code RunObjects} (docs/s2disasm/s2.asm:6694-6721), so a pass that
         * returned during the preceding frame had its queued work carried
         * through the V-blank that opened this observation, and that V-blank
         * already ran {@code ProcessDMAQueue} (s2.asm:1769) over it. Work
         * queued by a later pass in the same observation stays pending. The
         * standalone harness models the same boundary
         * ({@code S2SpecialStageReplayHarness.stepPasses}).
         *
         * <p>Default no-op: a driver that cannot distinguish the two cases
         * leaves every pass's work pending until the next observation.
         */
        default void afterPass(int index) {
        }
    }

    void setSpecialStageObservationPacing(SpecialStageObservationPacing pacing) {
        specialStageLifecycle.setObservationPacing(pacing);
    }

    private InputHandler inputHandler;
    private final GameLoopEditorController editorMode = new GameLoopEditorController();
    private Runnable applicationExitHandler = () -> {};
    private GameMode currentGameMode = GameMode.LEVEL;
    private Supplier<MasterTitleScreen> masterTitleScreenSupplier;
    private Supplier<LegalDisclaimerScreen> legalDisclaimerSupplier;
    private Runnable legalDisclaimerExitHandler;
    private Supplier<com.openggf.game.NativeModNoticeScreen> nativeModNoticeSupplier;
    private Runnable nativeModNoticeExitHandler;
    private Consumer<com.openggf.game.dataselect.DataSelectAction> dataSelectActionHandler;
    private GameplayTeamBootstrapContext gameplayTeamBootstrapContext =
            GameplayTeamBootstrapContext.registryOnly();
    final UserRecordingSessionLauncher userRecordingSessionLauncher;
    private final UserRecordingRuntimeControls userRecordingControls;
    final HostedRunController hostedRuns;
    private Consumer<RunEndReason> hostedRunReturn = reason -> { };
    private UserRecordingMenu.PlaybackStarter userRecordingPlaybackStarter;
    private int lastAppliedUserRecordingPlaybackFrame = -1;
    private long gameplayAudioFrame;
    private boolean audioUpdatedThisStep;

    private boolean specialStageRewindBoundaryThisFrame;

    private final GameLoopTitleCardLifecycle titleCardLifecycle;
    private final LevelIterationAdmissionController levelIterationAdmission;

    private final GameLoopSpecialStageLifecycle specialStageLifecycle;

    private final GameLoopBonusStageSession bonusSession;

    private final GameLoopEndingSession endingSession;

    private final com.openggf.game.nativemenu.NativeMenuController nativeMenus =
            new com.openggf.game.nativemenu.NativeMenuController();
    private Consumer<GameplayModeContext> nativeGameplayModePublisher = this::setGameplayMode;
    private final GameLoopNativeMenus nativeMenuFlow;

    // Listener for game mode changes (used by Engine to update projection)
    GameModeChangeListener gameModeChangeListener;

    // Optional trace camera focus controller — ticked at the top of every stepInternal()
    private TraceCameraFocusController traceCameraFocusController;
    private final GameLoopRewindBoundaryReporter liveRewindBoundaryReporter;
    private GameplayModeContext livePlaybackInputPublisherContext;
    private final Runnable scheduledPlaybackInputPublisher = this::applyScheduledPlaybackInputImmediately;

    /** @deprecated use {@link com.openggf.GameModeChangeListener}. */
    @Deprecated
    public interface GameModeChangeListener extends com.openggf.GameModeChangeListener {
    }

    private volatile boolean paused = false;      // Window focus pause
    private volatile boolean userPaused = false;  // Keyboard toggle pause
    private final GameLoopConfigurationCommands configurationCommands = new GameLoopConfigurationCommands();
    private PlcLifecycleFrame activePlcLifecycleFrame;

    public GameLoop() {
        this(EngineServices.current());
    }

    public GameLoop(EngineContext engineServices) {
        this.engineServices = Objects.requireNonNull(engineServices, "engineServices");
        EngineServices.configure(this.engineServices);
        this.modSceneActBridge = new ModSceneActBridge(this, engineServices,
                (root, effective, zone, launch) -> gameplayTeamBootstrapContext.openAndLoad(
                        root, effective, engineServices, engineServices.configuration(), zone, launch.act(),
                        this::setGameplayMode, launch));
        this.configService = this.engineServices.configuration();
        this.audioManager = this.engineServices.audio();
        this.outerFramePresentation = new OuterFramePresentation(this.audioManager);
        this.romManager = this.engineServices.roms();
        this.debugOverlayManager = this.engineServices.debugOverlay();
        this.profiler = this.engineServices.profiler();
        this.playbackDebugManager = this.engineServices.playbackDebug();
        this.sessionReset = new GameLoopSessionReset(this, this.engineServices);
        this.moduleResolutionService = this.engineServices.moduleResolutionService();
        this.nativeMenuFlow = new GameLoopNativeMenus(this, nativeMenus, this.engineServices,
                this.audioManager, startupRouteResolver, () -> gameplayTeamBootstrapContext,
                this::getDataSelectProviderLazy, this::initializeDataSelectMode, this::doEnterLevelSelect,
                this::doEnterLevelSelectFromTitleScreen, this::startLevelFromTitleScreenImmediate,
                this::publishNativeGameplayMode, () -> inputHandler, () -> currentGameMode);
        this.liveRewindManager = new LiveRewindManager(
                configService, audioManager, () -> currentGameMode);
        this.liveRewindBoundaryReporter = new GameLoopRewindBoundaryReporter(
                liveRewindManager, () -> TraceSessionLauncher.active() == null);
        this.levelIterationAdmission = new LevelIterationAdmissionController(context ->
                liveRewindBoundaryReporter.initialAssemblyCompleted(context, resolveGameplayModeContext(), currentGameMode));
        this.userRecordingSessionLauncher = new UserRecordingSessionLauncher(this);
        this.userRecordingControls = new UserRecordingRuntimeControls(
                new LiveUserRecordingRuntime(configService, userRecordingSessionLauncher, playbackDebugManager,
                        () -> currentGameMode,
                        () -> TraceSessionLauncher.active() != null
                                || com.openggf.game.session.WorldSessionPolicyAccess.hasPolicies(
                                        com.openggf.game.session.SessionManager.getCurrentWorldSession())
                                || configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)
                                || debugShortcutsEnabled() && debugOverlayManager.isEnabled(DebugOverlayToggle.OBJECT_ART_VIEWER),
                        () -> { userPaused = true; updateAudioPauseState(); },
                        levelIterationAdmission::resetLastAppliedPlaybackFrame), this::returnToMasterTitle);
        this.hostedRuns = new HostedRunController(configService);
        this.userRecordingPlaybackStarter = withPlaybackAppliedFrameReset(userRecordingSessionLauncher::beginPlayback);
        this.masterTitleLaunchCoordinator = new MasterTitleLaunchCoordinator(configService);
        this.specialStageLifecycle = new GameLoopSpecialStageLifecycle(new SpecialStageHost(), audioManager);
        this.debugShortcuts = new GameLoopDebugShortcuts(
                this::getCurrentGameMode, this::getActiveSpecialStageProvider,
                specialStageLifecycle::enterResultsScreenWithDebugRings);
        this.bonusSession = new GameLoopBonusStageSession(new BonusHost(), audioManager);
        this.endingSession = new GameLoopEndingSession(new EndingHost(), audioManager);
        this.titleCardLifecycle = new GameLoopTitleCardLifecycle(new TitleCardHost());
        this.masterTitleExitCoordinator = new MasterTitleExitCoordinator(
                () -> masterTitleLaunchCoordinator,
                () -> currentGameMode == GameMode.MASTER_TITLE_SCREEN, this::hasReadyGameplayRuntime,
                () -> changeGameModeForBoundary(GameMode.LEVEL), () -> resolveFadeManager().startFadeFromBlack(null),
                () -> requestSessionSave(SaveReason.PROGRESSION_SAVE), endingSession::setTransitionPending,
                audioManager::fadeOutMusic, callback -> fadeManager.startFadeToBlack(callback));
        this.escapeToMasterTitleController = new EscapeToMasterTitleController(
                () -> resolveFadeManager().isActive(), suspension::leave, suspension::modOwnsEscapeTap);
        this.presenceManager = new PresenceManager(
                configService.getBoolean(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED),
                configService.getBoolean(SonicConfiguration.DISCORD_RICH_PRESENCE_SHOW_TIMER),
                configService.getBoolean(SonicConfiguration.DISCORD_RICH_PRESENCE_SHOW_ZONE),
                new RuntimePresenceSnapshotProvider(this, configService),
                new PresenceFormatter(),
                new DiscordIpcPresenceClient(DiscordIpcTransports.defaultFactory()),
                System::currentTimeMillis);
        refreshRuntimeBindings();
    }

    public GameLoop(InputHandler inputHandler) {
        this(EngineServices.current(), inputHandler);
    }

    public GameLoop(EngineContext engineServices, InputHandler inputHandler) {
        this(engineServices);
        this.inputHandler = inputHandler;
    }

    public void setGameplayMode(GameplayModeContext gameplayMode) {
        if (this.gameplayMode != gameplayMode) { configurationCommands.clear(); }
        this.gameplayMode = gameplayMode;
        refreshRuntimeBindings();
        installLiveRewindBoundaryReporter();
    }

    void refreshRuntimeBindings() {
        GameplayModeContext currentGameplayMode = resolveGameplayModeContext();
        liveRewindManager.bindOwner(TraceSessionLauncher.active() == null
                        ? (suspension.parked() ? suspension.parkedOwner() : currentGameplayMode) : null,
                suspension.parked(), currentGameMode);
        if (currentGameplayMode == null || !currentGameplayMode.isGameplayRuntimeReady()) {
            bindScheduledPlaybackInputPublisher(null);
            // Gameplay mode has been torn down (e.g. trace teardown returning to
            // master title). Clear cached references so resolveFadeManager()
            // falls back to the graphics-owned bootstrap manager rather than
            // a destroyed gameplay FadeManager that the UI pipeline no longer
            // ticks — which would otherwise leave fade callbacks orphaned.
            this.gameplayMode = null;
            configurationCommands.clear();
            this.spriteManager = null;
            this.camera = null;
            this.timerManager = null;
            this.levelManager = null;
            this.gameState = null;
            this.fadeManager = null;
            this.waterSystem = null;
            liveRewindBoundaryReporter.clear();
            engineServices.graphics().clearRuntimeManagedReferences();
            return;
        }
        if (this.gameplayMode != currentGameplayMode) {
            configurationCommands.clear();
        }
        this.gameplayMode = currentGameplayMode;
        this.spriteManager = currentGameplayMode.getSpriteManager();
        this.camera = currentGameplayMode.getCamera();
        this.timerManager = currentGameplayMode.getTimerManager();
        this.levelManager = currentGameplayMode.getLevelManager();
        this.gameState = currentGameplayMode.getGameStateManager();
        this.fadeManager = currentGameplayMode.getFadeManager();
        this.waterSystem = currentGameplayMode.getWaterSystem();
        bindScheduledPlaybackInputPublisher(currentGameplayMode);
        engineServices.graphics().bindRuntimeManagedReferences(this.camera, this.fadeManager);
        liveRewindBoundaryReporter.refresh(currentGameplayMode);
    }

    private void bindScheduledPlaybackInputPublisher(GameplayModeContext context) {
        if (livePlaybackInputPublisherContext != context) {
            if (livePlaybackInputPublisherContext != null) {
                ScheduledPlaybackInputOps.detach(livePlaybackInputPublisherContext,
                        scheduledPlaybackInputPublisher);
            }
            livePlaybackInputPublisherContext = context;
        }
        if (context != null) {
            ScheduledPlaybackInputOps.attach(context, scheduledPlaybackInputPublisher);
        }
    }

    GameplayModeContext resolveGameplayModeContext() {
        return suspension.parked() ? null : gameplayMode != null ? gameplayMode : SessionManager.getCurrentGameplayMode();
    }

    public void resetModuleScopedProviders() {
        nativeMenus.close();
        continueScreen.reset();
        titleCardLifecycle.clearProviderCache();
    }

    public void setInputHandler(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    public void setEditorInputHandler(EditorInputHandler editorInputHandler) {
        editorMode.setInputHandler(editorInputHandler);
    }

    public void setEditorProjectInputHandler(java.util.function.Predicate<InputHandler> handler) {
        editorMode.setProjectInputHandler(handler);
    }

    public void setEditorProjectPlaytestSupplier(java.util.function.BooleanSupplier supplier) {
        editorMode.setProjectPlaytestSupplier(supplier);
    }

    /** Retires an editor-owned playtest without invoking its completion callback. */
    void stopEditorHostedRun() {
        hostedRunReturn = ignored -> { };
        hostedRuns.end(RunEndReason.ABORTED);
    }

    void renderLiveRewindHud(PixelFontTextRenderer textRenderer, int viewportWidth) {
        liveRewindManager.renderHud(currentGameMode, textRenderer, viewportWidth);
    }

    public void renderLiveRewindHud(PixelFontTextRenderer textRenderer) {
        renderLiveRewindHud(textRenderer, engineServices.graphics().getProjectionWidth());
    }

    public float liveRewindEffectIntensity() {
        return Math.max(liveRewindManager.effectIntensity(), controlledRewindPresentation().intensity());
    }

    public float liveRewindEffectSpeed() {
        var controlled = controlledRewindPresentation();
        return controlled.intensity() > liveRewindManager.effectIntensity()
                ? controlled.speed() : liveRewindManager.effectSpeed();
    }

    private com.openggf.game.presentation.RewindPresentation controlledRewindPresentation() {
        if (currentGameMode != GameMode.LEVEL || isPaused()) {
            return com.openggf.game.presentation.RewindPresentation.NONE;
        }
        var controller = com.openggf.game.mode.ControlledFrameRuntime.controller(resolveGameplayModeContext());
        return controller == null ? com.openggf.game.presentation.RewindPresentation.NONE
                : java.util.Objects.requireNonNullElse(controller.rewindPresentation(),
                        com.openggf.game.presentation.RewindPresentation.NONE);
    }

    public void renderUserRecordingHud(PixelFontTextRenderer textRenderer) {
        if (textRenderer == null) {
            return;
        }
        UserRecordingHudState state = userRecordingControls.hudState();
        if (state == null || !state.visible()) {
            return;
        }
        DebugColor color = state.redWarning()
                ? DebugColor.RED
                : state.amberWarning() ? DebugColor.ORANGE : DebugColor.WHITE;
        textRenderer.beginBatch();
        textRenderer.drawShadowedText(state.primaryText(), 8, 8, color, 0.7f);
        if (state.secondaryText() != null && !state.secondaryText().isBlank()) {
            textRenderer.drawShadowedText(state.secondaryText(), 8, 18, color, 0.6f);
        }
        textRenderer.endBatch();
    }

    /** Draws the hosted run's screen-space overlay above the level and stock HUD. */
    public void renderHostedRunOverlay() {
        var context = resolveGameplayModeContext();
        var world=context==null?SessionManager.getCurrentWorldSession():context.getWorldSession();
        if (!WorldSessionPolicyAccess.hasPolicies(world) && !hostedRuns.isActive()) return;
        engineServices.graphics().resetForFixedFunction();
        if(world!=null)WorldSessionPolicyAccess.drawOverlay(world,new com.openggf.mods.ui.LevelOverlayCanvas(engineServices.graphics(),
                camera==null?engineServices.graphics().getProjectionWidth():camera.getWidth(),
                camera==null?Math.max(1,(int)engineServices.graphics().getScreenHeight()):camera.getHeight()));
        if (hostedRuns.isActive() && camera != null) {
            hostedRuns.drawOverlay(new com.openggf.mods.ui.LevelOverlayCanvas(
                    GameServices.graphics(), camera.getWidth(), camera.getHeight()));
        }
        // PixelCanvas queues rectangles. Complete them in this post-fade pass,
        // rather than letting the following frame draw them beneath its fade.
        engineServices.graphics().flushScreenSpace();
    }

    public boolean shouldSuppressUserRecordingSceneRendering() {
        return !isPaused() && userRecordingControls.shouldSuppressSceneRendering();
    }

    public EscapeToMasterTitleController getEscapeToMasterTitleController() {
        return escapeToMasterTitleController;
    }

    public void setEditorPlaytestToggleHandler(Runnable editorPlaytestToggleHandler) {
        editorMode.setPlaytestToggleHandler(editorPlaytestToggleHandler);
    }

    public void setDataSelectActionHandler(Consumer<com.openggf.game.dataselect.DataSelectAction> dataSelectActionHandler) {
        this.dataSelectActionHandler = dataSelectActionHandler;
    }

    public void setEditorFreshStartHandler(Runnable editorFreshStartHandler) {
        editorMode.setFreshStartHandler(editorFreshStartHandler);
    }

    public void setApplicationExitHandler(Runnable applicationExitHandler) {
        this.applicationExitHandler = applicationExitHandler != null ? applicationExitHandler : () -> {};
    }

    public void setEditorStateSyncHandler(Runnable editorStateSyncHandler) {
        editorMode.setStateSyncHandler(editorStateSyncHandler);
    }

    public void setMasterTitleScreenSupplier(Supplier<MasterTitleScreen> masterTitleScreenSupplier) {
        this.masterTitleScreenSupplier = masterTitleScreenSupplier;
    }

    void setCharacterAvailabilitySupplier(Supplier<CharacterAvailability> supplier) {
        gameplayTeamBootstrapContext = new GameplayTeamBootstrapContext(supplier);
    }

    public void setUserRecordingPlaybackStarter(UserRecordingMenu.PlaybackStarter userRecordingPlaybackStarter) {
        this.userRecordingPlaybackStarter =
                levelIterationAdmission.withAppliedPlaybackFrameReset(userRecordingPlaybackStarter);
        installUserRecordingPlaybackStarter(currentMasterTitleScreen());
    }

    private UserRecordingMenu.PlaybackStarter withPlaybackAppliedFrameReset(
            UserRecordingMenu.PlaybackStarter starter) {
        Objects.requireNonNull(starter, "starter");
        return (entry, options) -> {
            levelIterationAdmission.resetLastAppliedPlaybackFrame();
            starter.start(entry, options);
        };
    }

    public void setMasterTitleExitHandler(Consumer<String> masterTitleExitHandler) {
        masterTitleExitCoordinator.setStockExitHandler(masterTitleExitHandler);
    }

    public void setStandaloneMasterTitleExitHandler(Consumer<MasterTitleEntry.Launch> standaloneMasterTitleExitHandler) {
        masterTitleExitCoordinator.setStandaloneExitHandler(standaloneMasterTitleExitHandler);
    }

    public void setLegalDisclaimerScreenSupplier(Supplier<LegalDisclaimerScreen> legalDisclaimerSupplier) {
        this.legalDisclaimerSupplier = legalDisclaimerSupplier;
    }

    public void setLegalDisclaimerExitHandler(Runnable legalDisclaimerExitHandler) {
        this.legalDisclaimerExitHandler = legalDisclaimerExitHandler;
    }

    void setNativeModNoticeScreenSupplier(
            Supplier<com.openggf.game.NativeModNoticeScreen> supplier) {
        this.nativeModNoticeSupplier = supplier;
    }

    void setNativeModNoticeExitHandler(Runnable handler) {
        this.nativeModNoticeExitHandler = handler;
    }

    /**
     * Gets the title card provider, lazily initializing it from the current
     * GameModule.
     * 
     * @return the title card provider
     */
    private TitleCardProvider getTitleCardProviderLazy() {
        return titleCardLifecycle.provider();
    }

    public InputHandler getInputHandler() {
        return inputHandler;
    }

    public void setGameModeChangeListener(GameModeChangeListener listener) {
        this.gameModeChangeListener = listener;
    }

    public void setTraceCameraFocusController(TraceCameraFocusController controller) {
        this.traceCameraFocusController = controller;
    }

    public GameMode getCurrentGameMode() {
        return currentGameMode;
    }

    /**
     * Sets the game mode directly. Used for master title screen initialization.
     */
    public void setGameMode(GameMode mode) {
        if (mode == GameMode.MASTER_TITLE_SCREEN) retireModScene();
        if (com.openggf.game.nativemenu.NativeMenuController.handles(currentGameMode)
                && !com.openggf.game.nativemenu.NativeMenuController.handles(mode)) nativeMenus.close();
        GameMode oldMode = changeGameModeForBoundary(mode);
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, mode);
        }
    }

    /** A temporary project prompt suspends the current mode without leaving its runtime. */
    void setEditorProjectModalMode(GameMode mode, boolean suspended) {
        currentGameMode = Objects.requireNonNull(mode);
        editorMode.setModalSuspended(suspended);
    }

    void installLiveRewindBoundaryReporter() {
        liveRewindBoundaryReporter.install(resolveGameplayModeContext());
    }

    void installLiveRewindBoundaryReporter(Consumer<RewindBoundary> liveBoundaryConsumer) {
        liveRewindBoundaryReporter.install(resolveGameplayModeContext(), liveBoundaryConsumer);
    }

    GameMode changeGameModeForBoundary(GameMode nextMode) {
        GameMode oldMode = currentGameMode;
        if (oldMode != nextMode) {
            if (oldMode == GameMode.SPECIAL_STAGE) {
                specialStageLifecycle.clearEntryPresentation();
            }
            currentGameMode = nextMode;
            reportRewindModeBoundary(oldMode, nextMode);
        }
        return oldMode;
    }

    GameMode changeGameModeWithoutRewindBoundary(GameMode nextMode) {
        GameMode oldMode = currentGameMode;
        if (oldMode == GameMode.SPECIAL_STAGE && oldMode != nextMode) {
            specialStageLifecycle.clearEntryPresentation();
        }
        currentGameMode = nextMode;
        return oldMode;
    }

    private void reportRewindModeBoundary(GameMode oldMode, GameMode newMode) {
        GameplayModeContext context = resolveGameplayModeContext();
        if (context == null) {
            return;
        }
        if (oldMode == GameMode.LEVEL && newMode != GameMode.LEVEL) {
            context.markRewindBoundary(RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE);
        }
        if (oldMode == GameMode.SPECIAL_STAGE && isActiveSpecialStageProviderRewindable()) {
            deregisterSpecialStageAdapter();
            if (!specialStageRewindBoundaryThisFrame) {
                context.markRewindBoundary(RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE);
            }
        }
        liveRewindManager.bindOwner(TraceSessionLauncher.active() == null ? context : null, false, newMode);
        installLiveRewindBoundaryReporter();
        if (newMode == GameMode.SPECIAL_STAGE && isActiveSpecialStageProviderRewindable()) {
            context.markRewindBoundary(RewindBoundary.MODE_ENTER_REWINDABLE);
        }
        if (oldMode != GameMode.LEVEL && newMode == GameMode.LEVEL) {
            context.markRewindBoundary(RewindBoundary.MODE_ENTER_REWINDABLE);
        }
    }

    private void deregisterSpecialStageAdapter() {
        GameplayModeContext context = resolveGameplayModeContext();
        if (context != null) {
            context.deregisterSpecialStageAdapter();
        }
    }

    void markBonusEntryNonRewindableBoundary() {
        GameplayModeContext context = resolveGameplayModeContext();
        if (context != null) {
            context.markRewindBoundary(RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE);
        }
    }

    GameMode enterBonusTitleCardAfterLevelLoadBoundary() {
        return titleCardLifecycle.enterBonusTitleCardAfterLevelLoadBoundary();
    }

    /**
     * Pauses the game loop due to window losing focus.
     * Audio is also paused to prevent music continuing while game is frozen.
     */
    public synchronized void pause() {
        if (!paused) {
            hostedRuns.suspendInput();
            editorMode.finishPendingEdits(currentGameMode);
            paused = true;
            updateAudioPauseState();
        }
    }

    /**
     * Resumes the game loop after window regains focus.
     * Audio playback is restored only if user hasn't also paused via keyboard.
     */
    public synchronized void resume() {
        if (paused) {
            paused = false;
            updateAudioPauseState();
        }
    }

    /**
     * Toggles the user-initiated pause state (via keyboard).
     * This is separate from window focus pause so both can work independently.
     */
    public synchronized void toggleUserPause() {
        if (!userPaused) hostedRuns.suspendInput();
        userPaused = !userPaused;
        updateAudioPauseState();
    }

    /**
     * @return true if the user has paused the game via keyboard
     */
    public boolean isUserPaused() {
        return userPaused;
    }

    /** @see OuterFramePresentation#modeFor */
    public PresentationMode presentationModeForOuterFrame(boolean modalPicker, boolean frameStepRequested) {
        return outerFramePresentation.modeFor(modalPicker, isPresentationPaused(), frameStepRequested);
    }

    /** @see OuterFramePresentation#present */
    public void presentOuterFrame(boolean modalPicker, boolean frameStepRequested) {
        outerFramePresentation.present(modalPicker, isPresentationPaused(), frameStepRequested);
    }

    void setAudioPresentationProbe(OuterFramePresentation.Probe probe) { outerFramePresentation.setProbe(probe); }

    private boolean isPresentationPaused() {
        var controller = currentGameMode == GameMode.LEVEL
                ? com.openggf.game.mode.ControlledFrameRuntime.controller(resolveGameplayModeContext()) : null;
        // Configuration holds retain their ROM-backed menu soundtrack and feedback.
        // Window/user pause still silences the complete presentation as before.
        return paused || userPaused || controller != null && controller.presentationPaused();
    }

    /**
     * @return true if the game loop is currently paused (either by window or user)
     */
    public synchronized boolean isPaused() {
        return paused || userPaused || GameLoopPauseInput.configurationPaused(currentGameMode);
    }

    public boolean externalFrameOrInputOwnerActive() {
        return ExternalFrameOrInputOwnership.active(engineServices);
    }

    /**
     * Updates audio pause state based on combined pause flags.
     * Audio should be paused if either window or user pause is active.
     */
    private synchronized void updateAudioPauseState() {
        GameLoopPauseInput.updateAudio(audioManager, paused || userPaused);
    }

    /**
     * Advances the game by one frame. This is the main update loop.
     * Call this method at your target FPS (typically 60fps).
     */
    public void step() {
        try {
            TraceSessionLauncher traceSession = TraceSessionLauncher.active();
            int traceFastForwardSteps = traceSession == null
                    ? 0
                    : traceSession.beginFastForwardOuterFrame(inputHandler, isPaused());
            LevelIterationAdmissionController.runTraceObservedStep(
                    this::stepInternal, () -> currentGameMode,
                    playbackDebugManager::getCursorFrame);
            int fastForwardedFrames = 0;
            while (fastForwardedFrames < traceFastForwardSteps
                    && !isPaused()
                    && traceSession.isFastForwardPumpAllowed()) {
                LevelIterationAdmissionController.runTraceObservedStep(
                        this::stepInternal, () -> currentGameMode,
                        playbackDebugManager::getCursorFrame);
                fastForwardedFrames++;
            }
            int pumpedFrames = 0;
            while (!isPaused()
                    && userRecordingControls.shouldPumpFastForward()
                    && pumpedFrames < USER_RECORDING_FAST_FORWARD_EXTRA_STEPS_PER_FRAME) {
                LevelIterationAdmissionController.runTraceObservedStep(
                        this::stepInternal, () -> currentGameMode,
                        playbackDebugManager::getCursorFrame);
                pumpedFrames++;
            }
        } finally {
            runAfterStepMasterTitleLaunchCallbackIfPresent();
            presenceManager.tick();
        }
    }

    /** Interactive 60/50 Hz presentation entry; canonical {@link #step()} remains one tick. */
    public void stepPresentationFrame() {
        var policyBefore = GameLoopWorldPolicyPresentation.capture(this, titleCardLifecycle.cachedProvider());
        gameplayAudioRateOwned = GameLoopGameplayPacing.step(this, resolveGameplayModeContext(),
                () -> levelManager == null ? null : levelManager.getCurrentLevel(),
                inputHandler, audioManager, this::canUseGameplayPacing, this::canUseGameplayPacingOutsideRewind,
                liveRewindManager::isRewindingOrReleasing, pacing -> {
                    try {
                        GameLoopZeroStepPresentation.run(currentGameMode, inputHandler, configService,
                                playbackDebugManager, userPaused, escapeToMasterTitleController,
                                this::handleConfigurationCommand, userRecordingControls::handlePlaybackTakeoverRequest,
                                paused -> { userPaused = paused; updateAudioPauseState(); },
                                () -> userRecordingControls.updateLevelControlInput(inputHandler),
                                this::canUseGameplayPacing, pacing);
                    } finally {
                        runAfterStepMasterTitleLaunchCallbackIfPresent(); presenceManager.tick();
                    }
                }, specialStagePacingInput, gameplayAudioRateOwned);
        GameLoopWorldPolicyPresentation.publish(this, policyBefore, titleCardLifecycle.cachedProvider(), inputHandler, engineServices,
                liveRewindManager::isRewindingOrReleasing, this::canUseGameplayPacing,
                () -> resolveFadeManager().isActive());
    }

    private void releaseGameplayAudioRate() {
        if (gameplayAudioRateOwned) {
            audioManager.setForwardPlaybackRate(1.0);
            gameplayAudioRateOwned = false;
        }
    }

    private boolean canUseGameplayPacing() {
        return canUseGameplayPacingOutsideRewind()
                && GameLoopPacingInterruption.allows(liveRewindManager, configService, inputHandler);
    }

    private boolean canUseGameplayPacingOutsideRewind() {
        return GameLoopPacingActivity.allows(currentGameMode,
                currentGameMode == GameMode.SPECIAL_STAGE ? getActiveSpecialStageProvider() : null,
                bonusSession.provider(), titleCardLifecycle.cachedProvider(), camera, levelManager, specialStageRewindBoundaryThisFrame)
                && !isPaused() && !isNonRewindableTransitionPending()
                && !ExternalFrameOrInputOwnership.active(engineServices)
                && (inputHandler == null || !inputHandler.hasLogicalOverride())
                && !userRecordingControls.shouldPumpFastForward();
    }

    public void closePresence() {
        releaseGameplayAudioRate();
        presenceManager.close();
    }

    /**
     * True while the level is mid a special-stage/bonus-stage/ending transition
     * or a pending zone/act transition -- the exact same composite condition
     * the gameplay-tick freeze block below computes (special/bonus/ending/
     * zone-act freeze). {@code currentGameMode} stays {@code GameMode.LEVEL}
     * throughout this whole window (the fade only flips the mode once its
     * completion callback runs), so this is exposed for
     * {@link LiveRewindManager}'s mode-based "not applicable" gate to consult
     * -- a sub-state {@code GameMode} alone cannot express. See
     * ssentry-rewind-report.md.
     * <p>
     * <strong>This predicate freezes ordinary gameplay ticks</strong> (via the
     * freeze block below) whenever it is true -- it must stay scoped to
     * the scene/act handoff and the four native flags that legitimately warrant that freeze (this was
     * pre-existing behavior). Do NOT fold {@link FadeManager#hasPendingCompletion()}
     * in here: unlike a special/bonus/ending/zone-act transition, an ordinary
     * callback-bearing fade (e.g. death respawn, act-complete) does not freeze
     * ROM gameplay -- objects keep ticking underneath a cosmetic fade overlay.
     * See {@link #isRewindBlocked()} for the rewind-only superset that adds the
     * fade term.
     */
    private boolean isNonRewindableTransitionPending() {
        // Called unconditionally near the top of stepInternal(), before any
        // currentGameMode-specific dispatch -- levelManager can be null in
        // non-gameplay modes (e.g. MASTER_TITLE_SCREEN with no active session).
        return modSceneActBridge.isTransitioning() || specialStageLifecycle.transitionPending()
                || bonusSession.isTransitionPending()
                || endingSession.isTransitionPending()
                || (levelManager != null && levelManager.isLevelInactiveForTransition());
    }

    /**
     * Services a queued Special Stage request when the run frame driver owns
     * the physical row and suppresses the native level body. The request is
     * still an engine-owned transition; only the ordinary gameplay work is
     * suppressed for the shared gap.
     */
    boolean consumeSpecialStageRequestDuringSuppressedRunRow() {
        if (currentGameMode != GameMode.LEVEL || levelManager == null
                || !levelManager.consumeSpecialStageRequest()) {
            return false;
        }
        enterSpecialStage();
        return true;
    }

    /**
     * Presents a level restart's mandatory title card on a row whose ordinary
     * level body is suppressed.
     *
     * <p>The card is not gameplay: the ROM's {@code Level_TtlCardLoop} runs
     * {@code ExecuteObjects}/{@code BuildSprites} over freshly cleared object
     * RAM holding only the card's own elements, plus {@code RunPLC}
     * (docs/s1disasm/sonic.asm:2814-2842). A restart therefore reaches the card
     * on the same rows a run's shared transition gap suppresses the level body
     * on, exactly as the special-stage results exit already does from its own
     * mode's update.
     */
    boolean presentPendingTitleCardDuringSuppressedRunRow() {
        if (currentGameMode != GameMode.LEVEL || levelManager == null
                || !levelManager.consumeTitleCardRequest()) {
            return false;
        }
        GameLoopPlcLifecycle.enterPendingTitleCard(() -> levelManager, resolveGameplayModeContext(), activePlcLifecycleFrame, this::enterTitleCard, this::getTitleCardProvider);
        return true;
    }

    /**
     * Starts a level restart's fade on a row whose ordinary level body is
     * suppressed.
     *
     * <p>{@code Sonic_ResetLevel}'s sixtieth decrement writes {@code f_restart}
     * from inside the object pass of the first row a run's transition gap owns
     * (docs/s1disasm/_incObj/01 Sonic.asm:2062-2073); the level main loop's own
     * test then falls into {@code GM_Level} without iterating again
     * (docs/s1disasm/sonic.asm:3016-3018). The engine's respawn consumer runs a
     * row later than that test, which lands on a gap row whose body is already
     * suppressed, so the restart is started here for the same reason the
     * results exit and the restart's title card are.
     */
    boolean startPendingRespawnDuringSuppressedRunRow() {
        if (currentGameMode != GameMode.LEVEL || levelManager == null
                || fadeManager == null || fadeManager.isActive()
                || !levelManager.consumeRespawnRequest()) {
            return false;
        }
        startRespawnFade();
        return true;
    }

    /**
     * Set when {@link GameLoopTitleCardLifecycle#exitTitleCard()} releases into {@link GameMode#LEVEL}
     * during the current iteration, and cleared at the top of every iteration.
     * Read only by {@link #runGapRowContinuesSourceLevelMainLoop()}.
     */
    private boolean titleCardReleasedIntoLevelThisIteration;

    /**
     * True while a run's shared transition-gap row is still the source level's
     * own main-loop iteration, so the ordinary level body must run on it.
     *
     * <p>See {@link TraceSessionLauncher#runGapRowContinuesSourceLevelMainLoop}
     * for the recorder and ROM basis. The engine's form of the ROM writes that
     * end a level loop is a pending level-exit request
     * ({@link com.openggf.level.LevelTransitionCoordinator#hasPendingLevelExit()})
     * or an in-flight blocking transition ({@link #isRewindBlocked()}, which
     * covers a native blocking fade's pending completion and the
     * bonus/ending/zone-act flags); either means the loop has already ended.
     *
     * <p>A title-card release into LEVEL on THIS iteration is a third such
     * write, and it is the one a level-to-level gap actually lands on. Once the
     * destination level has loaded and its card has been released, the source
     * level's main loop is provably over: the release IS
     * {@code Level_StartGame}, which the ROM reaches only after the title-card
     * leave loop's last iteration (docs/s2disasm/s2.asm:5060-5066, :5081-5082).
     * The destination's first object pass then comes from
     * {@code Level_MainLoop}, which runs {@code PauseGame} and
     * {@code WaitForVint} BEFORE its {@code jsr (RunObjects).l}
     * (docs/s2disasm/s2.asm:5088-5095) -- so the release row itself dispatches
     * no object pass at all, and the first one belongs to the destination's
     * next V-blank, i.e. its recorded row 0. S1 orders the same two routines the
     * same way ({@code Level_StartGame} at docs/s1disasm/sonic.asm:2990-2991,
     * then {@code Level_MainLoop}'s {@code WaitForVBlank} at :2998-3001 ahead of
     * {@code jsr (ExecuteObjects).l} at :3006), and so does S3K
     * ({@code bclr #7,(Game_mode).w} at docs/skdisasm/sonic3k.asm:7914, then
     * {@code Wait_VSync} at :7920 ahead of {@code Process_Sprites} at :7926).
     *
     * <p>Without this term the latch survived every locked title-card row (a
     * title-card iteration never reaches the gap-body test) and was consumed by
     * the release row instead, handing the freshly loaded destination one extra
     * pre-row-0 object pass. That put its CPU sidekick exactly one
     * {@code Tails_acceleration} step ahead for the rest of the segment
     * (docs/s2disasm/s2.asm:38907).
     */
    private boolean runGapRowContinuesSourceLevelMainLoop() {
        boolean levelExitWritten = levelManager == null
                || levelManager.hasPendingLevelExit()
                || titleCardReleasedIntoLevelThisIteration
                || isRewindBlocked();
        return TraceSessionLauncher.runGapRowContinuesSourceLevelMainLoop(
                currentGameMode, levelExitWritten);
    }

    /**
     * True whenever rewind engagement must be rejected: either
     * {@link #isNonRewindableTransitionPending()} (the four transition flags,
     * which ALSO freeze gameplay), or a fade is in flight with a completion
     * callback that has not yet run ({@link FadeManager#hasPendingCompletion()}).
     * <p>
     * The fade term is intentionally NOT folded into
     * {@link #isNonRewindableTransitionPending()} itself, because that method
     * also drives the gameplay-tick freeze block -- ROM gameplay keeps ticking
     * during an ordinary callback-bearing fade (death respawn, act-complete,
     * the S1 giant-ring special-stage entry, etc.), it is only REWIND
     * engagement that must be rejected there: {@link FadeManager#restore()}
     * deliberately does not restore the transient {@code onFadeComplete}
     * callback, so a rewind restore landing inside such a fade's window
     * orphans whatever the callback was going to do (dropping or freezing the
     * transition), independent of whether gameplay itself is frozen. This
     * composite is consumed ONLY by the two rewind-engagement call sites
     * below ({@link LiveRewindManager}/{@link TraceSessionLauncher}), never by
     * the gameplay freeze block. See ssentry-rewind-report.md.
     */
    private boolean isRewindBlocked() {
        // fadeManager can be null in non-gameplay modes (e.g.
        // MASTER_TITLE_SCREEN with no active session).
        return GameLoopPauseInput.configurationPaused(currentGameMode)
                // Input movies are append-only and cannot represent a world rollback.
                || userRecordingSessionLauncher.hasActiveRecordingSession()
                || isNonRewindableTransitionPending()
                || (fadeManager != null && fadeManager.hasPendingCompletion())
                || (levelManager != null && levelManager.hasPendingFreshLevelTransitionBoundary());
    }

    /**
     * True while the current bonus stage supports held rewind. The provider's
     * capability includes captured ordinary interiors and native runtime owners.
     */
    private boolean isBonusStageRewindable() {
        return currentGameMode == GameMode.BONUS_STAGE
                && bonusSession.provider() != null
                && bonusSession.provider().supportsRewind();
    }

    private boolean isSpecialStageRewindable() {
        return currentGameMode == GameMode.SPECIAL_STAGE
                && !specialStageRewindBoundaryThisFrame
                && isActiveSpecialStageProviderRewindable();
    }

    private boolean isActiveSpecialStageProviderRewindable() {
        return specialStageLifecycle.retainedProviderRewindable();
    }

    private void severSpecialStageRewindForLiveOnlyShortcut() {
        if (currentGameMode != GameMode.SPECIAL_STAGE || specialStageRewindBoundaryThisFrame) {
            return;
        }
        specialStageRewindBoundaryThisFrame = true;
        GameplayModeContext context = resolveGameplayModeContext();
        if (context != null) {
            context.markRewindBoundary(RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE);
        }
    }

    private void prepareSpecialStageRewindFrame() {
        specialStageRewindBoundaryThisFrame = false;
        if (currentGameMode == GameMode.SPECIAL_STAGE) {
            detectSpecialStageLiveOnlyShortcutBoundary();
        }
    }

    private void finishMasterTitleFrame() {
        if (pendingTitleEntryOpen != null && !resolveFadeManager().isActive()) {
            Runnable open = pendingTitleEntryOpen;
            pendingTitleEntryOpen = null;
            fadeOutTo(open);
        }
    }

    private void requireInputHandler() {
        if (inputHandler == null) {
            throw new IllegalStateException("InputHandler must be set before calling step()");
        }
    }

    private void stepInternal() {
        nativeMenus.beginFrame();
        if (suspension.preparing()) {
            inputHandler.update();
            return;
        }
        if (editorMode.consumeSuspendedFrame(inputHandler, () -> audioUpdatedThisStep = false)) {
            return;
        }
        if (sessionReset.prepareOrConsume(inputHandler)) return;
        continueScreen.beginIteration();
        levelIterationAdmission.beginIteration();
        refreshRuntimeBindings();
        GameplayModeContext lifecycleContext = resolveGameplayModeContext();
        if (currentGameMode == GameMode.LEVEL
                && com.openggf.game.mode.ControlledFrameRuntime.controller(lifecycleContext) != null) {
            requireInputHandler();
            audioUpdatedThisStep = false;
            if (RewindReleaseRetryCoordinator.consumePendingFrame(liveRewindManager, inputHandler)) return;
            ControlledLevelIteration.step(lifecycleContext, inputHandler, escapeToMasterTitleController,
                    userRecordingControls, liveRewindManager, playbackDebugManager,
                    new ControlledLevelIteration.HostPause(configService.getInt(SonicConfiguration.PAUSE_KEY),
                            configService.getInt(SonicConfiguration.FRAME_STEP_KEY), this::isPaused,
                            () -> handlePlaybackTakeoverBeforePlaybackInputBridge(inputHandler), this::toggleUserPause),
                    new ControlledLevelIteration.ConfigurationCommands(
                            () -> currentGameMode == GameMode.LEVEL && resolveGameplayModeContext() == lifecycleContext,
                            () -> configurationCommands.holdsTransition(currentGameMode, () -> resolveFadeManager().isActive()),
                            this::handleConfigurationCommand),
                    new ControlledLevelIteration.SceneActAdmission(
                            () -> modSceneActBridge.consumeExitOrHold(inputHandler),
                            () -> updateNonGameplayAudio(false)),
                    this::syncPlaybackInputBridge, this::beginGameplayAudioFrameForTick,
                    () -> advanceGameplayAudioFrameForTick(false),
                    () -> { audioManager.resume(); if (!modSceneNavigation.reopenStartupScene()) initializeTitleScreenMode(); });
            return;
        }
        if (lifecycleContext == null || !lifecycleContext.isGameplayRuntimeReady()) {
            stepInternalBody();
            completeNativeMenuFrame();
            return;
        }
        Runnable productionIteration = () -> {
            GameLoopPlcLifecycle.runLogicalIteration(lifecycleContext.plcFrameLifecycle(),
                    lifecycleContext, continueScreen::updateFade, frame -> {
                        activePlcLifecycleFrame = frame;
                        try {
                            stepInternalBody();
                            return null;
                        } finally {
                            activePlcLifecycleFrame = null;
                        }
                    });
            // The outgoing token has finished; keep setup inside trace failure
            // containment and before another tick or presentation sees its world.
            completeNativeMenuFrame();
        };
        TraceSessionLauncher.runProductionIterationIfActive(
                productionIteration, this::advanceTraceRunPhysicalRow);
    }

    private void advanceTraceRunPhysicalRow() {
        levelIterationAdmission.advanceTraceRunPhysicalRow(
                playbackDebugManager, userRecordingControls,
                TraceSessionLauncher.active());
    }

    private void stepInternalBody() {
        requireInputHandler();
        // VInt_Done bumps V_int_run_count on every V-int (sonic3k.asm:558-559); the
        // carrier counts the iterations no level object clock is bound to service.
        com.openggf.game.session.EngineTiming.vIntRunCounter(engineServices).serviceRepresentedVBlank();
        LevelIterationAdmissionController.refreshTraceInputSnapshot(inputHandler);
        audioUpdatedThisStep = false;
        refreshRuntimeBindings();
        prepareSpecialStageRewindFrame();
        PaletteOwnershipRegistry paletteRegistry = GameServices.paletteOwnershipRegistryOrNull();
        if (paletteRegistry != null) {
            paletteRegistry.beginFrame();
        }
        com.openggf.game.TitleInputOwnership.routePlayback(currentGameMode,
                () -> playbackDebugManager.handleInput(inputHandler));
        playbackDebugManager.setObservedMode(currentGameMode);

        if (RewindReleaseRetryCoordinator.consumePendingFrame(liveRewindManager, inputHandler)) return;
        boolean rewindBlocked = isRewindBlocked();
        if (currentGameMode == GameMode.LEVEL
                && TraceSessionLauncher.active() != null
                && TraceSessionLauncher.active().handleRealtimeRewindInput(
                        rewindBlocked, inputHandler)) {
            inputHandler.update();
            return;
        }
        if ((currentGameMode == GameMode.LEVEL || isBonusStageRewindable() || isSpecialStageRewindable())
                && TraceSessionLauncher.active() == null
                && runPolicy().liveRewind()
                && liveRewindManager.handleRealtimeRewindInput(
                        currentGameMode, rewindBlocked, inputHandler)) {
            inputHandler.update();
            return;
        }

        boolean playbackTakeoverConsumedPausePress =
                handlePlaybackTakeoverBeforePlaybackInputBridge(inputHandler);

        if (currentGameMode == GameMode.LEGAL_DISCLAIMER) {
            bootScreenModeController.updateLegalDisclaimer(
                    legalDisclaimerSupplier != null ? legalDisclaimerSupplier.get() : null,
                    inputHandler,
                    () -> {
                        if (legalDisclaimerExitHandler != null) {
                            legalDisclaimerExitHandler.run();
                            legalDisclaimerExitHandler = null;
                        }
                    });
            return;
        }

        if (currentGameMode == GameMode.NATIVE_MOD_NOTICE) {
            bootScreenModeController.updateNativeModNotice(
                    nativeModNoticeSupplier != null ? nativeModNoticeSupplier.get() : null,
                    inputHandler,
                    () -> {
                        if (nativeModNoticeExitHandler != null) {
                            nativeModNoticeExitHandler.run();
                            nativeModNoticeExitHandler = null;
                        }
                    });
            return;
        }

        if (editorMode.consumeProjectInput(currentGameMode, inputHandler)) {
            return;
        }

        if (currentGameMode == GameMode.MASTER_TITLE_SCREEN) {
            // Escape belongs to the title: Back, then its Quit dialog. Clear any hold
            // carried over from the game that just returned here.
            escapeToMasterTitleController.reset();
            MasterTitleScreen masterScreen = currentMasterTitleScreen();
            bootScreenModeController.updateMasterTitle(
                    masterScreen,
                    inputHandler,
                    this::exitMasterTitleScreen);
            if (!resolveFadeManager().isActive()) {
                com.openggf.game.TitleInputOwnership.routeQuit(masterScreen, () -> fadeOutTo(applicationExitHandler));
            }
            finishMasterTitleFrame();
            modSceneNavigation.onMasterTitleFrame(masterScreen);
            return;
        }

        if (editorMode.consumeEditorFrame(currentGameMode, inputHandler, this::isPaused,
                () -> runPolicy().editorEntry(), () -> configService.getBoolean(SonicConfiguration.EDITOR_ENABLED))) {
            return;
        }

        TraceSessionLauncher visualTraceSession = TraceSessionLauncher.active();
        if (LevelIterationAdmissionController.shouldVisualTraceOwnEscape(
                currentGameMode, visualTraceSession,
                inputHandler.isKeyPressed(GLFW_KEY_ESCAPE))) {
            visualTraceSession.requestEarlyExit();
            inputHandler.update();
            return;
        }

        if (configurationCommands.holdsTransition(currentGameMode, () -> resolveFadeManager().isActive())) {
            inputHandler.update();
            return;
        }
        boolean overlayOwnsPause = GameLoopPauseInput.handleOverlay(currentGameMode, inputHandler) || hostedRuns.ownsStart(currentGameMode);
        var overlay = GameLoopPauseInput.overlay(currentGameMode);
        var titleInputOwner = currentGameMode == GameMode.TITLE_SCREEN ? getTitleScreenProviderLazy() : null;
        boolean titleOwnsEscape = titleInputOwner != null && titleInputOwner.ownsEscapeInput();
        if (overlay == null && titleOwnsEscape) {
            overlay = GameServices.module().getGameService(LevelInputOverlay.class);
        }
        if (overlayOwnsPause || titleOwnsEscape) escapeToMasterTitleController.reset();
        if (overlay != null && handleConfigurationCommand(overlay)) {
            inputHandler.update();
            return;
        }
        if (!overlayOwnsPause && !titleOwnsEscape) escapeToMasterTitleController.update(currentGameMode, inputHandler);
        if (currentGameMode == GameMode.LEVEL && !overlayOwnsPause) {
            userRecordingControls.updateLevelControlInput(inputHandler);
        }

        boolean nextUserPaused = GameLoopPauseInput.nextUserPaused(currentGameMode, inputHandler,
                configService, userPaused, overlayOwnsPause, playbackTakeoverConsumedPausePress,
                playbackDebugManager, userRecordingControls::handlePlaybackTakeoverRequest);
        if (nextUserPaused != userPaused) {
            userPaused = nextUserPaused;
            updateAudioPauseState();
        }

        boolean doFrameStep = !GameLoopPauseInput.configurationPaused(currentGameMode)
                && GameLoopPauseInput.frameStep(inputHandler, configService, isPaused());
        boolean holdPausedFrame = GameLoopPauseInput.holdPausedFrame(this::isPaused, doFrameStep, hostedRuns);
        if (holdPausedFrame) {
            inputHandler.update();
            return;
        }

        // A fade completion may have installed a scene-launched LEVEL in this
        // native outer row. Its bridge hold precedes setup and all world owners.
        if (currentGameMode == GameMode.LEVEL && modSceneActBridge.consumeExitOrHold(inputHandler)) {
            updateNonGameplayAudio(doFrameStep);
            return;
        }

        titleCardReleasedIntoLevelThisIteration = false;
        if (!prepareAdmittedIteration(doFrameStep, overlayOwnsPause)) {
            return;
        }

        if ((currentGameMode == GameMode.LEVEL
                || currentGameMode == GameMode.BONUS_STAGE)
                && TraceSessionLauncher.admitsRunLogicalGameplayInput(currentGameMode)) {
            playbackDebugManager.shouldSkipCurrentGameplayTick();
        }

        boolean deferAudioUntilGameplayTick =
                currentGameMode == GameMode.LEVEL
                        || currentGameMode == GameMode.BONUS_STAGE
                        || currentGameMode == GameMode.TITLE_CARD;
        if (!deferAudioUntilGameplayTick) {
            updateNonGameplayAudio(doFrameStep);
        }

        // Master-title Extras can open a MOD_SCENE before the first gameplay
        // session. Its menu has no world timers until a run actually loads.
        if (timerManager != null && !playbackDebugManager.isCurrentGameplayTickSuppressed()) {
            profiler.beginSection("timers");
            timerManager.update();
            profiler.endSection("timers");
        }

        profiler.beginSection("input");
        boolean debugShortcutsEnabled = debugShortcutsEnabled();
        GameLoopDebugShortcuts.updateInput(hostedRuns,
                debugOverlayManager, () -> inputHandler,
                debugShortcutsEnabled, configService);

        if (isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_KEY))) {
            if (currentGameMode == GameMode.SPECIAL_STAGE) {
                severSpecialStageRewindForLiveOnlyShortcut();
            }
            handleSpecialStageDebugKey();
        }

        BonusStageType debugBonusType = debugShortcutsEnabled
                ? resolveBonusStageDebugShortcut(inputHandler)
                : BonusStageType.NONE;
        if (debugBonusType != BonusStageType.NONE) {
            bonusSession.handleBonusStageDebugKey(debugBonusType);
        }

        if (currentGameMode == GameMode.SPECIAL_STAGE) {
            specialStageLifecycle.updateSpecialStageMode();
        } else if (currentGameMode == GameMode.SPECIAL_STAGE_RESULTS) {
            specialStageLifecycle.updateSpecialStageResultsMode();
        } else if (currentGameMode == GameMode.TITLE_CARD) {
            throw new IllegalStateException("title-card admission must own the iteration");
        } else if (currentGameMode == GameMode.TITLE_SCREEN) {
            GameLoopPlcLifecycle.runPhase(activePlcLifecycleFrame,
                    PlcLifecyclePhase.TITLE_SCREEN, this::updateTitleScreenMode);
            profiler.endSection("input");
            return;
        } else if (com.openggf.game.nativemenu.NativeMenuController.handles(currentGameMode)) {
            GameLoopPlcLifecycle.runNativeScreen(activePlcLifecycleFrame,
                    currentGameMode, this::updateNativeMenuMode);
            profiler.endSection("input");
            return;
        } else if (currentGameMode == GameMode.LEVEL_SELECT) {
            GameLoopPlcLifecycle.runPhase(activePlcLifecycleFrame,
                    PlcLifecyclePhase.LEVEL_SELECT, this::updateLevelSelectMode);
            profiler.endSection("input");
            return;
        } else if (currentGameMode == GameMode.CONTINUE_SCREEN) {
            GameLoopPlcLifecycle.runPhase(activePlcLifecycleFrame,
                    PlcLifecyclePhase.CONTINUE_SCREEN, () -> continueScreen.update(activePlcLifecycleFrame));
            profiler.endSection("input");
            return;
        } else if (currentGameMode == GameMode.DATA_SELECT) {
            updateDataSelectMode();
            profiler.endSection("input");
            return;
        } else if (currentGameMode == GameMode.MOD_SCENE) {
            menuScreenModeController.updateModScene(modSceneActBridge::updateScene, inputHandler);
            startPendingHostedRunLaunch();
            profiler.endSection("input");
            return;
        } else if (currentGameMode == GameMode.CREDITS_TEXT
                || currentGameMode == GameMode.CREDITS_DEMO
                || currentGameMode == GameMode.TRY_AGAIN_END
                || currentGameMode == GameMode.ENDING_CUTSCENE) {
            endingSession.runFrame(activePlcLifecycleFrame);
            inputHandler.update();
            profiler.endSection("input");
            return;
        }

        profiler.endSection("input");

        // Evaluated before the suppression test, never inside it: the call
        // consumes the gap's first row whatever mode that row is in, and a gap
        // opened from a Special Stage results screen spends its first rows
        // outside LEVEL.
        boolean gapRowContinuesLevelMainLoop =
                runGapRowContinuesSourceLevelMainLoop();
        if (TraceSessionLauncher.suppressesRunNativeLevelBody(currentGameMode)
                && !gapRowContinuesLevelMainLoop) {
            // A shared transition gap's rows are owned by the game's blocking
            // level-exit/entry routine once the level's own main loop has ended
            // (see runGapRowContinuesSourceLevelMainLoop for the row that still
            // belongs to the loop). That routine is not gameplay, but it does
            // make mode transitions of its own -- the S1 results path raises the
            // Special Stage request from a fade callback, a restart's load
            // raises its locked title card, and a death's own f_restart starts
            // the reload. Service those here or the run driver replays gap input
            // forever against a scene that never moves.
            consumeSpecialStageRequestDuringSuppressedRunRow();
            presentPendingTitleCardDuringSuppressedRunRow();
            startPendingRespawnDuringSuppressedRunRow();
            inputHandler.update();
            return;
        }

        if (currentGameMode == GameMode.LEVEL) {
            if (!updateLevelMode(doFrameStep)) {
                return;
            }
            if (GameLoopTitleEntryFade.beginAfterLevelPass(this, audioManager::stopMusic)) userRecordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
        } else if (currentGameMode == GameMode.BONUS_STAGE) {
            updateBonusStageMode(doFrameStep);
        }

        if (traceCameraFocusController != null) {
            traceCameraFocusController.postUpdate();
        }

        inputHandler.update();
    }

    /** True while TITLE_SCREEN still shows the level's retained picture under the title's entry fade. */
    public boolean isTitleEntryShowingLevel() { return GameLoopTitleEntryFade.showsLevel(currentGameMode); }

    public boolean ownsGameplayFadeLifecycle() {
        GameplayModeContext context = resolveGameplayModeContext();
        return context != null && context.isGameplayRuntimeReady();
    }

    /** Configuration commands use the production fade and assembly owners. */
    private boolean handleConfigurationCommand(LevelInputOverlay overlay) {
        return configurationCommands.handle(overlay, this::resolveFadeManager,
                () -> { userPaused = false; updateAudioPauseState(); },
                () -> fadeOutTo(this::returnToMasterTitle),
                () -> {
                    audioManager.fadeOutMusic();
                    GameLoopPlcLifecycle.startToBlack(resolveGameplayModeContext(), resolveFadeManager(), () -> {
                        levelManager.restartCurrentLevelFromConfiguration();
                        configurationCommands.releaseTransition();
                        GameLoopPlcLifecycle.startFromBlack(resolveGameplayModeContext(), resolveFadeManager(), null);
                    });
                });
    }

    private boolean prepareAdmittedIteration(boolean doFrameStep, boolean overlayOwnsPause) {
        syncPlaybackInputBridge();
        LevelFrameResult admission = levelIterationAdmission.admit(
                currentGameMode, () -> updateTitleCardMode(doFrameStep),
                titleCardLifecycle::releaseResult, levelManager, gameplayMode,
                !overlayOwnsPause && (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.START))
                        || playbackDebugManager.isCurrentForcedStartPress()
                        || (RecordedInputOwnership.active(inputHandler)
                            && inputHandler.logical().player1().startPressed())),
                userRecordingControls,
                request -> routeHostedRunSeamlessTransitionBeforeApply(
                        request, doFrameStep),
                this::startPendingInLevelTitleCard,
                () -> LevelIterationAdmissionController
                        .prepareTraceRunAdmissionAndHardwareTiming(
                                currentGameMode, this::syncPlaybackInputBridge),
                LevelIterationAdmissionController
                        ::deactivateTraceHardwareTimingForAdmission);
        if (admission == LevelFrameResult.PAUSED) {
            hostedRuns.suspendInput();
            LevelFrameStep.serviceVBlankOnly(LevelFrameContext.from(gameplayMode),
                    activePlcLifecycleFrame, PlcLifecyclePhase.NORMAL_PAUSE);
            inputHandler.update();
            return false;
        }
        if (admission == LevelFrameResult.SETUP_ONLY) {
            // Locked title/load iterations skip the ordinary frame tail, but
            // still consume host input edges (including the unpause press).
            inputHandler.update();
            return false;
        }
        if (levelIterationAdmission.completePendingBoundary(
                doFrameStep, this::updateNonGameplayAudio,
                () -> levelIterationAdmission.finishPlaybackBoundary(
                        true, playbackDebugManager, userRecordingControls),
                this::resolveGameplayModeContext)) {
            LevelFrameStep.serviceVBlankOnly(LevelFrameContext.from(gameplayMode),
                    activePlcLifecycleFrame, PlcLifecyclePhase.LAG);
            inputHandler.update();
            return false;
        }
        if (traceCameraFocusController != null) {
            traceCameraFocusController.tick(inputHandler);
        }
        // The outer input pass owns recording controls, including paused iterations.
        return true;
    }

    /** Commits the trace-owned terminal boundary through the production results request. */
    public boolean commitDeferredTraceRunModeBoundaryIfReady() {
        return TraceSessionLauncher.commitDeferredRunModeBoundary(
                currentGameMode, getActiveSpecialStageProvider(), specialStageLifecycle::enterResultsScreen);
    }

    /**
     * TITLE_CARD per-frame update.
     *
     * @return {@code true} if the title card released control this frame,
     *         {@code false} if the card is still in its locked phase
     */
    private boolean updateTitleCardMode(boolean doFrameStep) {
        return titleCardLifecycle.update(
                doFrameStep, getTitleCardProviderLazy(), activePlcLifecycleFrame,
                gameplayMode, levelManager, spriteManager, camera, inputHandler,
                titleCardLifecycle.destination(),
                this::beginGameplayAudioFrameForTick,
                () -> advanceGameplayAudioFrameForTick(doFrameStep),
                phase -> GameLoopPlcLifecycle.prepare(activePlcLifecycleFrame, phase),
                (name, step) -> {
                    profiler.beginSection(name);
                    step.run();
                    profiler.endSection(name);
                });
    }

    /**
     * TITLE_SCREEN per-frame update: delegates provider sequencing to the
     * menu-screen controller and exits once it reports an exit request and no
     * fade is in progress. The controller owns {@code inputHandler.update()}.
     */
    private void updateTitleScreenMode() {
        if (GameLoopTitleEntryFade.step(this, continueScreen::returnToTitleScreen)) return;
        menuScreenModeController.updateTitleScreen(
                getTitleScreenProviderLazy(),
                inputHandler,
                () -> {
                    if (!fadeManager.isActive()) {
                        exitTitleScreen();
                    }
                });
    }

    /**
     * LEVEL_SELECT per-frame update: delegates provider sequencing to the
     * menu-screen controller and exits once it reports a selection.
     */
    private void updateLevelSelectMode() {
        menuScreenModeController.updateLevelSelect(
                getLevelSelectProviderLazy(),
                inputHandler,
                this::exitLevelSelect);
    }

    /**
     * DATA_SELECT per-frame update: delegates provider sequencing to the
     * menu-screen controller and exits once it reports completion.
     */
    private void updateDataSelectMode() {
        menuScreenModeController.updateDataSelect(
                getDataSelectProviderLazy(),
                inputHandler,
                this::exitDataSelect);
    }

    /**
     * LEVEL per-frame update: overlay tick, seamless/title-card/fade transition
     * routing, the canonical gameplay frame step (or lag-gated skip), special/
     * bonus-stage entry checks, trace-session drive, and level debug keys.
     *
     * @return {@code true} when the frame completed normally and the caller
     *         should run the shared post-update tail; {@code false} when a
     *         transition/fade consumed the frame and the caller must return.
     */
    private boolean updateLevelMode(boolean doFrameStep) {
        // Continue updating title card overlay if still active
        // (TEXT_WAIT and TEXT_EXIT phases where player can move but text is still
        // visible)
        TitleCardProvider tcp = getTitleCardProviderLazy();
        TraceSuppressedRowClosure.executeUnownedTitleCardWork(
                playbackDebugManager.isCurrentGameplayTickSuppressed(),
                false, tcp, this::startPendingInLevelTitleCard,
                this::applyTitleCardControlLock, gameplayMode.getWorldSession());

        // Check if a title card was requested (new level loaded)
        if (levelManager.consumeTitleCardRequest()) {
            GameLoopPlcLifecycle.enterPendingTitleCard(() -> levelManager, resolveGameplayModeContext(), activePlcLifecycleFrame, this::enterTitleCard, this::getTitleCardProvider);
            levelIterationAdmission.finishPlaybackBoundary(
                    false, playbackDebugManager, userRecordingControls);
            updateNonGameplayAudio(doFrameStep);
            return false; // Skip normal level update this frame
        }

        // Check for transition requests that need fade-to-black
        FadeManager fadeManager = this.fadeManager;
        if (!fadeManager.isActive()) {
            if (levelManager.consumeRespawnRequest()) {
                startRespawnFade();
                levelIterationAdmission.finishPlaybackBoundary(
                        false, playbackDebugManager, userRecordingControls);
                updateNonGameplayAudio(doFrameStep);
                return false;
            }
            GameOverExit gameOverExit = levelManager.consumeGameOverExitRequest();
            if (gameOverExit != null) {
                userRecordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
                GameLoopGameOverExit.startToBlack(gameOverExit, levelManager, audioManager, fadeManager,
                        resolveGameplayModeContext(), () -> continueScreen.enterAfterGameOverFade(gameOverExit));
                levelIterationAdmission.finishPlaybackBoundary(
                        false, playbackDebugManager, userRecordingControls);
                updateNonGameplayAudio(doFrameStep);
                return false;
            }
            // The real act-completion gate: LevelManager.advanceToNextLevel() (called
            // directly by S1/S2 results-screen objects, bypassing the request/consume
            // queue below) and S3kResultsScreenObjectInstance.onExitReady() both queue
            // this instead of advancing when a time attack attempt is active. The four
            // sites below remain for seamless/next-act/next-zone/credits transitions
            // that are NOT time-attack-gated at their source (debug keys, S1/S2 boss
            // defeat -> credits, and the S3K seamless cross-act reload).
            if (levelManager.consumeHostReturnRequest()) {
                userRecordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
                completeHostedRunAct();
                levelIterationAdmission.finishPlaybackBoundary(
                        false, playbackDebugManager, userRecordingControls);
                updateNonGameplayAudio(doFrameStep);
                return false;
            }
            if (levelManager.consumeNextActRequest()) {
                userRecordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
                // A host-returning run ends (or retries) instead of bleeding into the next act.
                if (runPolicy().returnsToHostOnActCompletion()) {
                    completeHostedRunAct();
                } else {
                    startNextActFade();
                }
                levelIterationAdmission.finishPlaybackBoundary(
                        false, playbackDebugManager, userRecordingControls);
                updateNonGameplayAudio(doFrameStep);
                return false;
            }
            if (levelManager.consumeNextZoneRequest()) {
                userRecordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
                // A host-returning run ends (or retries) instead of bleeding into the next zone.
                if (runPolicy().returnsToHostOnActCompletion()) {
                    completeHostedRunAct();
                } else {
                    startNextZoneFade();
                }
                levelIterationAdmission.finishPlaybackBoundary(
                        false, playbackDebugManager, userRecordingControls);
                updateNonGameplayAudio(doFrameStep);
                return false;
            }
            if (levelManager.consumeZoneActRequest()) {
                userRecordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
                startZoneActFade(levelManager.getRequestedZone(), levelManager.getRequestedAct(),
                        levelManager.getRequestedMusicId());
                levelIterationAdmission.finishPlaybackBoundary(
                        false, playbackDebugManager, userRecordingControls);
                updateNonGameplayAudio(doFrameStep);
                return false;
            }
            if (levelManager.consumeCreditsRequest()) {
                userRecordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
                // A host-returning run ends (or retries) instead of bleeding into the credits.
                if (runPolicy().returnsToHostOnActCompletion()) {
                    completeHostedRunAct();
                } else if (GameServices.module().getGameId() == GameId.STANDALONE) {
                    masterTitleExitCoordinator.startStandaloneCompletion();
                } else {
                    endingSession.startEndingFade();
                }
                levelIterationAdmission.finishPlaybackBoundary(
                        false, playbackDebugManager, userRecordingControls);
                updateNonGameplayAudio(doFrameStep);
                return false;
            }
            RunEndReason leaveReason = hostedRuns.consumeLeave();
            if (leaveReason != null) {
                userRecordingControls.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
                endHostedRun(leaveReason);
                levelIterationAdmission.finishPlaybackBoundary(
                        false, playbackDebugManager, userRecordingControls);
                updateNonGameplayAudio(doFrameStep);
                return false;
            }
            if (hostedRuns.consumeRetry()) {
                requestHostedRunRetry(false);
                levelIterationAdmission.finishPlaybackBoundary(
                        false, playbackDebugManager, userRecordingControls);
                updateNonGameplayAudio(doFrameStep);
                return false;
            }
        }

        // Held simulation must still process retries, exits and load transitions above.
        if (!hostedRuns.admitStep(inputHandler)) {
            updateNonGameplayAudio(doFrameStep);
            return true;
        }

        boolean freezeForArtViewer = debugShortcutsEnabled()
                && debugOverlayManager.isEnabled(DebugOverlayToggle.OBJECT_ART_VIEWER);
        // Freeze level updates during special/bonus stage entry transitions, an
        // ending transition, or a pending zone/act fade (ObjB2 transition parity).
        boolean freezeForNonRewindableTransition = isNonRewindableTransitionPending();
        if (!freezeForArtViewer && !freezeForNonRewindableTransition) {
            GameplayModeContext executedOwner = gameplayMode;
            GameMode executedMode = currentGameMode;
            beginGameplayAudioFrameForTick();
            // LiveTraceComparator (or any PlaybackFrameObserver) may ask
            // us to skip the gameplay tick on ROM lag frames so the
            // engine and trace stay aligned. Cursor advance still runs
            // via onLevelFrameAdvanced below.
            boolean skipGameplay = TraceSessionLauncher.shouldSkipRunGameplayTick(
                    playbackDebugManager);
            boolean holdVblankForPendingLoad =
                    playbackDebugManager.shouldHoldVblankForPendingLevelLoad();
            if (!skipGameplay) {
                // Canonical level tick sequence — see LevelFrameStep for ordering rationale.
                spriteManager.publishHeldInputForLevelEvents(inputHandler);
                // ROM in-game pause (Game_paused / Pause_Loop): the P1 Start leading
                // edge is classified by the outer FrameAdmission before reaching
                // this ordinary step, so a pause freezes the level update while
                // the frame counter still advances. Universal across S1/S2/S3K
                // (the trigger and unpause are a Start-press edge in every game;
                // per-game pause divergences are debug-only cheats inert in normal
                // play). Keyboard-only on purpose: this pause has no visible HUD
                // feedback and never halts audio, so gamepad Start instead drives
                // the PAUSE_KEY-style userPaused toggle below (which does both) --
                // see the pauseKey handling earlier in this method.
                userRecordingControls.beforeLevelFrame(inputHandler);
                if (hostedRuns.isActive()) {
                    hostedRuns.beforeStep(inputHandler);
                }
                int heldVblank = levelManager.getObjectManager() != null
                        ? levelManager.getObjectManager().getVblaCounter()
                        : 0;
                LevelFrameResult frameResult = LevelFrameStep.execute(
                        LevelFrameContext.from(gameplayMode),
                        activePlcLifecycleFrame, PlcLifecyclePhase.ORDINARY_LEVEL,
                        levelManager, camera, () -> spriteManager.update(inputHandler),
                        (name, step) -> {
                            profiler.beginSection(name);
                            step.run();
                            profiler.endSection(name);
                        });
                if (frameResult == LevelFrameResult.GAMEPLAY_FRAME) {
                    playbackDebugManager.onCurrentGameplayTickExecuted();
                }
                if (frameResult == LevelFrameResult.SETUP_ONLY) {
                    return false;
                }
                if (holdVblankForPendingLoad
                        && playbackDebugManager.shouldHoldVblankForPendingLevelLoad()
                        && levelManager.getObjectManager() != null) {
                    levelManager.getObjectManager().initVblaCounter(heldVblank);
                }
                userRecordingControls.afterLevelFrame();
                hostedRuns.afterStep();
            } else if (levelManager.getObjectManager() != null
                    && !holdVblankForPendingLoad) {
                // ROM v_vbla_byte increments in VBlank even on rows where
                // LevelLoop did not run. Headless trace replay mirrors
                // this in HeadlessTestRunner.skipFrameFromRecording();
                // live visual trace mode must do the same or object timing
                // gates enter gameplay hundreds of VBlanks behind.
                int vblankTicks = playbackDebugManager.currentSkippedTickVblankAdvanceCount();
                for (int tick = 0; tick < vblankTicks; tick++) {
                    // A held iteration still services LevelLoop's Kos queue
                    // tail (sonic3k.asm:7940/7887), without gameplay dispatch.
                    TraceSuppressedRowClosure.execute(
                            LevelFrameContext.from(gameplayMode),
                            activePlcLifecycleFrame, levelManager,
                            this::startPendingInLevelTitleCard,
                            this::applyTitleCardControlLock);
                }
            }
            advanceGameplayAudioFrameForTick(doFrameStep);
            // Fire the BK2-advance callback either way — on both real
            // gameplay ticks and lag-gated skips — so the observer's
            // cursor always matches the BK2 cursor. onLevelFrameAdvanced
            // is a no-op when no playback session is active.
            if (!TraceSessionLauncher.isRunFrameDriverActive()) {
                advanceTraceRunPhysicalRow();
            }
            boolean seamlessTransitionCompleted =
                    levelManager.consumeActTransitionRewindBoundaryDuringFrame();
            TraceSessionLauncher traceSession = TraceSessionLauncher.active();
            if ((traceSession != null
                    && !TraceSessionLauncher.isRunFrameDriverActive())
                    || traceSession == null) {
                // Reached only inside the !freezeForNonRewindableTransition branch,
                // so the transition-pending predicate is guaranteed false here.
                LevelRewindFrameRecorder.record(
                        traceSession,
                        liveRewindManager,
                        executedOwner, executedMode,
                        freezeForNonRewindableTransition,
                        inputHandler,
                        seamlessTransitionCompleted);
            }

            // Check if a checkpoint star requested a special stage. The request is
            // always consumed here regardless of time-attack state (see
            // enterSpecialStage(), which swallows entry uniformly for every caller).
            SpecialStageEntryRequest specialStageRequest =
                    levelManager.consumeSpecialStageEntryRequest();
            if (specialStageRequest != null) {
                specialStageLifecycle.enterSpecialStage(specialStageRequest);
            }

            // Check if a bonus star requested a bonus stage
            BonusStageType bonusRequest = levelManager.consumeBonusStageRequest();
            if (bonusRequest != null) {
                bonusSession.enterBonusStage(bonusRequest);
            }

            // Drive the trace session completion-hold + auto-fade state.
            traceSession = TraceSessionLauncher.active();
            if (traceSession != null) {
                traceSession.tick();
            }
        } else {
            updateNonGameplayAudio(doFrameStep);
            // A level-to-level transition fade freezes gameplay but not V_int:
            // the admission controller consumes the row when the compared span
            // still owns it. See consumeTransitionFreezeRow for the ROM
            // citations and why the ownership question is asked, not assumed.
            if (freezeForNonRewindableTransition
                    && !TraceSessionLauncher.isRunFrameDriverActive()) {
                levelIterationAdmission.consumeTransitionFreezeRow(
                        playbackDebugManager,
                        levelManager != null ? levelManager.getObjectManager() : null,
                        LevelFrameContext.from(gameplayMode), activePlcLifecycleFrame);
            }
        }

        // Debug keys for level transitions (use request system for fade)
        if (isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.NEXT_ACT))) {
            levelManager.requestNextAct();
        }

        if (isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.NEXT_ZONE))) {
            // DEZ: skip to ending cutscene instead of next zone
            if (levelManager.getRomZoneId() == 0x0E) {
                levelManager.requestCreditsTransition();
            } else {
                levelManager.requestNextZone();
            }
        }

        // Debug: Teleport to last checkpoint (END key, only in LEVEL mode)
        if (isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.DEBUG_LAST_CHECKPOINT_KEY))) {
            debugShortcuts.teleportToLastCheckpoint(levelManager, spriteManager, camera,
                    configService, resolveMainCharacterCode());
        }

        // Level select key (F9 by default)
        if (isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.LEVEL_SELECT_KEY))) {
            enterLevelSelect();
        }

        if (isUnmodifiedDebugKeyPressed(
                configService.getInt(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY))) {
            debugShortcuts.logCurrentPreviewCaptureOverride(camera, levelManager);
        }
        return true;
    }

    /**
     * Completes a host-returning run's act on a cross-act seamless request before the admission
     * owner applies its destination: the run ends or, like the other act-completion sites,
     * retries. Mid-act seamless requests remain owned by the ordinary transition path.
     *
     * <p>Only a deferred request reaches this route. The S3K events that reload in place inside
     * their own event pass (AIZ1's fire finish, CNZ1, HCZ1, MGZ1 and others once a level is
     * loaded) never queue one, so their act completes later through the results exit's
     * host-return request. MHZ1 queues its reload while the act-1 tally is still running, so
     * the run completes here, a few frames into the tally.
     *
     * <p>The request is dropped either way, so its single-use resource handoff is released
     * rather than left in the session registry holding the outgoing level. Everything else its
     * zone event armed for the reload (per-zone event handlers, queued art) belongs to the act
     * that a retry's fresh load replaces, as it does for a retry pressed mid-act.
     */
    private boolean routeHostedRunSeamlessTransitionBeforeApply(
            SeamlessLevelTransitionRequest request, boolean doFrameStep) {
        GameplayModeContext run = resolveGameplayModeContext();
        if (run == null || !GameplayRunRouting.endsRunOnSeamlessTransition(run.getRunPolicy(),
                run.getRunZone(), run.getRunAct(), request.type(), request.targetZone(), request.targetAct())) {
            return false;
        }
        var handoffs = run.seamlessTransitionResourceHandoffs();
        if (request.resourceHandoffId() != null && !handoffs.hasFailedTransfer(request.resourceHandoffId())) {
            handoffs.claim(request.resourceHandoffId());
        }
        completeHostedRunAct();
        updateNonGameplayAudio(doFrameStep);
        return true;
    }

    /**
     * A host-returning run completed its act: it restarts the act as a retry or ends, as
     * {@link HostedRunController#resolveActCompletion} decides.
     */
    private void completeHostedRunAct() {
        RunEndReason ended = hostedRuns.resolveActCompletion();
        if (ended == null) {
            requestHostedRunRetry(true);
        } else {
            endHostedRun(ended);
        }
    }

    /**
     * Reloads the hosted run's act from its start with star posts cleared (a retry). A
     * completed act stays frozen under the reload fade, as the level reload that follows a
     * shipped results exit fades out with no object pass (S1 {@code f_restart}, S2
     * {@code Level_Inactive_flag}); an S2 results exit has already frozen it for its own fade,
     * and nothing left in it may raise another completion. A retry pressed mid-act keeps
     * playing under the fade as before.
     */
    private void requestHostedRunRetry(boolean actCompleted) {
        RunSpec retrySpec = hostedRuns.spec();
        levelManager.getCheckpointState().clear();
        if (actCompleted) {
            levelManager.getTransitions().requestZoneAndAct(retrySpec.zone(), retrySpec.act(), true);
        } else {
            levelManager.getTransitions().requestZoneAndAct(retrySpec.zone(), retrySpec.act());
        }
    }

    /** Ends the session's hosted run and hands control back to its host. */
    private void endHostedRun(RunEndReason reason) {
        Consumer<RunEndReason> back = hostedRunReturn;
        hostedRunReturn = ignored -> { };
        hostedRuns.end(reason);
        back.accept(reason);
    }

    private Runnable pendingHostedRunLaunch;
    private Runnable pendingTitleEntryOpen;

    /** Opens a mod title entry once the master title's update has returned, behind a fade. */
    void requestTitleEntryOpen(Runnable open) {
        pendingTitleEntryOpen = Objects.requireNonNull(open, "open");
    }

    /** Starts a scene-launched run's session once the scene callback has unwound, behind a fade. */
    void requestHostedRunLaunch(Runnable launch) {
        pendingHostedRunLaunch = Objects.requireNonNull(launch, "launch");
    }

    private void startPendingHostedRunLaunch() {
        if (pendingHostedRunLaunch != null && GameLoopMenuTransitions.idleOrHeldBlack(resolveFadeManager())) {
            Runnable launch = pendingHostedRunLaunch;
            pendingHostedRunLaunch = null;
            GameLoopMenuTransitions.fadeOutOrContinue(resolveFadeManager(), audioManager, launch);
        }
    }

    /** A scene asked to launch a run (for example from {@code resumed}) and it has not started yet. */
    boolean hostedRunLaunchPending() { return pendingHostedRunLaunch != null; }

    /** Ends a hosted run whose session failed to load; optionally hands control back to its host. */
    void endHostedRunAfterFailedLaunch(boolean returnToHost) {
        if (returnToHost) {
            endHostedRun(RunEndReason.LOAD_FAILED);
        } else {
            hostedRunReturn = ignored -> { };
            hostedRuns.end(RunEndReason.LOAD_FAILED);
        }
    }

    /**
     * Tells the hosted run its level is ready when a launch or a fast death restart
     * ({@code deathRestart}) bypassed {@code doZoneAct}.
     */
    void announceHostedRunLevelReady(boolean deathRestart) {
        hostedRuns.onLevelReady(debugOverlayManager.isEnabled(DebugOverlayToggle.OVERLAY), deathRestart);
    }

    /** Hosts {@code host}'s run of {@code spec}; {@code returnToHost} runs once when it ends. */
    RunHandle beginHostedRun(RunSpec spec, RunHost host, Consumer<RunEndReason> returnToHost) {
        RunHandle handle = hostedRuns.begin(spec, host);
        hostedRunReturn = Objects.requireNonNull(returnToHost, "returnToHost");
        return handle;
    }

    private GameplayRunPolicy runPolicy() {
        GameplayModeContext run = resolveGameplayModeContext();
        return run != null ? run.getRunPolicy() : GameplayRunPolicy.stock();
    }

    /**
     * BONUS_STAGE per-frame update: overlay tick, provider-declared bootstrap, the
     * shared level frame step (with the bonus-stage high-priority enforcement
     * and completion check), and the gumball debug shortcuts. Falls through to
     * the shared post-update tail.
     */
    private void updateBonusStageMode(boolean doFrameStep) {
        // Continue updating title card overlay during bonus stage
        // (EXIT phase: elements slide off screen after exitTitleCard transitioned here)
        com.openggf.game.internal.BonusStageFrameLifecycle.updateOverlay(
                getTitleCardProviderLazy(), gameplayMode.getWorldSession());

        com.openggf.game.internal.BonusStageFrameLifecycle.ensureBootstrapPresent(
                bonusSession.provider(), levelManager.getObjectManager());

        // Bonus stage runs the same level frame steps as LEVEL mode
        BonusStageProvider executedProvider = bonusSession.provider();
        boolean freezeForBonusExit = bonusSession.isTransitionPending();
        if (!freezeForBonusExit) {
            GameplayModeContext executedOwner = gameplayMode;
            GameMode executedMode = currentGameMode;
            beginGameplayAudioFrameForTick();
            spriteManager.publishHeldInputForLevelEvents(inputHandler);
            // LiveTraceComparator (or any PlaybackFrameObserver) may ask us to
            // skip the gameplay tick on ROM lag frames so the engine and trace
            // stay aligned. Cursor advance still runs via onLevelFrameAdvanced
            // below. Mirrors updateLevelMode's skip gate.
            boolean skipGameplay = playbackDebugManager.shouldSkipCurrentGameplayTick();
            if (!skipGameplay) {
                LevelFrameStep.execute(
                        LevelFrameContext.from(gameplayMode),
                        activePlcLifecycleFrame, PlcLifecyclePhase.ORDINARY_LEVEL,
                        levelManager, camera, () -> {
                    spriteManager.update(inputHandler);
                }, (name, step) -> {
                    profiler.beginSection(name);
                    step.run();
                    profiler.endSection(name);
                });
                playbackDebugManager.onCurrentGameplayTickExecuted();

                // Restore priority only for stages using the ordinary bonus-player
                // policy. This must follow physics because hurt-landing can clear it.
                bonusSession.forcePlayerHighPriorityInBonusStage();

                // Notify coordinator of frame tick
                com.openggf.game.internal.BonusStageFrameLifecycle.updateProvider(executedProvider);

                // Record a rewind keyframe/step for rewind-supported bonus stages
                // whose providers capture their interior state. Placed before
                // the completion check so the transition boundary can retire
                // the interior history independently of ordinary LEVEL history.
                if (executedProvider != null && executedProvider.supportsRewind()
                        && TraceSessionLauncher.active() == null) {
                    liveRewindManager.recordExternalFrame(executedOwner,
                            executedMode, null, executedProvider, freezeForBonusExit, inputHandler, false);
                }

                // Check bonus stage completion
                if (bonusSession.provider() != null && bonusSession.provider().isStageComplete()) {
                    bonusSession.exitBonusStage();
                }
            } else if (levelManager.getObjectManager() != null) {
                // ROM v_vbla_byte increments in VBlank even on rows where
                // LevelLoop did not run; keep bonus lag rows in VBla-counter
                // parity, mirroring updateLevelMode's skip branch.
                int vblankTicks = playbackDebugManager.currentSkippedTickVblankAdvanceCount();
                for (int tick = 0; tick < vblankTicks; tick++) {
                    LevelFrameStep.serviceHardwareVBlankOnly(
                            LevelFrameContext.from(gameplayMode), activePlcLifecycleFrame);
                    // V-blank-only row: see the exactly-one-tick-per-serviced-V-blank invariant on ObjectManager.vblaCounter.
                    levelManager.getObjectManager().advanceVblaCounter();
                }
            }
            advanceGameplayAudioFrameForTick(doFrameStep);
            // Fire the BK2-advance callback either way — on both real
            // gameplay ticks and lag-gated skips — so the observer's cursor
            // always matches the BK2 cursor. onLevelFrameAdvanced is a no-op
            // when no playback session is active.
            playbackDebugManager.onLevelFrameAdvanced();
        } else {
            updateNonGameplayAudio(doFrameStep);
            // ROM parity during the bonus-exit hold/fade: after the machine catches
            // the player (Check_PlayerInRange -> loc_61076 sets Restart_level_flag),
            // the ROM keeps running BONUS_STAGE frames while the screen fades and the
            // level reload is staged, and V_int (the recorder's frame source) keeps
            // ticking across them -- so the recorded interior segment includes that
            // ~150-frame tail before its mode_change_bk2_frame. The engine's exit
            // fade freezes gameplay here, but must still advance the shared playback
            // cursor (and the VBla counter) once per frozen frame, exactly as the
            // lag-skip branch above does, or the cursor lands well short of the
            // return segment's offset and the BONUS->LEVEL fall-through feeds the
            // return level a stale frame-0 input. onLevelFrameAdvanced is a no-op
            // when no playback session is active.
            if (levelManager.getObjectManager() != null) {
                LevelFrameStep.serviceHardwareVBlankOnly(
                        LevelFrameContext.from(gameplayMode), activePlcLifecycleFrame);
                // V-blank-only row: see the exactly-one-tick-per-serviced-V-blank invariant on ObjectManager.vblaCounter.
                levelManager.getObjectManager().advanceVblaCounter();
            }
            playbackDebugManager.onLevelFrameAdvanced();
        }

        // Debug: F11 cycles through bucket/priority isolation for the gumball machine
        if (isUnmodifiedDebugKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F11)) {
            com.openggf.game.internal.BonusStageFrameLifecycle.cycleObjectRenderFilter(executedProvider);
        }
        // Debug: Insert cycles through gumball child-source isolation without colliding with overlay F-keys.
        if (isUnmodifiedDebugKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_INSERT)) {
            com.openggf.game.internal.BonusStageFrameLifecycle.cycleObjectSourceFilter(executedProvider);
        }
    }

    private void updateNonGameplayAudio(boolean doFrameStep) {
        if (audioUpdatedThisStep) {
            return;
        }
        if (shouldAdvanceGameplayAudioForCurrentMode()) {
            beginGameplayAudioFrameForTick();
            advanceGameplayAudioFrameForTick(doFrameStep);
            return;
        }
        audioUpdatedThisStep = true;
    }

    private boolean shouldAdvanceGameplayAudioForCurrentMode() {
        return currentGameMode == GameMode.LEVEL
                || currentGameMode == GameMode.BONUS_STAGE
                || currentGameMode == GameMode.TITLE_CARD;
    }

    private void beginGameplayAudioFrameForTick() {
        gameplayAudioFrame++;
        audioManager.beginGameplayAudioFrame(gameplayAudioFrame);
    }

    private void advanceGameplayAudioFrameForTick(boolean doFrameStep) {
        if (audioUpdatedThisStep) {
            return;
        }
        audioUpdatedThisStep = true;
    }

    boolean handlePlaybackTakeoverBeforePlaybackInputBridge(InputHandler input) {
        if (!GameLoopPauseInput.requestTakeover(currentGameMode, input, configService, userPaused,
                userRecordingControls::handlePlaybackTakeoverRequest)) {
            return false;
        }
        userPaused = false;
        updateAudioPauseState();
        return true;
    }

    public void applyScheduledPlaybackInputImmediately() {
        playbackInputBridge.publishImmediately(playbackDebugManager, inputHandler,
                spriteManager);
    }

    private void syncPlaybackInputBridge() {
        playbackInputBridge.sync(playbackDebugManager, currentGameMode,
                inputHandler, spriteManager);
    }

    /**
     * Package-private so {@code com.openggf.TraceSessionLauncher.LiveFixture}
     * can resolve the primary playable sprite by calling the same
     * ActiveGameplayTeamResolver path the rest of GameLoop uses.
     */
    AbstractPlayableSprite getMainPlayableSprite() {
        if (spriteManager == null) {
            return null;
        }
        String mainCode = resolveMainCharacterCode();
        var sprite = spriteManager.getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite playable) {
            return playable;
        }
        return null;
    }

    String resolveMainCharacterCode() {
        return ActiveGameplayTeamResolver.resolveMainCharacterCode(configService);
    }

    /**
     * Handles the special stage debug key (TAB by default).
     * When in level mode, enters the next special stage.
     * When in special stage mode, exits to results screen (as failure).
     * When in results screen mode, skips back to level.
     */
    private void handleSpecialStageDebugKey() {
        if (currentGameMode == GameMode.LEVEL) {
            enterSpecialStage();
        } else if (currentGameMode == GameMode.SPECIAL_STAGE) {
            specialStageLifecycle.enterResultsScreen(false);
        } else if (currentGameMode == GameMode.SPECIAL_STAGE_RESULTS) {
            specialStageLifecycle.exitResultsScreen();
        }
    }

    private void detectSpecialStageLiveOnlyShortcutBoundary() {
        if (currentGameMode != GameMode.SPECIAL_STAGE || !debugShortcutsEnabled()) {
            return;
        }

        SpecialStageProvider ssProvider = getActiveSpecialStageProvider();
        SpecialStageDebugCapabilities debugCapabilities =
                SpecialStageDebugCapabilities.orNone(ssProvider.debugCapabilities());
        int leftKey = configService.getInt(SonicConfiguration.LEFT);
        int rightKey = configService.getInt(SonicConfiguration.RIGHT);
        int upKey = configService.getInt(SonicConfiguration.UP);
        int downKey = configService.getInt(SonicConfiguration.DOWN);

        if (isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_KEY))
                || (debugCapabilities.stageSelection()
                && isUnmodifiedDebugKeyPressed(GLFW_KEY_X))
                || (debugCapabilities.layoutSelection()
                && isUnmodifiedDebugKeyPressed(GLFW_KEY_Z))
                || isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_COMPLETE_KEY))
                || isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_FAIL_KEY))
                || (debugCapabilities.spriteViewer()
                && isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_SPRITE_DEBUG_KEY)))
                || (debugCapabilities.planeVisibility()
                && isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_PLANE_DEBUG_KEY)))
                || (debugCapabilities.gameplayMovement()
                && isUnmodifiedDebugKeyPressed(configService.getInt(SonicConfiguration.DEBUG_MODE_KEY)))
                || (debugCapabilities.alignment()
                && isUnmodifiedDebugKeyPressed(GLFW_KEY_F4))
                || (debugCapabilities.lagCompensation()
                && isUnmodifiedDebugKeyPressed(GLFW_KEY_F1))) {
            severSpecialStageRewindForLiveOnlyShortcut();
            return;
        }

        if (debugCapabilities.spriteViewer() && ssProvider.isSpriteDebugMode()
                && ssProvider.getDebugProvider() != null
                && (isUnmodifiedDebugKeyPressed(leftKey)
                || isUnmodifiedDebugKeyPressed(rightKey)
                || isUnmodifiedDebugKeyPressed(upKey)
                || isUnmodifiedDebugKeyPressed(downKey))) {
            severSpecialStageRewindForLiveOnlyShortcut();
            return;
        }

        if (debugCapabilities.alignment() && ssProvider.isAlignmentTestMode()
                && (isUnmodifiedDebugKeyPressed(leftKey)
                || isUnmodifiedDebugKeyPressed(rightKey)
                || isUnmodifiedDebugKeyPressed(upKey)
                || isUnmodifiedDebugKeyPressed(downKey)
                || isUnmodifiedDebugKeyPressed(GLFW_KEY_SPACE))) {
            severSpecialStageRewindForLiveOnlyShortcut();
        }
    }

    private boolean isUnmodifiedDebugKeyPressed(int keyCode) {
        return debugShortcutsEnabled() && inputHandler.isKeyPressedWithoutModifiers(keyCode);
    }

    private boolean debugShortcutsEnabled() {
        return configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    }

    static BonusStageType resolveBonusStageDebugShortcut(InputHandler inputHandler) {
        return GameLoopDebugShortcuts.resolveBonusStageDebugShortcut(inputHandler);
    }

    public static ObjectSpawn resolveBonusStageBootstrapSpawn(BonusStageType type) {
        return BonusStageBootstrapInstaller.resolveSpawn(
                GameServices.currentOrBootstrapGameModule().getBonusStageProvider(), type);
    }

    /**
     * Enters the special stage from level mode.
     * Uses GameStateManager to track which stage to enter (cycles 0-6).
     * <p>
     * The mode change is immediate, because that is what every ROM does: the
     * level-side owner writes the game mode inside its own object tick and
     * runs no fade of its own — S1 {@code Got_ChkSS}
     * ("_incObj/3A Got Through Card.asm":198-201), S2 {@code Obj79_Star}
     * (s2.asm:44875-44877), S3K {@code SSEntryFlash_GoSS} (s3.asm:79688). The
     * white-out that precedes the stage belongs to the special-stage entry
     * itself ({@code GM_Special}'s {@code PaletteWhiteOut}, sonic.asm:3227 /
     * {@code SpecialStage}'s {@code Pal_FadeToWhite}, s2.asm:6546), which
     * {@link SpecialStageEntryPresentationController} owns: it fades the
     * level's frozen last frame to white (the Engine keeps drawing the level
     * while {@link SpecialStageProvider#isEntryFadeToWhiteActive()} holds) and
     * parks white until the provider's reveal boundary. Fading here first
     * would delay the mode change by the whole fade and run the white-out
     * twice.
     */
    public void enterSpecialStage() {
        specialStageLifecycle.enterSpecialStage(SpecialStageEntryRequest.ordinary());
    }

    /**
     * Actually enters the special stage after the transition fade completes.
     * The provider owns the entry fade; the mode boundary is committed immediately.
     *
     * @param fadeFromBlack true if the screen is already black and should fade from black;
     *                      false for the normal fade-from-white reveal
     */
    void doEnterSpecialStage(SpecialStageProvider ssProvider, int stageIndex,
                                     boolean fadeFromBlack) {
        SpecialStageStartupPolicy startupPolicy = specialStageLifecycle.defaultSpecialStageStartupPolicy();
        specialStageLifecycle.doEnterSpecialStage(ssProvider, stageIndex, fadeFromBlack, startupPolicy,
                specialStageLifecycle.rewardKind());
        if (startupPolicy == SpecialStageStartupPolicy.TRACE_ACCURATE) {
            // Startup policy and external scheduling admission are independent
            // contracts. Retain the legacy provider notification for callers
            // that implement live pacing; S2 disables its interactive
            // approximation because recorded lag rows are admitted at the
            // timing port.
            ssProvider.setLagCompensation(0);
        }
    }

    void doEnterSpecialStage(SpecialStageProvider ssProvider, int stageIndex,
                             boolean fadeFromBlack, SpecialStageStartupPolicy startupPolicy) {
        specialStageLifecycle.doEnterSpecialStage(ssProvider, stageIndex, fadeFromBlack, startupPolicy,
                EmeraldRewardKind.CHAOS_EMERALD);
    }

    // Compatibility seams; host-side visit state and choreography belong to bonusSession.
    static boolean shouldStartBonusStageExitFade(BonusStageProvider provider) {
        return GameLoopBonusStageSession.shouldStartBonusStageExitFade(provider);
    }

    static int encodeSavedShieldStatus(AbstractPlayableSprite playable) {
        return GameLoopBonusStageSession.encodeSavedShieldStatus(playable);
    }

    static ShieldType resolveShieldToRestore(BonusStageProvider.BonusStageRewards rewards, int savedStatusSecondary) {
        return GameLoopBonusStageSession.resolveShieldToRestore(rewards, savedStatusSecondary);
    }

    static ShieldType savedShieldType(int savedStatusSecondary) {
        return GameLoopBonusStageSession.savedShieldType(savedStatusSecondary);
    }

    public static void applyBonusStageEntryShieldRestore(AbstractPlayableSprite playable, int savedStatusSecondary) {
        GameLoopBonusStageSession.applyBonusStageEntryShieldRestore(playable, savedStatusSecondary);
    }

    AbstractPlayableSprite resolveMainPlayableSprite() {
        String mainCode = resolveMainCharacterCode();
        var sprite = spriteManager.getSprite(mainCode);
        return sprite instanceof AbstractPlayableSprite playable ? playable : null;
    }

    void applyPendingBonusStageShieldRestore(AbstractPlayableSprite playable) {
        bonusSession.applyPendingBonusStageShieldRestore(playable);
    }

    void setPendingBonusStageShieldRestoreForTest(ShieldType shieldType) {
        bonusSession.setPendingShield(shieldType);
    }

    boolean hasPendingBonusStageShieldRestoreForTest() {
        return bonusSession.hasPendingShield();
    }

    private void restoreBigRingReturn(AbstractPlayableSprite playable) {
        SpecialStageTransitionSupport.restoreBigRingReturn(levelManager.getBigRingReturn(),
                playable, camera, levelManager, waterSystem);
    }

    /**
     * Enters the title card for the current zone/act.
     * Called when a level first loads or after player respawns.
     *
     * @param zoneIndex Zone index (0-10)
     * @param actIndex  Act index (0-2)
     */
    public void enterTitleCard(int zoneIndex, int actIndex) {
        titleCardLifecycle.enterTitleCard(zoneIndex, actIndex);
    }

    void startPendingInLevelTitleCard() {
        titleCardLifecycle.startPendingInLevelTitleCard();
    }

    /**
     * Applies (or releases) the control lock on all playable sprites for the
     * duration of the title card.
     *
     * <p>ROM parity:
     * <ul>
     *   <li>S1 sets {@code Control_Locked} during the title-card setup so
     *       Sonic_ControlsLock skips input parsing.</li>
     *   <li>S2 sets {@code Control_Locked}/{@code Control_Locked_P2} at
     *       s2.asm:4950-4951 immediately after the {@code Level_TtlCard}
     *       wait loop, before the player is allowed to move.</li>
     *   <li>S3K sets {@code Ctrl_1_locked}/{@code Ctrl_2_locked} at
     *       sonic3k.asm:7806-7807 immediately after the title-card wait
     *       loop, clearing them when the level proper starts.</li>
     * </ul>
     *
     * <p>Both the main player and any CPU sidekicks are toggled so the
     * canonical {@code LevelFrameStep} sprite update path reads no input
     * while the title card is on screen.
     */
    void applyTitleCardControlLock(boolean locked) {
        titleCardLifecycle.applyTitleCardControlLock(locked);
    }

    // ==================== Master Title Screen Methods ====================

    /**
     * Pre-flight check for {@link #launchGameByEntry}. Returns false when
     * a master-title fade is already in flight (launchGameByEntry would
     * throw in that case). Package-private so
     * {@link TraceSessionLauncher} can refuse a launch *before* mutating
     * the configuration service.
     */
    boolean canLaunchGameNow() {
        return !resolveFadeManager().isActive();
    }

    void launchGameByEntry(MasterTitleScreen.GameEntry entry, Runnable afterGameLoaded) {
        launchGameByEntry(entry, afterGameLoaded, Map.of());
    }

    /** As above, applying session-only settings after the master-title exit clears overrides. */
    void launchGameByEntry(MasterTitleScreen.GameEntry entry, Runnable afterGameLoaded,
            Map<SonicConfiguration, Object> sessionOverrides) {
        GameLoopMenuTransitions.launchGameByEntry(currentMasterTitleScreen(), this::resolveFadeManager,
                masterTitleLaunchCoordinator, entry, afterGameLoaded, sessionOverrides, this::exitMasterTitleScreen);
    }

    private void runAfterStepMasterTitleLaunchCallbackIfPresent() {
        masterTitleLaunchCoordinator.runAfterStepLaunchCallbackIfPresent();
    }

    void setReturnToMasterTitleHandler(Runnable returnToMasterTitleHandler) {
        masterTitleLaunchCoordinator.setReturnToMasterTitleHandler(returnToMasterTitleHandler);
    }

    void setMasterTitleLaunchFailureHandler(Runnable masterTitleLaunchFailureHandler) {
        masterTitleLaunchCoordinator.setLaunchFailureHandler(masterTitleLaunchFailureHandler);
    }

    private MasterTitleScreen currentMasterTitleScreen() {
        MasterTitleScreen masterScreen = masterTitleScreenSupplier != null
                ? masterTitleScreenSupplier.get() : null;
        installUserRecordingPlaybackStarter(masterScreen);
        return masterScreen;
    }

    private void installUserRecordingPlaybackStarter(MasterTitleScreen masterScreen) {
        if (masterScreen != null) {
            masterScreen.setUserRecordingPlaybackStarter(userRecordingPlaybackStarter);
        }
    }

    /**
     * Tear down the current trace session and hand control back to the
     * Engine so it can recreate the master title screen and reset
     * gameplay state. Called by {@link TraceSessionLauncher#teardown()}.
     */
    void retireModScene() { modSceneActBridge.retire(); }

    void returnToMasterTitle() {
        retireModScene();
        tearDownGameplayForReturn();
        masterTitleLaunchCoordinator.returnToMasterTitle();
    }

    /** Gameplay teardown shared by the master-title return and a hosted run's scene return. */
    void tearDownGameplayForReturn() {
        escapeToMasterTitleController.reset();
        levelIterationAdmission.reset();
        userRecordingSessionLauncher.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
        userRecordingSessionLauncher.endPlaybackSession();
        levelIterationAdmission.resetLastAppliedPlaybackFrame();
        pendingHostedRunLaunch = null;
        // Every route out of a hosted run that was not already ended funnels through
        // here (escape-to-master-title hold and TraceSessionLauncher teardown), so the
        // run must end or its frame hooks would fire in the next session.
        hostedRunReturn = ignored -> { };
        hostedRuns.end(RunEndReason.ABORTED);
    }

    /**
     * Fades the music and picture out, then runs {@code next} (back to the master title, or the
     * application exit); does nothing while a fade is already running.
     */
    void fadeOutTo(Runnable next) {
        GameLoopMenuTransitions.fadeOutTo(resolveFadeManager(), audioManager, next);
    }

    /**
     * Exits the master title screen after the user selects a game.
     * Performs a fade-to-black, then initializes the selected game (Phase 2),
     * and transitions to the game-specific title screen.
     */
    private void exitMasterTitleScreen(MasterTitleScreen masterScreen) {
        if (resolveFadeManager().isActive()) return;
        GameLoopMenuTransitions.exitMasterTitleScreen(masterScreen, resolveFadeManager(), masterTitleExitCoordinator);
    }

    /**
     * Actually performs the master title screen exit after fade-to-black completes.
     */
    private void doExitMasterTitleScreen(String selectedGameId, boolean programmaticSelection) { masterTitleExitCoordinator.exitStock(selectedGameId, programmaticSelection); }

    private void doExitStandaloneMasterTitle(MasterTitleEntry.Launch launch) { masterTitleExitCoordinator.exitStandalone(launch); }

    private boolean hasReadyGameplayRuntime() {
        if (gameplayMode != null && gameplayMode.isGameplayRuntimeReady()) {
            return true;
        }
        GameplayModeContext currentGameplayMode = SessionManager.getCurrentGameplayMode();
        return currentGameplayMode != null && currentGameplayMode.isGameplayRuntimeReady();
    }

    public void restartFromRecordingLaunchContext(RecordingLaunchContext context) {
        gameplayTeamBootstrapContext.restartRecording(this, engineServices, context);
    }

    FadeManager resolveFadeManager() {
        FadeManager manager = this.fadeManager;
        if (manager != null) {
            return manager;
        }
        return engineServices.graphics().getFadeManager();
    }

    // ==================== Title Screen Methods ====================

    /**
     * Initializes the game loop to start in title screen mode.
     * Called from Engine.init() when TITLE_SCREEN_ON_STARTUP is true.
     */
    public void initializeTitleScreenMode() {
        LOGGER.info("Initializing game in Title Screen mode");

        // Ensure the ROM is loaded and audio is initialized
        try {
            var rom = romManager.getRom();
            var gameModule = GameServices.module();

            audioManager.setAudioProfile(gameModule.getAudioProfile());
            audioManager.setRom(rom);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ROM for title screen", e);
        }

        GameMode oldMode = changeGameModeForBoundary(GameMode.TITLE_SCREEN);

        camera.setX((short) 0);
        camera.setY((short) 0);

        TitleScreenProvider titleScreen = getTitleScreenProviderLazy();
        if (titleScreen != null) {
            titleScreen.initialize();
        }

        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        LOGGER.info("Game initialized in Title Screen mode");
    }

    /**
     * Exits the title screen and hands off to the resolved destination.
     *
     * <p>The destination flow owns the visual transition: Sonic 1 level select
     * reuses the frozen title backdrop, Sonic 2 level start goes straight into
     * the pre-level/title-card pipeline, and donated data select uses a
     * fade-to-black / fade-from-black handoff.
     */
    private void exitTitleScreen() {
        nativeMenuFlow.exitTitleScreen();
    }

    /**
     * Enters the level select screen directly from the title screen, with no
     * fade transition and no music restart. Used by Sonic 1 where the title
     * screen art remains visible (with level select palette) behind the menu.
     *
     * <p>From the Sonic 1 disassembly (Tit_ChkLevSel): the transition loads
     * Pal_LevelSel, clears the BG VRAM plane, and draws the menu text. Music
     * continues uninterrupted.
     */
    private void doEnterLevelSelectFromTitleScreen() {
        // Do NOT reset the title screen - its art data is still needed
        // for rendering the frozen background behind level select text.
        // Do NOT fade music - title music continues.

        GameMode oldMode = changeGameModeForBoundary(GameMode.LEVEL_SELECT);

        camera.setX((short) 0);
        camera.setY((short) 0);

        LevelSelectProvider levelSelect = getLevelSelectProviderLazy();
        if (levelSelect != null) {
            levelSelect.initializeFromTitleScreen();
        }

        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        LOGGER.info("Title screen -> Level Select (immediate, no fade)");
    }

    /**
     * Actually performs the title screen exit after fade-to-black completes.
     */
    private void doExitTitleScreen() {
        nativeMenuFlow.doExitTitleScreen();
    }

    private void doExitTitleScreen(TitleActionRoute route) {
        nativeMenuFlow.doExitTitleScreen(route);
    }

    /**
     * Gets the title screen provider from the current game module.
     */
    public TitleScreenProvider getTitleScreenProvider() {
        return getTitleScreenProviderLazy();
    }

    TitleScreenProvider getTitleScreenProviderLazy() {
        var gameModule = GameServices.module();
        if (gameModule != null) {
            TitleScreenProvider titleScreenProvider = gameModule.getTitleScreenProvider();
            if (titleScreenProvider != null) {
                titleScreenProvider.setExitToLevelHandler(this::handleTitleScreenExitFromProvider);
            }
            return titleScreenProvider;
        }
        return null;
    }

    void startLevelFromTitleScreenImmediate(int zone, int act) {
        setGameMode(GameMode.LEVEL);
        GameServices.gameState().startNewGameFromTitle();
        try {
            levelManager.loadZoneAndActForFreshRuntime(zone, act);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load title screen start level", e);
        }
        GameLoopPlcLifecycle.startFromBlack(resolveGameplayModeContext(), fadeManager, null);
    }

    private int titleStartAct(int zone) {
        return nativeMenuFlow.titleStartAct(zone);
    }

    private int titleStartZone() {
        return nativeMenuFlow.titleStartZone();
    }

    private void handleTitleScreenExitFromProvider() {
        nativeMenuFlow.handleTitleScreenExitFromProvider();
    }

    private TitleActionRoute resolveTitleActionRoute(TitleScreenProvider titleScreen) {
        return nativeMenuFlow.resolveTitleActionRoute(titleScreen);
    }

    private DataSelectPresentationResolution resolveDataSelectPresentation() {
        return nativeMenuFlow.resolveDataSelectPresentation();
    }

    private boolean shouldFadeTitleScreenExit(TitleActionRoute route) {
        return nativeMenuFlow.shouldFadeTitleScreenExit(route);
    }

    private void executeTitleActionRoute(TitleActionRoute route, int zone, int act) {
        nativeMenuFlow.executeTitleActionRoute(route, zone, act);
    }

    private void openNativeTitleMode(boolean options) {
        nativeMenuFlow.openNativeTitleMode(options);
    }

    com.openggf.game.nativemenu.NativeMenuScreen getNativeMenuScreen() {
        return nativeMenus.screen();
    }

    void setNativeGameplayModePublisher(Consumer<GameplayModeContext> publisher) {
        nativeGameplayModePublisher = Objects.requireNonNull(publisher, "publisher");
    }

    void publishNativeGameplayMode(GameplayModeContext context) {
        nativeGameplayModePublisher.accept(context);
    }

    private void updateNativeMenuMode() {
        nativeMenuFlow.updateNativeMenuMode();
    }

    private void completeNativeMenuFrame() {
        nativeMenuFlow.completeFrame();
        sessionReset.completeFrame(inputHandler);
    }

    /** Retire loop-local visits before rebuilding a Reset/Power world; the movie keeps its cursor. */
    void retireRuntimeForSessionReset() {
        retireModScene();
        playbackInputBridge.sync(playbackDebugManager, GameMode.MASTER_TITLE_SCREEN, inputHandler, spriteManager);
        escapeToMasterTitleController.reset();
        levelIterationAdmission.reset();
        configurationCommands.clear();
        userRecordingSessionLauncher.stopActiveRecording(UserRecordingStopReason.LEVEL_ENDED);
        pendingHostedRunLaunch = null;
        pendingTitleEntryOpen = null;
        hostedRunReturn = ignored -> { };
        hostedRuns.end(RunEndReason.ABORTED);
        specialStageLifecycle.retireForSessionReset();
        bonusSession.retireForSessionReset();
        endingSession.retireForSessionReset();
        resetModuleScopedProviders();
        titleCardLifecycle.destination(PostTitleCardDestination.LEVEL);
        specialStageRewindBoundaryThisFrame = false;
        titleCardReleasedIntoLevelThisIteration = false;
        userPaused = false;
        gameplayAudioRateOwned = false;
        gameplayAudioFrame = 0;
        audioUpdatedThisStep = false;
    }

    private void startNativeMenuLevel(com.openggf.game.nativemenu.NativeMenuScreen.Exit exit) {
        nativeMenuFlow.startNativeMenuLevel(exit);
    }

    // ==================== Data Select Methods ====================

    /**
     * Initializes the data select screen mode.
     * Called when the S3K title screen exits (instead of going directly to level).
     */
    private void initializeDataSelectMode() {
        GameMode oldMode = changeGameModeForBoundary(GameMode.DATA_SELECT);
        camera.setX((short) 0);
        camera.setY((short) 0);
        var dataSelect = getDataSelectProviderLazy();
        if (dataSelect != null) {
            dataSelect.initialize();
        }
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }
        fadeManager.startFadeFromBlack(null);
    }

    /**
     * Exits the data select screen.
     * For now, transitions to level loading.
     * This will be enhanced in later tasks to handle slot selection.
     */
    private void exitDataSelect() {
        if (resolveFadeManager().isActive()) {
            return;
        }
        var dataSelect = getDataSelectProviderLazy();
        com.openggf.game.dataselect.DataSelectAction action = com.openggf.game.dataselect.DataSelectAction.none();
        if (dataSelect instanceof com.openggf.game.dataselect.AbstractDataSelectProvider provider) {
            action = provider.consumePendingAction();
        }
        if (action.type() == com.openggf.game.dataselect.DataSelectActionType.NONE
                || dataSelectActionHandler == null) {
            if (dataSelect != null) {
                dataSelect.reset();
            }
            return;
        }
        if (isDataSelectGameplayAction(action.type())) {
            com.openggf.game.dataselect.DataSelectAction pendingAction = action;
            com.openggf.game.dataselect.DataSelectExitTransition transition =
                    dataSelect.exitTransition();
            if (transition.confirmationSfxId() >= 0) {
                audioManager.playSfx(transition.confirmationSfxId());
            }
            audioManager.fadeOutMusic(transition.musicFadeSteps(), transition.musicFadeDelay());
            resolveFadeManager().startFadeToBlack(() -> {
                try {
                    dataSelectActionHandler.accept(pendingAction);
                    if (dataSelect != null) {
                        dataSelect.reset();
                    }
                } catch (RuntimeException e) {
                    restoreDataSelectAfterLaunchFailure(dataSelect, pendingAction, e);
                }
                GameLoopPlcLifecycle.revealDataSelectLaunch(resolveFadeManager(), currentGameMode
                        == GameMode.DATA_SELECT ? null : GameServices.levelOrNull(), transition.revealTerminalNoOpFrames());
            });
            return;
        }
        if (dataSelect != null) {
            dataSelect.reset();
        }
        dataSelectActionHandler.accept(action);
    }

    private void restoreDataSelectAfterLaunchFailure(
            DataSelectProvider dataSelect,
            com.openggf.game.dataselect.DataSelectAction action,
            RuntimeException failure) {
        LOGGER.warning("Data Select launch failed for action " + action.type()
                + " slot " + action.slot() + ": " + failure.getMessage());
        if (currentGameMode != GameMode.DATA_SELECT) {
            setGameMode(GameMode.DATA_SELECT);
        }
        if (dataSelect != null) {
            dataSelect.showLaunchError("Unable to load selected save.");
        }
    }

    private boolean isDataSelectGameplayAction(com.openggf.game.dataselect.DataSelectActionType type) {
        return switch (type) {
            case NO_SAVE_START, NEW_SLOT_START, LOAD_SLOT, CLEAR_RESTART -> true;
            case NONE, DELETE_SLOT -> false;
        };
    }

    /**
     * Gets the data select provider from the current game module.
     */
    public DataSelectProvider getDataSelectProvider() {
        return getDataSelectProviderLazy();
    }

    /**
     * Lazily retrieves the data select provider from the current game module.
     */
    private DataSelectProvider getDataSelectProviderLazy() {
        var gameModule = GameServices.module();
        return gameModule != null ? gameModule.getDataSelectProvider() : null;
    }

    // ==================== Level Select Methods ====================

    /**
     * Initializes the game loop to start directly in level select mode.
     * Called from Engine.init() when LEVEL_SELECT_ON_STARTUP is true.
     * Unlike enterLevelSelect(), this does not require being in LEVEL mode first
     * and does not perform a fade transition.
     */
    public void initializeLevelSelectMode() {
        LOGGER.info("Initializing game in Level Select mode");

        // Ensure the ROM is loaded and audio is initialized before level select
        try {
            var rom = romManager.getRom();
            var gameModule = GameServices.module();

            // Initialize audio system with game module's audio profile
            audioManager.setAudioProfile(gameModule.getAudioProfile());
            audioManager.setRom(rom);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ROM for level select", e);
        }

        GameMode oldMode = changeGameModeForBoundary(GameMode.LEVEL_SELECT);

        // Set camera to origin for level select rendering
        camera.setX((short) 0);
        camera.setY((short) 0);

        // Initialize the level select provider (also loads and caches palettes)
        LevelSelectProvider levelSelect = getLevelSelectProviderLazy();
        if (levelSelect != null) {
            levelSelect.initialize();
        }

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        LOGGER.info("Game initialized in Level Select mode");
    }

    /**
     * Enters the level select screen from level mode.
     * Performs fade-to-black transition before showing level select.
     */
    public void enterLevelSelect() {
        if (currentGameMode != GameMode.LEVEL) {
            return;
        }
        GameModule module = GameServices.module();
        if (module != null && module.suppressesLevelSelect()) {
            return;
        }

        // Don't start another fade if one is already in progress
        FadeManager fadeManager = this.fadeManager;
        if (fadeManager.isActive()) {
            return;
        }

        // Fade out current music
        audioManager.fadeOutMusic();

        // Start fade-to-black, then enter level select when complete
        GameLoopPlcLifecycle.startToBlack(resolveGameplayModeContext(), fadeManager, () -> {
            doEnterLevelSelect();
        });

        LOGGER.info("Starting fade-to-black for Level Select");
    }

    /**
     * Actually enters the level select screen after fade-to-black completes.
     */
    private void doEnterLevelSelect() {
        GameMode oldMode = changeGameModeForBoundary(GameMode.LEVEL_SELECT);

        // Set camera to origin for level select rendering
        camera.setX((short) 0);
        camera.setY((short) 0);

        // Initialize the level select provider
        LevelSelectProvider levelSelect = getLevelSelectProviderLazy();
        if (levelSelect != null) {
            levelSelect.initialize();
        }

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        // Start fade-from-black to reveal the level select
        GameLoopPlcLifecycle.startFromBlack(resolveGameplayModeContext(), fadeManager, null);

        LOGGER.info("Entered Level Select screen");
    }

    /**
     * Exits the level select screen and loads the selected zone/act or special stage.
     */
    private void exitLevelSelect() {
        LevelSelectProvider levelSelect = getLevelSelectProviderLazy();
        if (levelSelect == null) {
            return;
        }

        // Don't start another fade if one is already in progress
        FadeManager fadeManager = this.fadeManager;
        if (fadeManager.isActive()) {
            return;
        }

        if (levelSelect.isSpecialStageSelected()) {
            // Enter special stage
            levelSelect.reset();
            GameMode oldMode = changeGameModeForBoundary(GameMode.LEVEL);

            // Notify listener of mode change
            if (gameModeChangeListener != null) {
                gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
            }

            // Now enter special stage via the normal path
            levelSelect.onGameplayStart();
            enterSpecialStage();
            LOGGER.info("Level select -> Special Stage");

        } else if (levelSelect.isSoundTestSelected()) {
            // Sound test was selected but not a level, just reset
            levelSelect.reset();
            LOGGER.info("Level select sound test (no level transition)");

        } else {
            // Load selected zone/act
            int zone = levelSelect.getSelectedZone();
            int act = levelSelect.getSelectedAct();

            // Reset level select manager
            levelSelect.reset();

            GameServices.gameState().startNewGameFromTitle(); // S1 LevSel_Level -> PlayLevel (sonic.asm:2270-2283)
            levelSelect.onGameplayStart();
            // Fade out level select music
            audioManager.fadeOutMusic();

            // Start fade-to-black, then load level
            GameLoopPlcLifecycle.startToBlack(resolveGameplayModeContext(), fadeManager, () -> {
                doExitLevelSelectToZone(zone, act);
            });

            LOGGER.info("Level select -> Zone " + zone + " Act " + act);
        }
    }

    /**
     * Actually loads the selected zone/act after fade-to-black completes.
     */
    private void doExitLevelSelectToZone(int zone, int act) {
        GameMode oldMode = changeGameModeForBoundary(GameMode.LEVEL);

        // Load the selected zone/act
        try {
            levelManager.loadZoneAndAct(zone, act);
        } catch (IOException e) {
            LOGGER.severe("Failed to load zone " + zone + " act " + act + ": " + e.getMessage());
            throw new RuntimeException("Failed to load zone " + zone + " act " + act, e);
        }

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        // Start fade-from-black to reveal the title card
        GameLoopPlcLifecycle.startFromBlack(resolveGameplayModeContext(), fadeManager, null);

        LOGGER.info("Loaded zone " + zone + " act " + act + " from level select");
    }

    /**
     * Gets the level select provider from the current game module.
     *
     * @return the level select provider, or null if not available
     */
    public LevelSelectProvider getLevelSelectProvider() {
        return getLevelSelectProviderLazy();
    }

    /**
     * Lazily retrieves the level select provider from the current game module.
     */
    private LevelSelectProvider getLevelSelectProviderLazy() {
        var gameModule = GameServices.module();
        if (gameModule != null) {
            return gameModule.getLevelSelectProvider();
        }
        return null;
    }

    // ==================== Level Transition Methods with Fade ====================

    public ContinueScreenProvider getContinueScreenProvider() {
        return continueScreen.provider();
    }

    /**
     * Starts the fade-to-black transition for death respawn.
     */
    private void startRespawnFade() {
        continueScreen.startRespawn(runPolicy(), audioManager::fadeOutMusic);
    }

    /**
     * Starts the fade-to-black transition for next act.
     */
    private void startNextActFade() {
        LOGGER.info("Starting fade-to-black for next act");

        // Fade out current music (ROM: s2.asm:4757 - level entry with title card)
        audioManager.fadeOutMusic();

        // Start fade-to-black, then load next act when complete
        GameLoopPlcLifecycle.startToBlack(resolveGameplayModeContext(), fadeManager, this::doNextAct);
    }

    /**
     * Actually loads the next act after fade-to-black completes.
     */
    private void doNextAct() {
        try {
            levelManager.nextAct();
            activateScheduledPlaybackForLoadedLevel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load next act", e);
        }

        // Start fade-from-black to reveal the title card
        GameLoopPlcLifecycle.startFromBlack(resolveGameplayModeContext(), fadeManager, null);

        LOGGER.info("Loaded next act");
    }

    /**
     * Starts the fade-to-black transition for next zone.
     */
    private void startNextZoneFade() {
        LOGGER.info("Starting fade-to-black for next zone");

        // Fade out current music (ROM: s2.asm:4757 - level entry with title card)
        audioManager.fadeOutMusic();

        // Start fade-to-black, then load next zone when complete
        GameLoopPlcLifecycle.startToBlack(resolveGameplayModeContext(), fadeManager, this::doNextZone);
    }

    /**
     * Actually loads the next zone after fade-to-black completes.
     */
    private void doNextZone() {
        try {
            levelManager.nextZone();
            activateScheduledPlaybackForLoadedLevel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load next zone", e);
        }

        // Start fade-from-black to reveal the title card
        GameLoopPlcLifecycle.startFromBlack(resolveGameplayModeContext(), fadeManager, null);

        LOGGER.info("Loaded next zone");
    }

    /**
     * Starts the fade-to-black transition for a specific zone/act.
     */
    private void startZoneActFade(int zone, int act, int postLoadMusicId) {
        boolean white = GameLoopPlcLifecycle.usesWhiteLevelEntry(GameServices.module(), zone, act);
        LOGGER.info("Starting fade-to-" + (white ? "white" : "black") + " for zone " + zone + " act " + act);

        // GM_Level enters its blocking PaletteFadeOut after the request's
        // completed level iteration, not when an event first writes f_restart
        // (S1 REV01 sonic.asm:3016-3017,2710-2712). An active host owns its
        // explicit retry policy: mid-act retries keep playing, completed-act
        // retries already requested a hold. Other profiles retain caller policy.
        if (!hostedRuns.isActive() && GameServices.module().getLevelInitProfile()
                instanceof com.openggf.game.internal.BlockingLevelEntryFade) {
            levelManager.setLevelInactiveForTransition(true);
        }

        audioManager.fadeOutMusic();

        GameLoopPlcLifecycle.startToLevelEntry(resolveGameplayModeContext(), fadeManager, white,
                () -> doZoneAct(zone, act, postLoadMusicId));
    }

    /**
     * Actually loads the specified zone/act after fade-to-black completes.
     */
    private void doZoneAct(int zone, int act, int postLoadMusicId) {
        boolean restoreSanctuaryOrigin =
                levelManager.isSanctuaryOriginRestorePending(zone, act);
        try {
            // A cutscene may still have a source-zone music fade in flight.
            // Load the destination normally, then issue its requested track
            // last so the old fade cannot win the handoff.
            if (postLoadMusicId >= 0) {
                levelManager.setSuppressNextMusicChange(true);
            }
            // A mid-act retry keeps the replaced act playing under its fade, so its results
            // object can still finish there. This load satisfies any act completion raised
            // before it, as HostedRunController.onLevelReady does a retry request.
            if (hostedRuns.isActive()) {
                levelManager.consumeHostReturnRequest();
                levelManager.consumeNextActRequest();
                levelManager.consumeNextZoneRequest();
                levelManager.consumeCreditsRequest();
            }
            // Only a title owner that models the native fresh title/terrain loop
            // (S3K Level/loc_6310-loc_64DC) holds players behind the title card.
            // Other games place the destination players and reset counters now.
            if (restoreSanctuaryOrigin
                    || !(getTitleCardProviderLazy()
                            instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication)) {
                levelManager.loadZoneAndAct(zone, act);
            } else {
                levelManager.loadZoneAndActAtFreshTitleCardBoundary(zone, act);
            }
            levelIterationAdmission.loadedFreshBoundary(
                    levelManager.hasPendingFreshLevelTransitionBoundary());
            activateScheduledPlaybackForLoadedLevel();
            if (postLoadMusicId >= 0) {
                audioManager.playMusic(postLoadMusicId);
            }
            if (restoreSanctuaryOrigin) {
                var sprite = spriteManager.getSprite(resolveMainCharacterCode());
                if (sprite instanceof AbstractPlayableSprite playable) {
                    restoreBigRingReturn(playable);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load zone " + zone + " act " + act, e);
        }

        // An overlay already visible before spawn is as much an advantage as toggling
        // one on mid-run, so the host hears about it with the level start.
        hostedRuns.onLevelReady(debugOverlayManager.isEnabled(DebugOverlayToggle.OVERLAY));

        if (GameLoopPlcLifecycle.usesWhiteLevelEntry(GameServices.module(), zone, act)) {
            GameLoopPlcLifecycle.startFromWhite(resolveGameplayModeContext(), fadeManager, null);
        } else if (levelManager.hasPendingFreshLevelTransitionBoundary()
                && getTitleCardProviderLazy() instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication boundary
                && boundary.installsImmediateFreshLevelPalette()) {
            fadeManager.clearOverlayForImmediatePaletteLoad();
        } else {
            GameLoopPlcLifecycle.startFromBlack(resolveGameplayModeContext(), fadeManager, null);
        }

        LOGGER.info("Loaded zone " + zone + " act " + act);
    }

    void activateScheduledPlaybackForLoadedLevel() {
        if (TraceSessionLauncher.activateScheduledPlaybackForLoadedLevel(
                playbackDebugManager)) {
            syncPlaybackInputBridge();
        }
    }

    /**
     * Requests a save to the active session slot, if any.
     * Safe to call when no session is active or when the session has no save slot.
     *
     * @param reason the reason triggering this save
     */
    private void requestSessionSave(SaveReason reason) {
        SessionSaveRequests.requestCurrentSessionSave(reason);
    }

    public void requestSaveForCurrentSession(SaveReason reason) {
        requestSessionSave(reason);
    }

    /**
     * Gets the title card provider (for rendering).
     * 
     * @return the title card provider
     */
    public TitleCardProvider getTitleCardProvider() {
        return getTitleCardProviderLazy();
    }

    /**
     * Gets the current results screen object (for rendering).
     * 
     * @return the results screen object, or null if not in results mode
     */
    public ResultsScreen getResultsScreen() {
        return specialStageLifecycle.resultsScreen();
    }

    /** Declared-setup hook for capture tools: the special-stage debug completion without a key read. */
    public void debugCompleteSpecialStageWithEmerald() {
        debugShortcuts.debugCompleteSpecialStageWithEmerald();
    }

    public SpecialStageProvider getActiveSpecialStageProvider() {
        return specialStageLifecycle.provider();
    }

    public Integer recordedSpecialStageIdentity(GameMode observedMode) {
        return specialStageLifecycle.recordedSpecialStageIdentity(observedMode);
    }

    /** Genuine virtual completion seam used by immediate and delayed results entry. */
    void doEnterResultsScreen() {
        specialStageLifecycle.doEnterResultsScreen();
    }

    /** Transition SFX is emitted by the owner that starts the fade. */
    static boolean shouldPlaySpecialStageEntrySfx(boolean transitionSfxAlreadyPlayed) {
        return GameLoopSpecialStageLifecycle.shouldPlaySpecialStageEntrySfx(transitionSfxAlreadyPlayed);
    }

    /** Typed live slots keep capture-time and callback-time title observations distinct. */
    private final class TitleCardHost implements GameLoopTitleCardLifecycle.Host {
        @Override public LevelManager level() { return levelManager; }
        @Override public SpriteManager sprites() { return spriteManager; }
        @Override public Camera camera() { return camera; }
        @Override public InputHandler input() { return inputHandler; }
        @Override public GameModule module() { return GameServices.module(); }
        @Override public GameMode currentMode() { return currentGameMode; }
        @Override public boolean endOfLevelActive() { return GameServices.gameState().isEndOfLevelActive(); }
        @Override public String mainCharacterCode() { return resolveMainCharacterCode(); }
        @Override public AbstractPlayableSprite mainPlayable() { return resolveMainPlayableSprite(); }
        @Override public void lockTitleControls(boolean locked) { applyTitleCardControlLock(locked); }
        @Override public void restorePendingShield(AbstractPlayableSprite playable) { applyPendingBonusStageShieldRestore(playable); }
        @Override public GameMode changeMode(GameMode mode) { return changeGameModeForBoundary(mode); }
        @Override public GameMode changeModeWithoutRewindBoundary(GameMode mode) { return GameLoop.this.changeGameModeWithoutRewindBoundary(mode); }
        @Override public void markBonusEntryBoundary() { markBonusEntryNonRewindableBoundary(); }
        @Override public boolean returningFromSpecialStage() { return specialStageLifecycle.returningFromSpecialStage(); }
        @Override public void completeTitleReturn() { specialStageLifecycle.completeTitleReturn(); }
        @Override public void applyDeferredBonusStageSetup() { bonusSession.applyDeferredBonusStageSetup(); }
        @Override public void syncPlaybackInput() { syncPlaybackInputBridge(); }
        @Override public void admitDestination() { TraceSessionLauncher.admitRunDestinationBeforeProductionIfActive(currentGameMode); }
        @Override public void recordLevelRelease() { titleCardReleasedIntoLevelThisIteration = true; }
        @Override public void notifyModeChanged(GameMode oldMode) {
            if (gameModeChangeListener != null) gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }
    }

    /** Typed live slots preserve capture versus callback-time reads across rebinding. */
    private final class SpecialStageHost implements GameLoopSpecialStageLifecycle.Host {
        @Override public LevelManager level() { return levelManager; }
        @Override public SpriteManager sprites() { return spriteManager; }
        @Override public Camera camera() { return camera; }
        @Override public FadeManager fade() { return fadeManager; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public GameModule module() { return GameServices.module(); }
        @Override public GameMode currentMode() { return currentGameMode; }
        @Override public GameplayRunPolicy runPolicy() { return GameLoop.this.runPolicy(); }
        @Override public SonicConfigurationService config() { return configService; }
        @Override public InputHandler input() { return inputHandler; }
        @Override public PlaybackDebugManager playback() { return playbackDebugManager; }
        @Override public GameplayModeContext boundGameplayContext() { return gameplayMode; }
        @Override public GameplayModeContext resolvedGameplayContext() { return resolveGameplayModeContext(); }
        @Override public PlcLifecycleFrame plcFrame() { return activePlcLifecycleFrame; }
        @Override public LiveRewindManager rewind() { return liveRewindManager; }
        @Override public GameLoopSpecialStageInput pacingInput() { return specialStagePacingInput; }
        @Override public SpecialStageProvider activeProvider() { return getActiveSpecialStageProvider(); }
        @Override public String mainCharacterCode() { return resolveMainCharacterCode(); }
        @Override public TitleCardProvider titleCard() { return getTitleCardProviderLazy(); }
        @Override public GameMode changeMode(GameMode mode) { return changeGameModeForBoundary(mode); }
        @Override public boolean debugKeyPressed(int key) { return isUnmodifiedDebugKeyPressed(key); }
        @Override public boolean rewindable() { return isSpecialStageRewindable(); }
        @Override public Runnable debugCompletionCallback() { return debugShortcuts::debugCompleteSpecialStageWithEmerald; }
        @Override public Runnable debugFailureCallback() { return debugShortcuts::debugFailSpecialStage; }
        @Override public void deregisterSpecialStageAdapter() { GameLoop.this.deregisterSpecialStageAdapter(); }
        @Override public void doEnterSpecialStage(SpecialStageProvider provider, int stage, boolean black) {
            GameLoop.this.doEnterSpecialStage(provider, stage, black);
        }
        @Override public void doEnterResultsScreen() { GameLoop.this.doEnterResultsScreen(); }
        @Override public void lockTitleControls(boolean locked) { applyTitleCardControlLock(locked); }
        @Override public void restoreBigRingReturn(AbstractPlayableSprite playable) { GameLoop.this.restoreBigRingReturn(playable); }
        @Override public void notifyModeChanged(GameMode oldMode) {
            if (gameModeChangeListener != null) gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }
        @Override public void requestSessionSave(SaveReason reason) { GameLoop.this.requestSessionSave(reason); }
    }

    /** Live cached-manager reads preserve callback rebinding; published and resolved contexts differ. */
    private final class BonusHost implements GameLoopBonusStageSession.Host {
        @Override public LevelManager level() { return levelManager; }
        @Override public SpriteManager sprites() { return spriteManager; }
        @Override public Camera camera() { return camera; }
        @Override public WaterSystem water() { return waterSystem; }
        @Override public FadeManager fade() { return fadeManager; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public GameModule module() { return GameServices.module(); }
        @Override public GameMode currentMode() { return currentGameMode; }
        @Override public GameplayRunPolicy runPolicy() { return GameLoop.this.runPolicy(); }
        @Override public GameplayModeContext publishedGameplayContext() { return SessionManager.getCurrentGameplayMode(); }
        @Override public GameplayModeContext resolvedGameplayContext() { return resolveGameplayModeContext(); }
        @Override public String mainCharacterCode() { return resolveMainCharacterCode(); }
        @Override public AbstractPlayableSprite mainPlayable() { return resolveMainPlayableSprite(); }
        @Override public TitleCardProvider titleCard() { return getTitleCardProviderLazy(); }
        @Override public GameMode enterBonusTitleCardAfterLevelLoadBoundary() { return GameLoop.this.enterBonusTitleCardAfterLevelLoadBoundary(); }
        @Override public GameMode changeMode(GameMode mode) { return changeGameModeForBoundary(mode); }
        @Override public void titleDestination(PostTitleCardDestination destination) { titleCardLifecycle.destination(destination); }
        @Override public void lockTitleControls(boolean locked) { applyTitleCardControlLock(locked); }
        @Override public void notifyModeChanged(GameMode oldMode) {
            if (gameModeChangeListener != null) gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }
    }

    /** Reads cached slots at the original expression points, including after callbacks. */
    private final class EndingHost implements GameLoopEndingSession.Host {
        @Override public LevelManager level() { return levelManager; }
        @Override public SpriteManager sprites() { return spriteManager; }
        @Override public Camera camera() { return camera; }
        @Override public WaterSystem water() { return waterSystem; }
        @Override public FadeManager fade() { return fadeManager; }
        @Override public GameModule module() { return GameServices.module(); }
        @Override public InputHandler input() { return inputHandler; }
        @Override public String mainCharacterCode() { return resolveMainCharacterCode(); }
        @Override public GameMode currentMode() { return currentGameMode; }
        @Override public GameplayModeContext boundGameplayContext() { return gameplayMode; }
        @Override public GameplayModeContext resolvedGameplayContext() { return resolveGameplayModeContext(); }
        @Override public void setGameMode(GameMode mode) { GameLoop.this.setGameMode(mode); }
        @Override public void requestSessionSave(SaveReason reason) { GameLoop.this.requestSessionSave(reason); }
        @Override public TitleScreenProvider titleScreen() { return getTitleScreenProviderLazy(); }
        @Override public void returnToTitleScreen() { continueScreen.returnToTitleScreen(); }
    }

    /**
     * Gets the ending provider (for rendering in Engine.java).
     */
    public EndingProvider getEndingProvider() {
        return endingSession.provider();
    }
}
