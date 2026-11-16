package com.openggf.game.soniccd;

import com.openggf.audio.StreamedMusicPort;
import com.openggf.game.audio.RomSoundCatalog;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCdModMusic {
    @Test void cdAuthoredZonesHaveExplicitUnsequencedProgression() {
        assertTrue(com.openggf.mods.StockProgressionAnchors.anchorsFor("scd").isEmpty());
        assertTrue(com.openggf.mods.StockProgressionAnchors.defaultAnchorFor("scd").isEmpty());
    }
    @Test void cdReskinsUseCdProviderKeys() {
        assertTrue(com.openggf.mods.StockArtOverrideCatalog.contains("scd", "cd.monitor"));
        assertFalse(com.openggf.mods.StockArtOverrideCatalog.contains("scd", "signpost"));
    }

    @Test void nativeMusicDomainContainsDiscAndPastMusicButRejectsCommandsAndVoices() {
        assertEquals("SCMD_R1AMUS", RomSoundCatalog.music("scd", 0x0F).name());
        assertTrue(RomSoundCatalog.music("scd").stream().anyMatch(e -> e.name().equals("SCMD_PASTMUS")));
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.music("scd", 0x74));
        assertEquals(0x92, RomSoundCatalog.sfx("scd", 0x92).engineId());
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.music("scd", 0x7A));
    }

    @Test void overrideRoutingPreservesNativeFallbackAndForwardsDiscControlCommands() {
        var calls = new ArrayList<String>();
        var streamed = (StreamedMusicPort) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{StreamedMusicPort.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    return method.getName().equals("hasStockOverride") ? (int) args[0] == 0x0F : null;
                });
        var profile = new SonicCdAudioProfile(() -> null);
        assertTrue(profile.routeMusicRequest(0x0F, streamed));
        assertFalse(profile.routeMusicRequest(0xD5, streamed));
        assertTrue(calls.contains("pause"));
        assertFalse(profile.routeMusicRequest(0xD6, streamed));
        assertTrue(calls.contains("resume"));
        assertFalse(profile.routeMusicRequest(0x10, streamed));
        assertTrue(calls.contains("stop"));
    }
}
