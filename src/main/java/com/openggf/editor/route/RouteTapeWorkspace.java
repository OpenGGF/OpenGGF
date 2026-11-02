package com.openggf.editor.route;

import com.openggf.game.MenuTextEditor;
import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.i18n.UiMessages;
import com.openggf.route.Inlet;
import com.openggf.route.InputTape;
import com.openggf.route.LaunchContract;
import com.openggf.route.LevelId;
import com.openggf.route.NativeFacts;
import com.openggf.route.PadRow;
import com.openggf.route.QualificationStamp;
import com.openggf.route.ReplayScope;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteFiles;
import com.openggf.route.RouteManifest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared route tape authoring: one open route, its working rows, row-edit history and the last
 * replay of the working content. Both editors present this model; neither keeps a second route
 * model. Jobs that boot a world run in an isolated worker through the host; this class only reads
 * and writes route files through the {@link RouteFiles} facade and edits immutable tapes.
 */
public final class RouteTapeWorkspace {
    /** Engine services the workspace needs; defaults describe a host without route support. */
    public interface Host {
        default Path initialDirectory() {
            return Path.of("src/test/resources/routes").toAbsolutePath().normalize();
        }

        default void startJob(RouteTapeJob.Request request, String content, int rows) throws IOException {
            throw new IOException(UiMessages.text("editor.route_tape.jobs_unavailable"));
        }

        default RouteTapeJob.Update pollJob() {
            return null;
        }

        default void cancelJob() {
        }

        /** The level the editor shows, if one is loaded. */
        default Optional<LevelId> currentLevel() {
            return Optional.empty();
        }

        /** Centres the editor's level view on a world point. */
        default void focusWorld(int x, int y) {
        }

        default void finishPendingEdits() {
        }
    }

    public enum Action {
        OPEN, SAVE, REPLAY, CANCEL_JOB, EXPORT_BK2, UNDO, REDO, TOGGLE, MARK, ROWS, INSERT_ROWS, DELETE_ROWS, DUPLICATE_ROWS, CLEAR_LANE, GO_TO_ROW, NEXT_FACT, MENU, EXIT;

        public String label() {
            return switch (this) {
                case OPEN -> UiMessages.text("editor.route_tape.action_open");
                case SAVE -> UiMessages.text("editor.route_tape.action_save");
                case REPLAY -> UiMessages.text("editor.route_tape.action_replay");
                case CANCEL_JOB -> UiMessages.text("editor.route_tape.action_cancel_job");
                case EXPORT_BK2 -> UiMessages.text("editor.route_tape.action_export_bk2");
                case UNDO -> UiMessages.text("editor.route_tape.action_undo");
                case REDO -> UiMessages.text("editor.route_tape.action_redo");
                case TOGGLE -> UiMessages.text("editor.route_tape.action_toggle");
                case MARK -> UiMessages.text("editor.route_tape.action_mark");
                case ROWS -> UiMessages.text("editor.route_tape.action_rows");
                case INSERT_ROWS -> UiMessages.text("editor.route_tape.action_insert_rows");
                case DELETE_ROWS -> UiMessages.text("editor.route_tape.action_delete_rows");
                case DUPLICATE_ROWS -> UiMessages.text("editor.route_tape.action_duplicate_rows");
                case CLEAR_LANE -> UiMessages.text("editor.route_tape.action_clear_lane");
                case GO_TO_ROW -> UiMessages.text("editor.route_tape.action_go_to_row");
                case NEXT_FACT -> UiMessages.text("editor.route_tape.action_next_fact");
                case MENU -> UiMessages.text("editor.route_tape.action_menu");
                case EXIT -> UiMessages.text("editor.route_tape.action_exit");
            };
        }
    }

    /** What the editor can truthfully say about the working rows. */
    public enum Qualification {
        /** No route is open. */
        EMPTY,
        /** A passing engine stamp in the file matches the working content. */
        QUALIFIED,
        /** A replay of the working content passed in this session; it is not saved. */
        PASSES_UNSAVED,
        /** A replay of the working content stopped before its last row. */
        NOT_QUALIFIED,
        /** The worker cannot replay this launch here (ROM missing, unsupported inlet). */
        UNSUPPORTED,
        /** The rows changed since the file's stamps; they have not been replayed. */
        EDITED,
        /** No engine stamp covers the working content. */
        UNCHECKED
    }

