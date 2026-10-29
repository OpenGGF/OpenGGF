package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-only author packet; every Java assertion is UNRUN by its author. Actual USA B ingress is mandatory,
 * with constructed player/probe/pool scenes labelled below. No assumptions, Present facade or media extraction.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Floating Block/Main.asm:7-176;
 * Object Solid.asm:174-479; Object Animate.asm:14-95; Object Tile ID Set.asm:7-17.
 * Expected images and masks are literal native stores, never computed by a second Solid/animation execution.
 * The publication stand-in is synchronous and observes every complete 96-image cut. These cases do not qualify
 * a World return, current art residency, DrawObjects drain, regional media, pixels or audio.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdPastFloatBlock {
    private static final int WHOLE = 40;
    private static final int MAP = 0x20A252;
    private static final OptionalInt NONE = OptionalInt.empty();
    private static final byte[] ANI = HexFormat.of().parseHex(
            "0006000a00140205ff00020105020503050405fc020100020003000400fc");
    private static final byte[] MAPS = HexFormat.of().parseHex(
            "000c000e001e002e003a0046000003f4090000f404000000fc0400000004"
            + "03f4090800f404000800f404000800fc02f4091800f404001800fc00"
            + "02f4091000f404001000fc0001f40a0006f4");
    private static final Set<CdSolidObject.PlayerField> POSITION = EnumSet.of(
            CdSolidObject.PlayerField.CENTRE_X, CdSolidObject.PlayerField.CENTRE_X_FRACTION,
            CdSolidObject.PlayerField.CENTRE_Y, CdSolidObject.PlayerField.CENTRE_Y_FRACTION);
    private static final Set<CdSolidObject.PlayerField> CARRY = EnumSet.of(
            CdSolidObject.PlayerField.CENTRE_X, CdSolidObject.PlayerField.CENTRE_X_FRACTION,
            CdSolidObject.PlayerField.CENTRE_Y, CdSolidObject.PlayerField.CENTRE_Y_FRACTION,
            CdSolidObject.PlayerField.PLAYER_JUMP, CdSolidObject.PlayerField.FLAGS);

    @Test void actualBFactoryPreservesTheThreeFiniteSourcesAndEmptyMapping() throws Exception {
        var b = retail(); var a = CdFloatBlock.Assets.fromPastAssets(b);
        assertEquals(MAP, a.mappingsAddress());
        assertEquals(72, a.objectTileIds().length());
        assertEquals(76, a.visual().mappings().length());
        assertEquals(30, a.visual().animationTable().length());
        assertArrayEquals(b.view(OBJECT_TILE_IDS).bytes().copy(), a.objectTileIds().copy());
        assertArrayEquals(MAPS, a.visual().mappings().copy());
        assertArrayEquals(ANI, a.visual().animationTable().copy());
        assertEquals("7f6759c3ab336c2eee70a2fea5128e9920c86d418590ae557e0310948c563da1",
                sha(a.objectTileIds().copy()));
        assertEquals("98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e", sha(MAPS));
        assertEquals("3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9", sha(ANI));
        assertEquals(List.of(12,14,30,46,58,70),
                a.visual().mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(0,3,3,2,2,1),
                a.visual().mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertTrue(a.visual().mappingFrames().getFirst().pieces().isEmpty());
        var last = a.visual().mappingFrames().get(5).pieces().getFirst();
        assertEquals(new CdObjectVisualAssets.Piece(-12,10,6,-12), last);
        assertEquals(9, last.tileCount());
        assertEquals(0x2E, a.objectTileIds().unsignedWord(10));
        assertEquals(0x4334, a.objectTileIds().unsignedWord(0x2E));
        assertThrows(IndexOutOfBoundsException.class, () -> a.objectTileIds().unsignedWord(72));
        assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeTileWord(a.objectTileIds().copy(), 5, 0));
        assertEquals(90, ANI_FLOAT_BLOCK.ordinal()); assertEquals(91, MAP_SPR_FLOAT_BLOCK.ordinal());
        assertThrows(NullPointerException.class, () -> CdFloatBlock.Assets.fromPastAssets(null));
    }

    @Test void recordExtentsAndAllOldPublicShapesStayOrdinary() throws Exception {
        var a = CdFloatBlock.Assets.fromPastAssets(retail());
        for (int n : new int[]{0,36,38,70,71,73,75,76}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdFloatBlock.Assets(CdAssetBytes.copyOf(new byte[n]), a.visual(), MAP));
        }
        assertEquals(72, new CdFloatBlock.Assets(a.objectTileIds(), a.visual(), MAP).objectTileIds().length());
        assertEquals(74, new CdFloatBlock.Assets(CdAssetBytes.copyOf(new byte[74]), a.visual(), MAP).objectTileIds().length());
        assertEquals(1, CdFloatBlock.class.getConstructors().length);
        assertEquals(5, CdFloatBlock.class.getConstructors()[0].getParameterCount());
        assertEquals(3, CdFloatBlock.Assets.class.getRecordComponents().length);
        assertEquals(4, CdFloatBlock.TopSolidCall.class.getRecordComponents().length);
        assertEquals(4, CdFloatBlock.PrefixTopSolidCall.class.getRecordComponents().length);
        assertEquals(CdSolidObject.Outcome.class, CdFloatBlock.PrefixTopSolidCall.class.getRecordComponents()[3].getType());
        assertEquals(2, CdFloatBlock.Result.class.getRecordComponents().length);
    }

    @Test void literalReached33InitialisesThenMakesTwoFreshOffscreenCallsAndOneDraw() throws Exception {
        var h = new H(retail(), 33, new Terrain());
        h.edit(h.slot().moveByte(ID,0x12).moveWord(X,0x1C10).moveWord(Y,0xA0));
        assertEquals("12000000000000001c10000000a0000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000", hex(h.slot()));
        var before = h.pool.capture(); var p = h.player;
        // Camera is CONSTRUCTED to retain X1C10; actual frontier camera has not been supplied.
        var out = h.run(0,0x1C00,0);
        var expected = before.slots().get(1).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4)
                .moveLong(MAPPINGS,MAP).moveWord(TILE,0x4334).moveByte(PRIORITY,1).moveByte(WIDTH,12)
                .moveByte(Y_RADIUS,12).moveByte(MAP_FRAME,5);
        assertArrayEquals(expected.copyBytes(), out.state().copyBytes());
        assertPool(before, h.pool, expected);
        assertEquals(List.of(expected), h.draws); assertEquals(2,h.calls.size());
        assertNotSame(h.calls.get(0).callCut(),h.calls.get(1).callCut());
        assertEquals(expected, h.calls.get(0).callCut().slots().get(1));
        assertEquals(expected, h.calls.get(1).callCut().slots().get(1));
        for (var c : h.calls) {
            var r = completed(c);
            assertEquals(CdSolidObject.GetOffReason.OBJECT_OFFSCREEN,r.getOffReason().orElseThrow());
            assertEquals(Set.of(SOLID_TYPE),r.objectWrites()); assertTrue(r.playerWrites().isEmpty());
            assertTrue(r.globalWrites().isEmpty()); assertTrue(r.otherSlots().isEmpty()); assertTrue(r.fmRequests().isEmpty());
            assertEquals(p,r.player());
        }
        assertEquals(List.of("INIT","MAIN","TOP0","TOP1","DRAW_OBJECT"), trace(out));
        assertEquals(p,h.player);
    }

    @Test void everyNonzeroPastSubtypeStopsAfterOnlyNativePreTileInitStores() throws Exception {
        var b = retail();
        for (int subtype = 1; subtype < 256; subtype++) {
            var h = new H(b); h.seed(0,0x12,0); h.edit(h.slot().moveByte(SUBTYPE2,subtype));
            var before = h.pool.capture(); var original = h.slot();
            var failure = assertThrows(CdFloatBlock.UnsupportedFloatBlock.class, () -> h.run(0,128,0));
            assertEquals(CdFloatBlock.UnsupportedBoundary.TILE_ID_READ,failure.boundary());
            assertEquals(WHOLE,failure.wholeIndex());
            var prefix = original.moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x16).moveLong(MAPPINGS,MAP);
            assertArrayEquals(prefix.copyBytes(),h.slot().copyBytes()); assertPool(before,h.pool,prefix);
            assertTrue(h.calls.isEmpty()); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void legacyConstructionKeepsItsPrewriteSubtypeGuardIncludingWith72ByteRecord() throws Exception {
        var b = retail(); var h = new H(b); h.seed(0,0x12,0);
        h.edit(h.slot().moveByte(SUBTYPE2,1)); var before = h.pool.capture();
        var legacy = new CdFloatBlock(h.runtime,CdFloatBlock.Assets.fromPastAssets(b),h.solid,
                call -> fail("legacy guard must not reach Solid"), (pool,id) -> fail("no draw"));
        var error = assertThrows(CdFloatBlock.UnsupportedFloatBlock.class,
                () -> legacy.run(WHOLE,new CdFloatBlock.Globals(0,128,0),h.inputs()));
        assertEquals(CdFloatBlock.UnsupportedBoundary.TILE_ID_SUBTYPE2,error.boundary());
        assertEquals(before.slots(),h.pool.capture().slots());
    }

    @Test void allSevenRoutinesUseTheSameBodyAndNativeNoDrawPaths() throws Exception {
        var b = retail();
        for (int routine : new int[]{0,2,4,6,8,10,12}) {
            var h = new H(b); h.seed(routine,4,5); var before = h.pool.capture();
            if (routine == 10) h.edit(h.slot().moveByte(ANIMATION,2).moveByte(PREVIOUS_ANIMATION,2)
                    .moveByte(ANIMATION_TIME,1));
            var out = h.run(0,128,0);
            assertEquals(routine==12 ? 0:2,h.calls.size(),"routine "+routine);
            assertEquals((routine==6 || routine==8 || routine==12) ? 0:1,h.draws.size(),"routine "+routine);
            int finalRoutine = routine==0 || routine==6 || routine==12 ? 2 : routine==8 ? 10:routine;
            assertEquals(finalRoutine,out.state().routine());
            assertEquals(routine==4 ? 0x82:0x80,out.state().centreY());
            assertEquals(0x4321,out.state().word(X_FRACTION)); assertEquals(0x8765,out.state().word(Y_FRACTION));
            assertPool(before,h.pool,out.state());
        }
    }

    @Test void rawPeriodUsesTheWholeByteIncludingWarpPastAndNeverNormalisesIt() throws Exception {
        var b = retail();
        for (int period : new int[]{0,1,2,0x80,0x81,0x82}) {
            var h = new H(b); h.seed(2,4,5);
            var out = h.run(period,128,0);
            assertEquals(period==0 || period==2 ? 5:0,out.state().unsignedByte(MAP_FRAME));
            assertEquals(2,out.state().routine()); assertEquals(2,h.calls.size()); assertEquals(1,h.draws.size());
        }
    }

    @Test void futureLandingMakesFourSynchronousCallsBeforeAddingOnlyTheYWord() throws Exception {
        var h = new H(retail()); h.seed(2,0x84,5);
        var before=h.pool.capture(); var out=h.run(2,128,0);
        assertEquals(List.of("MAIN","TOP0","TOP1","FALL","TOP2","TOP3","DRAW_OBJECT"),trace(out));
        assertEquals(4,h.calls.size());
        for (int i=1;i<4;i++) {
            assertNotSame(h.calls.get(i-1).callCut(),h.calls.get(i).callCut());
            assertEquals(completed(h.calls.get(i-1)).player(),h.calls.get(i).player());
            assertEquals(completed(h.calls.get(i-1)).globals(),h.calls.get(i).globals());
        }
        assertEquals(4,h.calls.get(2).callCut().slots().get(8).routine());
        assertEquals(0x80,completed(h.calls.get(3)).object().centreY());
        var expected=before.slots().get(8).moveByte(FLAGS,8).moveByte(ROUTINE,4).moveWord(Y,0x82);
        assertArrayEquals(expected.copyBytes(),out.state().copyBytes()); assertPool(before,h.pool,expected);
        var p=new P(); p.y=0x00611234;p.yv=0;p.gv=0x123;p.angle=0;p.flags=8;p.jump=0;p.stand=WHOLE;
        assertEquals(p.player(),h.player);
    }

    @Test void standZeroIsThePlayerAliasAndDynamicEndpointsReleaseEvenDirtyIdZero() throws Exception {
        var b=retail();
        for (int old : new int[]{0,32,127}) {
            var h=new H(b); h.seed(2,0x84,5); var p=new P();p.flags=8;p.stand=old;p.yv=0;h.player=p.player();
            CdObjectState previous=null;
            if (old!=0) {
                previous=h.pool.state(old).moveByte(FLAGS,0xA9).moveWord(X,0x2345);
                h.pool.write(previous); // ID remains zero: physical RAM still has release semantics.
            }
            var before=h.pool.capture(); var out=h.run(0,128,0); var first=completed(h.calls.getFirst());
            p.y=0x00611234;p.jump=0;p.stand=WHOLE;p.angle=0;p.yv=0;p.gv=0x123;p.flags=old==0 ? 0:8;
            assertEquals(p.player(),first.player());
            if(old==0) {
                assertTrue(first.otherSlots().isEmpty());
                // The second check sets standing again now that standObject is WHOLE.
                p.flags=8;
            } else {
                var released=previous.moveByte(FLAGS,0xA1);
                assertEquals(List.of(released),first.otherSlots());
                assertEquals(released,h.calls.get(1).callCut().slots().get(old-32));
                assertTrue(completed(h.calls.get(1)).otherSlots().isEmpty());
                assertPool(before,h.pool,out.state(),released);
            }
            assertEquals(p.player(),h.player);
            if(old==0) assertPool(before,h.pool,out.state());
        }
    }

    @Test void reachedStandAddressFaultPublishesExactPrefixThenRethrowsIdenticalInstanceImmediately() throws Exception {
        var b=retail();
        for(int old:new int[]{1,31,128,255}) {
            var h=new H(b);h.seed(8,0x84,5);h.edit(h.slot().moveByte(ANIMATION,1));
            var p=new P();p.flags=8;p.stand=old;p.yv=0;h.player=p.player();
            var before=h.pool.capture();var error=assertThrows(CdSolidObject.UnsupportedSolidObject.class,
                    ()->h.run(0,0x8000,0)); // A tail would delete: it must never run.
            assertEquals(1,h.calls.size()); assertTrue(h.draws.isEmpty());
            var stop=assertInstanceOf(CdSolidObject.NativeStopped.class,h.calls.getFirst().outcome());
            assertSame(error,stop.stop().originalException());assertEquals(CdSolidObject.StopSite.STAND_RELEASE,stop.stop().site());
            assertEquals(old,error.slotByte());assertEquals(CdSolidObject.Routine.STAND_ON_OBJECT,error.routine());
            var expected=before.slots().get(8).moveByte(ANIMATION,0).moveByte(FLAGS,8).moveByte(SOLID_TYPE,0);
            assertArrayEquals(expected.copyBytes(),h.slot().copyBytes());assertPool(before,h.pool,expected);
            p.y=0x00611234;p.jump=0;assertEquals(p.player(),h.player);
            assertEquals(CARRY,stop.prefix().playerWrites());
            assertEquals(Set.of(SOLID_TYPE,FLAGS),stop.prefix().objectWrites());
            assertTrue(stop.prefix().globalWrites().isEmpty());assertTrue(stop.prefix().otherSlots().isEmpty());
            assertEquals(List.of(new CdSolidObject.FloorDistance(27,NONE,NONE),
                    new CdSolidObject.RoutineCall(CdSolidObject.Routine.STAND_ON_OBJECT)),stop.prefix().calls());
        }
    }

    @Test void invalidStandByteIsInertUntilNativeReleaseActuallyDereferencesIt() throws Exception {
        var h=new H(retail());h.seed(2,4,5);var p=new P();p.flags=8;p.stand=255;h.player=p.player();
        var out=h.run(0,128,0);
        assertEquals(2,h.calls.size());assertEquals(p.player(),h.player);
        assertEquals(5,out.state().unsignedByte(MAP_FRAME));
        for(var c:h.calls) assertEquals(CdSolidObject.GetOffReason.OBJECT_OFFSCREEN,completed(c).getOffReason().orElseThrow());
    }

    @Test void genuineFirstAndSecondFootReadStopsKeepTheExactPartialPlayerAndNoAggregateProbeReturn() throws Exception {
        var b=retail();
        for(boolean secondary:new boolean[]{false,true}) {
            var terrain=new Terrain();terrain.fg[2+(secondary?0:1)]=(byte)0xF9;
            var h=new H(b,WHOLE,terrain);h.seed(2,0x84,5);
            var p=new P();p.spr=0x44;
            if(secondary){p.x=0x00F88000;h.edit(h.slot().moveWord(X,0xF8));}
            h.player=p.player();var before=h.pool.capture();
            var error=assertThrows(RuntimeException.class,()->h.run(0,0x8000,0));
            var stop=assertInstanceOf(CdSolidObject.NativeStopped.class,h.calls.getFirst().outcome());
            assertSame(error,stop.stop().originalException());assertEquals(1,h.calls.size());assertTrue(h.draws.isEmpty());
            assertEquals(secondary?CdSolidObject.StopSite.FLOOR_SECONDARY:CdSolidObject.StopSite.FLOOR_PRIMARY,stop.stop().site());
            var word=assertInstanceOf(CdSolidObject.TerrainWord.class,stop.stop().detail());
            assertEquals(secondary?0x21F2FC:0x21F2E0,word.address().nativeAddress());
            p.y=0x00611234;assertEquals(p.player(),h.player);
            assertEquals(POSITION,stop.prefix().playerWrites());assertEquals(Set.of(SOLID_TYPE),stop.prefix().objectWrites());
            assertTrue(stop.prefix().globalWrites().isEmpty());assertTrue(stop.prefix().calls().isEmpty());
            assertPool(before,h.pool,before.slots().get(8).moveByte(SOLID_TYPE,1));
        }
    }

    @Test void prefixCommitFailuresRetainTheirIdentityAndNeverReachAnotherCallDrawOrTail() throws Exception {
        var b=retail();
        for(boolean stopped:new boolean[]{false,true}) {
            var h=new H(b);h.seed(8,0x84,5);h.edit(h.slot().moveByte(ANIMATION,1));
            if(stopped){var p=new P();p.flags=8;p.stand=1;p.yv=0;h.player=p.player();}
            var failure=new IllegalStateException("actual synchronous prefix port");
            h.beforeCommit=call->{throw failure;};
            assertSame(failure,assertThrows(IllegalStateException.class,()->h.run(0,0x8000,0)));
            assertEquals(1,h.calls.size());assertTrue(h.draws.isEmpty());assertEquals(0,h.slot().unsignedByte(ANIMATION));
            assertEquals(8,h.slot().routine(),"the chunk-window tail must not delete");
        }
    }

    @Test void secondCommitFailureKeepsOnlyTheFirstCompletedPublicationAndItsPlayer() throws Exception {
        var h=new H(retail());h.seed(2,0x84,5);var before=h.pool.capture();
        var failure=new IllegalArgumentException("pair fall-through failure");
        h.beforeCommit=call->{if(h.calls.size()==2)throw failure;};
        assertSame(failure,assertThrows(IllegalArgumentException.class,()->h.run(0,0x8000,0)));
        assertEquals(2,h.calls.size());assertTrue(h.draws.isEmpty());
        var first=completed(h.calls.getFirst());assertEquals(first.player(),h.player);
        assertPool(before,h.pool,first.object());
    }

    @Test void completedAndStoppedPortsMustPublishTheEntireExpected96ImageSet() throws Exception {
        var b=retail();
        for(boolean stopped:new boolean[]{false,true}) {
            var h=new H(b);h.seed(2,0x84,5);
            if(stopped){var p=new P();p.flags=8;p.stand=31;p.yv=0;h.player=p.player();}
            h.publish=false;
            assertThrows(IllegalStateException.class,()->h.run(0,128,0));assertEquals(1,h.calls.size());
            assertTrue(h.draws.isEmpty());
            var wrong=new H(b);wrong.seed(2,stopped?0x84:4,5);
            if(stopped){var p=new P();p.flags=8;p.stand=31;p.yv=0;wrong.player=p.player();}
            wrong.afterCommit=call->wrong.pool.write(wrong.pool.state(127).moveByte(ANGLE,0xA6));
            assertThrows(IllegalStateException.class,()->wrong.run(0,128,0));
            assertEquals(1,wrong.calls.size());assertTrue(wrong.draws.isEmpty());
            assertEquals(stopped,wrong.calls.getFirst().outcome() instanceof CdSolidObject.NativeStopped,
                    "The unrelated-slot attack must exercise the selected genuine outcome");
        }
    }

    @Test void animationMissingIdOnlyFailsWhenTheTimerReachesItsReadAndKeepsResetAndDecrementPrefix() throws Exception {
        var h=new H(retail());h.seed(10,4,5);
        h.edit(h.slot().moveByte(ANIMATION,3).moveByte(PREVIOUS_ANIMATION,3)
                .moveByte(ANIMATION_FRAME,0xB7).moveByte(ANIMATION_TIME,2));
        var a=h.run(0,128,0);assertEquals(1,a.state().unsignedByte(ANIMATION_TIME));
        var b=h.run(0,128,0);assertEquals(0,b.state().unsignedByte(ANIMATION_TIME));
        var before=h.slot();int calls=h.calls.size(),draws=h.draws.size();
        assertThrows(UnsupportedOperationException.class,()->h.run(0,0x8000,0));
        assertArrayEquals(before.moveByte(ANIMATION_TIME,255).copyBytes(),h.slot().copyBytes());
        assertEquals(calls+2,h.calls.size());assertEquals(draws,h.draws.size());
        h.edit(h.slot().moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_TIME,0x55));
        var changed=h.slot();
        assertThrows(UnsupportedOperationException.class,()->h.run(0,0x8000,0));
        var prefix=changed.moveByte(PREVIOUS_ANIMATION,3).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255);
        assertArrayEquals(prefix.copyBytes(),h.slot().copyBytes());
    }

    @Test void selectedAnimationByteFailureRetainsItsAlreadyLoadedDelayAndRawIndex() throws Exception {
        var h=new H(retail());h.seed(10,4,5);
        h.edit(h.slot().moveByte(ANIMATION,2).moveByte(PREVIOUS_ANIMATION,2)
                .moveByte(ANIMATION_FRAME,0xFF).moveByte(ANIMATION_TIME,0));
        var before=h.pool.capture();var expected=h.slot().moveByte(ANIMATION_TIME,2);
        assertThrows(UnsupportedOperationException.class,()->h.run(0,0x8000,0));
        assertPool(before,h.pool,expected);assertTrue(h.draws.isEmpty());assertEquals(2,h.calls.size());
    }

    @Test void actualFfAndFcScriptsRunOncePerCallWithTheNativeDelayAndNoPriming() throws Exception {
        var b=retail();
        var steady=new H(b);steady.seed(10,4,0);steady.edit(steady.slot().moveByte(ANIMATION,0));
        for(int i=0;i<7;i++){
            var out=steady.run(0,128,0);
            assertEquals(5,out.state().unsignedByte(MAP_FRAME));assertEquals(10,out.state().routine());
            assertEquals(1,out.effects().stream().filter(e->e.equals(
                    new CdFloatBlock.CallReturned(CdFloatBlock.NativeCall.ANIMATE_OBJECT))).count());
        }
        var vanish=new H(b);vanish.seed(10,4,5);vanish.edit(vanish.slot().moveByte(ANIMATION,2));
        int[] frames={1,1,1,0,0,0,2,2,2,0,0,0,3,3,3,0,0,0,4,4,4,0,0,0,0};
        for(int i=0;i<frames.length;i++){
            var out=vanish.run(0,128,0);assertEquals(frames[i],out.state().unsignedByte(MAP_FRAME),"dispatch "+i);
            assertEquals(i==24?12:10,out.state().routine());
        }
        assertEquals(8,vanish.slot().unsignedByte(ANIMATION_FRAME));assertEquals(2,vanish.slot().unsignedByte(ANIMATION_TIME));
    }

    @Test void drawFailurePreservesItsOwnLiveWritesAndTheExactExceptionAfterBothCalls() throws Exception {
        var h=new H(retail());h.seed(2,0x84,5);
        var failure=new UnsupportedOperationException("actual draw port");
        h.drawAction=()->{h.pool.write(h.slot().moveByte(SPRITE_FLAGS,4));throw failure;};
        assertSame(failure,assertThrows(UnsupportedOperationException.class,()->h.run(0,0x8000,0)));
        assertEquals(2,h.calls.size());assertEquals(1,h.draws.size());assertEquals(4,h.slot().spriteFlags());
        assertEquals(2,h.slot().routine());
    }

    @Test void tailReloadsActualDrawXAndEmptyFrameRemainsAGenuineDraw() throws Exception {
        var h=new H(retail());h.seed(2,4,5);
        h.drawAction=()->h.pool.write(h.slot().moveWord(X,0x300).moveByte(SPRITE_FLAGS,0x84));
        var out=h.run(0x80,128,0);
        assertEquals(0,h.draws.getFirst().unsignedByte(MAP_FRAME));assertTrue(out.deleted());
        assertArrayEquals(new byte[64],out.state().copyBytes());
        assertEquals(List.of("MAIN","TOP0","TOP1","DRAW_OBJECT","DELETE:CHUNK_WINDOW"),trace(out));
    }

    @Test void fallBoundaryAndWrappingCameraRetainFractionsOrClearAll64WithoutPlacementMutation() throws Exception {
        var b=retail();
        for(int[] v:new int[][]{{0xDE,128,0,0},{0xDF,128,0,1},{0xDF,0x400,0,2},{0x80,128,0xFF40,1}}) {
            var h=new H(b);h.seed(4,4,5);h.edit(h.slot().moveWord(Y,v[0]).moveByte(SAVED_FLAGS_ID,7));
            var placement=h.runtime.capture();var before=h.pool.capture();var out=h.run(2,v[1],v[2]);
            assertEquals(placement.status(),h.runtime.status());
            assertArrayEquals(placement.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
            if(v[3]==0){
                assertFalse(out.deleted());assertEquals(0xE0,out.state().centreY());
                assertEquals(0x4321,out.state().word(X_FRACTION));assertEquals(0x8765,out.state().word(Y_FRACTION));
            }else{
                assertArrayEquals(new byte[64],out.state().copyBytes());assertTrue(h.draws.isEmpty());
                assertEquals(v[3],out.effects().stream().filter(CdFloatBlock.Deleted.class::isInstance).count());
            }
            assertPool(before,h.pool,out.state());
        }
    }

    @Test void chunkWindowUsesMaskedUnsignedWordsStrict280AndWrapWithoutDraw() throws Exception {
        var b=retail();
        for(int[] v:new int[][]{{0x280,128,0},{0x2FF,128,0},{0x300,128,1},{0x7F,256,1},{0x100,0x7F,0}}){
            var h=new H(b);h.seed(12,0x84,5);h.edit(h.slot().moveWord(X,v[0]));var before=h.pool.capture();
            var out=h.run(0,v[1],0);assertEquals(v[2]!=0,out.deleted());
            assertTrue(h.calls.isEmpty());assertTrue(h.draws.isEmpty());
            assertPool(before,h.pool,out.state());
            if(v[2]!=0)assertArrayEquals(new byte[64],out.state().copyBytes());
        }
    }

    @Test void badRoutineWrongIdAndFactoryNullsRejectBeforeAnyBodyWrite() throws Exception {
        var b=retail();var h=new H(b);
        for(int routine:new int[]{1,7,13,14,254}){
            h.seed(routine,0x84,5);var before=h.pool.capture();
            var error=assertThrows(CdFloatBlock.UnsupportedFloatBlock.class,()->h.run(0,128,0));
            assertEquals(CdFloatBlock.UnsupportedBoundary.ROUTINE_DISPATCH,error.boundary());
            assertEquals(before.slots(),h.pool.capture().slots());
        }
        h.edit(h.slot().moveByte(ID,0x26));var before=h.pool.capture();
        assertThrows(IllegalArgumentException.class,()->h.run(0,128,0));
        assertEquals(before.slots(),h.pool.capture().slots());
        assertThrows(NullPointerException.class,()->CdFloatBlock.fromPastAssets(null,b,h.solid,h::commit,h::draw));
        assertThrows(NullPointerException.class,()->CdFloatBlock.fromPastAssets(h.runtime,null,h.solid,h::commit,h::draw));
        assertThrows(NullPointerException.class,()->CdFloatBlock.fromPastAssets(h.runtime,b,null,h::commit,h::draw));
        assertThrows(NullPointerException.class,()->CdFloatBlock.fromPastAssets(h.runtime,b,h.solid,null,h::draw));
        assertThrows(NullPointerException.class,()->CdFloatBlock.fromPastAssets(h.runtime,b,h.solid,h::commit,null));
    }

    private static CdSolidObject.Result completed(CdFloatBlock.PrefixTopSolidCall call) {
        return assertInstanceOf(CdSolidObject.Completed.class,call.outcome()).completedResult();
    }
    private static List<String> trace(CdFloatBlock.Result result){
        var out=new ArrayList<String>();
        for(var e:result.effects()){
            if(e instanceof CdFloatBlock.RoutineEntered r)out.add(r.routine().name());
            else if(e instanceof CdFloatBlock.TopSolid s)out.add("TOP"+s.ordinal());
            else if(e instanceof CdFloatBlock.CallReturned r)out.add(r.call().name());
            else if(e instanceof CdFloatBlock.Deleted d)out.add("DELETE:"+d.site().name());
        }
        return out;
    }
    /** Independent expected full96 list; does not call overlay, Solid, the body, or the animator. */
    private static void assertPool(CdObjectPool.Snapshot before,CdObjectPool pool,CdObjectState... changes){
        var expected=new ArrayList<>(before.slots());
        for(var image:changes)expected.set(image.identity().wholeIndex()-32,image);
        assertEquals(expected,pool.capture().slots());
        for(int i=0;i<96;i++)assertArrayEquals(expected.get(i).copyBytes(),pool.state(i+32).copyBytes(),"physical "+(i+32));
    }
    private static String hex(CdObjectState state){return HexFormat.of().formatHex(state.copyBytes());}
    private static String sha(byte[] bytes)throws Exception{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class H {
        final int whole;
        final CdObjectPlacementRuntime runtime;
        final CdObjectPool pool;
        final CdSolidObject solid;
        final CdFloatBlock body;
        final List<CdFloatBlock.PrefixTopSolidCall> calls=new ArrayList<>();
        final List<CdObjectState> draws=new ArrayList<>();
        CdSolidObject.Player player=new P().player();
        CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);
        CdSolidObject.Globals globals=new CdSolidObject.Globals(0,4,0x12,0x34);
        Consumer<CdFloatBlock.PrefixTopSolidCall> beforeCommit=c->{},afterCommit=c->{};
        Runnable drawAction=()->{};
        boolean publish=true;
        H(CdR11bAssets b){this(b,WHOLE,new Terrain());}
        H(CdR11bAssets b,int whole,Terrain terrain){
            this.whole=whole;runtime=new CdObjectPlacementRuntime(b.placements());pool=runtime.pool();
            solid=terrain.owner();body=CdFloatBlock.fromPastAssets(runtime,b,solid,this::commit,this::draw);
        }
        CdObjectState slot(){return pool.state(whole);}
        void edit(CdObjectState image){pool.write(image);}
        void seed(int routine,int sprite,int frame){
            pool.deleteObject(whole);
            edit(slot().moveByte(ID,0x12).moveWord(X,0x100).moveWord(X_FRACTION,0x4321)
                    .moveWord(Y,0x80).moveWord(Y_FRACTION,0x8765).moveByte(ROUTINE,routine)
                    .moveByte(SPRITE_FLAGS,sprite).moveLong(MAPPINGS,MAP).moveWord(TILE,0x4334)
                    .moveByte(PRIORITY,1).moveByte(WIDTH,12).moveByte(Y_RADIUS,12).moveByte(MAP_FRAME,frame)
                    .moveByte(ANGLE,0xA5).moveByte(SUBTYPE,0xC3));
        }
        CdFloatBlock.SolidInputs inputs(){return new CdFloatBlock.SolidInputs(player,gates,globals);}
        CdFloatBlock.Result run(int period,int cameraX,int cameraY){
            return body.run(whole,new CdFloatBlock.Globals(period,cameraX,cameraY),inputs());
        }
        /** Deliberate stand-in for Root's Frame publication; data are already computed, never replayed. */
        void commit(CdFloatBlock.PrefixTopSolidCall call){
            calls.add(call);
            assertEquals(player,call.player());assertEquals(globals,call.globals());
            pool.validateRestore(call.callCut());assertEquals(call.callCut().slots(),pool.capture().slots());
            beforeCommit.accept(call);
            CdObjectState object;List<CdObjectState> other;CdSolidObject.Player next;CdSolidObject.Globals nextGlobals;
            switch(call.outcome()){
                case CdSolidObject.Completed done->{
                    var r=done.completedResult();object=r.object();other=r.otherSlots();next=r.player();nextGlobals=r.globals();
                }
                case CdSolidObject.NativeStopped stopped->{
                    var r=stopped.prefix();object=r.object();other=r.otherSlots();next=r.player();nextGlobals=r.globals();
                }
            }
            var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(other);
            for(var image:images)pool.validateWrite(image);
            if(publish){images.forEach(pool::write);player=next;globals=nextGlobals;}
            afterCommit.accept(call);
        }
        void draw(CdObjectPool actual,CdObjectState.Identity identity){
            assertSame(pool,actual);assertEquals(whole,identity.wholeIndex());
            draws.add(slot());drawAction.run();
        }
    }

    private String retainedRoot;
    private CdR11bAssets retained;
    private CdR11bAssets retail() throws Exception {
        String root=System.getProperty("soniccd.media.root");
        assertNotNull(root,"Original USA media cases require soniccd.media.root");
        assertFalse(root.isBlank(),"Original USA media root must not be blank");
        if(retained!=null&&root.equals(retainedRoot))return retained;
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size(),"Configured USA source must contain exactly one existing CUE");cue=cues.getFirst();
        }
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            retained=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
        retainedRoot=root;return retained;
    }

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
