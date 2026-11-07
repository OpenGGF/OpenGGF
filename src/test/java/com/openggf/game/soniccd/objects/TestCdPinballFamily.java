package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed component ingress and fake completion sinks ONLY. No source/media/World qualification.
 * Native constants/graphs: N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff CC Objects pinball bodies.
 * Shared math/animation/AddPoints execute; fake attachment returns unchanged player/object deliberately.
 */
class TestCdPinballFamily {
    static class Fixture implements CdPinballContext.Ports {
        CdObjectPool pool=new CdObjectPool();byte[] saved=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];
        int x=100,y=100,xv=123,yv=0,flags=0xf5,ctrl=0x80,routine=2,anim=9,period=1,good,debug,act;
        int score=42,nextLife=5000;int failSound=-1;boolean omitStore,omitSound,despawn;int warpDirection;RuntimeException fault=new IllegalStateException("owned fault");
        CdPinballContext.Sound leaked;CdPinballContext.ObjectStore leakedStore;CdPinballContext c;
        final List<String> calls=new ArrayList<>();CdPinballContext.AttachmentKind attachment;
        CdPlayerBody.State actor(){return new CdPlayerBody.State(new CdPlayerMotionState((x<<16)|0x1234,(y<<16)|0xabcd,
            xv,yv,0x321,routine,0x27,flags,0xc4,9,19,anim,8,1,3,-7,5,ctrl,0x12,0x34),
            new CdPlayerBody.AnimationCursor(2,3,4),new CdPlayerBody.Header(1,0x212345,0xa780,2,24),
            new CdPlayerBody.Timers(11,12,13,14),new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),8),
            17,new CdPlayerBody.ObjectLinks(41,42),19);}
        CdPlayerBody.Environment environment(){return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(
            new CdPlayerMovement.Physics(0x600,12,128),new CdPlayerMovement.Bounds(0,0x2800,0x710,0),
            new CdPlayerMovement.Gates(0x100,period,0,0,0,0,warpDirection),new CdPlayerMovement.Globals(1,0x60,3,4,5,6,7,8,9,10)),
            new CdPlayerBody.Controls(8,16,8,16,0),new CdPlayerBody.Globals(17,23,0,debug,3,7,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);}
        CdPinballContext context(){var actor=actor();var env=environment();
            c=new CdPinballContext(new CdPinballContext.Input(pool.capture(),new CdPinballContext.Pair(actor,env,actor,env),
                new CdPlayerTouchResponse.WorldGlobals(17,3,0,score,nextLife,0),CdAssetBytes.copyOf(saved),period,good,debug,act),assets(),math(),this);return c;}
        CdPinballImage object(int id,int routine,int subtype){var i=new CdPinballImage(pool.state(32));i.b(0,id);i.b(0x24,routine);i.b(0x28,subtype);
            i.w(8,100);i.w(12,100);pool.write(i.state());return i;}
        void set(CdPinballImage i){pool.write(i.state());}
        void full(){for(int n=33;n<128;n++)pool.write(pool.state(n).moveByte(CdObjectState.ByteField.ID,1));}
        public void sound(CdPinballContext.Sound op){leaked=op;calls.add("sound:"+op.nativeId());if(op.nativeId()==failSound)throw fault;if(!omitSound)op.completedNative();}
        public void draw(CdPinballContext.Draw op){calls.add("draw");op.completedNative();}
        public void despawn(CdPinballContext.Despawn op){calls.add("despawn:"+op.xWord());op.completedNative(despawn);}
        public void attachment(CdPinballContext.Attachment op){attachment=op.kind();op.completedNative(op.object(),op.player(),List.of());}
        public void objectStore(CdPinballContext.ObjectStore op){leakedStore=op;if(!omitStore)op.applyNative();}
        public void playerStore(CdPinballContext.PlayerStore op){op.applyNative();}
        public void scoreStore(CdPinballContext.ScoreStore op){op.applyNative();}
        public void savedStore(CdPinballContext.SavedStore op){op.applyNative();}
    }
    static CdObjectState slot(CdPinballContext.Result r,int whole){return r.pool().slots().get(whole-32);}
    static CdPinballImage image(CdPinballContext.Result r,int whole){return new CdPinballImage(slot(r,whole));}
    static CdR31PinballAssets.Visual view(int frames,int scripts){
        byte[] mappings=new byte[frames*2+2];for(int n=0;n<frames;n++)word(mappings,2*n,frames*2);
        var visual=CdObjectVisualAssets.decodeStatic(mappings,frames,1);
        if(scripts==0)return new CdR31PinballAssets.Visual(visual,0x213400,Optional.empty());
        byte[] bytes=new byte[scripts*2+3];for(int n=0;n<scripts;n++)word(bytes,2*n,scripts*2);
        bytes[scripts*2]=3;bytes[scripts*2+1]=0;bytes[scripts*2+2]=(byte)255;
        return new CdR31PinballAssets.Visual(visual,0x212300,Optional.of(new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes),0,scripts)));
    }
    static CdR31PinballAssets assets(){
        byte[] slopes=new byte[129];slopes[64]=18;slopes[128]=3;
        return new CdR31PinballAssets(CdNativeFile.R31A,view(3,2),view(3,2),view(2,1),view(1,0),view(4,0),view(2,1),CdAssetBytes.copyOf(slopes));
    }
    static void word(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
    static CdPlayerMath math(){
        // Artificial decoder-complete control tables. Cardinal/diagonal cells are native literals;
        // unvisited cells are zero controls, never production fallback or native-media evidence.
        byte[] maps=new byte[398],dplcs=new byte[398],animations=new byte[578],sine=new byte[640],atan=new byte[257];
        for(int n=0;n<198;n++){word(maps,2*n,396);word(dplcs,2*n,396);}
        for(int n=0;n<60;n++)word(animations,2*n,120);animations[120]=3;animations[121]=0;animations[122]=(byte)255;
        word(sine,64*2,256);word(sine,128*2,0);word(sine,192*2,-256);word(sine,32*2,181);word(sine,96*2,181);
        System.arraycopy(sine,0,sine,512,128);atan[256]=32;
        return new CdPlayerMath(CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{1,2,3,4,5,(byte)255},
            new byte[4],sine,atan,CdSonicVisualAssets.decode(maps,dplcs,new byte[69472])));
    }
    @Test void bumperInitFallsThroughAndZeroRangeRetainsDirtyVelocity(){var f=new Fixture();var i=f.object(0x1d,0,0);i.l(0x2a,0x10000);f.set(i);
        var before=f.pool.capture();var r=CdBumper.run(f.context(),32);assertEquals(101,slot(r,32).centreY());assertEquals(65535,image(r,32).w(0x2e));
        assertEquals(before.slots(),f.pool.capture().slots());assertEquals(5,image(r,32).w(0x34));}
    @Test void bumperAllPlacedSubtypeMotionAndTurnaround(){for(int subtype:new int[]{0,3,8,0x43,0x83,0xc3}){
        var f=new Fixture();f.object(0x1d,0,subtype);var r=CdBumper.run(f.context(),32);int range=((subtype&2)!=0?64:0)+((subtype&1)!=0?32:0);
        assertEquals(range,image(r,32).w(0x30));if(range!=0){int delta=(subtype&64)!=0?1:-1;
            assertEquals(100+((subtype&128)!=0?delta:0),slot(r,32).centreX());assertEquals(100+((subtype&128)==0?delta:0),slot(r,32).centreY());}}}
    @Test void bumperContactUsesArithmeticShiftAndRetainsCompletePairNonwrites(){var f=new Fixture();var i=f.object(0x1d,2,0);i.b(0x21,1);i.w(0x34,0);f.set(i);
        f.x=99;f.y=99;var c=f.context();var before=c.result();var r=CdBumper.run(c,32);var p=r.players().primary();
        assertEquals(-1267,p.motion().xVelocity());assertEquals(-1267,p.motion().yVelocity());assertEquals(0,p.motion().playerJump());
        assertEquals((f.flags|2)&~0x30,p.motion().flags());assertSame(before.players().primaryEnvironment(),r.players().primaryEnvironment());
        assertSame(before.players().secondary(),r.players().secondary());assertSame(before.players().secondaryEnvironment(),r.players().secondaryEnvironment());
        assertSame(before.players().primary().history(),p.history());assertSame(before.players().primary().header(),p.header());
        assertEquals(before.players().primary().timers(),p.timers());assertEquals(before.players().primary().animation(),p.animation());
        assertEquals(0x1234,p.motion().centreX16_16()&65535);assertEquals(0xabcd,p.motion().centreY16_16()&65535);
        assertEquals(before.players().primary().objectLinks(),p.objectLinks());assertEquals(before.players().primary().rotationAngle(),p.rotationAngle());}
    @Test void bumperLinearSaveIsCappedAndNotPeriodTriple(){for(int count:new int[]{0x89,0x8a,0xff}){var f=new Fixture();var i=f.object(0x1d,2,0);
        i.b(0x21,1);i.b(0x23,5);f.set(i);f.saved[7]=(byte)count;f.saved[18]=0x55;
        var r=CdBumper.run(f.context(),32);assertEquals(count<0x8a?count+1:count,r.savedFlags().unsignedByte(7));assertEquals(0x55,r.savedFlags().unsignedByte(18));}}
    @Test void bumperCappedScoreStillSpawnsDirtyPointsAndFull96StillAwards(){for(boolean full:new boolean[]{false,true}){
        var f=new Fixture();var i=f.object(0x1d,2,0);i.b(0x21,1);i.w(0x34,1);f.set(i);f.score=999995;f.nextLife=1000000;
        var dirty=new CdPinballImage(f.pool.state(33));dirty.w(10,0xabcd);dirty.b(0x24,0x56);f.set(dirty);if(full)f.full();
        var r=CdBumper.run(f.context(),32);assertEquals(999999,r.score().score());assertEquals(0,image(r,32).w(0x34));
        if(!full){assertEquals(0x1c,slot(r,33).id());assertEquals(0x80,slot(r,33).subtype());assertEquals(0xabcd,image(r,33).w(10));assertEquals(0x56,slot(r,33).routine());}}}
    @Test void bumperSoundFailureStopsBeforeContactClearAndLaunch(){var f=new Fixture();var i=f.object(0x1d,2,0);i.b(1,0x84);i.b(0x21,1);i.w(0x34,1);f.set(i);f.failSound=0x7f;
        var c=f.context();assertSame(f.fault,assertThrows(IllegalStateException.class,()->CdBumper.run(c,32)));
        var r=c.result();assertEquals(52,r.score().score());assertEquals(0x1c,slot(r,33).id());assertEquals(1,image(r,32).b(0x21));assertEquals(123,r.players().primary().motion().xVelocity());
        assertThrows(IllegalStateException.class,()->f.leaked.completedNative());}
    @Test void extraLifeSoundFailurePrecedesPointsAllocation(){var f=new Fixture();var i=f.object(0x1d,2,0);i.b(0x21,1);i.w(0x34,1);f.set(i);f.score=4995;f.failSound=0x7a;
        var c=f.context();assertThrows(IllegalStateException.class,()->CdBumper.run(c,32));assertEquals(5005,c.result().score().score());assertEquals(4,c.result().score().lives());assertEquals(0,slot(c.result(),33).id());}
    @Test void flipperMirroredLeftEndpointReadsBottomZero(){var f=new Fixture();var i=f.object(0x1e,2,0);i.b(0x19,32);i.b(0x22,1);f.set(i);f.x=68;f.y=99;
        var r=CdFlipper.run(f.context(),32);assertEquals(99,r.players().primary().motion().centreY16_16()>>>16);assertEquals(CdPinballContext.AttachmentKind.STAND_ON_OBJECT,f.attachment);}
    @Test void flipperMirroredBottomEndpointReadsActualFollowingByte(){var f=new Fixture();var i=f.object(0x1e,2,0);i.b(0x19,32);i.b(0x22,1);f.set(i);f.x=68;f.y=122;f.yv=-1;
        var r=CdFlipper.run(f.context(),32);assertEquals(122,r.players().primary().motion().centreY16_16()>>>16);assertEquals(0,r.players().primary().motion().yVelocity());
        assertEquals(123,r.players().primary().motion().groundVelocity());assertNull(f.attachment);}
    @Test void flipperRightEndpointExcludedAndAnimationSkipsAttachment(){for(int anim:new int[]{0,1}){var f=new Fixture();var i=f.object(0x1e,2,0);i.b(0x19,32);i.b(0x1c,anim);f.set(i);f.x=132;
        CdFlipper.run(f.context(),32);assertEquals(anim==0?CdPinballContext.AttachmentKind.GET_OFF_OBJECT:null,f.attachment);}}
    @Test void flipperNoJumpKernelOrJumpSoundIsRunByBody(){var f=new Fixture();f.object(0x1e,0,0);CdFlipper.run(f.context(),32);assertTrue(f.calls.stream().noneMatch(s->s.startsWith("sound:")));}
    @Test void platformSecondarySubtypeDoesNotSelectCounterpartOrMotionAxis(){for(int second:new int[]{0,1,0x80,0xff}){
        var f=new Fixture();var i=f.object(0x2a,0,0);i.b(0x29,second);f.set(i);var c=f.context();var before=c.result();var r=CdBumperPlatform.run(c,32);
        assertEquals(100,slot(r,32).centreX());assertEquals(99,slot(r,32).centreY());assertSame(before.players().secondary(),r.players().secondary());}}
    @Test void platformSideBandClearsOnlyXSpeed(){var f=new Fixture();var i=f.object(0x2a,2,0x80);i.b(0x21,1);i.l(0x2a,0x10000);i.w(0x2e,1);i.w(0x30,192);f.set(i);f.y=100;
        var c=f.context();var before=c.result();var r=CdBumperPlatform.run(c,32);assertEquals(0,r.players().primary().motion().xVelocity());assertEquals(f.flags,r.players().primary().motion().flags());
        assertEquals(101,slot(r,32).centreX());assertEquals(-0x10000,image(r,32).l(0x2a));assertEquals(192,image(r,32).w(0x2e));assertEquals(before.score(),r.score());}
    @Test void platformVerticalPreservesXAndDiagonalUsesBothSigns(){for(int dx:new int[]{0,-24,24}){var f=new Fixture();var i=f.object(0x2a,2,0);i.b(0x21,1);f.set(i);f.x=100+dx;f.y=80;
        var r=CdBumperPlatform.run(f.context(),32);assertEquals(dx==24?0x4f3:123,r.players().primary().motion().xVelocity());assertEquals(dx==24?-0x4f3:-0x700,r.players().primary().motion().yVelocity());}}
    @Test void glassInitAwardsBeforeAnimatorAndKeepsDirtyFractions(){var f=new Fixture();var i=f.object(0x29,0,0);i.b(1,0x81);i.w(10,0x1234);i.w(14,0xabcd);f.set(i);f.act=2;
        var r=CdGlassShatter.run(f.context(),32);assertEquals(0x642a,image(r,32).w(2));assertEquals(0x1234,image(r,32).w(10));assertEquals(52,r.score().score());
        assertTrue(r.completed().indexOf("ADD_POINTS:10")<r.completed().indexOf("ANIMATE"));assertEquals(0x1c,slot(r,33).id());assertEquals(4,image(r,32).b(1));}
    @Test void glassDeleteStillDrawsZeroedSlot(){var f=new Fixture();f.object(0x29,4,0);var r=CdGlassShatter.run(f.context(),32);assertArrayEquals(new byte[64],slot(r,32).copyBytes());assertEquals(List.of("draw"),f.calls);}
    @Test void pocketCapturePreservesFractionsAndHasNoInventedReleaseImpulse(){var f=new Fixture();f.object(0x2f,0,255);f.y=84;
        var c=f.context();var before=c.result();var r=CdPocket.run(c,32);var p=r.players().primary();assertEquals(4,slot(r,32).routine());assertEquals(255,image(r,32).b(0x3e));
        assertEquals(-0x800,p.motion().yVelocity());assertEquals(0,p.motion().xVelocity());assertEquals(0x81,p.motion().playerControl());assertEquals(7,p.motion().xRadius());
        assertEquals(14,p.motion().yRadius());assertEquals(24,p.header().width());assertEquals(0xabcd,p.motion().centreY16_16()&65535);assertEquals(List.of("sound:215","draw","despawn:100"),f.calls);
        assertSame(before.players().secondary(),r.players().secondary());}
    @Test void pocketCaptureRejectsAscendingHurtDeadAndFullWordDebug(){for(int which=0;which<4;which++){var f=new Fixture();f.object(0x2f,2,255);f.y=84;
        if(which==0)f.yv=-1;if(which==1)f.routine=4;if(which==2)f.routine=6;if(which==3)f.debug=0x100;
        assertEquals(2,slot(CdPocket.run(f.context(),32),32).routine());}}
    @Test void pocketFutureGoodDeletesParentButWrapperStillDraws(){var f=new Fixture();f.period=2;f.good=1;f.object(0x2f,0,255);
        var r=CdPocket.run(f.context(),32);assertEquals(0,slot(r,32).id());assertEquals(List.of("draw","despawn:0"),f.calls);}
    @Test void pocketFull96DeletesOnlyParentAndLeavesCapturedControl(){var f=new Fixture();var i=f.object(0x2f,4,255);i.w(0x2a,0);i.b(0x3e,255);f.set(i);f.full();f.ctrl=0x81;
        var r=CdPocket.run(f.context(),32);assertEquals(0,slot(r,32).id());assertEquals(0x81,r.players().primary().motion().playerControl());for(int n=33;n<128;n++)assertEquals(1,slot(r,n).id());}
    @Test void pocketPartialAllocationPreservesDirtyChildRoutineSubtypeAndFractions(){var f=new Fixture();var i=f.object(0x2f,4,255);i.w(0x2a,0);f.set(i);
        var dirty=new CdPinballImage(f.pool.state(33));dirty.b(0x24,4);dirty.b(0x28,0x81);dirty.w(10,0x5678);f.set(dirty);
        var r=CdPocket.run(f.context(),32);assertEquals(4,slot(r,33).routine());assertEquals(0x81,slot(r,33).subtype());assertEquals(0x5678,image(r,33).w(10));
        assertEquals(0xd840,image(r,32).w(0x2c));assertEquals(0xd800,image(r,33).w(0x2e));assertEquals(0xc0,r.players().primary().motion().playerControl());}
    @Test void pocketAwardsBothFiniteCountersWithoutReset(){for(int routine:new int[]{10,12}){var f=new Fixture();var i=f.object(0x2f,routine,255);i.w(0x2a,0);i.w(0x30,1);f.set(i);
        var r=CdPocket.run(f.context(),32);assertEquals(routine+2,slot(r,32).routine());assertEquals(0,image(r,32).w(0x30));assertEquals(52,r.score().score());assertEquals(0x1c,slot(r,33).id());}}
    @Test void pocketZeroAwardsStillAdvanceTimerWithoutPoints(){var f=new Fixture();var i=f.object(0x2f,12,255);i.w(0x2a,0);i.w(0x30,0);f.set(i);
        var r=CdPocket.run(f.context(),32);assertEquals(14,slot(r,32).routine());assertEquals(30,image(r,32).w(0x2a));assertEquals(42,r.score().score());}
    @Test void pocketReleaseWritesDeletedOrReusedChildAndClearsOnlyControlBits(){for(int id:new int[]{0,0x1d,0x2f}){var f=new Fixture();var i=f.object(0x2f,18,255);
        i.w(0x2a,0);i.w(0x2c,0xd840);i.b(0x3e,255);f.set(i);f.pool.write(f.pool.state(33).moveByte(CdObjectState.ByteField.ID,id));f.ctrl=0xff;
        var r=CdPocket.run(f.context(),32);assertEquals(4,slot(r,33).routine());assertEquals(id,slot(r,33).id());assertEquals(0xbe,r.players().primary().motion().playerControl());
        assertEquals(f.yv,r.players().primary().motion().yVelocity());assertEquals(120,image(r,32).w(0x2a));}}
    @Test void pocketSelfAliasedChildReleaseRetainsRawRoutineAndCompleteNonwrites()throws Exception {
        for(int caught:new int[]{0,255}){
            var f=new Fixture();var i=f.object(0x2f,18,0x80);i.w(0x2a,0);i.w(0x2c,0xd800);i.b(0x3e,caught);
            i.b(1,0xe5);i.b(0x1a,0xf3);i.b(0x25,0x67);i.b(0x3d,0xd1);i.b(0x3f,0xa7);f.set(i);f.ctrl=0xff;
            var c=f.context();var before=c.result();var expected=new CdPinballImage(slot(before,32));
            expected.b(0x24,4);expected.w(0x2a,120);expected.b(0x1a,0);expected.b(0x3e,0);expected.b(1,expected.b(1)&127);
            var after=CdPocket.run(c,32);assertEquals(expected.state(),slot(after,32));
            for(int whole=33;whole<128;whole++)assertEquals(slot(before,whole),slot(after,whole),"Nonwritten physical neighbour "+whole);
            var oldPlayer=before.players().primary();var newPlayer=after.players().primary();
            for(var component:CdPlayerMotionState.class.getRecordComponents()){
                var actual=component.getAccessor().invoke(newPlayer.motion());
                var wanted=component.getName().equals("playerControl")?(caught==0?0xff:0xbe):component.getAccessor().invoke(oldPlayer.motion());
                assertEquals(wanted,actual,component.getName());
            }
            for(var component:CdPlayerBody.State.class.getRecordComponents())if(!component.getName().equals("motion"))
                assertEquals(component.getAccessor().invoke(oldPlayer),component.getAccessor().invoke(newPlayer),component.getName());
            assertSame(before.players().primaryEnvironment(),after.players().primaryEnvironment());
            assertSame(before.players().secondary(),after.players().secondary());assertSame(before.players().secondaryEnvironment(),after.players().secondaryEnvironment());
            assertEquals(before.score(),after.score());assertArrayEquals(before.savedFlags().copy(),after.savedFlags().copy());
            assertEquals(List.of("sound:159","draw","despawn:100"),f.calls);
        }
    }
    @Test void pocketReleaseSoundFaultPrecedesAllReleaseWrites(){var f=new Fixture();var i=f.object(0x2f,18,255);i.w(0x2a,0);i.w(0x2c,0xd840);i.b(0x3e,255);f.set(i);f.failSound=0x9f;
        var c=f.context();assertThrows(IllegalStateException.class,()->CdPocket.run(c,32));var r=c.result();assertEquals(18,slot(r,32).routine());assertEquals(65535,image(r,32).w(0x2a));
        assertEquals(255,image(r,32).b(0x3e));assertEquals(0,slot(r,33).routine());}
    @Test void pocketUnownedLinkFailsAfterSoundRoutineTimerPrefix(){var f=new Fixture();var i=f.object(0x2f,18,255);i.w(0x2c,0xd000);f.set(i);
        var c=f.context();assertThrows(UnsupportedOperationException.class,()->CdPocket.run(c,32));assertEquals(20,slot(c.result(),32).routine());assertEquals(120,image(c.result(),32).w(0x2a));}
    @Test void pocketChildOnlyChecksParentIdNotGenerationOrRole(){for(int id:new int[]{0,0x1d,0x2f}){var f=new Fixture();var i=f.object(0x2f,0,0);i.w(0x2e,0xd840);f.set(i);
        f.pool.write(f.pool.state(33).moveByte(CdObjectState.ByteField.ID,id));var r=CdPocket.run(f.context(),32);
        assertEquals(id==0x2f?0x2f:0,slot(r,32).id());assertEquals(id==0x2f?List.of("draw"):List.of(),f.calls);}}
    @Test void pocketCooldownSignedBoundaryAndWordWrap(){for(int timer:new int[]{0,1,0x8000}){var f=new Fixture();var i=f.object(0x2f,20,255);i.w(0x2a,timer);i.b(0x3e,255);f.set(i);
        var r=CdPocket.run(f.context(),32);assertEquals(timer==0?2:20,slot(r,32).routine());assertEquals((timer-1)&65535,image(r,32).w(0x2a));}}
    @Test void omittedStoreStopsBeforeAnyStateAndReceiptExpires(){var f=new Fixture();f.object(0x29,0,0);f.omitStore=true;var c=f.context();var before=c.result();
        assertThrows(IllegalStateException.class,()->CdGlassShatter.run(c,32));assertEquals(before.pool().slots(),c.result().pool().slots());assertEquals(before.score(),c.result().score());
        assertThrows(IllegalStateException.class,()->f.leakedStore.applyNative());assertThrows(IllegalStateException.class,()->CdGlassShatter.run(c,32));}
    @Test void omittedSoundCannotFabricateCompletionAndAllValuesAreInert(){var f=new Fixture();f.object(0x2f,2,255);f.y=84;f.omitSound=true;var c=f.context();
        assertThrows(IllegalStateException.class,()->CdPocket.run(c,32));assertEquals(4,slot(c.result(),32).routine());assertEquals(0x81,c.result().players().primary().motion().playerControl());
        assertThrows(IllegalStateException.class,()->f.leaked.completedNative());assertEquals(2,f.pool.state(32).routine());}
    @Test void despawnUsesWarpSelectedTripleBankWhileBumperCounterRemainsLinear(){
        for(int[] input:new int[][]{{1,0,1},{0x81,1,0},{0x81,255,2},{0x80,1,0},{0x82,255,2}}){
            var f=new Fixture();var i=f.object(0x1d,2,0);i.b(0x23,5);f.set(i);f.period=input[0];f.warpDirection=input[1];f.despawn=true;
            Arrays.fill(f.saved,(byte)255);var r=CdBumper.run(f.context(),32);
            assertEquals(127,r.savedFlags().unsignedByte(2+15+input[2]));assertEquals(255,r.savedFlags().unsignedByte(7));
            assertArrayEquals(new byte[64],slot(r,32).copyBytes());
        }
    }
    @Test void objectStoreCompletionCannotBeReusedOrClaimedByAnotherProducer(){
        var f=new Fixture();f.object(0x29,4,0);CdGlassShatter.run(f.context(),32);
        var other=new Fixture();other.object(0x29,4,0);var foreign=other.context();
        assertThrows(IllegalStateException.class,()->f.leakedStore.requireOwner(foreign));
        assertThrows(IllegalStateException.class,()->f.leakedStore.applyNative());
    }
    static CdPinballContext continueFrom(Fixture f,CdPinballContext.Result state){return new CdPinballContext(
        new CdPinballContext.Input(state.pool(),state.players(),state.score(),state.savedFlags(),f.period,f.good,f.debug,f.act),assets(),math(),f);}
    static CdPinballContext.Result parentCalls(Fixture f,CdPinballContext.Result state,int count){
        for(int n=0;n<count;n++)state=CdPocket.run(continueFrom(f,state),32);return state;
    }
    @Test void completePocketCycleAndTrappedSnapshotResumeProduceSameImages(){
        var f=new Fixture();f.object(0x2f,0,255);f.y=84;
        var captured=CdPocket.run(f.context(),32);var trapped=parentCalls(f,captured,12);
        assertEquals(0xc1,trapped.players().primary().motion().playerControl());
        var end=parentCalls(f,trapped,240);var replay=parentCalls(f,trapped,240);
        assertEquals(62,end.score().score());assertEquals(5,image(end,32).w(0x30));assertEquals(2,slot(end,32).routine());
        assertEquals(0,end.players().primary().motion().playerControl()&65);assertEquals(-0x800,end.players().primary().motion().yVelocity());
        assertEquals(end.pool().slots(),replay.pool().slots());assertEquals(end.players(),replay.players());assertEquals(end.score(),replay.score());
        assertArrayEquals(end.savedFlags().copy(),replay.savedFlags().copy());
        var childIndex=(image(end,32).w(0x2c)-0xd000)/64;
        assertEquals(4,slot(end,childIndex).routine());
        var retired=CdPocket.run(continueFrom(f,end),childIndex);assertEquals(0,slot(retired,childIndex).id());
    }
    @Test void allPocketTimedStatesUseSignedPostdecrementWithoutExtraAdvance(){
        int[][] transitions={{4,6,6,2},{6,8,2,3},{8,10,30,0},{10,12,30,-1},{12,14,30,-1},{14,16,4,3},{16,18,6,2}};
        for(int[] row:transitions){var f=new Fixture();var i=f.object(0x2f,row[0],255);i.w(0x2a,1);f.set(i);
            var held=CdPocket.run(f.context(),32);assertEquals(row[0],slot(held,32).routine());assertEquals(0,image(held,32).w(0x2a));
            var advanced=CdPocket.run(continueFrom(f,held),32);assertEquals(row[1],slot(advanced,32).routine());assertEquals(row[2],image(advanced,32).w(0x2a));
            if(row[3]>=0)assertEquals(row[3],image(advanced,32).b(0x1a));
        }
    }
    @Test void full64ObjectImageNonwritesAndAll96PoolNeighboursSurviveIdleBumper(){
        var f=new Fixture();var i=f.object(0x1d,2,0);i.b(0x1e,2);i.w(0x32,0x3456);f.set(i);
        for(int n=33;n<128;n++){var d=new CdPinballImage(f.pool.state(n));d.b(0,n);d.l(8,0x12345678+n);f.set(d);}
        var before=f.pool.capture();var c=f.context();var actor=c.result().players();var r=CdBumper.run(c,32);
        byte[] expected=before.slots().getFirst().copyBytes();expected[0x1e]=1;
        assertArrayEquals(expected,slot(r,32).copyBytes());assertEquals(before.slots().subList(1,96),r.pool().slots().subList(1,96));assertEquals(actor,r.players());
    }
    @Test void assetsKeepIndependentProgrammeAndExactSlopeHull(){var a=assets();assertEquals(CdNativeFile.R31A,a.programme());assertEquals(18,a.flipperSlope(false,64));assertEquals(3,a.flipperSlope(true,64));
        assertThrows(IllegalArgumentException.class,()->a.flipperSlope(false,65));assertThrows(IllegalArgumentException.class,()->new CdR31PinballAssets(CdNativeFile.R31B,a.bumper(),a.flipper(),a.glass(),a.platform(),a.pocketParent(),a.pocketChild(),CdAssetBytes.copyOf(new byte[128])));}
}
