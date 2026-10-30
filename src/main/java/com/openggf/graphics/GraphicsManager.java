package com.openggf.graphics;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.graphics.shaderlib.DisplayShaderPipeline;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.BackgroundRenderer;
import com.openggf.level.render.SpritePieceRenderer;

import static com.openggf.level.LevelConstants.*;


import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;

import java.util.Queue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@com.openggf.game.ModApi
public class GraphicsManager {
    private final PlayerHeadFragmentBanks playerHeadFragmentBanks = new PlayerHeadFragmentBanks();
    private final ObjectHeadFragmentBanks objectHeadFragmentBanks = new ObjectHeadFragmentBanks();
    /** Companion art namespace follows the original virtual player bank, independently of pose or game. */
    public int playerHeadFragmentBase(int sourceBank) { return playerHeadFragmentBanks.baseFor(sourceBank); }
    public int objectHeadFragmentBase() { return objectHeadFragmentBanks.allocate(); }

	private static final Logger LOGGER = Logger.getLogger(GraphicsManager.class.getName());

	private static GraphicsManager graphicsManager;
	List<GLCommandable> commands = new ArrayList<>();
	private List<GLCommandable> commandCaptureTarget;
	// Pool of reusable capture lists for executeCapturedCommands() (stack so
	// nested captures don't share a list). Lists are cleared before pooling.
	private final java.util.ArrayDeque<List<GLCommandable>> captureListPool = new java.util.ArrayDeque<>(2);
	private final Queue<PendingRenderThreadTask<?>> pendingRenderThreadTasks = new ConcurrentLinkedQueue<>();

    private final PaletteTextureOwner paletteTextures = new PaletteTextureOwner(this);
    private PatternAtlas patternAtlas;
    private com.openggf.debug.PerformanceProfiler profiler;

	private static final int ATLAS_WIDTH = 1024;
	private static final int ATLAS_HEIGHT = 1024;

	// Lazily fetched to avoid initialization chain issues in headless tests
	private Camera camera;
	private Camera bootstrapCamera;
	private boolean glInitialized = false;
	private ShaderProgram shaderProgram;
	private ShaderProgram defaultShaderProgram;
	private WaterShaderProgram waterShaderProgram;
	private ShaderProgram currentShaderProgram;

	private ShaderProgram debugShaderProgram;
	private ShaderProgram fadeShaderProgram;
	private ShaderProgram shadowShaderProgram;
    private SpritePriorityShaderProgram nativeShadowShaderProgram;
	private static final String DEBUG_SHADER_PATH = "shaders/shader_debug_color.glsl";
	private static final String PARALLAX_SHADER_PATH = "shaders/shader_parallax_bg.glsl";
	private static final String FADE_SHADER_PATH = "shaders/shader_fade.glsl";
	private static final String SHADOW_SHADER_PATH = "shaders/shader_shadow.glsl";
    private static final String NATIVE_SHADOW_SHADER_PATH = "shaders/shader_native_shadow15.glsl";
	private static final String WATER_SHADER_PATH = "shaders/shader_water.glsl";
	private static final String TILEMAP_SHADER_PATH = "shaders/shader_tilemap.glsl";
	private static final String BASIC_VERTEX_SHADER_PATH = "shaders/shader_basic.vert";
	private static final String DEBUG_VERTEX_SHADER_PATH = "shaders/shader_debug_color.vert";
	private static final String INSTANCED_VERTEX_SHADER_PATH = "shaders/shader_instanced.vert";
	private static final String SPRITE_PRIORITY_SHADER_PATH = "shaders/shader_sprite_priority.glsl";

	// Sprite priority rendering for ROM-accurate sprite-to-tile layering
	private SpritePriorityShaderProgram spritePriorityShaderProgram;
	private TilePriorityFBO tilePriorityFBO;

	// Sprite priority shader mode flags
	private boolean useSpritePriorityShader = false;
	private boolean currentSpriteHighPriority = false;
	private int currentSpriteTileOcclusionPaletteMask = 0xF;
	private boolean ghostRenderEffectActive = false;
	private boolean ghostRenderPreservesColour = false;
	private float ghostRenderAlpha = 1.0f;
	private boolean spriteSatCollectionActive = false;
	private boolean spriteMaskRequested = false;
	private SpriteSatMaskPostProcessor.LogicalViewport spriteSatViewport;
    private java.util.function.BooleanSupplier spriteScanlineLimitSource = () -> true;
	private List<SpriteSatEntry> spriteSatFragments;
    private boolean submittingNativeSpriteOwnership;
    SpriteSatEntry.PixelOperation currentSpritePixelOperation = SpriteSatEntry.PixelOperation.NORMAL;
    Shadow15Backdrop currentShadow15Backdrop;
    private NativeSpriteOwnership nativeSpriteOwnership;
	private final List<SpriteSatEntry> spriteSatEntries = new ArrayList<>();
	// Reusable buffers/scratch for endSpriteSatCollectionAndReplay() — avoids per-frame
	// ArrayList and PatternDesc allocations on the SAT replay hot path.
	private final ArrayList<PatternRenderCommand> reusableReplayCommands = new ArrayList<>();
	private final PatternDesc reusableReplayDesc = new PatternDesc();
	// True while replaySpriteSatEntriesBatched has an instanced batch open.
	private boolean satReplayBatchOpen = false;
	private String currentSpriteSatDebugSource = null;
	private int currentSpriteSatBucket = RenderPriority.MIN;
	// Background renderer for per-scanline parallax scrolling
	private BackgroundRenderer backgroundRenderer;
	private TilemapGpuRenderer tilemapGpuRenderer;
	private InstancedPatternRenderer instancedPatternRenderer;

	// Fade manager for screen transitions
	private FadeManager fadeManager;
	private FadeManager bootstrapFadeManager;

	// Unified UI render pipeline for overlay + fade ordering
	private UiRenderPipeline uiRenderPipeline;
	private DisplayShaderPipeline displayShaderPipeline;

	// Batched rendering support
	private boolean batchingEnabled = true;
	private BatchedPatternRenderer batchedRenderer;
	private boolean instancedBatchingEnabled = true;
	private boolean instancedBatchActive = false;

	// Vertical wrap Y adjustment for sprite/object rendering.
	// When enabled, adjusts world Y coordinates passed to renderPattern/renderPatternWithId
	// to account for vertical level wrapping (LZ3/SBZ2). Emulates VDP modular sprite Y
	// which naturally wraps coordinates, preventing objects from vanishing at wrap boundaries.
	private boolean verticalWrapAdjustEnabled = false;
	private int verticalWrapRange = 0;
	private int verticalWrapCameraY = 0;

	/**
	 * The current render projection, supplied by the rendering host.
	 */
	private RenderProjection projectionSource;

	public void setPerformanceProfiler(com.openggf.debug.PerformanceProfiler profiler) {
		this.profiler = profiler;
	}

	/**
	 * Projection matrix buffer for shader-based rendering.
	 * Can be set directly by tests or other code that doesn't have an Engine instance.
	 */
	private float[] projectionMatrixBuffer;

	/** Reusable JOML matrix for safe-area projection computation (avoids per-call allocation). */
	private final org.joml.Matrix4f safeAreaMatrix = new org.joml.Matrix4f();
	/** Reusable float buffer to receive the safe-area matrix for shader upload. */
	private final float[] safeAreaBuffer = new float[16];

	/**
	 * Headless mode flag. When true, GL operations are skipped.
	 * This enables testing game logic without requiring an OpenGL context.
	 */
    private final ArenaMaskRenderer arenaMaskRenderer = new ArenaMaskRenderer();

    /** Queue a frozen presentation sample so later simulation cannot alter this frame. */
    void drawArenaMask(ArenaMaskState state) {
        if (headlessMode || !state.visible(getProjectionWidth())) return;
        flushPatternBatch();
        registerCommand((cameraX, cameraY, width, height) -> {
            arenaMaskRenderer.draw(width, height, state);
        });
    }

	private boolean headlessMode = false;
	SpritePresentation.Builder spritePresentationBuilder;
	SpritePresentation.Subject spritePresentationSubject = SpritePresentation.Subject.WORLD;

	/**
	 * When true, the batch renderer will use the underwater palette texture
	 * instead of the normal palette texture. Used for background rendering
	 * when Sonic is underwater (original game behavior).
	 */
	private boolean useUnderwaterPaletteForBackground = false;

	// Cached viewport dimensions to avoid glGetIntegerv(GL_VIEWPORT) every batch.
	// Updated when Engine.reshape() is called.
	private int viewportX = 0;
	private int viewportY = 0;
	private int viewportWidth = 320;
	private int viewportHeight = 224;

	// Cached SCREEN_HEIGHT_PIXELS config value used by PatternRenderCommand's
	// per-obtain() display-height resolution. Invalidated on reshape (setViewport)
	// and resetState so config changes are picked up; <= 0 means "not cached".
	private int cachedConfigScreenHeightPx = -1;

	// Projection-space width: the coordinate-space width of the full viewport.
	private int projectionWidth = 320;

	// Water-related state for sprite priority shader underwater palette support.
	// These values are set by LevelManager.updateWaterShaderState() each frame.
	private float waterlineScreenY = 0.0f;
	private float windowHeight = 224.0f;
	private float screenHeight = 224.0f;
	private boolean waterEnabled = false;

	public void registerCommand(GLCommandable command) {
		if (spritePresentationBuilder != null) {
			try {
				SpritePresentation.Geometry geometry;
				if (command instanceof GLCommand primitive) {
					geometry = primitive.preparePrimitive(spritePresentationBuilder.cameraX, spritePresentationBuilder.cameraY);
				} else if (command instanceof GLCommandGroup group) {
					geometry = group.prepareGroup(spritePresentationBuilder.cameraX, spritePresentationBuilder.cameraY);
				} else {
					throw new IllegalStateException("GPU command submitted during CPU sprite preparation: "
							+ command.getClass().getName());
				}
				spritePresentationBuilder.primitives.add(new SpritePresentation.Primitive(
						spritePresentationBuilder.tiles.size(), spritePresentationBuilder.layer, geometry, spritePresentationSubject));
			} finally { command.discard(); }
			return;
		}
		if (commandCaptureTarget != null) {
			commandCaptureTarget.add(command);
			return;
		}
		commands.add(command);
	}

	public void executeCapturedCommands(Runnable producer, int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
		List<GLCommandable> previousCaptureTarget = commandCaptureTarget;
		// Reuse capture lists via a small pool (a stack, so nested captures each
		// get their own list); returned to the pool cleared in the finally block.
		List<GLCommandable> capturedCommands = captureListPool.pollLast();
		if (capturedCommands == null) {
			capturedCommands = new ArrayList<>();
		}
		commandCaptureTarget = capturedCommands;
		int nextUnexecuted = 0;
		boolean frameStateActive = false;
		try {
			producer.run();
			if (headlessMode || capturedCommands.isEmpty() || !glInitialized) {
				return;
			}
			// A native batch can execute before the first scoped pattern command.
			// Rebuild expanded rows from published CRAM inputs before either path samples them.
			paletteTextures.refreshScopedUnderwaterResidency();
			PatternRenderCommand.resetFrameState();
			frameStateActive = true;
			for (int i = 0, n = capturedCommands.size(); i < n; i++) {
				capturedCommands.get(i).execute(cameraX, cameraY, cameraWidth, cameraHeight);
				nextUnexecuted = i + 1;
			}
		} catch (RuntimeException | Error failure) {
			unwindCommands(capturedCommands, nextUnexecuted,
					cameraX, cameraY, cameraWidth, cameraHeight, failure);
			nextUnexecuted = capturedCommands.size();
			throw failure;
		} finally {
			if (frameStateActive) {
				cleanupPatternFrameState();
			}
			discardCommands(capturedCommands, nextUnexecuted);
			commandCaptureTarget = previousCaptureTarget;
			capturedCommands.clear();
			captureListPool.addLast(capturedCommands);
		}
	}

	void cleanupPatternFrameState() {
		PatternRenderCommand.cleanupFrameState(this);
	}

	public <T> CompletableFuture<T> submitRenderThreadTask(Callable<T> callable) {
		CompletableFuture<T> future = new CompletableFuture<>();
		pendingRenderThreadTasks.add(new PendingRenderThreadTask<>(callable, future));
		return future;
	}

	public void runPendingRenderThreadTasks() {
		PendingRenderThreadTask<?> task;
		while ((task = pendingRenderThreadTasks.poll()) != null) {
			task.run();
		}
	}

	private void clearPendingRenderThreadTasks() {
		PendingRenderThreadTask<?> task;
		while ((task = pendingRenderThreadTasks.poll()) != null) {
			task.cancel();
		}
	}

	public void renderPatternWithIdScaled(int patternId, PatternDesc desc, float x, float y, float width, float height) {
		if (spritePresentationBuilder != null) {
			spritePresentationBuilder.add(this, patternId, desc, x, y, width, height);
			return;
		}
		if (headlessMode) {
			return;
		}

		ensurePatternAtlas();
		PatternAtlas.Entry entry = patternAtlas != null ? patternAtlas.getEntry(patternId) : null;

		Integer paletteTextureId = resolveEffectivePatternPaletteTextureId();

		if (entry == null || paletteTextureId == null) {
			return;
		}
		boolean restartInstanced = instancedBatchActive && instancedPatternRenderer != null;
		boolean restartBatched = !restartInstanced && batchedRenderer != null && batchedRenderer.isBatchActive();
		if (restartInstanced || restartBatched) {
			flushPatternBatch();
		}

		PatternRenderCommand command = obtainPatternCommand(entry, paletteTextureId, desc, x, y, width, height);
		registerCommand(command);
		restartPatternBatch(restartInstanced, restartBatched, 0);
	}

