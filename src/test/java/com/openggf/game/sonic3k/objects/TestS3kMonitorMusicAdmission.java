package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.audio.GameMusic;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.DrowningController;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TestS3kMonitorMusicAdmission {
    @Test
    void primaryPoweredFormSuppressesSidekickInvincibilityGrant() {
        var primary = mock(AbstractPlayableSprite.class);
        when(primary.isSuperSonic()).thenReturn(true);
        var recipient = mock(AbstractPlayableSprite.class);
        List<GameMusic> music = new ArrayList<>();
        var services = new TestObjectServices() {
            @Override public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
                return new com.openggf.level.objects.ObjectPlayerQuery(() -> primary, () -> List.of(recipient));
            }
            @Override public boolean playMusic(GameMusic request) { music.add(request); return true; }
        };
        var monitor = new Sonic3kMonitorObjectInstance(new ObjectSpawn(0, 0, 1, 8, 0, false, 0));
        monitor.setServices(services);
        monitor.applyPowerup(recipient);
        verify(recipient, never()).giveInvincibility();
        assertEquals(List.of(), music);
    }

    @Test
    void invincibilityGrantPreservesBossAndLowAirMusicAtExactThreshold() {
        for (int boss : new int[]{0, 1}) {
            for (int air : new int[]{11, 12, 13}) {
                var player = mock(AbstractPlayableSprite.class);
                var drowning = mock(DrowningController.class);
                when(player.getDrowningController()).thenReturn(drowning);
                when(drowning.getRemainingAir()).thenReturn(air);
                var state = mock(GameStateManager.class);
                when(state.getCurrentBossId()).thenReturn(boss);
                List<GameMusic> music = new ArrayList<>();
                var services = new TestObjectServices() {
                    @Override public boolean playMusic(GameMusic request) { music.add(request); return true; }
                }.withGameState(state);
                var monitor = new Sonic3kMonitorObjectInstance(new ObjectSpawn(0, 0, 1, 8, 0, false, 0));
                monitor.setServices(services);
                monitor.applyPowerup(player);
                verify(player).giveInvincibility();
                assertEquals(boss == 0 && air > 12 ? List.of(GameMusic.INVINCIBILITY) : List.of(), music,
                        "Boss_flag=" + boss + ", air_left=" + air);
            }
        }
    }
}
