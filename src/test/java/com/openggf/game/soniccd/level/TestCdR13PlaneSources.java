package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.scroll.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Actual original C/D finite plane tables; component values only, not World/pixels. Java UNRUN. */
public class TestCdR13PlaneSources {
    @Test void bothActualLeaRootsAndEntireSectionAndPointerTablesAreIndependent()throws Exception{
        for(var a:actual()){boolean c=a.identity().file()==CdNativeFile.R13C;var p=CdR13PlaneSources.fromAssets(a);assertTrue(p.ownsAssets(a));assertSame(a.identity(),p.identity());assertSame(a.terrain(),p.terrain());assertEquals(c?0x32F0:0x3308,p.idsRange().fileOffset());assertEquals(34,p.ids().length());assertEquals(c?0x3312:0x332A,p.pointersRange().fileOffset());assertEquals(16,p.pointers().length());assertEquals(c?"7f301c96bbf02cd63b073201917d0c385b4f2cf75755e87fb21f433339a9ae1d":"a4b4ed50ce9d8a9e322e7059a440abb004c95c343caab15c1e62f9d076bcca35",hash(p.ids()));assertEquals("2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1",hash(p.pointers()));assertSame(p.ids(),p.ids());assertSame(p.pointers(),p.pointers());}
    }
    @Test void actualGenericBackgroundWriterUsesEveryRetainedSourceRowWithoutDefaultTiles()throws Exception{
        for(var a:actual()){var p=CdR13PlaneSources.fromAssets(a);var plane=CdR11aBackgroundPlane.bindAct3Future(p);var before=new CdR11aBackgroundPlane.State(fill(4096,0xA5));var cam=new CdR11aBackgroundPlane.Camera(0,0x006C6000);var live=new CdR11aBackgroundPlane.LiveCameras(cam,cam,cam);var flags=new CdR11aBackgroundPlane.FlagWords(0x1234,0x2345,0x3456);var result=plane.initialize(before,live,flags);assertEquals(16,result.sections().size());assertSame(flags,result.flags());assertArrayEquals(fill(4096,0xA5).copy(),before.nameTable().copy());for(var row:result.sections()){assertEquals(1+(((0x6C+row.relativeY())&0x1F0)>>>4),row.tableIndex());assertEquals(p.ids().unsignedByte(row.tableIndex()),row.selector());}for(var block:result.blocks())assertTrue(block.blockByteOffset()>=0&&block.blockByteOffset()<=a.terrain().blocks().length()-8);assertFalse(Arrays.equals(before.nameTable().copy(),result.state().nameTable().copy()));}
    }
    @Test void noIncrementalFlagsReturnsTheExactExistingPhysicalPlaneWithUnknownUnusedWords()throws Exception{
        for(var a:actual()){var p=CdR13PlaneSources.fromAssets(a);var writer=CdR11aBackgroundPlane.bindAct3Future(p);var before=new CdR11aBackgroundPlane.State(fill(4096,0xA5));var cam=new CdR11aBackgroundPlane.Camera(0,0);var live=new CdR11aBackgroundPlane.LiveCameras(cam,cam,cam);var copied=new CdR11aBackgroundPlane.CopiedCameras(cam,cam,cam);var out=writer.incremental(before,live,copied,new CdR11aBackgroundPlane.FlagWords(0x00AA,0x1234,0x5678));assertSame(before,out.state());assertEquals(0x00AA,out.flags().bg1());assertEquals(0x1234,out.flags().bg2());assertEquals(0x5678,out.flags().bg3());assertTrue(out.blocks().isEmpty());}
    }
    @Test void exactOwnerAndImmutableViewsDoNotCreateRawOrEqualRecordAdmission()throws Exception{
        var a=actual().getFirst();var p=CdR13PlaneSources.fromAssets(a);assertFalse(p.ownsAssets(null));assertFalse(p.ownsAssets(actual().get(1)));byte[] b=p.ids().copy();b[0]=99;assertEquals(0,p.ids().unsignedByte(0));assertThrows(java.nio.ReadOnlyBufferException.class,()->p.ids().buffer().put(0,(byte)1));assertThrows(NullPointerException.class,()->CdR13PlaneSources.fromAssets(null));assertThrows(NullPointerException.class,()->CdR11aBackgroundPlane.bindAct3Future(null));for(var ctor:CdR13PlaneSources.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
    }
    private static List<CdR13FutureAssets> retained;
    public static synchronized List<CdR13FutureAssets> actual()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original USA CD media is absent");Path cue;try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=List.of(CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()));}return retained;}
    public static CdAssetBytes fill(int length,int value){byte[] b=new byte[length];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
    private static String hash(CdAssetBytes b){return CdR11aProfile.sha256(b.copy());}
}
