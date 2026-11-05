package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.CdPlayerMovement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests-first external candidate, ALL JAVA UNRUN. Native N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Monitor and Time Post/Main.asm:419-638 and Data/Mappings.asm:1-176;
 * Object Functions.asm:91-128. Reward values remain Root-owned; callback fixtures record completed cuts.
 * OpenAI Codex GPT-6.1 Sol <noreply@openai.com>.
 */
class TestCdMonitorItem {
    static final int WHOLE=41;

    @ParameterizedTest @CsvSource({"0,41", "1,63", "2,85", "3,107", "4,129", "5,151", "6,173", "7,195",
            "128,41", "129,63", "132,129", "135,195"})
    void initUsesAddByteWrappedRawIndexAndOddFirstPiecePointer(int animation,int offset) {
        var h=prepared(0); h.edit(o -> o.moveByte(ANIMATION,animation)); var r=h.run();
        assertEquals(TestCdMonitor.MAP+offset,r.state().longBits(MAPPINGS)); assertEquals(1,r.state().longBits(MAPPINGS)&1);
        assertEquals(animation,r.state().unsignedByte(MAP_FRAME)); assertEquals(0x24,r.state().spriteFlags());
        assertEquals(0x85A8,r.state().word(TILE)); assertEquals(3,r.state().unsignedByte(PRIORITY));
        assertEquals(8,r.state().unsignedByte(WIDTH)); assertEquals(2,r.state().routine());
        assertEquals(0x02D50000,r.state().longBits(Y_POSITION)); assertEquals(-0x2E8,r.state().signedWord(Y_VELOCITY));
        assertTrue(h.awards.isEmpty()); assertEquals(List.of("DRAW"),h.order);
    }

    @Test void rawIndexCanReadMappingBodyWordWithoutInventedFrameMaskOrEarlyTargetRead() {
        var h=prepared(0); h.edit(o -> o.moveByte(ANIMATION,20)); h.run();
        // ADD.B 20,20 => 40. The retained include bytes at40 are 04 F6, not a frame-directory word.
        assertEquals(TestCdMonitor.MAP+0x4F7,h.slot().longBits(MAPPINGS));
        assertEquals(20,h.slot().unsignedByte(MAP_FRAME)); assertEquals(1,h.draws.size());
    }

    @ParameterizedTest @ValueSource(ints={1,2,255})
    void nonzeroLayerOnlyClearsTheHighTilePriorityBit(int layer) {
        var h=prepared(0); h.edit(o -> o.moveByte(LAYER,layer)); h.run(); assertEquals(0x5A8,h.slot().word(TILE));
    }

    @Test void dirtyInitAndMovePreserveAllUnwrittenBytesFractionsAndStoredXVelocity() {
        var h=prepared(0); h.edit(o -> TestCdMonitor.dirty(o).moveByte(ID,0x1A).moveByte(ROUTINE,0)
                .moveByte(ANIMATION,4).moveByte(FLAGS,0x35).moveByte(LAYER,0).moveWord(X,0x850).moveWord(Y,0x2D8));
        var before=h.slot(); var r=h.run();
        var expected=before.moveByte(ROUTINE,2).moveWord(TILE,0x85A8).moveByte(SPRITE_FLAGS,0x24)
                .moveByte(PRIORITY,3).moveByte(WIDTH,8).moveWord(Y_VELOCITY,-0x2E8).moveByte(MAP_FRAME,4)
                .moveLong(MAPPINGS,TestCdMonitor.MAP+0x81).moveLong(X_POSITION,0x08615421).moveLong(Y_POSITION,0x02D58765);
        assertEquals(expected,r.state()); assertEquals(before.word(X_VELOCITY),r.state().word(X_VELOCITY));
        assertEquals(0x12AB,r.state().word(MONITOR_ITEM_DELETE));
    }

    @Test void riseHas32OldVelocityMovesThenRewardOn33AndNoTimerTickAtAward() {
        var h=prepared(0);
        for(int i=0;i<32;i++) { h.run(); assertTrue(h.awards.isEmpty()); }
        assertEquals(0x02A68000,h.slot().longBits(Y_POSITION)); assertEquals(0,h.slot().word(Y_VELOCITY));
        var before=h.slot(); var r=h.run();
        assertEquals(before.moveByte(ROUTINE,4).moveWord(MONITOR_ITEM_DELETE,29),r.state());
        assertEquals(1,h.awards.size()); assertEquals(4,h.awards.getFirst().rawAnimation());
        assertEquals(WHOLE,h.awards.getFirst().identity().wholeIndex());
        assertEquals(4,h.awardImages.getFirst().routine()); assertEquals(29,h.awardImages.getFirst().word(MONITOR_ITEM_DELETE));
        assertEquals(List.of("AWARD","DRAW"),h.order.subList(h.order.size()-2,h.order.size()));
    }

    @Test void negativeOneVelocityStillMovesBeforeWordAdditionAndFractionCarryIsNative() {
        var h=prepared(2); h.edit(o -> o.moveLong(X_POSITION,0xFFFFFFFE).moveLong(Y_POSITION,0x00000001)
                .moveWord(X_VELOCITY,1).moveWord(Y_VELOCITY,-1)); h.run();
        assertEquals(0x000000FE,h.slot().longBits(X_POSITION)); assertEquals(0xFFFFFF01,h.slot().longBits(Y_POSITION));
        assertEquals(23,h.slot().signedWord(Y_VELOCITY)); assertTrue(h.awards.isEmpty()); h.run(); assertEquals(1,h.awards.size());
    }

    @ParameterizedTest @ValueSource(ints={0,1,32767})
    void nonnegativeVelocityAwardsImmediatelyWithoutMovement(int velocity) {
        var h=prepared(2); h.edit(o -> o.moveWord(Y_VELOCITY,velocity)); var before=h.slot(); h.run();
        assertEquals(before.moveByte(ROUTINE,4).moveWord(MONITOR_ITEM_DELETE,29),h.slot()); assertEquals(1,h.awards.size());
    }

