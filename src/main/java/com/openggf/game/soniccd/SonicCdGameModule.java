package com.openggf.game.soniccd;

import com.openggf.audio.GameAudioProfile;
import com.openggf.game.Game;
import com.openggf.game.*;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.NativeRewindRestoreGuard;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.save.SaveSnapshotProvider;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdCheckpoint;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointLoadData;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayableSonic;
import com.openggf.game.soniccd.player.CdPlayerEngineRules;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scene.CdWarpAssets;
import com.openggf.game.soniccd.scene.CdWarpScene;
import com.openggf.level.Level;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.SecondaryAbility;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Real stock CD module retaining its default STATIC R11A terrain admission and
 * separately named terrain-only and USA opening-object development PLAYER entries.
 * The opening entry executes the bounded Ring/Mosqui owners, not a retail title
 * ingress or full route. Native title/new-game/HUD/audio driver, saves, export and
 * editor entry remain unavailable.
 * The full native metadata catalogue is not a load-capability list. GameLoop
 * remains the sole mode writer.
 */
public final class SonicCdGameModule implements GameModule {
    private static final String SHARED_REWIND_KEY = "soniccd-terrain-player-shared";
    private static final String RESTORE_CALLBACK_KEY = "soniccd-terrain-player-projection";
    private static final String OPENING_SHARED_REWIND_KEY = "soniccd-opening-player-shared";
    private final boolean playerAdmission;
    private final boolean openingObjectsAdmission;
    private final SonicCdZoneRegistry zones = new SonicCdZoneRegistry();
    private final PhysicsProvider rules;
    private final SonicCdStaticAudioProfile audio = new SonicCdStaticAudioProfile();
    private final SonicCdStaticPresentation presentation = new SonicCdStaticPresentation(this);
    private final SonicCdLevelInitProfile profile = new SonicCdLevelInitProfile(this);
    private final SonicCdPlayerPresentation playerPresentation;
    private final SonicCdPlayerInitProfile playerProfile;
    private final PlayableCharacterRegistry characters;
    private CdGameDataSource source;
    private GameplayModeContext playerMode;
    private CdPlayerFrameRuntime playerFrame;
    private CdPlayableSonic playerActor;
    private CdOpeningObjectRuntime openingObjects;
    private PlcLifecycleService openingPlc;
    private PlcLifecycleService terrainPlc;
    private CdR11aAssets authenticatedOpeningAssets;
    private boolean constructingPlayer;
    private RestartCarry stagedRestart;
    private LevelLoadContext stagedRestartContext;
    private LevelProgramState levelProgram;

    private CdWarpAssets warpAssets;
    private CdWarpScene warpScene;
    private CdWarpScene.State warpState;
    private List<ProgramSound> programSounds = List.of();

    enum LevelProgramPhase { LEVEL, WHITE_FADE, RETURNED, WARP, PAST_PENDING }
    /** Completed source requests only: no sound-driver execution or old Frame ledger mutation. */
    enum ProgramSound {
        STOP_FM(0xE0), WARP_DIRECT_FM_D8(0xD8);
        private final int nativeId;
        ProgramSound(int nativeId) { this.nativeId = nativeId; }
        int nativeId() { return nativeId; }
        int driverQueue() { return 2; }
    }
    java.util.Optional<CdWarpScene.State> warpSceneState() { return java.util.Optional.ofNullable(warpState); }
    List<ProgramSound> programSoundRequests() { return programSounds; }
    CdWarpAssets warpSceneAssets() { return Objects.requireNonNull(warpAssets, "No authenticated WARP binding"); }
    boolean warpProgramSelected() {
        var phase = levelProgramState().phase();
        return phase == LevelProgramPhase.WARP || phase == LevelProgramPhase.PAST_PENDING;
    }

    /**
     * Only the native programme globals not already owned by Frame/Runtime, plus FadeToWhite's
     * completed waits. The existing shared opening payload captures this immutable value once.
     * Fresh .BeginLevel is an explicit semantic ingress: IPX.RunMMD/NewGame and Main:328-329
     * justify these postconditions; the omitted Title/IPX/load prelude has not been executed.
     */
    record LevelProgramState(int demoMode, int timeStones, int specialStage, int usePlayer2,
                             int palFadeFlags, int gameMode, int levelStarted, int whiteFadeSteps,
                             LevelProgramPhase phase) {
        LevelProgramState {
            Objects.requireNonNull(phase, "native level programme phase");
            if ((demoMode & ~0xFFFF) != 0 || (timeStones & ~0xFF) != 0
                    || (specialStage & ~0xFF) != 0 || (usePlayer2 & ~0xFF) != 0
                    || (palFadeFlags & ~0xFF) != 0 || (gameMode & ~0xFF) != 0
                    || (levelStarted & ~0xFF) != 0 || whiteFadeSteps < 0 || whiteFadeSteps > 22
                    || (phase == LevelProgramPhase.LEVEL && whiteFadeSteps != 0)
                    || ((phase == LevelProgramPhase.RETURNED || phase == LevelProgramPhase.WARP
                            || phase == LevelProgramPhase.PAST_PENDING) && whiteFadeSteps != 22)) {
                throw new IllegalArgumentException("Incomplete native level programme value");
            }
        }

        private static LevelProgramState freshBeginLevel() {
            return new LevelProgramState(0, 0, 0, 0, 1, 0, 1, 0, LevelProgramPhase.LEVEL);
        }
    }

    /** Pure value observation; no entry, fade, capture or native service is performed. */
    LevelProgramState levelProgramState() {
        return Objects.requireNonNull(levelProgram, "No admitted native opening programme");
    }

    public enum RestartBoundary { GAME_OVER_EXIT, RESTART_PATH, WARP_LEVEL_START, WARP_PROGRAM_START, R11B_LOAD }
    public static final class UnsupportedRestart extends UnsupportedOperationException {
        private final RestartBoundary boundary;
        private UnsupportedRestart(RestartBoundary boundary) { super(boundary.name()); this.boundary = boundary; }
        public RestartBoundary boundary() { return boundary; }
    }

    /**
     * Explicit immutable buffer ingress, not another runtime scroll owner. Deformation has no
     * LevelStart clear; HScroll is cleared by ClearScreen (Functions (General).asm:220-226,
     * called at Main:225). Capture reads both complete buffers, then restartCarry applies that native clear.
     * Fresh zero deformation is explicit DEV ingress, not inferred native-cold RAM.
     */
    record BackgroundBuffers(CdAssetBytes deformationRam, CdAssetBytes hscrollRam) {
        BackgroundBuffers {
            Objects.requireNonNull(deformationRam, "complete deformation RAM");
            Objects.requireNonNull(hscrollRam, "complete HScroll RAM");
            if (deformationRam.length() != CdR11aBackgroundScroll.DEFORMATION_RAM_BYTES
                    || hscrollRam.length() != CdR11aBackgroundScroll.HSCROLL_RAM_BYTES) {
                throw new IllegalArgumentException("Background ingress requires all 512 deformation and 1024 HScroll bytes");
            }
        }
    }

    /** Immutable handoff between visits; never part of a captured Frame or a second global store. */
    record RestartCarry(CdRingGlobals ring, CdPlayerBody.ContactGlobals contact,
                        CdOpeningObjectRuntime.ScoreGlobals score, CdLevelGlobals level, CdAssetBytes savedObjectFlags,
                        CdPlayerFrameRuntime.RestartIngress player, CdPowerupArtRuntime.State powerupArt,
                        BackgroundBuffers background, CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                        CdOpeningObjectRuntime.CheckpointOwnerState checkpointState,
                        CdTimeWarpSaveData.State timeWarpSavedState, LevelProgramState program) {
        RestartCarry {
            Objects.requireNonNull(ring); Objects.requireNonNull(contact); Objects.requireNonNull(score);
            Objects.requireNonNull(level); Objects.requireNonNull(savedObjectFlags);
            Objects.requireNonNull(player); Objects.requireNonNull(powerupArt); Objects.requireNonNull(background);
            Objects.requireNonNull(cameraArt, "explicit current camera art carry");
            Objects.requireNonNull(checkpointState, "explicit current checkpoint live/saved owner");
            Objects.requireNonNull(timeWarpSavedState, "explicit distinct time-warp saved value");
            if (savedObjectFlags.length() != CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
                throw new IllegalArgumentException("Complete native saved-object bank required");
            }
        }

        /** Legacy pure policy ingress carries no knowledge of the native programme globals. */
        RestartCarry(CdRingGlobals ring, CdPlayerBody.ContactGlobals contact,
                     CdOpeningObjectRuntime.ScoreGlobals score, CdLevelGlobals level, CdAssetBytes savedObjectFlags,
                     CdPlayerFrameRuntime.RestartIngress player, CdPowerupArtRuntime.State powerupArt,
                     BackgroundBuffers background, CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState,
                     CdTimeWarpSaveData.State timeWarpSavedState) {
            this(ring, contact, score, level, savedObjectFlags, player, powerupArt, background, cameraArt,
                    checkpointState, timeWarpSavedState, null);
        }

        /** Legacy explicit ingress has no represented time-warp saved bytes. */
        RestartCarry(CdRingGlobals ring, CdPlayerBody.ContactGlobals contact,
                     CdOpeningObjectRuntime.ScoreGlobals score, CdLevelGlobals level, CdAssetBytes savedObjectFlags,
                     CdPlayerFrameRuntime.RestartIngress player, CdPowerupArtRuntime.State powerupArt,
                     BackgroundBuffers background, CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState) {
            this(ring, contact, score, level, savedObjectFlags, player, powerupArt, background, cameraArt,
                    checkpointState, CdTimeWarpSaveData.State.unknown());
        }
    }

