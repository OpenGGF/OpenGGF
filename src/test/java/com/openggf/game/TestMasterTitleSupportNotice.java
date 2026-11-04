package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.launch.GameSupportNotices;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.i18n.UiLanguage;
import com.openggf.i18n.UiMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.openggf.game.TestMasterTitleHub.press;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** The hub asks before starting a game whose support is unfinished; Back returns to the shelf. */
class TestMasterTitleSupportNotice {
    @TempDir Path directory;

    @AfterEach void restoreEnglish() { UiMessages.selectLanguage("en"); }

    private SonicConfigurationService config() {
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        return config;
    }

    private static MasterTitleScreen screen(SonicConfigurationService config) {
        return prepared(new MasterTitleScreen(config));
    }

    private static MasterTitleScreen prepared(MasterTitleScreen screen) {
        screen.setSupportLinkLauncherForTest(command -> fail("no test may open a real browser: " + command));
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (MasterTitleScreen.GameEntry game : MasterTitleScreen.GameEntry.values()) {
            screen.setRomAvailableForTest(game, true);
        }
        return screen;
    }

    private static void selectS3k(MasterTitleScreen screen) {
        while (!"s3k".equals(screen.getSelectedGameId())) press(screen, GLFW_KEY_RIGHT);
    }

    @Test void startingSonic3AndKnucklesAsksFirstAndBackReturnsToTheHub() {
        var screen = screen(config());
        selectS3k(screen);
        press(screen, GLFW_KEY_ENTER);
        assertNotNull(screen.supportNoticeForTest(), "the alpha notice opens instead of starting the game");
        assertFalse(screen.isGameSelected());
        assertEquals(GameSupportNotices.Stage.ALPHA, screen.supportNoticeForTest().stage());

        press(screen, GLFW_KEY_ESCAPE);
        assertNull(screen.supportNoticeForTest(), "Back closes the notice");
        assertFalse(screen.isGameSelected(), "Back never starts the game");
        assertFalse(screen.isQuitPromptOpenForTest(), "Back returns to the shelf, not the quit prompt");
        assertEquals("s3k", screen.getSelectedGameId());
    }

    @Test void continueStartsTheGameAndAsksAgainNextTime() {
        var config = config();
        var screen = screen(config);
        selectS3k(screen);
        press(screen, GLFW_KEY_ENTER);
        assertEquals(GameSupportNoticeDialog.Focus.CONTINUE, screen.supportNoticeForTest().focus(), "Continue is focused first");
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected());
        assertEquals("s3k", screen.getSelectedGameId());
        assertEquals("", config.getString(SonicConfiguration.HUB_DISMISSED_NOTICES), "Continue alone hides nothing");

