package com.openggf.tests.trace.s3k;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioRequestObserver;
import com.openggf.audio.GameMusic;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.specialstage.S3kSpecialStageTraceData;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.TraceFixtureRoot;
import com.openggf.tools.audio.playback.AudioPlaybackTraceEvent;
import com.openggf.tools.audio.playback.AudioPlaybackTraceSnapshot;
import com.openggf.tools.audio.playback.BoundedAudioPlaybackTrace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(SingletonResetExtension.class)
class TestS3kSpecialStageAudioPlaybackTrace {
    private static final Path TRACE = Path.of("src/test/resources/traces/s3k/runs/"
            + "s3k-sonic-tails-complete-emeralds/ss");

    @Test
    void realSpecialStageReplayExposesEveryBlueSphereFm5Restart()
            throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        Path traceDir = TraceFixtureRoot.resolve(TRACE);
        S3kSpecialStageTraceData traceData =
                S3kSpecialStageTraceData.load(traceDir);
        Rom rom = new Rom();
        rom.open(romFile.getAbsolutePath());
        TestEnvironment.configureRomFixture(rom);
        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();
        S3kSpecialStageReplayHarness harness = new S3kSpecialStageReplayHarness(
                AbstractS3kSpecialStageTraceReplayTest.resolveSourceBk2(
                        traceDir, traceData.metadata().sourceBk2()),
                traceData.metadata().bk2FrameOffset(),
                traceData.metadata().specialStageIndex());

        AudioManager audio = AudioManager.getInstance();
        var profile = GameServices.module().getAudioProfile();
        audio.setAudioProfile(profile);
        audio.setRom(rom);
        audio.setSoundMap(profile.getSoundMap());
        audio.resetRingSound();
        BoundedAudioPlaybackTrace audioTrace =
                new BoundedAudioPlaybackTrace(
                        200_000, 500_000, 1 << 4, 500_000);
        audio.setChipWriteObserver(audioTrace);
        audio.setRequestObserver(audioTrace);
        assertTrue(audio.playMusic(GameMusic.SPECIAL_STAGE));
        try (LiveCaptureAudioHandle capture =
                     AudioManagerTestDiagnostics.attachPresentationCapture(
                             audio, audio.presentationFrameRate())) {
            short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
            for (int frame = 0; frame < 500; frame++) {
                if (traceData.getFrame(frame).lag()) {
                    continue;
                }
                audioTrace.mark("frame-" + frame);
                harness.stepFrame(frame);
                audio.presentFrame(PresentationMode.FORWARD);
                int stereoFrames = capture.drainPresentationFrame(packet);
                audioTrace.recordPcm(packet, stereoFrames);
            }
        } finally {
            audio.setChipWriteObserver(null);
            audio.setRequestObserver(null);
            audio.resetState();
        }

