package com.openggf.mods.scene.art;

import com.openggf.mods.scene.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSceneArtHelpers {
    private final List<RomSpriteRequest> loads = new ArrayList<>();
    private final SceneSpriteSet set = new SceneSpriteSet() {
        public int frameCount() { return 4; }
        public SceneSprite frame(int frame) { return SceneSprite.of(new SceneImage(1, 1, new int[]{frame})); }
        public int[] animationFrames(int animation) { return new int[]{0,1,2}; }
        public int animationDelay(int animation) { return animation == 1 ? 256 : 3; }
    };

    private SceneRomArt rom(String hash) {
        return (SceneRomArt) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SceneRomArt.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "gameId" -> "s3k";
                    case "romSha1" -> hash;
                    case "sprites" -> { loads.add((RomSpriteRequest) arguments[0]); yield set; }
                    case "palette" -> {
                        int[] colors = new int[(int) arguments[1]];
                        for (int index = 0; index < colors.length; index++) colors[index] = (int) arguments[0] + index;
                        yield colors;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test void recipesFailClosedForUnknownOrWrongRomIdentity() {
        assertThrows(IllegalArgumentException.class, () -> StockSceneArt.S3K_RING.request(rom("")));
        assertThrows(IllegalArgumentException.class, () -> StockSceneArt.S3K_RING.request(rom("a".repeat(40))));
        assertEquals(0x192AEE, StockSceneArt.S3K_RING.request(rom(StockSceneArt.S3K_RING.romSha1())).artAddress());
        assertTrue(loads.isEmpty());
    }

    @Test void cacheOwnsPaletteIdentityEvictsAtCapacityAndCloses() {
        SceneRomArt art = rom(StockSceneArt.S3K_RING.romSha1());
        SceneArtCache cache = new SceneArtCache(art, 1);
        int[] palette = new int[64];
        cache.sprites(StockSceneArt.S3K_RING, palette);
        palette[0] = 123;
        cache.sprites(StockSceneArt.S3K_RING, new int[64]);
        assertEquals(1, loads.size(), "mutating caller palette cannot rewrite the cache key");
        cache.sprites(StockSceneArt.S3K_RING, palette);
        assertEquals(2, loads.size());
        assertEquals(1, cache.size());
        cache.sprites(StockSceneArt.S3K_EGG_ROBO, palette);
        assertEquals(3, loads.size());
        cache.close(); cache.close();
        assertEquals(0, cache.size());
        assertThrows(IllegalStateException.class, () -> cache.sprites(StockSceneArt.S3K_RING, palette));
        assertThrows(IllegalArgumentException.class, () -> new SceneArtCache(art, 129));
    }

    @Test void palettePlacementAndBuildAreIndependentAndBounded() {
        PaletteAssembly palette = new PaletteAssembly().rom(rom(""), 100, 0, 16).rom(rom(""), 200, 16, 48);
        assertEquals(100, palette.build()[0]);
        assertEquals(247, palette.build()[63]);
        int[] first = palette.build(); first[16] = -1;
        assertEquals(200, palette.build()[16]);
        assertThrows(IllegalArgumentException.class, () -> palette.rom(rom(""), 0, 63, 2));
        assertThrows(IllegalArgumentException.class, () -> palette.line(4, new int[16]));
    }

    @Test void animationPoliciesKeepRomAndAuthoredTimingExplicitIncludingNegativeTime() {
        assertEquals(1, AnimationSampling.frame(set, 0, 3, AnimationSampling.Timing.rom(4)).image().pixel(0,0));
        assertEquals(0, AnimationSampling.frame(set, 0, 3, new AnimationSampling.Timing(0,30,4,1)).image().pixel(0,0));
        assertEquals(1, AnimationSampling.frame(set, 1, 4, AnimationSampling.Timing.rom(4)).image().pixel(0,0));
        assertEquals(2, AnimationSampling.frame(set, 0, -1, AnimationSampling.Timing.fixed(4)).image().pixel(0,0));
        assertEquals(0, AnimationSampling.still(set, 0).image().pixel(0,0));
        assertThrows(IllegalArgumentException.class, () -> AnimationSampling.Timing.fixed(0));
    }

    @Test void anchorsRespectSpriteOriginsScalesAndMirroring() {
        List<float[]> positions = new ArrayList<>();
        SceneCanvas canvas = (SceneCanvas) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SceneCanvas.class},
                (proxy, method, arguments) -> { positions.add(new float[]{(float)arguments[1], (float)arguments[2]}); return null; });
        SceneSprite sprite = new SceneSprite(new SceneImage(10, 20, new int[200]), 3, 12);
        SpriteAnchors.feet(canvas, sprite, 50, 100, SceneDraw.plain().withScale(2));
        assertArrayEquals(new float[]{50,84}, positions.get(0));
        SpriteAnchors.centre(canvas, sprite, 50, 100, SceneDraw.plain().withScale(2).withFlipX(true));
        assertArrayEquals(new float[]{54,104}, positions.get(1));
        SpriteAnchors.centre(canvas, sprite, 50, 100, SceneDraw.plain().withScale(2).withFlipX(true).withFlipY(true));
        assertArrayEquals(new float[]{54,96}, positions.get(2));
        SpriteAnchors.feet(canvas, sprite, 50, 100, SceneDraw.plain().withScale(2).withFlipY(true));
        assertArrayEquals(new float[]{50,76}, positions.get(3));
    }

    @Test void objectAnimationCommandsAndCapturedStateMatchNativeTimerCadence() {
        RomAnimationPlayer player = new RomAnimationPlayer(new byte[]{0,2,1,0,1,(byte)0xFF}, 0);
        List<Integer> frames = new ArrayList<>();
        for (int tick=0; tick<8; tick++) { player.tick(); frames.add(player.frame()); }
        assertEquals(List.of(0,0,1,1,0,0,1,1), frames);
        var state = player.capture();
        player.tick(); var next = player.capture();
        player.restore(state); player.tick();
        assertEquals(next, player.capture());
        RomAnimationPlayer back = new RomAnimationPlayer(new byte[]{0,2,0,0,1,2,(byte)0xFE,1}, 0);
        frames.clear(); for(int tick=0;tick<7;tick++) {back.tick();frames.add(back.frame());}
        assertEquals(List.of(0,1,2,2,2,2,2), frames);
        RomAnimationPlayer switcher = new RomAnimationPlayer(new byte[]{0,4,0,8,0,3,(byte)0xFD,1,0,4,(byte)0xFC}, 0);
        switcher.tick(); switcher.tick(); assertEquals(1, switcher.animation());
        switcher.tick(); assertEquals(4, switcher.frame());
        switcher.tick(); assertTrue(switcher.advanced());
        switcher.clearAdvanced(); assertFalse(switcher.advanced());
        assertThrows(IllegalArgumentException.class, () -> new RomAnimationPlayer(new byte[]{0,99,0,1},0));
        RomAnimationPlayer invalid = new RomAnimationPlayer(new byte[]{0,2,0,(byte)0xFE,2},0);
        assertThrows(IllegalArgumentException.class, invalid::tick);
    }
}
