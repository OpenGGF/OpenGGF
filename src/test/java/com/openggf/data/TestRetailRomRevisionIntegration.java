package com.openggf.data;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Original bytes are local inputs; neither test fixtures nor generated ROMs are committed. */
class TestRetailRomRevisionIntegration {
    record Original(RetailRomRevision revision, String property, String defaultName) {
        @Override public String toString() { return revision.name(); }
        Path path() { return Path.of(System.getProperty(property, defaultName)).toAbsolutePath(); }
    }

    static Stream<Original> originals() {
        return Stream.of(
                new Original(RetailRomRevision.S1_REV00, "sonic1.rev00.rom.path", "s1-r0.gen"),
                new Original(RetailRomRevision.S1_REV01, "sonic1.rom.path", "s1.gen"),
                new Original(RetailRomRevision.S2_REV00, "sonic2.rev00.rom.path", "s2-r0.gen"),
                new Original(RetailRomRevision.S2_REV01, "sonic2.rom.path", "s2.gen"));
    }

    @ParameterizedTest
    @MethodSource("originals")
    void recognizesOriginalLogicalBytesAndRejectsAlteredCopies(Original original) throws Exception {
        assumeTrue(Files.isRegularFile(original.path()), () -> "Missing original ROM: " + original.path());
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(original.path().toString()));
            assertEquals(original.revision(), RetailRomRevision.detect(rom));
            RomByteReader nativeReader = RomByteReader.fromRom(rom);
            RomByteReader lockOnView = RomByteReader.concat(
                    RomByteReader.fromBytes(new byte[0x200000]), nativeReader)
                    .window(0x200000, nativeReader.size());
            assertEquals(original.revision(), RetailRomRevision.detect(lockOnView));
            byte[] changed = nativeReader.slice(0, nativeReader.size());
            changed[changed.length - 1] ^= 1;
            assertEquals(RetailRomRevision.UNKNOWN,
                    RetailRomRevision.detect(RomByteReader.fromBytes(changed)));
            assertEquals(original.revision(), RetailRomRevision.detect(nativeReader));
            var identity = original.revision().belongsTo(RomIdentity.S1) ? RomIdentity.S1 : RomIdentity.S2;
            assertEquals(original.revision().knownImage(),
                    RomIdentityTable.verify(identity, RomFingerprint.of(rom.readAllBytes())).orElseThrow());
        }
    }

    @ParameterizedTest
    @MethodSource("originals")
    void discoveryToolReadsNativePlacementsAndPlcAddresses(Original original) throws Exception {
        assumeTrue(Files.isRegularFile(original.path()), () -> "Missing original ROM: " + original.path());
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(original.path().toString()));
            com.openggf.tools.GameObjectProfile profile = original.revision().belongsTo(RomIdentity.S1)
                    ? new com.openggf.tools.Sonic1ObjectProfile() : new com.openggf.tools.Sonic2ObjectProfile();
            var firstAct = profile.getLevels().getFirst();
            RomByteReader reader = RomByteReader.fromRom(rom);
            assertTrue(!profile.loadObjects(reader, firstAct).isEmpty(), "native placement stream");
            var mappings = profile.getPlcObjectMappings(reader, firstAct);
            assertTrue(!mappings.isEmpty(), "native PLC addresses must still classify known object art");
            assertTrue(mappings.stream().allMatch(mapping -> mapping.nemesisRomAddr() > 0
                    && mapping.nemesisRomAddr() < reader.size() && !mapping.objectIds().isEmpty()));
        }
    }
}
