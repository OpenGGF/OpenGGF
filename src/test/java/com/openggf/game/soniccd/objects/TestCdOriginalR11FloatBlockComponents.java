package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed shared-component vectors, NOT original World execution or authenticated media.
 * Immutable fixture DATA/format setup from saved cc272559 TestCdFloatBlock; shared algorithms run once.
 * Authored and frozen before production; Java/runtime/pixels UNRUN. */
class TestCdOriginalR11FloatBlockComponents {
    private static final int WHOLE=40, MAP=0x00FB1012, TILE_WORD=0x5A3C;
    private static final byte[] ANI = bytes(
            0x00, 0x06, 0x00, 0x0A, 0x00, 0x14,
            0x02, 0x05, 0xFF, 0x00,
            0x02, 0x01, 0x05, 0x02, 0x05, 0x03, 0x05, 0x04, 0x05, 0xFC,
            0x02, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00, 0xFC);
    /** MapSpr_FloatBlock (Data/Mappings.asm): six frames, real zero pads after frames 0, 3 and 4. */
    private static final byte[] MAPS = bytes(
            0x00, 0x0C, 0x00, 0x0E, 0x00, 0x1E, 0x00, 0x2E, 0x00, 0x3A, 0x00, 0x46,
            0x00, 0x00,
            0x03, 0xF4, 0x09, 0x00, 0x00, 0xF4, 0x04, 0x00, 0x00, 0x00, 0xFC, 0x04, 0x00, 0x00, 0x00, 0x04,
            0x03, 0xF4, 0x09, 0x08, 0x00, 0xF4, 0x04, 0x00, 0x08, 0x00, 0xF4, 0x04, 0x00, 0x08, 0x00, 0xFC,
            0x02, 0xF4, 0x09, 0x18, 0x00, 0xF4, 0x04, 0x00, 0x18, 0x00, 0xFC, 0x00,
            0x02, 0xF4, 0x09, 0x10, 0x00, 0xF4, 0x04, 0x00, 0x10, 0x00, 0xFC, 0x00,
            0x01, 0xF4, 0x0A, 0x00, 0x06, 0xF4);
    private static final byte[] TILE_IDS = tileIds();

