package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdR4WaterAirPorts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Source-derived original contracts only. Java/media verification UNRUN. */
class TestCdProgrammeTtFirstWaterSource {
 static CdProgrammeData programme;static CdProgrammeObjectSource source;static CdMmdImage image;
 @BeforeAll static void actual()throws Exception{String root=System.getProperty("soniccd.media.root","");Path cue=Path.of(root,"USA","Sonic CD (US).cue");assumeTrue(!root.isBlank()&&Files.isRegularFile(cue));try(var a=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){programme=CdProgrammeData.open(a,CdR4DataLayout.Programme.R41A);source=programme.objectSource();image=a.mmd(CdNativeFile.R41A);}}
 @Test void originalWaterEntryDispatchAndEveryNewOperandBorrowExactOriginalRange(){
  assertEquals(0x20608CL,source.view(CdProgrammeObjectSource.Table.DISPATCH).bytes().unsignedLong((0x21-1)*4));assertEquals(0x2066D2L,source.view(CdProgrammeObjectSource.Table.DISPATCH).bytes().unsignedLong((0x0B-1)*4));
  for(var t:List.of(CdProgrammeObjectSource.Table.PLAYER_BUBBLES_ANIMATIONS,CdProgrammeObjectSource.Table.SPLASH_ANIMATIONS,CdProgrammeObjectSource.Table.SPLASH_MAPPINGS,CdProgrammeObjectSource.Table.PLAYER_BUBBLES_MAP_STORE,CdProgrammeObjectSource.Table.SPLASH_MAP_STORE)){var v=source.view(t);assertSame(v,source.view(t));assertArrayEquals(image.readFile(t.fileOffset(),t.length()),v.bytes().copy());assertEquals(t.sha256(),CdR11aProfile.sha256(v.bytes().copy()));}
 }
 @Test void bubbleDataIsExistingSameReaderWhileCountedAnimationsAndSplashAreFinite(){
  var b=CdR4WaterAirPorts.Animation.PLAYER_BUBBLES;var s=CdR4WaterAirPorts.Animation.SPLASH;
  assertEquals(source.bubbleSpawner().mappingsBits(),source.waterEntryMappings(b));assertEquals(0x21D652,source.waterEntryMappings(s));assertSame(source.waterEntryAnimation(b),source.waterEntryAnimation(b));assertSame(source.waterEntryAnimation(s),source.waterEntryAnimation(s));assertEquals(16,source.waterEntryAnimation(b).scriptCount());assertEquals(3,source.waterEntryAnimation(s).scriptCount());
  assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,source.mappings().resolve(0x21D652,false,6));assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(0x21D652,false,7));assertThrows(UnsupportedOperationException.class,()->source.waterEntryAnimation(CdR4WaterAirPorts.Animation.WATER_SURFACE));
 }
 @Test void oldSixtyOneOrdinalsAndExistingFallSpikesRemainFixed(){assertEquals(58,CdProgrammeObjectSource.Table.EXPLOSION_MAPPINGS.ordinal());assertEquals(59,CdProgrammeObjectSource.Table.FALL_SPIKES_MAPPINGS.ordinal());assertEquals(60,CdProgrammeObjectSource.Table.FALL_SPIKES_PARENT_MAP_STORE.ordinal());assertEquals(61,CdProgrammeObjectSource.Table.PLAYER_BUBBLES_ANIMATIONS.ordinal());assertEquals(0x20EB08,source.ecologyPointer(com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Data.FALL_SPIKES_MAP));}
}
