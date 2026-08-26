package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import com.openggf.trace.TraceMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the normal-gameplay recorder side of the strict trace-v5 contract. */
class TestTraceAnimationRecorderContract {
    private static final Path TOOLS = Path.of("tools/bizhawk");

    @Test
    void allGameplayRecordersEmitSymmetricAnimationColumns() throws IOException {
        for (String name : List.of(
                "s1_trace_recorder.lua",
                "s1_complete_run_recorder.lua",
                "s2_trace_recorder.lua",
                "s3k_trace_recorder.lua",
                "s3k_complete_run_recorder.lua")) {
            String script = Files.readString(TOOLS.resolve(name));
            assertTrue(script.contains("\"recorder\": \"lua-bizhawk-diagnostic\""), name);
            assertTrue(script.contains("\"recorder_version\": \"3.0\""), name);
            assertTrue(script.contains("\"trace_schema\": 5"), name);
            assertTrue(script.contains("player_animation_id"), name);
            assertTrue(script.contains("player_mapping_frame"), name);
            assertTrue(script.contains("sidekick_animation_id"), name);
            assertTrue(script.contains("sidekick_mapping_frame"), name);
            // Trailing life_count column: a death or 1UP must be attributable to
            // the exact frame it happened on. Read from Life_count ($FFFFFE12 in
            // all three games); comparison-only.
            assertTrue(script.contains("life_count"), name);
            assertTrue(script.contains("ADDR_LIFE_COUNT"), name);
            assertTrue(script.contains("0xFE12"), name);
        }
    }

    @Test
    void recordersReadNativeAnimationAndDisplayedMappingBytes() throws IOException {
        String s1 = Files.readString(TOOLS.resolve("s1_trace_recorder.lua"));
        String s2 = Files.readString(TOOLS.resolve("s2_trace_recorder.lua"));
        String s3k = Files.readString(TOOLS.resolve("s3k_trace_recorder.lua"));

        assertTrue(s1.contains("OFF_ANIM_FRAME_DISP  = 0x1A"));
        assertTrue(s1.contains("OFF_ANIM_ID          = 0x1C"));
        assertTrue(s2.contains("OFF_ANIM_FRAME_DISP  = 0x1A"));
        assertTrue(s2.contains("OFF_ANIM_ID          = 0x1C"));
        assertTrue(s3k.contains("OFF_ANIM_ID           = 0x20"));
        assertTrue(s3k.contains("mapping_frame = mainmemory.read_u8(base + 0x22)"));
    }

    @Test
    void s3kRecordersSupportPhysicsAnimationOnlyRegeneration() throws IOException {
        for (String name : List.of(
                "s3k_trace_recorder.lua", "s3k_complete_run_recorder.lua")) {
            String script = Files.readString(TOOLS.resolve(name));
            assertTrue(script.contains("OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS"), name);
            assertTrue(script.contains(
                    "physics_animation_aux_without_diagnostic_hooks"), name);
            assertTrue(script.contains(
                    "LIGHTWEIGHT_REGEN = not DIAGNOSTIC_HOOKS_ENABLED"), name);
            assertTrue(script.contains("if LIGHTWEIGHT_REGEN then"), name);
        }
    }

    @Test
    void s3kRecorderMetadataOmitsRetiredReplayPhaseControls() throws IOException {
        String script = Files.readString(TOOLS.resolve("s3k_trace_recorder.lua"));

        assertFalse(script.contains("pre_level_intro_prefix"));
        assertFalse(script.contains("sidekick_seed_frame_prelude"));
        assertFalse(script.contains("pre_trace_osc_frames"));

        assertTrue(script.contains("\"trace_profile\""));
        assertTrue(script.contains("\"bk2_frame_offset\""));
        assertTrue(script.contains("\"recorder\": \"lua-bizhawk-diagnostic\""));
        assertTrue(script.contains("\"recorder_version\": \"3.0\""));
        assertTrue(script.contains("\"trace_schema\": 5"));
        assertTrue(script.contains("OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS"));
        assertTrue(script.contains("OGGF_TRACE_QUIET"));
        assertTrue(script.contains(
                "LIGHTWEIGHT_REGEN = not DIAGNOSTIC_HOOKS_ENABLED"));
        assertTrue(script.contains("if LIGHTWEIGHT_REGEN then"));
    }

    @Test
    void s3kRecorderUsesCanonicalBk2OffsetForEveryProfile() throws IOException {
        String script = Files.readString(TOOLS.resolve("s3k_trace_recorder.lua"));
        assertCanonicalS3kInputWrapper(script);
    }

