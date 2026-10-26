package com.openggf.game.sonic3k.timing;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kAizDeformInstructionWork {
    @Test void calleeCostEqualsTheIndependentR16InstructionHandSum() {
        int prefix = Arrays.stream(new int[]{12,8,12,12,8,4,4,18,4,8,4,14,8,8,4,8,4,8,4,12,
                12,12,4,8,4,12,12,4,8,4,12,8,4,4,8,4,16,32,10,4}).sum();
        int hand = prefix + 6*(8+4+8+4+8) + 5*10 + 14 + (8+4+14+4)
                + 13*(8+4+8+4) + 12*10 + 14 + 16;
        assertEquals(1094,hand); assertEquals(hand,S3kAizDeformInstructionWork.INSTRUCTION_CYCLES);
    }
    @Test void unexecutedAndUnsupportedTermsAreUnavailableRatherThanZero() {
        var work = new S3kAizDeformInstructionWork();
        assertEquals(-1,work.lastInstructionCycles()); assertEquals(0,work.executionCount());
        work.recordExecution(null,17);
        assertEquals(1,work.executionCount()); assertEquals(17,work.lastExecutionFrame());
        assertEquals(-1,work.lastInstructionCycles());
        assertEquals(S3kAizDeformInstructionWork.Qualification.ROM_UNSUPPORTED,work.qualification());
        assertEquals(java.util.Set.of(S3kAizDeformInstructionWork.UnpricedTerm.values()),work.unpricedTerms());
    }
    @RequiresRom(SonicGame.SONIC_3K)
    @Test void completeCalleeAuthenticationRefusesEveryChangedByteIncludingOperands() throws Exception {
        byte[] bytes;
        try(var rom = new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            bytes = rom.readBytes(0,S3kAizDeformInstructionWork.CODE_START + S3kAizDeformInstructionWork.CODE_LENGTH);
        }
        assertTrue(S3kAizDeformInstructionWork.romMatches(RomByteReader.fromBytes(bytes)));
        for(int offset=0;offset<S3kAizDeformInstructionWork.CODE_LENGTH;offset++) {
            int address = S3kAizDeformInstructionWork.CODE_START + offset;
            bytes[address] ^= 1;
            assertFalse(S3kAizDeformInstructionWork.romMatches(RomByteReader.fromBytes(bytes)),
                    "every opcode AND operand byte is authenticated: " + Integer.toHexString(address));
            bytes[address] ^= 1;
        }
        assertFalse(S3kAizDeformInstructionWork.romMatches(RomByteReader.fromBytes(Arrays.copyOf(bytes,bytes.length-1))));
        bytes[0] ^= 1;
        assertTrue(S3kAizDeformInstructionWork.romMatches(RomByteReader.fromBytes(bytes)),
                "this authenticates the callee only, never the rest of the frame");
    }
}
