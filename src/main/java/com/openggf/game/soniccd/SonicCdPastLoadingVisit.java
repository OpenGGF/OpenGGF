package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineTiming;

import java.util.*;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.SonicCdGameModule.PastLoadCall.*;
import static com.openggf.game.soniccd.SonicCdGameModule.PastLoadPhase.*;

/**
 * Actual same-World USA R11B loading visit. Native N1ecc5f3a Level/Main.asm:119-329.
 * Host geometry preparation is separate from the executed blocks/layout/plane writes.
 * This owner has no gameplay loop, alternate clock, renderer readiness or RAM fallback.
 */
final class SonicCdPastLoadingVisit {
    private final SonicCdGameModule module;
    private final CdR11bAssets assets;
    private final CdPlayerFrameRuntime frame;
    private final SonicCdPlayerPresentation presentation;
    private final CdTimeWarpSaveData.State saved;
    private final CdR11bSectionArt art;
    private final CdR11bForegroundPlane foreground;
    private Values values;
    private List<CdPlayerFrameRuntime.Sound> sounds = List.of();
    private boolean servicing;
    private boolean loadingSaved;
    private int savedCursor;
    private CdTimeWarpLoadData.StoreTarget observedTarget;
    private OptionalInt observedValue;
    private CdPlayerFrameRuntime.ActorState observedActor;
    private CdPlayerFrameRuntime.SharedState observedShared;
    private SonicCdPlayerPresentation.ScrollCapture observedScroll;
    private Values observedValues;

    /** Exact reached fixed stores immediately before the unowned UpdateAnimTiles call. */
    private record TailValues(int player2ControlsWord, int player2BoredWord, int unknownLevelByte,
                              int demoS1IndexWord, int paletteFadeInfoWord) { }
    private record LoadingGlobals(CdCheckpointLiveAux.State live, CdPlayerBody.ContactGlobals contact,
                                  CdOpeningObjectRuntime.ScoreGlobals score, OptionalInt drownTimerWord,
                                  Optional<TailValues> tail) {
        private LoadingGlobals {
            Objects.requireNonNull(live); Objects.requireNonNull(contact);
            Objects.requireNonNull(score); Objects.requireNonNull(drownTimerWord); Objects.requireNonNull(tail);
        }
        LoadingGlobals withLive(CdCheckpointLiveAux.State next) {
            return new LoadingGlobals(next, contact, score, drownTimerWord, tail);
        }
        LoadingGlobals withDrownTimer(int word) {
            return new LoadingGlobals(live, contact, score, OptionalInt.of(word), tail);
        }
        LoadingGlobals finishMainStores() {
            return new LoadingGlobals(live, new CdPlayerBody.ContactGlobals(0, 0, contact.combineRing()),
                    new CdOpeningObjectRuntime.ScoreGlobals(score.score(), score.nextLifeScore(), 0x80),
                    drownTimerWord, Optional.of(new TailValues(0, 0, 0, 0, 0x202F)));
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
        private final SonicCdPlayerPresentation.PastLoadFlags flags;
        private final List<CdPlayerFrameRuntime.Sound> sounds;
        private Snapshot(SonicCdPastLoadingVisit owner, Values values,
                         CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared) {
            this.owner = owner; this.values = values; this.actor = actor; this.shared = shared;
            this.flags = owner.presentation.capturePastLoadFlags(); this.sounds = owner.sounds;
        }
    }

    SonicCdPastLoadingVisit(SonicCdGameModule module, CdR11bAssets assets, CdPlayerFrameRuntime frame,
                            SonicCdPlayerPresentation presentation, CdTimeWarpSaveData.State saved,
                            CdRingGlobals ring, CdLevelGlobals level, int sectionWord,
                            SonicCdPlayerPresentation.PastVideoInit video, CdCheckpointLiveAux.State live,
                            CdPlayerBody.ContactGlobals contact, CdOpeningObjectRuntime.ScoreGlobals score,
                            int displayedVScrollLongBits) {
        this.module = Objects.requireNonNull(module); this.assets = Objects.requireNonNull(assets);
        this.frame = Objects.requireNonNull(frame); this.presentation = Objects.requireNonNull(presentation);
        this.saved = Objects.requireNonNull(saved);
        art = CdR11bSectionArt.fromAssets(assets); foreground = CdR11bForegroundPlane.bind(assets);
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
                new LoadingGlobals(CdCheckpointLiveAux.clearMiscAtLevelStart(live), contact, score, OptionalInt.empty(), Optional.empty()),
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
        if (servicing || loadingSaved) throw new IllegalStateException("Past loading operation is active");
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
        if (values.phase() == BEGIN_LEVEL) {
            // The first ordinary8 needs a real player-art publication lifetime. Loading
            // has not bound one; stop BEFORE input, VScroll, native Finish or a pulse.
            throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.LEVEL_VINT_ART_PUBLICATION);
        }
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
        sounds = List.of(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_PASTMUS));
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
        presentation.clearPastFlowersImages(); call(CLEAR_FLOWERS);
        // The actual complete object clear includes primary slot0; its fresh RunMMD
        // image is already zero and has had no native object writer before this cut.
        if (frame.state().header().slotId() != 0 || frame.state().motion().routine() != 0)
            throw module.pastLoadingBoundary(SonicCdGameModule.PastLoadBoundary.ENTRY);
        replace(values.phase(), values.section(), values.cpuPalette(), values.fadePalette(), values.displayedPalette(),
                values.cpuSat(), values.displayedSat(), values.displayedHScroll(), values.foregroundPlane(),
                values.ring(), values.level(), values.globals(), zero(31 * 64), values.calls());
        call(CLEAR_OBJECTS);
        globals(g -> g.withLive(CdCheckpointLiveAux.clearMiscAtLevelStart(g.live()))); call(CLEAR_MISC);
        frame.clearLevelStartCameraPlayerValues(); presentation.clearPastCameraImages(); call(CLEAR_CAMERA);
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
        var layout = executeLayoutCopy(); call(LEVEL_LAYOUT);
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
                        g.tail().orElseThrow().demoS1IndexWord(), 0x003F))));
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
                || ((program.gameMode() & 0x80) != 0) == begun
                || ((fading || begun) && v.globals().tail().orElseThrow().paletteFadeInfoWord() != 0x003F)) {
            throw new IllegalArgumentException("Past marker, native fade progress and programme values differ");
        }
    }
    void restore(Snapshot capture, SonicCdGameModule.LevelProgramState program) {
        validateRestore(capture, program);
        frame.restoreActor(capture.actor); frame.restoreShared(capture.shared);
        presentation.restorePastLoadFlags(capture.flags); values = capture.values; sounds = capture.sounds;
    }
}
