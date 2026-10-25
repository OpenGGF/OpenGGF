package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests-first frozen source candidate; ALL JAVA UNRUN. Native source pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Monitor and Time Post/Main.asm:64-236,
 * Object Animate.asm:14-95, Object Spawner.asm:385-446, System.inc:144-274 (including anonymous183).
 * Constructed format fixtures copied from J's TestCdMonitor, not retail-media authentication.
 * Draw callback models only native BCLR7 plus explicitly selected test writes; no rendering claim.
 * Requires Root's one ByteField.TIME_POST_SPIN(0x2A) alias; no State edit belongs to this child.
 * OpenAI Codex GPT-6.1 Sol <noreply@openai.com>.
 */
class TestCdTimePost {
    static final int WHOLE = 40, MAP = 0x00CD1000;
    static final String MAP_HEX = "0028003e0054006a0080009600ac00c200d800ee0104011e01380152016c018601a001b001b601bc04f6050012f8f0060000f0f006080000080c0006f00004f6050016f8f0060000f0f006080000080c0006f00004f605001af8f0060000f0f006080000080c0006f00004f605001ef8f0060000f0f006080000080c0006f00004f6050022f8f0060000f0f006080000080c0006f00004f6050026f8f0060000f0f006080000080c0006f00004f605002af8f0060000f0f006080000080c0006f00004f605002ef8f0060000f0f006080000080c0006f00004f6050048f8f0060000f0f006080000080c0006f00004f6050848f8f0060000f0f006080000080c0006f00005d80d0032f0e803004cf8e803084c0008010050f8080108500005d80d0832f0e803004cf8e803084c0008010050f8080108500005d80d003af0e803004cf8e803084c0008010050f8080108500005d80d083af0e803004cf8e803084c0008010050f8080108500005d8050042f8e803004cf8e803084c0008010050f8080108500005d8010046fce803004cf8e803084c0008010050f8080108500003f0060000f0f006080000080c0006f001000d000af001f80d0032f001f80d003af0";
    static final String ANI_HEX = "00140020002c003800440050005c00680074007e01100000080000090000ff0001100101080101090101ff0001100202080202090202ff0001100303080303090303ff0001100404080404090404ff0001100505080505090505ff0001100606080606090606ff0001100707080707090707ff00010a0e0f0e0b0e0f0eff010c0e0f0e0d0e0f0eff";

    @Test void storageAndSubCpuIdsUseAllRsDeclarationsIncludingAnonymous183() {
        assertEquals(0x2A, TIME_POST_SPIN.byteOffset());
        assertEquals(0x77, CdTimePost.SubCpuCommand.SCMD_PASTSFX.nativeId());
        assertEquals(0x76, CdTimePost.SubCpuCommand.SCMD_FUTURESFX.nativeId());
        var h = prepared(2, 8); h.edit(o -> o.moveWord(MOSQUI_ANCHOR_X, 0xABCD));
        h.edit(o -> o.moveByte(TIME_POST_SPIN, 60));
        assertEquals(0x3CCD, h.slot().word(MOSQUI_ANCHOR_X));
    }

