package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Tests FIRST; Java UNRUN. Original configured C/D media; no art decode or runtime owner. */
class TestCdR13ReusedObjectAssets {
    private static final String MANIFEST="""
ANI_TAMABBOH_NORMAL 9c94 9cac 12 ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474
ANI_TAMABBOH_DAMAGED 9ca0 9cb8 26 09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84
MAP_SPR_TAMABBOH 9cba 9cd2 62 e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847
ANI_TAMABBOH_MISSILE 9dba 9dd2 8 764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5
MAP_SPR_TAMABBOH_MISSILE 9dc2 9dda 42 10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb
ANI_EXPLOSION 3c5dc 3c7da 20 9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649
MAP_SPR_EXPLOSION 3c5f0 3c7ee 146 0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44
ANI_FLOWER 336fa 336fa 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
MAP_SPR_FLOWER 33716 33716 90 48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41
MAP_SPR_POINTS 8fb6 8fce 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
MAP_SPR_POWERUP 3c404 3c602 274 d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc
        """;
    @Test void allElevenActualLiveRangesHashesAndCachesMatchOriginalImages()throws Exception {
        var f=TestCdR13FutureAssets.retail();assertEquals(11,CdR13ReusedObjectAssets.View.values().length);
        for(var a:f.all()){var o=CdR13ObjectAssets.fromAssets(a);var r=CdR13ReusedObjectAssets.fromAssets(a,o);assertTrue(r.ownsAssets(a,o));
            for(var row:MANIFEST.strip().split("\n")){var cells=row.strip().split(" ");var v=CdR13ReusedObjectAssets.View.valueOf(cells[0]);var range=v.range(a.identity().file());assertEquals(Integer.parseInt(cells[a.identity().file()==CdNativeFile.R13C?1:2],16),range.fileOffset());assertEquals(Integer.parseInt(cells[3]),range.length());assertEquals(cells[4],v.sha256(a.identity().file()));assertEquals(cells[4],TestCdR13FutureAssets.hash(r.view(v).copy()));assertArrayEquals(f.image(a.identity().file()).readFile(range.fileOffset(),range.length()),r.view(v).copy());assertSame(r.view(v),r.view(v));}
            assertEquals(27,CdR13ObjectAssets.View.values().length);assertEquals(29,CdR13FutureAssets.Asset.values().length);assertEquals(19,CdR13SectionSources.SourceId.values().length);}
    }
    @Test void actualInitAndLeaOperandsSelectLiveCopiesRatherThanEqualDormantTables()throws Exception {
        var f=TestCdR13FutureAssets.retail();for(var a:f.all()){var image=f.image(a.identity().file());boolean c=a.identity().file()==CdNativeFile.R13C;int d=c?0:24;
            int[][] abs={{40242+d,0x9DC2+d},{24042+d,c?0x3C5F0:0x3C7EE},{24196+d,0x33716},{36746+d,0x8FB6+d},{25430+d,c?0x3C404:0x3C602}};
            for(var v:abs){assertEquals(0x217C,image.readFile(v[0],2)[0]<<8&0xFF00|Byte.toUnsignedInt(image.readFile(v[0]+1,1)[0]));assertEquals(0x200000L+v[1],CdAssetBytes.copyOf(image.readFile(v[0]+2,4)).unsignedLong(0));}
            int[][] pc={{39724+d,0x9CBA+d},{39728+d,0x9C94+d},{39740+d,0x9CD0+d},{39744+d,0x9CA0+d},{40368+d,0x9DBA+d}};
            for(var v:pc)assertEquals(v[1],v[0]+2+(short)CdAssetBytes.copyOf(image.readFile(v[0]+2,2)).unsignedWord(0));
            assertEquals(0x7004,CdAssetBytes.copyOf(image.readFile(39710+d,2)).unsignedWord(0));
            int[][] script={{24088+d,c?0x3C5DC:0x3C7DA},{24332+d,0x336FA}};for(var v:script){assertEquals(0x43F9,CdAssetBytes.copyOf(image.readFile(v[0],2)).unsignedWord(0));assertEquals(0x200000L+v[1],CdAssetBytes.copyOf(image.readFile(v[0]+2,4)).unsignedLong(0));}
            assertEquals(0x237C,CdAssetBytes.copyOf(image.readFile(0x84D2+d,2)).unsignedWord(0));assertEquals(0x20860EL+d,CdAssetBytes.copyOf(image.readFile(0x84D4+d,4)).unsignedLong(0));
            assertEquals(0xA7AE,CdAssetBytes.copyOf(image.readFile(0x84E2+d,2)).unsignedWord(0));
            assertEquals(0x227C,CdAssetBytes.copyOf(image.readFile(0x8B80+d,2)).unsignedWord(0));assertEquals(0x208DA0L+d,CdAssetBytes.copyOf(image.readFile(0x8B82+d,4)).unsignedLong(0));
            assertEquals(0x85A8,CdAssetBytes.copyOf(image.readFile(0x8B4E+d,2)).unsignedWord(0));
            var idx=a.view(CdR13FutureAssets.Asset.OBJECT_INDEX);int[] ids={0x11,0x18,0x1A,0x1C,0x1F,0x22,0x34};int[] ptrs={2131062,2121140,2132784,2134024,2121282,2136758,2122528};for(int n=0;n<ids.length;n++)assertEquals(ptrs[n]+d,idx.unsignedLong((ids[n]-1)*4));}
    }
    @Test void visualCountsRootsAndExistingSourceBoundsAreExact()throws Exception {for(var a:TestCdR13FutureAssets.retail().all()){var r=CdR13ReusedObjectAssets.fromAssets(a,CdR13ObjectAssets.fromAssets(a));var list=List.of(r.tamabbohNormal(),r.tamabbohDamaged(),r.tamabbohMissile(),r.explosion(),r.flower(),r.points());int[] frames={3,3,4,7,8,6},scripts={2,2,1,2,4,0},bounds={59,59,59,70,39,5};for(int i=0;i<6;i++){var v=list.get(i);assertEquals(frames[i],v.mappingFrames().size());assertEquals(scripts[i],v.animations().size());assertSame(v,List.of(r.tamabbohNormal(),r.tamabbohDamaged(),r.tamabbohMissile(),r.explosion(),r.flower(),r.points()).get(i));for(var frame:v.mappingFrames())for(var piece:frame.pieces())assertTrue(piece.tileOffset()+piece.tileCount()<=bounds[i]);}assertEquals(22,r.tamabbohDamaged().mappingTableOffset());assertEquals(274,r.view(CdR13ReusedObjectAssets.View.MAP_SPR_POWERUP).length());}}
    @Test void additiveMapperRetainsOldTenRootsEightStaticAndAddsSevenActualRoots()throws Exception {for(var a:TestCdR13FutureAssets.retail().all()){var o=CdR13ObjectAssets.fromAssets(a);var r=CdR13ReusedObjectAssets.fromAssets(a,o);var old=CdObjectMappings.fromAct3FutureAssets(a,o);var next=CdObjectMappings.fromAct3FutureAssets(a,o,r);assertEquals(10,count(old,"tables"));assertEquals(8,count(old,"staticPieces"));assertEquals(17,count(next,"tables"));assertEquals(8,count(next,"staticPieces"));var visuals=List.of(r.tamabbohNormal(),r.tamabbohDamaged(),r.tamabbohMissile(),r.explosion(),r.flower(),r.points());var views=List.of(CdR13ReusedObjectAssets.View.MAP_SPR_TAMABBOH,CdR13ReusedObjectAssets.View.MAP_SPR_TAMABBOH,CdR13ReusedObjectAssets.View.MAP_SPR_TAMABBOH_MISSILE,CdR13ReusedObjectAssets.View.MAP_SPR_EXPLOSION,CdR13ReusedObjectAssets.View.MAP_SPR_FLOWER,CdR13ReusedObjectAssets.View.MAP_SPR_POINTS);for(int i=0;i<6;i++){var visual=visuals.get(i);int start=views.get(i).range(a.identity().file()).nativeAddress();var declared=CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(start,r.view(views.get(i)),visual.mappingTableOffset(),visual.mappingFrames().size())),List.of());int pointer=start+visual.mappingTableOffset();for(int frame=0;frame<visual.mappingFrames().size();frame++)assertEquals(declared.resolve(pointer,false,frame),next.resolve(pointer,false,frame));assertThrows(UnsupportedOperationException.class,()->next.resolve(pointer,false,visual.mappingFrames().size()));}var raw=r.view(CdR13ReusedObjectAssets.View.MAP_SPR_POWERUP);int root=CdR13ReusedObjectAssets.View.MAP_SPR_POWERUP.range(a.identity().file()).nativeAddress();for(int frame=0;frame<13;frame++){var expected=CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(root,raw,0,13)),List.of()).resolve(root,false,frame);assertEquals(expected,next.resolve(root,false,frame));}assertThrows(UnsupportedOperationException.class,()->old.resolve(root,false,1));}}
    @Test void equalForeignOwnerDoesNotBindAndRawValuesCannotMintAuthenticImage()throws Exception {var f=TestCdR13FutureAssets.retail();var a=f.good();var o=CdR13ObjectAssets.fromAssets(a);assertThrows(IllegalArgumentException.class,()->CdR13ReusedObjectAssets.fromAssets(f.bad(),o));var fake=mock(CdR13FutureAssets.class);when(fake.identity()).thenReturn(a.identity());assertThrows(IllegalArgumentException.class,()->CdR13ReusedObjectAssets.fromAssets(fake,o));assertThrows(NullPointerException.class,()->CdR13ReusedObjectAssets.fromAssets(null,o));assertThrows(NullPointerException.class,()->CdR13ReusedObjectAssets.fromAssets(a,null));}
    @Test void bytesVisualListsAndBoundsStayImmutableAndStrict()throws Exception {for(var a:TestCdR13FutureAssets.retail().all()){var r=CdR13ReusedObjectAssets.fromAssets(a,CdR13ObjectAssets.fromAssets(a));for(var v:CdR13ReusedObjectAssets.View.values()){byte[] before=r.view(v).copy(),b=r.view(v).copy();b[0]^=1;assertArrayEquals(before,r.view(v).copy());assertThrows(IndexOutOfBoundsException.class,()->r.view(v).unsignedByte(v.range(a.identity().file()).length()));assertThrows(IllegalArgumentException.class,()->v.range(CdNativeFile.R12B));}assertThrows(UnsupportedOperationException.class,()->r.flower().mappingFrames().clear());assertThrows(NullPointerException.class,()->r.view(null));}}
    @Test void metadataAndFactoriesExposeOnlyExplicitFiniteTypedSurfaces()throws Exception {
        assertEquals(List.of("explosion/0","flower/0","fromAssets/2","identity/0","ownsAssets/2","points/0","startup/0","tamabbohDamaged/0","tamabbohMissile/0","tamabbohNormal/0","view/1"),Arrays.stream(CdR13ReusedObjectAssets.class.getDeclaredMethods()).filter(m->java.lang.reflect.Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());assertEquals(0,CdR13ReusedObjectAssets.class.getConstructors().length);
        Class<?>[] types={CdTamabboh.class,CdSpinTunnelTag.class,CdExplosion.class,CdPoints.class,CdFlower.class,CdLostRing.class,CdMonitorItem.class};int[] arity={7,3,6,6,8,9,9};for(int n=0;n<types.length;n++){var found=Arrays.stream(types[n].getDeclaredMethods()).filter(m->m.getName().equals("fromAct3FutureAssets")).toList();assertEquals(1,found.size());assertEquals(arity[n],found.getFirst().getParameterCount());}assertEquals(6,CdTamabboh.Reads.class.getDeclaredMethods().length);
    }
    private static int count(CdObjectMappings value,String name)throws Exception{var field=CdObjectMappings.class.getDeclaredField(name);field.setAccessible(true);return ((Map<?,?>)field.get(value)).size();}
}
