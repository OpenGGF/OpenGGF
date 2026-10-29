package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests-first additive Past Time Post mode. All Java UNRUN by this source-only author.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Monitor and Time Post/Main.asm:64-236,
 * Object Spawner.asm:385-446. Existing shared animator and direction/command owners remain authoritative.
 * This constructed component harness has no private Past Visit, reserved-bank, Frame or sound authority.
 */
class TestCdPastTimePost {
    static final int WHOLE=40;
    @Test void legacyConstructorRunRecordsAndPastMethodAritiesRemainCompatible() {
        assertEquals(1,CdTimePost.class.getConstructors().length);
        assertEquals(5,CdTimePost.class.getConstructors()[0].getParameterCount());
        assertThrows(NullPointerException.class,()->new CdTimePost(null,null,null,null,null));
        assertEquals(6,CdTimePost.Globals.class.getRecordComponents().length);
        assertEquals(3,CdTimePost.DirectionCall.class.getRecordComponents().length);
        assertEquals(6,CdTimePost.PastGlobals.class.getDeclaredMethods().length);
        var h=new Harness();assertThrows(NullPointerException.class,
                ()->CdTimePost.withPastOwner(h.runtime,TestCdMonitor.assets(),null,h::voice,h::draw));
        assertTrue(h.reads.isEmpty());
    }

    @Test void timeAttackDeletesBeforeDirectionZoneLayerCameraTimeOrRoutineRead() {
        for(int subtype:new int[]{8,9,10,11,255}) {
            var h=new Harness();h.seed(255,subtype);h.attack=1;h.failRead="DIRECTION";
            var r=h.run();assertTrue(r.deleted());assertArrayEquals(new byte[64],h.slot().copyBytes());
            assertEquals(List.of("ATTACK"),h.reads);assertTrue(h.order.isEmpty());
        }
    }

    @Test void dynamicTimeIconStillStopsAtTheExistingReservedOwnerBoundary() {
        var h=new Harness();h.seed(0,10);var before=h.runtime.capture();
        var e=assertThrows(CdTimePost.UnsupportedTimePost.class,h::run);
        assertEquals(CdTimePost.UnsupportedBoundary.TIME_ICON_RESERVED_SLOT,e.boundary());
        assertEquals(before.pool().slots(),h.pool().capture().slots());
        assertArrayEquals(before.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
        assertEquals(List.of("ATTACK"),h.reads);
    }

    @Test void zeroContactDoneAndSpinDoNotReadUnusedPrimaryLayerOrDirection() {
        for(int routine:new int[]{2,6}) {
            var h=new Harness();h.seed(routine,8);h.zone=6;h.failRead="ZONE";
            h.run();assertEquals(List.of("ATTACK","CAMERA","TIME"),h.reads);
            assertTrue(h.voices.isEmpty());assertTrue(h.directions.isEmpty());
        }
        var h=new Harness();h.seed(4,8);h.edit(h.slot().moveByte(TIME_POST_SPIN,1).moveByte(ANIMATION,255));
        h.failRead="ZONE";h.run();assertEquals(6,h.slot().routine());assertEquals(11,h.slot().unsignedByte(MAP_FRAME));
        assertEquals(List.of("ATTACK","CAMERA","TIME"),h.reads);
    }

    @Test void initPublishesEveryPreZoneStoreBeforeAReachedSourceFailure() {
        var h=new Harness();h.seed(0,9);h.failRead="ZONE";var before=h.slot();
        assertSame(h.sourceFailure,assertThrows(IllegalStateException.class,h::run));
        assertEquals(before.addByte(ROUTINE,2).moveByte(Y_RADIUS,32).moveByte(X_RADIUS,14)
                .moveLong(CdObjectState.LongField.MAPPINGS,TestCdMonitor.MAP).moveWord(TILE,0x5A8)
                .moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3),h.slot());
        assertEquals(List.of("ATTACK","ZONE"),h.reads);assertTrue(h.draws.isEmpty());
    }

