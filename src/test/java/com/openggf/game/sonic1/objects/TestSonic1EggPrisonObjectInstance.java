package com.openggf.game.sonic1.objects;

import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic1EggPrisonObjectInstance {

    @Test
    void capsuleBalanceUsesNativeWidthWithoutSolidPadding() {
        Sonic1EggPrisonObjectInstance prison = new Sonic1EggPrisonObjectInstance(
                new ObjectSpawn(0x100, 0x100, Sonic1ObjectIds.EGG_PRISON, 0, 0, false, 0));

        assertEquals(0x2B, prison.getSolidParams().halfWidth(),
                "Pri_BodyMain adds Sonic's $B width for SolidObject");
        assertEquals(0x20, prison.getBalanceWidthPixels(),
                "Sonic_Move reads Obj3E's unpadded obActWid");
    }
}
