package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Tests FIRST: genuine controller WARP/Ready/first pass; no fake B state/context or ID rewrite.
 * Only the last guard test uses detached source DATA. All Java/runtime/pixels UNRUN. */
@Isolated class TestCdOriginalPastAntonWorld extends TestCdOriginalR11LoadingWorld {
 @Test void genuineFirstPastAntonPassReturnsAndReplaysCompleteFullAndCourseOwners()throws Exception{
  var original=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(original,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(original,attempt);readyPast(loop,m);var frame=m.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
   step(loop,0);assertFalse(frame.closed());assertEquals(1,frame.environment().globals().levelFrames());var current=m.originalR11PlacementImage().pool().slots().getFirst();var data=original.initialProgramSource().orElseThrow().pastLoadingSource().standardPastObjectSource();
   assertEquals(CdAnton.OBJECT_ID,current.id());assertEquals(CdR11bObjectData.Table.MAP_SPR_ANTON.nativeAddress(),current.longBits(CdObjectState.LongField.MAPPINGS));var tiles=data.view(CdR11bObjectData.Table.OBJECT_TILE_IDS).bytes();assertEquals(tiles.unsignedWord((short)tiles.unsignedWord(4)+current.subtype2()*2),current.word(CdObjectState.WordField.TILE));assertEquals(0x29,current.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));assertEquals(0x18,current.unsignedByte(CdObjectState.ByteField.X_RADIUS));assertEquals(0x13,current.unsignedByte(CdObjectState.ByteField.Y_RADIUS));assertEquals(current.subtype()==0?0xFFFF0000:0xFFFF8000,current.longBits(CdObjectState.LongField.ANTON_X_DELTA));
   var expected=image(m);registry.restore(full);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));registry.restoreCourse(course);step(loop,0);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));assertFalse(frame.closed());
  }
 }
 @Test void idleBorrowedCachedAntonCannotDispatchOrMutateTheHealthyPastWorld()throws Exception{
  var original=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(original,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(original,attempt);readyPast(loop,m);var before=image(m);var wf=SonicCdGameModule.class.getDeclaredField("r11World");wf.setAccessible(true);var world=wf.get(m);assertEquals(CdWorld.class,world.getClass());var bf=CdWorld.class.getDeclaredField("binding");bf.setAccessible(true);var binding=bf.get(world);var af=binding.getClass().getDeclaredField("enemyAnton");af.setAccessible(true);var anton=assertInstanceOf(CdAnton.class,af.get(binding));
   assertThrows(IllegalStateException.class,()->anton.run(32,new CdAnton.Globals(0,0,0,0,0)));CdCapturedValueAssertions.assertCapturedValueEquals(before,image(m));assertFalse(m.originalR11EntryFrame().closed());
  }
 }
 @Test void currentTerrainFenceRejectsEqualForeignSourcesAndChangedBlockOrLayoutData()throws Exception{
  var original=TestCdOriginalBootPrefix.source();var source=original.initialProgramSource().orElseThrow().pastLoadingSource();var player=source.playerSource();var terrain=player.terrain();
  CdWorld.requireOriginalPastAntonTerrain(player,source,terrain.blocks(),terrain.layout().ram());
  var bytes=terrain.blocks().copy();bytes[0]^=1;assertThrows(IllegalArgumentException.class,()->CdWorld.requireOriginalPastAntonTerrain(player,source,CdAssetBytes.copyOf(bytes),terrain.layout().ram()));
  var layout=terrain.layout().ram().copy();layout[layout.length-1]^=1;assertThrows(IllegalArgumentException.class,()->CdWorld.requireOriginalPastAntonTerrain(player,source,terrain.blocks(),CdAssetBytes.copyOf(layout)));
  var root=System.getProperty("soniccd.media.root");assertNotNull(root);try(var media=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty())){var other=CdPpzProgrammeData.open(media,CdPpzDataLayout.Programme.R11B).levelLoadingSource();var foreign=other.playerSource();assertThrows(IllegalArgumentException.class,()->CdWorld.requireOriginalPastAntonTerrain(foreign,source,terrain.blocks(),terrain.layout().ram()));}
 }
 private static void readyPast(com.openggf.GameLoop loop,SonicCdGameModule m){
  for(int n=0;n<256&&m.originalR11LoadingPrefix().main().levelStartedByte()!=1;n++)step(loop,0);assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());for(int n=0;n<6351;n++)step(loop,((n>=4000&&n<4032)||(n>=4096&&n<4128)||(n>=4608&&n<4640)||(n>=6000&&n<6032))?0x18:8);
  for(int n=0;n<22;n++)step(loop,8);step(loop,0);step(loop,0);for(int n=0;n<90;n++)step(loop,0);step(loop,0);for(int n=0;n<24;n++)step(loop,0);step(loop,0x80);for(int n=0;n<24;n++)step(loop,0);step(loop,0);step(loop,0);assertEquals(CdCommand.R11B,m.originalWarpDestination().orElseThrow().command());step(loop,0);
  for(int n=0;n<256&&m.originalR11LoadingImage().phase()!=SonicCdR31aLoadingVisit.Phase.READY;n++)step(loop,0);assertEquals(SonicCdR31aLoadingVisit.Phase.READY,m.originalR11LoadingImage().phase());assertEquals(CdStagePayload.R11B.stage(),m.originalR11EntryFrame().stageSource().stage());assertEquals(0,m.originalR11EntryFrame().environment().movement().gates().timeZone());
 }
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();return List.of(f.captureActor(),f.captureShared(),p.status(),p.pool().slots(),p.savedFlags(),m.originalR11LoadingImage(),m.originalR11LoadingPrefix(),m.originalR11WorldImage(),m.originalR11TimeWarpSavedImage(),m.programServicesState(),m.programSoundRequests());}
}
