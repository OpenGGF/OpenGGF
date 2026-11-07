package com.openggf.level.rings;

import com.openggf.game.GameModule;
import com.openggf.game.session.WorldSession;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.NativeCompetitionRuntimeFixture;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2CompetitionLostRingCadence {
    @Test void deleteNeedsNativeRosterFallingCadenceAndOffscreenFlag() throws Exception {
        TestEnvironment.resetAll();
        var world = new NativeCompetitionRuntimeFixture();
        for (boolean nativeMode : new boolean[]{false,true}) {
            for (boolean falling : new boolean[]{false,true}) {
                for (boolean cadence : new boolean[]{false,true}) {
                    for (boolean visible : new boolean[]{false,true}) {
                        var ring = new LostRingObjectInstance(new ObjectSpawn(256,256,0x37,0,0,false,0)) {
                            @Override protected int resolveVblaCounter() { return cadence ? 0 : 1; }
                            @Override protected boolean hasRomRenderFlagForFloorProbe() { return visible; }
                            @Override protected int ringCheckFloorDist(int x, int y) { return 0; }
                        };
                        ring.setServices(new StubObjectServices() {
                            @Override public WorldSession worldSession() { return nativeMode ? world.world : null; }
                            @Override public GameModule gameModule() { return world.world.getGameModule(); }
                        });
                        var velocity = LostRingObjectInstance.class.getDeclaredField("yVel");
                        velocity.setAccessible(true); velocity.setInt(ring, falling ? 0 : -0x100);
                        ring.stepPhysicsForTest(0x18,true);
                        assertEquals(nativeMode && falling && cadence && !visible, ring.isDestroyed(),
                                "native="+nativeMode+" falling="+falling+" cadence="+cadence+" visible="+visible);
                        assertEquals(falling ? 0 : -0x100,ring.getYSubpixelForTest(),
                                "position integration precedes the delete");
                        assertEquals((falling ? 0 : -0x100)+0x18, ring.getYVelForTest(),
                                "movement/gravity precedes even immediate cadence deletion");
                    }
                }
            }
        }
    }
}
