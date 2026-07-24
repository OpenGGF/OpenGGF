package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioPresentationArchitectureGuard {
    private static final Path AUDIO_ROOT =
            Path.of("src/main/java/com/openggf/audio");
    private static final Path PRODUCTION_ROOT =
            Path.of("src/main/java/com/openggf");
    private static final Set<String> BACKEND_COMMANDS = Set.of(
            "playMusic", "playSfx", "playSmps", "playSfxSmps",
            "toggleMute", "toggleSolo", "isMuted", "isSoloed");

    /**
     * Superseded split-runtime / recording-lease-switch identifiers. None may
     * survive anywhere in production once the unified presentation producer is
     * the sole owner of cadence, final PCM, history and capture leases.
     */
    private static final List<String> FORBIDDEN_RUNTIME_TOKENS = List.of(
            "DeterministicAudioRuntime",
            "setDeterministicAudioRuntime",
            "applyDeterministicAudioRuntime",
            "supportsDeterministicRuntimePresentation",
            "supportsLiveCapturePresentation",
            "presentationHandoff",
            "handoffMusicData",
            "handoffSfxData",
            "runtimeProvidesPresentationPcm",
            "deferredLiveCaptureRuntime",
            "preCaptureRuntime",
            "captureRuntime");

    /**
     * Files allowed to construct an {@link
     * com.openggf.audio.runtime.AudioFrameClock}. The producer owns the
     * presentation clock and its capture-handle internals; the clocked-silence
     * degradation handle continues a detached capture lease at the producer's
     * phase; the parity probe is a read-only presentation-package diagnostic
     * that mirrors — and never drives — that cadence.
     */
    private static final Set<String> AUDIO_FRAME_CLOCK_OWNERS = Set.of(
            "AudioPresentationProducer.java",
            "ClockedSilenceAudioHandle.java",
            "AudioPresentationParityProbe.java");

    private static final Set<String> PCM_HISTORY_OWNERS = Set.of(
            "AudioPresentationProducer.java");

    private static final List<String> OPENAL_TOKENS = List.of(
            "import org.lwjgl.openal",
            "alGenSources",
            "alSourcePlay",
            "alSourceQueueBuffers",
            "alSourceUnqueueBuffers",
            "alBufferData",
            "alSourcei(",
            "AL_LOOPING",
            "AL_PITCH");

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

    @Test
    void onlyProducerOwnsAudioFrameClockAndPcmHistory() throws IOException {
        assertEquals(List.of(),
                productionOffenders("new AudioFrameClock(",
                        AUDIO_FRAME_CLOCK_OWNERS, Set.of()),
                "AudioFrameClock construction outside the producer, its "
                        + "capture handles, and the clocked-silence handle");
        assertEquals(List.of(),
                productionOffenders("new PcmHistoryRing(",
                        PCM_HISTORY_OWNERS, Set.of("PcmHistoryRing.java")),
                "PcmHistoryRing construction outside the producer");
        assertEquals(List.of(),
                productionOffenders("PcmHistoryRing.ReverseCursor ",
                        PCM_HISTORY_OWNERS, Set.of("PcmHistoryRing.java")),
                "PcmHistoryRing reverse cursor ownership outside the producer");
    }

    @Test
    void noRuntimeInstallationOrCaptureLeaseSwitchRemains() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String token : FORBIDDEN_RUNTIME_TOKENS) {
            offenders.addAll(
                    productionOffenders(token, Set.of(), Set.of()));
        }
        assertEquals(List.of(), offenders.stream().sorted().distinct().toList(),
                "superseded runtime installation / capture-lease switch tokens");
    }

    @Test
    void backendHasNoPresentationHandoffOrReverseCursor() throws IOException {
        for (String backendFile : List.of(
                "AudioBackend.java",
                "AbstractSmpsAudioBackend.java",
                "LWJGLAudioBackend.java",
                "HeadlessSmpsAudioBackend.java",
                "NullAudioBackend.java")) {
            String source = Files.readString(AUDIO_ROOT.resolve(backendFile));
            for (String forbidden : List.of(
                    "presentationHandoff",
                    "handoffMusicData",
                    "handoffSfxData",
                    "pcmHistory",
                    "PcmHistoryRing",
                    "reverseCursor",
                    "ReverseCursor",
                    "AudioBackendLogicalSnapshot",
                    "DeterministicAudioRuntime",
                    "beginReversePresentation",
                    "endReversePresentation",
                    "setReversePlaybackRate",
                    "setRewindHistoryArmed")) {
                assertFalse(source.contains(forbidden),
                        backendFile + " still owns " + forbidden);
            }
        }
    }

    @Test
    void noVoiceWritesDirectlyToOpenAl() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String token : OPENAL_TOKENS) {
            offenders.addAll(productionOffenders(
                    token, Set.of("OpenAlPcmSink.java"), Set.of()));
        }
        assertEquals(List.of(), offenders.stream().sorted().distinct().toList(),
                "OpenAL is written to only by the single final-PCM sink");
    }

    private static List<String> productionOffenders(
            String token, Set<String> allowedFileNames,
            Set<String> ignoredFileNames) throws IOException {
        try (var files = Files.walk(PRODUCTION_ROOT)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !allowedFileNames.contains(
                            path.getFileName().toString()))
                    .filter(path -> !ignoredFileNames.contains(
                            path.getFileName().toString()))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(token);
                        } catch (IOException failure) {
                            throw new IllegalStateException(failure);
                        }
                    })
                    .map(path -> token + " @ " + path)
                    .sorted()
                    .toList();
        }
    }

    @Test
    void productionDoesNotBypassManagerOwnedAudioCommands() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.openggf");

        assertEquals(List.of(), directBackendCommandBypasses(production));
    }

    @Test
    void directBackendGuardRejectsRepresentativeBypass() {
        JavaClasses fixture = new ClassFileImporter()
                .importClasses(RepresentativeBackendBypass.class);

        List<String> bypasses = directBackendCommandBypasses(fixture);

        assertEquals(2, bypasses.size(), bypasses::toString);
        assertTrue(bypasses.stream().anyMatch(
                call -> call.contains(".playMusic(")));
        assertTrue(bypasses.stream().anyMatch(
                call -> call.contains(".toggleMute(")));
    }

    private static List<String> directBackendCommandBypasses(
            JavaClasses classes) {
        return classes.stream()
                .flatMap(owner -> owner.getMethodCallsFromSelf().stream())
                .filter(TestAudioPresentationArchitectureGuard
                        ::targetsBackendCommand)
                .filter(call -> !isApprovedBackendCommandOwner(
                        call.getOriginOwner()))
                .map(JavaMethodCall::getDescription)
                .sorted()
                .toList();
    }

    private static boolean targetsBackendCommand(JavaMethodCall call) {
        return BACKEND_COMMANDS.contains(call.getName())
                && call.getTargetOwner().isAssignableTo(AudioBackend.class);
    }

    private static boolean isApprovedBackendCommandOwner(
            com.tngtech.archunit.core.domain.JavaClass owner) {
        return owner.isEquivalentTo(AudioManager.class)
                || owner.isEquivalentTo(AudioPresentationSourceFactory.class)
                || owner.isAssignableTo(AudioBackend.class);
    }

    private static final class RepresentativeBackendBypass {
        private final AudioBackend backend = new NullAudioBackend();

        private void bypass() {
            backend.playMusic(1);
            backend.toggleMute(ChannelType.FM, 0);
        }
    }
}
