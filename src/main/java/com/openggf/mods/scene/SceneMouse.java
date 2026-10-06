package com.openggf.mods.scene;

/**
 * The mouse this tick, in logical screen pixels ({@code 0..width-1}, {@code 0..height-1}).
 * The engine builds one per tick ({@link SceneContext#mouse()}); {@link #none()} is a mouse
 * that is nowhere, for headless runs and tests.
 *
 * <ul>
 *   <li>{@link #inside} is false when the pointer is over the letterbox or outside the
 *       window; {@link #x}/{@link #y} are then -1 (no mouse input yet) or the last mapped
 *       position.</li>
 *   <li>{@code ...Down} is held this tick, {@code ...Pressed} went down this tick and
 *       {@code ...Released} came up this tick, for the left and right buttons.</li>
 *   <li>{@link #wheel} counts notches scrolled since the last tick (positive = away from the
 *       user).</li>
 *   <li>{@link #lastInputWasMouse} says whether the player's most recent input was the mouse
 *       (moving it, a button or the wheel) rather than a key or a gamepad button, so a scene
 *       can show hover highlights only to mouse players and its own cursor otherwise.</li>
 * </ul>
 */
@com.openggf.game.ModApi
public final class SceneMouse {
    private static final SceneMouse NONE = new SceneMouse(-1, -1, false, false, false, false, false, false,
            false, false, 0, false);

    private final int x;
    private final int y;
    private final boolean inside;
    private final boolean moved;
    private final boolean leftDown;
    private final boolean leftPressed;
    private final boolean leftReleased;
    private final boolean rightDown;
    private final boolean rightPressed;
    private final boolean rightReleased;
    private final int wheel;
    private final boolean lastInputWasMouse;

    /** Engine-built; scenes receive these from {@link SceneContext#mouse()}. */
    SceneMouse(int x, int y, boolean inside, boolean moved, boolean leftDown, boolean leftPressed,
            boolean leftReleased, boolean rightDown, boolean rightPressed, boolean rightReleased, int wheel,
            boolean lastInputWasMouse) {
        this.x = x;
        this.y = y;
        this.inside = inside;
        this.moved = moved;
        this.leftDown = leftDown;
        this.leftPressed = leftPressed;
        this.leftReleased = leftReleased;
        this.rightDown = rightDown;
        this.rightPressed = rightPressed;
        this.rightReleased = rightReleased;
        this.wheel = wheel;
        this.lastInputWasMouse = lastInputWasMouse;
    }

    /** A mouse that is nowhere (headless runs, before any mouse input). */
    public static SceneMouse none() {
        return NONE;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    /** True when the pointer is over the game picture. */
    public boolean inside() {
        return inside;
    }

    /** True when the pointer moved to another logical pixel this tick. */
    public boolean moved() {
        return moved;
    }

    public boolean leftDown() {
        return leftDown;
    }

    public boolean leftPressed() {
        return leftPressed;
    }

    public boolean leftReleased() {
        return leftReleased;
    }

    public boolean rightDown() {
        return rightDown;
    }

    public boolean rightPressed() {
        return rightPressed;
    }

    public boolean rightReleased() {
        return rightReleased;
    }

    /** Wheel notches since the last tick; positive scrolls away from the user. */
    public int wheel() {
        return wheel;
    }

    /**
     * True while the player's latest input is the mouse: set by moving it, a button or the
     * wheel, cleared by the next key or gamepad button press.
     */
    public boolean lastInputWasMouse() {
        return lastInputWasMouse;
    }

    /** True when the pointer is inside the rectangle. */
    public boolean over(int rx, int ry, int rw, int rh) {
        return inside && x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    @Override
    public String toString() {
        return "SceneMouse[" + x + "," + y + (inside ? " inside" : "") + (leftDown ? " left" : "")
                + (rightDown ? " right" : "") + (wheel != 0 ? " wheel=" + wheel : "") + "]";
    }
}
