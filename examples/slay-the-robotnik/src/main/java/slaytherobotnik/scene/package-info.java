/**
 * Everything on screen: the mod's {@code ModScene}, the screens and the room views.
 *
 * <p>{@link slaytherobotnik.scene.SlayScene} is the scene the engine runs. It owns a
 * {@link slaytherobotnik.scene.Shell} (shared state, music, fades and saves), which shows one
 * {@code Screen} at a time: the title, character select, compendium, or the
 * {@link slaytherobotnik.scene.RunScreen}. During a run, {@code RunScreen} shows one
 * {@code RoomView} per room ({@code CombatView}, {@code MapView}, {@code EventView}, ...) and the
 * HUD around it. The remaining classes compose pictures for those views: {@code EnemyVisuals},
 * {@code CardRenderer}, {@code EventPictures}, {@code LevelStages}, {@code Backdrops} and similar.
 * To add a screen, implement {@code Screen} and call {@code shell.go(...)}.
 */
package slaytherobotnik.scene;
