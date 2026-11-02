package com.openggf.route;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes routes. A route is two committed files: {@code <stem>.route.json} (the
 * manifest, the launch authority) and {@code <stem>.script} (the editable rows). A BizHawk movie
 * is derived on demand and never committed beside them.
 */
public final class RouteFiles {
    public static final String MANIFEST_SUFFIX = ".route.json";

    private RouteFiles() {
    }

    /** The files a write produced. */
    public record Written(Path manifest, Path script) { }

    /** Reads a manifest and its script, verifying row count and content hash. */
    public static RouteArtifact read(Path manifest) throws IOException {
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        RouteJson.Parsed parsed = RouteJson.read(text);
        if (parsed.scriptName().contains("/") || parsed.scriptName().contains("\\")) {
            throw new IOException("script must sit beside its manifest: " + parsed.scriptName());
        }
        Path script = manifest.resolveSibling(parsed.scriptName());
        InputTape tape = RouteScript.read(Files.readString(script, StandardCharsets.UTF_8));
        if (tape.size() != parsed.manifest().rowCount() || !tape.hash().equals(parsed.manifest().tapeHash())) {
            throw new IOException(script + " compiles to " + tape.size() + " rows " + tape.hash().shortForm()
                    + "; the manifest declares " + parsed.manifest().rowCount() + " rows "
                    + parsed.manifest().tapeHash().shortForm());
        }
        return new RouteArtifact(parsed.manifest(), tape);
    }

    /**
     * Writes a new route. Requires engine evidence for exactly this content; the evidence is
     * recorded as a qualification stamp. Refuses to replace an existing file.
     */
    public static Written writeNew(Path stem, RouteArtifact route, ReplayEvidence engine) throws IOException {
        Path manifest = manifestPath(stem);
        Path script = scriptPath(stem);
        if (Files.exists(manifest) || Files.exists(script)) {
            throw new IOException("route already exists: " + manifest);
        }
        return write(stem, route, engine);
    }

    /**
     * Replaces an existing route in place (a repair). The new content needs its own engine
     * evidence; stamps for the old content are dropped, as they no longer describe it.
     */
    public static Written replace(Path stem, RouteArtifact route, ReplayEvidence engine) throws IOException {
        if (!Files.exists(manifestPath(stem))) throw new IOException("no route to replace: " + manifestPath(stem));
        return write(stem, route, engine);
    }

    private static Written write(Path stem, RouteArtifact route, ReplayEvidence engine) throws IOException {
        requireEvidence(route, engine);
        Path manifest = manifestPath(stem);
        Path script = scriptPath(stem);
        var stamps = new ArrayList<QualificationStamp>();
        for (QualificationStamp stamp : route.manifest().qualification()) {
            if (stamp.content().equals(route.content()) && stamp.scope() != ReplayScope.ENGINE) stamps.add(stamp);
        }
        stamps.add(engine.stamp());
        RouteManifest next = route.manifest().withQualification(stamps);
        Path parent = manifest.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        writeAtomically(script, RouteScript.write(route.tape(), List.of(
                "Route rows for " + manifest.getFileName() + "; launch, identity and checks live there.")));
        writeAtomically(manifest, RouteJson.write(next, script.getFileName().toString()));
        return new Written(manifest, script);
    }

    /** Derives a standalone BizHawk movie of the route's rows, proven loadable row for row. */
    public static Path exportBk2(RouteArtifact route, Path out) throws IOException {
        RouteBk2.write(out, route.launch().rom(), route.tape());
        return out;
    }

    /** Compiles route-script text (the {@code InputScriptCompiler} grammar) to a tape. */
    public static InputTape tapeFromScript(String script) {
        return RouteScript.read(script);
    }

    /** Route-script text for {@code tape}, with explicit physical letters on both lanes. */
    public static String scriptText(InputTape tape) {
        return RouteScript.write(tape, List.of());
    }

    /** Rows {@code [0, rows)} of a movie or input log; reading past its end is an error, never a clamp. */
    public static InputTape tapeFromMovie(Path movie, int rows) throws IOException {
        Bk2Movie loaded = new Bk2MovieLoader().loadMovieOrInputLog(movie);
        List<Bk2FrameInput> frames = loaded.getFrames();
        if (rows < 0 || rows > frames.size()) {
            throw new IOException(movie + " has " + frames.size() + " rows; asked for " + rows);
        }
        var builder = new InputTape.Builder();
        for (int i = 0; i < rows; i++) builder.add(PadRow.fromEngine(frames.get(i)));
        return builder.build();
    }

    static Path manifestPath(Path stem) {
        return stem.resolveSibling(stem.getFileName() + MANIFEST_SUFFIX);
    }

    static Path scriptPath(Path stem) {
        return stem.resolveSibling(stem.getFileName() + ".script");
    }

    private static void requireEvidence(RouteArtifact route, ReplayEvidence engine) {
        if (engine == null || engine.scope() != ReplayScope.ENGINE || !engine.content().equals(route.content())
                || engine.rows() != route.tape().size()) {
            throw new IllegalArgumentException("writing a route needs complete engine replay evidence for its content "
                    + route.content().shortForm() + "; got " + engine);
        }
    }

    private static void writeAtomically(Path target, String text) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