    /**
     * LevelStart .NoReset/.NormalLoad (Level/Main.asm:37-53,154-216,278-329).
     * Present checkpoint0 retains its existing policy. The checkpoint path carries its
     * actual ring/checkpoint/spawn/HUD/section values until the new visit's native writers;
     * saved payload is not read here. Missing title-loaded bit stops before retirement.
     * This pure policy uses explicit values: score/lives/history/Sonic art and powerup VRAM carry; ClearFlowers
     * clears powerup CPU only. DEV omits fade/title/HUD/music/PLC rows, uses seed0 in install, carries rawHeld as
     * the last represented fade-time pad read, and keeps HUD flags/control lock at the existing DEV values.
     */
    static RestartCarry restartCarry(CdRingGlobals ring, CdLevelGlobals level, CdPlayerBody.ContactGlobals contact,
                                     CdOpeningObjectRuntime.ScoreGlobals score, CdAssetBytes savedFlags,
                                     CdPlayerBody.Environment environment, CdPlayerBody.State actor,
                                     CdPlayerArtRuntime.State art, CdPowerupArtRuntime.State powerupArt,
                                     BackgroundBuffers background,
                                     CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState) {
        return restartCarry(ring, level, contact, score, savedFlags, environment, actor, art, powerupArt,
                background, cameraArt, checkpointState, CdTimeWarpSaveData.State.unknown());
    }

    /** Actual visit handoff preserves the distinct saved value without admitting a warp load. */
    static RestartCarry restartCarry(CdRingGlobals ring, CdLevelGlobals level, CdPlayerBody.ContactGlobals contact,
                                     CdOpeningObjectRuntime.ScoreGlobals score, CdAssetBytes savedFlags,
                                     CdPlayerBody.Environment environment, CdPlayerBody.State actor,
                                     CdPlayerArtRuntime.State art, CdPowerupArtRuntime.State powerupArt,
                                     BackgroundBuffers background,
                                     CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState,
                                     CdTimeWarpSaveData.State timeWarpSavedState) {
        return restartCarry(ring, level, contact, score, savedFlags, environment, actor, art, powerupArt,
                background, cameraArt, checkpointState, timeWarpSavedState, null);
    }

    /** Actual module handoff borrows its current immutable programme value; pure callers remain unknown. */
    private static RestartCarry restartCarry(CdRingGlobals ring, CdLevelGlobals level,
                                     CdPlayerBody.ContactGlobals contact, CdOpeningObjectRuntime.ScoreGlobals score,
                                     CdAssetBytes savedFlags, CdPlayerBody.Environment environment, CdPlayerBody.State actor,
                                     CdPlayerArtRuntime.State art, CdPowerupArtRuntime.State powerupArt,
                                     BackgroundBuffers background,
                                     CdOpeningObjectRuntime.CameraArtRestartIngress cameraArt,
                                     CdOpeningObjectRuntime.CheckpointOwnerState checkpointState,
                                     CdTimeWarpSaveData.State timeWarpSavedState, LevelProgramState program) {
        Objects.requireNonNull(ring); Objects.requireNonNull(level); Objects.requireNonNull(contact);
        Objects.requireNonNull(score); Objects.requireNonNull(savedFlags); Objects.requireNonNull(environment);
        Objects.requireNonNull(actor); Objects.requireNonNull(art); Objects.requireNonNull(powerupArt);
        Objects.requireNonNull(background, "explicit carried background buffers");
        Objects.requireNonNull(cameraArt, "explicit carried current camera art and Nemesis table");
        Objects.requireNonNull(checkpointState, "explicit carried checkpoint live/saved owner");
        Objects.requireNonNull(timeWarpSavedState, "explicit carried time-warp saved value");
        if (savedFlags.length() != CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
            throw new IllegalArgumentException("Complete native saved-object bank required");
        }
        if (ring.lives() == 0) throw new UnsupportedRestart(RestartBoundary.GAME_OVER_EXIT);
        var gates = environment.movement().gates();
        boolean start = level.checkpoint() == 0 && level.spawnMode() == 0;
        boolean checkpoint = level.checkpoint() != 0 && level.spawnMode() == 1
                && (level.plcLoadFlags() & 3) == 3;
        // Main:271-275's title-omitted HIGH clear cannot follow a checkpoint LoadSectionArt
        // and be silently reordered. Only the actually title-loaded skip branch is admitted.
        if (level.levelRestart() != 1 || !(start || checkpoint)
                || (level.plcLoadFlags() & 1) == 0 || level.timeOver() != 0
                || (level.powerup() != 0 && level.powerup() != 8)
                || gates.zoneAct() != 0 || gates.timeZone() != 1 || environment.globals().timeAttackMode() != 0) {
            throw new UnsupportedRestart(RestartBoundary.RESTART_PATH);
        }
        var debug = environment.movement().globals();
        var player = new CdPlayerFrameRuntime.RestartIngress(actor.history().bytes(), art.cpuBuffer(), art.residentBank(),
                debug.debugAngle(), debug.debugAngleShift(), debug.debugQuadrant(), debug.debugFloorDistance(),
                environment.globals().debugCheat(), environment.controls().rawHeld());
        return new RestartCarry(checkpoint ? ring : new CdRingGlobals(0, ring.lives(), 0, 0, 0, 0, 0, 0),
                new CdPlayerBody.ContactGlobals(0, 0, contact.combineRing()),
                new CdOpeningObjectRuntime.ScoreGlobals(score.score(), score.nextLifeScore(), 0),
                checkpoint ? new CdLevelGlobals(level.levelRestart(), level.updateHudTime(), level.checkpoint(),
                        level.spawnMode(), level.plcLoadFlags() | 3, 0, 2, 0, 0)
                        : new CdLevelGlobals(0, 0, 0, 0, level.plcLoadFlags() | 3, 0, 2, 0, 0), savedFlags, player,
                new CdPowerupArtRuntime.State(CdAssetBytes.copyOf(new byte[0x480]), powerupArt.residentBank()),
                // LevelStart:225 -> ClearScreen.ClearHScroll:220-226. The extra DBF long lands in
                // the player header. The collapsed cold builder keeps cleared flags/tile, then represents
                // LoadPlayer (Main:438-443) with slotId1; neither load-scroll owner reads that ID.
                new BackgroundBuffers(background.deformationRam(),
                        CdAssetBytes.copyOf(new byte[CdR11aBackgroundScroll.HSCROLL_RAM_BYTES])),
                // Present/checkpoint0 death clears title-loaded bit (Sonic/Main.asm:2468-2472).
                // The represented title-omitted DEV rebuild applies Main:275's addressed HIGH clear.
                // Native immediate PLC3/Std1/title prehistory remains omitted: this table is the
                // LAST REPRESENTED table, not a fabricated post-load Ring code table. VRAM carries.
                new CdOpeningObjectRuntime.CameraArtRestartIngress(cameraArt.residency(),
                        cameraArt.codeTable512(), checkpoint ? cameraArt.sectionWord() : cameraArt.sectionWord() & 0xFF),
                checkpointState, timeWarpSavedState, program);
    }

    boolean restartStaged() { return stagedRestart != null; }
    boolean checkpointRestartStaged() {
        return openingObjectsAdmission && stagedRestart != null
                && stagedRestart.level().checkpoint() != 0 && stagedRestart.level().spawnMode() == 1;
    }
    boolean ownsRestartContext(LevelLoadContext context) {
        return openingObjectsAdmission && stagedRestart != null && context == stagedRestartContext;
    }

    /**
     * Called at the NEXT beforeFrame, after the restart-cut token and old-epoch recording have finished.
     * All preflight precedes retirement. The ordinary load owner emits the one boundary and generation;
     * no generic death/fade request, per-test reset or extra rewind reset is used.
     */
    void restartOpeningWorld() {
        if (!openingObjectsAdmission || stagedRestart != null) {
            throw new IllegalStateException("CD restart requires its unstaged opening owner");
        }
        try {
            requireTerrainPlayerReady(); requireOpeningClosedCut(); playerActor.requireCompletedNativeFrame();
            var program = levelProgramState();
            if (program.phase() == LevelProgramPhase.RETURNED) {
                beginWarpProgram();
                return;
            }
            if (program.phase() == LevelProgramPhase.PAST_PENDING) {
                // IPX.RunLevel:557-573 masks the actual marked period. R11SubCmds:650 selects
                // command1/R11B for Past0, never a checkpoint or an R11A flag-only reload.
                var retained = requireRuntimeSource().timeWarpPrograms().orElseThrow(
                        () -> new UnsupportedRestart(RestartBoundary.WARP_PROGRAM_START));
                if ((playerFrame.environment().movement().gates().timeZone() & 0x7F) != 0
                        || retained.r11b().fileBytes() != 262144) {
                    throw new UnsupportedRestart(RestartBoundary.RESTART_PATH);
                }
                throw new UnsupportedRestart(RestartBoundary.R11B_LOAD);
            }
            if (program.phase() != LevelProgramPhase.LEVEL) {
                throw new IllegalStateException("Old LevelStart cannot be entered twice");
            }
            var objects = openingObjects.captureClosedCut();
            var shared = playerFrame.captureShared(); var actor = playerFrame.captureActor();
            var gates = shared.environment().movement().gates();
            if ((objects.levelGlobals().levelRestart() & 0xFF00) == 0x0100
                    && objects.levelGlobals().spawnMode() == 2
                    && gates.timeZone() >= 0x80 && gates.timeZone() <= 0x82) {
                beginOldLevelStart(program, shared);
                return;
            }
            // No admitted ordinary writer changes these DEV globals yet. A future native owner
            // must extend the actual carry rather than defaulting retained good-future metadata.
            if (!openingObjects.devGlobals().equals(new CdOpeningObjectRuntime.DevGlobals(0, 0))) {
                throw new UnsupportedRestart(RestartBoundary.RESTART_PATH);
            }
            var carry = restartCarry(objects.ringGlobals(), objects.levelGlobals(), objects.contactGlobals(),
                    objects.scoreGlobals(), objects.placement().savedFlags(), shared.environment(), actor.state(),
                    shared.artState(), objects.powerupArtState(), playerPresentation.captureBackgroundBuffers(),
                    new CdOpeningObjectRuntime.CameraArtRestartIngress(objects.sectionArtState().residency(),
                            objects.sectionArtState().codeTable(), objects.sectionArtState().sectionWord()),
                    objects.checkpointState(), openingObjects.timeWarpSavedState(), program);
            var manager = playerMode.getLevelManager();
            stagedRestart = carry;
            stagedRestartContext = SonicCdPlayerInitProfile.openingObjectsRestartContext();
            try { manager.loadLevel(0, LevelLoadMode.FULL, stagedRestartContext); }
            catch (IOException failure) { throw new UncheckedIOException("Cannot reconstruct the CD restart visit", failure); }
        } catch (RuntimeException | Error failure) {
            closePlayerCycleAfterFailure(failure);
            throw failure;
        } finally {
            stagedRestart = null;
            stagedRestartContext = null;
        }
    }

