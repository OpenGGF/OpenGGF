package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** UNRUN. Labelled constructed scalar/body vectors, never World producer evidence. */
class TestCdPinballPrimaryOnly {
    private static CdPinballContext primary(TestCdPinballFamily.Fixture f) {
        return CdPinballContext.forPrimary(new CdPinballContext.PrimaryInput(f.pool.capture(), f.actor(), f.environment(),
                new CdPlayerTouchResponse.WorldGlobals(17,3,0,f.score,f.nextLife,0), CdAssetBytes.copyOf(f.saved),
                f.period,f.good,f.debug,f.act), TestCdPinballFamily.assets(), TestCdPinballFamily.math(), f);
    }
    @Test void allBumperBranchesRunWithoutAnySecondarySupplierAndMatchLegacyValues() {
        for (int routine : new int[]{0,2}) for (boolean contact : new boolean[]{false,true})
            for (boolean full : new boolean[]{false,true}) for (boolean delete : new boolean[]{false,true}) {
                var legacy = new TestCdPinballFamily.Fixture(); var one = new TestCdPinballFamily.Fixture();
                for (var f : List.of(legacy,one)) {
                    var i=f.object(0x1d,routine,0x80);i.b(0x21,contact?1:0);i.b(1,0x84);i.w(0x34,5);i.b(0x23,5);i.l(0x2a,0x18000);f.set(i);
                    f.saved[7]=(byte)0x89; f.despawn=delete; if(full)f.full();
                }
                var both=CdBumper.run(legacy.context(),32);var c=primary(one);var only=CdBumper.runPrimary(c,32);
                for(int n=0;n<96;n++)assertArrayEquals(both.pool().slots().get(n).copyBytes(),only.pool().slots().get(n).copyBytes());
                assertEquals(both.players().primary(),only.primary());assertEquals(both.players().primaryEnvironment(),only.environment());
                assertEquals(both.score(),only.score());assertArrayEquals(both.savedFlags().copy(),only.savedFlags().copy());
                assertEquals(both.completed(),only.completed());assertEquals(legacy.calls,one.calls);
                assertThrows(IllegalStateException.class,c::result);assertThrows(IllegalStateException.class,()->CdBumper.runPrimary(c,32));
            }
    }
    @Test void legacyResultAndEntryRejectBeforePrimaryOnlySideEffects() {
        var f=new TestCdPinballFamily.Fixture();f.object(0x1d,0,0);var c=primary(f);var before=c.primaryResult();
        assertThrows(IllegalStateException.class,c::result);assertThrows(IllegalStateException.class,()->CdBumper.run(c,32));
        assertEquals(before.pool().slots(),c.primaryResult().pool().slots());assertSame(before.primary(),c.primaryResult().primary());assertArrayEquals(before.savedFlags().copy(),c.primaryResult().savedFlags().copy());assertTrue(f.calls.isEmpty());assertEquals(2,CdBumper.runPrimary(c,32).pool().slots().getFirst().routine());
    }
    @Test void originalPrimaryPrefixIsSynchronousThreadBoundAndNotAReplayAuthority() {
        var retainedObjectStore=new AtomicReference<CdPinballContext.ObjectStore>();var seen=new ArrayList<Integer>();
        var f=new TestCdPinballFamily.Fixture(){@Override public void objectStore(CdPinballContext.ObjectStore op){
            retainedObjectStore.set(op);var before=op.primaryBefore();assertNotNull(before.environment());seen.add(op.ordinal());
            assertThrows(IllegalStateException.class,op::before);
            var error=new AtomicReference<Throwable>();var t=new Thread(()->{try{op.primaryBefore();}catch(Throwable x){error.set(x);}});t.start();
            try{t.join(5000);}catch(InterruptedException x){throw new AssertionError(x);}assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,error.get());
            op.applyNative();assertThrows(IllegalStateException.class,op::applyNative);
        }};
        f.object(0x1d,0,0);var c=primary(f);CdBumper.runPrimary(c,32);
        assertThrows(IllegalStateException.class,()->retainedObjectStore.get().primaryBefore());
        for(int i=1;i<seen.size();i++)assertTrue(seen.get(i)>seen.get(i-1));
    }
    @Test void originalSoundErrorRetainsNativeScoreChildContactPrefixAndRetiresCall() {
        var f=new TestCdPinballFamily.Fixture();var i=f.object(0x1d,2,0);i.b(1,0x84);i.b(0x21,1);i.w(0x34,5);f.set(i);f.failSound=0x7f;
        var c=primary(f);assertSame(f.fault,assertThrows(IllegalStateException.class,()->CdBumper.runPrimary(c,32)));
        var r=c.primaryResult();assertEquals(52,r.score().score());assertEquals(4,r.pool().slots().getFirst().word(CdObjectState.WordField.SWING_MAX));
        assertEquals(1,r.pool().slots().getFirst().unsignedByte(CdObjectState.ByteField.COLLISION_STATUS));
        assertEquals(0x1c,r.pool().slots().get(1).id());assertEquals(0x80,r.pool().slots().get(1).subtype());
        assertThrows(IllegalStateException.class,f.leaked::completedNative);
    }
    @Test void primaryCounterCapAndTripleDespawnBankStayIndependent() {
        for(int count:new int[]{0,0x89,0x8a,0xff}){
            var f=new TestCdPinballFamily.Fixture();var i=f.object(0x1d,2,0);i.b(0x21,1);i.b(0x23,5);f.set(i);f.saved[7]=(byte)count;f.saved[18]=(byte)255;f.despawn=true;
            var r=CdBumper.runPrimary(primary(f),32);assertEquals(count<0x8a?count+1:count,r.savedFlags().unsignedByte(7));assertEquals(127,r.savedFlags().unsignedByte(18));
        }
    }
}
