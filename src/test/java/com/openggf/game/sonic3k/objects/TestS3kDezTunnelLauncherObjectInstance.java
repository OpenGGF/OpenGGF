package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestS3kDezTunnelLauncherObjectInstance {
    private static final int[] PATH_ADDR = {
            0x1FB49E, 0x1FB518, 0x1FB5B2, 0x1FB5B2,
            0x1FB5F0, 0x1FB646, 0x1FB6B4, 0x1FB6CE
    };

    @Test
    void readySequenceLaunchesOnTheThirdSixtyPassCadence() {
        S3kDezTunnelLauncherObjectInstance launcher = launcher();
        launcher.armForTest();
        for (int frame = 0; frame < 179; frame++) launcher.update(frame, null);
        assertFalse(launcher.launchingForTest());
        assertEquals(8, launcher.launchCountForTest());
        launcher.update(179, null);
        assertTrue(launcher.launchingForTest());
        assertEquals(7, launcher.launchCountForTest());
        assertEquals(2, launcher.getReservedChildSlotCount());
        assertEquals(4, launcher.romObjectCodePointerHighWord());
    }

    @Test
    void allEightSubtypeEntriesAddressTheRomsSevenPathStreams() throws Exception {
        File file = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(file != null && file.exists());
        int[] expectedCounts = {0x1E, 0x26, 0x0F, 0x0F, 0x15, 0x1B, 6, 9};
        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(file.getAbsolutePath()));
            for (int i = 0; i < PATH_ADDR.length; i++) {
                assertEquals(expectedCounts[i], rom.read16BitAddr(PATH_ADDR[i]) & 0xFFFF,
                        "path entry count for subtype " + i);
                byte[] bytes = rom.readBytes(PATH_ADDR[i] + 2, expectedCounts[i] * 4);
                assertTrue(bytes.length >= 24, "each path includes capture, approach and release data");
                assertTrue(java.util.stream.IntStream.range(0, bytes.length)
                        .anyMatch(index -> (bytes[index] & 0x80) != 0),
                        "path " + i + " includes a packed curve command");
            }
        }
    }

    @Test
    void elevenFrameMappingAndResidentArtRegistrationMatchTheRom() throws Exception {
        Sonic3kPlcArtRegistry.LevelArtEntry entry = Sonic3kPlcArtRegistry.getPlan(0x0B, 0)
                .levelArt().stream().filter(e -> e.key().equals(Sonic3kObjectArtKeys.DEZ_TUNNEL_LAUNCHER))
                .findFirst().orElseThrow();
        assertEquals(Sonic3kConstants.MAP_DEZ_TUNNEL_LAUNCHER_ADDR, entry.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_DEZ_MISC + 0x38, entry.artTileBase());
        assertEquals(0, entry.palette());
        assertEquals(11, entry.mappingFrameCount());

        File file = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(file != null && file.exists());
        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(file.getAbsolutePath()));
            assertEquals(11, S3kSpriteDataLoader.loadMappingFrames(RomByteReader.fromRom(rom),
                    Sonic3kConstants.MAP_DEZ_TUNNEL_LAUNCHER_ADDR, 11).size());
        }
    }

    @Test
    void genericRewindRestoresTheNestedRiderPathBlock() {
        S3kDezTunnelLauncherObjectInstance launcher = launcher();
        launcher.primeRiderForRewindTest(11);
        long expected = launcher.riderChecksumForTest();
        var snapshot = launcher.captureRewindState(RewindCaptureContext.none());
        launcher.primeRiderForRewindTest(37);
        assertNotEquals(expected, launcher.riderChecksumForTest());
        launcher.restoreRewindState(snapshot, RewindCaptureContext.none());
        assertEquals(expected, launcher.riderChecksumForTest());
    }

    private static S3kDezTunnelLauncherObjectInstance launcher() {
        return new S3kDezTunnelLauncherObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x57, 0, 0, false, 0));
    }
}