        AudioPlaybackTraceSnapshot snapshot = audioTrace.snapshot();
        List<RequestRestart> restarts = blueSphereRestarts(snapshot);
        assertTrue(restarts.size() >= 4,
                () -> "expected repeated Blue Sphere requests, got " + restarts);
        assertTrue(restarts.stream().allMatch(value ->
                        value.attenuation().size() == 4),
                () -> "missing FM5 key-on evidence: " + restarts
                        + " events=" + snapshot.events().stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                event -> event.getClass().getSimpleName(),
                                java.util.stream.Collectors.counting())));
        assertTrue(restarts.stream().allMatch(value ->
                        value.keyOnFrame() > value.requestFrame()),
                () -> "S3K must defer a newly admitted SFX until the next "
                        + "driver update: " + restarts);
        assertTrue(restarts.stream().allMatch(RequestRestart::hasAdmissionKeyOff),
                () -> "missing immediate FM5 admission key-off: " + restarts);
        assertTrue(restarts.stream().allMatch(value ->
                        value.ssgEgClears().equals(Set.of(
                                0x91, 0x95, 0x99, 0x9D))),
                () -> "missing S3K FM5 admission SSG-EG clears: " + restarts);
        assertTrue(restarts.stream().allMatch(value ->
                        value.maxReleaseWrites().equals(Set.of(
                                0x81, 0x85, 0x89, 0x8D))),
                () -> "missing cfSetVoice maximum-release writes: " + restarts);
    }

    private static List<RequestRestart> blueSphereRestarts(
            AudioPlaybackTraceSnapshot snapshot) {
        List<AudioPlaybackTraceEvent> events = snapshot.events();
        java.util.ArrayList<RequestRestart> result = new java.util.ArrayList<>();
        int frame = -1;
        for (int index = 0; index < events.size(); index++) {
            AudioPlaybackTraceEvent event = events.get(index);
            if (event instanceof AudioPlaybackTraceEvent.Marker marker
                    && marker.name().startsWith("frame-")) {
                frame = Integer.parseInt(marker.name().substring(6));
            }
            if (!(event instanceof AudioPlaybackTraceEvent.AudioRequest request)
                    || request.requestClass()
                    != AudioRequestObserver.RequestClass.SFX
                    || request.rawSoundId() != 0x65) {
                continue;
            }
            int keyOnFrame = -1;
            int eventFrame = frame;
            boolean admissionKeyOff = false;
            Set<Integer> ssgEgClears = new HashSet<>();
            Set<Integer> maxReleaseWrites = new HashSet<>();
            java.util.ArrayList<Integer> attenuation = new java.util.ArrayList<>();
            for (int next = index + 1; next < events.size(); next++) {
                AudioPlaybackTraceEvent candidate = events.get(next);
                if (candidate instanceof AudioPlaybackTraceEvent.Marker marker
                        && marker.name().startsWith("frame-")) {
                    eventFrame = Integer.parseInt(marker.name().substring(6));
                    continue;
                }
                if (candidate instanceof AudioPlaybackTraceEvent.AudioRequest nextRequest
                        && nextRequest.requestClass()
                        == AudioRequestObserver.RequestClass.SFX
                        && nextRequest.rawSoundId() == 0x65) {
                    break;
                }
                if (candidate instanceof AudioPlaybackTraceEvent.Ym2612Write write) {
                    if (write.port() == 0 && write.register() == 0x28
                            && write.value() == 0x05) {
                        admissionKeyOff = true;
                    } else if (write.port() == 1 && write.value() == 0
                            && Set.of(0x91, 0x95, 0x99, 0x9D)
                                    .contains(write.register())) {
                        ssgEgClears.add(write.register());
                    } else if (write.port() == 1 && write.value() == 0xFF
                            && Set.of(0x81, 0x85, 0x89, 0x8D)
                                    .contains(write.register())) {
                        maxReleaseWrites.add(write.register());
                    }
                } else if (candidate instanceof AudioPlaybackTraceEvent.Ym2612KeyOn keyOn
                        && keyOn.channel() == 4) {
                    if (keyOnFrame < 0) {
                        keyOnFrame = eventFrame;
                    }
                    attenuation.add(keyOn.attenuation());
                    if (attenuation.size() == 4) {
                        break;
                    }
                }
            }
            result.add(new RequestRestart(
                    frame,
                    keyOnFrame,
                    List.copyOf(attenuation),
                    admissionKeyOff,
                    Set.copyOf(ssgEgClears),
                    Set.copyOf(maxReleaseWrites),
                    0,
                    0,
                    0.0));
        }
        List<AudioPlaybackTraceSnapshot.TimedAudioRequest> requests =
                snapshot.timedAudioRequests().stream()
                        .filter(request -> request.requestClass()
                                == AudioRequestObserver.RequestClass.SFX
                                && request.rawSoundId() == 0x65)
                        .toList();
        List<AudioPlaybackTraceSnapshot.Ym2612ChannelSample> samples =
                snapshot.ym2612ChannelSamples();
        if (requests.size() != result.size()) {
            throw new IllegalStateException(
                    "request event/timing count differs");
        }
        for (int index = 0; index < result.size(); index++) {
            int start = requests.get(index).sampleOrdinal();
            int end = Math.min(samples.size(), start + 12_800);
            if (index + 1 < requests.size()) {
                end = Math.min(end, requests.get(index + 1).sampleOrdinal());
            }
            long squares = 0;
            int peak = 0;
            for (int sample = start; sample < end; sample++) {
                int value = samples.get(sample).output();
                peak = Math.max(peak, Math.abs(value));
                squares += (long) value * value;
            }
            int count = end - start;
            RequestRestart restart = result.get(index);
            result.set(index, new RequestRestart(
                    restart.requestFrame(), restart.keyOnFrame(),
                    restart.attenuation(), restart.hasAdmissionKeyOff(),
                    restart.ssgEgClears(), restart.maxReleaseWrites(),
                    count, peak,
                    count == 0 ? 0.0 : Math.sqrt((double) squares / count)));
        }
        return List.copyOf(result);
    }

    private record RequestRestart(
            int requestFrame,
            int keyOnFrame,
            List<Integer> attenuation,
            boolean hasAdmissionKeyOff,
            Set<Integer> ssgEgClears,
            Set<Integer> maxReleaseWrites,
            int sampleCount,
            int peak,
            double rms) {
    }
}
