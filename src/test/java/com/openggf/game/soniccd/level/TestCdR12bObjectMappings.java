package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static com.openggf.game.soniccd.level.CdR12bObjectAssets.View.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original-USA source catalogue, never a World/dispatch/current-pixel certificate.
 * Every descriptor and constructed negative below is data only. Java UNRUN.
 * N1ecc5f3: DrawObjects327-343; Monitor and Time Post/Main451-459.
 */
class TestCdR12bObjectMappings {
    private static CdR12bTerrainAssets terrain,independent;
    private static CdR12bPlayerAssets player;
    private static final int SONIC=0x230F60,MONITOR=0x20A804;
    private static final List<Integer> ICON_POINTERS=List.of(0x20A82D,0x20A843,0x20A859,0x20A86F,0x20A885,0x20A89B,0x20A8B1,0x20A8C7);
    private static final List<String> ICON_BYTES=List.of("f6050012f8","f6050016f8","f605001af8","f605001ef8",
            "f6050022f8","f6050026f8","f605002af8","f605002ef8");
    private record ObjectRoot(CdR12bObjectAssets.View view,int pointer,int offset,int frames,
                              Function<CdR12bObjectAssets,CdObjectVisualAssets> visual){}
    private static List<ObjectRoot> roots(){return List.of(
            new ObjectRoot(MAP_SPR_RING,0x209EB0,0,9,CdR12bObjectAssets::rings),
            new ObjectRoot(MAP_SPRING,0x209956,0,3,CdR12bObjectAssets::springVertical),
            new ObjectRoot(MAP_SPRING,0x20995C,6,3,CdR12bObjectAssets::springHorizontal),
            new ObjectRoot(MAP_SPRING,0x2099A6,80,3,CdR12bObjectAssets::springDiagonal),
            new ObjectRoot(MAP_SPR_HUD,0x20AB1C,0,4,CdR12bObjectAssets::hudVisuals),
            new ObjectRoot(MAP_SPR_MONITOR_TIME,MONITOR,0,20,CdR12bObjectAssets::monitorTime),
            new ObjectRoot(MAP_SPR_COLLAPSE_PLATFORM_1,0x20C2D6,0,1,CdR12bObjectAssets::collapsePlatform1),
            new ObjectRoot(MAP_SPR_COLLAPSE_PLATFORM_2,0x20C3A2,0,2,CdR12bObjectAssets::collapsePlatform2),
            new ObjectRoot(MAP_SPR_COLLAPSE_PLATFORM_3,0x20C364,0,7,CdR12bObjectAssets::collapsePlatform3),
            new ObjectRoot(MAP_SPR_COLLAPSE_PLATFORM_4,0x20C4D8,0,2,CdR12bObjectAssets::collapsePlatform4),
            new ObjectRoot(MAP_SPR_TAMABBOH,0x20B71E,0,3,CdR12bObjectAssets::tamabbohNormal),
            new ObjectRoot(MAP_SPR_TAMABBOH,0x20B734,22,3,CdR12bObjectAssets::tamabbohDamaged),
            new ObjectRoot(MAP_SPR_TAMABBOH_MISSILE,0x20B826,0,4,CdR12bObjectAssets::tamabbohMissile),
            new ObjectRoot(MAP_SPR_FLYING_ANIMAL,0x20CEE2,0,3,CdR12bObjectAssets::flyingAnimal),
            new ObjectRoot(MAP_SPR_GROUND_ANIMAL,0x20CEF6,0,5,CdR12bObjectAssets::groundAnimal),
            new ObjectRoot(MAP_SPR_SWING,0x20ECEC,0,3,CdR12bObjectAssets::swing),
            new ObjectRoot(MAP_SPR_ROBOT_GENERATOR,0x20E482,0,8,CdR12bObjectAssets::robotGenerator),
            new ObjectRoot(MAP_SPR_ANTON,0x20B160,0,4,CdR12bObjectAssets::anton),
            new ObjectRoot(MAP_SPR_PLATFORM,0x20C980,0,3,CdR12bObjectAssets::platform));}

