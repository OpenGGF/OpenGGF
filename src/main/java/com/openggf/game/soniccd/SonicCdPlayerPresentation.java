package com.openggf.game.soniccd;

import com.openggf.camera.Camera;
import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.GameplayInputFilter;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.internal.ControlledVBlankOnlyOwner;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.player.CdPlayableSonic;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.level.scroll.CameraDrivenScrollHandler;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;

import java.util.Objects;

/**
 * Controlled-frame/camera adapters for the separately named private player entries.
 * Opening objects share one native pass and immutable picture; terrain-only retains
 * its existing player draw. Both compose native BG arithmetic through the development
 * planes, with no native BG section streaming/residency or pixel-parity claim, no HUD/native music and
 * no retail-route claim. Mixed plane/sprite priority remains a known presentation gap.
 * Source service is provided by the existing PLC boundary, never by draw/callback.
 */
final class SonicCdPlayerPresentation implements GameplayFrameController, ControlledVBlankOnlyOwner,
        ScrollHandlerProvider {
    private final SonicCdGameModule module;
    private final Scroll scroll = new Scroll();
    private CdR11aAssets assets;
    private CdPlayerFrameRuntime frame;
    private CdPlayableSonic actor;
    private CdOpeningObjectRuntime openingObjects;
    // This module exclusively reserves OBJECTS. Normal terrain reset replaces the
    // atlas; retained-level reset keeps this reservation and removes just our 400
    // picture patterns. Never clear other producers' ranges or infer ownership
    // from a range with matching coordinates. Rewind cannot move to another atlas.
    private PatternAtlas openingAtlas;
    private static final int OPENING_PICTURE_PATTERNS = 24 + 14 + 70 + 28 + 63 + 70 + 5 + 39 + 36 + 15 + 36;
    private CdR11aForegroundScroll.State scrollState;
    private CdR11aBackgroundScroll background;
    private CdR11aBackgroundScroll.State backgroundState; // The visit's ONE retained native BG image.
    private CdR11aBackgroundPlane backgroundPlane; // One immutable source binding, not another BG arithmetic owner.
    private CdR11aBackgroundPlane.State planeState; // Authoritative 4096 native bytes, advanced only by source draw service.
    private DrawCopies drawCopies;
    private CdR11aBackgroundPlane.State publishedPlane; // Selected immutable display value; no renderer upload in this unit.
    private boolean loadScrollPending;
    private Object scrollCaptureOwner = new Object();
    // CPU scroll prepared by the completed loop; V-int selects the previous one
    // alongside the Runtime's previous CPU SAT. These are immutable render inputs,
    // distinct from the native FG/BG accumulator pair owned by Scroll below.
    private LevelSpritePresentation.ScrollFrame preparedScroll;
    private LevelSpritePresentation.ScrollFrame publishedScroll;
    record ScrollPublication(Object owner, LevelSpritePresentation.ScrollFrame prepared,
                             LevelSpritePresentation.ScrollFrame published, CdR11aBackgroundPlane.State plane) {
        ScrollPublication { Objects.requireNonNull(owner); Objects.requireNonNull(plane); }
    }

    // VInt_Level's MOVEM image: all 32 camera bytes and eight flag bytes, never an arithmetic accumulator.
    record DrawCopies(int fgX16_16, int fgY16_16, CdR11aBackgroundPlane.CopiedCameras bg,
                      int fgFlags, CdR11aBackgroundPlane.FlagWords bgFlags) {
        DrawCopies {
            Objects.requireNonNull(bg); Objects.requireNonNull(bgFlags);
            if ((fgFlags & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned FG flag word");
        }
    }
    private PlcLifecyclePhase heldPhase;

    SonicCdPlayerPresentation(SonicCdGameModule module) { this.module = Objects.requireNonNull(module); }

    void bind(CdR11aAssets assets, SonicCdGameModule.BackgroundBuffers buffers) {
        if (frame != null && !frame.closed()) throw new IllegalStateException("Retire the previous CD frame owner first");
        Objects.requireNonNull(assets);
        Objects.requireNonNull(buffers, "explicit background buffer ingress");
        var nextForeground = CdR11aForegroundScroll.coldFromLoadPlan(assets.loadPlan(), 320, 224);
        var nextBackgroundOwner = CdR11aBackgroundScroll.bind(assets);
        var nextPlaneOwner = CdR11aBackgroundPlane.bind(assets);
        // ClearCamera (Main:210-216) clears all six BG longs, crossings/padding and flags.
        // LevelSizeLoad:31 writes $1010 ONLY to the adjacent FG pair, not these BG bytes.
        // ClearFlowers (Main:178-184) covers FF1600..FF1FFF; scrlSectSpeeds is FF1E00/512
        // (_Include/Main CPU Variables.inc:148,190-191). All cloud RAM is therefore zero,
        // before InitLevelScroll's redundant first-19 clear. ClearScreen:209 clears VScroll.
        var cleared = new CdR11aBackgroundScroll.State(0, 0, 0, 0, 0, 0,
                CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.CROSSING_RAM_BYTES]), 0, 0, 0,
                CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.CLOUD_RAM_BYTES]),
                buffers.deformationRam(), buffers.hscrollRam(), 0);
        // DefaultStart MOVEQ #0 + SetupCamera's WORD operations leave upper d0/d1 zero
        // (PP Scroll:76-109). Supply integer words, NEVER cameraY16_16's register bits.
        var nextBackground = CdR11aBackgroundScroll.init(cleared, new CdR11aBackgroundScroll.InitRegisters(
                nextForeground.cameraYWord(), nextForeground.cameraX(), assets.loadPlan().initialCentre().centreX()));
        // Main:225 -> ClearScreen (Functions (General).asm:196-207) clears Plane B. Initial
        // drawing at the actual $70B camera leaves 560 bytes untouched, so it cannot replace this seed.
        var clearedPlane = new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(new byte[CdR11aBackgroundPlane.NAME_TABLE_BYTES]));
        // Main.ClearFlowers:178-184 clears the distinct camera/flag copies. Never carry them across visits.
        var zeroCamera = new CdR11aBackgroundPlane.Camera(0, 0);
        var clearedCopies = new DrawCopies(0, 0,
                new CdR11aBackgroundPlane.CopiedCameras(zeroCamera, zeroCamera, zeroCamera),
                0, new CdR11aBackgroundPlane.FlagWords(0, 0, 0));
        // All pure bindings/shapes/initial camera values are complete before replacing any visit field.
        this.assets = assets;
        scrollState = nextForeground;
        background = nextBackgroundOwner;
        backgroundState = nextBackground;
        backgroundPlane = nextPlaneOwner;
        planeState = clearedPlane;
        drawCopies = clearedCopies;
        publishedPlane = null; // No completed initial draw yet; loadScrollPending prevents admission.
        loadScrollPending = true;
        scroll.resetProjection();
        scrollCaptureOwner = new Object();
        preparedScroll = null;
        publishedScroll = null;
        heldPhase = null;
    }

    /** Fresh zero deformation is DEV policy; zero HScroll follows native ClearScreen:220-226. */
    static SonicCdGameModule.BackgroundBuffers developmentZeroBackgroundResidue() {
        return new SonicCdGameModule.BackgroundBuffers(
                CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.DEFORMATION_RAM_BYTES]),
                CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.HSCROLL_RAM_BYTES]));
    }

    /**
     * Main:245-246 load-time call, once before ObjSonic_Init; not a gameplay loop or V-int.
     * Earlier renderer initialization may project Init RAM, but never consumes this call.
     * Main:247's live FG forced flag and :249's initial BG draw follow the completed arithmetic.
     * Native selector-C PLC waits and fade loops remain omitted by the existing DEV load; do not
     * manufacture controller samples, clocks or a V-int here. This is post-InitLevelDraw only.
     * No actor, object, input, animation, art or level-frame service is executed here.
     */
    void completeLoadScroll(CdPlayerFrameRuntime sourceFrame) {
        Objects.requireNonNull(sourceFrame, "pre-Init frame");
        if (!loadScrollPending || sourceFrame.assets() != assets || frame != null) {
            throw new IllegalStateException("CD load scroll requires its fresh source visit and runs exactly once");
        }
        var m = sourceFrame.state().motion();
        if (m.routine() != 0 || m.flags() != 0 || m.groundVelocity() != 0 || m.xVelocity() != 0
                || m.yVelocity() != 0 || m.playerControl() != 0 || m.animation() != 0) {
            throw new IllegalStateException("Load-time scroll requires the cleared player slot before ObjSonic_Init");
        }
        var next = CdR11aForegroundScroll.stepForBackground(scrollState, foregroundInput(sourceFrame, 0));
        var nextBackground = background.step(backgroundState, backgroundInput(next.state(), m));
        // BSET #2,scrollFlags (Main:247) addresses the HIGH byte. This is live FG only;
        // Plane B initialization consumes neither live flags nor the still-cleared draw copies.
        var f = next.state();
        var nextForeground = new CdR11aForegroundScroll.State(f.cameraX(), f.cameraY16_16(),
                f.horizontalCrossed(), f.verticalCrossed(), f.scrollXDiff(), f.scrollYDiff(), f.scrollFlags() | 0x0400,
                f.destBottomBound(), f.destLeftBound(), f.bottomBoundShift(), f.scrollLock());
        var initialized = backgroundPlane.initialize(planeState, liveCameras(nextBackground.state()), liveFlags(nextBackground.state()));
        // Stage the whole pure tuple before the first source/retained write. Failure closes load admission.
        sourceFrame.applyScrollFeedback(next.bounds(), next.cameraYCentre());
        scrollState = nextForeground;
        backgroundState = nextBackground.state();
        planeState = initialized.state();
        publishedPlane = initialized.state();
        loadScrollPending = false;
    }

    SonicCdGameModule.BackgroundBuffers captureBackgroundBuffers() {
        requireBound(); frame.captureShared(); // Closed source cut, pure; never retains a second runtime State.
        return new SonicCdGameModule.BackgroundBuffers(backgroundState.deformationRam(), backgroundState.hscrollRam());
    }

    private CdR11aForegroundScroll.Input foregroundInput(CdPlayerFrameRuntime sourceFrame, int sourceHudTime) {
        var m = sourceFrame.state().motion(); var shared = sourceFrame.environment().movement();
        return new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(m.centreX16_16() >>> 16,
                m.centreY16_16() >>> 16, m.flags(), m.groundVelocity(), m.animation(), m.playerControl()),
                shared.bounds(), assets.loadPlan().bounds().top(), sourceFrame.environment().globals().cameraXCentre(),
                shared.globals().cameraYCentre(), assets.profile().variant(), sourceHudTime, 320, 224);
    }

    private static CdR11aBackgroundScroll.Input backgroundInput(CdR11aForegroundScroll.State completed,
                                                                CdPlayerMotionState player) {
        return new CdR11aBackgroundScroll.Input(completed.cameraX(), completed.cameraYWord(),
                (short) completed.scrollXDiff(), player.xVelocity(), player.centreX16_16() >>> 16,
                player.playerControl(), completed.scrollLock() >>> 8);
    }

    private static CdR11aBackgroundPlane.LiveCameras liveCameras(CdR11aBackgroundScroll.State b) {
        return new CdR11aBackgroundPlane.LiveCameras(new CdR11aBackgroundPlane.Camera(b.cameraBgX(), b.cameraBgY()),
                new CdR11aBackgroundPlane.Camera(b.cameraBg2X(), b.cameraBg2Y()),
                new CdR11aBackgroundPlane.Camera(b.cameraBg3X(), b.cameraBg3Y()));
    }

    private static CdR11aBackgroundPlane.FlagWords liveFlags(CdR11aBackgroundScroll.State b) {
        return new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(), b.scrollFlagsBg2(), b.scrollFlagsBg3());
    }

    /** Pure admission before the Frame consumes input/art; lag admits no copy or draw service. */
    void validatePlaneVBlank(PlcLifecyclePhase phase) {
        Objects.requireNonNull(phase, "source V-int phase");
        requireBound();
        if (phase != PlcLifecyclePhase.ORDINARY_LEVEL && phase != PlcLifecyclePhase.NORMAL_PAUSE
                && phase != PlcLifecyclePhase.LAG) {
            throw new UnsupportedOperationException("Unsupported CD plane service phase: " + phase);
        }
        if (openingObjects != null) {
            validateScrollPublication(new ScrollPublication(scrollCaptureOwner, preparedScroll, publishedScroll, publishedPlane));
            if (phase != PlcLifecyclePhase.LAG && preparedScroll == null) {
                throw new IllegalStateException("Selector8 requires the first-input scroll admission");
            }
        }
    }

    /**
     * Exact selector8 source cut, after Sonic/powerup art and before VInt_Finish. Main:840-844
     * replaces every camera/flag copy bit-for-bit on EVERY call, including pause/death/lock.
     * Read retained fields directly: Frame.captureShared is forbidden inside its service operation.
     */
    void servicePlaneAtVInt() {
        requireBound();
        var live = liveCameras(backgroundState);
        var copied = new DrawCopies(scrollState.cameraX() << 16, scrollState.cameraY16_16(),
                new CdR11aBackgroundPlane.CopiedCameras(live.bg1(), live.bg2(), live.bg3()),
                scrollState.scrollFlags(), liveFlags(backgroundState));
        var result = backgroundPlane.incremental(planeState, live, copied.bg(), copied.bgFlags());
        var consumed = new DrawCopies(copied.fgX16_16(), copied.fgY16_16(), copied.bg(), copied.fgFlags(), result.flags());
        // Complete result and copies exist before either write. Live flags are never consumed here.
        // Native FG streaming/FG copy consumption is outside this bounded Plane B composition.
        planeState = result.state();
        drawCopies = consumed;
    }

    /** Read-only selected immutable image for a future renderer; no descriptor/cache/GPU operation. */
    CdR11aBackgroundPlane.State publishedPlane() { requireBound(); return publishedPlane; }

    void publishTerrainPlane(PlcLifecyclePhase phase) {
        requireBound();
        if (openingObjects != null) throw new IllegalStateException("Opening publication owns its scroll/SAT tuple");
        if (phase != PlcLifecyclePhase.LAG) publishedPlane = planeState;
    }

    CdOpeningObjectRuntime.LogicalBanks reserveOpeningPictureBank() {
        if (!module.openingObjectsAdmission()) throw new IllegalStateException("Not the opening picture owner");
        var atlas = Objects.requireNonNull(GameServices.graphics().getPatternAtlas(), "loaded terrain atlas");
        if (atlas != openingAtlas) {
            atlas.registerRange(PatternAtlasRange.OBJECTS); // Reject ANY competing OBJECTS producer.
            openingAtlas = atlas;
        }
        int base = PatternAtlasRange.OBJECTS.base();
        return new CdOpeningObjectRuntime.LogicalBanks(base, base + 24, base + 38);
    }

    private void requireOpeningAtlas() {
        if (openingAtlas == null || GameServices.graphics().getPatternAtlas() != openingAtlas) {
            throw new IllegalStateException("CD opening picture cannot rebind across an atlas reset; reload its world");
        }
    }

    void bindFrame(CdPlayerFrameRuntime frame, CdPlayableSonic actor, CdOpeningObjectRuntime openingObjects) {
        Objects.requireNonNull(frame); Objects.requireNonNull(actor);
        if (frame.assets() != assets || actor.nativeState() != frame.state()
                || module.openingObjectsAdmission() != (openingObjects != null)) {
            throw new IllegalArgumentException("CD actor/frame/scroll must bind the same source owner");
        }
        var camera = GameServices.camera();
        requireViewport(camera);
        this.frame = frame;
        this.actor = actor;
        this.openingObjects = openingObjects;
        requireBound();
        camera.setFocusedSprite(actor); // This resets camera words; project only AFTER this call.
        camera.setFrozen(true); // Only CameraDrivenScrollHandler advances the native foreground.
        camera.setLevelStarted(true);
        projectCamera(camera);
        // Output projection only: match stored HScroll/VScroll to this completed camera before capture.
        GameServices.parallax().update(0, 0, camera, GameServices.level().getFrameCounter());
    }

    void unbind() {
        try {
            if (frame != null) frame.clearPendingInput();
            if (openingAtlas != null && GameServices.graphics().getPatternAtlas() == openingAtlas) {
                for (int i = 0; i < OPENING_PICTURE_PATTERNS; i++) {
                    openingAtlas.removeEntry(PatternAtlasRange.OBJECTS.base() + i);
                }
            }
        } finally {
            frame = null; actor = null; openingObjects = null; assets = null; scrollState = null; heldPhase = null;
            background = null; backgroundState = null; loadScrollPending = false;
            backgroundPlane = null; planeState = null; drawCopies = null; publishedPlane = null;
            preparedScroll = null;
            publishedScroll = null;
            scroll.resetProjection();
        }
    }

    void afterRestore() {
        try {
            requireBound();
            actor.requireCompletedNativeFrame();
            if (GameServices.camera().getFocusedSprite() != actor) {
                throw new IllegalStateException("CD restore requires its retained main actor");
            }
            if (openingObjects != null && openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
                throw new IllegalStateException("CD restore projection requires a completed opening cut");
            }
            projectCamera(GameServices.camera());
            GameServices.parallax().update(0, 0, GameServices.camera(), GameServices.level().getFrameCounter());
            actor.rebindAfterRestore(); // Full24 residency, no input/body/animation/cue.
            // The restored immutable opening picture uploads its own frozen pixels
            // on the next normal draw; it is never reconverted from the current CPU SAT.
            heldPhase = null;
        } catch (RuntimeException | Error failure) {
            module.closePlayerCycleAfterFailure(failure);
            throw failure;
        }
    }

    ScrollPublication captureScrollPublication() {
        requireBound();
        frame.captureShared(); // Closed controller/body boundary, no state change.
        if (openingObjects == null || openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
            throw new IllegalStateException("Opening scroll capture requires its completed native loop");
        }
        return new ScrollPublication(scrollCaptureOwner, preparedScroll, publishedScroll, publishedPlane);
    }

    void validateScrollPublication(ScrollPublication snapshot) {
        requireBound();
        Objects.requireNonNull(snapshot, "opening scroll publication");
        if (snapshot.owner() != scrollCaptureOwner || (snapshot.prepared() == null) != (snapshot.published() == null)) {
            throw new IllegalArgumentException("Opening scroll capture belongs to another visit or an incomplete pair");
        }
        // State/DrawCopies constructors enforce complete immutable shapes; publication adds the
        // selected image, including the cold null/null ScrollFrame cut, without creating a service.
        Objects.requireNonNull(snapshot.plane(), "selected immutable Plane B");
        if (snapshot.prepared() != null) {
            var level = GameServices.level();
            LevelSpritePresentation.validateScroll(level, snapshot.prepared());
            LevelSpritePresentation.validateScroll(level, snapshot.published());
        }
    }

    void restoreScrollPublication(ScrollPublication snapshot) {
        validateScrollPublication(snapshot);
        preparedScroll = snapshot.prepared();
        publishedScroll = snapshot.published();
        publishedPlane = snapshot.plane();
    }

    // Called AFTER the one Runtime V-int service, never by rendering or diagnostics.
    // Do not capture here: this boundary publishes the previous completed loop.
    void publishOpeningScroll(PlcLifecyclePhase phase) {
        requireBound();
        if (openingObjects == null) throw new IllegalStateException("No opening scroll publication owner");
        if (phase == PlcLifecyclePhase.LAG) return; // Native PP lag retains scroll and SAT.
        if (phase != PlcLifecyclePhase.ORDINARY_LEVEL && phase != PlcLifecyclePhase.NORMAL_PAUSE) {
            throw new UnsupportedOperationException("Unsupported opening scroll service phase: " + phase);
        }
        if (preparedScroll == null || publishedScroll == null) {
            throw new IllegalStateException("Ordinary/pause publication requires its admitted cold or completed scroll");
        }
        var nextScroll = preparedScroll;
        var nextPlane = Objects.requireNonNull(planeState, "completed source Plane B");
        LevelSpritePresentation.validateScroll(GameServices.level(), nextScroll);
        publishedScroll = nextScroll;
        publishedPlane = nextPlane; // Same completed V-int as Runtime.displayedFrame; CPU may now prepare N+1.
    }

    private void initializeOpeningScrollAfterLoad() {
        if ((preparedScroll == null) != (publishedScroll == null)) {
            throw new IllegalStateException("Incomplete opening scroll pair");
        }
        if (preparedScroll == null) {
            // Profile installation precedes completedProductionLoadGeneration.
            // Capture only at first input, after load has returned. Restoring the
            // explicit cold null cut repeats this admission without advancing it.
            var initial = LevelSpritePresentation.captureScroll(GameServices.level());
            var initialPlane = Objects.requireNonNull(publishedPlane, "completed initial Plane B");
            preparedScroll = initial;
            publishedScroll = initial;
            publishedPlane = initialPlane;
        } else {
            LevelSpritePresentation.validateScroll(GameServices.level(), preparedScroll);
            LevelSpritePresentation.validateScroll(GameServices.level(), publishedScroll);
        }
    }

    private void requireBound() {
        module.validateTerrainPlayerRequest();
        if (frame == null || actor == null || assets == null || scrollState == null || background == null
                || backgroundState == null || backgroundPlane == null || planeState == null || drawCopies == null
                || publishedPlane == null || loadScrollPending || frame.closed()
                || (module.openingObjectsAdmission() && (openingObjects == null || openingObjects.closed()))) {
            throw new IllegalStateException("CD terrain-player admission is incomplete or closed");
        }
        if (module.requireRuntimeSource().assets() != assets
                || !(GameServices.level().getCurrentLevel() instanceof SonicCdLevel level)
                || level.assets() != assets) {
            throw new IllegalArgumentException("CD player no longer owns its authenticated stage");
        }
        var sprites = GameServices.sprites();
        if (sprites.getMainPlayable() != actor || sprites.getAllSprites().size() != 1
                || !sprites.getSidekicks().isEmpty()) {
            throw new UnsupportedOperationException("CD terrain entry requires its one retained Sonic");
        }
        var world = GameServices.level().getWorldSession();
        if (world.getGameModule() != module || WorldSessionPolicyAccess.hasPolicies(world)
                || GameplayInputFilterAccess.currentSessionFilter() != GameplayInputFilter.IDENTITY
                || SpriteManagerInternalAccess.motionMutators(actor) != null) {
            throw new UnsupportedOperationException("CD terrain input/slot admission does not support policy decorators");
        }
        requireViewport(GameServices.camera());
        if (openingObjects != null) requireOpeningAtlas();
    }

    private static void requireViewport(Camera camera) {
        if (camera.getWidth() != 320 || camera.getHeight() != 224) {
            throw new UnsupportedOperationException("Native CD foreground currently requires a 320x224 viewport");
        }
    }

    @Override public boolean requiresExclusiveControl() { return true; }
    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
        throw new IllegalStateException("CD uses the complete controlled-frame disposition");
    }
    @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
        // The previous token/afterTick/old-epoch recording have finished. Preflight and load occur before
        // this row admits a native owner or samples its input; preflight failure closes only our visit.
        if (openingObjects != null && openingObjects.levelGlobals().levelRestart() != 0) module.restartOpeningWorld();
        requireBound();
        actor.requireCompletedNativeFrame();
        if (input != null && input.debugModeTogglePressed()) {
            throw new UnsupportedOperationException("CD terrain entry does not support host debug movement");
        }
        int held = nativeHeldByte(input, SpriteManagerInternalAccess.inputSuppressed(GameServices.sprites()));
        if (openingObjects != null) {
            // Gate before the runtime's deliberately terminal mid-cycle predicates.
            if (openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
                var failure = new IllegalStateException("Opening input requires the completed previous loop");
                module.closePlayerCycleAfterFailure(failure);
                throw failure;
            }
            try { initializeOpeningScrollAfterLoad(); }
            catch (RuntimeException | Error failure) {
                module.closePlayerCycleAfterFailure(failure);
                throw failure;
            }
            var camera = GameServices.camera();
            openingObjects.setPreScrollForeground(new CdObjectDrawQueue.CameraWords(
                    camera.getX() & 0xFFFF, camera.getY() & 0xFFFF));
        }
        var selection = frame.prepareInput(held);
        heldPhase = selection.nextPaused() ? selection.phase() : null;
        return selection.nextPaused() ? GameplayFrameDisposition.HOLD : GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
    }

    static int nativeHeldByte(LogicalInputSnapshot input, boolean suppressed) {
        if (suppressed || input == null) return 0;
        var p1 = Objects.requireNonNull(input.player1());
        return (p1.heldMask() & 15) | InputActionMasks.toMegaDriveButtonBits(p1.actionHeldMask())
                | (p1.startHeld() ? 0x80 : 0);
    }

    @Override public PlcLifecyclePhase heldVBlankPhase() { return heldPhase; }
    @Override public void afterTick(CourseControl course, boolean advanced) {
        try {
            requireBound();
            actor.requireCompletedNativeFrame();
            if (advanced == frame.paused() || (advanced && frame.environment().globals().levelFrames()
                    != (GameServices.level().getFrameCounter() & 0xFFFF))) {
                throw new IllegalStateException("CD represented frame did not complete its selected native body/hold");
            }
            if (openingObjects != null) {
                if (advanced) {
                    if (openingObjects.levelGlobals().levelRestart() != 0) {
                        if (openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
                            throw new IllegalStateException("CD restart branch did not settle its old native loop");
                        }
                        // Level_MainLoop:407-408 branches before scroll/SAT and UpdateGlobalAnims.
                        // Keep the last completed prepared scroll; beforeFrame reconstructs next row.
                    } else {
                        openingObjects.finishOrdinaryTail(); // Exactly once, after camera/SAT build.
                        preparedScroll = LevelSpritePresentation.captureScroll(GameServices.level());
                    }
                } else if (openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
                    throw new IllegalStateException("Held opening service did not retain its completed cut");
                }
                // Last-loop sound ledger is retained across holds; never replay it here.
            }
            frame.clearPendingInput();
            heldPhase = null;
        } catch (RuntimeException | Error failure) {
            module.closePlayerCycleAfterFailure(failure);
            throw failure;
        }
    }

    @Override public boolean drawScene() { return true; }
    @Override public boolean presentationPaused() { return frame != null && frame.paused(); }
    @Override public void drawOverlay() {
        try { drawCompletedOverlay(); }
        catch (RuntimeException | Error failure) {
            if (module.openingObjectsAdmission()) module.closePlayerCycleAfterFailure(failure);
            throw failure;
        }
    }

    private void drawCompletedOverlay() {
        requireBound();
        actor.requireCompletedNativeFrame();
        if (openingObjects != null && openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
            throw new IllegalStateException("Opening draw requires its completed loop, never a partial SAT");
        }
        var level = GameServices.level();
        if (openingObjects == null) {
            GameServices.parallax().update(0, 0, GameServices.camera(), level.getFrameCounter());
            level.drawWithRenderOptions(GameServices.sprites(),
                    new LevelManager.LevelRenderOptions(true, false, false, false, false, false, false));
        } else {
            Runnable terrain = () -> level.drawWithRenderOptions(GameServices.sprites(),
                    LevelManager.LevelRenderOptions.tilesOnly());
            // Before first V-int the explicit empty CPU SAT has no published
            // render cut. Cold draw remains live but never creates one by drawing.
            if (publishedScroll == null) terrain.run();
            else LevelSpritePresentation.withScroll(level, publishedScroll, terrain);
        }
        if (openingObjects != null) {
            var camera = GameServices.camera();
            // Screen-relative immutable SAT picture; renderer adds camera back for
            // the world-coordinate graphics API. Preserve native SAT order. Plane
            // priority composition remains bounded, not full VDP parity.
            SpritePresentationRenderer.draw(GameServices.graphics(), openingObjects.displayedFrame(),
                    camera.getX(), camera.getY(), layer -> true);
        }
        GameServices.graphics().flush();
    }

    @Override public void close() { module.closeTerrainPlayer(); }
    @Override public void load(Rom rom) {
        if (rom != null) throw new UnsupportedOperationException("CD scroll is source-bound, not cartridge-backed");
    }
    @Override public ZoneScrollHandler getHandler(int zoneIndex) {
        if (zoneIndex != 0) throw new UnsupportedOperationException("Only R11A foreground is admitted");
        return scroll;
    }
    @Override public ZoneConstants getZoneConstants() {
        return new ZoneConstants() {
            @Override public int getZoneCount() { return 1; }
            @Override public String getZoneName(int index) { return module.getZoneRegistry().getZoneName(index); }
        };
    }
    @Override public void resetZoneState() { scroll.resetProjection(); }

    private void projectCamera(Camera camera) {
        var bounds = frame.environment().movement().bounds();
        camera.setMinX((short) bounds.left()); camera.setMaxX((short) bounds.right());
        camera.setMinY((short) assets.loadPlan().bounds().top()); camera.setMaxY((short) bounds.bottom());
        camera.setVerticalWrapEnabled(false);
        camera.setX((short) scrollState.cameraX());
        camera.setY((short) (scrollState.cameraY16_16() >> 16));
        camera.setShakeOffsets(0, 0);
    }

    // One visit identity covers arithmetic, authoritative Plane B and exact native draw copies.
    record ScrollCapture(Object owner, CdR11aForegroundScroll.State state, CdR11aBackgroundScroll.State background,
                         CdR11aBackgroundPlane.State plane, DrawCopies copies) {
        ScrollCapture {
            Objects.requireNonNull(owner); Objects.requireNonNull(state); Objects.requireNonNull(background);
            Objects.requireNonNull(plane); Objects.requireNonNull(copies);
        }
    }
    private final class Scroll implements ZoneScrollHandler, CameraDrivenScrollHandler {
        private ScrollEffectComposer projection = new ScrollEffectComposer(0); // Derived output only.
        void resetProjection() { projection = new ScrollEffectComposer(0); }
        private CdR11aBackgroundScroll.State requireBackground() {
            return Objects.requireNonNull(backgroundState, "CD background has no bound source visit");
        }
        @Override public boolean advanceCameraForFrame(Camera camera, int actId) {
            try {
                requireBound();
                if (actId != 0) throw new UnsupportedOperationException("Only R11A foreground is admitted");
                if (openingObjects != null && openingObjects.endCycleAtLevelRestart()) {
                    actor.finishSharedAfterCamera(); // Settle await only: no scroll, SAT or source bit write.
                    return true;
                }
                var m = frame.state().motion(); var shared = frame.environment().movement();
                // The native branch is after ALL object slots. Death skips LevelScroll but still
                // builds sprites and executes the ordinary loop tail (Level/Main.asm:409-424).
                CdR11aForegroundScroll.Result next;
                var nextBackground = backgroundState;
                if (frame.environment().globals().debugMode() == 0 && m.routine() >= 6) {
                    next = CdR11aForegroundScroll.deadCameraFreeze(scrollState, shared.bounds(),
                            shared.globals().cameraYCentre());
                } else {
                    int hudTime = openingObjects == null ? 0 : openingObjects.levelGlobals().updateHudTime();
                    // After ALL objects: USA events -> FG X -> FG Y -> real BG owner. The
                    // terrain DEV HUD omission is explicit; oXVel is the actual completed slot.
                    next = CdR11aForegroundScroll.stepForBackground(scrollState, foregroundInput(frame, hudTime));
                    nextBackground = background.step(backgroundState, backgroundInput(next.state(), m)).state();
                }
                frame.applyScrollFeedback(next.bounds(), next.cameraYCentre());
                scrollState = next.state();
                backgroundState = nextBackground;
                projectCamera(camera);
                if (openingObjects == null) actor.finishAfterCamera();
                else {
                    var foreground = new CdObjectDrawQueue.CameraWords(camera.getX() & 0xFFFF, camera.getY() & 0xFFFF);
                    var bg = new CdObjectDrawQueue.CameraWords(backgroundState.cameraBgXWord(), backgroundState.cameraBgYWord());
                    var bg3 = new CdObjectDrawQueue.CameraWords(backgroundState.cameraBg3XWord(), backgroundState.cameraBg3YWord());
                    openingObjects.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(foreground, bg, bg3));
                    actor.finishSharedAfterCamera(); // Project bit7 only; no second private SAT.
                }
                return true;
            } catch (RuntimeException | Error failure) {
                module.closePlayerCycleAfterFailure(failure); // Body completed; no rollback/retry.
                throw failure;
            }
        }

        @Override public void update(int[] output, int cameraX, int cameraY, int frameCounter, int actId) {
            if (actId != 0) throw new UnsupportedOperationException("Only R11A background presentation is admitted");
            Objects.requireNonNull(output, "packed HScroll output");
            if (output.length > CdR11aBackgroundScroll.HSCROLL_RAM_BYTES / 4) {
                throw new IllegalArgumentException("CD projection cannot read beyond the complete 256-long HScroll RAM");
            }
            var state = requireBackground();
            var next = new ScrollEffectComposer(output.length);
            for (int line = 0; line < output.length; line++) {
                // Preserve ALL packed bits, including untouched native tail longs. Composer
                // tracks signed BG-short minus FG-short offsets; no guessed parallax/cloud bias.
                next.writePackedScrollWord(line, (int) state.hscrollRam().unsignedLong(line * 4));
            }
            next.copyPackedScrollWordsTo(output);
            projection = next; // Draw/update/capture/restore never execute source arithmetic.
        }
        @Override public short getVscrollFactorBG() { return (short) requireBackground().vscroll(); }
        @Override public int getMinScrollOffset() { return projection.getMinScrollOffset(); }
        @Override public int getMaxScrollOffset() { return projection.getMaxScrollOffset(); }
        @Override public int getBgCameraX() { return (short) requireBackground().cameraBgXWord(); }
        @Override public int getBgPeriodWidth() {
            if (assets == null) throw new IllegalStateException("CD background has no bound assets");
            return assets.terrain().layout().columns(CdR11aTerrain.Plane.BACKGROUND) * 256;
        }
        @Override public Object captureRewindState() {
            if (scrollState == null) return null;
            requireBound();
            return new ScrollCapture(scrollCaptureOwner, scrollState, backgroundState, planeState, drawCopies);
        }
        @Override public void restoreRewindState(Object snapshot) {
            if (snapshot == null) return;
            requireBound();
            if (!(snapshot instanceof ScrollCapture value) || value.owner() != scrollCaptureOwner) {
                throw new IllegalArgumentException("CD camera capture belongs to another source visit");
            }
            // Every member was shape-checked by its immutable constructor. Identity/complete-value
            // preflight precedes ALL assignments; exact bytes/copies return without stepping source.
            var nextForeground = Objects.requireNonNull(value.state());
            var nextBackground = Objects.requireNonNull(value.background());
            var nextPlane = Objects.requireNonNull(value.plane());
            var nextCopies = Objects.requireNonNull(value.copies());
            scrollState = nextForeground;
            backgroundState = nextBackground;
            planeState = nextPlane;
            drawCopies = nextCopies;
            // Opening selected display bytes are restored by its preflighted shared publication payload.
            if (openingObjects == null) publishedPlane = nextPlane;
        }
    }
}
