package com.openggf.game.sonic3k.objects;

import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ROM arithmetic and collision contract for {@code Obj_LRZ3Platform}. */
class TestLrz3Platform {
    private TestObjectServices services;

    @BeforeEach void setUp() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        services = new TestObjectServices().withIsolatedObjectManager();
    }

    private Lrz3PlatformObjectInstance platform(int subtype) {
        var result = new Lrz3PlatformObjectInstance(
                new ObjectSpawn(0x100, 0x200, 0xAD, subtype, 0, false, 0));
        result.setServices(services);
        return result;
    }

    @Test void subtypeFourIsTheWideStaticFullSolid() {
        var platform = platform(4);
        assertEquals(0x2B, platform.getOnScreenHalfWidth());
        assertEquals(0x19, platform.getOnScreenHalfHeight());
        assertEquals(0x23, platform.getSolidParams().halfWidth());
        int y = platform.getY();
        for (int i=0;i<100;i++) platform.update(i,null);
        assertEquals(y, platform.getY());
    }

    @Test void internalMovingPlatformRisesForTwentyOneTicksThenDescends() {
        var platform = platform(3);
        assertEquals(-0x80, platform.yVelocityForTest());
        for (int i=0;i<34;i++) platform.update(i,null);
        assertEquals(0x80, platform.yVelocityForTest());
        assertTrue(platform.getY() < 0x200);
        assertTrue(platform.timerForTest() > 0x400);
    }

    @Test void everyPlacedSubtypeHasAProductionClass() {
        for (int subtype : new int[]{0,1,2,4}) {
            assertEquals(subtype, platform(subtype).subtypeForTest());
        }
    }
}
