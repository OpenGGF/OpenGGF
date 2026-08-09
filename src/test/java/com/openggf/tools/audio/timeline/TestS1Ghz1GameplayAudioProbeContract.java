package com.openggf.tools.audio.timeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestS1Ghz1GameplayAudioProbeContract {
    private static final Path PROBE = Path.of("tools", "bizhawk", "probes", "s1_ghz1_gameplay_audio_timeline_probe.lua");

    @Test
    void probeIsReadOnlyPinnedAndUsesTheTimelineContract() throws Exception {
        // Break caught: the real-ROM observer loses a required hook/identity check, mutates emulation,
        // or stops emitting through ProbeRuntime's managed OGGF_OUT stream.
        assertTrue(Files.isRegularFile(PROBE), "missing S1 GHZ1 gameplay-audio observer");
        String source = Files.readString(PROBE, StandardCharsets.UTF_8);
        assertTrue(source.contains("ProbeRuntime.run({"), "probe must use ProbeRuntime.run({...})");
        assertTrue(source.contains("s1_gameplay_audio_timeline_contract.lua"), "probe must use pure timeline contract");
        assertTrue(source.contains("context.log("), "probe must write only through ProbeRuntime output");
        assertTrue(source.contains("s1_gameplay_audio_timeline.v1"), "probe must emit the Task 1 schema");
        for (String required : List.of("0x138E", "0x1394", "0x139A", "0x71F02", "0x71F4C",
                "0x71FD2", "0x721C6", "0x7230C", "0x71B4C", "0x71C4C", "0x81", "860", "4975",
                "f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b")) {
            assertTrue(source.contains(required), "probe is missing required pinned observation: " + required);
        }
        assertTrue(source.contains("expectedOpcode"), "probe must verify ROM opcodes before capture");
        assertTrue(source.contains("mainmemory.read_u8"), "probe must read ROM sound RAM");
        assertTrue(source.contains("movie.length()"), "probe must pin the BK2 input length");
        assertTrue(source.contains("Genesis Plus GX") && source.contains("2.11"),
                "probe must pin BizHawk and core identity");
        assertTrue(source.contains("18"), "probe must read all music, normal, and special ROM track headers");
        for (String forbidden : List.of("mainmemory.write", "memory.write", "joypad.set", "savestate.",
                "emu.setregister", "io.open", "event.onmemoryexecute", "event.onmemorywrite", "client.exit")) {
            assertFalse(source.contains(forbidden), "read-only observer contains forbidden API: " + forbidden);
        }
    }
}
