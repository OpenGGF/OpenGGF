package com.openggf.tools;

import com.openggf.route.Cancellation;
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
        } catch (Exception failure) {
            System.err.println("Route worker: " + Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()));
            System.exit(1);
        }
    }

    static void run(RouteEditorProtocol.Request request, PrintStream output) throws IOException {
        RouteArtifact route = RouteCodec.parseTransport(request.route());
        var replay = new RouteReplay(EngineRouteHosts.withRoms(request.roms()));
        String content = route.content().toString();
        int rows = route.tape().size();
        int stride = RouteEditorProtocol.pathStride(rows);
        // One evidence-minting pass; the observer only copies post-row centres for the overlay.
        ReplayResult result = replay.replay(route, Cancellation.NONE, (row, sample) -> {
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

    private static void emit(PrintStream output, int slot, int row, long epoch, PlayerView view) {
        output.println(RouteEditorProtocol.point(new RouteEditorProtocol.Point(slot, row, epoch, view.centreX(),
                view.centreY(), view.air())));
    }
}
