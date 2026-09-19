package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** {@code loc_59078}: persistent SSZ2 ending camera and deformation controller. */
public final class SszAct2EndingCameraController extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int SWING_SPEED = 0x2000;
    private static final int SWING_ACCELERATION = 0x4A;

    private final S3kGradualSwing swing = new S3kGradualSwing();
    private int routine;
    private boolean firstZeroSeen;
    private boolean alignmentSignalled;
    /** ROM longword at $3A, whose low word at $3C accelerates by $20. */
    private int cameraVelocity;
    /** ROM longword `_unkEE9C`; its high word is read as the deformation offset. */
    private int motionAccumulator;
    private int cameraYFixed;

    public SszAct2EndingCameraController(ObjectSpawn spawn) {
        super(spawn, "SSZ2 ending camera controller");
    }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        SszZoneRuntimeState state = (SszZoneRuntimeState) services().zoneRuntimeState();
        switch (routine) {
            case 0 -> waitForEndingSignal(state);
            case 4 -> moveToEndingStage(state);
            case 8 -> finishEndingRise(state);
            default -> { }
        }
    }

    private void waitForEndingSignal(SszZoneRuntimeState state) {
        if (state.specialVIntRoutine() == 0) return;
        motionAccumulator += 0x11B;
        state.setCloudOscillator(motionAccumulator >> 16);
        int offset = swing.step(SWING_SPEED, SWING_ACCELERATION);
        if (offset == state.eventsBgWord(0)) return;
        state.setEventsBgWord(0, offset);
        if (offset != 0 || (short) state.eventsFg4() >= 0) return;
        if (!firstZeroSeen) {
            firstZeroSeen = true;
            return;
        }
        state.setEventsFg4(0);
        state.setSpecialVIntRoutine(0x0C);
        // The ROM's special V-int path owns Camera_Y_pos directly from here; suppress
        // the engine's ordinary player-follow pass while that owner is active.
        services().camera().setFrozen(true);
        cameraYFixed = services().camera().getY() << 16;
        routine = 4;
    }

    private void moveToEndingStage(SszZoneRuntimeState state) {
        if ((cameraVelocity & 0xFFFF) < 0x8000) cameraVelocity += 0x20;
        boolean allEmeralds = services().gameState().getCollectedSuperEmeraldIndices().size() >= 7
                || services().gameState().getCollectedChaosEmeraldIndices().size() >= 7;
        motionAccumulator += allEmeralds ? -cameraVelocity : cameraVelocity;
        state.setCloudOscillator(motionAccumulator >> 16);

        int y = services().camera().getY() & 0xFFFF;
        int alignment = y - 0x318 - (short) state.cloudOscillator();
        if (alignment == 0xD0) alignmentSignalled = true;
        if (allEmeralds && alignment > 0xD0) y--;
        else if (!allEmeralds && alignment < 0xD0) y++;
        services().camera().setY((short) y);

        int target = allEmeralds ? 0x2A0 : 0x600;
        if (y != target) return;
        state.setEventsFg4Low(0xFF);
        if (allEmeralds) {
            ObjectLifetimeOps.deleteNoRespawn(this);
        } else {
            cameraYFixed = y << 16;
            routine = 8;
        }
    }

    private void finishEndingRise(SszZoneRuntimeState state) {
        if (state.foregroundRoutine() < 0x18) return;
        cameraYFixed += cameraVelocity;
        int y = cameraYFixed >> 16;
        services().camera().setY((short) y);
        if (y < 0x7D0) return;
        cameraVelocity -= 0x98;
        if (cameraVelocity >= 0 || y != 0x804) return;
        // Obj_5EF68 is owned by the shared ending campaign; this zone-owned controller
        // stops at its allocation boundary.
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    public int routineForTest() { return routine; }
    public boolean alignmentSignalledForTest() { return alignmentSignalled; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) { }
}