	private PatternRenderCommand obtainPatternCommand(PatternAtlas.Entry entry, int paletteTextureId,
			PatternDesc desc, float x, float y, float width, float height) {
		PatternRenderCommand command = PatternRenderCommand.obtain(entry, paletteTextureId, this);
		// Preserve the acquired prefix, then palette/replay/underwater sampling before later attributes.
		command.capturePalette(desc.getPaletteIndex());
		command.finish(desc.getHFlip(), desc.getVFlip(), desc.getPriority(), x, y, width, height);
		return command;
	}

	/** Internal post-SAT picture bridge: source row crop/flips stay native; only the final quad/colour changes. */
	void renderPresentationPattern(int patternId, int paletteIndex, boolean hFlip, boolean vFlip, boolean priority,
	                               int rowStart, int rowEnd, float[] quad, float hue, float opacity) {
		if(quad.length!=8||!Float.isFinite(hue)||!Float.isFinite(opacity))throw new IllegalArgumentException("Invalid presentation geometry or colour");
		for(float value:quad)if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite picture vertex");
		if(headlessMode) return;
		ensurePatternAtlas();
		var entry=patternAtlas==null?null:patternAtlas.getEntry(patternId);
		Integer palette=resolveEffectivePatternPaletteTextureId();
		if(entry==null||palette==null||rowStart>=rowEnd) return;
		flushPatternBatch();
		var command=PatternRenderCommand.obtainRows(entry,palette,paletteIndex,hFlip,vFlip,priority,0,0,rowStart,rowEnd,this);
		command.presentation(quad,hue,opacity);
        // The queue is the painter-order boundary, including captured commands.
        // Never merge across a primitive, normal instance batch, or state command.
        List<GLCommandable> target = commandCaptureTarget != null ? commandCaptureTarget : commands;
        if (!batchingEnabled || target.isEmpty()
                || !(target.getLast() instanceof PatternRenderCommand previous)
                || !previous.appendPresentation(command)) registerCommand(command);
	}

	private final ObjectVisualPatternBanks objectVisualPatterns = new ObjectVisualPatternBanks();

	ObjectVisualPatternBanks.Bank objectVisualPatternBank(Object owner, int count) {
		return objectVisualPatterns.acquire(
				owner, count, presentationTrailBanks.values(), PRESENTATION_TRAIL_BANK_SIZE);
	}

	boolean objectVisualPatternBankValid(Object owner, ObjectVisualPatternBanks.Bank bank) {
		return objectVisualPatterns.valid(owner, bank);
	}

	void releaseObjectVisualPatternBank(Object owner, ObjectVisualPatternBanks.Bank bank) {
		objectVisualPatterns.release(owner, bank);
	}

	private final java.util.IdentityHashMap<Object,Integer> presentationTrailBanks=new java.util.IdentityHashMap<>();
	private final java.util.IdentityHashMap<Object,Long> presentationTrailGenerations=new java.util.IdentityHashMap<>();
	private long nextPresentationTrailGeneration;
	private static final int PRESENTATION_TRAIL_BANK_SIZE = 24 * 256;
	/** One bank per live owner; 24 poses, each with at most 256 distinct frozen tiles. */
	int presentationTrailBank(Object owner) {
		java.util.Objects.requireNonNull(owner);
		var existing=presentationTrailBanks.get(owner);if(existing!=null)return existing;
		var range=PatternAtlasRange.PRESENTATION_TRAILS;
		for(int base=range.base();base+PRESENTATION_TRAIL_BANK_SIZE<=range.endExclusive();base+=PRESENTATION_TRAIL_BANK_SIZE) {
			// Owners use identity, but reserved numeric addresses must use value equality.
			boolean occupied=false;
			for(int assigned:presentationTrailBanks.values())if(assigned==base){occupied=true;break;}
			if (objectVisualPatterns.overlaps(base, PRESENTATION_TRAIL_BANK_SIZE)) occupied = true;
			if(!occupied) {
				presentationTrailBanks.put(owner,base);presentationTrailGenerations.put(owner,++nextPresentationTrailGeneration);return base;
			}
		}
		throw new IllegalStateException("Presentation trail owner budget exhausted");
	}
	void releasePresentationTrailBank(Object owner) {
		Integer base=presentationTrailBanks.remove(owner);if(base==null)return;
		presentationTrailGenerations.remove(owner);
		for(int id=base;id<base+PRESENTATION_TRAIL_BANK_SIZE;id++) {
			uncachePattern(id);
			if(patternAtlas!=null)patternAtlas.releaseScenePatternSample(id);
		}
	}
	long presentationTrailGeneration(Object owner) { return presentationTrailGenerations.getOrDefault(owner,0L); }
	boolean presentationTrailBankValid(Object owner,int base,long generation) {
		return generation!=0&&java.util.Objects.equals(presentationTrailBanks.get(owner),base)
				&&presentationTrailGeneration(owner)==generation;
	}

	/**
	 * Initialize the GraphicsManager with shader loading.
	 */
	public void init(String pixelShaderPath) throws IOException {
		if (headlessMode) {
			return;
		}
		this.glInitialized = true;
		this.patternAtlas = new PatternAtlas(ATLAS_WIDTH, ATLAS_HEIGHT, profiler);
		this.patternAtlas.init();
		this.defaultShaderProgram = new ShaderProgram(BASIC_VERTEX_SHADER_PATH, pixelShaderPath); // Load default shader
		this.defaultShaderProgram.cacheUniformLocations();

		this.waterShaderProgram = new WaterShaderProgram(BASIC_VERTEX_SHADER_PATH, WATER_SHADER_PATH); // Load water shader
		this.waterShaderProgram.cacheUniformLocations();

		this.currentShaderProgram = this.defaultShaderProgram; // Start with default
		this.shaderProgram = this.currentShaderProgram; // Compatibility
		this.debugShaderProgram = new ShaderProgram(DEBUG_VERTEX_SHADER_PATH, DEBUG_SHADER_PATH);
		this.fadeShaderProgram = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER, FADE_SHADER_PATH);
		this.shadowShaderProgram = new ShaderProgram(BASIC_VERTEX_SHADER_PATH, SHADOW_SHADER_PATH);
		this.shadowShaderProgram.cacheUniformLocations();
        this.nativeShadowShaderProgram = new SpritePriorityShaderProgram(NATIVE_SHADOW_SHADER_PATH);
        this.nativeShadowShaderProgram.cacheUniformLocations();
		SonicConfigurationService cfg = GameServices.configuration();
		this.tilemapGpuRenderer = new TilemapGpuRenderer(cfg.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
		this.tilemapGpuRenderer.init(TILEMAP_SHADER_PATH);
		this.instancedPatternRenderer = new InstancedPatternRenderer(this, cfg);
		this.instancedPatternRenderer.init(INSTANCED_VERTEX_SHADER_PATH, pixelShaderPath, WATER_SHADER_PATH);

		ensureRuntimeManagedReferences();
		this.fadeManager.setFadeShader(this.fadeShaderProgram);

		// Initialize unified UI render pipeline
		this.uiRenderPipeline = new UiRenderPipeline(this);
		this.uiRenderPipeline.setFadeManager(this.fadeManager);
		this.displayShaderPipeline = new DisplayShaderPipeline();

		// Initialize sprite priority rendering system
		this.spritePriorityShaderProgram = new SpritePriorityShaderProgram(SPRITE_PRIORITY_SHADER_PATH);
		this.spritePriorityShaderProgram.cacheUniformLocations();
		this.tilePriorityFBO = new TilePriorityFBO();
		// FBO will be initialized when first needed with actual screen dimensions
	}

	/**
	 * Initialize the GraphicsManager in headless mode (no GL context).
	 * Use this for testing game logic without rendering.
	 */
	public void initHeadless() {
		objectVisualPatterns.clear();
		this.headlessMode = true;
		this.glInitialized = false;
		if (this.patternAtlas == null) {
			this.patternAtlas = new PatternAtlas(ATLAS_WIDTH, ATLAS_HEIGHT, profiler);
		}
		this.tilemapGpuRenderer = null;
		this.instancedPatternRenderer = null;
	}

	/**
	 * Check if running in headless mode.
	 */
	public boolean isHeadlessMode() {
		return headlessMode;
	}

	/**
	 * Set headless mode. Should be called before init().
	 */
	public void setHeadlessMode(boolean headless) {
		this.headlessMode = headless;
	}

	/**
	 * Mark the GL context as initialized.
	 */
	public void setGlInitialized(boolean initialized) {
		this.glInitialized = initialized;
	}

	/**
	 * Lazily get the Camera instance.
	 * This avoids triggering Camera singleton initialization during GraphicsManager construction.
	 */
	private Camera getCamera() {
		ensureRuntimeManagedReferences();
		return camera;
	}

	public void bindRuntimeManagedReferences(Camera runtimeCamera, FadeManager runtimeFadeManager) {
		camera = Objects.requireNonNull(runtimeCamera, "runtimeCamera");
		setActiveFadeManager(Objects.requireNonNull(runtimeFadeManager, "runtimeFadeManager"));
	}

	public void clearRuntimeManagedReferences() {
		if (camera != null || bootstrapCamera != null) {
			camera = getOrCreateBootstrapCamera();
		}
		if (uiRenderPipeline != null) {
			uiRenderPipeline.setHudRenderManager(null);
		}
		if (fadeManager != null || bootstrapFadeManager != null || uiRenderPipeline != null) {
			setActiveFadeManager(getOrCreateBootstrapFadeManager());
		}
	}

	private void ensureRuntimeManagedReferences() {
		if (camera == null) {
			camera = getOrCreateBootstrapCamera();
		}
		if (fadeManager == null) {
			setActiveFadeManager(getOrCreateBootstrapFadeManager());
		}
	}

	private void setActiveFadeManager(FadeManager resolvedFadeManager) {
		if (fadeManager != resolvedFadeManager) {
            if (fadeManager != null) fadeManager.bindPaletteGraphics(null);
			fadeManager = resolvedFadeManager;
            fadeManager.bindPaletteGraphics(this);
			if (fadeShaderProgram != null) {
				fadeManager.setFadeShader(fadeShaderProgram);
			}
			if (uiRenderPipeline != null) {
				uiRenderPipeline.setFadeManager(fadeManager);
			}
		}
	}

	private Camera getOrCreateBootstrapCamera() {
		if (bootstrapCamera == null) {
			bootstrapCamera = new Camera(GameServices.configuration());
		}
		return bootstrapCamera;
	}

	private FadeManager getOrCreateBootstrapFadeManager() {
		if (bootstrapFadeManager == null) {
			bootstrapFadeManager = new FadeManager();
		}
		return bootstrapFadeManager;
	}


    final PaletteTextureOwner paletteTextures() { return paletteTextures; }
    final boolean paletteHeadlessMode() { return headlessMode; }
    final boolean paletteGlInitialized() { return glInitialized; }
    final void clearUnderwaterPaletteSelection() { useUnderwaterPaletteForBackground = false; }

    /** Preserve the public virtual upload boundary on pending-water publication. */
    final void replayPendingUnderwaterUpload() {
        cacheUnderwaterPaletteTexture((Palette[]) paletteTextures.pendingUnderwaterPalettes(),
                (Palette) paletteTextures.pendingUnderwaterLine0());
    }

    public void setDisplayColorProfile(DisplayColorProfile displayColorProfile) { paletteTextures.setDisplayColorProfile(displayColorProfile); }
    public DisplayColorProfile getDisplayColorProfile() { return paletteTextures.getDisplayColorProfile(); }
    /** Changes uploaded RGB, preserving native palette bytes and pending publication. */
    public void setDisplayColorExpansion(DisplayColorExpansion expansion) { paletteTextures.setDisplayColorExpansion(expansion); }
    public DisplayColorExpansion getDisplayColorExpansion() { return paletteTextures.getDisplayColorExpansion(); }

	/**
	 * Flush all registered commands.
	 * Uses shake-adjusted camera positions so sprites shake in sync with FG tiles.
	 */
	public void flush() {
		if (spritePresentationBuilder != null) return;
		Camera cam = getCamera();
		flushWithCamera(cam.getXWithShake(), cam.getYWithShake(), cam.getWidth(), cam.getHeight());
	}

	/** Discards queued draw commands after an aborted frame without executing them. */
	public void discardQueuedCommands() {
		discardCommands(commands, 0);
		commands.clear();
        if (!spriteSatCollectionActive && spriteSatEntries.isEmpty()) objectHeadFragmentBanks.reset();
	}

	/**
	 * Flush all registered commands with a specific camera position.
	 * Use this for screen-space rendering by passing (0, 0) for camera position.
	 */
	public void flushWithCamera(short cameraX, short cameraY, short cameraWidth, short cameraHeight) {
        // Never recycle companions still referenced by native SAT entries awaiting admission.
        boolean retireHeadBanks=!spriteSatCollectionActive && spriteSatEntries.isEmpty();
		if (headlessMode || commands.isEmpty() || !glInitialized) {
			discardCommands(commands, 0);
			commands.clear();
            if(retireHeadBanks) objectHeadFragmentBanks.reset();
			return;
		}

		// Reset pattern render state for new batch of commands
		paletteTextures.refreshScopedUnderwaterResidency();
		PatternRenderCommand.resetFrameState();
		int nextUnexecuted = 0;
		try {
			for (int i = 0, n = commands.size(); i < n; i++) {
				commands.get(i).execute(cameraX, cameraY, cameraWidth, cameraHeight);
				nextUnexecuted = i + 1;
			}
		} catch (RuntimeException | Error failure) {
			unwindCommands(commands, nextUnexecuted,
					cameraX, cameraY, cameraWidth, cameraHeight, failure);
			nextUnexecuted = commands.size();
			throw failure;
		} finally {
			// Cleanup pattern render state even if a custom command throws.
			PatternRenderCommand.cleanupFrameState(this);
			discardCommands(commands, nextUnexecuted);
			commands.clear();
            if(retireHeadBanks) objectHeadFragmentBanks.reset();
		}
	}

