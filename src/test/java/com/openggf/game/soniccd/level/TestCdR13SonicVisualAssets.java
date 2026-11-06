package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdSonicVisualAssets.SourceFormat.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Original-USA R13C/D finite visual-format witnesses, not FULL/Frame/World or rendering admission.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff sprites/player.asm, sprites/player_gfx.asm;
 * LoadSonicDynPLC (Level/_Objects/Sonic/Main.asm:3055-3100), Data (Act 3).asm:62-75.
 * Literal metadata/fingerprints derive independently from native directives and original MMD bytes.
 * Runtime test assets come only through CdProgramAssets; no native-asset fallback or alignment synthesis.
 * Constructed inputs below are labelled format controls. Authored before production mirror; Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR13SonicVisualAssets {
    private static final String MAP_HASH = "4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965";
    private static final String DPLC_HASH = "313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae";
    private static final String ART_HASH = "a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276";
    // Actual post-LEA LoadSonicDynPLC operand/count/piece/copy bounds witness; no instruction execution.
    private static final String DPLC_CONSUMER = "d040d4f200007200321a53016b3847f8c80011fc0001f7677400141a3002e808e14a141a02420fffeb8a43f900220000d3c24cd9707c48d3707c47eb002051c8fff251c9ffd44e75";
    private static final byte[] MAP_DIRECTORY = directory("""
014e0150016601760186019601a601a601b601c201d2014e014e014e014e014e014e0740074c075c076c0782078e079e08d808e8
09280938094e095e09740980014e014e014e014e014e014e014e014e014e014e014e014e014e05bc05c205c805ce05d4025e01e8
02020212021e0238024802b202cc02ec030c032c034c036603800390039c03b603c603dc03f604160436045604760490049c04a8
04b404c004da04f0051005260532053e054a05560570058605a605da05ea0626014e014e0636014e014e014e064c065c066c067c
014e014e068c014e014e06ac06c2014e014e014e014e014e014e014e014e014e014e014e014e014e014e014e014e05fa0610014e
014e06d206e206ee06fe0714072a09080918014e014e014e014e014e026e027a029002a607d407e007ec07f80804081e082e0848
085808640870087c0888089e08ae08c807ae07be08f8
""");
    private static final byte[] DPLC_DIRECTORY = directory("""
014e0150015a0162016a0172017a017a018201880190014e014e014e014e014e014e041c0422042a0432043c0442044a04e204ea
050a0512051c0524052e0534014e014e014e014e014e014e014e014e014e014e014e014e014e035e03620366036a036e01d2019a
01a601ae01b401c001c801fa0206021402220230023e024a0256025e0264027002780282028e029c02aa02b802c602d202d802de
02e402ea02f60300030e0318031e0324032a0330033c034603540372037a0396014e014e039e014e014e014e03a803b003b803c0
014e014e03c8014e014e03d603e0014e014e014e014e014e014e014e014e014e014e014e014e014e014e014e014e0382038c014e
014e03e803f003f603fe0408041204fa0502014e014e014e014e014e01da01e001ea01f40464046a04700476047c04880490049c
04a404aa04b004b604bc04c604ce04da0452045a04f2
""");

    @Test void actualUsaCAndDOperandsSelectTheirOriginal167FrameTables() throws Exception {
        for (var a : actual()) {
            int delta = a.file() == CdNativeFile.R13C ? 0 : 0x18;
            assertEquals("217c0022df200004", hex(a.image().readFile(0x3DB4 + delta, 8)));
            assertEquals("45f90022e8ac", hex(a.image().readFile(0x5940 + delta, 6)));
            assertEquals("43f900220000", hex(a.image().readFile(0x5970 + delta, 6)));
            assertEquals(DPLC_CONSUMER, hex(a.image().readFile(0x5946 + delta, DPLC_CONSUMER.length() / 2)));
            assertEquals(MAP_HASH, hash(a.image().readFile(0x2DF20, 2443)));
            assertEquals(DPLC_HASH, hash(a.image().readFile(0x2E8AC, 1338)));
            assertEquals(ART_HASH, hash(a.image().readFile(0x20000, 57120)));
            assertArrayEquals(MAP_DIRECTORY, a.image().readFile(0x2DF20, 334));
            assertArrayEquals(DPLC_DIRECTORY, a.image().readFile(0x2E8AC, 334));
            assertEquals(0, a.image().readFile(0x2E8AB, 1)[0], "Mapping include's separate actual EVEN byte");
            assertEquals(FRAMES_167, a.visual().sourceFormat());
        }
    }

    @Test void all167MappingBodiesPreserveEveryNativeFiveBytePieceAndAlias() throws Exception {
        for (var a : actual()) {
            var v = a.visual(); byte[] raw = a.image().readFile(0x2DF20, 2443);
            assertArrayEquals(raw, v.mappings().copy()); assertEquals(167, v.mappingFrames().size());
            var distinct = new HashSet<Integer>(); var meta = new StringBuilder(); int total = 0, maximum = 0;
            for (int f = 0; f < 167; f++) {
                int at = u16(MAP_DIRECTORY, f * 2), count = raw[at] & 255; var frame = v.mappingFrames().get(f);
                assertEquals(at, frame.relativeOffset()); assertEquals(count, frame.pieceCount());
                assertArrayEquals(Arrays.copyOfRange(raw, at, at + 1 + count * 5), frame.bytes().copy());
                assertTrue(at >= 334 && (at & 1) == 0 && at + frame.bytes().length() <= raw.length);
                distinct.add(at); total += count; maximum = Math.max(maximum, count);
                meta.append(f).append(' ').append(Integer.toHexString(at)).append(' ').append(count).append(' ').append(hash(frame.bytes().copy())).append('\n');
            }
            assertEquals(115, distinct.size()); assertEquals(390, total); assertEquals(6, maximum);
            assertEquals("5f34fef3c5d86aa84f744ea25d3cff4199a4f4fa13b6a5058cff95eb56cdc177", hash(meta));
            assertEquals(v.mappingFrames().get(6).relativeOffset(), v.mappingFrames().get(7).relativeOffset());
            assertArrayEquals(v.mappingFrames().get(6).bytes().copy(), v.mappingFrames().get(7).bytes().copy());
        }
    }

    @Test void all167DplcBodiesAndEveryArtReadMatchTheOriginalFiniteSources() throws Exception {
        for (var a : actual()) {
            var v = a.visual(); byte[] raw = a.image().readFile(0x2E8AC, 1338); var meta = new StringBuilder();
            var distinct = new HashSet<Integer>(); var reads = MessageDigest.getInstance("SHA-256");
            int total = 0, maximum = 0, furthest = 0;
            assertArrayEquals(raw, v.dplcs().copy()); assertEquals(167, v.dplcFrames().size());
            for (int f = 0; f < 167; f++) {
                int at = u16(DPLC_DIRECTORY, f * 2), count = u16(raw, at), output = 0; var frame = v.dplcFrames().get(f);
                assertEquals(at, frame.relativeOffset()); assertEquals(count, frame.pieces().size());
                assertArrayEquals(Arrays.copyOfRange(raw, at, at + 2 + count * 2), frame.bytes().copy());
                for (int i = 0; i < count; i++) {
                    int word = u16(raw, at + 2 + i * 2), offset = (word & 4095) * 32, length = ((word >>> 12) + 1) * 32;
                    var piece = frame.pieces().get(i); assertEquals(word, piece.rawWord()); assertEquals(offset, piece.artByteOffset());
                    assertEquals(length / 32, piece.tileCount()); assertEquals(length, piece.byteLength());
                    assertTrue(offset + length <= 57120); assertArrayEquals(a.image().readFile(0x20000 + offset, length), v.artPiece(f, i).copy());
                    reads.update(v.artPiece(f, i).copy()); output += length; furthest = Math.max(furthest, offset + length);
                }
                assertEquals(output, frame.outputBytes()); assertTrue(output <= 0x300);
                distinct.add(at); total += count; maximum = Math.max(maximum, output);
                meta.append(f).append(' ').append(Integer.toHexString(at)).append(' ').append(count).append(' ').append(output).append(' ').append(hash(frame.bytes().copy())).append('\n');
            }
            assertEquals(115, distinct.size()); assertEquals(390, total); assertEquals(736, maximum); assertEquals(57120, furthest);
            assertEquals("5193e3d357cbb851817d4e4163b1e8ec7e291bf9024e2d9a403607437b64f28a", hash(meta));
            assertEquals("fb430972a9e564854833f6d5f82cf25244c7dd84cd457351636b6f0b10e2036d", hex(reads.digest()));
        }
    }

    @Test void actualLastArtByteIsRawDataEvenThoughItsShippedValueIsZero() throws Exception {
        for (var a : actual()) {
            var v = a.visual(); assertEquals(57120, v.rawArt().length()); assertEquals(57120, v.artReadView().length());
            assertArrayEquals(a.image().readFile(0x20000, 57120), v.rawArt().copy());
            assertArrayEquals(v.rawArt().copy(), v.artReadView().copy());
            assertThrows(IllegalStateException.class, v::alignmentByte);
            // Constructed mutation witnesses format semantics only; never submitted to a game source.
            byte[] art = v.rawArt().copy(); art[57119] = 0x5A;
            var changed = CdSonicVisualAssets.decode(FRAMES_167, v.mappings().copy(), v.dplcs().copy(), art);
            boolean reached = false;
            for (int f = 0; f < 167; f++) for (int i = 0; i < changed.dplcFrames().get(f).pieces().size(); i++) {
                var piece = changed.dplcFrames().get(f).pieces().get(i);
                if (piece.artByteOffset() + piece.byteLength() == 57120) {
                    assertEquals(0x5A, changed.artPiece(f, i).unsignedByte(piece.byteLength() - 1)); reached = true;
                }
            }
            assertTrue(reached); assertEquals(0x5A, changed.rawArt().unsignedByte(57119));
            assertThrows(IllegalStateException.class, changed::alignmentByte);
        }
    }

    @Test void sourceFormatSelectionIsExplicitAndCannotInventOrGuessPadding() throws Exception {
        assertArrayEquals(new CdSonicVisualAssets.SourceFormat[]{FRAMES_198_WITH_EVEN_BYTE, FRAMES_167}, CdSonicVisualAssets.SourceFormat.values());
        for (var a : actual()) {
            byte[] maps = a.visual().mappings().copy(), dplcs = a.visual().dplcs().copy(), art = a.visual().rawArt().copy();
            assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(maps, dplcs, art));
            assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(FRAMES_198_WITH_EVEN_BYTE, maps, dplcs, art));
            assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(FRAMES_167, maps, dplcs, Arrays.copyOf(art, 57119)));
            assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(FRAMES_167, maps, dplcs, Arrays.copyOf(art, 57121)));
            assertThrows(NullPointerException.class, () -> CdSonicVisualAssets.decode(null, maps, dplcs, art));
        }
    }

    @Test void constructed167DirectoriesRemainSignedEvenAndOutsideTheirDirectory() {
        var f = new Constructed167();
        assertEquals(167, f.decode().mappingFrames().size()); assertEquals(335, f.decode().mappings().length());
        for (int bad : new int[]{332, 335, 336, 0xFFFE}) {
            word(f.maps, 0, bad); assertThrows(IllegalArgumentException.class, f::decode); word(f.maps, 0, 334);
        }
        word(f.dplcs, 0, 0xFFFE); assertThrows(IllegalArgumentException.class, f::decode); word(f.dplcs, 0, 334);
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(FRAMES_167, Arrays.copyOf(f.maps, 333), f.dplcs, f.art));
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(FRAMES_167, f.maps, Arrays.copyOf(f.dplcs, 335), f.art));
    }

    @Test void constructed167BodiesCountsAndNativeOutputCapacityStayBounded() {
        var f = new Constructed167(); f.maps[334] = 1; assertThrows(IllegalArgumentException.class, f::decode); f.maps[334] = 0;
        word(f.dplcs, 334, 128); assertThrows(IllegalArgumentException.class, f::decode);
        word(f.dplcs, 334, 2); assertThrows(IllegalArgumentException.class, f::decode);
        byte[] overflow = Arrays.copyOf(f.dplcs, 340); word(overflow, 336, 0xF000); word(overflow, 338, 0xF010);
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(FRAMES_167, f.maps, overflow, f.art));
        word(f.dplcs, 334, 0);
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(FRAMES_167, Arrays.copyOf(f.maps, 2444), f.dplcs, f.art));
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(FRAMES_167, f.maps, Arrays.copyOf(f.dplcs, 1339), f.art));
    }

    @Test void constructed167ReadsReachExactlyTheLastRawTileAndRejectOverreads() {
        var f = new Constructed167(); word(f.dplcs, 334, 1); word(f.dplcs, 336, 0x06F8); f.art[57119] = 0x5A;
        var v = f.decode(); assertEquals(57088, v.dplcFrames().getFirst().pieces().getFirst().artByteOffset());
        assertEquals(0x5A, v.artPiece(166, 0).unsignedByte(31)); assertEquals(57120, v.rawArt().length());
        assertThrows(IllegalStateException.class, v::alignmentByte);
        word(f.dplcs, 336, 0x06F9); assertThrows(IllegalArgumentException.class, f::decode);
        word(f.dplcs, 336, 0x16F8); assertThrows(IllegalArgumentException.class, f::decode);
    }

    @Test void constructed167InputsAreDetachedAndPublishedViewsAndIndexesRemainBounded() {
        var f = new Constructed167(); word(f.dplcs, 334, 1); word(f.dplcs, 336, 0x06F8); f.art[57119] = 42; var v = f.decode();
        Arrays.fill(f.maps, (byte) 99); Arrays.fill(f.dplcs, (byte) 99); Arrays.fill(f.art, (byte) 99);
        assertEquals(0, v.mappingFrames().getFirst().pieceCount()); assertEquals(42, v.artPiece(166, 0).unsignedByte(31));
        assertThrows(ReadOnlyBufferException.class, () -> v.rawArt().buffer().put(0, (byte) 1));
        assertThrows(UnsupportedOperationException.class, () -> v.mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> v.dplcFrames().getFirst().pieces().clear());
        assertThrows(IndexOutOfBoundsException.class, () -> v.mappingFrames().get(167));
        assertThrows(IndexOutOfBoundsException.class, () -> v.artPiece(167, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> v.artReadView().unsignedByte(57120));
    }

    @Test void legacyDecodeThreeStillOwns198AndItsActualSingleEvenByte() {
        var f = new Constructed198(); word(f.dplcs, 396, 1); word(f.dplcs, 398, 0x087A); f.art[69470] = 42;
        var old = f.decode(); var explicit = CdSonicVisualAssets.decode(FRAMES_198_WITH_EVEN_BYTE, f.maps, f.dplcs, f.art);
        assertEquals(FRAMES_198_WITH_EVEN_BYTE, old.sourceFormat()); assertEquals(198, old.mappingFrames().size());
        assertEquals(198, old.dplcFrames().size()); assertEquals(69471, old.rawArt().length()); assertEquals(69472, old.artReadView().length());
        assertEquals(0, old.alignmentByte()); assertEquals(42, old.artPiece(30, 0).unsignedByte(30)); assertEquals(0, old.artPiece(30, 0).unsignedByte(31));
        assertArrayEquals(old.mappings().copy(), explicit.mappings().copy()); assertArrayEquals(old.dplcs().copy(), explicit.dplcs().copy());
        assertArrayEquals(old.artPiece(30, 0).copy(), explicit.artPiece(30, 0).copy());
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(FRAMES_167, f.maps, f.dplcs, f.art));
    }

    @Test void legacyDecodeThreeRetainsStrictBoundsAndExistingErrorMessages() {
        var f = new Constructed198(); f.art[69471] = 1;
        assertEquals("Sonic art requires the actual 69471 bytes plus one zero EVEN byte", assertThrows(IllegalArgumentException.class, f::decode).getMessage());
        f.art[69471] = 0;
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(f.maps, f.dplcs, Arrays.copyOf(f.art, 69471)));
        assertEquals("Sonic mappings extent", assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(Arrays.copyOf(f.maps, 397), f.dplcs, f.art)).getMessage());
        word(f.maps, 0, 394);
        assertEquals("Sonic frame relative pointer outside its data include", assertThrows(IllegalArgumentException.class, f::decode).getMessage());
        word(f.maps, 0, 396); word(f.dplcs, 396, 128);
        assertEquals("Unsupported native Sonic DPLC count", assertThrows(IllegalArgumentException.class, f::decode).getMessage());
        word(f.dplcs, 396, 1); word(f.dplcs, 398, 0x087B);
        assertEquals("DPLC reads beyond file-backed Sonic art including actual EVEN byte", assertThrows(IllegalArgumentException.class, f::decode).getMessage());
        byte[] overflow = Arrays.copyOf(f.dplcs, 402); word(overflow, 396, 2); word(overflow, 398, 0xF000); word(overflow, 400, 0xF010);
        assertEquals("DPLC frame exceeds native Sonic art buffer", assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(f.maps, overflow, f.art)).getMessage());
    }

    private static final class Constructed167 {
        final byte[] maps = table(167, 335), dplcs = table(167, 338), art = new byte[57120];
        CdSonicVisualAssets decode() { return CdSonicVisualAssets.decode(FRAMES_167, maps, dplcs, art); }
    }
    private static final class Constructed198 {
        final byte[] maps = table(198, 398), dplcs = table(198, 400), art = new byte[69472];
        CdSonicVisualAssets decode() { return CdSonicVisualAssets.decode(maps, dplcs, art); }
    }
    private static byte[] table(int count, int length) {
        byte[] data = new byte[length]; for (int i = 0; i < count; i++) word(data, i * 2, count * 2); return data;
    }
    private static byte[] directory(String text) { return HexFormat.of().parseHex(text.replaceAll("\\s", "")); }
    private static int u16(byte[] data, int at) { return (data[at] & 255) * 256 + (data[at + 1] & 255); }
    private static void word(byte[] data, int at, int value) { data[at] = (byte) (value >>> 8); data[at + 1] = (byte) value; }
    private static String hash(byte[] bytes) { return CdR11aProfile.sha256(bytes); }
    private static String hash(StringBuilder text) { return hash(text.toString().getBytes(StandardCharsets.UTF_8)); }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
    private record Actual(CdNativeFile file, CdMmdImage image, CdSonicVisualAssets visual) { }
    private List<Actual> retained;
    private List<Actual> actual() throws Exception {
        if (retained != null) return retained;
        var values = new ArrayList<Actual>();
        try (var source = CdProgramAssets.open(cue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assertEquals("a6b026a2381f926485cebb2da0bded29319cfd515ef7956a3ca79b18645bdb2b", source.identity().normalizedSha256());
            for (var file : List.of(CdNativeFile.R13C, CdNativeFile.R13D)) {
                var image = source.mmd(file); byte[] all = image.readFile(0, 262144);
                assertEquals(file == CdNativeFile.R13C ? "3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f"
                        : "27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356", hash(all));
                values.add(new Actual(file, image, CdSonicVisualAssets.decode(FRAMES_167,
                        image.readFile(0x2DF20, 2443), image.readFile(0x2E8AC, 1338), image.readFile(0x20000, 57120))));
            }
        }
        retained = List.copyOf(values); return retained;
    }
    private static Path cue() throws Exception {
        String root = System.getProperty("soniccd.media.root"); assumeTrue(root != null && !root.isBlank(), "Optional original Sonic CD media is not configured");
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must contain exactly one CUE"); return cues.getFirst();
        }
    }
}
