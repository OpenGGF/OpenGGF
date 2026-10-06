package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.patch.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.io.*;
import com.openggf.mods.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit-patch callbacks must retain engine-recorded ownership without stealing inherited callbacks. */
public class TestOwnerBoundGamePatch {
    @TempDir Path temp;

    public interface Controller extends RewindSnapshottable<Integer>, AutoCloseable {
        boolean beforeTick();
        void afterTick();
        void drawOverlay();
        @Override void close();
    }
    public interface Service { Controller controller(); }
    static final class Mode implements Controller {
        String failure;
        int state;
        Mode(String failure) { this.failure = failure; }
        void check(String callback) { if (callback.equals(failure)) throw new IllegalStateException(callback); }
        @Override public String key() { return "claimed-other-owner:mode"; }
        @Override public boolean beforeTick() { check("beforeTick"); return true; }
        @Override public void afterTick() { check("afterTick"); state++; }
        @Override public void drawOverlay() { check("drawOverlay"); }
        @Override public void close() { check("close"); state = -1; }
        @Override public Integer capture() { check("capture"); return state; }
        @Override public void restore(Integer value) { check("restore"); state = value; }
        @Override public void resetForMissingSnapshot() { check("reset"); state = 0; }
    }
    static final class Patched extends DelegatingGameModule {
        final Mode mode;
        final TitleScreenProvider title;
        Patched(GameModule base, Mode mode, TitleScreenProvider title) { super(base, "claimed-other-owner:patch"); this.mode = mode; this.title = title; }
        @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
        @Override public List<RewindSnapshottable<?>> rewindAdapters() {
            var adapters = new ArrayList<>(base().rewindAdapters()); adapters.add(mode); return adapters;
        }
        @Override public <T> T getGameService(Class<T> type) {
            if (type == Controller.class) return type.cast(mode);
            if (type == Service.class) return type.cast((Service)() -> mode);
            return base().getGameService(type);
        }
    }
    record Fixture(ModFaultBoundary boundary, ModRuntimeFindingStore findings, AtomicReference<Set<String>> disabled) { }
    Fixture fixture() {
        var findings = new ModRuntimeFindingStore(); var disabled = new AtomicReference<Set<String>>();
        return new Fixture(new ModFaultBoundary(Map.of("dependent", Set.of("registered-owner")), findings,
                owners -> new ModStateSaveResult.Saved(), disabled::set), findings, disabled);
    }
    GameModule wrap(GameModule base, GameModule result, Fixture fixture) throws Exception {
        return wrappedPatch(ignored -> result, fixture).apply(base, null);
    }
    GamePatch wrappedPatch(java.util.function.Function<GameModule, GameModule> apply, Fixture fixture) throws Exception {
        return wrappedPatch("registered-owner", apply, fixture);
    }
    GamePatch wrappedPatch(String owner, java.util.function.Function<GameModule, GameModule> apply, Fixture fixture) throws Exception {
        Class<?> helper = Class.forName("com.openggf.mods.runtime.OwnerBoundGamePatch");
        return (GamePatch)helper.getMethod("wrap", String.class, GamePatch.class, ModFaultBoundary.class)
                .invoke(null, owner, patch(apply), fixture.boundary());
    }
    static GamePatch patch(java.util.function.Function<GameModule, GameModule> apply) {
        return new GamePatch() {
            @Override public String id() { return "patch"; }
            @Override public String displayName() { return "claimed-other-owner"; }
            @Override public String baseGameId() { return "s2"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public GameModule apply(GameModule base, PatchContext context) { return apply.apply(base); }
        };
    }
    void abort(Fixture fixture, String callback, Runnable action) {
        var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class, action::run);
        assertEquals("registered-owner", aborted.owner()); assertEquals(callback, aborted.getCause().getMessage());
        assertEquals(Set.of("registered-owner", "dependent"), fixture.disabled().get());
        assertEquals("MOD_CALLBACK_FAILED", fixture.findings().findingsFor("registered-owner").getFirst().code());
        assertTrue(fixture.findings().findingsFor("claimed-other-owner").isEmpty());
    }

