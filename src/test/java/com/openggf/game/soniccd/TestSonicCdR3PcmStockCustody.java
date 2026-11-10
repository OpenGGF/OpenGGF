package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdR3PcmSource;
import com.openggf.game.soniccd.release.CdRetailRelease;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST, author Java UNRUN. Actual detached source custody, never installation/audio/World admission. */
class TestSonicCdR3PcmStockCustody {
    private static final String R3_SHA = "82b9ac37eddbf379155ab76407efc1596e61c7707c592eb47e41cfc468012340";
    @TempDir Path temporary;
    private record SourceKey(String root, CdReleaseVariant variant) { }
    private static final Map<SourceKey, CdGameDataSource> SOURCES = new HashMap<>();

    @Test void originalUsaStockRetainsTheCompleteR3FileAfterItsCueCloses() throws Exception {
        var source = source(CdReleaseVariant.USA);
        var pcm = source.r3PcmSource().orElseThrow();
        assertSame(CdRetailReleases.USA, source.release());
        assertEquals(CdNativeFile.R3PCM, pcm.file());
        assertEquals(CdReleaseVariant.USA, pcm.source().variant());
        assertEquals(source.assets().identity().observedWholeDiscSha256(), pcm.source().wholeDiscSha256());
        assertEquals(source.release().normalizedSha256(), pcm.source().wholeDiscSha256());
        assertEquals(R3_SHA, pcm.source().fileSha256());
        assertEquals(242597, pcm.bytes().length()); assertEquals(R3_SHA, sha(pcm.bytes().copy()));
        assertArrayEquals("SNCBNK27.S28    ".getBytes(StandardCharsets.US_ASCII), pcm.bytes().slice(0,16).copy());
        assertEquals(0x4EFA0906L, pcm.bytes().unsignedLong(0x10));
        assertEquals(0x4EFA0F90L, pcm.bytes().unsignedLong(0x14));
        assertArrayEquals(new byte[0x900], pcm.bytes().slice(0x18,0x900).copy());
        assertEquals("cfc335996cfae29fdf14fd2db7ea853366d350a5d387aa3c322616e81dacd56b",
                sha(pcm.bytes().slice(0x18,0x900).copy()));
        assertSame(pcm, source.r3PcmSource().orElseThrow()); assertTrue(source.rom().isEmpty());
    }

    @Test void independentlyAuthenticatedFactoryMatchesRetainedBytesWithoutAliasingMutableViews() throws Exception {
        var source = source(CdReleaseVariant.USA); var retained = source.r3PcmSource().orElseThrow();
        CdR3PcmSource independent;
        try (var programs = CdProgramAssets.open(suppliedCue(CdReleaseVariant.USA), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            independent = CdR3PcmSource.fromPrograms(programs);
            assertEquals(source.release().normalizedSha256(), programs.identity().normalizedSha256());
            assertEquals(242597, programs.entry(CdNativeFile.R3PCM).size());
            assertArrayEquals(programs.read(CdNativeFile.R3PCM,0,242597), retained.bytes().copy());
        }
        assertNotSame(retained, independent); assertNotSame(retained.bytes(), independent.bytes());
        assertEquals(retained.source(), independent.source()); assertEquals(retained.file(), independent.file());
        assertArrayEquals(retained.bytes().copy(), independent.bytes().copy());
        byte[] copy = retained.bytes().copy(); int first = retained.bytes().unsignedByte(0); copy[0] ^= 0x55;
        assertEquals(first, retained.bytes().unsignedByte(0));
        var a = retained.bytes().buffer(); var b = retained.bytes().buffer(); a.get(); assertEquals(0,b.position());
        assertThrows(ReadOnlyBufferException.class, () -> b.put(0,(byte)0));
        assertThrows(IndexOutOfBoundsException.class, () -> retained.bytes().unsignedByte(242597));
    }

    @Test void allLegacyConstructorsThroughNineExplicitlyOmitR3AndNineStillRetainsInitialSave() throws Exception {
        var s = source(CdReleaseVariant.USA);
        var candidates = List.of(
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets()));
        for (var candidate : candidates) {
            assertTrue(candidate.r3PcmSource().isEmpty()); assertEquals(s.identity(),candidate.identity());
            assertSame(s.release(),candidate.release()); assertSame(s.assets(),candidate.assets());
            assertSame(s.fmSoundAssets().orElseThrow(),candidate.fmSoundAssets().orElseThrow());
        }
        var nine = candidates.getLast();
        assertSame(s.timeWarpPrograms().orElseThrow(),nine.timeWarpPrograms().orElseThrow());
        assertSame(s.act2Assets().orElseThrow(),nine.act2Assets().orElseThrow());
        assertSame(s.act2PastAssets().orElseThrow(),nine.act2PastAssets().orElseThrow());
        assertSame(s.act3FutureAssets().orElseThrow(),nine.act3FutureAssets().orElseThrow());
        assertSame(s.r31aAssets().orElseThrow(),nine.r31aAssets().orElseThrow());
        assertSame(s.initialSaveAssets().orElseThrow(),nine.initialSaveAssets().orElseThrow());
        assertTrue(candidates.subList(0,6).stream().allMatch(v -> v.initialSaveAssets().isEmpty()));
    }

