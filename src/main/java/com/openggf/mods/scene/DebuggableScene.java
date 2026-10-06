package com.openggf.mods.scene;

/**
 * An optional debug entry point a {@link ModScene} can implement, so tools reach any screen
 * without playing up to it: the example-mod capture tool's {@code jump=} step and engine tests
 * call {@link #debugJump} between ticks. Players never reach it.
 *
 * <pre>{@code
 * public final class MyScene implements ModScene, DebuggableScene {
 *     ...
 *     public boolean debugJump(String command) {
 *         if (command.equals("shop")) { screen = new ShopScreen(); return true; }
 *         return false;                       // not a command this scene knows
 *     }
 * }
 * }</pre>
 */
@com.openggf.game.ModApi
public interface DebuggableScene {
    /**
     * Goes straight to the screen or state {@code command} names, in the scene's own grammar
     * (document it on the implementing class), using the {@link SceneContext} the scene was
     * entered with. Runs on the scene's thread between ticks and inside the fault boundary like
     * every other scene call, so an exception disables the mod: return false for a command the
     * scene does not understand instead of throwing.
     *
     * @return whether the scene understood and applied the command
     */
    boolean debugJump(String command);
}
