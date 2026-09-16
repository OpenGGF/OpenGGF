package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AIZ2 forest loop: the persistent Plane A ring must be filled from the live CPU
 * camera in the same frame as {@code AIZ2_DoShipLoop} (sonic3k.asm:105205), which
 * pairs its $200 camera subtraction with the {@code Camera_X_pos_rounded} baseline
 * retarget consumed by {@code DrawTilesAsYouMove} (sonic3k.asm:103171). Only
 * H-scroll/VSRAM/SAT are VBlank-published; a single-player lag VBlank retains the
 * previous ones. Feeding that retained camera into the ring while the frame's
 * {@code Level_repeat_offset} is live (regression from ca44ebed2) shifts the ring
 * baseline against an unwrapped camera and reseeds the forest entrance.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestAiz2ForestRingCameraGeneration {
    private static final int ZONE_AIZ = 0;
    private static final int ACT_2 = 1;
    private static final int POST_BOMBING_WRAP_X = 0x46C0;

    @Test void shipLoopWrapOnLagFrameFillsRingFromLiveCamera() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), ZONE_AIZ, ACT_2, settings);
            LevelManager level = GameServices.level();
            LevelRenderer renderer = level.spritePresentationRenderer();
            Sonic3kAIZEvents events = ((Sonic3kLevelEventManager)
                    GameServices.module().getLevelEventProvider()).getAizEvents();
            assertNotNull(events, "AIZ events should be initialized");
            setPrivate(events, "battleshipAutoScrollActive", true);
            setPrivate(events, "battleshipWrapX", POST_BOMBING_WRAP_X);
            assertTrue(events.isBattleshipForestLoopActive());

            // One published generation inside the loop, well before the wrap. Load
            // and lag VBlanks skip publication, so step until an ordinary one lands.
            placeCamera(level, 0x4600);
            LevelSpritePresentation.Tables.State retained = null;
            for (int frame = 0; frame < 64 && retained == null; frame++) {
                session.step(null);
                session.render();
                var state = renderer.spriteTables.capture();
                if (state.publishedScroll() != null
                        && state.publishedScroll().registers().cameraXWithShake() >= 0x4600) {
                    retained = state;
                }
            }
            assertNotNull(retained, "S3K publishes the scroll generation at an ordinary VBlank");
            int publishedCameraX = retained.publishedScroll().registers().cameraXWithShake();
            assertTrue((level.camera.getX() & 0xFFFF) < 0x46BC, "still well before the wrap boundary");

            // Wrap frame: AIZ2_DoShipLoop steps 0x46BC -> 0x46C0 and subtracts $200.
            placeCamera(level, 0x46BC);
            session.step(null);
            int liveCameraX = level.camera.getXWithShake();
            assertTrue((liveCameraX & 0xFFFF) < POST_BOMBING_WRAP_X, "camera must have wrapped");
            assertEquals(0x200, events.getLevelRepeatOffset(), "Level_repeat_offset is live on the wrap frame");

            // VInt_0 lag path: the VBlank after the wrap retains the previous publication.
            renderer.spriteTables.restore(retained);
            assertNotEquals(liveCameraX, publishedCameraX, "the retained generation must predate the wrap");
            session.render();

            assertEquals(liveCameraX, intField(level.tilemapManager, "foregroundRingCameraX"),
                    "DrawTilesAsYouMove reads Camera_X_pos_copy, not the published scroll register");
            assertEquals(0, intField(level.tilemapManager, "foregroundRingWorldWrapOffset"),
                    "the wrap offset is consumed by the same reconcile that saw the wrapped camera");
            assertEquals(Math.floorDiv(liveCameraX, LevelConstants.CHUNK_WIDTH) * LevelConstants.CHUNK_WIDTH,
                    intField(level.tilemapManager, "foregroundRingLastLeftCol"),
                    "the ring baseline follows the wrapped camera");
        }
    }

    private static void placeCamera(LevelManager level, int x) {
        level.camera.setX((short) x);
        level.camera.setMinX((short) x);
        level.camera.setMaxX((short) x);
    }

    private static void setPrivate(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int intField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
