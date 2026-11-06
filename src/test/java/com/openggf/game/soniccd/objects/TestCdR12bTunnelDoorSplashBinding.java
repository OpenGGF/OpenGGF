package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Actual original-USA R12B component binding; declared slot ingress is not an observed World child.
 * Tests authored first; Java UNRUN. N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Spin Tunnel/Main (Door).asm:135-177, Data/Mappings (Door Splash).asm:1-94,
 * Data/Animations (Door Splash).asm:1-9; Object Tile IDs (Act 2 Past).asm:21,53-54.
 */
class TestCdR12bTunnelDoorSplashBinding {
    private static CdR12bTerrainAssets terrain,independent;
    private static CdR12bPlayerAssets player;
    private static CdR12aTerrainAssets legacy;
    private static CdMmdImage original;
    private static final int WHOLE=41;
    private static final String MAP="000e001a0026002c00420058005e02f0050000fcf8000004f40002e0000005f8e80e0006f00001e00f0012f004d0060022f8d8000028f0e000002908e80e002af00004c0000036f8c8060037f8d800003df0e00f003ef00001f009004ef401f8040054f8";
    private static final String ANI="0006000e0014030004030102fc0003000102fc00030605fc";
    @BeforeAll static void actualOriginalSources()throws Exception{
        String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Set soniccd.media.root to original USA media");Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var media=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(media);independent=CdR12bTerrainAssets.fromPrograms(media);player=CdR12bPlayerAssets.fromPrograms(media);
            legacy=CdR12aTerrainAssets.fromPrograms(media);original=media.mmd(CdNativeFile.R12B);
        }
    }
    @Test void independentNativeLiteralTablesAndReachedR12bOperandsSurviveSourceClose(){
        var o=terrain.objects();var assets=CdTunnelDoorSplash.Assets.fromAct2PastAssets(o);
        assertSame(o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS),assets.objectTileIds());assertEquals(72,assets.objectTileIds().length());
        assertSame(o.tunnelDoorSplash(),assets.visual());assertEquals(0x21D652,assets.mappingsAddress());
        assertArrayEquals(HexFormat.of().parseHex(MAP),assets.visual().mappings().copy());assertArrayEquals(HexFormat.of().parseHex(ANI),assets.visual().animationTable().copy());
        assertEquals(0x2061A6L,o.view(CdR12bObjectAssets.View.OBJECT_INDEX).unsignedLong((0x0B-1)*4));
        for(var operand:List.of("3c72:002061a6","61b4:000600420052","61ca:217c0021d6520004","61d8:700d4eb90020cbde","61f6:43f90021d63a")){
            var pair=operand.split(":");assertEquals(pair[1],HexFormat.of().formatHex(original.readFile(Integer.parseInt(pair[0],16),pair[1].length()/2)));
        }
        assertEquals(0x3E,assets.objectTileIds().unsignedWord(0x0D*2));assertEquals(0x40,CdObjectTileIds.endOfBody(assets.objectTileIds(),0x0D));assertEquals(0,assets.objectTileIds().unsignedWord(0x3E));
        assertEquals(37,terrain.sectionSources().sources().size());assertEquals(7,assets.visual().mappingFrames().size());assertEquals(3,assets.visual().animations().size());
        assertEquals(List.of(14,26,38,44,66,88,94),assets.visual().mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(List.of(0,4,3,1,2),List.of(0,1,2),List.of(6,5)),assets.visual().animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        assertTrue(assets.visual().animations().stream().allMatch(a->a.terminal()==CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC));
    }
    @Test void actualMapperRegistersOnlyTheOneCompleteCountedSplashRootAndEveryUnsignedFence(){
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);var visual=terrain.objects().tunnelDoorSplash();
        for(int frame=0;frame<=255;frame++){
            int selected=frame;if(frame<7){var pieces=visual.mappingFrames().get(frame).pieces().stream().map(p->new CdObjectDrawQueue.RawPiece(p.y(),p.sizeByte(),p.rawTileWord(),p.x())).toList();assertEquals(new CdObjectDrawQueue.FrameMapping(pieces.size(),pieces),mapper.resolve(0x21D652,false,frame));}
            else assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x21D652,false,selected));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x21D652,true,selected));
        }
        for(int frame:new int[]{-1,256,Integer.MIN_VALUE,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->mapper.resolve(0x21D652,false,frame));
        for(int pointer:new int[]{0x1D652,0x121D652,0x21D63A,0x21D6B6,0x21D653})assertThrows(UnsupportedOperationException.class,()->mapper.resolve(pointer,false,0));
    }
    @Test void pairedFactoryRejectsEqualForeignPlacementAndKeepsLegacyR12aBinding(){
        var placement=placement();var before=placement.capture();var observer=new RecordingObserver();
        assertNotSame(terrain,independent);assertEquals(terrain.identity(),independent.identity());
        assertThrows(IllegalArgumentException.class,()->CdTunnelDoorSplash.fromAct2PastAssets(placement,independent,observer,c->{fail();},c->{fail();},observer));
        assertThrows(NullPointerException.class,()->CdTunnelDoorSplash.Assets.fromAct2PastAssets(null));
        var old=CdTunnelDoorSplash.Assets.fromAct2Assets(legacy.objects());assertEquals(74,old.objectTileIds().length());assertEquals(0x21D652,old.mappingsAddress());
        assertThrows(IllegalArgumentException.class,()->new CdTunnelDoorSplash.Assets(CdAssetBytes.copyOf(new byte[73]),old.visual(),old.mappingsAddress()));
        unchangedOthers(placement,before,-1);assertTrue(observer.seen.isEmpty());
    }
    @Test void originalPendingReceiptsDriveAllThreeRealInitPathsWithNativeTileZeroAndCompleteNonwrites(){
        for(int subtype=0;subtype<3;subtype++){
            var h=new Harness(subtype);var before=h.placement.capture();var incoming=h.slot();var result=h.body.run(WHOLE);byte[] expected=incoming.copyBytes();
            expected[0x24]=2;expected[1]=7;expected[0x18]=1;expected[4]=0;expected[5]=0x21;expected[6]=(byte)0xD6;expected[7]=0x52;
            expected[2]=0;expected[3]=0;expected[0x1C]=(byte)subtype;expected[0x1D]=(byte)subtype;expected[0x1B]=1;expected[0x1E]=3;expected[0x1A]=(byte)(subtype==2?6:0);
            assertArrayEquals(expected,result.state().copyBytes());assertEquals(1,h.drawn.size());assertArrayEquals(expected,h.drawn.getFirst().copyBytes());
            assertEquals(List.of(subtype<2?CdTunnelDoorSplash.FmSound.FM_A2:CdTunnelDoorSplash.FmSound.FM_A1),h.sounds);
            assertEquals(List.of("before:INIT_HEADER","prepared:INIT_HEADER","after:INIT_HEADER","completed:INIT_HEADER",
                    "before:INIT_TILE","prepared:INIT_TILE","after:INIT_TILE","completed:INIT_TILE",
                    "before:FM","prepared:FM","source:FM","after:FM","completed:FM",
                    "before:ANIMATION_RESULT","prepared:ANIMATION_RESULT","after:ANIMATION_RESULT","completed:ANIMATION_RESULT",
                    "before:DRAW","prepared:DRAW","source:DRAW","after:DRAW","completed:DRAW"),h.observer.events);
            assertEquals(List.of(new CdTunnelDoorSplash.PlayedFm(h.sounds.getFirst()),new CdTunnelDoorSplash.CallReturned(CdTunnelDoorSplash.NativeCall.INIT),new CdTunnelDoorSplash.CallReturned(CdTunnelDoorSplash.NativeCall.MAIN),new CdTunnelDoorSplash.CallReturned(CdTunnelDoorSplash.NativeCall.DRAW_OBJECT)),result.effects());
            h.observer.retired();unchangedOthers(h.placement,before,WHOLE);
        }
    }
    @Test void originalFcDrawCompletesBeforeTheFollowingSourceDeleteWithoutAllocatingOrClearingSavedFlags(){
        int[][] frames={{0,4,3,1,2},{0,1,2},{6,5}};
        for(int subtype=0;subtype<3;subtype++){
            var h=new Harness(subtype);var before=h.placement.capture();
            for(int call=0;call<frames[subtype].length*4;call++){
                var result=h.body.run(WHOLE);assertEquals(frames[subtype][call/4],result.state().unsignedByte(MAP_FRAME));assertEquals(2,result.state().routine());h.observer.retired();
            }
            var fc=h.body.run(WHOLE);assertEquals(4,fc.state().routine());assertFalse(fc.deleted());assertEquals(frames[subtype].length*4+1,h.drawn.size());
            int drawn=h.drawn.size(),sounds=h.sounds.size();var deleted=h.body.run(WHOLE);assertTrue(deleted.deleted());assertArrayEquals(new byte[64],deleted.state().copyBytes());
            assertEquals(drawn,h.drawn.size());assertEquals(sounds,h.sounds.size());assertTrue(h.observer.events.contains("completed:DELETE"));h.observer.retired();unchangedOthers(h.placement,before,WHOLE);
        }
    }
    @Test void originalFmRuntimeAndErrorFaultsRetainOnlyCommittedInitAndRetireEveryPendingReceipt(){
        for(Throwable failure:List.<Throwable>of(new IllegalStateException("original FM"),new AssertionError("original FM Error"))){
            var h=new Harness(0);h.soundFailure=failure;var before=h.placement.capture();var incoming=h.slot();
            assertSame(failure,assertThrows(Throwable.class,()->h.body.run(WHOLE)));assertEquals(2,h.slot().routine());assertEquals(0,h.slot().word(TILE));
            assertEquals(incoming.unsignedByte(PREVIOUS_ANIMATION),h.slot().unsignedByte(PREVIOUS_ANIMATION));assertTrue(h.drawn.isEmpty());
            assertFalse(h.observer.events.contains("completed:FM"));h.observer.retired();unchangedOthers(h.placement,before,WHOLE);
        }
    }
    @Test void originalDrawFaultRetainsAnimationAndOnlyItsActualSpriteBitClear(){
        var h=new Harness(0);h.drawFailure=new AssertionError("original Draw Error");var before=h.placement.capture();
        assertSame(h.drawFailure,assertThrows(AssertionError.class,()->h.body.run(WHOLE)));assertEquals(1,h.sounds.size());assertEquals(1,h.drawn.size());
        assertEquals(3,h.slot().unsignedByte(ANIMATION_TIME));assertEquals(0,h.slot().spriteFlags()&0x80);assertFalse(h.observer.events.contains("completed:DRAW"));h.observer.retired();unchangedOthers(h.placement,before,WHOLE);
    }
    @Test void afterPublicationErrorSeesOriginalCompletedStoreButFinallyRetiresItsIdentity(){
        var h=new Harness(0);var before=h.placement.capture();h.observer.failAfter=CdTunnelDoorSplash.StoreKind.INIT_TILE;
        var originalFault=new AssertionError("original after-publication Error");h.observer.afterFailure=originalFault;
        assertSame(originalFault,assertThrows(AssertionError.class,()->h.body.run(WHOLE)));assertEquals(2,h.slot().routine());assertEquals(0,h.slot().word(TILE));
        assertTrue(h.sounds.isEmpty());assertTrue(h.drawn.isEmpty());assertTrue(h.observer.events.contains("after:INIT_TILE"));assertFalse(h.observer.events.contains("completed:INIT_TILE"));h.observer.retired();unchangedOthers(h.placement,before,WHOLE);
    }
    @Test void reachedInvalidTileAndAnimationReadsKeepTheirActualOriginalPartialPrefixes(){
        var badTile=new Harness(0);badTile.placement.pool().write(badTile.slot().moveByte(SUBTYPE2,1));var tileBefore=badTile.slot();
        var tile=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->badTile.body.run(WHOLE));assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.TILE_ID_SUBTYPE2,tile.boundary());
        assertEquals(tileBefore.word(TILE),badTile.slot().word(TILE));assertEquals(2,badTile.slot().routine());assertTrue(badTile.sounds.isEmpty());assertTrue(badTile.drawn.isEmpty());badTile.observer.retired();
        var badAnimation=new Harness(3);var failure=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->badAnimation.body.run(WHOLE));
        assertEquals(CdTunnelDoorSplash.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,failure.boundary());assertEquals(6,failure.value());assertEquals(0,badAnimation.slot().word(TILE));
        assertEquals(3,badAnimation.slot().unsignedByte(PREVIOUS_ANIMATION));assertEquals(255,badAnimation.slot().unsignedByte(ANIMATION_TIME));assertTrue(badAnimation.drawn.isEmpty());badAnimation.observer.retired();
    }
    private static CdObjectPlacementRuntime placement(){return CdObjectPlacementRuntime.fromAssets(terrain,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));}
    private static void unchangedOthers(CdObjectPlacementRuntime placement,CdObjectPlacementRuntime.Snapshot before,int changed){
        var after=placement.capture();assertEquals(before.status(),after.status());assertArrayEquals(before.savedFlags().copy(),after.savedFlags().copy());
        for(var old:before.pool().slots())if(old.identity().wholeIndex()!=changed)assertArrayEquals(old.copyBytes(),placement.pool().state(old.identity().wholeIndex()).copyBytes());
    }
    private static void rethrow(Throwable failure){if(failure instanceof Error error)throw error;if(failure!=null)throw (RuntimeException)failure;}
    private static final class RecordingObserver implements CdTunnelDoorSplash.DispatchObserver,CdTunnelDoorSplash.ReceiptGuard {
        CdTunnelDoorSplash body;CdTunnelDoorSplash.StoreKind failAfter;Throwable afterFailure;
        final List<String> events=new ArrayList<>();final List<Object> seen=new ArrayList<>();
        private String kind(Object call){return call instanceof CdTunnelDoorSplash.Publication p?p.kind().name():call instanceof CdTunnelDoorSplash.FmCall?"FM":"DRAW";}
        private void check(Object call,boolean completed){
            assertNotNull(body);assertTrue(body.isCurrentCall(call));assertEquals(completed,body.isCurrentCallCompleted(call));
            Object copy=call instanceof CdTunnelDoorSplash.Publication p?new CdTunnelDoorSplash.Publication(p.ordinal(),p.kind(),p.cut(),p.source(),p.before(),p.output()):call instanceof CdTunnelDoorSplash.FmCall f?new CdTunnelDoorSplash.FmCall(f.cut(),f.source(),f.sound()):new CdTunnelDoorSplash.DrawCall(((CdTunnelDoorSplash.DrawCall)call).cut(),((CdTunnelDoorSplash.DrawCall)call).source());
            assertEquals(call,copy);assertFalse(body.isCurrentCall(copy));assertFalse(body.isCurrentCallCompleted(copy));
        }
        public void beforePublication(CdTunnelDoorSplash.Publication call){check(call,false);seen.add(call);events.add("before:"+kind(call));}
        public void afterPublication(CdTunnelDoorSplash.Publication call){check(call,true);events.add("after:"+kind(call));if(call.kind()==failAfter)rethrow(afterFailure);}
        public void beforeCall(Object call){check(call,false);seen.add(call);events.add("before:"+kind(call));}
        public void afterCall(Object call){check(call,true);events.add("after:"+kind(call));}
        public void prepared(Object call){check(call,false);events.add("prepared:"+kind(call));}
        public void completed(Object call){check(call,true);events.add("completed:"+kind(call));}
        void retired(){for(var call:seen){assertFalse(body.isCurrentCall(call));assertFalse(body.isCurrentCallCompleted(call));}assertFalse(body.isCurrentCallCompleted(null));}
    }
    private static final class Harness {
        final CdObjectPlacementRuntime placement=placement();final RecordingObserver observer=new RecordingObserver();final CdTunnelDoorSplash body;
        final List<CdObjectState> drawn=new ArrayList<>();final List<CdTunnelDoorSplash.FmSound> sounds=new ArrayList<>();Throwable soundFailure,drawFailure;
        Harness(int subtype){
            for(int whole=32;whole<128;whole++)placement.pool().write(dirty(placement.pool().state(whole)).moveByte(ID,0));
            placement.pool().write(slot().moveByte(ID,0x0B).moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SPRITE_FLAGS,0xFF).moveByte(FLAGS,3)
                    .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0xFE).moveByte(ANIMATION_FRAME,0xA9).moveByte(ANIMATION_TIME,0x38));
            body=CdTunnelDoorSplash.fromAct2PastAssets(placement,terrain,observer,call->{
                observer.check(call,false);observer.events.add("source:DRAW");assertEquals(call.source(),slot());drawn.add(call.source());
                placement.pool().write(slot().moveByte(SPRITE_FLAGS,slot().spriteFlags()&0x7F));rethrow(drawFailure);
            },call->{observer.check(call,false);observer.events.add("source:FM");sounds.add(call.sound());rethrow(soundFailure);},observer);observer.body=body;
        }
        CdObjectState slot(){return placement.pool().state(WHOLE);}
    }
    /** Complete dirty storage fixture only; no runtime raw-state API is added. */
    private static CdObjectState dirty(CdObjectState base){
        try{var owner=CdObjectState.class.getDeclaredField("owner");owner.setAccessible(true);var ctor=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);ctor.setAccessible(true);byte[] raw=new byte[64];for(int i=0;i<64;i++)raw[i]=(byte)(base.identity().wholeIndex()*13+i*7);return ctor.newInstance(owner.get(base),base.identity(),raw);}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
}
