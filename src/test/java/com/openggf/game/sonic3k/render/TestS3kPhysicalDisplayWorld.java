package com.openggf.game.sonic3k.render;

import com.openggf.game.*;
import com.openggf.game.internal.NativePhysicalLevelLoad;
import com.openggf.game.internal.WorldRewindAdapterFactory;
import com.openggf.game.rewind.NativeRewindAdapterPublication;
import com.openggf.game.rewind.RewindAdapterOwnership;
import com.openggf.game.session.*;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Clear-owned DATA only; direct leaf seeding below is test setup, never native producer proof. */
class TestS3kPhysicalDisplayWorld {
    @BeforeEach void configure() { EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap()); }
    @org.junit.jupiter.api.AfterEach void reset() { com.openggf.tests.TestEnvironment.resetAll(); }

    private static class ProfileHost extends Sonic3kGameModule {
        final LevelInitProfile selected = spy(super.getLevelInitProfile());
        @Override public LevelInitProfile getLevelInitProfile() { return selected == null ? super.getLevelInitProfile() : selected; }
    }
    private static final class ThrowingHost extends ProfileHost {
        final HoldingWriter holding = new HoldingWriter();
        @Override public <T> T getGameService(Class<T> type) {
            return type == NativePhysicalLevelLoad.Writer.class ? type.cast(holding) : super.getGameService(type);
        }
    }
    private static final class HoldingWriter implements NativePhysicalLevelLoad.Writer {
        NativePhysicalLevelLoad.Clear held;
        @Override public void clearFresh(NativePhysicalLevelLoad.Clear clear) {
            held = clear;
            throw new IllegalStateException("handler failed before clear body");
        }
        void replay() { held.target(MethodHandles.lookup(), this); }
    }
    private static LevelLoadContext fresh() {
        var context = new LevelLoadContext();
        context.setLoadMode(LevelLoadMode.FULL);
        context.setIncludePostLoadAssembly(true);
        context.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        return context;
    }
    private static Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }
    private static S3kPhysicalDisplayWorld owner(WorldSession world) throws Exception {
        return (S3kPhysicalDisplayWorld) field(world, "worldPhysicalData");
    }
    private static com.openggf.game.rewind.RewindSnapshottable<Object> adapter(
            S3kPhysicalDisplayWorld owner) throws Exception {
        @SuppressWarnings("unchecked") var adapter =
                (com.openggf.game.rewind.RewindSnapshottable<Object>) field(owner, "rewindAdapter");
        return adapter;
    }
    private static GameplayModeContext attachedContext(WorldSession world) {
        var context = new GameplayModeContext(world);
        context.attachGameplayManagers(new com.openggf.camera.Camera(), new com.openggf.timer.TimerManager(),
                new GameStateManager(), new com.openggf.graphics.FadeManager(),
                new GameRng(GameRng.Flavour.S1_S2), new com.openggf.game.solid.DefaultSolidExecutionRegistry());
        var terrain = new com.openggf.physics.TerrainCollisionManager();
        context.attachLevelManagers(new com.openggf.level.WaterSystem(),
                new com.openggf.level.ParallaxManager(), terrain,
                new com.openggf.physics.CollisionSystem(terrain), new SpriteManager(),
                mock(com.openggf.level.LevelManager.class));
        return context;
    }
    private static S3kPhysicalSstHeaderBank bank(S3kPhysicalDisplayWorld owner) throws Exception {
        return (S3kPhysicalSstHeaderBank) field(owner, "bank");
    }
    private static S3kPhysicalDisplayQueue queue(S3kPhysicalDisplayWorld owner) throws Exception {
        return (S3kPhysicalDisplayQueue) field(owner, "queue");
    }
    private static void seed(S3kPhysicalDisplayWorld owner, int value) throws Exception {
        var bank = bank(owner); var queue = queue(owner);
        bank.clearLevelObjectRam();
        bank.writeWord(0xB000, 0xA, value, "test-only residue");
        queue.restore(new S3kPhysicalDisplayQueue().capture());
        queue.submit(0xB000, 0); queue.submit(0xB000, 0);
        queue.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL);
        queue.publishNext(bank, new S3kPhysicalDisplayQueue.CameraWords(0, 0, 0x7FF));
    }
    private static List<InitStep> selected(ProfileHost root, LevelLoadContext context) {
        return root.selected.levelLoadSteps(context);
    }
    private static void run(ProfileHost root, WorldSession world, LevelLoadContext context,
            LevelLoadMode mode, List<InitStep> steps) throws Exception {
        doReturn(steps).when(root.selected).levelLoadSteps(context);
        // Invoke the real package-private attempt and its actual selected-step loop.
        var type = Class.forName("com.openggf.level.LevelLoadAttempt");
        var ctor = type.getDeclaredConstructor(WorldSession.class, SpriteManager.class,
                LevelLoadMode.class, LevelLoadContext.class);
        ctor.setAccessible(true);
        var attempt = ctor.newInstance(world, null, mode, context);
        var execute = type.getDeclaredMethod("execute", Supplier.class, int.class);
        execute.setAccessible(true);
        try { execute.invoke(attempt, (Supplier<GameModule>) () -> root, 0); }
        catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof Exception failure) throw failure;
            if (wrapped.getCause() instanceof Error failure) throw failure;
            throw wrapped;
        }
    }
    private static void assertCleared(S3kPhysicalDisplayWorld owner) throws Exception {
        var snapshot = bank(owner).capture();
        assertArrayEquals(new byte[0x1FCC], snapshot.image());
        for (boolean known : snapshot.knownBytes()) assertTrue(known);
        for (var origin : snapshot.provenance())
            assertEquals(S3kPhysicalSstHeaderBank.WriteKind.LEVEL_CLEAR, origin.kind());
        assertEquals(new S3kPhysicalDisplayQueue().capture(), queue(owner).capture());
    }

    @Test
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void realFreshLoadRegistersClearDataAndReplaysTwoForwardCycles() throws Exception {
        com.openggf.tests.TestEnvironment.configureGameModuleFixture(com.openggf.tests.rules.SonicGame.SONIC_3K);
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.boot(com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable().toPath(), 3, 0, settings);
            var context = SessionManager.getCurrentGameplayMode();
            var owner = owner(context.getWorldSession());
            assertCleared(owner); // The clear-owned DATA image, not then-current native gameplay RAM.
            var registry = context.getRewindRegistry();
            var before = registry.capture();
            assertEquals(owner.capture(), before.get(S3kPhysicalDisplayWorld.KEY));
            var keys = new java.util.ArrayList<>(before.entries().keySet());
            assertTrue(keys.indexOf("camera") < keys.indexOf("sprites"));
            assertTrue(keys.indexOf("sprites") < keys.indexOf(S3kPhysicalDisplayWorld.KEY));
            assertTrue(keys.indexOf(S3kPhysicalDisplayWorld.KEY) < keys.indexOf("object-manager"));
            session.step(null); session.step(null);
            Object expectedData = registry.capture().get(S3kPhysicalDisplayWorld.KEY);
            for (int replay = 0; replay < 2; replay++) {
                registry.restore(before);
                assertEquals(before.get(S3kPhysicalDisplayWorld.KEY), owner.capture());
                session.step(null); session.step(null);
                assertEquals(expectedData, registry.capture().get(S3kPhysicalDisplayWorld.KEY));
            }
        }
    }

    @Test void worldConstructionReservesDistinctInitiallyUnknownOwnersWithoutClassAliases() throws Exception {
        var root = new Sonic3kGameModule();
        var a = new WorldSession(root); var b = new WorldSession(root);
        assertNotSame(owner(a), owner(b));
        assertNotSame(owner(a), adapter(owner(a)));
        assertFalse(com.openggf.game.rewind.RewindSnapshottable.class.isInstance(owner(a)));
        assertTrue(RewindAdapterOwnership.isNativePublication(adapter(owner(a))));
        assertTrue(RewindAdapterOwnership.isNativePublication(adapter(owner(b))));
        for (boolean known : bank(owner(a)).capture().knownBytes()) assertFalse(known);
        assertNull(root.getGameService(S3kPhysicalDisplayWorld.class));
        assertEquals(0, field(owner(a), "owner").getClass().getDeclaredFields().length);
        for (var field : S3kPhysicalDisplayWorld.class.getDeclaredFields())
            assertFalse(WorldSession.class.isAssignableFrom(field.getType()));
    }

    @Test void contextRebuildRestoresBothWorldsThroughTheirOriginalDataAndAdapterOwners() throws Exception {
        var root = new Sonic3kGameModule();
        var worldA = new WorldSession(root); var worldB = new WorldSession(root);
        var dataA = owner(worldA); var dataB = owner(worldB);
        var adapterA = adapter(dataA); var adapterB = adapter(dataB);
        var contextA = attachedContext(worldA); seed(dataA, 0x1234);
        var savedA = contextA.getRewindRegistry().capture();
        contextA.tearDownManagers();
        var contextB = attachedContext(worldB); seed(dataB, 0x5678);
        var savedB = contextB.getRewindRegistry().capture();
        contextB.tearDownManagers();

        for (int replay = 0; replay < 2; replay++) {
            seed(dataA, 0xA000 + replay); seed(dataB, 0xB000 + replay);
            contextA = attachedContext(worldA);
            contextA.getRewindRegistry().restore(savedA);
            assertSame(dataA, owner(worldA)); assertSame(adapterA, adapter(dataA));
            assertEquals(savedA.get(S3kPhysicalDisplayWorld.KEY), adapterA.capture());
            assertEquals(0x1234, bank(dataA).artTile(0xB000));
            assertEquals(0xB000 + replay, bank(dataB).artTile(0xB000));
            contextA.tearDownManagers();
            contextB = attachedContext(worldB);
            contextB.getRewindRegistry().restore(savedB);
            assertSame(dataB, owner(worldB)); assertSame(adapterB, adapter(dataB));
            assertEquals(savedB.get(S3kPhysicalDisplayWorld.KEY), adapterB.capture());
            assertEquals(0x5678, bank(dataB).artTile(0xB000));
            assertEquals(savedA.get(S3kPhysicalDisplayWorld.KEY), adapterA.capture());
            contextB.tearDownManagers();
        }
    }

    @Test void actualSelectedFreshActionClearsFullBankAndPendingOpenQueueBeforeRngAndDma() throws Exception {
        var root = new ProfileHost(); var world = new WorldSession(root); var context = fresh();
        var steps = selected(root, context);
        assertEquals(List.of("ClearFreshPhysicalDisplayRam", "ClearFreshLevelRng", "ResetFreshLevelDmaQueue"),
                steps.subList(0, 3).stream().map(InitStep::name).toList());
        seed(owner(world), 0x1234);
        run(root, world, context, LevelLoadMode.FULL, List.of(steps.getFirst()));
        assertCleared(owner(world));
    }

    @ParameterizedTest @ValueSource(strings = {"decode", "preview", "restore", "in-place"})
    void nonclearProfilesCarryBothChannelsWithoutInferringFreshAuthority(String kind) throws Exception {
        var root = new ProfileHost(); var world = new WorldSession(root); var context = fresh();
        LevelLoadMode mode = LevelLoadMode.FULL;
        switch (kind) {
            case "decode" -> { context.setIncludePostLoadAssembly(false); context.setAssemblyKind(LevelAssemblyKind.DECODE_ONLY); }
            case "preview" -> { mode = LevelLoadMode.PREVIEW_CAPTURE; context.setLoadMode(mode); }
            case "restore" -> context.setAssemblyKind(LevelAssemblyKind.STATE_RESTORATION);
            case "in-place" -> context.setAssemblyKind(LevelAssemblyKind.DECODE_ONLY);
            default -> throw new AssertionError(kind);
        }
        assertTrue(selected(root, context).stream().noneMatch(step -> step.name().equals("ClearFreshPhysicalDisplayRam")));
        seed(owner(world), 0x4567); Object before = owner(world).capture();
        run(root, world, context, mode, List.of(new InitStep("carry", "nonclear test boundary", () -> {})));
        assertEquals(before, owner(world).capture());
    }

    @Test void savedPrivateActionCannotClearOutsideItsActualAttemptOrAfterAttemptClosure() throws Exception {
        var root = new ProfileHost(); var world = new WorldSession(root); var context = fresh();
        var clear = selected(root, context).getFirst();
        seed(owner(world), 0x2468); Object before = owner(world).capture();
        assertThrows(SecurityException.class, clear::execute);
        run(root, world, context, LevelLoadMode.FULL, List.of(new InitStep("carry", "none", () -> {})));
        assertThrows(SecurityException.class, clear::execute);
        assertEquals(before, owner(world).capture());
    }

    @Test void wrappedPrivateActionUnderAnotherSelectedStepHasNoClearAuthority() throws Exception {
        var root = new ProfileHost(); var world = new WorldSession(root); var context = fresh();
        var clear = selected(root, context).getFirst();
        seed(owner(world), 0x9876); Object before = owner(world).capture();
        var wrapped = new InitStep("not the selected private action", "test callback", clear::execute);
        assertThrows(SecurityException.class, () -> run(root, world, context, LevelLoadMode.FULL, List.of(wrapped)));
        assertEquals(before, owner(world).capture());
        context.setAssemblyKind(LevelAssemblyKind.STATE_RESTORATION);
        assertThrows(SecurityException.class, () -> run(root, world, context, LevelLoadMode.FULL, List.of(clear)));
        assertEquals(before, owner(world).capture());
    }

    @Test void duplicateSelectedActionCannotClearAgainAfterInterveningResidue() throws Exception {
        var root = new ProfileHost(); var world = new WorldSession(root); var context = fresh();
        var clear = selected(root, context).getFirst();
        var mutate = new InitStep("test residue", "test-only setup after real clear", () -> {
            try { seed(owner(world), 0x7788); } catch (Exception failure) { throw new AssertionError(failure); }
        });
        assertThrows(SecurityException.class, () -> run(root, world, context, LevelLoadMode.FULL,
                List.of(clear, mutate, clear)));
        assertEquals(0x7788, bank(owner(world)).artTile(0xB000));
        assertNotEquals(new S3kPhysicalDisplayQueue().capture(), queue(owner(world)).capture());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nestedAttemptPermanentlyRevokesSuspendedClearEvenWhenItReturns(boolean sameWorld) throws Exception {
        var root = new ProfileHost(); var world = new WorldSession(root); var context = fresh();
        var clear = selected(root, context).getFirst();
        var innerRoot = sameWorld ? root : new ProfileHost();
        var innerWorld = sameWorld ? world : new WorldSession(innerRoot);
        var innerContext = new LevelLoadContext();
        seed(owner(world), 0x3456); Object before = owner(world).capture();
        var nested = new InitStep("nested actual attempt", "test reentrancy", () -> {
            try { run(innerRoot, innerWorld, innerContext, LevelLoadMode.FULL,
                    List.of(new InitStep("inner carry", "none", () -> {}))); }
            catch (Exception failure) { throw new AssertionError(failure); }
        });
        assertThrows(SecurityException.class, () -> run(root, world, context, LevelLoadMode.FULL, List.of(nested, clear)));
        assertEquals(before, owner(world).capture());
    }

    @Test void eligibilityCallbackNestedDecodeDuringConsumeDeniesBeforeEitherChannelWrites() throws Exception {
        var root = new ProfileHost(); var world = new WorldSession(root);
        var reentered = new java.util.concurrent.atomic.AtomicBoolean();
        var walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        var context = new LevelLoadContext() {
            @Override public boolean permitsInitialProcessSpritesRequest() {
                // Identify the real consume boundary, never a fitted eligibility-call count.
                boolean atConsume = walker.walk(frames -> frames.anyMatch(frame ->
                        frame.getDeclaringClass() == NativePhysicalLevelLoad.Clear.class
                                && frame.getMethodName().equals("consume")));
                if (atConsume && reentered.compareAndSet(false, true)) {
                    try {
                        var decode = new LevelLoadContext(); // Genuine nonfresh nested attempt.
                        run(root, world, decode, LevelLoadMode.FULL,
                                List.of(new InitStep("nested decode", "test callback reentry", () -> {})));
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                }
                return super.permitsInitialProcessSpritesRequest();
            }
        };
        context.setLoadMode(LevelLoadMode.FULL);
        context.setIncludePostLoadAssembly(true);
        context.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        var clear = selected(root, context).getFirst();
        seed(owner(world), 0xA55A);
        var bankBefore = bank(owner(world)).capture();
        var queueBefore = queue(owner(world)).capture();
        Object before = owner(world).capture();
        assertThrows(IllegalStateException.class,
                () -> run(root, world, context, LevelLoadMode.FULL, List.of(clear)));
        assertTrue(reentered.get(), "The actual nested attempt must run inside consume's eligibility callback");
        assertEquals(bankBefore, bank(owner(world)).capture());
        assertEquals(queueBefore, queue(owner(world)).capture());
        assertEquals(before, owner(world).capture());
        assertThrows(SecurityException.class, clear::execute);
        assertEquals(before, owner(world).capture());
    }

    @Test void failedWriterCannotClearOrReplayItsHeldInvocation() throws Exception {
        var root = new ThrowingHost(); var world = new WorldSession(root); var context = fresh();
        var clear = selected(root, context).getFirst();
        seed(owner(world), 0x6677); Object before = owner(world).capture();
        assertThrows(IllegalStateException.class, () -> run(root, world, context, LevelLoadMode.FULL, List.of(clear)));
        assertNotNull(root.holding.held);
        assertThrows(IllegalStateException.class, root.holding::replay);
        assertEquals(before, owner(world).capture());
    }

    @Test void laterLoadFailureRetainsTheClearThatActuallyCompleted() throws Exception {
        var root = new ProfileHost(); var world = new WorldSession(root); var context = fresh();
        var clear = selected(root, context).getFirst();
        seed(owner(world), 0x8899);
        var later = new InitStep("later failure", "after native clear", () -> { throw new IllegalStateException("later"); });
        assertThrows(IllegalStateException.class, () -> run(root, world, context, LevelLoadMode.FULL, List.of(clear, later)));
        assertCleared(owner(world));
        assertThrows(SecurityException.class, clear::execute);
    }

    @Test void twoWorldsRestoreBothFullChannelsAndReplayTwoLeafCyclesIndependently() throws Exception {
        var root = new Sonic3kGameModule(); var a = owner(new WorldSession(root)); var b = owner(new WorldSession(root));
        seed(a, 0x1234); seed(b, 0x5678);
        Object sa = a.capture(), sb = b.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            queue(a).finishVisit(); queue(b).finishVisit();
            queue(a).publishNext(bank(a), new S3kPhysicalDisplayQueue.CameraWords(0, 0, 0x7FF));
            queue(b).publishNext(bank(b), new S3kPhysicalDisplayQueue.CameraWords(0, 0, 0x7FF));
            Object forwardA = a.capture(), forwardB = b.capture();
            a.restore(sa); b.restore(sb);
            assertEquals(sa, a.capture()); assertEquals(sb, b.capture());
            queue(a).finishVisit(); queue(b).finishVisit();
            queue(a).publishNext(bank(a), new S3kPhysicalDisplayQueue.CameraWords(0, 0, 0x7FF));
            queue(b).publishNext(bank(b), new S3kPhysicalDisplayQueue.CameraWords(0, 0, 0x7FF));
            assertEquals(forwardA, a.capture()); assertEquals(forwardB, b.capture());
            a.restore(sa); b.restore(sb);
        }
    }

    @Test void crossWorldAndMixedChannelSnapshotsAreRejectedBeforeEitherLeafMutation() throws Exception {
        var root = new Sonic3kGameModule(); var a = owner(new WorldSession(root)); var b = owner(new WorldSession(root));
        seed(a, 0x1111); seed(b, 0x2222);
        Object sa = a.capture(), sb = b.capture();
        assertThrows(IllegalArgumentException.class, () -> a.restore(sb));
        var ctor = sa.getClass().getDeclaredConstructors()[0]; ctor.setAccessible(true);
        for (String foreign : List.of("bank", "queue")) {
            Object mixed = ctor.newInstance(field(sa, "owner"),
                    field(foreign.equals("bank") ? sb : sa, "bank"),
                    field(foreign.equals("queue") ? sb : sa, "queue"));
            assertThrows(IllegalArgumentException.class, () -> a.restore(mixed));
            assertEquals(sa, a.capture()); assertEquals(sb, b.capture());
        }
    }

    @Test void snapshotClonesAndMissingEntryFailurePreserveAllChannels() throws Exception {
        var root = new Sonic3kGameModule(); var a = owner(new WorldSession(root)); seed(a, 0x1122);
        Object saved = a.capture();
        var bs = (S3kPhysicalSstHeaderBank.Snapshot) field(field(saved, "bank"), "value");
        var qs = (S3kPhysicalDisplayQueue.Snapshot) field(field(saved, "queue"), "value");
        bs.image()[0] = 99; bs.knownBytes()[0] = false; bs.provenance()[0] = null; qs.image()[0] = 99;
        assertEquals(saved, a.capture());
        assertThrows(IllegalStateException.class, adapter(a)::resetForMissingSnapshot);
        assertEquals(saved, a.capture());
    }

    @Test void knowingInternalClassesAndSupplyingOwnFullLookupCannotAllocateReserveOrAccess() throws Exception {
        var root = new Sonic3kGameModule(); var world = new WorldSession(root);
        var factory = root.getGameService(WorldRewindAdapterFactory.class);
        assertThrows(SecurityException.class, () -> factory.open(MethodHandles.lookup(), world));
        assertThrows(SecurityException.class, () -> S3kPhysicalDisplayWorld.create(MethodHandles.lookup(), factory, world));
        assertThrows(SecurityException.class, () -> WorldRewindAdapterAccess.owned(MethodHandles.lookup(), world));
        assertThrows(SecurityException.class, () -> WorldRewindAdapterAccess.data(MethodHandles.lookup(), world));
        assertThrows(SecurityException.class, () -> NativeRewindAdapterPublication.reserveWorldDataCreation(
                MethodHandles.lookup(), world, factory, adapter(owner(world))));
    }
}
