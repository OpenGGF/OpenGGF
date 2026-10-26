package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests first, frozen before the single production pass. ALL JAVA UNRUN.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Spin Tunnel/Main (Door).asm:7-126;
 * Data/Animations (Door).asm:1-3; Data/Mappings (Door).asm:1-27; Object Spawner.asm:329-446.
 * Constructed native-format fixtures confer no media authority. No injected Solid.Result/probe answer.
 * The real CPU pool, reserved bank, Solid, animator, format decoders and DrawObject queue are used.
 */
class TestCdTunnelDoor {
    private static final int WHOLE = 40, MAP = 0xCD1000;
    private static final byte[] MAPS = bytes(0,16,0,4,2,0xD0,3,0,0,0xE4,0xF0,1,0,4,0xE4,0,
            2,0xF8,12,0,6,0xE8,0xF8,4,0,10,8,0);
    private static final byte[] ANIM = bytes(0,2,0,0,0,1,1,1,1,1,1,1,1,0xFC);

    @Test void dirtyInitFallsIntoMainWithOnlyNativeWritesAndBothActualSolidCalls() {
        var h = prepared(0); h.edit(TestCdTunnelDoor::dirty);
        var before = h.slot(); var r = h.run();
        var expected = before.moveByte(ROUTINE,2).moveLong(MAPPINGS,MAP).moveByte(PRIORITY,1)
                .moveByte(SPRITE_FLAGS,0x35).moveByte(WIDTH,0x2C).moveByte(Y_RADIUS,8)
                .moveWord(TILE,0x8328).moveByte(SOLID_TYPE,0);
        assertEquals(expected, r.state()); assertEquals(expected, h.draws.getFirst());
        assertEquals(List.of("INIT","MAIN","PRIMARY","SECONDARY","DRAW_OBJECT","CHECK_OBJ_DESPAWN"), trace(r));
        assertEquals(2,h.calls.size()); assertEquals(h.player,r.finalPrimary()); assertEquals(h.globals,r.finalGlobals());
        assertEquals(0x4321,r.state().word(X_FRACTION)); assertEquals(0x8765,r.state().word(Y_FRACTION));
        assertEquals(9,r.state().unsignedByte(MAP_FRAME)); assertEquals(0x12AB,r.state().word(TUNNEL_DOOR_TIMER));
    }

    @ParameterizedTest @CsvSource({"0,44","1,44","2,24","255,44"})
    void initUsesOnlySubtypeTwoForNarrowWidth(int subtype,int width) {
        var h = prepared(0); h.edit(o -> o.moveByte(SUBTYPE,subtype)); h.run();
        assertEquals(width,h.slot().unsignedByte(WIDTH)); assertEquals(8,h.slot().unsignedByte(Y_RADIUS));
        assertEquals(0x8328,h.slot().word(TILE));
    }

    @ParameterizedTest @CsvSource({"231,false","232,true","279,true","280,false"})
    void narrowInitUsesItsNewWidthInTheSamePassOpeningCheck(int playerX,boolean opens) {
        var h=prepared(0); h.edit(o -> o.moveByte(SUBTYPE,2)); var p=new P(); p.x=(playerX<<16)|0x1234; p.yv=-1;
        h.player=p.player(); var r=h.run(); assertEquals(opens?4:2,r.state().routine());
        assertEquals(24,r.state().unsignedByte(WIDTH)); assertEquals(7,r.state().unsignedByte(MAP_FRAME));
        assertEquals(opens?1:2,h.calls.size()); assertTrue(h.sounds.isEmpty());
    }

    @Test void tileWordComesFromBoundSelectorC() {
        var tileIds=tiles(); word(tileIds,62,0x1357);
        var h=new Harness(assets(tileIds)); h.edit(o -> normal(o,0)); h.run();
        assertEquals(0x1357,h.slot().word(TILE));
    }

