package com.openggf.level.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Bake plan for one NATIVE widescreen dormant pose (see {@link DormantObjectLayer}).
 *
 * <p>A first copy runs for up to {@code maxUpdates} updates, or until its position
 * changes. Its render outputs are searched for the shortest period {@code P} such that
 * every output equals the one {@code P} updates earlier; a held first frame is not a
 * period of 1 until the whole window agrees. A period above 1 seen twice in full ends the
 * search early. A stationary copy with such a loop keeps one copy per distinct frame of
 * the loop, each stepped to that frame from a fresh construction and checked to reproduce
 * it; a copy whose output never changed is a frozen pose; anything else (it moved, no
 * loop fits, an output could not be compared, a fresh copy disagreed) keeps a single
 * copy after exactly one update. Presentation only: the caller wraps the whole bake in
 * one rewind capture/restore bracket.
 */
final class DormantPoseLoop {
    private DormantPoseLoop() { }

    /** A throwaway copy as the bake sees it. */
    interface Copy {
        /** Runs update number {@code tick} (0 for the first update). */
        void update(int tick);

        /** Render output after the latest update; {@code null} when it cannot be compared. */
        Object signature();

        /** Position after the latest update, packed as {@code x << 32 | y}. */
        long position();
    }

    /**
     * Kept copies and their loop: {@code frames.get(segmentAt[tick % loopLength])} draws at a
     * presentation tick. A frozen pose has one frame and {@code loopLength == 1}.
     */
    record Plan<C>(List<C> frames, int[] segmentAt, int updates) {
        Plan {
            frames = List.copyOf(frames);
            segmentAt = segmentAt.clone();
        }

        int loopLength() { return segmentAt.length; }

        boolean looped() { return segmentAt.length > 1; }
    }

    /** Bakes one spawn; {@code null} when the factory cannot construct a copy. */
    static <C extends Copy> Plan<C> bake(Supplier<C> factory, int maxUpdates) {
        C first = factory.get();
        if (first == null) return null;
        first.update(0);
        int updates = 1;
        Object start = first.signature();
        if (start == null) return frozen(first, updates);
        long origin = first.position();
        List<Object> outputs = new ArrayList<>();
        outputs.add(start);
        int period = 1;
        boolean comparable = true;
        for (int tick = 1; tick < maxUpdates; tick++) {
            first.update(tick);
            updates++;
            Object output = first.position() == origin ? first.signature() : null;
            if (output == null) {
                comparable = false;
                break;
            }
            outputs.add(output);
            while (period <= tick && !periodic(outputs, period)) period++;
            if (period > 1 && period <= tick && tick + 1 >= 2 * period) break;
        }
        int observed = outputs.size();
        if (comparable && period == 1 && observed == maxUpdates) return frozen(first, updates);
        if (!comparable || period == 1 || observed < 2 * period) return frozenFresh(factory, updates);
        int[] segmentAt = new int[period];
        List<Integer> starts = new ArrayList<>();
        for (int tick = 0; tick < period; tick++) {
            if (tick == 0 || !outputs.get(tick).equals(outputs.get(tick - 1))) starts.add(tick);
            segmentAt[tick] = starts.size() - 1;
        }
        // The searching copy is already on one segment; every other one comes from a fresh copy.
        int firstAt = segmentAt[(observed - 1) % period];
        List<C> frames = new ArrayList<>();
        for (int segment = 0; segment < starts.size(); segment++) {
            if (segment == firstAt) {
                frames.add(first);
                continue;
            }
            int tick = starts.get(segment);
            C copy = factory.get();
            if (copy == null) return frozenFresh(factory, updates);
            for (int step = 0; step <= tick; step++) copy.update(step);
            updates += tick + 1;
            if (copy.position() != origin || !Objects.equals(copy.signature(), outputs.get(tick))) {
                // Not reproducible from a fresh copy (RNG, shared state): keep the first frame only.
                return frozenFresh(factory, updates);
            }
            frames.add(copy);
        }
        return new Plan<>(frames, segmentAt, updates);
    }

    private static boolean periodic(List<Object> outputs, int period) {
        for (int tick = period; tick < outputs.size(); tick++) {
            if (!outputs.get(tick).equals(outputs.get(tick - period))) return false;
        }
        return true;
    }

    private static <C extends Copy> Plan<C> frozenFresh(Supplier<C> factory, int updates) {
        C pose = factory.get();
        if (pose == null) return null;
        pose.update(0);
        return frozen(pose, updates + 1);
    }

    private static <C> Plan<C> frozen(C copy, int updates) {
        return new Plan<>(List.of(copy), new int[]{0}, updates);
    }
}
