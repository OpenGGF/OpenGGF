package threeislands.core;

/**
 * Skills, including the dual and triple techs. {@code members} is a mask of {@link HeroId} bits:
 * one bit for a hero's own skill, several for a tech, which also spends the partners' turns this
 * round and {@code ep} from every member. {@code power} is a damage multiplier in tenths of
 * attack, or the base amount for healing.
 */
public enum Skill {
    SPIN_DASH("Spin Dash", 1, 3, 18, Kinds.ENEMY, Kinds.NONE, Kinds.DAMAGE, 1,
            "Rev up and blast one foe."),
    HOMING_ATTACK("Homing Attack", 1, 5, 11, Kinds.ALL_ENEMIES, Kinds.NONE, Kinds.DAMAGE, 3,
            "Bounce from foe to foe, hitting every enemy."),
    PEEL_OUT("Super Peel Out", 1, 4, 3, Kinds.ALL_ALLIES, Kinds.NONE, Kinds.HASTE, 5,
            "The whole party moves faster for 3 turns."),
    SONIC_BOOM("Sonic Boom", 1, 8, 30, Kinds.ENEMY, Kinds.NONE, Kinds.DAMAGE, 9,
            "Break the sound barrier straight through one foe."),
    LIGHT_SPEED("Light Speed Dash", 1, 12, 20, Kinds.ALL_ENEMIES, Kinds.ELEC, Kinds.DAMAGE, 14,
            "A blinding lightning dash through every enemy."),

    PATCH_UP("Patch Up", 2, 4, 30, Kinds.ALLY, Kinds.NONE, Kinds.HEAL, 1,
            "Tails' toolkit restores HP to one ally."),
    ANALYZE("Analyze", 2, 1, 0, Kinds.ENEMY, Kinds.NONE, Kinds.SCAN, 1,
            "Reveal a foe's HP and weakness."),
    TAIL_TWISTER("Tail Twister", 2, 5, 10, Kinds.ALL_ENEMIES, Kinds.NONE, Kinds.DAMAGE, 2,
            "A spinning gale of twin tails strikes all foes."),
    ENERGY_BALL("Energy Ball", 2, 6, 22, Kinds.ENEMY, Kinds.ELEC, Kinds.DAMAGE, 6,
            "A crackling lightning shot at one foe."),
    RING_SHOWER("Ring Shower", 2, 9, 24, Kinds.ALL_ALLIES, Kinds.NONE, Kinds.HEAL, 8,
            "Scatter rings that heal the whole party."),
    RESTART("Restart", 2, 10, 50, Kinds.FALLEN_ALLY, Kinds.NONE, Kinds.REVIVE, 12,
            "Revive a fallen ally with half their HP."),

    DRILL_CLAW("Drill Claw", 4, 4, 20, Kinds.ENEMY, Kinds.NONE, Kinds.BREAK, 1,
            "A corkscrew dive that also lowers the foe's defence."),
    GLIDE_SMASH("Glide Smash", 4, 6, 13, Kinds.ALL_ENEMIES, Kinds.NONE, Kinds.DAMAGE, 1,
            "Glide low across the battlefield into every foe."),
    GUARDIAN("Guardian Stance", 4, 3, 3, Kinds.SELF, Kinds.NONE, Kinds.TAUNT, 4,
            "Draw every attack to Knuckles and halve damage for 3 turns."),
    MAX_HEAT("Maximum Heat", 4, 10, 34, Kinds.ENEMY, Kinds.FIRE, Kinds.DAMAGE, 12,
            "Knuckles' burning charge punch."),

    TORNADO_SPIN("Tornado Spin", 3, 5, 17, Kinds.ALL_ENEMIES, Kinds.NONE, Kinds.DAMAGE, 1,
            "SONIC + TAILS: a whirlwind spin that hits every foe."),
    DOUBLE_SPIN("Double Spin Attack", 5, 5, 36, Kinds.ENEMY, Kinds.NONE, Kinds.DAMAGE, 1,
            "SONIC + KNUCKLES: two spinning blows on one foe."),
    THUNDER_GLIDE("Thunder Glide", 6, 5, 16, Kinds.ALL_ENEMIES, Kinds.ELEC, Kinds.DAMAGE, 1,
            "TAILS + KNUCKLES: a lightning-charged glide through all foes."),
    TRINITY_RUSH("Trinity Rush", 7, 7, 30, Kinds.ALL_ENEMIES, Kinds.NONE, Kinds.DAMAGE, 1,
            "ALL THREE: the islands' heroes strike together.");

    public final String label;
    public final int members;
    public final int ep;
    public final int power;
    public final int target;
    public final int elementIndex;
    public final int effect;
    public final int learnLevel;
    public final String description;

    Skill(String label, int members, int ep, int power, int target, int elementIndex, int effect, int learnLevel,
            String description) {
        this.label = label;
        this.members = members;
        this.ep = ep;
        this.power = power;
        this.target = target;
        this.elementIndex = elementIndex;
        this.effect = effect;
        this.learnLevel = learnLevel;
        this.description = description;
    }

    public Element element() {
        return Element.values()[elementIndex];
    }

    /** True for a dual or triple tech. */
    public boolean isTech() {
        return Integer.bitCount(members) > 1;
    }

    public boolean includes(HeroId hero) {
        return (members & hero.bit()) != 0;
    }
}
