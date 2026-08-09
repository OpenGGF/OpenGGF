package com.openggf.tests;

import org.junit.jupiter.api.Assumptions;
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
        String executable = stripLuaCommentsAndStrings(source);

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
                "client.exit)",
                "movie.mode() ==",
                "config.continueAfterMovie",
                "config.onFrame",
                "movieFinished")) {
            assertTrue(executable.contains(required), () -> RUNTIME + " must own `" + required + "`");
        }
        assertTrue(executable.indexOf("config.stage()") < executable.indexOf("event.onmemoryexecute"),
                "The runtime must evaluate the semantic stage gate before registering hooks");
    }

    @Test
    void sharedRuntimeCleansUpAndPreservesOriginalFailures() throws Exception {
        Path lua = Path.of("/usr/bin/lua");
        Assumptions.assumeTrue(Files.isExecutable(lua), "Lua is unavailable for the behavioral contract test");
        Path harness = Path.of("src", "test", "resources", "bizhawk",
                "probe_runtime_contract_test.lua");

        Process process = new ProcessBuilder(lua.toString(), harness.toString(), RUNTIME.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        assertTrue(exitCode == 0, () -> "Probe runtime behavioral contract failed:\n" + output);
    }

    @Test
    void everyNamespacedProbeUsesDeclarativeRuntimeContract() throws IOException {
        assertTrue(Files.isDirectory(PROBE_DIR), "Missing guarded probe namespace: " + PROBE_DIR);
        List<Path> probes;
        try (var paths = Files.walk(PROBE_DIR)) {
            probes = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".lua"))
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
                    "client.SetSoundOn", "client.exit", "io.open", "while true",
                    "mainmemory.write", "memory.write", "joypad.set", "savestate.",
                    "emu.setregister")) {
                assertTrue(!executable.contains(forbidden),
                        () -> probe + " must not own lifecycle or hook registration: `" + forbidden + "`");
            }
        }
    }

    @Test
    void luaLongStringsAndCommentsCannotSpoofTheContract() {
        String executable = stripLuaCommentsAndStrings("""
                --[=[ ProbeRuntime.run({ stage = function hooks = { client.exit() ]=]
                local decoy = [==[ event.onmemoryexecute client.exit() ]==]
                ProbeRuntime.run({ stage = function() return true end, hooks = {} })
                client.exit()
                """);
        assertTrue(!executable.contains("event.onmemoryexecute"), "long-string decoy survived");
        assertTrue(executable.indexOf("ProbeRuntime.run") == executable.lastIndexOf("ProbeRuntime.run"),
                "equal-delimited comment decoy survived");
        assertTrue(executable.contains("client.exit()"), "executable call was stripped");
    }

    private static String stripLuaCommentsAndStrings(String source) {
        StringBuilder executable = new StringBuilder(source.length());
        boolean lineComment = false;
        String longClose = null;
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    executable.append('\n');
                } else {
                    executable.append(' ');
                }
                continue;
            }
            if (longClose != null) {
                if (source.startsWith(longClose, i)) {
                    executable.append(" ".repeat(longClose.length()));
                    i += longClose.length() - 1;
                    longClose = null;
                } else executable.append(current == '\n' ? '\n' : ' ');
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
            if (source.startsWith("--", i)) {
                String close = longBracketClose(source, i + 2);
                if (close != null) {
                    int openerLength = close.length() + 2;
                    executable.append(" ".repeat(openerLength));
                    i += openerLength - 1;
                    longClose = close;
                } else {
                    lineComment = true;
                    executable.append("  ");
                    i++;
                }
                continue;
            }
            String close = longBracketClose(source, i);
            if (close != null) {
                executable.append(" ".repeat(close.length()));
                i += close.length() - 1;
                longClose = close;
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

    private static String longBracketClose(String source, int start) {
        if (start >= source.length() || source.charAt(start) != '[') return null;
        int cursor = start + 1;
        while (cursor < source.length() && source.charAt(cursor) == '=') cursor++;
        if (cursor >= source.length() || source.charAt(cursor) != '[') return null;
        return "]" + "=".repeat(cursor - start - 1) + "]";
    }
}
