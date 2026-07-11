package com.openggf.tools.modsdk;

import com.openggf.io.ModInputLimits;
import com.openggf.level.Pattern;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBakedSheetRoundTrip {
    @Test
    void writerAndReaderPreservePatternsFramesPiecesAndPalette() throws IOException {
        Pattern first = patterned(0);
        Pattern second = patterned(1);
        List<BakedSheetReader.Frame> frames = List.of(
                new BakedSheetReader.Frame(3, new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-2, 4, 2, 1, 0, true, false, 2, true)))),
                new BakedSheetReader.Frame(9, new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(12, -16, 1, 1, 1, false, true, 3, false)))));
        BakedSheetReader.Palette palette = new BakedSheetReader.Palette(2,
                new int[]{0x000, 0x002, 0x024, 0x246, 0x468, 0x68A, 0x8AC, 0xACE,
                        0xEEE, 0xECA, 0xCA8, 0xA86, 0x864, 0x642, 0x420, 0x200});
        BakedSheetReader.BakedSheet source = new BakedSheetReader.BakedSheet(
                new Pattern[]{first, second}, frames, Optional.of(palette));

        byte[] encoded = BakedSheetWriter.write(source);
        BakedSheetReader.BakedSheet decoded = BakedSheetReader.read(new ByteArrayInputStream(encoded));

        assertEquals(source, decoded);
        assertEquals(source.hashCode(), decoded.hashCode());
        assertEquals(frames, decoded.frames());
        assertEquals(Optional.of(palette), decoded.palette());
        assertPatternEquals(first, decoded.patterns()[0]);
        assertPatternEquals(second, decoded.patterns()[1]);
        assertEquals(frames.get(1).mapping(), decoded.toObjectSpriteSheet().getFrame(1));
        assertEquals(2, decoded.toObjectSpriteSheet().getPaletteIndex());
    }

    @Test
    void writerMatchesPinnedGoldenBytesAndPreservesOrder() throws IOException {
        Pattern zero = new Pattern();
        Pattern ascending = patterned(0);
        BakedSheetReader.BakedSheet sheet = new BakedSheetReader.BakedSheet(
                new Pattern[]{zero, ascending},
                List.of(new BakedSheetReader.Frame(3, new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-2, 4, 2, 1, 0, true, false, 2, true))))),
                Optional.empty());

        byte[] expected = hex("47474653000100000002"
                + "00".repeat(32)
                + "0123456789abcdef".repeat(4)
                + "000100030001fffe0004020100000000050200");

        assertArrayEquals(expected, BakedSheetWriter.write(sheet));
        assertEquals(sheet.frames(), BakedSheetReader.read(expected).frames());
    }

    @Test
    void rejectsBadMagicFutureVersionTruncationAndTrailingBytes() throws IOException {
        byte[] valid = minimalBytes();
        byte[] badMagic = valid.clone();
        badMagic[0] = 'X';
        byte[] future = valid.clone();
        future[5] = 2;

        assertThrows(IOException.class, () -> BakedSheetReader.read(badMagic));
        assertThrows(IOException.class, () -> BakedSheetReader.read(future));
        assertThrows(IOException.class, () -> BakedSheetReader.read(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IOException.class, () -> BakedSheetReader.read(append(valid, (byte) 0)));
    }

    @Test
    void rejectsReservedFlagsZeroDimensionsAndTileSpansOutsidePatterns() throws IOException {
        byte[] valid = onePieceBytes();
        int pieceOffset = 48;

        byte[] reservedFlags = valid.clone();
        reservedFlags[pieceOffset + 10] = 0x08;
        byte[] zeroWidth = valid.clone();
        zeroWidth[pieceOffset + 4] = 0;
        byte[] zeroHeight = valid.clone();
        zeroHeight[pieceOffset + 5] = 0;
        byte[] outside = valid.clone();
        outside[pieceOffset + 9] = 1;

        assertThrows(IOException.class, () -> BakedSheetReader.read(reservedFlags));
        assertThrows(IOException.class, () -> BakedSheetReader.read(zeroWidth));
        assertThrows(IOException.class, () -> BakedSheetReader.read(zeroHeight));
        assertThrows(IOException.class, () -> BakedSheetReader.read(outside));
    }

    @Test
    void rejectsUnsignedOverflowAndInjectedCountAndByteLimits() throws IOException {
        byte[] unsignedPatternCount = minimalBytes();
        unsignedPatternCount[6] = (byte) 0x80;
        byte[] tooManyFrames = minimalBytes();
        tooManyFrames[10] = 0;
        tooManyFrames[11] = 2;
        ModInputLimits oneEach = ModInputLimits.loweringBuilder()
                .maxSheetPatterns(1).maxSheetFrames(1).maxSheetPieces(1).build();
        ModInputLimits tinyBytes = ModInputLimits.loweringBuilder().maxAssetBytes(8).build();

        assertThrows(IOException.class, () -> BakedSheetReader.read(unsignedPatternCount));
        assertThrows(IOException.class, () -> BakedSheetReader.read(tooManyFrames, oneEach));
        assertThrows(IOException.class, () -> BakedSheetReader.read(minimalBytes(), tinyBytes));
    }

    @Test
    void rejectsDeclaredCollectionsThatCannotFitBeforeAllocatingThem() {
        ModInputLimits countLimits = ModInputLimits.loweringBuilder()
                .maxSheetPatterns(1_000).maxSheetFrames(1_000).maxSheetPieces(1_000)
                .maxAssetBytes(64).build();
        byte[] impossiblePatterns = hex("474746530001000003e8");
        byte[] impossibleFrames = hex("4747465300010000000003e8");
        byte[] impossiblePieces = hex("474746530001000000000001000003e8");

        assertThrows(IOException.class, () -> BakedSheetReader.read(impossiblePatterns, countLimits));
        assertThrows(IOException.class, () -> BakedSheetReader.read(impossibleFrames, countLimits));
        assertThrows(IOException.class, () -> BakedSheetReader.read(impossiblePieces, countLimits));
    }

    @Test
    void writerRejectsUnrepresentableOrInvalidValues() {
        Pattern one = new Pattern();
        assertThrows(IllegalArgumentException.class, () -> BakedSheetWriter.write(sheet(one,
                new SpriteMappingPiece(Short.MAX_VALUE + 1, 0, 1, 1, 0, false, false, 0, false))));
        assertThrows(IllegalArgumentException.class, () -> BakedSheetWriter.write(sheet(one,
                new SpriteMappingPiece(0, 0, 0, 1, 0, false, false, 0, false))));
        assertThrows(IllegalArgumentException.class, () -> BakedSheetWriter.write(sheet(one,
                new SpriteMappingPiece(0, 0, 1, 1, 1, false, false, 0, false))));
        assertThrows(IllegalArgumentException.class, () -> BakedSheetWriter.write(new BakedSheetReader.BakedSheet(
                new Pattern[]{one}, List.of(new BakedSheetReader.Frame(65_536,
                new SpriteMappingFrame(List.of()))), Optional.empty())));
    }

    @Test
    void writerAcceptsAContainerExactlyAtTheInjectedByteLimit() throws IOException {
        BakedSheetReader.BakedSheet sheet = sheet(new Pattern(),
                new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0, false));
        ModInputLimits exact = ModInputLimits.loweringBuilder().maxAssetBytes(61).build();

        assertEquals(61, BakedSheetWriter.write(sheet, BakedSheetReader.Limits.from(exact)).length);
    }

    @Test
    void writerValidatesAllPatternPixelsBeforeWritingAnything() {
        Pattern invalid = new Pattern();
        invalid.setPixel(7, 7, (byte) 16);
        BakedSheetReader.BakedSheet sheet = new BakedSheetReader.BakedSheet(
                new Pattern[]{new Pattern(), invalid}, List.of(), Optional.empty());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThrows(IllegalArgumentException.class, () -> BakedSheetWriter.write(sheet, output));
        assertEquals(0, output.size());
    }

    @Test
    void bakedSheetIsDeeplyImmutableAcrossInputsAccessorsAndRuntimeConversion() {
        Pattern inputPattern = patterned(0);
        List<SpriteMappingPiece> inputPieces = new ArrayList<>();
        SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 1, 1, 0,
                false, false, 0, false);
        inputPieces.add(piece);
        BakedSheetReader.BakedSheet sheet = new BakedSheetReader.BakedSheet(
                new Pattern[]{inputPattern},
                List.of(new BakedSheetReader.Frame(1, new SpriteMappingFrame(inputPieces))),
                Optional.empty());

        inputPattern.setPixel(0, 0, (byte) 15);
        inputPieces.clear();
        assertEquals(0, sheet.patterns()[0].getPixel(0, 0));
        assertEquals(List.of(piece), sheet.frames().getFirst().mapping().pieces());

        Pattern[] returned = sheet.patterns();
        returned[0].setPixel(0, 0, (byte) 14);
        assertEquals(0, sheet.patterns()[0].getPixel(0, 0));

        sheet.toObjectSpriteSheet().getPatterns()[0].setPixel(0, 0, (byte) 13);
        assertEquals(0, sheet.patterns()[0].getPixel(0, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> sheet.frames().getFirst().mapping().pieces().clear());
    }

    @Test
    void readerDoesNotCloseCallerOwnedInputStream() throws IOException {
        class TrackingInputStream extends ByteArrayInputStream {
            private boolean closed;
            TrackingInputStream(byte[] bytes) { super(bytes); }
            @Override public void close() { closed = true; }
        }
        TrackingInputStream input = new TrackingInputStream(minimalBytes());

        BakedSheetReader.read(input);

        assertEquals(false, input.closed);
    }

    private static BakedSheetReader.BakedSheet sheet(Pattern pattern, SpriteMappingPiece piece) {
        return new BakedSheetReader.BakedSheet(new Pattern[]{pattern},
                List.of(new BakedSheetReader.Frame(1, new SpriteMappingFrame(List.of(piece)))), Optional.empty());
    }

    private static byte[] minimalBytes() {
        return hex("47474653000100000000000000");
    }

    private static byte[] onePieceBytes() {
        return hex("47474653000100000001" + "00".repeat(32)
                + "00010001000100000000010100000000000000");
    }

    private static Pattern patterned(int shift) {
        Pattern pattern = new Pattern();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                pattern.setPixel(x, y, (byte) ((x + y * 8 + shift) & 0x0f));
            }
        }
        return pattern;
    }

    private static void assertPatternEquals(Pattern expected, Pattern actual) {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(expected.getPixel(x, y), actual.getPixel(x, y));
            }
        }
    }

    private static byte[] append(byte[] bytes, byte value) {
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[bytes.length] = value;
        return result;
    }

    private static byte[] hex(String value) {
        assertTrue((value.length() & 1) == 0);
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
