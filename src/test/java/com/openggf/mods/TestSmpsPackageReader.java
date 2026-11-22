package com.openggf.mods;

import com.openggf.audio.smps.PackageStockResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.audio.AudioManager;
import com.openggf.audio.NativeMusicOverridePort;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.OptionalLong;
import java.util.OptionalInt;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.openggf.mods.SmpsPackageReader.sha256;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSmpsPackageReader {
    static final String DIRECTORY = "audio/smps/song/";
    static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void manifestV2RoundTripsAndRejectsMixedFields() throws Exception {
        var parser = new ModAudioManifestParser("fixture");
        String manifest = """
                formatVersion: 2
                tracks:
                  - id: song
                    smpsPackage: audio/smps/song/
                    gain: 1.0
                    tempoEffects: true
                sfx: []
                """;
        assertArrayEquals(utf8(manifest), parser.writeCanonical(parser.parse(utf8(manifest))));
        for (String invalid : new String[]{
                manifest.replace("Version: 2", "Version: 1"),
                manifest.replace("smps/song/", "smps/../"),
                manifest.replace("smps/song/", "smps/Song/"),
                manifest.replace("smps/song/", "smps/song"),
                manifest.replace("gain: 1.0", "assetPath: audio/file.wav\n    gain: 1.0"),
                manifest.replace("gain: 1.0", "loop: false\n    gain: 1.0")}) {
            assertThrows(ModManifestException.class, () -> parser.parse(utf8(invalid)), invalid);
        }
    }

    @Test
    void rejectsUnknownFieldsDuplicateKeysUnknownDriversAndWrongHost() throws Exception {
        byte[] bank = songBank();
        ObjectNode document = manifest(bank);
        document.put("extra", true);
        assertFailure("Unknown package field extra", document.toString(), bank, Map.of(), "s3k");
        document.remove("extra");
        assertFailure("Duplicate field", document.toString().replace("\"portable\"", "\"portable\", \"profile\": \"portable\""),
                bank, Map.of(), "s3k");
        assertFailure("requires a patch with baseGame s3k", document.toString(), bank, Map.of(), "s2");
        document.put("driver", "scd");
        assertFailure("Unknown driver scd", document.toString(), bank, Map.of(), "scd");
        for (String driver : List.of("s1", "s2")) {
            document.put("driver", driver);
            assertFailure("requires a patch with baseGame " + driver, document.toString(), bank, Map.of(), "s3k");
            assertFailure("Missing package field speedUpTempo", document.toString(), bank, Map.of(), driver);
        }
    }

    @Test
    void rejectsUnlistedFilesHashMismatchesAndOversizeBank() throws Exception {
        byte[] bank = songBank();
        var document = manifest(bank);
        assertFailure("Unexpected package entry", document.toString(), bank, Map.of("extra.bin", new byte[1]), "s3k");
        var changed = bank.clone();
        changed[16]++;
        assertFailure("track.bin: expected SHA-256", document.toString(), changed, Map.of(), "s3k");
        byte[] oversized = new byte[SmpsPackageReader.BANK_LIMIT + 1];
        assertFailure("limit", manifest(oversized).toString(), oversized, Map.of(), "s3k");
    }

    @Test
    void patchesFillsAndGlobalVoiceHolesAndAppliesDacTransformsInOrder() throws Exception {
        byte[] bank = Arrays.copyOf(songBank(), 64);
        bank[0] = 32;
        byte[] voice = new byte[25];
        voice[0] = 4;
        byte[] patched = voice.clone();
        patched[0] = 6;
        byte[] assembled = bank.clone();
        System.arraycopy(patched, 0, assembled, 32, 25);
        byte[] pcm = {0, 64, 127, (byte) 129, (byte) 254};
        byte[] adjusted = {0, 126, (byte) 130};
        var doc = manifest(bank);
        stock(doc);
        ObjectNode b = doc.withObject("/bank");
        b.put("assembledSha256", sha256(assembled)).put("localVoiceCount", 1);
        b.withArray("fills").addObject().put("offset", 32).put("length", 25).put("asset", "fm/0");
        ObjectNode global = doc.putObject("globalVoices");
        global.put("path", "voices/global.bin").put("sha256", sha256(new byte[25])).putArray("voices").add("fm/0");
        ObjectNode fm = asset(doc, "fm/0", "FM_VOICE");
        reference(fm, "global-fm", "slot", 0, voice);
        fm.withArray("transforms").addObject().put("op", "patch")
                .putArray("bytes").addObject().put("offset", 0).put("value", 6);
        fm.put("resultSha256", sha256(patched));
        ObjectNode dac = asset(doc, "dac/0", "DAC_SAMPLE");
        reference(dac, "dac", "note", 129, pcm);
        dac.withArray("transforms").addObject().put("op", "gain").put("factor", "0x1.0p1");
        dac.withArray("transforms").addObject().put("op", "trim").put("start", 1).put("end", 4);
        dac.put("resultSha256", sha256(adjusted));
        doc.withArray("dacBindings").addObject().put("asset", "dac/0").put("note", 129).put("rate", 6);
        try (var root = packed(temp, doc.toString(), bank, Map.of("voices/global.bin", new byte[25]))) {
            var result = reader(root, loc -> loc.table().equals("dac") ? pcm : voice).read("s3k");
            assertArrayEquals(patched, result.data().getVoice(0));
            assertArrayEquals(assembled, ((Sonic3kSmpsData) result.data()).getBankData());
            // Read a second view using the global table alone to verify its fill independently.
            byte[] globalBank = bank.clone();
            globalBank[0] = 0;
            b.put("sha256", sha256(globalBank)).put("assembledSha256", sha256(globalBank)).put("localVoiceCount", 0);
            b.putArray("fills");
            try (var globalRoot = packed(temp, doc.toString(), globalBank, Map.of("voices/global.bin", new byte[25]))) {
                assertArrayEquals(patched, reader(globalRoot, loc -> loc.table().equals("dac") ? pcm : voice)
                        .read("s3k").data().getVoice(0));
            }
            var mapping = result.dac().mappingForNote(129);
            var sample = result.dac().sample(mapping.sampleId());
            assertEquals(6, mapping.rate());
            assertEquals(adjusted.length, sample.length());
            for (int i = 0; i < adjusted.length; i++) assertEquals(adjusted[i], sample.byteAt(i));
            var mismatch = assertThrows(IOException.class, () -> reader(root, loc -> new byte[25]).read("s3k"));
            assertTrue(mismatch.getMessage().contains("fm/0"));
            assertTrue(mismatch.getMessage().contains("expected SHA-256"));
        }
    }

    @Test
    void invalidLaterPackageFallsBackWithoutDisablingItsOwner() throws Exception {
        byte[] bank = songBank();
        var doc = manifest(bank);
        doc.withObject("/provenance").put("containsRetailSongData", true);
        var warnings = new java.util.ArrayList<String>();
        try (var healthy = packed(temp, doc.toString(), bank, Map.of())) {
            new SmpsPackageReader(healthy, healthy.validatedEntryNames(), DIRECTORY,
                    ModInputLimits.production(), null, warnings::add).read("s3k");
            assertTrue(warnings.stream().anyMatch(warning -> warning.contains("personal use")));
        }
        ModDescriptor first = descriptor("first", doc, bank);
        doc.withObject("/bank").put("sha256", "0".repeat(64));
        ModDescriptor later = descriptor("later", doc, bank);
        var registry = new ModTrackRegistry(List.of(declaration("first"), declaration("later")));
        var effective = new EffectiveModCatalog(List.of(first, later));
        var findings = new ModRuntimeFindingStore();
        var preparer = new ModAudioPreparer(temp, ModInputLimits.production(), findings,
                owners -> fail("Package failure must not persistently disable an owner"));
        try (var session = preparer.prepare(effective, registry, ModSfxRegistry.EMPTY, 48_000, "s3k", null);
             var music = PreparedModMusic.build(effective, registry, session, 48_000)) {
            assertTrue(session.failedOwners().isEmpty());
            assertEquals("first", music.resolveSmpsOverride("s3k", 1).key().modId());
            assertEquals("SMPS_PACKAGE_DISABLED", findings.findingsFor("later").getFirst().code());
        }
    }

    private ModDescriptor descriptor(String owner, ObjectNode doc, byte[] bank) throws IOException {
        Path file = writePackage(temp, doc.toString(), bank, Map.of());
        try (var root = ModAssetRoot.jar(temp, file)) {
            var manifest = new ModManifest(1, owner, owner, SemanticVersion.parse("1.0.0"), List.of("Author"),
                    "Synthetic fixture", VersionRange.parse("*"), ModType.PATCH, "s3k", null,
                    List.of(), Map.of(1, "song"), Map.of(), null, OptionalInt.empty());
            return new ModDescriptor(file, manifest, root.immutableSha256(), false, List.of());
        }
    }

    private static ModAudioTrack declaration(String owner) {
        return new ModAudioTrack(new TrackKey(owner, "song"), null, false, 0,
                OptionalLong.empty(), 1, true, DIRECTORY);
    }

    @Test
    void packageUsesNativePresentationAndRewindsWithItsOwnSource() throws Exception {
        byte[] bank = songBank();
        byte[] envelope = {0, (byte) 0x80};
        ObjectNode doc = manifest(bank);
        asset(doc, "psg/0", "PSG_VOLUME").put("path", "psg/asset-001.bin").put("sha256", sha256(envelope));
        doc.withArray("envelopeBindings").addObject().put("asset", "psg/0").put("slot", 1);
        try (var root = packed(temp, doc.toString(), bank, Map.of("psg/asset-001.bin", envelope))) {
            var song = reader(root, null).read("s3k", 1.0f, false);
            short[] first = play(song);
            assertFalse(Arrays.equals(first, new short[first.length]), "Native PSG should be audible");
            assertArrayEquals(first, play(song), "Repeated offline playback must produce the same PCM");
            assertArrayEquals(new short[first.length], play(reader(root, null).read("s3k", 0.0f, false)));
        }
    }

    private static short[] play(SmpsPackageReader.Result song) {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        try {
            SmpsLoader loader = mock(SmpsLoader.class);
            when(loader.loadDacData()).thenReturn(new DacData(Map.of(), Map.of(), 297));
            var jingle = new Sonic3kSmpsData(songBank());
            jingle.setId(Sonic3kMusic.EXTRA_LIFE.id);
            when(loader.loadMusicWithReadiness(jingle.getId())).thenCallRealMethod();
            when(loader.loadMusic(jingle.getId())).thenReturn(jingle);
            var profile = new Sonic3kAudioProfile() {
                @Override public SmpsLoader createSmpsLoader(Rom rom) { return loader; }
            };
            audio.setAudioProfile(profile);
            audio.setRom(new Rom());
            NativeMusicOverridePort port = mock(NativeMusicOverridePort.class);
            when(port.outputRate()).thenReturn(audio.outputSampleRate());
            when(port.nativeMusicOverride(1)).thenReturn(new NativeMusicOverridePort.Song(
                    new StreamedMusicPort.TrackRef("fixture", "song"), song.data(), song.dac()));
            audio.installStreamedMusicPort(port);
            audio.beginCaptureMode(audio.outputSampleRate(), audio.presentationFrameRate());
            audio.beginCommandTimelineFrame(1);
            audio.playMusic(1);
            audio.presentFrame(PresentationMode.FORWARD);
            audio.beginCommandTimelineFrame(2);
            audio.setSpeedMultiplier(8);
            audio.presentFrame(PresentationMode.FORWARD);
            var snapshot = audio.captureLogicalSnapshot();
            var source = snapshot.presentation().smpsLogical().sequencers().getFirst();
            assertEquals(SmpsSourceDescriptor.Kind.PACKAGE_MUSIC, source.source().kind());
            assertEquals("fixture:song", source.source().name());
            assertEquals(1, source.snapshot().speedMultiplier());
            short[] result = new short[4096];
            audio.drainCaptureFrame(result);
            audio.beginCommandTimelineFrame(3);
            audio.playMusic(jingle.getId());
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(jingle.getId(), audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
            audio.restoreMusic();
            audio.beginCommandTimelineFrame(4);
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(source.source(), audio.captureLogicalSnapshot().presentation().smpsLogical()
                    .sequencers().getFirst().source());
            audio.restoreLogicalSnapshot(snapshot);
            assertEquals(source.source(), audio.captureLogicalSnapshot().presentation().smpsLogical()
                    .sequencers().getFirst().source());
            return result;
        } finally {
            audio.endCaptureMode();
            audio.resetState();
        }
    }

    private void assertFailure(String reason, String document, byte[] bank, Map<String, byte[]> entries, String game)
            throws Exception {
        try (var root = packed(temp, document, bank, entries)) {
            var error = assertThrows(IOException.class, () -> reader(root, null).read(game));
            assertTrue(error.getMessage().contains(reason), error::getMessage);
        }
    }

    static byte[] songBank() {
        return new byte[]{0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 16, 0, 0, 0, 0, 1,
                (byte) 0xa5, 8, (byte) 0xf6, 16, 0};
    }

    static ObjectNode manifest(byte[] bank) throws IOException {
        return (ObjectNode) JSON.readTree("""
                {
                  "schemaVersion": 4, "profile": "portable", "driver": "s3k",
                  "package": {"id":"song", "name":"Song", "version":"1.0.0",
                              "authors":[], "license":null, "rights":null},
                  "generator":{"tool":"Test", "version":"1", "openggfAudio":"test"},
                  "provenance":{"containsRomDerivedData":false,"containsRetailSongData":false,
                                "derivedFromRetailSong":null,"warnings":[]},
                  "requires":{"stockAssets":false,"games":[]},
                  "driverOptions":{},
                  "bank":{"form":"embedded","path":"track.bin","sha256":"%s","assembledSha256":"%s",
                          "base":0,"headerOffset":0,"localVoiceCount":0,"previewFrames":30,
                          "relocations":[0,6,10,19],"relocationsComplete":true,"fills":[]},
                  "assets":[],"envelopeBindings":[],"dacBindings":[],"globalVoices":null,"source":null
                }
                """.formatted(sha256(bank), sha256(bank)));
    }

    static ObjectNode asset(ObjectNode doc, String key, String kind) {
        ObjectNode asset = doc.withArray("assets").addObject().put("key", key).put("kind", kind)
                .put("id", key).put("name", key);
        asset.putObject("origin").put("type", "original");
        return asset;
    }

    static void reference(ObjectNode asset, String table, String field, int index, byte[] payload) {
        asset.withObject("/origin").put("type", "stock");
        asset.putObject("ref").put("game", "s3k").put("table", table).put("sha256", sha256(payload))
                .put(field, index);
        asset.putArray("transforms");
    }

    static void stock(ObjectNode doc) {
        doc.withObject("/requires").put("stockAssets", true).putArray("games").add("s3k");
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static com.openggf.io.PackedModAssetRoot packed(Path temp, String document, byte[] bank, Map<String, byte[]> entries)
            throws IOException {
        return ModAssetRoot.jar(temp, writePackage(temp, document, bank, entries));
    }

    private static Path writePackage(Path temp, String document, byte[] bank, Map<String, byte[]> entries)
            throws IOException {
        Path file = Files.createTempFile(temp, "package-", ".jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            write(zip, "manifest.json", utf8(document));
            write(zip, "track.bin", bank);
            for (var entry : entries.entrySet()) write(zip, entry.getKey(), entry.getValue());
        }
        return file;
    }

    private static void write(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(DIRECTORY + path));
        zip.write(bytes);
        zip.closeEntry();
    }

    static SmpsPackageReader reader(com.openggf.io.PackedModAssetRoot root, PackageStockResolver resolver)
            throws IOException {
        return new SmpsPackageReader(root, root.validatedEntryNames(), DIRECTORY, ModInputLimits.production(),
                resolver, warning -> {});
    }
}
