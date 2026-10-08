package threeislands.core;

/**
 * One thing that happened during a turn, in order, for the view to animate: a line of text and
 * optionally an actor moving at a target with a number popping up.
 */
public record BattleEvent(int type, Combatant source, Combatant target, int amount, String text,
        Element element, boolean critical) {
    public static final int MESSAGE = 0;
    public static final int HIT = 1;
    public static final int HEAL = 2;
    public static final int BLOCK = 3;
    public static final int KO = 4;
    public static final int REVIVE = 5;
    public static final int BUFF = 6;
    public static final int CHARGE = 7;
    public static final int SUPER = 8;
    public static final int EP = 9;
    public static final int LUNGE = 10;
    public static final int FLEE = 11;

    public static BattleEvent message(String text) {
        return new BattleEvent(MESSAGE, null, null, 0, text, Element.NONE, false);
    }
}
