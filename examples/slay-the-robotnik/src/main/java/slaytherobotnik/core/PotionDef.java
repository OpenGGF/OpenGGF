package slaytherobotnik.core;

/**
 * A potion — presented in this mod as an item monitor you break during combat. Potions are
 * stateless, so the run stores the definitions themselves. {@code potency} is the number
 * shown in the text; a relic may double it.
 */
public record PotionDef(
        String id,
        String name,
        String rarity,
        String target,
        int potency,
        String text,
        Effect effect,
        RunEffect outsideCombat) {

    /** Target for potions that trigger themselves (the 1-Up revives you on death). */
    public static final String AUTO = "Auto";

    /** Combat use. {@code target} is null unless {@link #target()} is {@link CardTarget#ENEMY}. */
    @FunctionalInterface
    public interface Effect {
        void use(Combat combat, Enemy target, int potency);
    }

    /** Optional use on the map (for example raising max HP); {@code null} means combat only. */
    @FunctionalInterface
    public interface RunEffect {
        void use(RunState run, int potency);
    }

    public boolean usableOutsideCombat() {
        return outsideCombat != null;
    }

    /** Text with {@code {P}} replaced by the potency. */
    public String describe(int potency) {
        return text.replace("{P}", Integer.toString(potency));
    }
}
