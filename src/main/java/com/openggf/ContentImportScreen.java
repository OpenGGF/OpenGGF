package com.openggf;

import com.openggf.ContentImportService.PreparedImport;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.editor.project.EditorProjectDocument;
import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.game.GameId;
import com.openggf.game.MenuDetailsScreen;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuStyle;
import com.openggf.game.MenuTextEditor;
import com.openggf.graphics.PixelFont;
import com.openggf.mods.code.ModLevelDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared import landing page. The host queues GLFW drops and calls this screen on
 * its update thread; bounded file inspection runs on an owned virtual task.
 */
public final class ContentImportScreen implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ContentImportScreen.class.getName());
    private static final int ACTION_TOP = 138;
    private static final int ROW_HEIGHT = 18;
    private static final int BROWSE_TOP = 165;

    /** The host retains its normal unsaved-project and mod activation boundaries. */
    public interface Host {
        void openProject(EditorProjectDocument candidate);
        void importLevel(ModLevelDefinition level, GameId game);
        void modInstalled(Path installedPath);
        default boolean allowedToInstallMods() { return true; }
        default void closed() { }
    }

    @FunctionalInterface interface Inspection { PreparedImport inspect(Path path) throws Exception; }
    @FunctionalInterface interface Installation { Path install(PreparedImport candidate) throws IOException; }

    private final Inspection inspection;
    private final Installation installation;
    private final Host host;
    private final Executor worker;
    private final EditorProjectMenu menu = new EditorProjectMenu();
    private PendingInspection pending;
    private PendingInstallation installing;
    private PreparedImport candidate;
    private GameId selectedGame;
    private MenuDetailsScreen details;
    private String error;
    private String lastPath = "";
    private int selected;
    private int width = 320;
    private int ticks;
    private boolean open;
    private String directions = "Arrows", accept = "Enter", back = "Esc", detailsHint = "F1";
    private boolean controller;

    public ContentImportScreen(ContentImportService service, Host host) {
        this(Objects.requireNonNull(service, "service")::inspect, service::installMod, host,
                command -> Thread.ofVirtual().name("content-import-worker").start(command));
    }

    ContentImportScreen(Inspection inspection, Installation installation, Host host, Executor worker) {
        this.inspection = Objects.requireNonNull(inspection, "inspection");
        this.installation = Objects.requireNonNull(installation, "installation");
        this.host = Objects.requireNonNull(host, "host");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    public void open() {
        if (installing != null) { open = true; return; }
        discardPrepared();
        menu.close(); details = null; error = null; selected = 0; ticks = 0; open = true;
    }

    public boolean isOpen() { return open; }
    public boolean busy() { return pending != null || installing != null; }
    public boolean installing() { return installing != null; }
    public boolean capturesText() { return open && menu.capturesText(); }
    public String error() { return error; }

    /** Update-thread entry point, including replacement drops while a candidate is open. */
    public void drop(List<Path> paths) {
        if (installing != null) {
            report(new IllegalStateException("An installation is in progress; wait before dropping another file"));
            return;
        }
        open();
        if (paths == null || paths.size() != 1 || paths.getFirst() == null) {
            report(new IllegalArgumentException("Drop one file or level folder at a time"));
            return;
        }
        Path source = paths.getFirst();
        lastPath = source.toString();
        var request = new PendingInspection(source, inspection);
        pending = request;
        try { worker.execute(request); }
        catch (RuntimeException failure) {
            request.cancel(); pending = null; report(failure);
        }
    }

    /** Owns every open frame, including the frame that closes or hands off this screen. */
    public boolean update(InputHandler input) {
        if (!open) return false;
        Objects.requireNonNull(input, "input");
        ticks++;
        directions = MenuInput.directionLabel(input); accept = MenuInput.confirmLabel(input);
        back = MenuInput.backLabel(input); detailsHint = MenuInput.detailsLabel(input);
        controller = MenuInput.controller(input);
        // A drop or Browse confirmation cannot also activate a just-prepared payload.
        if (publishInspection() || publishInstallation()) { MenuInput.consumeText(input); return true; }
        if (menu.isOpen()) { menu.update(input); return true; }
        if (details != null) {
            if (details.update(input)) details = null;
            return true;
        }
        MenuInput.consumeText(input);
        if (installing != null) {
            if (MenuInput.details(input)) details = new MenuDetailsScreen("Import details", fullDetails());
            return true;
        }
        if (MenuInput.back(input)) {
            if (pending != null || candidate != null) {
                discardPrepared(); error = null; selected = 0;
            } else close();
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            return true;
        }
        if (MenuInput.details(input)) {
            details = new MenuDetailsScreen("Import details", fullDetails());
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
            return true;
        }
        if (pending != null) return true;
        int previous = selected;
        int count = candidate == null ? 1 : 3;
        if (MenuInput.up(input)) selected = Math.floorMod(selected - 1, count);
        if (MenuInput.down(input)) selected = (selected + 1) % count;
        var pointer = MenuInput.pointer(input, width, 224);
        boolean clicked = false;
        for (int i = 0; i < count; i++) {
            int top = candidate == null ? BROWSE_TOP : ACTION_TOP + i * ROW_HEIGHT;
            if (pointer.over(12, top, width - 24, candidate == null ? 17 : ROW_HEIGHT - 3) && pointer.leftPressed()) {
                selected = i; clicked = true;
            }
        }
        if (previous != selected) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        if (MenuInput.accept(input) || clicked) {
            if (candidate == null || selected == 1) browse();
            else if (selected == 2) { close(); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); }
            else confirm();
        }
        return true;
    }

    private boolean publishInspection() {
        if (pending == null) return false;
        Finished result = pending.take();
        if (result == null) return false;
        pending = null;
        if (result.failure() != null) report(result.failure());
        else {
            candidate = result.candidate();
            selectedGame = candidate.suggestedGame().orElse(null);
            selected = 0;
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        }
        return true;
    }

    private boolean publishInstallation() {
        if (installing == null) return false;
        Installed result = installing.take();
        if (result == null) return false;
        installing = null;
        if (result.failure() != null) { report(result.failure()); return true; }
        open = false;
        try { host.modInstalled(result.path()); host.closed(); MenuFeedback.emit(MenuFeedback.Cue.CONFIRM); }
        catch (RuntimeException failure) {
            open = true;
            report(new IllegalStateException("Installed " + result.path().getFileName()
                    + " disabled, but " + failure.getMessage(), failure));
        }
        return true;
    }

    private void browse() {
        menu.showForm("Browse import content", List.of(new EditorProjectMenu.Field("path", "File or folder",
                lastPath, MenuTextEditor.Mode.PATH)), "Inspect content", values -> {
            String path = values.get("path");
            if (path.isBlank()) throw new IllegalArgumentException("Choose a file or level folder");
            drop(List.of(Path.of(path)));
        });
        menu.setSubtitle("Projects, level.json, level ZIPs, mod JARs");
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
    }

    private void confirm() {
        if (candidate.kind() == ContentImportService.Kind.MOD && !host.allowedToInstallMods()) {
            report(new IllegalStateException("Install mods and mutators from the title screen; this editor session stays open"));
            return;
        }
        if (candidate.kind() == ContentImportService.Kind.LEVEL && selectedGame == null) {
            menu.showActions("Choose level game", List.of(gameAction("Sonic 1", GameId.S1),
                    gameAction("Sonic 2", GameId.S2), gameAction("Sonic 3 & Knuckles", GameId.S3K)));
            menu.setSubtitle("Choose the ROM host for this level");
            return;
        }
        PreparedImport accepted = candidate;
        candidate = null;
        if (accepted.kind() == ContentImportService.Kind.MOD) {
            var request = new PendingInstallation(accepted, installation);
            installing = request;
            error = null;
            try { worker.execute(request); }
            catch (RuntimeException failure) {
                installing = null; closeLease(accepted); report(failure);
            }
            return;
        }
        open = false;
        try {
            switch (accepted.kind()) {
                case PROJECT -> host.openProject(accepted.project());
                case LEVEL -> host.importLevel(accepted.level(), selectedGame);
                case MOD -> throw new IllegalStateException("Mod installation was not scheduled");
            }
            host.closed();
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        } catch (RuntimeException failure) {
            open = true; selected = 0; report(failure);
        } finally { closeLease(accepted); }
    }

    private EditorProjectMenu.Action gameAction(String label, GameId game) {
        return new EditorProjectMenu.Action(label, () -> { selectedGame = game; confirm(); });
    }

    public void report(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        MenuFeedback.emit(MenuFeedback.Cue.ERROR);
    }

    @Override public void close() {
        boolean wasOpen = open;
        open = false; menu.close(); details = null; discardPrepared();
        if (installing != null) { installing.detach(); installing = null; }
        if (wasOpen) host.closed();
    }

    private void discardPrepared() {
        if (pending != null) { pending.cancel(); pending = null; }
        PreparedImport old = candidate; candidate = null; selectedGame = null;
        if (old != null) closeLease(old);
    }

    private static void closeLease(PreparedImport prepared) {
        try { prepared.close(); }
        catch (IOException | RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Unable to remove prepared import resources", failure);
        }
    }

    public void render(PixelFont font, int width) {
        if (!open || font == null) return;
        this.width = Math.max(320, width);
        if (menu.isOpen()) { menu.render(font, this.width); return; }
        if (details != null) { details.render(font, this.width); return; }
        MenuChrome.page(font, this.width, "IMPORT CONTENT", null, "Levels, projects, mods and mutators");
        if (candidate != null) renderCandidate(font);
        else renderLanding(font);
        String status = error != null ? "Error: " + singleLine(error)
                : installing != null ? "Installing disabled; please wait"
                : pending != null ? "Inspecting content"
                : candidate != null ? "Review this content before applying" : "Drop one file or level folder onto the window";
        if (error != null) MenuChrome.icon(font, MenuChrome.Icon.WARNING, 9, 193, MenuChrome.RED);
        MenuChrome.compact(font, MenuChrome.fitCompact(status, this.width - 30), error != null ? 20 : 9, 193,
                error != null ? MenuChrome.RED : MenuChrome.MUTED);
        String vertical = controller ? directions : MenuChrome.ARROWS_UD;
        List<MenuChrome.Hint> hints = installing != null ? List.of(MenuChrome.hint(detailsHint, "DETAILS"))
                : pending != null ? List.of(MenuChrome.hint(detailsHint, "DETAILS"), MenuChrome.hint(back, "CANCEL"))
                : candidate != null ? List.of(MenuChrome.hint(vertical, "SELECT"), MenuChrome.hint(accept, "APPLY"),
                        MenuChrome.hint(back, "BACK"))
                : List.of(MenuChrome.hint(detailsHint, "HELP"), MenuChrome.hint(accept, "BROWSE"), MenuChrome.hint(back, "CLOSE"));
        MenuChrome.footer(font, this.width, null, hints, controller);
    }

    private void renderLanding(PixelFont font) {
        MenuChrome.fill(font, 12, 50, width - 24, 98, MenuChrome.NAVY, .96f);
        // A dashed drop zone, so the page reads as a target before any text.
        for (int x = 12; x < width - 12; x += 8) {
            MenuChrome.fill(font, x, 50, Math.min(5, width - 12 - x), 1, MenuChrome.EDGE);
            MenuChrome.fill(font, x, 147, Math.min(5, width - 12 - x), 1, MenuChrome.EDGE);
        }
        for (int y = 50; y < 148; y += 8) {
            MenuChrome.fill(font, 12, y, 1, Math.min(5, 148 - y), MenuChrome.EDGE);
            MenuChrome.fill(font, width - 13, y, 1, Math.min(5, 148 - y), MenuChrome.EDGE);
        }
        if (!busy()) {
            centered(font, "Drop a level, project,", 63, MenuChrome.WHITE);
            centered(font, "mod or mutator here", 75, MenuChrome.WHITE);
            int center = width / 2;
            MenuChrome.fill(font, center - 2, 95, 4, 15, MenuChrome.CYAN);
            MenuChrome.fill(font, center - 5, 105, 10, 3, MenuChrome.CYAN);
            MenuChrome.fill(font, center - 8, 102, 16, 3, MenuChrome.CYAN);
            MenuChrome.fill(font, center - 13, 116, 26, 2, MenuChrome.CYAN);
            MenuChrome.fill(font, center - 13, 110, 2, 8, MenuChrome.CYAN);
            MenuChrome.fill(font, center + 11, 110, 2, 8, MenuChrome.CYAN);
            String[] kinds = {".OGFPROJECT", "LEVEL.JSON / ZIP", "MOD JAR"};
            int total = -6;
            for (String kind : kinds) total += MenuChrome.tagWidth(kind) + 6;
            int x = (width - total) / 2;
            for (String kind : kinds) x += MenuChrome.tag(font, x, 130, kind, MenuChrome.MUTED) + 6;
            MenuChrome.button(font, 12, BROWSE_TOP, width - 24, 17, "Browse files", true, true, ticks);
        } else {
            centered(font, (installing == null ? "Inspecting" : "Installing")
                    + ".".repeat(1 + ticks / 20 % 3), 79, MenuChrome.CYAN);
            centered(font, singleLine(lastPath), 103, MenuChrome.TEXT);
            centered(font, installing == null ? "Checking the package before import"
                    : "Keep this screen open while it finishes", 121, MenuChrome.MUTED);
        }
    }

    private void renderCandidate(PixelFont font) {
        MenuChrome.descriptionBox(font, 12, 49, width - 24, 82, List.of(), List.of());
        MenuChrome.label(font, MenuChrome.fitLabel(singleLine(candidate.displayName()), width - 40), 20, 55, MenuChrome.WHITE);
        String kind = switch (candidate.kind()) {
            case PROJECT -> "EDITOR PROJECT";
            case LEVEL -> "LEVEL EXPORT";
            case MOD -> "MOD / MUTATOR";
        };
        int x = 20 + MenuChrome.tag(font, 20, 69, kind, MenuChrome.CYAN) + 4;
        if (selectedGame != null) MenuChrome.tag(font, x, 69, gameLabel(selectedGame).toUpperCase(java.util.Locale.ROOT),
                MenuChrome.MUTED);
        MenuChrome.compact(font, "Action: " + actionLabel(), 20, 85, MenuChrome.AMBER);
        String detail = singleLine(candidate.detail());
        int columns = Math.max(1, (width - 40) / 6);
        int end = Math.min(columns, detail.length());
        if (detail.length() > columns && detail.lastIndexOf(' ', columns) > 0) end = detail.lastIndexOf(' ', columns);
        MenuChrome.compact(font, detail.substring(0, end), 20, 101, MenuChrome.MUTED);
        if (end < detail.length()) MenuChrome.compact(font, MenuChrome.fitCompact(detail.substring(end).stripLeading(),
                width - 40), 20, 111, MenuChrome.MUTED);
        String[] actions = {actionLabel(), "Browse another file", "Cancel"};
        for (int i = 0; i < actions.length; i++) {
            MenuChrome.button(font, 12, ACTION_TOP + i * ROW_HEIGHT, width - 24, ROW_HEIGHT - 3, actions[i],
                    i == selected, i == 0, ticks);
        }
    }

    private String actionLabel() {
        return candidate.kind() == ContentImportService.Kind.MOD ? "Install disabled"
                : candidate.kind() == ContentImportService.Kind.PROJECT ? "Open project in editor" : "Import level into editor";
    }

    private String fullDetails() {
        if (error != null) return error;
        if (installing != null) return "Installing the confirmed mod package disabled. "
                + "Wait for installation to finish before choosing another file. "
                + "The Mods screen will open afterward so you can review enablement and code trust.\n\nSource: " + lastPath;
        if (candidate != null) return candidate.displayName() + "\nType: " + candidate.kind()
                + "\nSource: " + candidate.source() + "\nAction: " + actionLabel()
                + (selectedGame == null ? "" : "\nGame: " + gameLabel(selectedGame))
                + "\n\n" + candidate.detail() + "\n" + candidate.findings().stream()
                .map(finding -> finding.message()).reduce("", (a, b) -> a + "\n" + b);
        return "Drop one file or folder anywhere on the import screen, or choose Browse files. "
                + "The path field supports normal typing, a controller keyboard, and Up to browse paths.\n\n"
                + "Editable projects: .ogfproject\nLevels: a level folder, level.json, or a ZIP containing one level\n"
                + "Mods and mutators: mod JARs, or a ZIP containing one JAR\n\n"
                + "Review the name, type and action before confirming. Projects and levels open in the editor. "
                + "Mods install disabled; use Mods at the title screen to review, enable, trust code if required, and apply. "
                + "Inspection does not run creator code. Canceling keeps the current project and mod choices.";
    }

    private void centered(PixelFont font, String text, int y, float[] color) {
        String fitted = MenuChrome.fitCompact(text, width - 40);
        MenuChrome.compact(font, fitted, (width - MenuChrome.compactWidth(fitted)) / 2, y, color);
    }

    private static String singleLine(String text) { return MenuDetailsScreen.readable(text).replaceAll("\\s+", " ").trim(); }
    private static String gameLabel(GameId game) {
        return switch (game) {
            case S1 -> "Sonic 1";
            case S2 -> "Sonic 2";
            case S3K -> "Sonic 3&K";
            case STANDALONE -> "Standalone";
        };
    }

    private record Finished(PreparedImport candidate, Exception failure) { }
    private record Installed(Path path, Exception failure) { }

    /** A confirmed installation owns its lease until publication finishes; Back cannot interrupt a file move. */
    private static final class PendingInstallation implements Runnable {
        private final PreparedImport prepared;
        private final Installation installation;
        private boolean detached;
        private Installed result;

        PendingInstallation(PreparedImport prepared, Installation installation) {
            this.prepared = prepared; this.installation = installation;
        }

        @Override public void run() {
            Path installed = null;
            Exception failure = null;
            try { installed = installation.install(prepared); }
            catch (IOException | RuntimeException error) { failure = error; }
            finally { closeLease(prepared); }
            var completed = new Installed(installed, failure);
            synchronized (this) {
                if (!detached) { result = completed; return; }
            }
            logDetached(completed);
        }

        synchronized Installed take() { var completed = result; result = null; return completed; }

        void detach() {
            Installed completed;
            synchronized (this) { detached = true; completed = result; result = null; }
            if (completed != null) logDetached(completed);
        }

        private static void logDetached(Installed completed) {
            if (completed.failure() != null) LOGGER.log(Level.WARNING, "Content installation failed after screen cleanup", completed.failure());
            else LOGGER.info("Content installation completed disabled after screen cleanup: " + completed.path());
        }
    }

    /** Synchronization transfers the archive lease to exactly one owner, even after cancellation. */
    private static final class PendingInspection implements Runnable {
        private final Path source;
        private final Inspection inspection;
        private boolean canceled;
        private boolean finished;
        private PreparedImport candidate;
        private Exception failure;

        PendingInspection(Path source, Inspection inspection) { this.source = source; this.inspection = inspection; }

        @Override public void run() {
            synchronized (this) { if (canceled) return; }
            PreparedImport prepared = null;
            Exception failed = null;
            try { prepared = Objects.requireNonNull(inspection.inspect(source), "Prepared import"); }
            catch (Exception error) { failed = error; }
            synchronized (this) {
                if (!canceled) {
                    candidate = prepared; failure = failed; finished = true; return;
                }
            }
            if (prepared != null) closeLease(prepared);
        }

        synchronized Finished take() {
            if (!finished) return null;
            var result = new Finished(candidate, failure);
            candidate = null; failure = null; finished = false;
            return result;
        }

        void cancel() {
            PreparedImport abandoned;
            synchronized (this) { canceled = true; abandoned = candidate; candidate = null; }
            if (abandoned != null) closeLease(abandoned);
        }
    }
}
