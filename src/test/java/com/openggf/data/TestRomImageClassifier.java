package com.openggf.data;

import com.openggf.data.RomImageClassifier.LogicalWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.openggf.data.SyntheticRomImages.S1_SIZE;
import static com.openggf.data.SyntheticRomImages.S1_TITLE;
import static com.openggf.data.SyntheticRomImages.S2_SIZE;
import static com.openggf.data.SyntheticRomImages.S2_TITLE;
import static com.openggf.data.SyntheticRomImages.S3_SIZE;
import static com.openggf.data.SyntheticRomImages.S3_TITLE;
import static com.openggf.data.SyntheticRomImages.SK_SIZE;
import static com.openggf.data.SyntheticRomImages.SK_TITLE;
import static com.openggf.data.SyntheticRomImages.image;
import static com.openggf.data.SyntheticRomImages.physical;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One row per line of the design table, plus unknown images; no ROMs required. */
class TestRomImageClassifier {

    static Stream<Arguments> layouts() {
        int lockOn = 0x200000;
        return Stream.of(
                Arguments.of("Sonic 1", image(S1_SIZE, S1_TITLE, null),
                        List.of(new LogicalWindow(RomIdentity.S1, 0, S1_SIZE))),
                Arguments.of("Sonic 2", image(S2_SIZE, S2_TITLE, null),
                        List.of(new LogicalWindow(RomIdentity.S2, 0, S2_SIZE))),
                Arguments.of("Sonic 3", image(S3_SIZE, S3_TITLE, null),
                        List.of(new LogicalWindow(RomIdentity.S3, 0, S3_SIZE))),
                Arguments.of("Sonic & Knuckles", image(SK_SIZE, SK_TITLE, null),
                        List.of(new LogicalWindow(RomIdentity.SK, 0, SK_SIZE))),
                Arguments.of("Sonic 3 & Knuckles lock-on", image(SK_SIZE + S3_SIZE, SK_TITLE, S3_TITLE),
                        List.of(new LogicalWindow(RomIdentity.SK, 0, SK_SIZE),
                                new LogicalWindow(RomIdentity.S3, lockOn, S3_SIZE),
                                new LogicalWindow(RomIdentity.S3K, 0, SK_SIZE + S3_SIZE))),
                Arguments.of("Sonic & Knuckles + Sonic 1 lock-on", image(SK_SIZE + S1_SIZE, SK_TITLE, S1_TITLE),
                        List.of(new LogicalWindow(RomIdentity.SK, 0, SK_SIZE),
                                new LogicalWindow(RomIdentity.S1, lockOn, S1_SIZE))),
                Arguments.of("Sonic & Knuckles + Sonic 2 lock-on", image(0x340000, SK_TITLE, S2_TITLE),
                        List.of(new LogicalWindow(RomIdentity.SK, 0, SK_SIZE),
                                new LogicalWindow(RomIdentity.S2, lockOn, S2_SIZE),
                                new LogicalWindow(RomIdentity.KIS2_CHIP, 0x300000, 0x40000),
                                new LogicalWindow(RomIdentity.KIS2, 0, 0x340000))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layouts")
    void classifiesEachSupportedLayoutBySizeAndHeaders(String label, byte[] data, List<LogicalWindow> expected) {
        PhysicalImage image = physical(label + ".gen", data);
        assertEquals(label, RomImageClassifier.layoutOf(image).orElseThrow().label());
        assertEquals(expected, RomImageClassifier.classify(image));
        for (LogicalWindow window : expected) {
            assertTrue(window.end() <= data.length, "window inside image: " + window);
            assertEquals(window, RomImageClassifier.windowOf(image, window.rom()).orElseThrow());
        }
    }

    static Stream<Arguments> unknownImages() {
        return Stream.of(
                Arguments.of("wrong size for its header", image(S1_SIZE + 0x100, S1_TITLE, null)),
                Arguments.of("Sonic 2 header at Sonic 3 size", image(S3_SIZE, S2_TITLE, null)),
                Arguments.of("lock-on size without a second header", image(SK_SIZE + S3_SIZE, SK_TITLE, null)),
                Arguments.of("lock-on halves swapped", image(SK_SIZE + S3_SIZE, S3_TITLE, SK_TITLE)),
                Arguments.of("no recognised title", image(S1_SIZE, "SOME OTHER GAME", null)),
                Arguments.of("tiny file", new byte[16]),
                Arguments.of("empty file", new byte[0]));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unknownImages")
    void unknownImagesContainNoLogicalRom(String label, byte[] data) {
        PhysicalImage image = physical(label + ".gen", data);
        assertTrue(RomImageClassifier.layoutOf(image).isEmpty(), label);
        assertEquals(List.of(), RomImageClassifier.classify(image));
    }

    @Test
    void headerNamesAreClassifiedFromNormalisedTitles() {
        assertEquals(RomHeaderName.S1, RomHeaderName.of("SONIC THE               HEDGEHOG                "));
        assertEquals(RomHeaderName.S2, RomHeaderName.of("SONIC THE             HEDGEHOG 2                "));
        assertEquals(RomHeaderName.S3, RomHeaderName.of("SONIC THE             HEDGEHOG 3                "));
        assertEquals(RomHeaderName.SK, RomHeaderName.of("SONIC & KNUCKLES"));
        assertEquals(RomHeaderName.SK, RomHeaderName.of("sonic and knuckles"));
        assertEquals(RomHeaderName.UNKNOWN, RomHeaderName.of("SONIC3 & KNUCKLES"));
        assertEquals(RomHeaderName.UNKNOWN, RomHeaderName.of(""));
        assertEquals(RomHeaderName.UNKNOWN, RomHeaderName.of(null));
        assertEquals(RomHeaderName.S2, RomHeaderName.of("garbage", "SONIC THE HEDGEHOG 2"));
    }

    @Test
    void probedImageReadsBothHeaderSlotsWithoutLoadingTheBody() {
        PhysicalImage image = physical("lockon.gen", image(SK_SIZE + S3_SIZE, SK_TITLE, S3_TITLE));
        assertEquals(RomHeaderName.SK, image.headerAt0());
        assertEquals(RomHeaderName.S3, image.headerAtLockOn());
        assertEquals(SK_SIZE + S3_SIZE, image.size());
    }

    @Test
    void compositeRecipesListTheLockOnAddressSpaces() {
        assertEquals(List.of(RomIdentity.SK, RomIdentity.S3), RomImageClassifier.COMPOSITES.get(RomIdentity.S3K));
        assertEquals(List.of(RomIdentity.SK, RomIdentity.S2, RomIdentity.KIS2_CHIP),
                RomImageClassifier.COMPOSITES.get(RomIdentity.KIS2));
        assertEquals(2, RomImageClassifier.COMPOSITES.size());
    }

    @Test
    void identityTableVerifiesOnlyMatchingSizeAndHashes() {
        RomFingerprint s1 = new RomFingerprint(0x80000, "afe05eee", "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b");
        assertEquals("Sonic 1 World REV01", RomIdentityTable.verify(RomIdentity.S1, s1).orElseThrow().label());
        assertTrue(RomIdentityTable.verify(RomIdentity.S2, s1).isEmpty());
        assertTrue(RomIdentityTable.verify(RomIdentity.S1,
                new RomFingerprint(0x80000, "AFE05EEE", "0000000000000000000000000000000000000000")).isEmpty());
        assertTrue(RomIdentityTable.verify(RomIdentity.KIS2_CHIP, s1).isEmpty());
        RomFingerprint computed = RomFingerprint.of(new byte[] {1, 2, 3});
        assertEquals(3, computed.size());
        assertEquals("55BC801D", computed.crc32Hex());
        assertEquals("7037807198C22A7D2B0807371D763779A84FDFCF", computed.sha1Hex());
    }
}
