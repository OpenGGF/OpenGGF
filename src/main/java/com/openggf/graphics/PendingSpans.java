package com.openggf.graphics;

/**
 * Up to {@code capacity} disjoint half-open spans {@code [first, end)} awaiting upload.
 * Overlapping or adjacent spans join; past the capacity the two closest spans merge,
 * so the set always covers every added index with at most {@code capacity} uploads.
 */
final class PendingSpans {
    private final int capacity;
    private final int[] first;
    private final int[] end;
    private int count;

    PendingSpans(int capacity) {
        this.capacity = capacity;
        this.first = new int[capacity + 1];
        this.end = new int[capacity + 1];
    }

    int count() {
        return count;
    }

    int first(int index) {
        return first[index];
    }

    int end(int index) {
        return end[index];
    }

    /** Number of indices covered. */
    int total() {
        int total = 0;
        for (int i = 0; i < count; i++) total += end[i] - first[i];
        return total;
    }

    void clear() {
        count = 0;
    }

    void add(int spanFirst, int spanEnd) {
        int a = spanFirst;
        int b = spanEnd;
        for (int i = count - 1; i >= 0; i--) {
            if (first[i] <= b && a <= end[i]) {
                a = Math.min(a, first[i]);
                b = Math.max(b, end[i]);
                remove(i);
            }
        }
        first[count] = a;
        end[count] = b;
        count++;
        if (count > capacity) mergeClosest();
    }

    private void remove(int index) {
        count--;
        first[index] = first[count];
        end[index] = end[count];
    }

    private void mergeClosest() {
        int bestA = 0;
        int bestB = 1;
        int bestGap = Integer.MAX_VALUE;
        for (int a = 0; a < count; a++) {
            for (int b = a + 1; b < count; b++) {
                int gap = Math.max(first[a], first[b]) - Math.min(end[a], end[b]);
                if (gap < bestGap) {
                    bestGap = gap;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        first[bestA] = Math.min(first[bestA], first[bestB]);
        end[bestA] = Math.max(end[bestA], end[bestB]);
        remove(bestB);
    }
}
