package towerdefense.core;

/** Transient visual facts emitted by the rules. Drawing only reads these values. */
public final class Effect {
    final String type;
    final double x, y, toX, toY;
    final int color, lifetime;
    int age;

    Effect(String type, double x, double y, double toX, double toY, int color, int lifetime) {
        this.type = type; this.x = x; this.y = y; this.toX = toX; this.toY = toY;
        this.color = color; this.lifetime = lifetime;
    }

    public String type() { return type; }
    public double x() { return x; }
    public double y() { return y; }
    public double toX() { return toX; }
    public double toY() { return toY; }
    public int color() { return color; }
    public int age() { return age; }
    public double remaining() { return 1.0 - (double) age / lifetime; }
}
