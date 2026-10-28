package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Data-only stage/player binding. Configured original-USA cases are distinct from
 * constructed metadata controls; no Frame/Body, lifecycle, art upload or source lease.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Sonic/Main.asm:146;
 * Past Scroll:146-147. Original pointer/source joins remain the aggregate factories' job.
 */
class TestCdStagePlayerSource {
    private static CdR11aAssets present;
    private static CdR11aAssets anotherPresent;
    private static CdR11bAssets past;
    private static CdR11bAssets anotherPast;

    @BeforeAll
    static void loadConfiguredOriginalUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        if (root == null || root.isBlank()) return; // Only actual-media cases below skip.
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA directory must contain exactly one original CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            present = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStagePayload.R11A.stage());
            past = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
        // An independent original-disc opening produces equal metadata but distinct aggregates.
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            anotherPresent = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStagePayload.R11A.stage());
            anotherPast = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void namedNullFactoriesRejectRatherThanChoosingADefaultSource() {
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromPresent(null));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromPast(null));
    }

    @Test
    void publicSurfaceIsFinalReadonlyDataWithNamedFactoriesOnly() {
        assertTrue(Modifier.isFinal(CdStagePlayerSource.class.getModifiers()));
        var constructors = CdStagePlayerSource.class.getDeclaredConstructors();
        assertEquals(1, constructors.length); assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[]{CdR11aAssets.class, CdR11bAssets.class}, constructors[0].getParameterTypes());
        var names = Arrays.stream(CdStagePlayerSource.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(m -> m.getName() + "/" + m.getParameterCount()).collect(Collectors.toSet());
        assertEquals(Set.of("fromPresent/1", "fromPast/1", "terrain/0", "player/0", "sonic/0",
                "mappingAddress/0", "variant/0", "stage/0", "ordinaryPeriod/0", "ownsPresent/1", "ownsPast/1"), names);
        var fields = CdStagePlayerSource.class.getDeclaredFields(); assertEquals(9, fields.length);
        for (var field : fields) {
            assertTrue(Modifier.isPrivate(field.getModifiers())); assertTrue(Modifier.isFinal(field.getModifiers()));
            assertFalse(field.getType().isArray(), "No copied mutable byte bank");
        }
    }

    @Test
    void constructedWrongFileDomainStageAndProfileVariantRejectBeforeMissingData() {
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStagePayload.R11A.stage());
        var good = new CdR11aAssets.Identity(CdReleaseVariant.USA, CdStagePayload.R11A.stage(),
                CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, "declared", "declared");
        for (var bad : new CdR11aAssets.Identity[]{
                new CdR11aAssets.Identity(CdReleaseVariant.USA, good.stage(), CdNativeFile.R11B, good.domain(), "declared", "declared"),
                new CdR11aAssets.Identity(CdReleaseVariant.USA, good.stage(), good.file(), CdMmdImage.Domain.RELOCATED_COPY, "declared", "declared"),
                new CdR11aAssets.Identity(CdReleaseVariant.USA, CdStagePayload.R11B.stage(), good.file(), good.domain(), "declared", "declared"),
                new CdR11aAssets.Identity(CdReleaseVariant.JAPAN, good.stage(), good.file(), good.domain(), "declared", "declared")}) {
            assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromPresent(
                    declared(profile, bad, null, null, null)));
        }
    }

    @Test
    void constructedMissingMetadataRejectsWithoutReadingAProgramOrCreatingTerrain() {
        var profile = CdR11aProfile.forStage(CdReleaseVariant.USA, CdStagePayload.R11A.stage());
        var good = new CdR11aAssets.Identity(CdReleaseVariant.USA, CdStagePayload.R11A.stage(),
                CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, "declared", "declared");
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromPresent(declared(null, good, null, null, null)));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromPresent(declared(profile, null, null, null, null)));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromPresent(declared(profile,
                new CdR11aAssets.Identity(null, good.stage(), good.file(), good.domain(), "declared", "declared"), null, null, null)));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromPresent(declared(profile,
                new CdR11aAssets.Identity(good.variant(), null, good.file(), good.domain(), "declared", "declared"), null, null, null)));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromPresent(declared(profile, good, null, null, null)));
    }

    @Test
    void actualPresentBorrowsTheExistingImmutableFormatProducts() {
        requireActual(); var binding = CdStagePlayerSource.fromPresent(present);
        assertSame(present.terrain().stageTerrain(), binding.terrain());
        assertSame(present.player(), binding.player()); assertSame(present.sonic(), binding.sonic());
        assertSame(binding.sonic(), binding.player().sonic());
        assertEquals(0x21F200, binding.mappingAddress());
        assertEquals(CdStageTerrain.Shape.PPZ1_PRESENT, binding.terrain().shape());
        assertEquals(804, binding.terrain().blockCount()); assertEquals(800, binding.terrain().patternCount());
        assertEquals(121, binding.terrain().chunkCount());
        assertEquals(42, binding.terrain().layout().columns(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(8, binding.terrain().layout().rows(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(6, binding.terrain().layout().columns(CdStageTerrain.Plane.BACKGROUND));
        assertEquals(8, binding.terrain().layout().rows(CdStageTerrain.Plane.BACKGROUND));
    }

    @Test
    void actualPastBorrowsItsOwnSourceAndTheLiveMappingRatherThanTheUnusedCopy() {
        requireActual(); var binding = CdStagePlayerSource.fromPast(past);
        assertSame(past.terrain(), binding.terrain()); assertSame(past.player(), binding.player());
        assertSame(past.sonic(), binding.sonic()); assertSame(binding.sonic(), binding.player().sonic());
        assertEquals(0x230F60, binding.mappingAddress()); assertNotEquals(0x21F200, binding.mappingAddress());
        assertEquals(CdStageTerrain.Shape.PPZ1_PAST, binding.terrain().shape());
        assertEquals(660, binding.terrain().blockCount()); assertEquals(746, binding.terrain().patternCount());
        assertEquals(106, binding.terrain().chunkCount());
        assertEquals(42, binding.terrain().layout().columns(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(4, binding.terrain().layout().rows(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(10, binding.terrain().layout().columns(CdStageTerrain.Plane.BACKGROUND));
        assertEquals(2, binding.terrain().layout().rows(CdStageTerrain.Plane.BACKGROUND));
    }

    @Test
    void actualNativeIdentityAndOrdinaryPeriodRemainSourceDataNotLiveGateWrites() {
        requireActual(); var a = CdStagePlayerSource.fromPresent(present); var b = CdStagePlayerSource.fromPast(past);
        assertSame(present.identity().stage(), a.stage()); assertSame(past.identity().stage(), b.stage());
        assertSame(present.identity().variant(), a.variant()); assertSame(past.identity().variant(), b.variant());
        assertEquals("R11A", a.stage().nativeId()); assertEquals("R11B", b.stage().nativeId());
        assertEquals(1, a.ordinaryPeriod()); assertEquals(0, b.ordinaryPeriod());
        assertEquals(CdReleaseVariant.USA, a.variant()); assertEquals(CdReleaseVariant.USA, b.variant());
        assertSame(present.player(), a.player()); assertSame(past.player(), b.player());
    }

    @Test
    void equalValueIndependentOriginalAggregatesDoNotSupplyReferenceOwnership() {
        requireActual(); assertNotSame(present, anotherPresent); assertNotSame(past, anotherPast);
        assertEquals(present.identity(), anotherPresent.identity()); assertEquals(past.identity(), anotherPast.identity());
        var a = CdStagePlayerSource.fromPresent(present); var b = CdStagePlayerSource.fromPast(past);
        assertTrue(a.ownsPresent(present)); assertFalse(a.ownsPresent(anotherPresent));
        assertTrue(b.ownsPast(past)); assertFalse(b.ownsPast(anotherPast));
        assertFalse(a.ownsPast(past)); assertFalse(b.ownsPresent(present));
        assertFalse(a.ownsPresent(null)); assertFalse(a.ownsPast(null));
        assertFalse(b.ownsPresent(null)); assertFalse(b.ownsPast(null));
    }

    @Test
    void repeatedWrappersBorrowTheSameOwnerButAreNotAnInvocationToken() {
        requireActual(); var first = CdStagePlayerSource.fromPresent(present); var again = CdStagePlayerSource.fromPresent(present);
        var old = CdStagePlayerSource.fromPast(past); var next = CdStagePlayerSource.fromPast(past);
        assertNotSame(first, again); assertNotSame(old, next);
        assertTrue(first.ownsPresent(present)); assertTrue(again.ownsPresent(present));
        assertTrue(old.ownsPast(past)); assertTrue(next.ownsPast(past));
        assertSame(first.terrain(), again.terrain()); assertSame(first.player(), again.player());
        assertSame(old.terrain(), next.terrain()); assertSame(old.sonic(), next.sonic());
    }

    @Test
    void actualSpecialChunkInputsRemainDifferentRatherThanPresentSentinelDefaults() {
        requireActual();
        assertArrayEquals(new byte[]{0x7F, 0x7F, 0x7F, 0x7F}, CdStagePlayerSource.fromPresent(present).player().specialChunks().copy());
        assertArrayEquals(new byte[]{(byte) 0x8C, 0x7F, 0x1E, 0x1E}, CdStagePlayerSource.fromPast(past).player().specialChunks().copy());
    }

    @Test
    void separatelyConstructedMutablePresentProjectionCannotEditTheBinding() {
        requireActual(); var binding = CdStagePlayerSource.fromPresent(present); var level = new SonicCdLevel(present);
        var t = binding.terrain(); int pixel = t.pixel(0, 0, 0), block = t.blockWord(1, 0, 0).rawWord();
        level.getPattern(0).setPixel(0, 0, (byte) ((pixel + 1) & 15));
        level.getChunk(1).getPatternDesc(0, 0).set(0xFFFF); level.getMap().setValue(0, 0, 0, (byte) 0);
        assertEquals(pixel, t.pixel(0, 0, 0)); assertEquals(block, t.blockWord(1, 0, 0).rawWord());
        assertSame(present.terrain().stageTerrain(), binding.terrain()); assertTrue(binding.ownsPresent(present));
    }

    @Test
    void separatelyConstructedMutablePastProjectionCannotEditTheBinding() {
        requireActual(); var binding = CdStagePlayerSource.fromPast(past); var level = new SonicCdPastLevel(past);
        var t = binding.terrain(); int pixel = t.pixel(0, 0, 0), block = t.blockWord(1, 0, 0).rawWord();
        level.getPattern(0).setPixel(0, 0, (byte) ((pixel + 1) & 15));
        level.getChunk(1).getPatternDesc(0, 0).set(0xFFFF); level.getMap().setValue(0, 0, 0, (byte) 0);
        assertEquals(pixel, t.pixel(0, 0, 0)); assertEquals(block, t.blockWord(1, 0, 0).rawWord());
        assertSame(past.terrain(), binding.terrain()); assertTrue(binding.ownsPast(past));
    }

    @Test
    void constructedCrossAggregateSonicAndPlayerReferencesCannotBeJoined() {
        requireActual();
        assertArrayEquals(present.sonic().mappings().copy(), anotherPresent.sonic().mappings().copy());
        assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromPresent(
                declared(present.profile(), present.identity(), present.terrain(), anotherPresent.sonic(), present.player())));
        assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromPresent(
                declared(present.profile(), present.identity(), present.terrain(), present.sonic(), past.player())));
    }

    @Test
    void constructedMissingPlayerOrVisualDataNeverUsesAnotherSourceAsFallback() {
        requireActual();
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromPresent(
                declared(present.profile(), present.identity(), present.terrain(), null, present.player())));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromPresent(
                declared(present.profile(), present.identity(), present.terrain(), present.sonic(), null)));
    }

    @Test
    void consistentConstructedDataViewDoesNotReadUnrelatedBanksOrAuthenticateItsPackageFixture() {
        requireActual();
        // Deliberately constructed package fixture: image/decode/load/placement/shield fields are null.
        // This exercises data-only reads, not the supplied-disc factory or native admission.
        var fixture = declared(present.profile(), present.identity(), present.terrain(), present.sonic(), present.player());
        var view = CdStagePlayerSource.fromPresent(fixture);
        assertTrue(view.ownsPresent(fixture)); assertFalse(view.ownsPresent(present));
        assertSame(present.player(), view.player()); assertSame(present.sonic(), view.sonic());
        assertSame(present.terrain().stageTerrain(), view.terrain());
        assertNull(fixture.loadPlan()); assertNull(fixture.placements()); assertNull(fixture.shield());
    }

    private static void requireActual() {
        assumeTrue(present != null && past != null, "Set soniccd.media.root for original-USA A/B cases");
    }

    private static CdR11aAssets declared(CdR11aProfile profile, CdR11aAssets.Identity identity,
                                        CdR11aTerrain terrain, CdSonicVisualAssets sonic, CdR11aPlayerAssets player) {
        return new CdR11aAssets(profile, identity, null, null, null, terrain, null, null, sonic, player, null);
    }
}
