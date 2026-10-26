package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent CPU-only specifications. All Java invocations are UNRUN by the source author.
 * Constructed slots, explicit test tile commits and serviced windows are not native execution.
 * Native mapping literals: scddisasm 1ecc5f3af, Log Shadow/Data/Mappings.asm:1-15.
 */
class TestCdLogShadowPicture {
    private static final int ROOT = 0x2089EE, SHADOW = 12621;
    private static final int[] BASE = {10000,11000,12000,12070,12098,12161,12231,12236,12275,12311,
            12326,12362,12374,12456,12475,12521,12529,12541,12563,12620};
    private static final int[] FIRST = {0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,
            0x520,0x328,0x5A8,0x374,0x490,0x320,0x6CB,0x409,0x4BE,0x440};
    private static final int[] COUNT = {24,14,70,28,63,70,5,39,36,15,36,12,82,19,46,8,12,22,57,1};
    private static final byte[] RAW = hex("0004000a01f00f0000f001f80d0000f0");
    // Literal full first SAT entries, not expected geometry derived through the converter.
    private static final String[][] SAT = {
            {"00f00f0144180170", "00f00f014c180170", "00f00f0154180170", "00f00f015c180170"},
            {"00f80d0144180170", "00f80d014c180170", "00f80d0154180170", "00f80d015c180170"}};
    private static final int[][] OFFSETS = {{0,4,8,12,1,5,9,13,2,6,10,14,3,7,11,15}, {0,2,4,6,1,3,5,7}};
    private static final int[][] XS = {{240,248,256,264}, {264,256,248,240}};
    private static final int[][][] YS = {{{112,120,128,136},{136,128,120,112}}, {{120,128},{128,120}}};

    @Test void twoRawFramesFourFlipsHaveLiteralSatAndColumnMajorPatternsWithPaletteTwo() throws Exception {
        var f = fixture(); var state = shadowWrite(seeded(f), shadowBytes(f));
        var source = bank(f, state, BASE, SHADOW); var mappings = CdObjectMappings.fromOpeningAssets(f.assets(), f.objects());
        assertArrayEquals(RAW, f.objects().logShadow().mappings().copy());
        var unique = new TreeSet<Integer>();
        for (int frame = 0; frame < 2; frame++) for (int flip = 0; flip < 4; flip++) {
            var sat = draw(mappings, ROOT, frame, flip, 0x4418);
            assertEquals(1, sat.spriteCount()); byte[] expected = new byte[SAT_BYTE_SIZE];
            System.arraycopy(hex(SAT[frame][flip]), 0, expected, 0, 8); assertArrayEquals(expected, sat.bytes());
            var picture = CdObjectPresentation.convert(sat, source); assertEquals(OFFSETS[frame].length, picture.tiles().size());
            assertEquals(OFFSETS[frame].length, picture.patternVersions().size());
            for (int index = 0; index < OFFSETS[frame].length; index++) {
                int offset = OFFSETS[frame][index], id = alias(offset); var tile = picture.tiles().get(index); unique.add(id);
                assertEquals(id, tile.patternId()); assertEquals(XS[flip & 1][index % 4], tile.x());
                assertEquals(YS[frame][flip >>> 1][index / 4], tile.y()); assertEquals(2, tile.palette());
                assertEquals((flip & 1) != 0, tile.hFlip()); assertEquals((flip & 2) != 0, tile.vFlip()); assertFalse(tile.priority());
                assertEquals(CdObjectPresentation.patternVersion(shadowBytes(f).slice(offset * 32, 32)), picture.patternVersions().get(id));
            }
        }
        assertEquals(16, unique.size()); assertEquals(5, unique.stream().filter(id -> id >= SHADOW && id < SHADOW + 5).count());
        var priority = CdObjectPresentation.convert(single(0xC41F), source).tiles().getFirst();
        assertEquals(SHADOW, priority.patternId()); assertEquals(2, priority.palette()); assertTrue(priority.priority());
    }