    private static final int UNDO_LIMIT = 200;

    private final Host host;
    private final EditorProjectMenu menu = new EditorProjectMenu();
    private final Deque<InputTape> undo = new ArrayDeque<>();
    private final Deque<InputTape> redo = new ArrayDeque<>();
    private Path manifest;
    private RouteArtifact loaded;
    private InputTape tape = InputTape.empty();
    private RouteTapeJob.Update job;
    private RouteTapeJob.Update result;
    private boolean active;
    private int cursor;
    private int track;
    private int mark = -1;
    private int first;
    private int visibleRows = 120;
    private String status = UiMessages.text("editor.route_tape.status_open_a_route");

    public RouteTapeWorkspace(Host host) {
        this.host = Objects.requireNonNull(host, "host");
        // Route forms need no extra presentation; registering a presenter publishes their
        // structured form and action views, so a desktop front end draws them natively.
        menu.onShow(shown -> { });
    }

    public EditorProjectMenu menu() {
        return menu;
    }

    public boolean active() {
        return active;
    }

    public void activate() {
        active = true;
    }

    /** Workspace changes keep the open route and its unsaved rows. */
    public void deactivate() {
        menu.close();
        active = false;
    }

    public String status() {
        return status;
    }

    public void setStatus(String value) {
        status = Objects.requireNonNull(value, "value");
    }

    // ---------------------------------------------------------------- route state

    public boolean hasRoute() {
        return loaded != null;
    }

    public Path manifestPath() {
        return manifest;
    }

    public RouteArtifact loaded() {
        return loaded;
    }

    public InputTape tape() {
        return tape;
    }

    public boolean dirty() {
        return loaded != null && !tape.equals(loaded.tape());
    }

    /** The route as it would be saved: the file's launch, provenance and facts over the working rows. */
    public RouteArtifact working() {
        if (loaded == null) return null;
        RouteManifest source = loaded.manifest();
        return new RouteArtifact(new RouteManifest(source.launch(), tape.hash(), tape.size(), source.provenance(),
                source.qualification(), source.nativeFacts()), tape);
    }

    public String workingContent() {
        RouteArtifact working = working();
        return working == null ? null : working.content().toString();
    }

    public RouteTapeJob.Update job() {
        return job;
    }

    public boolean jobRunning() {
        return job != null && job.running();
    }

    /** The last completed job for the working content, or null when the rows changed since. */
    public RouteTapeJob.Update result() {
        return result != null && Objects.equals(result.content(), workingContent()) ? result : null;
    }

    /** The last observed path, which may describe earlier content; see {@link #pathStale()}. */
    public RouteTapeOverlay overlay() {
        return result == null ? null : result.path();
    }

    public boolean pathStale() {
        return result != null && !Objects.equals(result.content(), workingContent());
    }

    public Qualification qualification() {
        if (loaded == null) return Qualification.EMPTY;
        RouteTapeJob.Update current = result();
        boolean stamped = engineStamp().map(QualificationStamp::passed).orElse(false);
        if (current != null) {
            return switch (current.phase()) {
                case PASSED -> stamped ? Qualification.QUALIFIED : Qualification.PASSES_UNSAVED;
                case FAILED -> Qualification.NOT_QUALIFIED;
                case UNSUPPORTED -> Qualification.UNSUPPORTED;
                default -> dirty() ? Qualification.EDITED : stamped ? Qualification.QUALIFIED : Qualification.UNCHECKED;
            };
        }
        if (stamped) return Qualification.QUALIFIED;
        return dirty() ? Qualification.EDITED : Qualification.UNCHECKED;
    }

    /** The first row the last replay of the working content could not complete, or -1. */
    public int divergedRow() {
        RouteTapeJob.Update current = result();
        return current != null && current.phase() == RouteTapeJob.Phase.FAILED ? current.row() : -1;
    }

