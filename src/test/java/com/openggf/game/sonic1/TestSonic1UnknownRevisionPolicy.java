package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.events.Sonic1LZWaterEvents;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class)
class TestSonic1UnknownRevisionPolicy {
    @BeforeEach
    void configure() { TestEnvironment.configureGameModuleFixture(new Sonic1GameModule()); }

    @Test
    void unknownImageRetainsItsOriginalDrowningTransportBehavior() throws Exception {
        var provider = unknownProvider();
        var events = mock(Sonic1LZWaterEvents.class);
        installEvents(provider, events);
        var player = mock(AbstractPlayableSprite.class);
        when(player.isDrowningPreDeath()).thenReturn(true);
        provider.updatePrePhysics(player, 0, Sonic1Constants.ZONE_LZ);
        verify(events).updateWindTunnels();
    }

    @Test
    void unknownImageRetainsDynamicWaterWhileThePlayerIsDead() throws Exception {
        var provider = unknownProvider();
        var events = mock(Sonic1LZWaterEvents.class);
        installEvents(provider, events);
        var player = mock(AbstractPlayableSprite.class);
        when(player.getDead()).thenReturn(true);
        provider.update(player, 0, Sonic1Constants.ZONE_LZ);
        verify(events).update();
    }

    @Test
    void unverifiedHeaderCannotSelectRevisionZeroAddresses() throws Exception {
        try (var rom = Rom.fromReader(RomByteReader.fromBytes(new byte[512]), "unknown image")) {
            assertEquals(Sonic1Constants.LEVEL_HEADERS_ADDR,
                    Sonic1RomProfile.resolve(rom, Sonic1Constants.LEVEL_HEADERS_ADDR));
            assertFalse(Sonic1RomProfile.isRev00(rom));
        }
    }

    private static Sonic1ZoneFeatureProvider unknownProvider() throws Exception {
        var provider = new Sonic1ZoneFeatureProvider();
        // A non-water init binds identity without requiring graphics from a synthetic image.
        try (var rom = Rom.fromReader(RomByteReader.fromBytes(new byte[512]), "unknown image")) {
            provider.initZoneFeatures(rom, Sonic1Constants.ZONE_GHZ, 0, 0);
        }
        return provider;
    }

    private static void installEvents(Sonic1ZoneFeatureProvider provider, Sonic1LZWaterEvents events) throws Exception {
        var field = Sonic1ZoneFeatureProvider.class.getDeclaredField("waterEvents");
        field.setAccessible(true);
        field.set(provider, events);
    }
}
