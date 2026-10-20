package sonicbros.sim;

/** A small seeded random stream (xorshift64*), owned by one match or CPU so replays repeat. */
public final class Rng {
    private long state;

    public Rng(long seed) {
        state = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        for (int i = 0; i < 4; i++) next();
    }

    private long next() {
        state ^= state >>> 12;
        state ^= state << 25;
        state ^= state >>> 27;
        return state * 0x2545F4914F6CDD1DL;
    }

    /** 0 (inclusive) to {@code bound} (exclusive). */
    public int nextInt(int bound) {
        if (bound <= 0) return 0;
        return (int) Long.remainderUnsigned(next(), bound);
    }

    public double nextDouble() {
        return (next() >>> 11) * 0x1.0p-53;
    }

    public boolean chance(double p) { return nextDouble() < p; }
}
