package com.openggf.mods.code;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.internal.NativeRewindPublicationOwner;
import com.openggf.mods.runtime.OwnerBoundGamePatch;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.io.ModAssetRoot;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Proxy;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.nio.file.Files;
import com.openggf.game.rewind.*;
import com.openggf.game.session.*;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.kis2.Kis2GamePatch;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Genuine lock-on publication ownership across the two supported session-root shapes. */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestKis2NativeRewindPublicationOwnership {
    @TempDir Path temp;

    @BeforeEach
    void configure() { TestEnvironment.resetEngineOnly(); }
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    private GameModule lockOn(GameModule base, boolean chip) throws Exception {
        var config = SonicConfigurationService.createStandalone(temp);
        var skBytes = java.nio.file.Files.readAllBytes(
                com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable().toPath());
        var resolver = new LogicalRomResolver(() -> skBytes);
        var catalogue = new com.openggf.data.RomImageCatalogue(java.util.List.of(
                com.openggf.data.PhysicalImage.probe(Path.of(System.getProperty("kis2.rom.path")))), java.util.Map.of(), false);
        var context = new PatchContext(identity -> {
            if (identity == LogicalRom.KIS2) {
                if (!chip) throw new IOException("forced tier-one control");
                return catalogue.open(com.openggf.data.RomIdentity.KIS2);
            }
            return resolver.openOrThrow(identity);
        }, config);
        return new Kis2GamePatch().apply(base, context);
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void genuineRecreationRefreshesTheSelectedRawPublication(boolean chip, boolean lockOnRoot) throws Exception {
        var base = new Sonic2GameModule();
        var module = lockOn(base, chip);
        var rom = TestEnvironment.currentRom();
        var world = new WorldSession(lockOnRoot ? module : base, module, StockGameDataSources.pinned(rom, base), null);
        var context = new GameplayModeContext(world);
        context.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2), new DefaultSolidExecutionRegistry());
        var cold = module.rewindAdapters();
        var coldState = cold.stream().map(RewindSnapshottable::capture).toList();
        assertSame(module, NativeRewindAdapterPublication.publicationOwner(world.rootGameModule(), module));
        NativeRewindAdapterPublication.reserve(module);
        assertEquals(cold, module.rewindAdapters());
        assertEquals(coldState, cold.stream().map(RewindSnapshottable::capture).toList());
        cold.forEach(adapter -> assertTrue(RewindAdapterOwnership.isNativePublication(adapter)));
        assertNull(module.getGameService(PlcLifecycleService.class), "cold reservation creates no service/work");
        context.registerGameModuleRewindAdapters();

        module.createGame(rom);
        var old = module.getGameService(Sonic2PlcService.class);
        assertSame(old, module.getGameService(PlcLifecycleService.class));
        assertTrue(module.rewindAdapters().stream().anyMatch(adapter -> adapter == old));
        if (chip) assertNotSame(old, base.getGameService(Sonic2PlcService.class));
        else assertSame(old, base.getGameService(Sonic2PlcService.class));
        context.registerGameModuleRewindAdapters();
        var registry = context.getRewindRegistry();
        old.append(2);
        old.append(1);
        var queued = old.capture().queuedEntries();
        assertEquals(chip ? 0x27B592 : 0x7B592, queued.getFirst().sourceAddress());
        assertEquals(chip ? 0x27393C : 0x7393C, queued.get(1).sourceAddress());
        assertEquals(chip ? 0x279A86 : 0x79A86, queued.get(3).sourceAddress());
        old.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(68, old.capture().activeEntry().remainingPatterns());
        var full = registry.capture();
        var course = registry.captureCourse();
        var queue = old.capture();
        assertEquals(queue, full.get(old.key()));
        old.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(65, old.capture().activeEntry().remainingPatterns());
        old.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertEquals(59, old.capture().activeEntry().remainingPatterns());
        registry.restoreCourse(course);
        assertEquals(queue, old.capture(), "same-layout course restore remains valid");
        int headServiceBoundaries = (queue.activeEntry().remainingPatterns() + 2) / 3;
        for (int i = 0; i < headServiceBoundaries; i++) {
            old.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
            old.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        }
        var advanced = old.capture();
        assertEquals(queued.get(1).sourceAddress(), advanced.activeEntry().sourceAddress(), "FIFO advances to Coconuts");
        registry.restore(full);
        for (int i = 0; i < headServiceBoundaries; i++) {
            old.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
            old.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        }
        assertEquals(advanced, old.capture(), "registry-captured ROM queue replays exact progress and FIFO");

        long epoch = registry.courseLayoutVersion();
        module.createGame(rom);
        var current = module.getGameService(Sonic2PlcService.class);
        assertNotSame(old, current);
        assertThrows(IllegalStateException.class, () -> registry.registerOrRefresh(current));
        context.registerGameModuleRewindAdapters();
        assertTrue(registry.courseLayoutVersion() > epoch);
        long refreshed = registry.courseLayoutVersion();
        context.registerGameModuleRewindAdapters();
        assertEquals(refreshed, registry.courseLayoutVersion());
        registry.restore(full);
        assertEquals(queue, current.capture(), "explicit old full snapshot targets current incarnation");
        assertEquals(advanced, old.capture(), "discarded incarnation remains untouched");
        current.serviceNormalVBlank();
        var currentFull = registry.capture();
        var currentQueue = current.capture();
        current.serviceNormalVBlank();
        registry.restore(currentFull);
        assertEquals(currentQueue, current.capture());
        assertEquals(advanced, old.capture());
        assertThrows(IllegalArgumentException.class, () -> NativeRewindAdapterPublication.register(registry, module, old));
        if (chip) assertThrows(IllegalArgumentException.class, () -> NativeRewindAdapterPublication.register(
                registry, module, base.getGameService(Sonic2PlcService.class)));
        TestNativeRewindAdapterPublication.assertStaleCourseCheckpointRejected(context, course, epoch);
        assertEquals(currentQueue, current.capture(), "stale retained checkpoint rejects before queue mutation");
        assertEquals(advanced, old.capture());

        module.resetModuleScopedState();
        assertSame(module, NativeRewindAdapterPublication.publicationOwner(world.rootGameModule(), module));
        NativeRewindAdapterPublication.reserve(module);
        assertNull(module.getGameService(PlcLifecycleService.class));
        module.createGame(rom);
        var recreated = module.getGameService(Sonic2PlcService.class);
        assertNotSame(current, recreated);
        context.registerGameModuleRewindAdapters();
        assertTrue(registry.courseLayoutVersion() > refreshed);
        registry.restore(currentFull);
        assertEquals(currentQueue, recreated.capture());
        assertEquals(currentQueue, current.capture());
        assertSame(module, NativeRewindAdapterPublication.publicationOwner(world.rootGameModule(), module));
        context.destroy();
    }
    @Test void genuineOwnedProjectionRefreshesChipWithoutClaimingCreatorState() throws Exception {
        var base = new Sonic2GameModule();
        var module = lockOn(base, true);
        var mode = new TestNativeRewindAdapterPublication.Mode();
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        var resolved = OwnerBoundGamePatch.wrap("mode", TestNativeRewindAdapterPublication.patch(mode), boundary).apply(module, null);
        var rom = TestEnvironment.currentRom();
        var context = new GameplayModeContext(new WorldSession(base, resolved, StockGameDataSources.pinned(rom, base), null));
        context.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2), new DefaultSolidExecutionRegistry());
        resolved.createGame(rom);
        var old = module.getGameService(Sonic2PlcService.class);
        assertSame(old, resolved.getGameService(Sonic2PlcService.class));
        assertSame(old, resolved.getGameService(PlcLifecycleService.class));
        context.registerGameModuleRewindAdapters();
        old.append(2); old.prepare();
        var registry = context.getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse();
        var ownedController = (RewindSnapshottable<?>) resolved.gameplayFrameController();
        assertFalse(course.containsKey(ownedController.key()));
        assertFalse(RewindAdapterOwnership.isNativePublication(ownedController));
        resolved.createGame(rom);
        var current = module.getGameService(Sonic2PlcService.class);
        assertNotSame(old, current);
        context.registerGameModuleRewindAdapters();
        mode.value = 99;
        registry.restoreCourse(course);
        assertEquals(99, mode.value);
        assertEquals(full.get(current.key()), current.capture());
        registry.restore(full);
        assertEquals(7, mode.value);
        context.destroy();
    }

    @ParameterizedTest
    @CsvSource({"false,cold", "true,cold", "true,chip", "true,base", "false,base"})
    void creatorFactoriesAndReturnedCallbacksCannotCaptureEitherNativeInventory(boolean chip, String target) throws Exception {
        for (boolean expert : List.of(false, true)) {
            var base = new Sonic2GameModule();
            var module = lockOn(base, chip);
            if (!target.equals("cold")) module.createGame(TestEnvironment.currentRom());
            var state = target.equals("cold") ? module.rewindAdapters().getFirst()
                    : (target.equals("base") ? base : module).getGameService(Sonic2PlcService.class);
            var before = state.capture();
            assertFalse(RewindAdapterOwnership.isOwned(state), "fixture is not already reserved/registered");
            var disabled = new LinkedHashSet<String>();
            var boundary = new ModFaultBoundary(Map.of("dependent", Set.of("thief")), new ModRuntimeFindingStore(),
                    ignored -> new ModStateSaveResult.Saved(), disabled::addAll);
            if (expert) {
                var patch = TestNativeRewindAdapterPublication.patch(inherited -> {
                    assertTrue(RewindAdapterOwnership.isNativePublication(state), "reserved before expert apply");
                    return new DelegatingGameModule(inherited, "thief:patch") {
                        @Override public <T> T getGameService(Class<T> type) {
                            return type == TestNativeRewindAdapterPublication.CapturedNative.class
                                    ? type.cast((TestNativeRewindAdapterPublication.CapturedNative) () -> state)
                                    : super.getGameService(type);
                        }
                    };
                });
                var projected = OwnerBoundGamePatch.wrap("thief", patch, boundary).apply(module, null);
                assertSame(module, NativeRewindAdapterPublication.publicationOwner(base, projected));
                var callback = projected.getGameService(TestNativeRewindAdapterPublication.CapturedNative.class);
                var failure = assertThrows(ModFaultBoundary.CallbackAborted.class, callback::state);
                assertEquals("thief", failure.owner());
            } else {
                var context = new ModContext("thief", "s2", ModAssetRoot.forTests("thief"));
                context.registerServiceBundle("stolen", () -> {
                    assertTrue(RewindAdapterOwnership.isNativePublication(state), "reserved before bundle factory");
                    return GameServiceBundle.builder().capturedService("native", RewindSnapshottable.class, state).build();
                });
                var failure = assertThrows(ModFaultBoundary.CallbackAborted.class,
                        () -> OwnedModServices.decorate(module, context.freeze(), boundary));
                assertEquals("thief", failure.owner());
            }
            assertEquals(Set.of("thief", "dependent"), disabled);
            assertEquals(before, state.capture());
            assertFalse(RewindAdapterOwnership.isBound(state));
            assertThrows(IllegalStateException.class, () -> RewindAdapterOwnership.bind(state, "thief", "native"));
            assertThrows(IllegalStateException.class, () -> RewindAdapterOwnership.bindDelegate(new Object(), state, "thief", "native"));
            if (target.equals("cold")) assertNull(module.getGameService(PlcLifecycleService.class));
            else {
                assertTrue(RewindAdapterOwnership.isNativePublication(module.getGameService(Sonic2PlcService.class)));
                assertTrue(RewindAdapterOwnership.isNativePublication(base.getGameService(Sonic2PlcService.class)));
            }
        }
    }

    public static final class ForeignPassThrough extends DelegatingGameModule {
        public ForeignPassThrough(GameModule base) { super(base, "foreign:benign"); }
    }
    public static final class ForgedOwner extends DelegatingGameModule implements NativeRewindPublicationOwner {
        private final RewindSnapshottable<?> adapter;
        private final AtomicInteger reads;
        public ForgedOwner(GameModule base, RewindSnapshottable<?> adapter, AtomicInteger reads) {
            super(base, "foreign:forged"); this.adapter = adapter; this.reads = reads;
        }
        @Override public List<RewindSnapshottable<?>> rewindAdapters() { reads.incrementAndGet(); return List.of(adapter); }
    }
    public static final class ForgedRoot extends Sonic2GameModule implements NativeRewindPublicationOwner {
        private final AtomicInteger reads;
        public ForgedRoot(AtomicInteger reads) { this.reads = reads; }
        @Override public List<RewindSnapshottable<?>> rewindAdapters() { reads.incrementAndGet(); return List.of(); }
    }
    public static final class LineageHandler implements java.lang.reflect.InvocationHandler,
            com.openggf.game.internal.InheritedGameModuleProvider {
        GameModule inherited;
        final AtomicInteger reads;
        final AtomicInteger invocations;
        public LineageHandler(GameModule inherited, AtomicInteger reads, AtomicInteger invocations) {
            this.inherited = inherited; this.reads = reads; this.invocations = invocations;
        }
        @Override public GameModule inheritedModule() { reads.incrementAndGet(); return inherited; }
        @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            invocations.incrementAndGet(); throw new AssertionError("No proxy dispatch during native admission");
        }
    }

    private ModDependencyClassLoader foreignClasses() throws Exception {
        var names = Set.of(ForeignPassThrough.class.getName(), ForgedOwner.class.getName(),
                ForgedRoot.class.getName(), LineageHandler.class.getName());
        Path jar = temp.resolve("kis2-foreign-ownership.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String name : names) {
                String resource = name.replace('.', '/') + ".class";
                try (var bytes = getClass().getClassLoader().getResourceAsStream(resource)) {
                    output.putNextEntry(new JarEntry(resource));
                    output.write(Objects.requireNonNull(bytes).readAllBytes()); output.closeEntry();
                }
            }
        }
        var parent = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (names.contains(name)) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        return new ModDependencyClassLoader("hostile", new java.net.URL[]{jar.toUri().toURL()}, parent, List.of());
    }

    @Test void actualRootIdentityAnchorsTheResolvedPublisherIncludingOwnedProjections() throws Exception {
        var base = new Sonic2GameModule();
        var module = lockOn(base, true);
        var sibling = lockOn(base, true);
        var inheritedAdapter = module.rewindAdapters().getFirst();
        var registry = new RewindRegistry();
        NativeRewindAdapterPublication.register(registry, module, inheritedAdapter);
        assertThrows(IllegalStateException.class, () -> NativeRewindAdapterPublication.register(registry, sibling, inheritedAdapter));
        assertSame(module, NativeRewindAdapterPublication.publicationOwner(base, module));
        assertSame(module, NativeRewindAdapterPublication.publicationOwner(module, module));
        assertNull(NativeRewindAdapterPublication.publicationOwner(sibling, module), "equal canonical roots do not anchor siblings");
        assertNull(NativeRewindAdapterPublication.publicationOwner(new Sonic2GameModule(), module));
        var state = new TestNativeRewindAdapterPublication.State("foreign", 7);
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        var projected = OwnerBoundGamePatch.wrap("benign", TestNativeRewindAdapterPublication.patch(
                inherited -> new DelegatingGameModule(inherited, "benign:outer") { }), boundary).apply(module, null);
        assertTrue(Proxy.isProxyClass(projected.getClass()), "exercise the real engine-owned handler");
        assertSame(module, NativeRewindAdapterPublication.publicationOwner(base, projected));
        assertSame(module, NativeRewindAdapterPublication.publicationOwner(projected, projected));
        assertSame(base, NativeRewindAdapterPublication.nativeRoot(projected), "canonical-root API is unchanged");
        var overProjection = lockOn(projected, true);
        assertNull(NativeRewindAdapterPublication.publicationOwner(base, overProjection),
                "inner creator projection provenance cannot endorse its exposed inventory");
        var creator = new ModContext("benign", "s2", ModAssetRoot.forTests("benign"));
        creator.registerServiceBundle("state", () -> GameServiceBundle.builder().capturedService(
                "state", RewindSnapshottable.class, state).build());
        var bundled = OwnedModServices.decorate(module, creator.freeze(), boundary);
        assertSame(module, NativeRewindAdapterPublication.publicationOwner(base, bundled));
        assertFalse(RewindAdapterOwnership.isNativePublication(state), "outer creator publications confer no native authority");
        try (var loader = foreignClasses()) {
            var outer = (GameModule) loader.loadClass(ForeignPassThrough.class.getName()).getConstructor(GameModule.class).newInstance(module);
            assertSame(loader, outer.getClass().getClassLoader());
            assertSame(module, NativeRewindAdapterPublication.publicationOwner(base, outer));
            var plain = (GameModule) loader.loadClass(ForeignPassThrough.class.getName()).getConstructor(GameModule.class).newInstance(base);
            assertSame(base, NativeRewindAdapterPublication.publicationOwner(base, plain), "unmarked pass-through stays canonical-owned");
            var reads = new AtomicInteger();
            var forged = (GameModule) loader.loadClass(ForgedOwner.class.getName())
                    .getConstructor(GameModule.class, RewindSnapshottable.class, AtomicInteger.class).newInstance(module, state, reads);
            assertSame(module, NativeRewindAdapterPublication.publicationOwner(base, forged));
            NativeRewindAdapterPublication.reserve(forged);
            assertEquals(0, reads.get(), "foreign marker inventory is never read");
            assertFalse(RewindAdapterOwnership.isNativePublication(state));
            assertThrows(SecurityException.class, () -> NativeRewindAdapterPublication.register(new RewindRegistry(), forged, state));

            var launderer = lockOn(forged, true);
            assertNull(NativeRewindAdapterPublication.publicationOwner(base, launderer), "marked host cannot launder a foreign inner inventory");
            NativeRewindAdapterPublication.reserve(launderer);
            assertEquals(0, reads.get());
            assertThrows(SecurityException.class, () -> NativeRewindAdapterPublication.register(new RewindRegistry(), launderer, state));
            var forgedRoot = (GameModule) loader.loadClass(ForgedRoot.class.getName()).getConstructor(AtomicInteger.class).newInstance(reads);
            assertNull(NativeRewindAdapterPublication.publicationOwner(forgedRoot, forgedRoot));
            assertNull(NativeRewindAdapterPublication.publicationOwner(forgedRoot, lockOn(forgedRoot, false)));
            NativeRewindAdapterPublication.reserve(forgedRoot);
            assertEquals(0, reads.get());
        }
    }

    @Test void proxyMarkersNeverInvokeForeignHandlersAndValidationContinuesAfterFindingAnOwner() throws Exception {
        var base = new Sonic2GameModule();
        var reads = new AtomicInteger(); var invocations = new AtomicInteger();
        try (var loader = foreignClasses()) {
            var handler = (java.lang.reflect.InvocationHandler) loader.loadClass(LineageHandler.class.getName())
                    .getConstructor(GameModule.class, AtomicInteger.class, AtomicInteger.class).newInstance(base, reads, invocations);
            assertSame(loader, handler.getClass().getClassLoader());
            var proxy = (GameModule) Proxy.newProxyInstance(GameModule.class.getClassLoader(),
                    new Class<?>[]{GameModule.class, NativeRewindPublicationOwner.class}, handler);
            assertSame(GameModule.class.getClassLoader(), proxy.getClass().getClassLoader());
            assertNull(NativeRewindAdapterPublication.nativeRoot(proxy));
            assertNull(NativeRewindAdapterPublication.publicationOwner(proxy, proxy));
            NativeRewindAdapterPublication.reserve(proxy);
            var module = lockOn(proxy, true);
            assertNull(NativeRewindAdapterPublication.publicationOwner(proxy, module));
            NativeRewindAdapterPublication.reserve(module);
            assertEquals(0, reads.get()); assertEquals(0, invocations.get());
        }
        var handler = new LineageHandler(null, reads, invocations);
        var proxy = (GameModule) Proxy.newProxyInstance(GameModule.class.getClassLoader(), new Class<?>[]{GameModule.class}, handler);
        var cycle = lockOn(proxy, false);
        handler.inherited = cycle;
        assertThrows(IllegalArgumentException.class, () -> NativeRewindAdapterPublication.publicationOwner(cycle, cycle));
        assertThrows(IllegalArgumentException.class, () -> NativeRewindAdapterPublication.reserve(cycle));
        assertEquals(0, invocations.get());
        GameModule longChain = base;
        for (int i = 0; i < com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES; i++)
            longChain = new DelegatingGameModule(longChain, "bounded") { };
        var tooLong = lockOn(longChain, false);
        assertThrows(IllegalArgumentException.class, () -> NativeRewindAdapterPublication.publicationOwner(tooLong, tooLong));
        assertThrows(IllegalArgumentException.class, () -> NativeRewindAdapterPublication.reserve(tooLong));
    }

    @Test void inventoriesValidateBeforeReservationAndIdenticalDuplicatesKeepDeduplication() throws Exception {
        var first = new TestNativeRewindAdapterPublication.State("same-key", 1);
        var second = new TestNativeRewindAdapterPublication.State("same-key", 2);
        var base = new TestNativeRewindAdapterPublication.HostRoot(first, second);
        var module = lockOn(base, false);
        assertThrows(IllegalStateException.class, () -> NativeRewindAdapterPublication.reserve(module));
        assertFalse(RewindAdapterOwnership.isNativePublication(first));
        assertFalse(RewindAdapterOwnership.isNativePublication(second));
        base.adapters = Arrays.asList(first, null);
        assertThrows(NullPointerException.class, () -> NativeRewindAdapterPublication.reserve(module));
        assertFalse(RewindAdapterOwnership.isNativePublication(first));
        base.adapters = List.of(first, new TestNativeRewindAdapterPublication.State(null, 0));
        assertThrows(NullPointerException.class, () -> NativeRewindAdapterPublication.reserve(module));
        assertFalse(RewindAdapterOwnership.isNativePublication(first));
        base.adapters = Collections.nCopies(com.openggf.io.ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES + 1, first);
        assertThrows(IllegalArgumentException.class, () -> NativeRewindAdapterPublication.reserve(module));
        assertFalse(RewindAdapterOwnership.isNativePublication(first));
        var switchingBase = new Sonic2GameModule() {
            int reads;
            @Override public List<RewindSnapshottable<?>> rewindAdapters() {
                return reads++ == 0 ? List.of(first) : List.of(first, second);
            }
        };
        var invalidSelected = lockOn(switchingBase, false);
        assertThrows(IllegalStateException.class, () -> NativeRewindAdapterPublication.reserve(invalidSelected));
        assertFalse(RewindAdapterOwnership.isNativePublication(first), "valid canonical inventory cannot be partially marked before selected validation");
        base.adapters = List.of(first, first);
        NativeRewindAdapterPublication.reserve(module);
        var registry = new RewindRegistry();
        NativeRewindAdapterPublication.register(registry, module, first);
        NativeRewindAdapterPublication.register(registry, module, first);
        assertEquals(Set.of(first.key()), registry.capture().entries().keySet());
    }

}
