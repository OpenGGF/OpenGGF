package com.openggf.level.objects;

import com.openggf.camera.CameraPresentationAccess;
import com.openggf.game.GameServices;
import com.openggf.game.internal.HostedGameMedia;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.FbzSpringPlungerInstance;
import com.openggf.game.sonic3k.objects.badniks.ToxomisterBadnikInstance;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.objects.ssz.ObjSpringLauncher;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDormantObjectBoot {
    private static final GameplayCaptureSession.Settings SETTINGS = new GameplayCaptureSession.Settings(
            400, "sonic", "tails", "off", null, null, null, null, true,
            false, null, null, false, null, false, false, "NATIVE", "GREYSCALE");

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void flyingBatteryBakesTheUnpressedPlunger() throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), Sonic3kZoneIds.ZONE_FBZ, 0, SETTINGS);
            assertBakes(session, FbzSpringPlungerInstance.class);
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void lavaReefBakesToxomisterWithDetachedCloudChildren() throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), Sonic3kZoneIds.ZONE_LRZ, 0, SETTINGS);
            assertBakes(session, ToxomisterBadnikInstance.class);
        }
    }

    @Test
    void stardustSpeedwayBakesSpringLauncherWithDetachedArm() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var session = new GameplayCaptureSession(SETTINGS)) {
            var module = new SonicCdGameModule();
            var stage = HostedGameMedia.of(module).levelNamed("R71A", 1);
            session.boot(disc, module, stage.zone(), stage.act(), SETTINGS);
            assertBakes(session, ObjSpringLauncher.class);
        }
    }

    private static void assertBakes(GameplayCaptureSession session, Class<? extends ObjectInstance> expected) {
        assertTrue(CameraPresentationAccess.hasMargin(GameServices.camera()));
        var layer = GameServices.level().getObjectManager().dormantLayer;
        // Exercise the interactive frame entry from Engine.update(), retaining any pending title card.
        for (int frame = 0; frame < 180 && !layer.bakeComplete(); frame++) {
            session.loop().stepPresentationFrame();
        }
        assertTrue(layer.bakeComplete(), "the dormant bake should complete at level entry");
        assertTrue(layer.poses().stream().anyMatch(pose -> expected.isInstance(pose.frames()[0].instance())),
                () -> "missing dormant pose for " + expected.getSimpleName());
    }
}
