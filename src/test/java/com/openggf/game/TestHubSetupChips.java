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
        assertEquals(List.of("FROM SONIC 3&K", "4:3", "REWIND OFF", "DEBUG ON"), labels(overridden),
                "the donor leads, and under the 3&K save screen it is the only team status chip");
        assertTrue(overridden.stream().allMatch(HubSetupChips.Chip::thisGameOnly));
        assertEquals(List.of("FROM SONIC 1", "SONIC", "4:3"), labels(HubSetupChips.forStock(SONIC_2,
                new LaunchProfile(LaunchToggle.DEFAULT, "s1", LaunchToggle.DEFAULT, "NATIVE_4_3", "sonic", "none"),
                store, config, 0)).subList(0, 3), "a donor comes before the team it decides");
    }

    @Test void modsChipHasNoProfileRowAndSummaryReadsNaturally() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new LaunchProfileStore(config);
        var chips = HubSetupChips.forStock(SONIC_2,
                new LaunchProfile(LaunchToggle.ON, "off", LaunchToggle.DEFAULT, "global", "sonic", "tails"), store, config, 1);
        assertNull(chips.getLast().row(), "the mods chip reports mods, not a setup row");
        assertEquals("1 MOD", chips.getLast().label());
        assertEquals(List.of(), HubSetupChips.status(chips, HubSetupChips.fit(chips, 304), false),
                "nothing to add when every chip is shown");
        var shown = HubSetupChips.fit(chips, 100);
        assertEquals("Also " + String.join(", ", HubSetupChips.hidden(chips, shown).stream().map(HubSetupChips::phrase).toList()),
                HubSetupChips.plain(HubSetupChips.status(chips, shown, false)));
        assertTrue(HubSetupChips.plain(HubSetupChips.status(chips, shown, false)).endsWith("rewind on, 1 mod"));
    }

    @Test void saveScreenTeamIsNamedInWordsAndHiddenAmberChipsStayAmber() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new LaunchProfileStore(config);
        var all = HubSetupChips.forStock(SONIC_2,
                new LaunchProfile(LaunchToggle.ON, "s3k", LaunchToggle.ON, "WIDE_16_10", "sonic", "tails"), store, config, 12);
        var shown = HubSetupChips.fit(all, 200);
        assertEquals("+3 MORE", shown.getLast().label());
        assertTrue(shown.getLast().thisGameOnly(), "the count is amber when it hides per-game values");
        var status = HubSetupChips.status(all, shown, true);
        assertEquals("Team on the 3&K save screen; also rewind on, debug on, 12 mods", HubSetupChips.plain(status));
        assertEquals(List.of("Team on the 3&K save screen", "rewind on", "debug on"),
                status.stream().filter(HubSetupChips.Segment::thisGameOnly).map(HubSetupChips.Segment::text).toList());
        assertEquals(HubSetupChips.SAVE_SCREEN_TEAM, HubSetupChips.plain(HubSetupChips.status(all, all, true)));
        var twoLines = HubSetupChips.lines(status, 180);
        assertEquals(2, twoLines.size());
        assertTrue(HubSetupChips.plain(twoLines.get(1)).startsWith("Also "), "a new line restarts the sentence: " + twoLines);
        assertEquals(HubSetupChips.plain(status).replace("; also", "").replace(",", "").replace("Also ", "").replace(" ", ""),
                (HubSetupChips.plain(twoLines.get(0)) + HubSetupChips.plain(twoLines.get(1))).replace("; also", "")
                        .replace(",", "").replace("Also ", "").replace(" ", ""), "nothing is lost across the break");
    }

    /**
     * Every launch profile reachable through Game Setup, for every game, with every all-games
     * rewind/debug/aspect setting and 0, 1 or 12 mods, at the 320 and 400 hub widths: the chips
     * fit, nothing repeats, and whatever "+N MORE" hides is named on one status line, amber
     * where it is set for this game only.
     */
    @Test void everyReachableProfileShowsItsWholeStatusAt320And400() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new LaunchProfileStore(config);
        int checked = 0, crowded = 0;
        for (var game : MasterTitleScreen.GameEntry.values()) {
            var seen = new java.util.LinkedHashSet<LaunchProfile>();
            var queue = new java.util.ArrayDeque<LaunchProfile>(List.of(LaunchProfile.stockFor(game)));
            while (!queue.isEmpty()) {
                LaunchProfile profile = queue.poll();
                if (!seen.add(profile)) continue;
                for (var row : LaunchProfile.Row.values()) {
                    queue.add(profile.withNext(row, game));
                    queue.add(profile.withPrevious(row, game));
                }
            }
            for (LaunchProfile profile : seen) {
                for (boolean rewind : new boolean[] {false, true}) for (boolean debug : new boolean[] {false, true})
                    for (String aspect : List.of("NATIVE_4_3", "WIDE_16_10", "WIDE_16_9", "ULTRA_21_9", "SUPER_32_9"))
                        for (int mods : new int[] {0, 1, 12}) {
                            config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, rewind);
                            config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, debug);
                            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, aspect);
                            var all = HubSetupChips.forStock(game, profile, store, config, mods);
                            String where = game + " " + profile + " rewind " + rewind + " debug " + debug + " " + aspect + " mods " + mods;
                            assertEquals(all.size(), labels(all).stream().distinct().count(), "no repeated chip: " + where);
                            assertFalse(labels(all).contains("TEAM ON SAVE SCREEN"), where);
                            assertEquals(profile.usesS3kDataSelectCharacters(),
                                    all.stream().noneMatch(c -> c.row() == LaunchProfile.Row.MAIN_CHARACTER), where);
                            for (int width : new int[] {304, 384}) {
                                var shown = HubSetupChips.fit(all, width);
                                assertTrue(shown.stream().mapToInt(c -> MenuChrome.chipWidth(c.label()) + 4).sum() - 4 <= width, where);
                                var hidden = HubSetupChips.hidden(all, shown);
                                if (!hidden.isEmpty()) crowded++;
                                assertEquals(all.subList(0, shown.size() - (hidden.isEmpty() ? 0 : 1)),
                                        shown.subList(0, shown.size() - (hidden.isEmpty() ? 0 : 1)), where);
                                if (!hidden.isEmpty()) assertEquals(hidden.stream().anyMatch(HubSetupChips.Chip::thisGameOnly),
                                        shown.getLast().thisGameOnly(), "amber count: " + where);
                                var status = HubSetupChips.status(all, shown, profile.usesS3kDataSelectCharacters());
                                var lines = HubSetupChips.lines(status, width);
                                assertTrue(lines.size() <= 1, "one status line at " + width + ": " + HubSetupChips.plain(status) + " " + where);
                                assertTrue(HubSetupChips.statusWidth(status) <= width, where);
                                for (var chip : hidden) assertTrue(status.contains(new HubSetupChips.Segment(
                                        HubSetupChips.phrase(chip), chip.thisGameOnly())), chip + " named: " + where);
                                if (profile.usesS3kDataSelectCharacters())
                                    assertTrue(status.getFirst().thisGameOnly() && status.getFirst().text().contains("save screen"), where);
                                checked++;
                            }
                        }
            }
        }
        assertTrue(checked > 1000, "combinations checked: " + checked);
        assertTrue(crowded > 0, "some combinations overflow the chip row");
    }
    @Test void crowdedProfilesKeepStatusInsideNativeViewport() {
        var config = SonicConfigurationService.createStandalone(directory);
        var store = new LaunchProfileStore(config);
        var saveScreen = HubSetupChips.forStock(SONIC_2,
                new LaunchProfile(LaunchToggle.ON, "s3k", LaunchToggle.ON, "NATIVE_4_3", "sonic", "tails"),
                store, config, 12);
        assertEquals(saveScreen, HubSetupChips.fit(saveScreen, 304), "one donor chip leaves room for every chip at 320");
        var all = HubSetupChips.forStock(SONIC_2,
                new LaunchProfile(LaunchToggle.OFF, "s1", LaunchToggle.ON, "WIDE_16_10", "sonic", "tails"),
                store, config, 12);
        var shown = HubSetupChips.fit(all, 304);
        assertTrue(shown.stream().mapToInt(c -> MenuChrome.chipWidth(c.label()) + 4).sum() - 4 <= 304);
        assertEquals(all.getFirst(), shown.getFirst());
        assertEquals("+" + (all.size() - shown.size() + 1) + " MORE", shown.getLast().label());
        assertEquals(all, HubSetupChips.fit(all, 1000));
    }

}
