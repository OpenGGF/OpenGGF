package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;

/** Source-only candidates. Constructed owner controls are not Module/Frame/Ready evidence. */
class TestCdOriginalPlayerSemanticRuntime {
    private CdStagePlayerSource source() throws Exception {
        String root=System.getProperty("soniccd.media.root");
        assumeTrue(root!=null&&!root.isBlank(),"Optional original USA media absent");
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))) {
            var cues=paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size());cue=cues.getFirst();
        }
        try(var assets=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            return CdStagePlayerSource.fromProgramme(assets,CdR4DataLayout.Programme.R41A.payload().stage());
        }
    }
    @Test void dataFactoryFenceRemainsAndLoadingDoesNotGrantOrdinaryService() throws Exception {
        var s=source();assertEquals(0x200,s.nativeZoneActWord());
        assertThrows(CdPlayerBody.UnsupportedBody.class,()->CdPlayerBody.fromStageSource(s));
        assertThrows(CdPlayerMovement.UnsupportedMotion.class,()->CdPlayerMovement.fromStageSource(s));
        var h=new Harness(s);var loading=CdPlayerBody.forOriginalLoading(s);
        var init=loading.step(h.state,h.environment);
        assertEquals(CdPlayerBody.Boundary.INIT_RETURN,init.boundary());
        assertEquals(s.mappingAddress(),init.state().header().mappingAddress());
        assertEquals(2,init.state().motion().routine());
        assertThrows(CdPlayerBody.UnsupportedBody.class,()->loading.step(init.state(),init.environment()));
        assertThrows(CdPlayerMovement.UnsupportedMotion.class,()->CdPlayerMovement.forOriginalLoading(s)
            .step(init.state().motion(),new CdPlayerMovement.Input(0,0),init.environment().movement()));
    }
    @Test void waterCallIsBetweenHistoryAndCommonAngleAnimationTail() throws Exception {
        var h=ready(source());h.flags(0);h.y(0x300);h.vy(0x400);h.water.put(Value.WATER_Y,0x280);
        h.run();
        assertEquals(0x40,h.state.motion().flags()&0x40);
        assertEquals(0x100,h.state.motion().yVelocity());
        assertEquals(0x300,h.environment.movement().physics().topSpeed());
        assertEquals(4,h.state.history().index());assertEquals(0x300,h.state.history().bytes().unsignedWord(2));
        assertEquals(0x21,h.reserved.read(B.ID));assertEquals(0x81,h.reserved.read(B.SUBTYPE));
        assertEquals(0xB,h.child.read(B.ID));assertEquals(0x80,h.child.read(W.X));
        assertTrue(h.events.indexOf("draw")<h.events.indexOf("read:WATER_Y"));
        assertEquals(4,h.historyAtWater);assertEquals(0,h.animationIndexAtWater);
        assertEquals(1,h.state.animation().scriptIndex());
    }
    @Test void dryPositiveVelocityDoesNotReadFlagsResetAirOrAllocate() throws Exception {
        var h=ready(source());h.y(0x100);h.vy(0x100);h.water.put(Value.WATER_Y,0x280);h.run();
        assertFalse(h.events.contains("reserved"));assertFalse(h.events.contains("spawn"));
        assertEquals(0x100,h.state.motion().yVelocity());
    }
    @Test void exitDoublesAndCapsVelocityBeforeOneFullPoolSearch() throws Exception {
        var h=ready(source());h.flags(0x40);h.y(0x100);h.vy(-0x900);h.water.put(Value.WATER_Y,0x280);h.run();
        assertEquals(-0x1000,h.state.motion().yVelocity());assertEquals(0,h.state.motion().flags()&0x40);
        assertEquals(1,Collections.frequency(h.events,"spawn"));assertEquals(0x600,h.environment.movement().physics().topSpeed());
    }
    @Test void wetRepeatReturnsBeforeAirPhysicsAndAllocation() throws Exception {
        var h=ready(source());h.flags(0x40);h.y(0x300);h.vy(0x400);h.water.put(Value.WATER_Y,0x280);h.run();
        assertEquals(0x400,h.state.motion().yVelocity());assertFalse(h.events.contains("reserved"));
        assertFalse(h.events.contains("spawn"));assertFalse(h.events.contains("write:MAX_SPEED"));
    }
    @Test void firstAirReadFaultRetainsWaterFlagAndPublishedHistoryButNoTailOrRetry() throws Exception {
        var h=ready(source());h.y(0x300);h.water.put(Value.WATER_Y,0x280);
        var fault=new IllegalStateException("actual missing drown word");h.failField=Value.DROWN_TIMER;h.failure=fault;
        assertSame(fault,assertThrows(IllegalStateException.class,h::run));
        assertEquals(0x40,h.state.motion().flags()&0x40);assertEquals(4,h.state.history().index());
        assertEquals(0,h.state.animation().scriptIndex());assertFalse(h.events.contains("reserved"));
        assertThrows(IllegalStateException.class,h::run);assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(h.runtime));
    }
    @Test void reentryAndForeignRuntimeAreRefusedAndFirstFaultKeepsDistinctGuardFault() throws Exception {
        var h=ready(source());h.water.put(Value.WATER_Y,0x700); // Explicit dry component fixture; no production default.
        h.onDraw=()->{
            assertThrows(IllegalStateException.class,h::run);
            var foreign=CdPlayerSemanticRuntime.fromOriginalProgramme(h.source,new Harness(h.source));
            assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(foreign));
        };h.run();assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(h.runtime));
    }
    @Test void postPrimitiveGuardFaultDoesNotRollBackItsCompletedStore() throws Exception {
        var h=ready(source());h.y(0x300);h.water.put(Value.WATER_Y,0x280);h.invalidateOnFlags=true;
        assertThrows(IllegalStateException.class,h::run);
        assertEquals(0x40,h.state.motion().flags()&0x40);assertEquals(4,h.state.history().index());
        assertFalse(h.events.contains("reserved"));
    }
    @Test void ordinaryRuntimePureApiCannotBypassCurrentPorts() throws Exception {
        var h=ready(source());
        assertThrows(CdPlayerBody.UnsupportedBody.class,()->h.body.step(h.state,h.environment));
        assertThrows(CdPlayerMovement.UnsupportedMotion.class,()->CdPlayerMovement.fromOriginalProgramme(h.source,h.runtime)
            .step(h.state.motion(),new CdPlayerMovement.Input(0,0),h.environment.movement()));
    }
    @Test void wetAndDryNativeJumpUseLatchedInputAndSoundBeforeRollSize() throws Exception {
        for(boolean wet:new boolean[]{false,true}) {
            var h=ready(source());h.control(0x80);h.flags(wet?0x40:0);h.y(0x40);
            h.water.put(Value.WATER_Y,wet?0:0x400);
            h.environment=new CdPlayerBody.Environment(h.environment.movement(),new CdPlayerBody.Controls(0x10,0x10,4,4,0),h.environment.globals(),h.environment.dynamicSlots());
            h.run();
            assertEquals(wet?-0x380:-0x680,h.state.motion().yVelocity());
            assertEquals(0x45,h.state.motion().centreY16_16()>>>16);
            assertEquals(1,h.state.motion().playerJump());assertEquals(14,h.state.motion().yRadius());
            assertEquals(0x10,h.environment.controls().logicalHeld());assertEquals(0x10,h.environment.controls().logicalTap());
            assertEquals(19,h.jumpSoundRadius);assertEquals(1,Collections.frequency(h.events,"sound:146"));
        }
    }
    @Test void jumpReleaseHeightUsesWetCapBeforeFallGravityAndWaterTail() throws Exception {
        for(boolean wet:new boolean[]{false,true}) {
            var h=ready(source());h.control(0x80);h.flags(6|(wet?0x40:0));h.y(0x40);h.vy(-0x600);h.jump(1);
            h.water.put(Value.WATER_Y,wet?0:0x400);h.run();
            assertEquals(wet?-0x1F0:-0x3C8,h.state.motion().yVelocity());
            assertEquals((0x40<<16)+((wet?-0x200:-0x400)<<8),h.state.motion().centreY16_16());
            assertEquals(0,h.state.motion().charge());
        }
    }
    @Test void heldJumpDoesNotReleaseAndControlLockPreservesActualLogicalWord() throws Exception {
        var h=ready(source());h.control(0x80);h.flags(6|0x40);h.y(0x40);h.vy(-0x600);h.jump(1);h.water.put(Value.WATER_Y,0);
        h.environment=new CdPlayerBody.Environment(h.environment.movement(),new CdPlayerBody.Controls(0,0,0x10,0,1),h.environment.globals(),h.environment.dynamicSlots());
        h.run();assertEquals(-0x5F0,h.state.motion().yVelocity());assertEquals(0x10,h.environment.controls().logicalHeld());
    }
    @Test void hurtUsesTwoWetWordStoresAndNeverChecksWaterOrResetsAir() throws Exception {
        var h=ready(source());h.control(0x80);h.flags(2|0x40);h.y(0x40);h.vy(-0x100);h.routine(4);h.run();
        assertEquals(-0xF0,h.state.motion().yVelocity());assertEquals(4,h.state.history().index());
        assertFalse(h.events.contains("read:WATER_Y"));assertFalse(h.events.contains("reserved"));
        assertEquals(1,Collections.frequency(h.events,"draw"));
    }
    @Test void landedZeroVelocityEntryResetsAirAndPhysicsWithoutSplashSearch() throws Exception {
        var h=ready(source());h.y(0x300);h.vy(0);h.water.put(Value.WATER_Y,0x280);h.run();
        assertEquals(0x40,h.state.motion().flags()&0x40);assertEquals(30,h.water.get(Value.DROWN_TIMER));
        assertEquals(0,h.reserved.read(B.COUNTDOWN));assertEquals(0x21,h.reserved.read(B.ID));
        assertFalse(h.events.contains("spawn"));
    }
    @Test void refusedPrefixPublicationDoesNotDrawOrInventCompletedWrites() throws Exception {
        var h=ready(source());var before=h.state;var environment=h.environment;h.refusePublish=true;
        assertThrows(IllegalStateException.class,h::run);assertEquals(before,h.state);assertEquals(environment,h.environment);
        assertFalse(h.events.contains("draw"));assertFalse(h.events.contains("read:WATER_Y"));
    }
    @Test void failedPrimitiveKeepsFirstThrowableAndOneDistinctSuppressedGuardFailure() throws Exception {
        var h=ready(source());h.y(0x300);h.water.put(Value.WATER_Y,0x280);h.failField=Value.DROWN_TIMER;
        h.failure=new IllegalStateException("drown read");h.guardFailure=new IllegalStateException("scope retired during read");
        assertSame(h.failure,assertThrows(IllegalStateException.class,h::run));
        assertArrayEquals(new Throwable[]{h.guardFailure},h.failure.getSuppressed());
        assertEquals(0x40,h.state.motion().flags()&0x40);
    }
    @Test void sameThrowableFromGuardIsNeverSelfSuppressed() throws Exception {
        var h=ready(source());h.y(0x300);h.water.put(Value.WATER_Y,0x280);h.failField=Value.DROWN_TIMER;
        h.failure=new IllegalStateException("one physical fault");h.guardFailure=h.failure;
        assertSame(h.failure,assertThrows(IllegalStateException.class,h::run));assertEquals(0,h.failure.getSuppressed().length);
    }
    @Test void equalReopenedDataCannotReplaceRetainedSourceBinding() throws Exception {
        var h=ready(source());var other=source();assertNotSame(h.source,other);
        assertThrows(IllegalArgumentException.class,()->CdPlayerBody.fromOriginalProgramme(other,h.runtime));
        assertThrows(IllegalArgumentException.class,()->CdPlayerMovement.fromOriginalProgramme(other,h.runtime));
    }
    @Test void waterEntryCallsActualResetAirWithBossMusicPriorityBeforeTimerAndReservedWrites() throws Exception {
        var h=ready(source());h.y(0x300);h.water.put(Value.WATER_Y,0x280);h.water.put(Value.DROWN_TIMER,12);
        h.water.put(Value.ZONE_WORD,0x200);h.water.put(Value.INVINCIBLE,1);h.water.put(Value.BOSS_STARTED,1);
        var g=h.environment.globals();h.environment=new CdPlayerBody.Environment(h.environment.movement(),h.environment.controls(),
            new CdPlayerBody.Globals(g.levelFrames(),g.cameraXCentre(),g.timeAttackMode(),g.debugMode(),g.debugCheat(),g.timeWarpTimer(),1,g.bossMusic()),h.environment.dynamicSlots());h.run();
        assertEquals(1,Collections.frequency(h.events,"music:140"));
        assertTrue(h.events.indexOf("music:140")<h.events.indexOf("write:DROWN_TIMER"));
        assertTrue(h.events.indexOf("write:DROWN_TIMER")<h.events.indexOf("reserved"));
        assertEquals(30,h.water.get(Value.DROWN_TIMER));
    }
    @Test void afterScrollCurrentLeafUsesNativeJumpByteAndActualRepeatedHeldReads() throws Exception {
        // Deliberate leaf control, NOT a World after-scroll invocation or a Body pre-hook.
        var h=ready(source());h.control(0);h.y(0x200);h.jump(1);
        h.water.put(Value.DEBUG_MODE,0);h.water.put(Value.VBLANK_LOW,0x67);h.water.put(Value.HELD,0xA);h.water.put(Value.WATER_CURRENT,0);
        byte[] records={0,0x40,1,0,1,0,3,0,1,0,(byte)0xFF,(byte)0x80,0,0,(byte)0xFF};
        CdR4WaterCurrents.check(h,CdAssetBytes.copyOf(records),index->{throw new AssertionError("mode0 never reads switch bank");});
        assertEquals(1,h.water.get(Value.WATER_CURRENT));assertEquals(0,h.state.motion().playerJump());
        assertEquals(0x100,h.state.motion().xVelocity());assertEquals(-0x80,h.state.motion().yVelocity());
        assertEquals(0x81,h.state.motion().centreX16_16()>>>16);assertEquals(0x201,h.state.motion().centreY16_16()>>>16);
        assertEquals(0xF,h.state.motion().animation());assertEquals(4,Collections.frequency(h.events,"read:HELD"));
    }
    @Test void deadAndRestartNonreadBranchesDoNotBorrowMissingLevelOwners() throws Exception {
        var dead=ready(source());dead.routine(6);dead.y(0x40);dead.vy(0x100);dead.run();
        assertEquals(0x138,dead.state.motion().yVelocity());assertEquals(4,dead.state.history().index());
        assertEquals(1,Collections.frequency(dead.events,"draw"));assertFalse(dead.events.contains("read:WATER_Y"));
        var restart=ready(source());restart.routine(8);var before=restart.state;restart.run();
        assertEquals(before,restart.state);assertFalse(restart.events.contains("draw"));
    }
    @Test void deadMissingHudStoreKeepsEarlierNativeActorPrefixAndDoesNotEnterHistoryTail() throws Exception {
        var h=ready(source());h.routine(6);h.y(0x700);h.vy(0);
        assertThrows(UnsupportedOperationException.class,h::run);
        assertEquals(8,h.state.motion().routine());assertEquals(-0x38,h.state.motion().yVelocity());
        assertEquals(0,h.state.history().index());assertFalse(h.events.contains("draw"));
    }
    @Test void nativeDrawClearReturnsThroughMainHurtAndDeadWithoutStaleBodyRepublication() throws Exception {
        var s=source();
        for(int routine:new int[]{2,4,6}) {
            var h=ready(s);h.water.put(Value.WATER_Y,0x700);h.routine(routine);
            if(routine==4){h.control(0x80);h.flags(2|0x40);h.y(0x40);h.vy(-0x100);}
            if(routine==6){h.y(0x40);h.vy(0x100);}
            spriteFlags(h,0x84);
            h.onDraw=()->{assertEquals(0x84,h.state.motion().spriteFlags());spriteFlags(h,0x04);};
            h.run();
            assertEquals(0x04,h.state.motion().spriteFlags(),"Completed native clear must survive the Body tail for routine "+routine);
            assertEquals(1,Collections.frequency(h.events,"draw"));assertEquals(4,h.state.history().index());
        }
    }
    @Test void drawRejectsAnUnrelatedActorWriteAndKeepsItsActualRejectedPrefix() throws Exception {
        var h=ready(source());h.water.put(Value.WATER_Y,0x700);spriteFlags(h,0x84);
        h.onDraw=()->{spriteFlags(h,0x04);h.vx(0x123);};
        var failure=assertThrows(IllegalStateException.class,h::run);
        assertTrue(failure.getMessage().contains("DrawObject"));
        assertEquals(0x04,h.state.motion().spriteFlags());assertEquals(0x123,h.state.motion().xVelocity());
        assertEquals(0,h.state.history().index());assertFalse(h.events.contains("read:WATER_Y"));
    }
    @Test void drawRejectsAnEnvironmentWriteAndKeepsItsActualRejectedPrefix() throws Exception {
        var h=ready(source());h.water.put(Value.WATER_Y,0x700);spriteFlags(h,0x84);
        int originalSpeed=h.environment.movement().physics().topSpeed();
        h.onDraw=()->{spriteFlags(h,0x04);var p=h.environment.movement().physics();h.physics(new CdPlayerMovement.Physics(p.topSpeed()+1,p.acceleration(),p.deceleration()));};
        var failure=assertThrows(IllegalStateException.class,h::run);
        assertTrue(failure.getMessage().contains("DrawObject"));
        assertEquals(0x04,h.state.motion().spriteFlags());assertEquals(originalSpeed+1,h.environment.movement().physics().topSpeed());
        assertEquals(0,h.state.history().index());assertFalse(h.events.contains("read:WATER_Y"));
    }
    @Test void drawRejectsSettingOnScreenInsteadOfClearingIt() throws Exception {
        var h=ready(source());h.water.put(Value.WATER_Y,0x700);spriteFlags(h,0x04);
        h.onDraw=()->spriteFlags(h,0x84);
        var failure=assertThrows(IllegalStateException.class,h::run);
        assertTrue(failure.getMessage().contains("DrawObject"));assertEquals(0x84,h.state.motion().spriteFlags());
        assertEquals(0,h.state.history().index());assertFalse(h.events.contains("read:WATER_Y"));
    }
    @Test void completedDrawMustClearAnExistingOnScreenBit() throws Exception {
        var h=ready(source());h.water.put(Value.WATER_Y,0x700);spriteFlags(h,0x84);
        var failure=assertThrows(IllegalStateException.class,h::run);
        assertTrue(failure.getMessage().contains("DrawObject"));assertEquals(0x84,h.state.motion().spriteFlags());
        assertEquals(0,h.state.history().index());
    }
    @Test void drawClearThenOriginalThrowRetainsTheExactPrefixAndThrowableWithoutRetry() throws Exception {
        var h=ready(source());h.water.put(Value.WATER_Y,0x700);spriteFlags(h,0x84);
        var original=new UnsupportedOperationException("actual Draw callback fault after native clear");
        h.onDraw=()->{spriteFlags(h,0x04);throw original;};
        assertSame(original,assertThrows(UnsupportedOperationException.class,h::run));
        assertEquals(0,original.getSuppressed().length);assertEquals(0x04,h.state.motion().spriteFlags());
        assertEquals(0,h.state.history().index());assertFalse(h.events.contains("read:WATER_Y"));
        assertEquals(1,Collections.frequency(h.events,"draw"));assertThrows(IllegalStateException.class,h::run);
        assertEquals(1,Collections.frequency(h.events,"draw"));
    }
    /** Constructed component storage only; never a production Frame or private Draw occurrence. */
    private static void spriteFlags(Harness h,int value) {
        var s=h.state;var m=s.motion();
        h.state=new CdPlayerBody.State(new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),
                m.routine(),m.angle(),m.flags(),value,m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),
                m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle()),
                s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());
    }
    private Harness ready(CdStagePlayerSource s) {
        var h=new Harness(s);var init=CdPlayerBody.forOriginalLoading(s).step(h.state,h.environment);
        h.state=init.state();h.environment=init.environment();h.control(0x81); // Native controlled branch avoids MODE; water remains real.
        h.runtime=CdPlayerSemanticRuntime.fromOriginalProgramme(s,h);h.body=CdPlayerBody.fromOriginalProgramme(s,h.runtime);return h;
    }
    static final class Bytes implements Slot {
        final byte[] bytes=new byte[64];Runnable changed=()->{};
        int get(int at,int count){int v=0;for(int i=0;i<count;i++)v=(v<<8)|(bytes[at+i]&255);return v;}
        void put(int at,int n,int v){for(int i=n-1;i>=0;i--){bytes[at+i]=(byte)v;v>>>=8;}changed.run();}
        public int read(B f){return get(f.offset,1);}public int read(W f){return get(f.offset,2);}public int read(L f){return get(f.offset,4);}
        public void write(B f,int v){put(f.offset,1,v);}public void write(W f,int v){put(f.offset,2,v);}public void write(L f,int v){put(f.offset,4,v);}
    }
    static final class Harness implements CdPlayerSemanticRuntime.Ports,Calls {
        final CdStagePlayerSource source;CdPlayerSemanticRuntime runtime;CdPlayerBody body;
        CdPlayerBody.State state;CdPlayerBody.Environment environment;CdPlayerSemanticRuntime.Call saved;
        final Bytes primary=new Bytes(),reserved=new Bytes(),child=new Bytes();
        final EnumMap<Value,Integer> water=new EnumMap<>(Value.class);final List<String> events=new ArrayList<>();
        long generation=7;boolean invalid,invalidateOnFlags,refusePublish;int historyAtWater,animationIndexAtWater,jumpSoundRadius;
        Value failField;RuntimeException failure,guardFailure;Runnable onDraw=()->{};
        Harness(CdStagePlayerSource s){source=s;
            var m=new CdPlayerMotionState(0x800000,0x1000000,0,0,0,0,0,0,4,9,19,0,0,0,0,0,0,0,0,0);
            state=new CdPlayerBody.State(m,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0,0,0,0),
                new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0),0,new CdPlayerBody.ObjectLinks(0,0),0);
            var movement=new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,12,0x80),new CdPlayerMovement.Bounds(0,0x1C97,0x510,0),
                new CdPlayerMovement.Gates(0x200,1,0,0,0,0,0),new CdPlayerMovement.Globals(0,0x60,0x12,0x34,0,0,0,0,0,0));
            environment=new CdPlayerBody.Environment(movement,new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(1,0xA0,0,0,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
            // Deliberate harness storage; none of these values is a production default.
            water.put(Value.ZONE,2);water.put(Value.ACT,0);water.put(Value.DROWN_TIMER,30);water.put(Value.INVINCIBLE,0);water.put(Value.BOSS_STARTED,0);water.put(Value.TIME_ZONE,1);
        }
        void run(){body.runOriginalProgramme();}
        public long generation(){return generation;}
        public void requireCurrent(CdPlayerSemanticRuntime.Call c){c.requireCurrent(runtime);saved=c;if(invalid)throw guardFailure==null?new IllegalStateException("retired harness owner"):guardFailure;}
        public CdPlayerBody.State state(CdPlayerSemanticRuntime.Call c){return state;}
        public CdPlayerBody.Environment environment(CdPlayerSemanticRuntime.Call c){return environment;}
        public void publish(CdPlayerSemanticRuntime.Call c,CdPlayerBody.State before,CdPlayerBody.Environment be,CdPlayerBody.State next,CdPlayerBody.Environment ne){assertEquals(before,state);assertEquals(be,environment);if(refusePublish)throw new IllegalStateException("refused complete preimage");state=next;environment=ne;}
        public Optional<CdPlayerMovement.ObjectContext> objects(CdPlayerSemanticRuntime.Call c){return Optional.empty();}
        public void flipperAnimation(CdPlayerSemanticRuntime.Call c,CdPlayerMovement.SlotPatch p){throw new UnsupportedOperationException("actual stood owner not bound");}
        public Calls water(CdPlayerSemanticRuntime.Call c){historyAtWater=state.history().index();animationIndexAtWater=state.animation().scriptIndex();return this;}
        public void draw(CdPlayerSemanticRuntime.Call c){events.add("draw");onDraw.run();}
        public void sound(CdPlayerSemanticRuntime.Call c,int id){events.add("sound:"+id);if(id==0x92)jumpSoundRadius=state.motion().yRadius();}
        public void restoreStageMusic(CdPlayerSemanticRuntime.Call c){events.add("stageMusic");}
        public CdPlayerCharge.Result moveGroundCharge(CdPlayerSemanticRuntime.Call c,CdPlayerCharge.State before,CdPlayerCharge.Input input){throw new UnsupportedOperationException("current shared charge owner not bound in controlled harness");}
        public CdPlayerCharge.Result moveRollCharge(CdPlayerSemanticRuntime.Call c,CdPlayerCharge.State before,CdPlayerCharge.Input input){throw new UnsupportedOperationException("current shared roll owner not bound in controlled harness");}
        public CdPlayerCharge.Result startRoll(CdPlayerSemanticRuntime.Call c,CdPlayerCharge.State before,CdPlayerCharge.Body input){throw new UnsupportedOperationException("current shared start-roll owner not bound in controlled harness");}
        public void timeWarp(CdPlayerSemanticRuntime.Call c){assertEquals(0,environment.movement().gates().timeWarpDirection());events.add("timeWarp");}
        public void collideObjects(CdPlayerSemanticRuntime.Call c){throw new UnsupportedOperationException("actual TT touch owner absent in this controlled harness");}
        public CdPlayerBody.LevelContext level(CdPlayerSemanticRuntime.Call c){throw new UnsupportedOperationException("actual level owner intentionally unbound in harness");}
        public void storeLevel(CdPlayerSemanticRuntime.Call c,CdPlayerBody.LevelField f,int v){throw new UnsupportedOperationException("level store unbound");}
        public void spawnGameOver(CdPlayerSemanticRuntime.Call c){throw new UnsupportedOperationException("full96 game-over allocation unbound");}
        public void restartSoundFlag(CdPlayerSemanticRuntime.Call c){throw new UnsupportedOperationException("sound flag unbound");}
        public void resetSavedObjectFlags(CdPlayerSemanticRuntime.Call c){throw new UnsupportedOperationException("placement flag owner unbound");}
        public void completed(CdPlayerSemanticRuntime.Call c,CdPlayerBody.Boundary b){events.add("completed:"+b);}
        public int read(Value f){events.add("read:"+f);if(f==failField){if(guardFailure!=null)invalid=true;throw failure;}return Objects.requireNonNull(water.get(f),"unbound "+f);}
        public void write(Value f,int v){events.add("write:"+f);water.put(f,v);if(f==Value.WATER_CURRENT){var e=environment;var m=e.movement();var g=m.gates();environment=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),m.bounds(),new CdPlayerMovement.Gates(g.zoneAct(),g.timeZone(),g.miniSonic(),g.waterSlide(),v,g.speedShoes(),g.timeWarpDirection()),m.globals()),e.controls(),e.globals(),e.dynamicSlots());}if(f==Value.MAX_SPEED||f==Value.ACCELERATION||f==Value.DECELERATION){var e=environment.movement();var p=e.physics();physics(new CdPlayerMovement.Physics((short)(f==Value.MAX_SPEED?v:p.topSpeed()),(short)(f==Value.ACCELERATION?v:p.acceleration()),(short)(f==Value.DECELERATION?v:p.deceleration())));}}
        void physics(CdPlayerMovement.Physics p){var e=environment;var m=e.movement();environment=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(p,m.bounds(),m.gates(),m.globals()),e.controls(),e.globals(),e.dynamicSlots());}
        public Slot player(){var m=state.motion();primary.changed=()->{};
            primary.write(B.ID,state.header().slotId());primary.write(B.ANIM,m.animation());primary.write(B.FLAGS,m.flags());primary.write(B.CONTROL,m.playerControl());
            primary.write(B.ROUTINE,m.routine());primary.write(B.PHASE,m.charge());primary.write(B.CHARGE,m.playerJump());
            primary.write(W.X,m.centreX16_16()>>>16);primary.write(W.Y,m.centreY16_16()>>>16);primary.write(W.VX,m.xVelocity()&65535);primary.write(W.VY,m.yVelocity()&65535);
            primary.changed=()->{var a=state;var old=a.motion();
                state=new CdPlayerBody.State(new CdPlayerMotionState((primary.read(W.X)<<16)|(old.centreX16_16()&65535),(primary.read(W.Y)<<16)|(old.centreY16_16()&65535),
                    (short)primary.read(W.VX),(short)primary.read(W.VY),old.groundVelocity(),primary.read(B.ROUTINE),old.angle(),primary.read(B.FLAGS),old.spriteFlags(),old.xRadius(),old.yRadius(),
                    primary.read(B.ANIM),old.previousAnimation(),primary.read(B.CHARGE),primary.read(B.PHASE),old.moveLock(),old.stick(),primary.read(B.CONTROL),old.previousPrimaryAngle(),old.previousSecondaryAngle()),
                    a.animation(),a.header(),a.timers(),a.history(),a.rotationDistance(),a.objectLinks(),a.rotationAngle());
                if(invalidateOnFlags)invalid=true;};return primary;
        }
        public Slot reservedBubbles(){events.add("reserved");return reserved;}
        public Optional<Slot> spawn(){events.add("spawn");return Optional.of(child);}
        public int random(){throw new AssertionError("nonread RNG");}
        public void sound(int id){events.add("sound:"+id);}public void music(int id){events.add("music:"+id);}
        public CdPlayerMath.SineCosine sineCosine(int angle){return new CdPlayerMath(source.player()).calcSine(angle);}
        public void setPlayerGround(){throw new AssertionError("nonread ground");}public void move(Slot s){throw new AssertionError("nonread move");}
        public void animate(Slot s,Animation a){throw new AssertionError("nonread animate");}public void draw(Slot s){throw new AssertionError("nonread draw");}public void delete(Slot s){throw new AssertionError("nonread delete");}
        void flags(int f){replace(null,null,null,f,null);}void control(int c){replace(null,null,null,null,c);}void y(int y){replace(y,null,null,null,null);}void vy(int yv){replace(null,null,yv,null,null);}void vx(int xv){replace(null,xv,null,null,null);}
        void jump(int jump){changeRoutineAndJump(state.motion().routine(),jump);}
        void routine(int routine){changeRoutineAndJump(routine,state.motion().playerJump());}
        void changeRoutineAndJump(int routine,int jump){var s=state;var m=s.motion();state=new CdPlayerBody.State(new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),routine,m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),jump,m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle()),s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());}
        void replace(Integer y,Integer xv,Integer yv,Integer flags,Integer control){var s=state;var m=s.motion();state=new CdPlayerBody.State(new CdPlayerMotionState(m.centreX16_16(),y==null?m.centreY16_16():y<<16,xv==null?m.xVelocity():xv,yv==null?m.yVelocity():yv,m.groundVelocity(),m.routine(),m.angle(),flags==null?m.flags():flags,m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),control==null?m.playerControl():control,m.previousPrimaryAngle(),m.previousSecondaryAngle()),s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());}
    }
}
