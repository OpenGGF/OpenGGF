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
    private static int rowHeight() { return Math.max(ROW_HEIGHT, MenuChrome.compactLineHeight() + 8); }
    private static final int BROWSE_TOP = 165;
    private static int statusY() { return Math.min(193, MenuStyle.footerTop() - MenuChrome.compactLineHeight() - 3); }
    private static int actionTop() { return Math.min(ACTION_TOP, statusY() - 4 - 3 * rowHeight()); }
    private static int browseTop() { return Math.min(BROWSE_TOP, statusY() - rowHeight() - 4); }

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
    private String directions = UiMessages.text("menu.contentimportscreen.arrows"), accept = UiMessages.text("menu.contentimportscreen.enter"), back = UiMessages.text("menu.contentimportscreen.esc"), detailsHint = "F1";
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
            int top = candidate == null ? browseTop() : actionTop() + i * rowHeight();
            if (pointer.over(12, top, width - 24, candidate == null ? rowHeight() - 1 : rowHeight() - 3) && pointer.leftPressed()) {
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
            menu.showActions(UiMessages.text("import.ui.choose_level_game"), List.of(gameAction(UiMessages.text("editor.editor_project_workspace.sonic_1_1t0f5st"), GameId.S1),
                    gameAction(UiMessages.text("editor.editor_project_workspace.sonic_2_1t0f5su"), GameId.S2), gameAction(UiMessages.text("editor.editor_project_workspace.sonic_3_knuckles_138eayj"), GameId.S3K)));
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
                case MOD -> throw new IllegalStateException(UiMessages.text("menu.contentimportscreen.mod_installation_was_not_scheduled"));
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
            LOGGER.log(Level.WARNING, UiMessages.text("menu.contentimportscreen.unable_to_remove_prepared_import_resources"), failure);
        }
    }

    public void render(PixelFont font, int width) {
        if (!open || font == null) return;
        this.width = Math.max(320, width);
        if (menu.isOpen()) { menu.render(font, this.width); return; }
        if (details != null) { details.render(font, this.width); return; }
        MenuChrome.page(font, this.width, UiMessages.text("menu.contentimportscreen.import_content"), null,
                candidate != null && error == null && installing == null ? UiMessages.text("import.ui.review_this_content_before_applying")
                        : UiMessages.text("import.ui.levels_projects_mods_and_mutators"));
        if (candidate != null) renderCandidate(font);
        else renderLanding(font);
        String status = error != null ? UiMessages.text("menu.contentimportscreen.error") + singleLine(error)
                : installing != null ? UiMessages.text("import.ui.installing_disabled_please_wait")
                : pending != null ? UiMessages.text("menu.contentimportscreen.inspecting_content")
                : candidate != null ? UiMessages.text("menu.contentimportscreen.from") + singleLine(candidate.source().getFileName() == null
                        ? candidate.source().toString() : candidate.source().getFileName().toString())
                : UiMessages.text("import.ui.drop_one_file_or_level_folder_onto_the_window");
        if (error != null) MenuChrome.icon(font, MenuChrome.Icon.WARNING, 9, statusY(), MenuChrome.RED);
        MenuChrome.compact(font, MenuStyle.pageText(status, this.width - 30, MenuStyle.COMPACT, ticks), error != null ? 20 : 9, statusY(),
                error != null ? MenuChrome.RED : MenuChrome.MUTED);
        String vertical = controller ? directions : MenuChrome.ARROWS_UD;
        List<MenuChrome.Hint> hints = installing != null ? List.of(MenuChrome.hint(detailsHint, UiMessages.text("menu.path.details_caps")))
                : pending != null ? List.of(MenuChrome.hint(detailsHint, UiMessages.text("menu.path.details_caps")), MenuChrome.hint(back, UiMessages.text("menu.onboarding.cancel_caps")))
                : candidate != null ? List.of(MenuChrome.hint(detailsHint, UiMessages.text("menu.path.details_caps")), MenuChrome.hint(vertical, UiMessages.text("hub.ui.select_caps")), MenuChrome.hint(accept, UiMessages.text("editor.menu.editor_project_menu.apply")),
                        MenuChrome.hint(back, UiMessages.text("language.picker.back")))
                : List.of(MenuChrome.hint(detailsHint, UiMessages.text("hub.ui.help_caps")), MenuChrome.hint(accept, UiMessages.text("menu.contentimportscreen.browse")), MenuChrome.hint(back, UiMessages.text("editor.menu.editor_project_menu.close")));
        MenuChrome.footer(font, this.width, null, hints, controller, ticks);
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
            centered(font, UiMessages.text("import.ui.drop_a_level_project"), 63, MenuChrome.WHITE);
            centered(font, "mod or mutator here", 75, MenuChrome.WHITE);
            int center = width / 2;
            MenuChrome.fill(font, center - 2, 95, 4, 15, MenuChrome.CYAN);
            MenuChrome.fill(font, center - 5, 105, 10, 3, MenuChrome.CYAN);
            MenuChrome.fill(font, center - 8, 102, 16, 3, MenuChrome.CYAN);
            MenuChrome.fill(font, center - 13, 116, 26, 2, MenuChrome.CYAN);
            MenuChrome.fill(font, center - 13, 110, 2, 8, MenuChrome.CYAN);
            MenuChrome.fill(font, center + 11, 110, 2, 8, MenuChrome.CYAN);
            String[] kinds = {".OGFPROJECT", UiMessages.text("menu.contentimportscreen.level_json_zip"), UiMessages.text("menu.contentimportscreen.mod_jar")};
            int total = -6;
            for (String kind : kinds) total += MenuChrome.tagWidth(kind) + 6;
            int x = (width - total) / 2;
            for (String kind : kinds) x += MenuChrome.tag(font, x, 130, kind, MenuChrome.MUTED) + 6;
            MenuChrome.button(font, 12, browseTop(), width - 24, rowHeight() - 1, MenuStyle.pageText(UiMessages.text("import.ui.browse_files"), width - 40, MenuStyle.COMPACT, ticks), true, true, ticks);
        } else {
            centered(font, (installing == null ? UiMessages.text("menu.contentimportscreen.inspecting") : UiMessages.text("menu.contentimportscreen.installing"))
                    + ".".repeat(1 + ticks / 20 % 3), 79, MenuChrome.CYAN);
            centered(font, singleLine(lastPath), 103, MenuChrome.TEXT);
            centered(font, installing == null ? UiMessages.text("import.ui.checking_the_package_before_import")
                    : UiMessages.text("import.ui.keep_this_screen_open_while_it_finishes"), 121, MenuChrome.MUTED);
        }
    }

    private void renderCandidate(PixelFont font) {
        int bottom = actionTop() - 6;
        MenuChrome.descriptionBox(font, 12, 49, width - 24, Math.max(1, bottom - 49), List.of(), List.of());
        int y = 55;
        if (y + MenuChrome.labelLineHeight() <= bottom) {
            MenuChrome.label(font, MenuStyle.pageText(singleLine(candidate.displayName()), width - 40, 1f, ticks), 20, y, MenuChrome.WHITE);
            y += MenuChrome.labelLineHeight() + 4;
        }
        String kind = switch (candidate.kind()) {
            case PROJECT -> UiMessages.text("import.ui.editor_project_caps");
            case LEVEL -> UiMessages.text("import.ui.level_export_caps");
            case MOD -> UiMessages.text("menu.contentimportscreen.mod_mutator");
        };
        if (y + MenuChrome.compactLineHeight() + 4 <= bottom) {
            int x = 20 + MenuChrome.tag(font, 20, y, kind, MenuChrome.CYAN) + 4;
            if (selectedGame != null) MenuChrome.tag(font, x, y, gameLabel(selectedGame).toUpperCase(java.util.Locale.ROOT), MenuChrome.MUTED);
            y += MenuChrome.compactLineHeight() + 8;
        }
        if (!candidate.facts().isEmpty() && y + MenuChrome.compactLineHeight() <= bottom) {
            MenuChrome.compact(font, MenuStyle.pageText(singleLine(candidate.facts()), width - 40, MenuStyle.COMPACT, ticks), 20, y, MenuChrome.MUTED);
            y += MenuChrome.compactLineHeight() + 8;
        }
        for (String line : MenuStyle.wrap(singleLine(candidate.detail()), width - 40, MenuStyle.COMPACT)) {
            if (y + MenuChrome.compactLineHeight() > bottom) break;
            MenuChrome.compact(font, line, 20, y, MenuChrome.TEXT);
            y += Math.max(MenuChrome.DESCRIPTION_PITCH, MenuChrome.compactLineHeight());
        }
        String[] actions = {actionLabel(), UiMessages.text("import.ui.browse_another_file"), UiMessages.text("import.ui.cancel")};
        for (int i = 0; i < actions.length; i++) {
            MenuChrome.button(font, 12, actionTop() + i * rowHeight(), width - 24, rowHeight() - 3,
                    MenuStyle.pageText(actions[i], width - 48, MenuStyle.COMPACT, ticks), i == selected, i == 0, ticks);
        }
    }

    private String actionLabel() {
        return candidate.kind() == ContentImportService.Kind.MOD ? UiMessages.text("import.ui.install_disabled")
                : candidate.kind() == ContentImportService.Kind.PROJECT ? UiMessages.text("import.ui.open_project_in_editor") : UiMessages.text("import.ui.import_level_into_editor");
    }

    String fullDetails() {
        if (error != null) return error;
        if (pending != null) return UiMessages.text("menu.contentimportscreen.inspecting_content") + "\n\n"
                + UiMessages.text("menu.contentimportscreen.from") + lastPath;
        if (installing != null) return UiMessages.text("menu.contentimportscreen.installing_the_confirmed_mod_package_disabled")
                + UiMessages.text("menu.contentimportscreen.wait_for_installation_to_finish_before_choosing_another")
                + "The Mods screen will open afterward so you can review enablement and code trust.\n\nSource: " + lastPath;
        if (candidate != null) return candidate.displayName() + "\nType: " + candidate.kind()
                + "\nSource: " + candidate.source() + "\nAction: " + actionLabel()
                + (candidate.facts().isEmpty() ? "" : "\nIdentity: " + candidate.facts())
                + (selectedGame == null ? "" : "\nGame: " + gameLabel(selectedGame))
                + "\n\n" + candidate.detail() + "\n" + candidate.findings().stream()
                .map(finding -> finding.message()).reduce("", (a, b) -> a + "\n" + b);
        return UiMessages.text("menu.contentimportscreen.drop_one_file_or_folder_anywhere_on_the_import_screen_o")
                + "The path field supports normal typing, a controller keyboard, and Up to browse paths.\n\n"
                + "Editable projects: .ogfproject\nLevels: a level folder, level.json, or a ZIP containing one level\n"
                + "Mods and mutators: mod JARs, or a ZIP containing one JAR\n\n"
                + UiMessages.text("menu.contentimportscreen.review_the_name_type_and_action_before_confirming_proje")
                + "Mods install disabled; use Mods at the title screen to review, enable, trust code if required, and apply. "
                + UiMessages.text("menu.contentimportscreen.inspection_does_not_run_creator_code_canceling_keeps_th");
    }

    private void centered(PixelFont font, String text, int y, float[] color) {
        String fitted = MenuStyle.pageText(text, width - 40, MenuStyle.COMPACT, ticks);
        MenuChrome.compact(font, fitted, (width - MenuChrome.compactWidth(fitted)) / 2, y, color);
    }

    private static String singleLine(String text) { return MenuDetailsScreen.readable(text).replaceAll("\\s+", " ").trim(); }
    private static String gameLabel(GameId game) {
        return switch (game) {
            case S1 -> UiMessages.text("editor.editor_project_workspace.sonic_1_1t0f5st");
            case S2 -> UiMessages.text("editor.editor_project_workspace.sonic_2_1t0f5su");
            case S3K -> UiMessages.text("launch.value.sonic_3_k");
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
            if (completed.failure() != null) LOGGER.log(Level.WARNING, UiMessages.text("menu.contentimportscreen.content_installation_failed_after_screen_cleanup"), completed.failure());
            else LOGGER.info(UiMessages.text("menu.contentimportscreen.content_installation_completed_disabled_after_screen_cl") + completed.path());
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
            try { prepared = Objects.requireNonNull(inspection.inspect(source), UiMessages.text("menu.contentimportscreen.prepared_import")); }
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
