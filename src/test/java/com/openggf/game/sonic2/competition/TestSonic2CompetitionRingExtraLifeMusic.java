package com.openggf.game.sonic2.competition;

import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.camera.Camera;
import com.openggf.data.RetailRomRevision;
import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2RevisionProfile;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestPipeline;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingManagerInternalAccess;
import com.openggf.level.rings.RingSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
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
class TestSonic2CompetitionRingExtraLifeMusic {
    @Test
    void bothNativeSlotsSubmitOneSecondaryJinglePerThresholdAndDoNotRepeatAfterCountLoss() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
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
            for (int owner = 0; owner < 2; owner++) {
                var roster = mock(NativeCompetitionRoster.class);
                var revision = new Sonic2RevisionProfile(RetailRomRevision.S2_REV01);
                var states = new Sonic2CompetitionPlayerState[] {
                        new Sonic2CompetitionPlayerState(0, () -> revision),
                        new Sonic2CompetitionPlayerState(1, () -> revision)};
                var players = new AbstractPlayableSprite[] {
                        mock(AbstractPlayableSprite.class), mock(AbstractPlayableSprite.class)};
                for (int slot = 0; slot < 2; slot++) {
                    when(roster.camera(slot)).thenReturn(new Camera(GameServices.configuration()));
                    when(roster.player(slot)).thenReturn(players[slot]);
                    when(roster.slotOf(players[slot])).thenReturn(slot);
                    when(roster.levelState(slot)).thenReturn(states[slot]);
                    when(players[slot].getGameRules()).thenReturn(new Sonic2GameModule().getRules());
                }
                var policies = mock(WorldSessionPolicyState.class);
                when(policies.getService(NativeCompetitionRoster.class)).thenReturn(roster);
                var module = mock(GameModule.class);
                when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policies);
                var world = new WorldSession(module);
                var level = mock(LevelManager.class);
                var objects = mock(ObjectManager.class);
                when(level.getObjectManager()).thenReturn(objects);
                when(objects.getObjectServices()).thenReturn(new StubObjectServices() {
                    @Override public WorldSession worldSession() { return world; }
                });
                var spawns = List.of(new RingSpawn(100, 256), new RingSpawn(120, 256),
                        new RingSpawn(140, 256), new RingSpawn(160, 256));
                var rings = new RingManager(spawns, null, level, null, audio);
                RingManagerInternalAccess.enableNativeCompetitionStreaming(rings, new Sonic2CompetitionRingStreaming());
                rings.reset(0);
                var state = states[owner];
                int lives = state.gameState().getLives();
                for (int index = 0; index < 4; index++) {
                    // First visit awards100/200; later held-count reductions retain
                    // the native flags, so collecting back over either threshold cannot repeat.
                    int threshold = index % 2 == 0 ? 100 : 200;
                    boolean award = index < 2;
                    state.setRings(threshold - 1);
                    observed.clear();
                    assertTrue(rings.collectPlacedRing(spawns.get(index), players[owner], index));
                    assertFalse(rings.collectPlacedRing(spawns.get(index), players[owner], index));
                    assertEquals(threshold, state.getRings());
                    if (award) lives++;
                    assertEquals(lives, state.gameState().getLives());
                    assertEquals(0, states[1 - owner].getRings());
                    audio.presentFrame(PresentationMode.FORWARD);
                    var transfers = observed.stream()
                            .filter(Sonic2SoundRequestService.Transfer.class::isInstance)
                            .map(Sonic2SoundRequestService.Transfer.class::cast).toList();
                    assertEquals(1, transfers.size(), "slot=" + owner + ", pickup=" + index);
                    assertEquals(award ? 0x98 : 0xB5, transfers.getFirst().rawRequestId());
                    assertEquals(Sonic2SoundRequestPipeline.SourceSlot.SFX1, transfers.getFirst().sourceMailbox());
                    assertEquals(1, transfers.getFirst().physicalSlot());
                }
                assertEquals(6, state.getRingExtraLifeFlags());
            }
        } finally {
            audio.resetState();
            audio.setBackend(new NullAudioBackend());
            rom.close();
        }
    }
}
