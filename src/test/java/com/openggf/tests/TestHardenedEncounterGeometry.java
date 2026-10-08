package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Native room prerequisites, independently decoded from the supplied ROM. Not an act certification. */
@RequiresRom(SonicGame.SONIC_3K)
class TestHardenedEncounterGeometry {
    @Test void nativePostShelfHasFloorHeadroomAndUntouchedExitMachinery() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 0)
                .startPosition((short) 0x1d30, (short) 0x1a8).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        var level = assertInstanceOf(Sonic3kLevel.class, GameServices.level().getCurrentLevel());
        assertEquals(320, fixture.camera().getWidth());
        // Short upper shelf: the separate lower floor at $1FF is not combat geometry.
        for (int x = 0x1d10; x <= 0x1db8; x += 8) {
            var floor = ObjectTerrainUtils.checkFloorDist(GameServices.level(), x, 0x1b0);
            assertTrue(floor.foundSurface(), "upper shelf at " + Integer.toHexString(x));
            int surface = 0x1b0 + floor.distance();
            assertTrue(surface >= 0x1bf && surface <= 0x1c6, "upper surface " + Integer.toHexString(surface));
            for (int y = 0x100; y < 0x190; y += 16) {
                var ceiling = ObjectTerrainUtils.checkCeilingDist(x, y, 0);
                assertFalse(ceiling.foundSurface() && ceiling.distance() >= 0 && ceiling.distance() < 16,
                        "clear headroom at " + Integer.toHexString(x) + "," + Integer.toHexString(y));
            }
        }
        var post = level.getObjects().stream().filter(s -> s.x() == 0x1d60 && s.y() == 0x1a8)
                .findFirst().orElseThrow();
        assertEquals(0x34, post.objectId()); assertEquals(2, post.subtype());
        assertEquals(278, post.layoutIndex());
        assertTrue(level.getObjects().stream().anyMatch(s -> s.x() == 0x1dd0 && s.y() == 0x1d0 && s.subtype() == 0x32),
                "native spring beyond the bounded exit remains ROM-owned");
        assertTrue(level.getObjects().stream().anyMatch(s -> s.x() == 0x1de0 && s.y() == 0x1d0 && s.subtype() == 1),
                "native mushroom platform remains ROM-owned");
        assertTrue(level.getRings().stream().noneMatch(r -> r.x() >= 0x1d10 && r.x() <= 0x1db8
                && r.y() >= 0x100 && r.y() <= 0x1d0),
                "the prototype's one safe ring is an explicit native ring addition, not a fictitious retained ring");
    }
}
