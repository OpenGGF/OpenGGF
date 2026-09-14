package com.openggf.tools.fbzvisual;

import com.openggf.graphics.RgbaImage;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestFbzVisualDestinationPixels {
    @Test void resolvesPlacedDestinationFlipsPaletteAndOcclusionFromGpuBytes() {
        var spec = FbzVisualCadenceCapture.spec("fbz1-aniplc-208");
        byte[] lookup = new byte[528 * 4], atlas = new byte[64 * 8], palette = new byte[64 * 4];
        for (int tile = 0; tile < 8; tile++) lookup[(520 + tile) * 4] = (byte) tile;
        byte[] expectedPacked = new byte[8 * 32];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) atlas[y * 64 + x] = (byte) (1 + (x + y * 2) % 15);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x += 2)
            expectedPacked[y * 4 + x / 2] = (byte) (atlas[y * 64 + x] << 4 | atlas[y * 64 + x + 1]);
        for (int i = 0; i < 16; i++) palette[(16 + i) * 4] = (byte) (i * 16);
        RgbaImage image = new RgbaImage(8, 8, new int[64]);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            image.setArgb(x, y, 0xFF000000 | (atlas[(7 - y) * 64 + 7 - x] & 255) * 16 << 16);
        image.setArgb(3, 3, 0xFF000000); // occluding owner is not accepted as destination art
        Map<String, Object> uniforms = new HashMap<>();
        for (String key : new String[]{"PerLineScroll", "PerColumnVScroll", "WorldOffsetX", "WorldOffsetY", "TilemapRingBaseX", "TilemapRingBaseY", "WrapY"}) uniforms.put(key, 0);
        uniforms.put("TilemapWidth", 1); uniforms.put("TilemapHeight", 1); uniforms.put("AtlasWidth", 64);
        byte[] descriptors = new byte[]{8, 106, 0, -1}; // tile$208, palette1, both flips
        var textures = Map.of("foreground.rgba", descriptors, "lookup.rgba", lookup,
                "atlas.indexed", atlas, "palette.rgba", palette);
        var evidence = FbzVisualDestinationPixels.measure(spec, image, uniforms, textures);
        assertEquals(64, evidence.get("opaque_placed_pixels"));
        assertEquals(63, evidence.get("exact_matched_pixels"));
        assertEquals(FbzVisualPrebootVerifier.sha256(expectedPacked), evidence.get("gpu_destination_sha256"));
        var mask = (Map<?, ?>) evidence.get("source_pixel_mask");
        byte[] records = java.util.HexFormat.of().parseHex((String) mask.get("records_hex"));
        assertEquals(64 * 8, records.length);
        assertEquals(0x208, (records[4] & 255) * 256 + (records[5] & 255));
        assertEquals(128 | 63, records[6] & 255, "both flips map screen0,0 to source7,7");
        int occluded = (3 * 8 + 3) * 8;
        assertEquals(4 * 8 + 4, records[occluded + 6] & 255, "occlusion retains source coordinate without match bit");
        assertEquals(16 + (atlas[4 * 64 + 4] & 15), records[occluded + 7] & 255);
        descriptors[0] = 0; descriptors[1] = 0;
        assertEquals(0, FbzVisualDestinationPixels.measure(spec, image, uniforms, textures).get("opaque_placed_pixels"));
        uniforms.put("PerLineScroll", 1);
        assertThrows(IllegalStateException.class, () -> FbzVisualDestinationPixels.measure(spec, image, uniforms, textures));
    }
}
