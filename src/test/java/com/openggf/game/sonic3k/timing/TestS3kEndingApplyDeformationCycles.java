package com.openggf.game.sonic3k.timing;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kEndingApplyDeformationCycles {
    @Test void oneConstantBandEqualsTheIndependent4014CycleHandSum() {
        // R16: entry40 + first-band/setup132 +112 pairs*24 +111 dbf*10 +14 +exit30.
        int hand=40+132+112*24+111*10+14+30;
        assertEquals(4014,hand);
        assertEquals(hand,S3kEndingApplyDeformationWork.instructionCycles(new int[]{0x7FFF},0));
        assertEquals(-1,S3kEndingApplyDeformationWork.instructionCycles(new int[]{0,0x7FFF},0));
        assertEquals(-1,S3kEndingApplyDeformationWork.instructionCycles(new int[]{8},8));
        var unexecuted=new S3kEndingApplyDeformationWork();
        assertEquals(-1,unexecuted.lastInstructionCycles());assertEquals(0,unexecuted.executionCount());
    }
    private byte[] originalPrefix() throws Exception {
        try(var rom=new Rom()){
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            return rom.readBytes(0,S3kEndingApplyDeformationWork.CLOUD_BANDS+20);
        }
    }
    @RequiresRom(SonicGame.SONIC_3K)
    @Test void bothRomBandTablesMatchAll63IndependentR16BoundaryVectors() throws Exception {
        var rom=RomByteReader.fromBytes(originalPrefix());int rows=0;
        try(var input=getClass().getResourceAsStream("/timing/s3k-ending-apply-deformation-r16-v1.txt")){
            assertNotNull(input);
            for(String line:new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8)).lines().toList()){
                if(line.startsWith("#")||line.isBlank())continue;
                var fields=line.split(" ");int table=Integer.parseInt(fields[0],16);
                int y=Integer.parseInt(fields[1]),expected=Integer.parseInt(fields[2]);
                int[] bands=new int[table==S3kEndingApplyDeformationWork.ISLAND_BANDS?11:10];
                for(int i=0;i<bands.length;i++)bands[i]=rom.readU16BE(table+i*2);
                assertEquals(expected,S3kEndingApplyDeformationWork.instructionCycles(bands,y),line);
                var work=new S3kEndingApplyDeformationWork();work.recordExecution(rom,1,table,y);
                assertEquals(expected,work.lastInstructionCycles(),line);
                assertEquals(S3kEndingApplyDeformationWork.Qualification.QUALIFIED,work.qualification());rows++;
            }
        }
        assertEquals(63,rows);
    }
    @RequiresRom(SonicGame.SONIC_3K)
    @Test void complete140ByteAuthenticationRefusesEveryChangedOpcodeAndOperand() throws Exception {
        var bytes=originalPrefix();assertTrue(S3kEndingApplyDeformationWork.romMatches(RomByteReader.fromBytes(bytes)));
        for(int offset=0;offset<S3kEndingApplyDeformationWork.CODE_LENGTH;offset++){
            int address=S3kEndingApplyDeformationWork.CODE_START+offset;bytes[address]^=1;
            assertFalse(S3kEndingApplyDeformationWork.romMatches(RomByteReader.fromBytes(bytes)),Integer.toHexString(address));
            bytes[address]^=1;
        }
        assertFalse(S3kEndingApplyDeformationWork.romMatches(RomByteReader.fromBytes(Arrays.copyOf(bytes,
                S3kEndingApplyDeformationWork.CODE_START+S3kEndingApplyDeformationWork.CODE_LENGTH-1))));
        bytes[0]^=1;assertTrue(S3kEndingApplyDeformationWork.romMatches(RomByteReader.fromBytes(bytes)),
                "the callee pin authenticates no caller or interrupt code");
    }
}
