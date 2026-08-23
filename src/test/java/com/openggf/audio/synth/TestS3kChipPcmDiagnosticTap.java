package com.openggf.audio.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestS3kChipPcmDiagnosticTap {

    @Test
    void ymTapPublishesInternalMixBeforeOutputResampling() {
        Ym2612Chip chip = new Ym2612Chip();
        chip.setOutputSampleRate(Ym2612Chip.getInternalRate());
        List<ChipPcmSample> samples = new ArrayList<>();
        ChipPcmDiagnosticFactory.install(chip, samples::add);
        chip.write(0, 0x2B, 0x80);
        chip.write(0, 0x2A, 0x90);

        int[] left = new int[3];
        int[] right = new int[3];
        chip.renderStereo(left, right);

        assertEquals(6, samples.size());
        for (int ordinal = 0; ordinal < 3; ordinal++) {
            DacLatch sample = assertInstanceOf(DacLatch.class, samples.get(ordinal * 2));
            assertEquals(ordinal, sample.ordinal());
            assertEquals(ordinal * 1008L, sample.masterCycle());
            assertEquals(16, sample.signedCode());
            YmMixStereo mix = assertInstanceOf(YmMixStereo.class,
                    samples.get(ordinal * 2 + 1));
            assertEquals(ordinal, mix.ordinal());
            assertEquals(ordinal * 1008L, mix.masterCycle());
            assertEquals(left[ordinal], mix.left());
            assertEquals(right[ordinal], mix.right());
        }
    }

    @Test
    void psgTapPublishesNativeStereoBeforeBlipResampling() {
        double nativeRate = 3_579_545.0 / 16.0;
        PsgChip chip = new PsgChip(nativeRate, PsgChip.ChipType.INTEGRATED);
        chip.configure(100, 0x10);
        List<ChipPcmSample> samples = new ArrayList<>();
        ChipPcmDiagnosticFactory.install(chip, samples::add);
        chip.write(0x80 | 0x01);
        chip.write(0x00);
        chip.write(0x90);
        chip.renderStereo(new int[4], new int[4], 4);

        assertEquals(4, samples.size());
        for (int ordinal = 0; ordinal < samples.size(); ordinal++) {
            PsgNativeStereo sample = assertInstanceOf(PsgNativeStereo.class,
                    samples.get(ordinal));
            assertEquals(ordinal, sample.ordinal());
            assertEquals(ordinal * 240L, sample.masterCycle());
            assertEquals(0, sample.right());
        }
    }

    @Test
    void absentTapLeavesChipOutputAndSnapshotsExact() {
        Ym2612Chip baselineYm = new Ym2612Chip();
        Ym2612Chip observedYm = new Ym2612Chip();
        baselineYm.setOutputSampleRate(48_000);
        observedYm.setOutputSampleRate(48_000);
        ChipPcmDiagnosticFactory.install(observedYm, sample -> { });
        baselineYm.write(0, 0x2B, 0x80);
        observedYm.write(0, 0x2B, 0x80);
        baselineYm.write(0, 0x2A, 0xA0);
        observedYm.write(0, 0x2A, 0xA0);
        int[] baselineLeft = new int[127];
        int[] baselineRight = new int[127];
        int[] observedLeft = new int[127];
        int[] observedRight = new int[127];
        baselineYm.renderStereo(baselineLeft, baselineRight);
        observedYm.renderStereo(observedLeft, observedRight);
        assertArrayEquals(baselineLeft, observedLeft);
        assertArrayEquals(baselineRight, observedRight);
        assertEquals(baselineYm.captureSnapshot(), observedYm.captureSnapshot());

        PsgChip baselinePsg = new PsgChip(48_000, PsgChip.ChipType.INTEGRATED);
        PsgChip observedPsg = new PsgChip(48_000, PsgChip.ChipType.INTEGRATED);
        ChipPcmDiagnosticFactory.install(observedPsg, sample -> { });
        baselinePsg.write(0x90);
        observedPsg.write(0x90);
        baselineLeft = new int[127];
        baselineRight = new int[127];
        observedLeft = new int[127];
        observedRight = new int[127];
        baselinePsg.renderStereo(baselineLeft, baselineRight);
        observedPsg.renderStereo(observedLeft, observedRight);
        assertArrayEquals(baselineLeft, observedLeft);
        assertArrayEquals(baselineRight, observedRight);
        assertEquals(baselinePsg.captureSnapshot(), observedPsg.captureSnapshot());
    }

    @Test
    void diagnosticInstallationRemainsPackageConfinedAndNonStatic() throws Exception {
        var ym = Ym2612Chip.class.getDeclaredMethod(
                "installPcmDiagnosticTap", ChipPcmDiagnosticTap.class);
        var psg = PsgChip.class.getDeclaredMethod(
                "installPcmDiagnosticTap", ChipPcmDiagnosticTap.class);
        for (var method : List.of(ym, psg)) {
            assertEquals(false, Modifier.isPublic(method.getModifiers()));
            assertEquals(false, Modifier.isProtected(method.getModifiers()));
            assertEquals(false, Modifier.isStatic(method.getModifiers()));
        }
        assertEquals(false, Modifier.isPublic(ChipPcmDiagnosticTap.class.getModifiers()));
        assertEquals(false, Modifier.isPublic(ChipPcmDiagnosticFactory.class.getModifiers()));
    }

    @Test
    void nativePcmLayerIsSeparatelyLockedAndBounded() throws Exception {
        Path root = Path.of("tools/bizhawk-headless/native/gpgx-audio-observer");
        JsonNode lock = new ObjectMapper().readTree(
                root.resolve("s3k-pcm-artifact-lock.json").toFile());
        assertEquals("DIAGNOSTIC_S3K_PCM_ONLY", lock.path("publication").asText());
        assertEquals(false, lock.path("production_lock_eligible").asBoolean());
        assertEquals(sha256(root.resolve("s3k-parity-artifact-lock.json")),
                lock.path("base_parity_artifact_lock_sha256").asText());
        assertEquals(sha256(root.resolve("0003-s3k-chip-pcm-events.patch")),
                lock.path("pcm_patch_sha256").asText());
        assertEquals(sha256(root.resolve("selftest/s3k-pcm-run.sh")),
                lock.path("native_selftest_runner_sha256").asText());
        assertEquals(sha256(root.resolve("selftest/s3k_pcm_harness.c")),
                lock.path("native_pcm_selftest_sha256").asText());
        assertEquals(sha256(root.resolve("selftest/s3k_pcm_replay_harness.c")),
                lock.path("native_pcm_replay_selftest_sha256").asText());
        assertEquals(28, lock.path("pcm").path("event_size").asInt());
        assertEquals(16_384, lock.path("pcm").path("capacity").asInt());
        assertEquals(1008, lock.path("pcm").path("ym_master_cycles_per_sample").asInt());
        assertEquals(240, lock.path("pcm").path("psg_master_cycles_per_sample").asInt());
        assertEquals(0x70000, Integer.parseInt(lock.path("invisible_state")
                .path("pcm_events_size_hex").asText(), 16));
        assertEquals(true, Files.isExecutable(root.resolve("selftest/s3k-pcm-run.sh")));
    }

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
