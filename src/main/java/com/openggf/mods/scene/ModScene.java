package com.openggf.mods.scene;

/**
 * A full-screen screen owned by a mod: a menu, a minigame, or a whole game drawn with
 * {@link SceneCanvas} instead of the level renderer.
 *
 * <p>The engine calls {@link #enter} once when the scene opens, then {@link #update} 60 times
 * per second and {@link #draw} once per presented frame, and finally {@link #exit} when the
 * scene closes for any reason (the scene asked to leave, the player held Escape, or the
 * engine shut down). Every call runs inside the mod fault boundary: an exception disables
 * the mod and returns the player to the master title instead of crashing the engine.
 *
 * <p>Keep all state on the scene instance. Mod classes may not hold static state.
 *
 * <pre>{@code
 * public final class HelloScene implements ModScene {
 *     private int ticks;
 *     public void enter(SceneContext ctx) { ctx.audio().playMusic(0x2F); }
 *     public void update(SceneContext ctx) {
 *         ticks++;
 *         if (ctx.buttonPressed(SceneButtons.B)) ctx.exitToGameTitle();   // B goes back
 *     }
 *     public void draw(SceneContext ctx, SceneCanvas canvas) {
 *         canvas.clear(0x102040);
 *         canvas.text("HELLO " + ticks / 60, 16, 16, 0xFFFFFFFF);
 *     }
 * }
 * }</pre>
 */
@com.openggf.game.ModApi
public interface ModScene {
    /** The scene opens: load art, start music. */
    void enter(SceneContext ctx);

    /** One 60 Hz tick: read input and advance state. Do not draw here. */
    void update(SceneContext ctx);

    /** Draws the current state. Must not change game state; it may be skipped or repeated. */
    void draw(SceneContext ctx, SceneCanvas canvas);

    /** The scene is closing; save anything worth keeping. Images are released afterwards. */
    default void exit(SceneContext ctx) {
    }
}
