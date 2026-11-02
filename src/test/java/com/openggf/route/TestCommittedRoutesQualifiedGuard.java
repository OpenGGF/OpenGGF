package com.openggf.route;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** No unqualified draft is committed among the route fixtures; every manifest parses strictly. */
class TestCommittedRoutesQualifiedGuard {
    @Test
    void committedRouteManifestsAreNotDrafts() throws IOException {
        Path routes = Path.of("src/test/resources/routes");
        var problems = new ArrayList<String>();
        if (Files.isDirectory(routes)) {
            try (Stream<Path> files = Files.walk(routes)) {
                for (Path manifest : files.filter(p -> p.toString().endsWith(RouteFiles.MANIFEST_SUFFIX)).sorted().toList()) {
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