    @BeforeAll static void originalSources()throws Exception{
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){
            var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();
        }
        try(var media=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(media);independent=CdR12bTerrainAssets.fromPrograms(media);player=CdR12bPlayerAssets.fromPrograms(media);
        }
    }

    @Test void exactTwentyCountedRootsBorrowCompleteOriginalViewsAndNativeIndexedOffsets()throws Exception{
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);var table=tables(mapper);var expected=new HashSet<Integer>();expected.add(SONIC);
        var o=terrain.objects();assertEquals(19,roots().size());int frames=0;
        for(var c:roots()){
            assertTrue(expected.add(c.pointer()));var actual=table.get(c.pointer());assertNotNull(actual);
            var visual=c.visual().apply(o);assertEquals(c.offset(),visual.mappingTableOffset());assertEquals(c.frames(),visual.mappingFrames().size());
            assertEquals(c.pointer()-c.offset(),actual.nativeStartBits());assertEquals(c.offset(),actual.tableOffset());assertEquals(c.frames(),actual.frameCount());
            assertSame(o.view(c.view()),actual.bytes());assertArrayEquals(visual.mappings().copy(),actual.bytes().copy());frames+=c.frames();
        }
        assertEquals(90,frames);assertEquals(expected,table.keySet());assertEquals(20,table.size());
        var sonic=table.get(SONIC);assertEquals(198,sonic.frameCount());assertEquals(0,sonic.tableOffset());
        assertArrayEquals(player.view(CdR12bPlayerAssets.Asset.MAP_SPR_SONIC).copy(),sonic.bytes().copy());
        assertEquals(8,statics(mapper).size());assertEquals(new HashSet<>(ICON_POINTERS),statics(mapper).keySet());
    }

    @Test void allNinetyObjectFrameBodiesMatchActualVisualPiecesWithEveryUnsignedFrameFence(){
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);var o=terrain.objects();int valid=0;
        for(var c:roots()){
            var visual=c.visual().apply(o);
            for(int frame=0;frame<=255;frame++){final int selected=frame;
                if(frame<c.frames()){
                    var pieces=visual.mappingFrames().get(frame).pieces().stream()
                            .map(p->new CdObjectDrawQueue.RawPiece(p.y(),p.sizeByte(),p.rawTileWord(),p.x())).toList();
                    assertEquals(new CdObjectDrawQueue.FrameMapping(pieces.size(),pieces),mapper.resolve(c.pointer(),false,frame));valid++;
                }else assertThrows(UnsupportedOperationException.class,()->mapper.resolve(c.pointer(),false,selected),"full unsigned frame"+frame);
            }
            assertThrows(IllegalArgumentException.class,()->mapper.resolve(c.pointer(),false,-1));
            assertThrows(IllegalArgumentException.class,()->mapper.resolve(c.pointer(),false,256));
        }
        assertEquals(90,valid);
    }

    @Test void all198SonicFramesMatchTheirExactCountedSourceBodiesAndDoNotWrapHighFrameBytes(){
        var relation=CdStagePlayerSource.fromAct2Past(terrain,player);assertTrue(relation.ownsAct2Past(terrain,player));assertEquals(SONIC,relation.mappingAddress());
        var sonic=relation.sonic();assertSame(player.sonic(),sonic);assertEquals(198,sonic.mappingFrames().size());
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);
        for(int frame=0;frame<=255;frame++){final int selected=frame;
            if(frame<198){
                var source=sonic.mappingFrames().get(frame);assertEquals(source.pieceCount(),source.bytes().unsignedByte(0));
                var expected=sourcePieces(source.bytes(),source.pieceCount());
                assertEquals(new CdObjectDrawQueue.FrameMapping(expected.size(),expected),mapper.resolve(SONIC,false,frame));
            }else assertThrows(UnsupportedOperationException.class,()->mapper.resolve(SONIC,false,selected));
        }
        for(int invalid:new int[]{-1,256,Integer.MIN_VALUE,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->mapper.resolve(SONIC,false,invalid));
    }

    @Test void springAndTamabbohRootsRetainSharedCompleteIncludesWithoutRebasingOrLosingEmptyFrames()throws Exception{
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);var t=tables(mapper);
        assertSame(t.get(0x209956).bytes(),t.get(0x20995C).bytes());assertSame(t.get(0x209956).bytes(),t.get(0x2099A6).bytes());
        assertEquals(200,t.get(0x209956).bytes().length());assertEquals(0,t.get(0x209956).bytes().unsignedByte(199),"Actual parent EVEN byte, not manufactured padding");
        assertSame(t.get(0x20B71E).bytes(),t.get(0x20B734).bytes());assertEquals(62,t.get(0x20B734).bytes().length());
        assertEquals(22,t.get(0x20B734).tableOffset());
        for(int[] empty:new int[][]{{0x209EB0,8},{0x20CEE2,2},{0x20CEF6,2},{0x20E482,7}})
            assertEquals(new CdObjectDrawQueue.FrameMapping(0,List.of()),mapper.resolve(empty[0],false,empty[1]));
        for(int pointer:new int[]{0x209957,0x20995D,0x2099A7,0x20B71F,0x20B735})
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(pointer,false,0));
    }

    @Test void eightRealMonitorIconsAreExactlyHeaderFreeFiveByteSlicesAtOriginalOddPointers(){
        var o=terrain.objects();var raw=o.view(MAP_SPR_MONITOR_TIME);var pieces=CdObjectMappings.act2PastMonitorItemStaticPieces(o);
        assertEquals(ICON_POINTERS,pieces.stream().map(CdObjectMappings.DeclaredStaticPiece::addressBits).toList());
        for(int frame=0;frame<8;frame++){
            int offset=(short)raw.unsignedWord(frame*2)+1;var piece=pieces.get(frame);
            assertEquals(0x29+frame*0x16,offset);assertEquals(MONITOR+offset,piece.addressBits());assertEquals(1,piece.addressBits()&1);
            assertEquals(4,raw.unsignedByte(offset-1));assertEquals(5,piece.bytes().length());assertArrayEquals(raw.slice(offset,5).copy(),piece.bytes().copy());
            assertEquals(ICON_BYTES.get(frame),HexFormat.of().formatHex(piece.bytes().copy()));
        }
    }

    @Test void staticModeIgnoresOnlyValidFrameBytesAndCoexistsWithCountedMonitorFrames(){
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);
        for(int icon=0;icon<8;icon++){
            var counted=assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,mapper.resolve(MONITOR,false,icon));
            var expected=new CdObjectDrawQueue.StaticMapping(counted.pieces().getFirst());int pointer=ICON_POINTERS.get(icon);
            for(int frame=0;frame<=255;frame++)assertEquals(expected,mapper.resolve(pointer,true,frame));
            assertThrows(IllegalArgumentException.class,()->mapper.resolve(pointer,true,-1));assertThrows(IllegalArgumentException.class,()->mapper.resolve(pointer,true,256));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(pointer,false,0));
        }
        assertThrows(UnsupportedOperationException.class,()->mapper.resolve(MONITOR,true,0));
    }

    @Test void raw32BitTagsCountBytesAdjacentPiecesAndUnregisteredPostIconsNeverNormalizeIntoAdmissions(){
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);
        var pointers=new ArrayList<Integer>();pointers.add(SONIC);for(var c:roots())pointers.add(c.pointer());
        for(int pointer:pointers){
            for(int tag:new int[]{0x01000000,0x7F000000,0xFF000000}){
                int raw=pointer|tag;var stop=assertThrows(UnsupportedOperationException.class,()->mapper.resolve(raw,false,0));
                assertTrue(stop.getMessage().contains(String.format(Locale.ROOT,"%08X",raw)));
            }
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(pointer+1,false,0));
        }
        // Unused Sonic alias and old Present-family roots are not selected by the B catalogue.
        for(int pointer:new int[]{0x21F200,0x20ED60,0x20A876})
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(pointer,false,0));
        for(int pointer:ICON_POINTERS){
            for(int delta:new int[]{-1,1,5,10,15})assertThrows(UnsupportedOperationException.class,()->mapper.resolve(pointer+delta,true,0));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(pointer|0xFF000000,true,0));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(pointer&~1,true,0));
        }
        var raw=terrain.objects().view(MAP_SPR_MONITOR_TIME);
        for(int frame=8;frame<20;frame++){
            int unadmitted=MONITOR+(short)raw.unsignedWord(frame*2)+1;
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(unadmitted,true,0));
        }
    }

    @Test void typedFamilyAndNullGatesUseTheActualStagePairWithoutInventingForeignOwners()throws Exception{
        assertNotNull(CdObjectMappings.class.getMethod("fromAct2PastAssets",CdR12bTerrainAssets.class,CdR12bPlayerAssets.class));
        assertNotNull(CdObjectMappings.class.getMethod("act2PastMonitorItemStaticPieces",CdR12bObjectAssets.class));
        assertNotNull(CdObjectMappings.class.getMethod("fromOpeningAssets",CdR11aAssets.class,CdR11aObjectAssets.class));
        assertNotNull(CdObjectMappings.class.getMethod("act2MonitorItemStaticPieces",CdR12aObjectAssets.class));
        assertThrows(NullPointerException.class,()->CdObjectMappings.fromAct2PastAssets(null,player));
        assertThrows(NullPointerException.class,()->CdObjectMappings.fromAct2PastAssets(terrain,null));
        assertThrows(NullPointerException.class,()->CdObjectMappings.act2PastMonitorItemStaticPieces(null));
        // Equal authentic immutable data may form another data relation; it never authenticates a Frame/World visit.
        assertNotSame(terrain,independent);assertEquals(terrain.identity(),independent.identity());
        var a=CdObjectMappings.fromAct2PastAssets(terrain,player);var b=CdObjectMappings.fromAct2PastAssets(independent,player);
        for(var c:roots())for(int frame=0;frame<c.frames();frame++)assertEquals(a.resolve(c.pointer(),false,frame),b.resolve(c.pointer(),false,frame));
    }

    @Test void borrowedSourcesAndDescriptorCollectionsStayImmutableWithoutArtOrPlacementWrites()throws Exception{
        var o=terrain.objects();var product=terrain.terrain();var plan=terrain.loadPlan();var registry=terrain.sectionSources();var sonic=player.sonic();
        var placement=CdObjectPlacementRuntime.fromAssets(terrain,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));
        var before=placement.capture();var saved=new EnumMap<CdR12bObjectAssets.View,byte[]>(CdR12bObjectAssets.View.class);
        for(var c:roots())saved.put(c.view(),o.view(c.view()).copy());
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);var statics=CdObjectMappings.act2PastMonitorItemStaticPieces(o);
        assertThrows(UnsupportedOperationException.class,()->tables(mapper).clear());assertThrows(UnsupportedOperationException.class,()->statics(mapper).clear());
        assertThrows(UnsupportedOperationException.class,statics::clear);
        for(var piece:statics){
            byte[] expected=piece.bytes().copy();byte[] changed=piece.bytes().copy();changed[0]^=1;
            assertArrayEquals(expected,piece.bytes().copy());assertThrows(ReadOnlyBufferException.class,()->piece.bytes().buffer().put(0,(byte)0));
        }
        for(var entry:saved.entrySet())assertArrayEquals(entry.getValue(),o.view(entry.getKey()).copy());
        assertSame(product,terrain.terrain());assertSame(plan,terrain.loadPlan());assertSame(registry,terrain.sectionSources());assertSame(sonic,player.sonic());
        assertEquals(before.status(),placement.status());assertEquals(before.pool().slots(),placement.pool().capture().slots());
        assertArrayEquals(before.savedFlags().copy(),placement.capture().savedFlags().copy());
    }

    private static List<CdObjectDrawQueue.RawPiece> sourcePieces(CdAssetBytes body,int count){
        var out=new ArrayList<CdObjectDrawQueue.RawPiece>();assertEquals(1+count*5,body.length());
        for(int i=0;i<count;i++){int at=1+i*5;out.add(new CdObjectDrawQueue.RawPiece(body.signedByte(at),body.unsignedByte(at+1),body.unsignedWord(at+2),body.signedByte(at+4)));}
        return List.copyOf(out);
    }
    @SuppressWarnings("unchecked")private static Map<Integer,CdObjectMappings.DeclaredTable> tables(CdObjectMappings m)throws Exception{return(Map<Integer,CdObjectMappings.DeclaredTable>)field(m,"tables");}
    @SuppressWarnings("unchecked")private static Map<Integer,CdObjectMappings.DeclaredStaticPiece> statics(CdObjectMappings m)throws Exception{return(Map<Integer,CdObjectMappings.DeclaredStaticPiece>)field(m,"staticPieces");}
    private static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
}
