package com.openggf.game.sonic3k.events;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzEventWriteSupport {
    @Test
    void routesObjectWritesOnlyThroughFbzBridge() {
        RecordingBridge bridge = new RecordingBridge();
        Services services = new Services(bridge);

        S3kFbzEventWriteSupport.setMagneticState(services, Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 9);
        S3kFbzEventWriteSupport.setCloudRewindId(services, 2, ObjectRefId.dynamic(1, 2, 3));
        S3kFbzEventWriteSupport.setBossLoadPositionAdjustmentPending(services, true);
        S3kFbzEventWriteSupport.setBossBackgroundOffsets(services, 12, -14);
        S3kFbzEventWriteSupport.setPlaneAssignmentMode(services, Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED);
        S3kFbzEventWriteSupport.setCollisionMode(services,
                Sonic3kFBZEvents.CollisionMode.FOREGROUND_AND_BACKGROUND, 4, 5);
        S3kFbzEventWriteSupport.setScreenShakeState(services, true, -2, 7);

        assertEquals(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, bridge.polarity);
        assertEquals(9, bridge.phase);
        assertEquals(ObjectRefId.dynamic(1, 2, 3), bridge.cloudId);
        assertTrue(bridge.adjustmentPending);
        assertEquals(12, bridge.offsetX);
        assertEquals(-14, bridge.offsetY);
        assertEquals(Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED, bridge.planeMode);
        assertEquals(Sonic3kFBZEvents.CollisionMode.FOREGROUND_AND_BACKGROUND, bridge.collisionMode);
        assertTrue(bridge.shakeActive);
    }

    @Test
    void ignoresWritesWhenCurrentProviderIsNotFbzBridge() {
        Services services = new Services(new LevelEventProvider() {
            @Override public void initLevel(int zone, int act) { }
            @Override public void update() { }
        });
        assertDoesNotThrow(() -> S3kFbzEventWriteSupport.setBossLoadPositionAdjustmentPending(services, true));
    }

    private static final class Services extends TestObjectServices {
        private final LevelEventProvider provider;
        private Services(LevelEventProvider provider) { this.provider = provider; }
        @Override public LevelEventProvider levelEventProvider() { return provider; }
    }

    private static final class RecordingBridge implements FbzObjectEventBridge, LevelEventProvider {
        Sonic3kFBZEvents.MagneticPolarity polarity;
        int phase;
        ObjectRefId cloudId;
        boolean adjustmentPending;
        int offsetX, offsetY;
        Sonic3kFBZEvents.PlaneAssignmentMode planeMode;
        Sonic3kFBZEvents.CollisionMode collisionMode;
        boolean shakeActive;
        @Override public void initLevel(int zone, int act) { }
        @Override public void update() { }
        @Override public void setMagneticState(Sonic3kFBZEvents.MagneticPolarity polarity, int timerPhase) { this.polarity = polarity; this.phase = timerPhase; }
        @Override public void setCloudRewindId(int index, ObjectRefId id) { cloudId = id; }
        @Override public void setCloudCleanupTerminal(boolean value) { }
        @Override public void setBossLoadPositionAdjustmentPending(boolean value) { adjustmentPending = value; }
        @Override public void setBossBackgroundOffsets(int x, int y) { offsetX = x; offsetY = y; }
        @Override public void setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode plane) { planeMode = plane; }
        @Override public void setCollisionMode(Sonic3kFBZEvents.CollisionMode collision, int diffX, int diffY) { collisionMode = collision; }
        @Override public void setScreenShakeState(boolean active, int offset, int phase) { shakeActive = active; }
    }
}