        var again = screen(config);
        selectS3k(again);
        press(again, GLFW_KEY_ENTER);
        assertNotNull(again.supportNoticeForTest());
    }

    @Test void dontShowAgainHidesTheNoticeOnLaterStarts() {
        var config = config();
        var screen = screen(config);
        selectS3k(screen);
        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_LEFT);
        assertEquals(GameSupportNoticeDialog.Focus.DONT_SHOW_AGAIN, screen.supportNoticeForTest().focus());
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.supportNoticeForTest().dontShowAgain());
        assertFalse(screen.isGameSelected(), "flipping the toggle does not start the game");
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected());
        assertEquals("s3k:alpha", config.getString(SonicConfiguration.HUB_DISMISSED_NOTICES));

        var later = screen(config);
        selectS3k(later);
        press(later, GLFW_KEY_ENTER);
        assertNull(later.supportNoticeForTest());
        assertTrue(later.isGameSelected(), "a hidden notice starts the game directly");
    }

    @Test void toggledThenBackedOutRemembersNothing() {
        var config = config();
        var screen = screen(config);
        selectS3k(screen);
        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_LEFT);
        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_ESCAPE);
        assertEquals("", config.getString(SonicConfiguration.HUB_DISMISSED_NOTICES));
    }

    @Test void theLinksAreFocusableAndOpenInTheBrowserWithoutLeaving() {
        var screen = screen(config());
        List<List<String>> opened = new ArrayList<>();
        screen.setSupportLinkLauncherForTest(opened::add);
        selectS3k(screen);
        press(screen, GLFW_KEY_ENTER);
        var notice = screen.supportNoticeForTest();

        press(screen, GLFW_KEY_UP);
        assertEquals(GameSupportNoticeDialog.Focus.REPORT_ISSUE, notice.focus(), "Up from the controls reaches the links");
        press(screen, GLFW_KEY_UP);
        assertEquals(GameSupportNoticeDialog.Focus.DISCORD, notice.focus());
        press(screen, GLFW_KEY_UP);
        assertEquals(GameSupportNoticeDialog.Focus.DISCORD, notice.focus(), "the first link is the top row");
        press(screen, GLFW_KEY_LEFT);
        assertEquals(GameSupportNoticeDialog.Focus.DISCORD, notice.focus(), "a link row has nothing beside it");
        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_ENTER);
        assertEquals(List.of("https://openggf.com/discord", "https://openggf.com/report-issue"),
                opened.stream().map(List::getLast).toList());
        assertSame(notice, screen.supportNoticeForTest(), "opening a link keeps the notice open");
        assertFalse(screen.isGameSelected());

        press(screen, GLFW_KEY_DOWN);
        assertEquals(GameSupportNoticeDialog.Focus.CONTINUE, notice.focus(), "the controls row lands on Continue");
        press(screen, GLFW_KEY_ENTER);
        assertTrue(screen.isGameSelected());
        assertEquals(2, opened.size());
    }

    @Test void aMissingBrowserOpenerSignalsAnErrorAndKeepsTheNoticeOpen() {
        var config = config();
        List<MasterTitleScreen.AudioCue> cues = new ArrayList<>();
        var screen = prepared(new MasterTitleScreen(config, new LaunchProfileStore(config), cues::add));
        screen.setSupportLinkLauncherForTest(command -> { throw new IOException("no opener"); });
        selectS3k(screen);
        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_UP);
        cues.clear();
        press(screen, GLFW_KEY_ENTER);
        assertEquals(List.of(MasterTitleScreen.AudioCue.ERROR), cues);
        assertNotNull(screen.supportNoticeForTest(), "the address stays readable on screen");
    }

    @Test void theNoticeComesBeforeEndingASuspendedGame() {
        var screen = screen(config());
        var ended = new java.util.concurrent.atomic.AtomicInteger();
        screen.setSuspendedGame(new MasterTitleEntry.Suspended(
                new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2), "Emerald Hill, Act 1", true), 2, 2, new byte[16]);
        screen.setSuspendedGameEndHandler(ended::incrementAndGet);
        selectS3k(screen);

        press(screen, GLFW_KEY_ENTER);
        assertNotNull(screen.supportNoticeForTest(), "the notice opens before the end-game question");
        assertFalse(screen.isEndPromptOpenForTest());
        press(screen, GLFW_KEY_ESCAPE);
        assertTrue(screen.hasSuspendedGame(), "backing out of the notice keeps the suspended game");
        assertEquals(0, ended.get());

        press(screen, GLFW_KEY_ENTER);
        press(screen, GLFW_KEY_ENTER); // Continue
        assertNull(screen.supportNoticeForTest());
        assertTrue(screen.isEndPromptOpenForTest(), "Continue then asks before ending the suspended game");
        assertFalse(screen.isGameSelected());
        press(screen, GLFW_KEY_DOWN);
        press(screen, GLFW_KEY_ENTER);
        assertEquals(1, ended.get());
        assertTrue(screen.isGameSelected());
        assertEquals("s3k", screen.getSelectedGameId());
    }

    @Test void ungatedGamesStartDirectlyAndTheGateListIsReplaceable() {
        var screen = screen(config());
        assertEquals("s2", screen.getSelectedGameId());
        press(screen, GLFW_KEY_ENTER);
        assertNull(screen.supportNoticeForTest());
        assertTrue(screen.isGameSelected());

        var config = config();
        var gated = screen(config);
        gated.setSupportNoticesForTest(new GameSupportNotices(config, Map.of("s2", GameSupportNotices.Stage.BETA)));
        press(gated, GLFW_KEY_ENTER);
        assertEquals(GameSupportNotices.Stage.BETA, gated.supportNoticeForTest().stage());
    }

    @Test void theNoticeNamesTheGameAndStageInEveryParagraph() {
        var dialog = new GameSupportNoticeDialog("Sonic 3 & Knuckles", GameSupportNotices.Stage.ALPHA);
        String body = dialog.bodyText();
        assertTrue(body.contains("Sonic 3 & Knuckles support is currently in alpha."), body);
        assertTrue(body.contains("Discord") && body.contains("GitHub"), body);
        var layout = dialog.layout();
        assertTrue(layout.y() >= 0 && layout.y() + layout.h() <= MenuChrome.FOOTER_Y, "the panel clears the footer");
        assertEquals(1, layout.pages(), "English fits on one page at 320 px");
        assertEquals(List.of("openggf.com/discord", "openggf.com/report-issue"),
                GameSupportNoticeDialog.LINKS.stream().map(GameSupportNoticeDialog.Link::address).toList());
        int linksBottom = layout.bodyY();
        for (GameSupportNoticeDialog.Link link : GameSupportNoticeDialog.LINKS) {
            MasterTitleScreen.Rect r = layout.bounds(link.focus());
            assertTrue(r.w() >= MenuChrome.ICON_SIZE + 4 + MenuChrome.compactWidth(link.address()),
                    link.address() + " is drawn whole beside its icon");
            assertTrue(r.y() >= linksBottom, link.address() + " sits below the body and the link above it");
            linksBottom = r.y() + r.h();
        }
        assertTrue(Math.min(layout.toggle().y(), layout.button().y()) >= linksBottom, "the controls sit below both links");
        for (GameSupportNoticeDialog.Focus focus : GameSupportNoticeDialog.Focus.values()) {
            MasterTitleScreen.Rect r = layout.bounds(focus);
            assertTrue(r.x() >= layout.x() && r.x() + r.w() <= layout.x() + layout.w()
                    && r.y() + r.h() <= layout.y() + layout.h(), focus + " sits inside the panel");
        }
    }

    @Test void everyLanguageKeepsTheWholeNoticeOnScreenAtNativeWidth() {
        assertTrue(UiMessages.languages().size() > 10, "every bundled catalog is checked");
        for (UiLanguage language : UiMessages.languages()) {
            UiMessages.selectLanguage(language.tag());
            var dialog = new GameSupportNoticeDialog("Sonic 3 & Knuckles", GameSupportNotices.Stage.ALPHA);
            var layout = dialog.layout();
            String where = language.tag();
            assertTrue(layout.y() >= 0 && layout.y() + layout.h() <= MenuChrome.FOOTER_Y, where + ": panel clears the footer");
            int lines = 0;
            for (int page = 0; page < layout.pages(); page++) lines += layout.page(page).size();
            assertTrue(lines > 0, where);
            for (GameSupportNoticeDialog.Focus focus : GameSupportNoticeDialog.Focus.values()) {
                MasterTitleScreen.Rect r = layout.bounds(focus);
                assertTrue(r.x() >= layout.x() && r.x() + r.w() <= layout.x() + layout.w()
                        && r.y() + r.h() <= layout.y() + layout.h(), where + ": " + focus + " sits inside the panel");
            }
        }
    }
}
