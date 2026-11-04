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
 *
 * <p>{@link #read} is the consumer path: it refuses drafts and routes without a passed engine
 * stamp for their exact content. Editors save and reopen unqualified work with
 * {@link #writeDraft} and {@link #readDraft}.
 */
public final class RouteFiles {
    public static final String MANIFEST_SUFFIX = ".route.json";

    private RouteFiles() {
    }

    /** The files a write produced. */
    public record Written(Path manifest, Path script) { }

    /**
     * Reads a qualified route: verifies row count and content hash, and refuses a draft or a route
     * with no passed engine stamp for its content.
     */
    public static RouteArtifact read(Path manifest) throws IOException {
        RouteArtifact route = readDraft(manifest);
        if (route.manifest().draft()) {
            throw new IOException(manifest + " is an unqualified draft; qualify it before using it as a route");
        }
        if (!route.engineQualified()) {
            throw new IOException(manifest + " has no passed engine qualification for its content "
                    + route.content().shortForm());
        }
        return route;
    }

    /** Reads a route or draft for editing, verifying only that the rows match the manifest. */
    public static RouteArtifact readDraft(Path manifest) throws IOException {
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        RouteCodec.ParsedManifest parsed = RouteCodec.parseManifest(text);
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
     * Saves unqualified work: the manifest is marked as a draft and carries no qualification
     * stamp. A draft never replaces a qualified route.
     */
    public static Written writeDraft(Path stem, RouteArtifact route) throws IOException {
        Path manifest = manifestPath(stem);
        if (Files.exists(manifest) && !readDraft(manifest).manifest().draft()) {
            throw new IOException("a draft cannot replace the qualified route " + manifest);
        }
        return writeFiles(stem, route.manifest().asDraft(true), route.tape());
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
        var stamps = new ArrayList<QualificationStamp>();
        for (QualificationStamp stamp : route.manifest().qualification()) {
            if (stamp.content().equals(route.content()) && stamp.scope() != ReplayScope.ENGINE) stamps.add(stamp);
        }
        stamps.add(engine.stamp());
        return writeFiles(stem, route.manifest().asDraft(false).withQualification(stamps), route.tape());
    }

    /**
     * Records a verdict (for example a native one) on an existing qualified route without
     * changing its content. The stamp must be for the route's content.
     */
    public static Written addStamp(Path manifest, QualificationStamp stamp, NativeFacts facts) throws IOException {
        RouteArtifact route = read(manifest);
        if (!stamp.content().equals(route.content())) {
            throw new IllegalArgumentException("stamp is for " + stamp.content().shortForm() + ", route is "
                    + route.content().shortForm());
        }
        var stamps = new ArrayList<QualificationStamp>();
        for (QualificationStamp existing : route.manifest().qualification()) {
            if (existing.scope() != stamp.scope() || !existing.content().equals(stamp.content())) stamps.add(existing);
        }
        stamps.add(stamp);
        String name = manifest.getFileName().toString();
        Path stem = manifest.resolveSibling(name.substring(0, name.length() - MANIFEST_SUFFIX.length()));
        return writeFiles(stem, route.manifest().withQualification(stamps).withNativeFacts(facts), route.tape());
    }

    private static Written writeFiles(Path stem, RouteManifest next, InputTape tape) throws IOException {
        Path manifest = manifestPath(stem);
        Path script = scriptPath(stem);
        Path parent = manifest.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        writeAtomically(script, RouteScript.write(tape, List.of(
                "Route rows for " + manifest.getFileName() + "; launch, identity and checks live there.")));
        writeAtomically(manifest, RouteCodec.formatManifest(next, script.getFileName().toString()));
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
