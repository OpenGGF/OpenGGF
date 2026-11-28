package com.openggf.level.objects;

import com.openggf.camera.CameraPresentationAccess;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.graphics.RgbaImage;
import com.openggf.level.rings.RingSpawn;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Exercises sprite admission and replay through to the displayed NATIVE framebuffer. */
@Isolated
@RequiresRom(SonicGame.SONIC_1)
class TestNativeDormantMarginRendering {
    private static final int WIDTH = 800;
    private static final int NATIVE_WIDTH = 320;

    @ParameterizedTest(name = "{0} margin")
    @ValueSource(strings = {"left", "right"})
    void drawsNeverLoadedAndUnloadedBridgeAndRing(String side) throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        // Choose coarse camera buckets where S1's ObjPosLoad and out_of_range both
        // exclude the spawns, though their entire sprite fits in the displayed margin.
        boolean leftMargin = side.equals("left");
        int spawnX = leftMargin ? 0xA60 : 0xA00;
        int marginCameraX = leftMargin ? spawnX + 192 : spawnX - 528;
        var settings = new GameplayCaptureSession.Settings(WIDTH, "sonic", "", "off", null,
                marginCameraX + NATIVE_WIDTH / 2, 620, null, false, false,
                null, null, false, null, false, false, "NATIVE", "GREYSCALE");
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic1RomAvailable().toPath(), 0, 2, settings);
            session.step(null);
            session.step(null);
            var manager = GameServices.level().getObjectManager();
            var camera = GameServices.camera();
            assertEquals(NATIVE_WIDTH, camera.getWidth());
            assertEquals(WIDTH, CameraPresentationAccess.width(camera));

            int bridgeY = camera.getY() + 80;
            int ringY = bridgeY - 48;
            var bridge = new ObjectSpawn(spawnX, bridgeY, Sonic1ObjectIds.BRIDGE, 2, 0, false, bridgeY, 0);
            var ring = new ObjectSpawn(spawnX, ringY, Sonic1ObjectIds.RING, 0, 0, false, ringY, 1);
            var spawns = List.of(bridge, ring);
            manager.resyncSpawnList(spawns);
            GameServices.level().getRingManager().resyncSpawnList(List.of(new RingSpawn(spawnX, ringY)));
            camera.setX((short) marginCameraX);
            manager.reset(marginCameraX);

            var layer = manager.dormantLayer;
            manager.prepareDormantPresentation(SessionManager.getCurrentGameplayMode().getRewindRegistry());
            assertTrue(layer.bakeComplete());
            assertEquals(2, layer.poseCount());
            assertTrue(manager.population().placed.isEmpty(), "both spawns start outside the loader");
            assertEquals(0, layer.lastStateCount());
            assertMarginPixels(session, side + " never loaded", spawns);

            int centreCameraX = spawnX - NATIVE_WIDTH / 2;
            moveLoaderCamera(manager, centreCameraX);
            moveLoaderCamera(manager, centreCameraX);
            var live = spawns.stream().map(manager.population().placed::get).toList();
            assertNotNull(live.get(0), "the bridge entered the native loader");
            assertNotNull(live.get(1), "the ring entered the native loader");

            moveLoaderCamera(manager, marginCameraX);
            assertTrue(manager.population().placed.isEmpty(), "both objects unloaded by native lifetime checks");
            assertEquals(2, layer.lastStateCount());
            for (int i = 0; i < spawns.size(); i++) {
                var spawn = spawns.get(i);
                var pose = layer.poses().stream().filter(candidate -> candidate.spawn().equals(spawn))
                        .findFirst().orElseThrow();
                assertSame(live.get(i), layer.frameFor(pose).instance(), "uses the unloaded object's last state");
            }
            assertMarginPixels(session, side + " after unload", spawns);
        }
    }

    private static void moveLoaderCamera(ObjectManager manager, int cameraX) {
        GameServices.camera().setX((short) cameraX);
        manager.postCameraPlacementUpdate(cameraX);
        manager.update(cameraX, null, List.of(), 0, false);
    }

    private static void assertMarginPixels(GameplayCaptureSession session, String state, List<ObjectSpawn> spawns) {
        var config = GameServices.configuration();
        var pictures = new RgbaImage[2];
        try {
            PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(), () -> {
                pictures[0] = session.render();
                config.setSessionOverride(SonicConfiguration.WIDESCREEN_DORMANT_STYLE, "HIDDEN");
                pictures[1] = session.render();
            });
        } finally {
            config.setSessionOverride(SonicConfiguration.WIDESCREEN_DORMANT_STYLE, "GREYSCALE");
        }

        var camera = GameServices.camera();
        int left = CameraPresentationAccess.leftWithShake(camera);
        for (var spawn : spawns) {
            int x = spawn.x() - left;
            int y = spawn.y() - camera.getYWithShake();
            int inset = CameraPresentationAccess.inset(camera);
            assertTrue(x - 24 >= 0 && x + 24 <= WIDTH);
            assertTrue(x + 24 <= inset || x - 24 >= inset + NATIVE_WIDTH);
            assertTrue(hasDifferentPixel(pictures[0], pictures[1], x, y),
                    () -> state + " must display dormant object ID " + Integer.toHexString(spawn.objectId()));
        }
    }

    private static boolean hasDifferentPixel(RgbaImage shown, RgbaImage hidden, int centreX, int centreY) {
        for (int y = centreY - 12; y < centreY + 12; y++) {
            for (int x = centreX - 24; x < centreX + 24; x++) {
                if (shown.argb(x, y) != hidden.argb(x, y)) return true;
            }
        }
        return false;
    }
}
