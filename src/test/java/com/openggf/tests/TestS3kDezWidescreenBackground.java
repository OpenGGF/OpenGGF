package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.scroll.SwScrlS3kDez;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** ROM pixel oracle for the render-only DEZ1 wall extension. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezWidescreenBackground {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void centreIsUnchangedAndExtraWidthReflectsOnlyOuterWallPixels(int width) {
        boot(width, 0);
        var provider = new Sonic3kZoneFeatureProvider();
        var scroll = new SwScrlS3kDez();
        scroll.update(new int[224], 0x2345, 0x678, 123, 0);
        assertTrue(scroll.getBgPeriodWidth() >= width);
        if (width == 320) {
            assertEquals(0, provider.backgroundDescriptorRevision());
            assertFalse(provider.bgWrapsHorizontally());
            return;
        }
        assertNotEquals(0, provider.backgroundDescriptorRevision());
        assertTrue(provider.bgWrapsHorizontally());
        var lm = GameServices.level();
        var level = lm.getCurrentLevel();
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < width; x++) {
                int sourceX = x - (width - 320) / 2;
                if (sourceX < 0) {
                    int t = Math.floorMod(-sourceX - 1, 64);
                    sourceX = t < 32 ? t : 63 - t;
                } else if (sourceX >= 320) {
                    int t = Math.floorMod(sourceX - 320, 64);
                    sourceX = 319 - (t < 32 ? t : 63 - t);
                }
                int original = lm.getBackgroundTileDescriptorAtWorld(sourceX, y);
                int extended = provider.backgroundDescriptorAt(x & ~7, y & ~7);
                assertEquals(original & 0xE000, extended & 0xE000);
                int expected = level.getPattern(original & 0x7FF).getPixel(
                        (original & 0x800) != 0 ? 7 - (sourceX & 7) : sourceX & 7,
                        (original & 0x1000) != 0 ? 7 - (y & 7) : y & 7);
                int actual = level.getPattern(extended & 0x7FF).getPixel(
                        (extended & 0x800) != 0 ? 7 - (x & 7) : x & 7,
                        (extended & 0x1000) != 0 ? 7 - (y & 7) : y & 7);
                assertEquals(expected, actual, "pixel " + x + "," + y);
            }
        }
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void planetKeepsEveryCentralPixelAndExtendsOneContinuousHorizon(int width) throws Exception {
        boot(width, 1);
        var provider = new Sonic3kZoneFeatureProvider();
        var columns = provider.backgroundColumns();
        if (width == 320) { assertNull(columns); return; }
        assertNotNull(columns);
        assertSame(columns, provider.backgroundColumns(), "static projection is cached");
        assertEquals(width, columns.sourceX().size());
        int inset = (width - 320) / 2;
        for (int x = 0; x < 320; x++) {
            assertEquals(x, columns.sourceX().get(inset + x));
            assertEquals(0, columns.yOffsets().get(inset + x));
        }
        var lm = GameServices.level();
        int[] horizon = new int[320];
        for (int x = 0; x < 320; x++) {
            while (horizon[x] < 224 && backgroundRgb(x, horizon[x]) == 0) horizon[x]++;
            assertTrue(horizon[x] < 224);
        }
        for (int x = 0; x < inset; x++) {
            int source = columns.sourceX().get(x);
            assertTrue(source >= 0 && source < 64);
            int height = horizon[source] + columns.yOffsets().get(x);
            int next = horizon[columns.sourceX().get(x + 1)] + columns.yOffsets().get(x + 1);
            assertTrue(height >= next && height - next <= 1, "continuous left limb");
        }
        for (int x = inset + 320; x < width; x++) {
            int source = columns.sourceX().get(x);
            assertTrue(source >= 256 && source < 320);
            int height = horizon[source] + columns.yOffsets().get(x);
            int previous = horizon[columns.sourceX().get(x - 1)] + columns.yOffsets().get(x - 1);
            assertTrue(height >= previous && height - previous <= 1, "continuous right limb");
        }
        // Fade-to-black cannot change a newly derived projection.
        for (int bank = 0; bank < 4; bank++) for (int index = 0; index < 16; index++) {
            var color = lm.getCurrentLevel().getPalette(bank).getColor(index);
            color.r = 0; color.g = 0; color.b = 0;
        }
        var faded = new com.openggf.game.sonic3k.render.DezPlanetBackground().columns(lm, width);
        assertNotNull(faded);
        for (int x = 0; x < width; x++) {
            assertEquals(columns.sourceX().get(x), faded.sourceX().get(x));
            assertEquals(columns.yOffsets().get(x), faded.yOffsets().get(x));
        }
        var snapshot = TestEnvironment.activeGameplayMode().getRewindRegistry().capture();
        TestEnvironment.activeGameplayMode().getRewindRegistry().restore(snapshot);
        assertSame(columns, provider.backgroundColumns());
        // A load cannot retain the previous act's sampling override.
        lm.loadZoneAndAct(11, 0);
        assertNull(provider.backgroundColumns());
    }

    private int backgroundRgb(int x, int y) {
        var lm = GameServices.level();
        var level = lm.getCurrentLevel();
        int d = lm.getBackgroundTileDescriptorAtWorld(x, y);
        int index = level.getPattern(d & 0x7FF).getPixel(
                (d & 0x800) != 0 ? 7 - (x & 7) : x & 7,
                (d & 0x1000) != 0 ? 7 - (y & 7) : y & 7);
        var color = index == 0 ? level.getBackdropColor() : level.getPalette((d >>> 13) & 3).getColor(index);
        return (color.r & 255) << 16 | (color.g & 255) << 8 | color.b & 255;
    }

    private void boot(int width, int act) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(11, act).build();
        assertEquals(width, GameServices.camera().getWidth());
    }
}
