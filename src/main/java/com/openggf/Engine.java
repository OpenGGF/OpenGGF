package com.openggf;

import com.openggf.architecture.CompositionRoot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.*;
import com.openggf.graphics.*;
import com.openggf.version.AppVersion;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import com.openggf.control.InputHandler;
import com.openggf.editor.EditorInputHandler;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.persistence.EditorSaveManager;
import com.openggf.editor.render.EditorOverlayRenderer;
import com.openggf.audio.AudioManager;
import com.openggf.audio.LWJGLAudioBackend;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
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
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.startup.DonatedDataSelectWarmupTask;
import com.openggf.data.Rom;
import com.openggf.physics.Direction;
import com.openggf.graphics.color.DisplayColorProfileController;
import com.openggf.render.EngineRenderDispatcher;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

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
public class Engine {
	private static final Logger LOGGER = Logger.getLogger(Engine.class.getName());
	public static final String RESOURCES_SHADERS_PIXEL_SHADER_GLSL = "shaders/shader_the_hedgehog.glsl";
	private final SonicConfigurationService configService;
	private SpriteManager spriteManager;
	private final GraphicsManager graphicsManager;
	private final AudioManager audioManager;
	private final RomManager romManager;
	private final RomDetectionService romDetectionService;
	private final CrossGameFeatureProvider crossGameFeatureProvider;
	private final DebugOverlayManager debugOverlayManager;
	private final PlaybackDebugManager playbackDebugManager;

	private Camera camera;
	private DebugRenderer debugRenderer;
	private final PerformanceProfiler profiler;

	private final GameLoop gameLoop;
	private final EngineRenderDispatcher renderDispatcher = new EngineRenderDispatcher();
	private final EngineRenderDispatcher.ClearActions clearActions = new EngineClearActions();
	private final EngineRenderDispatcher.DrawActions drawActions = new EngineDrawActions();
	private final LevelEditorController levelEditorController = new LevelEditorController();
	private final EditorInputHandler editorInputHandler;
	private final EditorOverlayRenderer editorOverlayRenderer;
	// Match the rest of the debug overlay — no drop shadow.
	private final PixelFontTextRenderer traceHudTextRenderer =
		new PixelFontTextRenderer(PixelFontVariant.PIXEL_FONT_NO_SHADOW);
	private DisplayColorProfileController displayColorProfileController;

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

	// Window dimensions
	private int windowWidth;
	private int windowHeight;

	private boolean overlayStateReady = false;

	// Input handler for keyboard input
	private InputHandler inputHandler;

	// Master title screen (game selection before ROM loading)
	private MasterTitleScreen masterTitleScreen;

	// Legal disclaimer screen (pre-ROM disclaimer)
	private LegalDisclaimerScreen legalDisclaimerScreen;

	// Viewport parameters for aspect-ratio-correct rendering
	private int viewportX = 0;
	private int viewportY = 0;
	private int viewportWidth = 0;
	private int viewportHeight = 0;

	// Window snap-to-integer-scale after resize
	private long lastResizeTimeNanos = 0;
	private boolean resizePendingSnap = false;
	private boolean isSnappingWindowSize = false;
	private int lastSnappedScale = 0;

	// JOML matrices for projection - accessible for shader uniforms
	private final org.joml.Matrix4f projectionMatrix = new org.joml.Matrix4f();
	private final float[] matrixBuffer = new float[16];

	// Static instance for singleton access
	private static Engine instance;

