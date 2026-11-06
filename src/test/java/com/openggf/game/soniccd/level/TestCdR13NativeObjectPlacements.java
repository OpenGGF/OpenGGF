package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests authored first. Actual-media data admission; pure public-pool constructed ingress only. ALL UNRUN. */
class TestCdR13NativeObjectPlacements {
    // N1ecc5 src/r1/stage_object_maps_3.asm, independently joined to both original USA MMD LEA operands.
    // Comparison-only expected bytes: never a runtime asset source or an alternate owner factory.
    private static final byte[] EXPECTED = HexFormat.of().parseHex(("000c01fcffff000000000000" + """
            0010000890538000
            002b025c0a008000
            00300108a4018000
            0060025090138000
            00720131a4018000
            00a00008a4008000
            00b80028a4008000
            00c0012090238000
            00c002a890248000
            00d0010890228000
            00d0014099048000
            00e000f090218000
            00e000f090218000
            00e001a8a4008000
            00f00110a4018000
            00f0014099038000
            0110014099018000
            0120009099038000
            012001b8a4008000
            01480136a4018000
            017201cba4008000
            01e0006890238000
            0210011090538000
            0278025890138000
            02bb41c00a048000
            02f000d890538000
            02f001780a028000
            02f002d099018000
            031000d890238000
            03780280a2ff8000
            038801600a048000
            0398016099028000
            03e0019890538000
            03e002880a028000
            0410009090218000
            04300160a2ff8000
            0518013890248000
            05550040a2ff8000
            05d50146a2ff8000
            06250008a4008000
            06550025a4008000
            0710009099018000
            0730009099008000
            0738017090008000
            0750014290008000
            0750019b90008000
            0758006890248000
            0780012890008000
            078001b890008000
            07b0014290008000
            07b0019b90008000
            07c8017090008000
            08c00108a4008000
            0910019090218000
            092500d8a4008000
            0c100068aa00e000
            0bf002b099008000
            0bf0030099078000
            0bf0035099058000
            0cb001f834018000
            0e4803a09500e000
            """ + "ffff000000000000ffff00000000").replaceAll("\\s+", ""));

    @Test
    void nativeAdmissionIsPackagePrivateOwnerTypedAndRejectsWrongStageBeforeAnyMmdRead() throws Exception {
        var method = CdObjectPlacements.class.getDeclaredMethod("decodeR13NativeOrder", CdR13FutureAssets.class);
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertFalse(Modifier.isPublic(method.getModifiers()));
        assertFalse(Modifier.isProtected(method.getModifiers()));
        assertFalse(Modifier.isPrivate(method.getModifiers()));
        assertEquals(List.of(CdR13FutureAssets.class), Arrays.asList(method.getParameterTypes()));
        assertTrue(Modifier.isFinal(CdR13FutureAssets.class.getModifiers()));
        assertTrue(Arrays.stream(CdR13FutureAssets.class.getDeclaredConstructors())
                .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(NoSuchMethodException.class,
                () -> CdObjectPlacements.class.getDeclaredMethod("decodeR13NativeOrder", byte[].class));
        assertThrows(NoSuchMethodException.class,
                () -> CdObjectPlacements.class.getDeclaredMethod("decodeR13NativeOrder", byte[].class, boolean.class));
        assertThrows(NullPointerException.class, () -> CdObjectPlacements.decodeR13NativeOrder(null));
        assertThrows(NullPointerException.class,
                () -> CdR13FutureAssets.fromPrograms(null, CdStageKey.parse("R13C")));
        var programs = mock(CdProgramAssets.class);
        assertThrows(NullPointerException.class, () -> CdR13FutureAssets.fromPrograms(programs, null));
        for (String code : List.of("R11A", "R12B", "R33C")) {
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR13FutureAssets.fromPrograms(programs, CdStageKey.parse(code)));
        }
        verify(programs, never()).mmd(any(CdNativeFile.class));
    }

