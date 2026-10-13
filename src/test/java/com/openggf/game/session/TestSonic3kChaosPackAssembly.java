package com.openggf.game.session;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameplayObservation;
import com.openggf.game.mutators.AudioMutatorRuntime;
import com.openggf.game.mutators.LivePresentationRuntime;
import com.openggf.game.mutators.MotionMutatorRuntime;
import com.openggf.game.mutators.TransientMutatorPolicyAccess;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.mutators.NativeMutatorRuntime;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModBackedGamePatch;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.mutators.*;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real patch/provider/context composition, independent of the packs' isolated consumer fixtures. */
@Isolated
class TestSonic3kChaosPackAssembly {
    @TempDir Path saves;
    String oldRoot;
    final AtomicInteger preparations = new AtomicInteger();
    ModFaultBoundary faults;

    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        oldRoot = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", saves.toString());
        faults = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    @AfterEach void cleanup() {
        SessionManager.clear();
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        if (oldRoot == null) System.clearProperty("openggf.saveRoot");
        else System.setProperty("openggf.saveRoot", oldRoot);
    }

    GameModule patchedModule() {
        GameModule module = new Sonic3kGameModule();
        for (String owner : List.of("first-chaos", "second-chaos")) {
            var definition = new MutatorDefinition("jump", "Jump", "", MutatorScope.LIVE,
                    MutatorScope.LIVE, List.of(), Set.of(MutatorCapability.PLAYER_MOTION), options -> {
                preparations.incrementAndGet();
                return List.of(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.JUMP_SCALE, 150));
            });
            module = new ModBackedGamePatch(
                    ModContextTestAccess.freezeWithMutator(owner, "s3k", definition), faults).apply(module, null);
        }
        return new DelegatingGameModule(module, "chaos-fixture:ordinary") {
            @Override public boolean supportsSidekick() { return false; }
            @Override public <T> T getGameService(Class<T> type) {
                if (type == MutatorSupportProfile.class) return type.cast(new MutatorSupportProfile() {
                    public Set<MutatorCapability> capabilities(int zone, int act) {
                        return Set.of(MutatorCapability.PLAYER_MOTION);
                    }
                    public boolean supportsPlayer(String key, boolean leader) { return leader && key.equals("sonic"); }
                });
                return super.getGameService(type);
            }
        };
    }

    GameplayModeContext open() {
        var module = patchedModule();
        GameModuleRegistry.setCurrent(module);
        SessionManager.openGameplaySession(new Sonic3kGameModule(), module, null);
        return TestEnvironment.activeGameplayMode();
    }

    @Test void stockSonic3kRetainsNoPolicyGraph() {
        var world = new WorldSession(new Sonic3kGameModule());
        assertFalse(WorldSessionPolicyAccess.hasPolicies(world));
        assertNull(TransientMutatorPolicyAccess.source(world));
        assertNull(WorldSessionPolicyAccess.getService(world, AudioMutatorRuntime.class));
    }

    @Test void twoPatchOwnersShareOneBoundNativePackAndRestoreAcceptedPoliciesWithoutFactories() {
        var context = open();
        var world = context.getWorldSession();
        assertEquals(2, world.getGameModule().getGameService(MutatorCatalog.class).definitions().size());
        var motion = WorldSessionPolicyAccess.getService(world, MotionMutatorRuntime.class);
        assertNotNull(motion);
        assertNotNull(WorldSessionPolicyAccess.getService(world, NativeMutatorRuntime.class));
        assertNotNull(WorldSessionPolicyAccess.getService(world, LivePresentationRuntime.class));
        assertNotNull(WorldSessionPolicyAccess.getService(world, AudioMutatorRuntime.class));
        assertSame(motion, WorldSessionPolicyAccess.getService(world, MotionMutatorRuntime.class));
        var sonic = new Sonic("sonic", (short) 32, (short) 48);
        context.getSpriteManager().addSprite(sonic);
        var source = (TemporaryMutatorState) TransientMutatorPolicyAccess.source(world);
        try (var port = source.open("second-chaos", Set.of(MutatorCapability.PLAYER_MOTION))) {
            assertTrue(port.activate("jump", Map.of(), 2).accepted());
        }
        assertEquals(0x9C0, motion.jumpImpulse(sonic, 0x680), "assembly binds the real roster and trusted source");
        Object captured = world.policies.rewindAdapter().capture();
        int prepared = preparations.get();
        WorldSessionPolicyAccess.onPresentation(context, LogicalInputSnapshot.neutral(), GameplayObservation.Phase.PAUSED);
        assertEquals(0, source.clock());
        WorldSessionPolicyAccess.onPresentation(context, LogicalInputSnapshot.neutral(), GameplayObservation.Phase.ACTIVE);
        WorldSessionPolicyAccess.onPresentation(context, LogicalInputSnapshot.neutral(), GameplayObservation.Phase.ACTIVE);
        assertEquals(0x680, motion.jumpImpulse(sonic, 0x680));
        restore(world.policies.rewindAdapter(), captured);
        assertEquals(0x9C0, motion.jumpImpulse(sonic, 0x680));
        assertEquals(prepared, preparations.get(), "restoring accepted policies never reruns a mod factory");
        WorldSessionPolicyAccess.leaveOrdinaryGameplay(world, GameplayObservation.Phase.STAGE);
        assertTrue(source.entries().isEmpty());
        assertEquals(0x680, motion.jumpImpulse(sonic, 0x680));
    }

    @Test void aDifferentWorldCannotRestoreAnotherPackGraph() {
        var module = patchedModule();
        var first = new WorldSession(new Sonic3kGameModule(), module, null);
        var second = new WorldSession(new Sonic3kGameModule(), module, null);
        Object captured = first.policies.rewindAdapter().capture();
        assertThrows(IllegalArgumentException.class, () -> restore(second.policies.rewindAdapter(), captured));
        assertTrue(TransientMutatorPolicyAccess.entries(second).isEmpty());
        first.policies.retire();
        second.policies.retire();
    }

    @Test void decodeAndPreviewPreserveTheCapturedPackAndAcceptedLeases() {
        var context = open();
        var world = context.getWorldSession();
        var source = (TemporaryMutatorState) TransientMutatorPolicyAccess.source(world);
        try (var port = source.open("second-chaos", Set.of(MutatorCapability.PLAYER_MOTION))) {
            assertTrue(port.activate("jump", Map.of(), 600).accepted());
        }
        var nativeRuntime = WorldSessionPolicyAccess.getService(world, NativeMutatorRuntime.class);
        var motion = WorldSessionPolicyAccess.getService(world, MotionMutatorRuntime.class);
        var picture = WorldSessionPolicyAccess.getService(world, LivePresentationRuntime.class);
        var audio = WorldSessionPolicyAccess.getService(world, AudioMutatorRuntime.class);
        var nativeState = nativeRuntime.capture();
        var motionState = motion.capture();
        var pictureState = picture.capture();
        var audioState = audio.capture();
        var entries = source.entries();
        Object wholeGraph = world.policies.rewindAdapter().capture();
        for (var cause : List.of(com.openggf.game.LevelLoadCause.DECODE_ONLY,
                com.openggf.game.LevelLoadCause.PREVIEW)) {
            WorldSessionPolicyAccess.beforeAssembly(world, cause);
            WorldSessionPolicyAccess.failedAssembly(world, cause);
            assertEquals(nativeState, nativeRuntime.capture());
            assertEquals(motionState, motion.capture());
            assertEquals(pictureState, picture.capture());
            assertEquals(audioState, audio.capture());
            assertEquals(entries, source.entries());
            assertDoesNotThrow(() -> restore(world.policies.rewindAdapter(), wholeGraph));
        }
        WorldSessionPolicyAccess.beforeAssembly(world, com.openggf.game.LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        assertTrue(source.entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> nativeRuntime.restore(nativeState));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void restore(RewindSnapshottable adapter, Object snapshot) { adapter.restore(snapshot); }
}
