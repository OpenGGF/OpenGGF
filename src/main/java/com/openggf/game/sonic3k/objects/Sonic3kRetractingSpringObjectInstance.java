package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Shared object $3D, {@code Obj_RetractingSpring}.
 *
 * <p>The locked-on routine first initializes the ordinary {@code Obj_Spring}
 * profile, then applies {@code sub_23AB8}'s 8.8 offset to that spring's native
 * position. Physics, art, collision, player participation and launch behavior
 * therefore remain owned by {@link Sonic3kSpringObjectInstance}.</p>
 */
public final class Sonic3kRetractingSpringObjectInstance extends Sonic3kSpringObjectInstance {
    private static final int OFFSET_STEP = 0x800;
    private static final int OFFSET_MAX = 0x2000;
    private static final int HOLD_TICKS = 60;

    /** ROM $36, an 8.8 fixed-point displacement read back as its high byte. */
    private int offsetFixed;
    /** ROM $38: zero while extending, one while retracting. */
    private boolean retracting;
    /** ROM $3A. */
    private int holdTimer;
    /** Retained ROM render_flags bit 7 from the preceding Render_Sprites pass. */
    private boolean visibleOnPriorRenderPass;

    public Sonic3kRetractingSpringObjectInstance(ObjectSpawn spawn) {
        super(spawn);
    }

    @Override
    public int getX() {
        return switch (springDirectionIndex()) {
            case 1 -> spawn.x() + offsetPixels();
            case 3 -> spawn.x() + (isFlippedForMotion() ? offsetPixels() : -offsetPixels());
            case 4 -> spawn.x() + (isFlippedForMotion() ? -offsetPixels() : offsetPixels());
            default -> spawn.x();
        };
    }

    @Override
    public int getY() {
        return switch (springDirectionIndex()) {
            case 0, 2, 3, 4 -> spawn.y() + offsetPixels();
            default -> spawn.y();
        };
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        updateRetractionCycle();
        super.update(vIntRunCount, playerEntity);
    }

    private void updateRetractionCycle() {
        if (holdTimer != 0) {
            holdTimer--;
            if (holdTimer == 0 && visibleOnPriorRenderPass) {
                services().playSfx(Sonic3kSfx.SPIKE_MOVE.id);
            }
            return;
        }

        if (retracting) {
            offsetFixed -= OFFSET_STEP;
            if (offsetFixed < 0) {
                offsetFixed = 0;
                retracting = false;
                holdTimer = HOLD_TICKS;
            }
            return;
        }

        offsetFixed += OFFSET_STEP;
        if (offsetFixed >= OFFSET_MAX) {
            offsetFixed = OFFSET_MAX;
            retracting = true;
            holdTimer = HOLD_TICKS;
        }
    }

    int offsetPixels() {
        return (offsetFixed >>> 8) & 0xFF;
    }

    int holdTimer() {
        return holdTimer;
    }

    boolean retracting() {
        return retracting;
    }

    @Override
    public void refreshPostCameraRenderState() {
        visibleOnPriorRenderPass = isWithinRenderSpriteBounds(
                getOnScreenHalfWidth(), getOnScreenHalfHeight());
    }

    private int springDirectionIndex() {
        return ((spawn.subtype() & 0xFF) >>> 4) & 0x7;
    }

    private boolean isFlippedForMotion() {
        return (spawn.renderFlags() & 1) != 0;
    }
}
