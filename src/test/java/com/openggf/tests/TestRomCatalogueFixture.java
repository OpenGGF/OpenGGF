package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomGame;
import com.openggf.data.RomManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ROM-free contracts for explicit fixture bindings; temporary files are never opened as ROMs. */
class TestRomCatalogueFixture {
    @TempDir Path temporary;

    private SonicConfigurationService configuration() {
        return SonicConfigurationService.createStandalone(temporary);
    }

    private Path image(String name) throws Exception {
        return Files.write(temporary.resolve(name), new byte[] {1});
    }

    @Test
    void copiedPathsAreNormalizedAndOriginalResolvedObjectsAreRestored() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        String blank = " \t ";
        Object original = new Object();
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, blank);
        configuration.setConfigValue(SonicConfiguration.SONIC_2_ROM, null);
        configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, original);
        configuration.setConfigValue(SonicConfiguration.FPS, 51);
        configuration.setSessionOverride(SonicConfiguration.FPS, 73);
        var paths = new EnumMap<RomGame, Path>(RomGame.class);
        Path one = image("one image.bin");
        paths.put(RomGame.S1, temporary.resolve("not-created/../one image.bin"));
        paths.put(RomGame.S2, image("two.bin"));
        paths.put(RomGame.S3K, image("three.bin"));

        try (var fixture = RomCatalogueFixture.bind(configuration, manager, paths)) {
            assertEquals(one.toAbsolutePath().normalize().toString(),
                    configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
            paths.put(RomGame.S1, temporary.resolve("not-an-image"));
            fixture.reapply();
            assertEquals(one.toAbsolutePath().normalize().toString(),
                    configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
            assertEquals(73, configuration.getInt(SonicConfiguration.FPS));
        }

        assertSame(blank, configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
        assertNull(configuration.getConfigValue(SonicConfiguration.SONIC_2_ROM));
        assertSame(original, configuration.getConfigValue(SonicConfiguration.SONIC_3K_ROM));
        assertTrue(configuration.hasSessionOverride(SonicConfiguration.FPS));
        configuration.clearSessionOverrides();
        assertEquals(51, configuration.getInt(SonicConfiguration.FPS));
        verify(manager, times(3)).reloadCatalogue();
        verifyNoMoreInteractions(manager);
    }

    @Test
    void missingLaterImageCannotPartiallyBindAnEarlierKey() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        Object previous = configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM);
        var paths = new EnumMap<RomGame, Path>(RomGame.class);
        paths.put(RomGame.S1, image("one.bin"));
        paths.put(RomGame.S2, temporary.resolve("missing.bin"));

        assertThrows(IllegalArgumentException.class,
                () -> RomCatalogueFixture.bind(configuration, manager, paths));

        assertEquals(previous, configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
        verifyNoInteractions(manager);
    }

    @Test
    void aDirectoryIsNotAReadableRegularImage() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        Object previous = configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM);
        assertThrows(IllegalArgumentException.class, () -> RomCatalogueFixture.bind(
                configuration, manager, Map.of(RomGame.S1, temporary)));
        assertEquals(previous, configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
        verifyNoInteractions(manager);
    }

    @Test
    void nullDeclarationEntriesAreRefusedBeforeAnyWrite() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        Object previous = configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM);
        var paths = new HashMap<RomGame, Path>();
        paths.put(RomGame.S1, image("one.bin"));
        paths.put(null, image("other.bin"));
        assertThrows(NullPointerException.class,
                () -> RomCatalogueFixture.bind(configuration, manager, paths));
        paths.remove(null);
        paths.put(RomGame.S2, null);
        assertThrows(NullPointerException.class,
                () -> RomCatalogueFixture.bind(configuration, manager, paths));
        assertEquals(previous, configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
        verifyNoInteractions(manager);
    }

    @Test
    void aSelectedSessionOverlayIsNotReplacedOrCleared() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "original base");
        configuration.setSessionOverride(SonicConfiguration.SONIC_1_ROM, "another owner's choice");
        Path image = image("one.bin");

        assertThrows(IllegalStateException.class, () -> RomCatalogueFixture.bind(
                configuration, manager, Map.of(RomGame.S1, image)));

        assertEquals("another owner's choice", configuration.getString(SonicConfiguration.SONIC_1_ROM));
        assertTrue(configuration.hasSessionOverride(SonicConfiguration.SONIC_1_ROM));
        configuration.clearSessionOverrides();
        assertEquals("original base", configuration.getString(SonicConfiguration.SONIC_1_ROM));
        verifyNoInteractions(manager);
    }

    @Test
    void aBaseBindingSurvivesTheRecordingRestartOverlayClear() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        Path image = image("two.bin");
        try (var fixture = RomCatalogueFixture.bind(configuration, manager, Map.of(RomGame.S2, image))) {
            configuration.setSessionOverride(SonicConfiguration.DEFAULT_ROM, "s2");
            configuration.clearSessionOverrides();
            assertEquals(image.toAbsolutePath().normalize().toString(),
                    configuration.getString(SonicConfiguration.SONIC_2_ROM));
            assertFalse(configuration.hasSessionOverride(SonicConfiguration.SONIC_2_ROM));
        }
        verify(manager, times(2)).reloadCatalogue();
        verifyNoMoreInteractions(manager);
    }

    @Test
    void reapplyAfterDefaultResetRetainsTheFirstRestorationSnapshot() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, " original value ");
        Path image = image("one.bin");
        try (var fixture = RomCatalogueFixture.bind(configuration, manager, Map.of(RomGame.S1, image))) {
            configuration.resetToDefaults();
            assertNotEquals(image.toString(), configuration.getString(SonicConfiguration.SONIC_1_ROM));
            fixture.reapply();
            assertEquals(image.toAbsolutePath().normalize().toString(),
                    configuration.getString(SonicConfiguration.SONIC_1_ROM));
        }
        assertEquals(" original value ", configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
        verify(manager, times(3)).reloadCatalogue();
        verifyNoMoreInteractions(manager);
    }

    @Test
    void reapplyValidatesEveryFileAndOverlayBeforeAnyWrite() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        Path one = image("one.bin");
        Path two = image("two.bin");
        try (var fixture = RomCatalogueFixture.bind(configuration, manager,
                Map.of(RomGame.S1, one, RomGame.S2, two))) {
            configuration.resetToDefaults();
            Object previous = configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM);
            configuration.setSessionOverride(SonicConfiguration.SONIC_2_ROM, "foreign");
            assertThrows(IllegalStateException.class, fixture::reapply);
            assertEquals(previous, configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
            assertEquals("foreign", configuration.getString(SonicConfiguration.SONIC_2_ROM));
            configuration.clearSessionOverrides();
            Files.delete(two);
            assertThrows(IllegalArgumentException.class, fixture::reapply);
            assertEquals(previous, configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
        }
        verify(manager, times(2)).reloadCatalogue();
        verifyNoMoreInteractions(manager);
    }

    @Test
    void closeRestoresBaseValuesWithoutClearingNewOverlaysAndIsIdempotent() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "old");
        var fixture = RomCatalogueFixture.bind(configuration, manager, Map.of(RomGame.S1, image("one.bin")));
        configuration.setSessionOverride(SonicConfiguration.SONIC_1_ROM, "new session owner");
        configuration.setSessionOverride(SonicConfiguration.FPS, 71);

        fixture.close();
        fixture.close();

        assertEquals("new session owner", configuration.getString(SonicConfiguration.SONIC_1_ROM));
        assertEquals(71, configuration.getInt(SonicConfiguration.FPS));
        configuration.clearSessionOverrides();
        assertEquals("old", configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
        verify(manager, times(2)).reloadCatalogue();
        verifyNoMoreInteractions(manager);
    }

    @Test
    void failingFixtureBodyStillRestoresAndClosedScopeCannotReapply() throws Exception {
        var configuration = configuration();
        var manager = mock(RomManager.class);
        configuration.setConfigValue(SonicConfiguration.SONIC_1_ROM, "before");
        var fixture = RomCatalogueFixture.bind(configuration, manager, Map.of(RomGame.S1, image("one.bin")));
        var original = new IllegalArgumentException("fixture assertion");

        assertSame(original, assertThrows(IllegalArgumentException.class, () -> {
            try (fixture) {
                throw original;
            }
        }));

        assertEquals("before", configuration.getConfigValue(SonicConfiguration.SONIC_1_ROM));
        assertThrows(IllegalStateException.class, fixture::reapply);
        verify(manager, times(2)).reloadCatalogue();
        verifyNoMoreInteractions(manager);
    }
}
