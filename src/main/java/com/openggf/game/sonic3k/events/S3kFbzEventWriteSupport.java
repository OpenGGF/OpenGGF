package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.level.objects.ObjectServices;

/** ObjectServices-based FBZ event write routing. */
public final class S3kFbzEventWriteSupport {
    private S3kFbzEventWriteSupport() { }

    public static void setMagneticState(ObjectServices services,
                                        Sonic3kFBZEvents.MagneticPolarity polarity,
                                        int timerPhase) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setMagneticState(polarity, timerPhase);
    }

    public static void setCloudRewindId(ObjectServices services, int index, ObjectRefId id) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setCloudRewindId(index, id);
    }

    public static void setCloudCleanupTerminal(ObjectServices services, boolean value) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setCloudCleanupTerminal(value);
    }

    public static void setBossLoadPositionAdjustmentPending(ObjectServices services, boolean value) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setBossLoadPositionAdjustmentPending(value);
    }

    public static int getAct2ForegroundStage(ObjectServices services) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        return bridge == null ? 0 : bridge.getAct2ForegroundStage();
    }

    public static void setAct2ForegroundStage(ObjectServices services, int stage) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setAct2ForegroundStage(stage);
    }

    public static int getBossBackgroundOffsetX(ObjectServices services) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        return bridge == null ? 0 : bridge.getBossBackgroundOffsetX();
    }

    public static int getBossBackgroundOffsetY(ObjectServices services) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        return bridge == null ? 0 : bridge.getBossBackgroundOffsetY();
    }

    public static void setBossBackgroundOffsets(ObjectServices services, int x, int y) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setBossBackgroundOffsets(x, y);
    }

    public static void setBossApproachMotionState(ObjectServices services, int x, int y,
                                                   boolean collisionActive) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setBossApproachMotionState(x, y, collisionActive);
    }

    public static void setPlaneAssignmentMode(ObjectServices services, Sonic3kFBZEvents.PlaneAssignmentMode plane) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setPlaneAssignmentMode(plane);
    }

    public static void setCollisionMode(ObjectServices services, Sonic3kFBZEvents.CollisionMode collision,
                                        int cameraDiffX, int cameraDiffY) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setCollisionMode(collision, cameraDiffX, cameraDiffY);
    }

    public static void setScreenShakeState(ObjectServices services, boolean active, int offset, int phase) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setScreenShakeState(active, offset, phase);
    }

    public static boolean isScreenShakeActive(ObjectServices services) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        return bridge != null && bridge.isScreenShakeActive();
    }

    public static void setScreenShakeActive(ObjectServices services, boolean active) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setScreenShakeActive(active);
    }

    private static FbzObjectEventBridge bridgeOrNull(ObjectServices services) {
        Object provider = services.levelEventProvider();
        return provider instanceof FbzObjectEventBridge bridge ? bridge : null;
    }
}