    @Test void providerAndControllerCallbacksIncludingCloseAreOwnerBoundedAndStable() throws Exception {
        for (String callback : List.of("beforeTick", "afterTick", "drawOverlay", "close")) {
            Fixture fixture = fixture(); var mode = new Mode(callback); GameModule base = mock(GameModule.class);
            when(base.rewindAdapters()).thenReturn(List.of());
            GameModule wrapped = wrap(base, new Patched(base, mode, null), fixture);
            Controller controller = wrapped.getGameService(Controller.class);
            assertSame(controller, wrapped.getGameService(Controller.class));
            assertSame(controller, wrapped.rewindAdapters().getFirst(), "one object can implement controller and rewind interfaces");
            abort(fixture, callback, switch (callback) {
                case "beforeTick" -> () -> controller.beforeTick();
                case "afterTick" -> controller::afterTick;
                case "drawOverlay" -> controller::drawOverlay;
                default -> controller::close;
            });
        }
        Fixture fixture = fixture(); GameModule base = mock(GameModule.class); var title = mock(TitleScreenProvider.class);
        doThrow(new IllegalStateException("title draw")).when(title).draw();
        GameModule wrapped = wrap(base, new Patched(base, new Mode(null), title), fixture);
        assertSame(wrapped.getTitleScreenProvider(), wrapped.getTitleScreenProvider());
        abort(fixture, "title draw", wrapped.getTitleScreenProvider()::draw);
    }

    @Test void rewindCaptureRestoreResetAndNestedCallbacksHaveStableOwnerBoundaries() throws Exception {
        for (String callback : List.of("capture", "restore", "reset")) {
            Fixture fixture = fixture(); var mode = new Mode(callback); GameModule base = mock(GameModule.class);
            when(base.rewindAdapters()).thenReturn(List.of());
            GameModule wrapped = wrap(base, new Patched(base, mode, null), fixture);
            @SuppressWarnings("unchecked") var adapter = (RewindSnapshottable<Integer>)wrapped.rewindAdapters().getFirst();
            assertSame(adapter, wrapped.rewindAdapters().getFirst());
            abort(fixture, callback, switch (callback) {
                case "capture" -> () -> adapter.capture();
                case "restore" -> () -> adapter.restore(7);
                default -> adapter::resetForMissingSnapshot;
            });
        }
        Fixture fixture = fixture(); GameModule base = mock(GameModule.class); var mode = new Mode("close");
        GameModule wrapped = wrap(base, new Patched(base, mode, null), fixture);
        abort(fixture, "close", wrapped.getGameService(Service.class).controller()::close);
    }

    @Test void inheritedNativeAndEarlierOwnedProvidersAndAdaptersKeepTheirIdentityAndAttribution() throws Exception {
        Fixture fixture = fixture(); GameModule base = mock(GameModule.class); var nativeTitle = mock(TitleScreenProvider.class);
        var nativeAdapter = new Mode("capture"); when(base.getTitleScreenProvider()).thenReturn(nativeTitle);
        when(base.rewindAdapters()).thenReturn(List.of(nativeAdapter));
        doThrow(new IllegalStateException("native title")).when(nativeTitle).draw();
        GameModule wrapped = wrap(base, new Patched(base, new Mode(null), nativeTitle), fixture);
        assertSame(nativeTitle, wrapped.getTitleScreenProvider());
        assertSame(nativeAdapter, wrapped.rewindAdapters().getFirst());
        assertThrows(IllegalStateException.class, wrapped.getTitleScreenProvider()::draw);
        assertThrows(IllegalStateException.class, wrapped.rewindAdapters().getFirst()::capture);
        assertNull(fixture.disabled().get()); assertTrue(fixture.findings().snapshot().isEmpty());
        assertSame(base, wrap(base, base, fixture), "an identity patch adds no owner scope");

        var inheritedFailure = new IllegalStateException("native getter");
        when(base.getTitleScreenProvider()).thenThrow(inheritedFailure);
        GameModule inherited = wrap(base, new DelegatingGameModule(base, "inherited") { }, fixture);
        assertSame(inheritedFailure, assertThrows(IllegalStateException.class, inherited::getTitleScreenProvider));
        assertNull(fixture.disabled().get());
    }

    @Test void patchApplyFailuresUseTrustedRegistrationOwner() throws Exception {
        Fixture fixture = fixture(); GamePatch wrapped = wrappedPatch(base -> { throw new IllegalStateException("apply"); }, fixture);
        abort(fixture, "apply", () -> wrapped.apply(mock(GameModule.class), null));
    }

