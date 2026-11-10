package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Tests first/allJavaUNRUN: genuine original DATA only; no World, actor or art ownership. */
class TestCdOriginalTtMonitorGraphSource {
 private static CdProgrammeData programme,foreign;private static CdProgrammeObjectSource source;
 @BeforeAll static void original()throws Exception{String root=System.getProperty("soniccd.media.root","");assertFalse(root.isBlank());try(var p=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty())){programme=CdProgrammeData.open(p,CdR4DataLayout.Programme.R41A);foreign=CdProgrammeData.open(p,CdR4DataLayout.Programme.R41A);}source=programme.objectSource();}
 @Test void powerupDataPreservesTheRealOverlapAndNineOriginalScripts(){
  var a=source.powerupAssets();assertSame(a,source.powerupAssets());assertEquals(0x21D528,a.mappingAddressBits());assertEquals(9,a.animations().scriptCount());assertSame(source.view(CdProgrammeObjectSource.Table.POWERUP_ANIMATIONS).bytes(),a.animations().bytes());assertEquals(295,a.animations().bytes().length());assertEquals("cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61",CdR11aProfile.sha256(a.animations().bytes().copy()));var raw=source.view(CdProgrammeObjectSource.Table.POWERUP_MAPPINGS).bytes();assertEquals(273,raw.length());assertEquals(37,raw.unsignedWord(0));assertEquals(26,raw.unsignedWord(2));assertEquals(0,source.mappings().resolve(a.mappingAddressBits(),false,0).pieces().size());assertEquals(4,source.mappings().resolve(a.mappingAddressBits(),false,1).pieces().size());assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(a.mappingAddressBits(),false,13));
 }
 @Test void eightMonitorItemFirstPiecesUseOneExistingStaticMapperAlgorithm(){
  var assets=source.timePostAssets();var raw=assets.visual().mappings();var declared=CdObjectMappings.monitorItemStaticPieces(raw,assets.mappingsAddress());assertEquals(8,declared.size());for(int n=0;n<8;n++){int at=(short)raw.unsignedWord(n*2)+1;var piece=source.mappings().resolve(assets.mappingsAddress()+at,true,n);assertEquals(1,piece.pieces().size());assertEquals(raw.unsignedByte(at+1),piece.pieces().getFirst().sizeByte());}int postAt=(short)raw.unsignedWord(16)+1;assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(assets.mappingsAddress()+postAt,true,8));
 }
 @Test void nativePowerupIndexAndEveryFullCopyUseOnlyOriginalReaderBytes(){
  var sources=source.powerupArtSources();assertSame(sources,source.powerupArtSources());int[] pointers={0x2364D4,0x236954,0x236DD4,0x235D28,0x235968};String[] hashes={"cbca98fc186fec63ea75c2c0dd8da06ec651c02c37232c15e38cecb749d5b7d0","706736889a2cced446522a69a86853de41d4dfc9204c83ab0a7d2690d3036fcc","2094d9156ba35fdb865feb01bee3e64b809ad0297e3d46d7defcf79d94fb5919","911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd","8830d700eed4f092a9f4329993caf68940ac65bae7656e54a823031711e371f3"};var index=source.view(CdProgrammeObjectSource.Table.POWERUP_ART_INDEX);assertEquals(0x2068CE,index.nativeAddress());for(int n=0;n<5;n++){assertEquals(pointers[n],index.bytes().unsignedLong(n*4));var bytes=sources.copy(n);assertSame(bytes,sources.copy(n));assertEquals(1024,bytes.length());assertEquals(hashes[n],CdR11aProfile.sha256(bytes.copy()));}assertThrows(IndexOutOfBoundsException.class,()->sources.copy(5));
 }
 @Test void activeCopyRetainsAll128ActualCpuTailBytesWithoutSourcePadding(){
  byte[] cpu=new byte[0x480];for(int n=0;n<cpu.length;n++)cpu[n]=(byte)(n*31);var runtime=CdPowerupArtRuntime.fromRawSources(source.powerupArtSources());for(int slot=0;slot<5;slot++){var copy=runtime.prepareActiveCopy(CdAssetBytes.copyOf(cpu),2+slot*2).bytes();assertArrayEquals(source.powerupArtSources().copy(slot).copy(),copy.slice(0,1024).copy());assertArrayEquals(Arrays.copyOfRange(cpu,1024,cpu.length),copy.slice(1024,128).copy());}var failure=assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,()->runtime.prepareActiveCopy(CdAssetBytes.copyOf(cpu),3));assertEquals(2,failure.pointerOffset());
 }
 @Test void explosionBorrowsItsOwnSevenFramesAndTwoScriptsWithoutPpzRoot(){
  var a=source.explosionAssets();assertSame(a,source.explosionAssets());assertEquals(0x21D714,a.mappingsAddress());assertEquals(7,a.explosion().mappingFrames().size());assertEquals(2,a.explosion().animations().size());assertEquals(145,a.explosion().mappings().length());assertEquals(19,a.explosion().animationTable().length());assertEquals("8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada",CdR11aProfile.sha256(a.explosion().mappings().copy()));assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,source.mappings().resolve(a.mappingsAddress(),false,6));assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(a.mappingsAddress(),false,7));
 }
 @Test void powerupOwnershipDoesNotSupplyKnownPixelsBeforeItsActualWriter()throws Exception{
  var loading=programme.levelLoadingSource();var unknown=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.unknownForLoading(loading);for(int n=0;n<36;n++){int destination=0xA880+n*32;assertTrue(source.powerupDestination(destination));assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,()->com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(unknown,destination/32));}for(int destination:new int[]{0xA87F,0xA881,0xAD00})assertFalse(source.powerupDestination(destination));
 }
 @Test void sameStageOtherReaderHasNoCurrentPlacementAuthorityAndInitialFamiliesStayExact(){
  var p=new CdObjectPlacementRuntime(programme.placements());source.requirePlacement(p);assertThrows(IllegalArgumentException.class,()->foreign.objectSource().requirePlacement(p));assertNotSame(source.powerupAssets(),foreign.objectSource().powerupAssets());assertEquals(List.of(4,0x0B,0x10,0x19,0x1C,0x20,0x21,0x22,0x2B,0x2F,0x32,0x34,0x3C),source.initialFamilyIds());assertEquals(42,CdProgrammeObjectSource.Table.MONITOR_ANIMATIONS.ordinal());
 }
}
