package com.openggf;

import com.openggf.i18n.UiMessages;
import com.openggf.ContentImportService.PreparedImport;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.editor.project.EditorProjectDocument;
import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.game.GameId;
import com.openggf.game.MenuDetailsScreen;
import com.openggf.game.MenuFeedback;
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
            report(new IllegalStateException(UiMessages.text("import.ui.an_installation_is_in_progress_wait_before_dropping_another_file")));
            return;
        }
        open();
        if (paths == null || paths.size() != 1 || paths.getFirst() == null) {
            report(new IllegalArgumentException(UiMessages.text("import.ui.drop_one_file_or_level_folder_at_a_time")));
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
        // A drop or Browse confirmation cannot also activate a just-prepared payload.
        if (publishInspection() || publishInstallation()) { MenuInput.consumeText(input); return true; }
        if (menu.isOpen()) { menu.update(input); return true; }
        if (details != null) {
            if (details.update(input)) details = null;
            return true;
        }
        MenuInput.consumeText(input);
        if (installing != null) {
            if (MenuInput.details(input)) details = new MenuDetailsScreen(UiMessages.text("import.ui.import_details"), fullDetails());
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
            details = new MenuDetailsScreen(UiMessages.text("import.ui.import_details"), fullDetails());
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
            int top = candidate == null ? 171 : ACTION_TOP + i * rowHeight();
            if (pointer.over(12, top, width - 24, rowHeight() - 1) && pointer.leftPressed()) {
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
            report(new IllegalStateException(UiMessages.text("import.installed_followup_error", java.util.Map.of("file", result.path().getFileName().toString(), "error", String.valueOf(failure.getMessage()))), failure));
        }
        return true;
    }

    private void browse() {
        menu.showForm(UiMessages.text("import.ui.browse_import_content"), List.of(new EditorProjectMenu.Field("path", UiMessages.text("import.ui.file_or_folder"),
                lastPath, MenuTextEditor.Mode.PATH)), UiMessages.text("import.ui.inspect_content"), values -> {
            String path = values.get("path");
            if (path.isBlank()) throw new IllegalArgumentException(UiMessages.text("import.ui.choose_a_file_or_level_folder"));
            drop(List.of(Path.of(path)));
        });
        menu.setSubtitle(UiMessages.text("import.ui.projects_level_json_level_zips_mod_jars"));
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
    }

    private void confirm() {
        if (candidate.kind() == ContentImportService.Kind.MOD && !host.allowedToInstallMods()) {
            report(new IllegalStateException(UiMessages.text("import.ui.install_mods_and_mutators_from_the_title_screen_this_editor_session_stays_open")));
            return;
        }
        if (candidate.kind() == ContentImportService.Kind.LEVEL && selectedGame == null) {
            menu.showActions(UiMessages.text("import.ui.choose_level_game"), List.of(gameAction("Sonic 1", GameId.S1),
                    gameAction("Sonic 2", GameId.S2), gameAction("Sonic 3 & Knuckles", GameId.S3K)));
            menu.setSubtitle(UiMessages.text("import.ui.choose_the_rom_host_for_this_level"));
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
        MenuStyle.page(font, this.width, UiMessages.text("import.ui.import_content"), UiMessages.text("import.ui.levels_projects_mods_and_mutators"));
        if (candidate != null) renderCandidate(font);
        else renderLanding(font);
        String status = error != null ? UiMessages.text("import.full_error", java.util.Map.of("key", detailsHint, "error", singleLine(error)))
                : installing != null ? UiMessages.text("import.ui.installing_disabled_please_wait")
                : pending != null ? UiMessages.text("import.inspecting_status", java.util.Map.of("back", back))
                : candidate != null ? UiMessages.text("import.ui.review_this_content_before_applying") : UiMessages.text("import.ui.drop_one_file_or_level_folder_onto_the_window");
        String controls = installing != null ? UiMessages.text("import.details_control", java.util.Map.of("key", detailsHint))
                : pending != null ? UiMessages.text("import.cancel_controls", java.util.Map.of("back", back, "details", detailsHint))
                : candidate != null ? UiMessages.text("import.apply_controls", java.util.Map.of("accept", accept, "directions", directions, "back", back))
                : UiMessages.text("import.browse_controls", java.util.Map.of("accept", accept, "back", back, "details", detailsHint));
        MenuStyle.footer(font, this.width, status, controls);
    }

    private void renderLanding(PixelFont font) {
        MenuStyle.panel(font, 12, 52, width - 24, 96);
        MenuStyle.fill(font, 12, 52, width - 24, 1, .22f, .47f, .67f, 1);
        MenuStyle.fill(font, 12, 147, width - 24, 1, .22f, .47f, .67f, 1);
        if (!busy()) {
            centered(font, UiMessages.text("import.ui.drop_a_level_project"), 65, 1, 1, 1);
            centered(font, UiMessages.text("import.ui.mod_or_mutator"), 79, 1, 1, 1);
            int center = width / 2;
            MenuStyle.fill(font, center - 2, 101, 4, 15, .4f, .88f, 1, 1);
            MenuStyle.fill(font, center - 5, 111, 10, 3, .4f, .88f, 1, 1);
            MenuStyle.fill(font, center - 8, 108, 16, 3, .4f, .88f, 1, 1);
            MenuStyle.fill(font, center - 13, 122, 26, 2, .4f, .88f, 1, 1);
            MenuStyle.fill(font, center - 13, 116, 2, 8, .4f, .88f, 1, 1);
            MenuStyle.fill(font, center + 11, 116, 2, 8, .4f, .88f, 1, 1);
            centered(font, UiMessages.text("import.ui.or_choose_a_file_below"), 134, .7f, .8f, .95f);
            MenuStyle.focus(font, 12, 171, width - 24, rowHeight() - 1);
            MenuStyle.label(font, UiMessages.text("import.ui.browse_files"), 20, 175, width - 40, 1, 1, 1);
        } else {
            centered(font, UiMessages.text("import.progress", java.util.Map.of("installing", installing == null ? "no" : "yes", "dots", ".".repeat(1 + ticks / 20 % 3))), 79, .4f, .88f, 1);
            centered(font, singleLine(lastPath), 107, .7f, .8f, .95f);
            centered(font, installing == null ? UiMessages.text("import.ui.checking_the_package_before_import")
                    : UiMessages.text("import.ui.keep_this_screen_open_while_it_finishes"), 133, .7f, .8f, .95f);
        }
        centered(font, ".ogfproject  level.json / ZIP  mod JAR", 155, .65f, .75f, .9f);
    }

    private void renderCandidate(PixelFont font) {
        MenuStyle.panel(font, 12, 49, width - 24, 81);
        MenuStyle.label(font, singleLine(candidate.displayName()), 20, 56, width - 40, 1, 1, 1);
        String kind = switch (candidate.kind()) {
            case PROJECT -> UiMessages.text("import.ui.editor_project_caps");
            case LEVEL -> UiMessages.text("import.ui.level_export_caps");
            case MOD -> UiMessages.text("import.ui.mod_mutator_package_caps");
        };
        if (selectedGame != null) kind += "  " + gameLabel(selectedGame);
        MenuStyle.text(font, kind, 20, 73, width - 40, .4f, .88f, 1);
        MenuStyle.text(font, UiMessages.text("import.action_summary", java.util.Map.of("action", actionLabel())), 20, 89, width - 40, 1, .8f, .3f);
        String detail = singleLine(candidate.detail());
        var detailLines = com.openggf.graphics.text.HostText.shared().wrap(detail, width - 40,
                com.openggf.graphics.text.TextFace.COMPACT, com.openggf.graphics.text.HostTextContext.current());
        int pitch = Math.max(12, com.openggf.game.MenuChrome.compactLineHeight() + 2);
        for (int line = 0; line < detailLines.size() && 105 + (line + 1) * pitch < ACTION_TOP - 2; line++)
            MenuStyle.text(font, detailLines.get(line), 20, 105 + line * pitch, width - 40, .7f, .8f, .95f);
        String[] actions = {actionLabel(), UiMessages.text("import.ui.browse_another_file"), UiMessages.text("import.ui.cancel")};
        for (int i = 0; i < actions.length; i++) {
            int y = ACTION_TOP + i * rowHeight();
            if (i == selected) MenuStyle.focus(font, 12, y, width - 24, rowHeight() - 1);
            MenuStyle.label(font, actions[i], 20, y + 4, width - 40, 1, 1, 1);
        }
    }

    private String actionLabel() {
        return candidate.kind() == ContentImportService.Kind.MOD ? UiMessages.text("import.ui.install_disabled")
                : candidate.kind() == ContentImportService.Kind.PROJECT ? UiMessages.text("import.ui.open_project_in_editor") : UiMessages.text("import.ui.import_level_into_editor");
    }

    private String fullDetails() {
        if (error != null) return error;
        if (installing != null) return UiMessages.text("import.installing_details", java.util.Map.of("source", lastPath));
        if (candidate != null) return UiMessages.text("import.candidate_details", java.util.Map.of("name", candidate.displayName(), "type", candidate.kind().name(), "source", candidate.source().toString(), "action", actionLabel(), "game", selectedGame == null ? "" : gameLabel(selectedGame), "detail", candidate.detail(), "findings", candidate.findings().stream().map(finding -> finding.message()).collect(java.util.stream.Collectors.joining("\n"))));
        return UiMessages.text("import.help");
    }

    private static int rowHeight() { return Math.max(ROW_HEIGHT, com.openggf.game.MenuChrome.labelLineHeight() + 4); }

    private void centered(PixelFont font, String text, int y, float r, float g, float b) {
        String fitted = MenuStyle.fit(text, width - 40);
        MenuStyle.text(font, fitted, (width - com.openggf.game.MenuChrome.compactWidth(fitted)) / 2, y, width - 40, r, g, b);
    }

    private static String singleLine(String text) { return MenuDetailsScreen.readable(text).replaceAll("\\s+", " ").trim(); }
    private static String gameLabel(GameId game) {
        return switch (game) {
            case S1 -> "Sonic 1";
            case S2 -> "Sonic 2";
            case S3K -> "Sonic 3&K";
            case STANDALONE -> UiMessages.text("import.ui.standalone");
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
