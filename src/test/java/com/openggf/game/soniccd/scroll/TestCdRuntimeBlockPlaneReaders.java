package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.resources.CdBlockDefinitionStaging;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed pure format controls, no original loading or World authority. Java UNRUN. */
class TestCdRuntimeBlockPlaneReaders {
 private static CdAssetBytes bytes(byte[] b){return CdAssetBytes.copyOf(b);}
 private static CdR11aBackgroundPlane.State plane(){byte[] b=new byte[4096];Arrays.fill(b,(byte)0x55);return new CdR11aBackgroundPlane.State(bytes(b));}
 private static CdR11bForegroundPlane foreground(CdBlockDefinitionStaging bank){byte[] chunks=new byte[512],layout=new byte[2048];Arrays.fill(layout,(byte)1);for(int p=0;p<512;p+=2){chunks[p]=3;chunks[p+1]=(byte)0xFF;}return CdR11bForegroundPlane.fromTables(bytes(new byte[8]),bytes(chunks),bytes(layout)).withBlockDefinitions(bank);}
 private static CdR11bForegroundPlane.Result draw(CdR11bForegroundPlane owner,CdR11aBackgroundPlane.State before){return owner.initialize(before,new CdR11aBackgroundPlane.Camera(0,0),0,new CdR11bForegroundPlane.CallerMetadata(OptionalInt.of(0)));}
 @Test void unknownRuntimeBankNeverFallsBackToCanonicalOrJavaZero(){
  var before=plane();var fault=assertThrows(CdR11bForegroundPlane.InvalidPlane.class,()->draw(foreground(CdBlockDefinitionStaging.unknown()),before));
  assertEquals(CdR11bForegroundPlane.Boundary.BLOCK_READ,fault.boundary());assertEquals(8184,fault.byteOffset());
  assertInstanceOf(CdBlockDefinitionStaging.UnknownRead.class,fault.getCause());assertArrayEquals(before.nameTable().copy(),fault.completedPrefix().state().nameTable().copy());assertEquals(0x5555,before.nameTable().unsignedWord(0xF00));
 }
 @Test void provedClearSuffixUsesTheExistingRawChunkSelectionWithoutExtendingCanonicalData(){
  var result=draw(foreground(CdBlockDefinitionStaging.afterFileBufferClear()),plane());
  assertEquals(512,result.blocks().size());assertEquals(8184,result.blocks().getFirst().blockByteOffset());assertEquals(0,result.state().nameTable().unsignedWord(0xF00));
 }
 @Test void secondLongUnknownRetainsTheFirstActualPlaneLongAndTheInput(){
  byte[] prefix=new byte[8188];prefix[8184]=0x12;prefix[8185]=0x34;prefix[8186]=0x56;prefix[8187]=0x78;
  var bank=CdBlockDefinitionStaging.unknown().withCompletedPrefix(bytes(prefix));var before=plane();
  var fault=assertThrows(CdR11bForegroundPlane.InvalidPlane.class,()->draw(foreground(bank),before));
  assertEquals(8188,fault.byteOffset());assertEquals(0x1234,fault.completedPrefix().state().nameTable().unsignedWord(0xF00));assertEquals(0x5678,fault.completedPrefix().state().nameTable().unsignedWord(0xF02));assertEquals(0x5555,before.nameTable().unsignedWord(0xF00));
 }
 @Test void earlierImmutableReaderRetainsItsCapturedBankWhenAnotherValueIsDecoded(){
  var cleared=CdBlockDefinitionStaging.afterFileBufferClear();var owner=foreground(cleared);byte[] full=new byte[8192];full[8184]=0x12;full[8185]=0x34;
  var dirty=foreground(cleared.withCompletedPrefix(bytes(full)));assertEquals(0x1234,draw(dirty,plane()).state().nameTable().unsignedWord(0xF00));assertEquals(0,draw(owner,plane()).state().nameTable().unsignedWord(0xF00));
 }
 @Test void backgroundUsesTheSameExplicitKnowledgeWithoutASecondDrawAlgorithm(){
  byte[] blocks=new byte[6432],patterns=new byte[25600],chunks=new byte[61952],fg=new byte[338],bg=new byte[50];blocks[0]=0x12;blocks[1]=0x34;fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)1);Arrays.fill(bg,2,bg.length,(byte)1);
  var terrain=CdR11aTerrain.decode(blocks,patterns,chunks,fg,bg,new byte[900],new byte[256],new byte[4096],new byte[4096]);byte[] ptr={0,(byte)0xFF,(byte)0xF7,8,0,(byte)0xFF,(byte)0xF7,8,0,(byte)0xFF,(byte)0xF7,16,0,(byte)0xFF,(byte)0xF7,24};
  var raw=CdR11aBackgroundPlane.fromTables(terrain,bytes(new byte[130]),bytes(ptr));var camera=new CdR11aBackgroundPlane.Camera(0,0);var live=new CdR11aBackgroundPlane.LiveCameras(camera,camera,camera);var flags=new CdR11aBackgroundPlane.FlagWords(0,0,0);
  var unknown=raw.withBlockDefinitions(CdBlockDefinitionStaging.unknown());var fault=assertThrows(CdR11aBackgroundPlane.InvalidPlane.class,()->unknown.initialize(plane(),live,flags));assertEquals(CdR11aBackgroundPlane.Boundary.BLOCK_READ,fault.boundary());assertInstanceOf(CdBlockDefinitionStaging.UnknownRead.class,fault.getCause());
  var result=raw.withBlockDefinitions(CdBlockDefinitionStaging.afterFileBufferClear()).initialize(plane(),live,flags);assertEquals(0,result.state().nameTable().unsignedWord(0xF00));
 }
 @Test void backgroundNormalSecondLongUnknownRetainsItsFirstLong(){
  var before=plane();var bank=CdBlockDefinitionStaging.unknown().withCompletedPrefix(bytes(new byte[]{0x12,0x34,0x56,0x78}));
  var fault=backgroundFault(backgroundFixture(bank,0,false),before,false);var reached=fault.completedPrefix().orElseThrow();
  assertEquals(CdR11aBackgroundPlane.Boundary.BLOCK_READ,fault.boundary());assertEquals(4,fault.byteOffset());assertInstanceOf(CdBlockDefinitionStaging.UnknownRead.class,fault.getCause());
  assertEquals(0x1234,reached.state().nameTable().unsignedWord(0xF00));assertEquals(0x5678,reached.state().nameTable().unsignedWord(0xF02));assertEquals(0x5555,reached.state().nameTable().unsignedWord(0xF80));
  assertTrue(reached.blocks().isEmpty());assertEquals(1,reached.sections().size());assertEquals(new CdR11aBackgroundPlane.FlagWords(0x0305,0x1177,0x2288),reached.flags());assertEquals(0x5555,before.nameTable().unsignedWord(0xF00));
 }
 @Test void backgroundXSecondLongUnknownRetainsTheNativeSwappedXorLong(){
  var before=plane();var bank=CdBlockDefinitionStaging.unknown().withCompletedPrefix(bytes(new byte[]{0x12,0x34,0x56,0x78}));
  var fault=backgroundFault(backgroundFixture(bank,8,false),before,false);var reached=fault.completedPrefix().orElseThrow();
  assertEquals(4,fault.byteOffset());assertEquals(0x5E78,reached.state().nameTable().unsignedWord(0xF00));assertEquals(0x1A34,reached.state().nameTable().unsignedWord(0xF02));assertEquals(0x5555,reached.state().nameTable().unsignedWord(0xF80));assertEquals(0x5555,before.nameTable().unsignedWord(0xF00));
 }
 @Test void backgroundYAndXyDoNotWriteBeforeTheirSecondSourceLong(){
  for(int flip:new int[]{16,24}){var before=plane();var bank=CdBlockDefinitionStaging.unknown().withCompletedPrefix(bytes(new byte[]{0x12,0x34,0x56,0x78}));
   var fault=backgroundFault(backgroundFixture(bank,flip,false),before,false);var reached=fault.completedPrefix().orElseThrow();
   assertEquals(4,fault.byteOffset());assertSame(before,reached.state());assertArrayEquals(before.nameTable().copy(),reached.state().nameTable().copy());assertTrue(reached.blocks().isEmpty());
  }
 }
 @Test void backgroundFaultRetainsOnlyTheAlreadyConsumedScrollFlagBit(){
  var before=plane();var bank=CdBlockDefinitionStaging.unknown().withCompletedPrefix(bytes(new byte[]{0x12,0x34,0x56,0x78}));
  var fault=backgroundFault(backgroundFixture(bank,0,false),before,true);var reached=fault.completedPrefix().orElseThrow();
  assertEquals(new CdR11aBackgroundPlane.FlagWords(0x0205,0x1177,0x2288),reached.flags());assertEquals(0x1234,reached.state().nameTable().unsignedWord(0xF00));assertEquals(0x5555,before.nameTable().unsignedWord(0xF00));
 }
 @Test void backgroundLateBlockFaultKeepsEarlierBlocksAndItsOwnFirstLong(){
  var bank=CdBlockDefinitionStaging.unknown().withCompletedPrefix(bytes(new byte[]{0x11,0x11,0x22,0x22,0x33,0x33,0x44,0x44,(byte)0xAA,(byte)0xAA,(byte)0xBB,(byte)0xBB}));var before=plane();
  var fault=backgroundFault(backgroundFixture(bank,0,true),before,false);var reached=fault.completedPrefix().orElseThrow();
  assertEquals(12,fault.byteOffset());assertEquals(1,reached.blocks().size());assertEquals(0x1111,reached.state().nameTable().unsignedWord(0xF00));assertEquals(0x3333,reached.state().nameTable().unsignedWord(0xF80));assertEquals(0xAAAA,reached.state().nameTable().unsignedWord(0xF04));assertEquals(0xBBBB,reached.state().nameTable().unsignedWord(0xF06));assertEquals(0x5555,reached.state().nameTable().unsignedWord(0xF84));assertEquals(0x5555,before.nameTable().unsignedWord(0xF00));
 }
 // Explicit pure source fixtures. These bank values never enter an original World or a restore owner.
 private static CdR11aBackgroundPlane backgroundFixture(CdBlockDefinitionStaging bank,int high,boolean secondBlock){
  byte[] blocks=new byte[6432],patterns=new byte[25600],chunks=new byte[61952],fg=new byte[338],bg=new byte[50];blocks[0]=0x22;blocks[1]=0x22;fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)1);Arrays.fill(bg,2,bg.length,(byte)1);
  for(int at=0;at<512;at+=2){chunks[at]=(byte)high;if(secondBlock&&(at%32)==2)chunks[at+1]=1;}
  var terrain=CdR11aTerrain.decode(blocks,patterns,chunks,fg,bg,new byte[900],new byte[256],new byte[4096],new byte[4096]);byte[] ptr={0,(byte)0xFF,(byte)0xF7,8,0,(byte)0xFF,(byte)0xF7,8,0,(byte)0xFF,(byte)0xF7,16,0,(byte)0xFF,(byte)0xF7,24};
  return CdR11aBackgroundPlane.fromTables(terrain,bytes(new byte[130]),bytes(ptr)).withBlockDefinitions(bank);
 }
 private static CdR11aBackgroundPlane.InvalidPlane backgroundFault(CdR11aBackgroundPlane owner,CdR11aBackgroundPlane.State before,boolean incremental){
  var c=new CdR11aBackgroundPlane.Camera(0,0);var live=new CdR11aBackgroundPlane.LiveCameras(c,c,c);var flags=new CdR11aBackgroundPlane.FlagWords(0x0305,0x1177,0x2288);
  return assertThrows(CdR11aBackgroundPlane.InvalidPlane.class,()->{if(incremental)owner.incremental(before,live,new CdR11aBackgroundPlane.CopiedCameras(c,c,c),flags);else owner.initialize(before,live,flags);});
 }
}
