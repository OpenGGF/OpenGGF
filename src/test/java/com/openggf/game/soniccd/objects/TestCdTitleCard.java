package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal N1ecc5f3a title vectors, not World/retail ingress. Authored before production; all Java UNRUN. */
class TestCdTitleCard {
    private static final int MAP = 0x20E20E, WHOLE = 4;
    private static final int[][] ROWS = {{0x130,0x228,0x168,1},{0x100,0x238,0x178,2},
            {0x100,0x240,0x180,2},{0x100,0x248,0x188,2},{0x120,0x230,0x170,3},
            {0x140,0x248,0x188,4},{0x100,0x1D0,0x110,7},{0x100,0x1D0,0x110,8}};

    @Test void wordAliasesAppendOnlyAndDoNotBroadenDynamicIdentity() {
        assertEquals(0x0A,TITLE_CARD_SCREEN_Y_WORD.byteOffset()); assertEquals(0x2A,TITLE_CARD_DEST_X.byteOffset());
        assertEquals(0x2C,TITLE_CARD_START_X.byteOffset()); assertEquals(0x2E,TITLE_CARD_DEST_Y.byteOffset());
        assertEquals(0x30,TITLE_CARD_START_Y.byteOffset());
        assertThrows(IndexOutOfBoundsException.class,()->new CdObjectState.Identity(4));
        assertThrows(IndexOutOfBoundsException.class,()->new CdObjectPool().state(4));
    }

    @Test void constructorAndPhysicalIdRoutineGuardsDoNotReadUnreachedOwners() {
        assertThrows(NullPointerException.class,()->new CdTitleCard.Assets(null,MAP));
        var h=new Harness(); var before=h.images();
        for(int whole:new int[]{0,128,-1}) assertThrows(IllegalArgumentException.class,()->h.body.run(whole));
        h.assertImages(before);
        h.set(WHOLE,0,0); assertBoundary(CdTitleCard.UnsupportedBoundary.OBJECT_ID,()->h.body.run(WHOLE));
        for(int routine:new int[]{1,3,5,7,9,11,255}) { h.set(WHOLE,0,0x3C); h.set(WHOLE,0x24,routine);
            var selected=h.images();assertBoundary(CdTitleCard.UnsupportedBoundary.ROUTINE_DISPATCH,()->h.body.run(WHOLE));h.assertImages(selected); }
        assertTrue(h.order.isEmpty()); h.restoreSelected(before.get(WHOLE));
        var cut=new CdTitleCard.SourceCut(WHOLE,h.image(WHOLE),h.placement.capture());
        assertThrows(IllegalArgumentException.class,()->new CdTitleCard.StoreCall(CdTitleCard.StoreField.CONTROL_LOCK_BYTE,1,cut));
        assertThrows(IllegalArgumentException.class,()->new CdTitleCard.StoreCall(CdTitleCard.StoreField.SCROLL_LOCK_BYTE,2,cut));
        assertThrows(IllegalArgumentException.class,()->new CdTitleCard.PlcCall(18,cut));
        var absent=new CdTitleCard(h.placement,new CdTitleCard.Assets(CdAssetBytes.copyOf(data()),MAP),null,null,null,null,null,null);
        assertBoundary(CdTitleCard.UnsupportedBoundary.RESERVED_SLOT_OWNER,()->absent.run(WHOLE));
    }

    @Test void initChangesOnlyLiteralParentAndDirtyChildrenStoresAndNeverDraws() {
        var h=new Harness(); for(int whole=32;whole<38;whole++) h.pool().write(h.pool().state(whole).moveByte(ID,0x55));
        var expected=h.images(); parentInit(expected.get(WHOLE));
        for(int row=0;row<8;row++) childInit(expected.get(38+row),row,1,true);
        var result=h.body.run(WHOLE); h.assertImages(expected);
        assertEquals(List.of("read:ACT_BYTE"),h.order); assertFalse(result.deleted());
        assertEquals(List.of(38,39,40,41,42,43,44,45),result.effects().stream().filter(CdTitleCard.ChildInitialized.class::isInstance)
                .map(CdTitleCard.ChildInitialized.class::cast).map(CdTitleCard.ChildInitialized::wholeIndex).toList());
        assertEquals(0x30,h.word(WHOLE,0x0A)); assertEquals(Byte.toUnsignedInt(expected.get(WHOLE)[0x0C]),h.image(WHOLE).unsignedByte(0x0C));
        assertEquals(1,result.effects().stream().filter(CdTitleCard.ReadReturned.class::isInstance).count());
        // Allocation does not dispatch children. The increasing physical walk may explicitly call38 next.
        w(expected.get(38),8,0x220);expected.get(38)[1]&=0x7F;
        h.body.run(38);h.assertImages(expected);assertEquals(List.of("read:ACT_BYTE","draw"),h.order);
    }