    @ParameterizedTest @ValueSource(ints = {8,9})
    void newInitPreservesEveryUnwrittenByteAndFallsThroughWithoutPrimingAnimation(int subtype) {
        var h = prepared(0, subtype); h.edit(o -> dirty(o).moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype)
                .moveByte(COLLISION_STATUS,0).moveByte(SAVED_FLAGS_ID,7));
        h.flag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        h.flag(7,1,CdObjectPlacementRuntime.SavedBit.MEMBER_2);
        var before = h.slot(); var r = h.run();
        var expected = initPrefix(before,subtype).moveByte(MAP_FRAME,subtype==8?10:12).moveByte(COLLISION_TYPE,0xDF);
        assertEquals(expected,r.state()); assertEquals(expected,h.draws.getFirst());
        assertEquals(2,h.runtime.savedFlag(7,1)); assertEquals(List.of("DRAW"),h.order);
        assertEquals(0,count(r,CdTimePost.NativeCall.ANIMATE_OBJECT));
        assertEquals(0xABCD,r.state().word(MOSQUI_ANCHOR_X));
        assertEquals(0x4321,r.state().word(X_FRACTION)); assertEquals(0x8765,r.state().word(Y_FRACTION));
        assertTrue(r.effects().stream().anyMatch(e -> e instanceof CdTimePost.SavedFlagChanged f
                && f.previousValue()==130 && f.value()==2));
    }

    @ParameterizedTest @ValueSource(ints = {8,9})
    void activatedInitReturnsToWrapperWithoutConsumingDirtyContactOrWritingCollision(int subtype) {
        var h=prepared(0,subtype); h.edit(o -> dirty(o).moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype)
                .moveByte(SAVED_FLAGS_ID,7));
        h.flag(7,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1); h.flag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        var before=h.slot(); var r=h.run();
        assertEquals(initPrefix(before,subtype).moveByte(MAP_FRAME,subtype==8?11:13).moveByte(ROUTINE,6),r.state());
        assertEquals(0x55,r.state().unsignedByte(COLLISION_STATUS)); assertEquals(0x66,r.state().unsignedByte(COLLISION_TYPE));
        assertEquals(List.of("DRAW"),h.order); assertEquals(1,count(r,CdTimePost.NativeCall.CHECK_OBJ_DESPAWN));
        assertEquals(1,h.runtime.savedFlag(7,1));
    }

    @ParameterizedTest @CsvSource({"0,0,1448,3","6,0,34216,0","6,1,1448,3","6,255,1448,3","5,0,1448,3"})
    void initMmzPriorityReadsObjectLayerWithoutRequiringPlayerLayer(int zone,int objectLayer,int tile,int priority) {
        var h=prepared(0,8); h.zone=zone; h.edit(o -> o.moveByte(LAYER,objectLayer)); h.run();
        assertEquals(tile,h.slot().word(TILE)); assertEquals(priority,h.slot().unsignedByte(PRIORITY));
    }

    @ParameterizedTest @ValueSource(ints = {8,9,10,11,255})
    void timeAttackDeletesBeforeHudAndRoutineLookupWithoutDrawOrSavedAccess(int subtype) {
        var h=prepared(255,subtype); h.attack=255; h.edit(o -> dirty(o).moveByte(ROUTINE,255)
                .moveByte(SUBTYPE,subtype).moveByte(SAVED_FLAGS_ID,255));
        var bank=h.runtime.capture().savedFlags().copy(); var r=h.run();
        assertTrue(r.deleted()); assertArrayEquals(new byte[64],r.state().copyBytes());
        assertArrayEquals(bank,h.runtime.capture().savedFlags().copy()); assertTrue(h.order.isEmpty());
    }

    @Test void hudSubtypeTenIsAnExplicitReservedWhole31BoundaryBeforeEntryWrites() {
        var h=prepared(255,10); var before=h.slot();
        assertEquals(CdTimePost.UnsupportedBoundary.TIME_ICON_RESERVED_SLOT,
                assertThrows(CdTimePost.UnsupportedTimePost.class,h::run).boundary());
        assertEquals(before,h.slot()); assertTrue(h.order.isEmpty());
        assertEquals(96,h.pool().capture().slots().size());
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6,7})
    void monitorSubtypesRemainWithTheirExistingOwner(int subtype) {
        var h=prepared(0,subtype); h.attack=1; var before=h.slot();
        assertEquals(CdTimePost.UnsupportedBoundary.MONITOR_BRANCH,
                assertThrows(CdTimePost.UnsupportedTimePost.class,h::run).boundary());
        assertEquals(before,h.slot()); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1,3,5,7,8,128,255})
    void rawRoutineOffsetsAreNeitherMaskedNorRepaired(int routine) {
        var h=prepared(routine,8); var before=h.slot();
        var e=assertThrows(CdTimePost.UnsupportedTimePost.class,h::run);
        assertEquals(CdTimePost.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());
        assertEquals(routine,e.value()); assertEquals(WHOLE,e.wholeIndex());
        assertEquals(before,h.slot()); assertTrue(h.order.isEmpty());
    }

    @Test void mainWithoutContactDoesNotReadMissingMmzLayer() {
        var h=prepared(2,8); h.zone=6; var before=h.slot(); var r=h.run();
        assertEquals(before.moveByte(SPRITE_FLAGS,4),r.state()); assertEquals(List.of("DRAW"),h.order);
    }

    @ParameterizedTest @CsvSource({"0,1","1,0","255,0","0,255"})
    void mismatchedMmzLayerClearsContactBeforeReturningWithoutActivation(int objectLayer,int playerLayer) {
        var h=prepared(2,8); h.zone=6; h.layer=OptionalInt.of(playerLayer);
        h.edit(o -> o.moveByte(LAYER,objectLayer).moveByte(COLLISION_STATUS,255)); var before=h.slot(); var r=h.run();
        assertEquals(before.moveByte(COLLISION_STATUS,0).moveByte(SPRITE_FLAGS,4),r.state());
        assertEquals(List.of("DRAW"),h.order); assertEquals(0,h.runtime.savedFlag(7,1));
    }

    @Test void missingMmzLayerStopsAtReachedReadAfterContactWasCleared() {
        var h=prepared(2,8); h.zone=6; h.edit(o -> o.moveByte(COLLISION_STATUS,7)); var before=h.slot();
        var e=assertThrows(CdTimePost.UnsupportedTimePost.class,h::run);
        assertEquals(CdTimePost.UnsupportedBoundary.MMZ_LAYER_READ,e.boundary());
        assertEquals(before.moveByte(COLLISION_STATUS,0),h.slot()); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @CsvSource({"0,0","1,1","1,255","255,1"})
    void matchedMmzLayersUseZeroVersusNonzeroRatherThanExactByteEquality(int objectLayer,int playerLayer) {
        var h=prepared(2,8); h.zone=6; h.layer=OptionalInt.of(playerLayer);
        h.edit(o -> o.moveByte(LAYER,objectLayer).moveByte(COLLISION_STATUS,1)); h.run();
        assertEquals(4,h.slot().routine()); assertEquals(255,h.direction); assertEquals(1,h.voices.size());
    }

    @ParameterizedTest @ValueSource(ints = {0,5,7,255})
    void nonMmzContactNeverRequiresOrInventsPlayerLayer(int zone) {
        var h=prepared(2,8); h.zone=zone; h.edit(o -> o.moveByte(LAYER,255).moveByte(COLLISION_STATUS,1)); h.run();
        assertEquals(4,h.slot().routine()); assertEquals(255,h.direction);
    }

    @ParameterizedTest @ValueSource(ints = {8,9})
    void activationPublishesSavedFlagAndAllNativeDirectionStoresBeforeVoiceAndDraw(int subtype) {
        var h=prepared(2,subtype); h.direction=0x80;
        h.edit(o -> dirty(o).moveByte(ROUTINE,2).moveByte(SUBTYPE,subtype).moveByte(COLLISION_STATUS,0xFF)
                .moveByte(SAVED_FLAGS_ID,7));
        h.flag(7,1,CdObjectPlacementRuntime.SavedBit.MEMBER_2); var before=h.slot(); var r=h.run();
        var prefix=before.moveByte(COLLISION_STATUS,0).moveByte(TIME_POST_SPIN,60).moveByte(ROUTINE,4);
        assertEquals(prefix.moveByte(SPRITE_FLAGS,prefix.spriteFlags()&127),r.state());
        assertEquals(prefix,h.directionImages.getFirst()); assertEquals(prefix,h.voiceImages.getFirst());
        assertEquals(3,h.bankAtDirection.getFirst().intValue()); assertEquals(3,h.runtime.savedFlag(7,1));
        assertEquals(subtype==8?List.of("DIR:128>255","VOICE:119","DRAW"):
                List.of("DIR:128>255","DIR:255>1","VOICE:118","DRAW"),h.order);
        assertEquals(subtype==8?255:1,h.direction); assertEquals(0x66,h.slot().unsignedByte(COLLISION_TYPE));
        assertEquals(0x12AB,h.slot().word(MONITOR_ITEM_DELETE)); assertEquals(0x3CCD,h.slot().word(MOSQUI_ANCHOR_X));
        assertEquals(1,r.effects().stream().filter(CdTimePost.SubCpuRequest.class::isInstance).count());
    }

    @Test void sameValueDirectionAndSavedFlagStoresRemainCompletedObservations() {
        var h=prepared(2,8); h.direction=255; h.flag(7,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
        h.edit(o -> o.moveByte(COLLISION_STATUS,1)); var r=h.run();
        assertEquals(List.of("DIR:255>255","VOICE:119","DRAW"),h.order);
        assertEquals(1,r.effects().stream().filter(CdTimePost.DirectionCommitted.class::isInstance).count());
        assertTrue(r.effects().stream().anyMatch(e -> e instanceof CdTimePost.SavedFlagChanged f
                && f.previousValue()==1 && f.value()==1));
    }

    @Test void newInitWithDirtyContactActivatesInTheSameDispatchAndDoesNotAnimate() {
        var h=prepared(0,9); h.edit(o -> o.moveByte(COLLISION_STATUS,3));
        h.flag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED); var r=h.run();
        assertEquals(4,r.state().routine()); assertEquals(60,r.state().unsignedByte(TIME_POST_SPIN));
        assertEquals(0xDF,r.state().unsignedByte(COLLISION_TYPE)); assertEquals(12,r.state().unsignedByte(MAP_FRAME));
        assertEquals(1,h.runtime.savedFlag(7,1)); assertEquals(0,count(r,CdTimePost.NativeCall.ANIMATE_OBJECT));
        assertEquals(List.of("DIR:0>255","DIR:255>1","VOICE:118","DRAW"),h.order);
    }

    @Test void firstDirectionRejectionRetainsObjectAndSavedPrefixWithoutVoiceOrDraw() {
        var h=prepared(2,8); h.edit(o -> o.moveByte(COLLISION_STATUS,1)); h.rejectDirection=1;
        assertThrows(IllegalStateException.class,h::run);
        assertEquals(4,h.slot().routine()); assertEquals(60,h.slot().unsignedByte(TIME_POST_SPIN));
        assertEquals(0,h.slot().unsignedByte(COLLISION_STATUS)); assertEquals(1,h.runtime.savedFlag(7,1));
        assertEquals(0,h.direction); assertTrue(h.voices.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void futureSecondDirectionRejectionRetainsCompletedFirstStoreAndThreadedExpectedByte() {
        var h=prepared(2,9); h.direction=0x80; h.edit(o -> o.moveByte(COLLISION_STATUS,1)); h.rejectDirection=2;
        assertThrows(IllegalStateException.class,h::run);
        assertEquals(List.of("DIR:128>255","DIR:255>1"),h.order); assertEquals(255,h.direction);
        assertEquals(1,h.runtime.savedFlag(7,1)); assertTrue(h.voices.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void voiceRejectionRetainsBothDirectionStoresAndNeverDrawsOrRetries() {
        var h=prepared(2,9); h.edit(o -> o.moveByte(COLLISION_STATUS,1)); h.rejectVoice=true;
        assertThrows(IllegalStateException.class,h::run);
        assertEquals(List.of("DIR:0>255","DIR:255>1","VOICE:118"),h.order);
        assertEquals(1,h.direction); assertEquals(1,h.voices.size()); assertTrue(h.draws.isEmpty());
    }

    @Test void drawFailurePreservesActualOrderedDirectionStoresAndOneCompletedVoiceWithoutRetry() {
        var h=prepared(2,9); h.edit(o -> o.moveByte(COLLISION_STATUS,1)); h.rejectDraw=true;
        h.flag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        assertThrows(IllegalStateException.class,h::run);
        assertEquals(List.of("DIR:0>255","DIR:255>1","VOICE:118","DRAW"),h.order);
        assertEquals(1,h.direction); assertEquals(1,h.voices.size()); assertEquals(1,h.draws.size());
        assertEquals(129,h.runtime.savedFlag(7,1)); assertEquals(4,h.slot().routine());
    }

    @Test void directionPortCannotOverwriteThePublishedObjectPrefix() {
        var h=prepared(2,8); h.edit(o -> o.moveByte(COLLISION_STATUS,1)); h.mutateAtDirection=true;
        assertThrows(IllegalStateException.class,h::run); assertEquals(255,h.direction);
        assertTrue(h.voices.isEmpty()); assertTrue(h.draws.isEmpty()); assertEquals(0xA5,h.slot().flags());
    }

    @Test void voicePortCannotOverwriteThePublishedObjectPrefix() {
        var h=prepared(2,8); h.edit(o -> o.moveByte(COLLISION_STATUS,1)); h.mutateAtVoice=true;
        assertThrows(IllegalStateException.class,h::run); assertEquals(1,h.voices.size());
        assertTrue(h.draws.isEmpty()); assertEquals(0xA5,h.slot().flags());
    }

    @ParameterizedTest @CsvSource({"60,59","2,1","0,255","128,127","255,254"})
    void spinDecrementsByteBeforeSharedAnimationAndPreservesAdjacentLowByte(int spin,int decremented) {
        var h=prepared(4,8); h.edit(o -> o.moveWord(MOSQUI_ANCHOR_X,(spin<<8)|0xCD)
                .moveByte(ANIMATION,8).moveByte(PREVIOUS_ANIMATION,8).moveByte(ANIMATION_TIME,5)
                .moveByte(COLLISION_STATUS,0xE2)); var r=h.run();
        assertEquals((decremented<<8)|0xCD,r.state().word(MOSQUI_ANCHOR_X));
        assertEquals(4,r.state().unsignedByte(ANIMATION_TIME)); assertEquals(0xE2,r.state().unsignedByte(COLLISION_STATUS));
        assertEquals(1,count(r,CdTimePost.NativeCall.ANIMATE_OBJECT)); assertEquals(List.of("DRAW"),h.order);
    }

    @ParameterizedTest @ValueSource(ints = {8,9})
    void spinOneStopsWithoutAnyAnimationCallAndKeepsDirtyAnimationBytes(int subtype) {
        var h=prepared(4,subtype); h.edit(o -> o.moveByte(TIME_POST_SPIN,1).moveByte(ANIMATION,255)
                .moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_TIME,0xA7)); var before=h.slot(); var r=h.run();
        assertEquals(before.moveByte(TIME_POST_SPIN,0).moveByte(ROUTINE,6).moveByte(MAP_FRAME,subtype==8?11:13)
                .moveByte(SPRITE_FLAGS,4),r.state());
        assertEquals(0,count(r,CdTimePost.NativeCall.ANIMATE_OBJECT));
    }

    @ParameterizedTest @ValueSource(ints = {8,9})
    void spinningUsesFullNativePostSequenceAndHasNoTimeStopInput(int subtype) {
        var h=prepared(4,subtype); h.edit(o -> o.moveByte(TIME_POST_SPIN,60).moveByte(ANIMATION,subtype)
                .moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,0));
        int[] frames=subtype==8?new int[]{10,14,15,14,11,14,15,14,10}:new int[]{12,14,15,14,13,14,15,14,12};
        for(int frame:frames) {
            assertEquals(frame,h.run().state().unsignedByte(MAP_FRAME));
            assertEquals(frame,h.run().state().unsignedByte(MAP_FRAME));
        }
        assertEquals(42,h.slot().unsignedByte(TIME_POST_SPIN)); assertTrue(h.voices.isEmpty());
        assertEquals(List.of("rawTimeZone","rawTimeWarpDirection","cameraXWord","timeAttackMode","zone","layer"),
                java.util.Arrays.stream(CdTimePost.Globals.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test void doneDoesNotConsumeContactOrReadLayerOrAnimate() {
        var h=prepared(6,8); h.zone=6; h.edit(o -> dirty(o).moveByte(ROUTINE,6)); var before=h.slot(); var r=h.run();
        assertEquals(before.moveByte(SPRITE_FLAGS,before.spriteFlags()&127),r.state());
        assertEquals(0,count(r,CdTimePost.NativeCall.ANIMATE_OBJECT)); assertEquals(List.of("DRAW"),h.order);
    }

    @Test void rawInvalidSubtypeIsNotMaskedAndStopsOnlyWhenItsAnimationDirectoryIsRead() {
        var h=prepared(0,11); h.run(); assertEquals(11,h.slot().unsignedByte(ANIMATION));
        assertEquals(12,h.slot().unsignedByte(MAP_FRAME)); h.edit(o -> o.moveByte(COLLISION_STATUS,1)); h.run();
        assertEquals(1,h.direction); assertEquals(CdTimePost.SubCpuCommand.SCMD_FUTURESFX,h.voices.getFirst());
        h.order.clear(); var e=assertThrows(CdTimePost.UnsupportedTimePost.class,h::run);
        assertEquals(CdTimePost.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY,e.boundary()); assertEquals(22,e.value());
        assertEquals(59,h.slot().unsignedByte(TIME_POST_SPIN)); assertEquals(11,h.slot().unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(0,h.slot().unsignedByte(ANIMATION_FRAME)); assertEquals(255,h.slot().unsignedByte(ANIMATION_TIME));
        assertTrue(h.order.isEmpty());
    }

    @Test void invalidAnimationDirectoryIsNotReadWhileNativeTimerRemainsNonnegative() {
        var h=prepared(4,8); h.edit(o -> o.moveByte(TIME_POST_SPIN,60).moveByte(ANIMATION,255)
                .moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_TIME,1));
        h.run(); assertEquals(0,h.slot().unsignedByte(ANIMATION_TIME)); h.order.clear();
        var e=assertThrows(CdTimePost.UnsupportedTimePost.class,h::run);
        assertEquals(CdTimePost.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY,e.boundary()); assertEquals(510,e.value());
        assertEquals(58,h.slot().unsignedByte(TIME_POST_SPIN)); assertEquals(255,h.slot().unsignedByte(ANIMATION_TIME));
        assertTrue(h.order.isEmpty());
    }

    @Test void outOfViewAnimationFrameRetainsDecrementAndActualSpeedBytePrefix() {
        var h=prepared(4,8); h.edit(o -> o.moveByte(TIME_POST_SPIN,60).moveByte(ANIMATION,8)
                .moveByte(PREVIOUS_ANIMATION,8).moveByte(ANIMATION_TIME,0).moveByte(ANIMATION_FRAME,255));
        var e=assertThrows(CdTimePost.UnsupportedTimePost.class,h::run);
        assertEquals(CdTimePost.UnsupportedBoundary.ANIMATION_FRAME_ADDRESS,e.boundary()); assertEquals(372,e.value());
        assertEquals(59,h.slot().unsignedByte(TIME_POST_SPIN)); assertEquals(1,h.slot().unsignedByte(ANIMATION_TIME));
        assertEquals(255,h.slot().unsignedByte(ANIMATION_FRAME)); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @CsvSource({"1,0,7,1","130,1,7,1","128,1,7,0","130,255,7,2","4,0,8,1","127,0,49,1","129,128,7,2"})
    void savedHelperUsesRawTimeByteAndSignExtendedWarpDirectionInTheOneBank(int time,int direction,int id,int period) {
        var h=prepared(0,8); h.time=time; h.direction=direction; h.flag(id,period,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
        h.flag(id,period,CdObjectPlacementRuntime.SavedBit.LOADED); h.run();
        assertEquals(1,h.runtime.savedFlag(id,period)); assertEquals(766,h.runtime.capture().savedFlags().length());
        assertEquals(1,h.draws.size()); // Warping may delete afterwards, but saved Init still reaches DrawObject.
    }

    @Test void invalidInitSavedAddressRetainsOnlyActualInitPrefixIncludingSubtypeAnimation() {
        var h=prepared(0,8); h.edit(o -> dirty(o).moveByte(ROUTINE,0).moveByte(SUBTYPE,8).moveByte(SAVED_FLAGS_ID,255));
        var before=h.slot(); var bank=h.runtime.capture().savedFlags().copy();
        var e=assertThrows(CdTimePost.UnsupportedTimePost.class,h::run);
        assertEquals(CdTimePost.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,e.boundary()); assertEquals(768,e.value());
        assertEquals(initPrefix(before,8),h.slot()); assertArrayEquals(bank,h.runtime.capture().savedFlags().copy());
        assertTrue(h.order.isEmpty());
    }

    @Test void invalidActivationSavedAddressRetainsContactClearSpinAndRoutineBeforeAnyDirectionOrVoice() {
        var h=prepared(2,8); h.edit(o -> o.moveByte(SAVED_FLAGS_ID,255).moveByte(COLLISION_STATUS,1)); var before=h.slot();
        assertEquals(CdTimePost.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,
                assertThrows(CdTimePost.UnsupportedTimePost.class,h::run).boundary());
        assertEquals(before.moveByte(COLLISION_STATUS,0).moveByte(TIME_POST_SPIN,60).moveByte(ROUTINE,4),h.slot());
        assertEquals(0,h.direction); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @CsvSource({"254,1,true","254,2,false","255,0,false"})
    void savedBankHasExactFiniteLastByteRatherThan256TimesThree(int savedId,int time,boolean valid) {
        var h=prepared(0,8); h.time=time; h.edit(o -> o.moveByte(SAVED_FLAGS_ID,savedId));
        if(valid) { h.run(); assertEquals(2,h.slot().routine()); }
        else assertEquals(CdTimePost.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,
                assertThrows(CdTimePost.UnsupportedTimePost.class,h::run).boundary());
        assertEquals(766,h.runtime.capture().savedFlags().length());
    }

    @Test void idZeroIsReadByInitButSkippedOnlyByDespawn() {
        var h=prepared(0,8); h.edit(o -> o.moveByte(SAVED_FLAGS_ID,0)); h.flag(0,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        h.run(); assertEquals(0,h.runtime.savedFlag(0,1)); h.flag(0,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        h.edit(o -> o.moveByte(ROUTINE,6)); h.camera=0x6000;
        assertTrue(h.run().deleted()); assertEquals(128,h.runtime.savedFlag(0,1));
    }

    @ParameterizedTest @CsvSource({"2560,false","2688,true"})
    void despawnUsesUnsignedChunkThresholdAndRetainsActivatedFlag(int x,boolean deleted) {
        var h=prepared(6,8); h.camera=0x800; h.edit(o -> o.moveWord(X,x));
        h.flag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED); h.flag(7,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
        assertEquals(deleted,h.run().deleted()); assertEquals(deleted?1:129,h.runtime.savedFlag(7,1));
        assertEquals(1,h.draws.size());
    }

    @Test void despawnReloadsActualDrawVisibilityRatherThanUsingPriorBitSeven() {
        var h=prepared(6,8); h.camera=0x6000; h.drawFlags=0x84; assertFalse(h.run().deleted());
        h.drawFlags=4; assertTrue(h.run().deleted());
    }

    @Test void warpDespawnUsesNewlyCommittedDirectionInsteadOfStaleEntryDirection() {
        var h=prepared(2,9); h.time=0x81; h.direction=0;
        h.edit(o -> o.moveByte(COLLISION_STATUS,1)); h.flag(7,0,CdObjectPlacementRuntime.SavedBit.LOADED);
        h.flag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED); var r=h.run();
        assertTrue(r.deleted()); assertEquals(1,h.direction); assertEquals(0,h.runtime.savedFlag(7,0));
        assertEquals(129,h.runtime.savedFlag(7,1)); assertEquals(List.of("DIR:0>255","DIR:255>1","VOICE:118","DRAW"),h.order);
    }

    @Test void restoreOfExistingPoolAndSavedBankDoesNotReplayAnyCompletedPorts() {
        var h=prepared(2,8); h.edit(o -> o.moveByte(COLLISION_STATUS,1)); var before=h.runtime.capture();
        h.run(); var order=List.copyOf(h.order); h.runtime.restore(before);
        assertEquals(before.pool().slots(),h.pool().capture().slots());
        assertArrayEquals(before.savedFlags().copy(),h.runtime.capture().savedFlags().copy()); assertEquals(order,h.order);
        assertEquals(255,h.direction); // Frame direction is a DIFFERENT owner; no whole-world rewind claim.
    }

    @Test void resultMetadataIsImmutableAndNullPortsNeverBecomeNoops() {
        var h=prepared(6,8); var r=h.run(); assertThrows(UnsupportedOperationException.class,()->r.effects().clear());
        assertThrows(NullPointerException.class,()->new CdTimePost(h.runtime,assets(),null,h::voice,h::draw));
        assertThrows(NullPointerException.class,()->new CdTimePost(h.runtime,assets(),h::direction,null,h::draw));
        assertThrows(NullPointerException.class,()->new CdTimePost(h.runtime,assets(),h::direction,h::voice,null));
        assertThrows(IllegalArgumentException.class,()->new CdTimePost.Globals(256,0,0,0,0,OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class,()->new CdTimePost.Globals(0,0,0,0,6,OptionalInt.of(256)));
    }

    static long count(CdTimePost.Result r,CdTimePost.NativeCall call) {
        return r.effects().stream().filter(e -> e instanceof CdTimePost.CallReturned x && x.call()==call).count();
    }
    static CdObjectState initPrefix(CdObjectState before,int subtype) {
        return before.addByte(ROUTINE,2).moveByte(Y_RADIUS,0x20).moveByte(X_RADIUS,14).moveLong(MAPPINGS,MAP)
                .moveWord(TILE,0x5A8).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3).moveByte(WIDTH,15).moveByte(ANIMATION,subtype);
    }
    static Harness prepared(int routine,int subtype) { var h=new Harness(); h.prepare(routine,subtype); return h; }
    static CdMonitor.Assets assets() {
        return new CdMonitor.Assets(CdObjectVisualAssets.decode(HexFormat.of().parseHex(MAP_HEX),20,
                HexFormat.of().parseHex(ANI_HEX),10,82),MAP);
    }
    static CdObjectPlacements layout() {
        var b=ByteBuffer.allocate(34);
        b.putShort((short)12).putShort((short)28).putShort((short)0xFFFF).putInt(0).putShort((short)0);
        b.putShort((short)0x270).putShort((short)0x778).put((byte)0x99).put((byte)8).put((byte)0xE0).put((byte)0);
        b.putShort((short)0xFFFF).putInt(0).putShort((short)0); b.putShort((short)0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }
    static CdObjectState dirty(CdObjectState s) {
        return s.moveWord(TILE,0x6789).moveLong(MAPPINGS,0x12345678).moveWord(X_FRACTION,0x4321)
                .moveWord(Y_FRACTION,0x8765).moveWord(X_VELOCITY,0x1111).moveWord(Y_VELOCITY,0x2222)
                .moveByte(Y_RADIUS,7).moveByte(X_RADIUS,9).moveByte(PRIORITY,5).moveByte(WIDTH,11)
                .moveByte(MAP_FRAME,7).moveByte(ANIMATION_FRAME,5).moveByte(ANIMATION,3).moveByte(PREVIOUS_ANIMATION,9)
                .moveWord(MONITOR_ITEM_DELETE,0x12AB).moveByte(COLLISION_TYPE,0x66).moveByte(COLLISION_STATUS,0x55)
                .moveByte(FLAGS,0x35).moveByte(ROUTINE,6).moveByte(ROUTINE_2,0x44).moveByte(ANGLE,0x56)
                .moveByte(LAYER,0).moveWord(MOSQUI_ANCHOR_X,0xABCD).moveLong(MOSQUI_X_DELTA,0x23456789)
                .moveLong(MOSQUI_ANIMATION_SCRIPT,0x3456789A).moveWord(RING_ANCHOR_X,0x4567)
                .moveWord(SPRING_ANCHOR_X,0x5678).moveWord(TUNNEL_DOOR_TIMER,0x6789).moveWord(EXPLOSION_POINTS_FRAME,0x789A);
    }
    static final class Harness {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(layout());
        final CdTimePost body=new CdTimePost(runtime,assets(),this::direction,this::voice,this::draw);
        final List<String> order=new ArrayList<>();
        final List<CdObjectState> directionImages=new ArrayList<>(),voiceImages=new ArrayList<>(),draws=new ArrayList<>();
        final List<Integer> bankAtDirection=new ArrayList<>();
        final List<CdTimePost.SubCpuCommand> voices=new ArrayList<>();
        int time=1,direction=0,camera=0x200,attack=0,zone=0,drawFlags=-1,rejectDirection=0,directionCalls=0;
        OptionalInt layer=OptionalInt.empty();
        boolean rejectVoice,rejectDraw,mutateAtDirection,mutateAtVoice;
        Harness() { runtime.resetSavedObjectFlags(); }
        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> f) { pool().write(f.apply(slot())); }
        void flag(int id,int period,CdObjectPlacementRuntime.SavedBit bit) { runtime.bsetSavedFlag(id,period,bit); }
        void prepare(int routine,int subtype) {
            edit(o -> o.moveByte(ID,0x19).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype).moveByte(SAVED_FLAGS_ID,7)
                    .moveByte(SPRITE_FLAGS,0x84).moveWord(X,0x270).moveWord(Y,0x778).moveByte(Y_RADIUS,32)
                    .moveByte(X_RADIUS,14).moveByte(WIDTH,15).moveByte(PRIORITY,3).moveByte(ANIMATION,subtype)
                    .moveByte(MAP_FRAME,subtype==8?10:12).moveByte(COLLISION_TYPE,0xDF).moveLong(MAPPINGS,MAP));
        }
        CdTimePost.Result run() { return body.run(WHOLE,new CdTimePost.Globals(time,direction,camera,attack,zone,layer)); }
        void direction(CdTimePost.DirectionCall call) {
            assertEquals(slot().identity(),call.identity()); assertEquals(direction,call.expectedRawDirection());
            order.add("DIR:"+call.expectedRawDirection()+">"+call.rawDirection());
            directionImages.add(slot()); bankAtDirection.add(runtime.savedFlag(7,1));
            if(++directionCalls==rejectDirection) throw new IllegalStateException("constructed direction rejection");
            direction=call.rawDirection(); if(mutateAtDirection) edit(o -> o.moveByte(FLAGS,0xA5));
        }
        void voice(CdTimePost.SubCpuCommand command) {
            order.add("VOICE:"+command.nativeId()); voices.add(command); voiceImages.add(slot());
            if(rejectVoice) throw new IllegalStateException("constructed voice rejection");
            if(mutateAtVoice) edit(o -> o.moveByte(FLAGS,0xA5));
        }
        void draw(CdObjectPool pool,CdObjectState.Identity identity) {
            assertSame(pool(),pool); assertEquals(slot().identity(),identity); order.add("DRAW"); draws.add(slot());
            if(rejectDraw) throw new IllegalStateException("constructed draw rejection");
            edit(o -> o.moveByte(SPRITE_FLAGS,drawFlags>=0?drawFlags:o.spriteFlags()&127));
        }
    }
}
