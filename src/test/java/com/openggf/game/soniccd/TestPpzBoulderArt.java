package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdStageLoader;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CommonObjectArt;
import com.openggf.game.soniccd.objects.ppz.PpzObjectArt;
import com.openggf.level.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Disc-backed boulder pixels; no disassembly files or native captures needed at test time. */
class TestPpzBoulderArt {
    @ParameterizedTest
    @EnumSource(value = CdStagePayload.class, names = "R1.*", mode = EnumSource.Mode.MATCH_ALL)
    void firstSectionUsesBoulderPixelsInsteadOfTitleCardTiles(CdStagePayload payload) throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var loader = new CdStageLoader(disc);
            var image = loader.readStage(payload);
            var level = loader.load(payload, image, SonicCdZoneRegistry.zoneIndex(payload), false);
            var art = new CdObjectArtProvider(() -> level, () -> image);
            art.loadArtForZone(SonicCdZoneRegistry.zoneIndex(payload));
            int tile = art.objectTileId(CommonObjectArt.TILE_ID_BOULDER, 0);
            String key = PpzObjectArt.key(art, "boulder", tile, 0);
            // Before the fix, the only boulder sheet came from immediate PLC 1 (title-card tiles).
            var sheet = art.getSheet(key == null ? CommonObjectArt.boulderKey(tile) : key);
            assertNotNull(sheet);
            assertEquals(19, sheet.getPatterns().length, "two mapping pieces: 1x3 + 4x4 tiles");
            byte[] packed = new byte[19 * 32];
            Pattern[] patterns = sheet.getPatterns();
            for (int t = 0; t < patterns.length; t++) {
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x += 2) {
                        packed[t * 32 + y * 4 + x / 2] = (byte) (
                                patterns[t].getPixel(x, y) << 4 | patterns[t].getPixel(x + 1, y));
                    }
                }
            }
            // SHA-256 of the independently decoded Art_Boulder (608 bytes, 19 tiles),
            // researched from Level/_Objects/Boulder/Data/Art.nem; not an engine-output golden.
            assertEquals("abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packed)));
            assertNotNull(key, "the zone must register a section-aware boulder sheet");
        }
    }
}
