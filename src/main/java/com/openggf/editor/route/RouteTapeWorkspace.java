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

        default void startJob(RouteTapeJob.Request request) throws IOException {
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
        OPEN, NEW, SAVE, SAVE_DRAFT, REPLAY, REPAIR, USE_ATTEMPT, CANCEL_JOB, EXPORT_BK2, UNDO, REDO, TOGGLE, MARK, ROWS, INSERT_ROWS, DELETE_ROWS, DUPLICATE_ROWS, CLEAR_LANE, GO_TO_ROW, NEXT_FACT, MENU, EXIT;

        public String label() {
            return switch (this) {
                case OPEN -> UiMessages.text("editor.route_tape.action_open");
                case NEW -> UiMessages.text("editor.route_tape.action_new");
                case SAVE -> UiMessages.text("editor.route_tape.action_save");
                case SAVE_DRAFT -> UiMessages.text("editor.route_tape.action_save_draft");
                case REPLAY -> UiMessages.text("editor.route_tape.action_replay");
                case REPAIR -> UiMessages.text("editor.route_tape.action_repair");
                case USE_ATTEMPT -> UiMessages.text("editor.route_tape.action_use_attempt");
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
        /** The rows changed since the file was read; they have not been replayed. */
        EDITED,
        /** An unqualified draft: editors reopen it, consumers refuse it. */
        DRAFT,
        /** No engine stamp covers the working content. */
        UNCHECKED
    }

    private static final int UNDO_LIMIT = 200;

    private final Host host;
    private final EditorProjectMenu menu = new EditorProjectMenu();
    private final Deque<InputTape> undo = new ArrayDeque<>();
    private final Deque<InputTape> redo = new ArrayDeque<>();
    private Path stem;
    private boolean unsaved;
    private RouteArtifact loaded;
    private InputTape tape = InputTape.empty();
    private RouteTapeJob.Update job;
    private RouteTapeJob.Update result;
    /** The last replay that observed a path, kept while later edits make it stale. */
    private RouteTapeJob.Update pathResult;
    /** The last repair outcome and the working content it started from. */
    private RouteTapeJob.Update repair;
    private String repairFrom;
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

    /** The route's manifest path, or null for a new route not yet given a file. */
    public Path manifestPath() {
        return stem == null ? null : stem.resolveSibling(stem.getFileName() + RouteFiles.MANIFEST_SUFFIX);
    }

    public RouteArtifact loaded() {
        return loaded;
    }

    public InputTape tape() {
        return tape;
    }

    public boolean dirty() {
        return loaded != null && (unsaved || !tape.equals(loaded.tape()));
    }

    public boolean draft() {
        return loaded != null && loaded.manifest().draft();
    }

    /** The route as it would be saved: the file's launch, goals, provenance and facts over the working rows. */
    public RouteArtifact working() {
        if (loaded == null) return null;
        RouteManifest source = loaded.manifest();
        return new RouteArtifact(new RouteManifest(source.launch(), tape.hash(), tape.size(), source.checks(),
                source.draft(), source.provenance(), source.qualification(), source.nativeFacts()), tape);
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
        return pathResult == null ? null : pathResult.path();
    }

    public boolean pathStale() {
        return pathResult != null && !Objects.equals(pathResult.content(), workingContent());
    }

    /** The last repair's outcome, or null; a solved repair's spans describe the rows only while they are unchanged. */
    public RouteTapeJob.RepairOutcome repairOutcome() {
        return repair == null ? null : repair.repair();
    }

    /** Whether the working rows are exactly the last solved repair's rows. */
    public boolean repairCurrent() {
        var outcome = repairOutcome();
        return outcome != null && outcome.solved() && outcome.rows().equals(tape);
    }

    /** One line about the last repair, in the user's words; empty when there is none. */
    public String repairText() {
        var outcome = repairOutcome();
        if (outcome == null) return "";
        if (!outcome.solved()) return UiMessages.text("editor.route_tape.repair_stopped", Map.of("row",
                String.valueOf(repair.row()), "reason", outcome.detail()));
        if (!repairCurrent()) return UiMessages.text("editor.route_tape.repair_edited");
        long windows = outcome.spans().stream().filter(span -> span.kind() == RouteTapeTimeline.MarkerKind.SEARCHED).count();
        return UiMessages.text("editor.route_tape.repair_summary", Map.of("kept", String.valueOf(outcome.keptRows()),
                "original", String.valueOf(outcome.originalRows()), "share",
                String.valueOf(Math.round(outcome.keptShare() * 100)), "searched", String.valueOf(outcome.searchedRows()),
                "windows", String.valueOf(windows)));
    }

    public Qualification qualification() {
        if (loaded == null) return Qualification.EMPTY;
        RouteTapeJob.Update current = result();
        boolean stamped = !unsaved && working().engineQualified();
        if (current != null) {
            switch (current.phase()) {
                case PASSED -> { return stamped ? Qualification.QUALIFIED : Qualification.PASSES_UNSAVED; }
                case FAILED -> { return Qualification.NOT_QUALIFIED; }
                case UNSUPPORTED -> { return Qualification.UNSUPPORTED; }
                default -> { }
            }
        }
        if (stamped) return Qualification.QUALIFIED;
        if (engineStamp().isPresent() && !dirty()) return Qualification.NOT_QUALIFIED;
        if (dirty()) return Qualification.EDITED;
        return draft() ? Qualification.DRAFT : Qualification.UNCHECKED;
    }

    /**
     * The first row the working content could not complete: from this session's replay, else from
     * a failed engine stamp in the file; -1 when neither exists.
     */
    public int divergedRow() {
        RouteTapeJob.Update current = result();
        if (current != null && current.phase() == RouteTapeJob.Phase.FAILED) return current.row();
        return engineStamp().filter(stamp -> !stamp.passed()).map(stamp -> stamp.divergedRow().orElse(-1)).orElse(-1);
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
            case DRAFT -> UiMessages.text("editor.route_tape.qualification_draft");
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
        boolean engineQualified = working().engineQualified();
        for (QualificationStamp stamp : loaded.manifest().qualification()) {
            if (stamp.scope() != ReplayScope.BIZHAWK) continue;
            String verdict = stamp.passed() ? UiMessages.text("editor.route_tape.native_passed")
                    : UiMessages.text(engineQualified ? "editor.route_tape.native_diverged_engine_qualified"
                            : "editor.route_tape.native_diverged", Map.of("row",
                            String.valueOf(stamp.divergedRow().orElse(-1)), "reason", stamp.reason()));
            if (!stamp.content().equals(content)) verdict = UiMessages.text("editor.route_tape.native_other_content",
                    Map.of("verdict", verdict));
            lines.add(fact(UiMessages.text("editor.route_tape.fact_native_check"), verdict));
        }
        if (needsNativeCheck()) {
            lines.add(fact(UiMessages.text("editor.route_tape.fact_native_check"),
                    UiMessages.text("editor.route_tape.native_check_needed")));
        }
        return List.copyOf(lines);
    }

    /**
     * Whether native observations exist but none describe the working content, so a fresh native
     * check is needed (after an edit or a repair). Display only.
     */
    public boolean needsNativeCheck() {
        if (loaded == null) return false;
        var content = working().content();
        var facts = loaded.manifest().nativeFacts();
        boolean observed = !facts.lag().isEmpty() || loaded.manifest().qualification().stream()
                .anyMatch(stamp -> stamp.scope() == ReplayScope.BIZHAWK);
        boolean current = facts.lag().stream().anyMatch(lag -> lag.content().equals(content))
                || loaded.manifest().qualification().stream()
                .anyMatch(stamp -> stamp.scope() == ReplayScope.BIZHAWK && stamp.content().equals(content));
        return observed && !current;
    }

    /** The goal program replay checks, one line per leg, in manifest words. */
    public List<String[]> goalFacts() {
        if (loaded == null) return List.of();
        var program = loaded.checks();
        var lines = new ArrayList<String[]>();
        if (program.isEmpty()) {
            lines.add(fact(UiMessages.text("editor.route_tape.fact_goals"), UiMessages.text("editor.route_tape.goals_none")));
            return List.copyOf(lines);
        }
        for (var leg : program.legs()) {
            String goal = switch (leg.goal()) {
                case com.openggf.route.RouteGoal.Achieve achieve -> predicateText(achieve.condition());
                case com.openggf.route.RouteGoal.EnterBoundary ignored -> UiMessages.text("editor.route_tape.goal_boundary");
            };
            lines.add(fact(leg.id(), UiMessages.text("editor.route_tape.goal_within", Map.of("goal", goal,
                    "rows", String.valueOf(leg.maxRows())))));
        }
        return List.copyOf(lines);
    }

    private static String predicateText(com.openggf.route.SamplePredicate predicate) {
        return switch (predicate) {
            case com.openggf.route.SamplePredicate.PlayerIn in -> UiMessages.text("editor.route_tape.goal_reach",
                    Map.of("player", in.player().name(), "left", String.valueOf(in.region().left()),
                            "top", String.valueOf(in.region().top()), "right", String.valueOf(in.region().right()),
                            "bottom", String.valueOf(in.region().bottom())));
            case com.openggf.route.SamplePredicate.Settled settled -> UiMessages.text("editor.route_tape.goal_settled",
                    Map.of("player", settled.player().name()));
            case com.openggf.route.SamplePredicate.LevelIs level -> UiMessages.text("editor.route_tape.goal_level",
                    Map.of("zone", String.valueOf(level.zone()), "act", String.valueOf(level.act() + 1)));
            case com.openggf.route.SamplePredicate.All all -> String.join(" + ",
                    all.clauses().stream().map(RouteTapeWorkspace::predicateText).toList());
        };
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
            for (QualificationStamp stamp : loaded.manifest().qualification()) {
                int row = stamp.divergedRow().orElse(-1);
                if (stamp.scope() == ReplayScope.BIZHAWK && stamp.content().equals(content) && row >= 0 && row < tape.size()) {
                    markers.add(new RouteTapeTimeline.Marker(RouteTapeTimeline.MarkerKind.NATIVE_DIVERGED, row, row + 1));
                }
            }
            for (NativeFacts.LagFact lag : loaded.manifest().nativeFacts().lag()) {
                if (lag.content().equals(content) && lag.beforeRow() < tape.size()) {
                    markers.add(new RouteTapeTimeline.Marker(RouteTapeTimeline.MarkerKind.NATIVE_LAG,
                            lag.beforeRow(), lag.beforeRow() + 1));
                }
            }
        }
        if (repairCurrent()) {
            for (var span : repairOutcome().spans()) {
                if (span.length() > 0) markers.add(new RouteTapeTimeline.Marker(span.kind(), span.outputStart(),
                        span.outputStart() + span.length()));
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
                case NEW -> beforeReplace(this::newForm);
                case SAVE -> save();
                case SAVE_DRAFT -> saveDraft();
                case REPAIR -> repairForm();
                case USE_ATTEMPT -> useAttempt();
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
        if (!absolute.getFileName().toString().endsWith(RouteFiles.MANIFEST_SUFFIX)) {
            throw new IOException(UiMessages.text("editor.route_tape.not_a_manifest"));
        }
        // Editing reads drafts and unqualified routes too; consumers use RouteFiles.read.
        RouteArtifact candidate = RouteFiles.readDraft(absolute);
        cancelJob();
        stem = stemOf(absolute);
        unsaved = false;
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

    /** Re-reads the saved route after a save, keeping cursor and edit history. */
    private void reload(Path saved) throws IOException {
        RouteArtifact candidate = RouteFiles.readDraft(saved);
        stem = stemOf(saved.toAbsolutePath().normalize());
        unsaved = false;
        loaded = candidate;
    }

    private static Path stemOf(Path manifest) {
        String name = manifest.getFileName().toString();
        return manifest.resolveSibling(name.substring(0, name.length() - RouteFiles.MANIFEST_SUFFIX.length()));
    }

    /** A new unsaved draft route: a cold-level launch over {@code rows} neutral rows, to be saved at {@code manifest}. */
    public void create(Path manifest, LaunchContract launch, int rows) {
        Path absolute = manifest.toAbsolutePath().normalize();
        if (!absolute.getFileName().toString().endsWith(RouteFiles.MANIFEST_SUFFIX)) {
            throw new IllegalArgumentException(UiMessages.text("editor.route_tape.not_a_manifest"));
        }
        if (rows < 1 || rows > InputTape.MAX_ROWS) throw new IllegalArgumentException(UiMessages.text("editor.route_tape.insert_count_range"));
        var builder = new InputTape.Builder();
        for (int i = 0; i < rows; i++) builder.add(PadRow.NEUTRAL);
        InputTape rowsTape = builder.build();
        cancelJob();
        var fresh = RouteArtifact.of(launch, rowsTape, new com.openggf.route.Provenance("openggf-editor", "", "", List.of()));
        loaded = new RouteArtifact(new RouteManifest(launch, rowsTape.hash(), rowsTape.size(), fresh.checks(), true,
                fresh.manifest().provenance(), List.of(), fresh.manifest().nativeFacts()), rowsTape);
        tape = rowsTape;
        stem = stemOf(absolute);
        unsaved = true;
        undo.clear();
        redo.clear();
        result = null;
        cursor = 0;
        first = 0;
        mark = -1;
        active = true;
        status = UiMessages.text("editor.route_tape.status_created", Map.of("path", absolute.getFileName().toString()));
    }

    private void newForm() {
        String game = host.currentLevel().map(LevelId::game).orElse("s3k");
        int zone = host.currentLevel().map(LevelId::zone).orElse(0), act = host.currentLevel().map(LevelId::act).orElse(0);
        menu.showForm(UiMessages.text("editor.route_tape.title_new"), List.of(
                new EditorProjectMenu.Field("path", UiMessages.text("editor.route_tape.field_manifest"),
                        host.initialDirectory().resolve("new-route" + RouteFiles.MANIFEST_SUFFIX).toString(), MenuTextEditor.Mode.PATH),
                new EditorProjectMenu.Field("game", UiMessages.text("editor.route_tape.field_game"), game, List.of("s1", "s2", "s3k")),
                new EditorProjectMenu.Field("zone", UiMessages.text("editor.route_tape.field_zone"), String.valueOf(zone), MenuTextEditor.Mode.TEXT, 3),
                new EditorProjectMenu.Field("act", UiMessages.text("editor.route_tape.field_act"), String.valueOf(act + 1), MenuTextEditor.Mode.TEXT, 2),
                new EditorProjectMenu.Field("main", UiMessages.text("editor.route_tape.field_main"), "sonic", MenuTextEditor.Mode.TEXT, 24),
                new EditorProjectMenu.Field("sidekick", UiMessages.text("editor.route_tape.field_sidekick"), "tails", MenuTextEditor.Mode.TEXT, 24),
                new EditorProjectMenu.Field("width", UiMessages.text("editor.route_tape.field_width"), "320", MenuTextEditor.Mode.TEXT, 4),
                new EditorProjectMenu.Field("vint", UiMessages.text("editor.route_tape.field_vint"), "0", MenuTextEditor.Mode.TEXT, 6),
                new EditorProjectMenu.Field("rows", UiMessages.text("editor.route_tape.field_count"), "60", MenuTextEditor.Mode.TEXT, 7)),
                UiMessages.text("editor.route_tape.action_create"), values -> {
                    String chosen = values.get("game");
                    var level = new LevelId(chosen, Integer.parseInt(values.get("zone").strip()),
                            Integer.parseInt(values.get("act").strip()) - 1);
                    var launch = LaunchContract.coldLevel(com.openggf.route.RomIdentity.standard(chosen), level,
                            new com.openggf.route.Roster(values.get("main").strip(), values.get("sidekick").strip()),
                            Integer.parseInt(values.get("width").strip()), Integer.parseInt(values.get("vint").strip()));
                    create(Path.of(values.get("path")), launch, Integer.parseInt(values.get("rows").strip()));
                });
        menu.setSubtitle(UiMessages.text("editor.route_tape.subtitle_new"));
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

    /** Saves the working rows as an unqualified draft; a draft never replaces a qualified route. */
    private void saveDraft() {
        requireRoute();
        io(() -> {
            var written = RouteFiles.writeDraft(stem, working());
            reload(written.manifest());
            status = UiMessages.text("editor.route_tape.status_saved_draft", Map.of("path",
                    written.manifest().getFileName().toString()));
        });
    }

    private void replay() {
        requireRoute();
        startJob(RouteTapeJob.Kind.REPLAY);
    }

    private void startJob(RouteTapeJob.Kind kind) {
        if (jobRunning()) throw new IllegalStateException(UiMessages.text("editor.route_tape.job_already_running"));
        if (tape.size() == 0) throw new IllegalStateException(UiMessages.text("editor.route_tape.empty_tape"));
        var request = new RouteTapeJob.Request(kind, working(), kind == RouteTapeJob.Kind.SAVE ? stem : null,
                kind == RouteTapeJob.Kind.SAVE && Files.exists(manifestPath()));
        io(() -> host.startJob(request));
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
            case SOLVED, STOPPED -> finishRepair(next);
            case PASSED, FAILED, UNSUPPORTED -> {
                if (next.kind() == RouteTapeJob.Kind.REPAIR) {
                    status = UiMessages.text("editor.route_tape.status_replay_unsupported", Map.of("reason", next.message()));
                    return;
                }
                result = next;
                if (next.path() != null) pathResult = next;
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

    private void finishRepair(RouteTapeJob.Update next) {
        repair = next;
        var outcome = next.repair();
        if (!outcome.solved()) {
            status = repairText();
            return;
        }
        if (!Objects.equals(repairFrom, workingContent())) {
            repair = null;
            status = UiMessages.text("editor.route_tape.repair_stale");
            return;
        }
        edit(outcome.rows());
        // The planner cold-replayed exactly these rows; when the route's own content is the same, that
        // pass qualifies them in this session. A goal program the planner did not check stays unreplayed.
        result = new RouteTapeJob.Update(next.generation(), RouteTapeJob.Phase.PASSED, RouteTapeJob.Kind.REPAIR, 1, 1,
                outcome.rows().size(), outcome.rows().size(), next.message(), next.content(), outcome.rows().size(),
                null, null);
        status = repairText();
    }

    /** Adopts a stopped repair's furthest attempt as the working rows, for inspection; one undo step. */
    private void useAttempt() {
        var outcome = repairOutcome();
        if (outcome == null || outcome.solved() || outcome.rows().size() == 0) {
            throw new IllegalStateException(UiMessages.text("editor.route_tape.no_attempt"));
        }
        edit(outcome.rows());
        status = UiMessages.text("editor.route_tape.status_attempt", Map.of("rows", String.valueOf(outcome.rows().size())));
    }

    private void repairForm() {
        requireRows();
        String self = UiMessages.text("editor.route_tape.reference_self"), other = UiMessages.text("editor.route_tape.reference_other");
        menu.showForm(UiMessages.text("editor.route_tape.title_repair"), List.of(
                new EditorProjectMenu.Field("first", UiMessages.text("editor.route_tape.field_first_editable"),
                        String.valueOf(cursor), MenuTextEditor.Mode.TEXT, 8),
                new EditorProjectMenu.Field("reference", UiMessages.text("editor.route_tape.field_reference"),
                        unsaved ? other : self, List.of(self, other)),
                new EditorProjectMenu.Field("path", UiMessages.text("editor.route_tape.field_reference_path"),
                        host.initialDirectory() + "/", MenuTextEditor.Mode.PATH),
                new EditorProjectMenu.Field("rows", UiMessages.text("editor.route_tape.field_reference_rows"), "",
                        MenuTextEditor.Mode.TEXT, 8),
                new EditorProjectMenu.Field("from", UiMessages.text("editor.route_tape.field_window_from"),
                        String.valueOf(cursor), MenuTextEditor.Mode.TEXT, 8),
                new EditorProjectMenu.Field("to", UiMessages.text("editor.route_tape.field_window_to"), "",
                        MenuTextEditor.Mode.TEXT, 8),
                // The design's SSZ1 example bounds (360 authored rows, 24 windows); authoring choices, not rules.
                new EditorProjectMenu.Field("authored", UiMessages.text("editor.route_tape.field_max_authored"), "360",
                        MenuTextEditor.Mode.TEXT, 7),
                new EditorProjectMenu.Field("windows", UiMessages.text("editor.route_tape.field_max_windows"), "24",
                        MenuTextEditor.Mode.TEXT, 4)),
                UiMessages.text("editor.route_tape.action_repair"), values -> io(() -> {
                    RouteArtifact reference = values.get("reference").equals(self) ? savedReference()
                            : reference(Path.of(values.get("path").strip()), values.get("rows").strip());
                    String to = values.get("to").strip();
                    startRepair(reference, new RouteTapeJob.RepairBounds(Integer.parseInt(values.get("first").strip()),
                            Integer.parseInt(values.get("authored").strip()), Integer.parseInt(values.get("windows").strip()),
                            Integer.parseInt(values.get("from").strip()), to.isEmpty() ? -1 : Integer.parseInt(to)));
                }));
        menu.setSubtitle(UiMessages.text("editor.route_tape.subtitle_repair"));
    }

    /** Starts a repair of the working rows against {@code reference}. */
    public void startRepair(RouteArtifact reference, RouteTapeJob.RepairBounds bounds) throws IOException {
        requireRows();
        if (jobRunning()) throw new IllegalStateException(UiMessages.text("editor.route_tape.job_already_running"));
        if (bounds.firstEditable() > tape.size()) {
            throw new IllegalArgumentException(UiMessages.text("editor.route_tape.repair_first_range"));
        }
        repairFrom = workingContent();
        host.startJob(RouteTapeJob.Request.repair(working(), reference, bounds));
        job = host.pollJob();
        status = UiMessages.text("editor.route_tape.status_repairing");
    }

    /** The route's saved rows and launch, replayed on this engine as the reference to follow. */
    private RouteArtifact savedReference() {
        if (unsaved) throw new IllegalStateException(UiMessages.text("editor.route_tape.reference_unsaved"));
        return loaded;
    }

    /** A reference from another route manifest (its own launch), a route script or a movie (this route's launch). */
    private RouteArtifact reference(Path path, String rows) throws IOException {
        String name = path.getFileName().toString();
        if (name.endsWith(RouteFiles.MANIFEST_SUFFIX)) return RouteFiles.readDraft(path);
        var provenance = new com.openggf.route.Provenance("openggf-editor reference", "", path.toString(), List.of());
        if (name.endsWith(".script")) {
            return RouteArtifact.of(loaded.launch(), RouteFiles.tapeFromScript(Files.readString(path)), provenance);
        }
        if (rows.isEmpty()) throw new IOException(UiMessages.text("editor.route_tape.reference_rows_needed"));
        return RouteArtifact.of(loaded.launch(), RouteFiles.tapeFromMovie(path, Integer.parseInt(rows)), provenance);
    }

    private void exportForm() {
        requireRoute();
        String name = stem.getFileName() + ".bk2";
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
        actions.add(action(Action.NEW));
        if (loaded != null) {
            actions.add(action(Action.SAVE));
            actions.add(action(Action.SAVE_DRAFT));
            actions.add(action(jobRunning() ? Action.CANCEL_JOB : Action.REPLAY));
            if (!jobRunning()) actions.add(action(Action.REPAIR));
            if (repairOutcome() != null && !repairOutcome().solved()) actions.add(action(Action.USE_ATTEMPT));
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
        text.append(manifestPath() == null ? UiMessages.text("editor.route_tape.qualification_empty") : manifestPath().toString())
                .append("\n\n").append(qualificationText()).append('\n');
        for (String[] fact : launchFacts()) text.append(fact[0]).append(": ").append(fact[1]).append('\n');
        for (String[] fact : goalFacts()) text.append(fact[0]).append(": ").append(fact[1]).append('\n');
        if (!repairText().isEmpty()) text.append(repairText()).append('\n');
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
