package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.identity.ObjectRefId;

/** Narrow state bridge from FBZ objects to their canonical event handler. */
public interface FbzObjectEventBridge {
    void setMagneticState(Sonic3kFBZEvents.MagneticPolarity polarity, int timerPhase);
    void setCloudRewindId(int index, ObjectRefId id);
    void setCloudCleanupTerminal(boolean value);
    void setBossLoadPositionAdjustmentPending(boolean value);
    default int getAct2ForegroundStage() { return 0; }
    default void setAct2ForegroundStage(int stage) { }
    default int getBossBackgroundOffsetX() { return 0; }
    default int getBossBackgroundOffsetY() { return 0; }
    void setBossBackgroundOffsets(int x, int y);
    default void setBossApproachMotionState(int x, int y, boolean collisionActive) {
        setBossBackgroundOffsets(x, y);
    }
    void setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode plane);
    void setCollisionMode(Sonic3kFBZEvents.CollisionMode collision, int cameraDiffX, int cameraDiffY);
    void setScreenShakeState(boolean active, int offset, int phase);
    default void setScreenShakeActive(boolean active) { }
    default boolean isScreenShakeActive() { return false; }
}
