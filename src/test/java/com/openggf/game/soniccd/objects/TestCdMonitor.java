package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests-first external source candidate. ALL JAVA UNRUN. Literal native expectations from
 * scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Monitor and Time Post/Main.asm:207-409;
 * Object Spawner.asm:329-446; Object Solid.asm:15-146,188-479; Object Functions.asm:42-82.
 * Constructed native-format fixtures authenticate no media. No Solid/floor answer is injected.
 * OpenAI Codex GPT-6.1 Sol <noreply@openai.com>.
 */
class TestCdMonitor {
    static final int WHOLE = 40, MAP = 0x00CD1000; // Constructed marker, never a retail address.
    static final String MAP_HEX = "0028003e0054006a0080009600ac00c200d800ee0104011e01380152016c018601a001b001b601bc04f6050012f8f0060000f0f006080000080c0006f00004f6050016f8f0060000f0f006080000080c0006f00004f605001af8f0060000f0f006080000080c0006f00004f605001ef8f0060000f0f006080000080c0006f00004f6050022f8f0060000f0f006080000080c0006f00004f6050026f8f0060000f0f006080000080c0006f00004f605002af8f0060000f0f006080000080c0006f00004f605002ef8f0060000f0f006080000080c0006f00004f6050048f8f0060000f0f006080000080c0006f00004f6050848f8f0060000f0f006080000080c0006f00005d80d0032f0e803004cf8e803084c0008010050f8080108500005d80d0832f0e803004cf8e803084c0008010050f8080108500005d80d003af0e803004cf8e803084c0008010050f8080108500005d80d083af0e803004cf8e803084c0008010050f8080108500005d8050042f8e803004cf8e803084c0008010050f8080108500005d8010046fce803004cf8e803084c0008010050f8080108500003f0060000f0f006080000080c0006f001000d000af001f80d0032f001f80d003af0";
    static final String ANI_HEX = "00140020002c003800440050005c00680074007e01100000080000090000ff0001100101080101090101ff0001100202080202090202ff0001100303080303090303ff0001100404080404090404ff0001100505080505090505ff0001100606080606090606ff0001100707080707090707ff00010a0e0f0e0b0e0f0eff010c0e0f0e0d0e0f0eff";

