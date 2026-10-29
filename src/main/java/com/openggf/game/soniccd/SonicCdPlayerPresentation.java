package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.objects.CdTimeWarpLoadData;
import com.openggf.game.soniccd.scroll.CdR11bBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11bBackgroundPlane;

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
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdCheckpointLoadData;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.player.CdPlayableSonic;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdR11aCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.SpritePresentation;
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
 * its existing player draw. Both display the selected immutable native Plane B with
 * bounded DEV preloaded base-level art, not executed native PLC/section residency.
 * No mixed plane/sprite priority or native pixel-parity claim, no HUD/native music and
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
    // atlas; retained-level reset keeps this reservation and removes just our 664
    // picture patterns. Never clear other producers' ranges or infer ownership
    // from a range with matching coordinates. Rewind cannot move to another atlas.
    private PatternAtlas openingAtlas;
    private static final int OPENING_PICTURE_PATTERNS = 24 + 14 + 70 + 28 + 63 + 70 + 5 + 39 + 36 + 15 + 36 + 12 + 82 + 19 + 46 + 8 + 12 + 22 + 57 + 1 + 5;
    private CdR11aForegroundScroll.State scrollState;
    private CdR11aBackgroundScroll background;
    private CdR11aBackgroundScroll.State backgroundState; // The visit's ONE retained native BG image.
    private CdR11aBackgroundPlane backgroundPlane; // One immutable source binding, not another BG arithmetic owner.
    private CdR11aBackgroundPlane.State planeState; // Authoritative 4096 native bytes, advanced only by source draw service.
    private DrawCopies drawCopies;
    private CdR11aBackgroundPlane.State publishedPlane; // Selected immutable display value; GPU descriptors are derived.
    private boolean loadScrollPending;
    private boolean checkpointSetupPending;
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

    // BEGIN native program InitVDP finite presentation outputs.
    /** Only Background has a retained native image here. Other consumers stop, never borrow it. */
    enum ProgramPlane { FOREGROUND, WINDOW, BACKGROUND }

    static final class UnsupportedProgramVideo extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final ProgramPlane plane;
        private UnsupportedProgramVideo(ProgramPlane plane) {
            super("Native program initialization has no represented " + plane + " name-table consumer");
            this.plane = plane;
        }
        ProgramPlane plane() { return plane; }
    }

    /**
     * Selected physical display outputs, not CPU palette/SAT RAM or a ready flag.
     * Screen LONGs are vscrollScreen/hscrollScreen shadows (General:134-135), not a
     * fabricated VSRAM bank. Foreground/window and unowned pattern holes remain
     * explicit consumer boundaries; this finite value is no complete VDP model.
     * The existing module owns its real source phase, lifetime and once-only use.
     */
    record ProgramVideoSelection(CdAssetBytes displayedPalette, CdR11aBackgroundPlane.State backgroundPlane,
                                 CdAssetBytes displayedSat, int screenHScrollLongBits,
                                 int screenVScrollLongBits, boolean displayEnabled) {
        ProgramVideoSelection {
            Objects.requireNonNull(displayedPalette, "selected native palette");
            Objects.requireNonNull(backgroundPlane, "selected native Background plane");
            Objects.requireNonNull(displayedSat, "selected native sprite image");
            if (displayedPalette.length() != 128 || displayedSat.length() != CdObjectDrawQueue.SAT_BYTE_SIZE) {
                throw new IllegalArgumentException("Complete 128-byte palette and 640-byte selected sprite images required");
            }
        }

        CdR11aBackgroundPlane.State plane(ProgramPlane requested) {
            Objects.requireNonNull(requested, "selected native plane");
            if (requested != ProgramPlane.BACKGROUND) throw new UnsupportedProgramVideo(requested);
            return backgroundPlane;
        }

        @Override public boolean equals(Object other) {
            return this == other || other instanceof ProgramVideoSelection value
                    && sameProgramBytes(displayedPalette, value.displayedPalette)
                    && sameProgramBytes(backgroundPlane.nameTable(), value.backgroundPlane.nameTable())
                    && sameProgramBytes(displayedSat, value.displayedSat)
                    && screenHScrollLongBits == value.screenHScrollLongBits
                    && screenVScrollLongBits == value.screenVScrollLongBits
                    && displayEnabled == value.displayEnabled;
        }
        @Override public int hashCode() {
            return Objects.hash(programBytesHash(displayedPalette), programBytesHash(backgroundPlane.nameTable()),
                    programBytesHash(displayedSat), screenHScrollLongBits, screenVScrollLongBits, displayEnabled);
        }
    }

    /** Transient value for the actual Present program entry, not another retained current bank. */
    record PresentVideoInit(CdR11aCameraArtResidency.State currentPatterns, CdPlayerArtRuntime.State playerArt,
                            ProgramVideoSelection selection) {
        PresentVideoInit {
            Objects.requireNonNull(currentPatterns, "Present current patterns");
            Objects.requireNonNull(playerArt, "Present player art");
            Objects.requireNonNull(selection, "selected native video");
        }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof PresentVideoInit value
                    && currentPatterns.equals(value.currentPatterns)
                    && sameProgramPlayerArt(playerArt, value.playerArt) && selection.equals(value.selection);
        }
        @Override public int hashCode() {
            return Objects.hash(currentPatterns, programPlayerArtHash(playerArt), selection);
        }
    }

    /** Transient/load-visit value for actual Past entry; data never grants program or World authority. */
    record PastVideoInit(CdR11bCameraArtResidency.State currentPatterns, CdPlayerArtRuntime.State playerArt,
                         ProgramVideoSelection selection) {
        PastVideoInit {
            Objects.requireNonNull(currentPatterns, "Past current patterns");
            Objects.requireNonNull(playerArt, "Past player art");
            Objects.requireNonNull(selection, "selected native video");
        }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof PastVideoInit value
                    && currentPatterns.equals(value.currentPatterns)
                    && sameProgramPlayerArt(playerArt, value.playerArt) && selection.equals(value.selection);
        }
        @Override public int hashCode() {
            return Objects.hash(currentPatterns, programPlayerArtHash(playerArt), selection);
        }
    }

    /**
     * The actual module invokes this at Start.GameInit, before InitControllers.
     * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Initialization.asm:78-80
     * and Functions (General).asm:111-176. Applies the finite physical clear outputs,
     * leaving CPU art bytes/lastFrame/dirty, CPU palettes/SAT and camera accumulators
     * unchanged. No input, Frame/clock, decoder, queue, upload or GPU work occurs.
     * The caller adopts the returned tuple once in its existing program lifetime;
     * the explicitly later A DEV cut may supersede it, never retrospectively prove it.
     */
    PresentVideoInit initializeProgramVideo(CdR11aCameraArtResidency.State currentPatterns,
                                            CdPlayerArtRuntime.State playerArt) {
        requireProgramVideoInitializationCut();
        Objects.requireNonNull(currentPatterns, "Present current patterns");
        Objects.requireNonNull(playerArt, "player art at actual program initialization");
        var result = new PresentVideoInit(CdR11aCameraArtResidency.clear(currentPatterns),
                clearProgramPlayerResident(playerArt), programVideoSelection());
        // The existing Background owner alone adopts its ready selected image here.
        // CPU camera copies, live arithmetic and all shared capture ownership survive.
        planeState = result.selection().backgroundPlane();
        publishedPlane = result.selection().backgroundPlane();
        return result;
    }

    /** Same actual source operation over the strict Past physical profile; never an A adapter. */
    PastVideoInit initializeProgramVideo(CdR11bCameraArtResidency.State currentPatterns,
                                         CdPlayerArtRuntime.State playerArt) {
        requireProgramVideoInitializationCut();
        Objects.requireNonNull(currentPatterns, "Past current patterns");
        Objects.requireNonNull(playerArt, "player art at actual program initialization");
        var result = new PastVideoInit(CdR11bCameraArtResidency.clear(currentPatterns),
                clearProgramPlayerResident(playerArt), programVideoSelection());
        planeState = result.selection().backgroundPlane();
        publishedPlane = result.selection().backgroundPlane();
        return result;
    }

    private void requireProgramVideoInitializationCut() {
        if (!module.openingObjectsAdmission() || assets != null || frame != null || actor != null || openingObjects != null) {
            throw new IllegalStateException("Program video initialization requires the actual unbound opening-program load cut");
        }
    }

    private static ProgramVideoSelection programVideoSelection() {
        // The source clears 64 selected colour WORDs and physical displayed storage.
        // It does not load the CPU target palette, clear CPU sprites, or prime a Sonic pose.
        return new ProgramVideoSelection(CdAssetBytes.copyOf(new byte[128]),
                new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(new byte[CdR11aBackgroundPlane.NAME_TABLE_BYTES])),
                CdAssetBytes.copyOf(new byte[CdObjectDrawQueue.SAT_BYTE_SIZE]), 0, 0, false);
    }

    private static CdPlayerArtRuntime.State clearProgramPlayerResident(CdPlayerArtRuntime.State before) {
        return new CdPlayerArtRuntime.State(before.cpuBuffer(),
                CdAssetBytes.copyOf(new byte[CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES]), before.lastFrame(), before.dirty());
    }

    private static boolean sameProgramBytes(CdAssetBytes first, CdAssetBytes second) {
        return java.util.Arrays.equals(first.copy(), second.copy());
    }
    private static int programBytesHash(CdAssetBytes bytes) { return java.util.Arrays.hashCode(bytes.copy()); }

    private static boolean sameProgramPlayerArt(CdPlayerArtRuntime.State first, CdPlayerArtRuntime.State second) {
        return first.lastFrame() == second.lastFrame() && first.dirty() == second.dirty()
                && sameProgramBytes(first.cpuBuffer(), second.cpuBuffer())
                && sameProgramBytes(first.residentBank(), second.residentBank());
    }
    private static int programPlayerArtHash(CdPlayerArtRuntime.State value) {
        return Objects.hash(programBytesHash(value.cpuBuffer()), programBytesHash(value.residentBank()),
                value.lastFrame(), value.dirty());
    }
    // END native program InitVDP finite presentation outputs.

    void bind(CdR11aAssets assets, SonicCdGameModule.BackgroundBuffers buffers) {
        if (frame != null && !frame.closed()) throw new IllegalStateException("Retire the previous CD frame owner first");
        Objects.requireNonNull(assets);
        Objects.requireNonNull(buffers, "explicit background buffer ingress");
        boolean checkpoint = module.checkpointRestartStaged();
        var bounds = assets.loadPlan().bounds();
        // Main.ClearCamera:210-216, then LevelSizeLoad:19-34. A checkpoint has
        // not taken DefaultStart/SetupCamera/InitLevelScroll at this source cut.
        var nextForeground = checkpoint
                ? new CdR11aForegroundScroll.State(0, 0, 0x10, 0x10, 0, 0, 0,
                        bounds.bottom(), bounds.left(), 0, 0)
                : CdR11aForegroundScroll.coldFromLoadPlan(assets.loadPlan(), 320, 224);
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
        var nextBackground = checkpoint ? cleared
                : CdR11aBackgroundScroll.init(cleared, new CdR11aBackgroundScroll.InitRegisters(
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
        checkpointSetupPending = checkpoint;
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
    void completeLoadScroll(CdPlayerFrameRuntime sourceFrame) { completeLoadScroll(sourceFrame, 0); }

    /** Main:246 reads the actual carried HUD byte before Main:304, with no timer service. */
    void completeLoadScroll(CdPlayerFrameRuntime sourceFrame, int sourceHudTime) {
        Objects.requireNonNull(sourceFrame, "pre-Init frame");
        if ((sourceHudTime & ~0xFF) != 0) throw new IllegalArgumentException("Expected the raw HUD time byte");
        if (!loadScrollPending || checkpointSetupPending || sourceFrame.assets() != assets || frame != null) {
            throw new IllegalStateException("CD load scroll requires its fresh source visit and runs exactly once");
        }
        var m = sourceFrame.state().motion();
        if (m.routine() != 0 || m.flags() != 0 || m.groundVelocity() != 0 || m.xVelocity() != 0
                || m.yVelocity() != 0 || m.playerControl() != 0 || m.animation() != 0) {
            throw new IllegalStateException("Load-time scroll requires the cleared player slot before ObjSonic_Init");
        }
        // Whole raw period selects events; this visit still owns its bound Present background.
        var next = CdR11aForegroundScroll.stepForPeriodBackground(scrollState,
                foregroundInput(sourceFrame, sourceHudTime), sourceFrame.environment().movement().gates().timeZone());
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

    /**
     * Scalar reads remain available through the fresh UNBOUND checkpoint visit, including
     * after load scroll and before bindFrame. Post-scroll LoadSectionArt reads THIS owner,
     * never the engine camera projection. This gate grants no store or capture authority.
     */
    private void requireCheckpointLoadRead() {
        module.validateTerrainPlayerRequest();
        if (!module.checkpointRestartStaged() || frame != null || actor != null || assets == null
                || scrollState == null || background == null || backgroundState == null
                || module.requireRuntimeSource().assets() != assets) {
            throw new IllegalStateException("Checkpoint load scalar read requires its current unbound source visit");
        }
    }

    private void requireCheckpointLoadStore() {
        requireCheckpointLoadRead();
        if (!loadScrollPending || !checkpointSetupPending) {
            throw new IllegalStateException("Checkpoint camera stores precede SetupCamera and load scroll only");
        }
    }

    /** Only LoadCheckpointData:59-67 targets, in their existing FG/BG owners. */
    int readCheckpointLoadWord(CdCheckpointLoadData.StoreTarget target) {
        requireCheckpointLoadRead();
        return switch (Objects.requireNonNull(target, "checkpoint camera target")) {
            case DEST_BOTTOM_BOUND -> scrollState.destBottomBound();
            case FG_X -> scrollState.cameraX();
            case FG_Y -> scrollState.cameraYWord();
            case BG_X -> backgroundState.cameraBgXWord();
            case BG_Y -> backgroundState.cameraBgYWord();
            case BG2_X -> backgroundState.cameraBg2XWord();
            case BG2_Y -> backgroundState.cameraBg2YWord();
            case BG3_X -> backgroundState.cameraBg3XWord();
            case BG3_Y -> backgroundState.cameraBg3YWord();
            default -> throw new IllegalArgumentException("Not a Presentation checkpoint WORD target");
        };
    }

    /** One addressed WORD store; no whole camera restore and no mutation after SetupCamera. */
    void storeCheckpointLoadWord(CdCheckpointLoadData.StoreCall call) {
        Objects.requireNonNull(call, "checkpoint camera store");
        requireCheckpointLoadStore();
        if (call.expectedValue().isEmpty()
                || readCheckpointLoadWord(call.target()) != call.expectedValue().getAsInt()) {
            throw new IllegalArgumentException("Stale checkpoint camera WORD");
        }
        int value = call.value();
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned camera WORD");
        var f = scrollState;
        if (call.target() == CdCheckpointLoadData.StoreTarget.DEST_BOTTOM_BOUND
                || call.target() == CdCheckpointLoadData.StoreTarget.FG_X
                || call.target() == CdCheckpointLoadData.StoreTarget.FG_Y) {
            scrollState = new CdR11aForegroundScroll.State(
                    call.target() == CdCheckpointLoadData.StoreTarget.FG_X ? value : f.cameraX(),
                    call.target() == CdCheckpointLoadData.StoreTarget.FG_Y
                            ? storeIntegerWord(f.cameraY16_16(), value) : f.cameraY16_16(),
                    f.horizontalCrossed(), f.verticalCrossed(), f.scrollXDiff(), f.scrollYDiff(), f.scrollFlags(),
                    call.target() == CdCheckpointLoadData.StoreTarget.DEST_BOTTOM_BOUND ? value : f.destBottomBound(),
                    f.destLeftBound(), f.bottomBoundShift(), f.scrollLock());
            return;
        }
        var b = backgroundState;
        backgroundState = new CdR11aBackgroundScroll.State(
                call.target() == CdCheckpointLoadData.StoreTarget.BG_X ? storeIntegerWord(b.cameraBgX(), value) : b.cameraBgX(),
                call.target() == CdCheckpointLoadData.StoreTarget.BG_Y ? storeIntegerWord(b.cameraBgY(), value) : b.cameraBgY(),
                call.target() == CdCheckpointLoadData.StoreTarget.BG2_X ? storeIntegerWord(b.cameraBg2X(), value) : b.cameraBg2X(),
                call.target() == CdCheckpointLoadData.StoreTarget.BG2_Y ? storeIntegerWord(b.cameraBg2Y(), value) : b.cameraBg2Y(),
                call.target() == CdCheckpointLoadData.StoreTarget.BG3_X ? storeIntegerWord(b.cameraBg3X(), value) : b.cameraBg3X(),
                call.target() == CdCheckpointLoadData.StoreTarget.BG3_Y ? storeIntegerWord(b.cameraBg3Y(), value) : b.cameraBg3Y(),
                b.crossingRam(), b.scrollFlagsBg(), b.scrollFlagsBg2(), b.scrollFlagsBg3(), b.cloudRam(),
                b.deformationRam(), b.hscrollRam(), b.vscroll());
    }

    private static int storeIntegerWord(int before, int value) { return (value << 16) | (before & 0xFFFF); }

    /** PP Scroll:66-111/135-170, after all helper stores and before Main:246. */
    void completeCheckpointSetup(CdPlayerFrameRuntime sourceFrame) {
        Objects.requireNonNull(sourceFrame, "loaded checkpoint Frame");
        requireCheckpointLoadStore();
        if (sourceFrame.closed() || sourceFrame.assets() != assets) {
            throw new IllegalArgumentException("Checkpoint SetupCamera requires this fresh Frame");
        }
        var m = sourceFrame.state().motion();
        var next = CdR11aForegroundScroll.checkpointSetupCamera(scrollState, m.centreX16_16() >>> 16,
                m.centreY16_16() >>> 16, sourceFrame.environment().movement().bounds());
        // MOVEQ0 at PP Scroll:66-67 establishes the register upper words. BG1 Y
        // is a LONG write; BG2/3 WORD writes retain their existing fractional halves.
        var nextBackground = CdR11aBackgroundScroll.init(backgroundState,
                new CdR11aBackgroundScroll.InitRegisters(next.cameraYWord(), next.cameraX(), m.centreX16_16() >>> 16));
        scrollState = next;
        backgroundState = nextBackground;
        checkpointSetupPending = false;
    }

    /** Bound dynamic SaveData reads borrow the ONE live pre-scroll camera image, never DrawCopies. */
    int currentCheckpointCameraWord(CdCheckpointSaveData.Source source) {
        requireBound();
        return switch (Objects.requireNonNull(source, "checkpoint camera source")) {
            case FG_X -> scrollState.cameraX();
            case FG_Y -> scrollState.cameraYWord();
            case BG_X -> backgroundState.cameraBgXWord();
            case BG_Y -> backgroundState.cameraBgYWord();
            case BG2_X -> backgroundState.cameraBg2XWord();
            case BG2_Y -> backgroundState.cameraBg2YWord();
            case BG3_X -> backgroundState.cameraBg3XWord();
            case BG3_Y -> backgroundState.cameraBg3YWord();
            default -> throw new IllegalArgumentException("Not a checkpoint camera source WORD");
        };
    }

    void requireCheckpointReadOwner(CdPlayerFrameRuntime sourceFrame) {
        requireBound();
        if (frame != sourceFrame) throw new IllegalStateException("Checkpoint reads belong to another Frame visit");
    }

    /** Borrow only the live immutable source pair; neither capture nor Body reentry is performed. */
    CdOpeningObjectRuntime.TimeWarpWorldCut borrowTimeWarpWorld(CdPlayerFrameRuntime sourceFrame) {
        requireTimeWarpWorldOwner(sourceFrame);
        return new CdOpeningObjectRuntime.TimeWarpWorldCut(scrollState, backgroundState);
    }

    /** N Sonic/Main.asm:618 writes only scrollLock's HIGH byte, before any saved camera reads. */
    void validateTimeWarpLock(CdPlayerFrameRuntime sourceFrame,
                              CdOpeningObjectRuntime.TimeWarpLockPublication publication) {
        requireTimeWarpWorldOwner(sourceFrame);
        var expected = Objects.requireNonNull(publication, "prepared time-warp scroll publication").expected();
        var before = expected.foreground();
        var after = publication.finalForeground();
        if (!scrollState.equals(before) || !backgroundState.equals(expected.background())
                || after.cameraX() != before.cameraX() || after.cameraY16_16() != before.cameraY16_16()
                || after.horizontalCrossed() != before.horizontalCrossed()
                || after.verticalCrossed() != before.verticalCrossed()
                || after.scrollXDiff() != before.scrollXDiff() || after.scrollYDiff() != before.scrollYDiff()
                || after.scrollFlags() != before.scrollFlags() || after.destBottomBound() != before.destBottomBound()
                || after.destLeftBound() != before.destLeftBound() || after.bottomBoundShift() != before.bottomBoundShift()
                || after.scrollLock() != ((before.scrollLock() & 0xFF) | 0x100)) {
            throw new IllegalStateException("Time-warp scroll publication differs from its complete actual source pair");
        }
    }

    void applyTimeWarpLock(CdPlayerFrameRuntime sourceFrame,
                           CdOpeningObjectRuntime.TimeWarpLockPublication publication) {
        validateTimeWarpLock(sourceFrame, publication);
        scrollState = publication.finalForeground();
    }

    private void requireTimeWarpWorldOwner(CdPlayerFrameRuntime sourceFrame) {
        requireBound();
        if (frame != sourceFrame || openingObjects == null
                || openingObjects.phase() != CdOpeningObjectRuntime.Phase.PLAYER_BODY) {
            throw new IllegalStateException("Time-warp scroll access requires this bound Frame's PLAYER_BODY visit");
        }
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

    /** Borrow the ONE live foreground scroll-lock WORD for the selected native PLC service. */
    int currentScrollLockWord() { requireBound(); return scrollState.scrollLock(); }

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

    /** Read-only selected immutable image; drawing never advances this source publication. */
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
            background = null; backgroundState = null; loadScrollPending = false; checkpointSetupPending = false;
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
        // A returned programme settles IPX's own blank pulse before any scene pulse/input.
        // Existing level owners stay inactive and capturable until a genuine destination load.
        if (openingObjects != null) {
            var phase = module.levelProgramState().phase();
            if (phase == SonicCdGameModule.LevelProgramPhase.RETURNED
                    || phase == SonicCdGameModule.LevelProgramPhase.PAST_PENDING) {
                requireBound(); // Retained atlas/World admission precedes ANY programme prefix.
                module.restartOpeningWorld();
                heldPhase = null;
                return GameplayFrameDisposition.HOLD;
            }
            if (phase == SonicCdGameModule.LevelProgramPhase.LEVEL && openingObjects.levelGlobals().levelRestart() != 0)
                module.restartOpeningWorld();
        }
        requireBound();
        actor.requireCompletedNativeFrame();
        if (input != null && input.debugModeTogglePressed()) {
            throw new UnsupportedOperationException("CD terrain entry does not support host debug movement");
        }
        int held = nativeHeldByte(input, SpriteManagerInternalAccess.inputSuppressed(GameServices.sprites()));
        if (openingObjects != null && module.warpProgramSelected()) {
            heldPhase = null;
            // WARP owns its own counter/timer and conditional controller latch. Never tick the
            // retained level ObjectManager/Frame just to account for this advancing scene row.
            module.serviceWarpProgram(held);
            return GameplayFrameDisposition.HOLD;
        }
        if (openingObjects != null
                && module.levelProgramState().phase() == SonicCdGameModule.LevelProgramPhase.WHITE_FADE) {
            try {
                // Selector12 is serviced by the actual programme caller, never the ordinary held
                // level owner. Reuse the same represented-VBlank owner as controlled gameplay;
                // Frame retains its own native clock. Start remains only a raw controller bit.
                heldPhase = null;
                EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
                module.serviceOldLevelStartWhiteFade(held);
                return GameplayFrameDisposition.HOLD;
            } catch (RuntimeException | Error failure) {
                module.closePlayerCycleAfterFailure(failure);
                throw failure;
            }
        }
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
            if (openingObjects != null
                    && module.levelProgramState().phase() != SonicCdGameModule.LevelProgramPhase.LEVEL) {
                // The last fade wait returns with RETURNED, so phase alone identifies this HOLD.
                // No pending ordinary input, Body, camera/SAT build or ordinary tail is admitted.
                if (advanced || heldPhase != null || frame.paused()
                        || openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
                    throw new IllegalStateException("CD programme HOLD did not retain its completed cut");
                }
                return;
            }
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
            module.closePlayerCycleAfterFailure(failure);
            throw failure;
        }
    }

    /** GPU/engine palette objects are projections of the actual previous V-int selection, not native RAM owners. */
    private void publishSelectedPalette() {
        if (openingObjects == null) return;
        var current = GameServices.level().getCurrentLevel();
        if (!(current instanceof SonicCdLevel level) || level.assets() != assets) {
            throw new IllegalStateException("CD palette projection requires its current authenticated level");
        }
        var bytes = openingObjects.palettes().displayedPalette();
        for (int line = 0; line < 4; line++) {
            var palette = level.getPalette(line);
            palette.fromSegaFormat(bytes.slice(line * 32, 32).copy());
            GameServices.graphics().cachePaletteTexture(palette, line);
        }
    }

    private void drawCompletedOverlay() {
        requireBound();
        actor.requireCompletedNativeFrame();
        if (openingObjects != null && openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
            throw new IllegalStateException("Opening draw requires its completed loop, never a partial SAT");
        }
        if (openingObjects != null && module.warpProgramSelected()) {
            drawWarpProgram();
            return;
        }
        var level = GameServices.level();
        var selectedPlane = publishedPlane();
        var selectedScroll = openingObjects == null ? null : publishedScroll;
        var selectedPicture = openingObjects == null ? null : openingObjects.displayedFrame();
        var descriptors = nativeBackgroundDescriptors(selectedPlane);
        requireNativeBackgroundResidents(level, selectedPlane, false);
        // The shared scope owns actual idle/lifetime admission, the old-image FIFO barrier,
        // fixed-cache settlement and the selected-image command drain. Null means the current
        // completed-load view: no public ScrollFrame, first-input token or parallax update.
        LevelSpritePresentation.withScrollAndBackground(level, selectedScroll, descriptors, () -> {
            requireBound();
            actor.requireCompletedNativeFrame();
            if (publishedPlane != selectedPlane || (openingObjects != null
                    && (publishedScroll != selectedScroll || openingObjects.displayedFrame() != selectedPicture))) {
                throw new IllegalStateException("CD background publication changed during its render scope");
            }
            // Old commands have now drained. Recheck actual art/lookup authority before any
            // terrain command can sample it; never repair an invalid lookup by drawing a fallback.
            requireNativeBackgroundResidents(level, selectedPlane, true);
            // The previous-image FIFO has drained. Upload only the already-selected immutable
            // palette before any new terrain command; never recolour queued old-image work.
            publishSelectedPalette();
            level.drawWithRenderOptions(GameServices.sprites(), openingObjects == null
                    ? new LevelManager.LevelRenderOptions(true, false, false, false, false, false, false)
                    : LevelManager.LevelRenderOptions.tilesOnly());
        });
        if (openingObjects != null) {
            var camera = GameServices.camera();
            // Screen-relative immutable SAT picture; renderer adds camera back for
            // the world-coordinate graphics API. Preserve native SAT order. Plane
            // priority composition remains bounded, not full VDP parity.
            SpritePresentationRenderer.draw(GameServices.graphics(), selectedPicture,
                    camera.getX(), camera.getY(), layer -> true);
        }
        GameServices.graphics().flush();
    }


    /** Pure selected-scene projection inside the existing controller draw; no native work occurs here. */
    private void drawWarpProgram() {
        var graphics = GameServices.graphics();
        var scene = module.warpSceneState().orElseThrow();
        var selectedProgram = module.levelProgramState();
        var selectedAssets = module.warpSceneAssets();
        var camera = GameServices.camera();
        // Drain old images before reusing our existing exclusive OBJECTS cache slots or palettes.
        graphics.flush();
        com.openggf.graphics.PaletteUploadPresentation.withoutDisplayedFade(graphics, () -> {
            requireBound(); requireOpeningAtlas();
            if (module.warpSceneState().orElseThrow() != scene || module.levelProgramState() != selectedProgram)
                throw new IllegalStateException("Selected WARP scene changed during drawing");
            boolean enabled = selectedProgram.phase() == SonicCdGameModule.LevelProgramPhase.WARP && scene.displayEnabled();
            int backdrop = 0;
            if (enabled) {
                var palette = scene.displayedPalette().orElseThrow(() -> new IllegalStateException("WARP selected unknown CRAM"));
                for (int line = 0; line < 4; line++) {
                    var derived = new com.openggf.level.Palette();
                    derived.fromSegaFormat(palette.slice(line * 32, 32).copy());
                    graphics.cachePaletteTexture(derived, line);
                }
                var colour = new com.openggf.level.Palette.Color(); colour.fromSegaFormat(palette.unsignedWord(0));
                backdrop = com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(colour.r & 255, colour.g & 255,
                        colour.b & 255, graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
            }
            graphics.registerCommand(com.openggf.graphics.GLCommand.screenSpaceRect(graphics, 224, 0, 0, 320, 224, 0xFF000000 | backdrop));
            graphics.flush();
            if (enabled) {
                int base = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var background = com.openggf.game.soniccd.scene.CdWarpPresentation.background(selectedAssets, scene, base);
                var sprites = com.openggf.game.soniccd.scene.CdWarpPresentation.sprites(selectedAssets, scene, base);
                SpritePresentationRenderer.draw(graphics, background, camera.getX(), camera.getY(), layer -> true);
                graphics.flush();
                SpritePresentationRenderer.drawNative(graphics, sprites, camera.getX(), camera.getY(), layer -> true);
                graphics.flush();
            }
        });
    }

    /**
     * Physical 64x32 native words, already reflected by the source Plane owner, to renderer RGBA.
     * Zero still names pattern0; descriptor alpha is FF for every cell. Residency is a draw gate,
     * not an encoder rewrite. The native priority bit survives despite the existing overlay gap.
     */
    static byte[] nativeBackgroundDescriptors(CdR11aBackgroundPlane.State plane) {
        Objects.requireNonNull(plane, "selected native Plane B");
        byte[] descriptors = new byte[64 * 32 * 4];
        for (int cell = 0; cell < 64 * 32; cell++) {
            int word = plane.nameTable().unsignedWord(cell * 2);
            descriptors[cell * 4] = (byte) word;
            descriptors[cell * 4 + 1] = (byte) (((word >>> 8) & 7) | (((word >>> 13) & 3) << 3)
                    | ((word & 0x0800) != 0 ? 0x20 : 0) | ((word & 0x1000) != 0 ? 0x40 : 0)
                    | ((word & 0x8000) != 0 ? 0x80 : 0));
            descriptors[cell * 4 + 3] = (byte) 0xFF;
        }
        return descriptors;
    }

    /**
     * Explicit DEV resident-base-art policy: the authenticated800 patterns and the actual current
     * Level are authoritative. This is not native section PLC execution/residency and adds no path
     * fence. A CPU projection needs no atlas entries; real drawing additionally needs the current
     * initialized atlas0 upload samples, palette texture and exact lookup entries.
     */
    private void requireNativeBackgroundResidents(LevelManager level, CdR11aBackgroundPlane.State selected,
                                                   boolean lookupReady) {
        if (!(level.getCurrentLevel() instanceof SonicCdLevel current) || current.assets() != assets
                || assets.terrain().patternCount() != 800 || current.getPatternCount() != 800) {
            throw new IllegalStateException("CD background requires the authenticated800 resident base-level patterns");
        }
        var graphics = GameServices.graphics();
        boolean gpu = !graphics.isHeadlessMode();
        var atlas = graphics.getPatternAtlas();
        if (gpu && (!graphics.isGlInitialized() || atlas == null || !atlas.isInitialized()
                || atlas.getTextureId(0) == 0 || graphics.getTilemapGpuRenderer() == null
                || graphics.getCombinedPaletteTextureId() == null || graphics.getCombinedPaletteTextureId() == 0)) {
            throw new IllegalStateException("CD background requires initialized current atlas and level palette");
        }
        // Palette selection remains the native two-bit line; actual Level/publisher owns all colours.
        if (current.getPaletteCount() != 4) throw new IllegalStateException("CD background requires four current level palettes");
        for (int line = 0; line < 4; line++) {
            var palette = current.getPalette(line);
            if (palette == null || palette.getColorCount() != 16) {
                throw new IllegalStateException("CD background has no current level palette line " + line);
            }
            for (int colour = 0; colour < 16; colour++) {
                if (palette.getColor(colour) == null) throw new IllegalStateException("CD background has an absent level colour");
            }
        }
        var tilemap = level.getTilemapManager();
        byte[] lookup = gpu && lookupReady ? tilemap.getPatternLookupData() : null;
        boolean[] checked = new boolean[800];
        for (int cell = 0; cell < 64 * 32; cell++) {
            int id = selected.nameTable().unsignedWord(cell * 2) & 0x7FF;
            if (id >= checked.length) {
                throw new IllegalStateException("CD background references an unloaded resident base-level pattern " + id);
            }
            if (checked[id]) continue;
            checked[id] = true;
            var pattern = current.getPattern(id);
            if (pattern == null) throw new IllegalStateException("CD background has no resident base-level pattern " + id);
            var entry = gpu ? atlas.getEntry(id) : null; // No allocating get-entry wrapper or invented bank.
            var sample = gpu ? SpritePresentation.patternSample(graphics, id) : null;
            if (gpu && (entry == null || entry.patternId() != id || entry.atlasIndex() != 0 || sample == null
                    || entry.tileX() < 0 || entry.tileX() > 255 || (entry.tileX() + 1) * 8 > atlas.getAtlasWidth()
                    || entry.tileY() < 0 || entry.tileY() > 255 || (entry.tileY() + 1) * 8 > atlas.getAtlasHeight())) {
                throw new IllegalStateException("CD background has no current atlas0 resident base-level pattern " + id);
            }
            for (int pixel = 0; pixel < 64; pixel++) {
                int x = pixel & 7, y = pixel >>> 3, value = pattern.getPixel(x, y) & 255;
                if (value != assets.terrain().pixel(id, x, y)) {
                    throw new IllegalStateException("CD background changed resident base-level pattern " + id);
                }
                if (gpu) {
                    long word = switch (pixel >>> 4) { case 0 -> sample.a(); case 1 -> sample.b();
                        case 2 -> sample.c(); default -> sample.d(); };
                    if (((word >>> ((pixel & 15) * 4)) & 15) != value) {
                        throw new IllegalStateException("CD background has a stale resident base-level pattern upload " + id);
                    }
                }
            }
            // The generic shader ignores lookup alpha and atlasIndex; a missing/stale lookup
            // can otherwise sample clamped or unrelated pixels even with a valid native word.
            if (gpu && lookupReady && (lookup == null || tilemap.getPatternLookupSize() <= id
                    || lookup.length < (id + 1) * 4 || (lookup[id * 4] & 255) != entry.tileX()
                    || (lookup[id * 4 + 1] & 255) != entry.tileY() || lookup[id * 4 + 2] != 0
                    || (lookup[id * 4 + 3] & 255) != 255)) {
                throw new IllegalStateException("CD background has an invalid resident base-level pattern lookup " + id);
            }
        }
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
                    // Time-warp marking does not replace the bound background programme.
                    next = CdR11aForegroundScroll.stepForPeriodBackground(scrollState,
                            foregroundInput(frame, hudTime), shared.gates().timeZone());
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
            return 512; // Physical64x32 Plane B, Main.asm:227-231; never the source layout extent.
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
    // BEGIN explicit R11B loading source ports. These data relations are not Module authority.
    private record PastSource(CdR11bAssets assets, CdStagePlayerSource source,
                              CdPlayerFrameRuntime frame, CdR11bBackgroundPlane plane) { }
    private PastSource pastSource;

    /**
     * Borrows the actual B aggregate and the existing complete mutable images at this source cut.
     * No clear, saved read, SetupCamera, service, draw or gameplay admission occurs here.
     * The caller owns retiring the old visit and the actual Module/program order.
     */
    void bindPastSource(CdR11bAssets sourceAssets, CdPlayerFrameRuntime sourceFrame,
                        CdR11aForegroundScroll.State foreground,
                        CdR11aBackgroundScroll.State backgroundImage,
                        CdR11aBackgroundPlane.State plane, DrawCopies copies) {
        Objects.requireNonNull(sourceAssets, "actual Past aggregate");
        Objects.requireNonNull(sourceFrame, "actual Past Frame");
        Objects.requireNonNull(foreground, "current foreground");
        Objects.requireNonNull(backgroundImage, "current background");
        Objects.requireNonNull(plane, "current Plane B");
        Objects.requireNonNull(copies, "current draw copies");
        if (!module.openingObjectsAdmission() || assets != null || frame != null || actor != null
                || openingObjects != null || background != null || backgroundPlane != null
                || (pastSource != null && scrollState != null && !pastSource.frame().closed())) {
            throw new IllegalStateException("Past source bind requires the named unbound visit");
        }
        if (sourceFrame.closed()) throw new IllegalStateException("Closed Past Frame");
        sourceFrame.captureShared(); // Existing actual open/idle/closed-cut guard; no new capture owner.
        var source = sourceFrame.stageSource();
        if (!source.ownsPast(sourceAssets)) {
            throw new IllegalArgumentException("Past source aggregate and actual Frame differ");
        }
        var next = new PastSource(sourceAssets, source, sourceFrame, CdR11bBackgroundPlane.bind(sourceAssets));
        var nextOwner = new Object();
        pastSource = next;
        scrollState = foreground;
        backgroundState = backgroundImage;
        planeState = plane;
        drawCopies = copies;
        publishedPlane = plane;
        loadScrollPending = true;
        checkpointSetupPending = true;
        scrollCaptureOwner = nextOwner;
        preparedScroll = null;
        publishedScroll = null;
        heldPhase = null;
        scroll.resetProjection();
    }

    /** Actual B identity and complete retained-image presence, without borrowing legacy A admission. */
    private PastSource requirePastSource() {
        var source = pastSource;
        if (!module.openingObjectsAdmission() || source == null || assets != null || frame != null
                || actor != null || openingObjects != null || background != null || backgroundPlane != null
                || scrollState == null || backgroundState == null || planeState == null || drawCopies == null
                || source.frame().closed()) {
            throw new IllegalStateException("Past loading source is absent, retired or mixed with Present");
        }
        if (source.frame().stageSource() != source.source() || !source.source().ownsPast(source.assets())) {
            throw new IllegalArgumentException("Past Frame and aggregate relation changed");
        }
        var gates = source.frame().environment().movement().gates();
        int period = gates.timeZone();
        if (gates.zoneAct() != 0 || (period != 0 && period != 0x80 && period != 0x81 && period != 0x82)) {
            throw new IllegalArgumentException("Past loading source family differs");
        }
        return source;
    }

    private PastSource requirePastClosedSource() {
        var source = requirePastSource();
        source.frame().captureShared();
        return source;
    }

    /** LoadTimeWarpData:19-27 addressed destination observations; no saved bytes are sampled. */
    int readPastLoadWord(CdTimeWarpLoadData.StoreTarget target) {
        requirePastClosedSource();
        return switch (Objects.requireNonNull(target, "Past camera target")) {
            case DEST_BOTTOM_BOUND -> scrollState.destBottomBound();
            case FG_X -> scrollState.cameraX();
            case FG_Y -> scrollState.cameraYWord();
            case BG_X -> backgroundState.cameraBgXWord();
            case BG_Y -> backgroundState.cameraBgYWord();
            case BG2_X -> backgroundState.cameraBg2XWord();
            case BG2_Y -> backgroundState.cameraBg2YWord();
            case BG3_X -> backgroundState.cameraBg3XWord();
            case BG3_Y -> backgroundState.cameraBg3YWord();
            default -> throw new IllegalArgumentException("Not a Presentation time-warp WORD target");
        };
    }

    /**
     * One complete WORD value-CAS. Public scalar equality is not phase, invocation or exact-once
     * authority. The real Module's LoadTimeWarpData port owns those obligations and saved42.
     */
    void storePastLoadWord(CdTimeWarpLoadData.StoreCall call) {
        Objects.requireNonNull(call, "Past camera store");
        requirePastClosedSource();
        if (!loadScrollPending || !checkpointSetupPending) {
            throw new IllegalStateException("Past camera restores precede SetupCamera");
        }
        int before = readPastLoadWord(call.target()); // Also rejects every foreign-owner target.
        if (call.expectedValue().isEmpty() || call.expectedValue().getAsInt() != before) {
            throw new IllegalArgumentException("Stale Past camera WORD");
        }
        int value = call.value(); // StoreCall already validates its real target's exact width.
        var f = scrollState;
        if (call.target() == CdTimeWarpLoadData.StoreTarget.DEST_BOTTOM_BOUND
                || call.target() == CdTimeWarpLoadData.StoreTarget.FG_X
                || call.target() == CdTimeWarpLoadData.StoreTarget.FG_Y) {
            var next = new CdR11aForegroundScroll.State(
                    call.target() == CdTimeWarpLoadData.StoreTarget.FG_X ? value : f.cameraX(),
                    call.target() == CdTimeWarpLoadData.StoreTarget.FG_Y
                            ? storeIntegerWord(f.cameraY16_16(), value) : f.cameraY16_16(),
                    f.horizontalCrossed(), f.verticalCrossed(), f.scrollXDiff(), f.scrollYDiff(), f.scrollFlags(),
                    call.target() == CdTimeWarpLoadData.StoreTarget.DEST_BOTTOM_BOUND ? value : f.destBottomBound(),
                    f.destLeftBound(), f.bottomBoundShift(), f.scrollLock());
            scrollState = next;
            return;
        }
        var b = backgroundState;
        var next = new CdR11aBackgroundScroll.State(
                call.target() == CdTimeWarpLoadData.StoreTarget.BG_X ? storeIntegerWord(b.cameraBgX(), value) : b.cameraBgX(),
                call.target() == CdTimeWarpLoadData.StoreTarget.BG_Y ? storeIntegerWord(b.cameraBgY(), value) : b.cameraBgY(),
                call.target() == CdTimeWarpLoadData.StoreTarget.BG2_X ? storeIntegerWord(b.cameraBg2X(), value) : b.cameraBg2X(),
                call.target() == CdTimeWarpLoadData.StoreTarget.BG2_Y ? storeIntegerWord(b.cameraBg2Y(), value) : b.cameraBg2Y(),
                call.target() == CdTimeWarpLoadData.StoreTarget.BG3_X ? storeIntegerWord(b.cameraBg3X(), value) : b.cameraBg3X(),
                call.target() == CdTimeWarpLoadData.StoreTarget.BG3_Y ? storeIntegerWord(b.cameraBg3Y(), value) : b.cameraBg3Y(),
                b.crossingRam(), b.scrollFlagsBg(), b.scrollFlagsBg2(), b.scrollFlagsBg3(), b.cloudRam(),
                b.deformationRam(), b.hscrollRam(), b.vscroll());
        backgroundState = next;
    }

    /**
     * Past Scroll:63-177 then Main:245-247, after every actual reached saved store.
     * This is SetupCamera plus one period-aware FG/Past BG pass, never an entry/default reset.
     * Restored flags, velocities, GV, links, fractions and raw marked period remain actual values.
     * SpecChunks/RAM/layout/palette installation and initial plane draw remain separate owners/cuts.
     */
    void completePastLoadScroll(CdPlayerFrameRuntime sourceFrame, int sourceHudTime) {
        var source = requirePastClosedSource();
        if (sourceFrame != source.frame()) throw new IllegalArgumentException("Foreign Past setup Frame");
        if ((sourceHudTime & ~0xFF) != 0) throw new IllegalArgumentException("Expected the raw HUD time byte");
        if (!loadScrollPending || !checkpointSetupPending) {
            throw new IllegalStateException("Past load scroll requires its pre-SetupCamera data cut");
        }
        var m = sourceFrame.state().motion();
        var setup = CdR11aForegroundScroll.checkpointSetupCamera(scrollState, m.centreX16_16() >>> 16,
                m.centreY16_16() >>> 16, sourceFrame.environment().movement().bounds());
        var initialized = CdR11bBackgroundScroll.init(backgroundState, setup);
        var next = CdR11aForegroundScroll.stepForPeriodBackground(setup,
                pastForegroundInput(sourceFrame, sourceHudTime),
                sourceFrame.environment().movement().gates().timeZone());
        var f = next.state();
        var nextBackground = CdR11bBackgroundScroll.step(initialized,
                new CdR11bBackgroundScroll.Input(f.cameraX(), f.cameraYWord(),
                        (short) f.scrollXDiff(), (short) f.scrollYDiff(), f.scrollLock() >>> 8));
        var nextForeground = new CdR11aForegroundScroll.State(f.cameraX(), f.cameraY16_16(),
                f.horizontalCrossed(), f.verticalCrossed(), f.scrollXDiff(), f.scrollYDiff(), f.scrollFlags() | 0x0400,
                f.destBottomBound(), f.destLeftBound(), f.bottomBoundShift(), f.scrollLock());
        // All pure values are ready before the existing full Frame feedback validation/adoption.
        sourceFrame.applyScrollFeedback(next.bounds(), next.cameraYCentre());
        scrollState = nextForeground;
        backgroundState = nextBackground;
        checkpointSetupPending = false;
    }

    private CdR11aForegroundScroll.Input pastForegroundInput(CdPlayerFrameRuntime sourceFrame, int sourceHudTime) {
        var m = sourceFrame.state().motion(); var shared = sourceFrame.environment().movement();
        return new CdR11aForegroundScroll.Input(new CdR11aForegroundScroll.Actor(m.centreX16_16() >>> 16,
                m.centreY16_16() >>> 16, m.flags(), m.groundVelocity(), m.animation(), m.playerControl()),
                shared.bounds(), pastSource.assets().loadPlan().bounds().top(),
                sourceFrame.environment().globals().cameraXCentre(), shared.globals().cameraYCentre(),
                pastSource.source().variant(), sourceHudTime, 320, 224);
    }

    /** Main:249's B plane result only; actual preceding RAM/layout and FG draw remain Root-owned. */
    void initializePastPlaneAtLoad() {
        var source = requirePastClosedSource();
        if (!loadScrollPending || checkpointSetupPending) {
            throw new IllegalStateException("Initial Past Plane B follows completed setup/load scroll");
        }
        var initialized = source.plane().initialize(planeState, liveCameras(backgroundState), liveFlags(backgroundState));
        planeState = initialized.state();
        publishedPlane = initialized.state();
        loadScrollPending = false;
    }

    /** Prospective actual closed-cut check; carries no deferred service, token or invocation authority. */
    void validatePastLevelLoadPlane() { requirePastClosedSource(); }

    /**
     * Main:860-876 selectorC copy/draw cut, called synchronously inside the actual Frame service.
     * captureShared is deliberately unavailable inside that service. The real Module preflights
     * the closed cut and authenticates phase/exact-once/reentry; this method verifies source data.
     * No BG step, FG arithmetic, Body, DPLC, PLC, input or counter service is performed here.
     */
    void servicePastPlaneAtLevelLoad() {
        var source = requirePastSource();
        var live = liveCameras(backgroundState);
        var copied = new DrawCopies(scrollState.cameraX() << 16, scrollState.cameraY16_16(),
                new CdR11aBackgroundPlane.CopiedCameras(live.bg1(), live.bg2(), live.bg3()),
                scrollState.scrollFlags(), liveFlags(backgroundState));
        var drawn = source.plane().incremental(planeState, live, copied.bg(), copied.bgFlags());
        var consumed = new DrawCopies(copied.fgX16_16(), copied.fgY16_16(),
                copied.bg(), copied.fgFlags(), drawn.flags());
        planeState = drawn.state();
        drawCopies = consumed;
    }
    // END explicit R11B loading source ports.

}
