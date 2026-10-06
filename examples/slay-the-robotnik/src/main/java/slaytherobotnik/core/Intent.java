package slaytherobotnik.core;

/**
 * What an enemy shows above its head. {@code kind} is an {@link IntentKind} constant. For
 * attack intents {@code damage} is the per-hit damage after Strength, Weak and the
 * player's Vulnerable, so the number matches what will actually happen.
 */
public record Intent(String kind, int damage, int hits) {
    public boolean isAttack() {
        return IntentKind.isAttack(kind);
    }

    public int totalDamage() {
        return isAttack() ? damage * hits : 0;
    }
}
