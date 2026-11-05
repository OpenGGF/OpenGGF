package com.openggf.game.sonic3k;

import com.openggf.camera.CameraSubpixelAccess;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadContext;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Native Get_LevelSizeStart -> initial DeformBgLayer boundary, separate from results. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kInitialCameraMovement {
    @Test
    void freshStartCheckpointBypassAndResultsKeepTheirDistinctOwners() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(0x17, 1).build();
        var manager = GameServices.level();
        var camera = GameServices.camera();
        var profile = new Sonic3kLevelInitProfile(new Sonic3kLevelEventManager());
        var context = new LevelLoadContext();
        context.setIncludePostLoadAssembly(true);
        context.setAssemblyKind(com.openggf.game.LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        var fresh = profile.levelLoadSteps(context).stream()
                .filter(step -> step.name().equals("InitCamera")).findFirst().orElseThrow();
        CameraSubpixelAccess.setXFraction(camera, 0xA400);
        manager.initCameraForLevel();
        assertEquals(0x15A0, camera.getX() & 0xFFFF);
        assertEquals(0x0320, camera.getY() & 0xFFFF, "Get_LevelSizeStart, before deformation");
        fresh.execute();
        assertEquals(0x0326, camera.getY() & 0xFFFF, "executed native initial MoveCameraY");
        assertEquals(camera.getX(), camera.getXCopy());
        assertEquals(camera.getY(), camera.getYCopy());
        var before = camera.capture();
        profile.prepareInitialLevelScroll();
        assertEquals(before, camera.capture(), "later parallax preparation cannot move the camera twice");
        assertEquals(0xA400, CameraSubpixelAccess.xFraction(camera));
        camera.updatePosition();
        assertEquals(0x0320, camera.getY() & 0xFFFF, "ordinary bottom clamp remains active");

        manager.markSanctuaryReentry(2, true);
        fresh.execute();
        assertEquals(0x1600, camera.getX() & 0xFFFF, "loc_2E16C selected stage-2 backdrop");
        assertEquals(0x0240, camera.getY() & 0xFFFF);
        profile.prepareInitialLevelScroll();
        assertEquals(0x0240, camera.getY() & 0xFFFF, "results enters LevelSetup without deformation");
        assertEquals(camera.getX(), camera.getXCopy());
        assertEquals(camera.getY(), camera.getYCopy());
        assertEquals(0xA400, CameraSubpixelAccess.xFraction(camera));

        var checkpoint = new LevelLoadContext() {
            @Override public boolean hasCheckpoint() { return true; }
        };
        checkpoint.setIncludePostLoadAssembly(true);
        checkpoint.setAssemblyKind(com.openggf.game.LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        profile.levelLoadSteps(checkpoint).stream().filter(step -> step.name().equals("InitCamera"))
                .findFirst().orElseThrow().execute();
        assertEquals(0x15A0, camera.getX() & 0xFFFF, "checkpoint bypasses results override");
        assertEquals(0x0326, camera.getY() & 0xFFFF,
                "checkpoint still takes native Level deformation, bypassing results override");
        assertEquals(0xA400, CameraSubpixelAccess.xFraction(camera));
    }

    @Test
    void decodePreviewAndRestorationCannotAcquireInitialMovement() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(0x17, 1).build();
        var profile = new Sonic3kLevelInitProfile(new Sonic3kLevelEventManager());
        var context = new LevelLoadContext();
        context.setIncludePostLoadAssembly(true);
        for (var assembly : java.util.List.of(com.openggf.game.LevelAssemblyKind.DECODE_ONLY,
                com.openggf.game.LevelAssemblyKind.STATE_RESTORATION)) {
            context.setAssemblyKind(assembly);
            profile.levelLoadSteps(context).stream().filter(step -> step.name().equals("InitCamera"))
                    .findFirst().orElseThrow().execute();
            assertEquals(0x0320, GameServices.camera().getY());
        }
        context.setAssemblyKind(com.openggf.game.LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        context.setLoadMode(com.openggf.game.LevelLoadMode.PREVIEW_CAPTURE);
        profile.levelLoadSteps(context).stream().filter(step -> step.name().equals("InitCamera"))
                .findFirst().orElseThrow().execute();
        assertEquals(0x0320, GameServices.camera().getY());
    }

    @Test
    void freshNativeLoadMovesOnceAndRewindDoesNotRetainAnInitialMode() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(0x17, 1).build();
        var camera = GameServices.camera();
        int[] movements = {0};
        camera.setUpdateObserver(new com.openggf.camera.Camera.UpdateObserver() {
            public void onUpdatePosition(boolean force) { if (!force) movements[0]++; }
            public void onUpdateBoundaryEasing() { }
        });
        try {
            CameraSubpixelAccess.setXFraction(camera, 0xA400);
            GameServices.level().loadZoneAndActForFreshRuntime(0x17, 1);
            assertEquals(1, movements[0], "native plan and initial object walk share one dispatch");
            assertEquals(0x0326, camera.getY());
            assertEquals(0xA400, CameraSubpixelAccess.xFraction(camera));
            var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
            var saved = registry.capture();
            camera.updatePosition();
            assertEquals(0x0320, camera.getY());
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(saved);
                assertEquals(0x0326, camera.getY());
                assertEquals(0xA400, CameraSubpixelAccess.xFraction(camera));
                camera.updatePosition();
                assertEquals(0x0320, camera.getY(), "restoration does not rerun initial movement");
            }
        } finally {
            camera.setUpdateObserver(null);
        }
    }
}
