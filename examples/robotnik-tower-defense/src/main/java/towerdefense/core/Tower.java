package towerdefense.core;

/** A constructed defense; total investment is retained for an honest refund. */
public final class Tower {
    final int site, kind;
    int level = 1, cooldown, jamTicks, invested;

    Tower(int site, int kind) {
        this.site = site;
        this.kind = kind;
        invested = Catalog.defense(kind).cost();
    }

    public int site() { return site; }
    public int kind() { return kind; }
    public int level() { return level; }
    public int jamTicks() { return jamTicks; }
    public double x() { return Battlefield.siteX(site); }
    public double y() { return Battlefield.siteY(site) - 12; }
    public int range() { return Catalog.defense(kind).range() + (level - 1) * 9; }
    public double damage() { return Catalog.defense(kind).damage() * (1 + (level - 1) * 0.55); }
    public int reload() { return Catalog.defense(kind).cooldown() * (100 - (level - 1) * 12) / 100; }
}
