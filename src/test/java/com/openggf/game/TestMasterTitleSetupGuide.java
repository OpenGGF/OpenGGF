package com.openggf.game;

import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hub opens the guide once per run, keeps the old onboarding for session-less hubs, and lists it in Extras. */
class TestMasterTitleSetupGuide {
    @TempDir Path dir;

    private MasterTitleScreen hub(SonicConfigurationService config, SetupGuideSession session, boolean games) {
        var screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        if (games) screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        if (session != null) screen.setSetupGuideSession(session, -1);
        return screen;
    }

    @Test void aHubWithoutASessionKeepsTheEarlierStartupRule() {
        var config = SonicConfigurationService.createStandalone(dir);
        var empty = hub(config, null, false);
        empty.openStartupChildForTest();
        assertTrue(empty.onboardingOpenForTest(), "no games: Add your games, as before");
        assertNull(empty.setupGuideForTest());
        assertTrue(empty.extrasRowsForTest().isEmpty(), "no Extras entry without a session");
    }

    @Test void theGuideOpensOnItsOwnOncePerRunEvenWhenTheHubIsRebuilt() {
        var config = SonicConfigurationService.createStandalone(dir);
        var session = new SetupGuideSession(false);
        var first = hub(config, session, true);
        first.openStartupChildForTest();
        assertNotNull(first.setupGuideForTest());
        var rebuilt = hub(config, session, false);
        rebuilt.openStartupChildForTest();
        assertNull(rebuilt.setupGuideForTest(), "same run: never a second time");
        assertFalse(rebuilt.onboardingOpenForTest(), "and no second startup page of any kind");
    }

    @Test void aSettledGuideLeavesTheEarlierRuleInPlace() throws Exception {
        var config = SonicConfigurationService.createStandalone(dir);
        SetupGuidePreset.skip(config);
        var hub = hub(config, new SetupGuideSession(true), false);
        hub.openStartupChildForTest();
        assertNull(hub.setupGuideForTest());
        assertTrue(hub.onboardingOpenForTest(), "skipped with no games: Add your games still helps");
    }

    @Test void extrasListsTheGuideLastAndOpensItAsAReentry() throws Exception {
        var config = SonicConfigurationService.createStandalone(dir);
        SetupGuidePreset.skip(config);
        var hub = hub(config, new SetupGuideSession(false), true);
        hub.setTitleEntries(List.of("Time Attack"), (games, index) -> { });
        List<String> rows = hub.extrasRowsForTest();
        assertEquals(2, rows.size());
        assertEquals("Time Attack", rows.getFirst());
        hub.openExtrasRowForTest(1);
        assertNotNull(hub.setupGuideForTest(), "Extras opens the guide whatever its saved state");
        assertTrue(hub.setupGuideForTest().offersKeep(), "a re-entry offers Keep my settings");
    }
}
