package com.openggf.audio;

import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioTimelineEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestDeterministicAudioRuntimeBoundary {
    private AudioManager audio;
    private OrderedBackend backend;
    private RecordingRuntime runtime;
    private List<String> calls;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        calls = new ArrayList<>();
        backend = new OrderedBackend(calls);
        runtime = new RecordingRuntime(calls);
        audio.setBackend(backend);
        audio.setDeterministicAudioRuntime(runtime);
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void gameplayFrameAdvanceRunsAuthoritativeRuntimeBeforePresentationDrain() {
        audio.beginGameplayAudioFrame(12);

        audio.advanceGameplayFrameAudio();
        audio.update();

        assertEquals(List.of("advance:12:NORMAL", "backend:update"), calls);
    }

    @Test
    void pausedFrameStepAdvancesOneSilentAuthoritativeFrameWithoutPollingBackend() {
        audio.beginGameplayAudioFrame(34);

        audio.advancePausedFrameStepAudio();

        assertEquals(List.of("advance:34:SILENT_STEP"), calls);
    }

    @Test
    void settingRuntimeAttachesItToCurrentBackend() {
        AttachingBackend attachingBackend = new AttachingBackend();
        audio.setBackend(attachingBackend);

        audio.setDeterministicAudioRuntime(runtime);

        assertEquals(runtime, attachingBackend.attachedRuntime);
    }

    @Test
    void settingBackendAttachesCurrentRuntime() {
        audio.setDeterministicAudioRuntime(runtime);
        AttachingBackend attachingBackend = new AttachingBackend();

        audio.setBackend(attachingBackend);

        assertEquals(runtime, attachingBackend.attachedRuntime);
    }

    @Test
    void resetStateReattachesNoOpRuntimeToExistingBackend() {
        AttachingBackend attachingBackend = new AttachingBackend();
        audio.setBackend(attachingBackend);
        audio.setDeterministicAudioRuntime(new PresentationRuntime());

        audio.resetState();

        assertEquals(false, attachingBackend.attachedRuntime.providesPresentationPcm());
    }

    @Test
    void commandConsumingRuntimeDelaysBackendSideEffectsUntilFrameAdvance() {
        CommandConsumingRuntime consumingRuntime = new CommandConsumingRuntime(calls);
        audio.setDeterministicAudioRuntime(consumingRuntime);
        audio.beginGameplayAudioFrame(15);

        audio.playSfx("JUMP");

        assertEquals(List.of("submit:15:0"), calls);

        audio.advanceGameplayFrameAudio();

        assertEquals(List.of(
                "submit:15:0",
                "consume:15:0",
                "backend:sfx:JUMP:1.0",
                "advance:15:NORMAL"),
                calls);
    }

    @Test
    void updateAdvancesConsumingRuntimeWhenNoFrameOwnerDid() {
        CommandConsumingRuntime consumingRuntime = new CommandConsumingRuntime(calls);
        audio.setDeterministicAudioRuntime(consumingRuntime);

        audio.playSfx("JUMP");
        audio.update();

        assertEquals(List.of(
                "submit:0:0",
                "consume:0:0",
                "backend:sfx:JUMP:1.0",
                "advance:0:NORMAL",
                "backend:update"),
                calls);
    }

    private static final class RecordingRuntime implements DeterministicAudioRuntime {
        private final List<String> calls;

        private RecordingRuntime(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
            calls.add("advance:" + frame + ":" + mode);
        }
    }

    private static final class OrderedBackend extends NullAudioBackend {
        private final List<String> calls;

        private OrderedBackend(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void playSfx(String sfxName, float pitch) {
            calls.add("backend:sfx:" + sfxName + ":" + pitch);
        }

        @Override
        public void update() {
            calls.add("backend:update");
        }
    }

    private static final class AttachingBackend extends NullAudioBackend {
        private DeterministicAudioRuntime attachedRuntime;

        @Override
        public void attachDeterministicAudioRuntime(DeterministicAudioRuntime runtime) {
            attachedRuntime = runtime;
        }
    }

    private static final class PresentationRuntime implements DeterministicAudioRuntime {
        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
        }

        @Override
        public boolean providesPresentationPcm() {
            return true;
        }
    }

    private static final class CommandConsumingRuntime implements DeterministicAudioRuntime {
        private final List<String> calls;
        private final List<AudioTimelineEntry> entries = new ArrayList<>();
        private Consumer<AudioCommand> commandHandler = command -> {};

        private CommandConsumingRuntime(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public boolean consumesSubmittedCommands() {
            return true;
        }

        @Override
        public void setCommandHandler(Consumer<AudioCommand> commandHandler) {
            this.commandHandler = commandHandler;
        }

        @Override
        public void submit(AudioTimelineEntry entry) {
            entries.add(entry);
            calls.add("submit:" + entry.frame() + ":" + entry.order());
        }

        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
            for (AudioTimelineEntry entry : List.copyOf(entries)) {
                if (entry.frame() == frame) {
                    calls.add("consume:" + entry.frame() + ":" + entry.order());
                    commandHandler.accept(entry.command());
                    entries.remove(entry);
                }
            }
            calls.add("advance:" + frame + ":" + mode);
        }
    }
}
