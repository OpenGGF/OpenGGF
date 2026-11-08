package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.CdR31aArtResidency;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Tests FIRST, ALL JAVA UNRUN. Actual original-media ingress/common reader/Frame/pool/pass;
 * this inherited harness explicitly CONSTRUCTS Core/Ports/physical vectors, never World authority.
 * Genuine public Boot/World tests are separate. No natural reach, native parity or pixels claimed. */
class TestSonicCdR31aExplosionPointsPass {
    @Test void actualR31DispatchAndDataAreIndependentNativeLiterals() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var index=h.assets.view(CdR31aAssets.Asset.OBJECT_INDEX);
            assertEquals(0x205FD6,index.unsignedLong((0x18-1)*4));assertEquals(0x20A7A0,index.unsignedLong((0x1C-1)*4));
            var e=h.objects.explosionAssets();var p=h.objects.pointsAssets();assertSame(e,h.objects.explosionAssets());assertSame(p,h.objects.pointsAssets());
            assertEquals(0x23A6B4,e.mappingsAddress());assertEquals(0x20A74E,p.mappingsAddress());
            assertEquals("0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44",sha(e.explosion().mappings()));
            assertEquals("9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649",sha(e.explosion().animationTable()));
            assertEquals("24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e",sha(p.points().mappings()));
            assertArrayEquals(new int[]{2,3,4,4,4,3,4},e.explosion().mappingFrames().stream().mapToInt(x->x.pieces().size()).toArray());
            assertArrayEquals(new int[]{2,2,2,2,1,3},p.points().mappingFrames().stream().mapToInt(x->x.pieces().size()).toArray());assertEquals(67,CdR31aAssets.Asset.values().length);
        }
    }
    @Test void equalRowsAndValidDataCannotMintTheRegisteredPlacementRelation() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var detached=new CdObjectPlacementRuntime(h.assets.placements());var before=h.placement.capture();
            assertThrows(IllegalArgumentException.class,()->CdExplosion.fromR31aAssets(detached,h.assets,h.objects,
                new CdExplosion.DispatchObserver(){public void beforePublication(CdExplosion.Publication c){}public void afterPublication(CdExplosion.Publication c){}public void beforeCall(Object c){}public void afterCall(Object c){}},c->{},new CdExplosion.ReceiptGuard(){public void prepared(Object c){}public void completed(Object c){}}));
            assertThrows(IllegalArgumentException.class,()->CdPoints.fromR31aAssets(detached,h.assets,h.objects,
                new CdPoints.DispatchObserver(){public void beforePublication(CdPoints.Publication c){}public void afterPublication(CdPoints.Publication c){}public void beforeCall(Object c){}public void afterCall(Object c){}},c->{},new CdPoints.ReceiptGuard(){public void prepared(Object c){}public void completed(Object c){}}));
            nonwrites(before,h,Set.of());assertFalse(h.frame.closed());
        }
    }
    @Test void actualMapperOwnsOnlyTheTwoExactRootsAndSourcePatternsStaySeparate() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var m=CdObjectMappings.fromR31aAssets(h.assets,h.objects);
            assertEquals(4,assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,m.resolve(0x23A6B4,false,6)).countByte());
            assertEquals(3,assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,m.resolve(0x20A74E,false,5)).countByte());
            assertThrows(UnsupportedOperationException.class,()->m.resolve(0x23A6B5,false,0));assertThrows(UnsupportedOperationException.class,()->m.resolve(0x20A74E,false,6));
            var unknown=CdR31aArtResidency.unknown(h.assets);var e=assertThrows(CdR31aArtResidency.OwnershipBoundary.class,()->CdR31aArtResidency.readTile(unknown,0x680));
            assertEquals(CdR31aArtResidency.Boundary.UNKNOWN_PATTERN,e.boundary());h.objects.explosionAssets();assertThrows(CdR31aArtResidency.OwnershipBoundary.class,()->CdR31aArtResidency.readTile(unknown,0x6C6));
        }
    }
    @Test void dirtyNonbadnikInitSkipsPointsAndKeepsCompleteNonwrites() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var source=explosion(h,32,0,1).moveByte(SUBTYPE,1).moveByte(LAYER,1).moveByte(COLLISION_TYPE,6).moveByte(SAVED_FLAGS_ID,200).moveWord(X_FRACTION,0xCAFE).moveWord(Y_FRACTION,0xBEEF);
            h.placement.pool().write(source);var before=h.placement.capture();var actor=h.frame.captureActor();var shared=h.frame.captureShared();var display=h.frame.display();var clock=h.frame.vIntClock();var cut=h.cut;
            h.walk();assertEquals(firstExplosion(source),h.placement.pool().state(32));nonwrites(before,h,Set.of(32));assertSame(cut,h.cut);
            assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertSame(display,h.frame.display());assertEquals(clock,h.frame.vIntClock());h.finish();
        }
    }
    @Test void firstFreeA1ChildRunsLaterOnceAndKeepsDirtyFractionsAndFields() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var source=explosion(h,32,0,0).moveWord(EXPLOSION_POINTS_FRAME,0x010A);h.placement.pool().write(source);var dirty=dirtyFree(h,33);var before=h.placement.capture();
            h.walk();var child=dirty.moveByte(ID,0x1C).moveWord(X,100).moveWord(Y,100).moveByte(SUBTYPE,0x85);
            assertEquals(pointStep(child),h.placement.pool().state(33));assertEquals(firstExplosion(source),h.placement.pool().state(32));nonwrites(before,h,Set.of(32,33));h.finish();
        }
    }
    @Test void earlierFirstFreeChildWaitsForItsOwnNextWalk() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var source=explosion(h,40,0,0).moveWord(EXPLOSION_POINTS_FRAME,6);h.placement.pool().write(source);var dirty=dirtyFree(h,32);h.walk();
            var child=dirty.moveByte(ID,0x1C).moveWord(X,100).moveWord(Y,100).moveByte(SUBTYPE,0x83);assertEquals(child,h.placement.pool().state(32));h.finish();
            h.walk();assertEquals(pointStep(child),h.placement.pool().state(32));h.finish();
        }
    }
    @Test void capacityFailureDoesNotRetryAfterLaterSlotsDelete() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            for(int w=32;w<128;w++)explosion(h,w,4,1);var source=explosion(h,32,0,0);h.walk();assertEquals(firstExplosion(source),h.placement.pool().state(32));
            for(int w=33;w<128;w++)assertArrayEquals(new byte[64],h.placement.pool().state(w).copyBytes());h.finish();
        }
    }
    @Test void dirtyChildRoutineIsNotResetAndDeleteStillCallsTheActualZeroIdDraw() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            explosion(h,32,0,0);var dirty=dirtyFree(h,33).moveByte(ROUTINE,2).moveByte(POINTS_TIMER,1);h.placement.pool().write(dirty);
            var calls=new int[1];var p=(CdPoints)field(h.pass,"points");var original=(CdPoints.DispatchObserver)field(p,"observer");
            set(p,"observer",new CdPoints.DispatchObserver(){public void beforePublication(CdPoints.Publication c){original.beforePublication(c);}public void afterPublication(CdPoints.Publication c){original.afterPublication(c);}public void beforeCall(Object c){assertEquals(0,((CdPoints.DrawCall)c).source().id());calls[0]++;original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
            h.walk();assertEquals(1,calls[0]);assertArrayEquals(new byte[64],h.placement.pool().state(33).copyBytes());h.finish();
        }
    }
    @Test void doneNonzeroOriginDeletesAll64WithoutDrawingOrClearingSavedBank() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=explosion(h,32,4,1).moveByte(SAVED_FLAGS_ID,200);h.placement.pool().write(s);h.placement.bsetSavedFlag(200,1,CdObjectPlacementRuntime.SavedBit.LOADED);var before=h.placement.capture();h.walk();assertArrayEquals(new byte[64],h.placement.pool().state(32).copyBytes());nonwrites(before,h,Set.of(32));h.finish();assertEquals(0,h.cut.completedCpu().orElseThrow().spriteCount());}
    }
    @Test void flowerConversionCompletesOnlyItsTwoNativeStoresThenStopsAtNextVisit() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=explosion(h,32,4,0);h.walk();assertEquals(s.moveByte(ID,0x1F).moveByte(ROUTINE,0),h.placement.pool().state(32));h.finish();
            var e=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(SonicCdR31aObjectPass.Boundary.OBJECT_FAMILY,e.boundary());assertEquals(0x1F,e.value());}
    }
    @Test void pointsInitFallsThroughOnceAndPreservesAllUnwrittenBytes() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=points(h,32,0,0x85).moveWord(Y_FRACTION,0xBEEF).moveByte(PRIORITY,3).moveByte(WIDTH,17);h.placement.pool().write(s);var before=h.placement.capture();h.walk();assertEquals(pointStep(s),h.placement.pool().state(32));assertEquals(23,h.placement.pool().state(32).unsignedByte(POINTS_TIMER));nonwrites(before,h,Set.of(32));h.finish();}
    }
    @Test void pointsRawTimerZeroWrapsAndTimerOneDeletesWithoutASecondOwner() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=points(h,32,2,0x80).moveByte(POINTS_TIMER,0);h.placement.pool().write(s);h.walk();assertEquals(s.addByte(POINTS_TIMER,-1).addWord(Y,-2).moveByte(SPRITE_FLAGS,4),h.placement.pool().state(32));h.finish();}
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {points(h,32,2,0x80);h.placement.pool().write(h.placement.pool().state(32).moveByte(POINTS_TIMER,1));h.walk();assertArrayEquals(new byte[64],h.placement.pool().state(32).copyBytes());h.finish();}
    }
    @Test void sameIdReuseHasNoInventedGenerationAndWaitsWhenReallocatedEarlier() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {points(h,32,2,0x80);h.placement.pool().write(h.placement.pool().state(32).moveByte(POINTS_TIMER,1));explosion(h,40,0,0);h.walk();assertEquals(0x1C,h.placement.pool().state(32).id());assertEquals(0,h.placement.pool().state(32).routine());assertEquals(0,h.placement.pool().state(32).unsignedByte(POINTS_TIMER));
            var reused=h.placement.pool().state(32);
            // The earlier zero-ID Draw retained this slot reference. The later allocation
            // supplies live ID1C with an uninitialized mapping; no generation freezes ID0.
            var error=assertThrows(UnsupportedOperationException.class,h::finish);
            assertTrue(error.getMessage().contains("slot 32 mapping $00000000"));
            assertEquals(reused,h.placement.pool().state(32));}
    }
    @Test void dynamicHudSubtypeStaysUnownedAndReservedHudKeepsItsOldProducer() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {points(h,32,0,0);var before=h.placement.capture();var e=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(SonicCdR31aObjectPass.Boundary.PHYSICAL_OWNER,e.boundary());nonwrites(before,h,Set.of());}
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {h.reserved(2,0x1C,0);h.walk();assertEquals(2,h.row(2).unsignedByte(0x24));assertEquals(0x8568,h.row(2).unsignedWord(2));h.finish();}
    }
    @Test void heldUnknownAnimationAvoidsItsDirectoryUntilTheReachedExpiryPrefix() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=explosion(h,32,2,1).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_TIME,1);h.placement.pool().write(s);h.walk();assertEquals(0,h.placement.pool().state(32).unsignedByte(ANIMATION_TIME));h.finish();
            var e=assertThrows(CdExplosion.UnsupportedExplosion.class,h::walk);assertEquals(CdExplosion.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,e.boundary());assertEquals(255,h.placement.pool().state(32).unsignedByte(ANIMATION_TIME));assertNull(field(h.pass,"childEffect"));}
    }
    @Test void selectedMappingFailureRetainsActualPointsRiseAndOldCpu() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=points(h,32,0,0x86);var old=h.cut.cpuSat();h.walk();assertEquals(pointStep(s),h.placement.pool().state(32));assertThrows(UnsupportedOperationException.class,h::finish);assertSame(old,h.cut.cpuSat());}
    }
    @Test void cachedPairUsesOriginalPlacementAndSurvivesCompleteRestoreWithoutReplay() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var e=field(h.pass,"explosion");var p=field(h.pass,"points");explosion(h,32,0,0);var before=h.placement.capture();h.walk();var after=h.placement.capture();h.finish();h.placement.restore(before);h.walk();assertEquals(after.pool().slots(),h.placement.capture().pool().slots());assertSame(e,field(h.pass,"explosion"));assertSame(p,field(h.pass,"points"));h.finish();}
    }
    @Test void clonedPublicationCannotReplaceItsOpaqueOriginalPendingOperation() throws Exception {publicationFault(false,true);}
    @Test void originalRuntimeFaultBeforeHeaderRetainsItsIdentityAndPreimage() throws Exception {publicationFault(false,false);}
    @Test void originalErrorAfterHeaderRetainsTheReachedPrefixWithoutCompletionReplay() throws Exception {publicationFault(true,false);}
    @Test void omittedObserverCannotTurnValueEqualityIntoPreparation() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=explosion(h,32,0,1);var b=(CdExplosion)field(h.pass,"explosion");set(b,"observer",new CdExplosion.DispatchObserver(){public void beforePublication(CdExplosion.Publication c){}public void afterPublication(CdExplosion.Publication c){}public void beforeCall(Object c){}public void afterCall(Object c){}});assertThrows(IllegalStateException.class,h::walk);assertSame(s,h.placement.pool().state(32));assertNull(field(h.pass,"childEffect"));}
    }
    @Test void noOpSameValueDrawCannotCompleteWithoutPrivateQueueConsumption() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=points(h,32,2,0x80).moveByte(SPRITE_FLAGS,4);h.placement.pool().write(s);var p=(CdPoints)field(h.pass,"points");set(p,"sourceDraw",(CdPoints.SourceDraw)c->{});assertThrows(IllegalStateException.class,h::walk);assertEquals(s.addByte(POINTS_TIMER,-1).addWord(Y,-2),h.placement.pool().state(32));assertNull(field(h.pass,"childEffect"));}
    }
    @Test void duplicateForeignThreadAndExpiredDrawCallsCannotAcquireAnotherVisit() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {points(h,32,0,0x80);var p=(CdPoints)field(h.pass,"points");var original=(CdPoints.SourceDraw)field(p,"sourceDraw");var held=new AtomicReference<CdPoints.DrawCall>();
            set(p,"sourceDraw",(CdPoints.SourceDraw)c->{held.set(c);assertThrows(IllegalStateException.class,()->h.pass.runWalk(7));assertFalse(h.frame.closed());var failure=new AtomicReference<Throwable>();var t=new Thread(()->{try{original.draw(c);}catch(Throwable x){failure.set(x);}});t.start();try{t.join(5000);}catch(InterruptedException x){throw new AssertionError(x);}assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,failure.get());original.draw(c);assertThrows(IllegalStateException.class,()->original.draw(c));});
            h.walk();assertFalse(p.isCurrentCall(held.get()));assertThrows(IllegalStateException.class,()->original.draw(held.get()));assertFalse(h.frame.closed());h.finish();}
    }
    @Test void originalDrawErrorKeepsActualClearAndRiseWithoutRetry() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=points(h,32,0,0x80);var p=(CdPoints)field(h.pass,"points");var original=(CdPoints.SourceDraw)field(p,"sourceDraw");var error=new AssertionError("original after Draw");set(p,"sourceDraw",(CdPoints.SourceDraw)c->{original.draw(c);throw error;});assertSame(error,assertThrows(AssertionError.class,h::walk));assertEquals(pointStep(s),h.placement.pool().state(32));assertNull(field(h.pass,"childEffect"));}
    }
    @Test void unownedBankSlotAndFrameWritesAttachOnlyDistinctFaultsWithoutRollback() throws Exception {
        for(int mutation=0;mutation<3;mutation++)try(var h=new TestSonicCdR31aObjectPass.Harness()) {explosion(h,32,0,1);var b=(CdExplosion)field(h.pass,"explosion");var original=(CdExplosion.DispatchObserver)field(b,"observer");var error=new AssertionError("original prefix fault "+mutation);final int what=mutation;
            set(b,"observer",new CdExplosion.DispatchObserver(){public void beforePublication(CdExplosion.Publication c){original.beforePublication(c);if(what==0)h.placement.bsetSavedFlag(200,2,CdObjectPlacementRuntime.SavedBit.LOADED);if(what==1)h.placement.pool().write(h.placement.pool().state(127).moveByte(RAMP_TIMER,77));if(what==2)h.frame.applyPreparedObjectSolidPublication(h.frame.prepareObjectPrimaryAngle(h.frame.environment().movement().globals().primaryAngle(),7));throw error;}public void afterPublication(CdExplosion.Publication c){original.afterPublication(c);}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
            assertSame(error,assertThrows(AssertionError.class,h::walk));assertTrue(error.getSuppressed().length>0);if(what==0)assertEquals(128,h.placement.savedFlag(200,2));if(what==1)assertEquals(77,h.placement.pool().state(127).unsignedByte(RAMP_TIMER));if(what==2)assertEquals(7,h.frame.environment().movement().globals().primaryAngle());assertNull(field(h.pass,"childEffect"));}
    }
    private static void publicationFault(boolean after,boolean clone) throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {var s=explosion(h,32,0,1);var b=(CdExplosion)field(h.pass,"explosion");var original=(CdExplosion.DispatchObserver)field(b,"observer");var error=after?new AssertionError("original after header"):new IllegalStateException("original before header");
            set(b,"observer",new CdExplosion.DispatchObserver(){public void beforePublication(CdExplosion.Publication c){if(clone){original.beforePublication(new CdExplosion.Publication(c.ordinal(),c.kind(),c.cut(),c.source(),c.before(),c.output()));return;}original.beforePublication(c);if(!after)throw (RuntimeException)error;}public void afterPublication(CdExplosion.Publication c){original.afterPublication(c);if(after)throw (Error)error;}public void beforeCall(Object c){original.beforeCall(c);}public void afterCall(Object c){original.afterCall(c);}});
            if(clone){assertThrows(IllegalStateException.class,h::walk);assertSame(s,h.placement.pool().state(32));}else{Throwable seen=after?assertThrows(Error.class,h::walk):assertThrows(RuntimeException.class,h::walk);assertSame(error,seen);assertEquals(after?header(s):s,h.placement.pool().state(32));}assertNull(field(h.pass,"childEffect"));}
    }
    static CdObjectState explosion(TestSonicCdR31aObjectPass.Harness h,int w,int routine,int origin){var s=h.placement.pool().state(w).moveByte(ID,0x18).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,origin).moveByte(SUBTYPE,0).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveWord(X,100).moveWord(Y,100).moveLong(MAPPINGS,0x23A6B4).moveByte(ANIMATION_TIME,3);h.placement.pool().write(s);return s;}
    static CdObjectState points(TestSonicCdR31aObjectPass.Harness h,int w,int routine,int subtype){var s=h.placement.pool().state(w).moveByte(ID,0x1C).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype).moveByte(SPRITE_FLAGS,0x84).moveWord(X,100).moveWord(Y,100).moveLong(MAPPINGS,0x20A74E).moveByte(POINTS_TIMER,5);h.placement.pool().write(s);return s;}
    private static CdObjectState dirtyFree(TestSonicCdR31aObjectPass.Harness h,int w){var s=h.placement.pool().state(w);for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0xA5A5A5A5);for(var f:CdObjectState.WordField.values())s=s.moveWord(f,0xA5A5);for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,0xA5);s=s.moveByte(ID,0).moveByte(ROUTINE,0).moveByte(SPRITE_FLAGS,0x81);h.placement.pool().write(s);return s;}
    private static CdObjectState header(CdObjectState s){return s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveByte(PRIORITY,1).moveWord(TILE,s.unsignedByte(LAYER)==0?0x8680:0x0680).moveLong(MAPPINGS,0x23A6B4);}
    private static CdObjectState firstExplosion(CdObjectState s){int a=s.subtype()==0?0:1;return header(s).moveByte(COLLISION_TYPE,0).moveByte(ANIMATION,a).moveByte(PREVIOUS_ANIMATION,a).moveByte(ANIMATION_FRAME,1).moveByte(ANIMATION_TIME,3).moveByte(MAP_FRAME,0).moveByte(SPRITE_FLAGS,((s.spriteFlags()|4)&0x7C)|(s.flags()&3));}
    private static CdObjectState pointStep(CdObjectState s){if(s.routine()==0)s=s.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,s.spriteFlags()|4).moveWord(TILE,0x6C6).moveLong(MAPPINGS,0x20A74E).moveByte(MAP_FRAME,s.subtype()&127).moveByte(POINTS_TIMER,24);return s.addByte(POINTS_TIMER,-1).addWord(Y,-2).moveByte(SPRITE_FLAGS,s.spriteFlags()&127);}
    private static void nonwrites(CdObjectPlacementRuntime.Snapshot before,TestSonicCdR31aObjectPass.Harness h,Set<Integer> changed){var n=h.placement.capture();assertEquals(before.status(),n.status());assertArrayEquals(before.savedFlags().copy(),n.savedFlags().copy());for(int w=32;w<128;w++)if(!changed.contains(w))assertEquals(before.pool().slots().get(w-32),h.placement.pool().state(w));}
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private static String sha(CdAssetBytes b){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b.copy()));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
}