    @Test void everyFindUsesActualFirstFreeIdWithoutClearingOrAssumingContiguity() {
        var h=new Harness(); int[] free={32,34,39,50,61,78,101,127};
        for(int whole=32;whole<128;whole++) h.pool().write(h.pool().state(whole).moveByte(ID,0x55));
        for(int whole:free) h.pool().write(h.pool().state(whole).moveByte(ID,0));
        var expected=h.images(); parentInit(expected.get(WHOLE));
        for(int row=0;row<8;row++) childInit(expected.get(free[row]),row,1,true);
        h.body.run(WHOLE); h.assertImages(expected);
    }

    @Test void conditionalSixthRowReadsRawActAndWrapsOnlyItsFrameByte() {
        for(int act:new int[]{0,1,2,127,128,251,252,255}) {
            var h=new Harness(); h.act=act; var expected=h.images(); parentInit(expected.get(WHOLE));
            for(int row=0;row<8;row++) childInit(expected.get(32+row),row,act,true);
            h.body.run(WHOLE); h.assertImages(expected); assertEquals(List.of("read:ACT_BYTE"),h.order);
            assertEquals((4+act)&255,h.image(37).unsignedByte(0x1A));
        }
    }

    @Test void badActAndMissingActOwnerStopAfterSixthChildFrameButBeforeItsTimer() {
        for(boolean absent:new boolean[]{false,true}) {
            var h=new Harness(); h.act=256; if(absent) h.build(false,true,true,true,true);
            var expected=h.images(); parentInit(expected.get(WHOLE));
            for(int row=0;row<5;row++) childInit(expected.get(32+row),row,1,true);
            childInit(expected.get(37),5,0,false);
            assertBoundary(absent?CdTitleCard.UnsupportedBoundary.READ_OWNER:CdTitleCard.UnsupportedBoundary.READ_VALUE_WIDTH,()->h.body.run(WHOLE));
            h.assertImages(expected);
        }
    }

    @Test void truncatedDataStopsAtReachedWordAfterNativeHeaderAndPreservesAllOtherImages() {
        var h=new Harness(Arrays.copyOf(data(),1)); var expected=h.images(); parentInit(expected.get(WHOLE));
        childHeader(expected.get(32));
        var failure=assertThrows(CdTitleCard.UnsupportedTitleCard.class,()->h.body.run(WHOLE));
        assertEquals(CdTitleCard.UnsupportedBoundary.INIT_DATA_READ,failure.boundary()); assertEquals(0,failure.value());
        h.assertImages(expected); assertTrue(h.order.isEmpty());
    }

    @Test void exhaustedAllocationStopsBeforeUnownedAddressWithEveryCompletedChildRetained() {
        for(int available:new int[]{0,1,5,7}) {
            var h=new Harness(); for(int whole=32;whole<128;whole++) h.pool().write(h.pool().state(whole).moveByte(ID,0x55));
            for(int row=0;row<available;row++) h.pool().write(h.pool().state(32+row).moveByte(ID,0));
            var expected=h.images(); parentInit(expected.get(WHOLE));
            for(int row=0;row<available;row++) childInit(expected.get(32+row),row,1,true);
            var failure=assertThrows(CdTitleCard.UnsupportedTitleCard.class,()->h.body.run(WHOLE));
            assertEquals(CdTitleCard.UnsupportedBoundary.ALLOCATION_ADDRESS,failure.boundary()); assertEquals(0xFFF000,failure.value()); h.assertImages(expected);
            assertEquals(available>=6?List.of("read:ACT_BYTE"):List.of(),h.order);
        }
    }

