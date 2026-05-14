package com.openggf.tests.trace.s2;

import com.openggf.game.sonic2.objects.HTZLiftObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestS2HtzLiftPlatformSurfaceRegression {

    @Test
    void htzLiftPlatformSurfaceMatchesObj16PlatformObjectHeight() {
        int objectY = 0x03BE;
        HTZLiftObjectInstance lift = new HTZLiftObjectInstance(
                new ObjectSpawn(0x01A0, objectY, 0x16, 0x14, 0, false, 0),
                "HTZLift");

        SolidObjectParams params = lift.getSolidParams();

        // S2 Obj16 passes d3 = -$28 before JmpTo3_PlatformObject
        // (docs/s2disasm/s2.asm:47384-47388). Platform riding and landing
        // therefore use surfaceY = y_pos - d3 = y_pos + $28.
        assertEquals(objectY + 0x28, objectY + params.offsetY() - params.groundHalfHeight());
    }

    @Test
    void htzLiftFirstStandingFrameArmsSlideWithoutMovingUntilNextObjectUpdate() {
        ObjectManager objectManager = mock(ObjectManager.class);
        HTZLiftObjectInstance lift = new HTZLiftObjectInstance(
                new ObjectSpawn(0x01A0, 0x03BE, 0x16, 0x14, 0, false, 0),
                "HTZLift");
        lift.setServices(new TestObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }
        });
        when(objectManager.isAnyPlayerRiding(lift)).thenReturn(true);

        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        lift.onSolidContact(player, new SolidContact(true, false, false, true, false), 147);

        lift.update(148, null);
        assertEquals(0x01A0, lift.getX());
        assertEquals(0x03BE, lift.getY());

        lift.update(149, null);
        assertEquals(0x01A2, lift.getX());
        assertEquals(0x03BF, lift.getY());
    }
}