    @ParameterizedTest @ValueSource(strings = {"R13C", "R13D"})
    void actualOwnerRetainsAll514BytesEveryFieldAndEverySavedIdWithoutSorting(String code) throws Exception {
        var assets = actual(code);
        var identity = assets.identity();
        assertEquals(CdReleaseVariant.USA, identity.variant());
        assertEquals(CdStageKey.parse(code), identity.stage());
        assertEquals(CdNativeFile.valueOf(code), identity.file());
        assertEquals(com.openggf.game.soniccd.media.CdMmdImage.Domain.ORIGINAL_WORD_RAM, identity.domain());
        assertEquals("a6b026a2381f926485cebb2da0bded29319cfd515ef7956a3ca79b18645bdb2b", identity.observedWholeDiscSha256());
        assertEquals(code.equals("R13C") ? "3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f"
                : "27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356", identity.mmdSha256());
        var bytes = assets.nativeObjectLayout();
        assertEquals(514, bytes.length());
        assertArrayEquals(EXPECTED, bytes.copy());
        assertEquals("09c6d1e3e0a86cd64fbc950116868948a81c2a40ea4874dfbf709644efc3df1f", sha(bytes.copy()));
        assertEquals(12, bytes.unsignedWord(0));
        assertEquals(508, bytes.unsignedWord(2));
        var placements = assets.placements();
        assertSame(placements, assets.placements());
        assertEquals(61, placements.records().size());
        assertArrayEquals(Arrays.copyOfRange(EXPECTED, 4, 12), placements.backwardSentinel().orElseThrow().copy());
        assertArrayEquals(Arrays.copyOfRange(EXPECTED, 500, 508), placements.forwardSentinel().copy());
        assertArrayEquals(Arrays.copyOfRange(EXPECTED, 508, 514), placements.nullList().orElseThrow().copy());
        var expected = CdAssetBytes.copyOf(EXPECTED);
        int saved = 0;
        for (int i = 0; i < 61; i++) {
            var row = placements.records().get(i); int at = 12 + i * 8;
            assertEquals(i, row.index());
            assertEquals(expected.unsignedWord(at), row.centreX());
            assertEquals(expected.unsignedWord(at + 2), row.packedY());
            assertEquals(expected.unsignedWord(at + 2) & 0xFFF, row.centreY());
            assertEquals(expected.unsignedWord(at + 2) & 0xF000, row.nativeYFlags());
            assertEquals((expected.unsignedWord(at + 2) & 0x4000) != 0, row.flipX());
            assertEquals((expected.unsignedWord(at + 2) & 0x8000) != 0, row.flipY());
            assertEquals(expected.unsignedByte(at + 4), row.encodedId());
            assertEquals(expected.unsignedByte(at + 4) & 0x7F, row.objectId());
            assertEquals((expected.unsignedByte(at + 4) & 0x80) != 0, row.hasSavedFlag());
            assertEquals(expected.unsignedByte(at + 5), row.subtype());
            assertEquals(expected.unsignedByte(at + 6), row.periodFlags());
            assertEquals(expected.unsignedByte(at + 7), row.subtype2());
            if (row.hasSavedFlag()) saved++;
        }
        assertEquals(55, saved);
        assertEquals(List.of(55, 60), placements.forPeriod(CdPeriod.PAST).stream().map(CdObjectPlacements.Placement::index).toList());
        assertEquals(placements.forPeriod(CdPeriod.PAST), placements.forPeriod(CdPeriod.PRESENT));
        assertEquals(placements.records(), placements.forPeriod(CdPeriod.GOOD_FUTURE));
        assertEquals(placements.records(), placements.forPeriod(CdPeriod.BAD_FUTURE));
        assertThrows(UnsupportedOperationException.class, () -> placements.records().clear());
        assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put(0, (byte) 0));
        var copy = bytes.copy(); copy[0] ^= 1; assertArrayEquals(EXPECTED, bytes.copy());