    @Test void explicitTenRetainsEveryExactEarlierOwnerAndIdentityWithoutPresenceNormalization() throws Exception {
        var s = source(CdReleaseVariant.USA); var retained = retain(s,s.r3PcmSource());
        assertSame(s.r3PcmSource().orElseThrow(),retained.r3PcmSource().orElseThrow());
        assertSame(s.release(),retained.release()); assertSame(s.assets(),retained.assets());
        assertSame(s.fmSoundAssets().orElseThrow(),retained.fmSoundAssets().orElseThrow());
        assertSame(s.timeWarpPrograms().orElseThrow(),retained.timeWarpPrograms().orElseThrow());
        assertSame(s.act2Assets().orElseThrow(),retained.act2Assets().orElseThrow());
        assertSame(s.act2PastAssets().orElseThrow(),retained.act2PastAssets().orElseThrow());
        assertSame(s.act3FutureAssets().orElseThrow(),retained.act3FutureAssets().orElseThrow());
        assertSame(s.r31aAssets().orElseThrow(),retained.r31aAssets().orElseThrow());
        assertSame(s.initialSaveAssets().orElseThrow(),retained.initialSaveAssets().orElseThrow());
        assertEquals(s.identity(),retained.identity());
        var absent = retain(s,Optional.empty()); assertTrue(absent.r3PcmSource().isEmpty());
        assertSame(s.initialSaveAssets().orElseThrow(),absent.initialSaveAssets().orElseThrow());
        assertEquals(s.identity(),absent.identity());
    }

    @ParameterizedTest(name="{0}: R3 source remains absent and foreign USA retention rejects")
    @EnumSource(value=CdReleaseVariant.class,names={"JAPAN","EUROPE"})
    void regionsKeepAbsenceAndRejectGenuineForeignR3(CdReleaseVariant variant) throws Exception {
        var other = source(variant); assertTrue(other.r3PcmSource().isEmpty());
        assertTrue(retain(other,Optional.empty()).r3PcmSource().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> retain(other,source(CdReleaseVariant.USA).r3PcmSource()));
        assertTrue(other.fmSoundAssets().isEmpty()); assertTrue(other.timeWarpPrograms().isEmpty());
        assertTrue(other.initialSaveAssets().isEmpty()); assertTrue(other.r31aAssets().isEmpty());
    }

    @Test void nullAndContradictoryMetadataRejectWithoutMintingAnOwnerOrChangingOldErrorPrecedence() throws Exception {
        var s = source(CdReleaseVariant.USA);
        assertThrows(NullPointerException.class, () -> retain(s,null));
        var first = assertThrows(NullPointerException.class, () -> new CdGameDataSource(null,null,null,null,null,null,null,null,null,null));
        assertEquals("release",first.getMessage());
        var wrong = new CdRetailRelease("constructed-invalid-disc",CdReleaseVariant.USA,"0".repeat(64),s.release().reference());
        assertThrows(IllegalArgumentException.class, () -> new CdGameDataSource(wrong,s.assets(),s.fmSoundAssets(),
                s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets(),s.r3PcmSource()));
        // Negative metadata stubs only. No private factory or usable positive source is forged.
        var id = s.assets().identity();
        for (var foreign : List.of(
                new CdR11aAssets.Identity(id.variant(),CdStagePayload.R31A.stage(),id.file(),id.domain(),id.mmdSha256(),id.observedWholeDiscSha256()),
                new CdR11aAssets.Identity(id.variant(),id.stage(),CdNativeFile.R31A,id.domain(),id.mmdSha256(),id.observedWholeDiscSha256()),
                new CdR11aAssets.Identity(id.variant(),id.stage(),id.file(),CdMmdImage.Domain.RELOCATED_COPY,id.mmdSha256(),id.observedWholeDiscSha256()),
                new CdR11aAssets.Identity(id.variant(),id.stage(),id.file(),id.domain(),"0".repeat(64),id.observedWholeDiscSha256()))) {
            var stage = org.mockito.Mockito.mock(CdR11aAssets.class);
            org.mockito.Mockito.when(stage.identity()).thenReturn(foreign);
            org.mockito.Mockito.when(stage.profile()).thenReturn(s.assets().profile());
            assertThrows(IllegalArgumentException.class, () -> minimal(s,stage,s.r3PcmSource()));
        }
        var badFile = org.mockito.Mockito.mock(CdR3PcmSource.class);
        org.mockito.Mockito.when(badFile.source()).thenReturn(s.r3PcmSource().orElseThrow().source());
        org.mockito.Mockito.when(badFile.file()).thenReturn(CdNativeFile.R1PCM);
        assertThrows(IllegalArgumentException.class, () -> retain(s,Optional.of(badFile)));
    }

