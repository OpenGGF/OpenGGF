package com.openggf.game.sonic3k.scroll;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSwScrlEnding {
    @Test void islandRampConsumesOldSignedAccumulatorAndPreservesFractionOnAdvance(){
        var ram=new EndingZoneRuntimeState(PlayerCharacter.SONIC_ALONE).screen();
        ram.cloudDriftFixed=0x00148000;ram.scrollYFixed=0x1400000;
        SwScrlEnding.islandParameters(ram);
        assertEquals(0x00150000,ram.cloudDriftFixed);assertEquals(0x120,ram.backgroundCameraY);
        assertEquals(0x5E,ram.hScrollWords[12]);
        assertArrayEquals(new short[]{2,5,7,10,12,15,17,20},java.util.Arrays.copyOfRange(ram.hScrollWords,4,12));
    }
    @Test void cloudRampUsesOldFractionAndRepeatsEachNativeWordFourTimes(){
        var ram=new EndingZoneRuntimeState(PlayerCharacter.TAILS_ALONE).screen();
        ram.scrollXFixed=0x1000000;ram.scrollYFixed=0x2200000;ram.cloudDriftFixed=0x8000;
        SwScrlEnding.cloudParameters(ram);
        assertEquals(0x8800,ram.cloudDriftFixed);assertEquals(0x220,ram.backgroundCameraY);
        assertArrayEquals(new short[]{256,256,256,256,272,272,272,272,289,289,289,289},
                java.util.Arrays.copyOfRange(ram.hScrollWords,10,22));
        assertEquals(305,ram.hScrollWords[3]);assertEquals(289,ram.hScrollWords[9]);
    }
    @Test void perLineBandStartsAtSkippedWordAndReturnsToConstantConsumption(){
        short[] values=new short[140];for(int i=0;i<values.length;i++)values[i]=(short)(i*3);
        int[] output=new int[224];SwScrlEnding.compose(output,0x100,7,values,new int[]{4,0x8008,0x7FFF});
        assertEquals(0xFF00FFEEL,output[0]&0xFFFFFFFFL); // source6, after three skipped per-line words
        assertEquals(0xFF00FFE2L,output[4]&0xFFFFFFFFL); // source10, final per-line word
        assertEquals(0xFF00FFDFL,output[5]&0xFFFFFFFFL); // source11, next constant band
        assertEquals(output[5],output[223]);
    }
    @Test void endingScreenRamRestoresDriftAndScrollWordsExactly(){
        var state=new EndingZoneRuntimeState(PlayerCharacter.KNUCKLES);state.screen().cloudDriftFixed=-0x18000;
        state.screen().foregroundSignal=true;state.screen().hScrollWords[139]=(short)0xA500;
        byte[] saved=state.captureBytes();state.screen().cloudDriftFixed=1;state.screen().hScrollWords[139]=0;
        state.restoreBytes(saved);assertEquals(-0x18000,state.screen().cloudDriftFixed);
        assertTrue(state.screen().foregroundSignal);assertEquals((short)0xA500,state.screen().hScrollWords[139]);
        assertArrayEquals(saved,state.captureBytes());
    }
}
