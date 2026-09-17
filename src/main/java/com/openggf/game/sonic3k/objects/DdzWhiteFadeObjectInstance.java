package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * Doomsday white-palette effects built on {@code sub_85EB4} and {@code loc_85EE6}
 * (sonic3k.asm:180664-180760).
 *
 * <p>{@link Mode#DDZ_FLASH} is {@code loc_83108}: five whitening passes every four frames
 * ({@code $39 = 4}, {@code $3A = 3}, the first on the allocation frame), then {@code loc_85EE6}: eight
 * passes every four frames back down to {@code Target_palette}, after which it clears
 * {@code Palette_rotation_disable} and deletes itself.
 *
 * <p>{@link Mode#TO_WHITE_HOLD} is {@code loc_85E64} subtype 0 with {@code $3A = 7}: it sets
 * {@code Palette_rotation_disable}, whitens eight times every eight frames, then clears the flag and
 * deletes itself ({@code Go_Delete_Sprite} sets {@code status} bit 7, which the exit polls).
 */
final class DdzWhiteFadeObjectInstance extends AbstractDdzObjectInstance {
    enum Mode { DDZ_FLASH, TO_WHITE_HOLD }

    private final Mode mode;
    private boolean restoring;
    /** {@code $2E}, {@code $39}. */
    private int wait;
    private int steps;
    private boolean finished;
    private boolean started;


    DdzWhiteFadeObjectInstance(Mode mode) {
        super(new ObjectSpawn(0, 0, 0, mode.ordinal(), 0, false, 0), "DDZWhiteFade", null);
        this.mode = mode;
        steps = mode == Mode.DDZ_FLASH ? 4 : 7;
    }

    @Override
    public DdzWhiteFadeObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzWhiteFadeObjectInstance(Mode.values()[ctx.spawn().subtype()]);
    }

    /** {@code btst #7,status(a1)} on the fade object. */
    boolean finished() {
        return finished || isDestroyed();
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        var registry = services().paletteOwnershipRegistryOrNull();
        if (!started) {
            started = true;
            if (mode == Mode.TO_WHITE_HOLD && registry != null) {
                // st (Palette_rotation_disable).w
                registry.setPaletteRotationDisabled(true);
            }
        }
        wait = (short) (wait - 1);
        if (wait >= 0) {
            return;
        }
        if (restoring) {
            // loc_85EF8
            wait = 3;
            DdzPalette.fadeTowardsTarget(services());
            steps = (steps - 1) & 0xFF;
            if ((byte) steps < 0) {
                finish(registry);
            }
            return;
        }
        wait = mode == Mode.DDZ_FLASH ? 3 : 7;
        DdzPalette.whitenAll(services());
        steps = (steps - 1) & 0xFF;
        if ((byte) steps >= 0) {
            return;
        }
        if (mode == Mode.DDZ_FLASH) {
            // move.l #loc_85EE6,(a0): $39 = 7, $2E = 3.
            restoring = true;
            steps = 7;
            wait = 3;
            return;
        }
        finish(registry);
    }

    private void finish(com.openggf.game.palette.PaletteOwnershipRegistry registry) {
        if (registry != null) {
            registry.setPaletteRotationDisabled(false);
        }
        // Go_Delete_Sprite: bset #7,status(a0) is visible to the exit at once.
        finished = true;
        goDelete();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }


}
