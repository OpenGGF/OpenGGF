package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.ModSubsystem;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModAudioPreparer;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The entire packaged bank traverses production validation, decode, ownership and PCM playback. */
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
                    byte[] bytes = Files.readAllBytes(project.resolve("src/main/resources/audio/voice/"+id+".wav"));
                    assertEquals(record.get("sha256").asText(), HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(bytes)), id);
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
            }
        }
    }
}
