package com.openggf.game.timeattack;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.ghost.GhostCaptureBuffer;
import com.openggf.game.ghost.GhostFrame;
import com.openggf.game.ghost.GhostHeader;
import com.openggf.game.ghost.GhostFileCodec;
import com.openggf.game.ghost.GhostPlaybackCursor;
import com.openggf.game.ghost.GhostRecording;
import com.openggf.game.ghost.GhostRenderRegistry;
import com.openggf.game.recording.RecordingMainPlayerResolver;
import com.openggf.sprites.ghost.ActiveGhost;
import com.openggf.sprites.ghost.GhostRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.version.AppVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Solo time-attack orchestrator (main spec §3/§6.1). All timing decisions live
 * in TimeAttackAttempt; this class samples live state, feeds the attempt,
 * captures the ghost, plays back opponents, and persists new bests.
 */
public final class TimeAttackRuntime {
    private static final Logger LOGGER = Logger.getLogger(TimeAttackRuntime.class.getName());

    /** Multiplayer bridge for spawn-anchored attempt lifecycle and frame streaming. */
    public interface AttemptListener {
        void onAttemptBegan(int attemptOrdinal);

        void onFrameSampled(int attemptOrdinal, GhostFrame frame);

        void onAttemptFinished(int attemptOrdinal, int timeFrames,
                               int firstInputFrame, int finishFrame,
                               byte[] inputRecordingSha256,
                               AttemptInputRecording recording);

        void onAttemptVoided(int attemptOrdinal);
    }

    private final GhostStore store;
    private final java.nio.file.Path identityDir;
    private final java.util.function.BooleanSupplier launchBlocked;
    private final GhostRenderer ghostRenderer = new GhostRenderer();
    private final GhostRenderRegistry.GhostLayerRenderer layerRenderer = this::renderGhostsForLayer;
    private com.openggf.net.identity.PlayerIdentity identity;
    private TimeAttackLaunchRequest launch;
    private TimeAttackAttempt attempt;
    private AttemptInputRecording inputRecording;
    private final GhostCaptureBuffer capture = new GhostCaptureBuffer();
    private final List<GhostPlaybackCursor> opponents = new ArrayList<>();
    private final List<GhostRecording> opponentRecordings = new ArrayList<>();
    private GhostRecording bestGhost;
    private boolean tainted;
    private boolean newBest;
    private boolean retryRequested;
    private int pendingHeldMask;
    private boolean pendingStartHeld;
    private GhostRenderRegistry registeredRegistry;
    private AttemptListener attemptListener;
    private int attemptOrdinal;
    private Supplier<List<ActiveGhost>> extraGhostSupplier;

    public TimeAttackRuntime(GhostStore store, java.nio.file.Path identityDir,
                             java.util.function.BooleanSupplier launchBlocked) {
        this.store = store;
        this.identityDir = identityDir;
        this.launchBlocked = launchBlocked;
    }

    public void armForLaunch(TimeAttackLaunchRequest request) {
        if (launchBlocked.getAsBoolean()) {
            LOGGER.warning("Time attack refused: trace/test/playback-debug mode is active");
            return;
        }
        this.launch = request;
        this.attemptOrdinal = 0;
        if (identity == null) {
            try {
                identity = com.openggf.net.identity.PlayerIdentity.loadOrCreate(identityDir);
                LOGGER.info("Time attack identity " + identity.fingerprint());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Player identity unavailable; continuing without", e);
            }
        }
    }

    public boolean isActive() { return launch != null; }
    public boolean isAttemptRunning() {
        return attempt != null && attempt.phase() == TimeAttackAttempt.Phase.RUNNING;
    }

    public boolean isAttemptFinished() {
        return attempt != null && attempt.phase() == TimeAttackAttempt.Phase.FINISHED;
    }

    /** ARMED or RUNNING; spawn-idle attempts are already visible on the wire. */
    public boolean isAttemptActive() {
        return attempt != null && (attempt.phase() == TimeAttackAttempt.Phase.ARMED
                || attempt.phase() == TimeAttackAttempt.Phase.RUNNING);
    }

