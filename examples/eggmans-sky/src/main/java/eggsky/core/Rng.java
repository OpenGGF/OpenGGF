package eggsky.core;

/**
 * A small deterministic random stream (SplitMix64). Everything procedural in the galaxy is
 * derived from seeds with {@link #hash}, so the same seed always rebuilds the same star,
 * planet, creature or rock: nothing generated needs saving, only what the player changed.
 */
public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    /** A well-mixed 64-bit value from {@code value} (the SplitMix64 finaliser). */
    public static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A seed for a child of {@code parent} named by {@code salt} (and an index). */
    public static long hash(long parent, long salt) {
        return mix(parent * 0x2545F4914F6CDD1DL + mix(salt));
    }

    public static long hash(long parent, long salt, long index) {
        return hash(hash(parent, salt), index);
    }

    /** A child stream; the parent advances once. */
    public Rng fork(long salt) {
        return new Rng(hash(nextLong(), salt));
    }

    public long nextLong() {
        state += 0x9E3779B97F4A7C15L;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 0 to {@code bound - 1}; 0 when {@code bound} is not positive. */
    public int nextInt(int bound) {
        if (bound <= 1) {
            return 0;
        }
        return (int) Math.floorMod(nextLong() >>> 1, (long) bound);
    }

    /** {@code min} to {@code max} inclusive. */
    public int range(int min, int max) {
        return max <= min ? min : min + nextInt(max - min + 1);
    }

    /** 0 (inclusive) to 1 (exclusive). */
    public float nextFloat() {
        return (nextLong() >>> 40) / (float) (1L << 24);
    }

    public float range(float min, float max) {
        return min + (max - min) * nextFloat();
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean chance(double probability) {
        return nextDouble() < probability;
    }

    /** An index chosen with probability proportional to {@code weights}; -1 when all are zero. */
    public int weighted(int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return -1;
        }
        int pick = nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            pick -= Math.max(0, weights[i]);
            if (pick < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /** Gaussian-ish value in about -1..1 (sum of three uniforms). */
    public float soft() {
        return (nextFloat() + nextFloat() + nextFloat()) / 1.5f - 1f;
    }

    /** Smooth 1D value noise in -1..1 for coordinate {@code x} of a stream named by {@code seed}. */
    public static float noise1(long seed, float x) {
        int i = (int) Math.floor(x);
        float f = x - i;
        float a = lattice(seed, i);
        float b = lattice(seed, i + 1);
        float t = f * f * (3 - 2 * f);
        return a + (b - a) * t;
    }

    /** Fractal 1D noise: {@code octaves} layers, each half the size and amplitude. */
    public static float fractal1(long seed, float x, int octaves) {
        float sum = 0;
        float amp = 1;
        float norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += noise1(seed + o * 7919L, x) * amp;
            norm += amp;
            amp *= 0.5f;
            x *= 2;
        }
        return sum / norm;
    }

    /** Smooth 2D value noise in -1..1. */
    public static float noise2(long seed, float x, float y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        float tx = fx * fx * (3 - 2 * fx);
        float ty = fy * fy * (3 - 2 * fy);
        float a = lattice2(seed, ix, iy);
        float b = lattice2(seed, ix + 1, iy);
        float c = lattice2(seed, ix, iy + 1);
        float d = lattice2(seed, ix + 1, iy + 1);
        float top = a + (b - a) * tx;
        float bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    /** 2D noise that repeats every {@code period} units along x (for wrapping planets). */
    public static float noise2Wrapped(long seed, float x, float y, int period) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        float tx = fx * fx * (3 - 2 * fx);
        float ty = fy * fy * (3 - 2 * fy);
        int x0 = Math.floorMod(ix, period);
        int x1 = Math.floorMod(ix + 1, period);
        float a = lattice2(seed, x0, iy);
        float b = lattice2(seed, x1, iy);
        float c = lattice2(seed, x0, iy + 1);
        float d = lattice2(seed, x1, iy + 1);
        float top = a + (b - a) * tx;
        float bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    private static float lattice(long seed, int i) {
        return ((mix(seed * 31 + i) >>> 40) / (float) (1L << 24)) * 2f - 1f;
    }

    private static float lattice2(long seed, int x, int y) {
        return ((mix(seed * 31 + x * 0x9E3779B1L + y * 0x85EBCA77L) >>> 40) / (float) (1L << 24)) * 2f - 1f;
    }
}
