package com.openggf.mods.code;

import com.openggf.LevelFrameContext;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.GameplayObservation;
import com.openggf.game.GameplayObserver;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.mutators.MutatorCapability;
import com.openggf.mods.mutators.MutatorDefinition;
import com.openggf.mods.mutators.MutatorPolicy;
import com.openggf.mods.mutators.MutatorScope;
import com.openggf.mods.mutators.MutatorSupportProfile;
import com.openggf.mods.mutators.MutatorWorldAccess;
import com.openggf.mods.mutators.TemporaryMutators;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class TestSessionRetirementOwnership {
    private static final GameDataSource SOURCE = new GameDataSource() {
        @Override public Optional<com.openggf.data.Rom> rom() { return Optional.empty(); }
        @Override public InputStream openAsset(String path) { return InputStream.nullInputStream(); }
        @Override public String identity() { return "test:session-retirement"; }
    };

    @TempDir Path saveRoot;
    private String previousSaveRoot;
    private ModFaultBoundary faults;

    @BeforeEach
    void setup() {
        TestEnvironment.resetEngineOnly();
        EngineServices.current().graphics().initHeadless();
        previousSaveRoot = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", saveRoot.toString());
        faults = boundary();
    }

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previousSaveRoot == null) System.clearProperty("openggf.saveRoot");
        else System.setProperty("openggf.saveRoot", previousSaveRoot);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedObserverRetirementDetachesTheWorldBeforeClearOrReplacement(boolean replace) {
        var observer = new RecordingObserver("observer", true, new ArrayList<>());
        open(observerModule(new Sonic2GameModule(), "failing", observer));
        SessionManager.armNextGameplayAdmissionPolicy(HardwareReadinessAdmissionPolicy.RECORDED);

        assertThrows(RuntimeException.class, () -> {
            if (replace) openSession(new Sonic2GameModule());
            else SessionManager.clear();
        });

        assertEquals(1, observer.closes);
        assertNull(observer.publishedWorldDuringClose);
        assertFalse(observer.moduleAvailableDuringClose);
        assertNull(SessionManager.getCurrentWorldSession());
        assertNull(SessionManager.getCurrentGameplayMode());
        assertFalse(faults.isOwnerAvailable("failing"));

        var fresh = openSession(new Sonic2GameModule());
        assertEquals(HardwareReadinessAdmissionPolicy.LIVE,
                fresh.hardwareTiming().admissionPolicy());
        assertDoesNotThrow(SessionManager::clear);
        assertDoesNotThrow(SessionManager::clear);
        assertEquals(1, observer.closes);
    }

    @Test
    void modeTeardownFailureRemainsPrimaryAndSuppressesObserverRetirementFailure() {
        var observer = new RecordingObserver("observer", true, new ArrayList<>());
        var mode = open(observerModule(new Sonic2GameModule(), "failing", observer));
        var primary = new IllegalStateException("mode teardown failed");
        mode.setHardwareTimingReplayCloseHook(() -> { throw primary; });

        RuntimeException actual = assertThrows(RuntimeException.class, SessionManager::clear);

        assertSame(primary, actual);
        assertEquals(1, actual.getSuppressed().length);
        assertEquals(1, observer.closes);
        assertNull(SessionManager.getCurrentWorldSession());
        assertDoesNotThrow(SessionManager::clear);
        assertEquals(1, observer.closes);
    }

    @Test
    void observerOnlyWorldDispatchesEveryCallbackWithoutCreatingMutatorPreferences() {
        List<String> events = new ArrayList<>();
        var observer = new RecordingObserver("observer", false, events);
        var mode = open(observerModule(new Sonic2GameModule(), "observer-only", observer));
        WorldSession world = mode.getWorldSession();

        assertTrue(WorldSessionPolicyAccess.hasPolicies(world));
        assertNotNull(MutatorWorldAccess.state(world));
        assertTrue(MutatorWorldAccess.state(world).definitions().isEmpty());
        assertFalse(Files.exists(saveRoot.resolve("mutators")));

        WorldSessionPolicyAccess.onPresentation(mode, LogicalInputSnapshot.neutral(),
                GameplayObservation.Phase.ACTIVE);
        WorldSessionPolicyAccess.setHistoryRecording(world, true);
        LevelFrameContext frame = LevelFrameContext.from(mode);
        WorldSessionPolicyAccess.beforeNativeTick(frame);
        WorldSessionPolicyAccess.afterNativeTick(frame);
        Object history = WorldSessionPolicyAccess.captureHistory(world);
        assertNotNull(history);
        observer.value = 99;
        WorldSessionPolicyAccess.restoreHistoryBefore(world, history);
        assertEquals(2, observer.value);
        WorldSessionPolicyAccess.restoreHistoryAfter(world, history);
        assertEquals(2, observer.value);
        WorldSessionPolicyAccess.leaveOrdinaryGameplay(world, GameplayObservation.Phase.TRANSITION);

        assertEquals(List.of("observer:presentation", "observer:tick", "observer:boundary"), events);
        assertTrue(observer.availability.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> MutatorWorldAccess.save(world));
        SessionManager.clear();
        assertEquals(List.of("observer:presentation", "observer:tick", "observer:boundary", "observer:close"), events);
        assertFalse(Files.exists(saveRoot.resolve("mutators")));
    }

    @Test
    void unsupportedMutatorsNeedAnObserverToOwnCallbacksButStillHaveNoPreferences() {
        var unsupported = mutatorModule(new Sonic2GameModule(), "unsupported", false);
        var unsupportedWorld = new WorldSession(new Sonic2GameModule(), unsupported, SOURCE, null);
        assertFalse(WorldSessionPolicyAccess.hasPolicies(unsupportedWorld));
        assertFalse(Files.exists(saveRoot.resolve("mutators")));

        List<String> events = new ArrayList<>();
        var observer = new RecordingObserver("observer", false, events);
        var observed = observerModule(unsupported, "unsupported", observer);
        var mode = open(observed);
        WorldSession world = mode.getWorldSession();
        WorldSessionPolicyAccess.onPresentation(mode, LogicalInputSnapshot.neutral(),
                GameplayObservation.Phase.ACTIVE);

        assertEquals(List.of("observer:presentation"), events);
        assertEquals(1, observer.availability.size());
        assertFalse(observer.availability.getFirst().available());
        assertFalse(Files.exists(saveRoot.resolve("mutators")));
        assertThrows(UnsupportedOperationException.class, () -> MutatorWorldAccess.save(world));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void layeredObserversAndMutatorsShareOneWorldOwnerAndOneNativeChild(boolean mutatorFirst) {
        List<String> events = new ArrayList<>();
        var first = new RecordingObserver("first", false, events);
        var second = new RecordingObserver("second", false, events);
        var nativePolicy = new CountingNativePolicy();
        AtomicInteger nativeOpens = new AtomicInteger();
        GameModule module = new DelegatingGameModule(new Sonic2GameModule(), "test:native-policy") {
            @Override public <T> T getGameService(Class<T> type) {
                if (type == WorldSessionPolicyProvider.class) {
                    return type.cast((WorldSessionPolicyProvider) world -> {
                        nativeOpens.incrementAndGet();
                        return nativePolicy;
                    });
                }
                return super.getGameService(type);
            }
        };
        if (mutatorFirst) module = mutatorModule(module, "mutators", true);
        module = observerModule(module, "first", first);
        if (!mutatorFirst) module = mutatorModule(module, "mutators", true);
        module = observerModule(module, "second", second);

        var provider = module.getGameService(WorldSessionPolicyProvider.class);
        assertSame(provider, module.getGameService(WorldSessionPolicyProvider.class));
        var registry = module.getGameService(GameplayObserverRegistry.class);
        assertEquals(List.of("first", "second"),
                registry.entries().stream().map(GameplayObserverRegistry.Entry::owner).toList());
        var mode = open(module);
        WorldSession world = mode.getWorldSession();
        assertEquals(1, nativeOpens.get());
        assertEquals(1, MutatorWorldAccess.state(world).definitions().size());

        WorldSessionPolicyAccess.onPresentation(mode, LogicalInputSnapshot.neutral(),
                GameplayObservation.Phase.ACTIVE);
        WorldSessionPolicyAccess.setHistoryRecording(world, true);
        LevelFrameContext frame = LevelFrameContext.from(mode);
        WorldSessionPolicyAccess.beforeNativeTick(frame);
        WorldSessionPolicyAccess.afterNativeTick(frame);
        Object history = WorldSessionPolicyAccess.captureHistory(world);
        assertNotNull(history);
        assertEquals(2, first.captures);
        assertEquals(2, second.captures);
        first.value = 99;
        second.value = 98;
        WorldSessionPolicyAccess.restoreHistoryBefore(world, history);
        assertEquals(2, first.value);
        assertEquals(2, second.value);
        WorldSessionPolicyAccess.restoreHistoryAfter(world, history);
        assertEquals(2, first.value);
        assertEquals(2, second.value);
        assertEquals(2, first.restores);
        assertEquals(2, second.restores);
        WorldSessionPolicyAccess.leaveOrdinaryGameplay(world, GameplayObservation.Phase.TRANSITION);

        assertEquals(List.of(
                "first:presentation", "second:presentation",
                "first:tick", "second:tick",
                "first:boundary", "second:boundary"), events);
        SessionManager.clear();
        assertEquals(1, first.closes);
        assertEquals(1, second.closes);
        assertEquals(1, nativePolicy.retires);
        assertDoesNotThrow(SessionManager::clear);
        assertEquals(1, first.closes);
        assertEquals(1, second.closes);
        assertEquals(1, nativePolicy.retires);
    }

    @Test
    void observerCleanupAttemptsEveryOwnerOnceAfterOneOwnerFails() {
        List<String> events = new ArrayList<>();
        var failing = new RecordingObserver("failing", true, events);
        var healthy = new RecordingObserver("healthy", false, events);
        GameModule module = observerModule(new Sonic2GameModule(), "failing", failing);
        module = observerModule(module, "healthy", healthy);
        open(module);

        assertThrows(RuntimeException.class, SessionManager::clear);

        assertEquals(List.of("failing:close", "healthy:close"), events);
        assertEquals(1, failing.closes);
        assertEquals(1, healthy.closes);
        assertDoesNotThrow(SessionManager::clear);
        assertEquals(1, failing.closes);
        assertEquals(1, healthy.closes);
    }

    private com.openggf.game.session.GameplayModeContext open(GameModule module) {
        var mode = openSession(module);
        GameplaySessionFactory.attachManagers(mode, EngineServices.current());
        return mode;
    }

    private com.openggf.game.session.GameplayModeContext openSession(GameModule module) {
        return SessionManager.openGameplaySession(new Sonic2GameModule(), module, SOURCE, null);
    }

    private GameModule observerModule(GameModule base, String owner, RecordingObserver observer) {
        var context = new ModContext(owner, "s2", ModAssetRoot.forTests(owner));
        context.registerServiceBundle("observer", () -> GameServiceBundle.builder()
                .gameplayObserver("observer", observer).build());
        return new ModBackedGamePatch(context.freeze(), faults).apply(base, null);
    }

    private GameModule mutatorModule(GameModule base, String owner, boolean withSupport) {
        var context = new ModContext(owner, "s2", ModAssetRoot.forTests(owner));
        context.registerMutator(definition());
        if (withSupport) context.registerService("support", MutatorSupportProfile.class, support());
        return new ModBackedGamePatch(context.freeze(), faults).apply(base, null);
    }

    private static MutatorDefinition definition() {
        return new MutatorDefinition("gravity", "Gravity", "Test gravity", MutatorScope.LIVE,
                MutatorScope.LIVE, List.of(), Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                options -> List.of(new MutatorPolicy.DrySonicGravity(100)));
    }

    private static MutatorSupportProfile support() {
        return new MutatorSupportProfile() {
            @Override public Set<MutatorCapability> capabilities(int zone, int act) {
                return Set.of(MutatorCapability.DRY_SONIC_GRAVITY);
            }
            @Override public boolean supportsPlayer(String key, boolean leader) { return leader; }
        };
    }

    private static ModFaultBoundary boundary() {
        return new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    private static final class RecordingObserver implements GameplayObserver, RewindSnapshottable<Integer> {
        private final String name;
        private final boolean failClose;
        private final List<String> events;
        private int value;
        private int captures;
        private int restores;
        private int closes;
        private List<TemporaryMutators.Availability> availability = List.of();
        private WorldSession publishedWorldDuringClose;
        private boolean moduleAvailableDuringClose;

        private RecordingObserver(String name, boolean failClose, List<String> events) {
            this.name = name;
            this.failClose = failClose;
            this.events = events;
        }

        @Override public String key() { return name; }
        @Override public Integer capture() { captures++; return value; }
        @Override public void restore(Integer restored) { restores++; value = restored; }
        @Override public void onPresentation(GameplayObservation observation, TemporaryMutators mutators) {
            events.add(name + ":presentation");
            availability = mutators.available();
            value++;
        }
        @Override public void onTick(GameplayObservation observation, TemporaryMutators mutators) {
            events.add(name + ":tick");
            value++;
        }
        @Override public void onBoundary(GameplayObservation observation, TemporaryMutators mutators) {
            events.add(name + ":boundary");
        }
        @Override public void close() {
            closes++;
            events.add(name + ":close");
            publishedWorldDuringClose = SessionManager.getCurrentWorldSession();
            try {
                SessionManager.requireCurrentGameModule();
                moduleAvailableDuringClose = true;
            } catch (IllegalStateException expected) {
                moduleAvailableDuringClose = false;
            }
            if (failClose) throw new IllegalStateException("observer close failed");
        }
    }

    private static final class CountingNativePolicy implements WorldSessionPolicyState, RewindSnapshottable<Integer> {
        private int value;
        private int retires;
        @Override public <T> T getService(Class<T> type) { return null; }
        @Override public RewindSnapshottable<?> rewindAdapter() { return this; }
        @Override public String key() { return "test-native-policy"; }
        @Override public Integer capture() { return value; }
        @Override public void restore(Integer restored) { value = restored; }
        @Override public void beforeAssembly(LevelLoadCause cause) { }
        @Override public void bindRoster(SpriteManager sprites) { }
        @Override public void failedAssembly(LevelLoadCause cause) { }
        @Override public void closeScreens() { }
        @Override public void retire() { retires++; }
    }
}
