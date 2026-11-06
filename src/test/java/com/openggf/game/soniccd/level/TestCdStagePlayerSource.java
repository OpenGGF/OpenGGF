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
import static org.mockito.Mockito.*;

/**
 * Data-only stage/player binding. Configured original-USA cases are distinct from
 * constructed metadata controls; no Frame/Body, lifecycle, art upload or source lease.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Sonic/Main.asm:146;
 * Past Scroll:146-147; Act2 Present Scroll131-147. Original pointer/source joins remain the aggregate factories' job.
 * R12A additions are source-authored ALL JAVA UNRUN; fromAct2/2 is a data relation, not programme admission.
 */
class TestCdStagePlayerSource {
    private static CdR11aAssets present;
    private static CdR11aAssets anotherPresent;
    private static CdR11bAssets past;
    private static CdR11bAssets anotherPast;
    private static CdR12aTerrainAssets act2Terrain;
    private static CdR12aTerrainAssets anotherAct2Terrain;
    private static CdR12aPlayerAssets act2Player;
    private static CdR12aPlayerAssets anotherAct2Player;

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
            act2Terrain = CdR12aTerrainAssets.fromPrograms(source);
            act2Player = CdR12aPlayerAssets.fromPrograms(source);
        }
        // An independent original-disc opening produces equal metadata but distinct aggregates.
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            anotherPresent = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStagePayload.R11A.stage());
            anotherPast = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
            anotherAct2Terrain = CdR12aTerrainAssets.fromPrograms(source);
            anotherAct2Player = CdR12aPlayerAssets.fromPrograms(source);
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
        assertEquals(3, constructors.length);
        for (var constructor : constructors) assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertEquals(Set.of(Arrays.asList(CdR11aAssets.class, CdR11bAssets.class),
                Arrays.asList(CdR12aTerrainAssets.class, CdR12aPlayerAssets.class),
                Arrays.asList(CdR12bTerrainAssets.class, CdR12bPlayerAssets.class)),
                Arrays.stream(constructors).map(c -> Arrays.asList(c.getParameterTypes())).collect(Collectors.toSet()));
        var names = Arrays.stream(CdStagePlayerSource.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(m -> m.getName() + "/" + m.getParameterCount()).collect(Collectors.toSet());
        assertEquals(Set.of("fromPresent/1", "fromPast/1", "fromAct2/2", "terrain/0", "player/0", "sonic/0",
                "mappingAddress/0", "variant/0", "stage/0", "ordinaryPeriod/0", "ownsPresent/1", "ownsPast/1", "ownsAct2/2", "fromAct2Past/2", "ownsAct2Past/2"), names);
        var fields = CdStagePlayerSource.class.getDeclaredFields(); assertEquals(13, fields.length);
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

    @Test
    void explicitAct2NullArgumentsNeverChoosePresentPastOrAnotherAggregate() {
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(null, null));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(null, mock(CdR12aPlayerAssets.class)));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(mock(CdR12aTerrainAssets.class), null));
    }

    @Test
    void actualOriginalAct2PairBorrowsTerrainPlayerVisualsAndItsOwnNativeSourceValues() {
        requireAct2Actual(); var view = CdStagePlayerSource.fromAct2(act2Terrain, act2Player);
        assertEquals(act2Terrain.identity(), act2Player.identity()); assertNotSame(act2Terrain.profile(), act2Player.profile());
        assertSame(act2Terrain.terrain(), view.terrain()); assertSame(act2Player.player(), view.player()); assertSame(act2Player.sonic(), view.sonic());
        assertSame(view.sonic(), view.player().sonic()); assertSame(act2Terrain.identity().stage(), view.stage());
        assertEquals(CdReleaseVariant.USA, view.variant()); assertEquals("R12A", view.stage().nativeId()); assertEquals(1, view.ordinaryPeriod());
        assertEquals(0x230F60, view.mappingAddress()); assertEquals(CdStageTerrain.Shape.PPZ2_PRESENT, view.terrain().shape());
        assertEquals(728, view.terrain().blockCount()); assertEquals(811, view.terrain().patternCount()); assertEquals(106, view.terrain().chunkCount());
        assertArrayEquals(new byte[]{(byte) 0x91, (byte) 0xB6, 0x7F, 0x7F}, view.player().specialChunks().copy());
        assertFalse(view.ownsPresent(present)); assertFalse(view.ownsPast(past));
        assertFalse(view.ownsPresent(null)); assertFalse(view.ownsPast(null));
    }

    @Test
    void act2ReferenceOwnershipRequiresBothSelectedAggregatesWhileEqualSourceDataCanBePaired() {
        requireAct2Actual(); var view = CdStagePlayerSource.fromAct2(act2Terrain, act2Player);
        assertNotSame(act2Terrain, anotherAct2Terrain); assertNotSame(act2Player, anotherAct2Player);
        assertEquals(act2Terrain.identity(), anotherAct2Terrain.identity()); assertEquals(act2Player.identity(), anotherAct2Player.identity());
        assertTrue(view.ownsAct2(act2Terrain, act2Player)); assertFalse(view.ownsAct2(anotherAct2Terrain, act2Player));
        assertFalse(view.ownsAct2(act2Terrain, anotherAct2Player)); assertFalse(view.ownsAct2(anotherAct2Terrain, anotherAct2Player));
        assertFalse(view.ownsAct2(null, act2Player)); assertFalse(view.ownsAct2(act2Terrain, null)); assertFalse(view.ownsAct2(null, null));
        var compatible = CdStagePlayerSource.fromAct2(act2Terrain, anotherAct2Player);
        assertTrue(compatible.ownsAct2(act2Terrain, anotherAct2Player)); assertFalse(compatible.ownsAct2(act2Terrain, act2Player));
        assertSame(anotherAct2Player.sonic(), compatible.sonic()); assertSame(act2Terrain.terrain(), compatible.terrain());
        assertFalse(CdStagePlayerSource.fromPresent(present).ownsAct2(act2Terrain, act2Player));
        assertFalse(CdStagePlayerSource.fromPast(past).ownsAct2(act2Terrain, act2Player));
    }

    @Test
    void repeatedAct2WrappersAreReadonlyRelationsAndDoNotMintInvocationOrProgrammeTokens() {
        requireAct2Actual(); var first = CdStagePlayerSource.fromAct2(act2Terrain, act2Player);
        var again = CdStagePlayerSource.fromAct2(act2Terrain, act2Player);
        assertNotSame(first, again); assertTrue(first.ownsAct2(act2Terrain, act2Player)); assertTrue(again.ownsAct2(act2Terrain, act2Player));
        assertSame(first.terrain(), again.terrain()); assertSame(first.player(), again.player()); assertSame(first.sonic(), again.sonic());
        byte[] copy = first.player().specialChunks().copy(); Arrays.fill(copy, (byte) 0);
        assertArrayEquals(new byte[]{(byte) 0x91, (byte) 0xB6, 0x7F, 0x7F}, again.player().specialChunks().copy());
    }

    @Test
    void constructedAct2MmdOrDiscLineageMismatchCannotJoinTheTwoActualProducts() {
        requireAct2Actual(); var id = act2Player.identity();
        for (var bad : new CdR12aTerrainAssets.Identity[]{
                new CdR12aTerrainAssets.Identity(id.variant(), id.stage(), id.file(), id.domain(), "0".repeat(64), id.observedWholeDiscSha256()),
                new CdR12aTerrainAssets.Identity(id.variant(), id.stage(), id.file(), id.domain(), id.mmdSha256(), "0".repeat(64))}) {
            var player = declaredAct2Player(bad, act2Player.profile(), act2Player.player(), act2Player.sonic());
            assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromAct2(act2Terrain, player));
        }
        var wrong = new CdR12aTerrainAssets.Identity(id.variant(), id.stage(), id.file(), id.domain(), "0".repeat(64), id.observedWholeDiscSha256());
        var terrain = declaredAct2Terrain(wrong, act2Terrain.profile(), act2Terrain.terrain());
        var player = declaredAct2Player(wrong, act2Player.profile(), act2Player.player(), act2Player.sonic());
        assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromAct2(terrain, player), "Equal wrong MMD hashes still differ from both actual profiles");
    }

    @Test
    void constructedAct2ProfileVariantStageFileDomainHashOrMappingMismatchRejects() {
        requireAct2Actual();
        for (int kind = 0; kind < 6; kind++) {
            var bad = act2Profile();
            switch (kind) {
                case 0 -> when(bad.variant()).thenReturn(CdReleaseVariant.EUROPE);
                case 1 -> when(bad.stage()).thenReturn(CdStagePayload.R11A.stage());
                case 2 -> when(bad.file()).thenReturn(CdNativeFile.R11A);
                case 3 -> when(bad.domain()).thenReturn(CdMmdImage.Domain.RELOCATED_COPY);
                case 4 -> when(bad.expectedMmdSha256()).thenReturn("0".repeat(64));
                case 5 -> when(bad.range(CdR12aProfile.Asset.MAP_SPR_SONIC)).thenReturn(
                        new CdR12aProfile.FileRange(CdNativeFile.R12A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x1F200, 3004));
            }
            var terrain = declaredAct2Terrain(act2Terrain.identity(), bad, act2Terrain.terrain());
            assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromAct2(terrain, act2Player));
            var player = declaredAct2Player(act2Player.identity(), bad, act2Player.player(), act2Player.sonic());
            assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromAct2(act2Terrain, player));
        }
    }

    @Test
    void constructedAct2ForeignIdentityOrTerrainShapeNeverSelectsAnotherStage() {
        requireAct2Actual();
        for (int kind = 0; kind < 4; kind++) {
            var bad = mock(CdR12aTerrainAssets.Identity.class); var id = act2Terrain.identity();
            when(bad.variant()).thenReturn(id.variant()); when(bad.stage()).thenReturn(id.stage());
            when(bad.file()).thenReturn(id.file()); when(bad.domain()).thenReturn(id.domain());
            when(bad.mmdSha256()).thenReturn(id.mmdSha256()); when(bad.observedWholeDiscSha256()).thenReturn(id.observedWholeDiscSha256());
            switch (kind) {
                case 0 -> when(bad.variant()).thenReturn(CdReleaseVariant.JAPAN);
                case 1 -> when(bad.stage()).thenReturn(CdStagePayload.R11B.stage());
                case 2 -> when(bad.file()).thenReturn(CdNativeFile.R11A);
                case 3 -> when(bad.domain()).thenReturn(CdMmdImage.Domain.RELOCATED_COPY);
            }
            var terrain = declaredAct2Terrain(bad, act2Terrain.profile(), act2Terrain.terrain());
            var player = declaredAct2Player(bad, act2Player.profile(), act2Player.player(), act2Player.sonic());
            assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromAct2(terrain, player));
        }
        var wrongTerrain = declaredAct2Terrain(act2Terrain.identity(), act2Terrain.profile(), past.terrain());
        assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromAct2(wrongTerrain, act2Player));
    }

    @Test
    void constructedAct2MissingAggregateMetadataOrProductsNeverFallsBack() {
        requireAct2Actual();
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(declaredAct2Terrain(null, act2Terrain.profile(), act2Terrain.terrain()), act2Player));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(declaredAct2Terrain(act2Terrain.identity(), null, act2Terrain.terrain()), act2Player));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(declaredAct2Terrain(act2Terrain.identity(), act2Terrain.profile(), null), act2Player));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(act2Terrain, declaredAct2Player(null, act2Player.profile(), act2Player.player(), act2Player.sonic())));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(act2Terrain, declaredAct2Player(act2Player.identity(), null, act2Player.player(), act2Player.sonic())));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(act2Terrain, declaredAct2Player(act2Player.identity(), act2Player.profile(), null, act2Player.sonic())));
        assertThrows(NullPointerException.class, () -> CdStagePlayerSource.fromAct2(act2Terrain, declaredAct2Player(act2Player.identity(), act2Player.profile(), act2Player.player(), null)));
    }

    @Test
    void constructedAct2EqualVisualBytesDoNotReplaceTheExactSonicBorrowedByItsPlayer() {
        requireAct2Actual(); assertNotSame(act2Player.sonic(), anotherAct2Player.sonic());
        assertArrayEquals(act2Player.sonic().mappings().copy(), anotherAct2Player.sonic().mappings().copy());
        var broken = declaredAct2Player(act2Player.identity(), act2Player.profile(), act2Player.player(), anotherAct2Player.sonic());
        assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromAct2(act2Terrain, broken));
        var crossAct = declaredAct2Player(act2Player.identity(), act2Player.profile(), past.player(), act2Player.sonic());
        assertThrows(IllegalArgumentException.class, () -> CdStagePlayerSource.fromAct2(act2Terrain, crossAct));
    }

    @Test
    void consistentConstructedAct2RelationReadsOnlyMetadataAndBorrowedProducts() {
        requireAct2Actual(); var terrain = declaredAct2Terrain(act2Terrain.identity(), act2Terrain.profile(), act2Terrain.terrain());
        var player = declaredAct2Player(act2Player.identity(), act2Player.profile(), act2Player.player(), act2Player.sonic());
        var view = CdStagePlayerSource.fromAct2(terrain, player);
        assertTrue(view.ownsAct2(terrain, player)); assertFalse(view.ownsAct2(act2Terrain, act2Player));
        assertSame(act2Terrain.terrain(), view.terrain()); assertSame(act2Player.player(), view.player()); assertSame(act2Player.sonic(), view.sonic());
        verify(terrain, never()).view(any()); verify(terrain, never()).loadPlan(); verify(terrain, never()).blocksDecode(); verify(terrain, never()).levelTilesDecode();
        verify(player, never()).view(any()); verify(player, never()).sonicArtReadRange();
    }

    private static void requireAct2Actual() {
        assumeTrue(act2Terrain != null && act2Player != null && anotherAct2Terrain != null && anotherAct2Player != null,
                "Set soniccd.media.root for original-USA Act2 paired data");
    }
    private static CdR12aTerrainAssets declaredAct2Terrain(CdR12aTerrainAssets.Identity identity, CdR12aProfile profile, CdStageTerrain terrain) {
        var assets = mock(CdR12aTerrainAssets.class); when(assets.identity()).thenReturn(identity);
        when(assets.profile()).thenReturn(profile); when(assets.terrain()).thenReturn(terrain); return assets;
    }
    private static CdR12aPlayerAssets declaredAct2Player(CdR12aTerrainAssets.Identity identity, CdR12aProfile profile,
                                                       CdR11aPlayerAssets player, CdSonicVisualAssets sonic) {
        var assets = mock(CdR12aPlayerAssets.class); when(assets.identity()).thenReturn(identity); when(assets.profile()).thenReturn(profile);
        when(assets.player()).thenReturn(player); when(assets.sonic()).thenReturn(sonic); return assets;
    }
    private static CdR12aProfile act2Profile() {
        var actual = act2Terrain.profile(); var profile = mock(CdR12aProfile.class);
        when(profile.variant()).thenReturn(actual.variant()); when(profile.stage()).thenReturn(actual.stage());
        when(profile.file()).thenReturn(actual.file()); when(profile.domain()).thenReturn(actual.domain());
        when(profile.expectedMmdSha256()).thenReturn(actual.expectedMmdSha256());
        when(profile.range(CdR12aProfile.Asset.MAP_SPR_SONIC)).thenReturn(actual.range(CdR12aProfile.Asset.MAP_SPR_SONIC)); return profile;
    }

    private static void requireActual() {
        assumeTrue(present != null && past != null, "Set soniccd.media.root for original-USA A/B cases");
    }

    private static CdR11aAssets declared(CdR11aProfile profile, CdR11aAssets.Identity identity,
                                        CdR11aTerrain terrain, CdSonicVisualAssets sonic, CdR11aPlayerAssets player) {
        return new CdR11aAssets(profile, identity, null, null, null, terrain, null, null, sonic, player, null);
    }
}
