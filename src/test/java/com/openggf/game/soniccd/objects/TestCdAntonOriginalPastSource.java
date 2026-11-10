package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
/** Actual-reader DATA plus explicitly constructed component/fault controls.
 * These controls grant no World, original entry, service, native clear or runtime state. All Java UNRUN. */
class TestCdAntonOriginalPastSource {
 private record Fixture(CdLevelLoadingSource loading,CdR11bObjectData data,CdObjectPlacementRuntime placement){}
 private static CdLevelLoadingSource loading(CdPpzDataLayout.Programme programme)throws Exception{
  var root=System.getProperty("soniccd.media.root");assertNotNull(root);
  try(var media=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty())){return CdPpzProgrammeData.open(media,programme).levelLoadingSource();}
 }
 private static Fixture fixture()throws Exception{var s=loading(CdPpzDataLayout.Programme.R11B);return new Fixture(s,s.standardPastObjectSource(),CdObjectPlacementRuntime.fromPlacements(s.placements(),CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES])));}
 private static CdObjectState selected(Fixture f,int subtype,int secondary){var s=f.placement().pool().state(32).moveByte(ID,CdAnton.OBJECT_ID).moveWord(X,0x345).moveWord(Y,0x234).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,secondary);f.placement().pool().write(s);return s;}
 private static CdEnemyPrimitiveScope.Observer noGuard(){return new CdEnemyPrimitiveScope.Observer(){public void before(CdEnemyPrimitiveScope.Call c){}public void after(CdEnemyPrimitiveScope.Call c){}};}
 private static void samePlacement(CdObjectPlacementRuntime.Snapshot a,CdObjectPlacementRuntime.Snapshot b){assertEquals(a.status(),b.status());assertEquals(a.pool().slots(),b.pool().slots());assertArrayEquals(a.savedFlags().copy(),b.savedFlags().copy());}
 @Test void sameReaderAntonCachesFourFramesTwoScriptsAndActualB72TileOperand()throws Exception{
  var f=fixture();var data=f.data();assertSame(data.anton(),data.anton());assertSame(data.terrain(),f.loading().terrain());assertEquals(4,data.anton().mappingFrames().size());assertEquals(2,data.anton().animations().size());
  assertArrayEquals(data.view(CdR11bObjectData.Table.ANI_ANTON).bytes().copy(),data.anton().animationTable().copy());assertEquals(0x20B202,CdR11bObjectData.Table.MAP_SPR_ANTON.nativeAddress());
  assertEquals(0x20B10C,data.dispatch(CdAnton.OBJECT_ID).orElseThrow().handlerAddress());assertEquals("ObjAnton",data.dispatch(CdAnton.OBJECT_ID).orElseThrow().nativeLabel());
  var tiles=data.view(CdR11bObjectData.Table.OBJECT_TILE_IDS).bytes();assertEquals(72,tiles.length());assertEquals(0x23ED,CdAnton.readPastTileWord(tiles,0));assertEquals(0x2000,CdAnton.readPastTileWord(tiles,1));assertEquals(0x2403,CdAnton.readPastTileWord(tiles,2));assertNotEquals(0x2370,CdAnton.readPastTileWord(tiles,1),"Past aliases its real next directory WORD, not A's second Anton tile");
 }
 @Test void foreignEqualPastAndPresentPlacementsCannotBorrowTheCachedBFactory()throws Exception{
  var a=fixture();var b=fixture();var old=b.placement().capture();
  assertThrows(IllegalArgumentException.class,()->CdAnton.fromOriginalStandardPastSource(b.placement(),a.data(),c->{},(p,id)->{},noGuard()));samePlacement(old,b.placement().capture());
  var present=loading(CdPpzDataLayout.Programme.R11A);var p=CdObjectPlacementRuntime.fromPlacements(present.placements(),CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));
  assertThrows(IllegalArgumentException.class,()->CdAnton.fromOriginalStandardPastSource(p,a.data(),c->{},(pool,id)->{},noGuard()));assertSame(a.data().anton(),a.data().anton());
 }
 @Test void initAndPlaceRetainAllNativeHeaderTileAndYStoresBeforeFirstFloorFault()throws Exception{
  for(int subtype:new int[]{0,1})for(int secondary:new int[]{0,1}){
   var f=fixture();selected(f,subtype,secondary);var before=f.placement().capture();var fault=new IllegalStateException("reached native floor prezero");var stores=new ArrayList<CdObjectState>();var draws=new AtomicInteger();var angles=new AtomicInteger();
   var owner=new AtomicReference<CdAnton>();var observer=new CdEnemyPrimitiveScope.Observer(){public void before(CdEnemyPrimitiveScope.Call c){assertTrue(owner.get().isCurrentOriginalPrimitive(c));if(c.kind()==CdEnemyPrimitiveScope.Kind.ANGLE)throw fault;}public void after(CdEnemyPrimitiveScope.Call c){if(c.kind()==CdEnemyPrimitiveScope.Kind.STORE&&c.completed())stores.add(c.output());}};
   var body=CdAnton.fromOriginalStandardPastSource(f.placement(),f.data(),c->angles.incrementAndGet(),(p,id)->draws.incrementAndGet(),observer);owner.set(body);
   assertSame(fault,assertThrows(IllegalStateException.class,()->body.run(32,new CdAnton.Globals(0,0,0,0x300,0x5A))));var actual=f.placement().pool().state(32);assertEquals(4,stores.size());assertEquals(0,angles.get());assertEquals(0,draws.get());
   assertEquals(CdAnton.OBJECT_ID,actual.id());assertEquals(0,actual.routine());assertEquals(0x20B202,actual.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(secondary==0?0x23ED:0x2000,actual.word(CdObjectState.WordField.TILE));assertEquals(0x345,actual.word(ANTON_ANCHOR_X));assertEquals(0x235,actual.centreY());assertEquals(4,actual.spriteFlags());assertEquals(4,actual.unsignedByte(CdObjectState.ByteField.PRIORITY));assertEquals(0x18,actual.unsignedByte(CdObjectState.ByteField.X_RADIUS));assertEquals(0x13,actual.unsignedByte(CdObjectState.ByteField.Y_RADIUS));assertEquals(0x18,actual.unsignedByte(CdObjectState.ByteField.WIDTH));assertEquals(0x29,actual.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));assertEquals(subtype==0?0xFFFF0000:0xFFFF8000,actual.longBits(ANTON_X_DELTA));assertEquals(subtype==0?0:1,actual.unsignedByte(CdObjectState.ByteField.ANIMATION));
   assertEquals(before.pool().slots().subList(1,before.pool().slots().size()),f.placement().capture().pool().slots().subList(1,before.pool().slots().size()));assertEquals(before.status(),f.placement().status());assertArrayEquals(before.savedFlags().copy(),f.placement().capture().savedFlags().copy());
  }
 }
 @Test void originalAngleCallbackFaultKeepsItsIdentityAndRetiresTheExactCall()throws Exception{
  var f=fixture();selected(f,0,0);var fault=new IllegalStateException("actual angle owner fault");var call=new AtomicReference<CdAnton.PrimaryAngleCall>();var owner=new AtomicReference<CdAnton>();var draws=new AtomicInteger();
  var body=CdAnton.fromOriginalStandardPastSource(f.placement(),f.data(),c->{assertTrue(owner.get().isCurrentPrimaryAngleCall(c));assertEquals(0x55,c.expectedPrimaryAngle());assertEquals(0,c.value());call.set(c);throw fault;},(p,id)->draws.incrementAndGet(),noGuard());owner.set(body);
  assertSame(fault,assertThrows(IllegalStateException.class,()->body.run(32,new CdAnton.Globals(0,0,0,0x300,0x55))));assertEquals(0,fault.getSuppressed().length);assertEquals(0x235,f.placement().pool().state(32).centreY());assertEquals(0,draws.get());assertNotNull(call.get());assertFalse(body.isCurrentPrimaryAngleCall(call.get()));
 }
 @Test void fullComponentRestoreReplaysPrefixesWithoutReusingAnOldPrimitiveReceipt()throws Exception{
  var f=fixture();selected(f,1,1);var full=f.placement().capture();var last=new AtomicReference<CdEnemyPrimitiveScope.Call>();var owner=new AtomicReference<CdAnton>();var other=new AtomicReference<CdAnton>();var fault=new IllegalStateException("floor control");
  var observer=new CdEnemyPrimitiveScope.Observer(){public void before(CdEnemyPrimitiveScope.Call c){assertTrue(owner.get().isCurrentOriginalPrimitive(c));assertFalse(other.get().isCurrentOriginalPrimitive(c));if(c.kind()==CdEnemyPrimitiveScope.Kind.ANGLE){last.set(c);throw fault;}}public void after(CdEnemyPrimitiveScope.Call c){}};
  var body=CdAnton.fromOriginalStandardPastSource(f.placement(),f.data(),c->{},(p,id)->{},observer);owner.set(body);other.set(CdAnton.fromOriginalStandardPastSource(f.placement(),f.data(),c->{},(p,id)->{},noGuard()));
  assertSame(fault,assertThrows(IllegalStateException.class,()->body.run(32,new CdAnton.Globals(0,0,0,0x300,1))));var expected=f.placement().capture();var stale=last.get();assertFalse(body.isCurrentOriginalPrimitive(stale));f.placement().restore(full);
  assertSame(fault,assertThrows(IllegalStateException.class,()->body.run(32,new CdAnton.Globals(0,0,0,0x300,1))));samePlacement(expected,f.placement().capture());assertTrue(last.get().generation()>stale.generation());assertFalse(body.isCurrentOriginalPrimitive(stale));assertFalse(body.isCurrentOriginalPrimitive(last.get()));
 }
 @Test void goodFutureUsesTheSharedNativeDeleteAndExplosionPrefixWithoutDrawing()throws Exception{
  var f=fixture();selected(f,0,0);var draws=new AtomicInteger();var angles=new AtomicInteger();var body=CdAnton.fromOriginalStandardPastSource(f.placement(),f.data(),c->angles.incrementAndGet(),(p,id)->draws.incrementAndGet(),noGuard());
  var result=body.run(32,new CdAnton.Globals(1,0,0,0x300,7));assertEquals(0x18,result.state().id());assertEquals(0x345,result.state().centreX());assertEquals(0x234,result.state().centreY());assertEquals(0,result.state().spriteFlags());assertTrue(result.effects().stream().anyMatch(CdAnton.ConvertedToExplosion.class::isInstance));assertEquals(0,draws.get());assertEquals(0,angles.get());
 }
}