	private static void discardCommands(List<? extends GLCommandable> queued, int fromIndex) {
		for (int i = Math.max(0, fromIndex), n = queued.size(); i < n; i++) {
			queued.get(i).discard();
		}
	}

	private static void unwindCommands(List<? extends GLCommandable> queued, int fromIndex,
			int cameraX, int cameraY, int cameraWidth, int cameraHeight, Throwable failure) {
		for (int i = Math.max(0, fromIndex), n = queued.size(); i < n; i++) {
			try {
				queued.get(i).unwindAfterFailure(cameraX, cameraY, cameraWidth, cameraHeight);
			} catch (RuntimeException | Error cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
		}
	}

	/**
	 * Flush all registered commands in screen-space (camera at 0,0).
	 * Used for overlays like title cards and results screens.
	 */
	public void flushScreenSpace() {
		Camera cam = getCamera();
		flushWithCamera((short) 0, (short) 0, cam.getWidth(), cam.getHeight());
	}

	/**
	 * Reset OpenGL state for shader-based rendering.
	 * Call this between different rendering phases to ensure clean state.
	 * Note: Fixed-function calls (glDisable(GL_TEXTURE_2D), glMatrixMode, etc.)
	 * have been removed for OpenGL 4.1 core profile compatibility.
	 */
	public void resetForFixedFunction() {
		if (headlessMode || !glInitialized) {
			return;
		}
		// Ensure no shader is active
		glUseProgram(0);
		// Reset texture state
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE0);
	}

	/**
	 * Cache a pattern texture (contains color indices) in the GPU.
	 */
	public void cachePatternTexture(Pattern pattern, int patternId) {
		ensurePatternAtlas();
		if (headlessMode || !glInitialized) {
			patternAtlas.cachePatternHeadless(pattern, patternId);
			return;
		}
		patternAtlas.cachePattern(pattern, patternId);
	}

    void restoreFrozenPattern(SpritePresentation.PatternVersion version, int patternId) {
        ensurePatternAtlas();
        patternAtlas.restoreFrozenPattern(version, patternId, headlessMode || !glInitialized);
    }

    boolean presentationMaskIsPerInstance() {
        return !headlessMode && spritePresentationBuilder == null && batchingEnabled
                && instancedBatchActive && instancedPatternRenderer != null;
    }

	public void updatePatternTexture(Pattern pattern, int patternId) {
		// Native null uploads allocate residency but leave existing GPU bytes intact.
		ensurePatternAtlas();
		if (headlessMode || !glInitialized) {
			patternAtlas.updatePatternHeadless(pattern, patternId);
			return;
		}
		patternAtlas.updatePattern(pattern, patternId);
	}

	/**
	 * Re-uploads only the patterns whose indices are set in the dirty BitSet.
	 * Used by MutableLevel dirty-region processing to incrementally update
	 * the GPU pattern atlas after editor mutations.
	 *
	 * @param dirtyIndices BitSet of pattern indices that changed
	 * @param level the current level (provides pattern data)
	 */
	public void reuploadDirtyPatterns(java.util.BitSet dirtyIndices,
									  com.openggf.level.Level level) {
		if (headlessMode || !glInitialized) return;
		for (int i = dirtyIndices.nextSetBit(0); i >= 0;
			 i = dirtyIndices.nextSetBit(i + 1)) {
			if (i < level.getPatternCount()) {
				updatePatternTexture(level.getPattern(i), i);
			}
		}
	}

	/**
	 * Begin batching pattern atlas uploads. While active, individual
	 * {@code cachePatternTexture} calls write to a CPU-side buffer only.
	 * Call {@link #endPatternAtlasBatch()} to upload everything in one GL call.
	 */
	public void beginPatternAtlasBatch() {
		if (headlessMode || !glInitialized || patternAtlas == null) {
			return;
		}
		patternAtlas.beginBatch();
	}

	/**
	 * Flush the batched pattern atlas uploads to the GPU.
	 */
	public void endPatternAtlasBatch() {
		if (headlessMode || !glInitialized || patternAtlas == null) {
			return;
		}
		patternAtlas.endBatch();
	}

	/**
	 * Remove a pattern from the atlas cache.
	 * This causes the renderer to skip this pattern (getEntry returns null).
	 * Used by CNZ slot machine to clear the tilemap at VRAM 0x0550-0x057F
	 * so the shader overlay can render slot faces there.
	 *
	 * @param patternId The pattern ID to uncache
	 * @return true if the pattern was uncached, false if it wasn't in the cache
	 */
	public boolean uncachePattern(int patternId) {
		if (patternAtlas == null) {
			return false;
		}
		return patternAtlas.removeEntry(patternId);
	}

	/**
	 * Create an alias so that one pattern ID renders the same as another.
	 * This is useful for making multiple pattern IDs render as transparent
	 * by aliasing them to pattern 0 (which is typically all-transparent).
	 * No additional atlas slots are allocated.
	 *
	 * @param aliasId The pattern ID to create as an alias
	 * @param targetId The existing pattern ID to alias to
	 * @return true if the alias was created, false if target doesn't exist
	 */
	public boolean aliasPattern(int aliasId, int targetId) {
		if (patternAtlas == null) {
			return false;
		}
		return patternAtlas.aliasEntry(aliasId, targetId);
	}

    public void cachePaletteTexture(Palette palette, int paletteId) {
        paletteTextures.cacheImmediatePaletteTexture(palette, paletteId);
    }

    /** The palette fade currently applied to CRAM uploads; see {@link PaletteFadePresentation}. */
    public PaletteFadePresentation getPaletteFadePresentation() { return paletteTextures.getPaletteFadePresentation(); }
    /**
     * Applies a Mega Drive palette fade step to every later upload of the masked
     * lines, and re-uploads cached lines so a fade progresses without asking their
     * owners to write again. {@code steps} counts native component passes, not
     * display frames; the requested generation follows the publication gate.
     */
    public void setPaletteFadePresentation(PaletteFadePresentation.Mode mode, int steps, int lineMask) {
        paletteTextures.setPaletteFadePresentation(mode, steps, lineMask);
    }
    /** Ends the fade and re-uploads its affected lines from their owning palettes. */
    public void clearPaletteFadePresentation() { paletteTextures.clearPaletteFadePresentation(); }

	/**
	 * Render a pre-cached pattern at the given coordinates using the specified
	 * palette.
	 */
	public void renderPattern(PatternDesc desc, int x, int y) {
		renderPatternWithId(desc.getPatternIndex(), desc, x, y);
	}

	/**
	 * Render a pattern using an explicit pattern ID for texture lookup.
	 * This allows using pattern IDs beyond the 11-bit limit of PatternDesc.
	 */
	public void renderPatternWithId(int patternId, PatternDesc desc, int x, int y) {

		// Vertical wrap Y adjustment (emulates VDP modular sprite Y coordinates).
		// When enabled, wraps the world Y to the nearest equivalent position within
		// VERTICAL_WRAP_RANGE of the camera, so objects on the "wrong side" of a
		// wrap boundary render at the correct screen position.
		y = adjustVerticalWrapY(y);

		if (spritePresentationBuilder != null) {
			spritePresentationBuilder.add(this, patternId, desc, x, y, 8, 8);
			return;
		}
		if (headlessMode) return;
        if(spritePresentationSubject.paletteReplay()!=null)flushPatternBatch();

		ensurePatternAtlas();
		PatternAtlas.Entry entry = patternAtlas != null ? patternAtlas.getEntry(patternId) : null;

		Integer paletteTextureId = resolveEffectivePatternPaletteTextureId();

		if (entry == null) {
			return;
		}
		if (paletteTextureId == null) {
			return;
		}
		// Try batched rendering for better performance
		// Only use batching if enabled, batch is active, and pattern was successfully added
		boolean usedBatch = false;
		boolean restartBatchedAfterFallback = false;
		int paletteIndex = desc.getPaletteIndex();
		if (batchingEnabled && spritePresentationSubject.paletteReplay()==null && instancedBatchActive && instancedPatternRenderer != null) {
			usedBatch = tryAddInstancedPattern(entry, paletteIndex, desc, x, y);
			if (!usedBatch) {
				flushPatternBatch();
				restartPatternBatch(true, false, entry.atlasIndex());
				usedBatch = tryAddInstancedPattern(entry, desc.getPaletteIndex(), desc, x, y);
			}
		} else if (batchingEnabled && spritePresentationSubject.paletteReplay()==null && batchedRenderer != null && batchedRenderer.isBatchActive()) {
			if (entry.atlasIndex() == 0) {
				usedBatch = tryAddBatchedPattern(entry, paletteIndex, desc, x, y);
			}
			if (!usedBatch) {
				// A direct fallback must be ordered after already staged page-0 geometry.
				restartBatchedAfterFallback = true;
				flushPatternBatch();
			}
		}

		if (!usedBatch) {
			// Fallback to individual commands (use pooled allocation)
			PatternRenderCommand command = obtainPatternCommand(entry, paletteTextureId, desc, x, y, 8f, 8f);
			registerCommand(command);
			if (restartBatchedAfterFallback) {
				restartPatternBatch(false, true, 0);
			}
		}
	}

	// Keep descriptor reads after admission: virtual manager getters can mutate the
	// descriptor, replace the renderer, reject, or throw. Keep each attempt's receiver;
	// palette arguments are sampled at the call sites.
	private boolean tryAddBatchedPattern(PatternAtlas.Entry entry, int paletteIndex, PatternDesc desc, int x, int y) {
		BatchedPatternRenderer renderer = batchedRenderer;
		if (!renderer.admitPattern(entry)) return false;
		renderer.appendAdmittedPattern(entry, paletteIndex, desc.getHFlip(), desc.getVFlip(), x, y);
		return true;
	}

	private boolean tryAddBatchedStrip(PatternAtlas.Entry entry, int paletteIndex, PatternDesc desc, int x, int y, int stripIndex) {
		BatchedPatternRenderer renderer = batchedRenderer;
		if (!renderer.admitPattern(entry)) return false;
		renderer.appendAdmittedStripPattern(entry, paletteIndex, desc.getHFlip(), desc.getVFlip(), x, y, stripIndex);
		return true;
	}

	private boolean tryAddBatchedShadow(PatternAtlas.Entry entry, PatternDesc desc, int x, int y) {
		BatchedPatternRenderer renderer = batchedRenderer;
		if (!renderer.admitShadowPattern(entry)) return false;
		renderer.appendAdmittedShadowPattern(entry, desc.getHFlip(), desc.getVFlip(), x, y);
		return true;
	}

	private boolean tryAddInstancedPattern(PatternAtlas.Entry entry, int paletteIndex, PatternDesc desc, int x, int y) {
		InstancedPatternRenderer renderer = instancedPatternRenderer;
		if (!renderer.admitPattern(entry)) return false;
		renderer.appendAdmittedPattern(entry, paletteIndex, desc.getHFlip(), desc.getVFlip(), desc.getPriority(), x, y);
		return true;
	}

	private boolean tryAddInstancedStrip(PatternAtlas.Entry entry, int paletteIndex, PatternDesc desc, int x, int y, int stripIndex) {
		InstancedPatternRenderer renderer = instancedPatternRenderer;
		if (!renderer.admitPattern(entry)) return false;
		renderer.appendAdmittedStripPattern(entry, paletteIndex, desc.getHFlip(), desc.getVFlip(), desc.getPriority(), x, y, stripIndex);
		return true;
	}

	private int adjustVerticalWrapY(int y) {
		if (verticalWrapAdjustEnabled && verticalWrapRange > 0) {
			int diff = y - verticalWrapCameraY;
			diff = ((diff % verticalWrapRange) + verticalWrapRange) % verticalWrapRange;
			if (diff > verticalWrapRange / 2) {
				diff -= verticalWrapRange;
			}
			y = verticalWrapCameraY + diff;
		}
		return y;
	}

	/**
	 * Renders pixel rows {@code [rowStart, rowEnd)} (screen order, 0 = tile top) of
	 * an 8x8 pattern whose top is at {@code y}, for a VDP sprite mask that hides
	 * single scanlines. Callers pass whole tiles through {@link #renderPatternWithId}.
	 * {@code descriptor} is only handed to the presentation builder's decoder.
	 */
	void renderPatternRowsWithId(int patternId, Object descriptor, int paletteIndex, boolean hFlip, boolean vFlip,
			boolean priority, int x, int y, int rowStart, int rowEnd) {
		rowStart = Math.max(0, rowStart);
		rowEnd = Math.min(8, rowEnd);
		if (rowStart >= rowEnd) {
			return;
		}
		y = adjustVerticalWrapY(y);
		if (spritePresentationBuilder != null) {
			spritePresentationBuilder.addRows(this, patternId, descriptor, x, y, rowStart, rowEnd);
			return;
		}
		if (headlessMode) return;
        if(spritePresentationSubject.paletteReplay()!=null)flushPatternBatch();

		ensurePatternAtlas();
		PatternAtlas.Entry entry = patternAtlas != null ? patternAtlas.getEntry(patternId) : null;
		Integer paletteTextureId = resolveEffectivePatternPaletteTextureId();
		if (entry == null || paletteTextureId == null) {
			return;
		}
		boolean restartInstancedAfterDirect = false;
		boolean restartBatchedAfterDirect = false;
		if (batchingEnabled && spritePresentationSubject.paletteReplay()==null && instancedBatchActive && instancedPatternRenderer != null) {
			if (instancedPatternRenderer.addPatternRows(entry, paletteIndex, hFlip, vFlip, priority, x, y, rowStart, rowEnd)) {
				return;
			}
			flushPatternBatch();
			restartPatternBatch(true, false, entry.atlasIndex());
			if (instancedPatternRenderer.addPatternRows(entry, paletteIndex, hFlip, vFlip, priority, x, y, rowStart, rowEnd)) {
				return;
			}
			flushPatternBatch();
			restartInstancedAfterDirect = true;
		} else if (batchingEnabled && spritePresentationSubject.paletteReplay()==null && batchedRenderer != null && batchedRenderer.isBatchActive()) {
			// The page-0 batcher only draws whole tiles; keep order by flushing first.
			flushPatternBatch();
			restartBatchedAfterDirect = true;
		}
		registerCommand(PatternRenderCommand.obtainRows(entry, paletteTextureId, paletteIndex, hFlip, vFlip, priority,
				x, y, rowStart, rowEnd, this));
		restartPatternBatch(restartInstancedAfterDirect, restartBatchedAfterDirect, entry.atlasIndex());
	}