    public String qualificationText() {
        return switch (qualification()) {
            case EMPTY -> UiMessages.text("editor.route_tape.qualification_empty");
            case QUALIFIED -> UiMessages.text("editor.route_tape.qualification_qualified");
            case PASSES_UNSAVED -> UiMessages.text("editor.route_tape.qualification_passes_unsaved");
            case NOT_QUALIFIED -> UiMessages.text("editor.route_tape.qualification_not_qualified",
                    Map.of("row", String.valueOf(divergedRow())));
            case UNSUPPORTED -> UiMessages.text("editor.route_tape.qualification_unsupported");
            case EDITED -> UiMessages.text("editor.route_tape.qualification_edited");
            case UNCHECKED -> UiMessages.text("editor.route_tape.qualification_unchecked");
        };
    }

    private Optional<QualificationStamp> engineStamp() {
        if (loaded == null) return Optional.empty();
        var content = working().content();
        return loaded.manifest().qualification().stream()
                .filter(stamp -> stamp.scope() == ReplayScope.ENGINE && stamp.content().equals(content)).findFirst();
    }

    /** Launch facts as label/value pairs, in manifest words. */
    public List<String[]> launchFacts() {
        if (loaded == null) return List.of();
        LaunchContract launch = loaded.launch();
        var facts = new ArrayList<String[]>();
        facts.add(fact(UiMessages.text("editor.route_tape.fact_rom"), launch.rom().game().toUpperCase(java.util.Locale.ROOT) + " "
                + launch.rom().crc32Hex()));
        if (launch.inlet() instanceof Inlet.ColdLevel cold) {
            facts.add(fact(UiMessages.text("editor.route_tape.fact_level"), levelText(cold.level())));
        }
        facts.add(fact(UiMessages.text("editor.route_tape.fact_roster"), launch.roster().solo() ? launch.roster().main()
                : launch.roster().main() + " + " + launch.roster().sidekick()));
        facts.add(fact(UiMessages.text("editor.route_tape.fact_width"), String.valueOf(launch.width())));
        if (!launch.donor().equals("off")) facts.add(fact(UiMessages.text("editor.route_tape.fact_donor"), launch.donor()));
        facts.add(fact(UiMessages.text("editor.route_tape.fact_vint"), String.valueOf(launch.initial().vIntStart())));
        if (launch.initial().seeds().positioned()) {
            facts.add(fact(UiMessages.text("editor.route_tape.fact_start"), launch.initial().seeds().startX() + ", "
                    + launch.initial().seeds().startY()));
        }
        return List.copyOf(facts);
    }

    /**
     * Native observations stored in the manifest. Display only: they are comparison facts from a
     * native run and are never fed to the engine, a replay or a plan.
     */
    public List<String[]> nativeFacts() {
        if (loaded == null) return List.of();
        NativeFacts facts = loaded.manifest().nativeFacts();
        var content = working().content();
        var lines = new ArrayList<String[]>();
        lines.add(fact(UiMessages.text("editor.route_tape.fact_native_entry"), facts.entry()
                .map(entry -> UiMessages.text("editor.route_tape.native_entry_value",
                        Map.of("movie", entry.movie(), "rows", String.valueOf(entry.prefixRows()))))
                .orElse(UiMessages.text("editor.route_tape.native_entry_none"))));
        long matching = facts.lag().stream().filter(lag -> lag.content().equals(content)).count();
        lines.add(fact(UiMessages.text("editor.route_tape.fact_native_lag"), facts.lag().isEmpty()
                ? UiMessages.text("editor.route_tape.native_lag_none")
                : UiMessages.text("editor.route_tape.native_lag_value",
                        Map.of("count", String.valueOf(facts.lag().size()), "stale", String.valueOf(facts.lag().size() - matching)))));
        for (QualificationStamp stamp : loaded.manifest().qualification()) {
            if (stamp.scope() != ReplayScope.BIZHAWK) continue;
            String verdict = stamp.passed() ? UiMessages.text("editor.route_tape.native_passed") : stamp.verdict();
            if (!stamp.content().equals(content)) verdict = UiMessages.text("editor.route_tape.native_other_content",
                    Map.of("verdict", verdict));
            lines.add(fact(UiMessages.text("editor.route_tape.fact_native_check"), verdict));
        }
        return List.copyOf(lines);
    }

    private static String[] fact(String label, String value) {
        return new String[] {label, value};
    }

