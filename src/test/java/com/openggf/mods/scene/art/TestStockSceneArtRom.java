package com.openggf.mods.scene.art;

import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestStockSceneArtRom {
    @Test void namedRecipesRetainEveryRawRequestFrameAndPalettePixel() throws Exception {
        var rom = SceneRomArtFactory.create(GameServices.rom().getRom(), GameId.S3K, () -> null, null);
        assertEquals(StockSceneArt.S3K_RING.romSha1(), rom.romSha1());
        int[] palette = new PaletteAssembly().rom(rom, 0x0A8A3C, 0,16).rom(rom,0x0A8B7C,16,48).build();
        try (SceneArtCache cache = new SceneArtCache(rom,32)) {
            for (StockSceneArt recipe : StockSceneArt.values()) {
                var raw = rom.sprites(recipe.request(rom),palette);
                var named = cache.sprites(recipe,palette);
                assertSame(named,cache.sprites(recipe,palette));
                assertEquals(raw.frameCount(),named.frameCount(),recipe.name());
                if (recipe == StockSceneArt.S3K_BLUE_FLICKY) {
                    // Map_Animals1 has exactly three pointers, with frame 2 first in data.
                    assertEquals(3, named.frameCount());
                    assertEquals(16, named.frame(0).width());
                    assertEquals(16, named.frame(0).height());
                    assertEquals(24, named.frame(2).height());
                }
                if (recipe == StockSceneArt.S3K_DASH_DUST) {
                    // Map_DashDust has 30 pointers; DashDust_Load_DPLC uses player-layout cues.
                    assertEquals(30, named.frameCount());
                    assertEquals(32, named.frame(0x0A).width());
                    assertEquals(-4, named.frame(0x0A).originY());
                    assertEquals(16, named.frame(0x11).width());
                    assertEquals(8, named.frame(0x11).originY());
                    for (int index = 0x0A; index <= 0x14; index++) {
                        assertTrue(java.util.Arrays.stream(named.frame(index).image().pixels())
                                .anyMatch(pixel -> (pixel >>> 24) != 0), "dust frame " + index);
                    }
                }
                for(int index=0;index<raw.frameCount();index++) {
                    SceneSprite a=raw.frame(index),b=named.frame(index);
                    assertEquals(a.originX(),b.originX()); assertEquals(a.originY(),b.originY());
                    assertEquals(a.width(),b.width()); assertEquals(a.height(),b.height());
                    for(int y=0;y<a.height();y++) for(int x=0;x<a.width();x++)
                        assertEquals(a.image().pixel(x,y),b.image().pixel(x,y),recipe.name()+" frame "+index);
                }
            }
        }
    }
}
