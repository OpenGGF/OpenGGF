package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests-first finite Past Monitor adaptation; all Java UNRUN by this source-only author.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Monitor and Time Post/Main.asm:207-409,
 * Collision Check.asm:157-179, Object Solid.asm:188-479, Object Spawner.asm:385-446.
 * Constructed actors/terrain are component fixtures; only factory metadata uses original USA media.
 * No private Visit/Frame authority, physical43 subtype, framebuffer or audio playback is asserted.
 */
class TestCdPastMonitor {
    static final int WHOLE=40;
    @Test void additiveSurfaceKeepsTheOriginalConstructorRecordsAndNullResolution() throws Exception {
        assertEquals(1,CdMonitor.class.getConstructors().length);
        assertEquals(8,CdMonitor.class.getConstructors()[0].getParameterCount());
        assertThrows(NullPointerException.class,()->new CdMonitor(null,null,null,null,null,null,null,null));
        assertEquals(7,CdMonitor.Globals.class.getRecordComponents().length);
        assertEquals(4,CdMonitor.SolidCall.class.getRecordComponents().length);
        assertEquals(4,CdMonitor.Result.class.getRecordComponents().length);
        assertEquals(5,CdMonitor.PrefixSolidCut.class.getRecordComponents().length);
        assertEquals(2,CdMonitor.PrefixSolidCall.class.getRecordComponents().length);
        assertEquals(3,CdMonitor.LiveSolid.class.getRecordComponents().length);
        assertEquals(CdSolidObject.Outcome.class,CdMonitor.PrefixSolidCall.class.getMethod("outcome").getReturnType());
        assertEquals(7,CdMonitor.PastGlobals.class.getDeclaredMethods().length);
        var h=new Harness(false);
        assertThrows(NullPointerException.class,()->CdMonitor.withPastOwner(h.runtime,h.assets,h.solid,h.terrain,null,h::angle,h::draw,h::fm));
        assertTrue(h.reads.isEmpty());
    }

    @Test void pastFactoryUsesOnlyCompleteOriginalBMappingAndAnimationViews() throws Exception {
        var a=retail();var v=CdMonitor.Assets.fromPastAssets(a);
        assertEquals(CdReleaseVariant.USA,a.identity().variant());
        assertEquals(CdNativeFile.R11B,a.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,a.identity().domain());
        assertEquals(CdStagePayload.R11B.stage(),a.identity().stage());
        assertEquals(0x20A8A6,v.mappingsAddress());
        assertEquals(20,v.visual().mappingFrames().size());assertEquals(10,v.visual().animations().size());
        assertArrayEquals(java.util.HexFormat.of().parseHex(TestCdMonitor.MAP_HEX),v.visual().mappings().copy());
        assertArrayEquals(java.util.HexFormat.of().parseHex(TestCdMonitor.ANI_HEX),v.visual().animationTable().copy());
        assertEquals(450,v.visual().mappings().length());assertEquals(136,v.visual().animationTable().length());
        assertThrows(NullPointerException.class,()->CdMonitor.Assets.fromPastAssets(null));
    }

    @Test void completedPlainSolidIsGenuineAndPublishesBeforeAnyLaterReadOrDraw() {
        var h=new Harness(false);h.seed(2);h.timeStop=1;var before=h.runtime.capture();
        var r=h.run();assertEquals(1,h.calls.size());
        var call=h.calls.getFirst();var result=assertInstanceOf(CdSolidObject.Completed.class,call.outcome()).completedResult();
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT,result.entry());
        assertEquals(CdSolidObject.GetOffReason.PLAYER_DEAD,result.getOffReason().orElseThrow());
        assertTrue(result.objectWrites().contains(SOLID_TYPE));
        assertEquals(List.of("PREFLIGHT","SOLID","DRAW"),h.order);
        assertEquals(before.status(),h.runtime.capture().status());
        assertArrayEquals(before.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
        assertOtherSlots(before.pool(),h.pool(),Set.of(WHOLE));
        assertEquals(h.player,r.finalPlayer());assertEquals(h.globals,r.finalGlobals());
    }

