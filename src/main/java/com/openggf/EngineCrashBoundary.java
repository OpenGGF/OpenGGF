package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.crash.CrashIssueLink;
import com.openggf.game.crash.CrashReport;
import com.openggf.game.crash.CrashReportFile;
import com.openggf.game.crash.CrashScreen;
import com.openggf.game.crash.CrashScreenRenderer;
import com.openggf.util.ExternalBrowser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The window's owner of engine faults. A frame that throws outside a mod's fault boundary ends the
 * session: {@link #capture} records the report while the broken session still exists, the frame
 * boundary tears the session down to the master title, and the returned action opens the crash
 * page over it.
 *
 * <p>This is not containment. Nothing resumes the failed work, every fault is logged and saved in
 * full, and tests, headless tools and {@value #RETHROW_PROPERTY} keep the old behaviour of
 * rethrowing. A second fault while the page is open, or one in its own drawing, ends the process.
 */
final class EngineCrashBoundary implements AutoCloseable {
    /** Set to {@code true} to let engine faults end the process, as before the crash page existed. */
    static final String RETHROW_PROPERTY = "openggf.crash.rethrow";

    private final Supplier<Map<String, Supplier<String>>> sessionFacts;
    private final Path directory;
    private final CrashScreen.Host host;
    private final CrashScreenRenderer renderer = new CrashScreenRenderer();
    private CrashScreen screen;

    EngineCrashBoundary(Supplier<Map<String, Supplier<String>>> sessionFacts, Predicate<String> clipboard,
                        ExternalBrowser.Launcher browser, Path directory) {
        this.sessionFacts = Objects.requireNonNull(sessionFacts, "sessionFacts");
        this.directory = Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(clipboard, "clipboard");
        Objects.requireNonNull(browser, "browser");
        this.host = new CrashScreen.Host() {
            public boolean openIssue(String query) { return ExternalBrowser.open(CrashIssueLink.FORM, query, browser); }
            public boolean copy(String text) { return clipboard.test(text); }
            public void returnToGames() { screen = null; }
        };
    }

    /**
     * Records {@code failure} and returns the action that opens the page once the session is torn
     * down, or {@code null} when the failure must end the process instead.
     */
    Runnable capture(RuntimeException failure) {
        if (screen != null || Boolean.getBoolean(RETHROW_PROPERTY)) return null;
        CrashReport report = CrashReport.capture(failure, facts());
        return () -> screen = new CrashScreen(report, CrashReportFile.write(report, directory), host);
    }

    boolean isOpen() { return screen != null; }

    CrashScreen screen() { return screen; }

    /** Gives the page this frame's input; false when no page is open. */
    boolean update(InputHandler input) {
        if (screen == null) return false;
        screen.update(input);
        return true;
    }

    /** Draws the page; false when no page is open. */
    boolean render(float[] projection, int width) {
        if (screen == null) return false;
        renderer.render(screen, projection, width);
        return true;
    }

    /**
     * Each fact is read on its own: the session is broken by definition, so a fact that cannot be
     * read is reported as such instead of losing the report.
     */
    private List<CrashReport.Field> facts() {
        List<CrashReport.Field> fields = new ArrayList<>();
        sessionFacts.get().forEach((label, reader) -> fields.add(new CrashReport.Field(label, read(reader))));
        return fields;
    }

    private static String read(Supplier<String> reader) {
        try {
            return reader.get();
        } catch (RuntimeException unreadable) {
            return "unreadable (" + unreadable.getClass().getSimpleName() + ")";
        }
    }

    @Override
    public void close() { renderer.close(); }
}
