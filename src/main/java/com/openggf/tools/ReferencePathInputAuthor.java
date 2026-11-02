package com.openggf.tools;

import com.openggf.route.AuthorHints;
import com.openggf.route.Cancellation;
import com.openggf.route.InitialState;
import com.openggf.route.Inlet;
import com.openggf.route.InputTape;
import com.openggf.route.LaunchContract;
import com.openggf.route.LevelId;
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
import com.openggf.route.Seeds;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Reference-path front-end of the shared route planner: authors controller rows that follow a
 * reference P1 path through traversal whose object phases differ from the reference run (RNG- or
 * V_int-phased platforms, clouds, columns), where replaying the reference rows with time shifts
 * drifts off.
 *
 * <p>This class only builds the task. The reference is an engine replay of a reference movie
 * from its own launch ({@link ReferencePath#extract}); the prefix movie's rows are kept verbatim;
 * {@link RoutePlanner} follows the reference from there (verbatim reference rows while P1 tracks
 * within six pixels, otherwise the reference candidate families) and cold-replays the result.
 * Search, checkpoints and scoring live in {@code com.openggf.route}; this class has no loop.
 *
 * <p>Origin: cold LRZ→HPZ→SSZ campaign, lane cold3 (2026-10-15); moved onto the shared planner
 * 2026-11-02. Inputs: ROM, width, roster, a cold prefix movie and length (the prefix may cross
 * real loads), a reference movie with its boot and roster, the reference index window and an
 * empty output directory. Output: {@code route.route.json} + {@code route.script} when solved,
 * otherwise {@code draft.script} (unqualified) and {@code result.txt}.
 */
public final class ReferencePathInputAuthor {
    /**
     * {@code V_int_run_count} at row 0 when a launch does not declare one: the value a cold
     * capture-session boot leaves, measured 2026-11-02 for the campaign's routes.
     */
    public static final int DEFAULT_V_INT_START = 0;

    private ReferencePathInputAuthor() {
    }

    public static void main(String[] args) throws Exception {
        int bootZone = 9, bootAct = 0, vInt = DEFAULT_V_INT_START;
        while (args.length >= 2 && args[0].startsWith("--")) {
            switch (args[0]) {
                case "--boot" -> {
                    int[] boot = boot(args[1]);
                    bootZone = boot[0];
                    bootAct = boot[1];
                }
                case "--vint" -> vInt = Integer.parseInt(args[1]);
                default -> throw new IllegalArgumentException("Unknown option " + args[0]);
            }
            args = java.util.Arrays.copyOfRange(args, 2, args.length);
        }
        if (args.length != 11) {
            throw new IllegalArgumentException("Usage: ReferencePathInputAuthor [--boot ZONE:ACT] [--vint N] ROM WIDTH "
                    + "MAIN SIDEKICK|none PREFIX.bk2 PREFIX_LENGTH REFERENCE.bk2 REF_ZONE:ACT[/MAIN/SIDEKICK|none] "
                    + "FROM_INDEX TO_INDEX OUT_DIR");
        }
        Path rom = Path.of(args[0]);
        RomIdentity identity = RomIdentity.ofFile("s3k", rom);
        int width = Integer.parseInt(args[1]);
        var roster = new Roster(args[2], "none".equalsIgnoreCase(args[3]) ? "" : args[3]);
        String[] refSpec = args[7].split("/");
        var refRoster = refSpec.length < 3 ? roster
                : new Roster(refSpec[1], "none".equalsIgnoreCase(refSpec[2]) ? "" : refSpec[2]);
        int[] refBoot = boot(refSpec[0]);
        var launch = launch(identity, bootZone, bootAct, roster, width, vInt);
        var refLaunch = launch(identity, refBoot[0], refBoot[1], refRoster, width, vInt);
        int from = Integer.parseInt(args[8]);
        int to = Integer.parseInt(args[9]);
        Path output = Path.of(args[10]);
        Files.createDirectories(output);
        try (var existing = Files.list(output)) {
            if (existing.findAny().isPresent()) throw new IllegalArgumentException("Output is not empty: " + output);
        }
        var hosts = EngineRouteHosts.withRoms(Map.of("s3k", rom));
        InputTape prefix = RouteFiles.tapeFromMovie(Path.of(args[4]), Integer.parseInt(args[5]));
        Path referenceMovie = Path.of(args[6]);
        int available = new com.openggf.debug.playback.Bk2MovieLoader().loadMovieOrInputLog(referenceMovie).getFrameCount();
        InputTape referenceTape = RouteFiles.tapeFromMovie(referenceMovie,
                Math.min(available, to + AuthorHints.referenceFollowing().lookahead() + 1));
        PlanResult result = plan(hosts, launch, prefix, refLaunch, referenceTape, from, to);
        var report = new StringBuilder(result.getClass().getSimpleName()).append('\n')
                .append("ROM=").append(rom).append("\nlaunch=").append(launch)
                .append("\nprefix=").append(args[4]).append(' ').append(args[5])
                .append("\nreference=").append(args[6]).append(' ').append(args[7])
                .append("\nwindow=").append(from).append("..").append(to).append('\n');
        int exit = 0;
        switch (result) {
            case PlanResult.Solved solved -> {
                RouteFiles.writeNew(output.resolve("route"), solved.route(), solved.engineEvidence());
                report.append(solved.repair().summary()).append('\n').append(solved.work()).append('\n')
                        .append(solved.engineEvidence()).append('\n');
            }
            case PlanResult.Stopped stopped -> {
                Files.writeString(output.resolve("draft.script"), "# Unqualified draft; not a route.\n"
                        + RouteFiles.scriptText(stopped.bestDraft()));
                report.append(stopped.reason()).append(": ").append(stopped.detail()).append("\nbest progress ")
                        .append(stopped.bestProgress()).append('\n').append(stopped.work()).append('\n');
                exit = 2;
            }
        }
        Files.writeString(output.resolve("result.txt"), report);
        System.out.println(report);
        if (exit != 0) System.exit(exit);
    }

    /**
     * Keeps {@code prefix} verbatim and follows the reference (replayed from {@code refLaunch})
     * from index {@code from} until index {@code to}.
     */
    public static PlanResult plan(EngineRouteHosts hosts, LaunchContract launch, InputTape prefix,
                                  LaunchContract refLaunch, InputTape referenceRows, int from, int to) {
        ReferencePath reference = ReferencePath.extract(hosts, refLaunch, referenceRows, referenceRows.size())
                .window(from, to);
        var original = RouteArtifact.of(launch, prefix,
                new Provenance("ReferencePathInputAuthor", hosts.environment(), "prefix", List.of()));
        var task = new RouteTask.Repair(original, reference,
                new RepairScope(prefix.size(), Math.max(600, (to - from) * 10), 64),
                AuthorHints.referenceFollowing(), SearchBudget.referenceRepair());
        return new RoutePlanner(hosts).plan(task, Cancellation.NONE);
    }

    static LaunchContract launch(RomIdentity rom, int zone, int act, Roster roster, int width, int vInt) {
        return new LaunchContract(rom, new Inlet.ColdLevel(new LevelId(rom.game(), zone, act), false), roster, width,
                "off", new InitialState(vInt, Seeds.NONE));
    }

    private static int[] boot(String spec) {
        String[] parts = spec.split(":");
        return new int[]{Integer.decode(parts[0]), Integer.parseInt(parts[1]) - 1};
    }
}
