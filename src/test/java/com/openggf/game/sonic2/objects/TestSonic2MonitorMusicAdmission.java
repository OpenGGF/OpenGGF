package com.openggf.game.sonic2.objects;

import com.openggf.audio.GameMusic;
import com.openggf.game.GameStateManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.ObjectPlayerQuery;
import java.util.List;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.DrowningController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TestSonic2MonitorMusicAdmission {
    @Test
    void invincibilityKeepsBossAndDrowningMusicButStillGrantsThePowerup() {
        for (int boss : new int[] {0, 1}) {
            for (int air : new int[] {11, 12, 13, 30}) {
                var player = mock(AbstractPlayableSprite.class);
                var drowning = mock(DrowningController.class);
                when(player.getDrowningController()).thenReturn(drowning);
                when(drowning.getRemainingAir()).thenReturn(air);
                var services = new Services(player);
                services.state.setCurrentBossId(boss);

                MonitorObjectInstance.applyMonitorPowerup(7, player, services);

                verify(player).giveInvincibility();
                assertEquals(boss == 0 && air > 12 ? GameMusic.INVINCIBILITY : null,
                        services.music, "boss=" + boss + ", air_left=" + air);
            }
        }
    }

    @Test
    void superSonicDoesNotReceiveTheEffectOrChangeMusic() {
        var player = mock(AbstractPlayableSprite.class);
        when(player.isSuperSonic()).thenReturn(true);
        var services = new Services(player);

        MonitorObjectInstance.applyMonitorPowerup(7, player, services);

        verify(player, never()).giveInvincibility();
        assertEquals(null, services.music);
    }

    @Test
    void superMainPlayerSuppressesInvincibilityForSidekickRecipient() {
        var main = mock(AbstractPlayableSprite.class);
        var sidekick = mock(AbstractPlayableSprite.class);
        when(main.isSuperSonic()).thenReturn(true);
        var services = new Services(main);

        MonitorObjectInstance.applyMonitorPowerup(7, sidekick, services);

        verify(sidekick, never()).giveInvincibility();
        assertEquals(null, services.music);
    }

    private static final class Services extends StubObjectServices {
        final GameStateManager state = new GameStateManager();
        GameMusic music;
        Services(AbstractPlayableSprite main) {
            withPlayerQuery(new ObjectPlayerQuery(() -> main, List::of));
        }
        @Override public GameStateManager gameState() { return state; }
        @Override public boolean playMusic(GameMusic music) { this.music = music; return true; }
    }
}
