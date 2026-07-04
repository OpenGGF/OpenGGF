package com.openggf.game.timeattack;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Solo time-attack orchestrator (main spec §3/§6.1). All timing decisions live
 * in TimeAttackAttempt; this class samples live state, feeds the attempt,
 * captures the ghost, plays back opponents, and persists new bests.
 */
public final class TimeAttackRuntime {
    private static final Logger LOGGER = Logger.getLogger(TimeAttackRuntime.class.getName());

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
    }

    public void markTainted() { tainted = true; }

    /**
     * Void the in-flight attempt without ending the session. Used when a special/
     * bonus stage interrupts a timed run: the player returns to the act and the
     * retry stays available, but this run can never be replay-verified.
     */
    public void voidCurrentAttempt() {
        if (attempt != null) {
            attempt.voidAttempt();
        }
    }

    public void requestRetry() {
        if (attempt != null) attempt.voidAttempt();
        retryRequested = true;
    }
    public boolean consumeRetryRequested() {
        boolean r = retryRequested;
        retryRequested = false;
        return r;
    }

    void tickForTest(int heldMask, boolean startHeld, boolean endOfLevel, int checkpointIndex,
                     GhostFrame sampledFrame) {
        if (attempt == null) return;
        if (capture.frameCount() >= GhostFileCodec.MAX_FRAMES) {
            attempt.voidAttempt(); // ROM 10:00 time-over cap — over-cap ghosts can never exist
            return;
        }
        TimeAttackAttempt.Phase before = attempt.phase();
        attempt.onFrame(heldMask, endOfLevel, checkpointIndex);
        if (attempt.phase() == TimeAttackAttempt.Phase.VOID) return;
        inputRecording.appendFrame(heldMask, startHeld);
        capture.capture(sampledFrame.x(), sampledFrame.y(), sampledFrame.mappingFrame(),
                sampledFrame.hFlip(), sampledFrame.vFlip(), sampledFrame.priorityBucket(),
                sampledFrame.highPriority(), attempt.phase() == TimeAttackAttempt.Phase.FINISHED);
        if (before != TimeAttackAttempt.Phase.FINISHED
                && attempt.phase() == TimeAttackAttempt.Phase.FINISHED && !tainted) {
            persistIfBest();
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
        if (sprites == null || opponents.isEmpty()) {
            return;
        }
        AbstractPlayableSprite player;
        try {
            player = RecordingMainPlayerResolver.resolve(GameServices.configuration(), sprites);
        } catch (IllegalStateException e) {
            return;
        }
        int cursorFrame = attemptFrameCountForPlayback();
        List<ActiveGhost> active = new ArrayList<>(opponents.size());
        for (int i = 0; i < opponents.size(); i++) {
            GhostFrame frame = opponents.get(i).frameFor(cursorFrame);
            active.add(new ActiveGhost("ghost" + i, opponentRecordings.get(i).header().character(), frame));
        }
        ghostRenderer.renderForLayer(active, bucket, highPriority,
                player.getCentreX(), player.getCentreY());
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
        boolean endOfLevel = gameState.isEndOfLevelActive();
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
    }
}
