package com.openggf.editor.route;

import com.openggf.route.ContentHash;
import com.openggf.route.GoalProgram;
import com.openggf.route.InitialState;
import com.openggf.route.Inlet;
import com.openggf.route.InputTape;
import com.openggf.route.LaunchContract;
import com.openggf.route.LevelId;
import com.openggf.route.NativeFacts;
import com.openggf.route.Provenance;
import com.openggf.route.QualificationStamp;
import com.openggf.route.RomIdentity;
import com.openggf.route.Roster;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteCodec;
import com.openggf.route.RouteFiles;
import com.openggf.route.RouteManifest;
import com.openggf.route.Seeds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/** Route files written through the public codec, as a route author's tools would commit them. */
public final class RouteTapeFixtures {
    private RouteTapeFixtures() {
    }

    public static LaunchContract ehz() {
        return new LaunchContract(RomIdentity.standard("s2"), new Inlet.ColdLevel(new LevelId("s2", 0, 0), false),
                new Roster("sonic", "tails"), 320, "off", new InitialState(0, Seeds.NONE));
    }

    /**
     * Writes {@code <stem>.route.json} and {@code <stem>.script}.
     *
     * @param stamps qualification stamps, given the route's content hash
     */
    public static Path write(Path directory, String stem, String script, GoalProgram checks, boolean draft,
                             Function<ContentHash, List<QualificationStamp>> stamps,
                             Function<ContentHash, NativeFacts> nativeFacts) throws IOException {
        InputTape tape = RouteFiles.tapeFromScript(script);
        ContentHash content = RouteArtifact.of(ehz(), checks, tape, new Provenance("test", "", "", List.of())).content();
        var manifest = new RouteManifest(ehz(), tape.hash(), tape.size(), checks, draft,
                new Provenance("test", "", "", List.of()), stamps.apply(content), nativeFacts.apply(content));
        Files.writeString(directory.resolve(stem + ".script"), RouteFiles.scriptText(tape), StandardCharsets.UTF_8);
        Path path = directory.resolve(stem + RouteFiles.MANIFEST_SUFFIX);
        Files.writeString(path, RouteCodec.formatManifest(manifest, stem + ".script"), StandardCharsets.UTF_8);
        return path;
    }

    /** An unqualified route (no stamps), not marked as a draft. */
    public static Path write(Path directory, String stem, String script) throws IOException {
        return write(directory, stem, script, GoalProgram.NONE, false, content -> List.of(), content -> NativeFacts.NONE);
    }
}
