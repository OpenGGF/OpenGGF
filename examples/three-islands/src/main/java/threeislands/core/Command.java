package threeislands.core;

/** A hero's chosen action for its turn. {@code target} is null for party-wide or all-enemy actions. */
public record Command(int type, Skill skill, Item item, Combatant target) {
    public static final int ATTACK = 0;
    public static final int SKILL = 1;
    public static final int ITEM = 2;
    public static final int GUARD = 3;
    public static final int FLEE = 4;
    public static final int SUPER = 5;

    public static Command attack(Combatant target) { return new Command(ATTACK, null, null, target); }
    public static Command skill(Skill skill, Combatant target) { return new Command(SKILL, skill, null, target); }
    public static Command item(Item item, Combatant target) { return new Command(ITEM, null, item, target); }
    public static Command guard() { return new Command(GUARD, null, null, null); }
    public static Command flee() { return new Command(FLEE, null, null, null); }
    public static Command transform() { return new Command(SUPER, null, null, null); }
}
