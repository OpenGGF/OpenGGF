package com.openggf.tests;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.RgbaImage;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/** MGZ2's raised Plane-B floor must hide the ordinary low-priority Spiker body. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMgzBackgroundPriorityPixels {
    @Test
    void raisedBackgroundMasksLowSpikerWhileHighSpriteRemainsVisible() throws Exception {
        for (int width : new int[] {320, 400}) {
            var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null,
                    0x3800, 0x0A90);
            try (var session = new GameplayCaptureSession(settings)) {
                session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 2, 1, settings);
                GameServices.level().consumePendingInitialProcessSpritesPass();
                session.player().setRingCount(99);
                for (int frame = 0; frame < 3; frame++) {
                    session.step(new Bk2FrameInput(frame, 0, 0, false, ""));
                }
                var runtime = S3kRuntimeStates.currentMgz(GameServices.zoneRuntimeRegistry()).orElseThrow();
                assertEquals(8, runtime.bgRiseRoutine());
                assertEquals(0x1D0, runtime.bgRiseOffset(), "late-load route uses the finished raised floor");
                var spiker = GameServices.level().getObjectManager().activeObjectsOfType(SpikerBadnikInstance.class)
                        .stream().filter(object -> object.getX() == 0x3870).findFirst().orElseThrow();
                assertFalse(spiker.isHighPriority());
                // Retain a fixed camera observation of the native Spiker/floor
                // overlap. The production MGZ state-8 scroll and actual ROM
                // descriptor/art data still own every sampled background pixel.
                GameServices.camera().setX((short) 0x37D0);
                GameServices.camera().setY((short) 0x0A00);
                publishScene();
                RgbaImage tiles = session.render(false);
                RgbaImage low = session.render();
                Field priority = SpikerBadnikInstance.class.getSuperclass().getDeclaredField("planePriority");
                priority.setAccessible(true);
                priority.setBoolean(spiker, true);
                publishScene();
                RgbaImage high = session.render();
                int centreX = spiker.getX() - GameServices.camera().getX();
                int centreY = spiker.getY() - GameServices.camera().getY();
                int covered = 0;
                int exposed = 0;
                for (int x = centreX - 12; x < centreX + 12; x++) {
                    for (int y = centreY - 20; y < centreY + 8; y++) {
                        if (high.argb(x, y) != low.argb(x, y)) {
                            assertEquals(tiles.argb(x, y), low.argb(x, y),
                                    "high background tiles must retain their visible color over the low sprite");
                            covered++;
                        } else if (low.argb(x, y) != tiles.argb(x, y)) {
                            exposed++;
                        }
                    }
                }
                assertTrue(covered > 40, "a real Spiker portion must be behind the raised floor at width " + width);
            }
        }
    }

    private static void publishScene() {
        LevelSpritePresentation.prepare(GameServices.level(), GameServices.sprites());
        LevelSpritePresentation.publishPreparedScene(GameServices.level());
    }
}
