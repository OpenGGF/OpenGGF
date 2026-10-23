package com.openggf.game.sonic2.competition;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.internal.PlayerDplcDmaSink;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVramService;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.level.LevelManager;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The stable world port retains actual per-act PLC/VRAM owners, without an ordinary renderer. */
class TestSonic2CompetitionArtRuntime {
    @Test void foreignBaseModulesAreRejectedBeforeHostOrTitleOwnerLookup() {
        for (var game : List.of(com.openggf.game.GameId.S1, com.openggf.game.GameId.S3K,
                com.openggf.game.GameId.STANDALONE)) {
            var base = mock(com.openggf.game.GameModule.class);
            when(base.getGameId()).thenReturn(game);
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> new Sonic2CompetitionRuntimeModule(base, null));
            assertEquals("S2 ROM module required", failure.getMessage());
            org.mockito.Mockito.verify(base, org.mockito.Mockito.never()).getTitleCardProvider();
        }
    }

    @Test void earlyRegistrationCapturesAnHonestUninstalledBindingAndDelegatesOnce() {
        var delegate = new Delegate();
        var runtime = new Sonic2CompetitionArtRuntime(delegate);
        var registry = new RewindRegistry();
        runtime.registerRewindAdapters(registry);
        runtime.registerRewindAdapters(registry);
        assertEquals(1, delegate.registrations);
        var uninstalled = registry.capture();
        assertEquals(List.of(Delegate.KEY, Sonic2InterlaceVramService.REWIND_KEY),
                new ArrayList<>(uninstalled.entries().keySet()));
        assertNotNull(uninstalled.get(Sonic2InterlaceVramService.REWIND_KEY));
        assertNull(runtime.ownerOrNull());
        assertThrows(IllegalStateException.class, runtime::requireOwner);
        assertThrows(IllegalStateException.class, () -> runtime.playerLoadPlc(null, 1, null));
        assertThrows(IllegalStateException.class, () -> runtime.tailsTailLoadPlc(9, null));
        assertThrows(IllegalStateException.class, () -> runtime.playerLoadPlcOwnedByObject(null));

        var otherRegistry = new RewindRegistry();
        assertThrows(IllegalStateException.class, () -> runtime.registerRewindAdapters(otherRegistry));
        assertTrue(otherRegistry.capture().entries().isEmpty());
        assertEquals(1, delegate.registrations);
        assertLifecycleForwardedOnce(runtime, delegate);
        assertEquals(delegate.diagnostics, runtime.captureQueueDiagnostics());
        assertEquals(1, delegate.diagnosticReads);
        runtime.resetForMissingSnapshot();
        assertEquals(1, delegate.resets);
        registry.restore(uninstalled);
        assertNull(runtime.ownerOrNull(), "an early binding must not fabricate a zero-filled VRAM owner");

        runtime.deregisterRewindAdapters(registry);
        runtime.deregisterRewindAdapters(registry);
        assertTrue(registry.capture().entries().isEmpty());
        assertEquals(1, delegate.deregistrations);
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void everyActUsesItsFreshProductionPlcAndTheRetainedSinkSelectsAllThreeDplcCalls() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var module = new Sonic2GameModule();
        var runtime = new Sonic2CompetitionArtRuntime(new Delegate());
        PlayerDplcDmaSink retainedSink = runtime;
        var firstActors = Actors.fresh();
        module.createGame(rom);
        var firstPlc = module.getGameService(Sonic2PlcService.class);
        firstPlc.append(Sonic2Constants.PLC_STD1);
        var submitted = firstPlc.capture();
        runtime.install(rom, firstPlc, firstActors);
        var firstOwner = runtime.requireOwner();
        assertSame(firstPlc, firstOwner.plcService());
        assertEquals(submitted, firstPlc.capture(), "entry reset cannot erase submitted native preparation");
        assertSame(firstActors.sonic(), firstOwner.capture().sonic());
        assertSame(firstActors.tails(), firstOwner.capture().tails());
        retainedSink.playerLoadPlc(firstActors.sonic(), 1, null);
        firstOwner.serviceDmaQueueVBlank();
        assertArrayEquals(rom.readBytes(0x50000, 32),
                Arrays.copyOfRange(firstOwner.current().bytes(), 0xF000, 0xF020));
        var retainedFirst = firstOwner.capture();

        module.createGame(rom);
        var secondPlc = module.getGameService(Sonic2PlcService.class);
        var secondActors = Actors.fresh();
        secondPlc.append(Sonic2Constants.PLC_STD2);
        var secondSubmitted = secondPlc.capture();
        runtime.install(rom, secondPlc, secondActors);
        var secondOwner = runtime.requireOwner();
        assertNotSame(firstOwner, secondOwner);
        assertNotSame(firstPlc, secondPlc);
        assertSame(secondPlc, secondOwner.plcService());
        assertEquals(secondSubmitted, secondPlc.capture());
        assertArrayEquals(new byte[65536], secondOwner.current().bytes());
        assertEquals(0, secondOwner.capture().sonicLast());
        assertEquals(0, secondOwner.capture().tailsLast());
        assertEquals(0, secondOwner.capture().tailLast());

        retainedSink.playerLoadPlc(firstActors.sonic(), 2, null);
        assertTrue(secondOwner.capture().pending().isEmpty(), "retired human identity has no native art authority");
        retainedSink.playerLoadPlc(secondActors.sonic(), 1, null);
        secondActors.tails().setMappingFrame(1);
        retainedSink.playerLoadPlcOwnedByObject(secondActors.tails());
        retainedSink.tailsTailLoadPlc(9, null);
        assertEquals(7, secondOwner.capture().pending().size());
        var beforeRebind = secondOwner.capture();
        runtime.bindPlayers(secondActors);
        assertEquals(beforeRebind, secondOwner.capture(), "identity rebinding cannot reset pending or last-loaded bytes");
        secondOwner.serviceDmaQueueVBlank();
        // Actual REV01 first cues of Sonic frame 1, Tails frame 1 and tail frame 9.
        assertArrayEquals(rom.readBytes(0x50000, 32),
                Arrays.copyOfRange(secondOwner.current().bytes(), 0xF000, 0xF020));
        assertArrayEquals(rom.readBytes(0x64320, 32),
                Arrays.copyOfRange(secondOwner.current().bytes(), 0xF400, 0xF420));
        assertArrayEquals(rom.readBytes(0x64C20, 32),
                Arrays.copyOfRange(secondOwner.current().bytes(), 0xF600, 0xF620));
        assertEquals(retainedFirst, firstOwner.capture(), "install and retained sink calls cannot mutate an earlier act");
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void earlyBindingRestoresTheRetainedOwnerBeforeLaterHumanReconstructionAndReplaysForward() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var runtime = new Sonic2CompetitionArtRuntime(new Delegate());
        var registry = new RewindRegistry();
        runtime.registerRewindAdapters(registry);
        var sprites = new RebindingSprites(runtime);
        registry.register(sprites);
        var uninstalled = registry.capture();
        assertEquals(List.of(Delegate.KEY, Sonic2InterlaceVramService.REWIND_KEY, sprites.key()),
                new ArrayList<>(uninstalled.entries().keySet()));

        var module = new Sonic2GameModule();
        module.createGame(rom);
        var firstPlc = module.getGameService(Sonic2PlcService.class);
        firstPlc.append(Sonic2Constants.PLC_STD1);
        runtime.install(rom, firstPlc, sprites.actors);
        var firstOwner = runtime.requireOwner();
        firstOwner.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        runtime.playerLoadPlc(sprites.actors.sonic(), 1, null);
        runtime.tailsTailLoadPlc(9, null);
        var firstState = firstOwner.capture();
        assertNotNull(firstState.plc().activeEntry());
        assertFalse(firstState.pending().isEmpty());
        var firstBinding = registry.capture();
        finishPendingFrame(runtime, sprites.actors);
        var firstForward = firstOwner.capture();

        module.createGame(rom);
        var secondPlc = module.getGameService(Sonic2PlcService.class);
        secondPlc.append(Sonic2Constants.PLC_STD2);
        sprites.actors = Actors.fresh();
        runtime.install(rom, secondPlc, sprites.actors);
        var secondOwner = runtime.requireOwner();
        runtime.tailsTailLoadPlc(0x81, null);
        var secondState = secondOwner.capture();
        var secondBinding = registry.capture();

        sprites.expect(firstOwner, firstState);
        registry.restore(firstBinding);
        assertSame(firstOwner, runtime.requireOwner());
        assertNotSame(firstState.sonic(), sprites.actors.sonic());
        assertSame(sprites.actors.sonic(), firstOwner.capture().sonic());
        assertSame(sprites.actors.tails(), firstOwner.capture().tails());
        assertArtState(firstState, firstOwner.capture());
        runtime.playerLoadPlc(firstState.sonic(), 2, null);
        assertArtState(firstState, firstOwner.capture());
        finishPendingFrame(runtime, sprites.actors);
        assertArtState(firstForward, firstOwner.capture());

        sprites.expect(secondOwner, secondState);
        registry.restore(secondBinding);
        assertSame(secondPlc, runtime.requireOwner().plcService());
        assertArtState(secondState, secondOwner.capture());
        sprites.expect(null, null);
        registry.restore(uninstalled);
        assertNull(runtime.ownerOrNull());
        assertThrows(IllegalStateException.class, () -> runtime.tailsTailLoadPlc(9, null));
        assertArtState(firstForward, firstOwner.capture());
        assertArtState(secondState, secondOwner.capture());
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void selectedOwnerDelegatesLifecycleDiagnosticsAndMissingResetWithoutDuplicateAdapters() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var delegate = new Delegate();
        var runtime = new Sonic2CompetitionArtRuntime(delegate);
        var registry = new RewindRegistry();
        runtime.registerRewindAdapters(registry);
        var plc = new Sonic2PlcService(rom);
        plc.append(Sonic2Constants.PLC_STD1);
        var actors = Actors.fresh();
        runtime.install(rom, plc, actors);
        var owner = runtime.requireOwner();
        runtime.playerLoadPlc(actors.sonic(), 1, null);
        runtime.tailsTailLoadPlc(9, null);
        actors.tails().setMappingFrame(1);
        runtime.playerLoadPlcOwnedByObject(actors.tails());
        assertEquals(List.of("body", "tail", "owned-body"), delegate.sinkCalls);
        assertLifecycleForwardedOnce(runtime, delegate);
        assertEquals(delegate.diagnostics, runtime.captureQueueDiagnostics());
        assertEquals(1, delegate.diagnosticReads);
        assertEquals(QueueDiagnosticSnapshot.Kind.S2_NEMESIS_PLC,
                owner.plcLifecycle().captureQueueDiagnostics().getFirst().kind());
        assertEquals(List.of(Delegate.KEY, Sonic2InterlaceVramService.REWIND_KEY),
                new ArrayList<>(registry.capture().entries().keySet()));
        assertFalse(registry.capture().containsKey(Sonic2PlcService.REWIND_KEY));

        // Reset only the missing binding: the separately registered delegate has
        // its own present snapshot, so the owner must reset it exactly once.
        registry.restore(new CompositeSnapshot(Map.of(Delegate.KEY, delegate.capture())));
        assertSame(owner, runtime.requireOwner());
        assertEquals(1, delegate.resets);
        assertFalse(plc.isBusy());
        assertTrue(owner.capture().pending().isEmpty());
        assertArrayEquals(new byte[65536], owner.current().bytes());
        assertEquals(0, owner.capture().sonicLast());
        runtime.deregisterRewindAdapters(registry);
        assertTrue(registry.capture().entries().isEmpty());
        assertEquals(1, delegate.registrations);
        assertEquals(1, delegate.deregistrations);
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void profileExplicitLagDmaPublishesOnlyTheAdmittedOwnersRealQueuedBytes() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var base = new Sonic2GameModule();
        base.createGame(rom);
        var plc = base.getGameService(Sonic2PlcService.class);
        var delegate = new Delegate();
        var runtime = new Sonic2CompetitionArtRuntime(delegate);
        runtime.install(rom, plc, Actors.fresh());
        var owner = runtime.requireOwner();
        var module = new DelegatingGameModule(base, "test:native-dma-publication") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == Sonic2InterlaceVramService.class
                        ? type.cast(runtime.ownerOrNull()) : super.getGameService(type);
            }
        };
        var context = mock(GameplayModeContext.class);
        var level = mock(LevelManager.class);
        when(context.runtimeArtCoordinator()).thenReturn(runtime);
        when(context.getLevelManager()).thenReturn(level);
        when(context.getWorldSession()).thenReturn(new WorldSession(module));
        var host = mock(Sonic2NativeCompetitionHost.class);
        when(host.context()).thenReturn(context);
        var profile = new Sonic2CompetitionLevelInitProfile(host);

        plc.append(Sonic2Constants.PLC_STD1);
        owner.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertNotNull(plc.capture().activeEntry());
        // The actual first Sonic frame-one cue is 64 words; its first tile is blank.
        assertTrue(owner.queueDmaTransfer(0x50000, 0x400, 64));
        var queued = owner.capture();
        byte[] expected = rom.readBytes(0x50000, 128);
        assertFalse(Arrays.equals(new byte[128], expected), "ROM bytes must distinguish publication from no service");

        profile.serviceQueuedPatternDma(PlcLifecyclePhase.LAG, false, runtime, level);
        assertEquals(queued, owner.capture());
        assertThrows(IllegalArgumentException.class, () -> profile.serviceQueuedPatternDma(
                PlcLifecyclePhase.LAG, true, RuntimeArtCoordinator.NONE, level));
        assertEquals(queued, owner.capture());
        var foreignLevel = mock(LevelManager.class);
        assertThrows(IllegalArgumentException.class, () -> profile.serviceQueuedPatternDma(
                PlcLifecyclePhase.LAG, true, runtime, foreignLevel));
        assertEquals(queued, owner.capture());

        profile.serviceQueuedPatternDma(PlcLifecyclePhase.LAG, true, runtime, level);
        assertArrayEquals(expected, Arrays.copyOfRange(owner.current().bytes(), 0x400, 0x480));
        assertTrue(owner.capture().pending().isEmpty());
        assertEquals(queued.plc(), plc.capture(), "explicit CtrlDMA must not service or prepare the native PLC");
        assertTrue(delegate.calls.isEmpty(), "DMA publication cannot repeat runtime-art loop-tail callbacks");
    }

    private static void finishPendingFrame(Sonic2CompetitionArtRuntime runtime, Actors actors) {
        actors.tails().setMappingFrame(1);
        runtime.playerLoadPlcOwnedByObject(actors.tails());
        runtime.requireOwner().serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
    }

    private static void assertArtState(Sonic2InterlaceVramService.Snapshot expected,
                                       Sonic2InterlaceVramService.Snapshot actual) {
        assertEquals(expected.vram(), actual.vram());
        assertEquals(expected.plc(), actual.plc());
        assertEquals(expected.pending(), actual.pending());
        assertEquals(expected.sonicLast(), actual.sonicLast());
        assertEquals(expected.tailsLast(), actual.tailsLast());
        assertEquals(expected.tailLast(), actual.tailLast());
    }

    private static void assertLifecycleForwardedOnce(Sonic2CompetitionArtRuntime runtime, Delegate delegate) {
        runtime.beforeTimingService(HardwareServiceBoundary.VINT_SERVICE);
        runtime.afterTimingService(HardwareServiceBoundary.POST_OBJECTS);
        runtime.deferProductionSubmissionForHeldLoopTail();
        runtime.deferProductionFirstChildForLateProducer();
        runtime.deferProductionSubmissionForHeldLoopTailClosure();
        runtime.finishHeldLoopTailClosure();
        assertTrue(runtime.ownsHeldLevelCounterHardwareTail());
        runtime.runHeldIterationLoopTail();
        assertEquals(List.of("before:VINT_SERVICE", "after:POST_OBJECTS", "defer", "late", "closure", "finish",
                "owns", "held"), delegate.calls);
    }

    private record Actors(AbstractPlayableSprite sonic, AbstractPlayableSprite tails) implements NativeCompetitionRoster {
        static Actors fresh() {
            return new Actors(new Sonic("sonic", (short) 0, (short) 0),
                    new Tails("tails", (short) 0, (short) 0));
        }
        @Override public AbstractPlayableSprite player(int slot) {
            return switch (slot) { case 0 -> sonic; case 1 -> tails; default -> throw new IndexOutOfBoundsException(slot); };
        }
        @Override public LevelState levelState(int slot) { throw new UnsupportedOperationException(); }
        @Override public GameStateManager gameState(int slot) { throw new UnsupportedOperationException(); }
        @Override public Camera camera(int slot) { throw new UnsupportedOperationException(); }
    }

    /** Models the later sprite adapter recreating canonical humans, not restoring art itself. */
    private static final class RebindingSprites implements RewindSnapshottable<Actors> {
        private final Sonic2CompetitionArtRuntime runtime;
        private Actors actors = Actors.fresh();
        private Sonic2InterlaceVramService expectedOwner;
        private Sonic2InterlaceVramService.Snapshot expectedState;
        private RebindingSprites(Sonic2CompetitionArtRuntime runtime) { this.runtime = runtime; }
        void expect(Sonic2InterlaceVramService owner, Sonic2InterlaceVramService.Snapshot snapshot) {
            expectedOwner = owner;
            expectedState = snapshot;
        }
        @Override public String key() { return "later-canonical-sprites"; }
        @Override public Actors capture() { return actors; }
        @Override public void restore(Actors snapshot) {
            assertSame(expectedOwner, runtime.ownerOrNull(), "art selection must precede canonical sprite restore");
            if (expectedOwner != null) assertEquals(expectedState, expectedOwner.capture());
            actors = Actors.fresh();
            runtime.bindPlayers(actors);
        }
    }

    private static final class Delegate implements RuntimeArtCoordinator, PlayerDplcDmaSink,
            RewindSnapshottable<Integer> {
        private static final String KEY = "test-native-art-delegate";
        private final List<String> calls = new ArrayList<>();
        private final List<String> sinkCalls = new ArrayList<>();
        private final List<QueueDiagnosticSnapshot> diagnostics = List.of(QueueDiagnosticSnapshot.idle(
                QueueDiagnosticSnapshot.Kind.S3K_KOS_DIRECT, List.of()));
        private int registrations, deregistrations, resets, diagnosticReads;
        @Override public String key() { return KEY; }
        @Override public Integer capture() { return 0; }
        @Override public void restore(Integer snapshot) { }
        @Override public void registerRewindAdapters(RewindRegistry registry) { registrations++; registry.register(this); }
        @Override public void deregisterRewindAdapters(RewindRegistry registry) { deregistrations++; registry.deregister(KEY); }
        @Override public void resetForMissingSnapshot() { resets++; }
        @Override public void beforeTimingService(HardwareServiceBoundary boundary) { calls.add("before:" + boundary); }
        @Override public void afterTimingService(HardwareServiceBoundary boundary) { calls.add("after:" + boundary); }
        @Override public void deferProductionSubmissionForHeldLoopTail() { calls.add("defer"); }
        @Override public void deferProductionFirstChildForLateProducer() { calls.add("late"); }
        @Override public void deferProductionSubmissionForHeldLoopTailClosure() { calls.add("closure"); }
        @Override public void finishHeldLoopTailClosure() { calls.add("finish"); }
        @Override public boolean ownsHeldLevelCounterHardwareTail() { calls.add("owns"); return true; }
        @Override public void runHeldIterationLoopTail() { calls.add("held"); }
        @Override public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() { diagnosticReads++; return diagnostics; }
        @Override public void playerLoadPlc(AbstractPlayableSprite player, int frame, SpriteDplcFrame dplc) {
            sinkCalls.add("body");
        }
        @Override public void tailsTailLoadPlc(int frame, SpriteDplcFrame dplc) { sinkCalls.add("tail"); }
        @Override public void playerLoadPlcOwnedByObject(AbstractPlayableSprite player) { sinkCalls.add("owned-body"); }
    }
}
