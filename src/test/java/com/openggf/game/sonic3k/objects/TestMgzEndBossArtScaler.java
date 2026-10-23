package com.openggf.game.sonic3k.objects;

import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Literal MOVE/AND/OR and ADD/ADDX controls from skdisasm sub_246DA. */
class TestMgzEndBossArtScaler {
    private static final SpriteMappingFrame ROM_FRAME_FOUR = mappingGrid(2, 2, 4);
    private static final SpriteMappingFrame ROM_FRAME_MAX = new SpriteMappingFrame(List.of(
            new SpriteMappingPiece(0, 0, 2, 4, 0, false, false, 1)));

    @Test
    void modifiedEightMergesHighThenLowFromAdjacentBytes() {
        byte[] source = literalSource();
        put(source, 1, 0, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x12);
        Pattern[] output = scale(source, 4, ROM_FRAME_FOUR, 64);

        // loc_24984: HIGH(12)|LOW(34), HIGH(56)|LOW(78), ...
        assertPackedRow(output[0], 0, 0x14, 0x58, 0x9C, 0xD1);
        assertPackedRow(output[0], 1, 0x25, 0x69, 0xAD, 0xE2);
    }

    @Test
    void modifiedNineSkipsTheNinthByteAndCopiesOnlyTheLastHighNibble() {
        byte[] source = literalSource();
        put(source, 0, 63, 0xCD);
        fillRow(source, 1, 0x11);
        fillRow(source, 7, 0x77);
        fillRow(source, 8, 0x88);
        fillRow(source, 9, 0x99);
        Pattern[] output = scale(source, 5, ROM_FRAME_FOUR, 64);

        // loc_24906 consumes bytes0..7, skips byte8, then starts at byte9.
        assertPackedRow(output[0], 0, 0x14, 0x58, 0x9C, 0xD1);
        assertPackedRow(output[4], 0, 0x47, 0x8B, 0xCF, 0x10);
        assertPackedRow(output[28], 0, 0xC0, 0, 0, 0);
        assertPackedRow(output[0], 1, 0x11, 0x11, 0x11, 0x11);
        assertPackedRow(output[0], 7, 0x77, 0x77, 0x77, 0x77);
        // The eighth vertical $2000 addition carries: source row8 is skipped.
        assertPackedRow(output[1], 0, 0x99, 0x99, 0x99, 0x99);
    }

    @Test
    void modifiedTwelveCarriesTheByteIndexAndClearsOnlyThePartialTail() {
        byte[] source = literalSource();
        fillRow(source, 1, 0x22);
        fillRow(source, 3, 0x44);
        put(source, 0, 60, 0xAB, 0xCD, 0x12, 0xEF);
        Pattern[] output = scale(source, 8, ROM_FRAME_FOUR, 64);

        // loc_2479C: byte indices0,1,3,4,6,7,9,10 for modified scale12.
        assertPackedRow(output[0], 0, 0x14, 0x7A, 0xD1, 0x47);
        // Five full blocks end at byte60. In sub_24802 the byte63 read is
        // cleared AFTER its advance reaches64, despite byte63 itself being valid.
        assertPackedRow(output[20], 0, 0xAD, 0, 0, 0);
        assertPackedRow(output[0], 1, 0x22, 0x22, 0x22, 0x22);
        assertPackedRow(output[0], 2, 0x44, 0x44, 0x44, 0x44);
    }

    @Test
    void modifiedSixteenRetainsTheLastFullBlockSampleAtTheRowBoundary() {
        byte[] source = literalSource();
        put(source, 0, 62, 0xCD);
        fillRow(source, 1, 0x66);
        fillRow(source, 2, 0x77);
        Pattern[] output = scale(source, 12, ROM_FRAME_FOUR, 64);

        // loc_2479C has no post-increment zeroing: indices0,2,4,6 ->16,9E.
        assertPackedRow(output[0], 0, 0x16, 0x9E, 0x27, 0xAF);
        assertPackedRow(output[12], 0, 0, 0, 0, 0x0D);
        assertPackedRow(output[16], 0, 0, 0, 0, 0);
        assertPackedRow(output[0], 1, 0x77, 0x77, 0x77, 0x77);
    }

    @Test
    void genericVerticalFractionSurvivesRowsAndTheThirtyTwoRowBankBoundary() {
        byte[] source = new byte[0x1000];
        fillRow(source, 38, 0xAB);
        fillRow(source, 39, 0xCD);
        fillRow(source, 40, 0xEF);
        Pattern[] output = scale(source, 6, ROM_FRAME_FOUR, 64);

        // Modified10: before output31 the vertical fraction is $C000;
        // its next $4000 addition carries, taking output32 from source row40.
        assertPackedRow(output[3], 7, 0xAB, 0xAB, 0xAB, 0xAB);
        assertPackedRow(output[32], 0, 0xEF, 0xEF, 0xEF, 0xEF);
    }

