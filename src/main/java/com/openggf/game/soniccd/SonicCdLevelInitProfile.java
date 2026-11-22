package com.openggf.game.soniccd;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadRuntimeAccess;
import com.openggf.game.soniccd.player.CdPlayerStage;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;

import java.util.List;

/**
 * Level initialization for Sonic CD's {@code LevelStart} (Level/Main.asm:19-330), which is
 * Sonic 1's {@code Level:} sequence: load data, collision, player, then camera. No sidekick.
 * The title card is object $3C (Main.asm:271-275) and arrives with the object lanes.
 */
final class SonicCdLevelInitProfile extends AbstractLevelInitProfile
        implements com.openggf.game.internal.LevelMotionPresentation {
    private final CdLevelEventProvider events;
    private final com.openggf.game.soniccd.timetravel.CdTimeTravel timeTravel;
    private final com.openggf.game.soniccd.objects.shared.SharedSwitchFlags switchFlags;
    private final CdPlayerStage playerStage;
    private final CdLevelVIntCounter levelVIntCounter;
    private final CdLoadFrame loadFrame;
    private final CdLevelPause levelPause;
    /** The session's disc, for the stage load's identity (null before a disc is open). */
    private final java.util.function.Supplier<CdDiscDataSource> disc;
    /** {@code SPCmd_LoadLevel}'s PCM-driver load for a stage: the audio profile's sub-CPU owner. */
    private final java.util.function.Consumer<CdStagePayload> loadPcmDriver;
    private com.openggf.game.soniccd.timetravel.CdWarpCarry arrival;
    /** {@code spawnMode} 1 for this load: the checkpoint's saved position and time (LoadCheckpointData). */
    private boolean checkpointSpawn;
    /** {@code plcLoadFlags} bit 1 clear for this load: the title card is loaded (Level/Main.asm:271-277). */
    private boolean titleCard;

    SonicCdLevelInitProfile(CdLevelEventProvider events, com.openggf.game.soniccd.timetravel.CdTimeTravel timeTravel,
                            com.openggf.game.soniccd.objects.shared.SharedSwitchFlags switchFlags,
                            CdPlayerStage playerStage, CdLevelVIntCounter levelVIntCounter, CdLoadFrame loadFrame,
                            CdLevelPause levelPause,
                            java.util.function.Supplier<CdDiscDataSource> disc,
                            java.util.function.Consumer<CdStagePayload> loadPcmDriver) {
        this.disc = disc;
        this.loadPcmDriver = java.util.Objects.requireNonNull(loadPcmDriver, "loadPcmDriver");
        this.playerStage = playerStage;
        this.levelVIntCounter = levelVIntCounter;
        this.loadFrame = loadFrame;
        this.levelPause = levelPause;
        this.events = events;
        this.timeTravel = timeTravel;
        this.switchFlags = switchFlags;
    }

    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        List<InitStep> steps = new java.util.ArrayList<>();
        steps.add(new InitStep("ClearCdSwitchFlags",
                "LevelStart's camera-RAM clear, which covers switchFlags (Level/Main.asm:210-216; "
                        + "Level/_Definitions.inc:237, 336)",
                switchFlags::clear));
        steps.add(new InitStep("ResetCdRng",
                "InitStage clears the V-blank counters through rngSeed ($58 bytes from vblank_e_count: "
                        + "r4/main.asm:160-166, common/main.asm:206-212, Level/Main.asm:202-208; "
                        + "Level/_Definitions.inc:203-210), so Random starts from $2A6D365A",
                () -> {
                    var rng = com.openggf.game.GameServices.rngOrNull();
                    if (rng != null) rng.setSeed(0L);
                }));
        steps.add(new InitStep("ResetCdPlayerStage",
                "InitStage's drown timer and the cleared object and flag RAM: the bubbles slot and "
                        + "water_current_flag (r4/main.asm:212; Level/Main.asm:210-216)",
                playerStage::resetForLevel));
        for (InitStep core : buildCoreSteps(ctx)) {
            steps.add(core);
            if (core.name().equals("InitGameModule")) steps.add(loadLevelCommandStep(ctx));
        }
        if (ctx.isIncludePostLoadAssembly()) {
            steps.add(new InitStep("CdRestartSpawnMode",
                    "Level_MainLoop's pause restart (Level/Main.asm:368-395) and ObjSonic_Restart's "
                            + "death restart (Level/_Objects/Sonic/Main.asm:2459-2491)",
                    () -> restartSpawnMode(ctx)));
            steps.add(restoreCheckpointStep(ctx));
            steps.add(spawnPlayerStep(ctx));
            steps.add(resetPlayerStateStep(ctx));
            steps.add(new InitStep("InitCdPlayerSpeedWords",
                    "LoadPlayer (Level/Main.asm:438-446) installs the object whose ObjSonic_Init"
                            + " writes the stage's starting speed words (Level/_Objects/Sonic/Main.asm:152-154)",
                    () -> {
                        var player = LevelLoadRuntimeAccess.requireSprites(ctx).getMainPlayable();
                        if (player != null) {
                            PlayableSpriteInternalAccess.initializeNativeSpeedWords(
                                    player, LevelLoadRuntimeAccess.requireWorld(ctx));
                        }
                    }));
            steps.add(new InitStep("CdLoadCheckpointData",
                    "LoadCheckpointData's time and LoadPlayer's invulnerability (Load Saved Data.asm:44-56; "
                            + "Level/Main.asm:438-446)",
                    () -> loadCheckpointData(ctx)));
            steps.add(new InitStep("CdTimeTravelLevelStart",
                    "LevelStart's time values (Level/Main.asm:22-26, 209-216, 297-315); a warp arrival's "
                            + "LoadTimeWarpData and LoadPlayer (Load Saved Data.asm:7-40; Level/Main.asm:438-446)",
                    this::timeTravelLevelStart));
            steps.add(initCameraStep());
            steps.add(new InitStep("CdCameraYCentre",
                    "LevelSizeLoad's camYCenter from the stage's CamBounds, written on every stage load after "
                            + "LevelStart cleared camera RAM (Scroll (Act 1 Present).asm:32-33; Level/Main.asm:210-216, 245)",
                    SonicCdLevelInitProfile::cameraYCentre));
            steps.add(new InitStep("CdTimeWarpBounds",
                    "LoadTimeWarpData's bottom boundary (Load Saved Data.asm:18-19)",
                    this::timeWarpBounds));
            steps.add(initLevelEventsStep());
            steps.add(new InitStep("LoadCdReservedObjects",
                    "LevelStart: HUD $1C objects and the title card $3C (Level/Main.asm:262-275)",
                    this::loadReservedObjects));
            steps.add(new InitStep("LoadCdTimeIcon",
                    "LevelStart: HUD time icon $19 subtype $A in objHUDIconSlot (Level/Main.asm:268-269)",
                    SonicCdLevelInitProfile::loadTimeIcon));
            steps.add(new InitStep("LoadCdZoneLevelStartObjects",
                    "Level initialization's objects without a layout row: Tidal Tempest's water surface $04 in "
                            + "object_spawn_pool (r4/main.asm:249)",
                    SonicCdLevelInitProfile::loadZoneLevelStartObjects));
            steps.add(new InitStep("CdLevelVIntCounterAtStageStart",
                    "LevelStart's .ClearFlowers clears levelVIntCounter (Level/Main.asm:178-184; Main CPU "
                            + "Variables.inc:155); the load's own V-INTs before iteration 1 are hardware time, "
                            + "paced by the load-time profile (CdLevelVIntCounter, CdLoadTiming)",
                    () -> levelVIntCounter.establishAtStageStart(
                            com.openggf.game.GameServices.level().getObjectManager(),
                            com.openggf.game.GameServices.hardwareTimingOrNull(), this::stageLoad)));
            steps.add(new InitStep("RunCdFirstMainLoopIteration",
                    "LevelStart's LevelScroll and UpdateAnimTiles, then Level_MainLoop iteration 1: VInt_Level, "
                            + "SpawnObjects, RunObjects, LevelScroll, PaletteCycle (Level/Main.asm:246, 312, "
                            + "333-335, 400-425, 837)",
                    () -> {
                        CdFirstMainLoopIteration.run();
                        loadFrame.markFirstIterationRun();
                    }));
        }
        return List.copyOf(steps);
    }

    /**
     * The stage's PCM driver, before the level music request. Natively the stage program never loads it: the
     * initial program's {@code RunLevel} runs the stage with {@code RunMMD}, whose command ID is the stage's load
     * command (CD Initial Program/IPX.asm:542-547, 1307-1309), and the sub CPU's {@code SPCmd_LoadLevel} loads the
     * stage file and then, if another is loaded, the stage's PCM driver ({@code .PCMDrivers}, CD System
     * Program/SPX.asm:744-772). Only then does {@code LevelStart} run: one {@code .WaitPLC} V-sync, then the call
     * (Level/Main.asm:147-154) to {@code PlayLevelMusic} (:555-573), whose Past entry {@code SCMD_PASTMUS} writes
     * {@code PCMM_PAST} to the loaded driver's queue ({@code SPCmd_PlayPastMus}, SPX.asm:1612-1614). A death or
     * checkpoint restart re-enters the stage's start in the same program ({@code .DoReset}, Level/Main.asm:388-395;
     * {@code .NotPaused}, :397-408) with the driver still loaded; a time warp re-runs {@code RunMMD} with the same zone's load command, whose driver is
     * already loaded (IPX.asm:546-571; SPX.asm:762-764). Every entry therefore finds the driver loaded when
     * {@code PlayLevelMusic} queues its song, which this step gives the shared {@code InitAudio} after it.
     */
    private InitStep loadLevelCommandStep(LevelLoadContext ctx) {
        return new InitStep("CdLoadLevelPcmDriver",
                "RunMMD's SPCmd_LoadLevel: the stage's PCM driver before LevelStart's PlayLevelMusic "
                        + "(CD Initial Program/IPX.asm:1307-1309; CD System Program/SPX.asm:744-772; Level/Main.asm:154)",
                () -> {
                    int levelIndex = ctx.getLevelIndex();
                    var registry = com.openggf.game.GameServices.level().getGameModule().getZoneRegistry();
                    if (registry.modZoneRuntimeContribution(levelIndex) == null) {
                        loadPcmDriver.accept(CdStagePayload.values()[levelIndex]);
                    }
                });
    }

    /**
     * {@code ObjSonic_Restart} with lives left (scddisasm/src/Level/_Objects/Sonic/Main.asm:2459-2491): a checkpoint in the present
     * restarts there ({@code move.b #1,spawnMode}); in the past or future, or with no checkpoint, the stage
     * restarts from its start ({@code clr.b spawnMode}). The title card is reloaded only with no
     * checkpoint in the present ({@code bclr #1,plcLoadFlags}). Any other load spawns from the start with
     * its title card unless it is a warp arrival. The {@code checkpoint} number itself survives natively a
     * past or future restart; the engine's checkpoint state does not, so posts already passed re-arm.
     * A pause restart always uses spawn mode 0 and keeps the title card's loaded bit set
     * {@code Level_MainLoop} (scddisasm/src/Level/Main.asm:368-395).
     */
    private void restartSpawnMode(LevelLoadContext ctx) {
        boolean pauseRequested = levelPause.consumeRestartRequest();
        boolean deathRestart = ctx.getLoadCause() == com.openggf.game.LevelLoadCause.FULL_DEATH_RELOAD;
        if (pauseRequested && deathRestart) {
            // Only the R5 act 1 and 2 programs reach this branch with a saved checkpoint.
            // Clear the context's copy after restoring the bank so player and camera assembly
            // use spawn mode 0 per loc_201858 (scddisasm/src/r5/main.asm:328-337).
            com.openggf.game.GameServices.level().restoreCheckpointState(ctx);
            ctx.snapshotCheckpoint(null);
            checkpointSpawn = false;
            titleCard = false;
            return;
        }
        boolean present = timeTravel.timeZone() == com.openggf.game.soniccd.timetravel.CdTimeZone.PRESENT;
        boolean checkpoint = ctx.hasCheckpoint();
        titleCard = !deathRestart || (!checkpoint && present);
        if (com.openggf.game.GameServices.level().getCurrentLevel()
                instanceof com.openggf.game.soniccd.level.CdLevel cd && cd.demo() != null) {
            titleCard = cd.demo().showsTitleCard() && titleCard;
            if (checkpoint) ctx.snapshotCheckpoint(null);
        }
        if (deathRestart && checkpoint && !present) ctx.snapshotCheckpoint(null);
        checkpointSpawn = deathRestart && ctx.hasCheckpoint();
    }

    /**
     * Submit the stage program file and the PLC its loader queues for this entry.
     * {@code spawnMode} is set for a checkpoint restart (1) and a time-warp arrival (2).
     * {@code LoadSectionArt} reads the camera {@code LevelScroll} just placed (Level/Main.asm:245-248).
     */
    private com.openggf.game.timing.HardwareWorkSubmission stageLoad() {
        var level = com.openggf.game.GameServices.level();
        CdDiscDataSource source = disc == null ? null : disc.get();
        if (source == null || !(level.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel cd)) {
            return null;
        }
        CdStagePayload payload = cd.payload();
        CdStageDemo demo = cd.demo();
        CdNativeFile fileName = demo == null ? payload.file() : demo.file();
        try {
            boolean spawnModeSet = checkpointSpawn || arrival != null;
            boolean titleCardAlreadyLoaded = arrival != null || !titleCard;
            int plc = 0;
            if (spawnModeSet || titleCardAlreadyLoaded) {
                byte[] file = source.readFile(fileName);
                var entry = demo == null ? com.openggf.game.soniccd.level.CdStageCatalogue.entry(payload)
                        : com.openggf.game.soniccd.level.CdStageCatalogue.entry(demo);
                plc = CdLoadTiming.levelStartPlc(file, entry, com.openggf.game.GameServices.camera().getX(),
                        spawnModeSet, titleCardAlreadyLoaded);
            }
            return CdLoadTiming.stageLoad(source.assets().entry(fileName), plc);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("stage load identity for " + payload, e);
        }
    }

    /**
     * {@code LoadCheckpointData}: {@code move.l savedTime,time; move.b #59,timeFrames; subq.b #1,timeSeconds}
     * (Load Saved Data.asm:53-56), and {@code LoadPlayer}'s {@code move.w #$78,oPlayerHurt(a1)} for a
     * spawn that is not at the start (Level/Main.asm:438-446). A stored second of 0 wraps to 255 natively;
     * elapsed frames here clamp at 0.
     */
    private void loadCheckpointData(LevelLoadContext ctx) {
        if (!checkpointSpawn) return;
        var level = com.openggf.game.GameServices.level();
        var state = level.getLevelGamestate();
        if (state != null && ctx.hasCheckpointTimer()) {
            long saved = ctx.getCheckpointTimerFrames();
            state.setTimerFrames(Math.max(0, (saved / 3600) * 3600 + (saved / 60 % 60) * 60 - 1));
        }
        var player = com.openggf.game.GameServices.sprites().getMainPlayable();
        if (player != null) player.setInvulnerableFrames(0x78);
        if (player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
            var stage = com.openggf.game.soniccd.player.CdPlayerStage.of(sprite);
            if (stage != null) stage.restoreCheckpointMiniPlayer(sprite);   // move.b respawn_shrunk,shrunk_player
        }
    }

    /** The time owner's LevelStart for the loaded stage, then the warp carry if this is the arrival. */
    private void timeTravelLevelStart() {
        arrival = null;
        var level = com.openggf.game.GameServices.level();
        if (!(level.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel cd)) return;
        arrival = timeTravel.levelStart(cd.payload());
        if (arrival == null) return;
        var player = com.openggf.game.GameServices.sprites().getMainPlayable();
        if (player != null) {
            com.openggf.game.soniccd.timetravel.CdTimeWarp.arrive(player, arrival, level.getLevelGamestate());
        }
    }

    /**
     * The HUD, and the title card unless this is a warp arrival: {@code bset #1,plcLoadFlags;
     * bne .SkipTitleCard} keeps the card (and its control lock) to the act's first load
     * (Level/Main.asm:271-277).
     */
    private void loadReservedObjects() {
        var level = com.openggf.game.GameServices.level();
        if (!(level.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel cd)) return;
        var objects = level.getObjectManager();
        if (arrival == null && titleCard) {
            com.openggf.game.soniccd.objects.common.CdFixedObjects.install(objects, level.getLevelGamestate());
            // InitStageWaitForGfx (r4/main.asm:242-252) locks the cleared pad before the first object pass.
            var player = com.openggf.game.GameServices.sprites().getMainPlayable();
            if (player != null) playerStage.applyControlLock(player, cd);
            return;
        }
        for (var kind : com.openggf.game.soniccd.objects.common.CdHud.Kind.values()) {
            com.openggf.level.objects.ObjectLifetimeOps.addDynamicAtReservedSlot(objects,
                    new com.openggf.game.soniccd.objects.common.CdHud(kind), kind.slot());
        }
    }

    private static void loadTimeIcon() {
        var level = com.openggf.game.GameServices.level();
        if (!(level.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel)) return;
        com.openggf.level.objects.ObjectLifetimeOps.addDynamicAtReservedSlot(level.getObjectManager(),
                new com.openggf.game.soniccd.objects.common.CdTimeIcon(),
                com.openggf.game.soniccd.objects.common.CdTimeIcon.SLOT);
    }

    /** The stage zone's fragment places the objects its level initialization creates without a layout row. */
    private static void loadZoneLevelStartObjects() {
        var level = com.openggf.game.GameServices.level();
        if (!(level.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel cd)) return;
        com.openggf.game.soniccd.zones.CdZones.fragment(cd.payload().stage().zone())
                .installLevelStartObjects(cd.payload(), level.getObjectManager());
    }

    /** The camera's Y centre is camera RAM: a stage loaded after another starts from its own table value. */
    private static void cameraYCentre() {
        var level = com.openggf.game.GameServices.level();
        if (level.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel cd) {
            com.openggf.game.GameServices.camera().setYPosBias((short) cd.cameraYCentre());
        }
    }

    private void timeWarpBounds() {
        if (arrival != null) {
            com.openggf.game.soniccd.timetravel.CdTimeWarp.arriveBounds(arrival, com.openggf.game.GameServices.camera());
        }
    }

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetCdLevelEvents", "Undoes Sonic CD level events", events::resetState);
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetCdLevelEvents", "Undoes Sonic CD level events", events::resetState);
    }
}
