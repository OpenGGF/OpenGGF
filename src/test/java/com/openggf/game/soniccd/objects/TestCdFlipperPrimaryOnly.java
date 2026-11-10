package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** UNRUN constructed component vectors. Fake completion sinks prove no live owner or route. */
class TestCdFlipperPrimaryOnly {
    private static CdPinballContext primary(TestCdPinballFamily.Fixture f){
        return CdPinballContext.forPrimary(new CdPinballContext.PrimaryInput(f.pool.capture(),f.actor(),f.environment(),
            new CdPlayerTouchResponse.WorldGlobals(17,3,0,f.score,f.nextLife,0),CdAssetBytes.copyOf(f.saved),
            f.period,f.good,f.debug,f.act),TestCdPinballFamily.assets(),TestCdPinballFamily.math(),f);
    }
    private static void setup(TestCdPinballFamily.Fixture f,int routine,int animation,boolean mirrored,boolean bottom,int x){
        var i=f.object(0x1e,routine,0x80);i.b(0x19,32);i.b(0x1c,animation);i.b(0x22,mirrored?1:0);i.b(1,0x84);
        i.b(0x17,0xa7);i.w(10,0xcafe);i.w(14,0xbeef);i.b(0x21,0xd7);i.w(0x34,0xabcd);f.set(i);
        f.x=x;f.yv=bottom?-1:0;f.y=bottom?119:81;
    }
    @Test void primaryOnlyMatchesEveryLegacyBranchWithoutSecondaryPackaging(){
        for(int routine:new int[]{0,2})for(int animation:new int[]{0,1})for(boolean mirror:new boolean[]{false,true})
        for(boolean bottom:new boolean[]{false,true})for(int x:new int[]{67,68,100,131,132}){
            var f=new TestCdPinballFamily.Fixture();var g=new TestCdPinballFamily.Fixture();
            setup(f,routine,animation,mirror,bottom,x);setup(g,routine,animation,mirror,bottom,x);
            var old=CdFlipper.run(f.context(),32);var c=primary(g);var actual=CdFlipper.runPrimary(c,32);
            for(int slot=0;slot<96;slot++){
                assertEquals(old.pool().slots().get(slot).identity(),actual.pool().slots().get(slot).identity());
                assertArrayEquals(old.pool().slots().get(slot).copyBytes(),actual.pool().slots().get(slot).copyBytes());
            }
            assertEquals(old.players().primary(),actual.primary());
            assertEquals(old.players().primaryEnvironment(),actual.environment());assertEquals(old.score(),actual.score());
            assertArrayEquals(old.savedFlags().copy(),actual.savedFlags().copy());assertEquals(old.completed(),actual.completed());
            assertEquals(f.attachment,g.attachment);assertEquals(f.calls,g.calls);assertThrows(IllegalStateException.class,c::result);
        }
    }
    @Test void initRetainsEveryDirtyNonwriteAndEveryPhysicalNeighbour(){
        var f=new TestCdPinballFamily.Fixture();setup(f,0,1,false,false,100);f.full();var c=primary(f);var before=c.primaryResult();
        var r=CdFlipper.runPrimary(c,32);var bytes=before.pool().slots().getFirst().copyBytes();
        bytes[0x24]=2;bytes[4]=0;bytes[5]=0x21;bytes[6]=0x23;bytes[7]=0;bytes[2]=4;bytes[3]=(byte)0x9d;
        bytes[1]=4;bytes[0x18]=1;bytes[0x19]=32;bytes[0x16]=16;
        // Shared animator first switches dirty previous ID8 to1, resets cursor and reads frame0.
        bytes[0x1d]=1;bytes[0x1b]=1;bytes[0x1e]=3;bytes[0x1a]=0;
        assertArrayEquals(bytes,r.pool().slots().getFirst().copyBytes());assertEquals(before.pool().slots().subList(1,96),r.pool().slots().subList(1,96));
        assertSame(before.primary(),r.primary());assertSame(before.environment(),r.environment());assertTrue(f.calls.stream().noneMatch(v->v.startsWith("sound:")));
    }
    @Test void primaryLegacyEntryRejectsBeforeWritesAndStillAllowsPrimaryEntry(){
        var f=new TestCdPinballFamily.Fixture();setup(f,0,1,false,false,100);var c=primary(f);var before=c.primaryResult();
        assertThrows(IllegalStateException.class,()->CdFlipper.run(c,32));assertEquals(before.pool().slots(),c.primaryResult().pool().slots());
        CdFlipper.runPrimary(c,32);assertThrows(IllegalStateException.class,()->CdFlipper.runPrimary(c,32));
    }
    @Test void standFailureRetainsEarlierYWordAndOriginalThrowableAndRetiresOperation(){
        var wanted=new AssertionError("original Stand owner");var leak=new AtomicReference<CdPinballContext.Attachment>();
        var f=new TestCdPinballFamily.Fixture(){@Override public void attachment(CdPinballContext.Attachment o){leak.set(o);throw wanted;}};
        setup(f,2,0,true,false,68);f.y=99;var c=primary(f);assertSame(wanted,assertThrows(AssertionError.class,()->CdFlipper.runPrimary(c,32)));
        assertEquals(99,c.primaryResult().primary().motion().centreY16_16()>>>16);assertEquals(0xabcd,c.primaryResult().primary().motion().centreY16_16()&65535);
        assertEquals(List.of("PLAYER_STORE"),c.primaryResult().completed());assertThrows(IllegalStateException.class,leak.get()::player);
    }
    @Test void getOffFailureHasNoEarlierYStoreAndNoDraw(){
        var wanted=new IllegalStateException("original GetOff owner");var f=new TestCdPinballFamily.Fixture(){@Override public void attachment(CdPinballContext.Attachment o){throw wanted;}};
        setup(f,2,0,false,false,132);var c=primary(f);var p=c.primaryResult().primary();
        assertSame(wanted,assertThrows(IllegalStateException.class,()->CdFlipper.runPrimary(c,32)));assertSame(p,c.primaryResult().primary());assertTrue(c.primaryResult().completed().isEmpty());assertTrue(f.calls.isEmpty());
    }
    @Test void mirroredBottomColumn64UsesFollowingByteAndOrdersYBeforeVelocities(){
        var values=new ArrayList<CdPlayerBody.State>();var f=new TestCdPinballFamily.Fixture(){@Override public void playerStore(CdPinballContext.PlayerStore o){values.add(o.output());o.applyNative();}};
        setup(f,2,0,true,true,68);f.y=122;var r=CdFlipper.runPrimary(primary(f),32);assertEquals(2,values.size());
        assertEquals(-1,values.getFirst().motion().yVelocity());assertEquals(122,values.getFirst().motion().centreY16_16()>>>16);
        assertEquals(0,r.primary().motion().yVelocity());assertEquals(123,r.primary().motion().groundVelocity());assertNull(f.attachment);
    }
    @Test void originalDrawThrowableRetainsClearAndAnimationAndStopsBeforeDespawn(){
        var wanted=new AssertionError("draw");var f=new TestCdPinballFamily.Fixture(){@Override public void draw(CdPinballContext.Draw o){throw wanted;}};
        setup(f,2,1,false,false,100);var c=primary(f);assertSame(wanted,assertThrows(AssertionError.class,()->CdFlipper.runPrimary(c,32)));
        assertEquals(0,c.primaryResult().pool().slots().getFirst().spriteFlags()&128);assertTrue(c.primaryResult().completed().contains("ANIMATE"));assertTrue(f.calls.isEmpty());
        assertEquals(0,wanted.getSuppressed().length);
    }
    @Test void savedPeriodClearFollowsDrawAndDeletesOnlySelectedSlot(){
        var f=new TestCdPinballFamily.Fixture();setup(f,2,1,false,false,100);var i=new CdPinballImage(f.pool.state(32));i.b(0x23,5);f.set(i);f.saved[18]=(byte)255;f.despawn=true;
        var r=CdFlipper.runPrimary(primary(f),32);assertEquals(List.of("draw","despawn:100"),f.calls);assertEquals(127,r.savedFlags().unsignedByte(18));assertEquals(0,r.pool().slots().getFirst().id());
    }
}
