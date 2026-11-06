package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original-USA sources and Frame components, explicit ingresses and genuine Top outcomes; no World/route/pixel claim. Java UNRUN. */
class TestCdR12bFloatBlockBinding {
    static final int SLOT=33,MAP=0x20A1B0,TILE_WORD=0x434C;
    static CdR12bTerrainAssets terrain,equalTerrain;static CdR12bPlayerAssets playerAssets;static CdR12aTerrainAssets present;static CdR11bAssets otherPast;
    @BeforeAll static void originalUsa() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(programs);equalTerrain=CdR12bTerrainAssets.fromPrograms(programs);playerAssets=CdR12bPlayerAssets.fromPrograms(programs);
            present=CdR12aTerrainAssets.fromPrograms(programs);otherPast=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
    }
    @Test void additiveNamedFivePortFactoryAndAssetsKeepAllLegacyShapesAndConstructorPurity() throws Exception {
        assertNotNull(CdFloatBlock.class.getMethod("fromAct2PastAssets",CdObjectPlacementRuntime.class,CdR12bTerrainAssets.class,CdSolidObject.class,CdFloatBlock.PrefixTopSolidCommit.class,CdFloatBlock.DrawObjectCall.class));
        assertEquals(1,CdFloatBlock.class.getConstructors().length);assertEquals(5,CdFloatBlock.class.getConstructors()[0].getParameterCount());assertEquals(3,CdFloatBlock.Assets.class.getRecordComponents().length);assertEquals(4,CdFloatBlock.PrefixTopSolidCall.class.getRecordComponents().length);
        assertEquals(1,Arrays.stream(CdFloatBlock.class.getDeclaredMethods()).filter(m->m.getName().equals("fromPastAssets")).count());assertEquals(1,Arrays.stream(CdFloatBlock.class.getDeclaredMethods()).filter(m->m.getName().equals("fromAct2Assets")).count());
        var h=new H();var before=h.placement.capture();var actor=h.frame.captureActor();var shared=h.frame.captureShared();
        assertNotNull(CdFloatBlock.fromAct2PastAssets(h.placement,terrain,actualSolid(),call->fail("constructor cannot compute/publish Top"),(pool,id)->fail("constructor cannot draw")));
        nonwrites(before,h,Set.of());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertTrue(h.calls.isEmpty());assertEquals(0,h.draws);
    }
    @Test void actualBMapScriptsAndRaw72AreCompleteAndEmptyFrameZeroIsNotReplaced() throws Exception {
        var o=terrain.objects();var a=CdFloatBlock.Assets.fromAct2PastAssets(o);var v=o.floatBlock();assertSame(v,o.floatBlock());assertSame(v,a.visual());
        assertEquals(6,v.mappingFrames().size());assertEquals(3,v.animations().size());assertTrue(v.mappingFrames().getFirst().pieces().isEmpty());for(var f:v.mappingFrames())for(var piece:f.pieces())assertTrue(piece.tileOffset()+piece.tileCount()<=15);
        assertEquals(MAP,a.mappingsAddress());assertEquals(MAP,CdR12bObjectAssets.View.MAP_SPR_FLOAT_BLOCK.range().nativeAddress());assertEquals(44,CdR12bObjectAssets.View.MAP_SPR_FLOAT_BLOCK.ordinal());assertEquals(45,CdR12bObjectAssets.View.ANI_FLOAT_BLOCK.ordinal());
        var map=o.view(CdR12bObjectAssets.View.MAP_SPR_FLOAT_BLOCK);var ani=o.view(CdR12bObjectAssets.View.ANI_FLOAT_BLOCK);assertEquals(76,map.length());assertEquals(30,ani.length());
        assertEquals("98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e",sha(map));assertEquals("3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9",sha(ani));assertArrayEquals(ani.copy(),v.animationTable().copy());
        assertEquals(72,a.objectTileIds().length());assertEquals(0x2E,a.objectTileIds().unsignedWord(10));assertEquals(TILE_WORD,a.objectTileIds().unsignedWord(0x2E));
    }
    @Test void exactSourceGatePrecedesMetadataAndAllHelpersAndCallbacks() throws Exception {
        var h=new H();var before=h.placement.capture();assertEquals(terrain.identity(),equalTerrain.identity());assertNotSame(terrain.placements(),equalTerrain.placements());assertNull(field(equalTerrain,"objects"));
        for(var p:List.of(new CdObjectPlacementRuntime(terrain.placements()),CdObjectPlacementRuntime.fromAssets(present,bank()),CdObjectPlacementRuntime.fromAssets(otherPast,bank())))assertThrows(IllegalArgumentException.class,()->CdFloatBlock.fromAct2PastAssets(p,terrain,null,null,null));
        assertThrows(IllegalArgumentException.class,()->CdFloatBlock.fromAct2PastAssets(h.placement,equalTerrain,null,null,null));assertNull(field(equalTerrain,"objects"));
        assertThrows(NullPointerException.class,()->CdFloatBlock.fromAct2PastAssets(null,terrain,null,null,null));assertThrows(NullPointerException.class,()->CdFloatBlock.fromAct2PastAssets(h.placement,null,null,null,null));nonwrites(before,h,Set.of());
    }
    @Test void legacyCompletedOnly74DecoderCannotConsumeTheNewActual72Ingress() {
        var h=new H();var before=h.placement.capture();var assets=CdFloatBlock.Assets.fromAct2PastAssets(terrain.objects());
        assertThrows(IllegalArgumentException.class,()->CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(),5,0));
        var old=new CdFloatBlock(h.placement,assets,actualSolid(),call->fail("old74 decoder must reject before helper"),(pool,id)->fail("old74 decoder must reject before draw"));
        assertThrows(IllegalArgumentException.class,()->old.run(SLOT,h.globals,h.inputs()));nonwrites(before,h,Set.of());assertTrue(h.calls.isEmpty());
        assertEquals(74,CdFloatBlock.Assets.fromAct2Assets(present.objects()).objectTileIds().length());
    }
    @Test void actualPastInitHasLiteral434cMapAndTwoThreadedRealTopsBeforeDraw() {
        var h=new H();var before=h.placement.capture();byte[] expected=h.slot().copyBytes();expected[0x24]+=2;expected[1]|=4;l(expected,4,MAP);w(expected,2,TILE_WORD);expected[0x18]=1;expected[0x19]=12;expected[0x16]=12;expected[0x1A]=5;expected[0x25]=0;expected[1]&=0x7F;
        var actor=h.frame.captureActor();var shared=h.frame.captureShared();var result=h.run();assertArrayEquals(expected,result.state().copyBytes());assertEquals(2,h.calls.size());assertEquals(1,h.draws);
        assertEquals(List.of("top0","top1","draw"),h.order);for(var call:h.calls)assertEquals(CdSolidObject.Entry.TOP_SOLID_OBJECT,assertInstanceOf(CdSolidObject.Completed.class,call.outcome()).completedResult().entry());
        assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void selectedRaw72WordFaultRetainsOnlyReachedInitPrefixWithoutTopOrActorWork() {
        var h=new H();h.edit(s->s.moveByte(SUBTYPE2,1).moveWord(TILE,0xBEEF).moveLong(MAPPINGS,0x89ABCDEF));var before=h.placement.capture();byte[] expected=h.slot().copyBytes();expected[0x24]+=2;expected[1]|=4;l(expected,4,MAP);
        var actor=h.frame.captureActor();var shared=h.frame.captureShared();var failure=assertThrows(CdFloatBlock.UnsupportedFloatBlock.class,h::run);assertEquals(CdFloatBlock.UnsupportedBoundary.TILE_ID_READ,failure.boundary());
        assertArrayEquals(expected,h.slot().copyBytes());assertTrue(h.calls.isEmpty());assertEquals(0,h.draws);assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void firstActualTopFramePublicationFeedsTheSecondAndFmIsNotReplayed() {
        var h=new H(actualSolid(),8,SLOT,1,0x100,0x200);h.main();h.edit(s->s.moveByte(FLAGS,8));var before=h.placement.capture();var clock=h.frame.vIntClock();h.run();
        assertEquals(2,h.calls.size());var first=assertInstanceOf(CdSolidObject.Completed.class,h.calls.getFirst().outcome()).completedResult();var second=h.calls.get(1);
        assertEquals(first.player(),second.player());assertEquals(first.globals(),second.globals());assertEquals(0,h.frame.state().objectLinks().standObject());assertEquals(2,h.frame.state().motion().flags());
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),h.sounds);assertEquals(List.of("top0","top1","draw"),h.order);assertEquals(clock,h.frame.vIntClock());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void fallWordMotionAndPlainDeletesRetainFractionsAndAllSavedFlags() {
        var h=new H();h.main();h.edit(s->s.moveByte(ROUTINE,4).moveWord(Y,0x200).moveWord(Y_FRACTION,0xFEDC).moveByte(SAVED_FLAGS_ID,7));h.globals=new CdFloatBlock.Globals(0,0x1080,0x100);
        var before=h.placement.capture();var result=h.run();assertEquals(2,h.calls.size());assertEquals(0,h.draws);assertArrayEquals(new byte[64],result.state().copyBytes());
        assertEquals(List.of(CdFloatBlock.DeleteSite.FALL_BELOW_CAMERA,CdFloatBlock.DeleteSite.CHUNK_WINDOW),result.effects().stream().filter(CdFloatBlock.Deleted.class::isInstance).map(CdFloatBlock.Deleted.class::cast).map(CdFloatBlock.Deleted::site).toList());nonwrites(before,h,Set.of(SLOT));
        var kept=new H();kept.main();kept.edit(s->s.moveByte(ROUTINE,4).moveWord(Y,0x200).moveWord(Y_FRACTION,0xFEDC));kept.globals=new CdFloatBlock.Globals(0,0x1080,0x200);var keptBefore=kept.placement.capture();var returned=kept.run();assertEquals(0x202,returned.state().centreY());assertEquals(0xFEDC,returned.state().word(Y_FRACTION));assertEquals(1,kept.draws);nonwrites(keptBefore,kept,Set.of(SLOT));
    }
    @Test void resetAndVisibleNoStandingBranchesKeepTheirNoDrawAndNoAnimatorCuts() {
        var reset=new H();reset.main();reset.edit(s->s.moveByte(ROUTINE,12).moveWord(X,0x280));reset.globals=new CdFloatBlock.Globals(0,0x80,0x200);var before=reset.placement.capture();var expected=reset.slot().moveByte(ROUTINE,2);assertEquals(expected,reset.run().state());assertTrue(reset.calls.isEmpty());assertEquals(0,reset.draws);nonwrites(before,reset,Set.of(SLOT));
        var visible=new H();visible.main();visible.edit(s->s.moveByte(ROUTINE,8).moveByte(FLAGS,0).moveByte(ANIMATION,9).moveByte(PREVIOUS_ANIMATION,7).moveByte(ANIMATION_FRAME,14).moveByte(ANIMATION_TIME,20));before=visible.placement.capture();expected=visible.slot().moveByte(ROUTINE,10).moveByte(ANIMATION,2).moveByte(SOLID_TYPE,0);assertEquals(expected,visible.run().state());assertEquals(2,visible.calls.size());assertEquals(0,visible.draws);assertEquals(0,visible.calls.getFirst().callCut().slots().get(SLOT-32).unsignedByte(ANIMATION));nonwrites(before,visible,Set.of(SLOT));
    }
    @Test void actualVanishFcAdvancesOnlyItsReachedRoutineAndFiniteFailureKeepsPrefix() {
        var h=new H();h.main();h.edit(s->s.moveByte(ROUTINE,10).moveByte(ANIMATION,2).moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_FRAME,8).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,0));var before=h.placement.capture();
        var expected=h.slot().moveByte(ROUTINE,12).moveByte(ANIMATION_TIME,2).moveByte(SOLID_TYPE,0).moveByte(SPRITE_FLAGS,4);var result=h.run();assertEquals(expected,result.state());assertEquals(2,h.calls.size());assertEquals(1,h.draws);assertEquals(1,result.effects().stream().filter(e->e instanceof CdFloatBlock.CallReturned c&&c.call()==CdFloatBlock.NativeCall.ANIMATE_OBJECT).count());nonwrites(before,h,Set.of(SLOT));
        var failed=new H();failed.main();failed.edit(s->s.moveByte(ROUTINE,10).moveByte(ANIMATION,3).moveByte(PREVIOUS_ANIMATION,7).moveByte(ANIMATION_FRAME,55).moveByte(ANIMATION_TIME,9));before=failed.placement.capture();expected=failed.slot().moveByte(PREVIOUS_ANIMATION,3).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0xFF).moveByte(SOLID_TYPE,0);
        assertThrows(UnsupportedOperationException.class,failed::run);assertEquals(expected,failed.slot());assertEquals(2,failed.calls.size());assertEquals(0,failed.draws);nonwrites(before,failed,Set.of(SLOT));
    }
    @Test void genuineStoppedFirstTopPublishesItsOriginalPrefixAndNeverRunsThePairTail() {
        var h=new H(new CollisionFixture().solid(),8,1,0,0,0x1E1);h.main();h.edit(s->s.moveByte(FLAGS,0).moveByte(SOLID_TYPE,2));var before=h.placement.capture();RuntimeException error=assertThrows(RuntimeException.class,h::run);
        assertEquals(1,h.calls.size());var call=h.calls.getFirst();var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,call.outcome());assertSame(stopped.stop().originalException(),error);assertEquals(CdSolidObject.StopSite.STAND_RELEASE,stopped.stop().site());
        assertEquals(stopped.prefix().object(),h.slot());assertEquals(stopped.prefix().player(),h.player());assertEquals(stopped.prefix().globals(),h.solidGlobals());assertFalse(h.body.isCurrentPrefixTopSolidCall(call,0));assertEquals(0,h.draws);nonwrites(before,h,Set.of(SLOT));
    }
    @Test void trueCallOrdinalsAndDrawIdentityRejectCopiesReentryAndMissingPublication() {
        var h=new H();h.main();h.after=call->assertThrows(IllegalStateException.class,h::run);h.drawBefore=()->assertThrows(IllegalStateException.class,h::run);h.run();assertEquals(2,h.calls.size());assertEquals(1,h.draws);assertFalse(h.body.isCurrentPrefixTopSolidCall(h.calls.getFirst(),0));
        var missing=new H();missing.main();missing.edit(s->s.moveByte(SOLID_TYPE,0xA5));missing.publish=false;assertThrows(IllegalStateException.class,missing::run);assertEquals(1,missing.calls.size());assertEquals(0,missing.draws);
        var failed=new H();failed.main();failed.edit(s->s.moveByte(SOLID_TYPE,0xA5));var before=failed.placement.capture();var fault=new Fault();failed.after=call->{throw fault;};assertSame(fault,assertThrows(Fault.class,failed::run));
        var done=assertInstanceOf(CdSolidObject.Completed.class,failed.calls.getFirst().outcome()).completedResult();assertEquals(done.object(),failed.slot());assertEquals(done.player(),failed.player());assertEquals(1,failed.calls.size());assertEquals(0,failed.draws);nonwrites(before,failed,Set.of(SLOT));
    }

    static final class H {
        final CdObjectPlacementRuntime placement=CdObjectPlacementRuntime.fromAssets(terrain,bank());final CdPlayerFrameRuntime frame;final CdFloatBlock body;final List<CdFloatBlock.PrefixTopSolidCall> calls=new ArrayList<>();final List<CdPlayerTouchResponse.FmSound> sounds=new ArrayList<>();final List<String> order=new ArrayList<>();int draws;boolean publish=true;Consumer<CdFloatBlock.PrefixTopSolidCall> after=call->{};Runnable drawBefore=()->{};
        CdFloatBlock.Globals globals=new CdFloatBlock.Globals(0,0x1080,0x200);
        H(){this(actualSolid(),2,0,0,0x100,0x200);}
        H(CdSolidObject solid,int flags,int stand,int charge,int debug,int y){
            for(int whole=32;whole<128;whole++)pool().write(dirty(pool().state(whole)).moveByte(ID,0));edit(s->s.moveByte(ID,0x12).moveByte(ROUTINE,0).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,7).moveByte(SPRITE_FLAGS,0x63).moveByte(FLAGS,0).moveWord(X,0x1000).moveWord(Y,0x200));
            var motion=new CdPlayerMotionState(0x10008000,(y<<16)|0x1234,0x123,0,0x333,2,0x10,flags,4,9,19,0,0,1,charge,0,0,0,0x56,0x78);
            var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0x230F60,0x780,2,24),new CdPlayerBody.Timers(0,0,0,0x3456),new CdPlayerBody.History(bytes(256,0x35),0),0x12,new CdPlayerBody.ObjectLinks(0,stand),0x34);
            var b=terrain.loadPlan().bounds();var environment=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,0xC,0x80),new CdPlayerMovement.Bounds(b.left(),b.right(),b.bottom(),0),new CdPlayerMovement.Gates(1,0,0,0,0,0,0),new CdPlayerMovement.Globals(0,96,0x12,0x34,0x5678,4,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(0x1234,160,0,debug,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
            frame=CdPlayerFrameRuntime.fromAct2PastEntry(terrain,playerAssets,state,environment,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),17));body=CdFloatBlock.fromAct2PastAssets(placement,terrain,solid,this::commit,this::draw);
        }
        CdObjectPool pool(){return placement.pool();}CdObjectState slot(){return pool().state(SLOT);}void edit(UnaryOperator<CdObjectState> op){pool().write(op.apply(slot()));}
        void main(){edit(s->s.moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(SOLID_TYPE,0).moveByte(WIDTH,12).moveByte(Y_RADIUS,12).moveByte(PRIORITY,1).moveWord(TILE,TILE_WORD).moveLong(MAPPINGS,MAP).moveByte(MAP_FRAME,5).moveWord(X_VELOCITY,0).moveWord(Y_VELOCITY,0));}
        CdSolidObject.Player player(){var s=frame.state();var h=s.header();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());}
        CdSolidObject.Globals solidGlobals(){var e=frame.environment();var g=e.movement().globals();return new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());}
        CdFloatBlock.SolidInputs inputs(){var e=frame.environment();return new CdFloatBlock.SolidInputs(player(),new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),solidGlobals());}
        CdFloatBlock.Result run(){return body.run(SLOT,globals,inputs());}
        void commit(CdFloatBlock.PrefixTopSolidCall call){int ordinal=calls.size();assertTrue(body.isCurrentPrefixTopSolidCall(call,ordinal));assertFalse(body.isCurrentPrefixTopSolidCall(call,ordinal+1));assertFalse(body.isCurrentPrefixTopSolidCall(new CdFloatBlock.PrefixTopSolidCall(call.callCut(),call.player(),call.globals(),call.outcome()),ordinal));
            assertEquals(call.callCut().slots(),pool().capture().slots());assertEquals(call.player(),player());assertEquals(call.globals(),solidGlobals());calls.add(call);order.add("top"+ordinal);if(!publish)return;
            CdObjectState object;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> fm;CdPlayerFrameRuntime.ObjectSolidPublication readyFrame;
            if(call.outcome() instanceof CdSolidObject.Completed d){var r=d.completedResult();object=r.object();others=r.otherSlots();fm=r.fmRequests();readyFrame=frame.prepareObjectSolidResult(call.player(),call.globals(),r);}
            else{var r=((CdSolidObject.NativeStopped)call.outcome()).prefix();object=r.object();others=r.otherSlots();fm=r.fmRequests();readyFrame=frame.prepareObjectSolidPrefix(call.player(),call.globals(),r);}
            var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(others);var readyPool=CdObjectPool.overlay(call.callCut(),images);pool().validateRestore(readyPool);frame.validatePreparedObjectSolidPublication(readyFrame);images.forEach(pool()::write);frame.applyPreparedObjectSolidPublication(readyFrame);sounds.addAll(fm);after.accept(call);
        }
        void draw(CdObjectPool pool,CdObjectState.Identity identity){assertSame(pool(),pool);assertTrue(body.isCurrentDrawCall(pool,identity));assertFalse(body.isCurrentDrawCall(pool,new CdObjectState.Identity(identity.wholeIndex())));drawBefore.run();order.add("draw");draws++;var live=pool.state(identity.wholeIndex());pool.write(live.moveByte(SPRITE_FLAGS,live.spriteFlags()&0x7F));}
    }
    /** Explicit existing decoded-format collision control; real shared Top computes the stop, not a manufactured Outcome. */
    static final class CollisionFixture {
        CdSolidObject solid(){byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256],heights=new byte[4096],widths=new byte[4096];fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)2);ids[1]=1;ids[2]=2;Arrays.fill(heights,16,32,(byte)16);Arrays.fill(widths,32,48,(byte)16);angles[2]=(byte)0xC0;var terrain=CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);
            byte[] animations=new byte[578];for(int i=0;i<60;i++)w(animations,i*2,120);animations[122]=(byte)0xFF;byte[] maps=new byte[398],dplcs=new byte[398];for(int i=0;i<198;i++){w(maps,i*2,396);w(dplcs,i*2,396);}var visual=CdSonicVisualAssets.decode(maps,dplcs,new byte[69472]);byte[] atan=new byte[257];for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);return new CdSolidObject(new CdPlayerMovement(terrain,CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)0xFF},new byte[4],new byte[640],atan,visual)));}
    }
    private static CdSolidObject actualSolid(){return new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromAct2Past(terrain,playerAssets)));}
    private static void nonwrites(CdObjectPlacementRuntime.Snapshot before,H h,Set<Integer> changed){for(var image:before.pool().slots())if(!changed.contains(image.identity().wholeIndex()))assertEquals(image,h.pool().state(image.identity().wholeIndex()));assertEquals(before.status(),h.placement.status());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());assertFalse(h.frame.closed());}
    private static CdAssetBytes bank(){byte[] b=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];for(int i=0;i<b.length;i++)b[i]=(byte)(i*37+0xA6);return CdAssetBytes.copyOf(b);}private static CdAssetBytes bytes(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
    private static void w(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}private static void l(byte[] b,int at,int v){w(b,at,v>>>16);w(b,at+2,v);}
    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}private static String sha(CdAssetBytes b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.copy()));}
    private static CdObjectState dirty(CdObjectState s){try{byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(s.identity().wholeIndex()*13+i*7);var c=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);c.setAccessible(true);return c.newInstance(field(s,"owner"),s.identity(),b);}catch(Exception e){throw new AssertionError(e);}}
    static final class Fault extends RuntimeException{private static final long serialVersionUID=1L;}
}
