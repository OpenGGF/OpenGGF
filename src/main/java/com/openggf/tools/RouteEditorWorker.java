package com.openggf.tools;

import com.openggf.route.AuthorHints;
import com.openggf.route.Cancellation;
import com.openggf.route.GoalProgram;
import com.openggf.route.ObjectView;
import com.openggf.route.PlacementKey;
import com.openggf.route.RouteSample;
import com.openggf.route.PlanResult;
import com.openggf.route.ReferencePath;
import com.openggf.route.RepairReport;
import com.openggf.route.RepairScope;
import com.openggf.route.RouteHostException;
import com.openggf.route.RoutePlanner;
import com.openggf.route.RouteTask;
import com.openggf.route.SearchBudget;
import com.openggf.route.PlayerView;
import com.openggf.route.ReplayResult;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteCodec;
import com.openggf.route.RouteFiles;
import com.openggf.route.RouteReplay;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Isolated worker for the editor's route tape jobs. The engine route host is process-global and
 * boots a GL-backed capture session, so replay and qualified saves run here, never in the editor's
 * own session. It replays the working route once through the shared facade, streaming progress
 * and the observed path, then one result; a save writes the route only after that passing replay
 * minted evidence for exactly its content.
 *
 * <p>Inputs: a {@link RouteEditorProtocol} request on stdin. Outputs: protocol records on stdout;
 * diagnostics on stderr. Origin: 2026-11-02 route editor support (shared route planner).
 */
public final class RouteEditorWorker {
    private static final int PROGRESS_STEP = 600;

    private RouteEditorWorker() {
    }

