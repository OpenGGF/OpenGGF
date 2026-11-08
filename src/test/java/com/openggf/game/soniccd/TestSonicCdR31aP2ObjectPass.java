package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** Actual immutable original media/real cached pass/Frame/shared owners, CONSTRUCTED component
 * entry only. Native vectors and private original publication faults; not World/Ready admission. */
class TestSonicCdR31aP2ObjectPass {
    private static TestSonicCdR31aObjectPass.Harness h()throws Exception{return new TestSonicCdR31aObjectPass.Harness();}
    @Test void sourceBindsLiteralOriginalHullsOpcodesAndOddMappings()throws Exception{
        try(var h=h()){
            var source=CdR31aSolidsSource.fromAssets(h.assets);var a=source.data();assertTrue(source.owns(h.assets));
            assertEquals(0x20C7A2,a.metalMap());assertEquals(256,a.metalUp().length());assertEquals(258,a.drumMotion().length());
            assertEquals("2300fe000840fe00084000004600020008c0020008c000004600fe000840fe00",HexFormat.of().formatHex(a.metalUp().slice(0,32).copy()));
            var mapper=CdObjectMappings.fromR31aAssets(h.assets,h.objects);assertEquals(2,mapper.resolve(a.metalMap(),false,0).pieces().size());
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(a.metalMap(),false,1));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0xFF20C7A2,false,0));
        }
    }
    @Test void allSevenCachedBodiesHaveActualOrdinaryPassIngressAndNoSyntheticPrimary()throws Exception{
        for(int id:new int[]{0x20,0x21,0x22,0x23,0x26,0x27,0x28})try(var h=h()){
            h.dynamic(40,id,0,0);h.placement.pool().write(h.placement.pool().state(40).moveWord(X,0x100).moveWord(Y,0x100).moveWord(X_FRACTION,0xCAFE).moveWord(Y_FRACTION,0xBEEF));
            var actor=h.frame.captureActor();var shared=h.frame.captureShared();h.walk();assertEquals(2,h.placement.pool().state(40).routine(),"source Init reaches real body "+id);
            assertEquals(0xCAFE,h.placement.pool().state(40).word(X_FRACTION));assertEquals(0xBEEF,h.placement.pool().state(40).word(Y_FRACTION));
            assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());h.finish();
        }
    }
    @Test void reachedMetalInitPrefixIsPublishedBeforeOriginalTopPreparationFault()throws Exception{
        try(var h=h()){
            h.dynamic(49,0x23,0,3);var input=h.placement.pool().state(49).moveWord(X,1456).moveWord(Y,1296).moveWord(Y_FRACTION,0xBEEF);h.placement.pool().write(input);
            var error=new AssertionError("original P2 publication after native Init");h.afterAdopt=()->{throw error;};var actor=h.frame.captureActor();var shared=h.frame.captureShared();
            assertSame(error,assertThrows(AssertionError.class,h::walk));var expected=input.moveByte(SPRITE_FLAGS,4).moveWord(TILE,0x34E).moveByte(PRIORITY,3).moveLong(CdObjectState.LongField.MAPPINGS,0x20C7A2).moveByte(WIDTH,0x20).moveByte(Y_RADIUS,0x10).moveWord(SWING_CENTRE_Y,1456).moveWord(SWING_ANGLE,1296).moveByte(ROUTINE,2);
            assertArrayEquals(expected.copyBytes(),h.placement.pool().state(49).copyBytes());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);assertThrows(IllegalStateException.class,h::walk);
        }
    }
    @Test void sameOriginalTopReleasesRealReservedCounterpartAndPreservesItsOtherBytes()throws Exception{
        try(var h=h()){
            var s=h.frame.state();var m=s.motion();var env=h.frame.environment();var art=h.frame.artState();var clock=h.frame.vIntClock();
            var motion=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),0,0,0,0,m.angle(),8,m.spriteFlags(),m.xRadius(),m.yRadius(),0,0,m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
            var state=new CdPlayerBody.State(motion,s.animation(),new CdPlayerBody.Header(1,0,0,0,0),s.timers(),s.history(),s.rotationDistance(),new CdPlayerBody.ObjectLinks(0,1),s.rotationAngle());
            h.frame.close();h.frame=CdPlayerFrameRuntime.fromR31aEntry(h.assets,state,env,art,clock);h.pass=new SonicCdR31aObjectPass(h.assets,h.frame,h.placement,h.objects,h);
            h.edit(1,row->{row[0x22]=(byte)0xAD;row[0x1F]=(byte)0xD7;});var before=h.row(1).copy();h.dynamic(40,0x23,0,0);
            h.placement.pool().write(h.placement.pool().state(40).moveWord(X,96).moveWord(Y,1038).moveByte(SPRITE_FLAGS,0x84));h.service();h.walk();
            before[0x22]=(byte)0xA5;assertArrayEquals(before,h.row(1).copy());assertEquals(40,h.frame.state().objectLinks().standObject());assertEquals(8,h.frame.state().motion().flags()&8);h.finish();
        }
    }
    @Test void blockCreatesDirtySpringThenStopsAtTheRealUnqualifiedChildOwner()throws Exception{
        try(var h=h()){
            h.dynamic(40,0x21,0,2);h.placement.pool().write(h.placement.pool().state(41).moveWord(X_FRACTION,0xCAFE).moveByte(ROUTINE,0xD7));
            // Historical identifier retained: actual dirty routineD7/bit7-clear now follows
            // native DisplayOnly, without inventing Init, a mapping or a cleared free slot.
            h.walk();
            var child=h.placement.pool().state(41);assertEquals(0x0A,child.id());assertEquals(0xD7,child.routine());assertEquals(0xCAFE,child.word(X_FRACTION));assertEquals(0xDA00,child.word(SWING_MAX));assertEquals(2,h.placement.pool().state(40).routine());
        }
    }
    @Test void hiddenSpikeChildExecutesInPhysicalOrderAndRetainsParentRawLink()throws Exception{
        try(var h=h()){
            h.dynamic(40,0x22,0,1);h.placement.pool().write(h.placement.pool().state(40).moveWord(X,0x100).moveWord(Y,0x100));h.walk();
            var child=h.placement.pool().state(41);assertEquals(0x20,child.id());assertEquals(2,child.routine());assertEquals(0xDA00,child.word(SWING_MAX));assertEquals(0x120,child.centreX());assertEquals(h.placement.pool().state(40).centreY(),child.centreY());h.finish();
        }
    }
    @Test void rawReservedParentStopsAfterActualDrawClearWithoutInventingARead()throws Exception{
        try(var h=h()){
            h.dynamic(40,0x20,2,0);h.placement.pool().write(h.placement.pool().state(40).moveByte(SPRITE_FLAGS,0x84).moveWord(SWING_MAX,0xD000).moveLong(CdObjectState.LongField.MAPPINGS,0x20C176));
            var error=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(SonicCdR31aObjectPass.Boundary.P2_PARENT_OWNER,error.boundary());assertEquals(0xD000,error.value());assertEquals(4,h.placement.pool().state(40).spriteFlags());assertEquals(0x20,h.placement.pool().state(40).id());
        }
    }
    @Test void nativeBankClearPrecedesDeleteAndPreservesCollectedBits()throws Exception{
        try(var h=h()){
            h.dynamic(40,0x23,0,3);h.placement.pool().write(h.placement.pool().state(40).moveWord(X,0x800).moveByte(SAVED_FLAGS_ID,3));h.placement.bsetSavedFlag(3,1,CdObjectPlacementRuntime.SavedBit.LOADED);h.placement.bsetSavedFlag(3,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
            h.walk();assertArrayEquals(new byte[64],h.placement.pool().state(40).copyBytes());assertEquals(1,h.placement.savedFlag(3,1));h.finish();
        }
    }
    @Test void projectileInitStopsAtMissingOriginalAnimatorAfterOnlyItsOwnedPrefix()throws Exception{
        try(var h=h()){
            h.dynamic(40,0x27,0,2);h.placement.pool().write(h.placement.pool().state(40).moveByte(ANIMATION_TIME,0xD7).moveWord(TUNNEL_DOOR_TIMER,1));
            var error=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(SonicCdR31aObjectPass.Boundary.P2_ANIMATION_OWNER,error.boundary());var s=h.placement.pool().state(40);
            assertEquals(2,s.routine());assertEquals(0x100,s.word(X_VELOCITY));assertEquals(0xD7,s.unsignedByte(ANIMATION_TIME));assertEquals(0x98,s.unsignedByte(COLLISION_TYPE));assertTrue(h.cut.completedCpu().isEmpty());
        }
    }
    @Test void drumHasNoManufacturedAnimationArtClockAndRemainsComponentOnly()throws Exception{
        try(var h=h()){
            h.dynamic(40,0x28,2,0);h.placement.pool().write(h.placement.pool().state(40).moveByte(TUNNEL_DOOR_TIMER_HIGH,1));var before=h.placement.capture();
            var error=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(SonicCdR31aObjectPass.Boundary.P2_ART_CLOCK_OWNER,error.boundary());assertEquals(before.pool().slots(),h.placement.capture().pool().slots());
        }
    }
    @Test void flamePreparationUsesFullPairAndOnlyTheFourNativePlayerStores()throws Exception{
        try(var h=h()){
            var s=h.frame.state();var env=h.frame.environment();var ready=h.frame.prepareObjectCcFlameLaunch(s,env);var m=ready.finalState().motion();
            assertEquals(-0x700,m.yVelocity());assertEquals(2,m.flags());assertEquals(0x10,m.animation());assertEquals(s.motion().centreY16_16(),m.centreY16_16());assertEquals(s.objectLinks(),ready.finalState().objectLinks());assertEquals(env,ready.finalEnvironment());assertSame(s,h.frame.state());
            var equal=new CdPlayerBody.State(s.motion(),s.animation(),s.header(),s.timers(),s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());assertThrows(IllegalArgumentException.class,()->h.frame.prepareObjectCcFlameLaunch(equal,env));
        }
    }
    @Test void p2OriginalPrefixPublicationRejectsForeignThreadReentryAndExpiredReceipt()throws Exception{
        try(var h=h()){
            h.dynamic(40,0x23,0,3);h.beforeAdopt=()->{var a=h.lastAdoption;var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{a.applyNative();}catch(Throwable e){error.set(e);}},"foreign-P2");t.start();try{t.join(5000);}catch(InterruptedException e){throw new AssertionError(e);}assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,error.get());assertThrows(IllegalStateException.class,h::walk);};
            h.walk();assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);h.finish();
        }
    }
    private static TestSonicCdR31aObjectPass.Harness stoppedTop()throws Exception{
        var h=h();var s=h.frame.state();var m=s.motion();var env=h.frame.environment();var art=h.frame.artState();var clock=h.frame.vIntClock();
        var motion=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),0,0,0,0,m.angle(),8,m.spriteFlags(),m.xRadius(),m.yRadius(),0,0,m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var state=new CdPlayerBody.State(motion,s.animation(),new CdPlayerBody.Header(1,0,0,0,0),s.timers(),s.history(),s.rotationDistance(),new CdPlayerBody.ObjectLinks(0,2),s.rotationAngle());
        h.frame.close();h.frame=CdPlayerFrameRuntime.fromR31aEntry(h.assets,state,env,art,clock);h.pass=new SonicCdR31aObjectPass(h.assets,h.frame,h.placement,h.objects,h);
        h.dynamic(40,0x23,0,0);h.placement.pool().write(h.placement.pool().state(40).moveWord(X,96).moveWord(Y,1038).moveByte(SPRITE_FLAGS,0x84));h.service();return h;
    }
    /** Fault injection only inside the real post-kernel preparer, never an alternate native ingress. */
    private static boolean inStoppedPreparer(){return StackWalker.getInstance().walk(frames->frames.anyMatch(f->f.getClassName().equals(SonicCdR31aObjectPass.class.getName())&&f.getMethodName().equals("publishP2Contact")));}
    @Test void stoppedNativeCauseSurvivesTheFirstFreshCutPrebuildError()throws Exception{
        try(var h=stoppedTop()){
            var fault=new AssertionError("original external prefix fresh cut");h.onRead=()->{if(inStoppedPreparer())throw fault;};
            var original=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::walk);assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS,original.boundary());
            assertTrue(Arrays.stream(original.getSuppressed()).anyMatch(e->e==fault));assertEquals(0,h.placement.pool().state(40).flags()&8,"Unprepared native helper prefix was not adopted");assertEquals(1004,h.frame.state().motion().centreY16_16()>>>16);
        }
    }
    @Test void stoppedNativeCauseSurvivesCompleteFramePreparationInvalidation()throws Exception{
        try(var h=stoppedTop()){
            var fault=new IllegalStateException("original complete Frame cut retired");h.onRead=()->{if(inStoppedPreparer()){h.frame.close();throw fault;}};
            var original=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::walk);assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS,original.boundary());assertTrue(Arrays.stream(original.getSuppressed()).anyMatch(e->e==fault));assertTrue(h.frame.closed());assertEquals(1004,h.frame.state().motion().centreY16_16()>>>16);
        }
    }
    @Test void stoppedNativeCauseSurvivesPublicationErrorWithoutASecondPrefixAttempt()throws Exception{
        try(var h=stoppedTop()){
            var fault=new AssertionError("original stopped-prefix publication preparation");var attempts=new java.util.concurrent.atomic.AtomicInteger();h.beforeAdopt=()->{if(inStoppedPreparer()){attempts.incrementAndGet();throw fault;}};
            var original=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::walk);assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS,original.boundary());assertTrue(Arrays.stream(original.getSuppressed()).anyMatch(e->e==fault));assertEquals(1,attempts.get());assertEquals(0,h.placement.pool().state(40).flags()&8);assertEquals(1004,h.frame.state().motion().centreY16_16()>>>16);
        }
    }

}
