package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.SozFloatingPillarObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Native SOZ2 Plane-B sand hides the low-priority pillar and its spike pieces. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozSandPriorityPixels {
    @Test
    void risingSandOccludesPillarSpikesWithoutHidingItsExposedBody() throws Exception {
        var scene = SozConnectedMechanismRoute.Scene.LOWER;
        var settings = new GameplayCaptureSession.Settings(400, "sonic", "", "off", null, scene.x, scene.y);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 1, settings);
            GameServices.level().consumePendingInitialProcessSpritesPass();
            session.player().setRingCount(99);
            var route = new SozConnectedMechanismRoute(scene);
            for (int frame = 0; frame <= 1200; frame++) {
                session.step(route.input(frame, session.player()));
            }
            assertFalse(session.player().getDead());
            assertEquals(0x18, S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry())
                    .orElseThrow().events().backgroundRoutine());
            var pillar = GameServices.level().getObjectManager()
                    .activeObjectsOfType(SozFloatingPillarObjectInstance.class).stream()
                    .filter(object -> object.getSpawn().x() == 0x2300 && object.getSpawn().subtype() == 0x22)
                    .findFirst().orElseThrow();
            var full = session.render();
            var tiles = session.render(false);
            int x = pillar.getX() - GameServices.camera().getX();
            int y = pillar.getY() - GameServices.camera().getY();
            int exposed = 0;
            // Map_SOZFloatingPillar frame2: body -80..48, downward spikes +48..80.
            // This ordinary route boundary has the final eight spike rows below the sand.
            assertTrue(x - 24 >= 0 && x + 24 < full.width() && y + 80 < 199);
            for (int dx = -24; dx < 24; dx++) {
                for (int dy = 72; dy < 80; dy++) {
                    assertEquals(tiles.argb(x + dx, y + dy), full.argb(x + dx, y + dy),
                            "submerged spikes must not overwrite the native high-priority sand");
                }
                for (int dy = -48; dy < -16; dy++) {
                    if (tiles.argb(x + dx, y + dy) != full.argb(x + dx, y + dy)) exposed++;
                }
            }
            assertTrue(exposed > 100, "the pillar body above the sand must remain visible");
        }
    }
}
