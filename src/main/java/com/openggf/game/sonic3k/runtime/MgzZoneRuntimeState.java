package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kMGZEvents;

import java.util.Objects;

public final class MgzZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private final Sonic3kMGZEvents events;
    private int pendingScreenShakeOffset;
    private int currentScreenShakeOffset;
    private boolean bossBgScrollOffsetPublished;

    public MgzZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter, Sonic3kMGZEvents events) {
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_MGZ; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return events.getDynamicResizeRoutine(); }
    @Override public boolean isActTransitionFlagActive() { return events.isEventsFg5Raw(); }

    public int bgRiseRoutine() {
        return events.getBgRiseRoutine();
    }

    public int bgRiseOffset() {
        return events.getBgRiseOffset();
    }

    public int bossBgScrollOffset() {
        return events.getBossBgScrollOffset();
    }

    public boolean hasBossBgScrollOffset() {
        return bossBgScrollOffsetPublished;
    }

    public void publishBossBgScrollOffset(int offset) {
        events.setBossBgScrollOffset(offset & 0xFFFF);
        bossBgScrollOffsetPublished = true;
    }

    public void clearBossBgScrollOffset() {
        bossBgScrollOffsetPublished = false;
    }

    public void requestScreenShakeOffset(int offset) {
        if (offset > pendingScreenShakeOffset) {
            pendingScreenShakeOffset = offset;
        }
    }

    public int consumeScreenShakeOffset() {
        currentScreenShakeOffset = pendingScreenShakeOffset;
        pendingScreenShakeOffset = 0;
        return currentScreenShakeOffset;
    }

    public int currentScreenShakeOffset() {
        return currentScreenShakeOffset;
    }

    public void clearScreenShakeOffset() {
        pendingScreenShakeOffset = 0;
        currentScreenShakeOffset = 0;
    }
}
