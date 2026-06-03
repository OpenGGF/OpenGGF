package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class LbzZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private boolean alarmAnimationActive;
    private int rollingDrumP1Angle;
    private int rollingDrumP2Angle;

    public LbzZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter) {
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_LBZ; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public boolean isActTransitionFlagActive() { return false; }

    public boolean isAlarmAnimationActive() {
        return alarmAnimationActive;
    }

    public void setAlarmAnimationActive(boolean alarmAnimationActive) {
        this.alarmAnimationActive = alarmAnimationActive;
    }

    public int getRollingDrumAngle(int nativePlayerIndex) {
        return nativePlayerIndex == 0 ? rollingDrumP1Angle : rollingDrumP2Angle;
    }

    public void setRollingDrumAngle(int nativePlayerIndex, int angle) {
        int normalized = angle & 0xFF;
        if (nativePlayerIndex == 0) {
            rollingDrumP1Angle = normalized;
        } else {
            rollingDrumP2Angle = normalized;
        }
    }

    @Override
    public byte[] captureBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(3);
        buffer.put((byte) (alarmAnimationActive ? 1 : 0));
        buffer.put((byte) rollingDrumP1Angle);
        buffer.put((byte) rollingDrumP2Angle);
        return buffer.array();
    }

    @Override
    public void restoreBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        alarmAnimationActive = bytes[0] != 0;
        if (bytes.length >= 2) {
            rollingDrumP1Angle = bytes[1] & 0xFF;
        }
        if (bytes.length >= 3) {
            rollingDrumP2Angle = bytes[2] & 0xFF;
        }
    }
}
