package com.openggf.tools.fbzvisual;

import java.io.ByteArrayOutputStream;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Reconstructable actual-GPU source/pixel evidence from FBZ visual work, 2026-09-14.
 * Each eight-byte big-endian record is x:u16,y:u16,tile:u16,sample:u8,colour:u8.
 * sample bit7 is exact framebuffer match; bits0..5 are flipped tile y*8+x.
 * colour is palette*16+nibble. Transparent pixels are excluded, mismatches retained.
 */
final class FbzVisualSourcePixelMask {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    void add(int x, int y, int tile, int localX, int localY, int colour, boolean matched) {
        bytes.write(x >>> 8); bytes.write(x); bytes.write(y >>> 8); bytes.write(y);
        bytes.write(tile >>> 8); bytes.write(tile);
        bytes.write((matched ? 128 : 0) | localY * 8 + localX); bytes.write(colour);
    }
    Map<String, Object> evidence(byte[] palette) {
        List<Integer> rgb = new ArrayList<>();
        for (int p = 0; p < 64; p++) rgb.add((palette[p*4]&255)<<16
                | (palette[p*4+1]&255)<<8 | palette[p*4+2]&255);
        return Map.of("record_format", "be:x16,y16,tile16,match7+local_yx6,colour8",
                "records_hex", HexFormat.of().formatHex(bytes.toByteArray()),
                "uploaded_palette_rgb24", rgb);
    }
}
