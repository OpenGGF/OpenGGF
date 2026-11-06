package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.objects.CdR13Boss.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.CdPlayerMath;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdR13Boss.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
/** Tests authored before production; every Java check UNRUN. Actual authenticated C/D data;
* public-pool constructed component ingress, scalar/queue fixtures and arithmetic vectors.
* No World/Frame/player/physics/natural-fight/capture authority is constructed or claimed.
* N1ecc5f3a objects/r1/boss.asm (all63 directory entries), FixBugs=0. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR13BossComponents {
    private List<CdR13FutureAssets> originals;
    @Test void actualBothProfilesBindOnlyTheirRetainedPlacementAndSameOriginalMetadata() throws Exception {
        for (var a : actual()) {
            var h=new Harness(a);
            assertSame(a.identity(),h.boss.assets().identity());
            assertEquals(35,h.boss.assets().ranges().size());
        }
        var a=actual().getFirst();
        var b=actual().getLast();
        var p=placement(a);
        assertThrows(IllegalArgumentException.class,()->CdR13Boss.fromAct3FutureAssets(p,b,new Ports(){
        }));
        assertThrows(NullPointerException.class,()->CdR13Boss.fromAct3FutureAssets(p,a,null));
        var foreign=mock(CdR13FutureAssets.class);
        when(foreign.identity()).thenReturn(a.identity());
        when(foreign.placements()).thenReturn(a.placements());
        when(foreign.player()).thenReturn(a.player());
        assertThrows(IllegalArgumentException.class,()->CdR13Boss.fromAct3FutureAssets(p,foreign,new Ports(){
        }));
        verify(foreign,never()).plcs();
    }
    @Test void parentInitStoresNativePrefixThenPlcBossFightAndActualPaletteFiveInSourceOrder() throws Exception {
        for(var a:actual()){
            var h=new Harness(a);
            var old=h.put(90,0x2A,0).moveWord(X_FRACTION,0xA55A).moveWord(Y_FRACTION,0x5AA5);
            h.pool.write(old);
            var r=h.boss.run(90);
            var s=r.state();
            assertEquals(2,s.routine());
            assertEquals(0xC52,s.centreX());
            assertEquals(0x78,s.centreY());
            assertEquals(0xA55A,s.word(X_FRACTION));
            assertEquals(0x5AA5,s.word(Y_FRACTION));
            assertEquals(0x3FD,s.word(TILE));
            assertEquals(h.boss.assets().range(CdR13BossAssets.Table.EGGMAN_MAPPINGS).nativeAddress(),s.longBits(MAPPINGS));
            assertEquals(1,h.values.get(Field.BOSS_FIGHT));
            assertTrue(h.events.indexOf("plc4")<h.events.indexOf("store:BOSS_FIGHT"));
            assertTrue(h.events.indexOf("store:BOSS_FIGHT")<h.events.indexOf("palette5"));
            assertEquals(2,h.events.stream().filter(e->e.equals("store:BOTTOM_BOUND")||e.equals("store:TARGET_BOTTOM_BOUND")).count());
            assertEquals(1,h.palettes.size());
            assertSame(h.boss.assets().palette(),h.palettes.getFirst());
            assertTrue(r.effects().getLast() instanceof DrawReturned);
        }
    }
    @Test void emptyPortsStopAtFirstActualReadWithNoInventedBottomOrPlayerValue() throws Exception {
        var a=actual().getFirst();
        var p=placement(a);
        p.pool().write(p.pool().state(90).moveByte(ID,0x2A));
        var before=p.capture();
        var c=CdR13Boss.fromAct3FutureAssets(p,a,new Ports(){
        });
        var f=assertThrows(UnownedBoss.class,()->c.run(90));
        assertEquals(Boundary.EXTERNAL_OWNER,f.boundary());
        assertEquals(Site.BOTTOM_FLAGS,f.site());
        assertEquals(before.pool().slots(),p.capture().pool().slots());
        assertEquals(before.status(),p.status());
        assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());
    }
    @Test void preludeTimerAndShakeUseByteWrapAndConditionalFreshReads() throws Exception {
        var h=new Harness(actual().getFirst());
        h.put(90,0x2A,2);
        h.pool.write(h.pool.state(90).moveByte(BOSS_TIMER,1).moveByte(BOSS_SHAKE,2).moveByte(ANIMATION,4));
        h.values.put(Field.BOSS_FLAGS,1);
        h.values.put(Field.PLAYER_HURT_TIMER,1);
        h.boss.run(90);
        var s=h.pool.state(90);
        assertEquals(0x3C,s.unsignedByte(BOSS_TIMER));
        assertEquals(1,s.unsignedByte(BOSS_SHAKE));
        assertEquals(0xFF,h.values.get(Field.BOTTOM_BOUND));
        assertTrue(h.events.contains("read:PLAYER_HURT_TIMER"));
        assertFalse(h.events.contains("read:PLAYER_ROUTINE"));
        assertEquals(1,s.unsignedByte(ANIMATION));
    }
    @Test void entryCameraUsesSignedWordComparisonsAndStoresOnlyReachedBounds() throws Exception {
        int[] xs={
            0xA69,0xA6A,0xB5F,0xB60,0xFFFF
        };
        for(int x:xs){
            var h=new Harness(actual().getFirst());
            h.put(90,0x2A,2);
            h.values.put(Field.PLAYER_X,x);
            h.values.put(Field.LEFT_BOUND,0x900);
            h.boss.run(90);
            assertEquals(0xAC0,h.values.get(Field.RIGHT_BOUND));
            boolean reached=x>=0xA6A&&x<0x8000;
            assertEquals(reached,h.events.contains("read:LEFT_BOUND"));
            assertEquals(x==0xB60?12:2,h.pool.state(90).routine());
            if(x==0xB60)assertEquals(0xAC0,h.values.get(Field.LEFT_BOUND));
        }
    }
    @Test void cameraFocusAndDelayedChildAdmissionRetainExactSixtyAndNoClock() throws Exception {
        var h=new Harness(actual().getFirst());
        h.put(90,0x2A,12);
        h.values.put(Field.CAMERA_Y_CENTRE,0xC2);
        h.boss.run(90);
        assertEquals(14,h.pool.state(90).routine());
        assertEquals(1,h.values.get(Field.BOSS_FLAGS));
        assertEquals(List.of(CpuCommand.BOSS_MUSIC),h.commands);
        for(int n=1;n<60;n++){
            h.boss.run(90);
            assertEquals(14,h.pool.state(90).routine());
        }
        h.boss.run(90);
        assertEquals(4,h.pool.state(90).routine());
        assertEquals(14,h.pool.capture().slots().stream().filter(s->s.id()>=0x2B&&s.id()<=0x32).count());
    }
    @Test void fourteenOriginalAllocationsKeepNativeLinksDirtyFieldsAndForearmMaps() throws Exception {
        for(var a:actual()){
            var h=new Harness(a);
            for(int n=32;n<46;n++)h.pool.write(h.pool.state(n).moveByte(SAVED_FLAGS_ID,0x51+n-32).moveWord(X_FRACTION,0x1234).moveByte(SUBTYPE,0xA5).moveByte(ROUTINE,0x80+n-32).moveWord(TILE,0xABCD).moveLong(MAPPINGS,0x12345678));
            h.admit(90);
            int[] ids={
                0x2B,0x2C,0x2D,0x2E,0x2C,0x2D,0x2E,0x2F,0x30,0x31,0x32,0x30,0x31,0x32
            };
            for(int i=0;i<14;i++){
                var s=h.pool.state(32+i);
                assertEquals(ids[i],s.id());
                assertEquals(0x80+i,s.routine());
                assertEquals(0xABCD,s.word(TILE));
                if(i!=9&&i!=12)assertEquals(0x12345678,s.longBits(MAPPINGS));
                assertEquals(0x51+i,s.savedFlagsId());
                assertEquals(0x1234,s.word(X_FRACTION));
                assertEquals(0xA5,s.subtype());
                assertEquals(0xBD2,s.centreX());
            }
            assertEquals(ptr(32),h.pool.state(90).word(BOSS_NEXT));
            assertEquals(ptr(39),h.pool.state(90).word(BOSS_BRANCH));
            assertEquals(ptr(90),h.pool.state(32).word(BOSS_PARENT));
            assertEquals(ptr(32),h.pool.state(38).word(BOSS_NEXT));
            assertEquals(ptr(32),h.pool.state(42).word(BOSS_NEXT));
            assertEquals(ptr(32),h.pool.state(45).word(BOSS_NEXT));
            assertEquals(ptr(43),h.pool.state(39).word(BOSS_BRANCH));
            assertEquals(h.boss.assets().range(CdR13BossAssets.Table.FOREARM1_MAPPINGS).nativeAddress(),h.pool.state(41).longBits(MAPPINGS));
            assertEquals(h.boss.assets().range(CdR13BossAssets.Table.FOREARM2_MAPPINGS).nativeAddress(),h.pool.state(44).longBits(MAPPINGS));
            assertEquals(0xFF,h.pool.state(42).unsignedByte(COLLISION_TYPE));
            assertEquals(0xFC,h.pool.state(90).unsignedByte(COLLISION_TYPE));
        }
    }
    @Test void everyAllocationFailureRetainsExactlyItsExistingChildrenAndDoesNotInventHealthOrLinks() throws Exception {
        for(int count=0;count<14;count++){
            var h=new Harness(actual().getFirst());
            for(int n=32;n<128;n++)h.pool.write(h.pool.state(n).moveByte(ID,1));
            for(int n=32;n<32+count;n++)h.pool.objNull(n);
            h.admit(90);
            assertEquals(count,h.pool.capture().slots().stream().filter(s->s.id()>=0x2B&&s.id()<=0x32).count());
            assertEquals(0,h.pool.state(90).unsignedByte(BOSS_COUNTER));
            assertEquals(4,h.pool.state(90).routine());
            assertTrue(h.events.contains("draw"));
        }
    }
    @Test void physicalEarlierChildrenWaitAndLaterChildrenCanRunInSameCallerWalk() throws Exception {
        var earlier=new Harness(actual().getFirst());
        earlier.admit(90);
        assertEquals(0,earlier.pool.state(32).routine());
        earlier.boss.run(32);
        assertEquals(2,earlier.pool.state(32).routine());
        var later=new Harness(actual().getFirst());
        later.put(32,0x2A,14);
        later.pool.write(later.pool.state(32).moveByte(BOSS_COUNTER,59));
        for(int n=32;n<128;n++){
            int id=later.pool.state(n).id();
            if(id>=0x2A&&id<=0x32)later.boss.run(n);
        }
        assertEquals(2,later.pool.state(33).routine());
        assertEquals(6,later.pool.state(41).routine());
    }
    @Test void allSixtyThreeDirectoryEntriesHaveFiniteComponentBodiesIncludingArmAlias() throws Exception {
        int count=0;
        for(var a:actual())for(var p:CdR13BossAssets.Part.values())for(int r=0;r<p.routineCount()*2;r+=2){
            var h=new Harness(a);
            h.machine();
            int w=whole(p);
            var s=h.pool.state(w).moveByte(ROUTINE,r).moveByte(BOSS_FLAGS,0).moveByte(BOSS_PARAMETER,2).moveWord(Y,0x100);
            if(p==CdR13BossAssets.Part.EGGMAN)s=s.moveByte(ANIMATION,1);
            h.pool.write(s);
            assertDoesNotThrow(()->h.boss.run(w),p+" routine "+r);
            count++;
        }
        assertEquals(126,count);
    }
    @Test void oddAndOutOfDirectoryDispatchStopAfterParentPreludeButBeforeAnimationDraw() throws Exception {
        var h=new Harness(actual().getFirst());
        h.put(90,0x2A,3);
        var f=assertThrows(UnownedBoss.class,()->h.boss.run(90));
        assertEquals(Boundary.ROUTINE_DIRECTORY,f.boundary());
        assertTrue(h.events.contains("store:TARGET_BOTTOM_BOUND"));
        assertFalse(h.events.contains("draw"));
        h.put(91,0x32,6);
        assertEquals(Boundary.ROUTINE_DIRECTORY,assertThrows(UnownedBoss.class,()->h.boss.run(91)).boundary());
    }
    @Test void poseSequenceUsesActualSentinelBackedgeAndExactWordAnimationClear() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(90).moveByte(ROUTINE,4).moveByte(ROUTINE_2,16));
        h.pool.write(h.pool.state(32).moveByte(BOSS_FLAGS,1));
        h.boss.run(90);
        assertEquals(6,h.pool.state(90).unsignedByte(ROUTINE_2));
        assertEquals(8,h.pool.state(32).routine());
        assertEquals(5,h.pool.state(32).unsignedByte(BOSS_PARAMETER));
        h.pool.write(h.pool.state(90).moveByte(ROUTINE_2,2).moveByte(PREVIOUS_ANIMATION,0xAA));
        h.pool.write(h.pool.state(32).moveByte(BOSS_FLAGS,1));
        h.boss.run(90);
        assertEquals(6,h.pool.state(32).routine());
        assertEquals(0,h.pool.state(90).unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(3,h.pool.state(90).unsignedByte(BOSS_COUNTER));
    }
    @Test void parentHitBranchesRecoilAndDisableSelectedBumpersWithoutFabricatedTouch() throws Exception {
        for(int health=3;health>=1;health--){
            var h=new Harness(actual().getFirst());
            h.machine();
            h.pool.write(h.pool.state(90).moveByte(BOSS_FLAGS,8).moveByte(BOSS_COUNTER,health).moveByte(COLLISION_TYPE,0));
            h.values.put(Field.PLAYER_FLAGS,2);
            h.values.put(Field.PLAYER_X,0);
            h.boss.run(90);
            assertEquals(health==1?1:health-1,h.pool.state(90).unsignedByte(BOSS_COUNTER));
            assertEquals(0xFC00,h.values.get(Field.PLAYER_X_SPEED));
            assertEquals(0xFC00,h.values.get(Field.PLAYER_Y_SPEED));
            assertEquals(0x400,h.values.get(Field.PLAYER_GROUND_SPEED));
            assertEquals(health==1?6:4,h.pool.state(90).routine());
            assertEquals(20,h.pool.state(90).unsignedByte(BOSS_HIT_TIMER));
            assertFalse(h.events.contains("fm:HIT"));
        }
    }
    @Test void bumperHitPathReboundsGroundedPlayerThenCompletesNativeLaughTailAndFm98() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(90).moveByte(BOSS_FLAGS,8));
        h.pool.write(h.pool.state(45).moveByte(COLLISION_TYPE,0));
        h.values.put(Field.PLAYER_FLAGS,0);
        h.values.put(Field.PLAYER_ANGLE,0x31);
        h.values.put(Field.PLAYER_X,0xFFFF);
        h.boss.run(90);
        assertEquals(0xB1,h.values.get(Field.PLAYER_ANGLE));
        assertEquals(0,h.values.get(Field.PLAYER_Y_SPEED));
        assertEquals(0x400,h.values.get(Field.PLAYER_X_SPEED));
        assertEquals(List.of(FmSound.HIT),h.sounds);
        assertEquals(3,h.pool.state(90).unsignedByte(BOSS_COUNTER));
    }
    @Test void hitCooldownCallsOriginalRearmOnlyWhenItsByteExpires() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(90).moveByte(BOSS_HIT_TIMER,2).moveByte(COLLISION_TYPE,0));
        h.boss.run(90);
        assertEquals(0,h.pool.state(90).unsignedByte(COLLISION_TYPE));
        h.boss.run(90);
        assertEquals(0xFC,h.pool.state(90).unsignedByte(COLLISION_TYPE));
    }
    @Test void finalHitWritesEveryDestructionRoutineAndClearsOnlyNativeCollisionFields() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(90).moveByte(BOSS_FLAGS,8).moveByte(BOSS_COUNTER,1).moveByte(COLLISION_TYPE,0));
        h.boss.run(90);
        int[] w={
            39,32,33,34,35,36,37,38
        };
        int[] r={
            4,14,24,14,12,24,14,12
        };
        for(int i=0;i<w.length;i++)assertEquals(r[i],h.pool.state(w[i]).routine());
        for(int n:new int[]{
            34,35,37,38
        }){
            assertEquals(0,h.pool.state(n).unsignedByte(COLLISION_TYPE));
            assertEquals(0,h.pool.state(n).unsignedByte(COLLISION_STATUS));
        }
    }
    @Test void explosionCadenceUsesAllTenSignedPairsAndFullPoolFailureHasNoSound() throws Exception {
        var a=actual().getFirst();
        var b=CdR13BossAssets.bind(a);
        for(int pair=0;pair<10;pair++){
            var h=new Harness(a);
            h.machine();
            h.pool.write(h.pool.state(90).moveByte(ROUTINE,6).moveByte(BOSS_COUNTER,4*(pair+10)-1));
            var parent=h.pool.state(90);
            h.boss.run(90);
            var e=h.pool.state(46);
            assertEquals(0x18,e.id());
            assertEquals(0xFF,e.unsignedByte(ROUTINE_2));
            assertEquals((parent.centreX()+(short)b.view(CdR13BossAssets.Table.EXPLOSION_OFFSETS).unsignedWord(pair*4))&0xFFFF,e.centreX());
            assertEquals((parent.centreY()+(short)b.view(CdR13BossAssets.Table.EXPLOSION_OFFSETS).unsignedWord(pair*4+2))&0xFFFF,e.centreY());
            assertEquals(List.of(FmSound.EXPLOSION),h.sounds);
        }
        var h=new Harness(a);
        h.machine();
        for(int n=46;n<128;n++)if(n!=90)h.pool.write(h.pool.state(n).moveByte(ID,1));
        h.pool.write(h.pool.state(90).moveByte(ROUTINE,6).moveByte(BOSS_COUNTER,3));
        h.boss.run(90);
        assertTrue(h.sounds.isEmpty());
    }
    @Test void defeatAwardsOneNativeHundredUnitCallAt120AndOriginalFailureKeepsPriorSource() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(90).moveByte(ROUTINE,6).moveByte(BOSS_COUNTER,119));
        var original=new IllegalStateException("original score owner");
        h.failPoints=original;
        assertSame(original,assertThrows(IllegalStateException.class,()->h.boss.run(90)));
        assertEquals(8,h.pool.state(90).routine());
        assertEquals(32,h.pool.state(90).unsignedByte(WIDTH));
        assertEquals(List.of(100),h.points);
    }
    @Test void escapeFirstPhasePreservesFractionsAndRestoresTemporaryExplosionPositionOnlyAfterReturn() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(90).moveByte(ROUTINE,8).moveByte(ROUTINE_2,0).moveByte(BOSS_COUNTER,3).moveWord(BOSS_SAVED_X,0xB12).moveWord(X_VELOCITY,0x222).moveWord(Y,0x200).moveWord(X_FRACTION,0x8000));
        h.boss.run(90);
        assertEquals(0xBD3,h.pool.state(90).centreX());
        assertEquals(0,h.pool.state(90).word(X_FRACTION));
        assertEquals(0x1FE,h.pool.state(90).centreY());
        assertEquals(0xB12+48,h.pool.state(46).centreX());
        var f=new Harness(actual().getFirst());
        f.machine();
        f.pool.write(f.pool.state(90).moveByte(ROUTINE,8).moveByte(ROUTINE_2,0).moveByte(BOSS_COUNTER,3).moveWord(BOSS_SAVED_X,0xB12).moveWord(X_VELOCITY,0x222));
        f.failFm=new IllegalStateException("original FM");
        assertSame(f.failFm,assertThrows(IllegalStateException.class,()->f.boss.run(90)));
        assertEquals(0xB12,f.pool.state(90).centreX());
        assertEquals(0x222,f.pool.state(90).centreY());
    }
    @Test void escapeSineUsesActualSharedMathAndGoodFutureSelectsOnlyReachedMusicThenExitBounds() throws Exception {
        for(var a:actual())for(int good:new int[]{
            0,1
        }){
            var h=new Harness(a);
            h.machine();
            h.pool.write(h.pool.state(90).moveByte(ROUTINE,8).moveByte(ROUTINE_2,1).moveByte(BOSS_COUNTER,61).moveWord(Y,0x180).moveWord(BOSS_DY,8).moveWord(X,0xC7E));
            h.values.put(Field.GOOD_FUTURE,good);
            h.values.put(Field.RIGHT_BOUND,a.bounds().right()-12);
            h.values.put(Field.TARGET_RIGHT_BOUND,a.bounds().right()-12);
            h.boss.run(90);
            assertEquals((0x180-8+(new CdPlayerMath(a.player()).calcSine(64).sine()>>5))&0xFFFF,h.pool.state(90).centreY());
            assertEquals(good==0?CpuCommand.BAD_MUSIC:CpuCommand.GOOD_MUSIC,h.commands.getLast());
            assertEquals(0,h.values.get(Field.BOSS_FLAGS));
            assertEquals(0,h.values.get(Field.BOSS_FIGHT));
            assertTrue(h.events.indexOf("capsulePalette")<h.events.lastIndexOf("store:BOSS_FLAGS"));
            assertFalse(h.events.contains("draw"));
            h.boss.run(90);
            assertEquals(0,h.pool.state(90).id());
            assertArrayEquals(new byte[64],h.pool.state(90).copyBytes());
        }
    }
    @Test void allEightChildInitPrefixesPreservePrioritySavedIdAndForearmMapping() throws Exception {
        for(var a:actual()){
            var h=new Harness(a);
            h.admit(90);
            for(var p:CdR13BossAssets.Part.values())if(p!=CdR13BossAssets.Part.EGGMAN){
                int w=whole(p);
                var old=h.pool.state(w);
                h.boss.run(w);
                var s=h.pool.state(w);
                assertEquals(old.unsignedByte(PRIORITY),s.unsignedByte(PRIORITY));
                assertEquals(old.savedFlagsId(),s.savedFlagsId());
                assertEquals(old.subtype(),s.subtype());
                if(p==CdR13BossAssets.Part.FOREARM)assertEquals(old.longBits(MAPPINGS),s.longBits(MAPPINGS));
                assertEquals(p==CdR13BossAssets.Part.BODY?0x359:0x2359,s.word(TILE));
            }
        }
    }
    @Test void armUnsignedSubtractAndAddLimitsUseActualSineAndHiddenBranchHasNoDraw() throws Exception {
        for(int angle:new int[]{
            0,1,2,3,0x80,0xFF
        }){
            var h=new Harness(actual().getFirst());
            h.machine();
            h.pool.write(h.pool.state(40).moveByte(ROUTINE,2).moveByte(BOSS_TIMER,angle));
            h.boss.run(40);
            int v=angle<=2?0:(angle-2)&255;
            assertEquals(v,h.pool.state(40).unsignedByte(BOSS_TIMER));
            assertEquals(angle<=2?4:2,h.pool.state(40).routine());
        }
        var h=new Harness(actual().getFirst());
        h.machine();
        h.events.clear();
        h.pool.write(h.pool.state(43).moveByte(ROUTINE,6).moveByte(BOSS_TIMER,0x2F));
        h.boss.run(43);
        assertEquals(0x30,h.pool.state(43).unsignedByte(BOSS_TIMER));
        assertFalse(h.events.contains("draw"));
    }
    @Test void forearmTargetUsesSignedPlayerYAndBit7SkipsItsReadWithNativeHalfStep() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(41).moveByte(ROUTINE,6).moveLong(BOSS_DX_LONG,0x18000).moveLong(BOSS_DY_LONG,0).moveByte(BOSS_FLAGS,0x80));
        h.events.clear();
        h.boss.run(41);
        assertEquals(0x10000,h.pool.state(41).longBits(BOSS_DX_LONG));
        assertEquals(0x10000,h.pool.state(41).longBits(BOSS_DY_LONG));
        assertFalse(h.events.contains("read:PLAYER_Y"));
    }
    @Test void shoulderSeveringElidesOnlyTheProvenDeadScratchLoadForZeroAndForeignBranchWords() throws Exception {
        for(int raw:new int[]{
            0,0xD000,0xD801,0xFFFF
        }){
            var h=new Harness(actual().getFirst());
            h.machine();
            h.pool.write(h.pool.state(39).moveByte(ROUTINE,2).moveByte(BOSS_FLAGS,0x40).moveWord(BOSS_BRANCH,raw));
            h.boss.run(39);
            assertEquals(0,h.pool.state(39).word(BOSS_NEXT));
            assertEquals(raw,h.pool.state(39).word(BOSS_BRANCH));
            assertEquals(10,h.pool.state(40).routine());
            assertTrue(h.events.contains("draw"));
        }
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(39).moveByte(ROUTINE,2).moveByte(BOSS_FLAGS,0x20));
        h.boss.run(39);
        assertEquals(10,h.pool.state(43).routine());
        assertEquals(0,h.pool.state(39).word(BOSS_BRANCH));
        h.pool.write(h.pool.state(39).moveByte(BOSS_FLAGS,0x40));
        h.boss.run(39);
        assertEquals(0,h.pool.state(39).word(BOSS_NEXT));
        assertEquals(10,h.pool.state(40).routine());
    }
    @Test void actuallyConsumedShoulderLinkFaultKeepsEarlierPositionAndFlagPrefixes() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(39).moveByte(ROUTINE,2).moveByte(BOSS_FLAGS,0x40).moveWord(BOSS_NEXT,0).moveWord(X,1).moveWord(Y,2));
        var body=h.pool.state(32);
        var f=assertThrows(UnownedBoss.class,()->h.boss.run(39));
        assertEquals(Boundary.LINK_READ,f.boundary());
        assertEquals((body.centreX()+24)&65535,h.pool.state(39).centreX());
        assertEquals((body.centreY()-12)&65535,h.pool.state(39).centreY());
        assertEquals(0,h.pool.state(39).unsignedByte(BOSS_FLAGS)&64);
        assertFalse(h.events.contains("draw"));
    }
    @Test void bumperPlacementUsesAllThreeForearmFramesAndConsumesOnlyHitBit4() throws Exception {
        for(int frame:new int[]{
            0,1,2,255
        }){
            var h=new Harness(actual().getFirst());
            h.machine();
            h.pool.write(h.pool.state(41).moveWord(X,0x100).moveWord(Y,0x200).moveByte(MAP_FRAME,frame));
            h.pool.write(h.pool.state(42).moveByte(BOSS_FLAGS,0x94));
            h.boss.run(42);
            assertEquals(0x100-12+(frame==0?0:frame==1?8:16)-4,h.pool.state(42).centreX());
            assertEquals(0x84,h.pool.state(42).unsignedByte(BOSS_FLAGS));
        }
    }
    @Test void allDetachedPartsDeleteWhole64AtSigned240WithoutDrawingAndBeforeExtraExplosion() throws Exception {
        int[][] cases={
            {
                39,4
            },{
                40,10
            },{
                41,4
            },{
                42,4
            },{
                33,24
            },{
                34,14
            },{
                35,12
            }
        };
        for(int[] p:cases){
            var h=new Harness(actual().getFirst());
            h.machine();
            h.pool.write(h.pool.state(p[0]).moveByte(ROUTINE,p[1]).moveByte(BOSS_FLAGS,2).moveWord(Y,0x240).moveLong(BOSS_FALL_Y,0));
            h.events.clear();
            h.boss.run(p[0]);
            assertArrayEquals(new byte[64],h.pool.state(p[0]).copyBytes());
            assertFalse(h.events.contains("draw"));
            assertTrue(h.sounds.isEmpty());
        }
    }
    @Test void malformedLinkStopsAtReachedDereferenceRatherThanSubstitutingAFreeOrPlayerSlot() throws Exception {
        for(int bad:new int[]{
            0,0xD000,0xD7C0,0xD801,0xF000
        }){
            var h=new Harness(actual().getFirst());
            h.put(90,0x32,2);
            h.pool.write(h.pool.state(90).moveWord(BOSS_PARENT,bad));
            var before=h.placement.capture();
            var f=assertThrows(UnownedBoss.class,()->h.boss.run(90));
            assertEquals(Boundary.LINK_READ,f.boundary());
            assertEquals(before.pool().slots(),h.pool.capture().slots());
            assertTrue(h.events.isEmpty());
        }
    }
    @Test void callbackOrderIdentityAndReceiptsRejectReentryReplayAndMissingCompletion() throws Exception {
        var h=new Harness(actual().getFirst());
        h.put(90,0x2A,2);
        h.onRead=call->{
            assertTrue(h.boss.isCurrentCall(call));
            assertThrows(IllegalStateException.class,()->h.boss.run(90));
        };
        h.boss.run(90);
        var old=h.calls.getFirst();
        assertFalse(h.boss.isCurrentCall(old));
        assertThrows(IllegalStateException.class,()->((ReadCall)old).complete(0));
        for(int n=0;n<h.calls.size();n++)assertEquals(n,h.calls.get(n).order());
        var a=actual().getFirst();
        var p=placement(a);
        p.pool().write(p.pool().state(90).moveByte(ID,0x2A));
        var b=CdR13Boss.fromAct3FutureAssets(p,a,new Ports(){
            @Override public ReadReceipt read(ReadCall c){
                return null;
            }
        });
        assertThrows(IllegalStateException.class,()->b.run(90));
    }
    @Test void callbackPoolOrSavedBankMutationIsFencedAndOriginalFailureWinsWithoutRollback() throws Exception {
        var h=new Harness(actual().getFirst());
        h.put(90,0x2A,2);
        var fault=new IllegalArgumentException("original scalar read");
        h.onRead=c->{
            h.pool.write(h.pool.state(77).moveByte(ID,0x99));
            throw fault;
        };
        assertSame(fault,assertThrows(IllegalArgumentException.class,()->h.boss.run(90)));
        assertEquals(0x99,h.pool.state(77).id());
        assertEquals(1,fault.getSuppressed().length);
        var k=new Harness(actual().getFirst());
        k.put(90,0x2A,2);
        k.onRead=c->k.placement.bsetSavedFlag(1,2,CdObjectPlacementRuntime.SavedBit.LOADED);
        assertThrows(IllegalStateException.class,()->k.boss.run(90));
    }
    @Test void animationMalformedReachedIndexKeepsOriginalHeadAndSpeedBeforeSharedFault() throws Exception {
        var h=new Harness(actual().getFirst());
        h.put(90,0x2A,14);
        h.pool.write(h.pool.state(90).moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_FRAME,0xFF).moveByte(ANIMATION_TIME,0));
        var failure=assertThrows(UnsupportedOperationException.class,()->h.boss.run(90));
        assertFalse(failure instanceof UnownedBoss);
        assertEquals(7,h.pool.state(90).unsignedByte(ANIMATION_TIME));
        assertFalse(h.events.contains("draw"));
    }
    @Test void unchangedPoolStatusBankAndAllUnaddressedSourceBytesRemainExact() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        var before=h.placement.capture();
        h.pool.write(h.pool.state(42).moveByte(BOSS_FLAGS,0));
        before=h.placement.capture();
        h.boss.run(42);
        var after=h.placement.capture();
        assertEquals(before.status(),after.status());
        assertArrayEquals(before.savedFlags().copy(),after.savedFlags().copy());
        for(int i=0;i<96;i++)if(i!=42-32)assertEquals(before.pool().slots().get(i),after.pool().slots().get(i));
        assertOnly(before.pool().slots().get(10),after.pool().slots().get(10),1,8,9,12,13,44);
    }
    @Test void bodyWalkingBranchesRetainRepeatedCalfFlagClearAndAlternateNativeArms() throws Exception {
        for(int routine:new int[]{
            8,10
        })for(int phase:new int[]{
            0,64
        }){
            var h=new Harness(actual().getFirst());
            h.machine();
            h.pool.write(h.pool.state(32).moveByte(ROUTINE,routine).moveByte(BOSS_FLAGS,phase|2).moveByte(BOSS_PARAMETER,3).moveWord(X,0xB70));
            for(int n:new int[]{
                33,34,35,36,37,38
            })h.pool.write(h.pool.state(n).moveByte(BOSS_FLAGS,1));
            h.boss.run(32);
            assertEquals(2,h.pool.state(32).unsignedByte(BOSS_PARAMETER));
            assertEquals((phase^64)|2,h.pool.state(32).unsignedByte(BOSS_FLAGS));
            int leading=phase==0?33:36,following=phase==0?36:33;
            assertEquals(0,h.pool.state(leading).unsignedByte(BOSS_FLAGS)&1);
            assertEquals(0,h.pool.state(leading+2).unsignedByte(BOSS_FLAGS)&1);
            assertEquals(0,h.pool.state(following+1).unsignedByte(BOSS_FLAGS)&1);
            assertEquals(1,h.pool.state(following+2).unsignedByte(BOSS_FLAGS)&1,"shipped duplicate clears calf, not following foot");
        }
    }
    @Test void firstFastBodyCallRetainsActualShoulderClobberInsteadOfInventingTheSecondLegTarget() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(32).moveByte(ROUTINE,12).moveByte(BOSS_FLAGS,0));
        h.pool.write(h.pool.state(33).moveByte(ROUTINE,4));
        h.pool.write(h.pool.state(40).moveByte(ROUTINE,4));
        h.pool.write(h.pool.state(37).moveByte(ROUTINE,4));
        h.boss.run(32);
        assertEquals(2,h.pool.state(34).routine(),"Native continuation compares bumper42, not thigh33");
        assertEquals(4,h.pool.state(40).routine(),"Bumper42 routine2 skips both conditional routine stores");
        assertEquals(4,h.pool.state(37).routine(),"second-leg calf is untouched on this first call");
        assertEquals(8,h.pool.state(33).unsignedByte(BOSS_LIMB_SPEED));
        assertEquals(0x10000,h.pool.state(34).longBits(BOSS_VX_LONG));
        assertEquals(0x18000,h.pool.state(35).longBits(BOSS_FALL_Y));
    }
    @Test void firstFastBodyCallUsesTheConsumedBumperRoutineForBothConditionalStores() throws Exception {
        for(var source:actual()) {
            var h=new Harness(source);h.machine();
            h.pool.write(h.pool.state(32).moveByte(ROUTINE,12).moveByte(BOSS_FLAGS,0));
            h.pool.write(h.pool.state(42).moveByte(ROUTINE,4).moveWord(BOSS_NEXT,ptr(34)));
            h.boss.run(32);
            assertEquals(12,h.pool.state(33).routine(),"Native Thigh Init selects routineC; this first continuation consumes bumper42 instead");
            assertEquals(10,h.pool.state(34).routine(),"Actual bumper42.next receives native routineA");
            assertEquals(12,h.pool.state(40).routine(),"Actual shoulder39.next receives native routineC");
            assertEquals(0x80,h.pool.state(42).unsignedByte(BOSS_FLAGS)&0x80);
        }
    }
    @Test void firstFastBodyMissingArmPreservesFastStoresThenStopsAtTheConsumedZeroLink() throws Exception {
        for(var source:actual()) {
            var h=new Harness(source);h.machine();
            h.pool.write(h.pool.state(32).moveByte(ROUTINE,12).moveByte(BOSS_FLAGS,0));
            h.pool.write(h.pool.state(39).moveWord(BOSS_NEXT,0));
            var bank=h.placement.capture().savedFlags();
            var fault=assertThrows(UnownedBoss.class,()->h.boss.run(32));
            assertEquals(Boundary.LINK_READ,fault.boundary());assertEquals(0,fault.value());
            assertEquals(2,h.pool.state(32).unsignedByte(BOSS_FLAGS)&2);
            assertEquals(8,h.pool.state(33).unsignedByte(BOSS_LIMB_SPEED));
            assertEquals(0x10000,h.pool.state(34).longBits(BOSS_VX_LONG));
            assertEquals(0x18000,h.pool.state(35).longBits(BOSS_FALL_Y));
            assertEquals(0x80,h.pool.state(45).unsignedByte(BOSS_FLAGS)&0x80);
            assertFalse(h.events.contains("draw"));assertArrayEquals(bank.copy(),h.placement.capture().savedFlags().copy());
        }
    }
    @Test void plantedCalfUsesExistingWholeLongDeltasAndMovesExactlyThreeLiveAncestors() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(34).moveByte(ROUTINE,6).moveByte(BOSS_FLAGS,0x30).moveLong(BOSS_DX_LONG,0).moveLong(BOSS_VX_LONG,0x8000));
        var before=h.pool.capture();
        h.boss.run(34);
        assertEquals(0x8000,h.pool.state(34).longBits(BOSS_DX_LONG));
        for(int n:new int[]{
            33,32,90
        })assertEquals(before.slots().get(n-32).longBits(X_POSITION)-0x8000,h.pool.state(n).longBits(X_POSITION));
        assertEquals(before.slots().get(35-32),h.pool.state(35));
    }
    @Test void plantedFootReleaseMovesFourAncestorsAndClearsBothFractionWordsAtZero() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(35).moveByte(ROUTINE,6).moveByte(BOSS_FLAGS,0x30).moveLong(BOSS_DX_LONG,0x1234).moveLong(BOSS_DY_LONG,0x4000).moveLong(BOSS_VX_LONG,0x4000).moveLong(BOSS_FALL_Y,0x8000));
        var before=h.pool.capture();
        h.boss.run(35);
        assertEquals(0,h.pool.state(35).longBits(BOSS_DX_LONG));
        assertEquals(0,h.pool.state(35).longBits(BOSS_DY_LONG));
        assertEquals(8,h.pool.state(35).routine());
        for(int n:new int[]{
            34,33,32,90
        }){
            assertEquals(before.slots().get(n-32).longBits(X_POSITION)+0x4000,h.pool.state(n).longBits(X_POSITION));
            assertEquals(before.slots().get(n-32).longBits(Y_POSITION)-0x8000,h.pool.state(n).longBits(Y_POSITION));
        }
        // stored byte8 means native helper negates both deltas after the clamp
    }
    @Test void floorPrefixIsTheOriginalSharedProbeAndARejectedAngleStoreKeepsEarlierFootStores() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(35).moveByte(ROUTINE,2).moveByte(BOSS_FLAGS,0).moveLong(BOSS_DX_LONG,-0x80000).moveLong(BOSS_DY_LONG,0).moveLong(BOSS_VX_LONG,0x4000).moveLong(BOSS_FALL_Y,0x8000));
        h.values.put(Field.PRIMARY_ANGLE,77);
        var original=new IllegalStateException("original Frame angle owner");
        h.onStore=call->{
            if(call.field()==Field.PRIMARY_ANGLE)throw original;
        };
        assertSame(original,assertThrows(IllegalStateException.class,()->h.boss.run(35)));
        assertEquals(-0x84000,h.pool.state(35).longBits(BOSS_DX_LONG));
        assertEquals(0x8000,h.pool.state(35).longBits(BOSS_DY_LONG));
        assertEquals(0,h.values.get(Field.PRIMARY_ANGLE));
        assertFalse(h.events.contains("draw"));
    }
    @Test void thighSignedByteAndUnsignedAngleBranchesKeepTheirDifferentWrapRules() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(33).moveByte(ROUTINE,8).moveByte(BOSS_FLAGS,0).moveByte(BOSS_TIMER,1).moveByte(BOSS_LIMB_SPEED,2));
        h.boss.run(33);
        assertEquals(255,h.pool.state(33).unsignedByte(BOSS_TIMER));
        assertEquals(0,h.pool.state(33).unsignedByte(BOSS_FLAGS)&1,"BCC compares unsigned wrapped FF to18");
        h.pool.write(h.pool.state(33).moveByte(ROUTINE,2).moveByte(BOSS_TIMER,0x80));
        h.boss.run(33);
        assertEquals(0,h.pool.state(33).unsignedByte(BOSS_TIMER),"CMP.B0/BLE sees negative80");
    }
    @Test void originalSharedAnimationOutcomeAndDrawQueueResultAreDescriptiveAndImmutable() throws Exception {
        var h=new Harness(actual().getFirst());
        h.put(90,0x2A,14);
        var expected=CdObjectAnimation.step(new CdObjectAnimation.State(1,0,0,0,0,0,0,14,0),new CdObjectAnimation.AnimationTable(h.boss.assets().view(CdR13BossAssets.Table.EGGMAN_ANIMATIONS),0,5));
        var r=h.boss.run(90);
        var result=r.effects().stream().filter(AnimationReturned.class::isInstance).map(AnimationReturned.class::cast).findFirst().orElseThrow();
        assertEquals(expected,result.result());
        assertEquals(CdObjectDrawQueue.EnqueueResult.QUEUED,((DrawReturned)r.effects().getLast()).result());
        var cut=h.pool.capture();
        assertThrows(UnsupportedOperationException.class,()->r.effects().clear());
        for(var ignored:r.effects()){
        }
        assertEquals(cut.slots(),h.pool.capture().slots());
    }
    @Test void untouchedFollowingLegIsNotDereferencedBeforeTheLeadingReadyBranch() throws Exception {
        for(int routine:new int[]{
            8,10
        }){
            var h=new Harness(actual().getFirst());
            h.machine();
            h.pool.write(h.pool.state(32).moveByte(ROUTINE,routine).moveByte(BOSS_FLAGS,0).moveWord(BOSS_BRANCH,0));
            h.pool.write(h.pool.state(33).moveByte(BOSS_FLAGS,0));
            assertDoesNotThrow(()->h.boss.run(32));
            assertTrue(h.events.contains("draw"));
        }
    }
    @Test void delayedFootDereferenceKeepsThighAngleAndRoutineStoresAtTheActualThreshold() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(34).moveWord(BOSS_NEXT,0));
        h.pool.write(h.pool.state(33).moveByte(ROUTINE,4).moveByte(BOSS_TIMER,0x20).moveByte(BOSS_LIMB_SPEED,2));
        assertDoesNotThrow(()->h.boss.run(33));
        assertEquals(0x22,h.pool.state(33).unsignedByte(BOSS_TIMER));
        h.pool.write(h.pool.state(33).moveByte(BOSS_TIMER,0x56));
        assertEquals(Boundary.LINK_READ,assertThrows(UnownedBoss.class,()->h.boss.run(33)).boundary());
        assertEquals(0x58,h.pool.state(33).unsignedByte(BOSS_TIMER));
        assertEquals(6,h.pool.state(33).routine());
    }
    @Test void originalTerrainFloorContactSetsThreePlantFlagsAndOnlyTheActualRootShakeThenCommand() throws Exception {
        for(var a:actual()){
            var h=new Harness(a);
            h.machine();
            h.pool.write(h.pool.state(34).moveWord(X,0xBD2));
            h.pool.write(h.pool.state(35).moveByte(ROUTINE,4).moveByte(BOSS_FLAGS,0).moveWord(BOSS_DX,0).moveWord(BOSS_DY,0));
            var terrain=com.openggf.game.soniccd.player.CdPlayerTerrain.fromStageTerrain(a.terrain());
            int chosen=-1;
            CdObjectCollisionCheck.FloorDistance oracle=null;
            // Constructed bounded geometry control using genuine terrain, never a route/actor/default floor.
            for(int y=0;y<=0x300;y++){
                var sample=h.pool.state(35).moveWord(X,0xBD2-11).moveWord(Y,y+14);
                var result=CdObjectCollisionCheck.objGetFloorDist(terrain,sample);
                if(result.distance()<=0){
                    chosen=y;
                    oracle=result;
                    break;
                }
            }
            assertTrue(chosen>=0,"actual source terrain must supply this contact control");
            h.pool.write(h.pool.state(34).moveWord(Y,chosen));
            var contact=h.boss.run(35);
            assertEquals(oracle,contact.effects().stream().filter(FloorReturned.class::isInstance).map(FloorReturned.class::cast).findFirst().orElseThrow().result());
            assertEquals((chosen+14)&65535,h.pool.state(35).centreY(),"native contact never snaps object Y by distance");
            for(int n:new int[]{
                35,34,33
            })assertEquals(16,h.pool.state(n).unsignedByte(BOSS_FLAGS)&16);
            assertEquals(8,h.pool.state(90).unsignedByte(BOSS_SHAKE));
            assertEquals(List.of(CpuCommand.FOOT_IMPACT),h.commands);
            var actual=h.boss.run(35);
            assertTrue(actual.effects().stream().noneMatch(FloorReturned.class::isInstance),"planted routine4 returns before a second probe");
            assertNotNull(oracle);
        }
    }
    @Test void forearmSeventhByteCadenceUsesTheOtherOriginalExplosionAllocatorWithoutOffsets() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(41).moveByte(ROUTINE,4).moveByte(BOSS_FLAGS,2).moveByte(BOSS_TIMER,6).moveLong(BOSS_VX_LONG,0).moveLong(BOSS_FALL_Y,0).moveWord(Y,0x100));
        h.boss.run(41);
        assertEquals(0x18,h.pool.state(46).id());
        assertEquals(255,h.pool.state(46).unsignedByte(ROUTINE_2));
        assertEquals(h.pool.state(41).centreX(),h.pool.state(46).centreX());
        assertEquals(h.pool.state(41).centreY(),h.pool.state(46).centreY());
        assertEquals(List.of(FmSound.EXPLOSION),h.sounds);
        assertEquals(7,h.pool.state(41).unsignedByte(BOSS_TIMER));
    }
    @Test void defeat94SavesOriginalParentWordsAndSignalsBodyBeforeAnyPointAward() throws Exception {
        var h=new Harness(actual().getFirst());
        h.machine();
        h.pool.write(h.pool.state(90).moveByte(ROUTINE,6).moveByte(BOSS_COUNTER,93));
        var old=h.pool.state(90);
        h.boss.run(90);
        assertEquals(old.centreX(),h.pool.state(90).word(BOSS_SAVED_X));
        assertEquals(old.centreY(),h.pool.state(90).word(X_VELOCITY));
        assertEquals(3,h.pool.state(90).unsignedByte(ANIMATION));
        assertEquals(7,h.pool.state(90).unsignedByte(MAP_FRAME));
        assertEquals(1,h.pool.state(32).unsignedByte(BOSS_FLAGS)&1);
        assertTrue(h.points.isEmpty());
    }
    private static void assertOnly(CdObjectState before,CdObjectState after,int... allowed){
        var b=before.copyBytes();
        var a=after.copyBytes();
        var permitted=new HashSet<Integer>();
        for(int n:allowed)permitted.add(n);
        for(int n=0;n<64;n++)if(!permitted.contains(n))assertEquals(b[n],a[n],"unwritten byte "+n);
    }
    private static int whole(CdR13BossAssets.Part p){
        return switch(p){
            case EGGMAN->90;
            case BODY->32;
            case THIGH->33;
            case CALF->34;
            case FOOT->35;
            case SHOULDER->39;
            case ARM->40;
            case FOREARM->41;
            case BUMPER->42;
        };
    }
    private static int ptr(int whole){
        return new CdObjectState.Identity(whole).nativeAddress24()&0xFFFF;
    }
    private static CdObjectPlacementRuntime placement(CdR13FutureAssets a){
        return CdObjectPlacementRuntime.fromAct3FutureAssets(a,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));
    }
    private final class Harness implements Ports {
        final CdR13FutureAssets source;
        final CdObjectPlacementRuntime placement;
        final CdObjectPool pool;
        final CdR13Boss boss;
        final EnumMap<Field,Integer> values=new EnumMap<>(Field.class);
        final List<String> events=new ArrayList<>();
        final List<Call> calls=new ArrayList<>();
        final List<CpuCommand> commands=new ArrayList<>();
        final List<FmSound> sounds=new ArrayList<>();
        final List<Integer> points=new ArrayList<>();
        final List<CdR13BossAssets.Palette> palettes=new ArrayList<>();
        final CdObjectDrawQueue queue;
        Consumer<ReadCall> onRead;
        Consumer<StoreCall> onStore;
        RuntimeException failPoints,failFm;
        Harness(CdR13FutureAssets source){
            this.source=source;
            placement=placement(source);
            pool=placement.pool();
            for(var f:Field.values())values.put(f,0);
            values.put(Field.PLAYER_X,0x100);
            values.put(Field.PLAYER_Y,0x100);
            values.put(Field.PLAYER_FLAGS,2);
            values.put(Field.RIGHT_BOUND,source.bounds().right());
            values.put(Field.TARGET_RIGHT_BOUND,source.bounds().right());
            queue=new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots(){
                public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref){
                    throw new AssertionError("No DrawObjects/render in component test");
                }
                public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean bit){
                    var s=pool.state(ref.wholeIndex());pool.write(s.moveByte(SPRITE_FLAGS,bit?s.spriteFlags()|128:s.spriteFlags()&127));
                }
            },(address,staticPiece,frame)->{
                throw new AssertionError("No mapper/render ingress");
            },new byte[640]);
            queue.beginPass();
            boss=CdR13Boss.fromAct3FutureAssets(placement,source,this);
        }
        CdObjectState put(int w,int id,int routine){
            var s=pool.state(w).moveByte(ID,id).moveByte(ROUTINE,routine).moveByte(ANIMATION,1).moveWord(X,0xBD2).moveWord(Y,0x100);
            pool.write(s);
            return s;
        }
        void admit(int parent){
            put(parent,0x2A,14);
            pool.write(pool.state(parent).moveByte(BOSS_COUNTER,59));
            boss.run(parent);
        }
        void machine(){
            admit(90);
            for(int n=32;n<46;n++)boss.run(n);
            events.clear();
            calls.clear();
            commands.clear();
            sounds.clear();
        }
        void call(Call c){
            assertTrue(boss.isCurrentCall(c));
            calls.add(c);
            assertSame(pool.state(c.expectedSource().identity().wholeIndex()),c.expectedSource());
            assertEquals(placement.status(),c.actualCut().status());
            assertEquals(pool.capture().slots(),c.actualCut().pool().slots());
        }
        public ReadReceipt read(ReadCall c){
            call(c);
            events.add("read:"+c.field());
            if(onRead!=null)onRead.accept(c);
            var receipt=c.complete(values.get(c.field()));
            assertFalse(boss.isCurrentCall(c));
            assertThrows(IllegalStateException.class,()->c.complete(receipt.value()));
            return receipt;
        }
        public StoreReceipt store(StoreCall c){
            call(c);
            events.add("store:"+c.field());
            int before=values.get(c.field());
            int after=c.form()==StoreForm.XOR?before^c.operand():c.operand();
            values.put(c.field(),after);
            if(onStore!=null)onStore.accept(c);
            return c.complete(before,after);
        }
        public Completion loadPlc(PlcCall c){
            call(c);
            events.add("plc"+c.list().nativeId());
            return c.complete();
        }
        public Completion loadBossPalette(PaletteCall c){
            call(c);
            events.add("palette5");
            palettes.add(c.palette());
            return c.complete();
        }
        public Completion addPoints(PointsCall c){
            call(c);
            points.add(c.nativeUnits());
            if(failPoints!=null)throw failPoints;
            return c.complete();
        }
        public Completion fm(FmCall c){
            call(c);
            events.add("fm:"+c.sound());
            sounds.add(c.sound());
            if(failFm!=null)throw failFm;
            return c.complete();
        }
        public Completion subCpu(CpuCall c){
            call(c);
            commands.add(c.command());
            return c.complete();
        }
        public Completion loadCapsulePalette(CapsulePaletteCall c){
            call(c);
            events.add("capsulePalette");
            return c.complete();
        }
        public DrawReceipt draw(DrawCall c){
            call(c);
            events.add("draw");
            var s=c.expectedSource();
            var e=queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(s.identity().wholeIndex()),s.spriteFlags(),s.centreX(),s.centreY(),s.unsignedByte(Y_RADIUS),s.unsignedByte(WIDTH),s.unsignedByte(PRIORITY)),new CdObjectDrawQueue.CameraWords(0xAC0,0));
            return c.complete(e);
        }
    }
    private List<CdR13FutureAssets> actual()throws Exception{
        if(originals!=null)return originals;
        String root=System.getProperty("soniccd.media.root");
        assumeTrue(root!=null&&!root.isBlank(),"Optional configured original USA media");
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size(),"configured source must contain exactly one USA CUE");
            cue=cues.getFirst();
        }
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            originals=List.of(CdR13FutureAssets.fromPrograms(programs,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(programs,CdStagePayload.R13D.stage()));
        }
        return originals;
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
