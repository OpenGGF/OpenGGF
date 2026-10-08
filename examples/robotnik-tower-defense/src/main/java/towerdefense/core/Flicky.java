package towerdefense.core;

/** One member of the picket, including status timers and the door attack clock. */
public final class Flicky {
    final int id, kind;
    final double maxHp, speed;
    double x = -12, y, hp;
    int slowTicks, peckTicks, sabotageTicks, hitTicks;

    Flicky(int kind, int wave, int id) {
        this.kind = kind;
        this.id = id;
        double healthFactor = switch (kind) {
            case Catalog.SHIELD -> 2.4;
            case Catalog.ORGANISER, Catalog.SABOTEUR -> 1.5;
            case Catalog.COURIER -> 0.8;
            default -> 1.0;
        };
        double speedFactor = switch (kind) {
            case Catalog.COURIER -> 1.9;
            case Catalog.SHIELD -> 0.72;
            case Catalog.ORGANISER -> 0.85;
            default -> 1.0;
        };
        maxHp = (14 + wave * 3) * healthFactor;
        hp = maxHp;
        speed = (0.32 + wave * 0.018) * speedFactor;
        y = kind == Catalog.FLYER ? 80 : 148;
    }

    public int id() { return id; }
    public int kind() { return kind; }
    public double x() { return x; }
    public double y() { return y; }
    public double hp() { return hp; }
    public double maxHp() { return maxHp; }
    public boolean flying() { return kind == Catalog.FLYER; }
    public boolean slowed() { return slowTicks > 0; }
    public boolean flashing() { return hitTicks > 0; }
}
