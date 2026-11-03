package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.glFinish;

/** HCZ1 results must cache standalone art without replacing the brick terrain underneath SONIC. */
@RequiresRom(SonicGame.SONIC_3K)
class TestHczResultsTerrainPixels {
    @Test
    void resultsPublicationAndTallyLeaveCpuPatternsAtlasSlotsAndTerrainPixelsIntact() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, 0x3720, 0x6D0);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 1, 0, settings);
            GameServices.level().consumePendingInitialProcessSpritesPass();
            GameServices.camera().setX((short) 0x3680);
            GameServices.camera().setY((short) 0x660);
            Level level = GameServices.level().getCurrentLevel();
            var graphics = GameServices.graphics();
            RgbaImage before = session.render(false);
            byte[] patternsBefore = patterns(level);
            List<PatternAtlas.Entry> atlasBefore = new ArrayList<>();
            for (int i = 0; i < level.getPatternCount(); i++) atlasBefore.add(graphics.getPatternAtlasEntry(i));
            var manager = GameServices.level().getObjectManager();
            var results = manager.createDynamicObject(() -> new S3kResultsScreenObjectInstance(
                    PlayerCharacter.SONIC_AND_TAILS, 0));
            results.update(0, session.player());
            assertFalse(results.hasLoadedResultsArt());
            int dispatch = 1;
            // Hold gameplay/animation clocks at the observed HCZ1 frame. Only
            // the real resource boundaries and owning results SST dispatch;
            // legitimate water/palette animation cannot hide an art overwrite.
            for (; dispatch < 10_000 && !results.hasLoadedResultsArt(); dispatch++) {
                var mode = SessionManager.getCurrentGameplayMode();
                mode.serviceHardwareTimingBoundary(HardwareServiceBoundary.VINT_SERVICE);
                mode.serviceHardwareTimingBoundary(HardwareServiceBoundary.POST_OBJECTS);
                mode.serviceHardwareTimingBoundary(HardwareServiceBoundary.PRE_MAIN_LOOP);
                results.update(dispatch, session.player());
            }
            assertTrue(results.hasLoadedResultsArt(), "all three ROM results archives must be claimed");
            var children = manager.activeObjectsOfType(S3kResultsElementObjectInstance.class).stream().toList();
            assertEquals(12, children.size(), "native results publication must have completed");
            // Every ObjArray_LevResults element starts outside its declared bounds.
            // The real ROM-backed mappings must not enter the table until slide-in.
            graphics.beginSpriteSatCollection();
            for (var child : children) child.appendRenderCommands(new ArrayList<>());
            Field entriesField = graphics.getClass().getDeclaredField("spriteSatEntries");
            entriesField.setAccessible(true);
            assertTrue(((List<?>) entriesField.get(graphics)).isEmpty(),
                    "offscreen results must be culled before their ROM mappings consume SAT entries");
            graphics.endSpriteSatCollectionAndReplay();
            var bonus = children.stream().filter(child -> child.entryIndex() == 6).findFirst().orElseThrow();
            Field currentX = bonus.getClass().getDeclaredField("currentX");
            currentX.setAccessible(true);
            int startingX = currentX.getInt(bonus);
            currentX.setInt(bonus, -64); // whole-element left boundary survives; its pieces do not
            graphics.beginSpriteSatCollection();
            bonus.appendRenderCommands(new ArrayList<>());
            assertTrue(((List<?>) entriesField.get(graphics)).isEmpty(),
                    "multi-draw must reject piece origins outside the native padded raster");
            graphics.endSpriteSatCollectionAndReplay();
            currentX.setInt(bonus, startingX);

            assertArrayEquals(patternsBefore, patterns(level), "results art must not write CPU terrain patterns");
            for (int i = 0; i < 400 && integer(results, "totalBonusCountUp") == 0; i++) {
                results.update(dispatch++, session.player());
                for (var child : children) child.update(dispatch, session.player());
            }
            assertTrue(integer(results, "totalBonusCountUp") > 0, "the real tally path must execute");
            RgbaImage tallyTerrain = session.render(false);
            graphics.beginPatternBatch();
            for (var child : children) results.appendElementRender(child, new ArrayList<>());
            graphics.flushPatternBatch();
            graphics.flush(); glFinish();
            RgbaImage withResults = ScreenshotCapture.captureFramebuffer(320, 224);
            int overlayPixels = 0;
            for (int i = 0; i < withResults.pixels().length; i++) {
                if (withResults.pixels()[i] != tallyTerrain.pixels()[i]) overlayPixels++;
            }
            assertTrue(overlayPixels > 100, "the actual results renderer must submit visible text and digits");
            assertNotNull(graphics.getPatternAtlasEntry(PatternAtlasRange.RESULTS_SCREENS.base() + 1),
                    "results art must have been cached in its reserved range");
            assertArrayEquals(patternsBefore, patterns(level), "tally rendering must not write terrain bytes");
            for (int i = 0; i < atlasBefore.size(); i++) {
                assertEquals(atlasBefore.get(i), graphics.getPatternAtlasEntry(i),
                        "terrain pattern " + i + " must retain its exact atlas identity");
            }
            RgbaImage after = session.render(false);
            // The screenshot's affected brick lies below the left-side SONIC
            // heading. Excluding result sprites makes any changed terrain
            // pixel observable instead of covering it with the overlay itself.
            for (int y = 200; y < 224; y++) for (int x = 0; x < 160; x++) {
                assertEquals(before.argb(x, y), after.argb(x, y), "HCZ1 results brick changed at " + x + "," + y);
            }
        }
    }

    private static byte[] patterns(Level level) {
        byte[] bytes = new byte[level.getPatternCount() * Pattern.PATTERN_SIZE_IN_MEM];
        for (int i = 0; i < level.getPatternCount(); i++) {
            Pattern pattern = level.getPattern(i);
            if (pattern != null) pattern.copyInto(bytes, i * Pattern.PATTERN_SIZE_IN_MEM);
        }
        return bytes;
    }

    private static int integer(Object object, String name) throws Exception {
        Class<?> type = object.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name); field.setAccessible(true); return field.getInt(object);
            } catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
