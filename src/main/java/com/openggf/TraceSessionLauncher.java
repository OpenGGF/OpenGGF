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
import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.game.rewind.InputSource;
import com.openggf.game.rewind.PlaybackController;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindSeekAwareEngineStepper;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.graphics.FadeManager;
import com.openggf.sprites.ghost.GhostTraceRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.testmode.TraceCameraFocusController;
import com.openggf.testmode.TraceHudOverlay;
import com.openggf.testmode.SpecialStageTraceHudOverlay;
import com.openggf.testmode.TraceSessionOverlay;
import com.openggf.testmode.TraceLaunchStatus;
import com.openggf.testmode.TraceRunFailureStatus;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.catalog.TraceCatalog;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.replay.TraceReplayDriver;
import com.openggf.trace.replay.TraceGhostHook;
import com.openggf.trace.replay.TraceReplayFixture;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.trace.replay.TraceReplayRowPolicy;
import com.openggf.trace.replay.VisualTraceLaunchPhase;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.DestinationAdmissionReceipt;
import com.openggf.trace.replay.runs.RunBoundarySignal;
import com.openggf.trace.replay.runs.RunLevelLoadCause;
import com.openggf.trace.replay.runs.RunLevelLoadTracker;
import com.openggf.trace.replay.runs.RunPlaybackObservation;
import com.openggf.trace.replay.runs.TraceRunPlaybackCoordinator;
import com.openggf.trace.replay.runs.TraceRunBoundaryComparator;
import com.openggf.trace.replay.runs.TraceRunDynamicArtGapJournal;
import com.openggf.trace.replay.runs.TraceRunSpecialStageRows;
import com.openggf.trace.replay.runs.TraceRunSpecialStageRows.SpecialStageRowAdmission;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import com.openggf.trace.timing.HardwareTimingSchedule;
import com.openggf.trace.timing.TraceHardwareTimingBoundaryObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Drives a Trace Test Mode playback session. The picker calls
 * {@link #launch(TraceEntry)}; the launcher then asynchronously:
 * <ol>
 *   <li>asks {@link GameLoop#launchGameByEntry} to run the same
 *       master-title exit path as a user selecting the game,</li>
 *   <li>loads the trace's zone/act and shows the complete production title
 *       card without advancing replay, then</li>
 *   <li>adopts that same gameplay context, starts programmatic BK2 playback,
 *       applies {@link TraceReplaySessionBootstrap}, and attaches a
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

    private final VisualTraceLaunchPhase launchPhase =
            new VisualTraceLaunchPhase();

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
    private final TraceRunSpecialStageRows ssTrace;
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
     * session drives {@link #runCoordinator} from the all-mode GameLoop hook
     * instead of the LEVEL-only {@link #tick()} completion-hold. The legacy
     * {@link #runAdvancer} field remains only for compatibility tests and
     * sessions created by the older constructor seam.
     */
    private List<TraceRunReplayWalker.SegmentPlan> runSegments;
    private RunSegmentAdvancer runAdvancer;
    private TraceRunPlaybackCoordinator runCoordinator;
    private TraceRunReplayWalker.BoundaryProbe runBoundaryProbe;
    private final List<TraceRunPlaybackCoordinator.Action>
            runCoordinatorTranscript = new ArrayList<>();
    private long runAdmittedStepOrdinal;
    private long runLevelLoadGeneration;
    private int runForwardedBoundarySegment = -1;
    private TraceRunSpecialStageRows runSpecialRows;
    private int runSpecialLocalRow;
    private int runSpecialVblankBefore;
    private boolean runSpecialInputApplied;
    private TraceRunReplayWalker.TerminalMovieTailPlan runTerminalTail;
    private int runTerminalTailRow;
    private boolean runTerminalInputApplied;
    private InputHandler runOwnedInputHandler;
    private boolean runLevelLoadedDuringSourceProduction;
    private RunPlaybackObservation runProductionOwnerObservation;
    private TraceRunReplayWalker.HardwareTimingCoordinator runHardwareTiming;
    private TraceRunReplayWalker.DynamicArtSegmentController runDynamicArtSegments;
    private GameplayModeContext dynamicArtSegmentGameplayMode;
    private TraceRunDynamicArtGapJournal runDynamicArtGapJournal;
    private int runClosingDynamicArtSegment = -1;
    private int runSpecialTimingSegment = -1;
    private int runSpecialTimingRow;
    private int runSpecialDynamicArtPendingRow = -1;
    private long runSpecialDynamicArtTargetGeneration = -1;
    private TraceRunReplayWalker.DynamicArtSegmentComparison
            runSpecialDynamicArtComparison;
    private boolean runSpecialDynamicArtSegmentAnticipated;
    /**
     * Snapshot of the user's gameplay-altering config taken before
     * {@link TraceReplaySessionBootstrap#prepareConfiguration} ran.
     * Restored in {@link #teardown()} so the picker returns to the
     * user's own team / cross-game / S3K_SKIP_INTROS preferences.
     */
    private final TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot;
    private LiveTraceComparator comparator;
    private TraceCameraFocusController cameraFocusController;
    private TraceSessionOverlay overlay;
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
    private boolean specialStageTerminalExitPending;
    private boolean teardownPending;
    private boolean productionIterationInProgress;
    private boolean runEndPending;
    private boolean runGapEntryPending;
    private boolean runSpecialVerificationPending;
    private RunSegmentAdvancer.AdvanceAction runAdvancePending;
    private boolean completionStartPending;
    private long dynamicArtDeliverySerialBeforeIteration;
    private DynamicArtDiagnosticsSnapshot dynamicArtSnapshotBeforeIteration;
    private LiveTraceComparator productionIterationComparator;

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
        this(entry, movie, TraceRunSpecialStageRows.forS2(ssTrace), configSnapshot);
    }

    TraceSessionLauncher(TraceEntry entry, Bk2Movie movie,
                         TraceRunSpecialStageRows ssTrace,
                         TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot) {
        this.entry = entry;
        this.trace = null;
        this.movie = movie;
        this.configSnapshot = configSnapshot;
        this.ssTrace = ssTrace;
        this.ssBk2FrameOffset = entry.metadata().bk2FrameOffset();
        this.ssStageFinishedFrame =
                ssTrace.terminalRow().orElse(ssTrace.rowCount() - 1);
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
            TraceLaunchStatus.record(entry, "Engine is not initialised");
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
            TraceLaunchStatus.record(entry,
                    "A master-title fade is already in progress");
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
        if (isSpecialStageProfile(entry.metadata().traceProfile())) {
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
            // This context owns both title-card presentation and replay. Its
            // live timing epoch is converted in place after control release.
            SessionManager.armNextGameplayAdmissionPolicy(
                    HardwareReadinessAdmissionPolicy.LIVE);
            loop.launchGameByEntry(
                    resolveGameEntry(entry.gameId()),
                    session::finishLaunchAfterGameBootstrap);
            return true;
        } catch (Exception e) {
            TraceLaunchStatus.record(entry, e);
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
     * segment list the shared {@link TraceRunPlaybackCoordinator} walks.
     */
    private static boolean launchRun(TraceEntry entry, GameLoop loop,
            TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot) {
        boolean configMutated = false;
        try {
            TraceCatalog.PreparedRunLaunch prepared =
                    TraceCatalog.prepareRunLaunch(entry);
            List<TraceRunReplayWalker.SegmentPlan> segments = prepared.segments();
            TraceData seg0Trace = segments.get(0).trace();
            Bk2Movie movie = prepared.movie();
            TraceSessionLauncher session =
                    new TraceSessionLauncher(entry, movie, segments, configSnapshot);
            TraceReplaySessionBootstrap.prepareConfiguration(seg0Trace, seg0Trace.metadata());
            configMutated = true;
            // This context owns both title-card presentation and replay. Its
            // live timing epoch is converted in place after control release.
            SessionManager.armNextGameplayAdmissionPolicy(
                    HardwareReadinessAdmissionPolicy.LIVE);
            loop.launchGameByEntry(
                    resolveGameEntry(entry.gameId()),
                    session::finishRunLaunch);
            return true;
        } catch (Exception e) {
            TraceLaunchStatus.record(entry, e);
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
            TraceRunSpecialStageRows ssTrace = TraceRunSpecialStageRows.load(
                    entry.metadata().traceProfile(), entry.dir());
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
            TraceLaunchStatus.record(entry, e);
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
        armSpecialStageAdmissionPolicy(TraceRunSpecialStageRows.forS2(trace));
    }

    static void armSpecialStageAdmissionPolicy(TraceRunSpecialStageRows trace) {
        SessionManager.armNextGameplayAdmissionPolicy(
                trace.hardwareTimingSchedule().hasRecordedInput()
                        ? HardwareReadinessAdmissionPolicy.RECORDED
                        : HardwareReadinessAdmissionPolicy.LIVE);
    }

    private static boolean isSpecialStageProfile(String profile) {
        return "s1_special_stage".equals(profile)
                || "s2_special_stage".equals(profile)
                || "s3k_special_stage".equals(profile);
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
            abortLaunchFailure(
                    null,
                    "special-stage trace callback followed engine teardown",
                    null,
                    "Failed to abort SS trace after engine teardown");
            return;
        }
        finishSpecialStageLaunch(loop);
    }

    void finishSpecialStageLaunch(GameLoop loop) {
        try {
            this.fixture = new LiveFixture(GameServices.playbackDebug(), loop);
            installSpecialStageHardwareTiming(fixture);
            configureStandaloneSpecialStageAudio();
            this.overlay = new SpecialStageTraceHudOverlay(
                    () -> ssTrace.metadata().traceProfile()
                            .replace('_', ' ').toUpperCase(java.util.Locale.ROOT),
                    () -> ssCursor,
                    ssTrace::rowCount,
                    this::isCurrentSpecialStageRowLagged);
            // Mark active before entering so any entry-time rewind recording is
            // suppressed (GameLoop gates SS capture on active() == null).
            activeSession = this;
            SpecialStageProvider provider = GameServices.module().getSpecialStageProvider();
            Integer index = ssTrace.metadata().specialStageIndex();
            enterSpecialStageTrace(loop, provider, index != null ? index : 0);
            launchPhase.markActive();
        } catch (Throwable launchFailure) {
            abortLaunchFailure(
                    launchFailure,
                    "special-stage visual trace launch failed",
                    loop,
                    "Failed to finish SS trace launch for " + entry.dir());
        }
    }

    void configureStandaloneSpecialStageAudio() throws IOException {
        var audio = GameServices.audio();
        var profile = GameServices.module().getAudioProfile();
        audio.setAudioProfile(profile);
        var rom = GameServices.rom().getRom();
        if (rom != null) {
            audio.setRom(rom);
        }
        audio.setSoundMap(profile.getSoundMap());
        audio.resetRingSound();
    }

    private boolean isCurrentSpecialStageRowLagged() {
        return ssCursor >= 0 && ssCursor < ssTrace.rowCount()
                && !ssTrace.admission(ssCursor).executeGameplay();
    }

    interface TitleCardPresentation {
        void prepareLevel() throws Exception;

        void enterTitleCard() throws Exception;
    }

    /** Starts the visible prelude before any replay observer is installed. */
    void beginTitleCardPresentation(TitleCardPresentation presentation)
            throws Exception {
        Objects.requireNonNull(presentation, "presentation");
        presentation.prepareLevel();
        launchPhase.beginTitleCardPresentation();
        activeSession = this;
        presentation.enterTitleCard();
    }

    boolean isPresentingTitleCard() {
        return launchPhase.isPresentingTitleCard();
    }

    static boolean claimTitleCardControlReleaseBarrierIfActive() {
        TraceSessionLauncher session = active();
        return session != null
                && session.launchPhase.claimTitleCardControlRelease();
    }

    boolean beginReplayBootstrapAfterTitleCardIfReady(GameMode mode) {
        return launchPhase.beginReplayBootstrapIfReady(mode);
    }

    private void beginTitleCardPresentationAfterGameBootstrap() {
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            abortLaunchFailure(
                    null,
                    "visual trace title-card callback followed engine teardown",
                    null,
                    "Failed to abort trace after engine teardown");
            return;
        }
        try {
            beginTitleCardPresentation(new TitleCardPresentation() {
                @Override
                public void prepareLevel() throws Exception {
                    TraceReplaySessionBootstrap.resetLevelSubsystemsForReplay();
                    GameplayTeamBootstrap.registerActiveTeam(
                            GameServices.module(),
                            GameServices.sprites(),
                            GameServices.configuration());
                    GameServices.level().loadZoneAndAct(entry.zone(), entry.act());
                    loop.setGameMode(GameMode.LEVEL);
                    GameServices.level().consumeTitleCardRequest();
                    GameServices.level().consumeInLevelTitleCardRequest();
                }

                @Override
                public void enterTitleCard() {
                    loop.enterTitleCard(entry.zone(), entry.act());
                }
            });
        } catch (Throwable launchFailure) {
            abortLaunchFailure(
                    launchFailure,
                    "visual trace title-card presentation failed",
                    loop,
                    "Failed to present trace title card for " + entry.dir());
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
        beginTitleCardPresentationAfterGameBootstrap();
    }

    private void finishStandaloneReplayLaunch(GameLoop loop) {
        PlaybackDebugManager playback = GameServices.playbackDebug();
        try {
            // TraceReplayDriver owns shared playback/comparator activation;
            // this visual entry point adopts the already loaded production
            // level instead of running its reset/load entry point.
            this.fixture = new LiveFixture(playback, loop);
            installDynamicArtSegments(fixture.gameplayMode());
            TraceReplayDriver driver = new TraceReplayDriver(
                    trace, movie, fixture, loop, loop::getMainPlayableSprite);
            driver.startPreparedLevel();

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
            // TraceReplayDriver.startPreparedLevel already attached the
            // comparator as the playback frame observer.
            installTraceRewindController(loop, startIndex, initialCursor);
            activeSession = this;
            TraceGhostHook.set(ghostHook);
            launchPhase.markActive();
        } catch (Throwable launchFailure) {
            abortLaunchFailure(
                    launchFailure,
                    "visual trace launch failed",
                    loop,
                    "Failed to finish trace launch for " + entry.dir());
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
     * shared run coordinator that walks {@link #runSegments} from the
     * all-mode GameLoop hook.
     */
    private void finishRunLaunch() {
        beginTitleCardPresentationAfterGameBootstrap();
    }

    private void finishRunReplayLaunch(GameLoop loop) {
        PlaybackDebugManager playback = GameServices.playbackDebug();
        try {
            TraceData seg0Trace = runSegments.get(0).trace();
            this.fixture = new LiveFixture(playback, loop);
            installDynamicArtSegments(fixture.gameplayMode());
            TraceReplayDriver driver = new TraceReplayDriver(
                    seg0Trace, movie, fixture, loop, loop::getMainPlayableSprite,
                    loop::toggleUserPause,
                    TraceRunReplayWalker.hasHardwareTimingStream(runSegments));
            driver.startPreparedLevel();

            this.comparator = driver.comparator();
            this.runHardwareTiming =
                    new TraceRunReplayWalker.HardwareTimingCoordinator(
                            fixture,
                            TraceRunReplayWalker.hardwareTimingSegments(runSegments));
            this.runCoordinator = new TraceRunPlaybackCoordinator(
                    entry.runManifest(),
                    GameServices.module().getTracePlaybackProfile(),
                    movie.getFrameCount());
            this.runBoundaryProbe = createRunBoundaryProbe(loop);
            runBoundaryProbe.setDelegate(comparator);
            playback.setFrameObserver(runBoundaryProbe);
            runCoordinatorTranscript.addAll(runCoordinator.activateInitialLevel(
                    captureRunObservation(loop.getCurrentGameMode(), 0, false)));
            fixture.gameplayMode().runLevelLoads()
                    .prime(GameServices.level().getCurrentLevel());
            armCurrentRunBoundary();
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
            // TraceReplayDriver.startPreparedLevel already attached the
            // comparator as the playback frame observer. No
            // installTraceRewindController call here — see method javadoc.
            activeSession = this;
            TraceGhostHook.set(ghostHook);
            launchPhase.markActive();
        } catch (Throwable launchFailure) {
            abortLaunchFailure(
                    launchFailure,
                    "visual trace run launch failed",
                    loop,
                    "Failed to finish trace run launch for " + entry.dir());
        }
    }

    private TraceRunReplayWalker.BoundaryProbe createRunBoundaryProbe(GameLoop loop) {
        return new TraceRunReplayWalker.BoundaryProbe(
                new TraceRunReplayWalker.EngineHooks() {
                    @Override
                    public int currentBk2Frame() {
                        return GameServices.playbackDebug().getCursorFrame();
                    }

                    @Override
                    public BonusStageType peekBonusRequest() {
                        return GameServices.level().getTransitions()
                                .peekBonusStageRequest();
                    }

                    @Override
                    public boolean isSpecialStageRequested() {
                        return GameServices.level().getTransitions()
                                .isSpecialStageRequested();
                    }

                    @Override
                    public GameMode currentMode() {
                        return loop.getCurrentGameMode();
                    }
                });
    }

    private void armCurrentRunBoundary() {
        if (runBoundaryProbe == null || runCoordinator == null) {
            return;
        }
        int index = runCoordinator.currentSegmentIndex();
        runBoundaryProbe.arm(index >= 0 && index < runSegments.size()
                ? runSegments.get(index).exitBoundary() : null);
        runForwardedBoundarySegment = -1;
    }

    private RunPlaybackObservation captureRunObservation(
            GameMode mode, int rowsConsumed, boolean lagOnlyContinuation) {
        var levelManager = GameServices.level();
        RunPlaybackObservation.LevelIdentity levelIdentity =
                levelManager.getCurrentLevel() != null
                        ? new RunPlaybackObservation.LevelIdentity(
                                runLevelLoadGeneration,
                                levelManager.getCurrentZone(),
                                levelManager.getRomZoneId(),
                                levelManager.getCurrentAct())
                        : null;
        RunPlaybackObservation.BonusIdentity bonusIdentity = null;
        var bonus = GameServices.bonusStageOrNull();
        if (mode == GameMode.BONUS_STAGE && bonus != null
                && bonus.getActiveType() != BonusStageType.NONE) {
            bonusIdentity = new RunPlaybackObservation.BonusIdentity(
                    levelManager.getRomZoneId(), levelManager.getCurrentAct(),
                    bonus.getActiveType());
        }
        Integer specialStageIndex = mode == GameMode.SPECIAL_STAGE
                ? getActiveSpecialStageIndex() : null;
        long dynamicGeneration = GameServices.captureDynamicArtDiagnostics()
                .segmentGeneration();
        return new RunPlaybackObservation(
                mode,
                Math.max(0, GameServices.playbackDebug().getCursorFrame()),
                runAdmittedStepOrdinal,
                levelIdentity,
                levelManager.isTitleCardRequested(),
                bonusIdentity,
                specialStageIndex,
                productionIterationInProgress,
                isCurrentRunSegmentExhausted(),
                rowsConsumed,
                lagOnlyContinuation,
                Math.max(0, currentRunSegmentIndex()),
                dynamicGeneration);
    }

    private Integer getActiveSpecialStageIndex() {
        GameLoop loop = Engine.currentGameLoop();
        SpecialStageProvider provider = loop != null
                ? loop.getActiveSpecialStageProvider() : null;
        return provider != null ? provider.getCurrentStage() : null;
    }

    private int currentRunSegmentIndex() {
        if (runCoordinator != null) {
            return runCoordinator.currentSegmentIndex();
        }
        return runAdvancer != null ? runAdvancer.currentSegmentIndex() : -1;
    }

    private boolean isCurrentRunSegmentExhausted() {
        int index = currentRunSegmentIndex();
        if (index < 0 || index >= runSegments.size()) {
            return false;
        }
        if ("special_stage".equals(runSegments.get(index).segment().kind())) {
            return runSpecialRows != null
                    && runSpecialLocalRow >= runSpecialRows.rowCount();
        }
        return comparator != null && comparator.isComplete();
    }

    private void abortLaunchFailure(
            Throwable primary,
            String reason,
            GameLoop fallbackLoop,
            String logMessage) {
        Throwable failure = abortIncompleteSession(primary, reason, fallbackLoop);
        if (entry != null) {
            if (failure != null) {
                TraceLaunchStatus.record(entry, failure);
            } else {
                TraceLaunchStatus.record(entry, reason);
            }
        }
        if (failure instanceof Error fatal) {
            throw fatal;
        }
        if (failure != null) {
            LOGGER.log(java.util.logging.Level.SEVERE, logMessage, failure);
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
        if (fadeStarted) {
            return ssTrace != null && (specialStageTerminalExitPending
                    || teardownPending);
        }
        if (ssTrace != null) {
            return ssCursor >= 0 && ssCursor < ssTrace.rowCount()
                    && !ssTrace.admission(ssCursor).executeGameplay();
        }
        return currentRunSpecialAdmission()
                .map(admission -> !admission.executeGameplay())
                .orElse(false);
    }

    /** PLC lifecycle represented by a skipped visual special-stage row. */
    public Optional<com.openggf.game.resources.PlcLifecyclePhase>
            skippedSpecialStagePlcPhase() {
        if (ssTrace != null && shouldSkipCurrentSpecialStageTick()) {
            return Optional.of(com.openggf.game.resources.PlcLifecyclePhase.LAG);
        }
        return currentRunSpecialAdmission()
                .flatMap(SpecialStageRowAdmission::syntheticPlcPhase);
    }

    /**
     * Installs the recorded BK2 input for the current SS trace row as the
     * logical override, so GameLoop's {@code updateSpecialStageInput()} forwards
     * recorded input to the provider. Press-edge detection diffs against the
     * immediately preceding <em>physical</em> BK2 row (never the last stepped
     * row), matching the headless harness. No-op for level sessions.
     */
    public void applySpecialStageTraceInputIfActive(InputHandler input) {
        if (fadeStarted || input == null) {
            return;
        }
        int physicalRow;
        if (ssTrace != null) {
            physicalRow = ssBk2FrameOffset + ssCursor;
        } else if (currentRunSpecialAdmission().isPresent()) {
            physicalRow = runSegments.get(currentRunSegmentIndex()).segment()
                    .bk2FrameOffset() + runSpecialLocalRow;
            var objects = GameServices.level().getObjectManager();
            runSpecialVblankBefore = objects != null ? objects.getVblaCounter() : 0;
            runSpecialInputApplied = true;
        } else {
            return;
        }
        Bk2FrameInput current = movie.getFrame(physicalRow);
        int prevIndex = physicalRow - 1;
        Bk2FrameInput previous = prevIndex >= 0 && prevIndex < movie.getFrameCount()
                ? movie.getFrame(prevIndex)
                : null; // fromBk2 synthesises a neutral prior row when null
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
        runOwnedInputHandler = input;
    }

    /**
     * Clears the per-frame input override and advances the SS trace cursor by
     * one row (lag or not). Ends the session via the normal fade/teardown path
     * once the recorded stage-finished frame has played. No-op for level
     * sessions.
     */
    public void advanceSpecialStageTraceCursorIfActive(InputHandler input) {
        if (input != null) {
            input.clearLogicalOverride();
        }
        if (runSpecialInputApplied) {
            applyRunSpecialPreservedVblankPolicy();
            runSpecialInputApplied = false;
            runSpecialLocalRow++;
            return;
        }
        if (ssTrace == null) {
            return;
        }
        if (fadeStarted) {
            return;
        }
        if (ssCursor >= ssStageFinishedFrame) {
            if (fixture != null) {
                fixture.closeHardwareTimingReplayRun();
            }
            beginSpecialStageTerminalExit();
            return;
        }
        ssCursor++;
    }

    private Optional<SpecialStageRowAdmission> currentRunSpecialAdmission() {
        if (runCoordinator == null || runSpecialRows == null || fadeStarted
                || runCoordinator.phase()
                        != TraceRunPlaybackCoordinator.Phase.CURRENT_SEGMENT
                || runSpecialLocalRow < 0
                || runSpecialLocalRow >= runSpecialRows.rowCount()) {
            return Optional.empty();
        }
        return Optional.of(runSpecialRows.admission(runSpecialLocalRow));
    }

    private void applyRunSpecialPreservedVblankPolicy() {
        SpecialStageRowAdmission admission = runSpecialRows.admission(runSpecialLocalRow);
        if (!admission.advancePreservedVblankIfUnchanged()) {
            return;
        }
        var objects = GameServices.level().getObjectManager();
        if (objects != null && objects.getVblaCounter() == runSpecialVblankBefore) {
            objects.initVblaCounter(runSpecialVblankBefore + 1);
        }
    }

    /**
     * Called from {@link GameLoop} each LEVEL tick while active. A run
     * session early-returns here: this completion-hold arms a fade the
     * moment ANY comparator completes, which for a run would end the whole
     * session ~1s after its FIRST segment boundary. The shared run coordinator
     * owns run completion and terminal-tail admission.
     */
    public void tick() {
        if (comparator == null || fadeStarted || isRunSession()) {
            return;
        }
        if (comparator.isComplete() && !completionArmed) {
            completionStartPending = true;
            if (!productionIterationInProgress) {
                finishPendingCompletionStart();
            }
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
        if (retryPendingSpecialStageTerminalExit()) {
            return;
        }
        if (advanceTitleCardPresentationIfReady(mode)) {
            return;
        }
        if (runCoordinator != null) {
            try {
                runCoordinatorTick(mode);
            } catch (RuntimeException failure) {
                clearRunOwnedInputOverride();
                String diagnostic = failure.getMessage() != null
                        ? failure.getMessage()
                        : failure.getClass().getSimpleName();
                applyRunCoordinatorActions(runCoordinator.abort(diagnostic));
            }
            return;
        }
        if (runAdvancer == null || fadeStarted) {
            return;
        }
        int currentSegment = runAdvancer.currentSegmentIndex();
        if (runSpecialDynamicArtComparison != null
                && runSpecialTimingSegment == currentSegment
                && mode != GameMode.SPECIAL_STAGE) {
            if (productionIterationInProgress) {
                runSpecialVerificationPending = true;
            } else {
                verifyRunSpecialDynamicArtComplete();
            }
        }
        if (runDynamicArtSegments != null
                && mode != TraceRunReplayWalker.expectedMode(
                        runSegments.get(runAdvancer.currentSegmentIndex())
                                .segment())
                && !(runSpecialDynamicArtSegmentAnticipated
                        && mode == GameMode.SPECIAL_STAGE)) {
            if (productionIterationInProgress) {
                runGapEntryPending = true;
            } else {
                enterRunDynamicArtGap();
            }
        }
        RunSegmentAdvancer.Event event = runAdvancer.onFrame(mode, cursorFrame);
        if (event instanceof RunSegmentAdvancer.AdvanceAction action) {
            if (productionIterationInProgress) {
                runAdvancePending = action;
            } else {
                applyRunAdvanceAfterProduction(action);
            }
        } else if (event instanceof RunSegmentAdvancer.EndOfRun) {
            runEndPending = true;
            if (!productionIterationInProgress) {
                finishPendingRunEnd();
            }
        }
    }

    private boolean advanceTitleCardPresentationIfReady(GameMode mode) {
        if (!launchPhase.isPresentingTitleCard()) {
            return false;
        }
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            abortLaunchFailure(
                    null,
                    "visual trace title-card presentation lost its game loop",
                    null,
                    "Failed to continue trace title card for " + entry.dir());
            return true;
        }
        if (!launchPhase.beginReplayBootstrapIfReady(mode)) {
            return true;
        }
        try {
            if (isRunSession()) {
                finishRunReplayLaunch(loop);
            } else {
                finishStandaloneReplayLaunch(loop);
            }
        } catch (Throwable launchFailure) {
            abortLaunchFailure(
                    launchFailure,
                    "visual trace replay handoff failed",
                    loop,
                    "Failed to start trace replay after title card for "
                            + entry.dir());
        }
        return true;
    }

    private void runCoordinatorTick(GameMode mode) {
        if (fadeStarted || runCoordinator.phase()
                == TraceRunPlaybackCoordinator.Phase.COMPLETE
                || runCoordinator.phase()
                == TraceRunPlaybackCoordinator.Phase.FAILED) {
            clearRunOwnedInputOverride();
            return;
        }
        runAdmittedStepOrdinal++;
        if (runCoordinator.phase()
                == TraceRunPlaybackCoordinator.Phase.TERMINAL_TAIL) {
            finishRunTerminalTailStep(mode);
            return;
        }
        if (runSpecialRows != null && mode != GameMode.SPECIAL_STAGE
                && runSpecialLocalRow < runSpecialRows.rowCount()) {
            applyRunCoordinatorActions(runCoordinator.abort(
                    "special-stage segment " + currentRunSegmentIndex()
                            + " exited after " + runSpecialLocalRow + " of "
                            + runSpecialRows.rowCount() + " rows"));
            clearRunOwnedInputOverride();
            return;
        }
        forwardLatchedRunBoundary(mode);
        int rowsConsumed = 0;
        RunPlaybackObservation currentObservation = captureRunObservation(
                mode, rowsConsumed, isLagOnlySameLevelContinuation());
        applyRunCoordinatorActions(runCoordinator.beforeAdmission(
                currentObservation));
        RunPlaybackObservation productionObservation =
                runLevelLoadedDuringSourceProduction
                        && runProductionOwnerObservation != null
                        && runCoordinator.phase()
                                == TraceRunPlaybackCoordinator.Phase.CURRENT_SEGMENT
                        ? withProductionOwner(
                                currentObservation, runProductionOwnerObservation)
                        : currentObservation;
        int productionSegmentIndex = runCoordinator.currentSegmentIndex();
        if (productionObservation.currentSegmentExhausted()) {
            applyRunCoordinatorActions(
                    runCoordinator.afterProduction(productionObservation));
            applyRunCoordinatorActions(runCoordinator.beforeAdmission(
                    captureRunObservation(mode, 0,
                            isLagOnlySameLevelContinuation())));
        }
        RunPlaybackObservation stepObservation =
                runCoordinator.phase()
                                == TraceRunPlaybackCoordinator.Phase.CURRENT_SEGMENT
                        && runCoordinator.currentSegmentIndex()
                                == productionSegmentIndex
                        ? productionObservation
                        : captureRunObservation(mode, 0,
                                isLagOnlySameLevelContinuation());
        applyRunCoordinatorActions(runCoordinator.afterStep(stepObservation));
        runProductionOwnerObservation = null;
        clearRunOwnedInputOverride();
    }

    private static RunPlaybackObservation withProductionOwner(
            RunPlaybackObservation current,
            RunPlaybackObservation owner) {
        return new RunPlaybackObservation(
                owner.mode(), current.sharedBk2Cursor(),
                current.admittedStepOrdinal(), owner.level(),
                current.initialTitleCardPending(), owner.bonus(),
                owner.specialStageIndex(), current.productionOpen(),
                current.currentSegmentExhausted(), 0,
                current.lagOnlySameLevelContinuation(),
                current.timingScheduleGeneration(),
                current.dynamicArtGeneration());
    }

    private boolean isLagOnlySameLevelContinuation() {
        int index = currentRunSegmentIndex();
        return comparator != null && index >= 0
                && index + 1 < runSegments.size()
                && TraceRunReplayWalker.isLagOnlySameLevelContinuation(
                        runSegments.get(index).segment(),
                        runSegments.get(index + 1).segment(),
                        runSegments.get(index).segment().traceFrameCount(),
                        comparator.laggedFrames());
    }

    private void forwardLatchedRunBoundary(GameMode mode) {
        int index = currentRunSegmentIndex();
        if (index < 0 || index >= runSegments.size() - 1
                || runForwardedBoundarySegment == index
                || runBoundaryProbe == null || !runBoundaryProbe.latched()) {
            return;
        }
        var boundary = runSegments.get(index).exitBoundary();
        if (boundary == null) {
            return;
        }
        int frame = runBoundaryProbe.observation().observedBk2Frame();
        RunBoundarySignal signal = switch (boundary.entryKind()) {
            case "starpost_bonus" -> mode == GameMode.BONUS_STAGE
                    && GameServices.bonusStageOrNull() != null
                    ? new RunBoundarySignal.BonusRequest(frame,
                            GameServices.bonusStageOrNull().getActiveType()) : null;
            case "giant_ring", "starpost_special" ->
                    getActiveSpecialStageIndex() != null
                    ? new RunBoundarySignal.SpecialStageRequest(
                            frame, getActiveSpecialStageIndex()) : null;
            case "stage_exit" -> mode == GameMode.LEVEL
                    ? new RunBoundarySignal.StageExit(frame) : null;
            default -> null; // level-load signals are forwarded at their load seam
        };
        if (signal != null) {
            runCoordinator.observeBoundary(signal);
            runForwardedBoundarySegment = index;
        }
    }

    private void applyRunCoordinatorActions(
            List<TraceRunPlaybackCoordinator.Action> actions) {
        runCoordinatorTranscript.addAll(actions);
        for (TraceRunPlaybackCoordinator.Action action : actions) {
            if (action instanceof TraceRunPlaybackCoordinator.AdmitDestination admit) {
                applyRunDestinationAdmission(admit.receipt());
            } else if (action instanceof TraceRunPlaybackCoordinator.CloseSegment close) {
                closeRunSegment(close.segmentIndex());
            } else if (action instanceof TraceRunPlaybackCoordinator.EnterTransitionGap) {
                runLevelLoadedDuringSourceProduction = false;
            } else if (action instanceof TraceRunPlaybackCoordinator.BeginTerminalTail tail) {
                beginRunTerminalTail(tail.plan());
            } else if (action instanceof TraceRunPlaybackCoordinator.CompleteRun) {
                finishPendingRunEnd();
            } else if (action instanceof TraceRunPlaybackCoordinator.FailRun failure) {
                failRun(failure.diagnostic());
            }
        }
    }

    /** Immutable structural transcript used to prove adapter parity in tests. */
    public List<TraceRunPlaybackCoordinator.Action> runCoordinatorTranscript() {
        return List.copyOf(runCoordinatorTranscript);
    }

    private void closeRunSegment(int segmentIndex) {
        if (runBoundaryProbe != null) {
            runBoundaryProbe.setDelegate(null);
        }
        if (runSpecialRows != null) {
            requireNoPendingRunSpecialDynamicArtRow();
            if (runSpecialDynamicArtComparison != null) {
                runSpecialDynamicArtComparison.verifyComplete();
            }
            runSpecialRows = null;
        }
        if (runDynamicArtSegments != null) {
            enterRunDynamicArtGapForSegment(segmentIndex);
        }
        if (comparator != null) {
            comparator.finalizeTerminalDynamicArtComparison();
        }
        if (fixture != null) {
            fixture.enterHardwareTimingGap();
        }
    }

    private void applyRunDestinationAdmission(DestinationAdmissionReceipt receipt) {
        TraceRunReplayWalker.SegmentPlan segment = runSegments.get(receipt.segmentIndex());
        if (runHardwareTiming != null) {
            // The handoff verifies the source schedule. It must succeed before
            // any destination comparison, dynamic-art, or input owner opens.
            runHardwareTiming.handoffToSegment(receipt.segmentIndex());
        }
        FrameComparison gapComparison = null;
        if (receipt.inputClock() == DestinationAdmissionReceipt.InputClock.SPECIAL_LOCAL) {
            runSpecialRows = Objects.requireNonNull(
                    segment.specialStageRows(),
                    "prepared special-stage rows for segment "
                            + receipt.segmentIndex());
            runSpecialLocalRow = receipt.rowsConsumed();
            runBoundaryProbe.setDelegate(null);
        }
        armRunSpecialDynamicArtComparison(receipt.segmentIndex());
        if (runDynamicArtSegments != null) {
            runDynamicArtSegments.beginSegment();
            bindRunSpecialDynamicArtTargetGeneration();
            if (runDynamicArtGapJournal != null
                    && receipt.segmentIndex() > 0) {
                gapComparison = runDynamicArtGapJournal.destinationOpened(
                        receipt.segmentIndex());
            }
        }
        if (receipt.inputClock() == DestinationAdmissionReceipt.InputClock.SHARED) {
            installRunComparator(segment, receipt.rowsConsumed(), receipt.absoluteBk2Row());
            adoptRunDestinationProductionIterationOwner(comparator);
            compareRunReturnBoundaryIfPresent(receipt.segmentIndex());
        }
        if (gapComparison != null && comparator != null) {
            comparator.ingestExternalComparison(gapComparison);
        }
        armCurrentRunBoundary();
        runLevelLoadedDuringSourceProduction = false;
        completionArmed = false;
        completionHoldFrames = 0;
    }

    private void installRunComparator(
            TraceRunReplayWalker.SegmentPlan segment,
            int rowsConsumed,
            int absoluteBk2Row) {
        GameLoop loop = Engine.currentGameLoop();
        if (loop == null) {
            throw new IllegalStateException("run destination has no active GameLoop");
        }
        comparator = new LiveTraceComparator(
                segment.trace(), ToleranceConfig.DEFAULT, rowsConsumed,
                loop::getMainPlayableSprite, loop::toggleUserPause);
        cameraFocusController = new TraceCameraFocusController(
                comparator, loop::getMainPlayableSprite, () -> {
                    var sprites = GameServices.spritesOrNull();
                    return sprites == null || sprites.getSidekicks().isEmpty()
                            ? null : sprites.getSidekicks().getFirst();
                }, GameServices::camera, GameServices.configuration(), loop::isPaused);
        loop.setTraceCameraFocusController(cameraFocusController);
        overlay = new TraceHudOverlay(comparator,
                () -> cameraFocusController.currentLabel(), this::rewindStatusLabel);
        runBoundaryProbe.setDelegate(comparator);
        GameServices.playbackDebug().startSession(movie, absoluteBk2Row);
    }

    /**
     * Transfers the current host wrapper's deferred publication owner when a
     * destination is admitted before its first production row. The snapshot
     * is rebased after the destination segment opens so generation and row-zero
     * publication are compared within the same production window.
     */
    private void adoptRunDestinationProductionIterationOwner(
            LiveTraceComparator destinationComparator) {
        if (!productionIterationInProgress) {
            return;
        }
        productionIterationComparator = Objects.requireNonNull(
                destinationComparator, "destinationComparator");
        DynamicArtDiagnosticsSnapshot destinationBefore =
                GameServices.captureDynamicArtDiagnostics();
        dynamicArtSnapshotBeforeIteration = destinationBefore;
        dynamicArtDeliverySerialBeforeIteration =
                destinationBefore.deliverySerial();
    }

    private void enterRunDynamicArtGapForSegment(int segmentIndex) {
        runClosingDynamicArtSegment = segmentIndex;
        try {
            runDynamicArtSegments.enterGap();
        } finally {
            runClosingDynamicArtSegment = -1;
        }
        if (runDynamicArtGapJournal != null
                && segmentIndex + 1 < runSegments.size()) {
            runDynamicArtGapJournal.gapOpened(segmentIndex);
        }
    }

    private void compareRunReturnBoundaryIfPresent(int destinationIndex) {
        if (destinationIndex < 2 || comparator == null) {
            return;
        }
        TraceRunReplayWalker.SegmentPlan interior =
                runSegments.get(destinationIndex - 1);
        if (interior.exitBoundary() == null
                || !"stage_exit".equals(interior.exitBoundary().entryKind())
                || interior.entryBoundary() == null) {
            return;
        }
        TraceRunReplayWalker.SegmentPlan destination =
                runSegments.get(destinationIndex);
        TraceRunReplayWalker.SegmentPlan preEntry =
                runSegments.get(interior.entryBoundary().fromSegment());
        Integer resolvedZone = destination.segment().zoneId() == null
                ? null
                : GameServices.module().getTracePlaybackProfile()
                        .resolveRecordedLevel(destination.segment().zoneId(),
                                destination.segment().act()).zone();
        AbstractPlayableSprite sprite = Engine.currentGameLoop()
                .getMainPlayableSprite();
        TraceRunBoundaryComparator.ExpectedBoundary expected =
                new TraceRunBoundaryComparator.ExpectedBoundary(
                        interior.entryBoundary(), interior.exitBoundary(),
                        preEntry.segment(), destination.segment(),
                        destination.trace().getFrame(0), resolvedZone);
        TraceRunBoundaryComparator.ActualBoundary actual =
                new TraceRunBoundaryComparator.ActualBoundary(
                        sprite != null ? (int) sprite.getCentreX() : null,
                        sprite != null ? (int) sprite.getCentreY() : null,
                        GameServices.level().getCheckpointState()
                                .getLastCheckpointIndex(),
                        GameServices.level().getCurrentZone(),
                        GameServices.level().getCurrentAct(),
                        GameServices.level().getLevelGamestate().getRings(),
                        GameServices.gameState().getEmeraldCount(),
                        !TraceRunReplayWalker.isUncomparedInterior(
                                interior.segment()));
        comparator.ingestExternalComparison(TraceRunBoundaryComparator.compare(
                interior.exitBoundary().modeChangeBk2Frame(), expected, actual));
    }

    private void beginRunTerminalTail(
            TraceRunReplayWalker.TerminalMovieTailPlan plan) {
        GameServices.playbackDebug().endSession();
        if (runBoundaryProbe != null) {
            runBoundaryProbe.setDelegate(null);
        }
        runTerminalTail = plan;
        runTerminalTailRow = plan.tailStart();
        if (!plan.shouldReplay()) {
            applyRunCoordinatorActions(runCoordinator.finishTerminalTail(
                    Engine.currentGameLoop().getCurrentGameMode()));
        }
    }

    private void finishRunTerminalTailStep(GameMode mode) {
        clearRunOwnedInputOverride();
        if (!runTerminalInputApplied) {
            return;
        }
        runTerminalInputApplied = false;
        runTerminalTailRow++;
        if (runTerminalTailRow >= runTerminalTail.tailStart()
                + runTerminalTail.rowsToReplay()) {
            applyRunCoordinatorActions(runCoordinator.finishTerminalTail(mode));
        }
    }

    private void failRun(String diagnostic) {
        LOGGER.severe("Visual trace run failed: " + diagnostic);
        TraceRunFailureStatus.recordReason(
                Math.max(0, currentRunSegmentIndex()), diagnostic,
                Math.max(0, GameServices.playbackDebug().getCursorFrame()),
                runAdmittedStepOrdinal);
        closeRunDynamicArtSegments();
        if (fixture != null) {
            fixture.closeHardwareTimingReplayRun();
        }
        startFadeOut();
    }

    private void clearRunOwnedInputOverride() {
        if (runOwnedInputHandler != null) {
            runOwnedInputHandler.clearLogicalOverride();
            runOwnedInputHandler = null;
        }
    }

    /** Installs one physical terminal-tail movie row before any mode dispatch. */
    static void applyRunTerminalTailInputIfActive(InputHandler input) {
        TraceSessionLauncher session = active();
        if (session == null || input == null || session.runTerminalTail == null
                || session.runCoordinator == null
                || session.runCoordinator.phase()
                        != TraceRunPlaybackCoordinator.Phase.TERMINAL_TAIL
                || session.runTerminalInputApplied
                || session.runTerminalTailRow >= session.runTerminalTail.tailStart()
                        + session.runTerminalTail.rowsToReplay()) {
            return;
        }
        int row = session.runTerminalTailRow;
        Bk2FrameInput current = session.movie.getFrame(row);
        Bk2FrameInput previous = row > 0 ? session.movie.getFrame(row - 1) : null;
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
        session.runOwnedInputHandler = input;
        session.runTerminalInputApplied = true;
    }

    /** Attempts destination ownership after title-card/setup admission but before production. */
    static void admitRunDestinationBeforeProductionIfActive(GameMode mode) {
        TraceSessionLauncher session = active();
        if (session == null || session.runCoordinator == null) {
            return;
        }
        session.forwardLatchedRunBoundary(mode);
        session.applyRunCoordinatorActions(session.runCoordinator.beforeAdmission(
                session.captureRunObservation(mode, 0,
                        session.isLagOnlySameLevelContinuation())));
    }

    /** Classifies the next production-owned level load without requesting one. */
    static void markNextRunLevelLoadCause(RunLevelLoadCause cause) {
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        if (context != null) {
            context.runLevelLoads().markNext(cause);
        }
    }

    static void markNextRunInteriorReturnLoad() {
        markNextRunLevelLoadCause(RunLevelLoadCause.INTERIOR_RETURN);
    }

    static void runDeathRestartLoad(com.openggf.level.LevelManager levelManager) {
        markNextRunLevelLoadCause(RunLevelLoadCause.DEATH_RESTART);
        levelManager.loadCurrentLevel();
    }

    @FunctionalInterface
    public interface LevelLoadAction {
        void load() throws IOException;
    }

    public static void runLevelAdvanceLoad(LevelLoadAction action) throws IOException {
        markNextRunLevelLoadCause(RunLevelLoadCause.LEVEL_ADVANCE);
        action.load();
    }

    /** Reports a completed level load before any pending playback bridge activates. */
    public static void beforeRunLevelLoadPlaybackActivationIfActive() {
        var level = GameServices.level();
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        if (context == null) {
            return;
        }
        RunLevelLoadTracker.Receipt receipt = context.runLevelLoads()
                .observeLoaded(level).orElse(null);
        if (receipt == null) {
            return;
        }
        TraceSessionLauncher session = active();
        if (session == null || session.runCoordinator == null) {
            return;
        }
        session.runLevelLoadGeneration = receipt.identity().loadGeneration();
        int frame = Math.max(0, GameServices.playbackDebug().getCursorFrame());
        RunPlaybackObservation.LevelIdentity identity = receipt.identity();
        RunLevelLoadCause cause = receipt.cause();
        if (cause == RunLevelLoadCause.INTERIOR_RETURN) {
            RunBoundarySignal.StageExit exit = new RunBoundarySignal.StageExit(frame);
            session.runBoundaryProbe.observeSignal(exit);
            session.runCoordinator.observeBoundary(exit);
        }
        RunBoundarySignal.LevelLoaded signal =
                new RunBoundarySignal.LevelLoaded(frame, cause, identity);
        session.runBoundaryProbe.observeSignal(signal);
        session.runCoordinator.observeBoundary(signal);
        session.runLevelLoadedDuringSourceProduction =
                session.productionIterationInProgress
                        && session.runCoordinator.phase()
                                == TraceRunPlaybackCoordinator.Phase.CURRENT_SEGMENT;
        GameLoop loop = Engine.currentGameLoop();
        GameMode mode = loop != null ? loop.getCurrentGameMode() : GameMode.LEVEL;
        session.applyRunCoordinatorActions(
                session.runCoordinator.beforeLoadedLevelActivation(
                        signal, session.captureRunObservation(
                                mode,
                                0, false)));
    }

    /** Couples load notification with the existing pending playback activation. */
    public static boolean activateScheduledPlaybackForLoadedLevel(
            PlaybackDebugManager playback) {
        beforeRunLevelLoadPlaybackActivationIfActive();
        return playback.activateScheduledLevelLoadSession();
    }

    void installDynamicArtSegments(GameplayModeContext gameplayMode) {
        Objects.requireNonNull(gameplayMode, "gameplayMode");
        dynamicArtSegmentGameplayMode = gameplayMode;
        boolean[] firstWindow = {true};
        boolean[] ownershipAcquired = {false};
        TraceRunReplayWalker.DynamicArtSegmentController controller =
                new TraceRunReplayWalker.DynamicArtSegmentController(
                        new TraceRunReplayWalker.DynamicArtSegmentWindow() {
                            @Override
                            public void open() {
                                if (firstWindow[0]) {
                                    firstWindow[0] = false;
                                    gameplayMode.plcFrameLifecycle()
                                            .acquireExternalComparisonSegmentOwnershipAfterNextService();
                                    ownershipAcquired[0] = true;
                                } else {
                                    gameplayMode.dynamicArtLifecycle()
                                            .openComparisonSegment();
                                }
                            }

                            @Override
                            public void close() {
                                gameplayMode.plcFrameLifecycle()
                                        .closeExternallyManagedComparisonSegment();
                                if (runDynamicArtGapJournal != null
                                        && runClosingDynamicArtSegment >= 0) {
                                    runDynamicArtGapJournal.sourceClosed(
                                            runClosingDynamicArtSegment);
                                }
                            }
                        });
        runDynamicArtSegments = controller;
        try {
            controller.beginSegment();
            if (entry != null && entry.runManifest() != null) {
                runDynamicArtGapJournal = new TraceRunDynamicArtGapJournal(
                        entry.runManifest(), gameplayMode.dynamicArtLifecycle());
            }
        } catch (RuntimeException | Error failure) {
            runDynamicArtSegments = null;
            dynamicArtSegmentGameplayMode = null;
            if (ownershipAcquired[0]) {
                gameplayMode.plcFrameLifecycle()
                        .setComparisonSegmentsExternallyManaged(false);
            }
            throw failure;
        }
    }

    /** Compatibility seam retained for focused whole-run lifecycle tests. */
    void installRunDynamicArtSegments(GameplayModeContext gameplayMode) {
        installDynamicArtSegments(gameplayMode);
    }

    private void closeRunDynamicArtSegments() {
        GameplayModeContext gameplayMode = dynamicArtSegmentGameplayMode;
        try {
            if (runDynamicArtSegments != null) {
                runDynamicArtSegments.close();
            }
        } finally {
            if (gameplayMode != null) {
                gameplayMode.plcFrameLifecycle()
                        .setComparisonSegmentsExternallyManaged(false);
            }
            runDynamicArtSegments = null;
            dynamicArtSegmentGameplayMode = null;
            runDynamicArtGapJournal = null;
        }
    }

    private void abortRunDynamicArtSegments() {
        GameplayModeContext gameplayMode = dynamicArtSegmentGameplayMode;
        try {
            if (runDynamicArtSegments != null) {
                try {
                    runDynamicArtSegments.close();
                } catch (DynamicArtLifecycleService
                        .UnpublishedComparisonRowException incompleteWindow) {
                    if (gameplayMode == null) {
                        throw incompleteWindow;
                    }
                    gameplayMode.dynamicArtLifecycle()
                            .abandonComparisonSegment();
                }
            }
        } finally {
            if (gameplayMode != null) {
                gameplayMode.plcFrameLifecycle()
                        .setComparisonSegmentsExternallyManaged(false);
            }
            runDynamicArtSegments = null;
            dynamicArtSegmentGameplayMode = null;
            runDynamicArtGapJournal = null;
        }
    }

    /**
     * Marks the value-free production iteration boundary owned by
     * {@link GameLoop}. This carries no trace or gameplay value into a
     * production service; it only prevents terminal cleanup from running
     * before the matching post-finish snapshot pull.
     */
    void beforeProductionIteration() {
        productionIterationInProgress = true;
        productionIterationComparator = comparator;
        GameLoop loop = Engine.currentGameLoop();
        runProductionOwnerObservation = runCoordinator != null && loop != null
                ? captureRunObservation(
                        loop.getCurrentGameMode(), 0,
                        isLagOnlySameLevelContinuation())
                : null;
        dynamicArtSnapshotBeforeIteration =
                GameServices.captureDynamicArtDiagnostics();
        dynamicArtDeliverySerialBeforeIteration =
                dynamicArtSnapshotBeforeIteration.deliverySerial();
    }

    static void runProductionIterationIfActive(Runnable productionIteration) {
        TraceSessionLauncher session = active();
        if (session == null) {
            productionIteration.run();
            return;
        }
        Objects.requireNonNull(productionIteration, "productionIteration");
        session.beforeProductionIteration();
        Throwable primaryFailure = null;
        try {
            productionIteration.run();
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
        }
        try {
            session.afterProductionIteration();
        } catch (RuntimeException | Error hookFailure) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(hookFailure);
            } else {
                primaryFailure = hookFailure;
            }
        }
        if (primaryFailure == null) {
            return;
        }
        Throwable contained = session.abortIncompleteSession(
                primaryFailure, "visual trace production iteration failed", null);
        if (session.entry != null) {
            TraceLaunchStatus.record(session.entry,
                    contained != null ? contained : primaryFailure);
        }
        if (contained instanceof Error fatal) {
            throw fatal;
        }
        LOGGER.log(java.util.logging.Level.SEVERE,
                "Visual trace session aborted after a replay failure", contained);
    }

    /**
     * Pulls one immutable diagnostics snapshot after the production
     * coordinator has finished publishing the logical iteration, then drains
     * terminal actions requested by the iteration body.
     */
    void afterProductionIteration() {
        if (!productionIterationInProgress) {
            throw new IllegalStateException(
                    "dynamic-art post-iteration hook has no active iteration");
        }
        productionIterationInProgress = false;
        LiveTraceComparator iterationComparator = productionIterationComparator;
        DynamicArtDiagnosticsSnapshot before = dynamicArtSnapshotBeforeIteration;
        productionIterationComparator = null;
        dynamicArtSnapshotBeforeIteration = null;
        if (iterationComparator != null && before != null) {
            iterationComparator.consumePostProductionPlayableAnimationAction();
            DynamicArtDiagnosticsSnapshot after =
                    GameServices.captureDynamicArtDiagnostics();
            iterationComparator.publishPendingDynamicArtComparison(
                    before, after);
        }
        drainPendingRunBoundaryActions();
        if (runSpecialDynamicArtPendingRow >= 0) {
            DynamicArtDiagnosticsSnapshot snapshot =
                    GameServices.captureDynamicArtDiagnostics();
            if (snapshot.deliverySerial()
                    <= dynamicArtDeliverySerialBeforeIteration) {
                throw new IllegalStateException(
                        "advertised visual special-stage DPLC row "
                                + runSpecialDynamicArtPendingRow
                                + " was admitted but production published no snapshot");
            }
            if (!snapshot.published()
                    || snapshot.segmentGeneration()
                            != runSpecialDynamicArtTargetGeneration
                    || snapshot.frame() != runSpecialDynamicArtPendingRow) {
                throw new IllegalStateException(
                        "advertised visual special-stage DPLC row "
                                + runSpecialDynamicArtPendingRow
                                + " did not receive an atomic publication for "
                                + "generation "
                                + runSpecialDynamicArtTargetGeneration);
            }
            comparePublishedRunSpecialDynamicArtRow(snapshot);
        }
        if (runEndPending) {
            finishPendingRunEnd();
        }
        if (completionStartPending) {
            finishPendingCompletionStart();
        }
        retryPendingTeardown();
    }

    private void drainPendingRunBoundaryActions() {
        if (runSpecialVerificationPending) {
            runSpecialVerificationPending = false;
            verifyRunSpecialDynamicArtComplete();
        }
        if (runGapEntryPending) {
            runGapEntryPending = false;
            enterRunDynamicArtGap();
        }
        RunSegmentAdvancer.AdvanceAction advance = runAdvancePending;
        runAdvancePending = null;
        if (advance != null) {
            applyRunAdvanceAfterProduction(advance);
        }
    }

    private void verifyRunSpecialDynamicArtComplete() {
        requireNoPendingRunSpecialDynamicArtRow();
        runSpecialDynamicArtComparison.verifyComplete();
    }

    private void enterRunDynamicArtGap() {
        runDynamicArtSegments.enterGap();
        if (comparator != null) {
            comparator.finalizeTerminalDynamicArtComparison();
        }
    }

    private void applyRunAdvanceAfterProduction(
            RunSegmentAdvancer.AdvanceAction action) {
        applyRunSegmentAdvance(action);
        armRunSpecialDynamicArtComparison(action.nextSegmentIndex());
        if (runDynamicArtSegments == null) {
            return;
        }
        if (runSpecialDynamicArtSegmentAnticipated
                && runSpecialTimingSegment == action.nextSegmentIndex()) {
            runSpecialDynamicArtSegmentAnticipated = false;
            return;
        }
        runDynamicArtSegments.beginSegment();
        bindRunSpecialDynamicArtTargetGeneration();
    }

    private void finishPendingCompletionStart() {
        completionStartPending = false;
        if (fixture != null) {
            fixture.runTerminalDynamicArtIteration();
            closeRunDynamicArtSegments();
            comparator.finalizeTerminalDynamicArtComparison();
            fixture.closeHardwareTimingReplayRun();
        }
        completionArmed = true;
        completionHoldFrames =
                (int) Math.round(COMPLETION_HOLD_SECONDS * 60.0);
    }

    private void finishPendingRunEnd() {
        runEndPending = false;
        closeRunDynamicArtSegments();
        if (comparator != null) {
            comparator.finalizeTerminalDynamicArtComparison();
        }
        if (fixture != null) {
            fixture.closeHardwareTimingReplayRun();
        }
        startFadeOut();
    }

    void installSpecialStageHardwareTiming(TraceReplayFixture replayFixture) {
        fixture = replayFixture;
        if (ssTrace != null
                && ssTrace.hardwareTimingSchedule().hasRecordedInput()) {
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
        if (comparator != null
                && (mode == GameMode.LEVEL || mode == GameMode.BONUS_STAGE)) {
            comparator.activatePreparedRow();
        }
        if ((runAdvancer != null || runCoordinator != null)
                && runHardwareTiming != null) {
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
                    && ssTrace.hardwareTimingSchedule().hasRecordedInput()
                    && ssCursor >= 0
                    && ssCursor < ssTrace.rowCount()) {
                fixture.beginTraceRow(ssCursor, ssCursor);
            } else {
                fixture.enterHardwareTimingGap();
            }
            return;
        }
        if (trace != null
                && trace.hardwareTimingSchedule().hasRecordedInput()
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

    /** Commits replay production classification for an admitted pause VBlank. */
    static void activateProductionMarkerForPausedBoundaryIfActive() {
        if (activeSession != null && activeSession.comparator != null) {
            activeSession.comparator.activatePreparedProductionMarker();
        }
    }

    /**
     * Latches metadata-only special-stage rows before their production update.
     * The run advancer changes segments after the update, so the first stage
     * frame anticipates the immediately following structural segment.
     */
    private void prepareRunSpecialStageHardwareTimingRow() {
        requireNoPendingRunSpecialDynamicArtRow();
        int segmentIndex = currentRunSegmentIndex();
        if (runCoordinator != null) {
            if (currentRunSpecialAdmission().isEmpty()) {
                fixture.enterHardwareTimingGap();
                return;
            }
            if (runSpecialTimingSegment != segmentIndex) {
                armRunSpecialDynamicArtComparison(segmentIndex);
            }
            SpecialStageRowAdmission admission =
                    runSpecialRows.admission(runSpecialLocalRow);
            if (admission.admitHardwareTiming()) {
                runHardwareTiming.beginSegmentRow(segmentIndex, runSpecialLocalRow);
            } else {
                fixture.enterHardwareTimingGap();
            }
            if (runSpecialRows.metadata().hasPerFrameDynamicArtTransferState()) {
                runSpecialDynamicArtPendingRow = runSpecialLocalRow;
            }
            return;
        }
        if (!"special_stage".equals(runSegments.get(segmentIndex).segment().kind())
                && segmentIndex + 1 < runSegments.size()
                && "special_stage".equals(runSegments.get(segmentIndex + 1)
                        .segment().kind())) {
            segmentIndex++;
        }
        if (!"special_stage".equals(
                runSegments.get(segmentIndex).segment().kind())) {
            fixture.enterHardwareTimingGap();
            return;
        }
        if (runSpecialTimingSegment != segmentIndex) {
            armRunSpecialDynamicArtComparison(segmentIndex);
        }
        if (currentRunSegmentIndex() != segmentIndex
                && runDynamicArtSegments != null
                && !runSpecialDynamicArtSegmentAnticipated) {
            runDynamicArtSegments.beginSegment();
            bindRunSpecialDynamicArtTargetGeneration();
            runSpecialDynamicArtSegmentAnticipated = true;
        }
        int rowCount =
                runSegments.get(segmentIndex).segment().traceFrameCount();
        if (runSpecialTimingRow < rowCount) {
            runSpecialDynamicArtPendingRow = runSpecialTimingRow;
            runHardwareTiming.beginSegmentRow(
                    segmentIndex, runSpecialTimingRow++);
        } else {
            fixture.enterHardwareTimingGap();
        }
    }

    private void armRunSpecialDynamicArtComparison(int segmentIndex) {
        if (!"special_stage".equals(
                runSegments.get(segmentIndex).segment().kind())) {
            return;
        }
        if (runSpecialTimingSegment == segmentIndex
                && runSpecialDynamicArtComparison != null) {
            return;
        }
        requireNoPendingRunSpecialDynamicArtRow();
        if (runSpecialDynamicArtComparison != null) {
            runSpecialDynamicArtComparison.verifyComplete();
        }
        runSpecialTimingSegment = segmentIndex;
        runSpecialTimingRow = 0;
        runSpecialDynamicArtPendingRow = -1;
        runSpecialDynamicArtTargetGeneration = -1;
        runSpecialDynamicArtComparison =
                new TraceRunReplayWalker.DynamicArtSegmentComparison(
                        runSegments.get(segmentIndex).trace(),
                        runSegments.get(segmentIndex).segment()
                                .traceFrameCount());
        if (currentRunSegmentIndex() == segmentIndex
                && runDynamicArtSegments != null) {
            bindRunSpecialDynamicArtTargetGeneration();
        }
    }

    private void bindRunSpecialDynamicArtTargetGeneration() {
        if (runSpecialDynamicArtComparison != null) {
            runSpecialDynamicArtTargetGeneration =
                    GameServices.captureDynamicArtDiagnostics()
                            .segmentGeneration();
        }
    }

    /**
     * Receives one immutable row only after the production lifecycle has
     * published it. The run advancer already ran inside the logical iteration,
     * so row zero reaches the comparator installed by that iteration's
     * {@link RunSegmentAdvancer.AdvanceAction}.
     */
    private void comparePublishedRunSpecialDynamicArtRow(
            DynamicArtDiagnosticsSnapshot published) {
        if (runSpecialDynamicArtPendingRow < 0
                || runSpecialDynamicArtComparison == null) {
            return;
        }
        int row = runSpecialDynamicArtPendingRow;
        int rowCount = runSegments.get(runSpecialTimingSegment)
                .segment().traceFrameCount();
        runSpecialDynamicArtPendingRow = -1;
        DynamicArtDiagnosticsSnapshot actual = published;
        if (row == rowCount - 1 && runDynamicArtSegments != null) {
            enterRunDynamicArtGapForSegment(runSpecialTimingSegment);
            actual = GameServices.captureDynamicArtDiagnostics();
        }
        FrameComparison result = runSpecialDynamicArtComparison.compareRow(
                row, actual);
        if (row == rowCount - 1) {
            runSpecialDynamicArtComparison.verifyComplete();
        }
        ingestRunSpecialDynamicArtComparison(result);
    }

    private void requireNoPendingRunSpecialDynamicArtRow() {
        if (runSpecialDynamicArtPendingRow >= 0) {
            throw new IllegalStateException(
                    "advertised visual special-stage DPLC row "
                            + runSpecialDynamicArtPendingRow
                            + " was admitted but production published no snapshot");
        }
    }

    private void ingestRunSpecialDynamicArtComparison(
            FrameComparison result) {
        if (result == null) {
            return;
        }
        if (comparator == null) {
            throw new IllegalStateException(
                    "advertised visual special-stage DPLC row has no "
                            + "live comparison report sink");
        }
        comparator.ingestExternalComparison(result);
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
        if (fadeStarted
                || (comparator == null && !launchPhase.ownsEarlyExit())) {
            return;
        }
        Throwable failure = abortIncompleteSession(
                null, "visual trace session exited by user", null);
        if (failure != null) {
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Visual trace session cleanup failed during early exit", failure);
        }
    }

    private Throwable abortIncompleteSession(
            Throwable primary, String reason, GameLoop fallbackLoop) {
        Throwable failure = primary;
        activeSession = null;
        launchPhase.abort();
        teardownPending = false;
        completionStartPending = false;
        runEndPending = false;
        runAdvancePending = null;
        runGapEntryPending = false;
        runSpecialVerificationPending = false;
        SessionManager.clearNextGameplayAdmissionPolicy();
        if (fixture != null) {
            failure = cleanupFailure(failure,
                    fixture::abortHardwareTimingReplayRun);
        }
        failure = cleanupFailure(failure, this::abortRunDynamicArtSegments);
        failure = cleanupFailure(failure, () -> TraceGhostHook.clear(ghostHook));
        failure = cleanupFailure(failure,
                () -> GameServices.audio().setRewindHistoryArmed(false));
        failure = cleanupFailure(failure,
                () -> GameServices.playbackDebug().endSession());
        failure = cleanupFailure(failure,
                () -> TraceReplaySessionBootstrap.restoreGameplayConfig(configSnapshot));
        GameLoop currentLoop = Engine.currentGameLoop();
        GameLoop cleanupLoop = currentLoop != null ? currentLoop : fallbackLoop;
        if (cleanupLoop != null) {
            failure = cleanupFailure(failure,
                    () -> cleanupLoop.setTraceCameraFocusController(null));
        }
        GameplayModeContext failedContext =
                SessionManager.getCurrentGameplayMode();
        if (failedContext != null) {
            failure = cleanupFailure(failure, failedContext::destroy);
        }
        if (cleanupLoop != null) {
            failure = cleanupFailure(failure, cleanupLoop::returnToMasterTitle);
        }
        comparator = null;
        overlay = null;
        cameraFocusController = null;
        fixture = null;
        LOGGER.info(reason);
        return failure;
    }

    private static Throwable cleanupFailure(
            Throwable primary, Runnable cleanup) {
        try {
            cleanup.run();
            return primary;
        } catch (RuntimeException | Error cleanupFailure) {
            if (primary != null) {
                primary.addSuppressed(cleanupFailure);
                return primary;
            }
            return cleanupFailure;
        }
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

    /** Arms standalone special-stage completion without replacing a native fade. */
    void beginSpecialStageTerminalExit() {
        fadeStarted = true;
        specialStageTerminalExitPending = true;
        retryPendingSpecialStageTerminalExit();
    }

    /** Resolves the terminal fade at the all-mode frame owner. */
    boolean retryPendingSpecialStageTerminalExit() {
        if (!specialStageTerminalExitPending) {
            return false;
        }
        GameplayModeContext gameplayMode =
                SessionManager.getCurrentGameplayMode();
        if (gameplayMode == null || gameplayMode.getFadeManager() == null) {
            specialStageTerminalExitPending = false;
            teardown();
            return true;
        }
        FadeManager fade = gameplayMode.getFadeManager();
        if (fade.hasPendingCompletion()) {
            return true;
        }
        switch (fade.getState()) {
            case HOLD_WHITE -> {
                specialStageTerminalExitPending = false;
                teardown();
            }
            case NONE -> {
                specialStageTerminalExitPending = false;
                fade.startFadeToBlack(this::teardown);
            }
            case HOLD_BLACK -> {
                specialStageTerminalExitPending = false;
                String diagnostic =
                        "unsupported non-progressing special-stage terminal fade: HOLD_BLACK";
                LOGGER.severe(diagnostic);
                if (entry != null) {
                    TraceLaunchStatus.record(entry, diagnostic);
                }
                teardown();
            }
            default -> {
                // A callback-free native fade is still progressing. Retry
                // without overwriting its presentation state.
            }
        }
        return true;
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
        if (productionIterationInProgress) {
            return true;
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
        closeRunDynamicArtSegments();
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
        private int pendingP1ActionPressMask;
        private boolean pendingPlayableAnimationAfterClosure;
        private boolean pendingVblankStarvedProductionMarker;

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
            var gameplayMode = SessionManager.getCurrentGameplayMode();
            LevelFrameResult result = gameplayMode.plcFrameLifecycle().runReplayedLogicalIteration(
                    gameplayMode.getFadeManager()::update,
                    frame -> step(inputs, frame));
            if (pendingPlayableAnimationAfterClosure) {
                pendingPlayableAnimationAfterClosure = false;
                var sprites = GameServices.spritesOrNull();
                if (sprites != null) {
                    sprites.advancePlayableSlotPrefix();
                }
            }
            return result;
        }

        private LevelFrameResult step(
                Bk2FrameInput inputs,
                com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame
                        lifecycleFrame) {
            int traceIndex = traceIndexForInput(inputs);
            TraceReplayRowPolicy rowPolicy = rowPolicy(traceIndex, inputs.frameIndex());
            Bk2FrameInput appliedInput = rowPolicy != null
                    ? movie.getFrame(rowPolicy.appliedBk2Index()) : inputs;
            if (traceIndex >= 0 && traceIndex < trace.frameCount()) {
                fixture.beginTraceRow(
                        traceIndex, trace.getFrame(traceIndex).frame());
            } else {
                fixture.enterHardwareTimingGap();
            }
            TraceExecutionPhase phase = rowPolicy != null
                    ? rowPolicy.phase() : TraceExecutionPhase.FULL_LEVEL_FRAME;
            if (phase == TraceExecutionPhase.ADVANCE_ONLY) {
                activateProductionMarker(rowPolicy);
                publishPlaybackInput(appliedInput, rowPolicy);
                return LevelFrameResult.GAMEPLAY_FRAME;
            }
            if (phase == TraceExecutionPhase.VBLANK_ONLY
                    || phase == TraceExecutionPhase.PLAYABLE_ANIMATION_ONLY) {
                var level = GameServices.levelOrNull();
                if (level != null && level.getObjectManager() != null) {
                    activateProductionMarker(rowPolicy);
                    com.openggf.trace.replay.TraceSuppressedRowClosure.execute(
                            LevelFrameContext.from(
                                    SessionManager.getCurrentGameplayMode()),
                            lifecycleFrame,
                            level,
                            loop::startPendingInLevelTitleCard,
                            loop::applyTitleCardControlLock);
                }
                if (phase == TraceExecutionPhase.PLAYABLE_ANIMATION_ONLY) {
                    pendingPlayableAnimationAfterClosure = true;
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
                    isAppliedStartPressEdge(rowPolicy, appliedInput));
            if (admission.result() == LevelFrameResult.PAUSED) {
                activateProductionMarker(rowPolicy);
                LevelFrameStep.serviceVBlankOnly(
                        LevelFrameContext.from(SessionManager.getCurrentGameplayMode()),
                        lifecycleFrame,
                        com.openggf.game.resources.PlcLifecyclePhase.NORMAL_PAUSE);
                return admission.result();
            }
            if (admission.result() == LevelFrameResult.SETUP_ONLY) {
                return admission.result();
            }
            activateProductionMarker(rowPolicy);
            publishPlaybackInput(appliedInput, rowPolicy);
            sprites.setPlaybackInputSuppressed(true);
            sprites.publishHeldInputForLevelEvents(loop.getInputHandler());
            LevelFrameResult result = LevelFrameStep.execute(
                    LevelFrameContext.from(SessionManager.getCurrentGameplayMode()),
                    lifecycleFrame,
                    com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL,
                    level, camera, () -> sprites.update(loop.getInputHandler()),
                    LevelFrameStep.DIRECT_WRAPPER);
            if (result == LevelFrameResult.GAMEPLAY_FRAME) {
                pendingP1ActionPressMask = 0;
            }
            return result;
        }

        @Override
        public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
            int traceIndex = traceIndexForInput(inputAtFrame);
            pendingP1ActionPressMask = restorePendingP1ActionPressMask(traceIndex);
            if (inputAtFrame != null) {
                TraceReplayRowPolicy policy = rowPolicy(
                        traceIndex, inputAtFrame.frameIndex());
                Bk2FrameInput applied = policy != null
                        ? movie.getFrame(policy.appliedBk2Index()) : inputAtFrame;
                int predecessorIndex = policy != null
                        ? policy.appliedPredecessorBk2Index()
                        : applied.frameIndex() - 1;
                Bk2FrameInput previous = predecessorIndex >= 0
                        ? movie.getFrame(predecessorIndex) : null;
                loop.getInputHandler().setLogicalOverride(
                        RecordedInputSnapshots.fromBk2(
                                applied, previous, pendingP1ActionPressMask));
            }
            pendingVblankStarvedProductionMarker =
                    restorePendingProductionMarker(traceIndex);
        }

        private void publishPlaybackInput(
                Bk2FrameInput inputs,
                TraceReplayRowPolicy rowPolicy) {
            int predecessorIndex = rowPolicy != null
                    ? rowPolicy.appliedPredecessorBk2Index()
                    : inputs.frameIndex() - 1;
            Bk2FrameInput previous = predecessorIndex >= 0
                    ? movie.getFrame(predecessorIndex) : null;
            pendingP1ActionPressMask |= newP1ActionPressMask(inputs, previous);
            loop.getInputHandler().setLogicalOverride(
                    RecordedInputSnapshots.fromBk2(
                            inputs, previous, pendingP1ActionPressMask));

            var sprites = GameServices.spritesOrNull();
            if (sprites != null) {
                sprites.setPlaybackInputSuppressed(true);
            }
        }

        private int restorePendingP1ActionPressMask(int restoredTraceIndex) {
            if (restoredTraceIndex < traceBaseFrame
                    || restoredTraceIndex >= trace.frameCount()) {
                return 0;
            }
            int pending = 0;
            for (int index = restoredTraceIndex; index >= traceBaseFrame; index--) {
                int validationBk2Index = movieBaseFrame + index - traceBaseFrame;
                if (validationBk2Index < 0
                        || validationBk2Index >= movie.getFrameCount()) {
                    break;
                }
                TraceReplayRowPolicy policy = TraceReplayRowPolicy.resolve(
                        trace, index, validationBk2Index);
                if (policy.phase() == TraceExecutionPhase.FULL_LEVEL_FRAME
                        || policy.phase()
                        == TraceExecutionPhase.FULL_LEVEL_FRAME_WITH_SIDEKICK_ANIMATION_HELD) {
                    break;
                }
                if (policy.phase() != TraceExecutionPhase.ADVANCE_ONLY) {
                    continue;
                }
                Bk2FrameInput applied = movie.getFrame(policy.appliedBk2Index());
                Bk2FrameInput previous = policy.appliedPredecessorBk2Index() >= 0
                        ? movie.getFrame(policy.appliedPredecessorBk2Index()) : null;
                pending |= newP1ActionPressMask(applied, previous);
            }
            return pending;
        }

        private static int newP1ActionPressMask(
                Bk2FrameInput current,
                Bk2FrameInput previous) {
            int previousAction = previous != null ? previous.p1ActionMask() : 0;
            return current.p1ActionMask() & ~previousAction;
        }

        private boolean isAppliedStartPressEdge(
                TraceReplayRowPolicy rowPolicy,
                Bk2FrameInput appliedInput) {
            int predecessorIndex = rowPolicy != null
                    ? rowPolicy.appliedPredecessorBk2Index()
                    : appliedInput.frameIndex() - 1;
            boolean predecessorHeld = predecessorIndex >= 0
                    && movie.getFrame(predecessorIndex).p1StartPressed();
            return appliedInput.p1StartPressed() && !predecessorHeld;
        }

        private TraceReplayRowPolicy rowPolicy(
                int traceIndex, int validationBk2Index) {
            if (traceIndex < 0 || traceIndex >= trace.frameCount()) {
                return null;
            }
            return TraceReplayRowPolicy.resolve(
                    trace, traceIndex, validationBk2Index);
        }

        private void activateProductionMarker(TraceReplayRowPolicy rowPolicy) {
            if (rowPolicy == null) {
                return;
            }
            boolean vblankStarved = isVblankStarved(rowPolicy.traceIndex());
            if (!rowPolicy.productionPublicationClaim()) {
                pendingVblankStarvedProductionMarker |= vblankStarved;
                return;
            }
            if (pendingVblankStarvedProductionMarker || vblankStarved) {
                TraceReplayBootstrap.markReplayProductionIterationWithoutVblank();
            }
            pendingVblankStarvedProductionMarker = false;
        }

        private boolean isVblankStarved(int traceIndex) {
            TraceFrame current = trace.getFrame(traceIndex);
            TraceFrame previous = traceIndex > 0 ? trace.getFrame(traceIndex - 1) : null;
            return TraceReplayBootstrap.isVblankStarvedIterationForReplay(
                    previous, current);
        }

        private boolean restorePendingProductionMarker(int restoredTraceIndex) {
            boolean pending = false;
            for (int index = restoredTraceIndex;
                    index >= 0 && index < trace.frameCount(); index--) {
                TraceReplayRowPolicy policy = TraceReplayRowPolicy.resolve(
                        trace, index, 0);
                if (policy.productionPublicationClaim()) {
                    break;
                }
                pending |= isVblankStarved(index);
            }
            return pending;
        }

        private int traceIndexForInput(Bk2FrameInput input) {
            if (input == null) {
                return -1;
            }
            int relative = Math.max(
                    0, input.frameIndex() - movieBaseFrame + 1);
            return traceBaseFrame + relative - 1;
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
        public void abortHardwareTimingReplayRun() {
            if (hardwareTimingReplayClosed) {
                return;
            }
            hardwareTimingReplayClosed = true;
            if (hardwareTimingGameplayMode != null) {
                hardwareTimingGameplayMode.setHardwareTimingBoundaryObserver(null);
                if (hardwareTimingGameplayMode.getRewindRegistry() != null) {
                    hardwareTimingGameplayMode.getRewindRegistry()
                            .deregister(HardwareTimingReplayPort.REWIND_KEY);
                }
                hardwareTimingGameplayMode.clearHardwareTimingReplayCloseHook();
            }
            hardwareTimingObserver = null;
            hardwareTimingGameplayMode = null;
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
        public void advancePlayableFixedSlotsOnly() {
            GameServices.sprites().advanceTailsTailsAfterObjectExecution();
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

        @Override
        public int peekRecordingInputAt(int offset) {
            return playback.peekInputMaskAt(offset);
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
