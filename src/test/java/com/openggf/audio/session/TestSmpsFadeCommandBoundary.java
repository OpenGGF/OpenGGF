package com.openggf.audio.session;

import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsPhysicalPolicy;
import com.openggf.game.sonic3k.audio.Sonic3kStatefulCommandPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSmpsFadeCommandBoundary {
    @Test
    void hostFadeArmsCountersAndSilencesPsgEvenWithoutMusic() {
        List<Integer> writes = new ArrayList<>();
        ChipWriteObserver observer = new ChipWriteObserver() {
            @Override public void onYm2612Write(int port, int register, int value) { }
            @Override public void onPsgWrite(int value) { writes.add(value); }
        };
        try (OwnedSmpsAudioStream stream = new OwnedSmpsAudioStream("fade-test", 0,
                SmpsSessionTestFixtures.settings(), Sonic3kSmpsPhysicalPolicy.INSTANCE, observer,
                new SmpsDriverSessionConfiguration(Sonic3kStatefulCommandPolicy.INSTANCE))) {
            assertTrue(stream.logicalDriver().captureSnapshot().sequencers().isEmpty());
            writes.clear();
            stream.fadeOutMusic(0x28, 6);
            var state = stream.logicalDriver().captureSnapshot();
            assertEquals(0x28, state.fadeOutTimeout());
            assertEquals(6, state.fadeDelay());
            assertEquals(6, state.fadeDelayTimeout());
            assertTrue(state.driverOwnedFade());
            assertEquals(List.of(0x9f, 0xbf, 0xdf, 0xff), writes);
        }
    }
}