    @ParameterizedTest @ValueSource(ints={1,2,255})
    void unsupportedSubtypeTwoStopsAfterThePublishedInitPrefix(int subtype2) {
        var h=prepared(0); h.edit(o -> o.moveByte(SUBTYPE2,subtype2)); var before=h.slot();
        var e=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,h::run);
        assertEquals(CdTunnelDoor.UnsupportedBoundary.TILE_ID_SUBTYPE2,e.boundary()); assertEquals(subtype2,e.value());
        assertEquals(before.moveByte(ROUTINE,2).moveLong(MAPPINGS,MAP).moveByte(PRIORITY,1)
                .moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,44).moveByte(Y_RADIUS,8),h.slot());
        assertTrue(h.order.isEmpty());
    }

    @Test void tileDecoderFailureRetainsTheInitPrefix() {
        var t=tiles(); word(t,24,0xFFFF); var h=new Harness(assets(t)); h.edit(o -> normal(o,0));
        assertThrows(IllegalArgumentException.class,h::run);
        assertEquals(2,h.slot().routine()); assertEquals(MAP,h.slot().longBits(MAPPINGS)); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @CsvSource({"-32768,true","-1,true","0,false","1,false","32767,false"})
    void onlySignedNegativeYVelocityOpens(int velocity,boolean opens) {
        var h=prepared(2); var p=new P(); p.yv=velocity; h.player=p.player(); h.run();
        assertEquals(opens?4:2,h.slot().routine()); assertEquals(opens?1:2,h.calls.size()); assertTrue(h.sounds.isEmpty());
    }

    @ParameterizedTest @CsvSource({"256,211,false","256,212,true","256,255,true","256,256,true",
            "256,299,true","256,300,false","0,65535,true","65535,0,true",
            "32768,32724,true","32768,32812,false","0,32768,false","32768,0,false"})
    void openingXUsesBmiAndUnsignedWordEdges(int objectX,int playerX,boolean opens) {
        var h=prepared(2); h.edit(o -> o.moveWord(X,objectX)); var p=new P(); p.x=(playerX<<16)|0x1234; p.yv=-1;
        h.player=p.player(); h.run(1,0,(objectX+128)&65535);
        assertEquals(opens?4:2,h.slot().routine());
    }

    @ParameterizedTest @CsvSource({"128,119,false","128,120,true","128,127,true","128,128,true",
            "128,135,true","128,136,false","0,65535,true","65535,0,true","0,32768,false","32768,0,false"})
    void openingYUsesBmiAndUnsignedWordEdges(int objectY,int playerY,boolean opens) {
        var h=prepared(2); h.edit(o -> o.moveWord(Y,objectY)); var p=new P(); p.y=(playerY<<16)|0x7654; p.yv=-1;
        h.player=p.player(); h.run(); assertEquals(opens?4:2,h.slot().routine());
    }

    @ParameterizedTest @ValueSource(ints={1,2})
    void nonzeroSubtypeOpeningDoesNotAllocateOrRequestFm(int subtype) {
        var h=prepared(2); h.edit(o -> o.moveByte(SUBTYPE,subtype)); var p=new P(); p.yv=-1; h.player=p.player();
        var child=h.pool().state(32); var r=h.run();
        assertEquals(child,h.pool().state(32)); assertTrue(h.sounds.isEmpty()); assertEquals(4,h.slot().routine());
        assertEquals(List.of("MAIN","SECONDARY","DRAW_OBJECT","CHECK_OBJ_DESPAWN"),trace(r));
    }

    @Test void subtypeZeroAllocatesDirtySlotAndPublishesItBeforeFmAndSecondarySolid() {
        var h=prepared(2); h.edit(o -> o.moveByte(SUBTYPE,0)); var p=new P(); p.yv=-1; h.player=p.player();
        var free=dirty(h.pool().state(32)).moveByte(ID,0).moveByte(ROUTINE,0xAA);
        h.pool().write(free); var r=h.run();
        var expected=free.moveByte(ID,0x0B).moveWord(X,0x100).moveWord(Y,0x7C);
        assertEquals(expected,h.pool().state(32)); assertEquals(expected,h.soundChildren.getFirst());
        assertEquals(4,h.soundParents.getFirst().routine()); assertEquals(List.of("FM","PREFLIGHT:SECONDARY","COMMIT:SECONDARY","DRAW"),h.order);
        assertEquals(List.of(CdTunnelDoor.FmSound.FM_A4),h.sounds);
        var spawned=assertInstanceOf(CdTunnelDoor.SplashCreated.class,r.effects().get(1));
        assertEquals(expected,spawned.state()); assertFalse(spawned.laterInPhysicalPass());
        assertEquals(0x4321,expected.word(X_FRACTION)); assertEquals(0x8765,expected.word(Y_FRACTION));
    }

    @Test void fullPoolLeavesRoutineFourWithoutSplashOrFm() {
        var h=prepared(2); h.edit(o -> o.moveByte(SUBTYPE,0)); var p=new P(); p.yv=-1; h.player=p.player();
        for(int i=32;i<128;i++) if(i!=WHOLE) h.pool().write(h.pool().state(i).moveByte(ID,0x7E));
        var before=h.pool().capture(); var r=h.run();
        assertEquals(4,h.slot().routine()); assertTrue(h.sounds.isEmpty());
        assertTrue(r.effects().stream().noneMatch(CdTunnelDoor.SplashCreated.class::isInstance));
        for(int i=32;i<128;i++) if(i!=WHOLE) assertEquals(before.slots().get(i-32),h.pool().state(i));
    }

    @ParameterizedTest @CsvSource({"32,false","41,true"})
    void allocationReportsPhysicalEarlierOrLaterSchedulingWithoutDispatchingChild(int freeWhole,boolean later) {
        var h=prepared(2); h.edit(o -> o.moveByte(SUBTYPE,0)); var p=new P(); p.yv=-1; h.player=p.player();
        for(int i=32;i<freeWhole;i++) if(i!=WHOLE) h.pool().write(h.pool().state(i).moveByte(ID,0x7E));
        var r=h.run(); var created=assertInstanceOf(CdTunnelDoor.SplashCreated.class,r.effects().get(1));
        assertEquals(freeWhole,created.state().identity().wholeIndex()); assertEquals(later,created.laterInPhysicalPass());
        assertEquals(0,h.pool().state(freeWhole).routine()); assertEquals(0x0B,h.pool().state(freeWhole).id());
    }

    @Test void secondaryReadsTheActualBankAndRunsRealAbsentTopSolidRatherThanReusingPrimary() {
        var h=prepared(2); var real=h.bank.state(1); var r=h.run();
        assertEquals(2,h.calls.size()); var c=h.calls.get(1);
        assertSame(real,c.cut().secondary().orElseThrow()); assertEquals(1,real.identity().wholeIndex());
        assertEquals(CdSolidObject.Entry.TOP_SOLID_OBJECT,c.result().entry());
        assertEquals(CdSolidObject.GetOffReason.PLAYER_ABSENT,c.result().getOffReason().orElseThrow());
        assertEquals(CdSolidObject.GetOffReason.PLAYER_DEAD,h.calls.getFirst().result().getOffReason().orElseThrow());
        assertTrue(c.result().playerWrites().isEmpty()); assertTrue(c.result().globalWrites().isEmpty());
        assertTrue(c.result().otherSlots().isEmpty()); assertEquals(real,h.bank.state(1));
        assertEquals(h.player,r.finalPrimary()); assertEquals(1,r.finalPrimary().slotId());
    }

    @Test void firstSolidPublishesPlayerObjectOtherSlotAndGlobalsBeforeFreshSecondaryCut() {
        var h=standing(); var before=h.player; var r=h.run(); var a=h.calls.getFirst(); var b=h.calls.get(1);
        assertNotEquals(before,a.result().player()); assertEquals(a.result().player(),b.cut().expectedPrimary());
        assertEquals(a.result().globals(),b.cut().expectedGlobals());
        assertEquals(a.result().object(),b.cut().callCut().slots().get(WHOLE-32));
        assertEquals(0xA0,b.cut().callCut().slots().get(9).flags()); assertEquals(0xA0,h.pool().state(41).flags());
        assertEquals(a.result().player(),r.finalPrimary()); assertEquals(a.result().globals(),r.finalGlobals());
        assertEquals(CdSolidObject.Path.STAND_ON,a.result().path()); assertNotSame(a.cut().callCut(),b.cut().callCut());
    }

    @ParameterizedTest @ValueSource(ints={0,1,18,61,63})
    void anyNonclearedSecondaryByteStopsAtItsCutAndPreservesFirstPlayerPrefix(int offset) {
        var h=standing(); h.bank.write(h.bank.state(1).moveByteAt(offset,1));
        var e=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,h::run);
        assertEquals(CdTunnelDoor.UnsupportedBoundary.SECONDARY_PLAYER_OWNER,e.boundary()); assertEquals(WHOLE,e.wholeIndex());
        assertEquals(offset,e.value()); assertEquals(1,h.calls.size());
        assertEquals(h.calls.getFirst().result().player(),h.player); assertEquals(0xA0,h.pool().state(41).flags());
        assertEquals(1,h.bank.state(1).byteAt(offset)); assertTrue(h.draws.isEmpty());
    }

    @Test void defensiveSecondaryAdmissionRejectsARealSideEffectingSharedReturnBeforePublication() {
        var h=standing(); var input=h.player; var original=h.slot();
        var result=h.solid.run(CdSolidObject.Entry.TOP_SOLID_OBJECT,original,input,h.gates,h.globals,h.pool().capture());
        assertFalse(result.playerWrites().isEmpty());
        var e=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,
                () -> CdTunnelDoor.requireInactiveSecondaryReturn(WHOLE,result,input,h.globals));
        assertEquals(CdTunnelDoor.UnsupportedBoundary.SECONDARY_SOLID_OWNER,e.boundary());
        assertEquals(original,h.slot()); assertEquals(0xA8,h.pool().state(41).flags()); assertEquals(input,h.player);
    }

    @Test void defensiveSecondaryAdmissionRejectsTheRealStandLinkZeroPrimaryAlias() {
        var h=standing(); var p=new P(); p.routine=2; p.y=0x00661234; p.flags=8; p.stand=0; h.player=p.player();
        var original=h.slot();
        var result=h.solid.run(CdSolidObject.Entry.TOP_SOLID_OBJECT,original,h.player,h.gates,h.globals,h.pool().capture());
        assertEquals(CdSolidObject.Path.STAND_ON,result.path()); assertEquals(0,result.player().actor().motion().flags()&8);
        var e=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,
                () -> CdTunnelDoor.requireInactiveSecondaryReturn(WHOLE,result,h.player,h.globals));
        assertEquals(CdTunnelDoor.UnsupportedBoundary.SECONDARY_SOLID_OWNER,e.boundary());
        assertEquals(original,h.slot()); assertEquals(8,h.player.actor().motion().flags());
    }

    @Test void delayZeroAnimatesTenFramesThenFcAndOnlyNextDispatchResets() {
        var h=prepared(4); h.edit(o -> o.moveByte(PREVIOUS_ANIMATION,1).moveByte(MAP_FRAME,7));
        int[] frames={0,0,1,1,1,1,1,1,1,1};
        for(int i=0;i<frames.length;i++) {
            var r=h.run(); assertEquals(frames[i],r.state().unsignedByte(MAP_FRAME));
            assertEquals(i+1,r.state().unsignedByte(ANIMATION_FRAME)); assertEquals(0,r.state().unsignedByte(ANIMATION_TIME));
            assertEquals(4,r.state().routine()); assertEquals(List.of("ANIMATE","DRAW_OBJECT","CHECK_OBJ_DESPAWN"),trace(r));
        }
        var fc=h.run(); assertEquals(6,fc.state().routine()); assertEquals(1,fc.state().unsignedByte(MAP_FRAME));
        assertEquals(10,fc.state().unsignedByte(ANIMATION_FRAME));
        var reset=h.run(); assertEquals(2,reset.state().routine()); assertEquals(0,reset.state().unsignedByte(MAP_FRAME));
        assertEquals(1,reset.state().unsignedByte(PREVIOUS_ANIMATION)); assertTrue(h.calls.isEmpty());
        assertEquals(List.of("RESET","DRAW_OBJECT","CHECK_OBJ_DESPAWN"),trace(reset));
        h.edit(o -> o.moveByte(ROUTINE,4)); h.run(); assertEquals(1,h.slot().unsignedByte(ANIMATION_FRAME));
    }

    @Test void invalidAnimationHasNoPublicationOrDraw() {
        var h=prepared(4); h.edit(o -> o.moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,0)); var before=h.slot();
        assertThrows(UnsupportedOperationException.class,h::run); assertEquals(before,h.slot()); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints={1,3,5,7,8,254,255})
    void unsupportedRoutineByteStopsBeforeAnyWrite(int routine) {
        var h=prepared(routine); var before=h.slot(); var e=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,h::run);
        assertEquals(CdTunnelDoor.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary()); assertEquals(routine,e.value());
        assertEquals(before,h.slot()); assertTrue(h.order.isEmpty());
    }

    @ParameterizedTest @CsvSource({"640,128,false","768,128,true","65535,0,false","0,128,false","0,512,true","128,0,false"})
    void allWrappersUseRealDrawThenWrappedUnsignedDespawn(int x,int camera,boolean deleted) {
        var h=prepared(6); h.edit(o -> o.moveWord(X,x)); var r=h.run(0,0,camera);
        assertEquals(0x84,h.draws.getFirst().spriteFlags()); assertEquals(deleted,r.deleted());
        if(deleted) assertArrayEquals(new byte[64],r.state().copyBytes()); else assertEquals(4,r.state().spriteFlags());
        assertEquals(List.of("DRAW"),h.order);
    }

    @ParameterizedTest @CsvSource({"129,0,1","129,1,0","129,255,2","128,1,0","130,255,2","129,127,0","129,128,2"})
    void timeWarpClearsOnlyActualLoadedBitInSelectedSignedClampedPeriod(int rawZone,int direction,int period) {
        var h=prepared(6); h.edit(o -> o.moveByte(SAVED_FLAGS_ID,7)); seed(h,7);
        var expected=h.runtime.capture().savedFlags().copy(); expected[23+period]=0x45;
        var r=h.run(rawZone,direction,128); assertTrue(r.deleted());
        assertArrayEquals(expected,h.runtime.capture().savedFlags().copy());
        assertTrue(r.effects().contains(new CdTunnelDoor.LoadedCleared(7,period)));
    }

    @Test void currentPostDrawBitSevenControlsOffscreenBranchButWarpStillDeletes() {
        var h=prepared(6); h.edit(o -> o.moveWord(X,0x380));
        h.afterDraw=p -> p.write(p.state(WHOLE).moveByte(SPRITE_FLAGS,0x84));
        assertFalse(h.run().deleted()); assertEquals(0x84,h.slot().spriteFlags());
        assertTrue(h.run(0x81,0,128).deleted());
    }

    @Test void savedBankBoundsFailAfterResetAndDrawButBeforeDelete() {
        var h=prepared(6); h.edit(o -> o.moveWord(X,0x380).moveByte(SAVED_FLAGS_ID,254).moveByte(MAP_FRAME,9));
        var before=h.slot(); var bank=h.runtime.capture().savedFlags().copy();
        assertThrows(IndexOutOfBoundsException.class,() -> h.run(2,0,128));
        assertEquals(before.moveByte(PREVIOUS_ANIMATION,1).moveByte(MAP_FRAME,0).moveByte(ROUTINE,2)
                .moveByte(SPRITE_FLAGS,4),h.slot()); assertArrayEquals(bank,h.runtime.capture().savedFlags().copy());
    }

    @Test void savedIdZeroDeletesWithoutAnySavedBankAccess() {
        var h=prepared(6); seed(h,7); var before=h.runtime.capture().savedFlags().copy();
        assertTrue(h.run(255,128,128).deleted()); assertArrayEquals(before,h.runtime.capture().savedFlags().copy());
    }

    @Test void preflightFailureStopsBeforeActualHelperCommit() {
        var h=prepared(0); h.failPreflight=true;
        assertThrows(IllegalStateException.class,h::run); assertEquals(2,h.slot().routine());
        assertEquals(0x8328,h.slot().word(TILE)); assertTrue(h.calls.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void preflightCannotChangeDynamicCutBeforeHelper() {
        var h=prepared(2); h.preflightHook=c -> h.pool().write(h.pool().state(55).moveByte(ID,1));
        assertThrows(IllegalStateException.class,h::run); assertTrue(h.calls.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void staleExpectedPrimaryIsRejectedByRootPreflight() {
        var h=prepared(2); var stale=h.inputs(); var p=new P(); p.x=0x12340000; h.player=p.player();
        assertThrows(IllegalStateException.class,() -> h.body.run(WHOLE,new CdTunnelDoor.Globals(1,0,128),stale));
        assertTrue(h.calls.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void commitFailureRetainsEarlierNativeInitPrefixWithoutDrawOrSecondCall() {
        var h=prepared(0); h.failCommit=true;
        assertThrows(IllegalStateException.class,h::run); assertEquals(1,h.calls.size());
        assertEquals(2,h.slot().routine()); assertEquals(0x8328,h.slot().word(TILE)); assertTrue(h.draws.isEmpty());
    }

    @Test void missingObjectPublicationIsRejected() {
        var h=prepared(2); h.edit(o -> o.moveByte(SOLID_TYPE,0x55)); h.publishObject=false;
        assertThrows(IllegalStateException.class,h::run); assertEquals(0x55,h.slot().unsignedByte(SOLID_TYPE));
        assertEquals(1,h.calls.size()); assertTrue(h.draws.isEmpty());
    }

    @Test void missingOtherSlotPublicationIsRejected() {
        var h=standing(); h.publishOthers=false; assertThrows(IllegalStateException.class,h::run);
        assertEquals(0xA8,h.pool().state(41).flags()); assertEquals(1,h.calls.size()); assertTrue(h.draws.isEmpty());
    }

    @Test void staleReturnedGlobalsAreRejectedAfterCommitWithoutSecondCall() {
        var h=prepared(2); h.staleReturn=true; assertThrows(IllegalStateException.class,h::run);
        assertEquals(1,h.calls.size()); assertTrue(h.draws.isEmpty());
    }

    @Test void secondaryPreflightMutationStopsBeforeUnownedCallAndKeepsFirstPrefix() {
        var h=standing(); h.preflightHook=c -> {
            if(c.participant()==CdTunnelDoor.Participant.SECONDARY) h.bank.write(h.bank.state(1).moveByteAt(63,7));
        };
        var e=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,h::run);
        assertEquals(CdTunnelDoor.UnsupportedBoundary.SECONDARY_PLAYER_OWNER,e.boundary());
        assertEquals(1,h.calls.size()); assertEquals(0xA0,h.pool().state(41).flags()); assertTrue(h.draws.isEmpty());
    }

    @Test void secondaryCommitCannotMutateTheInactiveImage() {
        var h=prepared(2); h.corruptSecondaryCommit=true; assertThrows(IllegalStateException.class,h::run);
        assertEquals(2,h.calls.size()); assertTrue(h.draws.isEmpty()); assertEquals(1,h.bank.state(1).byteAt(63));
    }

    @Test void fmFailureKeepsRoutineAndDirtySplashAlreadyPublishedWithoutSecondCallOrDraw() {
        var h=prepared(2); h.edit(o -> o.moveByte(SUBTYPE,0)); var p=new P(); p.yv=-1; h.player=p.player(); h.failFm=true;
        assertThrows(IllegalStateException.class,h::run); assertEquals(4,h.slot().routine());
        assertEquals(0x0B,h.pool().state(32).id()); assertEquals(0x7C,h.pool().state(32).centreY());
        assertTrue(h.calls.isEmpty()); assertTrue(h.draws.isEmpty());
    }

    @Test void drawFailureKeepsItsCompletedResetAndSuppressesDespawn() {
        var h=prepared(6); h.edit(o -> o.moveWord(X,0x380).moveByte(SAVED_FLAGS_ID,7)); seed(h,7); h.failDraw=true;
        assertThrows(IllegalStateException.class,h::run); assertEquals(2,h.slot().routine());
        assertEquals(0x0D,h.slot().id()); assertEquals(0xC5,h.runtime.savedFlag(7,1)); assertEquals(4,h.slot().spriteFlags());
    }

    @Test void resultEffectsAreImmutableCompletedMetadataNeverReplayRequests() {
        var h=prepared(2); h.edit(o -> o.moveByte(SUBTYPE,0)); var p=new P(); p.yv=-1; h.player=p.player();
        var r=h.run(); assertThrows(UnsupportedOperationException.class,() -> r.effects().clear());
        assertEquals(List.of("MAIN","SPLASH:32","FM_A4","SECONDARY","DRAW_OBJECT","CHECK_OBJ_DESPAWN"),trace(r));
        h.run(); assertEquals(1,h.sounds.size()); assertEquals(1,h.calls.size());
    }

    @Test void assetsRequireCompleteTwoFrameOneScriptShapeWithoutAuthenticatingFixtures() {
        var a=assets(tiles()); assertArrayEquals(MAPS,a.visual().mappings().copy()); assertArrayEquals(ANIM,a.visual().animationTable().copy());
        assertEquals(List.of(16,4),a.visual().mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(10,a.visual().animations().getFirst().frameBytes().size());
        assertThrows(IllegalArgumentException.class,() -> assets(new byte[73]));
        assertThrows(IllegalArgumentException.class,() -> new CdTunnelDoor.Assets(a.visual(),MAP+1,a.objectTileIds()));
        assertThrows(IllegalArgumentException.class,() -> new CdTunnelDoor.Assets(a.visual(),0xFFFFF0,a.objectTileIds()));
        var staticVisual=CdObjectVisualAssets.decodeStatic(MAPS,2,12);
        assertThrows(IllegalArgumentException.class,() -> new CdTunnelDoor.Assets(staticVisual,MAP,a.objectTileIds()));
    }

    @Test void wrongIdReservedDispatchInvalidWidthsAndMissingCallbacksReject() {
        var h=prepared(2); h.edit(o -> o.moveByte(ID,0x35)); var before=h.slot();
        assertThrows(IllegalArgumentException.class,h::run); assertEquals(before,h.slot());
        assertThrows(IndexOutOfBoundsException.class,() -> h.body.run(1,new CdTunnelDoor.Globals(1,0,128),h.inputs()));
        assertThrows(IllegalArgumentException.class,() -> new CdTunnelDoor.Globals(256,0,0));
        assertThrows(IllegalArgumentException.class,() -> new CdTunnelDoor.Globals(0,-1,0));
        assertThrows(IllegalArgumentException.class,() -> new CdTunnelDoor.Globals(0,0,65536));
        assertThrows(NullPointerException.class,() -> new CdTunnelDoor(h.runtime,h.bank,assets(tiles()),h.solid,null,h::draw,h::fm));
        assertThrows(NullPointerException.class,() -> new CdTunnelDoor(h.runtime,h.bank,assets(tiles()),h.solid,h,null,h::fm));
        assertThrows(NullPointerException.class,() -> new CdTunnelDoor(h.runtime,h.bank,assets(tiles()),h.solid,h,h::draw,null));
    }

    private static Harness prepared(int routine) {
        var h=new Harness(assets(tiles())); h.edit(o -> normal(o,routine)); return h;
    }
    private static Harness standing() {
        var h=prepared(2); var p=new P(); p.routine=2; p.y=0x00661234; p.flags=8; p.stand=41;
        h.player=p.player(); h.pool().write(h.pool().state(41).moveByte(ID,0x12).moveByte(FLAGS,0xA8)); return h;
    }
    private static CdObjectState normal(CdObjectState o,int routine) {
        return o.moveByte(ID,0x0D).moveByte(ROUTINE,routine).moveByte(SPRITE_FLAGS,0x84)
                .moveWord(X,0x100).moveWord(Y,0x80).moveWord(X_FRACTION,0x4321).moveWord(Y_FRACTION,0x8765)
                .moveByte(WIDTH,44).moveByte(Y_RADIUS,8).moveByte(PRIORITY,1).moveLong(MAPPINGS,MAP)
                .moveWord(TILE,0x8328).moveByte(MAP_FRAME,7).moveByte(SUBTYPE,1);
    }
    private static CdObjectState dirty(CdObjectState o) {
        for(var f:CdObjectState.ByteField.values()) o=o.moveByte(f,0xB9);
        for(var f:CdObjectState.WordField.values()) o=o.moveWord(f,0xA65C);
        for(var f:CdObjectState.LongField.values()) o=o.moveLong(f,0x93E7516B);
        return normal(o,0).moveByte(SPRITE_FLAGS,0x31).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0)
                .moveByte(MAP_FRAME,9).moveWord(TUNNEL_DOOR_TIMER,0x12AB);
    }
    private static void seed(Harness h,int id) {
        for(int p=0;p<3;p++) for(var bit:List.of(CdObjectPlacementRuntime.SavedBit.LOADED,
                CdObjectPlacementRuntime.SavedBit.MEMBER_1,CdObjectPlacementRuntime.SavedBit.MEMBER_3,CdObjectPlacementRuntime.SavedBit.MEMBER_7))
            h.runtime.bsetSavedFlag(id,p,bit);
    }
    private static List<String> trace(CdTunnelDoor.Result r) {
        return r.effects().stream().map(e -> switch(e) {
            case CdTunnelDoor.RoutineEntered x -> x.routine().name();
            case CdTunnelDoor.Solid x -> x.participant().name();
            case CdTunnelDoor.SplashCreated x -> "SPLASH:"+x.state().identity().wholeIndex();
            case CdTunnelDoor.PlayedFm x -> x.sound().name();
            case CdTunnelDoor.CallReturned x -> x.call().name();
            case CdTunnelDoor.LoadedCleared x -> "UNLOAD:"+x.savedId()+":"+x.period();
            case CdTunnelDoor.Deleted x -> "DELETE";
        }).toList();
    }
    private static CdTunnelDoor.Assets assets(byte[] tiles) {
        return new CdTunnelDoor.Assets(CdObjectVisualAssets.decode(MAPS,2,ANIM,1,12),MAP,CdAssetBytes.copyOf(tiles));
    }
    private static byte[] tiles() {
        int[] words={36,38,40,44,46,48,58,52,56,54,50,60,62,64,66,68,70,72,
                0x23A0,0x23B0,0x2409,0x2370,0x2000,0x2428,0x4334,0x0320,0,0x4000,0x4000,
                0x0409,0x0374,0x8328,0,0,0x0490,0x03E4,0};
        var b=new byte[74]; for(int i=0;i<words.length;i++) word(b,i*2,words[i]); return b;
    }
    private static CdObjectPlacements layout() {
        var b=ByteBuffer.allocate(34);
        b.putShort((short)12).putShort((short)28).putShort((short)0xFFFF).putInt(0).putShort((short)0);
        b.putShort((short)0x083C).putShort((short)0x0228).put((byte)0x8D).put((byte)1).put((byte)0xE0).put((byte)0);
        b.putShort((short)0xFFFF).putInt(0).putShort((short)0); b.putShort((short)0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }

    /** Root stand-in validates one cut and synchronously publishes the actual shared result. */
    private static final class Harness implements CdTunnelDoor.SolidOwner {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(layout());
        final CdReservedObjectBank bank=CdReservedObjectBank.clearedAfterLevelStart();
        final CdSolidObject solid=terrainSolid();
        final CdTunnelDoor body;
        final List<CdTunnelDoor.SolidCall> calls=new ArrayList<>();
        final List<String> order=new ArrayList<>();
        final List<CdTunnelDoor.FmSound> sounds=new ArrayList<>();
        final List<CdPlayerTouchResponse.FmSound> sharedSounds=new ArrayList<>();
        final List<CdObjectState> soundParents=new ArrayList<>(),soundChildren=new ArrayList<>(),draws=new ArrayList<>();
        CdSolidObject.Player player=new P().player();
        CdSolidObject.Globals globals=new CdSolidObject.Globals(0,4,0x12,0x34);
        CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);
        Consumer<CdTunnelDoor.SolidCut> preflightHook=c -> {};
        Consumer<CdObjectPool> afterDraw=p -> {};
        boolean failPreflight,failCommit,failFm,failDraw,staleReturn,corruptSecondaryCommit;
        boolean publishObject=true,publishOthers=true;
        int camera;
        Harness(CdTunnelDoor.Assets assets) {
            runtime.resetSavedObjectFlags(); body=new CdTunnelDoor(runtime,bank,assets,solid,this,this::draw,this::fm);
        }
        CdObjectPool pool(){return runtime.pool();}
        CdObjectState slot(){return pool().state(WHOLE);}
        void edit(UnaryOperator<CdObjectState> edit){pool().write(edit.apply(slot()));}
        CdTunnelDoor.SolidInputs inputs(){return new CdTunnelDoor.SolidInputs(player,gates,globals);}
        CdTunnelDoor.LiveSolid live(){return new CdTunnelDoor.LiveSolid(player,gates,globals);}
        CdTunnelDoor.Result run(){return run(1,0,128);}
        CdTunnelDoor.Result run(int zone,int direction,int camera){
            this.camera=camera; return body.run(WHOLE,new CdTunnelDoor.Globals(zone,direction,camera),inputs());
        }
        @Override public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.SolidCut c) {
            order.add("PREFLIGHT:"+c.participant());
            if(failPreflight||!player.equals(c.expectedPrimary())||!globals.equals(c.expectedGlobals())||!gates.equals(c.expectedGates()))
                throw new IllegalStateException("Root stale/closed preflight");
            for(var s:c.callCut().slots()) if(!pool().state(s.identity().wholeIndex()).equals(s))
                throw new IllegalStateException("Root stale dynamic cut");
            c.secondary().ifPresent(s -> {if(!bank.state(1).equals(s)) throw new IllegalStateException("Root stale reserved cut");});
            preflightHook.accept(c); return live();
        }
        @Override public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.SolidCall c) {
            order.add("COMMIT:"+c.cut().participant()); calls.add(c);
            if(failCommit) throw new IllegalStateException("Root rejected shared return");
            assertEquals(player,c.cut().expectedPrimary()); assertEquals(globals,c.cut().expectedGlobals());
            for(var s:c.cut().callCut().slots()) assertEquals(s,pool().state(s.identity().wholeIndex()));
            if(publishObject) pool().write(c.result().object());
            if(publishOthers) c.result().otherSlots().forEach(pool()::write);
            if(c.cut().participant()==CdTunnelDoor.Participant.PRIMARY) {player=c.result().player(); globals=c.result().globals();}
            else if(corruptSecondaryCommit) bank.write(bank.state(1).moveByteAt(63,1));
            sharedSounds.addAll(c.result().fmRequests());
            return staleReturn?new CdTunnelDoor.LiveSolid(player,gates,new CdSolidObject.Globals(1,0,0,0)):live();
        }
        void fm(CdTunnelDoor.FmSound sound) {
            order.add("FM"); sounds.add(sound); soundParents.add(slot()); soundChildren.add(pool().state(32));
            if(failFm) throw new IllegalStateException("Root FM failed after native creation");
        }
        void draw(CdObjectPool pool,CdObjectState.Identity identity) {
            assertSame(pool(),pool); assertEquals(WHOLE,identity.wholeIndex()); order.add("DRAW"); var o=slot(); draws.add(o);
            var slots=new CdObjectDrawQueue.CdDrawSlots(){
                public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref){
                    var s=pool.state(ref.wholeIndex()); return new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),
                            s.centreX(),s.centreY(),s.word(X_FRACTION),s.longBits(MAPPINGS),s.unsignedByte(MAP_FRAME),s.word(TILE));
                }
                public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value){
                    var s=pool.state(ref.wholeIndex()); pool.write(s.moveByte(SPRITE_FLAGS,(s.spriteFlags()&127)|(value?128:0)));
                }
            };
            try(var queue=new CdObjectDrawQueue(slots,(address,staticPiece,frame) -> {
                throw new AssertionError("DrawObject does not map before DrawObjects");
            },new byte[640])) {
                queue.beginPass(); queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(WHOLE),
                        o.spriteFlags(),o.centreX(),o.centreY(),o.unsignedByte(Y_RADIUS),o.unsignedByte(WIDTH),o.unsignedByte(PRIORITY)),
                        new CdObjectDrawQueue.CameraWords(camera,0));
            }
            afterDraw.accept(pool); if(failDraw) throw new IllegalStateException("Root post-draw failure");
        }
    }
    private static final class P {
        int x=0x01008000,y=0x00801234,xv=0x123,yv=0,gv=0,routine=6,angle=0x10,flags=2;
        int spr=4,xr=9,yr=19,anim=0,prev=0,jump=1,charge=0,lock=0,stick=0,ctrl=0;
        int pri=0x56,sec=0x78,tile=0x780,priority=2,hurt=0,rot=0,push=0,stand=0;
        CdSolidObject.Player player(){
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x,y,xv,yv,gv,
                    routine,angle,flags,spr,xr,yr,anim,prev,jump,charge,lock,stick,ctrl,pri,sec),tile,priority,hurt,rot),1,push,stand);
        }
    }
    private static CdSolidObject terrainSolid(){
        byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256];
        byte[] heights=new byte[4096],widths=new byte[4096];
        fg[0]=41; fg[1]=7; bg[0]=5; bg[1]=7; Arrays.fill(fg,2,fg.length,(byte)2);
        ids[1]=1; ids[2]=2; Arrays.fill(heights,16,32,(byte)16); Arrays.fill(widths,32,48,(byte)16); angles[2]=(byte)0xC0;
        var terrain=CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);
        byte[] animations=new byte[578]; for(int i=0;i<60;i++) word(animations,i*2,120); animations[122]=(byte)0xFF;
        byte[] mappings=new byte[398],dplcs=new byte[398];
        for(int i=0;i<198;i++){word(mappings,i*2,396);word(dplcs,i*2,396);}
        var visual=CdSonicVisualAssets.decode(mappings,dplcs,new byte[69472]);
        byte[] atan=new byte[257]; for(int i=0;i<=256;i++) atan[i]=(byte)(i/8);
        var a=CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)0xFF},
                new byte[4],new byte[640],atan,visual);
        return new CdSolidObject(new CdPlayerMovement(terrain,a));
    }
    private static byte[] bytes(int... values){var b=new byte[values.length];for(int i=0;i<values.length;i++)b[i]=(byte)values[i];return b;}
    private static void word(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
}
