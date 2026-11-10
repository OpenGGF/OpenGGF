package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdR4WaterAirPorts;
import com.openggf.game.soniccd.player.CdPlayerMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tests FIRST; SOURCE/UNRUN. Actual source borrows plus explicitly constructed component
 * storage/fault vectors. No World/Frame birth, stage clear, Ready, natural route or pixels claimed. */
class TestCdProgrammeObjectSource {
 private static CdProgrammeData programme,foreign,past;
 private static CdProgrammeObjectSource source;
 @BeforeAll static void actualRetainedSource()throws Exception{
  var root=System.getProperty("soniccd.media.root","");var cue=Path.of(root,"USA","Sonic CD (US).cue");assumeTrue(!root.isBlank()&&Files.isRegularFile(cue),"Actual USA source");
  try(var actual=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
   assertEquals(CdR4DataLayout.USA_DISC_SHA256,actual.identity().normalizedSha256());
   programme=CdProgrammeData.open(actual,CdR4DataLayout.Programme.R41A);foreign=CdProgrammeData.open(actual,CdR4DataLayout.Programme.R41A);past=CdProgrammeData.open(actual,CdR4DataLayout.Programme.R41B);
  }
  source=programme.levelLoadingSource().objectSource();
 }
 @Test void facadeAndEveryBorrowAreCachedOnTheExactPrivateReader(){
  assertSame(source,programme.levelLoadingSource().objectSource());assertSame(source,programme.objectSource());
  assertEquals(CdStageKey.parse("R41A"),source.stage());assertEquals(0x200,source.nativeZoneActWord());
  for(var t:CdProgrammeObjectSource.Table.values()){var v=source.view(t);assertSame(v,source.view(t));assertEquals(t,v.table());assertEquals(t.nativeAddress(),v.nativeAddress());assertEquals(t.length(),v.bytes().length());assertEquals(t.sha256(),CdR11aProfile.sha256(v.bytes().copy()));}
  assertEquals(73,CdProgrammeObjectSource.Table.values().length);
 }
 @Test void actualLogicalIncludesKeepTheirOddTailsAndDistinctOriginalAddresses(){
  assertEquals(0x20954C,source.view(CdProgrammeObjectSource.Table.RING_MAPPINGS).nativeAddress());assertEquals(67,source.view(CdProgrammeObjectSource.Table.RING_MAPPINGS).bytes().length());
  assertEquals(0x206986,source.waterSurface().mappingsAddress());assertEquals(143,source.view(CdProgrammeObjectSource.Table.WATER_MAPPINGS).bytes().length());
  assertEquals(0x20BAAC,source.view(CdProgrammeObjectSource.Table.TITLE_CHARACTERS).nativeAddress());assertEquals(0x20BAEC,source.titleAssets().mappingsAddress());
  assertEquals(0x209FF6,source.hudAssets().hudMappingAddressBits());assertEquals(0x209CDE,source.hudAssets().monitorMappingAddressBits());
  assertSame(source.view(CdProgrammeObjectSource.Table.WATER_ANIMATIONS).bytes(),source.waterSurface().animation().bytes());assertSame(source.view(CdProgrammeObjectSource.Table.WATER_OFFSETS).bytes(),source.waterSurface().offsets());
 }
 @Test void actualDispatchAndStartupFamilyIdsAreNativeTtFacts(){
  var index=source.view(CdProgrammeObjectSource.Table.DISPATCH).bytes();
  assertEquals(0x2068E2,index.unsignedLong((4-1)*4));assertEquals(0x2090D0,index.unsignedLong((0x10-1)*4));assertEquals(0x209F46,index.unsignedLong((0x1C-1)*4));assertEquals(0x20B3E0,index.unsignedLong((0x3C-1)*4));
  assertEquals(1,programme.levelLoadingSource().ordinaryPeriod(),"N1ecc Main CPU Variables.inc:12-14 TIME_PRESENT=1; zoneAct0200 is separate");assertEquals(List.of(0x04,0x0B,0x10,0x19,0x1C,0x20,0x21,0x22,0x2B,0x2F,0x32,0x34,0x3C),source.initialFamilyIds());
 }
 @Test void equalOtherReaderAndUnadmittedProgrammeCannotBorrowActualPlacement(){
  var placement=fixture();source.requirePlacement(placement);assertThrows(IllegalArgumentException.class,()->foreign.objectSource().requirePlacement(placement));assertThrows(IllegalArgumentException.class,()->foreign.objectSource().ring(placement));
  assertThrows(UnsupportedOperationException.class,()->past.objectSource());
 }
 @Test void genuineMappingFacadeCachesSixTtRootsWithoutPpzPointerAdmission(){
  var mappings=source.mappings();assertSame(mappings,source.mappings());
  for(var t:List.of(CdProgrammeObjectSource.Table.RING_MAPPINGS,CdProgrammeObjectSource.Table.WATER_MAPPINGS,CdProgrammeObjectSource.Table.TITLE_MAPPINGS,CdProgrammeObjectSource.Table.HUD_MAPPINGS,CdProgrammeObjectSource.Table.MONITOR_MAPPINGS))assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,mappings.resolve(t.nativeAddress(),false,0));
  assertThrows(UnsupportedOperationException.class,()->mappings.resolve(0x23F05C,false,0));assertThrows(UnsupportedOperationException.class,()->mappings.resolve(0x20BAEC,false,9));
 }
 @Test void actualRingFactoryUsesTheSameSharedBodyAndPreservesDirtyUnwrittenBirthBytes(){
  var p=fixture();var body=source.ring(p);assertSame(source.ringData(),body.sourceData());assertThrows(IllegalArgumentException.class,()->new CdRing(p,source.ringData()));
  p.pool().write(p.pool().state(127).moveByte(ID,0x10).moveByte(SUBTYPE,0).moveWord(X,0x90).moveWord(Y,0x70).moveWord(X_FRACTION,0xCAFE));var before=p.capture();var g=new RingGlobals();int[] draw={0};
  body.executeSelected(127,inputs(),g,calls((pool,w)->{draw[0]++;pool.write(pool.state(w).moveByte(SPRITE_FLAGS,pool.state(w).spriteFlags()&127));}));
  var out=p.pool().state(127);assertEquals(2,out.routine());assertEquals(0x20954C,out.longBits(MAPPINGS));assertEquals(0xA7AE,out.word(TILE));assertEquals(0xCAFE,out.word(X_FRACTION));assertEquals(1,draw[0]);
  for(int w=32;w<127;w++)assertEquals(before.pool().slots().get(w-32),p.pool().state(w));assertEquals(before.status(),p.capture().status());
 }
 @Test void originalTtRingSoundFaultKeepsItsCompletedAwardAndBodyPrefix(){
  var p=fixture();p.pool().write(p.pool().state(127).moveByte(ID,0x10).moveByte(ROUTINE,4));var g=new RingGlobals();var fault=new Fault();
  var calls=new CdRing.SourceCalls(){public void playFmSound(CdRing.FmSound v){assertEquals(CdRing.FmSound.FM_RING,v);throw fault;}public void subCpuCommand(CdRing.SubCpuCommand c){fail("No later SubCPU");}public void drawObject(CdObjectPool pool,int w){fail("No Draw after sound fault");}};
  assertSame(fault,assertThrows(Fault.class,()->source.ring(p).executeSelected(127,inputs(),g,calls)));assertEquals(6,p.pool().state(127).routine());assertEquals(4,g.value.rings());assertEquals(1,p.pool().state(127).unsignedByte(PRIORITY));assertEquals(0,fault.getSuppressed().length);
 }
 @Test void boundTtRingRejectsForeignDomainBeforeAnyOwnerReadOrStore(){
  var p=fixture();p.pool().write(p.pool().state(127).moveByte(ID,0x10));var before=p.capture();
  assertThrows(IllegalArgumentException.class,()->source.ring(p).executeSelected(127,new CdRing.Inputs(CdReleaseVariant.USA,CdStageKey.parse("R11A"),0,0,1,0),null,null));assertEquals(before.pool().slots(),p.capture().pool().slots());
 }
 @Test void actualTitleCharacterSourceKeepsNativeChildPrefixAtFirstActReadFault(){
  var p=fixture();p.pool().write(p.pool().state(32).moveByte(SUBTYPE,0x7D));byte[][] reserved=new byte[32][64];reserved[16][0]=0x3C;var fault=new Fault();int[] reads={0};
  var body=new CdTitleCard(p,source.titleAssets(),new CdTitleCard.ReservedSlots(){public CdAssetBytes read(int w){return CdAssetBytes.copyOf(reserved[w]);}public void write(int w,CdAssetBytes old,CdAssetBytes next){assertArrayEquals(reserved[w],old.copy());reserved[w]=next.copy();}},c->{reads[0]++;throw fault;},c->{fail("No PLC read during Init");return 0;},c->fail("No global store"),c->fail("No PLC"),c->fail("No Draw before Init returns"));
  assertSame(fault,assertThrows(Fault.class,()->body.run(16)));assertEquals(1,reads[0]);assertEquals(2,reserved[16][0x24]);assertEquals(0x3C,p.pool().state(32).id());assertEquals(0x7D,p.pool().state(32).subtype());assertEquals(0x20BAEC,p.pool().state(32).longBits(MAPPINGS));
  assertEquals(0x3C,p.pool().state(37).id());assertEquals(0,p.pool().state(38).id());
 }
 @Test void actualHudDataUsesItsOwnMappingBeforeOriginalDrawFault(){
  byte[][] slots=new byte[32][64];slots[10][0]=0x1C;var fault=new Fault();
  var hud=new CdReservedHud(source.hudAssets(),new CdReservedHud.Slots(){public CdAssetBytes read(int w){return CdAssetBytes.copyOf(slots[w]);}public void write(int w,CdAssetBytes old,CdAssetBytes next){assertArrayEquals(slots[w],old.copy());slots[w]=next.copy();}},w->{assertEquals(10,w);throw fault;});
  assertSame(fault,assertThrows(Fault.class,()->hud.runLive(10,new CdReservedHud.LiveGlobals(0,1,0,0,0,0,()->{fail("No conditional VInt read with nonzero rings");return 0;}))));
  assertEquals(2,slots[10][0x24]);assertEquals(0x209FF6,CdAssetBytes.copyOf(slots[10]).unsignedLong(4));
 }
 @Test void waterSurfaceSourceKeepsBothYStoresBeforeTheOriginalAnimationFault(){
  var slot=new WaterSlot();slot.raw[1]=(byte)0x81;slot.raw[0x2A]=1;var fault=new Fault();var calls=new WaterCalls(){@Override public void animate(CdR4WaterAirPorts.Slot o,CdR4WaterAirPorts.Animation animation){assertSame(slot,o);assertEquals(CdR4WaterAirPorts.Animation.WATER_SURFACE,animation);throw fault;}};
  var data=source.waterSurface();assertSame(fault,assertThrows(Fault.class,()->CdR4WaterSurface.run(slot,calls,data.mappingsAddress(),data.offsets())));assertEquals(0x85,slot.raw[1]&255);assertEquals(0x206986,slot.read(CdR4WaterAirPorts.L.MAPPINGS));assertEquals(2,slot.raw[0x24]);assertEquals(0,slot.raw[0x2A]);assertEquals(0x140,slot.read(CdR4WaterAirPorts.W.X));assertEquals(0x282,slot.read(CdR4WaterAirPorts.W.Y));assertEquals(2,slot.yStores);assertEquals(0,calls.draws);
 }
 @Test void fontAndCurrentsAreBorrowedFromTheSameSourceWithoutAnArtOrSwitchOwner(){
  assertSame(source.font(),source.font());assertEquals(1024,source.font().numbers().length());assertSame(source.view(CdProgrammeObjectSource.Table.HUD_RESET).bytes(),source.font().resetTiles());assertSame(source.view(CdProgrammeObjectSource.Table.HUD_DIVISORS).bytes(),source.font().divisors());
  assertSame(source.waterCurrents(),source.waterCurrents());assertSame(programme.view(CdR4DataLayout.Programme.R41A.range("WaterCurrents")),source.waterCurrents());assertEquals(1024,source.waterCurrents().length());
 }
 @Test void authenticNullDispatchIsOnlyTheActualClearIdThenReturnHandler(){
  assertTrue(source.isNullHandler(0x23));assertTrue(source.isNullHandler(7));assertFalse(source.isNullHandler(4));assertFalse(source.isNullHandler(0x20));assertThrows(IllegalArgumentException.class,()->source.isNullHandler(0));assertThrows(IllegalArgumentException.class,()->source.isNullHandler(83));
  var code=source.view(CdProgrammeObjectSource.Table.NULL_HANDLER).bytes();assertEquals(6,code.length());assertEquals(0x10BC,code.unsignedWord(0));assertEquals(0,code.unsignedWord(2));assertEquals(0x4E75,code.unsignedWord(4));
  // This predicate performs no body write. The World must clear only actual ID, not all64 or nothing.
  var p=fixture();p.pool().write(p.pool().state(127).moveByte(ID,0x23).moveByte(SUBTYPE,0x7D));var before=p.capture();assertTrue(source.isNullHandler(p.pool().state(127).id()));assertEquals(before.pool().slots(),p.capture().pool().slots());
 }
 @Test void firstWindowBubbleSourceBorrowsItsOwnMappingAnimationAndWobble(){
  var data=source.bubbleSpawner();assertSame(data,source.bubbleSpawner());assertEquals(0x21E9B4,data.mappingsBits());assertEquals(0x20A7FE,data.spawnPatternsAddressBits());assertSame(programme.view(CdR4DataLayout.Programme.R41A.range("WobbleTable")),data.wobble());
  assertEquals(7,source.bubbleSpawnerAnimation().scriptCount());assertSame(source.view(CdProgrammeObjectSource.Table.BUBBLE_ANIMATIONS).bytes(),source.bubbleSpawnerAnimation().bytes());assertEquals(47,source.bubbleSpawnerAnimation().bytes().length());
  assertEquals(183,source.view(CdProgrammeObjectSource.Table.BUBBLE_MAPPINGS).bytes().length());assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,source.mappings().resolve(0x21E9B4,false,22));assertThrows(UnsupportedOperationException.class,()->source.mappings().resolve(0x21E9B4,false,23));
 }
 @Test void reachedPatternReadsKeepFullRawPointerAndRealNeighbourBytesWithoutDefault(){
  var data=source.bubbleSpawner();var normal=source.view(CdProgrammeObjectSource.Table.BUBBLE_PATTERNS).bytes();int pointer=data.spawnPatternsAddressBits();for(int n=0;n<18;n++)assertEquals(normal.unsignedByte(n),data.spawnPatterns().readByte(pointer,n));
  assertEquals(0x75,data.spawnPatterns().readByte(pointer-1,0));assertEquals(0x0C,data.spawnPatterns().readByte(pointer+18,0));assertThrows(IllegalArgumentException.class,()->data.spawnPatterns().readByte(pointer,256));
  var fault=assertThrows(CdProgrammeObjectSource.UnownedBubblePatternRead.class,()->data.spawnPatterns().readByte(0x20954C,0));assertEquals(0x20954C,fault.pointerBits());assertEquals(0,fault.unsignedIndex());
 }
 @Test void originalControllerBirthRetainsActualSourceHeaderAndBurstPrefixAtFirstRngFault(){
  var slot=new WaterSlot();slot.raw[CdR4WaterAirPorts.B.SUBTYPE.offset]=(byte)0x80;slot.write(CdR4WaterAirPorts.W.X,0x110);slot.write(CdR4WaterAirPorts.W.Y,0x5C0);var fault=new Fault();int[] random={0};
  var calls=new WaterCalls(){@Override public int random(){random[0]++;throw fault;}};
  assertSame(fault,assertThrows(Fault.class,()->CdR4BubbleSpawner.run(slot,calls,source.bubbleSpawner(),CdR4BubbleSpawner.ContactProfile.USA)));assertEquals(1,random[0]);assertEquals(10,slot.read(CdR4WaterAirPorts.B.ROUTINE));assertEquals(6,slot.read(CdR4WaterAirPorts.B.ANIM));assertEquals(0x21E9B4,slot.read(CdR4WaterAirPorts.L.MAPPINGS));assertEquals(0x848A,slot.read(CdR4WaterAirPorts.W.TILE));assertEquals(0xFFFF,slot.read(CdR4WaterAirPorts.W.TIMER));assertEquals(1,slot.read(CdR4WaterAirPorts.W.BURST));assertEquals(0,calls.draws);
 }
 @Test void missingPatternByteFaultRetainsAlreadyPublishedChildIdAndCoordinates(){
  var slot=new WaterSlot();slot.raw[CdR4WaterAirPorts.B.ROUTINE.offset]=10;slot.raw[CdR4WaterAirPorts.B.SPRITE_FLAGS.offset]=(byte)0x84;slot.write(CdR4WaterAirPorts.W.BURST,1);slot.write(CdR4WaterAirPorts.W.X,0x110);slot.write(CdR4WaterAirPorts.W.Y,0x5C0);slot.write(CdR4WaterAirPorts.L.PATTERN,0x20954C);var child=new WaterSlot();child.raw[CdR4WaterAirPorts.B.SUBTYPE.offset]=0x7D;int[] random={0},spawn={0};
  var calls=new WaterCalls(){@Override public int random(){random[0]++;return 0;}@Override public Optional<CdR4WaterAirPorts.Slot> spawn(){spawn[0]++;return Optional.of(child);}};
  var fault=assertThrows(CdProgrammeObjectSource.UnownedBubblePatternRead.class,()->CdR4BubbleSpawner.run(slot,calls,source.bubbleSpawner(),CdR4BubbleSpawner.ContactProfile.USA));assertEquals(0x20954C,fault.pointerBits());assertEquals(1,spawn[0]);assertEquals(2,random[0]);assertEquals(0x20,child.read(CdR4WaterAirPorts.B.ID));assertEquals(0x108,child.read(CdR4WaterAirPorts.W.X));assertEquals(0x5C0,child.read(CdR4WaterAirPorts.W.Y));assertEquals(0x7D,child.read(CdR4WaterAirPorts.B.SUBTYPE));assertEquals(0,calls.draws);
 }
 private static CdObjectPlacementRuntime fixture(){return new CdObjectPlacementRuntime(programme.placements());} // Declared component bank, not a claimed native clear/lifecycle.
 private static CdRing.Inputs inputs(){return new CdRing.Inputs(CdReleaseVariant.USA,CdStageKey.parse("R41A"),2,0,programme.levelLoadingSource().ordinaryPeriod(),0);}
 private static final class RingGlobals implements CdRing.GlobalsPort{CdRingGlobals value=new CdRingGlobals(3,3,0,0,0,0,0,1);public CdRingGlobals read(){return value;}public void write(CdRingGlobals next){value=next;}}
 private interface Draw{void draw(CdObjectPool pool,int w);}
 private static CdRing.SourceCalls calls(Draw draw){return new CdRing.SourceCalls(){public void playFmSound(CdRing.FmSound sound){fail("No sound in this control");}public void subCpuCommand(CdRing.SubCpuCommand command){fail("No subCPU in this control");}public void drawObject(CdObjectPool pool,int w){draw.draw(pool,w);}};}
 private static final class Fault extends RuntimeException{}
 private static final class WaterSlot implements CdR4WaterAirPorts.Slot{
  final byte[] raw=new byte[64];int yStores;
  public int read(CdR4WaterAirPorts.B f){return raw[f.offset]&255;}public int read(CdR4WaterAirPorts.W f){return((raw[f.offset]&255)<<8)|(raw[f.offset+1]&255);}public int read(CdR4WaterAirPorts.L f){return(readRaw(f.offset)<<16)|readRaw(f.offset+2);}private int readRaw(int at){return((raw[at]&255)<<8)|(raw[at+1]&255);}
  public void write(CdR4WaterAirPorts.B f,int v){raw[f.offset]=(byte)v;}public void write(CdR4WaterAirPorts.W f,int v){if(f==CdR4WaterAirPorts.W.Y)yStores++;writeRaw(f.offset,v);}public void write(CdR4WaterAirPorts.L f,int v){writeRaw(f.offset,v>>>16);writeRaw(f.offset+2,v);}private void writeRaw(int at,int v){raw[at]=(byte)(v>>>8);raw[at+1]=(byte)v;}
 }
 private static class WaterCalls implements CdR4WaterAirPorts.Calls{
  int draws;private AssertionError missing(){return new AssertionError("Unreached owner");}
  public int read(CdR4WaterAirPorts.Value f){return switch(f){case CAMERA_X->0x100;case WATER_Y->0x280;default->throw missing();};}public void write(CdR4WaterAirPorts.Value f,int v){throw missing();}public CdR4WaterAirPorts.Slot player(){throw missing();}public CdPlayerMath.SineCosine sineCosine(int a){throw missing();}public CdR4WaterAirPorts.Slot reservedBubbles(){throw missing();}public Optional<CdR4WaterAirPorts.Slot> spawn(){throw missing();}public int random(){throw missing();}public void sound(int n){throw missing();}public void music(int n){throw missing();}public void setPlayerGround(){throw missing();}public void move(CdR4WaterAirPorts.Slot s){throw missing();}public void animate(CdR4WaterAirPorts.Slot s,CdR4WaterAirPorts.Animation a){throw missing();}public void draw(CdR4WaterAirPorts.Slot s){draws++;throw missing();}public void delete(CdR4WaterAirPorts.Slot s){throw missing();}
 }
 @Test void actualHudWriterDestinationsRequireTheOriginalSixOperandsAndRejectAdjacentWindows(){
  var code=source.view(CdProgrammeObjectSource.Table.HUD_RENDER_CODE).bytes();
  assertEquals(0x500,code.length());assertEquals(0x4A79,code.unsignedWord(0));
  for(int dest:new int[]{0xB060,0xB220,0xB260,0xB2E0,0xB360,0xB400,0xB4A0,0xB4C0})assertTrue(source.hudDestination(dest),Integer.toHexString(dest));
  for(int dest:new int[]{-1,0xB05F,0xB401,0xB49F,0xB4C1,0x10000})assertFalse(source.hudDestination(dest),Integer.toHexString(dest));
 }
}
