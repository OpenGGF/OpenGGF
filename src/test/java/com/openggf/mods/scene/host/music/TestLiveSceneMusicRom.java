package com.openggf.mods.scene.host.music;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.session.OwnedSmpsAudioStream;
import com.openggf.audio.session.SmpsDriverSessionConfiguration;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.smps.*;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TestLiveSceneMusicRom {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void starLightMatchesAnUninterruptedRomDriverPastTheOldMinuteRestart() throws Exception {
        compare("s1", 0x84, new Sonic1AudioProfile());
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void mysticCaveKeepsItsNativeIntroAndLoopsAcrossArbitraryOutputPackets() throws Exception {
        compare("s2", 0x84, new Sonic2AudioProfile());
    }

    private void compare(String game, int id, GameAudioProfile profile) throws Exception {
        int rate = 8003; // Exercise fractional NTSC packet lengths too.
        var rom = GameServices.rom().getRom();
        var loader = profile.createSmpsLoader(rom);
        var data = loader.loadMusic(id);
        var dac = loader.loadDacData();
        var handlers = new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState());
        profile.configurePresentationCoordFlagHandlers(handlers);
        var base = profile.getSequencerConfig();
        var config = SmpsConfigBinding.bind(base,
                () -> base.getCoordFlagHandler() == null ? null : handlers.handlerFor(game));
        try (var reference = new OwnedSmpsAudioStream(game, 0, new SmpsPhysicalDevice.Settings(rate, false),
                profile.smpsPhysicalPolicy(), ChipWriteObserver.NONE,
                new SmpsDriverSessionConfiguration(profile.smpsStatefulCommandPolicy()));
             var live = new LiveSceneMusic(game, id, profile, rom, rate)) {
            var seq = new SmpsSequencer(data, dac, reference.logicalDriver(), () -> { }, config);
            reference.logicalDriver().setRegion(SmpsSequencer.Region.NTSC);
            seq.setSampleRate(rate);
            seq.setFallbackVoiceData(data);
            reference.logicalDriver().addSequencer(seq, false);
            short[] expected = new short[rate * 70 * 2];
            short[] packet = new short[((rate + 59) / 60) * 2];
            int output = 0;
            for (int frame = 0; frame < 70 * 60; frame++) {
                int end = (int) ((long) (frame + 1) * rate / 60);
                int count = end - output;
                reference.serviceAndRenderFrame(packet, count);
                System.arraycopy(packet, 0, expected, output * 2, count * 2);
                output = end;
            }
            short[] actual = new short[expected.length];
            short[] oddPacket = new short[514];
            for (int at = 0; at < output; ) {
                int count = Math.min(output - at, at % 257 + 1);
                live.render(oddPacket, count);
                System.arraycopy(oddPacket, 0, actual, at * 2, count * 2);
                at += count;
            }
            assertArrayEquals(expected, actual, "same continuous driver state past native loops and sixty seconds");
            assertTrue(variance(actual, 0, rate * 2) > 100, "music begins in the first second");
            assertTrue(variance(actual, rate * 60 * 2, rate * 2) > 100, "music continues after the old cutoff");
        }
    }

    private static double variance(short[] pcm, int start, int length) {
        double sum = 0, squares = 0;
        for (int i = start; i < start + length; i++) { sum += pcm[i]; squares += (double) pcm[i] * pcm[i]; }
        return squares / length - Math.pow(sum / length, 2);
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void pauseFadeAndCloseHaveBoundedLifetimes() throws Exception {
        var rom = GameServices.rom().getRom();
        try (var live = new LiveSceneMusic("s1", 0x83, new Sonic1AudioProfile(), rom, 8000);
             var control = new LiveSceneMusic("s1", 0x83, new Sonic1AudioProfile(), rom, 8000)) {
            short[] actual = new short[16000], expected = new short[16000];
            live.onHostPause();
            Arrays.fill(actual, (short) 123);
            live.render(actual, 8000);
            assertArrayEquals(new short[16000], actual);
            live.onHostResume();
            live.render(actual, 8000); control.render(expected, 8000);
            assertArrayEquals(expected, actual, "pause does not consume the intro");
            live.fadeOut(); live.render(actual, 8000);
            live.render(actual, 8000);
            assertArrayEquals(new short[16000], actual, "fade ends in silence");
            live.close();
            live.onHostResume(); live.render(actual, 8000);
            assertArrayEquals(new short[16000], actual, "close cannot be resumed");
        }
    }
}
