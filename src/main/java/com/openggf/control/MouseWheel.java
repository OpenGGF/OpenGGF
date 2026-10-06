package com.openggf.control;

/**
 * The mouse wheel, kept off the creator-facing {@link InputHandler}: the window's scroll
 * callback adds movement and the one screen that is reading the mouse (the master title or a mod
 * scene) takes whole notches each frame. One wheel belongs to each input handler
 * ({@link #of}). Engine-internal.
 */
public final class MouseWheel {
    private final InputHandler owner;
    private double accumulated;

    MouseWheel(InputHandler owner) {
        this.owner = owner;
    }

    /** The wheel of {@code input}. */
    public static MouseWheel of(InputHandler input) {
        return input.wheel;
    }

    /** Records wheel movement ({@code yOffset} positive when scrolling away from the user); it counts as mouse input. */
    public void scroll(double yOffset) {
        owner.noteMouseInput();
        accumulated += yOffset;
    }

    /**
     * Whole notches scrolled since the last call (positive = away from the user), keeping any
     * fractional remainder from smooth-scrolling devices.
     */
    public int takeNotches() {
        int notches = (int) accumulated;
        accumulated -= notches;
        return notches;
    }
}
