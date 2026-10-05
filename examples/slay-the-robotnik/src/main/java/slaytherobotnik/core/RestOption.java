package slaytherobotnik.core;

/**
 * A button at a Starpost (rest site). Relics can add options (see
 * {@link Relic#addRestOptions}); {@code action} runs when chosen and uses up the visit.
 */
public record RestOption(String id, String label, String detail, boolean enabled, Runnable action) {
}
