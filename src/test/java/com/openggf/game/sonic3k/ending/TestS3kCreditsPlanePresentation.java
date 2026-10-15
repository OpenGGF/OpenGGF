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
        assertEquals(S3kCreditsState.PLANE_ROWS,provider.foregroundDescriptorRingRows(),"credits Plane A is a wrapped 32-row VDP ring");
        long visibleRevision=provider.foregroundDescriptorRevision();runtime.ending().setFlag(3);
        assertEquals(0,provider.foregroundDescriptorAt(0x240+24,240));
        assertNotEquals(visibleRevision,provider.foregroundDescriptorRevision(),"native bit3 must invalidate the text plane");
        assertEquals(0,provider.foregroundDescriptorRingRows(),"bit3 returns Plane A to the finite SSZ2 layout");
    }

    /**
     * Part two (Camera_Y_pos from $F60) samples the retained plane through the foreground
     * tile pass's vertical wrap. The VDP wraps the 32-row Plane A every 256 lines, so the
     * line at world Y shows plane row (Y/8)&31. SSZ2's layout is 19 chunks (304 rows):
     * wrapping the pass at that height put every part-two row 16 rows (128px) off native.
     */
    @org.junit.jupiter.api.Test
    void partTwoSamplesThePlaneRingAtTheVdpWrapNotTheLayoutHeight() throws Exception {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        HeadlessTestFixture.builder().withZoneAndAct(10,1).withFreshLevelStartLifecycle().build();
        var runtime=(SszZoneRuntimeState)GameServices.zoneRuntimeState();runtime.setCenterNativeArenaCamera(true);
        var camera=GameServices.camera();camera.setX((short)NativeViewportFraming.visibleLeft(0x240,camera.getWidth()));
        var services=TestEnvironment.objectServices();S3kEndingCoordinator.beginCredits(services);
        var credits=runtime.ending().credits();
        // Run sub_5B18E into part two (_unkFAAE=4) with its first two rows written.
        for(int i=0;i<20000&&!(credits.partIndex()==4&&credits.rowIndex()>2);i++)credits.step(services.romReader());
        assertEquals(4,credits.partIndex());
        int cameraY=credits.scroll()>>16;
        assertTrue(cameraY>=0xF60,"part two starts at Camera_Y_pos $F60 or later");
        var provider=(Sonic3kZoneFeatureProvider)GameServices.module().getZoneFeatureProvider();
        var level=GameServices.level();
        level.snapshotForegroundTilemapBeforeRuntimeLayoutMutation();
        var tilemaps=level.getTilemapManager();
        byte[] data=tilemaps.getForegroundTilemapData();
        int width=tilemaps.getForegroundTilemapWidthTiles(), height=tilemaps.getForegroundTilemapHeightTiles();
        int textLines=0;
        for(int screenY=0;screenY<224;screenY+=8){
            int worldY=cameraY+screenY;
            // shader_tilemap.glsl WrapY: tileY = mod(floor(worldY/8), TilemapHeight).
            int sampledRow=Math.floorMod(worldY>>3,height);
            for(int column=0;column<40;column++){
                int tileX=(camera.getX()>>3)+column;
                int expected=provider.foregroundDescriptorAt(tileX*8,worldY);
                if(expected!=0)textLines++;
                int offset=(sampledRow*width+tileX)*4;
                assertEquals(encoded(expected),(data[offset]&0xFF)|((data[offset+1]&0xFF)<<8),
                        "screen line "+screenY+" column "+column+" must show plane row "+((worldY>>3)&31));
            }
        }
        assertTrue(textLines>0,"the comparison must cover written credit rows");
    }

    /** LevelTilemapManager.writeTilemapDescriptor's R/G bytes (pattern, palette, flips, priority). */
    private static int encoded(int descriptor) {
        if(descriptor==0)return 0;
        int pattern=descriptor&0x7FF;
        int g=((pattern>>8)&7)|(((descriptor>>13)&3)<<3)|((descriptor&0x800)!=0?0x20:0)
                |((descriptor&0x1000)!=0?0x40:0)|((descriptor&0x8000)!=0?0x80:0);
        return (pattern&0xFF)|(g<<8);
    }
}
