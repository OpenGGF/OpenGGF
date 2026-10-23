package com.openggf.tools;

import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioNativePcmCaptureAccess;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.synth.FmCoreSelection;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.GameMode;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real recorded input and hardware cadence through production title entry; no reference state. */
@RequiresRom(SonicGame.SONIC_2)
class TestS2NativePcmPresentationClock {
    @Test
    void recordedStartDoesNotStopChipClocksAcrossTheNaturalTitleLaunch() throws Exception {
        Path rom = RomTestUtils.ensureSonic2RomAvailable().toPath();
        Path moviePath = Path.of("src/test/resources/traces/s2/runs/"
                + "s2-sonic-tails-complete-emeralds/sonic-2-sonic-tails-complete-emeralds.bk2");
        var movie = new Bk2MovieLoader().load(moviePath);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        var audio = GameServices.audio();
        try (var session = new GameplayCaptureSession(settings)) {
            session.startAtTitle();
            session.enableAudio();
            var config = GameServices.configuration();
            config.setSessionOverride(SonicConfiguration.AUDIO_FM_CORE, FmCoreSelection.ACCURATE.configValue());
            config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
            AudioNativePcmCaptureAccess.enable(audio,
                    NativePcmCapture.configFromBk2(moviePath, rom, config.getString(SonicConfiguration.REGION)));
            try {
                session.boot(rom, 0, 0, settings);
                short[] samples = new short[8192];
                long total = 0;
                boolean leftTitle = false;
                var initialParity = AudioManagerTestDiagnostics.shadowParitySnapshot(audio);
                for (int row = 0; row < 600; row++) {
                    session.step(movie.getFrame(row));
                    session.renderFrame();
                    leftTitle |= session.loop().getCurrentGameMode() != GameMode.TITLE_SCREEN;
                    assertFalse(session.loop().isUserPaused(), "recorded Start became host pause at row " + row);
                    assertEquals(PresentationMode.FORWARD,
                            session.loop().presentationModeForOuterFrame(false, false), "row " + row);
                    session.loop().presentOuterFrame(false, false);
                    int frames = 0;
                    int count;
                    while ((count = AudioNativePcmCaptureAccess.drain(audio, samples, 0, samples.length / 2)) > 0) {
                        frames += count;
                    }
                    total += frames;
                    // Independent hardware budget: 3420*262 master clocks per
                    // NTSC frame, 53,693,175 Hz master, 44.1 kHz PCM output.
                    assertEquals((row + 1L) * 3420 * 262 * 44100 / 53693175, total,
                            "native-compatible chip clock lost a presentation at row " + row);
                }
                var finalParity = AudioManagerTestDiagnostics.shadowParitySnapshot(audio);
                assertEquals(600, finalParity.presentedFrames() - initialParity.presentedFrames());
                assertEquals(600, finalParity.forwardFrames() - initialParity.forwardFrames());
                assertEquals(initialParity.silentFrames(), finalParity.silentFrames());
                assertTrue(leftTitle, "the real recorded route must cross the title launch boundary");
                assertEquals(1, AudioNativePcmCaptureAccess.sessions(audio));
            } finally {
                AudioNativePcmCaptureAccess.enable(audio, null);
            }
        }
    }
}
