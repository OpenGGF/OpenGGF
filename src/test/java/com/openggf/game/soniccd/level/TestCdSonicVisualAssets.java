package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.openggf.game.soniccd.level.TestCdR11aTerrain.word;
import static org.junit.jupiter.api.Assertions.*;

class TestCdSonicVisualAssets {
    @Test
    void explicit188FormatOwnsItsByteEndingMappingAndFullNativeReadView() {
        byte[] maps = new byte[377], dplc = new byte[380], art = new byte[82944];
        for (int i = 0; i < 188; i++) {
            word(maps, i*2, 376); word(dplc, i*2, 376);
        }
        word(dplc, 376, 1); word(dplc, 378, 0x963); // last actual R4 tile, no EVEN byte.
        var v = CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_188, maps, dplc, art);
        assertEquals(188, v.mappingFrames().size());
        assertEquals(188, v.dplcFrames().size());
        assertEquals(76928, v.rawArt().length());
        assertEquals(82944, v.artReadView().length());
        assertEquals(76896, v.dplcFrames().getFirst().pieces().getFirst().artByteOffset());
        assertThrows(IllegalStateException.class, v::alignmentByte);
        word(dplc, 378, 0xA1F); // source B3/B4 read after raw art, through the exact finite endpoint.
        art[82943] = 42;
        var tail = CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_188, maps, dplc, art);
        assertEquals(42, tail.artPiece(0,0).unsignedByte(31));
        word(dplc, 378, 0xA20);
        assertThrows(IllegalArgumentException.class,
                () -> CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_188, maps, dplc, art));
        word(dplc, 378, 0x963);
        word(maps, 0, 374); // inside the 188-WORD directory.
        assertThrows(IllegalArgumentException.class,
                () -> CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_188, maps, dplc, art));
        word(maps, 0, 376);
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(maps, dplc, art));
        assertThrows(IllegalArgumentException.class,
                () -> CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_167, maps, dplc, art));
    }

    @Test
    void dplcCanReadTheActualSingleEvenByteBeyondTheRawSourceBlob() {
        Fixture f = new Fixture();
        word(f.dplc, 396, 1);
        word(f.dplc, 398, 0x087A); // last of 2171 art tiles.
        f.art[69470] = 42;
        var data = f.decode();
        assertEquals(198, data.mappingFrames().size());
        assertEquals(198, data.dplcFrames().size());
        assertEquals(69471, data.rawArt().length());
        assertEquals(69472, data.artReadView().length());
        assertEquals(0, data.alignmentByte());
        var piece = data.dplcFrames().get(30).pieces().getFirst();
        assertEquals(69440, piece.artByteOffset());
        assertEquals(1, piece.tileCount());
        assertEquals(32, data.dplcFrames().get(30).outputBytes());
        assertEquals(42, data.artPiece(30, 0).unsignedByte(30));
        assertEquals(0, data.artPiece(30, 0).unsignedByte(31));
        Arrays.fill(f.art, (byte) 99);
        Arrays.fill(f.mapping, (byte) 99);
        Arrays.fill(f.dplc, (byte) 99);
        assertEquals(0, data.alignmentByte());
        assertEquals(0, data.mappingFrames().getFirst().pieceCount());
        assertEquals(32, data.dplcFrames().get(30).outputBytes());
        assertThrows(UnsupportedOperationException.class, () -> data.dplcFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.dplcFrames().getFirst().pieces().clear());
    }

    @Test
    void missingOrInventedAlignmentAndReadsOutsideTheActualViewAreRejected() {
        Fixture f = new Fixture();
        f.art[69471] = 1;
        assertThrows(IllegalArgumentException.class, f::decode);
        f.art[69471] = 0;
        assertThrows(IllegalArgumentException.class,
                () -> CdSonicVisualAssets.decode(f.mapping, f.dplc, Arrays.copyOf(f.art, 69471)));
        word(f.dplc, 396, 1);
        word(f.dplc, 398, 0x087B);
        assertThrows(IllegalArgumentException.class, f::decode);
    }

    @Test
    void relativeIndexesFrameBodiesAndNativeArtBufferAreBounded() {
        Fixture f = new Fixture();
        word(f.mapping, 0, 394);
        assertThrows(IllegalArgumentException.class, f::decode);
        word(f.mapping, 0, 396);
        f.mapping[396] = 1;
        assertThrows(IllegalArgumentException.class, f::decode);
        f.mapping[396] = 0;
        word(f.dplc, 0, 0xFFFE);
        assertThrows(IllegalArgumentException.class, f::decode);
        word(f.dplc, 0, 396);
        word(f.dplc, 396, 128);
        assertThrows(IllegalArgumentException.class, f::decode);
        byte[] tooLarge = Arrays.copyOf(f.dplc, 402);
        word(tooLarge, 396, 2);
        word(tooLarge, 398, 0xF000);
        word(tooLarge, 400, 0xF010);
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(f.mapping, tooLarge, f.art));
    }

    private static final class Fixture {
        final byte[] mapping = new byte[398];
        final byte[] dplc = new byte[400];
        final byte[] art = new byte[69472];

        Fixture() {
            for (int i = 0; i < 198; i++) {
                word(mapping, i * 2, 396);
                word(dplc, i * 2, 396);
            }
        }
        CdSonicVisualAssets decode() { return CdSonicVisualAssets.decode(mapping, dplc, art); }
    }
}
