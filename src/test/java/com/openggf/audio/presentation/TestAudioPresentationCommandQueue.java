package com.openggf.audio.presentation;

import com.openggf.audio.ChannelType;
import com.openggf.audio.presentation.AudioPresentationCommand.AddSmpsSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.ChangeMusicTempo;
import com.openggf.audio.presentation.AudioPresentationCommand.EndMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.FadeMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.HardReset;
import com.openggf.audio.presentation.AudioPresentationCommand.PushMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceRawPcm;
import com.openggf.audio.presentation.AudioPresentationCommand.ResetRingAlternation;
import com.openggf.audio.presentation.AudioPresentationCommand.RestoreMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.RewindBoundary;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedMultiplier;
import com.openggf.audio.presentation.AudioPresentationCommand.SetSpeedShoes;
import com.openggf.audio.presentation.AudioPresentationCommand.SetVoiceGain;
import com.openggf.audio.presentation.AudioPresentationCommand.SetVoicePitch;
import com.openggf.audio.presentation.AudioPresentationCommand.StartSampleSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopAllSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.StopMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.StopRawPcm;
import com.openggf.audio.presentation.AudioPresentationCommand.ToggleMute;
import com.openggf.audio.presentation.AudioPresentationCommand.ToggleSolo;
import com.openggf.audio.rewind.AudioCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioPresentationCommandQueue {

    @Test
    void normalCommandsCannotConsumeFinalThirtyTwoStructuralSlots() {
        AudioPresentationCommandQueue queue = new AudioPresentationCommandQueue();

        for (int index = 0; index < AudioPresentationCommandQueue.CAPACITY; index++) {
            queue.submit(new StartSampleSfx(sample(index, 1)), () -> true, ignored -> {
            });
        }

        assertEquals(AudioPresentationCommandQueue.CAPACITY
                        - AudioPresentationCommandQueue.STRUCTURAL_RESERVE,
                queue.size());
    }

    @Test
    void structuralAdmissionEvictsOldestDroppableSampleStart() {
        AudioPresentationCommandQueue queue = new AudioPresentationCommandQueue();
        for (int index = 0; index < 224; index++) {
            queue.submit(new StartSampleSfx(sample(index, 1)), () -> true, ignored -> {
            });
        }
        for (int index = 0; index < 32; index++) {
            queue.submit(new FadeMusic(index, 1), () -> true, ignored -> {
            });
        }

        queue.submit(new StopMusic(), () -> true, ignored -> {
        });
        List<AudioPresentationCommand> applied = new ArrayList<>();
        queue.applyPending(applied::add);

        assertEquals(256, applied.size());
        assertInstanceOf(StartSampleSfx.class, applied.get(0));
        assertEquals(1, ((StartSampleSfx) applied.get(0)).voice().voiceId());
        assertFalse(applied.stream()
                .filter(StartSampleSfx.class::isInstance)
                .map(StartSampleSfx.class::cast)
                .anyMatch(command -> command.voice().voiceId() == 0));
        assertInstanceOf(StopMusic.class, applied.get(255));
    }

    @Test
    void fullStructuralQueueSynchronouslyDrainsAtAssertedOwnerBoundary() {
        AudioPresentationCommandQueue queue = new AudioPresentationCommandQueue();
        List<Integer> applied = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            queue.submit(new FadeMusic(index, 1), () -> true,
                    command -> applied.add(((FadeMusic) command).steps()));
        }

        queue.submit(new FadeMusic(256, 1), () -> true,
                command -> applied.add(((FadeMusic) command).steps()));

        assertEquals(range(0, 256), applied);
        assertEquals(1, queue.size());
        queue.applyPending(command -> applied.add(((FadeMusic) command).steps()));
        assertEquals(range(0, 257), applied);
    }

    @Test
    void fullStructuralQueueRejectsSubmissionDuringRendering() {
        AtomicBoolean rendering = new AtomicBoolean();
        AudioPresentationCommandQueue queue =
                new AudioPresentationCommandQueue(rendering::get);
        for (int index = 0; index < 256; index++) {
            queue.submit(new FadeMusic(index, 1), () -> true, ignored -> {
            });
        }

        rendering.set(true);

        assertThrows(IllegalStateException.class,
                () -> queue.submit(new FadeMusic(256, 1), () -> false, ignored -> {
                }));
        assertEquals(256, queue.size());
    }

    @Test
    void sameTargetScalarCommandsCoalesceWithoutCrossingAnotherCommand() {
        AudioPresentationCommandQueue queue = new AudioPresentationCommandQueue();

        queue.submit(new SetVoiceGain(4, 10), () -> true, ignored -> {
        });
        queue.submit(new SetVoiceGain(4, 20), () -> true, ignored -> {
        });
        queue.submit(new StopAllSfx(), () -> true, ignored -> {
        });
        queue.submit(new SetVoiceGain(4, 30), () -> true, ignored -> {
        });
        queue.submit(new SetVoicePitch(4, 40), () -> true, ignored -> {
        });
        queue.submit(new SetVoiceGain(4, 50), () -> true, ignored -> {
        });

        List<AudioPresentationCommand> applied = new ArrayList<>();
        queue.applyPending(applied::add);

        assertEquals(List.of(
                new SetVoiceGain(4, 20),
                new StopAllSfx(),
                new SetVoiceGain(4, 50),
                new SetVoicePitch(4, 40)), applied);
    }

    @Test
    void moreThanBothQueueRegionsAppliesEveryStructuralCommandInOriginalOrder() {
        AudioPresentationCommandQueue queue = new AudioPresentationCommandQueue();
        List<Integer> applied = new ArrayList<>();

        for (int index = 0; index < 300; index++) {
            queue.submit(new FadeMusic(index, 1), () -> true,
                    command -> applied.add(((FadeMusic) command).steps()));
        }
        queue.applyPending(command -> applied.add(((FadeMusic) command).steps()));

        assertEquals(range(0, 300), applied);
    }

    @Test
    void renderingNeverDrainsExternalCommands() {
        AtomicBoolean rendering = new AtomicBoolean();
        AudioPresentationCommandQueue queue =
                new AudioPresentationCommandQueue(rendering::get);
        queue.submit(new StopMusic(), () -> true, ignored -> {
        });
        List<AudioPresentationCommand> applied = new ArrayList<>();

        rendering.set(true);
        assertThrows(IllegalStateException.class, () -> queue.applyPending(applied::add));

        assertTrue(applied.isEmpty());
        assertEquals(1, queue.size());
        rendering.set(false);
        queue.applyPending(applied::add);
        assertEquals(List.of(new StopMusic()), applied);
    }

    @Test
    void everyAudioCommandVariantHasOneResolvedPresentationCommand() {
        Set<String> resolvedNames = Arrays.stream(AudioPresentationCommand.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(resolvedNames.containsAll(Set.of(
                ReplaceMusic.class.getSimpleName(),
                PushMusicOverride.class.getSimpleName(),
                AddSmpsSfx.class.getSimpleName(),
                StartSampleSfx.class.getSimpleName(),
                FadeMusic.class.getSimpleName(),
                StopMusic.class.getSimpleName(),
                StopAllSfx.class.getSimpleName(),
                EndMusicOverride.class.getSimpleName(),
                RestoreMusicOverride.class.getSimpleName(),
                SetSpeedShoes.class.getSimpleName(),
                SetSpeedMultiplier.class.getSimpleName(),
                ChangeMusicTempo.class.getSimpleName(),
                ResetRingAlternation.class.getSimpleName())));
        assertEquals(11, AudioCommand.class.getPermittedSubclasses().length);
    }

    @Test
    void resolvedCommandsContainNoConsumerRunnableOrMutableClosure() {
        Set<Class<?>> expectedRecords = Set.of(
                ReplaceMusic.class, PushMusicOverride.class, RestoreMusicOverride.class,
                EndMusicOverride.class, AddSmpsSfx.class, StartSampleSfx.class,
                ReplaceRawPcm.class, StopRawPcm.class, StopMusic.class, StopAllSfx.class,
                FadeMusic.class, SetVoiceGain.class, SetVoicePitch.class,
                SetSpeedShoes.class, SetSpeedMultiplier.class, ChangeMusicTempo.class,
                ResetRingAlternation.class, ToggleMute.class, ToggleSolo.class,
                RewindBoundary.class, HardReset.class);

        assertEquals(expectedRecords,
                Set.of(AudioPresentationCommand.class.getPermittedSubclasses()));
        for (Class<?> commandType : expectedRecords) {
            assertTrue(commandType.isRecord());
            for (RecordComponent component : commandType.getRecordComponents()) {
                Class<?> type = component.getType();
                assertFalse(Runnable.class.isAssignableFrom(type), commandType.getName());
                assertFalse(Consumer.class.isAssignableFrom(type), commandType.getName());
                assertFalse(type.getName().startsWith("java.util.function."), commandType.getName());
                assertFalse(type.getSimpleName().equals("AbstractSmpsData"), commandType.getName());
                assertFalse(type.getSimpleName().equals("DacData"), commandType.getName());
                assertFalse(type.getSimpleName().equals("SmpsSequencerConfig"), commandType.getName());
                assertFalse(type.getSimpleName().equals("SmpsSequencer"), commandType.getName());
            }
        }

        assertTrue(new ToggleMute(ChannelType.FM, 0).structural());
        assertTrue(new ToggleSolo(ChannelType.PSG, 0).structural());
    }

    private static SampleBackedVoice sample(long id, int priority) {
        return SampleBackedVoice.oneShot(id, priority,
                new DecodedPcm("sample-" + id, 1, 48_000, new short[] {1}),
                48_000, 1.0f, 1.0f);
    }

    private static List<Integer> range(int startInclusive, int endExclusive) {
        List<Integer> values = new ArrayList<>();
        for (int value = startInclusive; value < endExclusive; value++) {
            values.add(value);
        }
        return values;
    }
}