    @Test void typedBorrowIsPrivateCustodyAndThePublicSourceSurfaceIncludesProgrammeData() throws Exception {
        var borrow = CdGameDataSource.class.getDeclaredMethod("r3PcmSource");
        assertFalse(Modifier.isPublic(borrow.getModifiers())); assertFalse(Modifier.isPrivate(borrow.getModifiers()));
        assertFalse(Modifier.isProtected(borrow.getModifiers()));
        assertEquals("java.util.Optional<com.openggf.game.soniccd.media.CdR3PcmSource>",borrow.getGenericReturnType().getTypeName());
        var field = CdGameDataSource.class.getDeclaredField("r3PcmSource");
        assertTrue(Modifier.isPrivate(field.getModifiers())); assertTrue(Modifier.isFinal(field.getModifiers()));
        assertEquals(borrow.getGenericReturnType(),field.getGenericType());
        assertEquals(List.of(3,4,5,6,7,8,9,10,11,12,13),Arrays.stream(CdGameDataSource.class.getDeclaredConstructors()).map(v -> v.getParameterCount()).sorted().toList());
        assertTrue(Arrays.stream(CdGameDataSource.class.getDeclaredConstructors()).noneMatch(v -> Modifier.isPublic(v.getModifiers())));
        assertEquals(List.of("act2Assets/0","act2PastAssets/0","act3FutureAssets/0","assets/0","fmSoundAssets/0","identity/0",
                        "openAsset/1","programmeData/1","r31aAssets/0","release/0","rom/0","timeWarpPrograms/0"),
                Arrays.stream(CdGameDataSource.class.getDeclaredMethods()).filter(v -> Modifier.isPublic(v.getModifiers()))
                        .map(v -> v.getName()+"/"+v.getParameterCount()).sorted().toList());
        assertEquals(List.of("prepare"),Arrays.stream(CdStockLaunch.class.getDeclaredMethods()).filter(v -> Modifier.isPublic(v.getModifiers())).map(v -> v.getName()).sorted().toList());
        assertTrue(Arrays.stream(CdGameDataSource.class.getDeclaredFields()).noneMatch(v -> v.getType()==CdProgramAssets.class || v.getType()==Path.class));
    }

    @Test void sourceAddsNoLogicalPcmPathAndTheThreeOldStreamsRemainIndependent() throws Exception {
        var s = source(CdReleaseVariant.USA);
        for (String path : List.of("SNCBNK3B.BIN;1","program/SNCBNK3B.BIN;1","audio/R3PCM.bin","../SNCBNK3B.BIN;1","/SNCBNK3B.BIN;1"))
            assertThrows(FileNotFoundException.class, () -> s.openAsset(path));
        for (String path : List.of(CdGameDataSource.DECODED_BLOCKS,CdGameDataSource.DECODED_TILES,CdGameDataSource.INITIAL_PALETTE)) {
            try (var first=s.openAsset(path);var second=s.openAsset(path)) {
                int one=first.read(); assertEquals(one,second.read()); byte[] rest=second.readAllBytes();
                assertArrayEquals(rest,first.readAllBytes());
                try(var again=s.openAsset(path)){assertEquals(one,again.read());assertArrayEquals(rest,again.readAllBytes());}
                assertTrue(rest.length>0);rest[0]^=0x55;
                try(var again=s.openAsset(path)){assertEquals(one,again.read());assertNotEquals(rest[0],again.readAllBytes()[0]);}
            }
        }
    }

    @Test void unsupportedStagePreflightAndMissingCueStillFailBeforeAnyNewSourceRead() {
        assertThrows(UnsupportedOperationException.class, () -> CdStockLaunch.prepare(temporary.resolve("missing.cue"),
                new CdStockLaunch.Request(CdStagePayload.R31A.stage(),Optional.of(CdReleaseVariant.USA),Optional.empty())));
        assertThrows(IOException.class, () -> CdStockLaunch.prepare(temporary.resolve("missing.cue"),CdStockLaunch.Request.r11a()));
    }

    private static CdGameDataSource retain(CdGameDataSource s,Optional<CdR3PcmSource> r3) {
        return new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),
                s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets(),r3);
    }
    private static CdGameDataSource minimal(CdGameDataSource s,CdR11aAssets stage,Optional<CdR3PcmSource> r3) {
        return new CdGameDataSource(s.release(),stage,s.fmSoundAssets(),Optional.empty(),Optional.empty(),
                Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),r3);
    }
    private static synchronized CdGameDataSource source(CdReleaseVariant variant) throws Exception {
        var key=new SourceKey(configuredRoot(),variant);var prior=SOURCES.get(key);if(prior!=null)return prior;
        var created=CdStockLaunch.prepare(suppliedCue(variant),CdStockLaunch.Request.r11a());SOURCES.put(key,created);return created;
    }
    private static String configuredRoot() {
        String root=System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Explicit original media opt-in: soniccd.media.root");return root;
    }
    private static Path suppliedCue(CdReleaseVariant variant) throws IOException {
        String directory=switch(variant){case USA->"USA";case JAPAN->"Japan";case EUROPE->"Europe";};
        try(var paths=Files.list(Path.of(configuredRoot()).resolve(directory))){var cues=paths.filter(v->v.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());return cues.getFirst();}
    }
    private static String sha(byte[] b) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
}
