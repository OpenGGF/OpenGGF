package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdNemesisAsset;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aProfile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-authored, ALL JAVA UNRUN. Frozen before decoder production authoring.
 * Literal expectations come from N=1ecc5f3a General.asm:425-579,664-695,752-766,
 * not a second implementation or the shared Java reader. Media checks are opt-in.
 */
class TestCdNemesisPlcDecoder {
    private static final long ADDRESS = 0x237378L;
    // Eight one-bit runs per tile; each run emits eight A pixels. The final two
    // bytes are real supplied lookahead, intentionally not zero padding.
    private static final CdAssetBytes TWO_NORMAL = bytes(0, 2, 0x8A, 0x71, 0, 0xFF, 0, 0, 0x5A, 0xC3);

    @Test
    void prepareBuildsOnlyTableAndReallyPrefetchesTwoBytes() {
        var state = prepare(TWO_NORMAL, ADDRESS, seed(0xCC));
        assertEquals(2, state.headerWord());
        assertFalse(state.xorMode());
        assertEquals(2, state.remainingTiles());
        assertEquals(8, state.inputOffset());
        assertEquals(ADDRESS + 8, state.sourcePointer());
        assertEquals(ADDRESS, state.nativeAddress());
        assertArrayEquals(TWO_NORMAL.copy(), state.sourceBytes().copy());
        assertEquals(0, state.readWord());
        assertEquals(16, state.shiftWord());
        assertEquals(0, state.repeatWord());
        assertEquals(0, state.pixelNibble());
        assertEquals(0, state.previousRow());
        for (int i = 0; i < 128; i++) { assertEquals(0x017A, state.codeTable().unsignedWord(i * 2)); }
        for (int i = 256; i < 512; i++) { assertEquals(0xCC, state.codeTable().unsignedByte(i)); }
    }

