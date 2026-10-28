package com.openggf.game.soniccd.media;

import com.openggf.data.cd.DiscTrack;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Finite program custody only. Positive authenticity requires actual supplied media;
 * constructed Mockito owners below exercise rejection paths only, never a usable positive bundle.
 */
class TestCdTimeWarpPrograms {
    private static final String WARP_SHA = "a417c910f61e0d95b33f292b3e84206e1f71018c4478c83da86737b10d0ffe06";
    private static final String PAST_SHA = "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final CdMmdHeader WARP_HEADER = new CdMmdHeader(0, 0, 0xFF2000, 0x0BFF, 0xFF2000, 0, 0);
    private static final CdMmdHeader PAST_HEADER = new CdMmdHeader(0, 0, 0, 0, 0x200100, 0x20010C, 0x200112);

    @Test void nullOwnerCannotCreateDetachedPrograms() {
        assertThrows(NullPointerException.class, () -> CdTimeWarpPrograms.fromPrograms(null));
    }

    @Test void unknownObservationStopsBeforeHeaderOrProgramReads() throws Exception {
        var programs = mock(CdProgramAssets.class);
        when(programs.identity()).thenReturn(new CdObservedMediaIdentity(CdObservedMediaIdentity.DEFINITION,
                "0".repeat(64), new CdObservedMediaIdentity.FileHash("constructed.cue", 0, "0".repeat(64)),
                List.of(), Optional.empty(), List.of()));
        var failure = assertThrows(IOException.class, () -> CdTimeWarpPrograms.fromPrograms(programs));
        assertTrue(failure.getMessage().contains("known retail"));
        verify(programs, never()).header(); verify(programs, never()).mmd(any());
    }

    @Test void claimedKnownDigestWithoutConsistentTrackDescriptorsNeverAdmitsAHeaderOrFile() throws Exception {
        var original = constructedUsaObservation(); var tracks = new ArrayList<>(original.tracks());
        var track = tracks.get(12);
        tracks.set(12, new CdObservedMediaIdentity.TrackContent(track.number(), track.mode(), track.discIndex01Frame(),
                track.programFrames(), track.storedPregapFrames(), track.syntheticPregapFrames(), "0".repeat(64)));
        var programs = mock(CdProgramAssets.class);
        when(programs.identity()).thenReturn(new CdObservedMediaIdentity(original.definition(), original.normalizedSha256(),
                original.cue(), original.binaryFiles(), original.originalContainer(), tracks));
        assertThrows(IOException.class, () -> CdTimeWarpPrograms.fromPrograms(programs));
        verify(programs, never()).header(); verify(programs, never()).mmd(any());
    }

    @Test void constructedUsaDescriptorsWithForeignOrUnknownHeaderStopBeforeFiles() throws Exception {
        for (var header : List.of(header(CdReleaseVariant.JAPAN), header(CdReleaseVariant.EUROPE),
                new CdDiscHeader("SEGADISCSYSTEM", "UNKNOWN", "00000000", "UNKNOWN", "U"))) {
            var programs = negativeUsaOwner(); when(programs.header()).thenReturn(header);
            var failure = assertThrows(IOException.class, () -> CdTimeWarpPrograms.fromPrograms(programs));
            assertTrue(failure.getMessage().contains("USA")); verify(programs, never()).mmd(any());
        }
    }

