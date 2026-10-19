package sonicbros.sim;

import java.util.List;

/**
 * A stage's solid geometry in world pixels (y grows downward, the main floor sits near y = 0):
 * one main island with a real floor line and underside, pass-through platforms (some moving),
 * the blast lines that KO, and where fighters start and respawn.
 */
public final class Arena {
    /**
     * A pass-through platform: stand on it from above, jump up through it, hold down to drop.
     * It moves by {@code ax, ay} pixels over a sine of {@code period} frames ({@code period} 0
     * keeps it still).
     */
    public record Platform(double x0, double x1, double y, double ax, double ay, int period, double phase) {
        public double dx(long frame) { return period == 0 ? 0 : ax * Math.sin(2 * Math.PI * frame / period + phase); }

        public double dy(long frame) { return period == 0 ? 0 : ay * Math.sin(2 * Math.PI * frame / period + phase); }

        public double left(long frame) { return x0 + dx(frame); }

        public double right(long frame) { return x1 + dx(frame); }

        public double top(long frame) { return y + dy(frame); }
    }

    public final double left;
    private final double[] surface;
    private final double[] bottom;
    public final List<Platform> platforms;
    public final double blastLeft;
    public final double blastRight;
    public final double blastTop;
    public final double blastBottom;
    public final double[] spawnX;

    /**
     * @param left    world x of the island's first column
     * @param surface floor y of each island column
     * @param bottom  underside y of each island column
     */
    public Arena(double left, double[] surface, double[] bottom, List<Platform> platforms, double blastLeft,
            double blastRight, double blastTop, double blastBottom, double[] spawnX) {
        this.left = left;
        this.surface = surface.clone();
        this.bottom = bottom.clone();
        this.platforms = List.copyOf(platforms);
        this.blastLeft = blastLeft;
        this.blastRight = blastRight;
        this.blastTop = blastTop;
        this.blastBottom = blastBottom;
        this.spawnX = spawnX.clone();
    }

    /** A flat test island {@code width} wide centred on 0, with Battlefield-like platforms. */
    public static Arena flat(double width, boolean platforms) {
        int w = (int) width;
        double[] s = new double[w];
        double[] b = new double[w];
        for (int i = 0; i < w; i++) {
            double u = (i + 0.5) / w;
            s[i] = 0;
            b[i] = 20 + 50 * Math.sqrt(Math.max(0, 1 - Math.pow(2 * u - 1, 2)));
        }
        List<Platform> list = platforms
                ? List.of(new Platform(-100, -40, -52, 0, 0, 0, 0), new Platform(40, 100, -52, 0, 0, 0, 0),
                        new Platform(-30, 30, -100, 0, 0, 0, 0))
                : List.of();
        return new Arena(-w / 2.0, s, b, list, -w / 2.0 - 190, w / 2.0 + 190, -300, 190,
                new double[] {-w * 0.3, w * 0.3, -w * 0.12, w * 0.12});
    }

    public double right() { return left + surface.length; }

    public double width() { return surface.length; }

    public boolean over(double x) { return x >= left && x < right(); }

    private int column(double x) { return (int) Math.max(0, Math.min(surface.length - 1, Math.floor(x - left))); }

    /** Floor y at x (clamped to the island's ends). */
    public double surfaceAt(double x) { return surface[column(x)]; }

    /** Underside y at x (clamped). */
    public double bottomAt(double x) { return bottom[column(x)]; }

    public double ledgeY(int side) { return side < 0 ? surface[0] : surface[surface.length - 1]; }

    public double ledgeX(int side) { return side < 0 ? left : right(); }

    /** Average floor height, for cameras and spawning. */
    public double floorLevel() {
        double sum = 0;
        for (double v : surface) sum += v;
        return sum / surface.length;
    }

    public double copySurface(int i) { return surface[i]; }

    public double copyBottom(int i) { return bottom[i]; }
}