	/** Draws following sprites as a desaturated, translucent ghost. */
	public void beginGhostRenderEffect(float alpha) {
		beginGhostRenderEffect(alpha, false);
	}

	/**
	 * Draws following sprites as a translucent ghost; {@code preserveColour} keeps their palette
	 * colours (a player's chosen look) instead of desaturating them.
	 */
	public void beginGhostRenderEffect(float alpha, boolean preserveColour) {
		this.ghostRenderEffectActive = true;
		this.ghostRenderPreservesColour = preserveColour;
		this.ghostRenderAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
	}

	public void endGhostRenderEffect() {
		this.ghostRenderEffectActive = false;
		this.ghostRenderPreservesColour = false;
		this.ghostRenderAlpha = 1.0f;
	}

	public boolean isGhostRenderEffectActive() {
		return ghostRenderEffectActive;
	}

	/** Whether the active ghost effect keeps palette colours (meaningless when none is active). */
	public boolean isGhostRenderColourPreserved() {
		return ghostRenderEffectActive && ghostRenderPreservesColour;
	}

	/** Shader {@code GhostMode}: 0 none, 1 desaturated ghost, 2 colour-preserving ghost. */
	static int ghostShaderMode(boolean active, boolean preserveColour) {
		return !active ? 0 : preserveColour ? 2 : 1;
	}

	public float getGhostRenderAlpha() {
		return ghostRenderAlpha;
	}

	/**
	 * Render a pattern as a 2-scanline strip for special stage track rendering.
	 *
	 * The Sonic 2 special stage uses per-scanline horizontal scroll to create
	 * a pseudo-3D halfpipe effect where each 8x8 tile appears as 4 strips of
	 * 2 scanlines each. This method renders a single strip (8 wide × 2 high).
	 *
	 * @param patternId  The pattern texture ID
	 * @param desc       The pattern descriptor (handles H/V flip and palette)
	 * @param x          Screen X position
	 * @param y          Screen Y position of this strip
	 * @param stripIndex Which strip to render (0-3, where 0 is top of original
	 *                   tile)
	 */
	public void renderStripPatternWithId(int patternId, PatternDesc desc, int x, int y, int stripIndex) {
		if (headlessMode) {
			return;
		}
        if(spritePresentationSubject.paletteReplay()!=null)flushPatternBatch();
		ensurePatternAtlas();
		PatternAtlas.Entry entry = patternAtlas != null ? patternAtlas.getEntry(patternId) : null;
		Integer paletteTextureId = resolveEffectivePatternPaletteTextureId();
		if (entry == null || paletteTextureId == null) {
			return;
		}

		// Only use batched rendering for strip patterns.
		int paletteIndex = desc.getPaletteIndex();
		boolean restartInstancedAfterDirect = false;
		boolean restartBatchedAfterDirect = false;
		if (batchingEnabled && spritePresentationSubject.paletteReplay()==null && instancedBatchActive && instancedPatternRenderer != null) {
			boolean added = tryAddInstancedStrip(entry, paletteIndex, desc, x, y, stripIndex);
			if (!added) {
				flushPatternBatch();
				restartPatternBatch(true, false, entry.atlasIndex());
				added = tryAddInstancedStrip(entry, desc.getPaletteIndex(), desc, x, y, stripIndex);
			}
			if (!added) {
				flushPatternBatch();
				restartInstancedAfterDirect = true;
			} else {
				return;
			}
		} else if (batchingEnabled && spritePresentationSubject.paletteReplay()==null && batchedRenderer != null && batchedRenderer.isBatchActive()) {
			boolean added = entry.atlasIndex() == 0
					&& tryAddBatchedStrip(entry, paletteIndex, desc, x, y, stripIndex);
			if (added) {
				return;
			}
			flushPatternBatch();
			if (entry.atlasIndex() == 0) {
				restartPatternBatch(false, true, 0);
				if (tryAddBatchedStrip(entry, paletteIndex, desc, x, y, stripIndex)) {
					return;
				}
				flushPatternBatch();
			}
			restartBatchedAfterDirect = true;
		}
		PatternRenderCommand command = obtainPatternCommand(entry, paletteTextureId, desc, x, y, 8f, 2f);
		command.resolveStripTextureCoordinates(entry, stripIndex);
		registerCommand(command);
		restartPatternBatch(restartInstancedAfterDirect, restartBatchedAfterDirect, entry.atlasIndex());
	}

	private Integer resolveEffectivePatternPaletteTextureId() {
		if (useUnderwaterPaletteForBackground && paletteTextures.getUnderwaterPaletteTextureId() != null) {
			return paletteTextures.getUnderwaterPaletteTextureId();
		}
		return paletteTextures.getCombinedPaletteTextureId();
	}

	private void restartPatternBatch(boolean instanced, boolean batched, int atlasIndex) {
		if (instanced && instancedPatternRenderer != null) {
			instancedPatternRenderer.beginBatch(atlasIndex);
			instancedBatchActive = true;
		} else if (batched && batchedRenderer != null) {
			batchedRenderer.beginBatch();
		}
	}

	/**
	 * Begin a new pattern batch. Call before rendering patterns for a frame/layer.
	 */
	public void beginPatternBatch() {
		if (spritePresentationBuilder != null) return;
		if (headlessMode) {
			return;
		}
		if (!batchingEnabled) {
			return;
		}
		if (instancedBatchingEnabled && instancedPatternRenderer != null && instancedPatternRenderer.isSupported()) {
			instancedPatternRenderer.beginBatch();
			instancedBatchActive = true;
			return;
		}
		if (batchedRenderer == null) {
			batchedRenderer = new BatchedPatternRenderer(this, GameServices.configuration());
		}
		batchedRenderer.beginBatch();
	}

	/**
	 * Flush the current pattern batch. Call after all patterns for a layer are
	 * submitted. This queues the batch command for execution in the proper order.
	 */
	public void flushPatternBatch() {
		if (spritePresentationBuilder != null) return;
		if (headlessMode) {
			return;
		}
		if (instancedBatchActive && instancedPatternRenderer != null) {
			GLCommandable batchCommand = instancedPatternRenderer.endBatch();
			if (batchCommand != null) {
				registerCommand(batchCommand);
			}
			instancedBatchActive = false;
			return;
		}
		if (batchedRenderer != null) {
			// Always call endBatch to reset batchActive state, even if batch is empty
			GLCommandable batchCommand = batchedRenderer.endBatch();
			if (batchCommand != null) {
				registerCommand(batchCommand);
			}
		}
	}

	/**
	 * Begin a new shadow batch. Shadow batches use VDP shadow/highlight mode
	 * where palette index 14 darkens the background.
	 */
	public void beginShadowBatch() {
		if (headlessMode) {
			return;
		}
		if (batchedRenderer == null) {
			batchedRenderer = new BatchedPatternRenderer(this, GameServices.configuration());
		}
		batchedRenderer.beginShadowBatch();
	}

	/**
	 * Add a shadow pattern to the current shadow batch.
	 */
	public void addShadowPattern(int patternIndex, PatternDesc desc, int x, int y) {
		if (headlessMode) {
			return;
		}
		ensurePatternAtlas();
		PatternAtlas.Entry entry = patternAtlas != null ? patternAtlas.getEntry(patternIndex) : null;
		if (entry == null) {
			return;
		}
		if (batchedRenderer != null && batchedRenderer.isShadowBatchActive()) {
			if (!tryAddBatchedShadow(entry, desc, x, y)) {
				flushShadowBatch();
				batchedRenderer.beginShadowBatch(entry.atlasIndex());
				if (!tryAddBatchedShadow(entry, desc, x, y)) {
					throw new IllegalStateException("Unable to add shadow pattern to a fresh batch");
				}
			}
		}
	}

	/**
	 * Flush the current shadow batch. This queues the shadow command for
	 * execution with multiplicative blending.
	 */
	public void flushShadowBatch() {
		if (headlessMode) {
			return;
		}
		if (batchedRenderer != null) {
			GLCommandable batchCommand = batchedRenderer.endShadowBatch();
			if (batchCommand != null) {
				registerCommand(batchCommand);
			}
		}
	}

	/**
	 * Enable or disable pattern batching.
	 */
	public void setBatchingEnabled(boolean enabled) {
		this.batchingEnabled = enabled;
	}

	public boolean isBatchingEnabled() {
		return batchingEnabled;
	}

	public void setInstancedBatchingEnabled(boolean enabled) {
		this.instancedBatchingEnabled = enabled;
	}

	public boolean isInstancedBatchingEnabled() {
		return instancedBatchingEnabled;
	}

	/**
	 * Enables vertical wrap Y adjustment for sprite/object rendering.
	 * While enabled, Y coordinates passed to renderPattern/renderPatternWithId
	 * are adjusted to the nearest equivalent position modulo the wrap range,
	 * emulating the Mega Drive VDP's modular sprite coordinate system.
	 * <p>
	 * Call this BEFORE rendering sprite/object batches in vertically-wrapping zones,
	 * and call {@link #disableVerticalWrapAdjust()} afterwards to avoid affecting
	 * HUD or other non-wrapping renders.
	 *
	 * @param range   The vertical wrap range in pixels (e.g. 2048)
	 * @param cameraY The current camera Y position
	 */
	public void enableVerticalWrapAdjust(int range, int cameraY) {
		this.verticalWrapAdjustEnabled = true;
		this.verticalWrapRange = range;
		this.verticalWrapCameraY = cameraY;
	}

	/**
	 * Disables vertical wrap Y adjustment.
	 */
	public void disableVerticalWrapAdjust() {
		this.verticalWrapAdjustEnabled = false;
	}

	/**
	 * Get the combined palette texture ID.
	 */
	public Integer getCombinedPaletteTextureId() {
		return paletteTextures.getCombinedPaletteTextureId();
	}

	/**
	 * Get the texture ID for a cached pattern.
	 * @param patternIndex the pattern ID to look up
	 * @return the texture ID, -1 in headless mode when entry exists, or null if not cached
	 */
	public Integer getPatternTextureId(int patternIndex) {
		if (patternAtlas == null) {
			return null;
		}
		PatternAtlas.Entry entry = patternAtlas.getEntry(patternIndex);
		if (entry == null) {
			return null;  // Pattern not cached
		}
		int textureId = patternAtlas.getTextureId(entry.atlasIndex());
		// In headless mode, textureId is 0, return -1 as sentinel
		return textureId == 0 && headlessMode ? -1 : textureId;
	}

	public Integer getPatternAtlasTextureId() {
		return patternAtlas != null ? patternAtlas.getTextureId() : null;
	}

	public Integer getPatternAtlasTextureId(int atlasIndex) {
		return patternAtlas != null ? patternAtlas.getTextureId(atlasIndex) : null;
	}

	public int getPatternAtlasWidth() {
		return patternAtlas != null ? patternAtlas.getAtlasWidth() : 0;
	}

	public int getPatternAtlasHeight() {
		return patternAtlas != null ? patternAtlas.getAtlasHeight() : 0;
	}

	public PatternAtlas.Entry getPatternAtlasEntry(int patternId) {
		ensurePatternAtlas();
		return patternAtlas != null ? patternAtlas.getEntry(patternId) : null;
	}

	public PatternAtlas getPatternAtlas() {
		return patternAtlas;
	}

	/**
	 * Update cached viewport dimensions. Call this from Engine.reshape().
	 * Avoids expensive glGetIntegerv(GL_VIEWPORT) calls every batch.
	 */
	public void setViewport(int x, int y, int width, int height) {
		this.viewportX = x;
		this.viewportY = y;
		this.viewportWidth = width;
		this.viewportHeight = height;
		// Reshape may follow a config change; re-resolve the configured screen height lazily.
		this.cachedConfigScreenHeightPx = -1;
	}

	/** Internal bounded offscreen viewport; restores the host's projection source/override exactly. */
	void withPresentationViewport(int width, int height, Runnable draw) {
		int oldX = viewportX, oldY = viewportY, oldWidth = viewportWidth, oldHeight = viewportHeight;
		int oldProjectionWidth = projectionWidth, oldLogicalHeight = cachedConfigScreenHeightPx;
		float[] oldProjection = projectionMatrixBuffer;
		try {
			setViewport(0, 0, width, height);
			projectionWidth = width;
			cachedConfigScreenHeightPx = height;
			projectionMatrixBuffer = new org.joml.Matrix4f().ortho2D(0, width, 0, height).get(new float[16]);
			draw.run();
		} finally {
			viewportX = oldX; viewportY = oldY; viewportWidth = oldWidth; viewportHeight = oldHeight;
			projectionWidth = oldProjectionWidth; cachedConfigScreenHeightPx = oldLogicalHeight;
			projectionMatrixBuffer = oldProjection;
		}
	}

