package com.openggf.audio.rewind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class AudioCommandTimeline {
    private final List<AudioTimelineEntry> entries = new ArrayList<>();
    private long currentFrame;
    private int nextOrder;

    public void beginFrame(long frame) {
        if (frame != currentFrame) {
            currentFrame = frame;
            nextOrder = entriesOnFrame(frame);
        }
    }

    public AudioTimelineEntry record(AudioCommand command) {
        Objects.requireNonNull(command, "command");
        AudioTimelineEntry entry = new AudioTimelineEntry(currentFrame, nextOrder++, command);
        entries.add(entry);
        return entry;
    }

    public void discardAfter(long frame) {
        entries.removeIf(entry -> entry.frame() > frame);
        if (currentFrame > frame) {
            currentFrame = frame;
            nextOrder = entriesOnFrame(frame);
        }
    }

    public List<AudioTimelineEntry> entries() {
        return List.copyOf(entries);
    }

    public int entryCount() {
        return entries.size();
    }

    public int forEachEntryFrom(
            int startIndex,
            long targetFrameInclusive,
            Consumer<? super AudioTimelineEntry> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (startIndex < 0) {
            throw new IndexOutOfBoundsException("startIndex out of range: " + startIndex);
        }
        if (startIndex >= entries.size()) {
            return 0;
        }
        int endExclusive = entries.size();
        int visited = 0;
        for (int i = startIndex; i < endExclusive; i++) {
            AudioTimelineEntry entry = entries.get(i);
            if (entry.frame() > targetFrameInclusive) {
                break;
            }
            if (entry.frame() <= targetFrameInclusive) {
                consumer.accept(entry);
                visited++;
            }
        }
        return visited;
    }

    public long currentFrame() {
        return currentFrame;
    }

    public int nextOrder() {
        return nextOrder;
    }

    public void restoreCursor(long frame, int nextOrder) {
        if (nextOrder < 0) {
            throw new IllegalArgumentException("nextOrder must be non-negative");
        }
        this.currentFrame = frame;
        this.nextOrder = nextOrder;
    }

    public void clear() {
        entries.clear();
        currentFrame = 0;
        nextOrder = 0;
    }

    private int entriesOnFrame(long frame) {
        int count = 0;
        for (AudioTimelineEntry entry : entries) {
            if (entry.frame() == frame) {
                count++;
            }
        }
        return count;
    }
}