    @Test void nativeStoppedSolidCommitsItsActualPrefixThenRethrowsTheIdenticalOriginal() {
        var h=new Harness(false);h.seed(2);h.timeStop=1;
        var p=new TestCdMonitor.P();p.routine=2;p.x=0x02701234;p.y=0x07578765;p.flags=10;p.stand=1;p.yv=0x100;
        h.player=p.player();
        var failure=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::run);
        var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,h.calls.getFirst().outcome());
        assertSame(stopped.stop().originalException(),failure);
        assertEquals(CdSolidObject.StopSite.STAND_RELEASE,stopped.stop().site());
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT,stopped.prefix().entry());
        assertEquals(stopped.prefix().object(),h.slot());assertEquals(stopped.prefix().player(),h.player);
        assertEquals(stopped.prefix().globals(),h.globals);
        assertEquals(List.of("PREFLIGHT","SOLID"),h.order);assertFalse(h.reads.contains("STOP"));
        assertTrue(h.draws.isEmpty());
    }

    @Test void full96PreflightAndCommitNonwritesAreCheckedAtEveryPhysicalImage() {
        for(int whole=32;whole<128;whole++) {
            var h=new Harness(false);h.seed(2);final int at=whole;
            h.preflightHook=cut->h.pool().write(h.pool().state(at).moveByte(ANGLE,0xA5));
            assertThrows(IllegalStateException.class,h::run,"preflight whole "+whole);assertTrue(h.calls.isEmpty());
            var k=new Harness(false);k.seed(2);k.commitHook=call->k.pool().write(k.pool().state(at).moveByte(ANGLE,0xA5));
            assertThrows(IllegalStateException.class,k::run,"commit whole "+whole);assertTrue(k.draws.isEmpty());
        }
    }

    @Test void omittedObjectOtherSlotOrLiveReadbackPublicationStopsBeforeAnimation() {
        var h=new Harness(false);h.seed(2);h.publishObject=false;
        h.edit(h.slot().moveByte(FLAGS,0x28));var p=new TestCdMonitor.P();p.flags=0x28;p.push=WHOLE;p.stand=WHOLE;h.player=p.player();
        assertThrows(IllegalStateException.class,h::run);assertTrue(h.draws.isEmpty());
        var k=new Harness(false);k.seed(2);k.publishOthers=false;
        var q=new TestCdMonitor.P();q.routine=2;q.x=0x02700000;q.y=0x07570000;q.flags=10;q.stand=42;k.player=q.player();
        k.pool().write(k.pool().state(42).moveByte(ID,0x7F).moveByte(FLAGS,8));
        assertThrows(IllegalStateException.class,k::run);assertTrue(k.draws.isEmpty());
        var m=new Harness(false);m.seed(2);m.staleReadback=true;
        assertThrows(IllegalStateException.class,m::run);assertTrue(m.draws.isEmpty());
    }

    @Test void callbackThrowableStaysPrimaryWhenVerificationAlsoFindsAWrongImage() {
        var h=new Harness(false);h.seed(2);var sentinel=new AssertionError("actual owner failure");
        h.commitHook=call->{h.pool().write(h.pool().state(127).moveByte(ANGLE,0xA5));throw sentinel;};
        assertSame(sentinel,assertThrows(AssertionError.class,h::run));
        assertTrue(sentinel.getSuppressed().length>0);assertTrue(h.draws.isEmpty());
    }

    @Test void floorForwardsActualPrezeroAndOddRawAngleWithoutAggregateReplay() {
        var h=new Harness(true);h.seed(2);h.timeStop=1;
        h.edit(h.slot().moveLong(Y_POSITION,0x077A5678).moveWord(Y_VELOCITY,0).moveByte(ROUTINE_2,4));
        var r=h.run();assertEquals(List.of(0,0x23),h.angleWrites);assertEquals(0x23,h.globals.primaryAngle());
        assertEquals(0x07715678,r.state().longBits(Y_POSITION));assertEquals(0,r.state().word(Y_VELOCITY));
        assertEquals(0,r.state().unsignedByte(ROUTINE_2));assertTrue(h.calls.isEmpty());
        assertEquals(List.of("ANGLE","ANGLE","DRAW"),h.order);
    }

    @Test void floorRetainsPrezeroAndActualAngleBeforeALaterUnavailableResidentRead() {
        var h=new Harness(new FloorFixture().laterMissing());h.seed(2);h.timeStop=1;
        h.edit(h.slot().moveWord(Y,0x77A).moveWord(Y_VELOCITY,0).moveByte(ROUTINE_2,4).moveByte(SPRITE_FLAGS,0xC4));
        var before=h.slot();var failure=assertThrows(IllegalStateException.class,h::run);
        assertEquals("Collision ID requires unavailable resident bytes: 1024",failure.getMessage());
        assertEquals(List.of(0,0x22),h.angleWrites);assertEquals(0x22,h.globals.primaryAngle());
        assertEquals(before.moveWord(Y_VELOCITY,0x38),h.slot());assertTrue(h.draws.isEmpty());
    }

    @Test void angleCallbackFailureKeepsIntegratedPrefixAndOriginalThrowable() {
        var h=new Harness(true);h.seed(2);h.timeStop=1;
        h.edit(h.slot().moveLong(Y_POSITION,0x077A5678).moveWord(Y_VELOCITY,0).moveByte(ROUTINE_2,4));
        var sentinel=new IllegalArgumentException("actual angle failure");
        h.angleHook=()->{h.pool().write(h.pool().state(127).moveByte(ANGLE,0xA5));throw sentinel;};
        assertSame(sentinel,assertThrows(IllegalArgumentException.class,h::run));
        assertEquals(List.of(0),h.angleWrites);assertEquals(0x077A5678,h.slot().longBits(Y_POSITION));
        assertEquals(0x38,h.slot().word(Y_VELOCITY));assertTrue(sentinel.getSuppressed().length>0);
        assertTrue(h.draws.isEmpty());
    }

    @Test void lazyReadsKeepAttackLayerStopDirectionAndCameraAtTheirReachedCuts() {
        var h=new Harness(false);h.seed(8);h.drawFlags=0x84;h.failRead="CAMERA";
        h.run();assertEquals(List.of("TIME"),h.reads);
        var k=new Harness(false);k.seed(2);k.edit(k.slot().moveByte(SPRITE_FLAGS,4));k.failRead="ZONE";
        k.run();assertFalse(k.reads.contains("ZONE"));assertFalse(k.reads.contains("STOP"));assertFalse(k.reads.contains("ATTACK"));
        var m=new Harness(false);m.seed(0);m.failRead="ZONE";var before=m.slot();
        assertSame(m.sourceFailure,assertThrows(IllegalStateException.class,m::run));
        assertEquals(before.addByte(ROUTINE,2).moveByte(Y_RADIUS,14).moveByte(X_RADIUS,14)
                .moveLong(MAPPINGS,m.assets.mappingsAddress()).moveWord(TILE,0x5A8).moveByte(PRIORITY,3),m.slot());
        assertTrue(m.draws.isEmpty());
    }

    @Test void sourceCallbacksCannotChangePoolSavedOrStatusAndKeepTheirOriginalFault() {
        for(int kind=0;kind<3;kind++) {
            var h=new Harness(false);h.seed(8);final int selected=kind;
            h.readHook=()->{if(selected==0)h.pool().write(h.pool().state(127).moveByte(ANGLE,0xA5));
                else if(selected==1)h.runtime.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
                else h.runtime.spawnObjects(0x200,1);throw h.sourceFailure;};
            assertSame(h.sourceFailure,assertThrows(IllegalStateException.class,h::run));
            assertTrue(h.sourceFailure.getSuppressed().length>0);
        }
    }

    @Test void breakKeepsActualFmDirtyAllocationsSavedStoreAndDirectDrawOrder() {
        var h=new Harness(false);h.seed(4);
        for(int whole=32;whole<128;whole++)if(whole!=WHOLE)h.pool().write(h.pool().state(whole).moveByte(ID,0x7F));
        var a=TestCdMonitor.dirty(h.pool().state(32)).moveByte(ID,0);
        var b=TestCdMonitor.dirty(h.pool().state(41)).moveByte(ID,0);h.pool().write(a);h.pool().write(b);
        var parent=h.slot();var r=h.run();
        assertEquals(List.of("FM","DRAW"),h.order);
        assertEquals(a.moveByte(ID,0x1A).moveWord(X,parent.centreX()).moveWord(Y,parent.centreY())
                .moveByte(ANIMATION,parent.unsignedByte(ANIMATION)).moveByte(LAYER,parent.unsignedByte(LAYER)),h.pool().state(32));
        assertEquals(b.moveByte(ID,0x18).moveWord(X,parent.centreX()).moveWord(Y,parent.centreY())
                .moveByte(ROUTINE_2,1).moveByte(SUBTYPE,1).moveByte(LAYER,parent.unsignedByte(LAYER)),h.pool().state(41));
        assertEquals(1,h.runtime.savedFlag(7,1));assertEquals(0,TestCdMonitor.count(r,CdMonitor.NativeCall.CHECK_OBJ_DESPAWN));
        assertEquals(6,h.pool().state(41).routine());assertFalse(h.reads.contains("STOP"));
    }

    @Test void fmAndDrawCheckAllNonwritesAndPreserveCallbackFailureIdentity() {
        var h=new Harness(false);h.seed(4);var before=h.slot();var sentinel=new AssertionError("FM failed");
        h.fmHook=()->{h.runtime.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.MEMBER_1);throw sentinel;};
        assertSame(sentinel,assertThrows(AssertionError.class,h::run));assertEquals(before,h.slot());
        assertTrue(sentinel.getSuppressed().length>0);assertTrue(h.draws.isEmpty());
        for(int whole=32;whole<128;whole++) {
            var k=new Harness(false);k.seed(8);final int at=whole;
            k.drawHook=()->k.pool().write(k.pool().state(at).moveByte(ANGLE,0xA5));
            assertThrows(IllegalStateException.class,k::run,"draw whole "+whole);
        }
    }

    @Test void rawSavedPeriodIdZeroAndLastAddressRemainFinite() {
        var h=new Harness(false);h.seed(0);h.time=4;h.failRead="DIRECTION";
        h.runtime.bsetSavedFlag(8,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);h.run();
        assertEquals(8,h.slot().routine());assertFalse(h.reads.contains("DIRECTION"));
        var k=new Harness(false);k.seed(0);k.edit(k.slot().moveByte(SAVED_FLAGS_ID,0));
        k.runtime.bsetSavedFlag(0,1,CdObjectPlacementRuntime.SavedBit.LOADED);k.run();assertEquals(0,k.runtime.savedFlag(0,1));
        var m=new Harness(false);m.seed(0);m.edit(m.slot().moveByte(SAVED_FLAGS_ID,255));
        assertEquals(CdMonitor.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,assertThrows(CdMonitor.UnsupportedMonitor.class,m::run).boundary());
        assertEquals(766,m.runtime.capture().savedFlags().length());assertTrue(m.draws.isEmpty());
    }

    @Test void inheritedMonitorAnimatorFailureStaysAtomicAndDisclosed() {
        var h=new Harness(false);h.seed(6);h.edit(h.slot().moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,1));
        var before=h.slot();assertThrows(RuntimeException.class,h::run);assertEquals(before,h.slot());
        assertTrue(h.draws.isEmpty());assertEquals(List.of("STOP"),h.reads);
    }

    @Test void pastReentryGuardRetiresWithoutCreatingAnyInvocationAuthority() {
        var h=new Harness(false);h.seed(2);h.preflightHook=cut->h.run();
        assertThrows(IllegalStateException.class,h::run);assertTrue(h.calls.isEmpty());
        h.preflightHook=cut->{};h.seed(8);var r=h.run();
        assertThrows(UnsupportedOperationException.class,()->r.effects().clear());
    }

    static void assertOtherSlots(CdObjectPool.Snapshot before,CdObjectPool pool,Set<Integer> except) {
        for(var s:before.slots())if(!except.contains(s.identity().wholeIndex()))
            assertEquals(s,pool.state(s.identity().wholeIndex()),"whole "+s.identity().wholeIndex());
    }

    static final class Harness implements CdMonitor.PrefixSolidOwner,CdMonitor.PastGlobals {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(TestCdMonitor.layout());
        final CdMonitor.Assets assets=TestCdMonitor.assets();
        final CdPlayerTerrain terrain;final CdSolidObject solid;final CdMonitor body;
        final List<CdMonitor.PrefixSolidCall> calls=new ArrayList<>();
        final List<CdObjectState> draws=new ArrayList<>();
        final List<Integer> angleWrites=new ArrayList<>();
        final List<String> reads=new ArrayList<>(),order=new ArrayList<>();
        CdSolidObject.Player player=new TestCdMonitor.P().player();
        CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);
        CdSolidObject.Globals globals=new CdSolidObject.Globals(0,4,0x12,0x34);
        Consumer<CdMonitor.PrefixSolidCut> preflightHook=cut->{};
        Consumer<CdMonitor.PrefixSolidCall> commitHook=call->{};
        Runnable readHook=()->{},angleHook=()->{},drawHook=()->{},fmHook=()->{};
        final IllegalStateException sourceFailure=new IllegalStateException("actual source failure");
        String failRead="";int time=1,direction=0,camera=0x200,attack=0,zone=0,layer=0,timeStop=0,drawFlags=4;
        boolean publishObject=true,publishOthers=true,staleReadback;
        Harness(boolean floor){this(new FloorFixture().floor(floor?2:0,floor?0x23:0));}
        Harness(FloorFixture fixture) {
            var t=fixture.decode();terrain=new CdPlayerTerrain(t);solid=TestCdMonitor.solid(t);
            runtime.resetSavedObjectFlags();body=CdMonitor.withPastOwner(runtime,assets,solid,terrain,this,this::angle,this::draw,this::fm);
        }
        CdObjectPool pool(){return runtime.pool();}CdObjectState slot(){return pool().state(WHOLE);}
        void edit(CdObjectState s){pool().write(s);}
        void seed(int routine){edit(slot().moveByte(ID,0x19).moveByte(ROUTINE,routine).moveByte(SUBTYPE,4)
                .moveByte(SAVED_FLAGS_ID,7).moveByte(SPRITE_FLAGS,0x84).moveWord(X,0x270).moveWord(Y,0x778)
                .moveByte(Y_RADIUS,14).moveByte(X_RADIUS,14).moveByte(WIDTH,15).moveByte(PRIORITY,3)
                .moveByte(ANIMATION,4).moveByte(MAP_FRAME,7).moveByte(COLLISION_TYPE,0x46).moveLong(MAPPINGS,assets.mappingsAddress()));}
        CdMonitor.SolidInputs inputs(){return new CdMonitor.SolidInputs(player,gates,globals);}
        CdMonitor.Result run(){return body.runPast(WHOLE,this,inputs());}
        CdMonitor.LiveSolid live(){return new CdMonitor.LiveSolid(player,gates,globals);}
        public CdMonitor.LiveSolid preflight(CdMonitor.PrefixSolidCut cut) {
            order.add("PREFLIGHT");assertEquals(player,cut.expectedPlayer());assertEquals(gates,cut.expectedGates());
            assertEquals(globals,cut.expectedGlobals());assertOtherSlots(cut.callCut(),pool(),Set.of());
            preflightHook.accept(cut);return live();
        }
        public CdMonitor.LiveSolid commit(CdMonitor.PrefixSolidCall call) {
            calls.add(call);order.add("SOLID");
            CdObjectState object;List<CdObjectState> others;
            if(call.outcome() instanceof CdSolidObject.Completed done) {
                var r=done.completedResult();object=r.object();others=r.otherSlots();player=r.player();globals=r.globals();
            }else{var p=((CdSolidObject.NativeStopped)call.outcome()).prefix();
                object=p.object();others=p.otherSlots();player=p.player();globals=p.globals();}
            if(publishObject)pool().write(object);if(publishOthers)others.forEach(pool()::write);
            commitHook.accept(call);
            return staleReadback?new CdMonitor.LiveSolid(player,gates,new CdSolidObject.Globals(1,4,0x12,0x34)):live();
        }
        void angle(int expected,int value) {
            assertEquals(globals.primaryAngle(),expected);angleWrites.add(value);order.add("ANGLE");angleHook.run();
            globals=new CdSolidObject.Globals(globals.invincible(),globals.scoreChain(),value,globals.secondaryAngle());
        }
        void draw(CdObjectPool actual,CdObjectState.Identity identity) {
            assertSame(pool(),actual);assertEquals(slot().identity(),identity);draws.add(slot());order.add("DRAW");
            edit(slot().moveByte(SPRITE_FLAGS,drawFlags));drawHook.run();
        }
        void fm(CdMonitor.FmSound sound){assertEquals(CdMonitor.FmSound.FM_DESTROY,sound);order.add("FM");fmHook.run();}
        int read(String name,int value){reads.add(name);readHook.run();if(failRead.equals(name))throw sourceFailure;return value;}
        public int rawTimeZone(){return read("TIME",time);}
        public int rawTimeWarpDirection(){return read("DIRECTION",direction);}
        public int cameraXWord(){return read("CAMERA",camera);}
        public int timeAttackMode(){return read("ATTACK",attack);}
        public int zone(){return read("ZONE",zone);}
        public int layer(){return read("LAYER",layer);}
        public int timeStopTimer(){return read("STOP",timeStop);}
    }

    static final class FloorFixture {
        final byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256];
        final byte[] heights=new byte[4096],widths=new byte[4096];
        FloorFixture(){fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;fg[2+7*42+2]=2;}
        FloorFixture floor(int chunk,int angle) {
            if(chunk!=0){int at=(chunk-1)*512+(8*16+7)*2;chunks[at]=0x20;chunks[at+1]=1;
                ids[1]=7;angles[7]=(byte)angle;Arrays.fill(heights,7*16,8*16,(byte)16);}return this;
        }
        FloorFixture laterMissing(){floor(81,0x22);fg[2+7*42+2]=(byte)0xA8;
            int at=80*512+(7*16+7)*2;chunks[at]=0x24;chunks[at+1]=0;return this;}
        CdR11aTerrain decode(){return CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);}
    }

    static CdR11bAssets retail() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Original USA media requires soniccd.media.root");
        Path cue;try(var paths=Files.list(Path.of(root).resolve("USA"))) {
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size());cue=cues.getFirst();
        }
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            return CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
