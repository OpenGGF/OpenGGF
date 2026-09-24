package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;

import java.util.List;

/** ROM {@code Obj_59FC4}: the LRZ3 arena's solid, horizontally deforming lava surface. */
public final class Lrz3LavaSurfaceObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, RewindRecreatable {
    private static final int HALF_WIDTH = 0x180;
    private final byte[] slope = new byte[HALF_WIDTH];
    private int x = 0xB80;

    public Lrz3LavaSurfaceObjectInstance() {
        super(new ObjectSpawn(0xB80,0x640,0,0,0,false,0), "LRZ3LavaSurface");
        for (int i=0;i<slope.length;i++) slope[i] = 0x30;
    }

    @Override public void update(int frame, PlayableEntity player) {
        if (services().zoneRuntimeState() instanceof LrzZoneRuntimeState state
                && state.backgroundRoutine() == 0x0C) {
            x = 0xAA0;
            // Obj_59FC4's $00..$80 phase bends the $30 baseline into the boss waves.
            int phase = frame & 0x7F;
            for (int i=0;i<slope.length;i++) {
                slope[i] = (byte) (0x30 + ((phase * i / Math.max(1,slope.length-1)) >> 3));
            }
        }
        updateDynamicSpawn(x,0x640);
    }
    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(HALF_WIDTH,0x40,0x30); }
    @Override public SolidRoutineProfile getSolidRoutineProfile() { return SolidRoutineProfile.topSolid(false); }
    @Override public byte[] getSlopeData() { return slope; }
    @Override public boolean isSlopeFlipped() { return false; }
    @Override public int getSlopeSampleShift() { return 0; }
    @Override public int getSlopeBaseline() { return 0; }
    @Override public int getX() { return x; }
    @Override public int getY() { return 0x640; }
    @Override public int getOnScreenHalfWidth() { return HALF_WIDTH; }
    @Override public int getOnScreenHalfHeight() { return 0xC0; }
    @Override public boolean isPersistent() { return true; }
    @Override public Lrz3LavaSurfaceObjectInstance recreateForRewind(RewindRecreateContext c) {
        return new Lrz3LavaSurfaceObjectInstance();
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
