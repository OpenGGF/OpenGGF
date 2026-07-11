package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestTensionBridgeBendTiming {

    @Test
    void bendUsesPriorContactSegmentBeforePublishingCurrentSegment() throws Exception {
        TensionBridgeObjectInstance bridge = new TensionBridgeObjectInstance(new ObjectSpawn(
                0x1000, 0x0788, Sonic3kObjectIds.TENSION_BRIDGE, 0x08, 0, false, 0x0788));
        ObjectManager objectManager = mock(ObjectManager.class);
        bridge.setServices(new StubObjectServices() {
            @Override public ObjectManager objectManager() { return objectManager; }
            @Override public int romZoneId() { return Sonic3kZoneIds.ZONE_HCZ; }
            @Override public int featureZoneId() { return Sonic3kZoneIds.ZONE_HCZ; }
        });
        when(objectManager.isAnyPlayerRiding(bridge)).thenReturn(true);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x1018); // current ROM segment 6
        setField(bridge, "playerOnBridge", true);
        setField(bridge, "playerSegmentIndex", 7); // prior contact segment
        setField(bridge, "depressionAngle", 0x40);

        bridge.update(0, player);

        assertEquals(-1, bridge.getSlopeData()[6 * 8],
                "first dispatch bends from prior segment 7 before sub_38A88 publishes segment 6");

        bridge.update(1, player);

        assertEquals(-4, bridge.getSlopeData()[6 * 8],
                "following dispatch consumes the segment 6 value published by the prior contact pass");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