    @Test void dirtyInitPublishesBothPrimaryCallsInOrderWithoutSecondaryOrUnwrittenByteChanges() {
        var h=new H();h.edit(s->s.moveByte(SPRITE_FLAGS,1).moveByte(SUBTYPE,0xFE).moveByte(ANGLE,0xA5)
                .moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0xFEDC).moveWord(X_VELOCITY,0x9876));
        var before=h.slot();var out=h.run(1,0x80,0);
        assertEquals(List.of("prepare0","pool0","primary0","prepare1","pool1","primary1","draw"),h.order);
        var init=before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,5).moveLong(MAPPINGS,MAP).moveWord(TILE,TILE_WORD)
                .moveByte(PRIORITY,1).moveByte(WIDTH,12).moveByte(Y_RADIUS,12).moveByte(MAP_FRAME,5);
        assertEquals(init,h.calls.getFirst().callCut().slots().get(WHOLE-32));
        assertEquals(init.moveByte(MAP_FRAME,0),out.state());
        assertEquals(h.result(0).player(),h.calls.get(1).player());
        assertEquals(h.result(0).globals(),h.calls.get(1).globals());
        assertEquals(h.result(0).object(),h.calls.get(1).callCut().slots().get(WHOLE-32));
        assertEquals(2,out.effects().stream().filter(CdFloatBlock.TopSolid.class::isInstance).count());
        assertFalse(h.block.isCurrentPrefixTopSolidCall(h.calls.getFirst(),0));
        assertFalse(h.block.isCurrentDrawCall(h.runtime.pool(),out.state().identity()));
    }
    @Test void rawWarpByteAndUnsignedWordWrapUseSourceBranches() {
        for(int time:new int[]{0,1,2,0x80,0x81,0x82}){
            var h=new H();var out=h.run(time,0x80,0);
            assertEquals(time==0||time==2?5:0,out.state().unsignedByte(MAP_FRAME),"raw time "+time);
            assertEquals(2,h.calls.size());
        }
        var h=new H();h.edit(s->s.moveByte(ROUTINE,4).moveWord(Y,0xFFFF).moveWord(Y_FRACTION,0xABCD));
        var out=h.run(2,0x80,0xFF80);assertEquals(1,out.state().centreY());
        assertEquals(0xABCD,out.state().word(Y_FRACTION));assertFalse(out.deleted());
    }
    @Test void plainWindowDeletionRetainsSavedBankAndAllOtherDirtySlotsEvenWithFullAllocation() {
        var h=new H();var pool=h.runtime.pool();
        for(int w=32;w<128;w++)if(w!=WHOLE)pool.write(pool.state(w).moveByte(ID,0x55).moveByte(FLAGS,0xFA).moveWord(X_FRACTION,w));
        var before=h.runtime.capture();assertTrue(java.util.stream.IntStream.range(0,before.savedFlags().length()).anyMatch(i->before.savedFlags().unsignedByte(i)!=0));h.edit(s->s.moveByte(ROUTINE,12).moveWord(X,0x8000));
        var out=h.run(1,0x80,0);assertTrue(out.deleted());assertArrayEquals(new byte[64],out.state().copyBytes());
        assertArrayEquals(before.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
        for(int w=32;w<128;w++)if(w!=WHOLE)assertEquals(before.pool().slots().get(w-32),pool.state(w));
        assertTrue(h.calls.isEmpty());assertEquals(List.of(),h.order);
    }
    @Test void invalidRawSubtypeRetainsReachedInitPrefixAndDoesNotInvokeTop() {
        var h=new H();h.edit(s->s.moveByte(SUBTYPE2,255).moveByte(PRIORITY,0x77));var before=h.slot();
        assertThrows(UnsupportedOperationException.class,()->h.run(1,0x80,0));
        assertEquals(before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveLong(MAPPINGS,MAP),h.slot());
        assertEquals(0x77,h.slot().unsignedByte(PRIORITY));assertTrue(h.calls.isEmpty());
    }
    @Test void runtimeFailureBeforePublicationRetainsInitAndOriginalThrowable() {
        var h=new H();var original=new IllegalStateException("actual callback failure");h.before=()->{throw original;};
        assertSame(original,assertThrows(IllegalStateException.class,()->h.run(1,0x80,0)));
        assertEquals(2,h.slot().routine());assertEquals(5,h.slot().unsignedByte(MAP_FRAME));
        assertEquals(1,h.calls.size());assertEquals(List.of("prepare0"),h.order);
        assertFalse(h.block.isCurrentPrefixTopSolidCall(h.calls.getFirst(),0));
    }
    @Test void errorAfterFirstAdoptionRetainsExactPublishedPrefixAndStopsPairFallthrough() {
        var h=new H();var original=new AssertionError("after adoption");h.after=()->{throw original;};
        assertSame(original,assertThrows(AssertionError.class,()->h.run(1,0x80,0)));
        assertEquals(h.result(0).object(),h.slot());assertEquals(h.result(0).player(),h.player);
        assertEquals(1,h.calls.size());assertEquals(List.of("prepare0","pool0","primary0"),h.order);
    }
    @Test void reentryAndCopiedOperationDoNotAcquireCurrentProducerAuthority() {
        var h=new H();h.before=()->{
            var c=h.calls.getLast();int ordinal=h.calls.size()-1;assertTrue(h.block.isCurrentPrefixTopSolidCall(c,ordinal));
            assertFalse(h.block.isCurrentPrefixTopSolidCall(new CdFloatBlock.PrefixTopSolidCall(c.callCut(),c.player(),c.globals(),c.outcome()),0));
            assertFalse(h.block.isCurrentPrefixTopSolidCall(c,ordinal+1));
            assertThrows(IllegalStateException.class,()->h.run(1,0x80,0));
        };h.run(1,0x80,0);assertEquals(2,h.calls.size());
    }
    @Test void dynamicCounterpartReleasePublishesDirtyIdZeroImageBeforeSecondTop() {
        var h=new H();h.onScreen();var p=new P();p.flags=8;p.stand=41;h.player=p.player();
        var other=h.runtime.pool().state(41).moveByte(FLAGS,0xAB).moveWord(X_FRACTION,0xBEEF);h.runtime.pool().write(other);
        h.run(1,0x80,0);assertEquals(List.of(other.moveByte(FLAGS,0xA3)),h.result(0).otherSlots());
        assertEquals(other.moveByte(FLAGS,0xA3),h.calls.get(1).callCut().slots().get(9));
        assertEquals(WHOLE,h.player.standObject());assertEquals(0,h.runtime.pool().state(41).id());
    }
    @Test void reservedSecondaryReleaseStopsAtReachedPrefixWithoutInventedSecondaryReadOrReturn() {
        var h=new H();h.onScreen();var p=new P();p.flags=8;p.stand=1;h.player=p.player();
        var thrown=assertThrows(CdSolidObject.UnsupportedSolidObject.class,()->h.run(1,0x80,0));
        assertEquals(1,h.calls.size());var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,h.calls.getFirst().outcome());
        assertSame(stopped.stop().originalException(),thrown);assertEquals(CdSolidObject.StopSite.STAND_RELEASE,stopped.stop().site());
        assertEquals(stopped.prefix().object(),h.slot());assertEquals(stopped.prefix().player(),h.player);
        assertFalse(h.order.contains("draw"));
    }
    @Test void animationFiniteReadFailureRetainsItsNativePrefixAfterBothPublishedTops() {
        var h=new H();h.edit(s->s.moveByte(ROUTINE,10).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,0)
                .moveByte(ANIMATION_FRAME,0x77).moveByte(ANIMATION_TIME,0x55));
        assertThrows(UnsupportedOperationException.class,()->h.run(1,0x80,0));assertEquals(2,h.calls.size());
        assertEquals(255,h.slot().unsignedByte(PREVIOUS_ANIMATION));assertEquals(0,h.slot().unsignedByte(ANIMATION_FRAME));
        assertEquals(255,h.slot().unsignedByte(ANIMATION_TIME));assertFalse(h.order.contains("draw"));
    }
    @Test void drawFailureRetainsMainStoresAndOriginalErrorWithoutWindowDeletion() {
        var h=new H();var original=new AssertionError("draw");h.drawFailure=original;
        assertSame(original,assertThrows(AssertionError.class,()->h.run(1,0x80,0)));
        assertEquals(0,h.slot().unsignedByte(MAP_FRAME));assertEquals(0x12,h.slot().id());assertEquals(2,h.calls.size());
        assertFalse(h.block.isCurrentDrawCall(h.runtime.pool(),h.slot().identity()));
    }
    private static final class H {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(layout(row(0x100,0x80,0x92,0,0xE0,0)));
        final List<CdFloatBlock.PrefixTopSolidCall> calls=new ArrayList<>();final List<String> order=new ArrayList<>();
        final CdFloatBlock block;CdSolidObject.Player player=new P().player();CdSolidObject.Globals globals=new CdSolidObject.Globals(0,4,0x12,0x34);
        Runnable before=()->{},after=()->{};Error drawFailure;
        H(){runtime.spawnObjects(0x80,1); // Constructed saved row provides a real nonzero saved flag.
            if(runtime.pool().state(WHOLE).id()==0)runtime.pool().write(runtime.pool().state(WHOLE).moveByte(ID,0x12).moveWord(X,0x100).moveWord(Y,0x80));
            var data=new CdFloatBlock.Assets(CdAssetBytes.copyOf(TILE_IDS),CdObjectVisualAssets.decode(MAPS,6,ANI,3,15),MAP);
            block=new CdFloatBlock(runtime,data,new Terrain().owner(),null,this::commit,(pool,id)->{
                assertTrue(blockCurrentDraw(pool,id));order.add("draw");if(drawFailure!=null)throw drawFailure;
            });
        }
        boolean blockCurrentDraw(CdObjectPool pool,CdObjectState.Identity id){return block.isCurrentDrawCall(pool,id);}
        CdObjectState slot(){return runtime.pool().state(WHOLE);}
        void edit(java.util.function.UnaryOperator<CdObjectState> change){runtime.pool().write(change.apply(slot()));}
        void onScreen(){edit(s->s.moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,12).moveByte(Y_RADIUS,12));}
        CdFloatBlock.Result run(int time,int x,int y){return block.run(WHOLE,new CdFloatBlock.Globals(time,x,y),new CdFloatBlock.SolidInputs(player,new CdSolidObject.Gates(0,0),globals));}
        CdSolidObject.Result result(int i){return ((CdSolidObject.Completed)calls.get(i).outcome()).completedResult();}
        void commit(CdFloatBlock.PrefixTopSolidCall c){
            int ordinal=calls.size();calls.add(c);assertTrue(block.isCurrentPrefixTopSolidCall(c,ordinal));
            assertEquals(player,c.player());assertEquals(globals,c.globals());assertEquals(runtime.pool().capture().slots(),c.callCut().slots());
            var outcome=c.outcome();CdObjectState own;List<CdObjectState> others;CdSolidObject.Player next;CdSolidObject.Globals g;
            if(outcome instanceof CdSolidObject.Completed done){var r=done.completedResult();own=r.object();others=r.otherSlots();next=r.player();g=r.globals();}
            else{var r=((CdSolidObject.NativeStopped)outcome).prefix();own=r.object();others=r.otherSlots();next=r.player();g=r.globals();}
            var images=new ArrayList<CdObjectState>();images.add(own);images.addAll(others);var output=CdObjectPool.overlay(c.callCut(),images);
            runtime.pool().validateRestore(output);order.add("prepare"+ordinal);before.run();
            runtime.pool().restore(output);order.add("pool"+ordinal);player=next;globals=g;order.add("primary"+ordinal);after.run();
        }
    }
    private static byte[] tileIds() {
        var out = ByteBuffer.allocate(74);
        for (int i = 0; i < 18; i++) { out.putShort((short) (36 + 2 * i)); }
        for (int i = 0; i < 19; i++) { out.putShort((short) (i == 5 ? TILE_WORD : 0x0101 * (i + 1))); }
        return out.array();
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    private static int[] row(int x, int y, int id, int subtype, int periods, int subtype2) {
        return new int[]{x, y, id, subtype, periods, subtype2};
    }

    private static CdObjectPlacements layout(int[]... records) {
        int nullAt = 12 + 8 * (records.length + 1);
        var bytes = ByteBuffer.allocate(nullAt + 6);
        bytes.putShort((short) 12).putShort((short) nullAt).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        for (int[] r : records) {
            bytes.putShort((short) r[0]).putShort((short) r[1]).put((byte) r[2]).put((byte) r[3])
                    .put((byte) r[4]).put((byte) r[5]);
        }
        bytes.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        bytes.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(bytes.array());
    }

    /** Every player-slot byte the Solid owner reads is explicit constructed ingress (TestCdSolidObject's values). */
    private static final class P {
        int x = 0x01008000, y = 0x00631234, xv = 0x0123, yv = 0x0400, gv = 0, routine = 2, angle = 0x10, flags = 0x02;
        int spr = 0x04, xr = 9, yr = 0x13, anim = 0, prev = 0, jump = 1, charge = 0, lock = 0, stick = 0, ctrl = 0;
        int pri = 0x56, sec = 0x78, tile = 0x0780, priority = 2, hurt = 0, rot = 0, id = 1, push = 0, stand = 0;

        P copy() {
            var c = new P();
            c.x = x; c.y = y; c.xv = xv; c.yv = yv; c.gv = gv; c.routine = routine; c.angle = angle; c.flags = flags;
            c.spr = spr; c.xr = xr; c.yr = yr; c.anim = anim; c.prev = prev; c.jump = jump; c.charge = charge;
            c.lock = lock; c.stick = stick; c.ctrl = ctrl; c.pri = pri; c.sec = sec; c.tile = tile;
            c.priority = priority; c.hurt = hurt; c.rot = rot; c.id = id; c.push = push; c.stand = stand;
            return c;
        }

        CdSolidObject.Player player() {
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x, y, xv, yv, gv,
                    routine, angle, flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl, pri, sec), tile,
                    priority, hurt, rot), id, push, stand);
        }
    }

    /**
     * Format-decoder terrain, not an authenticated asset (TestCdSolidObject's fixture): 42x8 FG cells all chunk 2,
     * open air everywhere, so every floor probe returns 31 - (feet Y & 15) with no angle write.
     */
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];

        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2;
            Arrays.fill(heights, 16, 32, (byte) 16);
            Arrays.fill(widths, 32, 48, (byte) 16);
            angles[2] = (byte) 0xC0;
        }

        CdSolidObject owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578];
            for (int i = 0; i < 60; i++) word(animations, i * 2, 120);
            animations[122] = (byte) 0xFF;
            byte[] mappings = new byte[398], dplcs = new byte[398];
            for (int i = 0; i < 198; i++) { word(mappings, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(mappings, dplcs, new byte[69472]);
            byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var assets = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[4], new byte[640], atan, visual);
            return new CdSolidObject(new CdPlayerMovement(terrain, assets));
        }
    }

    private static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }
}
