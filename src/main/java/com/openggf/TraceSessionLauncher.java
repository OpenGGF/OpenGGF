package com.openggf;

import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.control.InputHandler;
import com.openggf.configuration.GlfwKeyNameResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.game.rewind.InputSource;
import com.openggf.game.rewind.PlaybackController;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindSeekAwareEngineStepper;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.sprites.ghost.GhostTraceRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.testmode.TraceCameraFocusController;
import com.openggf.testmode.TraceHudOverlay;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.replay.TraceReplayDriver;
import com.openggf.trace.replay.TraceGhostHook;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import com.openggf.trace.timing.TraceHardwareTimingBoundaryObserver;

import java.util.List;
import java.util.logging.Logger;

/**
 * Drives a Trace Test Mode playback session. The picker calls
 * {@link #launch(TraceEntry)}; the launcher then asynchronously:
 * <ol>
 *   <li>asks {@link GameLoop#launchGameByEntry} to run the same
 *       master-title exit path as a user selecting the game,</li>
 *   <li>when the runtime is ready (callback from step 1), loads the
 *       trace's zone/act, starts programmatic BK2 playback, applies
 *       {@link TraceReplaySessionBootstrap}, and attaches a
 *       {@link LiveTraceComparator} + HUD overlay.</li>
 * </ol>
 *
 * <p>The session ends either on BK2 exhaustion (after a 1-second hold
 * on {@code TRACE COMPLETE}) or on Esc, both converging on a single
 * fade-to-black → {@link #teardown()} → picker path.
 */
public final class TraceSessionLauncher {

    private static final Logger LOGGER =
            Logger.getLogger(TraceSessionLauncher.class.getName());

    /** Hold (in seconds at 60 Hz) on TRACE COMPLETE before auto-fade. */
    private static final double COMPLETION_HOLD_SECONDS = 1.0;

    private static TraceSessionLauncher activeSession;

    private static final String SPECIAL_STAGE_PROFILE = "s2_special_stage";

    private final TraceEntry entry;
    /** Null for special-stage sessions (which use {@link #ssTrace} instead). */
    private final TraceData trace;
    private final Bk2Movie movie;
    /**
     * Special-stage trace payload; non-null only for an SS visual session
     * (parallel branch). Level sessions leave this null and drive the runtime
     * through the {@link TraceReplayDriver}/{@link LiveTraceComparator} stack
     * above. An SS session has no comparator/rewind machinery, so every
     * level-path method here is already null-guarded on {@link #comparator}.
     */
    private final SpecialStageTraceData ssTrace;
    /** Absolute BK2 input-log row backing SS trace frame 0 (metadata offset). */
    private final int ssBk2FrameOffset;
    /** Last SS trace frame to play; the session fades out after it. */
    private final int ssStageFinishedFrame;
    /** Cursor into the SS trace; advances one row per engine frame (lag or not). */
    private int ssCursor;
    /**
     * Non-null only for a multi-segment run session (parallel branch). Each
     * entry is a planned segment (manifest record + loaded {@link TraceData}
     * + boundary transitions) from {@link TraceRunReplayWalker#plan}. A run
     * session drives {@link #runAdvancer} from the all-mode GameLoop hook
     * instead of the LEVEL-only {@link #tick()} completion-hold.
     */
    private List<TraceRunReplayWalker.SegmentPlan> runSegments;
    private RunSegmentAdvancer runAdvancer;
    private TraceRunReplayWalker.HardwareTimingCoordinator runHardwareTiming;
    private int runSpecialTimingSegment = -1;
    private int runSpecialTimingRow;
    /**
     * Snapshot of the user's gameplay-altering config taken before
     * {@link TraceReplaySessionBootstrap#prepareConfiguration} ran.
     * Restored in {@link #teardown()} so the picker returns to the
     * user's own team / cross-game / S3K_SKIP_INTROS preferences.
     */
    private final TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot;
    private LiveTraceComparator comparator;
    private TraceCameraFocusController cameraFocusController;
    private TraceHudOverlay overlay;
    private final GhostTraceRenderer ghostRenderer = new GhostTraceRenderer();
    /** Stable hook ref so set/clear match by identity in {@link TraceGhostHook}. */
    private final TraceGhostHook.GhostLayerRenderer ghostHook = this::renderGhostsForLayer;
    private TraceReplayFixture fixture;
    private PlaybackController rewindPlaybackController;
    private RewindController rewindController;
    private int rewindMovieBaseFrame;
    private int rewindTraceBaseFrame;
    private boolean realtimeRewinding;
    private boolean realtimeReleasePending;
    private AudioPresentationPolicy pendingRealtimeReleasePolicy;

    private boolean completionArmed;
    private int completionHoldFrames;
    private boolean fadeStarted;
    private boolean teardownPending;

    private TraceSessionLauncher(TraceEntry entry, TraceData trace, Bk2Movie movie,
                                 TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot) {
        this.entry = entry;
        this.trace = trace;
        this.movie = movie;
        this.configSnapshot = configSnapshot;
        this.ssTrace = null;
        this.ssBk2FrameOffset = 0;
        this.ssStageFinishedFrame = 0;
        this.ssCursor = 0;
    }

    /**
     * Special-stage visual session constructor (parallel to the level
     * constructor). Package-private so the skip-gate test can build a session
     * from the committed MVP trace without a live engine. The stage-finished
     * boundary comes from the trace's {@code stage_finished} aux event (or the
     * final frame if absent).
     */
    TraceSessionLauncher(TraceEntry entry, Bk2Movie movie, SpecialStageTraceData ssTrace,
                         TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot) {
        this.entry = entry;
        this.trace = null;
        this.movie = movie;
        this.configSnapshot = configSnapshot;
        this.ssTrace = ssTrace;
        this.ssBk2FrameOffset = entry.metadata().bk2FrameOffset();
        this.ssStageFinishedFrame =
                ssTrace.stageFinishedFrame().orElse(ssTrace.frameCount() - 1);
        this.ssCursor = 0;
    }

    /**
     * Multi-segment run session constructor (parallel to the level/SS
     * constructors). {@link #trace} is left null — {@link #finishRunLaunch}
     * and each segment advance read the active segment's {@code TraceData}
     * from {@link #runSegments} directly instead.
     */
    TraceSessionLauncher(TraceEntry entry, Bk2Movie movie,
                         List<TraceRunReplayWalker.SegmentPlan> runSegments,
                         TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot) {
        this.entry = entry;
        this.trace = null;
        this.movie = movie;
        this.configSnapshot = configSnapshot;
        this.ssTrace = null;
        this.ssBk2FrameOffset = 0;
        this.ssStageFinishedFrame = 0;
        this.ssCursor = 0;
        this.runSegments = runSegments;
    }

