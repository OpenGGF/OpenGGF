package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.common.CommonSpriteDataLoader;
import com.openggf.game.soniccd.level.CdR8DataLayout;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.mmz.MmzPohBee;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.render.SpriteMappingRasterizer;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledOnOs(OS.LINUX)
class TestMmzPohBeePicture {
    @Test
    void placedPohBeeMatchesTheSpriteFromTheDisc() throws Exception {
        var programme = CdR8DataLayout.Programme.R81A;
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, 0x1200, 0x660);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var session = new GameplayCaptureSession(settings)) {
            var config = GameServices.configuration();
            boolean previous = config.getBoolean(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT);
            config.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            try {
                session.boot(disc, new SonicCdGameModule(), 6, 0, settings);
                session.step(null);
                var level = GameServices.level();
                var body = level.getObjectManager().getActiveObjects().stream()
                        .filter(MmzPohBee.class::isInstance).map(MmzPohBee.class::cast)
                        .filter(o -> o.getSpawn().x() == 0x11B9).findFirst().orElseThrow();

                // SectionGfxInitList3 (r8/gfx_lists_1a.asm:98-112): PLC 6 puts the art at VRAM $8AA0 (tile $455).
                var stage = new CdStageImage(disc.readFile(programme.file()));
                var vram = new CdStageVram();
                vram.loadPlc(stage, CdStageCatalogue.entry(programme.payload()).plcLists(), 6);
                // PohBeeSprites (objects/r8/poh_bee.asm:247-251): the second mapping table follows the first by $14 bytes.
                int table = programme.range("PohBeeSprites").fileOffset() + 0x14;
                var frame = CommonSpriteDataLoader.loadVer1MappingFramesWithSignedOffsets(stage.reader(), table, 4).getFirst();
                int[] palette = new int[64];
                for (int i = 0; i < palette.length; i++) {
                    var color = level.getCurrentLevel().getPalette(i / 16).getColor(i % 16);
                    palette[i] = 0xFF000000 | ((color.r & 0xFF) << 16) | ((color.g & 0xFF) << 8) | (color.b & 0xFF);
                }
                var expected = SpriteMappingRasterizer.rasterize(frame, vram.window(0x455, 0x33), palette, 1, 0);
                // Isolate the placed sprite so the stage's other actors cannot exhaust the SAT budget.
                for (var object : level.getObjectManager().getActiveObjects()) {
                    if (object instanceof AbstractObjectInstance actor && actor != body) actor.setDestroyed(true);
                }
                session.step(null);
                var actual = session.render();
                int originX = body.getX() - GameServices.camera().getX();
                int originY = body.getY() - GameServices.camera().getY();
                int compared = 0, different = 0;
                for (int y = 0; y < expected.originY(); y++) {
                    for (int x = 0; x < expected.width(); x++) {
                        int color = expected.argb()[y * expected.width() + x];
                        if (color == 0) continue;
                        int actualX = originX + expected.originX() - x - 1;
                        int actualY = originY + y - expected.originY();
                        if (TestMmzObjectsPictures.level(color) != TestMmzObjectsPictures.level(actual.argb(actualX, actualY))) {
                            different++;
                        }
                        compared++;
                    }
                }
                assertEquals(List.of(true, 0), List.of(compared > 100, different),
                        "opaque upper-body pixels compared=" + compared);
            } finally {
                config.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, previous);
            }
        }
    }
}