    @Test
    void s3kRecorderCanonicalInputGuardRejectsAnAlternateAdjustedCall() throws IOException {
        String script = Files.readString(TOOLS.resolve("s3k_trace_recorder.lua"));
        String bypass = script.replace(
                "    return C.bk2_input_mask(\n"
                        + "        fallback_raw, trace_row, bk2_frame_offset, 0)\n",
                "    local shifted = C.bk2_input_mask(\n"
                        + "        fallback_raw, trace_row, bk2_frame_offset, -1)\n"
                        + "    return C.bk2_input_mask(\n"
                        + "        fallback_raw, trace_row, bk2_frame_offset, 0)\n");

        assertFalse(script.equals(bypass), "the adversarial recorder mutation must be applied");
        assertThrows(AssertionError.class, () -> assertCanonicalS3kInputWrapper(bypass),
                "a second nonzero-adjustment call must fail the canonical wrapper contract");
    }

    @Test
    void s1CompleteRunRecorderDisambiguatesEveryRepeatedSegmentDirectory() throws IOException {
        String script = Files.readString(TOOLS.resolve("s1_complete_run_recorder.lua"));
        assertTrue(script.contains("function next_segment_dir_token(base_token)"));
        assertTrue(script.contains("local dir_token = next_segment_dir_token(\"ss\")"));
        assertTrue(script.contains(
                "local dir_token = next_segment_dir_token(start_zone_name .. tostring(start_act + 1))"));
        assertTrue(script.contains("\"recorder_version\": \"3.0\""));
    }

    @Test
    void s1CompleteRunRecorderCanCaptureFocusedFinalZoneRngCalls() throws IOException {
        String script = Files.readString(TOOLS.resolve("s1_complete_run_recorder.lua"));

        assertTrue(script.contains("OGGF_S1_RNG_CALL_RANGE"));
        assertTrue(script.contains("ADDR_RANDOM_NUMBER = 0x0029AC"));
        assertTrue(script.contains("event.onmemoryexecute(S1_RNG_CALLS.record_hit, ADDR_RANDOM_NUMBER)"));
        assertTrue(script.contains("S1_RNG_CALLS.flush()"));
        assertTrue(script.contains("rng_call_per_frame"));
        assertTrue(script.contains("OGGF_TRACE_SOURCE_BK2"));
        assertTrue(script.contains("\"recorder_version\": \"3.0\""));
    }

    @Test
    void fastBizHawkWrapperDelegatesOneShotInitializationToRecorder() throws IOException {
        String generator = Files.readString(TOOLS.resolve("prepare_bizhawk_fast_lua.ps1"));
        String windowsLauncher = Files.readString(TOOLS.resolve("run_bizhawk_lua.bat"));

        assertTrue(generator.contains("dofile(target)"));
        assertTrue(generator.contains("OGGF_BIZHAWK_PROBE_RUNTIME"));
        assertTrue(generator.contains("probe_runtime.lua"));
        assertTrue(generator.contains("$validatedSource"));
        assertTrue(generator.contains("$env:OGGF_BIZHAWK_PROBE_RUNTIME"));
        assertTrue(generator.contains("Join-Path $PSScriptRoot \"probes\\probe_runtime.lua\""));
        assertTrue(generator.contains("[IO.Path]::IsPathRooted"));
        assertTrue(!generator.contains("pcall(client.invisibleemulation, true)"),
                "The validated recorder owns the single run-level invisible-emulation call");
        assertTrue(!generator.contains("event.onframestart(apply_openggf_fast_headless"),
                "Repeated invisibleemulation calls can stall explicit frameadvance recorders");
        assertTrue(windowsLauncher.contains("OGGF_BIZHAWK_PROBE_RUNTIME"));
        assertTrue(windowsLauncher.contains("%~dp0probes\\probe_runtime.lua"));
    }

    @Test
    void windowsValidatorAcceptsNestedProbeAndIgnoresLongBracketDecoys(@TempDir Path tempDir)
            throws Exception {
        Path pwsh = Path.of("/usr/bin/pwsh");
        Assumptions.assumeTrue(Files.isExecutable(pwsh),
                "PowerShell is unavailable for the executable validator test");
        Path nested = Files.createDirectories(tempDir.resolve("nested")).resolve("probe.lua");
        Files.writeString(nested, """
                --[=[ client.invisibleemulation(false) ]=]
                local decoy = [==[ client.SetSoundOn(true) ]==]
                local runtimePath = assert(os.getenv("OGGF_BIZHAWK_PROBE_RUNTIME"))
                local ProbeRuntime = dofile(runtimePath)
                ProbeRuntime.run({
                    stage = function() return true end,
                    hooks = {{ address = 0x123456, callback = function(context) context.finish() end }}
                })
                """);
        Path wrapper = tempDir.resolve("wrapper.lua");
        ProcessBuilder builder = new ProcessBuilder(
                pwsh.toString(), "-NoLogo", "-NoProfile", "-File",
                TOOLS.resolve("prepare_bizhawk_fast_lua.ps1").toAbsolutePath().toString(),
                "-LuaScript", nested.toString(), "-WrapperPath", wrapper.toString())
                .redirectErrorStream(true);
        builder.environment().put("OGGF_BIZHAWK_PROBE_RUNTIME",
                TOOLS.resolve("probes/probe_runtime.lua").toAbsolutePath().toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());

        assertEquals(0, process.waitFor(), output);
        assertTrue(Files.readString(wrapper).contains(nested.toAbsolutePath().toString()));
    }

