package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;

import com.openggf.game.GameServices;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineTiming;

import java.util.*;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.SonicCdGameModule.PastLoadCall.*;
import static com.openggf.game.soniccd.SonicCdGameModule.PastLoadPhase.*;

/**
 * Actual same-World USA R11B loading and bounded first object pass. Native N1ecc5f3a Level/Main.asm:119-403.
 * Host geometry preparation is separate from the executed blocks/layout/plane writes.
 * Complete gameplay dispatch remains bounded; no alternate clock, renderer readiness or RAM fallback.
 */
final class SonicCdPastLoadingVisit {
    private final SonicCdGameModule module;
    private final CdR11bAssets assets;
    private final CdPlayerFrameRuntime frame;
    private final SonicCdPlayerPresentation presentation;
    private final CdTimeWarpSaveData.State saved;
    private final CdR11bSectionArt art;
    private final CdR11bForegroundPlane foreground;
    private final PlayerSpriteRenderer playerRenderer;
    private final CdLevelHud hud;
    private final CdObjectPlacementRuntime placement;
    private final CdReservedHud reservedHud;
    private final CdRing ring;
    private final CdSpring spring;
    private final CdPlatform platform;
    private final CdFloatBlock floatBlock;
    private final CdAnimal animal;
    private final CdPataBata pataBata;
    private final CdTamabboh tamabboh;
    private final CdTunnelDoor tunnelDoor;
    private final CdMonitor monitor;
    private final CdTimePost timePost;
    private final CdPlayerObjectTouch primaryScanner;
    private final CdPlayerTouchResponse primaryResponse;
    private transient PrimaryInvocation activePrimary;
    private transient CdObjectDrawQueue drawQueue;
    private transient ReservedInvocation activeReserved;
    private transient RingInvocation activeRing;
    private transient SpringInvocation activeSpring;
    private transient PlatformInvocation activePlatform;
    private transient FloatInvocation activeFloat;
    private transient PataInvocation activePata;
    private transient TamabbohInvocation activeTamabboh;
    private transient TunnelDoorInvocation activeTunnelDoor;
    private transient MonitorInvocation activeMonitor;
    private transient DrainInvocation activeDrain;
    private final List<CdLevelPaletteCycle.Script> paletteCycles;
    private boolean runningObjects;
    private boolean reservedPort;
    private boolean drawingReserved;
    private boolean ringPort;
    private boolean drawingRing;
    private boolean springPort;
    private boolean drawingSpring;
    private boolean platformPort;
    private boolean drawingPlatform;
    private boolean floatPort;
    private boolean drawingFloat;
    private boolean drawingPata;
    private boolean tamabbohPort;
    private boolean drawingTamabboh;
    private boolean tunnelDoorPort;
    private boolean drawingTunnelDoor;
    private boolean monitorPort;
    private boolean drawingMonitor;
    private boolean queuePort;
    private Values values;
    private List<SonicCdGameModule.PastSound> sounds = List.of();
    private boolean servicing;
    private boolean loadingSaved;
    private int savedCursor;
    private CdTimeWarpLoadData.StoreTarget observedTarget;
    private OptionalInt observedValue;
    private CdPlayerFrameRuntime.ActorState observedActor;
    private CdPlayerFrameRuntime.SharedState observedShared;
    private SonicCdPlayerPresentation.ScrollCapture observedScroll;
    private Values observedValues;