    @Test void storageAliasesAreOnlyTheActualRawBytesAndDeletionWord() {
        assertEquals(0x2C, RAW_CONTROL_2C.byteOffset());
        assertEquals(0x3D, STAND_LINK_3D.byteOffset());
        assertEquals(0x1E, MONITOR_ITEM_DELETE.byteOffset());
        var h = prepared(8); h.edit(o -> o.moveWord(MONITOR_ITEM_DELETE, 0x12AB));
        assertEquals(0x12, h.slot().unsignedByte(ANIMATION_TIME));
        assertEquals(0xAB, Byte.toUnsignedInt(h.slot().copyBytes()[0x1F]));
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6,7})
    void initPreservesEveryUnwrittenByteAndDoesNotPrimeAnimationOrSolid(int subtype) {
        var h = prepared(0); h.edit(o -> dirty(o).moveByte(ID, 0x19).moveByte(ROUTINE, 0)
                .moveByte(SUBTYPE, subtype).moveByte(SAVED_FLAGS_ID, 7));
        h.runtime.bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.runtime.bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
        var before = h.slot(); var r = h.run();
        var expected = before.moveByte(ROUTINE, 2).moveByte(Y_RADIUS, 14).moveByte(X_RADIUS, 14)
                .moveLong(MAPPINGS, MAP).moveWord(TILE, 0x05A8).moveByte(PRIORITY, 3)
                .moveByte(SPRITE_FLAGS, 4).moveByte(WIDTH, 15).moveByte(COLLISION_TYPE, 0x46)
                .moveByte(ANIMATION, subtype);
        assertEquals(expected, r.state()); assertEquals(expected, h.draws.getFirst());
        assertEquals(2, h.runtime.savedFlag(7, 1));
        assertEquals(List.of("DRAW"), h.order); assertTrue(h.solids.isEmpty());
        assertEquals(0, count(r, CdMonitor.NativeCall.ANIMATE_OBJECT));
        assertEquals(0x4321, r.state().word(X_FRACTION)); assertEquals(0x8765, r.state().word(Y_FRACTION));
    }

    @Test void alreadyBrokenInitReturnsWithoutDrawDespawnCollisionOrAnimationWrites() {
        var h = prepared(0); h.edit(o -> dirty(o).moveByte(ID, 0x19).moveByte(ROUTINE, 0)
                .moveByte(SUBTYPE, 4).moveByte(SAVED_FLAGS_ID, 7));
        h.runtime.bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
        h.runtime.bsetSavedFlag(7, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.camera = 0x6000; var before = h.slot(); var r = h.run();
        assertEquals(before.moveByte(ROUTINE, 8).moveByte(Y_RADIUS, 14).moveByte(X_RADIUS, 14)
                .moveLong(MAPPINGS, MAP).moveWord(TILE, 0x5A8).moveByte(PRIORITY, 3)
                .moveByte(SPRITE_FLAGS, 4).moveByte(WIDTH, 15).moveByte(MAP_FRAME, 17), r.state());
        assertEquals(1, h.runtime.savedFlag(7, 1)); assertTrue(h.order.isEmpty());
        assertEquals(0, count(r, CdMonitor.NativeCall.CHECK_OBJ_DESPAWN));
    }

    @ParameterizedTest @CsvSource({"0,0,1448,3", "6,0,34216,0", "6,1,1448,3", "6,255,1448,3", "5,0,1448,3"})
    void initPriorityUsesActualZoneAndObjectLayer(int zone, int layer, int tile, int priority) {
        var h = prepared(0); h.zone=zone; h.edit(o -> o.moveByte(LAYER, layer)); h.run();
        assertEquals(tile, h.slot().word(TILE)); assertEquals(priority, h.slot().unsignedByte(PRIORITY));
    }

    @ParameterizedTest @CsvSource({"1,0,7,1", "130,1,7,1", "128,1,7,0", "130,255,7,2", "4,0,8,1"})
    void savedHelperProjectsActualLinearBankAndRawWarpDirection(int time, int direction, int id, int period) {
        var h = prepared(0); h.time=time; h.direction=direction;
        h.edit(o -> o.moveByte(SAVED_FLAGS_ID, 7));
        h.runtime.bsetSavedFlag(id, period, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
        h.runtime.bsetSavedFlag(id, period, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.run(); assertEquals(8, h.slot().routine()); assertEquals(1, h.runtime.savedFlag(id, period));
        assertTrue(h.draws.isEmpty(), "saved Init returns even while warping");
    }

    @Test void savedHelperReadsAndWritesIdZeroWhileDespawnSkipsIdZero() {
        var h = prepared(0); h.edit(o -> o.moveByte(SAVED_FLAGS_ID, 0));
        h.runtime.bsetSavedFlag(0, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.run(); assertEquals(0, h.runtime.savedFlag(0, 1));
        h.edit(o -> o.moveByte(ROUTINE, 8));
        h.runtime.bsetSavedFlag(0, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.camera=0x6000; assertTrue(h.run().deleted());
        assertEquals(128, h.runtime.savedFlag(0, 1));
    }

    @Test void savedAddressBoundaryRetainsOnlyInitPrefixAndBankIsStill766Bytes() {
        var h = prepared(0); h.edit(o -> o.moveByte(SAVED_FLAGS_ID, 255)); var before=h.slot();
        var e=assertThrows(CdMonitor.UnsupportedMonitor.class,h::run);
        assertEquals(CdMonitor.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,e.boundary());
        assertEquals(before.moveByte(ROUTINE,2).moveByte(Y_RADIUS,14).moveByte(X_RADIUS,14)
                .moveLong(MAPPINGS,MAP).moveWord(TILE,0x5A8).moveByte(PRIORITY,3)
                .moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,15),h.slot());
        assertEquals(766,h.runtime.capture().savedFlags().length()); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1,2,255})
    void timeAttackSubtypeZeroBypassesRoutineLookupAndDoesOnlyDespawn(int attack) {
        var h=prepared(255); h.attack=attack; h.edit(o -> o.moveByte(SUBTYPE,0)); var before=h.slot();
        h.run(); assertEquals(before,h.slot()); assertTrue(h.order.isEmpty());
        h.edit(o -> o.moveByte(SPRITE_FLAGS,4)); // Explicit offscreen visibility input for CheckObjDespawn.
        h.camera=0x6000; assertTrue(h.run().deleted());
    }

    @Test void timeAttackDoesNotSuppressOtherMonitorSubtypes() {
        var h=prepared(0); h.attack=255; h.run(); assertEquals(2,h.slot().routine()); assertEquals(1,h.draws.size());
    }

    @ParameterizedTest @ValueSource(ints = {8,9,10,127,128,255})
    void unownedPostOrHudStopsBeforeAnyEntryWrite(int subtype) {
        var h=prepared(0); h.attack=1; h.edit(o -> o.moveByte(SUBTYPE,subtype)); var before=h.runtime.capture();
        var e=assertThrows(CdMonitor.UnsupportedMonitor.class,h::run);
        assertEquals(CdMonitor.UnsupportedBoundary.TIME_POST_OR_HUD,e.boundary());
        assertEquals(before.pool().slots(),h.pool().capture().slots());
        assertArrayEquals(before.savedFlags().copy(),h.runtime.capture().savedFlags().copy()); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1,3,5,7,9,10,128,255})
    void routineByteIsNotMaskedRepairedOrRunWhenOutsideFiveWordTable(int routine) {
        var h=prepared(routine); var before=h.slot();
        assertEquals(CdMonitor.UnsupportedBoundary.ROUTINE_DISPATCH,
                assertThrows(CdMonitor.UnsupportedMonitor.class,h::run).boundary());
        assertEquals(before,h.slot()); assertTrue(h.order.isEmpty());
    }

    @Test void offscreenMainSkipsMovementFloorSolidAndAnimateEvenWhenFalling() {
        var h=prepared(2); h.edit(o -> o.moveByte(SPRITE_FLAGS,4).moveByte(ROUTINE_2,4)
                .moveWord(Y_VELOCITY,-0x180)); var before=h.slot(); var r=h.run();
        assertEquals(before,r.state()); assertEquals(List.of("DRAW"),h.order); assertTrue(h.solids.isEmpty());
        assertEquals(0,count(r,CdMonitor.NativeCall.OBJ_MOVE_GRV)); assertEquals(0,count(r,CdMonitor.NativeCall.ANIMATE_OBJECT));
    }

    @Test void visibleMainRunsActualPlainSolidThenAnimationWithoutPrimingAnExtraCall() {
        var h=prepared(2); var r=h.run();
        assertEquals(List.of("SOLID","DRAW"),h.order); assertEquals(1,h.solids.size());
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT,h.solids.getFirst().result().entry());
        assertEquals(CdSolidObject.GetOffReason.PLAYER_DEAD,h.solids.getFirst().result().getOffReason().orElseThrow());
        assertEquals(4,r.state().unsignedByte(PREVIOUS_ANIMATION)); assertEquals(1,r.state().unsignedByte(ANIMATION_FRAME));
        assertEquals(1,r.state().unsignedByte(ANIMATION_TIME)); assertEquals(16,r.state().unsignedByte(MAP_FRAME));
        assertEquals(1,count(r,CdMonitor.NativeCall.ANIMATE_OBJECT));
        assertEquals(h.player,r.finalPlayer()); assertEquals(h.solidGlobals,r.finalGlobals());
        assertTrue(h.solids.getFirst().result().objectWrites().contains(SOLID_TYPE),"same-value CLR.B is owned by Solid");
    }

    @ParameterizedTest @CsvSource({"0,0,true", "0,1,true", "6,0,true", "6,1,false", "6,255,false"})
    void layerGateUsesBooleanMMZLayersAndDoesNotSuppressAnimate(int zone,int objectLayer,boolean solid) {
        var h=prepared(2); h.zone=zone; h.layer=0; h.edit(o -> o.moveByte(LAYER,objectLayer));
        var r=h.run(); assertEquals(solid?1:0,h.solids.size()); assertEquals(1,count(r,CdMonitor.NativeCall.ANIMATE_OBJECT));
        if(zone==6) {
            h.layer=1; h.solids.clear();
            // Explicit next-call SAT visibility input; this harness models DrawObject, not DrawObjects.
            h.edit(o -> o.moveByte(SPRITE_FLAGS,o.spriteFlags()|0x80));
            h.run(); assertEquals(objectLayer==0?0:1,h.solids.size());
        }
    }

    @ParameterizedTest @ValueSource(ints = {1,0x8000,65535})
    void timeStopHoldsOnlyAnimationAndStillRunsSolid(int timer) {
        var h=prepared(2); h.timeStop=timer; var before=h.slot(); var r=h.run();
        assertEquals(before.moveByte(SPRITE_FLAGS,4),r.state()); assertEquals(1,h.solids.size());
        assertEquals(0,count(r,CdMonitor.NativeCall.ANIMATE_OBJECT));
    }

    @Test void animateAndDrawRoutinesHaveTheirDistinctEntryTails() {
        var h=prepared(6); h.run(); assertTrue(h.solids.isEmpty()); assertEquals(16,h.slot().unsignedByte(MAP_FRAME));
        assertEquals(List.of("DRAW"),h.order); h.order.clear(); h.edit(o -> o.moveByte(ROUTINE,8));
        var before=h.slot(); var r=h.run(); assertEquals(before,r.state()); assertEquals(0,count(r,CdMonitor.NativeCall.ANIMATE_OBJECT));
    }

    @Test void animationUsesRealNineEntrySequenceAndHoldsForOneCallBetweenEntries() {
        var h=prepared(6); int[] frames={16,4,4,8,4,4,9,4,4,16};
        for(int frame:frames) {
            h.run(); assertEquals(frame,h.slot().unsignedByte(MAP_FRAME));
            h.run(); assertEquals(frame,h.slot().unsignedByte(MAP_FRAME));
        }
    }

    @Test void fallingPublishesSharedOldVelocityMotionBeforeOnePrimaryAngleCommit() {
        var h=prepared(2); h.timeStop=1;
        h.edit(o -> o.moveLong(X_POSITION,0x02701234).moveLong(Y_POSITION,0x07355678)
                .moveWord(X_VELOCITY,0x100).moveWord(Y_VELOCITY,-0x180).moveByte(ROUTINE_2,4));
        h.run(); assertEquals(0x02711234,h.slot().longBits(X_POSITION));
        assertEquals(0x0733D678,h.slot().longBits(Y_POSITION)); assertEquals(-0x148,h.slot().signedWord(Y_VELOCITY));
        assertEquals(4,h.slot().unsignedByte(ROUTINE_2)); assertEquals(List.of("ANGLE","DRAW"),h.order);
        assertEquals(0,h.primaryWrites.getFirst().intValue()); assertEquals(1,h.primaryWrites.size());
        assertTrue(h.solids.isEmpty()); assertEquals(0,h.solidGlobals.primaryAngle());
    }

    @Test void floorLandingKeepsFractionsAndOddGlobalAngleButClearsWordVelocityAndFallByte() {
        var h=new Harness(true); h.prepare(2); h.timeStop=1;
        h.edit(o -> o.moveLong(Y_POSITION,0x077A5678).moveWord(Y_VELOCITY,0).moveByte(ROUTINE_2,4));
        var r=h.run(); assertEquals(0x07715678,r.state().longBits(Y_POSITION));
        assertEquals(0,r.state().word(Y_VELOCITY)); assertEquals(0,r.state().unsignedByte(ROUTINE_2));
        assertEquals(0x23,h.solidGlobals.primaryAngle());
        var floor=r.effects().stream().filter(CdMonitor.FloorProbed.class::isInstance)
                .map(CdMonitor.FloorProbed.class::cast).findFirst().orElseThrow().result();
        assertEquals(-9,floor.distance()); assertEquals(0,floor.angleResult());
        assertEquals(List.of("ANGLE","DRAW"),h.order);
    }

    @Test void zeroFloorDistanceDoesNotClearFallAndSameValueAngleStoreStillCallsPort() {
        var h=new Harness(true); h.prepare(2); h.timeStop=1;
        h.solidGlobals=new CdSolidObject.Globals(0,4,0x23,0x34);
        h.edit(o -> o.moveWord(Y,0x771).moveWord(Y_VELOCITY,0).moveByte(ROUTINE_2,4));
        h.run(); assertEquals(0x771,h.slot().centreY()); assertEquals(0x38,h.slot().word(Y_VELOCITY));
        assertEquals(4,h.slot().unsignedByte(ROUTINE_2)); assertEquals(List.of(0x23),h.primaryWrites);
    }

    @ParameterizedTest @CsvSource({"4,-384,-384", "8,-384,-384", "2,-2304,-2304", "0,32752,-32728", "0,4064,4096"})
    void rawControlScratchAndWordOverflowComeFromSharedGravity(int control,int oldY,int newY) {
        var h=prepared(2); h.timeStop=1;
        h.edit(o -> o.moveWord(Y,0x735).moveByte(RAW_CONTROL_2C,control).moveByte(ROUTINE_2,4).moveWord(Y_VELOCITY,oldY));
        h.run(); assertEquals(newY,h.slot().signedWord(Y_VELOCITY));
    }

    @Test void primaryCommitFailureKeepsIntegratedPrefixWithoutLandingAnimationOrDraw() {
        var h=prepared(2); h.failPrimary=true;
        h.edit(o -> o.moveLong(Y_POSITION,0x07355678).moveWord(Y_VELOCITY,-0x180).moveByte(ROUTINE_2,4));
        var before=h.slot(); assertThrows(IllegalStateException.class,h::run);
        assertEquals(before.moveLong(Y_POSITION,0x0733D678).moveWord(Y_VELOCITY,-0x148),h.slot());
        assertTrue(h.draws.isEmpty()); assertEquals(List.of("ANGLE"),h.order);
    }

    @Test void standingUsesActualSolidAndCommitsPlayerAndOtherStandingObjectBeforeDraw() {
        var h=prepared(2); h.timeStop=1; var p=new P(); p.routine=2; p.x=0x02701234; p.y=0x07578765;
        p.flags=10; p.stand=42; p.yv=0x100; p.xv=0x180; p.angle=0xAA; h.player=p.player();
        h.pool().write(h.pool().state(42).moveByte(ID,0x7F).moveByte(FLAGS,8));
        var r=h.run(); var m=r.finalPlayer().actor().motion();
        assertEquals(CdSolidObject.Path.STAND_ON,h.solids.getFirst().result().path());
        assertEquals(WHOLE,r.finalPlayer().standObject()); assertEquals(0,m.yVelocity()); assertEquals(0,m.angle());
        assertEquals(0x180,m.groundVelocity()); assertEquals(0x07578765,m.centreY16_16());
        assertEquals(8,r.state().flags()&8); assertEquals(0,h.pool().state(42).flags()&8);
        assertEquals(1,h.solids.getFirst().result().otherSlots().size());
    }

    @Test void rollingStatusRejectsMonitorTopThroughSharedSolidNotAnimationTwo() {
        var h=prepared(2); h.timeStop=1; var p=new P(); p.routine=2; p.flags=6; p.anim=0;
        p.x=0x02700000; p.y=0x07570000; h.player=p.player(); h.run();
        assertEquals(CdSolidObject.GetOffReason.MONITOR_ROLLING,h.solids.getFirst().result().getOffReason().orElseThrow());
        assertEquals(0,h.slot().flags()&8);
    }

    @Test void missingSolidObjectOrOtherSlotPublicationRejectsBeforeAnimateDraw() {
        var h=prepared(2); h.publishObject=false; h.edit(o -> o.moveByte(FLAGS,0x28));
        // Matching stood-on bits/link make native GetOffObject clear the object's bit3.
        var p=new P(); p.flags=0x28; p.push=WHOLE; p.stand=WHOLE; h.player=p.player();
        assertThrows(IllegalStateException.class,h::run); assertTrue(h.draws.isEmpty());
        var k=prepared(2); k.timeStop=1; k.publishOthers=false;
        var q=new P(); q.routine=2; q.x=0x02700000; q.y=0x07570000; q.flags=10; q.stand=42; k.player=q.player();
        k.pool().write(k.pool().state(42).moveByte(ID,0x7F).moveByte(FLAGS,8));
        assertThrows(IllegalStateException.class,k::run); assertTrue(k.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2})
    void breakAllocatesItemThenExplosionFromLiveIdOnlyPoolWithZeroOneOrTwoFree(int free) {
        var h=prepared(4); h.occupy(); if(free>0) h.pool().objNull(32); if(free>1) h.pool().objNull(41);
        h.edit(o -> o.moveByte(ANIMATION,4).moveByte(LAYER,1));
        var before=h.slot(); var r=h.run();
        assertEquals(List.of("FM","DRAW"),h.order); assertEquals(before,h.soundImages.getFirst());
        assertEquals(before.moveByte(ROUTINE,8).moveByte(COLLISION_TYPE,0).moveByte(MAP_FRAME,17)
                .moveByte(SPRITE_FLAGS,4),r.state());
        assertEquals(1,h.runtime.savedFlag(7,1)); assertEquals(List.of(CdMonitor.FmSound.FM_DESTROY),h.sounds);
        if(free>0) {
            var item=h.pool().state(32); assertEquals(0x1A,item.id()); assertEquals(4,item.unsignedByte(ANIMATION));
            assertEquals(before.centreX(),item.centreX()); assertEquals(before.centreY(),item.centreY()); assertEquals(1,item.subtype2());
        }
        if(free>1) { var e=h.pool().state(41); assertEquals(0x18,e.id()); assertEquals(1,e.unsignedByte(ROUTINE_2)); assertEquals(1,e.subtype()); }
        assertEquals(0,count(r,CdMonitor.NativeCall.CHECK_OBJ_DESPAWN)); assertTrue(h.solids.isEmpty());
        assertEquals(0,count(r,CdMonitor.NativeCall.ANIMATE_OBJECT));
    }

    @Test void breakPreservesDirtyFreeSlotFractionsRoutineAndAllUnwrittenBytes() {
        var h=prepared(4); h.occupy();
        var a=dirty(h.pool().state(32)).moveByte(ID,0); var b=dirty(h.pool().state(41)).moveByte(ID,0);
        h.pool().write(a); h.pool().write(b); var parent=h.slot(); h.run();
        assertEquals(a.moveByte(ID,0x1A).moveWord(X,parent.centreX()).moveWord(Y,parent.centreY())
                .moveByte(ANIMATION,4).moveByte(LAYER,0),h.pool().state(32));
        assertEquals(b.moveByte(ID,0x18).moveWord(X,parent.centreX()).moveWord(Y,parent.centreY())
                .moveByte(ROUTINE_2,1).moveByte(SUBTYPE,1).moveByte(LAYER,0),h.pool().state(41));
        assertEquals(6,h.pool().state(32).routine()); assertEquals(0x4321,h.pool().state(32).word(X_FRACTION));
    }

    @Test void breakNeverDispatchesEarlierOrLaterAllocatedChildrenItself() {
        var h=prepared(4); h.occupy(); h.pool().deleteObject(32); h.pool().deleteObject(41); h.run();
        assertEquals(0,h.pool().state(32).routine()); assertEquals(0,h.pool().state(41).routine());
        assertEquals(0,h.pool().state(32).word(Y_VELOCITY));
        // RunObjects' next physical visits belong to Runtime, not a recursive body call.
        var later=prepared(4); later.occupy(); later.pool().deleteObject(41); later.pool().deleteObject(42); later.run();
        assertEquals(0x1A,later.pool().state(41).id()); assertEquals(0x18,later.pool().state(42).id());
        assertEquals(0,later.pool().state(41).routine());
    }

    @Test void breakSoundFailurePublishesNoBreakPrefixAndSavedFailureRetainsBothLiveChildren() {
        var h=prepared(4); h.failSound=true; var before=h.runtime.capture();
        assertThrows(IllegalStateException.class,h::run); assertEquals(before.pool().slots(),h.pool().capture().slots());
        assertArrayEquals(before.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
        var k=prepared(4); k.edit(o -> o.moveByte(SAVED_FLAGS_ID,255));
        assertEquals(CdMonitor.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,assertThrows(CdMonitor.UnsupportedMonitor.class,k::run).boundary());
        assertEquals(8,k.slot().routine()); assertEquals(0,k.slot().unsignedByte(COLLISION_TYPE));
        assertEquals(0x1A,k.pool().state(32).id()); assertEquals(0x18,k.pool().state(33).id());
        assertEquals(7,k.slot().unsignedByte(MAP_FRAME)); assertTrue(k.draws.isEmpty());
    }

    @Test void breakDoesNotGetPlayerOffObjectAwardPointsOrReplayOnRoutineEight() {
        var h=prepared(4); var p=new P(); p.flags=8; p.stand=WHOLE; p.charge=9; h.player=p.player();
        h.edit(o -> o.moveByte(FLAGS,8)); var player=h.player; var globals=h.solidGlobals;
        h.run(); assertEquals(player,h.player); assertEquals(globals,h.solidGlobals); assertEquals(8,h.slot().flags());
        h.run(); assertEquals(1,h.sounds.size()); assertTrue(h.solids.isEmpty());
    }

    @ParameterizedTest @CsvSource({"2560,false", "2688,true"})
    void despawnUsesUnsignedChunkEqualityAndPreservesDestroyedFlag(int x,boolean deleted) {
        var h=prepared(8); h.camera=0x800; h.edit(o -> o.moveWord(X,x));
        h.runtime.bsetSavedFlag(7,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
        h.runtime.bsetSavedFlag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        assertEquals(deleted,h.run().deleted()); assertEquals(deleted?1:129,h.runtime.savedFlag(7,1));
    }

    @Test void drawReloadsLiveFlagsBeforeDespawnAndWarpForcesTheSourcePeriodUnload() {
        var h=prepared(8); h.camera=0x6000; h.drawFlags=0x84; assertFalse(h.run().deleted());
        h.time=0x82; h.direction=1; h.runtime.bsetSavedFlag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        h.runtime.bsetSavedFlag(7,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
        assertTrue(h.run().deleted()); assertEquals(1,h.runtime.savedFlag(7,1));
    }

    @Test void resultsAreImmutableAndNullCallbacksNeverInstallNoops() {
        var h=prepared(8); var r=h.run(); assertThrows(UnsupportedOperationException.class,()->r.effects().clear());
        assertThrows(NullPointerException.class,()->new CdMonitor(h.runtime,assets(),h.solid,h.probes,null,h::primary,h::draw,h::sound));
        assertThrows(NullPointerException.class,()->new CdMonitor(h.runtime,assets(),h.solid,h.probes,h::commit,null,h::draw,h::sound));
        assertThrows(NullPointerException.class,()->new CdMonitor(h.runtime,assets(),h.solid,h.probes,h::commit,h::primary,null,h::sound));
        assertThrows(NullPointerException.class,()->new CdMonitor(h.runtime,assets(),h.solid,h.probes,h::commit,h::primary,h::draw,null));
    }

    static long count(CdMonitor.Result r,CdMonitor.NativeCall call) {
        return r.effects().stream().filter(e -> e instanceof CdMonitor.CallReturned x && x.call()==call).count();
    }
    static Harness prepared(int routine) { var h=new Harness(false); h.prepare(routine); return h; }
    static CdMonitor.Assets assets() {
        return new CdMonitor.Assets(CdObjectVisualAssets.decode(HexFormat.of().parseHex(MAP_HEX),20,
                HexFormat.of().parseHex(ANI_HEX),10,82),MAP);
    }
    static CdObjectPlacements layout() {
        var b=ByteBuffer.allocate(34);
        b.putShort((short)12).putShort((short)28).putShort((short)0xFFFF).putInt(0).putShort((short)0);
        b.putShort((short)0x270).putShort((short)0x778).put((byte)0x99).put((byte)4).put((byte)0xE0).put((byte)0);
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
                .moveByte(LAYER,0).moveLong(MOSQUI_X_DELTA,0x23456789).moveLong(MOSQUI_ANIMATION_SCRIPT,0x3456789A)
                .moveWord(RING_ANCHOR_X,0x4567).moveWord(SPRING_ANCHOR_X,0x5678)
                .moveWord(TUNNEL_DOOR_TIMER,0x6789).moveWord(EXPLOSION_POINTS_FRAME,0x789A).moveByte(STAND_LINK_3D,42);
    }
    static final class Harness {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(layout());
        final CdPlayerTerrain probes; final CdSolidObject solid; final CdMonitor body;
        final List<CdMonitor.SolidCall> solids=new ArrayList<>();
        final List<CdMonitor.FmSound> sounds=new ArrayList<>();
        final List<CdObjectState> soundImages=new ArrayList<>(),draws=new ArrayList<>();
        final List<Integer> primaryWrites=new ArrayList<>(); final List<String> order=new ArrayList<>();
        CdSolidObject.Player player=new P().player();
        CdSolidObject.Globals solidGlobals=new CdSolidObject.Globals(0,4,0x12,0x34);
        CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);
        int time=1,direction=0,camera=0x200,attack=0,zone=0,layer=0,timeStop=0,drawFlags=-1;
        boolean failSound,failPrimary,publishObject=true,publishOthers=true;
        Harness(boolean floor) {
            var terrain=terrain(floor); probes=new CdPlayerTerrain(terrain); solid=solid(terrain);
            runtime.resetSavedObjectFlags();
            body=new CdMonitor(runtime,assets(),solid,probes,this::commit,this::primary,this::draw,this::sound);
        }
        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> f) { pool().write(f.apply(slot())); }
        void prepare(int routine) {
            edit(o -> o.moveByte(ID,0x19).moveByte(ROUTINE,routine).moveByte(SUBTYPE,4).moveByte(SAVED_FLAGS_ID,7)
                    .moveByte(SPRITE_FLAGS,0x84).moveWord(X,0x270).moveWord(Y,0x778).moveByte(Y_RADIUS,14)
                    .moveByte(X_RADIUS,14).moveByte(WIDTH,15).moveByte(PRIORITY,3).moveByte(ANIMATION,4)
                    .moveByte(MAP_FRAME,7).moveByte(COLLISION_TYPE,0x46).moveLong(MAPPINGS,MAP));
        }
        void occupy() { for(int i=32;i<128;i++) if(i!=WHOLE) pool().write(pool().state(i).moveByte(ID,0x7F)); }
        CdMonitor.Result run() {
            return body.run(WHOLE,new CdMonitor.Globals(time,direction,camera,attack,zone,layer,timeStop),
                    new CdMonitor.SolidInputs(player,gates,solidGlobals));
        }
        void commit(CdMonitor.SolidCall call) {
            order.add("SOLID"); solids.add(call); assertEquals(player,call.expectedPlayer()); assertEquals(solidGlobals,call.expectedGlobals());
            var images=new ArrayList<CdObjectState>(); images.add(call.result().object()); images.addAll(call.result().otherSlots());
            for(var o:images) assertEquals(call.callCut().slots().get(o.identity().dynamicIndex()),pool().state(o.identity().wholeIndex()));
            if(publishObject) pool().write(call.result().object()); if(publishOthers) call.result().otherSlots().forEach(pool()::write);
            player=call.result().player(); solidGlobals=call.result().globals();
        }
        void primary(int expected,int value) {
            order.add("ANGLE"); assertEquals(solidGlobals.primaryAngle(),expected); primaryWrites.add(value);
            if(failPrimary) throw new IllegalStateException("constructed primary rejection");
            solidGlobals=new CdSolidObject.Globals(solidGlobals.invincible(),solidGlobals.scoreChain(),value,solidGlobals.secondaryAngle());
        }
        void sound(CdMonitor.FmSound sound) {
            order.add("FM"); sounds.add(sound); soundImages.add(slot());
            if(failSound) throw new IllegalStateException("constructed FM rejection");
        }
        void draw(CdObjectPool pool,CdObjectState.Identity identity) {
            assertSame(pool(),pool); order.add("DRAW"); var o=pool.state(identity.wholeIndex()); draws.add(o);
            if(drawFlags>=0) { pool.write(o.moveByte(SPRITE_FLAGS,drawFlags)); return; }
            drawThroughQueue(pool,identity,camera);
        }
    }
    static void drawThroughQueue(CdObjectPool pool,CdObjectState.Identity identity,int camera) {
        var slots=new CdObjectDrawQueue.CdDrawSlots() {
            public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                var o=pool.state(ref.wholeIndex());
                return new CdObjectDrawQueue.LiveDrawSlot(o.id(),o.spriteFlags(),o.centreX(),o.centreY(),o.word(X_FRACTION),
                        o.longBits(MAPPINGS),o.unsignedByte(MAP_FRAME),o.word(TILE));
            }
            public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value) {
                var o=pool.state(ref.wholeIndex()); pool.write(o.moveByte(SPRITE_FLAGS,(o.spriteFlags()&127)|(value?128:0)));
            }
        };
        try(var queue=new CdObjectDrawQueue(slots,(address,statik,frame)->{
            throw new AssertionError("DrawObject cannot resolve mappings before DrawObjects");
        },new byte[640])) {
            queue.beginPass(); var o=pool.state(identity.wholeIndex());
            queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(identity.wholeIndex()),
                    o.spriteFlags(),o.centreX(),o.centreY(),o.unsignedByte(Y_RADIUS),o.unsignedByte(WIDTH),o.unsignedByte(PRIORITY)),
                    new CdObjectDrawQueue.CameraWords(camera,0));
        }
    }
    static final class P {
        int x=0x02708000,y=0x07571234,xv=0x123,yv=0x400,gv=0,routine=6,angle=0x10,flags=2;
        int spr=4,xr=9,yr=19,anim=0,prev=0,jump=1,charge=0,lock=0,stick=0,ctrl=0;
        int pri=0x56,sec=0x78,tile=0x780,priority=2,hurt=0,rot=0,push=0,stand=0;
        CdSolidObject.Player player() {
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x,y,xv,yv,gv,routine,angle,
                    flags,spr,xr,yr,anim,prev,jump,charge,lock,stick,ctrl,pri,sec),tile,priority,hurt,rot),1,push,stand);
        }
    }
    static CdR11aTerrain terrain(boolean floor) {
        byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256];
        byte[] heights=new byte[4096],widths=new byte[4096]; fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;fg[2+7*42+2]=2;
        if(floor) { int at=512+(8*16+7)*2; chunks[at]=0x20;chunks[at+1]=1;ids[1]=7;angles[7]=0x23;
            Arrays.fill(heights,7*16,8*16,(byte)16); }
        return CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);
    }
    static CdSolidObject solid(CdR11aTerrain terrain) {
        byte[] animations=new byte[578];for(int i=0;i<60;i++) word(animations,i*2,120);animations[122]=(byte)0xFF;
        byte[] mappings=new byte[398],dplcs=new byte[398];for(int i=0;i<198;i++){word(mappings,i*2,396);word(dplcs,i*2,396);}
        var visual=CdSonicVisualAssets.decode(mappings,dplcs,new byte[69472]); byte[] atan=new byte[257];
        for(int i=0;i<=256;i++) atan[i]=(byte)(i/8);
        var a=CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)0xFF},new byte[4],new byte[640],atan,visual);
        return new CdSolidObject(new CdPlayerMovement(terrain,a));
    }
    static void word(byte[] bytes,int at,int value) { bytes[at]=(byte)(value>>>8);bytes[at+1]=(byte)value; }
}