    @Test
    void bizHawkLinuxToolingPinsRecorderCompatible211() throws IOException {
        String ignore = Files.readString(Path.of(".gitignore"));
        String fetch = Files.readString(TOOLS.resolve("fetch_bizhawk_2_11_linux.sh"));
        String launcher = Files.readString(TOOLS.resolve("run_bizhawk_lua.sh"));
        String readme = Files.readString(TOOLS.resolve("README.md"));

        assertTrue(ignore.contains("/docs/BizHawk-*/"));
        assertTrue(!ignore.contains("/docs/BizHawk-2.11-win-x64/*"));
        assertTrue(fetch.contains("BizHawk-2.11-linux-x64.tar.gz"));
        assertTrue(fetch.contains("cdaf9650d880bae660d63a388430f630b8d8a96b1ba59ebf0e0195a645c3bab8"));
        assertTrue(fetch.contains("client.invisibleemulation"));
        assertTrue(fetch.contains("[[ -e \"${destination}\" || -L \"${destination}\" ]]"));
        assertTrue(fetch.contains("mv --no-clobber --no-target-directory"));
        assertTrue(launcher.contains("docs/BizHawk-2.11-linux-x64"));
        assertTrue(!launcher.contains("docs/BizHawk-*-linux-x64"));
        assertTrue(!launcher.contains("docs/BizHawk-2.11.1-linux-x64"));
        assertTrue(!launcher.contains("/opt/bizhawk"));
        assertTrue(readme.contains("BizHawk 2.11"));
        assertTrue(readme.contains("2.11.1"));
        assertTrue(readme.contains("client.invisibleemulation"));
    }

    @Test
    void allCommittedGameplayFixturesCarryV5AnimationCsv() throws IOException {
        Map<String, Integer> expectedCounts = Map.of("s1", 21, "s2", 19, "s3k", 21);
        for (Map.Entry<String, Integer> entry : expectedCounts.entrySet()) {
            Path gameRoot = Path.of("src/test/resources/traces", entry.getKey());
            List<Path> fixtures;
            try (var paths = Files.walk(gameRoot, 2)) {
                fixtures = paths.filter(path -> path.getFileName().toString().equals("metadata.json"))
                        .map(Path::getParent)
                        .filter(path -> !path.getFileName().toString().startsWith("credits_"))
                        .filter(path -> !path.getFileName().toString().equals("special_stage"))
                        .sorted()
                        .toList();
            }
            assertEquals(entry.getValue(), fixtures.size(), entry.getKey());
            for (Path fixture : fixtures) {
                TraceMetadata metadata = TraceMetadata.load(fixture.resolve("metadata.json"));
                assertEquals(5, metadata.traceSchema(), fixture.toString());
                String header = readPhysicsHeader(fixture);
                // 42 = recorded before the trailing life_count column existed,
                // 43 = with it. Both parse; TraceFrame reports life_count absent
                // for the narrower shape.
                int columns = header.split(",", -1).length;
                assertTrue(columns == 42 || columns == 43,
                        fixture + " has " + columns + " columns");
                assertEquals(columns == 43, header.endsWith("life_count"),
                        fixture + " must carry life_count as the trailing column");
                assertTrue(header.contains("player_animation_id"), fixture.toString());
                assertTrue(header.contains("sidekick_mapping_frame"), fixture.toString());
            }
        }
    }

    private static String readPhysicsHeader(Path fixture) throws IOException {
        Path plain = fixture.resolve("physics.csv");
        Path path = Files.exists(plain) ? plain : fixture.resolve("physics.csv.gz");
        InputStream raw = Files.newInputStream(path);
        try (InputStream input = path.toString().endsWith(".gz")
                    ? new GZIPInputStream(raw) : raw;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.readLine();
        }
    }

    private static void assertCanonicalS3kInputWrapper(String script) {
        int wrapperStart = script.indexOf("local function bk2_input_mask(");
        int wrapperEnd = script.indexOf("local function write_aux(", wrapperStart);
        assertTrue(wrapperStart >= 0 && wrapperEnd > wrapperStart,
                "s3k recorder must retain the shared BK2 input wrapper");

        String canonicalWrapper = """
                local function bk2_input_mask(fallback_raw, trace_row)
                    return C.bk2_input_mask(
                        fallback_raw, trace_row, bk2_frame_offset, 0)
                end
                """;
        String actualWrapper = script.substring(wrapperStart, wrapperEnd);
        assertEquals(canonicalWrapper.replaceAll("\\s+", ""),
                actualWrapper.replaceAll("\\s+", ""),
                "the input wrapper must contain exactly one canonical zero-adjustment call");
    }
}
