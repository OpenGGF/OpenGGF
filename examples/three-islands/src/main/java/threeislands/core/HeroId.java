package threeislands.core;

/**
 * The three heroes and their growth curves. Stats at level {@code L} are
 * {@code base + growth * (L - 1)}: Sonic is fast, Tails supports, Knuckles hits and endures.
 */
public enum HeroId {
    SONIC("Sonic", 46, 8, 12, 2, 12, 3, 7, 2, 14, 2, 0xFF3070FF),
    TAILS("Tails", 40, 7, 16, 3, 9, 2, 6, 2, 11, 2, 0xFFFFA020),
    KNUCKLES("Knuckles", 58, 10, 10, 2, 15, 4, 10, 3, 8, 1, 0xFFFF3040);

    public final String label;
    public final int baseHp, hpGrowth, baseEp, epGrowth, baseAtk, atkGrowth, baseDef, defGrowth, baseSpd, spdGrowth;
    public final int color;

    HeroId(String label, int baseHp, int hpGrowth, int baseEp, int epGrowth, int baseAtk, int atkGrowth,
            int baseDef, int defGrowth, int baseSpd, int spdGrowth, int color) {
        this.label = label;
        this.baseHp = baseHp;
        this.hpGrowth = hpGrowth;
        this.baseEp = baseEp;
        this.epGrowth = epGrowth;
        this.baseAtk = baseAtk;
        this.atkGrowth = atkGrowth;
        this.baseDef = baseDef;
        this.defGrowth = defGrowth;
        this.baseSpd = baseSpd;
        this.spdGrowth = spdGrowth;
        this.color = color;
    }

    /** Character code for {@code SceneRomArt.character}. */
    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public int bit() {
        return 1 << ordinal();
    }
}
