package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The art behind SKL {@code $58}, {@code Obj_DEZGravitySwitch}.
 *
 * <p>The object header (sonic3k.asm:94800-94807) names three things the engine has to
 * agree with before the pad can be drawn at all: {@code Map_DEZGravitySwitch} at ROM
 * {@code $48BEA} (sonic3k.lst:112040), the art tile
 * {@code make_art_tile(ArtTile_DEZMisc+$143,1,0)} — so palette line 1, based on the same
 * {@code ArtTile_DEZMisc} block the Death Egg door already draws from — and two mapping
 * frames, the second being the pressed pose.
 *
 * <p>The pad was solid and functional but invisible until this art was registered, which
 * is why the registration is pinned here rather than left to the clip: a clip of an
 * invisible object and a clip of an object the camera never reached look the same.
 */
class TestS3kDezGravitySwitchArt {

    /** Death Egg is zone {@code $0B}; the pads are act 2 objects but the art is per zone. */
    private static final int DEZ_ZONE_INDEX = 0x0B;

    @Test
    void theGravitySwitchArtIsRegisteredForBothDeathEggActs() {
        for (int act = 0; act <= 1; act++) {
            final int actIndex = act;
            Sonic3kPlcArtRegistry.LevelArtEntry entry =
                    Sonic3kPlcArtRegistry.getPlan(DEZ_ZONE_INDEX, actIndex).levelArt().stream()
                            .filter(e -> e.key().equals(Sonic3kObjectArtKeys.DEZ_GRAVITY_SWITCH))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(
                                    "no DEZ gravity switch art in act " + (actIndex + 1)));
            assertEquals(Sonic3kConstants.MAP_DEZ_GRAVITY_SWITCH_ADDR, entry.mappingAddr(),
                    "Map_DEZGravitySwitch, sonic3k.lst:112040");
            assertEquals(Sonic3kConstants.ARTTILE_DEZ_MISC + 0x143, entry.artTileBase(),
                    "make_art_tile(ArtTile_DEZMisc+$143,1,0), sonic3k.asm:94802");
            assertEquals(1, entry.palette(), "the same art_tile word's palette field");
            assertEquals(2, entry.mappingFrameCount(),
                    "word_48BEE (armed) and word_48C08 (pressed)");
        }
    }

    /**
     * The shape the ROM mapping actually has, decoded from the cartridge rather than from
     * the registry: frame 0 is four 16x8 pieces making a 32x16 pad, frame 1 drops the lower
     * row for the two-piece pressed pose, and the highest tile either frame reaches is
     * {@code +2} from the base.
     */
    @Test
    void theGravitySwitchMappingHasTheRomsTwoFrameShape() throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists());
        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()));
            List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(
                    RomByteReader.fromRom(rom),
                    Sonic3kConstants.MAP_DEZ_GRAVITY_SWITCH_ADDR, 2);

            assertArrayEquals(new int[] {4, 2},
                    frames.stream().mapToInt(frame -> frame.pieces().size()).toArray(),
                    "word_48BEE has dc.w 4 pieces, word_48C08 has dc.w 2");
            assertEquals(2, frames.stream().flatMap(frame -> frame.pieces().stream())
                            .mapToInt(piece -> piece.tileIndex() & 0x7FF).max().orElseThrow(),
                    "the highest tile word in either frame is $0802 & $7FF");

            // Frame 0's four pieces: y = -8 and 0, x = -16 and 0 (:48BF0-:48C02).
            int[] ys = frames.get(0).pieces().stream()
                    .mapToInt(SpriteMappingPiece::yOffset).sorted().distinct().toArray();
            int[] xs = frames.get(0).pieces().stream()
                    .mapToInt(SpriteMappingPiece::xOffset).sorted().distinct().toArray();
            assertArrayEquals(new int[] {-8, 0}, ys, "dc.b $F8 and dc.b 0 rows");
            assertArrayEquals(new int[] {-16, 0}, xs, "dc.b $FF,$F0 and dc.b 0,0 columns");
        }
    }
}