    private static String levelText(LevelId level) {
        return level.game().toUpperCase(java.util.Locale.ROOT) + " " + UiMessages.text("editor.route_tape.level_value",
                Map.of("zone", String.valueOf(level.zone()), "act", String.valueOf(level.act() + 1)));
    }

    /** Whether the editor's level is the route's launch level, so the path overlay belongs on it. */
    public boolean levelMatches() {
        if (loaded == null || !(loaded.launch().inlet() instanceof Inlet.ColdLevel cold)) return false;
        return host.currentLevel().map(cold.level()::equals).orElse(false);
    }

    // ---------------------------------------------------------------- timeline and cursor

    public int cursor() {
        return cursor;
    }

    public int track() {
        return track;
    }

    public int mark() {
        return mark;
    }

    public int firstRow() {
        return first;
    }

    public int visibleRows() {
        return visibleRows;
    }

    /** The frontend reports how many rows its timeline shows. */
    public void setVisibleRows(int rows) {
        visibleRows = Math.max(8, rows);
        first = RouteTapeTimeline.follow(first, visibleRows, cursor, tape.size());
    }

    public int selectionFrom() {
        return mark < 0 ? cursor : Math.min(mark, cursor);
    }

    /** Exclusive end of the selection; one row (the cursor) when nothing is marked. */
    public int selectionTo() {
        return mark < 0 ? cursor + 1 : Math.max(mark, cursor) + 1;
    }

    public void setCursor(int row) {
        if (tape.size() == 0) return;
        cursor = Math.max(0, Math.min(tape.size() - 1, row));
        first = RouteTapeTimeline.follow(first, visibleRows, cursor, tape.size());
        focusCursor();
    }

    public void moveCursor(int rows) {
        setCursor(cursor + rows);
    }

    public void setTrack(int value) {
        track = Math.floorMod(value, RouteTapeTimeline.TRACKS.size());
    }

    public void moveTrack(int delta) {
        setTrack(track + delta);
    }

    /** Scrolls the window without moving the cursor (pointer wheel). */
    public void scroll(int rows) {
        first = Math.max(0, Math.min(Math.max(0, tape.size() - visibleRows / 2), first + rows));
    }

    public RouteTapeTimeline.View timeline() {
        var markers = new ArrayList<RouteTapeTimeline.Marker>();
        int diverged = divergedRow();
        if (diverged >= 0 && diverged < Math.max(1, tape.size())) {
            markers.add(new RouteTapeTimeline.Marker(RouteTapeTimeline.MarkerKind.DIVERGED, diverged, diverged + 1));
        }
        if (loaded != null) {
            var content = working().content();
            for (NativeFacts.LagFact lag : loaded.manifest().nativeFacts().lag()) {
                if (lag.content().equals(content) && lag.beforeRow() < tape.size()) {
                    markers.add(new RouteTapeTimeline.Marker(RouteTapeTimeline.MarkerKind.NATIVE_LAG,
                            lag.beforeRow(), lag.beforeRow() + 1));
                }
            }
        }
        int selectionFrom = mark < 0 ? cursor : selectionFrom();
        int selectionTo = mark < 0 ? cursor : selectionTo();
        return RouteTapeTimeline.layout(tape, first, visibleRows, cursor, track, selectionFrom, selectionTo, markers);
    }

    /** Rows of facts on the timeline (divergence, native lag), in order; used to step between them. */
    public List<Integer> factRows() {
        return timeline().markers().stream().map(RouteTapeTimeline.Marker::from).distinct().sorted().toList();
    }

    private void focusCursor() {
        RouteTapeOverlay path = overlay();
        if (path == null || !levelMatches()) return;
        RouteTapeOverlay.Point point = path.at(cursor);
        if (point != null) host.focusWorld(point.x(), point.y());
    }

    /** Paints the observed path over the editor's level, only when it shows the route's level. */
    public void paintOverlay(RouteTapeOverlay.Painter painter, RouteTapeOverlay.Camera camera) {
        RouteTapeOverlay path = overlay();
        if (path != null && levelMatches()) path.paint(painter, camera, cursor);
    }

    // ---------------------------------------------------------------- commands

