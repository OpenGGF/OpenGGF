package com.openggf.tests;

import org.junit.jupiter.api.Test;
import com.openggf.audio.synth.Ym2612Chip;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ensures YM2612 voice loading accepts both 19-byte (S2) and 25-byte (TL-inclusive) voices,
 * and that the parsed voice bytes actually reach the chip's operator/channel registers.
 *
 * <p>Voice-byte -> register mapping (see Ym2612Chip.setInstrument):
 * <ul>
 *   <li>byte 0 -> feedback/algorithm (low 3 bits = algorithm).</li>
 *   <li>TL bytes live at voice indices 21,23,22,24 (only present in 25-byte voices)
 *       and are written to slots 0..3, which map to operators 0,2,1,3 respectively.
 *       19-byte voices have no TL bytes, so every operator TL stays 0.</li>
 * </ul>
 */
public class TestYm2612VoiceLengths {

    @Test
    public void accepts19ByteVoice() {
        Ym2612Chip chip = new Ym2612Chip();
        byte[] voice = new byte[19];
        // Algorithm 5 in the low 3 bits of byte 0; feedback in bits 3-5.
        voice[0] = (byte) 0x05;
        chip.setInstrument(0, voice);

        // Byte 0 reached the channel algorithm register.
        assertEquals(5, chip.getChannelAlgorithmForTest(0), "Algorithm byte should reach the channel");

        // A 19-byte voice carries no TL bytes, so every operator TL must remain 0.
        for (int op = 0; op < 4; op++) {
            assertEquals(0, chip.getOperatorTotalLevelForTest(0, op),
                    "19-byte voice must leave operator " + op + " TL at 0");
        }
    }

    @Test
    public void accepts25ByteVoice() {
        Ym2612Chip chip = new Ym2612Chip();
        byte[] voice = new byte[25];
        voice[0] = (byte) 0x06; // algorithm 6

        // Distinct TL values (< 0x80) at the 25-byte-voice TL positions.
        voice[21] = (byte) 0x11; // slot 0 -> operator 0
        voice[23] = (byte) 0x22; // slot 1 -> operator 2
        voice[22] = (byte) 0x33; // slot 2 -> operator 1
        voice[24] = (byte) 0x44; // slot 3 -> operator 3

        chip.setInstrument(0, voice);

        assertEquals(6, chip.getChannelAlgorithmForTest(0), "Algorithm byte should reach the channel");

        // The extra TL bytes must reach the operator TL registers, distinguishing
        // the 25-byte parse from the 19-byte one (which would leave these at 0).
        assertEquals(0x11, chip.getOperatorTotalLevelForTest(0, 0), "voice[21] -> operator 0 TL");
        assertEquals(0x22, chip.getOperatorTotalLevelForTest(0, 2), "voice[23] -> operator 2 TL");
        assertEquals(0x33, chip.getOperatorTotalLevelForTest(0, 1), "voice[22] -> operator 1 TL");
        assertEquals(0x44, chip.getOperatorTotalLevelForTest(0, 3), "voice[24] -> operator 3 TL");
    }
}



