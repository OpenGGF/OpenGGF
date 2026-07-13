package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;

import java.util.Objects;

public final class HczZoneRuntimeState implements S3kZoneRuntimeState {
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private final Sonic3kHCZEvents events;
    private boolean largeFanModulePrimed;

    public HczZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter, Sonic3kHCZEvents events) {
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_HCZ; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return events.getDynamicResizeRoutine(); }
    @Override public boolean isActTransitionFlagActive() { return events.isEventsFg5(); }
    public boolean isBackedBy(Sonic3kHCZEvents candidate) { return events == candidate; }

    /**
     * Whether the HCZ2 wall-chase BG high-priority overlay is currently active.
     * Drives the staged {@code HczWallChaseBgOverlayEffect} render pass.
     */
    public boolean wallChaseBgOverlayActive() {
        return events.isWallChaseBgOverlayActive();
    }

    /** Whether HCZ2 is using the post-chase 512px Plane B nametable. */
    public boolean normalBackgroundPlaneActive() {
        return actIndex == 1 && events.isAct2NormalBackgroundPlaneActive();
    }

    /** Claims the shared Obj39 KosM queue delay for this HCZ gameplay session. */
    public int claimLargeFanModuleWaitFrames() {
        int waitFrames = largeFanModulePrimed ? 2 : 3;
        largeFanModulePrimed = true;
        return waitFrames;
    }

    @Override
    public byte[] captureBytes() {
        return new byte[]{(byte) (largeFanModulePrimed ? 1 : 0)};
    }

    @Override
    public void restoreBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 1) {
            throw new IllegalArgumentException("HCZ runtime snapshot must contain exactly one byte");
        }
        largeFanModulePrimed = bytes[0] != 0;
    }
}