    @Test void wrongWarpHeaderOrFileExtentRejectsBeforeAnyPastRead() throws Exception {
        var headers = List.of(new CdMmdHeader(0x40, 0, 0xFF2000, 0x0BFF, 0xFF2000, 0, 0),
                new CdMmdHeader(0, 1, 0xFF2000, 0x0BFF, 0xFF2000, 0, 0),
                new CdMmdHeader(0, 0, 0xFF2002, 0x0BFF, 0xFF2000, 0, 0),
                new CdMmdHeader(0, 0, 0xFF2000, 0x0BFE, 0xFF2000, 0, 0),
                new CdMmdHeader(0, 0, 0xFF2000, 0x0BFF, 0xFF2002, 0, 0),
                new CdMmdHeader(0, 0, 0xFF2000, 0x0BFF, 0xFF2000, 0xFF2100, 0),
                new CdMmdHeader(0, 0, 0xFF2000, 0x0BFF, 0xFF2000, 0, 0xFF2100),
                new CdMmdHeader(0, 0, 0, 0x0BFF, 0xFF2000, 0, 0));
        for (var header : headers) {
            var programs = negativeUsaOwner(); when(programs.mmd(CdNativeFile.WARP)).thenReturn(image(header, 7242));
            var failure = assertThrows(IOException.class, () -> CdTimeWarpPrograms.fromPrograms(programs));
            assertTrue(failure.getMessage().contains("header or file extent"));
            verify(programs, never()).mmd(CdNativeFile.R11B);
        }
        for (int length : new int[]{7241, 7243}) {
            var programs = negativeUsaOwner(); when(programs.mmd(CdNativeFile.WARP)).thenReturn(image(WARP_HEADER, length));
            var failure = assertThrows(IOException.class, () -> CdTimeWarpPrograms.fromPrograms(programs));
            assertTrue(failure.getMessage().contains("header or file extent"));
            verify(programs, never()).mmd(CdNativeFile.R11B);
        }
    }

