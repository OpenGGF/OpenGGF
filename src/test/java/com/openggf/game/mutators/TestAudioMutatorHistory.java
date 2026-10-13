package com.openggf.game.mutators;

import com.openggf.LevelFrameContext;
import com.openggf.audio.AudioManager;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameplayObservation.Phase;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.*;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.level.LevelManager;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.mutators.*;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual temporary admission and composite history around the ROM driver's outer-frame restore. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestAudioMutatorHistory {
    @TempDir Path saves;

    @Test void naturalOneUpBetweenTicksAndAcceptedAudioReplayAcrossKeyframes() {
        String previousRoot = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var audio = AudioManager.getInstance();
        WorldSessionPolicyState[] owned = new WorldSessionPolicyState[1];
        try {
            audio.setAudioProfile(new Sonic3kAudioProfile());
            audio.setRom(TestEnvironment.currentRom());
            var preparations = new AtomicInteger();
            var capabilities = Set.of(MutatorCapability.AUDIO_EFFECT);
            var faults = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                    ignored -> new ModStateSaveResult.Saved(), ignored -> { });
            var catalog = new MutatorCatalog(List.of(
                    definition("jukebox", AudioEffectPolicy.Mode.MUSIC, 3, preparations),
                    definition("clang", AudioEffectPolicy.Mode.LANDING_CUE, 0x49, preparations)), faults);
            var support = new MutatorSupportProfile() {
                public Set<MutatorCapability> capabilities(int zone, int act) { return capabilities; }
                public boolean supportsPlayer(String character, boolean leader) { return true; }
            };
            var provider = new MutatorWorldProvider(catalog, AudioMutatorRuntime::new);
            var module = new DelegatingGameModule(new Sonic3kGameModule(), "audio-history") {
                @Override public <T> T getGameService(Class<T> type) {
                    if (type == MutatorSupportProfile.class) return type.cast(support);
                    if (type == WorldSessionPolicyProvider.class) return type.cast((WorldSessionPolicyProvider) world -> {
                        owned[0] = provider.open(world);
                        return owned[0];
                    });
                    return super.getGameService(type);
                }
            };
            var world = new WorldSession(module);
            var runtime = WorldSessionPolicyAccess.getService(world, AudioMutatorRuntime.class);
            var temporary = assertInstanceOf(TemporaryMutatorState.class, TransientMutatorPolicyAccess.source(world));
            var sprites = new SpriteManager();
            var player = new Sonic("sonic", (short) 100, (short) 100);
            sprites.addSprite(player);
            var gameplay = mock(GameplayModeContext.class);
            when(gameplay.getWorldSession()).thenReturn(world);
            when(gameplay.getSpriteManager()).thenReturn(sprites);
            WorldSessionPolicyAccess.beforeAssembly(world, LevelLoadCause.FULL_LEVEL_ASSEMBLY);
            WorldSessionPolicyAccess.bindGameplay(gameplay);
            WorldSessionPolicyAccess.bindRoster(world, sprites);
            var level = mock(LevelManager.class);
            when(level.getWorldSession()).thenReturn(world);
            var context = mock(LevelFrameContext.class);
            when(context.levelManager()).thenReturn(level);
            when(context.spriteManager()).thenReturn(sprites);
            when(context.gameModule()).thenReturn(module);
            var registry = new RewindRegistry();
            WorldSessionPolicyAccess.registerRewind(world, registry);
            WorldSessionPolicyAccess.setHistoryRecording(world, true);
            var diagnostics = new ArrayList<Integer>();
            audio.setRequestObserver((kind, cue) -> diagnostics.add(cue));
            audio.playMusic(1);

            try (var port = temporary.open("audio-history", capabilities)) {
                assertFalse(port.activate("jukebox", Map.of(), 0).accepted(), "audio admission is TIMED");
                var clang = port.activate("clang", Map.of(), 600);
                assertTrue(clang.accepted());
                WorldSessionPolicyAccess.beforeNativeTick(context);
                audio.playMusic(0x2A);
                WorldSessionPolicyAccess.afterNativeTick(context);
                assertNotNull(WorldSessionPolicyAccess.captureHistory(world));
                present(world, gameplay, audio);
                assertEquals(0x2A, audio.captureLogicalSnapshot().presentation().activeMusic().musicId());
                assertEquals(List.of(1), runtime.capture().nativeOverrides());
                var oneUpKeyframe = registry.capture();
                var oneUpAudio = audio.captureLogicalSnapshot();
                long ticksBeforeE4 = runtime.capture().nativeTicks();
                long presentationsBeforeE4 = temporary.clock();
                int commandsBeforeE4 = audio.commandTimeline().entryCount();
                int observedBeforeE4 = diagnostics.size();

                // Natural E4 is generated solely by the production ROM sequencer in outer audio frames.
                int outerFrames = 0;
                while (!runtime.capture().nativeOverrides().isEmpty() && outerFrames < 300) {
                    present(world, gameplay, audio);
                    outerFrames++;
                }
                assertTrue(outerFrames > 0 && outerFrames < 300, "one-up must complete through the real ROM driver");
                assertTrue(runtime.capture().nativeOverrides().isEmpty());
                assertEquals(1, runtime.capture().nativeMusic());
                assertEquals(ticksBeforeE4, runtime.capture().nativeTicks(), "outer E4 occurs between gameplay ticks");
                assertEquals(presentationsBeforeE4 + outerFrames, temporary.clock(),
                        "real temporary leases age during zero-tick presentations");
                assertEquals(commandsBeforeE4, audio.commandTimeline().entryCount(), "E4 notification submits no duplicate command");
                assertEquals(observedBeforeE4, diagnostics.size());
                assertEquals(1, audio.captureLogicalSnapshot().presentation().activeMusic().musicId());

                var jukebox = port.activate("jukebox", Map.of(), 180);
                assertTrue(jukebox.accepted());
                landing(context, runtime, player);
                var afterE4History = WorldSessionPolicyAccess.captureHistory(world);
                assertNotNull(afterE4History);
                var acceptedAudio = runtime.capture();
                var acceptedSource = temporary.capture();
                assertEquals(jukebox.activationId(), acceptedAudio.musicActivation());
                assertEquals(3, acceptedAudio.musicCue());
                assertEquals(ticksBeforeE4 + 1, acceptedAudio.lastCueTick());
                assertEquals(List.of(1, 0x2A, 3, 0x49), diagnostics);
                assertTrue(port.active().stream().allMatch(activation -> activation.status().contains("played")
                        || activation.status().contains("playing")));
                present(world, gameplay, audio);
                var acceptedKeyframe = registry.capture();
                var acceptedPhysicalAudio = audio.captureLogicalSnapshot();

                landing(context, runtime, player);
                var cooldownHistory = WorldSessionPolicyAccess.captureHistory(world);
                var cooldownAudio = runtime.capture();
                var cooldownSource = temporary.capture();
                assertEquals(acceptedAudio.lastCueTick(), cooldownAudio.lastCueTick());
                assertTrue(port.cancel(jukebox.activationId()));
                assertTrue(port.cancel(clang.activationId()));
                audio.playMusic(0x31); // Present-day state must not replace accepted historical intent.
                int commandCount = audio.commandTimeline().entryCount();
                int observerCount = diagnostics.size();
                int preparedCount = preparations.get();

                try (var replay = audio.beginRewindReplay(1, 3, AudioReplayReason.SEEK)) {
                    registry.restore(oneUpKeyframe);
                    audio.restoreLogicalSnapshot(oneUpAudio);
                    assertEquals(0x2A, runtime.capture().nativeMusic());
                    assertEquals(1, temporary.entries().size(), "the earlier keyframe predates jukebox admission");
                    // Seek replays native ticks, not the intervening outer audio presentations.
                    // The retained before-state must carry E4's accepted intent across that gap.
                    WorldSessionPolicyAccess.restoreHistoryBefore(world, afterE4History);
                    assertEquals(1, runtime.capture().nativeMusic());
                    assertTrue(runtime.capture().nativeOverrides().isEmpty());
                    assertEquals(0x2A, audio.captureLogicalSnapshot().presentation().activeMusic().musicId(),
                            "accepted intent restoration must not submit a physical zone-song replacement");
                    assertTrue(temporary.entries().stream().anyMatch(entry -> entry.activationId() == jukebox.activationId()));
                    landing(context, runtime, player);
                    WorldSessionPolicyAccess.restoreHistoryAfter(world, afterE4History);
                    assertEquals(acceptedAudio, runtime.capture());
                    assertEquals(acceptedSource, temporary.capture(), "history restores real accepted leases and statuses");

                    // Seeking from the later keyframe must retain the same cooldown and accepted music lease.
                    registry.restore(acceptedKeyframe);
                    audio.restoreLogicalSnapshot(acceptedPhysicalAudio);
                    WorldSessionPolicyAccess.restoreHistoryBefore(world, cooldownHistory);
                    landing(context, runtime, player);
                    WorldSessionPolicyAccess.restoreHistoryAfter(world, cooldownHistory);
                    assertEquals(cooldownAudio, runtime.capture());
                    assertEquals(cooldownSource, temporary.capture());
                    assertFalse(WorldSessionPolicyAccess.replaying(world));
                }
                assertEquals(preparedCount, preparations.get(), "rewind never reruns accepted policy factories");
                assertEquals(commandCount, audio.commandTimeline().entryCount(), "replayed audio decisions submit no physical duplicates");
                assertEquals(observerCount, diagnostics.size());
            }
        } finally {
            if (owned[0] != null) owned[0].retire();
            audio.resetState();
            if (previousRoot == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, previousRoot);
        }
    }

    private static OwnedMutator definition(String name, AudioEffectPolicy.Mode mode, int cue, AtomicInteger preparations) {
        return new OwnedMutator("audio-history", new MutatorDefinition(name, name, "", MutatorScope.LIVE,
                MutatorScope.LIVE, List.of(), Set.of(MutatorCapability.AUDIO_EFFECT), options -> {
                    preparations.incrementAndGet();
                    return List.of(new AudioEffectPolicy(mode, cue, 15));
                }));
    }

    private static void landing(LevelFrameContext context, AudioMutatorRuntime runtime, Sonic player) {
        player.setAir(true);
        WorldSessionPolicyAccess.beforeNativeTick(context);
        player.setAir(false);
        runtime.landed(player);
        WorldSessionPolicyAccess.afterNativeTick(context);
    }

    private static void present(WorldSession world, GameplayModeContext context, AudioManager audio) {
        WorldSessionPolicyAccess.onPresentation(context, LogicalInputSnapshot.neutral(), Phase.ACTIVE);
        audio.presentFrame(PresentationMode.FORWARD);
    }
}
