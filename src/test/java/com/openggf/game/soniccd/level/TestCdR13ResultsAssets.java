package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Tests authored before production. Actual C/D data only; Java UNRUN, no gameplay/World/current claim. */
public class TestCdR13ResultsAssets {
 @Test void completeOriginalCAndDManifestAndObjectIndexOperandsAreIndependent()throws Exception{
  for(var a:actual()){boolean c=a.assets().identity().file()==CdNativeFile.R13C;var d=a.results();assertTrue(d.ownsAssets(a.assets()));assertSame(a.assets().identity(),d.identity());int[] offsets={c?0xDBF6:0xDC0E,c?0xDC04:0xDC1C,c?0xDEB6:0xDECE,c?0xDECE:0xDEE6,c?0xDECE:0xDEE6};int[] lengths={704,10,24,621,622};String[] hashes={c?"3655a2f8a66116033bf9decf5b9eaa3a17e72f32cbd2f0f380701e7334ec8a6e":"ef336ba081081a532e038e2fcd4bc81b48f124e521e043bf46e00a5f56341ded","89ce92572429037c4193d89a9b1e3aaa0443203a40d91732d5450d32d8b51f1f","8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8","f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621","3421ca10ca8d30d20ebc792104211ebc360321c4e6a4509abf2fa0b9a6ea73e9"};assertEquals(5,CdR13ResultsAssets.View.values().length);for(var v:CdR13ResultsAssets.View.values()){int i=v.ordinal();assertEquals(offsets[i],d.range(v).fileOffset());assertEquals(lengths[i],d.view(v).length());assertEquals(hashes[i],CdR11aProfile.sha256(d.view(v).copy()));assertSame(d.view(v),d.view(v));assertThrows(IndexOutOfBoundsException.class,()->d.view(v).unsignedByte(lengths[i]));}assertEquals(0x200000L+offsets[0],a.assets().view(CdR13FutureAssets.Asset.OBJECT_INDEX).unsignedLong(0x39*4));}
 }
 @Test void completeBonusTableIsBorrowedFromTheExactAuthenticatedCapsuleOwner()throws Exception{
  int[] expected={50000,50000,10000,5000,4000,4000,3000,3000,2000,2000,2000,2000,1000,1000,1000,1000,500,500,500,500,0};for(var a:actual()){var b=a.results().timeBonuses();assertSame(a.capsule().view(CdR13CapsuleAssets.View.TIME_BONUSES),b);assertEquals(42,b.length());for(int i=0;i<expected.length;i++)assertEquals(expected[i],b.unsignedWord(i*2));assertEquals("be40c9a172da6d18e694f0b8cfe698fa674eb88ab210958f4fa82713e0cf0861",CdR11aProfile.sha256(b.copy()));}
 }
 @Test void allFiveRoutinesAndFourSharedMappingRootsUseTheExistingSelectedDecoder()throws Exception{
  for(var a:actual()){var d=a.results();int start=d.range(CdR13ResultsAssets.View.CODE).nativeAddress();assertEquals(List.of(start+24,start+44,start+248,start+306,start+486),d.routineTargets());var maps=d.mappings();assertEquals(622,maps.length());assertEquals(0,maps.unsignedByte(621));assertEquals(4,d.mappingRoots().size());var declarations=new ArrayList<CdObjectMappings.DeclaredTable>();for(var root:d.mappingRoots()){assertSame(maps,root.source());assertEquals(5,root.frameCount());assertEquals(d.mappingAddress()+root.tableOffset(),root.address());declarations.add(new CdObjectMappings.DeclaredTable(d.mappingAddress(),maps,root.tableOffset(),5));}var decoder=CdObjectMappings.declaredSourceViews(declarations,List.of());for(var root:d.mappingRoots())for(int frame=0;frame<5;frame++){var selected=decoder.resolve(root.address(),false,frame);assertTrue(selected.pieces().size()>0);}assertThrows(UnsupportedOperationException.class,()->d.mappingRoots().clear());assertThrows(UnsupportedOperationException.class,()->d.routineTargets().clear());}
 }
 @Test void sourceViewsAreImmutableAndRemainValidAfterTheOriginalProgramSourceClosed()throws Exception{
  var d=actual().getFirst().results();var b=d.mappings();byte old=b.signedByte(0);var copy=b.copy();copy[0]^=1;assertEquals(old,b.signedByte(0));assertThrows(java.nio.ReadOnlyBufferException.class,()->b.buffer().put(0,(byte)1));assertEquals(24,d.initData().length());assertArrayEquals(HexFormat.of().parseHex("00cc0000012000000110020000f0000100cc000001200002"),d.initData().copy());
 }
 @Test void foreignAndNullCapsuleOwnersCannotRelabelAnAuthenticatedSource()throws Exception{
  var a=actual();assertThrows(IllegalArgumentException.class,()->CdR13ResultsAssets.bind(a.getFirst().assets(),a.get(1).capsule()));assertThrows(NullPointerException.class,()->CdR13ResultsAssets.bind(null,a.getFirst().capsule()));assertThrows(NullPointerException.class,()->CdR13ResultsAssets.bind(a.getFirst().assets(),null));assertFalse(a.getFirst().results().ownsAssets(null));assertFalse(a.getFirst().results().ownsAssets(a.get(1).assets()));for(var c:CdR13ResultsAssets.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(c.getModifiers()));
 }
 @Test void aggregateAndCapsuleInventoriesRemainUnchangedAndNoScriptOrArtBankIsInvented()throws Exception{
  for(var a:actual()){assertEquals(29,CdR13FutureAssets.Asset.values().length);assertEquals(12,CdR13CapsuleAssets.View.values().length);assertEquals(4,CdR13ResultsAssets.MappingRoot.values().length);assertEquals(120,CdR13SectionSources.fromAssets(a.assets()).source(CdR13SectionSources.SourceId.ART_RESULTS).tileCount());assertTrue(Arrays.stream(CdR13ResultsAssets.View.values()).noneMatch(v->v.name().contains("ART")||v.name().contains("ANIMATION")));}
 }
 public record Source(CdR13FutureAssets assets,CdR13CapsuleAssets capsule,CdR13ResultsAssets results){}
 private static List<Source> retained;
 public static synchronized List<Source> actual()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original USA media absent");Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){var list=new ArrayList<Source>();for(var stage:List.of(CdStagePayload.R13C.stage(),CdStagePayload.R13D.stage())){var a=CdR13FutureAssets.fromPrograms(programs,stage);var capsule=CdR13CapsuleAssets.bind(a);list.add(new Source(a,capsule,CdR13ResultsAssets.bind(a,capsule)));}retained=List.copyOf(list);}return retained;}
}
