package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestPipeline;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class)
class TestRingExtraLifeRequestRouting {
    @Test
    void sonic2ActualHundredAndTwoHundredRingPickupsReplaceRingInSecondaryMailbox() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        // Module selection creates the world but does not load a level ledger.
        // Install the real ledger on its owning level manager before pickup.
        var state = new LevelGamestate();
        GameServices.level().resetLevelGamestate(state);
        var audio = GameServices.audio();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        var romFile = RomTestUtils.ensureSonic2RomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 2 REV01 ROM required for native request resolution");
        var rom = new Rom();
        assertTrue(rom.open(romFile.getAbsolutePath()));
        try {
            audio.setRom(rom);
            var observed = new ArrayList<Sonic2SoundRequestService.Event>();
            var profile = new Sonic2AudioProfile(observed::add);
            audio.setAudioProfile(profile);
            audio.setSoundMap(profile.getSoundMap());
            var first = new RingSpawn(100, 100);
            var second = new RingSpawn(120, 100);
            var rings = new RingManager(List.of(first, second), null, null, null, audio);
            rings.reset(0);
            var player = new Sonic("sonic", (short) 100, (short) 100);
            assertSame(state, player.currentLevelState(), "The collector must use the installed world ledger");
            state.setRingExtraLifeFlags(0);
            int lives = GameServices.gameState().getLives();
            for (int threshold : new int[] {100, 200}) {
                observed.clear();
                state.setRings(threshold - 1);
                assertTrue(rings.collectPlacedRing(threshold == 100 ? first : second, player, threshold));
                assertEquals(threshold, state.getRings());
                assertEquals(++lives, GameServices.gameState().getLives());
                audio.presentFrame(PresentationMode.FORWARD);
                var transfers = observed.stream()
                        .filter(Sonic2SoundRequestService.Transfer.class::isInstance)
                        .map(Sonic2SoundRequestService.Transfer.class::cast).toList();
                assertEquals(1, transfers.size(), "The threshold jingle replaces the pending ring request");
                assertEquals(0x98, transfers.getFirst().rawRequestId());
                assertEquals(Sonic2SoundRequestPipeline.SourceSlot.SFX1, transfers.getFirst().sourceMailbox());
                assertEquals(1, transfers.getFirst().physicalSlot());
            }
            assertEquals(6, state.getRingExtraLifeFlags());
        } finally {
            audio.resetState();
            audio.setBackend(new NullAudioBackend());
            rom.close();
        }
    }

    @Test
    void sonic1AndSonic3kThresholdAwardsRetainTheirMusicRequestPath() {
        for (var profile : List.<GameAudioProfile>of(new Sonic1AudioProfile(), new Sonic3kAudioProfile())) {
            var audio = mock(AudioManager.class);
            when(audio.getAudioProfile()).thenReturn(profile);
            var game = new GameStateManager();
            try (var services = mockStatic(GameServices.class)) {
                services.when(GameServices::audio).thenReturn(audio);
                services.when(GameServices::gameState).thenReturn(game);
                var state = new LevelGamestate();
                int lives = game.getLives();
                state.setRings(99);
                state.addRings(1);
                assertEquals(lives + 1, game.getLives());
                verify(audio).playMusic(profile.getExtraLifeMusicId());
                verify(audio).getAudioProfile();
                verifyNoMoreInteractions(audio);
            }
        }
    }
}
