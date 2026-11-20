package com.openggf.game.soniccd.audio;

import com.openggf.GameLoop;
import com.openggf.audio.AudioRequestObserver;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCdRecordedPauseMusic {
    private enum Music {
        PRESENT(CdStagePayload.R11A, 0xD5, 0xD6),
        PAST(CdStagePayload.R11B, 0x90, 0x91),
        PAST_INVINCIBILITY(CdStagePayload.R11B, 0xD5, 0xD6),
        PAST_SPEED_SHOES(CdStagePayload.R11B, 0xD5, 0xD6);

        final CdStagePayload stage;
        final int pause;
        final int unpause;

        Music(CdStagePayload stage, int pause, int unpause) {
            this.stage = stage;
            this.pause = pause;
            this.unpause = unpause;
        }
    }

    @ParameterizedTest
    @EnumSource(Music.class)
    void recordedStartPausesAndResumesMusic(Music music) throws Exception {
        var config = GameServices.configuration();
        boolean oldAudio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
        try (var disc = CdDiscDataSource.open(CdAudioTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            GameLoop loop = boot.boot(disc, new SonicCdGameModule(),
                    SonicCdZoneRegistry.zoneIndex(music.stage), SonicCdZoneRegistry.actIndex(music.stage));
            GameServices.level().skipPendingInitialTitleCardPresentation();
            var audio = GameServices.audio();
            try (var owner = RecordedInputOwnership.acquire(loop.getInputHandler());
                 var capture = audio.beginLiveCaptureAudio(audio.presentationFrameRate())) {
                var released = new Bk2FrameInput(0, 0, 0, false, "released");
                var start = new Bk2FrameInput(0, 0, 0, true, "Start");
                sample(loop, released, null);
                present(loop, capture, 120);
                startPowerUpMusic(music);
                present(loop, capture, 200);
                assertTrue(present(loop, capture, 30), "music is audible before pressing Start");

                List<Integer> requests = new ArrayList<>();
                audio.setRequestObserver((kind, id) -> {
                    if (kind == AudioRequestObserver.RequestClass.MUSIC) requests.add(id);
                });
                try {
                    sample(loop, start, released);
                    present(loop, capture, 1); // Allows the PCM command to reach its next driver tick.
                    sample(loop, start, start);
                    boolean audibleWhilePaused = present(loop, capture, 20);
                    assertAll(
                            () -> assertTrue(GameServices.gameState().isGamePaused(), "recorded Start pauses the level"),
                            () -> assertFalse(loop.isUserPaused(), "recorded Start leaves host pause clear"),
                            () -> assertEquals(List.of(music.pause), requests, "one native pause command while Start is held"),
                            () -> assertFalse(audibleWhilePaused, "music is silent during the pause"));

                    sample(loop, released, start);
                    present(loop, capture, 1);
                    sample(loop, start, released);
                    boolean audibleOnResume = present(loop, capture, 1);
                    sample(loop, start, start);
                    boolean audibleAfterResume = present(loop, capture, 29) || audibleOnResume;
                    assertAll(
                            () -> assertFalse(GameServices.gameState().isGamePaused(), "next Start resumes the level"),
                            () -> assertEquals(List.of(music.pause, music.unpause), requests, "one matching native resume command"),
                            () -> assertTrue(audibleAfterResume, "the music resumes"));
                } finally {
                    audio.setRequestObserver(AudioRequestObserver.NONE);
                }
            }
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
        }
    }

    private static void startPowerUpMusic(Music music) {
        var services = GameServices.level().getObjectManager().getObjectServices();
        var player = services.camera().getFocusedSprite();
        switch (music) {
            case PAST_INVINCIBILITY -> {
                player.giveInvincibility();
                CdSoundRequests.powerUpMusic(services, CdSoundRequests.INVINCIBILITY);
            }
            case PAST_SPEED_SHOES -> {
                player.giveSpeedShoes();
                CdSoundRequests.powerUpMusic(services, CdSoundRequests.SPEED_SHOES);
            }
            default -> { }
        }
    }

    private static void sample(GameLoop loop, Bk2FrameInput input, Bk2FrameInput previous) {
        loop.getInputHandler().setLogicalOverride(RecordedInputSnapshots.fromBk2(input, previous));
    }

    private static boolean present(GameLoop loop, LiveCaptureAudioHandle capture, int frames) {
        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
        boolean audible = false;
        for (int frame = 0; frame < frames; frame++) {
            loop.step();
            loop.presentOuterFrame(false, false);
            int stereoFrames = capture.drainPresentationFrame(packet);
            assertTrue(stereoFrames > 0, "the audio producer emitted a packet");
            for (int i = 0; i < stereoFrames * 2; i++) {
                audible |= Math.abs(packet[i]) > 64;
            }
        }
        return audible;
    }
}