    public void invoke(Action action) {
        try {
            host.finishPendingEdits();
            switch (action) {
                case OPEN -> beforeReplace(this::openForm);
                case SAVE -> save();
                case REPLAY -> replay();
                case CANCEL_JOB -> cancelJob();
                case EXPORT_BK2 -> exportForm();
                case UNDO -> undo();
                case REDO -> redo();
                case TOGGLE -> toggle();
                case MARK -> mark = mark < 0 ? cursor : -1;
                case ROWS -> rowsMenu();
                case INSERT_ROWS -> insertForm();
                case DELETE_ROWS -> deleteRows();
                case DUPLICATE_ROWS -> duplicateRows();
                case CLEAR_LANE -> clearLane();
                case GO_TO_ROW -> goToForm();
                case NEXT_FACT -> nextFact();
                case MENU -> routeMenu();
                case EXIT -> deactivate();
            }
        } catch (RuntimeException failure) {
            report(failure);
        }
    }

    public void report(Throwable failure) {
        Throwable cause = failure instanceof UncheckedIOException io ? io.getCause() : failure;
        status = Objects.toString(cause.getMessage(), cause.getClass().getSimpleName());
        menu.report(cause);
    }

    /** Called once per editor tick: polls the running job and the open form. */
    public boolean tick(com.openggf.control.InputHandler input) {
        pollJob();
        if (!active && !menu.isOpen()) return false;
        return menu.update(input);
    }

