package com.openggf.net.hub;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.LongSupplier;

/** Per-player ghost-stream contiguity, bounds, speed, rate, and pacing checks. */
public final class GhostStreamValidator {
    public enum Verdict { ACCEPT, ACCEPT_FLAGGED, DROP, KICK }

    public static final int KICK_THRESHOLD = 10;
    public static final int RATE_BURST_FRAMES = 300;
    public static final int RATE_REFILL_PER_SECOND = 66;
    public static final int MAX_FRAME_LEAD = 12;
    public static final long PACING_WARMUP_MILLIS = 3000;
    public static final int PACING_MIN_FPS = 54;

    private int swapFirst = -1, swapLast;
    private com.openggf.net.protocol.ControlMessage.SwapTarget swapTarget;
    void admitSwap(com.openggf.net.protocol.ControlMessage.SwapTarget target, int first) {
        swapTarget = target; swapFirst = first; swapLast = first + 180;
    }
    void cancelSwap() { swapTarget = null; }
    private TrackValidationProfile profile;
    private boolean respawnsEnabled, arenaRespawns;
    // Transport admission bounds, not native gameplay constants: one marked transition,
    // at most eight returns per lap/checkpoint and 64 per attempt, spaced two seconds apart.
    private static final int MAX_RESETS_PER_PROGRESS = 8;
    private int resetCount, lastResetFrame;
    private final java.util.Map<Integer, Integer> resetsByProgress = new java.util.HashMap<>();
    private final java.util.Set<Integer> savedAnchors = new java.util.HashSet<>();
    private final java.util.Map<Integer, GhostFrame> anchors = new java.util.HashMap<>();
    /** Competition only. Markers cannot move beyond an already observed checkpoint. */
    void enableRespawns(boolean enabled) { enableRespawns(enabled, profile != null && profile.lapCount() > 0); }
    void enableRespawns(boolean enabled, boolean arena) { respawnsEnabled = enabled; arenaRespawns = arena; }
    private final LongSupplier wallClockMillis;
    private final ViolationSink sink;

    private int currentAttemptId = Integer.MIN_VALUE;
    private int nextExpectedFrameIndex;
    private long attemptFirstSeenMillis;
    private boolean attemptFlagged;
    private boolean attemptStarted;
    private GhostFrame previousFrame;
    private MessageDigest streamDigest = newDigest();
    private byte[] sealedStreamHash;
    private int violationCount;
    private double rateTokens = RATE_BURST_FRAMES;
    private long rateLastRefillMillis = Long.MIN_VALUE;

    public GhostStreamValidator(TrackValidationProfile profileOrNull,
                                LongSupplier wallClockMillis, ViolationSink sink) {
        this.profile = profileOrNull;
        this.wallClockMillis = wallClockMillis;
        this.sink = sink;
    }

    public void updateProfile(TrackValidationProfile profileOrNull) {
        profile = profileOrNull;
    }

    /** Anchors stream pacing to the server-observed control message, not first data. */
    public void onAttemptStart(int attemptId) {
        if (attemptId <= currentAttemptId) {
            violate("attempt-order", "attempt " + attemptId
                    + " did not advance past " + currentAttemptId);
            return;
        }
        currentAttemptId = attemptId;
        nextExpectedFrameIndex = 0;
        attemptFirstSeenMillis = wallClockMillis.getAsLong();
        attemptFlagged = false;
        attemptStarted = true;
        previousFrame = null;
        resetCount = 0; lastResetFrame = -120; anchors.clear(); savedAnchors.clear(); resetsByProgress.clear();
        swapTarget = null;
        streamDigest = newDigest();
        sealedStreamHash = null;
    }

    public void onAttemptReset(int attemptId) {
        if (attemptId == currentAttemptId) {
            attemptStarted = false;
            previousFrame = null;
        }
    }

