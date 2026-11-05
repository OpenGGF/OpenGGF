package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SOURCE ONLY; written while BOTH owned production mirrors were absent. Compilation/JUnit UNRUN.
 * Native 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Swing/Main.asm:20-197;
 * Object Functions.asm:137-196,228-237; Object Spawner.asm:329-340.
 * Dirty images, player cuts, sine values and terrain below are explicitly constructed format inputs.
 * Shared TOP7 is always the real selected CdSolidObject. No fabricated outcomes, private Snapshot
 * clones, fake Frame, natural13451, art/current/visible/pixel/parity or completed-route claim.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdSwing {
    private static final int WHOLE = 42, MAP = 0x20ED60;
    private static final byte[] MOTION = hex("c00000080200fe00c00000020100ff00c000fffeff000100a00000030180fe808000fffeff000100800000020100ff008000ffffff0000008000000101000000");
    private static final byte[] MAPS = hex("0006000c001801f8050000f802f8090008e8f8090808000001f8050004f8");
    private static CdR12aTerrainAssets retail;

    @Test void boundedSurfaceWidthsAndTenOverlappingAliases() throws Exception {
        assertEquals(1, CdSwing.class.getConstructors().length); assertEquals(6, CdSwing.class.getConstructors()[0].getParameterCount());
        assertEquals(8,Arrays.stream(CdSwing.class.getDeclaredMethods()).filter(m->m.getName().equals("fromAct2Assets")).findFirst().orElseThrow().getParameterCount());
        assertEquals(4, CdSwing.Assets.class.getRecordComponents().length); assertEquals(3, CdSwing.Globals.class.getRecordComponents().length);
        assertEquals(4, CdSwing.PlayerCut.class.getRecordComponents().length); assertEquals(3, CdSwing.PlayerReadCall.class.getRecordComponents().length);
        assertEquals(4, CdSwing.SolidCall.class.getRecordComponents().length); assertEquals(5, CdSwing.PlayerYCall.class.getRecordComponents().length);
        assertEquals(9, CdSwing.Publication.class.getRecordComponents().length); assertEquals(4, CdSwing.StoreKind.values().length);
        assertNotNull(CdSwing.class.getMethod("run", int.class, CdSwing.Globals.class));
        assertEquals(List.of(0x36,0x32,0x30,0x3A,0x3E,0x34,0x38), List.of(SWING_CENTRE_X.byteOffset(),SWING_CENTRE_Y.byteOffset(),SWING_ANGLE.byteOffset(),SWING_PHASE.byteOffset(),SWING_STEP.byteOffset(),SWING_MAX.byteOffset(),SWING_MIN.byteOffset()));
        assertEquals(List.of(0x3C,0x2F,0x3D), List.of(SWING_PART.byteOffset(),SWING_COUNT.byteOffset(),SWING_REVERSING.byteOffset()));
        assertThrows(IllegalArgumentException.class, () -> new CdSwing.Globals(65536,0,0));
        assertThrows(IllegalArgumentException.class, () -> new CdSwing.Globals(0,256,0));
        assertThrows(IllegalArgumentException.class, () -> new CdSwing.Globals(0,0,-1));
    }

    @Test void actualRetailSourceFactoryBorrowsCachedStaticViewsWithoutArtOrUnpairedPlacement() throws Exception {
        var owner = media().objects(); var before = new java.util.HashMap<>((java.util.Map<?, ?>) field(owner,"decoded"));
        var a = CdSwing.Assets.fromAct2Assets(owner); assertSame(owner.swing(),a.visual()); assertSame(owner.view(CdR12aProfile.Asset.SWING_MOTION),a.motion());
        assertSame(owner.view(CdR12aProfile.Asset.OBJECT_TILE_IDS),a.objectTileIds()); assertEquals(MAP,a.mappingAddressBits());
        assertArrayEquals(MOTION,a.motion().copy()); assertArrayEquals(MAPS,a.visual().mappings().copy()); assertTrue(a.visual().animations().isEmpty());
        assertEquals(before,field(owner,"decoded")); var h = new H(true); assertSame(a.visual(),h.body.assets().visual());
        assertThrows(IllegalArgumentException.class, () -> CdSwing.fromAct2Assets(new CdObjectPlacementRuntime(owner.placements()),owner,h.math,h.solid,h,h::draw,h,h));
        assertThrows(NullPointerException.class, () -> CdSwing.Assets.fromAct2Assets(null));
    }

    @Test void dirtyInitParentAndFourChildrenRetainEveryUnwrittenByte() throws Exception {
        var h = new H(); var original = dirty(h.state()).moveByte(ID,0x29).moveByte(ROUTINE,0).moveByte(SUBTYPE,4).moveByte(SUBTYPE2,0).moveByte(SWING_PART,0)
                .moveByte(SPRITE_FLAGS,0xA1).moveWord(X,0x1AA0).moveWord(Y,0x188).moveWord(SWING_ANGLE,0).moveByte(SWING_REVERSING,0);
        h.put(original); h.globals=new CdSwing.Globals(0x1AA0,1,0); var old = new ArrayList<CdObjectState>(); for(int i=32;i<36;i++){var d=dirty(h.pool().state(i)).moveByte(ID,0);h.pool().write(d);old.add(d);}
        var cut = h.placement.capture(); byte[] e = original.copyBytes(); e[0x24]=2;e[1]=(byte)0x25;e[0x18]=3;ByteBuffer.wrap(e).putInt(4,MAP);word(e,2,0x418);e[0x19]=0x18;e[0x16]=8;
        word(e,0x36,0x1AA0);word(e,0x32,0x188);e[0x2F]=4;word(e,0x3A,0xC008);word(e,0x3E,8);word(e,0x34,0x200);word(e,0x38,0xFE00);word(e,0x30,8);e[0x3D]=0;
        var result = h.run(); assertArrayEquals(e,result.state().copyBytes());
        for(int i=0;i<4;i++){byte[] ce=old.get(i).copyBytes();ce[0]=0x29;word(ce,0x36,0x1AA0);word(ce,0x32,0x188);ce[0x28]=4;ce[0x2F]=4;ce[0x3C]=(byte)(4-i);ce[0x1A]=(byte)(i==0?1:2);assertArrayEquals(ce,h.pool().state(32+i).copyBytes());}
        assertTrue(h.reads.isEmpty()); assertEquals(1,h.draws.size()); unchanged(cut,h,List.of(WHOLE,32,33,34,35));
    }

    @Test void zeroLowNibbleMakes256AttemptsRatherThanNoChildrenOrEarlyCapacityReturn() throws Exception {
        var h = new H(); h.put(h.state().moveByte(SUBTYPE,0)); var result=h.run();
        var allocations=result.effects().stream().filter(CdSwing.AllocationAttempt.class::isInstance).map(CdSwing.AllocationAttempt.class::cast).toList();
        assertEquals(256,allocations.size()); assertEquals(0,allocations.getFirst().part()); assertEquals(255,allocations.get(1).part());assertEquals(1,allocations.getLast().part());
        assertEquals(95,allocations.stream().filter(a->a.wholeIndex()>=32).count());assertEquals(161,allocations.stream().filter(a->a.wholeIndex()==-1).count());
        assertEquals(1,h.pool().state(32).unsignedByte(MAP_FRAME));assertEquals(255,h.pool().state(33).unsignedByte(SWING_PART)); assertFalse(result.deleted());
    }

    @Test void allocationTestsOnlyIdAndContinuesAllAttemptsAgainstAFullDirtyPool() throws Exception {
        var h=new H();for(int i=32;i<128;i++)if(i!=WHOLE)h.pool().write(dirty(h.pool().state(i)).moveByte(ID,0x24));h.put(h.state().moveByte(SUBTYPE,4));
        var cut=h.placement.capture();var r=h.run();assertEquals(4,r.effects().stream().filter(CdSwing.AllocationAttempt.class::isInstance).count());
        assertTrue(r.effects().stream().filter(CdSwing.AllocationAttempt.class::isInstance).map(CdSwing.AllocationAttempt.class::cast).allMatch(a->a.wholeIndex()==-1));
        assertFalse(r.deleted());unchanged(cut,h,List.of(WHOLE));
    }

    @Test void nonzeroPartSkipsCentresCountAndAllocationThenReadsItsRawMotionRow() throws Exception {
        var h=new H();h.put(h.state().moveByte(SUBTYPE,0x71).moveByte(SWING_PART,1).moveByte(SWING_COUNT,2).moveWord(SWING_CENTRE_X,0x100).moveWord(SWING_CENTRE_Y,0x200));
        var r=h.run();assertEquals(0x8001,r.state().word(SWING_PHASE));assertEquals(1,r.state().word(SWING_STEP));assertEquals(0x100,r.state().word(SWING_MAX));assertEquals(0,r.state().word(SWING_MIN));
        assertEquals(0x100,r.state().word(SWING_CENTRE_X));assertEquals(0x200,r.state().word(SWING_CENTRE_Y));assertEquals(2,r.state().unsignedByte(SWING_COUNT));
        assertTrue(r.effects().stream().noneMatch(CdSwing.AllocationAttempt.class::isInstance));assertTrue(h.reads.isEmpty());
    }

    @Test void allEightMotionRowsStoreLiteralNativeWordsInOrder() throws Exception {
        int[][] rows={{0xC000,8,0x200,0xFE00},{0xC000,2,0x100,0xFF00},{0xC000,0xFFFE,0xFF00,0x100},{0xA000,3,0x180,0xFE80},{0x8000,0xFFFE,0xFF00,0x100},{0x8000,2,0x100,0xFF00},{0x8000,0xFFFF,0xFF00,0},{0x8000,1,0x100,0}};
        for(int i=0;i<8;i++){var h=new H();h.put(h.state().moveByte(SUBTYPE,i*16).moveByte(SWING_PART,1).moveByte(SWING_COUNT,2));var r=h.run();
            var reads=r.effects().stream().filter(CdSwing.MotionWordRead.class::isInstance).map(CdSwing.MotionWordRead.class::cast).toList();assertEquals(4,reads.size());
            for(int j=0;j<4;j++){assertEquals(i*8+j*2,reads.get(j).offset());assertEquals(rows[i][j],reads.get(j).value());}
        }
    }

    @Test void malformedMotionStopsOnlyAtTheReachedWordAfterAllPriorStoresAndAllocations() throws Exception {
        for(int length:new int[]{0,2,4,6}){var h=new H(assets(tiles(),Arrays.copyOf(MOTION,length)));h.put(h.state().moveByte(SUBTYPE,2));
            var x=assertThrows(CdSwing.UnsupportedSwing.class,h::run);assertEquals(CdSwing.UnsupportedBoundary.MOTION_WORD_READ,x.boundary());assertEquals(length,x.value());
            assertEquals(2,h.state().routine());assertEquals(0x418,h.state().word(TILE));assertEquals(8,h.state().unsignedByte(Y_RADIUS));assertEquals(2,h.state().unsignedByte(SWING_COUNT));
            assertEquals(0x29,h.pool().state(32).id());assertEquals(0x29,h.pool().state(33).id());assertTrue(h.draws.isEmpty());
            if(length>=2)assertEquals(0xC000,h.state().word(SWING_PHASE));if(length>=4)assertEquals(8,h.state().word(SWING_STEP));if(length>=6)assertEquals(0x200,h.state().word(SWING_MAX));
        }
    }

    @Test void undeclaredHighNibbleStopsAfterItsRealAllocationPrefixWithoutRowFitting() throws Exception {
        var h=new H();h.put(h.state().moveByte(SUBTYPE,0x84));var x=assertThrows(CdSwing.UnsupportedSwing.class,h::run);assertEquals(64,x.value());
        assertEquals(4,h.state().unsignedByte(SWING_COUNT));for(int i=32;i<36;i++)assertEquals(0x29,h.pool().state(i).id());assertTrue(h.draws.isEmpty());
    }

    @Test void malformedOrSubtypeTileReadPreservesHeaderBeforeSizeAndChildren() throws Exception {
        for(var a:List.of(assets(new byte[0],MOTION),assets(tiles(),MOTION))){var h=new H(a);h.put(h.state().moveByte(SUBTYPE2,1));
            var x=assertThrows(CdSwing.UnsupportedSwing.class,h::run);assertEquals(CdSwing.UnsupportedBoundary.TILE_ID_SUBTYPE2,x.boundary());
            assertEquals(2,h.state().routine());assertEquals(4,h.state().spriteFlags());assertEquals(3,h.state().unsignedByte(PRIORITY));assertEquals(MAP,h.state().longBits(MAPPINGS));
            assertEquals(0,h.state().unsignedByte(WIDTH));assertEquals(0,h.pool().state(32).id());assertTrue(h.draws.isEmpty());
        }
    }

    @Test void motionUsesPhaseHighByteUnsignedProductsSignedPartAndTwoDifferentArithmeticShifts() throws Exception {
        // Constructed format data stays inside the native sine WORD bound and repeats its 64-word extension.
        // Literal outputs independently expand native MULU32/ASR.L4/ASR.W4, including high-bit part bytes.
        int[][] vectors={{1,65518,65524},{4,65470,65482},{0x7F,63509,63783},{0x80,63734,63978},{0xFF,269,783}};
        for(int[] vector:vectors){int part=vector[0];byte[] sine=new byte[640];word(sine,0x12*2,0xFF01);word(sine,(0x12+64)*2,0xFF23);word(sine,0x34*2,0x11);word(sine,(0x34+64)*2,0x22);System.arraycopy(sine,0,sine,512,128);var h=new H(assets(tiles(),MOTION),sine);h.globals=new CdSwing.Globals(0xFFFE,1,0);
            h.main(0x1234,0,0,part,0);h.put(h.state().moveWord(SWING_CENTRE_X,0xFFFE).moveWord(SWING_CENTRE_Y,2).moveWord(X_FRACTION,0xABCD).moveWord(Y_FRACTION,0x7654));
            var r=h.run();
            assertEquals(vector[1],r.state().centreX());assertEquals(vector[2],r.state().centreY());
            assertEquals(0xABCD,r.state().word(X_FRACTION));assertEquals(0x7654,r.state().word(Y_FRACTION));
        }
    }

    @Test void reversalTestsExactWordEqualityAndRetainsOtherNonzeroReverseBytes() throws Exception {
        var h=new H();h.main(0xFFFE,0x1FF,1,1,0);h.put(h.state().moveWord(SWING_MAX,0x200));var r=h.run();assertEquals(0x200,r.state().word(SWING_ANGLE));assertEquals(0x1FE,r.state().word(SWING_PHASE));assertEquals(0xFF,r.state().unsignedByte(SWING_REVERSING));
        h.main(0,0x201,2,1,0);h.put(h.state().moveWord(SWING_MAX,0x200));assertEquals(0,h.run().state().unsignedByte(SWING_REVERSING));
        h.main(0,0xFF01,1,1,0x7E);h.put(h.state().moveWord(SWING_MIN,0xFF00));assertEquals(0,h.run().state().unsignedByte(SWING_REVERSING));
        h.main(0,0xFF02,1,1,0x7E);h.put(h.state().moveWord(SWING_MIN,0xFF00));assertEquals(0x7E,h.run().state().unsignedByte(SWING_REVERSING));
    }

    @Test void intermediateLinkKeepsVelocityAndNeverReadsAnyPlayer() throws Exception {
        var h=new H();h.main(0,0,0,1,0);h.put(h.state().moveWord(X_VELOCITY,0xABCD).moveWord(Y_VELOCITY,0x7654));var r=h.run();
        assertEquals(0xABCD,r.state().word(X_VELOCITY));assertEquals(0x7654,r.state().word(Y_VELOCITY));assertTrue(h.reads.isEmpty());assertTrue(h.solids.isEmpty());assertEquals(1,h.draws.size());
    }

    @Test void endLinkComputesVelocityFromWrappedFullLongDifferencesBeforeBothActualPlayers() throws Exception {
        var h=new H();h.main(0,0,0,2,0);h.put(h.state().moveByte(SWING_COUNT,2).moveLong(X_POSITION,0x8000ABCD).moveLong(Y_POSITION,0xFFFF7654).moveWord(SWING_CENTRE_X,0x7FFF).moveWord(SWING_CENTRE_Y,1));
        h.globals=new CdSwing.Globals(0x7FFF,1,0);var r=h.run();assertEquals(((0x7FFFABCD-0x8000ABCD)>>8)&65535,r.state().word(X_VELOCITY));assertEquals(((0x00017654-0xFFFF7654)>>8)&65535,r.state().word(Y_VELOCITY));
        assertEquals(List.of(CdSolidObject.PlayerSlot.PRIMARY,CdSolidObject.PlayerSlot.PRIMARY,CdSolidObject.PlayerSlot.SECONDARY,CdSolidObject.PlayerSlot.SECONDARY),h.solids.stream().map(c->c.expectedPlayerCut().selection().selected()).toList());
        assertEquals(List.of(8,12,8,12),h.solids.stream().map(c->c.expectedSource().unsignedByte(Y_RADIUS)).toList());assertEquals(8,r.state().unsignedByte(Y_RADIUS));
    }

    @Test void headerZeroSecondaryIsStillActuallyReadAndRunsRealTopTwice() throws Exception {
        var h=new H();h.main(0,0,0,0,0);h.put(h.state().moveByte(SWING_COUNT,0));h.players[1]=player(0,0,0,0,0,0);h.run();
        var secondary=h.solids.stream().filter(c->c.expectedPlayerCut().selection().selected()==CdSolidObject.PlayerSlot.SECONDARY).toList();assertEquals(2,secondary.size());
        assertTrue(secondary.stream().allMatch(c->c.expectedPlayerCut().player().slotId()==0));assertTrue(secondary.stream().allMatch(c->c.genuineSelectedOutcome().outcome() instanceof CdSolidObject.Completed));
    }

    @Test void realCollisionCarriesSelectedPlayerAndDirectYCommitPreservesItsFraction() throws Exception {
        var h=new H();h.main(0,0,0,0,0);h.put(h.state().moveByte(SWING_COUNT,0).moveWord(SWING_CENTRE_X,0x100).moveWord(SWING_CENTRE_Y,0x80).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,8));
        h.players[0]=player(0x01008000,0x00651234,1,8,WHOLE,19);var r=h.run();assertTrue(h.solids.size()>=2);assertEquals(1,h.ys.size());assertEquals(0x00671234,h.players[0].actor().motion().centreY16_16());
        assertEquals(0x1234,h.players[0].actor().motion().centreY16_16()&65535);assertEquals(8,r.state().unsignedByte(Y_RADIUS));
        for(var call:h.ys){assertEquals(call.expectedSource().centreY()-8-(byte)call.expectedPlayerCut().player().actor().motion().yRadius()+2, (short)call.value());}
    }

    @Test void sharedNativeStopIsAdoptedOnceThenRethrownByIdentityBeforeSecondPlayerOrDraw() throws Exception {
        var h=new H();h.main(0,0,0,0,0);h.put(h.state().moveByte(SWING_COUNT,0).moveWord(SWING_CENTRE_X,0x100).moveWord(SWING_CENTRE_Y,0x80).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,8));
        h.players[0]=player(0x01008000,0x00651234,1,8,5,19);
        var thrown=assertThrows(RuntimeException.class,h::run);assertEquals(1,h.solids.size());var call=h.solids.getFirst();var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,call.genuineSelectedOutcome().outcome());
        assertSame(stopped.stop().originalException(),thrown);assertEquals(stopped.prefix().player(),h.players[0]);assertEquals(stopped.prefix().object(),h.state());assertTrue(h.draws.isEmpty());assertTrue(h.ys.isEmpty());
    }

    @Test void playerReadIsFreshReadonlyAndOriginalThrowRetainsItsVerificationFault() throws Exception {
        var h=new H();h.main(0,0,0,0,0);h.put(h.state().moveByte(SWING_COUNT,0));var original=new IllegalStateException("actual player read");
        h.onRead=call->{h.pool().write(h.pool().state(127).moveByte(ID,7));throw original;};assertSame(original,assertThrows(IllegalStateException.class,h::run));
        assertEquals(1,original.getSuppressed().length);assertEquals(7,h.pool().state(127).id());assertTrue(h.solids.isEmpty());assertTrue(h.draws.isEmpty());
    }

    @Test void wrongSelectedSlotFromPlayerPortFailsBeforeSharedCall() throws Exception {
        var h=new H();h.main(0,0,0,0,0);h.put(h.state().moveByte(SWING_COUNT,0));h.wrongSelection=true;
        assertThrows(IllegalArgumentException.class,h::run);assertTrue(h.solids.isEmpty());assertEquals(8,h.state().unsignedByte(Y_RADIUS));
    }

    @Test void solidPortAllowsOnlyBeforeOrTheCompletePrebuiltGenuineOverlayOnThrow() throws Exception {
        for(boolean completed:new boolean[]{false,true}){var h=new H();h.main(0,0,0,0,0);h.put(h.state().moveByte(SWING_COUNT,0));var original=new IllegalArgumentException("commit");
            h.afterSolid=call->{throw original;};h.skipSolid=!completed;assertSame(original,assertThrows(IllegalArgumentException.class,h::run));assertEquals(0,original.getSuppressed().length);assertTrue(h.draws.isEmpty());
        }
        var h=new H();h.main(0,0,0,0,0);h.put(h.state().moveByte(SWING_COUNT,0));var original=new IllegalArgumentException("dirty commit");h.afterSolid=call->{h.pool().write(h.pool().state(127).moveByte(ID,9));throw original;};
        assertSame(original,assertThrows(IllegalArgumentException.class,h::run));assertEquals(1,original.getSuppressed().length);assertEquals(9,h.pool().state(127).id());
    }

    @Test void drawReloadAllowsOnlyTheRealSourceBit7ClearAndRejectsOtherPoolWrites() throws Exception {
        var h=new H();h.main(0,0,0,1,0);h.put(h.state().moveByte(SPRITE_FLAGS,0x84));var before=h.placement.capture();h.run();assertEquals(4,h.state().spriteFlags());unchanged(before,h,List.of(WHOLE));
        var bad=new H();bad.onDraw=call->bad.pool().write(bad.pool().state(126).moveByte(ID,3));assertThrows(IllegalStateException.class,bad::run);assertEquals(3,bad.pool().state(126).id());
    }

    @Test void drawThrowPreservesIdenticalErrorAfterItsGenuineCompletedBitClear() throws Exception {
        var h=new H();var original=new IllegalStateException("Draw stopped");h.onDraw=call->{throw original;};
        assertSame(original,assertThrows(IllegalStateException.class,h::run));assertEquals(4,h.state().spriteFlags());assertEquals(0,original.getSuppressed().length);
    }

    @Test void centeredDespawnUsesOriginalCentreRatherThanLiveMotionXAndClearsOnlyLoadedByte() throws Exception {
        var h=new H();h.main(0,0,0,1,0);h.put(h.state().moveWord(SWING_CENTRE_X,0x100).moveWord(X,0x7000).moveByte(SAVED_FLAGS_ID,2));h.globals=new CdSwing.Globals(0x100,1,0);h.placement.bsetSavedFlag(2,1,CdObjectPlacementRuntime.SavedBit.LOADED);h.placement.bsetSavedFlag(2,1,CdObjectPlacementRuntime.SavedBit.MEMBER_3);
        assertFalse(h.run().deleted());h.put(h.state().moveWord(SWING_CENTRE_X,0x800));var before=h.placement.capture();var r=h.run();assertTrue(r.deleted());assertArrayEquals(new byte[64],r.state().copyBytes());
        byte[] expected=before.savedFlags().copy();expected[9]&=0x7F;assertArrayEquals(expected,h.placement.capture().savedFlags().copy());assertEquals(before.status(),h.placement.status());
    }

    @Test void warpPeriodUsesSignedDirectionClampAndEachDirtyChildHasIndependentSavedLifetime() throws Exception {
        int[][] vectors={{0x80,1,0},{0x81,0xFF,2},{0x82,0xFE,2},{0x83,1,2}};
        for(int[] v:vectors){var h=new H();h.put(h.state().moveByte(SAVED_FLAGS_ID,3));h.globals=new CdSwing.Globals(0x100,v[0],v[1]);for(var bit:CdObjectPlacementRuntime.SavedBit.values())h.placement.bsetSavedFlag(3,v[2],bit);var r=h.run();assertTrue(r.deleted());assertEquals(0x7F,h.placement.savedFlag(3,v[2]));
            var changed=r.effects().stream().filter(CdSwing.SavedFlagChanged.class::isInstance).map(CdSwing.SavedFlagChanged.class::cast).toList();assertEquals(1,changed.size());assertEquals(11+v[2],changed.getFirst().index());
            assertEquals(0x29,h.pool().state(32).id());assertEquals(0,h.pool().state(32).savedFlagsId());
        }
    }

    @Test void reachedSavedAddressFailureKeepsReturnedDrawAndDoesNotDeleteOrClearAnotherByte() throws Exception {
        var h=new H();h.put(h.state().moveByte(SAVED_FLAGS_ID,255));h.globals=new CdSwing.Globals(0x800,127,0);var before=h.placement.capture();
        var x=assertThrows(CdSwing.UnsupportedSwing.class,h::run);assertEquals(CdSwing.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,x.boundary());assertEquals(894,x.value());assertEquals(0x29,h.state().id());assertEquals(1,h.draws.size());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
    }

    @Test void rawRoutineAndForeignSourceRejectBeforeAnyCallbackOrNormalization() throws Exception {
        for(int routine:new int[]{1,3,4,255}){var h=new H();h.put(h.state().moveByte(ROUTINE,routine));var before=h.placement.capture();var x=assertThrows(CdSwing.UnsupportedSwing.class,h::run);
            assertEquals(CdSwing.UnsupportedBoundary.ROUTINE_DISPATCH,x.boundary());assertEquals(routine,x.value());unchanged(before,h,List.of());assertTrue(h.draws.isEmpty());}
        var h=new H();h.put(h.state().moveByte(ID,0));assertThrows(IllegalArgumentException.class,h::run);assertTrue(h.draws.isEmpty());
    }

    @Test void sourcePairedOriginalReceiptsHaveOneCounterRejectClonesAndRetireAfterReturn() throws Exception {
        var h=new H(true);h.onPrepared=call->{if(call instanceof CdSwing.Publication p){var clone=new CdSwing.Publication(p.ordinal(),p.kind(),p.actualPoolCut(),p.expectedSource(),p.before(),p.output(),p.savedByteIndex(),p.previousValue(),p.value());assertEquals(p,clone);assertFalse(h.body.isCurrentCall(clone));assertThrows(IllegalStateException.class,()->h.body.currentCallOrdinal(clone));}};h.run();assertFalse(h.completed.isEmpty());assertEquals(h.completed.size(),h.ordinal);var original=h.completed.getFirst();assertFalse(h.body.isCurrentCall(original));
        assertThrows(IllegalStateException.class,()->h.body.currentCallOrdinal(original));var p=h.publications.getFirst();var clone=new CdSwing.Publication(p.ordinal(),p.kind(),p.actualPoolCut(),p.expectedSource(),p.before(),p.output(),p.savedByteIndex(),p.previousValue(),p.value());
        assertEquals(p,clone);assertNotSame(p,clone);assertFalse(h.body.isCurrentCall(clone));assertThrows(IllegalStateException.class,()->h.body.currentCallOrdinal(clone));
        assertEquals(List.of(CdSwing.StoreKind.SOURCE_STORE,CdSwing.StoreKind.SOURCE_STORE,CdSwing.StoreKind.SOURCE_STORE,CdSwing.StoreKind.SOURCE_STORE,CdSwing.StoreKind.CHILD_STORE,CdSwing.StoreKind.CHILD_STORE),h.publications.subList(0,6).stream().map(CdSwing.Publication::kind).toList());
    }

    @Test void reentryAndReadonlyBeforeAfterObserversCannotCrossActualStoreBoundary() throws Exception {
        var h=new H(true);var original=new IllegalStateException("before receipt");h.onPrepared=call->{assertThrows(IllegalStateException.class,h::run);throw original;};var before=h.placement.capture();
        assertSame(original,assertThrows(IllegalStateException.class,h::run));unchanged(before,h,List.of());assertFalse(h.body.isCurrentCall(h.pending));
        var after=new H(true);after.afterPublication=call->{after.pool().write(after.pool().state(127).moveByte(ID,5));};assertThrows(IllegalStateException.class,after::run);
        assertEquals(2,after.state().routine());assertEquals(5,after.pool().state(127).id());assertTrue(after.completed.isEmpty());
    }

    @Test void resultEffectsAreImmutableCompletedScalarsWithoutOperationsOrPlacementCuts() throws Exception {
        var h=new H();var r=h.run();assertThrows(UnsupportedOperationException.class,()->r.effects().clear());
        for(var e:r.effects())for(var component:e.getClass().getRecordComponents()){
            assertFalse(component.getType()==CdObjectPlacementRuntime.Snapshot.class);assertFalse(component.getType()==CdSwing.PlayerReadCall.class);assertFalse(component.getType()==CdSwing.SolidCall.class);assertFalse(component.getType()==CdSwing.PlayerYCall.class);
            assertFalse(component.getType()==CdSolidObject.SelectedOutcome.class);assertFalse(component.getType()==CdObjectState.class);
        }
    }

    private static final class H implements CdSwing.PlayerPort,CdSwing.DispatchObserver,CdSwing.ReceiptGuard {
        final CdObjectPlacementRuntime placement; final CdPlayerMath math; final CdSolidObject solid; final CdSwing body;
        final byte[] sine; final CdSolidObject.Player[] players={player(0,0,1,0,0,19),player(0,0,0,0,0,0)};
        CdSolidObject.Globals sharedGlobals=new CdSolidObject.Globals(0,4,0x12,0x34); CdSwing.Globals globals=new CdSwing.Globals(0x100,1,0);
        final List<CdSwing.PlayerReadCall> reads=new ArrayList<>(); final List<CdSwing.SolidCall> solids=new ArrayList<>();final List<CdSwing.PlayerYCall> ys=new ArrayList<>();final List<CdSwing.DrawCall> draws=new ArrayList<>();
        final List<CdSwing.Publication> publications=new ArrayList<>();final List<Object> completed=new ArrayList<>();final IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();Object pending;long ordinal;
        Consumer<CdSwing.PlayerReadCall> onRead=c->{};Consumer<CdSwing.SolidCall> afterSolid=c->{};Consumer<CdSwing.DrawCall> onDraw=c->{};
        Consumer<CdSwing.Publication> afterPublication=c->{};Consumer<Object> onPrepared=c->{};boolean skipSolid,wrongSelection;
        H() throws Exception {this(assets(tiles(),MOTION));}
        H(CdSwing.Assets assets) {this(assets,new byte[640]);}
        H(CdSwing.Assets assets,byte[] sineInput) {sine=sineInput.clone();placement=new CdObjectPlacementRuntime(layout());math=new CdPlayerMath(playerData(sine));solid=new CdSolidObject(new CdPlayerMovement(terrain(),playerData(sine)));body=new CdSwing(placement,assets,math,solid,this,this::draw);seed();}
        H(boolean paired) throws Exception {sine=new byte[640];var terrain=media();var owner=terrain.objects();placement=CdObjectPlacementRuntime.fromAssets(terrain,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));math=new CdPlayerMath(playerData(sine));solid=new CdSolidObject(new CdPlayerMovement(terrain(),playerData(sine)));body=CdSwing.fromAct2Assets(placement,owner,math,solid,this,this::draw,this,this);seed();}
        void seed(){put(state().moveByte(ID,0x29).moveByte(SUBTYPE,2).moveWord(X,0x100).moveWord(Y,0x80));}
        CdObjectPool pool(){return placement.pool();}CdObjectState state(){return pool().state(WHOLE);}void put(CdObjectState s){pool().write(s);}CdSwing.Result run(){return body.run(WHOLE,globals);}
        void main(int phase,int angle,int step,int part,int reverse){put(state().moveByte(ROUTINE,2).moveByte(SWING_PART,part).moveByte(SWING_COUNT,(part+1)&255).moveWord(SWING_PHASE,phase).moveWord(SWING_ANGLE,angle).moveWord(SWING_STEP,step).moveByte(SWING_REVERSING,reverse).moveWord(SWING_CENTRE_X,0x100).moveWord(SWING_CENTRE_Y,0x80));}
        public CdSwing.PlayerCut read(CdSwing.PlayerReadCall call){assertTrue(body.isCurrentCall(call));assertTrue(body.currentCallOrdinal(call)>0);reads.add(call);onRead.accept(call);int i=call.slot().wholeIndex();var selected=wrongSelection?CdSolidObject.PlayerSlot.SECONDARY:call.slot();return new CdSwing.PlayerCut(players[i],new CdSolidObject.Gates(0,0),sharedGlobals,new CdSolidObject.PlayerSelection(selected,players[1-i].actor().motion().flags()));}
        public void commitSolid(CdSwing.SolidCall call){assertTrue(body.isCurrentCall(call));assertTrue(body.currentCallOrdinal(call)>0);solids.add(call);if(!skipSolid){var o=call.genuineSelectedOutcome().outcome();CdSolidObject.Player player;CdObjectState source;List<CdObjectState> others;
                if(o instanceof CdSolidObject.Completed d){var r=d.completedResult();player=r.player();source=r.object();others=r.otherSlots();sharedGlobals=r.globals();}else{var p=((CdSolidObject.NativeStopped)o).prefix();player=p.player();source=p.object();others=p.otherSlots();sharedGlobals=p.globals();}
                pool().write(source);for(var s:others)pool().write(s);int i=call.expectedPlayerCut().selection().selected().wholeIndex();players[i]=player;var counterpart=call.genuineSelectedOutcome().counterpart();if(counterpart.stored())players[1-i]=flags(players[1-i],counterpart.value());}
            afterSolid.accept(call);}
        public void commitY(CdSwing.PlayerYCall call){assertTrue(body.isCurrentCall(call));assertTrue(body.currentCallOrdinal(call)>0);ys.add(call);int i=call.expectedPlayerCut().selection().selected().wholeIndex();var p=players[i];var m=p.actor().motion();players[i]=motion(p,new CdPlayerMotionState(m.centreX16_16(),(call.value()<<16)|(m.centreY16_16()&65535),m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle()));}
        void draw(CdSwing.DrawCall call){assertTrue(body.isCurrentCall(call));assertTrue(body.currentCallOrdinal(call)>0);draws.add(call);var source=pool().state(call.expectedSource().identity().wholeIndex());pool().write(source.moveByte(SPRITE_FLAGS,source.spriteFlags()&127));onDraw.accept(call);}
        public void beforePublication(CdSwing.Publication call){publications.add(call);}public void afterPublication(CdSwing.Publication call){afterPublication.accept(call);}public void beforeCall(Object call){}public void afterCall(Object call){}
        public void prepared(Object call){assertTrue(body.isCurrentCall(call));assertNull(pending);assertFalse(seen.containsKey(call));assertEquals(++ordinal,body.currentCallOrdinal(call));pending=call;onPrepared.accept(call);}
        public void completed(Object call){assertSame(pending,call);assertTrue(body.isCurrentCall(call));assertFalse(seen.containsKey(call));pending=null;seen.put(call,true);completed.add(call);}
    }
    private static CdSwing.Assets assets(byte[] tiles,byte[] motion){return new CdSwing.Assets(CdAssetBytes.copyOf(tiles),CdAssetBytes.copyOf(motion),CdObjectVisualAssets.decodeStatic(MAPS,3,14),MAP);}
    private static byte[] tiles(){byte[] b=new byte[74];for(int i=0;i<18;i++)word(b,i*2,36);word(b,34,72);word(b,72,0x418);return b;}
    private static int nativeProduct(int value,int distance){long unsignedProduct=(long)(value&65535)*(distance&65535);int shifted=(int)unsignedProduct>>4;return (short)((short)shifted>>4);}
    private static CdSolidObject.Player player(int x,int y,int id,int flags,int stand,int radius){var m=new CdPlayerMotionState(x,y,0,0,0,2,0,flags,4,9,radius,0,0,0,0,0,0,0,0,0);return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(m,0x780,2,0,0),id,0,stand);}
    private static CdSolidObject.Player motion(CdSolidObject.Player p,CdPlayerMotionState m){var a=p.actor();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(m,a.tile(),a.priority(),a.hurt(),a.rotationDistance()),p.slotId(),p.pushObject(),p.standObject());}
    private static CdSolidObject.Player flags(CdSolidObject.Player p,int value){var m=p.actor().motion();return motion(p,new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),value,m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle()));}
    private static CdR11aTerrain terrain(){byte[] fg=new byte[338],bg=new byte[50];fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)2);return CdR11aTerrain.decode(new byte[6432],new byte[25600],new byte[61952],fg,bg,new byte[900],new byte[256],new byte[4096],new byte[4096]);}
    private static CdR11aPlayerAssets playerData(byte[] sine){byte[] anim=new byte[578],maps=new byte[398],dplcs=new byte[398],atan=new byte[257];for(int i=0;i<60;i++)word(anim,2*i,120);anim[122]=(byte)255;for(int i=0;i<198;i++){word(maps,2*i,396);word(dplcs,2*i,396);}for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);return CdR11aPlayerAssets.decode(anim,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)255},new byte[4],sine,atan,CdSonicVisualAssets.decode(maps,dplcs,new byte[69472]));}
    private static CdObjectPlacements layout(){var b=ByteBuffer.allocate(26);b.putShort((short)12).putShort((short)20).putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0xFFFF).putInt(0);return CdObjectPlacements.decodeLayout(b.array());}
    private static CdObjectState dirty(CdObjectState s)throws Exception{byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(0x31+i*17);var c=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);c.setAccessible(true);return c.newInstance(field(s,"owner"),s.identity(),b);}
    private static void unchanged(CdObjectPlacementRuntime.Snapshot before,H h,List<Integer> except){assertEquals(before.status(),h.placement.status());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());for(var s:before.pool().slots())if(!except.contains(s.identity().wholeIndex()))assertEquals(s,h.pool().state(s.identity().wholeIndex()));}
    private static CdR12aTerrainAssets media()throws Exception{if(retail!=null)return retail;String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retail=CdR12aTerrainAssets.fromPrograms(p);}return retail;}
    private static Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static void word(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
    private static byte[] hex(String value){return HexFormat.of().parseHex(value);}
}
