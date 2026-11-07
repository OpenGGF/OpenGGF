package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.player.CdPlayerTouchResponse.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal R4 guarded Hurt/PlayerSetGround vectors. Constructed actual-pool/pair ingress;
 * the original shared kernel executes, no World/Frame or native route is fabricated. */
class TestCdGuardedHurtContinuation {
    static HurtPair pair(int flags,int hurt,int warp,int invincible,int shield,int rings,int cheat,int debug) {
        var m=new CdPlayerMotionState(0x2001234,0x3005678,0x111,0x222,0x333,2,0x55,flags,0xA5,7,14,5,6,0xCC,0xAB,0x1234,0x81,0xA1,0x91,0x92);
        return new HurtPair(new Actor(m,0x1234,6,hurt,0x79),new PlayerGlobals(warp,invincible,shield,0xA7,cheat,debug,0xBEEF,0),new WorldGlobals(rings,7,8,0x12345678,0x23456789,9));
    }
    static CdObjectPool pool(){var p=new CdObjectPool();p.write(p.state(70).moveByte(ID,0x4E).moveByte(COLLISION_TYPE,0xFE).moveWord(X,0x210));return p;}
    static List<HurtStep> visit(HurtPair pair,CdObjectPool pool) {
        Object key=new Object();List<HurtStep> steps=new ArrayList<>();var cut=pool.capture();
        var out=visitGuardedHurt(pair,pool.state(70),cut,key,s->{s.claim(key);steps.add(s);});
        assertTrue(out.failure().isEmpty());assertEquals(cut.slots(),pool.capture().slots());return steps;
    }
    @Test void allocationTargetAndDirtyNonwritesSurviveGroundResetAndSound(){
        var pool=pool();var dirty=pool.state(32).moveByte(COLLISION_STATUS,255).moveWord(X_FRACTION,0xBEEF).moveWord(Y_FRACTION,0xCAFE).moveByte(ROUTINE,0xA4);
        pool.write(dirty);var before=pair(0x74,0,0,0,0,5,0,0);var steps=visit(before,pool);
        assertEquals(List.of(HurtCut.SOUND,HurtCut.RETURN),steps.stream().map(HurtStep::cut).toList());
        for(var step:steps){assertEquals(32,step.returnedTargetWholeIndex());assertFalse(step.isCurrent());var image=step.prefix().changedSlots().getFirst();
            byte[] bytes=dirty.copyBytes();bytes[0]=0x11;bytes[8]=2;bytes[9]=0;bytes[12]=3;bytes[13]=0;assertArrayEquals(bytes,image.copyBytes());}
        var sound=steps.getFirst();assertEquals(FmSound.FM_CHARGESTOP,sound.sound().orElseThrow());var partial=sound.prefix().pair();
        assertEquals(0xCC,partial.actor().motion().playerJump());assertEquals(0xBEEF,partial.player().scoreChain());
        assertEquals(0x40,partial.actor().motion().flags());assertEquals(0x2FB5678,partial.actor().motion().centreY16_16());assertEquals(4,partial.actor().motion().routine());
        assertEquals(0,partial.actor().motion().charge());assertEquals(0x111,partial.actor().motion().xVelocity());
        var result=steps.getLast().prefix();assertEquals(Exit.HURT,result.exit().orElseThrow());assertEquals(-0x100,result.pair().actor().motion().xVelocity());
        assertEquals(-0x200,result.pair().actor().motion().yVelocity());assertEquals(0x42,result.pair().actor().motion().flags());assertEquals(120,result.pair().actor().hurt());
        assertEquals(0,result.pair().player().scoreChain());assertEquals(0,result.pair().actor().motion().playerJump());assertEquals(before.world(),result.pair().world());
        assertEquals(1,result.calls().stream().filter(FindObjSlot.class::isInstance).count());
    }
    @Test void warpInvincibilityAndFullHurtWordGuardsKeepSelectedTargetAndWholePair(){
        for(var input:List.of(pair(0x74,0,0x80,0,0,5,0,0),pair(0x74,0,0,255,0,5,0,0),pair(0x74,0x100,0,0,0,5,0,0))){
            var steps=visit(input,pool());assertEquals(1,steps.size());var last=steps.getFirst();assertEquals(70,last.returnedTargetWholeIndex());
            assertEquals(input,last.prefix().pair());assertEquals(Exit.NO_HURT,last.prefix().exit().orElseThrow());assertTrue(last.prefix().calls().isEmpty());assertTrue(last.prefix().changedSlots().isEmpty());}
    }
    @Test void fullAllocationReturnsEndOnlyAfterCompletedHurt(){var p=pool();for(int n=32;n<128;n++)if(n!=70)p.write(p.state(n).moveByte(ID,1));
        var steps=visit(pair(0,0,0,0,0,5,0,0),p);assertEquals(1,steps.size());var last=steps.getLast();assertEquals(128,last.returnedTargetWholeIndex());assertEquals(120,last.prefix().pair().actor().hurt());assertTrue(last.prefix().changedSlots().isEmpty());assertEquals(Exit.HURT,last.prefix().exit().orElseThrow());}
    @Test void shieldAndCheatPathsKeepOriginalTargetAndSkipAllocation(){
        for(var input:List.of(pair(0,0,0,0,1,0,0,0),pair(0,0,0,0,2,5,0,0),pair(0,0,0,0,0,0,0x100,0))){
            var last=visit(input,pool()).getLast();assertEquals(70,last.returnedTargetWholeIndex());assertEquals(Exit.HURT,last.prefix().exit().orElseThrow());assertTrue(last.prefix().changedSlots().isEmpty());
            assertFalse(last.prefix().calls().stream().anyMatch(FindObjSlot.class::isInstance));assertEquals((input.player().shield()&1)!=0?0xA7:0,last.prefix().pair().player().combineRing());}
    }
    @Test void killUsesOneSharedAlgorithmAndExposesSoundPrefixesBeforeReturn(){
        var steps=visit(pair(4,0,0,0,0,0,0,0),pool());assertEquals(3,steps.size());
        assertEquals(FmSound.FM_CHARGESTOP,steps.get(0).sound().orElseThrow());assertEquals(0xCC,steps.get(0).prefix().pair().actor().motion().playerJump());assertEquals(0xBEEF,steps.get(0).prefix().pair().player().scoreChain());
        assertEquals(FmSound.FM_HURT,steps.get(1).sound().orElseThrow());var finalSound=steps.get(1).prefix().pair();assertEquals(6,finalSound.actor().motion().routine());assertEquals(-0x700,finalSound.actor().motion().yVelocity());assertEquals(0x9234,finalSound.actor().tile());assertEquals(0,finalSound.actor().priority());assertEquals(0,finalSound.player().scoreChain());assertEquals(0x2FB5678,finalSound.actor().motion().centreY16_16());
        assertEquals(Exit.KILL_PLAYER,steps.getLast().prefix().exit().orElseThrow());for(var s:steps)assertEquals(70,s.returnedTargetWholeIndex());
        var debug=visit(pair(4,0,0,0,0,0,0,0x100),pool());assertEquals(1,debug.size());assertEquals(Exit.KILL_PLAYER_DEBUG_NO_WRITE,debug.getFirst().prefix().exit().orElseThrow());
    }
    @Test void callbackFaultReturnsItsExactReachedCalculatedPrefixAndExpiresTheStep(){
        var p=pool();Object key=new Object();var original=new RuntimeException("FM owner");List<HurtStep> steps=new ArrayList<>();var before=p.capture();
        var out=visitGuardedHurt(pair(4,0,0,0,0,5,0,0),p.state(70),before,key,s->{s.claim(key);steps.add(s);throw original;});
        assertSame(original,out.failure().orElseThrow());assertEquals(1,steps.size());assertFalse(steps.getFirst().isCurrent());assertThrows(IllegalStateException.class,()->steps.getFirst().claim(key));
        assertEquals(0xCC,out.prefix().pair().actor().motion().playerJump());assertEquals(0xBEEF,out.prefix().pair().player().scoreChain());assertEquals(before.slots(),p.capture().slots());assertTrue(out.prefix().exit().isEmpty());
    }
    @Test void currentStepRequiresOriginalIdentityAndCannotBeClaimedTwice(){var p=pool();Object key=new Object();List<HurtStep> kept=new ArrayList<>();
        var out=visitGuardedHurt(pair(0,1,0,0,0,0,0,0),p.state(70),p.capture(),key,s->{assertTrue(s.isCurrent());assertThrows(IllegalStateException.class,()->s.claim(new Object()));s.claim(key);assertThrows(IllegalStateException.class,()->s.claim(key));kept.add(s);});
        assertTrue(out.failure().isEmpty());assertThrows(IllegalStateException.class,()->kept.getFirst().claim(key));assertThrows(UnsupportedOperationException.class,()->out.prefix().changedSlots().clear());
    }
    @Test void foreignOrStaleSourceRejectsBeforeTheOriginalInvocation(){var p=pool();var cut=p.capture();p.write(p.state(70).moveByte(SUBTYPE,1));
        var pair=pair(0,0,0,0,0,5,0,0);assertThrows(IllegalArgumentException.class,()->visitGuardedHurt(pair,cut.slots().get(38),p.capture(),new Object(),s->fail()));
        assertThrows(IllegalArgumentException.class,()->visitGuardedHurt(pair,p.state(70),new CdObjectPool().capture(),new Object(),s->fail()));}
}
