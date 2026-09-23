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
 * The later loc_590E4/loc_59194 camera sequence belongs to the excluded ending,
 * beyond the accepted cold stop at loc_7BCFC. The pending phase remains captured.
 */
public final class SszAct2CameraController extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private final S3kGradualSwing swing = new S3kGradualSwing();
    private boolean crossedZero;
    private int routine;

    public SszAct2CameraController(ObjectSpawn spawn) {
        super(spawn, "SszAct2Camera");
        swing.seedSpeed(1);
    }

    @Override public void update(int clock, PlayableEntity player) {
        var state = S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        if (state == null || state.specialVIntRoutine() == 0 || routine != 0) return;
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
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
