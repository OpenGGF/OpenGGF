package com.openggf.mods.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link SceneBackdrop}'s band contract and scroll arithmetic, without a ROM. */
class TestSceneBackdrop {
    private static SceneImage image(int width, int height) {
        return new SceneImage(width, height, new int[width * height]);
    }

    @Test
    void bandsMustCoverTheImageInOrder() {
        SceneImage image = image(8, 10);
        new SceneBackdrop(image, List.of(new SceneBackdrop.Band(0, 4, 0.5, 0), new SceneBackdrop.Band(4, 6, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop(image,
                List.of(new SceneBackdrop.Band(0, 4, 0.5, 0))), "short of the bottom");
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop(image,
                List.of(new SceneBackdrop.Band(0, 4, 0.5, 0), new SceneBackdrop.Band(5, 5, 1, 0))), "gap");
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop(image,
                List.of(new SceneBackdrop.Band(0, 6, 0.5, 0), new SceneBackdrop.Band(4, 6, 1, 0))), "overlap");
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop.Band(0, 0, 1, 0), "empty band");
        assertThrows(IllegalArgumentException.class, () -> new SceneBackdrop.Band(0, 1, Double.NaN, 0), "NaN");
    }

    @Test
    void bandListIsCopied() {
        List<SceneBackdrop.Band> bands = new ArrayList<>(List.of(new SceneBackdrop.Band(0, 2, 1, 0)));
        SceneBackdrop backdrop = new SceneBackdrop(image(4, 2), bands);
        bands.clear();
        assertEquals(1, backdrop.bands().size());
        assertThrows(UnsupportedOperationException.class, () -> backdrop.bands().clear());
    }

    @Test
    void columnScrollsDriftsAndWraps() {
        SceneBackdrop backdrop = new SceneBackdrop(image(512, 2), List.of(new SceneBackdrop.Band(0, 2, 0.75, 0)));
        SceneBackdrop.Band band = backdrop.bands().get(0);
        assertEquals(0, backdrop.column(band, 0, 0));
        assertEquals(75, backdrop.column(band, 100, 0));
        assertEquals(512 - 75, backdrop.column(band, -100, 0), "scrolling left wraps from the right edge");
        assertEquals(76, backdrop.column(band, 101.5, 0), "rounds down");
        assertEquals(2250 % 512, backdrop.column(band, 3000, 0), "wraps at the image width");
        SceneBackdrop.Band clouds = new SceneBackdrop.Band(0, 2, 11 / 64.0, 0.75);
        assertEquals(Math.floorMod((long) Math.floor(640 * 11 / 64.0 + 0.75 * 333), 512L),
                backdrop.column(clouds, 640, 333));
        assertEquals(Math.floorMod((long) Math.floor(0.75 * 9_000_000L), 512L),
                backdrop.column(clouds, 0, 9_000_000L), "long scenes keep exact positions");
    }

    @Test
    void romArtWithoutBackdropsReturnsNull() {
        SceneRomArt plain = new SceneRomArt() {
            @Override
            public String gameId() {
                return "s3k";
            }

            @Override
            public byte[] read(int address, int length) {
                return new byte[length];
            }

            @Override
            public int[] palette(int address, int colors) {
                return new int[colors];
            }

            @Override
            public SceneSpriteSet sprites(RomSpriteRequest request, int[] palette) {
                return null;
            }

            @Override
            public SceneSpriteSet character(String characterCode) {
                return null;
            }

            @Override
            public SceneSpriteSet characterAccessory(String characterCode) {
                return null;
            }

            @Override
            public int[] characterPalette(String characterCode) {
                return new int[16];
            }
        };
        assertNull(plain.zoneBackdrop(0, 0), "the default keeps existing implementors compiling and unsupported");
    }
}