    /**
     * Native LevelStart:19-81 at N1ecc5f3a. This bounded marked-period act0 entry owns the
     * scalar prefix and joins Runtime's existing pause/PLC/queue authorities. The actual old
     * BSET bit selects the white fade; special-stage and first-load paths remain typed stops.
     */
    private void beginOldLevelStart(LevelProgramState program, CdPlayerFrameRuntime.SharedState shared) {
        if (program.specialStage() != 0 || (program.palFadeFlags() & 1) == 0
                || (openingObjects.levelGlobals().plcLoadFlags() & 1) == 0) {
            throw new UnsupportedRestart(RestartBoundary.WARP_LEVEL_START);
        }
        boolean forceGoodFuture = program.timeStones() == 0x7F
                && shared.environment().globals().timeAttackMode() == 0;
        // Main:20,29,31,56. Borrow Frame's actual time attack and pause and Runtime's
        // actual goodFuture/plcLoadFlags; no parallel native-global bank is introduced.
        levelProgram = new LevelProgramState(0, program.timeStones(), program.specialStage(), 0,
                program.palFadeFlags(), program.gameMode() | 0x80, 0, 0, LevelProgramPhase.LEVEL);
        openingObjects.beginWarpLevelStart(forceGoodFuture); // Main:35,37,57, including actual ClearPLCs.
        // Main:64 tests the OLD bit, proven set above, and preserves every other incoming bit.
        levelProgram = new LevelProgramState(0, program.timeStones(), program.specialStage(), 0,
                program.palFadeFlags() | 1, program.gameMode() | 0x80, 0, 0, LevelProgramPhase.WHITE_FADE);
    }

    /** One admitted selector12 pulse. FadeToWhite's DBF21 owns exactly22 waits, never a route index. */
    void serviceOldLevelStartWhiteFade(int rawHeldByte) {
        try {
            requireTerrainPlayerReady(); requireOpeningClosedCut(); playerActor.requireCompletedNativeFrame();
            var program = levelProgramState();
            if (program.phase() != LevelProgramPhase.WHITE_FADE || program.whiteFadeSteps() >= 22) {
                throw new IllegalStateException("No old-LevelStart white-fade wait is admitted");
            }
            openingObjects.serviceLevelStartWhiteFade(rawHeldByte);
            int completed = program.whiteFadeSteps() + 1;
            levelProgram = new LevelProgramState(program.demoMode(), program.timeStones(), program.specialStage(),
                    program.usePlayer2(), program.palFadeFlags(), program.gameMode(), program.levelStarted(),
                    completed, LevelProgramPhase.WHITE_FADE);
            if (completed == 22) {
                // Main:69 precedes direction clear:72, then complete restart WORD clear:75.
                levelProgram = new LevelProgramState(program.demoMode(), program.timeStones(), program.specialStage(),
                        program.usePlayer2(), program.palFadeFlags() & ~1, program.gameMode(), program.levelStarted(),
                        completed, LevelProgramPhase.WHITE_FADE);
                openingObjects.finishWarpLevelStart();
                levelProgram = new LevelProgramState(program.demoMode(), program.timeStones(), program.specialStage(),
                        program.usePlayer2(), program.palFadeFlags() & ~1, program.gameMode(), program.levelStarted(),
                        completed, LevelProgramPhase.RETURNED);
            }
        } catch (RuntimeException | Error failure) {
            closePlayerCycleAfterFailure(failure);
            throw failure;
        }
    }

    /** IPX.RunMMD:1372-1387: stop FM, one blank semantic pulse, then bounded WARP setup. */
    private void beginWarpProgram() {
        requireTerrainPlayerReady(); requireOpeningClosedCut(); playerActor.requireCompletedNativeFrame();
        var program = levelProgramState();
        if (program.phase() != LevelProgramPhase.RETURNED || warpState != null || !programSounds.isEmpty())
            throw new IllegalStateException("WARP entry requires the actual completed old programme");
        if (warpScene == null) {
            var retained = requireRuntimeSource().timeWarpPrograms().orElseThrow(
                    () -> new UnsupportedRestart(RestartBoundary.WARP_PROGRAM_START));
            try {
                var assets = CdWarpAssets.fromPrograms(retained);
                var scene = new CdWarpScene(assets);
                // Explicit semantic setup cut: native Start cleared its own counter; synchronous
                // asset/setup work contributes no represented pulse. This is not a measured console
                // IRQ phase or a copy of the suspended level's V-int/read-controller state.
                var initial = scene.initial(0);
                scene.validate(initial);
                warpAssets = assets; warpScene = scene;
            } catch (IOException failure) { throw new UncheckedIOException("Cannot bind original WARP programme", failure); }
        }
        var initial = warpScene.initial(0);
        var sounds = List.of(ProgramSound.STOP_FM);
        warpState = initial; programSounds = sounds;
        levelProgram = withProgramPhase(program, LevelProgramPhase.WARP);
        // No old Frame/ObjectManager V-int is serviced. Its owners remain inactive and capturable.
    }

    /** One current controller HOLD row; neither draw nor restore calls this source owner. */
    void serviceWarpProgram(int rawHeldByte) {
        try {
            requireTerrainPlayerReady(); requireOpeningClosedCut(); playerActor.requireCompletedNativeFrame();
            var program = levelProgramState();
            if (program.phase() != LevelProgramPhase.WARP || warpScene == null || warpState == null)
                throw new IllegalStateException("No executing WARP programme");
            if (warpState.phase() == CdWarpScene.Phase.RETURNED) {
                var ready = java.util.List.of(ProgramSound.STOP_FM, ProgramSound.WARP_DIRECT_FM_D8, ProgramSound.STOP_FM);
                // WARP's own RunMMD return tail owns a second blank pulse/FMC_STOP. The final
                // scene remains inspectable; the following beforeFrame owns the actual Past load.
                programSounds = ready;
                levelProgram = withProgramPhase(program, LevelProgramPhase.PAST_PENDING);
                return;
            }
            var before = warpState;
            var result = warpScene.step(before, rawHeldByte);
            var expected = before.phase() == CdWarpScene.Phase.PREPARE
                    ? List.of(CdWarpScene.SoundRequest.FM_D8) : List.<CdWarpScene.SoundRequest>of();
            if (!result.sounds().equals(expected)) throw new IllegalStateException("WARP sound request left its actual source cut");
            var ready = expected.isEmpty() ? programSounds : List.of(ProgramSound.STOP_FM, ProgramSound.WARP_DIRECT_FM_D8);
            warpScene.validate(result.state());
            warpState = result.state(); programSounds = ready;
        } catch (RuntimeException | Error failure) { closePlayerCycleAfterFailure(failure); throw failure; }
    }

