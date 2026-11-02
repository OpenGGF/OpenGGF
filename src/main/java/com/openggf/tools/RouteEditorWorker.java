package com.openggf.tools;

import com.openggf.route.InputTape;
import com.openggf.route.ReplayResult;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteFiles;
import com.openggf.route.RouteHostException;
import com.openggf.route.RouteManifest;
import com.openggf.route.RouteReplay;
import com.openggf.route.RouteSample;
import com.openggf.route.Cancellation;
import com.openggf.route.PlayerView;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Isolated worker for the editor's route tape jobs. The engine route host is process-global, so
 * replay and qualified saves run here, never in the editor's own session. It replays the working
 * rows of a route through the shared facade and streams progress, the observed path and one
 * result; a save writes the route only after a passing replay minted evidence for its content.
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
        } catch (Exception failure) {
            System.err.println("Route worker: " + Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()));
            System.exit(1);
        }
    }

    static void run(RouteEditorProtocol.Request request, PrintStream output) throws IOException {
        RouteArtifact route = working(request);
        var replay = new RouteReplay(EngineRouteHosts.withRoms(request.roms()));
        String content = route.content().toString();
        int rows = route.tape().size();
        // Pass 1 records the observed path; pass 2 is the qualifying replay that mints evidence.
        // One observed qualifying pass replaces both once the facade offers a replay observer.
        int stride = RouteEditorProtocol.pathStride(rows);
        try (var playback = replay.open(route)) {
            while (!playback.finished()) {
                RouteSample sample = playback.step();
                int row = playback.row() - 1;
                if (row % stride == 0 || playback.finished()) {
                    emit(output, 1, row, sample.epoch(), sample.p1());
                    if (sample.p2().isPresent()) emit(output, 2, row, sample.epoch(), sample.p2().get());
                }
                if (playback.row() % PROGRESS_STEP == 0) {
                    output.println(RouteEditorProtocol.progress(new RouteEditorProtocol.Progress(1, 2, playback.row(), rows)));
                }
            }
        } catch (RouteHostException refused) {
            output.println(RouteEditorProtocol.result(new RouteEditorProtocol.Result(
                    RouteEditorProtocol.Verdict.UNSUPPORTED, 0, refused.getMessage(), content)));
            return;
        }
        output.println(RouteEditorProtocol.progress(new RouteEditorProtocol.Progress(2, 2, 0, rows)));
        ReplayResult result = replay.replay(route, Cancellation.NONE);
        switch (result) {
            case ReplayResult.Passed passed -> {
                if (request.kind() == RouteEditorProtocol.Kind.SAVE) {
                    var written = request.replace()
                            ? RouteFiles.replace(request.target(), route, passed.evidence())
                            : RouteFiles.writeNew(request.target(), route, passed.evidence());
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

    /** The source manifest's launch, provenance and native facts over the working rows. */
    static RouteArtifact working(RouteEditorProtocol.Request request) throws IOException {
        RouteArtifact source = RouteFiles.read(request.manifest());
        InputTape tape = RouteFiles.tapeFromScript(request.script());
        RouteManifest manifest = source.manifest();
        return new RouteArtifact(new RouteManifest(manifest.launch(), tape.hash(), tape.size(), manifest.provenance(),
                manifest.qualification(), manifest.nativeFacts()), tape);
    }

    private static void emit(PrintStream output, int slot, int row, long epoch, PlayerView view) {
        output.println(RouteEditorProtocol.point(new RouteEditorProtocol.Point(slot, row, epoch, view.centreX(),
                view.centreY(), view.air())));
    }
}