    /** Reached fixed programme stores; subsequent HUD/fade operations preserve their other values. */
    private record TailValues(int player2ControlsWord, int player2BoredWord, int unknownLevelByte,
                              int demoS1IndexWord, int paletteFadeInfoWord) { }
    private record LoadingGlobals(CdCheckpointLiveAux.State live, CdPlayerBody.ContactGlobals contact,
                                  CdOpeningObjectRuntime.ScoreGlobals score, OptionalInt drownTimerWord,
                                  Optional<TailValues> tail, Optional<CdLevelHud.BonusValues> bonus,
                                  Optional<CdAssetBytes> layoutRam, OptionalInt debugBlockWord, OptionalInt pauseMusicBit,
                                  CdOpeningObjectRuntime.DevGlobals retainedDev) {
        private LoadingGlobals {
            Objects.requireNonNull(live); Objects.requireNonNull(contact);
            Objects.requireNonNull(score); Objects.requireNonNull(drownTimerWord); Objects.requireNonNull(tail);
            Objects.requireNonNull(bonus); Objects.requireNonNull(layoutRam); Objects.requireNonNull(debugBlockWord);
            Objects.requireNonNull(pauseMusicBit); Objects.requireNonNull(retainedDev);
            if (pauseMusicBit.isPresent() && (pauseMusicBit.getAsInt() & ~0x80) != 0)
                throw new IllegalArgumentException("Only PauseMusic's bit7 belongs to this projection");
        }
        LoadingGlobals withLive(CdCheckpointLiveAux.State next) {
            return new LoadingGlobals(next, contact, score, drownTimerWord, tail, bonus, layoutRam, debugBlockWord, pauseMusicBit, retainedDev);
        }
        LoadingGlobals withDrownTimer(int word) {
            return new LoadingGlobals(live, contact, score, OptionalInt.of(word), tail, bonus, layoutRam, debugBlockWord, pauseMusicBit, retainedDev);
        }
        LoadingGlobals clearBonus() {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail,
                    Optional.of(new CdLevelHud.BonusValues(0, 0, 0)), layoutRam, debugBlockWord, pauseMusicBit, retainedDev);
        }
        LoadingGlobals clearDebugBlock() {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail, bonus, layoutRam, OptionalInt.of(0), pauseMusicBit, retainedDev);
        }
        LoadingGlobals withLayout(CdAssetBytes bytes) {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail, bonus, Optional.of(bytes), debugBlockWord, pauseMusicBit, retainedDev);
        }
        LoadingGlobals withPauseMusicBit(int bit) {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail, bonus, layoutRam,
                    debugBlockWord, OptionalInt.of(bit), retainedDev);
        }
        LoadingGlobals finishMainStores() {
            return new LoadingGlobals(live, new CdPlayerBody.ContactGlobals(0, 0, contact.combineRing()),
                    new CdOpeningObjectRuntime.ScoreGlobals(score.score(), score.nextLifeScore(), 0x80),
                    drownTimerWord, Optional.of(new TailValues(0, 0, 0, 0, 0x202F)), bonus, layoutRam, debugBlockWord, pauseMusicBit, retainedDev);
        }
    }

    /** Finite native DBF progress plus the selected display-enable output; neither is a clock. */
    private record FadeProgress(int completedSteps, boolean displayEnabled) {
        private FadeProgress {
            if (completedSteps < 0 || completedSteps > 22) throw new IllegalArgumentException("FromWhite DBF progress");
        }
    }

    /** Native Main223 F78E is distinct from Main309 F790. Knowledge follows actual stores. */
    private record LoopGlobals(OptionalInt demoDataIndexWord,
                               Optional<CdGlobalObjectAnimation.Extras> animations,
                               CdAssetBytes paletteSteps7, CdAssetBytes paletteTimers7,
                               Optional<CdObjectDrawQueue.CpuSat> completedDraw,
                               SonicCdPastDisplay.Selection selectedDisplay) {
        private LoopGlobals {
            Objects.requireNonNull(demoDataIndexWord); Objects.requireNonNull(animations);
            Objects.requireNonNull(paletteSteps7); Objects.requireNonNull(paletteTimers7);
            Objects.requireNonNull(completedDraw); Objects.requireNonNull(selectedDisplay);
            if (paletteSteps7.length() != 7 || paletteTimers7.length() != 7)
                throw new IllegalArgumentException("Native palette allocation has seven timer/step bytes");
            if (demoDataIndexWord.isPresent() && (demoDataIndexWord.getAsInt() & ~0xFFFF) != 0)
                throw new IllegalArgumentException("Native demo index is a WORD");
        }
    }

    /** All mutable loading values live here once; actor/shared/scroll remain in their existing owners. */
    private record Values(SonicCdGameModule.PastLoadPhase phase, CdR11bSectionArt.State section,
                          CdAssetBytes cpuPalette, CdAssetBytes fadePalette,
                          CdAssetBytes displayedPalette, CdAssetBytes cpuSat, CdAssetBytes displayedSat,
                          CdAssetBytes displayedHScroll, int displayedVScrollLongBits,
                          CdR11aBackgroundPlane.State foregroundPlane,
                          CdRingGlobals ring, CdLevelGlobals level, LoadingGlobals globals,
                          CdAssetBytes hudObjects, FadeProgress fadeProgress, List<SonicCdGameModule.PastLoadCall> calls, LoopGlobals loop) {
        private Values { calls = List.copyOf(calls); }
    }

    static final class Snapshot {
        private final SonicCdPastLoadingVisit owner;
        private final Values values;
        private final CdPlayerFrameRuntime.ActorState actor;
        private final CdPlayerFrameRuntime.SharedState shared;
        private final CdObjectPlacementRuntime.Snapshot placement;
        private final SonicCdPlayerPresentation.PastLoadFlags flags;
        private final List<SonicCdGameModule.PastSound> sounds;
        private Snapshot(SonicCdPastLoadingVisit owner, Values values,
                         CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared) {
            this.owner = owner; this.values = values; this.actor = actor; this.shared = shared;
            this.placement = owner.placement.capture();
            this.flags = owner.presentation.capturePastLoadFlags(); this.sounds = owner.sounds;
        }
    }

    SonicCdPastLoadingVisit(SonicCdGameModule module, CdR11bAssets assets, CdPlayerFrameRuntime frame,
                            SonicCdPlayerPresentation presentation, CdTimeWarpSaveData.State saved,
                            CdRingGlobals ring, CdLevelGlobals level, int sectionWord,
                            SonicCdPlayerPresentation.PastVideoInit video, CdCheckpointLiveAux.State live,
                            CdPlayerBody.ContactGlobals contact, CdOpeningObjectRuntime.ScoreGlobals score,
                            int displayedVScrollLongBits, CdAssetBytes savedObjectFlags, CdOpeningObjectRuntime.DevGlobals retainedDev) {
        this.module = Objects.requireNonNull(module); this.assets = Objects.requireNonNull(assets);
        this.frame = Objects.requireNonNull(frame); this.presentation = Objects.requireNonNull(presentation);
        this.saved = Objects.requireNonNull(saved);
        // MAINVARS FF1200..FF14FD survives RunMMD and the later clears. Data is
        // carried before A retirement; this is not a foreign Snapshot restore.
        placement = CdObjectPlacementRuntime.fromAssets(assets, savedObjectFlags);
        this.ring = CdRing.fromAssets(placement, assets);
        animal = new CdAnimal(placement);
        pataBata = new CdPataBata(placement, CdPataBata.Assets.fromPastAssets(assets), this::drawPataBata);
        primaryScanner = new CdPlayerObjectTouch(CdR11aObjectAssets.decodeTouchDimensions(
                assets.view(CdR11bAssets.Asset.TOUCH_DIMENSIONS).bytes().copy()));
        primaryResponse = new CdPlayerTouchResponse(assets.view(CdR11bAssets.Asset.ENEMY_POINTS).bytes());
        var sharedSolid = new CdSolidObject(CdPlayerMovement.fromStageSource(frame.stageSource()));
        var sharedTerrain = CdPlayerTerrain.fromStageTerrain(assets.terrain());
        tamabboh = CdTamabboh.fromPastAssets(placement, assets, sharedTerrain, this::commitTamabbohPrimaryAngle,
                this::readTamabbohPlayerWord, this::playTamabbohFm, this::drawTamabboh);
        spring = new CdSpring(placement, CdSpring.Assets.fromPastAssets(assets), new CdPlayerMath(assets.player()),
                sharedSolid, this::commitSpringSolid, this::commitSpringLaunch, this::drawSpring, this::playSpringFm);
        platform = new CdPlatform(placement, CdPlatform.Assets.fromPastAssets(assets), new CdPlayerMath(assets.player()),
                sharedTerrain, sharedSolid,
                this::commitPlatformSolid, this::commitPlatformPrimaryAngle, this::drawPlatform);
        floatBlock = CdFloatBlock.fromPastAssets(placement, assets, sharedSolid, this::commitFloatSolid, this::drawFloatBlock);
        tunnelDoor = CdTunnelDoor.withPrefixOwner(placement, this::readTunnelDoorSecondary,
                CdTunnelDoor.Assets.fromPastAssets(assets), sharedSolid, new CdTunnelDoor.PrefixSolidOwner() {
                    @Override public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut cut) {
                        return preflightTunnelDoorSolid(cut);
                    }
                    @Override public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall call) {
                        return commitTunnelDoorSolid(call);
                    }
                }, this::drawTunnelDoor, this::playTunnelDoorFm);
        var monitorAssets = CdMonitor.Assets.fromPastAssets(assets);
        monitor = CdMonitor.withPastOwner(placement, monitorAssets, sharedSolid,
                sharedTerrain, new CdMonitor.PrefixSolidOwner() {
                    @Override public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut cut) {
                        return preflightMonitorSolid(cut);
                    }
                    @Override public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall call) {
                        return commitMonitorSolid(call);
                    }
                }, this::commitMonitorPrimaryAngle, this::drawMonitor, this::playMonitorFm);
        timePost = CdTimePost.withPastOwner(placement, monitorAssets,
                this::commitTimePostDirection, this::playTimePostSubCpu, this::drawMonitor);
        art = CdR11bSectionArt.fromAssets(assets); foreground = CdR11bForegroundPlane.bind(assets);
        var paletteData = assets.view(CdR11bAssets.Asset.PALETTE_CYCLE_DATA).bytes();
        var cycles = new ArrayList<CdLevelPaletteCycle.Script>(6);
        for (int i = 0; i < 6; i++) cycles.add(new CdLevelPaletteCycle.Script(
                paletteData.slice(i * 14, 8), paletteData.slice(i * 14 + 8, 6)));
        paletteCycles = List.copyOf(cycles);
        hud = new CdLevelHud(CdLevelHud.Assets.fromAssets(assets), art);
        reservedHud = new CdReservedHud(CdReservedHud.Assets.fromAssets(assets), new CdReservedHud.Slots() {
            @Override public CdAssetBytes read(int wholeIndex) { return readReservedHud(wholeIndex); }
            @Override public void write(int wholeIndex, CdAssetBytes expected, CdAssetBytes next) {
                writeReservedHud(wholeIndex, expected, next);
            }
        }, this::drawReservedHud);
        // A real presentation bank, projected from InitVDP's already-executed native
        // clear. No Actor/pose/implicit DPLC is created to satisfy service admission.
        playerRenderer = new PlayerSpriteRenderer(CdPlayerVisualAdapter.artSet(frame.stageSource().sonic(), 0x10000),
                GameServices.graphics());
        restorePlayerBank();
        frame.bindArtPublication(this::publishPlayerArt);
        // RunMMD really cleared AA00/table and C800/art before Start. ClearPLCs executes at Main:57.
        var section = art.clearPlcs(art.fromValues(zero(512), video.currentPatterns(), sectionWord));
        byte[] white = new byte[128];
        for (int i = 0; i < white.length; i += 2) { white[i] = 0x0E; white[i + 1] = (byte) 0xEE; }
        var initialSelection = SonicCdPastDisplay.project(new SonicCdPastDisplay.Inputs(
                Optional.empty(), video.selection().displayedSat(), video.selection().displayedPalette(),
                zero(CdR11aBackgroundScroll.HSCROLL_RAM_BYTES), displayedVScrollLongBits,
                video.selection().displayEnabled(), null, null, null, null));
        values = new Values(FIRST_PLC, section, CdAssetBytes.copyOf(white), zero(128),
                video.selection().displayedPalette(), zero(CdObjectDrawQueue.SAT_BYTE_SIZE),
                video.selection().displayedSat(), zero(CdR11aBackgroundScroll.HSCROLL_RAM_BYTES),
                displayedVScrollLongBits, null,
                ring, new CdLevelGlobals(0, level.updateHudTime(), level.checkpoint(), level.spawnMode(),
                        level.plcLoadFlags() | 1, level.flowerCount(), level.fadeEnableDisplay(),
                        level.powerup(), level.timeOver()),
                // RunMMD covers the same four water bytes; lower MAINVARS time and its
                // partial knowledge survive. The helper supplies only that finite projection.
                new LoadingGlobals(CdCheckpointLiveAux.clearMiscAtLevelStart(live), contact, score, OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), retainedDev),
                zero(31 * 64), new FadeProgress(0, video.selection().displayEnabled()),
                List.of(LOAD_LEVEL_PCM, INIT_VDP, INIT_CONTROLLERS, NORMAL_LOAD), new LoopGlobals(OptionalInt.empty(), Optional.empty(), zero(7), zero(7), Optional.empty(), initialSelection));
    }

    private static CdAssetBytes zero(int size) { return CdAssetBytes.copyOf(new byte[size]); }
    boolean owns(CdR11bAssets source, CdPlayerFrameRuntime sourceFrame) {
        return assets == source && frame == sourceFrame;
    }
    private OptionalInt liveValue(CdCheckpointLiveAux.Field field) {
        try { return OptionalInt.of(CdCheckpointLiveAux.read(values.globals().live(), field)); }
        catch (CdCheckpointLiveAux.UnknownLiveByte unknown) { return OptionalInt.empty(); }
    }
    private void requireCurrent() {
        module.requirePastLoadingVisit(this);
        if (servicing || loadingSaved || runningObjects || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || drawQueue != null)
            throw new IllegalStateException("Past loading operation or pending object pass is active");
        frame.captureShared();
    }
    private void requireCallback() {
        module.requirePastLoadingVisit(this);
        if (!servicing || loadingSaved || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat) throw new IllegalStateException("No actual Past selectorC callback");
    }
    private void call(SonicCdGameModule.PastLoadCall call) {
        var calls = new ArrayList<>(values.calls()); calls.add(call);
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(),
                values.displayedPalette(), values.cpuSat(), values.displayedSat(), values.displayedHScroll(),
                values.foregroundPlane(), values.ring(), values.level(), values.globals(), values.hudObjects(), calls);
    }
    private void replace(SonicCdGameModule.PastLoadPhase phase, CdR11bSectionArt.State section,
                         CdAssetBytes cpuPalette, CdAssetBytes fadePalette, CdAssetBytes displayedPalette,
                         CdAssetBytes cpuSat, CdAssetBytes displayedSat, CdAssetBytes displayedHScroll,
                         CdR11aBackgroundPlane.State fg, CdRingGlobals ring, CdLevelGlobals level,
                         LoadingGlobals globals, CdAssetBytes hud, List<SonicCdGameModule.PastLoadCall> calls) {
        values = new Values(phase, section, cpuPalette, fadePalette, displayedPalette, cpuSat,
                displayedSat, displayedHScroll, values.displayedVScrollLongBits(), fg, ring, level, globals, hud, values.fadeProgress(), calls, values.loop());
    }
    private void globals(UnaryOperator<LoadingGlobals> operation) {
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), operation.apply(values.globals()), values.hudObjects(), values.calls());
    }
    private void section(UnaryOperator<CdR11bSectionArt.State> operation) {
        try { section(operation.apply(values.section())); }
        catch (CdR11bSectionArt.SectionArtException stopped) { section(stopped.state()); throw stopped; }
    }
    private void section(CdR11bSectionArt.State next) {
        replace(values.phase(), next, values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
    }
    private void phase(SonicCdGameModule.PastLoadPhase phase) {
        replace(phase, values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
    }

    void service(int held) {
        presentation.requirePastRenderIdle();
        requireCurrent();
        if (values.phase() == ANIMATED_TILES) throw module.pastLoadingBoundary(
                SonicCdGameModule.PastLoadBoundary.ANIMATED_TILES);
        if (values.phase() == BEGIN_LEVEL) { serviceLevel(held); return; }
        if (values.phase() == FROM_WHITE) { serviceFromWhite(held); return; }
        presentation.validatePastLevelLoadPlane();
        var selectedCpu = values.loop().completedDraw();
        servicing = true;
        try { frame.serviceLevelLoad(held, this::publish, this::copyAndDraw, this::fastPlc); }
        finally { servicing = false; }
        representedServiceCompleted();
        call(LEVEL_SERVICE);
        section(art::prepare); call(PROCESS_PLCS);
        // A first empty queue still passed through exactly one actual selectorC above.
        if (values.section().queue().headSource() == 0 && values.section().remainingTilesWord() == 0) {
            if (values.phase() == FIRST_PLC) mainLoadPrefix();
            else finishSectionWait();
        }
        // Loading continuations can really change physical planes/art after the V-int.
        // Retain that completed image with the CPU reference borrowed before this service.
        settleSelectedDisplay(selectedCpu);
    }

    private void restorePlayerBank() {
        PlayerResidentBanks.claim(playerRenderer);
        PlayerResidentBanks.restore(playerRenderer, new PlayerResidentBanks.Snapshot(
                CdPlayerVisualAdapter.residentPatterns(frame.artState()), -1));
        playerRenderer.ensureCached(GameServices.graphics());
    }
    private void publishPlayerArt(CdPlayerArtRuntime.DmaRequest request, CdPlayerArtRuntime.State complete) {
        requireCallback();
        if (values.phase() != BEGIN_LEVEL || complete != frame.artState())
            throw new IllegalStateException("Player art publication is outside the actual Past service");
        // The Frame already adopted its exact native DMA prefix, including tile24's
        // retained tail. Publish that same value; no cue selection or gameplay replay.
        restorePlayerBank();
    }
    private void serviceLevel(int held) {
        presentation.validatePastLevelLoadPlane();
        // Completed-call observations are per ordinary service, not an unbounded
        // gameplay history or a source of execution authority.
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), List.of());
        // VInterrupt's selected VScroll precedes the actual controller read. Root
        // has preflighted the real source/World and the already-claimed art bank.
        var bg = presentation.pastBackgroundImage();
        values = new Values(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(),
                values.displayedPalette(), values.cpuSat(), values.displayedSat(), values.displayedHScroll(), bg.vscroll(),
                values.foregroundPlane(), values.ring(), values.level(), values.globals(), values.hudObjects(),
                values.fadeProgress(), values.calls(), values.loop());
        sounds = List.of(); // Current call requests, not an unbounded sound history.
        var selectedCpu = values.loop().completedDraw();
        var selected = frame.prepareInput(held);
        boolean[] published = {false};
        servicing = true;
        try { frame.serviceVBlank(selected.phase(), () -> { publishLevel(); published[0] = true; },
                this::serviceLevelArtAndHud, this::mainLevelMusic); }
        finally { servicing = false; }
        // This is after the actual art/plane/slow-PLC/HUD service, before Main can
        // replace CPU SAT or update scroll/palette. A real pause still selects here.
        if (published[0]) settleSelectedDisplay(selectedCpu);
        if (frame.paused()) return; // Main's pause branch precedes the native +1/placement.
        int levelFrame = frame.advanceLevelMainFrame(); call(LEVEL_FRAME_INCREMENT);
        // Native Main402 before any physical object call: the actual pre-scroll
        // camera and raw current period, with common-list order and carried bank.
        placement.spawnObjects(presentation.pastForegroundImage().cameraX(), frame.environment().movement().gates().timeZone());
        call(SPAWN_OBJECTS);
        runObjects(levelFrame);
    }

    /**
     * The queue is pending RunObjects output, never a second completed CPU image.
     * Until the dynamic walk and camera are owned, every exit closes admission
     * with its native pointer prefix retained and no finishAfterCamera call.
     */
    private void runObjects(int levelFrame) {
        if (runningObjects || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || drawQueue != null)
            throw new IllegalStateException("Past object pass cannot be repeated or reentered");
        runningObjects = true;
        try {
            drawQueue = createReservedDrawQueue();
            drawQueue.beginPass();
            var primary = frame.state();
            if (primary.header().slotId() != 0) {
                if (primary.header().slotId() == 1 && primary.motion().routine() == 2) {
                    runPrimaryMain(levelFrame); call(PRIMARY_MAIN);
                } else {
                if (primary.header().slotId() != 1 || primary.motion().routine() != 0)
                    throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.PRIMARY_BODY);
                // Existing B-bound Frame owns Init, its prior-charge operation and
                // final adoption once. No extra input/service, art cue or DrawObject.
                var publication = frame.runBody(levelFrame);
                for (var sound : publication.sounds()) appendSound(new SonicCdGameModule.PastFrameSound(sound));
                call(PRIMARY_INIT);
                }
            }
            // RunObjects reads every physical occupant anew, including ID zero.
            for (int whole = 1; whole < 32; whole++) {
                int id = reservedImage(whole).unsignedByte(0);
                if (id == 0) continue;
                if ((whole != 2 && whole != 3 && whole != 5 && whole != 31)
                        || (id != 0x1C && id != 0x19))
                    throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.RESERVED_HUD);
                runReservedHud(whole);
            }
            call(RESERVED_OBJECTS);
            for (int whole = 32; whole < 128; whole++) {
                int id = placement.pool().state(whole).id();
                if (id == 0) continue;
                if (id == 0x10) {
                    runRing(whole); call(RING_BODY);
                } else if (id == CdFloatBlock.OBJECT_ID) {
                    runFloatBlock(whole); call(FLOAT_BLOCK_BODY);
                } else if (id == CdAnimal.OBJECT_ID) {
                    runAnimal(whole); call(ANIMAL_PRESENCE);
                } else if (id == CdPataBata.OBJECT_ID) {
                    runPataBata(whole); call(PATA_BATA_BODY);
                } else if (id == CdTamabboh.OBJECT_ID) {
                    runTamabboh(whole); call(TAMABBOH_BODY);
                } else if (id == CdSpring.OBJECT_ID) {
                    runSpring(whole); call(SPRING_BODY);
                } else if (id == CdPlatform.OBJECT_ID) {
                    runPlatform(whole); call(PLATFORM_BODY);
                } else if (id == CdTunnelDoor.OBJECT_ID) {
                    runTunnelDoor(whole); call(TUNNEL_DOOR_BODY);
                } else if (id == CdMonitor.OBJECT_ID) {
                    boolean post = placement.pool().state(whole).subtype() >= 8;
                    runMonitor(whole); call(post ? TIME_POST_BODY : MONITOR_BODY);
                } else {
                    throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.DYNAMIC_OBJECTS);
                }
            }
            call(DYNAMIC_OBJECTS);
            finishOrdinaryMain();
        } catch (RuntimeException | Error failure) {
            try { if (drawQueue != null) drawQueue.close(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        } finally {
            runningObjects = false;
        }
    }

    /**
     * One genuine routine-2 primary invocation. Frame retains the player algorithms and
     * final actor adoption; this scope owns only the borrowed world cut and external outputs.
     * Native ObjSonic_Main (Sonic/Main.asm:285-351), before the remaining physical slots.
     */
    private final class PrimaryInvocation {
        private final SonicCdPastLoadingVisit owner = SonicCdPastLoadingVisit.this;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final CdPlayerBody.State originalState = frame.state();
        private final CdPlayerBody.Environment originalEnvironment = frame.environment();
        private final Optional<CdPlayerBody.DisplayPublication> originalDisplay = frame.display();
        private final CdPlayerArtRuntime.State originalArt = frame.artState();
        private final CdPlayerFrameRuntime.VIntClock originalClock = frame.vIntClock();
        private final boolean originalPaused = frame.paused();
        private final CdR11aForegroundScroll.State foreground = presentation.pastForegroundImage();
        private final CdR11aBackgroundScroll.State background = presentation.pastBackgroundImage();
        private final CdR11aBackgroundPlane.State plane = presentation.pastPlaneImage();
        private final SonicCdPlayerPresentation.DrawCopies copies = presentation.pastDrawCopies();
        private final CdObjectDrawQueue.CameraWords oldCamera =
                new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        private final CdObjectPlacementRuntime.Snapshot placed = placement.capture();
        private final Values base = values;
        private final List<SonicCdGameModule.PastSound> originalSounds = sounds;
        private CdPlayerBody.State expectedState = originalState;
        private CdPlayerBody.Environment expectedEnvironment = originalEnvironment;
        private Optional<CdPlayerBody.DisplayPublication> expectedDisplay = originalDisplay;
        private CdPlayerFrameRuntime.ActorState expectedActor;
        private CdPlayerFrameRuntime.SharedState expectedShared;
        private CdObjectPool.Snapshot expectedPool = placed.pool();
        private Values expectedValues = base;
        private List<SonicCdGameModule.PastSound> expectedSounds = originalSounds;
        private Optional<CdPlayerBody.DisplayEligibility> displayCut = Optional.empty();
        private boolean enqueued;
        private boolean drawing;
        private boolean cleared;

        private PrimaryInvocation() {
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
        }
    }

    private record PrimaryReady(CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                                CdObjectPool.Snapshot pool, Values values,
                                List<SonicCdGameModule.PastSound> sounds,
                                Optional<CdPlayerBody.DisplayEligibility> display) { }

    private void runPrimaryMain(int levelFrame) {
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual Past primary Main entry");
        var invocation = new PrimaryInvocation();
        activePrimary = invocation;
        try {
            verifyPrimary(invocation);
            var v = invocation.base;
            var response = new CdPlayerBody.ResponseContext(primaryResponse, v.globals().contact(),
                    new CdPlayerTouchResponse.WorldGlobals(v.ring().rings(), v.ring().lives(),
                            v.ring().updateHudLives(), v.globals().score().score(),
                            v.globals().score().nextLifeScore(), v.globals().score().updateHudScore()));
            // The SAME real post-SpawnObjects 96-slot cut supplies every borrowed consumer.
            var cut = invocation.placed.pool();
            var player = invocation.originalState;
            var support = new CdPlayerMovement.ObjectContext(cut, player.objectLinks().standObject(),
                    player.header().slotId(), player.header().width());
            // The HUD image has one existing owner. No raw-bank import or save-lock owner
            // exists here; absent reads retain TimeWarp's reached, typed native boundary.
            var warp = new CdPlayerTimeWarp.Context(OptionalInt.of(v.level().spawnMode()),
                    Optional.empty(), Optional.empty(), OptionalInt.of(v.level().levelRestart()));
            var publication = frame.runBody(levelFrame,
                    Optional.of(new CdPlayerBody.TouchContext(primaryScanner, cut, response)),
                    Optional.of(new CdPlayerBody.LevelContext(cut, v.ring(), v.level())),
                    Optional.of(support), Optional.of(new CdPlayerBody.TimeWarpContext(warp, v.globals().contact())));
            // Only the successful Frame call can advance these expected values. Before
            // it returns, entitlement forbids captures; the raw preimages above are legal.
            invocation.expectedState = publication.result().state();
            invocation.expectedEnvironment = publication.result().environment();
            invocation.expectedDisplay = publication.result().display();
            var ready = preparePrimary(invocation, publication);
            verifyPrimary(invocation);
            // All validation, overlays, immutable Values and sounds precede the first
            // live external write. Frame already adopted its own complete result once.
            invocation.pool.restore(ready.pool());
            values = ready.values(); sounds = ready.sounds();
            invocation.expectedPool = ready.pool();
            invocation.expectedValues = ready.values(); invocation.expectedSounds = ready.sounds();
            invocation.expectedActor = ready.actor(); invocation.expectedShared = ready.shared();
            invocation.displayCut = ready.display();
            verifyPrimary(invocation);
            if (ready.display().isPresent() && ready.display().orElseThrow().eligible())
                enqueuePrimary(invocation, ready.display().orElseThrow());
            verifyPrimary(invocation);
        } catch (RuntimeException | Error failure) {
            // Failed Body has no result/external adoption and spends its entitlement.
            // A later failure retains the genuinely reached Frame/queue prefix; no rollback.
            try { verifyPrimary(invocation); }
            catch (RuntimeException | Error verification) {
                if (verification != failure) failure.addSuppressed(verification);
            }
            throw failure;
        } finally { activePrimary = null; }
    }

    private PrimaryReady preparePrimary(PrimaryInvocation invocation, CdPlayerFrameRuntime.BodyPublication publication) {
        verifyPrimary(invocation);
        var result = publication.result();
        if (result.boundary() != CdPlayerBody.Boundary.MAIN_RETURN
                && result.boundary() != CdPlayerBody.Boundary.CONTROLLED_MAIN_RETURN)
            throw new IllegalStateException("Routine-2 primary returned a different native body");
        // These are actual closed post-Body captures, never fabricated pre-Body tokens.
        var actor = frame.captureActor(); var shared = frame.captureShared();
        if (actor.state() != result.state() || actor.display() != result.display()
                || shared.environment() != result.environment() || shared.artState() != invocation.originalArt
                || shared.vIntClock() != invocation.originalClock || shared.paused() != invocation.originalPaused)
            throw new IllegalStateException("Frame did not adopt exactly its completed primary result");
        invocation.expectedActor = actor; invocation.expectedShared = shared;
        var changed = new LinkedHashMap<Integer, CdObjectState>();
        var old = invocation.base;
        var nextRing = old.ring(); var nextLevel = old.level();
        var nextScore = old.globals().score(); var nextContact = old.globals().contact();
        Optional<CdPlayerBody.DisplayEligibility> display = Optional.empty();
        boolean touchSeen = false, warpSeen = false;
        var effects = result.effects();
        for (int at = 0; at < effects.size(); at++) {
            var effect = effects.get(at);
            if (effect instanceof CdPlayerBody.ModeEffect mode
                    && mode.effect() instanceof CdPlayerMovement.ObjectPatch write) {
                var patch = write.patch();
                int whole = patch.before().identity().wholeIndex();
                // overlay is also the accessible same-owner/full-identity preflight.
                CdObjectPool.overlay(invocation.placed.pool(), List.of(patch.before()));
                CdObjectPool.overlay(invocation.placed.pool(), List.of(patch.after()));
                var before = changed.getOrDefault(whole,
                        invocation.placed.pool().slots().get(patch.before().identity().dynamicIndex()));
                if (!patch.before().identity().equals(patch.after().identity()) || !before.equals(patch.before()))
                    throw new IllegalStateException("Stale ordered primary MODE object image");
                changed.put(whole, patch.after());
            } else if (effect instanceof CdPlayerBody.TimeWarpCall warp) {
                if (warpSeen) throw new IllegalStateException("Primary returned multiple TimeWarp calls");
                warpSeen = true;
                var warped = warp.result();
                if (warped.saveResult().isPresent() || !warped.reservedPatches().isEmpty())
                    throw new IllegalStateException("Primary produced an output for an absent warp owner");
                nextContact = new CdPlayerBody.ContactGlobals(warped.state().timeWarp(),
                        nextContact.shield(), nextContact.combineRing());
                if (warped.restartResult().isPresent()) {
                    var restart = warped.restartResult().orElseThrow();
                    if (restart.expectedRestartWord() != nextLevel.levelRestart()
                            || restart.finalRestartWord() != ((nextLevel.levelRestart() & 255) | 0x100)
                            || warped.state().timer() < 230 || !warped.stores().isEmpty() || at < 2
                            || !(effects.get(at - 2) instanceof CdPlayerBody.LevelWrite word)
                            || word.field() != CdPlayerBody.LevelField.LEVEL_RESTART
                            || word.value() != restart.finalRestartWord()
                            || !(effects.get(at - 1) instanceof CdPlayerBody.SoundRequest sound)
                            || sound.sound() != CdPlayerBody.Sound.SCMD_FADECDA)
                        throw new IllegalStateException("Primary TimeWarp restart word/fade source order differs");
                    var l = nextLevel;
                    nextLevel = new CdLevelGlobals(restart.finalRestartWord(), l.updateHudTime(), l.checkpoint(),
                            l.spawnMode(), l.plcLoadFlags(), l.flowerCount(), l.fadeEnableDisplay(), l.powerup(), l.timeOver());
                }
            } else if (effect instanceof CdPlayerBody.DisplayEligibility selected) {
                if (display.isPresent()) throw new IllegalStateException("Primary returned multiple Display cuts");
                display = Optional.of(selected); // Retain false as a real skipped DrawObject.
            } else if (effect instanceof CdPlayerBody.ObjectTouchResult touch) {
                if (touchSeen) throw new IllegalStateException("Primary returned multiple touch results");
                touchSeen = true;
                touch.result().changedSlot().ifPresent(image -> changed.put(image.identity().wholeIndex(), image));
            } else if (effect instanceof CdPlayerBody.ObjectTouchResponse touch) {
                if (touchSeen) throw new IllegalStateException("Primary returned multiple touch results");
                touchSeen = true;
                var response = touch.result(); var world = response.world(); var r = nextRing;
                nextRing = new CdRingGlobals(world.rings(), world.lives(), r.livesFlags(), r.updateHudRings(),
                        world.updateHudLives(), r.timeStopTimer(), r.ringAnimationTimer(), r.ringAnimationFrame());
                nextScore = new CdOpeningObjectRuntime.ScoreGlobals(world.score(), world.nextLifeScore(), world.updateHudScore());
                nextContact = touch.contact();
                // Later touch images supersede earlier MODE images for the same physical slot.
                for (var image : response.changedSlots()) changed.put(image.identity().wholeIndex(), image);
            } else if (effect instanceof CdPlayerBody.DeadCheckGone || effect instanceof CdPlayerBody.RestartCut
                    || effect instanceof CdPlayerBody.ResetSavedObjectFlagsRequest
                    || effect instanceof CdPlayerBody.UncheckedAliasWrite) {
                throw new IllegalStateException("Primary Main returned another routine's external publication");
            }
        }
        if (display.isEmpty()) throw new IllegalStateException("Completed primary Main omitted its Display cut");
        var pool = CdObjectPool.overlay(invocation.placed.pool(), List.copyOf(changed.values()));
        invocation.pool.validateRestore(pool);
        var g = old.globals();
        var globals = new LoadingGlobals(g.live(), nextContact, nextScore, g.drownTimerWord(), g.tail(),
                g.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev());
        var readyValues = new Values(old.phase(), old.section(), old.cpuPalette(), old.fadePalette(), old.displayedPalette(),
                old.cpuSat(), old.displayedSat(), old.displayedHScroll(), old.displayedVScrollLongBits(),
                old.foregroundPlane(), nextRing, nextLevel, globals, old.hudObjects(), old.fadeProgress(), old.calls(), old.loop());
        var ledger = new ArrayList<SonicCdGameModule.PastSound>(invocation.originalSounds);
        for (var sound : publication.sounds()) ledger.add(new SonicCdGameModule.PastFrameSound(sound));
        var readySounds = List.copyOf(ledger);
        verifyPrimary(invocation);
        return new PrimaryReady(actor, shared, pool, readyValues, readySounds, display);
    }

    private void verifyPrimary(PrimaryInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        var now = placement.capture();
        if (invocation == null || activePrimary != invocation || invocation.owner != this
                || !runningObjects || servicing || loadingSaved || activeDrain != null || activeReserved != null
                || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null
                || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort
                || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || frame.closed() || frame.state() != invocation.expectedState
                || frame.environment() != invocation.expectedEnvironment || frame.display() != invocation.expectedDisplay
                || frame.artState() != invocation.originalArt || frame.vIntClock() != invocation.originalClock
                || frame.paused() != invocation.originalPaused
                || presentation.pastForegroundImage() != invocation.foreground
                || presentation.pastBackgroundImage() != invocation.background
                || presentation.pastPlaneImage() != invocation.plane || presentation.pastDrawCopies() != invocation.copies
                || !now.status().equals(invocation.placed.status()) || !now.pool().slots().equals(invocation.expectedPool.slots())
                || !sameBytes(now.savedFlags(), invocation.placed.savedFlags())
                || values != invocation.expectedValues || sounds != invocation.expectedSounds
                || !sameBytes(values.hudObjects(), invocation.base.hudObjects()))
            throw new IllegalStateException("Primary source/World/Frame/full96/Values/scroll cut changed");
        if (invocation.expectedActor != null && (!frame.captureActor().equals(invocation.expectedActor)
                || !frame.captureShared().equals(invocation.expectedShared)))
            throw new IllegalStateException("Primary closed Frame differs from its actual expected complete capture");
    }

    private void enqueuePrimary(PrimaryInvocation invocation, CdPlayerBody.DisplayEligibility cut) {
        verifyPrimary(invocation);
        if (!cut.eligible() || invocation.displayCut.orElse(null) != cut || invocation.enqueued
                || invocation.drawing || invocation.cleared || queuePort)
            throw new IllegalStateException("Primary DrawObject lacks its actual once-only eligible Display cut");
        invocation.enqueued = true; invocation.drawing = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(0),
                    cut.spriteFlags(), cut.centreXWord(), cut.centreYWord(), cut.yRadius(), cut.width(), cut.priority()),
                    invocation.oldCamera);
            if (!invocation.cleared) throw new IllegalStateException("Actual primary DrawObject omitted its entry bit clear");
            verifyPrimary(invocation);
        } finally { invocation.drawing = false; }
    }

    private void writePrimaryOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean value) {
        var invocation = activePrimary;
        verifyPrimary(invocation);
        if (ref == null || ref.wholeIndex() != 0 || value || queuePort || !invocation.drawing
                || !invocation.enqueued || invocation.cleared || invocation.expectedActor == null)
            throw new IllegalStateException("Only actual collecting primary DrawObject may clear bit7 once");
        var ready = primaryScreenActor(invocation.expectedActor, false);
        queuePort = true;
        try {
            verifyPrimary(invocation);
            frame.publishDrawObjectOnScreen(false);
            var actual = frame.captureActor();
            if (!actual.equals(ready)) throw new IllegalStateException("Primary DrawObject changed more than complete-actor bit7");
            invocation.expectedActor = ready; invocation.expectedState = actual.state(); invocation.expectedDisplay = actual.display();
            invocation.cleared = true;
            verifyPrimary(invocation);
        } finally { queuePort = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot primaryLiveSlot() {
        if (activeDrain == null || !queuePort || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.DRAINING)
            throw new IllegalStateException("Primary live pointer dereference belongs only to actual DrawObjects");
        var state = frame.state(); var motion = state.motion(); var header = state.header();
        // Native oYScr WORD $0A aliases X's fraction, not the world-Y fraction.
        return new CdObjectDrawQueue.LiveDrawSlot(header.slotId(), motion.spriteFlags(),
                motion.centreX16_16() >>> 16, motion.centreY16_16() >>> 16, motion.centreX16_16() & 0xFFFF,
                header.mappingAddress(), state.animation().mapFrame(), header.tile());
    }

    /** Exact complete-actor expectation for the sole bit7 publication port; never an owner restore. */
    private static CdPlayerFrameRuntime.ActorState primaryScreenActor(CdPlayerFrameRuntime.ActorState before, boolean value) {
        var state = before.state(); var m = state.motion(); int bit = value ? 0x80 : 0;
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), (m.spriteFlags() & 0x7F) | bit, m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var next = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(), state.history(),
                state.rotationDistance(), state.objectLinks(), state.rotationAngle());
        var display = before.display().map(p -> new CdPlayerBody.DisplayPublication(p.queued(), p.mappingAddress(), p.tile(),
                p.priority(), p.width(), p.mapFrame(), (p.spriteFlags() & 0x7F) | bit, p.centreX16_16(), p.centreY16_16()));
        return new CdPlayerFrameRuntime.ActorState(before.owner(), next, display);
    }

    private void loop(LoopGlobals next) {
        var v = values;
        values = new Values(v.phase(), v.section(), v.cpuPalette(), v.fadePalette(), v.displayedPalette(),
                v.cpuSat(), v.displayedSat(), v.displayedHScroll(), v.displayedVScrollLongBits(),
                v.foregroundPlane(), v.ring(), v.level(), v.globals(), v.hudObjects(), v.fadeProgress(), v.calls(), next);
    }

    /** Pure projection at a completed source service cut; never calls Frame capture or native work. */
    private void settleSelectedDisplay(Optional<CdObjectDrawQueue.CpuSat> selectedCpu) {
        var old = values.loop();
        boolean enabled = values.fadeProgress().displayEnabled();
        var inputs = new SonicCdPastDisplay.Inputs(selectedCpu, values.displayedSat(), values.displayedPalette(),
                values.displayedHScroll(), values.displayedVScrollLongBits(), enabled,
                enabled ? values.foregroundPlane() : null, enabled ? presentation.pastPlaneImage() : null,
                enabled ? values.section().residency() : null, enabled ? frame.artState() : null);
        // Expected current/geometry knowledge gaps remain immutable render-only Blocked values.
        var selected = SonicCdPastDisplay.project(inputs);
        loop(new LoopGlobals(old.demoDataIndexWord(), old.animations(), old.paletteSteps7(), old.paletteTimers7(),
                old.completedDraw(), selected));
    }

    SonicCdPastDisplay.Selection selectedDisplay() {
        requireCurrent();
        return values.loop().selectedDisplay();
    }

    /** Native Main405-432. Only the actual completed physical pass can reach this continuation. */
    private void finishOrdinaryMain() {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activeDrain != null || activePrimary != null || activeReserved != null
                || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null
                || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor
                || monitorPort || drawingMonitor || queuePort || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || values.calls().isEmpty() || values.calls().getLast() != DYNAMIC_OBJECTS)
            throw new IllegalStateException("No completed actual Past physical pass");
        int demo = values.loop().demoDataIndexWord().orElseThrow(
                () -> module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.LEVEL_FLOW));
        if (demo == 0x0800 || values.level().levelRestart() != 0)
            throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.LEVEL_RESTART);
        call(POST_OBJECT_GUARDS);
        CdObjectDrawQueue.DrawCameras cameras;
        if (frame.environment().globals().debugMode() == 0 && frame.state().motion().routine() >= 6) {
            cameras = presentation.freezePastCameraAfterObjects(frame); call(DEAD_CAMERA_FREEZE);
        } else {
            cameras = presentation.continuePastCameraAfterObjects(frame, values.level().updateHudTime()); call(LEVEL_SCROLL);
        }
        var invocation = new DrainInvocation();
        activeDrain = invocation;
        CdObjectDrawQueue.CpuSat completed;
        try {
            completed = drawQueue.finishAfterCamera(cameras);
            verifyDrain(invocation);
        } catch (RuntimeException | Error failure) {
            try { verifyDrain(invocation); }
            catch (RuntimeException | Error verification) { if (verification != failure) failure.addSuppressed(verification); }
            throw failure;
        } finally { activeDrain = null; }
        var oldLoop = values.loop();
        var readyLoop = new LoopGlobals(oldLoop.demoDataIndexWord(), oldLoop.animations(), oldLoop.paletteSteps7(),
                oldLoop.paletteTimers7(), Optional.of(completed), oldLoop.selectedDisplay());
        var readyCpu = CdAssetBytes.copyOf(completed.bytes());
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                readyCpu, values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), values.ring(),
                values.level(), values.globals(), values.hudObjects(), values.calls());
        loop(readyLoop); drawQueue.close(); drawQueue = null; call(DRAW_OBJECTS);
        if (values.ring().timeStopTimer() == 0) {
            cyclePalette(); call(PALETTE_CYCLE);
        }
        section(s -> art.updateSection(s, presentation.pastForegroundImage().cameraX())); call(UPDATE_SECTION);
        // ProcessPLCs prepares the next native job; tile service belongs to a later V-int.
        section(art::prepare); call(PREPARE_PLCS);
        var extras = values.loop().animations().orElseThrow(
                () -> module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.GLOBAL_ANIMATIONS));
        var animated = CdGlobalObjectAnimation.step(extras, values.ring());
        var animationLoop = values.loop();
        var readyAnimationLoop = new LoopGlobals(animationLoop.demoDataIndexWord(), Optional.of(animated.extras()),
                animationLoop.paletteSteps7(), animationLoop.paletteTimers7(), animationLoop.completedDraw(), animationLoop.selectedDisplay());
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), animated.ring(),
                values.level(), values.globals(), values.hudObjects(), values.calls());
        loop(readyAnimationLoop); call(UPDATE_GLOBAL_ANIMATIONS); call(ORDINARY_MAIN_RETURN);
    }

    private void cyclePalette() {
        var old = values.loop();
        var result = CdLevelPaletteCycle.pass(paletteCycles, new CdLevelPaletteCycle.State(values.cpuPalette(),
                old.paletteTimers7().slice(0, 6), old.paletteSteps7().slice(0, 6)));
        byte[] timers = old.paletteTimers7().copy(), steps = old.paletteSteps7().copy();
        System.arraycopy(result.state().timers().copy(), 0, timers, 0, 6);
        System.arraycopy(result.state().steps().copy(), 0, steps, 0, 6);
        var readyLoop = new LoopGlobals(old.demoDataIndexWord(), old.animations(), CdAssetBytes.copyOf(steps),
                CdAssetBytes.copyOf(timers), old.completedDraw(), old.selectedDisplay());
        // Adopt every reached palette/timer store, including a finite source fault prefix.
        replace(values.phase(), values.section(), result.state().palette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), values.ring(),
                values.level(), values.globals(), values.hudObjects(), values.calls());
        loop(readyLoop);
        if (!result.completed()) throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.PALETTE_CYCLE);
    }

    /** One private DrawObjects scope, never captured and never granted by public slot values. */
    private final class DrainInvocation {
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final Object world;
        private final long generation;
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.ActorState expectedActor = actor;
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectPlacementRuntime.Snapshot placed = placement.capture();
        private CdObjectPool.Snapshot expectedPool = placed.pool();
        private Values expectedValues = values;
        private final List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private DrainInvocation() {
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
        }
    }

    private void verifyDrain(DrainInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        var actual = placement.capture();
        if (!runningObjects || servicing || loadingSaved || invocation == null || activeDrain != invocation
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort
                || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort
                || drawingTunnelDoor || monitorPort || drawingMonitor || invocation.queue != drawQueue
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.expectedActor) || !frame.captureShared().equals(invocation.shared)
                || !presentation.samePastScroll(invocation.scroll) || values != invocation.expectedValues
                || sounds != invocation.expectedSounds || !actual.status().equals(invocation.placed.status())
                || !actual.pool().slots().equals(invocation.expectedPool.slots())
                || !sameBytes(actual.savedFlags(), invocation.placed.savedFlags()))
            throw new IllegalStateException("DrawObjects changed another owner or stale full native image");
    }

    private DrainInvocation requireDrainingSlot(CdObjectDrawQueue.CdSlotRef ref) {
        Objects.requireNonNull(ref);
        var invocation = activeDrain; verifyDrain(invocation);
        if (queuePort || drawQueue.phase() != CdObjectDrawQueue.Phase.DRAINING)
            throw new IllegalStateException("No actual queue-draining port");
        return invocation;
    }

    private CdObjectDrawQueue.LiveDrawSlot readDrainingSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireDrainingSlot(ref);
        queuePort = true;
        try {
            int whole = ref.wholeIndex();
            if (whole == 0) {
                var live = primaryLiveSlot(); verifyDrain(invocation); return live;
            }
            CdAssetBytes image = whole < 32 ? reservedImage(whole) : CdAssetBytes.copyOf(placement.pool().state(whole).copyBytes());
            var live = new CdObjectDrawQueue.LiveDrawSlot(image.unsignedByte(0), image.unsignedByte(1),
                    image.unsignedWord(8), image.unsignedWord(0x0C), image.unsignedWord(0x0A),
                    (int) image.unsignedLong(4), image.unsignedByte(0x1A), image.unsignedWord(2));
            verifyDrain(invocation); return live;
        } finally { queuePort = false; }
    }

    private void writeDrainingOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireDrainingSlot(ref);
        if (!onScreen) throw new IllegalStateException("DrawObjects only sets a loaded occurrence's bit7");
        queuePort = true;
        try {
            int whole = ref.wholeIndex();
            if (whole == 0) {
                var readyActor = primaryScreenActor(invocation.expectedActor, true);
                verifyDrain(invocation);
                frame.publishDrawObjectOnScreen(true);
                invocation.expectedActor = readyActor;
            } else if (whole < 32) {
                byte[] hud = values.hudObjects().copy();
                hud[(whole - 1) * 64 + 1] |= (byte) 0x80;
                var ready = reservedValues(values, CdAssetBytes.copyOf(hud));
                verifyDrain(invocation); values = ready; invocation.expectedValues = ready;
            } else {
                var source = placement.pool().state(whole);
                var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() | 0x80);
                var ready = CdObjectPool.overlay(invocation.expectedPool, List.of(next));
                placement.pool().validateRestore(ready); verifyDrain(invocation);
                placement.pool().write(next); invocation.expectedPool = ready;
            }
            verifyDrain(invocation);
        } finally { queuePort = false; }
    }

    private CdObjectDrawQueue createReservedDrawQueue() {
        var hudView = assets.view(CdR11bAssets.Asset.MAP_SPR_HUD);
        var monitorView = assets.view(CdR11bAssets.Asset.MAP_SPR_MONITOR_TIME);
        var ringView = assets.view(CdR11bAssets.Asset.MAP_SPR_RING);
        var springView = assets.view(CdR11bAssets.Asset.MAP_SPRING);
        var platformView = assets.view(CdR11bAssets.Asset.PLATFORM_MAPPINGS);
        var floatView = assets.view(CdR11bAssets.Asset.MAP_SPR_FLOAT_BLOCK);
        var pataNormalView = assets.view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA1);
        var pataDamagedView = assets.view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA2);
        var tamabbohView = assets.view(CdR11bAssets.Asset.MAP_SPR_TAMABBOH);
        var tamabbohMissileView = assets.view(CdR11bAssets.Asset.MAP_SPR_TAMABBOH_MISSILE);
        var tunnelDoorView = assets.view(CdR11bAssets.Asset.MAP_SPR_TUNNEL_DOOR);
        var sonicView = assets.view(CdR11bAssets.Asset.MAP_SPR_SONIC);
        var sonic = assets.sonic();
        if (!sameBytes(sonicView.bytes(), sonic.mappings()))
            throw new IllegalArgumentException("Past Sonic mapping table differs from its authenticated B view");
        for (var mapped : sonic.mappingFrames()) {
            if (!sameBytes(mapped.bytes(), sonicView.bytes().slice(mapped.relativeOffset(), 1 + mapped.pieceCount() * 5)))
                throw new IllegalArgumentException("Past Sonic decoded mapping body differs from its retained source slice");
        }
        var mappings = CdObjectMappings.declaredSourceViews(List.of(
                new CdObjectMappings.DeclaredTable(sonicView.range().nativeAddress(), sonicView.bytes(), 0,
                        sonic.mappingFrames().size()),
                new CdObjectMappings.DeclaredTable(hudView.range().nativeAddress(), hudView.bytes(), 0, 4),
                new CdObjectMappings.DeclaredTable(monitorView.range().nativeAddress(), monitorView.bytes(), 0, 20),
                new CdObjectMappings.DeclaredTable(ringView.range().nativeAddress(), ringView.bytes(), 0, 9),
                new CdObjectMappings.DeclaredTable(springView.range().nativeAddress(), springView.bytes(), 0, 3),
                new CdObjectMappings.DeclaredTable(springView.range().nativeAddress(), springView.bytes(), 6, 3),
                new CdObjectMappings.DeclaredTable(springView.range().nativeAddress(), springView.bytes(), 80, 3),
                new CdObjectMappings.DeclaredTable(platformView.range().nativeAddress(), platformView.bytes(), 0, 3),
                new CdObjectMappings.DeclaredTable(floatView.range().nativeAddress(), floatView.bytes(), 0, 6),
                new CdObjectMappings.DeclaredTable(pataNormalView.range().nativeAddress(), pataNormalView.bytes(), 0, 3),
                new CdObjectMappings.DeclaredTable(pataDamagedView.range().nativeAddress(), pataDamagedView.bytes(), 0, 3),
                new CdObjectMappings.DeclaredTable(tamabbohView.range().nativeAddress(), tamabbohView.bytes(), 0, 3),
                new CdObjectMappings.DeclaredTable(tamabbohView.range().nativeAddress(), tamabbohView.bytes(), 22, 3),
                new CdObjectMappings.DeclaredTable(tamabbohMissileView.range().nativeAddress(), tamabbohMissileView.bytes(), 0, 4),
                new CdObjectMappings.DeclaredTable(tunnelDoorView.range().nativeAddress(), tunnelDoorView.bytes(), 0, 2)),
                List.of());
        return new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                if (activeDrain != null) return readDrainingSlot(ref);
                if (Objects.requireNonNull(ref).wholeIndex() == 0)
                    throw new IllegalStateException("Primary live pointer reads belong to the actual drain");
                return Objects.requireNonNull(ref).wholeIndex() < 32 ? readReservedDrawSlot(ref)
                        : activeSpring != null ? readSpringDrawSlot(ref)
                        : activePlatform != null ? readPlatformDrawSlot(ref)
                        : activeFloat != null ? readFloatDrawSlot(ref)
                        : activePata != null ? readPataDrawSlot(ref)
                        : activeTamabboh != null ? readTamabbohDrawSlot(ref)
                        : activeTunnelDoor != null ? readTunnelDoorDrawSlot(ref)
                        : activeMonitor != null ? readMonitorDrawSlot(ref) : readRingDrawSlot(ref);
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
                if (activeDrain != null) { writeDrainingOnScreen(ref, onScreen); return; }
                if (Objects.requireNonNull(ref).wholeIndex() == 0) { writePrimaryOnScreen(ref, onScreen); return; }
                if (Objects.requireNonNull(ref).wholeIndex() < 32) writeReservedOnScreen(ref, onScreen);
                else if (activeSpring != null) writeSpringOnScreen(ref, onScreen);
                else if (activePlatform != null) writePlatformOnScreen(ref, onScreen);
                else if (activeFloat != null) writeFloatOnScreen(ref, onScreen);
                else if (activePata != null) writePataOnScreen(ref, onScreen);
                else if (activeTamabboh != null) writeTamabbohOnScreen(ref, onScreen);
                else if (activeTunnelDoor != null) writeTunnelDoorOnScreen(ref, onScreen);
                else if (activeMonitor != null) writeMonitorOnScreen(ref, onScreen);
                else writeRingOnScreen(ref, onScreen);
            }
        }, mappings, values.cpuSat().copy(), List.of(
                new CdObjectDrawQueue.CdSlotRef(2), new CdObjectDrawQueue.CdSlotRef(3),
                new CdObjectDrawQueue.CdSlotRef(5), new CdObjectDrawQueue.CdSlotRef(31)));
    }

    /** Scalar verifier instructions only; never commands replayed from returned effects. */
    private record ReservedStore(int offset, int width, int value) { }
    private final class ReservedInvocation {
        private final CdReservedHud body = reservedHud;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final Object world;
        private final long generation;
        private final int whole;
        private final Values base = values;
        private CdAssetBytes expectedHud = values.hudObjects();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final CdObjectPlacementRuntime.Snapshot placed = placement.capture();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private final List<ReservedStore> stores;
        private final boolean drawExpected;
        private int storeCursor;
        private boolean drawn;

        private ReservedInvocation(int whole, CdReservedHud.Globals globals) {
            this.whole = whole;
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var source = reservedImage(whole);
            stores = reservedStores(source, globals);
            int id = source.unsignedByte(0), subtype = source.unsignedByte(0x28), routine = source.unsignedByte(0x24);
            drawExpected = (routine == 0 || routine == 2)
                    && (id == 0x1C && subtype < 0x80
                    || id == 0x19 && subtype == 0x0A && globals.timeAttackModeByte() == 0
                    && globals.timeWarpDirectionByte() != 0
                    && (globals.timeWarpTimerWord() < 90 || (globals.levelFramesWord() & 1) == 0));
        }
    }

    private void runReservedHud(int whole) {
        if (!runningObjects || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort)
            throw new IllegalStateException("No actual reserved invocation entry");
        var env = frame.environment();
        var g = env.globals();
        long absoluteVInt = frame.vIntClock().absoluteValue().orElseThrow(
                () -> new IllegalStateException("Past reserved HUD requires its absolute native VInt"));
        var globals = new CdReservedHud.Globals(g.debugCheat(), values.ring().rings(), (int) absoluteVInt & 0xFF,
                g.timeAttackMode(), env.movement().gates().timeWarpDirection(), g.timeWarpTimer(), g.levelFrames());
        var invocation = new ReservedInvocation(whole, globals);
        activeReserved = invocation;
        try {
            var result = reservedHud.run(whole, globals);
            verifyReservedInvocation(invocation);
            if (invocation.storeCursor != invocation.stores.size() || invocation.drawn != invocation.drawExpected
                    || !sameBytes(result.image(), reservedImage(whole)))
                throw new IllegalStateException("Incomplete actual reserved body publication");
            // Result/effects are completed metadata; no operation is replayed.
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure);
            throw failure;
        } finally {
            activeReserved = null;
        }
    }

    private ReservedInvocation requireReservedInvocation(int whole) {
        var invocation = activeReserved;
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeRing != null || ringPort || drawingRing || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation == null || invocation.whole != whole
                || invocation.body != reservedHud || invocation.sourceFrame != frame
                || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached reserved HUD invocation");
        verifyReservedInvocation(invocation);
        return invocation;
    }

    private void verifyReservedInvocation(ReservedInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        var now = placement.capture();
        if (activePrimary != null || activeReserved != invocation || !runningObjects || activeRing != null || ringPort || drawingRing || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation.body != reservedHud
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !now.status().equals(invocation.placed.status())
                || !now.pool().slots().equals(invocation.placed.pool().slots())
                || !sameBytes(now.savedFlags(), invocation.placed.savedFlags())
                || !presentation.samePastScroll(invocation.scroll) || sounds != invocation.expectedSounds
                || !values.equals(reservedValues(invocation.base, invocation.expectedHud))
                || !sameBytes(values.hudObjects(), invocation.expectedHud))
            throw new IllegalStateException("Reserved HUD changed another actual owner or stale full31 image");
    }

    private void verifyReservedFailure(Throwable original) {
        try {
            if (activeReserved != null) verifyReservedInvocation(activeReserved);
        } catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private CdAssetBytes readReservedHud(int whole) {
        var invocation = requireReservedInvocation(whole);
        if (reservedPort || drawingReserved || queuePort)
            throw new IllegalStateException("Reserved HUD read reentry");
        reservedPort = true;
        try {
            var image = reservedImage(whole);
            verifyReservedInvocation(invocation);
            return image;
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { reservedPort = false; }
    }

    private void writeReservedHud(int whole, CdAssetBytes expected, CdAssetBytes next) {
        var invocation = requireReservedInvocation(whole);
        if (reservedPort || drawingReserved || queuePort || invocation.drawn
                || expected == null || next == null || expected.length() != 64 || next.length() != 64
                || !sameBytes(expected, reservedImage(whole))
                || invocation.storeCursor >= invocation.stores.size())
            throw new IllegalStateException("Invalid selected reserved write or native store order");
        reservedPort = true;
        try {
            var raw = expected.copy();
            var store = invocation.stores.get(invocation.storeCursor);
            if (store.width() == 0) Arrays.fill(raw, (byte) 0);
            else for (int i = 0; i < store.width(); i++)
                raw[store.offset() + i] = (byte) (store.value() >>> ((store.width() - i - 1) * 8));
            if (!Arrays.equals(raw, next.copy()))
                throw new IllegalStateException("Reserved write is not the reached finite native store");
            publishReservedImage(invocation, next);
            invocation.storeCursor++;
            verifyReservedInvocation(invocation);
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { reservedPort = false; }
    }

    private CdAssetBytes reservedImage(int whole) {
        if (whole < 1 || whole > 31) throw new IllegalArgumentException("Reserved slot must be 1..31");
        return values.hudObjects().slice((whole - 1) * 64, 64);
    }
    private static boolean sameBytes(CdAssetBytes left, CdAssetBytes right) {
        return Arrays.equals(left.copy(), right.copy());
    }
    private static Values reservedValues(Values v, CdAssetBytes image) {
        return new Values(v.phase(), v.section(), v.cpuPalette(), v.fadePalette(), v.displayedPalette(),
                v.cpuSat(), v.displayedSat(), v.displayedHScroll(), v.displayedVScrollLongBits(),
                v.foregroundPlane(), v.ring(), v.level(), v.globals(), image, v.fadeProgress(), v.calls(), v.loop());
    }
    private void publishReservedImage(ReservedInvocation invocation, CdAssetBytes selected) {
        byte[] all = invocation.expectedHud.copy();
        System.arraycopy(selected.copy(), 0, all, (invocation.whole - 1) * 64, 64);
        var ready = CdAssetBytes.copyOf(all);
        var readyValues = reservedValues(values, ready);
        values = readyValues;
        invocation.expectedHud = ready;
    }

    private void drawReservedHud(int whole) {
        var invocation = requireReservedInvocation(whole);
        if (reservedPort || drawingReserved || queuePort || invocation.drawn || !invocation.drawExpected
                || invocation.storeCursor != invocation.stores.size())
            throw new IllegalStateException("Reserved DrawObject is not at its actual once-only cut");
        invocation.drawn = true;
        drawingReserved = true;
        try {
            var image = reservedImage(whole);
            var camera = presentation.pastForegroundImage();
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(whole),
                    image.unsignedByte(1), image.unsignedWord(8), image.unsignedWord(0x0C),
                    image.unsignedByte(0x16), image.unsignedByte(0x19), image.unsignedByte(0x18)),
                    new CdObjectDrawQueue.CameraWords(camera.cameraX(), camera.cameraYWord()));
            verifyReservedInvocation(invocation);
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { drawingReserved = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot readReservedDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireReservedInvocation(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingReserved || reservedPort || queuePort)
            throw new IllegalStateException("No actual reserved queue dereference");
        queuePort = true;
        try {
            var image = reservedImage(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(image.unsignedByte(0), image.unsignedByte(1),
                    image.unsignedWord(8), image.unsignedWord(0x0C), image.unsignedWord(0x0A),
                    (int) image.unsignedLong(4), image.unsignedByte(0x1A), image.unsignedWord(2));
            verifyReservedInvocation(invocation);
            return live;
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { queuePort = false; }
    }

    private void writeReservedOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireReservedInvocation(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingReserved || reservedPort || queuePort || onScreen)
            throw new IllegalStateException("Only actual DrawObject may clear reserved bit7");
        queuePort = true;
        try {
            byte[] image = reservedImage(invocation.whole).copy();
            image[1] &= 0x7F;
            publishReservedImage(invocation, CdAssetBytes.copyOf(image));
            verifyReservedInvocation(invocation);
        } catch (RuntimeException | Error failure) {
            verifyReservedFailure(failure); throw failure;
        } finally { queuePort = false; }
    }

    /**
     * Independent finite store-value verifier for these two native roots, not a
     * gameplay/body/animator replay. Only the actual cached producer can consume it.
     * An empty list preserves the component's earlier unsupported branch.
     */
    private List<ReservedStore> reservedStores(CdAssetBytes source, CdReservedHud.Globals g) {
        var stores = new ArrayList<ReservedStore>();
        int id = source.unsignedByte(0), subtype = source.unsignedByte(0x28);
        int subtype2 = source.unsignedByte(0x29), routine = source.unsignedByte(0x24);
        if (id == 0x1C && subtype < 0x80 && (routine == 0 || routine == 2)) {
            if (routine == 0) {
                stores.add(new ReservedStore(0x24, 1, 2));
                stores.add(new ReservedStore(4, 4, assets.view(CdR11bAssets.Asset.MAP_SPR_HUD).range().nativeAddress()));
                stores.add(new ReservedStore(2, 2, 0x8568));
                stores.add(new ReservedStore(8, 2, 0x90));
                stores.add(new ReservedStore(0x0A, 2, 0x88));
                if (subtype2 != 0) stores.add(new ReservedStore(0x1A, 1, 3));
                else {
                    if (g.debugCheatWord() != 0) stores.add(new ReservedStore(0x1A, 1, 2));
                    if (subtype != 0) {
                        stores.add(new ReservedStore(0x0A, 2, 0x148));
                        stores.add(new ReservedStore(0x1A, 1, 1));
                    }
                }
            }
            if (subtype == 0) {
                if (subtype2 != 0) {
                    int tileHigh = routine == 0 ? 0x85 : source.unsignedByte(2);
                    if (g.ringsWord() != 0) stores.add(new ReservedStore(2, 1, tileHigh & ~0x20));
                    else if ((g.vIntLowByte() & 0x0F) == 0)
                        stores.add(new ReservedStore(2, 1, tileHigh ^ 0x20));
                } else {
                    stores.add(new ReservedStore(0x1A, 1, 0));
                    if (g.debugCheatWord() != 0) stores.add(new ReservedStore(0x1A, 1, 2));
                }
            }
        } else if (id == 0x19 && subtype >= 8) {
            if (g.timeAttackModeByte() != 0) stores.add(new ReservedStore(0, 0, 0));
            else if (subtype == 0x0A && (routine == 0 || routine == 2)) {
                if (routine == 0) {
                    stores.add(new ReservedStore(0x24, 1, 2));
                    stores.add(new ReservedStore(4, 4, assets.view(CdR11bAssets.Asset.MAP_SPR_MONITOR_TIME).range().nativeAddress()));
                    stores.add(new ReservedStore(2, 2, 0x85A8));
                    stores.add(new ReservedStore(8, 2, 0xC4));
                    stores.add(new ReservedStore(0x0A, 2, 0x152));
                }
                stores.add(new ReservedStore(0x1A, 1, 0x12));
                if ((g.timeWarpDirectionByte() & 0x80) == 0) stores.add(new ReservedStore(0x1A, 1, 0x13));
            }
        }
        return List.copyOf(stores);
    }

    /**
     * Immediate capability around one real selected Ring call. The cached algorithm
     * owns pool/bank writes; these guards protect its synchronous external ports and
     * every other owner rather than replaying Init/Main/Collect/AnimateObject.
     */
    private final class RingInvocation {
        private final CdRing body = ring;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final int routine;
        private final Values base = values;
        private CdRingGlobals expectedRing = values.ring();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final CdObjectPlacementRuntime.Status placementStatus = placement.status();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectDrawQueue.CameraWords preScrollCamera;
        private CdRingGlobals readRing;
        private boolean rewardWritten;
        private boolean soundRequested;
        private boolean drawn;
        private boolean screenCleared;

        private RingInvocation(int whole) {
            this.whole = whole;
            routine = pool.state(whole).routine();
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var camera = presentation.pastForegroundImage();
            preScrollCamera = new CdObjectDrawQueue.CameraWords(camera.cameraX(), camera.cameraYWord());
        }
    }

    private void runRing(int whole) {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != 0x10
                || values.phase() != BEGIN_LEVEL || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live dynamic Ring invocation entry");
        var gates = frame.environment().movement().gates();
        var identity = assets.identity();
        var invocation = new RingInvocation(whole);
        var inputs = new CdRing.Inputs(identity.variant(), identity.stage(), gates.zoneAct() >>> 8,
                invocation.preScrollCamera.xWord(), gates.timeZone(), gates.timeWarpDirection());
        activeRing = invocation;
        try {
            ring.executeSelected(whole, inputs, new CdRing.GlobalsPort() {
                @Override public CdRingGlobals read() { return readRingGlobals(invocation); }
                @Override public void write(CdRingGlobals next) { writeRingGlobals(invocation, next); }
            }, new CdRing.SourceCalls() {
                @Override public void playFmSound(CdRing.FmSound sound) {
                    ringSound(invocation, new SonicCdGameModule.PastRingFm(sound));
                }
                @Override public void subCpuCommand(CdRing.SubCpuCommand command) {
                    ringSound(invocation, new SonicCdGameModule.PastRingSubCpu(command));
                }
                @Override public void drawObject(CdObjectPool pool, int currentWhole) {
                    drawRing(invocation, pool, currentWhole);
                }
            });
            verifyRingInvocation(invocation);
            if (drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                    || invocation.routine == 4 && (!invocation.rewardWritten || !invocation.soundRequested))
                throw new IllegalStateException("Incomplete actual Ring publication");
        } catch (RuntimeException | Error failure) {
            verifyRingFailure(failure);
            throw failure;
        } finally {
            activeRing = null;
        }
    }

    private RingInvocation requireRingInvocation(RingInvocation invocation, CdObjectPool pool, int whole) {
        if (invocation == null || activeRing != invocation || invocation.pool != pool || pool != placement.pool()
                || invocation.whole != whole || whole < 32 || whole >= 128 || pool.state(whole).id() != 0x10
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Ring callback for this live physical pointer");
        verifyRingInvocation(invocation);
        return invocation;
    }

    private void verifyRingInvocation(RingInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeRing != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeReserved != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || reservedPort || drawingReserved
                || invocation.body != ring || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.placementStatus)
                || !presentation.samePastScroll(invocation.scroll) || sounds != invocation.expectedSounds
                || !values.equals(ringValues(invocation.base, invocation.expectedRing)))
            throw new IllegalStateException("Ring changed another actual owner or retained a stale source cut");
    }

    private static Values ringValues(Values v, CdRingGlobals ring) {
        return new Values(v.phase(), v.section(), v.cpuPalette(), v.fadePalette(), v.displayedPalette(),
                v.cpuSat(), v.displayedSat(), v.displayedHScroll(), v.displayedVScrollLongBits(),
                v.foregroundPlane(), ring, v.level(), v.globals(), v.hudObjects(), v.fadeProgress(), v.calls(), v.loop());
    }

    /** Complete pool/bank nonwrite proof for a callback, with only DrawObject's one bit-clear exception. */
    private void verifyRingCallback(RingInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                    CdObjectState cleared) {
        verifyRingInvocation(invocation);
        var now = placement.capture();
        var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(expected)
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Ring callback changed its pool or saved bank outside the native bit7 clear");
    }

    private void verifyRingFailure(Throwable original) {
        try {
            if (activeRing != null) verifyRingInvocation(activeRing);
        } catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private void verifyRingCallbackFailure(RingInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                           CdObjectState cleared, Throwable original) {
        try { verifyRingCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private CdRingGlobals readRingGlobals(RingInvocation invocation) {
        requireRingInvocation(invocation, invocation.pool, invocation.whole);
        if (ringPort || drawingRing || queuePort || invocation.readRing != null || invocation.drawn)
            throw new IllegalStateException("Ring globals read is not at its actual once-only cut");
        var before = placement.capture();
        ringPort = true;
        try {
            var current = values.ring();
            invocation.readRing = current;
            verifyRingCallback(invocation, before, null);
            return current;
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { ringPort = false; }
    }

    private void writeRingGlobals(RingInvocation invocation, CdRingGlobals next) {
        requireRingInvocation(invocation, invocation.pool, invocation.whole);
        if (ringPort || drawingRing || queuePort || invocation.routine != 4 || invocation.readRing == null
                || invocation.rewardWritten || invocation.soundRequested || invocation.drawn
                || values.ring() != invocation.readRing || next == null)
            throw new IllegalStateException("Ring reward requires its actual routine4 read and once-only publication");
        requireRingReward(invocation.readRing, next);
        var before = placement.capture();
        ringPort = true;
        try {
            var ready = ringValues(values, next);
            values = ready;
            invocation.expectedRing = next;
            invocation.rewardWritten = true;
            verifyRingCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { ringPort = false; }
    }

    /**
     * CollectRing:290-306 scalar publication contract, independent of the producer.
     * Word/byte wrap and old BSET bits matter; no gameplay body is called to authorize a value.
     */
    private static void requireRingReward(CdRingGlobals before, CdRingGlobals next) {
        int rings = (before.rings() + 1) & 0xFFFF;
        int flags = before.livesFlags();
        if (rings >= 100) {
            flags |= 2;
            if ((before.livesFlags() & 2) != 0 && rings >= 200) flags |= 4;
        }
        boolean life = ringGainsLife(before);
        if (next.rings() != rings || next.livesFlags() != flags
                || next.lives() != ((before.lives() + (life ? 1 : 0)) & 0xFF)
                || next.updateHudRings() != (before.updateHudRings() | 1)
                || next.updateHudLives() != ((before.updateHudLives() + (life ? 1 : 0)) & 0xFF)
                || next.timeStopTimer() != before.timeStopTimer()
                || next.ringAnimationTimer() != before.ringAnimationTimer()
                || next.ringAnimationFrame() != before.ringAnimationFrame())
            throw new IllegalStateException("Ring globals are not the reached wrapped native reward");
    }

    private static boolean ringGainsLife(CdRingGlobals before) {
        int rings = (before.rings() + 1) & 0xFFFF;
        return rings >= 100 && ((before.livesFlags() & 2) == 0
                || rings >= 200 && (before.livesFlags() & 4) == 0);
    }

    private void ringSound(RingInvocation invocation, SonicCdGameModule.PastSound sound) {
        requireRingInvocation(invocation, invocation.pool, invocation.whole);
        if (ringPort || drawingRing || queuePort || invocation.routine != 4 || !invocation.rewardWritten
                || invocation.soundRequested || invocation.drawn
                || (ringGainsLife(invocation.readRing)
                ? !(sound instanceof SonicCdGameModule.PastRingSubCpu subCpu && subCpu.command() == CdRing.SubCpuCommand.SCMD_YESSFX)
                : !(sound instanceof SonicCdGameModule.PastRingFm s && s.sound() == CdRing.FmSound.FM_RING)))
            throw new IllegalStateException("Ring sound is not the actual once-only postreward source call");
        var before = placement.capture();
        ringPort = true;
        try {
            appendSound(sound);
            invocation.expectedSounds = sounds;
            invocation.soundRequested = true;
            verifyRingCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { ringPort = false; }
    }

    private void drawRing(RingInvocation invocation, CdObjectPool pool, int whole) {
        requireRingInvocation(invocation, pool, whole);
        if (ringPort || drawingRing || queuePort || invocation.drawn
                || invocation.routine == 4 && !invocation.soundRequested)
            throw new IllegalStateException("Ring DrawObject is not at its actual once-only source cut");
        var before = placement.capture();
        var current = pool.state(whole);
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true;
        drawingRing = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.preScrollCamera);
            if (!invocation.screenCleared) throw new IllegalStateException("Ring DrawObject did not clear its live bit7");
            verifyRingCallback(invocation, before, cleared);
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure);
            throw failure;
        } finally { drawingRing = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot readRingDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = activeRing;
        requireRingInvocation(invocation, placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingRing || ringPort || queuePort)
            throw new IllegalStateException("No actual Ring queue dereference");
        var before = placement.capture();
        queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(),
                    current.centreX(), current.centreY(), current.word(CdObjectState.WordField.X_FRACTION),
                    current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyRingCallback(invocation, before, null);
            return live;
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }

    private void writeRingOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = activeRing;
        requireRingInvocation(invocation, placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingRing || ringPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual DrawObject may clear this live Ring's bit7 once");
        var before = placement.capture();
        var current = invocation.pool.state(invocation.whole);
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        queuePort = true;
        try {
            invocation.pool.write(next);
            invocation.screenCleared = true;
            verifyRingCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            verifyRingCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure);
            throw failure;
        } finally { queuePort = false; }
    }


    /** Actual cached Spring producer scope; values and returned effects confer no call authority. */
    private final class SpringInvocation {
        private final CdSpring body = spring;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final Values base = values;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdSolidObject.Result solidResult;
        private boolean launched;
        private boolean sounded;
        private boolean drawn;
        private boolean screenCleared;

        private SpringInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runSpring(int whole) {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdSpring.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Past Spring invocation entry");
        var invocation = new SpringInvocation(whole);
        activeSpring = invocation;
        try {
            var env = frame.environment();
            var result = spring.run(whole, new CdSpring.Globals(invocation.camera.xWord()),
                    new CdSpring.SolidInputs(springPlayer(), new CdSolidObject.Gates(
                            env.globals().debugMode(), env.movement().gates().miniSonic()), springGlobals()));
            verifySpringInvocation(invocation);
            if (!invocation.drawn || !result.state().equals(placement.pool().state(whole))
                    || !result.finalPlayer().equals(springPlayer()) || !result.finalGlobals().equals(springGlobals())
                    || invocation.launched != invocation.sounded)
                throw new IllegalStateException("Incomplete actual Spring return");
        } catch (RuntimeException | Error failure) {
            verifySpringFailure(failure); throw failure;
        } finally { activeSpring = null; }
    }

    private SpringInvocation requireSpringInvocation(CdObjectPool pool, int whole) {
        var invocation = activeSpring;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || pool.state(whole).id() != CdSpring.OBJECT_ID
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Spring callback at this physical pointer");
        verifySpringInvocation(invocation);
        return invocation;
    }

    private void verifySpringInvocation(SpringInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeSpring != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeReserved != null || activeRing != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || reservedPort || drawingReserved || ringPort || drawingRing
                || invocation.body != spring || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status)
                || !sameBytes(placement.capture().savedFlags(), invocation.savedFlags)
                || !presentation.samePastScroll(invocation.scroll) || sounds != invocation.expectedSounds
                || values != invocation.base)
            throw new IllegalStateException("Spring changed another owner or retained a stale actual cut");
    }

    private void verifySpringFailure(Throwable original) {
        try { if (activeSpring != null) verifySpringInvocation(activeSpring); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private CdSolidObject.Player springPlayer() {
        var s = frame.state(); var h = s.header();
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(), h.tile(), h.priority(),
                s.timers().hurt(), s.rotationDistance()), h.slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
    }

    private CdSolidObject.Globals springGlobals() {
        var env = frame.environment(); var g = env.movement().globals();
        return new CdSolidObject.Globals(env.globals().invincible(), g.scoreChain(), g.primaryAngle(), g.secondaryAngle());
    }

    /** Complete native-byte nonwrites, including physical identity; aliases do not expand write authority. */
    private static void validateSpringSolidImage(CdObjectState before, CdObjectState after, boolean own,
                                                 Set<CdObjectState.ByteField> writes) {
        if (!before.identity().equals(after.identity()))
            throw new IllegalStateException("Spring Solid changed a physical identity");
        if (own && writes.stream().anyMatch(f -> f != CdObjectState.ByteField.FLAGS && f != CdObjectState.ByteField.SOLID_TYPE))
            throw new IllegalStateException("Spring Solid declared an unowned object byte");
        byte[] old = before.copyBytes(), next = after.copyBytes();
        for (int at = 0; at < 64; at++) {
            int mask = at == 0x22 ? 0x28 : own && at == 0x25 ? 0xFF : 0;
            if (own && at == 0x22 && !writes.contains(CdObjectState.ByteField.FLAGS)) mask = 0;
            if (own && at == 0x25 && !writes.contains(CdObjectState.ByteField.SOLID_TYPE)) mask = 0;
            if (((old[at] ^ next[at]) & ~mask & 0xFF) != 0)
                throw new IllegalStateException("Spring Solid changed unowned full-image byte " + at);
        }
        if (own && writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                && after.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0
                && after.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 2)
            throw new IllegalStateException("Spring Solid type differs from its plain/bottom native stores");
    }

    /** One genuine completed Solid call; prepare every cross-owner replacement before first assignment. */
    private void commitSpringSolid(CdSpring.SolidCall call) {
        Objects.requireNonNull(call);
        var invocation = requireSpringInvocation(placement.pool(), call.result().object().identity().wholeIndex());
        if (springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.solidResult != null || invocation.launched
                || invocation.sounded || invocation.drawn || invocation.entry.unsignedByte(CdObjectState.ByteField.ROUTINE_2) == 5)
            throw new IllegalStateException("Spring Solid is not at its once-only native cut");
        var pool = invocation.pool; var cut = call.callCut(); var result = call.result();
        pool.validateRestore(cut);
        if (!pool.capture().slots().equals(cut.slots()))
            throw new IllegalStateException("Spring Solid pool changed after its actual call cut");
        var before = cut.slots().get(invocation.whole - 32);
        var entry = switch (before.routine()) {
            case 2, 8, 20 -> CdSolidObject.Entry.SOLID_OBJECT;
            case 14 -> CdSolidObject.Entry.BTM_SOLID_OBJECT;
            default -> throw new IllegalStateException("Spring Solid lacks its reached Main source");
        };
        if (before.id() != CdSpring.OBJECT_ID || result.object().id() != CdSpring.OBJECT_ID
                || (before.spriteFlags() & 0x80) == 0 || result.entry() != entry
                || !call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Spring Solid entry/player/global cut differs");
        validateSpringSolidImage(before, result.object(), true, result.objectWrites());
        var images = new ArrayList<CdObjectState>(); images.add(result.object());
        for (var image : result.otherSlots()) {
            validateSpringSolidImage(cut.slots().get(image.identity().wholeIndex() - 32), image, false, Set.of());
            images.add(image);
        }
        var readyPool = CdObjectPool.overlay(cut, images);
        var readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : result.fmRequests())
            ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger);
        springPort = true;
        try {
            verifySpringInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots())) throw new IllegalStateException("Stale Spring publication pool");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds;
            invocation.actor = readyActor; invocation.shared = readyShared;
            invocation.expectedSounds = readySounds; invocation.solidResult = result;
            verifySpringInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()))
                throw new IllegalStateException("Spring completed Solid images were not adopted exactly");
        } catch (RuntimeException | Error failure) {
            verifySpringFailure(failure); throw failure;
        } finally { springPort = false; }
    }

    private void commitSpringLaunch(CdSpring.LaunchCall call) {
        Objects.requireNonNull(call);
        var invocation = requireSpringInvocation(placement.pool(), call.identity().wholeIndex());
        if (springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.solidResult == null
                || invocation.launched || invocation.sounded || invocation.drawn)
            throw new IllegalStateException("Spring launch is not after its one actual Solid");
        var solid = invocation.solidResult;
        int routine = invocation.entry.routine();
        boolean trigger = switch (routine) {
            case 2, 14 -> solid.d0().orElseThrow() != 0;
            case 8 -> (solid.object().flags() & 0x20) != 0;
            case 20 -> solid.d0().orElseThrow() != 0 || (solid.object().flags() & 0x20) != 0;
            default -> false;
        };
        int clear = routine == 8 ? 0x20 : routine == 20 ? 0x28 : 8;
        var expectedSource = solid.object().moveByte(CdObjectState.ByteField.ROUTINE, routine + 2)
                .moveByte(CdObjectState.ByteField.FLAGS, solid.object().flags() & ~clear);
        if (!trigger || !expectedSource.equals(invocation.pool.state(invocation.whole))
                || !call.expectedPlayer().equals(solid.player()) || !call.expectedGlobals().equals(solid.globals()))
            throw new IllegalStateException("Spring action source prefix differs from the genuine Solid return");
        Set<CdSpring.LaunchPlayerField> expectedWrites = switch (routine) {
            case 2 -> Set.of(CdSpring.LaunchPlayerField.CENTRE_Y, CdSpring.LaunchPlayerField.Y_VELOCITY,
                    CdSpring.LaunchPlayerField.FLAGS, CdSpring.LaunchPlayerField.ANIMATION);
            case 8 -> {
                var fields = EnumSet.of(CdSpring.LaunchPlayerField.X_VELOCITY, CdSpring.LaunchPlayerField.CENTRE_X,
                        CdSpring.LaunchPlayerField.FLAGS, CdSpring.LaunchPlayerField.MOVE_LOCK,
                        CdSpring.LaunchPlayerField.GROUND_VELOCITY, CdSpring.LaunchPlayerField.ANGLE);
                if ((call.expectedPlayer().actor().motion().flags() & 4) == 0) fields.add(CdSpring.LaunchPlayerField.ANIMATION);
                yield Set.copyOf(fields);
            }
            case 14 -> Set.of(CdSpring.LaunchPlayerField.CENTRE_Y, CdSpring.LaunchPlayerField.Y_VELOCITY, CdSpring.LaunchPlayerField.FLAGS);
            case 20 -> Set.of(CdSpring.LaunchPlayerField.CENTRE_X, CdSpring.LaunchPlayerField.CENTRE_Y,
                    CdSpring.LaunchPlayerField.X_VELOCITY, CdSpring.LaunchPlayerField.Y_VELOCITY, CdSpring.LaunchPlayerField.FLAGS);
            default -> throw new IllegalStateException("No native Spring action");
        };
        if (!call.playerWrites().equals(expectedWrites)) throw new IllegalStateException("Spring launch write sites differ");
        var before = placement.capture();
        frame.validateObjectSpringLaunch(call);
        springPort = true;
        try {
            frame.applyObjectSpringLaunch(call);
            var after = frame.captureActor(); var a = invocation.actor.state(); var b = after.state();
            if (after.owner() != invocation.actor.owner() || !after.display().equals(invocation.actor.display())
                    || !b.motion().equals(call.finalPlayer().actor().motion()) || !b.animation().equals(a.animation())
                    || !b.header().equals(a.header()) || !b.timers().equals(a.timers()) || !b.history().equals(a.history())
                    || b.rotationDistance() != a.rotationDistance() || !b.objectLinks().equals(a.objectLinks())
                    || b.rotationAngle() != a.rotationAngle())
                throw new IllegalStateException("Spring launch changed an unowned complete Actor value");
            invocation.actor = after; invocation.launched = true;
            verifySpringCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifySpringFailure(failure); throw failure;
        } finally { springPort = false; }
    }

    private void playSpringFm(CdSpring.FmSound sound) {
        var invocation = requireSpringInvocation(placement.pool(), activeSpring == null ? -1 : activeSpring.whole);
        if (springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || !invocation.launched || invocation.sounded || invocation.drawn
                || sound != CdSpring.FmSound.FM_SPRING)
            throw new IllegalStateException("Spring sound is not its once-only postlaunch call");
        var before = placement.capture(); var ledger = new ArrayList<>(sounds);
        ledger.add(new SonicCdGameModule.PastSpringFm(sound)); var ready = List.copyOf(ledger);
        springPort = true;
        try {
            sounds = ready; invocation.expectedSounds = ready; invocation.sounded = true;
            verifySpringCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifySpringFailure(failure); throw failure;
        } finally { springPort = false; }
    }

    private void verifySpringCallback(SpringInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                       CdObjectState cleared) {
        verifySpringInvocation(invocation);
        var now = placement.capture(); var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(expected)
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Spring callback changed pool/bank outside the actual bit7 clear");
    }

    private void verifySpringCallbackFailure(SpringInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                              CdObjectState cleared, Throwable original) {
        try { verifySpringCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private void drawSpring(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireSpringInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.launched != invocation.sounded)
            throw new IllegalStateException("Spring DrawObject is not at its actual once-only cut");
        var before = placement.capture(); var current = pool.state(invocation.whole);
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true; drawingSpring = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Spring DrawObject did not clear bit7");
            verifySpringCallback(invocation, before, cleared);
        } catch (RuntimeException | Error failure) {
            try { verifySpringCallback(invocation, before, invocation.screenCleared ? cleared : null); }
            catch (RuntimeException | Error verification) { if (verification != failure) failure.addSuppressed(verification); }
            throw failure;
        } finally { drawingSpring = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot readSpringDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireSpringInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingSpring || springPort || queuePort) throw new IllegalStateException("No actual Spring queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifySpringCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) {
            verifySpringCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }

    private void writeSpringOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireSpringInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingSpring || springPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual Spring DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.screenCleared = true;
            verifySpringCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            try { verifySpringCallback(invocation, before, invocation.screenCleared ? next : null); }
            catch (RuntimeException | Error verification) { if (verification != failure) failure.addSuppressed(verification); }
            throw failure;
        } finally { queuePort = false; }
    }


    /** Asset-free CheckAnimalPrescence/CheckObjDespawn; no visible Animal body is admitted here. */
    private enum AnimalRead { PRESENCE_TIME_ZONE, GOOD_FUTURE, PROJECTOR, CAMERA,
                              WARP_TIME_ZONE, PERIOD_TIME_ZONE, DIRECTION, DONE, BODY }
    private final class AnimalInvocation implements CdAnimal.Reads {
        private final CdAnimal body = animal;
        private final int whole;
        private final CdObjectPool pool = placement.pool();
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final CdObjectState source;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Values base = values;
        private final List<SonicCdGameModule.PastSound> ledger = sounds;
        private final Object world;
        private final long generation;
        private AnimalRead next;
        private CdAnimal.UnsupportedBoundary unsupported;
        private CdAnimal.DespawnEntry despawnEntry;
        private int period = -1, bankIndex = -1;
        private boolean delete, plainDelete, active = true, reading;
        private AnimalInvocation(int whole) {
            this.whole = whole; source = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            if ((source.subtype() & 0x80) != 0) {
                next = AnimalRead.BODY; unsupported = CdAnimal.UnsupportedBoundary.HOLOGRAM_BODY;
            } else next = AnimalRead.PRESENCE_TIME_ZONE;
        }
        @Override public int timeZone() {
            if (next != AnimalRead.PRESENCE_TIME_ZONE && next != AnimalRead.WARP_TIME_ZONE
                    && next != AnimalRead.PERIOD_TIME_ZONE)
                throw new IllegalStateException("Animal requested an unreached time-zone operand");
            return read(next);
        }
        @Override public int goodFuture() { return read(AnimalRead.GOOD_FUTURE); }
        @Override public int projDestroyed() { return read(AnimalRead.PROJECTOR); }
        @Override public int timeWarpDir() { return read(AnimalRead.DIRECTION); }
        @Override public int cameraXWord() { return read(AnimalRead.CAMERA); }
        private int read(AnimalRead site) {
            verifyAnimalContext(this);
            if (reading || next != site || site == AnimalRead.DONE || site == AnimalRead.BODY)
                throw new IllegalStateException("Animal read is outside its actual source sequence");
            verifyAnimalReadImages(this); reading = true;
            try {
                int value = switch (site) {
                    case PRESENCE_TIME_ZONE, WARP_TIME_ZONE, PERIOD_TIME_ZONE -> frame.environment().movement().gates().timeZone();
                    case GOOD_FUTURE -> values.globals().retainedDev().goodFuture();
                    case PROJECTOR -> values.globals().retainedDev().projDestroyed();
                    case CAMERA -> presentation.pastForegroundImage().cameraX();
                    case DIRECTION -> frame.environment().movement().gates().timeWarpDirection();
                    default -> throw new IllegalStateException("Animal operand has no native read");
                };
                advance(site, value); verifyAnimalContext(this); verifyAnimalReadImages(this); return value;
            } catch (RuntimeException | Error original) {
                try { verifyAnimalContext(this); verifyAnimalReadImages(this); }
                catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
                throw original;
            } finally { reading = false; }
        }
        private void advance(AnimalRead site, int value) {
            switch (site) {
                case PRESENCE_TIME_ZONE -> next = (byte) value >= 2 ? AnimalRead.GOOD_FUTURE : AnimalRead.PROJECTOR;
                case GOOD_FUTURE -> {
                    if (value != 0) { next = AnimalRead.BODY; unsupported = CdAnimal.UnsupportedBoundary.GOOD_FUTURE_BODY; }
                    else { delete = plainDelete = true; next = AnimalRead.DONE; }
                }
                case PROJECTOR -> {
                    if (value != 0) { next = AnimalRead.BODY; unsupported = CdAnimal.UnsupportedBoundary.PROJECTOR_DESTROYED_BODY; }
                    else next = (source.spriteFlags() & 0x80) == 0 ? AnimalRead.CAMERA : AnimalRead.WARP_TIME_ZONE;
                }
                case CAMERA -> {
                    int distance = ((source.centreX() & 0xFF80) - (((value - 0x80) & 0xFFFF) & 0xFF80)) & 0xFFFF;
                    if (distance > 0x280) despawn(CdAnimal.DespawnEntry.CURRENT_X_OFFSCREEN);
                    else next = AnimalRead.WARP_TIME_ZONE;
                }
                case WARP_TIME_ZONE -> {
                    if ((value & 0x80) != 0) despawn(CdAnimal.DespawnEntry.TIME_WARP);
                    else next = AnimalRead.DONE;
                }
                case PERIOD_TIME_ZONE -> {
                    period = value & 0x7F;
                    if ((value & 0x80) != 0) next = AnimalRead.DIRECTION;
                    else finishPeriod(period);
                }
                case DIRECTION -> {
                    int oldPeriod = (short) ((period - (byte) value) & 0xFFFF);
                    finishPeriod(oldPeriod < 0 ? 0 : Math.min(oldPeriod, 2));
                }
                default -> throw new IllegalStateException("Animal advanced an unread operand");
            }
        }
        private void despawn(CdAnimal.DespawnEntry entry) {
            delete = true; despawnEntry = entry;
            next = source.savedFlagsId() == 0 ? AnimalRead.DONE : AnimalRead.PERIOD_TIME_ZONE;
        }
        private void finishPeriod(int value) {
            period = value; bankIndex = 2 + 3 * source.savedFlagsId() + value; next = AnimalRead.DONE;
        }
    }

    private void runAnimal(int whole) {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || activeTunnelDoor != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || floatPort || drawingFloat || tunnelDoorPort || drawingTunnelDoor
                || monitorPort || drawingMonitor || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdAnimal.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Animal dispatch");
        var invocation = new AnimalInvocation(whole);
        try {
            var result = animal.runWithReads(whole, invocation);
            verifyAnimalMutation(invocation, true);
            if (invocation.next != AnimalRead.DONE || !result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("Animal returned before its exact native presence/despawn output");
            var effects = new ArrayList<CdAnimal.Effect>();
            effects.add(new CdAnimal.CallReturned(CdAnimal.NativeCall.CHECK_ANIMAL_PRESENCE));
            if (invocation.plainDelete) {
                effects.add(new CdAnimal.Deleted()); effects.add(new CdAnimal.CallReturned(CdAnimal.NativeCall.DELETE_OBJECT));
            } else {
                if (invocation.delete) effects.add(new CdAnimal.Despawn(invocation.despawnEntry,
                        invocation.source.savedFlagsId(), invocation.period));
                effects.add(new CdAnimal.CallReturned(CdAnimal.NativeCall.CHECK_OBJ_DESPAWN));
            }
            if (!effects.equals(result.effects()) || result.deleted() != invocation.delete)
                throw new IllegalStateException("Animal completed metadata disagrees with its actual native branch");
        } catch (RuntimeException | Error original) {
            try {
                verifyAnimalMutation(invocation, false);
                if (original instanceof CdAnimal.UnsupportedAnimal stopped
                        && (stopped.wholeIndex() != whole || stopped.boundary() != invocation.unsupported))
                    throw new IllegalStateException("Animal stopped at another native predicate");
            } catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { invocation.active = false; }
    }

    private void verifyAnimalContext(AnimalInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (!invocation.active || invocation.body != animal || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || activeTunnelDoor != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || floatPort || drawingFloat || tunnelDoorPort || drawingTunnelDoor
                || monitorPort || drawingMonitor || queuePort || invocation.pool != placement.pool()
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || values != invocation.base || values.phase() != BEGIN_LEVEL || sounds != invocation.ledger
                || !placement.status().equals(invocation.before.status()) || !presentation.samePastScroll(invocation.scroll)
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Animal read changed another owner or retained a stale actual source cut");
        invocation.pool.validateRestore(invocation.before.pool());
    }
    private void verifyAnimalReadImages(AnimalInvocation invocation) {
        var now = placement.capture();
        if (!now.pool().slots().equals(invocation.before.pool().slots())
                || !sameBytes(now.savedFlags(), invocation.before.savedFlags())
                || !now.status().equals(invocation.before.status()))
            throw new IllegalStateException("Animal scalar accessor changed actual full96/saved/status");
    }
    private void verifyAnimalMutation(AnimalInvocation invocation, boolean completed) {
        verifyAnimalContext(invocation);
        var now = placement.capture();
        byte[] originalBank = invocation.before.savedFlags().copy(), expectedBank = originalBank.clone();
        byte[] originalSource = invocation.source.copyBytes(), cleared = new byte[CdObjectState.BYTE_SIZE];
        boolean ready = invocation.next == AnimalRead.DONE && invocation.unsupported == null;
        boolean bank = ready && invocation.delete && !invocation.plainDelete && invocation.source.savedFlagsId() != 0;
        boolean owned = !bank || invocation.period >= 0 && invocation.period <= 2
                && invocation.bankIndex >= 2 && invocation.bankIndex < expectedBank.length;
        if (bank && owned) expectedBank[invocation.bankIndex] &= 0x7F;
        boolean expectedClear = ready && invocation.delete && owned;
        byte[] actual = now.pool().slots().get(invocation.whole - 32).copyBytes();
        byte[] actualBank = now.savedFlags().copy();
        boolean beforePair = Arrays.equals(actual, originalSource) && Arrays.equals(actualBank, originalBank);
        boolean afterPair = Arrays.equals(actual, expectedClear ? cleared : originalSource) && Arrays.equals(actualBank, expectedBank);
        boolean bankPrefix = expectedClear && bank && Arrays.equals(actual, originalSource) && Arrays.equals(actualBank, expectedBank);
        if (completed ? !ready || !owned || !afterPair : !beforePair && !afterPair && !bankPrefix)
            throw new IllegalStateException("Animal pool/bank output exceeds its genuine reached native prefix");
        for (int i = 0; i < 96; i++) if (i != invocation.whole - 32
                && !now.pool().slots().get(i).equals(invocation.before.pool().slots().get(i)))
            throw new IllegalStateException("Animal changed another physical image");
        if (!now.pool().slots().get(invocation.whole - 32).identity().equals(invocation.source.identity())
                || !now.status().equals(invocation.before.status()))
            throw new IllegalStateException("Animal changed source identity or placement cursors");
    }


    /** One cached native Tamabboh call. Values below guard owners; they never replay its algorithm. */
    private final class TamabbohInvocation {
        private final CdTamabboh body = tamabboh;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object stageSource = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdPlayerBody.State player = frame.state();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final CdObjectPlacementRuntime.Status placementStatus = placement.status();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final Values baseValues = values;
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdTamabboh.Globals globals;
        private final CdObjectDrawQueue.CameraWords camera;
        private CdObjectState expectedRangeX, drawSource;
        private boolean drawn, screenCleared, sounded;
        private TamabbohInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var fg = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(fg.cameraX(), fg.cameraYWord());
            var environment = frame.environment(); var movement = environment.movement();
            globals = new CdTamabboh.Globals(values.globals().retainedDev().goodFuture(),
                    movement.gates().timeZone(), movement.gates().timeWarpDirection(),
                    environment.globals().debugMode(), camera.xWord(), movement.globals().primaryAngle());
        }
    }

    private void runTamabboh(int whole) {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeFloat != null || activePata != null || activeTunnelDoor != null || activeMonitor != null
                || activeTamabboh != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || floatPort || drawingFloat
                || drawingPata || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || tamabbohPort || drawingTamabboh || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdTamabboh.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Tamabboh dispatch");
        var invocation = new TamabbohInvocation(whole); activeTamabboh = invocation;
        try {
            var result = tamabboh.run(whole, invocation.globals);
            verifyTamabbohInvocation(invocation);
            if (!result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("Tamabboh result differs from its actual completed physical image");
            boolean conversion = invocation.entry.subtype() != 1 && invocation.globals.goodFuture() != 0
                    && (invocation.globals.timeZone() != 1 || invocation.entry.subtype() != 0);
            if (conversion ? invocation.drawn : !invocation.drawn || !invocation.screenCleared)
                throw new IllegalStateException("Tamabboh returned without its actual native DrawObject cut");
        } catch (RuntimeException | Error original) {
            try { verifyTamabbohInvocation(invocation); }
            catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { activeTamabboh = null; }
    }

    private TamabbohInvocation requireTamabbohInvocation(CdObjectPool pool, int whole) {
        var invocation = activeTamabboh;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual cached Tamabboh callback at this physical pointer");
        verifyTamabbohInvocation(invocation); return invocation;
    }
    private void verifyTamabbohInvocation(TamabbohInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeTamabboh != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeFloat != null || activePata != null
                || activeTunnelDoor != null || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation.body != tamabboh || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.stageSource != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.baseValues != values || values.phase() != BEGIN_LEVEL || invocation.expectedSounds != sounds
                || !placement.status().equals(invocation.placementStatus) || !presentation.samePastScroll(invocation.scroll)
                || frame.state() != invocation.player || !frame.captureActor().equals(invocation.actor)
                || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Tamabboh changed another owner or retained a stale actual World/Frame cut");
    }
    private CdObjectState validateTamabbohCut(TamabbohInvocation invocation, CdObjectPool.Snapshot cut,
                                             CdObjectState source) {
        verifyTamabbohInvocation(invocation); invocation.pool.validateRestore(cut);
        var live = invocation.pool.state(invocation.whole);
        if (live.id() != CdTamabboh.OBJECT_ID || !live.equals(source)
                || source.identity().wholeIndex() != invocation.whole
                || !cut.slots().get(invocation.whole - 32).equals(source)
                || !invocation.pool.capture().slots().equals(cut.slots()))
            throw new IllegalStateException("Tamabboh port differs from its actual same-owner full96/source cut");
        return live;
    }
    private void verifyTamabbohCallback(TamabbohInvocation invocation, CdObjectPlacementRuntime.Snapshot before) {
        verifyTamabbohInvocation(invocation);
        var now = placement.capture();
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(before.pool().slots())
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Tamabboh read/angle/FM callback changed the full96 pool or saved/placement state");
    }
    private void verifyTamabbohCallbackFailure(TamabbohInvocation invocation,
                                              CdObjectPlacementRuntime.Snapshot before, Throwable original) {
        try { verifyTamabbohCallback(invocation, before); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** Every reached floor store uses this exact current primary byte; no player/state aggregate is written. */
    private void commitTamabbohPrimaryAngle(CdTamabboh.PrimaryAngleCall call) {
        Objects.requireNonNull(call); var invocation = requireTamabbohInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (tamabbohPort || drawingTamabboh || queuePort || invocation.drawn || invocation.expectedRangeX != null)
            throw new IllegalStateException("Tamabboh angle is outside its genuine floor call");
        var live = validateTamabbohCut(invocation, call.callCut(), call.expectedSource());
        if ((live.subtype() == 1 ? live.routine() != 2 : live.routine() != 2 && live.routine() != 4)
                || frame.environment().movement().globals().primaryAngle() != call.expectedPrimaryAngle())
            throw new IllegalStateException("Tamabboh angle differs from its reached routine/current Frame byte");
        var before = placement.capture();
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                monitorAngleEnvironment(invocation.shared.environment(), call.value()), invocation.shared.artState(),
                invocation.shared.paused(), invocation.shared.vIntClock());
        tamabbohPort = true;
        try {
            verifyTamabbohCallback(invocation, before);
            frame.applyObjectPrimaryAngle(call.value()); invocation.shared = readyShared;
            verifyTamabbohCallback(invocation, before);
        } catch (RuntimeException | Error original) {
            verifyTamabbohCallbackFailure(invocation, before, original); throw original;
        } finally { tamabbohPort = false; }
    }
    private int readTamabbohPlayerWord(CdTamabboh.PlayerReadCall call) {
        Objects.requireNonNull(call); var invocation = requireTamabbohInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (tamabbohPort || drawingTamabboh || queuePort || invocation.drawn)
            throw new IllegalStateException("Tamabboh player read is outside its actual range call");
        var live = validateTamabbohCut(invocation, call.callCut(), call.expectedSource());
        if (live.routine() != 4 || live.subtype() != 0 || live.word(CdObjectState.WordField.TAMABBOH_COOLDOWN) != 0
                || frame.environment().globals().debugMode() != 0)
            throw new IllegalStateException("Tamabboh player read is not its reached Main range cut");
        var before = placement.capture(); tamabbohPort = true;
        try {
            int value;
            if (call.field() == CdTamabboh.PlayerField.Y_WORD) {
                if (invocation.expectedRangeX != null) throw new IllegalStateException("Tamabboh repeated Y before its reached X read");
                value = invocation.player.motion().centreY16_16() >>> 16;
                invocation.expectedRangeX = ((value - live.centreY() + 0x50) & 0xFFFF) < 0xA0 ? live : null;
            } else {
                if (invocation.expectedRangeX == null || !invocation.expectedRangeX.equals(live))
                    throw new IllegalStateException("Tamabboh X read did not follow its actual in-range Y read");
                value = invocation.player.motion().centreX16_16() >>> 16; invocation.expectedRangeX = null;
            }
            verifyTamabbohCallback(invocation, before); return value;
        } catch (RuntimeException | Error original) {
            verifyTamabbohCallbackFailure(invocation, before, original); throw original;
        } finally { tamabbohPort = false; }
    }
    private void playTamabbohFm(CdTamabboh.FmCall call) {
        Objects.requireNonNull(call); var invocation = requireTamabbohInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (tamabbohPort || drawingTamabboh || queuePort || invocation.drawn || invocation.sounded)
            throw new IllegalStateException("Tamabboh FM lacks its actual first Fire request");
        var live = validateTamabbohCut(invocation, call.callCut(), call.expectedSource());
        if (live.routine() != 4 || live.subtype() != 0 || live.word(CdObjectState.WordField.TAMABBOH_COOLDOWN) != 0x78
                || live.unsignedByte(CdObjectState.ByteField.ANIMATION) != 0 || live.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) >= 0
                || call.sound() != CdTamabboh.FmSound.FM_A0
                || invocation.pool.findObjSlot().orElse(-1) != call.firstFreeWholeIndex()
                || invocation.pool.state(call.firstFreeWholeIndex()).id() != 0)
            throw new IllegalStateException("Tamabboh FM_A0 lacks its actual parent prefix/untouched first-free candidate");
        var before = placement.capture(); var ledger = new ArrayList<>(sounds);
        ledger.add(new SonicCdGameModule.PastTamabbohFm(call.sound())); var ready = List.copyOf(ledger);
        tamabbohPort = true;
        try {
            verifyTamabbohCallback(invocation, before);
            sounds = ready; invocation.expectedSounds = ready; invocation.sounded = true;
            verifyTamabbohCallback(invocation, before);
        } catch (RuntimeException | Error original) {
            verifyTamabbohCallbackFailure(invocation, before, original); throw original;
        } finally { tamabbohPort = false; }
    }

    private void drawTamabboh(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireTamabbohInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (tamabbohPort || drawingTamabboh || queuePort || invocation.drawn || !identity.equals(invocation.entry.identity()))
            throw new IllegalStateException("Tamabboh DrawObject lacks its actual once-only physical source");
        var current = pool.state(invocation.whole);
        boolean clearedMissile = invocation.entry.subtype() == 1 && invocation.entry.routine() == 2
                && current.id() == 0 && Arrays.equals(current.copyBytes(), new byte[CdObjectState.BYTE_SIZE]);
        if (current.id() != CdTamabboh.OBJECT_ID && !clearedMissile)
            throw new IllegalStateException("Only the genuine cleared missile may draw its live ID0 image");
        invocation.drawSource = current; invocation.drawn = true; drawingTamabboh = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Tamabboh DrawObject did not clear bit7 exactly once");
            verifyTamabbohInvocation(invocation);
        } catch (RuntimeException | Error original) {
            try { verifyTamabbohInvocation(invocation); }
            catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { drawingTamabboh = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readTamabbohDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireTamabbohInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingTamabboh || tamabbohPort || queuePort || !invocation.drawn)
            throw new IllegalStateException("No actual collecting Tamabboh queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyTamabbohCallback(invocation, before); return live;
        } catch (RuntimeException | Error original) {
            verifyTamabbohCallbackFailure(invocation, before, original); throw original;
        } finally { queuePort = false; }
    }
    private void writeTamabbohOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireTamabbohInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingTamabboh || tamabbohPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
            throw new IllegalStateException("Only actual Tamabboh DrawObject may clear bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("Tamabboh changed its DrawObject source before BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready);
        queuePort = true;
        try {
            verifyTamabbohCallback(invocation, before);
            invocation.pool.write(next); invocation.screenCleared = true;
            verifyTamabbohInvocation(invocation); var now = placement.capture();
            if (!now.pool().slots().equals(ready.slots()) || !sameBytes(now.savedFlags(), before.savedFlags())
                    || !now.status().equals(before.status()))
                throw new IllegalStateException("Tamabboh DrawObject changed more than its exact source BCLR7 byte");
        } catch (RuntimeException | Error original) {
            try { verifyTamabbohInvocation(invocation); }
            catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { queuePort = false; }
    }

    /** One real ID3E dispatch. All prefix/scalar models below observe writes; none execute a producer. */
    private enum PataSite { DRAW, RETURN, FAILURE }
    private final class PataInvocation {
        private final CdPataBata body = pataBata;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final Values base = values;
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final List<SonicCdGameModule.PastSound> ledger = sounds;
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final CdPataBata.Globals inputs;
        private CdObjectState drawSource;
        private boolean drawn, screenCleared, tailArmed, bodyReady;
        private CdPataBata.UnsupportedBoundary expectedStop;
        private PataInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
            var gates = frame.environment().movement().gates();
            inputs = new CdPataBata.Globals(values.globals().retainedDev().goodFuture(),
                    gates.timeZone(), gates.timeWarpDirection(), camera.xWord());
        }
    }

    private void runPataBata(int whole) {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeFloat != null || activeTunnelDoor != null || activeMonitor != null || activePata != null || activeTamabboh != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || floatPort || drawingFloat || tunnelDoorPort || drawingTunnelDoor
                || monitorPort || drawingMonitor || drawingPata || tamabbohPort || drawingTamabboh || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdPataBata.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Pata-Bata dispatch");
        var invocation = new PataInvocation(whole); activePata = invocation;
        try {
            var result = pataBata.run(whole, invocation.inputs);
            verifyPataInvocation(invocation); validatePataSource(invocation, PataSite.RETURN);
            verifyPataResult(invocation, result);
        } catch (RuntimeException | Error original) {
            verifyPataFailure(invocation, original); throw original;
        } finally { activePata = null; }
    }

    private PataInvocation requirePataInvocation(CdObjectPool pool, int whole) {
        var invocation = activePata;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Pata-Bata callback at this physical pointer");
        verifyPataInvocation(invocation); return invocation;
    }
    private void verifyPataInvocation(PataInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (invocation == null || activePata != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeFloat != null || activeTunnelDoor != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || floatPort || drawingFloat || tunnelDoorPort || drawingTunnelDoor
                || monitorPort || drawingMonitor || invocation.body != pataBata || invocation.sourceFrame != frame
                || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || invocation.pool != placement.pool() || invocation.queue != drawQueue || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || values != invocation.base || values.phase() != BEGIN_LEVEL || sounds != invocation.ledger
                || !placement.status().equals(invocation.before.status()) || !presentation.samePastScroll(invocation.scroll)
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Pata-Bata changed another owner or retained a stale actual World/Frame cut");
        invocation.pool.validateRestore(invocation.before.pool());
    }
    private void verifyPataFailure(PataInvocation invocation, Throwable original) {
        try {
            verifyPataInvocation(invocation); validatePataSource(invocation, PataSite.FAILURE);
            if (original instanceof CdPataBata.UnsupportedPataBata stopped) {
                var effects = new ArrayList<CdPataBata.Effect>();
                effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.DESTROY_ON_GOOD_FUTURE));
                var boundary = stopped.boundary();
                if (boundary == CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID
                        || boundary == CdPataBata.UnsupportedBoundary.ANIMATE_OBJECT) {
                    var prefixes = pataBodyPrefixes(invocation);
                    if (invocation.drawn || invocation.expectedStop != boundary
                            || !prefixes.getLast().equals(invocation.pool.state(invocation.whole)))
                        throw new IllegalStateException("Pata-Bata read fault lacks its exact committed native prefix");
                } else {
                    if (!invocation.bodyReady || !invocation.drawn)
                        throw new IllegalStateException("Pata-Bata callback/tail fault preceded its actual body");
                    effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.ANIMATE_OBJECT));
                    if (boundary == CdPataBata.UnsupportedBoundary.CHECK_OBJ_DESPAWN2) {
                        if (!invocation.tailArmed) throw new IllegalStateException("Pata-Bata tail fault preceded returned DrawObject");
                        effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.DRAW_OBJECT));
                    }
                }
                if (stopped.wholeIndex() != invocation.whole || !stopped.completedEffects().equals(effects))
                    throw new IllegalStateException("Pata-Bata fault metadata changed its physical source/completed calls");
            }
        } catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /**
     * Literal observations of the cached body's committed source images, N ObjPataBata:11-77.
     * Never invokes run, AnimateObject, CalcSine or a gameplay publication. The legacy animator
     * publishes atomically: an absent selected byte leaves the complete pre-animation image.
     */
    private List<CdObjectState> pataBodyPrefixes(PataInvocation invocation) {
        var prefixes = new ArrayList<CdObjectState>(); var source = invocation.entry;
        prefixes.add(source); invocation.bodyReady = false; invocation.expectedStop = null;
        if (source.routine() == 0) {
            source = source.addByte(CdObjectState.ByteField.ROUTINE, 2)
                    .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() | 4)
                    .moveByte(CdObjectState.ByteField.PRIORITY, 3).moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0x2A)
                    .moveByte(CdObjectState.ByteField.X_RADIUS, 0x10).moveByte(CdObjectState.ByteField.WIDTH, 0x10)
                    .moveByte(CdObjectState.ByteField.Y_RADIUS, 0x10)
                    .moveWord(CdObjectState.WordField.PATA_BATA_ANCHOR_X, source.centreX())
                    .moveLong(CdObjectState.LongField.PATA_BATA_ANCHOR_Y, (source.centreY() << 16) | 0x8000);
            prefixes.add(source);
            var tiles = assets.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes();
            int selected = (short) tiles.unsignedWord(2) + (source.subtype2() << 1);
            if ((selected & 1) != 0 || selected < 0 || selected > tiles.length() - 2) {
                invocation.expectedStop = CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID; return prefixes;
            }
            source = source.moveWord(CdObjectState.WordField.TILE, tiles.unsignedWord(selected)); prefixes.add(source);
            boolean damaged = source.subtype() != 0;
            source = source.moveLong(CdObjectState.LongField.PATA_BATA_X_DELTA, damaged ? -0x4000 : -0x8000)
                    .moveWord(CdObjectState.WordField.PATA_BATA_PHASE_SPEED, damaged ? -0x100 : -0x200)
                    .moveWord(CdObjectState.WordField.PATA_BATA_SHIFT, damaged ? 4 : 3)
                    .moveByte(CdObjectState.ByteField.ANIMATION, damaged ? 1 : 0)
                    .moveLong(CdObjectState.LongField.MAPPINGS, assets.view(damaged
                            ? CdR11bAssets.Asset.MAP_SPR_PATA_BATA2 : CdR11bAssets.Asset.MAP_SPR_PATA_BATA1).range().nativeAddress());
            prefixes.add(source);
        }
        // Every nonzero routine enters Main, including odd/FF. No time-stop, player or proximity gate.
        int delta = source.longBits(CdObjectState.LongField.PATA_BATA_X_DELTA);
        source = source.addLong(CdObjectState.LongField.X_POSITION, delta); prefixes.add(source);
        int distance = (source.centreX() - source.word(CdObjectState.WordField.PATA_BATA_ANCHOR_X)) & 0xFFFF;
        if ((short) distance < 0) distance = (-distance) & 0xFFFF;
        if ((short) distance >= 0x80) {
            delta = -delta;
            source = source.moveLong(CdObjectState.LongField.PATA_BATA_X_DELTA, delta); prefixes.add(source);
            source = source.addLong(CdObjectState.LongField.X_POSITION, delta); prefixes.add(source);
            source = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() ^ 1)
                    .moveByte(CdObjectState.ByteField.FLAGS, source.flags() ^ 1)
                    .moveWord(CdObjectState.WordField.PATA_BATA_PHASE, 0); prefixes.add(source);
        }
        source = source.addWord(CdObjectState.WordField.PATA_BATA_PHASE,
                source.signedWord(CdObjectState.WordField.PATA_BATA_PHASE_SPEED)); prefixes.add(source);
        int angle = source.word(CdObjectState.WordField.PATA_BATA_PHASE) >>> 8;
        // Observe the original B source WORD, not a second CalcSine helper invocation.
        int sine = (short) assets.player().sineTable().unsignedWord(angle * 2);
        int swapped = ((sine & 0xFFFF) << 16) | (delta >>> 16);
        int count = source.word(CdObjectState.WordField.PATA_BATA_SHIFT) & 63;
        source = source.moveLong(CdObjectState.LongField.Y_POSITION,
                source.longBits(CdObjectState.LongField.PATA_BATA_ANCHOR_Y) + (count >= 32 ? swapped >> 31 : swapped >> count));
        prefixes.add(source);
        var animated = pataAnimationImage(source);
        if (animated == null) { invocation.expectedStop = CdPataBata.UnsupportedBoundary.ANIMATE_OBJECT; return prefixes; }
        prefixes.add(animated); invocation.bodyReady = true; return prefixes;
    }

    /** Read-only scalar Object Animate:14-95 observation over this one shared 18-byte table. */
    private CdObjectState pataAnimationImage(CdObjectState source) {
        var expected = source;
        int animation = source.unsignedByte(CdObjectState.ByteField.ANIMATION);
        int index = source.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME);
        if (animation != source.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION)) {
            index = 0;
            expected = expected.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, animation)
                    .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0).moveByte(CdObjectState.ByteField.ANIMATION_TIME, 0);
        }
        expected = expected.addByte(CdObjectState.ByteField.ANIMATION_TIME, -1);
        if (expected.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME) < 0x80) return expected;
        var bytes = assets.view(CdR11bAssets.Asset.ANI_PATA_BATA).bytes();
        if (animation >= 2) return null;
        int script = (short) bytes.unsignedWord(animation * 2);
        int timer = pataAnimationByte(bytes, script, 0), raw = pataAnimationByte(bytes, script, 1 + index);
        if (timer < 0 || raw < 0) return null;
        expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_TIME, timer);
        if (raw == 0xFF) {
            index = 0; raw = pataAnimationByte(bytes, script, 1); if (raw < 0) return null;
        } else if (raw == 0xFE) {
            int back = pataAnimationByte(bytes, script, 2 + index); if (back < 0) return null;
            index = (index - back) & 0xFF;
            raw = pataAnimationByte(bytes, script, 1 + index); if (raw < 0) return null;
        } else if (raw >= 0x80) {
            if (raw == 0xFD) {
                int next = pataAnimationByte(bytes, script, 2 + index); if (next < 0) return null;
                expected = expected.moveByte(CdObjectState.ByteField.ANIMATION, next);
            } else if (raw == 0xFC) expected = expected.addByte(CdObjectState.ByteField.ROUTINE, 2);
            else if (raw == 0xFB) expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0)
                    .moveByte(CdObjectState.ByteField.ROUTINE_2, 0);
            else if (raw == 0xFA) expected = expected.addByte(CdObjectState.ByteField.ROUTINE_2, 2);
            return expected;
        }
        int rotated = ((raw << 3) | (raw >>> 5)) & 0xFF;
        return expected.moveByte(CdObjectState.ByteField.MAP_FRAME, raw & 0x1F)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, (source.spriteFlags() & 0xFC) | ((rotated ^ source.flags()) & 3))
                .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, (index + 1) & 0xFF);
    }
    private static int pataAnimationByte(CdAssetBytes bytes, int script, int offset) {
        long address = (long) script + offset;
        return address < 0 || address >= bytes.length() ? -1 : bytes.unsignedByte((int) address);
    }

    private void validatePataSource(PataInvocation invocation, PataSite site) {
        boolean failure = site == PataSite.FAILURE;
        var actual = invocation.pool.state(invocation.whole); var bank = invocation.before.savedFlags();
        boolean conversion = invocation.inputs.goodFuture() != 0
                && (invocation.inputs.timeZone() != 1 || invocation.entry.subtype() != 0);
        if (conversion) {
            if (invocation.drawn || site == PataSite.DRAW) throw new IllegalStateException("Converted Pata-Bata reached DrawObject");
            byte[] deleted = new byte[CdObjectState.BYTE_SIZE], x = deleted.clone();
            x[8] = (byte) (invocation.entry.centreX() >>> 8); x[9] = (byte) invocation.entry.centreX();
            byte[] xy = x.clone(); xy[12] = (byte) (invocation.entry.centreY() >>> 8); xy[13] = (byte) invocation.entry.centreY();
            byte[] converted = xy.clone(); converted[0] = 0x18;
            byte[] chosen = converted;
            if (failure) {
                chosen = null;
                for (var prefix : List.of(invocation.entry.copyBytes(), deleted, x, xy, converted))
                    if (Arrays.equals(actual.copyBytes(), prefix)) { chosen = prefix; break; }
                if (chosen == null) throw new IllegalStateException("Pata-Bata conversion exceeded Delete64/X/Y/ID18");
            }
            verifyPataImages(invocation, chosen, bank); return;
        }
        if (!invocation.drawn) {
            var prefixes = pataBodyPrefixes(invocation);
            byte[] chosen = null;
            if (failure) {
                for (var prefix : prefixes) if (prefix.equals(actual)) { chosen = prefix.copyBytes(); break; }
            } else if (site == PataSite.DRAW && invocation.bodyReady) chosen = prefixes.getLast().copyBytes();
            if (chosen == null) throw new IllegalStateException("Pata-Bata source is outside its actual Init/motion/animation prefix");
            verifyPataImages(invocation, chosen, bank); return;
        }
        if (site == PataSite.DRAW || invocation.drawSource == null)
            throw new IllegalStateException("Pata-Bata repeated or lost its native DrawObject source");
        var source = invocation.drawSource;
        if (!invocation.tailArmed) {
            if (!failure) throw new IllegalStateException("Pata-Bata returned before its actual DrawObject returned");
            verifyPataImages(invocation, source.copyBytes(), bank); return;
        }
        // N CheckObjDespawn2:388-446 reloads the live post-Draw anchor and saved ID.
        boolean outside = (source.spriteFlags() & 0x80) == 0
                && (((source.word(CdObjectState.WordField.PATA_BATA_ANCHOR_X) & 0xFF80)
                - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean delete = outside || (invocation.inputs.timeZone() & 0x80) != 0;
        byte[] expectedBank = bank.copy(); boolean owned = true;
        if (delete && source.savedFlagsId() != 0) {
            int period = invocation.inputs.timeZone() & 0x7F;
            if ((invocation.inputs.timeZone() & 0x80) != 0) {
                period = (short) ((period - (byte) invocation.inputs.timeWarpDir()) & 0xFFFF);
                period = period < 0 ? 0 : Math.min(period, 2);
            }
            int at = 2 + 3 * source.savedFlagsId() + period;
            owned = period >= 0 && period <= 2 && at >= 0 && at < expectedBank.length;
            if (owned) expectedBank[at] &= 0x7F;
        }
        byte[] expectedSource = delete && owned ? new byte[CdObjectState.BYTE_SIZE] : source.copyBytes();
        var actualBank = placement.capture().savedFlags();
        if (failure && Arrays.equals(actual.copyBytes(), source.copyBytes()) && sameBytes(actualBank, bank)) {
            verifyPataImages(invocation, source.copyBytes(), bank); return;
        }
        if (failure && owned && Arrays.equals(actual.copyBytes(), source.copyBytes())
                && Arrays.equals(actualBank.copy(), expectedBank)) {
            verifyPataImages(invocation, source.copyBytes(), CdAssetBytes.copyOf(expectedBank)); return;
        }
        if (!owned && !failure) throw new IllegalStateException("Pata-Bata returned beyond an unowned native saved-period address");
        verifyPataImages(invocation, expectedSource, CdAssetBytes.copyOf(expectedBank));
    }

    private void verifyPataImages(PataInvocation invocation, byte[] source, CdAssetBytes bank) {
        verifyPataInvocation(invocation);
        var now = placement.capture();
        if (!sameBytes(now.savedFlags(), bank) || !now.status().equals(invocation.before.status()))
            throw new IllegalStateException("Pata-Bata changed unowned saved-bank bits or placement cursors");
        for (int i = 0; i < 96; i++) {
            var image = now.pool().slots().get(i);
            if (i == invocation.whole - 32) {
                if (!image.identity().equals(invocation.entry.identity()) || !Arrays.equals(image.copyBytes(), source))
                    throw new IllegalStateException("Pata-Bata changed its actual source identity/full64 prefix");
            } else if (!image.equals(invocation.before.pool().slots().get(i)))
                throw new IllegalStateException("Pata-Bata changed another physical image");
        }
    }
    private void verifyPataCallback(PataInvocation invocation, CdObjectPlacementRuntime.Snapshot before) {
        verifyPataImages(invocation, invocation.drawSource.copyBytes(), before.savedFlags());
        if (!before.status().equals(invocation.before.status())
                || !before.pool().slots().get(invocation.whole - 32).identity().equals(invocation.entry.identity()))
            throw new IllegalStateException("Pata-Bata callback lost its actual pre-call source cut");
    }
    private void verifyPataCallbackFailure(PataInvocation invocation, CdObjectPlacementRuntime.Snapshot before, Throwable original) {
        try { verifyPataCallback(invocation, before); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    private void drawPataBata(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requirePataInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (drawingPata || tamabbohPort || drawingTamabboh || queuePort || invocation.drawn || !identity.equals(invocation.entry.identity()))
            throw new IllegalStateException("Pata-Bata DrawObject lacks its actual once-only native source");
        validatePataSource(invocation, PataSite.DRAW);
        var before = placement.capture(); invocation.drawSource = pool.state(invocation.whole);
        invocation.drawn = true; drawingPata = true;
        try {
            var current = invocation.drawSource;
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Pata-Bata DrawObject did not clear bit7 once");
            verifyPataCallback(invocation, before); invocation.tailArmed = true;
        } catch (RuntimeException | Error original) {
            verifyPataCallbackFailure(invocation, before, original); throw original;
        } finally { drawingPata = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readPataDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requirePataInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingPata || tamabbohPort || drawingTamabboh || queuePort || !invocation.drawn) throw new IllegalStateException("No actual Pata-Bata collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            verifyPataCallback(invocation, before); var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyPataCallback(invocation, before); return live;
        } catch (RuntimeException | Error original) {
            verifyPataCallbackFailure(invocation, before, original); throw original;
        } finally { queuePort = false; }
    }
    private void writePataOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requirePataInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingPata || tamabbohPort || drawingTamabboh || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
            throw new IllegalStateException("Only actual Pata-Bata DrawObject may clear bit7 once");
        var before = placement.capture(); verifyPataCallback(invocation, before);
        var next = invocation.drawSource.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, invocation.drawSource.spriteFlags() & 0x7F);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.drawSource = next; invocation.screenCleared = true;
            verifyPataCallback(invocation, before);
        } catch (RuntimeException | Error original) {
            verifyPataCallbackFailure(invocation, before, original); throw original;
        } finally { queuePort = false; }
    }

    /** Returned effects are checked observations only: no effect grants writes, drawing or another dispatch. */
    private void verifyPataResult(PataInvocation invocation, CdPataBata.Result result) {
        var effects = new ArrayList<CdPataBata.Effect>();
        boolean conversion = invocation.inputs.goodFuture() != 0
                && (invocation.inputs.timeZone() != 1 || invocation.entry.subtype() != 0);
        boolean delete = false;
        if (conversion) effects.add(new CdPataBata.ExplosionConversion(invocation.entry.centreX(), invocation.entry.centreY()));
        else {
            if (!invocation.drawn || !invocation.screenCleared || !invocation.tailArmed || !invocation.bodyReady)
                throw new IllegalStateException("Pata-Bata returned without its complete actual body/Draw cut");
            effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.DESTROY_ON_GOOD_FUTURE));
            effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.ANIMATE_OBJECT));
            effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.DRAW_OBJECT));
            var source = invocation.drawSource;
            boolean outside = (source.spriteFlags() & 0x80) == 0
                    && (((source.word(CdObjectState.WordField.PATA_BATA_ANCHOR_X) & 0xFF80)
                    - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
            delete = outside || (invocation.inputs.timeZone() & 0x80) != 0;
            if (delete) {
                int period = -1;
                if (source.savedFlagsId() != 0) {
                    period = invocation.inputs.timeZone() & 0x7F;
                    if ((invocation.inputs.timeZone() & 0x80) != 0) {
                        period = (short) ((period - (byte) invocation.inputs.timeWarpDir()) & 0xFFFF);
                        period = period < 0 ? 0 : Math.min(period, 2);
                    }
                }
                effects.add(new CdPataBata.Despawn(outside ? CdPataBata.DespawnEntry.ANCHOR_OFFSCREEN
                        : CdPataBata.DespawnEntry.TIME_WARP, source.savedFlagsId(), period));
            }
            effects.add(new CdPataBata.CallReturned(CdPataBata.NativeCall.CHECK_OBJ_DESPAWN2));
        }
        if (!result.state().equals(invocation.pool.state(invocation.whole)) || !result.effects().equals(effects)
                || result.converted() != conversion || result.deleted() != delete)
            throw new IllegalStateException("Pata-Bata returned metadata disagrees with its actual completed native branch");
    }

    /** Actual cached ObjFloatBlock invocation; equal public values cannot create this private scope. */
    private enum FloatSite { SOLID, DRAW, RETURN, FAILURE }
    private final class FloatInvocation {
        private final CdFloatBlock body = floatBlock;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final Values base = values;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private Values expectedValues = base;
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdObjectState expectedSource;
        private CdObjectPool.Snapshot lastCut;
        private CdSolidObject.Outcome adoptedOutcome;
        private List<CdSolidObject.Result> completed = List.of();
        private int ordinal;
        private boolean falling, stopped, drawn, screenCleared, bodyVerified, noDraw, tailArmed, animationStopped;
        private FloatInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole); expectedSource = entry;
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runFloatBlock(int whole) {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activeDrain != null || activePrimary != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || floatPort || drawingFloat || queuePort || whole < 32 || whole >= 128
                || placement.pool().state(whole).id() != CdFloatBlock.OBJECT_ID || values.phase() != BEGIN_LEVEL
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Past FloatBlock invocation entry");
        var invocation = new FloatInvocation(whole); activeFloat = invocation;
        try {
            var env = frame.environment(); var gates = env.movement().gates();
            var result = floatBlock.run(whole, new CdFloatBlock.Globals(gates.timeZone(),
                            invocation.camera.xWord(), invocation.camera.yWord()),
                    new CdFloatBlock.SolidInputs(springPlayer(),
                            new CdSolidObject.Gates(env.globals().debugMode(), gates.miniSonic()), springGlobals()));
            verifyFloatInvocation(invocation); validateFloatSource(invocation, FloatSite.RETURN);
            if (invocation.stopped || !result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("FloatBlock returned without its exact actual source image");
            verifyFloatResult(invocation, result);
        } catch (RuntimeException | Error failure) {
            if (invocation.adoptedOutcome instanceof CdSolidObject.NativeStopped nativeStop) {
                var original = nativeStop.stop().originalException();
                if (failure != original) original.addSuppressed(failure);
                verifyFloatFailure(original); throw original;
            }
            verifyFloatFailure(failure); throw failure;
        } finally { activeFloat = null; }
    }

    private FloatInvocation requireFloatInvocation(CdObjectPool pool, int whole) {
        var invocation = activeFloat;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached FloatBlock callback at this physical pointer");
        verifyFloatInvocation(invocation); return invocation;
    }

    private void verifyFloatInvocation(FloatInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeFloat != invocation || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || !runningObjects || servicing || loadingSaved || activeDrain != null
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null
                || activePlatform != null || activeTunnelDoor != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation.body != floatBlock || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status) || !presentation.samePastScroll(invocation.scroll)
                || !sameBytes(placement.capture().savedFlags(), invocation.savedFlags)
                || !sameBytes(values.hudObjects(), invocation.base.hudObjects())
                || sounds != invocation.expectedSounds || values != invocation.expectedValues || values.phase() != BEGIN_LEVEL)
            throw new IllegalStateException("FloatBlock changed another owner or retained a stale actual World/Frame cut");
    }

    private void verifyFloatFailure(Throwable original) {
        try {
            if (activeFloat != null) {
                verifyFloatInvocation(activeFloat); validateFloatSource(activeFloat, FloatSite.FAILURE);
            }
        } catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private boolean matchesFloatPool(FloatInvocation invocation, CdObjectState source) {
        return CdObjectPool.overlay(invocation.currentPool, List.of(source)).slots().equals(invocation.pool.capture().slots());
    }
    private void acceptFloatPool(FloatInvocation invocation, CdObjectState source) {
        var ready = CdObjectPool.overlay(invocation.currentPool, List.of(source));
        invocation.pool.validateRestore(ready);
        if (!ready.slots().equals(invocation.pool.capture().slots()))
            throw new IllegalStateException("FloatBlock changed bytes beyond its exact reached a0 prefix/full96 cut");
        invocation.currentPool = ready; invocation.expectedSource = source;
    }
    private CdObjectState floatDeletedImage(FloatInvocation invocation, CdObjectState source) {
        var actual = invocation.pool.state(invocation.whole);
        if (!actual.identity().equals(source.identity()) || !Arrays.equals(actual.copyBytes(), new byte[CdObjectState.BYTE_SIZE]))
            throw new IllegalStateException("FloatBlock DeleteObject did not clear its entire actual64 image");
        return actual;
    }
    private boolean floatOutsideWindow(FloatInvocation invocation, CdObjectState source) {
        return (((source.centreX() & 0xFF80) - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
    }

    /** Source instruction stores only. No Solid, Movement, terrain, animator or other producer is executed here. */
    private CdObjectState floatSolidSource(FloatInvocation invocation) {
        var source = invocation.expectedSource;
        if (invocation.ordinal == 0) {
            int routine = invocation.entry.routine();
            if ((routine & 1) != 0 || routine > 10)
                throw new IllegalStateException("FloatBlock Top does not belong to this native routine");
            if (routine == 0) {
                source = source.addByte(CdObjectState.ByteField.ROUTINE, 2)
                        .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() | 4)
                        .moveLong(CdObjectState.LongField.MAPPINGS,
                                assets.view(CdR11bAssets.Asset.MAP_SPR_FLOAT_BLOCK).range().nativeAddress());
                if (source.subtype2() != 0)
                    throw new IllegalStateException("FloatBlock Top advanced past a missing Init tile read");
                var tiles = assets.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes();
                int target = (short) tiles.unsignedWord(CdFloatBlock.TILE_SELECTOR * 2);
                int end = CdObjectTileIds.endOfBody(tiles, CdFloatBlock.TILE_SELECTOR);
                if (target < 0 || target > end - 2)
                    throw new IllegalStateException("FloatBlock Init selected an absent declared tile word");
                source = source.moveWord(CdObjectState.WordField.TILE, tiles.unsignedWord(target))
                        .moveByte(CdObjectState.ByteField.PRIORITY, 1).moveByte(CdObjectState.ByteField.WIDTH, 12)
                        .moveByte(CdObjectState.ByteField.Y_RADIUS, 12).moveByte(CdObjectState.ByteField.MAP_FRAME, 5);
            } else if (routine == 8) source = source.moveByte(CdObjectState.ByteField.ANIMATION, 0);
        } else if (invocation.ordinal == 2) {
            if ((invocation.entry.routine() != 0 && invocation.entry.routine() != 2)
                    || invocation.shared.environment().movement().gates().timeZone() != 2 || (source.flags() & 8) == 0)
                throw new IllegalStateException("FloatBlock third Top lacks Main's actual StartFall branch");
            source = source.addByte(CdObjectState.ByteField.ROUTINE, 2);
        } else if (invocation.ordinal != 1 && (invocation.ordinal != 3 || !invocation.falling))
            throw new IllegalStateException("FloatBlock Top ordinal is outside the actual native pair/fall-through");
        return source;
    }

    /** Validate complete live images at native cuts, including same-value stores, no-Draw paths and failed reads. */
    private void validateFloatSource(FloatInvocation invocation, FloatSite site) {
        boolean failure = site == FloatSite.FAILURE;
        var actual = invocation.pool.state(invocation.whole);
        if (failure && matchesFloatPool(invocation, invocation.expectedSource)) return;
        if (invocation.stopped) throw new IllegalStateException("Stopped FloatBlock executed later source work");
        if (site == FloatSite.SOLID) {
            if (invocation.drawn || invocation.bodyVerified)
                throw new IllegalStateException("FloatBlock Top follows its returned body");
            var source = floatSolidSource(invocation);
            acceptFloatPool(invocation, source);
            if (invocation.ordinal == 2) invocation.falling = true;
            return;
        }
        if (invocation.drawn || invocation.bodyVerified) {
            var source = invocation.expectedSource;
            if (!invocation.tailArmed) {
                if (!failure) throw new IllegalStateException("FloatBlock returned before DrawObject returned");
            } else if (floatOutsideWindow(invocation, source)) source = floatDeletedImage(invocation, source);
            acceptFloatPool(invocation, source); return;
        }
        int routine = invocation.entry.routine();
        if ((routine & 1) != 0 || routine > 12)
            throw new IllegalStateException("Unsupported FloatBlock routine changed its native source");
        if (failure && invocation.ordinal == 0 && routine == 0 && invocation.entry.subtype2() != 0) {
            var source = invocation.entry.addByte(CdObjectState.ByteField.ROUTINE, 2)
                    .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, invocation.entry.spriteFlags() | 4)
                    .moveLong(CdObjectState.LongField.MAPPINGS,
                            assets.view(CdR11bAssets.Asset.MAP_SPR_FLOAT_BLOCK).range().nativeAddress());
            acceptFloatPool(invocation, source); return;
        }
        int required = routine == 12 ? 0 : invocation.falling ? 4 : 2;
        if (invocation.ordinal != required) {
            if (failure) { acceptFloatPool(invocation, floatSolidSource(invocation)); return; }
            throw new IllegalStateException("FloatBlock skipped an actual TopSolid pair");
        }
        var source = invocation.expectedSource; boolean draw = true;
        int rawPeriod = invocation.shared.environment().movement().gates().timeZone();
        if (routine == 12) {
            source = source.moveByte(CdObjectState.ByteField.ROUTINE, 2); draw = false;
        } else if (routine == 4 || invocation.falling) {
            source = source.addWord(CdObjectState.WordField.Y, 2);
            if (((invocation.camera.yWord() + 0xE0) & 0xFFFF) < source.centreY()) {
                source = floatDeletedImage(invocation, source); draw = false;
            }
        } else if (routine == 0 || routine == 2) {
            if (rawPeriod != 0 && rawPeriod != 2) {
                source = source.moveByte(CdObjectState.ByteField.MAP_FRAME, 0);
                if ((source.flags() & 8) != 0) source = source.moveByte(CdObjectState.ByteField.ROUTINE, 6)
                        .moveByte(CdObjectState.ByteField.ANIMATION, 1);
            } else if (rawPeriod == 2 && (source.flags() & 8) != 0) {
                if (failure) { acceptFloatPool(invocation, floatSolidSource(invocation)); return; }
                throw new IllegalStateException("FloatBlock Main skipped its native Fall pair");
            }
        } else if (routine == 6 && (source.flags() & 8) == 0) {
            source = source.moveByte(CdObjectState.ByteField.ROUTINE, 2); draw = false;
        } else if (routine == 8 && (source.flags() & 8) == 0) {
            source = source.addByte(CdObjectState.ByteField.ROUTINE, 2).moveByte(CdObjectState.ByteField.ANIMATION, 2);
            draw = false;
        } else {
            source = validateFloatAnimation(invocation, source, failure);
            if (invocation.animationStopped) { acceptFloatPool(invocation, source); return; }
        }
        if (site == FloatSite.DRAW && !draw)
            throw new IllegalStateException("FloatBlock DrawObject is outside its actual native branch");
        if (site == FloatSite.RETURN && draw)
            throw new IllegalStateException("FloatBlock returned without its actual DrawObject");
        if (!draw && floatOutsideWindow(invocation, source)) source = floatDeletedImage(invocation, source);
        acceptFloatPool(invocation, source);
        invocation.bodyVerified = true; invocation.noDraw = !draw;
        if (!draw) invocation.tailArmed = true;
    }

    /**
     * Bounded verification of already-reached Object Animate:14-95 stores over the finite 30-byte owner.
     * This never calls the animator or returns a gameplay State. Missing reads admit only their exact native prefix.
     */
    private CdObjectState validateFloatAnimation(FloatInvocation invocation, CdObjectState source, boolean failure) {
        var expected = source; int animation = source.unsignedByte(CdObjectState.ByteField.ANIMATION);
        if (animation != source.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION))
            expected = expected.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, animation)
                    .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0).moveByte(CdObjectState.ByteField.ANIMATION_TIME, 0);
        expected = expected.addByte(CdObjectState.ByteField.ANIMATION_TIME, -1);
        if (expected.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME) < 0x80) return expected;
        var bytes = assets.view(CdR11bAssets.Asset.ANI_FLOAT_BLOCK).bytes();
        if (animation >= 3) return floatMissingAnimation(invocation, expected, failure);
        int script = (short) bytes.unsignedWord(animation * 2);
        if (script < 0 || script >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
        expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_TIME, bytes.unsignedByte(script));
        int index = expected.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME), at = script + 1 + index;
        if (at < 0 || at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
        int raw = bytes.unsignedByte(at);
        if (raw == 0xFF) {
            expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0); index = 0; at = script + 1;
            if (at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
            raw = bytes.unsignedByte(at);
        } else if (raw == 0xFE) {
            at = script + 2 + index;
            if (at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
            index = (index - bytes.unsignedByte(at)) & 0xFF;
            expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, index); at = script + 1 + index;
            if (at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
            raw = bytes.unsignedByte(at);
        } else if (raw >= 0x80) {
            if (raw == 0xFD) {
                at = script + 2 + index;
                if (at >= bytes.length()) return floatMissingAnimation(invocation, expected, failure);
                expected = expected.moveByte(CdObjectState.ByteField.ANIMATION, bytes.unsignedByte(at));
            } else if (raw == 0xFC) expected = expected.addByte(CdObjectState.ByteField.ROUTINE, 2);
            else if (raw == 0xFB) expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0)
                    .moveByte(CdObjectState.ByteField.ROUTINE_2, 0);
            else if (raw == 0xFA) expected = expected.addByte(CdObjectState.ByteField.ROUTINE_2, 2);
            return expected;
        }
        int rotated = ((raw << 3) | (raw >>> 5)) & 0xFF;
        return expected.moveByte(CdObjectState.ByteField.MAP_FRAME, raw & 0x1F)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, (source.spriteFlags() & 0xFC) | ((rotated ^ source.flags()) & 3))
                .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, (index + 1) & 0xFF);
    }
    private CdObjectState floatMissingAnimation(FloatInvocation invocation, CdObjectState prefix, boolean failure) {
        if (!failure) throw new IllegalStateException("FloatBlock advanced past an absent finite animation read");
        invocation.animationStopped = true; return prefix;
    }

    private List<CdObjectState> validateFloatImages(FloatInvocation invocation, CdFloatBlock.PrefixTopSolidCall call,
            CdObjectState source, CdObjectState object, List<CdObjectState> others, Set<CdObjectState.ByteField> writes) {
        if (!source.identity().equals(object.identity()) || source.id() != CdFloatBlock.OBJECT_ID
                || writes.stream().anyMatch(field -> field != CdObjectState.ByteField.SOLID_TYPE && field != CdObjectState.ByteField.FLAGS)
                || !writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                || object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) > 1 || ((source.flags() ^ object.flags()) & ~8) != 0)
            throw new IllegalStateException("FloatBlock Top changed identity, canonical masks or unowned flag bits");
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int at = 0; at < CdObjectState.BYTE_SIZE; at++) {
            int mask = at == 0x25 ? 0xFF : at == 0x22 && writes.contains(CdObjectState.ByteField.FLAGS) ? 8 : 0;
            if (((before[at] ^ after[at]) & ~mask & 0xFF) != 0)
                throw new IllegalStateException("FloatBlock Top changed undeclared a0 byte " + at);
        }
        if (others.size() > 1) throw new IllegalStateException("FloatBlock Top returned extra native stand releases");
        var images = new ArrayList<CdObjectState>(); images.add(object);
        var seen = new HashSet<Integer>(); seen.add(invocation.whole);
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || !seen.add(whole) || whole != call.player().standObject())
                throw new IllegalStateException("FloatBlock release lacks its unique actual dynamic stand identity");
            var old = call.callCut().slots().get(whole - 32);
            if (!old.identity().equals(other.identity())
                    || !old.moveByte(CdObjectState.ByteField.FLAGS, old.flags() & ~8).equals(other))
                throw new IllegalStateException("FloatBlock previous-stand release changed bytes beyond BCLR3");
            images.add(other);
        }
        return List.copyOf(images);
    }

    private void validateFloatStopped(CdFloatBlock.PrefixTopSolidCall call, CdSolidObject.NativeStopped stopped,
                                      CdObjectState source) {
        var prefix = stopped.prefix(); var stop = stopped.stop();
        if (prefix.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT || stop.originalException() == null)
            throw new IllegalStateException("FloatBlock stop is not its actual TopSolidObject prefix");
        if (stop.site() == CdSolidObject.StopSite.STAND_RELEASE) {
            if (!(stop.detail() instanceof CdSolidObject.SlotAddress address)
                    || address.routine() != CdSolidObject.Routine.STAND_ON_OBJECT
                    || address.slotByte() != call.player().standObject() || address.slotByte() == 0
                    || address.slotByte() >= 32 && address.slotByte() < 128
                    || address.boundary() != (address.slotByte() < 32 ? CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS
                            : CdSolidObject.UnsupportedBoundary.OUT_OF_POOL_ADDRESS)
                    || prefix.object().unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0 || (prefix.object().flags() & 8) == 0
                    || prefix.calls().stream().noneMatch(c -> c instanceof CdSolidObject.RoutineCall entered
                            && entered.routine() == CdSolidObject.Routine.STAND_ON_OBJECT))
                throw new IllegalStateException("FloatBlock stop lacks its actual previous-stand dereference prefix");
        } else if (stop.detail() instanceof CdSolidObject.SlotAddress
                || prefix.object().unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 1
                || prefix.object().flags() != source.flags())
            throw new IllegalStateException("FloatBlock terrain stop crossed the native pre-StandOn object prefix");
    }

    /** Prepare the full96, existing Frame publication, immutable sound ledger and call trace before any live write. */
    private void commitFloatSolid(CdFloatBlock.PrefixTopSolidCall call) {
        Objects.requireNonNull(call);
        var invocation = requireFloatInvocation(placement.pool(), activeFloat == null ? -1 : activeFloat.whole);
        if (floatPort || drawingFloat || queuePort || invocation.stopped || invocation.drawn
                || invocation.ordinal >= 4 || call.callCut() == invocation.lastCut || call.outcome() == invocation.adoptedOutcome)
            throw new IllegalStateException("FloatBlock Top lacks its next actual private helper cut");
        validateFloatSource(invocation, FloatSite.SOLID);
        var pool = invocation.pool; var cut = call.callCut(); pool.validateRestore(cut);
        if (!cut.slots().equals(invocation.currentPool.slots()) || !pool.capture().slots().equals(cut.slots())
                || !call.player().equals(springPlayer()) || !call.globals().equals(springGlobals()))
            throw new IllegalStateException("FloatBlock Top differs from its actual full96/player/global cut");
        var source = cut.slots().get(invocation.whole - 32);
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
        boolean stopped; var completed = new ArrayList<>(invocation.completed);
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var result = done.completedResult();
            if (result.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT
                    || result.path() != CdSolidObject.Path.GET_OFF && result.path() != CdSolidObject.Path.STAND_ON
                    || result.object().unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0)
                throw new IllegalStateException("FloatBlock completed a different native helper/path");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.player(), call.globals(), result);
            completed.add(result); stopped = false;
        } else {
            var nativeStop = (CdSolidObject.NativeStopped) call.outcome(); validateFloatStopped(call, nativeStop, source);
            var prefix = nativeStop.prefix();
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.player(), call.globals(), prefix); stopped = true;
        }
        var images = validateFloatImages(invocation, call, source, object, others, writes);
        var readyPool = CdObjectPool.overlay(cut, images); pool.validateRestore(readyPool);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger); var readyValues = preparedFloatValues(FLOAT_BLOCK_SOLID);
        var readyCompleted = List.copyOf(completed); int readyOrdinal = invocation.ordinal + 1;
        floatPort = true;
        try {
            verifyFloatInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots()) || !sameBytes(placement.capture().savedFlags(), invocation.savedFlags))
                throw new IllegalStateException("FloatBlock full96/saved source changed before publication");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds; values = readyValues;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.expectedValues = readyValues; invocation.currentPool = readyPool; invocation.expectedSource = object;
            invocation.ordinal = readyOrdinal; invocation.completed = readyCompleted; invocation.stopped = stopped;
            invocation.lastCut = cut; invocation.adoptedOutcome = call.outcome();
            verifyFloatInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()))
                throw new IllegalStateException("FloatBlock Top did not publish its exact complete output96");
        } catch (RuntimeException | Error failure) {
            verifyFloatFailure(failure); throw failure;
        } finally { floatPort = false; }
    }

    private Values preparedFloatValues(SonicCdGameModule.PastLoadCall call) {
        var old = values; var calls = new ArrayList<>(old.calls()); calls.add(call);
        return new Values(old.phase(), old.section(), old.cpuPalette(), old.fadePalette(), old.displayedPalette(), old.cpuSat(),
                old.displayedSat(), old.displayedHScroll(), old.displayedVScrollLongBits(), old.foregroundPlane(), old.ring(),
                old.level(), old.globals(), old.hudObjects(), old.fadeProgress(), List.copyOf(calls), old.loop());
    }

    private void verifyFloatCallback(FloatInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                     CdObjectState cleared) {
        verifyFloatInvocation(invocation);
        var now = placement.capture(); var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(expected)
                || !sameBytes(now.savedFlags(), before.savedFlags())
                || !now.pool().slots().equals(invocation.currentPool.slots()))
            throw new IllegalStateException("FloatBlock callback changed full96/bank outside its actual bit7 clear");
    }
    private void verifyFloatCallbackFailure(FloatInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                            CdObjectState cleared, Throwable original) {
        try { verifyFloatCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private void drawFloatBlock(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireFloatInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (floatPort || drawingFloat || queuePort || invocation.drawn || invocation.stopped
                || !identity.equals(invocation.entry.identity()))
            throw new IllegalStateException("FloatBlock DrawObject lacks its actual once-only native source");
        validateFloatSource(invocation, FloatSite.DRAW);
        var before = placement.capture(); var current = invocation.expectedSource;
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true; drawingFloat = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("FloatBlock DrawObject did not clear bit7 once");
            verifyFloatCallback(invocation, before, cleared); invocation.tailArmed = true;
        } catch (RuntimeException | Error failure) {
            verifyFloatCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure); throw failure;
        } finally { drawingFloat = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readFloatDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireFloatInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingFloat || floatPort || queuePort) throw new IllegalStateException("No actual FloatBlock queue read");
        var before = placement.capture(); queuePort = true;
        try {
            if (!before.pool().slots().equals(invocation.currentPool.slots()))
                throw new IllegalStateException("FloatBlock queue read lost its exact live96");
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyFloatCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) {
            verifyFloatCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }
    private void writeFloatOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireFloatInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingFloat || floatPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual FloatBlock DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        if (!before.pool().slots().equals(invocation.currentPool.slots()) || !current.equals(invocation.expectedSource))
            throw new IllegalStateException("FloatBlock bit7 store lost its complete current source");
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        var readyPool = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(readyPool);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.screenCleared = true;
            invocation.currentPool = readyPool; invocation.expectedSource = next;
            verifyFloatCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            verifyFloatCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure); throw failure;
        } finally { queuePort = false; }
    }

    /** Read-only completed metadata check, tied to the actual private helper results; never replay effects. */
    private void verifyFloatResult(FloatInvocation invocation, CdFloatBlock.Result result) {
        var expected = new ArrayList<CdFloatBlock.Effect>();
        int routine = invocation.entry.routine();
        expected.add(new CdFloatBlock.RoutineEntered(CdFloatBlock.NativeRoutine.values()[routine / 2]));
        if (routine == 0) expected.add(new CdFloatBlock.RoutineEntered(CdFloatBlock.NativeRoutine.MAIN));
        for (int i = 0; i < invocation.completed.size(); i++) {
            if (i == 2) expected.add(new CdFloatBlock.RoutineEntered(CdFloatBlock.NativeRoutine.FALL));
            expected.add(new CdFloatBlock.TopSolid(i, invocation.completed.get(i)));
        }
        if (routine == 6 && !invocation.noDraw || routine == 8 && !invocation.noDraw || routine == 10)
            expected.add(new CdFloatBlock.CallReturned(CdFloatBlock.NativeCall.ANIMATE_OBJECT));
        if (invocation.drawn) expected.add(new CdFloatBlock.CallReturned(CdFloatBlock.NativeCall.DRAW_OBJECT));
        // Deletion authority came from the checked live Y/window branch, never this returned metadata.
        var source = invocation.expectedSource;
        boolean below = (routine == 4 || invocation.falling)
                && ((invocation.camera.yWord() + 0xE0) & 0xFFFF) < ((invocation.entry.centreY() + 2) & 0xFFFF);
        if (below) expected.add(new CdFloatBlock.Deleted(CdFloatBlock.DeleteSite.FALL_BELOW_CAMERA));
        int x = below ? 0 : invocation.entry.centreX();
        if ((((x & 0xFF80) - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280)
            expected.add(new CdFloatBlock.Deleted(CdFloatBlock.DeleteSite.CHUNK_WINDOW));
        if (!result.effects().equals(expected) || !result.state().equals(source)
                || invocation.ordinal != invocation.completed.size() || !invocation.noDraw && !invocation.drawn)
            throw new IllegalStateException("FloatBlock returned metadata without its actual complete native call grammar");
        int at = 0;
        for (var effect : result.effects()) if (effect instanceof CdFloatBlock.TopSolid top
                && top.result() != invocation.completed.get(at++))
            throw new IllegalStateException("FloatBlock returned a substituted completed helper observation");
    }

    /** Actual cached Platform producer; no captured owner or public value grants this scope. */
    private final class PlatformInvocation {
        private final CdPlatform body = platform;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final Values base = values;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private final boolean capacityDelete;
        private CdObjectState destroySource;
        private boolean directDeleteArmed;
        private boolean stopped;
        private boolean drawn;
        private boolean screenCleared;

        private PlatformInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            capacityDelete = entry.routine() == 0 && entry.subtype2() != 0
                    && pool.capture().slots().stream().noneMatch(image -> image.id() == 0);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runPlatform(int whole) {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdPlatform.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Past Platform invocation entry");
        var invocation = new PlatformInvocation(whole);
        activePlatform = invocation;
        try {
            var env = frame.environment(); var gates = env.movement().gates();
            var result = platform.run(whole, new CdPlatform.Globals(gates.zoneAct(), values.ring().timeStopTimer(),
                            invocation.camera.xWord(), invocation.camera.yWord(), gates.timeZone()),
                    new CdPlatform.Inputs(springPlayer(), new CdSolidObject.Gates(
                            env.globals().debugMode(), gates.miniSonic()), springGlobals()));
            verifyPlatformInvocation(invocation);
            if (!invocation.drawn || invocation.stopped || !result.state().equals(placement.pool().state(whole)))
                throw new IllegalStateException("Incomplete actual Platform return");
        } catch (RuntimeException | Error failure) {
            verifyPlatformFailure(failure); throw failure;
        } finally { activePlatform = null; }
    }

    private PlatformInvocation requirePlatformInvocation(CdObjectPool pool, int whole) {
        var invocation = activePlatform;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Platform callback at this physical pointer");
        var source = pool.state(whole);
        if (source.id() != CdPlatform.OBJECT_ID && !platformDeletedSource(invocation, source))
            throw new IllegalStateException("Platform callback lost its live or genuinely deleted native source");
        verifyPlatformInvocation(invocation);
        return invocation;
    }

    private void verifyPlatformInvocation(PlatformInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activePlatform != invocation || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || invocation.body != platform || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status) || !presentation.samePastScroll(invocation.scroll)
                || sounds != invocation.expectedSounds || values != invocation.base)
            throw new IllegalStateException("Platform changed another owner or retained a stale actual cut");
        validatePlatformSavedFlags(invocation, invocation.pool.state(invocation.whole));
    }

    private void verifyPlatformFailure(Throwable original) {
        try { if (activePlatform != null) verifyPlatformInvocation(activePlatform); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    /** ID0 is admitted only after an actual private native deletion path, never by image equality alone. */
    private boolean platformDeletedSource(PlatformInvocation invocation, CdObjectState source) {
        return (invocation.capacityDelete || invocation.directDeleteArmed || invocation.destroySource != null)
                && source.identity().equals(invocation.entry.identity())
                && Arrays.equals(source.copyBytes(), new byte[CdObjectState.BYTE_SIZE]);
    }

    /** Destroy changes exactly one addressed membership bit; direct vertical DeleteObject changes none. */
    private void validatePlatformSavedFlags(PlatformInvocation invocation, CdObjectState source) {
        var current = placement.capture().savedFlags();
        if (sameBytes(current, invocation.savedFlags)) return;
        var destroyed = invocation.capacityDelete ? invocation.entry : invocation.destroySource;
        if (!platformDeletedSource(invocation, source) || destroyed == null || destroyed.savedFlagsId() == 0)
            throw new IllegalStateException("Platform changed its saved bank before a genuine Destroy");
        int index = 2 + 3 * destroyed.savedFlagsId() + invocation.shared.environment().movement().gates().timeZone();
        if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES)
            throw new IllegalStateException("Platform Destroy did not reach an in-bounds raw saved byte");
        byte[] expected = invocation.savedFlags.copy(); expected[index] &= 0x7F;
        if (!Arrays.equals(expected, current.copy()))
            throw new IllegalStateException("Platform changed saved bytes beyond its exact native BCLR7");
    }

    /** Native post-instruction cuts, including repeated Tops, timer1->0 and deleted5X anchor tails. */
    private void validatePlatformSite(PlatformInvocation invocation, CdPlatform.SolidCall call, CdObjectState source) {
        boolean deleted = call.site() == CdPlatform.Site.ANCHOR_GET_OFF && platformDeletedSource(invocation, source);
        if (!deleted && (source.id() != CdPlatform.OBJECT_ID || source.routine() != 2))
            throw new IllegalStateException("Platform helper requires live ID21/routine2 or its actual cleared anchor tail");
        int stop = values.ring().timeStopTimer();
        if (call.site() == CdPlatform.Site.TIME_STOP_TOP) {
            if (stop == 0 || deleted || call.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT)
                throw new IllegalStateException("Platform time-stop helper requires its nonzero WORD/direct Top");
            return;
        }
        if (stop != 0) throw new IllegalStateException("Platform ordinary helper cannot run under native time-stop");
        int family = source.subtype() >>> 4;
        int state = source.unsignedByte(CdObjectState.ByteField.PLATFORM_SUBSTATE);
        int timer = source.unsignedByte(CdObjectState.ByteField.PLATFORM_TIMER);
        int phase = source.unsignedByte(CdObjectState.ByteField.PLATFORM_PHASE);
        boolean top = true;
        boolean valid = switch (call.site()) {
            case SUBTYPE_0_TOP -> family == 0;
            case SUBTYPE_1_TOP -> family == 1;
            case SUBTYPE_2_TOP -> family == 2;
            case SUBTYPE_3_TOP -> family == 3;
            case SUBTYPE_4_TOP -> family == 4;
            case SUBTYPE_5_STATIONARY_TOP -> family == 5 && state == 0;
            case SUBTYPE_5_TIMER_TOP -> family == 5 && state != 0 && timer != 0xFF;
            case SUBTYPE_5_DROP_TOP -> family == 5 && state != 0 && timer == 0;
            case SUBTYPE_5_DELETE_GET_OFF -> {
                top = false;
                yield family == 5 && state != 0 && timer == 0
                        && ((invocation.camera.yWord() + 0x100) & 0xFFFF) < source.centreY();
            }
            case SUBTYPE_6_STATIONARY_1_TOP -> family == 6 && state == 0;
            case SUBTYPE_6_MOVE_DOWN_TOP -> family == 6 && state == 2 && phase >= 2 && phase <= 0x41;
            case SUBTYPE_6_STATIONARY_2_TOP -> family == 6 && state == 4;
            case SUBTYPE_7_STATIONARY_1_TOP -> family == 7 && state == 0;
            case SUBTYPE_7_TIMER_TOP -> family == 7 && state == 2 && timer != 0xFF;
            case SUBTYPE_7_RISING_TOP -> family == 7 && state == 2 && timer == 0;
            case SUBTYPE_7_STATIONARY_2_TOP -> family == 7 && state == 4;
            case SUBTYPE_8_STATIONARY_1_TOP -> family == 8 && state == 0;
            case SUBTYPE_8_TIMER_TOP -> family == 8 && state == 2 && timer != 0xFF;
            case SUBTYPE_8_MOVE_X_TOP -> family == 8 && state == 2 && timer == 0 && phase >= 1 && phase <= 0x40;
            case SUBTYPE_8_STATIONARY_2_TOP -> family == 8 && state == 4;
            case SUBTYPE_9_STATIONARY_1_TOP -> family == 9 && state == 0;
            case SUBTYPE_9_TIMER_TOP -> family == 9 && state == 2 && timer != 0xFF;
            case SUBTYPE_9_MOVE_X_TOP -> family == 9 && state == 2 && timer == 0 && phase >= 1 && phase <= 0x40;
            case SUBTYPE_9_STATIONARY_2_TOP -> family == 9 && state == 4;
            case ANCHOR_GET_OFF -> {
                top = false;
                int difference = ((source.word(CdObjectState.WordField.PLATFORM_ANCHOR_X) & 0xFF80)
                        - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF;
                yield difference > 0x280 && (deleted || family <= 9
                        && (family < 6 || state == 0 || state == 2 || state == 4));
            }
            case TIME_STOP_TOP -> false;
        };
        if (!valid || call.entry() != (top ? CdSolidObject.Entry.TOP_SOLID_OBJECT : CdSolidObject.Entry.GET_OFF_OBJECT))
            throw new IllegalStateException("Platform helper differs from its actual native instruction site/cut");
    }

    private List<CdObjectState> validatePlatformImages(PlatformInvocation invocation, CdPlatform.SolidCall call,
            CdObjectState source, CdObjectState object, List<CdObjectState> others, Set<CdObjectState.ByteField> writes) {
        if (!source.identity().equals(object.identity())
                || writes.stream().anyMatch(field -> field != CdObjectState.ByteField.SOLID_TYPE && field != CdObjectState.ByteField.FLAGS)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) != (call.entry() == CdSolidObject.Entry.TOP_SOLID_OBJECT)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) > 1
                || ((object.flags() ^ source.flags()) & ~8) != 0
                || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && (object.flags() & ~source.flags()) != 0)
            throw new IllegalStateException("Platform Solid changed identity, canonical masks or unowned flag bits");
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int at = 0; at < CdObjectState.BYTE_SIZE; at++) {
            if (at == 0x25 && writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                    || at == 0x22 && writes.contains(CdObjectState.ByteField.FLAGS)) continue;
            if (before[at] != after[at]) throw new IllegalStateException("Platform Solid changed undeclared a0 byte " + at);
        }
        if (others.size() > 1 || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && !others.isEmpty())
            throw new IllegalStateException("Platform helper returned extra dynamic releases");
        var images = new ArrayList<CdObjectState>(1 + others.size()); images.add(object);
        var seen = new HashSet<Integer>(); seen.add(invocation.whole);
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || !seen.add(whole) || whole != call.expectedPlayer().standObject())
                throw new IllegalStateException("Platform release lacks its actual distinct dynamic stand identity");
            var original = call.actualCut().slots().get(whole - 32);
            if (!original.identity().equals(other.identity())
                    || !original.moveByte(CdObjectState.ByteField.FLAGS, original.flags() & ~8).equals(other))
                throw new IllegalStateException("Platform release changed bytes beyond native BCLR3");
            images.add(other);
        }
        return List.copyOf(images);
    }

    /** All pool/Frame/ledger values and expected full pairs are ready before the first owner assignment. */
    private void commitPlatformSolid(CdPlatform.SolidCall call) {
        Objects.requireNonNull(call);
        var invocation = requirePlatformInvocation(placement.pool(), activePlatform == null ? -1 : activePlatform.whole);
        if (platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped)
            throw new IllegalStateException("Platform Solid lacks its current actual helper cut");
        var pool = invocation.pool; var cut = call.actualCut();
        pool.validateRestore(cut);
        if (!pool.capture().slots().equals(cut.slots())
                || !call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Platform Solid differs from its actual full96/player/global cut");
        var source = cut.slots().get(invocation.whole - 32);
        validatePlatformSite(invocation, call, source);
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests;
        CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
        boolean stopped;
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var result = done.completedResult();
            if (result.entry() != call.entry()) throw new IllegalStateException("Platform completed entry mismatch");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            stopped = false;
        } else {
            var prefix = ((CdSolidObject.NativeStopped) call.outcome()).prefix();
            if (prefix.entry() != call.entry() || call.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT
                    || !prefix.fmRequests().isEmpty() || !prefix.otherSlots().isEmpty())
                throw new IllegalStateException("Platform stopped Top acquired a completed FM/release or wrong entry");
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix);
            stopped = true;
        }
        var images = validatePlatformImages(invocation, call, source, object, others, writes);
        var readyPool = CdObjectPool.overlay(cut, images); pool.validateRestore(readyPool);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger);
        platformPort = true;
        try {
            verifyPlatformInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots())) throw new IllegalStateException("Stale Platform publication pool");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.stopped = stopped;
            if (!stopped && call.site() == CdPlatform.Site.SUBTYPE_5_DELETE_GET_OFF) invocation.directDeleteArmed = true;
            if (!stopped && call.site() == CdPlatform.Site.ANCHOR_GET_OFF) invocation.destroySource = source;
            verifyPlatformInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()))
                throw new IllegalStateException("Platform Solid output images were not adopted exactly");
        } catch (RuntimeException | Error failure) {
            verifyPlatformFailure(failure); throw failure;
        } finally { platformPort = false; }
    }

    /** Each reached raw ceiling byte, with no preclear and no change to any physical object image. */
    private void commitPlatformPrimaryAngle(CdPlatform.PrimaryAngleCall call) {
        Objects.requireNonNull(call);
        var invocation = requirePlatformInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped)
            throw new IllegalStateException("Platform angle lacks its current rising call cut");
        var pool = invocation.pool; var cut = call.actualCut(); var source = pool.state(invocation.whole);
        pool.validateRestore(cut);
        if (source.id() != CdPlatform.OBJECT_ID || source.routine() != 2 || source.subtype() >>> 4 != 7
                || source.unsignedByte(CdObjectState.ByteField.PLATFORM_SUBSTATE) != 2
                || source.unsignedByte(CdObjectState.ByteField.PLATFORM_TIMER) != 0
                || values.ring().timeStopTimer() != 0 || !source.equals(call.expectedSource())
                || !cut.slots().get(invocation.whole - 32).equals(source)
                || !pool.capture().slots().equals(cut.slots())
                || frame.environment().movement().globals().primaryAngle() != call.expectedPrimaryAngle())
            throw new IllegalStateException("Platform angle differs from its actual rising source/full96/Frame cut");
        var before = placement.capture(); var actorBefore = frame.captureActor(); var sharedBefore = frame.captureShared();
        var oldEnvironment = frame.environment(); var oldMovement = oldEnvironment.movement(); var oldGlobals = oldMovement.globals();
        platformPort = true;
        try {
            frame.applyObjectPrimaryAngle(call.value());
            var environment = frame.environment(); var movement = environment.movement(); var globals = movement.globals();
            if (!frame.captureActor().equals(actorBefore) || !environment.controls().equals(oldEnvironment.controls())
                    || !environment.globals().equals(oldEnvironment.globals()) || !environment.dynamicSlots().equals(oldEnvironment.dynamicSlots())
                    || !movement.physics().equals(oldMovement.physics()) || !movement.bounds().equals(oldMovement.bounds())
                    || !movement.gates().equals(oldMovement.gates()) || globals.primaryAngle() != call.value()
                    || globals.lookMode() != oldGlobals.lookMode() || globals.cameraYCentre() != oldGlobals.cameraYCentre()
                    || globals.secondaryAngle() != oldGlobals.secondaryAngle() || globals.boredTimer() != oldGlobals.boredTimer()
                    || globals.scoreChain() != oldGlobals.scoreChain() || globals.debugAngle() != oldGlobals.debugAngle()
                    || globals.debugAngleShift() != oldGlobals.debugAngleShift() || globals.debugQuadrant() != oldGlobals.debugQuadrant()
                    || globals.debugFloorDistance() != oldGlobals.debugFloorDistance())
                throw new IllegalStateException("Platform angle changed a non-primary Frame value");
            var shared = frame.captureShared();
            if (shared.owner() != sharedBefore.owner() || !shared.artState().equals(sharedBefore.artState())
                    || shared.paused() != sharedBefore.paused() || !shared.vIntClock().equals(sharedBefore.vIntClock()))
                throw new IllegalStateException("Platform angle changed Frame art/pause/clock ownership");
            invocation.actor = actorBefore; invocation.shared = shared;
            verifyPlatformCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyPlatformCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { platformPort = false; }
    }

    private void verifyPlatformCallback(PlatformInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                        CdObjectState cleared) {
        verifyPlatformInvocation(invocation);
        var now = placement.capture(); var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(expected)
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Platform callback changed pool/bank outside the actual bit7 clear");
    }

    private void verifyPlatformCallbackFailure(PlatformInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                               CdObjectState cleared, Throwable original) {
        try { verifyPlatformCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private void drawPlatform(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requirePlatformInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped)
            throw new IllegalStateException("Platform DrawObject is not its actual once-only normal return");
        var before = placement.capture(); var current = pool.state(invocation.whole);
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true; drawingPlatform = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Platform DrawObject did not clear bit7");
            verifyPlatformCallback(invocation, before, cleared);
        } catch (RuntimeException | Error failure) {
            verifyPlatformCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure); throw failure;
        } finally { drawingPlatform = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot readPlatformDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requirePlatformInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingPlatform || platformPort || queuePort) throw new IllegalStateException("No actual Platform queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyPlatformCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) {
            verifyPlatformCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }

    private void writePlatformOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requirePlatformInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingPlatform || platformPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual Platform DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.screenCleared = true;
            verifyPlatformCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            verifyPlatformCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure); throw failure;
        } finally { queuePort = false; }
    }


    /** Only the actual cached body may enter these ports; public cut values carry no execution authority. */
    private final class TunnelDoorInvocation {
        private final CdTunnelDoor body = tunnelDoor;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final Values base = values;
        private Values expectedValues = base;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes savedFlags = placement.capture().savedFlags();
        private final CdAssetBytes entryReserved = values.hudObjects();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdObjectState expectedSource;
        private CdObjectState createdChild;
        private CdAssetBytes secondaryImage;
        private CdTunnelDoor.PrefixSolidCut pendingCut;
        private CdSolidObject.Outcome adoptedOutcome;
        private boolean prefixVerified;
        private boolean primaryOpened;
        private boolean primaryPreflight;
        private boolean primaryCommitted;
        private boolean secondaryRead;
        private boolean secondaryPreflight;
        private boolean secondaryCommitted;
        private boolean sounded;
        private boolean stopped;
        private boolean drawn;
        private boolean screenCleared;
        private boolean tailArmed;
        private boolean checkingFailure;

        private TunnelDoorInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole); expectedSource = entry;
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession();
            generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runTunnelDoor(int whole) {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdTunnelDoor.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Past Tunnel Door invocation entry");
        var invocation = new TunnelDoorInvocation(whole);
        activeTunnelDoor = invocation;
        try {
            var env = frame.environment(); var gates = env.movement().gates();
            var result = tunnelDoor.run(whole, new CdTunnelDoor.Globals(gates.timeZone(),
                            gates.timeWarpDirection(), invocation.camera.xWord()),
                    new CdTunnelDoor.SolidInputs(springPlayer(),
                            new CdSolidObject.Gates(env.globals().debugMode(), gates.miniSonic()), springGlobals()));
            verifyTunnelDoorInvocation(invocation);
            validateTunnelDoorPrefix(invocation, invocation.pool.capture(), invocation.pool.state(whole));
            if (!invocation.drawn || !invocation.tailArmed || invocation.stopped || invocation.pendingCut != null
                    || !result.state().equals(invocation.pool.state(whole))
                    || !result.finalPrimary().equals(springPlayer()) || !result.finalGlobals().equals(springGlobals()))
                throw new IllegalStateException("Incomplete actual Tunnel Door return");
            var source = invocation.expectedSource;
            int difference = ((source.centreX() & 0xFF80)
                    - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF;
            boolean despawn = (gates.timeZone() & 0x80) != 0
                    || (source.spriteFlags() & 0x80) == 0 && difference > 0x280;
            if (despawn != result.deleted()
                    || despawn != Arrays.equals(result.state().copyBytes(), new byte[CdObjectState.BYTE_SIZE]))
                throw new IllegalStateException("Tunnel Door did not execute its actual post-Draw despawn tail");
        } catch (RuntimeException | Error failure) {
            verifyTunnelDoorFailure(failure); throw failure;
        } finally { activeTunnelDoor = null; }
    }

    private TunnelDoorInvocation requireTunnelDoorInvocation(CdObjectPool pool, int whole) {
        var invocation = activeTunnelDoor;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Tunnel Door callback at this physical pointer");
        verifyTunnelDoorInvocation(invocation);
        return invocation;
    }

    private void verifyTunnelDoorInvocation(TunnelDoorInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeTunnelDoor != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || monitorPort || drawingMonitor || invocation.body != tunnelDoor
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status) || !presentation.samePastScroll(invocation.scroll)
                || sounds != invocation.expectedSounds || values != invocation.expectedValues
                || !sameBytes(values.hudObjects(), invocation.entryReserved) || values.phase() != BEGIN_LEVEL)
            throw new IllegalStateException("Tunnel Door changed another owner or retained a stale actual cut");
        validateTunnelDoorSavedFlags(invocation, invocation.pool.state(invocation.whole));
    }

    private void verifyTunnelDoorFailure(Throwable original) {
        try {
            if (activeTunnelDoor != null) {
                var invocation = activeTunnelDoor;
                invocation.checkingFailure = true;
                verifyTunnelDoorInvocation(invocation);
                validateTunnelDoorPrefix(invocation, invocation.pool.capture(), invocation.pool.state(invocation.whole));
            }
        } catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    /** This port is observational, including faults; ZERO admission belongs to the reached component read. */
    private CdAssetBytes readTunnelDoorSecondary() {
        var invocation = requireTunnelDoorInvocation(placement.pool(), activeTunnelDoor == null ? -1 : activeTunnelDoor.whole);
        if (tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.stopped || invocation.drawn)
            throw new IllegalStateException("No reached Tunnel Door secondary-source read");
        var before = placement.capture();
        tunnelDoorPort = true;
        try {
            validateTunnelDoorPrefix(invocation, before.pool(), invocation.pool.state(invocation.whole));
            if (invocation.entry.routine() != 0 && invocation.entry.routine() != 2
                    || !invocation.primaryCommitted && !invocation.primaryOpened
                    || invocation.createdChild != null && !invocation.sounded
                    || invocation.pendingCut != null && invocation.pendingCut.participant() != CdTunnelDoor.Participant.SECONDARY)
                throw new IllegalStateException("Tunnel Door secondary read precedes its actual completed primary");
            var image = reservedImage(1);
            if (image.length() != CdObjectState.BYTE_SIZE
                    || invocation.secondaryImage != null && !sameBytes(image, invocation.secondaryImage))
                throw new IllegalStateException("Tunnel Door actual reserved source changed across its native reads");
            invocation.secondaryRead = true;
            if (invocation.secondaryImage == null) invocation.secondaryImage = image;
            verifyTunnelDoorCallback(invocation, before, null);
            return image;
        } catch (RuntimeException | Error failure) {
            verifyTunnelDoorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { tunnelDoorPort = false; }
    }

    private CdTunnelDoor.LiveSolid preflightTunnelDoorSolid(CdTunnelDoor.PrefixSolidCut cut) {
        Objects.requireNonNull(cut);
        var invocation = requireTunnelDoorInvocation(placement.pool(), cut.objectIdentity().wholeIndex());
        if (tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped
                || invocation.pendingCut != null || !cut.objectIdentity().equals(invocation.entry.identity()))
            throw new IllegalStateException("Tunnel Door Solid lacks its once-only actual preflight");
        var before = placement.capture();
        tunnelDoorPort = true;
        try {
            invocation.pool.validateRestore(cut.callCut());
            if (!before.pool().slots().equals(cut.callCut().slots())
                    || !cut.expectedPrimary().equals(springPlayer()) || !cut.expectedGlobals().equals(springGlobals())
                    || !cut.expectedGates().equals(new CdSolidObject.Gates(
                            frame.environment().globals().debugMode(), frame.environment().movement().gates().miniSonic())))
                throw new IllegalStateException("Tunnel Door preflight differs from its full96/actual primary cut");
            validateTunnelDoorPrefix(invocation, cut.callCut(), invocation.pool.state(invocation.whole));
            if (cut.participant() == CdTunnelDoor.Participant.PRIMARY) {
                if (invocation.entry.routine() != 0 && invocation.entry.routine() != 2
                        || invocation.expectedSource.routine() != 2 || invocation.primaryPreflight
                        || invocation.primaryCommitted || invocation.primaryOpened
                        || invocation.secondaryRead || cut.secondary().isPresent())
                    throw new IllegalStateException("Tunnel Door primary Top is not its native first participant");
            } else {
                if (!invocation.secondaryRead || invocation.secondaryPreflight || invocation.secondaryCommitted
                        || invocation.expectedSource.routine() != 2 && invocation.expectedSource.routine() != 4
                        || !invocation.primaryCommitted && !invocation.primaryOpened
                        || cut.secondary().isEmpty() || !sameBytes(cut.secondary().orElseThrow(), reservedImage(1))
                        || !sameBytes(cut.secondary().orElseThrow(), invocation.secondaryImage))
                    throw new IllegalStateException("Tunnel Door secondary Top lacks its reached actual raw source");
                for (int at = 0; at < CdObjectState.BYTE_SIZE; at++)
                    if (cut.secondary().orElseThrow().unsignedByte(at) != 0)
                        throw new IllegalStateException("Tunnel Door secondary is not the actual cleared64 image");
            }
            var live = new CdTunnelDoor.LiveSolid(springPlayer(), cut.expectedGates(), springGlobals());
            verifyTunnelDoorCallback(invocation, before, null);
            invocation.pendingCut = cut;
            if (cut.participant() == CdTunnelDoor.Participant.PRIMARY) invocation.primaryPreflight = true;
            else invocation.secondaryPreflight = true;
            return live;
        } catch (RuntimeException | Error failure) {
            verifyTunnelDoorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { tunnelDoorPort = false; }
    }

    /** Ready pool, complete Frame pair, calls and sound ledger precede the first owner assignment. */
    private CdTunnelDoor.LiveSolid commitTunnelDoorSolid(CdTunnelDoor.PrefixSolidCall call) {
        Objects.requireNonNull(call);
        var cut = call.cut();
        var invocation = requireTunnelDoorInvocation(placement.pool(), cut.objectIdentity().wholeIndex());
        if (tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped
                || invocation.pendingCut != cut || invocation.adoptedOutcome == call.outcome())
            throw new IllegalStateException("Tunnel Door commit lacks the actual pending preflight identity");
        var pool = invocation.pool;
        pool.validateRestore(cut.callCut());
        if (!pool.capture().slots().equals(cut.callCut().slots())
                || !cut.callCut().slots().equals(invocation.currentPool.slots())
                || !cut.expectedPrimary().equals(springPlayer()) || !cut.expectedGlobals().equals(springGlobals())
                || !cut.expectedGates().equals(new CdSolidObject.Gates(
                        frame.environment().globals().debugMode(), frame.environment().movement().gates().miniSonic())))
            throw new IllegalStateException("Stale Tunnel Door full pair/source publication");
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests;
        CdPlayerFrameRuntime.ObjectSolidPublication readyFrame = null;
        boolean stopped;
        boolean primary = cut.participant() == CdTunnelDoor.Participant.PRIMARY;
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var result = done.completedResult();
            if (result.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT)
                throw new IllegalStateException("Tunnel Door completed a different native helper entry");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            if (primary) readyFrame = frame.prepareObjectSolidResult(cut.expectedPrimary(), cut.expectedGlobals(), result);
            else validateTunnelDoorSecondary(cut, done);
            stopped = false;
        } else {
            if (!primary) throw new IllegalStateException("Actual cleared secondary cannot reach a native stop");
            var prefix = ((CdSolidObject.NativeStopped) call.outcome()).prefix();
            if (prefix.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT)
                throw new IllegalStateException("Tunnel Door stopped at a different native helper entry");
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(cut.expectedPrimary(), cut.expectedGlobals(), prefix);
            stopped = true;
        }
        var images = validateTunnelDoorImages(invocation, cut, object, others, writes);
        var readyPool = CdObjectPool.overlay(cut.callCut(), images); pool.validateRestore(readyPool);
        var readyActor = primary ? new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(),
                readyFrame.finalState(), invocation.actor.display()) : invocation.actor;
        var readyShared = primary ? new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                readyFrame.finalEnvironment(), invocation.shared.artState(), invocation.shared.paused(),
                invocation.shared.vIntClock()) : invocation.shared;
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger);
        var priorValues = invocation.expectedValues;
        var calls = new ArrayList<>(priorValues.calls());
        calls.add(primary ? TUNNEL_DOOR_PRIMARY_SOLID : TUNNEL_DOOR_SECONDARY_SOLID);
        var readyValues = new Values(priorValues.phase(), priorValues.section(), priorValues.cpuPalette(),
                priorValues.fadePalette(), priorValues.displayedPalette(), priorValues.cpuSat(), priorValues.displayedSat(),
                priorValues.displayedHScroll(), priorValues.displayedVScrollLongBits(), priorValues.foregroundPlane(),
                priorValues.ring(), priorValues.level(), priorValues.globals(), priorValues.hudObjects(),
                priorValues.fadeProgress(), List.copyOf(calls), priorValues.loop());
        tunnelDoorPort = true;
        try {
            verifyTunnelDoorInvocation(invocation);
            if (!pool.capture().slots().equals(cut.callCut().slots()))
                throw new IllegalStateException("Tunnel Door pool changed before adoption");
            if (primary) frame.validatePreparedObjectSolidPublication(readyFrame);
            else if (!sameBytes(cut.secondary().orElseThrow(), reservedImage(1)))
                throw new IllegalStateException("Tunnel Door secondary changed before adoption");
            pool.restore(readyPool);
            if (primary) frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds; values = readyValues;
            invocation.actor = readyActor; invocation.shared = readyShared;
            invocation.expectedSounds = readySounds; invocation.expectedValues = readyValues;
            invocation.currentPool = readyPool; invocation.expectedSource = object;
            invocation.stopped = stopped; invocation.adoptedOutcome = call.outcome(); invocation.pendingCut = null;
            if (primary) invocation.primaryCommitted = true;
            else invocation.secondaryCommitted = true;
            verifyTunnelDoorInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots())
                    || !primary && !sameBytes(cut.secondary().orElseThrow(), reservedImage(1)))
                throw new IllegalStateException("Tunnel Door full publication/readback differs from ready values");
            return new CdTunnelDoor.LiveSolid(springPlayer(), cut.expectedGates(), springGlobals());
        } catch (RuntimeException | Error failure) {
            verifyTunnelDoorFailure(failure); throw failure;
        } finally { tunnelDoorPort = false; }
    }

    /** Verify reached native own stores and dirty first-free creation; never replay a body or helper. */
    private void validateTunnelDoorPrefix(TunnelDoorInvocation invocation, CdObjectPool.Snapshot cut, CdObjectState source) {
        invocation.pool.validateRestore(cut);
        if (!invocation.pool.capture().slots().equals(cut.slots())
                || !source.equals(cut.slots().get(invocation.whole - 32)))
            throw new IllegalStateException("Tunnel Door source is not the actual current full96 cut");
        var expected = invocation.expectedSource;
        var images = new ArrayList<CdObjectState>();
        if (!invocation.prefixVerified) {
            int routine = invocation.entry.routine();
            expected = invocation.entry;
            if (routine == 0) {
                expected = expected.addByte(CdObjectState.ByteField.ROUTINE, 2)
                        .moveLong(CdObjectState.LongField.MAPPINGS,
                                assets.view(CdR11bAssets.Asset.MAP_SPR_TUNNEL_DOOR).range().nativeAddress())
                        .moveByte(CdObjectState.ByteField.PRIORITY, 1)
                        .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, expected.spriteFlags() | 4)
                        .moveByte(CdObjectState.ByteField.WIDTH, expected.subtype() == 2 ? 0x18 : 0x2C)
                        .moveByte(CdObjectState.ByteField.Y_RADIUS, 8);
                // The real Init publishes this prefix before reached subtype2/directory failure.
                if (invocation.checkingFailure && source.equals(expected)) {
                    images.add(expected);
                    var partial = CdObjectPool.overlay(invocation.currentPool, images);
                    if (!partial.slots().equals(cut.slots()))
                        throw new IllegalStateException("Tunnel Door failed Init changed another dynamic image");
                    return;
                }
                if (expected.subtype2() != 0)
                    throw new IllegalStateException("Tunnel Door advanced beyond its unsupported Init subtype2");
                var tiles = assets.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes();
                int target = (short) tiles.unsignedWord(CdTunnelDoor.TILE_SELECTOR * 2);
                expected = expected.moveWord(CdObjectState.WordField.TILE, tiles.unsignedWord(target));
            }
            if (routine == 0 || routine == 2) {
                var motion = invocation.actor.state().motion();
                int dx = ((motion.centreX16_16() >>> 16) - expected.centreX()
                        + expected.unsignedByte(CdObjectState.ByteField.WIDTH)) & 0xFFFF;
                int dy = ((motion.centreY16_16() >>> 16) - expected.centreY()
                        + expected.unsignedByte(CdObjectState.ByteField.Y_RADIUS)) & 0xFFFF;
                invocation.primaryOpened = motion.yVelocity() < 0 && (dx & 0x8000) == 0
                        && dx < 2 * expected.unsignedByte(CdObjectState.ByteField.WIDTH)
                        && (dy & 0x8000) == 0 && dy < 2 * expected.unsignedByte(CdObjectState.ByteField.Y_RADIUS);
                if (invocation.primaryOpened) {
                    expected = expected.moveByte(CdObjectState.ByteField.ROUTINE, 4);
                    if (expected.subtype() == 0) {
                        var free = invocation.currentPool.slots().stream().filter(image -> image.id() == 0).findFirst();
                        if (free.isPresent()) {
                            var child = free.orElseThrow().moveByte(CdObjectState.ByteField.ID, CdTunnelDoor.SPLASH_ID)
                                    .moveWord(CdObjectState.WordField.X, expected.centreX())
                                    .moveWord(CdObjectState.WordField.Y, (expected.centreY() - 4) & 0xFFFF);
                            invocation.createdChild = child; images.add(child);
                        }
                    }
                }
            } else if (routine == 6) {
                expected = expected.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, 1)
                        .moveByte(CdObjectState.ByteField.MAP_FRAME, 0).addByte(CdObjectState.ByteField.ROUTINE, -4);
            } else if (routine == 4) {
                // The unchanged shared animator is the producer. Only its selected Door script's fields may change.
                byte[] before = expected.copyBytes(), after = source.copyBytes();
                for (int at = 0; at < CdObjectState.BYTE_SIZE; at++) {
                    int mask = at == 1 ? 3 : at == 0x1A || at == 0x1B || at == 0x1D || at == 0x1E || at == 0x24 ? 0xFF : 0;
                    if (((before[at] ^ after[at]) & ~mask & 0xFF) != 0)
                        throw new IllegalStateException("Tunnel Door animator changed an unowned native byte " + at);
                }
                if (source.routine() != 4 && source.routine() != 6)
                    throw new IllegalStateException("Tunnel Door animator escaped its actual FC transition");
                expected = source;
            } else if (!invocation.checkingFailure || !source.equals(expected)) {
                throw new IllegalStateException("Tunnel Door unsupported routine advanced native work");
            }
            images.add(expected);
            var ready = CdObjectPool.overlay(invocation.currentPool, images);
            if (!ready.slots().equals(cut.slots()))
                throw new IllegalStateException("Tunnel Door native prefix changed unrelated/dirty image bytes");
            invocation.currentPool = ready; invocation.expectedSource = expected; invocation.prefixVerified = true;
            return;
        }
        if (invocation.tailArmed && Arrays.equals(source.copyBytes(), new byte[CdObjectState.BYTE_SIZE])) {
            var gates = invocation.shared.environment().movement().gates();
            int difference = ((expected.centreX() & 0xFF80)
                    - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF;
            if ((gates.timeZone() & 0x80) == 0
                    && ((expected.spriteFlags() & 0x80) != 0 || difference <= 0x280))
                throw new IllegalStateException("Tunnel Door zero image lacks its reached native despawn branch");
            images.add(source);
        } else images.add(expected);
        if (!CdObjectPool.overlay(invocation.currentPool, images).slots().equals(cut.slots()))
            throw new IllegalStateException("Tunnel Door changed a source/full96 value outside its reached native stores");
    }

    private List<CdObjectState> validateTunnelDoorImages(TunnelDoorInvocation invocation, CdTunnelDoor.PrefixSolidCut cut,
            CdObjectState object, List<CdObjectState> others, Set<CdObjectState.ByteField> writes) {
        var source = cut.callCut().slots().get(invocation.whole - 32);
        if (!object.identity().equals(source.identity())
                || !writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                || writes.stream().anyMatch(field -> field != CdObjectState.ByteField.SOLID_TYPE && field != CdObjectState.ByteField.FLAGS)
                || object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) > 1
                || ((source.flags() ^ object.flags()) & ~8) != 0)
            throw new IllegalStateException("Tunnel Door Top changed canonical write fields or flag masks");
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int at = 0; at < CdObjectState.BYTE_SIZE; at++) {
            if (at == 0x25 || at == 0x22 && writes.contains(CdObjectState.ByteField.FLAGS)) continue;
            if (before[at] != after[at])
                throw new IllegalStateException("Tunnel Door Top changed an undeclared own byte " + at);
        }
        if (others.size() > 1) throw new IllegalStateException("Tunnel Door Top returned multiple dynamic releases");
        var images = new ArrayList<CdObjectState>(); images.add(object);
        var seen = new HashSet<Integer>(); seen.add(invocation.whole);
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || !seen.add(whole) || whole != cut.expectedPrimary().standObject())
                throw new IllegalStateException("Tunnel Door release lacks its unique actual dynamic stand link");
            var original = cut.callCut().slots().get(whole - 32);
            if (!original.moveByte(CdObjectState.ByteField.FLAGS, original.flags() & ~8).equals(other))
                throw new IllegalStateException("Tunnel Door dynamic release changed bytes beyond native BCLR3");
            images.add(other);
        }
        return List.copyOf(images);
    }

    /** Independently check every raw player field; a secondary result never enters primary Frame preparation. */
    private void validateTunnelDoorSecondary(CdTunnelDoor.PrefixSolidCut cut, CdSolidObject.Completed done) {
        var raw = cut.secondary().orElseThrow();
        if (raw.length() != CdObjectState.BYTE_SIZE || !sameBytes(raw, reservedImage(1)))
            throw new IllegalStateException("Tunnel Door secondary lost its actual raw64 extent/content");
        for (int at = 0; at < raw.length(); at++) if (raw.unsignedByte(at) != 0)
            throw new IllegalStateException("Tunnel Door secondary carries a dirty actual native byte");
        var motion = new CdPlayerMotionState((int) raw.unsignedLong(8), (int) raw.unsignedLong(12),
                (short) raw.unsignedWord(16), (short) raw.unsignedWord(18), (short) raw.unsignedWord(20),
                raw.unsignedByte(0x24), raw.unsignedByte(0x26), raw.unsignedByte(0x22), raw.unsignedByte(1),
                raw.unsignedByte(0x17), raw.unsignedByte(0x16), raw.unsignedByte(0x1C), raw.unsignedByte(0x1D),
                raw.unsignedByte(0x3C), raw.unsignedByte(0x2A), (short) raw.unsignedWord(0x3E),
                raw.unsignedByte(0x38), raw.unsignedByte(0x2C), raw.unsignedByte(0x36), raw.unsignedByte(0x37));
        var projected = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(motion, raw.unsignedWord(2),
                raw.unsignedByte(0x18), raw.unsignedWord(0x30), raw.unsignedByte(0x39)),
                raw.unsignedByte(0), raw.unsignedByte(0x20), raw.unsignedByte(0x3D));
        var result = done.completedResult();
        var source = cut.callCut().slots().get(cut.objectIdentity().dynamicIndex());
        var expected = source.moveByte(CdObjectState.ByteField.SOLID_TYPE, 0);
        if (!result.player().equals(projected) || !result.globals().equals(cut.expectedGlobals())
                || !result.playerWrites().isEmpty() || !result.globalWrites().isEmpty()
                || !result.otherSlots().isEmpty() || !result.fmRequests().isEmpty()
                || !result.objectWrites().equals(Set.of(CdObjectState.ByteField.SOLID_TYPE))
                || !result.object().equals(expected) || result.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT
                || result.path() != CdSolidObject.Path.GET_OFF
                || result.getOffReason().orElse(null) != CdSolidObject.GetOffReason.PLAYER_ABSENT
                || result.d0().orElse(-1) != 0
                || !result.calls().equals(List.of(new CdSolidObject.RoutineCall(CdSolidObject.Routine.STOP_OBJ_PUSH),
                        new CdSolidObject.RoutineCall(CdSolidObject.Routine.GET_OFF_OBJECT))))
            throw new IllegalStateException("Tunnel Door secondary is not its real entry1/GetOff/final0 no-write return");
    }

    /** A zero ID has authority only after this invocation's actual Draw and the reached native despawn branch. */
    private void validateTunnelDoorSavedFlags(TunnelDoorInvocation invocation, CdObjectState source) {
        var actual = placement.capture().savedFlags();
        if (sameBytes(actual, invocation.savedFlags)) return;
        var expectedSource = invocation.expectedSource;
        if (!invocation.tailArmed || !invocation.drawn || expectedSource.savedFlagsId() == 0
                || !Arrays.equals(source.copyBytes(), new byte[CdObjectState.BYTE_SIZE]))
            throw new IllegalStateException("Tunnel Door changed saved data before its genuine native deletion tail");
        var gates = invocation.shared.environment().movement().gates();
        int difference = ((expectedSource.centreX() & 0xFF80)
                - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF;
        boolean warp = (gates.timeZone() & 0x80) != 0;
        if (!warp && ((expectedSource.spriteFlags() & 0x80) != 0 || difference <= 0x280))
            throw new IllegalStateException("Tunnel Door saved clear lacks an actual native despawn condition");
        int period = gates.timeZone() & 0x7F;
        if (warp) {
            int raw = (period - (byte) gates.timeWarpDirection()) & 0xFFFF;
            period = (short) raw < 0 ? 0 : raw >= 3 ? 2 : raw;
        }
        if (period > 2) throw new IllegalStateException("Tunnel Door retained the shared raw-period saved-bank boundary");
        int index = 2 + 3 * expectedSource.savedFlagsId() + period;
        byte[] expected = invocation.savedFlags.copy();
        if (index < 2 || index >= expected.length)
            throw new IllegalStateException("Tunnel Door saved clear did not address its actual finite bank");
        expected[index] &= 0x7F;
        if (!Arrays.equals(expected, actual.copy()))
            throw new IllegalStateException("Tunnel Door changed saved data beyond its one genuine native BCLR7");
    }

    private void verifyTunnelDoorCallback(TunnelDoorInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                          CdObjectState cleared) {
        verifyTunnelDoorInvocation(invocation);
        var now = placement.capture(); var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(expected)
                || !sameBytes(now.savedFlags(), before.savedFlags())
                || !sameBytes(values.hudObjects(), invocation.entryReserved))
            throw new IllegalStateException("Tunnel Door callback changed full96/saved/reserved outside its actual bit7 clear");
    }

    private void verifyTunnelDoorCallbackFailure(TunnelDoorInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                                 CdObjectState cleared, Throwable original) {
        try { verifyTunnelDoorCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private void playTunnelDoorFm(CdTunnelDoor.FmSound sound) {
        var invocation = requireTunnelDoorInvocation(placement.pool(), activeTunnelDoor == null ? -1 : activeTunnelDoor.whole);
        if (tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped
                || invocation.primaryPreflight || invocation.secondaryRead || invocation.sounded
                || sound != CdTunnelDoor.FmSound.FM_A4)
            throw new IllegalStateException("Tunnel Door A4 lacks its genuine primary dirty-child prefix");
        var before = placement.capture();
        tunnelDoorPort = true;
        try {
            validateTunnelDoorPrefix(invocation, before.pool(), invocation.pool.state(invocation.whole));
            if (!invocation.primaryOpened || invocation.createdChild == null
                    || invocation.expectedSource.routine() != 4 || invocation.expectedSource.subtype() != 0)
                throw new IllegalStateException("Tunnel Door A4 does not follow actual successful subtype0 creation");
            var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastTunnelDoorFm(sound));
            var readySounds = List.copyOf(ledger);
            verifyTunnelDoorCallback(invocation, before, null);
            sounds = readySounds; invocation.expectedSounds = readySounds; invocation.sounded = true;
            verifyTunnelDoorCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyTunnelDoorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { tunnelDoorPort = false; }
    }

    private void drawTunnelDoor(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireTunnelDoorInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped
                || invocation.pendingCut != null)
            throw new IllegalStateException("Tunnel Door DrawObject is not its once-only actual normal return");
        var before = placement.capture(); var current = pool.state(invocation.whole);
        validateTunnelDoorPrefix(invocation, before.pool(), current);
        if ((invocation.entry.routine() == 0 || invocation.entry.routine() == 2) && !invocation.secondaryCommitted
                || (invocation.entry.routine() == 4 || invocation.entry.routine() == 6)
                && (invocation.primaryPreflight || invocation.secondaryRead))
            throw new IllegalStateException("Tunnel Door Draw precedes its actual native routine completion");
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true; drawingTunnelDoor = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(),
                    current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Tunnel Door DrawObject did not clear bit7");
            verifyTunnelDoorCallback(invocation, before, cleared);
            invocation.tailArmed = true;
        } catch (RuntimeException | Error failure) {
            verifyTunnelDoorCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure); throw failure;
        } finally { drawingTunnelDoor = false; }
    }

    private CdObjectDrawQueue.LiveDrawSlot readTunnelDoorDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireTunnelDoorInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingTunnelDoor || tunnelDoorPort || queuePort)
            throw new IllegalStateException("No actual Tunnel Door queue read");
        var before = placement.capture(); queuePort = true;
        try {
            validateTunnelDoorPrefix(invocation, before.pool(), invocation.pool.state(invocation.whole));
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyTunnelDoorCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) {
            verifyTunnelDoorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }

    private void writeTunnelDoorOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireTunnelDoorInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingTunnelDoor || tunnelDoorPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual Tunnel Door DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        validateTunnelDoorPrefix(invocation, before.pool(), current);
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        var readyPool = CdObjectPool.overlay(before.pool(), List.of(next));
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.screenCleared = true;
            invocation.currentPool = readyPool; invocation.expectedSource = next;
            verifyTunnelDoorCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            verifyTunnelDoorCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure); throw failure;
        } finally { queuePort = false; }
    }


    /** One actual cached ID19 dispatch; public component values grant no invocation authority. */
    private enum MonitorSite { ENTRY, SOLID, FLOOR_ANGLE, MONITOR_FM, DIRECTION, SUB_CPU, DRAW, RETURN, FAILURE }
    private final class MonitorInvocation {
        private final CdMonitor monitorBody = monitor;
        private final CdTimePost timePostBody = timePost;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final boolean post;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final CdAssetBytes reserved = values.hudObjects();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private Values expectedValues = values;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdAssetBytes expectedSaved = placement.capture().savedFlags();
        private CdObjectState expectedSource;
        private CdMonitor.PrefixSolidCut pendingCut;
        private CdSolidObject.Outcome adoptedOutcome;
        private boolean initVerified, mainVerified, bodyVerified, solidPreflight, solidCommitted;
        private boolean floorStarted, landingVerified, sounded, voiced, drawn, screenCleared, tailArmed, stopped;
        private boolean noDraw, directBreak, checkingFailure;
        private int floorWrites, directionStores;
        private int activationAddress = -1;
        private MonitorInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole); expectedSource = entry; post = entry.subtype() >= 8;
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runMonitor(int whole) {
        module.requirePastLoadingVisit(this);
        if (!runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdMonitor.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Past Monitor/Post invocation entry");
        var invocation = new MonitorInvocation(whole); activeMonitor = invocation;
        try {
            // Fresh CURRENT raw subtype is the native unsigned branch, never a placement-row classification.
            if (invocation.post) {
                var result = timePost.runPast(whole, timePostGlobals());
                verifyMonitorInvocation(invocation); validateMonitorPrefix(invocation, MonitorSite.RETURN);
                if (!result.state().equals(invocation.pool.state(whole)) || result.deleted() != (result.state().id() == 0))
                    throw new IllegalStateException("Time Post return differs from its actual complete image");
            } else {
                var result = monitor.runPast(whole, monitorGlobals(), new CdMonitor.SolidInputs(springPlayer(),
                        new CdSolidObject.Gates(frame.environment().globals().debugMode(),
                                frame.environment().movement().gates().miniSonic()), springGlobals()));
                verifyMonitorInvocation(invocation); validateMonitorPrefix(invocation, MonitorSite.RETURN);
                if (!result.state().equals(invocation.pool.state(whole)) || !result.finalPlayer().equals(springPlayer())
                        || !result.finalGlobals().equals(springGlobals()) || result.deleted() != (result.state().id() == 0))
                    throw new IllegalStateException("Monitor return differs from its actual pool/Frame pair");
            }
            if (invocation.stopped || invocation.pendingCut != null || !invocation.noDraw && !invocation.drawn)
                throw new IllegalStateException("ID19 returned before its actual private operation grammar completed");
        } catch (RuntimeException | Error failure) {
            if (invocation.adoptedOutcome instanceof CdSolidObject.NativeStopped nativeStop) {
                var original = nativeStop.stop().originalException();
                if (failure != original) original.addSuppressed(failure);
                verifyMonitorFailure(original); throw original;
            }
            verifyMonitorFailure(failure); throw failure;
        } finally { activeMonitor = null; }
    }

    private MonitorInvocation requireMonitorInvocation(CdObjectPool pool, int whole) {
        var invocation = activeMonitor;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Monitor/Post callback at this physical pointer");
        verifyMonitorInvocation(invocation); return invocation;
    }
    private MonitorInvocation currentMonitorInvocation() {
        return requireMonitorInvocation(placement.pool(), activeMonitor == null ? -1 : activeMonitor.whole);
    }
    private void verifyMonitorInvocation(MonitorInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeMonitor != invocation || activeFloat != null || activePata != null || activeTamabboh != null || drawingPata || tamabbohPort || drawingTamabboh || floatPort || drawingFloat || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor
                || invocation.monitorBody != monitor || invocation.timePostBody != timePost
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || invocation.pool != placement.pool() || invocation.queue != drawQueue
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status) || !presentation.samePastScroll(invocation.scroll)
                || sounds != invocation.expectedSounds || values != invocation.expectedValues
                || !sameBytes(values.hudObjects(), invocation.reserved) || values.phase() != BEGIN_LEVEL)
            throw new IllegalStateException("Monitor/Post changed a source/World/Frame owner or retained a stale full cut");
    }
    private void verifyMonitorFailure(Throwable original) {
        try {
            if (activeMonitor != null) {
                activeMonitor.checkingFailure = true;
                verifyMonitorInvocation(activeMonitor); validateMonitorPrefix(activeMonitor, MonitorSite.FAILURE);
            }
        } catch (RuntimeException | Error verification) {
            if (verification != original) original.addSuppressed(verification);
        }
    }

    private CdMonitor.PastGlobals monitorGlobals() {
        return new CdMonitor.PastGlobals() {
            @Override public int rawTimeZone() { currentMonitorInvocation(); return frame.environment().movement().gates().timeZone(); }
            @Override public int rawTimeWarpDirection() { currentMonitorInvocation(); return frame.environment().movement().gates().timeWarpDirection(); }
            @Override public int cameraXWord() { return currentMonitorInvocation().camera.xWord(); }
            @Override public int timeAttackMode() { currentMonitorInvocation(); return frame.environment().globals().timeAttackMode(); }
            @Override public int zone() { currentMonitorInvocation(); return frame.environment().movement().gates().zoneAct() >>> 8; }
            @Override public int layer() { return monitorLayer(); }
            @Override public int timeStopTimer() { currentMonitorInvocation(); return values.ring().timeStopTimer(); }
        };
    }
    private CdTimePost.PastGlobals timePostGlobals() {
        return new CdTimePost.PastGlobals() {
            @Override public int rawTimeZone() { currentMonitorInvocation(); return frame.environment().movement().gates().timeZone(); }
            @Override public int rawTimeWarpDirection() { currentMonitorInvocation(); return frame.environment().movement().gates().timeWarpDirection(); }
            @Override public int cameraXWord() { return currentMonitorInvocation().camera.xWord(); }
            @Override public int timeAttackMode() { currentMonitorInvocation(); return frame.environment().globals().timeAttackMode(); }
            @Override public int zone() { currentMonitorInvocation(); return frame.environment().movement().gates().zoneAct() >>> 8; }
            @Override public OptionalInt layer() { currentMonitorInvocation(); return OptionalInt.empty(); }
        };
    }
    private int monitorLayer() {
        var invocation = currentMonitorInvocation();
        if (invocation.post || (frame.environment().movement().gates().zoneAct() >>> 8) != 6)
            throw new IllegalStateException("Monitor global layer is not at its reached MMZ read");
        validateMonitorPrefix(invocation, MonitorSite.SOLID);
        throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.MONITOR_LAYER_READ);
    }

    /** Finite raw linear address, including savedID0; no second saved bank or semantic period folding. */
    private OptionalInt monitorSavedAddress(CdObjectState source) {
        var gates = frame.environment().movement().gates(); int period = gates.timeZone() & 0x7F;
        if ((gates.timeZone() & 0x80) != 0) {
            int raw = (period - (byte) gates.timeWarpDirection()) & 0xFFFF;
            period = (short) raw < 0 ? 0 : raw >= 3 ? 2 : raw;
        }
        int at = 2 + (short) ((3 * source.savedFlagsId() + period) & 0xFFFF);
        return at < 2 || at >= CdObjectPlacementRuntime.SAVED_BANK_BYTES ? OptionalInt.empty() : OptionalInt.of(at);
    }
    private boolean monitorSavedChange(CdObjectState source, byte[] bank, int clear, int set) {
        var at = monitorSavedAddress(source);
        if (at.isEmpty()) return false;
        bank[at.getAsInt()] = (byte) ((Byte.toUnsignedInt(bank[at.getAsInt()]) & ~clear) | set); return true;
    }
    private boolean monitorDespawn(MonitorInvocation invocation, CdObjectState source) {
        boolean outside = false;
        if ((source.spriteFlags() & 0x80) == 0) {
            int difference = ((source.centreX() & 0xFF80) - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF;
            outside = difference > 0x280;
        }
        return outside || (frame.environment().movement().gates().timeZone() & 0x80) != 0;
    }
    private CdObjectState monitorDeletedImage(MonitorInvocation invocation, CdObjectState source) {
        var actual = invocation.pool.state(invocation.whole);
        if (!actual.identity().equals(source.identity()) || !Arrays.equals(actual.copyBytes(), new byte[CdObjectState.BYTE_SIZE]))
            throw new IllegalStateException("Monitor/Post deletion did not clear its entire actual64 image");
        return actual;
    }
    private boolean matchesMonitorPool(MonitorInvocation invocation, CdObjectState source,
                                        List<CdObjectState> extra, byte[] bank) {
        var images = new ArrayList<CdObjectState>(extra); images.add(source);
        return CdObjectPool.overlay(invocation.currentPool, images).slots().equals(invocation.pool.capture().slots())
                && Arrays.equals(bank, placement.capture().savedFlags().copy());
    }
    private void acceptMonitorPool(MonitorInvocation invocation, CdObjectState source,
                                   List<CdObjectState> extra, byte[] bank) {
        if (!matchesMonitorPool(invocation, source, extra, bank))
            throw new IllegalStateException("Monitor/Post source prefix changed full96/saved766 beyond native stores");
        var images = new ArrayList<CdObjectState>(extra); images.add(source);
        invocation.currentPool = CdObjectPool.overlay(invocation.currentPool, images);
        invocation.expectedSource = source; invocation.expectedSaved = CdAssetBytes.copyOf(bank);
    }
    private CdObjectState monitorInitPrefix(MonitorInvocation invocation) {
        var source = invocation.entry.addByte(CdObjectState.ByteField.ROUTINE, 2)
                .moveByte(CdObjectState.ByteField.Y_RADIUS, invocation.post ? 0x20 : 0x0E)
                .moveByte(CdObjectState.ByteField.X_RADIUS, 0x0E)
                .moveLong(CdObjectState.LongField.MAPPINGS, assets.view(CdR11bAssets.Asset.MAP_SPR_MONITOR_TIME).range().nativeAddress())
                .moveWord(CdObjectState.WordField.TILE, 0x05A8).moveByte(CdObjectState.ByteField.PRIORITY, 3);
        if ((frame.environment().movement().gates().zoneAct() >>> 8) == 6 && source.unsignedByte(CdObjectState.ByteField.LAYER) == 0)
            source = source.moveWord(CdObjectState.WordField.TILE, 0x85A8).moveByte(CdObjectState.ByteField.PRIORITY, 0);
        source = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 4).moveByte(CdObjectState.ByteField.WIDTH, 0x0F);
        return invocation.post ? source.moveByte(CdObjectState.ByteField.ANIMATION, source.subtype()) : source;
    }
    /** ObjMoveGrv:42-82 raw WORD/long arithmetic; this checker never invokes Movement or terrain. */
    private CdObjectState monitorGravityPrefix(CdObjectState source) {
        int xv = source.signedWord(CdObjectState.WordField.X_VELOCITY), yv = source.signedWord(CdObjectState.WordField.Y_VELOCITY);
        int control = source.unsignedByte(CdObjectState.ByteField.RAW_CONTROL_2C), next = yv;
        boolean gravity = (control & 8) == 0 && !(yv < 0 && (control & 2) != 0 && (yv & 0xFFFF) < 0xF800) && (control & 4) == 0;
        if (gravity) next = (short) (next + 0x38);
        if (next >= 0x1000) next = 0x1000;
        return source.moveLong(CdObjectState.LongField.X_POSITION, source.longBits(CdObjectState.LongField.X_POSITION) + (xv << 8))
                .moveLong(CdObjectState.LongField.Y_POSITION, source.longBits(CdObjectState.LongField.Y_POSITION) + (yv << 8))
                .moveWord(CdObjectState.WordField.Y_VELOCITY, next & 0xFFFF);
    }

    /** Validate only already-reached native cuts; never invoke a producer or fetch a future operand. */
    private void validateMonitorPrefix(MonitorInvocation invocation, MonitorSite site) {
        var actual = invocation.pool.state(invocation.whole); byte[] bank = invocation.expectedSaved.copy();
        if (site == MonitorSite.FAILURE && matchesMonitorPool(invocation, invocation.expectedSource, List.of(), bank)) return;
        if (invocation.drawn) {
            var source = invocation.expectedSource;
            if (invocation.tailArmed && monitorDespawn(invocation, source)) {
                if (source.savedFlagsId() != 0 && !monitorSavedChange(source, bank, 0x80, 0)) {
                    if (site == MonitorSite.FAILURE) { acceptMonitorPool(invocation, source, List.of(), bank); return; }
                    throw new IllegalStateException("Monitor/Post returned across an unowned saved byte");
                }
                source = monitorDeletedImage(invocation, source);
            }
            acceptMonitorPool(invocation, source, List.of(), bank); return;
        }
        var source = invocation.expectedSource;
        if (!invocation.initVerified) {
            boolean attack = (invocation.post || source.subtype() == 0)
                    && frame.environment().globals().timeAttackMode() != 0;
            if (invocation.post && attack) {
                invocation.noDraw = true; invocation.bodyVerified = true;
                acceptMonitorPool(invocation, monitorDeletedImage(invocation, source), List.of(), bank); return;
            }
            if (!invocation.post && source.subtype() == 0 && attack) {
                invocation.noDraw = true; invocation.bodyVerified = true; invocation.tailArmed = true;
                if (monitorDespawn(invocation, source)) {
                    if (source.savedFlagsId() != 0 && !monitorSavedChange(source, bank, 0x80, 0))
                        throw new IllegalStateException("Monitor attack tail crossed unowned saved address");
                    source = monitorDeletedImage(invocation, source);
                }
                acceptMonitorPool(invocation, source, List.of(), bank); return;
            }
            if (invocation.post && source.subtype() == 0x0A)
                throw new IllegalStateException("Dynamic Time Icon advanced its source image");
            int routine = invocation.entry.routine();
            if ((routine & 1) != 0 || routine > (invocation.post ? 6 : 8))
                throw new IllegalStateException("Unsupported ID19 routine/icon advanced its source image");
            if (routine == 0) {
                source = monitorInitPrefix(invocation);
                if (site == MonitorSite.FAILURE && matchesMonitorPool(invocation, source, List.of(), bank)) {
                    acceptMonitorPool(invocation, source, List.of(), bank); return;
                }
                if (!monitorSavedChange(source, bank, 0x80, 0))
                    throw new IllegalStateException("ID19 advanced beyond its native finite Init saved address");
                boolean used = (Byte.toUnsignedInt(bank[monitorSavedAddress(source).orElseThrow()]) & 1) != 0;
                if (invocation.post) {
                    source = source.moveByte(CdObjectState.ByteField.MAP_FRAME, source.subtype() == 8 ? 10 : 12);
                    if (used) source = source.addByte(CdObjectState.ByteField.MAP_FRAME, 1).moveByte(CdObjectState.ByteField.ROUTINE, 6);
                    else source = source.moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0xDF);
                } else if (used) {
                    source = source.moveByte(CdObjectState.ByteField.ROUTINE, 8).moveByte(CdObjectState.ByteField.MAP_FRAME, 17);
                    invocation.noDraw = true; invocation.bodyVerified = true;
                } else source = source.moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0x46)
                        .moveByte(CdObjectState.ByteField.ANIMATION, source.subtype());
            }
            invocation.initVerified = true;
        }
        var extra = new ArrayList<CdObjectState>();
        if (invocation.noDraw) { acceptMonitorPool(invocation, source, extra, bank); return; }
        if (!invocation.mainVerified) {
            int routine = source.routine();
            if (!invocation.post && routine == 4) {
                if (site == MonitorSite.MONITOR_FM) { acceptMonitorPool(invocation, source, extra, bank); return; }
                if (!invocation.sounded) throw new IllegalStateException("Monitor Break stores preceded its actual FM96");
                source = source.addByte(CdObjectState.ByteField.ROUTINE, 4).moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0);
                var working = invocation.currentPool;
                for (int id : new int[]{0x1A, 0x18}) {
                    var free = working.slots().stream().filter(image -> image.id() == 0).findFirst();
                    if (free.isPresent()) {
                        var child = free.orElseThrow().moveByte(CdObjectState.ByteField.ID, id)
                                .moveWord(CdObjectState.WordField.X, source.centreX()).moveWord(CdObjectState.WordField.Y, source.centreY())
                                .moveByte(CdObjectState.ByteField.LAYER, source.unsignedByte(CdObjectState.ByteField.LAYER));
                        if (id == 0x1A) child = child.moveByte(CdObjectState.ByteField.ANIMATION, source.unsignedByte(CdObjectState.ByteField.ANIMATION));
                        else child = child.moveByte(CdObjectState.ByteField.ROUTINE_2, 1).moveByte(CdObjectState.ByteField.SUBTYPE, 1);
                        extra.add(child); working = CdObjectPool.overlay(working, List.of(child));
                    }
                }
                if (!monitorSavedChange(source, bank, 0, 1)) {
                    if (site == MonitorSite.FAILURE) { acceptMonitorPool(invocation, source, extra, bank); return; }
                    throw new IllegalStateException("Monitor Break crossed its finite saved address");
                }
                source = source.moveByte(CdObjectState.ByteField.MAP_FRAME, 17); invocation.directBreak = true;
                invocation.mainVerified = true; invocation.bodyVerified = true;
            } else if (invocation.post && routine == 2 && source.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS) != 0) {
                source = source.moveByte(CdObjectState.ByteField.COLLISION_STATUS, 0);
                if ((frame.environment().movement().gates().zoneAct() >>> 8) == 6) {
                    if (site != MonitorSite.FAILURE) throw new IllegalStateException("Post crossed its absent native MMZ layer read");
                    acceptMonitorPool(invocation, source, extra, bank); return;
                }
                source = source.moveByte(CdObjectState.ByteField.TIME_POST_SPIN, 60).addByte(CdObjectState.ByteField.ROUTINE, 2);
                if (!monitorSavedChange(source, bank, 0, 1)) {
                    if (site == MonitorSite.FAILURE) { acceptMonitorPool(invocation, source, extra, bank); return; }
                    throw new IllegalStateException("Post advanced beyond its finite activation saved address");
                }
                invocation.activationAddress = monitorSavedAddress(source).orElseThrow();
                invocation.mainVerified = true;
            } else if (invocation.post && routine == 4) {
                source = source.addByte(CdObjectState.ByteField.TIME_POST_SPIN, -1);
                if (source.unsignedByte(CdObjectState.ByteField.TIME_POST_SPIN) == 0) {
                    source = source.addByte(CdObjectState.ByteField.ROUTINE, 2)
                            .moveByte(CdObjectState.ByteField.MAP_FRAME, source.subtype() == 8 ? 11 : 13);
                    invocation.bodyVerified = true;
                }
                invocation.mainVerified = true;
            } else if (!invocation.post && routine == 2 && (source.spriteFlags() & 0x80) != 0
                    && source.unsignedByte(CdObjectState.ByteField.ROUTINE_2) != 0) {
                source = monitorGravityPrefix(source); invocation.mainVerified = true;
            } else invocation.mainVerified = true;
        }
        if (site == MonitorSite.SOLID || site == MonitorSite.FLOOR_ANGLE || site == MonitorSite.DIRECTION
                || site == MonitorSite.SUB_CPU || site == MonitorSite.MONITOR_FM) {
            acceptMonitorPool(invocation, source, extra, bank); return;
        }
        if (site == MonitorSite.FAILURE && matchesMonitorPool(invocation, source, extra, bank)) {
            acceptMonitorPool(invocation, source, extra, bank); return;
        }
        if (!invocation.bodyVerified && !invocation.post && source.routine() == 2 && (source.spriteFlags() & 0x80) != 0) {
            if (source.unsignedByte(CdObjectState.ByteField.ROUTINE_2) != 0) {
                if (!invocation.floorStarted) throw new IllegalStateException("Monitor skipped its genuine floor pre-zero");
                // Completed terrain distance is owned by the cached floor producer. Only its negative landing writes are admitted.
                if (!invocation.landingVerified) {
                    int difference = (actual.centreY() - source.centreY()) & 0xFFFF;
                    if (actual.unsignedByte(CdObjectState.ByteField.ROUTINE_2) == 0) {
                        if ((short) difference >= 0 || actual.signedWord(CdObjectState.WordField.Y_VELOCITY) != 0)
                            throw new IllegalStateException("Monitor landing is not its negative native distance branch");
                        source = source.moveWord(CdObjectState.WordField.Y, actual.centreY())
                                .moveWord(CdObjectState.WordField.Y_VELOCITY, 0).moveByte(CdObjectState.ByteField.ROUTINE_2, 0);
                    }
                    invocation.landingVerified = true;
                }
            } else if (!invocation.solidCommitted)
                throw new IllegalStateException("Visible Monitor skipped its actual plain Solid call");
        }
        boolean animation = !invocation.bodyVerified && (invocation.post
                ? source.routine() == 4 && invocation.entry.routine() == 4
                : source.routine() == 6 || source.routine() == 2 && (source.spriteFlags() & 0x80) != 0);
        if (animation && (invocation.post || values.ring().timeStopTimer() == 0))
            source = validateMonitorAnimation(invocation, source, actual, site == MonitorSite.FAILURE);
        if (invocation.post && source.routine() == 4 && invocation.entry.routine() != 4) {
            int stores = source.subtype() == 8 ? 1 : 2;
            if (invocation.directionStores != stores || !invocation.voiced)
                throw new IllegalStateException("Post draw precedes native direction stores/voice");
        }
        acceptMonitorPool(invocation, source, extra, bank); invocation.bodyVerified = true;
    }

    /** Object Animate.asm:14-61, finite Ani_Monitor contains only FF commands; no producer is called. */
    private CdObjectState validateMonitorAnimation(MonitorInvocation invocation, CdObjectState source,
                                                   CdObjectState actual, boolean failure) {
        if (failure && !invocation.post && actual.equals(source)) return source; // Existing Monitor animator failure is atomic.
        var expected = source; int animation = source.unsignedByte(CdObjectState.ByteField.ANIMATION);
        if (animation != source.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION))
            expected = expected.moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, animation)
                    .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0).moveByte(CdObjectState.ByteField.ANIMATION_TIME, 0);
        expected = expected.addByte(CdObjectState.ByteField.ANIMATION_TIME, -1);
        if (failure && actual.equals(expected)) return expected; // Stop here: do not prefetch the next native read.
        if (expected.signedByte(CdObjectState.ByteField.ANIMATION_TIME) >= 0) return expected;
        var bytes = assets.view(CdR11bAssets.Asset.ANI_MONITOR).bytes();
        if (animation >= 10) throw new IllegalStateException("ID19 animator advanced past its finite directory");
        int script = (short) bytes.unsignedWord(animation * 2);
        if (script < 0 || script >= bytes.length()) throw new IllegalStateException("ID19 animator advanced past its speed");
        expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_TIME, bytes.unsignedByte(script));
        if (failure && actual.equals(expected)) return expected;
        int index = expected.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME), at = script + 1 + index;
        if (at < 0 || at >= bytes.length()) throw new IllegalStateException("ID19 animator advanced past its frame");
        int raw = bytes.unsignedByte(at);
        if (raw == 0xFF) {
            expected = expected.moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0); index = 0;
            if (failure && actual.equals(expected)) return expected;
            raw = bytes.unsignedByte(script + 1);
        } else if (raw >= 0x80) throw new IllegalStateException("Ani_Monitor acquired an undeclared negative command");
        int rotated = ((raw << 3) | (raw >>> 5)) & 0xFF;
        return expected.moveByte(CdObjectState.ByteField.MAP_FRAME, raw & 0x1F)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, (source.spriteFlags() & 0xFC) | ((rotated ^ source.flags()) & 3))
                .moveByte(CdObjectState.ByteField.ANIMATION_FRAME, (index + 1) & 0xFF);
    }

    private CdMonitor.LiveSolid preflightMonitorSolid(CdMonitor.PrefixSolidCut cut) {
        Objects.requireNonNull(cut);
        var invocation = requireMonitorInvocation(placement.pool(), cut.objectIdentity().wholeIndex());
        if (monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.stopped
                || invocation.solidPreflight || invocation.pendingCut != null || !cut.objectIdentity().equals(invocation.entry.identity()))
            throw new IllegalStateException("Monitor Solid lacks its once-only actual preflight");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.SOLID);
            var source = invocation.expectedSource;
            invocation.pool.validateRestore(cut.callCut());
            if (source.routine() != 2 || (source.spriteFlags() & 0x80) == 0
                    || source.unsignedByte(CdObjectState.ByteField.ROUTINE_2) != 0
                    || (frame.environment().movement().gates().zoneAct() >>> 8) == 6
                    || !before.pool().slots().equals(cut.callCut().slots())
                    || !cut.expectedPlayer().equals(springPlayer()) || !cut.expectedGlobals().equals(springGlobals())
                    || !cut.expectedGates().equals(new CdSolidObject.Gates(frame.environment().globals().debugMode(),
                            frame.environment().movement().gates().miniSonic())))
                throw new IllegalStateException("Monitor plain Solid is not its actual visible/nonfalling full96/Frame cut");
            var live = new CdMonitor.LiveSolid(springPlayer(), cut.expectedGates(), springGlobals());
            verifyMonitorCallback(invocation, before, null);
            invocation.pendingCut = cut; invocation.solidPreflight = true; return live;
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }
    private void validateMonitorCompleted(CdSolidObject.Result result) {
        if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT || result.path() == CdSolidObject.Path.ROUTINE_RETURN
                || result.d0().isEmpty() || result.d0().getAsInt() != (result.path() == CdSolidObject.Path.STAND_ON
                        || result.path() == CdSolidObject.Path.HIT_BOTTOM ? 1 : 0))
            throw new IllegalStateException("Monitor completed a different helper or fabricated native return");
    }
    private void validateMonitorStopped(CdSolidObject.NativeStopped stopped) {
        if (stopped.prefix().entry() != CdSolidObject.Entry.SOLID_OBJECT || stopped.stop().originalException() == null)
            throw new IllegalStateException("Monitor stopped outcome is not the genuine plain Solid prefix");
    }
    private List<CdObjectState> validateMonitorImages(MonitorInvocation invocation, CdMonitor.PrefixSolidCut cut,
            CdObjectState object, List<CdObjectState> others, Set<CdObjectState.ByteField> writes) {
        var source = cut.callCut().slots().get(invocation.whole - 32);
        if (!source.identity().equals(object.identity())
                || writes.stream().anyMatch(field -> field != CdObjectState.ByteField.FLAGS && field != CdObjectState.ByteField.SOLID_TYPE)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0
                || ((source.flags() ^ object.flags()) & ~0x28) != 0)
            throw new IllegalStateException("Monitor Solid changed canonical fields/masks; ROUTINE_2 is not write authority");
        byte[] old = source.copyBytes(), next = object.copyBytes();
        for (int at = 0; at < CdObjectState.BYTE_SIZE; at++) {
            int mask = at == 0x22 && writes.contains(CdObjectState.ByteField.FLAGS) ? 0x28
                    : at == 0x25 && writes.contains(CdObjectState.ByteField.SOLID_TYPE) ? 0xFF : 0;
            if (((old[at] ^ next[at]) & ~mask & 0xFF) != 0)
                throw new IllegalStateException("Monitor plain Solid changed undeclared full-image byte " + at);
        }
        if (others.size() > 1) throw new IllegalStateException("Monitor Solid returned more than one native stand release");
        var images = new ArrayList<CdObjectState>(); images.add(object);
        var seen = new HashSet<Integer>(); seen.add(invocation.whole);
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || !seen.add(whole) || whole != cut.expectedPlayer().standObject())
                throw new IllegalStateException("Monitor release lacks its unique actual dynamic stand link");
            var before = cut.callCut().slots().get(whole - 32);
            if (!before.moveByte(CdObjectState.ByteField.FLAGS, before.flags() & ~8).equals(other))
                throw new IllegalStateException("Monitor stand release changed bytes beyond the native BCLR3");
            images.add(other);
        }
        return List.copyOf(images);
    }

    /** All full-pool/Frame/ledger/expected values precede the first owner assignment. */
    private CdMonitor.LiveSolid commitMonitorSolid(CdMonitor.PrefixSolidCall call) {
        Objects.requireNonNull(call); var cut = call.cut();
        var invocation = requireMonitorInvocation(placement.pool(), cut.objectIdentity().wholeIndex());
        if (monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.stopped
                || invocation.pendingCut != cut || invocation.adoptedOutcome == call.outcome())
            throw new IllegalStateException("Monitor commit lacks the actual pending preflight identity");
        var pool = invocation.pool; pool.validateRestore(cut.callCut());
        if (!pool.capture().slots().equals(cut.callCut().slots()) || !cut.callCut().slots().equals(invocation.currentPool.slots())
                || !cut.expectedPlayer().equals(springPlayer()) || !cut.expectedGlobals().equals(springGlobals())
                || !cut.expectedGates().equals(new CdSolidObject.Gates(frame.environment().globals().debugMode(),
                        frame.environment().movement().gates().miniSonic())))
            throw new IllegalStateException("Stale Monitor full96/actual complete Frame publication");
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame; boolean stopped;
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var result = done.completedResult(); validateMonitorCompleted(result);
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(cut.expectedPlayer(), cut.expectedGlobals(), result); stopped = false;
        } else {
            var nativeStop = (CdSolidObject.NativeStopped) call.outcome(); validateMonitorStopped(nativeStop);
            var prefix = nativeStop.prefix();
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(cut.expectedPlayer(), cut.expectedGlobals(), prefix); stopped = true;
        }
        var images = validateMonitorImages(invocation, cut, object, others, writes);
        var readyPool = CdObjectPool.overlay(cut.callCut(), images); pool.validateRestore(readyPool);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger); var readyValues = preparedMonitorValues(MONITOR_SOLID);
        var readyLive = new CdMonitor.LiveSolid(
                call.outcome() instanceof CdSolidObject.Completed done ? done.completedResult().player() : ((CdSolidObject.NativeStopped) call.outcome()).prefix().player(),
                cut.expectedGates(),
                call.outcome() instanceof CdSolidObject.Completed done ? done.completedResult().globals() : ((CdSolidObject.NativeStopped) call.outcome()).prefix().globals());
        monitorPort = true;
        try {
            verifyMonitorInvocation(invocation);
            if (!pool.capture().slots().equals(cut.callCut().slots()) || !sameBytes(placement.capture().savedFlags(), invocation.expectedSaved))
                throw new IllegalStateException("Monitor source/bank changed before adoption");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds; values = readyValues;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.expectedValues = readyValues; invocation.currentPool = readyPool; invocation.expectedSource = object;
            invocation.stopped = stopped; invocation.adoptedOutcome = call.outcome(); invocation.pendingCut = null; invocation.solidCommitted = true;
            verifyMonitorInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()) || !sameBytes(placement.capture().savedFlags(), invocation.expectedSaved))
                throw new IllegalStateException("Monitor output full96/saved readback differs from prepared values");
            return readyLive;
        } catch (RuntimeException | Error failure) {
            verifyMonitorFailure(failure); throw failure;
        } finally { monitorPort = false; }
    }
    private Values preparedMonitorValues(SonicCdGameModule.PastLoadCall call) {
        var old = values; var calls = new ArrayList<>(old.calls()); calls.add(call);
        return new Values(old.phase(), old.section(), old.cpuPalette(), old.fadePalette(), old.displayedPalette(), old.cpuSat(),
                old.displayedSat(), old.displayedHScroll(), old.displayedVScrollLongBits(), old.foregroundPlane(), old.ring(),
                old.level(), old.globals(), old.hudObjects(), old.fadeProgress(), List.copyOf(calls), old.loop());
    }
    private CdPlayerBody.Environment monitorAngleEnvironment(CdPlayerBody.Environment environment, int angle) {
        var movement = environment.movement(); var globals = movement.globals();
        var next = new CdPlayerMovement.Globals(globals.lookMode(), globals.cameraYCentre(), angle, globals.secondaryAngle(),
                globals.boredTimer(), globals.scoreChain(), globals.debugAngle(), globals.debugAngleShift(),
                globals.debugQuadrant(), globals.debugFloorDistance());
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(), movement.gates(), next),
                environment.controls(), environment.globals(), environment.dynamicSlots());
    }
    /** Every genuine ObjGetFloorDist store: pre-zero first, then all recursive raw writes with no count cap. */
    private void commitMonitorPrimaryAngle(int expected, int angle) {
        var invocation = currentMonitorInvocation();
        if (monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.stopped
                || invocation.solidPreflight || (angle & ~0xFF) != 0
                || frame.environment().movement().globals().primaryAngle() != expected
                || invocation.floorWrites == 0 && angle != 0)
            throw new IllegalStateException("Monitor angle lacks its actual fall/native pre-zero/full Frame CAS");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.FLOOR_ANGLE);
            var source = invocation.expectedSource;
            if (source.routine() != 2 || (source.spriteFlags() & 0x80) == 0
                    || source.unsignedByte(CdObjectState.ByteField.ROUTINE_2) == 0)
                throw new IllegalStateException("Monitor angle is outside its actual native gravity/floor cut");
            var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                    monitorAngleEnvironment(invocation.shared.environment(), angle), invocation.shared.artState(),
                    invocation.shared.paused(), invocation.shared.vIntClock());
            var readyValues = preparedMonitorValues(MONITOR_FLOOR_ANGLE);
            verifyMonitorCallback(invocation, before, null);
            frame.applyObjectPrimaryAngle(angle);
            values = readyValues; invocation.expectedValues = readyValues; invocation.shared = readyShared;
            invocation.floorStarted = true; invocation.floorWrites++;
            verifyMonitorCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }

    private CdPlayerBody.Environment monitorDirectionEnvironment(CdPlayerBody.Environment environment, int direction) {
        var movement = environment.movement(); var old = movement.gates();
        var gates = new CdPlayerMovement.Gates(old.zoneAct(), old.timeZone(), old.miniSonic(), old.waterSlide(),
                old.windTunnel(), old.speedShoes(), direction);
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(), gates, movement.globals()),
                environment.controls(), environment.globals(), environment.dynamicSlots());
    }
    private void commitTimePostDirection(CdTimePost.DirectionCall call) {
        Objects.requireNonNull(call);
        var invocation = requireMonitorInvocation(placement.pool(), call.identity().wholeIndex());
        if (monitorPort || drawingMonitor || queuePort || !invocation.post || invocation.drawn || invocation.voiced
                || invocation.directionStores > 1 || invocation.directionStores == 1 && invocation.entry.subtype() == 8
                || call.rawDirection() != (invocation.directionStores == 0 ? 0xFF : 1))
            throw new IllegalStateException("Post direction lacks its native FF/conditional01 operation");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.DIRECTION); var source = invocation.expectedSource;
            int at = invocation.activationAddress;
            if (source.routine() != 4 || source.unsignedByte(CdObjectState.ByteField.TIME_POST_SPIN) != 60
                    || source.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS) != 0
                    || invocation.entry.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS) == 0
                    || at < 2 || at >= CdObjectPlacementRuntime.SAVED_BANK_BYTES || (invocation.expectedSaved.unsignedByte(at) & 1) == 0)
                throw new IllegalStateException("Post direction lacks actual cleared-contact/spin60/activation prefix");
            frame.validateObjectTimePostDirection(call.expectedRawDirection(), call.rawDirection());
            var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                    monitorDirectionEnvironment(invocation.shared.environment(), call.rawDirection()), invocation.shared.artState(),
                    invocation.shared.paused(), invocation.shared.vIntClock());
            var readyValues = preparedMonitorValues(TIME_POST_DIRECTION);
            verifyMonitorCallback(invocation, before, null);
            frame.applyObjectTimePostDirection(call.expectedRawDirection(), call.rawDirection());
            values = readyValues; invocation.expectedValues = readyValues; invocation.shared = readyShared; invocation.directionStores++;
            verifyMonitorCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }
    private void playTimePostSubCpu(CdTimePost.SubCpuCommand command) {
        Objects.requireNonNull(command); var invocation = currentMonitorInvocation();
        if (monitorPort || drawingMonitor || queuePort || !invocation.post || invocation.drawn || invocation.voiced
                || invocation.directionStores != (invocation.entry.subtype() == 8 ? 1 : 2)
                || command != (invocation.entry.subtype() == 8 ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX))
            throw new IllegalStateException("Post voice lacks completed direction stores or exact native77/76 command");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.SUB_CPU);
            var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastTimePostSubCpu(command));
            var readySounds = List.copyOf(ledger); var readyValues = preparedMonitorValues(TIME_POST_SUB_CPU);
            verifyMonitorCallback(invocation, before, null);
            sounds = readySounds; values = readyValues; invocation.expectedSounds = readySounds; invocation.expectedValues = readyValues; invocation.voiced = true;
            verifyMonitorCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }
    private void playMonitorFm(CdMonitor.FmSound sound) {
        var invocation = currentMonitorInvocation();
        if (monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.sounded
                || invocation.entry.routine() != 4 || invocation.solidPreflight || invocation.floorStarted
                || sound != CdMonitor.FmSound.FM_DESTROY)
            throw new IllegalStateException("Monitor FM96 is not its native pre-Break-store cut");
        var before = placement.capture(); monitorPort = true;
        try {
            validateMonitorPrefix(invocation, MonitorSite.MONITOR_FM);
            var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastMonitorFm(sound));
            var readySounds = List.copyOf(ledger); var readyValues = preparedMonitorValues(MONITOR_FM);
            verifyMonitorCallback(invocation, before, null);
            sounds = readySounds; values = readyValues; invocation.expectedSounds = readySounds; invocation.expectedValues = readyValues; invocation.sounded = true;
            verifyMonitorCallback(invocation, before, null);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { monitorPort = false; }
    }
    private void verifyMonitorCallback(MonitorInvocation invocation, CdObjectPlacementRuntime.Snapshot before, CdObjectState cleared) {
        verifyMonitorInvocation(invocation);
        var images = new ArrayList<>(before.pool().slots());
        if (cleared != null) images.set(invocation.whole - 32, cleared);
        var now = placement.capture();
        if (!now.status().equals(before.status()) || !now.pool().slots().equals(images) || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Monitor/Post callback changed full96/saved/status beyond its actual queue bit7 clear");
    }
    private void verifyMonitorCallbackFailure(MonitorInvocation invocation, CdObjectPlacementRuntime.Snapshot before,
                                               CdObjectState cleared, Throwable original) {
        try { verifyMonitorCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    private void drawMonitor(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireMonitorInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped || invocation.pendingCut != null)
            throw new IllegalStateException("Monitor/Post DrawObject lacks its once-only genuine native return");
        validateMonitorPrefix(invocation, MonitorSite.DRAW);
        if (invocation.noDraw) throw new IllegalStateException("ID19 attempted Draw on a native direct-return branch");
        var before = placement.capture(); var current = pool.state(invocation.whole);
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawn = true; drawingMonitor = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH), current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("ID19 actual DrawObject did not clear bit7");
            verifyMonitorCallback(invocation, before, cleared); invocation.tailArmed = !invocation.directBreak;
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure); throw failure;
        } finally { drawingMonitor = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readMonitorDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireMonitorInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingMonitor || monitorPort || queuePort) throw new IllegalStateException("No actual ID19 queue read");
        var before = placement.capture(); queuePort = true;
        try {
            acceptMonitorPool(invocation, invocation.expectedSource, List.of(), invocation.expectedSaved.copy());
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyMonitorCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, null, failure); throw failure;
        } finally { queuePort = false; }
    }
    private void writeMonitorOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireMonitorInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (!drawingMonitor || monitorPort || queuePort || onScreen || invocation.screenCleared)
            throw new IllegalStateException("Only actual ID19 DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        acceptMonitorPool(invocation, invocation.expectedSource, List.of(), invocation.expectedSaved.copy());
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        var readyPool = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(readyPool);
        queuePort = true;
        try {
            invocation.pool.write(next); invocation.screenCleared = true; invocation.currentPool = readyPool; invocation.expectedSource = next;
            verifyMonitorCallback(invocation, before, next);
        } catch (RuntimeException | Error failure) {
            verifyMonitorCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure); throw failure;
        } finally { queuePort = false; }
    }

    private void mainLevelMusic() {
        requireCallback();
        // The real8 Finish/count has completed; this callback precedes paused
        // reset admission and preserves its native music prefix on a later fault.
        representedServiceCompleted(); call(ORDINARY_SERVICE);
        int old = values.globals().pauseMusicBit().orElseThrow();
        if (frame.paused()) {
            appendSound(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ChargeSound(
                    CdPlayerCharge.Sound.FM_CHARGESTOP)));
            globals(g -> g.withPauseMusicBit(old | 0x80));
            if ((old & 0x80) != 0) return;
        } else {
            globals(g -> g.withPauseMusicBit(old & 0x7F));
            if ((old & 0x80) == 0) return;
        }
        var e = frame.environment();
        boolean pcm = (e.movement().gates().timeZone() & 0x7F) == 0
                && e.globals().invincible() == 0 && e.movement().gates().speedShoes() == 0;
        var command = frame.paused()
                ? pcm ? SonicCdGameModule.PastMusicCommand.SCMD_PAUSEPCM : SonicCdGameModule.PastMusicCommand.SCMD_PAUSECDA
                : pcm ? SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSEPCM : SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSECDA;
        appendSound(new SonicCdGameModule.PastSubCpuSound(command));
    }
    private void appendSound(SonicCdGameModule.PastSound sound) {
        var next = new ArrayList<>(sounds); next.add(sound); sounds = List.copyOf(next);
    }
    private void publishLevel() {
        requireCallback();
        // Normal8 selects palette, HScroll then SAT. No water-palette branch.
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.cpuPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        byte[] hscroll = values.displayedHScroll().copy();
        System.arraycopy(presentation.pastBackgroundImage().hscrollRam().copy(), 0, hscroll, 0, 0x380);
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), CdAssetBytes.copyOf(hscroll), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.cpuSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        call(ORDINARY_PUBLICATION);
    }
    private void serviceLevelArtAndHud() {
        requireCallback();
        var gate = CdPowerupArtRuntime.gate(values.level().powerup());
        levelFadeAndPowerup(values.level().fadeEnableDisplay(), gate.powerup());
        if (gate.copyRequired()) throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.POWERUP_ART);
        call(UPDATE_ANIMATED_TILES);
        copyAndDraw(); call(ORDINARY_DRAW_LEVEL);
        section(s -> art.serviceSlow(s, presentation.pastForegroundImage().scrollLock())); call(ORDINARY_SLOW_PLC);
        var state = frame.state(); var env = frame.environment(); var g = values.globals();
        var input = new CdLevelHud.Inputs(new CdLevelHud.Globals(env.globals().debugCheat(),
                (g.pauseMusicBit().orElseThrow() | (frame.paused() ? 1 : 0)) << 8,
                env.controls().controlLocked(), env.movement().gates().timeZone(), state.motion().centreX16_16() >>> 16,
                state.motion().centreY16_16() >>> 16, env.movement().gates().zoneAct()),
                new CdLevelHud.DebugData(OptionalInt.empty(), g.layoutRam(), g.debugBlockWord()));
        try { adoptHud(hud.run(new CdLevelHud.State(values.section(), values.ring(), values.level(), g.score(),
                g.live(), g.bonus()), input).state()); }
        catch (CdLevelHud.UnsupportedHud stopped) { adoptHud(stopped.state()); throw stopped; }
        call(UPDATE_HUD);
    }
    private void adoptHud(CdLevelHud.State next) {
        var g = values.globals();
        replace(values.phase(), next.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), next.ring(),
                next.level(), new LoadingGlobals(next.liveTime(), g.contact(), next.score(), g.drownTimerWord(), g.tail(),
                        next.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev()), values.hudObjects(), values.calls());
    }

    private void representedServiceCompleted() {
        // Account only a successfully returned Frame service, before its CPU continuation.
        // A raw/publication prefix that faults before Finish is not a completed pulse.
        EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
    }

    private void publish() {
        requireCallback();
        var background = presentation.pastBackgroundImage();
        byte[] selectedHScroll = values.displayedHScroll().copy();
        System.arraycopy(background.hscrollRam().copy(), 0, selectedHScroll, 0, 0x380);
        // Common VInterrupt selects the CPU LONG; C then publishes only 0x380 HScroll
        // bytes. Preserve the InitVDP-cleared selected tail and actual native VScroll.
        values = new Values(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(),
                values.cpuPalette(), values.cpuSat(), values.cpuSat(), CdAssetBytes.copyOf(selectedHScroll),
                background.vscroll(), values.foregroundPlane(), values.ring(), values.level(), values.globals(),
                values.hudObjects(), values.fadeProgress(), values.calls(), values.loop());
    }

    private void copyAndDraw() {
        requireCallback();
        var bg = presentation.servicePastPlaneAtLevelLoad();
        var copies = presentation.pastDrawCopies();
        if ((copies.fgFlags() & 0xFF00) == 0) return;
        if (values.foregroundPlane() == null) throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.ENTRY);
        // BG1 always restores IDs+1. Its column path advances through all16 rows,
        // including skipped rows, retaining the association just beyond the final iteration.
        int index = 1;
        for (var read : bg.sections()) {
            if (read.phase() == CdR11aBackgroundPlane.Phase.INCREMENTAL_COLUMN) index = read.tableIndex() + 1;
        }
        var ids = assets.view(CdR11bAssets.Asset.BG_CAMERA_SECT_IDS).bytes();
        var metadata = new CdR11bForegroundPlane.CallerMetadata(
                index >= 0 && index < ids.length() ? OptionalInt.of(ids.unsignedByte(index)) : OptionalInt.empty());
        CdR11bForegroundPlane.Result result;
        try {
            result = foreground.incremental(values.foregroundPlane(),
                    new CdR11aBackgroundPlane.Camera(copies.fgX16_16(), copies.fgY16_16()), copies.fgFlags(), metadata);
        } catch (CdR11bForegroundPlane.InvalidPlane stopped) {
            var prefix = stopped.completedPrefix();
            adoptForeground(prefix.state(), prefix.flagsWord());
            throw stopped;
        }
        adoptForeground(result.state(), result.flagsWord());
    }
    private void adoptForeground(CdR11aBackgroundPlane.State plane, int flags) {
        presentation.consumePastForegroundFlags(flags);
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), plane, values.ring(), values.level(),
                values.globals(), values.hudObjects(), values.calls());
    }
    private void fastPlc() { requireCallback(); section(art::serviceFast); }

    private void mainLoadPrefix() {
        requireCurrent();
        if (!sounds.isEmpty()) throw new IllegalStateException("Past music request cannot be replayed");
        sounds = List.of(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_PASTMUS)));
        call(PLAY_LEVEL_MUSIC); // Typed actual Sub CPU request; no sound-driver synthesis.
        section(s -> art.loadImmediateList(s, 3)); call(IMMEDIATE_LEVEL);
        section(s -> art.loadImmediateList(s, 1)); call(IMMEDIATE_STANDARD);
        // Main168 CLR.L includes fadeEnableDisplay, unlike its adjacent powerup byte.
        var l = values.level();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), values.ring(),
                new CdLevelGlobals(l.levelRestart(), l.updateHudTime(), l.checkpoint(), l.spawnMode(),
                        l.plcLoadFlags(), 0, 0, 0, l.timeOver()), values.globals(), values.hudObjects(), values.calls());
        frame.clearLevelStartFlowersPlayerValues();
        var r = values.ring();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                new CdRingGlobals(r.rings(), r.lives(), r.livesFlags(), r.updateHudRings(), r.updateHudLives(), 0, 0, 0),
                values.level(), values.globals(), values.hudObjects(), values.calls());
        // Main178-184 actually clears FF1600..FF1FFF, including ALL global animations.
        var loop = values.loop();
        loop(new LoopGlobals(loop.demoDataIndexWord(), Optional.of(new CdGlobalObjectAnimation.Extras(
                0, 0, 0, 0, new CdRingSpillGlobals(0, 0, 0))),
                loop.paletteSteps7(), loop.paletteTimers7(), loop.completedDraw(), loop.selectedDisplay()));
        presentation.clearPastFlowersImages(); globals(LoadingGlobals::clearDebugBlock); call(CLEAR_FLOWERS);
        // The actual complete object clear includes primary slot0; its fresh RunMMD
        // image is already zero and has had no native object writer before this cut.
        if (frame.state().header().slotId() != 0 || frame.state().motion().routine() != 0)
            throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.ENTRY);
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), zero(31 * 64), values.calls());
        placement.pool().clearDynamicSlots();
        call(CLEAR_OBJECTS);
        globals(g -> g.withLive(CdCheckpointLiveAux.clearMiscAtLevelStart(g.live())).withPauseMusicBit(0)); call(CLEAR_MISC);
        frame.clearLevelStartCameraPlayerValues(); presentation.clearPastCameraImages();
        // RunMMD's FF2000..FFFAFF clear already established these zero bytes;
        // Main210-216 re-clears the FFF7B0..FFF7BD palette allocation.
        var cameraLoop = values.loop();
        loop(new LoopGlobals(cameraLoop.demoDataIndexWord(), cameraLoop.animations(), zero(7), zero(7), cameraLoop.completedDraw(), cameraLoop.selectedDisplay()));
        globals(LoadingGlobals::clearBonus); call(CLEAR_CAMERA);
        // Main223, after the real camera clear and before ClearScreen. Never borrow demoS1Index/F790.
        var demoLoop = values.loop();
        loop(new LoopGlobals(OptionalInt.of(0), demoLoop.animations(), demoLoop.paletteSteps7(),
                demoLoop.paletteTimers7(), demoLoop.completedDraw(), demoLoop.selectedDisplay())); call(CLEAR_DEMO_INDEX);
        presentation.clearPastScreenImages();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                zero(CdObjectDrawQueue.SAT_BYTE_SIZE), values.displayedSat(), values.displayedHScroll(),
                new CdR11aBackgroundPlane.State(zero(CdR11aBackgroundPlane.NAME_TABLE_BYTES)),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        call(CLEAR_SCREEN);
        globals(g -> g.withDrownTimer(30));
        var sonic = assets.view(CdR11bAssets.Asset.PAL_SONIC).bytes();
        palette(sonic, 0, true); palette(sonic, 0, false); call(PALETTE_SONIC);
        var bounds = assets.loadPlan().bounds();
        frame.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(bounds.left(), bounds.right(), bounds.bottom(),
                frame.environment().movement().bounds().bossFight()), bounds.cameraCentreY());
        presentation.initializePastLevelSizeImages(); call(LEVEL_SIZE);
        loadSaved(); call(LOAD_SAVED);
        presentation.completePastLoadScroll(frame, values.level().updateHudTime()); call(LEVEL_SCROLL);
        CdR11bSectionArt.BlocksResult blocks;
        try { blocks = art.decodeBlocksToRam(values.section()); }
        catch (CdR11bSectionArt.SectionArtException stopped) { section(stopped.state()); throw stopped; }
        section(blocks.state()); call(BLOCKS_RAM);
        var layout = executeLayoutCopy(); globals(g -> g.withLayout(layout)); call(LEVEL_LAYOUT);
        palette(assets.view(CdR11bAssets.Asset.PAL_LEVEL).bytes(), 32, false); call(PALETTE_LEVEL);
        section(s -> art.loadSection(s, presentation.pastForegroundImage().cameraX())); call(SECTION_ART);
        // LoadLevelLayout's final copied BG row leaves the next byte at actual RAM CA.
        var f = presentation.pastForegroundImage();
        var drawn = foreground.initialize(values.foregroundPlane(),
                new CdR11aBackgroundPlane.Camera(f.cameraX() << 16, f.cameraY16_16()),
                f.scrollFlags(), new CdR11bForegroundPlane.CallerMetadata(OptionalInt.of(layout.unsignedByte(finalBackgroundLayoutCursor()))));
        adoptForeground(drawn.state(), presentation.pastDrawCopies().fgFlags()); call(INITIAL_FOREGROUND);
        presentation.initializePastPlaneAtLoad(); call(INITIAL_BACKGROUND);
        call(COLLISION_POINTER);
        phase(SECTION_PLC);
    }

    private CdAssetBytes executeLayoutCopy() {
        byte[] ram = new byte[2048];
        copyLayout(ram, 0, assets.view(CdR11bAssets.Asset.LAYOUT_FG).bytes());
        copyLayout(ram, 64, assets.view(CdR11bAssets.Asset.LAYOUT_BG).bytes());
        var prepared = assets.terrain().layout().ram();
        if (!Arrays.equals(ram, prepared.copy())) throw new IllegalStateException("Executed Past layout differs from authenticated product");
        return prepared; // Same-value adoption only AFTER the genuine source copy.
    }
    private int finalBackgroundLayoutCursor() {
        var bg = assets.view(CdR11bAssets.Asset.LAYOUT_BG).bytes();
        return 64 + bg.unsignedByte(1) * 128 + bg.unsignedByte(0) + 1;
    }
    private static void copyLayout(byte[] ram, int base, CdAssetBytes source) {
        int columns = source.unsignedByte(0) + 1, rows = source.unsignedByte(1) + 1, at = 2;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) ram[base + y * 128 + x] = (byte) source.unsignedByte(at++);
        }
        if (at != source.length()) throw new IllegalArgumentException("Complete native layout source required");
    }
    private void palette(CdAssetBytes source, int offset, boolean cpu) {
        byte[] target = (cpu ? values.cpuPalette() : values.fadePalette()).copy();
        System.arraycopy(source.copy(), 0, target, offset, source.length());
        replace(values.phase(), values.section(), cpu ? CdAssetBytes.copyOf(target) : values.cpuPalette(),
                cpu ? values.fadePalette() : CdAssetBytes.copyOf(target), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
    }

    private void finishSectionWait() {
        requireCurrent();
        frame.applyLevelStartWarpPlayer(); call(LOAD_PLAYER);
        byte[] hud = values.hudObjects().copy();
        for (int whole : new int[]{2, 3, 5}) hud[(whole - 1) * 64] = 0x1C;
        hud[(3 - 1) * 64 + 0x28] = 1; hud[(5 - 1) * 64 + 0x29] = 1;
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), CdAssetBytes.copyOf(hud), values.calls());
        call(RESERVED_HUD);
        section(s -> art.loadLifeIcon(s, frame.environment().movement().gates().timeZone())); call(LIFE_ICON);
        byte[] icon = values.hudObjects().copy();
        icon[(31 - 1) * 64] = 0x19; icon[(31 - 1) * 64 + 0x28] = 0x0A;
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), CdAssetBytes.copyOf(icon), values.calls());
        call(RESERVED_TIME_ICON);
        if ((values.level().plcLoadFlags() & 2) == 0)
            throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.ENTRY);
        frame.clearLevelStartControlsAndBoredTimer();
        frame.clearLevelStartPlayerGlobals();
        var l = values.level(); var r = values.ring();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                new CdRingGlobals(r.rings(), r.lives(), r.livesFlags(), 0x80, 1, r.timeStopTimer(),
                        r.ringAnimationTimer(), r.ringAnimationFrame()),
                new CdLevelGlobals(0, 1, l.checkpoint(), 0, l.plcLoadFlags() | 2, l.flowerCount(),
                        l.fadeEnableDisplay(), l.powerup(), 0), values.globals(),
                values.hudObjects(), values.calls());
        globals(LoadingGlobals::finishMainStores);
        call(BEGIN_MAIN); phase(ANIMATED_TILES);
        beginFromWhite();
    }

    private void beginFromWhite() {
        // PP Past UpdateAnimTiles is JMP LoadShieldArt. BCLR precedes its bit7 gate;
        // an inactive raw byte requires no assets, art buffer, timer or table read.
        var gate = CdPowerupArtRuntime.gate(values.level().powerup());
        levelFadeAndPowerup(values.level().fadeEnableDisplay(), gate.powerup());
        if (gate.copyRequired()) throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.POWERUP_ART);
        call(UPDATE_ANIMATED_TILES);
        levelFadeAndPowerup(1, values.level().powerup()); // Main:314
        if (!frame.clearLevelStartTimeZoneMarker()) {
            throw new IllegalStateException("Actual marked Past loading lost its Main315 branch");
        }
        // Main's taken marked-period branch skips palClearFlags BCLR. The called
        // FadeFromWhite overrides202F with003F and fills ALL64 CPU colours.
        globals(g -> new LoadingGlobals(g.live(), g.contact(), g.score(), g.drownTimerWord(),
                Optional.of(new TailValues(g.tail().orElseThrow().player2ControlsWord(),
                        g.tail().orElseThrow().player2BoredWord(), g.tail().orElseThrow().unknownLevelByte(),
                        g.tail().orElseThrow().demoS1IndexWord(), 0x003F)), g.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev()));
        byte[] white = new byte[128];
        for (int at = 0; at < white.length; at += 2) { white[at] = 0x0E; white[at + 1] = (byte) 0xEE; }
        replace(FROM_WHITE, values.section(), CdAssetBytes.copyOf(white), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
    }
    private void levelFadeAndPowerup(int fade, int powerup) {
        var l = values.level();
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(), values.ring(),
                new CdLevelGlobals(l.levelRestart(), l.updateHudTime(), l.checkpoint(), l.spawnMode(), l.plcLoadFlags(),
                        l.flowerCount(), fade, powerup, l.timeOver()), values.globals(), values.hudObjects(), values.calls());
    }
    private void fadeProgress(FadeProgress progress) {
        values = new Values(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(),
                values.displayedPalette(), values.cpuSat(), values.displayedSat(), values.displayedHScroll(),
                values.displayedVScrollLongBits(), values.foregroundPlane(), values.ring(), values.level(),
                values.globals(), values.hudObjects(), progress, values.calls(), values.loop());
    }
    private void publishFromWhite() {
        publish();
        if (values.level().fadeEnableDisplay() == 1) {
            levelFadeAndPowerup(2, values.level().powerup());
            // Native12 ORI40 into the InitVDP-derived display cache, expressed as
            // its selected semantic output. No renderer/GPU readiness is granted.
            fadeProgress(new FadeProgress(values.fadeProgress().completedSteps(), true));
        }
    }
    private void serviceFromWhite(int held) {
        var selectedCpu = values.loop().completedDraw();
        servicing = true;
        try { frame.serviceLevelPaletteFade(held, this::publishFromWhite, this::fastPlc); }
        finally { servicing = false; }
        representedServiceCompleted(); call(FROM_WHITE_SERVICE);
        var colours = CdLevelPaletteFade.stepFromWhite(values.cpuPalette(), values.fadePalette());
        replace(values.phase(), values.section(), colours, values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), values.hudObjects(), values.calls());
        call(FROM_WHITE_COLOURS);
        section(art::prepare); call(PROCESS_PLCS);
        fadeProgress(new FadeProgress(values.fadeProgress().completedSteps() + 1, values.fadeProgress().displayEnabled()));
        if (values.fadeProgress().completedSteps() == 22) {
            module.completePastLoadingBeginLevel(this); call(LEVEL_STARTED); phase(BEGIN_LEVEL);
        }
        settleSelectedDisplay(selectedCpu);
    }

    private void loadSaved() {
        requireCurrent();
        if (values.level().spawnMode() != 2 || frame.environment().movement().gates().timeZone() != 0x80)
            throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.ENTRY);
        loadingSaved = true; savedCursor = 0;
        try {
            var result = CdTimeWarpLoadData.load(saved, new CdTimeWarpLoadData.Port() {
                @Override public CdTimeWarpSaveData.State currentSaved() { requireSavedPort(); return saved; }
                @Override public OptionalInt read(CdTimeWarpLoadData.StoreTarget target) {
                    requireSavedPort();
                    var value = readSavedTarget(target);
                    observedTarget = target; observedValue = value; observedValues = values;
                    observedActor = frame.captureActor(); observedShared = frame.captureShared();
                    observedScroll = presentation.capturePastScroll();
                    return value;
                }
                @Override public OptionalInt readLive(CdTimeWarpLoadData.LiveRead source) {
                    requireSavedPort();
                    if (source == CdTimeWarpLoadData.LiveRead.ZONE && savedCursor == 20) {
                        int zone = frame.environment().movement().gates().zoneAct() >>> 8;
                        if (zone != 6) savedCursor++;
                        return OptionalInt.of(zone);
                    }
                    if (source == CdTimeWarpLoadData.LiveRead.SPAWN_MODE && savedCursor == 21) {
                        int spawn = values.level().spawnMode();
                        if ((spawn & 0x80) == 0) savedCursor++;
                        return OptionalInt.of(spawn);
                    }
                    throw new IllegalStateException("Wrong actual saved-load conditional read");
                }
                @Override public void commit(CdTimeWarpLoadData.StoreCall call) {
                    requireSavedPort();
                    if (savedCursor >= CdTimeWarpLoadData.NativeStore.values().length
                            || call.instruction() != CdTimeWarpLoadData.NativeStore.values()[savedCursor]
                            || observedTarget != call.target() || !call.expectedValue().equals(observedValue)
                            || values != observedValues || !frame.captureActor().equals(observedActor)
                            || !frame.captureShared().equals(observedShared)
                            || !presentation.samePastScroll(observedScroll)) {
                        throw new IllegalStateException("Stale or out-of-sequence actual saved-load store");
                    }
                    storeSavedTarget(call); savedCursor++; observedTarget = null;
                }
            });
            if (savedCursor != 22 || result.completedNativeStores().size() != 20)
                throw new IllegalStateException("Past saved-load source prefix incomplete");
        } finally {
            loadingSaved = false; observedTarget = null; observedValue = null;
            observedValues = null; observedActor = null; observedShared = null; observedScroll = null;
        }
    }
    private void requireSavedPort() {
        module.requirePastLoadingVisit(this);
        if (!loadingSaved || servicing) throw new IllegalStateException("No actual saved-load invocation");
    }
    private OptionalInt readSavedTarget(CdTimeWarpLoadData.StoreTarget target) {
        return switch (target) {
            case PRIMARY_X, PRIMARY_Y, PRIMARY_FLAGS, PRIMARY_GVEL, PRIMARY_X_VELOCITY, PRIMARY_Y_VELOCITY,
                    BOTTOM_BOUND, MINI_SONIC, LEFT_BOUND -> OptionalInt.of(frame.readTimeWarpLoadField(target));
            case DEST_BOTTOM_BOUND, FG_X, FG_Y, BG_X, BG_Y, BG2_X, BG2_Y, BG3_X, BG3_Y ->
                    OptionalInt.of(presentation.readPastLoadWord(target));
            case RINGS -> OptionalInt.of(values.ring().rings());
            case LIVES_FLAGS -> OptionalInt.of(values.ring().livesFlags());
            case TIME_LONG -> liveValue(CdCheckpointLiveAux.Field.TIME_LONG);
            case WATER_ROUTINE -> liveValue(CdCheckpointLiveAux.Field.WATER_ROUTINE);
        };
    }
    private void storeSavedTarget(CdTimeWarpLoadData.StoreCall call) {
        switch (call.target()) {
            case PRIMARY_X, PRIMARY_Y, PRIMARY_FLAGS, PRIMARY_GVEL, PRIMARY_X_VELOCITY, PRIMARY_Y_VELOCITY,
                    BOTTOM_BOUND, MINI_SONIC, LEFT_BOUND -> frame.applyTimeWarpLoadStore(call);
            case DEST_BOTTOM_BOUND, FG_X, FG_Y, BG_X, BG_Y, BG2_X, BG2_Y, BG3_X, BG3_Y -> presentation.storePastLoadWord(call);
            case RINGS, LIVES_FLAGS, TIME_LONG, WATER_ROUTINE -> {
                var r = values.ring();
                replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                        values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                        new CdRingGlobals(call.target() == CdTimeWarpLoadData.StoreTarget.RINGS ? call.value() : r.rings(),
                                r.lives(), call.target() == CdTimeWarpLoadData.StoreTarget.LIVES_FLAGS ? call.value() : r.livesFlags(),
                                r.updateHudRings(), r.updateHudLives(), r.timeStopTimer(), r.ringAnimationTimer(), r.ringAnimationFrame()),
                        values.level(), values.globals().withLive(
                                call.target() == CdTimeWarpLoadData.StoreTarget.TIME_LONG
                                        ? CdCheckpointLiveAux.writeTimeLong(values.globals().live(), call.value())
                                        : call.target() == CdTimeWarpLoadData.StoreTarget.WATER_ROUTINE
                                        ? CdCheckpointLiveAux.writeWaterRoutine(values.globals().live(), call.value())
                                        : values.globals().live()), values.hudObjects(), values.calls());
            }
        }
    }

    SonicCdGameModule.PastLoadState observation() {
        requireCurrent();
        return new SonicCdGameModule.PastLoadState(assets, values.phase(), values.cpuPalette(), values.displayedPalette(),
                values.ring(), values.level(), values.section(), frame.captureActor(), frame.captureShared(), values.calls(), sounds);
    }
    Snapshot capture() {
        presentation.requirePastRenderIdle();
        requireCurrent();
        return new Snapshot(this, values, frame.captureActor(), frame.captureShared());
    }
    void validateRestore(Snapshot capture, SonicCdGameModule.LevelProgramState program) {
        presentation.requirePastRenderIdle();
        requireCurrent(); Objects.requireNonNull(capture);
        if (capture.owner != this || capture.values == null) throw new IllegalArgumentException("Past capture belongs to another actual visit");
        placement.validateRestore(capture.placement);
        frame.validateActorRestore(capture.actor); frame.validateSharedRestore(capture.shared);
        if (capture.values.section().stopped()) throw new IllegalArgumentException("Stopped loading cannot be a healthy capture");
        boolean first = capture.values.phase() == FIRST_PLC;
        if (capture.flags.load() != first || capture.flags.setup() != first
                || capture.values.phase() == ANIMATED_TILES)
            throw new IllegalArgumentException("Past phase and source setup flags differ");
        var v = capture.values; var progress = v.fadeProgress();
        var retained = v.loop().selectedDisplay();
        boolean displayEnabled = progress.displayEnabled();
        // Main may stream foreground names and prepare the next CPU art cue after selection.
        // Keep those selected physical images/cue values; only resident player pixels remain
        // the same actual service bank in the later captured Frame.
        if (displayEnabled && !Arrays.equals(retained.inputs().player().residentBank().copy(),
                capture.shared.artState().residentBank().copy()))
            throw new IllegalArgumentException("Captured resident Sonic art differs from selected service bank");
        SonicCdPastDisplay.validateSelected(retained, new SonicCdPastDisplay.Inputs(
                retained.inputs().selectedCpu(), v.displayedSat(), v.displayedPalette(), v.displayedHScroll(),
                v.displayedVScrollLongBits(), displayEnabled, retained.inputs().foreground(),
                retained.inputs().background(), displayEnabled ? v.section().residency() : null,
                retained.inputs().player()));
        boolean fading = v.phase() == FROM_WHITE; boolean begun = v.phase() == BEGIN_LEVEL;
        if (capture.shared.environment().movement().gates().timeZone() != (fading || begun ? 0 : 0x80)
                || (fading && progress.completedSteps() >= 22)
                || (begun && progress.completedSteps() != 22)
                || (!fading && !begun && progress.completedSteps() != 0)
                || progress.displayEnabled() != (progress.completedSteps() != 0)
                || (!first && v.level().fadeEnableDisplay() != (fading || begun ? progress.completedSteps() == 0 ? 1 : 2 : 0))
                || program.phase() != SonicCdGameModule.LevelProgramPhase.PAST_LOADING
                || program.levelStarted() != (begun ? 1 : 0)
                || (!first && (v.globals().pauseMusicBit().isEmpty()
                        || v.globals().pauseMusicBit().getAsInt() != (begun && capture.shared.paused() ? 0x80 : 0)))
                || ((program.gameMode() & 0x80) != 0) == begun
                || ((fading || begun) && v.globals().tail().orElseThrow().paletteFadeInfoWord() != 0x003F)) {
            throw new IllegalArgumentException("Past marker, native fade progress and programme values differ");
        }
    }
    void restore(Snapshot capture, SonicCdGameModule.LevelProgramState program) {
        validateRestore(capture, program);
        frame.restoreActor(capture.actor); frame.restoreShared(capture.shared);
        placement.restore(capture.placement);
        presentation.restorePastLoadFlags(capture.flags); values = capture.values; sounds = capture.sounds;
        restorePlayerBank();
    }
}