    @ParameterizedTest @ValueSource(ints={0,1,2,3,5,6,7,128,255})
    void unownedRewardKindsStopAfterRoutineAndWordPrefixBeforeAwardOrDraw(int animation) {
        var h=prepared(2); h.edit(o -> o.moveByte(ANIMATION,animation).moveWord(Y_VELOCITY,0)); var before=h.slot();
        var e=assertThrows(CdMonitorItem.UnsupportedMonitorItem.class,h::run);
        var boundary=switch(animation) { case 0->CdMonitorItem.UnsupportedBoundary.REWARD_ONE_UP;
            case 1->CdMonitorItem.UnsupportedBoundary.REWARD_RINGS; case 2->CdMonitorItem.UnsupportedBoundary.REWARD_SHIELD;
            case 3->CdMonitorItem.UnsupportedBoundary.REWARD_INVINCIBILITY; case 5->CdMonitorItem.UnsupportedBoundary.REWARD_TIME_STOP;
            case 6->CdMonitorItem.UnsupportedBoundary.REWARD_COMBINE_RING; default->CdMonitorItem.UnsupportedBoundary.REWARD_S; };
        assertEquals(boundary,e.boundary()); assertEquals(animation,e.value());
        assertEquals(before.moveByte(ROUTINE,4).moveWord(MONITOR_ITEM_DELETE,29),h.slot());
        assertTrue(h.awards.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void awardPortFailureRetainsItemPrefixAndDoesNotReplayOnTheNextBodyCall() {
        var h=prepared(2); h.edit(o -> o.moveWord(Y_VELOCITY,0)); h.failAward=true;
        var before=h.slot(); assertThrows(IllegalStateException.class,h::run);
        assertEquals(before.moveByte(ROUTINE,4).moveWord(MONITOR_ITEM_DELETE,29),h.slot());
        assertTrue(h.draws.isEmpty()); assertEquals(1,h.awards.size());
        h.failAward=false; h.run(); assertEquals(1,h.awards.size()); assertEquals(28,h.slot().word(MONITOR_ITEM_DELETE));
    }

    @Test void completedRewardMetadataIsImmutableAndNeverReplayedAsSoundOrGameplay() {
        var h=prepared(2); h.edit(o -> o.moveWord(Y_VELOCITY,0));
        h.completed=List.of(CdMonitorItem.AwardSound.SCMD_FADEPCM,CdMonitorItem.AwardSound.SCMD_SHOESMUS);
        var r=h.run(); var awarded=r.effects().stream().filter(CdMonitorItem.Awarded.class::isInstance)
                .map(CdMonitorItem.Awarded.class::cast).findFirst().orElseThrow();
        assertEquals(h.completed,awarded.result().completedSounds());
        assertThrows(UnsupportedOperationException.class,()->awarded.result().completedSounds().clear());
        assertThrows(UnsupportedOperationException.class,()->r.effects().clear());
        h.run(); assertEquals(1,h.awards.size());
    }

    @ParameterizedTest @CsvSource({"29,28,false", "1,0,false", "0,65535,true", "256,255,false", "32768,32767,false", "32769,32768,true"})
    void deletionIsSignedWordAfterSubtractAndWrapperDrawRunsEvenAfterDelete(int old,int next,boolean deleted) {
        var h=prepared(4); h.edit(o -> o.moveWord(MONITOR_ITEM_DELETE,old)); var before=h.slot(); var r=h.run();
        assertEquals(deleted,r.deleted()); assertEquals(1,h.draws.size());
        if(deleted) { assertArrayEquals(new byte[64],h.draws.getFirst().copyBytes()); assertArrayEquals(new byte[64],r.state().copyBytes()); }
        else assertEquals(before.moveWord(MONITOR_ITEM_DELETE,next),r.state());
        assertTrue(h.awards.isEmpty());
    }

    @Test void deletionOccursOnThirtiethPostAwardDeleteCallAndNeverDespawnsByCamera() {
        var h=prepared(2); h.edit(o -> o.moveWord(Y_VELOCITY,0)); h.run();
        for(int i=1;i<=29;i++) { assertFalse(h.run().deleted()); assertEquals(29-i,h.slot().word(MONITOR_ITEM_DELETE)); }
        assertTrue(h.run().deleted()); assertEquals(31,h.draws.size()); assertEquals(1,h.awards.size());
    }

    @ParameterizedTest @ValueSource(ints={1,3,5,6,128,255})
    void invalidRoutineStopsBeforeAnyWriteOrWrapperDraw(int routine) {
        var h=prepared(routine); var before=h.slot();
        assertEquals(CdMonitorItem.UnsupportedBoundary.ROUTINE_DISPATCH,
                assertThrows(CdMonitorItem.UnsupportedMonitorItem.class,h::run).boundary());
        assertEquals(before,h.slot()); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @CsvSource({"30,0,16777216", "30,1,16908288", "127,1,16842752", "0,0,16842752"})
    void standingMoveReadsActualDynamicSupportIdAndFlagsForLocalResistanceOnly(int id,int flags,int x) {
        var h=prepared(2); h.edit(o -> o.moveByte(FLAGS,8).moveByte(STAND_LINK_3D,42)
                .moveLong(X_POSITION,0x01000000).moveWord(X_VELOCITY,0x100));
        h.pool().write(h.pool().state(42).moveByte(ID,id).moveByte(FLAGS,flags));
        h.run(); assertEquals(x,h.slot().longBits(X_POSITION)); assertEquals(0x100,h.slot().word(X_VELOCITY));
    }

    @Test void standZeroRequiresExplicitActualPrimaryIdAndFlagsAndNeverUsesItemFlagsAsPlayer() {
        var h=prepared(2); h.edit(o -> o.moveByte(FLAGS,9).moveByte(STAND_LINK_3D,0)
                .moveLong(X_POSITION,0x01000000).moveWord(X_VELOCITY,0x100));
        var before=h.slot(); var e=assertThrows(CdPlayerMovement.UnsupportedMotion.class,h::run);
        assertEquals(CdPlayerMovement.UnsupportedBoundary.OBJECT_SUPPORT,e.boundary()); assertEquals(before,h.slot());
        h.primary=Optional.of(new CdMonitorItem.PrimarySupport(0x1E,0)); h.run(); assertEquals(0x01000000,h.slot().longBits(X_POSITION));
        h.edit(o -> before); h.primary=Optional.of(new CdMonitorItem.PrimarySupport(0x1E,1)); h.run();
        assertEquals(0x01020000,h.slot().longBits(X_POSITION));
    }

    @ParameterizedTest @CsvSource({"1,RESERVED_OBJECT_SLOT", "31,RESERVED_OBJECT_SLOT", "128,OBJECT_ADDRESS", "192,OBJECT_ADDRESS", "255,OBJECT_ADDRESS"})
    void reachedUnsupportedStandAddressesKeepSharedTypedBoundaryAndInitPrefix(int link,String boundary) {
        var h=prepared(0); h.edit(o -> o.moveByte(FLAGS,8).moveByte(STAND_LINK_3D,link)); var before=h.slot();
        var e=assertThrows(CdPlayerMovement.UnsupportedMotion.class,h::run); assertEquals(boundary,e.boundary().name());
        assertEquals(before.moveByte(ROUTINE,2).moveWord(TILE,0x85A8).moveByte(SPRITE_FLAGS,0x24)
                .moveByte(PRIORITY,3).moveByte(WIDTH,8).moveWord(Y_VELOCITY,-0x300).moveByte(MAP_FRAME,4)
                .moveLong(MAPPINGS,TestCdMonitor.MAP+0x81),h.slot()); assertTrue(h.draws.isEmpty());
    }

    @Test void nonstandingMoveAndNonnegativeRewardNeverReadDirtyStandLinks() {
        var h=prepared(2); h.edit(o -> o.moveByte(STAND_LINK_3D,255).moveByte(FLAGS,0)); h.run();
        assertEquals(1,h.draws.size()); h.edit(o -> o.moveByte(FLAGS,8).moveWord(Y_VELOCITY,0)); h.run();
        assertEquals(1,h.awards.size());
    }

    @Test void drawReloadsLiveBytesAndFailureRetainsMoveOrDeletionPrefix() {
        var h=prepared(2); h.failDraw=true; var before=h.slot(); assertThrows(IllegalStateException.class,h::run);
        assertEquals(before.moveLong(Y_POSITION,0x02D50000).moveWord(Y_VELOCITY,-0x2E8),h.slot());
        var k=prepared(4); k.edit(o -> o.moveWord(MONITOR_ITEM_DELETE,0)); k.failDraw=true;
        assertThrows(IllegalStateException.class,k::run); assertArrayEquals(new byte[64],k.slot().copyBytes());
        var j=prepared(2); j.drawFlags=0xE4; assertEquals(0xE4,j.run().state().spriteFlags());
    }

    @Test void constructorRequiresExplicitAwardAndDrawAndRecordsRejectNullOrNonBytes() {
        var h=prepared(2);
        assertThrows(NullPointerException.class,()->new CdMonitorItem(h.runtime,TestCdMonitor.assets(),null,h::draw));
        assertThrows(NullPointerException.class,()->new CdMonitorItem(h.runtime,TestCdMonitor.assets(),h::award,null));
        assertThrows(IllegalArgumentException.class,()->new CdMonitorItem.AwardCall(h.slot().identity(),256));
        assertThrows(NullPointerException.class,()->new CdMonitorItem.AwardResult(null));
        assertThrows(IllegalArgumentException.class,()->new CdMonitorItem.PrimarySupport(-1,0));
        assertThrows(IllegalArgumentException.class,()->new CdMonitorItem.PrimarySupport(1,256));
    }

    // Additive Shield/receipt fixtures. All pre-existing tests and helpers above/below are preserved.
    @Test void shieldOptInUsesItsOwnTypedCompletedResultAndKeepsShoesGrammar() {
        var b = bound(2); b.h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        var before = b.h.slot(); var r = b.run();
        assertEquals(before.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 29), r.state());
        assertEquals(List.of("CONTACT1", "RESERVED_ID3", "FM_SHIELD97", "DRAW"), b.externalOrder);
        var awarded = r.effects().stream().filter(CdMonitorItem.ShieldAwarded.class::isInstance)
                .map(CdMonitorItem.ShieldAwarded.class::cast).findFirst().orElseThrow();
        assertSame(b.shieldResult, awarded.result()); assertEquals(2, awarded.call().rawAnimation());
        assertEquals(CdMonitorItem.ShieldSound.FM_SHIELD, awarded.result().completedSound());
        assertTrue(r.effects().stream().noneMatch(CdMonitorItem.Awarded.class::isInstance));
        assertTrue(b.h.awards.isEmpty()); assertEquals(1, b.shieldCalls);
        assertEquals(List.of(CdMonitorItem.AwardSound.SCMD_FADEPCM, CdMonitorItem.AwardSound.SCMD_SHOESMUS),
                List.of(CdMonitorItem.AwardSound.values()));
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
        b.run(); assertEquals(1, b.shieldCalls); assertEquals(28, b.h.slot().word(MONITOR_ITEM_DELETE));
    }

    @Test void namedUnobservedShieldConstructionIsExplicitAndLegacyAnimationTwoStillStops() {
        var h = prepared(2); h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        assertEquals(CdMonitorItem.UnsupportedBoundary.REWARD_SHIELD,
                assertThrows(CdMonitorItem.UnsupportedMonitorItem.class, h::run).boundary());
        h.edit(o -> o.moveByte(ROUTINE, 2)); var calls = new ArrayList<CdMonitorItem.ShieldAwardCall>();
        var body = CdMonitorItem.withShieldAward(h.runtime, TestCdMonitor.assets(), h::award, c -> {
            assertEquals(4, h.slot().routine()); assertEquals(29, h.slot().word(MONITOR_ITEM_DELETE));
            calls.add(c); return new CdMonitorItem.ShieldAwardResult(CdMonitorItem.ShieldSound.FM_SHIELD);
        }, h::draw);
        var r = body.run(WHOLE, Optional.empty());
        assertEquals(1, calls.size()); assertEquals(1, h.draws.size());
        assertTrue(r.effects().stream().anyMatch(CdMonitorItem.ShieldAwarded.class::isInstance));
    }

    @Test void boundShieldRises32TimesAndOnlyDispatch33AwardsWithoutReadingDirtySupport() {
        var b = bound(0); b.h.edit(o -> o.moveByte(ANIMATION, 2));
        for (int i = 0; i < 32; i++) b.run();
        assertEquals(0, b.shieldCalls); assertEquals(0x02A68000, b.h.slot().longBits(Y_POSITION));
        b.h.edit(o -> o.moveByte(FLAGS, 8).moveByte(STAND_LINK_3D, 255));
        b.run(); assertEquals(1, b.shieldCalls); assertEquals(29, b.h.slot().word(MONITOR_ITEM_DELETE));
        assertEquals(32, b.calls.stream().filter(CdMonitorItem.MotionCall.class::isInstance).count());
        assertEquals(1, b.calls.stream().filter(CdMonitorItem.ShieldCall.class::isInstance).count());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 3, 5, 6, 7, 128, 255})
    void shieldBindingDoesNotBroadenOtherUnsupportedRewardBranches(int animation) {
        var b = bound(2); b.h.edit(o -> o.moveByte(ANIMATION, animation).moveWord(Y_VELOCITY, 0));
        var before = b.h.slot(); assertThrows(CdMonitorItem.UnsupportedMonitorItem.class, b::run);
        assertEquals(before.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 29), b.h.slot());
        assertEquals(0, b.shieldCalls); assertTrue(b.h.awards.isEmpty()); assertTrue(b.h.draws.isEmpty());
        assertEquals(1, b.calls.size()); assertInstanceOf(CdMonitorItem.Publication.class, b.calls.getFirst());
    }

