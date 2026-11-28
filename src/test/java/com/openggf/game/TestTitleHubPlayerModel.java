package com.openggf.game;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.launch.LaunchProfileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_2;
import static com.openggf.game.TestMasterTitleHub.press;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestTitleHubPlayerModel {
    @TempDir Path preferences;

    private static final MasterTitleEntry.ModGame TIME_ATTACK =
            new MasterTitleEntry.ModGame("time-attack", "Time Attack", "any");

    private static List<MasterTitleEntry> catalog(int mods) {
        var cards = new ArrayList<MasterTitleEntry>();
        for (var game : MasterTitleScreen.GameEntry.values()) {
            cards.add(game.cartridge() ? new MasterTitleEntry.Stock(game)
                    : MasterTitleEntry.Hosted.of(GameId.fromCode(game.gameId)));
        }
        if (mods > 0) cards.add(TIME_ATTACK);
        IntStream.range(1, mods).mapToObj(i -> new MasterTitleEntry.ModGame("game-" + i, "Game " + i, "s2"))
                .forEach(cards::add);
        return List.copyOf(cards);
    }

    @Test
    void shelfFillsThreeSlotsAndRestoresKeptAndRecentCards() throws Exception {
        var config = SonicConfigurationService.createStandalone(preferences);
        var library = new TitleGameLibrary(config);
        library.catalog(catalog(0));
        assertEquals(catalog(0), library.shelf());
        library.catalog(catalog(3));
        assertEquals(catalog(3), library.shelf());
        var all = catalog(10);
        library.catalog(all);
        assertEquals(new MasterTitleEntry.Library(14), library.shelf().getLast());
        library.toggleKept(all.get(9));
        library.rememberPlayed(all.get(12));
        library.rememberPlayed(all.get(11));

        var reopened = new TitleGameLibrary(SonicConfigurationService.createStandalone(preferences));
        reopened.catalog(all);
        assertEquals(all.subList(0, 4), reopened.shelf().subList(0, 4));
        assertEquals(List.of(all.get(9), all.get(11), all.get(12)), reopened.shelf().subList(4, 7));
    }

    @Test
    void libraryFiltersStockAndModsAndKeepsCardIdentity() {
        var library = new TitleGameLibrary(SonicConfigurationService.createStandalone(preferences));
        var all = catalog(10);
        library.catalog(all);
        library.open(TIME_ATTACK.owner());
        assertEquals(TIME_ATTACK, library.selected());
        assertEquals(14, library.filtered().size());
        library.filter(TitleGameLibrary.Filter.SONIC);
        assertEquals(all.subList(0, 4), library.filtered());
        library.filter(TitleGameLibrary.Filter.MODS);
        assertEquals(all.subList(4, all.size()), library.filtered());
    }

    @Test
    void stockChipCountsOnlyTheSuppliedContributorsAndReflectsModsOff() {
        var config = SonicConfigurationService.createStandalone(preferences);
        var screen = screen(config, 0);
        assertFalse(screen.hubChips().stream().anyMatch(chip -> chip.label().contains("mod")));
        screen.setEnabledModsByGame(Map.of(SONIC_2, List.of("Level Pack")));
        assertTrue(screen.hubChips().stream().anyMatch(chip -> chip.label().equals("1 mod")));
        screen.setEnabledModsByGame(Map.of(SONIC_2, List.of("Level Pack", "Music Pack")));
        assertTrue(screen.hubChips().stream().anyMatch(chip -> chip.label().equals("2 mods")));
        press(screen, GLFW_KEY_TAB);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.hubChips().stream().anyMatch(chip -> chip.label().equals("Mods off")));
        assertFalse(new LaunchProfileStore(SonicConfigurationService.createStandalone(preferences)).modsOn(SONIC_2));
    }

    @Test
    void stockAndRepeatModGameTakeOneHubInputAfterTheDisclaimer() {
        var config = SonicConfigurationService.createStandalone(preferences);
        var stock = screen(config, 0);
        assertEquals(2, 1 + inputs(stock, GLFW_KEY_ENTER));
        assertTrue(stock.isGameSelected());

        var played = screen(config, 10);
        var launched = new ArrayList<MasterTitleEntry.ModGame>();
        played.setModGameOpener(launched::add);
        inputs(played, GLFW_KEY_L, GLFW_KEY_ENTER);
        assertEquals(List.of(TIME_ATTACK), launched);
        var repeat = screen(SonicConfigurationService.createStandalone(preferences), 10);
        repeat.setModGameOpener(launched::add);
        assertEquals(2, 1 + inputs(repeat, GLFW_KEY_ENTER));
        assertEquals(List.of(TIME_ATTACK, TIME_ATTACK), launched);
    }

    @Test
    void timeAttackStartsFromLibraryInThreeInputsIncludingTheDisclaimer() {
        var config = SonicConfigurationService.createStandalone(preferences);
        for (boolean returning : List.of(false, true)) {
            var screen = screen(config, 10);
            var launched = new ArrayList<MasterTitleEntry.ModGame>();
            screen.setModGameOpener(launched::add);
            assertTrue(1 + inputs(screen, GLFW_KEY_L, GLFW_KEY_ENTER) <= (returning ? 3 : 4));
            assertEquals(List.of(TIME_ATTACK), launched);
        }
    }

    @Test
    void plainSonic2TakesThreeInputsFromTheHubWhenAModIsEnabled() {
        var config = SonicConfigurationService.createStandalone(preferences);
        var screen = screen(config, 0);
        screen.setEnabledModsByGame(Map.of(SONIC_2, List.of("Level Pack")));
        assertEquals(3, inputs(screen, GLFW_KEY_TAB, GLFW_KEY_ENTER, GLFW_KEY_ENTER));
        assertTrue(screen.isGameSelected());
        assertFalse(screen.launchProfileStore().modsOn(SONIC_2));
    }

    @Test
    void everyCardInATwentyModLibraryCanBeReachedAndStartedInEightInputs() {
        var config = SonicConfigurationService.createStandalone(preferences);
        var library = new TitleGameLibrary(config);
        library.catalog(catalog(20));
        library.open(TIME_ATTACK.owner());
        List<MasterTitleEntry> all = library.filtered();
        for (int i = 0; i < all.size(); i++) {
            var screen = screen(config, 20);
            var launched = new ArrayList<MasterTitleEntry.ModGame>();
            screen.setModGameOpener(launched::add);
            int count = inputs(screen, GLFW_KEY_L);
            int rowSteps = i / TitleGameLibrary.COLUMNS - library.index() / TitleGameLibrary.COLUMNS;
            int colSteps = i % TitleGameLibrary.COLUMNS - library.index() % TitleGameLibrary.COLUMNS;
            for (int step = 0; step < Math.abs(rowSteps); step++)
                count += inputs(screen, rowSteps > 0 ? GLFW_KEY_DOWN : GLFW_KEY_UP);
            for (int step = 0; step < Math.abs(colSteps); step++)
                count += inputs(screen, colSteps > 0 ? GLFW_KEY_RIGHT : GLFW_KEY_LEFT);
            assertEquals(all.get(i).gameId(), screen.getSelectedGameId());
            if (all.get(i) instanceof MasterTitleEntry.ModGame card) {
                count += inputs(screen, GLFW_KEY_ENTER);
                assertEquals(List.of(card), launched);
                assertTrue(count <= 8, card.displayName() + " takes " + count + " inputs");
            }
        }
    }

    private static MasterTitleScreen screen(SonicConfigurationService config, int mods) {
        var screen = new MasterTitleScreen(config, new LaunchProfileStore(config), catalog(mods));
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        screen.setOnlineGameCard(TIME_ATTACK.owner());
        screen.setRomAvailableForTest(SONIC_2, true);
        return screen;
    }

    private static int inputs(MasterTitleScreen screen, int... keys) {
        for (int key : keys) press(screen, key);
        return keys.length;
    }
}
