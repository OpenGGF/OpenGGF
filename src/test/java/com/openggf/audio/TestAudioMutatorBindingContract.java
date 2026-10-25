package com.openggf.audio;

import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.data.Rom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Original-production binding protocol, pinned at 03e20f57 (2026-10-25).
 * ROM-free ordering probes use the existing virtual/package native seams; native
 * programs, PCM and world lifetime remain covered by the maintained ROM tests.
 */
public class TestAudioMutatorBindingContract {
    private static final String SECURITY = "Native audio bindings belong to the engine";
    private final List<AudioManager> managers = new ArrayList<>();
    private final List<AudioMutatorAccess.Binding> tokens = new ArrayList<>();

    @AfterEach void cleanup() {
        for (var token : tokens) token.close();
        for (var audio : managers) {
            audio.setStreamedMusicSessionInvalidator(() -> { });
            audio.resetState();
            audio.destroy();
        }
    }

    private AudioManager manager() {
        var audio = spy(AudioManager.class); // Construct the actual receiver; never copy final owners.
        managers.add(audio);
        return audio;
    }

    private AudioManager preparedSeam() {
        var audio = manager();
        doNothing().when(audio).prepareMutatorCue(anyInt(), anyBoolean());
        doReturn(false).when(audio).mutatorWarningActive();
        doNothing().when(audio).playMusic(anyInt());
        doReturn(true).when(audio).playSfx(anyInt());
        return audio;
    }

    private AudioMutatorAccess.Binding bind(AudioManager audio, AudioMutatorAccess.Listener listener) {
        var token = AudioMutatorAccess.bind(audio, listener);
        tokens.add(token);
        return token;
    }

    enum Entry {
        permitsMusic, isOverride, isWarning, warningActive, prepareMusic,
        prepareLandingCue, playTemporaryMusic, resumeNativeMusic, playLandingCue, close
    }
    enum Call { DIRECT, HIDDEN, REFLECTION }
    enum State { CURRENT, INACTIVE, OTHER_THREAD, INACTIVE_OTHER_THREAD;
        boolean inactive() { return this == INACTIVE || this == INACTIVE_OTHER_THREAD; }
        boolean offThread() { return this == OTHER_THREAD || this == INACTIVE_OTHER_THREAD; }
    }