    @Test void observedShoesStillReturnsTheOriginalShoesResultAndNeverCallsShield() {
        var b = bound(2); b.h.edit(o -> o.moveWord(Y_VELOCITY, 0));
        b.h.completed = List.of(CdMonitorItem.AwardSound.SCMD_FADEPCM, CdMonitorItem.AwardSound.SCMD_SHOESMUS);
        var r = b.run(); var awarded = r.effects().stream().filter(CdMonitorItem.Awarded.class::isInstance)
                .map(CdMonitorItem.Awarded.class::cast).findFirst().orElseThrow();
        int index = indexOf(b.calls, CdMonitorItem.ShoesCall.class);
        assertSame(awarded.result(), b.results.get(index)); assertEquals(b.h.completed, awarded.result().completedSounds());
        assertEquals(0, b.shieldCalls); assertEquals(1, b.h.awards.size());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void originalShieldFaultRetainsActualExternalAndItemPrefixesWithoutLaterDrawOrReplay(int stage) {
        var b = bound(2); b.h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        var before = b.h.slot(); b.failShieldStage = stage;
        assertSame(b.original, assertThrows(IllegalStateException.class, b::run));
        assertEquals(before.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 29), b.h.slot());
        assertEquals(stage >= 1 ? 1 : 0xA5, b.contact);
        assertEquals(stage >= 2 ? 3 : 0xB6, b.reservedId); assertEquals(stage >= 3 ? 1 : 0, b.fmCount);
        assertEquals(List.of("CONTACT1", "RESERVED_ID3", "FM_SHIELD97").subList(0, stage), b.externalOrder);
        assertTrue(b.h.draws.isEmpty()); assertEquals(1, b.failed.size());
        Object stale = b.failed.getFirst(); assertFalse(b.body.isCurrentCall(stale));
        assertThrows(IllegalStateException.class, () -> b.body.currentResult(stale));
        b.failShieldStage = -1; b.run(); assertEquals(1, b.shieldCalls);
        assertEquals(28, b.h.slot().word(MONITOR_ITEM_DELETE));
    }

