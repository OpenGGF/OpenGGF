package slaytherobotnik.core;

/**
 * One entry in an enemy's move set. {@code intent} is an {@link IntentKind} constant;
 * {@code baseDamage} and {@code hits} are only used by attack intents.
 */
public record Move(String id, String name, String intent, int baseDamage, int hits) {
    public static Move attack(String id, String name, int damage) {
        return new Move(id, name, IntentKind.ATTACK, damage, 1);
    }

    public static Move multiAttack(String id, String name, int damage, int hits) {
        return new Move(id, name, IntentKind.ATTACK, damage, hits);
    }

    public static Move of(String id, String name, String intent) {
        return new Move(id, name, intent, 0, 0);
    }

    public static Move of(String id, String name, String intent, int damage, int hits) {
        return new Move(id, name, intent, damage, hits);
    }
}
