package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.DrowningController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** S1_WATER: local surface crossing and last-air boundary, seeded before the crossing. */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1WaterVerdicts {
    static SharedLevel level;
    @BeforeAll static void load() throws Exception { level=SharedLevel.load(SonicGame.SONIC_1,3,0); }
    @AfterAll static void close(){ if(level!=null)level.dispose(); }
    @ParameterizedTest @ValueSource(ints={2,6})
    void lastAirSurfaceCrossingEscapesWhileLaterCrossingDrowns(int depth) throws Exception {
        var f=HeadlessTestFixture.builder().withSharedLevel(level).startPosition((short)96,(short)512).startPositionIsCentre().build();
        var s=f.sprite();
        f.stepIdleFrames(2);
        int water=GameServices.water().getGameplayWaterLevelY(GameServices.level().getFeatureZoneId(),0);
        assertTrue(water>100,"Loaded native LZ1 water surface");
        s.setCentreY((short)(water+depth)); s.setAir(true); s.setInWater(true);
        s.setYSpeed((short)-0x380); s.setXSpeed((short)0);
        s.getDrowningController().restoreRewindState(new DrowningController.RewindState(0,1,false,0,-1,0,0,1));
        // S1's fixed Obj0A, not the shared timer, owns the next native air decrement.
        var countdown=field(GameServices.module().getLevelEventProvider(),"fixedAirCountdownManager");
        var controller=field(countdown,"p1");
        assertEquals(true,field(controller,"installed"));
        assertEquals(10,field(controller,"routine"));
        var timer=controller.getClass().getDeclaredField("drownTime");timer.setAccessible(true);timer.setInt(controller,0);
        assertTrue(s.getCentreY()>water); assertFalse(s.isDrowningDeath());
        assertEquals(0,s.getDrowningController().getRemainingAir());
        f.stepFrame(false,false,false,false,false);
        if(depth==2){
            assertFalse(s.isInWater()); assertFalse(s.isDrowningDeath());
            assertEquals(-0x6e0,s.getYSpeed());
            assertEquals(30,s.getDrowningController().getRemainingAir());
        } else { assertTrue(s.isInWater()); assertTrue(s.isDrowningDeath()); }
    }
    private static Object field(Object owner,String name) throws Exception {
        var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
}
