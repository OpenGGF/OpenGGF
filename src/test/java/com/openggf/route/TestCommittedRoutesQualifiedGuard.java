package com.openggf.route;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No unqualified draft is committed among the route fixtures; every manifest parses strictly; and a
 * manifested route has no committed BizHawk movie beside it (a movie is derived on demand, so a
 * committed one would be a third copy of the rows that can drift).
 */
class TestCommittedRoutesQualifiedGuard {
    @Test
    void committedRouteManifestsAreNotDrafts() throws IOException {
        Path routes = Path.of("src/test/resources/routes");
        var problems = new ArrayList<String>();
        if (Files.isDirectory(routes)) {
            try (Stream<Path> files = Files.walk(routes)) {
                for (Path manifest : files.filter(p -> p.toString().endsWith(RouteFiles.MANIFEST_SUFFIX)).sorted().toList()) {
                    String name = manifest.getFileName().toString();
                    Path movie = manifest.resolveSibling(name.substring(0, name.length()
                            - RouteFiles.MANIFEST_SUFFIX.length()) + ".bk2");
                    if (Files.exists(movie)) problems.add(movie + " duplicates the rows of " + manifest);
                    try {
                        if (RouteCodec.parseManifest(Files.readString(manifest)).manifest().draft()) {
                            problems.add(manifest + " is an unqualified draft");
                        }
                    } catch (IOException malformed) {
                        problems.add(manifest + ": " + malformed.getMessage());
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "committed routes must be qualified manifests:\n  " + String.join("\n  ", problems));
    }
}
