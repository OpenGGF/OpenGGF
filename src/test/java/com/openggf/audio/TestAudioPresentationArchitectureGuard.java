package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationParityProbe;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.runtime.AudioFrameClock;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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
     * The v2 timeline schema has no production-audio data dependency. Future
     * entries need a plan-mandated immutable/read-only interface and an
     * explicit review here; mutating owners are never allow-listed.
     */
    private static final Set<String> TIMELINE_READ_ONLY_AUDIO_DEPENDENCIES = Set.of();

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
    void smpsOwnershipDetectorRejectsRepresentativeHotPathRegressions() {
        Map<String, String> sources = representativeSafeSmpsSources();
        sources.put("audio/smps/DacData.java",
                "public byte[] rawSample() { return bytes; }");
        sources.put("audio/smps/SmpsProgramView.java",
                "public int[] rawPointers();");
        sources.put("audio/presentation/AudioPresentationSourceFactory.java",
                "void freshSource() {} void copyDac() {} "
                        + "void newSequencer() { SmpsSourceDescriptor.from(data); }");
        sources.put("audio/presentation/AudioPresentationCommandResolver.java",
                "private Object resolveSmpsSfxCommand() { loadNow(); "
                        + "factory.findRegisteredSmpsSfxAsset(key, generation); } "
                        + "private Object loadNow() { return loader.load(); }");
        sources.put("audio/presentation/AudioVoiceRegistry.java",
                "private void addSmpsSfxToOwner() { "
                        + "captureLiveCommandMutation(); }");
        sources.put("audio/driver/SmpsDriver.java",
                "public void commitSfxAdmission() { "
                        + "hasChipWriteObserver(); "
                        + "captureLiveCommandMutation(); }");
        sources.put("audio/AudioManager.java",
                safeAudioManagerClassificationMethods()
                        + " private boolean ensureRegisteredSmpsSfx() { "
                        + "loadAsset(); catalog.findRegisteredSmpsSfxAsset(); "
                        + "return true; } "
                        + "private Object loadAsset() { "
                        + "return source.loader().loadSfx(1); }");

        assertEquals(List.of(
                "public raw DAC array @ audio/smps/DacData.java",
                "public raw SMPS array @ audio/smps/SmpsProgramView.java",
                "freshSource/copyDac @ audio/presentation/AudioPresentationSourceFactory.java",
                "load before lookup @ audio/presentation/AudioPresentationCommandResolver.java",
                "load before lookup @ audio/AudioManager.java",
                "observer-free registry snapshot @ audio/presentation/AudioVoiceRegistry.java",
                "warmed descriptor materialization @ audio/presentation/AudioPresentationSourceFactory.java",
                "unguarded driver snapshot @ audio/driver/SmpsDriver.java"),
                smpsOwnershipViolations(sources));
    }

    @Test
    void productionSmpsOwnershipKeepsAssetWorkOutOfTheWarmedPath()
            throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String relative : List.of(
                "audio/smps/DacData.java",
                "audio/smps/SmpsProgramView.java",
                "audio/AudioManager.java",
                "audio/presentation/AudioPresentationSourceFactory.java",
                "audio/presentation/AudioPresentationCommandResolver.java",
                "audio/presentation/AudioVoiceRegistry.java",
                "audio/driver/SmpsDriver.java")) {
            sources.put(relative,
                    Files.readString(PRODUCTION_ROOT.resolve(relative)));
        }
        assertEquals(List.of(), smpsOwnershipViolations(sources));
    }

    @Test
    void smpsOwnershipDetectorFailsClosedOnRenamedMethods() {
        Map<String, String> sources = representativeSafeSmpsSources();
        sources.put("audio/AudioManager.java",
                safeAudioManagerClassificationMethods().replace(
                        "ensureRegisteredSmpsSfx(", "renamedOwner(")
                        + " private boolean renamedOwner() { return true; }");
        sources.put("audio/driver/SmpsDriver.java",
                "public void renamedAdmission() {}");

        List<String> violations = smpsOwnershipViolations(sources);

        assertTrue(violations.contains(
                "missing method ensureRegisteredSmpsSfx( @ audio/AudioManager.java"));
        assertTrue(violations.contains(
                "missing method commitSfxAdmission( @ audio/driver/SmpsDriver.java"));
    }

    @Test
    void smpsOwnershipDetectorIgnoresCommentsAndRequiresObserverControlFlow() {
        Map<String, String> sources = representativeSafeSmpsSources();
        sources.put("audio/driver/SmpsDriver.java",
                "public void commitSfxAdmission() { "
                        + "String lie = \"hasChipWriteObserver() ? \"; "
                        + "// captureLiveCommandMutation()\n"
                        + "captureLiveCommandMutation(); }");

        assertTrue(smpsOwnershipViolations(sources).contains(
                "unguarded driver snapshot @ audio/driver/SmpsDriver.java"));
    }

    private static Map<String, String> representativeSafeSmpsSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("audio/smps/DacData.java", "final class DacData {}");
        sources.put("audio/smps/SmpsProgramView.java",
                "interface SmpsProgramView {}");
        sources.put("audio/AudioManager.java",
                safeAudioManagerClassificationMethods()
                        + " private boolean ensureRegisteredSmpsSfx() { "
                        + "catalog.findRegisteredSmpsSfxAsset(); "
                        + "return source.loader().loadSfx(1) != null; }");
        sources.put("audio/presentation/AudioPresentationSourceFactory.java",
                "private void newSequencer() {}");
        sources.put("audio/presentation/AudioPresentationCommandResolver.java",
                "private Object resolveSmpsSfxCommand() { "
                        + "factory.findRegisteredSmpsSfxAsset(); "
                        + "return loader.load(); }");
        sources.put("audio/presentation/AudioVoiceRegistry.java",
                "private void addSmpsSfxToOwner() {}");
        sources.put("audio/driver/SmpsDriver.java",
                "public void commitSfxAdmission() { Object state = "
                        + "hasChipWriteObserver() "
                        + "? captureLiveCommandMutation() : null; }");
        return sources;
    }

    private static String safeAudioManagerClassificationMethods() {
        return "public void playSfx(String sfxName, float pitch) { "
                + "ensureRegisteredSmpsSfx(); } "
                + "public void playSfx(GameSound sound, float pitch) { "
                + "playBaseSfx(); ensureRegisteredSmpsSfx(); } "
                + "public boolean playSfx(int sfxId, float pitch) { "
                + "return playBaseSfx(); } "
                + "public void playDonorSfx(String donorGameId, int sfxId) { "
                + "ensureRegisteredSmpsSfx(); }";
    }

    private static List<String> smpsOwnershipViolations(
            Map<String, String> sources) {
        List<String> violations = new ArrayList<>();
        String dac = sanitizedSource(
                source(sources, "audio/smps/DacData.java"));
        if (Pattern.compile(
                "\\bpublic\\s+(?:final\\s+)?(?:byte|short|int|long)\\[\\]"
                        + "\\s+\\w+")
                .matcher(dac).find()
                || Pattern.compile(
                "\\bpublic\\s+(?:final\\s+)?Map<[^>]*\\[\\][^>]*>")
                .matcher(dac).find()) {
            violations.add("public raw DAC array @ audio/smps/DacData.java");
        }

        String programView = sanitizedSource(source(
                sources, "audio/smps/SmpsProgramView.java"));
        if (Pattern.compile(
                "\\b(?:byte|short|int|long)\\[\\]\\s+\\w+\\s*\\(")
                .matcher(programView).find()) {
            violations.add(
                    "public raw SMPS array @ audio/smps/SmpsProgramView.java");
        }

        String factory = sanitizedSource(source(sources,
                "audio/presentation/AudioPresentationSourceFactory.java"));
        if (factory.contains("freshSource") || factory.contains("copyDac")) {
            violations.add("freshSource/copyDac @ "
                    + "audio/presentation/AudioPresentationSourceFactory.java");
        }

        String resolverSource = sanitizedSource(source(sources,
                "audio/presentation/AudioPresentationCommandResolver.java"));
        String resolver = requiredMethodBody(resolverSource,
                "resolveSmpsSfxCommand(",
                "audio/presentation/AudioPresentationCommandResolver.java",
                violations);
        if (resolver != null && !lookupPrecedesEveryLoad(
                resolverSource, "resolveSmpsSfxCommand")) {
            violations.add("load before lookup @ "
                    + "audio/presentation/AudioPresentationCommandResolver.java");
        }

        String managerSource = sanitizedSource(
                source(sources, "audio/AudioManager.java"));
        requireClassificationMethod(managerSource,
                "public void playSfx(String sfxName, float pitch)",
                "ensureRegisteredSmpsSfx(", violations);
        requireClassificationMethod(managerSource,
                "public void playSfx(GameSound sound, float pitch)",
                "ensureRegisteredSmpsSfx(", violations);
        requireClassificationMethod(managerSource,
                "public boolean playSfx(int sfxId, float pitch)",
                "playBaseSfx(", violations);
        requireClassificationMethod(managerSource,
                "public void playDonorSfx(String donorGameId, int sfxId)",
                "ensureRegisteredSmpsSfx(", violations);
        String managerOwner = requiredMethodBody(managerSource,
                "ensureRegisteredSmpsSfx(", "audio/AudioManager.java",
                violations);
        if (managerOwner != null && !lookupPrecedesEveryLoad(
                managerSource, "ensureRegisteredSmpsSfx")) {
            violations.add("load before lookup @ audio/AudioManager.java");
        }

        String registrySource = sanitizedSource(source(sources,
                "audio/presentation/AudioVoiceRegistry.java"));
        String registry = requiredMethodBody(registrySource,
                "addSmpsSfxToOwner(",
                "audio/presentation/AudioVoiceRegistry.java", violations);
        if (registry != null
                && registry.contains("captureLiveCommandMutation(")) {
            violations.add("observer-free registry snapshot @ "
                    + "audio/presentation/AudioVoiceRegistry.java");
        }

        String instantiation = requiredMethodBody(factory, "newSequencer(",
                "audio/presentation/AudioPresentationSourceFactory.java",
                violations);
        if (instantiation != null
                && (instantiation.contains("SmpsSourceDescriptor.from(")
                || instantiation.contains("getData()")
                || instantiation.contains("describeSfx("))) {
            violations.add("warmed descriptor materialization @ "
                    + "audio/presentation/AudioPresentationSourceFactory.java");
        }

        String driverSource = sanitizedSource(source(sources,
                "audio/driver/SmpsDriver.java"));
        String driver = requiredMethodBody(driverSource,
                "commitSfxAdmission(", "audio/driver/SmpsDriver.java",
                violations);
        if (driver != null && driver.contains("captureLiveCommandMutation(")
                && !observerControlsSnapshot(driver)) {
            violations.add("unguarded driver snapshot @ "
                    + "audio/driver/SmpsDriver.java");
        }
        return List.copyOf(violations);
    }

    private static String source(
            Map<String, String> sources, String path) {
        String source = sources.get(path);
        if (source == null) {
            throw new AssertionError("missing guard source " + path);
        }
        return source;
    }

    private static void requireClassificationMethod(
            String source, String marker, String ownerCall,
            List<String> violations) {
        String path = "audio/AudioManager.java";
        String body = requiredMethodBody(source, marker, path, violations);
        if (body != null && !body.contains(ownerCall)) {
            violations.add("classification bypass " + marker + " @ " + path);
        }
    }

    private static String requiredMethodBody(
            String source, String marker, String path,
            List<String> violations) {
        String body = methodBody(source, marker);
        if (body == null) {
            violations.add("missing method " + marker + " @ " + path);
        }
        return body;
    }

    private static String methodBody(String source, String marker) {
        int markerIndex = source.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int open = source.indexOf('{', markerIndex + marker.length());
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        return null;
    }

    private static boolean lookupPrecedesEveryLoad(
            String source, String rootMethod) {
        Map<String, String> methods = methodBodies(source);
        String root = methods.get(rootMethod);
        return root != null && inspectLookupOrder(
                root, methods, false, new HashSet<>()).valid();
    }

    private static LookupInspection inspectLookupOrder(
            String body,
            Map<String, String> methods,
            boolean lookupSeen,
            Set<String> activeMethods) {
        List<GuardEvent> events = new ArrayList<>();
        addEvents(events, body, "findRegisteredSmpsSfxAsset(", "lookup");
        addEvents(events, body, "loader.load(", "load");
        addEvents(events, body, ".loadSfx(", "load");
        for (String method : methods.keySet()) {
            addEvents(events, body, method + "(", "call:" + method);
        }
        events.sort(Comparator.comparingInt(GuardEvent::offset));
        boolean seen = lookupSeen;
        for (GuardEvent event : events) {
            if (event.kind().equals("lookup")) {
                seen = true;
            } else if (event.kind().equals("load")) {
                if (!seen) {
                    return new LookupInspection(false, seen);
                }
            } else {
                String called = event.kind().substring("call:".length());
                if (activeMethods.add(called)) {
                    LookupInspection nested = inspectLookupOrder(
                            methods.get(called), methods, seen,
                            activeMethods);
                    activeMethods.remove(called);
                    if (!nested.valid()) {
                        return nested;
                    }
                    seen |= nested.lookupSeen();
                }
            }
        }
        return new LookupInspection(true, seen);
    }

    private static void addEvents(
            List<GuardEvent> events, String body,
            String token, String kind) {
        int offset = body.indexOf(token);
        while (offset >= 0) {
            events.add(new GuardEvent(offset, kind));
            offset = body.indexOf(token, offset + token.length());
        }
    }

    private static Map<String, String> methodBodies(String source) {
        Pattern declarations = Pattern.compile(
                "(?:public|protected|private)\\s+(?:static\\s+)?"
                        + "(?:[\\w<>?.,\\[\\]]+\\s+)+"
                        + "([A-Za-z][A-Za-z0-9_]*)\\s*"
                        + "\\([^;{}]*\\)\\s*\\{");
        Matcher matcher = declarations.matcher(source);
        Map<String, String> methods = new LinkedHashMap<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            String body = bracedBody(source, source.indexOf('{', matcher.start()));
            methods.putIfAbsent(name, body);
        }
        return methods;
    }

    private static String bracedBody(String source, int open) {
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        return "";
    }

    private static boolean observerControlsSnapshot(String body) {
        return Pattern.compile("hasChipWriteObserver\\s*\\(\\s*\\)\\s*"
                        + "\\?\\s*captureLiveCommandMutation\\s*\\(")
                .matcher(body).find()
                || Pattern.compile("if\\s*\\(\\s*hasChipWriteObserver"
                        + "\\s*\\(\\s*\\)\\s*\\)\\s*\\{[^}]*"
                        + "captureLiveCommandMutation\\s*\\(",
                        Pattern.DOTALL).matcher(body).find();
    }

    private static String sanitizedSource(String source) {
        StringBuilder clean = new StringBuilder(source.length());
        int state = 0;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            char next = index + 1 < source.length()
                    ? source.charAt(index + 1) : '\0';
            if (state == 0 && value == '/' && next == '/') {
                clean.append("  ");
                index++;
                state = 1;
            } else if (state == 0 && value == '/' && next == '*') {
                clean.append("  ");
                index++;
                state = 2;
            } else if (state == 0 && (value == '"' || value == '\'')) {
                clean.append(' ');
                state = value == '"' ? 3 : 4;
            } else if (state == 1 && (value == '\n' || value == '\r')) {
                clean.append(value);
                state = 0;
            } else if (state == 2 && value == '*' && next == '/') {
                clean.append("  ");
                index++;
                state = 0;
            } else if ((state == 3 || state == 4) && value == '\\') {
                clean.append(' ');
                if (++index < source.length()) {
                    clean.append(' ');
                }
            } else if ((state == 3 && value == '"')
                    || (state == 4 && value == '\'')) {
                clean.append(' ');
                state = 0;
            } else {
                clean.append(state == 0 ? value : ' ');
            }
        }
        return clean.toString();
    }

    private record GuardEvent(int offset, String kind) {
    }

    private record LookupInspection(boolean valid, boolean lookupSeen) {
    }

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

    @Test
    void noRuntimeInstallationOrCaptureLeaseSwitchRemains() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String token : FORBIDDEN_RUNTIME_TOKENS) {
            offenders.addAll(
                    productionOffenders(token, Set.of(), Set.of()));
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
                    "setForwardPlaybackRate",
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

    @Test
    void gameplayAudioTimelineIsToolingOnlyAndCannotDriveAudioOrTraceAuthority()
            throws IOException {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.openggf");
        assertEquals(List.of(), timelineDependenciesOutsideTimeline(production),
                "production runtime must not depend on gameplay-audio timeline tooling");

        JavaClasses timeline = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.openggf.tools.audio.timeline");
        assertEquals(List.of(), timelineAuthorityCalls(timeline),
                "timeline tooling must remain a read-only schema boundary");
        assertEquals(List.of(), timelineAudioOwnerDependencies(timeline),
                "timeline tooling may not depend on mutation-capable audio owners");

        JavaClasses fixture = new ClassFileImporter()
                .importClasses(RepresentativeTimelineAuthorityBypass.class);
        assertEquals(1, timelineDependenciesOutsideTimeline(fixture).size(),
                "fully-qualified timeline references must be visible as class dependencies");
        assertEquals(13, timelineAuthorityCalls(fixture).size(),
                "representative direct audio mutation/advance calls must be visible");

        JavaClasses ownerFixture = new ClassFileImporter()
                .importClasses(RepresentativeTimelineAudioOwnerBypass.class);
        assertEquals(7, timelineAudioOwnerDependencies(ownerFixture).size(),
                "fully-qualified mutation-capable audio owners must be denied by ownership");
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

    private static List<String> timelineDependenciesOutsideTimeline(JavaClasses classes) {
        return classes.stream()
                .filter(origin -> !origin.getPackageName().startsWith("com.openggf.tools.audio.timeline"))
                .flatMap(origin -> origin.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName()
                        .startsWith("com.openggf.tools.audio.timeline"))
                .map(Dependency::getDescription)
                .sorted()
                .toList();
    }

    private static List<String> timelineAuthorityCalls(JavaClasses classes) {
        return classes.stream()
                .flatMap(origin -> origin.getMethodCallsFromSelf().stream())
                .filter(call -> isTimelineAuthorityCall(call, call.getTargetOwner()))
                .map(JavaMethodCall::getDescription)
                .sorted()
                .toList();
    }

    private static List<String> timelineAudioOwnerDependencies(JavaClasses classes) {
        return classes.stream()
                .flatMap(origin -> origin.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName()
                        .startsWith("com.openggf.audio"))
                .filter(dependency -> !TIMELINE_READ_ONLY_AUDIO_DEPENDENCIES
                        .contains(dependency.getTargetClass().getFullName()))
                .map(Dependency::getDescription)
                .sorted()
                .toList();
    }

    private static boolean isTimelineAuthorityCall(JavaMethodCall call, JavaClass targetOwner) {
        if (targetOwner.isEquivalentTo(AudioManager.class)) {
            return Set.of("playMusic", "playSfx", "replayTimelineCommand",
                    "replayTimelineCommandLogically", "restoreLogicalSnapshot", "presentFrame", "update")
                    .contains(call.getName());
        }
        if (targetOwner.isEquivalentTo(AudioPresentationProducer.class)) {
            return call.getName().equals("present");
        }
        if (targetOwner.isEquivalentTo(AudioKeyframeStore.class)) {
            return call.getName().equals("replayTo") || call.getName().equals("replayToLogicalState");
        }
        if (targetOwner.isAssignableTo(AudioBackend.class)) {
            return Set.of("playMusic", "playSfx", "update").contains(call.getName());
        }
        return targetOwner.getPackageName().startsWith("com.openggf.trace.timing");
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

    private static final class RepresentativeTimelineAuthorityBypass {
        @SuppressWarnings("unused")
        private final com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.Metadata metadata = null;

        private void bypass(com.openggf.audio.AudioManager audio,
                com.openggf.audio.presentation.AudioPresentationProducer producer,
                com.openggf.audio.rewind.AudioKeyframeStore keyframes,
                com.openggf.audio.AudioBackend backend) {
            audio.playMusic(0x81);
            audio.playSfx("ring");
            audio.replayTimelineCommand(null);
            audio.replayTimelineCommandLogically(null);
            audio.restoreLogicalSnapshot(null);
            audio.presentFrame(null);
            audio.update();
            producer.present(0, null);
            keyframes.replayTo(null, 0, null);
            keyframes.replayToLogicalState(null, 0);
            backend.playMusic(0x81);
            backend.playSfx("ring");
            backend.update();
        }
    }

    private static final class RepresentativeTimelineAudioOwnerBypass {
        private void bypass(com.openggf.audio.AudioManager audio,
                com.openggf.audio.debug.StandaloneAudioPresentationHost host) {
            audio.playStandaloneMusic(null, null);
            audio.playStandaloneSfx(null, null, 1.0f);
            host.playMusic(null, null);
            host.playSfx(null, null, 1.0f);
            host.presentFrame();
        }
    }
}
