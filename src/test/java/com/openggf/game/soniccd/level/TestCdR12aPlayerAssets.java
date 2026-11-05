package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdProgramCatalogue;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdDiscHeader;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdObservedMediaIdentity;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Original-USA source authentication and finite-format witnesses; no playable Act2 admission.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Sonic/Main135-150,797-831,2645-2662,2951-3100;
 * Functions (Misc)69-101,143-150,175-230; Scroll (Act 2 Present)131-147; Data (Act 2 Present)116-126.
 * Expected fingerprints came from independent directive and raw-format Python models over the actual original
 * R12A MMD, not from executing Java. No native-source asset path, fallback bytes, Body/Frame/World, DPLC upload,
 * resident/current art, animation execution, player ingress, route/transition, parity or pixels are claimed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aPlayerAssets {
    private static final String MMD = "6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7";
    private static final String MANIFEST = """
ART_SONIC 20000 69472 94edeaff466c9e0c8ddd0b090a99b24bca663b8c40ac1dcd45c530de60e9ce64
MAP_SPR_SONIC 30f60 3004 88968a4a46556ad579811c935d1ad2f9eb2df08f4658c424a20f75c265edec0c
DPLC_SONIC 31b1c 1636 0c5298383621ae7515a6f3f4b6cdca6704d3a446ef15264f002f9e9fe5fbaaea
ANI_SONIC 582e 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
SONIC_MINI_ANIMATIONS 57de 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
SONIC_RAMP_CHUNKS 45a4 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
SPEC_CHUNKS 2a1c 4 674d922d5583edfb5fba68eb6619dd121fe0387c3bbf2e0504d95420827b2f2f
SINE_TABLE 872 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
ARC_TAN_TABLE b58 258 7d0ff986365e0596080368cab93935822e1f39220f50071db5dd075c0cabe66f
BG_CAMERA_SECT_IDS 3428 34 81e548cbfa2e4fc9719ba3b2009a8abb80bd2a54e3f3e8ec2187589e62e4801c
BG_CAMERA_SECTS 344a 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
            """;

    @Test void actualCatalogueAndWholeOriginalFileOwnDetachedAct2PlayerAndVisualValues() throws Exception {
        var f = retail(); var a = f.assets();
        assertEquals(CdReleaseVariant.USA, a.identity().variant()); assertEquals(CdStagePayload.R12A.stage(), a.identity().stage());
        assertEquals(CdPeriod.PRESENT, a.identity().stage().period()); assertEquals(2, a.identity().stage().actNumber());
        assertEquals(CdNativeFile.R12A, a.identity().file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, a.identity().domain());
        assertEquals(MMD, a.identity().mmdSha256()); assertEquals(f.identity().normalizedSha256(), a.identity().observedWholeDiscSha256());
        assertSame(a.sonic(), a.player().sonic()); assertSame(a.sonic(), a.sonic()); assertSame(a.player(), a.player());
        assertEquals(0, f.original().fileBackedCopyBytes()); assertEquals(0, f.original().unresolvedCopyBytes());
        assertNotEquals(MMD, CdR11aProfile.sha256(f.present1().readFile(0, 262144)));
    }

    @Test void profilePreservesTheOriginal28DescriptorsAndPinsItsHistoricalElevenTailSpans() throws Exception {
        var a = retail().assets(); var all = CdR12aProfile.Asset.values(); var rows = MANIFEST.lines().filter(s -> !s.isBlank()).toList();
        assertEquals(146, all.length); assertEquals(11, rows.size()); var prefix = new StringBuilder();
        for (int i = 0; i < 17; i++) prefix.append(all[i].name()).append(' ').append(Integer.toHexString(all[i].fileOffset()))
                .append(' ').append(all[i].length()).append('\n');
        assertEquals("1e096a08addb1fb4dbfefbfe2467149e5f0b6f7fe3b215263554876e94eba102", hash(prefix));
        for (int i = 0; i < rows.size(); i++) {
            var cells = rows.get(i).split(" "); var asset = all[17 + i]; var range = a.profile().range(asset);
            assertEquals(cells[0], asset.name()); assertEquals(Integer.parseInt(cells[1], 16), asset.fileOffset());
            assertEquals(Integer.parseInt(cells[2]), asset.length()); assertEquals(CdNativeFile.R12A, range.file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, range.domain()); assertEquals(0x200000 + asset.fileOffset(), range.nativeAddress());
            assertEquals(asset.fileOffset() + asset.length(), range.endExclusive()); assertEquals(cells[3], hash(a.view(asset).copy()), asset.name());
            assertArrayEquals(retail().original().readFile(asset.fileOffset(), asset.length()), a.view(asset).copy());
        }
        assertEquals(a.profile().range(LEVEL_BLOCKS), a.profile().readerRange(LEVEL_BLOCKS));
        assertEquals(10722, a.profile().range(ART_LEVEL_TILES).length()); assertEquals(10723, a.profile().readerRange(ART_LEVEL_TILES).length());
        assertEquals(a.profile().range(ART_SONIC), a.sonicArtReadRange()); assertThrows(NullPointerException.class, () -> a.view(null));
    }

    @Test void nativeAbsoluteAndPcIndexedOperandsSelectActualLiveTablesIncludingAct2SpecChunks() throws Exception {
        var m = retail().original(); var a = retail().assets();
        assertEquals("217c00230f600004", hex(m.readFile(0x3EFA, 8))); // ObjSonic_Init's live MOVE.L, not legacy labels.
        assertArrayEquals(a.view(MAP_SPR_SONIC).copy(), m.readFile(0x1F200, 3004));
        assertEquals(0x230F60, a.profile().range(MAP_SPR_SONIC).nativeAddress());
        assertEquals("43f90020582e", hex(m.readFile(0x553E, 6))); assertEquals("45f9002045a4", hex(m.readFile(0x4564, 6)));
        assertEquals("43f900202a1c21d1f7ac", hex(m.readFile(0x2A0C, 10)));
        assertEquals("45f900231b1c", hex(m.readFile(0x5A86, 6))); assertEquals("43f900220000", hex(m.readFile(0x5AB6, 6)));
        assertEquals("103b0004", hex(m.readFile(0x57D8, 4))); assertEquals(0x57DE, pcIndexTarget(m, 0x57D8));
        assertEquals("323b000c", hex(m.readFile(0x864, 4))); assertEquals(0x872, pcIndexTarget(m, 0x864));
        assertEquals("303b0004", hex(m.readFile(0x86C, 4))); assertEquals(0x872, pcIndexTarget(m, 0x86C));
        assertEquals("103b4036", hex(m.readFile(0xB20, 4))); assertEquals(0xB58, pcIndexTarget(m, 0xB20));
        assertEquals("903b302a", hex(m.readFile(0xB2C, 4))); assertEquals(0xB58, pcIndexTarget(m, 0xB2C));
        assertEquals("91b67f7f", hex(a.player().specialChunks().copy()));
    }

    @Test void all60FullAnimationBodiesPreserveActualDirectoryAliasesCommandsAndPadding() throws Exception {
        var a = retail().assets(); var p = a.player(); var table = a.view(ANI_SONIC); var fingerprint = new StringBuilder();
        var expected = "78 80 88 90 98 a0 b6 bc c0 c4 c8 cc d0 d4 da de e6 ee f2 f8 fe 102 10a 10e 112 116 120 124 128 12c 134 138 14e 152 158 15e 164 168 16c 170 178 17c 180 190 1b8 1be 1c6 1ce 1d4 1dc 1e4 1ea 1f6 202 20a 212 216 22c 20a 212".split(" ");
        assertEquals(60, p.animations().size()); assertArrayEquals(table.copy(), p.animationTable().copy());
        for (int id = 0; id < 60; id++) {
            int at = Integer.parseInt(expected[id], 16), end = table.length();
            for (String other : expected) { int next = Integer.parseInt(other, 16); if (next > at) end = Math.min(end, next); }
            var script = p.animation(id); assertEquals(at, script.relativeOffset()); assertEquals(end - at, script.bytes().length());
            assertArrayEquals(table.slice(at, end - at).copy(), script.bytes().copy());
            fingerprint.append(id).append(' ').append(Integer.toHexString(at)).append(' ').append(end - at).append(' ').append(hash(script.bytes().copy())).append('\n');
        }
        assertEquals("b3a9b6e328ba28f85516828dc3f997da66052391cfe4a23a79907596e99f9125", hash(fingerprint));
        assertEquals(p.animation(54).relativeOffset(), p.animation(58).relativeOffset());
        assertEquals(p.animation(55).relativeOffset(), p.animation(59).relativeOffset());
        assertEquals((byte) 0xFF, p.animation(0).signedDelayOrHandler());
        assertThrows(IndexOutOfBoundsException.class, () -> p.animation(-1)); assertThrows(IndexOutOfBoundsException.class, () -> p.animation(60));
    }

    @Test void completeMiniAndAtanSourceTailsRemainRawWhileTheirOnlyReachablePrefixesFeedFormatOwner() throws Exception {
        var a = retail().assets(); var p = a.player();
        assertEquals(80, a.view(SONIC_MINI_ANIMATIONS).length()); assertEquals(60, p.miniAnimations().length());
        assertArrayEquals(a.view(SONIC_MINI_ANIMATIONS).slice(0, 60).copy(), p.miniAnimations().copy());
        assertArrayEquals(new byte[20], a.view(SONIC_MINI_ANIMATIONS).slice(60, 20).copy());
        assertEquals("adcb220955503a487e05e9d1310f244e7c84e50c26c0fbf5c2a64e968f2f4c1a", hash(p.miniAnimations().copy()));
        for (int id = 0; id < 60; id++) assertTrue(p.miniAnimationId(id) < 60);
        assertEquals(258, a.view(ARC_TAN_TABLE).length()); assertEquals(257, p.arcTanTable().length());
        assertArrayEquals(a.view(ARC_TAN_TABLE).slice(0, 257).copy(), p.arcTanTable().copy());
        assertEquals(0, a.view(ARC_TAN_TABLE).unsignedByte(257)); assertEquals(0x20, p.arcTanByte(256));
        assertEquals("a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295", hash(p.arcTanTable().copy()));
        assertThrows(IndexOutOfBoundsException.class, () -> p.miniAnimationId(60)); assertThrows(IndexOutOfBoundsException.class, () -> p.arcTanByte(257));
    }

    @Test void actualRampSentinelSpecialChunkOrderAndSineCosineExtensionRemainFiniteRawValues() throws Exception {
        var a = retail().assets(); var p = a.player();
        assertEquals("060708494cff", hex(p.rampChunks().copy())); assertEquals("91b67f7f", hex(p.specialChunks().copy()));
        assertArrayEquals(a.view(SONIC_RAMP_CHUNKS).copy(), p.rampChunks().copy()); assertArrayEquals(a.view(SINE_TABLE).copy(), p.sineTable().copy());
        for (int i = 0; i < 64; i++) assertEquals(p.sineWord(i), p.sineWord(256 + i));
        assertEquals(256, p.sineWord(64)); assertEquals(-256, p.sineWord(192));
        assertEquals(115, p.sineWord(19)); assertEquals(-117, p.sineWord(147), "Keep the shipped asymmetry");
        assertThrows(IndexOutOfBoundsException.class, () -> p.sineWord(-1)); assertThrows(IndexOutOfBoundsException.class, () -> p.sineWord(320));
    }

    @Test void all198MappingFramesRetainEveryFiveBytePieceAndSourceAlias() throws Exception {
        var a = retail().assets(); var raw = a.view(MAP_SPR_SONIC); var frames = a.sonic().mappingFrames();
        assertEquals(198, frames.size()); int total = 0, maximum = 0; var distinct = new HashSet<Integer>(); var meta = new StringBuilder();
        for (int id = 0; id < 198; id++) {
            var frame = frames.get(id); int at = (short) raw.unsignedWord(id * 2), count = raw.unsignedByte(at);
            assertEquals(at, frame.relativeOffset()); assertEquals(count, frame.pieceCount());
            assertArrayEquals(raw.slice(at, 1 + 5 * count).copy(), frame.bytes().copy()); total += count; maximum = Math.max(maximum, count); distinct.add(at);
            meta.append(id).append(' ').append(Integer.toHexString(at)).append(' ').append(count).append(' ').append(hash(frame.bytes().copy())).append('\n');
        }
        assertEquals(515, total); assertEquals(140, distinct.size()); assertEquals(6, maximum);
        assertEquals("eee7c51a305c5fdfb51d43e3d4da42b067d79c50de0300c4be11651ba103a86f", hash(meta));
    }

    @Test void all198DplcsBoundEveryActualArtReadIncludingTheRealFinalZeroWithoutResidencyOrUpload() throws Exception {
        var a = retail().assets(); var raw = a.view(DPLC_SONIC); var visual = a.sonic(); var frames = visual.dplcFrames();
        assertEquals(198, frames.size()); int total = 0, maximum = 0, furthest = 0; var distinct = new HashSet<Integer>();
        var meta = new StringBuilder(); var artReads = MessageDigest.getInstance("SHA-256");
        for (int id = 0; id < 198; id++) {
            var frame = frames.get(id); int at = (short) raw.unsignedWord(id * 2), count = raw.unsignedWord(at), output = 0;
            assertEquals(at, frame.relativeOffset()); assertEquals(count, frame.pieces().size()); assertArrayEquals(raw.slice(at, 2 + 2 * count).copy(), frame.bytes().copy());
            for (int piece = 0; piece < count; piece++) {
                var entry = frame.pieces().get(piece); assertEquals(raw.unsignedWord(at + 2 + 2 * piece), entry.rawWord());
                assertTrue(entry.artByteOffset() + entry.byteLength() <= 69472); output += entry.byteLength();
                furthest = Math.max(furthest, entry.artByteOffset() + entry.byteLength()); artReads.update(visual.artPiece(id, piece).copy());
                assertArrayEquals(a.view(ART_SONIC).slice(entry.artByteOffset(), entry.byteLength()).copy(), visual.artPiece(id, piece).copy());
            }
            assertEquals(output, frame.outputBytes()); assertTrue(output <= 0x300); total += count; maximum = Math.max(maximum, output); distinct.add(at);
            meta.append(id).append(' ').append(Integer.toHexString(at)).append(' ').append(count).append(' ').append(output).append(' ').append(hash(frame.bytes().copy())).append('\n');
        }
        assertEquals(515, total); assertEquals(140, distinct.size()); assertEquals(736, maximum); assertEquals(69472, furthest);
        assertEquals("56c3910eda0c03fe5e0e24303bc6cafbee3ed0230d82a1f763cf4e046f94c204", hash(meta));
        assertEquals("018878f0d5fd3a496dfa0630c9b7bf44f8c80631e12741b8e12c9b69b35c85b9", hex(artReads.digest()));
        assertEquals(0x3877, frames.get(30).pieces().get(1).rawWord()); assertEquals(128, visual.artPiece(30, 1).length());
        assertEquals(0, visual.artPiece(30, 1).unsignedByte(127)); assertEquals(0, visual.alignmentByte());
        assertEquals(69471, visual.rawArt().length()); assertEquals(69472, visual.artReadView().length());
        assertEquals("5160ff8ea26ed050cd3f41aaf1689d4e05a58cf15f090c0ca910932e7f629955", hash(visual.rawArt().copy()));
    }

    @Test void originalFactoryBorrowsItsCallerAndDetachedByteCopiesCannotMutateAnyRetainedSource() throws Exception {
        try (var source = CdProgramAssets.open(cue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            var a = CdR12aPlayerAssets.fromPrograms(source);
            assertDoesNotThrow(() -> source.mmd(CdNativeFile.R12A)); assertEquals(MMD, hash(source.mmd(CdNativeFile.R12A).readFile(0, 262144)));
            assertEquals(MMD, a.identity().mmdSha256());
        }
        var a = retail().assets(); // Fixture's actual CdProgramAssets owner has already closed.
        for (var asset : CdR12aProfile.Asset.values()) {
            var before = a.view(asset).copy(); var copy = a.view(asset).copy(); Arrays.fill(copy, (byte) 0xFF);
            assertArrayEquals(before, a.view(asset).copy()); assertThrows(ReadOnlyBufferException.class, () -> a.view(asset).buffer().put(0, (byte) 0));
        }
        assertThrows(UnsupportedOperationException.class, () -> a.player().animations().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.sonic().mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.sonic().dplcFrames().get(30).pieces().clear());
    }

    @Test void actualFactoryRejectsWrongReleaseCatalogueOtherActAndEveryChangedPlayerSourceSpan() throws Exception {
        var f = retail(); assertThrows(NullPointerException.class, () -> CdR12aPlayerAssets.fromPrograms(null));
        var foreign = mock(CdProgramAssets.class); var foreignHeader = mock(CdDiscHeader.class);
        when(foreignHeader.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.EUROPE)); when(foreign.header()).thenReturn(foreignHeader);
        assertThrows(IOException.class, () -> CdR12aPlayerAssets.fromPrograms(foreign)); verify(foreign, never()).mmd(any());
        var unknown = source(f); var id = f.identity();
        when(unknown.identity()).thenReturn(new CdObservedMediaIdentity(id.definition(), "0".repeat(64), id.cue(), id.binaryFiles(), id.originalContainer(), id.tracks()));
        assertThrows(IOException.class, () -> CdR12aPlayerAssets.fromPrograms(unknown)); verify(unknown, never()).mmd(any());
        var missing = source(f); when(missing.catalogue()).thenReturn(Optional.empty());
        assertThrows(IOException.class, () -> CdR12aPlayerAssets.fromPrograms(missing)); verify(missing, never()).mmd(any());
        for (var asset : Arrays.asList(CdR12aProfile.Asset.values()).subList(17, 26)) {
            byte[] bytes = f.original().readFile(0, 262144); bytes[asset.fileOffset() + asset.length() - 1] ^= 1;
            var changed = source(f); when(changed.mmd(CdNativeFile.R12A)).thenReturn(CdMmdImage.parse(bytes));
            assertThrows(IOException.class, () -> CdR12aPlayerAssets.fromPrograms(changed), asset.name()); verify(changed, never()).close();
        }
        for (var image : new CdMmdImage[]{f.present1(), CdMmdImage.parse(f.original().readFile(0, 262143))}) {
            var wrong = source(f); when(wrong.mmd(CdNativeFile.R12A)).thenReturn(image);
            assertThrows(IOException.class, () -> CdR12aPlayerAssets.fromPrograms(wrong)); verify(wrong, never()).close();
        }
    }

    @Test void existingVisualFormatRejectsBadSelectedPointersDplcArtReadsAndMissingOrChangedFinalZero() throws Exception {
        var a = retail().assets(); byte[] maps = a.view(MAP_SPR_SONIC).copy(), dplcs = a.view(DPLC_SONIC).copy(), art = a.view(ART_SONIC).copy();
        byte[] badMap = maps.clone(); word(badMap, 0, 0xFFFE);
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(badMap, dplcs, art));
        byte[] badDplc = dplcs.clone(); int body = a.sonic().dplcFrames().get(1).relativeOffset(); word(badDplc, body, 128);
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(maps, badDplc, art));
        byte[] overread = dplcs.clone(); word(overread, body + 2, 0xFFFF);
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(maps, overread, art));
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(maps, dplcs, Arrays.copyOf(art, 69471)));
        byte[] nonzero = art.clone(); nonzero[69471] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(maps, dplcs, nonzero));
    }

    @Test void existingPlayerFormatRejectsWholeUnusedTailsAndMalformedLiveTablesInsteadOfInventingBounds() throws Exception {
        var a = retail().assets(); var p = a.player();
        assertThrows(IllegalArgumentException.class, () -> CdR11aPlayerAssets.decode(a.view(ANI_SONIC).copy(), a.view(SONIC_MINI_ANIMATIONS).copy(),
                p.rampChunks().copy(), p.specialChunks().copy(), p.sineTable().copy(), p.arcTanTable().copy(), a.sonic()));
        assertThrows(IllegalArgumentException.class, () -> CdR11aPlayerAssets.decode(a.view(ANI_SONIC).copy(), p.miniAnimations().copy(),
                p.rampChunks().copy(), p.specialChunks().copy(), p.sineTable().copy(), a.view(ARC_TAN_TABLE).copy(), a.sonic()));
        byte[] ani = p.animationTable().copy(); word(ani, 0, 0xFFFF);
        assertThrows(IllegalArgumentException.class, () -> decode(a, ani, p.miniAnimations().copy(), p.rampChunks().copy()));
        byte[] mini = p.miniAnimations().copy(); mini[0] = (byte) 255;
        assertThrows(IllegalArgumentException.class, () -> decode(a, p.animationTable().copy(), mini, p.rampChunks().copy()));
        byte[] ramp = p.rampChunks().copy(); ramp[5] = 0;
        assertThrows(IllegalArgumentException.class, () -> decode(a, p.animationTable().copy(), p.miniAnimations().copy(), ramp));
    }

    private static CdR11aPlayerAssets decode(CdR12aPlayerAssets a, byte[] ani, byte[] mini, byte[] ramp) {
        var p = a.player(); return CdR11aPlayerAssets.decode(ani, mini, ramp, p.specialChunks().copy(), p.sineTable().copy(), p.arcTanTable().copy(), a.sonic());
    }
    private static CdProgramAssets source(Fixture f) {
        var source = mock(CdProgramAssets.class); when(source.header()).thenReturn(f.header()); when(source.identity()).thenReturn(f.identity());
        when(source.catalogue()).thenReturn(Optional.of(f.catalogue())); return source;
    }
    /** Decode the signed displacement of the already asserted real PC-indexed data operand, not CPU execution. */
    private static int pcIndexTarget(CdMmdImage image, int instruction) { return instruction + 2 + image.readFile(instruction + 3, 1)[0]; }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static String hash(byte[] bytes) { return CdR11aProfile.sha256(bytes); }
    private static String hash(StringBuilder text) { return hash(text.toString().getBytes(StandardCharsets.UTF_8)); }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }

    private Fixture retained;
    private record Fixture(CdR12aPlayerAssets assets, CdMmdImage original, CdMmdImage present1,
                           CdObservedMediaIdentity identity, CdDiscHeader header, CdProgramCatalogue catalogue) { }
    private Fixture retail() throws Exception {
        if (retained != null) return retained;
        try (var source = CdProgramAssets.open(cue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Fixture(CdR12aPlayerAssets.fromPrograms(source), source.mmd(CdNativeFile.R12A), source.mmd(CdNativeFile.R11A),
                    source.identity(), source.header(), source.catalogue().orElseThrow());
        }
        return retained;
    }
    private static Path cue() throws Exception {
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Configure the original CD media root"); assertFalse(root.isBlank());
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); return cues.getFirst();
        }
    }
}