    public void open(Path source) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        RouteArtifact candidate = RouteFiles.read(absolute);
        cancelJob();
        manifest = absolute;
        loaded = candidate;
        tape = candidate.tape();
        undo.clear();
        redo.clear();
        result = null;
        cursor = 0;
        first = 0;
        mark = -1;
        active = true;
        status = UiMessages.text("editor.route_tape.status_opened", Map.of("path", absolute.getFileName().toString(),
                "rows", String.valueOf(tape.size())));
    }

    /** Re-reads the saved route after a qualified save, keeping cursor and edit history. */
    private void reload(Path saved) throws IOException {
        RouteArtifact candidate = RouteFiles.read(saved);
        manifest = saved.toAbsolutePath().normalize();
        loaded = candidate;
    }

    private void openForm() {
        menu.showForm(UiMessages.text("editor.route_tape.title_open"), List.of(new EditorProjectMenu.Field("path",
                UiMessages.text("editor.route_tape.field_manifest"), host.initialDirectory() + "/",
                MenuTextEditor.Mode.PATH)), UiMessages.text("editor.route_tape.action_open"),
                values -> io(() -> open(Path.of(values.get("path")))));
        menu.setSubtitle(UiMessages.text("editor.route_tape.subtitle_open"));
    }

    private void beforeReplace(Runnable next) {
        if (!dirty()) {
            next.run();
            return;
        }
        menu.showActions(UiMessages.text("editor.route_tape.title_unsaved"), List.of(
                new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_discard"),
                        UiMessages.text("editor.route_tape.note_discard"), next),
                new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_keep_editing"), () -> { })));
    }

    /** Retires any running worker; the host calls this at shutdown. */
    public void close() {
        host.cancelJob();
        job = null;
    }

    /** Called by the host before quitting; cancellation keeps the route open. */
    public void requestQuit(Runnable next) {
        beforeReplace(next);
    }

    private void save() {
        requireRoute();
        startJob(RouteTapeJob.Kind.SAVE);
    }

    private void replay() {
        requireRoute();
        startJob(RouteTapeJob.Kind.REPLAY);
    }

    private void startJob(RouteTapeJob.Kind kind) {
        if (jobRunning()) throw new IllegalStateException(UiMessages.text("editor.route_tape.job_already_running"));
        if (tape.size() == 0) throw new IllegalStateException(UiMessages.text("editor.route_tape.empty_tape"));
        String stem = manifest.getFileName().toString();
        stem = stem.substring(0, stem.length() - RouteFiles.MANIFEST_SUFFIX.length());
        var request = new RouteTapeJob.Request(kind, manifest, RouteFiles.scriptText(tape),
                kind == RouteTapeJob.Kind.SAVE ? manifest.resolveSibling(stem) : null, true);
        io(() -> host.startJob(request, workingContent(), tape.size()));
        job = host.pollJob();
        status = UiMessages.text(kind == RouteTapeJob.Kind.SAVE ? "editor.route_tape.status_saving"
                : "editor.route_tape.status_replaying");
    }

    private void cancelJob() {
        if (job != null && job.running()) status = UiMessages.text("editor.route_tape.status_cancelled");
        host.cancelJob();
        job = null;
    }

    private void pollJob() {
        RouteTapeJob.Update next = host.pollJob();
        if (next == null || next.equals(job)) return;
        job = next;
        if (next.running()) return;
        switch (next.phase()) {
            case PASSED, FAILED, UNSUPPORTED -> {
                result = next;
                if (next.saved() != null) {
                    try {
                        reload(next.saved());
                    } catch (IOException failure) {
                        report(failure);
                        return;
                    }
                }
                status = switch (next.phase()) {
                    case PASSED -> UiMessages.text(next.saved() != null ? "editor.route_tape.status_saved_qualified"
                            : "editor.route_tape.status_replay_passed", Map.of("rows", String.valueOf(next.row())));
                    case FAILED -> UiMessages.text("editor.route_tape.status_replay_failed",
                            Map.of("row", String.valueOf(next.row()), "reason", next.message()));
                    default -> UiMessages.text("editor.route_tape.status_replay_unsupported",
                            Map.of("reason", next.message()));
                };
                if (next.phase() == RouteTapeJob.Phase.FAILED && next.row() >= 0) setCursor(next.row());
            }
            case ERROR -> status = UiMessages.text("editor.route_tape.status_job_error", Map.of("reason", next.message()));
            default -> { }
        }
    }

    private void exportForm() {
        requireRoute();
        String name = manifest.getFileName().toString().replace(RouteFiles.MANIFEST_SUFFIX, ".bk2");
        Path suggested = Path.of("target", "route-exports", name).toAbsolutePath().normalize();
        menu.showForm(UiMessages.text("editor.route_tape.title_export"), List.of(new EditorProjectMenu.Field("path",
                UiMessages.text("editor.route_tape.field_movie"), suggested.toString(), MenuTextEditor.Mode.PATH)),
                UiMessages.text("editor.route_tape.action_export"), values -> io(() -> exportBk2(Path.of(values.get("path")))));
        menu.setSubtitle(UiMessages.text("editor.route_tape.subtitle_export"));
    }

    /** Derives a BizHawk movie of the working rows; never overwrites an existing file. */
    public Path exportBk2(Path destination) throws IOException {
        requireRoute();
        Path target = destination.toAbsolutePath().normalize();
        if (Files.exists(target)) throw new IOException(UiMessages.text("editor.route_tape.export_exists"));
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        RouteFiles.exportBk2(working(), target);
        status = UiMessages.text("editor.route_tape.status_exported", Map.of("path", target.toString()));
        return target;
    }

    // ---------------------------------------------------------------- row edits

    private void edit(InputTape next) {
        if (next.equals(tape)) return;
        undo.push(tape);
        while (undo.size() > UNDO_LIMIT) undo.removeLast();
        redo.clear();
        tape = next;
        cursor = Math.min(cursor, Math.max(0, tape.size() - 1));
        if (mark >= tape.size()) mark = -1;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(tape);
        tape = undo.pop();
        cursor = Math.min(cursor, Math.max(0, tape.size() - 1));
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(tape);
        tape = redo.pop();
        cursor = Math.min(cursor, Math.max(0, tape.size() - 1));
    }

    private void toggle() {
        requireRows();
        var lane = RouteTapeTimeline.TRACKS.get(track);
        edit(RouteTapeEdits.toggle(tape, selectionFrom(), selectionTo(), lane.slot(), lane.button()));
    }

    private void clearLane() {
        requireRows();
        edit(RouteTapeEdits.setLane(tape, selectionFrom(), selectionTo(), RouteTapeTimeline.TRACKS.get(track).slot(), 0));
    }

    private void deleteRows() {
        requireRows();
        int from = selectionFrom();
        edit(RouteTapeEdits.delete(tape, from, selectionTo()));
        mark = -1;
        setCursor(from);
    }

    private void duplicateRows() {
        requireRows();
        edit(RouteTapeEdits.duplicate(tape, selectionFrom(), selectionTo()));
    }

    /** Inserts {@code count} rows before the cursor, copying the cursor row (or neutral rows). */
    public void insertRows(int count, boolean neutral) {
        requireRoute();
        PadRow template = neutral || tape.size() == 0 ? PadRow.NEUTRAL : tape.row(cursor);
        edit(RouteTapeEdits.insert(tape, Math.min(cursor, tape.size()), count, template));
    }

    private void insertForm() {
        requireRoute();
        menu.showForm(UiMessages.text("editor.route_tape.title_insert"), List.of(
                new EditorProjectMenu.Field("count", UiMessages.text("editor.route_tape.field_count"), "1",
                        MenuTextEditor.Mode.TEXT, 6),
                new EditorProjectMenu.Field("template", UiMessages.text("editor.route_tape.field_template"),
                        UiMessages.text("editor.route_tape.template_copy"),
                        List.of(UiMessages.text("editor.route_tape.template_copy"),
                                UiMessages.text("editor.route_tape.template_neutral")))),
                UiMessages.text("editor.route_tape.action_insert"), values -> {
                    int count = Integer.parseInt(values.get("count").strip());
                    if (count < 1 || count > InputTape.MAX_ROWS - tape.size()) {
                        throw new IllegalArgumentException(UiMessages.text("editor.route_tape.insert_count_range"));
                    }
                    insertRows(count, values.get("template").equals(UiMessages.text("editor.route_tape.template_neutral")));
                });
    }

    private void goToForm() {
        requireRows();
        menu.showForm(UiMessages.text("editor.route_tape.title_go_to"), List.of(new EditorProjectMenu.Field("row",
                UiMessages.text("editor.route_tape.field_row"), String.valueOf(cursor), MenuTextEditor.Mode.TEXT, 8)),
                UiMessages.text("editor.route_tape.action_go"), values -> setCursor(Integer.parseInt(values.get("row").strip())));
    }

    private void nextFact() {
        for (int row : factRows()) if (row > cursor) {
            setCursor(row);
            return;
        }
        List<Integer> rows = factRows();
        if (!rows.isEmpty()) setCursor(rows.getFirst());
    }

    private void rowsMenu() {
        requireRows();
        menu.showActions(UiMessages.text("editor.route_tape.title_rows", Map.of("from", String.valueOf(selectionFrom()),
                "to", String.valueOf(selectionTo() - 1))), List.of(
                action(Action.INSERT_ROWS), action(Action.DUPLICATE_ROWS), action(Action.DELETE_ROWS),
                action(Action.CLEAR_LANE), action(Action.GO_TO_ROW),
                new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_back"), () -> { })));
    }

    private void routeMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(Action.OPEN));
        if (loaded != null) {
            actions.add(action(Action.SAVE));
            actions.add(action(jobRunning() ? Action.CANCEL_JOB : Action.REPLAY));
            actions.add(action(Action.EXPORT_BK2));
            actions.add(action(Action.GO_TO_ROW));
        }
        actions.add(action(Action.EXIT));
        menu.showActions(UiMessages.text("editor.route_tape.title_route"), actions);
    }

    private EditorProjectMenu.Action action(Action action) {
        return new EditorProjectMenu.Action(action.label(), () -> invoke(action));
    }

    private void requireRoute() {
        if (loaded == null) throw new IllegalStateException(UiMessages.text("editor.route_tape.no_route"));
    }

    private void requireRows() {
        requireRoute();
        if (tape.size() == 0) throw new IllegalStateException(UiMessages.text("editor.route_tape.empty_tape"));
    }

    /** Plain-text details for the HUD reader and the desktop help. */
    public String detailsText() {
        var text = new StringBuilder();
        text.append(manifest == null ? UiMessages.text("editor.route_tape.qualification_empty") : manifest.toString())
                .append("\n\n").append(qualificationText()).append('\n');
        for (String[] fact : launchFacts()) text.append(fact[0]).append(": ").append(fact[1]).append('\n');
        text.append('\n').append(UiMessages.text("editor.route_tape.native_heading")).append('\n');
        for (String[] fact : nativeFacts()) text.append(fact[0]).append(": ").append(fact[1]).append('\n');
        text.append('\n').append(status);
        return text.toString();
    }

    @FunctionalInterface
    private interface IoTask {
        void run() throws IOException;
    }

    private static void io(IoTask task) {
        try {
            task.run();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
