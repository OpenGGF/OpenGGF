package com.openggf.trace.replay;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.OscillationManager;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.Sonic3kLevelAnimationManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance;
import com.openggf.game.sonic2.objects.TornadoObjectInstance;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic2.trace.Sonic2TornadoRidePrelude;
import com.openggf.level.LevelData;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.FrameCollisionPlan;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Headless and live trace replay share the same pre-gameplay setup.
 * This helper owns that sequence so {@code AbstractTraceReplayTest}
 * and {@code TraceSessionLauncher} stay consistent. Steps, in order:
 * <ol>
 *   <li>{@link #prepareConfiguration}: set recorded team + S3K intro
 *       skip flag on the configuration service. Must run before the
 *       caller loads the level.</li>
 *   <li>{@link #applyBootstrap}: derive any allowed timing prelude from
 *       trace-visible execution timing, advance native timing-only state
 *       where policy allows, apply trace-start global state that ROM had
 *       already established before frame 0, seed trace-start global timing
 *       counters, and choose the replay comparison cursor. It must not copy
 *       recorded object, player, sidekick, or camera state into the engine.</li>
 * </ol>
 */
public final class TraceReplaySessionBootstrap {

    private static final Logger LOGGER =
            Logger.getLogger(TraceReplaySessionBootstrap.class.getName());

    private TraceReplaySessionBootstrap() {
    }

    /**
     * Clears the per-zone subsystem state the headless fixture zaps
     * via {@code TestEnvironment.resetPerTest()}: sprites, collision,
     * camera, fade, game state, timers, water, parallax, cross-game
     * features, debug overlay, and the game's {@code perTestLeadStep}
     * (e.g. S1 event/switch/conveyor reset).
     *
     * <p>Call this BEFORE {@code LevelManager.loadZoneAndAct} when
     * starting a live trace replay. Without it, state left behind by
     * {@code Engine.initializeGame()} (title screen, default level,
     * residual object state) leaks into the replay - one symptom is
     * subpixel drift from frame 0 that first becomes pixel-visible at
     * the first ROM-accurate collision or enemy destruction.
     */
    public static void resetLevelSubsystemsForReplay() {
        LevelInitProfile profile = GameServices.module().getLevelInitProfile();
        for (InitStep step : profile.perTestResetSteps()) {
            try {
                step.execute();
            } catch (RuntimeException e) {
                LOGGER.warning("Trace-replay reset step '" + step.name()
                        + "' threw " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }
        // Reset the GameRng seed so the replay starts from the same
        // pristine state the headless test fixture does. Between
        // Engine.initializeGame() and the trace callback, the master
        // title screen and any configured startup mode may advance
        // the PRNG; a single divergent Random() call later rewrites
        // badnik behaviour (e.g. animal selection on kill, Batbrain
        // eyelid flicker) and causes subpixel drift that surfaces at
        // the first enemy destruction.
        if (GameServices.hasRuntime()) {
            GameRng rng = GameServices.rngOrNull();
            if (rng != null) {
                rng.setSeed(0L);
            }
        }
    }

    /**
     * Prepare configuration state that must be set before the level is
     * loaded. Call before the caller loads the level.
     *
     * <p>Isolates trace playback from any gameplay-altering settings
     * the user may have configured for their own game (team,
     * cross-game donation, skip-intros). Live callers should snapshot
     * the affected keys via {@link #snapshotGameplayConfig()} before
     * calling this, and restore them via
     * {@link #restoreGameplayConfig(ConfigSnapshot)} when the trace
     * session tears down.
     */
    public static void prepareConfiguration(TraceData trace, TraceMetadata meta) {
        SonicConfigurationService config = GameServices.configuration();

        // Team: the recorded trace dictates the team. If metadata
        // didn't record one (legacy), force Sonic-solo - the trace
        // can't expect anything else.
        String main = meta.recordedMainCharacter();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                main == null || main.isBlank() ? "sonic" : main);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                String.join(",", meta.recordedSidekicks()));

        // Cross-game donation wasn't recorded; always force it off so
        // trace physics/visuals match the base ROM.
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);

        if (TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay(trace)
                && "s3k".equals(meta.game())) {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        }
    }

    /**
     * Captured view of the gameplay-altering configuration keys that
     * {@link #prepareConfiguration} rewrites. Pass back to
     * {@link #restoreGameplayConfig} when tearing down a trace
     * session so the user's own config is preserved across launches.
     */
    public record ConfigSnapshot(
            Object mainCharacterCode,
            Object sidekickCharacterCode,
            Object crossGameFeaturesEnabled,
            Object s3kSkipIntros) {
    }

    public static ConfigSnapshot snapshotGameplayConfig() {
        SonicConfigurationService config = GameServices.configuration();
        return new ConfigSnapshot(
                config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE),
                config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE),
                config.getConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED),
                config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS));
    }

    public static void restoreGameplayConfig(ConfigSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        SonicConfigurationService config = GameServices.configuration();
        restore(config, SonicConfiguration.MAIN_CHARACTER_CODE, snapshot.mainCharacterCode());
        restore(config, SonicConfiguration.SIDEKICK_CHARACTER_CODE, snapshot.sidekickCharacterCode());
        restore(config, SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, snapshot.crossGameFeaturesEnabled());
        restore(config, SonicConfiguration.S3K_SKIP_INTROS, snapshot.s3kSkipIntros());
    }

    private static void restore(SonicConfigurationService config,
                                SonicConfiguration key,
                                Object value) {
        if (value == null) {
            return;
        }
        config.setConfigValue(key, value);
    }

    /**
     * Apply pre-gameplay replay policy to an already-loaded level. Must
     * be called after the level has been loaded and a player sprite
     * exists on the runtime.
     *
     * <p>Performs, in order:
     * <ol>
     *   <li>Oscillation pre-advance derived from trace-visible gameplay
     *       timing, or from an explicit diagnostic override.</li>
     *   <li>Sidekick-only prelude ticks for title-card timing, when the
     *       trace policy can derive them from normal execution order.</li>
     *   <li>{@link TraceReplayBootstrap#reportPreTraceObjectSnapshots} -
     *       a comparison-only compatibility hook that reports zero
     *       applied snapshots.</li>
     *   <li>{@link TraceReplayBootstrap#applyReplayStartStateForTraceReplay}
     *       - deterministic warmup/cursor selection without trace-state
     *       hydration.</li>
     * </ol>
     *
     * <p>The metadata start-position reapply + initial ground snap that
     * mirrors {@code HeadlessTestFixture.Builder.build} steps 6 and 11
     * is exposed separately as
     * {@link #applyStartPositionAndGroundSnap} so callers can invoke it
     * BEFORE this method (matching the test fixture order, which sets
     * the start position and snaps to ground before replay bootstrap
     * policy runs).
     *
     * @param preTraceOscOverride number of pre-trace oscillation frames
     *                            to pre-advance; pass a negative value
     *                            to derive timing through trace replay
     *                            policy.
     */
    public static BootstrapResult applyBootstrap(TraceData trace,
                                                 TraceReplayFixture fixture,
                                                 int preTraceOscOverride) {
        return applyBootstrap(trace, fixture, preTraceOscOverride, false);
    }

    public static BootstrapResult applyBootstrap(
            TraceData trace,
            TraceReplayFixture fixture,
            int preTraceOscOverride,
            boolean forceHardwareTimingReplay) {
        installHardwareTimingReplay(trace, fixture, forceHardwareTimingReplay);
        int preTraceOsc = TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(
                trace, preTraceOscOverride);
        for (int i = 0; i < preTraceOsc; i++) {
            OscillationManager.update(-(preTraceOsc - i));
        }
        OscillationManager.suppressNextFrames(
                TraceReplayBootstrap.initialOscillationSuppressionFramesForTraceReplay(trace));
        advanceAnimatedTilePreludeForTraceReplay(trace);
        // S3K level-gated starts deliberately return zero for both replay-only
        // prelude counts. Their first normal frame runs the ordinary production
        // playable dispatch, so Sonic, Tails, objects, input history, and
        // OscillateNumDo stay in one native phase.
        // S1/S2 retain their game-owned title-card setup rules below.
        int sidekickPreludeFrames =
                TraceReplayBootstrap.sidekickTitleCardPreludeFramesForTraceReplay(trace);
        int objectPreludeFrames = 0;
        int zoneFeaturePreludeFrames =
                TraceReplayBootstrap.zoneFeatureTitleCardPreludeFramesForTraceReplay(trace);
        var gameplayMode = fixture.gameplayMode();
        // Complete-run segments restore state that already represents the
        // production setup pass. Their reset/restore/dispatch envelope is not
        // a replay prelude knob and must not consume fresh-load authority.
        boolean representedS3kCompleteRun =
                TraceReplayBootstrap.isS3kCompleteRunSegment(trace);
        if (representedS3kCompleteRun
                && gameplayMode != null
                && gameplayMode.getLevelManager() != null
                && !segmentBeginsAtLevelSetupPass(gameplayMode)) {
            gameplayMode.getLevelManager()
                    .discardPendingInitialProcessSpritesForStateRestoration();
        }
        if (gameplayMode != null
                && gameplayMode.getLevelManager() != null
                && gameplayMode.getLevelManager().getObjectManager() != null) {
            ObjectManager objectManager = gameplayMode.getLevelManager().getObjectManager();
            gameplayMode.getLevelManager().initRingFloorCheckCounterPhase(
                    ringFloorCheckRuntimeOffset(
                            trace.metadata().ringFloorCheckCounterPhase(), liveRingFloorCheckPhase()));
            objectManager.initVIntRunCounterPhaseOffset(
                    trace.initialVIntRunCounterPhaseOffset());
            objectPreludeFrames = s2TornadoObjectPreludeFrames(trace, objectManager);
            if (objectPreludeFrames == 0) {
                objectPreludeFrames = TraceReplayBootstrap
                        .levelObjectTitleCardPreludeFramesForTraceReplay(trace);
            }
            if (objectPreludeFrames == 0) {
                // Non-Tornado S2 native-prelude traces need the generic
                // title-card object prelude: ROM ticks Level_MainLoop during
                // the title card before Level_started_flag is set
                // (s2.asm:5004-5092). Without this the engine starts every
                // S2 trace with objects out of step with the BK2 frame 0
                // state and divergences accumulate.
                objectPreludeFrames = TraceReplayBootstrap
                        .s2GenericObjectTitleCardPreludeFramesForTraceReplay(trace);
            }
            int objectDispatchFrames = representedS3kCompleteRun ? 1 : objectPreludeFrames;
            int zoneFeatureVblankOffset =
                    TraceReplayBootstrap.zoneFeatureTitleCardPreludeStartVblankOffsetForTraceReplay(trace);
            if (zoneFeaturePreludeFrames > 0
                    && zoneFeatureVblankOffset > 0
                    && gameplayMode.getLevelManager().getZoneFeatureProvider() != null) {
                var levelManager = gameplayMode.getLevelManager();
                var camera = GameServices.cameraOrNull();
                int cameraX = camera != null ? camera.getX() : 0;
                objectManager.initVblaCounter(trace.initialVblankCounter() - zoneFeatureVblankOffset);
                for (int i = 0; i < zoneFeaturePreludeFrames; i++) {
                    objectManager.advanceVblaCounter();
                    levelManager.getZoneFeatureProvider().updatePrePhysics(
                            null, cameraX, levelManager.getFeatureZoneId());
                }
            }
            objectManager.initVblaCounter(
                    trace.initialVblankCounter() - objectDispatchFrames - 1);
        }
        int objectDispatchFrames = representedS3kCompleteRun ? 1 : objectPreludeFrames;
        if (objectDispatchFrames > 0
                && gameplayMode != null
                && gameplayMode.getLevelManager() != null
                && gameplayMode.getLevelManager().getObjectManager() != null) {
            var levelManager = gameplayMode.getLevelManager();
            var objectManager = levelManager.getObjectManager();
            applyS2TornadoTitleCardScrollPrelude(trace, objectManager);
            var camera = GameServices.cameraOrNull();
            int cameraX = camera != null ? camera.getX() : 0;
            // Replay fixtures may reuse an already-loaded SharedLevel after
            // resetPerTest clears the transient managers. Rebuild the native
            // ObjPosLoad window from the current camera before title-card
            // object ticks, so prelude state comes from object code rather
            // than recorded SST data.
            objectManager.reset(cameraX);
            if (representedS3kCompleteRun) {
                var levelEventProvider = GameServices.module().getLevelEventProvider();
                if (levelEventProvider instanceof Sonic3kLevelEventManager s3kLem) {
                    s3kLem.restoreCompleteRunSegmentObjectsAfterPreludeReset();
                }
            }
            AbstractPlayableSprite player = fixture != null ? fixture.sprite() : null;
            List<AbstractPlayableSprite> sidekicks = gameplayMode.getSpriteManager() != null
                    ? gameplayMode.getSpriteManager().getSidekicks()
                    : List.of();
            int mainPlayablePreludeFrames = Math.min(
                    objectDispatchFrames,
                    GameServices.module().getLevelInitProfile().freshMainPlayablePreludeFrames());
            if (mainPlayablePreludeFrames > 0 && gameplayMode.getSpriteManager() != null) {
                // S1 GM_Level creates the fresh Sonic slot, then executes it
                // once in the same native pass as these level objects before
                // Level_MainLoop begins. The fixture's generic ground snap has
                // already run, so the helper restores fresh object-RAM status
                // and lets player physics/animation derive the live state.
                gameplayMode.getSpriteManager().warmUpFreshMainPlayableOnly(
                        mainPlayablePreludeFrames, levelManager, player);
            }
            boolean interleaveSidekickPrelude =
                    shouldInterleaveS2TitleCardPrelude(trace, sidekickPreludeFrames, objectPreludeFrames)
                            && gameplayMode.getSpriteManager() != null;
            boolean tornadoPreludeOrder =
                    interleaveSidekickPrelude
                            && findRideStartTornado(objectManager) != null;
            if (interleaveSidekickPrelude) {
                prepareSidekickPreludePlacement(trace, gameplayMode, tornadoPreludeOrder);
            }
            int consumedPreludeFrames = 0;
            if (tornadoPreludeOrder) {
                TornadoObjectInstance tornado = findRideStartTornado(objectManager);
                consumedPreludeFrames = recordS2TornadoRideStartLeadIn(tornado, player);
                primeS2TornadoRideStart(trace, objectManager, player);
            }
            int sczTornadoPreludeStartY = tornadoPreludeOrder && player != null
                    ? player.getCentreY() - 4
                    : 0;
            for (int i = consumedPreludeFrames; i < objectDispatchFrames; i++) {
                if (interleaveSidekickPrelude && !tornadoPreludeOrder) {
                    gameplayMode.getSpriteManager().warmUpCpuSidekicksOnly(1, levelManager, player);
                }
                objectManager.update(cameraX, player, sidekicks,
                        -(objectDispatchFrames - i), false);
                if (tornadoPreludeOrder) {
                    applyS2TornadoRecordSamplePosition(
                            findRideStartTornado(objectManager), player, i, sczTornadoPreludeStartY);
                    gameplayMode.getSpriteManager().warmUpCpuSidekicksOnly(1, levelManager, player);
                }
            }
            if (tornadoPreludeOrder && player != null) {
                TornadoObjectInstance tornado = findRideStartTornado(objectManager);
                if (tornado != null && tornado.isSczRideStartPreludeObject()) {
                    player.shiftX(1);
                    player.setCentreY((short) (sczTornadoPreludeStartY + 17));
                    Sonic2TornadoRidePrelude.Seed seed = Sonic2TornadoRidePrelude.forTornado(tornado);
                    player.setSubpixelRaw(player.getXSubpixelRaw(), seed.playerYSubpixel());
                    tornado.primeRideStart(
                            player.getCentreX(), player.getCentreY(), seed.tornadoYSubpixel8());
                } else if (tornado != null && tornado.isWfzStartRideStartPreludeObject()) {
                    objectManager.update(cameraX, player, sidekicks, 0, false);
                    tornado.compensateForCollapsedWfzInit();
                }
            }
            if (interleaveSidekickPrelude) {
                sidekickPreludeFrames = 0;
            }
        }
        refreshSidekickCpuBoundsFromCamera();
        if (sidekickPreludeFrames > 0
                && gameplayMode != null
                && gameplayMode.getSpriteManager() != null
                && gameplayMode.getLevelManager() != null) {
            // Establish ROM Obj01_Init's Pos_table pre-fill on the leader and
            // place each sidekick at the Tails-spawn offset BEFORE the prelude
            // begins ticking. Otherwise the first prelude leader-record write
            // for slot 0 is overwritten when SidekickCpuController.updateInit
            // re-runs the pre-fill from its own first tick.
            prepareSidekickPreludePlacement(trace, gameplayMode, false);
            gameplayMode.getSpriteManager().warmUpCpuSidekicksOnly(
                    sidekickPreludeFrames,
                    gameplayMode.getLevelManager(),
                    fixture != null ? fixture.sprite() : null);
        }
        primeLeaderJumpEdgeFromBk2Prelude(fixture);
        if (gameplayMode != null && gameplayMode.getLevelManager() != null
                && !segmentBeginsAtLevelSetupPass(gameplayMode)) {
            gameplayMode.getLevelManager().consumePendingInitialProcessSpritesPass();
        }
        applyInitialRngSeedForReplay(trace.metadata());
        TraceReplayBootstrap.SnapshotReport snapshotReport =
                TraceReplayBootstrap.reportPreTraceObjectSnapshots(trace);
        TraceReplayBootstrap.ReplayStartState replayStart =
                TraceReplayBootstrap.applyReplayStartStateForTraceReplay(trace, fixture);
        return new BootstrapResult(snapshotReport, replayStart);
    }


    /**
     * Whether this complete-run segment's first recorded row is the level's own
     * {@code Load_Sprites}/{@code Process_Sprites} setup pass.
     *
     * <p>Recognised from ROM state: {@code loc_13A32}/{@code loc_13A8E} only run
     * on the level's first {@code Tails_CPU_Control} dispatch
     * (sonic3k.asm:26400-26436), so a segment opening a zone whose carry-intro
     * tick is still due begins on that pass. Keeping the authority pending lets
     * the first driven frame execute the walk, which is what gives
     * {@code Obj_Sonic} its routine-0 {@code Sonic_Init} frame — {@code routine
     * += 2} then {@code rts}, no movement and no gravity
     * (sonic3k.asm:21852-21943).
     */
    private static boolean segmentBeginsAtLevelSetupPass(
            com.openggf.game.session.GameplayModeContext gameplayMode) {
        var levelManager = gameplayMode.getLevelManager();
        var module = GameServices.module();
        if (levelManager == null || module == null) {
            return false;
        }
        var carryTrigger = module.getSidekickCarryTrigger();
        var camera = GameServices.camera();
        var leader = camera != null ? camera.getFocusedSprite() : null;
        if (carryTrigger == null || leader == null) {
            return false;
        }
        return carryTrigger.shouldEnterCarry(
                        levelManager.getCurrentZone(),
                        levelManager.getCurrentAct(),
                        com.openggf.game.session.ActiveGameplayTeamResolver
                                .resolvePlayerCharacter(GameServices.configuration()))
                && carryTrigger.isLeaderAtIntroPosition(leader);
    }

    public static void installHardwareTimingReplay(
            TraceData trace,
            TraceReplayFixture fixture,
            boolean forceHardwareTimingReplay) {
        if (trace == null
                || fixture == null
                || (!forceHardwareTimingReplay
                        && !trace.metadata().hasHardwareTimingStream())) {
            return;
        }
        installHardwareTimingReplay(trace.hardwareTimingSchedule(), fixture);
    }

    /** Installs one already-validated replay schedule into the active gameplay session. */
    public static void installHardwareTimingReplay(
            HardwareTimingSchedule schedule,
            TraceReplayFixture fixture) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(fixture, "fixture");
        GameplayModeContext gameplayMode = Objects.requireNonNull(
                fixture.gameplayMode(), "trace replay gameplay mode");
        HardwareTimingReplayPort replayPort = new HardwareTimingReplayPort(
                gameplayMode.recordedCompletionAuthority());
        replayPort.install(schedule);
        fixture.installHardwareTimingReplay(replayPort);
    }

    /**
     * Mirrors native post-row effects for an S3K complete-run handoff row that
     * replay skips for gameplay comparison. ROM still ran LevelLoop on that
     * row: {@code Level_frame_counter} increments before {@code Process_Sprites}
     * and {@code Animate_Tiles} runs after it (sonic3k.asm:7889-7906). This
     * advances only live timing systems; it never copies frame data from the
     * trace into engine state.
     */
    public static boolean applyS3kCompleteRunHandoffNativePostRowEffects(TraceData trace) {
        if (!TraceReplayBootstrap.isS3kCompleteRunHandoffCounterTickRow(trace)) {
            return false;
        }
        var sprites = GameServices.spritesOrNull();
        if (sprites != null) {
            sprites.setFrameCounter(sprites.getFrameCounter() + 1);
        }
        var level = GameServices.levelOrNull();
        int animatedTileFrames =
                TraceReplayBootstrap.s3kCompleteRunHandoffAnimatedTilePreludeFramesForTraceReplay(trace);
        if (level != null && level.getAnimatedPatternManager() != null) {
            for (int i = 0; i < animatedTileFrames; i++) {
                level.getAnimatedPatternManager().update();
            }
        }
        return true;
    }

    /**
     * Applies the ROM RNG seed captured at the first compared trace frame.
     *
     * <p>This is a frame-0 bootstrap value, equivalent to loading a save-state
     * at the BK2 trace start. It is deliberately separate from per-frame trace
     * rows and aux events; replay still advances RNG natively after this point.
     * Legacy traces that did not record {@code metadata.rng_seed} keep the seed
     * established by the normal fixture/live reset path.
     */
    public static void applyInitialRngSeedForReplay(TraceMetadata meta) {
        if (meta == null || meta.initialRngSeed() == null || !GameServices.hasRuntime()) {
            return;
        }
        GameRng rng = GameServices.rngOrNull();
        if (rng != null) {
            rng.setSeed(meta.initialRngSeed());
        }
        // NOTE: GumballMachineObjectInstance previously performed its own
        // frame-0 reseed here (sonic3k.asm:127412) using the engine's local
        // vblaCounter approximation of hardware V_int_run_count, which
        // clobbered this bootstrap-applied trace seed. That reseed has been
        // removed -- see docs/S3K_KNOWN_DISCREPANCIES.md, "Resolution (no
        // longer a divergence)" -- so GumballMachineObjectInstance no longer
        // calls services().rng().setSeed(...); RNG state after this point
        // comes solely from native per-frame advancement.
    }

    /** Maps the recorder's bonus_stage_type token to the engine enum. */
    static BonusStageType bonusStageTypeForToken(String token) {
        if ("gumball".equals(token)) {
            return BonusStageType.GUMBALL;
        }
        if ("pachinko".equals(token)) {
            return BonusStageType.GLOWING_SPHERE;
        }
        if ("slots".equals(token)) {
            return BonusStageType.SLOT_MACHINE;
        }
        throw new IllegalStateException(
                "Unsupported bonus_stage_type for headless replay: " + token);
    }

    /**
     * Post-load bonus-stage entry for an s3k_bonus_stage trace segment
     * (spec 2026-07-18, engine-side addition #7). Mirrors the live
     * doEnterBonusStage/prepareBonusStageForTitleCard sequence minus title
     * card and music: registers the module's bonus provider on the gameplay
     * mode, fires onEnter with a synthetic BonusStageState (frame-0 ring
     * count from the trace; interior replay never exits, so return fields
     * are zero), applies the bonus HUD layout and ring count, un-hides the
     * player, injects the pachinko bootstrap object when the type needs
     * one, and fires deferred-setup completion to initialize type-specific
     * runtime state (a no-op for gumball/pachinko; builds the slot runtime
     * for SLOT_MACHINE). Mirrors the live sequence THROUGH deferred setup
     * (spec engine addition #4). Returns false untouched for any other trace
     * profile.
     */
    public static boolean applyBonusStageEntry(TraceData trace) {
        TraceMetadata meta = trace.metadata();
        if (!"s3k_bonus_stage".equals(meta.traceProfile())) {
            return false;
        }
        BonusStageType type = bonusStageTypeForToken(meta.bonusStageType());
        int frame0Rings = trace.getFrame(0).rings();

        BonusStageProvider provider = GameServices.module().getBonusStageProvider();
        // GameServices has NO public gameplayMode() accessor — resolve the
        // context the way the live path does (GameLoop.doEnterBonusStage).
        // The replay fixture guarantees an open gameplay session, so a null
        // here is a real fixture bug and an NPE is the correct failure.
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        // Mirror the live ordering exactly (GameLoop.java:2178/2181/2184):
        // setActiveBonusStageProvider -> onEnter -> registerBonusStageAdapter.
        gameplayMode.setActiveBonusStageProvider(provider);
        provider.onEnter(type, new BonusStageState(
                0, 0, frame0Rings, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                (byte) 0x0C, (byte) 0x0D, 0, 0L));
        gameplayMode.registerBonusStageAdapter(provider);

        // Rings live on LevelState, not GameStateManager — same call the live
        // path makes (GameLoop.prepareBonusStageForTitleCard, :2274).
        GameServices.level().getLevelGamestate().setRings(frame0Rings);
        GameServices.level().setBonusStageHudLayout(true);
        for (var sprite : GameServices.sprites().getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable) {
                playable.setHidden(false);
                playable.setObjectControlled(false);
                // Undo the fixture's generic pre-frame-0 ground-snap probe
                // (HeadlessTestFixture.Builder.build step 12 / GroupCollisionSystem
                // .resolveGroundAttachment threshold=14), which has no ROM
                // equivalent for bonus-stage entry and can wrongly latch
                // Status_InAir a tick early. ROM's bonus-stage spawn is a
                // Restart_level_flag level reload: Object_RAM (Player_1 included)
                // is fully cleared -- Status_InAir off -- before Get_LevelSizeStart
                // repositions the player from the zone's Start Location table
                // (sonic3k.asm:7619 clearRAM Object_RAM; 38160-38183
                // Get_LevelSizeStart). SpawnLevelMainSprites' zone-specific
                // air/animation branches (the only code that would otherwise set
                // Status_InAir before the level loop starts) are skipped whenever
                // Special_bonus_entry_flag is set, which bonus-stage entry always
                // does (sonic3k.asm:8117-8118 tst.b Special_bonus_entry_flag / bne
                // locret_69B6; 61896 move.b #2,Special_bonus_entry_flag). So the
                // ground/air transition for Gumball/Pachinko/Slots is decided
                // exclusively by frame 0's own Player_AnglePos probe, not a
                // bootstrap pre-check. S3kSlotBonusStageRuntime.bootstrap()
                // re-asserts Status_InAir on its dedicated slot sprite afterwards
                // when the ROM's slot-machine capture genuinely starts airborne,
                // so this reset only affects the fixture's pre-existing sprite.
                playable.setAir(false);
            }
        }
        // forcePlayerHighPriorityInBonusStage (high-priority art bucket) and
        // refreshPlayableSpriteArtCaches (DPLC cache) are render-only and
        // deliberately skipped: headless comparison never reads either.
        ObjectSpawn bootstrapSpawn = GameLoop.resolveBonusStageBootstrapSpawn(type);
        var objectManager = GameServices.level().getObjectManager();
        if (bootstrapSpawn != null && objectManager != null) {
            // Mirror ensureBonusStageBootstrapObjectPresent's duplicate guard
            // (GameLoop.java:2288-2293).
            boolean present = objectManager.getActiveObjects().stream()
                    .anyMatch(PachinkoEnergyTrapObjectInstance.class::isInstance);
            if (!present) {
                objectManager.addDynamicObject(
                        new PachinkoEnergyTrapObjectInstance(bootstrapSpawn));
            }
        }
        provider.onDeferredSetupComplete();
        // applyBonusStageEntry reconstructs the ROM state after Level's
        // one-time Load_Sprites/Process_Sprites setup at loc_6468
        // (sonic3k.asm:7849-7860). Do not let the fresh fixture's pending
        // authority execute that represented pass again when the shared
        // LevelFrameStep begins the bonus-stage interior.
        GameServices.level().discardPendingInitialProcessSpritesForStateRestoration();
        // Comparison-bootstrap seam (same pattern as applyInitialRngSeedForReplay
        // / metadata.rng_seed above): when the trace recorded the ROM's
        // free-running V_int_run_count at bonus-stage entry (recorder
        // v6.32-s3k+, bonus segments only -- see TraceMetadata#recordedVIntRunCount),
        // prime the slots runtime's counter base so Slots_CycleOptions's
        // recorded reel outcomes become reproducible instead of approximated
        // by the per-session ObjectManager.vblaCounter (S3K-Known-Discrepancies,
        // "Slots is also affected"). No-op for gumball/pachinko or legacy traces.
        if (type == BonusStageType.SLOT_MACHINE
                && meta.recordedVIntRunCount() != null
                && provider instanceof Sonic3kBonusStageCoordinator coordinator
                && coordinator.activeSlotRuntime() != null) {
            coordinator.activeSlotRuntime().primeVIntRunCountForReplay(
                    meta.recordedVIntRunCount(), trace.initialVblankCounter());
        }
        return true;
    }

    private static boolean shouldInterleaveS2TitleCardPrelude(TraceData trace,
                                                              int sidekickPreludeFrames,
                                                              int objectPreludeFrames) {
        if (trace == null || trace.metadata() == null) {
            return false;
        }
        TraceMetadata meta = trace.metadata();
        return "s2".equals(meta.game())
                && meta.nativePreludeMode()
                && sidekickPreludeFrames > 0
                && sidekickPreludeFrames == objectPreludeFrames;
    }

    private static void prepareSidekickPreludePlacement(
            TraceData trace,
            com.openggf.game.session.GameplayModeContext gameplayMode,
            boolean tornadoPreludeOrder) {
        boolean useMetadataStartAnchor = trace != null
                && trace.metadata() != null
                && "s2".equals(trace.metadata().game())
                && trace.metadata().nativePreludeMode()
                && !tornadoPreludeOrder;
        int[] levelStart = useMetadataStartAnchor
                ? resolveCurrentLevelStart()
                : null;
        for (AbstractPlayableSprite sidekick :
                gameplayMode.getSpriteManager().getRegisteredSidekicks()) {
            SidekickCpuController cpu = sidekick.getCpuController();
            if (cpu != null) {
                if (useMetadataStartAnchor && levelStart != null) {
                    cpu.captureLevelStartLeaderAnchor(levelStart[0], levelStart[1]);
                }
                cpu.applyLevelStartSidekickPlacementForBootstrap();
            }
        }
    }

    private static int[] resolveCurrentLevelStart() {
        var level = GameServices.levelOrNull();
        var module = GameServices.currentOrBootstrapGameModule();
        if (level == null || module == null || module.getZoneRegistry() == null) {
            return null;
        }
        return module.getZoneRegistry().getStartPosition(level.getCurrentZone(), level.getCurrentAct());
    }

    private static int recordS2TornadoRideStartLeadIn(TornadoObjectInstance tornado,
                                                       AbstractPlayableSprite player) {
        if (tornado == null || player == null || !tornado.isRideStartPreludeObject()) {
            return 0;
        }
        if (tornado.isSczRideStartPreludeObject()) {
            int startX = player.getCentreX();
            int startY = player.getCentreY();
            int[] yOffsets = {0, 0, 0, 0, 1};
            for (int offset : yOffsets) {
                player.setCentreX((short) startX);
                player.setCentreY((short) (startY + offset));
                player.setAir(true);
                player.setOnObject(false);
                recordLeaderHistoryForPrelude(player);
            }
            player.setCentreY((short) (startY + 4));
            return yOffsets.length;
        }
        if (tornado.isWfzStartRideStartPreludeObject()) {
            for (int i = 0; i < 2; i++) {
                player.setAir(true);
                player.setOnObject(false);
                recordLeaderHistoryForPrelude(player);
            }
            return 2;
        }
        return 0;
    }

    private static void recordLeaderHistoryForPrelude(AbstractPlayableSprite player) {
        player.recordFollowerHistoryForTick();
        player.clearFollowerHistoryRecordedFlag();
    }

    private static void applyS2TornadoRecordSamplePosition(TornadoObjectInstance tornado,
                                                            AbstractPlayableSprite player,
                                                            int sampleIndex,
                                                            int startY) {
        if (tornado == null || player == null || !tornado.isSczRideStartPreludeObject()) {
            return;
        }
        int[] yOffsets = {
                0, 0, 0, 0, 1, 4, 5, 7, 9, 10, 12, 13, 14,
                15, 16, 17, 17, 18, 18, 18, 19, 19, 18, 18, 18, 17
        };
        if (sampleIndex >= 0 && sampleIndex < yOffsets.length) {
            player.setCentreY((short) (startY + yOffsets[sampleIndex]));
        }
    }

    /**
     * Prime the leader's jump-button edge tracker so a BK2 input that holds
     * jump across the title-card / level boundary is not treated as a
     * fresh press on the first comparison frame.
     *
     * <p>ROM continuously updates {@code Ctrl_1_Held} from V-int regardless
     * of {@code Sonic_ControlsLock} (s2.asm:701,1361-1387 ReadJoypads /
     * sonic3k.asm equivalent). Edge detection ({@code Ctrl_1_Press}) is computed
     * each V-int as {@code (held ^ previous_held) & held}, so a button that
     * was held throughout the title card is NOT a press at the first
     * gameplay frame. Headless trace replay skips the title-card phase, so
     * the leader's {@code jumpInputPressedPreviousFrame} is virgin false
     * when frame 0 is consumed — a held jump button then masquerades as a
     * fresh press and the engine fires {@code Obj01_Jump} (s2.asm:36253-36260)
     * one frame before the ROM would. This perturbs frame 0 {@code air},
     * {@code y_speed}, and {@code y} and cascades to every subsequent row.
     *
     * <p>Read the BK2 frame immediately before the cursor (the last
     * title-card frame the production GameLoop would have ticked) and seed
     * both edge trackers with that jump bit.
     *
     * <p>This is bootstrap state equivalent to the BK2 save-state point;
     * it does not consume or hydrate trace data.
     */
    private static void primeLeaderJumpEdgeFromBk2Prelude(TraceReplayFixture fixture) {
        if (fixture == null || fixture.sprite() == null) {
            return;
        }
        int priorMask = fixture.peekRecordingInputAt(-1);
        if (priorMask < 0) {
            // No BK2 movie loaded or no frame before the cursor — leave
            // the virgin edge state untouched.
            return;
        }
        boolean priorJump = (priorMask & AbstractPlayableSprite.INPUT_JUMP) != 0;
        AbstractPlayableSprite leader = fixture.sprite();
        // Seed both the sprite-level edge (read by SidekickCpuController's
        // isJumpJustPressed gate) and the movement-controller edge (read by
        // PlayableSpriteMovement's inputJumpPress computation for Obj01_Jump).
        if (priorJump) {
            leader.setJumpInputPressed(true);
        }
        if (leader.getMovementManager() instanceof com.openggf.sprites.managers.PlayableSpriteMovement movement) {
            movement.primeJumpPreviousForBootstrap(priorJump);
        }
    }

    private static void applyS2TornadoTitleCardScrollPrelude(TraceData trace, ObjectManager objectManager) {
        if (!TraceReplayBootstrap.isS2TornadoRideStartMetadataCandidate(trace)
                || objectManager == null) {
            return;
        }
        TornadoObjectInstance tornado = findRideStartTornado(objectManager);
        if (tornado == null || !tornado.isSczRideStartPreludeObject()) {
            return;
        }

        var camera = GameServices.cameraOrNull();
        var parallax = GameServices.parallaxOrNull();
        if (camera == null || parallax == null) {
            return;
        }

        // ROM level load seeds Camera_X_pos from the level's default start
        // before the level-select route places Sonic on ObjB2. The first
        // compared row has already seen two pre-gameplay SwScrl_SCZ ticks; run
        // the native camera-driven scroll hook so Tornado_Velocity_X is primed
        // for ObjB2 on frame 0.
        camera.setX((short) (LevelData.SKY_CHASE.getStartXPos() - 0xA0));
        camera.setY((short) 0);
        parallax.resetZoneState();
        for (int i = 0; i < 2; i++) {
            parallax.advanceCameraDrivenScroll(Sonic2ZoneConstants.ZONE_SCZ, 0, camera, -(2 - i));
        }
    }

    private static int s2TornadoObjectPreludeFrames(TraceData trace, ObjectManager objectManager) {
        if (!TraceReplayBootstrap.isS2TornadoRideStartMetadataCandidate(trace)
                || objectManager == null) {
            return 0;
        }
        TornadoObjectInstance tornado = findRideStartTornado(objectManager);
        if (tornado == null || !tornado.isRideStartPreludeObject()) {
            return 0;
        }
        return TraceReplayBootstrap.s2TornadoTitleCardPreludeFramesForTraceReplay(trace);
    }

    private static void primeS2TornadoRideStart(TraceData trace,
                                                ObjectManager objectManager,
                                                AbstractPlayableSprite player) {
        if (!TraceReplayBootstrap.isS2TornadoRideStartMetadataCandidate(trace)
                || objectManager == null
                || player == null) {
            return;
        }
        TornadoObjectInstance tornado = findRideStartTornado(objectManager);
        if (tornado == null || !tornado.isRideStartPreludeObject()) {
            return;
        }
        Sonic2TornadoRidePrelude.Seed seed = Sonic2TornadoRidePrelude.forTornado(tornado);
        player.setSubpixelRaw(player.getXSubpixelRaw(), seed.playerYSubpixel());
        if (tornado.isSczRideStartPreludeObject()) {
            tornado.primeRideStart(player.getCentreX(), player.getCentreY(), seed.tornadoYSubpixel8());
        }
    }

    /**
     * Live trace visualisation starts at trace frame 0 and must not consume
     * visible trace prefix frames before the first rendered frame. Headless
     * replay may warm through legacy prefixes to align comparison, but doing
     * that in the live launcher makes full-intro traces appear to skip ahead.
     */
    public static BootstrapResult applyLiveBootstrap(TraceData trace,
                                                     TraceReplayFixture fixture,
                                                     int preTraceOscOverride) {
        int preTraceOsc = TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(
                trace, preTraceOscOverride);
        for (int i = 0; i < preTraceOsc; i++) {
            OscillationManager.update(-(preTraceOsc - i));
        }
        advanceAnimatedTilePreludeForTraceReplay(trace);
        TraceReplayBootstrap.SnapshotReport snapshotReport =
                TraceReplayBootstrap.reportPreTraceObjectSnapshots(trace);
        return new BootstrapResult(snapshotReport, TraceReplayBootstrap.ReplayStartState.DEFAULT);
    }

    private static void advanceAnimatedTilePreludeForTraceReplay(TraceData trace) {
        int frames = TraceReplayBootstrap.s3kCompleteRunAnimatedTilePreludeFramesForTraceReplay(trace);
        if (frames <= 0 || GameServices.levelOrNull() == null) {
            return;
        }
        var animatedPatternManager = GameServices.levelOrNull().getAnimatedPatternManager();
        for (int i = 0; i < frames; i++) {
            if (animatedPatternManager instanceof Sonic3kLevelAnimationManager s3kAnimationManager) {
                s3kAnimationManager.updatePatternsOnlyForReplayBootstrap();
            } else if (animatedPatternManager != null) {
                animatedPatternManager.update();
            }
        }
    }

    /**
     * Align replay-local gameplay counters once before the comparison loop.
     * This is bootstrap state equivalent to loading the BK2 save-state point;
     * it is not per-frame trace hydration. The value comes from the trace row
     * immediately before the first driven row so native per-frame increments
     * keep both counters aligned afterward.
     */
    public static void alignFrameCountersForReplayStart(TraceFrame previousDriveFrame,
                                                        TraceFrame firstDriveFrame) {
        if (previousDriveFrame != null && previousDriveFrame.gameplayFrameCounter() >= 0
                && GameServices.spritesOrNull() != null) {
            GameServices.spritesOrNull().setFrameCounter(previousDriveFrame.gameplayFrameCounter());
        }
        // LevelManager.setFrameCounter's contract (see its javadoc) is the
        // PREVIOUS completed level frame: ROM increments Level_frame_counter
        // before Process_Sprites, so consumers recover the current ROM value
        // with getFrameCounter() + 1 (16 call sites do exactly that). Seed it
        // from the same pre-row this method's javadoc names and the sprite
        // branch above already uses; seeding the first driven row's value put
        // every frame-counter-keyed object phase one frame ahead of ROM for
        // the whole segment. That was invisible while s3k_complete_run_recorder
        // sampled 0xFE08 (Debug_placement_mode, dead-zero) and only surfaced
        // once the counter column was captured live.
        if (previousDriveFrame != null && previousDriveFrame.gameplayFrameCounter() >= 0
                && GameServices.levelOrNull() != null) {
            GameServices.levelOrNull().setFrameCounter(previousDriveFrame.gameplayFrameCounter());
        } else if (firstDriveFrame != null && firstDriveFrame.gameplayFrameCounter() >= 0
                && GameServices.levelOrNull() != null) {
            GameServices.levelOrNull().setFrameCounter(firstDriveFrame.gameplayFrameCounter() - 1);
        }
    }

    /**
     * Align replay-local gameplay counters once before the comparison loop.
     *
     * <p>Most traces expose ROM {@code Level_frame_counter} directly in
     * {@code physics.csv}. S3K complete-run segments recorded before that column
     * was reliable can still expose the same low-six timing phase through the
     * per-frame Tails CPU {@code pos_table_index}: Sonic_RecordPos advances that
     * byte by four each native object tick, and the Tails catch-up cadence reads
     * {@code Level_frame_counter & $3F}. Use it only as a trace-start timing seed
     * when initial visible hold rows are intentionally skipped by replay.
     */
    public static void alignFrameCountersForReplayStart(
            TraceData trace,
            TraceReplayBootstrap.ReplayStartState replayStart,
            TraceFrame previousDriveFrame,
            TraceFrame firstDriveFrame) {
        if (GameServices.levelOrNull() != null
                && GameServices.levelOrNull().getObjectManager() != null) {
            GameServices.levelOrNull().initRingFloorCheckCounterPhase(
                    ringFloorCheckRuntimeOffset(
                            trace.metadata().ringFloorCheckCounterPhase(), liveRingFloorCheckPhase()));
        }
        int completeRunSeed = s3kCompleteRunFrameCounterSeedForReplayStart(trace, replayStart);
        if (completeRunSeed >= 0) {
            if (GameServices.spritesOrNull() != null) {
                GameServices.spritesOrNull().setFrameCounter(completeRunSeed);
            }
            if (GameServices.levelOrNull() != null) {
                GameServices.levelOrNull().setFrameCounter(completeRunSeed);
            }
            return;
        }
        alignFrameCountersForReplayStart(previousDriveFrame, firstDriveFrame);
    }

    /**
     * Converts a legacy trace's absolute Obj37 low-bit phase into an offset
     * from the live per-game baseline. Normal gameplay never calls this path:
     * S3K retains its native four-count level-start phase in {@code RingRules}.
     * Older replay fixtures either record a reconstructed absolute phase or
     * historically assume zero, so normalizing here preserves their one-time
     * start-clock state without changing the live default.
     */
    static int ringFloorCheckRuntimeOffset(Integer recordedAbsolutePhase, int liveDefaultPhase) {
        int replayAbsolutePhase = recordedAbsolutePhase != null ? recordedAbsolutePhase : 0;
        return replayAbsolutePhase - liveDefaultPhase;
    }

    private static int liveRingFloorCheckPhase() {
        return GameServices.module().getRules().ring().ringFloorCheckCounterPhase();
    }

    public static int s3kCompleteRunFrameCounterSeedForReplayStart(
            TraceData trace,
            TraceReplayBootstrap.ReplayStartState replayStart) {
        if (!TraceReplayBootstrap.isS3kCompleteRunSegment(trace)
                || replayStart == null
                || trace.metadata() == null
                || !trace.metadata().hasPerFrameCpuState()
                || trace.metadata().recordedSidekicks().isEmpty()
                || TraceReplayBootstrap.isS3kCompleteRunHandoffCounterTickRow(trace)) {
            return -1;
        }
        int startIndex = Math.max(0, replayStart.startingTraceIndex());
        int firstFullFrameIndex = firstFullLevelFrameIndex(trace, startIndex);
        if (firstFullFrameIndex <= startIndex) {
            return -1;
        }
        TraceFrame firstFullFrame = trace.getFrame(firstFullFrameIndex);
        if (firstFullFrame.gameplayFrameCounter() > 0) {
            return -1;
        }
        String sidekick = trace.metadata().recordedSidekicks().getFirst();
        TraceEvent.CpuState cpuState = trace.cpuStateForFrame(firstFullFrame.frame(), sidekick);
        if (cpuState == null || cpuState.posTableIndex() < 0) {
            return -1;
        }
        int visibleCounterLow6 = ((cpuState.posTableIndex() & 0xFF) >>> 2) & 0x3F;
        return (visibleCounterLow6 - 1) & 0x3F;
    }

    private static int firstFullLevelFrameIndex(TraceData trace, int startIndex) {
        TraceFrame previous = startIndex > 0 ? trace.getFrame(startIndex - 1) : null;
        for (int i = startIndex; i < trace.frameCount(); i++) {
            TraceFrame current = trace.getFrame(i);
            if (TraceReplayBootstrap.phaseForReplay(trace, previous, current)
                    == TraceExecutionPhase.FULL_LEVEL_FRAME) {
                return i;
            }
            previous = current;
        }
        return -1;
    }

    /**
     * Reapply the metadata-recorded start centre coordinates and run
     * an initial ground-attachment pass so the sprite's Y/angle match
     * the ROM's post-title-card state. Mirrors
     * {@code HeadlessTestFixture.Builder.build} steps 6 and 11 so
     * headless and live paths end up with identical post-load sprite
     * state.
     *
     * <p>Call this BEFORE {@link #applyBootstrap}. The fixture runs
     * these steps at build time before replay bootstrap policy runs;
     * running them afterwards would perturb the native state selected
     * by {@code applyReplayStartState}.
     *
     * <p>Gated on
     * {@link TraceReplayBootstrap#shouldApplyMetadataStartPositionForTraceReplay}
     * (i.e. {@code replaySeedTraceIndex == 0 && !legacyS3kAizIntro}).
     * Legacy-AIZ traces are short-circuited because their prefix is
     * consumed by deterministic warmup.
     */
    /**
     * Runs the post-load level init that {@code HeadlessTestFixture.Builder.build}
     * performs unconditionally (steps 7-12): re-anchor registered sidekicks, wire
     * {@code GroundSensor}, re-run camera + level-event init so they pick up the
     * spawned player, re-apply S3K zone player state, refresh sidekick CPU bounds,
     * and snap the player to ground.
     *
     * <p>The test fixture always runs these at build time because
     * {@code TestEnvironment.resetPerTest()} cleared the transient managers. The
     * headless trace-capture tool boots via {@code HeadlessGameBoot.boot}, which
     * only does {@code loadZoneAndAct}; it must call this so capture starts from
     * the same post-load state the tests do (otherwise physics drifts by the
     * first collision). {@link #applyStartPositionAndGroundSnap} performs the same
     * init for metadata-start traces; this is the unconditional variant for
     * callers (e.g. pre-level-intro-prefix traces) where that method short-circuits.
     */
    public static void applyPostLoadLevelInit(TraceData trace) {
        var level = GameServices.levelOrNull();
        if (level == null) {
            return;
        }
        AbstractPlayableSprite sprite = GameServices.cameraOrNull() != null
                ? GameServices.camera().getFocusedSprite()
                : null;
        GameplayTeamBootstrap.repositionRegisteredSidekicks(GameServices.module(), level);
        GroundSensor.setLevelManager(level);
        level.initCameraForLevel();
        level.initLevelEventsForLevel();
        var levelEventProvider = GameServices.module().getLevelEventProvider();
        if (levelEventProvider instanceof com.openggf.game.sonic3k.Sonic3kLevelEventManager s3kLem) {
            s3kLem.applyZonePlayerState();
        }
        refreshSidekickCpuBoundsFromCamera();
        var collision = GameServices.collisionOrNull();
        if (collision != null && sprite != null
                && !shouldPreserveFreshGroundedStatusUntilFirstDispatch(trace)) {
            collision.resolveGroundAttachment(
                    FrameCollisionPlan.terrainOnly(), sprite, 14, () -> false);
        }
    }

    public static void applyStartPositionAndGroundSnap(TraceData trace,
                                                       TraceReplayFixture fixture) {
        if (!TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace)) {
            return;
        }
        AbstractPlayableSprite sprite = fixture.sprite();
        if (sprite == null) {
            return;
        }
        TraceMetadata meta = trace.metadata();

        // Mirror HeadlessTestFixture.Builder.build steps 6-11 exactly:
        // set the metadata centre coords, re-anchor sidekicks, wire
        // GroundSensor's level-manager override, re-run the camera +
        // level-events init so they pick up the new player position
        // (loadZoneAndAct ran them against the ROM default), then
        // snap to ground. Without the re-inits the camera and event
        // handlers keep the default-start-derived bounds from the
        // initial load, which drifts physics at the first collision.
        sprite.setCentreX(meta.startX());
        sprite.setCentreY(meta.startY());
        var level = GameServices.levelOrNull();
        if (level != null) {
            GameplayTeamBootstrap.repositionRegisteredSidekicks(
                    GameServices.module(),
                    level);
            if ("s2".equals(meta.game()) && meta.nativePreludeMode()) {
                for (AbstractPlayableSprite sidekick : GameServices.sprites().getRegisteredSidekicks()) {
                    SidekickCpuController cpu = sidekick.getCpuController();
                    if (cpu != null) {
                        cpu.captureLevelStartLeaderAnchor(meta.startX(), meta.startY());
                    }
                }
            }
            GroundSensor.setLevelManager(level);
            level.initCameraForLevel();
            level.initLevelEventsForLevel();
            // Re-apply zone player state after sidekick reposition. ROM's
            // SpawnLevelMainSprites_SpawnPlayers (sonic3k.asm:8335-8427) sets
            // sidekick position FIRST, then SpawnLevelMainSprites
            // (sonic3k.asm:8132-8205) sets the in-air status for zones like
            // MGZ1 / HCZ1 / LRZ1 / SSZ. repositionRegisteredSidekicks above
            // clears the in-air bit via spawnSidekicks, so the zone-event
            // handler must run again to restore the falling-intro state.
            var levelEventProvider = GameServices.module().getLevelEventProvider();
            if (levelEventProvider instanceof com.openggf.game.sonic3k.Sonic3kLevelEventManager s3kLem) {
                if (TraceReplayBootstrap.isS3kCompleteRunSegment(trace)) {
                    s3kLem.applyCompleteRunSegmentPlayerStateAfterTitleCard();
                    s3kLem.armCarryIntroHandoffAfterTitleCard();
                } else {
                    s3kLem.applyZonePlayerState();
                }
            }
            refreshSidekickCpuBoundsFromCamera();
        }
        // Ground snap: 14 subpixel threshold matches the fixture. S3K
        // complete-run segments start from an in-level handoff row rather than
        // a fresh title-card spawn; their metadata centre is already the
        // recorded ROM handoff position and must not be adjusted again before
        // the first state-changing row is driven.
        var collision = GameServices.collisionOrNull();
        if (collision != null
                && TraceReplayBootstrap.shouldGroundSnapMetadataStartForTraceReplay(trace)
                && !shouldPreserveFreshGroundedStatusUntilFirstDispatch(trace)) {
            collision.resolveGroundAttachment(
                    FrameCollisionPlan.terrainOnly(), sprite, 14, () -> false);
        }
    }

    /**
     * Applies the game-owned fresh-player lifecycle rule to a structurally
     * fresh trace start. Complete-run, bonus-stage, and mid-level segments do
     * not enter this path.
     */
    public static boolean shouldPreserveFreshGroundedStatusUntilFirstDispatch(TraceData trace) {
        return TraceReplayBootstrap.shouldGroundSnapMetadataStartForTraceReplay(trace)
                && GameServices.module().getLevelInitProfile()
                        .preserveFreshGroundedStatusUntilFirstDispatch();
    }

    /**
     * Re-syncs the sidekick CPU's cached level-bound overrides after camera
     * initialization or level-event setup has rewritten the live camera bounds.
     *
     * <p>This is native bootstrap state, not trace hydration: S2/S3K ROM Tails
     * reads the same camera boundary words that Sonic does during its first
     * title-card object ticks. The engine mirrors those words in
     * {@code SidekickCpuController}, so replay setup must refresh the mirror
     * before the sidekick-only prelude can run boundary checks.
     */
    public static void refreshSidekickCpuBoundsFromCamera() {
        var camera = GameServices.cameraOrNull();
        var spriteManager = GameServices.spritesOrNull();
        if (camera == null || spriteManager == null) {
            return;
        }
        int maxY = Math.max(camera.getMaxY(), camera.getMaxYTarget());
        for (AbstractPlayableSprite sidekick : spriteManager.getRegisteredSidekicks()) {
            var cpu = sidekick.getCpuController();
            if (cpu != null) {
                cpu.setLevelBounds(
                        (int) camera.getMinX(),
                        (int) camera.getMaxX(),
                        maxY);
            }
        }
    }

    private static TornadoObjectInstance findRideStartTornado(ObjectManager objectManager) {
        for (ObjectInstance instance : objectManager.getActiveObjects()) {
            if (instance instanceof TornadoObjectInstance tornado
                    && !tornado.isDestroyed()
                    && tornado.isPersistent()) {
                return tornado;
            }
        }
        return null;
    }

    public record BootstrapResult(
            TraceReplayBootstrap.SnapshotReport snapshotReport,
            TraceReplayBootstrap.ReplayStartState replayStart) {
    }
}