    @Test void contactClearsBeforeMissingMmzLayerAndNeverReadsItOutsideMmz() {
        var h=new Harness();h.seed(2,8);h.edit(h.slot().moveByte(COLLISION_STATUS,1));h.zone=6;
        var before=h.slot();var e=assertThrows(CdTimePost.UnsupportedTimePost.class,h::run);
        assertEquals(CdTimePost.UnsupportedBoundary.MMZ_LAYER_READ,e.boundary());
        assertEquals(before.moveByte(COLLISION_STATUS,0),h.slot());
        assertEquals(List.of("ATTACK","ZONE","LAYER"),h.reads);assertTrue(h.order.isEmpty());
        var k=new Harness();k.seed(2,8);k.edit(k.slot().moveByte(COLLISION_STATUS,1));k.failRead="LAYER";
        k.run();assertFalse(k.reads.contains("LAYER"));assertEquals(255,k.direction);
    }

    @Test void futureCommitsBothNativeStoresAndOneCommandInSourceOrder() {
        var h=new Harness();h.seed(2,9);h.edit(h.slot().moveByte(COLLISION_STATUS,255));h.direction=0x80;
        var before=h.runtime.capture();var r=h.run();
        assertEquals(List.of("DIR:128>255","DIR:255>1","VOICE:118","DRAW"),h.order);
        assertEquals(1,h.direction);assertEquals(1,h.voices.size());
        assertEquals(1,h.runtime.savedFlag(7,1));assertEquals(60,h.slot().unsignedByte(TIME_POST_SPIN));
        assertEquals(4,h.slot().routine());assertEquals(0,h.slot().unsignedByte(COLLISION_STATUS));
        TestCdPastMonitor.assertOtherSlots(before.pool(),h.pool(),Set.of(WHOLE));
        assertEquals(before.status(),h.runtime.capture().status());
        assertEquals(2,r.effects().stream().filter(CdTimePost.DirectionCommitted.class::isInstance).count());
        assertEquals(1,r.effects().stream().filter(CdTimePost.SubCpuRequest.class::isInstance).count());
    }

    @Test void sameValuePastStoreStillInvokesItsPortAndUsesActualExpectedDirection() {
        var h=new Harness();h.seed(2,8);h.direction=255;h.edit(h.slot().moveByte(COLLISION_STATUS,1));
        h.runtime.bsetSavedFlag(7,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);h.run();
        assertEquals(List.of("DIR:255>255","VOICE:119","DRAW"),h.order);
        assertEquals(List.of("ATTACK","ZONE","TIME","DIRECTION","CAMERA","TIME"),h.reads);
    }

    @Test void directionAndCommandFailuresPreserveOrderedPrefixesAndOriginalThrowables() {
        for(int reject:new int[]{1,2,3}) {
            var h=new Harness();h.seed(2,9);h.edit(h.slot().moveByte(COLLISION_STATUS,1));h.rejectAt=reject;
            assertSame(h.callbackFailure,assertThrows(IllegalArgumentException.class,h::run));
            assertEquals(reject==1?0:reject==2?255:1,h.direction);
            assertEquals(1,h.runtime.savedFlag(7,1));assertEquals(4,h.slot().routine());
            assertEquals(60,h.slot().unsignedByte(TIME_POST_SPIN));assertTrue(h.draws.isEmpty());
            assertEquals(reject==3?1:0,h.voices.size());
        }
    }

    @Test void directionAndVoiceVerifyEveryOneOfThe96DynamicNonwrites() {
        for(int whole=32;whole<128;whole++)for(int port:new int[]{1,3}) {
            var h=new Harness();h.seed(2,9);h.edit(h.slot().moveByte(COLLISION_STATUS,1));
            final int at=whole;h.mutateAt=port;h.mutation=()->h.pool().write(h.pool().state(at).moveByte(ANGLE,0xA5));
            assertThrows(IllegalStateException.class,h::run,"port "+port+" whole "+whole);
            assertTrue(h.draws.isEmpty());assertEquals(port==3?1:0,h.voices.size());
        }
    }

