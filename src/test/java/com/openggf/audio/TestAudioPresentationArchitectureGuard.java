package com.openggf.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioPresentationArchitectureGuard {
    private static final Path AUDIO_ROOT =
            Path.of("src/main/java/com/openggf/audio");

    @Test
    void lwjglBackendDoesNotOwnIndependentMusicOrSfxSources()
            throws IOException {
        String source = Files.readString(
                AUDIO_ROOT.resolve("LWJGLAudioBackend.java"));
        for (String forbidden : List.of(
                "sfxSources", "musicSource", "playWav(",
                "AL_LOOPING", "AL_PITCH", "alSourcei(source, AL_BUFFER")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void openAlImportsExistOnlyInTheFinalPcmSink() throws IOException {
        try (var files = Files.walk(AUDIO_ROOT)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path)
                                    .contains("import org.lwjgl.openal");
                        } catch (IOException failure) {
                            throw new IllegalStateException(failure);
                        }
                    })
                    .filter(path -> !path.endsWith("OpenAlPcmSink.java"))
                    .toList();
            assertTrue(offenders.isEmpty(), offenders::toString);
        }
    }

    @Test
    void finalPcmSinkOwnsOneStereoPresentationSource()
            throws IOException {
        String source = Files.readString(
                AUDIO_ROOT.resolve("output/OpenAlPcmSink.java"));
        assertEquals(1, Pattern.compile(
                        "\\bint\\s+presentationSource\\b")
                .matcher(source).results().count());
        assertTrue(source.contains(
                "import com.openggf.audio.presentation.AudioPresentationFrameView;"));
        assertTrue(source.contains(
                "void accept(AudioPresentationFrameView frame)"));
        assertTrue(source.contains("AL_FORMAT_STEREO16"));
        assertFalse(source.contains("AL_FORMAT_MONO"));
        assertFalse(source.contains("AL_LOOPING"));
        assertFalse(source.contains("AL_PITCH"));
    }

    @Test
    void productionPumpsTheSpeakerExactlyOnceAtTheOuterFrameBoundary()
            throws IOException {
        Path productionRoot = Path.of("src/main/java/com/openggf");
        try (var files = Files.walk(productionRoot)) {
            List<String> pumps = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream()
                                    .filter(line -> line.contains(
                                            "GameServices.audio().update()"))
                                    .map(line -> path + ": " + line.trim());
                        } catch (IOException failure) {
                            throw new IllegalStateException(failure);
                        }
                    })
                    .toList();
            assertEquals(List.of(
                    Path.of("src/main/java/com/openggf/Engine.java")
                            + ": GameServices.audio().update();"), pumps);
        }
    }

    @Test
    void backendHasNoSecondCoordFlagOwnerOrCompatibilityFactory()
            throws IOException {
        String source = Files.readString(
                AUDIO_ROOT.resolve("AbstractSmpsAudioBackend.java"));
        assertFalse(source.contains("legacyCoordFlagHandlers"));
        assertFalse(source.contains("inactiveCoordFlagHandlers"));
        assertFalse(source.contains("createLegacySourceFactory"));
    }
}