    // These classes are redefined from committed class resources in one owned loader.
    // In particular, no engine helper stands between the foreign frame and the boundary.
    public static final class Probe {
        public static Object direct(String entry, AudioMutatorAccess.Binding b) {
            return switch (entry) {
                case "permitsMusic" -> b.permitsMusic(3);
                case "isOverride" -> b.isOverride(3);
                case "isWarning" -> b.isWarning(3);
                case "warningActive" -> b.warningActive();
                case "prepareMusic" -> { b.prepareMusic(3); yield null; }
                case "prepareLandingCue" -> { b.prepareLandingCue(0x49); yield null; }
                case "playTemporaryMusic" -> { b.playTemporaryMusic(3); yield null; }
                case "resumeNativeMusic" -> { b.resumeNativeMusic(3); yield null; }
                case "playLandingCue" -> { b.playLandingCue(0x49); yield null; }
                case "close" -> { b.close(); yield null; }
                default -> throw new AssertionError(entry);
            };
        }
        public static Runnable hidden(String entry, AudioMutatorAccess.Binding b) {
            return switch (entry) {
                case "permitsMusic" -> () -> b.permitsMusic(3);
                case "isOverride" -> () -> b.isOverride(3);
                case "isWarning" -> () -> b.isWarning(3);
                case "warningActive" -> b::warningActive;
                case "prepareMusic" -> () -> b.prepareMusic(3);
                case "prepareLandingCue" -> () -> b.prepareLandingCue(0x49);
                case "playTemporaryMusic" -> () -> b.playTemporaryMusic(3);
                case "resumeNativeMusic" -> () -> b.resumeNativeMusic(3);
                case "playLandingCue" -> () -> b.playLandingCue(0x49);
                case "close" -> b::close;
                default -> throw new AssertionError(entry);
            };
        }
        public static Object reflected(String entry, AudioMutatorAccess.Binding b) throws Throwable {
            try {
                if (entry.equals("warningActive") || entry.equals("close"))
                    return AudioMutatorAccess.Binding.class.getMethod(entry).invoke(b);
                return AudioMutatorAccess.Binding.class.getMethod(entry, int.class)
                        .invoke(b, entry.contains("Landing") ? 0x49 : 3);
            } catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
        public static AudioMutatorAccess.Binding directBind(AudioManager audio, AudioMutatorAccess.Listener listener) {
            return AudioMutatorAccess.bind(audio, listener);
        }
        public static Supplier<AudioMutatorAccess.Binding> hiddenBind(AudioManager audio, AudioMutatorAccess.Listener listener) {
            return () -> AudioMutatorAccess.bind(audio, listener);
        }
        public static Object reflectedBind(AudioManager audio, AudioMutatorAccess.Listener listener) throws Throwable {
            try { return AudioMutatorAccess.class.getMethod("bind", AudioManager.class, AudioMutatorAccess.Listener.class)
                    .invoke(null, audio, listener); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
        public static boolean active(AudioMutatorAccess.Binding b) { return b.active(); }
        public static BooleanSupplier hiddenActive(AudioMutatorAccess.Binding b) { return b::active; }
        public static boolean reflectedActive(AudioMutatorAccess.Binding b) throws Exception {
            return (boolean) AudioMutatorAccess.Binding.class.getMethod("active").invoke(b);
        }
    }
    public static final class ForeignListener implements AudioMutatorAccess.Listener {
        public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { throw new AssertionError("not admitted"); }
    }

    private static ClassLoader foreignLoader() {
        var names = Set.of(Probe.class.getName(), ForeignListener.class.getName());
        return new ClassLoader(TestAudioMutatorBindingContract.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!names.contains(name)) return super.loadClass(name, resolve);
                var type = findLoadedClass(name);
                if (type == null) {
                    try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        byte[] bytes = java.util.Objects.requireNonNull(input).readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException failure) { throw new ClassNotFoundException(name, failure); }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        };
    }

    private static Object invoke(Class<?> probe, String method, Class<?>[] types, Object... args) throws Throwable {
        try { return probe.getMethod(method, types).invoke(null, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    private static Object call(Class<?> probe, Call call, Entry entry, AudioMutatorAccess.Binding b) throws Throwable {
        var types = new Class<?>[]{String.class, AudioMutatorAccess.Binding.class};
        if (call == Call.HIDDEN) {
            var action = (Runnable) invoke(probe, "hidden", types, entry.name(), b);
            assertNotNull(action);
            action.run();
            return null;
        }
        return invoke(probe, call == Call.DIRECT ? "direct" : "reflected", types, entry.name(), b);
    }
    private static AudioMutatorAccess.Binding callBind(Class<?> probe, Call call, AudioManager audio,
                                                       AudioMutatorAccess.Listener listener) throws Throwable {
        var types = new Class<?>[]{AudioManager.class, AudioMutatorAccess.Listener.class};
        if (call == Call.HIDDEN) {
            @SuppressWarnings("unchecked") var action = (Supplier<AudioMutatorAccess.Binding>)
                    invoke(probe, "hiddenBind", types, audio, listener);
            return action.get();
        }
        return (AudioMutatorAccess.Binding) invoke(probe,
                call == Call.DIRECT ? "directBind" : "reflectedBind", types, audio, listener);
    }
    @FunctionalInterface interface Action { void run() throws Throwable; }
    private static void onThread(Action action) throws Throwable {
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> { try { action.run(); } catch (Throwable value) { failure.set(value); } },
                "owned-audio-binding-contract");
        thread.start();
        thread.join(5000);
        assertFalse(thread.isAlive(), "owned bounded callback must terminate");
        if (failure.get() != null) throw failure.get();
    }
    private static void security(Action action) {
        var failure = assertThrows(SecurityException.class, action::run);
        assertEquals(SECURITY, failure.getMessage());
        assertTrue(Stream.of(failure.getStackTrace()).anyMatch(frame ->
                frame.getClassName().startsWith(AudioMutatorAccess.class.getName())),
                "rejection must originate at the production boundary, not fixture linkage");
    }
    static Stream<Arguments> foreignEntries() {
        return Stream.of(Entry.values()).flatMap(entry -> Stream.of(Call.values()).flatMap(call ->
                Stream.of(State.values()).map(state -> Arguments.of(entry, call, state))));
    }
    static Stream<Arguments> engineEntries() {
        return Stream.of(Entry.values()).flatMap(entry -> Stream.of(Call.values()).map(call -> Arguments.of(entry, call)));
    }
    static Stream<Arguments> queryStates() {
        return Stream.of(Call.values()).flatMap(call -> Stream.of(State.values()).map(state -> Arguments.of(call, state)));
    }

    @ParameterizedTest(name = "{0}/{1}/{2}") @MethodSource("foreignEntries")
    void foreignCapabilityRejectsBeforeThreadIdentityOrNativeWork(Entry entry, Call call, State state) throws Throwable {
        var audio = preparedSeam();
        var b = bind(audio, intent -> fail("foreign capability must not emit"));
        if (state.inactive()) bind(audio, intent -> { });
        var loader = foreignLoader();
        var foreign = loader.loadClass(Probe.class.getName());
        assertSame(loader, foreign.getClassLoader());
        clearInvocations(audio);
        Action attempt = () -> security(() -> call(foreign, call, entry, b));
        if (state.offThread()) onThread(attempt); else attempt.run();
        verifyNoInteractions(audio);
        assertEquals(!state.inactive(), b.active());
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("engineEntries")
    void engineProbePositiveControlReachesEveryCapability(Entry entry, Call call) throws Throwable {
        var audio = preparedSeam();
        doReturn(new CueProfile(true)).when(audio).getAudioProfile();
        var b = bind(audio, intent -> { });
        call(Probe.class, call, entry, b);
        assertEquals(entry != Entry.close, b.active());
        switch (entry) {
            case permitsMusic, isOverride, isWarning -> verify(audio).getAudioProfile();
            case warningActive -> verify(audio).mutatorWarningActive();
            case prepareMusic -> verify(audio).prepareMutatorCue(3, true);
            case prepareLandingCue -> verify(audio).prepareMutatorCue(0x49, false);
            case playTemporaryMusic, resumeNativeMusic -> verify(audio).playMusic(3);
            case playLandingCue -> verify(audio).playSfx(0x49);
            case close -> { }
        }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("queryStates")
    void activeIsUnrestrictedForeignIdentityObservation(Call call, State state) throws Throwable {
        var audio = manager();
        var b = bind(audio, intent -> { });
        if (state.inactive()) bind(audio, intent -> { });
        var foreign = foreignLoader().loadClass(Probe.class.getName());
        Action query = () -> {
            boolean result = call == Call.HIDDEN
                    ? ((BooleanSupplier) invoke(foreign, "hiddenActive", new Class<?>[]{AudioMutatorAccess.Binding.class}, b)).getAsBoolean()
                    : (boolean) invoke(foreign, call == Call.DIRECT ? "active" : "reflectedActive",
                            new Class<?>[]{AudioMutatorAccess.Binding.class}, b);
            assertEquals(!state.inactive(), result);
        };
        if (state.offThread()) onThread(query); else query.run();
    }

    @ParameterizedTest @EnumSource(Call.class)
    void bindCallerListenerAndNullPrecedence(Call call) throws Throwable {
        var audio = manager();
        AudioMutatorAccess.Listener listener = intent -> { };
        var loader = foreignLoader();
        var foreign = loader.loadClass(Probe.class.getName());
        var foreignListener = (AudioMutatorAccess.Listener) loader.loadClass(ForeignListener.class.getName()).getConstructor().newInstance();
        security(() -> callBind(foreign, call, null, null));
        security(() -> callBind(foreign, call, audio, listener));
        security(() -> callBind(Probe.class, call, null, foreignListener));
        var listenerNull = assertThrows(NullPointerException.class, () -> callBind(Probe.class, call, null, null));
        assertTrue(listenerNull.getMessage().contains("getClass"), "listener dereference precedes audio null validation");
        var audioNull = assertThrows(NullPointerException.class, () -> callBind(Probe.class, call, null, listener));
        assertNull(audioNull.getMessage(), "Objects.requireNonNull(audio) is reached with an admitted listener");
        var b = callBind(Probe.class, call, audio, listener);
        tokens.add(b);
        assertTrue(b.active(), "same bytecode must bind successfully from its engine-loaded identity");
    }

    @ParameterizedTest @EnumSource(Entry.class)
    void engineStaleAndWrongThreadAdmissionPrecedesNativeWork(Entry entry) throws Throwable {
        var audio = preparedSeam();
        var b = bind(audio, intent -> { });
        clearInvocations(audio);
        onThread(() -> assertThrows(IllegalStateException.class, () -> Probe.direct(entry.name(), b)));
        var replacement = bind(audio, intent -> { });
        onThread(() -> assertThrows(IllegalStateException.class, () -> Probe.direct(entry.name(), b)));
        if (entry == Entry.close) assertDoesNotThrow(b::close);
        else assertThrows(IllegalStateException.class, () -> Probe.direct(entry.name(), b));
        verifyNoInteractions(audio);
        assertTrue(replacement.active());
        b.close();
        b.close();
        assertTrue(replacement.active());
    }

    @Test void replacementTokensRetainTheirOwnAcquiringThreads() throws Throwable {
        var audio = manager();
        doReturn(new CueProfile(true)).when(audio).getAudioProfile();
        var acquiringThread = Thread.currentThread();
        var first = bind(audio, intent -> fail("profile queries must not submit audio"));
        var replacement = new AtomicReference<AudioMutatorAccess.Binding>();
        assertTrue(first.active());

        onThread(() -> {
            assertNotSame(acquiringThread, Thread.currentThread());
            // Do not register this token for thread-A teardown: even an inactive
            // token must be closed only by its own acquiring thread.
            try (var second = AudioMutatorAccess.bind(audio,
                    intent -> fail("profile queries must not submit audio"))) {
                replacement.set(second);
                assertFalse(first.active());
                assertTrue(second.active());
                assertTrue(second.permitsMusic(3));
                assertThrows(IllegalStateException.class, first::close);
            }
            assertFalse(replacement.get().active());
        });

        assertNotNull(replacement.get());
        assertFalse(replacement.get().active());
        assertFalse(first.active());
        assertDoesNotThrow(first::close);
        verify(audio).getAudioProfile();
    }

    @Test void publicationPrecedesDisplacementAndNestedBindReturnsAnInactiveOuterToken() {
        var audio = manager();
        var events = new ArrayList<String>();
        var first = new AudioMutatorAccess.Binding[1];
        var third = new AudioMutatorAccess.Binding[1];
        first[0] = bind(audio, new AudioMutatorAccess.Listener() {
            public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { fail("old listener"); }
            public void onDisplaced() {
                assertFalse(Thread.holdsLock(audio));
                assertFalse(first[0].active());
                first[0].close();
                audio.setSpeedMultiplier(2);
                third[0] = bind(audio, intent -> events.add("third:" + intent.value()));
                audio.setSpeedMultiplier(3);
            }
        });
        var second = bind(audio, new AudioMutatorAccess.Listener() {
            public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { events.add("second:" + intent.value()); }
            public void onDisplaced() { events.add("second-displaced"); }
        });
        assertEquals(List.of("second:2", "second-displaced", "third:3"), events);
        assertFalse(second.active());
        second.close();
        assertTrue(third[0].active());
    }

    @Test void displacementCanResetPublishedReplacementBeforeOuterBindReturns() {
        var audio = manager();
        var events = new ArrayList<AudioMutatorAccess.Kind>();
        bind(audio, new AudioMutatorAccess.Listener() {
            public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { fail("old listener"); }
            public void onDisplaced() { audio.resetState(); }
        });
        var returned = bind(audio, intent -> events.add(intent.kind()));
        assertFalse(returned.active());
        assertEquals(List.of(AudioMutatorAccess.Kind.STOP), events);
        returned.close();
        audio.setSpeedMultiplier(2);
        assertEquals(List.of(AudioMutatorAccess.Kind.STOP), events);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void displacedFailureKeepsPublishedButUnreturnedReplacement(boolean error) {
        var audio = manager();
        var events = new ArrayList<Integer>();
        Throwable failure = error ? new AssertionError("displaced") : new IllegalArgumentException("displaced");
        var old = bind(audio, new AudioMutatorAccess.Listener() {
            public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { fail("displaced listener"); }
            public void onDisplaced() { raise(failure); }
        });
        assertSame(failure, assertThrows(failure.getClass(), () -> AudioMutatorAccess.bind(audio, intent -> events.add(intent.value()))));
        assertFalse(old.active());
        old.close();
        audio.setSpeedMultiplier(7);
        assertEquals(List.of(7), events);
    }

    enum Transition { REPLACE, CLOSE, RESET }
    enum Failure { NONE, RUNTIME, ERROR;
        Throwable value() { return this == NONE ? null : this == RUNTIME ? new IllegalArgumentException("submission") : new AssertionError("submission"); }
    }
    static Stream<Arguments> submissions() {
        return Stream.of(Transition.values()).flatMap(transition -> Stream.of(Failure.values()).flatMap(failure ->
                Stream.of(false, true).map(nested -> Arguments.of(transition, failure, nested))));
    }
    private static void raise(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure != null) throw (RuntimeException) failure;
    }

    @ParameterizedTest(name = "{0}/{1}/nested={2}") @MethodSource("submissions")
    void managerExclusionSurvivesReplacementCloseResetNestingAndUnwind(Transition transition, Failure kind, boolean nested) {
        var audio = preparedSeam();
        var events = new ArrayList<String>();
        var old = new AudioMutatorAccess.Binding[1];
        var current = new AudioMutatorAccess.Binding[1];
        AudioMutatorAccess.Listener replacement = new AudioMutatorAccess.Listener() {
            public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { events.add("new:" + intent.kind() + ":" + intent.value()); }
            public void onNativeSfx(int id) { events.add("new-sfx:" + id); }
        };
        old[0] = bind(audio, intent -> {
            events.add("old:" + intent.kind() + ":" + intent.value());
            if (intent.kind() == AudioMutatorAccess.Kind.STOP) {
                assertTrue(Thread.holdsLock(audio));
                assertFalse(old[0].active());
                current[0] = bind(audio, replacement);
                audio.setSpeedMultiplier(21); // Reset notification bypasses exclusion; this feedback must not.
            }
        });
        audio.setSpeedMultiplier(1);
        Throwable failure = kind.value();
        doAnswer(invocation -> {
            assertFalse(Thread.holdsLock(audio));
            int id = invocation.getArgument(0);
            if (id == 3) {
                switch (transition) {
                    case REPLACE -> { current[0] = bind(audio, replacement); old[0].close(); }
                    case CLOSE -> { old[0].close(); audio.setSpeedMultiplier(20); current[0] = bind(audio, replacement); }
                    case RESET -> audio.resetState();
                }
                audio.setSpeedMultiplier(22);
                audio.playSfx(0xDF, 1f);
                audio.playSfx(0xE0, 1f);
                if (nested) {
                    if (failure == null) current[0].resumeNativeMusic(4);
                    else assertSame(failure, assertThrows(failure.getClass(), () -> current[0].resumeNativeMusic(4)));
                    audio.setSpeedMultiplier(23); // Inner finally must leave the outer interval open.
                }
            } else assertEquals(4, id);
            audio.setSpeedMultiplier(24);
            raise(failure);
            return null;
        }).when(audio).playMusic(anyInt());
        if (failure == null) old[0].playTemporaryMusic(3);
        else assertSame(failure, assertThrows(failure.getClass(), () -> old[0].playTemporaryMusic(3)));
        assertFalse(old[0].active());
        assertTrue(current[0].active());
        audio.setSpeedMultiplier(25);
        audio.playSfx(0xDF, 1f);
        audio.playSfx(0xE0, 1f);
        assertEquals(transition == Transition.RESET
                ? List.of("old:SPEED_MULTIPLIER:1", "old:STOP:0", "new:SPEED_MULTIPLIER:25", "new-sfx:223", "new:COMMAND:224")
                : List.of("old:SPEED_MULTIPLIER:1", "new:SPEED_MULTIPLIER:25", "new-sfx:223", "new:COMMAND:224"), events);
    }

    @Test void secondManagerRemainsIndependentDuringSubmissionAndDestroyDoesNotUnbind() {
        var a = preparedSeam();
        var b = manager();
        var events = new ArrayList<String>();
        var first = bind(a, new AudioMutatorAccess.Listener() {
            public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { events.add("a"); }
            public void onDisplaced() { fail("exact close must not notify displacement"); }
        });
        var second = bind(b, intent -> events.add("b"));
        doAnswer(invocation -> { a.setSpeedMultiplier(2); b.setSpeedMultiplier(3); return null; }).when(a).playMusic(3);
        first.playTemporaryMusic(3);
        assertEquals(List.of("b"), events);
        a.destroy();
        assertTrue(first.active());
        a.setSpeedMultiplier(4);
        assertEquals(List.of("b", "a"), events);
        first.close();
        assertFalse(first.active());
        assertTrue(second.active());
        b.setSpeedMultiplier(5);
        assertEquals(List.of("b", "a", "b"), events);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void resetStopFailureConsumesIdentityAndPreventsStreamAndSourceEffects(boolean error) {
        var audio = manager();
        var profile = new AudioTestFixtures.StubAudioProfile(null);
        audio.setAudioProfile(profile);
        var port = mock(StreamedMusicPort.class);
        int rate = audio.outputSampleRate();
        when(port.outputRate()).thenReturn(rate);
        audio.installStreamedMusicPort(port);
        var events = new ArrayList<String>();
        audio.setStreamedMusicSessionInvalidator(() -> events.add("invalidate"));
        var old = new AudioMutatorAccess.Binding[1];
        var replacement = new AudioMutatorAccess.Binding[1];
        Throwable failure = error ? new AssertionError("STOP") : new IllegalArgumentException("STOP");
        old[0] = bind(audio, intent -> {
            assertEquals(new AudioMutatorAccess.MusicIntent(AudioMutatorAccess.Kind.STOP, 0), intent);
            assertFalse(old[0].active());
            assertTrue(Thread.holdsLock(audio));
            assertSame(profile, audio.getAudioProfile());
            events.add("stop");
            replacement[0] = bind(audio, ignored -> events.add("replacement"));
            raise(failure);
        });
        assertSame(failure, assertThrows(failure.getClass(), audio::resetState));
        assertEquals(List.of("stop"), events);
        assertFalse(old[0].active());
        assertTrue(replacement[0].active());
        assertSame(profile, audio.getAudioProfile());
        verify(port, never()).close();
        audio.installStreamedMusicPort(port); // Identity no-op proves the installed port survived.
        verify(port, never()).close();
        audio.setSpeedMultiplier(2);
        assertEquals(List.of("stop", "replacement"), events);
    }

    @Test void successfulResetKeepsCallbackReplacementAndOrdersStreamThenSourceReset() {
        var audio = manager();
        var profile = new AudioTestFixtures.StubAudioProfile(null);
        audio.setAudioProfile(profile);
        var events = new ArrayList<String>();
        audio.setStreamedMusicSessionInvalidator(() -> { assertSame(profile, audio.getAudioProfile()); events.add("invalidate"); });
        var port = mock(StreamedMusicPort.class);
        int rate = audio.outputSampleRate();
        when(port.outputRate()).thenReturn(rate);
        audio.installStreamedMusicPort(port);
        events.clear();
        doAnswer(invocation -> { events.add("port-close"); return null; }).when(port).close();
        var old = new AudioMutatorAccess.Binding[1];
        var replacement = new AudioMutatorAccess.Binding[1];
        old[0] = bind(audio, intent -> {
            assertFalse(old[0].active());
            assertTrue(Thread.holdsLock(audio));
            events.add("stop");
            replacement[0] = bind(audio, ignored -> events.add("new"));
        });
        audio.resetState();
        assertEquals(List.of("stop", "invalidate", "port-close"), events);
        assertNull(audio.getAudioProfile());
        assertTrue(replacement[0].active());
        old[0].close();
        audio.setSpeedMultiplier(2);
        assertEquals(List.of("stop", "invalidate", "port-close", "new"), events);
    }

    @Test void ordinaryFeedbackIsSynchronousUnlockedAndBothSourcePublicationsHoldMonitor() {
        var audio = manager();
        var thread = Thread.currentThread();
        var profile = new AudioTestFixtures.StubAudioProfile(null);
        var rom = new Rom(); // Identity-only source; no ROM bytes or file channel are needed.
        var events = new ArrayList<AudioMutatorAccess.Kind>();
        bind(audio, intent -> {
            assertSame(thread, Thread.currentThread());
            events.add(intent.kind());
            assertEquals(intent.kind() == AudioMutatorAccess.Kind.SOURCE, Thread.holdsLock(audio));
            if (intent.kind() == AudioMutatorAccess.Kind.SOURCE) assertSame(profile, audio.getAudioProfile());
        });
        audio.setSpeedMultiplier(2);
        audio.setAudioProfile(profile);
        audio.setRom(rom);
        assertEquals(List.of(AudioMutatorAccess.Kind.SPEED_MULTIPLIER, AudioMutatorAccess.Kind.SOURCE,
                AudioMutatorAccess.Kind.SOURCE), events);
    }

    private static final class CueProfile extends AudioTestFixtures.StubAudioProfile implements NativeAudioEffectCues {
        private final boolean answer;
        CueProfile(boolean answer) { super(null); this.answer = answer; }
        public boolean permitsTemporaryMusic(int id) { assertEquals(3, id); return answer; }
        public boolean permitsLandingCue(int id) { return answer; }
        public boolean isWarningCue(int id) { assertEquals(3, id); return answer; }
        @Override public boolean isMusicOverride(int id) { assertEquals(3, id); return answer; }
    }
    @Test void profileQueriesReadCurrentVirtualProfileIncludingNull() {
        var audio = manager();
        var b = bind(audio, intent -> { });
        for (boolean value : List.of(true, false)) {
            doReturn(new CueProfile(value)).when(audio).getAudioProfile();
            assertEquals(value, b.permitsMusic(3));
            assertEquals(value, b.isOverride(3));
            assertEquals(value, b.isWarning(3));
        }
        doReturn(null).when(audio).getAudioProfile();
        assertFalse(b.permitsMusic(3)); assertFalse(b.isOverride(3)); assertFalse(b.isWarning(3));
        verify(audio, times(9)).getAudioProfile();
    }

    static Stream<Entry> preparations() {
        return Stream.of(Entry.prepareMusic, Entry.prepareLandingCue, Entry.playTemporaryMusic,
                Entry.resumeNativeMusic, Entry.playLandingCue);
    }
    @ParameterizedTest @MethodSource("preparations")
    void preparationFailurePrecedesWarningAndSubmission(Entry entry) {
        var audio = preparedSeam();
        var b = bind(audio, intent -> { });
        var failure = new IllegalArgumentException("preparation");
        doThrow(failure).when(audio).prepareMutatorCue(anyInt(), anyBoolean());
        clearInvocations(audio);
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> Probe.direct(entry.name(), b)));
        verify(audio).prepareMutatorCue(entry == Entry.prepareLandingCue || entry == Entry.playLandingCue ? 0x49 : 3,
                entry != Entry.prepareLandingCue && entry != Entry.playLandingCue);
        verify(audio, never()).mutatorWarningActive();
        verify(audio, never()).playMusic(anyInt());
        verify(audio, never()).playSfx(anyInt());
    }

    @ParameterizedTest @EnumSource(value = Entry.class, names = {"playTemporaryMusic", "playLandingCue"})
    void liveWarningRejectionFollowsPreparationOutsideExclusion(Entry entry) {
        var audio = preparedSeam();
        var events = new ArrayList<Integer>();
        var b = bind(audio, intent -> events.add(intent.value()));
        doAnswer(invocation -> { audio.setSpeedMultiplier(1); return null; }).when(audio).prepareMutatorCue(anyInt(), anyBoolean());
        doAnswer(invocation -> { audio.setSpeedMultiplier(2); return true; }).when(audio).mutatorWarningActive();
        assertEquals("Native warning audio has priority", assertThrows(IllegalStateException.class,
                () -> Probe.direct(entry.name(), b)).getMessage());
        assertEquals(List.of(1, 2), events);
        verify(audio, never()).playMusic(anyInt()); verify(audio, never()).playSfx(anyInt());
    }

    @Test void resumePreparesThenUsesVirtualMusicWithoutAdditionalWarningGate() {
        var audio = preparedSeam();
        var order = new ArrayList<String>();
        var b = bind(audio, intent -> { });
        doAnswer(invocation -> { order.add("prepare"); return null; }).when(audio).prepareMutatorCue(3, true);
        doThrow(new AssertionError("resume has no warning gate")).when(audio).mutatorWarningActive();
        doAnswer(invocation -> { order.add("play"); return null; }).when(audio).playMusic(3);
        b.resumeNativeMusic(3);
        assertEquals(List.of("prepare", "play"), order);
        verify(audio, never()).mutatorWarningActive();
    }

    static Stream<Arguments> landingResults() {
        return Stream.of(false, true).flatMap(accepted -> Stream.of(false, true).map(replay -> Arguments.of(accepted, replay)));
    }
    @ParameterizedTest(name = "accepted={0}/replay={1}") @MethodSource("landingResults")
    void landingUsesVirtualSfxThenLiveReplayQueryOnlyForFalse(boolean accepted, boolean replay) {
        var audio = preparedSeam();
        var order = new ArrayList<String>();
        var b = bind(audio, intent -> { });
        doAnswer(invocation -> { order.add("prepare"); return null; }).when(audio).prepareMutatorCue(0x49, false);
        doAnswer(invocation -> { order.add("warning"); return false; }).when(audio).mutatorWarningActive();
        doAnswer(invocation -> { order.add("sfx"); return accepted; }).when(audio).playSfx(0x49);
        doAnswer(invocation -> { order.add("replay"); return replay; }).when(audio).isRewindReplaySuppressed();
        if (accepted || replay) b.playLandingCue(0x49);
        else assertEquals("Native landing cue was not submitted", assertThrows(IllegalStateException.class,
                () -> b.playLandingCue(0x49)).getMessage());
        assertEquals(accepted ? List.of("prepare", "warning", "sfx") : List.of("prepare", "warning", "sfx", "replay"), order);
    }

    @ParameterizedTest @EnumSource(value = Entry.class, names = {"playTemporaryMusic", "resumeNativeMusic", "playLandingCue"})
    void replacementDuringPreparationDoesNotRecheckOldTokenBeforeSubmission(Entry entry) {
        var audio = preparedSeam();
        var events = new ArrayList<String>();
        var old = bind(audio, intent -> events.add("old"));
        var replacement = new AudioMutatorAccess.Binding[1];
        doAnswer(invocation -> {
            replacement[0] = bind(audio, intent -> events.add("new:" + intent.value()));
            old.close();
            audio.setSpeedMultiplier(1);
            return null;
        }).when(audio).prepareMutatorCue(anyInt(), anyBoolean());
        doAnswer(invocation -> { audio.setSpeedMultiplier(2); return null; }).when(audio).playMusic(anyInt());
        doAnswer(invocation -> { audio.setSpeedMultiplier(2); return true; }).when(audio).playSfx(anyInt());
        assertDoesNotThrow(() -> Probe.direct(entry.name(), old));
        assertFalse(old.active());
        assertTrue(replacement[0].active());
        audio.setSpeedMultiplier(3);
        assertEquals(List.of("new:1", "new:3"), events);
    }

    @ParameterizedTest @EnumSource(value = Entry.class, names = {"playTemporaryMusic", "playLandingCue"})
    void replacementDuringWarningDoesNotRecheckOldTokenBeforeSubmission(Entry entry) {
        var audio = preparedSeam();
        var events = new ArrayList<Integer>();
        var old = bind(audio, intent -> fail("old listener"));
        doAnswer(invocation -> {
            bind(audio, intent -> events.add(intent.value()));
            audio.setSpeedMultiplier(1);
            return false;
        }).when(audio).mutatorWarningActive();
        doAnswer(invocation -> { audio.setSpeedMultiplier(2); return null; }).when(audio).playMusic(anyInt());
        doAnswer(invocation -> { audio.setSpeedMultiplier(2); return true; }).when(audio).playSfx(anyInt());
        assertDoesNotThrow(() -> Probe.direct(entry.name(), old));
        assertFalse(old.active());
        audio.setSpeedMultiplier(3);
        assertEquals(List.of(1, 3), events);
    }

    @Test void nativeSfxClassifiesAtCommandBoundaryAndRereadsCurrentListenerForNextIntent() {
        var audio = manager();
        var events = new ArrayList<String>();
        bind(audio, new AudioMutatorAccess.Listener() {
            public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { fail("SFX expected"); }
            public void onNativeSfx(int id) {
                events.add("old-sfx:" + id);
                bind(audio, new AudioMutatorAccess.Listener() {
                    public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { events.add("new:" + intent.kind() + ":" + intent.value()); }
                    public void onNativeSfx(int value) { events.add("new-sfx:" + value); }
                });
            }
        });
        try (var replay = audio.beginRewindReplay(1, 0, AudioReplayReason.SEEK)) {
            audio.playSfx(0xDF, 1f);
            audio.playSfx(0xE0, 1f);
            audio.playMusicMailboxNativeRequest(0xE1);
        }
        assertEquals(List.of("old-sfx:223", "new:COMMAND:224", "new:COMMAND:225"), events);
        assertEquals(0, audio.commandTimeline().entryCount());
    }

    @Test void semanticSfxObservesOldIdBeforeCallbackAndUsesCurrentMappingAfterward() {
        var audio = manager();
        var events = new ArrayList<String>();
        var loader = new AudioTestFixtures.StubSmpsLoader();
        loader.sfxResults.put(0x41, new AudioTestFixtures.StubSmpsData("mapped-after-callback"));
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader));
        audio.setRom(new Rom());
        audio.setSoundMap(Map.of(GameSound.JUMP, 0x40));
        audio.setRequestObserver((kind, id) -> events.add("request:" + id));
        bind(audio, new AudioMutatorAccess.Listener() {
            public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { fail("SFX expected"); }
            public void onNativeSfx(int id) {
                events.add("intent:" + id);
                audio.setSoundMap(Map.of(GameSound.JUMP, 0x41));
            }
        });
        audio.playSfx(GameSound.JUMP);
        assertEquals(List.of("intent:64", "request:65"), events);
        var command = assertInstanceOf(com.openggf.audio.rewind.AudioCommand.PlaySfx.class,
                audio.commandTimeline().entries().getLast().command());
        assertEquals(0x41, command.sfxId());
    }
}