    public static void main(String[] args) {
        PrintStream output = System.out;
        System.setOut(System.err);
        if (!java.util.Arrays.equals(args, new String[] {"--worker"})) {
            System.err.println("usage: RouteEditorWorker --worker < request");
            System.exit(2);
        }
        try (var input = new RouteEditorProtocol.LineReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            run(RouteEditorProtocol.decodeRequest(input), output);
            output.flush();
        } catch (IOException failure) {
            System.err.println("Route worker: " + Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()));
            System.exit(1);
        }
    }

    static void run(RouteEditorProtocol.Request request, PrintStream output) throws IOException {
        RouteArtifact route = RouteCodec.parseTransport(request.route());
        if (route.launch().inlet() instanceof com.openggf.route.Inlet.Title &&
                (request.kind() == RouteEditorProtocol.Kind.PLAN && request.row() < route.checks().startRow()
                || request.kind() == RouteEditorProtocol.Kind.REPAIR && request.repair().firstEditable() < route.checks().startRow())) {
            output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(RouteEditorProtocol.Verdict.STOPPED,
                    0, "The inherited prefix is locked", route.content().toString())));
            return;
        }
        switch (request.kind()) {
            case REPAIR -> {
                repair(request, route, output);
                return;
            }
            case PLAN -> {
                plan(request, route, output);
                return;
            }
            case SCAN -> {
                scan(request, route, output);
                return;
            }
            default -> { }
        }
        var replay = new RouteReplay(hosts(request, route));
        String content = route.content().toString();
        int rows = route.tape().size();
        int stride = RouteEditorProtocol.pathStride(rows);
        // One evidence-minting pass; the observer only copies post-row centres for the overlay.
        ReplayResult result = replay.replay(route, Cancellation.NONE, (row, sample) -> {
            if (row == rows - 1) sample.facts().forEach((key,value) -> output.println(RouteEditorProtocol.fact(key,value)));
            if (row % stride == 0 || row == rows - 1) {
                emit(output, 1, row, sample.epoch(), sample.p1());
                if (sample.p2().isPresent()) emit(output, 2, row, sample.epoch(), sample.p2().get());
            }
            if ((row + 1) % PROGRESS_STEP == 0) {
                output.println(RouteEditorProtocol.progress(new RouteEditorProtocol.Progress(1, 1, row + 1, rows)));
            }
        });
        switch (result) {
            case ReplayResult.Passed passed -> {
                if (request.kind() == RouteEditorProtocol.Kind.SAVE) {
                    var written = com.openggf.route.RouteEditorFiles.save(request.target(), route, passed.evidence(), request.replace());
                    output.println(RouteEditorProtocol.saved(written.manifest().toAbsolutePath()));
                }
                output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(
                        RouteEditorProtocol.Verdict.PASSED, passed.evidence().rows(), passed.evidence().environment(),
                        content)));
            }
            case ReplayResult.Failed failed -> output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(
                    RouteEditorProtocol.Verdict.FAILED, failed.row(), failed.reason(), content)));
            case ReplayResult.Unsupported unsupported -> output.println(RouteEditorProtocol.result(
                    new RouteEditorProtocol.Result(RouteEditorProtocol.Verdict.UNSUPPORTED, 0, unsupported.reason(),
                            content)));
        }
    }

    /**
     * Repairs {@code original} against the reference route through the one planner. A solved repair
     * returns its rows and kept/searched spans; a stop returns its furthest attempt for inspection.
     * Nothing is written: the editor adopts rows and saves through a qualifying replay.
     */
    private static void repair(RouteEditorProtocol.Request request, RouteArtifact original, PrintStream output)
            throws IOException {
        var hosts = hosts(request, original);
        var bounds = request.repair();
        ReferencePath reference;
        try {
            if (request.track() != null) {
                // A track recorded on the engine the movie's rows were authored against (step 6).
                int frames = new com.openggf.debug.playback.Bk2MovieLoader().loadMovieOrInputLog(request.movie()).getFrameCount();
                reference = RouteTrackReference.read(request.track(), request.movie(), original.launch(),
                        RouteFiles.tapeFromMovie(request.movie(), frames));
            } else {
                RouteArtifact referenceRoute = RouteCodec.parseTransport(request.reference());
                reference = ReferencePath.extract(hosts, referenceRoute.launch(), referenceRoute.tape(),
                        referenceRoute.tape().size());
            }
        } catch (RouteHostException refused) {
            output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(
                    RouteEditorProtocol.Verdict.UNSUPPORTED, 0, refused.getMessage(), original.content().toString())));
            return;
        }
        int to = bounds.windowTo() < 0 ? reference.size() - 1 : Math.min(bounds.windowTo(), reference.size() - 1);
        var task = new RouteTask.Repair(original, reference.window(Math.min(bounds.windowFrom(), to), to),
                new RepairScope(bounds.firstEditable(), bounds.maxAuthoredRows(), bounds.maxWindows()),
                AuthorHints.referenceFollowing(), SearchBudget.referenceRepair());
        PlanResult result = new RoutePlanner(hosts).plan(task, Cancellation.NONE, progress ->
                output.println(RouteEditorProtocol.plan(new RouteEditorProtocol.Plan(progress.phase(),
                        progress.expansions(), progress.bestRow(), progress.bestProgress(), progress.target()))));
        switch (result) {
            case PlanResult.Solved solved -> {
                for (RepairReport.Span span : solved.repair().spans()) {
                    output.println(RouteEditorProtocol.span(switch (span) {
                        case RepairReport.Span.Original kept -> new RouteEditorProtocol.Span("KEPT", kept.sourceStart(),
                                kept.outputStart(), kept.length());
                        case RepairReport.Span.Reference followed -> new RouteEditorProtocol.Span("REFERENCE",
                                followed.sourceStart(), followed.outputStart(), followed.length());
                        case RepairReport.Span.Authored searched -> new RouteEditorProtocol.Span("SEARCHED", -1,
                                searched.outputStart(), searched.length());
                    }));
                }
                output.println(RouteEditorProtocol.rows(RouteFiles.scriptText(solved.route().tape())));
                output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(RouteEditorProtocol.Verdict.SOLVED,
                        solved.repair().originalRows(), solved.repair().summary().lines().findFirst().orElse(""),
                        solved.route().content().toString())));
            }
            case PlanResult.Stopped stopped -> {
                output.println(RouteEditorProtocol.rows(RouteFiles.scriptText(stopped.bestDraft())));
                output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(RouteEditorProtocol.Verdict.STOPPED,
                        stopped.furthestRow(), stopped.reason() + ": " + stopped.detail(), original.content().toString())));
            }
        }
    }

    /**
     * Authors rows after the kept prefix {@code [0, row)} that complete the route's goal program, through
     * the one planner. The qualifying replay streams the path; a solved plan returns the whole route.
     */
    private static void plan(RouteEditorProtocol.Request request, RouteArtifact route, PrintStream output) {
        var hosts = hosts(request, route);
        var goals = route.checks();
        var program = route.launch().inlet() instanceof com.openggf.route.Inlet.Title
                ? goals : new GoalProgram(goals.routeGuard(), goals.legs(), request.row());
        var task = new RouteTask.Author(route.launch(), route.tape().slice(0, request.row()), program,
                SearchBudget.encounter());
        int[] stride = {1};
        PlanResult result = new RoutePlanner(hosts).plan(task, Cancellation.NONE, progress ->
                output.println(RouteEditorProtocol.plan(new RouteEditorProtocol.Plan(progress.phase(),
                        progress.expansions(), progress.bestRow(), progress.bestProgress(), progress.target()))),
                (row, sample) -> {
                    if (row % stride[0] == 0) {
                        emit(output, 1, row, sample.epoch(), sample.p1());
                        if (sample.p2().isPresent()) emit(output, 2, row, sample.epoch(), sample.p2().get());
                    }
                });
        switch (result) {
            case PlanResult.Solved solved -> {
                var planned = new RouteArtifact(solved.route().manifest().withNativeFacts(route.manifest().nativeFacts()), solved.route().tape());
                if (planned.manifest().nativeFacts().entry().isPresent()) {
                    var anchored = new RouteReplay(hosts).replay(planned, Cancellation.NONE);
                    if (!(anchored instanceof ReplayResult.Passed)) {
                        output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(
                                RouteEditorProtocol.Verdict.STOPPED, route.tape().size(),
                                "Native anchor verification failed: " + anchored, route.content().toString())));
                        return;
                    }
                }
                output.println(RouteEditorProtocol.plannedRoute(RouteCodec.formatTransport(planned)));
                output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(RouteEditorProtocol.Verdict.SOLVED,
                        solved.route().tape().size(), solved.engineEvidence().environment(),
                        solved.route().content().toString())));
            }
            case PlanResult.Stopped stopped -> {
                output.println(RouteEditorProtocol.rows(RouteFiles.scriptText(stopped.bestDraft())));
                output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(RouteEditorProtocol.Verdict.STOPPED,
                        stopped.furthestRow(), stopped.reason() + ": " + stopped.detail(), route.content().toString())));
            }
        }
    }

    /**
     * Plays the route's rows to {@code row} and lists the live objects that report a capability,
     * placed or spawned at runtime, so the editor can pick goal targets. Comparison of values only.
     */
    private static void scan(RouteEditorProtocol.Request request, RouteArtifact route, PrintStream output) {
        var replay = new RouteReplay(hosts(request, route));
        try (var playback = replay.open(route)) {
            RouteSample sample = playback.stepTo(Math.min(request.row(), route.tape().size()));
            sample.facts().forEach((key,value) -> output.println(RouteEditorProtocol.fact(key,value)));
            for (ObjectView object : sample.objects()) {
                var placement = object.placement();
                output.println(RouteEditorProtocol.object(new RouteEditorProtocol.ScannedObject(object.typeKey(),
                        placement.map(PlacementKey::zone).orElse(sample.level().zone()),
                        placement.map(PlacementKey::act).orElse(sample.level().act()),
                        placement.map(PlacementKey::layoutIndex).orElse(-1), placement.map(PlacementKey::objectId).orElse(-1),
                        placement.map(PlacementKey::subtype).orElse(-1), object.centreX(), object.centreY(),
                        object.capabilities().stream().map(Enum::name).sorted().toList(), object.phaseNames(),
                        object.phase(), object.hitsRemaining(), object.defeated())));
            }
            output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(RouteEditorProtocol.Verdict.PASSED,
                    playback.row(), "scan", route.content().toString())));
        } catch (RouteHostException refused) {
            output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(
                    RouteEditorProtocol.Verdict.UNSUPPORTED, 0, refused.getMessage(), route.content().toString())));
        }
    }

    private static com.openggf.route.RouteWorldFactory hosts(RouteEditorProtocol.Request request, RouteArtifact route) {
        if (route.launch().inlet() instanceof com.openggf.route.Inlet.Title) {
            var rom=request.roms().get("s2");
            if(rom==null) return new com.openggf.route.RouteWorldFactory() {
                private RouteHostException unavailable() { return new RouteHostException(RouteHostException.Reason.ROM_UNAVAILABLE,"S2 REV01 required"); }
                public com.openggf.route.RouteWorld openSearch(com.openggf.route.LaunchContract launch) { throw unavailable(); }
                public com.openggf.route.ReplayWorld openColdReplay(com.openggf.route.LaunchContract launch) { throw unavailable(); }
                public String environment() { return "S2 unavailable"; }
            };
            return new com.openggf.tools.StageplayCompetitionRouteHosts(rom);
        }
        return EngineRouteHosts.withRoms(request.roms());
    }

    private static void emit(PrintStream output, int slot, int row, long epoch, PlayerView view) {
        output.println(RouteEditorProtocol.point(new RouteEditorProtocol.Point(slot, row, epoch, view.centreX(),
                view.centreY(), view.air())));
    }
}
