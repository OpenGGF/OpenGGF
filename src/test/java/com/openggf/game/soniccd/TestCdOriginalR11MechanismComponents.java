package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
/** Source-only component faults. These manually supplied slots never certify public controller progress.
 * All Java/discovery/runtime UNRUN. The separate World tests require original NEW_GAME return/replay.
 */
class TestCdOriginalR11MechanismComponents {
 static final AssertionError STOP=new AssertionError("reached native primitive");
 static class Faults implements CdOriginalMechanismProtocol.Observer {
  final List<CdOriginalMechanismProtocol.Call> calls=new ArrayList<>();boolean after;Object stopPayload;
  public void before(CdOriginalMechanismProtocol.Call c){calls.add(c);if(!after&&(stopPayload==null||c.payload()==stopPayload))throw STOP;}
  public void after(CdOriginalMechanismProtocol.Call c){if(after)throw STOP;}
  public void failed(CdOriginalMechanismProtocol.Call c,Throwable original){assertSame(STOP,original);}
 }
 static CdR11aAssets assets()throws Exception{return TestCdOriginalBootPrefix.source().assets();}
 static CdObjectPlacementRuntime placement(CdR11aAssets a,int id){var p=CdObjectPlacementRuntime.fromAssets(a);p.pool().write(p.pool().state(43).moveByte(ID,id).moveWord(X,0x100).moveWord(Y,0x100));return p;}
 @Test void discFirstNativeStoreFaultRetainsExactHeaderPrefix()throws Exception{
  var a=assets();var p=placement(a,9);var f=new Faults();f.after=true;
  var body=CdSpinningDisc.fromOriginalPresentMechanism(p,a,new CdPlayerMath(a.player()),CdSolidObject.fromAssets(a),c->fail(),new CdSpinningDisc.PlayerOwner(){public CdSpinningDisc.PlayerCut read(){throw STOP;}public void commit(CdSpinningDisc.PlayerCall c){fail();}},c->fail(),(pool,id)->fail(),f);
  assertSame(STOP,assertThrows(AssertionError.class,()->body.run(43,new CdSpinningDisc.Globals(0,0x100,1,0))));assertEquals(1,f.calls.size());assertEquals(f.calls.getFirst().output(),p.pool().state(43));assertFalse(body.isCurrentOriginalOperation(f.calls.getFirst()));assertEquals(0x2095C6,p.pool().state(43).longBits(CdObjectState.LongField.MAPPINGS));
 }
 @Test void movingSpringChildIsBornBeforeFirstFloorReadWithDirtyBytesPreserved()throws Exception{
  var a=assets();var p=placement(a,15);var dirty=p.pool().state(32).moveByte(SUBTYPE2,0x67).moveByte(SAVED_FLAGS_ID,0x45).moveWord(TILE,0x1234);p.pool().write(dirty);var f=new Faults();f.stopPayload=CdOriginalMechanismProtocol.NativeSite.FLOOR_OWNER;
  var body=CdMovingSpring.fromOriginalPresentMechanism(p,a,new CdPlayerTerrain(a.terrain()),c->fail(),c->{fail();return Optional.empty();},c->fail(),f);
  assertSame(STOP,assertThrows(AssertionError.class,()->body.run(43,new CdMovingSpring.Globals(0x100,0))));var child=p.pool().state(32);
  var expected=dirty.moveByte(ID,10).moveWord(X,0x100).moveWord(Y,0xF0).moveByte(SPRING_PARENT_Y_OFFSET,0xF0).moveWord(SPRING_PARENT,p.pool().state(43).identity().nativeAddress24()).moveByte(SUBTYPE,p.pool().state(43).subtype());assertEquals(expected,child);assertEquals(2,p.pool().state(43).routine());assertFalse(body.isCurrentOriginalOperation(f.calls.getLast()));
 }
 @Test void movingSpringFailedAllocationDeletesActualParentAndCreatesNoChild()throws Exception{
  var a=assets();var p=placement(a,15);for(int w=32;w<128;w++)if(w!=43)p.pool().write(p.pool().state(w).moveByte(ID,0x33));
  var body=CdMovingSpring.fromOriginalPresentMechanism(p,a,new CdPlayerTerrain(a.terrain()),c->fail(),c->{fail();return Optional.empty();},c->fail(),quiet());body.run(43,new CdMovingSpring.Globals(0x100,0));assertEquals(0,p.pool().state(43).id());for(int w=32;w<128;w++)if(w!=43)assertEquals(0x33,p.pool().state(w).id());
 }
 @Test void springboardFirstMappingStoreFaultDoesNotInitializeRoutine()throws Exception{
  var a=assets();var p=placement(a,40);var f=new Faults();f.after=true;var body=CdSpringboard.fromOriginalPresentMechanism(p,a,c->fail(),c->fail(),f);
  try(var frame=CdPlayerFrameRuntime.terrainOnlyDevelopment(a)){assertSame(STOP,assertThrows(AssertionError.class,()->body.run(43,new CdSpringboard.Globals(1,0,0x100),new CdSpringboard.PrimaryInputs(frame.state(),frame.environment()))));}
  assertEquals(0,p.pool().state(43).routine());assertEquals(0x20C0C6,p.pool().state(43).longBits(CdObjectState.LongField.MAPPINGS));assertFalse(body.isCurrentOriginalOperation(f.calls.getFirst()));
 }
 @Test void holeZeroControlDoesNotReadPositionsOrPlayFm()throws Exception{
  var a=assets();var p=placement(a,49);p.pool().write(p.pool().state(43).moveByte(ROUTINE,2));var reads=new ArrayList<CdSonicHole.PlayerField>();var body=CdSonicHole.fromOriginalPresentMechanism(p,a,(field,pool,source)->{reads.add(field);return 0;},(fm,pool,source)->fail(),(pool,id)->fail(),quiet());body.run(43,new CdSonicHole.Globals(1,0,0x100));assertEquals(List.of(CdSonicHole.PlayerField.CONTROL_2C),reads);assertEquals(2,p.pool().state(43).routine());
 }
 @Test void holeFmFailureKeepsDisplayBitButNotRoutineAdvanceOrDraw()throws Exception{
  var a=assets();var p=placement(a,49);p.pool().write(p.pool().state(43).moveByte(ROUTINE,2).moveByte(SAVED_FLAGS_ID,1));var reads=new ArrayList<CdSonicHole.PlayerField>();var body=CdSonicHole.fromOriginalPresentMechanism(p,a,(field,pool,source)->{reads.add(field);return field==CdSonicHole.PlayerField.CONTROL_2C?1:0x100;},(fm,pool,source)->{assertEquals(CdSonicHole.FmSound.FM_A3,fm);throw STOP;},(pool,id)->fail(),quiet());assertSame(STOP,assertThrows(AssertionError.class,()->body.run(43,new CdSonicHole.Globals(1,0,0x100))));assertEquals(List.of(CdSonicHole.PlayerField.CONTROL_2C,CdSonicHole.PlayerField.X_WORD,CdSonicHole.PlayerField.Y_WORD),reads);assertEquals(2,p.pool().state(43).routine());assertTrue((p.savedFlag(1,1)&64)!=0);
 }
 @Test void wallBreakFmFailurePreservesBreakAndSavedVelocitiesBeforeHalving()throws Exception{
  var a=assets();var p=placement(a,54);p.pool().write(p.pool().state(43).moveByte(ROUTINE,2).moveByte(COLLISION_STATUS,1));var body=CdBreakableWall.fromOriginalPresentMechanism(p,a,CdSolidObject.fromAssets(a),c->fail(),c->fail(),(pool,id)->fail(),c->{assertEquals(0xB0,c.nativeId());throw STOP;},quiet());
  try(var frame=CdPlayerFrameRuntime.terrainOnlyDevelopment(a)){var s=frame.state();var e=frame.environment();var h=s.header();var g=e.movement().globals();var player=new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());var in=new CdBreakableWall.Inputs(player,new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle()));assertSame(STOP,assertThrows(AssertionError.class,()->body.run(43,new CdBreakableWall.Globals(1,0,0x100),in)));assertEquals(4,p.pool().state(43).routine());assertEquals(0,p.pool().state(43).unsignedByte(COLLISION_STATUS));assertEquals(s.motion().xVelocity()&65535,p.pool().state(43).word(TITLE_CARD_DEST_X));assertEquals(s.motion().yVelocity()&65535,p.pool().state(43).word(TITLE_CARD_DEST_Y));}
 }
 @Test void authenticatedFactoriesRejectConstructedPlacementBeforeCallbacks()throws Exception{
  var a=assets();var p=new CdObjectPlacementRuntime(a.placements());
  assertThrows(IllegalArgumentException.class,()->CdSpinningDisc.fromOriginalPresentMechanism(p,a,null,null,null,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdMovingSpring.fromOriginalPresentMechanism(p,a,null,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdSpringboard.fromOriginalPresentMechanism(p,a,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdSonicHole.fromOriginalPresentMechanism(p,a,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdBreakableWall.fromOriginalPresentMechanism(p,a,null,null,null,null,null,null));
 }
 @Test void mechanismDataRetainsIndependentOriginalRangesAndHashes()throws Exception{
  var o=assets().openingObjects();
  var rows=List.of(
   new Object[]{CdR11aObjectAssets.Table.MECHANISM_WHEEL_ANIMATIONS,40040,6,"e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a"},
   new Object[]{CdR11aObjectAssets.Table.MECHANISM_WHEEL_MAPPINGS,40046,16,"f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d"},
   new Object[]{CdR11aObjectAssets.Table.MECHANISM_BOARD_HITBOXES,48578,32,"48c0ebee94aed675266f2c3b8ffbb5f609aeac6e40c7d72d792ef851c7e2735c"},
   new Object[]{CdR11aObjectAssets.Table.MECHANISM_BOARD_ANIMATIONS,49296,54,"66b7d5d2f75e5d7ff1977d7d3f1cc4ec7635c1f1db4da5b7568f72ea142067ba"},
   new Object[]{CdR11aObjectAssets.Table.MECHANISM_BOARD_MAPPINGS,49350,84,"a5b367d30a33cd6d503930c42c8ac86f8155235475b57094740a62ee242b0a3f"},
   new Object[]{CdR11aObjectAssets.Table.MECHANISM_WALL_MAPPINGS,63984,312,"c561a76556cc21a46155e135205ab7c39cc8b007b9730c66e0a34a707d671226"},
   new Object[]{CdR11aObjectAssets.Table.MECHANISM_WALL_FRAMES,64296,64,"9ea247159cb7c1830c8392d11b18cd1ce1de576fa33e4451179201fbe800ac7f"},
   new Object[]{CdR11aObjectAssets.Table.MECHANISM_WALL_DELTAS,64360,24,"86444e752d8300afadd9aa1b5e2fa7a3252febf156c023d05f7bcf152f706e94"},
   new Object[]{CdR11aObjectAssets.Table.MECHANISM_WALL_SPEEDS,64384,48,"7a1571aadadf44de0c59b260de3ec3464b1b3676a4ea2cef273b069284032b31"}
  );for(var row:rows){var v=o.view((CdR11aObjectAssets.Table)row[0]);assertEquals(row[1],v.range().fileOffset());assertEquals(row[2],v.bytes().length());assertEquals(row[3],HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(v.bytes().copy())));}
  assertEquals(2,o.mechanismWheel().mappingFrames().size());assertEquals(6,o.mechanismBoard().mappingFrames().size());assertEquals(14,o.mechanismWall().mappingFrames().size());
 }
 static CdOriginalMechanismProtocol.Observer quiet(){return new CdOriginalMechanismProtocol.Observer(){public void before(CdOriginalMechanismProtocol.Call c){}public void after(CdOriginalMechanismProtocol.Call c){}public void failed(CdOriginalMechanismProtocol.Call c,Throwable t){}};}
}
