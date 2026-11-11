package com.openggf.game.sonic2.competition;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Comparison-only native observation: REV01 EHZ route, slot30, frames5823..5842. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionLostRingClock {
    @Test void equalClockRetainsNativeFirstBounceBoundaryAndWrongOriginDiscriminatesIt() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            class Ring extends LostRingObjectInstance {
                int clock;
                Ring() {
                    super(new ObjectSpawn(8543,912,0x37,0,0,false,0));
                    initFixedPointForTest(8543,912,-848,568,97,255);
                    setServices(w.host.context().getObjectManager().getObjectServices());
                }
                @Override protected int resolveVblaCounter() { return clock; }
                @Override protected boolean hasRomRenderFlagForFloorProbe() { return true; }
            }
            var nativeClock = new Ring(); var wrongOrigin = new Ring();
            for (int frame=5823; frame<=5842; frame++) {
                nativeClock.clock=frame-236;
                wrongOrigin.clock=frame-236+137;
                nativeClock.stepPhysicsForTest(0x18,true);
                wrongOrigin.stepPhysicsForTest(0x18,true);
                if(frame<=5841) {
                    assertEquals(nativeClock.getXSubpixelForTest(),wrongOrigin.getXSubpixelForTest());
                    assertEquals(nativeClock.getYSubpixelForTest(),wrongOrigin.getYSubpixelForTest());
                    assertEquals(nativeClock.getYVelForTest(),wrongOrigin.getYVelForTest());
                }
            }
            assertEquals(8476,nativeClock.getX());
            assertEquals(974,nativeClock.getY());
            assertEquals(1048,nativeClock.getYVelForTest());
            assertEquals(951,wrongOrigin.getY());
            assertEquals(-786,wrongOrigin.getYVelForTest());
        });
    }
}
