package com.openggf.tools.audio.completerun.s1;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestS1CompleteRunProbeContract {
    private static final Path PROBE = Path.of("tools", "bizhawk", "probes",
            "s1_complete_run_audio_probe.lua");

    @Test
    void probeIsReadOnlyAndPinsEverySourceDerivedM68kLifecycleAndLoaderSite() throws Exception {
        assertTrue(Files.isRegularFile(PROBE), "missing complete-run S1 probe");
        String source = Files.readString(PROBE, StandardCharsets.UTF_8);
        assertTrue(source.contains("ProbeRuntime.run({"));
        assertTrue(source.contains("s1_complete_run_audio_contract.lua"));
        assertTrue(source.contains("expectedOpcode"), "every execute hook must pin opcode bytes");
        assertTrue(source.contains("860") && source.contains("225101"), "probe must pin the full epoch");
        for (String address : List.of(
                "0x138E", "0x1394", "0x139A", "0x71B4C", "0x71BB2", "0x71F02", "0x71F4C",
                "0x71C4C", "0x71F26", "0x71F2C", "0x71FCE", "0x71FD0", "0x71FD2", "0x71FE6",
                "0x71FF8", "0x72012", "0x72018", "0x7202C", "0x72098", "0x72126", "0x72182", "0x7218E",
                "0x721B6", "0x721B8", "0x721C6", "0x721CA", "0x721CE", "0x721D2", "0x721D6",
                "0x721DA", "0x721F4", "0x7222E", "0x7227C", "0x7230C", "0x72310", "0x72314",
                "0x72318", "0x7231C", "0x72320", "0x7234C", "0x7236E",
                "0x722C6", "0x723C6", "0x7259E", "0x725BC", "0x7267C", "0x72688", "0x7268E",
                "0x726D6", "0x726DC", "0x726E0", "0x72B14", "0x72B1E", "0x72B24", "0x72B3A", "0x72B66",
                "0x72B70", "0x72B82", "0x72B88", "0x72B8E", "0x72B9A", "0x72B9C", "0x72C22",
                "0x72C24", "0x72E02", "0x72E04")) {
            assertTrue(source.contains(address), "missing reviewed hook address " + address);
        }
        for (String continuation : List.of(
                "0x71BD4", "0x71BE6", "0x71BF8", "0x71C10", "0x71C22", "0x71C38", "0x71C44")) {
            assertTrue(source.contains(continuation), "missing stack-sensitive continuation " + continuation);
        }
        for (String roleByte : List.of("0x06", "0x00", "0x01", "0x02", "0x04", "0x05",
                "0x80", "0xA0", "0xC0")) {
            assertTrue(source.contains(roleByte), "missing loader-derived voice-control byte " + roleByte);
        }
        for (String oracleFrame : List.of("3698", "3699", "3702", "3910")) {
            assertTrue(source.contains(oracleFrame), "missing mandatory extra-life oracle frame " + oracleFrame);
        }
        assertTrue(source.contains("frame < FIRST_FRAME") && source.contains("baseline"),
                "probe must arm and sample its baseline at row 860");
        assertTrue(source.contains("frame_service_counts"),
                "probe shape must preserve zero and multiple services per frame");
        assertTrue(source.contains("musicRoleByTrackRam") && source.contains("loader_roles")
                        && source.contains("M68K A5"),
                "loader callbacks must derive source ownership from actual loaded track slots");
        assertFalse(source.contains("oneTickPerFrame"),
                "complete-run capture must not inherit one-tick-per-frame convergence");
        for (String forbidden : List.of("mainmemory.write", "memory.write", "joypad.set", "savestate.",
                "emu.setregister", "io.open", "event.onmemoryexecute", "event.onmemorywrite")) {
            assertFalse(source.contains(forbidden), "read-only observer contains forbidden API " + forbidden);
        }
    }

    @Test
    void probeConsumesTypedNativeZ80DacServicesWithoutM68kParentAssumption() throws Exception {
        assertTrue(Files.isRegularFile(PROBE), "missing complete-run S1 probe");
        String source = Files.readString(PROBE, StandardCharsets.UTF_8);
        for (String token : List.of("typed_z80_dac", "acceptTypedZ80Service", "source_cpu",
                "Z80", "z80_dpcm_byte", "z80_sega_pcm_byte", "0x77", "0x86", "0x89", "0x9C",
                "0x9F", "0xAC", "0xC1", "0xC2", "0xC5", "0xD0", "raw_chip_events")) {
            assertTrue(source.contains(token), "missing typed native DAC contract token " + token);
        }
        assertTrue(source.contains("requires_m68k_parent = false"),
                "asynchronous Z80 DAC services must be parentless");
        assertFalse(source.contains("allWritesInsideUpdateMusic"),
                "probe must not inherit the GHZ all-writes-in-M68K assertion");
        assertFalse(source.contains("assert(activeInvocation"),
                "native Z80 writes must not require an open M68K invocation");
    }
}
