package com.openggf.game.sonic2.competition.specialstage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Native SSObjectsManager cursor publication and allocation-failure behavior. */
public final class CompetitiveHalfpipeObjectStream {
    public interface Factory<T> {
        T item(boolean bomb, int depth, int angle);
        T marker(int raw);
    }

    public record State(int stage, int lastSegment, int publishedCursor) { }

    private static final int STAGE_COUNT = 7;
    private final byte[] bytes;
    private final int[] starts = new int[STAGE_COUNT];
    private int stage = -1;
    private int lastSegment = 0xFF;
    private int publishedCursor;

    public CompetitiveHalfpipeObjectStream(CompetitiveHalfpipeRomData data) {
        Objects.requireNonNull(data, "data");
        try {
            bytes = data.loader().getObjectLocations().clone();
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot load native special-stage object stream", failure);
        }
        if (bytes.length < STAGE_COUNT * 2) throw new IllegalArgumentException("Missing native object stream headers");
        for (int index = 0; index < starts.length; index++) {
            starts[index] = ((bytes[index * 2] & 0xFF) << 8) | (bytes[index * 2 + 1] & 0xFF);
            if (starts[index] < STAGE_COUNT * 2 || starts[index] >= bytes.length) {
                throw new IllegalArgumentException("Invalid native object stream header for stage " + index);
            }
        }
    }

    public void load(int stage) {
        Objects.checkIndex(stage, STAGE_COUNT);
        this.stage = stage;
        lastSegment = 0xFF;
        publishedCursor = starts[stage];
    }

    public <T> void step(int segment, int drawingIndex, int segmentLength,
                         CompetitiveHalfpipeObjectRam<T> ram, Factory<T> factory) {
        Objects.requireNonNull(ram, "ram");
        Objects.requireNonNull(factory, "factory");
        if (stage < 0) throw new IllegalStateException("No native special-stage object stream loaded");
        int nativeSegment = segment & 0xFF;
        if (drawingIndex != 4 || nativeSegment == lastSegment) return;
        if (segmentLength < 0 || segmentLength > 0xFF) throw new IllegalArgumentException("Invalid native segment length");

        // SSObjectsManager (s2.asm:6935-6955) publishes LastSegment before
        // allocation. A full table therefore cannot retry during this segment.
        lastSegment = nativeSegment;
        int cursor = publishedCursor;
        while (true) {
            // This also precedes reading FF: an empty terminator needs a free
            // candidate, although it never writes an object ID into that slot.
            int candidate = ram.findFree();
            if (candidate < 0) return;
            int value = read(cursor++);
            if (value >= 0x80) {
                // Only a negative marker publishes the local pointer. Keep
                // actual ROM padding and following groups readable after FD.
                publishedCursor = cursor;
                if (value != 0xFF) ram.replace(candidate, factory.marker(value));
                return;
            }
            int angle = read(cursor++);
            int depth = (segmentLength + (value & 0x3F)) * 4;
            ram.replace(candidate, factory.item((value & 0x40) != 0, depth, angle));
            // On a later failure the already spawned prefix remains alive,
            // but publishedCursor still points to the beginning of its group.
            // SSObjectsManager (s2.asm:6952-7000), shipped FixBugs=0 behavior.
        }
    }

    public State capture() { return new State(stage, lastSegment, publishedCursor); }

    public void restore(State state) {
        Objects.requireNonNull(state, "state");
        if (state.stage() == -1) {
            if (state.lastSegment() != 0xFF || state.publishedCursor() != 0) {
                throw new IllegalArgumentException("Invalid unloaded native object stream state");
            }
        } else {
            if (state.stage() < 0 || state.stage() >= STAGE_COUNT
                    || state.lastSegment() < 0 || state.lastSegment() > 0xFF
                    || state.publishedCursor() < starts[state.stage()]
                    || state.publishedCursor() > bytes.length) {
                throw new IllegalArgumentException("Invalid native object stream state");
            }
        }
        stage = state.stage();
        lastSegment = state.lastSegment();
        publishedCursor = state.publishedCursor();
    }

    private int read(int cursor) {
        if (cursor < 0 || cursor >= bytes.length) {
            throw new IllegalStateException("Native object stream exhausted at " + cursor);
        }
        return bytes[cursor] & 0xFF;
    }
}
