package com.openggf.game.crash;

import com.openggf.i18n.UiMessages;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestCrashScreen {
    private static final class Host implements CrashScreen.Host {
        boolean browser = true;
        boolean clipboard = true;
        final List<String> opened = new ArrayList<>();
        final List<String> copied = new ArrayList<>();
        int returns;

        public boolean openIssue(String query) { opened.add(query); return browser; }
        public boolean copy(String text) { copied.add(text); return clipboard; }
        public void returnToGames() { returns++; }
    }

    private static CrashScreen screen(Host host, Optional<Path> file) {
        var failure = new IllegalStateException("no keyframe");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.openggf.game.rewind.RewindController", "seekTo", "RewindController.java", 410)});
        var report = new CrashReport(FaultKind.classify(failure), failure, Instant.EPOCH,
                List.of(new CrashReport.Field("Version", "test")), List.of(new CrashReport.Field("Game", "s3k")), "");
        return new CrashScreen(report, file, host);
    }

    @Test
    void reportOpensTheFilledFormAndNamesTheSavedFile() {
        var host = new Host();
        var screen = screen(host, Optional.of(Path.of("/games/crash-reports/crash-1.txt")));
        assertEquals(CrashScreen.Choice.REPORT, screen.selected());
        screen.run(CrashScreen.Choice.REPORT);
        assertEquals(1, host.opened.size());
        assertTrue(host.opened.getFirst().contains("template=crash.yml"));
        assertTrue(host.opened.getFirst().contains("report-file=crash-1.txt"));
        assertEquals(UiMessages.text("crash.status.opened"), screen.status());
    }

    @Test
    void withoutABrowserTheFormLinkGoesOnTheClipboard() {
        var host = new Host();
        host.browser = false;
        var screen = screen(host, Optional.empty());
        screen.run(CrashScreen.Choice.REPORT);
        assertTrue(host.copied.getFirst().startsWith("https://" + CrashIssueLink.FORM + "?template=crash.yml"));
        assertEquals(UiMessages.text("crash.status.link_copied"), screen.status());
        host.clipboard = false;
        screen.run(CrashScreen.Choice.REPORT);
        assertEquals(UiMessages.text("crash.status.unavailable"), screen.status());
    }

    @Test
    void copyPutsTheWholeReportOnTheClipboard() {
        var host = new Host();
        var screen = screen(host, Optional.empty());
        screen.run(CrashScreen.Choice.COPY);
        assertEquals(screen.report().fullText(), host.copied.getFirst());
        assertEquals(UiMessages.text("crash.status.copied"), screen.status());
        host.clipboard = false;
        screen.run(CrashScreen.Choice.COPY);
        assertEquals(UiMessages.text("crash.status.copy_failed"), screen.status());
    }

    @Test
    void detailsListEveryFactAndTheFullTrace() {
        var screen = screen(new Host(), Optional.empty());
        var content = screen.detailsContent();
        assertEquals(UiMessages.text("crash.headline.rewind"), content.result());
        assertEquals(UiMessages.text("crash.meaning_unsaved"), content.meaning());
        var labels = content.sections().stream().flatMap(s -> s.rows().stream()).map(r -> r.label()).toList();
        assertTrue(labels.containsAll(List.of("Game", "Version", UiMessages.text("crash.field.error"))), labels.toString());
        assertTrue(content.more().contains("RewindController.seekTo(RewindController.java:410)"));
        screen.run(CrashScreen.Choice.DETAILS);
        assertTrue(screen.detailsOpen());
    }

    @Test
    void returnToGamesHandsBackToTheHost() {
        var host = new Host();
        screen(host, Optional.empty()).run(CrashScreen.Choice.RETURN);
        assertEquals(1, host.returns);
    }

    @Test
    void buttonsStayInsideTheNarrowestPageAboveTheFooter() {
        var screen = screen(new Host(), Optional.empty());
        assertEquals(CrashScreen.Choice.values().length, screen.buttons().size());
        for (var button : screen.buttons()) {
            assertTrue(button.x() >= screen.artBox().x() + screen.artBox().width() + 8
                    && button.x() + button.width() <= 312, button.toString());
            assertTrue(button.y() + 12 <= com.openggf.game.MenuChrome.FOOTER_Y, button.toString());
        }
    }
}