	/**
	 * Returns the configured logical screen height (SCREEN_HEIGHT_PIXELS), cached
	 * to avoid a config-service lookup per rendered pattern. The cache is
	 * invalidated by {@link #setViewport} (called from Engine.reshape()) and
	 * {@link #resetState()}.
	 */
	public int getConfiguredScreenHeightPx() {
		int cached = cachedConfigScreenHeightPx;
		if (cached <= 0) {
			cached = GameServices.configuration().getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
			cachedConfigScreenHeightPx = cached;
		}
		return cached;
	}

	public int getViewportX() {
		return viewportX;
	}

	public int getViewportY() {
		return viewportY;
	}

	public int getViewportWidth() {
		return viewportWidth;
	}

	public int getViewportHeight() {
		return viewportHeight;
	}

	public void setProjectionWidth(int width) {
		this.projectionWidth = Math.max(320, width);
	}

	public int getProjectionWidth() {
		return projectionWidth;
	}

	/**
	 * Get the current waterline Y position in screen coordinates (pixels from top).
	 * Returns a negative value if there's no water in the current zone.
	 */
	public float getWaterlineScreenY() {
		return waterlineScreenY;
	}

	/**
	 * Set the waterline Y position for sprite priority shader underwater palette.
	 * Called by LevelManager.updateWaterShaderState() each frame.
	 *
	 * @param y Screen Y position where water starts (negative to disable)
	 */
	public void setWaterlineScreenY(float y) {
		this.waterlineScreenY = y;
	}

	/**
	 * Get the physical window height in pixels.
	 */
	public float getWindowHeight() {
		return windowHeight;
	}

	/**
	 * Set the physical window height for sprite priority shader.
	 * Called by LevelManager.updateWaterShaderState() each frame.
	 */
	public void setWindowHeight(float height) {
		this.windowHeight = height;
	}

	/**
	 * Get the logical screen height (e.g., 224 for Genesis).
	 */
	public float getScreenHeight() {
		return screenHeight;
	}

	/**
	 * Set the logical screen height for sprite priority shader.
	 * Called by LevelManager.updateWaterShaderState() each frame.
	 */
	public void setScreenHeight(float height) {
		this.screenHeight = height;
	}

	/**
	 * Check if water is enabled for the current zone.
	 */
	public boolean isWaterEnabled() {
		return waterEnabled;
	}

	/**
	 * Set whether water is enabled for sprite priority shader.
	 * Called by LevelManager.updateWaterShaderState() each frame.
	 */
	public void setWaterEnabled(boolean enabled) {
		this.waterEnabled = enabled;
	}

    public Integer getUnderwaterPaletteTextureId() { return paletteTextures.getUnderwaterPaletteTextureId(); }

    /** Discards texture/publication/residency resources, leaving CPU banks intact. */
    public void clearPaletteTextures() { paletteTextures.clearPaletteTextures(); }

    public Integer cacheUnderwaterPaletteTexture(Palette[] palettes, Palette normalLine0) {
        return paletteTextures.cacheUnderwaterPaletteTexture(palettes, normalLine0);
    }

	/**
	 * Release GL resources owned by the level renderers, palette textures, and
	 * native upload buffers. Safe to call in any mode (headless guards internally).
	 */
	private void releasePerLevelResources() {
		paletteTextures.forgetTextureEntries();
		if (!headlessMode && glInitialized) {
			if (backgroundRenderer != null) {
				backgroundRenderer.cleanup();
			}
			if (tilemapGpuRenderer != null) {
				tilemapGpuRenderer.cleanup();
			}
			if (instancedPatternRenderer != null) {
				instancedPatternRenderer.cleanup();
			}
		}
		backgroundRenderer = null;
		tilemapGpuRenderer = null;
		instancedPatternRenderer = null;
		clearPaletteTextures();
	}

	/**
	 * Resets the pattern atlas and palette textures without destroying shaders
	 * or the GL context.  Use after preview capture to discard all stale pattern
	 * data and palette state so subsequent rendering starts from a clean GPU.
	 */
	public void resetPatternAndPaletteState() {
		objectVisualPatterns.clear();
		if (headlessMode || !glInitialized) {
			return;
		}
		if (patternAtlas != null) {
			patternAtlas.cleanup();
			patternAtlas = new PatternAtlas(ATLAS_WIDTH, ATLAS_HEIGHT, profiler);
			patternAtlas.init();
		}
		// Reset palette textures so they're rebuilt from scratch
		clearPaletteTextures();
		discardCommands(commands, 0);
		commands.clear();
	}

	/**
	 * Cleanup method to delete textures and release resources.
	 */
	public void cleanup() {
        presentationTrailBanks.clear();
		objectVisualPatterns.clear();
        presentationTrailGenerations.clear();
        if (!headlessMode) arenaMaskRenderer.cleanup();
		clearPendingRenderThreadTasks();
		discardCommands(commands, 0);
		commands.clear();
		if (headlessMode || !glInitialized) {
			// In headless mode, just clear the tracking maps
			if (patternAtlas != null) {
				patternAtlas.cleanupHeadless();
			}
			if (batchedRenderer != null) {
				batchedRenderer.cleanupHeadless();
			}
			if (instancedPatternRenderer != null) {
				instancedPatternRenderer.cleanupHeadless();
			}
			clearPaletteTextures();
			PatternRenderCommand.cleanupHeadless();
			GLCommand.cleanupHeadless();
			GLCommandGroup.cleanup();
			return;
		}
		// Delete pattern atlas texture
		if (patternAtlas != null) {
			patternAtlas.cleanup();
		}
		// Cleanup shader programs
		if (defaultShaderProgram != null) {
			defaultShaderProgram.cleanup();
		}
		if (waterShaderProgram != null) {
			waterShaderProgram.cleanup();
		}
		if (debugShaderProgram != null) {
			debugShaderProgram.cleanup();
		}
		if (fadeShaderProgram != null) {
			fadeShaderProgram.cleanup();
		}
		if (shadowShaderProgram != null) {
			shadowShaderProgram.cleanup();
		}
        if (nativeShadowShaderProgram != null) nativeShadowShaderProgram.cleanup();
		if (batchedRenderer != null) {
			batchedRenderer.cleanup();
		}
		// Sprite priority rendering cleanup
		if (spritePriorityShaderProgram != null) {
			spritePriorityShaderProgram.cleanup();
		}
		if (tilePriorityFBO != null) {
			tilePriorityFBO.cleanup();
		}
		// Reset fade manager
		if (fadeManager != null) {
            fadeManager.bindPaletteGraphics(null);
			fadeManager.cleanup();
			fadeManager.cancel();
			fadeManager = null;
		}
		if (displayShaderPipeline != null) {
			displayShaderPipeline.dispose();
			displayShaderPipeline = null;
		}
		// Release renderers, palette textures, native buffers
		releasePerLevelResources();
		PatternRenderCommand.cleanup();
		GLCommand.cleanup();
		GLCommandGroup.cleanup();
		glInitialized = false;
	}

	private void ensurePatternAtlas() {
		if (patternAtlas == null) {
			patternAtlas = new PatternAtlas(ATLAS_WIDTH, ATLAS_HEIGHT, profiler);
		}
		if (!patternAtlas.isInitialized() && glInitialized) {
			patternAtlas.init();
		}
	}

	/**
	 * Singleton access to the GraphicsManager instance.
	 */
	public static synchronized final GraphicsManager getInstance() {
		if (graphicsManager == null) {
			graphicsManager = new GraphicsManager();
		}
		return graphicsManager;
	}

	/**
	 * Resets mutable rendering state without destroying the singleton instance.
	 * Preserves headlessMode and glInitialized configuration.
	 * Clears render command queues and palette caches.
	 */
	public void resetState() {
		discardCommands(commands, 0);
		commands.clear();
		clearPendingRenderThreadTasks();
		releasePerLevelResources();
		camera = null;
		bootstrapCamera = null;
		fadeManager = null;
		bootstrapFadeManager = null;
		if (displayShaderPipeline != null) {
			displayShaderPipeline.dispose();
			displayShaderPipeline = null;
		}
		useUnderwaterPaletteForBackground = false;
		useSpritePriorityShader = false;
		currentSpriteHighPriority = false;
		currentSpriteTileOcclusionPaletteMask = 0xF;
		spriteSatCollectionActive = false;
		spriteMaskRequested = false;
		satReplayBatchOpen = false;
		spriteSatEntries.clear();
		spriteSatFragments = null;
		currentSpriteSatDebugSource = null;
		spritePresentationSubject = SpritePresentation.Subject.WORLD;
        currentSpritePixelOperation = SpriteSatEntry.PixelOperation.NORMAL;
        currentShadow15Backdrop = null;
        objectHeadFragmentBanks.reset();
		waterlineScreenY = 0;
		windowHeight = 224;
		screenHeight = 224;
		waterEnabled = false;
		cachedConfigScreenHeightPx = -1;
		if (patternAtlas != null) {
			if (headlessMode) {
				patternAtlas.cleanupHeadless();
			} else {
				patternAtlas.cleanup();
			}
			patternAtlas = null;
		}
	}

	/**
	 * Destroys the singleton and releases all GL resources (shaders, FBOs, atlas).
	 * The next call to {@link #getInstance()} will create a fresh instance.
	 * <p>
	 * Use this only when full GL re-initialization is required (e.g. GPU tests
	 * that need clean shader/tilemap state). For normal test teardown, prefer
	 * {@link #resetState()} which preserves cached references.
	 */
	public static synchronized void destroyForReinit() {
		if (graphicsManager != null) {
			graphicsManager.cleanup();
			graphicsManager = null;
		}
	}

	public ShaderProgram getShaderProgram() {
		if ((useSpritePriorityShader || nativeSpriteOwnership != null) && spritePriorityShaderProgram != null) {
			return spritePriorityShaderProgram;
		}
		return currentShaderProgram;
	}

	/** Priority choice is frozen at picture submission; native water/default intent still resolves at execution. */
	ShaderProgram presentationShader(boolean priority) {
		return (priority || nativeSpriteOwnership != null) && spritePriorityShaderProgram != null
                ? spritePriorityShaderProgram : currentShaderProgram;
	}

	/** Immutable local cache sample for ROM-recipe projection; never supplies guest art content. */
	SpritePresentation.PatternVersion scenePatternSample(int patternId) {
		return patternAtlas == null ? null : patternAtlas.scenePatternSample(patternId);
	}

	/**
	 * Installs the rendering host's current projection. Null clears the source.
	 */
	void setProjectionSource(RenderProjection projection) {
		this.projectionSource = projection;
	}

	/**
	 * Returns the projection source without exposing engine orchestration.
	 */
	public RenderProjection getProjectionSource() {
		return projectionSource;
	}

	/**
	 * Set the projection matrix buffer directly.
	 * Use this when testing or when no Engine instance is available.
	 * The buffer should be a 16-element float array in column-major order.
	 */
	public void setProjectionMatrixBuffer(float[] buffer) {
		this.projectionMatrixBuffer = buffer;
	}

	/** Exact nullable local override; scoped engine projections must not capture source fallback. */
	float[] rawProjectionMatrixBuffer() { return projectionMatrixBuffer; }

	/**
	 * Get the projection matrix buffer for shader-based rendering.
	 * First checks a local override, then the rendering host's projection.
	 * @return the projection matrix as a 16-element float array, or null if not available
	 */
	public float[] getProjectionMatrixBuffer() {
		// First try local buffer (set directly by tests or other code)
		if (projectionMatrixBuffer != null) {
			return projectionMatrixBuffer;
		}
		if (projectionSource != null) {
			return projectionSource.getProjectionMatrixBuffer();
		}
		return null;
	}

	/**
	 * Push a centered-320 safe-area projection for the configured viewport width.
	 * At native width (320) the safe-area ortho equals the scene ortho [0, 320] — a no-op.
	 * <p>
	 * Callers MUST pair every call to this method with a call to
	 * {@link #endSafeAreaProjection()} before {@code UiRenderPipeline.renderFadePass()}
	 * so the fade pass runs at the full viewport projection, not the safe-area.
	 *
	 * @param viewportWidth       physical viewport width in pixels
	 * @param viewportHeightPixels physical viewport height in pixels
	 */
	public void beginSafeAreaProjection(int viewportWidth, int viewportHeightPixels) {
		safeAreaMatrix.identity().ortho2D(
				com.openggf.graphics.pipeline.SafeAreaProjection.orthoLeft(viewportWidth),
				com.openggf.graphics.pipeline.SafeAreaProjection.orthoRight(viewportWidth),
				0f, viewportHeightPixels);
		safeAreaMatrix.get(safeAreaBuffer);
		setProjectionMatrixBuffer(safeAreaBuffer);
	}

	/**
	 * Restore the engine's scene projection by clearing the local override.
	 * Must be called after safe-area UI drawing and BEFORE
	 * {@code UiRenderPipeline.renderFadePass()} so the fade runs at the full viewport.
	 */
	public void endSafeAreaProjection() {
		setProjectionMatrixBuffer(null);
	}

	/**
	 * Enable or disable sprite priority shader mode.
	 * When enabled, getShaderProgram() returns the sprite priority shader.
	 */
	public void setUseSpritePriorityShader(boolean use) {
		this.useSpritePriorityShader = use;
	}

	/**
	 * Check if sprite priority shader mode is enabled.
	 */
	public boolean isUseSpritePriorityShader() {
		return useSpritePriorityShader || submittingNativeSpriteOwnership;
	}

    boolean nativeSpriteOwnershipActive() { return nativeSpriteOwnership != null; }

    SpritePriorityShaderProgram nativeShadowShader() {
        if (nativeShadowShaderProgram == null) throw new IllegalStateException("Native shadow shader unavailable");
        return nativeShadowShaderProgram;
    }

    void withSpritePixelOperation(SpriteSatEntry.PixelOperation operation, Runnable producer) {
        var previous = currentSpritePixelOperation;
        currentSpritePixelOperation = java.util.Objects.requireNonNull(operation, "pixel operation");
        try { producer.run(); }
        finally { currentSpritePixelOperation = previous; }
    }

