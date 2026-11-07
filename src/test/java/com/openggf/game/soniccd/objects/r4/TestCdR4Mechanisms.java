package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerMath;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed component controls from literal N-source vectors, NOT World/Frame/route/pixel proof.
 * Ports deliberately expose helper call boundaries, dirty values and faults. No test publishes to a live pool. */
class TestCdR4Mechanisms {
    private static final int WHOLE=40;
    private static CdR4MechanismCut cut(int id) {
        var pool=new CdObjectPool(); pool.write(pool.state(WHOLE).moveByte(CdObjectState.ByteField.ID,id));
        return new CdR4MechanismCut(pool.capture(),new byte[64],new byte[64],new CdR4SwitchBank(new byte[32],-1));
    }
    private static class Ports implements CdR4SwitchDoor.Ports,CdR4Lifts.Ports,CdR4BreakGeyser.Ports,CdR4FanShooter.Ports,CdR4Prizes.Ports {
        final List<String> calls=new ArrayList<>(); boolean contact; int floor=1, era=1, act=0, camera=0, seconds=0, identity=7, ctrl=0, debug=0;
        int sine=0,cosine=256; String fail; int failureNumber=1; int hits;
        void call(String name) { calls.add(name); if(name.equals(fail) && ++hits==failureNumber) throw new IllegalStateException("injected "+name); }
        public int mapping(CdR4SwitchDoor.Mapping m) { call("map:"+m); return 0x210000; }
        public int mapping(CdR4Lifts.Mapping m) { call("map:"+m); return 0x210000; }
        public int mapping(CdR4BreakGeyser.Mapping m) { call("map:"+m); return 0x210000; }
        public int mapping(CdR4FanShooter.Mapping m) { call("map:"+m); return 0x210000; }
        public int mapping(CdR4Prizes.Mapping m) { call("map:"+m); return 0x210000; }
        public boolean solid(CdR4MechanismImage a,CdSolidObject.Entry e) { call("solid:"+e); return contact; }
        public void getOff(CdR4MechanismImage a) { call("getOff"); }
        public void draw(CdR4MechanismImage a) { call("draw:"+a.b(0)); }
        public void checkDespawn(CdR4MechanismImage a,int x) { call("check:"+x); }
        public void elevatorDespawn(CdR4MechanismImage a) { call("elevatorDespawn"); a.clear(); }
        public void despawn(CdR4MechanismImage a) { call("despawn"); a.clear(); }
        public void delete(CdR4MechanismImage a) { call("delete:"+a.wholeIndex()); a.clear(); }
        public void move(CdR4MechanismImage a) { call("move"); a.al(8,a.sw(16)<<8); a.al(12,a.sw(18)<<8); }
        public int floorDistance(CdR4MechanismImage a,boolean ceiling) { call(ceiling?"ceiling":"floor"); return floor; }
        public int switchBaseWord() { call("switchBase"); return 0xF7E0; } // actual declaration; source arithmetic independently checked
        public int cameraX() { call("camera"); return camera; }
        public int timeZone() { call("era"); return era; }
        public int act() { call("act"); return act; }
        public CdPlayerMath.SineCosine sineCosine(int angle) { call("sine:"+angle); return new CdPlayerMath.SineCosine(sine,cosine); }
        public void sound(int n) { call("sound:"+n); }
        public int debugModeByte() { call("debug"); return debug; }
        public int controllerWord() { call("controller"); return ctrl; }
        public int waterY() { call("water"); return 0x280; }
        public void paletteWord(int at,int value) { call("palette:"+at+":"+value); }
        public void animateGeyser(CdR4MechanismImage a) { call("geyserAnim"); }
        public int fanIdentityByte(int raw) { call("fanIdentity:"+raw); return identity; }
        public void animateFanAB(CdR4MechanismImage a) { call("fanAnim"); }
        public void animateMissile(CdR4MechanismImage a) { call("missileAnim"); }
        public int timeSecondsByte() { call("seconds"); return seconds; }
        public int bottomBound() { call("bottom"); return 0x700; }
        public void addPoints(int n) { call("points:"+n); }
        public void collectRing() { call("collectRing"); }
        public void animateRingPrize(CdR4MechanismImage a) { call("ringAnim"); }
    }
    private static void fill(CdR4MechanismCut c) { for(var a:c.dynamicImages()) a.b(0,1); }
    @Test void immutableSwitchBankHas32RawBytesAndLazyUnknowns() {
        byte[] bytes=new byte[32]; bytes[31]=(byte)0xA5; var bank=new CdR4SwitchBank(bytes,1<<31); bytes[31]=0;
        assertEquals(0xA5,bank.read(31)); assertThrows(IllegalStateException.class,()->bank.read(0));
        assertEquals(0x123&255,bank.write(0,0x123).read(0)); assertThrows(IndexOutOfBoundsException.class,()->bank.read(32));
        assertThrows(IllegalArgumentException.class,()->new CdR4SwitchBank(new byte[16],-1));
    }
    @Test void borrowedCutDoesNotPublishOrClearDirtyAllocation() {
        var pool=new CdObjectPool(); pool.write(pool.state(32).moveWord(CdObjectState.WordField.X_FRACTION,0xABCD)); var original=pool.capture();
        var c=new CdR4MechanismCut(original,new byte[64],new byte[64],new CdR4SwitchBank(new byte[32],0)); var child=c.allocate(c.actor(WHOLE),false);
        assertEquals(32,child.wholeIndex()); assertEquals(0xABCD,child.w(10)); child.b(0,0x42);
        assertEquals(0,pool.state(32).id()); assertEquals(0xABCD,pool.state(32).word(CdObjectState.WordField.X_FRACTION));
    }
    @Test void switchPressReleasePreservesOtherBitsAndLatchToggle() {
        var c=cut(0x27); var a=c.actor(WHOLE); var player=c.actor(0); a.w(12,100); a.b(1,128); player.b(0x16,19); c.switchByte(0,3);
        var p=new Ports(); p.contact=true; CdR4SwitchDoor.switchObject(c,WHOLE,p);
        assertEquals(0xE3,c.switches().read(0)); assertEquals(104,a.w(12)); assertEquals(6,a.b(0x16)); assertEquals(80,player.w(12));
        assertTrue(p.calls.indexOf("sound:191")<p.calls.indexOf("draw:39"));
        p.contact=false; CdR4SwitchDoor.switchObject(c,WHOLE,p);
        assertEquals(0x63,c.switches().read(0)); assertEquals(100,a.w(12)); assertEquals(10,a.b(0x16)); assertEquals(72,player.w(12));
    }
    @Test void switchUsesAlignmentZeroConditionAndFullPairRatherThanBooleans() {
        var c=cut(0x27); var a=c.actor(WHOLE); a.w(12,28); c.actor(0).b(0x16,19); var p=new Ports(); p.contact=true;
        CdR4SwitchDoor.switchObject(c,WHOLE,p); assertEquals(0,c.switches().read(0)); assertEquals(0,a.b(0x3F));
    }
    @Test void switchParentUsesRawLinkSignedOffsetsAndRetainsFraction() {
        var c=cut(0x27); var a=c.actor(WHOLE); a.b(0x24,2); a.w(0x3C,0xF7E0); a.w(0x34,0xDA40); a.b(0x38,0xFC); a.b(0x39,0x80); a.w(10,0xBEAD);
        c.actor(41).w(8,100); c.actor(41).w(12,200); CdR4SwitchDoor.switchObject(c,WHOLE,new Ports());
        assertEquals(96,a.w(8)); assertEquals(72,a.w(12)); assertEquals(0xBEAD,a.w(10)); assertEquals(0x6000,a.w(16));
    }
    @Test void switchFaultRetainsInitializationAndPrecedingSolidButNoDraw() {
        var pool=new CdObjectPool(); var c=new CdR4MechanismCut(pool.capture(),new byte[64],new byte[64],new CdR4SwitchBank(new byte[32],0)); var p=new Ports();
        assertThrows(IllegalStateException.class,()->CdR4SwitchDoor.switchObject(c,WHOLE,p));
        assertEquals(2,c.actor(WHOLE).b(0x24)); assertTrue(p.calls.contains("solid:SOLID_OBJECT")); assertFalse(p.calls.stream().anyMatch(x->x.startsWith("draw")));
    }
    @Test void switch2GeometryAndNonBooleanSubtype() {
        var c=cut(0x31); var a=c.actor(WHOLE); a.w(8,100); a.w(12,100); a.b(0x28,0x80); c.actor(0).w(8,100); c.actor(0).w(12,75); c.actor(0).b(0x16,19);
        CdR4SwitchDoor.switch2(c,WHOLE,new Ports()); assertEquals(0x81,a.b(0x28)); assertEquals(1,a.b(0x1A));
        c.actor(0).w(8,122); CdR4SwitchDoor.switch2(c,WHOLE,new Ports()); assertEquals(0x80,a.b(0x28)); assertEquals(1,a.b(0x1A));
    }
    @Test void doorInitializationDoesNotFallThroughAndAllFiveGeometries() {
        int[][] expected={{64,8,128,2},{64,8,128,2},{8,32,64,1},{8,64,128,0},{8,64,128,0}};
        for(int type=0;type<5;type++) { var c=cut(0x28); var a=c.actor(WHOLE); a.b(0x28,type<<4); var p=new Ports(); CdR4SwitchDoor.door(c,WHOLE,p);
            assertArrayEquals(expected[type],new int[]{a.b(0x19),a.b(0x16),a.b(0x3B),a.b(0x1A)}); assertFalse(p.calls.contains("solid:SOLID_OBJECT")); }
    }
    @Test void doorOpensInEightPixelStepsAndType4CanCloseWhileOthersLatch() {
        for(int type:new int[]{0,1,2,3,4}) { var c=cut(0x28); var a=c.actor(WHOLE); a.b(0x28,type<<4); a.w(8,100); a.w(12,200); a.w(10,0xABCD); a.w(14,0xFEDC); var p=new Ports();
            CdR4SwitchDoor.door(c,WHOLE,p); c.switchByte(0,128); CdR4SwitchDoor.door(c,WHOLE,p); assertEquals(8,a.b(0x3A));
            assertEquals(type==0?92:type==1?108:100,a.w(8)); assertEquals(type==2||type==4?192:type==3?208:200,a.w(12));
            c.switchByte(0,0); CdR4SwitchDoor.door(c,WHOLE,p); assertEquals(type==4?0:16,a.b(0x3A)); assertEquals(0xABCD,a.w(10)); assertEquals(0xFEDC,a.w(14)); }
    }
    @Test void bigDoorAnyNonzeroSwitchDeletesBeforeInitializationOrAllocation() {
        var c=cut(0x25); c.switchByte(0,1); var p=new Ports(); CdR4SwitchDoor.bigDoor(c,WHOLE,p); assertEquals(List.of("delete:40"),p.calls);
    }
    @Test void bigDoorPartialAfterAllocationCopiesFull64AndRetainsChildren() {
        var c=cut(0x25); var a=c.actor(WHOLE); a.w(8,200); a.w(10,0xABCD); a.b(0x3F,0xEE); var p=new Ports();
        CdR4SwitchDoor.bigDoor(c,WHOLE,p); c.switchByte(0,1); for(var s:c.dynamicImages()) if(s.wholeIndex()!=WHOLE && s.wholeIndex()!=41 && s.wholeIndex()!=42) s.b(0,1);
        CdR4SwitchDoor.bigDoor(c,WHOLE,p); assertEquals(0,a.b(0)); assertEquals(0x25,c.actor(41).b(0)); assertEquals(184,c.actor(41).w(8));
        assertEquals(0x10000,c.actor(41).l(0x2C)); assertEquals(-0x10000,c.actor(42).l(0x2C)); assertEquals(0xABCD,c.actor(41).w(10)); assertEquals(0xEE,c.actor(41).b(0x3F));
    }
    @Test void bigDoorCountdownMoves128TimesAndThenDeletes() {
        var c=cut(0x25); var a=c.actor(WHOLE); a.b(0x24,4); a.w(0x2A,1); a.l(0x2C,0x10000); var p=new Ports();
        CdR4SwitchDoor.bigDoor(c,WHOLE,p); assertEquals(0x10000,a.l(12)); CdR4SwitchDoor.bigDoor(c,WHOLE,p); assertEquals(0,a.b(0));
    }
    @Test void elevatorPresentAndFutureHaveLiteralSpeedCountAndNoEagerEraRead() {
        for(int era=0;era<3;era++) { var c=cut(0x26); var a=c.actor(WHOLE); var p=new Ports(); p.era=era; CdR4Lifts.elevator(c,WHOLE,p); assertFalse(p.calls.contains("era"));
            a.b(1,128); p.contact=true; CdR4Lifts.elevator(c,WHOLE,p); assertEquals(era==2?512:256,a.w(16)); assertEquals(era==2?80:160,a.w(0x3E));
            a.w(10,0x8000); a.w(14,0x1234); a.w(0x3E,1); CdR4Lifts.elevator(c,WHOLE,p); assertEquals(era==2?0x28000:0x18000,a.l(8)); assertEquals(0,a.w(16)); }
    }
    @Test void switchElevatorAllocationFailureLeavesFirstChildAndOuterTailRuns() {
        var c=cut(0x30); fill(c); c.actor(41).b(0,0); var a=c.actor(WHOLE); a.b(0,0x30); var p=new Ports(); CdR4Lifts.switchElevator(c,WHOLE,p);
        assertEquals(0,a.b(0)); assertEquals(0x30,c.actor(41).b(0)); assertEquals(0xDA00,c.actor(41).w(0x34));
        assertTrue(p.calls.indexOf("delete:40")<p.calls.indexOf("solid:SOLID_OBJECT")); assertTrue(p.calls.contains("draw:0"));
    }
    @Test void switchElevatorFailedCeilingRetainsTemporaryYAndNoMotion() {
        var c=cut(0x30); var a=c.actor(WHOLE); a.b(0x24,2); a.w(18,-256); a.l(12,0x2001234); var p=new Ports(); p.fail="ceiling";
        assertThrows(IllegalStateException.class,()->CdR4Lifts.switchElevator(c,WHOLE,p)); assertEquals(0x1C01234,a.l(12));
    }
    @Test void switchElevatorAttachmentsValidateNativeIdAndClampAccelerations() {
        var c=cut(0x30); var a=c.actor(WHOLE); a.b(0x24,2); a.w(0x30,0xDA40); a.w(0x34,0xDA80); a.w(18,254);
        c.actor(41).b(0,0x31); c.actor(42).b(0,0x30); var p=new Ports(); CdR4Lifts.switchElevator(c,WHOLE,p);
        assertEquals(256,a.w(18)); assertEquals(254,c.actor(42).w(18)); c.actor(41).b(0x28,1); a.w(18,-254); CdR4Lifts.switchElevator(c,WHOLE,p); assertEquals(0xFF00,a.w(18));
    }
    @Test void pressureElevatorHidesUntilPlayerThenFallsCompressesAndRisesAfterDraw() {
        var c=cut(0x49); var a=c.actor(WHOLE); a.w(12,100); c.actor(0).w(12,400); var p=new Ports();
        CdR4Lifts.pressureElevator(c,WHOLE,p); assertFalse(p.calls.stream().anyMatch(x->x.startsWith("draw"))); CdR4Lifts.pressureElevator(c,WHOLE,p); assertEquals(176,a.w(12));
        p.contact=true; CdR4Lifts.pressureElevator(c,WHOLE,p); assertEquals(8,a.b(0x24)); assertEquals(178,a.w(12)); assertEquals(404,c.actor(0).w(12)); assertTrue(p.calls.contains("getOff"));
        p.fail="draw:73"; assertThrows(IllegalStateException.class,()->CdR4Lifts.pressureElevator(c,WHOLE,p)); assertEquals(178,a.w(12)); assertEquals(10,a.b(0x24));
        p.fail=null; CdR4Lifts.pressureElevator(c,WHOLE,p); assertEquals(176,a.w(12));
    }
    @Test void platformChainCreatesEightDirtyChildrenInPhysicalOrderAndUsesHighAngleByte() {
        var c=cut(0x3E); var a=c.actor(WHOLE); a.w(8,100); a.w(12,200); a.b(0x28,128); a.b(0x3F,0x7F); c.actor(32).b(0x3F,0x7F); var p=new Ports();
        CdR4Lifts.platformChain(c,WHOLE,p); assertEquals(1,c.actor(32).b(0x3C)); assertEquals(8,c.actor(39).b(0x3C)); assertEquals(0x7F,c.actor(32).b(0x3F));
        assertEquals(0x217F,a.w(0x3E)); assertEquals(196,a.w(8)); assertTrue(p.calls.contains("sine:33"));
    }
    @Test void platformChainRepeatsAllEightAllocationAttemptsWhenFullAndLazySwitchGates() {
        var pool=new CdObjectPool(); var c=new CdR4MechanismCut(pool.capture(),new byte[64],new byte[64],new CdR4SwitchBank(new byte[32],0)); fill(c); var a=c.actor(WHOLE); a.b(0,0x3E); a.b(0x28,128); var p=new Ports();
        CdR4Lifts.platformChain(c,WHOLE,p); assertEquals(96,c.dynamicImages().stream().filter(x->x.b(0)!=0).count());
        a.b(0x28,0); assertThrows(IllegalStateException.class,()->CdR4Lifts.platformChain(c,WHOLE,p));
    }
    @Test void breakPoleCapturesRawPlayerLongAndReleasesOnTapOrExactTimer() {
        var c=cut(5); var a=c.actor(WHOLE); a.w(8,100); a.w(12,100); var player=c.actor(0); player.w(8,100); player.w(12,100); player.w(16,-256); player.w(10,0xBEEF); var p=new Ports();
        CdR4BreakGeyser.breakPole(c,WHOLE,p); assertEquals(0xFFFFD000,a.l(0x2C)); assertEquals(76,player.w(8)); assertEquals(0xBEEF,player.w(10)); assertEquals(180,a.w(0x2A));
        p.ctrl=0x100; CdR4BreakGeyser.breakPole(c,WHOLE,p); assertEquals(99,player.w(12)); assertEquals(4,a.b(0x24));
        p.ctrl=0x10; CdR4BreakGeyser.breakPole(c,WHOLE,p); assertEquals(6,a.b(0x24)); assertEquals(0,player.b(0x2C)&1);
    }
    @Test void breakPoleUnknownControllerIsNotReadAtTimerExpiryAndDirtyZeroTimerWraps() {
        var c=cut(5); var a=c.actor(WHOLE); a.b(0x24,4); a.l(0x2C,0xFFD000); a.w(0x2A,1); var p=new Ports(); p.fail="controller";
        CdR4BreakGeyser.breakPole(c,WHOLE,p); assertFalse(p.calls.contains("controller")); a.b(0x24,4); a.w(0x2A,0);
        assertThrows(IllegalStateException.class,()->CdR4BreakGeyser.breakPole(c,WHOLE,p)); assertEquals(65535,a.w(0x2A));
    }
    @Test void geyserPaletteFaultRetainsOnlyReachedPalettePrefix() {
        var c=cut(0x2A); var p=new Ports(); p.fail="palette:112:2698"; assertThrows(IllegalStateException.class,()->CdR4BreakGeyser.geyser(c,WHOLE,p));
        assertEquals(List.of("palette:110:1606","palette:112:2698"),p.calls); assertEquals(0,c.actor(WHOLE).b(0x24));
    }
    @Test void geyserSpawnUsesOldYBeforeWaterAndOnlyIncrementsRowOnSuccess() {
        var c=cut(0x2A); var a=c.actor(WHOLE); a.w(12,100); a.w(8,200); a.b(0x3B,240); a.b(0x3A,2); c.actor(32).w(14,0xAAAA); var p=new Ports();
        CdR4BreakGeyser.geyser(c,WHOLE,p); var child=c.actor(32); assertEquals(240,child.w(8)); assertEquals(28,child.w(12)); assertEquals(0xAAAA,child.w(14)); assertEquals(3,a.b(0x3A)); assertEquals(0x250,a.w(12));
        fill(c); a.b(0x3B,240); CdR4BreakGeyser.geyser(c,WHOLE,p); assertEquals(3,a.b(0x3A));
    }
    @Test void dropletByteCarryDeletesWithoutAnimationDrawOrWater() {
        var c=cut(0x2A); var a=c.actor(WHOLE); a.b(0x28,255); a.b(0x24,2); a.b(0x3B,254); var p=new Ports(); CdR4BreakGeyser.geyser(c,WHOLE,p); assertEquals(List.of("delete:40"),p.calls);
    }
    @Test void fanABLazyNegativeSwitchAndDirtyComplementAreNotBooleanNormalized() {
        var c=cut(0x35); var a=c.actor(WHOLE); a.b(0x29,255); a.b(0x3F,0x55); var p=new Ports(); CdR4FanShooter.fan(c,WHOLE,false,p); assertTrue(p.calls.contains("fanAnim"));
        a.b(0x29,0); c.switchByte(0,1); CdR4FanShooter.fan(c,WHOLE,false,p); assertEquals(0xAA,a.b(0x3F)); assertEquals(255,a.b(0x3E));
    }
    @Test void fanCDReadsIdentityBeforeInitAndAgainBeforeSwitchAndHasNoDespawn() {
        var c=cut(0x35); var a=c.actor(WHOLE); a.w(0x2A,0x1234); a.b(0x28,0x83); a.b(0x29,255); var p=new Ports(); CdR4FanShooter.fan(c,WHOLE,true,p);
        assertEquals("fanIdentity:4660",p.calls.getFirst()); assertEquals(2,p.calls.stream().filter(x->x.equals("fanIdentity:4660")).count());
        assertEquals(1,a.b(0x1A)); assertFalse(p.calls.stream().anyMatch(x->x.startsWith("check"))); p.calls.clear(); p.identity=8; CdR4FanShooter.fan(c,WHOLE,true,p); assertEquals(List.of("fanIdentity:4660","delete:40"),p.calls);
    }
    @Test void shooterFailedAllocationStillResets30TimerAndSuccessRetainsFractions() {
        var c=cut(0x42); var a=c.actor(WHOLE); fill(c); a.b(0,0x42); var p=new Ports(); CdR4FanShooter.shooter(c,WHOLE,p); assertEquals(30,a.w(0x2A));
        var child=c.actor(32); child.b(0,0); child.w(10,0xCAFE); child.w(14,0xBEEF); a.w(0x2A,0); CdR4FanShooter.shooter(c,WHOLE,p);
        assertEquals(0x42,child.b(0)); assertEquals(0xCAFE,child.w(10)); assertEquals(0xBEEF,child.w(14)); assertEquals(-0x30000,child.l(0x2C)); assertEquals(32,child.w(8));
    }
    @Test void missileRectangleHasExclusiveUpperEdgesAndOnlyGroundSpeedWrite() {
        for(int x:new int[]{-21,-20,19,20}) { var c=cut(0x42); var a=c.actor(WHOLE); a.b(0x28,255); a.l(0x2C,-0x30000); c.actor(0).w(8,x); c.actor(0).w(12,0); c.actor(0).w(16,0x1234); var p=new Ports();
            CdR4FanShooter.shooter(c,WHOLE,p); assertEquals(x>=-20&&x<20?0xFD00:0,c.actor(0).w(0x14)); assertEquals(0x1234,c.actor(0).w(16)); }
    }
    @Test void prizeSwitchSpawnsFiveAndDirtyChildrenKeepUnwrittenBytes() {
        var c=cut(0x3F); var a=c.actor(WHOLE); a.w(8,1000); a.w(12,1000); c.actor(32).w(10,0xAAAA); var p=new Ports(); CdR4Prizes.prizeSwitch(c,WHOLE,p);
        assertEquals(936,c.actor(32).w(8)); assertEquals(1064,c.actor(32).w(12)); assertEquals(0xAAAA,c.actor(32).w(10)); assertEquals(5,c.actor(36).b(0x34)); assertEquals(0xDA00,c.actor(36).w(0x3A));
    }
    @Test void prizeContactTableUsesPartTimesSixAndDecimalQuotientNotModulo() {
        var c=cut(0x3F); var a=c.actor(WHOLE); a.b(0x24,2); a.b(0x34,2); a.w(0x3A,0xDA00); a.w(12,100); a.b(0x16,8); var p=new Ports(); p.contact=true; p.seconds=30;
        CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(2,a.b(0x35)); assertEquals(0xDA00,a.w(0x3C)); assertEquals(104,a.w(12)); assertEquals(4,a.b(0x16)); assertTrue(p.calls.contains("sound:191"));
    }
    @Test void prizeCountdown16CallsThenGetOffAndFrame2() {
        var c=cut(0x3F); var a=c.actor(WHOLE); a.b(0x24,2); a.w(0x3A,0xDA00); a.w(0x3C,0xDA00); var p=new Ports();
        for(int n=0;n<15;n++) CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(2,a.b(0x24)); CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(4,a.b(0x24)); assertEquals(2,a.b(0x1A)); assertEquals(1,p.calls.stream().filter(x->x.equals("getOff")).count());
    }
    @Test void prizePointsBeforeFailedAllocationAndNoRepeatedAwardNextCall() {
        var c=cut(0x3F); fill(c); var a=c.actor(WHOLE); a.b(0,0x3F); a.b(0x24,4); a.w(0x3A,0xDA00); a.w(0x3C,0xDA40); a.b(0x35,1); var p=new Ports();
        CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(1,a.b(0x38)); CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(1,p.calls.stream().filter(x->x.equals("points:100")).count()); assertTrue(p.calls.contains("draw:0"));
    }
    @Test void prizeRingBurstCounts21AttemptsAt16TicksIncludingFailedAllocations() {
        var c=cut(0x3F); fill(c); var a=c.actor(WHOLE); a.b(0,0x3F); a.b(0x24,4); a.w(0x3A,0xDA00); a.b(0x35,3); var p=new Ports();
        for(int n=0;n<21*16;n++) CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(21,a.b(0x39)); assertEquals(0x3F,a.b(0));
        for(int n=0;n<16;n++) CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(0,a.b(0));
    }
    @Test void prizeBubbleModeCountsTwoAttemptsAt64TicksAndSelectedWaits256Ticks() {
        var c=cut(0x3F); fill(c); var a=c.actor(WHOLE); a.b(0,0x3F); a.b(0x24,4); a.w(0x3A,0xDA00); a.w(0x3C,0xDA40); a.b(0x35,2); var p=new Ports();
        for(int n=0;n<127;n++) CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(0x3F,a.b(0)); CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(0,a.b(0));
    }
    @Test void ringPrizeWaits48BeforeFloorReadAndBouncesBySignedThreeQuarters() {
        var c=cut(0x40); var a=c.actor(WHOLE); var p=new Ports(); p.floor=-3;
        CdR4Prizes.ringPrize(c,WHOLE,p); assertEquals(47,a.b(0x3B)); assertFalse(p.calls.contains("floor"));
        a.b(0x3B,1); a.w(12,100); a.w(18,0x101); CdR4Prizes.ringPrize(c,WHOLE,p); assertEquals(0xFF2D,a.w(18)); assertTrue(p.calls.contains("floor"));
    }
    @Test void ringPrizeLifeZeroAvoidsBottomReadAndCollectDelegatesOnceBeforeAnimation() {
        var c=cut(0x40); var a=c.actor(WHOLE); a.b(0x24,2); a.b(0x3A,1); a.b(0x3B,2); var p=new Ports(); CdR4Prizes.ringPrize(c,WHOLE,p); assertFalse(p.calls.contains("bottom"));
        a.b(0,0x40); a.b(0x24,4); CdR4Prizes.ringPrize(c,WHOLE,p); CdR4Prizes.ringPrize(c,WHOLE,p); assertEquals(1,p.calls.stream().filter(x->x.equals("collectRing")).count()); assertEquals(6,a.b(0x24));
    }
    @Test void pointsPrizeHas32StepsThenDeleteAndOuterDrawEvenAfterDelete() {
        var c=cut(0x41); var a=c.actor(WHOLE); var p=new Ports();
        for(int n=0;n<32;n++) CdR4Prizes.pointsPrize(c,WHOLE,p); assertEquals(0,a.w(18)); assertEquals(0x41,a.b(0)); CdR4Prizes.pointsPrize(c,WHOLE,p);
        assertEquals(List.of("delete:40","draw:0"),p.calls.subList(p.calls.size()-2,p.calls.size()));
    }
    @Test void rawLinksNeverFilterByIdAndRejectUnownedOrUnalignedOnlyWhenReached() {
        var c=cut(0x35); assertSame(c.actor(41),c.linkWord(0xDA40)); assertSame(c.actor(0),c.linkLong(0xFFD000)); assertThrows(IllegalStateException.class,()->c.linkWord(0xDA41));
        var a=c.actor(WHOLE); a.b(0x24,2); a.w(0x34,0xDA41); a.w(0x3C,0xF7E0); assertThrows(IllegalStateException.class,()->CdR4SwitchDoor.switchObject(c,WHOLE,new Ports()));
    }
    @Test void doorCrossingAndClosingRespectByteComparisonAndClamp() {
        var c=cut(0x28); var a=c.actor(WHOLE); var player=c.actor(0); a.b(0x24,4); a.b(0x31,0); a.w(12,100); a.w(0x3E,90); player.w(12,130); player.b(0x16,19); var p=new Ports();
        CdR4SwitchDoor.door(c,WHOLE,p); assertEquals(6,a.b(0x24)); a.b(0x3A,3); a.w(0x36,200); CdR4SwitchDoor.door(c,WHOLE,p); assertEquals(0,a.b(0x3A)); assertEquals(2,a.b(0x24)); assertEquals(200,a.w(8));
    }
    @Test void bigDoorThreeChildrenUseAfterSourceAndAlternateFrames() {
        var c=cut(0x25); var a=c.actor(WHOLE); a.w(8,200); var p=new Ports(); CdR4SwitchDoor.bigDoor(c,WHOLE,p); c.switchByte(0,1); CdR4SwitchDoor.bigDoor(c,WHOLE,p);
        assertEquals(0,c.actor(32).b(0)); assertEquals(2,c.actor(41).b(0x1A)); assertEquals(1,c.actor(42).b(0x1A)); assertEquals(2,c.actor(43).b(0x1A)); assertEquals(0xF8,c.actor(43).w(8)) /* native 200 - 0x30 + 3*0x20 */; assertEquals(0x25,a.b(0));
    }
    @Test void switchElevatorChildFollowsOnlyNativeIdAndDeleteSkipsDraw() {
        var c=cut(0x30); var a=c.actor(WHOLE); a.b(0x24,2); a.b(0x3C,1); a.w(0x34,0xDA40); c.actor(41).b(0,0x30); c.actor(41).w(8,100); c.actor(41).w(12,200); var p=new Ports();
        CdR4Lifts.switchElevator(c,WHOLE,p); assertEquals(68,a.w(8)); assertEquals(232,a.w(12)); p.calls.clear(); c.actor(41).b(0,1); CdR4Lifts.switchElevator(c,WHOLE,p);
        assertEquals(List.of("solid:SOLID_OBJECT","delete:40"),p.calls);
    }
    @Test void platformChainNegativeTrigRetainsAsrWordUpperAndEdgeReversalOnce() {
        var c=cut(0x3E); var a=c.actor(WHOLE); a.b(0x24,2); a.w(0x30,256); a.w(0x32,200); a.w(0x36,100); a.b(0x1A,1); c.switchByte(0,128); var p=new Ports(); p.sine=-256; p.cosine=-1;
        CdR4Lifts.platformChain(c,WHOLE,p); assertEquals(0xFF00,a.w(0x30)); assertEquals(104,a.w(12)); assertEquals(0x0063FFFF,a.l(8));
        CdR4Lifts.platformChain(c,WHOLE,p); assertEquals(0xFF00,a.w(0x30)); c.switchByte(0,0); CdR4Lifts.platformChain(c,WHOLE,p); c.switchByte(0,128); CdR4Lifts.platformChain(c,WHOLE,p); assertEquals(256,a.w(0x30));
    }
    @Test void breakPoleGeometryUpperEdgesAndDebugAvoidCapture() {
        for(int dx:new int[]{-4,-3,4,5}) { var c=cut(5); var a=c.actor(WHOLE); a.w(8,100); a.w(12,100); c.actor(0).w(8,100+dx); c.actor(0).w(12,100); var p=new Ports(); CdR4BreakGeyser.breakPole(c,WHOLE,p); assertEquals(dx>-4&&dx<=4?4:2,a.b(0x24)); }
        var c=cut(5); var p=new Ports(); p.debug=1; CdR4BreakGeyser.breakPole(c,WHOLE,p); assertEquals(2,c.actor(WHOLE).b(0x24));
    }
    @Test void selectedPrizeAddressIsComparedWithoutDereferenceAndSpikesExcludePart4() {
        var c=cut(0x3F); var a=c.actor(WHOLE); a.b(0x24,4); a.w(0x3A,0xDA00); a.w(0x3C,0x1235); a.b(0x35,0); a.b(0x34,4); var p=new Ports();
        CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(1,a.b(0x38)); assertEquals(0,c.actor(32).b(0));
        a.b(0x38,0); a.b(0x34,1); CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(0x22,c.actor(32).b(0));
    }
    @Test void selectedBubblePrizeWaits256AndRingBurstUsesLiteralFirstAndLastReachedOffsets() {
        var c=cut(0x3F); var a=c.actor(WHOLE); a.b(0x24,4); a.w(0x3A,0xDA00); a.w(0x3C,0xDA00); a.b(0x35,2); var p=new Ports();
        for(int n=0;n<255;n++) CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(0x3F,a.b(0)); CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(0,a.b(0));
        a.b(0,0x3F); a.b(0x24,4); a.w(0x3A,0xDA00); a.b(0x35,3); a.w(8,500); a.w(12,800); a.b(0x38,240); CdR4Prizes.prizeSwitch(c,WHOLE,p);
        assertEquals(468,c.actor(32).w(8)); assertEquals(416,c.actor(32).w(12)); a.b(0x38,240); a.b(0x39,20); CdR4Prizes.prizeSwitch(c,WHOLE,p); assertEquals(500,c.actor(33).w(8));
    }
    @Test void faultBeforePrizePointsRetainsNoAwardCountOrChildAndDrawIsUnreached() {
        var c=cut(0x3F); var a=c.actor(WHOLE); a.b(0x24,4); a.w(0x3A,0xDA00); a.w(0x3C,0xDA40); a.b(0x35,1); var p=new Ports(); p.fail="points:100";
        assertThrows(IllegalStateException.class,()->CdR4Prizes.prizeSwitch(c,WHOLE,p)); assertEquals(0,a.b(0x38)); assertEquals(0,c.actor(32).b(0)); assertEquals(List.of("points:100"),p.calls);
    }
    @Test void ringPrizeNegativeFloorAtZeroRetainsNativeWrappedYBottomDeletion() {
        var c=cut(0x40); var a=c.actor(WHOLE); a.b(0x24,2); a.b(0x3A,10); a.b(0x3B,1); var p=new Ports(); p.floor=-3;
        CdR4Prizes.ringPrize(c,WHOLE,p); assertEquals(0,a.b(0)); assertEquals(List.of("move","floor","bottom","delete:40"),p.calls);
    }
    @Test void pointsPrizeRetainsFull96AndBothPlayerNonwritesOutsideReachedMotionFields() {
        var c=cut(0x41);
        for(var image:c.dynamicImages()) { byte[] dirty=new byte[64]; for(int at=0;at<64;at++) dirty[at]=(byte)(image.wholeIndex()*3+at); image.replaceHelperData(dirty); }
        var a=c.actor(WHOLE); a.b(0,0x41); a.b(0x24,2); a.w(16,0); a.w(18,-0x300); a.b(0x22,0);
        byte[][] before=c.dynamicImages().stream().map(CdR4MechanismImage::bytes).toArray(byte[][]::new); byte[] primary=c.actor(0).bytes(), secondary=c.actor(1).bytes();
        CdR4Prizes.pointsPrize(c,WHOLE,new Ports());
        for(var image:c.dynamicImages()) if(image!=a) assertArrayEquals(before[image.wholeIndex()-32],image.bytes(),"unreached whole slot "+image.wholeIndex());
        byte[] old=before[WHOLE-32], now=a.bytes(); for(int at=0;at<64;at++) if((at<12||at>15)&&(at<18||at>19)) assertEquals(old[at],now[at],"nonwrite byte "+at);
        assertArrayEquals(primary,c.actor(0).bytes()); assertArrayEquals(secondary,c.actor(1).bytes()); assertEquals(0xFD18,a.w(18));
    }
    @Test void completePlayerPairAndOriginalPoolAreRetainedWithoutImplicitClears() {
        var pool=new CdObjectPool(); byte[] secondary=new byte[64]; secondary[0x22]=(byte)0xA5; var base=pool.capture();
        var c=new CdR4MechanismCut(base,new byte[64],secondary,new CdR4SwitchBank(new byte[32],-1)); secondary[0x22]=0;
        assertSame(base,c.borrowedPool()); assertEquals(0xA5,c.actor(1).b(0x22)); assertSame(c.actor(1),c.linkWord(0xD040));
        CdR4FanShooter.shooter(c,WHOLE,new Ports()); assertEquals(0xA5,c.actor(1).b(0x22));
        assertEquals(0xFFFFD000,c.actor(0).rawAddress()); assertEquals(0xFFFFF7E0,CdR4SwitchBank.BASE_LONG);
    }
    // Literal DoorObject_2 and BreakPoleObject_0_Routine4 controls from N, not live World proof.
    private static byte[][] cutImages(CdR4MechanismCut c) {
        byte[][] images=new byte[128][];
        for(int whole=0;whole<128;whole++) if(whole<2 || whole>=32) images[whole]=c.actor(whole).bytes();
        return images;
    }
    private static void expectedWord(byte[][] images,int whole,int at,int value) {
        images[whole][at]=(byte)(value>>>8); images[whole][at+1]=(byte)value;
    }
    private static void assertCutImages(byte[][] expected,CdR4MechanismCut actual) {
        for(int whole=0;whole<128;whole++) if(expected[whole]!=null)
            assertArrayEquals(expected[whole],actual.actor(whole).bytes(),"whole "+whole);
    }
    @Test void doorNegativeHorizontalCrossingUsesNativeByteAtWrappedDistances() {
        for(int type:new int[]{2,3,4}) for(int distance:new int[]{0x100,0x101,0x102,0x103,0x104,0x105,0x106,0x107,0x108,0x1FF,0x200,0x208}) {
            var c=cut(0x28); var a=c.actor(WHOLE); var player=c.actor(0);
            a.b(0x24,4); a.b(0x31,type); a.w(8,0x300); a.w(0x38,0x400);
            player.b(0x17,8); player.w(8,0x300-distance-8); player.w(10,0xA55A);
            var expected=cutImages(c); expected[WHOLE][0x24]=(byte)((distance&255)>=8?6:4);
            var p=new Ports(); CdR4SwitchDoor.door(c,WHOLE,p);
            assertCutImages(expected,c); assertEquals(List.of("solid:SOLID_OBJECT","draw:40","check:768"),p.calls);
        }
    }
    @Test void doorNegativeVerticalCrossingKeepsItsWordThreshold() {
        for(int type:new int[]{0,1}) for(int distance:new int[]{15,16,0x100}) {
            var c=cut(0x28); var a=c.actor(WHOLE); var player=c.actor(0);
            a.b(0x24,4); a.b(0x31,type); a.w(12,0x300); a.w(0x3E,0x400);
            player.b(0x16,19); player.w(12,0x300-distance-19); player.w(14,0xA55A);
            var expected=cutImages(c); expected[WHOLE][0x24]=(byte)(distance>=16?6:4);
            CdR4SwitchDoor.door(c,WHOLE,new Ports()); assertCutImages(expected,c);
        }
    }
    @Test void doorPositiveCrossingsRetainTheSeparateNativeByteGate() {
        for(int type=0;type<5;type++) for(int[] vector:new int[][]{{7,4},{8,6},{0x100,4},{0x108,6}}) {
            var c=cut(0x28); var a=c.actor(WHOLE); var player=c.actor(0);
            int pos=type<2?12:8,saved=type<2?0x3E:0x38,radius=type<2?0x16:0x17;
            a.b(0x24,4); a.b(0x31,type); a.w(pos,0x300); a.w(saved,0x200);
            player.b(radius,8); player.w(pos,0x308+vector[0]);
            var expected=cutImages(c); expected[WHOLE][0x24]=(byte)vector[1];
            CdR4SwitchDoor.door(c,WHOLE,new Ports()); assertCutImages(expected,c);
        }
    }
    @Test void doorSolidFailureRetainsTheNativeByteRoutineAndFullImages() {
        for(int[] vector:new int[][]{{0x100,4},{0x108,6}}) {
            var c=cut(0x28); var a=c.actor(WHOLE); a.b(0x24,4); a.b(0x31,2); a.w(8,0x300); a.w(0x38,0x400);
            c.actor(0).b(0x17,8); c.actor(0).w(8,0x300-vector[0]-8);
            var expected=cutImages(c); expected[WHOLE][0x24]=(byte)vector[1];
            var p=new Ports(); p.fail="solid:SOLID_OBJECT";
            assertThrows(IllegalStateException.class,()->CdR4SwitchDoor.door(c,WHOLE,p));
            assertCutImages(expected,c); assertEquals(List.of("solid:SOLID_OBJECT"),p.calls);
        }
    }
    @Test void breakPoleDoesNotResolveAnUnreachedCapturedTarget() {
        var c=cut(5); var a=c.actor(WHOLE); a.b(0x24,4); a.w(0x2A,2); a.l(0x2C,0x12345678);
        var expected=cutImages(c); expectedWord(expected,WHOLE,0x2A,1);
        var p=new Ports(); CdR4BreakGeyser.breakPole(c,WHOLE,p);
        assertCutImages(expected,c); assertEquals(List.of("controller","draw:5","check:0"),p.calls);
    }
    @Test void breakPoleExpiryRetainsTimerStoreBeforeTargetRejectionAndSkipsController() {
        var c=cut(5); var a=c.actor(WHOLE); a.b(0x24,4); a.w(0x2A,1); a.l(0x2C,0x12345678);
        var expected=cutImages(c); expectedWord(expected,WHOLE,0x2A,0); var p=new Ports();
        var failure=assertThrows(IllegalStateException.class,()->CdR4BreakGeyser.breakPole(c,WHOLE,p));
        assertTrue(failure.getMessage().contains("12345678")); assertCutImages(expected,c); assertTrue(p.calls.isEmpty());
    }
    @Test void breakPoleControllerFailureRetainsTimerAndOriginalThrowableBeforeTargetAccess() {
        for(Throwable failure:new Throwable[]{new IllegalStateException("controller"),new AssertionError("controller")}) {
            var c=cut(5); var a=c.actor(WHOLE); a.b(0x24,4); a.w(0x2A,2); a.l(0x2C,0x12345678);
            var expected=cutImages(c); expectedWord(expected,WHOLE,0x2A,1);
            var p=new Ports() { @Override public int controllerWord() {
                calls.add("controller"); if(failure instanceof Error error) throw error; throw (RuntimeException)failure;
            }};
            assertSame(failure,assertThrows(failure.getClass(),()->CdR4BreakGeyser.breakPole(c,WHOLE,p)));
            assertCutImages(expected,c); assertEquals(List.of("controller"),p.calls);
        }
    }
    @Test void breakPoleSelectedTargetRejectionFollowsControllerAndTimerForTapUpDown() {
        for(int ctrl:new int[]{0x10,0x100,0x200}) {
            var c=cut(5); var a=c.actor(WHOLE); a.b(0x24,4); a.w(0x2A,2); a.l(0x2C,0x12345678);
            var expected=cutImages(c); expectedWord(expected,WHOLE,0x2A,1); var p=new Ports(); p.ctrl=ctrl;
            var failure=assertThrows(IllegalStateException.class,()->CdR4BreakGeyser.breakPole(c,WHOLE,p));
            assertTrue(failure.getMessage().contains("12345678")); assertCutImages(expected,c); assertEquals(List.of("controller"),p.calls);
        }
    }
    @Test void breakPoleValidRawPrimarySecondaryAndDynamicTargetsPreserveFullNonwrites() {
        for(int[] target:new int[][]{{0x00FFD000,0},{0xFFFFD000,0},{0x00FFD040,1},{0xFFFFD040,1},{0x00FFDA40,41},{0xFFFFDA40,41}})
        for(int ctrl:new int[]{0x100,0x200,0x300,0x10}) {
            var c=cut(5); var a=c.actor(WHOLE); var player=c.actor(target[1]);
            a.b(0x24,4); a.w(0x2A,2); a.l(0x2C,target[0]); a.w(12,100);
            player.w(12,100); player.w(14,0xA55A); player.b(0x2C,0xA5);
            var expected=cutImages(c); expectedWord(expected,WHOLE,0x2A,1);
            if(ctrl==0x10) { expected[target[1]][0x2C]=(byte)0xA4; expected[WHOLE][0x1A]=1; expected[WHOLE][0x24]=6; }
            else expectedWord(expected,target[1],12,ctrl==0x200?101:99);
            var p=new Ports(); p.ctrl=ctrl; CdR4BreakGeyser.breakPole(c,WHOLE,p);
            assertCutImages(expected,c); assertEquals(List.of("controller","draw:5","check:0"),p.calls);
        }
    }

    @Test void breakPoleRawSelfLinkUsesSourceYFromBeforeTheTargetWrite() {
        for(int[] vector:new int[][]{{0x100,10,0xFFF2},{0x200,0xFFFA,0x12}}) {
            var c=cut(5);var a=c.actor(WHOLE);a.b(0x24,4);a.w(0x2A,2);a.l(0x2C,0xFFFFDA00);
            a.w(12,vector[1]);a.w(14,0xA55A);var expected=cutImages(c);
            expectedWord(expected,WHOLE,0x2A,1);expectedWord(expected,WHOLE,12,vector[2]);
            var p=new Ports();p.ctrl=vector[0];CdR4BreakGeyser.breakPole(c,WHOLE,p);
            assertCutImages(expected,c);assertEquals(List.of("controller","draw:5","check:0"),p.calls);
        }
    }

}
