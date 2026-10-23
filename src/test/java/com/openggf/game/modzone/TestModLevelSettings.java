package com.openggf.game.modzone;

import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.Sonic1ModLevelSettingsProvider;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2ModLevelSettingsProvider;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kModLevelSettingsProvider;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestModLevelSettings {
    @Test void settingsAreImmutableBoundedAndKeepIndependentDefaults() {
        var sidekicks = new ArrayList<>(List.of(CharacterKey.TAILS));
        var team = new GameplayLaunchTeam(CharacterKey.SONIC, sidekicks);
        var source = new ModLevelSettings.StockScrollSource(4, 0);
        var settings = ModLevelSettings.DEFAULT.withBackground(source).withWaterHeight(512).withTeam(team);
        sidekicks.clear();
        assertEquals(List.of(CharacterKey.TAILS), settings.team().sidekicks());
        assertNull(ModLevelSettings.DEFAULT.background());
        assertNull(ModLevelSettings.DEFAULT.waterHeight());
        assertNull(ModLevelSettings.DEFAULT.team());
        assertEquals(ModLevelSettings.DEFAULT, settings.withBackground(null).withWaterHeight(null).withTeam(null));
        assertThrows(IllegalArgumentException.class, () -> new ModLevelSettings.StockScrollSource(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ModLevelSettings.StockScrollSource(0, 256));
        assertThrows(IllegalArgumentException.class, () -> settings.withWaterHeight(32768));
        assertThrows(IllegalArgumentException.class, () -> settings.withWaterHeight(-32769));
        assertDoesNotThrow(() -> settings.withWaterHeight(Short.MIN_VALUE * 1));
        assertThrows(IllegalArgumentException.class, () -> settings.withTeam(new GameplayLaunchTeam(
                CharacterKey.SONIC, java.util.Collections.nCopies(17, CharacterKey.TAILS))));
    }

    @Test void hostsAndDecoratorsExposeOnlyExplicitlySupportedSources() throws Exception {
        var modules = List.of(new Sonic1GameModule(), new Sonic2GameModule(), new Sonic3kGameModule());
        for (var module : modules) {
            var provider = module.getModLevelSettingsProvider();
            assertFalse(provider.stockScrollSources().isEmpty());
            assertTrue(provider.supportsWater());
            assertThrows(UnsupportedOperationException.class, () -> provider.stockScrollSources().clear());
            assertThrows(IllegalArgumentException.class, () -> provider.validate(ModLevelSettings.DEFAULT
                    .withBackground(new ModLevelSettings.StockScrollSource(255, 255))));
            var decorated = new DelegatingGameModule(module, "settings-test") { };
            assertEquals(provider.stockScrollSources(), decorated.getModLevelSettingsProvider().stockScrollSources());
            var defaults = provider.create(ModLevelSettings.DEFAULT, null);
            assertNull(defaults.scroll());
            assertNull(defaults.water());
            assertThrows(IOException.class, () -> provider.create(ModLevelSettings.DEFAULT.withWaterHeight(300), null));
        }
        assertThrows(IllegalArgumentException.class, () -> ModLevelSettingsProvider.UNSUPPORTED.validate(
                ModLevelSettings.DEFAULT.withWaterHeight(300)));
    }

    @Test void sourceActWrapperPreservesOutputsAndDelegatesLogicalRewindState() {
        class Stateful extends AbstractZoneScrollHandler {
            int initializedAct = -1, updatedAct = -1, state;
            @Override public void init(int act, int x, int y) { initializedAct = act; }
            @Override public void update(int[] words, int x, int y, int frame, int act) {
                updatedAct = act; words[0] = ++state;
            }
            @Override public Object captureRewindState() { return state; }
            @Override public void restoreRewindState(Object saved) { state = (Integer) saved; }
            @Override public int getBgPeriodWidth() { return 1024; }
            @Override public short getVscrollFactorFG() { return 27; }
            @Override public int getShakeOffsetX() { return -3; }
        }
        var nativeHandler = new Stateful();
        var wrapper = new SourceActScrollHandler(nativeHandler, 2);
        wrapper.init(0, 0, 0);
        int[] words = new int[224];
        wrapper.update(words, 100, 50, 1, 0);
        Object snapshot = wrapper.captureRewindState();
        wrapper.update(words, 104, 52, 2, 0);
        int expected = words[0];
        wrapper.restoreRewindState(snapshot);
        wrapper.update(words, 104, 52, 2, 9);
        assertEquals(expected, words[0]);
        assertEquals(2, nativeHandler.initializedAct);
        assertEquals(2, nativeHandler.updatedAct);
        assertEquals(1024, wrapper.getBgPeriodWidth());
        assertEquals(27, wrapper.getVscrollFactorFG());
        assertEquals(-3, wrapper.getShakeOffsetX());
    }

    @Test void flyingBatteryIndoorDeformationVariesWithCameraAndReplaysAfterRestore() throws Exception {
        var provider = new Sonic3kModLevelSettingsProvider();
        var settings = ModLevelSettings.DEFAULT.withBackground(new ModLevelSettings.StockScrollSource(4, 0));
        var first = provider.create(settings, null).scroll();
        var independent = provider.create(settings, null).scroll();
        assertNotSame(first, independent);
        first.init(8, 128, 64);
        int[] initial = new int[224], moved = new int[224], replay = new int[224];
        first.update(initial, 128, 64, 10, 8);
        Object saved = first.captureRewindState();
        first.update(moved, 256, 96, 11, 8);
        assertTrue(java.util.stream.IntStream.range(0, 224)
                .anyMatch(line -> (short) initial[line] != (short) moved[line]), "Stock BG bands must move with the camera");
        first.update(replay, 1024, 384, 30, 8);
        first.restoreRewindState(saved);
        first.update(replay, 256, 96, 11, 8);
        assertArrayEquals(moved, replay);
        independent.init(0, 128, 64);
        independent.update(replay, 128, 64, 10, 0);
        assertArrayEquals(initial, replay, "An independent load must not inherit another handler's state");
        assertThrows(IllegalArgumentException.class, () -> provider.validate(settings.withBackground(
                new ModLevelSettings.StockScrollSource(4, 1))), "FBZ2 outdoor/boss state is not advertised");
    }

    @Test void greenHillRetainsItsInitialCameraAnchorAcrossRewindReplay() throws Exception {
        var provider = new Sonic1ModLevelSettingsProvider();
        var settings = ModLevelSettings.DEFAULT.withBackground(new ModLevelSettings.StockScrollSource(0, 0));
        var handler = provider.create(settings, null).scroll();
        int[] words = new int[224];
        handler.init(8, 400, 80);
        handler.update(words, 400, 80, 10, 8);
        Object saved = handler.captureRewindState();
        handler.update(words, 416, 80, 11, 8);
        int[] expected = words.clone();
        handler.update(words, 600, 96, 30, 8);
        handler.restoreRewindState(saved);
        handler.update(words, 416, 80, 11, 8);
        assertArrayEquals(expected, words);
        assertTrue(Arrays.stream(words).anyMatch(word -> (short) word != 0));
    }

    @Test void emeraldHillRequiresItsRealRomTable() {
        var provider = new Sonic2ModLevelSettingsProvider();
        var settings = ModLevelSettings.DEFAULT.withBackground(new ModLevelSettings.StockScrollSource(0, 1));
        assertThrows(IOException.class, () -> provider.create(settings, null));
    }
}
