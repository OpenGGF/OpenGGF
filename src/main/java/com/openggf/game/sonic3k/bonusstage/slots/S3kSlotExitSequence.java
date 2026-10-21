package com.openggf.game.sonic3k.bonusstage.slots;

/**
 * Exit sequence matching ROM loc_4BC1E / loc_4BC54 (sonic3k.asm:99010-99051).
 * Phase 1: Rotation wind-down — increment scalar by 0x40 per frame until 0x1800.
 * Phase 2: loc_4BC54 for 60 iterations ($38): rotation continues and Pal_ToBlack runs
 * whenever Pal_fade_delay underflows (every third iteration, starting with the first).
 */
public final class S3kSlotExitSequence {

    static final int WIND_DOWN_INCREMENT = 0x40;
    static final int WIND_DOWN_TARGET = 0x1800;
    static final int FADE_FRAMES = 60;
    /** loc_4BC54: {@code move.w #2,(Pal_fade_delay).w} after each underflow. */
    static final int FADE_CALL_DELAY = 2;

    @com.openggf.game.rewind.RewindTransient(reason="Stable live controller; its stage RAM is owned by the Slot runtime snapshot")
    private final S3kSlotStageController controller;
    private int currentScalar;
    private int fadeTimer;
    /** Pal_fade_delay; loc_4BC1E clears it when the fade begins. */
    private int fadeDelay;
    private boolean palToBlackThisTick;
    private boolean inFade;
    private boolean complete;

    public S3kSlotExitSequence(S3kSlotStageController controller) {
        this.controller = controller;
        this.currentScalar = controller != null ? controller.scalarIndex() : 0;
    }

    /** Call once per frame during exit sequence */
    public void tick() {
        palToBlackThisTick = false;
        if (complete) return;

        if (!inFade) {
            // Phase 1: wind-down
            currentScalar += WIND_DOWN_INCREMENT;
            controller.setScalarIndex(currentScalar);
            controller.tick(); // continue rotating

            if (currentScalar >= WIND_DOWN_TARGET) {
                inFade = true;
                fadeTimer = FADE_FRAMES;
                fadeDelay = 0;
            }
        } else {
            // Phase 2: loc_4BC54 -- subq.w #1,(Pal_fade_delay).w / bpl.s / move.w #2 / Pal_ToBlack,
            // ahead of the rotation step and the $38 countdown.
            if (--fadeDelay < 0) {
                fadeDelay = FADE_CALL_DELAY;
                palToBlackThisTick = true;
            }
            currentScalar += WIND_DOWN_INCREMENT;
            controller.setScalarIndex(currentScalar);
            controller.tick(); // rotation continues during fade
            fadeTimer--;

            if (fadeTimer <= 0) {
                complete = true;
            }
        }
    }

    public boolean isComplete() { return complete; }
    public boolean isFading() { return inFade && !complete; }
    public boolean isWindingDown() { return !inFade && !complete; }

    /** True when this tick's loc_4BC54 iteration called Pal_ToBlack. */
    public boolean palToBlackThisTick() { return palToBlackThisTick; }

    /** loc_4BC1E's {@code st (Palette_cycle_counters+$00).w}, written at the trigger. */
    public boolean stopsPaletteCycle() { return inFade; }
}
