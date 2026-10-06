/**
 * Slay the Robotnik: a Slay the Spire-style deck builder played inside Sonic 3 &amp; Knuckles.
 *
 * <p>The packages split into the game and its presentation. {@code core}, {@code content},
 * {@code map} and {@code run} are plain Java with no engine imports, so the whole game runs and is
 * tested without a ROM or a window. {@code art}, {@code ui} and {@code scene} draw it through the
 * engine's scene API ({@code com.openggf.mods.scene}). {@link slaytherobotnik.SlayTheRobotnikMod}
 * is the entry point; {@code scene.SlayScene} is the scene it registers.
 */
package slaytherobotnik;