    @Test void earlierOwnedAdapterIsNotReattributedToTheNextPatch() throws Exception {
        Fixture fixture = fixture(); GameModule nativeBase = mock(GameModule.class);
        when(nativeBase.rewindAdapters()).thenReturn(List.of());
        GameModule earlier = wrappedPatch("earlier-owner", base -> new Patched(base, new Mode("capture"), null), fixture)
                .apply(nativeBase, null);
        GameModule later = wrap(earlier, new Patched(earlier, new Mode(null), null), fixture);
        assertSame(earlier.rewindAdapters().getFirst(), later.rewindAdapters().getFirst());
        var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class, later.rewindAdapters().getFirst()::capture);
        assertEquals("earlier-owner", aborted.owner());
        assertEquals(Set.of("earlier-owner"), fixture.disabled().get());
        assertTrue(fixture.findings().findingsFor("registered-owner").isEmpty());
    }

    @Test void invalidNewAdapterListFailsInsideItsOwnerBoundary() throws Exception {
        Fixture fixture = fixture(); GameModule base = mock(GameModule.class);
        when(base.rewindAdapters()).thenReturn(List.of());
        GameModule wrapped = wrap(base, new DelegatingGameModule(base, "bad-list") {
            @Override public List<RewindSnapshottable<?>> rewindAdapters() { return null; }
        }, fixture);
        abort(fixture, "rewindAdapters must return a list", wrapped::rewindAdapters);
    }

    @Test void standaloneRewindListIsOwnerBoundedWithoutReplacingItsExistingProviderBoundary() throws Exception {
        Fixture fixture = fixture(); GameModule delegate = mock(GameModule.class); var mode = new Mode("restore");
        when(delegate.rewindAdapters()).thenReturn(List.of(mode));
        GameModule standalone = OwnerAwareStandaloneModule.wrap("registered-owner", delegate, fixture.boundary(), Map.of());
        Class<?> helper = Class.forName("com.openggf.mods.runtime.OwnerBoundGamePatch");
        GameModule wrapped = (GameModule)helper.getMethod("wrapStandaloneRewinds", String.class, GameModule.class, ModFaultBoundary.class)
                .invoke(null, "registered-owner", standalone, fixture.boundary());
        @SuppressWarnings("unchecked") var adapter = (RewindSnapshottable<Integer>)wrapped.rewindAdapters().getFirst();
        assertSame(adapter, wrapped.rewindAdapters().getFirst());
        abort(fixture, "restore", () -> adapter.restore(3));
    }

    public static final class RuntimeEntrypoint implements GgfMod {
        @Override public void register(ModContext context) {
            context.registerGamePatch(patch(base -> new DelegatingGameModule(base, "registered-owner:runtime") {
                private final Mode mode = new Mode("capture");
                @Override public List<RewindSnapshottable<?>> rewindAdapters() { return List.of(mode); }
            }));
        }
    }
    public static final class RuntimeContentEntrypoint implements GgfMod {
        @Override public void register(ModContext context) {
            context.registerObject("marker", (spawn, registry) -> null);
            new RuntimeEntrypoint().register(context);
        }
    }
    @Test void runtimeRegistrationInstallsBoundaryForExplicitPatchesWithoutContentBacking() throws Exception {
        runtimeRegistrationInstallsBoundary(RuntimeEntrypoint.class, 1);
    }
    @Test void runtimeRegistrationInstallsBoundaryForExplicitPatchesAfterContentBacking() throws Exception {
        runtimeRegistrationInstallsBoundary(RuntimeContentEntrypoint.class, 2);
    }
    void runtimeRegistrationInstallsBoundary(Class<? extends GgfMod> entrypoint, int registrationCount) throws Exception {
        Path assets = Files.createDirectory(temp.resolve("assets"));
        var snapshot = ModAssetRoot.snapshotDirectory(temp, assets, ModInputLimits.production(), DirectoryAccess.TEST);
        var manifest = new ModManifest(1, "registered-owner", "Owner", SemanticVersion.parse("1.0.0"), List.of("test"),
                "test", VersionRange.parse("*"), ModType.PATCH, "s2", entrypoint.getName(),
                List.of(), Map.of(), Map.of(), null, OptionalInt.empty());
        var descriptor = new ModDescriptor(assets, manifest, "0".repeat(64), true, List.of());
        var loader = new ModDependencyClassLoader("registered-owner", new java.net.URL[0], getClass().getClassLoader(), List.of());
        try (var runtime = new ModRuntime(Map.of("registered-owner", loader), Map.of("registered-owner", snapshot),
                Map.of("registered-owner", descriptor), Map.of())) {
            Fixture fixture = fixture(); runtime.installFaultBoundary(fixture.boundary());
            var plan = runtime.newRegistrationPlan();
            assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures().toString());
            assertEquals(registrationCount, plan.registrations().size());
            var registration = plan.registrations().getLast();
            assertEquals(new PatchOwner.Mod("registered-owner"), registration.owner());
            GameModule wrapped = registration.patch().apply(mock(GameModule.class), null);
            abort(fixture, "capture", wrapped.rewindAdapters().getFirst()::capture);
        }
    }
}