    @Test
    void exactNormalTileCutsKeepFinalReadAheadAndPendingDbf() {
        var prepared = prepare(TWO_NORMAL, ADDRESS, seed(0));
        var first = decodeTile(prepared);
        assertArrayEquals(fill(32, 0xAA), first.bytes().copy());
        cut(first.state(), 1, 9, 0x005A, 16, 0, 0xA, 0);
        var second = decodeTile(first.state());
        assertArrayEquals(fill(32, 0xAA), second.bytes().copy());
        cut(second.state(), 0, 10, 0x5AC3, 16, 0, 0xA, 0);
        assertEquals(2, prepared.remainingTiles());
        assertEquals(8, prepared.inputOffset());
        var failure = assertThrows(DecodeException.class, () -> decodeTile(second.state()));
        assertEquals(Reason.NO_TILES_REMAINING, failure.reason());
        assertSame(second.state(), failure.diagnostic().boundaryState());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void shortAndExactCodeTableModesHaveIndependentCursorCuts(int length) {
        // A zero code expands into 2^(8-length) words; eight runs consume
        // 8*length bits and therefore length extra bytes under >=9 refill.
        byte[] input = new byte[8 + length];
        input[1] = 1; input[2] = (byte) 0x8B;
        input[3] = (byte) (0x70 | length); input[5] = (byte) 0xFF;
        var state = prepare(CdAssetBytes.copyOf(input), ADDRESS, seed(0xCD));
        int entries = 1 << (8 - length);
        for (int i = 0; i < entries; i++) {
            assertEquals((length << 8) | 0x7B, state.codeTable().unsignedWord(i * 2));
        }
        assertEquals(0xCDCD, state.codeTable().unsignedWord(entries * 2));
        var tile = decodeTile(state);
        assertArrayEquals(fill(32, 0xBB), tile.bytes().copy());
        cut(tile.state(), 0, 8 + length, 0, 16, 0, 0xB, 0);
    }

    @Test
    void exactEightBitNonzeroCodeUsesWordIndexAndFinalRefill() {
        var input = bytes(0, 1, 0x8B, 0x78, 0x12, 0xFF,
                0x12, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12, 0xD7);
        var state = prepare(input, ADDRESS, seed(0xCC));
        assertEquals(0x087B, state.codeTable().unsignedWord(0x24));
        assertEquals(0xCCCC, state.codeTable().unsignedWord(0x22));
        var tile = decodeTile(state);
        assertArrayEquals(fill(32, 0xBB), tile.bytes().copy());
        cut(tile.state(), 0, 16, 0x12D7, 16, 0, 0xB, 0);
    }

    @Test
    void sevenPixelRunStraddlesEveryRowAndBothTileCuts() {
        // 7-pixel runs: tile1 ends at the first pixel of run10 (d0=6),
        // tile2 at the second pixel of run19 (d0=5). DBF has not executed
        // after either final pixel. The next tile begins by executing it.
        byte[] table = new byte[512];
        for (int i = 0; i < 256; i++) { table[2 * i] = 1; table[2 * i + 1] = 0x65; }
        var prepared = prepare(bytes(0, 2, 0xFF, 0, 0, 0, 0), ADDRESS, CdAssetBytes.copyOf(table));
        var first = decodeTile(prepared);
        cut(first.state(), 1, 6, 0, 14, 6, 5, 0);
        assertArrayEquals(fill(32, 0x55), first.bytes().copy());
        var second = decodeTile(first.state());
        cut(second.state(), 0, 7, 0, 13, 5, 5, 0);
        assertArrayEquals(fill(32, 0x55), second.bytes().copy());
    }

    @Test
    void inlineNormalAndFullLongXorRowsRetainCrossTileHistory() {
        // Each literal is six marker bits, count=7, a nibble. After eight
        // rows 104 bits are consumed: cursor=3+15, shift16, next word FFCF.
        // After sixteen rows 208 bits: cursor=3+28, final real word 9669.
        int[] nibbles = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        int[] xorRows = {0x11111111, 0x33333333, 0, 0x44444444, 0x11111111, 0x77777777, 0, 0x88888888,
                0x11111111, 0xBBBBBBBB, 0, 0xCCCCCCCC, 0x11111111, 0xFFFFFFFF, 0, 0};
        for (boolean xor : new boolean[]{false, true}) {
            var state = prepare(inline(xor, nibbles), ADDRESS, seed(0));
            for (int tile = 0; tile < 2; tile++) {
                var result = decodeTile(state);
                for (int row = 0; row < 8; row++) {
                    int expected = xor ? xorRows[tile * 8 + row] : nibbles[tile * 8 + row] * 0x11111111;
                    assertEquals(Integer.toUnsignedLong(expected), result.bytes().unsignedLong(row * 4));
                }
                state = result.state();
                cut(state, 1 - tile, tile == 0 ? 18 : 31, tile == 0 ? 0xFFCF : 0x9669,
                        16, 0, tile == 0 ? 8 : 0, xor && tile == 0 ? 0x88888888 : 0);
            }
        }
    }

    @Test
    void suppliedSeedHolesAndOverlappingDescriptionsArePreserved() {
        var input = bytes(0, 1, 0x82, 0x72, 0, 0x83, 0x71, 0, 0xFF, 0, 0, 0);
        var state = prepare(input, ADDRESS, seed(0xED));
        for (int i = 0; i < 128; i++) { assertEquals(0x0173, state.codeTable().unsignedWord(i * 2)); }
        assertEquals(0xEDED, state.codeTable().unsignedWord(256));
        assertArrayEquals(fill(32, 0x33), decodeTile(state).bytes().copy());
        byte[] table = new byte[512];
        for (int i = 0; i < 256; i++) { table[i * 2] = 1; table[i * 2 + 1] = 0x74; }
        var holeOnly = prepare(bytes(0, 1, 0xFF, 0, 0, 0), ADDRESS, CdAssetBytes.copyOf(table));
        assertArrayEquals(table, holeOnly.codeTable().copy());
        assertArrayEquals(fill(32, 0x44), decodeTile(holeOnly).bytes().copy());
    }

    @Test
    void malformedTableWritesStopAtActualSignedRelativeOwnedMemoryBoundary() {
        var negative = assertThrows(DecodeException.class,
                () -> prepare(bytes(0, 1, 0x8A, 0x71, 0xFF), ADDRESS, seed(0xCC)));
        assertEquals(Reason.TABLE_ADDRESS, negative.reason());
        assertEquals(-256, negative.diagnostic().tableOffset());
        assertEquals(5, negative.diagnostic().inputOffset());
        assertArrayEquals(fill(512, 0xCC), negative.diagnostic().codeTable().copy());
        // Length9 -> word shift count63 -> zero, DBF starts FFFF. Native
        // writes 256 owned words, then reaches +512; it is not rejected early.
        var pastEnd = assertThrows(DecodeException.class,
                () -> prepare(bytes(0, 1, 0x8A, 0x79, 0), ADDRESS, seed(0xCC)));
        assertEquals(Reason.TABLE_ADDRESS, pastEnd.reason());
        assertEquals(512, pastEnd.diagnostic().tableOffset());
        assertEquals(5, pastEnd.diagnostic().inputOffset());
        for (int i = 0; i < 256; i++) { assertEquals(0x097A, pastEnd.diagnostic().codeTable().unsignedWord(i * 2)); }
        assertNull(pastEnd.diagnostic().boundaryState());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9, 128, 255})
    void malformedReachedSeedLengthsHaveTypedUnsupportedBoundary(int length) {
        byte[] table = new byte[512]; table[0] = (byte) length; table[1] = 0x7A;
        var state = prepare(bytes(0, 1, 0xFF, 0, 0), ADDRESS, CdAssetBytes.copyOf(table));
        var failure = assertThrows(DecodeException.class, () -> decodeTile(state));
        assertEquals(Reason.UNSUPPORTED_CODE_LENGTH, failure.reason());
        assertEquals(0, failure.diagnostic().tableOffset());
        assertEquals(5, failure.diagnostic().inputOffset());
        assertEquals(0, failure.diagnostic().emittedRows().length());
        assertSame(state, failure.diagnostic().boundaryState());
    }

