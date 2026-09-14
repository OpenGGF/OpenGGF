package com.openggf.tools;

import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Real bonus entry and GPU overlap regression for the slots glass, September 2026. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "openggf.test.gl.native", matches = "true")
class TestS3kSlotsGlassNative {
    @ParameterizedTest
    @CsvSource({"sonic,320", "tails,352", "knuckles,400", "sonic,528", "sonic,800"})
    void glassOccludesPlayerAfterRealBonusFrame(String character, int width) throws Exception {
        var settings = new GameplayCaptureSession.Settings(width, character, "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            GameServices.level().requestBonusStageEntry(BonusStageType.SLOT_MACHINE);
            int frame = 0;
            for (; frame < 600 && session.loop().getCurrentGameMode() != GameMode.BONUS_STAGE; frame++) {
                session.step(null);
                session.render();
            }
            assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode());
            var coordinator = (Sonic3kBonusStageCoordinator) GameServices.bonusStage();
            assertNotNull(coordinator.activeSlotRuntime());
            // Neutral input falls into the machine; run the actual bonus loop,
            // including its post-physics priority enforcement, on every frame.
            for (int i = 0; i < 90; i++, frame++) {
                session.step(null);
                session.render();
            }
            var player = GameServices.camera().getFocusedSprite();
            boolean liveHighPriority = player.isHighPriority();
            RgbaImage actual = session.render();
            player.setHighPriority(false);
            GameServices.sprites().invalidateRenderBuckets();
            RgbaImage behind = session.render();
            player.setHighPriority(true);
            GameServices.sprites().invalidateRenderBuckets();
            RgbaImage inFront = session.render();
            player.setHighPriority(liveHighPriority);
            GameServices.sprites().invalidateRenderBuckets();

            String captureDir = System.getProperty("openggf.test.slots.captureDir");
            if (captureDir != null) {
                Path out = Path.of(captureDir).resolve(character + "-" + width);
                Files.createDirectories(out);
                Files.writeString(out.resolve("state.csv"),
                        "frame,x,y,cam_x,cam_y,mode,high_priority\n" + frame + ","
                                + player.getCentreX() + "," + player.getCentreY() + ","
                                + GameServices.camera().getX() + "," + GameServices.camera().getY() + ","
                                + session.loop().getCurrentGameMode() + "," + liveHighPriority + "\n");
                ScreenshotCapture.savePNG(actual, out.resolve("actual.png"));
                ScreenshotCapture.savePNG(behind, out.resolve("behind.png"));
                ScreenshotCapture.savePNG(inFront, out.resolve("in-front.png"));
            }
            int overlap = 0;
            int wrongPixels = 0;
            for (int y = 0; y < actual.height(); y++) {
                for (int x = 0; x < actual.width(); x++) {
                    if (behind.argb(x, y) != inFront.argb(x, y)) {
                        overlap++;
                        if (actual.argb(x, y) != behind.argb(x, y)) {
                            wrongPixels++;
                        }
                    }
                }
            }
            assertTrue(overlap > 20, "Capture must contain an actual glass/player overlap: " + overlap);
            assertEquals(0, wrongPixels, "Glass must occlude Sonic in the production framebuffer");
            assertFalse(liveHighPriority, "The real bonus loop must retain the slot player's low priority");
        }
    }
}
