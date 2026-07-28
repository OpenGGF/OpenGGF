package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationParityProbe;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.runtime.AudioFrameClock;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
     *
     * <p>Matched on the repo-relative path, not the bare file name, so a new
     * production file that merely happens to share one of these names does not
     * inherit the exemption.
     */
    private static final Set<String> AUDIO_FRAME_CLOCK_OWNERS = Set.of(
            "audio/presentation/AudioPresentationProducer.java",
            "audio/ClockedSilenceAudioHandle.java",
            "audio/presentation/AudioPresentationParityProbe.java");

    private static final Set<String> PCM_HISTORY_OWNERS = Set.of(
            "audio/presentation/AudioPresentationProducer.java");

    private static final Set<String> PCM_HISTORY_ITSELF = Set.of(
            "audio/runtime/PcmHistoryRing.java");

    private static final Set<String> FINAL_PCM_SINK = Set.of(
            "audio/output/OpenAlPcmSink.java");

    /** The one production site allowed to publish a presented packet. */
    private static final Set<String> PRESENTATION_FAN_OUT_OWNER = Set.of(
            "audio/presentation/AudioPresentationProducer.java");

    /** The listener contract itself, which only declares the callback. */
    private static final Set<String> PRESENTATION_LISTENER_CONTRACT = Set.of(
            "audio/presentation/AudioPresentationListener.java");

    /**
     * The manager-only reverse-release failure injection. It is consumed on
     * every reverse release and reset by {@code resetState()}, but a second
     * production reference could arm it and abort a real held-rewind release,
     * so exactly one production file may name it.
     */
    private static final Set<String> REVERSE_RELEASE_INJECTION_OWNER = Set.of(
            "audio/AudioManager.java");

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
                        PCM_HISTORY_OWNERS, PCM_HISTORY_ITSELF),
                "PcmHistoryRing construction outside the producer");
        // No trailing space: a reintroduced second cursor is at least as likely
        // to appear as `new PcmHistoryRing.ReverseCursor(...)`, a generic type
        // argument or a cast as it is as a bare field declaration.
        assertEquals(List.of(),
                productionOffenders("PcmHistoryRing.ReverseCursor",
                        PCM_HISTORY_OWNERS, PCM_HISTORY_ITSELF),
                "PcmHistoryRing reverse cursor ownership outside the producer");

        // The parity probe is exempted from the clock allow-list only because
        // it mirrors cadence; bound that exemption by proving its clock never
        // escapes to drive anything.
        for (var method : AudioPresentationParityProbe.class.getMethods()) {
            assertNotEquals(AudioFrameClock.class, method.getReturnType(),
                    "the parity probe must not hand out its mirror clock: "
                            + method.getName());
        }
    }

    /**
     * The FBZ visual-capture tooling has its own {@code captureRuntime()} that
     * snapshots visual state. It is not the retired audio runtime installation
     * these tokens guard, and the scan matches on substrings.
     */
    private static final Set<String> UNRELATED_CAPTURE_RUNTIME_PATHS = Set.of(
            "com/openggf/tools/fbzvisual/FbzGameServicesFixturePort.java",
            "com/openggf/tools/fbzvisual/FbzVisualStateProbe.java",
            "com/openggf/tools/fbzvisual/HiddenGlCaptureSession.java");

    @Test
    void noRuntimeInstallationOrCaptureLeaseSwitchRemains() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String token : FORBIDDEN_RUNTIME_TOKENS) {
            offenders.addAll(
                    productionOffenders(token, Set.of(), UNRELATED_CAPTURE_RUNTIME_PATHS));
        }
        assertEquals(List.of(), offenders.stream().sorted().distinct().toList(),
                "superseded runtime installation / capture-lease switch tokens");

        assertEquals(List.of(),
                productionOffenders("failNextReverseRelease",
                        REVERSE_RELEASE_INJECTION_OWNER, Set.of()),
                "the reverse-release failure injection is manager-private");
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

    /**
     * The speaker and every capture lease must be fed by the one producer
     * fan-out, from the one packet that presentation just selected. A second
     * production site that handed a frame view to a sink or a listener could
     * give the recorder a different packet from the speaker's — exactly the
     * split the ROM/integration parity tests assert cannot happen — so the fan
     * out is pinned to the producer here rather than only observed in tests.
     */
    @Test
    void onlyTheProducerFansOnePacketOutToSpeakerAndCaptureConsumers()
            throws IOException {
        assertEquals(List.of(),
                productionOffenders("sink.accept(",
                        PRESENTATION_FAN_OUT_OWNER, Set.of()),
                "speaker packets are handed out only by the producer");
        assertEquals(List.of(),
                productionOffenders("onPresentationFrame(",
                        PRESENTATION_FAN_OUT_OWNER,
                        PRESENTATION_LISTENER_CONTRACT),
                "capture packets are handed out only by the producer");
    }

    @Test
    void noVoiceWritesDirectlyToOpenAl() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String token : OPENAL_TOKENS) {
            offenders.addAll(productionOffenders(
                    token, FINAL_PCM_SINK, Set.of()));
        }
        assertEquals(List.of(), offenders.stream().sorted().distinct().toList(),
                "OpenAL is written to only by the single final-PCM sink");
    }

    /**
     * @param allowedPaths repo-relative path suffixes (e.g.
     *                     {@code "audio/presentation/X.java"}) permitted to
     *                     contain {@code token}; matched on the path so a
     *                     same-named file elsewhere is not exempted
     */
    private static List<String> productionOffenders(
            String token, Set<String> allowedPaths,
            Set<String> ignoredPaths) throws IOException {
        try (var files = Files.walk(PRODUCTION_ROOT)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !matchesAny(path, allowedPaths))
                    .filter(path -> !matchesAny(path, ignoredPaths))
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

    private static boolean matchesAny(Path path, Set<String> pathSuffixes) {
        String normalized = path.toString().replace('\\', '/');
        return pathSuffixes.stream()
                .anyMatch(suffix -> normalized.endsWith("/" + suffix));
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
