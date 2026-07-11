package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class TestModAudioManifestParser {
    private static final String GOLDEN = """
            formatVersion: 1
            tracks:
              - id: boss-remix
                assetPath: audio/boss-remix.ogg
                loop: true
                loopStartFrame: 44100
                loopEndFrame: 176400
                gain: 1.0
                tempoEffects: true
            sfx: []
            """;

    @Test
    void goldenManifestParsesAndWritesCanonicalBytes() throws Exception {
        ModAudioManifestParser parser = parser();
        ModAudioManifest manifest = parser.parse(GOLDEN.getBytes(StandardCharsets.UTF_8));
        assertEquals(new TrackKey("owner", "boss-remix"), manifest.tracks().getFirst().key());
        assertEquals(OptionalLong.of(176400), manifest.tracks().getFirst().loopEndFrame());
        assertArrayEquals(GOLDEN.getBytes(StandardCharsets.UTF_8), parser.writeCanonical(manifest));
        assertThrows(UnsupportedOperationException.class, () -> manifest.tracks().clear());
    }

    @Test
    void emptyAndNonLoopingFormsApplyExactOmissionRules() throws Exception {
        ModAudioManifest empty = parser().parse(bytes("formatVersion: 1\ntracks: []\n"));
        assertTrue(empty.tracks().isEmpty());
        assertTrue(empty.sfx().isEmpty());
        String nonLoop = """
                formatVersion: 1
                tracks:
                  - id: calm
                    assetPath: audio/calm.wav
                    loop: false
                    loopStartFrame: 0
                    gain: 0.0
                    tempoEffects: false
                sfx: []
                """;
        ModAudioTrack track = parser().parse(bytes(nonLoop)).tracks().getFirst();
        assertFalse(track.loop());
        assertTrue(track.loopEndFrame().isEmpty());
    }

    @Test
    void strictYamlAndInvalidTypedUnionsAreRejected() {
        for (String yaml : List.of(
                "", "formatVersion: 2\ntracks: []\n", "formatVersion: 1\ntracks: null\n",
                GOLDEN + "unknown: true\n",
                GOLDEN.replace("loop: true", "loop: 'true'"),
                GOLDEN.replace("loopStartFrame: 44100", "loopStartFrame: -1"),
                GOLDEN.replace("loopEndFrame: 176400", "loopEndFrame: 1"),
                GOLDEN.replace("gain: 1.0", "gain: .nan"),
                GOLDEN.replace("boss-remix", "Bad_Id"),
                GOLDEN.replace("audio/boss-remix.ogg", "../boss.ogg"),
                GOLDEN.replace("sfx: []", "sfx: null"),
                GOLDEN.replace("tracks:", "tracks: &tracks").replace("sfx: []", "sfx: *tracks"),
                GOLDEN.replace("gain: 1.0", "gain: 1.0\n    gain: 2.0"),
                GOLDEN + "---\nformatVersion: 1\ntracks: []\n")) {
            assertThrows(ModManifestException.class, () -> parser().parse(bytes(yaml)), yaml);
        }
    }

    @Test
    void directConstructorsAndInjectedBoundsCannotBeBypassed() {
        TrackKey key = new TrackKey("owner", "track");
        assertThrows(IllegalArgumentException.class, () -> new TrackKey("other", "Bad"));
        assertThrows(IllegalArgumentException.class, () -> new ModAudioTrack(key, "audio/track.mp3",
                false, 0, OptionalLong.empty(), 1, false));
        assertThrows(IllegalArgumentException.class, () -> new ModAudioTrack(key, "audio/track.ogg",
                false, 1, OptionalLong.empty(), 1, false));
        assertThrows(IllegalArgumentException.class, () -> new ModAudioTrack(key, "audio/track.ogg",
                true, 2, OptionalLong.of(2), 1, false));
        assertThrows(IllegalArgumentException.class, () -> new ModAudioSfx(
                new SfxKey("owner", "hit"), "audio/hit.wav", Float.NaN));
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxMetadataBytes(32).build();
        assertThrows(ModManifestException.class,
                () -> new ModAudioManifestParser("owner", limits).parse(bytes(GOLDEN)));
    }

    @Test
    void ordinaryDecimalGainAndNumericPreflightAreHandledWithoutFalsePositives() throws Exception {
        ModAudioManifest decimal = parser().parse(bytes(GOLDEN.replace("gain: 1.0", "gain: 0.1")));
        assertEquals(0.1f, decimal.tracks().getFirst().gain());
        ModInputLimits numeric = ModInputLimits.loweringBuilder().maxNumericDigits(2).build();
        for (String value : List.of("123.4", "1e123", "0xABCD", "1_234")) {
            ModManifestException error = assertThrows(ModManifestException.class,
                    () -> new ModAudioManifestParser("owner", numeric).parse(bytes(
                            GOLDEN.replace("formatVersion: 1", "formatVersion: " + value))));
            assertTrue(error.getMessage().contains("numeric token"), value);
        }
    }

    @Test
    void canonicalWriterHonorsInjectedCollectionLimitAndDeclaringOwner() {
        ModAudioTrack one = new ModAudioTrack(new TrackKey("owner", "one"), "audio/one.ogg",
                false, 0, OptionalLong.empty(), 1, false);
        ModAudioTrack two = new ModAudioTrack(new TrackKey("owner", "two"), "audio/two.ogg",
                false, 0, OptionalLong.empty(), 1, false);
        ModAudioManifest twoTracks = new ModAudioManifest(1, List.of(one, two), List.of());
        ModAudioManifestParser limited = new ModAudioManifestParser("owner",
                ModInputLimits.loweringBuilder().maxCollectionEntries(1).build());
        assertThrows(IllegalArgumentException.class, () -> limited.writeCanonical(twoTracks));
        ModAudioManifest wrongOwner = new ModAudioManifest(1, List.of(new ModAudioTrack(
                new TrackKey("other", "one"), "audio/one.ogg", false, 0,
                OptionalLong.empty(), 1, false)), List.of());
        assertThrows(IllegalArgumentException.class, () -> parser().writeCanonical(wrongOwner));
    }

    @Test
    void loweredAndProductionCollectionBoundariesRemainInclusive() throws Exception {
        String lowered = """
                formatVersion: 1
                tracks:
                  - {id: one, assetPath: audio/one.ogg, loop: false, loopStartFrame: 0, gain: 1, tempoEffects: false}
                  - {id: two, assetPath: audio/two.ogg, loop: false, loopStartFrame: 0, gain: 1, tempoEffects: false}
                sfx:
                  - {id: one, assetPath: audio/one.wav, gain: 1}
                  - {id: two, assetPath: audio/two.wav, gain: 1}
                """;
        ModInputLimits two = ModInputLimits.loweringBuilder().maxCollectionEntries(7).build();
        ModAudioManifest parsed = new ModAudioManifestParser("owner", two).parse(bytes(lowered));
        assertEquals(2, parsed.tracks().size());
        assertEquals(2, parsed.sfx().size());

        StringBuilder tracks = new StringBuilder("formatVersion: 1\ntracks:\n");
        StringBuilder sfx = new StringBuilder("formatVersion: 1\ntracks: []\nsfx:\n");
        for (int index = 0; index < 10_000; index++) {
            String id = "t" + Integer.toString(index, 36);
            tracks.append("- {id: ").append(id).append(", assetPath: audio/").append(id)
                    .append(".ogg, loop: false, loopStartFrame: 0, gain: 1, tempoEffects: false}\n");
            sfx.append("- {id: ").append(id).append(", assetPath: audio/").append(id)
                    .append(".wav, gain: 1}\n");
        }
        tracks.append("sfx: []\n");
        assertEquals(10_000, parser().parse(bytes(tracks.toString())).tracks().size());
        assertEquals(10_000, parser().parse(bytes(sfx.toString())).sfx().size());
    }

    @Test
    void canonicalWriterQuotesAmbiguousIdsAndEmitsEofLoopsAndSfxExactly() throws Exception {
        for (String id : List.of("null", "true", "false", "yes", "no", "on", "off",
                "123", "2026-07-11")) {
            ModAudioManifest value = new ModAudioManifest(1, List.of(new ModAudioTrack(
                    new TrackKey("owner", id), "audio/value.ogg", true, 5,
                    OptionalLong.empty(), 1, false)), List.of(new ModAudioSfx(
                    new SfxKey("owner", id), "audio/value.wav", 1)));
            String expected = "formatVersion: 1\ntracks:\n  - id: '" + id
                    + "'\n    assetPath: audio/value.ogg\n    loop: true\n    loopStartFrame: 5\n"
                    + "    gain: 1.0\n    tempoEffects: false\nsfx:\n  - id: '" + id
                    + "'\n    assetPath: audio/value.wav\n    gain: 1.0\n";
            byte[] written = parser().writeCanonical(value);
            assertArrayEquals(bytes(expected), written, id);
            assertEquals(value, parser().parse(written));
        }
    }

    @Test
    void exactTrackFieldsTypedUnionsAndTypedIdUniquenessAreEnforced() throws Exception {
        assertTrue(parser().parse(bytes(GOLDEN.replace("sfx: []", """
                sfx:
                  - id: boss-remix
                    assetPath: audio/hit.wav
                    gain: 1.0
                """))).sfx().getFirst().key().localName().equals("boss-remix"));
        for (String yaml : List.of(
                GOLDEN.replace("    tempoEffects: true\n", ""),
                GOLDEN.replace("loopStartFrame: 44100", "loopStartFrame: 1.5"),
                GOLDEN.replace("tempoEffects: true", "tempoEffects: 'true'"),
                GOLDEN.replace("loop: true", "loop: false"),
                GOLDEN.replace("sfx: []", "tracks: []"),
                GOLDEN.replace("sfx: []", "sfx: [null]"),
                GOLDEN.replace("sfx: []", """
                        sfx:
                          - {id: hit, assetPath: audio/hit.wav, gain: 1}
                          - {id: hit, assetPath: audio/hit2.wav, gain: 1}
                        """),
                GOLDEN.replace("sfx: []", "").replace("tracks:", "tracks:\n" +
                        "  - {id: boss-remix, assetPath: audio/other.ogg, loop: false, loopStartFrame: 0, gain: 1, tempoEffects: false}"))) {
            assertThrows(ModManifestException.class, () -> parser().parse(bytes(yaml)), yaml);
        }
    }

    @Test
    void canonicalEmptyAndNonloopBytesAreExactAndOmitLoopEnd() {
        assertArrayEquals(bytes("formatVersion: 1\ntracks: []\nsfx: []\n"),
                parser().writeCanonical(new ModAudioManifest(1, List.of(), List.of())));
        ModAudioManifest nonloop = new ModAudioManifest(1, List.of(new ModAudioTrack(
                new TrackKey("owner", "calm"), "audio/calm.wav", false, 0,
                OptionalLong.empty(), 0.5f, false)), List.of());
        assertArrayEquals(bytes("""
                formatVersion: 1
                tracks:
                  - id: calm
                    assetPath: audio/calm.wav
                    loop: false
                    loopStartFrame: 0
                    gain: 0.5
                    tempoEffects: false
                sfx: []
                """), parser().writeCanonical(nonloop));
    }

    @Test
    void canonicalWriterRevalidatesInjectedUtf8PathByteLimit() {
        ModAudioManifest overLimitTrack = new ModAudioManifest(1, List.of(new ModAudioTrack(
                new TrackKey("owner", "track"), "audio/éééé.ogg", false, 0,
                OptionalLong.empty(), 1, false)), List.of());
        ModAudioManifest overLimitSfx = new ModAudioManifest(1, List.of(), List.of(new ModAudioSfx(
                new SfxKey("owner", "hit"), "audio/éééé.wav", 1)));
        ModAudioManifestParser limited = new ModAudioManifestParser("owner",
                ModInputLimits.loweringBuilder().maxEntryNameBytes(16).build());
        assertThrows(IllegalArgumentException.class, () -> limited.writeCanonical(overLimitTrack));
        assertThrows(IllegalArgumentException.class, () -> limited.writeCanonical(overLimitSfx));
    }

    private static ModAudioManifestParser parser() {
        return new ModAudioManifestParser("owner", ModInputLimits.production());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
