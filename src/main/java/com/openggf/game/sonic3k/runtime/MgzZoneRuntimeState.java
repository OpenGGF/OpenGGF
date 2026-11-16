package com.openggf.game.sonic3k.runtime;

import com.openggf.physics.BackgroundPlaneCollisionProvider;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kMGZEvents;

import java.util.Objects;

public final class MgzZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private final Sonic3kMGZEvents events;
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
    public boolean isBackedBy(Sonic3kMGZEvents candidate) { return events == candidate; }

    public int bgRiseRoutine() { return events.getBgRiseRoutine(); }
    public int bgRiseOffset() { return events.getBgRiseOffset(); }
    public int bossBgScrollOffset() { return events.getBossBgScrollOffset(); }
    public boolean hasBossBgScrollOffset() { return events.getScreenEventRoutine() == 8; }
    public void publishBossBgScrollOffset(int offset) { events.setBossBgScrollOffset(offset & 0xFFFF); }

    public void writeScreenShakeFlag(int flag) { events.writeScreenShakeFlag(flag); }
    public void requestContinuousScreenShake() { events.requestContinuousScreenShake(); }
    public void clearScreenShakeFlag() { events.clearScreenShakeFlag(); }
    public Sonic3kMGZEvents.ScreenPublication screenPublication() { return events.screenPublication(); }
    @Override public BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
        return actIndex == 1 ? events.backgroundCollisionState() : null;
    }
}
