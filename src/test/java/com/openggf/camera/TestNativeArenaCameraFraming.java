package com.openggf.camera;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.internal.NativeArenaCameraFraming;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestNativeArenaCameraFraming {
    @ParameterizedTest
    @CsvSource({"9,0,320", "9,0,352", "9,0,400", "9,0,528", "9,0,800",
            "11,1,320", "11,1,352", "11,1,400", "11,1,528", "11,1,800"})
    void centersBothViewLimitsButRetainsNativeBoundsAndRestoresPolicy(int zone, int act, int width) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(zone,act).build();
        var state = TestEnvironment.objectServices().zoneRuntimeState();
        var provider = (NativeArenaCameraFraming) GameServices.level().getZoneFeatureProvider();
        assertFalse(provider.centerNativeArenaCamera(), "fresh load has ordinary framing");
        if (state instanceof LrzZoneRuntimeState lrz) lrz.setCenterNativeArenaCamera(true);
        else ((S3kDezZoneRuntimeState)state).setCenterNativeArenaCamera(true);
        byte[] saved = state.captureBytes();
        assertTrue(provider.centerNativeArenaCamera());
        var config = mock(SonicConfigurationService.class);
        when(config.getShort(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short)width);
        when(config.getShort(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn((short)224);
        when(config.getString(SonicConfiguration.WIDESCREEN_DEADZONE_MODE)).thenReturn("CENTER_SCALED");
        var camera = new Camera(config);
        var player = fixture.sprite(); camera.setFocusedSprite(player);
        int min = zone==9 ? 0x2C00 : 0x3400;
        int max = zone==9 ? 0x2C00 : 0x34E0;
        camera.setMinX((short)min); camera.setMaxX((short)max);
        camera.setMinY((short)0); camera.setMaxY((short)0x1000);
        player.setCentreX((short)(min-0x400));
        camera.updatePosition(true);
        assertEquals(min-(width-320)/2,camera.getX());
        player.setCentreX((short)(max+0x500));
        camera.updatePosition(true);
        assertEquals(max-(width-320)/2,camera.getX());
        assertEquals(min,camera.getMinX(),"player's left boundary remains native");
        assertEquals(max,camera.getMaxX(),"player's right boundary remains native");
        if (state instanceof LrzZoneRuntimeState lrz) lrz.setCenterNativeArenaCamera(false);
        else ((S3kDezZoneRuntimeState)state).setCenterNativeArenaCamera(false);
        assertFalse(provider.centerNativeArenaCamera());
        state.restoreBytes(saved);
        assertTrue(provider.centerNativeArenaCamera(),"rewind restores the zone's presentation choice");
        camera.updatePosition(true);
        assertEquals(max-(width-320)/2,camera.getX());
    }
}
