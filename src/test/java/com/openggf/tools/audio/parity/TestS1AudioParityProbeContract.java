package com.openggf.tools.audio.parity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS1AudioParityProbeContract {
    private static final Path PROBE = Path.of("tools", "bizhawk", "probes",
            "s1_audio_driver_parity_probe.lua");

    @TempDir
    Path temp;

    @Test
    void observerIsRuntimeOwnedReadOnlyAndCoversEveryReviewedCaptureSite() throws Exception {
        // Break caught: the real-ROM observer loses a required capture/verification site,
        // mutates emulation, or writes outside ProbeRuntime's OGGF_OUT stream.
        assertTrue(Files.isRegularFile(PROBE), "missing S1 audio parity ROM observer");
        String source = Files.readString(PROBE, StandardCharsets.UTF_8);

        assertTrue(source.contains("ProbeRuntime.run({"), "probe must use ProbeRuntime.run({...})");
        assertTrue(source.contains("s1_audio_parity_contract.lua"), "probe must use the pure parity module");
        assertTrue(source.contains("mainmemory.read_u8"), "sound RAM must be read through mainmemory");
        assertTrue(source.contains("0xF000"), "sound RAM offsets must be rooted at mainmemory $F000");

        for (String forbidden : List.of("mainmemory.write", "memory.write", "joypad.set",
                "savestate.", "emu.setregister", "io.open")) {
            assertFalse(source.contains(forbidden), "read-only observer contains forbidden API: " + forbidden);
        }

        for (String address : List.of("0x71B4C", "0x71C4C", "0x71FD0", "0x71FD2")) {
            assertTrue(source.contains(address), "missing UpdateMusic epoch hook " + address);
        }
        for (String address : List.of("0xA04000", "0xA04001", "0xA04002", "0xA04003", "0xC00011")) {
            assertTrue(source.contains(address), "missing write callback address " + address);
        }
        for (String address : List.of(
                "0x7273A", "0x72752", "0x72770", "0x72788",
                "0x7225E", "0x72268", "0x723B6", "0x723C0", "0x7246A", "0x724DC",
                "0x72912", "0x72918", "0x72984", "0x729AE", "0x729BC", "0x729C0",
                "0x729C4", "0x729C8", "0x72DFA", "0x72E16")) {
            assertTrue(source.contains(address), "missing reviewed fallback manifest site " + address);
        }

        assertTrue(source.contains("expectedOpcode"), "fallback sites must carry exact opcode bytes");
        assertTrue(source.contains("verifyFallbackManifest"), "complete fallback manifest must be verified");
        assertTrue(source.contains("M68K D7") && source.contains("& 0xFF") && source.contains("acceptBgm"),
                "observer must arm only from D7 low byte through the tested lifecycle");
        assertTrue(source.contains("newInvocationLifecycle"),
                "DAC-busy retries require the tested active-invocation lifecycle");
        assertTrue(source.contains("newCallbackProof") && source.contains("assertVerified"),
                "memory callbacks must be selected only after per-port value correlation plus PSG proof");
        assertTrue(source.contains("OGGF_BIZHAWK_MOVIE_SHA256") && source.contains("requireSha256"),
                "observer must verify the launcher-computed BK2 content digest");
        assertTrue(source.contains("readU8(0x2A) == 0"),
                "GHZ capture must reject f_speedup activity at $FFF02A");
        assertTrue(source.contains("readU8(base + 0x0C)") && source.contains("volumeEnvelopeIndex"),
                "active PSG parity state must read the unsigned T+$0C VolEnvIndex cursor");
        assertTrue(source.contains("continueAfterMovie = true"), "capture must continue after movie input ends");
        assertTrue(source.contains("joypad.get(1)"), "post-movie controller 1 must be checked for neutrality");
        assertTrue(source.contains("joypad.get(2)"), "post-movie controller 2 must be checked for neutrality");
        assertTrue(source.contains("context.log("), "all output must use the runtime-owned OGGF_OUT stream");
    }

    @Test
    void linuxLauncherSuppliesDigestOfActualMovieBytes() throws Exception {
        // Break caught: the launcher passes a caller-provided or hardcoded movie identity
        // instead of hashing the exact BK2 path handed to EmuHawk.
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/bash"))
                && Files.isExecutable(Path.of("/usr/bin/sha256sum")),
                "Linux launcher dependencies are unavailable");
        Path home = Files.createDirectories(temp.resolve("bizhawk"));
        Files.writeString(home.resolve("EmuHawk.exe"), "");
        Path lua = Files.writeString(temp.resolve("probe.lua"), "return true\n");
        Path movie = Files.writeString(temp.resolve("movie.bk2"), "wrong movie content\n");
        Path rom = Files.writeString(temp.resolve("rom.gen"), "rom\n");
        Path fakeMono = temp.resolve("fake-mono.sh");
        Files.writeString(fakeMono, "#!/bin/sh\nprintf 'MOVIE_SHA=%s\\n' \"$OGGF_BIZHAWK_MOVIE_SHA256\"\n");
        Files.setPosixFilePermissions(fakeMono, PosixFilePermissions.fromString("rwx------"));

        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "tools/bizhawk/run_bizhawk_lua.sh",
                lua.toString(), movie.toString(), rom.toString());
        builder.environment().put("BIZHAWK_HOME", home.toString());
        builder.environment().put("MONO_BIN", fakeMono.toString());
        builder.environment().put("OGGF_BIZHAWK_MOVIE_SHA256", "caller-value-must-not-win");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor() == 0, () -> "fake launcher run failed:\n" + output);
        assertTrue(output.contains("MOVIE_SHA=22957c24718a1d19fee7dfecb153002b00c2a35b98662b9548140e9227b784f3"),
                () -> "launcher did not supply the actual BK2 content digest:\n" + output);
    }
}
