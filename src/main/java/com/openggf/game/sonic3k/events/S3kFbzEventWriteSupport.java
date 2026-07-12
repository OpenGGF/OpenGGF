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

    public static void setBossBackgroundOffsets(ObjectServices services, int x, int y) {
        FbzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) bridge.setBossBackgroundOffsets(x, y);
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

    private static FbzObjectEventBridge bridgeOrNull(ObjectServices services) {
        Object provider = services.levelEventProvider();
        return provider instanceof FbzObjectEventBridge bridge ? bridge : null;
    }
}
