package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestCdR11aPlcBuffer {
    private static final CdR11aPlcBuffer.Entry P = new CdR11aPlcBuffer.Entry(0x237378L, 0x7600);
    private static final CdR11aPlcBuffer.Entry A = new CdR11aPlcBuffer.Entry(0x236E46L, 0x8120);
    private static final CdR11aPlcBuffer.Entry T = new CdR11aPlcBuffer.Entry(0x237950L, 0x8500);
    private static CdR11aPlcBuffer.State seed(byte[] bytes) {
        return CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(bytes));
    }
    private static byte[] fill(int value) { byte[] b=new byte[96];Arrays.fill(b,(byte)value);return b; }
    private static void same(CdR11aPlcBuffer.State s, byte[] bytes) { assertArrayEquals(bytes,s.bytes().copy()); }

    @Test void exactFirstBatchIsThreeOrderedBigEndianDescriptorsAndNoOtherWrite() {
        var s=CdR11aPlcBuffer.replace(CdR11aPlcBuffer.cleared(),List.of(P,A,T));
        assertArrayEquals(new byte[]{0,0x23,0x73,0x78,0x76,0,0,0x23,0x6E,0x46,(byte)0x81,0x20,0,0x23,0x79,0x50,(byte)0x85,0},s.bytes().slice(0,18).copy());
        assertArrayEquals(new byte[78],s.bytes().slice(18,78).copy());
        assertEquals(0x237378L,s.headSource());assertEquals(0x7600,s.headDestination());
    }
    @Test void appendScansSourceLongOnlyAndKeepsDirtyDestinationAndEverythingAfterNewRows() {
        byte[] b=fill(0x5A);Arrays.fill(b,6,10,(byte)0);
        var s=CdR11aPlcBuffer.append(seed(b),List.of(P));
        byte[] expected=b.clone();byte[] row={0,0x23,0x73,0x78,0x76,0};System.arraycopy(row,0,expected,6,6);
        same(s,expected);
    }
    @Test void appendDoesNotCompactHolesOrMoveExistingTail() {
        var first=CdR11aPlcBuffer.replace(CdR11aPlcBuffer.cleared(),List.of(P));
        var s=CdR11aPlcBuffer.append(first,List.of(A,T));
        assertEquals(0x236E46L,s.bytes().unsignedLong(6));assertEquals(0x237950L,s.bytes().unsignedLong(12));
        assertArrayEquals(first.bytes().slice(0,6).copy(),s.bytes().slice(0,6).copy());
    }
    @Test void replaceClearsAllNinetySixDescriptorBytesBeforeCopying() {
        var old=seed(fill(0x7F));var s=CdR11aPlcBuffer.replace(old,List.of(P));
        assertArrayEquals(new byte[90],s.bytes().slice(6,90).copy());same(old,fill(0x7F));
        same(CdR11aPlcBuffer.replace(old,List.of()),new byte[96]);
    }
    @Test void signedEmptyAppendStillPerformsNativeFreeSourceScan() {
        var full=seed(fill(0x11));var ex=assertThrows(CdR11aPlcBuffer.BufferBoundary.class,()->CdR11aPlcBuffer.append(full,List.of()));
        assertEquals(CdR11aPlcBuffer.Access.SOURCE_SCAN,ex.access());assertEquals(96,ex.byteOffset());assertEquals(full,ex.state());
        assertEquals(CdR11aPlcBuffer.cleared(),CdR11aPlcBuffer.append(CdR11aPlcBuffer.cleared(),List.of()));
    }
    @Test void zeroSourceIsWrittenAndRemainsNativeFreeOnNextAppend() {
        var z=new CdR11aPlcBuffer.Entry(0,0xFFE0);
        var s=CdR11aPlcBuffer.append(CdR11aPlcBuffer.cleared(),List.of(z));assertEquals(0xFFE0,s.headDestination());
        var next=CdR11aPlcBuffer.append(s,List.of(P));assertEquals(0x237378L,next.headSource());assertEquals(0x7600,next.headDestination());
    }
    @Test void sixteenthDescriptorFitsThenAppendRejectsReachedUnownedScan() {
        var rows=java.util.Collections.nCopies(16,P);var s=CdR11aPlcBuffer.replace(CdR11aPlcBuffer.cleared(),rows);
        assertEquals(0x237378L,s.bytes().unsignedLong(90));assertEquals(0x7600,s.bytes().unsignedWord(94));
        var ex=assertThrows(CdR11aPlcBuffer.BufferBoundary.class,()->CdR11aPlcBuffer.append(s,List.of(A)));
        assertEquals(s,ex.state());assertEquals(96,ex.byteOffset());assertEquals(CdR11aPlcBuffer.Access.SOURCE_SCAN,ex.access());
    }
    @Test void overflowRetainsEveryCompletedDescriptorRatherThanAtomicRollback() {
        var rows=java.util.Collections.nCopies(17,P);var ex=assertThrows(CdR11aPlcBuffer.BufferBoundary.class,()->CdR11aPlcBuffer.replace(seed(fill(0x7F)),rows));
        assertEquals(CdR11aPlcBuffer.Access.SOURCE_WRITE,ex.access());assertEquals(96,ex.byteOffset());
        assertEquals(CdR11aPlcBuffer.replace(CdR11aPlcBuffer.cleared(),rows.subList(0,16)),ex.state());
    }
    @Test void appendOverflowPreservesPrefixBeforeUnownedWrite() {
        var s=CdR11aPlcBuffer.replace(CdR11aPlcBuffer.cleared(),java.util.Collections.nCopies(15,P));
        var ex=assertThrows(CdR11aPlcBuffer.BufferBoundary.class,()->CdR11aPlcBuffer.append(s,List.of(A,T)));
        assertEquals(0x236E46L,ex.state().bytes().unsignedLong(90));assertEquals(0x8120,ex.state().bytes().unsignedWord(94));
        assertEquals(CdR11aPlcBuffer.Access.SOURCE_WRITE,ex.access());
    }
    @Test void popCopiesExactlyEightyEightBytesForwardAndKeepsEightByteTail() {
        byte[] b=new byte[96];for(int i=0;i<96;i++)b[i]=(byte)(i+1);
        byte[] expected=b.clone();System.arraycopy(b,6,expected,0,88);
        var before=seed(b);same(CdR11aPlcBuffer.pop(before),expected);same(before,b);
        assertEquals(b[88],CdR11aPlcBuffer.pop(before).bytes().signedByte(88));
        assertEquals(b[94],CdR11aPlcBuffer.pop(before).bytes().signedByte(94));
    }
    @Test void saturatedFinalDescriptorRepeatsUnderBuggedPop() {
        var s=CdR11aPlcBuffer.replace(CdR11aPlcBuffer.cleared(),java.util.Collections.nCopies(16,P));
        for(int i=0;i<20;i++)s=CdR11aPlcBuffer.pop(s);
        assertEquals(0x237378L,s.headSource());assertEquals(0x7600,s.headDestination());
    }
    @Test void headStoresPreserveAllOtherBytesAndDoNotMaskFullSourceLong() {
        byte[] b=fill(0x55);var before=seed(b);
        var source=CdR11aPlcBuffer.withHeadSource(before,0xFEDCBA98L);
        assertEquals(0xFEDCBA98L,source.headSource());assertEquals(0x5555,source.headDestination());
        assertArrayEquals(b, before.bytes().copy());assertArrayEquals(Arrays.copyOfRange(b,4,96),source.bytes().slice(4,92).copy());
        var dest=CdR11aPlcBuffer.withHeadDestination(source,0x20);
        assertEquals(0xFEDCBA98L,dest.headSource());assertEquals(0x20,dest.headDestination());
        assertArrayEquals(Arrays.copyOfRange(b,6,96),dest.bytes().slice(6,90).copy());
    }
    @Test void bytesAreOwnedAndValueEqualityHashDoNotUseWrapperIdentity() {
        byte[] b=fill(0x3A);var a=seed(b);var copy=seed(b.clone());b[0]=0;
        assertEquals(a,copy);assertEquals(a.hashCode(),copy.hashCode());a.bytes().copy()[0]=0;assertEquals(a,copy);
        assertNotEquals(a,CdR11aPlcBuffer.withHeadDestination(a,0x1234));
    }
    @Test void restoreByValueAndForwardReplayRetainBuggedTail() {
        var capture=CdR11aPlcBuffer.replace(CdR11aPlcBuffer.cleared(),List.of(P,A,T));
        var expected=CdR11aPlcBuffer.pop(CdR11aPlcBuffer.withHeadDestination(capture,0x7C60));
        assertEquals(expected,CdR11aPlcBuffer.pop(CdR11aPlcBuffer.withHeadDestination(seed(capture.bytes().copy()),0x7C60)));
        assertEquals(0x237378L,capture.headSource());
    }
    @Test void nativeFieldWidthsAndNullMisuseRejectWithoutChangingInput() {
        assertThrows(IllegalArgumentException.class,()->new CdR11aPlcBuffer.Entry(-1,0));
        assertThrows(IllegalArgumentException.class,()->new CdR11aPlcBuffer.Entry(0x100000000L,0));
        assertThrows(IllegalArgumentException.class,()->new CdR11aPlcBuffer.Entry(0,-1));
        assertThrows(IllegalArgumentException.class,()->new CdR11aPlcBuffer.Entry(0,0x10000));
        assertThrows(IllegalArgumentException.class,()->CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(new byte[95])));
        assertThrows(NullPointerException.class,()->CdR11aPlcBuffer.fromBytes(null));
        assertThrows(NullPointerException.class,()->CdR11aPlcBuffer.append(null,List.of()));
        assertThrows(NullPointerException.class,()->CdR11aPlcBuffer.append(CdR11aPlcBuffer.cleared(),null));
        var s=CdR11aPlcBuffer.cleared();assertThrows(IllegalArgumentException.class,()->CdR11aPlcBuffer.withHeadSource(s,-1));
        assertThrows(IllegalArgumentException.class,()->CdR11aPlcBuffer.withHeadDestination(s,65536));same(s,new byte[96]);
    }
}
