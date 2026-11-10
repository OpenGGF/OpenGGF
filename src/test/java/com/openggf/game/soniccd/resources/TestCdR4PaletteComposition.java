package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.resources.CdR4PaletteComposition.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native literal scripts plus clearly constructed bad/unknown views, no live publication proof. */
class TestCdR4PaletteComposition {
    @Test void wordGateAndOtherAct1HaveCompleteNonwritesWithoutTables(){var s=state();assertSame(s,step(Mode.PRESENT,s,0,0,0,0x8000,null).state());assertSame(s,step(Mode.PAST_OR_FUTURE,s,0,0,0,0,null).state());}
    @Test void presentStandardUsesFourGroupsAndPreservesThreeUnusedSlotsAndWaterFade(){
        var s=state();var out=step(Mode.PRESENT,s,1,0,0,0,present());assertTrue(out.completed());assertEquals(4,out.writes().size());
        int[] colours={0xECA,0xEA8,0xE86,0xEEE};for(int n=0;n<4;n++){assertEquals(colours[n],out.state().normal().unsignedWord((49+n)*2));assertEquals(2,out.state().timers().unsignedByte(n));assertEquals(1,out.state().steps().unsignedByte(n));}
        for(int n=4;n<7;n++){assertEquals(0,out.state().timers().unsignedByte(n));assertEquals(0,out.state().steps().unsignedByte(n));}
        assertSame(s.water(),out.state().water());assertSame(s.fade(),out.state().fade());assertSame(s.waterFade(),out.state().waterFade());assertArrayEquals(filled(128,0x55).copy(),s.normal().copy());
    }
    @Test void upperGroupSelectionUsesUnsignedExclusiveCameraAndSixLiteralGroups(){
        var out=step(Mode.PRESENT,state(),0,0x27F,0x15F,0,present());assertEquals(6,out.writes().size());assertEquals(0x004A,out.state().normal().unsignedWord(64));assertEquals(0x0A4E,out.state().normal().unsignedWord(100));assertEquals(0x0408,out.state().normal().unsignedWord(102));assertEquals(0x082C,out.state().normal().unsignedWord(104));assertEquals(0x066C,out.state().normal().unsignedWord(114));assertEquals(0x042A,out.state().normal().unsignedWord(116));assertEquals(0,out.state().timers().unsignedByte(6));
        assertEquals(4,step(Mode.PRESENT,state(),0,0x280,0,0,present()).writes().size());assertEquals(4,step(Mode.PRESENT,state(),0,0xFFFF,0,0,present()).writes().size());
    }
    @Test void row4LoadIsOneTimeOnlyOnTheReachedYBranchNotAnyExitFromUpperArea(){
        var out=step(Mode.PRESENT,state(),0,0,0x160,0,present());assertEquals(24,out.stage4LongsCopied());assertEquals(52,out.writes().size());assertEquals(128,out.state().timers().unsignedByte(4));assertEquals(0x2222,out.state().normal().unsignedWord(32));
        var held=step(Mode.PRESENT,out.state(),0,0,0x160,0,new Tables(present().standard(),present().upper(),b()));assertTrue(held.completed());assertEquals(0,held.stage4LongsCopied());
        assertEquals(0,step(Mode.PRESENT,state(),0,0x280,0x160,0,present()).stage4LongsCopied());
    }
    @Test void row4SourceFaultKeepsLatchAndCompletedLongPrefixBeforeAnyCycle(){
        var t=new Tables(present().standard(),present().upper(),b(0x12,0x34,0x56,0x78,0xAA));var s=state();var out=step(Mode.PRESENT,s,0,0,0x160,0,t);
        assertEquals(new Fault(-1,Access.STAGE4_SOURCE,4,4),out.fault().orElseThrow());assertEquals(1,out.stage4LongsCopied());assertEquals(2,out.writes().size());assertEquals(128,out.state().timers().unsignedByte(4));assertEquals(0,out.state().timers().unsignedByte(0));assertEquals(0x1234,out.state().normal().unsignedWord(32));assertEquals(0x5678,out.state().normal().unsignedWord(34));assertEquals(0x5555,out.state().normal().unsignedWord(36));
    }
    @Test void timerSignAndStepWrapAreNativeByteOperationsWithLazyEmptyScripts(){
        var s=withBytes(state(),b(128,1,1,1,4,5,6),b(255,254,0,0,4,5,6));var empty=new CdLevelPaletteCycle.Script(b(),b());var out=step(Mode.PRESENT,s,1,0,0,0,new Tables(Collections.nCopies(4,empty),List.of(),b()));assertTrue(out.completed());assertTrue(out.writes().isEmpty());assertEquals(127,out.state().timers().unsignedByte(0));assertEquals(255,out.state().steps().unsignedByte(0));assertEquals(0,out.state().timers().unsignedByte(1));
        s=withBytes(state(),b(0,1,1,1,0,0,0),b(255,0,0,0,0,0,0));out=step(Mode.PRESENT,s,1,0,0,0,present());assertEquals(0,out.state().steps().unsignedByte(0));assertEquals(0xEEE,out.state().normal().unsignedWord(98));
    }
    @Test void paletteSourceReadFaultPrecedesDestinationAndRetainsTimerStepPrefix(){
        var malformed=new CdLevelPaletteCycle.Script(b(255,1,7,255),b());var out=step(Mode.PAST_OR_FUTURE,state(),1,0,0,0,new Tables(List.of(malformed,malformed,malformed),List.of(),b()));
        assertEquals(new Fault(0,Access.COLOURS,-2,2),out.fault().orElseThrow());assertEquals(7,out.state().timers().unsignedByte(0));assertEquals(0,out.state().steps().unsignedByte(0));assertEquals(0,out.state().timers().unsignedByte(1));assertTrue(out.writes().isEmpty());
    }
    @Test void laterCycleFaultRetainsEarlierColorAndNeverTouchesFollowingSlots(){
        var good=new CdLevelPaletteCycle.Script(b(50,1,2,0),b(0x0C,0xC0));var bad=new CdLevelPaletteCycle.Script(b(),b());var out=step(Mode.PAST_OR_FUTURE,state(),1,0,0,0,new Tables(List.of(good,bad,good),List.of(),b()));assertEquals(new Fault(1,Access.SCRIPT,0,1),out.fault().orElseThrow());assertEquals(0xCC0,out.state().normal().unsignedWord(100));assertEquals(1,out.writes().size());assertEquals(255,out.state().timers().unsignedByte(1));assertEquals(0,out.state().timers().unsignedByte(2));
    }
    @Test void threeOtherEraGroupsUseIndependentLiteralColorOperands(){
        int[][] colours={{0xCC0,0xEE0,0xEE4},{0xCCC,0xC82,0xEA6,0xECA},{0xC6A,0xC8C,0xCAE}};
        for(int[] cs:colours){var source=words(cs);var scripts=new ArrayList<CdLevelPaletteCycle.Script>();for(int n=0;n<3;n++){int count=cs.length;int[] timing=new int[2+count*2];timing[0]=50+n;timing[1]=count;for(int i=0;i<count;i++){timing[2+i*2]=2;timing[3+i*2]=(i+n+(count==4?1:0))%count;}scripts.add(new CdLevelPaletteCycle.Script(b(timing),source));}var out=step(Mode.PAST_OR_FUTURE,state(),2,0,0,0,new Tables(scripts,List.of(),b()));assertTrue(out.completed());assertEquals(3,out.writes().size());for(int n=0;n<3;n++)assertEquals(cs[(1+n+(cs.length==4?1:0))%cs.length],out.state().normal().unsignedWord((50+n)*2));}
    }
    static Tables present(){
        var colours=words(0xEEE,0xECA,0xEA8,0xE86);var standard=new ArrayList<CdLevelPaletteCycle.Script>();for(int n=0;n<4;n++){int[] script={49+n,4,2,n,2,(n+1)%4,2,(n+2)%4,2,(n+3)%4};standard.add(new CdLevelPaletteCycle.Script(b(script),colours));}
        int[] dest={32,50,51,52,57,58};int[][] upperColours={{2,0x4A,0x28,4},{0xA80,0xA4E,0xE8C,0xA48,0xA60},{0x620,0x408,0xA48,0x624},{0xA60,0x82C,0xC6A,0x826,0x842},{0x64C,0x66C},{0x408,0x42A}};var upper=new ArrayList<CdLevelPaletteCycle.Script>();for(int n=0;n<6;n++){int fourth=n==1||n==3?4:n>=4?0:3;int second=n>=4?1:2,third=n>=4?0:3;upper.add(new CdLevelPaletteCycle.Script(b(dest[n],8,60,0,8,1,8,second,8,third,8,fourth,8,0,6,0,6,0),words(upperColours[n])));}return new Tables(standard,upper,filled(96,0x22));
    }
    static State state(){return new State(filled(128,0x55),filled(128,0x66),filled(128,0x77),filled(128,0x88),b(new int[7]),b(new int[7]));}
    static State withBytes(State s,CdAssetBytes timers,CdAssetBytes steps){return new State(s.normal(),s.water(),s.fade(),s.waterFade(),timers,steps);}
    static CdAssetBytes b(int... values){byte[] out=new byte[values.length];for(int i=0;i<values.length;i++)out[i]=(byte)values[i];return CdAssetBytes.copyOf(out);}
    static CdAssetBytes filled(int n,int value){byte[] out=new byte[n];Arrays.fill(out,(byte)value);return CdAssetBytes.copyOf(out);}
    static CdAssetBytes words(int... values){byte[] out=new byte[values.length*2];for(int i=0;i<values.length;i++){out[i*2]=(byte)(values[i]>>>8);out[i*2+1]=(byte)values[i];}return CdAssetBytes.copyOf(out);}
}
