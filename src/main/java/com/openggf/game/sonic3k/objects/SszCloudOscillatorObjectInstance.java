package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/**
 * ROM {@code loc_57B6A}/{@code loc_57B76} (sonic3k.asm:116712-116723): the invisible object
 * {@code SSZ1_BackgroundInit} allocates first, whose only job is to drive {@code _unkEE9C}.
 *
 * <p>Its init pass seeds {@code $30(a0)} — the low word of {@code Gradual_SwingOffset}'s
 * {@code $2E} speed longword — with {@code $8000}, so the first swing starts downward with
 * speed {@code $8000} instead of taking the routine's "no speed yet" reset branch. Every frame
 * afterwards it calls {@code Gradual_SwingOffset} with initial speed {@code $8000} and
 * acceleration {@code $100} and stores the returned integer offset in {@code _unkEE9C}.
 *
 * <p>Three owners read that word: {@code sub_579F0} adds it to the plain background Y,
 * {@code sub_57A60} folds half of it into the cloud-band background Y, and
 * {@code loc_57B8E} subtracts it from every solid cloud's {@code y_pos}, so the platforms and
 * the sky they sit in breathe together.
 */
public final class SszCloudOscillatorObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    /** {@code loc_57B76}: {@code move.l #$8000,d0}. */
    static final int SWING_SPEED = 0x8000;
    /** {@code loc_57B76}: {@code move.l #$100,d1}. */
    static final int SWING_ACCELERATION = 0x100;

    private final S3kGradualSwing swing = new S3kGradualSwing();

    public SszCloudOscillatorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SszCloudOscillator");
        // loc_57B6A: move.w #$8000,$30(a0) pre-seeds the low word of the $2E speed longword.
        swing.seedSpeed(SWING_SPEED);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        SszZoneRuntimeState state = sszState();
        if (state == null) {
            return;
        }
        state.setCloudOscillator(swing.step(SWING_SPEED, SWING_ACCELERATION));
    }

    /**
     * {@code loc_57B76} has no {@code Sprite_OnScreen_Test}, {@code Delete_Sprite_If_Not_In_Range} or
     * any other range check: {@code SSZ1_BackgroundInit} allocates it once at load and it runs for
     * the whole act. Without this the engine's {@code MarkObjGone} equivalent unloads it the
     * first frame its position leaves the camera window.
     */
    @Override
    public boolean isPersistent() {
        return true;
    }

    /** Invisible: {@code loc_57B76} never calls a draw routine. */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }


    private SszZoneRuntimeState sszState() {
        return tryServices() == null ? null
                : S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
    }
}
