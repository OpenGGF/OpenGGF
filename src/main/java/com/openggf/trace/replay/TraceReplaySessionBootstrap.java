package com.openggf.trace.replay;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GroundMode;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.OscillationManager;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.sonic2.objects.TornadoObjectInstance;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic2.trace.Sonic2TornadoRidePrelude;
import com.openggf.level.LevelData;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;

import java.util.List;
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
 *       where policy allows, seed trace-start global timing counters, and
 *       choose the replay comparison cursor. It must not copy recorded
 *       object, player, sidekick, RNG, or camera state into the engine.</li>
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
        int preTraceOsc = TraceReplayBootstrap.preTraceOscillationFramesForTraceReplay(
                trace, preTraceOscOverride);
        for (int i = 0; i < preTraceOsc; i++) {
            OscillationManager.update(-(preTraceOsc - i));
        }
        OscillationManager.suppressNextFrames(
                TraceReplayBootstrap.initialOscillationSuppressionFramesForTraceReplay(trace));
        int sidekickPreludeFrames =
                TraceReplayBootstrap.sidekickTitleCardPreludeFramesForTraceReplay(trace);
        int objectPreludeFrames = 0;
        int zoneFeaturePreludeFrames =
                TraceReplayBootstrap.zoneFeatureTitleCardPreludeFramesForTraceReplay(trace);
        var gameplayMode = fixture.gameplayMode();
        if (gameplayMode != null
                && gameplayMode.getLevelManager() != null
                && gameplayMode.getLevelManager().getObjectManager() != null) {
            ObjectManager objectManager = gameplayMode.getLevelManager().getObjectManager();
            objectPreludeFrames = s2TornadoObjectPreludeFrames(trace, objectManager);
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
                    trace.initialVblankCounter() - objectPreludeFrames - 1);
        }
        if (objectPreludeFrames > 0
                && gameplayMode != null
                && gameplayMode.getLevelManager() != null
                && gameplayMode.getLevelManager().getObjectManager() != null) {
            var levelManager = gameplayMode.getLevelManager();
            var objectManager = levelManager.getObjectManager();
            applyS2TornadoTitleCardScrollPrelude(trace, objectManager);
            var camera = GameServices.cameraOrNull();
            int cameraX = camera != null ? camera.getX() : 0;
            for (int i = 0; i < objectPreludeFrames; i++) {
                objectManager.update(cameraX, null, List.of(), -(objectPreludeFrames - i), false);
            }
        }
        applyS2TornadoRideStart(trace, fixture);
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
            for (AbstractPlayableSprite sidekick :
                    gameplayMode.getSpriteManager().getRegisteredSidekicks()) {
                SidekickCpuController cpu = sidekick.getCpuController();
                if (cpu != null) {
                    cpu.applyLevelStartSidekickPlacementForBootstrap();
                }
            }
            gameplayMode.getSpriteManager().warmUpCpuSidekicksOnly(
                    sidekickPreludeFrames,
                    gameplayMode.getLevelManager());
        }
        primeLeaderJumpEdgeFromBk2Prelude(fixture);
        TraceReplayBootstrap.SnapshotReport snapshotReport =
                TraceReplayBootstrap.reportPreTraceObjectSnapshots(trace);
        TraceReplayBootstrap.ReplayStartState replayStart =
                TraceReplayBootstrap.applyReplayStartStateForTraceReplay(trace, fixture);
        return new BootstrapResult(snapshotReport, replayStart);
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
        if (!TraceReplayBootstrap.usesS2TornadoRideStartForTraceReplay(trace)
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
        if (!TraceReplayBootstrap.usesS2TornadoRideStartForTraceReplay(trace)
                || objectManager == null) {
            return 0;
        }
        TornadoObjectInstance tornado = findRideStartTornado(objectManager);
        if (tornado == null || !tornado.isRideStartPreludeObject()) {
            return 0;
        }
        return TraceReplayBootstrap.s2TornadoTitleCardPreludeFramesForTraceReplay(trace);
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
        TraceReplayBootstrap.SnapshotReport snapshotReport =
                TraceReplayBootstrap.reportPreTraceObjectSnapshots(trace);
        return new BootstrapResult(snapshotReport, TraceReplayBootstrap.ReplayStartState.DEFAULT);
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
        if (firstDriveFrame != null && firstDriveFrame.gameplayFrameCounter() >= 0
                && GameServices.levelOrNull() != null) {
            GameServices.levelOrNull().setFrameCounter(firstDriveFrame.gameplayFrameCounter());
        }
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
                s3kLem.applyZonePlayerState();
            }
            refreshSidekickCpuBoundsFromCamera();
        }
        // Ground snap: 14 subpixel threshold matches the fixture.
        var collision = GameServices.collisionOrNull();
        if (collision != null) {
            collision.resolveGroundAttachment(sprite, 14, () -> false);
        }
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

    private static void applyS2TornadoRideStart(TraceData trace, TraceReplayFixture fixture) {
        if (!TraceReplayBootstrap.usesS2TornadoRideStartForTraceReplay(trace)
                || fixture == null
                || fixture.sprite() == null) {
            return;
        }
        var gameplayMode = fixture.gameplayMode();
        if (gameplayMode == null
                || gameplayMode.getLevelManager() == null
                || gameplayMode.getLevelManager().getObjectManager() == null) {
            return;
        }
        ObjectManager objectManager = gameplayMode.getLevelManager().getObjectManager();
        TornadoObjectInstance tornado = findRideStartTornado(objectManager);
        if (tornado == null) {
            return;
        }

        AbstractPlayableSprite player = fixture.sprite();
        TraceMetadata meta = trace.metadata();
        short playerStartX = meta.startX();
        player.setCentreX(playerStartX);
        player.setCentreY(meta.startY());
        if (!tornado.isRideStartPreludeObject()) {
            return;
        }
        Sonic2TornadoRidePrelude.Seed seed = Sonic2TornadoRidePrelude.forTornado(tornado);
        player.setSubpixelRaw(player.getXSubpixelRaw(), seed.playerYSubpixel());
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAngle((byte) 0);
        player.setRolling(false);
        player.setAir(false);
        player.setOnObject(true);
        player.setGroundMode(GroundMode.GROUND);

        tornado.primeRideStart(playerStartX, meta.startY(), seed.tornadoYSubpixel8());
        if (tornado.isWfzStartRideStartPreludeObject()) {
            // The 26-frame object prelude consumed by the engine collapses ROM's
            // two ObjB2 init frames (ObjB2_Init at s2.asm:78271-78284 and
            // ObjB2_Main_WFZ_Start_init at s2.asm:78368-78372) into one engine
            // frame, leaving the WFZ Tornado one main-routine move ahead of ROM
            // by frame -1. Roll the timer back by one tick so the
            // WFZ_Start_main -> shot_down transition fires on the same trace
            // frame as ROM (s2.asm:78375-78394).
            tornado.compensateForCollapsedWfzInit();
        }
        objectManager.forceRidingObjectForBootstrap(player, tornado);
        objectManager.refreshRidingTrackingPosition(tornado);
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