    @Test void signedSlidesUseWordCompareAndEightOrSixteenStepWithoutClamping() {
        int[][] vectors={{2,0x20,0x30,0x28},{2,0x40,0x30,0x38},{2,0x7FFF,0x8000,0x7FF7},
                {2,0xFFFF,0,7},{4,0x20,0x30,0x28},{4,0x40,0x30,0x38},{4,0x8000,0x7FFF,0x8008},
                {6,0x20,0x30,0x30},{6,0x40,0x30,0x30},{8,0xFFFF,0,0xF}};
        for(var v:vectors) {
            var h=new Harness(); int pos=v[0]==2||v[0]==6?0x0A:8;
            int dest=switch(v[0]) {case 2->0x2E;case 4->0x2A;case 6->0x30;default->0x2C;};
            h.set(WHOLE,0x24,v[0]);h.set(WHOLE,0x1E,0);h.word(WHOLE,pos,v[1]);h.word(WHOLE,dest,v[2]);
            var expected=h.images();w(expected.get(WHOLE),pos,v[3]);expected.get(WHOLE)[1]&=0x7F;
            h.body.run(WHOLE);h.assertImages(expected);assertEquals(List.of("draw"),h.order);
        }
    }

    @Test void equalSlideInOnlyAdvancesItsRoutineBeforeTheStandardDrawBitClear() {
        for(int routine:new int[]{2,4}) {
            var h=new Harness();h.set(WHOLE,0x24,routine);h.word(WHOLE,routine==2?0x0A:8,0x8000);
            h.word(WHOLE,routine==2?0x2E:0x2A,0x8000);var expected=h.images();expected.get(WHOLE)[0x24]=(byte)(routine+4);expected.get(WHOLE)[1]&=0x7F;
            h.body.run(WHOLE);h.assertImages(expected);assertEquals(List.of("draw"),h.order);
        }
    }

    @Test void nonzeroSlideOutTimersCountOneByteThenDrawWithoutMovementOrEqualityAction() {
        for(int routine:new int[]{6,8}) for(int timer:new int[]{1,2,0x80,0xFF}) {
            var h=new Harness();h.set(WHOLE,0x24,routine);h.set(WHOLE,0x1E,timer);var expected=h.images();
            expected.get(WHOLE)[0x1E]=(byte)(timer-1);expected.get(WHOLE)[1]&=0x7F;
            h.body.run(WHOLE);h.assertImages(expected);assertEquals(List.of("draw"),h.order);
        }
    }

    @Test void verticalExitEqualityStoresRoutineThenScrollLockThenExactPlc2WithoutDraw() {
        var h=new Harness();h.set(WHOLE,0x24,6);h.set(WHOLE,0x1E,0);h.word(WHOLE,0x0A,0x30);h.word(WHOLE,0x30,0x30);
        var expected=h.images();expected.get(WHOLE)[0x24]=10;
        h.hook=site->{h.assertImages(expected);if(site.equals("plc"))assertEquals(1,h.scrollLock);};
        h.body.run(WHOLE);h.assertImages(expected);assertEquals(List.of("store:SCROLL_LOCK_BYTE:1","plc:2"),h.order);assertEquals(0x81,h.controlLock);
    }

    @Test void horizontalExitEqualityDeletesOnlyItsWholeImageAndHasNoDrawOrSavedBankEffect() {
        for(int whole:new int[]{4,70}) {
            var h=new Harness();h.select(whole,8);h.set(whole,0x1E,0);h.word(whole,8,0x8000);h.word(whole,0x2C,0x8000);
            var expected=h.images();expected.set(whole,new byte[64]);var result=h.body.run(whole);h.assertImages(expected);
            assertTrue(result.deleted());assertTrue(h.order.isEmpty());
        }
    }

