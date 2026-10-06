package slaytherobotnik.core;

/**
 * A button on an event page. {@code label} is the bracketed action ("[Enter]"),
 * {@code detail} the consequence ("Lose 8 HP. Obtain a relic."). A disabled option is shown
 * greyed out with its detail explaining why.
 */
public record EventOption(String label, String detail, boolean enabled, Runnable action) {
    public static EventOption of(String label, String detail, Runnable action) {
        return new EventOption(label, detail, true, action);
    }

    public static EventOption locked(String label, String detail) {
        return new EventOption(label, detail, false, () -> { });
    }
}
