package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CommonObjectArt;
import com.openggf.game.soniccd.objects.ppz.PpzObjectArt;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.*;

/** Representative production-renderer art plate, not a native frame-parity certification. */
@EnabledOnOs(OS.LINUX)
class TestSonicCdObjectFixPictures {
    @Test
    void correctedDiscArtRendersWithBoundedMonitorItems() throws Exception {
        var config = GameServices.configuration();
        boolean oldAudio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(), SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A),
                    SonicCdZoneRegistry.actIndex(CdStagePayload.R11A));
            for (int i = 0; i < 200; i++) loop.step(); // Complete the native level-entry palette fade.
            var provider = (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
            int x = GameServices.camera().getX(), y = GameServices.camera().getY();
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            provider.getRenderer(CommonObjectArt.FLOWER).drawFrameIndex(7, x + 40, y + 70, false, false, 1);
            int springTile = provider.objectTileId(CdObjectArtProvider.TILE_ID_DIAGONAL_SPRING, 0);
            provider.getRenderer(CdObjectArtProvider.diagonalSpringKey(springTile))
                    .drawFrameIndex(0, x + 100, y + 70, false, false, 1);
            for (int item = 0; item < 8; item++) {
                provider.getRenderer(CdObjectArtProvider.MONITOR_ITEM)
                        .drawFrameIndex(item, x + 30 + item * 34, y + 140, false, false, 0);
            }
            int tile = CommonObjectArt.robotGeneratorTile(provider.image(), CdStagePayload.R11A,
                    provider.entry().offset("robotGeneratorBaseTiles"));
            provider.getRenderer(PpzObjectArt.key(provider, PpzObjectArt.PPZ_GENERATOR, tile, 0x2000))
                    .drawFrameIndex(2, x + 230, y + 65, false, false, CdObjectArtProvider.tilePalette(tile));
            GameServices.graphics().flush();
            glFinish();
            var picture = ScreenshotCapture.captureFramebuffer(320, 224);
            Path out = Path.of("target", "soniccd-object-fixes", "disc-art.png");
            Files.createDirectories(out.getParent());
            ScreenshotCapture.savePNG(picture, out);
            int visible = 0;
            for (int py = 0; py < 224; py++) for (int px = 0; px < 320; px++) {
                if ((picture.argb(px, py) & 0xFFFFFF) != 0) visible++;
            }
            assertTrue(visible > 100, "the production renderer must submit visible disc art");
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
    }
}
