package com.openggf.audio;

import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.NoOpDeterministicAudioRuntime;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestAudioManagerCapturesClockSnapshot {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void streamBackedRuntimePopulatesClockSnapshot() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        DeterministicAudioRuntime runtime = new StreamBackedDeterministicAudioRuntime(
                new AudioFrameClock(120, 60), new AudioOutputFifo(120));
        audio.setDeterministicAudioRuntime(runtime);
        audio.setBackend(new NullAudioBackend());

        AudioLogicalSnapshot snap = audio.captureLogicalSnapshot();
        assertNotNull(snap.backend().clockSnapshot(),
                "StreamBacked runtime must expose its clock snapshot via the backend snapshot");
    }

    @Test
    void noOpRuntimeLeavesClockSnapshotNull() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setDeterministicAudioRuntime(NoOpDeterministicAudioRuntime.INSTANCE);
        audio.setBackend(new NullAudioBackend());

        AudioLogicalSnapshot snap = audio.captureLogicalSnapshot();
        assertNull(snap.backend().clockSnapshot(),
                "NoOp runtime returns null clock snapshot, AudioManager must propagate that");
    }
}
