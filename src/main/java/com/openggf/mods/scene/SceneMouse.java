package com.openggf.mods.scene;

/**
 * The mouse this tick, in logical screen pixels ({@code 0..width-1}, {@code 0..height-1}).
 * {@code inside} is false when the pointer is over the letterbox or outside the window.
 * {@code wheel} counts notches scrolled since the last tick (positive = away from the user).
 * {@code active} becomes true once the player has used the mouse at all, so a scene can hide
 * hover effects for keyboard and gamepad players.
 */
@com.openggf.game.ModApi
public record SceneMouse(
        int x,
        int y,
        boolean inside,
        boolean moved,
        boolean leftDown,
        boolean leftPressed,
        boolean leftReleased,
        boolean rightPressed,
        int wheel,
        boolean active) {

    /** True when the pointer is inside the rectangle. */
    public boolean over(int rx, int ry, int rw, int rh) {
        return inside && x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    /** A mouse that is nowhere (headless runs, before any mouse input). */
    public static SceneMouse none() {
        return new SceneMouse(-1, -1, false, false, false, false, false, false, 0, false);
    }
}
