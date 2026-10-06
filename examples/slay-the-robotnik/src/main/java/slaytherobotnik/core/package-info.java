/**
 * The rules: cards, creatures, powers, intents, relics, potions, events and the combat turn loop.
 *
 * <p>Plain Java with no engine imports. Rules apply immediately and report what happened as
 * {@link slaytherobotnik.core.CombatEvent}s, which the presentation animates later; nothing here
 * waits for a frame. New mechanics (a power, a keyword, a card hook) belong here; the cards and
 * enemies that use them belong in {@code content}.
 */
package slaytherobotnik.core;
