package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;
import static com.openggf.game.soniccd.objects.TestCdR4SolidMovers.*;

/** Constructed finite component controls. N1ecc5f3a crush_blocks:123-173 and swing:104-159.
 * No World/publication, retail negative-span binding, native execution or route claims.
 */
class TestCdR4SolidMoverNativeCuts {
    @Test void dirtyCrushWordSelectsTheActualSignedPrecedingRowBeforePhaseMask() {
        Scene s=crush(0xFFF8);var in=withData(s,relative(s,new RelativeTable(bytes(1,2,1,1,1,1,2,1),-8)));
        var before=in.pool();var out=CdR4CrushBlocks.run(in);ok(out);
        var expected=CdR4SolidMover.put(before.slots().get(8),0x2A,2,0x0101);
        expected=CdR4SolidMover.put(expected,0x2C,4,0x01010201);expected=CdR4SolidMover.put(expected,0x34,2,0);
        assertEquals(expected,object(out));assertEquals(Tail.DESPAWN_ANCHOR_30,out.tail());
        childPrefix(in,out);pair(in,out);assertEquals(before.slots(),s.pool.capture().slots());
    }
    @Test void missingSecondSignedLongRetainsTheFirstTimerAndEarlierChildWrites() {
        Scene s=crush(0xFFF8);var in=withData(s,relative(s,new RelativeTable(bytes(1,2,1,1),-8)));
        var out=CdR4CrushBlocks.run(in);assertInstanceOf(IndexOutOfBoundsException.class,out.stopped().orElseThrow());
        assertEquals(0x0101,w(object(out),0x2A));assertEquals(0x12345,l(object(out),0x2C));
        assertEquals(0xFFF8,w(object(out),0x34));assertEquals(Tail.NONE,out.tail());
        assertTrue(out.nativeSolidStop().isEmpty());childPrefix(in,out);pair(in,out);
    }
    @Test void absentNegativeExtentStopsWithoutBorrowingShapesOrNormalizingThePhase() {
        Scene s=crush(0xFFF8);int[] borrows={0};
        var data=new CdR4SolidMover.Data(){
            public int mapping(Mapping m){return s.data.mapping(m);}
            public CdAssetBytes table(Table t){return s.data.table(t);}
            public int address(Address a){return s.data.address(a);}
            public RelativeTable relativeTable(Table t){borrows[0]++;return CdR4SolidMover.Data.super.relativeTable(t);}
        };
        var in=withData(s,data);var out=CdR4CrushBlocks.run(in);
        assertInstanceOf(IndexOutOfBoundsException.class,out.stopped().orElseThrow());assertEquals(1,borrows[0]);
        assertEquals(0,w(object(out),0x2A));assertEquals(0x12345,l(object(out),0x2C));assertEquals(0xFFF8,w(object(out),0x34));
        childPrefix(in,out);pair(in,out);
    }
    @ParameterizedTest @ValueSource(ints={0x8000,0x7FFE})
    void sourceRelativeLongsKeepTheEvenSignedWordExtremes(int phase) {
        Scene s=crush(phase);var in=withData(s,relative(s,new RelativeTable(longs(0x11223344,0x55667788),(short)phase)));
        var out=CdR4CrushBlocks.run(in);ok(out);assertEquals(0x3344,w(object(out),0x2A));
        assertEquals(0x55667788,l(object(out),0x2C));assertEquals((phase+8)&31,w(object(out),0x34));childPrefix(in,out);pair(in,out);
    }
    @Test void swingSineErrorReturnsTheOriginalErrorAndOnlyItsReachedPhasePrefix() {
        Scene s=new Scene();s.set(0x24,1,2);s.set(0x3A,2,0xC055);s.set(0x3C,1,6);
        var fault=new AssertionError("borrowed sine Error");s.sine=a->{throw fault;};var in=s.in();
        var out=assertDoesNotThrow(()->CdR4Swing.run(in));assertSame(fault,out.stopped().orElseThrow());
        assertEquals(Tail.NONE,out.tail());assertEquals(CdR4SolidMover.put(in.pool().slots().get(8),0x3A,2,0xBFD5),object(out));
        assertTrue(out.prefix().observations().isEmpty());assertTrue(out.nativeSolidStop().isEmpty());pair(in,out);
        for(int i=0;i<96;i++)if(i!=8)assertEquals(in.pool().slots().get(i),out.prefix().pool().slots().get(i));
        assertEquals(in.pool().slots(),s.pool.capture().slots());
    }
    @Test void swingSineErrorAfterInitKeepsAllSixChildrenAndNoPositionOrSolidTail() {
        Scene s=new Scene();s.set(0x3A,2,0x0055);var fault=new AssertionError("after allocation Error");
        s.sine=a->{throw fault;};var in=s.in();var out=assertDoesNotThrow(()->CdR4Swing.run(in));
        assertSame(fault,out.stopped().orElseThrow());assertTrue(out.nativeSolidStop().isEmpty());assertEquals(Tail.NONE,out.tail());
        var expected=in.pool().slots().get(8);
        for(int[] store:new int[][]{{0x24,1,2},{1,1,4},{0x18,1,4},{4,4,0x20ABCD},{2,2,0x340},
                {0x19,1,32},{0x16,1,10},{0x36,2,0x100},{0x32,2,0x200},{0x3A,2,0xBFD5}})
            expected=CdR4SolidMover.put(expected,store[0],store[1],store[2]);
        assertEquals(expected,object(out));assertEquals(6,out.prefix().observations().size());
        for(int n=0;n<6;n++) {
            var child=in.pool().slots().get(n);
            for(int[] store:new int[][]{{0,1,0x29},{0x36,2,0x100},{0x32,2,0x200},{0x28,1,0},{0x3C,1,6-n},{0x1A,1,n==0?1:2}})
                child=CdR4SolidMover.put(child,store[0],store[1],store[2]);
            assertEquals(child,at(out,32+n));assertInstanceOf(Search.class,out.prefix().observations().get(n));
        }
        for(int n=38;n<128;n++)if(n!=40)assertEquals(in.pool().slots().get(n-32),at(out,n));
        pair(in,out);assertEquals(in.pool().slots(),s.pool.capture().slots());
    }
    @Test void onlyTheSharedOwnersActualStoppedOutcomeSuppliesAnInnerNativePrefix() {
        Scene s=new Scene();s.set(1,1,128);s.primary=player(1,0x1001234,0x1e05678,0x123,0x100,10,1);s.secondary=withFlags(s.secondary,10);
        var out=CdR4Block.run(s.in());var nativeStop=out.nativeSolidStop().orElseThrow();
        assertEquals(CdSolidObject.StopSite.STAND_RELEASE,nativeStop.stop().site());
        assertSame(nativeStop.stop().originalException(),out.stopped().orElseThrow());
        assertEquals(Tail.NONE,out.tail());assertEquals(s.secondary,out.prefix().secondary());
    }
    @Test void canonicalBorrowDoesNotRejectKnownButUnreachedSurplusBytes() {
        Scene s=crush(0);byte[] raw=new byte[40000];raw[3]=60;
        s.data.tables.put(Table.CRUSH_MOTION,CdAssetBytes.copyOf(raw));var in=s.in();
        var out=CdR4CrushBlocks.run(in);ok(out);assertEquals(60,w(object(out),0x2A));
        assertEquals(0,l(object(out),0x2C));assertEquals(8,w(object(out),0x34));childPrefix(in,out);pair(in,out);
    }
    private static Scene crush(int phase) {
        Scene s=new Scene();s.set(0,1,0x33);s.set(0x24,1,2);s.set(0x2A,2,1);s.set(0x2C,4,0x12345);s.set(0x34,2,phase);
        for(int n=0;n<8;n++) {
            int whole=41+n;s.slot(whole,0,1,0x33);s.slot(whole,8,4,0x0400CDEF+(n<<16));s.slot(whole,0x10,2,0xBEEF);
            s.set(0x38+n,1,s.pool.state(whole).identity().nativeAddress24()>>6);
        }
        s.slot(127,0x34,2,0xA55A);return s;
    }
    private static CdR4SolidMover.Data relative(Scene s,RelativeTable view) {
        return new CdR4SolidMover.Data(){
            public int mapping(Mapping m){return s.data.mapping(m);}
            public CdAssetBytes table(Table t){return s.data.table(t);}
            public int address(Address a){return s.data.address(a);}
            public RelativeTable relativeTable(Table t){assertEquals(Table.CRUSH_MOTION,t);return view;}
        };
    }
    private static Inputs withData(Scene s,CdR4SolidMover.Data data) {
        var i=s.in();return new Inputs(i.pool(),i.wholeIndex(),i.primary(),i.secondary(),i.gates(),i.globals(),i.solid(),data,
                i.sine(),i.terrain(),i.savedFlags(),i.timeZone(),i.water(),i.widths());
    }
    private static void childPrefix(Inputs in,Outcome out) {
        for(int n=0;n<8;n++) {
            var child=in.pool().slots().get(9+n);int delta=(n&1)==0?-0x12345:0x12345;
            child=CdR4SolidMover.put(child,8,4,l(child,8)+delta);
            child=CdR4SolidMover.put(child,0x10,2,(n&1)==0?-(0x12345>>8):0x12345>>8);
            assertEquals(child,at(out,41+n));
        }
        for(int i=0;i<96;i++)if(i<8||i>16)assertEquals(in.pool().slots().get(i),out.prefix().pool().slots().get(i));
    }
    private static void pair(Inputs in,Outcome out) {
        assertEquals(in.primary(),out.prefix().primary());assertEquals(in.secondary(),out.prefix().secondary());
        assertEquals(in.globals(),out.prefix().globals());assertEquals(in.water(),out.prefix().water());
        assertArrayEquals(in.savedFlags().copy(),out.prefix().savedFlags().copy());
    }
}
