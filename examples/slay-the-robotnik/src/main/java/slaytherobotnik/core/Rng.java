package slaytherobotnik.core;

import java.util.List;

/**
 * Small deterministic random number generator (SplitMix64).
 *
 * <p>Every random decision in a run draws from a named {@code Rng} owned by
 * {@link RunRngs}, never from {@link java.util.Random} or {@code Math.random()}. The whole
 * state is one {@code long}, so a run can be saved and resumed and the same seed always
 * produces the same map, rewards and fights. Keeping one stream per purpose (as Slay the
 * Spire does) means that, for example, looking at a shop does not change which cards the
 * next combat rewards.
 */
public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    /** Next raw 64-bit value. */
    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform integer in {@code [0, bound)}. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    /** Uniform integer in {@code [min, max]} (both inclusive). */
    public int range(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("empty range " + min + ".." + max);
        }
        return min + nextInt(max - min + 1);
    }

    /** Uniform float in {@code [0, 1)}. */
    public float nextFloat() {
        return (nextLong() >>> 40) / (float) (1 << 24);
    }

    /** True with probability {@code p}. */
    public boolean chance(double p) {
        return nextFloat() < p;
    }

    public boolean nextBoolean() {
        return (nextLong() & 1L) != 0;
    }

    /** A uniformly chosen element; the list must not be empty. */
    public <T> T pick(List<T> items) {
        return items.get(nextInt(items.size()));
    }

    /** Fisher-Yates shuffle in place. */
    public <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            T tmp = items.get(i);
            items.set(i, items.get(j));
            items.set(j, tmp);
        }
    }

    /** The complete generator state, for saving. */
    public long state() {
        return state;
    }

    /** Restores a state captured by {@link #state()}. */
    public void restore(long saved) {
        this.state = saved;
    }

    /** Derives an independent seed from a base seed and a salt (used for per-floor streams). */
    public static long derive(long seed, long salt) {
        Rng mixer = new Rng(seed ^ (salt * 0xD1B54A32D192ED03L));
        mixer.nextLong();
        return mixer.nextLong();
    }
}
