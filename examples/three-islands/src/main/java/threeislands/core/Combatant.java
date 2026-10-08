package threeislands.core;

/**
 * One fighter in a battle: a hero (whose HP/EP are copied back to {@link Hero} afterwards) or an
 * enemy built from an {@link EnemyKind} and the zone tier. Status counters count the owner's own
 * turns and tick down at the start of each of them.
 */
public final class Combatant {
    public final String name;
    public final Hero hero;
    public final EnemyKind kind;
    public final int slot;
    public int hp;
    public final int maxHp;
    public int ep;
    public final int maxEp;
    private final int baseAtk;
    private final int baseDef;
    private final int baseSpd;
    public final int xp;
    public final int rings;

    public boolean guarding;
    public Element shield;
    public int haste;
    public int defDown;
    public int invincible;
    public int taunt;
    public boolean superForm;
    public boolean charging;
    public boolean scanned;
    public int turnsTaken;

    private Combatant(String name, Hero hero, EnemyKind kind, int slot, int hp, int maxHp, int ep, int maxEp,
            int atk, int def, int spd, int xp, int rings) {
        this.name = name;
        this.hero = hero;
        this.kind = kind;
        this.slot = slot;
        this.hp = hp;
        this.maxHp = maxHp;
        this.ep = ep;
        this.maxEp = maxEp;
        this.baseAtk = atk;
        this.baseDef = def;
        this.baseSpd = spd;
        this.xp = xp;
        this.rings = rings;
    }

    public static Combatant hero(Hero hero, int slot) {
        return new Combatant(hero.id.label, hero, null, slot, hero.hp(), hero.maxHp(), hero.ep(), hero.maxEp(),
                hero.atk(), hero.def(), hero.spd(), 0, 0);
    }

    /**
     * A foe balanced for a party at {@code level}: an ordinary foe takes about three of Sonic's
     * basic hits and deals about an eighth of his HP per hit, scaled by the kind's percentages.
     * HP also scales with the party's size (bosses less steeply), so South Island suits Sonic
     * alone and three heroes with techs still face a real fight.
     */
    public static Combatant enemy(EnemyKind kind, int level, int partySize, int slot, String name) {
        int size = Math.max(1, Math.min(3, partySize));
        // Knuckles and the Trinity Rush lift a trio's damage well beyond a duo's, hence the jumps.
        int partyPct = switch (size) {
            case 1 -> kind.boss ? 50 : 100;
            case 2 -> kind.boss ? 52 : 165;
            default -> kind.boss ? 130 : 260;
        };
        int hp = (24 + 7 * level) * kind.hpPct / 100 * partyPct / 100;
        int atk = (7 + 2 * level) * kind.atkPct / 100;
        int def = (2 + level * 8 / 5) * kind.defPct / 100;
        int spd = (6 + level * 9 / 5) * kind.spdPct / 100;
        int xp = (4 + level * level * 3 / 4) * Math.max(100, kind.hpPct * 2 / 3) / 100;
        int rings = (5 + 2 * level) * (kind.boss ? 5 : 1);
        return new Combatant(name, null, kind, slot, hp, hp, 0, 0, Math.max(1, atk), Math.max(0, def),
                Math.max(1, spd), xp, rings);
    }

    public boolean isHero() { return hero != null; }
    public boolean alive() { return hp > 0; }

    public int atk() { return baseAtk * (superForm ? 2 : 1); }

    public int def() {
        int def = defDown > 0 ? baseDef * 3 / 5 : baseDef;
        return superForm ? def * 2 : def;
    }

    public int spd() {
        int spd = haste > 0 ? baseSpd * 3 / 2 : baseSpd;
        return spd + (superForm ? 8 : 0);
    }

    /** Ticks this fighter's own status counters at the start of its turn. */
    void startTurn() {
        guarding = false;
        if (haste > 0) haste--;
        if (defDown > 0) defDown--;
        if (invincible > 0) invincible--;
        if (taunt > 0) taunt--;
        turnsTaken++;
    }

    /** Copies a hero's HP and EP back to the persistent party state. */
    void writeBack() {
        if (hero != null) {
            hero.setHp(hp);
            hero.setEp(ep);
        }
    }
}
