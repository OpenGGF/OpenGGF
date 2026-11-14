package com.openggf.graphics;

import java.util.Arrays;

/**
 * The atlas's latest CPU sample of each pattern id, stored as the four packed
 * words of a {@link SpritePresentation.PatternVersion} without allocating on write.
 *
 * <p>Every DPLC/AniPLC upload records a sample, so a {@code HashMap<Integer,
 * PatternVersion>} boxed the id and allocated a record per uploaded tile (about 5%
 * of steady-state allocation, 12% in SOZ). Readers are rare (SHADOW_15 validation
 * and frozen presentation capture): {@link #get} creates the record on first read
 * of a changed sample and returns that same instance until the sample changes.
 *
 * <p>Open addressing with linear probing and backward-shift deletion, so removals
 * leave no tombstones. Not thread-safe; owned by one {@link PatternAtlas}.
 */
final class PatternSampleTable {
    private static final int INITIAL_CAPACITY = 256;

    private int[] keys = new int[INITIAL_CAPACITY];
    private boolean[] used = new boolean[INITIAL_CAPACITY];
    // Four packed words per slot, in PatternVersion component order a, b, c, d.
    private long[] words = new long[INITIAL_CAPACITY * 4];
    private SpritePresentation.PatternVersion[] versions =
            new SpritePresentation.PatternVersion[INITIAL_CAPACITY];
    private int size;

    /** Records the 8x8 indexed pixels in {@code pixels[0..63]} as the sample for {@code patternId}. */
    void put(int patternId, byte[] pixels) {
        long a = pack(pixels, 0);
        long b = pack(pixels, 16);
        long c = pack(pixels, 32);
        long d = pack(pixels, 48);
        int slot = slotFor(patternId);
        int w = slot * 4;
        if (words[w] != a || words[w + 1] != b || words[w + 2] != c || words[w + 3] != d) {
            words[w] = a;
            words[w + 1] = b;
            words[w + 2] = c;
            words[w + 3] = d;
            versions[slot] = null;
        }
    }

    /** Records an existing version (frozen replay) and keeps it as the instance readers receive. */
    void put(int patternId, SpritePresentation.PatternVersion version) {
        int slot = slotFor(patternId);
        int w = slot * 4;
        words[w] = version.a();
        words[w + 1] = version.b();
        words[w + 2] = version.c();
        words[w + 3] = version.d();
        versions[slot] = version;
    }

    SpritePresentation.PatternVersion get(int patternId) {
        int slot = find(patternId);
        if (slot < 0) {
            return null;
        }
        SpritePresentation.PatternVersion version = versions[slot];
        if (version == null) {
            int w = slot * 4;
            version = new SpritePresentation.PatternVersion(words[w], words[w + 1], words[w + 2], words[w + 3]);
            versions[slot] = version;
        }
        return version;
    }

    void remove(int patternId) {
        int slot = find(patternId);
        if (slot < 0) {
            return;
        }
        int mask = keys.length - 1;
        // Backward-shift deletion: pull later members of the probe run into the hole.
        int hole = slot;
        for (int next = (hole + 1) & mask; used[next]; next = (next + 1) & mask) {
            int home = indexFor(keys[next], mask);
            // Move the entry only if the hole lies on its probe path (cyclically in [home, next)).
            if (((next - home) & mask) >= ((next - hole) & mask)) {
                move(next, hole);
                hole = next;
            }
        }
        used[hole] = false;
        versions[hole] = null;
        size--;
    }

    void clear() {
        Arrays.fill(used, false);
        Arrays.fill(versions, null);
        size = 0;
    }

    int size() {
        return size;
    }

    /** Must match {@link SpritePresentation#patternVersion(byte[])}. */
    static long pack(byte[] pixels, int first) {
        long value = 0;
        for (int i = 0; i < 16; i++) {
            value |= (long) (pixels[first + i] & 15) << (4 * i);
        }
        return value;
    }

    private int find(int patternId) {
        int mask = keys.length - 1;
        for (int i = indexFor(patternId, mask); used[i]; i = (i + 1) & mask) {
            if (keys[i] == patternId) {
                return i;
            }
        }
        return -1;
    }

    /** Slot holding {@code patternId}, inserting it (words zeroed, no version) when absent. */
    private int slotFor(int patternId) {
        int existing = find(patternId);
        if (existing >= 0) {
            return existing;
        }
        if ((size + 1) * 2 > keys.length) {
            grow();
        }
        int mask = keys.length - 1;
        int i = indexFor(patternId, mask);
        while (used[i]) {
            i = (i + 1) & mask;
        }
        used[i] = true;
        keys[i] = patternId;
        int w = i * 4;
        words[w] = 0;
        words[w + 1] = 0;
        words[w + 2] = 0;
        words[w + 3] = 0;
        versions[i] = null;
        size++;
        return i;
    }

    private void move(int from, int to) {
        keys[to] = keys[from];
        used[to] = true;
        System.arraycopy(words, from * 4, words, to * 4, 4);
        versions[to] = versions[from];
    }

    private void grow() {
        int[] oldKeys = keys;
        boolean[] oldUsed = used;
        long[] oldWords = words;
        SpritePresentation.PatternVersion[] oldVersions = versions;
        int capacity = oldKeys.length * 2;
        keys = new int[capacity];
        used = new boolean[capacity];
        words = new long[capacity * 4];
        versions = new SpritePresentation.PatternVersion[capacity];
        int mask = capacity - 1;
        for (int old = 0; old < oldKeys.length; old++) {
            if (!oldUsed[old]) {
                continue;
            }
            int i = indexFor(oldKeys[old], mask);
            while (used[i]) {
                i = (i + 1) & mask;
            }
            used[i] = true;
            keys[i] = oldKeys[old];
            System.arraycopy(oldWords, old * 4, words, i * 4, 4);
            versions[i] = oldVersions[old];
        }
    }

    private static int indexFor(int key, int mask) {
        int h = key * 0x9E3779B9;
        return (h ^ (h >>> 16)) & mask;
    }
}
