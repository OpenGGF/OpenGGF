package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual R12B metadata, real shared Solid outcomes, declared component ports; no Frame/World/pixels claim. Java UNRUN. */
class TestCdR12bBoulderBinding {
    static final int SLOT=65,MAP=0x20940A,TILE_WORD=0x035E,SAVED=7;
    static CdR12bTerrainAssets terrain,equalTerrain;static CdR12bPlayerAssets player;
    static CdR12aTerrainAssets present;static CdR11bAssets otherPast;
    @BeforeAll static void actualOriginalUsa() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(programs);equalTerrain=CdR12bTerrainAssets.fromPrograms(programs);player=CdR12bPlayerAssets.fromPrograms(programs);
            present=CdR12aTerrainAssets.fromPrograms(programs);otherPast=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
    }
    @Test void namedFactoryIsAdditiveAndConstructionPerformsNoNativeWork() throws Exception {
        assertNotNull(CdBoulder.class.getMethod("fromAct2PastAssets",CdObjectPlacementRuntime.class,CdR12bTerrainAssets.class,CdSolidObject.class,CdBoulder.PrefixSolidCommit.class,CdBoulder.DrawObjectCall.class));
        assertEquals(1,CdBoulder.class.getConstructors().length);assertEquals(5,CdBoulder.class.getConstructors()[0].getParameterCount());assertEquals(3,CdBoulder.Assets.class.getRecordComponents().length);
        assertEquals(4,CdBoulder.PrefixSolidCall.class.getRecordComponents().length);assertEquals(4,CdBoulder.SolidCall.class.getRecordComponents().length);
        var placement=CdObjectPlacementRuntime.fromAssets(terrain,bank());var before=placement.capture();
        var body=CdBoulder.fromAct2PastAssets(placement,terrain,actualSolid(),call->fail("factory cannot invoke Solid"),(pool,id)->fail("factory cannot draw"));
        assertNotNull(body);assertEquals(before.status(),placement.status());assertEquals(before.pool().slots(),placement.capture().pool().slots());assertArrayEquals(before.savedFlags().copy(),placement.capture().savedFlags().copy());
    }
    @Test void actualRootAndRaw72TileDirectoryAreBorrowedWithoutRelaxingLegacy74() throws Exception {
        var objects=terrain.objects();var map=objects.view(CdR12bObjectAssets.View.MAP_SPR_BOULDER);var visual=objects.boulder();
        assertEquals(14,map.length());assertEquals(MAP,CdR12bObjectAssets.View.MAP_SPR_BOULDER.range().nativeAddress());assertEquals(39,CdR12bObjectAssets.View.MAP_SPR_BOULDER.ordinal());
        assertEquals("dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d",sha(map.slice(0,13)));
        assertEquals("f1613b80dc72cd1d153d190f3a660e3990f92648d54e65c989aed93bc9189652",sha(map));assertEquals(0,map.unsignedByte(13));
        assertSame(visual,objects.boulder());assertEquals(1,visual.mappingFrames().size());assertTrue(visual.animations().isEmpty());assertEquals(2,visual.mappingFrames().getFirst().pieces().size());
        for(var piece:visual.mappingFrames().getFirst().pieces())assertTrue(piece.tileOffset()+piece.tileCount()<=19);
        var tiles=objects.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS);assertEquals(72,tiles.length());assertEquals(0x3A,tiles.unsignedWord(22));assertEquals(TILE_WORD,CdBoulder.readPastTileWord(tiles,0));
        assertThrows(IllegalArgumentException.class,()->new CdBoulder.Assets(tiles,visual,MAP));
        var legacy=CdBoulder.Assets.fromAct2Assets(present.objects());assertEquals(74,legacy.objectTileIds().length());
    }
    @Test void sameRetainedPlacementGatePrecedesEveryMetadataHelperAndBodyRead() throws Exception {
        var placement=CdObjectPlacementRuntime.fromAssets(terrain,bank());var before=placement.capture();assertEquals(terrain.identity(),equalTerrain.identity());assertNotSame(terrain.placements(),equalTerrain.placements());
        assertNull(field(equalTerrain,"objects"));
        assertThrows(IllegalArgumentException.class,()->CdBoulder.fromAct2PastAssets(placement,equalTerrain,null,null,null));
        assertThrows(IllegalArgumentException.class,()->CdBoulder.fromAct2PastAssets(new CdObjectPlacementRuntime(terrain.placements()),terrain,null,null,null));
        assertThrows(IllegalArgumentException.class,()->CdBoulder.fromAct2PastAssets(CdObjectPlacementRuntime.fromAssets(present,bank()),terrain,null,null,null));
        assertThrows(IllegalArgumentException.class,()->CdBoulder.fromAct2PastAssets(CdObjectPlacementRuntime.fromAssets(otherPast,bank()),terrain,null,null,null));
        assertNull(field(equalTerrain,"objects"));assertThrows(NullPointerException.class,()->CdBoulder.fromAct2PastAssets(null,terrain,null,null,null));
        assertThrows(NullPointerException.class,()->CdBoulder.fromAct2PastAssets(placement,null,null,null,null));
        assertEquals(before.pool().slots(),placement.capture().pool().slots());assertArrayEquals(before.savedFlags().copy(),placement.capture().savedFlags().copy());
    }
    @Test void actualInitHasLiteralBMap035eTileAndOnlyItsNativeFull64Writes() {
        var h=new H();h.put(h.state().moveByte(SPRITE_FLAGS,0x63).moveByte(FLAGS,0xA5).moveByte(MAP_FRAME,7).moveWord(TILE,0xBEEF).moveLong(MAPPINGS,0x89ABCDEF));
        var before=h.placement.capture();var expected=init(h.state(),true);var result=h.body.run(SLOT,world(),null);
        assertArrayEquals(expected,result.state().copyBytes());assertEquals(MAP,result.state().longBits(MAPPINGS));assertEquals(TILE_WORD,result.state().word(TILE));
        assertEquals(List.of(new CdBoulder.RoutineEntered(CdBoulder.NativeRoutine.INIT),new CdBoulder.RoutineEntered(CdBoulder.NativeRoutine.MAIN),new CdBoulder.CallReturned(CdBoulder.NativeCall.DRAW_OBJECT),new CdBoulder.DespawnChecked(false)),result.effects());
        assertTrue(h.calls.isEmpty());assertEquals(1,h.draws.size());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void nonzeroSubtype2StopsAfterTheRealInitPrefixAndBeforeTileSolidDrawAndTail() {
        for(int subtype:new int[]{1,2,0x80,0xFF}){var h=new H();h.put(h.state().moveByte(SUBTYPE2,subtype).moveByte(SPRITE_FLAGS,0x84).moveWord(TILE,0xBEEF));
            var before=h.placement.capture();var expected=init(h.state(),false);var error=assertThrows(CdBoulder.UnsupportedBoulder.class,()->h.body.run(SLOT,world(),null));
            assertEquals(CdBoulder.UnsupportedBoundary.TILE_ID_SUBTYPE2,error.boundary());assertEquals(SLOT,error.wholeIndex());assertArrayEquals(expected,h.state().copyBytes());
            assertTrue(h.calls.isEmpty());assertTrue(h.draws.isEmpty());nonwrites(before,h,Set.of(SLOT));}
    }
    @Test void nonnegativeMainSkipsSolidInputsAndRoutineRejectionWritesNothing() {
        var h=new H();h.main();h.put(h.state().moveByte(SPRITE_FLAGS,0x63));var before=h.placement.capture();var source=h.state();
        assertEquals(source,h.body.run(SLOT,world(),null).state());assertTrue(h.calls.isEmpty());assertEquals(1,h.draws.size());nonwrites(before,h,Set.of());
        h.put(h.state().moveByte(ROUTINE,3));before=h.placement.capture();assertEquals(CdBoulder.UnsupportedBoundary.ROUTINE_DISPATCH,assertThrows(CdBoulder.UnsupportedBoulder.class,h::run).boundary());nonwrites(before,h,Set.of());
    }
    @Test void actualSourcePlainSolidGetOffPublishesFullValuesAndOneOrderedFmRequest() {
        var h=new H();h.main();h.debug=0x100;h.p.flags=8;h.p.stand=SLOT;h.p.charge=1;h.p.push=SLOT;h.put(h.state().moveByte(FLAGS,8).moveByte(SOLID_TYPE,0xA5));
        var before=h.placement.capture();var result=h.run();var call=h.calls.getFirst();var solid=assertInstanceOf(CdSolidObject.Completed.class,call.outcome()).completedResult();
        assertEquals(Optional.of(CdSolidObject.GetOffReason.DEBUG_MODE),solid.getOffReason());assertEquals(CdSolidObject.Entry.SOLID_OBJECT,solid.entry());
        assertSame(solid,assertInstanceOf(CdBoulder.Solid.class,result.effects().get(1)).result());assertEquals(solid.player(),h.livePlayer);assertEquals(solid.globals(),h.liveGlobals);
        assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),h.sounds);assertEquals(0,h.state().unsignedByte(SOLID_TYPE));assertEquals(0,h.livePlayer.standObject());
        assertEquals(solid.object().moveByte(SPRITE_FLAGS,4),result.state());assertEquals(1,h.calls.size());assertEquals(1,h.draws.size());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void actualSourcePlainSolidSidePushUsesRealAlgorithmAndRetainsFractionsAndOtherBytes() {
        var h=new H();h.main();h.p.x=0x0FE78000;h.p.y=0x02001234;h.p.flags=0;h.p.xv=0x600;h.p.yv=0;h.p.gv=0x333;
        var before=h.placement.capture();var expected=h.p.copy();expected.x=0x0FE68000;expected.xv=0;expected.gv=0;expected.flags=0x20;expected.push=SLOT;
        var result=h.run();var solid=assertInstanceOf(CdSolidObject.Completed.class,h.calls.getFirst().outcome()).completedResult();
        assertEquals(CdSolidObject.Path.HALT_ON_X,solid.path());assertEquals(OptionalInt.of(0),solid.d0());assertEquals(expected.player(),h.livePlayer);
        assertEquals(before.pool().slots().get(SLOT-32).moveByte(FLAGS,0x20),solid.object());assertEquals(solid.object().moveByte(SPRITE_FLAGS,4),result.state());
        assertEquals(1,h.calls.size());assertEquals(1,h.draws.size());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void constructedCollisionComponentProducesGenuineStoppedPrefixBeforeOriginalException() {
        var h=new H(new CollisionFixture().solid());h.main();h.p.flags=8;h.p.stand=1;h.p.yv=0;h.put(h.state().moveByte(SOLID_TYPE,2));var before=h.placement.capture();
        var error=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::run);var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,h.calls.getFirst().outcome());
        assertSame(stopped.stop().originalException(),error);assertEquals(CdSolidObject.StopSite.STAND_RELEASE,stopped.stop().site());
        assertEquals(1,assertInstanceOf(CdSolidObject.SlotAddress.class,stopped.stop().detail()).slotByte());assertEquals(stopped.prefix().object(),h.state());
        assertEquals(8,h.state().flags());assertEquals(0,h.state().unsignedByte(SOLID_TYPE));assertEquals(0x84,h.state().spriteFlags());
        var expected=h.p.copy();expected.jump=0;assertEquals(expected.player(),h.livePlayer);assertEquals(stopped.prefix().globals(),h.liveGlobals);
        assertFalse(h.body.isCurrentPrefixSolidCall(h.calls.getFirst()));assertTrue(h.draws.isEmpty());nonwrites(before,h,Set.of(SLOT));
    }
    @Test void drawClearsTheActualLiveSignBeforeDespawnReloadAndOnlyThePastSavedByteChanges() {
        var h=new H();h.main();h.debug=0x100;h.put(h.state().moveWord(X,0x3000));var before=h.placement.capture();var result=h.run();
        assertEquals(1,h.calls.size());assertEquals(1,h.draws.size());assertTrue(result.deleted());assertArrayEquals(new byte[64],result.state().copyBytes());
        for(var image:before.pool().slots())if(image.identity().wholeIndex()!=SLOT)assertEquals(image,h.pool().state(image.identity().wholeIndex()));
        byte[] expected=before.savedFlags().copy();expected[2+3*SAVED]&=0x7F;assertArrayEquals(expected,h.placement.capture().savedFlags().copy());assertEquals(before.status(),h.placement.status());
    }
    @Test void aMissingPublicationOrUnrelatedCallbackWriteCannotPassTheFull96Readback() {
        var missing=new H();missing.main();missing.debug=0x100;missing.put(missing.state().moveByte(SOLID_TYPE,0xA5));missing.publish=false;
        assertThrows(IllegalStateException.class,missing::run);assertEquals(1,missing.calls.size());assertTrue(missing.draws.isEmpty());
        var corrupt=new H();corrupt.main();corrupt.debug=0x100;corrupt.before=call->corrupt.pool().write(corrupt.pool().state(66).moveByte(SUBTYPE,0xA5));
        assertThrows(IllegalStateException.class,corrupt::run);assertEquals(1,corrupt.calls.size());assertTrue(corrupt.draws.isEmpty());
    }
    @Test void realPendingCallIdentityReentryAndCallbackFailureKeepTheirExistingPrefixRules() {
        var h=new H();h.main();h.debug=0x100;h.before=call->{assertTrue(h.body.isCurrentPrefixSolidCall(call));assertFalse(h.body.isCurrentPrefixSolidCall(new CdBoulder.PrefixSolidCall(call.callCut(),call.expectedPlayer(),call.expectedGlobals(),call.outcome())));assertThrows(IllegalStateException.class,h::run);};
        h.drawHook=()->assertThrows(IllegalStateException.class,h::run);h.run();assertFalse(h.body.isCurrentPrefixSolidCall(h.calls.getFirst()));assertEquals(1,h.calls.size());assertEquals(1,h.draws.size());
        var failed=new H();failed.main();failed.debug=0x100;failed.put(failed.state().moveByte(SOLID_TYPE,0xA5));var before=failed.placement.capture();var fault=new Fault();failed.after=call->{throw fault;};
        assertSame(fault,assertThrows(Fault.class,failed::run));var done=assertInstanceOf(CdSolidObject.Completed.class,failed.calls.getFirst().outcome()).completedResult();
        assertEquals(done.object(),failed.state());assertEquals(done.player(),failed.livePlayer);assertTrue(failed.draws.isEmpty());assertFalse(failed.body.isCurrentPrefixSolidCall(failed.calls.getFirst()));nonwrites(before,failed,Set.of(SLOT));
    }

    static final class H {
        final CdObjectPlacementRuntime placement=CdObjectPlacementRuntime.fromAssets(terrain,bank());final CdBoulder body;final P p=new P();
        final List<CdBoulder.PrefixSolidCall> calls=new ArrayList<>();final List<CdObjectState> draws=new ArrayList<>();final List<CdPlayerTouchResponse.FmSound> sounds=new ArrayList<>();
        CdSolidObject.Player livePlayer;CdSolidObject.Globals liveGlobals;int debug;boolean publish=true;Consumer<CdBoulder.PrefixSolidCall> before=call->{};Consumer<CdBoulder.PrefixSolidCall> after=call->{};Runnable drawHook=()->{};
        H(){this(actualSolid());}H(CdSolidObject solid){for(int whole=32;whole<128;whole++)pool().write(dirty(pool().state(whole)).moveByte(ID,0));
            put(state().moveByte(ID,0x1B).moveByte(ROUTINE,0).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,SAVED).moveByte(SPRITE_FLAGS,0x63).moveWord(X,0x1000).moveWord(Y,0x200));
            body=CdBoulder.fromAct2PastAssets(placement,terrain,solid,this::commit,this::draw);}
        CdObjectPool pool(){return placement.pool();}CdObjectState state(){return pool().state(SLOT);}void put(CdObjectState image){pool().write(image);}
        void main(){put(state().moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(SOLID_TYPE,0).moveByte(WIDTH,16).moveByte(Y_RADIUS,16).moveByte(PRIORITY,4).moveLong(MAPPINGS,MAP).moveWord(TILE,TILE_WORD).moveWord(X_VELOCITY,0).moveWord(Y_VELOCITY,0));}
        CdBoulder.Result run(){livePlayer=p.player();liveGlobals=new CdSolidObject.Globals(0,4,0x12,0x34);return body.run(SLOT,world(),new CdBoulder.SolidInputs(livePlayer,new CdSolidObject.Gates(debug,0),liveGlobals));}
        void commit(CdBoulder.PrefixSolidCall call){calls.add(call);assertTrue(body.isCurrentPrefixSolidCall(call));pool().validateRestore(call.callCut());assertEquals(call.callCut().slots(),pool().capture().slots());assertEquals(livePlayer,call.expectedPlayer());assertEquals(liveGlobals,call.expectedGlobals());before.accept(call);if(!publish)return;
            CdObjectState object;List<CdObjectState> others;if(call.outcome() instanceof CdSolidObject.Completed d){var result=d.completedResult();object=result.object();others=result.otherSlots();livePlayer=result.player();liveGlobals=result.globals();sounds.addAll(result.fmRequests());}
            else{var prefix=((CdSolidObject.NativeStopped)call.outcome()).prefix();object=prefix.object();others=prefix.otherSlots();livePlayer=prefix.player();liveGlobals=prefix.globals();sounds.addAll(prefix.fmRequests());}
            var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(others);pool().validateRestore(CdObjectPool.overlay(call.callCut(),images));images.forEach(pool()::write);after.accept(call);}
        void draw(CdObjectPool pool,CdObjectState.Identity id){assertSame(pool(),pool);assertEquals(SLOT,id.wholeIndex());var live=pool.state(id.wholeIndex());draws.add(live);pool.write(live.moveByte(SPRITE_FLAGS,live.spriteFlags()&0x7F));drawHook.run();}
    }
    static final class P {
        int x=0x10008000,y=0x01DD1234,xv=0x123,yv=0x100,gv,flags=2,jump=1,stand,push,angle=0x10,charge;
        P copy(){var p=new P();p.x=x;p.y=y;p.xv=xv;p.yv=yv;p.gv=gv;p.flags=flags;p.jump=jump;p.stand=stand;p.push=push;p.angle=angle;p.charge=charge;return p;}
        CdSolidObject.Player player(){return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x,y,xv,yv,gv,2,angle,flags,4,9,19,0,0,jump,charge,0,0,0,0x56,0x78),0x780,2,0,0),1,push,stand);}
    }
    /** Explicit decoded collision-format negative control, reused from the existing Boulder fixture; no fake Solid Outcome. */
    static final class CollisionFixture {
        CdSolidObject solid(){byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256],heights=new byte[4096],widths=new byte[4096];
            fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)2);ids[1]=1;ids[2]=2;Arrays.fill(heights,16,32,(byte)16);Arrays.fill(widths,32,48,(byte)16);angles[2]=(byte)0xC0;
            var t=CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);byte[] animations=new byte[578];for(int i=0;i<60;i++)word(animations,i*2,120);animations[122]=(byte)0xFF;
            byte[] maps=new byte[398],dplcs=new byte[398];for(int i=0;i<198;i++){word(maps,i*2,396);word(dplcs,i*2,396);}var visual=CdSonicVisualAssets.decode(maps,dplcs,new byte[69472]);byte[] atan=new byte[257];for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);
            var assets=CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)0xFF},new byte[4],new byte[640],atan,visual);return new CdSolidObject(new CdPlayerMovement(t,assets));}
    }
    private static CdSolidObject actualSolid(){return new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromAct2Past(terrain,player)));}
    private static CdBoulder.Globals world(){return new CdBoulder.Globals(0,0,0x1080);}
    private static CdAssetBytes bank(){byte[] b=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];for(int i=0;i<b.length;i++)b[i]=(byte)(i*37+0xA6);return CdAssetBytes.copyOf(b);}
    private static byte[] init(CdObjectState source,boolean tile){byte[] b=source.copyBytes();b[0x24]+=2;b[1]|=4;b[0x18]=4;ByteBuffer.wrap(b).putInt(4,MAP);b[0x19]=16;b[0x16]=16;b[0x1A]=0;if(tile)word(b,2,TILE_WORD);return b;}
    private static void word(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
    private static String sha(CdAssetBytes bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copy()));}
    private static void nonwrites(CdObjectPlacementRuntime.Snapshot before,H h,Set<Integer> changed){for(var image:before.pool().slots())if(!changed.contains(image.identity().wholeIndex()))assertEquals(image,h.pool().state(image.identity().wholeIndex()));assertEquals(before.status(),h.placement.status());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());}
    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static CdObjectState dirty(CdObjectState source){try{byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(source.identity().wholeIndex()*13+i*7);var c=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);c.setAccessible(true);return c.newInstance(field(source,"owner"),source.identity(),b);}catch(Exception e){throw new AssertionError(e);}}
    static final class Fault extends RuntimeException{private static final long serialVersionUID=1L;}
}
