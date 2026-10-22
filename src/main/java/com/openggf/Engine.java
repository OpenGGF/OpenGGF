package com.openggf;

import com.openggf.architecture.CompositionRoot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.mods.code.ModClassLoaderFactory;
import com.openggf.mods.code.ModClassResolver;
import com.openggf.mods.code.ModRuntime;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.ModLevelExportStagingValidator;
import com.openggf.mods.code.EffectiveCatalogPatchEnablement;
import com.openggf.game.*;
import com.openggf.graphics.*;
import com.openggf.graphics.pipeline.RenderOrderRecorder;
import com.openggf.version.AppVersion;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import com.openggf.control.InputHandler;
import com.openggf.editor.EditorInputHandler;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.persistence.EditorSaveManager;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.editor.render.EditorOverlayRenderer;
import com.openggf.editor.project.EditorProjectDocument;
import com.openggf.editor.project.EditorProjectWorkspace;
import com.openggf.editor.project.EditorProjectRuntimeModule;
import com.openggf.editor.project.EditorProjectMenuRenderer;
import com.openggf.audio.AudioManager;
import com.openggf.audio.DebugFailAfterFramesAudioHandle;
import com.openggf.audio.LWJGLAudioBackend;
import com.openggf.io.ModInputLimits;
import com.openggf.capture.*;
import com.openggf.camera.Camera;
import com.openggf.configuration.FrameRateResolver;
import com.openggf.configuration.KeyChord;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameId;
import com.openggf.game.launch.SuspendedSettings;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.DebugOption;
import com.openggf.debug.DebugColor;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.debug.DebugRenderer;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.DebugState;
import com.openggf.game.ShieldType;
import com.openggf.level.LevelManager;
import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorModeContext;
import com.openggf.game.session.EditorPlaytestStash;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.data.RomManager;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.GameplayTeamAvailability;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.startup.DonatedDataSelectWarmupTask;
import com.openggf.data.Rom;
import com.openggf.physics.Direction;
import com.openggf.graphics.color.DisplayColorProfileController;
import com.openggf.graphics.shaderlib.DisplayShaderController;
import com.openggf.graphics.shaderlib.DisplayShaderLibrary;
import com.openggf.graphics.shaderlib.DisplayShaderPickerController;
import com.openggf.graphics.shaderlib.DisplayShaderPipeline;
import com.openggf.graphics.shaderlib.DisplayShaderPresetLoader;
import com.openggf.graphics.shaderlib.DisplayShaderPresetRef;
import com.openggf.graphics.shaderlib.DisplayShaderSelectionModel;
import com.openggf.graphics.shaderlib.RewindVhsEffectPass;
import com.openggf.graphics.shaderlib.ShaderPhase;
import com.openggf.render.EngineRenderDispatcher;
import com.openggf.util.RetroArchGlslShaderPackDownloader;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.Set;
import java.util.logging.Logger;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Controls the game.
 *
 * @author james
 */
@CompositionRoot
public class Engine implements com.openggf.graphics.RenderProjection {
	private static final Logger LOGGER = Logger.getLogger(Engine.class.getName());
	public static final String RESOURCES_SHADERS_PIXEL_SHADER_GLSL = "shaders/shader_the_hedgehog.glsl";
	private final SonicConfigurationService configService;
	private SpriteManager spriteManager;
	private final GraphicsManager graphicsManager;
	private final AudioManager audioManager;
	private boolean audioBackendInitialized;
	private boolean configuredHeadlessSession;
	private com.openggf.audio.AudioBackend configuredHeadlessBackend;
	private final RomManager romManager;
	private final RomDetectionService romDetectionService;
	private final CrossGameFeatureProvider crossGameFeatureProvider;
	private final DebugOverlayManager debugOverlayManager;
	private final PlaybackDebugManager playbackDebugManager;
	private final ModuleResolutionService moduleResolutionService;

	private Camera camera;
	private DebugRenderer debugRenderer;
	private final PerformanceProfiler profiler;

	private final GameLoop gameLoop;
	private final EngineRenderDispatcher renderDispatcher = new EngineRenderDispatcher();
	private final EngineRenderDispatcher.ClearActions clearActions = new EngineClearActions();
	private final EngineRenderDispatcher.DrawActions drawActions = new EngineDrawActions();
	private final LevelEditorController levelEditorController = new LevelEditorController();
	private EditorSaveManager editorSaveManager;
	private final EditorInputHandler editorInputHandler;
	private final EditorOverlayRenderer editorOverlayRenderer;
	/**
	 * Window-resolution editor UI: a separate editor build if present, else the built-in
	 * controller-first HUD; null for the in-game chrome.
	 */
	private com.openggf.editor.EditorWindowChrome editorChrome = com.openggf.editor.EditorWindowChromes.select(
			com.openggf.editor.hud.EditorHudRuntime::new);
	private boolean editorChromeActive;
	private final EditorProjectWorkspace editorProjects;
	private final com.openggf.editor.stageplay.StageplayWorkspace stageplay;
	private final com.openggf.stageplay.editor.StageplayEditorSession stageplaySession;
	private Path stageplayInitialAssetRoot;
	private final EditorProjectMenuRenderer editorProjectMenuRenderer = new EditorProjectMenuRenderer();
	private final ContentImportScreen contentImportScreen;
	private final ContentImportScreenRenderer contentImportRenderer = new ContentImportScreenRenderer();
	private final ContentImportWindowBridge contentImportWindow;
	private boolean contentImportShutdown;
	private boolean quitAfterContentInstall;
	private EditorProjectDocument shownEditorProject;
	private GameModule editorProjectBaseModule;
	private GameModule editorProjectRootModule;
	private GameDataSource editorProjectDataSource;
	private boolean editorProjectPlaytestActive;
	private EditorQuitView editorQuitView;
	private record EditorQuitView(GameMode mode, Camera camera, short x, short y, short renderX, short renderY) { }
	// Match the rest of the debug overlay — no drop shadow.
	private final PixelFontTextRenderer traceHudTextRenderer =
		new PixelFontTextRenderer(PixelFontVariant.PIXEL_FONT_NO_SHADOW);
	private final PixelFontTextRenderer pauseTextRenderer =
		new PixelFontTextRenderer(PixelFontVariant.PIXEL_FONT_NO_SHADOW);
	private final PixelFontTextRenderer menuOverlayTextRenderer = PixelFontTextRenderer.menu();
	private static final int ESCAPE_TOAST_REST_Y = 6;
	private final PixelFontTextRenderer liveCaptureTextRenderer =
		new PixelFontTextRenderer(PixelFontVariant.PIXEL_FONT_NO_SHADOW);
	private static final String USER_PAUSE_INDICATOR_TEXT = "PAUSED";
	private static final int USER_PAUSE_INDICATOR_MARGIN = 8;
	private static final long USER_PAUSE_INDICATOR_HALF_PERIOD_NANOS = 500_000_000L;
	private static final long USER_PAUSE_INDICATOR_PERIOD_NANOS = USER_PAUSE_INDICATOR_HALF_PERIOD_NANOS * 2L;
	private static final long USER_PAUSE_SCREENSHOT_HIDE_NANOS = 1_000_000_000L;
	private DisplayColorProfileController displayColorProfileController;
	private DisplayShaderController displayShaderController;
	private DisplayShaderPickerController displayShaderPickerController;
	private RewindVhsEffectPass rewindVhsEffectPass;
	private int displayShaderFrameCounter;
	private volatile boolean displayShaderPackDownloadInProgress;
	private volatile boolean displayShaderPackRescanRequested;
	private volatile String displayShaderPackStatus;

	private static volatile DebugState debugState = DebugState.NONE;
	private static volatile DebugOption debugOption = DebugOption.A;

	public static DebugState getDebugState() { return debugState; }
	public static DebugOption getDebugOption() { return debugOption; }
	public static void setDebugOption(DebugOption option) { debugOption = option; }

	private double realWidth;
	private double realHeight;

	// Current projection width - can be changed for H32/H40 mode switching
	// H40 mode (normal levels): 320 pixels wide
	// H32 mode (special stages): 256 pixels wide
	private double projectionWidth;

	private boolean debugViewEnabled;

	private LevelManager levelManager;

	// The active session-owned gameplay mode set during initializeGame().
	private GameplayModeContext gameplayMode;

	// Pre-allocated list for results screen rendering
	private final java.util.List<GLCommand> resultsCommands = new java.util.ArrayList<>(64);

	// GLFW window handle
	private long window;
	private boolean glfwInitialized;

	// Window dimensions
	private int windowWidth;
	private int windowHeight;

	record ResolvedDisplayDimensions(int pixelWidth, int pixelHeight, int windowWidth, int windowHeight) {
	}

	record FramebufferDimensions(int width, int height) {
	}

	record PauseIndicatorPlacement(int x, int y) {
	}

	enum ConfiguredStartupBranch {
		LEGAL_DISCLAIMER,
		MASTER_TITLE,
		GAME
	}

	enum LiveCapturePresentationState {
		NORMAL,
		MODAL_SHADER_PICKER,
		PAUSED,
		FRAME_STEP,
		REWIND
	}

	static final class LiveCaptureFailureTransitionReporter {
		private static final Throwable UNKNOWN_FAILURE =
				new IllegalStateException("Live capture entered FAILED without a cause");
		private final Consumer<Throwable> warningSink;
		private final Set<Throwable> reported =
				Collections.newSetFromMap(new IdentityHashMap<>());

		LiveCaptureFailureTransitionReporter(Consumer<Throwable> warningSink) {
			this.warningSink = Objects.requireNonNull(warningSink, "warningSink");
		}

		void beginAttempt() {
			reported.clear();
		}

		void observe(LiveCaptureController controller) {
			Objects.requireNonNull(controller, "controller");
			if (controller.state() != LiveCaptureController.State.FAILED) {
				reported.clear();
				return;
			}
			Throwable failure = controller.lastFailure();
			if (failure == null) {
				failure = UNKNOWN_FAILURE;
			}
			if (reported.add(failure)) {
				warningSink.accept(failure);
			}
		}
	}

	private boolean overlayStateReady = false;

	// Input handler for keyboard input
	private InputHandler inputHandler;

	// Master title screen (game selection before ROM loading)
	private MasterTitleScreen masterTitleScreen;
	private SuspendedGame suspendedGame;
	private SuspendedGame.Pending pendingSuspend;
	private SuspendedGame.Thumbnail pendingSuspendThumbnail;
	private boolean suspendThumbnailRequested;

	// Legal disclaimer screen (pre-ROM disclaimer)
	private LegalDisclaimerScreen legalDisclaimerScreen;
	private com.openggf.game.NativeModNoticeScreen nativeModNoticeScreen;

	// Viewport parameters for aspect-ratio-correct rendering
	private int viewportX = 0;
	private int viewportY = 0;
	private int viewportWidth = 0;
	private int viewportHeight = 0;

	private DisplayWindowController displayWindowController;
	private StageplayWindowController stageplayWindowController;
	private String displayScaling;

	// JOML matrices for projection - accessible for shader uniforms
	private final org.joml.Matrix4f projectionMatrix = new org.joml.Matrix4f();
	private final float[] matrixBuffer = new float[16];

	// Static instance for singleton access
	private static Engine instance;
	private ModRuntime modRuntime = ModRuntime.empty();
	private final boolean compiledModsSupported = !isNativeImage();
	private java.util.function.BiConsumer<com.openggf.game.patch.ResolutionResult.LaunchAborted, Boolean>
			patchLaunchAbortHandler = this::handlePatchLaunchAbort;

	// Frame timing
	private int targetFps;
	private final LiveCaptureChord liveCaptureChord = new LiveCaptureChord();
	private final LiveCaptureController liveCaptureController;
	private final AsyncScreenshotWriter screenshotWriter = new AsyncScreenshotWriter();
	private final LiveCapturePresentationCoordinator liveCapturePresentation;
	private final LiveCaptureIndicatorRenderer liveCaptureIndicator;
	/** How long a recording-interrupted notice stays on screen. */
	private static final long LIVE_CAPTURE_NOTICE_NANOS = 3_000_000_000L;
	private LiveCaptureController.Interruption liveCaptureInterruption;
	private long liveCaptureInterruptionExpiryNanos;
	private final LiveCaptureFailureTransitionReporter liveCaptureFailureReporter =
			new LiveCaptureFailureTransitionReporter(failure ->
					LOGGER.log(java.util.logging.Level.WARNING,
							"Live viewport recording failed", failure));
	private long lastFrameTime;
	private boolean paused = false;
	private long userPauseIndicatorHiddenUntilNanos;

	private DebugRenderer getDebugRenderer() {
		if (debugRenderer == null) {
			debugRenderer = new DebugRenderer();
		}
		return debugRenderer;
	}

	public Engine() {
		this(EngineServices.current());
	}

