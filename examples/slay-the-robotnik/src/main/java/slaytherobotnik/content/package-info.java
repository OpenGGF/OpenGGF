/**
 * The game's data: every card, enemy, encounter, relic, potion, event and act.
 *
 * <p>One class per act ({@code AngelIsland}, {@code Hydrocity}, {@code LaunchBase},
 * {@code SkySanctuary}) registers that act's map settings, enemies and encounters; one class per
 * character registers their cards; {@link slaytherobotnik.content.Content} registers everything
 * into a {@link slaytherobotnik.core.Catalog}. Plain Java. To add an enemy or card, start here,
 * then give it a look in {@code scene} ({@code EnemyVisuals}, {@code CardArt}).
 */
package slaytherobotnik.content;
