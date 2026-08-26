package com.openggf.game.sonic3k.audio.smps;

import com.openggf.data.Rom;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kModEnvelopeRetailBugs {

    @Test
    void loopOperandsComeFromTheRetailDriversBogusBcPointer() {
        Rom rom = TestEnvironment.currentRom();
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(rom);

        assertLoopOperand(loader, 4, 0x09);
        assertLoopOperand(loader, 5, 0x06);
        assertLoopOperand(loader, 6, 0x66);
        assertLoopOperand(loader, 7, 0x23);
        assertLoopOperand(loader, 8, 0x66);
    }

    private static void assertLoopOperand(
            Sonic3kSmpsLoader loader, int envelopeId, int expected) {
        byte[] envelope = loader.getModEnvelopes().get(envelopeId);
        assertEquals(0x82, envelope[envelope.length - 2] & 0xff);
        assertEquals(expected, envelope[envelope.length - 1] & 0xff,
                "modulation envelope " + envelopeId);
    }
}
