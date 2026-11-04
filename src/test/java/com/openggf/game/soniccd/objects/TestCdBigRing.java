package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Constructed source oracles; bounded execution is recorded in the implementation plan.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Level End/Main.asm:385-562;
 * Object Animate.asm:14-95, Object Spawner.asm:329-340,385-446. Raw expected64B/all96 images
 * and declared external cells do not authenticate media, actual Frame adoption, route or parity.
 */
class TestCdBigRing {
    private static final int WHOLE=40, X0=1000, Y0=740, MAP=0x21D8F6, FLASH_MAP=0x20D9E6;
    private static final byte[] RING=hex("00020700010203ff"), FLASH=hex("0002020001020304050607fc");

    @Test void subtypeFirstFlashNeedsNoGlobalPlayerOrProgrammeOwnerAndPreservesDirtyNonwrites() {
        for(int subtype:new int[]{1,127,128,255}) {
            var h=new H(assets(RING,FLASH),Set.of(Owner.READ,Owner.SPECIAL,Owner.PLAYER,Owner.SCROLL,Owner.SOUND));
            h.edit(s -> dirty(s).moveByte(ID,0x14).moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype)
                    .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,1));
            var e=images(h); var b=e.get(WHOLE-32); b[1]=(byte)((b[1]|4)&0x7F); b[0x24]=2; w(b,2,0x03EF); l(b,4,FLASH_MAP); b[0x1E]=0;
            h.run(); assertImages(e,h); assertEquals(List.of("draw"),h.order);
        }
        assertThrows(NullPointerException.class,() -> CdBigRing.Assets.fromPastAssets(null));
    }

    @Test void parentFiftyRingGatePrecedesRawRoutineAdmissionAndNeverDrawsBelowFifty() {
        for(int rings:new int[]{0,1,49}) {
            var h=new H(); h.values.put(CdBigRing.ReadField.RINGS_WORD,rings);
            h.edit(s -> s.moveByte(ROUTINE,255).moveByte(SPRITE_FLAGS,0x84)); var e=images(h); h.run(); assertImages(e,h);
            assertEquals(List.of("read:RINGS_WORD","read:TIME_ZONE_BYTE"),h.order);
        }
        for(int rings:new int[]{50,0x8000,0xFFFF}) {
            var h=new H(); h.values.put(CdBigRing.ReadField.RINGS_WORD,rings); h.edit(s -> s.moveByte(ROUTINE,255)); var e=images(h);
            assertEquals(CdBigRing.UnsupportedBoundary.ROUTINE_DISPATCH,assertThrows(CdBigRing.UnsupportedBigRing.class,h::run).boundary());
            assertImages(e,h); assertEquals(List.of("read:RINGS_WORD"),h.order);
        }
    }

    @Test void parentAndFlashRejectUnsupportedRawDirectoriesOnlyAfterTheirActualOuterGate() {
        for(int subtype:new int[]{0,1}) for(int routine:new int[]{1,3,5,6,128,254,255}) {
            var h=new H(); h.edit(s -> s.moveByte(SUBTYPE,subtype).moveByte(ROUTINE,routine)); var e=images(h);
            var failure=assertThrows(CdBigRing.UnsupportedBigRing.class,h::run);
            assertEquals(CdBigRing.UnsupportedBoundary.ROUTINE_DISPATCH,failure.boundary()); assertEquals(routine,failure.value());
            assertImages(e,h); assertEquals(subtype==0?List.of("read:RINGS_WORD"):List.of(),h.order);
        }
    }

    @Test void exactTimeStoneByteAndNonzeroTimeAttackDeleteFull64BeforeLaterReadsThenDrawZero() {
        for(int[] input:new int[][]{{127,0},{127,255},{0,1},{255,128}}) {
            var h=new H(); h.edit(s -> dirty(s).moveByte(ID,0x14).moveByte(SUBTYPE,0).moveByte(ROUTINE,0));
            h.values.put(CdBigRing.ReadField.TIME_STONES_BYTE,input[0]); h.values.put(CdBigRing.ReadField.TIME_ATTACK_BYTE,input[1]);
            var saved=h.placement.capture().savedFlags().copy(); var e=images(h); e.set(WHOLE-32,new byte[64]);
            var result=h.run(); assertTrue(result.deleted()); assertImages(e,h); assertArrayEquals(saved,h.placement.capture().savedFlags().copy());
            assertArrayEquals(new byte[64],h.draws.getFirst().copyBytes());
            assertEquals(input[0]==127?List.of("read:RINGS_WORD","read:TIME_STONES_BYTE","draw")
                    :List.of("read:RINGS_WORD","read:TIME_STONES_BYTE","read:TIME_ATTACK_BYTE","draw"),h.order);
        }
        var h=new H(); h.values.put(CdBigRing.ReadField.TIME_STONES_BYTE,255); h.edit(s -> s.moveByte(ROUTINE,0));
        h.run(); assertEquals(2,h.slot().routine());
    }

    @Test void initCommitsOnlyNativeHeaderBeforeFirstPlayerReadFailureRetainingEveryOtherByte() {
        var h=new H(); h.edit(s -> dirty(s).moveByte(ID,0x14).moveByte(SUBTYPE,0).moveByte(ROUTINE,0));
        var e=images(h); var b=e.get(WHOLE-32); b[0x24]=2; b[1]|=4; w(b,2,0x2488); l(b,4,MAP); b[0x17]=b[0x19]=b[0x16]=32;
        var original=new AssertionError("player radius owner"); h.onRead=c -> { if(c.field()==CdBigRing.ReadField.PLAYER_X_RADIUS_BYTE)throw original; };
        assertSame(original,assertThrows(AssertionError.class,h::run)); assertImages(e,h); assertTrue(h.draws.isEmpty());
    }

    @Test void customTouchWindowUsesSignedRadiiWrappedWordsAndShortCircuitsUnreachedY() {
        int[][] vectors={{X0-25,Y0,9,19,1},{X0+24,Y0,9,19,1},{X0+25,Y0,9,19,0},
                {X0,Y0-51,9,19,1},{X0,Y0+50,9,19,1},{X0,Y0+51,9,19,0},
                {X0,Y0,128,19,0},{X0,Y0,240,19,0},{X0+14,Y0,255,19,1},{X0+15,Y0,255,19,0},
                {X0,Y0,9,224,0}};
        for(var v:vectors) {
            var h=new H(); h.playerXLong=(v[0]<<16)|0x1234; h.playerYLong=(v[1]<<16)|0x4567;
            h.values.put(CdBigRing.ReadField.PLAYER_X_RADIUS_BYTE,v[2]); h.values.put(CdBigRing.ReadField.PLAYER_Y_RADIUS_BYTE,v[3]);
            h.run(); assertEquals(v[4],h.specialCalls.size());
            if(v[2]==128 || v[2]==240 || v[0]==X0+25 || v[0]==X0+15)
                assertFalse(h.order.contains("read:PLAYER_Y_RADIUS_BYTE"));
        }
        var h=new H(); h.edit(s -> s.moveWord(X,8)); h.playerXLong=(0xFFF0<<16)|0x1234; h.playerYLong=(Y0<<16)|0x4567;
        h.run(); assertEquals(1,h.specialCalls.size());
    }

    @Test void hitPublishesOrderedTypedStoresBeforeDirtyFirstFreeChildAndHidesTheParent() {
        var h=new H(); h.contact(); var free=dirty(h.pool().state(32)).moveByte(ID,0); h.pool().write(free);
        var e=images(h); var parent=e.get(WHOLE-32); parent[0x24]=4; parent[0x1E]=0;
        byte[] child=free.copyBytes(); child[0]=0x14; w(child,8,X0); w(child,12,Y0); child[0x28]=1; e.set(0,child);
        h.onSpecial=c -> assertEquals(2,c.expectedSource().routine());
        h.onPlayer=c -> { assertEquals(4,c.expectedSource().routine()); assertEquals(0,h.pool().state(32).id()); };
        h.onSound=c -> assertEquals(0,h.pool().state(32).id());
        var result=h.run(); assertImages(e,h); assertTrue(h.draws.isEmpty());
        assertEquals(1,h.special); assertEquals((1236<<16)|0x1234,h.playerXLong);
        assertEquals((Y0<<16)|0x4567,h.playerYLong); assertEquals(0,h.xv); assertEquals(0,h.gv);
        assertEquals(0xA5,h.controlLock); assertEquals(0x0808,h.logical); assertEquals(0x08,h.rawHeld); assertEquals(0x10,h.rawTap);
        assertEquals(0x01EF,h.scrollWord); assertEquals(1,h.sounds.size());
        assertEquals(List.of("read:RINGS_WORD","read:PLAYER_X_RADIUS_BYTE","read:PLAYER_X_WORD",
                "read:PLAYER_Y_RADIUS_BYTE","read:PLAYER_Y_WORD","special","read:CAMERA_X_WORD","player:PlayerXWord",
                "player:ControlLockBit0","player:ControlHold0808","player:XVelocityZero","player:GroundVelocityZero","scroll","fm"),h.order);
        assertEquals(1,result.effects().stream().filter(CdBigRing.ChildCreated.class::isInstance).count());
        assertThrows(UnsupportedOperationException.class,() -> result.effects().clear());
    }

    @Test void laterFlashExecutesInTheSameConstructedPhysicalWalkWhileAnEarlierFlashWaits() {
        for(boolean later:new boolean[]{false,true}) {
            var h=new H(); h.contact();
            if(later) for(int whole=32;whole<WHOLE;whole++)h.pool().write(h.pool().state(whole).moveByte(ID,0x10));
            for(int whole=WHOLE;whole<128;whole++)if(h.pool().state(whole).id()==0x14)h.body.run(whole);
            int child=later?41:32; assertEquals(later?2:0,h.pool().state(child).routine());
            assertEquals(1,h.pool().state(child).subtype()); assertEquals(later?1:0,h.draws.size());
            if(later)assertEquals(FLASH_MAP,h.pool().state(child).longBits(MAPPINGS));
        }
    }

    @Test void noFreeSlotBranchesToMainAndRechecksFreshForcedPlayerWithoutReplayingTheOuterGate() {
        var h=new H(); h.contact(); h.full(); var result=h.run();
        assertEquals(1,h.specialCalls.size()); assertEquals(1,h.sounds.size()); assertEquals(4,h.slot().routine());
        assertEquals(1,h.order.stream().filter("read:RINGS_WORD"::equals).count());
        assertEquals(2,h.order.stream().filter("read:PLAYER_X_WORD"::equals).count());
        assertEquals(1,result.effects().stream().filter(CdBigRing.AllocationRecheck.class::isInstance).count());
        assertTrue(result.effects().stream().noneMatch(CdBigRing.ChildCreated.class::isInstance)); assertTrue(h.draws.isEmpty());
    }

    @Test void repeatedOwnedAllocationCutFencesTheUnclosedNativeMainContinuation() {
        var h=new H(); h.contact(); h.full(); h.values.put(CdBigRing.ReadField.CAMERA_X_WORD,X0-336);
        var e=assertThrows(CdBigRing.UnsupportedBigRing.class,h::run);
        assertEquals(CdBigRing.UnsupportedBoundary.ALLOCATION_MAIN_CYCLE,e.boundary());
        // ADDQ.B#2 cycles the full native byte after128 hits; the next identical post-hit cut repeats.
        assertEquals(129,h.specialCalls.size()); assertEquals(129,h.sounds.size()); assertEquals(4,h.slot().routine());
        assertEquals(1,h.order.stream().filter("read:RINGS_WORD"::equals).count()); assertTrue(h.draws.isEmpty());
        assertThrows(UnsupportedOperationException.class,() -> e.completedEffects().clear());
    }

    @Test void eachMissingExternalOwnerStopsOnlyItsReachedNativePrefix() {
        for(var owner:Owner.values()) {
            var h=new H(assets(RING,FLASH),Set.of(owner)); h.contact();
            var e=assertThrows(CdBigRing.UnsupportedBigRing.class,h::run);
            var boundary=switch(owner) {
                case READ -> CdBigRing.UnsupportedBoundary.READ_OWNER;
                case SPECIAL -> CdBigRing.UnsupportedBoundary.SPECIAL_STAGE_OWNER;
                case PLAYER -> CdBigRing.UnsupportedBoundary.PLAYER_OWNER;
                case SCROLL -> CdBigRing.UnsupportedBoundary.SCROLL_LOCK_OWNER;
                case SOUND -> CdBigRing.UnsupportedBoundary.SOUND_OWNER;
            };
            assertEquals(boundary,e.boundary()); assertEquals(owner==Owner.READ||owner==Owner.SPECIAL?2:4,h.slot().routine());
            assertEquals(owner==Owner.READ||owner==Owner.SPECIAL?0xA5:1,h.special);
            assertEquals(0,h.pool().state(32).id()); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void playerAndSoundFailuresRetainTheirExactAlreadyAdoptedPrefixAndOriginalError() {
        var h=new H(); h.contact(); var original=new AssertionError("GV owner");
        h.onPlayer=c -> { if(c instanceof CdBigRing.GroundVelocityZero)throw original; };
        assertSame(original,assertThrows(AssertionError.class,h::run)); assertEquals(4,h.slot().routine());
        assertEquals(1,h.special); assertEquals(0,h.xv); assertEquals(-292,h.gv); assertEquals(0xBEEF,h.scrollWord);
        assertEquals(0,h.pool().state(32).id()); assertTrue(h.sounds.isEmpty()); assertTrue(h.draws.isEmpty());
        var fm=new H(); fm.contact(); var soundError=new IllegalArgumentException("FM owner"); fm.onSound=c -> { throw soundError; };
        assertSame(soundError,assertThrows(IllegalArgumentException.class,fm::run)); assertEquals(0x01EF,fm.scrollWord);
        assertEquals(0,fm.xv); assertEquals(0,fm.gv); assertEquals(1,fm.slot().unsignedByte(ANIMATION_TIME));
        assertEquals(0,fm.pool().state(32).id()); assertTrue(fm.draws.isEmpty());
    }

    @Test void fullPlacementAttacksOnReadsAndEveryTypedStoreAreRejectedWithoutRepair() {
        for(var owner:Owner.values()) {
            var h=new H(); h.contact(); Runnable attack=() -> h.pool().write(h.pool().state(75).moveByte(COLLISION_STATUS,0x55));
            switch(owner) {
                case READ -> h.onRead=c -> attack.run();
                case SPECIAL -> h.onSpecial=c -> attack.run();
                case PLAYER -> h.onPlayer=c -> attack.run();
                case SCROLL -> h.onScroll=c -> attack.run();
                case SOUND -> h.onSound=c -> attack.run();
            }
            assertThrows(IllegalStateException.class,h::run); assertEquals(0x55,h.pool().state(75).unsignedByte(COLLISION_STATUS));
            assertEquals(0,h.pool().state(32).id()); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void readBankOrCursorMutationAndInvalidWidthAreNotAcceptedAsReadOnlyValues() {
        var bank=new H(); bank.onRead=c -> bank.placement.bsetSavedFlag(11,0,CdObjectPlacementRuntime.SavedBit.LOADED);
        assertThrows(IllegalStateException.class,bank::run); assertEquals(0x80,bank.placement.savedFlag(11,0));
        var cursor=new H(); cursor.onRead=c -> cursor.placement.spawnObjects(0,0);
        assertThrows(IllegalStateException.class,cursor::run); assertEquals(2,cursor.placement.status().routine());
        var width=new H(); width.readOverride=c -> 65536;
        assertEquals(CdBigRing.UnsupportedBoundary.READ_VALUE_WIDTH,assertThrows(CdBigRing.UnsupportedBigRing.class,width::run).boundary());
        assertEquals(2,width.slot().routine());
    }

    @Test void originalCallbackFailureWinsAndPostVerificationIsSuppressedWithItsMutationRetained() {
        var h=new H(); h.contact(); var original=new AssertionError("original FM");
        h.onSound=c -> { h.pool().write(h.pool().state(75).moveByte(COLLISION_STATUS,0x55)); throw original; };
        assertSame(original,assertThrows(AssertionError.class,h::run)); assertEquals(1,original.getSuppressed().length);
        assertEquals(0x55,h.pool().state(75).unsignedByte(COLLISION_STATUS)); assertEquals(0x01EF,h.scrollWord);
        assertEquals(0,h.pool().state(32).id()); assertTrue(h.draws.isEmpty());
    }

    @Test void sameInstanceRejectsReentryAtEveryReachedPortAndRetiresScopeAfterFailure() {
        var h=new H(); h.contact();
        h.onRead=c -> assertThrows(IllegalStateException.class,h::run);
        h.onSpecial=c -> assertThrows(IllegalStateException.class,h::run);
        h.onPlayer=c -> assertThrows(IllegalStateException.class,h::run);
        h.onScroll=c -> assertThrows(IllegalStateException.class,h::run);
        h.onSound=c -> assertThrows(IllegalStateException.class,h::run);
        h.run(); assertEquals(1,h.sounds.size());
        h.edit(s -> s.moveByte(SUBTYPE,1).moveByte(ROUTINE,2));
        h.onDraw=c -> assertThrows(IllegalStateException.class,h::run); h.run(); assertEquals(1,h.draws.size());
    }

    @Test void returnedPublicPortValuesCannotAuthenticateActualFrameAdoption() {
        var h=new H(); h.contact(); h.ignorePlayer=true; var originalX=h.playerXLong;
        var result=h.run(); assertEquals(originalX,h.playerXLong); assertEquals(0x1234,h.xv);
        assertTrue(result.effects().stream().anyMatch(CdBigRing.PlayerCommitted.class::isInstance));
        // This stand-in supplies no actual Frame. Root must independently reject skipped adoption.
        assertEquals(1,h.pool().state(32).subtype()); assertEquals(4,h.slot().routine());
    }

    @Test void normalHiddenRoutineFourUsesEightVisitFramesAndTheRealFfLoop() {
        var h=new H(); h.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION_TIME,0));
        for(int visit=1;visit<=32;visit++) {
            h.run(); assertEquals((visit-1)/8,h.slot().unsignedByte(MAP_FRAME)); assertEquals(7-(visit-1)%8,h.slot().unsignedByte(ANIMATION_TIME));
        }
        h.run(); assertEquals(0,h.slot().unsignedByte(MAP_FRAME)); assertEquals(1,h.slot().unsignedByte(ANIMATION_FRAME));
        assertTrue(h.draws.isEmpty()); assertTrue(h.specialCalls.isEmpty());
    }

    @Test void flashUsesThreeVisitFramesThenFcAndDrawsItsFullDeleteWithoutSavedFlagChanges() {
        var h=new H(); h.edit(s -> dirty(s).moveByte(ID,0x14).moveByte(SUBTYPE,1).moveByte(ROUTINE,0)
                .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0));
        var before=h.placement.capture();
        for(int visit=1;visit<=24;visit++) {
            h.run(); assertEquals((visit-1)/3,h.slot().unsignedByte(MAP_FRAME)); assertEquals(2-(visit-1)%3,h.slot().unsignedByte(ANIMATION_TIME));
        }
        h.run(); assertEquals(4,h.slot().routine()); assertEquals(7,h.slot().unsignedByte(MAP_FRAME));
        h.run(); assertArrayEquals(new byte[64],h.slot().copyBytes()); assertEquals(26,h.draws.size());
        assertArrayEquals(new byte[64],h.draws.getLast().copyBytes()); assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
    }

    @Test void heldRawIdsAndEmptyTablesAreUnreadUntilTheActualTimerUnderflows() {
        for(boolean flash:new boolean[]{false,true}) {
            var h=new H(assets(new byte[0],new byte[0]),Set.of());
            h.edit(s -> s.moveByte(SUBTYPE,flash?1:0).moveByte(ROUTINE,flash?2:4).moveByte(ANIMATION,255)
                    .moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_FRAME,0xA7).moveByte(MAP_FRAME,0xFE).moveByte(ANIMATION_TIME,1));
            h.run(); assertEquals(0,h.slot().unsignedByte(ANIMATION_TIME)); assertEquals(0xFE,h.slot().unsignedByte(MAP_FRAME));
            var e=images(h); e.get(WHOLE-32)[0x1E]=(byte)255;
            assertEquals(CdBigRing.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,assertThrows(CdBigRing.UnsupportedBigRing.class,h::run).boundary());
            assertImages(e,h); assertEquals(flash?1:0,h.draws.size());
        }
    }

    @Test void selectedAnimationFaultsRetainResetSubqSpeedAndFeIndexPrefixesWithoutAnotherStep() {
        var changed=new H(); changed.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,0)
                .moveByte(ANIMATION_FRAME,0xA7).moveByte(ANIMATION_TIME,0x55));
        var e=images(changed); var b=e.get(WHOLE-32); b[0x1D]=1;b[0x1B]=0;b[0x1E]=(byte)255;
        var fault=assertThrows(CdBigRing.UnsupportedBigRing.class,changed::run); assertEquals(CdBigRing.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,fault.boundary());
        assertNotNull(fault.getCause()); assertImages(e,changed);
        for(boolean flash:new boolean[]{false,true}) {
            var h=new H(); h.edit(s -> s.moveByte(SUBTYPE,flash?1:0).moveByte(ROUTINE,flash?2:4)
                    .moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,0)); var expected=images(h); expected.get(WHOLE-32)[0x1E]=(byte)(flash?2:7);
            assertEquals(CdBigRing.UnsupportedBoundary.ANIMATION_FRAME_READ,assertThrows(CdBigRing.UnsupportedBigRing.class,h::run).boundary());
            assertImages(expected,h); assertTrue(h.draws.isEmpty());
        }
        var fe=new H(assets(hex("000200fe01"),FLASH),Set.of()); fe.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION_TIME,0));
        var expected=images(fe); expected.get(WHOLE-32)[0x1E]=0; expected.get(WHOLE-32)[0x1B]=(byte)255;
        assertEquals(CdBigRing.UnsupportedBoundary.ANIMATION_FRAME_READ,assertThrows(CdBigRing.UnsupportedBigRing.class,fe::run).boundary()); assertImages(expected,fe);
    }

    @Test void badSignedScriptPointerAndMissingFdOperandKeepOnlyReachedStores() {
        for(byte[] bytes:new byte[][]{hex("ffff00"),hex("000200fd")}) {
            var h=new H(assets(bytes,FLASH),Set.of()); h.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION_TIME,0)); var e=images(h);
            e.get(WHOLE-32)[0x1E]=(byte)(bytes[0]==(byte)255?255:0);
            var failure=assertThrows(CdBigRing.UnsupportedBigRing.class,h::run);
            assertEquals(bytes[0]==(byte)255?CdBigRing.UnsupportedBoundary.ANIMATION_SPEED_READ:CdBigRing.UnsupportedBoundary.ANIMATION_OPERAND_READ,failure.boundary());
            assertImages(e,h); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void belowFiftyDespawnKeepsExactDistanceBoundaryAndClearsOnlyLoadedBeforeFullDelete() {
        for(int x:new int[]{0x300,0x380}) {
            var h=new H(); h.values.put(CdBigRing.ReadField.RINGS_WORD,49); h.values.put(CdBigRing.ReadField.CAMERA_X_WORD,0x100);
            h.edit(s -> s.moveByte(SPRITE_FLAGS,4).moveWord(X,x).moveByte(SAVED_FLAGS_ID,10));
            h.placement.bsetSavedFlag(10,0,CdObjectPlacementRuntime.SavedBit.LOADED); h.placement.bsetSavedFlag(10,0,CdObjectPlacementRuntime.SavedBit.MEMBER_2);
            var result=h.run(); assertEquals(x==0x380,result.deleted()); assertEquals(x==0x380?2:0x82,h.placement.savedFlag(10,0));
            if(x==0x380)assertArrayEquals(new byte[64],h.slot().copyBytes()); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void rawDespawnPeriodCanAddressAnotherLegalBankByteAndBadAddressStopsBeforeDeletion() {
        var h=new H(); h.values.put(CdBigRing.ReadField.RINGS_WORD,0); h.values.put(CdBigRing.ReadField.CAMERA_X_WORD,0x100);
        h.values.put(CdBigRing.ReadField.TIME_ZONE_BYTE,127); h.edit(s -> s.moveByte(SPRITE_FLAGS,4).moveWord(X,0x380).moveByte(SAVED_FLAGS_ID,1));
        h.placement.bsetSavedFlag(43,1,CdObjectPlacementRuntime.SavedBit.LOADED); var result=h.run();
        assertEquals(0,h.placement.savedFlag(43,1)); assertTrue(result.effects().stream().anyMatch(v -> v instanceof CdBigRing.SavedFlagChanged b && b.byteIndex()==132));
        var bad=new H(); bad.values.put(CdBigRing.ReadField.RINGS_WORD,0); bad.values.put(CdBigRing.ReadField.CAMERA_X_WORD,0x100);
        bad.edit(s -> s.moveByte(SPRITE_FLAGS,4).moveWord(X,0x380).moveByte(SAVED_FLAGS_ID,255)); var e=images(bad);
        var failure=assertThrows(CdBigRing.UnsupportedBigRing.class,bad::run); assertEquals(CdBigRing.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,failure.boundary());
        assertEquals(767,failure.value()); assertImages(e,bad);
    }

    @Test void warpedDespawnReadsItsOwnCopiedPeriodAndSignedDirectionWithoutNormalizingEarly() {
        for(int[] v:new int[][]{{0x80,1,0},{0x80,255,1},{0x82,1,1},{0xFF,0,2}}) {
            var h=new H(); h.values.put(CdBigRing.ReadField.RINGS_WORD,49); h.values.put(CdBigRing.ReadField.TIME_ZONE_BYTE,v[0]);
            h.values.put(CdBigRing.ReadField.TIME_WARP_DIRECTION_BYTE,v[1]); h.edit(s -> s.moveByte(SPRITE_FLAGS,0x84).moveByte(SAVED_FLAGS_ID,10));
            for(int p=0;p<3;p++)h.placement.bsetSavedFlag(10,p,CdObjectPlacementRuntime.SavedBit.LOADED);
            h.run(); for(int p=0;p<3;p++)assertEquals(p==v[2]?0:128,h.placement.savedFlag(10,p));
            assertEquals(List.of("read:RINGS_WORD","read:TIME_ZONE_BYTE","read:TIME_ZONE_BYTE","read:TIME_WARP_DIRECTION_BYTE"),h.order);
        }
    }

    @Test void drawMayOnlyClearTheSourceBitAndOriginalDrawFailureKeepsItsPartialClear() {
        var h=new H(); h.onDraw=c -> h.pool().write(h.pool().state(75).moveByte(COLLISION_STATUS,0x55));
        assertThrows(IllegalStateException.class,h::run); assertEquals(0x55,h.pool().state(75).unsignedByte(COLLISION_STATUS));
        var failed=new H(); var original=new AssertionError("draw owner"); failed.onDraw=c -> {
            var source=c.expectedSource(); failed.pool().write(source.moveByte(SPRITE_FLAGS,source.spriteFlags()&0x7F)); throw original; };
        assertSame(original,assertThrows(AssertionError.class,failed::run)); assertEquals(0,original.getSuppressed().length);
        assertEquals(4,failed.slot().spriteFlags()); assertEquals(0,failed.slot().unsignedByte(ANIMATION_TIME));
        // A new constructed component entitlement checks finally retirement, not a failed World retry.
        failed.onDraw=c -> {}; failed.run(); assertEquals(7,failed.slot().unsignedByte(ANIMATION_TIME));
    }

    private enum Owner { READ, SPECIAL, PLAYER, SCROLL, SOUND }
    private static final class H {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(emptyLayout()); final CdBigRing body;
        final EnumMap<CdBigRing.ReadField,Integer> values=new EnumMap<>(CdBigRing.ReadField.class);
        final List<String> order=new ArrayList<>(); final List<CdBigRing.SpecialStageCall> specialCalls=new ArrayList<>();
        final List<CdBigRing.SoundCall> sounds=new ArrayList<>(); final List<CdObjectState> draws=new ArrayList<>();
        final Set<CdObjectPlacementRuntime.Snapshot> cuts=Collections.newSetFromMap(new IdentityHashMap<>());
        Consumer<CdBigRing.ReadCall> onRead=c -> {}; Consumer<CdBigRing.SpecialStageCall> onSpecial=c -> {};
        Consumer<CdBigRing.PlayerStore> onPlayer=c -> {}; Consumer<CdBigRing.ScrollLockHighByteOne> onScroll=c -> {};
        Consumer<CdBigRing.SoundCall> onSound=c -> {}; Consumer<CdBigRing.DrawCall> onDraw=c -> {};
        ToIntFunction<CdBigRing.ReadCall> readOverride;
        boolean ignorePlayer;
        int playerXLong=0x1234,playerYLong=(Y0<<16)|0x4567,xv=0x1234,gv=-292,controlLock=0xA4,logical=0x1234,
                rawHeld=0x08,rawTap=0x10,special=0xA5,scrollWord=0xBEEF;
        H(){this(assets(RING,FLASH),Set.of());}
        H(CdBigRing.Assets assets,Set<Owner> missing) {
            for(var f:CdBigRing.ReadField.values())values.put(f,0);
            values.put(CdBigRing.ReadField.RINGS_WORD,50); values.put(CdBigRing.ReadField.PLAYER_X_RADIUS_BYTE,9);
            values.put(CdBigRing.ReadField.PLAYER_Y_RADIUS_BYTE,19); values.put(CdBigRing.ReadField.CAMERA_X_WORD,900);
            body=new CdBigRing(placement,assets,missing.contains(Owner.READ)?null:c -> {
                check(c.actualCut(),c.expectedSource()); order.add("read:"+c.field()); onRead.accept(c);
                if(readOverride!=null)return readOverride.applyAsInt(c);
                return switch(c.field()){case PLAYER_X_WORD -> playerXLong>>>16;case PLAYER_Y_WORD -> playerYLong>>>16;default -> values.get(c.field());};
            },missing.contains(Owner.SPECIAL)?null:c -> {
                check(c.actualCut(),c.expectedSource());order.add("special");onSpecial.accept(c);special=1;specialCalls.add(c);
            },missing.contains(Owner.PLAYER)?null:c -> {
                check(c.actualCut(),c.expectedSource());order.add("player:"+c.getClass().getSimpleName());onPlayer.accept(c);
                if(ignorePlayer)return;
                if(c instanceof CdBigRing.PlayerXWord x)playerXLong=(x.value()<<16)|(playerXLong&0xFFFF);
                else if(c instanceof CdBigRing.ControlLockBit0)controlLock|=1;
                else if(c instanceof CdBigRing.ControlHold0808)logical=0x0808;
                else if(c instanceof CdBigRing.XVelocityZero)xv=0;
                else if(c instanceof CdBigRing.GroundVelocityZero)gv=0;
                else fail("Unknown sealed player operation");
            },missing.contains(Owner.SCROLL)?null:c -> {
                check(c.actualCut(),c.expectedSource());order.add("scroll");onScroll.accept(c);scrollWord=(scrollWord&255)|0x100;
            },missing.contains(Owner.SOUND)?null:c -> {
                check(c.actualCut(),c.expectedSource());order.add("fm");onSound.accept(c);sounds.add(c);
            },c -> {
                check(c.actualCut(),c.expectedSource());order.add("draw");draws.add(c.expectedSource());onDraw.accept(c);
                var state=pool().state(c.expectedSource().identity().wholeIndex());pool().write(state.moveByte(SPRITE_FLAGS,state.spriteFlags()&0x7F));
            });
            edit(s -> s.moveByte(ID,0x14).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveWord(X,X0).moveWord(Y,Y0)
                    .moveLong(MAPPINGS,MAP).moveByte(ANIMATION_TIME,1));
        }
        CdObjectPool pool(){return placement.pool();} CdObjectState slot(){return pool().state(WHOLE);}
        void edit(UnaryOperator<CdObjectState> edit){pool().write(edit.apply(slot()));} CdBigRing.Result run(){return body.run(WHOLE);}
        void contact(){playerXLong=(X0<<16)|0x1234;playerYLong=(Y0<<16)|0x4567;}
        void full(){for(int i=32;i<128;i++)if(i!=WHOLE)pool().write(pool().state(i).moveByte(ID,0x10));}
        void check(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source){
            placement.validateRestore(cut);assertEquals(placement.status(),cut.status());assertArrayEquals(placement.capture().savedFlags().copy(),cut.savedFlags().copy());
            assertEquals(pool().capture().slots(),cut.pool().slots());assertEquals(pool().state(source.identity().wholeIndex()),source);assertTrue(cuts.add(cut),"Every port gets a fresh actual cut");
        }
    }
    private static CdBigRing.Assets assets(byte[] ring,byte[] flash){
        return new CdBigRing.Assets(CdObjectVisualAssets.decodeStatic(emptyMaps(4),4,106),MAP,CdAssetBytes.copyOf(ring),
                CdObjectVisualAssets.decodeStatic(emptyMaps(8),8,77),FLASH_MAP,CdAssetBytes.copyOf(flash));
    }
    private static byte[] emptyMaps(int frames){var b=ByteBuffer.allocate(frames*3);for(int i=0;i<frames;i++)b.putShort((short)(frames*2+i));return b.array();}
    private static CdObjectPlacements emptyLayout(){return CdObjectPlacements.decodeLayout(hex("000c0014ffff000000000000ffff000000000000ffff00000000"));}
    private static List<byte[]> images(H h){var result=new ArrayList<byte[]>();for(int i=32;i<128;i++)result.add(h.pool().state(i).copyBytes());return result;}
    private static void assertImages(List<byte[]> expected,H h){for(int i=0;i<96;i++)assertArrayEquals(expected.get(i),h.pool().state(i+32).copyBytes(),"whole"+(i+32));}
    private static CdObjectState dirty(CdObjectState s){for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,0x53+7*f.byteOffset());for(var f:CdObjectState.WordField.values())s=s.moveWord(f,0xA153+0x103*f.byteOffset());for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0xA1536729+0x01030507*f.byteOffset());return s;}
    private static void w(byte[] b,int at,int n){b[at]=(byte)(n>>>8);b[at+1]=(byte)n;} private static void l(byte[] b,int at,int n){w(b,at,n>>>16);w(b,at+2,n);}
    private static byte[] hex(String text){return HexFormat.of().parseHex(text);}
}