    public static TraceSessionLauncher active() {
        return activeSession;
    }

    public static boolean launch(TraceEntry entry) {
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            LOGGER.severe("Cannot launch trace " + entry.dir()
                    + ": Engine is not initialised");
            return false;
        }
        // prepareConfiguration must run BEFORE launchGameByEntry
        // because the master-title exit handler calls
        // GameplayTeamBootstrap.registerActiveTeam, which reads
        // MAIN_CHARACTER_CODE / SIDEKICK_CHARACTER_CODE to build the
        // sprites for this session. If we deferred the write until
        // after the handler, the session would use the pre-trace team.
        //
        // Pre-flight the fade check via GameLoop so we don't mutate
        // config and then fail at launchGameByEntry with a
        // fade-active throw. GameServices.fade() isn't usable here —
        // no gameplay mode exists at master-title time — so go through
        // GameLoop which resolves the graphics-backed fade manager.
        if (!loop.canLaunchGameNow()) {
            LOGGER.severe("Cannot launch trace " + entry.dir()
                    + ": a master-title fade is already in flight");
            return false;
        }
        // Snapshot the user's gameplay config BEFORE prepareConfiguration
        // mutates it, so teardown can restore the team / cross-game /
        // S3K_SKIP_INTROS preferences the user actually had.
        clearLaunchSessionOverridesBeforeTraceSnapshot(GameServices.configuration());
        TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot =
                TraceReplaySessionBootstrap.snapshotGameplayConfig();
        // Multi-segment trace runs take their own branch BEFORE the
        // special-stage profile check below: a run's entry.metadata() is
        // segment 0's metadata, which may itself carry the SS trace_profile
        // (a run can start with a special-stage segment) and must not be
        // hijacked into the single-segment SS path.
        if (entry.isRun()) {
            return launchRun(entry, loop, configSnapshot);
        }
        // Special-stage traces (no meaningful zone/act) take a parallel branch:
        // they skip the level driver stack entirely and drive the SS runtime
        // directly through GameLoop's SPECIAL_STAGE update.
        if (SPECIAL_STAGE_PROFILE.equals(entry.metadata().traceProfile())) {
            return launchSpecialStage(entry, loop, configSnapshot);
        }
        boolean configMutated = false;
        try {
            TraceData trace = TraceData.load(entry.dir());
            Bk2Movie movie = new Bk2MovieLoader().load(entry.bk2Path());
            TraceSessionLauncher session = new TraceSessionLauncher(
                    entry, trace, movie, configSnapshot);
            TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());
            configMutated = true;
            SessionManager.armNextGameplayAdmissionPolicy(
                    trace.metadata().hasHardwareTimingStream()
                            ? HardwareReadinessAdmissionPolicy.RECORDED
                            : HardwareReadinessAdmissionPolicy.LIVE);
            loop.launchGameByEntry(
                    resolveGameEntry(entry.gameId()),
                    session::finishLaunchAfterGameBootstrap);
            return true;
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to launch trace " + entry.dir(), e);
            // If we already mutated config before launchGameByEntry
            // threw, restore the user's settings so the picker
            // resumes with their preferences intact.
            restoreFailedLaunch(configSnapshot, configMutated);
            return false;
        }
    }

    /**
     * Parallel launch path for a multi-segment trace run. Boots the game
     * module against segment 0's zone/act exactly like the ordinary level
     * branch (segment 0's zone/act are already engine-converted by
     * {@link TraceEntry#forRun}), then {@link #finishRunLaunch} takes over
     * from the game-bootstrap callback and additionally stores the planned
     * segment list the {@link RunSegmentAdvancer} walks.
     */
    private static boolean launchRun(TraceEntry entry, GameLoop loop,
            TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot) {
        boolean configMutated = false;
        try {
            List<TraceRunReplayWalker.SegmentPlan> segments =
                    TraceRunReplayWalker.plan(entry.runManifest(), entry.runDir());
            TraceData seg0Trace = segments.get(0).trace();
            Bk2Movie movie = new Bk2MovieLoader().load(entry.bk2Path());
            TraceSessionLauncher session =
                    new TraceSessionLauncher(entry, movie, segments, configSnapshot);
            TraceReplaySessionBootstrap.prepareConfiguration(seg0Trace, seg0Trace.metadata());
            configMutated = true;
            SessionManager.armNextGameplayAdmissionPolicy(
                    TraceRunReplayWalker.hasHardwareTimingStream(segments)
                            ? HardwareReadinessAdmissionPolicy.RECORDED
                            : HardwareReadinessAdmissionPolicy.LIVE);
            loop.launchGameByEntry(
                    resolveGameEntry(entry.gameId()),
                    session::finishRunLaunch);
            return true;
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to launch trace run " + entry.dir(), e);
            restoreFailedLaunch(configSnapshot, configMutated);
            return false;
        }
    }

    /**
     * Parallel launch path for a Sonic 2 special-stage visual session. Boots
     * the game module the same way the level path does (so sprites / gameplay
     * mode / ROM are wired), then enters SPECIAL_STAGE mode directly from the
     * callback instead of loading a zone/act.
     */
    private static boolean launchSpecialStage(TraceEntry entry, GameLoop loop,
            TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot) {
        boolean configMutated = false;
        try {
            SpecialStageTraceData ssTrace = SpecialStageTraceData.load(entry.dir());
            Bk2Movie movie = new Bk2MovieLoader().load(entry.bk2Path());
            TraceSessionLauncher session =
                    new TraceSessionLauncher(entry, movie, ssTrace, configSnapshot);
            prepareSpecialStageConfiguration(entry.metadata());
            configMutated = true;
            armSpecialStageAdmissionPolicy(ssTrace);
            loop.launchGameByEntry(
                    resolveGameEntry(entry.gameId()),
                    session::finishSpecialStageLaunch);
            return true;
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to launch SS trace " + entry.dir(), e);
            restoreFailedLaunch(configSnapshot, configMutated);
            return false;
        }
    }

    static void restoreFailedLaunch(
            TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot,
            boolean configMutated) {
        SessionManager.clearNextGameplayAdmissionPolicy();
        if (configMutated) {
            TraceReplaySessionBootstrap.restoreGameplayConfig(configSnapshot);
        }
    }

    static void armSpecialStageAdmissionPolicy(SpecialStageTraceData trace) {
        SessionManager.armNextGameplayAdmissionPolicy(
                trace.metadata().hasHardwareTimingStream()
                        ? HardwareReadinessAdmissionPolicy.RECORDED
                        : HardwareReadinessAdmissionPolicy.LIVE);
    }

    /**
     * Applies per-game special-stage configuration for trace replay. Routes
     * through the static helper to centralize the shared team / cross-game /
     * S3K fresh-load policy. Reads the fresh_load metadata field to determine
     * whether to override the S3K intro-skip gate.
     *
     * @param meta the trace metadata (provides recorded team, game id, and fresh_load flag)
     */
    static void prepareSpecialStageConfiguration(TraceMetadata meta) {
        SonicConfigurationService config = GameServices.configuration();
        applyPerGameSpecialStageConfig(config, meta, meta.isFreshLoad());
    }

    /**
     * Static helper for per-game special-stage launch configuration. Applies
     * the shared team + cross-game settings, plus the S3K fresh-load branch
     * when applicable (mirroring {@link TraceReplaySessionBootstrap#prepareConfiguration}).
     *
     * <p>For S1: no special intro-skip behavior applies to special stages.
     *
     * @param config the configuration service to mutate
     * @param meta the trace metadata (provides recorded team and game id)
     * @param freshLoadSignal true when the trace requires a fresh level load
     *     (S3K only; false is the safe default until blue-spheres plan wires
     *     the real signal)
     */
    static void applyPerGameSpecialStageConfig(SonicConfigurationService config,
                                               TraceMetadata meta,
                                               boolean freshLoadSignal) {
        // Team: the recorded trace dictates the team.
        String main = meta.recordedMainCharacter();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                main == null || main.isBlank() ? "sonic" : main);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                String.join(",", meta.recordedSidekicks()));

        // Cross-game donation wasn't recorded; always force it off so
        // trace physics/visuals match the base ROM.
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);

        // S3K: apply the fresh-load intro-skip branch when a trace
        // requires a fresh level load. This mirrors the level-mode branch
        // in TraceReplaySessionBootstrap.prepareConfiguration. S1 has no
        // intro-skip behavior in special stages.
        if (freshLoadSignal && "s3k".equals(meta.game())) {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        }
    }

    private void finishSpecialStageLaunch() {
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            LOGGER.severe("SS trace launch callback fired after engine teardown; "
                    + "aborting for " + entry.dir());
            return;
        }
        finishSpecialStageLaunch(loop);
    }

    void finishSpecialStageLaunch(GameLoop loop) {
        GameplayModeContext failedContext = SessionManager.getCurrentGameplayMode();
        try {
            this.fixture = new LiveFixture(GameServices.playbackDebug(), loop);
            installSpecialStageHardwareTiming(fixture);
            // Mark active before entering so any entry-time rewind recording is
            // suppressed (GameLoop gates SS capture on active() == null).
            activeSession = this;
            SpecialStageProvider provider = GameServices.module().getSpecialStageProvider();
            Integer index = ssTrace.metadata().specialStageIndex();
            enterSpecialStageTrace(loop, provider, index != null ? index : 0);
        } catch (Exception e) {
            activeSession = null;
            try {
                if (fixture != null) {
                    fixture.closeHardwareTimingReplayRun();
                }
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            } finally {
                try {
                    TraceReplaySessionBootstrap.restoreGameplayConfig(
                            configSnapshot);
                } catch (RuntimeException restoreFailure) {
                    e.addSuppressed(restoreFailure);
                } finally {
                    try {
                        if (failedContext != null) {
                            failedContext.destroy();
                        }
                    } catch (RuntimeException contextFailure) {
                        e.addSuppressed(contextFailure);
                    } finally {
                        try {
                            loop.returnToMasterTitle();
                        } catch (RuntimeException returnFailure) {
                            e.addSuppressed(returnFailure);
                        }
                    }
                }
            }
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to finish SS trace launch for " + entry.dir(), e);
        }
    }

    static void enterSpecialStageTrace(
            GameLoop loop, SpecialStageProvider provider, int stageIndex) {
        loop.doEnterSpecialStage(provider, stageIndex, true,
                SpecialStageStartupPolicy.TRACE_ACCURATE);
        // Startup observations and external frame pacing are independent contracts.
        provider.setLagCompensation(0);
    }

    private void finishLaunchAfterGameBootstrap() {
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            LOGGER.severe("Trace launch callback fired after engine teardown; "
                    + "aborting for " + entry.dir());
            return;
        }
        PlaybackDebugManager playback = GameServices.playbackDebug();
        try {
            // The reusable deterministic-replay bootstrap (reset team,
            // load zone/act, swallow title cards, start playback,
            // apply start position + bootstrap, align counters, build +
            // attach the comparator) lives in TraceReplayDriver so the
            // headless trace-capture tool can reuse the exact ordering.
            // The fixture and sprite supplier come from this live launcher.
            this.fixture = new LiveFixture(playback, loop);
            TraceReplayDriver driver = new TraceReplayDriver(
                    trace, movie, fixture, loop, loop::getMainPlayableSprite);
            driver.start(entry.zone(), entry.act());

            int startIndex = driver.recordingStartFrame();
            int initialCursor = driver.initialCursor();
            this.comparator = driver.comparator();
            playback.setFrameObserver(comparator);
            this.cameraFocusController = new TraceCameraFocusController(
                    comparator,
                    loop::getMainPlayableSprite,
                    () -> {
                        var sprites = GameServices.spritesOrNull();
                        if (sprites == null) return null;
                        var sks = sprites.getSidekicks();
                        return sks.isEmpty() ? null : sks.get(0);
                    },
                    GameServices::camera,
                    GameServices.configuration(),
                    loop::isPaused);
            loop.setTraceCameraFocusController(cameraFocusController);
            this.overlay = new TraceHudOverlay(comparator,
                    () -> cameraFocusController.currentLabel(),
                    this::rewindStatusLabel);
            // TraceReplayDriver.start already attached the comparator as
            // the playback frame observer.
            installTraceRewindController(loop, startIndex, initialCursor);
            activeSession = this;
            TraceGhostHook.set(ghostHook);
        } catch (Exception e) {
            // Partial bootstrap: detach playback, restore the user's
            // gameplay config (we already mutated it in launch()), and
            // route back to the picker so the engine doesn't end up
            // orphaned in LEVEL mode with no session.
            playback.endSession();
            loop.setTraceCameraFocusController(null);
            this.cameraFocusController = null;
            activeSession = null;
            GameServices.audio().setRewindHistoryArmed(false);
            TraceGhostHook.clear(ghostHook);
            TraceReplaySessionBootstrap.restoreGameplayConfig(configSnapshot);
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to finish trace launch for " + entry.dir(), e);
            // loop is guaranteed non-null here — we early-returned at the
            // top of the method when it was null.
            loop.returnToMasterTitle();
        }
    }

    /**
     * Multi-segment run launch callback. Mirrors
     * {@link #finishLaunchAfterGameBootstrap} EXCEPT it never calls
     * {@link #installTraceRewindController}: the stepper/base-frame capture
     * assumes a single fixed segment and would silently rewind against the
     * wrong segment after a mid-run re-seek, so {@link #rewindController}
     * stays null for the whole run session (GameLoop's realtime-rewind
     * engagement then no-ops). Per-segment rewind support is a documented
     * follow-up, not silently-wrong behavior. Additionally installs the
     * {@link RunSegmentAdvancer} that walks {@link #runSegments} from the
     * all-mode GameLoop hook.
     */
    private void finishRunLaunch() {
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            LOGGER.severe("Trace run launch callback fired after engine teardown; "
                    + "aborting for " + entry.dir());
            return;
        }
        PlaybackDebugManager playback = GameServices.playbackDebug();
        try {
            TraceData seg0Trace = runSegments.get(0).trace();
            this.fixture = new LiveFixture(playback, loop);
            TraceReplayDriver driver = new TraceReplayDriver(
                    seg0Trace, movie, fixture, loop, loop::getMainPlayableSprite,
                    loop::toggleUserPause,
                    TraceRunReplayWalker.hasHardwareTimingStream(runSegments));
            driver.start(entry.zone(), entry.act());

            this.comparator = driver.comparator();
            this.runHardwareTiming =
                    new TraceRunReplayWalker.HardwareTimingCoordinator(
                            fixture,
                            TraceRunReplayWalker.hardwareTimingSegments(runSegments));
            playback.setFrameObserver(comparator);
            this.cameraFocusController = new TraceCameraFocusController(
                    comparator,
                    loop::getMainPlayableSprite,
                    () -> {
                        var sprites = GameServices.spritesOrNull();
                        if (sprites == null) return null;
                        var sks = sprites.getSidekicks();
                        return sks.isEmpty() ? null : sks.get(0);
                    },
                    GameServices::camera,
                    GameServices.configuration(),
                    loop::isPaused);
            loop.setTraceCameraFocusController(cameraFocusController);
            this.overlay = new TraceHudOverlay(comparator,
                    () -> cameraFocusController.currentLabel(),
                    this::rewindStatusLabel);
            // TraceReplayDriver.start already attached the comparator as
            // the playback frame observer. No installTraceRewindController
            // call here — see method javadoc.
            this.runAdvancer = new RunSegmentAdvancer(runSegments);
            activeSession = this;
            TraceGhostHook.set(ghostHook);
        } catch (Exception e) {
            playback.endSession();
            loop.setTraceCameraFocusController(null);
            this.cameraFocusController = null;
            activeSession = null;
            GameServices.audio().setRewindHistoryArmed(false);
            TraceGhostHook.clear(ghostHook);
            TraceReplaySessionBootstrap.restoreGameplayConfig(configSnapshot);
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to finish trace run launch for " + entry.dir(), e);
            loop.returnToMasterTitle();
        }
    }

    /** True for a special-stage visual session (parallel branch). */
    public boolean isSpecialStageSession() {
        return ssTrace != null;
    }

    /** True for a multi-segment run session (parallel branch). */
    public boolean isRunSession() {
        return runSegments != null;
    }

    /**
     * True when the current SS trace row is a lag frame, so GameLoop must not
     * run {@code updateSpecialStageInput()} / {@code ssProvider.update()} this
     * engine frame (the recorded input replaces live input; a lag row advances
     * nothing engine-side). Always false for level sessions.
     */
    public boolean shouldSkipCurrentSpecialStageTick() {
        return ssTrace != null && !fadeStarted
                && ssCursor >= 0 && ssCursor < ssTrace.frameCount()
                && ssTrace.getFrame(ssCursor).lag();
    }

    /**
     * Installs the recorded BK2 input for the current SS trace row as the
     * logical override, so GameLoop's {@code updateSpecialStageInput()} forwards
     * recorded input to the provider. Press-edge detection diffs against the
     * immediately preceding <em>physical</em> BK2 row (never the last stepped
     * row), matching the headless harness. No-op for level sessions.
     */
    public void applySpecialStageTraceInputIfActive(InputHandler input) {
        if (ssTrace == null || fadeStarted || input == null) {
            return;
        }
        Bk2FrameInput current = movie.getFrame(ssBk2FrameOffset + ssCursor);
        int prevIndex = ssBk2FrameOffset + ssCursor - 1;
        Bk2FrameInput previous = prevIndex >= 0 && prevIndex < movie.getFrameCount()
                ? movie.getFrame(prevIndex)
                : null; // fromBk2 synthesises a neutral prior row when null
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
    }

    /**
     * Clears the per-frame input override and advances the SS trace cursor by
     * one row (lag or not). Ends the session via the normal fade/teardown path
     * once the recorded stage-finished frame has played. No-op for level
     * sessions.
     */
    public void advanceSpecialStageTraceCursorIfActive(InputHandler input) {
        if (ssTrace == null) {
            return;
        }
        if (input != null) {
            input.clearLogicalOverride();
        }
        if (fadeStarted) {
            return;
        }
        if (ssCursor >= ssStageFinishedFrame) {
            if (fixture != null) {
                fixture.closeHardwareTimingReplayRun();
            }
            startFadeOut();
            return;
        }
        ssCursor++;
    }

    /**
     * Called from {@link GameLoop} each LEVEL tick while active. A run
     * session early-returns here: this completion-hold arms a fade the
     * moment ANY comparator completes, which for a run would end the whole
     * session ~1s after its FIRST segment boundary. {@link #runAdvancer}
     * alone owns run end, via {@link #runAdvanceTickIfActive} emitting
     * {@code END_OF_RUN}.
     */
    public void tick() {
        if (comparator == null || fadeStarted || isRunSession()) {
            return;
        }
        if (comparator.isComplete() && !completionArmed) {
            if (fixture != null) {
                fixture.closeHardwareTimingReplayRun();
            }
            completionArmed = true;
            completionHoldFrames = (int) Math.round(COMPLETION_HOLD_SECONDS * 60.0);
        }
        if (completionArmed) {
            if (completionHoldFrames > 0) {
                completionHoldFrames--;
            } else {
                startFadeOut();
            }
        }
    }

    /**
     * All-mode per-frame hook for a run session's segment-advance state
     * machine (called from GameLoop unconditionally, every mode, not just
     * LEVEL — {@link #tick()} alone goes blind the instant the mode leaves
     * LEVEL). No-op for a non-run session or once the fade has started. The
     * re-seek + comparator/HUD/camera rebind for a segment advance happen
     * here, between frames — never from inside a
     * {@link com.openggf.debug.playback.PlaybackDebugManager.PlaybackFrameObserver}
     * callback.
     */
    public void runAdvanceTickIfActive(GameMode mode, int cursorFrame) {
        if (runAdvancer == null || fadeStarted) {
            return;
        }
        RunSegmentAdvancer.Event event = runAdvancer.onFrame(mode, cursorFrame);
        if (event instanceof RunSegmentAdvancer.AdvanceAction action) {
            applyRunSegmentAdvance(action);
        } else if (event instanceof RunSegmentAdvancer.EndOfRun) {
            if (fixture != null) {
                fixture.closeHardwareTimingReplayRun();
            }
            startFadeOut();
        }
    }

    void installSpecialStageHardwareTiming(TraceReplayFixture replayFixture) {
        fixture = replayFixture;
        if (ssTrace != null
                && ssTrace.metadata().hasHardwareTimingStream()) {
            TraceReplaySessionBootstrap.installHardwareTimingReplay(
                    ssTrace.hardwareTimingSchedule(), replayFixture);
        }
    }

    /**
     * Selects represented hardware-timing authority before iteration
     * admission, so paused VBlank service and transition/title-card scans
     * cannot observe the previous row's latch.
     */
    public void prepareHardwareTimingForAdmission(GameMode mode) {
        if (fixture == null) {
            return;
        }
        if (fadeStarted) {
            fixture.enterHardwareTimingGap();
            return;
        }
        if (runAdvancer != null && runHardwareTiming != null) {
            if (mode == GameMode.LEVEL
                    || mode == GameMode.BONUS_STAGE) {
                runHardwareTiming.beginPlaybackFrame(
                        GameServices.playbackDebug().currentFrameOrThrow());
            } else if (mode == GameMode.SPECIAL_STAGE) {
                prepareRunSpecialStageHardwareTimingRow();
            } else {
                fixture.enterHardwareTimingGap();
            }
            return;
        }
        if (ssTrace != null) {
            if (mode == GameMode.SPECIAL_STAGE
                    && ssTrace.metadata().hasHardwareTimingStream()
                    && ssCursor >= 0
                    && ssCursor < ssTrace.frameCount()) {
                fixture.beginTraceRow(ssCursor, ssCursor);
            } else {
                fixture.enterHardwareTimingGap();
            }
            return;
        }
        if (trace != null
                && trace.metadata().hasHardwareTimingStream()
                && mode == GameMode.LEVEL
                && comparator != null) {
            int cursor = comparator.cursor();
            if (cursor >= 0 && cursor < trace.frameCount()) {
                fixture.beginTraceRow(
                        cursor, trace.getFrame(cursor).frame());
                return;
            }
        }
        fixture.enterHardwareTimingGap();
    }

    public void deactivateHardwareTimingForAdmission() {
        if (fixture != null) {
            fixture.enterHardwareTimingGap();
        }
    }

    /**
     * Latches metadata-only special-stage rows before their production update.
     * The run advancer changes segments after the update, so the first stage
     * frame anticipates the immediately following structural segment.
     */
    private void prepareRunSpecialStageHardwareTimingRow() {
        int segmentIndex = runAdvancer.currentSegmentIndex();
        if (!"special_stage".equals(
                runSegments.get(segmentIndex).segment().kind())
                && segmentIndex + 1 < runSegments.size()
                && "special_stage".equals(
                        runSegments.get(segmentIndex + 1).segment().kind())) {
            segmentIndex++;
        }
        if (!"special_stage".equals(
                runSegments.get(segmentIndex).segment().kind())) {
            fixture.enterHardwareTimingGap();
            return;
        }
        if (runSpecialTimingSegment != segmentIndex) {
            runSpecialTimingSegment = segmentIndex;
            runSpecialTimingRow = 0;
        }
        int rowCount =
                runSegments.get(segmentIndex).segment().traceFrameCount();
        if (runSpecialTimingRow < rowCount) {
            runHardwareTiming.beginSegmentRow(
                    segmentIndex, runSpecialTimingRow++);
        } else {
            fixture.enterHardwareTimingGap();
        }
    }

    /**
     * Applies a segment-advance {@link RunSegmentAdvancer.AdvanceAction}: re-seeks
     * playback to the next segment's BK2 offset and rebinds EVERYTHING that
     * captured the old comparator — the comparator field itself (read by
     * ghost rendering), the camera focus controller and HUD overlay (rebuilt
     * and re-registered, mirroring {@link #finishRunLaunch}), and the
     * playback frame observer. A stale binding would keep showing the
     * previous segment's counts.
     */
    private void applyRunSegmentAdvance(RunSegmentAdvancer.AdvanceAction action) {
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            return;
        }
        TraceRunReplayWalker.SegmentPlan segment = runSegments.get(action.nextSegmentIndex());
        // firstErrorCallback = loop::toggleUserPause, matching the live
        // TraceReplayDriver constructor's onComparatorPause (segment 0's
        // comparator, built via TraceReplayDriver.start, gets the same
        // callback) -- every segment must pause on its first divergence,
        // not just segment 0.
        this.comparator = new LiveTraceComparator(
                segment.trace(), ToleranceConfig.DEFAULT, 0,
                loop::getMainPlayableSprite, loop::toggleUserPause);
        this.cameraFocusController = new TraceCameraFocusController(
                comparator,
                loop::getMainPlayableSprite,
                () -> {
                    var sprites = GameServices.spritesOrNull();
                    if (sprites == null) return null;
                    var sks = sprites.getSidekicks();
                    return sks.isEmpty() ? null : sks.get(0);
                },
                GameServices::camera,
                GameServices.configuration(),
                loop::isPaused);
        loop.setTraceCameraFocusController(cameraFocusController);
        this.overlay = new TraceHudOverlay(comparator,
                () -> cameraFocusController.currentLabel(),
                this::rewindStatusLabel);
        GameServices.playbackDebug().setFrameObserver(
                comparator);
        GameServices.playbackDebug().startSession(movie, action.reseekOffset());
        completionArmed = false;
        completionHoldFrames = 0;
    }

    /**
     * Handles the held real-time rewind key in visual Trace Test Mode.
     *
     * @param rewindBlocked true while rewind engagement must be rejected: either
     *     the level is mid a special/bonus-stage/ending transition or a pending
     *     zone/act transition, OR a fade is in flight with a completion
     *     callback that has not yet run (see {@code GameLoop.isRewindBlocked()}).
     *     {@code currentGameMode} stays {@code GameMode.LEVEL} throughout both
     *     kinds of window -- a fade only flips the mode (or otherwise acts on
     *     its result) once its completion callback runs -- so a held Trace
     *     Test Mode rewind could otherwise keep walking backward through
     *     pre-transition/pre-fade history the same way
     *     {@link com.openggf.game.rewind.LiveRewindManager} could before it was
     *     gated on this same composite predicate (commit {@code 26fb7debd} for
     *     the transition-flag half; the fade half added later). Widens this
     *     method's existing "not applicable this frame" rejection, reusing its
     *     {@link #cleanupRealtimeRewindPresentation} teardown rather than
     *     needing a separate mid-hold cancel path. Note this is a STRICT
     *     SUPERSET of {@code GameLoop.isNonRewindableTransitionPending()} (the
     *     predicate that also freezes gameplay) -- the fade term must never be
     *     folded into that narrower predicate, since ROM gameplay keeps
     *     ticking during an ordinary callback-bearing fade even though rewind
     *     must still be rejected. See ssentry-rewind-report.md.
     * @return true when the frame was consumed by rewind and normal gameplay
     *         should not advance
     */
    public boolean handleRealtimeRewindInput(boolean rewindBlocked, InputHandler input) {
        if (rewindBlocked || input == null || rewindPlaybackController == null
                || rewindController == null || comparator == null || fadeStarted) {
            return !cleanupRealtimeRewindPresentation(
                    AudioPresentationPolicy.STOP_ALL_PRESENTATION);
        }
        if (realtimeReleasePending) {
            if (!cleanupRealtimeRewindPresentation(
                    pendingRealtimeReleasePolicy)) {
                return true;
            }
            rewindPlaybackController.play();
            syncVisualRewindCursors(true);
            return false;
        }
        int rewindKey = GameServices.configuration().getInt(SonicConfiguration.TRACE_REWIND_KEY);
        boolean held = input.isKeyDown(rewindKey);
        if (held) {
            if (!realtimeRewinding) {
                GameServices.audio().beginReverseAudioPresentation();
                beginReverseFadePresentation();
            }
            realtimeRewinding = true;
            rewindPlaybackController.rewind();
            rewindPlaybackController.tick();
            syncVisualRewindCursors(false);
            if (cameraFocusController != null) {
                cameraFocusController.syncDefaultCameraToCurrentPosition();
            }
            completionArmed = false;
            return true;
        }
        if (realtimeRewinding) {
            // Release lands a committed logical restore via
            // cleanupRealtimeRewindPresentation's commitDeferredAudioRestore()
            // before this cleanup runs, so the RESYNC_MUSIC variant's extra
            // music-stack pop is not needed and would incorrectly end an
            // override (e.g. invincibility) the just-restored state says
            // should still be active.
            if (!cleanupRealtimeRewindPresentation(
                    AudioPresentationPolicy.STOP_TRANSIENT_SFX)) {
                return true;
            }
            rewindPlaybackController.play();
            syncVisualRewindCursors(true);
        }
        return false;
    }

    /** Records a normal visual replay frame after {@link GameLoop} has advanced it. */
    public void recordExternalRewindFrame() {
        if (realtimeRewinding || rewindController == null || fadeStarted) {
            return;
        }
        rewindController.recordExternalStep();
    }

    /**
     * Records a transition-only replay frame, then reroots trace rewind so
     * realtime rewind cannot cross the just-applied level boundary.
     */
    public void recordExternalRewindFrameAtBoundary() {
        if (realtimeRewinding || rewindController == null || fadeStarted) {
            return;
        }
        rewindController.recordExternalStep();
        rewindController.resetBufferAtCurrentFrame();
    }

    /** Called when Esc is pressed during a LEVEL tick. */
    public void requestEarlyExit() {
        if (comparator == null || fadeStarted) {
            return;
        }
        startFadeOut();
    }

    public void render(PixelFontTextRenderer textRenderer) {
        if (overlay != null) {
            overlay.render(textRenderer);
        }
    }

    public void renderGhosts() {
        renderGhostsForLayer(Integer.MIN_VALUE, false, false);
    }

    public void renderGhostsForLayer(int bucket, boolean highPriority) {
        renderGhostsForLayer(bucket, highPriority, true);
    }

    private void renderGhostsForLayer(int bucket, boolean highPriority, boolean filterLayer) {
        if (comparator == null) {
            return;
        }
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            return;
        }
        var sprites = GameServices.spritesOrNull();
        if (filterLayer) {
            ghostRenderer.renderForLayer(
                    comparator.metadata(),
                    comparator.currentVisualFrame(),
                    loop.getMainPlayableSprite(),
                    sprites != null ? sprites.getRegisteredSidekicks() : java.util.List.of(),
                    bucket,
                    highPriority);
        } else {
            ghostRenderer.render(
                    comparator.metadata(),
                    comparator.currentVisualFrame(),
                    loop.getMainPlayableSprite(),
                    sprites != null ? sprites.getRegisteredSidekicks() : java.util.List.of());
        }
    }

    private void startFadeOut() {
        fadeStarted = true;
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode == null || gameplayMode.getFadeManager() == null) {
            throw new IllegalStateException(
                    "trace fade requires an active gameplay fade manager");
        }
        gameplayMode.getFadeManager().startFadeToBlack(this::teardown);
    }

    private void installTraceRewindController(GameLoop loop, int movieBaseFrame, int traceBaseFrame) {
        var gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode == null) {
            return;
        }
        this.rewindMovieBaseFrame = movieBaseFrame;
        this.rewindTraceBaseFrame = traceBaseFrame;
        this.rewindPlaybackController = gameplayMode.installPlaybackController(
                new OffsetMovieInputSource(movie, movieBaseFrame),
                new VisualTraceRewindStepper(
                        loop, movie, trace, fixture,
                        movieBaseFrame, traceBaseFrame),
                60);
        this.rewindController = gameplayMode.getRewindController();
        // A Trace Test Mode session is the whole reason held rewind exists
        // here; arm PCM recording for its lifetime so held rewind has audio
        // history, and disarm it in teardown()/the bootstrap failure path
        // below so no session leaves it running afterward.
        GameServices.audio().setRewindHistoryArmed(true);
    }

    private void syncVisualRewindCursors(boolean playing) {
        int relativeFrame = rewindController.currentFrame();
        GameServices.playbackDebug().seekSessionFrame(
                rewindMovieBaseFrame + relativeFrame,
                playing);
        comparator.seekForRewind(rewindTraceBaseFrame + relativeFrame);
    }

    private void beginReverseFadePresentation() {
        var fadeManager = GameServices.fadeOrNull();
        if (fadeManager != null) {
            fadeManager.beginReversePresentation();
        }
    }

    private void endReverseFadePresentationIfNeeded() {
        var fadeManager = GameServices.fadeOrNull();
        if (fadeManager != null && fadeManager.isReversePresentationActive()) {
            fadeManager.endReversePresentation();
        }
    }

    private boolean cleanupRealtimeRewindPresentation(
            AudioPresentationPolicy policy) {
        if (!realtimeRewinding && !realtimeReleasePending) {
            endReverseFadePresentationIfNeeded();
            return true;
        }
        boolean released;
        if (rewindController != null) {
            // Land the single deferred logical restore at the committed frame
            // before the presentation cleanup acts on backend logical state.
            rewindController.commitDeferredAudioRestore();
            released = GameServices.audio().afterRewindRestore(
                    rewindController.currentFrame(), policy);
        } else {
            released = GameServices.audio().endReverseAudioPresentation();
        }
        if (!released) {
            realtimeReleasePending = true;
            pendingRealtimeReleasePolicy = policy;
            return false;
        }
        realtimeRewinding = false;
        realtimeReleasePending = false;
        pendingRealtimeReleasePolicy = null;
        endReverseFadePresentationIfNeeded();
        return true;
    }

    private String rewindStatusLabel() {
        if (rewindController == null) {
            return null;
        }
        int rewindKey = GameServices.configuration().getInt(SonicConfiguration.TRACE_REWIND_KEY);
        String key = GlfwKeyNameResolver.nameOf(rewindKey);
        if (realtimeRewinding) {
            return "REWIND " + rewindController.currentFrame();
        }
        return "Hold " + key + " Rewind";
    }

    private void teardown() {
        teardownPending = true;
        retryPendingTeardown();
    }

    /**
     * Retries teardown at the all-mode frame owner. The active session remains
     * the retry host until audio release has committed.
     *
     * @return true when teardown owned and consumed this frame
     */
    public boolean retryPendingTeardown() {
        if (!teardownPending) {
            return false;
        }
        if (rewindController != null) {
            rewindController.commitDeferredAudioRestore();
            if (!GameServices.audio().afterRewindRestore(
                    rewindController.currentFrame(),
                    AudioPresentationPolicy.STOP_ALL_PRESENTATION)) {
                return true;
            }
        }
        activeSession = null;
        TraceGhostHook.clear(ghostHook);
        GameServices.audio().setRewindHistoryArmed(false);
        GameServices.playbackDebug().endSession();
        // Restore the user's gameplay-altering config before we
        // rebuild the master title. If the user re-launches the
        // picker immediately, they see their own preferences rather
        // than whatever the trace dictated.
        TraceReplaySessionBootstrap.restoreGameplayConfig(configSnapshot);
        GameLoop loop = Engine.currentGameLoop();
        if (loop != null) {
            loop.setTraceCameraFocusController(null);
            loop.returnToMasterTitle();
        }
        this.cameraFocusController = null;
        teardownPending = false;
        return true;
    }

    private static final class OffsetMovieInputSource implements InputSource {
        private final Bk2Movie movie;
        private final int baseFrame;

        private OffsetMovieInputSource(Bk2Movie movie, int baseFrame) {
            this.movie = movie;
            this.baseFrame = Math.max(0, baseFrame);
        }

        @Override
        public int frameCount() {
            return Math.max(1, movie.getFrameCount() - baseFrame + 1);
        }

        @Override
        public Bk2FrameInput read(int frame) {
            int movieFrame = baseFrame + Math.max(0, frame - 1);
            return movie.getFrame(movieFrame);
        }
    }

    private static final class VisualTraceRewindStepper implements RewindSeekAwareEngineStepper {
        private final GameLoop loop;
        private final Bk2Movie movie;
        private final TraceData trace;
        private final TraceReplayFixture fixture;
        private final int movieBaseFrame;
        private final int traceBaseFrame;
        private boolean pendingForcedJumpPress;

        private VisualTraceRewindStepper(
                GameLoop loop,
                Bk2Movie movie,
                TraceData trace,
                TraceReplayFixture fixture,
                int movieBaseFrame,
                int traceBaseFrame) {
            this.loop = loop;
            this.movie = movie;
            this.trace = trace;
            this.fixture = fixture;
            this.movieBaseFrame = movieBaseFrame;
            this.traceBaseFrame = traceBaseFrame;
        }

        @Override
        public LevelFrameResult step(Bk2FrameInput inputs) {
            int relative = Math.max(0, inputs.frameIndex() - movieBaseFrame + 1);
            int traceIndex = traceBaseFrame + relative - 1;
            if (traceIndex >= 0 && traceIndex < trace.frameCount()) {
                fixture.beginTraceRow(
                        traceIndex, trace.getFrame(traceIndex).frame());
            } else {
                fixture.enterHardwareTimingGap();
            }
            TraceExecutionPhase phase = executionPhase(traceIndex);
            if (phase == TraceExecutionPhase.ADVANCE_ONLY) {
                publishPlaybackInput(inputs);
                return LevelFrameResult.GAMEPLAY_FRAME;
            }
            if (phase == TraceExecutionPhase.VBLANK_ONLY
                    || phase == TraceExecutionPhase.PLAYABLE_ANIMATION_ONLY) {
                var level = GameServices.levelOrNull();
                if (level != null && level.getObjectManager() != null) {
                    level.getObjectManager().advanceVblaCounter();
                }
                if (phase == TraceExecutionPhase.PLAYABLE_ANIMATION_ONLY) {
                    var sprites = GameServices.spritesOrNull();
                    if (sprites != null) {
                        sprites.advancePlayableSlotPrefix();
                    }
                }
                return LevelFrameResult.GAMEPLAY_FRAME;
            }

            if (phase == TraceExecutionPhase.FULL_LEVEL_FRAME_WITH_SIDEKICK_ANIMATION_HELD) {
                var sidekickSprites = GameServices.spritesOrNull();
                if (sidekickSprites != null && !sidekickSprites.getSidekicks().isEmpty()) {
                    sidekickSprites.getSidekicks().getFirst()
                            .getAnimationManager().suppressNextUpdate();
                }
            }

            var sprites = GameServices.spritesOrNull();
            var level = GameServices.levelOrNull();
            var camera = GameServices.cameraOrNull();
            if (sprites == null || level == null || camera == null) {
                return LevelFrameResult.PAUSED;
            }

            FrameAdmission admission = LevelFrameStep.admit(
                    LevelFrameContext.from(SessionManager.getCurrentGameplayMode()),
                    level,
                    inputs.p1StartPressed());
            if (!admission.runsGameplay()) {
                return admission.result();
            }
            publishPlaybackInput(inputs);
            sprites.setPlaybackInputSuppressed(true);
            sprites.publishHeldInputForLevelEvents(loop.getInputHandler());
            LevelFrameResult result = LevelFrameStep.execute(
                    LevelFrameContext.from(SessionManager.getCurrentGameplayMode()),
                    level, camera, () -> sprites.update(loop.getInputHandler()));
            if (result == LevelFrameResult.GAMEPLAY_FRAME) {
                pendingForcedJumpPress = false;
            }
            return result;
        }

        @Override
        public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
            AbstractPlayableSprite player = loop.getMainPlayableSprite();
            if (player != null && inputAtFrame != null) {
                player.setForcedInputMask(inputAtFrame.p1InputMask());
                pendingForcedJumpPress = player.isForcedJumpPress();
            } else {
                pendingForcedJumpPress = false;
            }
        }

        private void publishPlaybackInput(Bk2FrameInput inputs) {
            AbstractPlayableSprite player = loop.getMainPlayableSprite();
            if (player == null) {
                return;
            }
            player.setForcedInputMask(inputs.p1InputMask());
            int previousAction = inputs.frameIndex() > 0
                    ? movie.getFrame(inputs.frameIndex() - 1).p1ActionMask()
                    : 0;
            int pressed = (inputs.p1ActionMask() ^ previousAction) & inputs.p1ActionMask();
            if (pressed != 0) {
                pendingForcedJumpPress = true;
            }
            player.setForcedJumpPress(pendingForcedJumpPress);

            var sprites = GameServices.spritesOrNull();
            if (sprites != null) {
                sprites.setPlaybackInputSuppressed(true);
            }
        }

        private TraceExecutionPhase executionPhase(int traceIndex) {
            if (traceIndex < 0 || traceIndex >= trace.frameCount()) {
                return TraceExecutionPhase.FULL_LEVEL_FRAME;
            }
            TraceFrame current = trace.getFrame(traceIndex);
            TraceFrame previous = traceIndex > 0 ? trace.getFrame(traceIndex - 1) : null;
            TraceExecutionPhase phase =
                    TraceReplayBootstrap.phaseForReplay(trace, previous, current);
            return phase;
        }
    }

    private static MasterTitleScreen.GameEntry resolveGameEntry(String gameId) {
        return MasterTitleScreen.GameEntry.fromGameId(gameId);
    }

    static void clearLaunchSessionOverridesBeforeTraceSnapshot(SonicConfigurationService config) {
        config.clearSessionOverrides();
        config.resolveDisplayAspect();
    }

    /** Thin live-engine implementation of {@link TraceReplayFixture}. */
    private static final class LiveFixture implements TraceReplayFixture {
        private final PlaybackDebugManager playback;
        private final GameLoop gameLoop;
        private HardwareTimingReplayPort hardwareTimingReplayPort;
        private TraceHardwareTimingBoundaryObserver hardwareTimingObserver;
        private GameplayModeContext hardwareTimingGameplayMode;
        private boolean hardwareTimingReplayClosed;

        private LiveFixture(PlaybackDebugManager playback, GameLoop gameLoop) {
            this.playback = playback;
            this.gameLoop = gameLoop;
        }

        @Override
        public AbstractPlayableSprite sprite() {
            return gameLoop.getMainPlayableSprite();
        }

        @Override
        public GameplayModeContext gameplayMode() {
            return SessionManager.getCurrentGameplayMode();
        }

        @Override
        public void installHardwareTimingReplay(HardwareTimingReplayPort replayPort) {
            if (hardwareTimingReplayPort != null) {
                throw new IllegalStateException("hardware timing replay is already installed");
            }
            hardwareTimingReplayPort = replayPort;
            hardwareTimingObserver = new TraceHardwareTimingBoundaryObserver(replayPort);
            hardwareTimingGameplayMode = gameplayMode();
            hardwareTimingReplayClosed = false;
            hardwareTimingGameplayMode.getRewindRegistry().register(replayPort);
            hardwareTimingGameplayMode.setHardwareTimingBoundaryObserver(
                    hardwareTimingObserver);
            hardwareTimingGameplayMode.setHardwareTimingReplayCloseHook(
                    this::closeHardwareTimingReplayRun);
        }

        @Override
        public void beginTraceRow(int traceIndex, int rawFrame) {
            if (hardwareTimingObserver != null) {
                hardwareTimingObserver.beginRawFrame(rawFrame);
            }
        }

        @Override
        public void enterHardwareTimingGap() {
            if (hardwareTimingObserver != null) {
                hardwareTimingObserver.enterUnrepresentedGap();
            }
        }

        @Override
        public void verifyHardwareTimingSegmentEdges() {
            if (hardwareTimingReplayPort != null) {
                hardwareTimingReplayPort.verifySegmentEdges();
            }
        }

        @Override
        public void handoffHardwareTimingReplay(HardwareTimingSchedule nextSchedule) {
            if (hardwareTimingReplayPort != null) {
                hardwareTimingReplayPort.handoffTo(nextSchedule);
            }
        }

        @Override
        public void closeHardwareTimingReplayRun() {
            if (hardwareTimingReplayPort == null || hardwareTimingReplayClosed) {
                return;
            }
            hardwareTimingReplayClosed = true;
            try {
                hardwareTimingReplayPort.verifyRunComplete();
            } finally {
                if (hardwareTimingGameplayMode != null) {
                    hardwareTimingGameplayMode.setHardwareTimingBoundaryObserver(
                            null);
                    if (hardwareTimingGameplayMode.getRewindRegistry() != null) {
                        hardwareTimingGameplayMode.getRewindRegistry()
                                .deregister(HardwareTimingReplayPort.REWIND_KEY);
                    }
                    hardwareTimingGameplayMode.clearHardwareTimingReplayCloseHook();
                }
                hardwareTimingObserver = null;
                hardwareTimingGameplayMode = null;
            }
        }

        @Override
        public int stepFrameFromRecording() {
            Bk2FrameInput frame = playback.currentFrameOrThrow();
            int mask = toReplayValidationMask(frame);
            gameLoop.step();
            return mask;
        }

        @Override
        public int skipFrameFromRecording() {
            Bk2FrameInput frame = playback.currentFrameOrThrow();
            int mask = toReplayValidationMask(frame);
            playback.advanceCurrentFrameWithoutGameplay();
            return mask;
        }

        @Override
        public void advancePlayableAnimationsOnly() {
            GameServices.sprites().advancePlayableSlotPrefix();
        }

        @Override
        public void suppressFirstSidekickAnimationOnce() {
            var sprites = GameServices.spritesOrNull();
            if (sprites != null && !sprites.getSidekicks().isEmpty()) {
                sprites.getSidekicks().getFirst().getAnimationManager().suppressNextUpdate();
            }
        }

        @Override
        public int consumeRecordingFrameInputOnly() {
            Bk2FrameInput frame = playback.currentFrameOrThrow();
            int mask = toReplayValidationMask(frame);
            playback.advanceCurrentFrameWithoutGameplay();
            return mask;
        }

        @Override
        public void advanceRecordingCursor(int frameCount) {
            for (int i = 0; i < frameCount; i++) {
                playback.advanceCurrentFrameWithoutGameplay();
            }
        }

        private static int toReplayValidationMask(Bk2FrameInput frame) {
            int mask = frame.p1InputMask();
            if (frame.p1ActionMask() != 0) {
                mask |= AbstractPlayableSprite.INPUT_JUMP;
            }
            return mask;
        }
    }
}
