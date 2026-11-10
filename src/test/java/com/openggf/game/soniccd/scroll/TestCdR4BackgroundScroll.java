package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static com.openggf.game.soniccd.scroll.CdR4BackgroundScroll.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal native vectors and deliberately constructed unknown/fault controls; no World/route proof. */
class TestCdR4BackgroundScroll {
    @Test void lockReadsNoTablesAndWritesNoByte(){var s=state(0,0,0,0,0,0);var out=step(Profile.ACT1_PRESENT,s,input(0,0,0,0,1,0,65535),null);assertSame(s,out.state());assertEquals(65535,out.deform());assertEquals(0,out.bandWordsWritten());assertEquals(0,out.hscrollWordsWritten());}
    @Test void nativeInitProfilesKeepFractionsAndOnlyAct1Clears60Bytes(){
        for(var p:Profile.values()){
            var s=state(0x11223344,0x55667788,0x99AABBCC,0xDDEEFF01,0x12345678,0x345689AB);
            var out=initialize(p,s,0x1234,0x301);
            assertEquals(0x00D83344,out.cameraBgX());assertEquals(0x01B3BBCC,out.cameraBg2X());assertEquals(0x01235678,out.cameraBg3X());
            assertEquals(p==Profile.ACT1_PRESENT?0x02C0C000:0x01800000,out.cameraBgY());
            assertEquals(p==Profile.ACT1_PRESENT?0x02C0FF01:0x0180FF01,out.cameraBg2Y());
            assertSame(s.cloudRam(),out.cloudRam());assertSame(s.crossingRam(),out.crossingRam());assertSame(s.hscrollRam(),out.hscrollRam());assertEquals(s.vscroll(),out.vscroll());
            assertArrayEquals(s.deformationRam().slice(60,452).copy(),out.deformationRam().slice(60,452).copy());
            for(int at=0;at<60;at++)assertEquals(p==Profile.ACT2_OR_3?0x53:0,out.deformationRam().unsignedByte(at));
        }
    }
    @Test void presentUpperCameraPiecewiseYUsesFullLongAtInitOnly(){
        var s=state(0,0x11112222,0,0x33334444,0,0x55556666);
        assertEquals(0x015F0000,initialize(Profile.ACT1_PRESENT,s,0x27F,0x15F).cameraBgY());
        assertEquals(0x02000000,initialize(Profile.ACT1_PRESENT,s,0x280,0x15F).cameraBgY());
        var out=step(Profile.ACT1_PRESENT,s,input(0,0x301,0,0,0,0x7FFF,0),tables(b(),b(13,9,3,1,1,0),b()));
        assertEquals(0x02C02222,out.state().cameraBgY());assertEquals(0x030102C0,out.state().vscroll());
    }
    @Test void flatBandsWrite192WordsAnd232LinesRetainingAllTails(){
        var s=state(0x0020ABCD,0,0x12345678,0x77779999,0x87654321,0x8888AAAA);
        var out=step(Profile.ACT2_OR_3,s,input(100,0,0,0,0,0x7FFF,0xFF80),tables(b(),b(),b()));
        assertEquals(192,out.bandWordsWritten());assertEquals(464,out.hscrollWordsWritten());assertEquals(0,out.deform());
        for(int n=0;n<232;n++){assertEquals(0xFF9C,out.state().hscrollRam().unsignedWord(n*4));assertEquals(0xFFE0,out.state().hscrollRam().unsignedWord(n*4+2));}
        assertArrayEquals(s.deformationRam().slice(384,128).copy(),out.state().deformationRam().slice(384,128).copy());
        assertArrayEquals(s.hscrollRam().slice(928,96).copy(),out.state().hscrollRam().slice(928,96).copy());
        assertEquals(s.cameraBg2X(),out.state().cameraBg2X());assertEquals(s.cameraBg3X(),out.state().cameraBg3X());assertEquals(0x00009999,out.state().cameraBg2Y());assertSame(s.cloudRam(),out.state().cloudRam());
    }
    @Test void signedMovementAndByteCrossingMergeKeepLowFlagBytes(){
        var s=state(0x00100000,0x00100000,0,0,0,0);
        var out=step(Profile.ACT2_OR_3,s,input(0,0,-256,-256,0,0x7FFF,0),tables(b(),b(),b())).state();
        assertEquals(0x000FF400,out.cameraBgX());assertEquals(0x000F8000,out.cameraBgY());assertEquals(0xF723,out.scrollFlagsBg());assertEquals(0x56,out.scrollFlagsBg2());assertEquals(0x89,out.scrollFlagsBg3());
        assertArrayEquals(new byte[]{16,16,0,0,0,0,0,0},out.crossingRam().copy());
    }
    @Test void wetPathWritesForegroundBeforeFaultingFirstWobbleRead(){
        var s=state(0,0,0,0,0,0);var f=assertThrows(ScrollFault.class,()->step(Profile.ACT2_OR_3,s,input(100,0,0,0,0,0,0xFF80),tables(b(),b(),b())));
        assertEquals(Access.WOBBLE,f.access());assertEquals(255,f.offset());assertEquals(1,f.bytes());assertEquals(192,f.prefix().bandWordsWritten());assertEquals(1,f.prefix().hscrollWordsWritten());assertEquals(0,f.prefix().deform());
        assertEquals(0xFF9C,f.prefix().state().hscrollRam().unsignedWord(0));assertEquals(0x7575,f.prefix().state().hscrollRam().unsignedWord(2));assertEquals(0x7575,s.hscrollRam().unsignedWord(0));
    }
    @Test void separateWaveWordUsesOldHighByteAndWrapsByteSamples(){
        byte[] wobble=new byte[256];wobble[255]=(byte)0xFE;wobble[0]=3;
        var out=step(Profile.ACT2_OR_3,state(0x00200000,0,0,0,0,0),input(100,0,0,0,0,0,0xFF80),tables(CdAssetBytes.copyOf(wobble),b(),b()));
        assertEquals(0xFFDE,out.state().hscrollRam().unsignedWord(2));assertEquals(0xFFE3,out.state().hscrollRam().unsignedWord(6));assertEquals(0xFF9C,out.state().hscrollRam().unsignedWord(4));assertEquals(0,out.deform());
    }
    @Test void signedWaterCompareAndWetLoopNeverReturnToDryComparison(){
        byte[] wave=new byte[256];Arrays.fill(wave,(byte)5);
        var s=state(0,0,0,0,0,0);
        var out=step(Profile.ACT2_OR_3,s,input(0,0x7FFF,0,0,0,0x7FFF,0),tables(CdAssetBytes.copyOf(wave),b(),b()));
        assertEquals(5,out.state().hscrollRam().unsignedWord(2));assertEquals(5,out.state().hscrollRam().unsignedWord(6));
        var dry=step(Profile.ACT2_OR_3,s,input(0,0x8000,0,0,0,0,0),tables(b(),b(),b()));assertEquals(464,dry.hscrollWordsWritten());
    }
    @Test void dryWetBoundaryHasNoForegroundWaveAndOnlyReachedSamples(){
        var out=step(Profile.ACT2_OR_3,state(0x00200000,0,0,0,0,0),input(100,20,0,0,0,22,0),tables(filled(256,7),b(),b()));
        assertEquals(0xFFE0,out.state().hscrollRam().unsignedWord(6));assertEquals(0xFFE7,out.state().hscrollRam().unsignedWord(10));assertEquals(0xFF9C,out.state().hscrollRam().unsignedWord(8));
    }
    @Test void lastLineReachesExtraBandReadAndRetainsCompleted232Outputs(){
        // Act1 mask selects band255; fine0 reads band256 after8 outputs, unless adjacent bytes owned.
        var s=state(0,0x07F80000,0,0,0,0);
        var f=assertThrows(ScrollFault.class,()->step(Profile.ACT1_OTHER,s,input(0,0,0,0,0,0x7FFF,0),tables(b(),b(),b())));
        assertEquals(Access.BAND_READ,f.access());assertEquals(512,f.offset());assertEquals(16,f.prefix().hscrollWordsWritten());assertEquals(0x80,f.prefix().deform());
        // Start band227; the30th read is band256 AFTER all232 outputs (no preflight/read trimming).
        var last=state(0,0x07180000,0,0,0,0);
        f=assertThrows(ScrollFault.class,()->step(Profile.ACT1_OTHER,last,input(0,0,0,0,0,0x7FFF,0),tables(b(),b(),b())));
        assertEquals(512,f.offset());assertEquals(464,f.prefix().hscrollWordsWritten());
        var ok=step(Profile.ACT1_OTHER,last,input(0,0,0,0,0,0x7FFF,0),tables(b(),b(),b(0x12,0x34)));assertEquals(464,ok.hscrollWordsWritten());
    }
    @Test void fineAlignmentAndRowMaskRetainOldUnwrittenRows(){
        var s=state(0,0x07FF0000,0,0,0,0);var out=step(Profile.ACT1_OTHER,s,input(0,0,0,0,0,0x7FFF,0),tables(b(),b(),filled(60,0x34)));
        assertEquals(0x5353,out.state().hscrollRam().unsignedWord(2));assertEquals(0x3434,out.state().hscrollRam().unsignedWord(6));
        var p2=step(Profile.ACT2_OR_3,s,input(0,0,0,0,0,0x7FFF,0),tables(b(),b(),b()));assertEquals(464,p2.hscrollWordsWritten());
    }
    @Test void presentRepeatsReadReverseAndFailAfterLiteral16WordPrefix(){
        var f=assertThrows(ScrollFault.class,()->step(Profile.ACT1_PRESENT,state(0x20A000,0,0,0,0,0),input(0,0,0,0,0,0x7FFF,0),tables(b(),b(13,9,3,1),b())));
        assertEquals(Access.REPEATS,f.access());assertEquals(4,f.offset());assertEquals(16,f.prefix().bandWordsWritten());assertEquals(0,f.prefix().hscrollWordsWritten());assertEquals(0,f.prefix().deform());
    }
    @Test void presentPerspectiveLiteralFiveGroupsAnd144WordTail(){
        var out=step(Profile.ACT1_PRESENT,state(0,0,0x00060000,0,0,0),input(20,0,0,0,0,0x7FFF,0),tables(b(),b(13,9,3,1,1),b()));
        int[] starts={16,18,20,24,34},ends={18,20,24,34,48},values={0,-4,-8,-12,-16};
        for(int g=0;g<5;g++)for(int row=starts[g];row<ends[g];row++)assertEquals(values[g]&65535,out.state().deformationRam().unsignedWord(row*2));
        for(int row=48;row<192;row++)assertEquals(0xFFFA,out.state().deformationRam().unsignedWord(row*2));assertEquals(192,out.bandWordsWritten());
    }
    @Test void scratchHighWordAffectsNegativePerspective(){
        var out=step(Profile.ACT1_PRESENT,state(0,0,0,0,0x00010000,0),input(1,0,-1,0,0,0x7FFF,0),tables(b(),b(0,0,0,0,0),b()));
        // bg3 becomes0.fff0; slope DIVU128/5=25. d3 high=$FFFF carries into next integer.
        assertEquals(0,out.state().deformationRam().unsignedWord(32));assertEquals(65535,out.state().deformationRam().unsignedWord(34));
    }
    @Test void unsignedDivideOverflowRetainsLowNumeratorBeforeSignExtension(){
        // fg0-bg3= -1 -> numerator$FFFFFF80. DIVU#5 overflows a WORD quotient,
        // leaving$FFFFFF80; EXT.L sees -128, then <<9 produces -$10000.
        var out=step(Profile.ACT1_PRESENT,state(0,0,0,0,0x00010000,0),input(0,0,0,0,0,0x7FFF,0),tables(b(),b(0,0,0,0,0),b()));
        assertEquals(65535,out.state().deformationRam().unsignedWord(32));
        assertEquals(0,out.state().deformationRam().unsignedWord(34));
        assertEquals(1,out.state().deformationRam().unsignedWord(36));
        assertEquals(2,out.state().deformationRam().unsignedWord(38));
        assertEquals(3,out.state().deformationRam().unsignedWord(40));
    }
    @Test void inputWidthsAreNotSilentlyNormalized(){assertThrows(IllegalArgumentException.class,()->input(65536,0,0,0,0,0,0));assertThrows(IllegalArgumentException.class,()->input(0,0,32768,0,0,0,0));assertThrows(IllegalArgumentException.class,()->input(0,0,0,0,256,0,0));}
    static Input input(int x,int y,int dx,int dy,int lock,int water,int phase){return new Input(x,y,dx,dy,lock,water,phase);}
    static Tables tables(CdAssetBytes wobble,CdAssetBytes repeats,CdAssetBytes tail){return new Tables(wobble,repeats,tail);}
    static CdAssetBytes b(int... bytes){byte[] out=new byte[bytes.length];for(int i=0;i<bytes.length;i++)out[i]=(byte)bytes[i];return CdAssetBytes.copyOf(out);}
    static CdAssetBytes filled(int count,int value){byte[] b=new byte[count];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
    static CdR11aBackgroundScroll.State state(int x,int y,int x2,int y2,int x3,int y3){return new CdR11aBackgroundScroll.State(x,y,x2,y2,x3,y3,filled(8,0),0xA123,0xB456,0xC789,filled(512,0x31),filled(512,0x53),filled(1024,0x75),0x87654321);}
}
