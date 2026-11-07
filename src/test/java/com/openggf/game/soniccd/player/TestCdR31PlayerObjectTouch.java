package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** Tests first. Constructed COMPLETE pure component cuts over actual original R31A data; no Body/Frame/World admission. Java UNRUN. */
class TestCdR31PlayerObjectTouch {
    @Test void exactOriginalAssetsBindBothProductsWithoutEqualOwnerConfusion() throws Exception {
        var pair=assets();var a=pair.first();var b=pair.second();assertNotSame(a,b);assertEquals(a.identity(),b.identity());
        assertEquals(CdNativeFile.R31A,a.identity().file());assertEquals(0x20754A,a.range(CdR31aAssets.Asset.TOUCH_DIMENSIONS).nativeAddress());
        assertEquals(126,a.view(CdR31aAssets.Asset.TOUCH_DIMENSIONS).length());assertEquals("5337f6beae28fe2b8458277610f92d358020a78e947d68b09470b38829e46575",hash(a.view(CdR31aAssets.Asset.TOUCH_DIMENSIONS).copy()));
        assertEquals(0x207340,a.range(CdR31aAssets.Asset.ENEMY_POINTS).nativeAddress());assertEquals("75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5",hash(a.view(CdR31aAssets.Asset.ENEMY_POINTS).copy()));
        var touch=CdPlayerObjectTouch.fromR31aAssets(a);assertTrue(touch.ownsR31aAssets(a));assertFalse(touch.ownsR31aAssets(b));assertFalse(touch.ownsR31aAssets(null));
        assertFalse(new CdPlayerObjectTouch(sizes(a)).ownsR31aAssets(a));assertThrows(NullPointerException.class,()->CdPlayerObjectTouch.fromR31aAssets(null));
        assertThrows(NullPointerException.class,()->CdPlayerTouchResponse.fromR31aAssets(null));
    }
    @Test void rawBit6ReturnsBeforeLookupWhileEveryOtherRawControlBitStaysDirect() throws Exception {
        var scanner=scanner();var pool=new CdObjectPool();pool.write(object(pool,32,0,0x80,0x40,0x100,0x200));var cut=pool.capture();
        for(int raw=0;raw<256;raw++) {
            int suppliedRaw=raw;
            if((raw&0x40)!=0)assertSame(CdPlayerObjectTouch.NoTouch.INSTANCE,scanner.scan(player(0x100,0x200,0x13,0,0),raw,cut));
            else assertThrows(IllegalArgumentException.class,()->scanner.scan(player(0x100,0x200,0x13,0,0),suppliedRaw,cut));
        }
        assertEquals(cut.slots(),pool.capture().slots());
        assertEquals(CdPlayerObjectTouch.UnsupportedBoundary.R3_CONTROL_2C_OWNER,assertThrows(CdPlayerObjectTouch.UnsupportedSelection.class,
                ()->scanner.scan(player(0x100,0x200,0x13,0,0),cut)).boundary());
        assertThrows(IllegalArgumentException.class,()->scanner.scan(player(0x100,0x200,0x13,0,0),256,cut));
        var ppz=new CdPlayerObjectTouch(sizes(assets().first()));assertThrows(IllegalArgumentException.class,()->ppz.scan(player(0x100,0x200,0x13,0,0),0,cut));
    }
    @Test void spriteSignZeroTypeAndDirtyIdZeroPreserveFirstPhysicalSelectionAndAll96Inputs() throws Exception {
        var pool=new CdObjectPool();pool.write(object(pool,32,0x19,0x7F,0x40,0x100,0x200));pool.write(object(pool,33,1,0xFF,0,0x100,0x200));
        var first=object(pool,34,0,0x80,0x47,0x100,0x200).moveByte(ROUTINE,0xFE).moveByte(SAVED_FLAGS_ID,0xA5);pool.write(first);
        pool.write(object(pool,35,0x19,0xFF,6,0x100,0x200));var cut=pool.capture();var r=scanner().scan(player(0x100,0x200,0x13,0,0),0x80,cut);
        assertEquals(CdPlayerObjectTouch.Kind.ITEM,r.kind());assertSame(first,r.selectedSlot().orElseThrow());assertEquals(0,r.changedSlot().orElseThrow().routine());
        byte[] expected=first.copyBytes();expected[0x24]=0;assertArrayEquals(expected,r.changedSlot().orElseThrow().copyBytes());assertEquals(cut.slots(),pool.capture().slots());
    }
    @Test void physicalWalkIncludesEveryOneOf96SlotsAndDoesNotSortOrUseSavedIds() throws Exception {
        var scanner=scanner();for(int whole=32;whole<128;whole++) {
            var pool=new CdObjectPool();for(int at=32;at<whole;at++)pool.write(object(pool,at,0x10,0x80,0x47,0x4000,0x200));
            var chosen=object(pool,whole,0,0x80,0x47,0x100,0x200).moveByte(SAVED_FLAGS_ID,127-whole);pool.write(chosen);var cut=pool.capture();
            var r=scanner.scan(player(0x100,0x200,0x13,0,0),0,cut);assertSame(chosen,r.selectedSlot().orElseThrow());assertEquals(whole,r.changedSlot().orElseThrow().identity().wholeIndex());
            assertEquals(96,cut.slots().size());assertEquals(cut.slots(),pool.capture().slots());
        }
    }
    @Test void literalNativeBoxEdgesFrame39AndByteRadiusUnderflowUseActualSixBySixPair() throws Exception {
        var sizes=sizes(assets().first());assertEquals(new CdR11aObjectAssets.TouchSize(6,6),sizes.get(6));
        for(int[] v:new int[][]{{0xF1,0x200,0},{0xF2,0x200,1},{0x10E,0x200,1},{0x10F,0x200,0},{0x100,0x1E9,0},{0x100,0x1EA,1},{0x100,0x216,1},{0x100,0x217,0}})
            edge(player(0x100,0x200,0x13,0,0),v[0],v[1],v[2]!=0);
        edge(player(0x100,0x200,0x13,0x39,0),0x100,0x1F5,false);edge(player(0x100,0x200,0x13,0x39,0),0x100,0x1F6,true);
        edge(player(0x100,0x200,0,0,0),0x100,0xFC,false);edge(player(0x100,0x200,0,0,0),0x100,0xFD,true);
        edge(player(0x100,0x200,0,0,0),0x100,0x303,true);edge(player(0x100,0x200,0,0,0),0x100,0x304,false);
    }
    @Test void unsignedHalfWidth80KeepsNativeWordWrapAsymmetryAtLiteralEdges() throws Exception {
        assertEquals(new CdR11aObjectAssets.TouchSize(0x80,0x20),sizes(assets().first()).get(0x14));var scanner=scanner();
        for(int[] v:new int[][]{{0x7F,0},{0x80,1},{0x188,1},{0x189,0}}) {
            var pool=new CdObjectPool();pool.write(object(pool,32,0x19,0x80,0x55,v[0],0x200));var cut=pool.capture();
            assertEquals(v[1]!=0,scanner.scan(player(0x100,0x200,0x13,0,0),0,cut).kind()==CdPlayerObjectTouch.Kind.ITEM);
            assertEquals(cut.slots(),pool.capture().slots());
        }
    }
    @Test void nativeTypeGroupsNotObjectIdsChooseHandlersAndHurt90StillEndsItemScan() throws Exception {
        var scanner=scanner();int[] types={7,0x47,0x46,0x87,0xD7};var kinds=List.of(CdPlayerObjectTouch.Kind.ENEMY,CdPlayerObjectTouch.Kind.ITEM,CdPlayerObjectTouch.Kind.MONITOR,CdPlayerObjectTouch.Kind.HAZARD,CdPlayerObjectTouch.Kind.SPECIAL);
        for(int n=0;n<types.length;n++) {
            var pool=new CdObjectPool();var first=object(pool,32,n==2?0x10:0x19,0x80,types[n],0x100,0x200);pool.write(first);pool.write(object(pool,33,0x10,0x80,0x47,0x100,0x200));var cut=pool.capture();
            var r=scanner.scan(player(0x100,0x200,0x13,0,90),0,cut);assertEquals(kinds.get(n),r.kind());assertSame(first,r.selectedSlot().orElseThrow());assertTrue(r.changedSlot().isEmpty());assertEquals(cut.slots(),pool.capture().slots());
        }
    }
    @Test void selectedZeroSelectorRemainsAnExplicitFiniteTableFailureBeforeGeometry() throws Exception {
        var scanner=scanner();for(int type:new int[]{0x40,0x80,0xC0}) {
            var pool=new CdObjectPool();pool.write(object(pool,32,0,0x80,type,0x4000,0x4000));var cut=pool.capture();
            assertThrows(IllegalArgumentException.class,()->scanner.scan(player(0x100,0x200,0x13,0,0),0,cut));
            assertSame(CdPlayerObjectTouch.NoTouch.INSTANCE,scanner.scan(player(0x100,0x200,0x13,0,0),0x40,cut));assertEquals(cut.slots(),pool.capture().slots());
        }
    }
    @Test void clearGateGeometryMatchesUnchangedPureLegacyKernelOnTheSameRealTable() throws Exception {
        var a=assets().first();var r3=CdPlayerObjectTouch.fromR31aAssets(a);var control=new CdPlayerObjectTouch(sizes(a));
        // Same raw format data is a constructed legacy arithmetic control, not authenticated PPZ admission.
        for(int type=1;type<256;type++) {
            if((type&0x3F)==0)continue;var pool=new CdObjectPool();pool.write(object(pool,32,0,0x80,type,0x100,0x200));var cut=pool.capture();
            for(var p:List.of(player(0x100,0x200,0x13,0,0),player(0xFFFF,0xFFFF,0,0x39,0xFFFF))) {
                var actual=r3.scan(p,0,cut);var expected=control.scan(p,cut);assertEquals(expected.kind(),actual.kind());assertEquals(expected.selectedSlot(),actual.selectedSlot());assertEquals(expected.changedSlot(),actual.changedSlot());
            }
            assertEquals(cut.slots(),pool.capture().slots());
        }
    }
    private static void edge(CdPlayerObjectTouch.PlayerCut p,int x,int y,boolean hit) throws Exception {
        var pool=new CdObjectPool();pool.write(object(pool,32,0x10,0x80,0x47,x,y));assertEquals(hit,scanner().scan(p,0,pool.capture()).kind()!=CdPlayerObjectTouch.Kind.NONE,"native object centre "+x+"/"+y);
    }
    static CdPlayerObjectTouch scanner() throws Exception{return CdPlayerObjectTouch.fromR31aAssets(assets().first());}
    static CdPlayerObjectTouch.PlayerCut player(int x,int y,int radius,int frame,int hurt){return new CdPlayerObjectTouch.PlayerCut(x,y,radius,frame,hurt);}
    static CdObjectState object(CdObjectPool pool,int whole,int id,int sprite,int type,int x,int y){return pool.state(whole).moveByte(ID,id).moveByte(SPRITE_FLAGS,sprite).moveByte(COLLISION_TYPE,type).moveWord(X,x).moveWord(Y,y);}
    static List<CdR11aObjectAssets.TouchSize> sizes(CdR31aAssets a){return CdR11aObjectAssets.decodeTouchDimensions(a.view(CdR31aAssets.Asset.TOUCH_DIMENSIONS).copy());}
    record Pair(CdR31aAssets first,CdR31aAssets second) { }
    private static Pair cached;private static String cachedRoot;
    static synchronized Pair assets() throws Exception {
        String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional configured original USA CD source absent");
        if(cached!=null&&root.equals(cachedRoot))return cached;Path usa=Path.of(root).resolve("USA");assertTrue(Files.isDirectory(usa),"Configured original USA source must exist");
        List<Path> cues;try(var files=Files.list(usa)){cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();}assertEquals(1,cues.size());
        try(var programs=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            cached=new Pair(CdR31aAssets.fromPrograms(programs,CdStagePayload.R31A.stage()),CdR31aAssets.fromPrograms(programs,CdStagePayload.R31A.stage()));
        }
        cachedRoot=root;return cached;
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
