package com.openggf.net.hub;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
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
    public static final long PACING_WARMUP_MILLIS = 3000;
    public static final int PACING_MIN_FPS = 54;

    private TrackValidationProfile profile;
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

        refillTokens(now);
        if (rateTokens < batch.frameCount()) {
            return violate("rate-cap",
                    "sustained frame rate above " + RATE_REFILL_PER_SECOND + "/s");
        }
        rateTokens -= batch.frameCount();

        int maxSpeed = profile != null
                ? profile.maxSpeedPxPerFrame()
                : TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME;
        GhostFrame previous = previousFrame;
        byte[] frameData = batch.frameData();
        for (int i = 0; i < batch.frameCount(); i++) {
            GhostFrame frame = GhostFrameCodec.decode(
                    frameData, i * GhostFrameCodec.BYTES);
            if (previous != null) {
                int dx = Math.abs(frame.x() - previous.x());
                int dy = Math.abs(frame.y() - previous.y());
                if (dx > maxSpeed || dy > maxSpeed) {
                    return violate("speed",
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
            previous = frame;
        }
        previousFrame = previous;
        streamDigest.update(frameData);
        nextExpectedFrameIndex = batch.startFrameIndex() + batch.frameCount();

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

    public boolean isAttemptFlagged() {
        return attemptFlagged;
    }

    public int violationCount() {
        return violationCount;
    }

    private void refillTokens(long now) {
        if (rateLastRefillMillis != Long.MIN_VALUE && now > rateLastRefillMillis) {
            rateTokens = Math.min(RATE_BURST_FRAMES,
                    rateTokens + (now - rateLastRefillMillis)
                            * RATE_REFILL_PER_SECOND / 1000.0);
        }
        if (rateLastRefillMillis == Long.MIN_VALUE || now > rateLastRefillMillis) {
            rateLastRefillMillis = now;
        }
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