        // Constructed pure consumer: no production R13 spawner/World/body/frame authority is asserted.
        var runtime = new CdObjectPlacementRuntime(placements);
        assertTrue(runtime.sourceIdentity().isEmpty());
        var before = runtime.capture();
        for (int camera = 0; camera <= 0xC00; camera += 0x80) runtime.spawnObjects(camera, 2);
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0xC00, 61, 55, 56, 51, 0, 0), runtime.status());
        byte[] bank = before.savedFlags().copy(); bank[0] = 56; bank[1] = 51; saved = 0;
        for (int i = 0; i < 96; i++) {
            var state = before.pool().slots().get(i);
            if (i < 61) {
                var row = placements.records().get(i);
                int flips = ((row.packedY() << 2) | (row.packedY() >>> 14)) & 3;
                state = state.moveWord(X, row.centreX()).moveWord(Y, row.centreY())
                        .moveByte(SPRITE_FLAGS, flips).moveByte(FLAGS, flips).moveByte(ID, row.objectId())
                        .moveByte(SUBTYPE, row.subtype()).moveByte(SUBTYPE2, row.subtype2());
                if (row.hasSavedFlag()) {
                    saved++; state = state.moveByte(SAVED_FLAGS_ID, saved); bank[2 + 3 * saved + 2] |= (byte) 0x80;
                }
            }
            assertArrayEquals(state.copyBytes(), runtime.pool().state(32 + i).copyBytes(), "whole " + (32 + i));
        }
        assertEquals(55, saved);
        assertArrayEquals(bank, runtime.capture().savedFlags().copy(), "all766 bank bytes and saved IDs in native order");
    }

    @ParameterizedTest @ValueSource(strings = {"R13C", "R13D"})
    void nativeForwardBoundaryStopsAtEggmanBeforeThreeSmallerXMonitors(String code) throws Exception {
        var placements = actual(code).placements();
        assertEquals(0xC10, placements.records().get(55).centreX());
        assertEquals(0xAA, placements.records().get(55).encodedId());
        assertEquals(0xBF0, placements.records().get(56).centreX());
        assertEquals(0x19, placements.records().get(56).objectId(), "source row56 is Monitor, not Animal");
        // N Object Spawner.asm:57-101,176-227: chunk980 +280 =C00, strict unsigned BLS gate.
        var runtime = new CdObjectPlacementRuntime(placements);
        runtime.spawnObjects(0x980, 2);
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0x980, 55, 53, 51, 49, 0, 0), runtime.status());
        assertTrue(placements.records().get(56).centreX() < 0xC00);
        assertEquals(0x910, runtime.pool().state(32).centreX()); assertEquals(49, runtime.pool().state(32).savedFlagsId());
        assertEquals(0x925, runtime.pool().state(33).centreX()); assertEquals(50, runtime.pool().state(33).savedFlagsId());
        assertEquals(0, runtime.pool().state(34).id(), "row55 gates rows56..58; no pre-sort may expose them");
        for (int id = 51; id <= 54; id++) assertEquals(0, runtime.savedFlag(id, 2));
        runtime.spawnObjects(0xA00, 2); // C80 now admits row55 followed by all three native BF0 rows.
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0xA00, 59, 55, 55, 51, 0, 0), runtime.status());
        for (int i = 0; i < 4; i++) {
            var state = runtime.pool().state(34 + i);
            assertEquals(i == 0 ? 0x2A : 0x19, state.id());
            assertEquals(i == 0 ? 0xC10 : 0xBF0, state.centreX());
            assertEquals(51 + i, state.savedFlagsId());
            assertEquals(0x80, runtime.savedFlag(51 + i, 2));
        }
        assertEquals(0, runtime.pool().state(38).id());
    }

    @ParameterizedTest @ValueSource(strings = {"R13C", "R13D"})
    void publicSortedDecodersStillRejectTheActualNativeReversal(String code) throws Exception {
        byte[] bytes = actual(code).nativeObjectLayout().copy();
        var layout = assertThrows(IllegalArgumentException.class, () -> CdObjectPlacements.decodeLayout(bytes));
        assertEquals("Placements are not in native X order", layout.getMessage());
        var records = assertThrows(IllegalArgumentException.class,
                () -> CdObjectPlacements.decodeRecords(Arrays.copyOfRange(bytes, 12, 508)));
        assertEquals("Placements are not in native X order", records.getMessage());
    }

    @Test
    void authenticButForeignOriginalProgrammeImagesCannotMintAnR13LayoutOwner() throws Exception {
        try (var programs = CdProgramAssets.open(cue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            for (String code : List.of("R13C", "R13D")) {
                var selected = CdNativeFile.valueOf(code);
                var other = CdNativeFile.valueOf(code.equals("R13C") ? "R13D" : "R13C");
                for (var foreign : List.of(other, CdNativeFile.R12B)) {
                    var altered = spy(programs);
                    doReturn(programs.mmd(foreign)).when(altered).mmd(selected);
                    assertThrows(IOException.class,
                            () -> CdR13FutureAssets.fromPrograms(altered, CdStageKey.parse(code)));
                    assertEquals(262144, programs.entry(selected).size(), "rejected borrower cannot close the real owner");
                }
            }
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String cachedRoot;
    private static CdR13FutureAssets good, bad;
    private static synchronized CdR13FutureAssets actual(String code) throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "UNRUN media opt-in: set soniccd.media.root");
        if (good == null || !root.equals(cachedRoot)) {
            try (var programs = CdProgramAssets.open(cue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
                good = CdR13FutureAssets.fromPrograms(programs, CdStageKey.parse("R13C"));
                bad = CdR13FutureAssets.fromPrograms(programs, CdStageKey.parse("R13D"));
                cachedRoot = root;
            }
        }
        return code.equals("R13C") ? good : bad;
    }

    private static Path cue() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "UNRUN media opt-in: set soniccd.media.root");
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must contain exactly one CUE; invalid configured roots fail");
            return cues.getFirst();
        }
    }
}
