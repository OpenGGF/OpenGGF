package com.openggf.audio.presentation;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.ChannelType;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.presentation.AudioPresentationCommand.AddSmpsSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.EndMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.HardReset;
import com.openggf.audio.presentation.AudioPresentationCommand.MusicVoiceEntry;
import com.openggf.audio.presentation.AudioPresentationCommand.PushMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceRawPcm;
import com.openggf.audio.presentation.AudioPresentationCommand.RestoreMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedMultiplier;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedShoes;
import com.openggf.audio.presentation.AudioPresentationCommand.StartSampleSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopAllSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopRawPcm;
import com.openggf.audio.presentation.AudioPresentationCommand.ToggleMute;
import com.openggf.audio.presentation.AudioPresentationCommand.ToggleSolo;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.CoordFlagContext;
import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioVoiceRegistry {
    private static final int OUTPUT_RATE = 48_000;
    private static final int MAX_STEREO_FRAMES = 16;

    @Test
    void iterationOrderIsMusicThenSmpsThenRawPcmThenSampleSfxByVoiceId() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        registry.apply(new ReplaceMusic(music(20, 0x81, "music")));
        registry.apply(new AddSmpsSfx(source(10, 0xB0)));
        registry.apply(raw(sample(5, 0, "raw")));
        registry.apply(start(sample(50, 1, "late")));
        registry.apply(start(sample(40, 1, "early")));

        assertEquals(List.of(20L, 10L, 5L, 40L, 50L), orderedIds(registry));
    }

    @Test
    void thirtySecondSampleSfxIsAdmittedAndThirtyThirdEqualPriorityIsRejected() {
        List<String> warnings = new ArrayList<>();
        AudioVoiceRegistry registry = registry(new RecordingInstantiation(), warnings);
        for (int index = 0; index < 32; index++) {
            registry.apply(start(sample(index, 4, "sample-" + index)));
        }
        SampleBackedVoice rejected = sample(32, 4, "rejected");

        registry.apply(start(rejected));
        registry.apply(start(rejected));

        assertEquals(32, registry.orderedVoiceCount());
        assertFalse(orderedIds(registry).contains(32L));
        assertEquals(1, warnings.size(), "a rejected voice id warns exactly once");
    }

    @Test
    void blockedSampleStartWarnsWithoutResolvingItsAsset() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        List<String> warnings = new ArrayList<>();
        AudioVoiceRegistry registry = registry(instantiation, warnings);
        registry.setSfxBlocked(true);
        instantiation.failingPcmAsset = "blocked";

        registry.apply(start(sample(40, 4, "blocked")));

        assertEquals(0, instantiation.pcmResolveCount);
        assertEquals(List.of("sample SFX blocked at presentation boundary"),
                warnings);
        assertEquals(0, registry.orderedVoiceCount());
    }

    @Test
    void fullSampleBankRejectsEqualAndLowerPriorityWithoutResolvingAssets() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        List<String> warnings = new ArrayList<>();
        AudioVoiceRegistry registry = registry(instantiation, warnings);
        for (int index = 0; index < 32; index++) {
            registry.apply(start(sample(index, 4, "sample-" + index)));
        }
        instantiation.pcmResolveCount = 0;
        instantiation.resolvedPcmAssets.clear();
        instantiation.failingPcmAsset = "equal";
        AudioPresentationCommandQueue queue = new AudioPresentationCommandQueue();
        queue.submit(start(sample(100, 4, "equal")), () -> true,
                registry::apply);
        queue.submit(start(sample(101, 3, "lower")), () -> true,
                registry::apply);
        queue.submit(new ToggleMute(ChannelType.FM, 3), () -> true,
                registry::apply);

        queue.applyPending(registry::apply);

        assertEquals(0, instantiation.pcmResolveCount);
        assertTrue(instantiation.resolvedPcmAssets.isEmpty());
        assertEquals(List.of(
                "sample SFX capacity rejected voice 100",
                "sample SFX capacity rejected voice 101"), warnings);
        assertEquals(32, registry.orderedVoiceCount());
        assertFalse(orderedIds(registry).contains(100L));
        assertFalse(orderedIds(registry).contains(101L));
        assertEquals(0, queue.size());
        assertEquals(1 << 3, registry.snapshot().fmMuteMask(),
                "rejected starts cannot stop later queued commands");
    }

    @Test
    void fullSampleBankResolvesAcceptedHigherPriorityExactlyOnce() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        for (int index = 0; index < 32; index++) {
            registry.apply(start(sample(index, 4, "sample-" + index)));
        }
        instantiation.pcmResolveCount = 0;
        instantiation.resolvedPcmAssets.clear();

        registry.apply(start(sample(100, 5, "higher")));

        assertEquals(1, instantiation.pcmResolveCount);
        assertEquals(List.of("higher"), instantiation.resolvedPcmAssets);
        assertEquals(32, registry.orderedVoiceCount());
        assertFalse(orderedIds(registry).contains(0L),
                "oldest strictly lower-priority voice is replaced");
        assertTrue(orderedIds(registry).contains(100L));
    }

    @Test
    void failedAcceptedMaterializationPreservesBankAndQueueContinuation() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        for (int index = 0; index < 32; index++) {
            registry.apply(start(sample(index, 4, "sample-" + index)));
        }
        List<Long> originalIds = orderedIds(registry);
        PresentationVoice oldest = registry.orderedVoiceAt(0);
        instantiation.pcmResolveCount = 0;
        instantiation.resolvedPcmAssets.clear();
        instantiation.failingPcmAsset = "failing";
        AudioPresentationCommandQueue queue = new AudioPresentationCommandQueue();
        queue.submit(start(sample(100, 5, "failing")), () -> true,
                registry::apply);
        queue.submit(start(sample(101, 6, "continuation")), () -> true,
                registry::apply);

        assertThrows(IllegalStateException.class,
                () -> queue.applyPending(registry::apply));

        assertEquals(originalIds, orderedIds(registry));
        assertFalse(oldest.isComplete(),
                "failed materialization cannot stop the replacement candidate");
        assertEquals(1, queue.size(),
                "commands after the failed start remain pending");
        assertEquals(List.of("failing"), instantiation.resolvedPcmAssets);

        instantiation.failingPcmAsset = null;
        queue.applyPending(registry::apply);

        assertEquals(0, queue.size());
        assertFalse(orderedIds(registry).contains(0L));
        assertFalse(orderedIds(registry).contains(100L));
        assertTrue(orderedIds(registry).contains(101L));
        assertEquals(List.of("failing", "continuation"),
                instantiation.resolvedPcmAssets);
    }

    @Test
    void higherPrioritySampleReplacesOnlyOldestStrictlyLowerPrioritySample() {
        AudioVoiceRegistry registry = registry(new RecordingInstantiation(), new ArrayList<>());
        for (int index = 0; index < 32; index++) {
            int priority = index == 0 ? 2 : index <= 2 ? 1 : 7;
            registry.apply(start(sample(index, priority, "sample-" + index)));
        }

        registry.apply(start(sample(100, 3, "replacement")));

        assertTrue(orderedIds(registry).contains(0L));
        assertFalse(orderedIds(registry).contains(1L),
                "oldest voice at the lowest eligible priority is replaced");
        assertTrue(orderedIds(registry).contains(100L));
        assertEquals(32, registry.orderedVoiceCount());
    }

    @Test
    void sampleOverflowCannotEvictMusicRawPcmOrSmpsComposite() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        registry.apply(new ReplaceMusic(music(200, 0x81, "music")));
        registry.apply(new AddSmpsSfx(source(201, 0xB0)));
        registry.apply(raw(sample(202, 0, "raw")));
        for (int index = 0; index < 32; index++) {
            registry.apply(start(sample(index, 1, "sample-" + index)));
        }

        registry.apply(start(sample(300, 100, "replacement")));

        assertTrue(orderedIds(registry).containsAll(List.of(200L, 201L, 202L)));
        assertEquals(35, registry.orderedVoiceCount());
    }

    @Test
    void rawPcmReplacementStopsThePriorRawPcmVoice() {
        AudioVoiceRegistry registry = registry(new RecordingInstantiation(), new ArrayList<>());
        SampleBackedVoice first = sample(1, 0, "raw-1");
        SampleBackedVoice second = sample(2, 0, "raw-2");
        registry.apply(raw(first));
        PresentationVoice ownedFirst = registry.orderedVoiceAt(0);

        registry.apply(raw(second));

        assertNotSame(first, ownedFirst);
        assertTrue(ownedFirst.isComplete());
        assertFalse(first.isComplete(),
                "command submitter retains no mutable registry ownership");
        assertEquals(List.of(2L), orderedIds(registry));
    }

    @Test
    void stopAllSfxPreservesMusic() {
        AudioVoiceRegistry registry = registry(new RecordingInstantiation(), new ArrayList<>());
        MusicVoiceEntry music = music(50, 0x81, "music");
        registry.apply(new ReplaceMusic(music));
        registry.apply(raw(sample(51, 0, "raw")));
        registry.apply(start(sample(52, 1, "sample")));
        registry.apply(new AddSmpsSfx(source(53, 0xB0)));

        registry.apply(new StopAllSfx());

        assertEquals(List.of(50L), orderedIds(registry));
        assertEquals(50, registry.orderedVoiceAt(0).voiceId());
    }

    @Test
    void sixtyFourCompletionsUseDeferredSlots() {
        AudioVoiceRegistry registry =
                fullRegistry(new RecordingInstantiation());

        registry.beginRendering();
        int realCompletions = stopAndDeferEveryAdmittedVoice(registry);
        for (int notification = realCompletions; notification < 64; notification++) {
            registry.deferRemoval(1_000 + notification);
        }

        assertEquals(35, realCompletions,
                "all dedicated slots and every sample slot participate");
        assertEquals(35, registry.orderedVoiceCount(),
                "render traversal cannot mutate storage");
        assertFalse(registry.completionSweepRequired());
        registry.endRendering();
        assertEquals(0, registry.orderedVoiceCount());
        assertFalse(registry.completionSweepRequired());
        assertEquals(0, registry.completionSweepCount());
    }

    @Test
    void sixtyFiveCompletionsCollapseIntoOneDeterministicSweep() {
        AudioVoiceRegistry registry =
                fullRegistry(new RecordingInstantiation());

        registry.beginRendering();
        int realCompletions = stopAndDeferEveryAdmittedVoice(registry);
        for (int notification = realCompletions; notification < 65; notification++) {
            registry.deferRemoval(1_000 + notification);
        }

        assertEquals(35, realCompletions,
                "all dedicated slots and every sample slot participate");
        assertTrue(registry.completionSweepRequired());
        registry.endRendering();
        assertEquals(0, registry.orderedVoiceCount());
        assertFalse(registry.completionSweepRequired());
        assertEquals(1, registry.completionSweepCount());
    }

    @Test
    void throwingVoiceIsWarnedAndRemovedAtFrameBoundary() {
        List<String> warnings = new ArrayList<>();
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, warnings);
        SmpsCompositeVoice throwing = new SmpsCompositeVoice(
                80, 0, 0x81, AudioSourceDescriptor.baseMusic(0x81),
                MAX_STEREO_FRAMES, new ThrowingDriver());
        instantiation.enqueueMusicDriver(new ThrowingDriver());
        registry.apply(new ReplaceMusic(MusicVoiceEntry.fromVoice(
                0x81, AudioSourceDescriptor.baseMusic(0x81), throwing)));
        AudioPresentationMixer mixer =
                new AudioPresentationMixer(MAX_STEREO_FRAMES, registry::onVoiceFailure);

        registry.beginRendering();
        mixer.mix(registry, 1);
        assertEquals(1, registry.orderedVoiceCount());
        registry.endRendering();

        assertEquals(0, registry.orderedVoiceCount());
        assertEquals(1, warnings.size());
    }

    @Test
    void crossThreadRemovalAndFailureCallbacksCannotMutateActiveTraversal()
            throws InterruptedException {
        List<String> warnings = new ArrayList<>();
        AudioVoiceRegistry registry =
                registry(new RecordingInstantiation(), warnings);
        registry.apply(start(longSample(7, 1, "sample")));
        PresentationVoice voice = registry.orderedVoiceAt(0);
        AtomicReference<Throwable> removalFailure = new AtomicReference<>();
        AtomicReference<Throwable> voiceFailure = new AtomicReference<>();

        registry.beginRendering();
        Thread intruder = new Thread(() -> {
            try {
                registry.deferRemoval(voice.voiceId());
            } catch (Throwable failure) {
                removalFailure.set(failure);
            }
            try {
                registry.onVoiceFailure(voice);
            } catch (Throwable failure) {
                voiceFailure.set(failure);
            }
        });
        intruder.start();
        intruder.join();

        assertTrue(removalFailure.get() instanceof IllegalStateException);
        assertTrue(voiceFailure.get() instanceof IllegalStateException);
        assertTrue(warnings.isEmpty(), "rejected callback cannot emit a warning");
        assertEquals(List.of(7L), orderedIds(registry));
        assertFalse(registry.completionSweepRequired());

        registry.endRendering();
        assertEquals(List.of(7L), orderedIds(registry),
                "rejected callbacks cannot leave deferred mutations behind");
    }

    @Test
    void snapshotRestorePreservesStructureAndDurableCursors() {
        AudioVoiceRegistry original = registry(new RecordingInstantiation(), new ArrayList<>());
        original.apply(new ReplaceMusic(music(1, 0x81, "music")));
        original.apply(raw(longSample(2, 2, "raw")));
        original.apply(start(longSample(3, 3, "sample")));
        mixFrames(original, 3);
        AudioPresentationSnapshot snapshot = original.snapshot();
        List<short[]> expected = mixPackets(original, 10);

        AudioVoiceRegistry restored = registry(new RecordingInstantiation(), new ArrayList<>());
        restored.restore(snapshot, new FixtureResolver());
        List<short[]> actual = mixPackets(restored, 10);

        assertEquals(List.of(1L, 2L, 3L), orderedIds(restored));
        for (int packet = 0; packet < 10; packet++) {
            assertArrayEquals(expected.get(packet), actual.get(packet));
        }
    }

    @Test
    void snapshotRestoreRecreatesNonEmptySmpsDriverNextPacketsExactly() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry original = registry(instantiation, new ArrayList<>());
        SmpsDriver driver = new SmpsDriver();
        AudioTestFixtures.StubSmpsData data =
                new AudioTestFixtures.StubSmpsData("music");
        data.setId(0x81);
        driver.addSequencer(new SmpsSequencer(
                data, dacData(), driver, AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder().build()), false);
        primeSynth(driver);
        instantiation.enqueueMusicDriver(driver);
        original.apply(new ReplaceMusic(MusicVoiceEntry.fromVoice(
                0x81, AudioSourceDescriptor.baseMusic(0x81),
                composite(70, 0x81, driver))));

        AudioPresentationSnapshot snapshot = original.snapshot();
        PresentationVoiceSnapshot.Smps smpsSnapshot = snapshot.voices().stream()
                .filter(PresentationVoiceSnapshot.Smps.class::isInstance)
                .map(PresentationVoiceSnapshot.Smps.class::cast)
                .findFirst()
                .orElseThrow();
        List<short[]> expected = mixPackets(original, 10);

        AudioVoiceRegistry restored =
                registry(new RecordingInstantiation(), new ArrayList<>());
        restored.restore(snapshot, new FixtureResolver());
        List<short[]> actual = mixPackets(restored, 10);

        assertFalse(smpsSnapshot.driver().sequencers().isEmpty(),
                "registry snapshot must preserve a live SMPS sequencer");
        assertTrue(expected.stream()
                .flatMapToInt(packet -> {
                    int[] samples = new int[packet.length];
                    for (int index = 0; index < packet.length; index++) {
                        samples[index] = packet[index];
                    }
                    return Arrays.stream(samples);
                })
                .anyMatch(sample -> sample != 0),
                "fixture must exercise audible driver state");
        for (int packet = 0; packet < expected.size(); packet++) {
            assertArrayEquals(expected.get(packet), actual.get(packet));
        }
    }

    @Test
    void restoreIntoEmptyRegistryRecreatesEveryDedicatedAndSampleSlot() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry original = registry(instantiation, new ArrayList<>());
        SmpsCompositeVoice music = composite(10, 0x81, new SmpsDriver());
        original.apply(new ReplaceMusic(MusicVoiceEntry.fromVoice(
                0x81, AudioSourceDescriptor.baseMusic(0x81), music)));
        original.apply(new PushMusicOverride(music(11, 0x82, "override")));
        original.apply(new AddSmpsSfx(source(12, 0xB0)));
        original.apply(raw(longSample(13, 0, "raw")));
        original.apply(start(longSample(14, 1, "sample")));
        AudioPresentationSnapshot snapshot = original.snapshot();
        FixtureResolver resolver = new FixtureResolver();

        AudioVoiceRegistry restored = registry(new RecordingInstantiation(), new ArrayList<>());
        restored.restore(snapshot, resolver);

        assertEquals(List.of(11L, 12L, 13L, 14L), orderedIds(restored));
        assertEquals(1, restored.snapshot().overrideStack().size());
        assertEquals(2, resolver.recreatedSmps);
        assertEquals(MAX_STEREO_FRAMES, resolver.lastMaxStereoFrames);
    }

    @Test
    void snapshotIncludesMusicIdentityDescriptorMuteSoloAndOverrideFlags() {
        AudioVoiceRegistry registry = registry(new RecordingInstantiation(), new ArrayList<>());
        registry.apply(new ReplaceMusic(music(1, 0x81, "base")));
        registry.apply(new PushMusicOverride(music(2, 0x82, "override")));
        registry.apply(new ToggleMute(ChannelType.FM, 2));
        registry.apply(new ToggleSolo(ChannelType.PSG, 1));
        registry.apply(new SetSpeedShoes(true));
        registry.apply(new SetSpeedMultiplier(8));
        registry.setSfxBlocked(true);
        registry.setPendingRestore(true);

        AudioPresentationSnapshot snapshot = registry.snapshot();

        assertEquals(0x82, snapshot.activeMusic().musicId());
        assertEquals(AudioSourceDescriptor.baseMusic(0x82),
                snapshot.activeMusic().sourceDescriptor());
        assertEquals(2, snapshot.activeMusic().voiceId());
        assertEquals(0x81, snapshot.overrideStack().get(0).musicId());
        assertEquals(1 << 2, snapshot.fmMuteMask());
        assertEquals(1 << 1, snapshot.psgSoloMask());
        assertTrue(snapshot.sfxBlocked());
        assertTrue(snapshot.pendingRestore());
        assertTrue(snapshot.speedShoesEnabled());
        assertEquals(8, snapshot.speedMultiplier());
    }

    @Test
    void nestedFallbackWavOverridesEndByMusicIdAndRestoreTheirOwnSlotMetadata() {
        AudioVoiceRegistry registry = registry(new RecordingInstantiation(), new ArrayList<>());
        registry.apply(new ReplaceMusic(fallbackMusic(1, 10, "base")));
        registry.apply(new PushMusicOverride(fallbackMusic(2, 20, "outer")));
        registry.apply(new PushMusicOverride(fallbackMusic(3, 30, "inner")));

        registry.apply(new EndMusicOverride(2));
        registry.apply(new RestoreMusicOverride());

        AudioPresentationSnapshot snapshot = registry.snapshot();
        assertEquals(1, snapshot.activeMusic().musicId());
        assertEquals(AudioSourceDescriptor.fallbackMusic(1),
                snapshot.activeMusic().sourceDescriptor());
        PresentationVoiceSnapshot.Sample active =
                sampleSnapshot(snapshot, snapshot.activeMusic().voiceId());
        assertEquals(1, active.musicId());
        assertEquals(AudioSourceDescriptor.fallbackMusic(1), active.sourceDescriptor());
        assertTrue(snapshot.overrideStack().isEmpty());
    }

    @Test
    void sameOverrideRetriggerDoesNotPushItselfAndEndRestoresBaseExactly() {
        AudioVoiceRegistry registry = registry(new RecordingInstantiation(), new ArrayList<>());
        registry.apply(new ReplaceMusic(fallbackMusic(1, 10, "base")));
        registry.apply(new PushMusicOverride(fallbackMusic(2, 20, "override-first")));
        registry.apply(new PushMusicOverride(fallbackMusic(2, 21, "override-retrigger")));

        registry.apply(new EndMusicOverride(2));

        AudioPresentationSnapshot snapshot = registry.snapshot();
        assertEquals(1, snapshot.activeMusic().musicId());
        assertEquals(10, snapshot.activeMusic().voiceId());
        assertEquals(AudioSourceDescriptor.fallbackMusic(1),
                snapshot.activeMusic().sourceDescriptor());
        assertTrue(snapshot.overrideStack().isEmpty());
    }

    @Test
    void removedRawPcmVoiceRecreatesFromItsRegisteredImmutableAsset() {
        AudioVoiceRegistry registry = registry(new RecordingInstantiation(), new ArrayList<>());
        registry.apply(raw(longSample(9, 0, "registered-raw")));
        AudioPresentationSnapshot snapshot = registry.snapshot();
        registry.apply(new StopRawPcm());
        FixtureResolver resolver = new FixtureResolver();

        AudioVoiceRegistry restored = registry(new RecordingInstantiation(), new ArrayList<>());
        restored.restore(snapshot, resolver);

        assertEquals(List.of("registered-raw"), resolver.resolvedAssets);
        assertEquals(9, restored.snapshot().rawPcmVoiceId());
    }

    @Test
    void sameBoundaryMusicReplacementThenSfxAttachesToTheReplacementDriver() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        RecordingDriver driver = new RecordingDriver(false);
        instantiation.enqueueMusicDriver(driver);
        registry.apply(new ReplaceMusic(MusicVoiceEntry.fromVoice(
                0x81, AudioSourceDescriptor.baseMusic(0x81),
                composite(1, 0x81, driver))));

        registry.apply(new AddSmpsSfx(source(2, 0xB0)));

        assertSame(driver, instantiation.lastCachedOwner);
        assertEquals(1, driver.addedSequencers);
    }

    @Test
    void sameBoundaryPushRestoreThenSfxAttachesToTheFinalRestoredDriver() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        RecordingDriver base = new RecordingDriver(false);
        RecordingDriver override = new RecordingDriver(false);
        instantiation.enqueueMusicDriver(base);
        instantiation.enqueueMusicDriver(override);
        registry.apply(new ReplaceMusic(MusicVoiceEntry.fromVoice(
                0x81, AudioSourceDescriptor.baseMusic(0x81),
                composite(1, 0x81, base))));
        registry.apply(new PushMusicOverride(MusicVoiceEntry.fromVoice(
                0x82, AudioSourceDescriptor.baseMusic(0x82),
                composite(2, 0x82, override))));

        registry.apply(new RestoreMusicOverride());
        registry.apply(new AddSmpsSfx(source(3, 0xB0)));

        assertSame(base, instantiation.lastCachedOwner);
    }

    @Test
    void cacheMissRejectsOnlyThatSfxStartDeterministicallyWithoutIo() {
        List<String> warnings = new ArrayList<>();
        RecordingInstantiation instantiation = new RecordingInstantiation();
        instantiation.cacheMiss = true;
        AudioVoiceRegistry registry = registry(instantiation, warnings);
        registry.apply(new ReplaceMusic(music(1, 0x81, "music")));
        registry.apply(start(sample(2, 1, "sample")));

        registry.apply(new AddSmpsSfx(source(3, 0xB0)));

        assertEquals(List.of(1L, 2L), orderedIds(registry));
        assertEquals(1, warnings.size());
        assertEquals(1, instantiation.cachedCalls + instantiation.standaloneCalls);
    }

    @Test
    void overlappingNoMusicSfxReuseOneStandaloneCompositeAndDriverArbitration() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());

        registry.apply(new AddSmpsSfx(source(10, 0xB0)));
        PresentationVoice standalone = registry.orderedVoiceAt(0);
        registry.apply(new AddSmpsSfx(source(11, 0xB1)));

        assertSame(standalone, registry.orderedVoiceAt(0));
        assertEquals(1, registry.orderedVoiceCount());
        assertEquals(1, instantiation.standaloneCalls);
        assertEquals(2, instantiation.cachedCalls);
        assertSame(((SmpsCompositeVoice) standalone).driver(),
                instantiation.lastCachedOwner);
    }

    @Test
    void existingMuteAndSoloMasksApplyBeforeStandaloneSfxAttachment() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        registry.apply(new ToggleMute(ChannelType.FM, 2));
        registry.apply(new ToggleSolo(ChannelType.PSG, 1));

        registry.apply(new AddSmpsSfx(source(10, 0xB0)));

        RecordingDriver driver = (RecordingDriver)
                ((SmpsCompositeVoice) registry.orderedVoiceAt(0)).driver();
        assertTrue(driver.fmMuteAtFirstAttachment[2]);
        assertTrue(driver.fmMuteAtFirstAttachment[0],
                "PSG solo mutes non-solo FM channels");
        assertFalse(driver.psgMuteAtFirstAttachment[1]);
        assertTrue(driver.psgMuteAtFirstAttachment[0]);
    }

    @Test
    void continuousRetriggerExtendsMusicOwnerWithoutCreatingSequencer() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        RecordingDriver driver = new RecordingDriver(true);
        instantiation.enqueueMusicDriver(driver);
        registry.apply(new ReplaceMusic(MusicVoiceEntry.fromVoice(
                0x81, AudioSourceDescriptor.baseMusic(0x81),
                composite(1, 0x81, driver))));

        registry.apply(new AddSmpsSfx(source(2, 0xBC)));

        assertEquals(1, driver.extensionCalls);
        assertEquals(0, instantiation.cachedCalls);
        assertEquals(0, driver.addedSequencers);
    }

    @Test
    void continuousRetriggerExtendsStandaloneOwnerWithoutCreatingSequencer() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        instantiation.standaloneExtends = true;
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        registry.apply(new AddSmpsSfx(source(10, 0xBC)));
        RecordingDriver driver =
                (RecordingDriver) ((SmpsCompositeVoice) registry.orderedVoiceAt(0)).driver();
        int cachedCallsAfterInitialStart = instantiation.cachedCalls;

        registry.apply(new AddSmpsSfx(source(11, 0xBC)));

        assertEquals(1, driver.extensionCalls);
        assertEquals(1, cachedCallsAfterInitialStart);
        assertEquals(cachedCallsAfterInitialStart, instantiation.cachedCalls,
                "continuous retrigger does not construct another sequencer");
        assertEquals(1, instantiation.standaloneCalls);
    }

    @Test
    void continuousExtensionReturnsBeforeAConfiguredFailingCacheLookup() {
        RecordingInstantiation instantiation = new RecordingInstantiation();
        instantiation.failIfCached = true;
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        instantiation.enqueueMusicDriver(new RecordingDriver(true));
        registry.apply(new ReplaceMusic(MusicVoiceEntry.fromVoice(
                0x81, AudioSourceDescriptor.baseMusic(0x81),
                composite(1, 0x81, new RecordingDriver(true)))));

        registry.apply(new AddSmpsSfx(source(2, 0xBC)));

        assertEquals(0, instantiation.cachedCalls);
    }

    @Test
    void coordFlagRuntimeStateSnapshotsRestoresAndResetsWithRegistryLifecycle() {
        SmpsCoordFlagRuntimeState state = new SmpsCoordFlagRuntimeState();
        SmpsCoordFlagHandlerOwner owner = new SmpsCoordFlagHandlerOwner(state);
        AtomicInteger creations = new AtomicInteger();
        owner.register("s3k", shared -> {
            creations.incrementAndGet();
            return noOpHandler();
        });
        CoordFlagHandler handler = owner.handlerFor("s3k");
        state.setSpindashRevCounter(7);
        AudioVoiceRegistry registry =
                new AudioVoiceRegistry(new RecordingInstantiation(), owner, ignored -> {
                });
        AudioPresentationSnapshot snapshot = registry.snapshot();
        state.setSpindashRevCounter(99);

        registry.restore(snapshot, new FixtureResolver());

        assertEquals(7, state.spindashRevCounter());
        assertSame(handler, owner.handlerFor("s3k"));
        assertEquals(1, creations.get());
        registry.apply(new HardReset());
        assertEquals(0, state.spindashRevCounter());
        assertSame(handler, owner.handlerFor("s3k"),
                "hard reset zeros state without discarding session handler identity");
    }

    @Test
    void registryExposesOnlyIndexedPreallocatedVoiceStorageNotAMixBypass() {
        assertFalse(Arrays.stream(AudioVoiceRegistry.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch("mixInto"::equals));
        for (Field field : AudioVoiceRegistry.class.getDeclaredFields()) {
            assertFalse(Collection.class.isAssignableFrom(field.getType()), field.getName());
            assertFalse(Map.class.isAssignableFrom(field.getType()), field.getName());
        }
        assertTrue(Arrays.stream(AudioVoiceRegistry.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == SampleBackedVoice[].class));
        assertTrue(Arrays.stream(AudioVoiceRegistry.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == PresentationVoice[].class));
        assertTrue(Arrays.stream(AudioVoiceRegistry.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == long[].class));
    }

    private static AudioVoiceRegistry registry(RecordingInstantiation instantiation,
                                               List<String> warnings) {
        return new AudioVoiceRegistry(instantiation, instantiation,
                new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()),
                warnings::add);
    }

    private static AudioVoiceRegistry fullRegistry(
            RecordingInstantiation instantiation) {
        AudioVoiceRegistry registry = registry(instantiation, new ArrayList<>());
        registry.apply(new ReplaceMusic(music(100, 0x81, "music")));
        registry.apply(new AddSmpsSfx(source(101, 0xB0)));
        registry.apply(raw(longSample(102, 0, "raw")));
        for (int index = 0; index < 32; index++) {
            registry.apply(start(longSample(index, 1, "sample-" + index)));
        }
        return registry;
    }

    private static int stopAndDeferEveryAdmittedVoice(
            AudioVoiceRegistry registry) {
        int voiceCount = registry.orderedVoiceCount();
        for (int index = 0; index < voiceCount; index++) {
            PresentationVoice voice = registry.orderedVoiceAt(index);
            voice.stop();
            registry.deferRemoval(voice.voiceId());
        }
        return voiceCount;
    }

    private static MusicVoiceEntry music(long voiceId, int musicId, String asset) {
        return MusicVoiceEntry.fromVoice(
                musicId, AudioSourceDescriptor.baseMusic(musicId),
                SampleBackedVoice.loopingMusic(voiceId,
                        pcm(asset, 100, 200, 300, 400), OUTPUT_RATE, 1.0f));
    }

    private static MusicVoiceEntry fallbackMusic(int musicId, long voiceId,
                                                 String asset) {
        return MusicVoiceEntry.fromVoice(
                musicId, AudioSourceDescriptor.fallbackMusic(musicId),
                SampleBackedVoice.loopingMusic(voiceId,
                        pcm(asset, 100, 200), OUTPUT_RATE, 1.0f));
    }

    private static SampleBackedVoice sample(long voiceId, int priority, String asset) {
        return SampleBackedVoice.oneShot(voiceId, priority, pcm(asset, 100),
                OUTPUT_RATE, 1.0f, 1.0f);
    }

    private static SampleBackedVoice longSample(long voiceId, int priority,
                                                String asset) {
        return SampleBackedVoice.oneShot(voiceId, priority,
                pcm(asset, 100, 200, 300, 400, 500, 600, 700, 800,
                        900, 1_000, 1_100, 1_200, 1_300, 1_400, 1_500, 1_600),
                OUTPUT_RATE, 1.0f, 1.0f);
    }

    private static StartSampleSfx start(SampleBackedVoice voice) {
        return StartSampleSfx.fromVoice(voice);
    }

    private static ReplaceRawPcm raw(SampleBackedVoice voice) {
        return ReplaceRawPcm.fromVoice(voice);
    }

    private static DecodedPcm pcm(String asset, int... samples) {
        short[] converted = new short[samples.length];
        for (int index = 0; index < samples.length; index++) {
            converted[index] = (short) samples[index];
        }
        return new DecodedPcm(asset, 1, OUTPUT_RATE, converted);
    }

    private static DacData dacData() {
        return new DacData(Map.of(1, new byte[] {0, 24, 64, 127}),
                Map.of(0x81, new DacData.DacEntry(1, 4)), 295);
    }

    private static void primeSynth(SmpsDriver driver) {
        driver.setDacData(dacData());
        driver.setDacInterpolate(true);
        driver.writeFm(driver, 0, 0x22, 0x0B);
        driver.writeFm(driver, 0, 0x2B, 0x80);
        driver.setInstrument(driver, 0, new byte[] {
                0x32, 0x71, 0x0D, 0x33, 0x01, 0x5F, 0x5F, 0x5F, 0x5F,
                0x14, 0x0E, 0x0E, 0x0E, 0x08, 0x08, 0x08, 0x08,
                0x0F, 0x0F, 0x0F, 0x0F, 0x1B, 0x16, 0x1F, 0x00
        });
        driver.writeFm(driver, 0, 0xA4, 0x22);
        driver.writeFm(driver, 0, 0xA0, 0x69);
        driver.writeFm(driver, 0, 0xB4, 0xC7);
        driver.writeFm(driver, 0, 0x28, 0xF0);
        driver.playDac(driver, 0x81);
        driver.writePsg(driver, 0x84);
        driver.writePsg(driver, 0x12);
        driver.writePsg(driver, 0x92);
    }

    private static SmpsCompositeVoice composite(long voiceId, int musicId,
                                                SmpsDriver driver) {
        return new SmpsCompositeVoice(voiceId, 0, musicId,
                AudioSourceDescriptor.baseMusic(musicId),
                MAX_STEREO_FRAMES, driver);
    }

    private static ResolvedSmpsSfxSource source(long standaloneVoiceId, int sfxId) {
        return new ResolvedSmpsSfxSource(standaloneVoiceId,
                new SmpsAssetKey("s3k", SmpsAssetKey.Route.BASE_ID, sfxId, null),
                1 << 16, 0x70, sfxId, 1, MAX_STEREO_FRAMES);
    }

    private static List<Long> orderedIds(AudioVoiceRegistry registry) {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < registry.orderedVoiceCount(); index++) {
            ids.add(registry.orderedVoiceAt(index).voiceId());
        }
        return ids;
    }

    private static void mixFrames(AudioVoiceRegistry registry, int frames) {
        AudioPresentationMixer mixer = new AudioPresentationMixer(MAX_STEREO_FRAMES);
        mixer.mix(registry, frames);
    }

    private static List<short[]> mixPackets(AudioVoiceRegistry registry, int count) {
        AudioPresentationMixer mixer = new AudioPresentationMixer(MAX_STEREO_FRAMES);
        List<short[]> packets = new ArrayList<>();
        for (int packet = 0; packet < count; packet++) {
            packets.add(Arrays.copyOf(mixer.mix(registry, 1), 2));
        }
        return packets;
    }

    private static PresentationVoiceSnapshot.Sample sampleSnapshot(
            AudioPresentationSnapshot snapshot, long voiceId) {
        return snapshot.voices().stream()
                .filter(PresentationVoiceSnapshot.Sample.class::isInstance)
                .map(PresentationVoiceSnapshot.Sample.class::cast)
                .filter(voice -> voice.voiceId() == voiceId)
                .findFirst()
                .orElseThrow();
    }

    private static CoordFlagHandler noOpHandler() {
        return new CoordFlagHandler() {
            @Override
            public boolean handleFlag(CoordFlagContext ctx, SmpsSequencer.Track track,
                                      int command) {
                return false;
            }

            @Override
            public int flagParamLength(int command) {
                return -1;
            }
        };
    }

    private static final class RecordingInstantiation
            implements SmpsSfxInstantiation, AudioPresentationDependencyResolver {
        private final List<SmpsDriver> musicDrivers = new ArrayList<>();
        private int cachedCalls;
        private int standaloneCalls;
        private int musicDriverIndex;
        private final List<String> resolvedPcmAssets = new ArrayList<>();
        private int pcmResolveCount;
        private String failingPcmAsset;
        private SmpsDriver lastCachedOwner;
        private boolean cacheMiss;
        private boolean failIfCached;
        private boolean standaloneExtends;

        private void enqueueMusicDriver(SmpsDriver driver) {
            musicDrivers.add(driver);
        }

        @Override
        public DecodedPcm resolvePcm(String assetId) {
            pcmResolveCount++;
            resolvedPcmAssets.add(assetId);
            if (assetId.equals(failingPcmAsset)) {
                throw new IllegalStateException(
                        "fixture PCM resolution failure for " + assetId);
            }
            if ("music".equals(assetId)) {
                return pcm(assetId, 100, 200, 300, 400);
            }
            if ("raw".equals(assetId) || "sample".equals(assetId)
                    || "registered-raw".equals(assetId)) {
                return pcm(assetId, 100, 200, 300, 400, 500, 600, 700, 800,
                        900, 1_000, 1_100, 1_200, 1_300, 1_400, 1_500, 1_600);
            }
            return pcm(assetId, 100, 200, 300, 400);
        }

        @Override
        public SmpsCompositeVoice recreateSmps(
                PresentationVoiceSnapshot.Smps snapshot) {
            SmpsCompositeVoice voice = new SmpsCompositeVoice(
                    snapshot.voiceId(), snapshot.priority(), snapshot.musicId(),
                    snapshot.sourceDescriptor(), snapshot.maxStereoFrames(),
                    new SmpsDriver());
            voice.restore(snapshot, SmpsDriverSnapshot.liveReferences());
            return voice;
        }

        @Override
        public SmpsCompositeVoice recreateSmps(
                AudioPresentationCommand.SmpsVoiceDescriptor descriptor) {
            SmpsDriver driver = musicDriverIndex < musicDrivers.size()
                    ? musicDrivers.get(musicDriverIndex++) : new SmpsDriver();
            return new SmpsCompositeVoice(
                    descriptor.voiceId(), descriptor.priority(),
                    descriptor.musicId(), descriptor.sourceDescriptor(),
                    descriptor.maxStereoFrames(), driver);
        }

        @Override
        public SmpsSequencer instantiateCached(ResolvedSmpsSfxSource source,
                                               SmpsDriver currentOwner) {
            if (failIfCached) {
                throw new AssertionError("cache lookup must not happen");
            }
            cachedCalls++;
            lastCachedOwner = currentOwner;
            return cacheMiss ? null : sequencer(source);
        }

        @Override
        public SmpsCompositeVoice instantiateStandaloneCached(
                ResolvedSmpsSfxSource source) {
            standaloneCalls++;
            if (cacheMiss) {
                return null;
            }
            RecordingDriver driver = new RecordingDriver(standaloneExtends);
            return new SmpsCompositeVoice(source.standaloneVoiceId(), source.priority(),
                    null, null, source.maxStereoFrames(), driver);
        }
    }

    private static SmpsSequencer sequencer(ResolvedSmpsSfxSource source) {
        AudioTestFixtures.StubSmpsData data =
                new AudioTestFixtures.StubSmpsData(source.assetKey().gameId());
        data.setId(source.assetKey().sfxId());
        SmpsSequencer sequencer = new SmpsSequencer(data, AudioTestFixtures.EMPTY_DAC,
                AudioManager.getInstance(), new SmpsSequencerConfig.Builder().build());
        sequencer.setSfxPriority(source.priority());
        sequencer.setPitch(source.pitchQ16() / (float) (1 << 16));
        return sequencer;
    }

    private static final class RecordingDriver extends SmpsDriver {
        private final boolean extendsContinuous;
        private final boolean[] fmMutes = new boolean[6];
        private final boolean[] psgMutes = new boolean[4];
        private final boolean[] fmMuteAtFirstAttachment = new boolean[6];
        private final boolean[] psgMuteAtFirstAttachment = new boolean[4];
        private int extensionCalls;
        private int addedSequencers;

        private RecordingDriver(boolean extendsContinuous) {
            this.extendsContinuous = extendsContinuous;
        }

        @Override
        public boolean extendContinuousSfx(int sfxId, int trackCount) {
            extensionCalls++;
            return extendsContinuous;
        }

        @Override
        public void setFmMute(int channel, boolean mute) {
            fmMutes[channel] = mute;
            super.setFmMute(channel, mute);
        }

        @Override
        public void setPsgMute(int channel, boolean mute) {
            psgMutes[channel] = mute;
            super.setPsgMute(channel, mute);
        }

        @Override
        public void addSequencer(SmpsSequencer sequencer, boolean sfx) {
            if (addedSequencers == 0) {
                System.arraycopy(fmMutes, 0, fmMuteAtFirstAttachment, 0,
                        fmMutes.length);
                System.arraycopy(psgMutes, 0, psgMuteAtFirstAttachment, 0,
                        psgMutes.length);
            }
            addedSequencers++;
            super.addSequencer(sequencer, sfx);
        }
    }

    private static final class ThrowingDriver extends SmpsDriver {
        @Override
        public int read(short[] buffer, int length) {
            throw new IllegalStateException("fixture failure");
        }

        @Override
        public boolean isComplete() {
            return false;
        }
    }

    private static final class FixtureResolver
            implements AudioPresentationDependencyResolver {
        private final List<String> resolvedAssets = new ArrayList<>();
        private int recreatedSmps;
        private int lastMaxStereoFrames;

        @Override
        public DecodedPcm resolvePcm(String assetId) {
            resolvedAssets.add(assetId);
            return switch (assetId) {
                case "music" -> pcm(assetId, 100, 200, 300, 400);
                case "raw", "sample", "registered-raw" ->
                        pcm(assetId, 100, 200, 300, 400, 500, 600, 700, 800,
                                900, 1_000, 1_100, 1_200, 1_300, 1_400, 1_500, 1_600);
                default -> pcm(assetId, 100, 200);
            };
        }

        @Override
        public SmpsCompositeVoice recreateSmps(
                PresentationVoiceSnapshot.Smps snapshot) {
            recreatedSmps++;
            lastMaxStereoFrames = snapshot.maxStereoFrames();
            SmpsCompositeVoice voice = new SmpsCompositeVoice(
                    snapshot.voiceId(), snapshot.priority(), snapshot.musicId(),
                    snapshot.sourceDescriptor(), snapshot.maxStereoFrames(),
                    new SmpsDriver());
            voice.restore(snapshot, SmpsDriverSnapshot.liveReferences());
            return voice;
        }
    }
}