    @Test void waitReadsOnlyActualRawLongHeadAndZeroClearsLocksInOrderBeforeDelete() {
        for(int head:new int[]{1,-1,Integer.MIN_VALUE,0}) {
            var h=new Harness(new byte[0]);h.set(WHOLE,0x24,10);h.head=head;var expected=h.images();
            if(head==0)expected.set(WHOLE,new byte[64]);
            h.body.run(WHOLE);h.assertImages(expected);
            assertEquals(head==0?List.of("read:PLC_HEAD_LONG","store:SCROLL_LOCK_BYTE:0","store:CONTROL_LOCK_BYTE:0"):List.of("read:PLC_HEAD_LONG"),h.order);
            assertEquals(head==0?0:0x91,h.scrollLock);assertEquals(head==0?0:0x81,h.controlLock);
        }
    }

    @Test void absentReachedPortsKeepTheirNativePrefixAndUnreachedPortsAreOptional() {
        var init=new Harness();init.build(true,false,false,false,false);assertDoesNotThrow(()->init.body.run(WHOLE));
        var draw=new Harness();draw.set(WHOLE,0x24,2);draw.build(false,false,false,false,false);
        assertBoundary(CdTitleCard.UnsupportedBoundary.DRAW_OWNER,()->draw.body.run(WHOLE));
        var exit=new Harness();exit.set(WHOLE,0x24,6);exit.set(WHOLE,0x1E,0);exit.word(WHOLE,0x0A,0x30);exit.word(WHOLE,0x30,0x30);exit.build(false,false,true,false,false);
        assertBoundary(CdTitleCard.UnsupportedBoundary.PLC_OWNER,()->exit.body.run(WHOLE));assertEquals(10,exit.image(WHOLE).unsignedByte(0x24));assertEquals(1,exit.scrollLock);
        var wait=new Harness();wait.set(WHOLE,0x24,10);wait.build(false,false,false,false,false);
        assertBoundary(CdTitleCard.UnsupportedBoundary.READ_OWNER,()->wait.body.run(WHOLE));
        var store=new Harness();store.set(WHOLE,0x24,10);store.head=0;store.build(false,true,false,false,false);
        assertBoundary(CdTitleCard.UnsupportedBoundary.STORE_OWNER,()->store.body.run(WHOLE));assertEquals(0x3C,store.image(WHOLE).unsignedByte(0));
    }

    @Test void callbackMutationIsRejectedOriginalFailureSurvivesAndReentryCannotAdvanceAnotherCall() {
        var h=new Harness();h.set(WHOLE,0x24,10);h.head=1;var before=h.images();
        h.hook=site->assertThrows(IllegalStateException.class,()->h.body.run(WHOLE));h.body.run(WHOLE);h.assertImages(before);
        var fault=new IllegalArgumentException("native port stopped");
        h.hook=site->{h.pool().write(h.pool().state(127).moveByte(ID,0x66));throw fault;};
        assertSame(fault,assertThrows(IllegalArgumentException.class,()->h.body.run(WHOLE)));assertTrue(fault.getSuppressed().length>0);
        var changed=new Harness();changed.set(WHOLE,0x24,10);changed.head=1;
        changed.hook=site->changed.pool().write(changed.pool().state(127).moveByte(ID,0x66));
        assertThrows(IllegalStateException.class,()->changed.body.run(WHOLE));assertEquals(0x66,changed.pool().state(127).id());
        var wrongDraw=new Harness();wrongDraw.set(WHOLE,0x24,2);wrongDraw.hook=site->{if(site.equals("draw"))wrongDraw.set(WHOLE,0x19,0xEF);};
        assertThrows(IllegalStateException.class,()->wrongDraw.body.run(WHOLE));
    }

    @Test void laterPortFailureRetainsCompletedGlobalPrefixAndDoesNotDeleteOrReplay() {
        var h=new Harness();h.set(WHOLE,0x24,10);h.head=0;var before=h.images();
        var fault=new AssertionError("control owner stopped");h.hook=site->{if(site.equals("store:CONTROL_LOCK_BYTE"))throw fault;};
        assertSame(fault,assertThrows(AssertionError.class,()->h.body.run(WHOLE)));h.assertImages(before);
        assertEquals(0,h.scrollLock);assertEquals(0x81,h.controlLock);
        assertEquals(List.of("read:PLC_HEAD_LONG","store:SCROLL_LOCK_BYTE:0","store:CONTROL_LOCK_BYTE:0"),h.order);
    }