    @Test
    void mappingExtentsKeepColumnMajorTilesAndSeparateThirtyTwoRowBanks() {
        byte[] source = new byte[0x1000];
        put(source, 0, 0, 0x12, 0x34);
        put(source, 0, 8, 0x56, 0x78);
        put(source, 0, 32, 0x9A, 0xBC);
        put(source, 8, 0, 0xDE, 0xF1);
        put(source, 32, 0, 0x23, 0x45);
        put(source, 32, 32, 0x67, 0x89);
        SpriteMappingFrame shifted = new SpriteMappingFrame(ROM_FRAME_FOUR.pieces().stream()
                .map(p -> new SpriteMappingPiece(p.xOffset() - 32, p.yOffset() - 16,
                        p.widthTiles(), p.heightTiles(), p.tileIndex(), false, false, 1)).toList());
        Pattern[] output = scale(source, 4, shifted, 64);

        assertPackedRow(output[0], 0, 0x14, 0, 0, 0);
        assertPackedRow(output[4], 0, 0x58, 0, 0, 0);
        assertPackedRow(output[16], 0, 0x9C, 0, 0, 0);
        assertPackedRow(output[1], 0, 0xD1, 0, 0, 0);
        assertPackedRow(output[32], 0, 0x25, 0, 0, 0);
        assertPackedRow(output[48], 0, 0x69, 0, 0, 0);
    }

    @Test
    void modifiedFourCopiesPackedBytesAndRepeatsEachSourceRow() {
        byte[] source = literalSource();
        put(source, 1, 0, 0xAB, 0xCD, 0xEF, 0x12);
        put(source, 63, 0, 0x56, 0x78, 0x9A, 0xBC);
        Pattern[] output = scale(source, 0, mappingGrid(4, 4, 4), 256);

        // loc_24B6A copies longwords, with a vertical carry every other row.
        assertPackedRow(output[0], 0, 0x12, 0x34, 0x56, 0x78);
        assertPackedRow(output[0], 1, 0x12, 0x34, 0x56, 0x78);
        assertPackedRow(output[0], 2, 0xAB, 0xCD, 0xEF, 0x12);
        assertPackedRow(output[195], 7, 0x56, 0x78, 0x9A, 0xBC);
    }

    @Test
    void modifiedFiveKeepsItsCopyCopyMergeCopyGroupsAndThreeByteTail() {
        byte[] source = literalSource();
        put(source, 0, 60, 0xAB, 0xCD, 0xEF, 0x91);
        Pattern[] output = scale(source, 1, mappingGrid(4, 4, 2), 256);

        // loc_24B4A / loc_24B32 differ in instruction width, not byte order.
        assertPackedRow(output[0], 0, 0x12, 0x34, 0x58, 0x9A);
        assertPackedRow(output[4], 0, 0xBC, 0xDE, 0xF3, 0x45);
        assertPackedRow(output[48], 0, 0xAB, 0xCD, 0xE1, 0);
    }

    @Test
    void modifiedSixKeepsItsAlternatingCopyMergeGroupsAndLiteralFinalByte() {
        byte[] source = literalSource();
        put(source, 0, 60, 0xAB, 0xCD, 0xEF, 0x91);
        Pattern[] output = scale(source, 2, mappingGrid(3, 3, 4), 144);

        // loc_24AA4, then loc_24A56's copy/merge/copy tail.
        assertPackedRow(output[0], 0, 0x12, 0x36, 0x78, 0x9C);
        assertPackedRow(output[4], 0, 0xDE, 0xF3, 0x45, 0x69);
        assertPackedRow(output[40], 0, 0xAB, 0xCF, 0x91, 0);
    }

    @Test
    void modifiedSevenKeepsItsCopiedFirstByteAndFinalByteSixtyThree() {
        byte[] source = literalSource();
        put(source, 0, 63, 0x91);
        Pattern[] output = scale(source, 3, mappingGrid(3, 3, 4), 144);

        // loc_24A10 copies one byte, merges three pairs, then loc_249D4 copies63.
        assertPackedRow(output[0], 0, 0x12, 0x36, 0x7A, 0xBE);
        assertPackedRow(output[4], 0, 0xF1, 0x25, 0x69, 0xAD);
        assertPackedRow(output[36], 0, 0x91, 0, 0, 0);
    }