	// Frame timing
	private int targetFps;
	private long lastFrameTime;
	private boolean paused = false;

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
		this.profiler = engineServices.profiler();
		this.graphicsManager.setPerformanceProfiler(profiler);
		this.editorOverlayRenderer = new EditorOverlayRenderer(levelEditorController, graphicsManager);
		this.gameLoop = new GameLoop(engineServices);
		this.gameLoop.setEditorStateSyncHandler(this::syncEditorState);
		this.gameLoop.setMasterTitleScreenSupplier(() -> masterTitleScreen);
		this.gameLoop.setMasterTitleExitHandler(this::exitMasterTitleScreen);
		this.gameLoop.setLegalDisclaimerScreenSupplier(() -> legalDisclaimerScreen);
		this.gameLoop.setLegalDisclaimerExitHandler(this::exitLegalDisclaimer);
		this.gameLoop.setDataSelectActionHandler(this::launchGameplayFromDataSelect);
		this.gameLoop.setReturnToMasterTitleHandler(this::returnToMasterTitleScreen);
		this.realWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
		this.realHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
		this.projectionWidth = realWidth;
		this.debugViewEnabled = configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
		this.windowWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH);
		this.windowHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT);
		this.targetFps = configService.getInt(SonicConfiguration.FPS);
		this.editorInputHandler = new EditorInputHandler(
				levelEditorController, () -> camera, () -> graphicsManager, this::saveCurrentEditorLevel);

		// Set up game mode change listener to update projection width
		gameLoop.setGameModeChangeListener((oldMode, newMode) -> {
			// Keep projection at 320 for both modes
			projectionWidth = realWidth;
		});
		gameLoop.setEditorInputHandler(editorInputHandler);
		gameLoop.setEditorPlaytestToggleHandler(this::toggleEditorPlaytestMode);
		gameLoop.setEditorFreshStartHandler(this::startGameplayFromBeginning);

		instance = this;
	}

	static String buildWindowTitle() {
		return "OpenGGF " + AppVersion.get();
	}

	public void setInputHandler(InputHandler inputHandler) {
		this.inputHandler = inputHandler;
		gameLoop.setInputHandler(inputHandler);
	}

	public void run() {
		init();
		try { loop(); } finally { cleanup(); }
	}

	private static boolean isNativeImage() {
		return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
	}

	private void init() {
		// === PHASE 1: Window, GL context, input (always runs) ===

		// Setup an error callback
		GLFWErrorCallback.createPrint(System.err).set();

		// Initialize GLFW
		if (!glfwInit()) {
			throw new IllegalStateException("Unable to initialize GLFW");
		}

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

		// Create the window
		window = glfwCreateWindow(windowWidth, windowHeight,
				buildWindowTitle(), NULL, NULL);
		if (window == NULL) {
			throw new RuntimeException("Failed to create the GLFW window");
		}

		// Setup key callback
		glfwSetKeyCallback(window, (windowHandle, key, scancode, action, mods) -> {
			if (inputHandler != null) {
				inputHandler.handleKeyEvent(key, action);
			}
		});
		glfwSetCursorPosCallback(window, (windowHandle, x, y) -> {
			if (inputHandler != null) {
				inputHandler.handleMouseMove(x, y);
			}
		});
		glfwSetMouseButtonCallback(window, (windowHandle, button, action, mods) -> {
			if (inputHandler != null) {
				inputHandler.handleMouseButton(button, action);
			}
		});

		// Setup window resize callback
		glfwSetFramebufferSizeCallback(window, (windowHandle, width, height) -> {
			this.windowWidth = width;
			this.windowHeight = height;
			// Only reshape if GL context is initialized (avoids crash during window setup)
			if (graphicsManager.isGlInitialized()) {
				reshape(width, height);
			}
			// Schedule a window snap once the user finishes resizing
			if (!isSnappingWindowSize) {
				resizePendingSnap = true;
				lastResizeTimeNanos = System.nanoTime();
			}
		});

		// Setup window focus callback
		glfwSetWindowFocusCallback(window, (windowHandle, focused) -> {
			if (focused) {
				paused = false;
				gameLoop.resume();
			} else {
				paused = true;
				gameLoop.pause();
			}
		});

		// Setup window iconify callback
		glfwSetWindowIconifyCallback(window, (windowHandle, iconified) -> {
			if (iconified) {
				paused = true;
				gameLoop.pause();
			} else {
				paused = false;
				gameLoop.resume();
			}
		});

		// Get the thread stack and push a new frame
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);

			// Get the window size passed to glfwCreateWindow
			glfwGetWindowSize(window, pWidth, pHeight);

			// Get the resolution of the primary monitor
			GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

			// Center the window
			glfwSetWindowPos(
					window,
					(vidmode.width() - pWidth.get(0)) / 2,
					(vidmode.height() - pHeight.get(0)) / 2
			);
		}

		// Make the OpenGL context current
		glfwMakeContextCurrent(window);

		// Enable v-sync
		glfwSwapInterval(1);

		// Make the window visible
		glfwShowWindow(window);

		// This line is critical for LWJGL's interoperation with GLFW's
		// OpenGL context, or any context that is managed externally.
		GL.createCapabilities();

		try {
			graphicsManager.init(RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
			graphicsManager.setEngine(this);
			displayColorProfileController = DisplayColorProfileController.fromConfig(
					configService,
					graphicsManager,
					this::refreshDisplayPalettes);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// Create input handler and set it
		inputHandler = new InputHandler();
		setInputHandler(inputHandler);

		// Set window handle for clipboard operations (GLFW-based, no AWT dependency)
		debugOverlayManager.setWindowHandle(window);

		// Initial reshape and snap to integer scale (handles DPI-scaled framebuffer)
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);
			glfwGetFramebufferSize(window, pWidth, pHeight);
			this.windowWidth = pWidth.get(0);
			this.windowHeight = pHeight.get(0);
			reshape(windowWidth, windowHeight);
		}
		snapWindowToIntegerScale();

		// === Check legal disclaimer / master title screen before Phase 2 ===
		boolean showLegalDisclaimer = configService.getBoolean(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP);
		boolean masterTitleOnStartup = configService.getBoolean(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP);

		if (showLegalDisclaimer) {
			legalDisclaimerScreen = new com.openggf.game.LegalDisclaimerScreen(
					graphicsManager.getFadeManager());
			legalDisclaimerScreen.initialize();
			gameLoop.setGameMode(GameMode.LEGAL_DISCLAIMER);
			// master title (if enabled) or Phase 2 init is deferred to
			// exitLegalDisclaimer once the user dismisses the screen.
		} else if (masterTitleOnStartup) {
			masterTitleScreen = new MasterTitleScreen(configService);
			masterTitleScreen.initialize();
			gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
			// Skip Phase 2 entirely - will be called on game selection
		} else {
			// === PHASE 2: ROM loading, sprites, audio, level ===
			initializeGame();
		}

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

	private void refreshDisplayPalettes() {
		LevelManager activeLevelManager = GameServices.levelOrNull();
		if (activeLevelManager != null) {
			activeLevelManager.reloadLevelPalettes();
		}
	}

	/**
	 * Phase 2 initialization: loads ROM, creates sprites, initializes audio, loads level.
	 * Called either directly from init() (no master title screen) or from
	 * exitMasterTitleScreen() after game selection.
	 */
	public void initializeGame() {
		GameModule module;
		try {
			Rom rom = romManager.getRom();
			module = romDetectionService
				.detectAndCreateModule(rom)
				.orElseGet(() -> {
					LOGGER.warning("ROM detection failed during game initialization, using default Sonic 2 module");
					return new Sonic2GameModule();
				});
		} catch (IOException e) {
			throw new RuntimeException("Failed to load ROM during game initialization", e);
		}
		GameplayModeContext gameplayMode = SessionManager.openGameplaySession(module);
		initializeGameplayRuntime(gameplayMode, true);
		boolean generationRan = maybeGenerateDonatedDataSelectImagesBeforeStartupMode(module);
		if (generationRan) {
			// Preview capture loaded full levels into the LevelManager and
			// GraphicsManager, corrupting the pattern atlas, palette textures,
			// DPLC banks, sprite art, and other GPU/manager state.
			// Reset GPU state (pattern atlas + palette textures) and rebuild
			// the gameplay mode so everything starts from a clean slate.
			graphicsManager.resetPatternAndPaletteState();
			gameplayMode = SessionManager.openGameplaySession(module);
			initializeGameplayRuntime(gameplayMode, false);
		} else {
			graphicsManager.runPendingRenderThreadTasks();
		}
		enterConfiguredStartupMode();
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

		boolean masterTitleOnStartup = configService.getBoolean(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP);
		if (masterTitleOnStartup) {
			masterTitleScreen = new MasterTitleScreen(configService);
			masterTitleScreen.initialize();
			gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
		} else {
			initializeGame();
		}

		graphicsManager.getFadeManager().startFadeFromBlack(null);
	}

	private void resetForGameplayFromMasterTitle() {
		GameServices.module().resetModuleScopedState();
		SessionManager.clear();
		romManager.close();
		GameModuleRegistry.reset();
		audioManager.resetState();
		crossGameFeatureProvider.resetState();
		debugOverlayManager.resetState();
		RenderContext.reset();
		gameLoop.resetModuleScopedProviders();
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
		masterTitleScreen = new MasterTitleScreen(configService);
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
		if (!isEditorEnabled()) {
			throw new IllegalStateException("Level editor is disabled by configuration.");
		}
		ensureRuntimeBound();
		prepareMutableEditorLevel();
		primeEditorSelection(playerX, playerY);
		levelEditorController.setWorldCursor(new EditorCursorState(playerX, playerY));
		// Camera bounds are world-derived but stored on the gameplay-mode
		// camera, which gets reset when the gameplay mode is destroyed.
		short savedMinX = camera != null ? camera.getMinX() : 0;
		short savedMaxX = camera != null ? camera.getMaxX() : 0;
		short savedMinY = camera != null ? camera.getMinY() : 0;
		short savedMaxY = camera != null ? camera.getMaxY() : 0;
		Level editorLevel = levelEditorController.currentLevel();
		SessionManager.enterEditorMode(new EditorCursorState(playerX, playerY), stash);
		gameplayMode = null;
		gameLoop.setGameplayMode(null);
		bindEditorLevelView(editorLevel, savedMinX, savedMaxX, savedMinY, savedMaxY);
		syncEditorState();
		gameLoop.setGameMode(GameMode.EDITOR);
	}

	public void resumePlaytestFromEditor() {
		editorInputHandler.finishActiveStroke();
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
			new EditorSaveManager(Path.of("saves"))
					.save(module.getGameId(), worldSession.getCurrentZone(), worldSession.getCurrentAct(), mutableLevel);
			return true;
		} catch (IOException e) {
			LOGGER.warning("Failed to save editor edits: " + e.getMessage());
			return false;
		}
	}

	public void startGameplayFromBeginning() {
		GameplayModeContext gameplay = SessionManager.restartGameplayFromBeginning();
		initializeGameplayRuntime(gameplay, false);
		loadDefaultStartingLevel(false);
		gameLoop.setGameMode(GameMode.LEVEL);
	}

	private void initializeGameplayRuntime(GameplayModeContext gameplayMode, boolean initializeGlobalGameplayServices) {
		GameModule module = gameplayMode.getWorldSession().getGameModule();
		GameplaySessionFactory.attachManagers(gameplayMode, EngineServices.current());
		bindGameplayMode(gameplayMode);
		gameplayMode.getGameStateManager().configureSpecialStageProgress(
				module.getSpecialStageCycleCount(),
				module.getChaosEmeraldCount());

		if (initializeGlobalGameplayServices) {
			initializeGlobalGameplayServices();
		}

		GameplayTeamBootstrap.BootstrappedTeam team = GameplayTeamBootstrap.registerActiveTeam(
				module, spriteManager, configService);
		camera.setFocusedSprite(team.mainSprite());
		camera.updatePosition(true);
	}

	private void initializeGlobalGameplayServices() {
		if (configService.getBoolean(SonicConfiguration.AUDIO_ENABLED)) {
			audioManager.setBackend(new LWJGLAudioBackend(configService, profiler));
		}

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

	private boolean maybeGenerateDonatedDataSelectImagesBeforeStartupMode(GameModule module) {
		boolean crossGameEnabled = configService.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED);
		String donorCode = configService.getString(SonicConfiguration.CROSS_GAME_SOURCE);
		boolean s3kConfiguredDonor = "s3k".equalsIgnoreCase(donorCode);
		if (module == null || !crossGameEnabled || !s3kConfiguredDonor || !CrossGameFeatureProvider.isS3kDonorActive()) {
			return false;
		}
		Optional<DonatedDataSelectWarmupTask> warmup = module.getDonatedDataSelectWarmupTask();
		if (warmup.isEmpty()) {
			return false;
		}
		DonatedDataSelectWarmupTask task = warmup.orElseThrow();
		task.start();
		pumpRenderThreadTasksUntilSettled(task::isRunning);
		return true;
	}

	private void pumpRenderThreadTasksUntilSettled(java.util.function.BooleanSupplier generationRunning) {
		if (generationRunning == null) {
			return;
		}
		while (generationRunning.getAsBoolean()) {
			graphicsManager.runPendingRenderThreadTasks();
			Thread.onSpinWait();
		}
		graphicsManager.runPendingRenderThreadTasks();
	}

	private void enterConfiguredStartupMode() {
		boolean titleScreenOnStartup = configService.getBoolean(SonicConfiguration.TITLE_SCREEN_ON_STARTUP);
		boolean levelSelectOnStartup = configService.getBoolean(SonicConfiguration.LEVEL_SELECT_ON_STARTUP);
		if (titleScreenOnStartup) {
			gameLoop.initializeTitleScreenMode();
		} else if (levelSelectOnStartup) {
			gameLoop.initializeLevelSelectMode();
		} else {
			loadDefaultStartingLevel(true);
		}
	}

	private void loadDefaultStartingLevel(boolean requireRom) {
		if (!requireRom && !romManager.isRomAvailable()) {
			return;
		}
		try {
			levelManager.loadZoneAndAct(0, 0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void launchGameplayFromDataSelect(com.openggf.game.dataselect.DataSelectAction action) {
		GameModule module = SessionManager.requireCurrentGameModule();
		SaveManager saveManager = new SaveManager(Path.of("saves"));
		Map<String, Object> loadedPayload = loadDataSelectPayload(module, action, saveManager);
		com.openggf.game.save.SaveSessionContext saveContext = createDataSelectSaveContext(module, action, saveManager);

		GameplayModeContext gameplay = SessionManager.openGameplaySession(module, saveContext);
		initializeGameplayRuntime(gameplay, false);
		loadLevelFromDataSelect(action.zone(), action.act());
		restoreGameplayModeFromDataSelectPayload(gameplayMode, loadedPayload);
		gameLoop.setGameMode(GameMode.LEVEL);

		dataSelectLaunchSaveReason(action.type())
				.ifPresent(gameLoop::requestSaveForCurrentSession);
	}

	static Optional<com.openggf.game.save.SaveReason> dataSelectLaunchSaveReason(
			com.openggf.game.dataselect.DataSelectActionType actionType) {
		return switch (actionType) {
			case NEW_SLOT_START -> Optional.of(com.openggf.game.save.SaveReason.NEW_SLOT_START);
			case LOAD_SLOT -> Optional.of(com.openggf.game.save.SaveReason.EXISTING_SLOT_LOAD);
			case CLEAR_RESTART -> Optional.of(com.openggf.game.save.SaveReason.CLEAR_RESTART_COMMIT);
			case NONE, NO_SAVE_START, DELETE_SLOT -> Optional.empty();
		};
	}

	private void loadLevelFromDataSelect(int zone, int act) {
		try {
			levelManager.loadZoneAndAct(zone, act);
		} catch (IOException e) {
			throw new RuntimeException("Failed to load zone " + zone + " act " + act + " from data select", e);
		}
	}

	static com.openggf.game.save.SaveSessionContext createDataSelectSaveContext(
			GameModule module,
			com.openggf.game.dataselect.DataSelectAction action,
			SaveManager saveManager) {
		String gameCode = switch (module.getGameId()) {
			case S1 -> "s1";
			case S2 -> "s2";
			case S3K -> "s3k";
		};
		Map<String, Object> payload = loadDataSelectPayload(module, action, saveManager);
		SelectedTeam team = payload == null ? action.team() : teamFromPayload(payload, action.team());
		com.openggf.game.save.SaveSessionContext context =
				action.slot() > 0
						? com.openggf.game.save.SaveSessionContext.forSlot(
								gameCode, action.slot(), team, action.zone(), action.act())
						: com.openggf.game.save.SaveSessionContext.noSave(
								gameCode, team, action.zone(), action.act());
		if (payload != null && Boolean.TRUE.equals(payload.get("clear"))) {
			context.markClear();
		}
		return context;
	}

	private static Map<String, Object> loadDataSelectPayload(
			GameModule module,
			com.openggf.game.dataselect.DataSelectAction action,
			SaveManager saveManager) {
		if (action.slot() <= 0) {
			return null;
		}
		return switch (action.type()) {
			case LOAD_SLOT, CLEAR_RESTART -> {
				try {
					String gameCode = switch (module.getGameId()) {
						case S1 -> "s1";
						case S2 -> "s2";
						case S3K -> "s3k";
					};
					SaveSlotSummary summary = saveManager.readSlotSummary(gameCode, action.slot());
					yield summary.state() == com.openggf.game.save.SaveSlotState.EMPTY ? null : summary.payload();
				} catch (IOException e) {
					throw new RuntimeException("Failed to read save slot " + action.slot() + " for data select launch", e);
				}
			}
			case NONE, NO_SAVE_START, NEW_SLOT_START, DELETE_SLOT -> null;
		};
	}

	static void restoreGameplayModeFromDataSelectPayload(GameplayModeContext gameplayMode, Map<String, Object> payload) {
		if (gameplayMode == null || payload == null) {
			return;
		}
		int lives = readInt(payload, "lives", gameplayMode.getGameStateManager().getLives());
		int continues = readInt(payload, "continues", gameplayMode.getGameStateManager().getContinues());
		gameplayMode.getGameStateManager().restoreSaveProgress(
				lives,
				continues,
				readIntList(payload.get("chaosEmeralds")),
				readIntList(payload.get("superEmeralds")));
	}

	private static SelectedTeam teamFromPayload(Map<String, Object> payload, SelectedTeam fallback) {
		Object mainRaw = payload.get("mainCharacter");
		if (!(mainRaw instanceof String main)) {
			return fallback;
		}
		Object sidekicksRaw = payload.get("sidekicks");
		List<String> sidekicks = sidekicksRaw instanceof List<?>
				? ((List<?>) sidekicksRaw).stream().map(String::valueOf).toList()
				: List.of();
		return new SelectedTeam(main, sidekicks);
	}

	private static int readInt(Map<String, Object> payload, String key, int fallback) {
		Object value = payload.get(key);
		return value instanceof Number number ? number.intValue() : fallback;
	}

	private static List<Integer> readIntList(Object raw) {
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		List<Integer> values = new ArrayList<>();
		for (Object value : list) {
			if (value instanceof Number number) {
				values.add(number.intValue());
			}
		}
		return List.copyOf(values);
	}

	public void toggleEditorPlaytestMode() {
		if (getCurrentGameMode() == GameMode.EDITOR) {
			resumePlaytestFromEditor();
			return;
		}
		if (getCurrentGameMode() != GameMode.LEVEL) {
			return;
		}
		if (!isEditorEnabled()) {
			return;
		}
		AbstractPlayableSprite player = resolveMainPlayableSprite();
		if (player == null) {
			return;
		}
		EditorPlaytestStash stash = capturePlaytestStash(player);
		enterEditorFromCurrentPlayer(stash, player.getCentreX(), player.getCentreY());
	}

	private boolean isEditorEnabled() {
		return configService.getBoolean(SonicConfiguration.EDITOR_ENABLED);
	}

	private void bindGameplayMode(GameplayModeContext gameplayMode) {
		this.gameplayMode = gameplayMode;
		this.camera = gameplayMode.getCamera();
		this.spriteManager = gameplayMode.getSpriteManager();
		this.levelManager = gameplayMode.getLevelManager();
		gameLoop.setGameplayMode(gameplayMode);
	}

	private void bindEditorLevelView(Level editorLevel,
	                                 short minX,
	                                 short maxX,
	                                 short minY,
	                                 short maxY) {
		EditorModeContext editorMode = SessionManager.getCurrentEditorMode();
		if (editorMode == null || !editorMode.isEditorRuntimeReady()) {
			throw new IllegalStateException("Editor mode is not ready for level rendering.");
		}
		Camera editorCamera = editorMode.getCamera();
		SpriteManager editorSprites = editorMode.getSpriteManager();
		LevelManager editorLevelManager = editorMode.getLevelManager();
		editorLevelManager.restoreEditorLevelView(editorLevel);
		editorCamera.setMinX(minX);
		editorCamera.setMaxX(maxX);
		editorCamera.setMinY(minY);
		editorCamera.setMaxY(maxY);
		this.camera = editorCamera;
		this.spriteManager = editorSprites;
		this.levelManager = editorLevelManager;
	}

	private void ensureRuntimeBound() {
		if (gameplayMode != null) {
			return;
		}
		GameplayModeContext currentGameplayMode = SessionManager.getCurrentGameplayMode();
		if (currentGameplayMode == null) {
			throw new IllegalStateException("No active gameplay mode");
		}
		bindGameplayMode(currentGameplayMode);
	}

	private AbstractPlayableSprite resolveMainPlayableSprite() {
		ensureRuntimeBound();
		String mainCode = ActiveGameplayTeamResolver.resolveMainCharacterCode(configService);
		var sprite = spriteManager.getSprite(mainCode);
		if (sprite instanceof AbstractPlayableSprite playable) {
			return playable;
		}
		return null;
	}

	private EditorPlaytestStash capturePlaytestStash(AbstractPlayableSprite player) {
		boolean facingRight = player.getDirection() != Direction.LEFT;
		int shieldState = player.getShieldType() == null ? 0 : player.getShieldType().ordinal() + 1;
		return new EditorPlaytestStash(
				player.getCentreX(),
				player.getCentreY(),
				player.getXSpeed(),
				player.getYSpeed(),
				facingRight,
				player.getRingCount(),
				shieldState);
	}

	private void prepareMutableEditorLevel() {
		Level currentLevel = levelManager.getCurrentLevel();
		if (currentLevel == null) {
			return;
		}

		MutableLevel mutableLevel = currentLevel instanceof MutableLevel existing
				? existing
				: MutableLevel.snapshot(currentLevel);
		if (mutableLevel != currentLevel) {
			levelManager.setLevel(mutableLevel);
		}
		levelEditorController.attachLevel(mutableLevel);
	}

	private void primeEditorSelection(int playerX, int playerY) {
		Level currentLevel = levelManager.getCurrentLevel();
		if (currentLevel == null) {
			return;
		}

		int blockIndex = levelManager.getBlockIdAt(playerX, playerY);
		if (blockIndex < 0 || blockIndex >= currentLevel.getBlockCount()) {
			return;
		}

		levelEditorController.selectBlock(blockIndex);
		levelEditorController.selectChunk(0);
	}

	private void synchronizeEditorOverlayDepth() {
		editorOverlayRenderer.setHierarchyDepth(levelEditorController.depth());
	}

	public LevelEditorController getLevelEditorController() {
		return levelEditorController;
	}

	public void syncEditorState() {
		EditorModeContext editorMode = SessionManager.getCurrentEditorMode();
		if (editorMode == null) {
			return;
		}

		EditorCursorState cursor = levelEditorController.worldCursor();
		editorMode.setCursor(cursor);
		synchronizeEditorOverlayDepth();

		if (levelEditorController.depth() == EditorHierarchyDepth.WORLD && camera != null) {
			camera.setX(clampCameraAxisWithWrap(cursor.x() - 152, camera.getMinX(), camera.getMaxX()));
			camera.setY(clampCameraAxisWithWrap(cursor.y() - 96, camera.getMinY(), camera.getMaxY()));
		}
	}

	private void repairEditorCursorForResume() {
		EditorModeContext editorMode = SessionManager.getCurrentEditorMode();
		if (editorMode == null) {
			return;
		}

		EditorCursorState repaired = clampCursorToCurrentLevel(levelEditorController.worldCursor());
		levelEditorController.setWorldCursor(repaired);
		editorMode.setCursor(levelEditorController.worldCursor());
	}

	private EditorCursorState clampCursorToCurrentLevel(EditorCursorState cursor) {
		if (cursor == null || levelManager == null || levelManager.getCurrentLevel() == null) {
			return cursor;
		}

		Level level = levelManager.getCurrentLevel();
		return new EditorCursorState(
				clampToBounds(cursor.x(), level.getMinX(), level.getMaxX()),
				clampToBounds(cursor.y(), level.getMinY(), level.getMaxY()));
	}

	private int clampToBounds(int value, int min, int max) {
		if (max < min) {
			return value < min ? min : value;
		}
		return Math.max(min, Math.min(max, value));
	}

	private short clampCameraAxisWithWrap(int value, short min, short max) {
		if (max < min) {
			return (short) (value < min ? min : value);
		}
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return (short) value;
	}

	private void applyResumedPlaytestState(GameplayModeContext gameplay) {
		AbstractPlayableSprite player = resolveMainPlayableSprite();
		if (player == null) {
			return;
		}

		player.setCentreX((short) gameplay.getSpawnX());
		player.setCentreY((short) gameplay.getSpawnY());

		if (gameplay.getResumeStash().isPresent()) {
			EditorPlaytestStash stash = gameplay.getResumeStash().orElseThrow();
			player.setXSpeed((short) stash.xVelocity());
			player.setYSpeed((short) stash.yVelocity());
			player.setDirection(stash.facingRight() ? Direction.RIGHT : Direction.LEFT);
			player.setRingCount(stash.rings());
			player.clearPowerUps();
			ShieldType shieldType = decodeShieldState(stash.shieldState());
			if (shieldType != null) {
				player.giveShield(shieldType);
			}
		}

		camera.setFocusedSprite(player);
		camera.updatePosition(true);
	}

	private ShieldType decodeShieldState(int shieldState) {
		if (shieldState <= 0) {
			return null;
		}
		ShieldType[] values = ShieldType.values();
		int index = shieldState - 1;
		if (index < 0 || index >= values.length) {
			return null;
		}
		return values[index];
	}

	private void reshape(int width, int height) {
		int nativeW = (int) realWidth;   // 320
		int nativeH = (int) realHeight;  // 224

		// Find the largest integer scale that fits both dimensions
		int scale = Math.max(1, Math.min(width / nativeW, height / nativeH));
		viewportWidth = scale * nativeW;
		viewportHeight = scale * nativeH;

		// Center the integer-scaled viewport within the framebuffer
		viewportX = (width - viewportWidth) / 2;
		viewportY = (height - viewportHeight) / 2;

		// Set the viewport to the integer-scaled area
		glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Cache viewport dimensions in GraphicsManager
		graphicsManager.setViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Setup orthographic projection using JOML - stored for shader access
		projectionMatrix.identity().ortho2D(0, (float) projectionWidth, 0, (float) realHeight);
		projectionMatrix.get(matrixBuffer);
	}

	/**
	 * Snaps the window size to the nearest integer multiple of the native resolution
	 * so every game pixel maps to exactly NxN screen pixels with no fractional scaling.
	 * Rounds UP when the window grew (e.g. DPI increase) and DOWN when it shrank,
	 * preventing progressive shrinking across monitor moves.
	 */
	private void snapWindowToIntegerScale() {
		int nativeW = (int) realWidth;
		int nativeH = (int) realHeight;

		// Get the current monitor's usable resolution as an upper bound
		long monitor = glfwGetWindowMonitor(window);
		if (monitor == NULL) {
			monitor = glfwGetPrimaryMonitor();
		}
		GLFWVidMode vidmode = glfwGetVideoMode(monitor);
		int maxScale = Math.min(vidmode.width() / nativeW, vidmode.height() / nativeH);

		double currentScale = Math.min((double) windowWidth / nativeW, (double) windowHeight / nativeH);

		int scale;
		if (lastSnappedScale > 0 && currentScale > lastSnappedScale) {
			// Window grew (DPI increase or manual resize up) - round up
			scale = (int) Math.ceil(currentScale);
		} else {
			// Window shrank, unchanged, or initial load - round down
			scale = (int) currentScale;
		}
		scale = Math.max(1, Math.min(scale, maxScale));

		lastSnappedScale = scale;
		int targetW = scale * nativeW;
		int targetH = scale * nativeH;
		if (targetW != windowWidth || targetH != windowHeight) {
			isSnappingWindowSize = true;
			glfwSetWindowSize(window, targetW, targetH);
			isSnappingWindowSize = false;
		}
	}

	private void loop() {
		long frameTimeNanos = 1_000_000_000L / targetFps;
		long accumulator = 0;
		long previousTime = System.nanoTime();

		while (!glfwWindowShouldClose(window)) {
			long currentTime = System.nanoTime();
			long deltaTime = currentTime - previousTime;
			previousTime = currentTime;

			if (!paused) {
				accumulator += deltaTime;

				// Process exactly one frame per target interval
				if (accumulator >= frameTimeNanos) {
					display();
					glfwSwapBuffers(window);
					// Preserve remainder to prevent timing drift
					accumulator -= frameTimeNanos;

					// Clamp accumulator to prevent spiral of death if frames take too long
					if (accumulator > frameTimeNanos) {
						accumulator = frameTimeNanos;
					}
				}
			}

			glfwPollEvents();

			// Snap window to nearest integer scale once resize drag ends (~200ms debounce)
			if (resizePendingSnap && System.nanoTime() - lastResizeTimeNanos > 200_000_000L) {
				resizePendingSnap = false;
				snapWindowToIntegerScale();
			}

			// Note: inputHandler.update() is called at the end of GameLoop.step()
			// to properly handle isKeyPressed() edge detection. Do NOT call update()
			// here or isKeyPressed() will always return false.

			if (paused) {
				// When unfocused/minimized, sleep longer — no need for frame-precise
				// timing. Just poll events ~30 times/sec to stay responsive to focus.
				try {
					Thread.sleep(33);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				// Reset accumulator so we don't process a burst of frames on resume
				accumulator = 0;
				previousTime = System.nanoTime();
				continue;
			}

			// Hybrid sleep: sleep most of the wait time, then spin-wait for precision
			long remainingTime = frameTimeNanos - accumulator;
			if (remainingTime > 2_000_000) {
				// Sleep for most of the remaining time, leaving ~1ms for spin-wait
				try {
					Thread.sleep((remainingTime - 1_000_000) / 1_000_000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

			// Spin-wait the final portion for sub-millisecond precision
			// Calculate target time for next frame check
			long targetTime = previousTime + (frameTimeNanos - accumulator);
			while (System.nanoTime() < targetTime) {
				Thread.onSpinWait();
			}
		}
	}

	/**
	 * Called each frame to render the game.
	 */
	private void display() {
		profiler.setEnabled(debugViewEnabled
				&& !isNativeImage()
				&& debugOverlayManager.isEnabled(DebugOverlayToggle.PERFORMANCE));
		profiler.beginFrame();

		// Clear the entire window to black first (for letterbox/pillarbox bars)
		glViewport(0, 0, windowWidth, windowHeight);
		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		// Set the viewport to the aspect-ratio-correct area for game rendering
		glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Update projection matrix for current mode - stored for shader access
		projectionMatrix.identity().ortho2D(0, (float) projectionWidth, 0, (float) realHeight);
		projectionMatrix.get(matrixBuffer);

		renderDispatcher.applyClearColor(getCurrentGameMode(), clearActions);
		glScissor(viewportX, viewportY, viewportWidth, viewportHeight);
		glEnable(GL_SCISSOR_TEST);
		glClear(GL_COLOR_BUFFER_BIT);
		glDisable(GL_SCISSOR_TEST);

		glColorMask(true, true, true, true);

		// Update fade via unified UI render pipeline — process fade state first so
		// that callbacks (e.g. credits transition flags) are available to step()
		// in the same frame, preventing 1-frame gaps where the overlay would drop.
		var uiPipeline = graphicsManager.getUiRenderPipeline();
		if (uiPipeline != null) {
			uiPipeline.updateFade();
		}

		profiler.beginSection("update");
		if (displayColorProfileController != null) {
			displayColorProfileController.update(inputHandler);
		}
		update();
		profiler.endSection("update");

		profiler.beginSection("render");
		graphicsManager.runPendingRenderThreadTasks();
		draw();
		graphicsManager.flush();
		profiler.endSection("render");

		// Render screen fade overlay via unified UI render pipeline
		if (uiPipeline != null) {
			uiPipeline.renderFadePass();
		}

		renderDisplayColorProfileNotification();

		// Trace Test Mode HUD: drawn AFTER the fade pass so counters and
		// TRACE COMPLETE remain readable during fade-to-black teardown.
		TraceSessionLauncher traceSession = TraceSessionLauncher.active();
		if (traceSession != null) {
			traceHudTextRenderer.setProjectionMatrix(getProjectionMatrixBuffer());
			traceSession.render(traceHudTextRenderer);
		} else if (gameLoop != null) {
			traceHudTextRenderer.setProjectionMatrix(getProjectionMatrixBuffer());
			gameLoop.renderLiveRewindHud(traceHudTextRenderer);
		}
		if (getCurrentGameMode() == GameMode.CREDITS_DEMO) {
			EndingProvider provider = gameLoop.getEndingProvider();
			if (provider != null && provider.shouldRenderDemoSpritesOverFade()) {
				// Reset shader/texture state before the post-fade sprite pass: the fade
				// shader binds a program and modifies blend/depth state, and even though
				// FadeManager restores blend on its own, we must not rely on the next pass
				// inheriting whatever shader/texture bindings the fade pass left active.
				graphicsManager.resetForFixedFunction();
				levelManager.renderSpriteObjectPass(spriteManager, true);
				graphicsManager.flush();
			}
		}

		boolean playbackHud = playbackDebugManager.isHudVisible();
		boolean needsOverlay = (getCurrentGameMode() == GameMode.SPECIAL_STAGE) ||
				((debugViewEnabled || playbackHud) && getCurrentGameMode() != GameMode.SPECIAL_STAGE);

		if (needsOverlay) {
			prepareOverlayState();
		}

		profiler.beginSection("debug");
		if (getCurrentGameMode() == GameMode.SPECIAL_STAGE) {
			SpecialStageProvider ssProvider = gameLoop.getActiveSpecialStageProvider();
			if (ssProvider.isAlignmentTestMode()) {
				ssProvider.renderAlignmentOverlay(windowWidth, windowHeight);
			} else {
				ssProvider.renderLagCompensationOverlay(windowWidth, windowHeight);
			}
		} else if (debugViewEnabled || playbackHud) {
			getDebugRenderer().updateViewport(viewportWidth, viewportHeight);
			getDebugRenderer().renderDebugInfo();

			// Clean up GL state after debug rendering to prevent macOS event loop issues
			glBindVertexArray(0);
			glBindBuffer(GL_ARRAY_BUFFER, 0);
			glUseProgram(0);
		}
		profiler.endSection("debug");

		// F12 screenshot capture (after all rendering is complete)
		if (inputHandler != null && inputHandler.isKeyPressed(GLFW_KEY_F12)) {
			try {
				String timestamp = java.time.LocalDateTime.now()
						.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
				java.nio.file.Path path = java.nio.file.Path.of("screenshot_" + timestamp + ".png");
				ScreenshotCapture.captureAndSavePNG(viewportWidth, viewportHeight, path);
				LOGGER.info("Screenshot saved: " + path);
			} catch (Exception e) {
				LOGGER.warning("Screenshot failed: " + e.getMessage());
			}
		}

		profiler.endFrame();
		overlayStateReady = false;
	}

	private void applySpecialStageClearColor() {
		SpecialStageProvider ssProviderForClear = gameLoop.getActiveSpecialStageProvider();
		if (ssProviderForClear != null) {
			ssProviderForClear.setClearColor();
		} else {
			applyBlackClearColor();
		}
	}

	private void applySpecialStageResultsClearColor() {
		glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
	}

	private void applyTitleScreenClearColor() {
		TitleScreenProvider titleScreen = gameLoop.getTitleScreenProvider();
		if (titleScreen != null) {
			titleScreen.setClearColor();
		}
	}

	private void applyLevelSelectClearColor() {
		LevelSelectProvider levelSelect = gameLoop.getLevelSelectProvider();
		if (levelSelect != null) {
			levelSelect.setClearColor();
		}
	}

	private void applyDataSelectClearColor() {
		DataSelectProvider dataSelect = gameLoop.getDataSelectProvider();
		if (dataSelect != null) {
			dataSelect.setClearColor();
		} else {
			applyBlackClearColor();
		}
	}

	private void applyEndingClearColor() {
		EndingProvider ending = gameLoop.getEndingProvider();
		if (ending != null) {
			ending.setClearColor();
		} else {
			applyBlackClearColor();
		}
	}

	private void applyBlackClearColor() {
		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
	}

	private void applyLevelClearColor() {
		levelManager.setClearColor();
	}

	private final class EngineClearActions implements EngineRenderDispatcher.ClearActions {
		@Override public void specialStage() { applySpecialStageClearColor(); }
		@Override public void specialStageResults() { applySpecialStageResultsClearColor(); }
		@Override public void titleScreen() { applyTitleScreenClearColor(); }
		@Override public void levelSelect() { applyLevelSelectClearColor(); }
		@Override public void dataSelect() { applyDataSelectClearColor(); }
		@Override public void ending() { applyEndingClearColor(); }
		@Override public void black() { applyBlackClearColor(); }
		@Override public void level() { applyLevelClearColor(); }
	}

	private final class EngineDrawActions implements EngineRenderDispatcher.DrawActions {
		@Override public void legalDisclaimer() { drawLegalDisclaimer(); }
		@Override public void masterTitle() { drawMasterTitle(); }
		@Override public void editor() { drawEditor(); }
		@Override public void specialStage() { drawSpecialStage(); }
		@Override public void specialStageResults() { drawSpecialStageResults(); }
		@Override public void titleScreen() { drawTitleScreen(); }
		@Override public void levelSelect() { drawLevelSelect(); }
		@Override public void dataSelect() { drawDataSelect(); }
		@Override public void endingCutscene() { drawEndingCutscene(); }
		@Override public void creditsText() { drawCreditsText(); }
		@Override public void creditsDemo() { drawCreditsDemo(); }
		@Override public void tryAgainEnd() { drawTryAgainEnd(); }
		@Override public void titleCard() { drawTitleCardMode(); }
		@Override public void debugPatterns() { drawDebugPatterns(); }
		@Override public void debugBlocks() { drawDebugBlocks(); }
		@Override public void level() { drawLevel(); }
	}

	private void renderDisplayColorProfileNotification() {
		if (displayColorProfileController == null) {
			return;
		}
		String text = displayColorProfileController.notificationText();
		if (text == null) {
			return;
		}
		float scale = 1.0f;
		int y = 224 - traceHudTextRenderer.lineHeight(scale) - 4;
		traceHudTextRenderer.setProjectionMatrix(getProjectionMatrixBuffer());
		traceHudTextRenderer.drawShadowedText(text, 4, y, DebugColor.YELLOW, scale);
	}

	/**
	 * Updates the game state by one frame.
	 */
	public void update() {
		gameLoop.step();
	}

	/**
	 * Gets the current game mode from the game loop.
	 */
	public GameMode getCurrentGameMode() {
		return gameLoop.getCurrentGameMode();
	}

	/**
	 * Gets the game loop instance for testing purposes.
	 */
	public GameLoop getGameLoop() {
		return gameLoop;
	}

	/**
	 * Static accessor used by {@link com.openggf.TraceSessionLauncher} (and
	 * other {@code com.openggf.*} classes) to reach the singleton game
	 * loop without going through a tier-1 service.
	 */
	public static GameLoop currentGameLoop() {
		return instance != null ? instance.gameLoop : null;
	}

	public void draw() {
		renderDispatcher.draw(getCurrentGameMode(), debugViewEnabled, debugState, drawActions);
	}

	private void drawLegalDisclaimer() {
		resetCameraForScreenSpaceIfPresent();
		if (legalDisclaimerScreen != null) {
			legalDisclaimerScreen.setProjectionMatrix(getProjectionMatrixBuffer());
			legalDisclaimerScreen.draw();
		}
	}

	private void drawMasterTitle() {
		resetCameraForScreenSpaceIfPresent();
		if (masterTitleScreen != null) {
			masterTitleScreen.setViewportWidth((int) realWidth);
			masterTitleScreen.setProjectionMatrix(getProjectionMatrixBuffer());
			masterTitleScreen.draw();
		}
	}

	private void drawEditor() {
		flushEditorDirtyRegionsForRendering();
		levelManager.drawWithSpritePriority(spriteManager);
		editorOverlayRenderer.renderWorldSpaceOverlay();
		graphicsManager.flush();
		graphicsManager.resetForFixedFunction();
		prepareOverlayState();
		editorOverlayRenderer.renderScreenSpaceOverlay();
		graphicsManager.flushScreenSpace();
	}

	private void drawSpecialStage() {
		SpecialStageProvider ssProvider = gameLoop.getActiveSpecialStageProvider();
		if (ssProvider.isSpriteDebugMode()) {
			SpecialStageDebugProvider debugProvider = ssProvider.getDebugProvider();
			if (debugProvider != null) {
				debugProvider.draw();
			} else {
				ssProvider.draw();
			}
		} else {
			ssProvider.draw();
		}
	}

	private void drawSpecialStageResults() {
		var resultsScreen = gameLoop.getResultsScreen();
		if (resultsScreen == null) {
			return;
		}
		camera.setX((short) 0);
		camera.setY((short) 0);

		graphicsManager.beginPatternBatch();

		resultsCommands.clear();
		resultsScreen.appendRenderCommands(resultsCommands);

		graphicsManager.flushPatternBatch();

		if (!resultsCommands.isEmpty()) {
			graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, resultsCommands));
		}

		graphicsManager.flushScreenSpace();
	}

	private void drawTitleScreen() {
		resetCameraForScreenSpace();
		TitleScreenProvider titleScreen = gameLoop.getTitleScreenProvider();
		if (titleScreen != null) {
			titleScreen.draw();
		}
	}

	private void drawLevelSelect() {
		resetCameraForScreenSpace();
		LevelSelectProvider levelSelect = gameLoop.getLevelSelectProvider();
		if (levelSelect != null) {
			levelSelect.draw();
		}
	}

	private void drawDataSelect() {
		resetCameraForScreenSpace();
		DataSelectProvider dataSelect = gameLoop.getDataSelectProvider();
		if (dataSelect != null) {
			dataSelect.draw();
		}
	}

	private void drawEndingCutscene() {
		resetCameraForScreenSpace();
		EndingProvider provider = gameLoop.getEndingProvider();
		if (provider == null) {
			return;
		}
		if (provider.needsLevelBackground()) {
			levelManager.renderEndingBackground(
					provider.getBackgroundVscroll(),
					provider.getBackdropColorOverride());
			graphicsManager.flush();
		}
		provider.draw();
	}

	private void drawCreditsText() {
		resetCameraForScreenSpace();
		EndingProvider provider = gameLoop.getEndingProvider();
		if (provider != null) {
			provider.draw();
		}
	}

	private void drawCreditsDemo() {
		EndingProvider provider = gameLoop.getEndingProvider();
		boolean includeSprites = provider == null || !provider.shouldRenderDemoSpritesOverFade();
		levelManager.drawWithSpritePriority(spriteManager, includeSprites);
	}

	private void drawTryAgainEnd() {
		resetCameraForScreenSpace();
		EndingProvider provider = gameLoop.getEndingProvider();
		if (provider != null) {
			provider.draw();
		}
	}

	private void drawTitleCardMode() {
		levelManager.drawWithSpritePriority(spriteManager);

		graphicsManager.flush();
		graphicsManager.resetForFixedFunction();

		TitleCardProvider titleCardProvider = gameLoop.getTitleCardProvider();
		if (titleCardProvider != null) {
			titleCardProvider.draw();
			graphicsManager.flushScreenSpace();
		}
	}

	private void drawDebugPatterns() {
		levelManager.drawAllPatterns();
		drawActiveLevelTitleCardOverlay();
	}

	private void drawDebugBlocks() {
		levelManager.draw();
		drawActiveLevelTitleCardOverlay();
	}

	private void drawLevel() {
		levelManager.drawWithSpritePriority(spriteManager);
		drawActiveLevelTitleCardOverlay();
	}

	private void drawActiveLevelTitleCardOverlay() {
		TitleCardProvider titleCardProvider = gameLoop.getTitleCardProvider();
		if (titleCardProvider != null && titleCardProvider.isOverlayActive()) {
			graphicsManager.flush();
			graphicsManager.resetForFixedFunction();
			titleCardProvider.draw();
			graphicsManager.flushScreenSpace();
		}
	}

	private void resetCameraForScreenSpaceIfPresent() {
		if (camera != null) {
			camera.setX((short) 0);
			camera.setY((short) 0);
		}
	}

	private void resetCameraForScreenSpace() {
		camera.setX((short) 0);
		camera.setY((short) 0);
	}

	void flushEditorDirtyRegionsForRendering() {
		levelManager.processDirtyRegions();
	}

	private void prepareOverlayState() {
		if (overlayStateReady) {
			return;
		}
		glActiveTexture(GL_TEXTURE0);
		glUseProgram(0);
		glDisable(GL_DEPTH_TEST);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE0);

		glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Update projection matrix for overlay - stored for shader access
		projectionMatrix.identity().ortho2D(0, (float) projectionWidth, 0, (float) realHeight);
		projectionMatrix.get(matrixBuffer);

		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		overlayStateReady = true;
	}

	private void cleanup() {
		SessionManager.clear();
		audioManager.clearDonorAudio();
		crossGameFeatureProvider.resetState();
		RenderContext.reset();
		if (masterTitleScreen != null) {
			masterTitleScreen.cleanup();
			masterTitleScreen = null;
		}
		traceHudTextRenderer.cleanup();
		graphicsManager.cleanup();
		gameLoop.closePresence();
		audioManager.destroy();

		// Free the window callbacks and destroy the window
		glfwFreeCallbacks(window);
		glfwDestroyWindow(window);

		// Terminate GLFW and free the error callback
		glfwTerminate();
		GLFWErrorCallback callback = glfwSetErrorCallback(null);
		if (callback != null) {
			callback.free();
		}
	}

	public static void nextDebugState() {
		debugState = debugState.next();
		debugOption = DebugOption.A;
	}

	public static void nextDebugOption() {
		debugOption = debugOption.next();
	}

	public static void main(String[] args) {
		if (isNativeImage() && System.getProperty("org.lwjgl.librarypath") == null) {
			// In a native image, LWJGL can't extract native libs from JARs.
			// Find the bundled .dylib/.so/.dll files next to the executable.
			String libPath = findNativeLibsDir();
			if (libPath != null) {
				System.setProperty("org.lwjgl.librarypath", libPath);
			}
		}
		EngineContext services = EngineContext.fromLegacySingletonsForBootstrap();
		services.configuration().ensureConfigFileExists();
		new Engine(services).run();
	}

	private static String findNativeLibsDir() {
		// Strategy 1: SONIC_NATIVE_LIBS_DIR env var set by .app launcher script.
		// This is NOT stripped by macOS SIP (unlike DYLD_LIBRARY_PATH).
		String envDir = System.getenv("SONIC_NATIVE_LIBS_DIR");
		if (envDir != null && !envDir.isEmpty()) {
			java.io.File dir = new java.io.File(envDir);
			if (hasNativeLibs(dir)) {
				return dir.getAbsolutePath();
			}
		}

		// Strategy 2: directory containing the executable (works for bare binary
		// and .app bundles where dylibs are co-located with the binary)
		try {
			String cmd = ProcessHandle.current().info().command().orElse("");
			if (!cmd.isEmpty()) {
				java.io.File execDir = new java.io.File(cmd).getParentFile();
				if (execDir != null && hasNativeLibs(execDir)) {
					return execDir.getAbsolutePath();
				}
			}
		} catch (Exception ignored) {
		}

		// Strategy 3: working directory (if user runs from target/)
		java.io.File cwd = new java.io.File(System.getProperty("user.dir"));
		if (hasNativeLibs(cwd)) {
			return cwd.getAbsolutePath();
		}

		// Strategy 4: target/native-libs/ relative to working directory (dev builds)
		java.io.File targetNativeLibs = new java.io.File(cwd, "target/native-libs");
		if (hasNativeLibs(targetNativeLibs)) {
			return targetNativeLibs.getAbsolutePath();
		}

		return null;
	}

	private static boolean hasNativeLibs(java.io.File dir) {
		if (!dir.isDirectory()) return false;
		String[] files = dir.list();
		if (files == null) return false;
		for (String f : files) {
			if (f.startsWith("liblwjgl.")) return true;   // .dylib or .so
			if (f.equals("lwjgl.dll")) return true;
		}
		return false;
	}

	// For testing - get window handle
	long getWindowHandle() {
		return window;
	}

	/**
	 * Gets the singleton instance of the Engine.
	 * @return the Engine instance, or null if not yet created
	 */
	public static synchronized Engine getInstance() {
		return instance;
	}

	/**
	 * Gets the current projection matrix for use in shaders.
	 * @return the projection matrix
	 */
	public org.joml.Matrix4f getProjectionMatrix() {
		return projectionMatrix;
	}

	/**
	 * Gets the projection matrix data as a float array for shader uniforms.
	 * @return the projection matrix as a 16-element float array
	 */
	public float[] getProjectionMatrixBuffer() {
		return fboProjectionActive ? fboMatrixBuffer : matrixBuffer;
	}

	// FBO projection support - used when rendering to off-screen framebuffers
	private boolean fboProjectionActive = false;
	private final org.joml.Matrix4f fboProjectionMatrix = new org.joml.Matrix4f();
	private final float[] fboMatrixBuffer = new float[16];
	private int fboWidth = 256;
	private int fboHeight = 256;

	/**
	 * Sets up FBO projection mode for rendering to an off-screen framebuffer.
	 * While active, getProjectionMatrixBuffer() returns the FBO projection.
	 *
	 * @param width  The FBO width in pixels
	 * @param height The FBO height in pixels
	 */
	public void beginFBOProjection(int width, int height) {
		this.fboWidth = width;
		this.fboHeight = height;
		fboProjectionMatrix.identity().ortho2D(0, width, 0, height);
		fboProjectionMatrix.get(fboMatrixBuffer);
		fboProjectionActive = true;
	}

	/**
	 * Restores normal screen projection after FBO rendering.
	 */
	public void endFBOProjection() {
		fboProjectionActive = false;
	}

	/**
	 * Returns the current display height for coordinate calculations.
	 * When FBO projection is active, returns the FBO height.
	 * Otherwise returns the normal screen height.
	 */
	public int getCurrentDisplayHeight() {
		return fboProjectionActive ? fboHeight : (int) realHeight;
	}

	/**
	 * Returns whether FBO projection mode is currently active.
	 */
	public boolean isFBOProjectionActive() {
		return fboProjectionActive;
	}

	/**
	 * Parses a comma-separated sidekick configuration string into a list of
	 * character names. Returns an empty list for null, empty, or blank input.
	 */
	public static List<String> parseSidekickConfig(String value) {
		return ActiveGameplayTeamResolver.parseConfiguredSidekicks(value);
	}
}
