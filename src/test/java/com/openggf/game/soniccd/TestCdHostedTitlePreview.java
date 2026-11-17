package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCdHostedTitlePreview {
    @Test void detachedDiscTitleSurvivesMediaCloseAndPreservesOwners() throws Exception {
        CdTestMedia.usaCue();
        var bootstrap = GameModuleRegistry.getBootstrapDefault();
        var world = com.openggf.game.session.SessionManager.getCurrentWorldSession();
        var sequence = GameModuleRegistry.hostedMedia(GameId.SCD)
                .titlePreview(SonicConfigurationService.createStandalone()).orElseThrow();
        assertEquals(256, sequence.width());
        assertEquals(224, sequence.height());
        var still = sequence.imageAt(sequence.stillTick());
        assertTrue(still.hasVisiblePixels());
        assertTrue(java.util.stream.IntStream.range(0, still.rgba().length)
                .filter(i -> i % 4 != 3).anyMatch(i -> still.rgba()[i] != 0), "title has coloured art");
        assertArrayEquals(sequence.imageAt(-1).rgba(), sequence.imageAt(0).rgba());
        assertArrayEquals(still.rgba(), sequence.imageAt(sequence.stillTick()).rgba());
        assertSame(bootstrap, GameModuleRegistry.getBootstrapDefault());
        assertSame(world, com.openggf.game.session.SessionManager.getCurrentWorldSession());
    }
    @Test void menuMusicOwnsAnIndependentDiscProfile() throws Exception {
        CdTestMedia.usaCue();
        var media = GameModuleRegistry.hostedMedia(GameId.SCD);
        var config = SonicConfigurationService.createStandalone();
        try (var first = media.menuMusic(config).orElseThrow();
             var second = media.menuMusic(config).orElseThrow()) {
            assertNotSame(first.profile(), second.profile());
            var a = (com.openggf.game.soniccd.audio.SonicCdAudioProfile) first.profile();
            var b = (com.openggf.game.soniccd.audio.SonicCdAudioProfile) second.profile();
            a.subCpuAudio().bindOutputRate(48000);
            a.subCpuAudio().submit(first.musicId());
            short[] pcm = new short[48000 * 2];
            a.subCpuAudio().render(pcm, 48000);
            boolean audible = false;
            for (short sample : pcm) audible |= sample != 0;
            assertTrue(audible, "disc title produces PCM");
            assertEquals(0, b.subCpuAudio().state().cdda().track(), "second transport stays idle");
        }
    }
}
