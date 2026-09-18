package com.openggf.level;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.InputLogAuthorTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Regression for FBZ SAT publication making MHZ objects slide against live-camera terrain. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMovingCameraPresentation {
    @TempDir Path temporary;

    @Test void movingMhzCameraPublishesTerrainAndSpriteCoordinatesTogether() throws Exception {
        Path input = temporary.resolve("run.txt");
        InputLogAuthorTool.author("240 R; 60 -", input, "s3k");
        var movie = new Bk2MovieLoader().loadInputLog(input);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        int movingFrames = 0;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            LevelManager level = GameServices.level();
            LevelRenderer renderer = level.spritePresentationRenderer();
            for (var inputFrame : movie.getFrames()) {
                var prepared = renderer.spriteTables.capture().prepared();
                int cameraX = level.camera.getXWithShake();
                short foregroundY = level.parallaxManager.getVscrollFactorFG();
                int[] horizontal = level.parallaxManager.getHScrollForShader().clone();
                session.step(inputFrame);
                if (cameraX == level.camera.getXWithShake() || prepared.tiles().isEmpty()
                        || renderer.spriteTables.published() != prepared) continue;
                session.render(); // execute the actual terrain and occlusion-mask commands
                assertEquals((float) cameraX, field(renderer, "pendingFgWorldOffsetX_low"),
                        "visible terrain must use the camera that normalized the published SAT");
                assertEquals((float) cameraX, field(renderer, "pendingFboFgWorldOffsetX"),
                        "sprite occlusion must use the same published terrain origin");
                assertEquals((float) foregroundY, field(renderer, "pendingFgWorldOffsetY_low"));
                assertEquals((float) foregroundY, field(renderer, "pendingFboFgWorldOffsetY"));
                var drawnScroll = (com.openggf.util.IntIndexedView) field(renderer, "pendingFgHScrollView");
                assertNotNull(drawnScroll);
                assertEquals(horizontal.length, drawnScroll.size());
                for (int line = 0; line < horizontal.length; line++) {
                    assertEquals(horizontal[line], drawnScroll.get(line),
                            "foreground H-scroll DMA generation at line " + line);
                }
                movingFrames++;
            }
        }
        assertTrue(movingFrames >= 20, "must exercise actual camera movement, observed " + movingFrames);
    }

    private static Object field(LevelRenderer renderer, String name) throws Exception {
        Field field = LevelRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(renderer);
    }
}