    @Test void callbackVerificationAlsoCoversSavedAndStatusAndNeverReplacesOriginalError() {
        for(int kind=0;kind<2;kind++) {
            var h=new Harness();h.seed(2,8);h.edit(h.slot().moveByte(COLLISION_STATUS,1));h.mutateAt=1;
            final int selected=kind;var sentinel=new AssertionError("actual direction error");
            h.mutation=()->{if(selected==0)h.runtime.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
                else h.runtime.spawnObjects(0x200,1);throw sentinel;};
            assertSame(sentinel,assertThrows(AssertionError.class,h::run));
            assertTrue(sentinel.getSuppressed().length>0);assertTrue(h.voices.isEmpty());assertTrue(h.draws.isEmpty());
        }
    }

    @Test void drawReloadsLiveVisibilityAndAllowsOnlyItsOwnOnscreenBit() {
        var h=new Harness();h.seed(6,8);h.camera=0x6000;h.drawFlags=0x84;
        assertFalse(h.run().deleted());assertFalse(h.reads.contains("CAMERA"));
        for(int whole=32;whole<128;whole++) {
            var k=new Harness();k.seed(6,8);k.mutateAt=4;final int at=whole;
            k.mutation=()->k.pool().write(k.pool().state(at).moveByte(ANGLE,0xA5));
            assertThrows(IllegalStateException.class,k::run,"draw whole "+whole);
        }
    }

    @Test void savedAddressUsesIdZeroRawNonwarpPeriodAndNewlyCommittedWarpDirection() {
        var h=new Harness();h.seed(0,8);h.time=4;h.failRead="DIRECTION";
        h.runtime.bsetSavedFlag(8,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);h.run();
        assertEquals(6,h.slot().routine());assertFalse(h.reads.contains("DIRECTION"));
        var k=new Harness();k.seed(0,8);k.edit(k.slot().moveByte(SAVED_FLAGS_ID,0));
        k.runtime.bsetSavedFlag(0,1,CdObjectPlacementRuntime.SavedBit.LOADED);k.run();assertEquals(0,k.runtime.savedFlag(0,1));
        var m=new Harness();m.seed(2,9);m.edit(m.slot().moveByte(COLLISION_STATUS,1));m.time=0x81;
        m.runtime.bsetSavedFlag(7,0,CdObjectPlacementRuntime.SavedBit.LOADED);
        m.runtime.bsetSavedFlag(7,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        assertTrue(m.run().deleted());assertEquals(1,m.direction);
        assertEquals(0,m.runtime.savedFlag(7,0));assertEquals(129,m.runtime.savedFlag(7,1));
    }

    @Test void rawInvalidSubtypeRetainsOnlyExistingFiniteAnimatorPrefixes() {
        var h=new Harness();h.seed(4,11);
        h.edit(h.slot().moveByte(TIME_POST_SPIN,60).moveByte(ANIMATION,11).moveByte(PREVIOUS_ANIMATION,0));
        var before=h.slot();var e=assertThrows(CdTimePost.UnsupportedTimePost.class,h::run);
        assertEquals(CdTimePost.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY,e.boundary());assertEquals(22,e.value());
        assertEquals(before.moveByte(TIME_POST_SPIN,59).moveByte(PREVIOUS_ANIMATION,11)
                .moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255),h.slot());
        assertEquals(List.of("ATTACK"),h.reads);assertTrue(h.draws.isEmpty());
    }

    @Test void sourceReadsAreReadOnlyEvenWhenThrowingAndWidthsAreCheckedOnlyWhenReached() {
        for(int kind=0;kind<3;kind++) {
            var h=new Harness();h.seed(6,8);final int selected=kind;
            h.readHook=()->{if(selected==0)h.pool().write(h.pool().state(127).moveByte(ANGLE,0xA5));
                else if(selected==1)h.runtime.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
                else h.runtime.spawnObjects(0x200,1);throw h.sourceFailure;};
            assertSame(h.sourceFailure,assertThrows(IllegalStateException.class,h::run));
            assertTrue(h.sourceFailure.getSuppressed().length>0);assertTrue(h.draws.isEmpty());
        }
        var h=new Harness();h.seed(6,8);h.layer=OptionalInt.of(256);h.run();
        var k=new Harness();k.seed(2,8);k.zone=6;k.edit(k.slot().moveByte(COLLISION_STATUS,1));k.layer=OptionalInt.of(256);
        assertThrows(IllegalArgumentException.class,k::run);assertEquals(0,k.slot().unsignedByte(COLLISION_STATUS));
    }