    void withShadow15Backdrop(int texture, int originX, int originY, int width, int height, Runnable producer) {
        var previous = currentShadow15Backdrop;
        currentShadow15Backdrop = new Shadow15Backdrop(texture, originX, originY, width, height);
        try { producer.run(); }
        finally { currentShadow15Backdrop = previous; }
    }

    void submitShadowPieces(Runnable preparedSubmissions) {
        if (!spriteSatCollectionActive) throw new IllegalStateException("Shadow pieces require an active native SAT collection");
        java.util.Objects.requireNonNull(preparedSubmissions, "shadow submissions");
        withSpritePixelOperation(SpriteSatEntry.PixelOperation.SHADOW_15, preparedSubmissions);
    }

    void submitShadowGroups(List<SpriteSatEntry> geometry, Runnable producer) {
        var shadowGeometry = geometry.stream()
                .map(entry -> entry.withPixelOperation(SpriteSatEntry.PixelOperation.SHADOW_15)).toList();
        withSpritePixelOperation(SpriteSatEntry.PixelOperation.SHADOW_15,
                () -> submitSpriteSatGroups(shadowGeometry, producer));
    }

    private void validateShadowPattern(int patternId, int palette) {
        if (palette != 3) throw new IllegalArgumentException("SHADOW_15 requires native palette line 3");
        var version = scenePatternSample(patternId);
        if (version == null) throw new IllegalArgumentException("SHADOW_15 has no resident indexed source: " + patternId);
        for (long word : new long[]{version.a(), version.b(), version.c(), version.d()}) {
            for (int pixel = 0; pixel < 16; pixel++) {
                int index = (int) ((word >>> (pixel * 4)) & 15);
                if (index != 0 && index != 15) throw new IllegalArgumentException(
                        "SHADOW_15 source " + patternId + " contains unsupported color " + index);
            }
        }
    }

    private void validateShadowEntry(SpriteSatEntry entry) {
        // Canonical expansion retains clipped source addresses under every flip.
        entry.piece().forEachTile((pattern, hFlip, vFlip, palette, x, y) ->
                validateShadowPattern(pattern, palette));
    }

    /** Published native operators retain the same immutable source row crop and final quad. */
    void renderShadowTile(SpritePresentation.Tile tile, int cameraX, int cameraY, float[] quad, float opacity) {
        if (tile.pixelOperation() != SpriteSatEntry.PixelOperation.SHADOW_15)
            throw new IllegalArgumentException("Expected constrained shadow tile");
        validateShadowPattern(tile.patternId(), tile.palette());
        if (spritePresentationBuilder != null) {
            var builder = spritePresentationBuilder;
            builder.tiles.add(new SpritePresentation.Tile(tile.layer(), tile.patternId(), tile.palette(),
                    tile.hFlip(), tile.vFlip(), tile.priority(), tile.x() + cameraX - builder.cameraX,
                    tile.y() + cameraY - builder.cameraY, tile.width(), tile.height(), tile.priorityShader(),
                    tile.occlusionMask(), tile.ghost(), tile.ghostAlpha(), tile.rowStart(), tile.rowEnd(),
                    tile.subject(), tile.ghostColour(), tile.pixelOperation()));
            SpritePresentation.bindPatternVersion(this, tile.patternId(), scenePatternSample(tile.patternId()));
            return;
        }
        if (headlessMode || tile.rowStart() >= tile.rowEnd()) return;
        flushPatternBatch();
        var atlas = patternAtlas.getEntry(tile.patternId());
        if (atlas == null) throw new IllegalStateException("Missing resident shadow atlas entry");
        var command = PatternRenderCommand.obtainNativeShadowRows(atlas, tile.palette(), tile.hFlip(), tile.vFlip(),
                tile.priority(), 0, 0, tile.rowStart(), tile.rowEnd(), this);
        command.presentation(quad, 0, opacity);
        registerCommand(command);
    }

    /** Queues a native coverage scope; all GPU work remains on the command execution thread. */
    void withNativeSpriteOwnership(Runnable producer) {
        if (submittingNativeSpriteOwnership) throw new IllegalStateException("Nested native sprite ownership");
        if (spritePresentationBuilder != null) { producer.run(); return; }
        boolean previousPriorityShader = useSpritePriorityShader;
        if (headlessMode) {
            submittingNativeSpriteOwnership = true;
            try { producer.run(); }
            finally {
                submittingNativeSpriteOwnership = false;
                useSpritePriorityShader = previousPriorityShader;
            }
            return;
        }
        flushPatternBatch();
        var state = new NativeSpriteOwnership.Commands(this, scope -> nativeSpriteOwnership = scope);
        registerCommand(state.begin);
        submittingNativeSpriteOwnership = true;
        try { producer.run(); }
        finally {
            try { flushPatternBatch(); }
            finally {
                submittingNativeSpriteOwnership = false;
                useSpritePriorityShader = previousPriorityShader;
                registerCommand(state.end);
            }
        }
    }

    /** Engine-only layered coverage; the ordinary no-ghost ownership path stays intact. */
    <T> void replayNativeLayers(List<T> frontToBack, java.util.function.Predicate<T> isGhost,
                               java.util.function.Consumer<List<T>> emitter) {
        java.util.Objects.requireNonNull(emitter, "native layer emitter");
        var layers = NativeSpriteOwnership.layers(frontToBack, isGhost);
        if (layers.isEmpty()) {
            withNativeSpriteOwnership(() -> emitter.accept(frontToBack));
            return;
        }
        if (submittingNativeSpriteOwnership) throw new IllegalStateException("Nested native sprite ownership");
        if (spritePresentationBuilder != null) { emitter.accept(frontToBack); return; }
        boolean previousPriorityShader = useSpritePriorityShader;
        if (headlessMode) {
            submittingNativeSpriteOwnership = true;
            try { NativeSpriteOwnership.replayLayers(layers, (rank, ghost) -> { }, emitter); }
            finally {
                submittingNativeSpriteOwnership = false;
                useSpritePriorityShader = previousPriorityShader;
            }
            return;
        }
        flushPatternBatch();
        var state = new NativeSpriteOwnership.Commands(this, scope -> nativeSpriteOwnership = scope, true);
        registerCommand(state.begin);
        submittingNativeSpriteOwnership = true;
        try {
            NativeSpriteOwnership.replayLayers(layers, (rank, ghost) -> {
                flushPatternBatch();
                registerCommand(state.layer(rank, ghost));
            }, emitter);
        } finally {
            try { flushPatternBatch(); }
            finally {
                submittingNativeSpriteOwnership = false;
                useSpritePriorityShader = previousPriorityShader;
                registerCommand(state.end);
            }
        }
    }

	/**
	 * Set whether the current sprite being rendered has high priority.
	 * This is used by the sprite priority shader to determine if the sprite
	 * should appear above or behind high-priority tiles.
	 */
	public void setCurrentSpriteHighPriority(boolean highPriority) {
		this.currentSpriteHighPriority = highPriority;
		this.currentSpriteTileOcclusionPaletteMask = highPriority ? 0 : 0xF;
	}

	/**
	 * Get whether the current sprite being rendered has high priority.
	 */
	public boolean getCurrentSpriteHighPriority() {
		return currentSpriteHighPriority;
	}

	public void setCurrentSpriteTileOcclusionPaletteMask(int mask) {
		currentSpriteTileOcclusionPaletteMask = mask & 0xF;
		currentSpriteHighPriority = currentSpriteTileOcclusionPaletteMask == 0;
	}

	public int getCurrentSpriteTileOcclusionPaletteMask() {
		return currentSpriteTileOcclusionPaletteMask;
	}

	void cancelSpritePresentationCollection() {
		spriteSatCollectionActive = false;
		spriteMaskRequested = false;
		spriteSatEntries.clear();
		spriteSatFragments = null;
		currentSpriteSatDebugSource = null;
		currentSpriteSatBucket = RenderPriority.MIN;
	}

