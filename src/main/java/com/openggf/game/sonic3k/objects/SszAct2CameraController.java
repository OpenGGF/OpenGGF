package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/**
 * loc_59078's encounter camera oscillator (loc_5908E). ScreenInit seeds $30=1;
 * the arrival bounds gate Special_V_int_routine before any drift or swing runs.
 * loc_590E4 is reached by the declared ending-presentation seed after the cold
 * stop. The actual ending owner remains separate from this SSZ camera controller.
 */
public final class SszAct2CameraController extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private final S3kGradualSwing swing = new S3kGradualSwing();
    private boolean crossedZero;
    private int routine;
    private int cloudSpeed;
    private boolean aligned;
    // Camera_Y_pos+2 during loc_59194. The shared camera stores integer words;
    // this owner retains the fraction until the native ending-camera handoff.
    private int cameraFraction;

    public SszAct2CameraController(ObjectSpawn spawn) {
        super(spawn, "SszAct2Camera");
        swing.seedSpeed(1);
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var state = S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        if (state == null) return;
        if (routine == 8) { moveIslandCamera(state); return; }
        if (routine == 4) { moveEndingCamera(state); return; }
        if (routine != 0 || state.specialVIntRoutine() == 0) return;
        state.setCloudOffsetFixed(state.cloudOffsetFixed() + 0x11B);
        int offset = swing.step(0x2000, 0x4A);
        if ((short) state.eventsBgWord(0) == offset) return;
        state.setEventsBgWord(0, offset);
        if (offset != 0 || (short) state.eventsFg4() >= 0) return;
        if (!crossedZero) {
            crossedZero = true;
            return;
        }
        state.setEventsFg4(0);
        state.setSpecialVIntRoutine(0xC);
        routine = 4;
        moveEndingCamera(state); // loc_590D6 falls through, without a one-pass delay.
    }

    /** loc_590E4..59184: unsigned camera-relative gate, one pixel per object pass. */
    private void moveEndingCamera(com.openggf.game.sonic3k.runtime.SszZoneRuntimeState state) {
        boolean completeEmeralds = services().gameState().hasAllSuperEmeralds()
                || services().gameState().hasAllEmeralds();
        // $3C is the fractional low word of $3A. The unsigned limit prevents
        // overflow; widening this to a signed short would accelerate forever.
        if ((cloudSpeed & 0xFFFF) < 0x8000) cloudSpeed += 0x20;
        state.setCloudOffsetFixed(state.cloudOffsetFixed() + (completeEmeralds ? -cloudSpeed : cloudSpeed));
        var camera = services().camera();
        int relative = (camera.getY() - 0x318 - state.cloudOscillator()) & 0xFFFF;
        if (relative == 0xD0 && !aligned) {
            aligned = true;
            state.setEndingCloudAligned(true); // ST _unkFAA9, once per controller.
        } else if (completeEmeralds ? relative > 0xD0 : relative < 0xD0) {
            camera.setY((short) (camera.getY() + (completeEmeralds ? -1 : 1)));
        }
        if ((camera.getY() & 0xFFFF) == (completeEmeralds ? 0x2A0 : 0x600)) {
            state.setEventsFg4Low(0xFF);
            if (completeEmeralds) com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
            else {
                routine = 8;
                moveIslandCamera(state); // loc_59184 falls through to loc_59194.
            }
        }
    }

    /** loc_59194: wait for the second redraw, descend, then decelerate above $7D0. */
    private void moveIslandCamera(com.openggf.game.sonic3k.runtime.SszZoneRuntimeState state) {
        if (state.foregroundRoutine() < 0x18) return;
        var camera = services().camera();
        // loc_58C42 clears Camera_Y_pos+2 when publishing the $720 redraw.
        // No fractional motion occurs before that gate; keep the remainder
        // here because normal Camera movement only exposes the integer word.
        int position = ((camera.getY() & 0xFFFF) << 16) | cameraFraction;
        position += cloudSpeed;
        cameraFraction = position & 0xFFFF;
        camera.setY((short) (position >>> 16));
        if ((camera.getY() & 0xFFFF) < 0x7D0) return;
        cloudSpeed -= 0x98;
        if (cloudSpeed >= 0 || (camera.getY() & 0xFFFF) != 0x804) return;
        // Native loc_591CE deletes this controller after attempting Obj_5EF68.
        // That object starts the actual ending, outside the retained SSZ island
        // presentation. Preserve this controller's motion/lifetime but stop at
        // that documented boundary instead of fabricating an ending owner.
        com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
    }

    int routineForTest() { return routine; }
    int cloudSpeedForTest() { return cloudSpeed; }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
