package com.openggf.game.soniccd;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** UNRUN. Original media/cached increasing pass/Frame; constructed Host and slot/contact vectors.
 * Public Boot/World producer obligations are separately TestCdBumperWorldBinding. */
class TestCdBumperOriginalPass {
    private static TestSonicCdR31aObjectPass.Harness h() throws Exception {return new TestSonicCdR31aObjectPass.Harness();}
    private static void bumper(TestSonicCdR31aObjectPass.Harness h,int whole,int routine,boolean hit){
        h.dynamic(whole,0x1d,routine,0);h.placement.pool().write(h.placement.pool().state(whole).moveWord(X,100).moveWord(Y,100)
                .moveWord(MOVING_SPRING_BASE_Y,100).moveWord(SWING_MAX,5).moveByte(COLLISION_STATUS,hit?1:0)
                .moveByte(SPRITE_FLAGS,0x84).moveLong(MAPPINGS,0x20b0aa));
    }
    @Test void actualInitUsesFullWordAxisAndPreservesReservedWholeOneAndBank() throws Exception {
        try(var h=h()){
            h.edit(1,row->{row[0x22]=(byte)0xa5;row[0x1f]=(byte)0xd7;});var reserved=h.cut.core().reserved().copy();var bank=h.placement.capture().savedFlags().copy();
            bumper(h,40,0,false);h.placement.pool().write(h.placement.pool().state(40).moveByte(SUBTYPE,0x80).moveByte(SUBTYPE2,0).moveLong(ANTON_X_DELTA,0x18000));
            var actor=h.frame.state();var env=h.frame.environment();h.walk();var s=h.placement.pool().state(40);
            assertEquals(101,s.centreX());assertEquals(0x8000,s.word(X_FRACTION));assertEquals(100,s.centreY());assertEquals(0xffff,s.word(BOSS_PARENT));
            assertEquals(5,s.word(SWING_MAX));assertSame(actor,h.frame.state());assertSame(env,h.frame.environment());
            assertArrayEquals(reserved,h.cut.core().reserved().copy());assertArrayEquals(bank,h.placement.capture().savedFlags().copy());
        }
    }
    @Test void oldPhysicalDirtyPointsSlotWaitsForNextWalkAndKeepsDirtyFields() throws Exception {
        try(var h=h()){
            bumper(h,127,2,true);var dirty=h.placement.pool().state(32).moveByte(ROUTINE,0xd7).moveWord(X_FRACTION,0xcafe).moveByte(ANGLE,0x6d);h.placement.pool().write(dirty);
            h.walk();var child=h.placement.pool().state(32);assertEquals(0x1c,child.id());assertEquals(0xd7,child.routine());assertEquals(0xcafe,child.word(X_FRACTION));assertEquals(0x6d,child.unsignedByte(ANGLE));assertEquals(0x80,child.subtype());
            assertEquals(10,h.cut.world().score());assertEquals(4,h.placement.pool().state(127).word(SWING_MAX));h.finish();
            // The genuine Points body now owns this visit. Its dirty unsupported routine
            // must fault before writes, not disappear behind the old ID1C family boundary.
            var reached=assertThrows(CdPoints.UnsupportedPoints.class,h::walk);
            assertEquals(CdPoints.UnsupportedBoundary.ROUTINE_DISPATCH,reached.boundary());assertEquals(32,reached.wholeIndex());
            assertEquals(child,h.placement.pool().state(32));assertEquals(10,h.cut.world().score());
        }
        try(var h=h()){
            bumper(h,127,2,true);var dirty=h.placement.pool().state(32).moveByte(ROUTINE,0).moveWord(X_FRACTION,0xcafe).moveByte(ANGLE,0x6d);h.placement.pool().write(dirty);
            h.walk();var child=h.placement.pool().state(32);assertEquals(0x1c,child.id());assertEquals(0,child.routine());assertEquals(0xcafe,child.word(X_FRACTION));assertEquals(0x6d,child.unsignedByte(ANGLE));assertEquals(0x80,child.subtype());
            assertEquals(10,h.cut.world().score());assertEquals(4,h.placement.pool().state(127).word(SWING_MAX));h.finish();
            h.walk();var reached=h.placement.pool().state(32);assertEquals(2,reached.routine());assertEquals(23,reached.unsignedByte(POINTS_TIMER));
            assertEquals(child.centreX(),reached.centreX());assertEquals((child.centreY()-2)&0xffff,reached.centreY());assertEquals(0xcafe,reached.word(X_FRACTION));assertEquals(0x6d,reached.unsignedByte(ANGLE));
            assertEquals(0x20a74e,reached.longBits(MAPPINGS));assertEquals(10,h.cut.world().score());h.finish();
        }
    }
    @Test void laterDirtyPointsSlotIsReachedInSameWalkAfterAllParentPrefixes() throws Exception {
        try(var h=h()){
            bumper(h,32,2,true);h.placement.pool().write(h.placement.pool().state(33).moveWord(X_FRACTION,0xbeef).moveByte(ROUTINE,0xc1));
            var reached=assertThrows(CdPoints.UnsupportedPoints.class,h::walk);assertEquals(CdPoints.UnsupportedBoundary.ROUTINE_DISPATCH,reached.boundary());assertEquals(33,reached.wholeIndex());
            assertEquals(10,h.cut.world().score());assertEquals(0,h.placement.pool().state(32).unsignedByte(COLLISION_STATUS));assertEquals(0xbeef,h.placement.pool().state(33).word(X_FRACTION));assertEquals(0xc1,h.placement.pool().state(33).routine());
        }
        try(var h=h()){
            bumper(h,32,2,true);h.placement.pool().write(h.placement.pool().state(33).moveWord(X_FRACTION,0xbeef).moveByte(ROUTINE,0));
            h.walk();var child=h.placement.pool().state(33);
            assertEquals(0x1c,child.id());assertEquals(2,child.routine());assertEquals(23,child.unsignedByte(POINTS_TIMER));assertEquals(98,child.centreY());
            assertEquals(0xbeef,child.word(X_FRACTION));assertEquals(0x20a74e,child.longBits(MAPPINGS));assertEquals(10,h.cut.world().score());
            assertEquals(4,h.placement.pool().state(32).word(SWING_MAX));assertEquals(0,h.placement.pool().state(32).unsignedByte(COLLISION_STATUS));h.finish();
        }
    }
    @Test void scoreOwnerFaultKeepsAwardDecrementBeforeAddPointsAndAllocation() throws Exception {
        try(var h=h()){
            bumper(h,32,2,true);var error=new AssertionError("original score owner");h.beforeAdopt=()->{if(h.lastBumper!=null&&h.lastBumper.operation() instanceof CdPinballContext.ScoreStore)throw error;};
            assertSame(error,assertThrows(AssertionError.class,h::walk));assertEquals(4,h.placement.pool().state(32).word(SWING_MAX));assertEquals(0,h.cut.world().score());assertEquals(0,h.placement.pool().state(33).id());assertEquals(1,h.placement.pool().state(32).unsignedByte(COLLISION_STATUS));
            assertThrows(IllegalStateException.class,h.lastBumper::applyNative);
        }
    }
    @Test void subCpuFailureAfterOriginalPublicationRetainsScoreChildSoundBeforeContactClear() throws Exception {
        try(var h=h()){
            bumper(h,127,2,true);var error=new AssertionError("original sound return");h.afterAdopt=()->{};
            h.beforeAdopt=()->{if(h.lastBumper!=null&&h.lastBumper.operation() instanceof CdPinballContext.Sound s&&s.nativeId()==0x7f)h.afterAdopt=()->{throw error;};};
            assertSame(error,assertThrows(AssertionError.class,h::walk));assertEquals(10,h.cut.world().score());assertEquals(0x1c,h.placement.pool().state(32).id());assertEquals(1,h.placement.pool().state(127).unsignedByte(COLLISION_STATUS));
            assertTrue(h.cut.sounds().stream().anyMatch(s->s instanceof SonicCdGameModule.PastBumperSubCpu c&&c.nativeId()==0x7f));
        }
    }
    @Test void originalPublicationRejectsThreadReentryAndRetiredReplay() throws Exception {
        try(var h=h()){
            bumper(h,40,0,false);h.beforeAdopt=()->{
                var p=h.lastBumper;if(p==null)return;var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{p.applyNative();}catch(Throwable x){error.set(x);}});t.start();try{t.join(5000);}catch(InterruptedException x){throw new AssertionError(x);}assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,error.get());assertThrows(IllegalStateException.class,h::walk);
            };
            h.walk();assertThrows(IllegalStateException.class,h.lastBumper::applyNative);assertThrows(IllegalStateException.class,()->h.lastBumper.operation().primaryBefore());
        }
    }
    @Test void fullPhysicalPoolStillAwardsOnlyFiveTimesWithoutAnyPointsReplay() throws Exception {
        try(var h=h()){
            for(int w=32;w<128;w++)bumper(h,w,2,false);
            for(int n=0;n<7;n++){
                h.placement.pool().write(h.placement.pool().state(32).moveByte(COLLISION_STATUS,1));h.walk();
                assertEquals(10*Math.min(5,n+1),h.cut.world().score());assertEquals(Math.max(0,4-n),h.placement.pool().state(32).word(SWING_MAX));
                for(var row:h.placement.pool().capture().slots())assertEquals(0x1d,row.id());h.finish();
            }
        }
    }
    @Test void changedReservedWholeOneIsRejectedBeforeOriginalNativeStore() throws Exception {
        try(var h=h()){
            bumper(h,40,0,false);var source=h.placement.pool().state(40);var original=new AssertionError("original reserved mutation");
            h.beforeAdopt=()->{if(h.lastBumper!=null){h.edit(1,row->row[0x1f]=(byte)0xd7);throw original;}};
            assertSame(original,assertThrows(AssertionError.class,h::walk));assertEquals(source,h.placement.pool().state(40));
            assertTrue(original.getSuppressed().length>0);for(var x:original.getSuppressed())assertNotSame(original,x);
        }
    }
    @Test void sameIdReuseStartsNewInitRatherThanReplayingRetiredBody() throws Exception {
        try(var h=h()){
            bumper(h,40,0,false);h.walk();var retired=h.lastBumper;h.finish();
            h.placement.pool().write(h.placement.pool().state(40).moveByte(ROUTINE,0).moveWord(SWING_MAX,0));
            h.walk();assertEquals(5,h.placement.pool().state(40).word(SWING_MAX));assertThrows(IllegalStateException.class,retired::applyNative);
        }
    }
    @Test void selectedUnknownMappingFailsAtSharedDrainAfterRealBodyAndDrawPrefix() throws Exception {
        try(var h=h()){
            bumper(h,40,2,false);h.placement.pool().write(h.placement.pool().state(40).moveLong(MAPPINGS,0x20dead));h.walk();
            assertEquals(0,h.placement.pool().state(40).spriteFlags()&128);
            assertThrows(UnsupportedOperationException.class,h::finish);
        }
    }

    @Test void actualPlayerCallCannotBeAppliedByPreparedValuesOrAnotherFrame() throws Exception {
        try(var h=h();var other=h()){
            bumper(h,40,2,true);var saw=new java.util.concurrent.atomic.AtomicBoolean();
            h.beforeAdopt=()->{
                var p=h.lastBumper;if(p==null||!(p.operation() instanceof CdPinballContext.PlayerStore))return;saw.set(true);
                var call=CdR31BumperCall.fromOriginal(p);var before=h.frame.state();var prepared=h.frame.prepareBumperPlayerStore(call);
                assertSame(before,h.frame.state());assertThrows(IllegalStateException.class,()->h.frame.applyBumperPlayerStore(prepared));
                assertSame(before,h.frame.state());assertThrows(IllegalArgumentException.class,()->other.frame.prepareBumperPlayerStore(call));
            };
            // Child goes into already-walked slot32, so the original parent can return.
            h.dynamic(40,0,0,0);bumper(h,127,2,true);h.walk();assertTrue(saw.get());
            assertTrue(java.util.Arrays.stream(CdR31BumperCall.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        }
    }

}
