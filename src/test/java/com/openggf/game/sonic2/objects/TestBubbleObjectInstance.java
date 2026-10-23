package com.openggf.game.sonic2.objects;

import com.openggf.game.GameRng;
import com.openggf.data.RomByteReader;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestBubbleObjectInstance {
    @Test
    void romInitializedBubbleConsumesAngleRngOnFirstObjectPass() {
        GameRng control = new GameRng(GameRng.Flavour.S1_S2, 0xB4BD7B3AL);
        control.nextRaw();

        GameRng actual = new GameRng(GameRng.Flavour.S1_S2, 0xB4BD7B3AL);
        BubbleObjectInstance bubble = new BubbleObjectInstance(0x0C9B, 0x076A, 1, 0, true);
        // This RNG-only unit owns a minimal script1 reader. Actual retail
        // scripts, wobble bytes and lifetime are exercised by the ROM regression.
        byte[] bytes = new byte[0x1FBF6];
        bytes[0x1FBCF] = 0x13;
        bytes[0x1FBDF] = 0x0E;
        bytes[0x1FBE0] = 1;
        bubble.setServices(new TestObjectServices().withRng(actual)
                .withRomReader(RomByteReader.fromBytes(bytes)));

        assertEquals(0xB4BD7B3AL, actual.getSeed());

        bubble.update(0, null);

        assertEquals(control.getSeed(), actual.getSeed());
    }
}