    @Test void actualSourceSeventeenReusesAllSixteenPhysicalOffsetsAndElevenOldIdentities() throws Exception {
        var f = fixture(); var cold = seeded(f); var prior = oldBank(f, cold, BASE); var bytes = shadowBytes(f);
        var state = shadowWrite(cold, bytes); var source = bank(f, state, BASE, SHADOW); var previous = oldBank(f, state, BASE);
        var ids = new TreeSet<Integer>();
        for (int offset = 0; offset < 16; offset++) {
            int nativeId = 0x418 + offset; var tile = source.resolve(nativeId); ids.add(tile.logicalPatternId());
            assertEquals(alias(offset), tile.logicalPatternId()); assertArrayEquals(bytes.slice(offset * 32, 32).copy(), CdR11aCameraArtResidency.readTile(state, nativeId).copy());
            assertEquals(CdObjectPresentation.patternVersion(bytes.slice(offset * 32, 32)), tile.version());
            if (offset < 7 || offset >= 12) assertEquals(previous.resolve(nativeId), tile);
            else assertThrows(UnsupportedOperationException.class, () -> previous.resolve(nativeId));
            if (offset >= 12) assertEquals(prior.resolve(nativeId).logicalPatternId(), tile.logicalPatternId());
        }
        assertEquals(16, ids.size()); assertEquals(11, ids.stream().filter(id -> id < SHADOW).count());
        assertEquals(9, CdR11aCameraArtResidency.Region.values().length);
        var sources = f.objects().sectionArtSources(); assertSame(sources, f.objects().sectionArtSources()); assertEquals(17, sources.sources().size());
        var shadow = sources.source(CdR11aAsset.ART_LOG_SHADOW); assertSame(shadow, sources.sources().get(16));
        assertEquals(5, shadow.canonicalPlcId()); assertEquals(6, shadow.canonicalRowIndex()); assertEquals(0x8300, shadow.canonicalPlc().vramAddress());
        assertEquals(16, shadow.tileCount()); assertEquals(24, shadow.readerBytes().length()); assertEquals(512, bytes.length());
        for (int index = 0; index < bytes.length(); index++) assertEquals(0x33, bytes.unsignedByte(index));
    }

