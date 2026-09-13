package com.openggf.data;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@FullReset
@ExtendWith(SingletonResetExtension.class)
class TestRomManagerLocationResolution {

    @TempDir
    Path temporaryDirectory;

    private SonicConfigurationService configuration;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        configuration = SonicConfigurationService.getInstance();
        configuration.resetToDefaults();
        RomManager.getInstance().close();
    }

    @AfterEach
    void tearDown() {
        RomManager.getInstance().close();
        configuration.resetToDefaults();
    }

    @Test
    void legacyForwarderPreservesCaseInsensitiveGameSelectionAndConfiguredText() {
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, " ./s1 configured.gen ");
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, " ./s2 configured.gen ");
        configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, " ./s3k configured.gen ");

        assertEquals(" ./s1 configured.gen ", RomManager.resolveRomForGame("s1"));
        assertEquals(" ./s1 configured.gen ", RomManager.resolveRomForGame("S1"));
        assertEquals(" ./s3k configured.gen ", RomManager.resolveRomForGame("s3k"));
        assertEquals(" ./s3k configured.gen ", RomManager.resolveRomForGame("S3K"));
        assertEquals(" ./s2 configured.gen ", RomManager.resolveRomForGame("s2"));
        assertThrows(IllegalArgumentException.class,
                () -> RomManager.resolveRomForGame(null));
        assertThrows(IllegalArgumentException.class,
                () -> RomManager.resolveRomForGame("unknown"));
        assertThrows(IllegalArgumentException.class,
                () -> RomManager.resolveRomForGame("other"));
    }

    @Test
    void legacyForwarderExposesMissingConfigurationAsTheServiceEmptyString() {
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, null);

        assertEquals("", RomManager.resolveRomForGame("s2"));
    }

    @Test
    void legacyForwarderReturnsWhitespaceOnlyConfigurationVerbatim() {
        configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, " \t");

        assertEquals(" \t", RomManager.resolveRomForGame("s3k"));
    }

    @Test
    void activeRomResolvesConfiguredRelativePathAgainstCurrentWorkingDirectory() throws Exception {
        Path workingDirectory = Files.createDirectory(temporaryDirectory.resolve("active-working-directory"));
        Files.write(workingDirectory.resolve("active.gen"), new byte[] {0x12});
        configureDefaultS2("active.gen");
        String originalUserDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", workingDirectory.toString());

            Rom rom = RomManager.getInstance().getRom();

            assertTrue(rom.isOpen());
            assertEquals(0x12, Byte.toUnsignedInt(rom.readByte(0)));
        } finally {
            restoreUserDirectory(originalUserDirectory);
        }
    }

    @Test
    void activeRomReopenUsesWorkingDirectoryCurrentAtEachResolution() throws Exception {
        Path firstWorkingDirectory = Files.createDirectory(temporaryDirectory.resolve("first-working-directory"));
        Path secondWorkingDirectory = Files.createDirectory(temporaryDirectory.resolve("second-working-directory"));
        Files.write(firstWorkingDirectory.resolve("active.gen"), new byte[] {0x12});
        Files.write(secondWorkingDirectory.resolve("active.gen"), new byte[] {0x34});
        configureDefaultS2("active.gen");
        String originalUserDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", firstWorkingDirectory.toString());
            assertEquals(0x12, Byte.toUnsignedInt(RomManager.getInstance().getRom().readByte(0)));

            RomManager.getInstance().close();
            System.setProperty("user.dir", secondWorkingDirectory.toString());

            assertEquals(0x34, Byte.toUnsignedInt(RomManager.getInstance().getRom().readByte(0)));
        } finally {
            restoreUserDirectory(originalUserDirectory);
        }
    }

    @Test
    void activeRomMissingRelativePathRetainsConfiguredValueInClassifiedFailure() {
        configureDefaultS2("missing/../missing-active.gen");

        IOException failure = assertThrows(IOException.class, () -> RomManager.getInstance().getRom());

        assertEquals("ROM file does not exist: missing/../missing-active.gen", failure.getMessage());
        assertTrue(RomManager.isConfiguredRomMissing(failure));
    }

    /**
     * A blank key means "find it in the catalogue". With nothing to find the
     * legacy unconfigured-ROM failure stands; the working directory is an empty
     * temp directory because the process directory may hold real images.
     */
    @Test
    void activeRomBlankConfigurationRetainsExistingFailure() throws Exception {
        Path workingDirectory = Files.createDirectory(temporaryDirectory.resolve("blank-active-working-directory"));
        configureDefaultS2("");
        String originalUserDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", workingDirectory.toString());

            IOException failure = assertThrows(IOException.class, () -> RomManager.getInstance().getRom());

            assertEquals("ROM filename not configured (DEFAULT_ROM not set or per-game ROM key empty)",
                    failure.getMessage());
        } finally {
            restoreUserDirectory(originalUserDirectory);
        }
    }

    @Test
    void activeRomBlankConfigurationServesAnImageFoundByTheCatalogueScan() throws Exception {
        Path workingDirectory = Files.createDirectory(temporaryDirectory.resolve("blank-scan-working-directory"));
        byte[] image = new byte[0x80000];
        image[0] = 0x56;
        byte[] title = "SONIC THE HEDGEHOG".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, image, 0x120, title.length);
        Files.write(workingDirectory.resolve("Any Name At All.gen"), image);
        configuration.setConfigValue(SonicConfiguration.DEFAULT_ROM, "s1");
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "");
        String originalUserDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", workingDirectory.toString());

            assertEquals(0x56, Byte.toUnsignedInt(RomManager.getInstance().getRom().readByte(0)));
        } finally {
            restoreUserDirectory(originalUserDirectory);
        }
    }

    @Test
    void activeRomDefaultHintAbsentFallsBackToTheCatalogueScan() throws Exception {
        Path workingDirectory = Files.createDirectory(temporaryDirectory.resolve("hint-scan-working-directory"));
        byte[] image = new byte[0x80000];
        image[0] = 0x57;
        byte[] title = "SONIC THE HEDGEHOG".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, image, 0x120, title.length);
        Files.write(workingDirectory.resolve("Any Name At All.gen"), image);
        configuration.setConfigValue(SonicConfiguration.DEFAULT_ROM, "s1");
        assertEquals("s1.gen", configuration.getString(SonicConfiguration.SONIC_1_ROM),
                "this test relies on the built-in default hint");
        String originalUserDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", workingDirectory.toString());

            assertEquals(0x57, Byte.toUnsignedInt(RomManager.getInstance().getRom().readByte(0)));
        } finally {
            restoreUserDirectory(originalUserDirectory);
        }
    }

    @Test
    void secondaryRomsSelectEachConfiguredGameAndResolveRelativePaths() throws Exception {
        Path workingDirectory = Files.createDirectory(temporaryDirectory.resolve("secondary-working-directory"));
        writeReadableRom(workingDirectory.resolve("s1.gen"), (byte) 0x11);
        writeReadableRom(workingDirectory.resolve("s2.gen"), (byte) 0x22);
        writeReadableRom(workingDirectory.resolve("s3k.gen"), (byte) 0x33);
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "s1.gen");
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, "s2.gen");
        configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, "s3k.gen");
        String originalUserDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", workingDirectory.toString());

            assertEquals(0x11, Byte.toUnsignedInt(RomManager.getInstance().getSecondaryRom("s1").readByte(0)));
            assertEquals(0x22, Byte.toUnsignedInt(RomManager.getInstance().getSecondaryRom("s2").readByte(0)));
            assertEquals(0x33, Byte.toUnsignedInt(RomManager.getInstance().getSecondaryRom("s3k").readByte(0)));
        } finally {
            restoreUserDirectory(originalUserDirectory);
        }
    }

    @Test
    void secondaryRomLegacyFallbackUsesS2ForUnknownAndNullGameIds() throws Exception {
        Path workingDirectory = Files.createDirectory(temporaryDirectory.resolve("secondary-fallback-working-directory"));
        writeReadableRom(workingDirectory.resolve("s2.gen"), (byte) 0x22);
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, "s2.gen");
        String originalUserDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", workingDirectory.toString());

            assertEquals(0x22,
                    Byte.toUnsignedInt(RomManager.getInstance().getSecondaryRom("unknown").readByte(0)));
            assertEquals(0x22,
                    Byte.toUnsignedInt(RomManager.getInstance().getSecondaryRom(null).readByte(0)));
        } finally {
            restoreUserDirectory(originalUserDirectory);
        }
    }

    /** Blank key, empty working directory: the legacy unconfigured failure stands. */
    @Test
    void secondaryRomBlankConfigurationRetainsExistingFailure() throws Exception {
        Path workingDirectory = Files.createDirectory(temporaryDirectory.resolve("blank-secondary-working-directory"));
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "");
        String originalUserDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", workingDirectory.toString());

            IOException failure = assertThrows(IOException.class, () -> RomManager.getInstance().getSecondaryRom("s1"));

            assertEquals("No ROM configured for game: s1", failure.getMessage());
        } finally {
            restoreUserDirectory(originalUserDirectory);
        }
    }

    @Test
    void secondaryRomOpenFailureRetainsExactConfiguredValueInDiagnostic() {
        configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, "missing/../unopenable-secondary.gen");

        IOException failure = assertThrows(IOException.class, () -> RomManager.getInstance().getSecondaryRom("s3k"));

        assertEquals("Failed to open secondary ROM: missing/../unopenable-secondary.gen", failure.getMessage());
    }

    private void configureDefaultS2(String configuredRom) {
        configuration.setConfigValue(SonicConfiguration.DEFAULT_ROM, "s2");
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, configuredRom);
    }

    private static void restoreUserDirectory(String originalUserDirectory) {
        if (originalUserDirectory == null) {
            System.clearProperty("user.dir");
        } else {
            System.setProperty("user.dir", originalUserDirectory);
        }
    }

    private static void writeReadableRom(Path path, byte marker) throws IOException {
        byte[] bytes = new byte[0x200];
        bytes[0] = marker;
        Files.write(path, bytes);
    }
}
