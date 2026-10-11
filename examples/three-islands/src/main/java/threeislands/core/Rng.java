package threeislands.core;

/** A small deterministic xorshift64* generator whose whole state is one saved long. */
public final class Rng {
    private long state;

    public Rng(long seed) {
        state = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
    }

    public long state() {
        return state;
    }

    public void restore(long saved) {
        state = saved == 0 ? 0x9E3779B97F4A7C15L : saved;
    }

    public long nextLong() {
        state ^= state >>> 12;
        state ^= state << 25;
        state ^= state >>> 27;
        return state * 0x2545F4914F6CDD1DL;
    }

    /** Uniform in {@code [0, bound)}; bound must be positive. */
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    /** Uniform in {@code [0, 1)}. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** True with probability {@code percent / 100}. */
    public boolean chance(int percent) {
        return nextInt(100) < percent;
    }
}