	public void beginSpriteSatCollection() {
		int rasterWidth = getProjectionWidth();
		int rasterHeight = getConfiguredScreenHeightPx();
		int rasterX = spritePresentationBuilder != null ? spritePresentationBuilder.cameraX : getCamera().getXWithShake();
		int rasterY = spritePresentationBuilder != null ? spritePresentationBuilder.cameraY : getCamera().getYWithShake();
		if ((rasterWidth & 7) == 0) {
			beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.extended(rasterX, rasterY, rasterWidth, rasterHeight));
		} else {
			beginLogicalSpriteSatCollection(SpriteSatMaskPostProcessor.LogicalViewport.h40(
					rasterX, rasterY, rasterWidth, rasterHeight));
		}
	}

	/** Explicit native inner raster, e.g. fixed H32 inside a wider special-stage presentation. */
	void beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster raster) {
        if (currentSpritePixelOperation != SpriteSatEntry.PixelOperation.NORMAL)
            throw new IllegalStateException("Shadow submissions may only submit prepared pieces, not begin a SAT table");
		beginLogicalSpriteSatCollection(SpriteSatMaskPostProcessor.LogicalViewport.fromAligned(
				java.util.Objects.requireNonNull(raster, "SAT raster")));
	}

	/** Exact logical presentation span with unchanged native SAT admission. */
	void beginLogicalSpriteSatCollection(SpriteSatMaskPostProcessor.LogicalViewport viewport) {
        if (currentSpritePixelOperation != SpriteSatEntry.PixelOperation.NORMAL)
            throw new IllegalStateException("Shadow submissions may only submit prepared pieces, not begin a SAT table");
		spriteSatViewport = java.util.Objects.requireNonNull(viewport, "SAT viewport");
		spriteSatCollectionActive = true;
		spriteMaskRequested = false;
		spriteSatEntries.clear();
		spriteSatFragments = null;
		currentSpriteSatDebugSource = null;
		currentSpriteSatBucket = RenderPriority.MIN;
	}

	public boolean isSpriteSatCollectionActive() {
		return spriteSatCollectionActive;
	}

    void bindSpriteScanlineLimitSource(java.util.function.BooleanSupplier source) {
        spriteScanlineLimitSource = java.util.Objects.requireNonNull(source, "sprite line-limit source");
    }

	public void requestSpriteMask() {
		SpriteCaptureControl.control(
				"SAT sprite-mask controls require native display-list replay");
		if (spriteSatCollectionActive) {
			spriteMaskRequested = true;
		}
	}

	/** Submits one non-drawable SAT control entry while collection is active. */
	public void submitSpriteSatControlEntry(int x, int y, int widthTiles, int heightTiles,
			int rawTileWordLow11) {
		SpriteCaptureControl.control(
				"SAT control entries require native display-list replay");
		if (!spriteSatCollectionActive) return;
		spriteSatEntries.add(new SpriteSatEntry(currentSpriteSatBucket, new SpritePiece(x, y,
				widthTiles, heightTiles, 0, rawTileWordLow11, 0,
				false, false, false, currentSpriteHighPriority,
				SpriteMaskReplayRole.NORMAL, 0, widthTiles, 0, heightTiles,
				spritePresentationBuilder == null ? currentSpriteSatDebugSource : spritePresentationBuilder.layer.name())));
	}

	public void setCurrentSpriteSatDebugSource(String debugSource) {
		currentSpriteSatDebugSource = debugSource;
	}

	public void setCurrentSpriteSatBucket(int bucket) {
		currentSpriteSatBucket = RenderPriority.clamp(bucket);
	}

	public void submitSpriteSatPiece(SpritePieceRenderer.PreparedPiece piece) {
		if (!spriteSatCollectionActive || piece == null) {
			return;
		}
		String presentationSource = spritePresentationBuilder == null ? currentSpriteSatDebugSource
				: spritePresentationBuilder.layer.name();
		// The pinned producer type is copied only at this compatibility ingress.
		SpritePiece admitted = new SpritePiece(piece.x(), piece.y(), piece.widthTiles(), piece.heightTiles(),
				piece.firstPatternIndex(), piece.rawTileWordLow11(), piece.paletteIndex(),
				piece.hFlip(), piece.vFlip(), piece.piecePriority(), piece.globalHighPriority(),
				piece.maskReplayRole(), piece.startColTile(), piece.colCountTiles(),
				piece.startRowTile(), piece.rowCountTiles(), piece.debugSource());
		if (presentationSource != null) {
			admitted = admitted.withDebugSource(presentationSource);
		}
		SpriteSatEntry entry = new SpriteSatEntry(currentSpriteSatBucket, admitted)
                .withTileOcclusionPaletteMask(getCurrentSpriteTileOcclusionPaletteMask())
                .withPixelOperation(currentSpritePixelOperation);
        if (entry.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15) validateShadowEntry(entry);
		if (spritePresentationSubject != SpritePresentation.Subject.WORLD) {
			entry = entry.withPresentationSubject(spritePresentationSubject);
		}
		if (ghostRenderEffectActive) {
			// The SAT replay runs after the ghost renderer has ended its effect; carry it.
			entry = entry.withGhost(new SpriteSatEntry.Ghost(ghostRenderAlpha, ghostRenderPreservesColour));
		}
		(spriteSatFragments == null ? spriteSatEntries : spriteSatFragments).add(entry);
	}

    /**
     * Admit native geometry once while retaining scattered resident art as immutable
     * fragments. Used by the HUD: its native SAT pieces include several digits, whose
     * engine pattern addresses need not be contiguous. The producer submits prepared
     * pieces, never direct GL commands. Geometry with no art still consumes its budget.
     */
    void submitSpriteSatGroups(List<SpriteSatEntry> geometry, Runnable producer) {
        if (!spriteSatCollectionActive || spriteSatFragments != null) {
            throw new IllegalStateException("SAT groups require an active, unnested collection");
        }
        var fragments = new ArrayList<SpriteSatEntry>();
        spriteSatFragments = fragments;
        try { producer.run(); }
        finally { spriteSatFragments = null; }
        List<SpriteSatEntry> frozen = List.copyOf(fragments);
        for (SpriteSatEntry group : geometry) {
            spriteSatEntries.add(group.withReplayFragments(frozen));
        }
    }

    /** Attach only to the last tile in the currently collecting native art stream. */
    void attachObjectVisualSatMask(int mask) {
        List<SpriteSatEntry> active = activeVisualSatTiles();
        int index = active.size() - 1;
        active.set(index, active.get(index).withTileOcclusionPaletteMask(mask & 15));
    }

    void clipObjectVisualSatTile(int rowStart, int rowEnd) {
        List<SpriteSatEntry> active = activeVisualSatTiles();
        if (rowStart < 0 || rowEnd > 8 || rowStart > rowEnd) {
            throw new IllegalArgumentException("Visual row clip must stay within one native tile");
        }
        int index = active.size() - 1;
        SpriteSatEntry tile = active.get(index);
        int top = Math.max(tile.visibleTopY(), tile.piece().y() + rowStart);
        int bottom = Math.max(top, Math.min(tile.visibleBottomY(), tile.piece().y() + rowEnd));
        active.set(index, tile.withVisibleScanlines(top, bottom));
    }

    private List<SpriteSatEntry> activeVisualSatTiles() {
        List<SpriteSatEntry> active = spriteSatFragments != null ? spriteSatFragments : spriteSatEntries;
        if (!spriteSatCollectionActive || active.isEmpty()) {
            throw new IllegalStateException("Visual attachment has no submitted native SAT tile");
        }
        if (active.getLast().replayFragments() != null) {
            throw new IllegalStateException("Visual attachment cannot modify a completed SAT composite");
        }
        return active;
    }

	public void endSpriteSatCollectionAndReplay() {
		if (!spriteSatCollectionActive) {
			return;
		}
        if (currentSpritePixelOperation != SpriteSatEntry.PixelOperation.NORMAL)
            throw new IllegalStateException("Shadow submissions may only submit prepared pieces, not replay a SAT table");

		boolean applyMask = spriteMaskRequested;

		// Evaluation retains masks/table capacity even when line budgets are disabled.
		// Use logical display dimensions: a wide trace can retain a native-width camera.
		// Results are thread-owned scratch, consumed synchronously before collection clears.
		List<SpriteSatEntry> processedEntries = SpriteSatMaskPostProcessor.processLogicalReusable(
                spriteSatEntries, applyMask, spriteSatViewport,
                spriteScanlineLimitSource.getAsBoolean());

		spriteSatCollectionActive = false;
		spriteMaskRequested = false;
		currentSpriteSatDebugSource = null;
		currentSpriteSatBucket = RenderPriority.MIN;

		try {
			if (processedEntries.isEmpty()) {
				return;
			}

			if (spritePresentationBuilder != null) {
				for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
					for (int i = processedEntries.size() - 1; i >= 0; i--) {
						SpriteSatEntry entry = processedEntries.get(i);
						if (entry.priorityBucket() != bucket) continue;
						if (entry.piece().debugSource() != null)
							spritePresentationBuilder.layer = SpritePresentation.Layer.valueOf(entry.piece().debugSource());
						setCurrentSpriteHighPriority(entry.piece().globalHighPriority());
                        setCurrentSpriteTileOcclusionPaletteMask(entry.tileOcclusionPaletteMask());
						SpritePresentation.withSubject(this, entry.presentationSubject(),
								() -> appendBatchedReplayCommands(entry, -1));
					}
				}
				return;
			}

            var headReplay = processedEntries.stream().map(entry -> entry.presentationSubject().head())
                    .filter(java.util.Objects::nonNull).findFirst();
            if (headReplay.isPresent()) {
                headReplay.get().replay().replay(this, processedEntries,
                        entry -> appendBatchedReplayCommands(entry, -1));
                return;
            }

            if (processedEntries.stream().anyMatch(SpriteSatEntry::isGhost)) {
                // Mask admission already expanded immutable art fragments. Flatten
                // the actual bucket order before planning real/ghost coverage spans.
                var frontToBack = new ArrayList<SpriteSatEntry>(processedEntries.size());
                for (int bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++) {
                    for (SpriteSatEntry entry : processedEntries) {
                        if (entry.priorityBucket() == bucket && !entry.presentationSubject().suppressed())
                            frontToBack.add(entry);
                    }
                }
                replayNativeLayers(frontToBack, SpriteSatEntry::isGhost, this::replayOrderedSpriteSatEntries);
                return;
            }

			// The SAT replay must not re-enter renderPatternWithId(): that could merge the
			// carefully ordered SAT sequence into a still-open batch owned by another layer,
			// flattening or reordering it. Instead the replay owns its emission: a dedicated
			// instanced batch (flushed before any direct command so order is preserved, with
			// per-instance VDP priority carried in the instance data), or — when instanced
			// batching is unavailable — the original direct per-tile commands.
			withNativeSpriteOwnership(() -> {
			setCurrentSpriteHighPriority(false);
			if (canBatchSpriteSatReplay()) {
				replaySpriteSatEntriesBatched(processedEntries);
				return;
			}
			List<PatternRenderCommand> commands = buildSpriteSatReplayCommands(processedEntries);
			// `commands` is a reused buffer (`reusableReplayCommands`); copy via index iteration
			// without releasing the reference, then clear the buffer once registerCommand has
			// consumed each entry.
			for (int i = 0, n = commands.size(); i < n; i++) {
				registerCommand(commands.get(i));
			}
			reusableReplayCommands.clear();
            });
		} finally {
			spriteSatEntries.clear();
		}
	}

	private boolean canBatchSpriteSatReplay() {
		return batchingEnabled
				&& instancedBatchingEnabled
				&& instancedPatternRenderer != null
				&& instancedPatternRenderer.isSupported()
				&& !instancedBatchActive;
	}

    /** A single already ordered span; its dedicated batch cannot cross a stencil selection command. */
    private void replayOrderedSpriteSatEntries(List<SpriteSatEntry> entries) {
        ensurePatternAtlas();
        Integer paletteTextureId = resolveSpriteSatReplayPaletteTextureId();
        int palette = paletteTextureId == null ? -1 : paletteTextureId;
        setCurrentSpriteHighPriority(false);
        if (canBatchSpriteSatReplay()) {
            try {
                satReplayBatchOpen = false;
                for (SpriteSatEntry entry : entries) {
                    if (paletteTextureId == null && entry.pixelOperation() != SpriteSatEntry.PixelOperation.SHADOW_15) continue;
                    setCurrentSpriteTileOcclusionPaletteMask(entry.tileOcclusionPaletteMask());
                    appendBatchedReplayCommands(entry, palette);
                }
                flushSatReplayBatch();
            } finally {
                instancedPatternRenderer.cancelBatch();
                satReplayBatchOpen = false;
            }
            return;
        }
        reusableReplayCommands.clear();
        int registered = 0;
        try {
            for (SpriteSatEntry entry : entries) {
                if (paletteTextureId == null && entry.pixelOperation() != SpriteSatEntry.PixelOperation.SHADOW_15) continue;
                setCurrentSpriteTileOcclusionPaletteMask(entry.tileOcclusionPaletteMask());
                appendDirectReplayCommands(entry, palette, reusableReplayCommands);
            }
            for (PatternRenderCommand command : reusableReplayCommands) {
                registerCommand(command);
                registered++;
            }
        } finally {
            for (int index = registered; index < reusableReplayCommands.size(); index++)
                reusableReplayCommands.get(index).discard();
            reusableReplayCommands.clear();
        }
    }

	/**
	 * Replays the processed SAT entries through dedicated instanced batches instead
	 * of one PatternRenderCommand (3 glBufferData + 1 draw) per 8x8 tile. Tiles are
	 * emitted in exactly the same bucket-major order as the direct path; within an
	 * instanced draw, instances render in submission order, and any tile that cannot
	 * join the batch (overflow atlas, batch full) flushes the open batch first so
	 * the final draw order matches the direct path's command order.
	 */
	private void replaySpriteSatEntriesBatched(List<SpriteSatEntry> processedEntries) {
		ensurePatternAtlas();
		Integer paletteTextureId = resolveSpriteSatReplayPaletteTextureId();
		try {
			satReplayBatchOpen = false;
			int paletteTexId = paletteTextureId == null ? -1 : paletteTextureId;
			for (int bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++) {
				for (int i = 0; i < processedEntries.size(); i++) {
					SpriteSatEntry processedEntry = processedEntries.get(i);
					if (processedEntry.priorityBucket() == bucket && !processedEntry.presentationSubject().suppressed()
                            && (paletteTextureId != null || processedEntry.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15)) {
						setCurrentSpriteTileOcclusionPaletteMask(processedEntry.tileOcclusionPaletteMask());
						appendBatchedReplayCommands(processedEntry, paletteTexId);
					}
				}
			}
			flushSatReplayBatch();
		} finally {
			// An exception mid-replay must not strand an open instanced batch.
			instancedPatternRenderer.cancelBatch();
			satReplayBatchOpen = false;
		}
	}

	private void appendBatchedReplayCommands(SpriteSatEntry entry, int paletteTextureId) {
        withSpritePixelOperation(entry.pixelOperation(),
                () -> withSatEntryGhost(entry, () -> appendBatchedReplayTiles(entry, paletteTextureId)));
	}

	/**
	 * Replays {@code entry} under its gameplay-ghost style, if it has one. Every replay sink
	 * (presentation tiles, instanced batches, direct commands) captures the ghost state when a
	 * tile is added, and a batch never mixes states, so a ghost keeps its translucency and
	 * colour mode inside the SAT order.
	 */
	private void withSatEntryGhost(SpriteSatEntry entry, Runnable replay) {
		if (!entry.isGhost()) {
			replay.run();
			return;
		}
		boolean active = ghostRenderEffectActive;
		boolean colour = ghostRenderPreservesColour;
		float alpha = ghostRenderAlpha;
		beginGhostRenderEffect(entry.ghost().alpha(), entry.ghost().preserveColour());
		try {
			replay.run();
		} finally {
			ghostRenderEffectActive = active;
			ghostRenderPreservesColour = colour;
			ghostRenderAlpha = alpha;
		}
	}

	private void appendBatchedReplayTiles(SpriteSatEntry entry, int paletteTextureId) {
		entry.piece().forEachTile(
				(patternIndex, pieceHFlip, pieceVFlip, paletteIndex, drawX, drawY) -> {
					// CPU preparation uses this same tile decoder before any atlas lookup.
					// A sprite-mask band can hide single scanlines of this tile.
					int rowStart = entry.visibleTileRowStart(drawY);
					int rowEnd = entry.visibleTileRowEnd(drawY);
					if (rowStart >= rowEnd) {
						return;
					}
					if (spritePresentationBuilder != null) {
						prepareReplayDesc(entry, patternIndex, pieceHFlip, pieceVFlip, paletteIndex);
						if (rowStart == 0 && rowEnd == 8) {
							renderPatternWithId(patternIndex, reusableReplayDesc, drawX, drawY);
						} else {
							renderPatternRowsWithId(patternIndex, reusableReplayDesc, paletteIndex, pieceHFlip, pieceVFlip,
									entry.piece().effectiveHighPriority(), drawX, drawY, rowStart, rowEnd);
						}
						return;
					}
					PatternAtlas.Entry atlasEntry = patternAtlas != null ? patternAtlas.getEntry(patternIndex) : null;
					if (atlasEntry == null) {
						return;
					}
                    if(entry.presentationSubject().paletteReplay()!=null
                            || entry.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15) {
                        prepareReplayDesc(entry,patternIndex,pieceHFlip,pieceVFlip,paletteIndex);
                        flushSatReplayBatch();
                        registerCommand(obtainReplayCommand(entry,atlasEntry,paletteTextureId,paletteIndex,
                                pieceHFlip,pieceVFlip,drawX,drawY,rowStart,rowEnd));return;
                    }
					prepareReplayDesc(entry, patternIndex, pieceHFlip, pieceVFlip, paletteIndex);
					if (addToSatReplayBatch(entry, atlasEntry, paletteIndex, pieceHFlip, pieceVFlip, drawX, drawY,
							rowStart, rowEnd)) {
						return;
					}
					// Unsupported state: flush first so draw order is preserved, then draw direct.
					flushSatReplayBatch();
					registerCommand(obtainReplayCommand(entry, atlasEntry, paletteTextureId, paletteIndex,
							pieceHFlip, pieceVFlip, drawX, drawY, rowStart, rowEnd));
				});
	}

	private boolean addToSatReplayBatch(SpriteSatEntry satEntry, PatternAtlas.Entry atlasEntry, int paletteIndex,
			boolean pieceHFlip, boolean pieceVFlip, int drawX, int drawY, int rowStart, int rowEnd) {
		if (!satReplayBatchOpen) {
			instancedPatternRenderer.beginBatch(atlasEntry.atlasIndex());
			satReplayBatchOpen = true;
		}
		if (addSatReplayTile(satEntry, atlasEntry, paletteIndex, pieceHFlip, pieceVFlip, drawX, drawY, rowStart, rowEnd)) {
			return true;
		}
		// Batch full: flush and retry once in a fresh batch.
		flushSatReplayBatch();
		instancedPatternRenderer.beginBatch(atlasEntry.atlasIndex());
		satReplayBatchOpen = true;
		return addSatReplayTile(satEntry, atlasEntry, paletteIndex, pieceHFlip, pieceVFlip, drawX, drawY, rowStart, rowEnd);
	}

	private boolean addSatReplayTile(SpriteSatEntry satEntry, PatternAtlas.Entry atlasEntry, int paletteIndex,
			boolean pieceHFlip, boolean pieceVFlip, int drawX, int drawY, int rowStart, int rowEnd) {
		if (rowStart == 0 && rowEnd == 8) {
			return tryAddInstancedPattern(atlasEntry, paletteIndex, reusableReplayDesc, drawX, drawY);
		}
		return instancedPatternRenderer.addPatternRows(atlasEntry, paletteIndex, pieceHFlip, pieceVFlip,
				satEntry.piece().effectiveHighPriority(), drawX, drawY, rowStart, rowEnd);
	}

	private PatternRenderCommand obtainReplayCommand(SpriteSatEntry satEntry,PatternAtlas.Entry atlasEntry,
            int paletteTextureId,int paletteIndex,boolean pieceHFlip,boolean pieceVFlip,int drawX,int drawY,int rowStart,int rowEnd) {
        if (satEntry.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15) {
            return PatternRenderCommand.obtainNativeShadowRows(atlasEntry, paletteIndex, pieceHFlip, pieceVFlip,
                    satEntry.piece().effectiveHighPriority(), drawX, drawY, rowStart, rowEnd, this);
        }
        PatternRenderCommand command=rowStart==0 && rowEnd==8
                ?obtainPatternCommand(atlasEntry,paletteTextureId,reusableReplayDesc,drawX,drawY,8f,8f)
                :PatternRenderCommand.obtainRows(atlasEntry,paletteTextureId,paletteIndex,pieceHFlip,pieceVFlip,
                        satEntry.piece().effectiveHighPriority(),drawX,drawY,rowStart,rowEnd,this);
        command.paletteReplay(satEntry.presentationSubject().paletteReplay());
        return command;
    }

	private void flushSatReplayBatch() {
		if (!satReplayBatchOpen) {
			return;
		}
		GLCommandable batchCommand = instancedPatternRenderer.endBatch();
		if (batchCommand != null) {
			registerCommand(batchCommand);
		}
		satReplayBatchOpen = false;
	}

	List<PatternRenderCommand> buildSpriteSatReplayCommands(List<SpriteSatEntry> processedEntries) {
		// Reuse a single ArrayList across calls instead of allocating one per replay frame.
		// Callers must consume the contents before the next invocation; the only caller is
		// endSpriteSatCollectionAndReplay() which drains-then-clears in the same statement.
		reusableReplayCommands.clear();
		if (processedEntries == null || processedEntries.isEmpty()) {
			return reusableReplayCommands;
		}
		ensurePatternAtlas();
		Integer paletteTextureId = resolveSpriteSatReplayPaletteTextureId();
		for (int bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++) {
			for (int i = 0; i < processedEntries.size(); i++) {
				SpriteSatEntry processedEntry = processedEntries.get(i);
				if (processedEntry.priorityBucket() == bucket && !processedEntry.presentationSubject().suppressed()
                        && (paletteTextureId != null || processedEntry.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15)) {
					setCurrentSpriteTileOcclusionPaletteMask(processedEntry.tileOcclusionPaletteMask());
					appendDirectReplayCommands(processedEntry, paletteTextureId == null ? -1 : paletteTextureId, reusableReplayCommands);
				}
			}
		}
		return reusableReplayCommands;
	}

    Integer scopedReplayPaletteTexture(boolean underwater) {
        return underwater && paletteTextures.getUnderwaterPaletteTextureId()!=null?paletteTextures.getUnderwaterPaletteTextureId():paletteTextures.getCombinedPaletteTextureId();
    }

	private Integer resolveSpriteSatReplayPaletteTextureId() {
		if (useUnderwaterPaletteForBackground && paletteTextures.getUnderwaterPaletteTextureId() != null) {
			return paletteTextures.getUnderwaterPaletteTextureId();
		}
		if (paletteTextures.getCombinedPaletteTextureId() != null) {
			return paletteTextures.getCombinedPaletteTextureId();
		}
		return headlessMode ? -1 : null;
	}

	private void appendDirectReplayCommands(
			SpriteSatEntry entry,
			int paletteTextureId,
			List<PatternRenderCommand> replayCommands) {
		if (entry == null || replayCommands == null) {
			return;
		}
		withSatEntryGhost(entry, () -> appendDirectReplayTiles(entry, paletteTextureId, replayCommands));
	}

	private void appendDirectReplayTiles(SpriteSatEntry entry, int paletteTextureId,
			List<PatternRenderCommand> replayCommands) {
		entry.piece().forEachTile(
				(patternIndex, pieceHFlip, pieceVFlip, paletteIndex, drawX, drawY) -> {
					PatternAtlas.Entry atlasEntry = patternAtlas != null ? patternAtlas.getEntry(patternIndex) : null;
					if (atlasEntry == null) {
						return;
					}
					int rowStart = entry.visibleTileRowStart(drawY);
					int rowEnd = entry.visibleTileRowEnd(drawY);
					if (rowStart >= rowEnd) {
						return;
					}
					prepareReplayDesc(entry, patternIndex, pieceHFlip, pieceVFlip, paletteIndex);
					replayCommands.add(obtainReplayCommand(entry, atlasEntry, paletteTextureId, paletteIndex,
							pieceHFlip, pieceVFlip, drawX, drawY, rowStart, rowEnd));
				});
	}

	/**
	 * Loads the shared replay PatternDesc with the tile word for one replayed SAT
	 * tile. Reuses a single PatternDesc across the entire SAT replay: both
	 * obtainPatternCommand() and tryAddInstancedPattern() copy
	 * all needed fields out of the desc and do not retain a reference, so mutating
	 * it before the next tile is safe. PatternDesc.set() resets every derived
	 * field via updateFields().
	 */
	private void prepareReplayDesc(SpriteSatEntry entry, int patternIndex,
			boolean pieceHFlip, boolean pieceVFlip, int paletteIndex) {
		int descIndex = patternIndex & 0x7FF;
		if (entry.piece().effectiveHighPriority()) {
			descIndex |= 0x8000;
		}
		if (pieceHFlip) {
			descIndex |= 0x800;
		}
		if (pieceVFlip) {
			descIndex |= 0x1000;
		}
		descIndex |= (paletteIndex & 0x3) << 13;
		reusableReplayDesc.set(descIndex);
		reusableReplayDesc.setPaletteIndex(paletteIndex);
	}

	public WaterShaderProgram getWaterShaderProgram() {
		return waterShaderProgram;
	}

	public ShaderProgram getInstancedShaderProgram() {
		return instancedPatternRenderer != null ? instancedPatternRenderer.getInstancedShaderProgram() : null;
	}

	public WaterShaderProgram getInstancedWaterShaderProgram() {
		return instancedPatternRenderer != null ? instancedPatternRenderer.getInstancedWaterShaderProgram() : null;
	}

	public void setUseWaterShader(boolean use) {
		if (use) {
			currentShaderProgram = waterShaderProgram;
		} else {
			currentShaderProgram = defaultShaderProgram;
		}
		this.shaderProgram = currentShaderProgram;
	}

	/**
	 * Sets whether to use the underwater palette for background rendering.
	 * When true, all patterns rendered will use the underwater palette instead of
	 * the normal palette.
	 * This mirrors the original game's behavior where the entire background changes
	 * palette
	 * when Sonic is underwater.
	 */
	public void setUseUnderwaterPaletteForBackground(boolean use) {
		this.useUnderwaterPaletteForBackground = use;
	}

	/**
	 * Returns whether the underwater palette should be used for background
	 * rendering.
	 */
	public boolean isUseUnderwaterPaletteForBackground() {
		return useUnderwaterPaletteForBackground;
	}

	public ShaderProgram getDebugShaderProgram() {
		return debugShaderProgram;
	}

	public ShaderProgram getFadeShaderProgram() {
		return fadeShaderProgram;
	}

	public TilemapGpuRenderer getTilemapGpuRenderer() {
		return tilemapGpuRenderer;
	}

	public void applyResolvedDisplayWidth(int pixelWidth) {
		if (tilemapGpuRenderer == null) {
			return;
		}
		tilemapGpuRenderer.applyResolvedDisplayWidth(pixelWidth);
	}

	public ShaderProgram getShadowShaderProgram() {
		return shadowShaderProgram;
	}

	/**
	 * Get the fade manager for screen transitions.
	 */
	public FadeManager getFadeManager() {
		ensureRuntimeManagedReferences();
		return fadeManager;
	}

	/**
	 * Check if GL is initialized.
	 */
	public boolean isGlInitialized() {
		return glInitialized;
	}

	/**
	 * Get the background renderer for shader-based parallax scrolling.
	 * Initializes it lazily on first access.
	 */
	public BackgroundRenderer getBackgroundRenderer() {
		if (headlessMode) {
			return null;
		}
		if (backgroundRenderer == null && glInitialized) {
			try {
				backgroundRenderer = new BackgroundRenderer(this);
				backgroundRenderer.init(PARALLAX_SHADER_PATH);
				LOGGER.info("BackgroundRenderer initialized for shader-based parallax.");
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "Failed to initialize BackgroundRenderer", e);
			}
		}
		return backgroundRenderer;
	}

	/**
	 * Enqueue OpenGL state for debug line rendering.
	 * Note: Deprecated fixed-function calls (GL_TEXTURE_2D, GL_LIGHTING, GL_COLOR_MATERIAL)
	 * have been removed for OpenGL 4.1 core profile compatibility.
	 */
	public void enqueueDebugLineState() {
		if (spritePresentationBuilder != null) return;
		ShaderProgram debugShader = getDebugShaderProgram();
		int programId = debugShader != null ? debugShader.getProgramId() : 0;
		registerCommand(new GLCommand(GLCommand.CommandType.USE_PROGRAM, programId));
		registerCommand(new GLCommand(GLCommand.CommandType.DISABLE, GL_DEPTH_TEST));
	}

	/**
	 * Enqueue OpenGL state for default shader rendering.
	 * Note: glEnable(GL_TEXTURE_2D) has been removed for OpenGL 4.1 core profile compatibility.
	 * Texturing is now controlled entirely through shaders.
	 */
	public void enqueueDefaultShaderState() {
		if (spritePresentationBuilder != null) return;
		ShaderProgram shader = getShaderProgram();
		if (shader != null) {
			int programId = shader.getProgramId();
			if (programId != 0) {
				registerCommand(new GLCommand(GLCommand.CommandType.USE_PROGRAM, programId));
			}
		}
	}

	/**
	 * Enables scissor test with the specified rectangle.
	 * Coordinates are in OpenGL screen space (Y=0 at bottom).
	 *
	 * @param x      Left edge of scissor rectangle
	 * @param y      Bottom edge of scissor rectangle
	 * @param width  Width of scissor rectangle
	 * @param height Height of scissor rectangle
	 */
	public void enableScissor(int x, int y, int width, int height) {
		if (headlessMode || !glInitialized)
			return;
		glScissor(x, y, width, height);
		glEnable(GL_SCISSOR_TEST);
	}

	/**
	 * Disables scissor test.
	 */
	public void disableScissor() {
		if (headlessMode || !glInitialized)
			return;
		glDisable(GL_SCISSOR_TEST);
	}

	/**
	 * Get the unified UI render pipeline for overlay + fade ordering.
	 */
	public UiRenderPipeline getUiRenderPipeline() {
		ensureRuntimeManagedReferences();
		return uiRenderPipeline;
	}

	public DisplayShaderPipeline getDisplayShaderPipeline() {
		return displayShaderPipeline;
	}

	// ==================== Sprite Priority Rendering ====================

	/**
	 * Get the sprite priority shader program for ROM-accurate sprite-to-tile layering.
	 * This shader composites sprites with awareness of high-priority foreground tiles.
	 */
	public SpritePriorityShaderProgram getSpritePriorityShaderProgram() {
		return spritePriorityShaderProgram;
	}

	/**
	 * Get the tile priority FBO for rendering high-priority tile information.
	 * Lazily initializes the FBO with specified dimensions if not already done.
	 *
	 * @param width  Screen width in pixels
	 * @param height Screen height in pixels
	 */
	public TilePriorityFBO getTilePriorityFBO(int width, int height) {
		if (headlessMode || !glInitialized) {
			return null;
		}
		if (tilePriorityFBO != null && !tilePriorityFBO.isInitialized()) {
			tilePriorityFBO.init(width, height);
		} else if (tilePriorityFBO != null) {
			tilePriorityFBO.resize(width, height);
		}
		return tilePriorityFBO;
	}

	/**
	 * Get the tile priority FBO without initializing or resizing.
	 * Returns null if not yet initialized.
	 */
	public TilePriorityFBO getTilePriorityFBO() {
		return tilePriorityFBO;
	}

	/**
	 * Begin rendering high-priority tiles to the tile priority FBO.
	 * Call this before rendering the high-priority foreground tile pass.
	 *
	 * @param width  Screen width in pixels
	 * @param height Screen height in pixels
	 */
	public void beginTilePriorityPass(int width, int height) {
		if (headlessMode || !glInitialized) {
			return;
		}
		TilePriorityFBO fbo = getTilePriorityFBO(width, height);
		if (fbo != null) {
			fbo.begin();
		}
	}

	/**
	 * End rendering high-priority tiles to the tile priority FBO.
	 * Call this after rendering the high-priority foreground tile pass.
	 */
	public void endTilePriorityPass() {
		if (headlessMode || !glInitialized || tilePriorityFBO == null) {
			return;
		}
		tilePriorityFBO.end();
	}

	private record PendingRenderThreadTask<T>(Callable<T> callable, CompletableFuture<T> future) {
		void run() {
			try {
				future.complete(callable.call());
			} catch (Throwable t) {
				future.completeExceptionally(t);
			}
		}

		void cancel() {
			future.cancel(false);
		}
	}
}
