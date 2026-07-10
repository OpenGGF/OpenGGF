package com.openggf.net.hub;

import com.openggf.ghost.GhostFrame;
import com.openggf.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;

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
    private GhostFrame previousFrame;
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

    public Verdict onBatch(GhostPackets.FramesBatch batch) {
        long now = wallClockMillis.getAsLong();
        if (batch.attemptId() < currentAttemptId) {
            return Verdict.DROP;
        }
        if (batch.attemptId() > currentAttemptId) {
            if (batch.startFrameIndex() != 0) {
                return violate("attempt-start", "attempt " + batch.attemptId()
                        + " began at frame " + batch.startFrameIndex());
            }
            currentAttemptId = batch.attemptId();
            nextExpectedFrameIndex = 0;
            attemptFirstSeenMillis = now;
            attemptFlagged = false;
            previousFrame = null;
        } else if (batch.startFrameIndex() != nextExpectedFrameIndex) {
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
        nextExpectedFrameIndex = batch.startFrameIndex() + batch.frameCount();

        long elapsed = Math.max(0, now - attemptFirstSeenMillis);
        if (elapsed > PACING_WARMUP_MILLIS
                && nextExpectedFrameIndex < elapsed * PACING_MIN_FPS / 1000) {
            if (!attemptFlagged) {
                attemptFlagged = true;
                violationCount++;
                sink.onViolation("pacing", "attempt " + currentAttemptId + " at "
                        + nextExpectedFrameIndex * 1000L / Math.max(elapsed, 1) + " fps");
            }
            return violationCount >= KICK_THRESHOLD ? Verdict.KICK : Verdict.ACCEPT_FLAGGED;
        }
        return Verdict.ACCEPT;
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

    private Verdict violate(String kind, String detail) {
        violationCount++;
        sink.onViolation(kind, detail);
        return violationCount >= KICK_THRESHOLD ? Verdict.KICK : Verdict.DROP;
    }
}
