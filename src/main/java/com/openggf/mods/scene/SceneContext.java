package com.openggf.mods.scene;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PhysicalInput;

/**
 * Everything a {@link ModScene} can use: input, the screen size, art loading, audio,
 * storage, and ways to leave. One context lives for one visit to the scene.
 */
@com.openggf.game.ModApi
public interface SceneContext {
    /** The mod that owns the scene. */
    String ownerModId();

    /**
     * Logical screen width in pixels, fixed while the scene is open: the player's display
     * aspect decides it (320 for 4:3, 352, 400 for 16:9, 528 or 800) unless the mod requires
     * an aspect. Lay out from this value rather than assuming one width.
     */
    int width();

    /** Logical screen height in pixels (224). */
    int height();

    /** Ticks since the scene opened. */
    long ticks();

    /**
     * True while any of {@code buttons} ({@link SceneButtons} bits, combined with {@code |}) is
     * held on player 1's pad or the keyboard keys mapped to it.
     */
    boolean buttonDown(int buttons);

    /** True on the tick any of {@code buttons} went down. */
    boolean buttonPressed(int buttons);

    /**
     * True on the tick any of {@code buttons} went down, and again every 4 ticks once it has been
     * held for 24, as the engine's own menus repeat: use it to move a cursor through a menu.
     */
    boolean buttonRepeated(int buttons);

    /**
     * This tick's keyboard and gamepad state for both players, merged; {@link #buttonDown} and
     * friends cover player 1 more simply. The {@code menu*} flags are true on the tick a
     * direction or button went down. In a scene {@code menuAccept} is A, C or Start and
     * {@code menuBack} is B (the Genesis convention; see {@link SceneButtons}), so they never
     * fire together.
     */
    LogicalInputSnapshot input();

    /** Physical keyboard/pad events, timestamped in the host's monotonic clock domain. */
    default PhysicalInput physicalInput() { return PhysicalInput.neutral(); }

    /**
     * Characters typed this tick through the operating system's text input, honouring the
     * user's keyboard layout and Shift; control characters are omitted. Use this, not key codes,
     * for anything the player spells. Each tick's text is returned to every call in that tick.
     */
    default String typedText() { return ""; }

    /**
     * Claims the keyboard's text and editing keys through the next tick, so host shortcuts bound
     * to letters (display filters, recording chords) do not fire while the player types. Call it
     * every tick the scene wants typing; the claim lapses on its own. Escape, function keys and
     * gamepads stay with the host. {@link #keyDown}, {@link #keyPressed} and
     * {@link #physicalInput()} still report claimed keys to this scene.
     */
    default void captureKeyboardText() { }

    /** True while a key is held ({@link SceneKeys} code, such as {@code SceneKeys.TAB}). */
    boolean keyDown(int key);

    /** True on the tick a key went down ({@link SceneKeys} code). */
    boolean keyPressed(int key);

    /** The mouse, in logical screen pixels. */
    SceneMouse mouse();

    /** Image decoding: PNGs from the mod's files and art from the player's ROM. */
    SceneArt art();

    /** Base-game driver music/SFX and this mod's declared audio-manifest SFX. */
    SceneAudio audio();

    /** Finite ROM music with an audible sample clock, independent of scene update cadence. */
    default SceneMusic music() { throw new UnsupportedOperationException("Finite scene music unavailable"); }

    /** Explicit, bounded direct peer messaging owned by this scene visit. */
    default SceneNetwork network() { throw new UnsupportedOperationException("Scene networking unavailable"); }

    /** Small text files kept for this mod across sessions (saves, settings, high scores). */
    SceneStorage storage();

    /**
     * Fades out, closes the scene and starts {@code act} of {@code zone} in the running game
     * with this mod's patches active. Indices are the running game's zone registry indices, as a
     * title screen's start zone uses. Returns false, doing nothing, when the indices are out of
     * range or the host cannot start gameplay (headless or mid-transition). Gameplay returns
     * here when a frame controller requests the title
     * ({@link com.openggf.game.mode.GameplayFrameController#consumeTitleRequest()}).
     */
    default boolean startStockAct(int zone, int act) { return false; }

    /** The running stock session's game and team. */
    default SceneLaunch currentLaunch() { return null; }

    /**
     * Relaunches through the master title into {@code launch}'s game with its team for this
     * session only (the player's own settings are untouched), reopening this mod's startup scene
     * there. Use it to move between supplied games or to fix the team a mode needs. Returns
     * false, doing nothing, when that game's ROM is unavailable or the host is mid-transition.
     */
    default boolean relaunch(SceneLaunch launch) { return false; }

    /** Fades out and returns to the base game's own title screen. */
    void exitToGameTitle();

    /** Fades out and returns to the OpenGGF master title (game picker). */
    void exitToMasterTitle();
}