    @Test
    void normalFinalRunRefillFaultPrecedesLastRowEmission() {
        var state = prepare(TWO_NORMAL.slice(0, 8), ADDRESS, seed(0));
        var failure = assertThrows(DecodeException.class, () -> decodeTile(state));
        assertEquals(Reason.SOURCE_EXHAUSTED, failure.reason());
        assertEquals("CODE_REFILL", failure.diagnostic().phase());
        assertEquals(8, failure.diagnostic().inputOffset());
        assertEquals(ADDRESS + 8, failure.diagnostic().sourcePointer());
        assertEquals(16, failure.diagnostic().shiftWord());
        assertArrayEquals(fill(28, 0xAA), failure.diagnostic().emittedRows().copy());
        assertEquals(0, failure.diagnostic().pixelsInRow());
        assertEquals(0, failure.diagnostic().row());
        assertEquals(state, failure.diagnostic().boundaryState());
        assertEquals(2, state.remainingTiles());
        assertEquals(8, state.inputOffset());
        byte[] exposed = failure.diagnostic().emittedRows().copy(); exposed[0] = 0;
        assertEquals(0xAA, failure.diagnostic().emittedRows().unsignedByte(0));
    }

    @Test
    void inlineValueRefillIsReachedEvenBeforeFirstPixel() {
        var input = inline(true, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        var state = prepare(input.slice(0, 5), ADDRESS, seed(0));
        var failure = assertThrows(DecodeException.class, () -> decodeTile(state));
        assertEquals(Reason.SOURCE_EXHAUSTED, failure.reason());
        assertEquals("INLINE_VALUE_REFILL", failure.diagnostic().phase());
        assertEquals(5, failure.diagnostic().inputOffset());
        assertEquals(11, failure.diagnostic().shiftWord());
        assertEquals(0, failure.diagnostic().emittedRows().length());
        assertEquals(0, failure.diagnostic().pixelsInRow());
    }

    @Test
    void everyFinitePrefixFailsAtReachedReadWithoutChangingPriorBoundary() {
        for (int end = 0; end < TWO_NORMAL.length(); end++) {
            final int limit = end;
            var failure = assertThrows(DecodeException.class, () -> {
                var state = prepare(TWO_NORMAL.slice(0, limit), ADDRESS, seed(0));
                while (state.remainingTiles() > 0) { state = decodeTile(state).state(); }
            });
            assertEquals(Reason.SOURCE_EXHAUSTED, failure.reason(), "prefix " + end);
            assertEquals(end < 2 ? 0 : end, failure.diagnostic().inputOffset(), "prefix " + end);
        }
        var partialPrefetch = assertThrows(DecodeException.class,
                () -> prepare(bytes(0, 1, 0xFF, 0x12), ADDRESS, seed(0)));
        assertEquals("PREFETCH_LOW", partialPrefetch.diagnostic().phase());
        assertEquals(0x1200, partialPrefetch.diagnostic().readWord());
        assertEquals(4, partialPrefetch.diagnostic().inputOffset());
    }

    @Test
    void allCompleteTileSnapshotsAreImmutableAndReplayByByteContent() {
        var original = prepare(TWO_NORMAL, ADDRESS, seed(0xA5));
        var duplicate = prepare(CdAssetBytes.copyOf(TWO_NORMAL.copy()), ADDRESS, seed(0xA5));
        var cuts = new ArrayList<State>(); cuts.add(original);
        var state = original;
        while (state.remainingTiles() > 0) { state = decodeTile(state).state(); cuts.add(state); }
        for (int i = 0; i < cuts.size(); i++) {
            var replay = duplicate;
            for (int n = 0; n < i; n++) { replay = decodeTile(replay).state(); }
            assertEquals(cuts.get(i), replay);
            assertEquals(cuts.get(i).hashCode(), replay.hashCode());
            if (replay.remainingTiles() > 0) {
                var a = decodeTile(cuts.get(i)); var b = decodeTile(replay);
                assertEquals(a.state(), b.state()); assertArrayEquals(a.bytes().copy(), b.bytes().copy());
                byte[] detached = a.bytes().copy(); detached[0] = 0;
                assertEquals(0xAA, a.bytes().unsignedByte(0));
            }
        }
        byte[] exposedSource = original.sourceBytes().copy(); exposedSource[0] = 0x55;
        byte[] exposedTable = original.codeTable().copy(); exposedTable[0] = 0x55;
        assertEquals(original, duplicate);
        assertNotEquals(original, prepare(TWO_NORMAL, ADDRESS + 2, seed(0xA5)));
        byte[] otherSeed = seed(0xA5).copy(); otherSeed[511] ^= 1;
        assertNotEquals(original, prepare(TWO_NORMAL, ADDRESS, CdAssetBytes.copyOf(otherSeed)));
        byte[] otherSource = TWO_NORMAL.copy(); otherSource[9] ^= 1;
        assertNotEquals(original, prepare(CdAssetBytes.copyOf(otherSource), ADDRESS, seed(0xA5)));
        for (var type : new Class<?>[]{State.class, TileResult.class, Diagnostic.class}) {
            assertTrue(Modifier.isFinal(type.getModifiers()));
            for (var constructor : type.getDeclaredConstructors()) { assertTrue(Modifier.isPrivate(constructor.getModifiers())); }
        }
    }

    @Test
    void zeroHeaderStillBuildsTableAndPrefetchesButCannotServiceTile() {
        for (int header : new int[]{0, 0x8000}) {
            var state = prepare(bytes(header >>> 8, 0, 0xFF, 0x12, 0x34), ADDRESS, seed(0x9A));
            assertEquals(5, state.inputOffset()); assertEquals(0x1234, state.readWord());
            assertEquals(0, state.remainingTiles()); assertEquals(header != 0, state.xorMode());
            assertArrayEquals(fill(512, 0x9A), state.codeTable().copy());
            assertEquals(Reason.NO_TILES_REMAINING,
                    assertThrows(DecodeException.class, () -> decodeTile(state)).reason());
        }
        assertEquals(Reason.SOURCE_EXHAUSTED,
                assertThrows(DecodeException.class, () -> prepare(bytes(0, 0, 0xFF), ADDRESS, seed(0))).reason());
    }

    @Test
    void nullWidthAlignmentAndUnsignedLongPointerContractsAreExplicit() {
        assertThrows(NullPointerException.class, () -> prepare(null, ADDRESS, seed(0)));
        assertThrows(NullPointerException.class, () -> prepare(TWO_NORMAL, ADDRESS, null));
        assertThrows(NullPointerException.class, () -> decodeTile(null));
        for (int length : new int[]{0, 511, 513}) {
            assertThrows(IllegalArgumentException.class, () -> prepare(TWO_NORMAL, ADDRESS, seedOfLength(length)));
        }
        for (long address : new long[]{-1, 0x1_0000_0000L}) {
            assertThrows(IllegalArgumentException.class, () -> prepare(TWO_NORMAL, address, seed(0)));
        }
        var odd = assertThrows(DecodeException.class, () -> prepare(TWO_NORMAL, ADDRESS + 1, seed(0)));
        assertEquals(Reason.SOURCE_ADDRESS, odd.reason()); assertEquals(0, odd.diagnostic().inputOffset());
        var wrapped = prepare(bytes(0, 0, 0xFF, 0x12, 0x34), 0xFFFF_FFFEL, seed(0));
        assertEquals(3, wrapped.sourcePointer()); assertEquals(0xFFFF_FFFEL, wrapped.nativeAddress());
    }

    @Test
    void optionalActualUsaSixProducersKeepRetainedOwnerBytesAndNativeFiniteCutsAfterClose() throws Exception {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for actual USA media");
        Path cue;
        try (var files = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        CdR11aAssets terrain;
        CdR11aObjectAssets objects;
        CdMmdImage retained;
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            terrain = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
            retained = source.mmd(CdNativeFile.R11A);
            objects = terrain.openingObjects();
        }
        // The actual owner reauthenticates the retained entire MMD; a plain
        // prepare(CdAssetBytes,...) never certifies media/release identity.
        terrain.profile().verifyProgram(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, retained);
        for (var fixture : MEDIA) {
            var blob = terrain.profile().range(fixture.asset());
            var range = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), fixture.nativeBytes());
            var input = range.read(retained);
            assertEquals(fixture.sourceHash(), sha(input));
            assertEquals(fixture.header(), input.unsignedWord(0));
            int size = (fixture.header() & 0x7FFF) * 32;
            var old = CdNemesisAsset.decode(input.slice(0, fixture.javaBytes()).copy(), size, size);
            assertEquals(fixture.javaBytes(), old.consumedInputBytes());
            assertEquals(fixture.decodedHash(), sha(old.bytes()));
            CdAssetBytes existing = switch (fixture.asset()) {
                case ART_PATA_BATA -> objects.pataBataArtBank().binding(fixture.asset()).bytes();
                case ART_MOSQUI -> objects.initialArtBank().binding(fixture.asset()).bytes();
                case ART_3D_PLANT, ART_3D_RAMP_BOOST -> objects.frontierArtBank().binding(fixture.asset()).bytes();
                default -> old.bytes(); // Anton/Tam have no decoded object-bank owner at immutable J.
            };
            assertArrayEquals(old.bytes().copy(), existing.copy());
            var state = prepare(input, Integer.toUnsignedLong(range.nativeAddress()), seed(0));
            var cuts = new ArrayList<State>(); cuts.add(state);
            byte[] decoded = new byte[size];
            for (int tile = 0; tile < size / 32; tile++) {
                var result = decodeTile(state);
                assertArrayEquals(old.bytes().slice(tile * 32, 32).copy(), result.bytes().copy(), fixture.asset() + " tile " + tile);
                System.arraycopy(result.bytes().copy(), 0, decoded, tile * 32, 32);
                state = result.state(); cuts.add(state);
            }
            assertEquals(0, state.remainingTiles());
            assertEquals(fixture.nativeBytes(), state.inputOffset());
            assertEquals(Integer.toUnsignedLong(range.nativeAddress()) + fixture.nativeBytes(), state.sourcePointer());
            assertEquals(fixture.decodedHash(), sha(CdAssetBytes.copyOf(decoded)));
            // Replay every immutable partial tile boundary, not only the final image.
            for (int tile = 0; tile < cuts.size() - 1; tile++) {
                assertEquals(cuts.get(tile + 1), decodeTile(cuts.get(tile)).state());
            }
            final var truncated = input.slice(0, input.length() - 1);
            var failure = assertThrows(DecodeException.class, () -> {
                var partial = prepare(truncated, Integer.toUnsignedLong(range.nativeAddress()), seed(0));
                while (partial.remainingTiles() > 0) { partial = decodeTile(partial).state(); }
            });
            assertEquals(Reason.SOURCE_EXHAUSTED, failure.reason());
            assertEquals(fixture.nativeBytes() - 1, failure.diagnostic().inputOffset());
        }
    }

    // Root/source-worker supplied Python-model evidence, not Java/native execution.
    private record Media(CdR11aAsset asset, int header, int nativeBytes, int javaBytes,
                         String sourceHash, String decodedHash) { }
    private static final Media[] MEDIA = {
            new Media(CdR11aAsset.ART_PATA_BATA, 0x8032, 715, 714,
                    "a415ffa282fb1e90378a97f458f05e379a6d82d1a006751d1b85c6efcfc736e3",
                    "8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943"),
            new Media(CdR11aAsset.ART_ANTON, 0x0016, 485, 484,
                    "e0e121da920926a86b5a445ba9df766931ee4cac91d4e611a3b30fcf5ebba556",
                    "2e0a7e3f6719c8afa2c569d5078824dd7c522a04eeee75fa773830ceea95b101"),
            new Media(CdR11aAsset.ART_TAMABBOH, 0x803B, 1143, 1142,
                    "c90255c16ce2a453a6dad66e1b9ff641e12b2fc87e5b639429e1ef1962978b0e",
                    "aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95"),
            new Media(CdR11aAsset.ART_MOSQUI, 0x0046, 847, 846,
                    "7696cc4d61b4f4607afcafdb693994cd65972efce4949f18a622dd630df8c4b5",
                    "230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd"),
            new Media(CdR11aAsset.ART_3D_PLANT, 0x801C, 393, 393,
                    "f7746c8d049390552d26e0db9617f02370e93aad8a0585cc6049c30969a89b6f",
                    "a3a40dce6206cd71835f043eadee914a860724ec4361d1275a9535c7b2a1b164"),
            new Media(CdR11aAsset.ART_3D_RAMP_BOOST, 0x803F, 678, 677,
                    "1fbee908474abae3f81f527d437fa6479a2c91902e0f65df78108de39e4a57c0",
                    "e52c4daa8995ac4365b3674cabf8bc81fc52db73d729e756dac757d79942b7a7")
    };

    private static void cut(State state, int remaining, int cursor, int read, int shift,
                            int repeat, int pixel, int row) {
        assertEquals(remaining, state.remainingTiles()); assertEquals(cursor, state.inputOffset());
        assertEquals(read, state.readWord()); assertEquals(shift, state.shiftWord());
        assertEquals(repeat, state.repeatWord()); assertEquals(pixel, state.pixelNibble());
        assertEquals(row, state.previousRow());
    }
    private static CdAssetBytes bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return CdAssetBytes.copyOf(out);
    }
    private static byte[] fill(int length, int value) {
        byte[] out = new byte[length]; Arrays.fill(out, (byte) value); return out;
    }
    private static CdAssetBytes seed(int value) { return CdAssetBytes.copyOf(fill(512, value)); }
    private static CdAssetBytes seedOfLength(int length) { return CdAssetBytes.copyOf(new byte[length]); }
    private static String sha(CdAssetBytes value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.copy()));
    }
    /** Encoder of literal test bits only; no reader, table builder or production dependency. */
    private static CdAssetBytes inline(boolean xor, int[] nibbles) {
        StringBuilder bits = new StringBuilder();
        for (int nibble : nibbles) {
            bits.append("111111111"); // six marker bits + three repeat=7 bits
            for (int bit = 3; bit >= 0; bit--) { bits.append((nibble >>> bit) & 1); }
        }
        while (bits.length() % 8 != 0) { bits.append('0'); }
        int dataBytes = bits.length() / 8;
        byte[] out = new byte[3 + dataBytes + 2];
        out[0] = (byte) (xor ? 0x80 : 0); out[1] = (byte) (nibbles.length / 8); out[2] = (byte) 0xFF;
        for (int i = 0; i < dataBytes; i++) { out[3 + i] = (byte) Integer.parseInt(bits.substring(i * 8, i * 8 + 8), 2); }
        out[out.length - 2] = (byte) 0x96; out[out.length - 1] = 0x69;
        return CdAssetBytes.copyOf(out);
    }
}