	public Engine(EngineContext engineServices) {
		engineServices = Objects.requireNonNull(engineServices, "engineServices");
		EngineServices.configure(engineServices);
		this.configService = engineServices.configuration();
		this.graphicsManager = engineServices.graphics();
		this.audioManager = engineServices.audio();
		this.romManager = engineServices.roms();
		this.romDetectionService = engineServices.romDetection();
		this.crossGameFeatureProvider = engineServices.crossGameFeatures();
		this.debugOverlayManager = engineServices.debugOverlay();
		this.playbackDebugManager = engineServices.playbackDebug();
		this.moduleResolutionService = engineServices.moduleResolutionService();
		this.profiler = engineServices.profiler();
		this.editorSaveManager = new EditorSaveManager(Path.of("saves"), this::effectiveObjectKeyExists,
				finding -> LOGGER.warning(finding.code() + ": " + finding.message()));
		this.graphicsManager.setPerformanceProfiler(profiler);
		this.editorOverlayRenderer = new EditorOverlayRenderer(levelEditorController, graphicsManager);
		this.gameLoop = new GameLoop(engineServices);
		this.gameLoop.setCharacterAvailabilitySupplier(() -> modRuntime.characterAvailability());
		this.gameLoop.setEditorStateSyncHandler(this::syncEditorState);
		this.gameLoop.setMasterTitleScreenSupplier(() -> masterTitleScreen);
		this.gameLoop.suspension.setHost(new GameLoopSuspension.Host() {
			@Override public boolean prepareSuspend(GameMode mode) { return Engine.this.prepareSuspend(mode); }
			@Override public void suspend() { suspendToMasterTitle(); }
			@Override public void resume() { resumeSuspendedGame(); }
		});
		this.gameLoop.setMasterTitleExitHandler(this::exitMasterTitleScreen);
		this.gameLoop.setStandaloneMasterTitleExitHandler(this::exitStandaloneMasterTitle);
		this.gameLoop.setLegalDisclaimerScreenSupplier(() -> legalDisclaimerScreen);
		this.gameLoop.setLegalDisclaimerExitHandler(this::exitLegalDisclaimer);
		this.gameLoop.setNativeModNoticeScreenSupplier(() -> nativeModNoticeScreen);
		this.gameLoop.setNativeModNoticeExitHandler(this::exitNativeModNotice);
		this.gameLoop.setDataSelectActionHandler(this::launchGameplayFromDataSelect);
		this.gameLoop.setReturnToMasterTitleHandler(this::returnToMasterTitleScreen);
		this.gameLoop.setMasterTitleLaunchFailureHandler(this::rollbackLaunchSessionCachedConfig);
		this.gameLoop.setApplicationExitHandler(this::requestApplicationExit);
		this.realWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
		this.realHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
		this.projectionWidth = realWidth;
		this.debugViewEnabled = configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
		this.windowWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH);
		this.windowHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT);
		this.targetFps = resolveTargetFps(configService);
		this.liveCaptureController = createLiveCaptureController();
		this.liveCapturePresentation = new LiveCapturePresentationCoordinator(liveCaptureController);
		this.liveCaptureIndicator = new LiveCaptureIndicatorRenderer(
				this::drawLiveCaptureDot,
				(text, x, y, red, green, blue, alpha, fontScale) ->
						liveCaptureTextRenderer.drawShadowedText(
								text, x, y,
								new DebugColor(Math.round(red * 255f),
										Math.round(green * 255f),
										Math.round(blue * 255f),
										Math.round(alpha * 255f)),
								fontScale),
				new LiveCaptureIndicatorRenderer.TextMeasurer() {
					@Override
					public int width(String text, float fontScale) {
						return liveCaptureTextRenderer.measureWidth(text, fontScale);
					}

					@Override
					public int height(float fontScale) {
						return liveCaptureTextRenderer.lineHeight(fontScale);
					}
				});
		this.editorInputHandler = new EditorInputHandler(
				levelEditorController, () -> camera, () -> graphicsManager, this::saveCurrentEditorLevel,
				this::exportCurrentEditorLevel);
		editorInputHandler.setFrontEnd(new EditorInputHandler.FrontEnd() {
			public boolean update(InputHandler input) { return updateEditorFrontEnd(input); }
			public void finishPendingEdits() { if (editorChrome != null) editorChrome.finishPendingEdits(); }
		});
		this.editorProjects = new EditorProjectWorkspace(new EditorProjectWorkspace.Host() {
			public GameModule selectGame(GameId game) throws IOException { return selectEditorProjectGame(game); }
			public GameModule currentModule() { return editorProjectBaseModule != null ? editorProjectBaseModule : GameServices.module(); }
			public Level loadLibrary(int zone, int act) throws IOException { return loadEditorAssetLibrary(zone, act); }
			public String sourceFingerprint() {
				var world = SessionManager.getCurrentWorldSession();
				return world == null ? "unknown" : world.getDataSource().identity();
			}
			public EditorCursorState cursor() { return levelEditorController.worldCursor(); }
			public void finishPendingEdits() { editorInputHandler.finishPendingEdits(); }
			public void showDocument(EditorProjectDocument document) throws IOException { showEditorProject(document); }
			public void playtest(EditorProjectDocument document, boolean fromCursor) throws IOException { startEditorProjectPlaytest(document, fromCursor); }
			public void closeDocument() { returnToMasterTitleScreen(); shownEditorProject = null; }
			public void menuClosed(EditorProjectDocument document) {
				if (editorQuitView != null) { restoreEditorQuitView(); return; }
				if (editorProjectPlaytestActive) return;
				var world = SessionManager.getCurrentWorldSession();
				boolean restoreDocument = document != null && (getCurrentGameMode() != GameMode.EDITOR
						|| world == null || world.getCurrentLevel() != document.level());
				boolean restoreTitle = document == null && getCurrentGameMode() == GameMode.MASTER_TITLE_SCREEN
						&& masterTitleScreen == null;
				if (!restoreDocument && !restoreTitle) return;
				try { if (restoreDocument) showEditorProject(document); else returnToMasterTitleScreen(); }
				catch (IOException | RuntimeException failure) { reportEditorProjectFailure(failure); }
			}
			public void execute(com.openggf.editor.EditorCommand command) { levelEditorController.executeCommand(command); }
			public void status(String message) { levelEditorController.setStatus(message); }
			public void metadataChanged(EditorProjectDocument document) { configureEditorProjectSpawnEditing(document); }
		});
		levelEditorController.setModifiedStateSupplier(() -> editorProjects.document() != null
				? editorProjects.document().isModified()
				: levelEditorController.currentLevel() != null && levelEditorController.currentLevel().isModifiedSinceLastSave());
		levelEditorController.setCanvasNavigationSupplier(() -> editorProjects.document() != null || shownEditorProject != null);
		this.stageplaySession = new com.openggf.stageplay.editor.StageplayEditorSession(
				() -> editorProjectBaseModule != null ? editorProjectBaseModule : GameServices.module(), romManager, audioManager);
		this.stageplay = new com.openggf.editor.stageplay.StageplayWorkspace(new com.openggf.editor.stageplay.StageplayWorkspace.Host() {
			public com.openggf.stageplay.StageplayDocument initialDocument() { return initialStageplayDocument(); }
			public Path initialAssetRoot() {
				if (stageplayInitialAssetRoot != null) return stageplayInitialAssetRoot;
				var project = editorProjects.document();
				return project != null && project.path() != null ? project.path().toAbsolutePath().getParent() : Path.of("projects").toAbsolutePath();
			}
			public void finishPendingEdits() { editorInputHandler.finishPendingEdits(); }
			public void validate(com.openggf.stageplay.StageplayDocument document, Path root) throws IOException { stageplaySession.validate(document, root); }
			public com.openggf.stageplay.StageplayTimeline.Frame resolvePreview(com.openggf.stageplay.StageplayDocument document, Path root, int frame) throws IOException { return stageplaySession.resolvePreview(document, root, frame); }
			public void refreshResources() { stageplaySession.release(); }
			public void play(com.openggf.stageplay.StageplayDocument document, Path root, int frame) throws IOException { stageplaySession.play(document, root, frame); }
			public void present(com.openggf.stageplay.StageplayDocument document, int frame) { stageplaySession.present(document, frame); }
			public void stop() { stageplaySession.stop(); }
			public void export(com.openggf.stageplay.StageplayDocument document, Path source, Path output) throws IOException { stageplaySession.export(document, source, output); }
		});
		this.contentImportScreen = new ContentImportScreen(new ContentImportService(Path.of("mods"), () ->
				ModSubsystem.current().processCatalog().scanned().stream()
						.filter(com.openggf.mods.ModDescriptor.class::isInstance)
						.map(com.openggf.mods.ModDescriptor.class::cast)
						.map(descriptor -> descriptor.manifest().id()).collect(java.util.stream.Collectors.toSet())),
				new ContentImportScreen.Host() {
					public void openProject(EditorProjectDocument candidate) { editorProjects.openProject(candidate); }
					public void importLevel(com.openggf.mods.code.ModLevelDefinition level, GameId game) {
						editorProjects.importLevel(level, game);
					}
					public boolean allowedToInstallMods() {
						return getCurrentGameMode() == GameMode.MASTER_TITLE_SCREEN
								&& ModSubsystem.current().policy().mayScanAtBoot();
					}
					public void modInstalled(Path installed) {
						reloadExternalContentAtTitle();
						TitleContentImportAccess.openMods(masterTitleScreen);
					}
					public void closed() { if (!contentImportShutdown && editorQuitView == null) editorProjects.restoreView(); }
				});
		this.contentImportWindow = new ContentImportWindowBridge(contentImportScreen::drop,
				this::contentImportBlockedReason, message -> {
					LOGGER.info(message);
					levelEditorController.setStatus(message);
				});

        com.openggf.editor.EditorCommandPalette.forController(levelEditorController)
                .setHostActions(this::toggleEditorPlaytestMode, this::startGameplayFromBeginning);
		var editorCommands = new ArrayList<>(editorProjects.commands());
		editorCommands.add(new com.openggf.editor.EditorCommandPalette.HostCommand("Stageplay workspace (F4)", stageplay::activate));
		editorCommands.add(new com.openggf.editor.EditorCommandPalette.HostCommand("Import content", this::openContentImport));
		com.openggf.editor.EditorCommandPalette.forController(levelEditorController).setHostCommands(editorCommands);

		// Set up game mode change listener to update projection width
		gameLoop.setGameModeChangeListener((oldMode, newMode) -> {
			if (oldMode == GameMode.EDITOR && newMode != GameMode.EDITOR) stageplay.stop();
			if (newMode == GameMode.LEVEL && GameServices.hasRuntime())
                applyRequiredDisplayAspect(GameServices.module());
            // Resolve module-selected dimensions before the title hands off.
			projectionWidth = realWidth;
		});
        gameLoop.setNativeGameplayModePublisher(this::bindGameplayMode);
		gameLoop.setEditorInputHandler(editorInputHandler);
		gameLoop.setEditorProjectInputHandler(this::updateEditorProjectMenus);
		gameLoop.setEditorProjectPlaytestSupplier(() -> editorProjectPlaytestActive);
		gameLoop.setEditorPlaytestToggleHandler(this::toggleEditorPlaytestMode);
		gameLoop.setEditorFreshStartHandler(this::startGameplayFromBeginning);

		instance = this;
	}

	private boolean updateEditorFrontEnd(InputHandler input) {
		boolean owned = editorChromeActive && editorChrome.update(input);
		return stageplay.tick(input) || owned;
	}

	private boolean updateEditorProjectMenus(InputHandler input) {
		if (contentImportScreen.isOpen()) return contentImportScreen.update(input);
		if (stageplay.menu().isOpen()) {
			stageplay.tick(input);
			if (!stageplay.menu().isOpen() && !editorProjects.menu().isOpen() && editorQuitView != null) restoreEditorQuitView();
			return true;
		}
		return editorProjects.update(input);
	}

	private com.openggf.stageplay.StageplayDocument initialStageplayDocument() {
		var project = editorProjects.document();
		stageplayInitialAssetRoot = Path.of("projects").toAbsolutePath().normalize();
		String game = project != null ? project.metadata().gameId().code() : GameServices.module().getGameId().code();
		int zone = project != null ? project.metadata().sourceZone() : levelManager.getCurrentZone();
		int act = project != null ? project.metadata().sourceAct() : levelManager.getCurrentAct();
		var document = com.openggf.stageplay.StageplayDocument.empty(game, zone, act,
				camera == null ? 0 : camera.getX(), camera == null ? 0 : camera.getY(), (int) realWidth, (int) realHeight);
		if (project != null) {
			Path setPath = project.path();
			if (setPath == null || project.isModified()) {
				// Snapshot unsaved terrain without changing the level editor's path or savepoint.
				stageplayInitialAssetRoot = stageplayInitialAssetRoot.resolve("stageplay-" + java.util.UUID.randomUUID());
				setPath = stageplayInitialAssetRoot.resolve("set.ogfproject");
				try {
					java.nio.file.Files.createDirectories(stageplayInitialAssetRoot);
					new com.openggf.editor.project.EditorProjectStore().save(setPath,
							EditorProjectDocument.create(project.metadata(), project.snapshot()));
				} catch (IOException failure) { throw new java.io.UncheckedIOException("Cannot snapshot the loaded Stageplay set", failure); }
			} else stageplayInitialAssetRoot = setPath.toAbsolutePath().getParent();
			var source = document.set();
			document = document.withSet(new com.openggf.stageplay.StageplayDocument.SetSource(game, zone, act,
					source.romSha1(), setPath.getFileName().toString()));
		}
		return document;
	}

	static int resolveTargetFps(SonicConfigurationService config) {
		return FrameRateResolver.effective(config);
	}

	private LiveCaptureController createLiveCaptureController() {
		String ffmpeg = FfmpegEncoder.findFfmpeg().map(Path::toString).orElse("ffmpeg");
		LiveCaptureRecorderFactory recorderFactory =
				new LiveCaptureRecorderFactory(configService, Clock.systemUTC(), ffmpeg);
		ExecutorService finalizer = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "live-capture-finalizer");
			thread.setDaemon(true);
			return thread;
		});
		return new LiveCaptureController(new LiveCaptureController.Dependencies(
				frameRate -> DebugFailAfterFramesAudioHandle.maybeWrap(
						audioManager.beginLiveCaptureAudio(frameRate),
						resolveLiveCaptureAudioFailAfterFrames()),
				audioManager::outputSampleRate,
				GlPboFrameGrabber::create,
				recorderFactory::create,
				finalizer,
				Duration.ofSeconds(10)));
	}

	static int resolveLiveCaptureAudioFailAfterFrames() {
		String raw = System.getProperty(
				"openggf.debug.liveCaptureAudioFailAfterFrames", "-1");
		try {
			int value = Integer.parseInt(raw);
			if (value >= -1) return value;
		} catch (NumberFormatException ignored) {
			// Warn below using the same safe-disabled behavior.
		}
		LOGGER.warning("Invalid openggf.debug.liveCaptureAudioFailAfterFrames='"
				+ raw + "'; failure injection is disabled");
		return -1;
	}

	static String buildWindowTitle() {
		return "OpenGGF " + AppVersion.get();
	}

	public void setInputHandler(InputHandler inputHandler) {
		this.inputHandler = inputHandler;
		gameLoop.setInputHandler(inputHandler);
	}

	public void run() {
		try {
			init();
			loop();
		} finally {
			cleanup();
		}
	}

	private static boolean isNativeImage() {
		return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
	}

	static boolean shouldEnablePerformanceProfiler(boolean performanceOverlayEnabled, boolean nativeImage) {
		return performanceOverlayEnabled;
	}

	static boolean shouldRenderPerformanceOverlay(GameMode currentMode,
												 boolean performanceOverlayEnabled,
												 boolean userRecordingSceneSuppressed) {
		return performanceOverlayEnabled && !userRecordingSceneSuppressed;
	}

	private void init() {
		com.openggf.configuration.PendingRestart.captureAtStart(configService);
		initializeExternalContentAtBoot();
		// === PHASE 1: Window, GL context, input (always runs) ===
		// Setup an error callback
		GLFWErrorCallback.createPrint(System.err).set();

		// Initialize GLFW
		if (!glfwInit()) {
			throw new IllegalStateException("Unable to initialize GLFW");
		}
		glfwInitialized = true;

		// Configure GLFW
		glfwDefaultWindowHints();
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE); // DPI-aware window scaling

		// Request OpenGL 4.1 core profile for macOS compatibility
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Required for macOS

		// Identify the window to the desktop shell so Wayland/X11 can match its icon
		WindowIconLoader.applyWindowClassHints();

		// Create the window
		window = glfwCreateWindow(windowWidth, windowHeight,
				buildWindowTitle(), NULL, NULL);
		if (window == NULL) {
			throw new RuntimeException("Failed to create the GLFW window");
		}

		// Apply the application icon (ignored on platforms that manage icons themselves)
		WindowIconLoader.apply(window);

		// Setup key callback
		glfwSetKeyCallback(window, (windowHandle, key, scancode, action, mods) -> {
			if (inputHandler != null && (editorChrome == null || editorChrome.key(key, action, mods))) {
				inputHandler.handleKeyEvent(key, action);
			}
		});
		installEditorTextInputCallback();
		glfwSetDropCallback(window, (windowHandle, count, names) -> {
			var paths = new ArrayList<String>(Math.min(count, 16));
			// Copy callback-owned UTF-8 names before GLFW releases them; no files are read here.
			for (int i = 0; i < Math.min(count, 16); i++) paths.add(GLFWDropCallback.getName(names, i));
			contentImportWindow.receive(paths);
		});
		glfwSetCursorPosCallback(window, (windowHandle, x, y) -> {
			if (inputHandler != null && (editorChrome == null || editorChrome.mouseMove(x, y))) {
				inputHandler.handleMouseMove(x, y);
			}
		});
		glfwSetMouseButtonCallback(window, (windowHandle, button, action, mods) -> {
			if (inputHandler != null && (editorChrome == null || editorChrome.mouseButton(button, action))) {
				inputHandler.handleMouseButton(button, action);
			}
		});
		org.lwjgl.glfw.GLFW.glfwSetScrollCallback(window, (windowHandle, xOffset, yOffset) -> {
			if (inputHandler != null && (editorChrome == null || editorChrome.scroll(xOffset, yOffset))) {
				com.openggf.control.MouseWheel.of(inputHandler).scroll(yOffset);
			}
		});

		// Setup window resize callback
		glfwSetFramebufferSizeCallback(window, (windowHandle, width, height) -> {
			liveCaptureController.requestStop(LiveCaptureController.StopReason.VIEWPORT_CHANGED);
			this.windowWidth = width;
			this.windowHeight = height;
			// Only reshape if GL context is initialized (avoids crash during window setup)
			if (graphicsManager.isGlInitialized()) {
				reshape(width, height);
			}
		});

		// Setup window focus callback
		glfwSetWindowCloseCallback(window, windowHandle -> {
			glfwSetWindowShouldClose(windowHandle, false);
			requestApplicationExit();
		});

		// Setup window focus callback
		glfwSetWindowFocusCallback(window, (windowHandle, focused) -> {
			if (editorChrome != null) editorChrome.focus(focused);
			paused = applyWindowActivation(focused, inputHandler, gameLoop::pause, gameLoop::resume);
		});

		// Setup window iconify callback
		glfwSetWindowIconifyCallback(window, (windowHandle, iconified) ->
				paused = applyWindowActivation(!iconified, inputHandler, gameLoop::pause, gameLoop::resume));

		// Get the thread stack and push a new frame
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);

			// Get the window size passed to glfwCreateWindow
			glfwGetWindowSize(window, pWidth, pHeight);

			// Center the window when the desktop video mode is available.
			long primaryMonitor = glfwGetPrimaryMonitor();
			GLFWVidMode vidmode = primaryMonitor != NULL ? glfwGetVideoMode(primaryMonitor) : null;
			if (vidmode != null) {
				glfwSetWindowPos(
						window,
						(vidmode.width() - pWidth.get(0)) / 2,
						(vidmode.height() - pHeight.get(0)) / 2
				);
			}
		}

		// Make the OpenGL context current
		glfwMakeContextCurrent(window);

		// Enable v-sync
		glfwSwapInterval(1);

		// Apply the saved desktop mode before showing the window.
		displayWindowController = new DisplayWindowController(window);
		stageplayWindowController = new StageplayWindowController(window);
		displayWindowController.apply(com.openggf.configuration.DisplaySettingsTrial.forConfig(configService).windowMode());
		glfwShowWindow(window);

		// This line is critical for LWJGL's interoperation with GLFW's
		// OpenGL context, or any context that is managed externally.
		GL.createCapabilities();

		try {
			initializePresentationGraphics();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		initializeEditorChrome();

		// Create input handler and set it
		inputHandler = InputHandler.live(InputBindingFactory.supplier(configService));
		setInputHandler(inputHandler);

		// Set window handle for clipboard operations (GLFW-based, no AWT dependency)
		debugOverlayManager.setWindowHandle(window);

		// Initial viewport uses physical framebuffer pixels, including high-DPI displays.
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);
			glfwGetFramebufferSize(window, pWidth, pHeight);
			this.windowWidth = pWidth.get(0);
			this.windowHeight = pHeight.get(0);
			reshape(windowWidth, windowHeight);
		}

		initializeConfiguredStartup();

		// Eagerly initialize debug renderer resources before the main loop starts.
		if (debugViewEnabled) {
			getDebugRenderer().updateViewport(viewportWidth, viewportHeight);
			getDebugRenderer().eagerInit();
			// Force GL sync and unbind all state
			glFinish();
			glBindTexture(GL_TEXTURE_2D, 0);
			glBindVertexArray(0);
			glBindBuffer(GL_ARRAY_BUFFER, 0);
			glUseProgram(0);
		}

		lastFrameTime = System.nanoTime();
	}

	private void installEditorTextInputCallback() {
		glfwSetCharCallback(window, (windowHandle, codepoint) -> {
			if (editorChrome != null && !editorChrome.character(codepoint)) return;
			com.openggf.control.MenuInput.handleCharEvent(inputHandler, codepoint);
		});
	}

	/**
	 * Initializes the ordinary configured startup graph without creating a
	 * window, GL context, or audio device. The caller owns both supplied
	 * instances; this seam only installs them and executes the same startup
	 * branch used by {@link #init()}. The configured session remains owned by
	 * the caller if startup throws, so {@link #closeConfiguredHeadlessSession()}
	 * must run from the caller's cleanup scope.
	 */
	public void initializeConfiguredHeadlessSession(
			InputHandler input, com.openggf.audio.AudioBackend backend) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(backend, "backend");
		if (configuredHeadlessSession) {
			throw new IllegalStateException(
					"a configured headless session is already active");
		}
		setInputHandler(input);
		audioManager.setBackend(backend);
		if (!audioManager.hasInstalledBackend(backend)) {
			setInputHandler(null);
			throw new IllegalStateException(
					"the supplied headless audio backend was not installed");
		}
		audioBackendInitialized = true;
		configuredHeadlessBackend = backend;
		configuredHeadlessSession = true;
		initializeConfiguredStartup();
	}

	/** Idempotent no-platform teardown for {@link #initializeConfiguredHeadlessSession}. */
	public void closeConfiguredHeadlessSession() {
		if (!configuredHeadlessSession) {
			return;
		}
		Throwable failure = cleanupConfiguredHeadlessResources();
		failure = releaseConfiguredHeadlessInput(failure);
		if (failure != null) {
			rethrowConfiguredHeadlessCleanupFailure(failure);
		}
		audioBackendInitialized = false;
		configuredHeadlessSession = false;
		configuredHeadlessBackend = null;
	}

	private Throwable cleanupConfiguredHeadlessResources() {
		Throwable failure = null;
		failure = appendConfiguredHeadlessCleanupFailure(
				failure, screenshotWriter::close);
		failure = appendConfiguredHeadlessCleanupFailure(
				failure, liveCaptureController::close);
		failure = appendConfiguredHeadlessCleanupFailure(
				failure, com.openggf.game.save.SessionSaveRequests::flushPendingSaves);
		failure = appendConfiguredHeadlessCleanupFailure(
				failure, SessionManager::clear);
		failure = appendConfiguredHeadlessCleanupFailure(
				failure, audioManager::clearDonorAudio);
		failure = appendConfiguredHeadlessCleanupFailure(
				failure, crossGameFeatureProvider::resetState);
		failure = appendConfiguredHeadlessCleanupFailure(
				failure, gameLoop::closePresence);
		failure = appendConfiguredHeadlessCleanupFailure(
				failure, audioManager::resetState);
		failure = appendConfiguredHeadlessCleanupFailure(
				failure, audioManager::destroy);
		return failure;
	}

	private Throwable releaseConfiguredHeadlessInput(Throwable failure) {
		InputHandler installedInput = inputHandler;
		if (installedInput != null) {
			failure = appendConfiguredHeadlessCleanupFailure(
					failure, installedInput::clearLogicalOverride);
		}
		return appendConfiguredHeadlessCleanupFailure(
				failure, () -> setInputHandler(null));
	}

	private static Throwable appendConfiguredHeadlessCleanupFailure(
			Throwable aggregate, Runnable cleanup) {
		try {
			cleanup.run();
		} catch (RuntimeException | Error failure) {
			if (aggregate == null) {
				return failure;
			}
			aggregate.addSuppressed(failure);
		}
		return aggregate;
	}

	private static void rethrowConfiguredHeadlessCleanupFailure(
			Throwable failure) {
		if (failure instanceof RuntimeException runtimeFailure) {
			throw runtimeFailure;
		}
		throw (Error) failure;
	}

	static ConfiguredStartupBranch resolveConfiguredStartupBranch(
			SonicConfigurationService configuration) {
		Objects.requireNonNull(configuration, "configuration");
		if (configuration.getBoolean(
				SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP)) {
			return ConfiguredStartupBranch.LEGAL_DISCLAIMER;
		}
		if (configuration.getBoolean(
				SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP)) {
			return ConfiguredStartupBranch.MASTER_TITLE;
		}
		return ConfiguredStartupBranch.GAME;
	}

	private void initializeConfiguredStartup() {
		switch (resolveConfiguredStartupBranch(configService)) {
			case LEGAL_DISCLAIMER -> {
				legalDisclaimerScreen = new LegalDisclaimerScreen(
						graphicsManager.getFadeManager());
				legalDisclaimerScreen.initialize();
				gameLoop.setGameMode(GameMode.LEGAL_DISCLAIMER);
			}
			case MASTER_TITLE, GAME -> enterBootModNoticeOrProceed(false);
		}
	}

	private void refreshDisplayPalettes() {
		LevelManager activeLevelManager = GameServices.levelOrNull();
		if (activeLevelManager != null) {
			activeLevelManager.reloadLevelPalettes();
		}
	}

	private void initializePresentationGraphics() throws IOException {
		graphicsManager.init(RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
		com.openggf.graphics.GraphicsProjectionAccess.install(graphicsManager, this);
		displayColorProfileController = DisplayColorProfileController.fromConfig(
				configService,
				graphicsManager,
				this::refreshDisplayPalettes);
		initializeDisplayShaders();
	}

	private void initializeDisplayShaders() {
		DisplayShaderLibrary library = scanDisplayShaderLibrary();
		DisplayShaderSelectionModel selectionModel = new DisplayShaderSelectionModel(library);
		DisplayShaderPresetLoader loader = new DisplayShaderPresetLoader();
		ShaderPhase defaultPhase = parseDisplayShaderDefaultPhase();
		displayShaderPickerController = new DisplayShaderPickerController(
				selectionModel,
				configService.getInt(SonicConfiguration.DISPLAY_SHADER_PICKER_KEY));
		displayShaderController = new DisplayShaderController(
				library,
				configService.getString(SonicConfiguration.DISPLAY_SHADER_SELECTION),
				configService.getInt(SonicConfiguration.DISPLAY_SHADER_NEXT_KEY),
				configService.getInt(SonicConfiguration.DISPLAY_SHADER_PREVIOUS_KEY),
				this::persistDisplayShaderSelection,
				ref -> activateDisplayShader(ref, loader, defaultPhase));
		displayShaderController.applySavedSelectionSilently();

		// A launch profile may enable live rewind after graphics have initialized.
		// Prewarm from the presentation preference alone so that session override
		// still has an effect when the global live-rewind default is disabled.
		if (configService.getBoolean(SonicConfiguration.LIVE_REWIND_VHS_EFFECT)) {
			rewindVhsEffectPass = new RewindVhsEffectPass();
			rewindVhsEffectPass.prewarm(
					configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS),
					configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS),
					Math.max(1, viewportWidth),
					Math.max(1, viewportHeight));
		}
	}

	private void reloadDisplayShaderLibrary() {
		DisplayShaderLibrary library = scanDisplayShaderLibrary();
		DisplayShaderSelectionModel selectionModel = new DisplayShaderSelectionModel(library);
		String savedSelection = configService.getString(SonicConfiguration.DISPLAY_SHADER_SELECTION);
		if (displayShaderController != null) {
			displayShaderController.replaceLibrary(library, savedSelection);
		}
		if (displayShaderPickerController != null) {
			DisplayShaderPresetRef currentRef = displayShaderController != null
					? displayShaderController.currentRef()
					: DisplayShaderPresetRef.OFF;
			displayShaderPickerController.replaceSelectionModel(selectionModel, currentRef);
		}
	}

	private DisplayShaderLibrary scanDisplayShaderLibrary() {
		String root = configService.getString(SonicConfiguration.DISPLAY_SHADER_LIBRARY_ROOT);
		try {
			return DisplayShaderLibrary.scan(Path.of(root));
		} catch (RuntimeException e) {
			LOGGER.warning("Display shader library root is invalid: " + root);
			return DisplayShaderLibrary.scan(null);
		}
	}

	private ShaderPhase parseDisplayShaderDefaultPhase() {
		String raw = configService.getString(SonicConfiguration.DISPLAY_SHADER_DEFAULT_PHASE);
		try {
			return ShaderPhase.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (RuntimeException e) {
			LOGGER.warning("Invalid display shader default phase '" + raw + "', using PRESENTATION");
			return ShaderPhase.PRESENTATION;
		}
	}

	private boolean activateDisplayShader(
			DisplayShaderPresetRef ref,
			DisplayShaderPresetLoader loader,
			ShaderPhase defaultPhase) {
		DisplayShaderPipeline pipeline = graphicsManager.getDisplayShaderPipeline();
		if (pipeline == null) {
			return ref != null && ref.kind() == DisplayShaderPresetRef.Kind.OFF;
		}
		if (ref == null || ref.kind() == DisplayShaderPresetRef.Kind.OFF) {
			return pipeline.activate(null);
		}
		try {
			return pipeline.activate(loader.load(ref, defaultPhase));
		} catch (Exception e) {
			LOGGER.log(java.util.logging.Level.WARNING, "Display shader failed to load: " + ref.label(), e);
			return false;
		}
	}

	private void persistDisplayShaderSelection(String selection) {
		configService.setConfigValue(SonicConfiguration.DISPLAY_SHADER_SELECTION, selection);
		configService.saveConfig();
	}

	private void startDisplayShaderPackDownload() {
		if (displayShaderPackDownloadInProgress) {
			displayShaderPackStatus = "Libretro GLSL: already downloading";
			return;
		}

		final Path shaderRoot;
		try {
			shaderRoot = Path.of(configService.getString(SonicConfiguration.DISPLAY_SHADER_LIBRARY_ROOT));
		} catch (RuntimeException e) {
			displayShaderPackStatus = "Libretro GLSL failed: invalid shader root";
			return;
		}

		displayShaderPackDownloadInProgress = true;
		displayShaderPackStatus = "Libretro GLSL: checking";
		Thread downloaderThread = new Thread(() -> {
			try {
				RetroArchGlslShaderPackDownloader.InstallResult result =
						new RetroArchGlslShaderPackDownloader().downloadIfNewer(
								shaderRoot,
								this::updateDisplayShaderPackProgress);
				displayShaderPackStatus = result.downloaded()
						? "Libretro GLSL: installed"
						: "Libretro GLSL: up to date";
				displayShaderPackRescanRequested = true;
			} catch (RetroArchGlslShaderPackDownloader.ShaderPackDownloadException e) {
				displayShaderPackStatus = "Libretro GLSL failed: " + e.reason();
				LOGGER.log(java.util.logging.Level.WARNING, "Libretro GLSL shader pack download failed", e);
			} finally {
				displayShaderPackDownloadInProgress = false;
			}
		}, "OpenGGF-libretro-glsl-download");
		downloaderThread.setDaemon(true);
		downloaderThread.start();
	}

	private void updateDisplayShaderPackProgress(
			RetroArchGlslShaderPackDownloader.Stage stage,
			long completed,
			long total,
			String detail) {
		displayShaderPackStatus = switch (stage) {
			case CHECKING -> "Libretro GLSL: checking";
			case DOWNLOADING -> total > 0
					? "Libretro GLSL: downloading " + Math.min(100, (completed * 100 / total)) + "%"
					: "Libretro GLSL: downloading";
			case EXTRACTING -> "Libretro GLSL: extracting " + completed;
			case COMPLETE -> detail == null || detail.isBlank()
					? "Libretro GLSL: complete"
					: "Libretro GLSL: " + detail;
		};
	}

	private void processDisplayShaderPackRescan() {
		if (!displayShaderPackRescanRequested || displayShaderPackDownloadInProgress) {
			return;
		}
		displayShaderPackRescanRequested = false;
		reloadDisplayShaderLibrary();
	}

	void refreshLaunchSessionCachedConfig() {
		debugViewEnabled = configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
	}

	ResolvedDisplayDimensions readResolvedDisplayDimensionsForLaunch() {
		return new ResolvedDisplayDimensions(
				configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS),
				configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS),
				configService.getInt(SonicConfiguration.SCREEN_WIDTH),
				configService.getInt(SonicConfiguration.SCREEN_HEIGHT));
	}

	/**
	 * Pins the aspect a resolved module requires as a session override. Returning to
	 * the master title clears session overrides, restoring the player's aspect;
	 * {@code resolveDisplayAspect} still forces native 4:3 in trace test mode.
	 */
	void applyRequiredDisplayAspect(GameModule module) {
		String required = module.requiredDisplayAspect();
		if (required == null) {
			return;
		}
		String aspect = WidescreenAspect.parse(required).name();
		if (aspect.equals(configService.getString(SonicConfiguration.DISPLAY_ASPECT))) {
			return;
		}
		configService.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect);
		configService.resolveDisplayAspect();
		applyResolvedDisplayDimensions();
	}

	void applyResolvedDisplayDimensions() {
		ResolvedDisplayDimensions resolved = readResolvedDisplayDimensionsForLaunch();
		realWidth = resolved.pixelWidth();
		realHeight = resolved.pixelHeight();
		projectionWidth = realWidth;
		graphicsManager.setProjectionWidth((int) projectionWidth);
		graphicsManager.applyResolvedDisplayWidth((int) projectionWidth);
        var camera = GameServices.cameraOrNull();
        if (camera != null) camera.refreshViewportDimensions(configService);
        var level = GameServices.levelOrNull();
        if (level != null) com.openggf.level.LevelDisplayWidth.refresh(level);

		if (glfwInitialized && window != 0L) {
			DisplayWindowFit.apply(window, (displayWindowController == null || displayWindowController.isWindowed())
					&& configService.getBoolean(SonicConfiguration.DISPLAY_WINDOW_AUTOSIZE),
					(int) realWidth, (int) realHeight);
			FramebufferDimensions framebuffer = readCurrentFramebufferDimensions();
			windowWidth = framebuffer.width();
			windowHeight = framebuffer.height();
			reshape(framebuffer.width(), framebuffer.height());
		}
	}

	private FramebufferDimensions readCurrentFramebufferDimensions() {
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);
			glfwGetFramebufferSize(window, pWidth, pHeight);
			return resolveFramebufferDimensionsAfterWindowResize(
					windowWidth, windowHeight, pWidth.get(0), pHeight.get(0));
		}
	}

	static FramebufferDimensions resolveFramebufferDimensionsAfterWindowResize(
			int requestedWindowWidth, int requestedWindowHeight, int framebufferWidth, int framebufferHeight) {
		if (framebufferWidth > 0 && framebufferHeight > 0) {
			return new FramebufferDimensions(framebufferWidth, framebufferHeight);
		}
		return new FramebufferDimensions(requestedWindowWidth, requestedWindowHeight);
	}

	void rollbackLaunchSessionCachedConfig() {
		refreshLaunchSessionCachedConfig();
		applyResolvedDisplayDimensions();
	}

	/**
	 * Phase 2 initialization: loads ROM, creates sprites, initializes audio, loads level.
	 * Called either directly from init() (no master title screen) or from
	 * exitMasterTitleScreen() after game selection.
	 */
	public void initializeGame() {
		GameModule rootModule;
		GameDataSource dataSource;
		try {
			Rom rom = romManager.getRom();
			var detectedModule = romDetectionService.detectAndCreateModule(rom);
			if (detectedModule.isEmpty()) {
				showStartupRomError("ROM not recognized or corrupt. OpenGGF requires a supported Sonic 1, Sonic 2, or Sonic 3&K ROM.");
				return;
			}
			rootModule = detectedModule.orElseThrow();
			dataSource = StockGameDataSources.pinned(rom, rootModule);
		} catch (IOException e) {
			showStartupRomError("Failed to load ROM during game initialization: " + e.getMessage());
			return;
		}
		GameModule module = resolveInitialModuleForLaunch(rootModule);
		if (module == null) {
			return;
		}
		applyRequiredDisplayAspect(module);
		if (!preparePresentationForLaunch(module)) {
			return;
		}
		GameplayModeContext gameplayMode = SessionManager.openGameplaySession(
				rootModule, module, dataSource, null);
		initializeGameplayRuntime(gameplayMode, true);
		boolean generationRan = maybeGenerateDonatedDataSelectImagesBeforeStartupMode(module);
		if (generationRan) {
			// Preview capture loaded full levels into the LevelManager and
			// GraphicsManager, corrupting the pattern atlas, palette textures,
			// DPLC banks, sprite art, and other GPU/manager state.
			// Reset GPU state (pattern atlas + palette textures) and rebuild
			// the gameplay mode so everything starts from a clean slate.
			graphicsManager.resetPatternAndPaletteState();
			gameplayMode = SessionManager.openGameplaySession(
					rootModule, module, dataSource, null);
			initializeGameplayRuntime(gameplayMode, false);
		} else {
			graphicsManager.runPendingRenderThreadTasks();
		}
		enterConfiguredStartupMode();
	}

	/** Detection-free boot for one effective standalone descriptor. */
	void initializeStandaloneGame(com.openggf.mods.ModDescriptor descriptor) {
		initializeStandaloneGame(descriptor, com.openggf.game.MasterTitleEntry.Action.NEW_GAME);
	}

	void initializeStandaloneGame(com.openggf.mods.ModDescriptor descriptor,
			com.openggf.game.MasterTitleEntry.Action action) {
		Objects.requireNonNull(descriptor, "descriptor");
		Objects.requireNonNull(action, "action");
		try {
			StandaloneSessionLaunch launch = openStandaloneSession(descriptor, action);
			GameplayModeContext gameplay = launch.gameplay();
			GameModule module = gameplay.getWorldSession().getGameModule();
			pinStandaloneTeam(launch.team());
			if (!preparePresentationForLaunch(module, descriptor.manifest().id())) return;
			initializeGameplayRuntime(gameplay, true);
			loadLevelFromDataSelect(launch.zone(), launch.act());
			restoreGameplayModeFromDataSelectPayload(gameplay, launch.payload());
			gameLoop.setGameMode(GameMode.LEVEL);
			if (action == com.openggf.game.MasterTitleEntry.Action.NEW_GAME) {
				gameLoop.requestSaveForCurrentSession(com.openggf.game.save.SaveReason.NEW_SLOT_START);
			}
		} catch (com.openggf.mods.code.ModFaultBoundary.CallbackAborted aborted) {
			LOGGER.log(java.util.logging.Level.SEVERE,
					"Standalone mod callback aborted launch", aborted);
			showStandaloneLaunchError(descriptor.manifest().id());
		} catch (IOException | RuntimeException failure) {
			LOGGER.log(java.util.logging.Level.SEVERE, "Standalone mod launch failed", failure);
			showStandaloneLaunchError(descriptor.manifest().id());
		}
	}

	/** Package-visible no-render standalone session seam for headless integration tests. */
	GameplayModeContext openStandaloneSession(com.openggf.mods.ModDescriptor descriptor)
			throws IOException {
		StandalonePrepared prepared = prepareStandalone(descriptor);
		return com.openggf.tools.HeadlessGameBoot.openStandaloneSessionForBoot(
				EngineServices.current(), prepared.module(), prepared.source());
	}

	private StandaloneSessionLaunch openStandaloneSession(
			com.openggf.mods.ModDescriptor descriptor,
			com.openggf.game.MasterTitleEntry.Action action) throws IOException {
		StandalonePrepared prepared = prepareStandalone(descriptor);
		GameModule module = prepared.module();
		SelectedTeam fallback = new SelectedTeam(
				standaloneDefaultCharacter(module.getPlayableCharacterRegistry()).persisted(), List.of());
		Map<String, Object> payload = action == com.openggf.game.MasterTitleEntry.Action.CONTINUE
				? requireStandaloneSlotPayload(module) : null;
		StandaloneSavePayload parsed = payload == null ? null : parseStandalonePayload(payload);
		SelectedTeam team = parsed == null ? fallback : parsed.team();
		validateStandaloneTeam(module, team);
		int zone = parsed == null ? 0 : parsed.zone();
		int act = parsed == null ? 0 : parsed.act();
		validateStandaloneLocation(module, zone, act);
		var saveContext = com.openggf.game.save.SaveSessionContext.forSlot(
				module.getGameCode(), 1, team, zone, act);
		GameplayModeContext gameplay = com.openggf.tools.HeadlessGameBoot.openStandaloneSessionForBoot(
				EngineServices.current(), module, prepared.source(), saveContext);
		return new StandaloneSessionLaunch(gameplay, team, zone, act, payload);
	}

	private StandalonePrepared prepareStandalone(com.openggf.mods.ModDescriptor descriptor)
			throws IOException {
		Objects.requireNonNull(descriptor, "descriptor");
		if (descriptor.manifest().type() != com.openggf.mods.ModType.STANDALONE) {
			throw new IllegalArgumentException("Descriptor is not standalone");
		}
		String owner = descriptor.manifest().id();
		com.openggf.mods.ModDescriptor retained = modRuntime.standaloneDescriptor(owner);
		if (!retained.sha256().equals(descriptor.sha256())
				|| !retained.manifest().equals(descriptor.manifest())) {
			throw new IllegalArgumentException("Standalone descriptor does not match runtime snapshot");
		}
		GameModule module = modRuntime.prepareStandaloneModule(owner).orElseThrow(() ->
				new com.openggf.mods.code.ModRegistrationException(owner,
						"Standalone registration did not publish a module"));
		ModSubsystem.current().recordRegistrationFailures(modRuntime.registrationFailures());
		GameDataSource source = new ModAssetDataSource(
				owner, modRuntime.standaloneAssetSnapshot(owner));
		return new StandalonePrepared(module, source);
	}

	private void pinStandaloneTeam(SelectedTeam team) {
		configService.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, team.mainCharacter());
		configService.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
				String.join(",", team.sidekicks()));
	}

	private Map<String, Object> requireStandaloneSlotPayload(GameModule module) throws IOException {
		com.openggf.game.save.SessionSaveRequests.flushPendingSaves();
		SaveSlotSummary summary = new SaveManager(com.openggf.game.save.SavePaths.root())
				.readSlotSummary(module.getGameCode(), 1);
		if (!summary.isLoadable()) throw new IOException("Standalone Continue slot is unavailable");
		Map<String, Object> payload = summary.payload();
		parseStandalonePayload(payload);
		return payload;
	}

	private static void validateStandaloneTeam(GameModule module, SelectedTeam team) throws IOException {
		var definitions = module.getPlayableCharacterRegistry().definitions();
		boolean mainKnown = definitions.keySet().stream()
				.anyMatch(key -> key.persisted().equals(team.mainCharacter()));
		boolean sidekicksKnown = team.sidekicks().stream().allMatch(value -> definitions.keySet().stream()
				.anyMatch(key -> key.persisted().equals(value)));
		if (!mainKnown || !sidekicksKnown) throw new IOException("Standalone Continue slot names unknown characters");
	}

	private static void validateStandaloneLocation(GameModule module, int zone, int act) throws IOException {
		if (zone < 0 || zone >= module.getZoneRegistry().getZoneCount()
				|| act < 0 || act >= module.getZoneRegistry().getActCount(zone)) {
			throw new IOException("Standalone Continue slot names an unknown zone/act");
		}
	}

	private record StandalonePrepared(GameModule module, GameDataSource source) { }
	private record StandaloneSessionLaunch(GameplayModeContext gameplay, SelectedTeam team,
			int zone, int act, Map<String, Object> payload) { }
	private record StandaloneSavePayload(SelectedTeam team, int zone, int act) { }

	private static StandaloneSavePayload parseStandalonePayload(Map<String, Object> payload)
			throws IOException {
		if (!(payload.get("mainCharacter") instanceof String main) || main.isBlank()
				|| !(payload.get("sidekicks") instanceof List<?> rawSidekicks)
				|| rawSidekicks.stream().anyMatch(value -> !(value instanceof String))) {
			throw new IOException("Standalone Continue slot has invalid launch data");
		}
		int zone = exactStandaloneSaveIndex(payload.get("zone"), "zone");
		int act = exactStandaloneSaveIndex(payload.get("act"), "act");
		if (zone < 0 || act < 0) throw new IOException("Standalone Continue location is negative");
		List<String> sidekicks = rawSidekicks.stream().map(String.class::cast).toList();
		return new StandaloneSavePayload(new SelectedTeam(main, sidekicks), zone, act);
	}

	private static int exactStandaloneSaveIndex(Object value, String field) throws IOException {
		if (value instanceof Integer integer) return integer;
		if (value instanceof Long longValue
				&& longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
			return longValue.intValue();
		}
		throw new IOException("Standalone Continue " + field + " must be an exact 32-bit integer");
	}

	static CharacterKey standaloneDefaultCharacter(PlayableCharacterRegistry registry) {
		return Objects.requireNonNull(registry, "registry").definitions().keySet().stream()
				.findFirst().orElseThrow(() ->
						new IllegalStateException("Standalone module has no playable character"));
	}

	GameModule resolveInitialModuleForLaunch(GameModule rootModule) {
		ModuleResolutionService.LaunchPolicy launchPolicy =
				configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)
						? ModuleResolutionService.LaunchPolicy.DETERMINISTIC
						: ModuleResolutionService.LaunchPolicy.STANDARD;
		ModuleResolutionService.PreparedLaunch launch = moduleResolutionService.prepareLaunch(launchPolicy);
		return consumePatchResolution(moduleResolutionService.resolveForLaunchResult(launch, rootModule,
				GameplayLaunchRequest.fromConfig(configService, rootModule.getGameId().code())), false);
	}

	void setPatchLaunchAbortHandlerForTests(
			java.util.function.Consumer<com.openggf.game.patch.ResolutionResult.LaunchAborted> handler) {
		Objects.requireNonNull(handler, "handler");
		patchLaunchAbortHandler = (aborted, activeSession) -> handler.accept(aborted);
	}

	private GameModule consumePatchResolution(com.openggf.game.patch.ResolutionResult result,
			boolean activeSession) {
		if (result instanceof com.openggf.game.patch.ResolutionResult.Resolved resolved) {
			recordResolvedOwnerFailures(resolved.ownerFailures());
			return resolved.module();
		}
		var aborted = (com.openggf.game.patch.ResolutionResult.LaunchAborted) result;
		if (!(aborted.failedOwner() instanceof com.openggf.game.patch.PatchOwner.Mod)) {
			throw new IllegalStateException("Built-in patch launch failed at " + aborted.patchId(),
					aborted.cause());
		}
		patchLaunchAbortHandler.accept(aborted, activeSession);
		return null;
	}

	private void recordResolvedOwnerFailures(
			java.util.Map<com.openggf.game.patch.PatchOwner, Throwable> failures) {
		java.util.IdentityHashMap<Throwable, java.util.Set<String>> grouped =
				new java.util.IdentityHashMap<>();
		for (var entry : failures.entrySet()) {
			if (entry.getKey() instanceof com.openggf.game.patch.PatchOwner.Mod mod) {
				grouped.computeIfAbsent(entry.getValue(), ignored -> new java.util.LinkedHashSet<>())
						.add(mod.modId());
			}
		}
		grouped.forEach((failure, owners) -> {
			modRuntime.disableOwnersForProcess(owners);
			ModSubsystem.current().recordOwnerFailures(
					owners, failure, "MOD_PATCH_METADATA_FAILED");
		});
	}

	private void handlePatchLaunchAbort(
			com.openggf.game.patch.ResolutionResult.LaunchAborted aborted,
			boolean activeSession) {
		LOGGER.log(java.util.logging.Level.SEVERE,
				"Mod patch launch aborted at " + aborted.patchId(), aborted.cause());
		java.util.Set<String> failedMods = aborted.failedOwners().stream()
				.filter(com.openggf.game.patch.PatchOwner.Mod.class::isInstance)
				.map(com.openggf.game.patch.PatchOwner.Mod.class::cast)
				.map(com.openggf.game.patch.PatchOwner.Mod::modId)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		modRuntime.disableOwnersForProcess(failedMods);
		ModSubsystem.current().recordOwnerFailures(failedMods, aborted.cause(),
				"MOD_PATCH_APPLY_FAILED");
		if (activeSession) {
			returnToMasterTitleScreen();
		} else {
			showModLaunchError();
		}
	}

	private void showModLaunchError() {
		showModLaunchError(configService.getString(SonicConfiguration.DEFAULT_ROM));
	}

	private void showModLaunchError(String selectedGameId) {
		gameplayMode = null;
		gameLoop.setGameplayMode(null);
		if (masterTitleScreen != null) {
			masterTitleScreen.cleanup();
		}
		masterTitleScreen = createMasterTitleScreen();
		if (graphicsManager.isGlInitialized()) {
			masterTitleScreen.initialize();
		}
		masterTitleScreen.showModLaunchError(selectedGameId);
		gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
	}

	void showStandaloneLaunchError() {
		showStandaloneLaunchError(configService.getString(SonicConfiguration.DEFAULT_ROM));
	}

	void showStandaloneLaunchError(String owner) {
		SessionManager.closeGameplaySession();
		showModLaunchError(owner);
	}

	private void showStartupRomError(String message) {
		LOGGER.warning(message);
		gameplayMode = null;
		gameLoop.setGameplayMode(null);
		if (masterTitleScreen != null) {
			masterTitleScreen.cleanup();
		}
		masterTitleScreen = createMasterTitleScreen();
		if (graphicsManager.isGlInitialized()) {
			masterTitleScreen.initialize();
		}
		masterTitleScreen.showRomLoadError(configService.getString(SonicConfiguration.DEFAULT_ROM));
		gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
	}

	/**
	 * Gets the master title screen instance (for GameLoop to call update/draw).
	 */
	public MasterTitleScreen getMasterTitleScreen() {
		return masterTitleScreen;
	}

	public LegalDisclaimerScreen getLegalDisclaimerScreen() {
		return legalDisclaimerScreen;
	}

	/**
	 * Called by GameLoop when the user selects a game from the master title screen.
	 * Performs the Phase 2 init for the selected game.
	 */
	public void exitMasterTitleScreen(String gameId) {
		refreshLaunchSessionCachedConfig();
		applyResolvedDisplayDimensions();

		// Set the DEFAULT_ROM config to the selected game
		configService.setConfigValue(SonicConfiguration.DEFAULT_ROM, gameId);

		// Clean up master title screen GL resources
		if (masterTitleScreen != null) {
			masterTitleScreen.cleanup();
			masterTitleScreen = null;
		}

		// Bootstrap title-screen mode runs before gameplay mode/module/ROM state is
		// fully established. Tear down that bootstrap-era state so the selected game
		// starts from a clean gameplay/render/ROM baseline instead of inheriting caches.
		resetForGameplayFromMasterTitle();

		// Phase 2: load ROM, sprites, audio, level
		initializeGame();
	}

	private void exitStandaloneMasterTitle(com.openggf.game.MasterTitleEntry.Launch launch) {
		if (!(launch.entry() instanceof com.openggf.game.MasterTitleEntry.Standalone standalone)) {
			throw new IllegalArgumentException("Standalone launch requires a standalone entry");
		}
		com.openggf.mods.ModDescriptor descriptor = ModSubsystem.current().processCatalog().effective()
				.orderedEnabled().stream()
				.filter(value -> value.manifest().type() == com.openggf.mods.ModType.STANDALONE
						&& value.manifest().id().equals(standalone.owner()))
				.findFirst().orElseThrow(() -> new IllegalArgumentException(
						"Standalone entry is no longer effective: " + standalone.owner()));
		if (com.openggf.mods.NativeUnsupportedMods.blocksStandalone(
				descriptor, compiledModsSupported)) {
			throw new IllegalStateException(
					"Standalone mod requires the JVM build (code unsupported on native): "
							+ standalone.owner());
		}
		refreshLaunchSessionCachedConfig();
		applyResolvedDisplayDimensions();
		if (masterTitleScreen != null) {
			masterTitleScreen.cleanup();
			masterTitleScreen = null;
		}
		resetForGameplayFromMasterTitle();
		initializeStandaloneGame(descriptor, launch.action());
	}

	private void requestApplicationExit() {
		if (editorQuitView != null) return;
		if (contentImportScreen.installing()) { quitAfterContentInstall = true; return; }
		quitAfterContentInstall = false;
		contentImportScreen.close();
		stageplay.stop();
		editorInputHandler.finishPendingEdits();
		var document = editorProjects.document();
		if ((document == null || !document.isModified()) && !stageplay.controller().dirty()) { closeApplicationWindow(); return; }
		editorQuitView = new EditorQuitView(getCurrentGameMode(), camera,
				camera == null ? 0 : camera.getX(), camera == null ? 0 : camera.getY(),
				camera == null ? 0 : camera.getXCopy(), camera == null ? 0 : camera.getYCopy());
		gameLoop.setEditorProjectModalMode(GameMode.MASTER_TITLE_SCREEN, true);
		stageplay.requestQuit(() -> editorProjects.requestQuit(this::closeApplicationWindow));
	}

	private void restoreEditorQuitView() {
		var previous = editorQuitView;
		editorQuitView = null;
		camera = previous.camera();
		if (camera != null) {
			camera.setX(previous.x()); camera.setY(previous.y());
			camera.setXCopy(previous.renderX()); camera.setYCopy(previous.renderY());
		}
		gameLoop.setEditorProjectModalMode(previous.mode(), false);
		var fade = gameLoop.resolveFadeManager();
		if (fade.getState() == com.openggf.graphics.FadeManager.FadeState.HOLD_BLACK
				&& !fade.hasPendingCompletion()) fade.startFadeFromBlack(null);
	}

	private void closeApplicationWindow() {
		if (window != NULL) {
			glfwSetWindowShouldClose(window, true);
		}
	}

	/**
	 * Called by GameLoop when the disclaimer's fade-to-black completes.
	 * Cleans up the disclaimer GL resources, then either builds the
	 * MasterTitleScreen (if MASTER_TITLE_SCREEN_ON_STARTUP is true) or
	 * runs Phase 2 init directly. In both branches the host owns the
	 * post-disclaimer reveal fade-from-black: none of
	 * enterConfiguredStartupMode's three sub-paths (title screen,
	 * level select, default level load) starts its own intro fade.
	 */
	public void exitLegalDisclaimer() {
		if (legalDisclaimerScreen != null) {
			legalDisclaimerScreen.cleanup();
			legalDisclaimerScreen = null;
		}

		enterBootModNoticeOrProceed(true);
	}

	private void proceedToMasterTitleOrGame(boolean fadeFromBlack) {
		boolean masterTitleOnStartup = configService.getBoolean(
				SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP);
		// `ggfmod run` of a patch mod opens its base game at once (holding Escape still reaches
		// the master title); deterministic test mode keeps the configured startup.
		java.util.Optional<String> developmentGame = configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)
				? java.util.Optional.empty() : ModSubsystem.current().developmentBaseGame();
		if (masterTitleOnStartup) {
			masterTitleScreen = createMasterTitleScreen();
			masterTitleScreen.initialize();
			gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
			if (developmentGame.isPresent() && launchDevelopmentGame(developmentGame.get())) {
				return;
			}
		} else {
			developmentGame.ifPresent(game -> configService.setSessionOverride(SonicConfiguration.DEFAULT_ROM, game));
			initializeGame();
		}

		if (fadeFromBlack) {
			graphicsManager.getFadeManager().startFadeFromBlack(null);
		}
	}

	/** Selects the development mod's base game on the master title; false (staying there) if it cannot start. */
	private boolean launchDevelopmentGame(String gameId) {
		try {
			gameLoop.launchGameByEntry(MasterTitleScreen.GameEntry.fromGameId(gameId), null);
			return true;
		} catch (RuntimeException unavailable) {
			LOGGER.warning("Development mod's base game " + gameId + " cannot start: " + unavailable.getMessage());
			return false;
		}
	}

	private void enterBootModNoticeOrProceed(boolean fadeFromBlackWhenNoNotice) {
		List<com.openggf.mods.ModDescriptor> unsupported =
				com.openggf.mods.NativeUnsupportedMods.compute(
						ModSubsystem.current().processCatalog().scanned(),
						ModSubsystem.current().startupModState(),
						compiledModsSupported);
		if (unsupported.isEmpty()) {
			proceedToMasterTitleOrGame(fadeFromBlackWhenNoNotice);
			return;
		}
		List<String> names = unsupported.stream()
				.map(descriptor -> descriptor.manifest().name()).toList();
		LOGGER.warning(com.openggf.mods.NativeUnsupportedMods.NOTICE_HEADER + " "
				+ String.join(", ", unsupported.stream()
						.map(descriptor -> descriptor.manifest().id()).toList()));
		nativeModNoticeScreen = new com.openggf.game.NativeModNoticeScreen(
				graphicsManager.getFadeManager(),
				com.openggf.mods.NativeUnsupportedMods.noticeLines(
						names, Integer.MAX_VALUE));
		nativeModNoticeScreen.initialize();
		gameLoop.setNativeModNoticeExitHandler(this::exitNativeModNotice);
		gameLoop.setGameMode(GameMode.NATIVE_MOD_NOTICE);
	}

	private void exitNativeModNotice() {
		if (nativeModNoticeScreen != null) {
			nativeModNoticeScreen.cleanup();
			nativeModNoticeScreen = null;
		}
		proceedToMasterTitleOrGame(true);
	}

	private void resetForGameplayFromMasterTitle() {
		if (stageplay != null) stageplay.deactivate();
		if (stageplaySession != null) stageplaySession.release();
		gameLoop.retireModScene();
		resetGameplayRuntime();
	}

	/** Clears a game's runtime while retaining the scene that owns a hosted run. */
	private void resetGameplayRuntime() {
		if (suspendedGame != null) {
			suspendedGame = null;
			gameLoop.suspension.discard();
		}
		if (editorProjectPlaytestActive) {
			gameLoop.stopEditorHostedRun();
			editorProjectPlaytestActive = false;
		}
		var worldSession = SessionManager.getCurrentWorldSession();
		if (worldSession != null) {
			worldSession.getGameModule().resetModuleScopedState();
		}
		SessionManager.clear();
		romManager.close();
		GameModuleRegistry.reset();
		ModSubsystem.current().returnToTitle();
		audioManager.resetState();
		crossGameFeatureProvider.resetState();
		debugOverlayManager.resetState();
		RenderContext.reset();
		graphicsManager.clearPaletteTextures();
		gameLoop.resetModuleScopedProviders();
	}

	// ==================== Suspended game (hold Escape) ====================

	/** Captures what resuming needs while the game's last frame and music are still intact. */
	private boolean prepareSuspend(GameMode mode) {
		var world = SessionManager.getCurrentWorldSession();
		if (editorProjectPlaytestActive || suspendedGame != null || world == null) return false;
		if (com.openggf.game.GameModuleRouting.isStandalone(world.getGameModule())) return false;
		GameId game = world.getGameModule().getGameId();
		com.openggf.audio.rewind.AudioLogicalSnapshot audio = null;
		String zone = null;
		try {
			if (configService.getBoolean(SonicConfiguration.AUDIO_ENABLED)) audio = audioManager.captureLogicalSnapshot();
			zone = world.getGameModule().getZoneRegistry().getZoneName(world.getCurrentZone());
		} catch (RuntimeException failure) {
			LOGGER.warning("Suspending without part of its state: " + failure.getMessage());
		}
		pendingSuspend = new SuspendedGame.Pending(audio,
				SuspendedGame.card(game.code(), mode, SuspendedGame.location(mode, zone, world.getApparentAct() + 1)));
		pendingSuspendThumbnail = null;
		suspendThumbnailRequested = true;
		audioManager.fadeOutMusic();
		return true;
	}

	/** Reads this frame's game picture before the fade pass and post-fade overlays draw over it. */
	private void captureSuspendThumbnailIfRequested() {
		if (!suspendThumbnailRequested) return;
		suspendThumbnailRequested = false;
		try {
			pendingSuspendThumbnail = SuspendedGame.thumbnail(ScreenshotCapture.captureFramebufferRegion(
					viewportX, viewportY, viewportWidth, viewportHeight), (int) projectionWidth, (int) realHeight);
		} catch (RuntimeException failure) {
			LOGGER.warning("Suspended game has no picture: " + failure.getMessage());
		}
	}

	/** After capturing the frozen frame: park its session and animate the picture into the hub. */
	private void suspendToMasterTitle() {
		SuspendedGame.Pending pending = pendingSuspend;
		pendingSuspend = null;
		if (pending == null) { gameLoop.returnToMasterTitle(); return; }
		SuspendedSettings settings = SuspendedSettings.suspend(configService);
		SuspendedGame.CameraPosition position = SuspendedGame.CameraPosition.of(camera);
		suspendedGame = new SuspendedGame(gameLoop.suspension.park(), pending, pendingSuspendThumbnail,
				settings, position);
		pendingSuspendThumbnail = null;
		refreshLaunchSessionCachedConfig();
		applyResolvedDisplayDimensions();
		if (masterTitleScreen != null) masterTitleScreen.cleanup();
		masterTitleScreen = createMasterTitleScreen();
		masterTitleScreen.initialize();
		graphicsManager.getFadeManager().cancel();
		masterTitleScreen.animateSuspendedGame();
		LOGGER.info("Suspended " + pending.card().menuLabel() + " to the master title");
	}

	/**
	 * After the picture has zoomed back in: restore the settings (live ones take their new values, see
	 * {@code LiveSettings}), then the picture size, camera and music.
	 */
	private void resumeSuspendedGame() {
		SuspendedGame game = suspendedGame;
		if (game == null) return;
		suspendedGame = null;
		if (masterTitleScreen != null) {
			masterTitleScreen.cleanup();
			masterTitleScreen = null;
		}
		game.settings().resume(configService, game.card().game().game(), game.mode() == GameMode.LEVEL);
		refreshLaunchSessionCachedConfig();
		gameLoop.suspension.unpark(game.mode());
		applyResolvedDisplayDimensions();
		if (game.camera() != null) game.camera().restore(camera);
		try {
			if (game.audio() != null) audioManager.restoreLogicalSnapshot(game.audio());
		} catch (RuntimeException failure) {
			LOGGER.warning("Resumed without its music state: " + failure.getMessage());
		}
		gameLoop.resolveFadeManager().cancel();
		LOGGER.info("Resumed " + game.card().menuLabel());
	}

	/** Ends a suspended game from the hub with the same teardown as a launch, keeping the hub up. */
	private void discardSuspendedGame() {
		if (suspendedGame == null) return;
		resetForGameplayFromMasterTitle();
		gameLoop.setGameplayMode(null);
		gameplayMode = null;
		ensureMasterTitleAudioBackend();
	}

	/**
	 * Teardown path used by {@link TraceSessionLauncher} after a trace
	 * playback session completes. Resets gameplay mode state (same
	 * cleanup as when gameplay first exits the master title) and
	 * rebuilds the master title screen so the picker can re-enter on
	 * the next frame.
	 */
	void returnToMasterTitleScreen() {
		resetForGameplayFromMasterTitle();
		// resetForGameplayFromMasterTitle destroyed the gameplay mode, but GameLoop
		// still caches the old mode + its FadeManager. Drop the reference
		// so the next launch's fade-to-black runs on the bootstrap manager
		// (which the UI pipeline actually ticks) rather than the dead one.
		gameLoop.setGameplayMode(null);
		this.gameplayMode = null;
		if (masterTitleScreen != null) {
			masterTitleScreen.cleanup();
		}
		refreshLaunchSessionCachedConfig();
		applyResolvedDisplayDimensions();
		masterTitleScreen = createMasterTitleScreen();
		masterTitleScreen.initialize();
		gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
		// Counter the teardown's fade-to-black. Without this the screen
		// stays fully black and the new master title never becomes
		// visible. Use the graphics-owned fade manager - the gameplay mode
		// (and its fade manager) was just destroyed above.
		FadeManager fadeManager = graphicsManager.getFadeManager();
		if (fadeManager != null) {
			fadeManager.startFadeFromBlack(null);
		}
	}

	public void enterEditorFromCurrentPlayer(EditorPlaytestStash stash, int playerX, int playerY) {
		if (!isEditorEnabled() && !editorProjectPlaytestActive) {
			throw new IllegalStateException("Level editor is disabled by configuration.");
		}
		ensureRuntimeBound();
		try {
			if (editorProjects.document() != null) {
				showEditorProject(editorProjects.document());
			} else {
				var world = SessionManager.getCurrentWorldSession();
				editorProjectBaseModule = world.getGameModule();
				editorProjectRootModule = world.rootGameModule();
				editorProjectDataSource = world.getDataSource();
				editorProjects.adoptLoadedLevel(editorProjectBaseModule, levelManager.getCurrentLevel(),
						world.getCurrentZone(), world.getCurrentAct());
				levelEditorController.setWorldCursor(new EditorCursorState(playerX, playerY));
				syncEditorState();
			}
			return;
		} catch (IOException | RuntimeException failure) {
			reportEditorProjectFailure(failure);
			return;
		}
	}

	public void resumePlaytestFromEditor() {
		if (editorProjects.document() != null) {
			try { editorProjects.playtest(true); }
			catch (RuntimeException failure) { reportEditorProjectFailure(failure); }
			return;
		}
		editorInputHandler.finishPendingEdits();
		if (!saveCurrentEditorLevel()) {
			return;
		}
		repairEditorCursorForResume();
		syncEditorState();
		GameplayModeContext gameplay = SessionManager.resumeGameplayFromEditor();
		// Build a fresh gameplay mode over the surviving WorldSession, then
		// rehydrate the loaded level (preserving any MutableLevel mutations
		// made in editor) via restoreInheritedLevel.
		initializeGameplayRuntime(gameplay, false);
		try {
			gameplay.getLevelManager().restoreInheritedLevel();
		} catch (IOException e) {
			throw new RuntimeException("Failed to restore inherited level on editor exit", e);
		}
		// Per the design, editor exit reinitializes gameplay session state as
		// fresh — score/timer/checkpoint must not carry over from before the
		// editor detour. (Counters that initializeGameplayRuntime already set —
		// special-stage progress configuration — stay.)
		gameplay.initializeFreshGameplayState();
		applyResumedPlaytestState(gameplay);
		gameLoop.setGameMode(GameMode.LEVEL);
	}

	private boolean saveCurrentEditorLevel() {
		if (editorProjects.document() != null) {
			try { editorProjects.save(); return true; }
			catch (RuntimeException failure) { reportEditorProjectFailure(failure); return false; }
		}
		MutableLevel mutableLevel = levelEditorController.currentLevel();
		if (mutableLevel == null) {
			return true;
		}
		com.openggf.game.session.WorldSession worldSession = SessionManager.getCurrentWorldSession();
		if (worldSession == null) {
			return true;
		}
		GameModule module = worldSession.getGameModule();
		try {
			EditorSaveManager.SaveResult save = editorSaveManager.save(
					module.getGameId(), module.getObjectPlacementEncoding(),
					worldSession.getCurrentZone(), worldSession.getCurrentAct(), mutableLevel);
			levelEditorController.setPersistenceStatus(save.persistenceStatus());
			return true;
		} catch (IOException e) {
			LOGGER.warning("Failed to save editor edits: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Production editor-export policy: a stable, non-overwriting directory per active game/act,
	 * reserved mod ids derived from the runtime indices, stock registry start/music metadata,
	 * and the effective (already patched) game module.
	 */
	private void exportCurrentEditorLevel() {
		if (editorProjects.document() != null) {
			try { editorProjects.export(); }
			catch (RuntimeException failure) { reportEditorProjectFailure(failure); }
			return;
		}
		MutableLevel level = levelEditorController.currentLevel();
		com.openggf.game.session.WorldSession session = SessionManager.getCurrentWorldSession();
		if (level == null || session == null) return;
		GameModule module = session.getGameModule();
		int zone = session.getCurrentZone(), act = session.getCurrentAct();
		ZoneRegistry zones = module.getZoneRegistry();
		int[] start = zones.getStartPosition(zone, act);
		MusicReference reference = zones.getMusicReference(zone, act);
		FullLevelExporter.ExportMusic music = reference instanceof MusicReference.Namespaced keyed
				? new FullLevelExporter.ExportMusic.Track(keyed.owner(), keyed.localName())
				: new FullLevelExporter.ExportMusic.Stock(Math.max(0, ((MusicReference.Stock) reference).musicId()));
		Path output = Path.of("exports", "editor", module.getGameId().code(),
				"zone_" + zone + "_act_" + act);
		FullLevelExporter.ExportRequest request = new FullLevelExporter.ExportRequest(output,
				zones.getZoneName(zone) + " EDITOR EXPORT", Math.addExact(0x40, zone),
				Math.addExact(0x400, Math.addExact(Math.multiplyExact(zone, 16), act)),
				start[0], start[1], music);
		try {
			levelEditorController.exportLevel(new FullLevelExporter(new ModLevelExportStagingValidator()), request);
			LOGGER.info("Exported editor level to " + output.toAbsolutePath().normalize());
		} catch (IOException | RuntimeException failure) {
			LOGGER.warning("Failed to export editor level: " + failure.getMessage());
		}
	}

	private boolean effectiveObjectKeyExists(String objectKey) {
		com.openggf.game.session.WorldSession session = SessionManager.getCurrentWorldSession();
		return session != null && session.getGameModule().createObjectRegistry().hasObjectKey(objectKey);
	}

	public void startGameplayFromBeginning() {
		editorInputHandler.finishPendingEdits();
		if (editorProjects.document() != null) {
			try { editorProjects.playtest(false); }
			catch (RuntimeException failure) { reportEditorProjectFailure(failure); }
			return;
		}
		GameplayModeContext gameplay = SessionManager.restartGameplayFromBeginning();
		initializeGameplayRuntime(gameplay, false);
		try { gameplay.getLevelManager().restoreInheritedLevel(); }
		catch (IOException failure) { throw new IllegalStateException("Unable to restart the edited act", failure); }
		gameLoop.setGameMode(GameMode.LEVEL);
	}

	private GameModule selectEditorProjectGame(GameId game) throws IOException {
		if (TraceSessionLauncher.active() != null) throw new IOException("Editor projects are unavailable during a trace session");
		var currentWorld = SessionManager.getCurrentWorldSession();
		if (currentWorld != null && currentWorld.getGameModule().getGameId() == game) {
			GameModule active = currentWorld.getGameModule();
			editorProjectBaseModule = active instanceof EditorProjectRuntimeModule project ? project.base() : active;
			editorProjectRootModule = currentWorld.rootGameModule();
			editorProjectDataSource = currentWorld.getDataSource();
		} else {
			// Read and identify the selected ROM before retiring an open project's runtime.
			Rom candidateRom = romManager.getSecondaryRom(game.code());
			var detected = romDetectionService.detectAndCreateModule(candidateRom);
			if (detected.isEmpty() || detected.orElseThrow().getGameId() != game)
				throw new IOException("Configure a supported " + game.code() + " ROM before creating this project");
			if (masterTitleScreen != null) { masterTitleScreen.cleanup(); masterTitleScreen = null; }
			refreshLaunchSessionCachedConfig();
			configService.setConfigValue(SonicConfiguration.DEFAULT_ROM, game.code());
			resetForGameplayFromMasterTitle();
			initializeGame();
			var world = SessionManager.getCurrentWorldSession();
			if (world == null || world.getGameModule().getGameId() != game)
				throw new IOException("Configure a supported " + game.code() + " ROM before creating this project");
			editorProjectBaseModule = world.getGameModule();
			editorProjectRootModule = world.rootGameModule();
			editorProjectDataSource = world.getDataSource();
			gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
		}
		return editorProjectBaseModule;
	}

	private Level loadEditorAssetLibrary(int zone, int act) throws IOException {
		var gameplay = SessionManager.openGameplaySession(editorProjectRootModule, editorProjectBaseModule,
				editorProjectDataSource, null);
		initializeGameplayRuntime(gameplay, false);
		levelManager.setEditorSaveManager(null);
		levelManager.loadZoneAndAct(zone, act, LevelLoadMode.PREVIEW_CAPTURE);
		gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
		return levelManager.getCurrentLevel();
	}

	private void showEditorProject(EditorProjectDocument document) throws IOException {
		editorInputHandler.finishPendingEdits();
		if (editorProjectPlaytestActive) { gameLoop.stopEditorHostedRun(); editorProjectPlaytestActive = false; }
		selectEditorProjectGame(document.metadata().gameId());
		if (masterTitleScreen != null) { masterTitleScreen.cleanup(); masterTitleScreen = null; }
		EditorCursorState cursor = shownEditorProject == document ? levelEditorController.worldCursor()
				: new EditorCursorState(document.metadata().startX(), document.metadata().startY());
		var gameplay = SessionManager.openGameplaySession(editorProjectRootModule, editorProjectBaseModule,
				editorProjectDataSource, null);
		initializeGameplayRuntime(gameplay, false);
		var world = gameplay.getWorldSession();
		int zone = Math.clamp(document.metadata().sourceZone(), 0, editorProjectBaseModule.getZoneRegistry().getZoneCount() - 1);
		int act = Math.clamp(document.metadata().sourceAct(), 0, editorProjectBaseModule.getZoneRegistry().getActCount(zone) - 1);
		world.setCurrentZone(zone); world.setCurrentAct(act); world.setApparentAct(act);
		world.setCurrentLevel(document.level());
		SessionManager.enterEditorMode(cursor);
		gameplayMode = null; gameLoop.setGameplayMode(null);
		Level level = document.level();
		// Editor navigation covers authored cells independently of native gameplay camera limits.
		bindEditorLevelView(level, (short) 0,
				(short) Math.max(0, level.getMap().getWidth() * level.getBlockPixelSize() - camera.getWidth()),
				(short) 0, (short) Math.max(0, level.getMap().getHeight() * level.getBlockPixelSize() - camera.getHeight()));
		levelManager.setEditorSaveManager(null);
		if (graphicsManager.isGlInitialized()) {
			for (int p = 0; p < level.getPaletteCount(); p++) graphicsManager.cachePaletteTexture(level.getPalette(p), p);
			graphicsManager.beginPatternAtlasBatch();
			try { for (int p = 0; p < level.getPatternCount(); p++) graphicsManager.cachePatternTexture(level.getPattern(p), p); }
			finally { graphicsManager.endPatternAtlasBatch(); }
		}
		boolean newLevel = levelEditorController.currentLevel() != document.level();
		levelEditorController.attachLevel(document.level());
		configureEditorProjectSpawnEditing(document);
		levelEditorController.setPersistenceStatus(EditorSaveManager.ApplyResult.APPLIED);
		if (newLevel) {
			levelEditorController.setWorldCursor(cursor);
			primeEditorSelection(cursor.x(), cursor.y());
		}
		shownEditorProject = document;
		gameLoop.setGameMode(GameMode.EDITOR);
		syncEditorState();
	}

	private void configureEditorProjectSpawnEditing(EditorProjectDocument document) {
		var registry = editorProjectBaseModule.createObjectRegistry();
		java.util.function.IntFunction<String> names = registry::getPrimaryName;
		if (document.metadata().objectZoneSet() != null
				&& editorProjectRootModule.createObjectRegistry() instanceof com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry nativeRegistry) {
			var family = com.openggf.game.sonic3k.constants.S3kZoneSet.valueOf(document.metadata().objectZoneSet().name());
			names = id -> nativeRegistry.getPrimaryName(id, family);
		}
		levelEditorController.configureSpawnEditing(registry, editorProjectBaseModule.getObjectPlacementEncoding(),
				names);
	}

	private void startEditorProjectPlaytest(EditorProjectDocument document, boolean fromCursor) throws IOException {
		editorInputHandler.finishPendingEdits();
		EditorCursorState cursor = fromCursor ? levelEditorController.worldCursor()
				: new EditorCursorState(document.metadata().startX(), document.metadata().startY());
		var module = EditorProjectRuntimeModule.prepare(editorProjectBaseModule, document, cursor.x(), cursor.y());
		try {
		var gameplay = SessionManager.openGameplaySession(editorProjectRootModule, module, editorProjectDataSource, null);
		initializeGameplayRuntime(gameplay, false);
		levelManager.setEditorSaveManager(null);
		var policy = new com.openggf.game.session.GameplayRunPolicy(false, false,
				com.openggf.game.session.GameplayRunPolicy.ActCompletion.RETURN_TO_HOST, true, true);
		gameplay.beginGameplayRun(policy, 0, 0);
		gameLoop.beginHostedRun(new com.openggf.mods.run.RunSpec(module.getGameId().code(), 0, 0,
				configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE), policy), new com.openggf.mods.run.RunHost() {},
				reason -> {
					editorProjectPlaytestActive = false;
					try { showEditorProject(document); levelEditorController.setStatus("Playtest finished: " + reason); }
					catch (IOException | RuntimeException failure) { reportEditorProjectFailure(failure); }
				});
			levelManager.loadZoneAndActForFreshRuntime(0, 0);
			gameplay.initializeFreshGameplayState();
			editorProjectPlaytestActive = true;
			gameLoop.announceHostedRunLevelReady();
			gameLoop.setGameMode(GameMode.LEVEL);
		} catch (IOException | RuntimeException failure) {
			gameLoop.stopEditorHostedRun();
			editorProjectPlaytestActive = false;
			try { showEditorProject(document); }
			catch (IOException | RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
			throw failure;
		}
	}

	private void reportEditorProjectFailure(Throwable failure) {
		LOGGER.warning("Editor project: " + failure.getMessage());
		levelEditorController.setStatus("Project error: " + failure.getMessage());
		if (getCurrentGameMode() == GameMode.EDITOR || getCurrentGameMode() == GameMode.MASTER_TITLE_SCREEN
				|| getCurrentGameMode() == GameMode.LEVEL) {
			editorProjects.openMenu(); editorProjects.menu().report(failure);
		}
	}

	public EditorProjectWorkspace getEditorProjectWorkspace() { return editorProjects; }

	private void initializeGameplayRuntime(GameplayModeContext gameplayMode, boolean initializeGlobalGameplayServices) {
		GameModule module = gameplayMode.getWorldSession().getGameModule();
		com.openggf.game.session.PatternWindowSessionState.install(
				gameplayMode.getWorldSession(),
				ModSubsystem.current().patternWindowStateForSession());
		GameplaySessionFactory.attachManagers(gameplayMode, EngineServices.current());
		bindGameplayMode(gameplayMode);
		gameplayMode.getGameStateManager().configureSpecialStageProgress(
				module.getSpecialStageCycleCount(),
				module.getChaosEmeraldCount());

		if (initializeGlobalGameplayServices) {
			initializeGlobalGameplayServices();
		}

		GameplayTeamBootstrap.BootstrappedTeam team = GameplayTeamBootstrap.registerActiveTeam(
				module, gameplayMode.getWorldSession().getPlayableCharacterRegistry(),
				modRuntime.characterAvailability(), spriteManager, configService,
				GameplayTeamBootstrap.DEFAULT_MAIN_X, GameplayTeamBootstrap.DEFAULT_MAIN_Y,
				ModCharacterFallbackFindings.sink(ModSubsystem.current().runtimeFindings()));
		camera.setFocusedSprite(team.mainSprite());
		camera.updatePosition(true);
	}

	private void initializeGlobalGameplayServices() {
		if (configService.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED)) {
			try {
				String donorGame = configService.getString(SonicConfiguration.CROSS_GAME_SOURCE);
				crossGameFeatureProvider.initialize(donorGame);
			} catch (IOException e) {
				LOGGER.severe("Cross-game features enabled but initialization failed. "
						+ "Check that the " + configService.getString(SonicConfiguration.CROSS_GAME_SOURCE)
						+ " ROM is configured and accessible. Error: " + e.getMessage());
			}
		}
	}

	private void initializeExternalContentAtBoot() {
		ExternalContentPolicy policy = new ExternalContentPolicy(externalContentBootMode(
				configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)));
		ModSubsystem.installAtBoot(policy, ModSubsystem.normalBootLoader(
				() -> Path.of("mods").toAbsolutePath().normalize(),
				ModInputLimits.production(), StockMusicDomains::containsSupported,
				ModSubsystem.SessionAudioBoundary.audioManager(audioManager),
				com.openggf.mods.BundledModSource.Locator.production()),
				compiledModsSupported);
		try {
			var effectiveMods = ModSubsystem.current().processCatalog().effective();
			ModSubsystem.current().transferDevelopmentSourceOwnership();
			installModRuntime(ModSubsystem.current(), new ModClassLoaderFactory(Engine.class.getClassLoader())
					.create(effectiveMods, ModSubsystem.current().trustedCodeOwners(), compiledModsSupported));
		} catch (IOException error) {
			LOGGER.log(java.util.logging.Level.WARNING, "Compiled-mod runtime initialization failed", error);
		}
	}

	/** Rebuilds the catalog and code owners while the title owns no gameplay session. */
	private void reloadExternalContentAtTitle() {
		discardSuspendedGame();
		ModSubsystem candidate = ModSubsystem.normalBootLoader(
				() -> Path.of("mods").toAbsolutePath().normalize(),
				ModInputLimits.production(), StockMusicDomains::containsSupported,
				ModSubsystem.SessionAudioBoundary.audioManager(audioManager)).get();
		candidate.setCompiledModsSupported(compiledModsSupported);
		ModRuntime replacement;
		try {
			replacement = new ModClassLoaderFactory(Engine.class.getClassLoader())
					.create(candidate.processCatalog().effective(), candidate.trustedCodeOwners(),
							compiledModsSupported);
		} catch (IOException | RuntimeException failure) {
			candidate.close();
			throw new IllegalStateException("Unable to prepare mods", failure);
		}
		ModSubsystem.installProcess(candidate);
		installModRuntime(candidate, replacement);
		MasterTitleScreen previous = masterTitleScreen;
		MasterTitleScreen refreshed = createMasterTitleScreen();
		refreshed.initialize();
		masterTitleScreen = refreshed;
		if (previous != null) previous.cleanup();
	}

	private void installModRuntime(ModSubsystem subsystem, ModRuntime replacement) {
		modRuntime = replaceModRuntime(modRuntime, replacement);
		replacement.installFaultBoundary(subsystem.createFaultBoundary(replacement));
		replacement.installSaveFindingSink((owner, finding) ->
				subsystem.runtimeFindings().upsertOwnerFinding(owner,
						new com.openggf.mods.ModFinding(com.openggf.mods.ModFindingSeverity.WARNING,
								finding.code(), finding.detail(), null)));
		subsystem.installRewindClassResolver(
				new ModClassResolver(replacement, Engine.class.getClassLoader()));
		var effectiveMods = subsystem.processCatalog().effective();
		moduleResolutionService.installModPlanSource(
				new EffectiveCatalogPatchEnablement(effectiveMods), enablement -> {
					var plan = replacement.newRegistrationPlan();
					subsystem.recordRegistrationFailures(replacement.registrationFailures());
					return plan;
				});
	}

	static ExternalContentMode externalContentBootMode(boolean testMode){
		return testMode&&!com.openggf.mods.DevelopmentModSource.isConfigured()
				?ExternalContentMode.STARTUP_DETERMINISTIC:ExternalContentMode.NORMAL;
	}

	private MasterTitleScreen createMasterTitleScreen() {
		ensureMasterTitleAudioBackend();
		ModuleResolutionService.LaunchPolicy policy =
				configService.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)
						? ModuleResolutionService.LaunchPolicy.DETERMINISTIC
						: ModuleResolutionService.LaunchPolicy.STANDARD;
		ModuleResolutionService.PreparedLaunch preparedLaunch =
				moduleResolutionService.prepareLaunch(policy);
		MasterTitleScreen screen = new MasterTitleScreen(configService,
				new com.openggf.game.launch.LaunchProfileStore(
						configService, moduleResolutionService, preparedLaunch),
				masterTitleEntries(),
				cue -> audioManager.playSfx(cue.sfxName()));
		screen.setEditorProjectsOpenHandler(editorProjects::openMenu);
		if (suspendedGame != null) {
			SuspendedGame.Thumbnail thumbnail = suspendedGame.thumbnail();
			screen.setSuspendedGame(suspendedGame.card(), thumbnail == null ? 0 : thumbnail.width(),
					thumbnail == null ? 0 : thumbnail.height(), thumbnail == null ? null : thumbnail.rgba());
		}
		screen.setSuspendedGameEndHandler(this::discardSuspendedGame);
		if (ModSubsystem.current().policy().mayScanAtBoot())
			TitleContentImportAccess.bind(screen, this::openContentImport);
		screen.setEnabledModsByGame(enabledTitleModsByGame());
		com.openggf.game.MasterTitleCatalog.bind(screen, this::masterTitleEntries);
		if (ModSubsystem.current().policy().mayScanAtBoot()) {
			screen.setModManagerScreenFactory(font -> ModSubsystem.current().createManager(font));
			screen.setModManagerApplyHandler(this::reloadExternalContentAtTitle);
			List<com.openggf.mods.code.OwnedTitleEntry> titleEntries = modRuntime.titleEntries();
			screen.setTitleEntries(titleEntries.stream().map(com.openggf.mods.code.OwnedTitleEntry::label).toList(),
					(games, index) -> gameLoop.requestTitleEntryOpen(
							() -> openTitleEntryScene(titleEntries.get(index), games)));
		}
		return screen;
	}

	private Map<MasterTitleScreen.GameEntry, List<String>> enabledTitleModsByGame() {
		Map<MasterTitleScreen.GameEntry, List<String>> names =
				new java.util.EnumMap<>(MasterTitleScreen.GameEntry.class);
		var subsystem = ModSubsystem.current();
		var trusted = subsystem.trustedCodeOwners();
		var rejected = modRuntime == null ? java.util.Set.<String>of()
				: modRuntime.rejectedOwners().keySet();
		var failed = modRuntime == null ? java.util.Set.<String>of()
				: modRuntime.registrationFailures().keySet();
		var disabled = modRuntime == null ? java.util.Set.<String>of()
				: modRuntime.runtimeDisabledOwners();
		for (MasterTitleScreen.GameEntry game : MasterTitleScreen.GameEntry.values()) {
			List<String> gameNames = new ArrayList<>();
			for (var descriptor : subsystem.processCatalog().effective().orderedEnabled()) {
				var manifest = descriptor.manifest();
				if (manifest.type() != com.openggf.mods.ModType.PATCH
						|| !(game.gameId.equals(manifest.baseGame()) || "any".equals(manifest.baseGame()))
						|| descriptor.containsCode() && (!compiledModsSupported || !trusted.contains(manifest.id()))
						|| rejected.contains(manifest.id()) || failed.contains(manifest.id())
						|| disabled.contains(manifest.id())) continue;
				gameNames.add(manifest.name());
			}
			names.put(game, List.copyOf(gameNames));
		}
		return names;
	}

	private void ensureMasterTitleAudioBackend() {
		if (!configService.getBoolean(SonicConfiguration.AUDIO_ENABLED)) {
			return;
		}
		if (!audioBackendInitialized) {
			audioManager.setBackend(new LWJGLAudioBackend(configService, profiler));
			audioBackendInitialized = true;
		} else {
			audioManager.ensurePresentationSink();
		}
	}

	private List<com.openggf.game.MasterTitleEntry> masterTitleEntries() {
		// This menu's reader is distinct from the in-game async writer.
		com.openggf.game.save.SessionSaveRequests.flushPendingSaves();
		List<com.openggf.game.MasterTitleEntry> entries = new ArrayList<>();
		for (MasterTitleScreen.GameEntry game : MasterTitleScreen.GameEntry.values()) {
			entries.add(new com.openggf.game.MasterTitleEntry.Stock(game));
		}
		SaveManager saves = new SaveManager(com.openggf.game.save.SavePaths.root());
		for (com.openggf.mods.ModDescriptor descriptor
				: ModSubsystem.current().processCatalog().effective().orderedEnabled()) {
			if (descriptor.manifest().type() != com.openggf.mods.ModType.STANDALONE) continue;
			if (com.openggf.mods.NativeUnsupportedMods.blocksStandalone(
					descriptor, compiledModsSupported)) {
				continue;
			}
			String owner = descriptor.manifest().id();
			boolean canContinue = false;
			try {
				SaveSlotSummary summary = saves.readSlotSummary(owner, 1);
				canContinue = summary.isLoadable()
						&& validStandaloneTitlePayload(owner, summary.payload());
			} catch (IOException failure) {
				LOGGER.log(java.util.logging.Level.WARNING,
						"Failed to inspect standalone Continue slot for " + owner, failure);
			}
			entries.add(new com.openggf.game.MasterTitleEntry.Standalone(
					owner, descriptor.manifest().name(), canContinue));
		}
		return List.copyOf(entries);
	}

	private boolean validStandaloneTitlePayload(String owner, Map<String, Object> payload) {
		try {
			StandaloneSavePayload parsed = parseStandalonePayload(payload);
			GameModule module = modRuntime.prepareStandaloneModule(owner).orElseThrow(() ->
					new IOException("Standalone module is unavailable"));
			validateStandaloneTeam(module, parsed.team());
			validateStandaloneLocation(module, parsed.zone(), parsed.act());
			return true;
		} catch (IOException | RuntimeException invalid) {
			return false;
		}
	}

	private boolean preparePresentationForLaunch(GameModule module) {
		return preparePresentationForLaunch(module, null);
	}

	private boolean preparePresentationForLaunch(GameModule module, String standaloneOwner) {
		try {
			if (configuredHeadlessSession) {
				if (!audioManager.hasInstalledBackend(configuredHeadlessBackend)) {
					throw new IllegalStateException("Configured headless audio backend was replaced");
				}
				// A runtime reset closes the sink, not the caller-owned offline backend.
				audioManager.ensurePresentationSink();
			} else if (configService.getBoolean(SonicConfiguration.AUDIO_ENABLED)) {