    @Test void reentryGuardAndComponentRestoreCannotReplayACompletedCommand() {
        var h=new Harness();h.seed(2,8);h.edit(h.slot().moveByte(COLLISION_STATUS,1));h.mutateAt=1;h.mutation=()->h.run();
        assertThrows(IllegalStateException.class,h::run);assertTrue(h.voices.isEmpty());
        var k=new Harness();k.seed(2,8);k.edit(k.slot().moveByte(COLLISION_STATUS,1));var cut=k.runtime.capture();
        var r=k.run();var order=List.copyOf(k.order);k.runtime.restore(cut);
        assertEquals(order,k.order);assertEquals(255,k.direction);
        assertThrows(UnsupportedOperationException.class,()->r.effects().clear());
        // The standalone placement restore is not a healthy-cut World restore and grants no retry authority.
    }

    static final class Harness implements CdTimePost.PastGlobals {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(TestCdTimePost.layout());
        final CdTimePost body=CdTimePost.withPastOwner(runtime,TestCdMonitor.assets(),this::direction,this::voice,this::draw);
        final List<String> reads=new ArrayList<>(),order=new ArrayList<>();
        final List<CdTimePost.DirectionCall> directions=new ArrayList<>();
        final List<CdTimePost.SubCpuCommand> voices=new ArrayList<>();
        final List<CdObjectState> draws=new ArrayList<>();
        final IllegalArgumentException callbackFailure=new IllegalArgumentException("actual callback failure");
        final IllegalStateException sourceFailure=new IllegalStateException("actual source failure");
        Runnable mutation=()->{},readHook=()->{};
        String failRead="";int time=1,direction=0,camera=0x200,attack=0,zone=0,drawFlags=4,portCalls,rejectAt,mutateAt;
        OptionalInt layer=OptionalInt.empty();
        Harness(){runtime.resetSavedObjectFlags();}
        CdObjectPool pool(){return runtime.pool();}CdObjectState slot(){return pool().state(WHOLE);}
        void edit(CdObjectState value){pool().write(value);}
        void seed(int routine,int subtype){edit(slot().moveByte(ID,0x19).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype)
                .moveByte(SAVED_FLAGS_ID,7).moveByte(SPRITE_FLAGS,0x84).moveWord(X,0x270).moveWord(Y,0x778)
                .moveByte(Y_RADIUS,32).moveByte(X_RADIUS,14).moveByte(WIDTH,15).moveByte(PRIORITY,3)
                .moveByte(ANIMATION,subtype).moveByte(MAP_FRAME,subtype==8?10:12).moveByte(COLLISION_TYPE,0xDF)
                .moveLong(CdObjectState.LongField.MAPPINGS,TestCdMonitor.MAP));}
        CdTimePost.Result run(){return body.runPast(WHOLE,this);}
        void reached(int port){if(++portCalls==rejectAt)throw callbackFailure;if(mutateAt==port)mutation.run();}
        void direction(CdTimePost.DirectionCall call) {
            assertEquals(slot().identity(),call.identity());assertEquals(direction,call.expectedRawDirection());
            order.add("DIR:"+call.expectedRawDirection()+">"+call.rawDirection());directions.add(call);
            reached(directions.size());direction=call.rawDirection();
        }
        void voice(CdTimePost.SubCpuCommand command){order.add("VOICE:"+command.nativeId());voices.add(command);reached(3);}
        void draw(CdObjectPool actual,CdObjectState.Identity identity) {
            assertSame(pool(),actual);assertEquals(slot().identity(),identity);order.add("DRAW");draws.add(slot());
            edit(slot().moveByte(SPRITE_FLAGS,drawFlags));reached(4);
        }
        int read(String name,int value){reads.add(name);readHook.run();if(name.equals(failRead))throw sourceFailure;return value;}
        public int rawTimeZone(){return read("TIME",time);}public int rawTimeWarpDirection(){return read("DIRECTION",direction);}
        public int cameraXWord(){return read("CAMERA",camera);}public int timeAttackMode(){return read("ATTACK",attack);}
        public int zone(){return read("ZONE",zone);}
        public OptionalInt layer(){read("LAYER",0);return layer;}
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
