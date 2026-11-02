package com.openggf.tools;

import com.openggf.route.Cancellation;
import com.openggf.route.Capability;
import com.openggf.route.GoalProgram;
import com.openggf.route.Guard;
import com.openggf.route.InitialState;
import com.openggf.route.Inlet;
import com.openggf.route.InputTape;
import com.openggf.route.LaunchContract;
import com.openggf.route.LevelId;
import com.openggf.route.ObjectTarget;
import com.openggf.route.PlanResult;
import com.openggf.route.RomIdentity;
import com.openggf.route.Roster;
import com.openggf.route.RouteFiles;
import com.openggf.route.RouteGoals;
import com.openggf.route.RoutePlanner;
import com.openggf.route.RouteReplay;
import com.openggf.route.RouteTask;
import com.openggf.route.SearchBudget;
import com.openggf.route.Seeds;
import com.openggf.route.StopReason;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The shared front-end of the boss encounter authors: a cold launch, the supplied movie's first
 * {@code prefix} rows kept verbatim, then one {@code defeat} leg against the live object of the
 * named type that reports hit points. The shared planner searches (its encounter candidate
 * families came from the SSZ boss author) and cold-replays the result. There is no loop here.
 *
 * <p>Outputs in an empty directory: {@code route.route.json} + {@code route.script} when solved,
 * a derived {@code input.bk2}, a per-row {@code state.csv} from a fresh playback of the frozen
 * rows, and {@code result.txt}; when stopped, an unqualified {@code draft.script} and
 * {@code result.txt}.
 */
final class EncounterRouteAuthor {
    /** What the author reached: a qualified defeat, or the stop and its furthest row. */
    record Outcome(boolean defeated, boolean dead, int frames, String detail) { }

    private EncounterRouteAuthor() {
    }

    static Outcome author(Path rom, String game, GameplayCaptureSession.Settings settings, int bootZone, int bootAct,
                          Path input, int prefix, String bossType, Path output, int maxInputs) throws Exception {
        if (maxInputs <= 0) throw new IllegalArgumentException("MAX_NEW_INPUTS must be positive");
        if (settings.startX() != null || settings.startY() != null || !"off".equals(settings.donor())) {
            throw new IllegalArgumentException("Author requires a cold native entry");
        }
        int available = new com.openggf.debug.playback.Bk2MovieLoader().loadMovieOrInputLog(input).getFrameCount();
        if (prefix < 0 || prefix > available) {
            throw new IllegalArgumentException("PREFIX must be inside the supplied movie");
        }
        Files.createDirectories(output);
        try (var existing = Files.list(output)) {
            if (existing.findAny().isPresent()) throw new IllegalArgumentException("Output is not empty: " + output);
        }
        InputTape prefixRows = RouteFiles.tapeFromMovie(input, prefix);
        var launch = new LaunchContract(RomIdentity.ofFile(game, rom),
                new Inlet.ColdLevel(new LevelId(game, bootZone, bootAct), false),
                new Roster(settings.mainCharacter(), settings.sidekickCharacter()), settings.width(), "off",
                new InitialState(0, Seeds.NONE));
        var goals = new GoalProgram(new Guard(true, true, false, false),
                List.of(RouteGoals.defeat("defeat", ObjectTarget.typed(bossType, Capability.HIT_POINTS), maxInputs)),
                prefix);
        var hosts = EngineRouteHosts.withRoms(Map.of(game, rom));
        PlanResult result = new RoutePlanner(hosts).plan(
                new RouteTask.Author(launch, prefixRows, goals, SearchBudget.encounter()), Cancellation.NONE);
        var report = new StringBuilder(result.getClass().getSimpleName()).append("\nROM=").append(rom)
                .append("\nlaunch=").append(launch).append("\ninput=").append(input).append("\nprefix=").append(prefix)
                .append("\nboss=").append(bossType).append("\nmaxNewInputs=").append(maxInputs).append('\n');
        Outcome outcome = switch (result) {
            case PlanResult.Solved solved -> {
                RouteFiles.writeNew(output.resolve("route"), solved.route(), solved.engineEvidence());
                RouteFiles.exportBk2(solved.route(), output.resolve("input.bk2"));
                Files.writeString(output.resolve("state.csv"), stateCsv(hosts, solved), StandardCharsets.UTF_8);
                report.append(solved.work()).append('\n').append(solved.engineEvidence()).append('\n');
                yield new Outcome(true, false, solved.route().tape().size(), "defeated");
            }
            case PlanResult.Stopped stopped -> {
                Files.writeString(output.resolve("draft.script"), "# Unqualified draft; not a route.\n"
                        + RouteFiles.scriptText(stopped.bestDraft()));
                report.append(stopped.reason()).append(": ").append(stopped.detail()).append('\n');
                yield new Outcome(false, stopped.reason() == StopReason.QUALIFICATION_FAILED
                        && stopped.detail().contains("died"), stopped.furthestRow(), stopped.detail());
            }
        };
        Files.writeString(output.resolve("result.txt"), report);
        return outcome;
    }

    /** One state line per row from a fresh playback of the frozen rows (no search state can leak in). */
    private static String stateCsv(EngineRouteHosts hosts, PlanResult.Solved solved) {
        var rows = new StringBuilder(GameplayCaptureSession.stateHeader()).append('\n');
        try (var playback = new RouteReplay(hosts).open(solved.route())) {
            var session = playback.view(EngineRouteHosts.EngineView.class).session();
            for (int f = 0; f < playback.size(); f++) {
                playback.step();
                rows.append(session.stateLine(f, solved.route().tape().row(f).toEngine(f))).append('\n');
            }
        }
        return rows.toString();
    }
}
