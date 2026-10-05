package com.openggf.mods.scene;

import com.openggf.control.LogicalInputSnapshot;

/**
 * Everything a {@link ModScene} can use: input, the screen size, art loading, audio,
 * storage, and ways to leave. One context lives for one visit to the scene.
 */
@com.openggf.game.ModApi
public interface SceneContext {
    /** The mod that owns the scene. */
    String ownerModId();

    /** Logical screen width in pixels: 320 for 4:3, 400 for 16:9 (see the mod's required aspect). */
    int width();

    /** Logical screen height in pixels (224). */
    int height();

    /** Ticks since the scene opened. */
    long ticks();

    /**
     * This tick's keyboard and gamepad state, merged. The {@code menu*} flags are true on the
     * tick a direction or button went down: {@code menuAccept} is any action button or Start,
     * {@code menuBack} is action button C. {@code player1()} has the held and pressed masks
     * for building key repeat or reading individual buttons.
     */
    LogicalInputSnapshot input();

    /** True while a key is held (GLFW key code, e.g. {@code GLFW_KEY_TAB = 258}). */
    boolean keyDown(int glfwKey);

    /** True on the tick a key went down. */
    boolean keyPressed(int glfwKey);

    /** The mouse, in logical screen pixels. */
    SceneMouse mouse();

    /** Image creation: PNGs, pixels built in code, and ROM sprites. */
    SceneArt art();

    /** Music and sound effects (the base game's sound driver and IDs). */
    SceneAudio audio();

    /** Small text files kept for this mod across sessions (saves, settings, high scores). */
    SceneStorage storage();

    /** Fades out and returns to the base game's own title screen. */
    void exitToGameTitle();

    /** Fades out and returns to the OpenGGF master title (game picker). */
    void exitToMasterTitle();
}