    public void setAttemptListener(AttemptListener listener) {
        this.attemptListener = listener;
    }

    public void setExtraGhostSupplier(Supplier<List<ActiveGhost>> supplier) {
        this.extraGhostSupplier = supplier;
    }

    public TimeAttackLaunchRequest launch() { return launch; }

    /** Spawn hook: level for an armed run is loaded. Fingerprint captured by caller. */
    void beginAttemptForTest(String fingerprint) {
        attempt = new TimeAttackAttempt();
        inputRecording = new AttemptInputRecording(new AttemptStartDescriptor(
                launch.gameId(), launch.zone(), launch.act(), launch.character(), fingerprint));
        capture.reset();
        tainted = false;
        newBest = false;
        opponents.clear();
        opponentRecordings.clear();
        try {
            bestGhost = store.loadBest(launch.gameId(), launch.zone(), launch.act(),
                    launch.character()).orElse(null);
            if (bestGhost != null) {
                opponents.add(new GhostPlaybackCursor(bestGhost));
                opponentRecordings.add(bestGhost);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed loading best ghost", e);
        }
        for (Path extra : launch.extraGhosts()) {
            try {
                GhostRecording imported = GhostFileCodec.read(extra);
                GhostHeader h = imported.header();
                if (!h.gameId().equals(launch.gameId())
                        || h.zone() != launch.zone() || h.act() != launch.act()) {
                    LOGGER.warning("Skipping import " + extra + ": recorded for "
                            + h.gameId() + " " + h.zone() + "-" + h.act()
                            + ", room is " + launch.gameId() + " " + launch.zone() + "-" + launch.act());
                    continue;
                }
                opponents.add(new GhostPlaybackCursor(imported));
                opponentRecordings.add(imported);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Skipping unreadable import " + extra, e);
            }
        }
        attemptOrdinal++;
        if (attemptListener != null) {
            attemptListener.onAttemptBegan(attemptOrdinal);
        }
    }

    public void markTainted() { tainted = true; }

    public void requestRetry() {
        voidCurrentAttempt();
        retryRequested = true;
    }

    /** Voids an ARMED or RUNNING attempt exactly once. */
    public void voidCurrentAttempt() {
        if (!isAttemptActive()) {
            return;
        }
        attempt.voidAttempt();
        if (attemptListener != null) {
            attemptListener.onAttemptVoided(attemptOrdinal);
        }
    }
    public boolean consumeRetryRequested() {
        boolean r = retryRequested;
        retryRequested = false;
        return r;
    }

    void tickForTest(int heldMask, boolean startHeld, boolean endOfLevel, int checkpointIndex,
                     GhostFrame sampledFrame) {
        if (!isAttemptActive()) return;
        if (capture.frameCount() >= GhostFileCodec.MAX_FRAMES) {
            voidCurrentAttempt(); // ROM 10:00 time-over cap — over-cap ghosts can never exist
            return;
        }
        TimeAttackAttempt.Phase before = attempt.phase();
        attempt.onFrame(heldMask, endOfLevel, checkpointIndex);
        if (attempt.phase() == TimeAttackAttempt.Phase.VOID) return;
        inputRecording.appendFrame(heldMask, startHeld);
        capture.capture(sampledFrame.x(), sampledFrame.y(), sampledFrame.mappingFrame(),
                sampledFrame.hFlip(), sampledFrame.vFlip(), sampledFrame.priorityBucket(),
                sampledFrame.highPriority(), attempt.phase() == TimeAttackAttempt.Phase.FINISHED);
        if (attemptListener != null) {
            attemptListener.onFrameSampled(attemptOrdinal, sampledFrame);
        }
        if (before != TimeAttackAttempt.Phase.FINISHED
                && attempt.phase() == TimeAttackAttempt.Phase.FINISHED) {
            if (!tainted) {
                persistIfBest();
            }
            if (attemptListener != null) {
                attemptListener.onAttemptFinished(attemptOrdinal, attempt.finalTimeFrames(),
                        attempt.firstInputFrame(), attempt.finishFrame(), inputRecording.sha256(),
                        inputRecording);
            }
        }
    }

    private void persistIfBest() {
        String displayName = identity != null ? identity.fingerprint().substring(0, 8) : "";
        GhostHeader header = new GhostHeader(GhostFileCodec.FORMAT_VERSION, launch.gameId(),
                launch.zone(), launch.act(), launch.character(), displayName,
                attempt.firstInputFrame(), attempt.finishFrame(), attempt.splitFrames(),
                inputRecording.sha256());
        try {
            newBest = store.saveIfBest(new GhostRecording(header, capture.toFrameData()),
                    inputRecording);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed saving best ghost", e);
        }
    }

    public TimeAttackHudState hudState() {
        if (attempt == null) return TimeAttackHudState.INACTIVE;
        int best = bestGhost != null ? bestGhost.header().finalTimeFrames() : -1;
        int[] ghostSplits = bestGhost != null ? bestGhost.header().splitFrames() : new int[0];
        int ghostFirstInput = bestGhost != null ? bestGhost.header().firstInputFrame() : 0;
        int[] attemptSplits = attempt.splitFrames();
        int lastDelta = attemptSplits.length == 0 ? Integer.MIN_VALUE
                : TimeAttackDeltas.deltaAtSplit(attemptSplits, attempt.firstInputFrame(),
                        ghostSplits, ghostFirstInput, attemptSplits.length - 1);
        return new TimeAttackHudState(true, attempt.elapsedDisplayFrames(), best, lastDelta,
                attempt.phase() == TimeAttackAttempt.Phase.FINISHED, newBest);
    }

    int attemptFrameCountForPlayback() { return attempt == null ? 0 : attempt.frameCount(); }
    List<GhostPlaybackCursor> opponents() { return opponents; }
    GhostRecording bestGhost() { return bestGhost; }

    // ── Live wrappers (thin; all decision logic above) ─────────────────────

    /** Spawn hook: level for an armed/retried run has just loaded. */
    public void onLevelReady() {
        String fingerprint = new DeterminismFingerprint(AppVersion.get(), romChecksumOrZero()).asString();
        beginAttemptForTest(fingerprint);
        // The sidekick pattern-bank cursor resets on every level load
        // (LevelPlayableArtInitializer), so cached ghost slots hold stale bank
        // bases — clear them before the new level's first render.
        ghostRenderer.clearSlots();
        attachRenderer(GameServices.ghostRenderRegistryOrNull());
        applyTimeAttackActiveFlag(GameServices.gameStateOrNull(), true);
    }

    /**
     * Sets/clears {@link GameStateManager#setTimeAttackActive(boolean)}, tolerating a
     * null {@code gameState} (mirrors the other GameServices-resolved wrappers in this
     * class). Package-visible seam so the flag lifecycle is testable without
     * GameServices/a live GameplayModeContext.
     */
    void applyTimeAttackActiveFlag(GameStateManager gameState, boolean active) {
        if (gameState != null) {
            gameState.setTimeAttackActive(active);
        }
    }

    /**
     * Register the ghost layer renderer on {@code registry}, first detaching from any
     * previously-registered registry. A retry re-enters {@link #onLevelReady()} on the
     * same {@code GameplayModeContext} (and an editor round-trip on a rebuilt one), so
     * without this detach the renderer would stack duplicate registrations and draw the
     * ghost N+1 times. Package-visible seam so registration is testable engine-free.
     */
    void attachRenderer(GhostRenderRegistry registry) {
        if (registeredRegistry != null) {
            registeredRegistry.unregister(layerRenderer);
            registeredRegistry = null;
        }
        if (registry != null) {
            registry.register(layerRenderer);
            registeredRegistry = registry;
        }
    }

    private int romChecksumOrZero() {
        try {
            return GameServices.rom().getRom().calculateChecksum();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to compute ROM checksum for time-attack fingerprint", e);
            return 0;
        }
    }

    private void renderGhostsForLayer(int bucket, boolean highPriority) {
        var sprites = GameServices.spritesOrNull();
        if (sprites == null) {
            return;
        }
        AbstractPlayableSprite player;
        try {
            player = RecordingMainPlayerResolver.resolve(GameServices.configuration(), sprites);
        } catch (IllegalStateException e) {
            return;
        }
        List<ActiveGhost> active = assembleActiveGhosts();
        if (active.isEmpty()) {
            return;
        }
        ghostRenderer.renderForLayer(active, bucket, highPriority,
                player.getCentreX(), player.getCentreY());
    }

    private List<ActiveGhost> assembleActiveGhosts() {
        int cursorFrame = attemptFrameCountForPlayback();
        List<ActiveGhost> active = new ArrayList<>(Math.min(8, opponents.size() + 1));
        for (int i = 0; i < opponents.size(); i++) {
            if (active.size() >= 8) {
                break;
            }
            GhostFrame frame = opponents.get(i).frameFor(cursorFrame);
            active.add(new ActiveGhost("ghost" + i, opponentRecordings.get(i).header().character(), frame));
        }
        if (extraGhostSupplier != null && active.size() < 8) {
            List<ActiveGhost> extras = extraGhostSupplier.get();
            if (extras != null) {
                for (ActiveGhost extra : extras) {
                    if (active.size() >= 8) {
                        break;
                    }
                    if (extra != null) {
                        active.add(extra);
                    }
                }
            }
        }
        return active;
    }

    List<ActiveGhost> activeGhostsForTest() {
        return List.copyOf(assembleActiveGhosts());
    }

    public void beforeLevelFrame(InputHandler input) {
        var p1 = input.logical().player1();
        pendingHeldMask = p1.heldMask();
        pendingStartHeld = p1.startHeld();
    }

    public void afterLevelFrame() {
        var sprites = GameServices.spritesOrNull();
        var level = GameServices.levelOrNull();
        var gameState = GameServices.gameStateOrNull();
        if (sprites == null || level == null || gameState == null) {
            return;
        }
        AbstractPlayableSprite player;
        try {
            player = RecordingMainPlayerResolver.resolve(GameServices.configuration(), sprites);
        } catch (IllegalStateException e) {
            return;
        }
        // S3K raises the ROM Level_end_flag (endOfLevelActive); S1/S2 raise the
        // game-agnostic act-completion signal (their ROMs never set Level_end_flag,
        // and shared physics reads it for the strict right-boundary clamp).
        boolean endOfLevel = gameState.isEndOfLevelActive() || gameState.isActCompletionSignalActive();
        var checkpointState = level.getCheckpointState();
        int checkpointIndex = checkpointState != null ? checkpointState.getLastCheckpointIndex() : -1;
        GhostFrame sampledFrame = new GhostFrame(player.getCentreX(), player.getCentreY(),
                player.getMappingFrame(), player.getRenderHFlip(), player.getRenderVFlip(),
                false, player.getPriorityBucket(), player.isHighPriority());
        tickForTest(pendingHeldMask, pendingStartHeld, endOfLevel, checkpointIndex, sampledFrame);
    }

    public void deactivate() {
        if (registeredRegistry != null) {
            registeredRegistry.unregister(layerRenderer);
            registeredRegistry = null;
        }
        ghostRenderer.clearSlots();
        // Drop opponent cursors so a frozen ghost cannot keep rendering after
        // a level-ended deactivate.
        opponents.clear();
        opponentRecordings.clear();
        launch = null;
        attempt = null;
        attemptListener = null;
        extraGhostSupplier = null;
        applyTimeAttackActiveFlag(GameServices.gameStateOrNull(), false);
    }
}
