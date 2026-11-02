package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.route.LaunchContract;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

/**
 * Committed S3K route fixtures by stem. A route is its qualified manifest plus rows; these
 * capture-session tests step its rows as recorded input, converted once by {@code PadRow.toEngine}
 * (the same conversion the planner's host and a derived BizHawk movie use). No movie file is read.
 */
final class RouteFixtures {
    static final Path S3K = Path.of("src/test/resources/routes/s3k");

    private RouteFixtures() {
    }

    /** The qualified route; refuses a draft or a route without a passed engine stamp. */
    static RouteArtifact route(String stem) throws IOException {
        return RouteFiles.read(S3K.resolve(stem + RouteFiles.MANIFEST_SUFFIX));
    }

    /**
     * The route's rows for a session booted with {@code settings}, which must be the manifest's
     * launch (width, roster, donor and, when given, the initial {@code V_int_run_count}).
     */
    static Bk2Movie movie(String stem, GameplayCaptureSession.Settings settings) throws IOException {
        RouteArtifact route = route(stem);
        LaunchContract launch = route.launch();
        boolean same = launch.width() == settings.width() && launch.roster().main().equals(settings.mainCharacter())
                && launch.roster().sidekick().equals(settings.sidekickCharacter()) && launch.donor().equals(settings.donor())
                && (settings.vIntRunCount() == null || settings.vIntRunCount() == launch.initial().vIntStart());
        if (!same) throw new IllegalArgumentException(stem + " launches as " + launch + ", not as " + settings);
        return rows(route, stem);
    }

    /**
     * The route's rows replayed under other settings than its launch (for example the native320
     * rows at another viewport width). The test owns that deviation; the route's qualification
     * covers only its own launch.
     */
    static Bk2Movie rowsUnderOtherSettings(String stem) throws IOException {
        return rows(route(stem), stem);
    }

    private static Bk2Movie rows(RouteArtifact route, String stem) {
        var frames = new ArrayList<Bk2FrameInput>(route.tape().size());
        for (int i = 0; i < route.tape().size(); i++) frames.add(route.tape().row(i).toEngine(i));
        return new Bk2Movie(S3K.resolve(stem + RouteFiles.MANIFEST_SUFFIX), "", Map.of(), frames, 0);
    }
}
