package com.openggf.tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBizhawkProbeContractGuard {

    private static final Path PROBE_DIR = Path.of("tools", "bizhawk", "probes");
    private static final Path RUNTIME = PROBE_DIR.resolve("probe_runtime.lua");

    @Test
    void sharedRuntimeOwnsProbeLifecycle() throws IOException {
        assertTrue(Files.isRegularFile(RUNTIME), "Missing shared ad-hoc probe runtime: " + RUNTIME);
        String source = Files.readString(RUNTIME);

        for (String required : List.of(
                "emu.limitframerate(false)",
                "client.speedmode(6400)",
                "client.invisibleemulation(true)",
                "config.stage()",
                "config.hooks",
                "event.onmemoryexecute",
                "event.onmemorywrite",
                "event.unregisterbyname",
                "outfile:flush()",
                "outfile:close()",
                "client.exit()",
                "movie.mode() == \"FINISHED\"")) {
            assertTrue(source.contains(required), () -> RUNTIME + " must own `" + required + "`");
        }
        assertTrue(source.indexOf("config.stage()") < source.indexOf("event.onmemoryexecute"),
                "The runtime must evaluate the semantic stage gate before registering hooks");
    }

    @Test
    void everyNamespacedProbeUsesDeclarativeRuntimeContract() throws IOException {
        assertTrue(Files.isDirectory(PROBE_DIR), "Missing guarded probe namespace: " + PROBE_DIR);
        List<Path> probes;
        try (var paths = Files.list(PROBE_DIR)) {
            probes = paths.filter(path -> path.toString().endsWith(".lua"))
                    .filter(path -> !path.equals(RUNTIME))
                    .toList();
        }
        assertTrue(!probes.isEmpty(), "The guarded namespace needs a minimal contract example");

        for (Path probe : probes) {
            String source = Files.readString(probe);
            String executable = stripLuaCommentsAndStrings(source);
            assertTrue(executable.contains("ProbeRuntime.run({"), probe + " must delegate to ProbeRuntime.run");
            assertTrue(executable.contains("stage = function"), probe + " must declare a semantic stage gate");
            assertTrue(executable.contains("hooks = {"), probe + " must declare hooks for deferred registration");
            for (String forbidden : List.of(
                    "event.onmemoryexecute", "event.onmemorywrite", "event.unregisterbyname",
                    "emu.limitframerate", "client.speedmode", "client.invisibleemulation",
                    "client.SetSoundOn", "client.exit", "io.open", "while true")) {
                assertTrue(!executable.contains(forbidden),
                        () -> probe + " must not own lifecycle or hook registration: `" + forbidden + "`");
            }
        }
    }

    private static String stripLuaCommentsAndStrings(String source) {
        StringBuilder executable = new StringBuilder(source.length());
        boolean lineComment = false;
        boolean blockComment = false;
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : 0;

            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    executable.append('\n');
                } else {
                    executable.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (current == ']' && next == ']') {
                    blockComment = false;
                    executable.append("  ");
                    i++;
                } else {
                    executable.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (quote != 0) {
                executable.append(current == '\n' ? '\n' : ' ');
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '-' && next == '-') {
                if (i + 3 < source.length()
                        && source.charAt(i + 2) == '[' && source.charAt(i + 3) == '[') {
                    blockComment = true;
                    executable.append("    ");
                    i += 3;
                } else {
                    lineComment = true;
                    executable.append("  ");
                    i++;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                executable.append(' ');
                continue;
            }
            executable.append(current);
        }
        return executable.toString();
    }
}
