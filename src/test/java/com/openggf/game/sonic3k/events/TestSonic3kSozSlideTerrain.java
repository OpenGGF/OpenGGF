package com.openggf.game.sonic3k.events;

import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kSozSlideTerrain {
    private byte[] table(int address, int size) throws Exception {
        return TestEnvironment.currentRom().readBytes(address, size);
    }
    private boolean apply(AbstractPlayableSprite p, int block) throws Exception {
        return Sonic3kSOZEvents.applySlideTerrain(p, block, table(0x74AC,17), table(0x74BD,34));
    }
    @Test void nativeRomTablesAndEveryChunkBranch() throws Exception {
        int[] ids={0xF,0x13,0x14,0x15,0x16,0x17,0x35,0x6C,0x6D,0x76,0x77,0x7E,0x7F,0x85,0x8A,0x8C,0x90};
        int[] targets={-8,-8,-6,-6,-8,-8,-8,6,0,6,8,8,8,8,8,0,-8};
        int[] modes={0,0,1,2,0,0,0,1,3,2,0,0,0,0,0,3,0};
        byte[] blocks=table(0x74AC,17), pairs=table(0x74BD,34);
        for(int i=0;i<ids.length;i++) {
            assertEquals(ids[i],blocks[i]&255);assertEquals(targets[i],pairs[i*2]);assertEquals(modes[i],pairs[i*2+1]);
            var p=testPlayer();p.setAir(false);p.setTopSolidBit((byte)0xC);
            com.openggf.sprites.NativePositionOps.addXPosPreserveSubpixel(p,modes[i]==2?64:0);
            assertTrue(apply(p,ids[i]));assertTrue(p.isSliding());assertEquals(0x19,p.getAnimationId());
            assertEquals(targets[i]>0?64:-64,p.getGSpeed());
        }
    }
    @Test void halfChunkBoundaryAndBothPaths() throws Exception {
        for(int block:new int[]{0x14,0x15,0x6C,0x76})for(int x:new int[]{63,64}) {
            var p=testPlayer();p.setAir(false);com.openggf.sprites.NativePositionOps.addXPosPreserveSubpixel(p,x);
            boolean left=block==0x14||block==0x6C;
            assertEquals(left?x<64:x>=64,apply(p,block));
        }
        for(int path:new int[]{0xC,0xD}) {
            var p=testPlayer();p.setAir(false);p.setTopSolidBit((byte)path);apply(p,0x6D);
            assertEquals(path==0xC?-64:64,p.getGSpeed());
        }
    }
    @Test void entryOppositionDampingAndExistingSlide() throws Exception {
        for(int block:new int[]{0x14,0xF,0x6C,0x77})for(boolean sliding:new boolean[]{false,true}) {
            boolean negative=block==0x14||block==0xF;var p=testPlayer();p.setAir(false);p.setSliding(sliding);
            p.setGSpeed((short)(negative?0x400:-0x400));apply(p,block);
            int moved=negative?0x3C0:-0x3C0;
            assertEquals(sliding?moved:(block==0xF||block==0x77?0:moved>>1),p.getGSpeed());
            assertEquals(negative?Direction.RIGHT:Direction.LEFT,p.getDirection());
        }
    }
    @Test void smallRadiiEntryAndGroundVersusAirExit() throws Exception {
        for(boolean air:new boolean[]{false,true}) {
            var p=testPlayer();p.setAir(false);p.applyCustomRadii(9,19);p.setDirection(Direction.LEFT);
            int y=p.getCentreY();apply(p,0x77);
            assertEquals(y+5,p.getCentreY());assertEquals(7,p.getXRadius());assertEquals(14,p.getYRadius());
            assertEquals(Direction.LEFT,p.getDirection());assertFalse(p.getRolling());
            p.setAir(air);assertFalse(apply(p,0));assertFalse(p.isSliding());assertEquals(5,p.getMoveLockTimer());
            assertEquals(air?0x19:0,p.getAnimationId());assertEquals(y+5,p.getCentreY());
            if(air)assertEquals(14,p.getYRadius());
        }
    }
    @Test void standingOnObjectRejectsMatchingTerrainAndRestoresDefaultRadii() throws Exception {
        var p=testPlayer();p.setAir(false);apply(p,0x77);p.setOnObject(true);
        assertFalse(apply(p,0x77));assertFalse(p.isSliding());assertEquals(5,p.getMoveLockTimer());
        assertEquals(p.getStandXRadius(),p.getXRadius());assertEquals(p.getStandYRadius(),p.getYRadius());
    }
    @Test void signedHighByteThresholdDoesNotAccelerateFurther() throws Exception {
        for(int block:new int[]{0xF,0x77}) {
            var p=testPlayer();p.setAir(false);p.setGSpeed((short)(block==0xF?-0x810:0x810));
            int old=p.getGSpeed();apply(p,block);assertEquals(old,p.getGSpeed());
        }
    }
    private static AbstractPlayableSprite testPlayer() {
        return new AbstractPlayableSprite("sonic", (short) 0, (short) 0) {
            @Override
            protected void defineSpeeds() {
            }

            @Override
            protected void createSensorLines() {
            }

            @Override
            public void draw() {
            }

            @Override
            public SecondaryAbility getSecondaryAbility() {
                return SecondaryAbility.INSTA_SHIELD;
            }
        };
    }
}