    @Test void matchingWarpHeaderWithInventedPayloadDoesNotAuthenticateContent() throws Exception {
        var programs = negativeUsaOwner(); when(programs.mmd(CdNativeFile.WARP)).thenReturn(image(WARP_HEADER, 7242));
        var failure = assertThrows(IOException.class, () -> CdTimeWarpPrograms.fromPrograms(programs));
        assertTrue(failure.getMessage().contains("content")); verify(programs, never()).mmd(CdNativeFile.R11B);
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void originalMediaDetachesOnlyTheAuthenticatedUsaProgramsAndKeepsFileBackedDomains(CdReleaseVariant variant)
            throws Exception {
        var programs = open(variant);
        CdTimeWarpPrograms detached; CdMmdImage originalWarp; CdMmdImage originalPast;
        try (programs) {
            if (variant != CdReleaseVariant.USA) {
                var failure = assertThrows(IOException.class, () -> CdTimeWarpPrograms.fromPrograms(programs));
                assertTrue(failure.getMessage().contains("USA"));
                assertNotNull(programs.entry(CdNativeFile.WARP)); assertNotNull(programs.entry(CdNativeFile.R11B));
                return;
            }
            detached = CdTimeWarpPrograms.fromPrograms(programs);
            originalWarp = programs.mmd(CdNativeFile.WARP); originalPast = programs.mmd(CdNativeFile.R11B);
        }
        assertThrows(IOException.class, () -> programs.entry(CdNativeFile.WARP));
        assertEquals(new CdTimeWarpPrograms.Source(CdReleaseVariant.USA, CdRetailReleases.USA.normalizedSha256(),
                WARP_SHA, PAST_SHA), detached.source());
        assertSame(detached.warp(), detached.warp()); assertSame(detached.r11b(), detached.r11b());
        assertEquals(7242, detached.warp().fileBytes()); assertEquals(262144, detached.r11b().fileBytes());
        assertEquals(WARP_SHA, sha(detached.warp().readFile(0, 7242)));
        assertEquals(PAST_SHA, sha(detached.r11b().readFile(0, 262144)));
        assertArrayEquals(originalWarp.readFile(0, 7242), detached.warp().readFile(0, 7242));
        assertArrayEquals(originalPast.readFile(0, 262144), detached.r11b().readFile(0, 262144));
        assertEquals(WARP_HEADER, detached.warp().header()); assertEquals(PAST_HEADER, detached.r11b().header());
        assertEquals(12288, detached.warp().header().declaredCopyBytes());
        assertEquals(6986, detached.warp().fileBackedCopyBytes()); assertEquals(5302, detached.warp().unresolvedCopyBytes());
        var copy = detached.warp().relocatedCopy().orElseThrow();
        assertEquals(CdMmdImage.Domain.RELOCATED_COPY, copy.domain()); assertEquals(0xFF2000, copy.address());
        assertEquals(6986, copy.length()); assertEquals(new CdMmdImage.FileSlice(0x100, 6986), copy.fileSlice());
        assertEquals(0, detached.r11b().fileBackedCopyBytes()); assertEquals(0, detached.r11b().unresolvedCopyBytes());
        assertTrue(detached.r11b().relocatedCopy().isEmpty());
        assertArrayEquals(detached.warp().readFile(0x100, 6986), bytes(detached.warp().nativeSlice(
                CdMmdImage.Domain.RELOCATED_COPY, 0xFF2000, 6986)));
        assertEquals(0, detached.warp().nativeSlice(CdMmdImage.Domain.RELOCATED_COPY, 0xFF3B4A, 0).remaining());
        assertThrows(IllegalArgumentException.class, () -> detached.warp().nativeSlice(CdMmdImage.Domain.RELOCATED_COPY, 0xFF3B4A, 1));
        assertThrows(IllegalArgumentException.class, () -> detached.warp().nativeSlice(CdMmdImage.Domain.RELOCATED_COPY, 0xFF5000, 1));
        assertThrows(IllegalArgumentException.class, () -> detached.warp().nativeSlice(CdMmdImage.Domain.RELOCATED_COPY, 0x200100, 1));
        assertThrows(IllegalArgumentException.class, () -> detached.r11b().nativeSlice(CdMmdImage.Domain.RELOCATED_COPY, 0xFF2000, 1));
        assertEquals(0, detached.warp().readFile(7242, 0).length);
        for (long offset : new long[]{-1, 7242, Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> detached.warp().readFile(offset, 1));
        byte[] changed = detached.warp().readFile(0x100, 8); int original = detached.warp().readFile(0x100, 1)[0];
        changed[0] ^= 0x55; assertEquals(original, detached.warp().readFile(0x100, 1)[0]);
        var first = detached.warp().fileSlice(0x100, 8); var second = detached.warp().fileSlice(0x100, 8);
        first.get(); assertEquals(0, second.position()); assertEquals(1, first.position());
        assertThrows(ReadOnlyBufferException.class, () -> second.put(0, (byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> detached.r11b().nativeSlice(
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x200100, 4).put(0, (byte) 0));
        assertEquals("4816a3bcc085395430481ff295e5c2543aff36bc393f8bed5145d3d15064d875",
                sha(detached.warp().readFile(0x708, 64)));
        assertEquals("75a5361ed9b0766746faa65cbb2eac63cf2bcebb5f66f619a3e9f45c31b9252a",
                sha(detached.r11b().readFile(0x10000, 54272)));
    }

    @Test void actualUsaWarpDoesNotAllowMalformedPastOrSameHeaderR11aAsItsDestination() throws Exception {
        try (var actual = open(CdReleaseVariant.USA)) {
            var warp = actual.mmd(CdNativeFile.WARP); var past = actual.mmd(CdNativeFile.R11B);
            var present = actual.mmd(CdNativeFile.R11A);
            assertEquals(past.header(), present.header(), "The shared header is insufficient to identify the Past program");
            var changed = past.readFile(0, past.fileBytes()); changed[0x100] ^= 1;
            var shortFile = past.readFile(0, past.fileBytes() - 1);
            var badHeader = past.readFile(0, past.fileBytes()); badHeader[1] = 1;
            var negatives = List.of(present, CdMmdImage.parse(changed), CdMmdImage.parse(shortFile), CdMmdImage.parse(badHeader));
            for (var wrong : negatives) {
                var programs = mock(CdProgramAssets.class);
                when(programs.identity()).thenReturn(actual.identity()); when(programs.header()).thenReturn(actual.header());
                when(programs.mmd(CdNativeFile.WARP)).thenReturn(warp); when(programs.mmd(CdNativeFile.R11B)).thenReturn(wrong);
                var failure = assertThrows(IOException.class, () -> CdTimeWarpPrograms.fromPrograms(programs));
                assertTrue(failure.getMessage().contains("R11B"));
            }
        }
    }

    private static CdDiscHeader header(CdReleaseVariant variant) {
        return new CdDiscHeader("SEGADISCSYSTEM", variant.discHeaderVolumeId(), variant.sourceBuildDate(),
                variant.productId(), variant.headerMarket());
    }
    private static CdMmdImage image(CdMmdHeader header, int length) throws IOException {
        byte[] bytes = new byte[length];
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).put((byte) header.flags()).put((byte) header.reserved())
                .putInt((int) header.origin()).putShort((short) header.sizeCounter()).putInt((int) header.entry())
                .putInt((int) header.horizontalInterrupt()).putInt((int) header.verticalInterrupt());
        return CdMmdImage.parse(bytes);
    }
    private static CdProgramAssets negativeUsaOwner() {
        var programs = mock(CdProgramAssets.class);
        var observed = constructedUsaObservation();
        assertEquals(CdRetailReleases.USA, CdRetailReleases.find(observed).orElseThrow(), "Constructed descriptor control only");
        when(programs.identity()).thenReturn(observed); when(programs.header()).thenReturn(header(CdReleaseVariant.USA));
        return programs;
    }
    private static CdObservedMediaIdentity constructedUsaObservation() {
        // Metadata only, copied exactly from immutable RetailProfileFixtures at462b56ed8.
        // It permits negative component tests past the known-record predicate; it authenticates no supplied bytes.
        String rows = """
                59261 0 fed168f4048a8867a6795c98bee1fdffd25dcb33e2bd8ce5913c7780dd9028fc
                462 150 76b5baaaf2f688826d00b3bbc0d442ec2facae3a5c91a8f337774086b9cb0d1d
                8341 152 1c2564f743f859f8017901cce6a3e31e69822a676d09ec086dfe8d98c38be448
                6153 150 8b2d14f3bc5ac84cfdc1cadd34072a4fc115d99cd99aa92728b954494e2a71b5
                6460 150 2a4c444a1cc2bdb315a01c1025999e37450cea47061414beea9de7c18a1b7d3d
                7580 150 f4654de4b433a964a235d2e5afdda57392e9eb30d2b84828afdefad4ed17a3a0
                6080 150 462b1d4b873fdcafbacc86193b4ca57c2472ee235187598556c681165d51d205
                6224 150 eb468da3443b5c843bc3c3d5855ecd008d8bcc8fb22fec1d6d469f5b5579521f
                8712 152 7bb1d0523a5fc532fe1ebde50a39d52bc0271ab4b0585dc23d8cd37a08839ce1
                6079 152 84223974dc86de65b0e8393d137d07acee7b5204c5aee5bfe1fa494b5bd0c56d
                5929 150 7691b0ca1adacd2aef0cc2f280a2094248b6ba4399e262ff8594da615ce6251b
                7802 150 f38a8d47302f536d62917147792b829d2b2aaa330030e3506c11eeca7b1e5ec5
                5958 152 1ee1b0d2a3d0f9af269ff375fa5addf451b7ed72525a169c4c5d13ccab5c3b5b
                4962 152 a78004680b4e893fa3a99a3f01fd81b36e3a5589296fc67f55ec9307c74c99b2
                6978 152 a700110dae78d5f6df2f427b174b0126a8f24ef61dea8ab22ba14dbab16c92e1
                4735 152 c832d40d470e1e4e910861d2e2a6098b9032dcde226bad260dde7567e597dd49
                6083 152 7b3e182a84dff8dba50e64f805ea029937f039cf3638634c852cae4a641b662c
                8710 150 e3d61c09745cb32a1b0626470a0501260f0e8a379459fcbc9975a95106f1df35
                5560 150 76306470ea6a0a37e193d35ed0a9b1f948c14232a39c4cd87e67800d064df304
                5104 150 b3ccf01500d63a3821809b9edee910e5dcb430b430dcd2453cc40fa7e1914f35
                6459 152 f3e102e176a6915a034f730d53ae1941cf7e09f7d871471f9f428d03d6ba3017
                5032 150 32a17bc0029882828d56f69b5a2de7739efbe86f99ff443d6102f629f2dc2b53
                5554 150 73cacf8006980d9d73e2b043f8137e87d8846199042c86f69e603e616c37d936
                6081 152 ec7b39734af6a3384e25dce34cc9e723ed4ad1f930723db42ac9c353eae107a8
                6374 150 921053dc10c67207384a9becdc54ef23ed2f156b6f4bded1161ee01d06f0f84a
                2328 152 0602d1003eacec8ecd8416d8fb2805b2b5537075392e339e76552b08ab9e54b8
                2326 150 cc9a69d70fb1742eb3c57714a7caa58d8f880a163e8c6320feaeb99ff399a455
                785 150 80d71c4ed57b76c7bb88d11e79bdafbc844276c1eb69de5542aeb77cbb573cf2
                1799 150 71032f7ee7bada5f3f7b7d6574f2871ded4a0e22c03471e2c375be5ada3063b0
                1765 152 9d2e86c62fbad03c411be1b4e5bcfbc4b165ccc7d690dd95884900fbe7072e3d
                976 150 a6183797b3a0b17ca443e293e827d9742ae6cae8d52afac0f8cd1873929d74fc
                9235 150 8615f39ef544d9bb50e6d94827a49334baaf6eb19690c8e0dc1b7c16dd3aef0b
                9384 150 d388c0fd0fa5d749ee9f0c64ba4792d6a63ff06863bedab6f8211c2068cb6620
                6975 150 0cd9dc83f3fe6be0756c3502521d3ed7ee3c61107199dc488b87b0fcda8fde97
                13580 152 7fb689e9a16a7de0e26127a21996d24dd11a6b1ac38185eeb8b0306e4e052df9
                """;
        var tracks = new ArrayList<CdObservedMediaIdentity.TrackContent>(); long cursor = 0;
        for (String row : rows.strip().lines().toList()) {
            var columns = row.strip().split(" "); long frames = Long.parseLong(columns[0]); long gap = Long.parseLong(columns[1]);
            int number = tracks.size() + 1;
            tracks.add(new CdObservedMediaIdentity.TrackContent(number,
                    number == 1 ? DiscTrack.Mode.MODE1_2352 : DiscTrack.Mode.AUDIO,
                    cursor + gap, frames - gap, gap, 0, columns[2]));
            cursor += frames;
        }
        return new CdObservedMediaIdentity(CdObservedMediaIdentity.DEFINITION, CdRetailReleases.USA.normalizedSha256(),
                new CdObservedMediaIdentity.FileHash("constructed-not-authenticated.cue", 0, "0".repeat(64)),
                List.of(), Optional.empty(), tracks);
    }
    private static CdProgramAssets open(CdReleaseVariant variant) throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for actual warp-program checks");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var files = Files.list(Path.of(root).resolve(directory))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); return CdProgramAssets.open(cues.getFirst(), Optional.of(variant), Optional.empty());
        }
    }
    private static byte[] bytes(ByteBuffer buffer) { byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); return bytes; }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    @Test void actualWarpArtHasDistinctJavaAndNativeStyleFiniteReadersWithoutSyntheticPadding() throws Exception {
        CdTimeWarpPrograms programs;
        try (var owner = open(CdReleaseVariant.USA)) { programs = CdTimeWarpPrograms.fromPrograms(owner); }
        var warp = programs.warp(); // Every actual file view remains owned after the CUE closes.
        byte[] javaView = warp.readFile(0x748, 1536);
        byte[] nativeView = warp.readFile(0x748, 1537);
        assertEquals(0x8065, ByteBuffer.wrap(javaView).getShort() & 0xFFFF);
        assertEquals(0, javaView[1535]); assertEquals(0x20, nativeView[1536]);
        assertEquals(1, warp.readFile(0xD49, 1)[0]);
        assertEquals("c4c472457c5d8ceb868fd30bc2371ea1fef7c4ac9259c134340594a50a5ca2f3", sha(javaView));
        assertEquals("60f682739bc23e9be1b1874d42477223a93be4b2d5d4f850e6c4f49e2b3a1bf6", sha(nativeView));
        var decoded = com.openggf.game.soniccd.level.CdNemesisAsset.decode(javaView, 3232, 3232);
        assertEquals(1536, decoded.consumedInputBytes());
        assertEquals("453513fafdfe7f1c9d6d28ae22c9ec36b3158315945508790c9ad2cc7d4d4d81", sha(decoded.bytes().copy()));
        assertThrows(IOException.class, () -> com.openggf.game.soniccd.level.CdNemesisAsset.decode(
                java.util.Arrays.copyOf(javaView, 1535), 3232, 3232));
        for (int seedPattern : new int[]{0, 0xA5}) {
            byte[] seed = new byte[512]; java.util.Arrays.fill(seed, (byte) seedPattern);
            byte[] output = new byte[3232]; var end = decodeWarpWords(nativeView, seed, output);
            assertArrayEquals(decoded.bytes().copy(), output);
            assertEquals(1537, end.inputOffset()); assertEquals(0xFF2C49L, end.sourcePointer());
            assertEquals(0x20, end.readWord()); assertEquals(16, end.shiftWord());
            assertEquals(0, end.remainingTiles()); assertEquals(0, end.repeatWord());
            var shortView = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.prepare(
                    com.openggf.game.soniccd.level.CdAssetBytes.copyOf(javaView), 0xFF2648L,
                    com.openggf.game.soniccd.level.CdAssetBytes.copyOf(seed));
            for (int tile = 0; tile < 100; tile++)
                shortView = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.decodeTile(shortView).state();
            var lastCompleteTile = shortView;
            var stopped = assertThrows(com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.DecodeException.class,
                    () -> com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.decodeTile(lastCompleteTile));
            assertEquals(com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED, stopped.reason());
            assertEquals("CODE_REFILL", stopped.diagnostic().phase());
            assertEquals(1536, stopped.diagnostic().inputOffset()); assertEquals(0xFF2C48L, stopped.diagnostic().sourcePointer());
            assertEquals(0, stopped.diagnostic().pixelsInRow()); assertEquals(28, stopped.diagnostic().emittedRows().length());
            assertArrayEquals(java.util.Arrays.copyOfRange(output, 3200, 3228), stopped.diagnostic().emittedRows().copy());
            assertSame(lastCompleteTile, stopped.diagnostic().boundaryState()); assertEquals(1, lastCompleteTile.remainingTiles());
        }
        // Comparison-only controls: output equality alone cannot establish the native read extent.
        byte[] changedRead = nativeView.clone(); changedRead[1536] = (byte) 0xA5;
        byte[] controlledOutput = new byte[3232]; var changedEnd = decodeWarpWords(changedRead, new byte[512], controlledOutput);
        assertArrayEquals(decoded.bytes().copy(), controlledOutput); assertEquals(0xA5, changedEnd.readWord());
        byte[] changedUnread = warp.readFile(0x748, 1538); changedUnread[1537] = (byte) 0xFF;
        var unreadEnd = decodeWarpWords(changedUnread, new byte[512], controlledOutput);
        assertArrayEquals(decoded.bytes().copy(), controlledOutput); assertEquals(1537, unreadEnd.inputOffset());
        assertEquals(0x20, unreadEnd.readWord()); assertEquals(5302, warp.unresolvedCopyBytes());
    }

    private static com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.State decodeWarpWords(
            byte[] source, byte[] seed, byte[] output) {
        var state = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.prepare(
                com.openggf.game.soniccd.level.CdAssetBytes.copyOf(source), 0xFF2648L,
                com.openggf.game.soniccd.level.CdAssetBytes.copyOf(seed));
        assertTrue(state.xorMode()); assertEquals(101, state.remainingTiles());
        for (int tile = 0; tile < 101; tile++) {
            var result = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.decodeTile(state);
            System.arraycopy(result.bytes().copy(), 0, output, tile * 32, 32); state = result.state();
        }
        return state;
    }

}
