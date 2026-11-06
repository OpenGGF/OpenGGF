package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.resources.CdLevelPaletteCycle.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed semantic vectors, no media/programme/clock authority; tests first, Java UNRUN. */
class TestCdLevelPaletteSingleCycle {
    @Test void singleSlotMatchesEachLegacySixSlotPassAcrossAllByteTimersAndSteps(){
        var script=new Script(b(2,3,7,0,5,1,3,2),b(0x12,0x34,0x56,0x78,0x9A,0xBC));
        for(int timer=0;timer<256;timer++)for(int step:new int[]{0,1,2,3,127,128,254,255}){
            byte[] ts={1,1,(byte)timer,1,1,1},ss={9,8,(byte)step,6,5,4};var old=pass(Collections.nCopies(6,script),new State(b(new int[128]),CdAssetBytes.copyOf(ts),CdAssetBytes.copyOf(ss)));var one=single(script,new SingleState(b(new int[128]),timer,step));assertArrayEquals(old.state().palette().copy(),one.state().palette().copy());assertEquals(old.state().timers().unsignedByte(2),one.state().timerByte());assertEquals(old.state().steps().unsignedByte(2),one.state().stepByte());assertEquals(old.completed(),one.completed());assertEquals(old.writes().size(),one.writes().size());if(!one.writes().isEmpty()){assertEquals(0,one.writes().getFirst().cycle());assertEquals(old.writes().getFirst().afterWord(),one.writes().getFirst().afterWord());}}
    }
    @Test void heldTimerNeverReadsEvenEmptyViewsAndRetainsTheExactStepByte(){var s=new SingleState(b(new int[128]),128,255);var out=single(new Script(b(),b()),s);assertTrue(out.completed());assertTrue(out.writes().isEmpty());assertEquals(127,out.state().timerByte());assertEquals(255,out.state().stepByte());assertArrayEquals(s.palette().copy(),out.state().palette().copy());}
    @Test void signedColourIndexFaultRetainsOnlyReachedStepAndTimerStores(){var out=single(new Script(b(2,1,7,255),b(0,1)),new SingleState(b(new int[128]),0,9));assertEquals(Optional.of(new Fault(0,Access.COLOURS,-2,2)),out.fault());assertEquals(0,out.state().stepByte());assertEquals(7,out.state().timerByte());assertTrue(out.writes().isEmpty());}
    @Test void sourceReadPrecedesDestinationFaultAndMalformedScriptRetainsNativePrefix(){var badDest=single(new Script(b(255,1,4,0),b(0x12,0x34)),new SingleState(b(new int[128]),0,0));assertEquals(Optional.of(new Fault(0,Access.NORMAL_PALETTE,510,2)),badDest.fault());var badSource=single(new Script(b(255,1,4,0),b()),new SingleState(b(new int[128]),0,0));assertEquals(Access.COLOURS,badSource.fault().orElseThrow().access());var missingTimer=single(new Script(b(2,3),b()),new SingleState(b(new int[128]),0,1));assertEquals(2,missingTimer.state().stepByte());assertEquals(255,missingTimer.state().timerByte());assertEquals(new Fault(0,Access.SCRIPT,6,1),missingTimer.fault().orElseThrow());}
    @Test void oldSixSlotContractAndErrorRemainExactWithoutPadding(){var s=new State(b(new int[128]),b(new int[6]),b(new int[6]));assertEquals("PalCycle_Do requires six ordered scripts",assertThrows(IllegalArgumentException.class,()->pass(List.of(new Script(b(),b())),s)).getMessage());assertThrows(IllegalArgumentException.class,()->new SingleState(b(new int[128]),256,0));assertThrows(IllegalArgumentException.class,()->new SingleState(b(new int[128]),0,-1));var out=single(new Script(b(2,1,7,0),b(0x12,0x34)),new SingleState(b(new int[128]),0,0));assertThrows(UnsupportedOperationException.class,()->out.writes().clear());assertEquals(0,s.timers().unsignedByte(0));}
    static CdAssetBytes b(int... values){byte[] b=new byte[values.length];for(int i=0;i<b.length;i++)b[i]=(byte)values[i];return CdAssetBytes.copyOf(b);}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
