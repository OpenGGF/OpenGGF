package com.openggf.mods;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.NativeMusicOverridePort;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsMusicParameters;
import com.openggf.data.Rom;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSmpsPackageInterop {
    @TempDir Path temp;

    @ParameterizedTest
    @CsvSource({"s1,native-controls,301", "s2,native-controls,295", "s3k,dac-percussion,297"})
    void openSmpsDeckExportPlaysDeterministicallyThroughNativePresentation(
            String game, String example, int dacCycles) throws Exception {
        var song = load(game, example, true);
        assertEquals(dacCycles, song.dac().baseCycles());
        short[] pcm = render(game, song, true);
        assertFalse(Arrays.equals(new short[pcm.length], pcm), "The package must produce audible PCM");
        assertArrayEquals(pcm, render(game, song, true));

        if (!game.equals("s3k")) {
            var unaffected = load(game, example, false);
            assertArrayEquals(render(game, unaffected, false), render(game, unaffected, true));
        }
    }

    private PreparedSmpsTrack load(String game, String example, boolean tempoEffects) throws Exception {
        String directory = TestSmpsPackageReader.DIRECTORY;
        byte[] yaml = """
                formatVersion: 2
                tracks:
                  - id: song
                    smpsPackage: %s
                    gain: 1.0
                    tempoEffects: %s
                sfx: []
                """.formatted(directory, tempoEffects).getBytes(StandardCharsets.UTF_8);
        Path fixture = Path.of(getClass().getResource("/mods/smps-package-interop/" + game + "-" + example).toURI());
        byte[] manifestBytes = Files.readAllBytes(fixture.resolve("manifest.json"));
        var metadata = TestSmpsPackageReader.JSON.readTree(manifestBytes);
        assertFalse(metadata.path("provenance").path("containsRomDerivedData").booleanValue());
        assertFalse(metadata.path("provenance").path("containsRetailSongData").booleanValue());
        Path jar = Files.createTempFile(temp, "interop-", ".jar");
        var binaryEntries = new Properties();
        try (var encoded = Files.newInputStream(fixture.resolve("binary-assets.properties"))) {
            binaryEntries.load(encoded);
        }
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            write(zip, "audio/audio-manifest.yaml", yaml);
            write(zip, directory + "manifest.json", manifestBytes);
            for (String name : binaryEntries.stringPropertyNames().stream().sorted().toList()) {
                write(zip, directory + name, Base64.getDecoder().decode(binaryEntries.getProperty(name)));
            }
        }
        try (var root = ModAssetRoot.jar(temp, jar)) {
            var declarations = new ModAudioManifestParser("fixture").parse(
                    root.readBounded("audio/audio-manifest.yaml", 4096));
            var manifest = new ModManifest(1, "fixture", "Fixture", SemanticVersion.parse("1.0.0"),
                    List.of("Author"), "Interop fixture", VersionRange.parse("*"), ModType.PATCH, game, null,
                    List.of(), Map.of(0x81, "song"), Map.of(), null, OptionalInt.empty());
            var descriptor = new ModDescriptor(jar, manifest, root.immutableSha256(), false, List.of());
            var catalog = new EffectiveModCatalog(List.of(descriptor));
            var registry = new ModTrackRegistry(declarations.tracks());
            var findings = new ModRuntimeFindingStore();
            var preparer = new ModAudioPreparer(temp, ModInputLimits.production(), findings,
                    owners -> fail("The package must not disable its owner"));
            try (var session = preparer.prepare(catalog, registry, ModSfxRegistry.EMPTY, 48_000, game, null);
                 var music = PreparedModMusic.build(catalog, registry, session, 48_000)) {
                var song = music.resolveSmpsOverride(game, 0x81);
                assertNotNull(song, () -> findings.findingsFor("fixture").toString());
                return song;
            }
        }
    }

    private static void write(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static short[] render(String game, PreparedSmpsTrack song, boolean speedShoes) {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        try {
            SmpsLoader loader = mock(SmpsLoader.class);
            when(loader.loadDacData()).thenReturn(new DacData(Map.of(), Map.of(), song.dac().baseCycles()));
            GameAudioProfile profile = switch (game) {
                case "s1" -> new Sonic1AudioProfile() {
                    @Override public SmpsLoader createSmpsLoader(Rom rom) { return loader; }
                };
                case "s2" -> new Sonic2AudioProfile() {
                    @Override public SmpsLoader createSmpsLoader(Rom rom) { return loader; }
                };
                case "s3k" -> new Sonic3kAudioProfile() {
                    @Override public SmpsLoader createSmpsLoader(Rom rom) { return loader; }
                };
                default -> throw new IllegalArgumentException(game);
            };
            audio.setAudioProfile(profile);
            audio.setRom(new Rom());
            var port = mock(NativeMusicOverridePort.class);
            when(port.outputRate()).thenReturn(audio.outputSampleRate());
            when(port.nativeMusicOverride(0x81)).thenReturn(new NativeMusicOverridePort.Song(
                    new StreamedMusicPort.TrackRef("fixture", "song"), song.data(), song.dac()));
            audio.installStreamedMusicPort(port);
            audio.beginCaptureMode(audio.outputSampleRate(), audio.presentationFrameRate());
            short[] pcm = new short[4096 * 32];
            short[] frame = new short[4096];
            for (int i = 0; i < 32; i++) {
                audio.beginCommandTimelineFrame(i + 1);
                if (i == 0 && speedShoes) audio.setSpeedShoes(true);
                if (i == 1) audio.playMusic(0x81);
                if (i == 8) audio.setSpeedShoes(false);
                if (i == 16 && speedShoes) audio.setSpeedShoes(true);
                audio.presentFrame(PresentationMode.FORWARD);
                Arrays.fill(frame, (short) 0);
                audio.drainCaptureFrame(frame);
                System.arraycopy(frame, 0, pcm, i * frame.length, frame.length);
                if (i == 1 || i == 8 || i == 16) {
                    var source = audio.captureLogicalSnapshot().presentation().smpsLogical().sequencers().getFirst();
                    assertEquals(SmpsSourceDescriptor.Kind.PACKAGE_MUSIC, source.source().kind());
                    if (!game.equals("s3k")) {
                        boolean enabled = speedShoes && i != 8 && SmpsMusicParameters.of(song.data()).tempoEffects();
                        assertEquals(enabled ? 48 : song.data().getTempo(), source.snapshot().tempoWeight());
                    }
                }
            }
            return pcm;
        } finally {
            audio.endCaptureMode();
            audio.resetState();
        }
    }
}