    @ParameterizedTest @ValueSource(strings = {"OWN", "OTHER", "SAVED", "STATUS"})
    void shieldPortFaultKeepsIdenticalThrowableAndSuppressesFullOwnedStorageVerification(String target) {
        var b = bound(2); b.h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        b.shieldHook = () -> { corrupt(b.h, target); throw b.original; };
        assertSame(b.original, assertThrows(IllegalStateException.class, b::run));
        assertTrue(b.original.getSuppressed().length >= 1); assertTrue(b.h.draws.isEmpty());
        assertEquals(4, b.h.slot().routine()); assertEquals(29, b.h.slot().word(MONITOR_ITEM_DELETE));
        assertEquals(0, b.fmCount); assertEquals(0xA5, b.contact);
    }

    @ParameterizedTest @ValueSource(strings = {"OWN", "OTHER", "SAVED", "STATUS"})
    void shieldSuccessCannotHideDynamicOrSourceBankWrites(String target) {
        var b = bound(2); b.h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        b.shieldHook = () -> corrupt(b.h, target);
        assertThrows(IllegalStateException.class, b::run); assertTrue(b.h.draws.isEmpty());
        assertEquals(1, b.shieldCalls); assertEquals(1, b.fmCount); // Actual external prefix is not rolled back.
    }

