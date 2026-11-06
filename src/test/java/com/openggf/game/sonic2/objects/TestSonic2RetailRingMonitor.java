package com.openggf.game.sonic2.objects;

import com.openggf.audio.GameMusic;
import com.openggf.data.RetailRomRevision;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2RevisionProfile;
import com.openggf.game.sonic2.constants.Sonic2AudioConstants;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2RetailRingMonitor {
    @Test void monitorCapAndWordWrapBelongToItsOwnNativeAward() {
        for (var revision : new RetailRomRevision[] {RetailRomRevision.S2_REV00, RetailRomRevision.S2_REV01}) {
            var f = new Fixture(revision);
            f.state.setRings(999);
            f.state.setRingExtraLifeFlags(6);
            f.award();
            assertEquals(revision == RetailRomRevision.S2_REV00 ? 1009 : 999, f.state.getRings());
            assertEquals(3, f.game.getLives());
            assertEquals(Sonic2AudioConstants.SFX_RING_RIGHT, f.mailbox);
            assertNull(f.music);
        }
        var original = new Fixture(RetailRomRevision.S2_REV00);
        original.state.setRings(0xFFFA);
        original.award();
        assertEquals(4, original.state.getRings(), "REV00 uses ADDI.W rather than an unbounded Java sum");
        assertEquals(3, original.game.getLives());
    }

    @Test void bothMonitorRevisionsAwardOnlyOneLifeAndUseTheExtraLifeMusicBranch() {
        for (var revision : new RetailRomRevision[] {RetailRomRevision.S2_REV00, RetailRomRevision.S2_REV01}) {
            var f = new Fixture(revision);
            f.state.setRings(95);
            f.award();
            assertEquals(105, f.state.getRings());
            assertEquals(2, f.state.getRingExtraLifeFlags());
            assertEquals(4, f.game.getLives());
            assertEquals(GameMusic.EXTRA_LIFE, f.music);
            assertEquals(-1, f.mailbox);
        }
    }

    private static final class Fixture extends StubObjectServices {
        final LevelGamestate state = new LevelGamestate();
        final GameStateManager game = new GameStateManager();
        final AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        final Sonic2GameModule module;
        int mailbox = -1;
        GameMusic music;

        Fixture(RetailRomRevision revision) {
            var profile = new Sonic2RevisionProfile(revision);
            module = new Sonic2GameModule() {
                @Override public <T> T getGameService(Class<T> type) {
                    return type == Sonic2RevisionProfile.class ? type.cast(profile) : super.getGameService(type);
                }
            };
            when(player.currentLevelState()).thenReturn(state);
            when(player.currentGameState()).thenReturn(game);
        }
        void award() { MonitorObjectInstance.applyMonitorPowerup(4, player, this); }
        @Override public Sonic2GameModule gameModule() { return module; }
        @Override public LevelGamestate levelGamestate() { return state; }
        @Override public GameStateManager gameState() { return game; }
        @Override public void playMusicMailboxNativeRequest(int id) { mailbox = id; }
        @Override public boolean playMusic(GameMusic id) { music = id; return true; }
    }
}
