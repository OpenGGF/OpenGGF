package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.ModSubsystem;
import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModAudioPreparer;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.PcmDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/** The entire packaged bank traverses production validation, decode, ownership and PCM playback. */
@Isolated
class TestEggmansSkyVoiceAssets {
    @TempDir Path work;

    @Test void everyDeclaredClipIsVerifiedDecodableAudibleAndHasAnAccurateQueueLease() throws Exception {
        Path project = Path.of("examples/eggmans-sky");
        try (ExampleModHarness harness = ExampleModHarness.build(project, work)) {
            Path root = work.toAbsolutePath().normalize();
            var validation = new ModCatalogValidator(root, ModInputLimits.production(), (game, id) -> true)
                    .validate(new DefaultModRepositoryScanner().scan(root));
            assertEquals(1, validation.entries().size());
            ModDescriptor descriptor = assertInstanceOf(ModDescriptor.class, validation.entries().getFirst());
            assertFalse(descriptor.hasErrors(), () -> descriptor.findings().toString());
            Object[] lines = harness.loader().loadClass("eggsky.core.VoiceLine").getEnumConstants();
            assertEquals(122, lines.length);
            assertEquals(lines.length, validation.sfxRegistry().sfx().size());
            var effective = new EffectiveModCatalog(List.of(descriptor));
            var preparer = new ModAudioPreparer(root, ModInputLimits.production(), new ModRuntimeFindingStore(),
                    owners -> { fail("Valid voice bank must not disable its owner: "+owners); return new ModStateSaveResult.Saved(); });
            var provenance = new ObjectMapper().readTree(project.resolve("src/main/resources/audio/voice/provenance.json").toFile());
            assertEquals(2, provenance.get("format_version").asInt());
            assertEquals("vorbis", provenance.get("output").get("codec").asText());
            assertEquals(lines.length, provenance.get("entries").size());
            // Use the real launch factory: an SFX-only patch must not be mistaken for no external audio.
            try (var view = ModSubsystem.preparedAudioFactory(preparer, effective, validation.registry(),
                    validation.sfxRegistry()).prepare(48_000, "s3k")) {
                var port = view.streamedMusicPort();
                assertEquals(48_000, port.outputRate());
                for (Object line : lines) {
                    String asset = (String) line.getClass().getField("asset").get(line);
                    int duration = line.getClass().getField("durationTicks").getInt(line);
                    String id = ((Enum<?>) line).name().toLowerCase(java.util.Locale.ROOT);
                    var record = java.util.stream.StreamSupport.stream(provenance.get("entries").spliterator(), false)
                            .filter(row -> row.get("id").asText().equals(id)).findFirst().orElseThrow();
                    assertTrue(record.get("blind_word_check").asBoolean(), id);
                    assertEquals(record.get("processed_wav_sha256").asText(),
                            record.get("blind_word_check_sha256").asText(), id+" word check names its WAV input");
                    String assetPath = "audio/voice/"+id+".ogg";
                    assertFalse(Files.exists(project.resolve("src/main/resources/audio/voice/"+id+".wav")),
                            id+" must not ship its WAV master");
                    byte[] bytes = Files.readAllBytes(project.resolve("src/main/resources/"+assetPath));
                    assertEquals(record.get("sha256").asText(), HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(bytes)), id);
                    var decoded = new PcmDecoder().decodeSfx(assetPath, bytes, ModInputLimits.production());
                    assertEquals(48_000, decoded.sampleRate(), id);
                    assertEquals(1, decoded.channels(), id);
                    assertEquals(record.get("frames").asInt(), decoded.sampleCount(), id+" exact Vorbis duration");
                    var ref = new StreamedMusicPort.SfxRef("eggmans-sky", asset);
                    assertTrue(port.hasSfx(ref), id);
                    assertFalse(port.hasSfx(new StreamedMusicPort.SfxRef("other-owner", asset)), id);
                    assertEquals((record.get("frames").asInt()+799)/800, duration, id);
                    long energy = 0;
                    int chunks = 0;
                    int peak = 0;
                    boolean channelsMatch = true;
                    try (var cursor = port.openSfx(ref)) {
                        while (!cursor.complete()) {
                            short[] stereo = new short[1600];
                            cursor.mixInto(stereo, 800);
                            for (int i = 0; i < stereo.length; i += 2) {
                                channelsMatch &= stereo[i] == stereo[i+1];
                                int magnitude = Math.abs((int) stereo[i]);
                                peak = Math.max(peak, magnitude);
                                energy += magnitude;
                            }
                            assertTrue(++chunks <= duration, id+" cursor outlived its queue lease");
                        }
                    }
                    assertTrue(channelsMatch, id+" mono voice maps equally to stereo");
                    assertTrue(peak < 32767, id+" clipped PCM");
                    assertEquals(duration, chunks, id);
                    assertTrue(energy > 1000, id+" silent playback");
                }
                assertEveryClipReachesFinalPcm(port, lines);
            }
        }
    }

    private static void assertEveryClipReachesFinalPcm(StreamedMusicPort port, Object[] lines)
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        try {
            audio.installStreamedMusicPort(port);
            try (var capture = audio.beginLiveCaptureAudio(audio.presentationFrameRate())) {
                short[] samples = new short[capture.maxStereoFramesPerPacket() * 2];
                for (Object line : lines) {
                    String asset = (String) line.getClass().getField("asset").get(line);
                    int duration = line.getClass().getField("durationTicks").getInt(line);
                    audio.stopAllSfx();
                    assertTrue(audio.playNamespacedSfx(new StreamedMusicPort.SfxRef("eggmans-sky", asset)), asset);
                    boolean audible = false;
                    for (int tick = 0; tick < duration + 2; tick++) {
                        audio.presentFrame(PresentationMode.FORWARD);
                        audio.update();
                        int count = capture.drainPresentationFrame(samples);
                        for (int channel = 0; channel < 2; channel++) {
                            int min = Short.MAX_VALUE;
                            int max = Short.MIN_VALUE;
                            for (int i = channel; i < count * 2; i += 2) {
                                min = Math.min(min, samples[i]);
                                max = Math.max(max, samples[i]);
                            }
                            audible |= count > 0 && max - min > 32;
                        }
                    }
                    assertTrue(audible, asset + " must reach final mixed PCM through the session view");
                }
            }
        } finally {
            // Retire presentation before the enclosing prepared view releases its lease.
            audio.resetState();
        }
    }
}
