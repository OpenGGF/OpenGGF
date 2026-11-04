package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestGameSupportNotices {
    @TempDir Path directory;

    @Test void sonic3AndKnucklesIsGatedAsAlphaAndTheOthersStartFreely() {
        var notices = new GameSupportNotices(SonicConfigurationService.createStandalone(directory));
        assertEquals(Optional.of(GameSupportNotices.Stage.ALPHA), notices.pendingNotice("s3k"));
        assertEquals(Optional.of(GameSupportNotices.Stage.ALPHA), notices.pendingNotice("S3K"), "game IDs ignore case");
        assertEquals(Optional.empty(), notices.pendingNotice("s1"));
        assertEquals(Optional.empty(), notices.pendingNotice("s2"));
        assertEquals(Optional.empty(), notices.pendingNotice(null));
    }

    @Test void dontShowAgainPersistsAcrossLaunches() {
        new GameSupportNotices(SonicConfigurationService.createStandalone(directory)).dismiss("s3k", GameSupportNotices.Stage.ALPHA);
        var fresh = SonicConfigurationService.createStandalone(directory);
        assertEquals("s3k:alpha", fresh.getString(SonicConfiguration.HUB_DISMISSED_NOTICES));
        assertEquals(Optional.empty(), new GameSupportNotices(fresh).pendingNotice("s3k"));
        assertEquals(Optional.of(GameSupportNotices.Stage.ALPHA), new GameSupportNotices(fresh).stageOf("s3k"),
                "hiding the notice does not change the game's stage");
    }

    @Test void aDismissalCoversOnlyItsStageAndAnyGameCanBeGated() {
        var config = SonicConfigurationService.createStandalone(directory);
        new GameSupportNotices(config).dismiss("s3k", GameSupportNotices.Stage.ALPHA);
        var later = new GameSupportNotices(config, Map.of(
                "s3k", GameSupportNotices.Stage.BETA, "s2", GameSupportNotices.Stage.ALPHA));
        assertEquals(Optional.of(GameSupportNotices.Stage.BETA), later.pendingNotice("s3k"),
                "a game moving to beta shows its new notice once");
        assertEquals(Optional.of(GameSupportNotices.Stage.ALPHA), later.pendingNotice("s2"));
        later.dismiss("s2", GameSupportNotices.Stage.ALPHA);
        later.dismiss("s2", GameSupportNotices.Stage.ALPHA);
        assertEquals("s3k:alpha,s2:alpha", config.getString(SonicConfiguration.HUB_DISMISSED_NOTICES),
                "dismissals accumulate without duplicates");
    }
}
