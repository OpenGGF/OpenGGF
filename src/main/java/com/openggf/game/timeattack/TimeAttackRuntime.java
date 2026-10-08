package com.openggf.game.timeattack;

import com.openggf.game.run.DeterminismFingerprint;
import com.openggf.game.ghost.GhostCaptureBuffer;
import com.openggf.ghost.GhostFrame;
import com.openggf.game.ghost.GhostHeader;
import com.openggf.game.ghost.GhostFileCodec;
import com.openggf.game.ghost.GhostPlaybackCursor;
import com.openggf.game.ghost.GhostRecording;
import com.openggf.sprites.ghost.ActiveGhost;
import com.openggf.game.run.GhostPose;
import com.openggf.game.run.PlayerPose;
import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunHandle;
import com.openggf.game.run.RunHost;
import com.openggf.game.run.RunInput;
import com.openggf.game.run.RunLevelStart;
import com.openggf.game.run.RunSpec;
import com.openggf.game.run.RunStep;
import com.openggf.game.session.GameplayRunPolicy;

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
public final class TimeAttackRuntime implements RunHost {
    private static final Logger LOGGER = Logger.getLogger(TimeAttackRuntime.class.getName());

    /** Per-frame partner of a multiplayer round: drains the network and may hold the run. */
    public interface FrameCompanion {
        /** Called every level frame before the step; false holds the run (countdown). */
        boolean admit(RunInput input);

        /** Called after every executed step. */
        void afterStep();
    }

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
    private RunHandle handle;
    private java.util.function.IntSupplier retryKey;
    private FrameCompanion companion;
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
        if (listener != null && isAttemptActive()) {
            listener.onAttemptBegan(attemptOrdinal);
        }
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
        if (handle != null) {
            handle.retry();
        }
    }

    /** The run handle the engine returned when it began hosting this runtime's run. */
    public void attachHandle(RunHandle handle) {
        this.handle = handle;
    }

    /** Key (GLFW/SceneKeys code) that retries the attempt. */
    public void setRetryKey(java.util.function.IntSupplier retryKey) {
        this.retryKey = retryKey;
    }

    public void setFrameCompanion(FrameCompanion companion) {
        this.companion = companion;
    }

    /** The spec the engine runs for the armed launch: one isolated act. */
    public RunSpec runSpec() {
        return new RunSpec(launch.gameId(), launch.zone(), launch.act(), launch.character(),
                GameplayRunPolicy.isolatedAct());
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

    // ── RunHost (thin; all decision logic above) ──────────────────────────

    @Override
    public void onLevelReady(RunLevelStart start) {
        beginAttemptForTest(start.determinismFingerprint());
        if (start.debugAssisted()) {
            // An overlay already visible before spawn is as much an advantage as
            // toggling one on mid-run, so taint immediately.
            markTainted();
        }
    }

    @Override
    public boolean admitStep(RunInput input) {
        if (retryKey != null && input.keyPressed(retryKey.getAsInt())) {
            requestRetry();
        }
        return companion == null || companion.admit(input);
    }

    @Override
    public void afterStep(RunStep step) {
        if (step.debugAssisted()) {
            markTainted();
        }
        PlayerPose pose = step.player();
        tickForTest(step.heldMask(), step.startHeld(), step.actComplete(), step.checkpointIndex(),
                new GhostFrame(pose.centreX(), pose.centreY(), pose.mappingFrame(), pose.hFlip(),
                        pose.vFlip(), false, pose.priorityBucket(), pose.highPriority()));
        if (companion != null) {
            companion.afterStep();
        }
    }

    @Override
    public List<GhostPose> ghosts() {
        List<ActiveGhost> active = assembleActiveGhosts();
        List<GhostPose> poses = new ArrayList<>(active.size());
        for (ActiveGhost ghost : active) {
            GhostFrame f = ghost.frame();
            poses.add(new GhostPose(ghost.slotId(), ghost.characterCode() == null ? "sonic" : ghost.characterCode(),
                    new PlayerPose(f.x(), f.y(), f.mappingFrame(), f.hFlip(), f.vFlip(), f.priorityBucket(),
                            f.highPriority()),
                    ghost.nameplate(), ghost.opacityScale()));
        }
        return poses;
    }

    @Override
    public void onRunEnded(RunEndReason reason) {
        deactivate();
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

    public void deactivate() {
        // Drop opponent cursors so a frozen ghost cannot keep rendering after
        // a level-ended deactivate.
        opponents.clear();
        opponentRecordings.clear();
        launch = null;
        attempt = null;
        attemptListener = null;
        extraGhostSupplier = null;
        handle = null;
        companion = null;
    }
}
