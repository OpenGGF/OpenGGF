package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.game.launch.LaunchToggle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_1;
import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_2;
import static org.junit.jupiter.api.Assertions.*;

class TestHubSetupChips {
    @TempDir Path directory;

    private List<String> labels(List<HubSetupChips.Chip> chips) {
        return chips.stream().map(HubSetupChips.Chip::label).toList();
    }

    @Test void stockProfileShowsTeamAndScreenFromSettings() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new LaunchProfileStore(config);
        var chips = HubSetupChips.forStock(SONIC_2, LaunchProfile.stockFor(SONIC_2), store, config, 0);
        assertEquals(List.of("SONIC & TAILS", "4:3"), labels(chips));
        assertTrue(chips.stream().noneMatch(HubSetupChips.Chip::thisGameOnly), "nothing is set for this game only");
        assertEquals(List.of("SONIC"), labels(HubSetupChips.forStock(SONIC_1, LaunchProfile.stockFor(SONIC_1), store, config, 0))
                .subList(0, 1), "a solo team names one character");
    }

    @Test void overridesAreAmberAndDefaultsFollowSettings() {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
        var store = new LaunchProfileStore(config);

        var inherited = HubSetupChips.forStock(SONIC_2, LaunchProfile.stockFor(SONIC_2), store, config, 2);
        assertEquals(List.of("SONIC & TAILS", "16:9", "REWIND ON", "2 MODS"), labels(inherited));
        assertTrue(inherited.stream().noneMatch(HubSetupChips.Chip::thisGameOnly));

        var overridden = HubSetupChips.forStock(SONIC_2,
                new LaunchProfile(LaunchToggle.OFF, "s3k", LaunchToggle.ON, "NATIVE_4_3", "sonic", "tails"),
                store, config, 0);
        assertEquals(List.of("TEAM ON SAVE SCREEN", "4:3", "FROM SONIC 3&K", "REWIND OFF", "DEBUG ON"), labels(overridden));
        assertTrue(overridden.stream().allMatch(HubSetupChips.Chip::thisGameOnly));
    }

    @Test void modsChipHasNoProfileRowAndSummaryReadsNaturally() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new LaunchProfileStore(config);
        var chips = HubSetupChips.forStock(SONIC_2,
                new LaunchProfile(LaunchToggle.ON, "off", LaunchToggle.DEFAULT, "global", "sonic", "tails"), store, config, 1);
        assertNull(chips.getLast().row(), "the mods chip reports mods, not a setup row");
        assertEquals("1 MOD", chips.getLast().label());
        assertEquals("Start Sonic 2 as Sonic & Tails, 4:3, rewind on", HubSetupChips.startSummary("Sonic 2", chips));
    }
    @Test void crowdedProfilesKeepStatusInsideNativeViewport() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new LaunchProfileStore(config);
        var all = HubSetupChips.forStock(SONIC_2,
                new LaunchProfile(LaunchToggle.ON, "s3k", LaunchToggle.ON, "NATIVE_4_3", "sonic", "tails"),
                store, config, 12);
        var shown = HubSetupChips.fit(all, 304);
        assertTrue(shown.stream().mapToInt(c -> MenuChrome.chipWidth(c.label()) + 4).sum() - 4 <= 304);
        assertEquals(all.getFirst(), shown.getFirst());
        assertEquals("+" + (all.size() - shown.size() + 1) + " MORE", shown.getLast().label());
        assertEquals(all, HubSetupChips.fit(all, 1000));
    }

}
