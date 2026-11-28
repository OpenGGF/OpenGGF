package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameId;
import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.game.preview.TitlePreviewProvider.Sequence;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class TestCdHostedTitlePreview {
    @Test void cardLeavesBackdropCloudsMountainsAndSeaTransparent() throws Exception {
        var sequence = discPreview();
        for (int tick : new int[]{0, 16}) {
            var image = sequence.imageAt(tick);
            assertAll(
                    () -> assertEquals(0, rgba(image, 0, 0), "sky"),
                    () -> assertEquals(0, rgba(image, 8, 40), "cloud field"),
                    () -> assertEquals(0, rgba(image, 8, 140), "mountains"),
                    () -> assertEquals(0, rgba(image, 8, 200), "sea"));
        }
    }

    @Test void cardPreservesTheEmblemAndItsTransparentOutline() throws Exception {
        var image = discPreview().imageAt(0);
        // Samples from Map_Emblem's lower rim, clear of the title sprites.
        assertAll(
                () -> assertEquals(0xDBDB00FF, rgba(image, 112, 175), "gold emblem ink"),
                () -> assertEquals(0x000000FF, rgba(image, 128, 175), "opaque black emblem ink"),
                () -> assertEquals(0, rgba(image, 100, 175), "outside the emblem edge"));
    }

    @Test void cardKeepsTheNativePressStartBlink() throws Exception {
        var sequence = discPreview();
        // ObjMenu_PressStart toggles the sprite mapping every 16 title ticks.
        int first = rgba(sequence.imageAt(0), 84, 181);
        int second = rgba(sequence.imageAt(16), 84, 181);
        assertNotEquals(first, second);
        assertTrue(first == 0 || second == 0, "the hidden text reveals the hub backdrop");
    }

    private static Sequence discPreview() throws Exception {
        CdTestMedia.usaCue();
        return GameModuleRegistry.hostedMedia(GameId.SCD)
                .titlePreview(SonicConfigurationService.createStandalone()).orElseThrow();
    }

    private static int rgba(Image image, int x, int y) {
        return ByteBuffer.wrap(image.rgba()).getInt((y * image.width() + x) * 4);
    }

    @Test void detachedDiscTitleSurvivesMediaCloseAndPreservesOwners() throws Exception {
        var bootstrap = GameModuleRegistry.getBootstrapDefault();
        var world = com.openggf.game.session.SessionManager.getCurrentWorldSession();
        var sequence = discPreview();
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
