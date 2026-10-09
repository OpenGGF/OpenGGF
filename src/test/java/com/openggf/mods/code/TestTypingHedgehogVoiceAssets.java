package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.OggPcmDecoder;
import com.openggf.mods.PcmData;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Typing of the Hedgehog's published voice clips decode through the engine's stb_vorbis Ogg
 * decoder with exactly the frame counts their provenance and VoiceLine queue leases claim, and
 * VoiceLine names both the clean clip and its load-time Mega Drive variant.
 */
class TestTypingHedgehogVoiceAssets {
    private static final Path VOICE = Path.of("examples/typing-of-the-hedgehog/src/main/resources/audio/voice");
    private static final Path ENUM = Path.of("examples/typing-of-the-hedgehog/src/main/java/typinghog/audio/VoiceLine.java");

    @TempDir Path work;

    @Test void everyPublishedClipDecodesExactlyAndMatchesItsVoiceLine() throws Exception {
        JsonNode provenance = new ObjectMapper().readTree(VOICE.resolve("provenance.json").toFile());
        String suffix = provenance.get("runtime_variant").get("suffix").asText();
        Map<String, JsonNode> records = new HashMap<>();
        for (JsonNode record : provenance.get("entries")) {
            records.put(record.get("id").asText(), record);
        }
        assertFalse(records.isEmpty());

        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-d", work.toString(), ENUM.toString(),
                ENUM.resolveSibling("Announcer.java").toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] {work.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("typinghog.audio.VoiceLine");
            Object[] lines = type.getEnumConstants();
            assertEquals(records.size(), lines.length, "one VoiceLine per published clip");
            var assetOf = type.getMethod("asset", boolean.class);
            var taunt = type.getMethod("taunt", String.class);
            OggPcmDecoder decoder = new OggPcmDecoder();
            for (Object line : lines) {
                String id = ((Enum<?>) line).name().toLowerCase(java.util.Locale.ROOT);
                JsonNode record = records.get(id);
                assertNotNull(record, id);
                String asset = (String) type.getField("asset").get(line);
                assertEquals(record.get("asset").asText(), asset, id);
                assertEquals(asset, assetOf.invoke(line, false), id);
                assertEquals(asset + suffix, assetOf.invoke(line, true), id);
                Object clip = type.getMethod("clip").invoke(line);
                assertEquals(asset, clip.getClass().getMethod("asset").invoke(clip), id);
                assertEquals(type.getField("durationTicks").get(line), clip.getClass().getMethod("durationTicks").invoke(clip), id);
                assertNotNull(type.getMethod("speaker").invoke(line), id);
                String text = (String) type.getField("text").get(line);
                assertEquals(record.get("text").asText(), text, id);
                if (type.getField("tauntTag").get(line) != null) {
                    assertSame(line, taunt.invoke(null, text), id);
                }
                assertTrue(record.get("blind_word_check").asBoolean(), id);
                assertTrue(record.get("retro_gate_word_check").asBoolean() || record.has("retro_mishearing_accepted"), id);

                byte[] bytes = Files.readAllBytes(VOICE.resolve(record.get("file").asText()));
                assertEquals(record.get("sha256").asText(),
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), id);
                PcmData pcm = decoder.decode(bytes, ModInputLimits.production());
                assertEquals(48_000, pcm.sampleRate(), id);
                assertEquals(1, pcm.channels(), id);
                int frames = record.get("frames").asInt();
                assertEquals(frames, pcm.sampleCount(), id + " stb_vorbis frame count");
                assertEquals((frames + 799) / 800, type.getField("durationTicks").getInt(line), id + " queue lease");
                int peak = 0;
                for (short sample : pcm.copySamples()) {
                    peak = Math.max(peak, Math.abs((int) sample));
                }
                assertTrue(peak < 32767, id + " clipped PCM");
                assertTrue(peak > 3000, id + " near-silent clip");
            }
        }
    }
}