    @Test void coldNewFiveRemainUnknownAtBindingEmptyAndOffspanButFailStrictlyWhenSelected() throws Exception {
        var f = fixture(); var cold = seeded(f); var old = oldBank(f, cold, BASE); var source = bank(f, cold, BASE, SHADOW);
        var mask = cold.region(CdR11aCameraArtResidency.Region.AT_83E0).knownMask(); assertArrayEquals(new byte[160], mask.copy());
        assertTrue(CdObjectPresentation.convert(empty(), source).tiles().isEmpty());
        assertEquals(CdObjectPresentation.convert(single(0x27AE), old), CdObjectPresentation.convert(single(0x27AE), source));
        assertEquals(old.resolve(0x424), source.resolve(0x424));
        for (int nativeId = 0x41F; nativeId <= 0x423; nativeId++) {
            unknown(source, nativeId); final int word = 0x4000 | nativeId;
            var fault = assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class, () -> CdObjectPresentation.convert(single(word), source));
            assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN, fault.boundary()); assertEquals(nativeId * 32, fault.destinationWord());
        }
        unknown(source, 0x418); unknown(source, 0x440); unknown(source, 0x4BE);
        assertArrayEquals(mask.copy(), cold.region(CdR11aCameraArtResidency.Region.AT_83E0).knownMask().copy());
        for (int byteAt = 0; byteAt < 160; byteAt++) assertTrue(cold.region(CdR11aCameraArtResidency.Region.AT_83E0).knownByte(byteAt).isEmpty());
    }

    @Test void eachSelectedNewTileUsesItsOwnExactCurrentBytesAndRetainedBeforeAfterGenerations() throws Exception {
        var f = fixture(); var cold = seeded(f); var before = bank(f, cold, BASE, SHADOW); var state = cold;
        for (int offset = 0; offset < 5; offset++) {
            var predecessor = bank(f, state, BASE, SHADOW); final int selected = 0x41F + offset; unknown(predecessor, selected);
            byte[] input = tileBytes(0x21 + offset * 17); var immutable = CdAssetBytes.copyOf(input);
            var next = CdR11aCameraArtResidency.writeTile(state, selected * 32, immutable); var current = bank(f, next, BASE, SHADOW);
            assertEquals(SHADOW + offset, current.resolve(selected).logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(immutable), current.resolve(selected).version());
            assertArrayEquals(input, CdR11aCameraArtResidency.readTile(next, selected).copy()); Arrays.fill(input, (byte) 0);
            for (int id = 0x41F; id <= 0x423; id++) {
                if (id > selected) unknown(current, id);
                if (id < selected) assertEquals(predecessor.resolve(id), current.resolve(id));
            }
            unknown(predecessor, selected); unknown(before, selected); state = next;
        }
        var retained = bank(f, state, BASE, SHADOW); var sat = single(0x441F); var frozen = CdObjectPresentation.convert(sat, retained);
        var overwrite = CdR11aCameraArtResidency.writeTile(state, 0x8400, CdAssetBytes.copyOf(tileBytes(0xF2)));
        var changed = bank(f, overwrite, BASE, SHADOW); assertEquals(retained.resolve(0x41F), changed.resolve(0x41F));
        assertNotEquals(retained.resolve(0x420).version(), changed.resolve(0x420).version());
        for (int id : new int[]{0x421, 0x422, 0x423}) assertEquals(retained.resolve(id), changed.resolve(id));
        assertEquals(frozen, CdObjectPresentation.convert(sat, retained)); assertEquals(frozen, CdObjectPresentation.convert(sat, bank(f, state, BASE, SHADOW)));
        assertNotEquals(CdObjectPresentation.convert(single(0x4420), retained), CdObjectPresentation.convert(single(0x4420), changed));
        assertArrayEquals(state.region(CdR11aCameraArtResidency.Region.AT_83E0).knownMask().copy(), overwrite.region(CdR11aCameraArtResidency.Region.AT_83E0).knownMask().copy());
        for (var region : CdR11aCameraArtResidency.Region.values()) if (region != CdR11aCameraArtResidency.Region.AT_83E0) assertEquals(state.region(region), overwrite.region(region));
        assertThrows(UnsupportedOperationException.class, () -> frozen.patternVersions().clear()); assertThrows(UnsupportedOperationException.class, () -> frozen.tiles().clear());
    }

    @Test void partialSixteenTileOverwriteRetainsOldSourcesAndPicturesAcrossThreeExistingRegions() throws Exception {
        var f = fixture(); var initial = completeOld(f); var old = oldBank(f, initial, BASE); var state = initial;
        var bytes = shadowBytes(f);
        for (int offset = 0; offset < 16; offset++) {
            final int nativeId = 0x418 + offset; var before = bank(f, state, BASE, SHADOW);
            var next = CdR11aCameraArtResidency.writeTile(state, nativeId * 32, bytes.slice(offset * 32, 32));
            var after = bank(f, next, BASE, SHADOW); assertEquals(alias(offset), after.resolve(nativeId).logicalPatternId());
            assertEquals(CdObjectPresentation.patternVersion(bytes.slice(offset * 32, 32)), after.resolve(nativeId).version());
            for (int prior = 0; prior < offset; prior++) assertEquals(before.resolve(0x418 + prior), after.resolve(0x418 + prior));
            if (offset >= 7 && offset <= 11) unknown(before, nativeId);
            else assertEquals(old.resolve(nativeId), before.resolve(nativeId));
            state = next;
        }
        var source = bank(f, state, BASE, SHADOW); var mappings = CdObjectMappings.fromOpeningAssets(f.assets(), f.objects());
        var sat = draw(mappings, ROOT, 0, 0, 0x4418); var frozen = CdObjectPresentation.convert(sat, source);
        var overwritten = CdR11aCameraArtResidency.writeTile(state, 0x83E0, CdAssetBytes.copyOf(tileBytes(0x5D)));
        assertNotEquals(frozen, CdObjectPresentation.convert(sat, bank(f, overwritten, BASE, SHADOW)));
        assertEquals(frozen, CdObjectPresentation.convert(sat, source)); assertEquals(frozen, CdObjectPresentation.convert(sat, bank(f, state, BASE, SHADOW)));
        for (int offset : new int[]{0, 6, 12, 15}) assertEquals(old.resolve(0x418 + offset).logicalPatternId(), source.resolve(0x418 + offset).logicalPatternId());
    }

    @Test void everyOtherRawNativeIdDelegatesExactValuesAndFailuresIncludingColdLazyOldSpans() throws Exception {
        var f = fixture();
        for (var state : List.of(seeded(f), shadowWrite(completeOld(f), shadowBytes(f)))) {
            var old = oldBank(f, state, BASE); var source = bank(f, state, BASE, SHADOW);
            for (int id = 0; id <= 0x7FF; id++) if (id < 0x41F || id > 0x423) assertEquals(outcome(old, id), outcome(source, id), "raw native " + id);
            for (int id : new int[]{-1, Integer.MIN_VALUE, 0x800, 0x441F, 0xFFFF, Integer.MAX_VALUE}) assertEquals(outcome(old, id), outcome(source, id));
        }
    }

    @Test void allTwentyLogicalRangesRejectFirstLastAndCrossingOverlapsButAllowBothAdjacencies() throws Exception {
        var f = fixture(); var cold = seeded(f); int[] separated = new int[20];
        for (int span = 0; span < 20; span++) separated[span] = 10000 + span * 1000;
        for (int span = 0; span < 20; span++) {
            for (int overlap : new int[]{separated[span] - 4, separated[span], separated[span] + COUNT[span] - 1})
                assertThrows(IllegalArgumentException.class, () -> bank(f, cold, separated, overlap));
            for (int adjacent : new int[]{separated[span] - 5, separated[span] + COUNT[span]}) {
                var source = bank(f, cold, separated, adjacent); assertTrue(CdObjectPresentation.convert(empty(), source).tiles().isEmpty()); unknown(source, 0x41F);
            }
        }
    }

    @Test void newFiveEndOverflowRejectsAndC30ErrorsAndEagerUnknownAlwaysOccurFirst() throws Exception {
        var f = fixture(); var cold = seeded(f);
        for (int bad : new int[]{-1, Integer.MIN_VALUE, Integer.MAX_VALUE - 3, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> bank(f, cold, BASE, bad));
        var state = CdR11aCameraArtResidency.writeTile(cold, 0x8460, CdAssetBytes.copyOf(tileBytes(0x39)));
        for (int valid : new int[]{0, Integer.MAX_VALUE - 4}) assertEquals(valid + 4, bank(f, state, BASE, valid).resolve(0x423).logicalPatternId());
        for (int span = 0; span < 20; span++) {
            int[] invalid = BASE.clone(); invalid[span] = -1;
            var oldFailure = assertThrows(IllegalArgumentException.class, () -> oldBank(f, cold, invalid));
            var newFailure = assertThrows(IllegalArgumentException.class, () -> bank(f, cold, invalid, -1)); assertEquals(oldFailure.getMessage(), newFailure.getMessage());
        }
        int[] overlap = BASE.clone(); overlap[1] = overlap[0];
        assertEquals(assertThrows(IllegalArgumentException.class, () -> oldBank(f, cold, overlap)).getMessage(),
                assertThrows(IllegalArgumentException.class, () -> bank(f, cold, overlap, -1)).getMessage());
        var unprepared = legacy(f);
        var oldFailure = assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class, () -> oldBank(f, unprepared, BASE));
        var newFailure = assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class, () -> bank(f, unprepared, BASE, -1));
        assertEquals(oldFailure.getMessage(), newFailure.getMessage()); assertEquals(0x374, newFailure.nativePatternId());
    }

    @Test void allOldPotentialIdentitiesAndVersionsRemainAndPublicC31HasTheExactC30TypePrefix() throws Exception {
        var f = fixture(); var state = shadowWrite(completeOld(f), shadowBytes(f)); var old = oldBank(f, state, BASE); var source = bank(f, state, BASE, SHADOW);
        var ids = new TreeSet<Integer>();
        for (int span = 0; span < 20; span++) for (int offset = 0; offset < COUNT[span]; offset++) {
            int id = FIRST[span] + offset; assertEquals(old.resolve(id), source.resolve(id)); assertEquals(BASE[span] + offset, source.resolve(id).logicalPatternId()); assertTrue(ids.add(source.resolve(id).logicalPatternId()));
        }
        assertEquals(659, ids.size()); for (int id = 0x41F; id <= 0x423; id++) assertTrue(ids.add(source.resolve(id).logicalPatternId())); assertEquals(664, ids.size());
        var methods = Arrays.stream(CdObjectPresentation.class.getDeclaredMethods()).filter(m -> m.getName().equals("usaR11aCurrentCameraDevPreloadedArt"))
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())).sorted(java.util.Comparator.comparingInt(java.lang.reflect.Method::getParameterCount)).toList();
        assertEquals(List.of(23,24,25,26,27,28,29,30,31), methods.stream().map(java.lang.reflect.Method::getParameterCount).toList());
        var c30 = methods.get(7); var c31 = methods.get(8); assertArrayEquals(c30.getParameterTypes(), Arrays.copyOf(c31.getParameterTypes(), 30));
        assertEquals(int.class, c31.getParameterTypes()[30]); assertEquals(CdObjectPresentation.PatternSource.class, c31.getReturnType());
        assertEquals(CdObjectPresentation.PatternSource.class, c30.getReturnType()); assertEquals(0, c31.getExceptionTypes().length);
    }

    private record Fixture(CdR11aAssets assets, CdR11aObjectAssets objects, CdPlayerArtRuntime.State player, CdPowerupArtRuntime.State powerup) { }
    private static Fixture fixture() throws Exception {
        String configured = System.getProperty("soniccd.media.root"); Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for actual retained USA assets");
        Path cue; try (var files = Files.list(Path.of(configured).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        CdR11aAssets assets; try (var programs = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11aAssetLoader.load(programs, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
        // Explicit test-only serviced windows; no fabricated current-camera knowledge or owner snapshot.
        byte[] player = bankBytes(24, 0x17), powerup = bankBytes(36, 0x43);
        return new Fixture(assets, assets.openingObjects(), new CdPlayerArtRuntime.State(player, player, 0, false), new CdPowerupArtRuntime.State(powerup, powerup));
    }
    private static CdR11aCameraArtResidency.State legacy(Fixture f) throws Exception {
        return CdR11aCameraArtResidency.devSeed(f.objects().initialArtBank().binding(CdR11aAsset.ART_MOSQUI).bytes(),
                f.objects().frontierArtBank().binding(CdR11aAsset.ART_3D_PLANT).bytes(), f.objects().frontierArtBank().binding(CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
    }
    private static CdR11aCameraArtResidency.State seeded(Fixture f) throws Exception {
        var sources = f.objects().sectionArtSources(); var state = legacy(f);
        state = write(state, 0x6E80, sources.source(CdR11aAsset.ART_BOULDER).decode().bytes());
        state = write(state, 0x9200, sources.source(CdR11aAsset.ART_DIAGONAL_SPRING).decode().bytes());
        state = write(state, 0x6400, sources.source(CdR11aAsset.ART_SPIKES).decode().bytes());
        return write(state, 0xD960, sources.source(CdR11aAsset.ART_CHECKPOINT).decode().bytes());
    }
    private static CdR11aCameraArtResidency.State completeOld(Fixture f) throws Exception {
        var sources = f.objects().sectionArtSources(); var state = seeded(f);
        state = write(state, 0x8120, sources.source(CdR11aAsset.ART_ANTON).decode().bytes());
        state = write(state, 0x97C0, sources.source(CdR11aAsset.ART_PLATFORM).decode().bytes());
        return CdR11aCameraArtResidency.writeTile(state, 0x8800, CdAssetBytes.copyOf(tileBytes(0xB1)));
    }
    private static CdAssetBytes shadowBytes(Fixture f) throws Exception { return f.objects().sectionArtSources().source(CdR11aAsset.ART_LOG_SHADOW).decode().bytes(); }
    private static CdR11aCameraArtResidency.State shadowWrite(CdR11aCameraArtResidency.State state, CdAssetBytes bytes) { assertEquals(512, bytes.length()); return write(state, 0x8300, bytes); }
    private static CdR11aCameraArtResidency.State write(CdR11aCameraArtResidency.State state, int destination, CdAssetBytes bytes) {
        assertEquals(0, bytes.length() % 32); for (int at = 0; at < bytes.length(); at += 32) state = CdR11aCameraArtResidency.writeTile(state, destination + at, bytes.slice(at, 32)); return state;
    }
    private static CdObjectPresentation.PatternSource oldBank(Fixture f, CdR11aCameraArtResidency.State state, int[] b) throws Exception {
        var o = f.objects(); return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(), f.powerup(), o, o.frontierArtBank(), o.contactArtBank(), o.floatBlockArtBank(), o.springArtBank(), o.tunnelDoorArtBank(), o.monitorTimeArtBank(), state,
                b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15],b[16],b[17],b[18],b[19]);
    }
    private static CdObjectPresentation.PatternSource bank(Fixture f, CdR11aCameraArtResidency.State state, int[] b, final int logShadowLogicalBase) throws Exception {
        var o = f.objects(); return CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(f.player(), f.powerup(), o, o.frontierArtBank(), o.contactArtBank(), o.floatBlockArtBank(), o.springArtBank(), o.tunnelDoorArtBank(), o.monitorTimeArtBank(), state,
                b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11],b[12],b[13],b[14],b[15],b[16],b[17],b[18],b[19],logShadowLogicalBase);
    }
    private static int alias(int offset) {
        if (offset < 0 || offset >= 16) throw new AssertionError("Outside the sixteen physical Log Shadow offsets");
        return offset < 7 ? BASE[17] + 15 + offset : offset < 12 ? SHADOW + offset - 7 : BASE[3] + offset - 12;
    }
    private record Outcome(CdObjectPresentation.TilePattern tile, Class<?> error, String message) { }
    private static Outcome outcome(CdObjectPresentation.PatternSource source, int id) {
        try { return new Outcome(source.resolve(id), null, null); } catch (RuntimeException error) { return new Outcome(null, error.getClass(), error.getMessage()); }
    }
    private static void unknown(CdObjectPresentation.PatternSource source, int id) {
        var error = assertThrows(CdR11aCameraArtResidency.OwnershipBoundary.class, () -> source.resolve(id));
        assertEquals(CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN, error.boundary()); assertEquals(id, error.nativePatternId()); assertEquals(id * 32, error.destinationWord());
    }
    private static byte[] tileBytes(int salt) { byte[] bytes = new byte[32]; for (int index = 0; index < 32; index++) bytes[index] = (byte) (salt + index * 13); return bytes; }
    private static byte[] bankBytes(int count, int salt) { byte[] bytes = new byte[count * 32]; for (int tile = 0; tile < count; tile++) System.arraycopy(tileBytes(salt + tile * 7), 0, bytes, tile * 32, 32); return bytes; }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static CpuSat single(int tileWord) {
        var mappings = CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(0x123456, CdAssetBytes.copyOf(hex("0002010000000000")), 0, 1)), List.of());
        return draw(mappings, 0x123456, 0, 0, tileWord);
    }
    private static CpuSat draw(CdDrawMappings mappings, int pointer, int frame, int flip, int base) {
        var slots = new EmptySlots() {
            LiveDrawSlot live = new LiveDrawSlot(0x33, 4 | flip, 0x100, 0x80, 0, pointer, frame, base);
            @Override public LiveDrawSlot read(CdSlotRef ref) { assertEquals(47, ref.wholeIndex()); return live; }
            @Override public void writeOnScreenBit(CdSlotRef ref, boolean on) { assertEquals(47, ref.wholeIndex()); var s = live; live = new LiveDrawSlot(s.idByte(), (s.spriteFlagsByte() & 127) | (on ? 128 : 0), s.xWord(), s.yWord(), s.screenYWord(), s.mappingAddressBits(), s.mapFrameByte(), s.baseTileWord()); }
        };
        try (var queue = new com.openggf.game.soniccd.objects.CdObjectDrawQueue(slots, mappings, new byte[SAT_BYTE_SIZE])) {
            var camera = new CameraWords(0, 0); queue.beginPass();
            assertEquals(EnqueueResult.QUEUED, queue.drawObject(new DrawCallCut(new CdSlotRef(47), 4 | flip, 0x100, 0x80, 16, 16, 6), camera));
            return queue.finishAfterCamera(new DrawCameras(camera, camera, camera));
        }
    }
    private static CpuSat empty() {
        try (var queue = new com.openggf.game.soniccd.objects.CdObjectDrawQueue(new EmptySlots(), (a,b,c) -> { throw new AssertionError("Empty SAT must not select mappings"); }, new byte[SAT_BYTE_SIZE])) {
            var camera = new CameraWords(0, 0); queue.beginPass(); return queue.finishAfterCamera(new DrawCameras(camera, camera, camera));
        }
    }
    private static class EmptySlots implements CdDrawSlots {
        @Override public LiveDrawSlot read(CdSlotRef ref) { throw new AssertionError("Unexpected slot read"); }
        @Override public void writeOnScreenBit(CdSlotRef ref, boolean on) { throw new AssertionError("Unexpected slot write"); }
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
