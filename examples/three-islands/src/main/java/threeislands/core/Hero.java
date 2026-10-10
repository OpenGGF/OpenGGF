package threeislands.core;

/** A hero's persistent state between battles: level, experience, current HP and EP. */
public final class Hero {
    public static final int MAX_LEVEL = 30;

    public final HeroId id;
    private int level = 1;
    private int xp;
    private int hp;
    private int ep;
    private Gear gear;

    public Hero(HeroId id) {
        this.id = id;
        hp = maxHp();
        ep = maxEp();
    }

    public int level() { return level; }
    public int xp() { return xp; }
    public int hp() { return hp; }
    public int ep() { return ep; }

    public int maxHp() { return id.baseHp + id.hpGrowth * (level - 1) + (gear == null ? 0 : gear.hp); }
    public int maxEp() { return id.baseEp + id.epGrowth * (level - 1) + (gear == null ? 0 : gear.ep); }
    public int atk() { return id.baseAtk + id.atkGrowth * (level - 1) + (gear == null ? 0 : gear.atk); }
    public int def() { return id.baseDef + id.defGrowth * (level - 1) + (gear == null ? 0 : gear.def); }
    public int spd() { return id.baseSpd + id.spdGrowth * (level - 1) + (gear == null ? 0 : gear.spd); }

    /** The equipped accessory, or null. */
    public Gear gear() { return gear; }

    /** Changes the accessory; current HP/EP keep their values within the new maxima. */
    public void equip(Gear value) {
        gear = value;
        setHp(hp);
        setEp(ep);
    }

    /** Experience needed to go from {@code level} to the next. */
    public static int xpToNext(int level) {
        return 12 + level * level * 6;
    }

    public void setHp(int value) { hp = Math.max(0, Math.min(maxHp(), value)); }
    public void setEp(int value) { ep = Math.max(0, Math.min(maxEp(), value)); }

    public void restore() {
        hp = maxHp();
        ep = maxEp();
    }

    /** Adds experience and returns how many levels were gained; HP and EP rise with the maxima. */
    public int gainXp(int amount) {
        if (amount < 0) throw new IllegalArgumentException("negative experience");
        int gained = 0;
        xp += amount;
        while (level < MAX_LEVEL && xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            int oldHp = maxHp(), oldEp = maxEp();
            level++;
            gained++;
            hp += maxHp() - oldHp;
            ep += maxEp() - oldEp;
        }
        if (level == MAX_LEVEL) xp = 0;
        return gained;
    }

    /** Sets a level directly (a joining hero catches up with the party), restoring HP and EP. */
    public void setLevel(int value) {
        level = Math.max(1, Math.min(MAX_LEVEL, value));
        xp = 0;
        restore();
    }

    /** Loads saved values without growth side effects. */
    public void load(int savedLevel, int savedXp, int savedHp, int savedEp) {
        level = Math.max(1, Math.min(MAX_LEVEL, savedLevel));
        xp = Math.max(0, Math.min(savedXp, xpToNext(level) - 1));
        setHp(savedHp);
        setEp(savedEp);
    }
}
