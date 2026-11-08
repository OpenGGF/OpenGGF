package com.openggf.game.sonic2.titlecard;
import java.nio.file.*;
import java.util.*;
@com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
class TestObj34NativeRoutines {
 @org.junit.jupiter.api.Test
 void initOperandsComeFromRev01() throws Exception {
  var rom=com.openggf.tests.TestEnvironment.currentRom();
  Obj34 o=new Obj34();o.init(0,0x12,false);o.tick();
  for(int i=0;i<6;i++) {
   byte[] data=rom.readBytes(0x13cd4+i*10,10);
   org.junit.jupiter.api.Assertions.assertEquals(data[0]&255,o.parts[i].routine);
   org.junit.jupiter.api.Assertions.assertEquals(((data[3]&255)-(i==0 || i==3 ? 0 : 1))<<8,o.parts[i].duration);
   org.junit.jupiter.api.Assertions.assertEquals(((data[4]&255)<<8)|(data[5]&255),o.parts[i].source);
   org.junit.jupiter.api.Assertions.assertEquals(((data[6]&255)<<8)|(data[7]&255),o.parts[i].target);
   org.junit.jupiter.api.Assertions.assertEquals(((data[8]&255)<<8)|(data[9]&255),o.parts[i].y);
  }
 }
 @org.junit.jupiter.api.Test
 void matchesNativePerFrameEntryLeaveAndTail() throws Exception {

  Obj34 o=new Obj34();o.init(0,0x12,false);
  var rows=loadRows();
  int comparisons=0;
  for(int f=760;f<=955;f++) {
   // The load/PLC wait runs title object passes through arrival; LoadZoneTiles then holds.
   // The standalone observer checks entry and leave/tail windows separately.
   if(f>=761 && (f<=805 || f>=874)) { o.consumeFills(); o.tick(); }
   if(f==874)o.armLeave();
   if(f==899)o.armTail();
   if(f>805 && f<874)continue;
   for(String[] a:rows.get(f)) {
    int i=Integer.parseInt(a[1]);Obj34.Part p=o.parts[i];
    if((Integer.parseInt(a[2])==52)!=p.alive) throw new AssertionError("alive "+f+" "+i);

    int[] actual={p.routine,p.x,p.y,p.duration,p.target,p.source,p.location,referenceWord(p.dest),referenceWord(p.dest2),p.split};
    for(int j=0;j<actual.length;j++)if(actual[j]!=Integer.parseInt(a[j+3])) throw new AssertionError("frame "+f+" part "+i+" field "+j+" expected "+a[j+3]+" got "+actual[j]);
    org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(a[14]),p.frame,"mapping "+f+" "+i);
    org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(a[15]),p.width,"width "+f+" "+i);
    org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(a[13]),i==0 && o.leave ? 65535 : 0,"leave "+f+" "+i);
    comparisons++;
   }
  }
  org.junit.jupiter.api.Assertions.assertEquals(768,comparisons);
 }

 @org.junit.jupiter.api.Test
 void matchesColdTwoPlayerEntryLeaveAndTailEveryFrame() throws Exception {
  Obj34 o=new Obj34();o.init(0,0x12,true);
  var stream=getClass().getResourceAsStream("/sonic2/titlecard/ehz1-2p-obj34-native.csv.gz");
  java.util.List<String> lines;
  try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(stream)))) {
   lines=reader.lines().skip(1).toList();
  }
  var exported=new StringBuilder("frame,part,routine,x,y,duration,target,source,location,dest,dest2,split\n");
  for(String line:lines) {
   String[] a=line.split(",");int frame=Integer.parseInt(a[0]),i=Integer.parseInt(a[1]);
   if(i==0) {
    o.consumeFills();
    // Explicit native load boundaries: comparison rows never drive the owner.
    if(frame>=1185 && (frame<=1229 || frame>=1304)) o.tick();
    if(frame==1304)o.armLeave();
    if(frame==1329)o.armTail();
   }
   Obj34.Part p=o.parts[i];
   if(frame==1184 && i==0) org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
     ()->org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(a[4]),p.x+1),"negative position control");
   org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(a[2])==52,p.alive,"alive "+frame+" "+i);
   int[] actual={p.routine,p.x,p.y,p.duration,p.target,p.source,p.location,referenceWord(p.dest),referenceWord(p.dest2),p.split};
   for(int j=0;j<actual.length;j++) org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(a[j+3]),actual[j],"frame "+frame+" part "+i+" field "+j);
   org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(a[14]),p.frame,"mapping "+frame+" "+i);
   org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(a[15]),p.width,"width "+frame+" "+i);
   org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(a[13]),i==0 && o.leave ? 65535 : 0,"leave "+frame+" "+i);
   exported.append(frame).append(',').append(i);
   for(int value:actual)exported.append(',').append(value);
   exported.append('\n');
  }
  Files.createDirectories(Path.of("target/obj34-2p-pixels"));
  Files.writeString(Path.of("target/obj34-2p-pixels/engine-parts.csv"),exported);
 }
 /** Comparison-only reconstruction of native RAM words; runtime targets are semantic. */
 private static int referenceWord(Obj34.Target target) {
  if(target==null)return 0;
  if(target.revealY()!=null)return target.revealY()&65535;
  var d=target.destination();
  return (d.plane()==TitleCardNativeDisplayAccess.Foreground.PLAYER_ONE?0xc000:0xa000)
    +d.row()*128+d.column()*2;
 }
 private Map<Integer,List<String[]>> loadRows() throws Exception {
  Map<Integer,List<String[]>> rows=new TreeMap<>();
  var stream=getClass().getResourceAsStream("/sonic2/titlecard/ehz1-obj34-native.csv.gz");
  java.util.List<String> lines;
  try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(stream)))) {
   lines=reader.lines().skip(1).toList();
  }
  for(String line:lines) {
   String[] a=line.split(",");rows.computeIfAbsent(Integer.parseInt(a[0]),k->new ArrayList<>()).add(a);
  }
  return rows;
 }
 @org.junit.jupiter.api.Test
 void managerDispatchesNativeLeaveAndArmsTheTailInTheDeletingIteration() throws Exception {
  var rows=loadRows();
  var manager=new TitleCardManager(()->null,()->false);
  manager.beginOmittedPresentationLeave(0,0);
  manager.completeLockedIteration();
  for(int f=875;f<=899;f++) {
   manager.update();manager.completeLockedIteration();
   var actual=TitleCardNativeDisplayAccess.read(manager);
   for(String[] row:rows.get(f)) {
    int index=Integer.parseInt(row[1]);var part=actual.parts().get(index);
    org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(row[2])==52,part.alive(),"alive "+f+" "+index);
    org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(row[3]),part.routine(),"routine "+f+" "+index);
    org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(row[4]),part.x(),"X "+f+" "+index);
    org.junit.jupiter.api.Assertions.assertEquals(Integer.parseInt(row[6]),part.duration(),"duration "+f+" "+index);
   }
   org.junit.jupiter.api.Assertions.assertEquals(f==899,manager.shouldReleaseControl());
  }
 }
 @org.junit.jupiter.api.Test
 void publicationRetainsThePrecedingObjectPassAndRewindsIt() throws Exception {
  Obj34 o=new Obj34();o.init(0,0x12,false);
  for(int i=0;i<35;i++){o.consumeFills();o.tick();}
  var expected=loadRows().get(794).get(0);
  int publishedX=Integer.parseInt(expected[4]);
  org.junit.jupiter.api.Assertions.assertEquals(publishedX,o.publishedParts.get(0).x());
  org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
    ()->org.junit.jupiter.api.Assertions.assertEquals(publishedX,o.parts[0].x),
    "negative control: current RAM is not the published sprite position");
  var snapshot=o.capture();o.consumeFills();o.tick();o.restore(snapshot);
  org.junit.jupiter.api.Assertions.assertEquals(snapshot,o.capture());
 }
 @org.junit.jupiter.api.Test
 void backgroundDestinationsUseBothRev01ForegroundBases() throws Exception {
  var rom=com.openggf.tests.TestEnvironment.currentRom();
  byte[] code=rom.readBytes(0x13d22,0x36);
  int base1=((code[0x1c]&255)<<8)|(code[0x1d]&255);
  int base2=((code[0x2a]&255)<<8)|(code[0x2b]&255);
  Obj34 o=new Obj34();o.init(0,0x12,true);
  for(int row=0;row<32;row++) {
   o.consumeFills();o.tick();
   org.junit.jupiter.api.Assertions.assertEquals(base1+row*128,referenceWord(o.parts[3].dest));
   org.junit.jupiter.api.Assertions.assertEquals(base2+row*128,referenceWord(o.parts[3].dest2));
  }
 }
 @org.junit.jupiter.api.Test
 void twoPlayerLeavePublishesBothTablesAndP2FirstWorldRowRequests() throws Exception {
  Obj34 o=new Obj34();o.init(0,0x12,true);o.settle();o.armLeave();
  byte[] fillPair=com.openggf.tests.TestEnvironment.currentRom().readBytes(0x155a4,4);
  int blueWord=((fillPair[0]&255)<<8)|(fillPair[1]&255);
  for(int pass=1;pass<=25;pass++) {
   o.consumeFills();o.tick();
   var fills=o.pendingFills();var reveals=o.pendingRowReveals();
   if(pass==1) {
    org.junit.jupiter.api.Assertions.assertEquals(4,fills.size());
    org.junit.jupiter.api.Assertions.assertEquals(10,fills.get(0).destination().column());
    org.junit.jupiter.api.Assertions.assertEquals(10,fills.get(0).rows());
    org.junit.jupiter.api.Assertions.assertEquals(blueWord,fills.get(0).tileWord());
    org.junit.jupiter.api.Assertions.assertEquals(TitleCardNativeDisplayAccess.Foreground.PLAYER_ONE,fills.get(0).destination().plane());
    org.junit.jupiter.api.Assertions.assertEquals(TitleCardNativeDisplayAccess.Foreground.PLAYER_TWO,fills.get(2).destination().plane());
   }
   if(pass==6) {
    org.junit.jupiter.api.Assertions.assertEquals(2,fills.size());
    org.junit.jupiter.api.Assertions.assertEquals(4,fills.get(0).rows());
    org.junit.jupiter.api.Assertions.assertEquals(4,fills.get(0).columns());
    org.junit.jupiter.api.Assertions.assertEquals(10,fills.get(0).destination().row());
   }
   if(pass>=17 && pass<=24) {
    org.junit.jupiter.api.Assertions.assertEquals(2,reveals.size());
    org.junit.jupiter.api.Assertions.assertEquals(TitleCardNativeDisplayAccess.Foreground.PLAYER_TWO,reveals.get(0).plane());
    org.junit.jupiter.api.Assertions.assertEquals(TitleCardNativeDisplayAccess.Foreground.PLAYER_ONE,reveals.get(1).plane());
    org.junit.jupiter.api.Assertions.assertEquals(208-(pass-17)*32,reveals.get(0).screenY());
    org.junit.jupiter.api.Assertions.assertEquals(2,reveals.get(0).blockRows());
    org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
      ()->org.junit.jupiter.api.Assertions.assertEquals(TitleCardNativeDisplayAccess.Foreground.PLAYER_ONE,reveals.get(0).plane()),
      "negative control: reversing the P2-first row publication is incorrect");
   } else org.junit.jupiter.api.Assertions.assertTrue(reveals.isEmpty());
  }
 }
 @org.junit.jupiter.api.Test
 void rewindBeforeTheInitPassReplaysTheSameNativeSlots() {
  Obj34 o=new Obj34();o.init(0,0x12,false);var allocated=o.capture();
  org.junit.jupiter.api.Assertions.assertEquals(0,o.parts[0].routine);
  org.junit.jupiter.api.Assertions.assertFalse(o.parts[1].alive);
  o.consumeFills();o.tick();var initialized=o.capture();
  o.restore(allocated);o.consumeFills();o.tick();
  org.junit.jupiter.api.Assertions.assertEquals(initialized,o.capture());
 }
 @org.junit.jupiter.api.Test
 void omittedTailRetainsTheExactNativePostLeaveFields() throws Exception {
  var manager=new TitleCardManager(()->null,()->false);
  manager.beginOmittedPresentationExitTail(0,0);
  var o=manager.obj34;
  org.junit.jupiter.api.Assertions.assertTrue(TitleCardNativeDisplayAccess.read(manager).leaving());
  for(String[] row:loadRows().get(899)) {
   int i=Integer.parseInt(row[1]);var p=o.parts[i];
   int[] actual={p.alive?52:0,p.routine,p.x,p.y,p.duration,p.target,p.source,
     p.location,referenceWord(p.dest),referenceWord(p.dest2),p.split,
     i==0 && o.leave?65535:0,p.frame,p.width};
   int[] expected=new int[14];for(int j=0;j<14;j++)expected[j]=Integer.parseInt(row[j+2]);
   org.junit.jupiter.api.Assertions.assertArrayEquals(expected,actual,"native post-leave part "+i);
  }
 }
 @org.junit.jupiter.api.Test
 void pendingFillsAndBothPlaneTablesSurviveRestore() {
  Obj34 o=new Obj34();o.init(0,0x12,true);
  for(int i=0;i<12;i++){o.consumeFills();o.tick();}
  var snapshot=o.capture();o.consumeFills();var after=o.capture();
  o.init(0,0x12,false);o.restore(snapshot);o.consumeFills();
  org.junit.jupiter.api.Assertions.assertEquals(after,o.capture());
  org.junit.jupiter.api.Assertions.assertEquals(after.foreground1(),after.foreground2());
  org.junit.jupiter.api.Assertions.assertTrue(after.foreground1().stream().anyMatch(w->w!=0));
  o.restore(snapshot);o.parts[Obj34.BACKGROUND].dest=null;o.consumeFills();
  org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
    ()->org.junit.jupiter.api.Assertions.assertEquals(after,o.capture()),
    "negative control: losing the pending P1 fill must fail restore equality");
  org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
    ()->org.junit.jupiter.api.Assertions.assertEquals(o.capture().foreground1(),o.capture().foreground2()),
    "negative control: a dropped P1 fill must distinguish the two planes");
 }
}
