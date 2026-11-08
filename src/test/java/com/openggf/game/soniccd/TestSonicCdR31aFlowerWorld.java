package com.openggf.game.soniccd;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import org.junit.jupiter.api.parallel.Isolated;import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
/** All Java UNRUN. Actual public Boot/World/Frame/placement/registry ingress; explicitly
 * CONSTRUCTED source slots after first ordinary visit, never natural reach or original kill proof. */
@Isolated class TestSonicCdR31aFlowerWorld {
 @TempDir Path directory;private final TestSonicCdR31aSelectedProfileLoading f=new TestSonicCdR31aSelectedProfileLoading();
 @BeforeEach void setup(){f.directory=directory;f.setup();}@AfterEach void cleanup(){f.cleanup();}
 @Test void originalExplosionReturnPrecedesTheNextPhysicalFlowerInvocation()throws Exception{try(var b=f.start(1,0x80)){ready(b);seed(b,0x18,4,1);var p=b.module.r31WorldHost().placement().pool();p.write(p.state(32).moveByte(ROUTINE_2,0));TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(0x1F,p.state(32).id());assertEquals(0,p.state(32).routine());TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(2,p.state(32).routine());assertEquals(0x6D7,p.state(32).word(TILE));}}
 @Test void fullCourseRestoreReplaysOriginalFlowerThroughTheSameOwners()throws Exception{try(var b=f.start(1,0x80)){ready(b);seed(b,0x1F,4,1);var h=b.module.r31WorldHost();var frame=b.module.requirePlayerFrame();var r=b.mode.getRewindRegistry();var full=r.capture();var course=r.captureCourse();TestSonicCdR31aSelectedProfileLoading.step(b,8);var after=TestSonicCdR31aSelectedProfileLoading.values(b);for(boolean c:new boolean[]{false,true}){if(c)r.restoreCourse(course);else r.restore(full);TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(after,TestSonicCdR31aSelectedProfileLoading.values(b));assertSame(frame,b.module.requirePlayerFrame());assertSame(h,b.module.r31WorldHost());}}}
 @Test void genuineWorldSeedLandingRetainsCountAndAngleBeforeTheMissingPositionStore()throws Exception{
  try(var b=f.start(1,0x80)){
   ready(b);seed(b,0x1F,2,0);var h=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var frame=b.module.requirePlayerFrame();var placement=h.placement();var pool=placement.pool();
   pool.write(pool.state(32).moveWord(X,96).moveWord(Y,1024));var selected=pool.state(32);
   var floor=com.openggf.game.soniccd.objects.CdObjectCollisionCheck.objGetFloorDist(com.openggf.game.soniccd.player.CdPlayerTerrain.fromStageTerrain(h.currentTerrain()),selected);
   assertTrue(floor.distance()<0,"Constructed landing vector requires the genuine current-terrain read");
   assertEquals(1,frame.environment().movement().gates().timeZone()&0x7F);assertEquals(0,core.state().level().flowerCount());
   var saved=placement.capture().savedFlags();
   TestSonicCdR31aSelectedProfileLoading.step(b,8);
   // Same authentic count/angle obligation; now the original X then Y stores return.
   assertEquals(0x100,core.state().level().flowerCount());assertEquals((selected.centreY()+floor.distance())&0xFFFF,pool.state(32).centreY());
   assertEquals(floor.primaryAngle(),frame.environment().movement().globals().primaryAngle());assertEquals(4,pool.state(32).routine());assertEquals(1,pool.state(32).unsignedByte(ANIMATION));
   assertEquals(selected.centreX(),core.state().flowerPositions().word(0x100));assertEquals(pool.state(32).centreY(),core.state().flowerPositions().word(0x102));
   assertTrue(core.state().flowerPositions().clearedByOriginal());assertArrayEquals(saved.copy(),placement.capture().savedFlags().copy());assertFalse(frame.closed());

  }
 }
 private static void ready(TestSonicCdR31aSelectedProfileLoading.Booted b){TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);}
 private static void seed(TestSonicCdR31aSelectedProfileLoading.Booted b,int id,int routine,int subtype){var p=b.module.r31WorldHost().placement().pool();for(int w=32;w<128;w++)p.deleteObject(w);var m=b.module.requirePlayerFrame().state().motion();p.write(p.state(32).moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype).moveByte(SPRITE_FLAGS,4).moveLong(MAPPINGS,id==0x1F?0x2336D8:0x23A6B4).moveWord(TILE,0x6D7).moveWord(X,(m.centreX16_16()>>>16)+100).moveWord(Y,100));}
}
