package com.openggf.net.client;

import com.openggf.game.ghost.GhostFrame;
import com.openggf.game.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Adaptive jitter-buffered playback for one remote cosmetic ghost. */
public final class RemoteGhostPlayback {
    public static final int INITIAL_DELAY_FRAMES = 9;
    public static final int MIN_DELAY_FRAMES = 6;
    public static final int MAX_DELAY_FRAMES = 30;
    public static final int CATCHUP_SLACK_FRAMES = 15;
    public static final int SNAP_BACKLOG_FRAMES = 60;
    public static final int EXTRAPOLATE_MAX_FRAMES = 6;
    public static final int DELAY_GROW_FRAMES = 3;
    public static final int DELAY_SHRINK_CLEAN_STREAK = 600;

    public record RenderState(GhostFrame frame, float opacityScale, boolean snapped) {
    }

    private final Map<Integer, GhostFrame> frames = new HashMap<>();
    private int attemptId = Integer.MIN_VALUE;
    private int newestIndex = -1;
    private int cursor = -1;
    private int delay = INITIAL_DELAY_FRAMES;
    private int extrapolated;
    private int velocityX;
    private int velocityY;
    private int cleanStreak;
    private boolean stalled;

    public void onEntry(GhostPackets.AggregateEntry entry) {
        if (entry.attemptId() < attemptId) {
            return;
        }
        if (entry.attemptId() > attemptId) {
            attemptId = entry.attemptId();
            frames.clear();
            newestIndex = -1;
            cursor = -1;
            extrapolated = 0;
            velocityX = 0;
            velocityY = 0;
            cleanStreak = 0;
            stalled = false;
        }
        byte[] data = entry.frameData();
        for (int i = 0; i < entry.frameCount(); i++) {
            int index = entry.startFrameIndex() + i;
            frames.put(index, GhostFrameCodec.decode(data, i * GhostFrameCodec.BYTES));
            newestIndex = Math.max(newestIndex, index);
        }
    }

    public Optional<RenderState> advance() {
        if (cursor < 0) {
            if (newestIndex + 1 < delay) {
                return Optional.empty();
            }
            cursor = 0;
            return clean(cursor, false);
        }
        int backlog = newestIndex - cursor;
        if (backlog > SNAP_BACKLOG_FRAMES) {
            cursor = newestIndex - delay;
            return clean(cursor, true);
        }
        int step = backlog > delay + CATCHUP_SLACK_FRAMES ? 2 : 1;
        if (cursor + step <= newestIndex) {
            cursor += step;
            return clean(cursor, false);
        }
        return dry();
    }

    public int delayFrames() {
        return delay;
    }

    public boolean isStalled() {
        return stalled;
    }

    private Optional<RenderState> clean(int index, boolean snapped) {
        GhostFrame frame = frames.get(index);
        if (frame == null) {
            return dry();
        }
        GhostFrame previous = frames.get(index - 1);
        if (previous != null) {
            velocityX = frame.x() - previous.x();
            velocityY = frame.y() - previous.y();
        }
        extrapolated = 0;
        stalled = false;
        if (++cleanStreak >= DELAY_SHRINK_CLEAN_STREAK) {
            cleanStreak = 0;
            delay = Math.max(MIN_DELAY_FRAMES, delay - 1);
        }
        frames.keySet().removeIf(frameIndex -> frameIndex < index - 4);
        return Optional.of(new RenderState(frame, 1.0f, snapped));
    }

    private Optional<RenderState> dry() {
        GhostFrame last = frames.get(cursor);
        if (last == null) {
            return Optional.empty();
        }
        cleanStreak = 0;
        delay = Math.min(MAX_DELAY_FRAMES, delay + DELAY_GROW_FRAMES);
        if (extrapolated < EXTRAPOLATE_MAX_FRAMES) {
            extrapolated++;
            GhostFrame projected = new GhostFrame(
                    clampU16(last.x() + velocityX * extrapolated),
                    clampU16(last.y() + velocityY * extrapolated),
                    last.mappingFrame(), last.hFlip(), last.vFlip(), last.finished(),
                    last.priorityBucket(), last.highPriority());
            return Optional.of(new RenderState(projected, 1.0f, false));
        }
        stalled = true;
        return Optional.of(new RenderState(last, 0.5f, false));
    }

    private static int clampU16(int value) {
        return Math.min(Math.max(value, 0), 0xFFFF);
    }
}
