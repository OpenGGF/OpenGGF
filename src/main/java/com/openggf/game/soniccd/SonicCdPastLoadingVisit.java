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
    private transient CdObjectDrawQueue drawQueue;
    private transient ReservedInvocation activeReserved;
    private transient RingInvocation activeRing;
    private transient SpringInvocation activeSpring;
    private transient PlatformInvocation activePlatform;
    private boolean runningObjects;
    private boolean reservedPort;
    private boolean drawingReserved;
    private boolean ringPort;
    private boolean drawingRing;
    private boolean springPort;
    private boolean drawingSpring;
    private boolean platformPort;
    private boolean drawingPlatform;
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
                                  Optional<CdAssetBytes> layoutRam, OptionalInt debugBlockWord, OptionalInt pauseMusicBit) {
        private LoadingGlobals {
            Objects.requireNonNull(live); Objects.requireNonNull(contact);
            Objects.requireNonNull(score); Objects.requireNonNull(drownTimerWord); Objects.requireNonNull(tail);
            Objects.requireNonNull(bonus); Objects.requireNonNull(layoutRam); Objects.requireNonNull(debugBlockWord);
            Objects.requireNonNull(pauseMusicBit);
            if (pauseMusicBit.isPresent() && (pauseMusicBit.getAsInt() & ~0x80) != 0)
                throw new IllegalArgumentException("Only PauseMusic's bit7 belongs to this projection");
        }
        LoadingGlobals withLive(CdCheckpointLiveAux.State next) {
            return new LoadingGlobals(next, contact, score, drownTimerWord, tail, bonus, layoutRam, debugBlockWord, pauseMusicBit);
        }
        LoadingGlobals withDrownTimer(int word) {
            return new LoadingGlobals(live, contact, score, OptionalInt.of(word), tail, bonus, layoutRam, debugBlockWord, pauseMusicBit);
        }
        LoadingGlobals clearBonus() {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail,
                    Optional.of(new CdLevelHud.BonusValues(0, 0, 0)), layoutRam, debugBlockWord, pauseMusicBit);
        }
        LoadingGlobals clearDebugBlock() {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail, bonus, layoutRam, OptionalInt.of(0), pauseMusicBit);
        }
        LoadingGlobals withLayout(CdAssetBytes bytes) {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail, bonus, Optional.of(bytes), debugBlockWord, pauseMusicBit);
        }
        LoadingGlobals withPauseMusicBit(int bit) {
            return new LoadingGlobals(live, contact, score, drownTimerWord, tail, bonus, layoutRam,
                    debugBlockWord, OptionalInt.of(bit));
        }
        LoadingGlobals finishMainStores() {
            return new LoadingGlobals(live, new CdPlayerBody.ContactGlobals(0, 0, contact.combineRing()),
                    new CdOpeningObjectRuntime.ScoreGlobals(score.score(), score.nextLifeScore(), 0x80),
                    drownTimerWord, Optional.of(new TailValues(0, 0, 0, 0, 0x202F)), bonus, layoutRam, debugBlockWord, pauseMusicBit);
        }
    }

    /** Finite native DBF progress plus the selected display-enable output; neither is a clock. */
    private record FadeProgress(int completedSteps, boolean displayEnabled) {
        private FadeProgress {
            if (completedSteps < 0 || completedSteps > 22) throw new IllegalArgumentException("FromWhite DBF progress");
        }
    }

    /** All mutable loading values live here once; actor/shared/scroll remain in their existing owners. */
    private record Values(SonicCdGameModule.PastLoadPhase phase, CdR11bSectionArt.State section,
                          CdAssetBytes cpuPalette, CdAssetBytes fadePalette,
                          CdAssetBytes displayedPalette, CdAssetBytes cpuSat, CdAssetBytes displayedSat,
                          CdAssetBytes displayedHScroll, int displayedVScrollLongBits,
                          CdR11aBackgroundPlane.State foregroundPlane,
                          CdRingGlobals ring, CdLevelGlobals level, LoadingGlobals globals,
                          CdAssetBytes hudObjects, FadeProgress fadeProgress, List<SonicCdGameModule.PastLoadCall> calls) {
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
                            int displayedVScrollLongBits, CdAssetBytes savedObjectFlags) {
        this.module = Objects.requireNonNull(module); this.assets = Objects.requireNonNull(assets);
        this.frame = Objects.requireNonNull(frame); this.presentation = Objects.requireNonNull(presentation);
        this.saved = Objects.requireNonNull(saved);
        // MAINVARS FF1200..FF14FD survives RunMMD and the later clears. Data is
        // carried before A retirement; this is not a foreign Snapshot restore.
        placement = CdObjectPlacementRuntime.fromAssets(assets, savedObjectFlags);
        this.ring = CdRing.fromAssets(placement, assets);
        var sharedSolid = new CdSolidObject(CdPlayerMovement.fromStageSource(frame.stageSource()));
        spring = new CdSpring(placement, CdSpring.Assets.fromPastAssets(assets), new CdPlayerMath(assets.player()),
                sharedSolid, this::commitSpringSolid, this::commitSpringLaunch, this::drawSpring, this::playSpringFm);
        platform = new CdPlatform(placement, CdPlatform.Assets.fromPastAssets(assets), new CdPlayerMath(assets.player()),
                CdPlayerTerrain.fromStageTerrain(assets.terrain()), sharedSolid,
                this::commitPlatformSolid, this::commitPlatformPrimaryAngle, this::drawPlatform);
        art = CdR11bSectionArt.fromAssets(assets); foreground = CdR11bForegroundPlane.bind(assets);
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
        values = new Values(FIRST_PLC, section, CdAssetBytes.copyOf(white), zero(128),
                video.selection().displayedPalette(), zero(CdObjectDrawQueue.SAT_BYTE_SIZE),
                video.selection().displayedSat(), zero(CdR11aBackgroundScroll.HSCROLL_RAM_BYTES),
                displayedVScrollLongBits, null,
                ring, new CdLevelGlobals(0, level.updateHudTime(), level.checkpoint(), level.spawnMode(),
                        level.plcLoadFlags() | 1, level.flowerCount(), level.fadeEnableDisplay(),
                        level.powerup(), level.timeOver()),
                // RunMMD covers the same four water bytes; lower MAINVARS time and its
                // partial knowledge survive. The helper supplies only that finite projection.
                new LoadingGlobals(CdCheckpointLiveAux.clearMiscAtLevelStart(live), contact, score, OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty()),
                zero(31 * 64), new FadeProgress(0, video.selection().displayEnabled()),
                List.of(LOAD_LEVEL_PCM, INIT_VDP, INIT_CONTROLLERS, NORMAL_LOAD));
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
        if (servicing || loadingSaved || runningObjects || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || drawQueue != null)
            throw new IllegalStateException("Past loading operation or pending object pass is active");
        frame.captureShared();
    }
    private void requireCallback() {
        module.requirePastLoadingVisit(this);
        if (!servicing || loadingSaved) throw new IllegalStateException("No actual Past selectorC callback");
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
                displayedSat, displayedHScroll, values.displayedVScrollLongBits(), fg, ring, level, globals, hud, values.fadeProgress(), calls);
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
        requireCurrent();
        if (values.phase() == ANIMATED_TILES) throw module.pastLoadingBoundary(
                SonicCdGameModule.PastLoadBoundary.ANIMATED_TILES);
        if (values.phase() == BEGIN_LEVEL) { serviceLevel(held); return; }
        if (values.phase() == FROM_WHITE) { serviceFromWhite(held); return; }
        presentation.validatePastLevelLoadPlane();
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
                values.fadeProgress(), values.calls());
        sounds = List.of(); // Current call requests, not an unbounded sound history.
        var selected = frame.prepareInput(held);
        servicing = true;
        try { frame.serviceVBlank(selected.phase(), this::publishLevel, this::serviceLevelArtAndHud, this::mainLevelMusic); }
        finally { servicing = false; }
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
        if (runningObjects || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null || drawQueue != null)
            throw new IllegalStateException("Past object pass cannot be repeated or reentered");
        runningObjects = true;
        try {
            drawQueue = createReservedDrawQueue();
            drawQueue.beginPass();
            var primary = frame.state();
            if (primary.header().slotId() != 0) {
                if (primary.header().slotId() != 1 || primary.motion().routine() != 0)
                    throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.PRIMARY_BODY);
                // Existing B-bound Frame owns Init, its prior-charge operation and
                // final adoption once. No extra input/service, art cue or DrawObject.
                var publication = frame.runBody(levelFrame);
                for (var sound : publication.sounds()) appendSound(new SonicCdGameModule.PastFrameSound(sound));
                call(PRIMARY_INIT);
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
                } else if (id == CdSpring.OBJECT_ID) {
                    runSpring(whole); call(SPRING_BODY);
                } else if (id == CdPlatform.OBJECT_ID) {
                    runPlatform(whole); call(PLATFORM_BODY);
                } else {
                    throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.DYNAMIC_OBJECTS);
                }
            }
            call(DYNAMIC_OBJECTS);
            // A completed dynamic walk does not own LevelEvents/scroll/DrawObjects.
            throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.LEVEL_FLOW);
        } catch (RuntimeException | Error failure) {
            try { if (drawQueue != null) drawQueue.close(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        } finally {
            runningObjects = false;
        }
    }

    private CdObjectDrawQueue createReservedDrawQueue() {
        var hudView = assets.view(CdR11bAssets.Asset.MAP_SPR_HUD);
        var monitorView = assets.view(CdR11bAssets.Asset.MAP_SPR_MONITOR_TIME);
        var ringView = assets.view(CdR11bAssets.Asset.MAP_SPR_RING);
        var springView = assets.view(CdR11bAssets.Asset.MAP_SPRING);
        var platformView = assets.view(CdR11bAssets.Asset.PLATFORM_MAPPINGS);
        var mappings = CdObjectMappings.declaredSourceViews(List.of(
                new CdObjectMappings.DeclaredTable(hudView.range().nativeAddress(), hudView.bytes(), 0, 4),
                new CdObjectMappings.DeclaredTable(monitorView.range().nativeAddress(), monitorView.bytes(), 0, 20),
                new CdObjectMappings.DeclaredTable(ringView.range().nativeAddress(), ringView.bytes(), 0, 9),
                new CdObjectMappings.DeclaredTable(springView.range().nativeAddress(), springView.bytes(), 0, 3),
                new CdObjectMappings.DeclaredTable(springView.range().nativeAddress(), springView.bytes(), 6, 3),
                new CdObjectMappings.DeclaredTable(springView.range().nativeAddress(), springView.bytes(), 80, 3),
                new CdObjectMappings.DeclaredTable(platformView.range().nativeAddress(), platformView.bytes(), 0, 3)),
                List.of());
        return new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
            @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                return Objects.requireNonNull(ref).wholeIndex() < 32 ? readReservedDrawSlot(ref)
                        : activeSpring != null ? readSpringDrawSlot(ref)
                        : activePlatform != null ? readPlatformDrawSlot(ref) : readRingDrawSlot(ref);
            }
            @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref, boolean onScreen) {
                if (Objects.requireNonNull(ref).wholeIndex() < 32) writeReservedOnScreen(ref, onScreen);
                else if (activeSpring != null) writeSpringOnScreen(ref, onScreen);
                else if (activePlatform != null) writePlatformOnScreen(ref, onScreen);
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
        if (!runningObjects || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform || queuePort)
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
        if (!runningObjects || servicing || loadingSaved || activeRing != null || ringPort || drawingRing || activeSpring != null || activePlatform != null || springPort || drawingSpring || platformPort || drawingPlatform
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
        if (activeReserved != invocation || !runningObjects || activeRing != null || ringPort || drawingRing || activeSpring != null || activePlatform != null || springPort || drawingSpring || platformPort || drawingPlatform
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
                v.foregroundPlane(), v.ring(), v.level(), v.globals(), image, v.fadeProgress(), v.calls());
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
        if (!runningObjects || servicing || loadingSaved || activeReserved != null || activeRing != null || activeSpring != null || activePlatform != null
                || reservedPort || drawingReserved || ringPort || drawingRing || springPort || drawingSpring || platformPort || drawingPlatform || queuePort
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
                || activeReserved != null || activeSpring != null || activePlatform != null || springPort || drawingSpring || platformPort || drawingPlatform || reservedPort || drawingReserved
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
                v.foregroundPlane(), ring, v.level(), v.globals(), v.hudObjects(), v.fadeProgress(), v.calls());
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
        if (!runningObjects || servicing || loadingSaved || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || queuePort || whole < 32 || whole >= 128
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
                || activeReserved != null || activeRing != null || activePlatform != null || platformPort || drawingPlatform
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
        if (springPort || drawingSpring || platformPort || drawingPlatform || queuePort || invocation.solidResult != null || invocation.launched
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
        if (springPort || drawingSpring || platformPort || drawingPlatform || queuePort || invocation.solidResult == null
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
        if (springPort || drawingSpring || platformPort || drawingPlatform || queuePort || !invocation.launched || invocation.sounded || invocation.drawn
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
        if (springPort || drawingSpring || platformPort || drawingPlatform || queuePort || invocation.drawn || invocation.launched != invocation.sounded)
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
        if (!runningObjects || servicing || loadingSaved || activeReserved != null || activeRing != null
                || activeSpring != null || activePlatform != null || reservedPort || drawingReserved || ringPort || drawingRing
                || springPort || drawingSpring || platformPort || drawingPlatform || queuePort
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
        if (activePlatform != invocation || !runningObjects || servicing || loadingSaved
                || activeReserved != null || activeRing != null || activeSpring != null
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
        if (platformPort || drawingPlatform || queuePort || invocation.drawn || invocation.stopped)
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
        if (platformPort || drawingPlatform || queuePort || invocation.drawn || invocation.stopped)
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
        if (platformPort || drawingPlatform || queuePort || invocation.drawn || invocation.stopped)
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
                        next.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit()), values.hudObjects(), values.calls());
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
                values.hudObjects(), values.fadeProgress(), values.calls());
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
        globals(LoadingGlobals::clearBonus); call(CLEAR_CAMERA);
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
                        g.tail().orElseThrow().demoS1IndexWord(), 0x003F)), g.bonus(), g.layoutRam(), g.debugBlockWord(), g.pauseMusicBit()));
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
                values.globals(), values.hudObjects(), progress, values.calls());
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
        requireCurrent();
        return new Snapshot(this, values, frame.captureActor(), frame.captureShared());
    }
    void validateRestore(Snapshot capture, SonicCdGameModule.LevelProgramState program) {
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
