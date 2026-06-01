package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;

import java.util.Objects;

public final class LbzZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private boolean alarmAnimationActive;

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
}
