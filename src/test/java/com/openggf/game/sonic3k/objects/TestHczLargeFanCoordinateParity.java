package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestHczLargeFanCoordinateParity {

    @Test
    void activationWindowUsesPlayerRomCentrePosition() {
        int fanX = 0x0B80;
        int fanY = 0x0580;
        HCZLargeFanObjectInstance fan = new HCZLargeFanObjectInstance(
                new ObjectSpawn(fanX, fanY, Sonic3kObjectIds.HCZ_LARGE_FAN, 0, 0, false, 0));
        fan.setServices(new StubObjectServices());

        TestablePlayableSprite player = standingPlayer();
        player.setCentreX((short) (fanX + 0x20));
        player.setCentreY((short) (fanY + 0x20));

        fan.update(1, player);

        assertEquals(fanY + 8, fan.getY(),
                "Obj_HCZLargeFan compares ROM x_pos/y_pos, which map to player centre coordinates");
    }

    private static TestablePlayableSprite standingPlayer() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setWidth(18);
        player.setHeight(38);
        return player;
    }
}
