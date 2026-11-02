package com.openggf.tools;

import com.openggf.route.AuthorHints;
import com.openggf.route.Cancellation;
import com.openggf.route.InputTape;
import com.openggf.route.LaunchContract;
import com.openggf.route.PlanProgress;
import com.openggf.route.PlanResult;
import com.openggf.route.Provenance;
import com.openggf.route.ReferencePath;
import com.openggf.route.RepairScope;
import com.openggf.route.RomIdentity;
import com.openggf.route.Roster;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteFiles;
import com.openggf.route.RoutePlanner;
import com.openggf.route.RouteTask;
import com.openggf.route.SearchBudget;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Repair front-end of the shared route planner: keeps a route movie's rows up to a first editable
 * row, then follows the route's own recorded track ({@link RouteTrackReference}), reusing the
 * original rows wherever play still tracks within six pixels and searching only where it drifts.
 * The planner cold-replays the result before it is written. This class has no loop.
 *
 * <p>Origin: shared route planner step 6 (2026-11-02), the SSZ1 routes after the
 * {@code Obj_SSZCutsceneBridge} walk-off fix moved P1 at input 1366. Inputs: ROM, cold boot
 * {@code ZONE:ACT} (act 1-based), width, main, sidekick or {@code none}, the route movie, its
 * track, the first editable row and an empty output directory. Output: {@code <stem>.route.json} +
 * {@code <stem>.script} and {@code result.txt} when solved, otherwise {@code draft.script}
 * (unqualified) and {@code result.txt}; exit 2 when stopped.
 *
 * <pre>
 * java ... com.openggf.tools.RouteRepairTool s3k.gen 10:1 320 sonic tails \
 *   src/test/resources/routes/s3k/ssz1-sonic-tails-cold-complete-320.bk2 \
 *   src/test/resources/routes/s3k/tracks/ssz1-sonic-tails-cold-complete-320.csv.gz 1366 \
 *   ssz1-sonic-tails-cold-complete-320 target/route-repair/ssz1-pair
 * </pre>
 */
public final class RouteRepairTool {
    private RouteRepairTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 10) {
            throw new IllegalArgumentException("Usage: RouteRepairTool ROM ZONE:ACT WIDTH MAIN SIDEKICK|none MOVIE.bk2 "
                    + "TRACK.csv[.gz] FIRST_EDITABLE STEM OUT_DIR");
        }
        Path rom = Path.of(args[0]);
        String[] boot = args[1].split(":");
        var launch = ReferencePathInputAuthor.launch(RomIdentity.ofFile("s3k", rom), Integer.decode(boot[0]),
                Integer.parseInt(boot[1]) - 1, new Roster(args[3], "none".equalsIgnoreCase(args[4]) ? "" : args[4]),
                Integer.parseInt(args[2]), ReferencePathInputAuthor.DEFAULT_V_INT_START);
        Path movie = Path.of(args[5]);
        Path track = Path.of(args[6]);
        int firstEditable = Integer.parseInt(args[7]);
        String stem = args[8];
        Path output = Path.of(args[9]);
        Files.createDirectories(output);
        try (var existing = Files.list(output)) {
            if (existing.findAny().isPresent()) throw new IllegalArgumentException("Output is not empty: " + output);
        }
        int frames = new com.openggf.debug.playback.Bk2MovieLoader().loadMovieOrInputLog(movie).getFrameCount();
        InputTape rows = RouteFiles.tapeFromMovie(movie, frames);
        var hosts = EngineRouteHosts.withRoms(Map.of("s3k", rom));
        ReferencePath reference = RouteTrackReference.read(track, movie, launch, rows);
        long started = System.nanoTime();
        PlanResult result = repair(hosts, launch, rows, reference, firstEditable, movie.getFileName().toString(),
                progress -> System.out.println("progress " + progress));
        var report = new StringBuilder(result.getClass().getSimpleName()).append('\n')
                .append("ROM=").append(rom).append("\nlaunch=").append(launch)
                .append("\nmovie=").append(movie).append(' ').append(frames)
                .append("\ntrack=").append(track).append(' ').append(RouteTrackReference.header(track))
                .append("\nreference=").append(reference)
                .append("\nfirstEditable=").append(firstEditable)
                .append("\nwallSeconds=").append((System.nanoTime() - started) / 1_000_000_000L).append('\n');
        int exit = 0;
        switch (result) {
            case PlanResult.Solved solved -> {
                RouteFiles.writeNew(output.resolve(stem), solved.route(), solved.engineEvidence());
                report.append(solved.repair().summary()).append('\n').append(solved.work()).append('\n')
                        .append(solved.engineEvidence()).append("\nreplayEnd=").append(solved.replayEnd()).append('\n');
            }
            case PlanResult.Stopped stopped -> {
                Files.writeString(output.resolve("draft.script"), "# Unqualified draft; not a route.\n"
                        + RouteFiles.scriptText(stopped.bestDraft()));
                report.append(stopped.reason()).append(": ").append(stopped.detail()).append("\nfurthest row ")
                        .append(stopped.furthestRow()).append("\nbest progress ").append(stopped.bestProgress())
                        .append('\n').append(stopped.work()).append('\n');
                exit = 2;
            }
        }
        Files.writeString(output.resolve("result.txt"), report);
        System.out.println(report);
        if (exit != 0) System.exit(exit);
    }

    /**
     * Keeps {@code rows[0, firstEditable)} and repairs the rest along {@code reference}, whose
     * source rows must be {@code rows} themselves (a track of this route).
     */
    public static PlanResult repair(EngineRouteHosts hosts, LaunchContract launch, InputTape rows, ReferencePath reference,
                                    int firstEditable, String source, PlanProgress.Listener listener) {
        var original = RouteArtifact.of(launch, rows,
                new Provenance("RouteRepairTool", hosts.environment(), source, List.of()));
        int remaining = reference.to() - firstEditable + 1;
        var task = new RouteTask.Repair(original, reference.window(firstEditable, reference.to()),
                new RepairScope(firstEditable, Math.max(600, remaining), 256),
                AuthorHints.referenceFollowing(), SearchBudget.referenceRepair());
        return new RoutePlanner(hosts).plan(task, Cancellation.NONE, listener);
    }
}
