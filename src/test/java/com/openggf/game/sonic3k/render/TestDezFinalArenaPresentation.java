package com.openggf.game.sonic3k.render;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Final DEZ arena layering and widescreen presentation policy. */
@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalArenaPresentation {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test void floorIsHighPlaneBOverTheLowRobotAndIsReplayedAboveIt() {
        boot(320);
        var level = GameServices.level();
        // DEZ3_ScreenInit (sonic3k.asm:120291-120348) draws Plane B from the background
        // layout; its floor rows $E0-$FF are high priority. DEZ3_BackgroundInit
        // (sonic3k.asm:120425-120432) draws the robot into Plane A with low priority.
        for (int x = 0; x < 0x400; x += 8) for (int y = 0xE0; y < 0x100; y += 8)
            assertNotEquals(0, level.getBackgroundTileDescriptorAtWorld(x, y) & 0x8000, "floor " + x + "," + y);
        for (int y = 0x180; y < 0x200; y += 8) for (int x = 0x240; x < 0x380; x += 8) {
            int d = level.getForegroundTileDescriptorAtWorld(x, y);
            if ((d & 0x7FF) != 0) assertEquals(0, d & 0x8000, "robot body " + x + "," + y);
        }
        var registry = new SpecialRenderEffectRegistry();
        new Sonic3kZoneFeatureProvider().registerSpecialRenderEffects(registry, 0x17, 0);
        assertEquals(1, registry.size(SpecialRenderEffectStage.AFTER_FOREGROUND));
        var other = new SpecialRenderEffectRegistry();
        new Sonic3kZoneFeatureProvider().registerSpecialRenderEffects(other, 0x17, 1);
        assertEquals(0, other.size(SpecialRenderEffectStage.AFTER_FOREGROUND));
    }

    @ParameterizedTest @ValueSource(ints = {352, 400, 528, 800})
    void wideCameraCentresThePlanetAndKeepsItsMarginsUnmasked(int width) {
        boot(width);
        var provider = new Sonic3kZoneFeatureProvider();
        assertTrue(provider.ownsWidescreenMargins());
        var columns = provider.backgroundColumns();
        assertNotNull(columns);
        assertEquals(width, columns.sourceX().size());
        int inset = (width - 320) / 2;
        for (int x = 0; x < 320; x++) assertEquals(x, columns.sourceX().get(inset + x));
        for (int x = 0; x < inset; x++) assertTrue(columns.sourceX().get(x) < 64, "left limb " + x);
        for (int x = inset + 320; x < width; x++) assertTrue(columns.sourceX().get(x) >= 256, "right limb " + x);
    }

    @ParameterizedTest @ValueSource(ints = {352, 400, 528, 800})
    void nativeCameraInAWiderRasterExtendsThePlanetToTheRight(int width) {
        // Trace capture keeps a 320px gameplay camera and widens only the raster.
        boot(320);
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        assertEquals(320, GameServices.camera().getWidth());
        var columns = new Sonic3kZoneFeatureProvider().backgroundColumns();
        assertNotNull(columns);
        assertEquals(width, columns.sourceX().size());
        for (int x = 0; x < 320; x++) {
            assertEquals(x, columns.sourceX().get(x));
            assertEquals(0, columns.yOffsets().get(x));
        }
        for (int x = 320; x < width; x++) assertTrue(columns.sourceX().get(x) >= 256, "right limb " + x);
    }

    @Test void nativeWidthHasNoProjectionAndOtherScenesKeepTheMask() throws Exception {
        boot(320);
        var provider = new Sonic3kZoneFeatureProvider();
        assertNull(provider.backgroundColumns());
        GameServices.level().loadZoneAndAct(11, 1);
        assertFalse(provider.ownsWidescreenMargins());
    }

    private void boot(int width) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
        assertEquals(width, GameServices.camera().getWidth());
    }
}
