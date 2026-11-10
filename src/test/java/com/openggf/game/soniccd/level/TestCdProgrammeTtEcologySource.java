package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Requirements before production. Exact media DATA only; no fabricated World invocation. */
class TestCdProgrammeTtEcologySource {
 private static CdProgrammeData programme;private static CdProgrammeObjectSource source;private static CdMmdImage image;
 private record Expected(int offset,int bytes,String sha){}
 private static final Map<CdProgrammeObjectSource.Table,Expected> EXPECTED=Map.ofEntries(
   Map.entry(CdProgrammeObjectSource.Table.SPIKES_MAPPINGS,new Expected(0xC1DA,16,"88bdf56813b465f06d5909b3026a7eb1cfbeabcd1bb5c9bb76eead7065040107")),
   Map.entry(CdProgrammeObjectSource.Table.TONBO_MAPPINGS1,new Expected(0xD5D4,189,"e8dcd7b95175ec8ff31d6c3bac5a3afd83709b38a26414d5b3e952fdb4b48ab8")),
   Map.entry(CdProgrammeObjectSource.Table.TONBO_MAPPINGS2,new Expected(0xD692,174,"831c3ac32faf8d04a7c49ac51ab36476c16deefe31506224bcda2646f59f6836")),
   Map.entry(CdProgrammeObjectSource.Table.TONBO_ANIMATION,new Expected(0xD5C8,12,"402e2dd679fcfbfaa23046e9f4b789eab0a22722ff2ac7b059379e4b693c7578")),
   Map.entry(CdProgrammeObjectSource.Table.ANIMAL_MAPPINGS1,new Expected(0xE56E,39,"7cdabdab21ec32c5aa5373a1e0e6b71ffe1c0c4fb24997e0c78a45f704f52553")),
   Map.entry(CdProgrammeObjectSource.Table.ANIMAL_MAPPINGS2,new Expected(0xE596,39,"61399d0f394c2e0c64137e14638640681b0c9c1af333328a4705f7185957502d")),
   Map.entry(CdProgrammeObjectSource.Table.ANIMAL_ANIMATION,new Expected(0xE548,38,"5bf35fa79c141fcec25ad20726e8e1cd18941eb9255d74f6be806857995b1762")),
   Map.entry(CdProgrammeObjectSource.Table.ANIMAL_TILES,new Expected(0xE5BE,22,"264f607eed187439efa83a84217f7fdcf98d4898418b2346c61f2dd1390776e1")));
 @BeforeAll static void actual()throws Exception{
  String root=System.getProperty("soniccd.media.root","");Path cue=Path.of(root,"USA","Sonic CD (US).cue");assumeTrue(!root.isBlank()&&Files.isRegularFile(cue),"Actual original USA source");
  try(var actual=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
   assertEquals(CdR4DataLayout.USA_DISC_SHA256,actual.identity().normalizedSha256());programme=CdProgrammeData.open(actual,CdR4DataLayout.Programme.R41A);image=actual.mmd(CdNativeFile.R41A);
  }source=programme.objectSource();
 }
 @Test void everyEcologyViewBorrowsItsExactOriginalIncludeAndReaderCache(){
  assertEquals(8,EXPECTED.size());for(var entry:EXPECTED.entrySet()){
   var table=entry.getKey();var e=entry.getValue();var v=source.view(table);assertSame(v,source.view(table));assertEquals(0x200000+e.offset(),v.nativeAddress());assertEquals(e.bytes(),v.bytes().length());assertEquals(e.sha(),CdR11aProfile.sha256(v.bytes().copy()));assertArrayEquals(image.readFile(e.offset(),e.bytes()),v.bytes().copy());
  }
 }
 @Test void countedEcologyMappingRootsAdmitTheirLastFramesAndRejectTheNext(){
  for(var table:List.of(CdProgrammeObjectSource.Table.SPIKES_MAPPINGS,CdProgrammeObjectSource.Table.TONBO_MAPPINGS1,CdProgrammeObjectSource.Table.TONBO_MAPPINGS2,CdProgrammeObjectSource.Table.ANIMAL_MAPPINGS1,CdProgrammeObjectSource.Table.ANIMAL_MAPPINGS2)){
   int count=table==CdProgrammeObjectSource.Table.SPIKES_MAPPINGS?2:3;int address=source.view(table).nativeAddress();
   assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,source.mappings().resolve(address,false,count-1));assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(address,false,count));
  }
  assertSame(source.mappings(),source.mappings());assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(0x23F05C,false,0));
 }
 @Test void sourceSelectorsKeepActualAnimationsAndNeverBorrowAnUnownedFamily(){
  assertEquals(source.view(CdProgrammeObjectSource.Table.TONBO_MAPPINGS1).nativeAddress(),source.ecologyPointer(CdR4ObjectPorts.Data.TONBO_MAP1));
  assertEquals(source.view(CdProgrammeObjectSource.Table.SPIKES_MAPPINGS).nativeAddress(),source.ecologyPointer(CdR4ObjectPorts.Data.SPIKES_MAP));
  for(var field:List.of(CdR4ObjectPorts.Data.TONBO_ANIM,CdR4ObjectPorts.Data.ANIMAL_ANIM)){
   var a=source.ecologyAnimation(field);assertSame(a,source.ecologyAnimation(field));assertEquals(2,a.scriptCount());
  }
  assertThrows(UnsupportedOperationException.class,()->source.ecologyPointer(CdR4ObjectPorts.Data.YAGO_MAP1));assertThrows(UnsupportedOperationException.class,()->source.ecologyAnimation(CdR4ObjectPorts.Data.SPIKES_MAP));
 }
 @Test void animalTileWordReadKeepsLiteralZerosAndRejectsUnownedIndices(){
  int[] expected={0x450,0x450,0x450,0,0x450,0x450,0x450,0,0,0,0x450};
  for(int n=0;n<expected.length;n++)assertEquals(expected[n],source.ecologyAnimalTile(n*2));
  assertThrows(UnsupportedOperationException.class,()->source.ecologyAnimalTile(-2));assertThrows(UnsupportedOperationException.class,()->source.ecologyAnimalTile(1));assertThrows(UnsupportedOperationException.class,()->source.ecologyAnimalTile(22));assertThrows(UnsupportedOperationException.class,()->source.ecologyAnimalTile(0x10000));
 }
}
