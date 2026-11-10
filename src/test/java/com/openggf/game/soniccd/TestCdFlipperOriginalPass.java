package com.openggf.game.soniccd;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** UNRUN original media/pass/Frame, CONSTRUCTED Core/Host/slot ingress. No public route proof. */
class TestCdFlipperOriginalPass {
    private static void flipper(TestSonicCdR31aObjectPass.Harness h,int routine){
        h.dynamic(40,0x1e,routine,0);h.placement.pool().write(h.placement.pool().state(40).moveWord(X,100).moveWord(Y,100)
            .moveByte(ANIMATION,1).moveByte(SPRITE_FLAGS,0x84).moveLong(MAPPINGS,0x20b0ea).moveWord(X_FRACTION,0xcafe));
    }
    private static void primary(TestSonicCdR31aObjectPass.Harness h,int flags,int stand,int charge,int control,int routine,int animation,int yVelocity) throws Exception {
        var old=h.frame.state();var m=old.motion();var env=h.frame.environment();var art=h.frame.artState();var clock=h.frame.vIntClock();
        var motion=new com.openggf.game.soniccd.player.CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),123,yVelocity,0x321,routine,0x27,flags,m.spriteFlags(),9,19,animation,m.previousAnimation(),1,charge,-7,5,control,0x12,0x34);
        var value=new com.openggf.game.soniccd.player.CdPlayerBody.State(motion,old.animation(),old.header(),old.timers(),old.history(),old.rotationDistance(),new com.openggf.game.soniccd.player.CdPlayerBody.ObjectLinks(41,stand),old.rotationAngle());
        h.frame.close();h.frame=com.openggf.game.soniccd.player.CdPlayerFrameRuntime.fromR31aEntry(h.assets,value,env,art,clock);
        h.pass=new SonicCdR31aObjectPass(h.assets,h.frame,h.placement,h.objects,h);
    }
    private static void contact(TestSonicCdR31aObjectPass.Harness h){
        flipper(h,2);h.placement.pool().write(h.placement.pool().state(40).moveByte(ANIMATION,0).moveByte(WIDTH,32).moveWord(Y,1027));
    }
    @Test void actualStandReleasesDirtyOldParentAndKeepsRawPrimaryFields() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            primary(h,8,42,0,0x81,4,9,0);contact(h);h.placement.pool().write(h.placement.pool().state(42).moveByte(FLAGS,0xa9));
            h.walk();assertEquals(0xa1,h.placement.pool().state(42).flags());assertEquals(40,h.frame.state().objectLinks().standObject());
            assertEquals(0,h.frame.state().motion().angle());assertEquals(0,h.frame.state().motion().yVelocity());assertEquals(123,h.frame.state().motion().groundVelocity());
            assertEquals(2,h.frame.state().motion().routine());assertEquals(120,h.frame.state().timers().hurt());assertEquals(0x81,h.frame.state().motion().playerControl());h.finish();
        }
    }
    @Test void unownedStandAddressPublishesPreciseSharedPrefixAfterYStoreThenRethrowsOriginal() throws Exception {
        for(int old:new int[]{1,31,128,255})try(var h=new TestSonicCdR31aObjectPass.Harness()){
            primary(h,0x1c,old,0,0x81,4,9,0);contact(h);var original=new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
            h.beforeAdopt=()->{var p=h.lastPinball;if(p.operation() instanceof CdPinballContext.Attachment){var call=CdR31PinballCall.fromOriginal(p);original.set(((CdSolidObject.NativeStopped)call.attachmentOutcome()).stop().originalException());}};
            var fault=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::walk);assertSame(original.get(),fault);assertEquals(old,fault.slotByte());
            // Y1007 is the Flipper store; rolled Stand then subtracts5 before the missing old-parent read.
            assertEquals(1002,h.frame.state().motion().centreY16_16()>>>16);assertEquals(2,h.frame.state().motion().routine());assertEquals(120,h.frame.state().timers().hurt());
            assertEquals(8,h.placement.pool().state(40).flags()&8);assertEquals(old,h.frame.state().objectLinks().standObject());assertEquals(0x27,h.frame.state().motion().angle());
        }
    }
    @Test void chargeStopHostFaultPrecedesEveryGetOffStoreAndRetainsOriginalThrowable() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            primary(h,8,40,3,0x81,2,9,0);contact(h);h.placement.pool().write(h.placement.pool().state(40).moveByte(FLAGS,8).moveWord(X,300));
            var before=h.frame.state();var object=h.placement.pool().state(40);var fault=new AssertionError("actual charge-stop Host");
            h.beforeAdopt=()->{if(h.lastPinball.changesHost())throw fault;};assertSame(fault,assertThrows(AssertionError.class,h::walk));
            assertSame(before,h.frame.state());assertEquals(object,h.placement.pool().state(40));assertTrue(h.cut.sounds().isEmpty());
        }
    }
    @Test void getOffRetainsChargeInvisibleControlAndActualSoundOrder() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            primary(h,8,40,3,0xc1,2,9,0);contact(h);h.placement.pool().write(h.placement.pool().state(40).moveByte(FLAGS,8).moveWord(X,300));
            h.walk();var m=h.frame.state().motion();assertEquals(3,m.charge());assertEquals(0xc1,m.playerControl());assertEquals(0,m.stick());assertEquals(0,m.flags()&8);assertEquals(2,m.flags()&2);assertEquals(0,h.frame.state().objectLinks().standObject());assertEquals(1,h.cut.sounds().size());h.finish();
        }
    }
    @Test void realPreparedPlayerAndAttachmentValuesCannotApplyEarlyOrThroughForeignFrame() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness();var other=new TestSonicCdR31aObjectPass.Harness()){
            primary(h,0,0,0,0x81,2,9,0);contact(h);var seen=new java.util.concurrent.atomic.AtomicInteger();
            h.beforeAdopt=()->{var p=h.lastPinball;var call=CdR31PinballCall.fromOriginal(p);
                if(p.operation() instanceof CdPinballContext.PlayerStore){var ready=h.frame.preparePinballPlayerStore(call);assertThrows(IllegalStateException.class,()->h.frame.applyBumperPlayerStore(ready));assertThrows(IllegalArgumentException.class,()->other.frame.preparePinballPlayerStore(call));seen.incrementAndGet();}
                if(p.operation() instanceof CdPinballContext.Attachment){var ready=h.frame.preparePinballAttachment(call);assertThrows(IllegalStateException.class,()->h.frame.applyPinballAttachment(ready));assertThrows(IllegalArgumentException.class,()->other.frame.preparePinballAttachment(call));seen.incrementAndGet();}
            };h.walk();assertEquals(2,seen.get());h.beforeAdopt=()->{};h.finish();
        }
    }
    private static com.openggf.game.soniccd.level.CdR31aAssets freshOriginalAssets()throws Exception{
        var root=java.nio.file.Path.of(System.getProperty("soniccd.media.root")).resolve("USA");
        java.util.List<java.nio.file.Path> cues;try(var paths=java.nio.file.Files.list(root)){
            cues=paths.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
        }
        assertEquals(1,cues.size());
        try(var media=com.openggf.game.soniccd.media.CdProgramAssets.open(cues.getFirst(),java.util.Optional.of(CdReleaseVariant.USA),java.util.Optional.empty())){
            return com.openggf.game.soniccd.level.CdR31aAssets.fromPrograms(media,CdStagePayload.R31A.stage());
        }
    }
    @Test void foreignProgrammeOwnerFramePlacementAndMetadataRejectBeforeInvocation() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness();var other=new TestSonicCdR31aObjectPass.Harness()){
            var foreign=freshOriginalAssets();assertNotSame(h.assets,foreign);assertEquals(h.assets.identity(),foreign.identity());
            assertFalse(com.openggf.game.soniccd.level.CdR31aBumperSource.fromAssets(h.assets).owns(foreign));
            var foreignFrame=com.openggf.game.soniccd.player.CdPlayerFrameRuntime.fromR31aEntry(foreign,h.frame.state(),h.frame.environment(),h.frame.artState(),h.frame.vIntClock());
            try{
                var placed=CdObjectPlacementRuntime.fromR31aAssets(foreign,h.placement.capture().savedFlags());
                assertThrows(IllegalArgumentException.class,()->new SonicCdR31aObjectPass(h.assets,foreignFrame,h.placement,h.objects,h));
                assertThrows(IllegalArgumentException.class,()->new SonicCdR31aObjectPass(h.assets,h.frame,placed,h.objects,h));
                var metadata=com.openggf.game.soniccd.level.CdR31aObjectAssets.fromAssets(foreign);
                assertThrows(IllegalArgumentException.class,()->CdObjectMappings.fromR31aPinballAssets(h.assets,metadata));
            }finally{foreignFrame.close();}
        }
    }
    @Test void actualUndersideStoresYThenVelocityWithoutCallingAttachment() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            primary(h,0xd5,42,3,0x81,2,9,-1);contact(h);h.placement.pool().write(h.placement.pool().state(40).moveWord(Y,972));
            var sequence=new java.util.ArrayList<com.openggf.game.soniccd.player.CdPlayerBody.State>();
            h.beforeAdopt=()->{if(h.lastPinball.operation() instanceof CdPinballContext.PlayerStore o)sequence.add(o.output());else assertFalse(h.lastPinball.operation() instanceof CdPinballContext.Attachment);};
            h.walk();assertEquals(2,sequence.size());assertEquals(-1,sequence.getFirst().motion().yVelocity());assertEquals(1007,sequence.getFirst().motion().centreY16_16()>>>16);
            assertEquals(0,h.frame.state().motion().yVelocity());assertEquals(123,h.frame.state().motion().groundVelocity());assertEquals(0xd5,h.frame.state().motion().flags());assertEquals(0x27,h.frame.state().motion().angle());assertEquals(0x81,h.frame.state().motion().playerControl());h.finish();
        }
    }
    @Test void animationFailureKeepsCompletedInitWithoutDrawOrDespawn() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flipper(h,0);h.placement.pool().write(h.placement.pool().state(40).moveByte(ANIMATION,0x7f));
            assertThrows(RuntimeException.class,h::walk);var s=h.placement.pool().state(40);assertEquals(2,s.routine());assertEquals(0x20b0ea,s.longBits(MAPPINGS));assertEquals(32,s.unsignedByte(WIDTH));assertEquals(4,s.spriteFlags());
        }
    }
    @Test void actualInitUsesFlipperAndRetainsCompleteOtherOwners() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flipper(h,0);h.edit(1,b->{b[0x1f]=(byte)0xd7;b[0x22]=(byte)0xa5;});
            var p=h.placement.capture();var actor=h.frame.state();var env=h.frame.environment();var core=h.cut.core();h.walk();
            var s=h.placement.pool().state(40);assertEquals(2,s.routine());assertEquals(0x20b0ea,s.longBits(MAPPINGS));assertEquals(0x49d,s.word(TILE));assertEquals(0xcafe,s.word(X_FRACTION));
            assertSame(actor,h.frame.state());assertSame(env,h.frame.environment());assertEquals(core,h.cut.core());assertEquals(p.status(),h.placement.status());assertArrayEquals(p.savedFlags().copy(),h.placement.capture().savedFlags().copy());
            for(int w=32;w<128;w++)if(w!=40)assertEquals(p.pool().slots().get(w-32),h.placement.pool().state(w));h.finish();
        }
    }
    @Test void getOffRunsAtActualPrimaryOnlySiteWithoutSecondaryProjection() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flipper(h,2);h.placement.pool().write(h.placement.pool().state(40).moveByte(ANIMATION,0).moveByte(WIDTH,32));
            // Actual primary Y1004 is outside the Flipper Y100 hull; GetOff is still a real call.
            var actor=h.frame.state();var env=h.frame.environment();h.walk();assertEquals(actor,h.frame.state());assertSame(env,h.frame.environment());
            assertNotNull(h.lastPinball);h.finish();
        }
    }
    @Test void originalFailureAndAliasThrowableRetainedWithNoSelfSuppression() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flipper(h,0);var fault=new AssertionError("original and verifier alias");h.beforeAdopt=()->{h.onRead=()->{throw fault;};throw fault;};
            assertSame(fault,assertThrows(AssertionError.class,h::walk));for(var s:fault.getSuppressed())assertNotSame(fault,s);
            assertEquals(0,h.placement.pool().state(40).routine());
        }
    }
    @Test void fullPlacementAndCrossOwnerMutationRejectBeforeSelectedStore() throws Exception {
        for(int mode=0;mode<4;mode++)try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flipper(h,0);var selected=h.placement.pool().state(40);int which=mode;
            h.beforeAdopt=()->{switch(which){
                case 0->h.placement.pool().write(h.placement.pool().state(127).moveWord(X_FRACTION,0xcafe));
                case 1->h.placement.bsetSavedFlag(1,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
                case 2->h.edit(1,b->b[0x1f]=(byte)0xd7);
                default->h.terrainDiverged=true;
            }};
            assertThrows(IllegalArgumentException.class,h::walk);assertEquals(selected,h.placement.pool().state(40));
        }
    }
    @Test void healthyPreparedAdoptionRejectsForeignFrameReentryAndRetiredReceipt() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness();var other=new TestSonicCdR31aObjectPass.Harness()){
            flipper(h,0);h.beforeAdopt=()->{var p=h.lastPinball;assertThrows(IllegalStateException.class,h::walk);assertThrows(IllegalStateException.class,()->h.pass.discard());
                var call=CdR31PinballCall.fromOriginal(p);assertThrows(IllegalArgumentException.class,()->call.requirePlayerPreparation(other.frame));};
            h.walk();assertThrows(IllegalStateException.class,h.lastPinball::applyNative);h.beforeAdopt=()->{};h.finish();
        }
    }
    @Test void unsupportedDirtyRoutineAndBadMappingKeepOriginalFaultBoundaries() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flipper(h,0xd7);var before=h.placement.pool().state(40);assertThrows(UnsupportedOperationException.class,h::walk);assertEquals(before,h.placement.pool().state(40));
        }
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flipper(h,2);h.placement.pool().write(h.placement.pool().state(40).moveLong(MAPPINGS,0x20dead));h.walk();
            assertEquals(0,h.placement.pool().state(40).spriteFlags()&128);assertThrows(UnsupportedOperationException.class,h::finish);
        }
    }
}
