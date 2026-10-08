package com.openggf.mods.code;

import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.patch.*;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.*;
import com.openggf.game.session.*;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.graphics.FadeManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.runtime.OwnerBoundGamePatch;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Native recreation is admitted by root identity, never by a reported adapter key. */
@Isolated
public class TestNativeRewindAdapterPublication {
    @TempDir Path temp;

    @BeforeEach void configureServices() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    public static final class State implements RewindSnapshottable<Integer> {
        int value;
        final String key;
        State(String key, int value) { this.key=key; this.value=value; }
        @Override public String key() { return key; }
        @Override public Integer capture() { return value; }
        @Override public void restore(Integer value) { this.value=value; }
    }
    public static final class HostRoot extends Sonic2GameModule {
        List<RewindSnapshottable<?>> adapters;
        HostRoot(RewindSnapshottable<?>... adapters) { this.adapters=List.of(adapters); }
        @Override public List<RewindSnapshottable<?>> rewindAdapters() { return adapters; }
    }
    public static final class CreatorRoot extends DelegatingGameModule {
        final RewindSnapshottable<?> adapter;
        public CreatorRoot(GameModule base, RewindSnapshottable<?> adapter) {
            super(base,"creator:forged-root"); this.adapter=adapter;
        }
        @Override public List<RewindSnapshottable<?>> rewindAdapters() { return List.of(adapter); }
    }
    public interface Publication {
        void register(RewindRegistry registry, GameModule root, RewindSnapshottable<?> adapter);
    }
    public static final class ForeignLineageHandler implements java.lang.reflect.InvocationHandler,
            com.openggf.game.internal.InheritedGameModuleProvider {
        private final GameModule inherited;
        private final java.util.concurrent.atomic.AtomicInteger reads;
        public ForeignLineageHandler(GameModule inherited, java.util.concurrent.atomic.AtomicInteger reads) {
            this.inherited=inherited; this.reads=reads;
        }
        @Override public GameModule inheritedModule() { reads.incrementAndGet(); return inherited; }
        @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            throw new AssertionError("A foreign lineage handler must never be dispatched");
        }
    }
    public static final class CreatorProbe {
        public static void direct(RewindRegistry registry, GameModule root, RewindSnapshottable<?> adapter) {
            NativeRewindAdapterPublication.register(registry,root,adapter);
        }
        public static Publication hidden() { return NativeRewindAdapterPublication::register; }
        public static Function<GameModule,GameModule> hiddenRoot() { return NativeRewindAdapterPublication::nativeRoot; }
        public static Function<GameModule,GameModule> hiddenInherited() { return OwnerBoundGamePatch::inheritedModule; }
        public static java.util.function.Consumer<GameModule> hiddenReserve() { return NativeRewindAdapterPublication::reserve; }
    }

    @Test void genericRefreshStillRejectsDistinctUnownedOrNativeAdapters() {
        var old=new State("native-service",7); var root=new HostRoot(old); var registry=new RewindRegistry();
        NativeRewindAdapterPublication.register(registry,root,old);
        long version=registry.courseLayoutVersion();
        NativeRewindAdapterPublication.register(registry,root,old);
        assertEquals(version,registry.courseLayoutVersion());
        var replacement=new State(old.key(),99); root.adapters=List.of(replacement);
        assertThrows(IllegalStateException.class,()->registry.registerOrRefresh(replacement));
        assertEquals(7,registry.capture().get(old.key()));
        NativeRewindAdapterPublication.register(registry,root,replacement);
        assertEquals(99,registry.capture().get(old.key()));
        assertTrue(registry.courseLayoutVersion()>version);
        assertThrows(IllegalStateException.class,()->registry.registerOrRefresh(old));
        assertThrows(IllegalArgumentException.class,()->NativeRewindAdapterPublication.register(registry,root,old));
    }

    @Test void anotherActualEngineRootCannotReplaceThePublishedAuthority() {
        var first=new State("native-service",7); var second=new State(first.key(),99); var registry=new RewindRegistry();
        NativeRewindAdapterPublication.register(registry,new HostRoot(first),first);
        assertThrows(IllegalStateException.class,()->NativeRewindAdapterPublication.register(registry,new HostRoot(second),second));
        assertEquals(7,registry.capture().get(first.key()));
        assertThrows(IllegalArgumentException.class,()->NativeRewindAdapterPublication.register(registry,new HostRoot(second),first));
    }

    @Test void duplicateNativePublicationsAndModRetaggingAreRejected() {
        var first=new State("native-service",7); var second=new State(first.key(),99); var registry=new RewindRegistry();
        assertThrows(IllegalStateException.class,()->NativeRewindAdapterPublication.register(registry,new HostRoot(first,second),first));
        assertTrue(registry.capture().entries().isEmpty());
        NativeRewindAdapterPublication.register(registry,new HostRoot(first),first);
        assertThrows(IllegalStateException.class,()->RewindAdapterOwnership.bind(first,"thief","rings"));
        assertThrows(IllegalStateException.class,()->RewindAdapterOwnership.bindDelegate(new Object(),first,"thief","rings"));
        var owned=new State("reported",1); RewindAdapterOwnership.bind(owned,"owner","state");
        assertThrows(IllegalArgumentException.class,()->NativeRewindAdapterPublication.register(new RewindRegistry(),new HostRoot(owned),owned));
    }

    @Test void capturedBundleCannotReassignPublishedNativeServiceAndDisablesItsActualOwner() {
        var nativeService=new State("native-service",7); var root=new HostRoot(nativeService);
        NativeRewindAdapterPublication.register(new RewindRegistry(),root,nativeService);
        var disabled=new LinkedHashSet<String>();
        var boundary=new ModFaultBoundary(Map.of("dependent",Set.of("thief")),new ModRuntimeFindingStore(),
                ignored->new ModStateSaveResult.Saved(),disabled::addAll);
        var context=new ModContext("thief","s2",ModAssetRoot.forTests("thief"));
        context.registerServiceBundle("stolen",()->GameServiceBundle.builder()
                .capturedService("native",RewindSnapshottable.class,nativeService).build());
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->OwnedModServices.decorate(root,context.freeze(),boundary));
        assertEquals("thief",failure.owner()); assertEquals(Set.of("thief","dependent"),disabled);
        assertEquals(7,nativeService.capture());
    }

    @Test void childDirectHiddenMethodReferencesAndForgedRootCannotAcquireEngineAuthority() throws Exception {
        var names=Set.of(CreatorProbe.class.getName(),CreatorRoot.class.getName(),ForeignLineageHandler.class.getName());
        Path jar=temp.resolve("native-authority-probe.jar");
        try(var output=new JarOutputStream(Files.newOutputStream(jar))) {
            for(String name:names) {
                String resource=name.replace('.','/')+".class";
                try(var bytes=getClass().getClassLoader().getResourceAsStream(resource)) {
                    output.putNextEntry(new JarEntry(resource)); output.write(Objects.requireNonNull(bytes).readAllBytes()); output.closeEntry();
                }
            }
        }
        ClassLoader parent=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if(names.contains(name)) throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        };
        try(var loader=new ModDependencyClassLoader("hostile",new java.net.URL[]{jar.toUri().toURL()},parent,List.of())) {
            var probe=loader.loadClass(CreatorProbe.class.getName()); assertSame(loader,probe.getClassLoader());
            var adapter=new State("native-service",7); var root=new HostRoot(adapter); var registry=new RewindRegistry();
            var direct=assertThrows(InvocationTargetException.class,()->probe.getMethod("direct",RewindRegistry.class,GameModule.class,RewindSnapshottable.class)
                    .invoke(null,registry,root,adapter));
            assertInstanceOf(SecurityException.class,direct.getCause());
            var hidden=(Publication)probe.getMethod("hidden").invoke(null);
            assertThrows(SecurityException.class,()->hidden.register(registry,root,adapter));
            @SuppressWarnings("unchecked") var hiddenReserve=(java.util.function.Consumer<GameModule>)probe.getMethod("hiddenReserve").invoke(null);
            assertThrows(SecurityException.class,()->hiddenReserve.accept(root));
            @SuppressWarnings("unchecked") var hiddenRoot=(Function<GameModule,GameModule>)probe.getMethod("hiddenRoot").invoke(null);
            assertThrows(SecurityException.class,()->hiddenRoot.apply(root));
            @SuppressWarnings("unchecked") var hiddenInherited=(Function<GameModule,GameModule>)probe.getMethod("hiddenInherited").invoke(null);
            assertThrows(SecurityException.class,()->hiddenInherited.apply(root));
            GameModule forged=(GameModule)loader.loadClass(CreatorRoot.class.getName()).getConstructor(GameModule.class,RewindSnapshottable.class)
                    .newInstance(root,adapter);
            assertThrows(SecurityException.class,()->NativeRewindAdapterPublication.register(registry,forged,adapter));
            assertSame(root,NativeRewindAdapterPublication.nativeRoot(forged),"An immutable decorator identifies its real base, not its reported publications");
            var reads=new java.util.concurrent.atomic.AtomicInteger();
            var handler=(java.lang.reflect.InvocationHandler)loader.loadClass(ForeignLineageHandler.class.getName())
                    .getConstructor(GameModule.class,java.util.concurrent.atomic.AtomicInteger.class).newInstance(root,reads);
            GameModule foreignProjection=(GameModule)java.lang.reflect.Proxy.newProxyInstance(GameModule.class.getClassLoader(),
                    new Class<?>[]{GameModule.class},handler);
            assertNull(NativeRewindAdapterPublication.nativeRoot(foreignProjection),
                    "An engine-loaded proxy cannot endorse its creator-loaded lineage handler");
            assertEquals(0,reads.get(),"Foreign reported lineage must not execute outside its owner boundary");
            Publication hostReference=NativeRewindAdapterPublication::register;
            assertDoesNotThrow(()->hostReference.register(registry,root,adapter));
            assertEquals(7,registry.capture().get(adapter.key()));
        }
    }

    public static final class Mode implements GameplayFrameController,RewindSnapshottable<Integer> {
        int value=7;
        @Override public String key() { return "reported-mode"; }
        @Override public Integer capture() { return value; }
        @Override public void restore(Integer value) { this.value=value; }
        @Override public boolean beforeTick(CourseControl course,LogicalInputSnapshot input) { return false; }
        @Override public void afterTick(CourseControl course,boolean advanced) { }
    }
    public interface CapturedNative { RewindSnapshottable<?> state(); }

    @Test void coldServiceBundleCannotCaptureUnregisteredCanonicalNativeState() {
        var root=new Sonic2GameModule(); var nativeService=root.rewindAdapters().getFirst();
        Object before=nativeService.capture(); assertFalse(RewindAdapterOwnership.isOwned(nativeService));
        var disabled=new LinkedHashSet<String>();
        var boundary=new ModFaultBoundary(Map.of("dependent",Set.of("thief")),new ModRuntimeFindingStore(),
                ignored->new ModStateSaveResult.Saved(),disabled::addAll);
        var context=new ModContext("thief","s2",ModAssetRoot.forTests("thief"));
        context.registerServiceBundle("stolen",()-> {
            assertTrue(RewindAdapterOwnership.isNativePublication(nativeService),"Host identity is reserved before this creator factory runs");
            return GameServiceBundle.builder().capturedService("scheduler",RewindSnapshottable.class,nativeService).build();
        });
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->new ModBackedGamePatch(context.freeze(),boundary,(owner,finding)->{}).apply(root,null));
        assertEquals("thief",failure.owner()); assertEquals(Set.of("thief","dependent"),disabled);
        assertEquals(before,nativeService.capture()); assertSame(nativeService,root.rewindAdapters().getFirst());
        assertNull(root.getGameService(Sonic2PlcService.class),"Identity reservation creates no absent PLC job or service");
    }

    @Test void coldExpertPatchCannotRetagUnregisteredNativeStateThroughReturnedCallback() {
        var root=new Sonic2GameModule(); var nativeService=root.rewindAdapters().getFirst();
        Object before=nativeService.capture(); assertFalse(RewindAdapterOwnership.isOwned(nativeService));
        var disabled=new LinkedHashSet<String>();
        var boundary=new ModFaultBoundary(Map.of("dependent",Set.of("thief")),new ModRuntimeFindingStore(),
                ignored->new ModStateSaveResult.Saved(),disabled::addAll);
        var contributed=patch(base-> {
            assertTrue(RewindAdapterOwnership.isNativePublication(nativeService),"Host identity is reserved before this creator apply callback runs");
            return new DelegatingGameModule(base,"thief:patch") {
                @Override public <T> T getGameService(Class<T> type) {
                    return type==CapturedNative.class ? type.cast((CapturedNative)()->nativeService) : super.getGameService(type);
                }
            };
        });
        var module=OwnerBoundGamePatch.wrap("thief",contributed,boundary).apply(root,null);
        var callback=module.getGameService(CapturedNative.class);
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,callback::state);
        assertEquals("thief",failure.owner()); assertEquals(Set.of("thief","dependent"),disabled);
        assertEquals(before,nativeService.capture()); assertFalse(RewindAdapterOwnership.isBound(nativeService));
        assertNull(root.getGameService(Sonic2PlcService.class));
    }

    static GamePatch patch(Mode mode) {
        return patch(base->new DelegatingGameModule(base,"mode:patch") {
            @Override public GameplayFrameController gameplayFrameController() { return mode; }
            @Override public List<RewindSnapshottable<?>> rewindAdapters() {
                var result=new ArrayList<>(super.rewindAdapters()); result.add(mode); return result;
            }
        });
    }
    static GamePatch patch(Function<GameModule,GameModule> factory) {
        return new GamePatch() {
            public String id() { return "mode:patch"; }
            public String displayName() { return "Native lifetime fixture"; }
            public String baseGameId() { return "any"; }
            public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            public List<String> providedMainCharacters() { return List.of(); }
            public GameModule apply(GameModule base,PatchContext context) {
                return factory.apply(base);
            }
        };
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void actualSonic2NativeServiceRecreationKeepsWholePlcStateAndOwnedModePartition() throws Exception {
        exerciseNativeLifetime(TestEnvironment.currentRom(),new Sonic2GameModule(),false);
        exerciseNativeLifetime(TestEnvironment.currentRom(),new Sonic2GameModule(),true);
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void actualSonic3kNativeServiceRecreationKeepsWholePlcStateAndOwnedModePartition() throws Exception {
        exerciseNativeLifetime(TestEnvironment.currentRom(),new Sonic3kGameModule(),false);
        exerciseNativeLifetime(TestEnvironment.currentRom(),new Sonic3kGameModule(),true);
    }

    static void exerciseNativeLifetime(Rom rom,GameModule root,boolean legacyDecoratedRoot) throws Exception {
        var mode=new Mode();
        var cold=root.rewindAdapters(); var coldState=cold.stream().map(RewindSnapshottable::capture).toList();
        assertNull(root.getGameService(PlcLifecycleService.class));
        var boundary=new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),ignored->new ModStateSaveResult.Saved(),ignored->{});
        var resolved=OwnerBoundGamePatch.wrap("mode",patch(mode),boundary).apply(root,null);
        assertEquals(cold,root.rewindAdapters()); assertEquals(coldState,cold.stream().map(RewindSnapshottable::capture).toList());
        assertNull(root.getGameService(PlcLifecycleService.class),"Cold creator application reserves identities without creating native PLC work");
        var world=legacyDecoratedRoot ? new WorldSession(resolved,resolved,StockGameDataSources.pinned(rom,root),null)
                : new WorldSession(root,resolved,StockGameDataSources.pinned(rom,root),null);
        var context=new GameplayModeContext(world);
        var rng=new GameRng(root instanceof Sonic3kGameModule ? GameRng.Flavour.S3K : GameRng.Flavour.S1_S2);
        context.attachGameplayManagers(new Camera(),new TimerManager(),new GameStateManager(),new FadeManager(),rng,new DefaultSolidExecutionRegistry());
        assertSame(root,NativeRewindAdapterPublication.nativeRoot(world.rootGameModule()));
        assertNotNull(resolved.createGame(world.getDataSource()));
        var old=assertInstanceOf(RewindSnapshottable.class,root.getGameService(PlcLifecycleService.class));
        assertSame(old,resolved.getGameService(PlcLifecycleService.class));
        assertSame(old,root.rewindAdapters().stream().filter(adapter->adapter==old).findFirst().orElseThrow());
        assertSame(old,root.rewindAdapters().stream().filter(adapter->adapter==old).findFirst().orElseThrow());
        context.registerGameModuleRewindAdapters();
        var registry=context.getRewindRegistry();
        var controller=(RewindSnapshottable<?>)resolved.gameplayFrameController();
        assertSame(controller,resolved.rewindAdapters().stream().filter(adapter->adapter==controller).findFirst().orElseThrow());
        prepareNativeQueue(old);
        var full=registry.capture(); var course=registry.captureCourse();
        Object queue=old.capture();
        assertEquals(queue,full.get(old.key())); assertEquals(queue,course.get(old.key()));
        assertFalse(course.containsKey(controller.key())); assertTrue(course.containsKey("gamerng"));
        ((PlcLifecycleService)old).serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        Object advanced=old.capture(); assertNotEquals(queue,advanced);
        mode.value=99; registry.restore(full); assertEquals(queue,old.capture()); assertEquals(7,mode.value);
        ((PlcLifecycleService)old).serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        long epoch=registry.courseLayoutVersion();
        assertNotNull(resolved.createGame(world.getDataSource()));
        var current=assertInstanceOf(RewindSnapshottable.class,root.getGameService(PlcLifecycleService.class));
        assertNotSame(old,current,"The native createGame lifecycle retains its existing fresh-service behavior");
        assertThrows(IllegalStateException.class,()->registry.registerOrRefresh(current));
        context.registerGameModuleRewindAdapters();
        assertTrue(registry.courseLayoutVersion()>epoch,"Retained course checkpoints see the changed native incarnation");
        long refreshed=registry.courseLayoutVersion(); context.registerGameModuleRewindAdapters();
        assertEquals(refreshed,registry.courseLayoutVersion(),"Repeated publication of this exact incarnation is idempotent");
        registry.restore(full); assertEquals(queue,current.capture()); assertEquals(advanced,old.capture(),"Prior-load snapshot targets the current service, not the discarded service");
        mode.value=99; registry.restoreCourse(course); assertEquals(99,mode.value); assertEquals(queue,current.capture());
        var invalid=new LinkedHashMap<>(course.entries()); invalid.remove(current.key());
        Object before=current.capture();
        assertThrows(IllegalArgumentException.class,()->registry.restoreCourse(new CompositeSnapshot(invalid)));
        assertEquals(before,current.capture()); assertEquals(99,mode.value);
        assertStaleCourseCheckpointRejected(context,course,epoch);
        assertEquals(before,current.capture()); assertEquals(99,mode.value);
        assertThrows(IllegalStateException.class,()->RewindAdapterOwnership.bind(current,"thief","native"));
        context.destroy();
    }

    static void assertStaleCourseCheckpointRejected(GameplayModeContext context,CompositeSnapshot course,long epoch) throws Exception {
        // Only level metadata is a double. The actual retained checkpoint, registry
        // generation and CourseControl rejection execute before any player/world mutation.
        var level=org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        var levelField=GameplayModeContext.class.getDeclaredField("levelManager"); levelField.setAccessible(true); levelField.set(context,level);
        var checkpointType=com.openggf.game.mode.CourseCheckpoint.class;
        var constructor=checkpointType.getDeclaredConstructor(Object.class,com.openggf.level.Level.class,int.class,int.class,String.class,CompositeSnapshot.class,long.class);
        constructor.setAccessible(true);
        var checkpoint=constructor.newInstance(context.courseCheckpointIdentity(),null,0,0,"sonic",course,epoch);
        var controlConstructor=CourseControl.class.getDeclaredConstructor(GameplayModeContext.class); controlConstructor.setAccessible(true);
        var failure=assertThrows(IllegalArgumentException.class,()->controlConstructor.newInstance(context).restore(checkpoint));
        assertTrue(failure.getMessage().contains("registry adapters have changed"));
        org.mockito.Mockito.verify(level).getCurrentLevel();
        org.mockito.Mockito.verify(level).getCurrentZone();
        org.mockito.Mockito.verify(level).getCurrentAct();
        org.mockito.Mockito.verifyNoMoreInteractions(level);
    }

    static void prepareNativeQueue(RewindSnapshottable<?> service) throws Exception {
        if(service instanceof Sonic2PlcService s2) { s2.append(Sonic2Constants.PLC_EHZ2); s2.prepare(); }
        else assertInstanceOf(Sonic3kLevelTitlePlcService.class,service).beginFreshZoneTitle(8,0,"sonic");
        ((PlcLifecycleService)service).prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
    }
}
