package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the normal-gameplay recorder side of the CSV v7 animation contract. */
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
            assertTrue(script.contains("\"csv_version\": 7"), name);
            assertTrue(script.contains("player_animation_id"), name);
            assertTrue(script.contains("player_mapping_frame"), name);
            assertTrue(script.contains("sidekick_animation_id"), name);
            assertTrue(script.contains("sidekick_mapping_frame"), name);
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
            assertTrue(script.contains("OGGF_TRACE_LIGHTWEIGHT"), name);
            assertTrue(script.contains("physics_animation_only"), name);
            assertTrue(script.contains("if not LIGHTWEIGHT_REGEN then"), name);
        }
    }

    @Test
    void s1CompleteRunRecorderDisambiguatesEveryRepeatedSegmentDirectory() throws IOException {
        String script = Files.readString(TOOLS.resolve("s1_complete_run_recorder.lua"));
        assertTrue(script.contains("function next_segment_dir_token(base_token)"));
        assertTrue(script.contains("local dir_token = next_segment_dir_token(\"ss\")"));
        assertTrue(script.contains(
                "local dir_token = next_segment_dir_token(start_zone_name .. tostring(start_act + 1))"));
        assertTrue(script.contains("\"lua_script_version\": \"3.17\""));
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
        assertTrue(script.contains("\"lua_script_version\": \"3.17\""));
    }

    @Test
    void fastBizHawkWrapperDelegatesOneShotInitializationToRecorder() throws IOException {
        String generator = Files.readString(TOOLS.resolve("prepare_bizhawk_fast_lua.ps1"));

        assertTrue(generator.contains("dofile(target)"));
        assertTrue(!generator.contains("pcall(client.invisibleemulation, true)"),
                "The validated recorder owns the single run-level invisible-emulation call");
        assertTrue(!generator.contains("event.onframestart(apply_openggf_fast_headless"),
                "Repeated invisibleemulation calls can stall explicit frameadvance recorders");
    }

    @Test
    void allCommittedGameplayFixturesCarryV7AnimationCsv() throws IOException {
        Map<String, Integer> expectedCounts = Map.of("s1", 21, "s2", 19, "s3k", 10);
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
                assertEquals(7, metadata.csvVersion(), fixture.toString());
                String header = readPhysicsHeader(fixture);
                assertEquals(42, header.split(",", -1).length, fixture.toString());
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
}