    public Verdict onBatch(GhostPackets.FramesBatch batch) {
        long now = wallClockMillis.getAsLong();
        if (batch.attemptId() < currentAttemptId) {
            return Verdict.DROP;
        }
        if (!attemptStarted || batch.attemptId() != currentAttemptId) {
            return violate("attempt-start", "stream arrived without matching AttemptStart");
        }
        if (batch.startFrameIndex() != nextExpectedFrameIndex) {
            return violate("frame-gap", "expected frame " + nextExpectedFrameIndex
                    + " got " + batch.startFrameIndex());
        }

        long elapsed = Math.max(0, now - attemptFirstSeenMillis);
        long maxProgress = MAX_FRAME_LEAD
                + elapsed * RATE_REFILL_PER_SECOND / 1000;
        long proposedProgress = (long) nextExpectedFrameIndex + batch.frameCount();
        if (proposedProgress > maxProgress) {
            return violate("rate-cap", "attempt " + currentAttemptId + " advanced to "
                    + proposedProgress + " frames after " + elapsed + " ms");
        }

        double availableTokens = rateTokens;
        if (rateLastRefillMillis != Long.MIN_VALUE && now > rateLastRefillMillis) {
            availableTokens = Math.min(RATE_BURST_FRAMES,
                    availableTokens + (now - rateLastRefillMillis)
                            * RATE_REFILL_PER_SECOND / 1000.0);
        }
        if (availableTokens < batch.frameCount()) {
            return violate("rate-cap",
                    "sustained frame rate above " + RATE_REFILL_PER_SECOND + "/s");
        }

        int maxSpeed = profile != null
                ? profile.maxSpeedPxPerFrame()
                : TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME;
        GhostFrame previous = previousFrame;
        var remainingSwap = swapTarget;
        int resets = resetCount, lastReset = lastResetFrame;
        var nextAnchors = new java.util.HashMap<>(anchors);
        var nextSavedAnchors = new java.util.HashSet<>(savedAnchors);
        var nextResets = new java.util.HashMap<>(resetsByProgress);
        byte[] frameData = batch.frameData();
        for (int i = 0; i < batch.frameCount(); i++) {
            GhostFrame frame;
            try { frame = GhostFrameCodec.decode(frameData, i * GhostFrameCodec.BYTES); }
            catch (IllegalArgumentException invalid) { return violate("pose", "invalid ghost pose bits"); }
            int index = batch.startFrameIndex() + i;
            boolean reset = frame.respawnReset();
            boolean arena = arenaRespawns;
            int anchorKey = arena ? 0 : frame.progress();
            if (reset) {
                GhostFrame anchor = nextAnchors.get(anchorKey);
                // Activation samples the player, whereas SaveData stores the post centre:
                // Obj79_CheckActivation (s2.asm:44639-44643), Obj79_SaveData
                // (s2.asm:44738-44739), Obj79_LoadData (s2.asm:44778-44779).
                // S3K: sub_2D028 (sonic3k.asm:61655-61663), sub_2D164
                // (sonic3k.asm:61743-61746). First return resolves that activation
                // envelope to a saved centre; subsequent returns use the fixed centre.
                boolean activationAnchor = !arena && !nextSavedAnchors.contains(anchorKey);
                int xTolerance = maxSpeed + (activationAnchor ? 8 : 0);
                int yTolerance = maxSpeed + (activationAnchor ? 64 : 0);
                if (!respawnsEnabled || previous == null || anchor == null || resets >= 64
                        || nextResets.getOrDefault(frame.progress(), 0) >= MAX_RESETS_PER_PROGRESS
                        || index - lastReset < 120 || frame.progress() > previous.progress()
                        || Math.abs(TrackRules.delta(frame.x() - anchor.x(), profile == null ? 0 : profile.wrapWidthPx())) > xTolerance
                        || Math.abs(TrackRules.delta(frame.y() - anchor.y(), profile == null ? 0 : profile.wrapHeightPx())) > yTolerance)
                    return violate("respawn-reset", "unbounded or unanchored respawn");
                if (activationAnchor) { nextAnchors.put(anchorKey, frame); nextSavedAnchors.add(anchorKey); }
                resets++; lastReset = index;
                nextResets.merge(frame.progress(), 1, Integer::sum);
            }
            if (previous != null && !reset && frame.progress() < previous.progress() && remainingSwap == null)
                return violate("progress", "progress moved backwards without respawn or host swap");
            boolean arenaSwap = profile != null && profile.lapCount() > 0;
            boolean reachedSwap = remainingSwap != null && index >= swapFirst && (arenaSwap
                    ? frame.shoeKind() == remainingSwap.shoeKind() : Math.abs(TrackRules.delta(frame.x() - remainingSwap.centreX(), profile == null ? 0 : profile.wrapWidthPx())) <= maxSpeed
                    && Math.abs(TrackRules.delta(frame.y() - remainingSwap.centreY(), profile == null ? 0 : profile.wrapHeightPx())) <= maxSpeed);
            if (reachedSwap) remainingSwap = null;
            if (remainingSwap != null && index > swapLast) {
                attemptFlagged = true;
                violationCount++;
                sink.onViolation("swap-ignored", "stream never reached host swap target");
                remainingSwap = null;
            }
            if (previous != null && !reset) {
                int dx = Math.abs(TrackRules.delta(frame.x() - previous.x(), profile == null ? 0 : profile.wrapWidthPx()));
                int dy = Math.abs(TrackRules.delta(frame.y() - previous.y(), profile == null ? 0 : profile.wrapHeightPx()));
                if (dx > maxSpeed || dy > maxSpeed) {
                    if (!reachedSwap || index > swapLast) return violate("speed",
                            "delta " + dx + "," + dy + " exceeds " + maxSpeed);
                }
            }
            if (profile != null
                    && (frame.x() > profile.levelWidthPx()
                    + TrackValidationProfile.BOUNDS_MARGIN_PX
                    || frame.y() > profile.levelHeightPx()
                    + TrackValidationProfile.BOUNDS_MARGIN_PX)) {
                return violate("bounds",
                        "position " + frame.x() + "," + frame.y() + " outside level");
            }
            nextAnchors.putIfAbsent(anchorKey, frame);
            previous = frame;
        }
        swapTarget = remainingSwap;
        resetCount = resets; lastResetFrame = lastReset;
        savedAnchors.clear(); savedAnchors.addAll(nextSavedAnchors);
        resetsByProgress.clear(); resetsByProgress.putAll(nextResets); anchors.clear(); anchors.putAll(nextAnchors);
        previousFrame = previous;
        streamDigest.update(frameData);
        nextExpectedFrameIndex = batch.startFrameIndex() + batch.frameCount();
        rateTokens = availableTokens - batch.frameCount();
        if (rateLastRefillMillis == Long.MIN_VALUE || now > rateLastRefillMillis) {
            rateLastRefillMillis = now;
        }

        if (pacingFlagged(now)) {
            return violationCount >= KICK_THRESHOLD ? Verdict.KICK : Verdict.ACCEPT_FLAGGED;
        }
        return Verdict.ACCEPT;
    }

