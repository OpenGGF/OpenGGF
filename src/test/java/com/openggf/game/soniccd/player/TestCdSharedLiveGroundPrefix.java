package com.openggf.game.soniccd.player;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Constructed native-format controls, never authenticated World/route terrain.
 * Native flagged chunk121 ($F9), with actor sprite bit6, selects unavailable chunk122 only at the reached query. */
class TestCdSharedLiveGroundPrefix {
 static final class F {
  final byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256],heights=new byte[4096],widths=new byte[4096];
  F(){fg[0]=41;fg[1]=7;fg[2]=2;bg[0]=5;bg[1]=7;}
  void fill(int word){for(int i=512;i<1024;i+=2){chunks[i]=(byte)(word>>>8);chunks[i+1]=(byte)word;}}
  void shape(int angle,int height){ids[1]=7;angles[7]=(byte)angle;Arrays.fill(heights,7*16,8*16,(byte)height);Arrays.fill(widths,7*16,8*16,(byte)height);}
  CdPlayerTerrain terrain(){return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths));}
  CdPlayerGroundCollision ground(){return new CdPlayerGroundCollision(terrain());}
 }
 static final class W implements CdPlayerGroundCollision.AngleWrites {
  int primary=0x12,secondary=0x34;final List<String> writes=new ArrayList<>();
  public void primary(int v){primary=v;writes.add("p:"+v);}public void secondary(int v){secondary=v;writes.add("s:"+v);}
 }
 private CdPlayerGroundCollision.Input input(int x,int y,int flags,int xr){return new CdPlayerGroundCollision.Input(new CdPlayerGroundCollision.State((x<<16)|0x1357,(y<<16)|0xABCD,0,flags,0x57),xr,19,0x40,0);}
 @Test void firstUnavailableTerrainReadRetainsBothThreeSeeds(){
  var f=new F();f.fg[2]=(byte)0xF9;var w=new W();assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,()->f.ground().groundWithPrefix(input(35,45,0,9),w));
  assertEquals(3,w.primary);assertEquals(3,w.secondary);assertEquals(List.of("p:3","s:3"),w.writes);
 }
 @Test void primaryTwentyThenSecondaryFailureRetainsTwentyAndThree(){
  var f=new F();f.fg[2]=(byte)0xF9;f.fg[3]=2;f.fill(0x2001);f.shape(0x20,8);var w=new W();
  assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,()->f.ground().groundWithPrefix(input(256,45,0,9),w));assertEquals(0x20,w.primary);assertEquals(3,w.secondary);assertEquals(List.of("p:3","s:3","p:32"),w.writes);
 }
 @Test void intraProbeRawAngleSurvivesItsLaterExtensionReadFailure(){
  var f=new F();f.fg[44]=(byte)0xF9;f.fill(0x2001);f.shape(0x20,0);var w=new W();
  var fault=assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,()->f.ground().groundWithPrefix(input(35,236,0,0),w));
  assertEquals(0x20,w.primary);assertEquals(3,w.secondary);assertSame(fault.originalException(),fault.getCause());assertTrue(w.writes.contains("p:32"));
 }
 @Test void balanceZeroIsPublishedBeforeItsFirstUnavailableRead(){
  var f=new F();f.fg[2]=(byte)0xF9;var writes=new ArrayList<Integer>();
  assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,()->f.terrain().floorWithPrimaryZeroPrefix(35,64,13,0,16,0x40,writes::add));assertEquals(List.of(0),writes);
 }
 @Test void balanceRawAngleIsRetainedBeforeExtensionFault(){
  var f=new F();f.fg[44]=(byte)0xF9;f.fill(0x2001);f.shape(0x20,0);var writes=new ArrayList<Integer>();
  assertThrows(CdPlayerTerrain.UnownedTerrainRead.class,()->f.terrain().floorWithPrimaryZeroPrefix(35,255,13,0,16,0x40,writes::add));assertEquals(List.of(0,0x20),writes);
 }
 @Test void objectSupportedBypassPublishesZeroZeroWithoutAnyTerrainRead(){
  var f=new F();f.fg[2]=(byte)0xF9;var w=new W();var in=input(35,45,8,9);var out=f.ground().groundWithPrefix(in,w);
  assertEquals(in.state(),out.state());assertEquals(0,w.primary);assertEquals(0,w.secondary);assertEquals(List.of("p:0","s:0"),w.writes);
 }
 @Test void hookFailureKeepsExactThrowableAndNeverExecutesNextSeedOrProbe(){
  var f=new F();var fault=new IllegalArgumentException("actual primary angle writer");var writes=new ArrayList<Integer>();
  assertSame(fault,assertThrows(IllegalArgumentException.class,()->f.ground().groundWithPrefix(input(35,45,0,9),new CdPlayerGroundCollision.AngleWrites(){public void primary(int v){writes.add(v);throw fault;}public void secondary(int v){fail("after first fault");}})));assertEquals(List.of(3),writes);
 }
 @Test void pureFinalResultsAndInputsRemainExactAcrossAllQuadrants(){
  var f=new F();f.fill(0x6001);f.shape(0x20,8);var owner=f.ground();
  for(int angle:new int[]{0,0x40,0x80,0xC0}){var base=input(48,48,0,9);var m=base.state();var in=new CdPlayerGroundCollision.Input(new CdPlayerGroundCollision.State(m.xPosition(),m.yPosition(),angle,m.flags(),m.previousAnimation()),9,19,0,0);var w=new W();assertEquals(owner.ground(in),owner.groundWithPrefix(in,w));assertEquals(angle,in.state().angle());}
 }
 @Test void invalidBalanceArgumentsCannotPublishASeed(){var writes=new ArrayList<Integer>();assertThrows(IllegalArgumentException.class,()->new F().terrain().floorWithPrimaryZeroPrefix(0,0,16,0,16,0,writes::add));assertTrue(writes.isEmpty());}
}
