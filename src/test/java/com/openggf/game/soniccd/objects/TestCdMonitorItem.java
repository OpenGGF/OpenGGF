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
