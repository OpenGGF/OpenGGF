package com.openggf.game.sonic3k.ending;

import com.openggf.camera.NativeViewportFraming;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native credits enter the ordinary Plane A/high-priority tile path, outside the SAT. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCreditsPlanePresentation {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();
    }
    @ParameterizedTest @ValueSource(ints={320,352,400,528,800})
    void nativeDescriptorsUseResidentFontTilesAndCropTheCenteredWindow(int width) throws Exception {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        GameServices.camera().refreshViewportDimensions(config);
        assertEquals(width,GameServices.camera().getWidth(),"the ROM extension creates the camera before this scenario override");
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        HeadlessTestFixture.builder().withZoneAndAct(10,1).withFreshLevelStartLifecycle().build();
        var runtime=(SszZoneRuntimeState)GameServices.zoneRuntimeState();runtime.setCenterNativeArenaCamera(true);
        var camera=GameServices.camera();camera.setX((short)NativeViewportFraming.visibleLeft(0x240,width));
        var services=TestEnvironment.objectServices();S3kEndingCoordinator.beginCredits(services);
        var credits=runtime.ending().credits();
        for(int i=0;i<200&&credits.rowIndex()==0;i++)credits.step(services.romReader());
        assertEquals(2,credits.rowIndex());
        var provider=(Sonic3kZoneFeatureProvider)GameServices.module().getZoneFeatureProvider();
        assertEquals(0xA34A,provider.foregroundDescriptorAt(0x240+24,240));
        assertEquals(0,provider.foregroundDescriptorAt(0x238,240));
        assertEquals(0,provider.foregroundDescriptorAt(0x240+320,240));
        // sub_5B18E scrolls Camera_Y_pos to $1CA0 while Plane A is a 32-row ring: the VDP
        // wraps V-scroll at 256 lines, so part two (Camera_Y_pos >= $F60) reads the same
        // cells. The tile pass must wrap them rather than discard rows past the SSZ2 layout.
        assertEquals(provider.foregroundDescriptorAt(0x240+24,240),provider.foregroundDescriptorAt(0x240+24,240+0x1000));
        assertTrue(provider.foregroundDescriptorsWrapVertically(),"credits Plane A is a wrapped VDP ring");
        long visibleRevision=provider.foregroundDescriptorRevision();runtime.ending().setFlag(3);
        assertEquals(0,provider.foregroundDescriptorAt(0x240+24,240));
        assertNotEquals(visibleRevision,provider.foregroundDescriptorRevision(),"native bit3 must invalidate the text plane");
        assertFalse(provider.foregroundDescriptorsWrapVertically(),"bit3 returns Plane A to the finite SSZ2 layout");
    }
}
