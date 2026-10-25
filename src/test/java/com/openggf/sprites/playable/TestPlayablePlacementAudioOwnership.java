package com.openggf.sprites.playable;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.game.CharacterKey;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPlayablePlacementAudioOwnership {
    @BeforeEach void setUp() { TestEnvironment.resetAll(); }
    @AfterEach void tearDown() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void captureReleasesItsManagerAndPreservesCallerAudioEvenWhenConstructionFails(boolean fail) {
        var caller = GameServices.audio();
        var callerBackend = spy(new NullAudioBackend());
        caller.setBackend(callerBackend);
        var callerPort = mock(StreamedMusicPort.class);
        when(callerPort.outputRate()).thenReturn(48_000);
        caller.installStreamedMusicPort(callerPort);

        var owned = AudioManager.createSilent();
        var ownedBackend = spy(new NullAudioBackend());
        owned.setBackend(ownedBackend);
        var ownedPort = mock(StreamedMusicPort.class);
        when(ownedPort.outputRate()).thenReturn(48_000);
        owned.installStreamedMusicPort(ownedPort);
        var donor = spy(new Sonic2GameModule());
        var definition = donor.getPlayableCharacterRegistry().find(CharacterKey.SONIC).orElseThrow();
        var failure = new IllegalStateException("native construction failed");

        // Inject only the allocation boundary; all manager cleanup and native geometry capture stay real.
        try (var allocation = mockStatic(AudioManager.class, CALLS_REAL_METHODS)) {
            allocation.when(AudioManager::createSilent).thenReturn(owned);
            if (fail) {
                var throwingDefinition = new CharacterDefinition(definition.key(), definition.displayName(),
                        (code, x, y) -> { throw failure; }, definition.respawnStrategyFactory(),
                        definition.behavesLike(), definition.secondaryAbility(), definition.supportsSuperForm(),
                        definition.artSupplier(), definition.paletteSupplier(), definition.movementAbilities());
                doReturn(PlayableCharacterRegistry.empty().register(CharacterKey.SONIC, throwingDefinition))
                        .when(donor).getPlayableCharacterRegistry();
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> PlayablePlacementProfile.capture(donor, GameServices.level(), "sonic", 0)));
            } else {
                var profile = PlayablePlacementProfile.capture(donor, GameServices.level(), "sonic", 0);
                assertTrue(profile.xRadius() > 0);
                assertTrue(profile.yRadius() > 0);
            }
            verify(ownedPort).close();
            verify(ownedBackend).destroy();
            assertSame(caller, GameServices.audio());
            assertSame(callerBackend, caller.getBackend());
            verify(callerPort, never()).close();
            verify(callerBackend, never()).destroy();
        } finally {
            owned.destroy();
        }
    }
}
