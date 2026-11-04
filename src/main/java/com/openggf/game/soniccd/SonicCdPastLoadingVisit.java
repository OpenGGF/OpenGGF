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
    private final CdCollapsePlatform collapsePlatform;
    private final CdFloatBlock floatBlock;
    private final CdAnimal animal;
    private final CdPataBata pataBata;
    private final CdTamabboh tamabboh;
    private final CdBoulder boulder;
    private final CdMosqui mosqui;
    private final CdSpikes spikes;
    private final CdAnton anton;
    private final CdRobotGenerator robotGenerator;
    private final CdExplosion explosion;
    private final CdGoalPost goalPost;
    private final CdSignpost signpost;
    private final CdBigRing bigRing;
    private final CdResults results;
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
    private transient CollapseInvocation activeCollapse;
    private transient FloatInvocation activeFloat;
    private transient PataInvocation activePata;
    private transient TamabbohInvocation activeTamabboh;
    private transient BoulderInvocation activeBoulder;
    private transient MosquiInvocation activeMosqui;
    private transient SpikesInvocation activeSpikes;
    private transient AntonInvocation activeAnton;
    private transient MachineInvocation activeMachine;
    private transient GoalInvocation activeGoal;
    private transient SignpostInvocation activeSignpost;
    private transient BigRingInvocation activeBigRing;
    private transient ResultsInvocation activeResults;
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
    private boolean collapsePort;
    private boolean drawingCollapse;
    private boolean platformPort;
    private boolean drawingPlatform;
    private boolean floatPort;
    private boolean drawingFloat;
    private boolean drawingPata;
    private boolean tamabbohPort;
    private boolean drawingTamabboh;
    private boolean boulderPort;
    private boolean drawingBoulder;
    private boolean mosquiPort;
    private boolean drawingMosqui;
    private boolean spikesPort;
    private boolean drawingSpikes;
    private boolean antonPort;
    private boolean drawingAnton;
    private boolean bigRingPort;
    private boolean drawingBigRing;
    private boolean resultsPort;
    private boolean drawingResults;
    private boolean signpostPort;
    private boolean drawingSignpost;
    private boolean goalPort;
    private boolean drawingGoal;
    private boolean machinePort;
    private boolean drawingMachine;
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
        boulder = CdBoulder.fromPastAssets(placement, assets, sharedSolid, this::commitBoulderSolid, this::drawBoulder);
        mosqui = CdMosqui.fromPastAssets(placement, assets, sharedTerrain, this::commitMosquiPrimaryAngle,
                this::playMosquiFm, this::drawMosqui);
        spikes = CdSpikes.fromPastAssetsWithPrefix(placement, assets, sharedSolid, this::commitSpikesSolid,
                this::commitSpikesYRewind, this::commitSpikesHurt, this::drawSpikes);
        anton = CdAnton.fromPastAssets(placement, assets, sharedTerrain, this::commitAntonPrimaryAngle, this::drawAnton);
        results = new CdResults(placement, CdResults.Assets.fromPastAssets(assets), this::readResults,
                this::storeResultsBonus, this::addResultsPoints, this::loadResultsPlc, this::soundResults, this::drawResults,
                null, null); // Native NextLevel's first MOVE.W#2,levelRestart remains unowned, before its write.
        bigRing = new CdBigRing(placement, CdBigRing.Assets.fromPastAssets(assets), this::readBigRing,
                this::storeBigRingSpecialStage, this::storeBigRingPlayer, this::storeBigRingScrollLock, this::soundBigRing, this::drawBigRing);
        signpost = new CdSignpost(placement, CdSignpost.Assets.fromPastAssets(assets), this::readSignpost,
                this::storeSignpost, this::soundSignpost, this::drawSignpost);
        goalPost = new CdGoalPost(placement, CdGoalPost.Assets.fromPastAssets(assets), this::readGoalPlayerWord,
                this::storeGoalReached, this::loadGoalPlc, this::readGoalPlcHead, this::drawGoal);
        robotGenerator = new CdRobotGenerator(placement, CdRobotGenerator.Assets.fromPastAssets(assets), sharedSolid,
                this::commitRobotSolid, this::storeRobotGoodFuture, this::addRobotPoints, this::playRobotFm, this::drawRobot);
        explosion = CdExplosion.fromPastAssets(placement, assets, this::drawExplosion);
        spring = new CdSpring(placement, CdSpring.Assets.fromPastAssets(assets), new CdPlayerMath(assets.player()),
                sharedSolid, this::commitSpringSolid, this::commitSpringLaunch, this::drawSpring, this::playSpringFm);
        platform = new CdPlatform(placement, CdPlatform.Assets.fromPastAssets(assets), new CdPlayerMath(assets.player()),
                sharedTerrain, sharedSolid,
                this::commitPlatformSolid, this::commitPlatformPrimaryAngle, this::drawPlatform);
        collapsePlatform = new CdCollapsePlatform(placement, CdCollapsePlatform.Assets.fromPastAssets(assets), sharedSolid,
                this::commitCollapseSolid, this::drawCollapse, this::playCollapseFm);
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || servicing || loadingSaved || runningObjects || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || drawQueue != null)
            throw new IllegalStateException("Past loading operation or pending object pass is active");
        frame.captureShared();
    }
    private void requireCallback() {
        module.requirePastLoadingVisit(this);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !servicing || loadingSaved || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat) throw new IllegalStateException("No actual Past selectorC callback");
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || runningObjects || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || drawQueue != null)
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
                } else if (id == CdBoulder.OBJECT_ID) {
                    runBoulder(whole); call(BOULDER_BODY);
                } else if (id == CdMosqui.OBJECT_ID) {
                    runMosqui(whole); call(MOSQUI_BODY);
                } else if (id == CdSpikes.OBJECT_ID) {
                    runSpikes(whole); call(SPIKES_BODY);
                } else if (id == CdAnton.OBJECT_ID) {
                    runAnton(whole); call(ANTON_BODY);
                } else if (id == CdResults.OBJECT_ID) {
                    runResults(whole); call(RESULTS_BODY);
                } else if (id == CdBigRing.OBJECT_ID) {
                    runBigRing(whole); call(BIG_RING_BODY);
                } else if (id == CdSignpost.OBJECT_ID) {
                    runSignpost(whole); call(SIGNPOST_BODY);
                } else if (id == CdGoalPost.OBJECT_ID) {
                    runGoal(whole); call(GOAL_POST_BODY);
                } else if (id == CdRobotGenerator.OBJECT_ID) {
                    runMachine(whole, true); call(ROBOT_GENERATOR_BODY);
                } else if (id == CdExplosion.OBJECT_ID) {
                    runMachine(whole, false); call(EXPLOSION_BODY);
                } else if (id == CdSpring.OBJECT_ID) {
                    runSpring(whole); call(SPRING_BODY);
                } else if (id == CdCollapsePlatform.OBJECT_ID) {
                    runCollapse(whole); call(COLLAPSE_PLATFORM_BODY);
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || reservedPort || drawingReserved
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || invocation == null || activePrimary != invocation || invocation.owner != this
                || !runningObjects || servicing || loadingSaved || activeDrain != null || activeReserved != null
                || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null
                || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort || drawingRing
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeDrain == null || !queuePort || drawQueue == null
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activeDrain != null || activePrimary != null || activeReserved != null
                || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null
                || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort || drawingRing
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || invocation == null || activeDrain != invocation
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort
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
        var collapse1 = assets.view(CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_1);
        var collapse2 = assets.view(CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_2);
        var collapse3 = assets.view(CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_3);
        var collapse4 = assets.view(CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_4);
        var floatView = assets.view(CdR11bAssets.Asset.MAP_SPR_FLOAT_BLOCK);
        var pataNormalView = assets.view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA1);
        var pataDamagedView = assets.view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA2);
        var tamabbohView = assets.view(CdR11bAssets.Asset.MAP_SPR_TAMABBOH);
        var tamabbohMissileView = assets.view(CdR11bAssets.Asset.MAP_SPR_TAMABBOH_MISSILE);
        var boulderView = assets.view(CdR11bAssets.Asset.MAP_SPR_BOULDER);
        var mosquiNormal = assets.view(CdR11bAssets.Asset.MAP_SPR_MOSQUI_NORMAL);
        var mosquiDamaged = assets.view(CdR11bAssets.Asset.MAP_SPR_MOSQUI_DAMAGED);
        var spikesView = assets.view(CdR11bAssets.Asset.MAP_SPR_SPIKES);
        var antonView = assets.view(CdR11bAssets.Asset.MAP_SPR_ANTON);
        var bigRingView = assets.view(CdR11bAssets.Asset.MAP_SPR_BIG_RING);
        var bigRingFlashView = assets.view(CdR11bAssets.Asset.MAP_SPR_BIG_RING_FLASH);
        var resultsView = assets.view(CdR11bAssets.Asset.MAP_SPR_RESULTS);
        var goalView = assets.view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST);
        var robotView = assets.view(CdR11bAssets.Asset.MAP_SPR_ROBOT_GENERATOR);
        var explosionView = assets.view(CdR11bAssets.Asset.MAP_SPR_EXPLOSION);
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
                new CdObjectMappings.DeclaredTable(collapse1.range().nativeAddress(), collapse1.bytes(), 0, 2),
                new CdObjectMappings.DeclaredTable(collapse2.range().nativeAddress(), collapse2.bytes(), 0, 6),
                new CdObjectMappings.DeclaredTable(collapse3.range().nativeAddress(), collapse3.bytes(), 0, 7),
                new CdObjectMappings.DeclaredTable(collapse4.range().nativeAddress(), collapse4.bytes(), 0, 7),
                new CdObjectMappings.DeclaredTable(floatView.range().nativeAddress(), floatView.bytes(), 0, 6),
                new CdObjectMappings.DeclaredTable(pataNormalView.range().nativeAddress(), pataNormalView.bytes(), 0, 3),
                new CdObjectMappings.DeclaredTable(pataDamagedView.range().nativeAddress(), pataDamagedView.bytes(), 0, 3),
                new CdObjectMappings.DeclaredTable(tamabbohView.range().nativeAddress(), tamabbohView.bytes(), 0, 3),
                new CdObjectMappings.DeclaredTable(tamabbohView.range().nativeAddress(), tamabbohView.bytes(), 22, 3),
                new CdObjectMappings.DeclaredTable(tamabbohMissileView.range().nativeAddress(), tamabbohMissileView.bytes(), 0, 4),
                new CdObjectMappings.DeclaredTable(boulderView.range().nativeAddress(), boulderView.bytes(), 0, 1),
                new CdObjectMappings.DeclaredTable(mosquiNormal.range().nativeAddress(), mosquiNormal.bytes(), 0, 5),
                new CdObjectMappings.DeclaredTable(mosquiDamaged.range().nativeAddress(), mosquiDamaged.bytes(), 0, 5),
                new CdObjectMappings.DeclaredTable(spikesView.range().nativeAddress(), spikesView.bytes(), 0, 1),
                new CdObjectMappings.DeclaredTable(antonView.range().nativeAddress(), antonView.bytes(), 0, 4),
                new CdObjectMappings.DeclaredTable(bigRingView.range().nativeAddress(), bigRingView.bytes(), 0, 4),
                new CdObjectMappings.DeclaredTable(bigRingFlashView.range().nativeAddress(), bigRingFlashView.bytes(), 0, 8),
                // Results four roots retain the SAME complete622B source, including shared bodies and real pads.
                new CdObjectMappings.DeclaredTable(resultsView.range().nativeAddress(), resultsView.bytes(), 0, 5),
                new CdObjectMappings.DeclaredTable(resultsView.range().nativeAddress(), resultsView.bytes(), 10, 5),
                new CdObjectMappings.DeclaredTable(resultsView.range().nativeAddress(), resultsView.bytes(), 20, 5),
                new CdObjectMappings.DeclaredTable(resultsView.range().nativeAddress(), resultsView.bytes(), 30, 5),
                new CdObjectMappings.DeclaredTable(goalView.range().nativeAddress(), goalView.bytes(), 0, 6),
                new CdObjectMappings.DeclaredTable(robotView.range().nativeAddress(), robotView.bytes(), 0, 8),
                new CdObjectMappings.DeclaredTable(explosionView.range().nativeAddress(), explosionView.bytes(), 0, 7),
                new CdObjectMappings.DeclaredTable(tunnelDoorView.range().nativeAddress(), tunnelDoorView.bytes(), 0, 2)),
                List.of());
        return new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                if (activeDrain != null) return readDrainingSlot(ref);
                if (Objects.requireNonNull(ref).wholeIndex() == 0)
                    throw new IllegalStateException("Primary live pointer reads belong to the actual drain");
                return Objects.requireNonNull(ref).wholeIndex() < 32 ? readReservedDrawSlot(ref)
                        : activeSpring != null ? readSpringDrawSlot(ref)
                        : activeCollapse != null ? readCollapseDrawSlot(ref)
                        : activePlatform != null ? readPlatformDrawSlot(ref)
                        : activeFloat != null ? readFloatDrawSlot(ref)
                        : activePata != null ? readPataDrawSlot(ref)
                        : activeTamabboh != null || activeCollapse != null ? readTamabbohDrawSlot(ref)
                        : activeMosqui != null ? readMosquiDrawSlot(ref)
                        : activeBoulder != null ? readBoulderDrawSlot(ref)
                        : activeSpikes != null ? readSpikesDrawSlot(ref)
                        : activeAnton != null ? readAntonDrawSlot(ref)
                        : activeResults != null ? readResultsDrawSlot(ref)
                        : activeBigRing != null ? readBigRingDrawSlot(ref)
                        : activeSignpost != null ? readSignpostDrawSlot(ref)
                        : activeGoal != null ? readGoalDrawSlot(ref)
                        : activeMachine != null ? readMachineDrawSlot(ref)
                        : activeTunnelDoor != null ? readTunnelDoorDrawSlot(ref)
                        : activeMonitor != null ? readMonitorDrawSlot(ref) : readRingDrawSlot(ref);
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
                if (activeDrain != null) { writeDrainingOnScreen(ref, onScreen); return; }
                if (Objects.requireNonNull(ref).wholeIndex() == 0) { writePrimaryOnScreen(ref, onScreen); return; }
                if (Objects.requireNonNull(ref).wholeIndex() < 32) writeReservedOnScreen(ref, onScreen);
                else if (activeSpring != null) writeSpringOnScreen(ref, onScreen);
                else if (activeCollapse != null) writeCollapseOnScreen(ref, onScreen);
                else if (activePlatform != null) writePlatformOnScreen(ref, onScreen);
                else if (activeFloat != null) writeFloatOnScreen(ref, onScreen);
                else if (activePata != null) writePataOnScreen(ref, onScreen);
                else if (activeTamabboh != null || activeCollapse != null) writeTamabbohOnScreen(ref, onScreen);
                else if (activeMosqui != null) writeMosquiOnScreen(ref, onScreen);
                else if (activeBoulder != null) writeBoulderOnScreen(ref, onScreen);
                else if (activeSpikes != null) writeSpikesOnScreen(ref, onScreen);
                else if (activeAnton != null) writeAntonOnScreen(ref, onScreen);
                else if (activeResults != null) writeResultsOnScreen(ref, onScreen);
                else if (activeBigRing != null) writeBigRingOnScreen(ref, onScreen);
                else if (activeSignpost != null) writeSignpostOnScreen(ref, onScreen);
                else if (activeGoal != null) writeGoalOnScreen(ref, onScreen);
                else if (activeMachine != null) writeMachineOnScreen(ref, onScreen);
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeRing != null || ringPort || drawingRing || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activePrimary != null || activeReserved != invocation || !runningObjects || activeRing != null || ringPort || drawingRing || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || reservedPort || drawingReserved || queuePort)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || reservedPort || drawingReserved || queuePort || invocation.drawn
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || reservedPort || drawingReserved || queuePort || invocation.drawn || !invocation.drawExpected
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingReserved || reservedPort || queuePort)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingReserved || reservedPort || queuePort || onScreen)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || invocation == null || activeRing != invocation || invocation.pool != pool || pool != placement.pool()
                || invocation.whole != whole || whole < 32 || whole >= 128 || pool.state(whole).id() != 0x10
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Ring callback for this live physical pointer");
        verifyRingInvocation(invocation);
        return invocation;
    }

    private void verifyRingInvocation(RingInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeRing != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeReserved != null || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || reservedPort || drawingReserved
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || ringPort || drawingRing || queuePort || invocation.readRing != null || invocation.drawn)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || ringPort || drawingRing || queuePort || invocation.routine != 4 || invocation.readRing == null
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || ringPort || drawingRing || queuePort || invocation.routine != 4 || !invocation.rewardWritten
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || ringPort || drawingRing || queuePort || invocation.drawn
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingRing || ringPort || queuePort)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingRing || ringPort || queuePort || onScreen || invocation.screenCleared)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort || drawingRing
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeSpring != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeReserved != null || activeRing != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.solidResult != null || invocation.launched
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.solidResult == null
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || !invocation.launched || invocation.sounded || invocation.drawn
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || springPort || drawingSpring || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.launched != invocation.sounded)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingSpring || springPort || queuePort) throw new IllegalStateException("No actual Spring queue read");
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingSpring || springPort || queuePort || onScreen || invocation.screenCleared)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || activeTunnelDoor != null || activeMonitor != null
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !invocation.active || invocation.body != animal || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || activeTunnelDoor != null || activeMonitor != null
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


    /** Missing live knowledge stays a reached, addressed read boundary, never a zero time. */
    static final class UnsupportedSignpostRead extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final CdSignpost.ReadField field; private final int nativeAddress;
        private UnsupportedSignpostRead(CdSignpost.ReadField field, int nativeAddress) {
            super("Actual Signpost " + field + " requires known live byte $" + Integer.toHexString(nativeAddress));
            this.field = field; this.nativeAddress = nativeAddress;
        }
        CdSignpost.ReadField field() { return field; }
        int nativeAddress() { return nativeAddress; }
    }
    static final class UnsupportedSignpostBonus extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final CdSignpost.StoreField field;
        private UnsupportedSignpostBonus(CdSignpost.StoreField field) {
            super("Actual Signpost " + field + " has no complete existing bonus preimage"); this.field = field;
        }
        CdSignpost.StoreField field() { return field; }
    }
    enum ResultsReadBoundary { BONUS_PREIMAGE }
    static final class UnsupportedResultsRead extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final ResultsReadBoundary boundary;
        private UnsupportedResultsRead(ResultsReadBoundary boundary) {
            super("Actual Results has no complete existing value at " + boundary); this.boundary = boundary;
        }
        ResultsReadBoundary boundary() { return boundary; }
    }
    private enum ResultsCut {
        INVALID, PLC, HEAD, WAIT_ZONE, CAMERA, PLAYER_X, ROW_ZONE, ROW_GOOD, ROW_ACT,
        UPDATE, TIME, TIME_STORE, RING, RING_STORE, CHECK_TIME, CHECK_RING, SPECIAL,
        TALLY, KACHING, WARP, POINTS, DRAW, RETURN, ALLOCATION_STOP, PROGRAMME
    }
    /** Private actual producer/order proof; descriptive public callbacks and Effects cannot mint it. */
    private final class ResultsInvocation {
        private final CdResults body = results;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object stage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private final CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final SonicCdGameModule.LevelProgramState program = module.levelProgramState();
        private Values base = values;
        private List<SonicCdGameModule.PastSound> ledger = sounds;
        private final CdObjectDrawQueue.CameraWords camera;
        private final Object world; private final long generation; private final int whole;
        private final CdObjectState entry;
        private CdObjectPool.Snapshot expectedPool;
        private ResultsCut next;
        private int row, selected, rowRoot, edge, time, rings, points;
        private boolean drawn, screenCleared;
        private CdObjectState drawSource;
        private ResultsInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole); expectedPool = before.pool(); selected = whole;
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            camera = new CdObjectDrawQueue.CameraWords(scroll.state().cameraX(), scroll.state().cameraYWord());
            next = switch (entry.routine()) {
                case 0 -> ResultsCut.PLC;
                case 2 -> ResultsCut.HEAD;
                case 4 -> ResultsCut.DRAW;
                case 6 -> ResultsCut.UPDATE;
                case 8 -> ResultsCut.PROGRAMME;
                default -> ResultsCut.INVALID;
            };
            if (entry.routine() == 0) {
                expectResultsImage(this, entry.addByte(CdObjectState.ByteField.RESULTS_TIMER, -1));
                if (resultsSource(this).unsignedByte(CdObjectState.ByteField.RESULTS_TIMER) != 0) next = ResultsCut.RETURN;
            } else if (entry.routine() == 4) prepareResultsMove(this);
        }
    }
    private boolean resultsForeignScope() {
        return activeBigRing != null || bigRingPort || drawingBigRing || bigRingForeignScope();
    }
    private void runResults(int whole) {
        module.requirePastLoadingVisit(this);
        if (resultsForeignScope() || activeResults != null || resultsPort || drawingResults || queuePort
                || !runningObjects || servicing || loadingSaved || whole < 32 || whole >= 128 || values.phase() != BEGIN_LEVEL
                || placement.pool().state(whole).id() != CdResults.OBJECT_ID
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Results dispatch");
        var i = new ResultsInvocation(whole); activeResults = i;
        try {
            var result = results.run(whole); verifyResults(i); validateResultsImages(i);
            if (i.next != ResultsCut.RETURN || i.drawn != i.screenCleared || !result.state().equals(resultsSource(i)))
                throw new IllegalStateException("Results returned before its genuine conditional native tail");
        } catch (RuntimeException | Error original) { verifyResultsFailure(i, original); throw original; }
        finally { activeResults = null; }
    }
    private ResultsInvocation requireResults(int whole) {
        var i = activeResults;
        if (i == null || i.whole != whole || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual selected Results callback");
        verifyResults(i); return i;
    }
    private void verifyResults(ResultsInvocation i) {
        module.requirePastLoadingVisit(this); var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeResults != i || resultsForeignScope() || !runningObjects || servicing || loadingSaved
                || i.body != results || i.sourceFrame != frame || i.sourceAssets != assets || i.stage != frame.stageSource()
                || i.pool != placement.pool() || i.queue != drawQueue || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING || values.phase() != BEGIN_LEVEL
                || i.world != mode.getWorldSession() || i.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || i.base != values || i.ledger != sounds || i.program != module.levelProgramState()
                || !frame.captureActor().equals(i.actor) || !frame.captureShared().equals(i.shared)
                || !frame.state().equals(i.actor.state()) || !frame.environment().equals(i.shared.environment())
                || !presentation.samePastScroll(i.scroll) || !placement.status().equals(i.before.status()))
            throw new IllegalStateException("Results retained another World/Frame/queue/programme cut or changed a foreign owner");
        placement.validateRestore(i.before); i.pool.validateRestore(i.expectedPool);
    }
    private CdObjectState resultsSource(ResultsInvocation i) { return i.expectedPool.slots().get(i.whole - 32); }
    /** Verifier images only: this helper never publishes a gameplay store. */
    private void expectResultsImage(ResultsInvocation i, CdObjectState image) {
        i.expectedPool = CdObjectPool.overlay(i.expectedPool, List.of(image));
    }
    private void validateResultsImages(ResultsInvocation i) {
        var now = placement.capture();
        if (!now.pool().slots().equals(i.expectedPool.slots()) || !now.status().equals(i.before.status())
                || !sameBytes(now.savedFlags(), i.before.savedFlags()))
            throw new IllegalStateException("Results differs from its reached full96 dirty-row prefix or saved/cursor nonwrites");
    }
    private void validateResultsCut(ResultsInvocation i, CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        verifyResults(i); placement.validateRestore(Objects.requireNonNull(cut)); validateResultsImages(i);
        var now = placement.capture();
        if (!source.equals(resultsSource(i)) || !source.identity().equals(i.entry.identity())
                || !source.equals(i.pool.state(i.whole)) || !cut.pool().slots().equals(now.pool().slots())
                || !cut.status().equals(now.status()) || !sameBytes(cut.savedFlags(), now.savedFlags()))
            throw new IllegalStateException("Results callback lacks its fresh complete actual placement/source");
    }
    private void requireResultsSite(ResultsInvocation i, ResultsCut site, CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        if (resultsPort || drawingResults || queuePort || i.drawn || i.next != site)
            throw new IllegalStateException("Results callback lacks its actual once-only reached native position");
        validateResultsCut(i, cut, source);
    }
    private void prepareResultsMove(ResultsInvocation i) {
        var s = resultsSource(i);
        if (s.word(CdObjectState.WordField.RESULTS_TIMER_WORD) != 0) s = s.addWord(CdObjectState.WordField.RESULTS_TIMER_WORD, -1);
        int x = s.centreX(), target = s.word(CdObjectState.WordField.RESULTS_DEST_X);
        if (x != target) s = s.addWord(CdObjectState.WordField.X, (short) target < (short) x ? -8 : 8);
        else if (s.unsignedByte(CdObjectState.ByteField.MAP_FRAME) == 0) s = s.addByte(CdObjectState.ByteField.ROUTINE, 2);
        expectResultsImage(i, s); i.next = s.word(CdObjectState.WordField.RESULTS_TIMER_WORD) < 472 ? ResultsCut.DRAW : ResultsCut.RETURN;
    }
    private void beginResultsRows(ResultsInvocation i) {
        expectResultsImage(i, resultsSource(i).moveWord(CdObjectState.WordField.RESULTS_TIMER_WORD, 480));
        i.row = 0; i.selected = i.whole; prepareResultsRow(i);
    }
    private void prepareResultsRow(ResultsInvocation i) {
        var s = i.expectedPool.slots().get(i.selected - 32);
        expectResultsImage(i, s.moveWord(CdObjectState.WordField.RESULTS_TIMER_WORD, 480)
                .moveByte(CdObjectState.ByteField.ID, CdResults.OBJECT_ID).moveByte(CdObjectState.ByteField.ROUTINE, 4)
                .moveWord(CdObjectState.WordField.TILE, 0x83C4));
        i.next = ResultsCut.ROW_ZONE;
    }
    private void finishResultsRow(ResultsInvocation i) {
        if (++i.row == 3) { i.next = ResultsCut.RETURN; return; }
        i.selected = -1;
        for (var s : i.expectedPool.slots()) if (s.id() == 0) { i.selected = s.identity().wholeIndex(); break; }
        if (i.selected < 0) { i.next = ResultsCut.ALLOCATION_STOP; return; }
        prepareResultsRow(i);
    }
    private void prepareResultsRowData(ResultsInvocation i) {
        var data = assets.view(CdR11bAssets.Asset.RESULTS_INIT_DATA).bytes(); int at = i.row * 8;
        var s = i.expectedPool.slots().get(i.selected - 32)
                .moveWord(CdObjectState.WordField.RESULTS_SCREEN_Y_WORD, data.unsignedWord(at))
                .moveWord(CdObjectState.WordField.X, data.unsignedWord(at + 2))
                .moveWord(CdObjectState.WordField.RESULTS_DEST_X, data.unsignedWord(at + 4))
                .moveByte(CdObjectState.ByteField.MAP_FRAME, data.unsignedByte(at + 7));
        expectResultsImage(i, s);
        if (i.row == 2) i.next = ResultsCut.ROW_ACT; else finishResultsRow(i);
    }
    private void prepareResultsNoBonus(ResultsInvocation i) {
        var s = resultsSource(i).addWord(CdObjectState.WordField.RESULTS_TIMER_WORD, -1);
        int timer = s.word(CdObjectState.WordField.RESULTS_TIMER_WORD);
        if ((short) timer < 0) s = s.addByte(CdObjectState.ByteField.ROUTINE, 2);
        expectResultsImage(i, s); i.next = timer == 30 ? ResultsCut.SPECIAL : ResultsCut.DRAW;
    }
    private void prepareResultsHaveBonus(ResultsInvocation i) {
        var s = resultsSource(i);
        if (s.word(CdObjectState.WordField.RESULTS_TIMER_WORD) != 0) s = s.addWord(CdObjectState.WordField.RESULTS_TIMER_WORD, -1);
        expectResultsImage(i, s);
        // Native memory BTST#0 at offset$32 selects HIGH timer BYTE, not WORD parity.
        i.next = (s.unsignedByte(CdObjectState.ByteField.RESULTS_TIMER) & 1) == 0 ? ResultsCut.TALLY : ResultsCut.POINTS;
    }
    private CdLevelHud.BonusValues resultsBonus() {
        return values.globals().bonus().orElseThrow(() -> new UnsupportedResultsRead(ResultsReadBoundary.BONUS_PREIMAGE));
    }
    private int readResults(CdResults.ReadCall call) {
        Objects.requireNonNull(call); var i = requireResults(call.expectedSource().identity().wholeIndex());
        var site = i.next;
        boolean reached = switch (call.field()) {
            case PLC_HEAD_LONG -> site == ResultsCut.HEAD;
            case ZONE_ACT_WORD -> site == ResultsCut.WAIT_ZONE || site == ResultsCut.ROW_ZONE;
            case CAMERA_X_WORD -> site == ResultsCut.CAMERA;
            case PLAYER_X_WORD -> site == ResultsCut.PLAYER_X;
            case GOOD_FUTURE_BYTE -> site == ResultsCut.ROW_GOOD;
            case ACT_BYTE -> site == ResultsCut.ROW_ACT;
            case TIME_BONUS_WORD -> site == ResultsCut.TIME || site == ResultsCut.CHECK_TIME;
            case RING_BONUS_WORD -> site == ResultsCut.RING || site == ResultsCut.CHECK_RING;
            case SPECIAL_STAGE_BYTE -> site == ResultsCut.SPECIAL;
            case TIME_ATTACK_MODE_BYTE, TIME_STONES_BYTE, GOOD_FUTURE_FLAGS_BYTE -> false; // Behind the absent programme store.
        };
        if (!reached) throw new IllegalStateException("Results operand is not at its genuine source-conditional read");
        requireResultsSite(i, site, call.actualCut(), call.expectedSource());
        int value = switch (call.field()) {
            case PLC_HEAD_LONG -> (int) values.section().queue().headSource();
            case ZONE_ACT_WORD -> frame.environment().movement().gates().zoneAct();
            case CAMERA_X_WORD -> presentation.pastForegroundImage().cameraX();
            case PLAYER_X_WORD -> frame.state().motion().centreX16_16() >>> 16;
            case GOOD_FUTURE_BYTE -> values.globals().retainedDev().goodFuture();
            case ACT_BYTE -> frame.environment().movement().gates().zoneAct() & 255;
            case TIME_BONUS_WORD -> resultsBonus().timeBonusWord();
            case RING_BONUS_WORD -> resultsBonus().ringBonusWord();
            case SPECIAL_STAGE_BYTE -> i.program.specialStage();
            case TIME_ATTACK_MODE_BYTE, TIME_STONES_BYTE, GOOD_FUTURE_FLAGS_BYTE -> throw new IllegalStateException("Unreached programme operand");
        };
        resultsPort = true;
        try {
            verifyResults(i); validateResultsCut(i, call.actualCut(), call.expectedSource());
            switch (site) {
                case HEAD -> i.next = value == 0 ? ResultsCut.WAIT_ZONE : ResultsCut.RETURN;
                case WAIT_ZONE -> { if (value == 0x502) beginResultsRows(i); else i.next = ResultsCut.CAMERA; }
                case CAMERA -> { i.edge = (value + 336) & 0xFFFF; i.next = ResultsCut.PLAYER_X; }
                case PLAYER_X -> { if (i.edge < value) beginResultsRows(i); else i.next = ResultsCut.RETURN; }
                case ROW_ZONE -> {
                    var s = i.expectedPool.slots().get(i.selected - 32);
                    if (value == 0x502) s = s.moveWord(CdObjectState.WordField.TILE, 0x82F2);
                    i.rowRoot = assets.view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress() + (value == 0x502 ? 10 : 0);
                    expectResultsImage(i, s.moveLong(CdObjectState.LongField.MAPPINGS, i.rowRoot)); i.next = ResultsCut.ROW_GOOD;
                }
                case ROW_GOOD -> {
                    if (value != 0) expectResultsImage(i, i.expectedPool.slots().get(i.selected - 32).moveLong(CdObjectState.LongField.MAPPINGS, i.rowRoot + 20));
                    prepareResultsRowData(i);
                }
                case ROW_ACT -> {
                    expectResultsImage(i, i.expectedPool.slots().get(i.selected - 32).addByte(CdObjectState.ByteField.MAP_FRAME, value)); finishResultsRow(i);
                }
                case TIME -> { i.time = value; i.next = value == 0 ? ResultsCut.RING : ResultsCut.TIME_STORE; }
                case RING -> { i.rings = value;
                    if (value != 0) i.next = ResultsCut.RING_STORE;
                    else if (i.time == 0) prepareResultsNoBonus(i); else i.next = ResultsCut.CHECK_TIME; }
                case CHECK_TIME -> { if (value != 0) prepareResultsHaveBonus(i); else i.next = ResultsCut.CHECK_RING; }
                case CHECK_RING -> { if (value != 0) prepareResultsHaveBonus(i); else i.next = ResultsCut.KACHING; }
                case SPECIAL -> i.next = value == 0 ? ResultsCut.DRAW : ResultsCut.WARP;
                default -> throw new IllegalStateException("No reached Results read transition");
            }
            verifyResults(i); return value;
        } finally { resultsPort = false; }
    }
    private Values resultsValues(CdR11bSectionArt.State section, CdRingGlobals ring, LoadingGlobals globals) {
        var o = values;
        return new Values(o.phase(), section, o.cpuPalette(), o.fadePalette(), o.displayedPalette(), o.cpuSat(), o.displayedSat(),
                o.displayedHScroll(), o.displayedVScrollLongBits(), o.foregroundPlane(), ring, o.level(), globals, o.hudObjects(),
                o.fadeProgress(), o.calls(), o.loop());
    }
    private void storeResultsBonus(CdResults.BonusStoreCall call) {
        Objects.requireNonNull(call); var i = requireResults(call.expectedSource().identity().wholeIndex());
        var site = switch (call.field()) {
            case UPDATE_HUD_BONUS_BYTE -> ResultsCut.UPDATE;
            case TIME_BONUS_WORD -> ResultsCut.TIME_STORE;
            case RING_BONUS_WORD -> ResultsCut.RING_STORE;
        };
        requireResultsSite(i, site, call.actualCut(), call.expectedSource());
        int operand = call.field() == CdResults.BonusField.UPDATE_HUD_BONUS_BYTE ? 1
                : ((call.field() == CdResults.BonusField.TIME_BONUS_WORD ? i.time : i.rings) - 100) & 0xFFFF;
        if (call.value() != operand) throw new IllegalStateException("Results bonus store differs from its actual native subtract/update operand");
        var b = resultsBonus(); var g = values.globals();
        var readyBonus = new CdLevelHud.BonusValues(call.field() == CdResults.BonusField.TIME_BONUS_WORD ? operand : b.timeBonusWord(),
                call.field() == CdResults.BonusField.RING_BONUS_WORD ? operand : b.ringBonusWord(),
                call.field() == CdResults.BonusField.UPDATE_HUD_BONUS_BYTE ? 1 : b.updateHudBonusByte());
        var ready = resultsValues(values.section(), values.ring(), new LoadingGlobals(g.live(), g.contact(), g.score(), g.drownTimerWord(),
                g.tail(), Optional.of(readyBonus), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev()));
        resultsPort = true;
        try {
            verifyResults(i); validateResultsCut(i, call.actualCut(), call.expectedSource()); values = ready; i.base = ready;
            if (site == ResultsCut.UPDATE) i.next = ResultsCut.TIME;
            else { i.points += 10; i.next = site == ResultsCut.TIME_STORE ? ResultsCut.RING : ResultsCut.CHECK_TIME; }
            verifyResults(i); validateResultsCut(i, call.actualCut(), call.expectedSource());
        } finally { resultsPort = false; }
    }
    private void addResultsPoints(CdResults.PointsCall call) {
        Objects.requireNonNull(call); var i = requireResults(call.expectedSource().identity().wholeIndex());
        requireResultsSite(i, ResultsCut.POINTS, call.actualCut(), call.expectedSource());
        if (call.points() != i.points || i.points != 10 && i.points != 20) throw new IllegalStateException("Results AddPoints lacks its actual nonzero bonus subtracts");
        var old = values.ring(); var g = values.globals(); var beforeScore = g.score();
        var result = CdPlayerTouchResponse.addPoints(new CdPlayerTouchResponse.WorldGlobals(old.rings(), old.lives(), old.updateHudLives(),
                beforeScore.score(), beforeScore.nextLifeScore(), beforeScore.updateHudScore()), call.points());
        var w = result.world();
        var readyRing = new CdRingGlobals(w.rings(), w.lives(), old.livesFlags(), old.updateHudRings(), w.updateHudLives(),
                old.timeStopTimer(), old.ringAnimationTimer(), old.ringAnimationFrame());
        var ready = resultsValues(values.section(), readyRing, new LoadingGlobals(g.live(), g.contact(),
                new CdOpeningObjectRuntime.ScoreGlobals(w.score(), w.nextLifeScore(), w.updateHudScore()), g.drownTimerWord(),
                g.tail(), g.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev()));
        var ledger = new ArrayList<>(sounds);
        result.command().ifPresent(command -> ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactSubCpuCommand(command))));
        var readySounds = List.copyOf(ledger); resultsPort = true;
        try {
            verifyResults(i); validateResultsCut(i, call.actualCut(), call.expectedSource());
            values = ready; sounds = readySounds; i.base = ready; i.ledger = readySounds; i.next = ResultsCut.DRAW;
            verifyResults(i); validateResultsCut(i, call.actualCut(), call.expectedSource());
        } finally { resultsPort = false; }
    }
    private void loadResultsPlc(CdResults.PlcCall call) {
        Objects.requireNonNull(call); var i = requireResults(call.expectedSource().identity().wholeIndex());
        requireResultsSite(i, ResultsCut.PLC, call.actualCut(), call.expectedSource());
        if (call.nativeId() != 16) throw new IllegalStateException("Results requested another actual PLC site");
        CdR11bSectionArt.State section; CdR11bSectionArt.SectionArtException stopped = null;
        try { section = art.appendList(values.section(), 16); }
        catch (CdR11bSectionArt.SectionArtException failure) { section = failure.state(); stopped = failure; }
        var ready = resultsValues(section, values.ring(), values.globals()); resultsPort = true;
        try {
            verifyResults(i); validateResultsCut(i, call.actualCut(), call.expectedSource()); values = ready; i.base = ready;
            verifyResults(i); validateResultsCut(i, call.actualCut(), call.expectedSource());
            if (stopped == null) { expectResultsImage(i, resultsSource(i).addByte(CdObjectState.ByteField.ROUTINE, 2)); i.next = ResultsCut.HEAD; }
        } finally { resultsPort = false; }
        if (stopped != null) throw stopped; // Publish the real queue prefix and preserve its exact original failure.
    }
    private void soundResults(CdResults.SoundCall call) {
        Objects.requireNonNull(call); var i = requireResults(call.expectedSource().identity().wholeIndex());
        var site = switch (call.sound()) { case FM_TALLY -> ResultsCut.TALLY; case DRIVER_KACHING -> ResultsCut.KACHING; case FM_SSWARP -> ResultsCut.WARP; };
        requireResultsSite(i, site, call.actualCut(), call.expectedSource());
        var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastResultsSound(call.sound()));
        var ready = List.copyOf(ledger); resultsPort = true;
        try {
            verifyResults(i); validateResultsCut(i, call.actualCut(), call.expectedSource()); sounds = ready; i.ledger = ready;
            if (site == ResultsCut.KACHING) {
                var s = resultsSource(i);
                if (s.word(CdObjectState.WordField.RESULTS_TIMER_WORD) < 45) expectResultsImage(i, s.moveWord(CdObjectState.WordField.RESULTS_TIMER_WORD, 45));
            }
            i.next = site == ResultsCut.WARP ? ResultsCut.DRAW : ResultsCut.POINTS; verifyResults(i);
        } finally { resultsPort = false; }
    }
    private void drawResults(CdResults.DrawCall call) {
        Objects.requireNonNull(call); var i = requireResults(call.expectedSource().identity().wholeIndex());
        requireResultsSite(i, ResultsCut.DRAW, call.actualCut(), call.expectedSource());
        var source = call.expectedSource(); i.drawSource = source; i.drawn = true; drawingResults = true;
        try {
            // DrawObject uses world Y for culling only when dirty flags request a world-camera mode.
            // Screen mode uses the distinct WORD oYScr($0A) at actual DrawObjects dereference below.
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole), source.spriteFlags(),
                    source.centreX(), source.centreY(), source.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    source.unsignedByte(CdObjectState.ByteField.WIDTH), source.unsignedByte(CdObjectState.ByteField.PRIORITY)), i.camera);
            verifyResults(i); validateResultsImages(i);
            if (!i.screenCleared) throw new IllegalStateException("Results Draw omitted its native source BCLR7");
            i.next = ResultsCut.RETURN;
        } finally { drawingResults = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readResultsDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var i = requireResults(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingResults || resultsPort || queuePort || !i.drawn || !i.screenCleared)
            throw new IllegalStateException("No actual Results collecting queue dereference");
        validateResultsImages(i); var before = placement.capture(); queuePort = true;
        try {
            var s = i.pool.state(i.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(s.id(), s.spriteFlags(), s.centreX(), s.centreY(),
                    s.word(CdObjectState.WordField.RESULTS_SCREEN_Y_WORD), s.longBits(CdObjectState.LongField.MAPPINGS),
                    s.unsignedByte(CdObjectState.ByteField.MAP_FRAME), s.word(CdObjectState.WordField.TILE));
            verifyResults(i); validateResultsImages(i);
            if (!before.pool().slots().equals(placement.capture().pool().slots())) throw new IllegalStateException("Results queue read changed its full placement");
            return live;
        } finally { queuePort = false; }
    }
    private void writeResultsOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var i = requireResults(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingResults || resultsPort || queuePort || !i.drawn || i.screenCleared || onScreen || !i.drawSource.equals(resultsSource(i)))
            throw new IllegalStateException("Only the actual Results Draw may clear its source bit7 once");
        validateResultsImages(i); var next = resultsSource(i).moveByte(CdObjectState.ByteField.SPRITE_FLAGS, resultsSource(i).spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(i.expectedPool, List.of(next)); i.pool.validateRestore(ready); queuePort = true;
        try {
            verifyResults(i); i.pool.write(next); i.expectedPool = ready; i.screenCleared = true; verifyResults(i); validateResultsImages(i);
        } finally { queuePort = false; }
    }
    private void verifyResultsFailure(ResultsInvocation i, Throwable original) {
        try {
            verifyResults(i); validateResultsImages(i);
            if (original instanceof CdResults.UnsupportedResults stop) {
                if (stop.wholeIndex() != i.whole) throw new IllegalStateException("Results stopped at another actual source");
                boolean exact = switch (stop.boundary()) {
                    case ROUTINE_DISPATCH -> i.next == ResultsCut.INVALID && stop.value() == i.entry.routine();
                    case ALLOCATION_ADDRESS -> i.next == ResultsCut.ALLOCATION_STOP && stop.value() == CdObjectPool.END_WHOLE_INDEX;
                    case PROGRAMME_STORE_OWNER -> i.next == ResultsCut.PROGRAMME && stop.value() == CdResults.ProgrammeField.LEVEL_RESTART_WORD.ordinal();
                    default -> false; // Actual authenticated133 views/owned callbacks cannot produce another component boundary.
                };
                if (!exact) throw new IllegalStateException("Results failed at a different unowned instruction than its private actual site");
            }
        } catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    private enum BigRingCut {
        RINGS, STONES, ATTACK, XR, X, YR, Y, SPECIAL, CAMERA_TOUCH, PLAYER_X, LOCK, HOLD, XV, GV,
        SCROLL, FM, CAMERA_DESPAWN, PERIOD_DESPAWN, COPIED_PERIOD, DIRECTION, TAIL, DRAW, RETURN
    }
    /** The actual producer scope, never manufactured by the component's descriptive public records. */
    private final class BigRingInvocation {
        private final CdBigRing body = bigRing;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object stage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final Values base = values;
        private List<SonicCdGameModule.PastSound> ledger = sounds;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private SonicCdGameModule.LevelProgramState program = module.pastBigRingProgramImage(SonicCdPastLoadingVisit.this);
        private final CdObjectDrawQueue.CameraWords camera;
        private final Object world; private final long generation; private final int whole;
        private final CdObjectState entry; private CdObjectState source, drawSource;
        private final boolean flash;
        private BigRingCut next;
        private boolean lowRings, remove, animate, deleted, drawn, screenCleared;
        private int width, height, cameraTouch, copiedPeriod, direction, child = -1;
        private CdObjectState childImage;
        private BigRingInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole); source = entry; flash = entry.subtype() != 0;
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            camera = new CdObjectDrawQueue.CameraWords(scroll.state().cameraX(), scroll.state().cameraYWord());
            next = flash ? BigRingCut.TAIL : BigRingCut.RINGS;
        }
    }
    private boolean bigRingForeignScope() {
        return activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal
                || activeMachine != null || machinePort || drawingMachine || machineForeignScope();
    }
    private void runBigRing(int whole) {
        module.requirePastLoadingVisit(this);
        if (bigRingForeignScope() || activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || queuePort
                || !runningObjects || servicing || loadingSaved || whole < 32 || whole >= 128 || values.phase() != BEGIN_LEVEL
                || placement.pool().state(whole).id() != CdBigRing.OBJECT_ID
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Big Ring dispatch");
        var invocation = new BigRingInvocation(whole); activeBigRing = invocation;
        try {
            var result = bigRing.run(whole); verifyBigRing(invocation);
            prepareBigRingTail(invocation, null);
            boolean draw = invocation.flash || !invocation.lowRings && invocation.source.routine() != 4;
            if (draw != invocation.drawn || invocation.drawn && (!invocation.screenCleared || invocation.next != BigRingCut.RETURN)
                    || !invocation.drawn && invocation.next != BigRingCut.TAIL)
                throw new IllegalStateException("Big Ring returned before its genuine conditional tail");
            validateBigRingImages(invocation, true);
            if (!result.state().equals(invocation.pool.state(whole))) throw new IllegalStateException("Big Ring result lost its actual source");
        } catch (RuntimeException | Error original) { verifyBigRingFailure(invocation, original); throw original; }
        finally { activeBigRing = null; }
    }
    private BigRingInvocation requireBigRing(int whole) {
        var invocation = activeBigRing;
        if (invocation == null || invocation.whole != whole || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual selected Big Ring callback");
        verifyBigRing(invocation); return invocation;
    }
    private void verifyBigRing(BigRingInvocation invocation) {
        module.requirePastLoadingVisit(this); var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeBigRing != invocation || bigRingForeignScope() || !runningObjects || servicing || loadingSaved
                || invocation.body != bigRing || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.stage != frame.stageSource() || invocation.pool != placement.pool() || invocation.queue != drawQueue
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING || values.phase() != BEGIN_LEVEL
                || invocation.world != mode.getWorldSession() || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.base != values || invocation.ledger != sounds || module.pastBigRingProgramImage(this) != invocation.program
                || !placement.status().equals(invocation.before.status()) || !frame.captureActor().equals(invocation.actor)
                || !frame.captureShared().equals(invocation.shared) || !frame.state().equals(invocation.actor.state())
                || !frame.environment().equals(invocation.shared.environment()) || !presentation.samePastScroll(invocation.scroll))
            throw new IllegalStateException("Big Ring changed another owner or retained a stale World/Frame/programme cut");
        invocation.pool.validateRestore(invocation.before.pool());
    }
    private void validateBigRingImages(BigRingInvocation invocation, boolean tail) {
        if (!invocation.source.equals(invocation.pool.state(invocation.whole))) throw new IllegalStateException("Big Ring source differs from its exact full64 prefix");
        for (var old : invocation.before.pool().slots()) {
            int whole = old.identity().wholeIndex(); if (whole == invocation.whole) continue;
            var expected = tail && whole == invocation.child ? invocation.childImage : old;
            if (!expected.equals(invocation.pool.state(whole))) throw new IllegalStateException("Big Ring changed an unrelated image or its dirty first-free flash bytes");
        }
        var bank = invocation.before.savedFlags().copy();
        if (tail && invocation.lowRings && invocation.remove && invocation.entry.savedFlagsId() != 0) {
            int period = invocation.copiedPeriod & 0x7F;
            if ((invocation.copiedPeriod & 0x80) != 0) period = Math.max(0, Math.min((short) ((period - (byte) invocation.direction) & 0xFFFF), 2));
            int at = 2 + (short) ((3 * invocation.entry.savedFlagsId() + period) & 0xFFFF);
            if (at < 2 || at >= bank.length) throw new IllegalStateException("Big Ring has no represented saved flag address");
            bank[at] = (byte) (bank[at] & 0x7F);
        }
        var now = placement.capture();
        if (!Arrays.equals(bank, now.savedFlags().copy()) || !now.status().equals(invocation.before.status()))
            throw new IllegalStateException("Big Ring changed unrelated saved bytes or spawner cursors");
    }
    private void validateBigRingCut(BigRingInvocation invocation, CdObjectPlacementRuntime.Snapshot cut, CdObjectState source, boolean tail) {
        verifyBigRing(invocation); placement.validateRestore(Objects.requireNonNull(cut)); validateBigRingImages(invocation, tail);
        var now = placement.capture();
        if (!source.equals(invocation.source) || !source.identity().equals(invocation.entry.identity())
                || !cut.pool().slots().equals(now.pool().slots()) || !cut.status().equals(now.status()) || !sameBytes(cut.savedFlags(), now.savedFlags()))
            throw new IllegalStateException("Big Ring port lacks its genuine fresh full-placement/source cut");
    }
    private int readBigRing(CdBigRing.ReadCall call) {
        Objects.requireNonNull(call); var i = requireBigRing(call.expectedSource().identity().wholeIndex());
        if (bigRingPort || drawingBigRing || queuePort || i.drawn || !bigRingReadAt(i.next, call.field()))
            throw new IllegalStateException("Big Ring read lacks its native conditional position");
        validateBigRingCut(i, call.actualCut(), call.expectedSource(), false);
        int value = switch (call.field()) {
            case RINGS_WORD -> values.ring().rings();
            case TIME_STONES_BYTE -> module.pastBigRingTimeStones(this, i.program);
            case TIME_ATTACK_BYTE -> frame.environment().globals().timeAttackMode();
            case PLAYER_X_RADIUS_BYTE -> frame.state().motion().xRadius();
            case PLAYER_Y_RADIUS_BYTE -> frame.state().motion().yRadius();
            case PLAYER_X_WORD -> frame.state().motion().centreX16_16() >>> 16;
            case PLAYER_Y_WORD -> frame.state().motion().centreY16_16() >>> 16;
            case CAMERA_X_WORD -> presentation.pastForegroundImage().cameraX();
            case TIME_ZONE_BYTE -> frame.environment().movement().gates().timeZone();
            case TIME_WARP_DIRECTION_BYTE -> frame.environment().movement().gates().timeWarpDirection();
        };
        bigRingPort = true;
        try {
            verifyBigRing(i); validateBigRingCut(i, call.actualCut(), call.expectedSource(), false);
            switch (call.field()) {
                case RINGS_WORD -> {
                    i.lowRings = value < 50;
                    if (i.lowRings) i.next = i.entry.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) >= 0 ? BigRingCut.CAMERA_DESPAWN : BigRingCut.PERIOD_DESPAWN;
                    else i.next = switch (i.entry.routine()) { case 0 -> BigRingCut.STONES; case 2 -> BigRingCut.XR; default -> BigRingCut.TAIL; };
                    if (!i.lowRings && i.entry.routine() == 4) i.animate = true;
                }
                case TIME_STONES_BYTE -> { i.deleted = value == 0x7F; i.next = i.deleted ? BigRingCut.TAIL : BigRingCut.ATTACK; }
                case TIME_ATTACK_BYTE -> {
                    i.deleted = value != 0; i.next = i.deleted ? BigRingCut.TAIL : BigRingCut.XR;
                    if (!i.deleted) i.source = i.source.addByte(CdObjectState.ByteField.ROUTINE, 2)
                            .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, i.source.spriteFlags() | 4).moveWord(CdObjectState.WordField.TILE, 0x2488)
                            .moveLong(CdObjectState.LongField.MAPPINGS, bigRing.assets().ringMappingsAddress())
                            .moveByte(CdObjectState.ByteField.X_RADIUS, 32).moveByte(CdObjectState.ByteField.WIDTH, 32).moveByte(CdObjectState.ByteField.Y_RADIUS, 32);
                }
                case PLAYER_X_RADIUS_BYTE -> { i.width = ((byte) value + 16) & 0xFFFF; i.next = BigRingCut.X; }
                case PLAYER_X_WORD -> { int dx = (value - i.source.centreX() + i.width) & 0xFFFF;
                    i.next = (short) dx < 0 || dx >= ((2 * i.width) & 0xFFFF) ? BigRingCut.TAIL : BigRingCut.YR;
                    if (i.next == BigRingCut.TAIL) i.animate = true; }
                case PLAYER_Y_RADIUS_BYTE -> { i.height = ((byte) value + 32) & 0xFFFF; i.next = BigRingCut.Y; }
                case PLAYER_Y_WORD -> { int dy = (value - i.source.centreY() + i.height) & 0xFFFF;
                    i.next = (short) dy < 0 || dy >= ((2 * i.height) & 0xFFFF) ? BigRingCut.TAIL : BigRingCut.SPECIAL;
                    if (i.next == BigRingCut.TAIL) i.animate = true; }
                case CAMERA_X_WORD -> {
                    if (i.next == BigRingCut.CAMERA_TOUCH) { i.cameraTouch = value; i.next = BigRingCut.PLAYER_X; }
                    else { int distance = ((i.entry.centreX() & 0xFF80) - (((value - 0x80) & 0xFFFF) & 0xFF80)) & 0xFFFF;
                        i.remove = distance > 0x280; i.next = i.remove ? bigRingRemovalCut(i) : BigRingCut.PERIOD_DESPAWN; }
                }
                case TIME_ZONE_BYTE -> {
                    if (i.next == BigRingCut.PERIOD_DESPAWN) { i.remove = (value & 0x80) != 0; i.next = i.remove ? bigRingRemovalCut(i) : BigRingCut.TAIL; }
                    else { i.copiedPeriod = value; i.next = (value & 0x80) != 0 ? BigRingCut.DIRECTION : BigRingCut.TAIL; }
                }
                case TIME_WARP_DIRECTION_BYTE -> { i.direction = value; i.next = BigRingCut.TAIL; }
            }
            verifyBigRing(i); return value;
        } finally { bigRingPort = false; }
    }
    private static BigRingCut bigRingRemovalCut(BigRingInvocation i) { return i.entry.savedFlagsId() == 0 ? BigRingCut.TAIL : BigRingCut.COPIED_PERIOD; }
    private static boolean bigRingReadAt(BigRingCut cut, CdBigRing.ReadField field) {
        return switch (field) {
            case RINGS_WORD -> cut == BigRingCut.RINGS;
            case TIME_STONES_BYTE -> cut == BigRingCut.STONES;
            case TIME_ATTACK_BYTE -> cut == BigRingCut.ATTACK;
            case PLAYER_X_RADIUS_BYTE -> cut == BigRingCut.XR;
            case PLAYER_X_WORD -> cut == BigRingCut.X;
            case PLAYER_Y_RADIUS_BYTE -> cut == BigRingCut.YR;
            case PLAYER_Y_WORD -> cut == BigRingCut.Y;
            case CAMERA_X_WORD -> cut == BigRingCut.CAMERA_TOUCH || cut == BigRingCut.CAMERA_DESPAWN;
            case TIME_ZONE_BYTE -> cut == BigRingCut.PERIOD_DESPAWN || cut == BigRingCut.COPIED_PERIOD;
            case TIME_WARP_DIRECTION_BYTE -> cut == BigRingCut.DIRECTION;
        };
    }
    private void requireBigRingStore(BigRingInvocation i, BigRingCut cut, CdObjectPlacementRuntime.Snapshot actual, CdObjectState source) {
        if (bigRingPort || drawingBigRing || queuePort || i.drawn || i.next != cut) throw new IllegalStateException("Big Ring store lacks its once-only native order");
        validateBigRingCut(i, actual, source, false);
    }
    /** Module's programme CAS is reachable only inside this actual once-only private native store site. */
    void requireBigRingProgramPublication(SonicCdGameModule.LevelProgramState expected) {
        var i = activeBigRing;
        if (i == null || i.program != expected || i.next != BigRingCut.SPECIAL || drawingBigRing || queuePort)
            throw new IllegalStateException("No genuine Big Ring specialStage source transaction");
        verifyBigRing(i);
    }
    private void storeBigRingSpecialStage(CdBigRing.SpecialStageCall call) {
        Objects.requireNonNull(call); var i = requireBigRing(call.expectedSource().identity().wholeIndex());
        requireBigRingStore(i, BigRingCut.SPECIAL, call.actualCut(), call.expectedSource());
        var ready = module.preparePastBigRingSpecialStage(this, frame, i.program);
        var source = i.source.addByte(CdObjectState.ByteField.ROUTINE, 2); bigRingPort = true;
        try {
            verifyBigRing(i); validateBigRingCut(i, call.actualCut(), call.expectedSource(), false);
            module.validatePreparedPastBigRingSpecialStage(ready); module.applyPreparedPastBigRingSpecialStage(ready);
            i.program = ready.result(); i.source = source; i.next = BigRingCut.CAMERA_TOUCH; verifyBigRing(i);
        } finally { bigRingPort = false; }
    }
    private void storeBigRingPlayer(CdBigRing.PlayerStore call) {
        Objects.requireNonNull(call); var i = requireBigRing(call.expectedSource().identity().wholeIndex());
        var cut = switch (call) {
            case CdBigRing.PlayerXWord x -> BigRingCut.PLAYER_X;
            case CdBigRing.ControlLockBit0 x -> BigRingCut.LOCK;
            case CdBigRing.ControlHold0808 x -> BigRingCut.HOLD;
            case CdBigRing.XVelocityZero x -> BigRingCut.XV;
            case CdBigRing.GroundVelocityZero x -> BigRingCut.GV;
        };
        requireBigRingStore(i, cut, call.actualCut(), call.expectedSource());
        if (call instanceof CdBigRing.PlayerXWord x && x.value() != ((i.cameraTouch + 336) & 0xFFFF))
            throw new IllegalStateException("Big Ring forced X differs from its actual camera plus native336");
        var ready = frame.prepareObjectBigRingPlayerStore(i.actor.state(), i.shared.environment(), call);
        var actor = new CdPlayerFrameRuntime.ActorState(i.actor.owner(), ready.finalState(), i.actor.display());
        var shared = new CdPlayerFrameRuntime.SharedState(i.shared.owner(), ready.finalEnvironment(), i.shared.artState(), i.shared.paused(), i.shared.vIntClock());
        bigRingPort = true;
        try {
            verifyBigRing(i); validateBigRingCut(i, call.actualCut(), call.expectedSource(), false);
            frame.validatePreparedObjectSolidPublication(ready); frame.applyPreparedObjectSolidPublication(ready);
            i.actor = actor; i.shared = shared;
            i.next = switch (cut) { case PLAYER_X -> BigRingCut.LOCK; case LOCK -> BigRingCut.HOLD; case HOLD -> BigRingCut.XV;
                case XV -> BigRingCut.GV; case GV -> BigRingCut.SCROLL; default -> throw new IllegalStateException("Big Ring player cursor"); };
            verifyBigRing(i);
        } finally { bigRingPort = false; }
    }
    private void storeBigRingScrollLock(CdBigRing.ScrollLockHighByteOne call) {
        Objects.requireNonNull(call); var i = requireBigRing(call.expectedSource().identity().wholeIndex());
        requireBigRingStore(i, BigRingCut.SCROLL, call.actualCut(), call.expectedSource());
        var ready = presentation.preparePastBigRingScrollLockHighByteOne(frame, i.scroll); bigRingPort = true;
        try {
            verifyBigRing(i); validateBigRingCut(i, call.actualCut(), call.expectedSource(), false);
            presentation.validatePreparedPastGoalPostDestLeftBound(ready); presentation.applyPreparedPastGoalPostDestLeftBound(ready);
            i.scroll = ready.result(); i.next = BigRingCut.FM; verifyBigRing(i);
        } finally { bigRingPort = false; }
    }
    private void soundBigRing(CdBigRing.SoundCall call) {
        Objects.requireNonNull(call); var i = requireBigRing(call.expectedSource().identity().wholeIndex());
        requireBigRingStore(i, BigRingCut.FM, call.actualCut(), call.expectedSource());
        var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastBigRingFm()); var ready = List.copyOf(ledger);
        int first = -1; CdObjectState child = null;
        for (var old : call.actualCut().pool().slots()) if (old.id() == 0) {
            first = old.identity().wholeIndex(); child = old.moveByte(CdObjectState.ByteField.ID, CdBigRing.OBJECT_ID)
                    .moveWord(CdObjectState.WordField.X, i.source.centreX()).moveWord(CdObjectState.WordField.Y, i.source.centreY())
                    .moveByte(CdObjectState.ByteField.SUBTYPE, 1); break;
        }
        bigRingPort = true;
        try {
            verifyBigRing(i); validateBigRingCut(i, call.actualCut(), call.expectedSource(), false);
            sounds = ready; i.ledger = ready; i.child = first; i.childImage = child;
            i.next = first < 0 ? BigRingCut.XR : BigRingCut.TAIL; i.animate = first >= 0; verifyBigRing(i);
        } finally { bigRingPort = false; }
    }
    /** Independent raw-script verification only; never invokes the shared successful animator again. */
    private CdObjectState bigRingAnimationImage(CdObjectState original, boolean flash, CdBigRing.UnsupportedBoundary stop) {
        var out = original; int id = original.unsignedByte(CdObjectState.ByteField.ANIMATION);
        if (id != original.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION)) out = out
                .moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, id).moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0)
                .moveByte(CdObjectState.ByteField.ANIMATION_TIME, 0);
        out = out.addByte(CdObjectState.ByteField.ANIMATION_TIME, -1);
        if (out.signedByte(CdObjectState.ByteField.ANIMATION_TIME) >= 0 || stop == CdBigRing.UnsupportedBoundary.ANIMATION_DIRECTORY_READ
                || stop == CdBigRing.UnsupportedBoundary.ANIMATION_SPEED_READ) return out;
        if (id != 0) throw new IllegalStateException("Big Ring successful animation has no actual directory row");
        var bytes = flash ? bigRing.assets().flashAnimations() : bigRing.assets().ringAnimations();
        int script = (short) bytes.unsignedWord(0), index = out.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME);
        out = out.moveByte(CdObjectState.ByteField.ANIMATION_TIME, bytes.unsignedByte(script));
        if (stop != null) return out;
        int selected = bytes.unsignedByte(script + 1 + index);
        if (selected == 0xFF) { index = 0; selected = bytes.unsignedByte(script + 1); }
        if (selected == 0xFC) return out.addByte(CdObjectState.ByteField.ROUTINE, 2);
        if (selected >= 0x80) throw new IllegalStateException("Big Ring raw script selected an undeclared command");
        int rotated = ((selected << 3) | (selected >>> 5)) & 255;
        return out.moveByte(CdObjectState.ByteField.MAP_FRAME, selected & 31).moveByte(CdObjectState.ByteField.ANIMATION_FRAME, (index + 1) & 255)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, (out.spriteFlags() & 0xFC) | ((rotated ^ out.flags()) & 3));
    }
    private void prepareBigRingTail(BigRingInvocation i, CdBigRing.UnsupportedBoundary stop) {
        if (i.drawn || i.next != BigRingCut.TAIL) return;
        if (i.flash) {
            switch (i.entry.routine()) {
                case 0 -> { i.source = i.entry.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, i.entry.spriteFlags() | 4)
                        .addByte(CdObjectState.ByteField.ROUTINE, 2).moveWord(CdObjectState.WordField.TILE, 0x03EF)
                        .moveLong(CdObjectState.LongField.MAPPINGS, bigRing.assets().flashMappingsAddress()); i.animate = true; }
                case 2 -> i.animate = true;
                case 4 -> i.deleted = true;
                default -> { return; }
            }
        }
        if (i.deleted || i.lowRings && i.remove) {
            var cleared = i.pool.state(i.whole);
            if (!cleared.identity().equals(i.entry.identity()) || !Arrays.equals(new byte[CdObjectState.BYTE_SIZE], cleared.copyBytes()))
                throw new IllegalStateException("Big Ring DeleteObject must clear all64 actual source bytes");
            i.source = cleared;
        }
        else if (i.animate) i.source = bigRingAnimationImage(i.source, i.flash, stop);
        i.animate = false;
    }
    private void drawBigRing(CdBigRing.DrawCall call) {
        Objects.requireNonNull(call); var i = requireBigRing(call.expectedSource().identity().wholeIndex());
        if (bigRingPort || drawingBigRing || queuePort || i.drawn || i.lowRings || i.next != BigRingCut.TAIL)
            throw new IllegalStateException("Big Ring Draw lacks its native conditional tail");
        prepareBigRingTail(i, null);
        if (!i.flash && i.source.routine() == 4) throw new IllegalStateException("Parent routine4 suppresses Draw");
        validateBigRingCut(i, call.actualCut(), call.expectedSource(), true);
        i.drawSource = i.source; i.drawn = true; i.next = BigRingCut.DRAW; drawingBigRing = true;
        try {
            var source = i.source;
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(i.whole), source.spriteFlags(),
                    source.centreX(), source.centreY(), source.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    source.unsignedByte(CdObjectState.ByteField.WIDTH), source.unsignedByte(CdObjectState.ByteField.PRIORITY)), i.camera);
            verifyBigRing(i); if (!i.screenCleared) throw new IllegalStateException("Big Ring Draw omitted its genuine BCLR7"); i.next = BigRingCut.RETURN;
        } finally { drawingBigRing = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readBigRingDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var i = requireBigRing(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingBigRing || bigRingPort || queuePort || !i.drawn) throw new IllegalStateException("No actual Big Ring queue read");
        validateBigRingImages(i, true); queuePort = true;
        try {
            var s = i.pool.state(i.whole); var live = new CdObjectDrawQueue.LiveDrawSlot(s.id(), s.spriteFlags(), s.centreX(), s.centreY(),
                    s.word(CdObjectState.WordField.X_FRACTION), s.longBits(CdObjectState.LongField.MAPPINGS),
                    s.unsignedByte(CdObjectState.ByteField.MAP_FRAME), s.word(CdObjectState.WordField.TILE));
            verifyBigRing(i); validateBigRingImages(i, true); return live;
        } finally { queuePort = false; }
    }
    private void writeBigRingOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var i = requireBigRing(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingBigRing || bigRingPort || queuePort || !i.drawn || i.screenCleared || onScreen || !i.source.equals(i.drawSource))
            throw new IllegalStateException("Only actual Big Ring Draw clears bit7 once");
        validateBigRingImages(i, true); var next = i.source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, i.source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(placement.capture().pool(), List.of(next)); i.pool.validateRestore(ready); queuePort = true;
        try { verifyBigRing(i); i.pool.write(next); i.source = next; i.screenCleared = true; verifyBigRing(i); validateBigRingImages(i, true); }
        finally { queuePort = false; }
    }
    private void verifyBigRingFailure(BigRingInvocation i, Throwable original) {
        try {
            verifyBigRing(i);
            if (original instanceof CdBigRing.UnsupportedBigRing stop) {
                if (stop.wholeIndex() != i.whole) throw new IllegalStateException("Big Ring stopped at another source");
                switch (stop.boundary()) {
                    case ANIMATION_DIRECTORY_READ, ANIMATION_SPEED_READ, ANIMATION_FRAME_READ, ANIMATION_OPERAND_READ -> prepareBigRingTail(i, stop.boundary());
                    case SAVED_FLAGS_ADDRESS -> { validateBigRingImages(i, false); return; }
                    default -> { }
                }
            }
            validateBigRingImages(i, i.next == BigRingCut.TAIL || i.drawn);
        } catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    private enum SignpostCut {
        ZONE, Y, X, CAMERA_LEFT, LEFT, CAMERA_DEST, DEST, HUD_TIME, SPEED, INVINCIBLE, FM,
        PERIOD, FADE, MUSIC, LOCK, HOLD, ZONE_ACT, HOLD_ZERO, BONUS, MINUTES, SECONDS,
        TIME_BONUS, RINGS, RING_BONUS, DRAW, RETURN
    }
    /** Private actual producer scope; public component records cannot mint or replay it. */
    private final class SignpostInvocation {
        private final CdSignpost body = signpost;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final Object world; private final long generation; private final int whole;
        private final CdObjectState entry;
        private Values base = values;
        private List<SonicCdGameModule.PastSound> ledger = sounds;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final CdObjectDrawQueue.CameraWords camera;
        private SignpostCut next;
        private boolean zoneRead, hit, resultsPrefix, drawn, screenCleared;
        private int zone, y, x, observedCamera, period, zoneAct, minutes, seconds, rings;
        private int child = -1; private CdObjectState childImage, drawSource;
        private SignpostInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            camera = new CdObjectDrawQueue.CameraWords(scroll.state().cameraX(), scroll.state().cameraYWord());
            next = switch (entry.routine()) {
                case 0 -> SignpostCut.ZONE;
                case 2 -> SignpostCut.Y;
                case 6 -> ((entry.unsignedByte(CdObjectState.ByteField.LEVEL_END_TIMER) - 1) & 255) == 0
                        ? SignpostCut.PERIOD : SignpostCut.DRAW;
                default -> SignpostCut.DRAW;
            };
        }
    }
    private boolean signpostForeignScope() {
        return activeGoal != null || goalPort || drawingGoal || goalForeignScope();
    }
    private void runSignpost(int whole) {
        module.requirePastLoadingVisit(this);
        if (signpostForeignScope() || activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || queuePort
                || !runningObjects || servicing || loadingSaved || whole < 32 || whole >= 128 || values.phase() != BEGIN_LEVEL
                || placement.pool().state(whole).id() != CdSignpost.OBJECT_ID
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Signpost dispatch");
        var invocation = new SignpostInvocation(whole); activeSignpost = invocation;
        try {
            var result = signpost.run(whole); verifySignpost(invocation);
            if (!invocation.drawn || !invocation.screenCleared || invocation.next != SignpostCut.RETURN)
                throw new IllegalStateException("Signpost returned before its genuine unconditional Draw tail");
            validateSignpostImages(invocation, signpostSource(invocation), false, false);
            if (!result.state().equals(invocation.pool.state(whole))) throw new IllegalStateException("Signpost result lost its live physical source");
            // A real ID3A child remains in this same increasing physical walk and reaches the cached Results owner.
        } catch (RuntimeException | Error original) { verifySignpostFailure(invocation, original); throw original; }
        finally { activeSignpost = null; }
    }
    private SignpostInvocation requireSignpost(int whole) {
        var invocation = activeSignpost;
        if (invocation == null || invocation.whole != whole || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual selected Signpost callback");
        verifySignpost(invocation); return invocation;
    }
    private void verifySignpost(SignpostInvocation invocation) {
        module.requirePastLoadingVisit(this); var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeSignpost != invocation || signpostForeignScope() || !runningObjects || servicing || loadingSaved
                || invocation.body != signpost || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool() || invocation.queue != drawQueue
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING || values.phase() != BEGIN_LEVEL
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.base != values || invocation.ledger != sounds || !placement.status().equals(invocation.before.status())
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !frame.state().equals(invocation.actor.state()) || !frame.environment().equals(invocation.shared.environment())
                || !presentation.samePastScroll(invocation.scroll))
            throw new IllegalStateException("Signpost changed another owner or retained a stale World/Frame cut");
        invocation.pool.validateRestore(invocation.before.pool());
    }
    private CdObjectState signpostInitHeader(SignpostInvocation invocation) {
        return invocation.entry.addByte(CdObjectState.ByteField.ROUTINE, 2)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, invocation.entry.spriteFlags() | 4)
                .moveByte(CdObjectState.ByteField.X_RADIUS, 24).moveByte(CdObjectState.ByteField.WIDTH, 24)
                .moveByte(CdObjectState.ByteField.Y_RADIUS, 32).moveByte(CdObjectState.ByteField.PRIORITY, 4)
                .moveWord(CdObjectState.WordField.TILE, 0x043C);
    }
    private CdObjectState signpostAnimation(SignpostInvocation invocation) {
        var original = invocation.entry;
        var out = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(CdObjectState.ByteField.ANIMATION),
                original.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION), original.unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME),
                original.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME), original.unsignedByte(CdObjectState.ByteField.MAP_FRAME),
                original.spriteFlags(), original.flags(), original.routine(), original.unsignedByte(CdObjectState.ByteField.ROUTINE_2)),
                new CdObjectAnimation.AnimationTable(assets.view(CdR11bAssets.Asset.ANI_SIGNPOST).bytes(), 0, 1));
        return original.moveByte(CdObjectState.ByteField.ANIMATION, out.animationId())
                .moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, out.previousId()).moveByte(CdObjectState.ByteField.ANIMATION_FRAME, out.scriptIndex())
                .moveByte(CdObjectState.ByteField.ANIMATION_TIME, out.timer()).moveByte(CdObjectState.ByteField.MAP_FRAME, out.mapFrame())
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, out.spriteFlags()).moveByte(CdObjectState.ByteField.FLAGS, out.statusFlags())
                .moveByte(CdObjectState.ByteField.ROUTINE, out.routine()).moveByte(CdObjectState.ByteField.ROUTINE_2, out.routine2());
    }
    private CdObjectState signpostSource(SignpostInvocation invocation) {
        var source = invocation.entry;
        if (source.routine() == 0) {
            source = signpostInitHeader(invocation);
            if (invocation.zoneRead) source = source.moveWord(CdObjectState.WordField.TILE, invocation.zone == 3 ? 0x043C : 0x843C)
                    .moveLong(CdObjectState.LongField.MAPPINGS, assets.view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST).range().nativeAddress());
        }
        if (invocation.hit) source = source.moveByte(CdObjectState.ByteField.LEVEL_END_TIMER, 120)
                .moveByte(CdObjectState.ByteField.MAP_FRAME, 0).addByte(CdObjectState.ByteField.ROUTINE, 2);
        if (invocation.entry.routine() == 4) {
            source = signpostAnimation(invocation).addByte(CdObjectState.ByteField.LEVEL_END_TIMER, -1);
            if (source.unsignedByte(CdObjectState.ByteField.LEVEL_END_TIMER) == 0) source = source.addByte(CdObjectState.ByteField.ROUTINE, 2)
                    .moveByte(CdObjectState.ByteField.MAP_FRAME, 3).moveByte(CdObjectState.ByteField.LEVEL_END_TIMER, 60);
        }
        if (invocation.entry.routine() == 6) {
            source = source.addByte(CdObjectState.ByteField.LEVEL_END_TIMER, -1);
            if (invocation.resultsPrefix) source = source.moveByte(CdObjectState.ByteField.LEVEL_END_TIMER, 180).addByte(CdObjectState.ByteField.ROUTINE, 2);
        }
        if (invocation.screenCleared) source = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        return source;
    }
    /** Only the actual first-free ID3A and its delay byte may differ from this invocation's other96 images. */
    private void validateSignpostImages(SignpostInvocation invocation, CdObjectState expectedSource,
                                        boolean admitChild, boolean admitChildIdPrefix) {
        if (!expectedSource.equals(invocation.pool.state(invocation.whole))) throw new IllegalStateException("Signpost source differs from its reached full64 prefix");
        int first = -1;
        for (var old : invocation.before.pool().slots()) if (old.id() == 0) { first = old.identity().wholeIndex(); break; }
        for (var old : invocation.before.pool().slots()) {
            int whole = old.identity().wholeIndex(); if (whole == invocation.whole) continue;
            var live = invocation.pool.state(whole);
            if (whole == invocation.child) {
                if (!live.equals(invocation.childImage)) throw new IllegalStateException("Signpost rewrote its already-created Results child");
                continue;
            }
            if (old.equals(live)) continue;
            if (!admitChild || !invocation.resultsPrefix || invocation.child >= 0 || whole != first)
                throw new IllegalStateException("Signpost changed an unrelated full64 physical image");
            var idOnly = old.moveByte(CdObjectState.ByteField.ID, CdSignpost.RESULTS_ID);
            var complete = idOnly.moveByte(CdObjectState.ByteField.RESULTS_TIMER, 16);
            if (!live.equals(complete) && !(admitChildIdPrefix && live.equals(idOnly)))
                throw new IllegalStateException("Signpost Results allocation changed dirty bytes beyond ID/delay");
            invocation.child = whole; invocation.childImage = live;
        }
        var now = placement.capture();
        if (!sameBytes(now.savedFlags(), invocation.before.savedFlags()) || !now.status().equals(invocation.before.status()))
            throw new IllegalStateException("Signpost has no saved-bank or spawner-cursor writes");
    }
    private void validateSignpostCut(SignpostInvocation invocation, CdObjectPlacementRuntime.Snapshot cut,
                                     CdObjectState source, boolean admitChild) {
        verifySignpost(invocation); placement.validateRestore(Objects.requireNonNull(cut));
        validateSignpostImages(invocation, signpostSource(invocation), admitChild, false);
        var now = placement.capture();
        if (!source.equals(invocation.pool.state(invocation.whole)) || !source.identity().equals(invocation.entry.identity())
                || !cut.pool().slots().equals(now.pool().slots()) || !cut.status().equals(now.status())
                || !sameBytes(cut.savedFlags(), now.savedFlags()))
            throw new IllegalStateException("Signpost port lacks its actual complete fresh placement/source cut");
    }
    private int readSignpost(CdSignpost.ReadCall call) {
        Objects.requireNonNull(call); var invocation = requireSignpost(call.expectedSource().identity().wholeIndex());
        if (signpostPort || drawingSignpost || queuePort || invocation.drawn || !signpostReadAt(invocation.next, call.field()))
            throw new IllegalStateException("Signpost read lacks its genuine native conditional position");
        validateSignpostCut(invocation, call.actualCut(), call.expectedSource(), false);
        int value = switch (call.field()) {
            case ZONE_BYTE -> frame.environment().movement().gates().zoneAct() >>> 8;
            case PLAYER_Y_WORD -> frame.state().motion().centreY16_16() >>> 16;
            case PLAYER_X_WORD -> frame.state().motion().centreX16_16() >>> 16;
            case CAMERA_X_WORD -> presentation.pastForegroundImage().cameraX();
            case TIME_ZONE_BYTE -> frame.environment().movement().gates().timeZone();
            case ZONE_ACT_WORD -> frame.environment().movement().gates().zoneAct();
            case TIME_MINUTES_BYTE -> signpostClock(CdCheckpointLiveAux.TimeByte.MINUTES, call.field());
            case TIME_SECONDS_BYTE -> signpostClock(CdCheckpointLiveAux.TimeByte.SECONDS, call.field());
            case RINGS_WORD -> values.ring().rings();
        };
        signpostPort = true;
        try {
            verifySignpost(invocation); validateSignpostCut(invocation, call.actualCut(), call.expectedSource(), false);
            switch (call.field()) {
                case ZONE_BYTE -> { invocation.zone = value; invocation.zoneRead = true; invocation.next = SignpostCut.Y; }
                case PLAYER_Y_WORD -> { invocation.y = value; int dy = (value - invocation.entry.centreY() + 128) & 0xFFFF;
                    invocation.next = (short) dy < 0 || dy >= 256 ? SignpostCut.DRAW : SignpostCut.X; }
                case PLAYER_X_WORD -> { invocation.x = value; invocation.next = value > invocation.entry.centreX() ? SignpostCut.CAMERA_LEFT : SignpostCut.DRAW; }
                case CAMERA_X_WORD -> { invocation.observedCamera = value; invocation.next = invocation.next == SignpostCut.CAMERA_LEFT ? SignpostCut.LEFT : SignpostCut.DEST; }
                case TIME_ZONE_BYTE -> { invocation.period = value; invocation.next = value == 0 ? SignpostCut.FADE : SignpostCut.MUSIC; }
                case ZONE_ACT_WORD -> { invocation.zoneAct = value; invocation.next = value == 0x502 ? SignpostCut.HOLD_ZERO : SignpostCut.BONUS;
                    if (value != 0x502) invocation.resultsPrefix = true; }
                case TIME_MINUTES_BYTE -> { invocation.minutes = value; invocation.next = SignpostCut.SECONDS; }
                case TIME_SECONDS_BYTE -> { invocation.seconds = value; invocation.next = SignpostCut.TIME_BONUS; }
                case RINGS_WORD -> { invocation.rings = value; invocation.next = SignpostCut.RING_BONUS; }
            }
            verifySignpost(invocation); return value;
        } finally { signpostPort = false; }
    }
    private static boolean signpostReadAt(SignpostCut cut, CdSignpost.ReadField field) {
        return switch (field) {
            case ZONE_BYTE -> cut == SignpostCut.ZONE;
            case PLAYER_Y_WORD -> cut == SignpostCut.Y;
            case PLAYER_X_WORD -> cut == SignpostCut.X;
            case CAMERA_X_WORD -> cut == SignpostCut.CAMERA_LEFT || cut == SignpostCut.CAMERA_DEST;
            case TIME_ZONE_BYTE -> cut == SignpostCut.PERIOD;
            case ZONE_ACT_WORD -> cut == SignpostCut.ZONE_ACT;
            case TIME_MINUTES_BYTE -> cut == SignpostCut.MINUTES;
            case TIME_SECONDS_BYTE -> cut == SignpostCut.SECONDS;
            case RINGS_WORD -> cut == SignpostCut.RINGS;
        };
    }
    private int signpostClock(CdCheckpointLiveAux.TimeByte byteField, CdSignpost.ReadField field) {
        // Exact existing native aliases: timeMinutes FF1515 / timeSeconds FF1516, Main CPU Variables67-68.
        // Do not require the unrelated leading time byte or expand an unknown byte to a fabricated zero.
        return values.globals().live().knownByte(byteField.nativeAddress())
                .orElseThrow(() -> new UnsupportedSignpostRead(field, byteField.nativeAddress()));
    }
    private void storeSignpost(CdSignpost.StoreCall call) {
        Objects.requireNonNull(call); var invocation = requireSignpost(call.expectedSource().identity().wholeIndex());
        if (signpostPort || drawingSignpost || queuePort || invocation.drawn || !signpostStoreAt(invocation.next, call.field()))
            throw new IllegalStateException("Signpost store lacks its actual once-only mixed-owner order");
        validateSignpostCut(invocation, call.actualCut(), call.expectedSource(), call.field() == CdSignpost.StoreField.UPDATE_HUD_BONUS_BYTE);
        int expected = switch (call.field()) {
            case LEFT_BOUND_WORD, DEST_LEFT_BOUND_WORD -> invocation.observedCamera;
            case UPDATE_HUD_TIME_BYTE, SPEED_SHOES_BYTE, INVINCIBLE_BYTE -> 0;
            case CONTROL_LOCK_BIT0, UPDATE_HUD_BONUS_BYTE -> 1;
            case PLAYER_CONTROL_HOLD_WORD -> invocation.next == SignpostCut.HOLD_ZERO ? 0 : 0x0808;
            case TIME_BONUS_WORD -> assets.view(CdR11bAssets.Asset.LEVEL_END_TIME_BONUSES).bytes()
                    .unsignedWord(Math.min(((invocation.minutes * 60 + invocation.seconds) & 0xFFFF) / 15, 20) * 2);
            case RING_BONUS_WORD -> (invocation.rings * 100) & 0xFFFF;
        };
        if (call.value() != expected) throw new IllegalStateException("Signpost store differs from its genuine reached native operand");
        boolean frameStore = switch (call.field()) {
            case LEFT_BOUND_WORD, SPEED_SHOES_BYTE, INVINCIBLE_BYTE, CONTROL_LOCK_BIT0, PLAYER_CONTROL_HOLD_WORD -> true;
            default -> false;
        };
        if (frameStore) publishSignpostFrame(invocation, call);
        else if (call.field() == CdSignpost.StoreField.DEST_LEFT_BOUND_WORD) publishSignpostDestination(invocation, call);
        else publishSignpostValues(invocation, call);
        invocation.next = switch (call.field()) {
            case LEFT_BOUND_WORD -> SignpostCut.CAMERA_DEST;
            case DEST_LEFT_BOUND_WORD -> SignpostCut.HUD_TIME;
            case UPDATE_HUD_TIME_BYTE -> SignpostCut.SPEED;
            case SPEED_SHOES_BYTE -> SignpostCut.INVINCIBLE;
            case INVINCIBLE_BYTE -> SignpostCut.FM;
            case CONTROL_LOCK_BIT0 -> SignpostCut.HOLD;
            case PLAYER_CONTROL_HOLD_WORD -> invocation.next == SignpostCut.HOLD_ZERO ? SignpostCut.BONUS : SignpostCut.ZONE_ACT;
            case UPDATE_HUD_BONUS_BYTE -> SignpostCut.MINUTES;
            case TIME_BONUS_WORD -> SignpostCut.RINGS;
            case RING_BONUS_WORD -> SignpostCut.DRAW;
        };
        if (call.field() == CdSignpost.StoreField.UPDATE_HUD_TIME_BYTE) invocation.hit = true;
        if (call.field() == CdSignpost.StoreField.PLAYER_CONTROL_HOLD_WORD && call.value() == 0) invocation.resultsPrefix = true;
        verifySignpost(invocation);
    }
    private static boolean signpostStoreAt(SignpostCut cut, CdSignpost.StoreField field) {
        return switch (field) {
            case LEFT_BOUND_WORD -> cut == SignpostCut.LEFT;
            case DEST_LEFT_BOUND_WORD -> cut == SignpostCut.DEST;
            case UPDATE_HUD_TIME_BYTE -> cut == SignpostCut.HUD_TIME;
            case SPEED_SHOES_BYTE -> cut == SignpostCut.SPEED;
            case INVINCIBLE_BYTE -> cut == SignpostCut.INVINCIBLE;
            case CONTROL_LOCK_BIT0 -> cut == SignpostCut.LOCK;
            case PLAYER_CONTROL_HOLD_WORD -> cut == SignpostCut.HOLD || cut == SignpostCut.HOLD_ZERO;
            case UPDATE_HUD_BONUS_BYTE -> cut == SignpostCut.BONUS;
            case TIME_BONUS_WORD -> cut == SignpostCut.TIME_BONUS;
            case RING_BONUS_WORD -> cut == SignpostCut.RING_BONUS;
        };
    }
    private Values signpostValues(CdLevelGlobals level, LoadingGlobals globals) {
        var old = values;
        return new Values(old.phase(), old.section(), old.cpuPalette(), old.fadePalette(), old.displayedPalette(), old.cpuSat(), old.displayedSat(),
                old.displayedHScroll(), old.displayedVScrollLongBits(), old.foregroundPlane(), old.ring(), level, globals, old.hudObjects(),
                old.fadeProgress(), old.calls(), old.loop());
    }
    private void publishSignpostFrame(SignpostInvocation invocation, CdSignpost.StoreCall call) {
        var ready = frame.prepareObjectSignpostStore(invocation.actor.state(), invocation.shared.environment(), call.field(), call.value());
        var actor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), ready.finalState(), invocation.actor.display());
        var shared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), ready.finalEnvironment(), invocation.shared.artState(),
                invocation.shared.paused(), invocation.shared.vIntClock());
        signpostPort = true;
        try {
            verifySignpost(invocation); validateSignpostCut(invocation, call.actualCut(), call.expectedSource(), false);
            frame.validatePreparedObjectSolidPublication(ready); frame.applyPreparedObjectSolidPublication(ready);
            invocation.actor = actor; invocation.shared = shared; verifySignpost(invocation);
        } finally { signpostPort = false; }
    }
    private void publishSignpostDestination(SignpostInvocation invocation, CdSignpost.StoreCall call) {
        var ready = presentation.preparePastGoalPostDestLeftBound(frame, invocation.scroll, call.value());
        signpostPort = true;
        try {
            verifySignpost(invocation); validateSignpostCut(invocation, call.actualCut(), call.expectedSource(), false);
            presentation.validatePreparedPastGoalPostDestLeftBound(ready); presentation.applyPreparedPastGoalPostDestLeftBound(ready);
            invocation.scroll = ready.result(); verifySignpost(invocation);
        } finally { signpostPort = false; }
    }
    private void publishSignpostValues(SignpostInvocation invocation, CdSignpost.StoreCall call) {
        var level = values.level(); var globals = values.globals();
        if (call.field() == CdSignpost.StoreField.UPDATE_HUD_TIME_BYTE) {
            level = new CdLevelGlobals(level.levelRestart(), call.value(), level.checkpoint(), level.spawnMode(), level.plcLoadFlags(),
                    level.flowerCount(), level.fadeEnableDisplay(), level.powerup(), level.timeOver());
        } else {
            var before = globals.bonus().orElseThrow(() -> new UnsupportedSignpostBonus(call.field()));
            var bonus = new CdLevelHud.BonusValues(call.field() == CdSignpost.StoreField.TIME_BONUS_WORD ? call.value() : before.timeBonusWord(),
                    call.field() == CdSignpost.StoreField.RING_BONUS_WORD ? call.value() : before.ringBonusWord(),
                    call.field() == CdSignpost.StoreField.UPDATE_HUD_BONUS_BYTE ? call.value() : before.updateHudBonusByte());
            globals = new LoadingGlobals(globals.live(), globals.contact(), globals.score(), globals.drownTimerWord(), globals.tail(),
                    Optional.of(bonus), globals.layoutRam(), globals.debugBlockWord(), globals.pauseMusicBit(), globals.retainedDev());
        }
        var ready = signpostValues(level, globals); signpostPort = true;
        try {
            verifySignpost(invocation); validateSignpostCut(invocation, call.actualCut(), call.expectedSource(), false);
            values = ready; invocation.base = ready; verifySignpost(invocation);
        } finally { signpostPort = false; }
    }
    private void soundSignpost(CdSignpost.SoundCall call) {
        Objects.requireNonNull(call); var invocation = requireSignpost(call.expectedSource().identity().wholeIndex());
        boolean expected = switch (call.sound()) {
            case FM_SIGNPOST -> invocation.next == SignpostCut.FM;
            case SCMD_FADEPCM -> invocation.next == SignpostCut.FADE && invocation.period == 0;
            case SCMD_RESULTMUS -> invocation.next == SignpostCut.MUSIC;
        };
        if (signpostPort || drawingSignpost || queuePort || invocation.drawn || !expected)
            throw new IllegalStateException("Signpost sound lacks its actual once-only native site");
        validateSignpostCut(invocation, call.actualCut(), call.expectedSource(), false);
        var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastSignpostSound(call.sound()));
        var ready = List.copyOf(ledger); signpostPort = true;
        try {
            verifySignpost(invocation); validateSignpostCut(invocation, call.actualCut(), call.expectedSource(), false);
            sounds = ready; invocation.ledger = ready;
            invocation.next = call.sound() == CdSignpost.Sound.FM_SIGNPOST ? SignpostCut.DRAW
                    : call.sound() == CdSignpost.Sound.SCMD_FADEPCM ? SignpostCut.MUSIC : SignpostCut.LOCK;
            verifySignpost(invocation);
        } finally { signpostPort = false; }
    }
    private void drawSignpost(CdSignpost.DrawCall call) {
        Objects.requireNonNull(call); var invocation = requireSignpost(call.expectedSource().identity().wholeIndex());
        if (signpostPort || drawingSignpost || queuePort || invocation.drawn || invocation.next != SignpostCut.DRAW)
            throw new IllegalStateException("Signpost Draw lacks its genuine returned-body tail");
        validateSignpostCut(invocation, call.actualCut(), call.expectedSource(), false);
        var source = call.expectedSource(); invocation.drawSource = source; invocation.drawn = true; drawingSignpost = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole), source.spriteFlags(),
                    source.centreX(), source.centreY(), source.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    source.unsignedByte(CdObjectState.ByteField.WIDTH), source.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            verifySignpost(invocation);
            if (!invocation.screenCleared) throw new IllegalStateException("Signpost Draw omitted the actual source BCLR7");
            invocation.next = SignpostCut.RETURN;
        } finally { drawingSignpost = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readSignpostDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireSignpost(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingSignpost || signpostPort || queuePort || !invocation.drawn)
            throw new IllegalStateException("No actual Signpost collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var source = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(source.id(), source.spriteFlags(), source.centreX(), source.centreY(),
                    source.word(CdObjectState.WordField.X_FRACTION), source.longBits(CdObjectState.LongField.MAPPINGS),
                    source.unsignedByte(CdObjectState.ByteField.MAP_FRAME), source.word(CdObjectState.WordField.TILE));
            verifySignpost(invocation);
            if (!before.pool().slots().equals(placement.capture().pool().slots()) || !sameBytes(before.savedFlags(), placement.capture().savedFlags()))
                throw new IllegalStateException("Signpost queue read changed its actual full placement");
            return live;
        } finally { queuePort = false; }
    }
    private void writeSignpostOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireSignpost(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingSignpost || signpostPort || queuePort || !invocation.drawn || invocation.screenCleared || onScreen)
            throw new IllegalStateException("Only actual Signpost Draw clears bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("Signpost changed before its actual BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready); queuePort = true;
        try {
            verifySignpost(invocation); invocation.pool.write(next); invocation.screenCleared = true; verifySignpost(invocation);
            if (!ready.slots().equals(invocation.pool.capture().slots()) || !sameBytes(before.savedFlags(), placement.capture().savedFlags()))
                throw new IllegalStateException("Signpost Draw changed more than its source bit7");
        } finally { queuePort = false; }
    }
    /** Genuine animation-read stop prefix; never a second successful animation or adoption. */
    private CdObjectState signpostAnimationStop(SignpostInvocation invocation, CdSignpost.UnsupportedSignpost stopped) {
        var source = invocation.entry; int animation = source.unsignedByte(CdObjectState.ByteField.ANIMATION);
        if (animation != source.unsignedByte(CdObjectState.ByteField.PREVIOUS_ANIMATION)) source = source
                .moveByte(CdObjectState.ByteField.PREVIOUS_ANIMATION, animation).moveByte(CdObjectState.ByteField.ANIMATION_FRAME, 0)
                .moveByte(CdObjectState.ByteField.ANIMATION_TIME, 0);
        source = source.addByte(CdObjectState.ByteField.ANIMATION_TIME, -1);
        if (stopped.boundary() == CdSignpost.UnsupportedBoundary.ANIMATION_DIRECTORY_READ) return source;
        var bytes = assets.view(CdR11bAssets.Asset.ANI_SIGNPOST).bytes(); int script = (short) bytes.unsignedWord(animation * 2);
        if (stopped.boundary() == CdSignpost.UnsupportedBoundary.ANIMATION_SPEED_READ) return source;
        source = source.moveByte(CdObjectState.ByteField.ANIMATION_TIME, bytes.unsignedByte(script));
        return source; // Original finite source has FF only; malformed raw frame/index stops precede timer decrement.
    }
    private void verifySignpostFailure(SignpostInvocation invocation, Throwable original) {
        try {
            verifySignpost(invocation); CdObjectState source;
            if (original instanceof CdSignpost.UnsupportedSignpost stopped) {
                if (stopped.wholeIndex() != invocation.whole) throw new IllegalStateException("Signpost stopped at another physical source");
                source = switch (stopped.boundary()) {
                    case ANIMATION_DIRECTORY_READ, ANIMATION_SPEED_READ, ANIMATION_FRAME_READ, ANIMATION_OPERAND_READ -> signpostAnimationStop(invocation, stopped);
                    default -> signpostSource(invocation);
                };
            } else source = signpostSource(invocation);
            validateSignpostImages(invocation, source, invocation.resultsPrefix, true);
        } catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** Actual GoalPost scope. Public component records/effects cannot create or resume this invocation. */
    private enum GoalSite { Y, X, GOAL, GOAL_PLC, TIDAL_PLC, HEAD, DRAW, RETURN }
    private final class GoalInvocation {
        private final CdGoalPost body = goalPost;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdGoalPost.Globals globals;
        private final CdObjectDrawQueue.CameraWords camera;
        private Values base = values;
        private final List<SonicCdGameModule.PastSound> ledger = sounds;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private GoalSite next;
        private int y, x, plcHead;
        private boolean yRead, xRead, passed, headRead, goalStored, plcTaken, drawn, screenCleared;
        private CdObjectState drawSource;
        private GoalInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            camera = new CdObjectDrawQueue.CameraWords(scroll.state().cameraX(), scroll.state().cameraYWord());
            var gates = shared.environment().movement().gates();
            globals = new CdGoalPost.Globals(gates.zoneAct(), gates.timeZone(), values.globals().retainedDev().goodFuture(),
                    gates.timeWarpDirection(), camera.xWord());
            boolean tidal = globals.zoneAct() == 0x201 && globals.timeZone() == 1;
            next = entry.routine() == 0 && tidal ? entry.subtype() == 0 ? GoalSite.TIDAL_PLC : GoalSite.HEAD
                    : entry.routine() == 0 || entry.routine() == 2 ? GoalSite.Y : GoalSite.DRAW;
        }
    }
    private boolean goalForeignScope() {
        return activeMachine != null || machinePort || drawingMachine || machineForeignScope();
    }
    private void runGoal(int whole) {
        module.requirePastLoadingVisit(this);
        if (goalForeignScope() || activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || queuePort || !runningObjects
                || servicing || loadingSaved || whole < 32 || whole >= 128 || values.phase() != BEGIN_LEVEL
                || placement.pool().state(whole).id() != CdGoalPost.OBJECT_ID
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past GoalPost dispatch");
        var invocation = new GoalInvocation(whole); activeGoal = invocation;
        try {
            var result = goalPost.run(whole, invocation.globals);
            verifyGoal(invocation);
            if ((invocation.globals.zoneAct() & 255) == 2 ? invocation.drawn
                    : !invocation.drawn || !invocation.screenCleared)
                throw new IllegalStateException("GoalPost skipped or repeated its native act-byte Draw selection");
            if (invocation.next != GoalSite.RETURN && !((invocation.globals.zoneAct() & 255) == 2 && invocation.next == GoalSite.DRAW))
                throw new IllegalStateException("GoalPost returned before its actual conditional read/store/PLC order");
            verifyGoalTail(invocation, true);
            if (!result.state().equals(invocation.pool.state(whole))) throw new IllegalStateException("GoalPost result is not its live source image");
        } catch (RuntimeException | Error original) {
            verifyGoalFailure(invocation, original); throw original;
        } finally { activeGoal = null; }
    }
    private GoalInvocation requireGoal(int whole) {
        var invocation = activeGoal;
        if (invocation == null || invocation.whole != whole || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual selected GoalPost callback");
        verifyGoal(invocation); return invocation;
    }
    private void verifyGoal(GoalInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != invocation || goalForeignScope() || !runningObjects || servicing || loadingSaved
                || invocation.body != goalPost || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || values != invocation.base || values.phase() != BEGIN_LEVEL || sounds != invocation.ledger
                || !placement.status().equals(invocation.before.status()) || !presentation.samePastScroll(invocation.scroll)
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !frame.state().equals(invocation.actor.state()) || !frame.environment().equals(invocation.shared.environment()))
            throw new IllegalStateException("GoalPost changed another owner or retained a stale World/Frame cut");
        invocation.pool.validateRestore(invocation.before.pool());
    }
    /** Native Init:612-619 prefix before its reached finite tile WORD. No reset of dirty collision/animation bytes. */
    private CdObjectState goalHeader(GoalInvocation invocation) {
        return invocation.entry.addByte(CdObjectState.ByteField.ROUTINE, 2)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, invocation.entry.spriteFlags() | 4)
                .moveByte(CdObjectState.ByteField.PRIORITY, 4)
                .moveLong(CdObjectState.LongField.MAPPINGS, assets.view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST).range().nativeAddress())
                .moveByte(CdObjectState.ByteField.X_RADIUS, 16).moveByte(CdObjectState.ByteField.WIDTH, 16)
                .moveByte(CdObjectState.ByteField.Y_RADIUS, 32).moveByte(CdObjectState.ByteField.MAP_FRAME, 5);
    }
    /** Native SetBaseTile:664-681 uses BYTE shift/add wrap, then a finite source WORD, then zone3 priority exception. */
    private CdObjectState goalInitialMainSource(GoalInvocation invocation) {
        var g = invocation.globals; int index = ((g.zoneAct() & 0xFF00) | ((g.zoneAct() << 7) & 255)) >>> 4;
        int period = g.timeZone(); if (period == 2) period = (period + g.goodFuture()) & 255;
        index = (index & 0xFF00) | ((index + ((period * 2) & 255)) & 255);
        var tiles = assets.view(CdR11bAssets.Asset.GOAL_POST_BASE_TILES).bytes();
        if ((short) index < 0 || index > tiles.length() - 2)
            throw new IllegalStateException("GoalPost reached another finite base-tile operand");
        int tile = tiles.unsignedWord(index); if ((g.zoneAct() >>> 8) != 3) tile |= 0x8000;
        return goalHeader(invocation).moveWord(CdObjectState.WordField.TILE, tile);
    }
    private CdObjectState goalSource(GoalInvocation invocation) {
        var source = invocation.entry;
        if (source.routine() == 0) {
            boolean tidal = invocation.globals.zoneAct() == 0x201 && invocation.globals.timeZone() == 1;
            if (tidal && source.subtype() == 0) source = source.moveByte(CdObjectState.ByteField.SUBTYPE, 1);
            else if (!tidal || invocation.headRead && invocation.plcHead == 0) source = goalInitialMainSource(invocation);
        }
        if (invocation.passed) source = source.addByte(CdObjectState.ByteField.ROUTINE, 2);
        if (invocation.screenCleared) source = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        return source;
    }
    private void validateGoalCut(GoalInvocation invocation, CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        verifyGoal(invocation); placement.validateRestore(Objects.requireNonNull(cut));
        var expected = goalSource(invocation); var now = placement.capture();
        var expectedPool = CdObjectPool.overlay(invocation.before.pool(), List.of(expected));
        if (!expected.equals(source) || !source.equals(invocation.pool.state(invocation.whole))
                || !cut.pool().slots().equals(now.pool().slots()) || !now.pool().slots().equals(expectedPool.slots())
                || !cut.status().equals(now.status()) || !sameBytes(cut.savedFlags(), now.savedFlags())
                || !sameBytes(now.savedFlags(), invocation.before.savedFlags()))
            throw new IllegalStateException("GoalPost port lacks its actual full96/source/native-prefix cut");
    }
    private int readGoalPlayerWord(CdGoalPost.ReadCall call) {
        Objects.requireNonNull(call); var invocation = requireGoal(call.expectedSource().identity().wholeIndex());
        boolean y = call.field() == CdGoalPost.PlayerField.Y_WORD;
        if (goalPort || drawingGoal || queuePort || invocation.drawn || (y ? invocation.next != GoalSite.Y || invocation.yRead
                : invocation.next != GoalSite.X || !invocation.yRead || invocation.xRead))
            throw new IllegalStateException("GoalPost player read lacks its genuine Y-before-conditional-X position");
        validateGoalCut(invocation, call.actualCut(), call.expectedSource());
        var motion = frame.state().motion(); int value = y ? motion.centreY16_16() >>> 16 : motion.centreX16_16() >>> 16;
        goalPort = true;
        try {
            verifyGoal(invocation); validateGoalCut(invocation, call.actualCut(), call.expectedSource());
            if (y) {
                invocation.yRead = true; invocation.y = value;
                int distance = (value - call.expectedSource().centreY() + 128) & 0xFFFF;
                invocation.next = (short) distance < 0 || distance >= 256 ? GoalSite.DRAW : GoalSite.X;
            } else {
                invocation.xRead = true; invocation.x = value; invocation.passed = value >= call.expectedSource().centreX();
                invocation.next = invocation.passed ? GoalSite.GOAL : GoalSite.DRAW;
            }
            verifyGoal(invocation); return value;
        } finally { goalPort = false; }
    }
    private Values goalValues(CdR11bSectionArt.State section, LoadingGlobals globals) {
        var old = values;
        return new Values(old.phase(), section, old.cpuPalette(), old.fadePalette(), old.displayedPalette(), old.cpuSat(),
                old.displayedSat(), old.displayedHScroll(), old.displayedVScrollLongBits(), old.foregroundPlane(), old.ring(),
                old.level(), globals, old.hudObjects(), old.fadeProgress(), old.calls(), old.loop());
    }
    private void storeGoalReached(CdGoalPost.GoalReachedCall call) {
        Objects.requireNonNull(call); var invocation = requireGoal(call.expectedSource().identity().wholeIndex());
        int dy = (invocation.y - invocation.entry.centreY() + 128) & 0xFFFF;
        if (goalPort || drawingGoal || queuePort || invocation.drawn || invocation.goalStored || invocation.next != GoalSite.GOAL
                || !invocation.yRead || !invocation.xRead || !invocation.passed || (short) dy < 0 || dy >= 256
                || invocation.x < invocation.entry.centreX() || call.cameraXWord() != invocation.camera.xWord())
            throw new IllegalStateException("GoalPost store lacks the actual accepted native Y/X/camera cut");
        validateGoalCut(invocation, call.actualCut(), call.expectedSource());
        var readyFrame = frame.prepareObjectGoalPostReached(invocation.actor.state(), invocation.shared.environment(), call.cameraXWord());
        var readyScroll = presentation.preparePastGoalPostDestLeftBound(frame, invocation.scroll, call.cameraXWord());
        var g = values.globals();
        var readyValues = goalValues(values.section(), new LoadingGlobals(g.live(),
                new CdPlayerBody.ContactGlobals(0, g.contact().shield(), g.contact().combineRing()), g.score(), g.drownTimerWord(),
                g.tail(), g.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev()));
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        goalPort = true;
        try {
            verifyGoal(invocation); validateGoalCut(invocation, call.actualCut(), call.expectedSource());
            frame.validatePreparedObjectSolidPublication(readyFrame);
            presentation.validatePreparedPastGoalPostDestLeftBound(readyScroll);
            // Every pair/reference and the full native preimage is ready. No callbacks or allocation between stores.
            frame.applyPreparedObjectSolidPublication(readyFrame);
            presentation.applyPreparedPastGoalPostDestLeftBound(readyScroll); values = readyValues;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.scroll = readyScroll.result();
            invocation.base = readyValues; invocation.goalStored = true; invocation.next = GoalSite.GOAL_PLC;
            verifyGoal(invocation); validateGoalCut(invocation, call.actualCut(), call.expectedSource());
        } finally { goalPort = false; }
    }
    private void loadGoalPlc(CdGoalPost.PlcCall call) {
        Objects.requireNonNull(call); var invocation = requireGoal(call.expectedSource().identity().wholeIndex());
        boolean tidal = call.site() == CdGoalPost.PlcSite.TIDAL_INIT;
        if (goalPort || drawingGoal || queuePort || invocation.drawn || invocation.plcTaken
                || (tidal ? invocation.next != GoalSite.TIDAL_PLC || call.nativeId() != 0x13
                : invocation.next != GoalSite.GOAL_PLC || !invocation.goalStored || call.nativeId() != 0x12))
            throw new IllegalStateException("GoalPost LoadPLC lacks its actual once-only native cue cut");
        validateGoalCut(invocation, call.actualCut(), call.expectedSource());
        CdR11bSectionArt.State next; CdR11bSectionArt.SectionArtException stopped = null;
        try { next = art.appendList(values.section(), call.nativeId()); }
        catch (CdR11bSectionArt.SectionArtException failure) { next = failure.state(); stopped = failure; }
        var ready = goalValues(next, values.globals()); goalPort = true;
        try {
            verifyGoal(invocation); validateGoalCut(invocation, call.actualCut(), call.expectedSource());
            values = ready; invocation.base = ready;
            if (stopped == null) { invocation.plcTaken = true; invocation.next = GoalSite.DRAW; }
            verifyGoal(invocation); validateGoalCut(invocation, call.actualCut(), call.expectedSource());
        } finally { goalPort = false; }
        if (stopped != null) throw stopped; // Preserve the actual queue prefix and identical source exception.
    }
    private int readGoalPlcHead(CdGoalPost.PlcHeadCall call) {
        Objects.requireNonNull(call); var invocation = requireGoal(call.expectedSource().identity().wholeIndex());
        if (goalPort || drawingGoal || queuePort || invocation.drawn || invocation.headRead || invocation.next != GoalSite.HEAD)
            throw new IllegalStateException("GoalPost PLC-head read lacks its actual Tidal retry cut");
        validateGoalCut(invocation, call.actualCut(), call.expectedSource());
        int value = (int) values.section().queue().headSource(); goalPort = true;
        try {
            verifyGoal(invocation); invocation.plcHead = value; invocation.headRead = true;
            invocation.next = value == 0 ? GoalSite.Y : GoalSite.DRAW; verifyGoal(invocation); return value;
        } finally { goalPort = false; }
    }
    private void drawGoal(CdGoalPost.DrawCall call) {
        Objects.requireNonNull(call); var invocation = requireGoal(call.expectedSource().identity().wholeIndex());
        if (goalPort || drawingGoal || queuePort || invocation.drawn || invocation.next != GoalSite.DRAW
                || (invocation.globals.zoneAct() & 255) == 2)
            throw new IllegalStateException("GoalPost Draw lacks its actual returned-body/act-byte cut");
        validateGoalCut(invocation, call.actualCut(), call.expectedSource());
        var source = call.expectedSource(); invocation.drawSource = source; invocation.drawn = true; drawingGoal = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    source.spriteFlags(), source.centreX(), source.centreY(), source.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    source.unsignedByte(CdObjectState.ByteField.WIDTH), source.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            verifyGoal(invocation);
            if (!invocation.screenCleared) throw new IllegalStateException("GoalPost Draw omitted its actual BCLR7");
            invocation.next = GoalSite.RETURN;
        } finally { drawingGoal = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readGoalDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireGoal(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingGoal || goalPort || queuePort || !invocation.drawn)
            throw new IllegalStateException("No actual GoalPost collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var source = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(source.id(), source.spriteFlags(), source.centreX(), source.centreY(),
                    source.word(CdObjectState.WordField.X_FRACTION), source.longBits(CdObjectState.LongField.MAPPINGS),
                    source.unsignedByte(CdObjectState.ByteField.MAP_FRAME), source.word(CdObjectState.WordField.TILE));
            verifyGoal(invocation);
            if (!placement.capture().pool().slots().equals(before.pool().slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("GoalPost queue read changed full placement images");
            return live;
        } finally { queuePort = false; }
    }
    private void writeGoalOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireGoal(Objects.requireNonNull(ref).wholeIndex());
        if (!drawingGoal || goalPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
            throw new IllegalStateException("Only actual GoalPost Draw clears bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("GoalPost changed before its actual BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready); queuePort = true;
        try {
            verifyGoal(invocation); invocation.pool.write(next); invocation.screenCleared = true; verifyGoal(invocation);
            if (!invocation.pool.capture().slots().equals(ready.slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("GoalPost Draw changed more than its actual source bit7");
        } finally { queuePort = false; }
    }
    private void verifyGoalTail(GoalInvocation invocation, boolean completed) {
        var expected = goalSource(invocation); byte[] bank = invocation.before.savedFlags().copy();
        boolean outside = expected.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) >= 0
                && (((expected.centreX() & 0xFF80) - ((invocation.camera.xWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean delete = outside || (invocation.globals.timeZone() & 0x80) != 0;
        if (delete && expected.savedFlagsId() != 0) {
            int period = invocation.globals.timeZone() & 0x7F;
            if ((invocation.globals.timeZone() & 0x80) != 0) {
                int direction = invocation.goalStored ? 0 : invocation.globals.timeWarpDirection();
                period = Math.max(0, Math.min(2, (short) ((period - (byte) direction) & 0xFFFF)));
            }
            int index = 2 + (short) ((3 * expected.savedFlagsId() + period) & 0xFFFF);
            if (index < 2 || index >= bank.length) {
                if (completed) throw new IllegalStateException("GoalPost returned through an unowned native saved-bank read");
                delete = false;
            } else bank[index] &= 0x7F;
        }
        var live = invocation.pool.state(invocation.whole);
        if (delete) {
            if (!Arrays.equals(live.copyBytes(), new byte[64])) throw new IllegalStateException("GoalPost omitted its full native delete");
        } else if (!expected.equals(live)) throw new IllegalStateException("GoalPost changed unowned dirty source bytes");
        var expectedPool = CdObjectPool.overlay(invocation.before.pool(), List.of(live));
        if (!expectedPool.slots().equals(invocation.pool.capture().slots()) || !Arrays.equals(bank, placement.capture().savedFlags().copy()))
            throw new IllegalStateException("GoalPost changed another full64 image or an unowned saved byte");
    }
    private void verifyGoalFailure(GoalInvocation invocation, Throwable original) {
        try {
            verifyGoal(invocation);
            if (original instanceof CdGoalPost.UnsupportedGoalPost stopped && stopped.wholeIndex() != invocation.whole)
                throw new IllegalStateException("GoalPost stopped at another physical source");
            if (original instanceof CdGoalPost.UnsupportedGoalPost stopped
                    && stopped.boundary() == CdGoalPost.UnsupportedBoundary.BASE_TILE_READ) {
                var expectedPool = CdObjectPool.overlay(invocation.before.pool(), List.of(goalHeader(invocation)));
                if (!expectedPool.slots().equals(invocation.pool.capture().slots())
                        || !sameBytes(invocation.before.savedFlags(), placement.capture().savedFlags()))
                    throw new IllegalStateException("GoalPost finite tile stop lost its exact Init header prefix");
            } else if (invocation.next == GoalSite.RETURN) verifyGoalTail(invocation, false);
            else {
                var expectedPool = CdObjectPool.overlay(invocation.before.pool(), List.of(goalSource(invocation)));
                if (!expectedPool.slots().equals(invocation.pool.capture().slots())
                        || !sameBytes(invocation.before.savedFlags(), placement.capture().savedFlags()))
                    throw new IllegalStateException("GoalPost failure exceeds its genuine reached object prefix");
            }
        } catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** One cached Robot/Explosion producer. Public cuts and completed effects never create this scope. */
    private enum MachineSite { BODY, GOOD_FUTURE, POINTS, SOLID, GET_OFF, FM, DRAW }
    private final class MachineInvocation {
        private final boolean generator;
        private final Object body;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object sourceStage = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdRobotGenerator.Globals globals;
        private final CdObjectDrawQueue.CameraWords camera;
        private Values base = values;
        private List<SonicCdGameModule.PastSound> ledger = sounds;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private MachineSite site = MachineSite.BODY;
        private CdObjectState drawSource;
        private int child = -1;
        private boolean goodStored, pointsAdded, solidTaken, getOffRequired, gotOff, sounded, stopped, drawn, screenCleared;
        private MachineInvocation(int whole, boolean generator) {
            this.whole = whole; this.generator = generator; body = generator ? robotGenerator : explosion; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
            var gates = frame.environment().movement().gates();
            globals = new CdRobotGenerator.Globals(values.globals().retainedDev().goodFuture(), gates.timeZone(),
                    gates.zoneAct() & 255, gates.timeWarpDirection(), camera.xWord());
        }
    }
    private boolean machineForeignScope() {
        return activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeCollapse != null || activeFloat != null
                || activePata != null || activeTamabboh != null || activeBoulder != null || activeMosqui != null
                || activeSpikes != null || activeAnton != null || activeTunnelDoor != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || collapsePort || drawingCollapse || floatPort || drawingFloat
                || drawingPata || tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder
                || mosquiPort || drawingMosqui || spikesPort || drawingSpikes || antonPort || drawingAnton
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor;
    }
    private void runMachine(int whole, boolean generator) {
        module.requirePastLoadingVisit(this);
        if (machineForeignScope() || activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || queuePort
                || !runningObjects || servicing || loadingSaved || whole < 32 || whole >= 128
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || placement.pool().state(whole).id() != (generator ? CdRobotGenerator.OBJECT_ID : CdExplosion.OBJECT_ID))
            throw new IllegalStateException("No actual cached Past Robot/Explosion dispatch");
        var invocation = new MachineInvocation(whole, generator); activeMachine = invocation;
        try {
            CdObjectState result;
            if (generator) {
                var environment = frame.environment(); var gates = environment.movement().gates();
                result = robotGenerator.run(whole, invocation.globals, new CdRobotGenerator.Inputs(springPlayer(),
                        new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), springGlobals())).state();
            } else result = explosion.run(whole).state();
            verifyMachine(invocation); machineOtherImages(invocation, true); machineSavedTail(invocation);
            if (!result.equals(invocation.pool.state(whole)) || invocation.stopped
                    || (generator || invocation.entry.routine() != 4) && (!invocation.drawn || !invocation.screenCleared))
                throw new IllegalStateException("Robot/Explosion omitted its actual completed source/Draw publication");
            if (!generator && invocation.entry.routine() == 4 && invocation.drawn)
                throw new IllegalStateException("Explosion Done cannot Draw");
            // Native state/pool/global writes already happened through the cached producer and synchronous ports.
        } catch (RuntimeException | Error original) { verifyMachineFailure(invocation, original); throw original; }
        finally { activeMachine = null; }
    }
    private MachineInvocation requireMachine(int whole, boolean generator) {
        var invocation = activeMachine;
        if (invocation == null || invocation.whole != whole || invocation.generator != generator || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual selected Robot/Explosion callback");
        verifyMachine(invocation); return invocation;
    }
    private void verifyMachine(MachineInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != invocation || machineForeignScope() || !runningObjects || servicing || loadingSaved
                || invocation.body != (invocation.generator ? robotGenerator : explosion)
                || invocation.sourceFrame != frame || invocation.sourceAssets != assets || invocation.sourceStage != frame.stageSource()
                || invocation.pool != placement.pool() || invocation.queue != drawQueue || drawQueue == null
                || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING || values.phase() != BEGIN_LEVEL
                || invocation.world != mode.getWorldSession() || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.base != values || invocation.ledger != sounds || !placement.status().equals(invocation.before.status())
                || !presentation.samePastScroll(invocation.scroll) || !frame.captureActor().equals(invocation.actor)
                || !frame.captureShared().equals(invocation.shared) || !frame.state().equals(invocation.actor.state())
                || !frame.environment().equals(invocation.shared.environment()))
            throw new IllegalStateException("Robot/Explosion changed another owner or retained a stale actual World/Frame cut");
        invocation.pool.validateRestore(invocation.currentPool);
    }
    /** Native child authority is bounded to the actual first ID-zero image and its prescribed byte fields. */
    private void machineOtherImages(MachineInvocation invocation, boolean allowNativeChild) {
        int first = -1;
        for (var old : invocation.currentPool.slots()) if (old.id() == 0) { first = old.identity().wholeIndex(); break; }
        boolean changed = false;
        for (var old : invocation.currentPool.slots()) {
            int whole = old.identity().wholeIndex(); if (whole == invocation.whole) continue;
            var actual = invocation.pool.state(whole); if (old.equals(actual)) continue;
            boolean robotChild = invocation.generator && invocation.entry.routine() == 4;
            boolean pointsChild = !invocation.generator && invocation.entry.routine() == 0
                    && invocation.entry.unsignedByte(CdObjectState.ByteField.ROUTINE_2) == 0;
            if (!allowNativeChild || invocation.child >= 0 || whole != first || (!robotChild && !pointsChild))
                throw new IllegalStateException("Robot/Explosion changed an unrelated full64 physical image");
            var expected = robotChild ? old.moveByte(CdObjectState.ByteField.ID, CdExplosion.OBJECT_ID)
                    .moveByte(CdObjectState.ByteField.ROUTINE_2, 1)
                    .moveWord(CdObjectState.WordField.X, actual.centreX()).moveWord(CdObjectState.WordField.Y, actual.centreY())
                    : old.moveByte(CdObjectState.ByteField.ID, CdExplosion.POINTS_OBJECT_ID)
                    .moveWord(CdObjectState.WordField.X, invocation.entry.centreX()).moveWord(CdObjectState.WordField.Y, invocation.entry.centreY())
                    .moveByte(CdObjectState.ByteField.SUBTYPE, actual.subtype());
            if (!expected.equals(actual) || pointsChild && (actual.subtype() & 0x80) == 0)
                throw new IllegalStateException("Native first-free child changed unowned dirty bytes");
            invocation.child = whole; changed = true;
        }
        if (changed) invocation.currentPool = invocation.pool.capture();
    }
    private void machineSavedTail(MachineInvocation invocation) {
        byte[] expected = invocation.before.savedFlags().copy();
        var current = invocation.pool.state(invocation.whole);
        if (invocation.generator && current.id() == 0 && invocation.entry.savedFlagsId() != 0) {
            int period = invocation.globals.timeZone() & 0x7F;
            if ((invocation.globals.timeZone() & 0x80) != 0)
                period = Math.max(0, Math.min(2, (short) ((period - (byte) invocation.globals.timeWarpDirection()) & 0xFFFF)));
            int at = 2 + (short) ((3 * invocation.entry.savedFlagsId() + period) & 0xFFFF);
            if (at < 2 || at >= expected.length || !Arrays.equals(current.copyBytes(), new byte[64]))
                throw new IllegalStateException("Robot deletion differs from its reached saved/full64 tail");
            expected[at] &= 0x7F;
        }
        if (!Arrays.equals(expected, placement.capture().savedFlags().copy()))
            throw new IllegalStateException("Robot/Explosion changed an unowned saved byte");
    }
    private void verifyMachineFailure(MachineInvocation invocation, Throwable original) {
        try { verifyMachine(invocation); machineOtherImages(invocation, true); machineSavedTail(invocation); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }
    private CdObjectState validateMachineCut(MachineInvocation invocation, CdObjectPlacementRuntime.Snapshot cut,
                                              CdObjectState source, boolean allowNativeChild) {
        verifyMachine(invocation); placement.validateRestore(cut); machineOtherImages(invocation, allowNativeChild);
        var actual = placement.capture();
        if (source.id() != (invocation.generator ? CdRobotGenerator.OBJECT_ID : CdExplosion.OBJECT_ID)
                || !source.identity().equals(invocation.entry.identity()) || !source.equals(invocation.pool.state(invocation.whole))
                || !cut.pool().slots().get(invocation.whole - 32).equals(source)
                || !actual.pool().slots().equals(cut.pool().slots()) || !actual.status().equals(cut.status())
                || !sameBytes(actual.savedFlags(), cut.savedFlags()) || !sameBytes(cut.savedFlags(), invocation.before.savedFlags()))
            throw new IllegalStateException("Robot port differs from its actual fresh full placement/source cut");
        return source;
    }
    private Values machineValues(CdRingGlobals ring, LoadingGlobals globals) {
        var old = values;
        return new Values(old.phase(), old.section(), old.cpuPalette(), old.fadePalette(), old.displayedPalette(),
                old.cpuSat(), old.displayedSat(), old.displayedHScroll(), old.displayedVScrollLongBits(), old.foregroundPlane(),
                ring, old.level(), globals, old.hudObjects(), old.fadeProgress(), old.calls(), old.loop());
    }
    private void storeRobotGoodFuture(CdRobotGenerator.GoodFutureCall call) {
        Objects.requireNonNull(call); var invocation = requireMachine(call.expectedSource().identity().wholeIndex(), true);
        if (machinePort || drawingMachine || queuePort || invocation.goodStored || invocation.site != MachineSite.BODY
                || invocation.drawn || call.expectedGoodFuture() != values.globals().retainedDev().goodFuture()
                || call.value() != 1 || invocation.globals.goodFuture() != 0 || invocation.globals.timeZone() != 0
                || invocation.entry.routine() != 0 && invocation.entry.routine() != 2)
            throw new IllegalStateException("Robot goodFuture lacks its actual once-only hit store");
        var source = validateMachineCut(invocation, call.actualCut(), call.expectedSource(), false);
        if (source.routine() != 4 || source.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE) != 0
                || source.unsignedByte(CdObjectState.ByteField.COLLISION_STATUS) != 0
                || source.word(CdObjectState.WordField.ROBOT_GENERATOR_TIMER) != 0 || source.unsignedByte(CdObjectState.ByteField.MAP_FRAME) != 7)
            throw new IllegalStateException("Robot goodFuture omitted its reached collision/timer/frame prefix");
        var g = values.globals(); var ready = machineValues(values.ring(), new LoadingGlobals(g.live(), g.contact(), g.score(),
                g.drownTimerWord(), g.tail(), g.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(),
                new CdOpeningObjectRuntime.DevGlobals(1, g.retainedDev().projDestroyed())));
        machinePort = true;
        try { verifyMachine(invocation); values = ready; invocation.base = ready; invocation.goodStored = true;
            invocation.site = MachineSite.GOOD_FUTURE; verifyMachine(invocation); validateMachineCut(invocation, call.actualCut(), source, false); }
        finally { machinePort = false; }
    }
    private void addRobotPoints(CdRobotGenerator.PointsCall call) {
        Objects.requireNonNull(call); var invocation = requireMachine(call.expectedSource().identity().wholeIndex(), true);
        if (machinePort || drawingMachine || queuePort || !invocation.goodStored || invocation.pointsAdded
                || invocation.site != MachineSite.GOOD_FUTURE || invocation.drawn || call.points() != 150)
            throw new IllegalStateException("Robot AddPoints lacks the completed goodFuture store");
        validateMachineCut(invocation, call.actualCut(), call.expectedSource(), false);
        var old = values.ring(); var beforeScore = values.globals().score();
        var result = CdPlayerTouchResponse.addPoints(new CdPlayerTouchResponse.WorldGlobals(old.rings(), old.lives(),
                old.updateHudLives(), beforeScore.score(), beforeScore.nextLifeScore(), beforeScore.updateHudScore()), call.points());
        var w = result.world(); var g = values.globals();
        var readyRing = new CdRingGlobals(w.rings(), w.lives(), old.livesFlags(), old.updateHudRings(), w.updateHudLives(),
                old.timeStopTimer(), old.ringAnimationTimer(), old.ringAnimationFrame());
        var readyValues = machineValues(readyRing, new LoadingGlobals(g.live(), g.contact(),
                new CdOpeningObjectRuntime.ScoreGlobals(w.score(), w.nextLifeScore(), w.updateHudScore()), g.drownTimerWord(),
                g.tail(), g.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev()));
        var ledger = new ArrayList<>(sounds);
        result.command().ifPresent(command -> ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactSubCpuCommand(command))));
        var readySounds = List.copyOf(ledger); machinePort = true;
        try { verifyMachine(invocation); values = readyValues; sounds = readySounds;
            invocation.base = readyValues; invocation.ledger = readySounds; invocation.pointsAdded = true;
            invocation.site = MachineSite.POINTS; verifyMachine(invocation); validateMachineCut(invocation, call.actualCut(), call.expectedSource(), false); }
        finally { machinePort = false; }
    }
    private void commitRobotSolid(CdRobotGenerator.SolidCall call) {
        Objects.requireNonNull(call); var invocation = requireMachine(call.expectedSource().identity().wholeIndex(), true);
        if (machinePort || drawingMachine || queuePort || invocation.drawn || invocation.stopped
                || !call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Robot Solid has another actual player/global cut");
        boolean getOff = call.site() == CdRobotGenerator.Site.HIT_GET_OFF;
        if (call.site() == CdRobotGenerator.Site.ACTIVE_SOLID ? invocation.site != MachineSite.BODY || invocation.solidTaken
                : getOff ? invocation.site != MachineSite.SOLID || !invocation.getOffRequired || invocation.gotOff
                : invocation.site != MachineSite.POINTS || !invocation.pointsAdded || invocation.solidTaken)
            throw new IllegalStateException("Robot Solid/GetOff lacks its reached once-only native order");
        var source = validateMachineCut(invocation, call.actualCut(), call.expectedSource(), false);
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
        boolean stopped; boolean required = false;
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var r = done.completedResult(); object = r.object(); others = r.otherSlots(); writes = r.objectWrites(); requests = r.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), r); stopped = false;
            if (call.site() == CdRobotGenerator.Site.HIT_SOLID) required = r.d0().orElseThrow() != 0;
        } else {
            var p = ((CdSolidObject.NativeStopped) call.outcome()).prefix(); object = p.object(); others = p.otherSlots();
            writes = p.objectWrites(); requests = p.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), p); stopped = true;
        }
        validateSpringSolidImage(source, object, true, writes);
        var images = new ArrayList<CdObjectState>(); images.add(object);
        if (others.size() > 1) throw new IllegalStateException("Robot Solid released multiple stand pointers");
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || whole == invocation.whole || whole != call.expectedPlayer().standObject())
                throw new IllegalStateException("Robot Solid release is not its actual prior dynamic stand link");
            validateSpringSolidImage(call.actualCut().pool().slots().get(whole - 32), other, false, Set.of()); images.add(other);
        }
        var readyPool = CdObjectPool.overlay(call.actualCut().pool(), images); invocation.pool.validateRestore(readyPool);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger); machinePort = true;
        try {
            verifyMachine(invocation); validateMachineCut(invocation, call.actualCut(), source, false);
            frame.validatePreparedObjectSolidPublication(readyFrame);
            invocation.pool.restore(readyPool); frame.applyPreparedObjectSolidPublication(readyFrame); sounds = readySounds;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.ledger = readySounds;
            invocation.currentPool = readyPool; invocation.stopped = stopped; invocation.solidTaken = true;
            if (getOff) invocation.gotOff = true; else invocation.getOffRequired = required;
            invocation.site = getOff ? MachineSite.GET_OFF : MachineSite.SOLID;
            verifyMachine(invocation);
            if (!invocation.pool.capture().slots().equals(readyPool.slots())
                    || !sameBytes(placement.capture().savedFlags(), call.actualCut().savedFlags()))
                throw new IllegalStateException("Robot Solid omitted its actual complete pool/prepared Frame publication");
        } finally { machinePort = false; }
        // NativeStopped is rethrown identically by the original component immediately after this synchronous adoption.
    }
    private void playRobotFm(CdRobotGenerator.FmRequest call) {
        Objects.requireNonNull(call); var invocation = requireMachine(call.expectedSource().identity().wholeIndex(), true);
        if (machinePort || drawingMachine || queuePort || invocation.sounded || invocation.drawn || invocation.site != MachineSite.BODY
                || (call.nativeId() == 0x9E ? invocation.entry.routine() != 4 : call.nativeId() != 0xD9 || invocation.entry.routine() != 6))
            throw new IllegalStateException("Robot FM lacks its actual explosion/rebuild native site");
        var source = validateMachineCut(invocation, call.actualCut(), call.expectedSource(), call.nativeId() == 0x9E);
        if (call.nativeId() == 0x9E ? invocation.child < 0 : source.routine() != 0)
            throw new IllegalStateException("Robot FM precedes its actual child or rebuild prefix");
        var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastRobotGeneratorFm(call.nativeId()));
        var ready = List.copyOf(ledger); machinePort = true;
        try { verifyMachine(invocation); sounds = ready; invocation.ledger = ready; invocation.sounded = true;
            invocation.site = MachineSite.FM; verifyMachine(invocation); validateMachineCut(invocation, call.actualCut(), source, false); }
        finally { machinePort = false; }
    }
    private void drawRobot(CdRobotGenerator.DrawCall call) {
        Objects.requireNonNull(call); var invocation = requireMachine(call.expectedSource().identity().wholeIndex(), true);
        validateMachineCut(invocation, call.actualCut(), call.expectedSource(), true);
        if (invocation.goodStored && (!invocation.pointsAdded || !invocation.solidTaken || invocation.getOffRequired && !invocation.gotOff)
                || !invocation.goodStored && (invocation.entry.routine() == 0 || invocation.entry.routine() == 2)
                && invocation.globals.goodFuture() == 0 && invocation.globals.timeZone() == 0 && !invocation.solidTaken)
            throw new IllegalStateException("Robot Draw skipped a reached native hit/active prefix");
        drawMachine(invocation, call.expectedSource().identity());
    }
    private void drawExplosion(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireMachine(Objects.requireNonNull(identity).wholeIndex(), false);
        if (pool != invocation.pool) throw new IllegalStateException("Explosion retained another pool");
        machineOtherImages(invocation, true); machineSavedTail(invocation); drawMachine(invocation, identity);
    }
    private void drawMachine(MachineInvocation invocation, CdObjectState.Identity identity) {
        if (machinePort || drawingMachine || queuePort || invocation.drawn || invocation.stopped
                || !identity.equals(invocation.entry.identity())
                || invocation.pool.state(invocation.whole).id() != (invocation.generator ? CdRobotGenerator.OBJECT_ID : CdExplosion.OBJECT_ID))
            throw new IllegalStateException("Robot/Explosion Draw lacks its actual once-only current source");
        var source = invocation.pool.state(invocation.whole); invocation.drawSource = source;
        invocation.drawn = true; invocation.site = MachineSite.DRAW; drawingMachine = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    source.spriteFlags(), source.centreX(), source.centreY(), source.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    source.unsignedByte(CdObjectState.ByteField.WIDTH), source.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            verifyMachine(invocation);
            if (!invocation.screenCleared) throw new IllegalStateException("Robot/Explosion Draw omitted its actual BCLR7");
        } finally { drawingMachine = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readMachineDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = activeMachine;
        if (invocation == null) throw new IllegalStateException("No actual Robot/Explosion queue scope");
        requireMachine(Objects.requireNonNull(ref).wholeIndex(), invocation.generator);
        if (!drawingMachine || machinePort || queuePort || !invocation.drawn)
            throw new IllegalStateException("No actual Robot/Explosion collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var source = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(source.id(), source.spriteFlags(), source.centreX(), source.centreY(),
                    source.word(CdObjectState.WordField.X_FRACTION), source.longBits(CdObjectState.LongField.MAPPINGS),
                    source.unsignedByte(CdObjectState.ByteField.MAP_FRAME), source.word(CdObjectState.WordField.TILE));
            verifyMachine(invocation);
            if (!placement.capture().pool().slots().equals(before.pool().slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Robot/Explosion queue read changed full placement images");
            return live;
        } finally { queuePort = false; }
    }
    private void writeMachineOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = activeMachine;
        if (invocation == null) throw new IllegalStateException("No actual Robot/Explosion Draw scope");
        requireMachine(Objects.requireNonNull(ref).wholeIndex(), invocation.generator);
        if (!drawingMachine || machinePort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
            throw new IllegalStateException("Only actual Robot/Explosion Draw may clear bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("Robot/Explosion changed before its actual Draw BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready); queuePort = true;
        try {
            verifyMachine(invocation); invocation.pool.write(next); invocation.currentPool = ready; invocation.screenCleared = true;
            verifyMachine(invocation);
            if (!invocation.pool.capture().slots().equals(ready.slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Robot/Explosion Draw changed more than its actual source bit7");
        } finally { queuePort = false; }
    }

    /** One real cached ID3F call. This scope validates owners; Anton retains its native algorithm. */
    private final class AntonInvocation {
        private final CdAnton body = anton;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object stageSource = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdObjectPool.Snapshot initialPool = pool.capture();
        private final CdPlayerBody.State player = frame.state();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final Values base = values;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final List<SonicCdGameModule.PastSound> ledger = sounds;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdObjectDrawQueue.CameraWords camera;
        private final CdAnton.Globals globals;
        private CdObjectState drawSource;
        private boolean angleSeen, drawn, screenCleared;
        private AntonInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
            var gates = frame.environment().movement().gates();
            globals = new CdAnton.Globals(values.globals().retainedDev().goodFuture(), gates.timeZone(), gates.timeWarpDirection(),
                    camera.xWord(), frame.environment().movement().globals().primaryAngle());
        }
    }

    private void runAnton(int whole) {
        module.requirePastLoadingVisit(this);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeSpikes != null || spikesPort || drawingSpikes || !runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null || activeReserved != null
                || activeRing != null || activeSpring != null || activePlatform != null || activeFloat != null || activePata != null
                || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeAnton != null
                || activeTunnelDoor != null || activeMonitor != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || floatPort || drawingFloat || drawingPata
                || tamabbohPort || drawingTamabboh || collapsePort || drawingCollapse || boulderPort || drawingBoulder || mosquiPort || drawingMosqui
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || antonPort || drawingAnton || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdAnton.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Anton dispatch");
        var invocation = new AntonInvocation(whole); activeAnton = invocation;
        try {
            var result = anton.run(whole, invocation.globals);
            verifyAntonInvocation(invocation); requireAntonOtherImages(invocation);
            boolean conversion = invocation.globals.goodFuture() != 0
                    && (invocation.globals.timeZone() != 1 || invocation.entry.subtype() != 0);
            if (!result.state().equals(invocation.pool.state(whole))
                    || result.primaryAngle() != frame.environment().movement().globals().primaryAngle()
                    || (conversion ? invocation.drawn : !invocation.angleSeen || !invocation.drawn || !invocation.screenCleared))
                throw new IllegalStateException("Anton returned without its actual angle/Draw/source publications");
        } catch (RuntimeException | Error failure) { verifyAntonFailure(invocation, failure); throw failure; }
        finally { activeAnton = null; }
    }
    private AntonInvocation requireAntonInvocation(CdObjectPool pool, int whole) {
        var invocation = activeAnton;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual cached Anton callback at this physical pointer");
        verifyAntonInvocation(invocation); return invocation;
    }
    private void verifyAntonInvocation(AntonInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeSpikes != null || spikesPort || drawingSpikes || activeAnton != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null || activeSpring != null
                || activePlatform != null || activeFloat != null || activePata != null || activeTamabboh != null
                || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeTunnelDoor != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || drawingCollapse
                || boulderPort || drawingBoulder || mosquiPort || drawingMosqui || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation.body != anton || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.stageSource != frame.stageSource() || invocation.pool != placement.pool() || invocation.queue != drawQueue
                || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.base != values || values.phase() != BEGIN_LEVEL || invocation.ledger != sounds
                || !placement.status().equals(invocation.status) || !presentation.samePastScroll(invocation.scroll)
                || frame.state() != invocation.player || !frame.captureActor().equals(invocation.actor)
                || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Anton changed another owner or retained a stale actual World/Frame cut");
    }
    private void requireAntonOtherImages(AntonInvocation invocation) {
        for (var source : invocation.initialPool.slots()) if (source.identity().wholeIndex() != invocation.whole
                && !source.equals(invocation.pool.state(source.identity().wholeIndex())))
            throw new IllegalStateException("Anton changed an unrelated physical image");
    }
    private void verifyAntonReadOnly(AntonInvocation invocation, CdObjectPlacementRuntime.Snapshot before) {
        verifyAntonInvocation(invocation);
        var now = placement.capture();
        if (!now.pool().slots().equals(before.pool().slots()) || !now.status().equals(before.status())
                || !sameBytes(now.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Anton scalar callback changed actual full96/source/saved state");
    }
    private void verifyAntonFailure(AntonInvocation invocation, Throwable original) {
        try { verifyAntonInvocation(invocation); requireAntonOtherImages(invocation); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** Every genuine raw floor store, including same-value pre-zero, goes through the existing Frame owner once. */
    private void commitAntonPrimaryAngle(CdAnton.PrimaryAngleCall call) {
        Objects.requireNonNull(call);
        var invocation = requireAntonInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || antonPort || drawingAnton || queuePort || invocation.drawn)
            throw new IllegalStateException("Anton angle is outside its actual pre-Draw floor call");
        var pool = invocation.pool; var cut = call.callCut(); pool.validateRestore(cut);
        var source = pool.state(invocation.whole); requireAntonOtherImages(invocation);
        if (source.id() != CdAnton.OBJECT_ID || source.routine() != 0 && source.routine() != 2 && source.routine() != 4
                || !source.equals(call.expectedSource()) || !cut.slots().get(invocation.whole - 32).equals(source)
                || !pool.capture().slots().equals(cut.slots())
                || frame.environment().movement().globals().primaryAngle() != call.expectedPrimaryAngle())
            throw new IllegalStateException("Anton angle differs from its actual full96/source/current Frame byte");
        var before = placement.capture();
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(),
                monitorAngleEnvironment(invocation.shared.environment(), call.value()), invocation.shared.artState(),
                invocation.shared.paused(), invocation.shared.vIntClock());
        antonPort = true;
        try {
            verifyAntonReadOnly(invocation, before);
            frame.applyObjectPrimaryAngle(call.value()); invocation.shared = readyShared; invocation.angleSeen = true;
            verifyAntonReadOnly(invocation, before);
        } catch (RuntimeException | Error original) {
            try { verifyAntonReadOnly(invocation, before); }
            catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
            throw original;
        } finally { antonPort = false; }
    }

    private void drawAnton(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireAntonInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || antonPort || drawingAnton || queuePort || invocation.drawn || !invocation.angleSeen
                || !identity.equals(invocation.entry.identity()) || pool.state(invocation.whole).id() != CdAnton.OBJECT_ID)
            throw new IllegalStateException("Anton DrawObject lacks its genuine live physical source");
        requireAntonOtherImages(invocation);
        invocation.drawSource = pool.state(invocation.whole); invocation.drawn = true; drawingAnton = true;
        var current = invocation.drawSource;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH), current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            verifyAntonInvocation(invocation);
            if (!invocation.screenCleared) throw new IllegalStateException("Anton skipped its actual DrawObject BCLR7");
        } catch (RuntimeException | Error failure) { verifyAntonFailure(invocation, failure); throw failure; }
        finally { drawingAnton = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readAntonDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireAntonInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingAnton || antonPort || queuePort || !invocation.drawn)
            throw new IllegalStateException("No actual Anton collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var source = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(source.id(), source.spriteFlags(), source.centreX(), source.centreY(),
                    source.word(CdObjectState.WordField.X_FRACTION), source.longBits(CdObjectState.LongField.MAPPINGS),
                    source.unsignedByte(CdObjectState.ByteField.MAP_FRAME), source.word(CdObjectState.WordField.TILE));
            verifyAntonReadOnly(invocation, before); return live;
        } catch (RuntimeException | Error failure) { verifyAntonFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }
    private void writeAntonOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireAntonInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingAnton || antonPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
            throw new IllegalStateException("Only actual Anton DrawObject may clear bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("Anton changed its Draw source before BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready);
        queuePort = true;
        try {
            verifyAntonReadOnly(invocation, before);
            invocation.pool.write(next); invocation.screenCleared = true;
            verifyAntonInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(ready.slots())
                    || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Anton Draw changed more than its actual source sprite byte");
        } catch (RuntimeException | Error failure) { verifyAntonFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }

    /** One cached Boulder visit; actual producer scope, not public values, authorizes publication. */
    private final class BoulderInvocation {
        private final CdBoulder body = boulder;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object stageSource = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final Values base = values;
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdSolidObject.Outcome adopted;
        private CdObjectState drawSource;
        private boolean solidTaken, stopped, drawn, screenCleared;
        private BoulderInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runBoulder(int whole) {
        module.requirePastLoadingVisit(this);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activeDrain != null || activePrimary != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null
                || activeTunnelDoor != null || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || drawingCollapse
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || boulderPort || spikesPort || drawingBoulder || mosquiPort || drawingMosqui || drawingSpikes || antonPort || drawingAnton || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdBoulder.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Boulder dispatch");
        var invocation = new BoulderInvocation(whole); activeBoulder = invocation;
        try {
            var environment = frame.environment(); var gates = environment.movement().gates();
            var result = boulder.run(whole, new CdBoulder.Globals(gates.timeZone(), gates.timeWarpDirection(), invocation.camera.xWord()),
                    new CdBoulder.SolidInputs(springPlayer(), new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), springGlobals()));
            verifyBoulderInvocation(invocation); requireBoulderOtherImages(invocation);
            if (invocation.stopped || !invocation.drawn || !invocation.screenCleared
                    || !result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("Boulder returned without its actual completed Solid/Draw/source cut");
        } catch (RuntimeException | Error failure) {
            if (invocation.adopted instanceof CdSolidObject.NativeStopped nativeStop) {
                var original = nativeStop.stop().originalException();
                if (failure != original) original.addSuppressed(failure);
                verifyBoulderFailure(invocation, original); throw original;
            }
            verifyBoulderFailure(invocation, failure); throw failure;
        } finally { activeBoulder = null; }
    }

    private BoulderInvocation requireBoulderInvocation(CdObjectPool pool, int whole) {
        var invocation = activeBoulder;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual cached Boulder callback at this physical pointer");
        verifyBoulderInvocation(invocation); return invocation;
    }
    private void verifyBoulderInvocation(BoulderInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeBoulder != invocation || activeAnton != null || !runningObjects || servicing || loadingSaved
                || activeDrain != null || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null
                || activeTunnelDoor != null || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || drawingCollapse
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation.body != boulder || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.stageSource != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.base != values || values.phase() != BEGIN_LEVEL || invocation.expectedSounds != sounds
                || !placement.status().equals(invocation.status) || !presentation.samePastScroll(invocation.scroll)
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Boulder changed another owner or retained a stale actual World/Frame cut");
    }
    private void requireBoulderOtherImages(BoulderInvocation invocation) {
        for (var source : invocation.currentPool.slots()) if (source.identity().wholeIndex() != invocation.whole
                && !source.equals(invocation.pool.state(source.identity().wholeIndex())))
            throw new IllegalStateException("Boulder changed an unrelated physical image");
    }
    private void verifyBoulderFailure(BoulderInvocation invocation, Throwable original) {
        try { verifyBoulderInvocation(invocation); requireBoulderOtherImages(invocation); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** Prepare every pool/Frame/ledger value before publishing the genuine current plain-Solid outcome. */
    private void commitBoulderSolid(CdBoulder.PrefixSolidCall call) {
        Objects.requireNonNull(call);
        var invocation = requireBoulderInvocation(placement.pool(), activeBoulder == null ? -1 : activeBoulder.whole);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || boulderPort || spikesPort || drawingBoulder || mosquiPort || drawingMosqui || drawingSpikes || antonPort || drawingAnton || queuePort || invocation.solidTaken || invocation.stopped || invocation.drawn)
            throw new IllegalStateException("Boulder Solid lacks its actual once-only pre-Draw helper cut");
        var pool = invocation.pool; var cut = call.callCut(); pool.validateRestore(cut);
        var source = pool.state(invocation.whole);
        requireBoulderOtherImages(invocation);
        if (!pool.capture().slots().equals(cut.slots()) || !source.equals(cut.slots().get(invocation.whole - 32))
                || source.id() != CdBoulder.OBJECT_ID || source.routine() != 2 || source.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) >= 0
                || !call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Boulder Solid differs from its actual full96/source/player/global cut");
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
        boolean stopped;
        if (call.outcome() instanceof CdSolidObject.Completed completed) {
            var result = completed.completedResult();
            if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT) throw new IllegalStateException("Boulder completed a different helper");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result); stopped = false;
        } else {
            var prefix = ((CdSolidObject.NativeStopped) call.outcome()).prefix();
            if (prefix.entry() != CdSolidObject.Entry.SOLID_OBJECT) throw new IllegalStateException("Boulder stopped a different helper");
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix); stopped = true;
        }
        validateSpringSolidImage(source, object, true, writes);
        if (writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0)
            throw new IllegalStateException("Boulder plain Solid cannot store Top/Bottom type bytes");
        var images = new ArrayList<CdObjectState>(); images.add(object);
        if (others.size() > 1) throw new IllegalStateException("Boulder Solid released more than one native stand link");
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || whole == invocation.whole || whole != call.expectedPlayer().standObject())
                throw new IllegalStateException("Boulder stand release lacks its actual dynamic prior link");
            var beforeOther = cut.slots().get(whole - 32);
            if (!beforeOther.moveByte(CdObjectState.ByteField.FLAGS, beforeOther.flags() & ~8).equals(other))
                throw new IllegalStateException("Boulder stand release changed more than its actual linked flag bit");
            images.add(other);
        }
        var readyPool = CdObjectPool.overlay(cut, images); pool.validateRestore(readyPool);
        var before = placement.capture();
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger);
        boulderPort = true;
        try {
            verifyBoulderInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Boulder source changed before prepared publication");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.currentPool = readyPool; invocation.solidTaken = true; invocation.stopped = stopped; invocation.adopted = call.outcome();
            verifyBoulderInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Boulder Solid did not adopt its complete output96 and saved nonwrites");
        } catch (RuntimeException | Error failure) { verifyBoulderFailure(invocation, failure); throw failure; }
        finally { boulderPort = false; }
    }

    private void drawBoulder(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireBoulderInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || boulderPort || spikesPort || drawingBoulder || mosquiPort || drawingMosqui || drawingSpikes || antonPort || drawingAnton || queuePort || invocation.drawn || invocation.stopped
                || !identity.equals(invocation.entry.identity()) || pool.state(invocation.whole).id() != CdBoulder.OBJECT_ID)
            throw new IllegalStateException("Boulder DrawObject lacks its genuine live physical source");
        requireBoulderOtherImages(invocation);
        invocation.drawSource = pool.state(invocation.whole); invocation.drawn = true; drawingBoulder = true;
        var current = invocation.drawSource;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH), current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            verifyBoulderInvocation(invocation);
            if (!invocation.screenCleared) throw new IllegalStateException("Boulder skipped its actual DrawObject BCLR7");
        } catch (RuntimeException | Error failure) { verifyBoulderFailure(invocation, failure); throw failure; }
        finally { drawingBoulder = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readBoulderDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireBoulderInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingBoulder || antonPort || drawingAnton || boulderPort || spikesPort || queuePort || !invocation.drawn || invocation.stopped)
            throw new IllegalStateException("No actual Boulder collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var source = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(source.id(), source.spriteFlags(), source.centreX(), source.centreY(),
                    source.word(CdObjectState.WordField.X_FRACTION), source.longBits(CdObjectState.LongField.MAPPINGS),
                    source.unsignedByte(CdObjectState.ByteField.MAP_FRAME), source.word(CdObjectState.WordField.TILE));
            verifyBoulderInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(before.pool().slots())
                    || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Boulder queue read changed actual full96/saved state");
            return live;
        } catch (RuntimeException | Error failure) { verifyBoulderFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }
    private void writeBoulderOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireBoulderInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingBoulder || antonPort || drawingAnton || boulderPort || spikesPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn || invocation.stopped)
            throw new IllegalStateException("Only actual Boulder DrawObject may clear bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("Boulder changed its native Draw source before BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready);
        queuePort = true;
        try {
            verifyBoulderInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(before.pool().slots())) throw new IllegalStateException("Stale Boulder Draw pool");
            invocation.pool.write(next); invocation.currentPool = ready; invocation.screenCleared = true;
            verifyBoulderInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(ready.slots())
                    || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Boulder Draw changed more than its exact source sprite byte");
        } catch (RuntimeException | Error failure) { verifyBoulderFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }

    /** One cached Spikes visit; actual producer scope, not public values, authorizes publication. */
    private final class SpikesInvocation {
        private final CdSpikes body = spikes;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object stageSource = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private Values base = values;
        private CdPlayerBody.State expectedState = frame.state();
        private CdPlayerBody.Environment expectedEnvironment = frame.environment();
        private final CdObjectPlacementRuntime.Status status = placement.status();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdObjectDrawQueue.CameraWords camera;
        private CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdSolidObject.Outcome adopted;
        private CdObjectState drawSource;
        private boolean solidTaken, stopped, drawn, screenCleared, yRewound, hurtTaken;
        private SpikesInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runSpikes(int whole) {
        module.requirePastLoadingVisit(this);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeMosqui != null || mosquiPort || drawingMosqui || activeAnton != null || antonPort || drawingAnton || !runningObjects || servicing || loadingSaved || activeDrain != null || activePrimary != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeSpikes != null
                || activeTunnelDoor != null || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || drawingCollapse
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || spikesPort || drawingSpikes || queuePort
                || boulderPort || drawingBoulder || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdSpikes.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Spikes dispatch");
        var invocation = new SpikesInvocation(whole); activeSpikes = invocation;
        try {
            var environment = frame.environment(); var gates = environment.movement().gates();
            var result = spikes.run(whole, new CdSpikes.Globals(gates.timeZone(), gates.timeWarpDirection(), invocation.camera.xWord()),
                    new CdSpikes.Inputs(springPlayer(), new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()),
                            springGlobals(), spikesPlayerGlobals(), spikesWorldGlobals()));
            verifySpikesInvocation(invocation); requireSpikesOtherImages(invocation);
            if (invocation.stopped || !invocation.drawn || !invocation.screenCleared || invocation.yRewound != invocation.hurtTaken
                    || !result.player().equals(springPlayer()) || !result.solidGlobals().equals(springGlobals())
                    || !result.playerGlobals().equals(spikesPlayerGlobals())
                    || !result.worldGlobals().equals(spikesWorldGlobals()) || !result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("Spikes returned without its actual completed Solid/Draw/source cut");
        } catch (RuntimeException | Error failure) {
            verifySpikesFailure(invocation, failure); throw failure;
        } finally { activeSpikes = null; }
    }

    private SpikesInvocation requireSpikesInvocation(CdObjectPool pool, int whole) {
        var invocation = activeSpikes;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual cached Spikes callback at this physical pointer");
        verifySpikesInvocation(invocation); return invocation;
    }
    private void verifySpikesInvocation(SpikesInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeMosqui != null || mosquiPort || drawingMosqui || activeAnton != null || antonPort || drawingAnton || activeSpikes != invocation || activeBoulder != null || boulderPort || drawingBoulder || !runningObjects || servicing || loadingSaved
                || activeDrain != null || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null
                || activeTunnelDoor != null || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || drawingCollapse
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation.body != spikes || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.stageSource != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.base != values || values.phase() != BEGIN_LEVEL || invocation.expectedSounds != sounds
                || !placement.status().equals(invocation.status) || !presentation.samePastScroll(invocation.scroll)
                || !frame.state().equals(invocation.expectedState) || !frame.environment().equals(invocation.expectedEnvironment)
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Spikes changed another owner or retained a stale actual World/Frame cut");
    }
    private void requireSpikesOtherImages(SpikesInvocation invocation) {
        for (var source : invocation.currentPool.slots()) if (source.identity().wholeIndex() != invocation.whole
                && !source.equals(invocation.pool.state(source.identity().wholeIndex())))
            throw new IllegalStateException("Spikes changed an unrelated physical image");
    }
    private void verifySpikesFailure(SpikesInvocation invocation, Throwable original) {
        try { verifySpikesInvocation(invocation); requireSpikesOtherImages(invocation); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** Prepare every pool/Frame/ledger value before publishing the genuine current plain-Solid outcome. */
    private void commitSpikesSolid(CdSpikes.PrefixSolidCall call) {
        Objects.requireNonNull(call);
        var invocation = requireSpikesInvocation(placement.pool(), activeSpikes == null ? -1 : activeSpikes.whole);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || spikesPort || drawingSpikes || queuePort || invocation.solidTaken || invocation.stopped || invocation.drawn)
            throw new IllegalStateException("Spikes Solid lacks its actual once-only pre-Draw helper cut");
        var pool = invocation.pool; var cut = call.actualCut(); pool.validateRestore(cut);
        var source = pool.state(invocation.whole);
        requireSpikesOtherImages(invocation);
        if (!pool.capture().slots().equals(cut.slots()) || !source.equals(cut.slots().get(invocation.whole - 32))
                || source.id() != CdSpikes.OBJECT_ID || source.routine() != 2
                || !call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Spikes Solid differs from its actual full96/source/player/global cut");
        int playerY = call.expectedPlayer().actor().motion().centreY16_16() >>> 16;
        int distance = (playerY - source.centreY()) & 0xFFFF;
        if (playerY < source.centreY()) distance = (-distance) & 0xFFFF;
        if (distance >= 0x20) throw new IllegalStateException("Spikes Solid is outside its actual native pre-call absDY gate");
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
        boolean stopped;
        if (call.outcome() instanceof CdSolidObject.Completed completed) {
            var result = completed.completedResult();
            if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT) throw new IllegalStateException("Spikes completed a different helper");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result); stopped = false;
        } else {
            var prefix = ((CdSolidObject.NativeStopped) call.outcome()).prefix();
            if (prefix.entry() != CdSolidObject.Entry.SOLID_OBJECT) throw new IllegalStateException("Spikes stopped a different helper");
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix); stopped = true;
        }
        validateSpringSolidImage(source, object, true, writes);
        if (writes.contains(CdObjectState.ByteField.SOLID_TYPE)
                && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) != 0)
            throw new IllegalStateException("Spikes plain Solid cannot store Top/Bottom type bytes");
        var images = new ArrayList<CdObjectState>(); images.add(object);
        if (others.size() > 1) throw new IllegalStateException("Spikes Solid released more than one native stand link");
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || whole == invocation.whole || whole != call.expectedPlayer().standObject())
                throw new IllegalStateException("Spikes stand release lacks its actual dynamic prior link");
            var beforeOther = cut.slots().get(whole - 32);
            if (!beforeOther.moveByte(CdObjectState.ByteField.FLAGS, beforeOther.flags() & ~8).equals(other))
                throw new IllegalStateException("Spikes stand release changed more than its actual linked flag bit");
            images.add(other);
        }
        var readyPool = CdObjectPool.overlay(cut, images); pool.validateRestore(readyPool);
        var before = placement.capture();
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds);
        for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger);
        spikesPort = true;
        try {
            verifySpikesInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Spikes source changed before prepared publication");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            sounds = readySounds;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.expectedState = readyFrame.finalState(); invocation.expectedEnvironment = readyFrame.finalEnvironment();
            invocation.currentPool = readyPool; invocation.solidTaken = true; invocation.stopped = stopped; invocation.adopted = call.outcome();
            verifySpikesInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Spikes Solid did not adopt its complete output96 and saved nonwrites");
        } catch (RuntimeException | Error failure) { verifySpikesFailure(invocation, failure); throw failure; }
        finally { spikesPort = false; }
    }

    private void drawSpikes(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireSpikesInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || spikesPort || drawingSpikes || queuePort || invocation.drawn || invocation.stopped
                || invocation.yRewound != invocation.hurtTaken || !identity.equals(invocation.entry.identity()) || pool.state(invocation.whole).id() != CdSpikes.OBJECT_ID)
            throw new IllegalStateException("Spikes DrawObject lacks its genuine live physical source");
        requireSpikesOtherImages(invocation);
        invocation.drawSource = pool.state(invocation.whole); invocation.drawn = true; drawingSpikes = true;
        var current = invocation.drawSource;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH), current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            verifySpikesInvocation(invocation);
            if (!invocation.screenCleared) throw new IllegalStateException("Spikes skipped its actual DrawObject BCLR7");
        } catch (RuntimeException | Error failure) { verifySpikesFailure(invocation, failure); throw failure; }
        finally { drawingSpikes = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readSpikesDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireSpikesInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingSpikes || spikesPort || queuePort || !invocation.drawn || invocation.stopped)
            throw new IllegalStateException("No actual Spikes collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var source = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(source.id(), source.spriteFlags(), source.centreX(), source.centreY(),
                    source.word(CdObjectState.WordField.X_FRACTION), source.longBits(CdObjectState.LongField.MAPPINGS),
                    source.unsignedByte(CdObjectState.ByteField.MAP_FRAME), source.word(CdObjectState.WordField.TILE));
            verifySpikesInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(before.pool().slots())
                    || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Spikes queue read changed actual full96/saved state");
            return live;
        } catch (RuntimeException | Error failure) { verifySpikesFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }
    private void writeSpikesOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireSpikesInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingSpikes || spikesPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn || invocation.stopped)
            throw new IllegalStateException("Only actual Spikes DrawObject may clear bit7 once");
        var before = placement.capture(); var source = invocation.pool.state(invocation.whole);
        if (!source.equals(invocation.drawSource)) throw new IllegalStateException("Spikes changed its native Draw source before BCLR7");
        var next = source.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, source.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready);
        queuePort = true;
        try {
            verifySpikesInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(before.pool().slots())) throw new IllegalStateException("Stale Spikes Draw pool");
            invocation.pool.write(next); invocation.currentPool = ready; invocation.screenCleared = true;
            verifySpikesInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(ready.slots())
                    || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Spikes Draw changed more than its exact source sprite byte");
        } catch (RuntimeException | Error failure) { verifySpikesFailure(invocation, failure); throw failure; }
        finally { queuePort = false; }
    }

    private CdPlayerTouchResponse.PlayerGlobals spikesPlayerGlobals() {
        var environment = frame.environment(); var body = environment.globals(); var movement = environment.movement();
        var contact = values.globals().contact();
        return new CdPlayerTouchResponse.PlayerGlobals(contact.timeWarp(), body.invincible(), contact.shield(),
                contact.combineRing(), body.debugCheat(), body.debugMode(), movement.globals().scoreChain(), movement.gates().miniSonic());
    }
    private CdPlayerTouchResponse.WorldGlobals spikesWorldGlobals() {
        var ring = values.ring(); var score = values.globals().score();
        return new CdPlayerTouchResponse.WorldGlobals(ring.rings(), ring.lives(), ring.updateHudLives(),
                score.score(), score.nextLifeScore(), score.updateHudScore());
    }
    private SpikesInvocation requireSpikesMutation(CdObjectState.Identity identity) {
        var invocation = requireSpikesInvocation(placement.pool(), Objects.requireNonNull(identity).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || spikesPort || drawingSpikes || queuePort || invocation.stopped || invocation.drawn
                || !identity.equals(invocation.entry.identity())
                || placement.pool().state(invocation.whole).id() != CdSpikes.OBJECT_ID
                || placement.pool().state(invocation.whole).routine() != 2)
            throw new IllegalStateException("Spikes mutation lacks its actual live once-only Main cut");
        if (!invocation.pool.capture().slots().equals(invocation.currentPool.slots()))
            throw new IllegalStateException("Spikes callback changed a source64 or other full96 image between native cuts");
        return invocation;
    }
    /** Source .ChkStand predicates AFTER the actual completed Solid, with no repeated pre-call Y test. */
    private void requireSpikesHurtGates(SpikesInvocation invocation) {
        if (!invocation.solidTaken || !(invocation.adopted instanceof CdSolidObject.Completed completed)
                || completed.completedResult().d0().orElseThrow() == 0)
            throw new IllegalStateException("Spikes Hurt has no actual completed nonzero plain-Solid return");
        var live = invocation.pool.state(invocation.whole); var actor = springPlayer().actor();
        if ((live.spriteFlags() & 2) != 0 || (live.flags() & 8) == 0 || values.globals().contact().timeWarp() != 0
                || frame.environment().globals().invincible() != 0 || actor.motion().routine() >= 4 || actor.hurt() != 0)
            throw new IllegalStateException("Spikes Hurt gates differ from the current native post-Solid actor/contact");
    }

    /** Native standalone LONG-Y prefix: one Frame owner only; no pool, contact, ledger or world write.
     * Paired Frame source a5825d2080e47af8647391136395b4de5682d7c1a333275e0f85598fb2869f46
     * prepares every State/Environment value before adoption; no Frame aggregate is rebuilt here.
     */
    private void commitSpikesYRewind(CdSpikes.YRewindCall call) {
        Objects.requireNonNull(call);
        var invocation = requireSpikesMutation(call.sourceIdentity()); requireSpikesHurtGates(invocation);
        if (invocation.yRewound || invocation.hurtTaken || !call.expectedActor().equals(springPlayer().actor()))
            throw new IllegalStateException("Spikes Y rewind is duplicated or has another current actor");
        var before = placement.capture(); var ledger = sounds;
        var readyFrame = frame.prepareObjectPreHurtYRewind(call.expectedActor(), call.finalYBits());
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        spikesPort = true;
        try {
            verifySpikesInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(before.pool().slots())) throw new IllegalStateException("Stale Spikes Y source pool");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            invocation.actor = readyActor; invocation.shared = readyShared;
            invocation.expectedState = readyFrame.finalState(); invocation.expectedEnvironment = readyFrame.finalEnvironment();
            invocation.yRewound = true;
            verifySpikesInvocation(invocation);
            if (sounds != ledger || !invocation.pool.capture().slots().equals(before.pool().slots())
                    || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Spikes Y prefix changed an unowned pool/saved/ledger value");
        } catch (RuntimeException | Error failure) { verifySpikesFailure(invocation, failure); throw failure; }
        finally { spikesPort = false; }
    }

    /** Actual direct Hurt after the Y prefix, never the touch scanner or a second Hurt invocation.
     * All pool, Frame, contact and ordered typed sound values are prepared before owner assignments.
     * prepareObjectHurtResult/3 is supplied by the verified paired Frame source; no Frame aggregate is reconstructed.
     */
    private void commitSpikesHurt(CdSpikes.HurtCall call) {
        Objects.requireNonNull(call);
        var invocation = requireSpikesMutation(call.sourceA2().identity()); requireSpikesHurtGates(invocation);
        if (!invocation.yRewound || invocation.hurtTaken) throw new IllegalStateException("Spikes Hurt has no single completed Y prefix");
        var pool = invocation.pool; var cut = call.callCut(); pool.validateRestore(cut);
        var live = pool.state(invocation.whole); var result = call.result();
        var expectedPlayer = call.expectedPlayer(); var expectedWorld = call.expectedWorld();
        if (!pool.capture().slots().equals(cut.slots()) || !live.equals(call.sourceA2())
                || !live.equals(cut.slots().get(invocation.whole - 32)) || !call.expectedActor().equals(springPlayer().actor())
                || !expectedPlayer.equals(spikesPlayerGlobals()) || !expectedWorld.equals(spikesWorldGlobals()))
            throw new IllegalStateException("Spikes direct Hurt has a stale source96/actor/contact/ring/score cut");
            if (!result.world().equals(expectedWorld)) {
                throw new IllegalArgumentException("Direct HurtPlayer cannot change rings/lives/HUD/score world values");
            }
            boolean find = expectedPlayer.shield() == 0 && expectedWorld.rings() != 0;
            boolean kill = expectedPlayer.shield() == 0 && expectedWorld.rings() == 0 && expectedPlayer.debugCheat() == 0;
            var exit = kill ? (expectedPlayer.debugMode() == 0 ? CdPlayerTouchResponse.Exit.KILL_PLAYER
                    : CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE) : CdPlayerTouchResponse.Exit.HURT;
            if (result.exit() != exit) throw new IllegalArgumentException("Direct HurtPlayer exit disagrees with the current native branch");
            var player = result.player();
            int shield = exit == CdPlayerTouchResponse.Exit.HURT ? expectedPlayer.shield() & ~1 : expectedPlayer.shield();
            int combine = exit == CdPlayerTouchResponse.Exit.HURT && (expectedPlayer.shield() & 1) == 0 ? 0 : expectedPlayer.combineRing();
            if (player.timeWarp() != expectedPlayer.timeWarp() || player.shield() != shield || player.combineRing() != combine
                    || player.debugCheat() != expectedPlayer.debugCheat() || player.debugMode() != expectedPlayer.debugMode()
                    || player.miniSonic() != expectedPlayer.miniSonic()) {
                throw new IllegalArgumentException("Direct HurtPlayer changes an unowned global/contact value");
            }
            // Precreate the complete immutable Root contact value before ANY response writes.
            var nextContact = new CdPlayerBody.ContactGlobals(expectedPlayer.timeWarp(), shield, combine);
            var firstFree = OptionalInt.empty();
            if (find) {
                // FindObjSlot (Object Spawner.asm:329-340) scans ALL96 ID bytes in physical order, without clearing.
                for (var image : cut.slots()) if (image.id() == 0) { firstFree = OptionalInt.of(image.identity().wholeIndex()); break; }
            }
            var expectedCalls = new ArrayList<CdPlayerTouchResponse.Call>();
            if (find) expectedCalls.add(new CdPlayerTouchResponse.FindObjSlot(firstFree));
            if (exit != CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE) {
                expectedCalls.add(new CdPlayerTouchResponse.ResetOnFloor());
                if ((call.expectedActor().motion().flags() & 4) != 0) {
                    expectedCalls.add(new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP));
                }
                if (exit == CdPlayerTouchResponse.Exit.KILL_PLAYER) {
                    expectedCalls.add(new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT));
                }
            }
            // Exact observations forbid AddPoints/SubCPU/FM_DESTROY, duplicate allocation/reset, wrong FM/order.
            if (!result.calls().equals(expectedCalls)) {
                throw new IllegalArgumentException("Direct HurtPlayer native call observations disagree with the reached branch");
            }
            var images = result.changedSlots();
            var readyPool = CdObjectPool.overlay(cut, images); pool.validateRestore(readyPool);
            if (images.size() != (firstFree.isPresent() ? 1 : 0)) {
                throw new IllegalArgumentException("Direct HurtPlayer publishes only the actual optional first-free LostRing image");
            }
            if (firstFree.isPresent()) {
                int whole = firstFree.getAsInt(); var before = cut.slots().get(whole - CdObjectPool.FIRST_WHOLE_INDEX);
                var expected = before.moveByte(CdObjectState.ByteField.ID, CdLostRing.OBJECT_ID)
                        .moveWord(CdObjectState.WordField.X, call.expectedActor().motion().centreX16_16() >>> 16)
                        .moveWord(CdObjectState.WordField.Y, call.expectedActor().motion().centreY16_16() >>> 16);
                if (whole == invocation.whole || !images.getFirst().equals(expected)) {
                    throw new IllegalArgumentException("LostRing allocation may write only ID/X.w/Y.w over the exact dirty first-free image");
                }
            }
        var readyFrame = frame.prepareObjectHurtResult(call.expectedActor(), expectedPlayer, result);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(),
                invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var old = values; var g = old.globals();
        var nextGlobals = new LoadingGlobals(g.live(), nextContact, g.score(), g.drownTimerWord(), g.tail(),
                g.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit(), g.retainedDev());
        // Direct Hurt's world result is required identical above. Ring/score ownership is retained, never defaulted.
        var readyValues = new Values(old.phase(), old.section(), old.cpuPalette(), old.fadePalette(), old.displayedPalette(),
                old.cpuSat(), old.displayedSat(), old.displayedHScroll(), old.displayedVScrollLongBits(), old.foregroundPlane(),
                old.ring(), old.level(), nextGlobals, old.hudObjects(), old.fadeProgress(), old.calls(), old.loop());
        var ledger = new ArrayList<>(sounds);
        for (var observation : result.calls()) if (observation instanceof CdPlayerTouchResponse.PlayFm fm)
            ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm.sound())));
        var readySounds = List.copyOf(ledger); var before = placement.capture();
        spikesPort = true;
        try {
            verifySpikesInvocation(invocation);
            if (!pool.capture().slots().equals(cut.slots())) throw new IllegalStateException("Stale Spikes Hurt pool before prepared adoption");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            pool.restore(readyPool);
            frame.applyPreparedObjectSolidPublication(readyFrame);
            values = readyValues; sounds = readySounds;
            invocation.base = readyValues; invocation.expectedSounds = readySounds;
            invocation.actor = readyActor; invocation.shared = readyShared;
            invocation.expectedState = readyFrame.finalState(); invocation.expectedEnvironment = readyFrame.finalEnvironment();
            invocation.currentPool = readyPool; invocation.hurtTaken = true;
            verifySpikesInvocation(invocation);
            if (!pool.capture().slots().equals(readyPool.slots()) || !sameBytes(placement.capture().savedFlags(), before.savedFlags()))
                throw new IllegalStateException("Spikes direct Hurt did not adopt exact output96/saved nonwrites");
        } catch (RuntimeException | Error failure) { verifySpikesFailure(invocation, failure); throw failure; }
        finally { spikesPort = false; }
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeFloat != null || activePata != null || activeTunnelDoor != null || activeMonitor != null
                || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || floatPort || drawingFloat
                || drawingPata || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || queuePort || whole < 32 || whole >= 128
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeTamabboh != invocation || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || !runningObjects || servicing || loadingSaved
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || queuePort || invocation.drawn || invocation.expectedRangeX != null)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || queuePort || invocation.drawn)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || queuePort || invocation.drawn || invocation.sounded)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || queuePort || invocation.drawn || !identity.equals(invocation.entry.identity()))
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || tamabbohPort || queuePort || !invocation.drawn)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || tamabbohPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
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

    /** Cached B Mosqui producer scope; public records/equal cuts cannot mint this authority. */
    private enum MosquiCut { BODY, FLOOR, SOUNDED, DRAW }
    private final class MosquiInvocation {
        private final CdMosqui body = mosqui;
        private final CdPlayerFrameRuntime sourceFrame = frame;
        private final CdR11bAssets sourceAssets = assets;
        private final Object stageSource = frame.stageSource();
        private final CdObjectPool pool = placement.pool();
        private final CdObjectDrawQueue queue = drawQueue;
        private final CdPlayerBody.State player = frame.state();
        private final CdPlayerFrameRuntime.ActorState actor = frame.captureActor();
        private CdPlayerFrameRuntime.SharedState shared = frame.captureShared();
        private final CdObjectPlacementRuntime.Snapshot before = placement.capture();
        private final SonicCdPlayerPresentation.ScrollCapture scroll = presentation.capturePastScroll();
        private final Values baseValues = values;
        private List<SonicCdGameModule.PastSound> expectedSounds = sounds;
        private final Object world;
        private final long generation;
        private final int whole;
        private final CdObjectState entry;
        private final CdMosqui.Globals globals;
        private final CdObjectDrawQueue.CameraWords camera;
        private MosquiCut next = MosquiCut.BODY;
        private CdObjectState drawSource;
        private int angleStores;
        private boolean drawn, screenCleared, sounded;
        private MosquiInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var fg = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(fg.cameraX(), fg.cameraYWord());
            var environment = frame.environment(); var gates = environment.movement().gates();
            globals = new CdMosqui.Globals(values.globals().retainedDev().goodFuture(), gates.timeZone(),
                    gates.timeWarpDirection(), environment.globals().debugMode(), camera.xWord(),
                    player.motion().centreX16_16() >>> 16, player.motion().centreY16_16() >>> 16);
        }
    }

    private void runMosqui(int whole) {
        module.requirePastLoadingVisit(this);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeSpikes != null || spikesPort || drawingSpikes || !runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeCollapse != null || activeFloat != null || activePata != null || activeTamabboh != null
                || activeBoulder != null || activeMosqui != null || activeAnton != null || activeTunnelDoor != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || collapsePort || drawingCollapse || floatPort || drawingFloat
                || drawingPata || tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder || antonPort || drawingAnton
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || mosquiPort || drawingMosqui || queuePort
                || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdMosqui.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Past Mosqui dispatch");
        var invocation = new MosquiInvocation(whole); activeMosqui = invocation;
        try {
            var result = mosqui.run(whole, invocation.globals);
            verifyMosquiInvocation(invocation);
            if (!invocation.drawn || !invocation.screenCleared || invocation.next != MosquiCut.DRAW
                    || !result.state().equals(invocation.pool.state(whole)))
                throw new IllegalStateException("Mosqui returned without its genuine completed Draw/source cut");
            var afterDraw = invocation.drawSource.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,
                    invocation.drawSource.spriteFlags() & 0x7F);
            int distance = ((afterDraw.word(CdObjectState.WordField.MOSQUI_ANCHOR_X) & 0xFF80)
                    - (((invocation.camera.xWord() - 0x80) & 0xFFFF) & 0xFF80)) & 0xFFFF;
            boolean deleted = distance > 0x280 || (invocation.globals.timeZone() & 0x80) != 0;
            if (deleted ? !Arrays.equals(result.state().copyBytes(), new byte[64]) : !afterDraw.equals(result.state()))
                throw new IllegalStateException("Mosqui changed more than the actual reloaded Draw/despawn tail");
            // Completed effects describe work only; the private ports already published each native cut.
        } catch (RuntimeException | Error original) {
            verifyMosquiFailure(invocation, original); throw original;
        } finally { activeMosqui = null; }
    }
    private MosquiInvocation requireMosquiInvocation(CdObjectPool pool, int whole) {
        var invocation = activeMosqui;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128)
            throw new IllegalStateException("No actual cached Mosqui callback at this physical pointer");
        verifyMosquiInvocation(invocation); return invocation;
    }
    private void verifyMosquiInvocation(MosquiInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeSpikes != null || spikesPort || drawingSpikes || activeMosqui != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeDrain != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeCollapse != null || activeFloat != null
                || activePata != null || activeTamabboh != null || activeBoulder != null || activeAnton != null || activeTunnelDoor != null || activeMonitor != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || collapsePort || drawingCollapse || floatPort || drawingFloat
                || drawingPata || tamabbohPort || drawingTamabboh || boulderPort || drawingBoulder || antonPort || drawingAnton
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation.body != mosqui || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.stageSource != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || invocation.world != mode.getWorldSession()
                || invocation.generation != mode.getLevelManager().getCompletedProductionLoadGeneration()
                || invocation.baseValues != values || values.phase() != BEGIN_LEVEL || invocation.expectedSounds != sounds
                || !placement.status().equals(invocation.before.status()) || !presentation.samePastScroll(invocation.scroll)
                || frame.state() != invocation.player || frame.environment() != invocation.shared.environment()
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared))
            throw new IllegalStateException("Mosqui changed another owner or retained a stale actual World/Frame/cursor cut");
        invocation.pool.validateRestore(invocation.before.pool());
        for (var previous : invocation.before.pool().slots())
            if (previous.identity().wholeIndex() != invocation.whole
                    && !previous.equals(invocation.pool.state(previous.identity().wholeIndex())))
                throw new IllegalStateException("Mosqui changed an unrelated physical image");
        var current = invocation.pool.state(invocation.whole); byte[] expectedSaved = invocation.before.savedFlags().copy();
        if (current.id() != 0 && current.id() != CdMosqui.OBJECT_ID)
            throw new IllegalStateException("Mosqui changed its live object identity");
        if (current.id() == 0) {
            boolean waiting = invocation.entry.routine() == 8 && invocation.entry.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) >= 0;
            if ((!waiting && !invocation.drawn) || !Arrays.equals(current.copyBytes(), new byte[64]))
                throw new IllegalStateException("Mosqui cleared before its actual native Wait/Draw tail");
            int savedId = invocation.entry.savedFlagsId();
            if (savedId != 0) {
                int period = invocation.globals.timeZone() & 0x7F;
                if ((invocation.globals.timeZone() & 0x80) != 0)
                    period = Math.max(0, Math.min(2, (short) ((period - (byte) invocation.globals.timeWarpDir()) & 0xFFFF)));
                int at = 2 + 3 * savedId + period;
                if (at < 0 || at >= expectedSaved.length) throw new IllegalStateException("Mosqui deleted beyond its available saved byte");
                expectedSaved[at] &= 0x7F;
            }
        }
        if (!Arrays.equals(expectedSaved, placement.capture().savedFlags().copy()))
            throw new IllegalStateException("Mosqui changed more than its actual saved loaded-bit store");
    }
    private void verifyMosquiFailure(MosquiInvocation invocation, Throwable original) {
        try { verifyMosquiInvocation(invocation); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }
    private CdObjectState validateMosquiCut(MosquiInvocation invocation, CdObjectPool.Snapshot cut, CdObjectState source) {
        verifyMosquiInvocation(invocation); invocation.pool.validateRestore(cut);
        var current = invocation.pool.state(invocation.whole);
        if (current.id() != CdMosqui.OBJECT_ID || !current.equals(source)
                || source.identity().wholeIndex() != invocation.whole
                || !cut.slots().get(invocation.whole - 32).equals(source)
                || !invocation.pool.capture().slots().equals(cut.slots()))
            throw new IllegalStateException("Mosqui port differs from its actual same-owner full96/source cut");
        return current;
    }
    private void verifyMosquiCallback(MosquiInvocation invocation, CdObjectPlacementRuntime.Snapshot before) {
        verifyMosquiInvocation(invocation); var actual = placement.capture();
        if (!actual.status().equals(before.status()) || !actual.pool().slots().equals(before.pool().slots())
                || !sameBytes(actual.savedFlags(), before.savedFlags()))
            throw new IllegalStateException("Mosqui angle/FM/queue callback changed full96/source/saved/cursors");
    }
    private void verifyMosquiCallbackFailure(MosquiInvocation invocation, CdObjectPlacementRuntime.Snapshot before, Throwable original) {
        try { verifyMosquiCallback(invocation, before); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }
    /** Only finite native scalar observations; no floor/helper execution or environment reconstruction. */
    private void requireMosquiDiveSource(MosquiInvocation invocation, CdObjectState source, boolean corrected) {
        var probe = invocation.entry.addWord(CdObjectState.WordField.Y, 6);
        if (invocation.entry.routine() != 6 || (corrected
                ? source.routine() != 8 || (short) (source.centreY() - probe.centreY()) > 0
                    || (short) (source.centreY() - probe.centreY()) < -32760
                    || !probe.moveWord(CdObjectState.WordField.Y, source.centreY()).addByte(CdObjectState.ByteField.ROUTINE, 2).equals(source)
                : !probe.equals(source)))
            throw new IllegalStateException("Mosqui callback lacks its reached native Y WORD/floor/correction prefix");
    }
    private void commitMosquiPrimaryAngle(CdMosqui.PrimaryAngleCall call) {
        Objects.requireNonNull(call); var invocation = requireMosquiInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || mosquiPort || drawingMosqui || queuePort || invocation.drawn || invocation.sounded
                || (invocation.next != MosquiCut.BODY && invocation.next != MosquiCut.FLOOR)
                || invocation.angleStores == 0 && call.value() != 0)
            throw new IllegalStateException("Mosqui angle lacks its genuine ordered floor prezero/raw store");
        var current = validateMosquiCut(invocation, call.actualCut(), call.expectedSource());
        requireMosquiDiveSource(invocation, current, false);
        var before = placement.capture(); var shared = invocation.shared; mosquiPort = true;
        try {
            verifyMosquiCallback(invocation, before); // Complete actor/environment value CAS before the only Frame writer.
            frame.applyObjectPrimaryAngle(call.value());
            var actual = frame.captureShared(); requireMosquiAngleNonwrites(shared, actual, call.value());
            invocation.shared = actual; invocation.angleStores++; invocation.next = MosquiCut.FLOOR;
            verifyMosquiCallback(invocation, before);
        } catch (RuntimeException | Error original) {
            verifyMosquiCallbackFailure(invocation, before, original); throw original;
        } finally { mosquiPort = false; }
    }
    private void requireMosquiAngleNonwrites(CdPlayerFrameRuntime.SharedState before, CdPlayerFrameRuntime.SharedState after, int value) {
        var e = before.environment(); var a = after.environment(); var em = e.movement(); var am = a.movement();
        var x = em.globals(); var y = am.globals();
        if (before.owner() != after.owner() || before.paused() != after.paused() || !before.vIntClock().equals(after.vIntClock())
                || !before.artState().equals(after.artState()) || !e.controls().equals(a.controls())
                || !e.globals().equals(a.globals()) || !e.dynamicSlots().equals(a.dynamicSlots())
                || !em.physics().equals(am.physics()) || !em.bounds().equals(am.bounds()) || !em.gates().equals(am.gates())
                || y.primaryAngle() != value || x.lookMode() != y.lookMode() || x.cameraYCentre() != y.cameraYCentre()
                || x.secondaryAngle() != y.secondaryAngle() || x.boredTimer() != y.boredTimer() || x.scoreChain() != y.scoreChain()
                || x.debugAngle() != y.debugAngle() || x.debugAngleShift() != y.debugAngleShift()
                || x.debugQuadrant() != y.debugQuadrant() || x.debugFloorDistance() != y.debugFloorDistance())
            throw new IllegalStateException("Mosqui scratch port changed more than the actual Frame primary byte");
    }
    private void playMosquiFm(CdMosqui.FmCall call) {
        Objects.requireNonNull(call); var invocation = requireMosquiInvocation(placement.pool(), call.expectedSource().identity().wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || mosquiPort || drawingMosqui || queuePort || invocation.drawn || invocation.sounded
                || invocation.next != MosquiCut.FLOOR || invocation.angleStores == 0 || call.sound() != CdMosqui.Sound.FM_A7
                || invocation.entry.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) >= 0)
            throw new IllegalStateException("Mosqui FM lacks its actual once-only post-floor visible Dive cut");
        var current = validateMosquiCut(invocation, call.actualCut(), call.expectedSource());
        requireMosquiDiveSource(invocation, current, true);
        var before = placement.capture(); var ledger = new ArrayList<>(sounds);
        ledger.add(new SonicCdGameModule.PastMosquiFm(call.sound())); var ready = List.copyOf(ledger); mosquiPort = true;
        try {
            verifyMosquiCallback(invocation, before);
            sounds = ready; invocation.expectedSounds = ready; invocation.sounded = true; invocation.next = MosquiCut.SOUNDED;
            verifyMosquiCallback(invocation, before);
        } catch (RuntimeException | Error original) {
            verifyMosquiCallbackFailure(invocation, before, original); throw original;
        } finally { mosquiPort = false; }
    }
    private void drawMosqui(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireMosquiInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || mosquiPort || drawingMosqui || queuePort || invocation.drawn || !identity.equals(invocation.entry.identity()))
            throw new IllegalStateException("Mosqui DrawObject lacks its actual once-only physical source");
        var current = pool.state(invocation.whole);
        boolean clearedWait = invocation.entry.routine() == 8 && invocation.entry.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) >= 0
                && current.id() == 0 && Arrays.equals(current.copyBytes(), new byte[64]);
        if (current.id() != CdMosqui.OBJECT_ID && !clearedWait)
            throw new IllegalStateException("Only genuine Mosqui Wait may Draw its cleared live ID0 source");
        if (invocation.entry.routine() == 6) {
            if (invocation.angleStores == 0) throw new IllegalStateException("Mosqui Draw skipped the actual floor prezero");
            requireMosquiDiveSource(invocation, current, current.routine() == 8);
            boolean soundRequired = current.routine() == 8 && invocation.entry.signedByte(CdObjectState.ByteField.SPRITE_FLAGS) < 0;
            if (invocation.sounded != soundRequired) throw new IllegalStateException("Mosqui Draw skipped/repeated the native post-floor A7 cut");
        } else if (invocation.next != MosquiCut.BODY || invocation.angleStores != 0 || invocation.sounded)
            throw new IllegalStateException("A non-Dive Mosqui borrowed a floor or FM publication");
        invocation.drawSource = current; invocation.drawn = true; invocation.next = MosquiCut.DRAW; drawingMosqui = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole),
                    current.spriteFlags(), current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS),
                    current.unsignedByte(CdObjectState.ByteField.WIDTH), current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Mosqui DrawObject omitted its actual BCLR7");
            verifyMosquiInvocation(invocation);
        } catch (RuntimeException | Error original) { verifyMosquiFailure(invocation, original); throw original; }
        finally { drawingMosqui = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readMosquiDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireMosquiInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingMosqui || mosquiPort || queuePort || !invocation.drawn || invocation.next != MosquiCut.DRAW)
            throw new IllegalStateException("No actual Mosqui collecting queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(),
                    current.word(CdObjectState.WordField.X_FRACTION), current.longBits(CdObjectState.LongField.MAPPINGS),
                    current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyMosquiCallback(invocation, before); return live;
        } catch (RuntimeException | Error original) {
            verifyMosquiCallbackFailure(invocation, before, original); throw original;
        } finally { queuePort = false; }
    }
    private void writeMosquiOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireMosquiInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingMosqui || mosquiPort || queuePort || onScreen || invocation.screenCleared || !invocation.drawn || invocation.next != MosquiCut.DRAW)
            throw new IllegalStateException("Only actual Mosqui DrawObject may clear bit7 exactly once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole);
        if (!current.equals(invocation.drawSource)) throw new IllegalStateException("Mosqui changed before its actual Draw BCLR7");
        var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.pool(), List.of(next)); invocation.pool.validateRestore(ready); queuePort = true;
        try {
            verifyMosquiCallback(invocation, before);
            invocation.pool.write(next); invocation.screenCleared = true;
            verifyMosquiInvocation(invocation); var actual = placement.capture();
            if (!actual.pool().slots().equals(ready.slots()) || !sameBytes(actual.savedFlags(), before.savedFlags())
                    || !actual.status().equals(before.status())) throw new IllegalStateException("Mosqui Draw changed more than its source BCLR7 byte");
        } catch (RuntimeException | Error original) { verifyMosquiFailure(invocation, original); throw original; }
        finally { queuePort = false; }
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeDrain != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeFloat != null || activeTunnelDoor != null || activeMonitor != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || floatPort || drawingFloat || tunnelDoorPort || drawingTunnelDoor
                || monitorPort || drawingMonitor || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || queuePort || whole < 32 || whole >= 128
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || invocation == null || activePata != invocation || activeMosqui != null || !runningObjects || servicing || loadingSaved
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || queuePort || invocation.drawn || !identity.equals(invocation.entry.identity()))
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || queuePort || !invocation.drawn) throw new IllegalStateException("No actual Pata-Bata collecting queue read");
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || queuePort || onScreen || invocation.screenCleared || !invocation.drawn)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activeDrain != null || activePrimary != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeFloat != invocation || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || !runningObjects || servicing || loadingSaved || activeDrain != null
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || floatPort || drawingFloat || queuePort || invocation.stopped || invocation.drawn
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || floatPort || drawingFloat || queuePort || invocation.drawn || invocation.stopped
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingFloat || floatPort || queuePort) throw new IllegalStateException("No actual FloatBlock queue read");
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingFloat || floatPort || queuePort || onScreen || invocation.screenCleared)
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

    /** One actual cached Collapse20 visit; completed effects remain observations, never commands. */
    private enum CollapseStep { ENTRY, MAIN_GET_OFF, FM, BREAKUP, CAPACITY_GET_OFF, DELAY_GET_OFF, DRAW, DONE, STOPPED }
    private final class CollapseInvocation {
        private final CdCollapsePlatform body = collapsePlatform;
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
        private CdObjectPool.Snapshot currentPool = pool.capture();
        private CdObjectPool.Snapshot lastCut;
        private CdSolidObject.Outcome adopted;
        private CdObjectState drawSource;
        private CollapseStep next = CollapseStep.ENTRY;
        private boolean single, sounded, drawn, screenCleared;
        private CollapseInvocation(int whole) {
            this.whole = whole; entry = pool.state(whole);
            var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
            world = mode.getWorldSession(); generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
            var foreground = presentation.pastForegroundImage();
            camera = new CdObjectDrawQueue.CameraWords(foreground.cameraX(), foreground.cameraYWord());
        }
    }

    private void runCollapse(int whole) {
        module.requirePastLoadingVisit(this);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activeDrain != null || activePrimary != null
                || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null
                || activeTamabboh != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || activeCollapse != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tamabbohPort || drawingTamabboh || boulderPort || spikesPort || drawingBoulder || mosquiPort || drawingMosqui || drawingSpikes
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || collapsePort || drawingCollapse
                || queuePort || whole < 32 || whole >= 128 || placement.pool().state(whole).id() != CdCollapsePlatform.OBJECT_ID
                || values.phase() != BEGIN_LEVEL || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual live Past Collapse invocation entry");
        var invocation = new CollapseInvocation(whole); activeCollapse = invocation;
        try {
            var environment = frame.environment(); var gates = environment.movement().gates();
            var result = collapsePlatform.run(whole, new CdCollapsePlatform.DespawnGlobals(gates.timeZone(),
                    gates.timeWarpDirection(), invocation.camera.xWord()), new CdCollapsePlatform.Inputs(springPlayer(),
                    new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), springGlobals()));
            verifyCollapseInvocation(invocation); validateCollapseReturn(invocation, result);
        } catch (RuntimeException | Error failure) {
            verifyCollapseFailure(failure); throw failure;
        } finally { activeCollapse = null; }
    }

    private CollapseInvocation requireCollapseInvocation(CdObjectPool pool, int whole) {
        var invocation = activeCollapse;
        if (invocation == null || invocation.pool != pool || pool != placement.pool() || invocation.whole != whole
                || whole < 32 || whole >= 128 || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING)
            throw new IllegalStateException("No actual cached Collapse callback at this physical pointer");
        verifyCollapseInvocation(invocation); return invocation;
    }
    private void verifyCollapseInvocation(CollapseInvocation invocation) {
        module.requirePastLoadingVisit(this);
        var mode = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeCollapse != invocation || !runningObjects || servicing || loadingSaved || activeDrain != null
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null
                || activePlatform != null || activeFloat != null || activePata != null || activeTamabboh != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null
                || activeTunnelDoor != null || activeMonitor != null || reservedPort || drawingReserved
                || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform
                || floatPort || drawingFloat || drawingPata || tamabbohPort || drawingTamabboh || boulderPort || spikesPort || drawingBoulder || mosquiPort || drawingMosqui || drawingSpikes
                || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor
                || invocation.body != collapsePlatform || invocation.sourceFrame != frame || invocation.sourceAssets != assets
                || invocation.sourceStage != frame.stageSource() || invocation.pool != placement.pool()
                || invocation.queue != drawQueue || drawQueue == null || drawQueue.phase() != CdObjectDrawQueue.Phase.COLLECTING
                || mode.getWorldSession() != invocation.world
                || mode.getLevelManager().getCompletedProductionLoadGeneration() != invocation.generation
                || !frame.captureActor().equals(invocation.actor) || !frame.captureShared().equals(invocation.shared)
                || !placement.status().equals(invocation.status) || !presentation.samePastScroll(invocation.scroll)
                || sounds != invocation.expectedSounds || values != invocation.base || values.phase() != BEGIN_LEVEL)
            throw new IllegalStateException("Collapse changed another owner or retained a stale World/Frame/queue cut");
    }
    private void verifyCollapseFailure(Throwable original) {
        try { if (activeCollapse != null) verifyCollapseInvocation(activeCollapse); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }

    /** The trusted cached body alone can write its own prefix and first-free children between callbacks. */
    private void acceptCollapseBodyCut(CollapseInvocation invocation, CdObjectPool.Snapshot cut, boolean breakup) {
        invocation.pool.validateRestore(cut);
        if (!cut.slots().equals(invocation.pool.capture().slots()) || !sameBytes(placement.capture().savedFlags(), invocation.savedFlags))
            throw new IllegalStateException("Collapse source cut differs from the actual full96/saved bank");
        boolean freeGap = false;
        for (int at = 0; at < 96; at++) {
            if (at == invocation.whole - 32) continue;
            var before = invocation.currentPool.slots().get(at); var after = cut.slots().get(at);
            if (before.equals(after)) { if (breakup && before.id() == 0) freeGap = true; continue; }
            if (!breakup || freeGap || before.id() != 0) throw new IllegalStateException("Collapse changed a non-child full64 image");
            validateCollapseChild(invocation, before, after);
        }
        invocation.currentPool = cut;
    }
    private void validateCollapseChild(CollapseInvocation invocation, CdObjectState before, CdObjectState after) {
        int map = assets.view(invocation.single ? CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_4
                : CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_3).range().nativeAddress();
        int flip = !invocation.single && (invocation.entry.subtype() & 16) != 0 ? 1 : 0;
        if (!before.identity().equals(after.identity()) || after.id() != CdCollapsePlatform.OBJECT_ID || after.routine() != 4
                || after.word(CdObjectState.WordField.TILE) != 0x44BE || after.longBits(CdObjectState.LongField.MAPPINGS) != map
                || after.longBits(CdObjectState.LongField.COLLAPSE_FALL_DELTA) != 0x20000
                || after.unsignedByte(CdObjectState.ByteField.PRIORITY) != 3
                || after.unsignedByte(CdObjectState.ByteField.MAP_FRAME) >= 7
                || after.spriteFlags() != (before.spriteFlags() | 4 | flip)
                || after.flags() != (before.flags() | flip))
            throw new IllegalStateException("Collapse child lacks its actual family/native header");
        boolean radii = invocation.single || before.unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER)
                != after.unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER)
                || before.unsignedByte(CdObjectState.ByteField.X_RADIUS) != after.unsignedByte(CdObjectState.ByteField.X_RADIUS)
                || before.unsignedByte(CdObjectState.ByteField.Y_RADIUS) != after.unsignedByte(CdObjectState.ByteField.Y_RADIUS)
                || before.unsignedByte(CdObjectState.ByteField.WIDTH) != after.unsignedByte(CdObjectState.ByteField.WIDTH);
        if (radii && (after.unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER) != 255
                || after.unsignedByte(CdObjectState.ByteField.X_RADIUS) != 8 || after.unsignedByte(CdObjectState.ByteField.WIDTH) != 8
                || !invocation.single && after.unsignedByte(CdObjectState.ByteField.Y_RADIUS) != 9))
            throw new IllegalStateException("Collapse child changed radii beyond its native solid-row stores");
        byte[] old = before.copyBytes(), next = after.copyBytes();
        for (int at = 0; at < 64; at++) {
            boolean allowed = at <= 9 || at == 12 || at == 13 || at == 0x18 || at == 0x1A || at == 0x22
                    || at == 0x24 || at == 0x2A || at == 0x2B || at >= 0x2C && at < 0x30
                    || radii && (at == 0x16 || at == 0x17 || at == 0x19 || at == 0x3E);
            if (!allowed && old[at] != next[at]) throw new IllegalStateException("Collapse child changed undeclared byte " + at);
        }
    }

    /** Actual instruction sites and prior completed helper determine the next allowed port. */
    private void validateCollapseSite(CollapseInvocation invocation, CdCollapsePlatform.SolidCall call, CdObjectState source) {
        boolean top; boolean valid;
        switch (call.site()) {
            case MAIN_TOP -> { top = true; valid = invocation.next == CollapseStep.ENTRY && invocation.entry.routine() <= 2 && source.routine() == 2; }
            case MAIN_GET_OFF -> { top = false; valid = invocation.next == CollapseStep.MAIN_GET_OFF && source.routine() == 2; }
            case DELAY_TOP -> { top = true; valid = invocation.next == CollapseStep.ENTRY && invocation.entry.routine() == 4
                    && (source.routine() == 4 || source.routine() == 6) && source.unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER) != 0
                    && (source.routine() == 6) == (source.word(CdObjectState.WordField.COLLAPSE_DELAY) == 0); }
            case DELAY_GET_OFF -> { top = false; valid = invocation.next == CollapseStep.DELAY_GET_OFF && source.routine() == 6
                    && source.word(CdObjectState.WordField.COLLAPSE_DELAY) == 0; }
            case MULTI_CAPACITY_TOP, SINGLE_CAPACITY_TOP -> { top = true; invocation.single = call.site() == CdCollapsePlatform.Site.SINGLE_CAPACITY_TOP;
                valid = invocation.next == CollapseStep.BREAKUP && invocation.sounded && source.routine() == 4
                        && (source.signedByte(CdObjectState.ByteField.SUBTYPE) < 0) == invocation.single
                        && source.unsignedByte(CdObjectState.ByteField.COLLAPSE_SAVED_SOURCE_ID) == CdCollapsePlatform.OBJECT_ID
                        && invocation.pool.findObjSlot().isEmpty(); }
            case MULTI_CAPACITY_GET_OFF, SINGLE_CAPACITY_GET_OFF -> { top = false;
                valid = invocation.next == CollapseStep.CAPACITY_GET_OFF && source.routine() == 4
                        && (call.site() == CdCollapsePlatform.Site.SINGLE_CAPACITY_GET_OFF) == invocation.single; }
            default -> throw new IllegalStateException("Unowned Collapse helper site");
        }
        if (!valid || source.id() != CdCollapsePlatform.OBJECT_ID
                || call.entry() != (top ? CdSolidObject.Entry.TOP_SOLID_OBJECT : CdSolidObject.Entry.GET_OFF_OBJECT))
            throw new IllegalStateException("Collapse helper differs from its actual private native site/order");
    }
    private List<CdObjectState> validateCollapseImages(CollapseInvocation invocation, CdCollapsePlatform.SolidCall call,
            CdObjectState source, CdObjectState object, List<CdObjectState> others, Set<CdObjectState.ByteField> writes) {
        if (!source.identity().equals(object.identity())
                || writes.stream().anyMatch(field -> field != CdObjectState.ByteField.SOLID_TYPE && field != CdObjectState.ByteField.FLAGS)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) != (call.entry() == CdSolidObject.Entry.TOP_SOLID_OBJECT)
                || writes.contains(CdObjectState.ByteField.SOLID_TYPE) && object.unsignedByte(CdObjectState.ByteField.SOLID_TYPE) > 1
                || ((object.flags() ^ source.flags()) & ~8) != 0
                || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && (object.flags() & ~source.flags()) != 0)
            throw new IllegalStateException("Collapse Solid changed identity, native masks or unowned flag bits");
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int at = 0; at < 64; at++) {
            if (at == 0x25 && writes.contains(CdObjectState.ByteField.SOLID_TYPE) || at == 0x22 && writes.contains(CdObjectState.ByteField.FLAGS)) continue;
            if (before[at] != after[at]) throw new IllegalStateException("Collapse Solid changed undeclared source byte " + at);
        }
        if (others.size() > 1 || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && !others.isEmpty())
            throw new IllegalStateException("Collapse Solid returned extra stand releases");
        var images = new ArrayList<CdObjectState>(); images.add(object); var seen = new HashSet<Integer>(); seen.add(invocation.whole);
        for (var other : others) {
            int whole = other.identity().wholeIndex();
            if (whole < 32 || whole >= 128 || !seen.add(whole) || whole != call.expectedPlayer().standObject())
                throw new IllegalStateException("Collapse release lacks its actual distinct stand identity");
            var original = call.actualCut().slots().get(whole - 32);
            if (!original.identity().equals(other.identity()) || !original.moveByte(CdObjectState.ByteField.FLAGS, original.flags() & ~8).equals(other))
                throw new IllegalStateException("Collapse release changed bytes beyond native BCLR3");
            images.add(other);
        }
        return List.copyOf(images);
    }

    private void commitCollapseSolid(CdCollapsePlatform.SolidCall call) {
        Objects.requireNonNull(call); var invocation = requireCollapseInvocation(placement.pool(), activeCollapse == null ? -1 : activeCollapse.whole);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || collapsePort || drawingCollapse || queuePort || invocation.drawn || invocation.next == CollapseStep.STOPPED
                || call.actualCut() == invocation.lastCut || call.outcome() == invocation.adopted)
            throw new IllegalStateException("Collapse Solid lacks its next fresh actual call");
        var cut = call.actualCut(); invocation.pool.validateRestore(cut);
        var source = cut.slots().get(invocation.whole - 32);
        validateCollapseSite(invocation, call, source);
        acceptCollapseBodyCut(invocation, cut, invocation.next == CollapseStep.BREAKUP);
        if (!call.expectedPlayer().equals(springPlayer()) || !call.expectedGlobals().equals(springGlobals()))
            throw new IllegalStateException("Collapse Solid differs from the actual complete Frame pair");
        CdObjectState object; List<CdObjectState> others; Set<CdObjectState.ByteField> writes;
        List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication readyFrame; CollapseStep next;
        if (call.outcome() instanceof CdSolidObject.Completed done) {
            var result = done.completedResult(); if (result.entry() != call.entry()) throw new IllegalStateException("Wrong Collapse completed helper");
            object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
            readyFrame = frame.prepareObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            next = switch (call.site()) {
                case MAIN_TOP -> result.d0().orElseThrow() == 0 ? CollapseStep.DRAW : CollapseStep.MAIN_GET_OFF;
                case MAIN_GET_OFF -> CollapseStep.FM;
                case DELAY_TOP -> result.d0().orElseThrow() != 0 && source.word(CdObjectState.WordField.COLLAPSE_DELAY) == 0 ? CollapseStep.DELAY_GET_OFF : CollapseStep.DRAW;
                case MULTI_CAPACITY_TOP, SINGLE_CAPACITY_TOP -> result.d0().orElseThrow() != 0 ? CollapseStep.CAPACITY_GET_OFF : CollapseStep.DRAW;
                default -> CollapseStep.DRAW;
            };
        } else {
            var stopped = (CdSolidObject.NativeStopped) call.outcome(); var prefix = stopped.prefix();
            if (prefix.entry() != call.entry() || call.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT
                    || !prefix.otherSlots().isEmpty() || !prefix.fmRequests().isEmpty() || stopped.stop().originalException() == null)
                throw new IllegalStateException("Collapse stop is not the genuine reached Top prefix");
            object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
            readyFrame = frame.prepareObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix); next = CollapseStep.STOPPED;
        }
        var images = validateCollapseImages(invocation, call, source, object, others, writes);
        var readyPool = CdObjectPool.overlay(cut, images); invocation.pool.validateRestore(readyPool);
        var readyActor = new CdPlayerFrameRuntime.ActorState(invocation.actor.owner(), readyFrame.finalState(), invocation.actor.display());
        var readyShared = new CdPlayerFrameRuntime.SharedState(invocation.shared.owner(), readyFrame.finalEnvironment(), invocation.shared.artState(), invocation.shared.paused(), invocation.shared.vIntClock());
        var ledger = new ArrayList<>(sounds); for (var fm : requests) ledger.add(new SonicCdGameModule.PastFrameSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
        var readySounds = List.copyOf(ledger); collapsePort = true;
        try {
            verifyCollapseInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(cut.slots())) throw new IllegalStateException("Stale Collapse publication full96");
            frame.validatePreparedObjectSolidPublication(readyFrame);
            invocation.pool.restore(readyPool); frame.applyPreparedObjectSolidPublication(readyFrame); sounds = readySounds;
            invocation.actor = readyActor; invocation.shared = readyShared; invocation.expectedSounds = readySounds;
            invocation.currentPool = readyPool; invocation.lastCut = cut; invocation.adopted = call.outcome(); invocation.next = next;
            verifyCollapseInvocation(invocation);
            if (!invocation.pool.capture().slots().equals(readyPool.slots())) throw new IllegalStateException("Collapse full output96 was not adopted exactly");
        } catch (RuntimeException | Error failure) { verifyCollapseFailure(failure); throw failure; }
        finally { collapsePort = false; }
    }
    private void playCollapseFm(int nativeId) {
        var invocation = requireCollapseInvocation(placement.pool(), activeCollapse == null ? -1 : activeCollapse.whole);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || collapsePort || drawingCollapse || queuePort || invocation.next != CollapseStep.FM || invocation.sounded)
            throw new IllegalStateException("Collapse A3 is outside its actual post-GetOff/pre-ADDQ cut");
        var before = placement.capture(); var source = before.pool().slots().get(invocation.whole - 32);
        if (source.id() != CdCollapsePlatform.OBJECT_ID || source.routine() != 2 || !before.pool().slots().equals(invocation.currentPool.slots())
                || !sameBytes(before.savedFlags(), invocation.savedFlags)) throw new IllegalStateException("Stale Collapse FM full96 source");
        var ledger = new ArrayList<>(sounds); ledger.add(new SonicCdGameModule.PastCollapsePlatformFm(nativeId)); var ready = List.copyOf(ledger);
        collapsePort = true;
        try {
            verifyCollapseInvocation(invocation); sounds = ready; invocation.expectedSounds = ready; invocation.sounded = true;
            invocation.single = source.signedByte(CdObjectState.ByteField.SUBTYPE) < 0; invocation.next = CollapseStep.BREAKUP;
            verifyCollapseCallback(invocation, before, null);
        } finally { collapsePort = false; }
    }

    private void verifyCollapseCallback(CollapseInvocation invocation, CdObjectPlacementRuntime.Snapshot before, CdObjectState cleared) {
        verifyCollapseInvocation(invocation); var now = placement.capture(); var expected = new ArrayList<>(before.pool().slots());
        if (cleared != null) expected.set(invocation.whole - 32, cleared);
        if (!now.status().equals(before.status()) || !sameBytes(now.savedFlags(), before.savedFlags()) || !now.pool().slots().equals(expected))
            throw new IllegalStateException("Collapse callback changed full96/saved state beyond its actual bit7 clear");
    }
    private void verifyCollapseCallbackFailure(CollapseInvocation invocation, CdObjectPlacementRuntime.Snapshot before, CdObjectState cleared, Throwable original) {
        try { verifyCollapseCallback(invocation, before, cleared); }
        catch (RuntimeException | Error verification) { if (verification != original) original.addSuppressed(verification); }
    }
    private void drawCollapse(CdObjectPool pool, CdObjectState.Identity identity) {
        var invocation = requireCollapseInvocation(pool, Objects.requireNonNull(identity).wholeIndex());
        boolean ownOnly = invocation.next == CollapseStep.ENTRY && (invocation.entry.routine() == 4
                && pool.state(invocation.whole).unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER) == 0 || invocation.entry.routine() == 6);
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || collapsePort || drawingCollapse || queuePort || invocation.drawn || !identity.equals(invocation.entry.identity())
                || !ownOnly && invocation.next != CollapseStep.DRAW && invocation.next != CollapseStep.BREAKUP)
            throw new IllegalStateException("Collapse DrawObject is outside its actual once-only wrapper cut");
        acceptCollapseBodyCut(invocation, pool.capture(), invocation.next == CollapseStep.BREAKUP);
        var before = placement.capture(); var current = pool.state(invocation.whole);
        if (current.id() != CdCollapsePlatform.OBJECT_ID && !(current.id() == 0 && (invocation.sounded || invocation.entry.routine() == 6)
                && Arrays.equals(current.copyBytes(), new byte[64]))) throw new IllegalStateException("Collapse Draw lacks live or genuinely cleared source");
        if (invocation.sounded && current.id() != 0) throw new IllegalStateException("Collapse breakup skipped native parent deletion");
        var cleared = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        invocation.drawSource = cleared; invocation.drawn = true; drawingCollapse = true;
        try {
            drawQueue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(invocation.whole), current.spriteFlags(),
                    current.centreX(), current.centreY(), current.unsignedByte(CdObjectState.ByteField.Y_RADIUS), current.unsignedByte(CdObjectState.ByteField.WIDTH),
                    current.unsignedByte(CdObjectState.ByteField.PRIORITY)), invocation.camera);
            if (!invocation.screenCleared) throw new IllegalStateException("Collapse DrawObject did not clear bit7");
            verifyCollapseCallback(invocation, before, cleared); invocation.currentPool = pool.capture(); invocation.next = CollapseStep.DONE;
        } catch (RuntimeException | Error failure) { verifyCollapseCallbackFailure(invocation, before, invocation.screenCleared ? cleared : null, failure); throw failure; }
        finally { drawingCollapse = false; }
    }
    private CdObjectDrawQueue.LiveDrawSlot readCollapseDrawSlot(CdObjectDrawQueue.CdSlotRef ref) {
        var invocation = requireCollapseInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingCollapse || collapsePort || queuePort) throw new IllegalStateException("No actual Collapse queue read");
        var before = placement.capture(); queuePort = true;
        try {
            var current = invocation.pool.state(invocation.whole);
            var live = new CdObjectDrawQueue.LiveDrawSlot(current.id(), current.spriteFlags(), current.centreX(), current.centreY(), current.word(CdObjectState.WordField.X_FRACTION),
                    current.longBits(CdObjectState.LongField.MAPPINGS), current.unsignedByte(CdObjectState.ByteField.MAP_FRAME), current.word(CdObjectState.WordField.TILE));
            verifyCollapseCallback(invocation, before, null); return live;
        } catch (RuntimeException | Error failure) { verifyCollapseCallbackFailure(invocation, before, null, failure); throw failure; }
        finally { queuePort = false; }
    }
    private void writeCollapseOnScreen(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
        var invocation = requireCollapseInvocation(placement.pool(), Objects.requireNonNull(ref).wholeIndex());
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingCollapse || collapsePort || queuePort || onScreen || invocation.screenCleared) throw new IllegalStateException("Only actual Collapse DrawObject may clear bit7 once");
        var before = placement.capture(); var current = invocation.pool.state(invocation.whole); var next = current.moveByte(CdObjectState.ByteField.SPRITE_FLAGS, current.spriteFlags() & 0x7F);
        queuePort = true;
        try { invocation.pool.write(next); invocation.screenCleared = true; verifyCollapseCallback(invocation, before, next); }
        catch (RuntimeException | Error failure) { verifyCollapseCallbackFailure(invocation, before, invocation.screenCleared ? next : null, failure); throw failure; }
        finally { queuePort = false; }
    }

    private void validateCollapseReturn(CollapseInvocation invocation, CdCollapsePlatform.Result result) {
        if (!invocation.drawn || invocation.next != CollapseStep.DONE || !result.state().equals(invocation.pool.state(invocation.whole)))
            throw new IllegalStateException("Incomplete actual Collapse return");
        var source = invocation.drawSource; var gates = invocation.shared.environment().movement().gates();
        boolean tail = source.signedByte(CdObjectState.ByteField.ROUTINE) < 4;
        boolean delete = tail && (((source.centreX() & 0xFF80) - ((invocation.camera.xWord() - 0x80) & 0xFF80) & 0xFFFF) > 0x280 || (gates.timeZone() & 0x80) != 0);
        var now = invocation.pool.capture();
        for (int at = 0; at < 96; at++) {
            var expected = invocation.currentPool.slots().get(at); var actual = now.slots().get(at);
            if (delete && at == invocation.whole - 32) {
                if (!actual.identity().equals(expected.identity()) || !Arrays.equals(actual.copyBytes(), new byte[64]))
                    throw new IllegalStateException("Collapse tail did not clear its actual complete source image");
            } else if (!expected.equals(actual)) throw new IllegalStateException("Collapse changed full96 beyond its reached tail deletion");
        }
        byte[] bank = invocation.savedFlags.copy();
        if (delete && source.savedFlagsId() != 0) {
            int period = gates.timeZone() & 0x7F;
            if ((gates.timeZone() & 0x80) != 0) { int selected = (short) ((period - (byte) gates.timeWarpDirection()) & 0xFFFF); period = selected < 0 ? 0 : Math.min(selected, 2); }
            int index = 2 + 3 * source.savedFlagsId() + period;
            if (index >= bank.length) throw new IllegalStateException("Collapse returned past an unavailable saved-byte address");
            bank[index] &= 0x7F;
        }
        if (!Arrays.equals(bank, placement.capture().savedFlags().copy())) throw new IllegalStateException("Collapse changed another saved byte");
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || reservedPort || drawingReserved || ringPort || drawingRing
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activePlatform != invocation || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || !runningObjects || servicing || loadingSaved
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || platformPort || drawingPlatform || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingPlatform || platformPort || queuePort) throw new IllegalStateException("No actual Platform queue read");
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingPlatform || platformPort || queuePort || onScreen || invocation.screenCleared)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeTunnelDoor != invocation || !runningObjects || servicing || loadingSaved
                || activePrimary != null || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring
                || platformPort || drawingPlatform || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || monitorPort || drawingMonitor || invocation.body != tunnelDoor
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.stopped || invocation.drawn)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || tunnelDoorPort || drawingTunnelDoor || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingTunnelDoor || tunnelDoorPort || queuePort)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingTunnelDoor || tunnelDoorPort || queuePort || onScreen || invocation.screenCleared)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !runningObjects || servicing || loadingSaved || activePrimary != null || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || activeTunnelDoor != null || activeMonitor != null || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || activeMonitor != invocation || activeFloat != null || activePata != null || activeTamabboh != null || activeCollapse != null || activeBoulder != null || activeMosqui != null || activeSpikes != null || activeAnton != null || drawingPata || tamabbohPort || drawingTamabboh || collapsePort || boulderPort || spikesPort || drawingCollapse || drawingBoulder || drawingSpikes || antonPort || drawingAnton || floatPort || drawingFloat || !runningObjects || servicing || loadingSaved
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.stopped
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.stopped
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.stopped
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || monitorPort || drawingMonitor || queuePort || !invocation.post || invocation.drawn || invocation.voiced
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || monitorPort || drawingMonitor || queuePort || !invocation.post || invocation.drawn || invocation.voiced
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || monitorPort || drawingMonitor || queuePort || invocation.post || invocation.drawn || invocation.sounded
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || monitorPort || drawingMonitor || queuePort || invocation.drawn || invocation.stopped || invocation.pendingCut != null)
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingMonitor || monitorPort || queuePort) throw new IllegalStateException("No actual ID19 queue read");
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
        if (activeResults != null || resultsPort || drawingResults || activeBigRing != null || bigRingPort || drawingBigRing || activeSignpost != null || signpostPort || drawingSignpost || activeGoal != null || goalPort || drawingGoal || activeMachine != null || machinePort || drawingMachine || !drawingMonitor || monitorPort || queuePort || onScreen || invocation.screenCleared)
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
