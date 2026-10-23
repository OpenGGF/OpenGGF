package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.game.sonic2.Sonic2ZoneFeatureProvider;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLevelRuntimeBackdrop {
    private static final Palette.Color LEVEL_BACKDROP =
            new Palette.Color((byte) 0x12, (byte) 0x34, (byte) 0x56);

    private LevelManager levelManager;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        levelManager = GameServices.level();
        Level level = mock(Level.class);
        when(level.getBackdropColor()).thenReturn(LEVEL_BACKDROP);
        levelManager.level = level;
        levelManager.zoneFeatureProvider = null;
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void legacyMczProviderStillForcesBlackBackdrop() throws IOException {
        GameServices.zoneRuntimeRegistry().clear();
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        provider.initZoneFeatures(null, Sonic2ZoneConstants.ROM_ZONE_MCZ, 0, 0);
        levelManager.zoneFeatureProvider = provider;

        assertBlack(levelManager.resolveLevelBackdropColor());
    }

    @Test
    void wfzEscapeRuntimeDoesNotOverrideLevelBackdrop() {
        Sonic2LevelEventManager eventManager = new Sonic2LevelEventManager();
        eventManager.initLevel(Sonic2LevelEventManager.ZONE_WFZ, 0);
        eventManager.setEventRoutine(6);

        Palette.Color resolved = levelManager.resolveLevelBackdropColor();

        assertEquals(LEVEL_BACKDROP.r, resolved.r);
        assertEquals(LEVEL_BACKDROP.g, resolved.g);
        assertEquals(LEVEL_BACKDROP.b, resolved.b);
    }

    @Test
    void latchedLevelFadeBackdropFollowsTheDisplayedCramGeneration() {
        // S3K VInt_8 copies Normal_palette (Pal_FromBlack's output) to CRAM with the frame's
        // sprite table; colour 0 of the backdrop line is part of that upload, so the clear colour
        // must trail a latched fade request exactly like the plane lines it sits behind.
        when(levelManager.level.getBackdropPaletteLine()).thenReturn(2);
        var graphics = levelManager.graphicsManager;
        assertNotNull(graphics);
        com.openggf.graphics.PaletteUploadPresentation.publishAndLatch(graphics);
        try {
            graphics.setPaletteFadePresentation(com.openggf.graphics.PaletteFadePresentation.Mode.FROM_BLACK, 3,
                    com.openggf.graphics.PaletteFadePresentation.LINES_1_TO_3);
            Palette.Color beforeVint = levelManager.resolveLevelBackdropColor();
            assertEquals(LEVEL_BACKDROP.r, beforeVint.r, "requested fade must not reach the backdrop before its V-int");
            assertEquals(LEVEL_BACKDROP.g, beforeVint.g);
            assertEquals(LEVEL_BACKDROP.b, beforeVint.b);

            com.openggf.graphics.PaletteUploadPresentation.publishAndLatch(graphics);
            Palette.Color afterVint = levelManager.resolveLevelBackdropColor();
            int expected = com.openggf.graphics.PaletteFadePresentation.fadeRgb(0x12, 0x34, 0x56,
                    com.openggf.graphics.PaletteFadePresentation.Mode.FROM_BLACK, 3);
            assertEquals((byte) (expected >>> 16), afterVint.r, "published fade reaches the backdrop at its V-int");
            assertEquals((byte) (expected >>> 8), afterVint.g);
            assertEquals((byte) expected, afterVint.b);
        } finally {
            com.openggf.graphics.PaletteUploadPresentation.release(graphics);
            graphics.clearPaletteFadePresentation();
        }
    }

    @Test
    void editorEntryRetiresNativePublicationFadeWithoutAdvancingGameplay() {
        var graphics = levelManager.graphicsManager;
        graphics.setPaletteFadePresentation(com.openggf.graphics.PaletteFadePresentation.Mode.FROM_BLACK, 3,
                com.openggf.graphics.PaletteFadePresentation.LINES_1_TO_3);
        com.openggf.graphics.PaletteUploadPresentation.publishAndLatch(graphics);
        levelManager.restoreEditorLevelView(null);
        var displayed = com.openggf.graphics.PaletteUploadPresentation.displayedFade(graphics);
        assertEquals(com.openggf.graphics.PaletteFadePresentation.Mode.NONE, displayed.mode());
    }

    private static void assertBlack(Palette.Color color) {
        assertEquals(0, color.r & 0xFF);
        assertEquals(0, color.g & 0xFF);
        assertEquals(0, color.b & 0xFF);
    }
}