    @Test
    void highScaleTailClearsTheJustReadSampleAndAllOldUnmappedPatterns() {
        Pattern[] output = new Pattern[65];
        Arrays.setAll(output, ignored -> new Pattern());
        output[64] = null;
        byte[] source = new byte[0x1000];
        Arrays.fill(source, (byte) 0xFF);
        MgzEndBossArtScaler.scale(source, 4, ROM_FRAME_FOUR, output);
        assertEquals(15, output[63].getPixel(7, 7));

        MgzEndBossArtScaler.scale(source, 0x7C, ROM_FRAME_MAX, output);
        // Modified$80 has zero full blocks. Byte indices0,16,32,48 are read;
        // advance from48 to64 clears that fourth (low) sample in sub_24802.
        assertPackedRow(output[0], 0, 0xFF, 0xF0, 0, 0);
        assertPackedRow(output[0], 3, 0xFF, 0xF0, 0, 0);
        assertPackedRow(output[0], 4, 0, 0, 0, 0);
        assertPackedRow(output[4], 0, 0, 0, 0, 0);
        assertPackedRow(output[63], 7, 0, 0, 0, 0);
    }

    @Test
    void stockMgzHighScaleTailMatchesLiteralRomBytes() throws Exception {
        byte[] source = romRaster(0x36C572,
                "a6217a7e6879aec9d7845e4f4fbeeb9dbed0d5bc66c5449b45a53a62dbb751f9");
        assertEquals(0xBB, Byte.toUnsignedInt(source[16 * 64 + 48]));
        Pattern[] output = scale(source, 0x7C, ROM_FRAME_MAX, 8);

        // Row16 bytes0/16/32/48 are00/44/55/BB. The final BB is cleared.
        assertPackedRow(output[0], 1, 0x04, 0x50, 0, 0);
        assertPackedRow(output[0], 2, 0x04, 0x70, 0, 0);
        assertPackedRow(output[0], 3, 0x04, 0xB0, 0, 0);
    }

    @Test
    void secondStockRasterAlsoExercisesTheHighScaleTailWithoutAddingACaller() throws Exception {
        byte[] source = romRaster(0x17B6E0,
                "ea387429e275b152e2d29b86bf2e8dc3cce7e79577fb110326cb5e391a69787b");
        assertEquals(0xAA, Byte.toUnsignedInt(source[32 * 64 + 48]));
        Pattern[] output = scale(source, 0x7C, ROM_FRAME_MAX, 8);

        // EggRobo row32 bytes0/16/32/48 are00/AA/99/AA. Input-only control:
        // this helper currently has no SSZ production caller or scene proof.
        assertPackedRow(output[0], 2, 0x0A, 0x90, 0, 0);
    }

    private static byte[] romRaster(int offset, String sha256) throws Exception {
        String romPath = System.getProperty("s3k.rom.path");
        assumeTrue(romPath != null && Files.isRegularFile(Path.of(romPath)), "S3K ROM required");
        byte[] source = Arrays.copyOfRange(Files.readAllBytes(Path.of(romPath)), offset, offset + 0x1000);
        assertEquals(sha256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source)),
                "ROM-backed source bank identity");
        return source;
    }

    private static byte[] literalSource() {
        byte[] source = new byte[0x1000];
        put(source, 0, 0, 0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC, 0xDE, 0xF1,
                0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x12);
        return source;
    }

    private static void put(byte[] source, int row, int offset, int... bytes) {
        for (int i = 0; i < bytes.length; i++) source[row * 64 + offset + i] = (byte) bytes[i];
    }

    private static void fillRow(byte[] source, int row, int value) {
        Arrays.fill(source, row * 64, (row + 1) * 64, (byte) value);
    }

    private static Pattern[] scale(byte[] source, int step, SpriteMappingFrame frame, int count) {
        Pattern[] output = new Pattern[count];
        Arrays.setAll(output, ignored -> new Pattern());
        MgzEndBossArtScaler.scale(source, step, frame, output);
        return output;
    }

    /** Native Map_ScaledArt grids, including frame1's narrow rightmost pieces. */
    private static SpriteMappingFrame mappingGrid(int columns, int rows, int lastWidth) {
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                pieces.add(new SpriteMappingPiece(column * 32, row * 32,
                        column == columns - 1 ? lastWidth : 4, 4,
                        (row * columns + column) * 16, false, false, 1));
            }
        }
        return new SpriteMappingFrame(pieces);
    }

    private static void assertPackedRow(Pattern pattern, int y, int... expectedBytes) {
        assertEquals(4, expectedBytes.length);
        for (int i = 0; i < expectedBytes.length; i++) {
            int packed = (pattern.getPixel(i * 2, y) << 4) | pattern.getPixel(i * 2 + 1, y);
            assertEquals(expectedBytes[i], packed, "row " + y + " packed byte " + i);
        }
    }
}