    @Test void plcFailureRetainsRoutineAndScrollLockAndVerifiesTheCompletePlacementOnThrow() {
        for(boolean corrupt:new boolean[]{false,true}) {
            var h=new Harness();h.set(WHOLE,0x24,6);h.set(WHOLE,0x1E,0);h.word(WHOLE,0x0A,0x30);h.word(WHOLE,0x30,0x30);
            var expected=h.images();expected.get(WHOLE)[0x24]=10;var fault=new IllegalArgumentException("PLC owner stopped");
            h.hook=site->{if(site.equals("plc")){if(corrupt)h.pool().write(h.pool().state(127).moveByte(ID,0x66));throw fault;}};
            assertSame(fault,assertThrows(IllegalArgumentException.class,()->h.body.run(WHOLE)));
            if(!corrupt)h.assertImages(expected);else {assertTrue(fault.getSuppressed().length>0);assertEquals(0x66,h.pool().state(127).id());}
            assertEquals(10,h.image(WHOLE).unsignedByte(0x24));assertEquals(1,h.scrollLock);assertEquals(0x81,h.controlLock);
            assertEquals(List.of("store:SCROLL_LOCK_BYTE:1","plc:2"),h.order);
        }
    }

    @Test void reservedWriteFailureRetainsItsAtomicSelectedPrefixAndDetectsDynamicCorruption() {
        var h=new Harness();var fault=new AssertionError("reserved owner stopped");
        h.reservedHook=site->{h.pool().write(h.pool().state(127).moveByte(ID,0x66));throw fault;};
        assertSame(fault,assertThrows(AssertionError.class,()->h.body.run(WHOLE)));
        assertEquals(2,h.image(WHOLE).unsignedByte(0x24));assertEquals(0x66,h.pool().state(127).id());
        assertTrue(fault.getSuppressed().length>0);assertTrue(h.order.isEmpty());
    }

