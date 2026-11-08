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

        /** Only the loaded, hash-verified retail S2 REV01 image admits the title host. */
        default boolean competitionAvailable() {
            if (!currentLevel().map(l -> l.game().equals("s2")).orElse(false)) return false;
            try {
                var rom = com.openggf.game.GameServices.rom().getRom();
                return com.openggf.data.RomFingerprint.of(rom.readAllBytes()).matches(
                        "7B905383", "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9");
            } catch (IOException | RuntimeException unavailable) { return false; }
        }

        /** Placed objects of the level the editor shows (layout order), for placement goal targets. */
        default List<PlacedObject> placements() {
            return List.of();
        }

        /** Centres the editor's level view on a world point. */
        default void focusWorld(int x, int y) {
        }

        default void finishPendingEdits() {
        }
    }

    /** A placed object of the editor's level: its layout index, id, subtype and position. */
    public record PlacedObject(int layoutIndex, int objectId, int subtype, int x, int y) { }

    /** A goal target the author chose, with words for it and the phases it reports. */
    public record PickedTarget(com.openggf.route.ObjectTarget target, String description, List<String> phases) {
        public PickedTarget {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(description, "description");
            phases = List.copyOf(phases);
        }
    }

    public enum Action {
        OPEN, NEW, SAVE, SAVE_DRAFT, NATIVE_FACTS, REPLAY, REPAIR, PLAN, GOALS, ADD_LEG, EDIT_GOALS, SCAN, USE_ATTEMPT, APPLY_ROWS, CANCEL_JOB, EXPORT_BK2, UNDO, REDO, TOGGLE, MARK, ROWS, INSERT_ROWS, DELETE_ROWS, DUPLICATE_ROWS, CLEAR_LANE, GO_TO_ROW, NEXT_FACT, MENU, EXIT;

        public String label() {
            return switch (this) {
                case NATIVE_FACTS -> UiMessages.text("editor.route_tape.native_anchors");
                case OPEN -> UiMessages.text("editor.route_tape.action_open");
                case NEW -> UiMessages.text("editor.route_tape.action_new");
                case SAVE -> UiMessages.text("editor.route_tape.action_save");
                case SAVE_DRAFT -> UiMessages.text("editor.route_tape.action_save_draft");
                case REPLAY -> UiMessages.text("editor.route_tape.action_replay");
                case REPAIR -> UiMessages.text("editor.route_tape.action_repair");
                case PLAN -> UiMessages.text("editor.route_tape.action_plan");
                case GOALS -> UiMessages.text("editor.route_tape.action_goals");
                case ADD_LEG -> UiMessages.text("editor.route_tape.action_add_leg");
                case EDIT_GOALS -> UiMessages.text("editor.route_tape.action_edit_goals");
                case SCAN -> UiMessages.text("editor.route_tape.action_scan");
                case USE_ATTEMPT -> UiMessages.text("editor.route_tape.action_use_attempt");
                case APPLY_ROWS -> UiMessages.text("editor.route_tape.action_apply_rows");
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
        /** A Stageplay frozen input track: its scene plays it; it is applied to the scene, not saved as a route. */
        SCENE,
        /** No engine stamp covers the working content. */
        UNCHECKED
    }

    private static final int UNDO_LIMIT = 200;

    /**
     * A Stageplay frozen input track opened for row editing. {@code apply} returns edited rows to
     * the scene as one of its own undo steps; {@code back} returns to the scene's workspace.
     */
    public record SceneRows(String title, String detail, InputTape original, java.util.function.Consumer<InputTape> apply,
                            Runnable back) {
        public SceneRows {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(original, "original");
            Objects.requireNonNull(apply, "apply");
            Objects.requireNonNull(back, "back");
        }
    }

    private final Host host;
    private final EditorProjectMenu menu = new EditorProjectMenu();
    /** Rows and goals change together under undo: a plan replaces both in one step. */
    private record State(InputTape tape, com.openggf.route.GoalProgram goals, NativeFacts facts) { }
    private NativeFacts currentNativeFacts = NativeFacts.NONE;
    private NativeFacts jobFacts = NativeFacts.NONE;
    private NativeFacts resultFacts = NativeFacts.NONE;

    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private com.openggf.route.GoalProgram goals = com.openggf.route.GoalProgram.NONE;
    private PickedTarget picked;
    private List<RouteTapeJob.LiveObject> scanned = List.of();
    private Path stem;
    private boolean unsaved;
    private SceneRows scene;
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

    /** Whether rows are open: a route file, a new route or a scene's frozen track. */
    public boolean hasRoute() {
        return loaded != null || scene != null;
    }

    /** The scene track being edited, or null for a route file. */
    public SceneRows sceneRows() {
        return scene;
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
        if (scene != null) return !tape.equals(scene.original());
        return loaded != null && (unsaved || !tape.equals(loaded.tape()) || !goals.equals(loaded.checks()) || !currentNativeFacts.equals(loaded.manifest().nativeFacts()));
    }

    /** The working goal program replay checks and Plan completes. */
    public com.openggf.route.GoalProgram goals() {
        return goals;
    }

    public PickedTarget pickedTarget() {
        return picked;
    }

    /** Live objects the last scan saw, for picking targets. */
    public List<RouteTapeJob.LiveObject> scannedObjects() {
        return scanned;
    }

    public boolean draft() {
        return loaded != null && loaded.manifest().draft();
    }

    /** The route as it would be saved: the file's launch, goals, provenance and facts over the working rows. */
    public RouteArtifact working() {
        if (loaded == null) return null;
        RouteManifest source = loaded.manifest();
        return new RouteArtifact(new RouteManifest(source.launch(), tape.hash(), tape.size(), goals,
                source.draft(), source.provenance(), source.qualification(), currentNativeFacts), tape);
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
        return result != null && resultFacts.equals(currentNativeFacts) && Objects.equals(result.content(), workingContent()) ? result : null;
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
        if (scene != null) return Qualification.SCENE;
        if (loaded == null) return Qualification.EMPTY;
        RouteTapeJob.Update current = result();
        boolean stamped = !unsaved && currentNativeFacts.equals(loaded.manifest().nativeFacts()) && working().engineQualified();
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
            case SCENE -> UiMessages.text(dirty() ? "editor.route_tape.qualification_scene_edited"
                    : "editor.route_tape.qualification_scene");
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
        if (scene != null) return List.of(fact(UiMessages.text("editor.route_tape.fact_scene_track"), scene.title()),
                fact(UiMessages.text("editor.route_tape.fact_scene_detail"), scene.detail()));
        if (loaded == null) return List.of();
        LaunchContract launch = loaded.launch();
        var facts = new ArrayList<String[]>();
        facts.add(fact(UiMessages.text("editor.route_tape.fact_rom"), launch.rom().game().toUpperCase(java.util.Locale.ROOT) + " "
                + launch.rom().crc32Hex()));
        if (launch.inlet() instanceof Inlet.Title) {
            facts.add(fact(UiMessages.text("editor.route_tape.field_host"), UiMessages.text("editor.route_tape.host_competition")));
        }
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
        NativeFacts facts = currentNativeFacts;
        var content = working().content();
        var lines = new ArrayList<String[]>();
        if (!facts.exportBlocker().isBlank()) lines.add(fact("PROVISIONAL", facts.exportBlocker()));
        lines.add(fact(UiMessages.text("editor.route_tape.fact_native_entry"), facts.entry()
                .map(entry -> UiMessages.text("editor.route_tape.native_entry_value",
                        Map.of("movie", entry.movie(), "rows", String.valueOf(entry.prefixRows()))))
                .orElse(UiMessages.text("editor.route_tape.native_entry_none"))));
        for(var inlet:facts.inlets()) lines.add(fact("Native inlet", "engine ["+inlet.engineFromRows()+","+inlet.anchor().engineAnchorRows()+") → "+inlet.anchor().movie()+" through "+inlet.anchor().prefixRows()));
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
            if(!stamp.coverage().isEmpty())lines.add(fact(UiMessages.text("editor.route_tape.fact_native_check"),String.join(", ",stamp.coverage())));
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
        var facts = currentNativeFacts;
        boolean observed = !facts.lag().isEmpty() || loaded.manifest().qualification().stream()
                .anyMatch(stamp -> stamp.scope() == ReplayScope.BIZHAWK);
        boolean current = facts.lag().stream().anyMatch(lag -> lag.content().equals(content))
                || loaded.manifest().qualification().stream()
                .anyMatch(stamp -> stamp.scope() == ReplayScope.BIZHAWK && stamp.content().equals(content));
        return observed && !current;
    }

    public String exportBlocker() {
        if (loaded == null) return "";
        if (!currentNativeFacts.exportBlocker().isBlank()) return currentNativeFacts.exportBlocker();
        if (loaded.launch().inlet() instanceof Inlet.Title && (currentNativeFacts.entry().isEmpty() ||
                currentNativeFacts.entry().get().anchorBoundary().isBlank())) return UiMessages.text("editor.route_tape.missing_anchors");
        if (!currentNativeFacts.equals(loaded.manifest().nativeFacts()) &&
                (result() == null || result().phase() != RouteTapeJob.Phase.PASSED)) return UiMessages.text("editor.route_tape.changed_anchors");
        return "";
    }

    public String exportSummary() {
        return !currentNativeFacts.exportBlocker().isBlank() ? UiMessages.text("editor.route_tape.export_blocked_short") : exportBlocker();
    }

    public String evidenceSummary() {
        if (loaded == null) return UiMessages.text("editor.route_tape.bizhawk_none");
        var content = working().content();
        return loaded.manifest().qualification().stream()
                .filter(q -> q.scope() == ReplayScope.BIZHAWK && q.content().equals(content))
                .reduce((a,b) -> b).map(q -> q.passed() ? "BIZHAWK · Passed" : "BIZHAWK · Diverged")
                .orElse(UiMessages.text("editor.route_tape.bizhawk_none"));
    }

    /** Presentation of a goal, shared by both fronts. Global predicates concern both pads. */
    public record GoalRow(String id, String player, String predicate) { }

    public List<GoalRow> goalRows() {
        return goals.legs().stream().map(leg -> new GoalRow(leg.id(),
                leg.goal() instanceof com.openggf.route.RouteGoal.Achieve a ? playerChip(a.condition()) : "Both",
                leg.goal() instanceof com.openggf.route.RouteGoal.Achieve a ? goalSummary(a.condition())
                        : UiMessages.text("editor.route_tape.goal_boundary"))).toList();
    }

    private String goalSummary(com.openggf.route.SamplePredicate predicate) {
        if (loaded != null && loaded.launch().inlet() instanceof Inlet.Title) {
            try { RouteCompetitionFacts.keys(predicate).forEach(RouteCompetitionFacts::requireKey); }
            catch (IllegalArgumentException bad) { return "Error: " + bad.getMessage(); }
        }
        return predicateText(predicate);
    }

    private static String playerChip(com.openggf.route.SamplePredicate predicate) {
        return switch (predicate) {
            case com.openggf.route.SamplePredicate.PlayerIn p -> p.player().name();
            case com.openggf.route.SamplePredicate.Settled p -> p.player().name();
            case com.openggf.route.SamplePredicate.FactEquals f -> factPlayer(f.key());
            case com.openggf.route.SamplePredicate.FactsEqual f -> factPlayer(f.left()).equals(factPlayer(f.right()))
                    ? factPlayer(f.left()) : "Both";
            case com.openggf.route.SamplePredicate.Riding p -> p.player().name();
            case com.openggf.route.SamplePredicate.Attached p -> p.player().name();
            case com.openggf.route.SamplePredicate.All all -> {
                var chips = all.clauses().stream().map(RouteTapeWorkspace::playerChip).distinct().toList();
                yield chips.size() == 1 ? chips.getFirst() : "Both";
            }
            default -> "Both";
        };
    }

    private static String factPlayer(String key) {
        if (key.startsWith("competition.p1.") || key.endsWith(".p1")) return "P1";
        if (key.startsWith("competition.p2.") || key.endsWith(".p2")) return "P2";
        return "Both";
    }

    /** Title programmes retain the inherited tape before the programme begins. */
    public int immutablePrefixRows() {
        return loaded != null && loaded.launch().inlet() instanceof Inlet.Title ? goals.startRow() : 0;
    }

    public void createCompetition(Path manifest, int entry) {
        if (!host.competitionAvailable()) throw new IllegalStateException(UiMessages.text("editor.route_tape.competition_unavailable"));
        var prefix = com.openggf.stageplay.route.StageplayCompetitionRouteHosts.menuPrefix(entry);
        create(manifest, com.openggf.stageplay.route.StageplayCompetitionRouteHosts.launch(), prefix.size());
        tape = prefix;
        goals = new com.openggf.route.GoalProgram(com.openggf.route.Guard.NONE, List.of(), prefix.size());
        loaded = working();
        cursor = prefix.size() - 1;
    }

    /** The goal program replay checks, one line per leg, in manifest words. */
    public List<String[]> goalFacts() {
        if (loaded == null) return List.of();
        var program = goals;
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
            case com.openggf.route.SamplePredicate.FactsEqual equal -> equal.left() + " = " + equal.right();
            case com.openggf.route.SamplePredicate.FactEquals fact -> fact.key().equals("competition.flip.visible") && fact.value() == 1
                    ? "flip visible" : fact.key() + " = " + fact.value();
            case com.openggf.route.SamplePredicate.PlayerIn in -> in.player() + " in (" + in.region().left() + "," + in.region().top()
                    + ")–(" + in.region().right() + "," + in.region().bottom() + ")";
            case com.openggf.route.SamplePredicate.Settled settled -> UiMessages.text("editor.route_tape.goal_settled",
                    Map.of("player", settled.player().name()));
            case com.openggf.route.SamplePredicate.LevelIs level -> UiMessages.text("editor.route_tape.goal_level",
                    Map.of("zone", String.valueOf(level.zone()), "act", String.valueOf(level.act() + 1)));
            case com.openggf.route.SamplePredicate.All all -> String.join(" + ",
                    all.clauses().stream().map(RouteTapeWorkspace::predicateText).toList());
            case com.openggf.route.SamplePredicate.Riding riding -> UiMessages.text("editor.route_tape.goal_riding",
                    Map.of("player", riding.player().name(), "target", targetText(riding.target())));
            case com.openggf.route.SamplePredicate.Attached attached -> UiMessages.text("editor.route_tape.goal_attached",
                    Map.of("player", attached.player().name(), "kind", attached.kind().name().toLowerCase(java.util.Locale.ROOT),
                            "target", targetText(attached.target())));
            case com.openggf.route.SamplePredicate.PhaseIs phase -> UiMessages.text("editor.route_tape.goal_phase",
                    Map.of("target", targetText(phase.target()), "phase", phase.phase()));
            case com.openggf.route.SamplePredicate.Defeated defeated -> UiMessages.text("editor.route_tape.goal_defeated",
                    Map.of("target", targetText(defeated.target())));
        };
    }

    /** A target in words: a placed object by layout index, or a live object by type. */
    public static String targetText(com.openggf.route.ObjectTarget target) {
        return switch (target) {
            case com.openggf.route.ObjectTarget.Placement p -> UiMessages.text("editor.route_tape.target_placed", Map.of(
                    "index", String.valueOf(p.key().layoutIndex()), "id", String.format("%02X", p.key().objectId())));
            case com.openggf.route.ObjectTarget.Typed t -> UiMessages.text("editor.route_tape.target_typed",
                    Map.of("type", t.typeKey(), "capability", t.capability().name().toLowerCase(java.util.Locale.ROOT)));
            case com.openggf.route.ObjectTarget.Instance i -> UiMessages.text("editor.route_tape.target_instance",
                    Map.of("kind", i.key().kind(), "slot", String.valueOf(i.key().slot())));
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
                case NATIVE_FACTS -> nativeAnchorForm();
                case OPEN -> beforeReplace(this::openForm);
                case APPLY_ROWS -> applySceneRows();
                case NEW -> beforeReplace(this::newForm);
                case SAVE -> save();
                case SAVE_DRAFT -> saveDraft();
                case REPAIR -> repairForm();
                case PLAN -> planForm();
                case GOALS -> goalsMenu();
                case ADD_LEG -> legForm();
                case EDIT_GOALS -> goalsTextForm();
                case SCAN -> startScan();
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
                case EXIT -> exit();
            }
        } catch (UncheckedIOException | IllegalArgumentException | IllegalStateException failure) {
            // Route I/O and the workspace's own validation messages; anything else is an engine bug and propagates.
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
        scene = null;
        stem = stemOf(absolute);
        unsaved = false;
        loaded = candidate;
        currentNativeFacts = candidate.manifest().nativeFacts();
        tape = candidate.tape();
        goals = candidate.checks();
        picked = null;
        scanned = List.of();
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

    /** Opens a scene's frozen input track in the timeline; route files stay closed until reopened. */
    public void openSceneRows(SceneRows rows) {
        if (loaded != null && dirty()) throw new IllegalStateException(UiMessages.text("editor.route_tape.route_unsaved_first"));
        cancelJob();
        scene = Objects.requireNonNull(rows, "rows");
        loaded = null;
        currentNativeFacts = NativeFacts.NONE;
        stem = null;
        unsaved = false;
        tape = rows.original();
        goals = com.openggf.route.GoalProgram.NONE;
        undo.clear();
        redo.clear();
        result = null;
        pathResult = null;
        repair = null;
        cursor = 0;
        first = 0;
        mark = -1;
        active = true;
        status = UiMessages.text("editor.route_tape.status_scene_rows", Map.of("track", rows.title(),
                "rows", String.valueOf(tape.size())));
    }

    /** Returns edited scene rows to their scene (one scene undo step), then goes back to it. */
    private void applySceneRows() {
        if (scene == null) throw new IllegalStateException(UiMessages.text("editor.route_tape.no_scene_rows"));
        if (tape.size() == 0) throw new IllegalStateException(UiMessages.text("editor.route_tape.empty_tape"));
        SceneRows rows = scene;
        rows.apply().accept(tape);
        scene = null;
        deactivate();
        rows.back().run();
    }

    /** Leaves for the level editor, or back to the scene; unapplied scene rows ask first. */
    private void exit() {
        if (scene == null) {
            deactivate();
            return;
        }
        SceneRows rows = scene;
        Runnable back = () -> {
            scene = null;
            loaded = null;
        currentNativeFacts = NativeFacts.NONE;
            tape = InputTape.empty();
            deactivate();
            rows.back().run();
        };
        if (!dirty()) {
            back.run();
            return;
        }
        menu.showActions(UiMessages.text("editor.route_tape.title_unapplied"), List.of(
                action(Action.APPLY_ROWS),
                new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_discard"),
                        UiMessages.text("editor.route_tape.note_discard"), back),
                new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_keep_editing"), () -> { })));
    }

    /** Re-reads the saved route after a save, keeping cursor and edit history. */
    private void reload(Path saved) throws IOException {
        RouteArtifact candidate = RouteFiles.readDraft(saved);
        stem = stemOf(saved.toAbsolutePath().normalize());
        unsaved = false;
        loaded = candidate;
        currentNativeFacts = candidate.manifest().nativeFacts();
        goals = candidate.checks();
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
        scene = null;
        var fresh = RouteArtifact.of(launch, rowsTape, new com.openggf.route.Provenance("openggf-editor", "", "", List.of()));
        loaded = new RouteArtifact(new RouteManifest(launch, rowsTape.hash(), rowsTape.size(), fresh.checks(), true,
                fresh.manifest().provenance(), List.of(), fresh.manifest().nativeFacts()), rowsTape);
        currentNativeFacts = fresh.manifest().nativeFacts();
        tape = rowsTape;
        goals = com.openggf.route.GoalProgram.NONE;
        picked = null;
        scanned = List.of();
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
        var fields = new ArrayList<>(List.of(
                new EditorProjectMenu.Field("path", UiMessages.text("editor.route_tape.field_manifest"),
                        host.initialDirectory().resolve("new-route" + RouteFiles.MANIFEST_SUFFIX).toString(), MenuTextEditor.Mode.PATH),
                new EditorProjectMenu.Field("game", UiMessages.text("editor.route_tape.field_game"), game, List.of("s1", "s2", "s3k")),
                new EditorProjectMenu.Field("zone", UiMessages.text("editor.route_tape.field_zone"), String.valueOf(zone), MenuTextEditor.Mode.TEXT, 3),
                new EditorProjectMenu.Field("act", UiMessages.text("editor.route_tape.field_act"), String.valueOf(act + 1), MenuTextEditor.Mode.TEXT, 2),
                new EditorProjectMenu.Field("main", UiMessages.text("editor.route_tape.field_main"), "sonic", MenuTextEditor.Mode.TEXT, 24),
                new EditorProjectMenu.Field("sidekick", UiMessages.text("editor.route_tape.field_sidekick"), "tails", MenuTextEditor.Mode.TEXT, 24),
                new EditorProjectMenu.Field("width", UiMessages.text("editor.route_tape.field_width"), "320", MenuTextEditor.Mode.TEXT, 4),
                new EditorProjectMenu.Field("vint", UiMessages.text("editor.route_tape.field_vint"), "0", MenuTextEditor.Mode.TEXT, 6),
                new EditorProjectMenu.Field("rows", UiMessages.text("editor.route_tape.field_count"), "60", MenuTextEditor.Mode.TEXT, 7)));
        fields.addFirst(new EditorProjectMenu.Field("host", UiMessages.text("editor.route_tape.field_host"),
                UiMessages.text("editor.route_tape.host_level"), host.competitionAvailable()
                ? List.of(UiMessages.text("editor.route_tape.host_level"), UiMessages.text("editor.route_tape.host_competition"))
                : List.of(UiMessages.text("editor.route_tape.host_level"))));
        if (host.competitionAvailable()) fields.add(1, new EditorProjectMenu.Field("entry",
                UiMessages.text("editor.route_tape.field_entry"), "EHZ", List.of("EHZ", "MCZ", "CNZ", "Special Stage")));
        menu.showForm(UiMessages.text("editor.route_tape.title_new"), fields,
                UiMessages.text("editor.route_tape.action_create"), values -> {
                    if (values.get("host").equals(UiMessages.text("editor.route_tape.host_competition"))) {
                        createCompetition(Path.of(values.get("path")), List.of("EHZ", "MCZ", "CNZ", "Special Stage").indexOf(values.get("entry")));
                        return;
                    }
                    String chosen = values.get("game");
                    var level = new LevelId(chosen, Integer.parseInt(values.get("zone").strip()),
                            Integer.parseInt(values.get("act").strip()) - 1);
                    var launch = LaunchContract.coldLevel(com.openggf.route.RomIdentity.standard(chosen), level,
                            new com.openggf.route.Roster(values.get("main").strip(), values.get("sidekick").strip()),
                            Integer.parseInt(values.get("width").strip()), Integer.parseInt(values.get("vint").strip()));
                    create(Path.of(values.get("path")), launch, Integer.parseInt(values.get("rows").strip()));
                });
        menu.setSection(UiMessages.text("editor.route_tape.level_settings"), "game", "zone", "act", "main", "sidekick", "width", "vint", "rows");
        menu.setHelp(UiMessages.text("editor.route_tape.setup_hint"), UiMessages.text("editor.route_tape.subtitle_new"));
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
        if (scene != null && dirty()) {
            menu.showActions(UiMessages.text("editor.route_tape.title_unapplied"), List.of(
                    new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_discard"),
                            UiMessages.text("editor.route_tape.note_discard"), next),
                    new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_keep_editing"), () -> { })));
            return;
        }
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
            var written = RouteEditorFiles.draft(stem, working());
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
        jobFacts = currentNativeFacts;
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
        if (!jobFacts.equals(currentNativeFacts)) {
            status = "Route anchors changed while the worker ran; replay again.";
            return;
        }
        resultFacts = currentNativeFacts;
        switch (next.phase()) {
            case SOLVED, STOPPED -> {
                if (next.kind() == RouteTapeJob.Kind.PLAN) finishPlan(next);
                else finishRepair(next);
            }
            case SCANNED -> finishScan(next);
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
        requireRoute();
        requireRows();
        String self = UiMessages.text("editor.route_tape.reference_self"), other = UiMessages.text("editor.route_tape.reference_other");
        String recorded = UiMessages.text("editor.route_tape.reference_track");
        menu.showForm(UiMessages.text("editor.route_tape.title_repair"), List.of(
                new EditorProjectMenu.Field("first", UiMessages.text("editor.route_tape.field_first_editable"),
                        String.valueOf(cursor), MenuTextEditor.Mode.TEXT, 8),
                new EditorProjectMenu.Field("reference", UiMessages.text("editor.route_tape.field_reference"),
                        unsaved ? other : self, List.of(self, other, recorded)),
                new EditorProjectMenu.Field("path", UiMessages.text("editor.route_tape.field_reference_path"),
                        host.initialDirectory() + "/", MenuTextEditor.Mode.PATH),
                new EditorProjectMenu.Field("rows", UiMessages.text("editor.route_tape.field_reference_rows"), "",
                        MenuTextEditor.Mode.TEXT, 8),
                new EditorProjectMenu.Field("movie", UiMessages.text("editor.route_tape.field_track_movie"),
                        host.initialDirectory() + "/", MenuTextEditor.Mode.PATH),
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
                    String to = values.get("to").strip();
                    var bounds = new RouteTapeJob.RepairBounds(Integer.parseInt(values.get("first").strip()),
                            Integer.parseInt(values.get("authored").strip()), Integer.parseInt(values.get("windows").strip()),
                            Integer.parseInt(values.get("from").strip()), to.isEmpty() ? -1 : Integer.parseInt(to));
                    if (values.get("reference").equals(recorded)) {
                        startRepair(new RouteTapeJob.RecordedTrack(Path.of(values.get("path").strip()),
                                Path.of(values.get("movie").strip())), bounds);
                        return;
                    }
                    startRepair(values.get("reference").equals(self) ? savedReference()
                            : reference(Path.of(values.get("path").strip()), values.get("rows").strip()), bounds);
                }));
        menu.setSubtitle(UiMessages.text("editor.route_tape.subtitle_repair"));
    }

    /** Starts a repair of the working rows along a track recorded by an earlier replay of its movie. */
    public void startRepair(RouteTapeJob.RecordedTrack recorded, RouteTapeJob.RepairBounds bounds) throws IOException {
        submitRepair(RouteTapeJob.Request.repair(working(), recorded, bounds), bounds);
    }

    /** Starts a repair of the working rows against {@code reference}. */
    public void startRepair(RouteArtifact reference, RouteTapeJob.RepairBounds bounds) throws IOException {
        submitRepair(RouteTapeJob.Request.repair(working(), reference, bounds), bounds);
    }

    private void submitRepair(RouteTapeJob.Request request, RouteTapeJob.RepairBounds bounds) throws IOException {
        requireRows();
        if (jobRunning()) throw new IllegalStateException(UiMessages.text("editor.route_tape.job_already_running"));
        if (bounds.firstEditable() < immutablePrefixRows() || bounds.firstEditable() > tape.size()) {
            throw new IllegalArgumentException(UiMessages.text("editor.route_tape.repair_first_range"));
        }
        repairFrom = workingContent();
        jobFacts = currentNativeFacts;
        host.startJob(request);
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

    // ---------------------------------------------------------------- goals, scan and plan

    private void goalsMenu() {
        requireRoute();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(Action.ADD_LEG));
        actions.add(action(Action.EDIT_GOALS));
        for (int i=0;i<goals.legs().size();i++) {
            int index=i;
            actions.add(new EditorProjectMenu.Action("Edit " + goals.legs().get(i).id(), () -> editGoal(index)));
        }
        if (!goals.legs().isEmpty()) {
            var last = goals.legs().getLast();
            actions.add(new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_remove_leg",
                    Map.of("leg", last.id())), () -> setGoals(new com.openggf.route.GoalProgram(goals.routeGuard(),
                    goals.legs().subList(0, goals.legs().size() - 1), goals.startRow()))));
        }
        boolean safe = goals.routeGuard().equals(com.openggf.route.Guard.safe());
        actions.add(new EditorProjectMenu.Action(UiMessages.text(safe ? "editor.route_tape.action_guard_off"
                : "editor.route_tape.action_guard_safe"), () -> setGoals(new com.openggf.route.GoalProgram(
                safe ? com.openggf.route.Guard.NONE : com.openggf.route.Guard.safe(), goals.legs(), goals.startRow()))));
        if (!jobRunning()) {
            actions.add(action(Action.SCAN));
            if (!goals.legs().isEmpty()) actions.add(action(Action.PLAN));
        }
        actions.add(new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_back"), () -> { }));
        menu.showActions(UiMessages.text("editor.route_tape.title_goals", Map.of("legs", String.valueOf(goals.legs().size()))),
                actions);
    }

    /** Opens one leg through the same typed fact editor or lossless programme field. */
    public void editGoal(int index) {
        requireRoute();
        var old=goals.legs().get(index);
        com.openggf.route.SamplePredicate predicate = old.goal() instanceof com.openggf.route.RouteGoal.Achieve a ? a.condition() : null;
        String key = predicate instanceof com.openggf.route.SamplePredicate.FactEquals f ? f.key()
                : predicate instanceof com.openggf.route.SamplePredicate.FactsEqual f ? f.left() : null;
        if(key != null) {
            boolean equal = predicate instanceof com.openggf.route.SamplePredicate.FactsEqual;
            var fields=new ArrayList<EditorProjectMenu.Field>();
            fields.add(new EditorProjectMenu.Field("id",UiMessages.text("editor.route_tape.field_leg_id"),old.id(),MenuTextEditor.Mode.TEXT,32));
            fields.add(new EditorProjectMenu.Field("fact",UiMessages.text("editor.route_tape.fact_key"),key,factChoices(key)));
            if(equal) fields.add(new EditorProjectMenu.Field("otherFact",UiMessages.text("editor.route_tape.other_fact"),
                    ((com.openggf.route.SamplePredicate.FactsEqual)predicate).right(),factChoices(((com.openggf.route.SamplePredicate.FactsEqual)predicate).right())));
            else fields.add(new EditorProjectMenu.Field("factValue",UiMessages.text("editor.route_tape.fact_value"),String.valueOf(
                    ((com.openggf.route.SamplePredicate.FactEquals)predicate).value()),MenuTextEditor.Mode.TEXT,12));
            fields.add(new EditorProjectMenu.Field("rows",UiMessages.text("editor.route_tape.field_leg_rows"),String.valueOf(old.maxRows()),MenuTextEditor.Mode.TEXT,9));
            menu.showForm("Edit " + old.id(),fields,UiMessages.text("editor.route_tape.action_apply"), values -> replaceGoal(index,
                    new com.openggf.route.Leg(values.get("id"),new com.openggf.route.RouteGoal.Achieve(
                            factPredicate(values.get("fact"),values.get("factValue"),equal?values.get("otherFact"):null)),
                            old.guard(),Integer.parseInt(values.get("rows")))));
            try { RouteCompetitionFacts.keys(predicate).forEach(RouteCompetitionFacts::requireKey); }
            catch (IllegalArgumentException invalid) { menu.report(invalid); }
        } else {
            String text=com.openggf.route.RouteCodec.formatGoals(new com.openggf.route.GoalProgram(
                    com.openggf.route.Guard.NONE,List.of(old),goals.startRow()));
            menu.showForm("Edit " + old.id(),List.of(new EditorProjectMenu.Field("leg","Leg",text,MenuTextEditor.Mode.TEXT,8192)),
                    UiMessages.text("editor.route_tape.action_apply"),values -> io(() -> {
                        var parsed=com.openggf.route.RouteCodec.parseGoals(values.get("leg"));
                        if(parsed.legs().size()!=1) throw new IllegalArgumentException("Edit exactly one leg");
                        replaceGoal(index,parsed.legs().getFirst());
                    }));
            menu.setMultilinePreview("leg");
        }
    }

    private static List<String> factChoices(String current) {
        if (RouteCompetitionFacts.KEYS.contains(current)) return RouteCompetitionFacts.KEYS;
        var choices=new ArrayList<>(RouteCompetitionFacts.KEYS); choices.addFirst(current);
        return List.copyOf(choices);
    }

    private void replaceGoal(int index, com.openggf.route.Leg replacement) {
        var copy=new ArrayList<>(goals.legs()); copy.set(index,replacement);
        setGoals(new com.openggf.route.GoalProgram(goals.routeGuard(),copy,goals.startRow()));
    }

    /** The goal program as the codec's text, edited whole; parsing errors leave the goals unchanged. */
    private void goalsTextForm() {
        requireRoute();
        String text = com.openggf.route.RouteCodec.formatGoals(goals);
        // The shared text field holds at most 8192 characters; longer programs are edited leg by leg.
        if (text.length() > 8192) throw new IllegalStateException(UiMessages.text("editor.route_tape.goals_text_too_long"));
        menu.showForm(UiMessages.text("editor.route_tape.action_edit_goals"), List.of(new EditorProjectMenu.Field("goals",
                UiMessages.text("editor.route_tape.field_goals"), text, MenuTextEditor.Mode.TEXT, 8192)),
                UiMessages.text("editor.route_tape.action_apply"),
                values -> io(() -> setGoals(com.openggf.route.RouteCodec.parseGoals(values.get("goals")))));
        menu.setMultilinePreview("goals");
        menu.setSubtitle(UiMessages.text("editor.route_tape.subtitle_goals_text"));
    }

    private static final List<String> LEG_KINDS = List.of("reach", "board", "ride", "attach", "hold", "await-phase",
            "defeat", "cross", "fact-equals", "facts-equal");

    private void legForm() {
        requireRoute();
        int[] at = cursorPosition();
        var targets = new ArrayList<String>();
        if (picked != null) targets.add(UiMessages.text("editor.route_tape.target_choice_picked", Map.of("target", picked.description())));
        targets.add(UiMessages.text("editor.route_tape.target_choice_placed"));
        String phase = picked != null && !picked.phases().isEmpty() ? picked.phases().getFirst() : "";
        menu.showForm(UiMessages.text("editor.route_tape.action_add_leg"), List.of(
                new EditorProjectMenu.Field("id", UiMessages.text("editor.route_tape.field_leg_id"),
                        "leg" + (goals.legs().size() + 1), MenuTextEditor.Mode.TEXT, 32),
                new EditorProjectMenu.Field("goal", UiMessages.text("editor.route_tape.field_leg_goal"), "reach", LEG_KINDS),
                new EditorProjectMenu.Field("player", UiMessages.text("editor.route_tape.field_leg_player"), "P1",
                        List.of("P1", "P2", "Both")),
                new EditorProjectMenu.Field("fact", UiMessages.text("editor.route_tape.fact_key"), "competition.slot.inUse", RouteCompetitionFacts.KEYS),
                new EditorProjectMenu.Field("factValue", UiMessages.text("editor.route_tape.fact_value"), "1", MenuTextEditor.Mode.TEXT, 12),
                new EditorProjectMenu.Field("otherFact", UiMessages.text("editor.route_tape.other_fact"), "competition.zoneWins.p2", RouteCompetitionFacts.KEYS),
                new EditorProjectMenu.Field("target", UiMessages.text("editor.route_tape.field_leg_target"), targets.getFirst(),
                        List.copyOf(targets)),
                new EditorProjectMenu.Field("x", UiMessages.text("editor.route_tape.field_leg_x"), String.valueOf(at[0]),
                        MenuTextEditor.Mode.TEXT, 6),
                new EditorProjectMenu.Field("y", UiMessages.text("editor.route_tape.field_leg_y"), String.valueOf(at[1]),
                        MenuTextEditor.Mode.TEXT, 6),
                new EditorProjectMenu.Field("radius", UiMessages.text("editor.route_tape.field_leg_radius"), "32",
                        MenuTextEditor.Mode.TEXT, 5),
                new EditorProjectMenu.Field("interaction", UiMessages.text("editor.route_tape.field_leg_interaction"), "GRAB",
                        java.util.Arrays.stream(com.openggf.route.InteractionKind.values()).map(Enum::name).toList()),
                new EditorProjectMenu.Field("phase", UiMessages.text("editor.route_tape.field_leg_phase"), phase,
                        MenuTextEditor.Mode.TEXT, 32),
                new EditorProjectMenu.Field("until", UiMessages.text("editor.route_tape.field_leg_until"), "phase",
                        List.of("phase", "region")),
                new EditorProjectMenu.Field("rows", UiMessages.text("editor.route_tape.field_leg_rows"), "600",
                        MenuTextEditor.Mode.TEXT, 7)),
                UiMessages.text("editor.route_tape.action_add"), values -> setGoals(appendLeg(values, targets)));
        menu.setSection(UiMessages.text("editor.route_tape.movement_objects"), "target", "x", "y", "radius", "interaction", "phase", "until");
        menu.setHelp(UiMessages.text("editor.route_tape.leg_hint"), UiMessages.text("editor.route_tape.subtitle_leg") +
                " " + UiMessages.text("editor.route_tape.fact_help"));
    }

    private com.openggf.route.GoalProgram appendLeg(Map<String, String> values, List<String> targets) {
        String id = values.get("id").strip();
        var player = values.get("player").equals("Both") ? com.openggf.route.PlayerSlot.P1 : com.openggf.route.PlayerSlot.valueOf(values.get("player"));
        int x = Integer.parseInt(values.get("x").strip()), y = Integer.parseInt(values.get("y").strip());
        int radius = Integer.parseInt(values.get("radius").strip());
        int rows = Integer.parseInt(values.get("rows").strip());
        var region = com.openggf.route.Region.around(x, y, radius, radius);
        java.util.function.Supplier<com.openggf.route.ObjectTarget> target = () ->
                picked != null && values.get("target").equals(targets.getFirst()) ? picked.target() : placedNear(x, y);
        String phase = values.get("phase").strip();
        java.util.function.Supplier<com.openggf.route.SamplePredicate> until = () -> values.get("until").equals("region")
                ? new com.openggf.route.SamplePredicate.PlayerIn(player, region)
                : new com.openggf.route.SamplePredicate.PhaseIs(target.get(), requirePhase(phase));
        var interaction = com.openggf.route.InteractionKind.valueOf(values.get("interaction"));
        com.openggf.route.Leg leg = switch (values.get("goal")) {
            case "fact-equals" -> com.openggf.route.Leg.of(id, new com.openggf.route.RouteGoal.Achieve(
                    factPredicate(values.get("fact"), values.get("factValue"), null)), rows);
            case "facts-equal" -> com.openggf.route.Leg.of(id, new com.openggf.route.RouteGoal.Achieve(
                    factPredicate(values.get("fact"), null, values.get("otherFact"))), rows);
            case "reach" -> com.openggf.route.RouteGoals.reach(id, player, region, rows);
            case "board" -> com.openggf.route.RouteGoals.board(id, player, target.get(), rows);
            case "ride" -> com.openggf.route.RouteGoals.ride(id, player, target.get(), until.get(), rows);
            case "attach" -> com.openggf.route.RouteGoals.attach(id, player, target.get(), interaction, rows);
            case "hold" -> com.openggf.route.RouteGoals.hold(id, player, target.get(), interaction, until.get(), rows);
            case "await-phase" -> com.openggf.route.RouteGoals.awaitPhase(id, target.get(), requirePhase(phase), rows);
            case "defeat" -> com.openggf.route.RouteGoals.defeat(id, target.get(), rows);
            case "cross" -> com.openggf.route.RouteGoals.cross(id, rows);
            default -> throw new IllegalArgumentException(values.get("goal"));
        };
        if (values.get("player").equals("Both") && !values.get("goal").startsWith("fact") &&
                leg.goal() instanceof com.openggf.route.RouteGoal.Achieve) {
            var other = new java.util.HashMap<>(values); other.put("player", "P2");
            var p2 = appendLeg(other, targets).legs().getLast();
            leg = new com.openggf.route.Leg(id, new com.openggf.route.RouteGoal.Achieve(
                    new com.openggf.route.SamplePredicate.All(List.of(
                            ((com.openggf.route.RouteGoal.Achieve)leg.goal()).condition(),
                            ((com.openggf.route.RouteGoal.Achieve)p2.goal()).condition()))),leg.guard(),rows);
        }
        var legs = new ArrayList<>(goals.legs());
        legs.add(leg);
        return new com.openggf.route.GoalProgram(goals.routeGuard(), legs, goals.startRow());
    }

    private com.openggf.route.SamplePredicate factPredicate(String key, String value, String right) {
        RouteCompetitionFacts.requireKey(key);
        if (right != null) RouteCompetitionFacts.requireKey(right);
        var observed = observedFacts();
        if (!observed.isEmpty()) {
            if (!observed.containsKey(key)) throw new IllegalArgumentException("Missing fact at cursor: " + key);
            if (right != null && !observed.containsKey(right)) throw new IllegalArgumentException("Missing fact at cursor: " + right);
        }
        return right == null ? new com.openggf.route.SamplePredicate.FactEquals(key,Integer.parseInt(value.strip()))
                : new com.openggf.route.SamplePredicate.FactsEqual(key,right);
    }

    public Map<String,Integer> observedFacts() {
        return job != null && Objects.equals(job.content(),workingContent()) &&
                (job.kind() == RouteTapeJob.Kind.REPLAY || job.kind() == RouteTapeJob.Kind.SCAN)
                && job.row() == cursor + 1 ? job.facts() : Map.of();
    }

    private static String requirePhase(String phase) {
        if (phase.isEmpty()) throw new IllegalArgumentException(UiMessages.text("editor.route_tape.phase_needed"));
        return phase;
    }

    /** The placed object of the editor's level nearest {@code (x, y)}, as a placement target. */
    private com.openggf.route.ObjectTarget placedNear(int x, int y) {
        if (!levelMatches()) throw new IllegalStateException(UiMessages.text("editor.route_tape.placed_needs_level"));
        var level = ((Inlet.ColdLevel) loaded.launch().inlet()).level();
        return host.placements().stream()
                .min(java.util.Comparator.comparingLong(p -> (long) (p.x() - x) * (p.x() - x) + (long) (p.y() - y) * (p.y() - y)))
                .map(p -> (com.openggf.route.ObjectTarget) new com.openggf.route.ObjectTarget.Placement(
                        new com.openggf.route.PlacementKey(level.zone(), level.act(), p.layoutIndex(), p.objectId(), p.subtype())))
                .orElseThrow(() -> new IllegalStateException(UiMessages.text("editor.route_tape.no_placements")));
    }

    /** P1's observed centre after the cursor row, else the origin; a convenient default target point. */
    private int[] cursorPosition() {
        RouteTapeOverlay path = overlay();
        RouteTapeOverlay.Point point = path == null ? null : path.at(cursor);
        return point == null ? new int[] {0, 0} : new int[] {point.x(), point.y()};
    }

    /** Lists the live objects (placed or spawned at runtime) after the cursor row, for picking a target. */
    private void startScan() {
        requireRoute();
        if (jobRunning()) throw new IllegalStateException(UiMessages.text("editor.route_tape.job_already_running"));
        jobFacts = currentNativeFacts;
        io(() -> host.startJob(RouteTapeJob.Request.scan(working(), Math.min(cursor + 1, tape.size()))));
        job = host.pollJob();
        status = UiMessages.text("editor.route_tape.status_scanning", Map.of("row", String.valueOf(cursor)));
    }

    private void finishScan(RouteTapeJob.Update next) {
        scanned = next.objects();
        if (scanned.isEmpty()) {
            status = UiMessages.text("editor.route_tape.scan_none", Map.of("row", String.valueOf(next.row() - 1)));
            return;
        }
        var actions = new ArrayList<EditorProjectMenu.Action>();
        for (var object : scanned) {
            actions.add(new EditorProjectMenu.Action(objectText(object), String.join(", ", object.capabilities())
                    + (object.phase().isEmpty() ? "" : " · " + object.phase()), () -> pick(object)));
        }
        actions.add(new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_back"), () -> { }));
        menu.showActions(UiMessages.text("editor.route_tape.title_scan", Map.of("row", String.valueOf(next.row() - 1))), actions);
        status = UiMessages.text("editor.route_tape.status_scanned", Map.of("count", String.valueOf(scanned.size())));
    }

    private static String objectText(RouteTapeJob.LiveObject object) {
        String x = String.valueOf(object.x()), y = String.valueOf(object.y());
        return object.placed()
                ? UiMessages.text("editor.route_tape.object_placed", Map.of("type", object.typeKey(), "x", x, "y", y,
                        "index", String.valueOf(object.layoutIndex())))
                : UiMessages.text("editor.route_tape.object_runtime", Map.of("type", object.typeKey(), "x", x, "y", y));
    }

    /** Placed objects become placement targets; runtime objects a typed target near where they were seen. */
    public void pick(RouteTapeJob.LiveObject object) {
        com.openggf.route.ObjectTarget target = object.placed()
                ? new com.openggf.route.ObjectTarget.Placement(new com.openggf.route.PlacementKey(object.zone(), object.act(),
                        object.layoutIndex(), object.objectId(), object.subtype()))
                : new com.openggf.route.ObjectTarget.Typed(object.typeKey(),
                        com.openggf.route.Capability.valueOf(object.capabilities().getFirst()),
                        java.util.Optional.of(com.openggf.route.Region.around(object.x(), object.y(), 64, 64)));
        picked = new PickedTarget(target, objectText(object), object.phaseNames());
        status = UiMessages.text("editor.route_tape.status_picked", Map.of("target", picked.description()));
    }

    private void planForm() {
        requireRows();
        requireRoute();
        if (goals.legs().isEmpty()) throw new IllegalStateException(UiMessages.text("editor.route_tape.plan_needs_legs"));
        menu.showForm(UiMessages.text("editor.route_tape.title_plan"), List.of(new EditorProjectMenu.Field("from",
                UiMessages.text("editor.route_tape.field_plan_from"), String.valueOf(Math.max(cursor, immutablePrefixRows())), MenuTextEditor.Mode.TEXT, 8)),
                UiMessages.text("editor.route_tape.action_plan"), values -> io(() ->
                        startPlan(Integer.parseInt(values.get("from").strip()))));
        menu.setSubtitle(UiMessages.text("editor.route_tape.subtitle_plan"));
    }

    /** Plans rows after the kept prefix {@code [0, from)} that complete the working goals. */
    public void startPlan(int from) throws IOException {
        requireRoute();
        if (jobRunning()) throw new IllegalStateException(UiMessages.text("editor.route_tape.job_already_running"));
        if (goals.legs().isEmpty()) throw new IllegalStateException(UiMessages.text("editor.route_tape.plan_needs_legs"));
        if (from < immutablePrefixRows() || from > tape.size()) throw new IllegalArgumentException(UiMessages.text("editor.route_tape.repair_first_range"));
        repairFrom = workingContent();
        jobFacts = currentNativeFacts;
        host.startJob(RouteTapeJob.Request.plan(working(), from));
        job = host.pollJob();
        status = UiMessages.text("editor.route_tape.status_planning");
    }

    private void finishPlan(RouteTapeJob.Update next) {
        if (next.phase() == RouteTapeJob.Phase.STOPPED) {
            repair = next;
            status = repairText();
            return;
        }
        if (!Objects.equals(repairFrom, workingContent())) {
            status = UiMessages.text("editor.route_tape.repair_stale");
            return;
        }
        var planned = next.planned();
        change(planned.tape(), planned.checks());
        repair = null;
        result = new RouteTapeJob.Update(next.generation(), RouteTapeJob.Phase.PASSED, RouteTapeJob.Kind.PLAN, 1, 1,
                planned.tape().size(), planned.tape().size(), next.message(), planned.content().toString(),
                planned.tape().size(), next.path(), null);
        pathResult = result.path() == null ? pathResult : result;
        status = UiMessages.text("editor.route_tape.status_planned", Map.of("rows", String.valueOf(planned.tape().size())));
    }

    private void nativeAnchorForm() {
        requireRoute();
        if (jobRunning()) throw new IllegalStateException(UiMessages.text("editor.route_tape.job_already_running"));
        var entry = currentNativeFacts.entry();
        menu.showForm(UiMessages.text("editor.route_tape.native_anchors"), List.of(
                new EditorProjectMenu.Field("movie", UiMessages.text("editor.route_tape.native_witness"), entry.map(NativeFacts.NativeEntry::movie).orElse(""), MenuTextEditor.Mode.PATH),
                new EditorProjectMenu.Field("nativeRows", UiMessages.text("editor.route_tape.native_rows"), entry.map(e->String.valueOf(e.prefixRows())).orElse("0"), MenuTextEditor.Mode.TEXT, 9),
                new EditorProjectMenu.Field("engineRows", UiMessages.text("editor.route_tape.engine_rows"), entry.map(e->String.valueOf(e.engineAnchorRows())).orElse("0"), MenuTextEditor.Mode.TEXT, 9),
                new EditorProjectMenu.Field("boundary", UiMessages.text("editor.route_tape.anchor_boundary"), entry.map(NativeFacts.NativeEntry::anchorBoundary).orElse("first-ordinary-level-lfc-zero"),
                        List.of("first-ordinary-level-lfc-zero"))), UiMessages.text("editor.route_tape.apply_anchors"), values -> io(() -> {
                    Path witness = RouteEditorFiles.resolveWitness(manifestPath(), values.get("movie"));
                    int nativeRows=Integer.parseInt(values.get("nativeRows")), engineRows=Integer.parseInt(values.get("engineRows"));
                    if(nativeRows < 1 || engineRows < 1 || engineRows > tape.size()) throw new IllegalArgumentException("Anchor rows outside the tape");
                    var prefix = RouteFiles.inputPrefix(witness,nativeRows);
                    var previous=currentNativeFacts;
                    var facts = new NativeFacts(Optional.of(new NativeFacts.NativeEntry(witness.toString(), nativeRows,
                            prefix.hash(), engineRows, values.get("boundary"))), previous.lag(), previous.exportBlocker(),previous.inlets());
                    undo.push(new State(tape, goals, currentNativeFacts)); redo.clear();
                    currentNativeFacts=facts;
                    result=null;
                }));
        menu.setHelp(UiMessages.text("editor.route_tape.anchor_hint"), UiMessages.text("editor.route_tape.anchor_help"));
    }

    private void exportForm() {
        requireRoute();
        if (!exportBlocker().isBlank()) throw new IllegalStateException(exportBlocker());
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
        if (!exportBlocker().isBlank()) throw new IOException(exportBlocker());
        Path target = destination.toAbsolutePath().normalize();
        if (Files.exists(target)) throw new IOException(UiMessages.text("editor.route_tape.export_exists"));
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        RouteEditorFiles.export(working(), manifestPath(), target);
        status = UiMessages.text("editor.route_tape.status_exported", Map.of("path", target.toString()));
        return target;
    }

    // ---------------------------------------------------------------- row edits

    private void edit(InputTape next) {
        change(next, goals);
    }

    /** Replaces the goal program as one undo step. */
    public void setGoals(com.openggf.route.GoalProgram next) {
        if (scene != null) throw new IllegalStateException(UiMessages.text("editor.route_tape.scene_rows_only"));
        Objects.requireNonNull(next, "next");
        if (loaded != null && loaded.launch().inlet() instanceof Inlet.Title) RouteCompetitionFacts.validate(next);
        change(tape, next);
    }

    private void change(InputTape nextTape, com.openggf.route.GoalProgram nextGoals) {
        if (nextTape.equals(tape) && nextGoals.equals(goals)) return;
        int locked = immutablePrefixRows();
        if (locked > 0 && (nextTape.size() < locked || !nextTape.slice(0, locked).equals(tape.slice(0, locked))))
            throw new IllegalArgumentException(UiMessages.text("editor.route_tape.prefix_locked"));
        if (locked > 0 && nextGoals.startRow() != locked)
            throw new IllegalArgumentException(UiMessages.text("editor.route_tape.prefix_locked"));
        undo.push(new State(tape, goals, currentNativeFacts));
        while (undo.size() > UNDO_LIMIT) undo.removeLast();
        redo.clear();
        tape = nextTape;
        goals = nextGoals;
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
        redo.push(new State(tape, goals, currentNativeFacts));
        restore(undo.pop());
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(new State(tape, goals, currentNativeFacts));
        restore(redo.pop());
    }

    private void restore(State state) {
        tape = state.tape();
        goals = state.goals();
        currentNativeFacts = state.facts();
        cursor = Math.min(cursor, Math.max(0, tape.size() - 1));
        if (mark >= tape.size()) mark = -1;
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
        if (!hasRoute()) throw new IllegalStateException(UiMessages.text("editor.route_tape.no_route"));
        PadRow template = neutral || tape.size() == 0 ? PadRow.NEUTRAL : tape.row(cursor);
        edit(RouteTapeEdits.insert(tape, Math.min(cursor, tape.size()), count, template));
    }

    private void insertForm() {
        if (!hasRoute()) throw new IllegalStateException(UiMessages.text("editor.route_tape.no_route"));
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
        if (scene != null) {
            actions.add(action(Action.APPLY_ROWS));
            actions.add(action(Action.GO_TO_ROW));
            actions.add(new EditorProjectMenu.Action(UiMessages.text("editor.route_tape.action_back_to_scene"),
                    () -> invoke(Action.EXIT)));
            menu.showActions(scene.title(), actions);
            return;
        }
        actions.add(action(Action.OPEN));
        actions.add(action(Action.NEW));
        if (loaded != null) {
            actions.add(action(Action.SAVE));
            actions.add(action(Action.SAVE_DRAFT));
            actions.add(action(jobRunning() ? Action.CANCEL_JOB : Action.REPLAY));
            if (!jobRunning()) {
                actions.add(action(Action.GOALS));
                actions.add(action(Action.PLAN));
                actions.add(action(Action.REPAIR));
            }
            if (repairOutcome() != null && !repairOutcome().solved()) actions.add(action(Action.USE_ATTEMPT));
            actions.add(action(Action.NATIVE_FACTS));
            if (exportBlocker().isBlank()) actions.add(action(Action.EXPORT_BK2));
            actions.add(action(Action.GO_TO_ROW));
        }
        actions.add(action(Action.EXIT));
        menu.showActions(UiMessages.text("editor.route_tape.title_route"), actions);
    }

    private EditorProjectMenu.Action action(Action action) {
        return new EditorProjectMenu.Action(action.label(), () -> invoke(action));
    }

    /** File, replay and repair commands need a route; scene rows go back to their scene instead. */
    private void requireRoute() {
        if (scene != null) throw new IllegalStateException(UiMessages.text("editor.route_tape.scene_rows_only"));
        if (loaded == null) throw new IllegalStateException(UiMessages.text("editor.route_tape.no_route"));
    }

    private void requireRows() {
        if (!hasRoute()) throw new IllegalStateException(UiMessages.text("editor.route_tape.no_route"));
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
        if (!observedFacts().isEmpty()) {
            text.append("\nENGINE facts at cursor\n");
            observedFacts().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    text.append(e.getKey()).append(": ").append(e.getValue()).append('\n'));
        }
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
