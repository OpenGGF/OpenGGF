package com.openggf.tests;

import com.openggf.tests.reel.S1ReelFrames;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Representative seeded S1 native-terrain scenarios; no trace state is imported. */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1TerrainVerdicts {
    @Test void currentLimitationHorizontalUnderflowClampsLeftInsteadOfNativeRight() throws Exception {
        try (var scope = new Scope(0, 0, 16, 700)) {
            var f = scope.f; var s = f.sprite();
            s.setAir(true); s.setXSpeed((short)-0x2000); s.setYSpeed((short)0);
            int expected = f.camera().getMaxX() + 320 - 24 + 64;
            assertEquals(16, s.getCentreX());
            assertEquals(0xfff0, (s.getCentreX() + (s.getXSpeed() >> 8)) & 0xffff);
            S1ReelFrames.step(f,false,false,false,false,false);
            assertEquals(16, s.getCentreX(), "Confirmed limitation: signed compare clamps left instead of native unsigned right");
            assertNotEquals(expected, s.getCentreX());
            assertEquals(0, s.getXSpeed());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={3,4})
    void cogJumpTimingSelectsForwardClipOrWallStop(int jumpFrame) throws Exception {
        // Valid high-speed grounded entry, not an already embedded position.
        // Independent native REV01 runs, S1NativeProbe.cs, jump=3 and jump=4.
        try (var scope = new Scope(5, 1, 0x2b0, 0x710)) {
            var f = scope.f; var s = f.sprite();
            s.setGSpeed((short)0xf00); s.setXSpeed((short)0xf00);
            for(int i=0;i<=8;i++) {
                S1ReelFrames.step(f,false,false,false,false,i>=jumpFrame);
                if(i==2) { assertEquals(0x2d9,s.getCentreX()); assertEquals(0x718,s.getCentreY()); assertTrue(s.isStickToConvex()); assertFalse(s.getAir()); }
                if(i==jumpFrame) { assertTrue(s.getAir()); assertTrue(s.getXSpeed()>0x1000); assertFalse(s.isStickToConvex()); }
                if(i==7 && jumpFrame==3) { assertEquals(0x320,s.getCentreX()); assertFalse(s.getAir()); assertTrue(s.isStickToConvex()); }
            }
            if(jumpFrame==3) {
                assertEquals(0x33c,s.getCentreX(),"The next cog corrects forward by 28 pixels on the next tick");
                assertEquals(0x715,s.getCentreY()); assertEquals(0xc0,s.getAngle()&255);
                assertEquals(1780,s.getXSpeed()); assertEquals(-3339,s.getYSpeed());
            } else {
                assertEquals(0x315,s.getCentreX(),"One-frame-later launch stops at the next cog wall");
                assertEquals(0,s.getXSpeed()); assertTrue(s.getAir());
            }
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void movingPlatformWallEntryProducesInputDependentReverseLaunch(boolean left) throws Exception {
        // Native SBZ1 Obj52 at (0x1240,0x308); start above it, outside terrain.
        // Captured native REV01 left/right controls reproduce these exact boundaries.
        try (var scope = new Scope(5, 0, 0x1240, 0x2e4)) {
            var f=scope.f;var s=f.sprite();
            for(int n=0;n<=28;n++){
                S1ReelFrames.step(f,false,n<16,n>=16&&left,n>=16&&!left,false);
                if(n==8){assertTrue(s.isOnObject());assertEquals(0x2ec,s.getCentreY());assertEquals(0,s.getXSpeed());}
                if(n==15)assertEquals(0x1208,s.getCentreX(),"Real moving platform carries Sonic toward the wall");
                if(n==16){assertEquals(0x1203,s.getCentreX());assertEquals(left?756:12,s.getXSpeed());}
                if(n==20){assertEquals(0x1203,s.getCentreX());assertEquals(left?1780:60,s.getXSpeed());assertTrue(s.isOnObject());}
            }
            assertFalse(s.isOnObject());assertTrue(s.getAir());
            assertEquals(left?0x121f:0x120d,s.getCentreX(),"The acquired reverse launch escapes the embedding to the right");
            assertEquals(left?1708:180,s.getXSpeed());
        }
    }
    @Test void nonUnderflowLeftBoundaryUsesTheSameClampInNativeAndEngine() throws Exception {
        try(var scope=new Scope(0,0,40,700)) {
            var s=scope.f.sprite();s.setAir(true);s.setXSpeed((short)-0x2000);
            assertEquals(8,s.getCentreX()+(s.getXSpeed()>>8));
            S1ReelFrames.step(scope.f,false,false,false,false,false);
            assertEquals(16,s.getCentreX());assertEquals(0,s.getXSpeed());
        }
    }
    private static final class Scope implements AutoCloseable {
        final SharedLevel level; final HeadlessTestFixture f;
        Scope(int zone,int act,int x,int y) throws Exception {
            level=SharedLevel.load(SonicGame.SONIC_1,zone,act);
            f=HeadlessTestFixture.builder().withSharedLevel(level).startPosition((short)x,(short)y).startPositionIsCentre().build();
        }
        public void close(){level.dispose();}
    }
}