    private static byte[] data() {var b=ByteBuffer.allocate(64);for(var row:ROWS)b.putShort((short)row[0]).putShort((short)row[1]).putShort((short)row[2]).put((byte)row[3]).put((byte)0x5A);return b.array();}
    private static void parentInit(byte[] b) {b[0x24]=2;w(b,8,0x118);w(b,0x0A,0x30);w(b,0x30,0x30);w(b,0x2E,0xF0);b[0x1E]=0x5A;w(b,2,0x8360);l(b,4,MAP);b[0x18]=4;}
    private static void childHeader(byte[] b) {b[0]=0x3C;b[0x24]=4;w(b,2,0x8360);l(b,4,MAP);}
    private static void childInit(byte[] b,int row,int act,boolean complete) {childHeader(b);w(b,0x0A,ROWS[row][0]);w(b,8,ROWS[row][1]);w(b,0x2C,ROWS[row][1]);w(b,0x2A,ROWS[row][2]);b[0x1A]=(byte)(ROWS[row][3]+(row==5?act:0));if(complete)b[0x1E]=0x5A;}
    private static void w(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}
    private static void l(byte[] b,int at,int v){w(b,at,v>>>16);w(b,at+2,v);}
    private static CdObjectState dirty(CdObjectState s){for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,0x51+7*f.byteOffset());for(var f:CdObjectState.WordField.values())s=s.moveWord(f,0xA157+0x103*f.byteOffset());for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0xA1536729+0x1030507*f.byteOffset());return s;}
    private static CdObjectPlacements layout(){var b=ByteBuffer.allocate(34);b.putShort((short)12).putShort((short)28).putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0).putShort((short)0).put((byte)0x3C).put((byte)0).put((byte)0x40).put((byte)0);b.putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0xFFFF).putInt(0);return CdObjectPlacements.decodeLayout(b.array());}
    private static void assertBoundary(CdTitleCard.UnsupportedBoundary b,org.junit.jupiter.api.function.Executable r){assertEquals(b,assertThrows(CdTitleCard.UnsupportedTitleCard.class,r).boundary());}
    private static final class Harness {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(layout());final CdAssetBytes[] reserved=new CdAssetBytes[32];
        final List<String> order=new ArrayList<>();final byte[] table;final CdAssetBytes originalSaved=placement.capture().savedFlags();
        final CdObjectPlacementRuntime.Status originalStatus=placement.status();CdTitleCard body;int act=1,head,scrollLock=0x91,controlLock=0x81;
        Consumer<String> hook=s->{},reservedHook=s->{};
        Harness(){this(data());}Harness(byte[] table){this.table=table;for(int i=1;i<32;i++){byte[] b=new byte[64];for(int k=0;k<64;k++)b[k]=(byte)(i*7+k*13);reserved[i]=CdAssetBytes.copyOf(b);}for(int i=32;i<128;i++)pool().write(dirty(pool().state(i)).moveByte(ID,0));select(WHOLE,0);build(true,true,true,true,true);}
        void build(boolean readAct,boolean readHead,boolean store,boolean plc,boolean draw){body=new CdTitleCard(placement,new CdTitleCard.Assets(CdAssetBytes.copyOf(table),MAP),new CdTitleCard.ReservedSlots(){public CdAssetBytes read(int whole){return reserved[whole];}public void write(int whole,CdAssetBytes expected,CdAssetBytes next){assertArrayEquals(expected.copy(),reserved[whole].copy());reserved[whole]=next;reservedHook.accept("write");}},
                readAct?this::read:null,readHead?this::read:null,
                store?c->{check(c.cut());order.add("store:"+c.field()+":"+c.value());hook.accept("store:"+c.field());if(c.field()==CdTitleCard.StoreField.SCROLL_LOCK_BYTE)scrollLock=c.value();else controlLock=c.value();}:null,
                plc?c->{check(c.cut());order.add("plc:"+c.nativeId());hook.accept("plc");}:null,
                draw?c->{check(c.cut());order.add("draw");hook.accept("draw");set(c.cut().wholeIndex(),1,image(c.cut().wholeIndex()).unsignedByte(1)&0x7F);}:null);
        }
        CdObjectPool pool(){return placement.pool();}
        int read(CdTitleCard.ReadCall c){check(c.cut());order.add("read:"+c.field());hook.accept("read:"+c.field());return c.field()==CdTitleCard.ReadField.ACT_BYTE?act:head;}
        CdAssetBytes image(int whole){return whole<32?reserved[whole]:CdAssetBytes.copyOf(pool().state(whole).copyBytes());}
        void select(int whole,int routine){set(whole,0,0x3C);set(whole,0x24,routine);set(whole,1,0x81);}
        void set(int whole,int at,int value){if(whole<32){byte[] b=image(whole).copy();b[at]=(byte)value;reserved[whole]=CdAssetBytes.copyOf(b);}else{var f=Arrays.stream(CdObjectState.ByteField.values()).filter(x->x.byteOffset()==at).findFirst().orElseThrow();pool().write(pool().state(whole).moveByte(f,value));}}
        void word(int whole,int at,int value){if(whole<32){byte[] b=image(whole).copy();w(b,at,value);reserved[whole]=CdAssetBytes.copyOf(b);}else{var f=Arrays.stream(CdObjectState.WordField.values()).filter(x->x.byteOffset()==at).findFirst().orElseThrow();pool().write(pool().state(whole).moveWord(f,value));}}
        int word(int whole,int at){return image(whole).unsignedWord(at);}
        List<byte[]> images(){var out=new ArrayList<byte[]>();out.add(new byte[64]);for(int i=1;i<128;i++)out.add(image(i).copy());return out;}
        void restoreSelected(byte[] bytes){reserved[WHOLE]=CdAssetBytes.copyOf(bytes);}
        void assertImages(List<byte[]> expected){for(int i=1;i<128;i++)assertArrayEquals(expected.get(i),image(i).copy(),"whole "+i);assertArrayEquals(originalSaved.copy(),placement.capture().savedFlags().copy());assertEquals(originalStatus,placement.status());}
        void check(CdTitleCard.SourceCut c){assertArrayEquals(image(c.wholeIndex()).copy(),c.expectedSource().copy());placement.validateRestore(c.actualPlacement());assertEquals(placement.status(),c.actualPlacement().status());assertEquals(pool().capture().slots(),c.actualPlacement().pool().slots());assertArrayEquals(placement.capture().savedFlags().copy(),c.actualPlacement().savedFlags().copy());}
    }
}