    @ParameterizedTest @ValueSource(strings = {"BEFORE", "PREPARED", "AFTER", "COMPLETED"})
    void callbackFaultPreservesReachedPrimitivePrefixAndCleansAuthority(String phase) {
        var b = bound(2); b.h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        var before = b.h.slot();
        java.util.function.Consumer<Object> reject = call -> { throw b.original; };
        switch (phase) {
            case "BEFORE" -> b.beforeHook = reject;
            case "PREPARED" -> b.preparedHook = reject;
            case "AFTER" -> b.afterHook = reject;
            case "COMPLETED" -> b.completedHook = reject;
        }
        assertSame(b.original, assertThrows(IllegalStateException.class, b::run));
        boolean wrote = phase.equals("AFTER") || phase.equals("COMPLETED");
        assertEquals(wrote ? before.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 29) : before, b.h.slot());
        assertEquals(0, b.shieldCalls); assertTrue(b.h.draws.isEmpty());
        assertFalse(b.body.isCurrentCall(b.calls.getFirst()));
    }

    @Test void failedObserverExceptionIsSuppressedAndCannotReplaceOriginalAwardFault() {
        var b = bound(2); b.h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        var verification = new AssertionError("failure observer verification");
        b.failShieldStage = 1; b.failedHook = call -> { throw verification; };
        assertSame(b.original, assertThrows(IllegalStateException.class, b::run));
        assertArrayEquals(new Throwable[]{verification}, b.original.getSuppressed());
        assertEquals(1, b.contact); assertTrue(b.h.draws.isEmpty());
    }

    @Test void observerAndGuardNonwritesAreCheckedBeforeAnActualSourceReadOrAward() {
        var b = bound(0); b.beforeHook = call -> {
            if (call instanceof CdMonitorItem.MappingReadCall) corrupt(b.h, "OTHER");
        };
        assertThrows(IllegalStateException.class, b::run);
        assertEquals(0, b.h.slot().longBits(MAPPINGS)); // Init prefix exists, reached read did not return.
        assertEquals(2, b.h.slot().routine()); assertTrue(b.h.draws.isEmpty());
        var k = bound(2); k.h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        k.preparedHook = call -> { if (call instanceof CdMonitorItem.ShieldCall) corrupt(k.h, "SAVED"); };
        assertThrows(IllegalStateException.class, k::run); assertEquals(0, k.shieldCalls);
    }

    @Test void currentOriginalReceiptsExposeExactReadMotionAndReturnedResultObjectsInSourceOrder() {
        var b = bound(0); var r = b.run();
        assertSame(b.body.assets(), b.assets); assertSame(r, b.adopted);
        assertEquals(List.of("INIT_PREFIX", "READ", "MAPPING_POINTER", "MOVE_READY", "MOVE", "MOVE_RESULT",
                "WRAPPER_READY", "DRAW", "COMPLETE"), b.calls.stream().map(TestCdMonitorItem::operationName).toList());
        var read = (CdMonitorItem.MappingReadCall) b.calls.get(1);
        assertEquals(8, read.byteOffset()); assertEquals(2, read.source().routine());
        assertEquals(0x80, b.results.get(1));
        assertInstanceOf(CdPlayerMovement.RawObjectMoveResult.class, b.results.get(4));
        assertSame(r, b.results.getLast());
        for (Object stale : b.calls) {
            assertFalse(b.body.isCurrentCall(stale)); assertFalse(b.body.isCurrentPrimitive(stale));
            assertThrows(IllegalStateException.class, () -> b.body.currentOrdinal(stale));
            assertThrows(IllegalStateException.class, () -> b.body.currentResult(stale));
        }
        assertFalse(b.body.isCurrentCall(null)); assertFalse(b.body.isCurrentCall(new Object()));
        var equalClone = new CdMonitorItem.Result(r.state(), r.effects()); assertEquals(r, equalClone); assertNotSame(r, equalClone);
        b.run(); assertEquals(1L, b.firstOrdinal); // Ordinals restart per invocation, host generation still required.
    }

    @Test void equalPublicCallClonesNeverAcquirePendingOrPrimitiveOrCompletionAuthority() {
        var b = bound(2); b.h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        b.beforeHook = call -> {
            Object clone = cloneCall(call); assertEquals(call, clone); assertNotSame(call, clone);
            assertFalse(b.body.isCurrentCall(clone)); assertFalse(b.body.isCurrentPrimitive(clone));
            assertThrows(IllegalStateException.class, () -> b.body.currentOrdinal(clone));
            assertThrows(IllegalStateException.class, () -> b.body.currentResult(clone));
            assertThrows(IllegalStateException.class, b::run);
        };
        b.shieldHook = () -> {
            var actual = (CdMonitorItem.ShieldCall) b.pending;
            assertFalse(b.body.isCurrentPrimitive(cloneCall(actual)));
            assertThrows(IllegalStateException.class, () -> b.body.currentResult(actual));
            assertThrows(IllegalStateException.class, b::run);
        };
        b.run(); assertEquals(1, b.shieldCalls);
    }

    @Test void observedUnsupportedSupportReadKeepsTypedFailureAndInitMappingPrefixWithoutMotionWrites() {
        var b = bound(0); b.h.edit(o -> o.moveByte(FLAGS, 8).moveByte(STAND_LINK_3D, 31));
        var original = assertThrows(CdPlayerMovement.UnsupportedMotion.class, b::run);
        assertEquals(CdPlayerMovement.UnsupportedBoundary.RESERVED_OBJECT_SLOT, original.boundary());
        assertEquals(TestCdMonitor.MAP + 0x81, b.h.slot().longBits(MAPPINGS));
        assertEquals(-0x300, b.h.slot().signedWord(Y_VELOCITY)); assertEquals(0x02D80000, b.h.slot().longBits(Y_POSITION));
        assertInstanceOf(CdMonitorItem.MotionCall.class, b.failed.getFirst()); assertTrue(b.h.draws.isEmpty());
    }

    @Test void boundOldNegativeMoveReadsRealDynamicSupportAndRetainsStoredVelocity() {
        var b = bound(2); b.h.edit(o -> o.moveByte(FLAGS, 8).moveByte(STAND_LINK_3D, 42)
                .moveLong(X_POSITION, 0x01000000).moveWord(X_VELOCITY, 0x100).moveWord(Y_VELOCITY, -1));
        b.h.pool().write(b.h.pool().state(42).moveByte(ID, 0x1E).moveByte(FLAGS, 1));
        var support = b.h.pool().state(42); var r = b.run();
        assertEquals(0x01020000, r.state().longBits(X_POSITION)); assertEquals(23, r.state().signedWord(Y_VELOCITY));
        assertEquals(0x100, r.state().word(X_VELOCITY)); assertEquals(support, b.h.pool().state(42));
    }

    @Test void observedRawMappingBodyWordUsesSignedRelativePlusOneWithoutAnEarlyTargetRead() {
        var b = bound(0); b.h.edit(o -> o.moveByte(ANIMATION, 23));
        // Actual retained monitor include at46: F0 06, selected by raw animation23.
        var r = b.run();
        assertEquals(TestCdMonitor.MAP - 4090 + 1, r.state().longBits(MAPPINGS));
        var read = (CdMonitorItem.MappingReadCall) b.calls.get(1); assertEquals(46, read.byteOffset());
        assertEquals(-4090, b.results.get(1)); assertEquals(23, r.state().unsignedByte(MAP_FRAME));
    }

    @ParameterizedTest @CsvSource({"0,true", "32768,false", "32769,true"})
    void observedDeleteIsWordSignBasedAndStillReturnsThroughRealDraw(int timer, boolean deleted) {
        var b = bound(4); b.h.edit(o -> o.moveWord(MONITOR_ITEM_DELETE, timer));
        var r = b.run(); assertEquals(deleted, r.deleted()); assertEquals(1, b.h.draws.size());
        assertEquals(deleted ? 1 : 0, b.calls.stream().filter(CdMonitorItem.DeleteCall.class::isInstance).count());
        if (deleted) assertArrayEquals(new byte[64], r.state().copyBytes());
        else assertEquals(32767, r.state().word(MONITOR_ITEM_DELETE));
        assertSame(r, b.adopted); assertEquals(0, b.shieldCalls);
    }

    @Test void drawAllowsOnlyActualOwnBitSevenAndFreezesItBeforeObserverCallbacks() {
        var b = bound(2); b.h.drawFlags = 0xA4; assertEquals(0xA4, b.run().state().spriteFlags());
        var k = bound(2); k.h.drawFlags = 0xE4; assertThrows(IllegalStateException.class, k::run);
        assertEquals(0xE4, k.h.slot().spriteFlags()); // Invalid actual primitive bytes remain; no rollback.
        var j = bound(2); j.afterHook = call -> {
            if (call instanceof CdMonitorItem.DrawCall) j.h.edit(o -> o.moveByte(SPRITE_FLAGS, o.spriteFlags() ^ 128));
        };
        assertThrows(IllegalStateException.class, j::run);
        assertTrue(j.calls.stream().noneMatch(CdMonitorItem.CompletionCall.class::isInstance));
    }

    @Test void drawFailureRetainsActualBitSevenAndIdenticalThrowableWithVerificationSuppressed() {
        var b = bound(4); b.h.edit(o -> o.moveWord(MONITOR_ITEM_DELETE, 0));
        b.drawHook = () -> { b.h.edit(o -> o.moveByte(SPRITE_FLAGS, 128)); throw b.original; };
        assertSame(b.original, assertThrows(IllegalStateException.class, b::run));
        assertEquals(128, b.h.slot().spriteFlags()); assertEquals(0, b.h.slot().id());
        assertEquals(0, b.original.getSuppressed().length); assertInstanceOf(CdMonitorItem.DrawCall.class, b.failed.getFirst());
        var k = bound(2); k.drawHook = () -> { corrupt(k.h, "OTHER"); throw k.original; };
        assertSame(k.original, assertThrows(IllegalStateException.class, k::run));
        assertTrue(k.original.getSuppressed().length >= 1);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void drawPermissionEndsBeforeFailedObserverEvenWhenAnotherSlotIsCorrupt(boolean throwInDraw) {
        var b = bound(2);
        b.drawHook = () -> {
            corrupt(b.h, "OTHER");
            if (throwInDraw) throw b.original;
        };
        b.failedHook = call -> {
            if (call instanceof CdMonitorItem.DrawCall) {
                b.h.pool().deleteObject(127); // Repair only the unrelated corruption, exposing persistent own-bit change.
                b.h.edit(o -> o.moveByte(SPRITE_FLAGS, o.spriteFlags() ^ 128));
            }
        };
        var failure = assertThrows(IllegalStateException.class, b::run);
        if (throwInDraw) assertSame(b.original, failure);
        else assertTrue(failure.getMessage().contains("at 127"));
        assertEquals(0xA4, b.h.slot().spriteFlags(), "Unauthorized failed-observer write is detected, not rolled back");
        assertTrue(java.util.Arrays.stream(failure.getSuppressed()).anyMatch(f -> f.getMessage() != null
                && f.getMessage().contains("unowned dynamic bytes at " + WHOLE)),
                "Draw's actual bit must be frozen before failedOperation changes it");
        assertTrue(b.calls.stream().noneMatch(CdMonitorItem.CompletionCall.class::isInstance));
        assertFalse(b.body.isCurrentCall(b.failed.getLast()));
    }

    @Test void nullShieldReturnStopsAfterActualExternalPrefixAndErrorIdentityAlsoSurvivesVerification() {
        var h = prepared(2); h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        var body = CdMonitorItem.withShieldAward(h.runtime, TestCdMonitor.assets(), h::award, c -> null, h::draw);
        assertThrows(NullPointerException.class, () -> body.run(WHOLE, Optional.empty()));
        assertEquals(4, h.slot().routine()); assertEquals(29, h.slot().word(MONITOR_ITEM_DELETE)); assertTrue(h.draws.isEmpty());
        var b = bound(2); b.h.edit(o -> o.moveByte(ANIMATION, 2).moveWord(Y_VELOCITY, 0));
        var error = new AssertionError("original Shield error"); b.shieldHook = () -> { corrupt(b.h, "SAVED"); throw error; };
        assertSame(error, assertThrows(AssertionError.class, b::run)); assertTrue(error.getSuppressed().length >= 1);
    }

    @Test void everyOperationDescriptionCloneIsRejectedAcrossRiseRewardDeleteAndCompletion() {
        var b = bound(0); b.h.edit(o -> o.moveByte(ANIMATION, 2));
        b.beforeHook = call -> {
            Object clone = cloneCall(call); assertEquals(call, clone); assertNotSame(call, clone);
            assertFalse(b.body.isCurrentCall(clone)); assertFalse(b.body.isCurrentPrimitive(clone));
            assertThrows(IllegalStateException.class, () -> b.body.currentOrdinal(clone));
            assertThrows(IllegalStateException.class, () -> b.body.currentResult(clone));
        };
        b.run(); b.h.edit(o -> o.moveWord(Y_VELOCITY, 0)); b.run();
        b.h.edit(o -> o.moveWord(MONITOR_ITEM_DELETE, 0)); b.run();
        var k = bound(2); k.h.edit(o -> o.moveWord(Y_VELOCITY, 0));
        k.beforeHook = call -> assertFalse(k.body.isCurrentCall(cloneCall(call))); k.run();
        assertTrue(b.calls.stream().anyMatch(CdMonitorItem.DeleteCall.class::isInstance));
        assertTrue(b.calls.stream().anyMatch(CdMonitorItem.MappingReadCall.class::isInstance));
        assertTrue(b.calls.stream().anyMatch(CdMonitorItem.MotionCall.class::isInstance));
    }

    @Test void publicOptInFactoriesAndShieldRecordsRejectMissingOwnersAndInvalidShapes() {
        var h = prepared(2); var a = TestCdMonitor.assets();
        CdMonitorItem.ShieldAwardPort shield = c -> new CdMonitorItem.ShieldAwardResult(CdMonitorItem.ShieldSound.FM_SHIELD);
        assertThrows(NullPointerException.class, () -> CdMonitorItem.withShieldAward(h.runtime, a, h::award, null, h::draw));
        assertThrows(NullPointerException.class, () -> CdMonitorItem.withWorldOwner(h.runtime, a, h::award, shield, h::draw, null, null));
        var b = bound(2);
        assertThrows(NullPointerException.class, () -> CdMonitorItem.withWorldOwner(h.runtime, a, h::award, shield, h::draw, b, null));
        assertThrows(IllegalArgumentException.class, () -> new CdMonitorItem.ShieldAwardCall(h.slot().identity(), 4));
        assertThrows(IllegalArgumentException.class, () -> new CdMonitorItem.ShieldAwardCall(h.slot().identity(), 258));
        assertThrows(NullPointerException.class, () -> new CdMonitorItem.ShieldAwardResult(null));
        assertThrows(NullPointerException.class, () -> new CdMonitorItem.ShieldAwarded(null, b.shieldResult));
        assertThrows(NullPointerException.class, () -> new CdMonitorItem.ShieldAwarded(new CdMonitorItem.ShieldAwardCall(h.slot().identity(), 2), null));
    }

    static int indexOf(List<Object> calls, Class<?> type) {
        for (int i = 0; i < calls.size(); i++) if (type.isInstance(calls.get(i))) return i;
        throw new AssertionError("Missing actual operation " + type);
    }
    static String operationName(Object call) {
        if (call instanceof CdMonitorItem.Publication p) return p.kind().name();
        if (call instanceof CdMonitorItem.MappingReadCall) return "READ";
        if (call instanceof CdMonitorItem.MotionCall) return "MOVE";
        if (call instanceof CdMonitorItem.DrawCall) return "DRAW";
        if (call instanceof CdMonitorItem.CompletionCall) return "COMPLETE";
        throw new AssertionError("Unexpected call " + call);
    }
    static Object cloneCall(Object call) {
        if (call instanceof CdMonitorItem.Publication c) return new CdMonitorItem.Publication(c.kind(), c.cut(), c.source(), c.output());
        if (call instanceof CdMonitorItem.ShieldCall c) return new CdMonitorItem.ShieldCall(c.cut(), c.source(), c.request());
        if (call instanceof CdMonitorItem.ShoesCall c) return new CdMonitorItem.ShoesCall(c.cut(), c.source(), c.request());
        if (call instanceof CdMonitorItem.MappingReadCall c) return new CdMonitorItem.MappingReadCall(c.cut(), c.source(), c.byteOffset());
        if (call instanceof CdMonitorItem.MotionCall c) return new CdMonitorItem.MotionCall(c.cut(), c.source(), c.primarySupport());
        if (call instanceof CdMonitorItem.DeleteCall c) return new CdMonitorItem.DeleteCall(c.cut(), c.source());
        if (call instanceof CdMonitorItem.DrawCall c) return new CdMonitorItem.DrawCall(c.cut(), c.source());
        if (call instanceof CdMonitorItem.CompletionCall c) return new CdMonitorItem.CompletionCall(c.cut(), c.source());
        throw new AssertionError("Unexpected clone fixture " + call);
    }
    static void corrupt(Harness h, String target) {
        switch (target) {
            case "OWN" -> h.edit(o -> o.moveByte(ANGLE, o.unsignedByte(ANGLE) ^ 1));
            case "OTHER" -> h.pool().write(h.pool().state(127).moveByte(ANGLE, 1));
            case "SAVED" -> h.runtime.bsetSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
            case "STATUS" -> h.runtime.spawnObjects(0, 0);
            default -> throw new AssertionError(target);
        }
    }
    static Binding bound(int routine) { return new Binding(prepared(routine)); }
    static final class Binding implements CdMonitorItem.DispatchObserver, CdMonitorItem.ReceiptGuard {
        final Harness h;
        final CdMonitor.Assets assets = TestCdMonitor.assets();
        final CdMonitorItem body;
        final CdMonitorItem.ShieldAwardResult shieldResult = new CdMonitorItem.ShieldAwardResult(CdMonitorItem.ShieldSound.FM_SHIELD);
        final IllegalStateException original = new IllegalStateException("actual constructed port failure");
        final List<Object> calls = new ArrayList<>(), results = new ArrayList<>(), failed = new ArrayList<>();
        final List<String> externalOrder = new ArrayList<>();
        java.util.function.Consumer<Object> beforeHook = c -> {}, preparedHook = c -> {}, afterHook = c -> {},
                completedHook = c -> {}, failedHook = c -> {};
        Runnable shieldHook = () -> {}, drawHook = () -> {};
        int shieldCalls, contact = 0xA5, reservedId = 0xB6, fmCount, failShieldStage = -1;
        Object pending;
        CdMonitorItem.Result adopted;
        long firstOrdinal;
        Binding(Harness h) {
            this.h = h;
            body = CdMonitorItem.withWorldOwner(h.runtime, assets, call -> {
                assertTrue(bodyPrimitive());
                assertSame(((CdMonitorItem.ShoesCall) pending).request(), call); return h.award(call);
            }, this::shield, (pool, identity) -> {
                assertTrue(bodyPrimitive()); drawHook.run(); externalOrder.add("DRAW"); h.draw(pool, identity);
            }, this, this);
        }
        boolean bodyPrimitive() { return body.isCurrentPrimitive(pending); }
        CdMonitorItem.Result run() { return body.run(WHOLE, h.primary); }
        CdMonitorItem.ShieldAwardResult shield(CdMonitorItem.ShieldAwardCall call) {
            assertTrue(bodyPrimitive()); assertSame(((CdMonitorItem.ShieldCall) pending).request(), call);
            assertEquals(h.slot().identity(), call.identity()); assertEquals(4, h.slot().routine());
            assertEquals(29, h.slot().word(MONITOR_ITEM_DELETE)); shieldCalls++; shieldHook.run();
            if (failShieldStage == 0) throw original;
            contact = 1; externalOrder.add("CONTACT1"); if (failShieldStage == 1) throw original;
            reservedId = 3; externalOrder.add("RESERVED_ID3"); if (failShieldStage == 2) throw original;
            fmCount++; externalOrder.add("FM_SHIELD97"); if (failShieldStage == 3) throw original;
            return shieldResult;
        }
        public void beforeOperation(Object call) {
            pending = call; calls.add(call); assertTrue(body.isCurrentCall(call)); assertFalse(bodyPrimitive());
            long ordinal = body.currentOrdinal(call); if (ordinal == 1) firstOrdinal = ordinal;
            assertThrows(IllegalStateException.class, () -> body.currentResult(call)); beforeHook.accept(call);
        }
        public void prepared(Object call) { assertSame(pending, call); assertFalse(bodyPrimitive()); preparedHook.accept(call); }
        public void afterOperation(Object call) {
            assertSame(pending, call); assertTrue(body.isCurrentCall(call)); assertFalse(bodyPrimitive());
            Object result = body.currentResult(call); results.add(result);
            if (call instanceof CdMonitorItem.CompletionCall) adopted = (CdMonitorItem.Result) result;
            afterHook.accept(call);
        }
        public void completed(Object call) {
            assertSame(pending, call); assertFalse(bodyPrimitive()); assertSame(results.getLast(), body.currentResult(call));
            completedHook.accept(call);
        }
        public void failedOperation(Object call, Throwable failure) {
            assertSame(pending, call); assertTrue(body.isCurrentCall(call)); assertFalse(bodyPrimitive());
            assertNotNull(failure); failed.add(call); failedHook.accept(call);
        }
    }

    static Harness prepared(int routine) { var h=new Harness(); h.edit(o -> o.moveByte(ID,0x1A).moveByte(ROUTINE,routine)
            .moveByte(ANIMATION,4).moveByte(SPRITE_FLAGS,0x24).moveWord(X,0x850).moveWord(Y,0x2D8)
            .moveWord(Y_VELOCITY,-0x300)); return h; }
    static final class Harness {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(TestCdMonitor.layout());
        final CdMonitorItem body=new CdMonitorItem(runtime,TestCdMonitor.assets(),this::award,this::draw);
        final List<CdMonitorItem.AwardCall> awards=new ArrayList<>();
        final List<CdObjectState> awardImages=new ArrayList<>(),draws=new ArrayList<>();
        final List<String> order=new ArrayList<>();
        Optional<CdMonitorItem.PrimarySupport> primary=Optional.empty();
        List<CdMonitorItem.AwardSound> completed=List.of(CdMonitorItem.AwardSound.SCMD_SHOESMUS);
        boolean failAward,failDraw; int drawFlags=-1;
        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> f) { pool().write(f.apply(slot())); }
        CdMonitorItem.Result run() { return body.run(WHOLE,primary); }
        CdMonitorItem.AwardResult award(CdMonitorItem.AwardCall call) {
            order.add("AWARD");awards.add(call);awardImages.add(slot());
            assertEquals(4,call.rawAnimation());assertEquals(slot().identity(),call.identity());
            if(failAward) throw new IllegalStateException("constructed award rejection");
            return new CdMonitorItem.AwardResult(completed);
        }
        void draw(CdObjectPool pool,CdObjectState.Identity identity) {
            assertSame(pool(),pool);order.add("DRAW");draws.add(slot());
            if(drawFlags>=0) pool.write(slot().moveByte(SPRITE_FLAGS,drawFlags));
            // This fixture uses an explicit source-call observer, including empty ID after DeleteObject.
            if(failDraw) throw new IllegalStateException("constructed draw rejection");
        }
    }
}
