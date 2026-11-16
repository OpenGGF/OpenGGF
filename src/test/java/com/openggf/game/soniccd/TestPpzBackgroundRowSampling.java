package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdStageLoader;
import com.openggf.game.soniccd.zones.ppz.PpzBackgroundRows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPpzBackgroundRowSampling {
    @Test
    void farCameraCacheCoversEveryVisibleSourceColumn() {
        for (var payload : CdPeriod.values()) {
            for (int y : new int[] {479, 480, 481, 511, 512, 513, 639, 640, 641}) {
                var handler = new com.openggf.game.soniccd.zones.ppz.PpzScrollHandler(payload,
                        new com.openggf.game.soniccd.zones.ppz.PpzScrollHandler.PlayerView() {
                            public int centreX() { return 9499; }
                            public int xSpeed() { return 0; }
                            public boolean on3dRamp() { return false; }
                        });
                int[] words = new int[224];
                handler.init(0, 9340, y);
                handler.update(words, 9340, y, 1, 0);
                int width = handler.getBgPeriodWidth();
                for (int line = 0; line < words.length; line++) {
                    org.junit.jupiter.api.Assertions.assertTrue(-(short) words[line] + 320 <= width,
                            payload + " y=" + y + " line=" + line + " cache too narrow: " + width);
                }
            }
        }
    }

    @Test
    void staticRowsKeepTheirOwnPeriodBesideStreamingRowsInEveryVariant() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var loader = new CdStageLoader(disc);
            for (CdStagePayload payload : CdStagePayload.values()) {
                if (payload.stage().zone() != CdZone.PALMTREE_PANIC) continue;
                var rows = PpzBackgroundRows.read(loader.readStage(payload));
                // Independent expected boundaries from BGCameraSectIDs: Act 1's first moving
                // mountain starts at $460 (Present), $40 (Past), $60 (Good), $70 (Bad).
                // Act 2 Present starts at $60; Past/Good at $80 and Bad at $70.
                int movingY = payload.stage().actNumber() == 1 ? switch (payload.stage().period()) {
                    case PRESENT -> 0x460;
                    case PAST -> 0x40;
                    case GOOD_FUTURE -> 0x60;
                    case BAD_FUTURE -> 0x70;
                } : payload.stage().period() == CdPeriod.PRESENT ? 0x60
                        : payload.stage().period() == CdPeriod.BAD_FUTURE ? 0x70 : 0x80;
                for (int x : new int[] {-513, -1, 511, 512, 9499}) {
                    assertEquals(Math.floorMod(x, 512), rows.layoutX(x, 0), payload + " clouds");
                    assertEquals(x, rows.layoutX(x, movingY), payload + " moving mountains");
                }
            }
        }
    }

    @Test
    void farCameraBackgroundAndBottomRowsMatchDiscLayoutAcrossVerticalThresholds() throws Exception {
        var config = com.openggf.game.GameServices.configuration();
        boolean audio = config.getBoolean(com.openggf.configuration.SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.AUDIO_ENABLED, false);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            for (var payload : new CdStagePayload[] {CdStagePayload.R11A, CdStagePayload.R12A}) {
                try (var boot = new com.openggf.tools.HeadlessGameBoot(320, 224)) {
                    var loop = boot.boot(disc, new SonicCdGameModule(), 0, SonicCdZoneRegistry.actIndex(payload));
                    var camera = com.openggf.game.GameServices.camera();
                    camera.setFrozen(true);
                    // Follow bounded per-frame deltas: scrollYDiff is a signed 8.8 word.
                    for (int x = camera.getX(); x < 9340; x += 16) {
                        camera.setX((short) x);
                        loop.step();
                    }
                    camera.setX((short) 9340);
                    for (int y = camera.getY(); y > 400; y -= 16) {
                        camera.setY((short) y);
                        loop.step();
                    }
                    int checked = 0;
                    for (int y = 400; y <= 720; y++) {
                        camera.setY((short) y);
                        loop.step();
                        if (y == 479 || y == 480 || y == 481 || y == 511 || y == 512 || y == 513 || y == 639 || y == 640 || y == 641 || y == 720) {
                            checked += verifyPicture(payload, y);
                        }
                    }
                    org.junit.jupiter.api.Assertions.assertTrue(checked > 400,
                            payload + " too little exposed background across the sweep: " + checked);
                    System.out.println(payload + " camera threshold sweep: " + checked + " exact background pixels");
                }
            }
        } finally {
            config.setSessionOverride(com.openggf.configuration.SonicConfiguration.AUDIO_ENABLED, audio);
        }
    }

    private static int verifyPicture(CdStagePayload payload, int cameraY) throws Exception {
        var manager = com.openggf.game.GameServices.level();
        var level = (com.openggf.game.soniccd.level.CdLevel) manager.getCurrentLevel();
        var graphics = com.openggf.game.GameServices.graphics();
        graphics.setDisplayColorProfile(com.openggf.graphics.color.DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.FULL);
        manager.setClearColor();
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
        manager.drawWithSpritePriority(com.openggf.game.GameServices.sprites(), false);
        graphics.flush();
        org.lwjgl.opengl.GL11.glFinish();
        var image = com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320, 224);
        var parallax = com.openggf.game.GameServices.parallax();
        int[] scroll = parallax.getHScroll();
        int bgY = parallax.getVscrollFactorBG();
        int fgY = parallax.getVscrollFactorFG();
        int bgWidth = level.getLayerWidthBlocks(1) * level.getBlockPixelSize();
        int bgHeight = level.getLayerHeightBlocks(1) * level.getBlockPixelSize();
        com.openggf.level.render.PlaneRasterizer.TileSource tiles = (layer, index, x) -> level.getPattern(index);
        int checked = 0;
        for (int y = 0; y < 224; y++) {
            for (int x : new int[] {20, 100, 220, 300}) {
                int fg = com.openggf.level.render.PlaneRasterizer.rasterize(level, tiles, 0,
                        x - (short) (scroll[y] >>> 16), Math.floorMod(y + fgY, 2048), 1, 1, 0)[0];
                if (fg != 0) continue;
                int sourceY = Math.floorMod(y + bgY, bgHeight);
                int sourceX = Math.floorMod(level.backgroundLayoutX(x - (short) scroll[y], sourceY), bgWidth);
                int expected = com.openggf.level.render.PlaneRasterizer.rasterize(level, tiles, 1,
                        sourceX, sourceY, 1, 1, 0)[0];
                if (expected == 0) continue;
                assertEquals(TestPpzR11aBackgroundPicture.level(expected),
                        TestPpzR11aBackgroundPicture.level(image.argb(x, y)),
                        payload + " cameraY=" + cameraY + " pixel=" + x + "," + y);
                checked++;
            }
        }
        assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR, org.lwjgl.opengl.GL11.glGetError());
        var out = java.nio.file.Path.of("target/ppz-camera-thresholds");
        java.nio.file.Files.createDirectories(out);
        com.openggf.graphics.ScreenshotCapture.savePNG(image, out.resolve(payload + "-y" + cameraY + ".png"));
        return checked;
    }
}
