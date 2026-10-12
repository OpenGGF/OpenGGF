package com.openggf.mods.scene.art;

import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.RomSpriteRequest;
import static com.openggf.mods.scene.RomSpriteRequest.Compression.*;
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
                // Original independently reused example facts, rather than a recipe compared to itself.
                RomSpriteRequest original = switch(recipe) {
                    case S3K_MONITOR -> RomSpriteRequest.of(0x190F4A,NEMESIS,0x01DBA2,0);
                    case S3K_AIZ_ROCK -> RomSpriteRequest.of(0x38DC90,NEMESIS,0x21DCDC,1);
                    case S3K_BLOOMINATOR -> RomSpriteRequest.of(0x367DCA,KOSINSKI_MODULED,0x3616C0,1);
                    case S3K_RING -> RomSpriteRequest.of(0x192AEE,NEMESIS,0x01A99A,1);
                    case S3K_ROBOTNIK_SHIP -> RomSpriteRequest.of(0x0D771E,NEMESIS,0x06820C,0);
                    case S3K_EGG_CAPSULE -> RomSpriteRequest.of(0x0DD990,NEMESIS,0x086BFC,0);
                    case S3K_EXPLOSION -> RomSpriteRequest.of(0x19200A,NEMESIS,0x01E758,0);
                    case S3K_EGG_ROBO -> RomSpriteRequest.of(0x17B17E,KOSINSKI_MODULED,0x184F34,0);
                    case S3K_MONKEY_DUDE -> RomSpriteRequest.of(0x36800C,KOSINSKI_MODULED,0x361776,1);
                    case S3K_BUGGERNAUT -> RomSpriteRequest.of(0x36A3E0,NEMESIS,0x360EB4,1);
                    case S3K_TURBO_SPIKER -> RomSpriteRequest.of(0x36A968,KOSINSKI_MODULED,0x361212,1);
                    case S3K_SNALE_BLASTER -> RomSpriteRequest.of(0x377996,KOSINSKI_MODULED,0x360400,1);
                    case S3K_ORBINAUT -> RomSpriteRequest.of(0x377D1A,KOSINSKI_MODULED,0x3604A4,1);
                    default -> recipe.request(rom);
                };
                assertEquals(original,recipe.request(rom),recipe.name());
                var raw = rom.sprites(original,palette);
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