    /** True only when the current attempt has an unflagged spawn-to-finish stream. */
    public boolean hasFinishEvidence(int attemptId, int finishFrame,
                                     String claimedStreamHashHex) {
        long now = wallClockMillis.getAsLong();
        if (!attemptStarted || attemptId != currentAttemptId || finishFrame < 0
                || nextExpectedFrameIndex != finishFrame + 1) {
            violate("finish-evidence", "attempt " + attemptId + " expected through frame "
                    + finishFrame + " but received " + nextExpectedFrameIndex);
            return false;
        }
        if (pacingFlagged(now) || attemptFlagged) {
            return false;
        }
        if (sealedStreamHash == null) {
            sealedStreamHash = streamDigest.digest();
        }
        byte[] claimed;
        try {
            if (claimedStreamHashHex == null || claimedStreamHashHex.length() != 64) {
                throw new IllegalArgumentException("not a SHA-256 hex digest");
            }
            claimed = HexFormat.of().parseHex(claimedStreamHashHex);
        } catch (IllegalArgumentException invalidHash) {
            violate("stream-hash", "malformed claimed stream hash");
            return false;
        }
        if (!MessageDigest.isEqual(sealedStreamHash, claimed)) {
            violate("stream-hash", "claimed stream hash does not match accepted frames");
            return false;
        }
        return true;
    }

    boolean plausibleClockStart(int clockStart, long scheduledGo) {
        // Allow two seconds of transport/load skew, but never discard an arbitrary
        // prefix of the hub's contiguous validated stream as an alleged countdown.
        long lead = Math.max(0, scheduledGo - attemptFirstSeenMillis);
        return clockStart <= MAX_FRAME_LEAD + (lead + 2000) * RATE_REFILL_PER_SECOND / 1000;
    }

    public boolean isAttemptFlagged() {
        return attemptFlagged;
    }

    public int violationCount() {
        return violationCount;
    }

    private boolean pacingFlagged(long now) {
        long elapsed = Math.max(0, now - attemptFirstSeenMillis);
        if (elapsed <= PACING_WARMUP_MILLIS
                || nextExpectedFrameIndex >= elapsed * PACING_MIN_FPS / 1000) {
            return attemptFlagged;
        }
        if (!attemptFlagged) {
            attemptFlagged = true;
            violationCount++;
            sink.onViolation("pacing", "attempt " + currentAttemptId + " at "
                    + nextExpectedFrameIndex * 1000L / Math.max(elapsed, 1) + " fps");
        }
        return true;
    }

    private Verdict violate(String kind, String detail) {
        violationCount++;
        sink.onViolation(kind, detail);
        return violationCount >= KICK_THRESHOLD ? Verdict.KICK : Verdict.DROP;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
