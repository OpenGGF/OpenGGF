package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-only constructed GoalPost tests, UNRUN including compilation/discovery.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level End/Main.asm:568-702,
 * alias89-98 and Object Spawner.asm:385-446. Full64/all96 expected images use literal source
 * offsets and native input edges. Public-format values authenticate no media/private invocation;
 * simulated external stores are harness assertions, not Frame/presentation/PLC authority.
 * No naturally reached goal, completed act, route, pixel, parity or Java execution claim.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdGoalPost {
    private static final int WHOLE=40,X0=9984,Y0=740,MAP=0x21D9B4;
    private static final byte[] BASE=hex("035a04f704f704f7038104f704f704f7"
            +"03000300030003000300030003000300"
            +"04f204f204f204f204f204f204f204f2"
            +"02ba02cc02b302b102ba02cc02b302b1"
            +"0254022c029402380278028a02bc0298"
            +"03ae03ae03ae03ae03ae03ae03ae03ae"
            +"02200221024c0236023e024a025d0246");

    @Test void finiteAssetsAcceptUnreadRawTileExtentAndNativeWidthGlobals() {
        assertEquals(0,assets(new byte[0]).baseTiles().length()); assertEquals(112,BASE.length);
        assertDoesNotThrow(() -> new CdGoalPost.Globals(65535,255,255,255,65535));
        assertThrows(IllegalArgumentException.class,() -> new CdGoalPost.Globals(-1,0,0,0,0));
        assertThrows(IllegalArgumentException.class,() -> new CdGoalPost.Globals(0,256,0,0,0));
        assertThrows(IllegalArgumentException.class,() -> new CdGoalPost.Globals(0,0,-1,0,0));
        assertThrows(IllegalArgumentException.class,() -> new CdGoalPost.Globals(0,0,0,256,0));
        assertThrows(IllegalArgumentException.class,() -> new CdGoalPost.Globals(0,0,0,0,65536));
        assertThrows(NullPointerException.class,() -> CdGoalPost.Assets.fromPastAssets(null));
    }

    @Test void selectedFrameFiveHasItsOwnTwentyFourTileBoundWithinTheCommonSignpostMapping() {
        byte[] map=new byte[19]; for(int i=0;i<5;i++) w(map,2*i,12); w(map,10,13); map[13]=1; map[17]=24;
        var outside=CdObjectVisualAssets.decodeStatic(map,6,76);
        assertThrows(IllegalArgumentException.class,() -> new CdGoalPost.Assets(outside,MAP,bytes(BASE)));
        map[17]=23; var inside=CdObjectVisualAssets.decodeStatic(map,6,76);
        assertDoesNotThrow(() -> new CdGoalPost.Assets(inside,MAP,bytes(BASE)));
    }

    @Test void invalidRoutineStopsBeforeGlobalOrPlayerReadAndRetainsFullPlacement() {
        for(int routine=0;routine<256;routine++) {
            if(routine==0 || routine==2 || routine==4) continue;
            var h=new Harness(); final int raw=routine; h.edit(s -> s.moveByte(ROUTINE,raw)); var before=images(h);
            var e=assertThrows(CdGoalPost.UnsupportedGoalPost.class,() -> h.body.run(WHOLE,null));
            assertEquals(CdGoalPost.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary()); assertEquals(raw,e.value()); assertImages(before,h); assertTrue(h.order.isEmpty());
        }
    }

    @Test void pastInitPreservesEveryDirtyNonwriteAndSetsNativeHighPriorityTile835a() {
        var h=new Harness(); h.edit(s -> dirty(s).moveByte(ID,0x16).moveByte(ROUTINE,0).moveByte(SAVED_FLAGS_ID,0)
                .moveByte(SPRITE_FLAGS,0xC1).moveWord(X,X0).moveWord(Y,Y0));
        h.pool().write(dirty(h.pool().state(127)).moveByte(ID,0x55)); var expected=images(h); var b=expected.get(WHOLE-32);
        b[0x24]=2; b[1]=0x45; b[0x18]=4; l(b,4,MAP); b[0x17]=b[0x19]=16; b[0x16]=32; b[0x1A]=5; w(b,2,0x835A);
        h.run(); assertImages(expected,h); assertEquals(List.of("read:Y_WORD","draw"),h.order);
        assertEquals(0x835A,h.reads.getFirst().expectedSource().word(TILE));
    }

    @Test void malformedTileReadStopsAfterExactInitHeaderBeforeAnyPlayerReadOrPriorityOr() {
        var h=new Harness(assets(new byte[]{3})); h.edit(s -> s.moveByte(ROUTINE,0).moveWord(TILE,0x4567));
        var expected=images(h); var b=expected.get(WHOLE-32); b[0x24]=2; b[1]|=4; b[0x18]=4;
        l(b,4,MAP); b[0x17]=b[0x19]=16; b[0x16]=32; b[0x1A]=5;
        var e=assertThrows(CdGoalPost.UnsupportedGoalPost.class,h::run); assertEquals(CdGoalPost.UnsupportedBoundary.BASE_TILE_READ,e.boundary());
        assertEquals(0,e.value()); assertImages(expected,h); assertTrue(h.order.isEmpty());
    }

    @Test void allActBytesUseOnlyNativeLslByteParityAndOnlyExactActTwoSkipsDraw() {
        for(int act=0;act<256;act++) {
            var h=new Harness(); h.zoneAct=0x400|act; final int value=act; h.edit(s -> s.moveByte(ROUTINE,0).moveByte(SPRITE_FLAGS,0xC1));
            var expected=images(h); var b=expected.get(WHOLE-32); b[0x24]=2; b[1]=(byte)(value==2?0xC5:0x45); b[0x18]=4;
            l(b,4,MAP); b[0x17]=b[0x19]=16; b[0x16]=32; b[0x1A]=5; w(b,2,(value&1)==0?0x8254:0x8278);
            h.run(); assertImages(expected,h); assertEquals(act==2?List.of("read:Y_WORD"):List.of("read:Y_WORD","draw"),h.order);
            assertEquals((act&1)==0?0x8254:0x8278,h.reads.getFirst().expectedSource().word(TILE));
        }
    }

    @Test void literalCrossGameTileRowsAndQuartzPriorityExceptionUseRawZoneByte() {
        int[][] vectors={{0x000,0,0,0x835A},{0x001,0,0,0x8381},{0x000,1,0,0x84F7},{0x000,2,1,0x84F7},
                {0x100,0,0,0x8300},{0x200,0,0,0x84F2},{0x300,0,0,0x2BA},{0x300,1,0,0x2CC},{0x300,2,0,0x2B3},{0x300,2,1,0x2B1},
                {0x400,0,0,0x8254},{0x401,1,0,0x828A},{0x500,2,0,0x83AE},{0x600,0,0,0x8220},{0x601,2,1,0x8246}};
        for(var vector:vectors) {
            var h=new Harness(); h.zoneAct=vector[0]; h.time=vector[1]; h.good=vector[2]; h.edit(s -> s.moveByte(ROUTINE,0));
            h.run(); assertEquals(vector[3],h.reads.getFirst().expectedSource().word(TILE),Arrays.toString(vector));
        }
    }

    @Test void everyRawTimeByteDoublesAsByteAndEitherReadsFiniteOperandOrStopsAfterHeader() {
        for(int raw=0;raw<256;raw++) {
            var h=new Harness(); h.time=raw; h.edit(s -> s.moveByte(ROUTINE,0)); int index=(raw*2)&255;
            if(index<=110) { h.run(); int expected=((BASE[index]&255)<<8)|(BASE[index+1]&255)|0x8000;
                assertEquals(expected,h.reads.getFirst().expectedSource().word(TILE),"time "+raw); }
            else { var before=images(h); var b=before.get(WHOLE-32); b[0x24]=2; b[1]|=4; b[0x18]=4; l(b,4,MAP);
                b[0x17]=b[0x19]=16; b[0x16]=32; b[0x1A]=5; var e=assertThrows(CdGoalPost.UnsupportedGoalPost.class,h::run);
                assertEquals(index,e.value()); assertEquals(CdGoalPost.UnsupportedBoundary.BASE_TILE_READ,e.boundary()); assertImages(before,h); assertTrue(h.order.isEmpty()); }
        }
    }

    @Test void everyGoodFutureByteIsAddedOnlyForExactFutureAndWrapsBeforeByteDoubling() {
        for(int good=0;good<256;good++) {
            var h=new Harness(); h.time=2; h.good=good; h.edit(s -> s.moveByte(ROUTINE,0)); int index=((good+2)*2)&255;
            if(index<=110) { h.run(); assertEquals((((BASE[index]&255)<<8)|(BASE[index+1]&255)|0x8000),h.reads.getFirst().expectedSource().word(TILE),"good "+good); }
            else { var e=assertThrows(CdGoalPost.UnsupportedGoalPost.class,h::run); assertEquals(index,e.value()); assertTrue(h.reads.isEmpty()); }
            var present=new Harness(); present.time=1; present.good=good; present.edit(s -> s.moveByte(ROUTINE,0)); present.run();
            assertEquals(0x84F7,present.reads.getFirst().expectedSource().word(TILE));
        }
    }

    @Test void rawZoneHighByteIsRetainedAcrossLowByteCarryAndStopsAtFiniteOriginalTable() {
        int[][] vectors={{0x700,0,0x70},{0xF00,0x80,0xF0},{0x1000,0x80,0x100},{0xFF01,0xFF,0xFF6},{0x0100,0x7F,0x0E}};
        for(var v:vectors) { var h=new Harness(); h.zoneAct=v[0]; h.time=v[1]; h.edit(s -> s.moveByte(ROUTINE,0));
            if(v[2]<=110) { h.run(); int tile=(((BASE[v[2]]&255)<<8)|(BASE[v[2]+1]&255)|0x8000); assertEquals(tile,h.reads.getFirst().expectedSource().word(TILE)); }
            else { var ex=assertThrows(CdGoalPost.UnsupportedGoalPost.class,h::run); assertEquals(v[2],ex.value()); assertTrue(h.reads.isEmpty()); }
        }
    }

    @Test void verticalSignedAndUnsignedEdgesReadXOnlyForTheNative256WordWindow() {
        int[][] vectors={{Y0-129,0},{Y0-128,1},{Y0+127,1},{Y0+128,0},{Y0+0x7F80,0},{Y0+0xFF80,1},{Y0+0x1007F,1}};
        for(var v:vectors) { var h=new Harness(); h.y=v[0]&0xFFFF; h.x=X0-1; h.run();
            assertEquals(v[1]==0?List.of("read:Y_WORD","draw"):List.of("read:Y_WORD","read:X_WORD","draw"),h.order);
            assertEquals(2,h.slot().routine()); assertTrue(h.goals.isEmpty()); }
        var wrapped=new Harness(); wrapped.edit(s -> s.moveWord(Y,0xFF80)); wrapped.y=0; wrapped.run(); assertEquals(List.of("read:Y_WORD","draw"),wrapped.order);
    }

    @Test void horizontalComparisonIsUnsignedAndEqualityReachesGoal() {
        for(int x:new int[]{0,0x7FFF,0x8000,0xFFFF}) {
            var h=new Harness(); h.edit(s -> s.moveWord(X,0x8000)); h.camera=0x8000; h.y=Y0; h.x=x; h.run();
            boolean passed=x>=0x8000; assertEquals(passed?4:2,h.slot().routine()); assertEquals(passed?1:0,h.goals.size());
        }
    }

    @Test void reachedGoalPublishesRoutineThenAllFiveExternalStoresThenHex12CueBeforeDraw() {
        var h=new Harness(); h.y=Y0; h.x=X0; h.left=0x1234; h.destLeft=0x5678; h.timer=0x9ABC; h.direction=255; h.contact=0x5A;
        var expected=images(h); expected.get(WHOLE-32)[0x24]=4;
        h.beforeGoal=c -> { assertImages(expected,h); assertEquals(X0,c.cameraXWord()); assertEquals(0x1234,h.left); assertEquals(0x9ABC,h.timer); };
        h.beforePlc=c -> { assertImages(expected,h); assertEquals(0x12,c.nativeId()); assertEquals(X0,h.left); assertEquals(X0,h.destLeft);
            assertEquals(0,h.timer); assertEquals(0,h.direction); assertEquals(0,h.contact); };
        h.run(); expected.get(WHOLE-32)[1]&=0x7F; assertImages(expected,h);
        assertEquals(List.of("read:Y_WORD","read:X_WORD","goal","plc:GOAL_REACHED:18","draw"),h.order);
    }

    @Test void doneReadsNoPlayerPlcHeadOrTileDataButStillDrawsAndDespawns() {
        var h=new Harness(assets(new byte[0])); h.edit(s -> s.moveByte(ROUTINE,4)); var expected=images(h); expected.get(WHOLE-32)[1]&=0x7F;
        h.beforeRead=c -> { throw new AssertionError("unreached player"); }; h.beforeHead=c -> { throw new AssertionError("unreached head"); };
        h.run(); assertImages(expected,h); assertEquals(List.of("draw"),h.order);
    }

    @Test void actThreeSkipsOnlyDrawAndStillTakesOrdinaryWarpDespawn() {
        var h=new Harness(); h.zoneAct=2; h.time=0x80; h.edit(s -> s.moveByte(ROUTINE,4).moveByte(SAVED_FLAGS_ID,236));
        h.placement.bsetSavedFlag(236,0,CdObjectPlacementRuntime.SavedBit.LOADED); var expected=images(h); expected.set(WHOLE-32,new byte[64]);
        var r=h.run(); assertImages(expected,h); assertTrue(r.deleted()); assertEquals(0,h.placement.savedFlag(236,0)); assertTrue(h.order.isEmpty());
    }

    @Test void tidalInitSetsSubtypeBeforeHex13AndOnlyLaterReadsActualPlcHead() {
        var h=new Harness(); h.zoneAct=0x201; h.time=1; h.edit(s -> s.moveByte(ROUTINE,0)); var expected=images(h); expected.get(WHOLE-32)[0x28]=1;
        h.beforePlc=c -> { assertEquals(0x13,c.nativeId()); assertImages(expected,h); }; h.run(); expected.get(WHOLE-32)[1]&=0x7F; assertImages(expected,h);
        assertEquals(List.of("plc:TIDAL_INIT:19","draw"),h.order); assertTrue(h.heads.isEmpty()); assertTrue(h.reads.isEmpty());
        h.order.clear(); h.beforePlc=c -> { throw new AssertionError("cue repeated"); }; h.head=0x80000000; h.run(); assertImages(expected,h);
        assertEquals(List.of("head","draw"),h.order); assertEquals(0,h.slot().routine());
        h.order.clear(); h.head=0; h.run(); assertEquals(2,h.slot().routine()); assertEquals(0x84F2,h.slot().word(TILE));
        assertEquals(List.of("head","read:Y_WORD","draw"),h.order); assertEquals(1,h.slot().subtype());
    }

    @Test void anyNonzeroTidalSubtypeWaitsButOtherRawPeriodsAndFamiliesDoNotTouchHead() {
        for(int subtype:new int[]{1,2,128,255}) { var h=new Harness(); h.zoneAct=0x201; h.time=1; h.head=1;
            final int value=subtype; h.edit(s -> s.moveByte(ROUTINE,0).moveByte(SUBTYPE,value)); h.run(); assertEquals(0,h.slot().routine()); assertEquals(List.of("head","draw"),h.order); }
        int[][] variants={{0x201,0},{0x201,2},{0x201,0x81},{0x200,1},{0x202,1},{0x101,1}};
        for(var v:variants) { var h=new Harness(); h.zoneAct=v[0]; h.time=v[1]; h.edit(s -> s.moveByte(ROUTINE,0));
            h.beforeHead=c -> { throw new AssertionError("unreached head"); }; h.run(); assertTrue(h.heads.isEmpty()); assertTrue(h.plcs.isEmpty()); }
    }

    @Test void absentTidalHeadOwnerStopsOnlyAtReachedWaitAfterPriorLatchAndCue() {
        var h=new Harness(assets(BASE),false); h.zoneAct=0x201; h.time=1; h.edit(s -> s.moveByte(ROUTINE,0)); h.run();
        var before=images(h); h.order.clear(); var e=assertThrows(CdGoalPost.UnsupportedGoalPost.class,h::run);
        assertEquals(CdGoalPost.UnsupportedBoundary.PLC_HEAD_OWNER,e.boundary()); assertImages(before,h); assertTrue(h.order.isEmpty());
    }

    @Test void goalStoreDirectionClearIsUsedBySameCallWarpDespawnInsteadOfOldDirection() {
        var h=new Harness(); h.time=0x81; h.direction=1; h.y=Y0; h.x=X0; h.edit(s -> s.moveByte(SAVED_FLAGS_ID,236));
        for(int p=0;p<3;p++) h.placement.bsetSavedFlag(236,p,CdObjectPlacementRuntime.SavedBit.LOADED);
        var r=h.run(); assertTrue(r.deleted()); assertEquals(0x80,h.placement.savedFlag(236,0)); assertEquals(0,h.placement.savedFlag(236,1)); assertEquals(0x80,h.placement.savedFlag(236,2));
        assertEquals(0,h.direction); assertEquals(1,h.goals.size());
    }

    @Test void ordinaryDespawnUsesSpriteSignUntilActThreeSkippedDrawAndAlwaysChecksWarp() {
        var skipped=new Harness(); skipped.zoneAct=2; skipped.camera=0; skipped.edit(s -> s.moveByte(ROUTINE,4).moveByte(SPRITE_FLAGS,0x84));
        var expected=images(skipped); skipped.run(); assertImages(expected,skipped); assertTrue(skipped.order.isEmpty());
        var drawn=new Harness(); drawn.camera=0; drawn.edit(s -> s.moveByte(ROUTINE,4)); assertTrue(drawn.run().deleted());
        var warp=new Harness(); warp.zoneAct=2; warp.time=0x80; warp.edit(s -> s.moveByte(ROUTINE,4)); assertTrue(warp.run().deleted());
    }

    @Test void exactChunkDistanceEqualityKeepsTheObjectAndOneChunkBeyondDeletes() {
        for(int x:new int[]{0xA00,0xA80}) { var h=new Harness(); h.camera=0x800; h.edit(s -> s.moveByte(ROUTINE,4).moveWord(X,x));
            assertEquals(x==0xA80,h.run().deleted()); }
    }

    @Test void rawLinearSavedAliasAndBoundsAreCheckedOnlyAfterActualDraw() {
        var h=new Harness(); h.time=3; h.camera=0; h.edit(s -> s.moveByte(ROUTINE,4).moveByte(SAVED_FLAGS_ID,1));
        h.placement.bsetSavedFlag(2,0,CdObjectPlacementRuntime.SavedBit.LOADED); h.run(); assertEquals(0,h.placement.savedFlag(2,0));
        var bad=new Harness(); bad.time=127; bad.camera=0; bad.edit(s -> s.moveByte(ROUTINE,4).moveByte(SAVED_FLAGS_ID,255));
        var expected=images(bad); expected.get(WHOLE-32)[1]&=0x7F; var e=assertThrows(CdGoalPost.UnsupportedGoalPost.class,bad::run);
        assertEquals(CdGoalPost.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,e.boundary()); assertEquals(894,e.value()); assertImages(expected,bad); assertEquals(List.of("draw"),bad.order);
    }

    @Test void rawWarpDirectionsKeepNativeSignedByteClampWhenNoGoalStoreOccurred() {
        for(int direction:new int[]{1,255,128,127}) { var h=new Harness(); h.time=0x81; h.direction=direction;
            h.edit(s -> s.moveByte(ROUTINE,4).moveByte(SAVED_FLAGS_ID,236)); for(int p=0;p<3;p++) h.placement.bsetSavedFlag(236,p,CdObjectPlacementRuntime.SavedBit.LOADED);
            h.run(); int d=(short)(1-(byte)direction), selected=d<0?0:Math.min(d,2);
            for(int p=0;p<3;p++) assertEquals(p==selected?0:0x80,h.placement.savedFlag(236,p)); }
    }

    @Test void readOnlyPortFailuresKeepOriginalExceptionAndCorruptedPlacementWithoutRepair() {
        for(String port:List.of("player","goal","plc","head")) { var h=new Harness(); var original=new IllegalArgumentException(port);
            Runnable corrupt=() -> { h.pool().write(h.pool().state(127).moveByte(ID,0x55)); h.placement.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.LOADED); throw original; };
            switch(port) { case "player" -> h.beforeRead=c -> corrupt.run(); case "goal" -> { h.y=Y0; h.beforeGoal=c -> corrupt.run(); }
                case "plc" -> { h.y=Y0; h.beforePlc=c -> corrupt.run(); } case "head" -> { h.zoneAct=0x201; h.time=1; h.edit(s -> s.moveByte(ROUTINE,0).moveByte(SUBTYPE,1)); h.beforeHead=c -> corrupt.run(); } }
            assertSame(original,assertThrows(IllegalArgumentException.class,h::run)); assertEquals(1,original.getSuppressed().length);
            assertEquals(0x55,h.pool().state(127).id()); assertEquals(0x80,h.placement.savedFlag(1,0)); assertTrue(h.draws.isEmpty()); }
    }

    @Test void callbackReturningWrongFullPoolCannotPublishACompletedObject() {
        var h=new Harness(); h.beforeRead=c -> h.pool().write(h.pool().state(127).moveByte(ID,1));
        assertThrows(IllegalStateException.class,h::run); assertTrue(h.draws.isEmpty()); assertEquals(2,h.slot().routine());
        var goal=new Harness(); goal.y=Y0; goal.beforeGoal=c -> goal.pool().write(goal.pool().state(127).moveByte(ID,1));
        assertThrows(IllegalStateException.class,goal::run); assertEquals(4,goal.slot().routine()); assertTrue(goal.plcs.isEmpty());
    }

    @Test void goalAndCueFailureRetainExactlyTheirReachedObjectAndExternalPrefix() {
        var goal=new Harness(); goal.y=Y0; var original=new AssertionError("store"); goal.beforeGoal=c -> { throw original; };
        assertSame(original,assertThrows(AssertionError.class,goal::run)); assertEquals(0,original.getSuppressed().length); assertEquals(4,goal.slot().routine()); assertTrue(goal.plcs.isEmpty());
        var cue=new Harness(); cue.y=Y0; cue.timer=123; var failure=new IllegalStateException("queue"); cue.beforePlc=c -> { throw failure; };
        assertSame(failure,assertThrows(IllegalStateException.class,cue::run)); assertEquals(0,cue.timer); assertEquals(4,cue.slot().routine()); assertEquals(1,cue.goals.size()); assertTrue(cue.draws.isEmpty());
    }

    @Test void outOfWidthPlayerReadStopsAtTheReadWithoutFollowingXOrGoalPublication() {
        for(int raw:new int[]{-1,65536}) { var h=new Harness(); h.y=raw; var before=images(h); assertThrows(IllegalArgumentException.class,h::run);
            assertImages(before,h); assertEquals(List.of("read:Y_WORD"),h.order); assertTrue(h.goals.isEmpty()); assertTrue(h.draws.isEmpty()); }
    }

    @Test void drawPermitsOnlyBitSevenAndPreservesOriginalFailureIdentity() {
        var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,4)); h.afterDraw=c -> h.edit(s -> s.moveByte(ROUTINE,2));
        assertThrows(IllegalStateException.class,h::run); assertEquals(2,h.slot().routine());
        var failed=new Harness(); failed.edit(s -> s.moveByte(ROUTINE,4)); var original=new AssertionError("draw"); failed.afterDraw=c -> { throw original; };
        assertSame(original,assertThrows(AssertionError.class,failed::run)); assertEquals(0,original.getSuppressed().length); assertEquals(4,failed.slot().spriteFlags());
    }

    @Test void synchronousReentryStopsAtItsNativePortAndEffectsNeverReplayStoresOrCues() {
        var h=new Harness(); h.beforeRead=c -> h.run(); assertThrows(IllegalStateException.class,h::run); assertTrue(h.draws.isEmpty());
        var completed=new Harness(); completed.y=Y0; var r=completed.run(); var after=images(completed);
        for(int i=0;i<5;i++) assertEquals(r.effects(),r.effects()); assertImages(after,completed); assertEquals(1,completed.goals.size()); assertEquals(1,completed.plcs.size());
        assertThrows(UnsupportedOperationException.class,() -> r.effects().clear());
    }

    private static CdGoalPost.Assets assets(byte[] base) {
        byte[] map=new byte[13]; for(int i=0;i<6;i++) w(map,2*i,12);
        return new CdGoalPost.Assets(CdObjectVisualAssets.decodeStatic(map,6,76),MAP,bytes(base));
    }
    private static CdAssetBytes bytes(byte[] b) { return CdAssetBytes.copyOf(b); }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static void w(byte[] b,int at,int value) { b[at]=(byte)(value>>>8); b[at+1]=(byte)value; }
    private static void l(byte[] b,int at,int value) { w(b,at,value>>>16); w(b,at+2,value); }
    private static List<byte[]> images(Harness h) {
        var images = new ArrayList<byte[]>(); for (int whole=32;whole<128;whole++) images.add(h.pool().state(whole).copyBytes()); return images;
    }
    private static void assertImages(List<byte[]> expected,Harness h) {
        assertEquals(96,expected.size()); for (int whole=32;whole<128;whole++) assertArrayEquals(expected.get(whole-32),h.pool().state(whole).copyBytes(),"whole "+whole);
    }
    private static CdObjectState dirty(CdObjectState s) {
        for (var f:CdObjectState.ByteField.values()) s=s.moveByte(f,0x51+7*f.byteOffset());
        for (var f:CdObjectState.WordField.values()) s=s.moveWord(f,0xA157+0x103*f.byteOffset());
        for (var f:CdObjectState.LongField.values()) s=s.moveLong(f,0xA1536729+0x01030507*f.byteOffset()); return s;
    }
    private static CdObjectPlacements layout() {
        var b=ByteBuffer.allocate(34); b.putShort((short)12).putShort((short)28).putShort((short)0xFFFF).putInt(0).putShort((short)0);
        b.putShort((short)X0).putShort((short)Y0).put((byte)0x96).put((byte)0xC0).put((byte)0x40).put((byte)0);
        b.putShort((short)0xFFFF).putInt(0).putShort((short)0); b.putShort((short)0xFFFF).putInt(0); return CdObjectPlacements.decodeLayout(b.array());
    }
    private static final class Harness {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(layout()); final List<String> order=new ArrayList<>();
        final List<CdGoalPost.ReadCall> reads=new ArrayList<>(); final List<CdGoalPost.PlcCall> plcs=new ArrayList<>();
        final List<CdGoalPost.PlcHeadCall> heads=new ArrayList<>(); final List<CdGoalPost.GoalReachedCall> goals=new ArrayList<>();
        final List<CdGoalPost.DrawCall> draws=new ArrayList<>(); final CdGoalPost body;
        int zoneAct,time,good,direction,camera=X0,y=Y0+128,x=X0,head,left,destLeft,timer,contact;
        Consumer<CdGoalPost.ReadCall> beforeRead=c -> {}; Consumer<CdGoalPost.PlcCall> beforePlc=c -> {};
        Consumer<CdGoalPost.PlcHeadCall> beforeHead=c -> {}; Consumer<CdGoalPost.GoalReachedCall> beforeGoal=c -> {};
        Consumer<CdGoalPost.DrawCall> afterDraw=c -> {};
        Harness() { this(assets(BASE)); } Harness(CdGoalPost.Assets a) { this(a,true); }
        Harness(CdGoalPost.Assets a,boolean hasHead) {
            edit(s -> s.moveByte(ID,0x16).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveWord(X,X0).moveWord(Y,Y0)
                    .moveByte(X_RADIUS,16).moveByte(WIDTH,16).moveByte(Y_RADIUS,32).moveLong(MAPPINGS,MAP));
            body=new CdGoalPost(placement,a,c -> { assertCut(c.actualCut(),c.expectedSource()); beforeRead.accept(c); reads.add(c); order.add("read:"+c.field()); return c.field()==CdGoalPost.PlayerField.Y_WORD?y:x; },
                    c -> { assertCut(c.actualCut(),c.expectedSource()); beforeGoal.accept(c); goals.add(c); order.add("goal");
                        left=c.cameraXWord(); destLeft=c.cameraXWord(); timer=0; direction=0; contact=0; },
                    c -> { assertCut(c.actualCut(),c.expectedSource()); beforePlc.accept(c); plcs.add(c); order.add("plc:"+c.site()+":"+c.nativeId()); },
                    hasHead?c -> { assertCut(c.actualCut(),c.expectedSource()); beforeHead.accept(c); heads.add(c); order.add("head"); return head; }:null,
                    c -> { assertCut(c.actualCut(),c.expectedSource()); draws.add(c); order.add("draw");
                        pool().write(slot().moveByte(SPRITE_FLAGS,slot().spriteFlags()&0x7F)); afterDraw.accept(c); });
        }
        CdObjectPool pool() { return placement.pool(); } CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> op) { pool().write(op.apply(slot())); }
        CdGoalPost.Result run() { return body.run(WHOLE,new CdGoalPost.Globals(zoneAct,time,good,direction,camera)); }
        void assertCut(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source) {
            placement.validateRestore(cut); assertEquals(source,slot()); assertEquals(96,cut.pool().slots().size()); assertEquals(placement.status(),cut.status());
            assertArrayEquals(placement.capture().savedFlags().copy(),cut.savedFlags().copy()); for(var s:cut.pool().slots()) assertEquals(s,pool().state(s.identity().wholeIndex()));
        }
    }
}
