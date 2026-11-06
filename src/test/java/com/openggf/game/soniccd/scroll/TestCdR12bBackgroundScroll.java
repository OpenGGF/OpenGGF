package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State;
import static org.junit.jupiter.api.Assertions.*;

/** N1ecc5f3a, Scroll (Act 2 Past):165-259,265-561. Constructed component controls; ALL JAVA UNRUN. */
class TestCdR12bBackgroundScroll {
    @Test void surfaceRetainsOnlyTheImmutableTableAndReusesTheExistingFourteenFieldState() {
        var fields = CdR12bBackgroundScroll.class.getDeclaredFields(); assertEquals(1, fields.length);
        assertEquals(CdAssetBytes.class, fields[0].getType());
        assertTrue(Modifier.isPrivate(fields[0].getModifiers())); assertTrue(Modifier.isFinal(fields[0].getModifiers()));
        var constructors = CdR12bBackgroundScroll.class.getDeclaredConstructors(); assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers())); assertEquals(1, constructors[0].getParameterCount());
        assertEquals(List.of("bind/1", "fromTables/1", "init/2", "step/2"),
                Arrays.stream(CdR12bBackgroundScroll.class.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                        .map(m -> m.getName() + "/" + m.getParameterCount()).sorted().toList());
        assertEquals(14, State.class.getRecordComponents().length);
        assertEquals(List.of("cameraXWord", "cameraYWord", "scrollXDiff", "scrollYDiff", "scrollLockByte"),
                Arrays.stream(CdR12bBackgroundScroll.Input.class.getRecordComponents()).map(c -> c.getName()).toList());
    }

    @Test void widthsAreUnsignedCameraWordsSignedNativeDiffsAndTheHighLockByte() {
        new CdR12bBackgroundScroll.Input(65535, 32768, -32768, 32767, 255);
        for (int field = 0; field < 5; field++) {
            int at = field; int[] invalid = field < 2 ? new int[]{-1, 65536} : field < 4 ? new int[]{-32769, 32768} : new int[]{-1, 256};
            for (int value : invalid) {
                int[] row = {0, 0, 0, 0, 0}; row[at] = value;
                assertThrows(IllegalArgumentException.class, () -> new CdR12bBackgroundScroll.Input(row[0], row[1], row[2], row[3], row[4]));
            }
        }
    }

    @Test void tableIngressHasExactEightBytesAndRejectsDeformationOverflowWithoutMediaAuthority() {
        assertThrows(NullPointerException.class, () -> CdR12bBackgroundScroll.bind(null));
        assertThrows(NullPointerException.class, () -> CdR12bBackgroundScroll.fromTables(null));
        for (int length : new int[]{0, 7, 9, 512})
            assertThrows(IllegalArgumentException.class, () -> CdR12bBackgroundScroll.fromTables(CdAssetBytes.copyOf(new byte[length])));
        byte[] counts = new byte[8]; counts[0] = (byte) 185; // 185+1 plus seven other WORDs plus48 =241, exceeding240 available WORDs.
        assertThrows(IllegalArgumentException.class, () -> CdR12bBackgroundScroll.fromTables(CdAssetBytes.copyOf(counts)));
        counts[0] = (byte) 184; // Exact512-byte deformation boundary is admitted.
        assertNotNull(CdR12bBackgroundScroll.fromTables(CdAssetBytes.copyOf(counts)));
    }

    @Test void initStoresThreeSixteenthsYAndRoundedXWordsPreservingAllOtherBits() {
        State before = state(0x8123A1B2, 0x82345678, 0x9245C3D4, 0xB456E5F6, 0xA3671020, 0xC5673040);
        State after = CdR12bBackgroundScroll.init(before, foreground(0x0F60, 0x00A0, 0xCAFE));
        byte[] deform = before.deformationRam().copy(); Arrays.fill(deform, 0, 32, (byte) 0);
        State expected = new State(0x07B0A1B2, 0x001E0000, 0x02E2C3D4, 0x001EE5F6,
                0x01EC1020, 0x001E3040, before.crossingRam(), before.scrollFlagsBg(), before.scrollFlagsBg2(),
                before.scrollFlagsBg3(), before.cloudRam(), CdAssetBytes.copyOf(deform), before.hscrollRam(), before.vscroll());
        value(expected, after); assertSame(before.crossingRam(), after.crossingRam());
        assertSame(before.cloudRam(), after.cloudRam()); assertSame(before.hscrollRam(), after.hscrollRam());
        assertEquals(0x82345678, before.cameraBgY());
    }

    @Test void initUsesSignedIntegerYAndClearsEightLongsWithoutBorrowingFgFractionOrSeparateCloudRam() {
        int[][] rows = {{0x61, 0x00123000}, {0x7FFF, 0x17FFD000}, {0x8000, 0xE8000000}, {0xFFFF, 0xFFFFD000}};
        State before = state(0, 0x76543210, 0, 0x1111ABCD, 0, 0x2222DCBA);
        for (int[] row : rows) {
            State a = CdR12bBackgroundScroll.init(before, foreground(0, row[0], 0x1357));
            State b = CdR12bBackgroundScroll.init(before, foreground(0, row[0], 0xECA8)); value(a, b);
            assertEquals(row[1], a.cameraBgY()); assertEquals((row[1] & 0xFFFF0000) | 0xABCD, a.cameraBg2Y());
            assertEquals((row[1] & 0xFFFF0000) | 0xDCBA, a.cameraBg3Y());
            byte[] expected = before.deformationRam().copy(); Arrays.fill(expected, 0, 32, (byte) 0);
            assertArrayEquals(expected, a.deformationRam().copy()); assertSame(before.cloudRam(), a.cloudRam());
        }
        for (int[] row : new int[][]{{15, 7, 0, 1}, {31, 15, 3, 3}, {65535, 32767, 0x2FFD, 0x1FFF}}) {
            State a = CdR12bBackgroundScroll.init(before, foreground(row[0], 0, 0));
            assertEquals(row[1], a.cameraBgXWord()); assertEquals(row[2], a.cameraBg2XWord()); assertEquals(row[3], a.cameraBg3XWord());
        }
    }

    @Test void lockReturnsTheExactInputBeforeFlagClearsTicksOrTableReads() {
        State before = state(0x12345678, 0x23456789, 0x3456789A, 0x456789AB, 0x56789ABC, 0x6789ABCD);
        for (int lock : new int[]{1, 0x80, 0xFF})
            assertSame(before, owner().step(before, new CdR12bBackgroundScroll.Input(65535, 32768, -32768, 32767, lock)));
    }

    @Test void zeroDiffsClearAllFlagWordsAndTestWholeCrossingBytesIncludingZeroMovement() {
        State before = state(0, 0, 0, 0, 0, 0); State after = owner().step(before, input(0, 0, 0, 0));
        assertEquals(0xAA00, after.scrollFlagsBg()); assertEquals(0, after.scrollFlagsBg2()); assertEquals(0, after.scrollFlagsBg3());
        assertArrayEquals(new byte[]{16,16,16,0x33,16,0x55,0x66,0x77}, after.crossingRam().copy());
        for (byte[] dirty : new byte[][]{{(byte)0xA0,(byte)0xB0,(byte)0xC0,0x33,(byte)0xD0,0x55,0x66,0x77}, {1,1,1,0x33,1,0x55,0x66,0x77}}) {
            State a = owner().step(crossings(before, dirty), input(0, 0, 0, 0));
            assertArrayEquals(dirty, a.crossingRam().copy()); assertEquals(0, a.scrollFlagsBg());
            assertEquals(0, a.scrollFlagsBg2()); assertEquals(0, a.scrollFlagsBg3());
        }
    }

    @Test void signedDiffRatesAndFlagDirectionsUseRawWrappingLongDifferencesAndKeepSecondaryYFractions() {
        State before = crossings(state(0x00200000, 0x00200000, 0x00200000, 0x00FF1234, 0x00200000, 0xFFFF5678), new byte[]{16,16,16,0x33,16,0x55,0x66,0x77});
        State after = owner().step(before, input(0x1234, 0xABCD, -0x100, -0x100));
        cameras(after, 0x001F8000, 0x001FD000, 0x001FD000, 0x001F1234, 0x001FE000, 0x001F5678);
        assertEquals(0x5500, after.scrollFlagsBg()); assertEquals(0xABCD001F, after.vscroll());
        assertArrayEquals(new byte[]{0,0,0,0x33,0,0x55,0x66,0x77}, after.crossingRam().copy());
        for (int upper : new int[]{0, 0x7FF00000}) {
            before = state(upper + 0x000F8000, upper + 0x000FD000, upper + 0x000FD000, 0x11111234, upper + 0x000FE000, 0x22225678);
            before = crossings(before, new byte[]{(byte)(upper==0?16:0),(byte)(upper==0?16:0),(byte)(upper==0?16:0),0x33,(byte)(upper==0?16:0),0x55,0x66,0x77});
            after = owner().step(before, input(65535, 0xABC1, 0x100, 0x100));
            cameras(after, upper + 0x00100000, upper + 0x00100000, upper + 0x00100000, (upper + 0x00100000) | 0x1234, upper + 0x00100000, (upper + 0x00100000) | 0x5678);
            assertEquals(0xAA00, after.scrollFlagsBg()); assertEquals(0, after.scrollFlagsBg2()); assertEquals(0, after.scrollFlagsBg3());
        }
        before=crossings(state(0x80000000,0x80000000,0x80000000,0x11111234,0x80000000,0x22225678),new byte[]{16,16,16,0x33,16,0x55,0x66,0x77});
        after=owner().step(before,input(0,0x1234,-0x100,-0x100));
        cameras(after,0x7FFF8000,0x7FFFD000,0x7FFFD000,0x7FFF1234,0x7FFFE000,0x7FFF5678);
        assertEquals(0x5500,after.scrollFlagsBg());assertEquals(0x12347FFF,after.vscroll());
    }

    @Test void verticalMotionAccumulatesThreeTimesTheSignedShiftInsteadOfRecomputingFromForegroundY() {
        State before = buffers(state(0, 0x00123456, 0, 0x5555ABCD, 0, 0x6666DCBA), new byte[512], filled(1024, 0x5A));
        State a = owner().step(before, input(0, 0x7FFF, 0, 0x80)); State b = owner().step(a, input(0, 0x7FFF, 0, 0x80));
        assertEquals(0x00124C56, a.cameraBgY()); assertEquals(0x00126456, b.cameraBgY());
        assertEquals(0x0012ABCD, b.cameraBg2Y()); assertEquals(0x0012DCBA, b.cameraBg3Y()); assertEquals(0x7FFF0012, b.vscroll());
        int[] increments = {0x10000,0xE000,0xC000,0xA000,0x8000,0x6000,0x5000,0x4000};
        for (int i=0; i<8; i++) assertEquals(2L * increments[i], b.deformationRam().unsignedLong(i*4));
    }

    @Test void eightIndependentCloudCarriesWriteOnlyTheSourceOwnedSixtyFourWords() {
        byte[] deform = filled(512, 0xA5); Arrays.fill(deform, 0, 32, (byte)0xFF);
        State before = buffers(state(0x00300000, 0, 0x00200000, 0, 0x00100000, 0), deform, filled(1024, 0x5A));
        State after = owner().step(before, input(0, 0, 0, 0)); byte[] expected = deform.clone();
        int[] values = {0xFFFF,0xDFFF,0xBFFF,0x9FFF,0x7FFF,0x5FFF,0x4FFF,0x3FFF};
        for (int i=0; i<8; i++) { putLong(expected, i*4, values[i]); words(expected,32+i*4,2,0xFFF0); }
        words(expected,64,8,0xFFF0); words(expected,80,8,0xFFE0); words(expected,96,32,0xFFE0);
        assertArrayEquals(expected, after.deformationRam().copy()); assertArrayEquals(deform, before.deformationRam().copy());
        assertSame(before.cloudRam(), after.cloudRam());
        assertArrayEquals(Arrays.copyOfRange(deform,160,512), Arrays.copyOfRange(after.deformationRam().copy(),160,512));
    }

    @Test void cloudLineCountsAreReadInReverseWhileAccumulatorsAdvanceForward() {
        byte[] counts = {0,1,2,3,4,5,6,7}; var owner = CdR12bBackgroundScroll.fromTables(CdAssetBytes.copyOf(counts));
        byte[] deform = filled(512,0x5A); for (int i=0;i<8;i++) putLong(deform,i*4,((i+1)<<16));
        State before = buffers(state(0,0,0,0,0,0),deform,filled(1024,0xA5));
        State after = owner.step(before,input(0,0,0,0)); int at=32;
        // First accumulator1 gains1; the remaining seven retain their integer before carry.
        for(int i=0;i<8;i++) for(int j=0;j<8-i;j++,at+=2) assertEquals((-(i==0?2:i+1))&65535,after.deformationRam().unsignedWord(at));
        for(int i=0;i<48;i++,at+=2) assertEquals(0,after.deformationRam().unsignedWord(at));
        assertArrayEquals(Arrays.copyOfRange(deform,at,512),Arrays.copyOfRange(after.deformationRam().copy(),at,512));
        Arrays.fill(counts,(byte)0xFF); value(after,owner.step(before,input(0,0,0,0)));
    }

    @Test void everyLowYPhaseWritesExactly240MinusPhaseLongsWithNativeWordNegationAndUntouchedTail() {
        for(int phase=0;phase<8;phase++) {
            State before=buffers(state(0,phase<<16,0x00020000,0x77771234,0x00010000,0x88885678),new byte[512],filled(1024,0x5A));
            State after=owner().step(before,input(0x0124,0x3456,0,0)); byte[] expected=before.hscrollRam().copy();
            int dest=0;
            for(int block=0;block<30;block++) {
                int value=0xFEDC0000|after.deformationRam().unsignedWord(32+block*2);
                for(int line=0;line<(block==0?8-phase:8);line++,dest+=4) putLong(expected,dest,value);
            }
            assertEquals((240-phase)*4,dest); assertArrayEquals(expected,after.hscrollRam().copy());
            assertEquals(0x34560000|phase,after.vscroll());
        }
    }

    @Test void hscrollReadsTheSuppliedDeformationTailAfterTheNativeMaskWithoutPaddingOrClamping() {
        byte[] deform=new byte[512]; for(int at=160;at<512;at+=2) putWord(deform,at,0xB000+at);
        State before=buffers(state(0,0x01FF1234,0,0x1111ABCD,0,0x2222DCBA),deform,filled(1024,0x5A));
        State after=owner().step(before,input(0x8000,0xFFFF,0,0)); byte[] expected=before.hscrollRam().copy(); int dest=0;
        for(int block=0;block<30;block++) {
            int value=0x80000000|after.deformationRam().unsignedWord(158+block*2);
            for(int line=0;line<(block==0?1:8);line++,dest+=4) putLong(expected,dest,value);
        }
        assertEquals(932,dest); assertArrayEquals(expected,after.hscrollRam().copy()); assertEquals(0xFFFF01FF,after.vscroll());
    }

    @Test void deterministicReplayDoesNotMutateInputsAndReturnedBytesStayImmutable() {
        State before=state(0x1234ABCD,0x00405678,0x2345BCDE,0x00506789,0x3456CDEF,0x0060789A);
        byte[] original=before.deformationRam().copy(); var owner=owner();
        State a=owner.step(before,input(0x2345,0x3456,-0x80,0x80)),b=owner.step(before,input(0x2345,0x3456,-0x80,0x80)); value(a,b);
        assertArrayEquals(original,before.deformationRam().copy()); byte[] copied=a.deformationRam().copy(); Arrays.fill(copied,(byte)0);
        assertArrayEquals(b.deformationRam().copy(),a.deformationRam().copy());
        assertThrows(ReadOnlyBufferException.class,()->a.deformationRam().buffer().put(0,(byte)0));
        assertThrows(NullPointerException.class,()->owner.step(null,input(0,0,0,0)));
        assertThrows(NullPointerException.class,()->owner.step(before,null));
        assertThrows(NullPointerException.class,()->CdR12bBackgroundScroll.init(before,null));
    }

    private static CdR12bBackgroundScroll owner(){return CdR12bBackgroundScroll.fromTables(CdAssetBytes.copyOf(new byte[]{1,1,1,1,1,1,1,1}));}
    private static CdR12bBackgroundScroll.Input input(int x,int y,int dx,int dy){return new CdR12bBackgroundScroll.Input(x,y,dx,dy,0);}
    private static CdR11aForegroundScroll.State foreground(int x,int y,int fraction){return new CdR11aForegroundScroll.State(x,(y<<16)|fraction,16,16,0x1234,0x5678,0xABCD,0x2222,0x3333,0x4444,0x5555);}
    private static State state(int x,int y,int x2,int y2,int x3,int y3){return new State(x,y,x2,y2,x3,y3,CdAssetBytes.copyOf(new byte[]{0,0,0,0x33,0,0x55,0x66,0x77}),0xA15A,0xB26B,0xC37C,CdAssetBytes.copyOf(filled(512,0x6D)),CdAssetBytes.copyOf(filled(512,0x69)),CdAssetBytes.copyOf(filled(1024,0xA5)),0x12345678);}
    private static State buffers(State s,byte[] deform,byte[] hscroll){return new State(s.cameraBgX(),s.cameraBgY(),s.cameraBg2X(),s.cameraBg2Y(),s.cameraBg3X(),s.cameraBg3Y(),s.crossingRam(),s.scrollFlagsBg(),s.scrollFlagsBg2(),s.scrollFlagsBg3(),s.cloudRam(),CdAssetBytes.copyOf(deform),CdAssetBytes.copyOf(hscroll),s.vscroll());}
    private static State crossings(State s,byte[] crossing){return new State(s.cameraBgX(),s.cameraBgY(),s.cameraBg2X(),s.cameraBg2Y(),s.cameraBg3X(),s.cameraBg3Y(),CdAssetBytes.copyOf(crossing),s.scrollFlagsBg(),s.scrollFlagsBg2(),s.scrollFlagsBg3(),s.cloudRam(),s.deformationRam(),s.hscrollRam(),s.vscroll());}
    private static byte[] filled(int n,int v){byte[] a=new byte[n];Arrays.fill(a,(byte)v);return a;}
    private static void putWord(byte[] a,int at,int v){a[at]=(byte)(v>>>8);a[at+1]=(byte)v;}
    private static void putLong(byte[] a,int at,int v){putWord(a,at,v>>>16);putWord(a,at+2,v);}
    private static void words(byte[] a,int at,int count,int v){for(int i=0;i<count;i++)putWord(a,at+i*2,v);}
    private static void cameras(State s,int x,int y,int x2,int y2,int x3,int y3){assertArrayEquals(new int[]{x,y,x2,y2,x3,y3},new int[]{s.cameraBgX(),s.cameraBgY(),s.cameraBg2X(),s.cameraBg2Y(),s.cameraBg3X(),s.cameraBg3Y()});}
    private static void value(State a,State b){cameras(b,a.cameraBgX(),a.cameraBgY(),a.cameraBg2X(),a.cameraBg2Y(),a.cameraBg3X(),a.cameraBg3Y());assertArrayEquals(a.crossingRam().copy(),b.crossingRam().copy());assertEquals(a.scrollFlagsBg(),b.scrollFlagsBg());assertEquals(a.scrollFlagsBg2(),b.scrollFlagsBg2());assertEquals(a.scrollFlagsBg3(),b.scrollFlagsBg3());assertArrayEquals(a.cloudRam().copy(),b.cloudRam().copy());assertArrayEquals(a.deformationRam().copy(),b.deformationRam().copy());assertArrayEquals(a.hscrollRam().copy(),b.hscrollRam().copy());assertEquals(a.vscroll(),b.vscroll());}
}
