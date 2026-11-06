package com.openggf.control;

/**
 * The mouse wheel, kept off the creator-facing {@link InputHandler}: the window's scroll
 * callback adds movement, and whichever screen reads the mouse this input pass sees the same
 * whole notches, like a button press. {@link InputHandler#update()} retires them at the end of
 * the pass, so notches nobody read (during gameplay, say) never reach a later screen.
 * One wheel belongs to each input handler ({@link #of}). Engine-internal.
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

    /** Whole notches scrolled this input pass (positive = away from the user). Reading does not consume them. */
    public int notches() {
        return (int) accumulated;
    }

    /** Retires this pass's whole notches, keeping any fractional remainder from smooth-scrolling devices. */
    void endPass() {
        accumulated -= (int) accumulated;
    }
}