    private static LevelProgramState withProgramPhase(LevelProgramState p, LevelProgramPhase phase) {
        return new LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(), p.usePlayer2(),
                p.palFadeFlags(), p.gameMode(), p.levelStarted(), p.whiteFadeSteps(), phase);
    }

    private void validateWarpProgramCut(LevelProgramState program, CdWarpScene.State scene, List<ProgramSound> sounds) {
        Objects.requireNonNull(sounds, "programme sound ledger");
        boolean selected = program.phase() == LevelProgramPhase.WARP || program.phase() == LevelProgramPhase.PAST_PENDING;
        if (!selected) {
            if (scene != null || !sounds.isEmpty()) throw new IllegalArgumentException("Level capture carries foreign programme state");
            return;
        }
        if (warpScene == null || warpAssets == null || scene == null) throw new IllegalArgumentException("Missing actual WARP binding");
        warpScene.validate(scene);
        boolean returned = program.phase() == LevelProgramPhase.PAST_PENDING;
        if (returned && scene.phase() != CdWarpScene.Phase.RETURNED) throw new IllegalArgumentException("WARP did not return");
        var expected = returned ? List.of(ProgramSound.STOP_FM, ProgramSound.WARP_DIRECT_FM_D8, ProgramSound.STOP_FM)
                : scene.phase() == CdWarpScene.Phase.DELAY || scene.phase() == CdWarpScene.Phase.PREPARE
                        ? List.of(ProgramSound.STOP_FM) : List.of(ProgramSound.STOP_FM, ProgramSound.WARP_DIRECT_FM_D8);
        if (!sounds.equals(expected)) throw new IllegalArgumentException("Programme requests disagree with the selected native cut");
    }

    // This stable module adapter owns shared state only. Actor Body/history/display,
    // sounds and the actual sprite packet live exclusively in the actor extra.
    private final RewindSnapshottable<CdPlayerFrameRuntime.SharedState> sharedPlayerAdapter = new RewindSnapshottable<>() {
        @Override public String key() { return SHARED_REWIND_KEY; }
        @Override public CdPlayerFrameRuntime.SharedState capture() {
            requireTerrainPlayerReady();
            playerActor.requireCompletedNativeFrame();
            return playerFrame.captureShared();
        }
        @Override public void restore(CdPlayerFrameRuntime.SharedState snapshot) {
            requireTerrainPlayerReady();
            playerActor.requireCompletedNativeFrame();
            playerFrame.validateSharedRestore(snapshot);
            playerFrame.restoreShared(snapshot);
        }
    };

    // One shared opening payload. Body/history/display remain in actor Extra;
    // FG/BG accumulator/camera remain together in their existing scroll adapter. The two
    // immutable render-scroll cuts belong here, once, beside the CPU/display SAT.
    private record OpeningSharedState(CdPlayerFrameRuntime.SharedState frame,
                                      CdOpeningObjectRuntime.Snapshot objects,
                                      SonicCdPlayerPresentation.ScrollPublication scroll,
                                      LevelProgramState program, CdWarpScene.State warp,
                                      List<ProgramSound> sounds) {
        private OpeningSharedState { sounds = List.copyOf(sounds); }
    }
    private final NativeRewindRestoreGuard<OpeningSharedState> sharedOpeningAdapter = new NativeRewindRestoreGuard<>() {
        @Override public String key() { return OPENING_SHARED_REWIND_KEY; }
        @Override public OpeningSharedState capture() {
            requireTerrainPlayerReady();
            requireOpeningClosedCut();
            playerActor.requireCompletedNativeFrame();
            return new OpeningSharedState(playerFrame.captureShared(), openingObjects.captureClosedCut(),
                    playerPresentation.captureScrollPublication(), levelProgramState(), warpState, programSounds);
        }
        @Override public void validateRestore(OpeningSharedState snapshot) {
            requireTerrainPlayerReady();
            requireOpeningClosedCut();
            playerActor.requireCompletedNativeFrame();
            playerFrame.captureShared(); // Pure pending-input/entitlement guard before runtime preflight.
            Objects.requireNonNull(snapshot, "opening shared capture");
            // All preflights are pure and run BEFORE any partition changes.
            playerFrame.validateSharedRestore(snapshot.frame());
            openingObjects.validateRestore(snapshot.objects());
            playerPresentation.validateScrollPublication(snapshot.scroll());
            validateLevelProgramCut(snapshot.program(), snapshot.frame(), snapshot.objects());
            validateWarpProgramCut(snapshot.program(), snapshot.warp(), snapshot.sounds());
        }
        @Override public void restore(OpeningSharedState snapshot) {
            validateRestore(snapshot); // Keep direct adapter restore guarded as well as composite restore.
            try {
                playerFrame.restoreShared(snapshot.frame());
                openingObjects.restore(snapshot.objects());
                playerPresentation.restoreScrollPublication(snapshot.scroll());
                levelProgram = snapshot.program(); // Exact immutable adoption; no entry, fade or scene replay.
                warpState = snapshot.warp(); programSounds = snapshot.sounds();
            } catch (RuntimeException | Error failure) {
                closePlayerCycleAfterFailure(failure);
                throw failure;
            }
        }
    };

    private static void validateLevelProgramCut(LevelProgramState program,
                                               CdPlayerFrameRuntime.SharedState frame,
                                               CdOpeningObjectRuntime.Snapshot objects) {
        Objects.requireNonNull(program, "native opening programme capture");
        if (program.phase() == LevelProgramPhase.LEVEL) return;
        var level = objects.levelGlobals();
        var gates = frame.environment().movement().gates();
        boolean returned = program.phase() == LevelProgramPhase.RETURNED
                || program.phase() == LevelProgramPhase.WARP || program.phase() == LevelProgramPhase.PAST_PENDING;
        if (program.demoMode() != 0 || program.specialStage() != 0 || program.usePlayer2() != 0
                || program.levelStarted() != 0 || (program.gameMode() & 0x80) == 0
                || frame.paused() || level.spawnMode() != 2 || gates.zoneAct() != 0
                || gates.timeZone() < 0x80 || gates.timeZone() > 0x82
                || (returned ? (program.palFadeFlags() & 1) != 0 || level.levelRestart() != 0
                        || gates.timeWarpDirection() != 0
                        : (program.palFadeFlags() & 1) == 0 || program.whiteFadeSteps() >= 22
                                || (level.levelRestart() & 0xFF00) != 0x0100)) {
            throw new IllegalArgumentException("Native programme capture is inconsistent with its existing owners");
        }
    }

    /** The stock/default entry retains its STATIC/HOLD/no-actor contract. */
    public SonicCdGameModule() { this(false, false); }

    private SonicCdGameModule(boolean playerAdmission, boolean openingObjectsAdmission) {
        this.playerAdmission = playerAdmission;
        this.openingObjectsAdmission = openingObjectsAdmission;
        rules = playerAdmission ? new CdPlayerEngineRules() : new SonicCdStaticRules();
        playerPresentation = playerAdmission ? new SonicCdPlayerPresentation(this) : null;
        playerProfile = playerAdmission ? new SonicCdPlayerInitProfile(this, profile) : null;
        characters = playerAdmission ? PlayableCharacterRegistry.empty().register(CharacterKey.SONIC,
                new CharacterDefinition(CharacterKey.SONIC, openingObjectsAdmission
                        ? "Sonic (CD opening objects development)" : "Sonic (CD terrain development)",
                        this::constructTerrainPlayer, null, PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false,
                        code -> { throw SonicCd.unsupported("generic playable-art loading; CD owns its complete resident bank"); }))
                : PlayableCharacterRegistry.empty();
    }

    /** Explicit private development entry; does not change the stock registry or menu. */
    public static SonicCdGameModule terrainOnlyDevelopmentPlayer() { return new SonicCdGameModule(true, false); }

    /** Private USA R11A DEV ingress; stock registry/configuration/menu remain unchanged. */
    public static SonicCdGameModule openingObjectsDevelopmentPlayer() { return new SonicCdGameModule(true, true); }

    @Override public String getIdentifier() {
        return openingObjectsAdmission ? "Sonic CD (development USA R11A opening objects PLAYER)"
                : playerAdmission ? "Sonic CD (development terrain-only R11A PLAYER)" : "Sonic CD (development STATIC R11A)";
    }
    @Override public GameId getGameId() { return GameId.SCD; }

    @Override public Game createGame(GameDataSource dataSource) {
        CdGameDataSource candidate = SonicCd.requireSource(dataSource);
        authenticateOpeningSource(candidate); // Actual retained media before replacing source/presentation state.
        if (source != null && !source.identity().equals(candidate.identity())) {
            throw new IllegalArgumentException("CD module is pinned to another authenticated source; open a new module/world");
        }
        if (playerAdmission && playerFrame != null && !playerFrame.closed()) {
            throw new IllegalStateException("Retire the admitted CD player before binding a fresh level");
        }
        source = candidate;
        zones.bind(candidate);
        if (playerAdmission) playerPresentation.bind(candidate.assets(), backgroundLoadBuffers());
        else presentation.bind(candidate.assets());
        return new SonicCd(candidate);
    }

    boolean terrainPlayerAdmission() { return playerAdmission; }
    boolean openingObjectsAdmission() { return openingObjectsAdmission; }

    private void authenticateOpeningSource(CdGameDataSource candidate) {
        if (!openingObjectsAdmission || authenticatedOpeningAssets == candidate.assets()) return;
        try { candidate.assets().openingObjects(); } // USA + actual MMD hash/tables/art, not a declared region alone.
        catch (IOException failure) { throw new UncheckedIOException("Cannot authenticate CD opening assets", failure); }
        authenticatedOpeningAssets = candidate.assets();
    }

    /** Load admission; the presentation can call this before each row's input/service too. */
    void validateTerrainPlayerRequest() {
        if (!playerAdmission) throw new IllegalStateException("Not the named CD player admission");
        var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode(), "gameplay mode");
        var world = mode.getWorldSession();
        if (world.rootGameModule() != this || world.resolvedGameModule() != this
                || world.getPlayableCharacterRegistry() != characters || WorldSessionPolicyAccess.hasPolicies(world)
                || GameplayInputFilterAccess.current(mode) != GameplayInputFilter.IDENTITY) {
            throw SonicCd.unsupported("decorated modules, world policies or input filters in terrain-player admission");
        }
        var config = GameServices.configuration();
        String sidekick = config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        if (!"sonic".equals(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE))
                || (sidekick != null && !sidekick.isBlank())
                || config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED)) {
            throw SonicCd.unsupported("terrain-player admission requires Sonic alone, without a donor");
        }
        if (mode.getCamera().getWidth() != 320 || mode.getCamera().getHeight() != 224) {
            throw SonicCd.unsupported("terrain-player admission requires an actual 320x224 viewport");
        }
        requireRuntimeSource();
        // A fresh load may retire only this admission's previous actor. Never clear
        // an unknown roster in order to make the new load appear admissible.
        for (var actor : mode.getSpriteManager().getAllSprites()) {
            if (mode != playerMode || actor != playerActor) {
                throw SonicCd.unsupported("foreign or additional terrain-player roster");
            }
        }
        if (!mode.getSpriteManager().getRegisteredSidekicks().isEmpty()) {
            throw SonicCd.unsupported("terrain-player sidekicks");
        }
        if (mode == playerMode && playerActor != null
                && (com.openggf.sprites.managers.SpriteManagerInternalAccess.motionMutators(playerActor) != null
                || !com.openggf.sprites.playable.PlayableMutatorPolicy.STOCK.equals(
                        com.openggf.sprites.playable.PlayableSpriteInternalAccess.mutatorPolicy(playerActor)))) {
            throw SonicCd.unsupported("terrain-player motion or presentation mutators");
        }
    }

    private CdPlayableSonic constructTerrainPlayer(String code, int centreX, int centreY) {
        validateTerrainPlayerRequest();
        if (!constructingPlayer || playerMode != SessionManager.getCurrentGameplayMode()
                || playerFrame == null || playerFrame.closed() || playerActor != null
                || !"sonic".equals(code)
                || centreX != (playerFrame.state().motion().centreX16_16() >> 16)
                || centreY != (playerFrame.state().motion().centreY16_16() >> 16)) {
            throw new IllegalStateException("CD factory is available only for its staged cold player");
        }
        if (!openingObjectsAdmission) return new CdPlayableSonic(code, playerFrame, GameServices.graphics());
        var runtime = Objects.requireNonNull(openingObjects, "staged opening pass");
        return new CdPlayableSonic(code, playerFrame, GameServices.graphics(), new CdPlayableSonic.ObjectPass() {
            @Override public CdPlayerFrameRuntime.BodyPublication runBody(int levelFrames) {
                return runtime.runPlayerBody(levelFrames);
            }
            @Override public void enqueuePlayer(CdPlayerBody.DisplayEligibility cut) { runtime.enqueuePlayer(cut); }
            @Override public void close() { runtime.close(); }
        });
    }

    /** Runs only after the composed terrain/reset/adapter stages have finished. */
    void installTerrainPlayer() {
        validateTerrainPlayerRequest();
        if (playerFrame != null || playerActor != null) throw new IllegalStateException("CD player already staged");
        var mode = Objects.requireNonNull(SessionManager.getCurrentGameplayMode());
        var candidate = requireRuntimeSource();
        if (!(mode.getLevelManager().getCurrentLevel() instanceof SonicCdLevel level)
                || level.assets() != candidate.assets() || !mode.getSpriteManager().getAllSprites().isEmpty()) {
            throw new IllegalStateException("CD player requires its installed source terrain and an empty roster");
        }
        playerMode = mode;
        try {
            var carry = stagedRestart;
            // A fresh explicit .BeginLevel semantic ingress establishes this lineage once.
            // Same-program death/checkpoint assembly retains the actual handoff, including knowledge.
            var nextProgram = !openingObjectsAdmission ? null : carry == null
                    ? LevelProgramState.freshBeginLevel()
                    : Objects.requireNonNull(carry.program(), "Same-program reload requires its actual programme carry");
            // Declared DEV native V-int seed0 at this admitted cut, not measured retail entry.
            var loadVisit = openingObjectsAdmission ? new CheckpointLoadVisit(carry, candidate.assets(), mode) : null;
            boolean checkpoint = checkpointRestartStaged();
            playerFrame = openingObjectsAdmission
                    ? (carry == null ? CdPlayerFrameRuntime.externalTouchRequiredDevelopment(candidate.assets(), 0)
                            : checkpoint ? CdPlayerFrameRuntime.checkpointRestartDevelopment(
                                    candidate.assets(), 0, carry.player(), loadVisit::initialize)
                            : CdPlayerFrameRuntime.externalTouchRequiredDevelopment(candidate.assets(), 0, carry.player()))
                    : CdPlayerFrameRuntime.terrainOnlyDevelopment(candidate.assets());
            if (checkpoint) playerPresentation.completeCheckpointSetup(playerFrame);
            // LevelSizeLoad + one load-time LevelScroll (Main:245-246), live FG's forced flag
            // (:247) and initial BG draw (:249) all precede LoadPlayer/ObjSonic_Init.
            // No native selector-C load wait/fade V-int is fabricated by this DEV entry.
            playerPresentation.completeLoadScroll(playerFrame, loadVisit == null ? 0 : loadVisit.level.updateHudTime());
            // PP LoadLevelData:37-39 sees the newly restored spawn byte, before Main:301.
            // Read actual post-scroll FG X, never the engine camera's not-yet-bound projection.
            var loadSectionCameraX = checkpoint && loadVisit.level.spawnMode() != 0
                    ? OptionalInt.of(playerPresentation.readCheckpointLoadWord(CdCheckpointLoadData.StoreTarget.FG_X))
                    : OptionalInt.empty();
            if (loadVisit != null) loadVisit.finishMain(playerFrame);
            if (openingObjectsAdmission) {
                var banks = playerPresentation.reserveOpeningPictureBank();
                var camera = mode.getCamera();
                try {
                    // Explicit DEVELOPMENT startup policy, never title/new-game proof.
                    // Reserved owners 1..31 absent, goodFuture=0, object art preloaded.
                    var initialRing = loadVisit.ring;
                    var initialContact = carry == null ? new CdPlayerBody.ContactGlobals(0, 0, 0) : carry.contact();
                    var initialScore = carry == null ? new CdOpeningObjectRuntime.ScoreGlobals(0, 5000, 0) : carry.score();
                    var initialLevel = loadVisit.level;
                    var checkpointBindings = new CdOpeningObjectRuntime.CheckpointBindings(loadVisit.checkpointState,
                            checkpointReads(playerFrame, candidate.assets(), mode), loadSectionCameraX);
                    var timeWarpWorld = new TimeWarpWorldBridge(playerFrame, candidate.assets(), mode);
                    var timeWarpBindings = new CdOpeningObjectRuntime.TimeWarpBindings(
                            carry == null ? CdTimeWarpSaveData.State.unknown() : carry.timeWarpSavedState(), timeWarpWorld);
                    var foreground = checkpoint
                            ? new CdObjectDrawQueue.CameraWords(
                                    playerPresentation.readCheckpointLoadWord(CdCheckpointLoadData.StoreTarget.FG_X),
                                    playerPresentation.readCheckpointLoadWord(CdCheckpointLoadData.StoreTarget.FG_Y))
                            : new CdObjectDrawQueue.CameraWords(camera.getX() & 0xFFFF, camera.getY() & 0xFFFF);
                    openingObjects = carry == null ? CdOpeningObjectRuntime.usaR11aOpeningDevEmptySatPreloadedArt(
                            candidate.assets(), playerFrame, initialRing, initialContact, initialScore,
                            // Declared post-LevelStart level globals (Level/Main.asm): levelRestart 0 (:299);
                            // updateHUDTime 0 is the DEV HUD omission, native :304 writes 1; checkpoint 0 (:41 on
                            // a first entry, no Checkpoint owner); spawnMode 0 (:301); plcLoadFlags $03 (BSET #0
                            // :37, BSET #1 :271); flowerCount 0 (:168); fadeEnableDisplay 2 (:314 writes 1, the
                            // next VInt_PalFade :897-899 adds 1); powerup 0 (:167); timeOver 0 (:293).
                            initialLevel, checkpointBindings,
                            new byte[CdObjectDrawQueue.SAT_BYTE_SIZE], banks,
                            foreground, playerFrame::applyObjectPrimaryAngle, timeWarpBindings)
                            : CdOpeningObjectRuntime.usaR11aRestartDevEmptySatPreloadedArt(candidate.assets(), playerFrame,
                                    initialRing, initialContact, initialScore, initialLevel, checkpointBindings, carry.savedObjectFlags(),
                                    carry.powerupArt(), carry.cameraArt(), new byte[CdObjectDrawQueue.SAT_BYTE_SIZE], banks, foreground,
                                    playerFrame::applyObjectPrimaryAngle, timeWarpBindings);
                    timeWarpWorld.bind(openingObjects);
                    openingPlc = composeOpeningPlc(openingObjects, playerFrame);
                } catch (IOException failure) {
                    throw new UncheckedIOException("Cannot stage authenticated CD opening objects", failure);
                }
            }
            if (!openingObjectsAdmission) terrainPlc = composeTerrainPlc(playerFrame);
            var definition = mode.getWorldSession().getPlayableCharacterRegistry().find(CharacterKey.SONIC).orElseThrow();
            var loadedMotion = playerFrame.state().motion();
            int centreX = loadedMotion.centreX16_16() >> 16;
            int centreY = loadedMotion.centreY16_16() >> 16;
            constructingPlayer = true;
            try {
                playerActor = (CdPlayableSonic) CharacterConstructionScope.call(CharacterKey.SONIC, () ->
                        CharacterConstructionScope.validateFactoryResult(CharacterKey.SONIC,
                                definition.spriteFactory().create("sonic", centreX, centreY)));
            } finally {
                constructingPlayer = false;
            }
            mode.getSpriteManager().addSprite(playerActor);
            playerPresentation.bindFrame(playerFrame, playerActor, openingObjects);
            levelProgram = nextProgram;
            mode.registerLevelAdapters(mode.getLevelManager());
            mode.registerGameModuleRewindAdapters();
            var rewind = Objects.requireNonNull(mode.getRewindRegistry(), "rewind registry");
            rewind.deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
            rewind.registerPostRestoreCallback(RESTORE_CALLBACK_KEY, () -> {
                requireTerrainPlayerReady();
                playerPresentation.afterRestore();
            });
            requireTerrainPlayerReady();
        } catch (RuntimeException | Error failure) {
            try { closeTerrainPlayer(); }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    /**
     * Construction-local actual load owners. Nothing here survives Runtime adoption or
     * enters a rewind partition. The privately minted Frame port cannot escape its callback.
     * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Load Saved Data:49-84.
     */
    /**
     * Main.ClearFlowers:178-184 clears $FF1600..$FF2000. The existing Ring owner
     * represents timeStopTimer at $FF190E (WORD) and ringAnimTimer/frame at $FF1912/13.
     * Apply this only to the new visit, before LoadCheckpointData, retaining the
     * incoming rings/lives/HUD values until their own reached native writers.
     */
    static CdRingGlobals clearFlowersRingGlobals(CdRingGlobals before) {
        Objects.requireNonNull(before, "pre-ClearFlowers ring globals");
        return new CdRingGlobals(before.rings(), before.lives(), before.livesFlags(),
                before.updateHudRings(), before.updateHudLives(), 0, 0, 0);
    }

    private final class CheckpointLoadVisit implements CdCheckpointLoadData.Port {
        private final RestartCarry carry;
        private final CdR11aAssets assets;
        private final GameplayModeContext mode;
        private final CdCheckpointSaveData.State saved;
        private CdOpeningObjectRuntime.CheckpointOwnerState checkpointState;
        private CdRingGlobals ring;
        private CdLevelGlobals level;
        private CdPlayerFrameRuntime.CheckpointLoadPort framePort;
        private boolean started;
        private boolean loading;
        private boolean loadFinished;
        private boolean finalized;
        private int cursor;
        // One ephemeral expected-value/knowledge cut, never a saved RAM image.
        private CdCheckpointLoadData.StoreTarget observedTarget;
        private OptionalInt observedValue;
        private CdOpeningObjectRuntime.CheckpointOwnerState observedCheckpoint;
        private CdRingGlobals observedRing;
        private CdLevelGlobals observedLevel;

        private CheckpointLoadVisit(RestartCarry carry, CdR11aAssets assets, GameplayModeContext mode) {
            this.carry = carry; this.assets = assets; this.mode = mode;
            ring = clearFlowersRingGlobals(carry == null ? new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0) : carry.ring());
            level = carry == null ? new CdLevelGlobals(0, 0, 0, 0, 0x03, 0, 2, 0, 0) : carry.level();
            var incoming = carry == null ? new CdOpeningObjectRuntime.CheckpointOwnerState(
                    CdCheckpointLiveAux.unknown(), CdCheckpointSaveData.State.unknown()) : carry.checkpointState();
            // Actual admitted Main.ClearMiscVars:202-208 writes the four water bytes only.
            // Time/saved bytes survive. Unknown MAINVARS is never replaced with a zero image.
            checkpointState = new CdOpeningObjectRuntime.CheckpointOwnerState(
                    CdCheckpointLiveAux.clearMiscAtLevelStart(incoming.live()), incoming.saved());
            saved = incoming.saved();
            requireConstruction();
        }

        private void requireConstruction() {
            if (finalized || !openingObjectsAdmission || stagedRestart != carry || playerMode != mode
                    || SessionManager.getCurrentGameplayMode() != mode || playerActor != null || openingObjects != null
                    || !(mode.getLevelManager().getCurrentLevel() instanceof SonicCdLevel current)
                    || current.assets() != assets || checkpointState.saved() != saved) {
                throw new IllegalStateException("Checkpoint construction belongs to another or completed source visit");
            }
        }

        private void requireLoad() {
            requireConstruction();
            if (!loading || framePort == null || playerFrame != null || !checkpointRestartStaged()) {
                throw new IllegalStateException("Checkpoint load requires its one fresh Frame construction callback");
            }
        }

        private void initialize(CdPlayerFrameRuntime.CheckpointLoadPort actualFrame) {
            requireConstruction();
            if (started || carry == null || !checkpointRestartStaged() || playerFrame != null) {
                throw new IllegalStateException("Checkpoint load initializer cannot be reused");
            }
            started = true; loading = true;
            framePort = Objects.requireNonNull(actualFrame, "privately minted Frame load port");
            try {
                var result = CdCheckpointLoadData.load(carry.level().spawnMode(), 0, saved, this);
                int count = 19 + ((level.spawnMode() & 0x80) != 0 ? 1 : 0);
                if (cursor != count || result.completedNativeStores().size() != count) {
                    throw new IllegalStateException("R11A checkpoint load did not complete its exact native store prefix");
                }
                for (int index = 0; index < 19; index++) {
                    if (result.completedNativeStores().get(index) != CdCheckpointLoadData.NativeStore.values()[index]) {
                        throw new IllegalStateException("Checkpoint completion metadata differs from actual native cursor");
                    }
                }
                if (count == 20 && result.completedNativeStores().get(19) != CdCheckpointLoadData.NativeStore.SET_LEFT_BOUND) {
                    throw new IllegalStateException("Negative restored spawn requires the one wrapped left-bound store");
                }
                loadFinished = true; // Completed observations never drive stores, replay or a resume.
            } finally {
                loading = false; framePort = null; observedTarget = null; observedValue = null;
                observedCheckpoint = null; observedRing = null; observedLevel = null;
            }
        }

        @Override public CdCheckpointSaveData.State currentSaved() {
            requireLoad();
            return checkpointState.saved();
        }

        @Override public OptionalInt read(CdCheckpointLoadData.StoreTarget target) {
            requireLoad();
            var value = readTarget(Objects.requireNonNull(target, "reached load target"));
            observedTarget = target; observedValue = value; observedCheckpoint = checkpointState;
            observedRing = ring; observedLevel = level;
            return value;
        }

        private OptionalInt readTarget(CdCheckpointLoadData.StoreTarget target) {
            return switch (target) {
                case SPAWN_MODE -> OptionalInt.of(level.spawnMode());
                case PRIMARY_X, PRIMARY_Y, BOTTOM_BOUND, LEFT_BOUND, MINI_SONIC ->
                        OptionalInt.of(framePort.read(frameField(target)));
                case RINGS -> OptionalInt.of(ring.rings());
                case LIVES_FLAGS -> OptionalInt.of(ring.livesFlags());
                case TIME_LONG -> knownAux(CdCheckpointLiveAux.Field.TIME_LONG);
                case TIME_FRAMES -> checkpointState.live().knownByte(0xFF1517);
                case TIME_SECONDS -> checkpointState.live().knownByte(0xFF1516);
                case WATER_ROUTINE -> knownAux(CdCheckpointLiveAux.Field.WATER_ROUTINE);
                case WATER_HEIGHT2 -> knownAux(CdCheckpointLiveAux.Field.WATER_HEIGHT2);
                case WATER_FULLSCREEN -> knownAux(CdCheckpointLiveAux.Field.WATER_FULLSCREEN);
                case DEST_BOTTOM_BOUND, FG_X, FG_Y, BG_X, BG_Y, BG2_X, BG2_Y, BG3_X, BG3_Y ->
                        OptionalInt.of(playerPresentation.readCheckpointLoadWord(target));
            };
        }

        private OptionalInt knownAux(CdCheckpointLiveAux.Field field) {
            int value = 0;
            for (int part = 0; part < field.width(); part++) {
                var next = checkpointState.live().knownByte(field.nativeAddress() + part);
                if (next.isEmpty()) return OptionalInt.empty();
                value = (value << 8) | next.getAsInt();
            }
            return OptionalInt.of(value);
        }

        private CdCheckpointLoadData.NativeStore nextInstruction() {
            if (cursor < 19) return CdCheckpointLoadData.NativeStore.values()[cursor];
            if (cursor == 19 && (level.spawnMode() & 0x80) != 0) {
                return CdCheckpointLoadData.NativeStore.SET_LEFT_BOUND;
            }
            throw new IllegalStateException("No remaining native R11A checkpoint store");
        }

        @Override public void commit(CdCheckpointLoadData.StoreCall call) {
            requireLoad();
            Objects.requireNonNull(call, "reached native checkpoint store");
            var instruction = nextInstruction();
            if (instruction.target() != call.target() || call.target() != observedTarget
                    || checkpointState != observedCheckpoint || ring != observedRing || level != observedLevel
                    || !call.expectedValue().equals(observedValue)
                    || !call.expectedValue().equals(readTarget(call.target()))) {
                throw new IllegalArgumentException("Checkpoint native cursor/current owner/value/knowledge CAS failed");
            }
            // Pure validation at this reached instruction. Unknown saved bytes fail first
            // inside the helper at their native read; no saved field is preread at ingress.
            int nativeValue = switch (instruction) {
                case CLEAR_RINGS, CLEAR_LIVES_FLAGS -> 0;
                case SET_FRAMES_59 -> 59;
                case DECREMENT_SECONDS -> (call.expectedValue().orElseThrow() - 1) & 0xFF;
                case SET_LEFT_BOUND -> (saved.read(CdCheckpointSaveData.SavedField.PRIMARY_X) - 160) & 0xFFFF;
                default -> saved.read(instruction.savedField().orElseThrow());
            };
            if (call.value() != nativeValue) throw new IllegalArgumentException("Not the reached native source value");
            switch (call.target()) {
                case SPAWN_MODE -> level = levelWithSpawn(level, call.value());
                case PRIMARY_X, PRIMARY_Y, BOTTOM_BOUND, LEFT_BOUND, MINI_SONIC -> framePort.store(
                        new CdPlayerFrameRuntime.CheckpointLoadStore(frameField(call.target()),
                                call.expectedValue().orElseThrow(), call.value()));
                case RINGS -> ring = ringWithCount(ring, call.value());
                case LIVES_FLAGS -> ring = ringWithLivesFlags(ring, call.value());
                case TIME_LONG -> checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.writeTimeLong(checkpointState.live(), call.value()));
                case TIME_FRAMES -> checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.writeTimeByte(checkpointState.live(), CdCheckpointLiveAux.TimeByte.FRAMES, call.value()));
                case TIME_SECONDS -> checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.writeTimeByte(checkpointState.live(), CdCheckpointLiveAux.TimeByte.SECONDS, call.value()));
                case WATER_ROUTINE -> checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.writeWaterRoutine(checkpointState.live(), call.value()));
                case DEST_BOTTOM_BOUND, FG_X, FG_Y, BG_X, BG_Y, BG2_X, BG2_Y, BG3_X, BG3_Y ->
                        playerPresentation.storeCheckpointLoadWord(call);
                // R11A never reaches these instructions. No broader zone admission is granted.
                case WATER_HEIGHT2, WATER_FULLSCREEN -> throw new IllegalStateException("Unowned non-R11A water restore");
            }
            cursor++; // Even a same-value store has synchronously executed exactly once.
        }

        private void finishMain(CdPlayerFrameRuntime actualFrame) {
            requireConstruction();
            if (loading || actualFrame != playerFrame || actualFrame.assets() != assets || actualFrame.closed()
                    || (checkpointRestartStaged() && !loadFinished)) {
                throw new IllegalStateException("Later Main policy requires the completed current load");
            }
            // Main:285-290 tests the RESTORED byte before Main:301 clears it.
            // The raw $00050000 checkpoint time therefore remains $0005FF3B for nonzero spawn.
            if (level.spawnMode() == 0) {
                ring = ringWithCount(ring, 0);
                checkpointState = checkpointWithLive(checkpointState,
                        CdCheckpointLiveAux.clearTimeAtSpawnZero(checkpointState.live()));
                ring = ringWithLivesFlags(ring, 0);
            }
            // Main:299 and :301 are separate addressed stores. Other existing Frame
            // clear policies are constructor-collapsed; no Body/input/clock service runs.
            level = new CdLevelGlobals(0, level.updateHudTime(), level.checkpoint(), level.spawnMode(),
                    level.plcLoadFlags(), level.flowerCount(), level.fadeEnableDisplay(), level.powerup(), level.timeOver());
            level = levelWithSpawn(level, 0);
            // Existing DEV HUD/fade policy: native Main:304 writes1 and :314 writes1,
            // but the omitted HUD/title/fade services retain HUD0/fade2 here.
            level = new CdLevelGlobals(level.levelRestart(), 0, level.checkpoint(), level.spawnMode(),
                    level.plcLoadFlags(), level.flowerCount(), level.fadeEnableDisplay(), level.powerup(), 0);
            finalized = true;
        }
    }

    private static CdPlayerFrameRuntime.CheckpointLoadField frameField(CdCheckpointLoadData.StoreTarget target) {
        return switch (target) {
            case PRIMARY_X -> CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_X;
            case PRIMARY_Y -> CdPlayerFrameRuntime.CheckpointLoadField.PRIMARY_Y;
            case BOTTOM_BOUND -> CdPlayerFrameRuntime.CheckpointLoadField.CURRENT_BOTTOM;
            case LEFT_BOUND -> CdPlayerFrameRuntime.CheckpointLoadField.LEFT_BOUND;
            case MINI_SONIC -> CdPlayerFrameRuntime.CheckpointLoadField.MINI_SONIC;
            default -> throw new IllegalArgumentException("Not a Frame checkpoint load field");
        };
    }

    private static CdRingGlobals ringWithCount(CdRingGlobals before, int value) {
        return new CdRingGlobals(value, before.lives(), before.livesFlags(), before.updateHudRings(),
                before.updateHudLives(), before.timeStopTimer(), before.ringAnimationTimer(), before.ringAnimationFrame());
    }

    private static CdRingGlobals ringWithLivesFlags(CdRingGlobals before, int value) {
        return new CdRingGlobals(before.rings(), before.lives(), value, before.updateHudRings(),
                before.updateHudLives(), before.timeStopTimer(), before.ringAnimationTimer(), before.ringAnimationFrame());
    }

    private static CdLevelGlobals levelWithSpawn(CdLevelGlobals before, int value) {
        return new CdLevelGlobals(before.levelRestart(), before.updateHudTime(), before.checkpoint(), value,
                before.plcLoadFlags(), before.flowerCount(), before.fadeEnableDisplay(), before.powerup(), before.timeOver());
    }

    private static CdOpeningObjectRuntime.CheckpointOwnerState checkpointWithLive(
            CdOpeningObjectRuntime.CheckpointOwnerState before, CdCheckpointLiveAux.State live) {
        return new CdOpeningObjectRuntime.CheckpointOwnerState(live, before.saved());
    }

    /** Read-only binding to this exact Frame/Presentation/assets/visit; Runtime owns dispatch/cursor admission. */
    private CdOpeningObjectRuntime.CheckpointReads checkpointReads(CdPlayerFrameRuntime exactFrame,
                                                                   CdR11aAssets exactAssets, GameplayModeContext exactMode) {
        return new CdOpeningObjectRuntime.CheckpointReads() {
            private void requireCurrent() {
                if (playerFrame != exactFrame || exactFrame.closed() || exactFrame.assets() != exactAssets
                        || playerMode != exactMode || SessionManager.getCurrentGameplayMode() != exactMode
                        || openingObjects == null || openingObjects.closed()) {
                    throw new IllegalStateException("Checkpoint read bridge belongs to a retired source visit");
                }
                playerPresentation.requireCheckpointReadOwner(exactFrame);
            }
            @Override public int readCameraWord(CdCheckpointSaveData.Source source) {
                requireCurrent();
                return playerPresentation.currentCheckpointCameraWord(source);
            }
            @Override public int readPrimaryByte(int byteOffset) {
                requireCurrent();
                var state = exactFrame.state(); var m = state.motion(); var h = state.header();
                // Deliberately partial scalar ownership, NOT a synthesized whole primary slot.
                // N Level/_Definitions.inc:33-63. Unrepresented $2E/$2F are never guessed clear.
                return switch (byteOffset) {
                    case 0x00 -> h.slotId();
                    case 0x01 -> m.spriteFlags();
                    case 0x02 -> h.tile() >>> 8;
                    case 0x03 -> h.tile() & 0xFF;
                    case 0x08 -> m.centreX16_16() >>> 24;
                    case 0x09 -> (m.centreX16_16() >>> 16) & 0xFF;
                    case 0x0A -> (m.centreX16_16() >>> 8) & 0xFF;
                    case 0x0B -> m.centreX16_16() & 0xFF;
                    case 0x0C -> m.centreY16_16() >>> 24;
                    case 0x0D -> (m.centreY16_16() >>> 16) & 0xFF;
                    case 0x0E -> (m.centreY16_16() >>> 8) & 0xFF;
                    case 0x0F -> m.centreY16_16() & 0xFF;
                    case 0x24 -> m.routine();
                    case 0x26 -> m.angle();
                    default -> throw new CdCheckpoint.UnownedParentByte(0xFFD000 + byteOffset);
                };
            }
        };
    }

    /**
     * Private visit binding only; Runtime retains the actual PLAYER_BODY invocation authority.
     * The Runtime reference is bound once after its construction. No borrowed scroll cut is retained.
     */
    private final class TimeWarpWorldBridge implements CdOpeningObjectRuntime.TimeWarpWorldPort {
        private final CdPlayerFrameRuntime exactFrame;
        private final CdR11aAssets exactAssets;
        private final GameplayModeContext exactMode;
        private CdOpeningObjectRuntime exactRuntime;

        private TimeWarpWorldBridge(CdPlayerFrameRuntime exactFrame, CdR11aAssets exactAssets,
                                    GameplayModeContext exactMode) {
            this.exactFrame = Objects.requireNonNull(exactFrame, "actual time-warp Frame");
            this.exactAssets = Objects.requireNonNull(exactAssets, "actual time-warp source assets");
            this.exactMode = Objects.requireNonNull(exactMode, "actual time-warp mode");
        }

        private void bind(CdOpeningObjectRuntime runtime) {
            Objects.requireNonNull(runtime, "actual constructed opening Runtime");
            if (exactRuntime != null || openingObjects != runtime || runtime.closed()
                    || playerFrame != exactFrame || exactFrame.closed() || exactFrame.assets() != exactAssets
                    || playerMode != exactMode || SessionManager.getCurrentGameplayMode() != exactMode) {
                throw new IllegalStateException("Time-warp world bridge cannot bind another or retired visit");
            }
            exactRuntime = runtime;
        }

        private void requireCurrent() {
            if (exactRuntime == null || openingObjects != exactRuntime || exactRuntime.closed()
                    || playerFrame != exactFrame || exactFrame.closed() || exactFrame.assets() != exactAssets
                    || playerMode != exactMode || SessionManager.getCurrentGameplayMode() != exactMode
                    || requireRuntimeSource().assets() != exactAssets
                    || exactRuntime.phase() != CdOpeningObjectRuntime.Phase.PLAYER_BODY) {
                throw new IllegalStateException("Time-warp world bridge requires its actual PLAYER_BODY visit");
            }
        }

        @Override public CdOpeningObjectRuntime.TimeWarpWorldCut read() {
            requireCurrent();
            return playerPresentation.borrowTimeWarpWorld(exactFrame);
        }

        @Override public void validate(CdOpeningObjectRuntime.TimeWarpLockPublication publication) {
            requireCurrent();
            playerPresentation.validateTimeWarpLock(exactFrame, publication);
        }

        @Override public void apply(CdOpeningObjectRuntime.TimeWarpLockPublication publication) {
            requireCurrent();
            playerPresentation.applyTimeWarpLock(exactFrame, publication);
        }
    }

    private PlcLifecycleService composeOpeningPlc(CdOpeningObjectRuntime owner, CdPlayerFrameRuntime frameOwner) {
        return new PlcLifecycleService() {
            private void requireCurrentOwner() {
                // A retained service from a prior visit must not close or advance
                // the new world. Rejection precedes the current-owner failure path.
                if (openingObjects != owner || playerFrame != frameOwner) {
                    throw new IllegalStateException("Opening PLC service belongs to a retired visit");
                }
            }
            @Override public void serviceVBlank(PlcLifecyclePhase phase) {
                requireCurrentOwner();
                try {
                    requireTerrainPlayerReady();
                    playerPresentation.validatePlaneVBlank(phase); // Pure complete-tuple preflight before Frame consumption.
                    // Root-owned overload runs this exact callback after level art/before Finish, never on PP lag.
                    owner.serviceVBlank(phase, playerPresentation::servicePlaneAtVInt,
                            playerPresentation.currentScrollLockWord());
                    playerPresentation.publishOpeningScroll(phase);
                } catch (RuntimeException | Error failure) {
                    closePlayerCycleAfterFailure(failure);
                    throw failure;
                }
            }
            @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                requireCurrentOwner();
                return owner.hasPreparationBoundary(phase);
            }
            @Override public boolean ownsTimedLoopTailArm() {
                requireCurrentOwner();
                return owner.ownsTimedLoopTailArm();
            }
            @Override public void prepareAfterLoop(PlcLifecyclePhase phase) {
                requireCurrentOwner();
                owner.prepareAfterLoop(phase);
            }
            @Override public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() {
                requireCurrentOwner();
                return owner.captureQueueDiagnostics();
            }
        };
    }

    private PlcLifecycleService composeTerrainPlc(CdPlayerFrameRuntime owner) {
        return new PlcLifecycleService() {
            private void requireCurrentOwner() {
                if (playerFrame != owner) throw new IllegalStateException("Terrain PLC service belongs to a retired visit");
            }
            @Override public void serviceVBlank(PlcLifecyclePhase phase) {
                requireCurrentOwner();
                try {
                    requireTerrainPlayerReady();
                    playerPresentation.validatePlaneVBlank(phase);
                    owner.serviceVBlank(phase, playerPresentation::servicePlaneAtVInt);
                    playerPresentation.publishTerrainPlane(phase);
                } catch (RuntimeException | Error failure) {
                    closePlayerCycleAfterFailure(failure);
                    throw failure;
                }
            }
            @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                requireCurrentOwner(); return owner.hasPreparationBoundary(phase);
            }
            @Override public boolean ownsTimedLoopTailArm() {
                requireCurrentOwner(); return owner.ownsTimedLoopTailArm();
            }
            @Override public void prepareAfterLoop(PlcLifecyclePhase phase) {
                requireCurrentOwner(); owner.prepareAfterLoop(phase);
            }
            @Override public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() {
                requireCurrentOwner(); return owner.captureQueueDiagnostics();
            }
        };
    }

    void requireTerrainPlayerReady() {
        validateTerrainPlayerRequest();
        var mode = SessionManager.getCurrentGameplayMode();
        if (mode != playerMode || playerFrame == null || playerFrame.closed() || playerActor == null
                || playerFrame.assets() != requireRuntimeSource().assets()
                || mode.getSpriteManager().getAllSprites().size() != 1
                || mode.getSpriteManager().getSprite("sonic") != playerActor
                || mode.getCamera().getFocusedSprite() != playerActor
                || (openingObjectsAdmission && (openingObjects == null || openingObjects.closed()))) {
            throw new IllegalStateException("CD terrain-player admission is incomplete or its fixed roster changed");
        }
    }

    CdPlayerFrameRuntime requirePlayerFrame() { requireTerrainPlayerReady(); return playerFrame; }
    CdPlayableSonic requirePlayerActor() { requireTerrainPlayerReady(); return playerActor; }

    private void requireOpeningClosedCut() {
        // Runtime mid-cycle capture is terminal. Reject diagnostics/rewind here
        // before calling that destructive predicate; do not pretend to complete a tail.
        if (openingObjects.phase() != CdOpeningObjectRuntime.Phase.CLOSED_CUT) {
            throw new IllegalStateException("Opening rewind requires a completed native loop");
        }
    }

    /** Close the source siblings after an advanced failure; never fabricate rollback or a retry cut. */
    void closePlayerCycleAfterFailure(Throwable failure) {
        try { if (openingObjects != null) openingObjects.close(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        try { if (playerFrame != null) playerFrame.close(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
    }

    /** Also called by the player controller's close(): session teardown does not run the profile. */
    void closeTerrainPlayer() {
        if (!playerAdmission) return;
        var oldFrame = playerFrame;
        var oldObjects = openingObjects;
        var oldActor = playerActor;
        var oldMode = playerMode;
        constructingPlayer = false;
        try {
            try { if (oldObjects != null) oldObjects.close(); }
            finally { if (oldFrame != null) oldFrame.close(); }
        } finally {
            try {
                playerPresentation.unbind();
            } finally {
                try {
                    try {
                        if (oldMode != null && oldMode.getRewindRegistry() != null) {
                            oldMode.getRewindRegistry().deregisterPostRestoreCallback(RESTORE_CALLBACK_KEY);
                        }
                    } finally {
                        if (oldMode != null && oldActor != null && oldMode.getSpriteManager() != null
                                && oldMode.getSpriteManager().getSprite(oldActor.getCode()) == oldActor) {
                            oldMode.getSpriteManager().removeSprite(oldActor.getCode());
                        }
                    }
                } finally {
                    openingObjects = null;
                    levelProgram = null;
                    warpState = null; warpScene = null; warpAssets = null; programSounds = List.of();
                    openingPlc = null;
                    terrainPlc = null;
                    playerFrame = null;
                    playerActor = null;
                    playerMode = null;
                }
            }
        }
    }

    void rebindTerrainPresentation() { playerPresentation.bind(requireRuntimeSource().assets(), backgroundLoadBuffers()); }

    private BackgroundBuffers backgroundLoadBuffers() {
        if (stagedRestart != null) {
            if (!openingObjectsAdmission) throw new IllegalStateException("Only the source opening module stages restart buffers");
            return stagedRestart.background();
        }
        return SonicCdPlayerPresentation.developmentZeroBackgroundResidue();
    }

    CdGameDataSource requireRuntimeSource() {
        CdGameDataSource candidate = SonicCd.requireSource(GameServices.level().getWorldSession().getDataSource());
        if (source != null && !source.identity().equals(candidate.identity())) {
            throw new IllegalArgumentException("CD runtime source differs from this module's authenticated binding");
        }
        authenticateOpeningSource(candidate);
        return candidate;
    }

    @Override public Level transformDecodedLevel(Level decoded) {
        var candidate = requireRuntimeSource();
        if (!(decoded instanceof SonicCdLevel level)
                || !candidate.assets().identity().equals(level.assets().identity())) {
            throw new IllegalArgumentException("CD decoded terrain differs from the authenticated runtime stage");
        }
        SonicCdStaticPresentation.publishDecodedGraphics(level, GameServices.graphics());
        return level;
    }

    void retirePriorAudio() { audio.retirePriorAudio(GameServices.audio()); }

    @Override public TouchResponseTable createTouchResponseTable(GameDataSource dataSource) {
        SonicCd.requireSource(dataSource);
        throw SonicCd.unsupported("native touch response (no actors are installed)");
    }

    @Override public ObjectRegistry createObjectRegistry() {
        return new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) {
                throw SonicCd.unsupported("native object spawning");
            }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) {
                if (!spawns.isEmpty()) throw SonicCd.unsupported("executable object placements");
            }
            @Override public String getPrimaryName(int objectId) {
                throw SonicCd.unsupported("native object registry lookup");
            }
            @Override public ObjectSlotLayout objectSlotLayout() {
                throw SonicCd.unsupported("native object slot layout");
            }
        };
    }
    @Override public ObjectPlacementEncoding getObjectPlacementEncoding() {
        throw SonicCd.unsupported("editor object encoding; raw native placements remain in CdR11aAssets");
    }
    @Override public int getPlaneSwitcherObjectId() { return 0; }
    @Override public PlaneSwitcherConfig getPlaneSwitcherConfig() { throw SonicCd.unsupported("native plane switching"); }
    @Override public LevelEventProvider getLevelEventProvider() { return null; }
    @Override public RespawnState createRespawnState() { return new SonicCdStaticState.Respawn(); }
    @Override public LevelState createLevelState() {
        return playerAdmission ? new SonicCdDevelopmentPlayerState() : new SonicCdStaticState.Level();
    }
    @Override public ZoneRegistry getZoneRegistry() { return zones; }
    @Override public ScrollHandlerProvider getScrollHandlerProvider() { return playerAdmission ? playerPresentation : presentation; }
    @Override public ZoneFeatureProvider getZoneFeatureProvider() { return null; }
    @Override public DebugOverlayProvider getDebugOverlayProvider() { return null; }
    @Override public ObjectArtProvider getObjectArtProvider() { return null; }
    @Override public PhysicsProvider getPhysicsProvider() { return rules; }
    @Override public GameAudioProfile getAudioProfile() { return audio; }
    @Override public LevelInitProfile getLevelInitProfile() { return playerAdmission ? playerProfile : profile; }
    @Override public GameplayFrameController gameplayFrameController() { return playerAdmission ? playerPresentation : presentation; }
    @Override public PlayableCharacterRegistry getPlayableCharacterRegistry() { return characters; }
    @Override public List<RewindSnapshottable<?>> rewindAdapters() {
        return openingObjectsAdmission ? List.of(sharedOpeningAdapter)
                : playerAdmission ? List.of(sharedPlayerAdapter) : List.of();
    }
    @Override public <T> T getGameService(Class<T> type) {
        if (openingObjectsAdmission) {
            // Even a failed owner remains selected until teardown: returning null here
            // would silently fall back to normal gameplay after a terminal failure.
            if (type == NativeDynamicObjectPass.class) return openingObjects == null ? null : type.cast(openingObjects);
            return type == PlcLifecycleService.class && openingPlc != null ? type.cast(openingPlc) : null;
        }
        return playerAdmission && type == PlcLifecycleService.class && terrainPlc != null ? type.cast(terrainPlc) : null;
    }
    @Override public boolean suppressesLevelSelect() { return true; }
    @Override public void onNewGameFromTitle(GameStateManager gameState) { throw SonicCd.unsupported("native new-game entry"); }
    @Override public TitleScreenProvider getTitleScreenProvider() { throw SonicCd.unsupported("native title entry"); }
    @Override public LevelSelectProvider getLevelSelectProvider() { throw SonicCd.unsupported("native level select"); }
    @Override public SaveSnapshotProvider getSaveSnapshotProvider() { throw SonicCd.unsupported("campaign saves/BRAM"); }
}
