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
    @ParameterizedTest
    @CsvSource({"320", "352", "400", "528", "800"})
    void horizontalArenaLockIgnoresSonicXButKeepsVerticalTrackingAndNativeBounds(int width) {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(11,1).build();
        var state=(S3kDezZoneRuntimeState)TestEnvironment.objectServices().zoneRuntimeState();
        state.setCenterNativeArenaCamera(true); state.lockWidescreenHorizontalArena(0x3400,0x34E0);
        var provider=(NativeArenaCameraFraming)GameServices.level().getZoneFeatureProvider();
        assertEquals(0x3470,provider.lockedNativeHorizontalCamera().orElseThrow());
        var config=mock(SonicConfigurationService.class);
        when(config.getShort(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short)width);
        when(config.getShort(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn((short)224);
        when(config.getString(SonicConfiguration.WIDESCREEN_DEADZONE_MODE)).thenReturn("CENTER_SCALED");
        var camera=new Camera(config); var player=fixture.sprite(); camera.setFocusedSprite(player);
        camera.setMinX((short)0x3400); camera.setMaxX((short)0x34E0);
        camera.setMinY((short)0x218); camera.setMaxY((short)0x288);
        int anchor=NativeViewportFraming.visibleLeft(0x3470,width);
        for(int playerX:new int[]{0x33E0,0x3470,0x35A0,0x3680}) {
            player.setCentreX((short)playerX); player.setCentreY((short)0x2B0);
            camera.updatePosition(true);
            if(width>320) assertEquals(anchor,camera.getX());
            else assertEquals(Math.max(0x3400,Math.min(0x34E0,playerX-160)),camera.getX());
            if(width>320) assertEquals((short)anchor,camera.previewNextX());
        }
        camera.setY((short)0x250); player.setAir(false); player.setGSpeed((short)0);
        player.setCentreY((short)0x240); camera.updatePosition(); int up=camera.getY();
        assertTrue(up<0x250,"horizontal lock leaves vertical tracking live");
        player.setCentreY((short)0x380); camera.updatePosition(); assertTrue(camera.getY()>up);
        if(width>320) assertEquals(anchor,camera.getX());
        assertEquals(0x3400,camera.getMinX()); assertEquals(0x34E0,camera.getMaxX());
        byte[] saved=state.captureBytes(); state.clearWidescreenHorizontalArenaLock();
        assertTrue(provider.lockedNativeHorizontalCamera().isEmpty());
        player.setCentreX((short)0x3800); camera.updatePosition(true);
        assertEquals(NativeViewportFraming.visibleLeft(0x34E0,width),camera.getX());
        state.restoreBytes(saved); camera.updatePosition(true);
        if(width>320) assertEquals(anchor,camera.getX());
    }

    @ParameterizedTest
    @CsvSource({"352,0,320", "352,0,352", "352,0,400", "352,0,528", "352,0,800",
            "5728,2,320", "5728,2,352", "5728,2,400", "5728,2,528", "5728,2,800"})
    void sszReplicaLocksRetainCenteredCameraAcrossPlayerDisplacementAndRewind(int anchor, int event, int width) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10,0).build();
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState)
                TestEnvironment.objectServices().zoneRuntimeState();
        var provider = (NativeArenaCameraFraming) GameServices.level().getZoneFeatureProvider();
        state.setEventsBgWord(0,0); state.setEventsBgWord(2,0);
        assertFalse(provider.centerNativeArenaCamera());
        state.setEventsBgByte(event+1,0xFF);
        assertTrue(provider.centerNativeArenaCamera(), "lock begins before allocation");
        state.setEventsBgWord(event,0x7F00); state.setEventsBgByte(5,0xFF);
        byte[] saved = state.captureBytes();
        var config = mock(SonicConfigurationService.class);
        when(config.getShort(SonicConfiguration.SCREEN_WIDTH_PIXELS)).thenReturn((short)width);
        when(config.getShort(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenReturn((short)224);
        when(config.getString(SonicConfiguration.WIDESCREEN_DEADZONE_MODE)).thenReturn("CENTER_SCALED");
        var camera = new Camera(config); var player = fixture.sprite(); camera.setFocusedSprite(player);
        camera.setMinX((short)anchor); camera.setMaxX((short)anchor);
        camera.setMinY((short)0); camera.setMaxY((short)0x1000);
        int visible = NativeViewportFraming.visibleLeft(anchor,width);
        for (int displacement : new int[]{0,88,-88,160,-160}) {
            player.setCentreX((short)(anchor+160+displacement));
            camera.updatePosition(true);
            assertEquals(visible,camera.getX(), "player displacement cannot move the arena");
            assertEquals(visible,camera.previewNextX());
        }
        state.setEventsBgByte(event,0xFF);
        assertTrue(provider.centerNativeArenaCamera(), "defeat alone does not release bounds");
        state.setEventsBgByte(5,0);
        assertFalse(provider.centerNativeArenaCamera(), "launch releases framing");
        state.restoreBytes(saved);
        assertTrue(provider.centerNativeArenaCamera(), "rewind restores lock ownership");
        camera.updatePosition(true); assertEquals(visible,camera.getX());
        assertEquals(anchor,camera.getMinX()); assertEquals(anchor,camera.getMaxX());
    }

}
