package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.resources.CdR12bCameraArtResidency;
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
            new ObjectRoot(MAP_SPR_PLATFORM,0x20C980,0,3,CdR12bObjectAssets::platform),
            new ObjectRoot(MAP_SPR_BOULDER,0x20940A,0,1,CdR12bObjectAssets::boulder),
            new ObjectRoot(MAP_SPR_BREAKABLE_WALL,0x20EE64,0,14,CdR12bObjectAssets::breakableWall),
            new ObjectRoot(MAP_SPR_FLOAT_BLOCK,0x20A1B0,0,6,CdR12bObjectAssets::floatBlock));}

    @BeforeAll static void originalSources()throws Exception{
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){
            var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();
        }
        try(var media=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(media);independent=CdR12bTerrainAssets.fromPrograms(media);player=CdR12bPlayerAssets.fromPrograms(media);
        }
    }

    @Test void exactTwentyThreeCountedRootsBorrowCompleteOriginalViewsAndNativeIndexedOffsets()throws Exception{
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);var table=tables(mapper);var expected=new HashSet<Integer>();expected.add(SONIC);
        var o=terrain.objects();assertEquals(22,roots().size());int frames=0;
        for(var c:roots()){
            assertTrue(expected.add(c.pointer()));var actual=table.get(c.pointer());assertNotNull(actual);
            var visual=c.visual().apply(o);assertEquals(c.offset(),visual.mappingTableOffset());assertEquals(c.frames(),visual.mappingFrames().size());
            assertEquals(c.pointer()-c.offset(),actual.nativeStartBits());assertEquals(c.offset(),actual.tableOffset());assertEquals(c.frames(),actual.frameCount());
            assertSame(o.view(c.view()),actual.bytes());assertArrayEquals(visual.mappings().copy(),actual.bytes().copy());frames+=c.frames();
        }
        assertEquals(111,frames);assertEquals(expected,table.keySet());assertEquals(23,table.size());
        assertEquals(309,table.values().stream().mapToInt(CdObjectMappings.DeclaredTable::frameCount).sum());
        var sonic=table.get(SONIC);assertEquals(198,sonic.frameCount());assertEquals(0,sonic.tableOffset());
        assertArrayEquals(player.view(CdR12bPlayerAssets.Asset.MAP_SPR_SONIC).copy(),sonic.bytes().copy());
        assertEquals(8,statics(mapper).size());assertEquals(new HashSet<>(ICON_POINTERS),statics(mapper).keySet());
    }

    @Test void allOneHundredElevenObjectFrameBodiesMatchActualVisualPiecesWithEveryUnsignedFrameFence(){
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
        assertEquals(111,valid);
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


    @Test void boulderRegistersTheActualR12bCountedRootAndLiteralTwoPieceBody()throws Exception{
        var objects=terrain.objects();var bytes=objects.view(MAP_SPR_BOULDER);var visual=objects.boulder();
        assertEquals(46,CdR12bObjectAssets.View.values().length);assertEquals(39,MAP_SPR_BOULDER.ordinal());
        assertEquals(0x940A,MAP_SPR_BOULDER.range().fileOffset());assertEquals(0x20940A,MAP_SPR_BOULDER.range().nativeAddress());
        assertEquals(13,MAP_SPR_BOULDER.canonicalRange().length());assertEquals(14,bytes.length());
        assertEquals("000202f8020000ecf00f0003f400",HexFormat.of().formatHex(bytes.copy()),"Original thirteen-byte include plus real parent EVEN00");
        assertEquals("dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d",MAP_SPR_BOULDER.canonicalSha256());
        assertEquals("f1613b80dc72cd1d153d190f3a660e3990f92648d54e65c989aed93bc9189652",MAP_SPR_BOULDER.sha256());
        assertSame(visual,objects.boulder());assertEquals(1,visual.mappingFrames().size());assertTrue(visual.animations().isEmpty());assertEquals(0,visual.mappingTableOffset());
        assertEquals(2,visual.mappingFrames().getFirst().relativeOffset());assertEquals(11,visual.mappingFrames().getFirst().bytes().length());
        var expected=new CdObjectDrawQueue.FrameMapping(2,List.of(new CdObjectDrawQueue.RawPiece(-8,2,0,-20),new CdObjectDrawQueue.RawPiece(-16,15,3,-12)));
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);assertEquals(expected,mapper.resolve(0x20940A,false,0));
        var descriptor=tables(mapper).get(0x20940A);assertNotNull(descriptor);assertSame(bytes,descriptor.bytes());
        assertEquals(0x20940A,descriptor.nativeStartBits());assertEquals(0,descriptor.tableOffset());assertEquals(1,descriptor.frameCount());
        assertThrows(ReadOnlyBufferException.class,()->descriptor.bytes().buffer().put(0,(byte)0));
    }

    @Test void boulderKeepsEveryUnsignedFrameAndForeignPointerFenceWithoutStaticPieceAdmission(){
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);
        for(int frame=0;frame<=255;frame++){final int selected=frame;
            if(frame!=0)assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20940A,false,selected),"Boulder full unsigned frame"+frame);
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20940A,true,selected),"Boulder is counted, never a header-free static piece");
        }
        for(int invalid:new int[]{-1,256,Integer.MIN_VALUE,Integer.MAX_VALUE}){
            assertThrows(IllegalArgumentException.class,()->mapper.resolve(0x20940A,false,invalid));
            assertThrows(IllegalArgumentException.class,()->mapper.resolve(0x20940A,true,invalid));
        }
        // Genuine R11A Boulder data at a foreign root cannot lend its address to this R12B catalogue.
        assertEquals(0x209654,CdR11aObjectAssets.Table.BOULDER_MAPPINGS.range().nativeAddress());
        for(int foreign:new int[]{0x209654,0x209409,0x20940B,0x20940C,0x20940D,0x209417,0x209418,0x0120940A,0x7F20940A,0xFF20940A}){
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(foreign,false,0));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(foreign,true,0));
        }
    }

    @Test void resolvingBoulderMetadataDoesNotSupplyUnknownArtKnowledgeOrChangeExistingSources()throws Exception{
        var registry=terrain.sectionSources();assertEquals(19,registry.source(CdR12bSectionSources.SourceId.ART_BOULDER).tileCount());
        var placement=CdObjectPlacementRuntime.fromAssets(terrain,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));var before=placement.capture();
        var unknown=CdR12bCameraArtResidency.unknown();var mask=unknown.region(CdR12bCameraArtResidency.Region.AT_6BC0).knownMask().copy();
        var objects=terrain.objects();byte[] original=objects.view(MAP_SPR_BOULDER).copy();var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);
        assertEquals(2,mapper.resolve(0x20940A,false,0).pieces().size());
        // Native selector11 (decimal) supplies tile035E, destination6BC0. Geometry does not make these19 patterns known.
        var ids=objects.view(OBJECT_TILE_IDS);assertEquals(0x035E,ids.unsignedWord((short)ids.unsignedWord(11*2)));
        for(int tile=0;tile<19;tile++){int pattern=0x035E+tile;var stop=assertThrows(CdR12bCameraArtResidency.OwnershipBoundary.class,()->CdR12bCameraArtResidency.readTile(unknown,pattern));
            assertEquals(CdR12bCameraArtResidency.Boundary.UNKNOWN_PATTERN,stop.boundary());assertEquals(pattern,stop.nativePatternId());}
        assertArrayEquals(mask,unknown.region(CdR12bCameraArtResidency.Region.AT_6BC0).knownMask().copy());
        // Constructed knowledge control only: an explicit existing value write makes precisely its own tile readable.
        byte[] tile=new byte[32];Arrays.fill(tile,(byte)0x5A);var one=CdR12bCameraArtResidency.withTile(unknown,0x6BC0,CdAssetBytes.copyOf(tile));
        assertArrayEquals(tile,CdR12bCameraArtResidency.readTile(one,0x035E).copy());
        assertEquals(CdR12bCameraArtResidency.Boundary.UNKNOWN_PATTERN,assertThrows(CdR12bCameraArtResidency.OwnershipBoundary.class,()->CdR12bCameraArtResidency.readTile(one,0x035F)).boundary());
        assertSame(registry,terrain.sectionSources());assertArrayEquals(original,objects.view(MAP_SPR_BOULDER).copy());
        assertEquals(before.status(),placement.status());assertEquals(before.pool().slots(),placement.pool().capture().slots());assertArrayEquals(before.savedFlags().copy(),placement.capture().savedFlags().copy());
        assertEquals(8,statics(mapper).size());assertEquals(new HashSet<>(ICON_POINTERS),statics(mapper).keySet());
    }


    @Test void breakableWallRegistersAllLiteralMainAndFragmentFramesOnlyAtTheSelectedR12bRoot()throws Exception{
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);var source=terrain.objects().breakableWall();var table=tables(mapper).get(0x20EE64);
        assertNotNull(table);assertEquals(0x20EE64,MAP_SPR_BREAKABLE_WALL.range().nativeAddress());assertSame(terrain.objects().view(MAP_SPR_BREAKABLE_WALL),table.bytes());assertEquals(312,table.bytes().length());assertEquals(0,table.tableOffset());assertEquals(14,table.frameCount());
        var tiles=List.of(List.of(41,49,41,45,53,45),List.of(41,49,41,51,43,51),List.of(51,43,51,51,43,51),List.of(51,43,51,45,53,45),
                List.of(49,41,49,53,45,53),List.of(49,41,49,43,51,43),List.of(43,51,43,43,51,43),List.of(43,51,43,53,45,53));
        for(int frame=0;frame<8;frame++){var expected=new ArrayList<CdObjectDrawQueue.RawPiece>();for(int i=0;i<6;i++)expected.add(new CdObjectDrawQueue.RawPiece(new int[]{-24,-8,8}[i%3],5,tiles.get(frame).get(i),i<3?-16:0));
            assertEquals(new CdObjectDrawQueue.FrameMapping(6,expected),mapper.resolve(0x20EE64,false,frame));}
        int[] fragments={41,49,51,43,45,53};for(int i=0;i<6;i++)assertEquals(new CdObjectDrawQueue.FrameMapping(1,List.of(new CdObjectDrawQueue.RawPiece(-8,5,fragments[i],-8))),mapper.resolve(0x20EE64,false,8+i));
        for(int frame=14;frame<=255;frame++){int invalid=frame;assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20EE64,false,invalid));}
        for(int frame=0;frame<=255;frame++){int selected=frame;assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20EE64,true,selected));}
        for(int invalid:new int[]{-1,256,Integer.MIN_VALUE,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->mapper.resolve(0x20EE64,false,invalid));
        for(int foreign:new int[]{0x20F9F0,0x20EE63,0x20EE65,0x20EE80,0x20EF9C,0x20EFDC,0x20EFF4,0x0120EE64,0x7F20EE64,0xFF20EE64}){
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(foreign,false,0));assertThrows(UnsupportedOperationException.class,()->mapper.resolve(foreign,true,0));}
        // Existing ART_PLATFORM supplies57 source tiles at97C0; resolving geometry never makes current patterns known.
        assertEquals(57,terrain.sectionSources().source(CdR12bSectionSources.SourceId.ART_PLATFORM).tileCount());
        var unknown=CdR12bCameraArtResidency.unknown();var mask=unknown.region(CdR12bCameraArtResidency.Region.AT_6BC0).knownMask().copy();
        for(int offset=41;offset<57;offset++){int pattern=0x04BE+offset;var stop=assertThrows(CdR12bCameraArtResidency.OwnershipBoundary.class,()->CdR12bCameraArtResidency.readTile(unknown,pattern));
            assertEquals(CdR12bCameraArtResidency.Boundary.UNKNOWN_PATTERN,stop.boundary());assertEquals(pattern,stop.nativePatternId());}
        assertArrayEquals(mask,unknown.region(CdR12bCameraArtResidency.Region.AT_6BC0).knownMask().copy());
        assertEquals(14,source.mappingFrames().size());assertEquals(8,statics(mapper).size());
    }

    @Test void floatBlockRegistersSixLiteralFramesWithEmptyZeroAndKeepsEveryUnsignedFenceAndUnknownArt()throws Exception{
        var mapper=CdObjectMappings.fromAct2PastAssets(terrain,player);var source=terrain.objects().floatBlock();var table=tables(mapper).get(0x20A1B0);
        assertNotNull(table);assertSame(terrain.objects().view(MAP_SPR_FLOAT_BLOCK),table.bytes());assertEquals(76,table.bytes().length());assertEquals(0,table.tableOffset());assertEquals(6,table.frameCount());
        var expected=List.of(new CdObjectDrawQueue.FrameMapping(0,List.of()),
                new CdObjectDrawQueue.FrameMapping(3,List.of(new CdObjectDrawQueue.RawPiece(-12,9,0,-12),new CdObjectDrawQueue.RawPiece(4,0,0,-4),new CdObjectDrawQueue.RawPiece(4,0,0,4))),
                new CdObjectDrawQueue.FrameMapping(3,List.of(new CdObjectDrawQueue.RawPiece(-12,9,2048,-12),new CdObjectDrawQueue.RawPiece(4,0,2048,-12),new CdObjectDrawQueue.RawPiece(4,0,2048,-4))),
                new CdObjectDrawQueue.FrameMapping(2,List.of(new CdObjectDrawQueue.RawPiece(-12,9,6144,-12),new CdObjectDrawQueue.RawPiece(4,0,6144,-4))),
                new CdObjectDrawQueue.FrameMapping(2,List.of(new CdObjectDrawQueue.RawPiece(-12,9,4096,-12),new CdObjectDrawQueue.RawPiece(4,0,4096,-4))),
                new CdObjectDrawQueue.FrameMapping(1,List.of(new CdObjectDrawQueue.RawPiece(-12,10,6,-12))));
        for(int i=0;i<6;i++)assertEquals(expected.get(i),mapper.resolve(0x20A1B0,false,i));assertEquals(0,mapper.resolve(0x20A1B0,false,0).pieces().size());
        for(int frame=6;frame<=255;frame++){int invalid=frame;assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20A1B0,false,invalid));}
        for(int frame=0;frame<=255;frame++){int selected=frame;assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20A1B0,true,selected));}
        for(int invalid:new int[]{-1,256,Integer.MIN_VALUE,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->mapper.resolve(0x20A1B0,false,invalid));
        for(int foreign:new int[]{0x20A1AF,0x20A1B1,0x20A1BC,0x20A1BE,0x20A1FC,0x20A192,0x0120A1B0,0x7F20A1B0,0xFF20A1B0}){
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(foreign,false,0));assertThrows(UnsupportedOperationException.class,()->mapper.resolve(foreign,true,0));}
        var objects=terrain.objects();var ids=objects.view(OBJECT_TILE_IDS);assertEquals(0x434C,ids.unsignedWord((short)ids.unsignedWord(5*2)));
        assertEquals(15,terrain.sectionSources().source(CdR12bSectionSources.SourceId.ART_BLOCK).tileCount());
        var unknown=CdR12bCameraArtResidency.unknown();var mask=unknown.region(CdR12bCameraArtResidency.Region.AT_6600).knownMask().copy();
        for(int offset=0;offset<15;offset++){int pattern=0x034C+offset;var stop=assertThrows(CdR12bCameraArtResidency.OwnershipBoundary.class,()->CdR12bCameraArtResidency.readTile(unknown,pattern));
            assertEquals(CdR12bCameraArtResidency.Boundary.UNKNOWN_PATTERN,stop.boundary());assertEquals(pattern,stop.nativePatternId());}
        assertArrayEquals(mask,unknown.region(CdR12bCameraArtResidency.Region.AT_6600).knownMask().copy());assertEquals(3,source.animations().size());assertEquals(8,statics(mapper).size());
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
