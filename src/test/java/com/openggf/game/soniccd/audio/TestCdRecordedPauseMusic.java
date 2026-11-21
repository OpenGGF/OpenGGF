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
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdSignpost;
import com.openggf.game.soniccd.objects.common.CdTimePost;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.timetravel.CdTimeWarp;
import com.openggf.game.soniccd.timetravel.CdTimeZone;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestCdRecordedPauseMusic {
    private enum Music {
        PRESENT(CdStagePayload.R11A, 0xD5, 0xD6),
        PAST(CdStagePayload.R11B, 0x90, 0x91),
        PAST_INVINCIBILITY(CdStagePayload.R11B, 0xD5, 0xD6),
        PAST_SPEED_SHOES(CdStagePayload.R11B, 0xD5, 0xD6),
        FUTURE(CdStagePayload.R11D, 0xD5, 0xD6),
        PRESENT_WARPING_TO_PAST(CdStagePayload.R12A, 0x90, 0x91, CdTimeZone.PAST),
        PAST_WARPING_TO_PRESENT(CdStagePayload.R12B, 0xD5, 0xD6, CdTimeZone.PRESENT),
        PAST_SIGNPOST_WITH_SPEED_SHOES(CdStagePayload.R11B, 0x90, 0x91);

        final CdStagePayload stage;
        final int pause;
        final int unpause;
        final CdTimeZone warpDestination;

        Music(CdStagePayload stage, int pause, int unpause) {
            this(stage, pause, unpause, null);
        }

        Music(CdStagePayload stage, int pause, int unpause, CdTimeZone warpDestination) {
            this.stage = stage;
            this.pause = pause;
            this.unpause = unpause;
            this.warpDestination = warpDestination;
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
            var module = new SonicCdGameModule();
            GameLoop loop = boot.boot(disc, module,
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

                var player = GameServices.camera().getFocusedSprite();
                if (music.warpDestination != null) {
                    enterWarpWindow(loop, capture, CdTimeTravel.of(module), player, music);
                    assertPowerUpMusicDuringWarp(module);
                }
                if (music == Music.PAST_SIGNPOST_WITH_SPEED_SHOES) {
                    addSignpostBehind(player);
                    present(loop, capture, 1);
                    assertAll(
                            () -> assertFalse(player.hasSpeedShoes(), "the signpost clears the speed-shoes flag"),
                            () -> assertEquals(0xC00, player.getEffectiveMax(), "the signpost leaves top speed boosted"),
                            () -> assertEquals(0x18, player.getEffectiveRunAccel(), "the signpost leaves acceleration boosted"));
                }

                boolean fadingPcm = music == Music.PAST_WARPING_TO_PRESENT;
                boolean audibleDuringPause = music.warpDestination != null
                        || music == Music.PAST_SIGNPOST_WITH_SPEED_SHOES;
                List<Integer> requests = new ArrayList<>();
                List<Integer> chargeStops = new ArrayList<>();
                audio.setRequestObserver((kind, id) -> {
                    if (kind == AudioRequestObserver.RequestClass.MUSIC
                            && (id == 0x90 || id == 0x91 || id == 0xD5 || id == 0xD6)) {
                        requests.add(id);
                    }
                    if (kind == AudioRequestObserver.RequestClass.SFX && id == 0xAB) chargeStops.add(id);
                });
                try {
                    sample(loop, start, released);
                    present(loop, capture, 1); // Allows the PCM command to reach its next driver tick.
                    int stopsOnEntry = chargeStops.size();
                    sample(loop, start, start);
                    boolean audibleWhilePaused = present(loop, capture, 20);
                    boolean paused = GameServices.gameState().isGamePaused();
                    boolean hostPaused = loop.isUserPaused();
                    List<Integer> pausedMusicRequests = List.copyOf(requests);
                    int stopsAfterHold = chargeStops.size();
                    boolean shoesWhilePaused = player.hasSpeedShoes();
                    boolean lateAudio = music.warpDestination != null
                            && presentLateInWarpPause(loop, capture);
                    int stopsBeforeRelease = chargeStops.size();

                    sample(loop, released, start);
                    present(loop, capture, 1);
                    int stopsBeforeResume = chargeStops.size();
                    sample(loop, start, released);
                    boolean audibleOnResume = present(loop, capture, 1);
                    sample(loop, start, start);
                    boolean audibleAfterResume = present(loop, capture, 29) || audibleOnResume;
                    assertAll(
                            () -> assertTrue(paused, "recorded Start pauses the level"),
                            () -> assertFalse(hostPaused, "recorded Start leaves host pause clear"),
                            () -> assertEquals(List.of(music.pause), pausedMusicRequests, "one native pause command while Start is held"),
                            () -> assertEquals(1, stopsOnEntry, "the entry frame requests FM_CHARGESTOP"),
                            () -> assertEquals(21, stopsAfterHold, "each paused frame requests FM_CHARGESTOP"),
                            () -> assertEquals(audibleDuringPause, audibleWhilePaused, "music output during the pause"),
                            () -> {
                                if (music == Music.PAST_SIGNPOST_WITH_SPEED_SHOES) {
                                    assertFalse(shoesWhilePaused, "crossing the signpost clears speed shoes");
                                }
                            },
                            () -> {
                                if (music.warpDestination == CdTimeZone.PAST) {
                                    assertTrue(lateAudio, "the source CD track keeps playing during pause");
                                } else if (fadingPcm) {
                                    assertFalse(lateAudio, "the source PCM fade completes during pause");
                                }
                            },
                            () -> assertFalse(GameServices.gameState().isGamePaused(), "next Start resumes the level"),
                            () -> assertEquals(List.of(music.pause, music.unpause), requests, "one matching native resume command"),
                            () -> assertEquals(stopsBeforeRelease + 1, stopsBeforeResume,
                                    "releasing Start leaves the charge stop request active"),
                            () -> assertEquals(!fadingPcm, audibleAfterResume, "music output after resume"));
                } finally {
                    audio.setRequestObserver(AudioRequestObserver.NONE);
                }
                if (music == Music.PAST_SIGNPOST_WITH_SPEED_SHOES) {
                    sample(loop, released, start);
                    var level = (CdLevel) GameServices.level().getCurrentLevel();
                    for (int frame = 0; frame < 240 && !level.flags().ctrlLocked; frame++) {
                        present(loop, capture, 1);
                    }
                    assertTrue(level.flags().ctrlLocked, "the signpost starts the forced results run");
                    assertEquals(0xC00, player.getMax(), "results movement still uses boosted top speed");
                    assertEquals(0x18, player.getRunAccel(), "results movement still uses boosted acceleration");

                    var resultsState = player.captureRewindState();
                    player.giveSpeedShoes();
                    player.deactivateSpeedShoes();
                    assertEquals(0x600, player.getMax(), "ordinary expiry writes normal top speed");
                    assertEquals(0xC, player.getRunAccel(), "ordinary expiry writes normal acceleration");
                    player.restoreRewindState(resultsState);
                    assertFalse(player.hasSpeedShoes(), "rewind restores the cleared flag");
                    assertEquals(0xC00, player.getMax(), "rewind restores boosted top speed");
                    assertEquals(0x18, player.getRunAccel(), "rewind restores boosted acceleration");
                }
            }
            if (music == Music.PAST_SIGNPOST_WITH_SPEED_SHOES) {
                // ObjSonic_Init (Level/_Objects/Sonic/Main.asm:152-154) writes the new act's words.
                GameServices.level().loadZoneAndAct(
                        SonicCdZoneRegistry.zoneIndex(CdStagePayload.R12A), SonicCdZoneRegistry.actIndex(CdStagePayload.R12A));
                var nextPlayer = GameServices.camera().getFocusedSprite();
                assertEquals(0x600, nextPlayer.getMax(), "the next act initializes normal top speed");
                assertEquals(0xC, nextPlayer.getRunAccel(), "the next act initializes normal acceleration");
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
            case PAST_SPEED_SHOES, PAST_SIGNPOST_WITH_SPEED_SHOES -> {
                player.giveSpeedShoes();
                CdSoundRequests.powerUpMusic(services, CdSoundRequests.SPEED_SHOES);
            }
            default -> { }
        }
    }

    private static void enterWarpWindow(GameLoop loop, LiveCaptureAudioHandle capture,
                                        CdTimeTravel timeTravel, AbstractPlayableSprite player, Music music) {
        int subtype = music.warpDestination == CdTimeZone.PAST ? CdTimePost.PAST_SUBTYPE : 9;
        var spawn = new ObjectSpawn(player.getCentreX(), player.getCentreY(), 0x19, subtype, 0, false, 0);
        GameServices.level().getObjectManager().addDynamicObject(new CdTimePost(spawn));
        present(loop, capture, 3);

        // ObjSonic_TimeWarp (Level/_Objects/Sonic/Main.asm:594-613) reaches its trigger
        // at 210. Hold the speed condition here so the next game-loop step triggers it
        // and dispatches the outgoing PCM fade.
        short groundSpeed = player.getGSpeed();
        player.setGSpeed((short) 0x600);
        for (int frame = 0; frame < 209 && timeTravel.warpTimer() < 209; frame++) {
            CdTimeWarp.run(timeTravel, player, false, null);
            timeTravel.vIntTick(false);
        }
        present(loop, capture, 1);
        player.setGSpeed(groundSpeed);

        var level = (CdLevel) GameServices.level().getCurrentLevel();
        assertAll(
                () -> assertEquals(music.stage, level.payload(), "the source stage is still loaded"),
                () -> assertEquals(music.warpDestination, timeTravel.timeZone(), "the warp has set the destination time zone"),
                () -> assertTrue(timeTravel.warping() && !timeTravel.warpReady(), "the warp has triggered before restart"));
    }

    private static void assertPowerUpMusicDuringWarp(SonicCdGameModule module) {
        var services = mock(ObjectServices.class);
        when(services.gameModule()).thenReturn(module);
        when(services.levelManager()).thenReturn(GameServices.level());

        CdSoundRequests.powerUpMusic(services, CdSoundRequests.INVINCIBILITY);
        CdSoundRequests.powerUpMusic(services, CdSoundRequests.SPEED_SHOES);

        verify(services).playMusic(CdSoundRequests.INVINCIBILITY);
        verify(services).playMusic(CdSoundRequests.SPEED_SHOES);
        verify(services, never()).playMusic(CdSoundRequests.FADE_PCM);
    }

    private static boolean presentLateInWarpPause(GameLoop loop, LiveCaptureAudioHandle capture) {
        present(loop, capture, 220);
        return present(loop, capture, 10);
    }

    private static void addSignpostBehind(AbstractPlayableSprite player) {
        var spawn = new ObjectSpawn(player.getCentreX() - 16, player.getCentreY(), 0x17, 0, 0, false, 0);
        GameServices.level().getObjectManager().addDynamicObject(new CdSignpost(spawn));
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
