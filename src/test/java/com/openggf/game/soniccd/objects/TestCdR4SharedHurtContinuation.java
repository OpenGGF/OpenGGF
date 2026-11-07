package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.CdR4Boss.*;
import static com.openggf.game.soniccd.player.CdPlayerTouchResponse.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed original component ingress using the ACTUAL one shared Hurt algorithm. No fake
 * World/Frame/campaign/private runtime admission. Root still owns those real production joins. */
class TestCdR4SharedHurtContinuation {
    static HurtPair pair(int flags,int hurt,int warp,int invincible,int shield,int rings,int cheat,int debug){
        var m=new CdPlayerMotionState(0x2001234,0x3005678,0x111,0x222,0x333,2,0x55,flags,0xA5,7,14,5,6,0xCC,0xAB,0x1234,0x81,0xA1,0x91,0x92);
        return new HurtPair(new Actor(m,0x1234,6,hurt,0x79),new PlayerGlobals(warp,invincible,shield,0xA7,cheat,debug,0xBEEF,0),new WorldGlobals(rings,7,8,0x12345678,0x23456789,9));
    }
    static final class H implements Ports {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(HexFormat.of().parseHex("000c0014ffff000000000000ffff000000000000ffff00000000")));
        final CdObjectPool pool=placement.pool();final CdR4Boss body;
        HurtPair pair=pair(4,0,0,0,0,5,0,0);int started=4,publications;
        List<HurtPublication> pubs=new ArrayList<>();List<CdPlayerTouchResponse.FmSound> sounds=new ArrayList<>();
        List<String> order=new ArrayList<>();Consumer<HurtPublication> prepareHook,soundHook,publishHook;
        PreparedHurtPair override;
        Throwable pairReadFailure;
        H(){body=new CdR4Boss(placement,new Assets(){public int mapping(Part p){throw new AssertionError("No art ingress");}
            public CdObjectAnimation.AnimationTable animation(Part p){throw new AssertionError("No animation ingress");}
            public CdAssetBytes table(Table p){throw new AssertionError("No table ingress");}},this);
            pool.write(pool.state(70).moveByte(ID,0x4E).moveByte(COLLISION_TYPE,0xFE).moveByte(COLLISION_STATUS,41).moveWord(X,0x210));}
        public int read(CdR4Boss.Call c,Field f){assertTrue(body.isCurrentCall(c));assertEquals(Field.BOSS_STARTED,f);c.claim();return started;}
        public HurtPair hurtPair(CdR4Boss.Call c){assertTrue(body.isCurrentHurtCall(c));
            if(pairReadFailure instanceof RuntimeException failure)throw failure;
            if(pairReadFailure instanceof Error failure)throw failure;
            return pair;}
        public PreparedHurtPair prepareHurtPair(HurtPublication p){
            assertTrue(body.isCurrentHurtPublication(p));assertEquals(pair,p.before());assertEquals(pool.capture().slots(),p.actualCut().pool().slots());
            assertThrows(IllegalStateException.class,()->body.special(70));pubs.add(p);if(prepareHook!=null)prepareHook.accept(p);
            if(override!=null)return override;
            var before=p.before();var after=p.reachedPrefix().pair();
            return new PreparedHurtPair(){public HurtPublication original(){return p;}public HurtPair before(){return before;}public HurtPair after(){return after;}
                public void publish(){assertTrue(body.isCurrentHurtPublication(p));publications++;pair=after;order.add("pair:"+p.reachedPrefix().exit());if(publishHook!=null)publishHook.accept(p);}};
        }
        public void hurtSound(HurtPublication p,CdPlayerTouchResponse.FmSound sound){assertTrue(body.isCurrentHurtPublication(p));assertTrue(p.adopted());assertEquals(pair,p.reachedPrefix().pair());sounds.add(sound);order.add("sound:"+sound);if(soundHook!=null)soundHook.accept(p);}
        void full(){for(int n=32;n<128;n++)if(n!=70)pool.write(pool.state(n).moveByte(ID,1));}
    }
    static void others(H h,CdObjectPlacementRuntime.Snapshot before,int changed){
        for(int n=32;n<128;n++)if(n!=changed)assertArrayEquals(before.pool().slots().get(n-32).copyBytes(),h.pool.state(n).copyBytes(),"whole"+n);
        assertEquals(before.status(),h.placement.status());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
    }
    @Test void ringedHurtTailUsesActualNewSlotAndPreservesDirtyNonwrites(){for(int boss:new int[]{4,5}){
        var h=new H();h.started=boss;h.pool.write(h.pool.state(70).moveByte(COLLISION_TYPE,boss==4?0xFE:0xFD));
        var dirty=h.pool.state(32).moveByte(COLLISION_STATUS,255).moveByte(ROUTINE,0xA4).moveWord(X_FRACTION,0xBEEF).moveWord(Y_FRACTION,0xCAFE);h.pool.write(dirty);var before=h.placement.capture();
        h.body.special(70);byte[] expected=dirty.copyBytes();expected[0]=0x11;expected[8]=2;expected[9]=0;expected[12]=3;expected[13]=0;expected[0x21]=0;
        assertArrayEquals(expected,h.pool.state(32).copyBytes());others(h,before,32);assertEquals(2,h.publications);assertEquals(List.of(FmSound.FM_CHARGESTOP),h.sounds);
        assertEquals(-0x200,h.pair.actor().motion().xVelocity());assertEquals(120,h.pair.actor().hurt());assertEquals(0,h.pair.player().scoreChain());assertEquals(5,h.pair.world().rings());
        assertEquals(1,h.pubs.getLast().reachedPrefix().calls().stream().filter(FindObjSlot.class::isInstance).count());
        assertFalse(h.body.isCurrentHurtPublication(h.pubs.getFirst()));
    }}
    @Test void fullPoolRetainsHurtAndSoundBeforeFirstUnownedTailWrite(){var h=new H();h.full();var before=h.placement.capture();
        var fault=assertThrows(UnownedHurtTail.class,()->h.body.special(70));assertEquals(128,fault.wholeIndex());
        assertEquals(120,h.pair.actor().hurt());assertEquals(List.of(FmSound.FM_CHARGESTOP),h.sounds);assertEquals(before.pool().slots(),h.pool.capture().slots());assertEquals(41,h.pool.state(70).unsignedByte(COLLISION_STATUS));
    }
    @Test void guardedHurtStillIncrementsSelectedSourceWithoutPpzTail(){for(int mode=0;mode<3;mode++){
        var h=new H();h.pair=pair(4,mode==2?0x100:0,mode==0?0x80:0,mode==1?255:0,0,5,0,0);var beforePair=h.pair;var before=h.placement.capture();
        h.body.special(70);assertEquals(beforePair,h.pair);assertEquals(42,h.pool.state(70).unsignedByte(COLLISION_STATUS));assertEquals(0xFE,h.pool.state(70).unsignedByte(COLLISION_TYPE));assertEquals(0,h.pool.state(32).id());assertTrue(h.sounds.isEmpty());others(h,before,70);
    }}
    @Test void shieldNoRingCheatAndKillUseActualSharedBranches(){for(int mode=0;mode<4;mode++){
        var h=new H();h.pair=pair(0,0,0,0,mode==0?1:0,0,mode==1?0x100:0,mode==2?0x100:0);
        h.body.special(70);assertEquals(42,h.pool.state(70).unsignedByte(COLLISION_STATUS));assertEquals(0,h.pool.state(32).id());
        assertEquals(mode==2?2:mode==3?6:4,h.pair.actor().motion().routine());
        assertEquals(mode==3?List.of(FmSound.FM_HURT):List.of(),h.sounds);
    }}
    @Test void soundFailureRetainsOnlyReachedGroundPrefixAndOriginalFault(){var h=new H();RuntimeException fault=new RuntimeException("actual FM owner");
        h.soundHook=p->{throw fault;};assertSame(fault,assertThrows(RuntimeException.class,()->h.body.special(70)));
        assertEquals(0x11,h.pool.state(32).id());assertEquals(0xCC,h.pair.actor().motion().playerJump());assertEquals(0xBEEF,h.pair.player().scoreChain());assertEquals(4,h.pair.actor().motion().routine());assertEquals(0x111,h.pair.actor().motion().xVelocity());assertEquals(0x2FB5678,h.pair.actor().motion().centreY16_16());assertEquals(0,h.pool.state(32).unsignedByte(COLLISION_STATUS));assertEquals(41,h.pool.state(70).unsignedByte(COLLISION_STATUS));assertEquals(0,fault.getSuppressed().length);
    }
    @Test void wrongPreparedWholePairRejectsBeforeAnyOwnerPublication(){var h=new H();var before=h.placement.capture();var pair=h.pair;
        h.prepareHook=p->{h.override=new PreparedHurtPair(){public HurtPublication original(){return p;}public HurtPair before(){return pair;}public HurtPair after(){return pair;}public void publish(){fail();}};};
        assertThrows(IllegalStateException.class,()->h.body.special(70));assertEquals(0,h.publications);assertEquals(before.pool().slots(),h.pool.capture().slots());assertEquals(pair,h.pair);
    }
    @Test void stalePreparedOperationIdentityCannotPublishEvenEqualValues(){var h=new H();h.pair=pair(0,1,0,0,0,5,0,0);h.body.special(70);var old=h.pubs.getLast();var current=h.pair;
        h.override=new PreparedHurtPair(){public HurtPublication original(){return old;}public HurtPair before(){return current;}public HurtPair after(){return current;}public void publish(){fail();}};
        int count=h.publications;assertThrows(IllegalStateException.class,()->h.body.special(70));assertEquals(count,h.publications);assertEquals(42,h.pool.state(70).unsignedByte(COLLISION_STATUS));
    }
    @Test void sourceAndSavedNonwritesAreFencedDuringPreparation(){for(int mode=0;mode<2;mode++){
        var h=new H();final int selected=mode;
        h.prepareHook=p->{if(selected==0)h.pool.write(h.pool.state(99).moveByte(ID,9));else h.placement.bsetSavedFlag(1,1,CdObjectPlacementRuntime.SavedBit.MEMBER_1);};
        assertThrows(IllegalStateException.class,()->h.body.special(70));assertEquals(0,h.publications);assertEquals(0,h.pool.state(32).id());
    }}
    @Test void fullPairMutationAtSoundIsDetectedWithoutReplacingOriginalFault(){var h=new H();var fault=new RuntimeException("FM failure");
        h.soundHook=p->{var a=h.pair.actor();h.pair=new HurtPair(new Actor(a.motion(),a.tile()^1,a.priority(),a.hurt(),a.rotationDistance()),h.pair.player(),h.pair.world());throw fault;};
        assertSame(fault,assertThrows(RuntimeException.class,()->h.body.special(70)));assertTrue(fault.getSuppressed().length>=1);assertEquals(0x11,h.pool.state(32).id());assertEquals(41,h.pool.state(70).unsignedByte(COLLISION_STATUS));
    }
    @Test void nonSpecialIngressRejectsBeforeBorrowingAnyPair(){var h=new H();h.pool.write(h.pool.state(70).moveByte(COLLISION_TYPE,0x3E));assertThrows(IllegalArgumentException.class,()->h.body.special(70));assertEquals(0,h.publications);assertTrue(h.pubs.isEmpty());}
    private static void throwOriginal(Throwable failure) {
        if(failure instanceof RuntimeException runtime)throw runtime;
        throw (Error)failure;
    }
    @Test void sameFaultFromSoundAndPairFenceKeepsOriginalIdentityAndRetiresTheCall() {
        for(Throwable failure:new Throwable[]{new RuntimeException("FM owner"),new AssertionError("FM owner")}) {
            var h=new H();
            var dirty=h.pool.state(32).moveByte(COLLISION_STATUS,0xF3).moveByte(ROUTINE,0xA4)
                .moveWord(X_FRACTION,0xBEEF).moveWord(Y_FRACTION,0xCAFE);
            h.pool.write(dirty);var before=h.placement.capture();
            h.soundHook=p->{h.pairReadFailure=failure;throwOriginal(failure);};
            assertSame(failure,assertThrows(failure.getClass(),()->h.body.special(70)));
            byte[] expected=dirty.copyBytes();expected[0]=0x11;expected[8]=2;expected[9]=0;expected[12]=3;expected[13]=0;
            assertArrayEquals(expected,h.pool.state(32).copyBytes());others(h,before,32);
            assertEquals(1,h.publications);assertEquals(List.of(FmSound.FM_CHARGESTOP),h.sounds);
            assertEquals(0xCC,h.pair.actor().motion().playerJump());assertEquals(0xBEEF,h.pair.player().scoreChain());
            assertEquals(4,h.pair.actor().motion().routine());assertEquals(0x111,h.pair.actor().motion().xVelocity());
            assertEquals(0x2FB5678,h.pair.actor().motion().centreY16_16());
            assertEquals(0,failure.getSuppressed().length);
            assertFalse(h.body.isCurrentHurtPublication(h.pubs.getFirst()));
            assertFalse(h.body.isCurrentHurtCall(h.pubs.getFirst().originalCall()));
        }
    }
    @Test void distinctFaultFromPairFenceStaysSuppressedOnTheOriginalSoundFault() {
        for(Throwable failure:new Throwable[]{new RuntimeException("FM owner"),new AssertionError("FM owner")}) {
            var h=new H();var secondary=new IllegalStateException("pair owner");
            h.soundHook=p->{h.pairReadFailure=secondary;throwOriginal(failure);};
            assertSame(failure,assertThrows(failure.getClass(),()->h.body.special(70)));
            assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(x->x==secondary));
            assertEquals(1,h.publications);assertEquals(List.of(FmSound.FM_CHARGESTOP),h.sounds);
            assertEquals(0xCC,h.pair.actor().motion().playerJump());assertEquals(0xBEEF,h.pair.player().scoreChain());
            assertEquals(0x11,h.pool.state(32).id());assertEquals(0,h.pool.state(32).unsignedByte(COLLISION_STATUS));
            assertEquals(41,h.pool.state(70).unsignedByte(COLLISION_STATUS));
            assertFalse(h.body.isCurrentHurtPublication(h.pubs.getFirst()));
        }
    }

}
