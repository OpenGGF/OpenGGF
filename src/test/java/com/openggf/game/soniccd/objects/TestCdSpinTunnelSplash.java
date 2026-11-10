package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import com.openggf.game.soniccd.objects.CdSpinTunnelSplash.Kind;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SOURCE authored before the component; Java/discovery/media execution remains UNRUN.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Spin Tunnel/Main (Waterfall Splash).asm:7-51, Data/Animations (Waterfall Splash).asm:1-3.
 * Constructed storage/format controls supply no original-media or World authority.
 */
class TestCdSpinTunnelSplash {
    private static final int WHOLE = 41, MAP = 0x23F05C;
    private static final byte[] ANI = bytes(0,2,3,0,1,2,0xFC,0);

    @Test void presentInitRetainsFullDirtyImageAndOrderedPrimitiveStores() {
        var h = new Harness(); var before = h.slot(); var cut = h.runtime.capture();
        var out = h.body.run(WHOLE);
        var expected = before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,before.spriteFlags()|4)
                .moveLong(MAPPINGS,MAP).moveWord(TILE,0x3E4).moveByte(PRIORITY,1).addByte(ANIMATION_TIME,-1)
                .moveByte(SPRITE_FLAGS,(before.spriteFlags()|4)&0x7F);
        assertEquals(expected,out); assertEquals(List.of(Kind.DISPATCH,Kind.INIT_ROUTINE,Kind.INIT_FLAGS,Kind.INIT_MAPPING,Kind.INIT_TILE,
                Kind.TIME_ZONE,Kind.INIT_PRIORITY,Kind.ANIMATION,Kind.ANIMATION,Kind.ANIMATION,Kind.DRAW_OBJECT),h.kinds());
        assertEquals(1,h.periodReads); assertEquals(1,h.drawn.size()); assertOther95(cut,h);
        assertEquals(before.unsignedByte(SUBTYPE),out.unsignedByte(SUBTYPE));
        assertEquals(before.unsignedByte(SUBTYPE2),out.unsignedByte(SUBTYPE2));
        assertEquals(before.word(X_FRACTION),out.word(X_FRACTION));
        assertEquals(before.word(Y_FRACTION),out.word(Y_FRACTION));
        assertEquals(before.unsignedByte(ROUTINE_2),out.unsignedByte(SOLID_TYPE));
    }

    @Test void pastInitWritesDefaultTileBeforeReadAndSeparatePastTileBeforePriority() {
        var h=new Harness();h.period=0;h.before=op->{if(op.kind()==Kind.TIME_ZONE){
            assertEquals(0x3E4,h.slot().word(TILE));assertEquals(2,h.slot().routine());
            assertEquals(MAP,h.slot().longBits(MAPPINGS));assertEquals(0xA5,h.slot().unsignedByte(PRIORITY));
        }};
        h.body.run(WHOLE);assertEquals(0x39E,h.slot().word(TILE));
        assertEquals(List.of(Kind.DISPATCH,Kind.INIT_ROUTINE,Kind.INIT_FLAGS,Kind.INIT_MAPPING,Kind.INIT_TILE,Kind.TIME_ZONE,Kind.PAST_TILE,Kind.INIT_PRIORITY),h.kinds().subList(0,8));
        var stores=h.operations.stream().filter(op->op.kind()==Kind.INIT_TILE||op.kind()==Kind.PAST_TILE).toList();
        assertEquals(0x3E4,stores.get(0).output().word(TILE));assertEquals(0x39E,stores.get(1).output().word(TILE));
    }

    @Test void everyNonzeroRawPeriodUsesPresentTileWithoutMaskingToTwoBits() {
        for(int period:new int[]{1,2,3,0x80,0xFF}) {
            var h=new Harness();h.period=period;h.body.run(WHOLE);
            assertEquals(0x3E4,h.slot().word(TILE));assertFalse(h.kinds().contains(Kind.PAST_TILE));assertEquals(1,h.periodReads);
        }
    }

    @Test void routineTwoDoesNotReadPeriodOrRewriteHeaderEvenWithInvalidAnimationSelectorHeld() {
        var h=new Harness();var before=h.slot().moveByte(ROUTINE,2).moveByte(ANIMATION,255)
                .moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,5);
        h.write(before);h.periodFault=new UnsupportedOperationException("must not read");
        assertEquals(before.addByte(ANIMATION_TIME,-1).moveByte(SPRITE_FLAGS,before.spriteFlags()&0x7F),h.body.run(WHOLE));
        assertEquals(0,h.periodReads);assertEquals(1,h.drawn.size());
        assertEquals(List.of(CdObjectAnimation.Kind.ENTER,CdObjectAnimation.Kind.STORE,CdObjectAnimation.Kind.RETURN),h.animationKinds());
    }

    @Test void routineFourDeletesAll64ThroughPoolWithoutPeriodAnimationOrDraw() {
        var h=new Harness();h.write(h.slot().moveByte(ROUTINE,4));var cut=h.runtime.capture();
        h.periodFault=new AssertionError("no period");var out=h.body.run(WHOLE);
        assertArrayEquals(new byte[64],out.copyBytes());assertEquals(List.of(Kind.DISPATCH,Kind.DELETE_OBJECT),h.kinds());
        assertTrue(h.drawn.isEmpty());assertEquals(0,h.periodReads);assertOther95(cut,h);
    }

    @Test void periodFaultKeepsDefaultTileAndEarlierStoresWithoutPriorityOrAnimation() {
        var h=new Harness();var before=h.slot();var original=new UnsupportedOperationException("actual timeZone port");h.periodFault=original;
        assertSame(original,assertThrows(UnsupportedOperationException.class,()->h.body.run(WHOLE)));
        assertEquals(before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,before.spriteFlags()|4).moveLong(MAPPINGS,MAP)
                .moveWord(TILE,0x3E4),h.slot());assertTrue(h.drawn.isEmpty());
        var last=h.operations.getLast();assertEquals(Kind.TIME_ZONE,last.kind());assertFalse(last.completed());assertFalse(h.body.isCurrentCall(last));
    }

    @Test void invalidPortByteIsContractErrorAndKeepsReachedPrefix() {
        var h=new Harness();h.period=256;assertThrows(IllegalArgumentException.class,()->h.body.run(WHOLE));
        assertEquals(0x3E4,h.slot().word(TILE));assertEquals(0xA5,h.slot().unsignedByte(PRIORITY));
        assertEquals(1,h.periodReads);assertTrue(h.drawn.isEmpty());
    }

    @Test void nativeThreeFramesAndFcStillDrawThenDeleteOnlyOnFollowingDispatch() {
        var h=new Harness();h.write(h.slot().moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,1)
                .moveByte(ANIMATION_TIME,91).moveByte(ANIMATION_FRAME,249));
        for(int call=0;call<12;call++) {
            var out=h.body.run(WHOLE);assertEquals(call/4,out.unsignedByte(MAP_FRAME));
            assertEquals(3-call%4,out.unsignedByte(ANIMATION_TIME));assertEquals(2,out.routine());
        }
        var fc=h.body.run(WHOLE);assertEquals(4,fc.routine());assertEquals(2,fc.unsignedByte(MAP_FRAME));
        assertEquals(13,h.drawn.size());assertEquals(4,h.drawn.getLast().routine());
        assertArrayEquals(new byte[64],h.body.run(WHOLE).copyBytes());assertEquals(13,h.drawn.size());assertEquals(1,h.periodReads);
    }

    @Test void changedAnimationPublishesResetBytesBeforeFailedSelectedDirectory() {
        var h=new Harness();var before=h.slot().moveByte(ROUTINE,2).moveByte(ANIMATION,1)
                .moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,19).moveByte(ANIMATION_TIME,57);h.write(before);
        var e=assertThrows(CdObjectAnimation.SelectedReadFailure.class,()->h.body.run(WHOLE));
        assertEquals(CdObjectAnimation.Kind.DIRECTORY,e.boundary());assertEquals(2,e.selectedAddress());
        assertEquals(before.moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255),h.slot());
        assertTrue(h.drawn.isEmpty());assertEquals(0,h.periodReads);
    }

    @Test void selectedFrameFailureKeepsTimerReloadAndNoInventedSuccessfulAnimation() {
        var h=new Harness();var before=h.slot().moveByte(ROUTINE,2).moveByte(ANIMATION,0)
                .moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,0).moveByte(ANIMATION_FRAME,250);h.write(before);
        var e=assertThrows(CdObjectAnimation.SelectedReadFailure.class,()->h.body.run(WHOLE));
        assertEquals(CdObjectAnimation.Kind.FRAME,e.boundary());assertEquals(253,e.selectedAddress());
        assertEquals(before.moveByte(ANIMATION_TIME,3),h.slot());assertTrue(h.drawn.isEmpty());
    }

    @Test void selectedTrailingZeroAndDirectoryByteAliasesAreRealBytes() {
        var h=new Harness();h.write(h.slot().moveByte(ROUTINE,2).moveByte(ANIMATION_TIME,0).moveByte(ANIMATION_FRAME,4)
                .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0));
        h.body.run(WHOLE);assertEquals(0,h.slot().unsignedByte(MAP_FRAME));assertEquals(5,h.slot().unsignedByte(ANIMATION_FRAME));
        var alias=new Harness(bytes(0,0,3,0,1,2,0xFC,0));alias.write(alias.slot().moveByte(ROUTINE,2).moveByte(ANIMATION_TIME,0)
                .moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0));
        alias.body.run(WHOLE);assertEquals(0,alias.slot().unsignedByte(MAP_FRAME));assertEquals(0,alias.slot().unsignedByte(ANIMATION_TIME));
    }

    @Test void inheritedFfFeFdFbFaCommandsUseSingleSharedPrefix() {
        // Constructed format adversaries: the production R11 view contains frames/FC only.
        for(int command:new int[]{0xFF,0xFE,0xFD,0xFB,0xFA}) {
            var h=new Harness(bytes(0,2,0,command,0,2,0xFC,0));
            h.write(h.slot().moveByte(ROUTINE,2).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0)
                    .moveByte(ANIMATION_TIME,0).moveByte(ANIMATION_FRAME,0).moveByte(ROUTINE_2,7));
            h.body.run(WHOLE);assertEquals(1,h.drawn.size());assertEquals(0,h.periodReads);
            if(command==0xFF||command==0xFE) {assertEquals(command&31,h.slot().unsignedByte(MAP_FRAME));assertEquals(1,h.slot().unsignedByte(ANIMATION_FRAME));}
            if(command==0xFB) {assertEquals(0,h.slot().unsignedByte(ANIMATION_FRAME));assertEquals(0,h.slot().unsignedByte(ROUTINE_2));}
            if(command==0xFA) assertEquals(9,h.slot().unsignedByte(ROUTINE_2));
            if(command==0xFD) assertTrue(h.animationKinds().contains(CdObjectAnimation.Kind.FD_OPERAND));
        }
    }

    @Test void feTargetFailureRetainsItsRealWrappedIndexStore() {
        var h=new Harness(bytes(0,2,3,0xFE,1,2,0xFC,0));
        var before=h.slot().moveByte(ROUTINE,2).moveByte(ANIMATION_TIME,0).moveByte(ANIMATION_FRAME,0);h.write(before);
        var fault=assertThrows(CdObjectAnimation.SelectedReadFailure.class,()->h.body.run(WHOLE));
        assertEquals(CdObjectAnimation.Kind.FE_TARGET,fault.boundary());assertEquals(258,fault.selectedAddress());
        assertEquals(before.moveByte(ANIMATION_TIME,3).moveByte(ANIMATION_FRAME,255),h.slot());assertTrue(h.drawn.isEmpty());
    }

    @Test void missingFeAndFdOperandsFailBeforeIndexOrAnimationStores() {
        for(int command:new int[]{0xFE,0xFD}) {
            var h=new Harness(bytes(0,2,3,0,1,2,0xFC,command));
            var before=h.slot().moveByte(ROUTINE,2).moveByte(ANIMATION_TIME,0).moveByte(ANIMATION_FRAME,4);h.write(before);
            var fault=assertThrows(CdObjectAnimation.SelectedReadFailure.class,()->h.body.run(WHOLE));
            assertEquals(command==0xFE?CdObjectAnimation.Kind.FE_OPERAND:CdObjectAnimation.Kind.FD_OPERAND,fault.boundary());
            assertEquals(8,fault.selectedAddress());assertEquals(before.moveByte(ANIMATION_TIME,3),h.slot());assertTrue(h.drawn.isEmpty());
        }
    }

    @Test void declinedAndLandedInitStoresRetainExactlyTheirOwnPrefixes() {
        for(var kind:List.of(Kind.INIT_ROUTINE,Kind.INIT_FLAGS,Kind.INIT_MAPPING,Kind.INIT_TILE,Kind.PAST_TILE,Kind.INIT_PRIORITY)) {
            for(boolean landed:new boolean[]{false,true}) {
                var h=new Harness();h.period=0;var original=new IllegalArgumentException(kind+"/"+landed);
                Consumer<CdSpinTunnelSplash.Operation> fault=op->{if(op.kind()==kind)throw original;};
                if(landed)h.after=fault;else h.before=fault;
                assertSame(original,assertThrows(IllegalArgumentException.class,()->h.body.run(WHOLE)));
                var op=h.operations.stream().filter(o->o.kind()==kind).findFirst().orElseThrow();
                assertEquals(landed,op.completed());assertEquals(landed?op.output():op.before(),h.slot());
                assertFalse(h.body.isCurrentCall(op));assertTrue(h.drawn.isEmpty());
            }
        }
    }

    @Test void declinedAndLandedAnimationByteStoresKeepActualPrefixAndRetireChildIdentity() {
        for(boolean landed:new boolean[]{false,true}) {
            var h=new Harness();h.write(h.slot().moveByte(ROUTINE,2));var before=h.slot();var original=new IllegalStateException("animation byte");
            Consumer<CdSpinTunnelSplash.Operation> fault=op->{if(op.animation()!=null&&op.animation().kind()==CdObjectAnimation.Kind.STORE)throw original;};
            if(landed)h.after=fault;else h.before=fault;
            assertSame(original,assertThrows(IllegalStateException.class,()->h.body.run(WHOLE)));
            assertEquals(landed?before.addByte(ANIMATION_TIME,-1):before,h.slot());assertTrue(h.drawn.isEmpty());
            var op=h.operations.getLast();assertNotNull(op.animation());assertEquals(landed,op.completed());assertFalse(h.body.isCurrentCall(op));
        }
    }

    @Test void guardUnsupportedExceptionIsNeverReclassifiedAsNativeSelectedRead() {
        var h=new Harness();var original=new UnsupportedOperationException("source/lifetime guard");
        h.before=op->{if(op.animation()!=null&&op.animation().kind()==CdObjectAnimation.Kind.DIRECTORY)throw original;};
        h.write(h.slot().moveByte(ROUTINE,2).moveByte(ANIMATION_TIME,0));
        assertSame(original,assertThrows(UnsupportedOperationException.class,()->h.body.run(WHOLE)));
        assertFalse(original instanceof CdObjectAnimation.SelectedReadFailure);assertEquals(255,h.slot().unsignedByte(ANIMATION_TIME));
    }

    @Test void sourceAndAll95OtherImagesAreCheckedAtBothCallbackCuts() {
        for(int whole:new int[]{WHOLE,32,127}) for(boolean after:new boolean[]{false,true}) {
            var h=new Harness();Consumer<CdSpinTunnelSplash.Operation> mutate=op->{if(op.kind()==Kind.INIT_ROUTINE)
                h.runtime.pool().write(h.runtime.pool().state(whole).moveByte(ANGLE,7));};
            if(after)h.after=mutate;else h.before=mutate;
            assertThrows(IllegalStateException.class,()->h.body.run(WHOLE));assertTrue(h.drawn.isEmpty());
            assertEquals(after?2:0,h.slot().routine());assertEquals(7,h.runtime.pool().state(whole).unsignedByte(ANGLE));
        }
    }

    @Test void savedBankAndPlacementMutationCannotPassObservation() {
        var h=new Harness();h.before=op->{h.runtime.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.LOADED);};
        assertThrows(IllegalStateException.class,()->h.body.run(WHOLE));assertEquals(0,h.slot().routine());
        var p=new Harness();p.before=op->p.runtime.spawnObjects(0,1);
        assertThrows(IllegalStateException.class,()->p.body.run(WHOLE));assertEquals(0,p.slot().routine());
    }

    @Test void originalReadFaultWinsWithDistinctMutationAndAfterGuardFaultsSuppressed() {
        var h=new Harness();var original=new UnsupportedOperationException("period source");var unwind=new AssertionError("external unwind");
        h.periodAction=()->{h.runtime.pool().write(h.runtime.pool().state(127).moveByte(ANGLE,9));throw original;};
        h.after=op->{if(op.kind()==Kind.TIME_ZONE)throw unwind;};
        assertSame(original,assertThrows(UnsupportedOperationException.class,()->h.body.run(WHOLE)));
        assertTrue(List.of(original.getSuppressed()).contains(unwind));
        assertTrue(List.of(original.getSuppressed()).stream().anyMatch(IllegalStateException.class::isInstance));
        assertTrue(h.drawn.isEmpty());assertEquals(0x3E4,h.slot().word(TILE));
    }

    @Test void errorBeforeStoreStillCallsUnwindAndKeepsFirstThrowable() {
        var h=new Harness();var first=new AssertionError("first");var second=new IllegalArgumentException("second");
        h.before=op->{throw first;};h.after=op->{assertFalse(op.completed());throw second;};
        var before=h.slot();assertSame(first,assertThrows(AssertionError.class,()->h.body.run(WHOLE)));
        assertEquals(before,h.slot());assertArrayEquals(new Throwable[]{second},first.getSuppressed());
    }

    @Test void successfulDrawRequiresOnlyActualSourceBitSevenClear() {
        var h=new Harness();h.write(h.slot().moveByte(ROUTINE,2));var before=h.slot();h.body.run(WHOLE);
        assertEquals(before.spriteFlags()&0x7F,h.slot().spriteFlags());assertEquals(1,h.drawn.size());
        for(int whole:new int[]{WHOLE,127}) {
            var bad=new Harness();bad.drawAction=()->{bad.runtime.pool().write(bad.runtime.pool().state(whole).moveByte(WIDTH,1));};
            assertThrows(IllegalStateException.class,()->bad.body.run(WHOLE));
        }
        var noClear=new Harness();noClear.drawAction=()->{};assertThrows(IllegalStateException.class,()->noClear.body.run(WHOLE));
    }

    @Test void failedDrawAdmitsOnlyRefusedOrClearedPrefixAndKeepsOriginal() {
        for(boolean cleared:new boolean[]{false,true}) {
            var h=new Harness();var original=new UnsupportedOperationException("Draw port");h.drawAction=()->{
                if(cleared)h.clearDrawBit();throw original;
            };
            assertSame(original,assertThrows(UnsupportedOperationException.class,()->h.body.run(WHOLE)));
            assertEquals(cleared?0x45:0xC5,h.slot().spriteFlags());assertEquals(0,original.getSuppressed().length);
            assertFalse(h.operations.getLast().completed());assertFalse(h.body.isCurrentCall(h.operations.getLast()));
        }
        var bad=new Harness();var original=new AssertionError("partial Draw");bad.drawAction=()->{
            bad.clearDrawBit();bad.write(bad.slot().moveWord(X_FRACTION,0));throw original;};
        assertSame(original,assertThrows(AssertionError.class,()->bad.body.run(WHOLE)));
        assertTrue(original.getSuppressed().length>0);
    }

    @Test void originalOperationIdentityIsPrivateLiveAndCompletedOnlyAtAfterCut() {
        var h=new Harness();h.before=op->{assertTrue(h.body.isCurrentCall(op));assertFalse(op.completed());
            assertEquals(h.slot(),op.before());assertEquals(h.runtime.capture().pool().slots(),op.cut().pool().slots());
            assertFalse(h.body.isCurrentCall(new Object()));if(h.operations.size()>1)assertFalse(h.body.isCurrentCall(h.operations.getFirst()));};
        h.after=op->{assertTrue(h.body.isCurrentCall(op));assertTrue(op.completed());assertTrue(h.body.isCurrentCallCompleted(op));};
        h.body.run(WHOLE);for(var op:h.operations){assertFalse(h.body.isCurrentCall(op));assertFalse(h.body.isCurrentCallCompleted(op));}
        var other=new Harness();assertFalse(other.body.isCurrentCall(h.operations.getFirst()));
    }

    @Test void guardPeriodAndDrawReentryRefuseBeforeAnyNestedMutation() {
        for(int port=0;port<3;port++) {
            var h=new Harness();var original=new IllegalStateException("outer");
            Runnable reenter=()->{var before=h.runtime.capture();assertThrows(IllegalStateException.class,()->h.body.run(WHOLE));
                assertEquals(before.pool().slots(),h.runtime.capture().pool().slots());throw original;};
            if(port==0)h.before=op->reenter.run();if(port==1)h.periodAction=()->{reenter.run();return 1;};if(port==2)h.drawAction=reenter;
            assertSame(original,assertThrows(IllegalStateException.class,()->h.body.run(WHOLE)));
        }
    }

    @Test void foreignThreadCannotRunOrClaimRetainedOperation() throws InterruptedException {
        var h=new Harness();var failures=new ArrayList<Throwable>();var before=h.slot();
        var thread=new Thread(()->{try{h.body.run(WHOLE);}catch(Throwable e){failures.add(e);}});thread.start();thread.join();
        assertEquals(1,failures.size());assertInstanceOf(IllegalStateException.class,failures.getFirst());assertEquals(before,h.slot());
        assertTrue(h.operations.isEmpty());
        h.before=op->{var claimedLive=new boolean[]{true};var foreign=new Thread(()->claimedLive[0]=h.body.isCurrentCall(op));
            foreign.start();try{foreign.join();}catch(InterruptedException e){throw new AssertionError(e);}assertFalse(claimedLive[0]);};
        h.body.run(WHOLE);var op=h.operations.getLast();var claimed=new boolean[]{true};
        var second=new Thread(()->claimed[0]=h.body.isCurrentCall(op));second.start();second.join();assertFalse(claimed[0]);
    }

    @Test void wrongIdBoundsAndUnsupportedRoutineStopBeforePortsOrStores() {
        for(int routine:new int[]{1,3,5,255}) {
            var h=new Harness();h.write(h.slot().moveByte(ROUTINE,routine));var before=h.slot();
            var e=assertThrows(CdSpinTunnelSplash.UnsupportedSpinTunnelSplash.class,()->h.body.run(WHOLE));
            assertEquals(routine,e.routine());assertEquals(WHOLE,e.wholeIndex());assertEquals(before,h.slot());assertEquals(List.of(Kind.DISPATCH),h.kinds());assertFalse(h.operations.getFirst().completed());
        }
        var h=new Harness();h.write(h.slot().moveByte(ID,11));assertThrows(IllegalArgumentException.class,()->h.body.run(WHOLE));
        assertThrows(IndexOutOfBoundsException.class,()->h.body.run(31));assertTrue(h.operations.isEmpty());assertEquals(0,h.periodReads);
    }

    @Test void originalLifetimeRefusalPrecedesNativeUnsupportedRoutineClassification() {
        var h=new Harness();h.write(h.slot().moveByte(ROUTINE,255));var before=h.slot();
        var fault=new UnsupportedOperationException("retired original frame");h.before=op->{throw fault;};
        assertSame(fault,assertThrows(UnsupportedOperationException.class,()->h.body.run(WHOLE)));
        assertEquals(before,h.slot());assertEquals(List.of(Kind.DISPATCH),h.kinds());assertFalse(h.operations.getFirst().completed());
    }

    @Test void equalValueStoresAreSeparateAndDeletionRefusalNeverClearsEarly() {
        var h=new Harness();h.write(h.slot().moveByte(SPRITE_FLAGS,4).moveLong(MAPPINGS,MAP).moveWord(TILE,0x3E4).moveByte(PRIORITY,1));
        h.body.run(WHOLE);assertTrue(h.operations.stream().anyMatch(op->op.kind()==Kind.INIT_MAPPING&&op.before().equals(op.output())));
        for(boolean landed:new boolean[]{false,true}) {
            var d=new Harness();d.write(d.slot().moveByte(ROUTINE,4));var before=d.slot();var fault=new AssertionError("delete");
            if(landed)d.after=op->{if(op.kind()==Kind.DELETE_OBJECT)throw fault;};else d.before=op->{if(op.kind()==Kind.DELETE_OBJECT)throw fault;};
            assertSame(fault,assertThrows(AssertionError.class,()->d.body.run(WHOLE)));
            assertArrayEquals(landed?new byte[64]:before.copyBytes(),d.slot().copyBytes());assertTrue(d.drawn.isEmpty());
        }
    }

    @Test void constructionDoesNotPrimeReadPeriodDrawAllocateOrPublish() {
        var h=new Harness();assertTrue(h.operations.isEmpty());assertEquals(0,h.periodReads);assertTrue(h.drawn.isEmpty());
        assertEquals(0,h.slot().routine());assertEquals(0,h.runtime.pool().state(32).id());
        assertThrows(IllegalArgumentException.class,()->new CdSpinTunnelSplash.Data(MAP+1,table(ANI)));
        assertThrows(IllegalArgumentException.class,()->new CdSpinTunnelSplash.Data(MAP,new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(new byte[10]),0,1)));
    }

    private static void assertOther95(CdObjectPlacementRuntime.Snapshot cut,Harness h) {
        for(var old:cut.pool().slots())if(old.identity().wholeIndex()!=WHOLE)assertEquals(old,h.runtime.pool().state(old.identity().wholeIndex()));
        assertEquals(cut.status(),h.runtime.capture().status());assertArrayEquals(cut.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
    }
    private static CdObjectAnimation.AnimationTable table(byte[] bytes) {return new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes),0,1);}
    private static byte[] bytes(int... values) {var b=new byte[values.length];for(int n=0;n<b.length;n++)b[n]=(byte)values[n];return b;}
    private static CdObjectPlacements layout() {
        var b=ByteBuffer.allocate(26);b.putShort((short)12).putShort((short)20).putShort((short)0xFFFF).putInt(0).putShort((short)0);
        b.putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0xFFFF).putInt(0);return CdObjectPlacements.decodeLayout(b.array());
    }
    private static CdObjectState dirty(CdObjectState base) {
        try {
            var owner=CdObjectState.class.getDeclaredField("owner");owner.setAccessible(true);
            var ctor=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);ctor.setAccessible(true);
            var b=new byte[64];for(int n=0;n<64;n++)b[n]=(byte)(0x31+17*n);return ctor.newInstance(owner.get(base),base.identity(),b);
        }catch(ReflectiveOperationException e){throw new AssertionError("Storage-only fixture",e);}
    }
    private static final class Harness {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(layout());
        final List<CdSpinTunnelSplash.Operation> operations=new ArrayList<>();final List<CdObjectState> drawn=new ArrayList<>();
        Consumer<CdSpinTunnelSplash.Operation> before=op->{},after=op->{};
        CdSpinTunnelSplash.TimeZoneRead periodAction;Runnable drawAction;Throwable periodFault;int period=1,periodReads;
        final CdSpinTunnelSplash body;
        Harness(){this(ANI);}
        Harness(byte[] ani) {
            write(dirty(slot()).moveByte(ID,0x0E).moveByte(ROUTINE,0).moveByte(SPRITE_FLAGS,0xC1).moveByte(FLAGS,1)
                    .moveByte(PRIORITY,0xA5).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0)
                    .moveByte(ANIMATION_FRAME,249).moveByte(ANIMATION_TIME,5));
            runtime.pool().write(runtime.pool().state(127).moveWord(X_FRACTION,0x5678).moveByte(ANGLE,6));
            var guard=new CdSpinTunnelSplash.Guard(){
                public void before(CdSpinTunnelSplash.Operation op){operations.add(op);before.accept(op);}
                public void after(CdSpinTunnelSplash.Operation op){after.accept(op);}
            };
            body=new CdSpinTunnelSplash(runtime,new CdSpinTunnelSplash.Data(MAP,table(ani)),guard,()->{
                periodReads++;if(periodAction!=null)return periodAction.readTimeZone();
                if(periodFault instanceof Error e)throw e;if(periodFault!=null)throw (RuntimeException)periodFault;return period;
            },op->{drawn.add(slot());if(drawAction!=null)drawAction.run();else clearDrawBit();});
        }
        CdObjectState slot(){return runtime.pool().state(WHOLE);}void write(CdObjectState s){runtime.pool().write(s);}
        void clearDrawBit(){write(slot().moveByte(SPRITE_FLAGS,slot().spriteFlags()&0x7F));}
        List<CdSpinTunnelSplash.Kind> kinds(){return operations.stream().map(CdSpinTunnelSplash.Operation::kind).toList();}
        List<CdObjectAnimation.Kind> animationKinds(){return operations.stream().filter(o->o.animation()!=null).map(o->o.animation().kind()).toList();}
    }
}
